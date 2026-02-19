# CLAUDE.md

## Language rule (HIGHEST PRIORITY)

- **Think in English, explain in Korean**: 내부 사고/분석은 영어로, 사용자에게 작업 완료 설명은 한글로.

## Build & Install rule (IMPORTANT)

- **빌드/설치는 사용자가 요청할 때만** 실행. 코드 수정 후 자동으로 빌드하지 않음.

## Auto-commit rule (IMPORTANT)

- 코드 수정 작업이 완료되면 **항상 자동으로 git commit**. 사용자에게 묻지 않음.
- 빌드/설치 여부와 무관하게, 변경사항이 있으면 즉시 커밋.

## Shortcut: ㅂㅂ (IMPORTANT)

- 사용자가 "ㅂㅂ"라고 입력하면 → **빌드 + 설치 + 커밋**을 한 번에 실행.
  1. `./gradlew assembleDebug`
  2. `adb install -r app/build/outputs/apk/debug/Dogakdogak_1.0.0-debug.apk`
  3. 변경사항이 있으면 `git commit`

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
