#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds one of Sukrara's Garmin apps (apps/garmin/README.md). Headless: needs only the Connect IQ SDK, the device files
# of the app's manifest.xml products (export builds every one; the others one, CIQ_DEVICE) and a JDK 17+.
#   apps/garmin/build.sh <app> [<mode>]   <app>: aura (Sukrara Aura, the face), app (Sukrara, the watch app), field
#                                         (Sukrara data field); every output in apps/garmin/bin/<Name>…
#   release (default)  the release .prg (debug code compiled out)   → bin/<Name>.prg
#   debug              the app with debug code                     → bin/<Name>-debug.prg
#   export             the store package (monkeyc -e -r)            → bin/<Name>.iq
#   export-beta        the same app under its beta app id           → bin/<Name>-beta.iq
#                      (a Connect IQ *beta app* needs its own id: docs/20-publishing.md › Connect IQ store)
#   cases | test       the debug case loader (every case of its cases.json in turn, printing its layout or input): the
#                      picture diffs' build → bin/<Name>-cases.prg (aura/tools/garmin_test.py run, app/tools/app_test.py
#                      run, field/tools/field_test.py run build and run it)
# <Name>: SukraraAura, Sukrara, SukraraField. Environment: CIQ_SDK (default: the SDK Manager's current SDK), CIQ_KEY
# (default ~/.connectiq/sukrara-developer-key.der), JAVA_HOME (default: Homebrew's openjdk@17 when present), CIQ_DEVICE
# (default descentmk343mm).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
APP=${1:-}
MODE=${2:-release}
# each app: its folder (apps/garmin/<app>) and its output name
case "$APP" in
  aura) NAME=SukraraAura ;;
  app) NAME=Sukrara ;;
  field) NAME=SukraraField ;;
  *) echo "usage: build.sh <aura|app|field> [release|debug|export|export-beta|cases|test]" >&2; exit 2 ;;
esac
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
JUNGLE=$APP/monkey.jungle
case "$MODE" in
  release) OUT=bin/$NAME.prg; FLAGS="-r" ;;
  debug) OUT=bin/$NAME-debug.prg; FLAGS="" ;;
  cases | test)
    case "$APP" in
      aura) JUNGLE=aura/cases.jungle; python3 aura/tools/cases.py ;;
      app) JUNGLE=app/build/cases/cases.jungle; python3 app/tools/cases.py ;;
      field) JUNGLE=field/build/cases/cases.jungle; CIQ_DEVICE=$DEVICE python3 field/tools/cases.py ;;
    esac
    OUT=bin/$NAME-cases.prg; FLAGS="" ;;
  export) OUT=bin/$NAME.iq; FLAGS="-e -r" ;;
  export-beta)
    # the app's beta id from the registry (fixed, so every beta upload updates the same beta app)
    BETA_ID=$(python3 -c 'import json,sys; print(next(a["beta"] for a in json.load(open(sys.argv[1]))["apps"] if a["app"] == sys.argv[2]))' \
      "$HERE/../../contracts/watchface/payload/apps.json" "$APP")
    # the app's jungle naming a copy of its manifest with the beta's id (bin/beta/<app>/manifest.xml). A second jungle
    # can't reset project.manifest, and a jungle's paths are relative to it: so a copy beside the app's own jungle,
    # removed when the build ends
    mkdir -p "bin/beta/$APP"
    sed "s/<iq:application id=\"[^\"]*\"/<iq:application id=\"$BETA_ID\"/" "$APP/manifest.xml" >"bin/beta/$APP/manifest.xml"
    JUNGLE=$APP/beta.jungle
    trap 'rm -f "$HERE/$JUNGLE"' EXIT
    sed "s:^project.manifest = .*:project.manifest = ../bin/beta/$APP/manifest.xml:" "$APP/monkey.jungle" >"$JUNGLE"
    OUT=bin/$NAME-beta.iq; FLAGS="-e -r" ;;
  *) echo "usage: build.sh <aura|app|field> [release|debug|export|export-beta|cases|test]" >&2; exit 2 ;;
esac
if [ "$MODE" = export ] || [ "$MODE" = export-beta ]; then
  # the store package: every product of the manifest, no -d
  # shellcheck disable=SC2086
  "$CIQ_SDK/bin/monkeyc" -f "$JUNGLE" -o "$OUT" -y "$KEY" -w -O 2z $FLAGS
else
  # shellcheck disable=SC2086
  "$CIQ_SDK/bin/monkeyc" -d "$DEVICE" -f "$JUNGLE" -o "$OUT" -y "$KEY" -w -O 2z $FLAGS ${CIQ_BUILD_STATS:+--build-stats 0}
fi
echo "$HERE/$OUT"
