#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Copies the face onto the Garmin watch on USB (MTP, brew libmtp) as GARMIN/Apps/SUKRARAAURA.PRG; the watch installs
# it when it's unplugged (docs/DEVELOPMENT.md › Garmin). Touches only that one file: an older copy of it is replaced,
# nothing else on the watch is read or written. Garmin Express / OpenMTP must be closed (one MTP client at a time).
#   apps/garmin/tools/sideload.sh apps/garmin/bin/SukraraAura.prg [--dry-run]
set -eu
PRG=${1:?usage: sideload.sh <prg> [--dry-run]}
DRY=${2:-}
NAME=SUKRARAAURA.PRG
[ -f "$PRG" ] || { echo "sideload: no $PRG" >&2; exit 2; }
command -v mtp-sendfile >/dev/null || { echo "sideload: no libmtp (brew install libmtp)" >&2; exit 2; }
mtp-detect 2>/dev/null | grep -q "Manufacturer: Garmin" || { echo "sideload: no Garmin watch on USB (MTP)" >&2; exit 1; }
# the Apps folder's id, and our file's if an older copy is there (by exact name, in that folder only)
APPS=$(mtp-folders 2>/dev/null | awk -F'\t' '$2 == "  Apps" { print $1; exit }')
[ -n "$APPS" ] || { echo "sideload: no GARMIN/Apps folder on the watch" >&2; exit 1; }
OLD=$(mtp-files 2>/dev/null | awk -v apps="$APPS" -v name="$NAME" '
  /^File ID:/ { id = $3 } /Filename:/ { fn = $2 } /Parent ID:/ { if ($3 == apps && fn == name) print id }')
if [ "$DRY" = "--dry-run" ]; then
  echo "would replace file $OLD and send $PRG to /GARMIN/Apps/$NAME"
  exit 0
fi
if [ -n "$OLD" ]; then
  for id in $OLD; do
    echo "+ mtp-delfile -n $id (the older $NAME)"
    mtp-delfile -n "$id" >/dev/null 2>&1
  done
fi
echo "+ mtp-sendfile $PRG /GARMIN/Apps/$NAME"
mtp-sendfile "$PRG" "/GARMIN/Apps/$NAME" 2>&1 | grep -v "association type" | tail -3
NEW=$(mtp-files 2>/dev/null | awk -v apps="$APPS" -v name="$NAME" '
  /^File ID:/ { id = $3 } /Filename:/ { fn = $2 } /Parent ID:/ { if ($3 == apps && fn == name) print id }')
[ -n "$NEW" ] || { echo "sideload: $NAME isn't in GARMIN/Apps after sending" >&2; exit 1; }
echo "sideload: $NAME is on the watch (file $NEW); unplug it to install, then pick the face (hold MENU › Watch face)"
