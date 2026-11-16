#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds Sukrara Aura (apps/garmin/README.md). Headless: needs only the Connect IQ SDK, its descentmk343mm device files
# and a JDK 17+.
#   apps/garmin/build.sh            release .prg (debug code compiled out)  → apps/garmin/bin/SukraraAura.prg
#   apps/garmin/build.sh debug      the face with debug code                → apps/garmin/bin/SukraraAura-debug.prg
#   apps/garmin/build.sh cases      the debug case loader (every case of cases.json in turn) → bin/SukraraAura-cases.prg
#   apps/garmin/build.sh test       the unit tests (expect.json)            → bin/SukraraAura-test.prg
# Environment: CIQ_SDK (default: the SDK Manager's current SDK), CIQ_KEY (default ~/.connectiq/sukrara-developer-key.der),
# JAVA_HOME (default: Homebrew's openjdk@17 when present), CIQ_DEVICE (default descentmk343mm).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
MODE=${1:-release}
DEVICE=${CIQ_DEVICE:-descentmk343mm}
KEY=${CIQ_KEY:-$HOME/.connectiq/sukrara-developer-key.der}
if [ -z "${CIQ_SDK:-}" ]; then
  CFG="$HOME/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg"
  [ -f "$CFG" ] || { echo "build.sh: no Connect IQ SDK (install it with the SDK Manager or set CIQ_SDK)" >&2; exit 2; }
  CIQ_SDK=$(sed 's:/*$::' "$CFG")
fi
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
  JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
[ -n "${JAVA_HOME:-}" ] && export JAVA_HOME && PATH="$JAVA_HOME/bin:$PATH"
[ -f "$KEY" ] || { echo "build.sh: no developer key at $KEY (docs/DEVELOPMENT.md › Garmin)" >&2; exit 2; }
mkdir -p "$HERE/bin"
cd "$HERE"
case "$MODE" in
  release) JUNGLE=monkey.jungle; OUT=bin/SukraraAura.prg; FLAGS="-r" ;;
  debug) JUNGLE=monkey.jungle; OUT=bin/SukraraAura-debug.prg; FLAGS="" ;;
  cases) JUNGLE="monkey.jungle;cases.jungle"; OUT=bin/SukraraAura-cases.prg; FLAGS="" ;;
  test) JUNGLE="monkey.jungle;test.jungle"; OUT=bin/SukraraAura-test.prg; FLAGS="-t" ;;
  *) echo "usage: build.sh [release|debug|cases|test]" >&2; exit 2 ;;
esac
# shellcheck disable=SC2086
"$CIQ_SDK/bin/monkeyc" -d "$DEVICE" -f "$JUNGLE" -o "$OUT" -y "$KEY" -w $FLAGS
echo "$HERE/$OUT"
