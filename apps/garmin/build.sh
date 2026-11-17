#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds Sukrara Aura (apps/garmin/README.md). Headless: needs only the Connect IQ SDK, the device files of manifest.xml's
# products (export builds every one; the others one, CIQ_DEVICE) and a JDK 17+.
#   apps/garmin/build.sh            release .prg (debug code compiled out)  → apps/garmin/bin/SukraraAura.prg
#   apps/garmin/build.sh debug      the face with debug code                → apps/garmin/bin/SukraraAura-debug.prg
#   apps/garmin/build.sh cases      the debug case loader (every case of cases.json in turn, printing its layout):
#                                   the unit tests' and the picture diffs' build → bin/SukraraAura-cases.prg
#                                   (tools/garmin_test.py run builds and runs it); `test` is the same build
#   apps/garmin/build.sh export     the store package (monkeyc -e -r)      → apps/garmin/bin/SukraraAura.iq
#   apps/garmin/build.sh export-beta  the same face under the beta app id   → apps/garmin/bin/SukraraAura-beta.iq
#                                   (a Connect IQ *beta app* needs its own id: docs/20-publishing.md › Connect IQ store)
#   apps/garmin/build.sh memory     the face with its heaviest state, printing its memory (debug, #217; source-memory/)
#                                   → bin/SukraraAura-memory.prg: monkeydo it, read the MEM| lines
# Environment: CIQ_SDK (default: the SDK Manager's current SDK), CIQ_KEY (default ~/.connectiq/sukrara-developer-key.der),
# JAVA_HOME (default: Homebrew's openjdk@17 when present), CIQ_DEVICE (default descentmk343mm).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
MODE=${1:-release}
DEVICE=${CIQ_DEVICE:-descentmk343mm}
KEY=${CIQ_KEY:-$HOME/.connectiq/sukrara-developer-key.der}
if [ -z "${CIQ_SDK:-}" ]; then
  CFG="$HOME/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg"
  [ -f "$CFG" ] || CFG="$HOME/.Garmin/ConnectIQ/current-sdk.cfg" # Linux
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
  cases | test) JUNGLE=cases.jungle; OUT=bin/SukraraAura-cases.prg; FLAGS=""; python3 tools/cases.py ;;
  export) JUNGLE=monkey.jungle; OUT=bin/SukraraAura.iq; FLAGS="-e -r" ;;
  export-beta)
    # the manifest with its app id swapped for the beta's (BETA_ID, fixed so every beta upload updates the same beta
    # app), built by beta.jungle (monkey.jungle's paths)
    BETA_ID=635140ec-f724-44fb-bc08-976fa9111250
    mkdir -p bin/beta
    sed "s/<iq:application id=\"[^\"]*\"/<iq:application id=\"$BETA_ID\"/" manifest.xml >bin/beta/manifest.xml
    JUNGLE=beta.jungle; OUT=bin/SukraraAura-beta.iq; FLAGS="-e -r" ;;
  memory)
    # the manifest with the memory hook's entry (source-memory/AuraMemory.mc), built by memory.jungle; debug only
    mkdir -p bin/memory
    sed 's/entry="AuraApp"/entry="AuraMemoryApp"/' manifest.xml >bin/memory/manifest.xml
    JUNGLE=memory.jungle; OUT=bin/SukraraAura-memory.prg; FLAGS="" ;;
  *) echo "usage: build.sh [release|debug|cases|test|export|export-beta|memory]" >&2; exit 2 ;;
esac
if [ "$MODE" = export ] || [ "$MODE" = export-beta ]; then
  # the store package: every product of the manifest, no -d
  # shellcheck disable=SC2086
  "$CIQ_SDK/bin/monkeyc" -f "$JUNGLE" -o "$OUT" -y "$KEY" -w -O 2z $FLAGS
else
  # shellcheck disable=SC2086
  "$CIQ_SDK/bin/monkeyc" -d "$DEVICE" -f "$JUNGLE" -o "$OUT" -y "$KEY" -w -O 2z $FLAGS
fi
echo "$HERE/$OUT"
