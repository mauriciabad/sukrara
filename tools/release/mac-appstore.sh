#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the Mac App Store version for TestFlight (#186, docs/20-publishing.md › Mac App Store): Bundle/bundle.sh
# --app-store signed with the "Apple Distribution" identity and the Mac App Store profiles (app and widget, in
# ~/.apple-dev/profiles), verify-app.sh --app-store, then build/Sukrara-<version>-appstore.pkg signed with the "Mac
# Installer Distribution" identity (productbuild), checked with pkgutil, and validated by App Store Connect.
#   tools/release/mac-appstore.sh            build, package, validate (uploads nothing)
#   tools/release/mac-appstore.sh --upload   uploads that pkg as it is (nothing is built again) to App Store Connect,
#                                            where it becomes a TestFlight build: release.py's testflight step uploads
#                                            the gate's pkg. Only with the owner's OK: every upload is a new build there.
#   SIGN_ID / INSTALLER_ID   pick the identities by name or SHA-1 (default: the first valid one of each kind)
#   ASC_KEY                  the App Store Connect API key (default: ~/.apple-dev/release-AuthKey_<id>.p8, the App Manager key)
#   ASC_ISSUER_ID            its issuer (default: ~/.apple-dev/asc-issuer-id); without one, validation is skipped
# The key and the profiles stay in ~/.apple-dev (never the repo). The GitHub version is untouched: mac-release.sh.
set -eu
. "$(dirname "$0")/lib.sh"
root="$(repo_root)"
upload=0
for arg in "$@"; do
  case "$arg" in
    --upload) upload=1 ;;
    -h | --help) sed -n '5,16p' "$0"; exit 0 ;;
    *) echo "unknown argument '$arg' (--upload)" >&2; exit 2 ;;
  esac
done
if [ ! -e "$root/tools/check/strict" ]; then
  echo "refusing: tools/check/strict is missing (every registry file must exist before a release)" >&2; exit 2
fi
set -- $(version_info)
version="$1"
build="$root/apps/macos/build"
pkg="$build/Sukrara-$version-appstore.pkg"
checklist="docs/20-publishing.md › Mac App Store: signing"
die() { echo "mac-appstore.sh: $*" >&2; exit 2; }

# App Store Connect: the API key validates and uploads
key="${ASC_KEY:-$(ls "$HOME"/.apple-dev/release-AuthKey_*.p8 2>/dev/null | head -1)}"
issuer="${ASC_ISSUER_ID:-$(cat "$HOME/.apple-dev/asc-issuer-id" 2>/dev/null || true)}"
auth="--api-key $(basename "${key:-none}" .p8 | sed 's/^.*AuthKey_//') --api-issuer $issuer --p8-file-path $key"

if [ "$upload" = 1 ]; then
  [ -f "$pkg" ] || die "no $pkg: build and validate it first (tools/release/mac-appstore.sh)"
  [ -n "$key" ] && [ -n "$issuer" ] || die "--upload needs the API key and its issuer (ASC_KEY, ASC_ISSUER_ID)"
  echo "uploading $pkg (built $(stat -f %Sm "$pkg"))"
  # shellcheck disable=SC2086 # $auth is word-split on purpose (no spaces in its parts)
  xcrun altool --upload-package "$pkg" --platform macos $auth
  echo "uploaded: App Store Connect › Sukrara › TestFlight shows it once processed (minutes)"
  exit 0
fi

# The identities by SHA-1 (unambiguous after a renewal). Apple names the installer one "3rd Party Mac Developer
# Installer" in the keychain (older certificates) or "Mac Installer Distribution".
ids="$(security find-identity -v)"
pick() { # $1 = override, $2 = pattern of the kind
  if [ -n "$1" ]; then echo "$ids" | grep -F "$1" | head -1; else echo "$ids" | grep -E "$2" | head -1; fi
}
app_line="$(pick "${SIGN_ID:-}" '"(Apple Distribution|3rd Party Mac Developer Application):')"
pkg_line="$(pick "${INSTALLER_ID:-}" '"(3rd Party Mac Developer Installer|Mac Installer Distribution):')"
[ -n "$app_line" ] || die "no Apple Distribution identity in the keychain ($checklist)"
[ -n "$pkg_line" ] || die "no Mac Installer Distribution identity in the keychain ($checklist)"
SIGN_ID="$(echo "$app_line" | awk '{print $2}')"
installer="$(echo "$pkg_line" | awk '{print $2}')"
echo "app: $(echo "$app_line" | cut -d'"' -f2)"
echo "pkg: $(echo "$pkg_line" | cut -d'"' -f2)"

SIGN_ID="$SIGN_ID" "$root/apps/macos/Bundle/bundle.sh" --app-store
"$root/tools/release/verify-app.sh" "$build/Sukrara.app" --app-store
codesign -dvv "$build/Sukrara.app" 2>&1 | grep -Eq 'Authority=(Apple Distribution|3rd Party Mac Developer Application):' \
  || die "the app isn't signed for the Mac App Store"

rm -f "$pkg"
# On macOS 27 pkgbuild (inside productbuild) prints "write: Permission denied" four times whenever it writes a payload,
# for any payload, even an empty --root (not with --analyze or --nopayload); the pkg is complete and pkgutil and
# altool check it below. Only that exact line is dropped, and only when productbuild succeeds.
err="$(mktemp)"
if ! productbuild --component "$build/Sukrara.app" /Applications --sign "$installer" "$pkg" 2>"$err"; then
  cat "$err" >&2; rm -f "$err"; die "productbuild failed"
fi
grep -vx 'write: Permission denied' "$err" >&2 || true
rm -f "$err"
pkgutil --check-signature "$pkg" | grep -Eq '(3rd Party Mac Developer Installer|Mac Installer Distribution):' \
  || die "$pkg isn't signed by the installer identity"
echo "packaged $pkg ($(plutil -extract CFBundleVersion raw -o - "$build/Sukrara.app/Contents/Info.plist"))"

if [ -z "$key" ] || [ -z "$issuer" ]; then
  echo "not validated: no App Store Connect API key or issuer (ASC_KEY, ASC_ISSUER_ID or ~/.apple-dev/asc-issuer-id)"
  exit 0
fi
# shellcheck disable=SC2086
xcrun altool --validate-app "$pkg" --platform macos $auth
echo "ready to upload: tools/release/mac-appstore.sh --upload (only with the owner's OK; nothing is built again)"
