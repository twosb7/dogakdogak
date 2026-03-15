# In-App Update Prompt Design

## Summary

Add a Google Play in-app update prompt when the app opens. Show a custom bottom sheet before starting the update flow, allow the user to defer, and suppress repeat prompts according to the agreed version-based rules.

## Product Behavior

- Check for updates when the user opens the launcher activity (`SettingsActivity`).
- If a Google Play immediate update is available, show a bottom sheet with:
  - title/message equivalent to: "업데이트가 있습니다. 3초 만에 깔아볼까요?"
  - primary CTA: `업데이트 할게요`
  - secondary CTA: `다음에 할게요`
- `업데이트 할게요`
  - briefly switches the sheet into a small loading/progress state such as `업데이트 준비 중...`
  - then starts the Google Play Immediate update flow
- `다음에 할게요`
  - hides the prompt for the rest of the current day for that version
  - if pressed again on a later day for the same available version, permanently suppresses that version
  - the sheet appears again only when a newer `availableVersionCode` is seen

## Important Constraint

This feature uses Google Play Immediate updates, not Flexible updates.

That means:

- the custom bottom sheet can show a short "starting update" loading state
- the real download/install progress is controlled and displayed by Google Play's own update UI
- we should not promise an app-managed percentage progress bar during the immediate flow

Official references:

- https://developer.android.com/guide/app-bundle/in-app-updates
- https://developer.android.com/guide/playcore/in-app-updates/kotlin-java

## UX Notes

- The `업데이트 할게요` button should be visually dominant.
- The defer action should remain clearly available but less visually prominent.
- The bottom sheet should feel lightweight and slightly animated, like it slides up to invite action.
- If an update is already in progress and the app resumes, the app should re-enter the immediate update flow instead of showing the sheet again.

## State Model

Persist these pieces of state in SharedPreferences:

- latest version prompted
- date when that version was deferred
- version permanently suppressed after multi-day defer

Rules:

- same day, same version, already deferred once: do not show
- later day, same version, deferred again: mark that version permanently suppressed
- newer version than suppressed version: show again

## Scope

In scope:

- Play Core dependency and update coordinator
- bottom sheet UI
- defer suppression policy
- immediate update launch/re-entry handling

Out of scope:

- server-driven minimum version enforcement
- flexible update progress UI
- non-Play distribution channels

## Verification

- unit tests for defer suppression rules
- update availability gating tests where feasible
- manual validation path for launcher start -> sheet -> immediate update handoff
