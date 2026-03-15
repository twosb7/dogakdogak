#!/usr/bin/env python3
from __future__ import annotations

import math
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageFilter


SOURCE_PATH = Path("/Users/twosb/Documents/new/image2.png")
OUTPUT_DIR = Path("tmp/laughing-meme-frames")
OUTPUT_VIDEO = Path("tmp/laughing-meme-loop.mp4")
FPS = 30
DURATION_SECONDS = 4
TOTAL_FRAMES = FPS * DURATION_SECONDS
CANVAS_SIZE = (1080, 1920)
BACKGROUND_COLOR = (255, 255, 255, 255)


def layer_motion(frame_index: int, amp_x: float, amp_y: float, rot: float, phase: float, cycles_x: int, cycles_y: int) -> tuple[float, float, float]:
    cycle_span = max(1, TOTAL_FRAMES - 1)
    t = frame_index / cycle_span
    dx = amp_x * math.sin(2 * math.pi * (cycles_x * t + phase))
    dx += amp_x * 0.45 * math.sin(2 * math.pi * ((cycles_x * 2) * t + phase / 2))
    dy = amp_y * math.sin(2 * math.pi * (cycles_y * t + phase / 1.7))
    dy += amp_y * 0.35 * math.sin(2 * math.pi * ((cycles_y * 2) * t + phase / 3))
    angle = rot * math.sin(2 * math.pi * ((cycles_x + cycles_y) * t + phase / 2.3))
    return dx, dy, angle


def upscale_to_canvas(source: Image.Image) -> Image.Image:
    src_w, src_h = source.size
    dst_w, dst_h = CANVAS_SIZE
    scale = dst_w / src_w
    resized = source.resize((dst_w, round(src_h * scale)), Image.Resampling.LANCZOS)
    resized = resized.filter(ImageFilter.UnsharpMask(radius=1.6, percent=140, threshold=2))
    offset_y = (dst_h - resized.height) // 2
    canvas = Image.new("RGBA", CANVAS_SIZE, BACKGROUND_COLOR)
    canvas.alpha_composite(resized, (0, offset_y))
    return canvas


def paste_rotated(base: Image.Image, layer: Image.Image, xy: tuple[int, int], angle: float) -> None:
    rotated = layer.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    x = round(xy[0] - (rotated.width - layer.width) / 2)
    y = round(xy[1] - (rotated.height - layer.height) / 2)
    base.alpha_composite(rotated, (x, y))


def render_video() -> None:
    if not SOURCE_PATH.exists():
        raise FileNotFoundError(f"Missing source image: {SOURCE_PATH}")
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("ffmpeg is required but was not found in PATH.")

    source = Image.open(SOURCE_PATH).convert("RGBA")
    if source.size != (588, 886):
        print(f"Using source size {source.size}, not the originally expected 588x886.")

    canvas = upscale_to_canvas(source)
    motion_layer = canvas.resize((CANVAS_SIZE[0] + 72, CANVAS_SIZE[1] + 72), Image.Resampling.LANCZOS)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for old_frame in OUTPUT_DIR.glob("frame-*.png"):
        old_frame.unlink()

    motion = (13.0, 11.0, 1.4, 0.16, 11, 13)

    for frame_index in range(TOTAL_FRAMES):
        frame = Image.new("RGBA", CANVAS_SIZE, BACKGROUND_COLOR)
        dx, dy, angle = layer_motion(frame_index, *motion)
        paste_rotated(frame, motion_layer, (-36 + round(dx), -36 + round(dy)), angle)
        frame.save(OUTPUT_DIR / f"frame-{frame_index:04d}.png")

    if OUTPUT_VIDEO.exists():
        OUTPUT_VIDEO.unlink()

    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-framerate",
            str(FPS),
            "-i",
            str(OUTPUT_DIR / "frame-%04d.png"),
            "-vf",
            "format=yuv420p",
            "-c:v",
            "libx264",
            "-preset",
            "slow",
            "-crf",
            "17",
            "-movflags",
            "+faststart",
            str(OUTPUT_VIDEO),
        ],
        check=True,
    )


if __name__ == "__main__":
    render_video()
