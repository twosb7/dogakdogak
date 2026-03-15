# In-App Update Prompt Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a custom launcher-time update bottom sheet for Google Play Immediate updates, support per-version defer suppression, and hand off install/restart to Google Play.

**Architecture:** Add a small Play Core wrapper plus a local suppression-policy component. `SettingsActivity` will query update availability on app open, show a Compose bottom sheet when eligible, and launch the Google Play Immediate update flow from the primary CTA. Suppression rules will be persisted in SharedPreferences and verified with unit tests before wiring the UI.

**Tech Stack:** Android Play Core in-app updates, Compose Material3 bottom sheet, SharedPreferences, Kotlin unit tests

---

## Chunk 1: Update Policy

### Task 1: Add failing tests for defer suppression rules

**Files:**
- Modify: `app/src/test/java/helium314/keyboard/latin/dogakdogak/...` (new update policy test file)

- [ ] **Step 1: Write failing tests for same-day hide, multi-day same-version suppression, and newer-version re-show**
- [ ] **Step 2: Run the focused test command and confirm failure**
  Run: `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.InAppUpdatePolicyTest`
- [ ] **Step 3: Implement the minimal policy object to pass the tests**

## Chunk 2: Play Core Integration

### Task 2: Add Play Core dependency and update coordinator

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/helium314/keyboard/latin/dogakdogak/InAppUpdateCoordinator.kt`

- [ ] **Step 1: Add the Google Play in-app update dependency**
- [ ] **Step 2: Add a wrapper that exposes update availability, available version code, and immediate-flow resume handling**
- [ ] **Step 3: Keep Google Play specifics out of the UI layer**

## Chunk 3: Launcher UI

### Task 3: Add bottom sheet state and handoff in SettingsActivity

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt`
- Create or Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/...` (bottom sheet UI helper if needed)
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/PrefsKeys.kt`

- [ ] **Step 1: Add persisted prefs keys for update prompt suppression**
- [ ] **Step 2: Query update availability when the launcher activity opens**
- [ ] **Step 3: Show a custom `ModalBottomSheet` only when policy says it should appear**
- [ ] **Step 4: Style `업데이트 할게요` as the dominant CTA and `다음에 할게요` as secondary**
- [ ] **Step 5: On primary CTA, switch the sheet into a short loading state and then launch the immediate update flow**
- [ ] **Step 6: On secondary CTA, persist the defer/suppress rule for the current available version**

## Chunk 4: Resume Handling

### Task 4: Resume update flow when an immediate update is already in progress

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/InAppUpdateCoordinator.kt`

- [ ] **Step 1: Re-check update state on resume**
- [ ] **Step 2: If Play reports an in-progress immediate update, resume it instead of re-showing the sheet**
- [ ] **Step 3: Ensure the custom sheet does not conflict with the resumed Play UI**

## Chunk 5: Verification

### Task 5: Run targeted verification and commit

**Files:**
- Verify only

- [ ] **Step 1: Re-run the update policy tests**
- [ ] **Step 2: Run related activity/UI regression tests where feasible**
- [ ] **Step 3: Confirm no unrelated files are staged**
- [ ] **Step 4: Commit only the in-app update changes**
