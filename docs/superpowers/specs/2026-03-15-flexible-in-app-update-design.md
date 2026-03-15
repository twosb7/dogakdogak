# Flexible In-App Update Design

## Summary

Switch the app from Google Play Immediate in-app updates to Flexible in-app updates so the update can download while the user continues using the app, then prompt for restart once the download completes.

## Desired UX

- On launcher open, show a smaller update bottom sheet.
- Message:
  - title: `업데이트가 있습니다`
  - subtitle: `3초 만에 설치 해볼까요?`
- Primary CTA remains visually dominant.
- Secondary defer CTA keeps the existing per-version suppression behavior.
- Tapping the primary CTA starts a Google Play Flexible update flow.
- While downloading, the user can continue using the app.
- When the update is downloaded, show a compact bottom banner with:
  - `업데이트가 다운로드되었습니다.`
  - primary restart CTA: `재시작`
- Tapping restart calls `completeUpdate()`.

## Important Product Change

- This no longer blocks app usage during update download.
- This is a UX trade-off in exchange for the in-app download/restart flow the user requested.

## Technical Approach

- Update the coordinator to:
  - expose flexible-update eligibility
  - start flexible update flow
  - listen for install state changes
  - surface downloaded state for `completeUpdate()`
- Keep defer policy unchanged.
- Replace immediate-update decision logic with flexible-update decision logic.
- Add a separate compact restart banner composable for the downloaded state.

## Scope

In scope:

- Play Core coordinator changes
- settings activity state handling
- smaller prompt sheet
- downloaded/restart banner

Out of scope:

- changing ranking/update defer policy rules
- changing app store release process

## Verification

- unit tests for new flexible action resolution
- focused settings activity stability test
- manual device check for:
  - prompt shown
  - flexible download starts
  - restart banner shown after download
