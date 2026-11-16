#!/usr/bin/env bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# The Garmin face's release files, on the release Mac (release.py's gate; docs/20-publishing.md › Garmin):
#
#   tools/release/garmin-release.sh <version>    apps/garmin/bin/dist/SukraraAura-<version>.iq (the store package, every
#                                                manifest device) and SukraraAura-<version>-<device>.prg (sideload)
#   tools/release/garmin-release.sh --key-check  only checks the key (release.py's preconditions)
#   tools/release/garmin-release.sh --beta       apps/garmin/bin/dist/SukraraAura-beta.iq: the store package under the
#                                                beta app id (build.sh export-beta), for a Connect IQ beta app upload
#
# Every .iq is checked before it is kept (verify_iq): it unpacks (a 7-zip: bsdtar), its manifest has the expected app
# id, version and every manifest device, its dev_key.pub is the store key's public key, and each device's .prg passes
# apps/garmin/tools/verify_prg.sh (the id, no debug code).
#
# Signed with the store key: CIQ_KEY (default ~/.connectiq/sukrara-developer-key.der), after checking that its public
# key's SHA-256 is tools/release/garmin-key.sha256. The Connect IQ store ties an app to the key of its first upload
# and refuses every later version signed with another ("All app versions have to be signed with the same key pair");
# Garmin keeps no copy, so the key stays on this Mac with its backup in the password manager, never on CI.
# The builds are apps/garmin/build.sh's own (release: debug code compiled out; export: the store package), with the
# manifest's version checked against the release's.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/apps/garmin"
KEY="${CIQ_KEY:-$HOME/.connectiq/sukrara-developer-key.der}"
PIN="$ROOT/tools/release/garmin-key.sha256"
DIST="$APP/bin/dist"
die() { echo "garmin-release.sh: $1" >&2; exit 1; }

key_check() {
  [ -f "$PIN" ] || die "no $PIN: the Garmin face isn't released yet (docs/20-publishing.md › Garmin)"
  [ -f "$KEY" ] || die "no store key at $KEY (restore it from the password manager: docs/DEVELOPMENT.md › Signing)"
  local got
  got="$(openssl pkey -inform DER -in "$KEY" -pubout -outform DER | shasum -a 256 | cut -d' ' -f1)"
  [ "$got" = "$(tr -d ' \n' < "$PIN")" ] ||
    die "$KEY isn't the store key (public SHA-256 $got, $PIN says $(cat "$PIN")): the store refuses an update signed with another key"
  echo "store key: $KEY ($got)"
}

pub_sha() { openssl pkey -inform DER -in "$KEY" -pubout -outform DER | shasum -a 256 | cut -d' ' -f1; }

# verify_iq <file.iq> <app id> <version>
verify_iq() {
  local iq="$1" id="$2" version="$3" tmp got
  tmp="$(mktemp -d)"
  IQ_TMP="$tmp"
  trap 'rm -rf "$IQ_TMP"' EXIT
  bsdtar -xf "$iq" -C "$tmp" 2>/dev/null || die "$iq doesn't unpack (not a Connect IQ package)"
  [ -f "$tmp/manifest.xml" ] && [ -f "$tmp/dev_key.pub" ] && [ -f "$tmp/manifest.sig2" ] || die "$iq lacks manifest.xml, dev_key.pub or manifest.sig2"
  got="$(openssl pkey -pubin -inform DER -in "$tmp/dev_key.pub" -pubout -outform DER | shasum -a 256 | cut -d' ' -f1)"
  [ "$got" = "$(pub_sha)" ] || die "$iq is signed with another key (public SHA-256 $got)"
  grep -q "<iq:application[^>]* id=\"$(echo "$id" | tr -d '-')\"" "$tmp/manifest.xml" || die "$iq doesn't carry app id $id"
  grep -q "<iq:application[^>]* version=\"$version\"" "$tmp/manifest.xml" || die "$iq isn't version $version"
  local n=0 prg
  for prg in $(sed -n 's/.*filename="\([^"]*\.prg\)".*/\1/p' "$tmp/manifest.xml"); do
    "$APP/tools/verify_prg.sh" "$tmp/$prg" "$id" >/dev/null || die "$iq: $prg failed verify_prg.sh"
    n=$((n + 1))
  done
  local want
  want="$(grep -c '<iq:product ' "$APP/manifest.xml")"
  [ "$n" = "$want" ] || die "$iq has $n devices, manifest.xml lists $want"
  echo "verified: $iq (app id $id, version $version, $n devices, the store key)"
}

if [ "${1:-}" = "--key-check" ]; then
  key_check
  exit 0
fi
if [ "${1:-}" = "--beta" ]; then
  if [ -f "$PIN" ]; then key_check; else
    [ -f "$KEY" ] || die "no store key at $KEY"
    echo "store key: $KEY ($(pub_sha); not pinned yet: $PIN comes with the first public upload)"
  fi
  export CIQ_KEY="$KEY"
  out="$("$APP/build.sh" export-beta | tail -1)" || die "apps/garmin/build.sh export-beta failed"
  beta_id="$(sed -n 's/.*<iq:application id="\([^"]*\)".*/\1/p' "$APP/bin/beta/manifest.xml")"
  version="$(sed -n 's/.*<iq:application[^>]* version="\([^"]*\)".*/\1/p' "$APP/manifest.xml")"
  verify_iq "$out" "$beta_id" "$version"
  mkdir -p "$DIST"
  cp "$out" "$DIST/SukraraAura-beta.iq"
  ls -l "$DIST/SukraraAura-beta.iq"
  exit 0
fi
VERSION="${1:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: garmin-release.sh <version> | --key-check"
key_check
manifest_version="$(sed -n 's/.*<iq:application[^>]* version="\([^"]*\)".*/\1/p' "$APP/manifest.xml")"
[ "$manifest_version" = "$VERSION" ] || die "apps/garmin/manifest.xml says version $manifest_version, not $VERSION"
rm -rf "$DIST"
mkdir -p "$DIST"
export CIQ_KEY="$KEY"
for device in $(sed -n 's/.*<iq:product[^>]*id="\([^"]*\)".*/\1/p' "$APP/manifest.xml"); do
  out="$(CIQ_DEVICE="$device" "$APP/build.sh" release | tail -1)"
  "$APP/tools/verify_prg.sh" "$out"
  cp "$out" "$DIST/SukraraAura-$VERSION-$device.prg"
done
out="$("$APP/build.sh" export | tail -1)" || die "apps/garmin/build.sh export failed (the store package: monkeyc -e -r)"
[ "${out##*.}" = "iq" ] || die "apps/garmin/build.sh export made no .iq ($out)"
verify_iq "$out" "$(sed -n 's/.*<iq:application id="\([^"]*\)".*/\1/p' "$APP/manifest.xml")" "$VERSION"
cp "$out" "$DIST/SukraraAura-$VERSION.iq"
ls -l "$DIST"
