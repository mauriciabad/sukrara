#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds apps/macos/build/Sukrara.app with Contents/PlugIns/SukraraWidget.appex from `swift build -c release`.
#   Bundle/bundle.sh             signed with SIGN_ID, else the first identity of team QWUV9H4A3G that a provisioning
#                                profile in SUKRARA_PROFILES_DIR lists, else the first "Apple Development" one (macOS
#                                only loads widgets whose signature carries a Team ID; a team-prefixed App Group needs no
#                                profile). Hardened runtime, inside-out (appex, then app).
#   SUKRARA_PROFILES_DIR=<dir>   the provisioning profiles (*.provisionprofile; default ~/.apple-dev/profiles). The
#                                newest unexpired one for app.mauri.sukrara that lists the signing certificate is
#                                embedded and the app signed with app-profile.entitlements.in: Time Sensitive alarms, the
#                                data-protection keychain, universal links for https://sukrara.mauri.app/join. The
#                                widget's (app.mauri.sukrara.widget) is embedded the same way when there is one. Without
#                                a profile for the app: app.entitlements.in, as before. A profile for the app that
#                                can't sign this build (expired, another certificate) stops the build: a build without
#                                the data-protection keychain can't read the follower key a profile build moved there.
#   Bundle/bundle.sh --no-profile  signed without a provisioning profile even when one is there.
#   Bundle/bundle.sh --unsigned  ad hoc signature (CI, no identity, no profile): verifies structurally, widgets won't
#                                load. AD_HOC=1 does the same.
#   Bundle/bundle.sh --dev       CFBundleVersion <versionCode>.<UTC yyMMddHHmm>: chronod keeps a widget's gallery
#                                previews while CFBundleVersion stays the same.
#   NOTARY_PROFILE=<profile>     notarize + staple (needs a Developer ID identity).
# Version: <repo>/VERSION (x.y.z); CFBundleVersion = versionCode = x*1000000 + y*1000 + z (0.12.0 → 12000).
# Never copy the result into /Applications or ~/Applications by hand: tools/install.sh mac does that.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
PKG=$(dirname "$HERE")
REPO=$(cd "$PKG/../.." && pwd)
TEAM=QWUV9H4A3G
GROUP=$TEAM.app.mauri.sukrara
APP_ID=$TEAM.app.mauri.sukrara
WIDGET_ID=$TEAM.app.mauri.sukrara.widget
PROFILES_DIR=${SUKRARA_PROFILES_DIR:-$HOME/.apple-dev/profiles}
die() { echo "bundle.sh: $*" >&2; exit 1; }

UNSIGNED=0; DEV=0; NO_PROFILE=0
for arg in "$@"; do
  case "$arg" in
    --unsigned) UNSIGNED=1 ;;
    --no-profile) NO_PROFILE=1 ;;
    --dev) DEV=1 ;;
    -h|--help) sed -n '5,25p' "$0"; exit 0 ;;
    *) die "unknown argument '$arg' (--unsigned, --no-profile, --dev)" ;;
  esac
done
[ "${AD_HOC:-}" = 1 ] && UNSIGNED=1
[ "$UNSIGNED" = 1 ] && NO_PROFILE=1
WORK=$(mktemp -d "${TMPDIR:-/tmp}/sukrara-bundle.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

# Provisioning profiles. decode <file>: its plist into $WORK/p.plist; pget <keypath>: a string, date, bool or data
# (base64) of it; pvalues <keypath>: a string or an array's strings, one per line.
decode() { security cms -D -i "$1" -o "$WORK/p.plist" 2>/dev/null; }
pget() { plutil -extract "$1" raw -o - "$WORK/p.plist" 2>/dev/null; }
pvalues() {
  V=$(plutil -extract "$1" json -o - "$WORK/p.plist" 2>/dev/null) || V=$(pget "$1") || return 0
  echo "$V" | tr -d '[]"' | tr ',' '\n'
}
profile_app_id() { decode "$1" && pget 'Entitlements.com\.apple\.application-identifier'; }
UDID=
udid() {
  [ -n "$UDID" ] || UDID=$(system_profiler SPHardwareDataType | awk -F': ' '/Provisioning UDID/ {print $2}')
  echo "$UDID"
}
# Why profile $1 can't sign app id $2 with the identity whose SHA-1 is $3 (prints nothing when it can).
unfit() {
  [ "$(profile_app_id "$1")" = "$2" ] || { echo "not for $2"; return; }
  EXP=$(pget ExpirationDate)
  awk -v e="$EXP" -v n="$(date -u +%Y-%m-%dT%H:%M:%SZ)" 'BEGIN { exit !(e > n) }' || { echo "expired $EXP"; return; }
  i=0; LISTED=0
  while C=$(pget "DeveloperCertificates.$i"); do
    [ "$(echo "$C" | base64 -D | shasum -a 1 | awk '{print toupper($1)}')" = "$3" ] && LISTED=1
    i=$((i + 1))
  done
  [ "$LISTED" = 1 ] || { echo "doesn't list the signing certificate"; return; }
  if [ "$(pget ProvisionsAllDevices)" != true ]; then
    pvalues ProvisionedDevices | grep -qx "$(udid)" || echo "doesn't list this Mac (Provisioning UDID $(udid))"
  fi
}
# The newest-expiring profile in PROFILES_DIR that can sign app id $1 with the identity $2 (nothing: none).
profile_for() {
  BEST=; BEST_EXP=
  for F in "$PROFILES_DIR"/*.provisionprofile; do
    [ -f "$F" ] && [ -z "$(unfit "$F" "$1" "$2")" ] || continue
    decode "$F"; EXP=$(pget ExpirationDate)
    if [ -z "$BEST" ] || awk -v e="$EXP" -v b="$BEST_EXP" 'BEGIN { exit !(e > b) }'; then BEST=$F; BEST_EXP=$EXP; fi
  done
  echo "$BEST"
}
# What app-profile.entitlements.in asks for that profile $1 doesn't grant (prints nothing when it grants it all).
ungranted() {
  decode "$1"
  [ "$(pget 'Entitlements.com\.apple\.developer\.usernotifications\.time-sensitive')" = true ] \
    || echo "Time Sensitive Notifications"
  pvalues 'Entitlements.com\.apple\.developer\.associated-domains' | grep -qx -e '\*' -e 'applinks:sukrara.mauri.app' \
    || echo "Associated Domains"
  pvalues 'Entitlements.keychain-access-groups' | grep -qx -e "$TEAM\.\*" -e "$APP_ID" \
    || echo "keychain-access-groups $APP_ID"
}

# Version
VERSION=$(tr -d ' \n' < "$REPO/VERSION")
echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || die "VERSION '$VERSION' is not x.y.z"
CODE=$(echo "$VERSION" | awk -F. '{ print $1 * 1000000 + $2 * 1000 + $3 }')
BUILD=$CODE
[ "$DEV" = 1 ] && BUILD="$CODE.$(date -u +%y%m%d%H%M)"

# Identity and provisioning profiles (before building, so a missing one fails fast)
SIGN=-; TS=--timestamp=none; PROFILE=; WIDGET_PROFILE=
if [ "$UNSIGNED" = 0 ]; then
  # Valid identities as `<sha1> "<name>"`. Everything below works on the SHA-1: after a certificate renewal two
  # valid identities share a name and signing by name is ambiguous.
  IDS=$(security find-identity -v -p codesigning | awk '/"/ {print $2, substr($0, index($0, "\""))}')
  LINE=
  if [ -n "${SIGN_ID:-}" ]; then
    LINE=$(echo "$IDS" | grep -F "$SIGN_ID" | head -1)
    [ -n "$LINE" ] || die "SIGN_ID '$SIGN_ID' is not a valid code-signing identity"
  elif [ "$NO_PROFILE" = 0 ]; then
    # the identity a profile for the app lists (the Developer ID one with the published profiles)
    for S in $(echo "$IDS" | awk '{print $1}'); do
      [ -n "$(profile_for "$APP_ID" "$S")" ] && { LINE=$(echo "$IDS" | grep "^$S " | head -1); break; }
    done
  fi
  if [ -z "$LINE" ]; then
    # The name's "(…)" is the personal id, not the team: the team is checked on the certificate below.
    LINE=$(echo "$IDS" | grep -F '"Apple Development:' | head -1)
    [ -n "$LINE" ] || die "no \"Apple Development\" identity in the keychain (Xcode → Settings → Accounts → Manage
  Certificates), or pass --unsigned"
  fi
  SHA=${LINE%% *}
  NAME=${LINE#* }
  # The certificate with exactly this SHA-1 (find-certificate -Z prints "SHA-1 hash: …" before each PEM).
  CERT_TEAM=$(security find-certificate -a -Z -p 2>/dev/null \
    | awk -v sha="$SHA" '/^SHA-1 hash:/ {keep = ($3 == sha)} keep && !/^SHA-1 hash:|^SHA-256 hash:/' \
    | openssl x509 -noout -subject -nameopt multiline 2>/dev/null | awk -F' = ' '/organizationalUnitName/{print $2; exit}')
  [ "$CERT_TEAM" = "$TEAM" ] || die "identity $NAME belongs to team '${CERT_TEAM:-none}', not $TEAM: the App Group
  $GROUP would not be granted"
  SIGN=$SHA
  case "$NAME" in '"Developer ID Application:'*) TS=--timestamp ;; esac
  if [ "$NO_PROFILE" = 0 ]; then
    PROFILE=$(profile_for "$APP_ID" "$SHA")
    if [ -n "$PROFILE" ]; then
      MISSING=$(ungranted "$PROFILE" | paste -sd, -)
      [ -z "$MISSING" ] || die "$PROFILE doesn't grant: $MISSING. Enable it on the App ID app.mauri.sukrara and
  download the profile again (docs/20-publishing.md), or pass --no-profile"
      WIDGET_PROFILE=$(profile_for "$WIDGET_ID" "$SHA")
    else
      # a profile for the app that can't sign this build: stop rather than build one that can't read the follower key
      WHY=$(for F in "$PROFILES_DIR"/*.provisionprofile; do
        [ -f "$F" ] && [ "$(profile_app_id "$F")" = "$APP_ID" ] && echo "  $(basename "$F"): $(unfit "$F" "$APP_ID" "$SHA")"
      done || true)
      [ -z "$WHY" ] || die "no provisioning profile in $PROFILES_DIR can sign $APP_ID with $NAME:
$WHY
  Renew it or sign with the identity it lists (SIGN_ID); --no-profile builds without (the follower key that a profile
  build moved into the data-protection keychain is then out of reach)"
    fi
  fi
fi

# Resources: the brand icon and the alert sounds
first_existing() { for p in "$@"; do [ -e "$p" ] && { echo "$p"; return; }; done; }
ICON=$(first_existing "$REPO/assets/brand/sukrara/macos/AppIcon.icns")
SOUNDS=$(first_existing "$REPO/contracts/alerts/sounds/mac")
missing() {
  if [ "$UNSIGNED" = 1 ]; then echo "bundle.sh: warning: $* (unsigned build continues)" >&2
  else die "$* (a signed build ships alert sounds and the icon)"; fi
}
[ -n "$ICON" ] || missing "no AppIcon.icns"
[ -n "$SOUNDS" ] && ls "$SOUNDS"/*.caf >/dev/null 2>&1 || missing "no alert sounds (*.caf)"

cd "$PKG"
swift build -c release --product Sukrara
swift build -c release --product SukraraWidget
BIN=$(swift build -c release --show-bin-path)

OUT=$PKG/build
APP=$OUT/Sukrara.app
APPEX=$APP/Contents/PlugIns/SukraraWidget.appex
rm -rf "$APP" "$OUT/entitlements"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/Sounds" "$APPEX/Contents/MacOS" "$OUT/entitlements"
cp "$BIN/Sukrara" "$APP/Contents/MacOS/Sukrara"
cp "$BIN/SukraraWidget" "$APPEX/Contents/MacOS/SukraraWidget"
# SwiftPM resource bundles (the windows' String Catalog): a release `Bundle.module` looks only in Contents/Resources
# and traps when the bundle is missing, so a missing copy is a crash on the first localized text.
[ -d "$BIN/Sukrara_SukraraApp.bundle" ] || die "no Sukrara_SukraraApp.bundle in $BIN"
cp -R "$BIN/Sukrara_SukraraApp.bundle" "$APP/Contents/Resources/"
[ -d "$APP/Contents/Resources/Sukrara_SukraraApp.bundle/Contents/Resources/es.lproj" ] || die "no es.lproj in the resource bundle"
# The Info.plist's privacy descriptions in Spanish (the English is in Info.plist.in)
cp -R "$HERE/es.lproj" "$APP/Contents/Resources/"
[ -f "$APP/Contents/Resources/es.lproj/InfoPlist.strings" ] || die "no es.lproj/InfoPlist.strings in the app"
[ -n "$ICON" ] && cp "$ICON" "$APP/Contents/Resources/AppIcon.icns"
[ -n "$SOUNDS" ] && cp "$SOUNDS"/*.caf "$APP/Contents/Resources/Sounds/" 2>/dev/null || true

fill() {
  sed -e "s/@VERSION@/$VERSION/g" -e "s/@BUILD@/$BUILD/g" -e "s/@GROUP@/$GROUP/g" -e "s/@TEAM@/$TEAM/g" "$1" > "$2"
  plutil -lint -s "$2"
}
fill "$HERE/Info.plist.in" "$APP/Contents/Info.plist"
# without CFBundleLocalizations the app's nested bundles resolve to English only: Spanish would never show
plutil -extract CFBundleLocalizations json -o - "$APP/Contents/Info.plist" | grep -q '"es"' \
  || die "Info.plist has no CFBundleLocalizations with es"
fill "$HERE/Widget-Info.plist.in" "$APPEX/Contents/Info.plist"
# With a provisioning profile: embedded, and the entitlements only it grants (the app's are checked by `ungranted`).
if [ -n "$PROFILE" ]; then
  cp "$PROFILE" "$APP/Contents/embedded.provisionprofile"
  fill "$HERE/app-profile.entitlements.in" "$OUT/entitlements/app.plist"
else
  fill "$HERE/app.entitlements.in" "$OUT/entitlements/app.plist"
fi
if [ -n "$WIDGET_PROFILE" ]; then
  cp "$WIDGET_PROFILE" "$APPEX/Contents/embedded.provisionprofile"
  fill "$HERE/widget-profile.entitlements.in" "$OUT/entitlements/widget.plist"
else
  fill "$HERE/widget.entitlements.in" "$OUT/entitlements/widget.plist"
fi

# No extended attributes or .DS_Store inside the bundle: they break `codesign --strict`.
xattr -cr "$APP"
find "$APP" -name .DS_Store -delete
# Inside-out: the extension first, then the app (which seals the extension's signature into its own).
codesign --force --sign "$SIGN" --options runtime $TS --entitlements "$OUT/entitlements/widget.plist" "$APPEX"
codesign --force --sign "$SIGN" --options runtime $TS --entitlements "$OUT/entitlements/app.plist" "$APP"
codesign --verify --deep --strict "$APP"
for C in "$APP" "$APPEX"; do
  if codesign -d --entitlements - "$C" 2>/dev/null | grep -q get-task-allow; then die "$C has get-task-allow"; fi
done
# the QR scanner: the hardened runtime blocks the camera without the entitlement, and macOS ends an app that opens it
# without a usage description
codesign -d --entitlements - "$APP" 2>/dev/null | grep -q com.apple.security.device.camera \
  || die "$APP was signed without com.apple.security.device.camera (the QR scanner's camera)"
plutil -extract NSCameraUsageDescription raw -o - "$APP/Contents/Info.plist" >/dev/null 2>&1 \
  || die "Info.plist has no NSCameraUsageDescription"
# a profile build carries what the profile grants (else the app falls back to the login keychain without a word)
if [ -n "$PROFILE" ]; then
  SIGNED=$(codesign -d --entitlements - "$APP" 2>/dev/null)
  for E in com.apple.developer.usernotifications.time-sensitive keychain-access-groups \
    com.apple.developer.associated-domains com.apple.application-identifier; do
    echo "$SIGNED" | grep -q "$E" || die "$APP was signed without $E"
  done
fi

if [ -n "${NOTARY_PROFILE:-}" ]; then
  [ "$TS" = --timestamp ] || die "NOTARY_PROFILE needs a Developer ID Application identity (SIGN_ID)"
  ZIP=$OUT/Sukrara-notarize.zip; rm -f "$ZIP"
  ditto -c -k --keepParent "$APP" "$ZIP"
  xcrun notarytool submit "$ZIP" --keychain-profile "$NOTARY_PROFILE" --wait | tee "$OUT/notary.log"
  rm -f "$ZIP"
  grep -q 'status: Accepted' "$OUT/notary.log" || die "notarization failed (see $OUT/notary.log)"
  xcrun stapler staple "$APP"
fi

if [ "$SIGN" = - ]; then SIGNED_BY="ad hoc, widgets won't load"; else SIGNED_BY="$NAME"; fi
if [ -n "$PROFILE" ]; then
  decode "$PROFILE"
  SIGNED_BY="$SIGNED_BY, profile \"$(pget Name)\" until $(pget ExpirationDate)"
  [ -n "$WIDGET_PROFILE" ] || SIGNED_BY="$SIGNED_BY (none for the widget)"
elif [ "$SIGN" != - ]; then
  SIGNED_BY="$SIGNED_BY, no provisioning profile: alarms not Time Sensitive, login keychain, no universal links"
fi
echo "built $APP $VERSION ($BUILD), signed: $SIGNED_BY"
