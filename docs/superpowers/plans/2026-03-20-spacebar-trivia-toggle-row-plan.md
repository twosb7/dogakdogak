# Spacebar Trivia Toggle Row Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the verbose spacebar trivia setting card UI with a single-row subject label and inline toggle while keeping existing behavior.

**Architecture:** Keep the change isolated to `SettingsScreen.kt`, extract the subject copy into a small helper to support a focused regression test, and preserve the current preference write plus `refreshTrivia()` call path.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Gradle unit tests

---

### Task 1: Lock the subject copy with a failing test

**Files:**
- Create: `app/src/test/java/helium314/keyboard/latin/dogakdogak/SettingsScreenTextTest.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/SettingsScreen.kt`

- [ ] **Step 1: Write the failing test**

Add a unit test that expects the subject copy to be exactly `스페이스 상식 표시`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "helium314.keyboard.latin.dogakdogak.SettingsScreenTextTest"`
Expected: FAIL because `spacebarTriviaSubject()` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Add `spacebarTriviaSubject()` and use it in the settings UI.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "helium314.keyboard.latin.dogakdogak.SettingsScreenTextTest"`
Expected: PASS

### Task 2: Simplify the settings row UI

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/SettingsScreen.kt`

- [ ] **Step 1: Replace the custom card contents**

Remove the extra title, helper copy, status text, and custom ON/OFF badge.

- [ ] **Step 2: Keep only one row**

Render `스페이스 상식 표시` on the left and a Material `Switch` on the right.

- [ ] **Step 3: Preserve behavior**

Keep the same preference update and `KeyboardSwitcher.getInstance().getMainKeyboardView()?.refreshTrivia()` call on toggle changes.

- [ ] **Step 4: Verify with build-focused checks**

Run:
- `./gradlew :app:testDebugUnitTest --tests "helium314.keyboard.latin.dogakdogak.SettingsScreenTextTest"`
- `./gradlew :app:compileDebugKotlin`

Expected: both commands pass.
