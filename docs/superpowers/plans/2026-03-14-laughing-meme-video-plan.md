# Laughing Meme Video Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the supplied meme image into a silent 1080x1920 looping MP4 with fast jitter applied to the entire visible frame.

**Architecture:** Use a self-contained Python renderer built on PIL to upscale the source image into a 1080x1920 canvas, treat that canvas as a single moving layer, apply loop-safe sinusoidal translation and light rotation to the full frame, write PNG frames, then encode them into a 4-second H.264 MP4 with ffmpeg.

**Tech Stack:** Python 3, PIL, ffmpeg

---

### Task 1: Build the renderer

**Files:**
- Create: `tools/render_laughing_meme_video.py`

- [ ] Load the user-provided PNG and validate source dimensions.
- [ ] Upscale the source image into a `1080x1920` white canvas while preserving the drawing layout.
- [ ] Create a slightly oversized working layer so whole-frame motion does not reveal empty edges.
- [ ] Animate the full image layer with loop-safe sinusoidal offsets and light rotation.

### Task 2: Produce the MP4

**Files:**
- Create: `tmp/laughing-meme-frames/*.png`
- Create: `tmp/laughing-meme-loop.mp4`

- [ ] Render `4` seconds of frames at `30fps`.
- [ ] Encode the frames into a silent H.264 MP4 using `ffmpeg`.
- [ ] Use `yuv420p` pixel format for broad compatibility.

### Task 3: Verify output

**Files:**
- Verify: `tmp/laughing-meme-loop.mp4`

- [ ] Confirm the file exists.
- [ ] Confirm the resolution is `1080x1920`.
- [ ] Confirm the duration is about `4` seconds and the video has no audio stream.
