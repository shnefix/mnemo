#!/usr/bin/env bash
# Runs the on-device design audit at the given font scale (default 1.0), prints any
# problems and pulls per-screen screenshots to /tmp/mnemo-shots-<scale>.
# Uses `am instrument` directly so the app (and its screenshots) is not uninstalled afterwards.
set -u
scale="${1:-1.0}"
density="${2:-}"   # optional wm density, e.g. 600 to simulate a narrow screen
cd "$(dirname "$0")/.."
adb shell settings put system font_scale "$scale"
[ -n "$density" ] && adb shell wm density "$density"
./gradlew :composeApp:ktlintFormat :composeApp:installDebug :composeApp:installDebugAndroidTest -q 2>&1 | grep -E "^e:|FAILED" | head -5
adb shell pm clear app.mnemo >/dev/null 2>&1
adb shell rm -rf /sdcard/Android/data/app.mnemo/files/shots
out=$(adb shell am instrument -w -e class app.mnemo.OnDeviceDesignAuditTest app.mnemo.test/androidx.test.runner.AndroidJUnitRunner)
echo "$out" | grep -vE "^INSTRUMENTATION_(STATUS|CODE)" | head -60
tag="$scale${density:+-d$density}"
rm -rf "/tmp/mnemo-shots-$tag"
adb pull /sdcard/Android/data/app.mnemo/files/shots "/tmp/mnemo-shots-$tag" >/dev/null 2>&1
ls "/tmp/mnemo-shots-$tag"
adb shell settings put system font_scale 1.0
adb shell wm density reset
