# Ranking Sync Dedup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the last successful ranking sync payload and skip duplicate score/touch sync RPCs across login, ranking screen, and background sync flows.

**Architecture:** Keep deduplication centralized inside `RankingRepository` by adding a persisted snapshot store and routing all four sync methods through it. Use constructor injection for `SharedPreferences` and current-user lookup so repository tests can verify skip and retry behavior without real network calls.

**Tech Stack:** Kotlin, SharedPreferences, Robolectric/JUnit, kotlinx-coroutines-test

---

## Chunk 1: Repository Test Harness

### Task 1: Add failing repository tests for dedup behavior

**Files:**
- Modify: `app/src/test/java/helium314/keyboard/latin/dogakdogak/RankingRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run the focused test command and confirm failure**
  Run: `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.RankingRepositoryTest`
- [ ] **Step 3: Cover scalar sync dedup, changed payload retry, and failed RPC retry**

## Chunk 2: Repository Dedup Store

### Task 2: Add persisted snapshot support to RankingRepository

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/RankingRepository.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/dogakdogak/PrefsKeys.kt`

- [ ] **Step 1: Add sync snapshot keys and repository constructor injection for snapshot prefs/current user lookup**
- [ ] **Step 2: Add minimal helper logic to build, compare, and persist scalar/map snapshots**
- [ ] **Step 3: Route `syncDailyClicks`, `syncDailyTouches`, `syncAppDailyClicks`, and `syncAppDailyTouches` through the helper**
- [ ] **Step 4: Only persist snapshots after successful RPC completion**

## Chunk 3: Verification

### Task 3: Run focused verification and commit

**Files:**
- Verify only

- [ ] **Step 1: Re-run `RankingRepositoryTest` and confirm pass**
- [ ] **Step 2: Re-run related ranking tests**
  Run: `./gradlew testDebugUnitTest --tests helium314.keyboard.latin.dogakdogak.RankingRepositoryTest --tests helium314.keyboard.latin.dogakdogak.RankingSyncWorkerTest --tests helium314.keyboard.latin.dogakdogak.AppClickCountRepositoryTest`
- [ ] **Step 3: Commit only the dedup-related files**
