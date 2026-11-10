#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Investigation 49: does a CompanionDeviceManager association touch the battery exemption the person set by hand
# (Settings > Apps > Battery: Unrestricted, which Sukrara's alert checklist asks for)? Run it with both probe flavors:
#   investigations/49-companion-presence/allowlist-run.sh emulator-5580 .../cdmprobe-full-debug.apk
#   investigations/49-companion-presence/allowlist-run.sh emulator-5580 .../cdmprobe-fgsOnly-debug.apk
# Emulators only.
set -eu
S="${1:?emulator serial}"
APK="${2:?the CdmProbe apk}"
case "$S" in emulator-*) ;; *) echo "emulators only" >&2; exit 2 ;; esac
P=dev.spike.cdmprobe
MAC=F0:F1:F2:F3:F4:F7
a() { adb -s "$S" "$@"; }
sh_() { a shell "$@"; }
cmd() { sh_ am broadcast -a $P.CMD -p $P --es cmd "$@" >/dev/null; }
allow() { v=$(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r' || true); printf '  %-44s %s\n' "$1" "${v:-not exempt}"; }
by_hand() { sh_ dumpsys deviceidle whitelist +$P >/dev/null; }

echo "== $(basename "$APK")"
sh_ cmd companiondevice disassociate-all 0 >/dev/null 2>&1 || true
a uninstall $P >/dev/null 2>&1 || true
a install -g "$APK" >/dev/null
sh_ am start -W -n $P/.MainActivity >/dev/null; sleep 2; sh_ input keyevent KEYCODE_HOME; sleep 2
allow "new install:"
by_hand; allow "the person sets Unrestricted:"
sh_ cmd companiondevice associate 0 $P $MAC; sleep 3
ID=$(sh_ cmd companiondevice list 0 | sed -n "s/.*mId=\([0-9]*\).*$(echo $MAC | tr 'A-F' 'a-f').*/\1/p" | head -1)
allow "associated (id $ID):"
cmd observe --ei id "$ID"; sleep 3; allow "observing:"
sh_ cmd companiondevice simulate-device-event "$ID" 0; sleep 5; allow "sensor appeared:"
sh_ cmd companiondevice simulate-device-event "$ID" 1; sleep 5; allow "sensor disappeared:"
by_hand; allow "the person sets Unrestricted again:"
a install -r -g "$APK" >/dev/null; sleep 5; allow "app updated (sensor away):"
by_hand; sh_ cmd companiondevice simulate-device-event "$ID" 0; sleep 5
a install -r -g "$APK" >/dev/null; sleep 5; allow "app updated (sensor present):"
sh_ cmd companiondevice simulate-device-event "$ID" 1; sleep 3
by_hand; sh_ cmd companiondevice disassociate 0 $P $MAC; sleep 3; allow "Unrestricted, then association removed:"
