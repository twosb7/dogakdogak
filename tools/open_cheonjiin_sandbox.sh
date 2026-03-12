#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/Dogakdogak_1.0.14-debug.apk"
IME_ID="com.dogakdogak.keyboard/helium314.keyboard.latin.LatinIME"
ACTIVITY="com.dogakdogak.keyboard/helium314.keyboard.settings.ImeSandboxActivity"
CHEONJIIN_SUBTYPE_ID="1164772596"

prefill_text=""
prefill_codepoints=""
cursor_position=""
install_apk="false"

ensure_cheonjiin_ime() {
  adb shell ime enable "$IME_ID" >/dev/null
  adb shell ime set "$IME_ID" >/dev/null
  adb shell settings put secure default_input_method "$IME_ID" >/dev/null
  adb shell settings put secure selected_input_method_subtype "$CHEONJIIN_SUBTYPE_ID" >/dev/null
}

ime_is_ready() {
  local dump
  dump="$(adb shell dumpsys input_method | tr -d '\r')"
  [[ "$dump" == *"mSelectedMethodId=$IME_ID"* ]] || return 1
  [[ "$dump" == *"mCurId=$IME_ID"* ]] || return 1
  [[ "$dump" == *"mCurrentSubtype=android.view.inputmethod.InputMethodSubtype@456d04f4"* ]] || return 1
}

while (($#)); do
  case "$1" in
    --install)
      install_apk="true"
      shift
      ;;
    --prefill-text)
      prefill_text="${2:-}"
      shift 2
      ;;
    --prefill-codepoints)
      prefill_codepoints="${2:-}"
      shift 2
      ;;
    --cursor)
      cursor_position="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ "$install_apk" == "true" ]]; then
  adb install -r "$APK_PATH"
fi

ensure_cheonjiin_ime

cmd=(adb shell am start -W -n "$ACTIVITY")
if [[ -n "$prefill_text" ]]; then
  cmd+=(--es prefill_text "$prefill_text")
elif [[ -z "$prefill_codepoints" ]]; then
  cmd+=(--ez clear_text true)
fi
if [[ -n "$prefill_codepoints" ]]; then
  cmd+=(--es prefill_codepoints "$prefill_codepoints")
fi
if [[ -n "$cursor_position" ]]; then
  cmd+=(--ei cursor_position "$cursor_position")
fi

"${cmd[@]}" >/dev/null
sleep 3

for _ in 1 2 3; do
  if ime_is_ready; then
    exit 0
  fi
  ensure_cheonjiin_ime
  adb shell input tap 540 800 >/dev/null 2>&1 || true
  sleep 1
done

echo "Failed to keep Cheonjiin IME active after opening sandbox" >&2
adb shell dumpsys input_method | rg 'mSelectedMethodId=|mCurrentSubtype=|mCurId=|mImeWindowVis|mInputShown' -n >&2 || true
exit 1
