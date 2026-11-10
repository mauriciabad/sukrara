#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks a release APK before it reaches a phone or a GitHub release:
#   tools/release/verify-apk.sh <apk> <mapping.txt>
# - signature verifies and the signer certificate is THE release key (tools/release/android-cert.sha256)
# - package app.mauri.sukrara, versionName / versionCode = tools/release/version.sh, not debuggable
# - the Libre 3 native core lib/arm64-v8a/libsukrara_libre3.so is inside
# - no debug code: the merged manifest names no DebugReceiver, and (R8 renames classes, so the dex names prove
#   nothing) the build's R8 mapping lists no DebugReceiver / DebugFakes / DebugRelays / Synthetic* class
# - cleartext only to loopback: the network security config is src/main's, not the debug one (+ 10.0.2.2, a test relay)
# - no exported component without a permission except the launcher activity, the alert sound provider and widgets
# - every alert sound of contracts/alerts/sounds/android survived resource shrinking (verify-sounds.sh)
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
apk="${1:?usage: verify-apk.sh <apk> <mapping.txt>}"
mapping="${2:?usage: verify-apk.sh <apk> <mapping.txt> (R8 renames classes: only the mapping shows them)}"
fail=0
bad() { echo "FAIL $*"; fail=1; }
ok() { echo "ok   $*"; }

[ -f "$apk" ] || { echo "FAIL no APK at $apk"; exit 1; }
JAVA_HOME="$(dirname "$(dirname "$(jdk_tool java)")")"  # apksigner is a Java tool; macOS's stub has no JRE
export JAVA_HOME
apksigner="$(sdk_tool apksigner)"
aapt2="$(sdk_tool aapt2)"
want_cert="$(cat "$here/android-cert.sha256")"
VI=$(version_info) || exit 1
set -- $VI
want_name="$1" want_code="$2"

certs="$("$apksigner" verify --print-certs "$apk" 2>&1)" || { bad "signature doesn't verify: $certs"; certs=""; }
signers="$(printf '%s\n' "$certs" | grep -v 'Stamp' | sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' | tr 'a-f' 'A-F' | sort -u)"
if [ "$signers" = "$want_cert" ]; then ok "signed by the release key"; else bad "signer '$signers' is not the release key $want_cert"; fi

badging="$("$aapt2" dump badging "$apk")"
pkg="$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
code="$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p")"
name="$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")"
[ "$pkg" = app.mauri.sukrara ] && ok "package $pkg" || bad "package '$pkg' (want app.mauri.sukrara)"
[ "$code" = "$want_code" ] && ok "versionCode $code" || bad "versionCode '$code' (want $want_code from VERSION)"
[ "$name" = "$want_name" ] && ok "versionName $name" || bad "versionName '$name' (want $want_name)"
if printf '%s\n' "$badging" | grep -q "application-debuggable"; then bad "the APK is debuggable"; else ok "not debuggable"; fi

# Looked up by name, kept only by keep_alert_sounds.xml: a stripped sound would be a silent alarm.
AAPT2="$aapt2" "$here/verify-sounds.sh" "$apk" || fail=1

if unzip -l "$apk" | grep -q 'lib/arm64-v8a/libsukrara_libre3.so$'; then ok "Libre 3 native core present"
else bad "lib/arm64-v8a/libsukrara_libre3.so missing (third_party/Juggluco not built in)"; fi

manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
if printf '%s\n' "$manifest" | grep -Eq 'DebugReceiver|DebugFakes|DebugRelays|Synthetic'; then bad "the manifest registers debug components"
else ok "manifest has no debug components"; fi

# Cleartext only to this phone's loopback: the debug build's config also allows 10.0.2.2 (a local test relay).
nsc_file="$("$aapt2" dump resources "$apk" | grep -A1 ' xml/network_security_config$' | sed -n 's/.*(file) \([^ ]*\) type=XML.*/\1/p' | head -1)"
nsc="$([ -n "$nsc_file" ] && "$aapt2" dump xmltree --file "$nsc_file" "$apk")"
nsc_hosts="$(printf '%s\n' "$nsc" | sed -n "s/.*T: '\([^']*\)'.*/\1/p" | sort | tr '\n' ' ')"
nsc_base="$(printf '%s\n' "$nsc" | grep -A1 'E: base-config' | sed -n 's/.*cleartextTrafficPermitted=\([a-z]*\).*/\1/p')"
if [ "$nsc_base" = false ] && [ "$nsc_hosts" = "127.0.0.1 localhost " ]; then ok "cleartext only to loopback (network security config)"
else bad "network security config: base cleartext '${nsc_base:-?}', cleartext hosts '${nsc_hosts:-none}' (want false; 127.0.0.1 localhost only)"; fi

# Exported components without a permission (the merged manifest: libraries' components too). Allowed, by exact name:
# the launcher / App Link activity, and the alert sound provider (System UI reads channel sounds from it and can hold
# no permission; it serves only bundled sounds), and the home-screen widgets' receivers + configure activity (any home
# app sends the widget broadcasts and opens the configure screen, so they can't require a permission; they only redraw
# or edit that placement). Anything else exported needs a permission.
allowed_open='app.mauri.sukrara.MainActivity
app.mauri.sukrara.monitor.AlertSoundProvider
app.mauri.sukrara.widgets.ValueWidgetReceiver
app.mauri.sukrara.widgets.GraphWidgetReceiver
app.mauri.sukrara.widgets.CombinedWidgetReceiver
app.mauri.sukrara.widgets.WidgetConfigActivity'
open_components="$(printf '%s\n' "$manifest" | awk '
  function flush() { if (comp != "" && exported && !perm) print comp; comp = "" }
  /E: (activity|activity-alias|service|receiver|provider) / { flush(); depth = index($0, "E:"); exported = perm = 0; comp = "?"; next }
  /E: / && comp != "" && index($0, "E:") <= depth { flush() }
  comp == "?" && /android:name\(/ { match($0, /="[^"]*"/); comp = substr($0, RSTART + 2, RLENGTH - 3) }
  comp != "" && /android:exported\(/ { exported = ($0 ~ /=(true|0xffffffff)/) }
  comp != "" && /android:permission\(/ { perm = 1 }
  END { flush() }' | grep -vxF "$allowed_open" || true)"
if [ -z "$open_components" ]; then ok "no exported component without a permission beyond the allowed ones"
else bad "exported without a permission: $(printf '%s' "$open_components" | tr '\n' ' ')"; fi

# The Live Update chip (P0): the promoted glucose notification needs this install-time permission.
if printf '%s\n' "$manifest" | grep -q '"android.permission.POST_PROMOTED_NOTIFICATIONS"'; then ok "POST_PROMOTED_NOTIFICATIONS declared (Live Update chip)"
else bad "POST_PROMOTED_NOTIFICATIONS missing: no Live Update chip"; fi

# R8 full mode, release compilation, and the mapping belongs to THIS APK (a stale mapping would hide debug classes).
marker="$(unzip -p "$apk" 'classes*.dex' | LC_ALL=C grep -ao '~~R8{[^}]*}' | head -1)"
case "$marker" in
  *'"compilation-mode":"release"'*'"r8-mode":"full"'*) ok "R8 full mode, release compilation" ;;
  *) bad "not an R8 full-mode release dex (marker: ${marker:-none})" ;;
esac
dex_map_id="$(printf '%s' "$marker" | sed -n 's/.*"pg-map-id":"\([0-9a-f]*\)".*/\1/p')"

if [ ! -f "$mapping" ]; then bad "no R8 mapping at $mapping (a minified release build writes one)"
else
  map_id="$(sed -n 's/^# pg_map_id: //p' "$mapping" | head -1)"
  if [ -n "$map_id" ] && [ "$map_id" = "$dex_map_id" ]; then ok "mapping matches the APK (pg_map_id $map_id)"
  else bad "mapping pg_map_id '$map_id' isn't the APK's '$dex_map_id' (stale mapping)"; fi
  if grep -Eq '^[^ ].*\.(DebugReceiver|DebugFakes|DebugRelays|Synthetic[A-Za-z]*)(\$[^ ]*)? ->' "$mapping"; then bad "R8 mapping lists debug classes"
  else ok "R8 mapping lists no debug classes"; fi
fi
# A second net that survives renaming: the debug tools' log tag and registry name are string constants.
if unzip -p "$apk" 'classes*.dex' | LC_ALL=C grep -aq -e 'SukraraDebug' -e 'DebugFakes'; then bad "dex contains debug-tool strings"
else ok "dex has no debug-tool strings"; fi
exit $fail
