"""
도각도각 키보드 타건음 오디오 프로세싱
- 주파수 정리 (EQ & Filtering)
- 타격감 향상 (Transient Shaping)
- 무음 제거 (Silence Trimming)
- 음량 평준화 (Normalization to -1.0dB peak)
"""

import os
import sys
import glob
import numpy as np
from scipy.signal import butter, sosfilt, sosfiltfilt
from scipy.io import wavfile
import subprocess
import tempfile
import shutil

# ffmpeg 경로
FFMPEG = os.path.expanduser("~/ffmpeg_bin/ffmpeg.exe")

# 소스 디렉토리
RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
RAW_DIR = os.path.normpath(RAW_DIR)

# 백업 디렉토리
BACKUP_DIR = os.path.join(os.path.dirname(__file__), "audio_backup")


def mp3_to_wav(mp3_path: str, wav_path: str):
    """MP3 -> WAV 변환 (ffmpeg 사용)"""
    subprocess.run(
        [FFMPEG, "-y", "-i", mp3_path, "-ar", "44100", "-ac", "1", wav_path],
        capture_output=True, check=True
    )


def wav_to_mp3(wav_path: str, mp3_path: str, bitrate: str = "192k"):
    """WAV -> MP3 변환 (ffmpeg 사용, 고품질)"""
    subprocess.run(
        [FFMPEG, "-y", "-i", wav_path, "-codec:a", "libmp3lame", "-b:a", bitrate, mp3_path],
        capture_output=True, check=True
    )


def read_wav(wav_path: str):
    """WAV 파일 읽기 -> (sample_rate, float64 array [-1, 1])"""
    sr, data = wavfile.read(wav_path)
    if data.dtype == np.int16:
        data = data.astype(np.float64) / 32768.0
    elif data.dtype == np.int32:
        data = data.astype(np.float64) / 2147483648.0
    elif data.dtype == np.float32:
        data = data.astype(np.float64)
    return sr, data


def write_wav(wav_path: str, sr: int, data: np.ndarray):
    """float64 array -> WAV int16 저장"""
    data = np.clip(data, -1.0, 1.0)
    data_int16 = (data * 32767).astype(np.int16)
    wavfile.write(wav_path, sr, data_int16)


# ═══════════════════════════════════════════════════════════
#  1. 주파수 정리 (EQ & Filtering)
# ═══════════════════════════════════════════════════════════

def highpass_filter(data: np.ndarray, sr: int, cutoff: float = 80.0, order: int = 4) -> np.ndarray:
    """80Hz 이하 Low Cut (High-pass filter) - Rumble 제거"""
    sos = butter(order, cutoff, btype='highpass', fs=sr, output='sos')
    return sosfiltfilt(sos, data)


def parametric_eq(data: np.ndarray, sr: int) -> np.ndarray:
    """
    Mud Removal: 300-500Hz 감쇠 (-3dB)
    Clarity Boost: 2kHz-4kHz 부스팅 (+3dB)
    """
    # Mud Removal: 300-500Hz 대역 감쇠
    # Bandpass로 해당 대역 추출 후 원본에서 감쇠
    sos_mud = butter(2, [300, 500], btype='bandpass', fs=sr, output='sos')
    mud_band = sosfiltfilt(sos_mud, data)
    mud_attenuation = 0.5  # -3dB ≈ 0.707, 좀 더 공격적으로 0.5 (-6dB)
    data_eq = data - mud_band * (1.0 - mud_attenuation)

    # Clarity Boost: 2kHz-4kHz 대역 부스팅
    sos_clarity = butter(2, [2000, 4000], btype='bandpass', fs=sr, output='sos')
    clarity_band = sosfiltfilt(sos_clarity, data_eq)
    clarity_boost = 0.6  # +3~4dB 부스트
    data_eq = data_eq + clarity_band * clarity_boost

    return data_eq


# ═══════════════════════════════════════════════════════════
#  2. 타격감 향상 (Transient Shaping)
# ═══════════════════════════════════════════════════════════

def enhance_transients(data: np.ndarray, sr: int) -> np.ndarray:
    """
    Attack 강조: 소리 시작부분의 transient를 날카롭게 만듦.
    엔벨로프 팔로워로 attack 구간 감지 후 증폭.
    """
    # 엔벨로프 추출 (RMS 기반, 짧은 윈도우)
    window_ms = 2  # 2ms 윈도우 (매우 짧은 transient 감지)
    window_samples = max(int(sr * window_ms / 1000), 1)

    # RMS 엔벨로프 계산
    abs_data = np.abs(data)
    envelope = np.zeros_like(data)
    for i in range(len(data)):
        start = max(0, i - window_samples)
        envelope[i] = np.sqrt(np.mean(data[start:i + 1] ** 2))

    # 엔벨로프의 미분 (positive = attack 구간)
    envelope_diff = np.diff(envelope, prepend=0)
    attack_mask = np.maximum(envelope_diff, 0)

    # Attack 마스크 정규화
    max_attack = np.max(attack_mask)
    if max_attack > 0:
        attack_mask = attack_mask / max_attack

    # Attack 구간에 부스트 적용 (최대 +6dB)
    attack_boost = 1.5  # 배율
    gain = 1.0 + attack_mask * attack_boost

    # 짧은 스무딩으로 클릭 방지
    smooth_samples = int(sr * 0.5 / 1000)  # 0.5ms 스무딩
    if smooth_samples > 1:
        kernel = np.ones(smooth_samples) / smooth_samples
        gain = np.convolve(gain, kernel, mode='same')

    return data * gain


# ═══════════════════════════════════════════════════════════
#  3. 무음 제거 (Silence Trimming)
# ═══════════════════════════════════════════════════════════

def trim_silence(data: np.ndarray, sr: int, threshold_db: float = -40.0,
                 pad_ms: float = 5.0) -> np.ndarray:
    """
    앞뒤 무음 제거 (threshold -40dB).
    키를 누르자마자 소리가 나도록 타이트하게 트리밍.
    약간의 패딩(5ms)을 남겨서 클릭 방지.
    """
    threshold_linear = 10 ** (threshold_db / 20.0)
    abs_data = np.abs(data)

    # RMS 기반 감지 (1ms 윈도우)
    window = max(int(sr * 0.001), 1)
    rms = np.zeros(len(data))
    for i in range(0, len(data), window):
        end = min(i + window, len(data))
        rms_val = np.sqrt(np.mean(data[i:end] ** 2))
        rms[i:end] = rms_val

    # 시작점 찾기
    above = np.where(rms > threshold_linear)[0]
    if len(above) == 0:
        return data  # 무음만 있으면 원본 반환

    start = max(0, above[0] - int(sr * pad_ms / 1000))
    end = min(len(data), above[-1] + int(sr * pad_ms / 1000))

    return data[start:end]


# ═══════════════════════════════════════════════════════════
#  4. 음량 평준화 (Normalization to -1.0dB peak)
# ═══════════════════════════════════════════════════════════

def normalize_peak(data: np.ndarray, target_db: float = -1.0) -> np.ndarray:
    """Peak Amplitude를 target_db로 정규화"""
    peak = np.max(np.abs(data))
    if peak == 0:
        return data
    target_linear = 10 ** (target_db / 20.0)
    return data * (target_linear / peak)


# ═══════════════════════════════════════════════════════════
#  메인 처리 파이프라인
# ═══════════════════════════════════════════════════════════

def process_file(mp3_path: str, tmp_dir: str) -> dict:
    """단일 파일 처리 파이프라인"""
    basename = os.path.basename(mp3_path)
    wav_in = os.path.join(tmp_dir, basename.replace(".mp3", "_in.wav"))
    wav_out = os.path.join(tmp_dir, basename.replace(".mp3", "_out.wav"))

    # MP3 -> WAV
    mp3_to_wav(mp3_path, wav_in)
    sr, data = read_wav(wav_in)

    original_len = len(data)
    original_peak = np.max(np.abs(data))

    # 1. High-pass filter (80Hz)
    data = highpass_filter(data, sr, cutoff=80.0)

    # 2. Parametric EQ (Mud removal + Clarity boost)
    data = parametric_eq(data, sr)

    # 3. Transient enhancement
    data = enhance_transients(data, sr)

    # 4. Silence trimming
    data = trim_silence(data, sr, threshold_db=-40.0, pad_ms=5.0)

    # 5. Peak normalization (-1.0dB)
    data = normalize_peak(data, target_db=-1.0)

    # WAV -> MP3 (고품질)
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


def main():
    # 모든 switch_*.mp3 파일 찾기
    pattern = os.path.join(RAW_DIR, "switch_*.mp3")
    files = sorted(glob.glob(pattern))

    if not files:
        print(f"ERROR: No MP3 files found in {RAW_DIR}")
        sys.exit(1)

    print(f"=== 도각도각 오디오 프로세싱 ===")
    print(f"대상 파일: {len(files)}개")
    print(f"소스 디렉토리: {RAW_DIR}")
    print()

    # 백업 생성
    os.makedirs(BACKUP_DIR, exist_ok=True)
    print(f"백업 생성 중... -> {BACKUP_DIR}")
    for f in files:
        shutil.copy2(f, os.path.join(BACKUP_DIR, os.path.basename(f)))
    print(f"백업 완료: {len(files)}개 파일")
    print()

    # 임시 디렉토리
    tmp_dir = tempfile.mkdtemp(prefix="dogak_audio_")
    print(f"임시 디렉토리: {tmp_dir}")
    print()

    # 처리 시작
    print("처리 파이프라인:")
    print("  1. High-pass filter (80Hz) - Rumble 제거")
    print("  2. Parametric EQ - Mud removal (300-500Hz) + Clarity boost (2-4kHz)")
    print("  3. Transient enhancement - Attack 강조")
    print("  4. Silence trimming (-40dB)")
    print("  5. Peak normalization (-1.0dB)")
    print()

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

    # 정리
    shutil.rmtree(tmp_dir, ignore_errors=True)

    # 통계
    print()
    print("=== 처리 완료 ===")
    print(f"성공: {len(results)}/{len(files)}개")
    if results:
        durations = [r['duration_ms'] for r in results]
        peaks = [r['processed_peak_db'] for r in results]
        print(f"Duration 범위: {min(durations):.0f}ms ~ {max(durations):.0f}ms (avg: {np.mean(durations):.0f}ms)")
        print(f"Peak 범위: {min(peaks):+.1f}dB ~ {max(peaks):+.1f}dB")
        total_trimmed = sum(r['trimmed_ms'] for r in results)
        print(f"총 트리밍된 무음: {total_trimmed:.0f}ms")
    print(f"\n원본 백업: {BACKUP_DIR}")


if __name__ == "__main__":
    main()
