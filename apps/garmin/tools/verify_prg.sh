#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks an app's .prg before it goes on the watch (tools/install.sh watch; CLAUDE.md "Debug code is compiled out"):
# the app's own id (not the case loader's), and no debug code inside.
#   apps/garmin/tools/verify_prg.sh <app> apps/garmin/bin/<Name>.prg [<app id>]   (<app>: aura, app, field; default
#                                                       id: the app's manifest.xml's; the beta's for a beta package's .prg)
set -eu
APP=${1:?usage: verify_prg.sh <aura|app|field> <prg> [<app id>]}
PRG=${2:?usage: verify_prg.sh <aura|app|field> <prg> [<app id>]}
HERE=$(cd "$(dirname "$0")/.." && pwd)
fail() {
  echo "verify_prg: $*" >&2
  exit 1
}
[ -f "$HERE/$APP/manifest.xml" ] || fail "no app '$APP' (aura, app, field)"
[ -s "$PRG" ] || fail "no $PRG"
# the debug code's names and data (aura's case loader: source-cases, build/cases) are
# never in a release
for s in AuraCases AuraDump AuraCaseTable glucose-in-range; do
  if LC_ALL=C grep -a -q "$s" "$PRG"; then fail "$PRG contains '$s': a debug build or the case loader"; fi
done
# the app's id (its manifest.xml), as bytes in the executable
ID=${3:-$(sed -n 's/.*iq:application id="\([0-9a-f-]*\)".*/\1/p' "$HERE/$APP/manifest.xml")}
ID=$(echo "$ID" | tr -d '-')
LC_ALL=C od -An -tx1 -v "$PRG" | tr -d ' \n' | grep -q "$ID" || fail "$PRG doesn't carry $APP's app id $ID"
echo "verify_prg: $PRG ok ($APP's id, no debug code)"
