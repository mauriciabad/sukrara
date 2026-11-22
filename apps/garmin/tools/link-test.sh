#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# The phone-link tests: contracts/watchface/payload/cases.json through the shared link (shared/test/link) and the app's
# own seam (its test/), built into the app (its monkey.jungle plus its link-test.jungle) and run in the Connect IQ
# simulator (one simulator is shared: it is started when none runs, and left running).
#   apps/garmin/tools/link-test.sh [<app>]         build and run (<app>: aura, app or field; default aura)
#   apps/garmin/tools/link-test.sh [<app>] build   build only
# Environment as apps/garmin/build.sh (CIQ_SDK, CIQ_KEY, JAVA_HOME, CIQ_DEVICE).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
APP=aura
case "${1:-}" in aura | app | field) APP=$1; shift ;; esac
[ -f "$HERE/$APP/link-test.jungle" ] || { echo "link-test.sh: $APP has no link-test.jungle" >&2; exit 2; }
python3 "$HERE/tools/link_cases.py" --check
DEVICE=${CIQ_DEVICE:-descentmk343mm}
KEY=${CIQ_KEY:-$HOME/.connectiq/sukrara-developer-key.der}
if [ -z "${CIQ_SDK:-}" ]; then
  CIQ_SDK=$(sed 's:/*$::' "$HOME/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg")
fi
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
  JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME && PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$HERE/bin"
cd "$HERE"
PRG=bin/$APP-link-test.prg
"$CIQ_SDK/bin/monkeyc" -d "$DEVICE" -f "$APP/monkey.jungle;$APP/link-test.jungle" -o "$PRG" -y "$KEY" -t -w
[ "${1:-}" = build ] && exit 0
if ! pgrep -f "ConnectIQ.app/Contents/MacOS/simulator" >/dev/null; then
  "$CIQ_SDK/bin/connectiq" &
  sleep 8
fi
"$CIQ_SDK/bin/monkeydo" "$PRG" "$DEVICE" -t
