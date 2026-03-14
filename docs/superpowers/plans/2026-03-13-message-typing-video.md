# Message Typing Video Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 736x1012 video that types a long message into a chat input, plays Pebble 9 typing audio, and sends the text into a final chat bubble.

**Architecture:** Recreate the provided chat screenshot style as a deterministic PIL-rendered layout, generate animation frames for input typing and final sent state, synthesize randomized Pebble 9 audio to match typing cadence, then mux frames and audio into an MP4.

**Tech Stack:** Python 3, PIL, ffmpeg-static, existing Pebble 9 mp3 assets

---

### Task 1: Produce the animation and audio assets

**Files:**
- Create: `tmp/render_message_typing_video.py`
- Create: `tmp/message_typing_video.mp4`
- Create: `tmp/message_typing_audio.mp3`

- [ ] Write a self-contained Python renderer script in `tmp/render_message_typing_video.py`.
- [ ] Generate frame images with the provided message typed into the bottom input bar.
- [ ] Add a final frame state where the message moves into a new received-style chat bubble.
- [ ] Build randomized Pebble 9 typing audio from existing `switch_pebble9_*.mp3` clips.
- [ ] Encode the final video to `tmp/message_typing_video.mp4`.

### Task 2: Verify output

**Files:**
- Verify: `tmp/message_typing_video.mp4`

- [ ] Confirm the video exists and has the expected 736x1012 resolution.
- [ ] Confirm duration is about 10 seconds and audio is present.
- [ ] Share the output path with the user.
