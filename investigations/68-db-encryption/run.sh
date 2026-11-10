#!/usr/bin/env bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the investigation, runs DbEncryptionBench on ONE emulator and saves its results.
#   investigations/68-db-encryption/run.sh emulator-5668 [results/<name>.json]
# Emulator only: a serial that isn't emulator-* is refused (the owner's phone is never used).
set -euo pipefail
cd "$(dirname "$0")"

serial="${1:?usage: run.sh emulator-<port> [results/<name>.json]}"
out="${2:-results/emulator.json}"
[[ "$serial" == emulator-* ]] || { echo "refusing $serial: emulators only" >&2; exit 2; }

export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@21)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
../../apps/android/gradlew -p . assembleDebug assembleDebugAndroidTest --console=plain -q

adb -s "$serial" install -r -t build/outputs/apk/debug/inv68-db-encryption-debug.apk
adb -s "$serial" install -r -t build/outputs/apk/androidTest/debug/inv68-db-encryption-debug-androidTest.apk
adb -s "$serial" shell pm clear app.mauri.sukrara.inv68 >/dev/null
adb -s "$serial" shell am instrument -w -e class app.mauri.sukrara.inv68.DbEncryptionBench \
  app.mauri.sukrara.inv68.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$(dirname "$out")"
adb -s "$serial" exec-out run-as app.mauri.sukrara.inv68 cat files/results.json > "$out"
echo "results: $out"
