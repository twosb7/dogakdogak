#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/cheonjiin-device-smoke"
mkdir -p "$OUT_DIR"

open_sandbox() {
  "$ROOT_DIR/tools/open_cheonjiin_sandbox.sh" "$@"
}

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT_DIR/$name.png"
}

dump_text() {
  local name="$1"
  adb shell uiautomator dump /sdcard/uidump.xml >/dev/null
  adb shell cat /sdcard/uidump.xml > "$OUT_DIR/$name.xml"
}

read_text() {
  local name="$1"
  python3 - <<PY
import xml.etree.ElementTree as ET
root = ET.parse("$OUT_DIR/$name.xml").getroot()
for node in root.iter('node'):
    if node.attrib.get('class') == 'android.widget.EditText':
        print(node.attrib.get('text', ''))
        break
else:
    print("")
PY
}

assert_text() {
  local name="$1"
  local expected="$2"
  dump_text "$name"
  local actual
  actual="$(read_text "$name")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Scenario '$name' mismatch: expected '$expected' but was '$actual'" >&2
    capture "${name}_failure"
    return 1
  fi
  return 0
}

wait_for_text() {
  local expected="$1"
  local attempts=10
  for _ in $(seq 1 "$attempts"); do
    dump_text "__wait"
    local actual
    actual="$(read_text "__wait")"
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 0.4
  done
  echo "Timed out waiting for text '$expected'" >&2
  capture "wait_timeout"
  return 1
}

ensure_keyboard_visible() {
  local attempts=8
  for _ in $(seq 1 "$attempts"); do
    if adb shell dumpsys input_method | grep -q 'mInputShown=true'; then
      return 0
    fi
    adb shell input tap 540 800 >/dev/null 2>&1 || true
    sleep 0.5
  done
  echo "Timed out waiting for keyboard visibility" >&2
  capture "keyboard_timeout"
  return 1
}

tap_key() {
  local x="$1"
  local y="$2"
  adb shell input tap "$x" "$y"
  sleep 0.25
}

tap_delete() {
  tap_key 1016 1812
}

input_da() {
  tap_key 835 1590
  tap_key 242 1395
  tap_key 539 1395
}

input_ga() {
  tap_key 242 1600
  tap_key 242 1395
  tap_key 539 1395
}

input_na() {
  tap_key 539 1600
  tap_key 242 1395
  tap_key 539 1395
}

input_geo() {
  tap_key 242 1600
  tap_key 539 1395
  tap_key 242 1395
}

# Scenario 1: middle insert "가|나" + "다" -> "가다나"
status=0

open_sandbox
ensure_keyboard_visible || status=1
input_ga
input_na
sleep 0.8
assert_text "__prefill_gana" "가나" || status=1
adb shell input keyevent KEYCODE_MOVE_HOME
sleep 0.6
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 0.8
input_da
sleep 1.0
capture "middle_insert"
assert_text "middle_insert" "가다나" || status=1

# Scenario 2: middle backspace "가|다나" -> "다나"
open_sandbox --prefill-text "가다나"
wait_for_text "가다나" || status=1
ensure_keyboard_visible || status=1
adb shell input keyevent KEYCODE_MOVE_HOME
sleep 0.6
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 0.8
tap_delete
sleep 1.0
capture "middle_backspace"
assert_text "middle_backspace" "다나" || status=1

# Scenario 3: directional vowel delete "거" -> "ㄱㆍ"
open_sandbox --prefill-text "거"
wait_for_text "거" || status=1
ensure_keyboard_visible || status=1
tap_delete
sleep 1.0
capture "geo_delete"
assert_text "geo_delete" "ㄱㆍ" || status=1

echo "Saved device smoke artifacts to $OUT_DIR"
exit "$status"
