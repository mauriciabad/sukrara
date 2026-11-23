#!/usr/bin/env bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# A Garmin app's release files, on the release Mac (release.py's gate; docs/20-publishing.md › Garmin). <app>: aura
# (Sukrara Aura, <Name> SukraraAura), app (Sukrara), field (SukraraField); apps/garmin/README.md:
#
#   tools/release/garmin-release.sh <app> <version>  apps/garmin/bin/dist/<Name>-<version>.iq (the store package, every
#                                                    manifest device) and <Name>-<version>-sideload.zip (one
#                                                    <Name>-<device>.prg per device, to copy onto a watch by hand)
#   tools/release/garmin-release.sh sdk              apps/garmin/bin/dist/SukraraFace-<its version>.barrel: the
#                                                    barrel for other developers' faces (its own release, face-sdk)
#   tools/release/garmin-release.sh --key-check      only checks the key (release.py's preconditions)
#   tools/release/garmin-release.sh <app> --beta     apps/garmin/bin/dist/<Name>-beta.iq: the store package under the
#                                                    app's beta id (build.sh <app> export-beta), for a Connect IQ beta
#                                                    app upload
#
# Every .iq is checked before it is kept (verify_iq): it unpacks (a 7-zip: bsdtar), its manifest has the expected app
# id, version and the part numbers of every manifest device (the SDK's device files; one device can have several), its dev_key.pub is the store key's public key, and each device's .prg passes
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
GARMIN="$ROOT/apps/garmin"
KEY="${CIQ_KEY:-$HOME/.connectiq/sukrara-developer-key.der}"
PIN="$ROOT/tools/release/garmin-key.sha256"
DIST="$GARMIN/bin/dist"
die() { echo "garmin-release.sh: $1" >&2; exit 1; }

key_check() {
  [ -f "$PIN" ] || die "no $PIN: no Garmin app is released yet (docs/20-publishing.md › Garmin)"
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
  TMPS+=("$tmp")
  bsdtar -xf "$iq" -C "$tmp" 2>/dev/null || die "$iq doesn't unpack (not a Connect IQ package)"
  [ -f "$tmp/manifest.xml" ] && [ -f "$tmp/dev_key.pub" ] && [ -f "$tmp/manifest.sig2" ] || die "$iq lacks manifest.xml, dev_key.pub or manifest.sig2"
  got="$(openssl pkey -pubin -inform DER -in "$tmp/dev_key.pub" -pubout -outform DER | shasum -a 256 | cut -d' ' -f1)"
  [ "$got" = "$(pub_sha)" ] || die "$iq is signed with another key (public SHA-256 $got)"
  grep -q "<iq:application[^>]* id=\"$(echo "$id" | tr -d '-')\"" "$tmp/manifest.xml" || die "$iq doesn't carry app id $id"
  grep -q "<iq:application[^>]* version=\"$version\"" "$tmp/manifest.xml" || die "$iq isn't version $version"
  local n=0 prg
  for prg in $(sed -n 's/.*filename="\([^"]*\.prg\)".*/\1/p' "$tmp/manifest.xml"); do
    "$GARMIN/tools/verify_prg.sh" "$APP" "$tmp/$prg" "$id" >/dev/null || die "$iq: $prg failed verify_prg.sh"
    n=$((n + 1))
  done
  # one entry per part number, not per device (the MARQ (Gen 2) has two): the package's part numbers are those of
  # every manifest device, as the SDK's device files list them
  local got_parts want_parts
  got_parts="$(sed -n 's/.*partNumber="\([^"]*\)".*/\1/p' "$tmp/manifest.xml" | LC_ALL=C sort -u | tr '\n' ' ')"
  want_parts="$(part_numbers)" || die "no device files (compiler.json) for every device of apps/garmin/$APP/manifest.xml"
  [ "$got_parts" = "$want_parts" ] ||
    die "$iq has the part numbers $got_parts; the devices of apps/garmin/$APP/manifest.xml have $want_parts"
  echo "verified: $iq (app id $id, version $version, $(devices | wc -l | tr -d ' ') devices in $n part numbers, the store key)"
}

devices() { sed -n 's/.*<iq:product[^>]*id="\([^"]*\)".*/\1/p' "$MANIFEST"; }

# the part numbers of the manifest's devices, sorted, each followed by a space, from the SDK Manager's device files
part_numbers() {
  local dir
  for dir in "$HOME/Library/Application Support/Garmin/ConnectIQ/Devices" "$HOME/.Garmin/ConnectIQ/Devices"; do
    [ -d "$dir" ] && break
  done
  # shellcheck disable=SC2046 # one argument per device id
  python3 -I -c 'import json, os, sys
parts = {p["number"] for dev in sys.argv[2:] for p in json.load(open(os.path.join(sys.argv[1], dev, "compiler.json")))["partNumbers"]}
print("".join(p + " " for p in sorted(parts)))' "$dir" $(devices)
}

TMPS=()
trap 'rm -rf "${TMPS[@]:-}"' EXIT

if [ "${1:-}" = "--key-check" ]; then
  key_check
  exit 0
fi
# SukraraFace, the barrel other developers' faces read Sukrara with (apps/garmin/sdk, its own version, its own release
# face-sdk-v<version>; docs/GARMIN-FACES.md): built for every product of its manifest, checked, and kept as
# SukraraFace-<its version>.barrel. A barrel isn't signed: no key.
if [ "${1:-}" = sdk ]; then
  MANIFEST="$GARMIN/sdk/manifest.xml"
  version="$(sed -n 's/.*<iq:barrel[^>]* version="\([^"]*\)".*/\1/p' "$MANIFEST")"
  out="$("$GARMIN/build.sh" sdk release | tail -1)" || die "apps/garmin/build.sh sdk release failed (the barrel)"
  tmp="$(mktemp -d)"
  TMPS+=("$tmp")
  bsdtar -xf "$out" -C "$tmp" 2>/dev/null || die "$out doesn't unpack (not a barrel)"
  grep -q "<iq:barrel[^>]* module=\"SukraraFace\" version=\"$version\"" "$tmp/manifest.xml" ||
    die "$out isn't the SukraraFace $version barrel"
  want="$(devices | wc -l | tr -d ' ')"
  [ "$want" -gt 0 ] || die "apps/garmin/sdk/manifest.xml lists no product"
  mkdir -p "$DIST"
  rm -f "$DIST"/SukraraFace*.barrel
  cp "$out" "$DIST/SukraraFace-$version.barrel"
  echo "verified: $out (SukraraFace $version, $want products)"
  ls -l "$DIST"/SukraraFace*.barrel
  exit 0
fi
APP="${1:-}"
case "$APP" in
  aura) NAME=SukraraAura ;;
  app) NAME=Sukrara ;;
  field) NAME=SukraraField ;;
  *) die "usage: garmin-release.sh <aura|app|field> <version> | <aura|app|field> --beta | --key-check" ;;
esac
shift
MANIFEST="$GARMIN/$APP/manifest.xml"
if [ "${1:-}" = "--beta" ]; then
  if [ -f "$PIN" ]; then key_check; else
    [ -f "$KEY" ] || die "no store key at $KEY"
    echo "store key: $KEY ($(pub_sha); not pinned yet: $PIN comes with the first public upload)"
  fi
  export CIQ_KEY="$KEY"
  out="$("$GARMIN/build.sh" "$APP" export-beta | tail -1)" || die "apps/garmin/build.sh $APP export-beta failed"
  beta_id="$(sed -n 's/.*<iq:application id="\([^"]*\)".*/\1/p' "$GARMIN/bin/beta/$APP/manifest.xml")"
  version="$(sed -n 's/.*<iq:application[^>]* version="\([^"]*\)".*/\1/p' "$MANIFEST")"
  verify_iq "$out" "$beta_id" "$version"
  mkdir -p "$DIST"
  cp "$out" "$DIST/$NAME-beta.iq"
  ls -l "$DIST/$NAME-beta.iq"
  exit 0
fi
VERSION="${1:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: garmin-release.sh <aura|app|field> <version> | --key-check"
key_check
manifest_version="$(sed -n 's/.*<iq:application[^>]* version="\([^"]*\)".*/\1/p' "$MANIFEST")"
[ "$manifest_version" = "$VERSION" ] || die "apps/garmin/$APP/manifest.xml says version $manifest_version, not $VERSION"
mkdir -p "$DIST"
rm -f "$DIST/$NAME"-*
export CIQ_KEY="$KEY"
# the sideload builds: one release .prg per device, together in <Name>-<version>-sideload.zip (<Name>-<device>.prg)
side="$(mktemp -d)"
TMPS+=("$side")
for device in $(devices); do
  out="$(CIQ_DEVICE="$device" "$GARMIN/build.sh" "$APP" release | tail -1)"
  "$GARMIN/tools/verify_prg.sh" "$APP" "$out"
  cp "$out" "$side/$NAME-$device.prg"
done
(cd "$side" && zip -q -X "$DIST/$NAME-$VERSION-sideload.zip" ./*.prg)
out="$("$GARMIN/build.sh" "$APP" export | tail -1)" || die "apps/garmin/build.sh $APP export failed (the store package: monkeyc -e -r)"
[ "${out##*.}" = "iq" ] || die "apps/garmin/build.sh $APP export made no .iq ($out)"
verify_iq "$out" "$(sed -n 's/.*<iq:application id="\([^"]*\)".*/\1/p' "$MANIFEST")" "$VERSION"
cp "$out" "$DIST/$NAME-$VERSION.iq"
ls -l "$DIST/$NAME"-*
