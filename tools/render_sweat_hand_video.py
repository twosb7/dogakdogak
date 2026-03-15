#!/usr/bin/env python3
from __future__ import annotations

import math
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter


SOURCE_PATH = Path("/Users/twosb/Documents/new/image-9x16-upscaled.png")
OUTPUT_DIR = Path("tmp/sweat-hand-frames")
OUTPUT_VIDEO = Path("tmp/sweat-hand-loop.mp4")
FPS = 30
DURATION_SECONDS = 4
TOTAL_FRAMES = FPS * DURATION_SECONDS
CANVAS_SIZE = (1080, 1920)
BACKGROUND_COLOR = (255, 255, 255, 255)


def whiteness_mask(crop: Image.Image, threshold: int = 14) -> Image.Image:
    rgba = crop.convert("RGBA")
    white_bg = Image.new("RGBA", rgba.size, BACKGROUND_COLOR)
    diff = ImageChops.difference(rgba, white_bg).convert("L")
    return diff.point(lambda value: 0 if value <= threshold else min(255, (value - threshold) * 4))


def extract_layer(source: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    crop = source.crop(box).convert("RGBA")
    crop.putalpha(whiteness_mask(crop))
    return crop


def paste_rotated(base: Image.Image, layer: Image.Image, xy: tuple[int, int], angle: float) -> None:
    rotated = layer.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    x = round(xy[0] - (rotated.width - layer.width) / 2)
    y = round(xy[1] - (rotated.height - layer.height) / 2)
    base.alpha_composite(rotated, (x, y))


def loop_t(frame_index: int) -> float:
    return frame_index / max(1, TOTAL_FRAMES - 1)


def hand_motion(frame_index: int) -> tuple[float, float, float]:
    t = loop_t(frame_index)
    dx = 7.5 * math.sin(2 * math.pi * (20 * t + 0.15))
    dx += 3.0 * math.sin(2 * math.pi * (41 * t + 0.31))
    dy = 5.0 * math.sin(2 * math.pi * (24 * t + 0.07))
    dy += 2.0 * math.sin(2 * math.pi * (37 * t + 0.21))
    angle = 1.5 * math.sin(2 * math.pi * (18 * t + 0.11))
    return dx, dy, angle


def sweat_transform(frame_index: int, phase: float) -> tuple[float, float, float, int]:
    t = loop_t(frame_index)
    local = (t + phase) % 1.0
    eased = 0.5 - 0.5 * math.cos(local * math.pi)
    dx = -3.0 * eased
    dy = 18.0 * eased
    scale = 0.72 + 0.36 * math.sin(local * math.pi)
    alpha = int(40 + 215 * math.sin(local * math.pi))
    return dx, dy, scale, alpha


def build_static_background(source: Image.Image, blank_boxes: list[tuple[int, int, int, int]]) -> Image.Image:
    static_bg = source.copy()
    for x1, y1, x2, y2 in blank_boxes:
        static_bg.paste(BACKGROUND_COLOR, (x1, y1, x2, y2))
    return static_bg


def main() -> None:
    if not SOURCE_PATH.exists():
        raise FileNotFoundError(f"Missing source image: {SOURCE_PATH}")
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("ffmpeg is required but was not found in PATH.")

    source = Image.open(SOURCE_PATH).convert("RGBA")
    if source.size != CANVAS_SIZE:
        raise ValueError(f"Expected source size {CANVAS_SIZE}, got {source.size}")

    sweat_box = (22, 690, 300, 885)
    hand_box = (540, 1040, 1040, 1405)

    sweat_layer = extract_layer(source, sweat_box)
    hand_layer = extract_layer(source, hand_box)

    static_bg = build_static_background(
        source,
        [
            (sweat_box[0] - 10, sweat_box[1] - 10, sweat_box[2] + 10, sweat_box[3] + 10),
            (hand_box[0] - 12, hand_box[1] - 12, hand_box[2] + 12, hand_box[3] + 12),
        ],
    )

    sweat_drops = [
        ((15, 42, 72, 90), 0.00),
        ((92, 5, 150, 52), 0.28),
        ((182, 28, 242, 86), 0.55),
    ]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for old_frame in OUTPUT_DIR.glob("frame-*.png"):
        old_frame.unlink()

    for frame_index in range(TOTAL_FRAMES):
        frame = static_bg.copy()

        # Sweat animation: render each drop separately with phase offsets.
        for crop_box, phase in sweat_drops:
            drop = sweat_layer.crop(crop_box)
            dx, dy, scale, alpha = sweat_transform(frame_index, phase)
            new_size = (
                max(1, round(drop.width * scale)),
                max(1, round(drop.height * scale)),
            )
            scaled = drop.resize(new_size, Image.Resampling.LANCZOS)
            alpha_mask = scaled.getchannel("A").point(lambda value: value * alpha // 255)
            scaled.putalpha(alpha_mask)
            base_x = sweat_box[0] + crop_box[0]
            base_y = sweat_box[1] + crop_box[1]
            x = round(base_x + dx - (new_size[0] - drop.width) / 2)
            y = round(base_y + dy - (new_size[1] - drop.height) / 2)
            frame.alpha_composite(scaled, (x, y))

        dx, dy, angle = hand_motion(frame_index)
        paste_rotated(frame, hand_layer, (round(hand_box[0] + dx), round(hand_box[1] + dy)), angle)
        frame = frame.filter(ImageFilter.UnsharpMask(radius=0.8, percent=110, threshold=2))
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
    main()
