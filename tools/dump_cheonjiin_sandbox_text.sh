#!/usr/bin/env bash
set -euo pipefail

OUT_XML="/tmp/cheonjiin_sandbox_dump.xml"

adb shell uiautomator dump /sdcard/uidump.xml >/dev/null
adb shell cat /sdcard/uidump.xml > "$OUT_XML"

python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse("/tmp/cheonjiin_sandbox_dump.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("class") == "android.widget.EditText":
        text = node.attrib.get("text", "")
        print(text)
        print([hex(ord(ch)) for ch in text])
        break
else:
    raise SystemExit("No EditText found in current UI dump")
PY
