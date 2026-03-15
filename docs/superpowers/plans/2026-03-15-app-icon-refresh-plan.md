# App Icon Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace launcher and in-app brand icons with the user-provided combo keyboard artwork.

**Architecture:** Generate a transparent foreground asset for adaptive launchers and a cream-backed square asset for legacy launcher icons and in-app reuse. Preserve existing resource names so all current references keep working.

**Tech Stack:** Android resources, mipmap/drawable assets, Python Pillow, XML drawables

---

### Task 1: Generate refreshed icon assets

**Files:**
- Modify: `app/src/main/res/mipmap-*/ic_launcher.webp`
- Modify: `app/src/main/res/mipmap-*/ic_launcher_round.webp`
- Modify: `app/src/main/res/mipmap-*/ic_launcher_foreground.webp`
- Modify: `app/src/main/res/drawable/dogakdogak_icon.webp`

- [ ] **Step 1: Create the transparent foreground master from the supplied image**
- [ ] **Step 2: Create the cream-backed master for legacy/in-app use**
- [ ] **Step 3: Export all required density variants with existing filenames**

### Task 2: Align adaptive icon background

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable-v24/ic_launcher_background.xml`

- [ ] **Step 1: Replace the old default background with a warm cream tone**
- [ ] **Step 2: Keep adaptive icon XML references unchanged**

### Task 3: Verify generated assets

**Files:**
- Verify only

- [ ] **Step 1: Visually inspect generated icon preview images**
- [ ] **Step 2: Confirm resource files exist in every expected density path**
- [ ] **Step 3: Commit only icon refresh changes**
