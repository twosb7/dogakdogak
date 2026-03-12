"""
도각도각 키보드 타건음 오디오 프로세싱 v2
- 먹먹한(Muddy) 구간 감지 및 제거 (Spectral Centroid 기반)
- 더 공격적인 EQ: 200-600Hz 대역 강력 감쇠
- Highpass 100Hz (더 높은 컷오프)
- 타격감 향상 (Transient Shaping)
- 무음 제거 (Silence Trimming)
- 음량 평준화 (Normalization to -1.0dB peak)
"""

import os
import sys
import glob
import numpy as np
from scipy.signal import butter, sosfiltfilt
from scipy.io import wavfile
import subprocess
import tempfile
import shutil

# ffmpeg 경로
FFMPEG = (
    os.environ.get("FFMPEG")
    or shutil.which("ffmpeg")
    or shutil.which("ffmpeg.exe")
    or os.path.expanduser("~/ffmpeg_bin/ffmpeg.exe")
)

# 소스 디렉토리
RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
RAW_DIR = os.path.normpath(RAW_DIR)

# 백업 디렉토리 (원본 백업 사용)
BACKUP_DIR = os.path.join(os.path.dirname(__file__), "audio_backup")


def mp3_to_wav(mp3_path: str, wav_path: str) -> None:
    subprocess.run(
        [FFMPEG, "-y", "-i", mp3_path, "-ar", "44100", "-ac", "1", wav_path],
        capture_output=True, check=True
    )


def wav_to_mp3(wav_path: str, mp3_path: str, bitrate: str = "192k") -> None:
    subprocess.run(
        [FFMPEG, "-y", "-i", wav_path, "-codec:a", "libmp3lame", "-b:a", bitrate, mp3_path],
        capture_output=True, check=True
    )


def read_wav(wav_path: str) -> tuple[int, np.ndarray]:
    sr, data = wavfile.read(wav_path)
    if data.dtype == np.int16:
        data = data.astype(np.float64) / 32768.0
    elif data.dtype == np.int32:
        data = data.astype(np.float64) / 2147483648.0
    elif data.dtype == np.float32:
        data = data.astype(np.float64)
    return sr, data


def write_wav(wav_path: str, sr: int, data: np.ndarray) -> None:
    data = np.clip(data, -1.0, 1.0)
    data_int16 = (data * 32767).astype(np.int16)
    wavfile.write(wav_path, sr, data_int16)


# ═══════════════════════════════════════════════════════════
#  1. Spectral Centroid 기반 Muddy 구간 감지 & 제거
# ═══════════════════════════════════════════════════════════

def compute_spectral_centroid(frame: np.ndarray, sr: int) -> float:
    """프레임의 spectral centroid (Hz) 계산"""
    windowed = frame * np.hanning(len(frame))
    spectrum = np.abs(np.fft.rfft(windowed))
    freqs = np.fft.rfftfreq(len(frame), 1.0 / sr)
    total = np.sum(spectrum)
    if total < 1e-10:
        return 0.0
    return float(np.sum(freqs * spectrum) / total)


def compute_mud_ratio(frame: np.ndarray, sr: int) -> float:
    """프레임에서 200-600Hz 에너지 비율 계산 (높을수록 먹먹함)"""
    windowed = frame * np.hanning(len(frame))
    spectrum = np.abs(np.fft.rfft(windowed)) ** 2
    freqs = np.fft.rfftfreq(len(frame), 1.0 / sr)
    total_energy = np.sum(spectrum)
    if total_energy < 1e-10:
        return 0.0
    mud_mask = (freqs >= 200) & (freqs <= 600)
    mud_energy = np.sum(spectrum[mud_mask])
    return float(mud_energy / total_energy)


def remove_muddy_sections(data: np.ndarray, sr: int,
                          frame_ms: float = 10.0,
                          centroid_threshold: float = 800.0,
                          mud_ratio_threshold: float = 0.6,
                          min_clean_ms: float = 3.0) -> np.ndarray:
    """
    Muddy 구간 감지 및 제거:
    - Spectral centroid가 낮고 (< 800Hz)
    - 200-600Hz 에너지 비율이 높은 (> 60%) 프레임을 제거
    - 최소 clean 구간(3ms) 이상만 유지하여 글리치 방지
    """
    frame_size = int(sr * frame_ms / 1000)
    hop_size = frame_size // 2
    n_frames = max(1, (len(data) - frame_size) // hop_size + 1)

    # 프레임별 분석
    is_clean = np.ones(n_frames, dtype=bool)
    frame_rms = np.zeros(n_frames)

    for i in range(n_frames):
        start = i * hop_size
        end = min(start + frame_size, len(data))
        frame = data[start:end]

        rms = np.sqrt(np.mean(frame ** 2))
        frame_rms[i] = rms

        # 무음 프레임은 그냥 유지
        if rms < 0.005:
            continue

        centroid = compute_spectral_centroid(frame, sr)
        mud_ratio = compute_mud_ratio(frame, sr)

        # Muddy 판정: centroid 낮고 mud 비율 높으면 제거
        if centroid < centroid_threshold and mud_ratio > mud_ratio_threshold:
            is_clean[i] = False

    # Attack 구간 보호: 처음 5ms는 항상 유지 (transient)
    protect_frames = max(1, int(5.0 / frame_ms * 2))  # hop 기준
    for i in range(min(protect_frames, n_frames)):
        is_clean[i] = True

    # 너무 짧은 clean 구간 제거 (글리치 방지)
    min_clean_frames = max(1, int(min_clean_ms / frame_ms * 2))
    # Clean 구간 연속성 확인
    clean_count = 0
    for i in range(n_frames):
        if is_clean[i]:
            clean_count += 1
        else:
            if 0 < clean_count < min_clean_frames:
                # 너무 짧은 clean 구간은 muddy로 처리
                for j in range(i - clean_count, i):
                    is_clean[j] = False
            clean_count = 0

    # Clean 프레임만 이어붙이기
    clean_segments = []
    in_clean = False
    seg_start = 0

    for i in range(n_frames):
        sample_pos = i * hop_size
        if is_clean[i] and not in_clean:
            seg_start = sample_pos
            in_clean = True
        elif not is_clean[i] and in_clean:
            seg_end = sample_pos + frame_size
            clean_segments.append(data[seg_start:min(seg_end, len(data))])
            in_clean = False

    if in_clean:
        clean_segments.append(data[seg_start:])

    if not clean_segments:
        return data  # 모두 muddy면 원본 반환

    # 세그먼트 사이 짧은 crossfade (1ms) 적용하여 글리치 방지
    crossfade_samples = max(int(sr * 0.001), 1)
    result = clean_segments[0]
    for seg in clean_segments[1:]:
        if len(result) >= crossfade_samples and len(seg) >= crossfade_samples:
            fade_out = np.linspace(1, 0, crossfade_samples)
            fade_in = np.linspace(0, 1, crossfade_samples)
            result[-crossfade_samples:] *= fade_out
            seg_copy = seg.copy()
            seg_copy[:crossfade_samples] *= fade_in
            result[-crossfade_samples:] += seg_copy[:crossfade_samples]
            result = np.concatenate([result, seg_copy[crossfade_samples:]])
        else:
            result = np.concatenate([result, seg])

    removed_pct = (1.0 - len(result) / len(data)) * 100
    if removed_pct > 0:
        print(f"    [Mud Removal] {removed_pct:.1f}% removed", end="")

    return result


# ═══════════════════════════════════════════════════════════
#  2. 공격적 EQ (High-pass + Wide Mud Cut + Clarity Boost)
# ═══════════════════════════════════════════════════════════

def highpass_filter(data: np.ndarray, sr: int, cutoff: float = 100.0, order: int = 4) -> np.ndarray:
    """100Hz 이하 Low Cut - 더 공격적인 rumble 제거"""
    sos = butter(order, cutoff, btype='highpass', fs=sr, output='sos')
    return sosfiltfilt(sos, data)


def aggressive_eq(data: np.ndarray, sr: int) -> np.ndarray:
    """
    더 공격적인 EQ:
    - Wide Mud Cut: 200-600Hz 대역 -9dB 감쇠
    - Presence Boost: 3kHz-6kHz +4dB
    - Air/Brightness: 8kHz-12kHz +2dB
    """
    # Wide Mud Removal: 200-600Hz
    sos_mud = butter(3, [200, 600], btype='bandpass', fs=sr, output='sos')
    mud_band = sosfiltfilt(sos_mud, data)
    data_eq = data - mud_band * 0.65  # ~-9dB 감쇠

    # Presence Boost: 3kHz-6kHz
    sos_presence = butter(2, [3000, 6000], btype='bandpass', fs=sr, output='sos')
    presence_band = sosfiltfilt(sos_presence, data_eq)
    data_eq = data_eq + presence_band * 0.6  # ~+4dB

    # Air/Brightness: 8kHz-12kHz
    sos_air = butter(2, [8000, 12000], btype='bandpass', fs=sr, output='sos')
    air_band = sosfiltfilt(sos_air, data_eq)
    data_eq = data_eq + air_band * 0.3  # ~+2dB

    return data_eq


# ═══════════════════════════════════════════════════════════
#  3. Transient Shaping (최적화 버전)
# ═══════════════════════════════════════════════════════════

def enhance_transients(data: np.ndarray, sr: int) -> np.ndarray:
    """Vectorized transient enhancement"""
    window_ms = 2
    window_samples = max(int(sr * window_ms / 1000), 1)

    # Vectorized RMS envelope
    padded = np.pad(data ** 2, (window_samples, 0), mode='constant')
    cumsum = np.cumsum(padded)
    rms_sq = (cumsum[window_samples:] - cumsum[:-window_samples]) / window_samples
    envelope = np.sqrt(np.maximum(rms_sq[:len(data)], 0))

    # Attack detection
    envelope_diff = np.diff(envelope, prepend=0)
    attack_mask = np.maximum(envelope_diff, 0)

    max_attack = np.max(attack_mask)
    if max_attack > 0:
        attack_mask /= max_attack

    # Apply boost
    gain = 1.0 + attack_mask * 1.5

    # Smooth
    smooth_samples = int(sr * 0.5 / 1000)
    if smooth_samples > 1:
        kernel = np.ones(smooth_samples) / smooth_samples
        gain = np.convolve(gain, kernel, mode='same')

    return data * gain


# ═══════════════════════════════════════════════════════════
#  4. Silence Trimming
# ═══════════════════════════════════════════════════════════

def trim_silence(data: np.ndarray, sr: int, threshold_db: float = -40.0,
                 pad_ms: float = 3.0) -> np.ndarray:
    """앞뒤 무음 제거 (더 타이트한 패딩)"""
    threshold_linear = 10 ** (threshold_db / 20.0)

    window = max(int(sr * 0.001), 1)
    n_windows = len(data) // window
    rms = np.zeros(len(data))
    for i in range(n_windows):
        start = i * window
        end = start + window
        rms_val = np.sqrt(np.mean(data[start:end] ** 2))
        rms[start:end] = rms_val

    above = np.where(rms > threshold_linear)[0]
    if len(above) == 0:
        return data

    pad_samples = int(sr * pad_ms / 1000)
    start = max(0, above[0] - pad_samples)
    end = min(len(data), above[-1] + pad_samples)

    return data[start:end]


# ═══════════════════════════════════════════════════════════
#  5. Peak Normalization
# ═══════════════════════════════════════════════════════════

def normalize_peak(data: np.ndarray, target_db: float = -1.0) -> np.ndarray:
    peak = np.max(np.abs(data))
    if peak == 0:
        return data
    target_linear = 10 ** (target_db / 20.0)
    return data * (target_linear / peak)


# ═══════════════════════════════════════════════════════════
#  메인 처리 파이프라인
# ═══════════════════════════════════════════════════════════

def process_file(mp3_path: str, tmp_dir: str) -> dict:
    basename = os.path.basename(mp3_path)
    wav_in = os.path.join(tmp_dir, basename.replace(".mp3", "_in.wav"))
    wav_out = os.path.join(tmp_dir, basename.replace(".mp3", "_out.wav"))

    mp3_to_wav(mp3_path, wav_in)
    sr, data = read_wav(wav_in)

    original_len = len(data)
    original_peak = np.max(np.abs(data))

    # 1. High-pass filter (100Hz - 더 높은 컷오프)
    data = highpass_filter(data, sr, cutoff=100.0)

    # 2. Muddy 구간 감지 & 제거
    data = remove_muddy_sections(data, sr)

    # 3. 공격적 EQ (wide mud cut + presence + air)
    data = aggressive_eq(data, sr)

    # 4. Transient enhancement
    data = enhance_transients(data, sr)

    # 5. Silence trimming (더 타이트)
    data = trim_silence(data, sr, threshold_db=-40.0, pad_ms=3.0)

    # 6. Peak normalization
    data = normalize_peak(data, target_db=-1.0)

    write_wav(wav_out, sr, data)
    wav_to_mp3(wav_out, mp3_path, bitrate="192k")

    return {
        "file": basename,
        "original_samples": original_len,
        "processed_samples": len(data),
        "original_peak_db": 20 * np.log10(original_peak + 1e-10),
        "processed_peak_db": 20 * np.log10(np.max(np.abs(data)) + 1e-10),
        "trimmed_ms": (original_len - len(data)) / sr * 1000,
        "duration_ms": len(data) / sr * 1000,
    }


def main() -> None:
    # 백업에서 복원 (원본 기반으로 재처리)
    if os.path.isdir(BACKUP_DIR):
        backup_files = glob.glob(os.path.join(BACKUP_DIR, "switch_*.mp3"))
        if backup_files:
            print(f"=== 원본 백업에서 복원 ({len(backup_files)}개) ===")
            for bf in backup_files:
                dest = os.path.join(RAW_DIR, os.path.basename(bf))
                shutil.copy2(bf, dest)
            print("복원 완료\n")

    pattern = os.path.join(RAW_DIR, "switch_*.mp3")
    files = sorted(glob.glob(pattern))

    if not files:
        print(f"ERROR: No MP3 files found in {RAW_DIR}")
        sys.exit(1)

    print(f"=== 도각도각 오디오 프로세싱 v2 ===")
    print(f"대상 파일: {len(files)}개")
    print(f"소스 디렉토리: {RAW_DIR}")
    print()

    print("처리 파이프라인:")
    print("  1. High-pass filter (100Hz)")
    print("  2. Muddy section detection & removal (Spectral Centroid + Mud Ratio)")
    print("  3. Aggressive EQ - Wide mud cut (200-600Hz) + Presence (3-6kHz) + Air (8-12kHz)")
    print("  4. Transient enhancement")
    print("  5. Silence trimming (-40dB, 3ms padding)")
    print("  6. Peak normalization (-1.0dB)")
    print()

    tmp_dir = tempfile.mkdtemp(prefix="dogak_audio_v2_")

    results = []
    for i, f in enumerate(files):
        basename = os.path.basename(f)
        try:
            result = process_file(f, tmp_dir)
            results.append(result)
            print(f"  [{i + 1:3d}/{len(files)}] {basename:<30s} "
                  f"peak: {result['original_peak_db']:+.1f} -> {result['processed_peak_db']:+.1f} dB  "
                  f"dur: {result['duration_ms']:.0f}ms  "
                  f"trimmed: {result['trimmed_ms']:+.0f}ms")
        except Exception as e:
            print(f"  [{i + 1:3d}/{len(files)}] {basename:<30s} ERROR: {e}")

    shutil.rmtree(tmp_dir, ignore_errors=True)

    print()
    print("=== 처리 완료 ===")
    print(f"성공: {len(results)}/{len(files)}개")
    if results:
        durations = [r['duration_ms'] for r in results]
        peaks = [r['processed_peak_db'] for r in results]
        print(f"Duration 범위: {min(durations):.0f}ms ~ {max(durations):.0f}ms (avg: {np.mean(durations):.0f}ms)")
        print(f"Peak 범위: {min(peaks):+.1f}dB ~ {max(peaks):+.1f}dB")
        total_trimmed = sum(r['trimmed_ms'] for r in results)
        print(f"총 트리밍된 시간: {total_trimmed:.0f}ms")


if __name__ == "__main__":
    main()
