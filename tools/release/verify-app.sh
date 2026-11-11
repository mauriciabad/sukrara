#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks a Mac bundle before it is installed or released:
#   tools/release/verify-app.sh <Sukrara.app> [--unsigned | --notarized | --app-store]
# - bundle id app.mauri.sukrara, version / build = tools/release/version.sh (dev builds may add .<yyMMddHHmm>)
# - the widget extension (app.mauri.sukrara.widget) is inside
# - the camera's usage description (the QR scanner; macOS ends an app that opens the camera without one)
# - signed (unless --unsigned): codesign --verify --deep --strict, team QWUV9H4A3G on the app and the appex; the
#   camera entitlement (the hardened runtime blocks the camera without it); the entitlements only a provisioning
#   profile grants all there with an embedded profile, none without
# - a release build: no debug entry points in any binary (debugsend, DebugFakes, Synthetic*, the snapshot renderer)
# - every file readable by everyone (App Store Connect refuses files only root can read: 90255)
# - --notarized (mac-release.sh with a Developer ID, #91): the app and the appex signed by "Developer ID Application"
#   with the hardened runtime and a secure timestamp, the notarization ticket stapled, and Gatekeeper accepting the app
#   as "Notarized Developer ID"
# - the GitHub version (no flag, --unsigned, --notarized): SukraraDistribution github and the app not sandboxed
# - --app-store (bundle.sh --app-store, #186): SukraraDistribution appStore; the app sandboxed with exactly what it uses
#   (network client, Bluetooth, camera, user-selected files, the App Group; never network.server); with an "Apple
#   Distribution" signature, a Mac App Store profile (no device list) embedded in the app and the widget
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
app="${1:?usage: verify-app.sh <Sukrara.app> [--unsigned | --notarized | --app-store]}"
mode="${2:-}"
case "$mode" in "" | --unsigned | --notarized | --app-store) ;;
  *) echo "unknown argument '$mode' (--unsigned, --notarized, --app-store)" >&2; exit 2 ;; esac
fail=0
bad() { echo "FAIL $*"; fail=1; }
ok() { echo "ok   $*"; }
plist() { /usr/libexec/PlistBuddy -c "Print :$2" "$1/Contents/Info.plist" 2>/dev/null || true; }
VI=$(version_info) || exit 1
set -- $VI
want_name="$1" want_code="$2"

id="$(plist "$app" CFBundleIdentifier)"
[ "$id" = app.mauri.sukrara ] && ok "bundle id $id" || bad "bundle id '$id' (want app.mauri.sukrara)"
[ "$(plist "$app" CFBundleShortVersionString)" = "$want_name" ] && ok "version $want_name" || bad "CFBundleShortVersionString isn't $want_name"
case "$(plist "$app" CFBundleVersion)" in
  "$want_code" | "$want_code".*) ok "build $(plist "$app" CFBundleVersion)" ;;
  *) bad "CFBundleVersion '$(plist "$app" CFBundleVersion)' (want $want_code[.yyMMddHHmm])" ;;
esac
[ -n "$(plist "$app" NSCameraUsageDescription)" ] && ok "camera usage description (QR scanner)" \
  || bad "no NSCameraUsageDescription: macOS ends the app when the QR scanner opens the camera"
want_dist=github
[ "$mode" = --app-store ] && want_dist=appStore
dist="$(plist "$app" SukraraDistribution)"
[ "$dist" = "$want_dist" ] && ok "SukraraDistribution $dist" || bad "SukraraDistribution '$dist' (want $want_dist)"
appex="$app/Contents/PlugIns/SukraraWidget.appex"
if [ -d "$appex" ] && [ "$(plist "$appex" CFBundleIdentifier)" = app.mauri.sukrara.widget ]; then ok "widget extension inside"
else bad "SukraraWidget.appex (app.mauri.sukrara.widget) missing"; fi

if [ "$mode" != --unsigned ]; then
  if codesign --verify --deep --strict "$app" 2>/dev/null; then ok "signature verifies"; else bad "codesign --verify --deep --strict fails"; fi
  for b in "$app" "$appex"; do
    team="$(codesign -dv "$b" 2>&1 | sed -n 's/^TeamIdentifier=//p')"
    [ "$team" = QWUV9H4A3G ] && ok "team $team: $(basename "$b")" || bad "$(basename "$b") team '$team' (want QWUV9H4A3G)"
  done
  # Only a provisioning profile grants these (bundle.sh): with one embedded the app has them all; without, none (macOS
  # refuses to launch an app that claims them without a profile).
  ents="$(codesign -d --entitlements - "$app" 2>/dev/null || true)"
  # the QR scanner: the hardened runtime blocks the camera without it (no provisioning profile needed)
  echo "$ents" | grep -q com.apple.security.device.camera && ok "com.apple.security.device.camera (QR scanner)" \
    || bad "no com.apple.security.device.camera: the hardened runtime blocks the QR scanner's camera"
  for e in com.apple.developer.usernotifications.time-sensitive keychain-access-groups \
    com.apple.developer.associated-domains; do
    if [ -f "$app/Contents/embedded.provisionprofile" ]; then
      echo "$ents" | grep -q "$e" && ok "$e (provisioning profile)" || bad "a provisioning profile but no $e"
    elif echo "$ents" | grep -q "$e"; then bad "$e without a provisioning profile: macOS won't launch it"; fi
  done
  [ -f "$app/Contents/embedded.provisionprofile" ] \
    || echo "note no provisioning profile: alarms not Time Sensitive, login keychain, no universal links"
fi

if [ "$mode" != --unsigned ]; then
  sandbox_ents="com.apple.security.network.client com.apple.security.device.bluetooth
    com.apple.security.files.user-selected.read-write com.apple.security.application-groups"
  if [ "$mode" = --app-store ]; then
    echo "$ents" | grep -q com.apple.security.app-sandbox && ok "app sandboxed (App Store)" || bad "the App Store version isn't sandboxed"
    for e in $sandbox_ents; do echo "$ents" | grep -q "$e" && ok "$e" || bad "the App Store version lacks $e"; done
    if echo "$ents" | grep -q com.apple.security.network.server; then bad "network.server: the Mac app listens on nothing"; fi
    wents="$(codesign -d --entitlements - "$appex" 2>/dev/null || true)"
    echo "$wents" | grep -q com.apple.security.app-sandbox && ok "widget sandboxed" || bad "the widget isn't sandboxed"
    case "$(codesign -dvv "$app" 2>&1)" in
      *"Authority=Apple Distribution:"* | *"Authority=3rd Party Mac Developer Application:"*)
        for b in "$app" "$appex"; do
          prof="$b/Contents/embedded.provisionprofile"
          decoded="$(mktemp)"
          # a store profile lists no Macs and doesn't provision all of them (that's Developer ID / development)
          if [ -f "$prof" ] && security cms -D -i "$prof" -o "$decoded" 2>/dev/null \
            && ! plutil -extract ProvisionedDevices raw -o - "$decoded" >/dev/null 2>&1 \
            && ! plutil -extract ProvisionsAllDevices raw -o - "$decoded" >/dev/null 2>&1; then
            ok "Mac App Store profile: $(basename "$b")"
          else bad "$(basename "$b") has no Mac App Store provisioning profile"; fi
          rm -f "$decoded"
        done ;;
      *) echo "note signed to run on this Mac, not for upload (tools/release/mac-appstore.sh signs for upload)" ;;
    esac
  elif echo "$ents" | grep -q com.apple.security.app-sandbox; then
    bad "the GitHub version is sandboxed (only bundle.sh --app-store is)"
  else ok "not sandboxed (GitHub version)"; fi
fi

if [ "$mode" = --notarized ]; then
  for b in "$app" "$appex"; do
    info="$(codesign -dvv "$b" 2>&1 || true)"
    case "$info" in *"Authority=Developer ID Application:"*) ok "Developer ID: $(basename "$b")" ;;
      *) bad "$(basename "$b") isn't signed by a Developer ID Application identity" ;; esac
    case "$info" in *"flags="*"(runtime)"*) ok "hardened runtime: $(basename "$b")" ;;
      *) bad "$(basename "$b") has no hardened runtime (notarization requires it)" ;; esac
    # "Timestamp=" is a secure timestamp from Apple; "Signed Time=" is the Mac's own clock (--timestamp=none)
    case "$info" in *"Timestamp="*) ok "secure timestamp: $(basename "$b")" ;;
      *) bad "$(basename "$b") has no secure timestamp (notarization requires it)" ;; esac
  done
  if xcrun stapler validate -q "$app" 2>/dev/null; then ok "notarization ticket stapled"; else bad "no stapled notarization ticket"; fi
  gk="$(spctl --assess --type execute -vv "$app" 2>&1 || true)"
  case "$gk" in *accepted*"Notarized Developer ID"*) ok "Gatekeeper: Notarized Developer ID" ;;
    *) bad "Gatekeeper doesn't accept it as notarized: $(echo "$gk" | tr '\n' ' ')" ;; esac
fi

# The snapshot renderer's own strings ("sukrara-snap: …", "usage: sukrara-snap <…", its "sukrara-snap-<uuid>" scratch
# dir), not the app's launch error that only names it ("… are rendered by sukrara-snap.").
found=""
for bin in "$app"/Contents/MacOS/* "$appex"/Contents/MacOS/*; do
  [ -f "$bin" ] || continue
  if strings -a "$bin" | grep -Eq 'debugsend|DebugFakes|Synthetic[A-Z]|sukrara-snap[ :-]'; then found="$found $(basename "$bin")"; fi
done
[ -z "$found" ] && ok "no debug entry points in the binaries" || bad "debug code compiled into:$found (build with -c release)"
unreadable=$(find "$app" ! -perm -o+r | sed "s|^$app/||")
[ -z "$unreadable" ] && ok "every file readable by everyone" || bad "not readable by everyone (installed as root):" $unreadable
exit $fail
