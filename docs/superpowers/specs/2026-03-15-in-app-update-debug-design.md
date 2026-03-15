# In-App Update Debug Logging Design

## Summary

Add explicit debug logging around Play Core in-app update eligibility so we can tell why the update prompt does not appear on a real device.

## Problem

- The app currently only shows the bottom sheet when an immediate update is both available and allowed.
- When the prompt does not appear, the app gives no evidence about whether:
  - no update is visible
  - immediate updates are disallowed
  - Play Core returned failed update preconditions

## Approach

- Keep the UI behavior unchanged.
- Add a small formatter that converts Play Core precondition codes into readable labels.
- Log the update snapshot each time `checkForUpdate` succeeds:
  - available version code
  - update availability
  - install status
  - immediate update allowed
  - failed preconditions for immediate update

## Verification

- Unit-test the formatter for known precondition codes.
- Run focused in-app update policy tests and settings stability tests.
