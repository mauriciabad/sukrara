#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks a release APK before it reaches a phone or a GitHub release:
#   tools/release/verify-apk.sh [--play] [--unsigned] <apk> <mapping.txt>
# --play: the Google Play build (play flavor; tools/release/play-bundle.sh passes the bundle's universal APK): the
#   permissions are allow.json's with `play_permissions` applied, and no LSPosed module, root status bar or update
#   check is inside (no META-INF/xposed, no XposedProvider / HookHelloReceiver, no systemui or libxposed class in the
#   R8 mapping, no api.github.com in the dex).
# --unsigned: CI only (no release key, no Juggluco checkout): skips the signer and the Libre 3 core, checks the rest.
# - signature verifies and the signer certificate is THE release key (tools/release/android-cert.sha256)
# - package app.mauri.sukrara, versionName / versionCode = tools/release/version.sh, not debuggable
# - the Libre 3 native core lib/arm64-v8a/libsukrara_libre3.so is inside
# - no debug code: the merged manifest names no DebugReceiver, and (R8 renames classes, so the dex names prove
#   nothing) the build's R8 mapping lists no DebugReceiver / DebugFakes / DebugRelays / Synthetic* class
# - cleartext only to loopback: the network security config is src/main's, not the debug one (+ 10.0.2.2, a test relay)
# - no exported component without a permission except the launcher activity, the alert sound provider and widgets
# - every alert sound of contracts/alerts/sounds/android survived resource shrinking (verify-sounds.sh)
# - the uses-permission list and the <queries> entries are exactly tools/check/allow.json `permissions` / `queries`
#   (#36: a new library or module permission fails until the list says why)
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
play=0 unsigned=0
while [ $# -gt 0 ]; do
  case "$1" in
    --play) play=1; shift ;;
    --unsigned) unsigned=1; shift ;;
    *) break ;;
  esac
done
apk="${1:?usage: verify-apk.sh [--play] [--unsigned] <apk> <mapping.txt>}"
mapping="${2:?usage: verify-apk.sh [--play] [--unsigned] <apk> <mapping.txt> (R8 renames classes: only the mapping shows them)}"
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

if [ $unsigned = 1 ]; then echo "skip signer (--unsigned)"
else
  certs="$("$apksigner" verify --print-certs "$apk" 2>&1)" || { bad "signature doesn't verify: $certs"; certs=""; }
  signers="$(printf '%s\n' "$certs" | grep -v 'Stamp' | sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' | tr 'a-f' 'A-F' | sort -u)"
  if [ "$signers" = "$want_cert" ]; then ok "signed by the release key"; else bad "signer '$signers' is not the release key $want_cert"; fi
fi

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

if [ $unsigned = 1 ]; then echo "skip Libre 3 native core (--unsigned: CI has no Juggluco checkout)"
elif unzip -l "$apk" | grep -q 'lib/arm64-v8a/libsukrara_libre3.so$'; then ok "Libre 3 native core present"
else bad "lib/arm64-v8a/libsukrara_libre3.so missing (third_party/Juggluco not built in)"; fi

manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
if printf '%s\n' "$manifest" | grep -Eq 'DebugReceiver|DebugFakes|DebugRelays|Synthetic'; then bad "the manifest registers debug components"
else ok "manifest has no debug components"; fi

# The Google Play build: no LSPosed module, no root status bar, no GitHub update check (docs/20-publishing.md › Flavors).
if [ $play = 1 ]; then
  if unzip -l "$apk" | grep -q 'META-INF/xposed/'; then bad "META-INF/xposed is inside: LSPosed would offer it as a module"
  else ok "no META-INF/xposed (not an LSPosed module)"; fi
  if printf '%s\n' "$manifest" | grep -Eq 'XposedProvider|HookHelloReceiver|SYSTEMUI_STATE'; then bad "the manifest still has the System UI module's components"
  else ok "manifest has no System UI module components"; fi
  gone='^(app\.mauri\.sukrara\.monitor\.systemui\.|io\.github\.libxposed\.|app\.mauri\.sukrara\.feature\.settings\.GitHubReleases)[^ ]*'
  if [ -f "$mapping" ] && grep -Eq "$gone ->" "$mapping"; then
    bad "R8 mapping lists root / LSPosed / update-check classes: $(grep -Eo "$gone" "$mapping" | head -3 | tr '\n' ' ')"
  else ok "no root, LSPosed or update-check classes (R8 mapping)"; fi
  if unzip -p "$apk" 'classes*.dex' | LC_ALL=C grep -aq 'api.github.com'; then bad "dex contains the GitHub update check's URL"
  else ok "no GitHub update check URL (dex)"; fi
fi

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
# or edit that placement). The full build also has libxposed's XposedProvider (LSPosed hands the app its service there
# and can hold no permission; what it reports is advisory, as ManifestTest says); the Play build must not (--play).
# Anything else exported needs a permission.
allowed_open='app.mauri.sukrara.MainActivity
app.mauri.sukrara.monitor.AlertSoundProvider
app.mauri.sukrara.widgets.ValueWidgetReceiver
app.mauri.sukrara.widgets.GraphWidgetReceiver
app.mauri.sukrara.widgets.CombinedWidgetReceiver
app.mauri.sukrara.widgets.WidgetConfigActivity'
[ $play = 1 ] || allowed_open="$allowed_open
io.github.libxposed.service.XposedProvider"
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

# #36: exactly the permissions and package-visibility queries the shipped features use (allow.json, each with why).
allow="$here/../check/allow.json"
declared="$(printf '%s\n' "$manifest" | python3 -I -c '
import re, sys
stack, items, name = [], [], re.compile(r":name\([^)]*\)=\"([^\"]*)\"")
for line in sys.stdin:
    m = re.match(r"( *)(E|A): (\S+)", line)
    if not m:
        continue
    depth, kind, what = len(m.group(1)), m.group(2), m.group(3)
    if kind == "E":
        while stack and stack[-1][0] >= depth:
            stack.pop()
        stack.append([depth, what, None])
    elif stack and name.search(line):
        stack[-1][2] = name.search(line).group(1)
        tags = [t for _, t, _ in stack]
        if tags[-1] == "uses-permission" and len(tags) == 2:
            items.append("permission " + stack[-1][2])
        elif tags[-2:] == ["queries", "package"]:
            items.append("query package:" + stack[-1][2])
        elif tags[-3:] == ["queries", "intent", "action"]:
            items.append("query intent:" + stack[-1][2])
print("\n".join(sorted(set(items))))
')"
wanted="$(python3 -I -c '
import json, sys
d = json.load(open(sys.argv[1]))
perms = set(d["permissions"])
if sys.argv[2] == "1":  # the Google Play build
    perms = (perms - set(d["play_permissions"]["remove"])) | set(d["play_permissions"]["add"])
print("\n".join(sorted(["permission " + k for k in perms] + ["query " + k for k in d["queries"]])))
' "$allow" "$play")"
if [ "$declared" = "$wanted" ]; then ok "permissions and queries are allow.json's$([ $play = 1 ] && echo ' with play_permissions') ($(printf '%s\n' "$declared" | grep -c '^permission') permissions, $(printf '%s\n' "$declared" | grep -c '^query') queries)"
else
  extra="$(printf '%s\n' "$declared" | grep -vxF "$wanted" | tr '\n' ' ')"
  missing="$(printf '%s\n' "$wanted" | grep -vxF "$declared" | tr '\n' ' ')"
  bad "permissions / queries differ from tools/check/allow.json: in the APK only: ${extra:-none}; in allow.json only: ${missing:-none}"
fi

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
