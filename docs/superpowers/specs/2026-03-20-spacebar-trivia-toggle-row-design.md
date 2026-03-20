# Spacebar Trivia Toggle Row Design

**Goal:** Simplify the spacebar trivia setting so the card shows only a single subject label and an inline on/off toggle.

## Context

The existing card uses a title, description, status copy, and a custom ON/OFF badge. The toggle behavior already works, but the presentation feels heavier than needed for a simple preference.

## Approved Design

- Keep the existing `GlassCard` placement in the settings screen.
- Remove the card title, helper description, status sentence, and custom ON/OFF badge.
- Render one horizontal row with:
  - left: `스페이스 상식 표시`
  - right: Material `Switch`
- Preserve the existing preference persistence and `refreshTrivia()` behavior when the toggle changes.

## Notes

- This is intentionally a minimal UI-only simplification.
- No preference key or behavior changes are required.
