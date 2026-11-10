#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the Android release APK for a GitHub release: assembleFullRelease (the full flavor) with THE release key
# (password from the Keychain, never on a command line), verify-apk.sh, then copies it to
# apps/android/app/build/dist/Sukrara-<version>.apk
# with its R8 mapping gzipped next to it, Sukrara-<version>-android-mapping.txt.gz, a release asset (crash traces from a
# phone are unreadable without it; it isn't secret, the source is public; About lists only the Mac files as downloads),
# and a copy in ~/.android/sukrara-mappings/<version>-mapping.txt.
# Refuses unless tools/check/strict exists. Prints the APK path on the last line.
set -eu
. "$(dirname "$0")/lib.sh"
root="$(repo_root)"
if [ ! -e "$root/tools/check/strict" ]; then
  echo "refusing: tools/check/strict is missing (every registry file must exist before a release)" >&2; exit 2
fi
set -- $(version_info)
version="$1"

JAVA_HOME="$(dirname "$(dirname "$(jdk_tool java)")")"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
SUKRARA_KEYSTORE_PASSWORD="$(release_password)" || { echo "no release key password (Keychain item sukrara-android-release)" >&2; exit 2; }
export JAVA_HOME ANDROID_HOME SUKRARA_KEYSTORE_PASSWORD

apk="$root/apps/android/app/build/outputs/apk/full/release/app-full-release.apk"
mapping="$root/apps/android/app/build/outputs/mapping/fullRelease/mapping.txt"
rm -f "$apk"   # a failed build must not leave an old APK to ship
# --no-daemon: the key's password must not stay in a long-lived Gradle daemon's environment.
(cd "$root/apps/android" && ./gradlew --no-daemon --console=plain :app:assembleFullRelease)
unset SUKRARA_KEYSTORE_PASSWORD
"$root/tools/release/verify-apk.sh" "$apk" "$mapping"

dist="$root/apps/android/app/build/dist"
mkdir -p "$dist" "$HOME/.android/sukrara-mappings"
cp "$apk" "$dist/Sukrara-$version.apk"
cp "$mapping" "$HOME/.android/sukrara-mappings/$version-mapping.txt"
# -n: no file name or time inside, so the same mapping gives the same .gz
gzip -9 -n -c "$mapping" > "$dist/Sukrara-$version-android-mapping.txt.gz"
echo "mapping: $dist/Sukrara-$version-android-mapping.txt.gz (release asset), copy in $HOME/.android/sukrara-mappings/$version-mapping.txt"
# #81: a pre-release library in a release is recorded in its notes, with why (tools/check/allow.json prerelease_ok)
python3 -I -c '
import json, sys
ok = json.load(open(sys.argv[1])).get("prerelease_ok", {})
if ok:
    print("release notes, pre-release libraries in this build:")
    for name, e in sorted(ok.items()):
        print("  - %s %s: %s" % (name, e["version"], e["reason"]))
' "$root/tools/check/allow.json"
echo "$dist/Sukrara-$version.apk"
