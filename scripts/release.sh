#!/usr/bin/env bash
# Publishes the current commit as a GitHub release; phones offer it as an update.
# Usage: scripts/release.sh "What changed (shown in the update dialog)"
set -euo pipefail
cd "$(dirname "$0")/.."
notes="${1:-}"
[ -n "$(git status --porcelain)" ] && { echo "Commit your changes first."; exit 1; }
code=$(git rev-list --count HEAD)
# Signed with this computer's debug key, the same key as `installDebug`, so a release
# installs over the copies already on the phones. Keep ~/.android/debug.keystore backed up:
# without it, new versions can't update the installed app.
./gradlew -q :composeApp:assembleRelease -Pmnemo.signing=debug
apk=composeApp/build/outputs/apk/release/composeApp-release.apk
cp "$apk" "build/mnemo-$code.apk"
git push -q origin HEAD
gh release create "v$code" "build/mnemo-$code.apk" --title "Mnemo 0.1.$code" --notes "${notes:-Mnemo 0.1.$code}" --target "$(git rev-parse HEAD)"
echo "Published v$code"
