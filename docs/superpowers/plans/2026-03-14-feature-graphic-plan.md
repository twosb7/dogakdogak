# Feature Graphic Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a cleaner Play Store feature graphic that matches the existing app icon style.

**Architecture:** Reuse the current icon asset as the visual anchor, generate a soft pastel banner composition with PIL, and export directly to the existing Play Store feature graphic path.

**Tech Stack:** Python 3, PIL, existing PNG assets

---

### Task 1: Build a reproducible graphic renderer

**Files:**
- Create: `tools/generate_feature_graphic.py`
- Modify: `fastlane/metadata/android/en-US/images/featureGraphic.png`

- [ ] **Step 1: Write the renderer script**
- [ ] **Step 2: Generate the graphic with icon, title, and ASMR pill**
- [ ] **Step 3: Export to the Play Store feature graphic path**

### Task 2: Verify the output

**Files:**
- Verify: `fastlane/metadata/android/en-US/images/featureGraphic.png`

- [ ] **Step 1: Check final dimensions are 1024x500**
- [ ] **Step 2: Review visual tone against the icon**
- [ ] **Step 3: Share the output path and summary**
