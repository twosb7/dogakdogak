# Flexible In-App Update Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the current immediate in-app update flow to a smaller flexible-update prompt with a post-download restart banner.

**Architecture:** Extend the Play Core coordinator to expose flexible eligibility and install-state events, keep the suppression policy intact, and drive the UI from explicit Compose state in `SettingsActivity`. Split prompt UI and downloaded-state UI into separate composables so the launcher prompt can stay compact while the restart banner remains lightweight.

**Tech Stack:** Android Play Core in-app updates, Jetpack Compose Material3, SharedPreferences, Robolectric/JUnit

---

### Task 1: Add failing tests for flexible update decision logic

**Files:**
- Modify: `app/src/test/java/helium314/keyboard/latin/dogakdogak/InAppUpdatePolicyTest.kt`

- [ ] **Step 1: Add failing tests for flexible prompt eligibility and downloaded-state restart behavior**
- [ ] **Step 2: Run the focused policy test command and confirm failure**
  Run: `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.InAppUpdatePolicyTest`
- [ ] **Step 3: Implement the minimal decision logic to pass**

### Task 2: Extend the Play Core coordinator

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/InAppUpdateCoordinator.kt`
- Modify: `app/src/test/java/helium314/keyboard/latin/dogakdogak/InAppUpdateCoordinatorTest.kt`

- [ ] **Step 1: Add flexible eligibility and install-state fields to the availability model**
- [ ] **Step 2: Start flexible update flow instead of immediate**
- [ ] **Step 3: Add install state listener registration/unregistration and complete-update support**
- [ ] **Step 4: Keep debug logging useful for future troubleshooting**

### Task 3: Refresh the UI

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/InAppUpdateSheet.kt`
- Create or Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/...` (restart banner composable if split out)
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt`

- [ ] **Step 1: Shrink the prompt sheet and change copy to `설치 해볼까요?`**
- [ ] **Step 2: Wire the primary CTA to flexible download start**
- [ ] **Step 3: Show a compact downloaded-state restart banner**
- [ ] **Step 4: Trigger `completeUpdate()` from the restart CTA**

### Task 4: Verify and commit

**Files:**
- Verify only

- [ ] **Step 1: Re-run focused update tests**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.InAppUpdateCoordinatorTest --tests helium314.keyboard.latin.dogakdogak.InAppUpdatePolicyTest --tests helium314.keyboard.settings.SettingsActivityStabilityTest`**
- [ ] **Step 3: Commit only the flexible-update changes**
