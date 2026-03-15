# Ranking Disclosure Modal Refresh Design

## Summary

Refresh the settings-only "랭킹 데이터 안내" modal so it feels aligned with the app's current visual tone instead of a generic system-style dialog.

## Current Problem

- The modal uses a basic `AlertDialog`.
- The content is rendered as a long uninterrupted bullet list.
- The visual hierarchy feels weak compared with the rest of the Dogakdogak UI.

## Desired Outcome

- Keep the same disclosure meaning and links.
- Present the information with stronger visual grouping and better hierarchy.
- Match existing Dogakdogak colors, rounded corners, spacing, and typography.
- Limit the redesign to the settings modal, not the onboarding/settings disclosure cards.

## Recommended Approach

Replace the current settings dialog body with a custom app-themed dialog component.

Structure:

- headline block with short reassurance message
- three compact info sections:
  - what is not stored
  - what is synced for ranking
  - what appears on profile/ranking surfaces
- two app-toned link pills for privacy policy and deletion guide
- one clear close action at the bottom

## Scope

In scope:

- settings ranking disclosure modal
- modal-specific content organization
- small helper data model if useful for testing

Out of scope:

- `RankingDisclosureCard`
- onboarding disclosure flow
- wording changes that alter policy meaning

## Verification

- unit test for modal section data shape
- focused settings activity stability test
- visual review of the new modal hierarchy
