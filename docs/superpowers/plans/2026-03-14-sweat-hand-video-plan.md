# Sweat Hand Video Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a silent 1080x1920 looping MP4 where the sweat drops repeatedly animate and only the hand plus mouse cable shake rapidly.

**Architecture:** Use a small PIL-based renderer that extracts the sweat area and the hand/cable area from the padded source image, keeps the rest of the image static, animates the sweat with phased opacity/position/scale changes, animates the hand layer with fast loop-safe translation and light rotation, then encodes the frames to a 4-second H.264 MP4 via ffmpeg.

**Tech Stack:** Python 3, PIL, ffmpeg

---

### Task 1: Build the renderer

**Files:**
- Create: `tools/render_sweat_hand_video.py`

- [ ] Load the padded source PNG and validate its size.
- [ ] Define the sweat and hand/cable bounding boxes.
- [ ] Extract those regions with transparency masks.
- [ ] Blank the moving regions out of the static background.
- [ ] Animate the sweat drops with phased loop-safe motion.
- [ ] Animate the hand/cable layer with rapid loop-safe jitter.

### Task 2: Produce the MP4

**Files:**
- Create: `tmp/sweat-hand-frames/*.png`
- Create: `tmp/sweat-hand-loop.mp4`

- [ ] Render 4 seconds at 30 fps.
- [ ] Encode the frames to silent H.264 MP4.
- [ ] Use `yuv420p` for compatibility.

### Task 3: Verify output

**Files:**
- Verify: `tmp/sweat-hand-loop.mp4`

- [ ] Confirm the video exists.
- [ ] Confirm the resolution is `1080x1920`.
- [ ] Confirm the duration is about `4` seconds and there is no audio stream.
