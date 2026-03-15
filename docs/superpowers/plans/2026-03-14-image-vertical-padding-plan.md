# Image Vertical Padding Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the supplied image into a 1080x1920 PNG with white padding and improved apparent resolution.

**Architecture:** Load the source PNG with PIL, resize it proportionally to fit inside a 1080x1920 canvas, place it centered on a white background, apply a light sharpening pass, and save the result as a new PNG next to the source file.

**Tech Stack:** Python 3, PIL

---

### Task 1: Generate the padded image

**Files:**
- Create: `/Users/twosb/Documents/new/image-9x16-upscaled.png`

- [ ] Load the source PNG and validate its dimensions.
- [ ] Resize the source proportionally to fit within `1080x1920`.
- [ ] Place the resized image on a white `1080x1920` canvas.
- [ ] Apply a light sharpening pass and save the PNG.

### Task 2: Verify output

**Files:**
- Verify: `/Users/twosb/Documents/new/image-9x16-upscaled.png`

- [ ] Confirm the file exists.
- [ ] Confirm the output resolution is `1080x1920`.
