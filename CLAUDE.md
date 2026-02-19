# CLAUDE.md

## Auto-commit rule (IMPORTANT)

After a successful `adb install` (APK installed on device), **always automatically git commit** all staged/unstaged changes without asking the user. Include a descriptive commit message summarizing all changes made in that session.

## Project info

- Android keyboard app (HeliBoard fork) with ASMR sound effects
- Package: `com.dogakdogak.keyboard`
- Build: `./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/Dogakdogak_1.0.0-debug.apk`
- Debug SHA-1: `EF:C1:C3:CC:03:E3:1A:F2:4A:00:FF:51:CC:5E:EA:1D:7F:13:34:7B`
- `local.properties` is gitignored and contains SDK path + dummy release signing passwords for debug builds
