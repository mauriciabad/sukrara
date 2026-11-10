#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Packages build/Sukrara.app (run Bundle/bundle.sh first) as build/Sukrara-<version>.dmg (a window with the app, an
# arrow and an Applications shortcut; needs dmgbuild: `uv tool install dmgbuild`, else a plain image with the same
# two items) and build/Sukrara-mac-<version>.zip (ditto, keeps the signature). The dmg is signed with the certificate
# that signed the app, named by its SHA-1 (after a renewal two valid identities share a name), and checked by mounting
# it and verifying the app inside. Images are made and mounted with `diskutil image` (hdiutil's attach / create /
# detach are deprecated since macOS 27; dmgbuild still calls hdiutil itself).
# NOTARY_PROFILE=<profile>: notarize + staple the dmg (needs a Developer ID build). Without it,
# Gatekeeper asks on first launch (System Settings → Privacy & Security → Open Anyway).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(dirname "$HERE")/build
APP=$OUT/Sukrara.app
die() { echo "dmg.sh: $*" >&2; exit 1; }
[ -d "$APP" ] || die "no $APP: run Bundle/bundle.sh first"
codesign --verify --deep --strict "$APP" || die "$APP has an invalid signature: rebuild with Bundle/bundle.sh"
ID=$(codesign -dvv "$APP" 2>&1 | sed -n 's/^Authority=//p' | head -1)
case "$ID" in "Developer ID Application:"*) TS=--timestamp ;; *) TS=--timestamp=none ;; esac
if [ -n "${NOTARY_PROFILE:-}" ]; then
  [ "$TS" = --timestamp ] || die "NOTARY_PROFILE needs an app signed with a Developer ID Application identity"
  xcrun stapler validate -q "$APP" || die "$APP is not notarized: run NOTARY_PROFILE=$NOTARY_PROFILE Bundle/bundle.sh"
fi
VER=$(plutil -extract CFBundleShortVersionString raw "$APP/Contents/Info.plist")
BUILD=$(plutil -extract CFBundleVersion raw "$APP/Contents/Info.plist")
# Only a signed release build becomes an installer (DMG_FORCE=1 packages an ad hoc / --dev build for testing).
if [ "${DMG_FORCE:-}" != 1 ]; then
  [ -n "$ID" ] || die "$APP is ad hoc signed (bundle.sh --unsigned): widgets would not load; set DMG_FORCE=1 to package it"
  case "$BUILD" in *.*) die "$APP is a --dev build ($BUILD); set DMG_FORCE=1 to package it" ;; esac
fi
DMG=$OUT/Sukrara-$VER.dmg
W=$OUT/dmg
rm -rf "$W" "$DMG"
mkdir -p "$W"
# The signing certificate's SHA-1: the leaf the app carries (cert0, DER), so the dmg gets exactly the app's identity.
SHA=
if [ -n "$ID" ]; then
  codesign -d --extract-certificates="$W/cert" "$APP" 2>/dev/null || die "can't read the certificates $APP is signed with"
  SHA=$(shasum -a 1 "$W/cert0" | cut -d' ' -f1 | tr 'a-f' 'A-F')
  security find-identity -v -p codesigning | grep -q " $SHA " ||
    die "the identity that signed $APP ($ID, SHA-1 $SHA) isn't a valid code-signing identity in the keychain"
fi
if command -v dmgbuild >/dev/null; then
  swift "$HERE/dmg/background.swift" "$W"
  tiffutil -cathidpicheck "$W/background.png" "$W/background@2x.png" -out "$W/background.tiff" >/dev/null
  dmgbuild -s "$HERE/dmg/settings.py" -D app="$APP" -D background="$W/background.tiff" Sukrara "$DMG"
else
  echo "dmg.sh: dmgbuild not found: plain image without the window layout (uv tool install dmgbuild)" >&2
  mkdir -p "$W/root"
  cp -R "$APP" "$W/root/"
  ln -s /Applications "$W/root/Applications"
  diskutil image create from --format UDZO --volumeName Sukrara "$W/root" "$DMG" >/dev/null
fi
if [ -n "$SHA" ]; then codesign --force --sign "$SHA" $TS "$DMG"; fi
# The app as the user gets it: mount read-only and verify the copy inside.
MNT=$(mktemp -d "$OUT/mnt.XXXXXX")
DEV=
# Never leave the image attached or the mount point behind, whatever fails.
trap '[ -z "$DEV" ] || diskutil eject "$DEV" >/dev/null 2>&1 || true; rmdir "$MNT" 2>/dev/null || true' EXIT
# The first line names the image's own device (the volumes it holds follow): ejecting it detaches the image.
DEV=$(diskutil image attach --readOnly --nobrowse --mountPoint "$MNT" "$DMG" | awk 'NR == 1 {print $1}')
case "$DEV" in /dev/disk*) ;; *) die "attaching $DMG didn't name its device ('$DEV')" ;; esac
OK=1
codesign --verify --deep --strict "$MNT/Sukrara.app" || OK=0
diskutil eject "$DEV" >/dev/null
DEV=
rmdir "$MNT" 2>/dev/null || true  # diskutil removes a mount point it made; this one is ours
trap - EXIT
[ "$OK" = 1 ] || die "the app inside $DMG fails codesign --verify --deep --strict"
if [ -n "${NOTARY_PROFILE:-}" ]; then
  xcrun notarytool submit "$DMG" --keychain-profile "$NOTARY_PROFILE" --wait | tee "$OUT/notary-dmg.log"
  grep -q 'status: Accepted' "$OUT/notary-dmg.log" || die "notarization of $DMG failed (see $OUT/notary-dmg.log)"
  xcrun stapler staple "$DMG"
fi
ZIP=$OUT/Sukrara-mac-$VER.zip
rm -f "$ZIP"
ditto -c -k --keepParent "$APP" "$ZIP"
echo "built $DMG${ID:+ (signed: $ID, SHA-1 $SHA)} and $ZIP"
