# Ranking Disclosure Modal Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain settings ranking disclosure alert with an app-themed dialog that feels consistent with the current Dogakdogak UI.

**Architecture:** Keep the disclosure copy source centralized in `PolicyDisclosure.kt`, add a small modal-specific section builder for testability, and swap the settings screen from a stock `AlertDialog` to a custom dialog surface rendered with existing Dogakdogak colors and typography.

**Tech Stack:** Jetpack Compose Material3, custom Compose dialog UI, Robolectric/JUnit

---

### Task 1: Add failing tests for modal content structure

**Files:**
- Modify: `app/src/test/java/helium314/keyboard/latin/dogakdogak/PolicyDisclosureTest.kt`

- [ ] **Step 1: Write failing tests for the new modal section data**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.PolicyDisclosureTest` and confirm failure**
- [ ] **Step 3: Implement the minimal modal content builder to satisfy the tests**

### Task 2: Build the custom ranking disclosure modal

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/PolicyDisclosure.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/SettingsScreen.kt`

- [ ] **Step 1: Add a modal-specific data model and section builder**
- [ ] **Step 2: Add a custom app-themed dialog composable in `PolicyDisclosure.kt`**
- [ ] **Step 3: Update settings to use the new dialog instead of `AlertDialog`**
- [ ] **Step 4: Preserve the privacy/deletion external links and close action**

### Task 3: Verify and commit

**Files:**
- Verify only

- [ ] **Step 1: Re-run `PolicyDisclosureTest`**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests helium314.keyboard.settings.SettingsActivityStabilityTest --tests helium314.keyboard.latin.dogakdogak.PolicyDisclosureTest`**
- [ ] **Step 3: Commit only the modal refresh changes**
