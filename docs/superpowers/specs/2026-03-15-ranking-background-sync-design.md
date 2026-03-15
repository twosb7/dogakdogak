# Ranking Background Sync Design

## Summary

Change the ranking background sync policy so that any user who is still logged in can be synchronized periodically, even if they have never opened the ranking tab.

## Current Behavior

- `LatinIME` schedules `RankingSyncWorker` once per hour when the app process starts.
- `RankingSyncWorker` currently skips unless:
  - the user is logged in, and
  - the ranking screen was visited within the last 7 days.
- As a result, users who stay logged in but never open the ranking tab only get ranking data uploaded during login-time sync.

## Desired Behavior

- Keep the existing hourly WorkManager schedule.
- Keep the existing login check.
- Remove the ranking-screen visit requirement.
- Logged-in users should continue to upload score/touch totals every periodic sync window, even if ranking screen visit count is zero.

## Recommended Approach

Extract the worker eligibility rule into a small internal helper and cover it with unit tests.

Why this approach:

- It keeps the behavior change localized to the worker.
- It makes the policy explicit and testable.
- It avoids touching ranking UI, preferences, or scheduling code.

## Trade-Offs

Pros:

- Ranking freshness improves for logged-in users who never visit the ranking tab.
- Behavior matches the product expectation more closely.

Cons:

- More periodic sync attempts for logged-in users.
- More network and server load than the previous opt-in-by-visit behavior.

## Scope

In scope:

- Worker eligibility logic
- Unit tests for the new eligibility behavior

Out of scope:

- Changing the 1-hour schedule
- Changing login-time sync
- Removing `LAST_RANKING_VISIT` writes from the ranking screen

## Verification

- Add a test that proves authenticated users are eligible for periodic sync even with no ranking visit.
- Add a test that unauthenticated users are still excluded.
- Run the focused worker test class after the code change.
