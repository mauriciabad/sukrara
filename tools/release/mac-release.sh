#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the Mac release for a GitHub release: Bundle/bundle.sh, verify-app.sh, Bundle/dmg.sh, then verifies the dmg.
# Leaves apps/macos/build/Sukrara-<version>.dmg and Sukrara-mac-<version>.zip, and (notarized only) the release's item
# in Sparkle's appcast, docs/appcast.xml (#91).
#
# Developer ID and notarization switch on by themselves once the credentials exist (#91, docs/20-publishing.md › Mac
# release: Developer ID and notarization):
# - a valid "Developer ID Application" identity in the keychain (SIGN_ID picks one by name or SHA-1) → the app is
#   signed with it (hardened runtime, secure timestamp), and then a release must be notarized: the notarytool keychain
#   profile NOTARY_PROFILE (default "sukrara") must answer, else the release is refused. bundle.sh notarizes and
#   staples the app, dmg.sh the dmg; verify-app.sh --notarized and the dmg checks below prove both (Developer ID
#   authority, hardened runtime, secure timestamp, stapled ticket, Gatekeeper "Notarized Developer ID").
# - none → the Apple Development identity of team QWUV9H4A3G, not notarized: Gatekeeper asks once on first open
#   (System Settings › Privacy & Security › Open Anyway).
# Sparkle (#91, docs/20-publishing.md › Sparkle: the update feed): a notarized dmg is signed with the EdDSA key in the
# login keychain (Sparkle's sign_update from the SwiftPM artifact; the keychain may ask once to allow it) and added to
# docs/appcast.xml by tools/release/appcast.py: the GitHub release's dmg URL, the signature and length, the
# "prerelease" channel for a 0.x release, the CHANGELOG.md section as notes. Only the dmg is the update (Sparkle installs
# from it); the zip stays a plain download. Commit docs/appcast.xml after the GitHub release with the dmg is published.
# An unnotarized build never goes in the appcast.
# Refuses unless tools/check/strict exists. Prints the dmg and zip paths on the last two lines.
set -eu
. "$(dirname "$0")/lib.sh"
root="$(repo_root)"
if [ ! -e "$root/tools/check/strict" ]; then
  echo "refusing: tools/check/strict is missing (every registry file must exist before a release)" >&2; exit 2
fi
set -- $(version_info)
version="$1" code="$2" kind="$3"
build="$root/apps/macos/build"
checklist="docs/20-publishing.md › Mac release: Developer ID and notarization"
sparkle_doc="docs/20-publishing.md › Sparkle: the update feed"

# The identity: SIGN_ID when given, else the first valid Developer ID Application identity (bundle.sh checks its team).
ids="$(security find-identity -v -p codesigning)"
if [ -n "${SIGN_ID:-}" ]; then line="$(echo "$ids" | grep -F "$SIGN_ID" | head -1)"
else line="$(echo "$ids" | grep -F '"Developer ID Application:' | head -1)"; fi
case "$line" in
  *'"Developer ID Application:'*)
    SIGN_ID="$(echo "$line" | awk '{print $2}')"  # its SHA-1: unambiguous after a certificate renewal
    NOTARY_PROFILE="${NOTARY_PROFILE:-sukrara}"
    # Checks the stored credentials with Apple (a release needs the network anyway).
    if ! xcrun notarytool history --keychain-profile "$NOTARY_PROFILE" >/dev/null 2>&1; then
      echo "refusing: Developer ID identity found, but the notarytool keychain profile '$NOTARY_PROFILE' doesn't answer" >&2
      echo "  (xcrun notarytool store-credentials $NOTARY_PROFILE …, see $checklist)" >&2
      exit 2
    fi
    export SIGN_ID NOTARY_PROFILE
    notarize=1
    echo "Developer ID: $(echo "$line" | cut -d'"' -f2), notarizing with profile $NOTARY_PROFILE"
    ;;
  *)
    if [ -n "${NOTARY_PROFILE:-}" ]; then
      echo "refusing: NOTARY_PROFILE is set but there is no Developer ID Application identity (see $checklist)" >&2; exit 2
    fi
    notarize=0
    ;;
esac

"$root/apps/macos/Bundle/bundle.sh"
if [ "$notarize" = 1 ]; then "$root/tools/release/verify-app.sh" "$build/Sukrara.app" --notarized
else "$root/tools/release/verify-app.sh" "$build/Sukrara.app"; fi
"$root/apps/macos/Bundle/dmg.sh"
dmg="$build/Sukrara-$version.dmg"
zip="$build/Sukrara-mac-$version.zip"
codesign --verify --strict "$dmg"
[ -s "$zip" ] || { echo "no $zip" >&2; exit 1; }
if [ "$notarize" = 1 ]; then
  xcrun stapler validate "$dmg"
  # What Gatekeeper says about the downloaded dmg: accepted, source=Notarized Developer ID.
  gk="$(spctl --assess --type open --context context:primary-signature -vv "$dmg" 2>&1 || true)"
  echo "$gk"
  case "$gk" in *accepted*"Notarized Developer ID"*) ;; *) echo "Gatekeeper doesn't accept $dmg as notarized" >&2; exit 1 ;; esac
  echo "notarized and stapled: Gatekeeper opens it without Open Anyway"
  # The update: the stapled dmg, signed with the EdDSA key the app's SUPublicEDKey names, into the appcast.
  sign_update="$root/apps/macos/.build/artifacts/sparkle/Sparkle/bin/sign_update"
  [ -x "$sign_update" ] || { echo "no $sign_update (bundle.sh's swift build resolves Sparkle)" >&2; exit 1; }
  sig="$("$sign_update" -p "$dmg")" || { echo "sign_update failed: is the Sparkle key in the login keychain? (see $sparkle_doc)" >&2; exit 1; }
  "$sign_update" --verify "$dmg" "$sig" >/dev/null 2>&1 || { echo "the dmg's EdDSA signature doesn't verify" >&2; exit 1; }
  # the key that signed it is the one every installed copy trusts
  want_key="$(/usr/libexec/PlistBuddy -c 'Print :SUPublicEDKey' "$build/Sukrara.app/Contents/Info.plist")"
  have_key="$("$root/apps/macos/.build/artifacts/sparkle/Sparkle/bin/generate_keys" -p 2>/dev/null || true)"
  [ "$have_key" = "$want_key" ] || { echo "the keychain's Sparkle key ($have_key) isn't the app's SUPublicEDKey ($want_key): see $sparkle_doc" >&2; exit 1; }
  python3 "$root/tools/release/appcast.py" --version "$version" --build "$code" --kind "$kind" --signature "$sig" \
    --length "$(stat -f %z "$dmg")"
  echo "docs/appcast.xml: commit it once the GitHub release v$version with $(basename "$dmg") is published"
else
  echo "not notarized (no Developer ID identity): Gatekeeper asks once on first open (Open Anyway); not in the appcast"
fi
echo "$dmg"
echo "$zip"
