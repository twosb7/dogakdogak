# Developer Suggestion Email Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 화면 하단에 로그인 기반 `개발자에게 건의하기` 액션을 추가하고 메일 앱으로 개발자 메일을 작성할 수 있게 만든다.

**Architecture:** 메일 포맷은 순수 helper로 분리해 단위 테스트로 고정한다. UI는 기존 `SettingsScreen`과 `SettingsActivity` 흐름에 최소 파라미터만 추가해서 로그인 차단, 입력 다이얼로그, 메일 앱 호출을 연결한다.

**Tech Stack:** Kotlin, Jetpack Compose, Android Intent, Robolectric/JUnit

---

## Chunk 1: 메일 포맷 helper

### Task 1: 메일 helper 테스트와 구현

**Files:**
- Create: `app/src/test/java/helium314/keyboard/latin/dogakdogak/DeveloperSuggestionTest.kt`
- Create: `app/src/main/java/helium314/keyboard/latin/dogakdogak/DeveloperSuggestion.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run the test and verify it fails**
- [ ] **Step 3: Implement the minimal helper**
- [ ] **Step 4: Run the helper test and verify it passes**

## Chunk 2: 설정 화면 연결

### Task 2: 설정 UI와 메일 실행 연결

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/SettingsScreen.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/DeveloperSuggestion.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/DogakdogakMainScreen.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt`
- Test: `app/src/test/java/helium314/keyboard/settings/SettingsActivityStabilityTest.kt`
- Test: `app/src/test/java/helium314/keyboard/latin/dogakdogak/DeveloperSuggestionTest.kt`

- [ ] **Step 1: Add the failing UI-facing test coverage that is practical in this repo**
- [ ] **Step 2: Run the targeted tests**
- [ ] **Step 3: Add the settings button reward description, toss-style bottom sheet, and intent launcher**
- [ ] **Step 4: Run targeted tests again**

## Chunk 3: Verification

### Task 3: Final verification

**Files:**
- No file changes required

- [ ] **Step 1: Run `./gradlew testDebugNoMinifyUnitTest --tests 'helium314.keyboard.latin.dogakdogak.DeveloperSuggestionTest' --tests 'helium314.keyboard.settings.SettingsActivityStabilityTest'`**
- [ ] **Step 2: If green, run one broader compilation guard as needed**
