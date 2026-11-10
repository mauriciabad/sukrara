#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks that every alert sound survived resource shrinking in a release APK:
#   tools/release/verify-sounds.sh <apk>
# The sounds are looked up by name (AlertSoundProvider, AlertTryOut: getIdentifier), so nothing in the code keeps them;
# only contracts/alerts/sounds/android/raw/keep_alert_sounds.xml does. A stripped sound is a silent alarm on the release
# build only. For each contracts/alerts/sounds/android/raw/*.ogg this asserts that the APK's resource table has
# raw/<name> and that the file it points at is in the APK with the contract file's exact size (the shrinker can keep
# the entry and replace the file with an empty stub; path shortening renames the file, so it is found via the table).
# Needs no signing key: verify-apk.sh calls it, and it runs on an unsigned build too
#   (cd apps/android && ./gradlew :app:assembleRelease -x verifyReleaseSigning).
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
apk="${1:?usage: verify-sounds.sh <apk>}"
[ -f "$apk" ] || { echo "FAIL no APK at $apk"; exit 1; }
sounds="$(repo_root)/contracts/alerts/sounds/android/raw"
aapt2="${AAPT2:-$(sdk_tool aapt2)}"

# "<name> <path in the APK>" for every raw resource (the default configuration's file).
table="$("$aapt2" dump resources "$apk" | awk '
  /^ *resource 0x[0-9a-f]+ / { name = ""; if ($3 ~ /^raw\//) name = substr($3, 5); next }
  name != "" && /\(file\)/ { for (i = 1; i <= NF; i++) if ($i == "(file)") { print name, $(i + 1); break }; name = "" }')"
# "<path> <uncompressed size>" for every file in the APK.
files="$(unzip -l "$apk" | awk 'NR > 3 && $4 != "" { print $4, $1 }')"

fail=0 n=0
for f in "$sounds"/*.ogg; do
  name="$(basename "$f" .ogg)"
  n=$((n + 1))
  path="$(printf '%s\n' "$table" | awk -v n="$name" '$1 == n { print $2; exit }')"
  if [ -z "$path" ]; then echo "FAIL raw/$name is not in the release APK's resource table"; fail=1; continue; fi
  want="$(wc -c < "$f" | tr -d ' ')"
  got="$(printf '%s\n' "$files" | awk -v p="$path" '$1 == p { print $2; exit }')"
  if [ -z "$got" ]; then echo "FAIL raw/$name points at $path, which is not in the APK"; fail=1
  elif [ "$got" != "$want" ]; then echo "FAIL raw/$name ($path) is $got bytes, the contract's $name.ogg is $want (shrunk to a stub?)"; fail=1
  fi
done
if [ "$n" -eq 0 ]; then echo "FAIL no alert sounds in $sounds"; exit 1; fi
[ "$fail" -eq 0 ] && echo "ok   all $n alert sounds in the APK, byte for byte"
exit $fail
