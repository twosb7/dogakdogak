# CLAUDE.md

## Language rule (HIGHEST PRIORITY)

- **Think in English, explain in Korean**: 내부 사고/분석은 영어로, 사용자에게 작업 완료 설명은 한글로.

## Auto-commit rule (IMPORTANT)

After a successful `adb install` (APK installed on device), **always automatically git commit** all staged/unstaged changes without asking the user. Include a descriptive commit message summarizing all changes made in that session.

## Project info

- Android keyboard app (HeliBoard fork) with ASMR sound effects
- Package: `com.dogakdogak.keyboard`
- Build: `./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/Dogakdogak_1.0.0-debug.apk`
- Debug SHA-1: `EF:C1:C3:CC:03:E3:1A:F2:4A:00:FF:51:CC:5E:EA:1D:7F:13:34:7B`
- `local.properties` is gitignored and contains SDK path + dummy release signing passwords for debug builds

## Windows 환경 주의사항

- `adb logcat` 출력을 `findstr`로 필터링하면 한글이 깨짐 → 대신 PowerShell `Select-String`을 사용하거나, `adb logcat`을 파일로 리다이렉트 후 UTF-8로 읽기
- 예: `adb logcat -d -t 200 2>&1 | Out-String` 또는 `adb logcat -d > log.txt` 후 `Select-String -Path log.txt -Pattern "keyword"`
