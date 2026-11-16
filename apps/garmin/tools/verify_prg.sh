#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks a face .prg before it goes on the watch (tools/install.sh watch; CLAUDE.md "Debug code is compiled out"):
# the face's own app id (not the case loader's), and none of the debug case loader inside.
#   apps/garmin/tools/verify_prg.sh apps/garmin/bin/SukraraAura.prg [<app id>]   (default: manifest.xml's; the beta's
#                                                                              for a beta package's .prg)
set -eu
PRG=${1:?usage: verify_prg.sh <prg>}
HERE=$(cd "$(dirname "$0")/.." && pwd)
fail() {
  echo "verify_prg: $*" >&2
  exit 1
}
[ -s "$PRG" ] || fail "no $PRG"
# the case loader's names and data (source-cases, build/cases) are never in a release
for s in AuraCases AuraDump AuraCaseTable glucose-in-range; do
  if LC_ALL=C grep -a -q "$s" "$PRG"; then fail "$PRG contains '$s': a debug build or the case loader"; fi
done
# the face's id (manifest.xml), as bytes in the executable
ID=${2:-$(sed -n 's/.*iq:application id="\([0-9a-f-]*\)".*/\1/p' "$HERE/manifest.xml")}
ID=$(echo "$ID" | tr -d '-')
LC_ALL=C od -An -tx1 -v "$PRG" | tr -d ' \n' | grep -q "$ID" || fail "$PRG doesn't carry the face's app id $ID"
echo "verify_prg: $PRG ok (the face's id, no debug code)"
