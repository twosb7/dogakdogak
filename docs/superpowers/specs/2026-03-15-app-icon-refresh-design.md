# App Icon Refresh Design

## Summary

Replace the launcher icon set and in-app Dogakdogak brand icon with the new keyboard combo artwork supplied by the user.

## Visual Direction

- Use the artwork itself as the brand motif.
- Remove the flat white background from the launcher foreground so the adaptive icon feels native.
- Use a warm cream background behind the asset for legacy launcher icons and in-app branding surfaces.

## Scope

In scope:

- launcher mipmap icons
- adaptive icon background color
- in-app `dogakdogak_icon` asset

Out of scope:

- Play Store listing graphics
- unrelated toolbar/system icons

## Implementation Notes

- Generate one transparent foreground asset for adaptive icons.
- Generate one cream-backed square asset for in-app and legacy launcher usage.
- Keep existing file names so code references do not need to change.

## Verification

- Confirm all launcher icon files are regenerated.
- Confirm in-app `dogakdogak_icon` is replaced.
- Spot-check the generated assets visually.
