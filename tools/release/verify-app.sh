#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks a Mac bundle before it is installed or released:
#   tools/release/verify-app.sh <Sukrara.app> [--unsigned | --notarized]
# - bundle id app.mauri.sukrara, version / build = tools/release/version.sh (dev builds may add .<yyMMddHHmm>)
# - the widget extension (app.mauri.sukrara.widget) is inside
# - signed (unless --unsigned): codesign --verify --deep --strict, team QWUV9H4A3G on the app and the appex
# - a release build: no debug entry points in any binary (debugsend, DebugFakes, Synthetic*, the snapshot renderer)
# - --notarized (mac-release.sh with a Developer ID, #91): the app and the appex signed by "Developer ID Application"
#   with the hardened runtime and a secure timestamp, the notarization ticket stapled, and Gatekeeper accepting the app
#   as "Notarized Developer ID"
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
app="${1:?usage: verify-app.sh <Sukrara.app> [--unsigned | --notarized]}"
mode="${2:-}"
case "$mode" in "" | --unsigned | --notarized) ;; *) echo "unknown argument '$mode' (--unsigned, --notarized)" >&2; exit 2 ;; esac
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
appex="$app/Contents/PlugIns/SukraraWidget.appex"
if [ -d "$appex" ] && [ "$(plist "$appex" CFBundleIdentifier)" = app.mauri.sukrara.widget ]; then ok "widget extension inside"
else bad "SukraraWidget.appex (app.mauri.sukrara.widget) missing"; fi

if [ "$mode" != --unsigned ]; then
  if codesign --verify --deep --strict "$app" 2>/dev/null; then ok "signature verifies"; else bad "codesign --verify --deep --strict fails"; fi
  for b in "$app" "$appex"; do
    team="$(codesign -dv "$b" 2>&1 | sed -n 's/^TeamIdentifier=//p')"
    [ "$team" = QWUV9H4A3G ] && ok "team $team: $(basename "$b")" || bad "$(basename "$b") team '$team' (want QWUV9H4A3G)"
  done
fi

if [ "$mode" = --notarized ]; then
  for b in "$app" "$appex"; do
    info="$(codesign -dvv "$b" 2>&1)"
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
exit $fail
