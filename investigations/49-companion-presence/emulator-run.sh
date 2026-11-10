#!/bin/sh
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Investigation 49: what a CompanionDeviceManager association and presence observation give a background app, on an
# emulator (no Bluetooth device needed: the system's own `cmd companiondevice` creates the association and simulates
# the presence events). Prints the probe's log, Android's reason for each foreground-service start, and the process's
# standing after each step.
#   investigations/49-companion-presence/emulator-run.sh emulator-5580 path/to/cdmprobe-debug.apk
# Needs a userdebug emulator (adb root: the process is killed the way the low-memory killer does it). Emulators only.
set -eu
S="${1:?emulator serial, e.g. emulator-5580}"
APK="${2:?the CdmProbe apk}"
case "$S" in emulator-*) ;; *) echo "emulators only" >&2; exit 2 ;; esac
P=dev.spike.cdmprobe
MAC=F0:F1:F2:F3:F4:F5
MAC2=F0:F1:F2:F3:F4:F6
a() { adb -s "$S" "$@"; }
sh_() { a shell "$@"; }
cmd() { sh_ am broadcast -a $P.CMD -p $P --es cmd "$@" >/dev/null; }
now() { sh_ date +%H:%M:%S.%N | cut -c1-12; }
step() { printf '\n== %s (device time %s)\n' "$1" "$(now)"; }
showlog() { sleep "${1:-4}"; sh_ run-as $P sh -c "'cat files/probe.log 2>/dev/null; rm -f files/probe.log'" || true; }
pid() { sh_ pidof $P || echo none; }
kill9() { p=$(sh_ pidof $P || true); [ -n "$p" ] && sh_ kill -9 "$p" || true; sleep 2; echo "process killed (was ${p:-none}), now: $(pid)"; }
# Android's own verdict on the last foreground-service start (ActiveServices logs the reason code)
fgsreason() { sh_ logcat -d -s ActivityManager:* | grep "Background started FGS.*$P" | tail -1 | sed -n 's/.*FGS: \([A-Za-z]*\).*code:\([A-Z_]*\).*/  Android: \1, code \2/p'; }
standing() {
  sh_ dumpsys activity processes $P | grep -m2 -E 'oom:|curProcState' | sed 's/^ */  /' || true
  echo "  power-save allowlist: $(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r' || echo no)"
  echo "  standby bucket: $(sh_ am get-standby-bucket $P)"
}
assoc_id() { sh_ cmd companiondevice list 0 | sed -n "s/.*mId=\([0-9]*\).*mDeviceMacAddress=$(echo "$1" | tr 'A-F' 'a-f').*/\1/p" | head -1; }
granted() { sh_ dumpsys package $P | sed -n 's/^ *\(android\.permission\.[A-Z_]*\): granted=true.*/\1/p' | sort -u | tr '\n' ' '; echo; }

a root >/dev/null; sleep 2; a wait-for-device
sh_ cmd companiondevice disassociate-all 0 >/dev/null 2>&1 || true
a uninstall $P >/dev/null 2>&1 || true
a install -r -g "$APK" >/dev/null
# open once (a new install is "stopped" and gets no broadcasts), then leave it long enough for every
# recently-visible grace period to end
sh_ am start -W -n $P/.MainActivity >/dev/null; sleep 2; sh_ input keyevent KEYCODE_HOME
echo "waiting 90 s after leaving the app"; sleep 90
sh_ run-as $P rm -f files/probe.log

step "1. No association: a background start of the foreground service"
kill9; sh_ logcat -c; cmd fgs; showlog; fgsreason; standing; cmd stop

step "2. Association made by the shell (as if the person accepted the dialog)"
# positional MAC: the help text says --mac-address, which doesn't parse on Android 17
sh_ cmd companiondevice associate 0 $P $MAC
ID=$(assoc_id $MAC)
echo "association id: $ID"
cmd list; showlog 2; standing

step "3. Associated, not observing: a background start of the foreground service"
kill9; sh_ logcat -c; cmd fgs; showlog; fgsreason; cmd stop; sleep 1

step "4. Observing; process dead; the device appears (BLE)"
cmd observe --ei id "$ID"; sleep 1; showlog 1
kill9; sh_ logcat -c
echo "simulate BLE appeared at $(now)"; sh_ cmd companiondevice simulate-device-event "$ID" 0
showlog 6; fgsreason; echo "process: $(pid)"; standing

step "5. Bound by CDM, foreground service stopped: the process's standing"
cmd stop; sleep 3; standing; showlog 1

step "5b. Killed while the device is present: does CDM bring the process back by itself?"
kill9; echo "killed at $(now)"; sleep 20; echo "process: $(pid)"; showlog 1
sh_ logcat -d -s CDM_CompanionAppBinder:* ActivityManager:* | grep -E 'scheduleRebinding|Start proc.*cdmprobe' | tail -2

step "6. The device disappears (BLE)"
sh_ cmd companiondevice simulate-device-event "$ID" 1; showlog 4; echo "process: $(pid)"; standing

step "7. Bluetooth connected / disconnected (what a GATT link to the sensor reports)"
kill9; sh_ logcat -c
echo "simulate BT connected at $(now)"; sh_ cmd companiondevice simulate-device-event "$ID" 2; showlog 6; fgsreason
echo "process: $(pid)"; standing
sh_ cmd companiondevice simulate-device-event "$ID" 3; showlog 4; echo "process: $(pid)"; standing

step "8. Deep Doze: process dead, the device appears"
cmd stop; sleep 1; sh_ dumpsys deviceidle force-idle; kill9; sh_ logcat -c
echo "simulate BLE appeared at $(now)"; sh_ cmd companiondevice simulate-device-event "$ID" 0; showlog 8; fgsreason
echo "process: $(pid)"
sh_ cmd companiondevice simulate-device-event "$ID" 1; sh_ dumpsys deviceidle unforce >/dev/null; showlog 3

step "9. A battery exemption the person set by hand (Settings: Unrestricted), then the device comes and goes"
cmd stop; sleep 1
sh_ dumpsys deviceidle whitelist +$P >/dev/null; echo "set by hand: $(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r')"
sh_ cmd companiondevice simulate-device-event "$ID" 0; showlog 4
echo "after appeared: $(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r' || echo removed)"
sh_ cmd companiondevice simulate-device-event "$ID" 1; showlog 4
echo "after disappeared: $(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r' || echo removed)"

step "10. Force-stopped (Settings > Force stop): the device appears"
cmd stop; sleep 1; sh_ am force-stop $P; sleep 2
echo "simulate BLE appeared at $(now)"; sh_ cmd companiondevice simulate-device-event "$ID" 0; showlog 8; echo "process: $(pid)"
sh_ cmd companiondevice simulate-device-event "$ID" 1; showlog 3
sh_ am start -W -n $P/.MainActivity >/dev/null; sleep 2; sh_ input keyevent KEYCODE_HOME; showlog 1

step "11. The person removes the association (Settings > Connected devices)"
sh_ cmd companiondevice disassociate 0 $P $MAC; sleep 2; cmd list; showlog 2; standing
echo "waiting 90 s after the app was last visible"; sleep 90
kill9; sh_ logcat -c; cmd fgs; showlog; fgsreason; cmd stop

step "12. Android 17's medical device profile: what accepting the association grants"
a uninstall $P >/dev/null; a install "$APK" >/dev/null   # no -g: runtime permissions as a new install has them
sh_ am start -W -n $P/.MainActivity >/dev/null; sleep 2; sh_ input keyevent KEYCODE_HOME
echo "granted before: $(granted)"
echo "appops before: $(sh_ appops get $P SCHEDULE_EXACT_ALARM | tr -d '\r') · $(sh_ appops get $P USE_FULL_SCREEN_INTENT | tr -d '\r')"
sh_ cmd companiondevice associate 0 $P $MAC2 android.app.role.COMPANION_DEVICE_MEDICAL; sleep 3
sh_ cmd companiondevice list 0 | grep -o 'mDeviceProfile=[^,]*' || true
echo "role holders: $(sh_ cmd role get-role-holders android.app.role.COMPANION_DEVICE_MEDICAL | tr -d '\r')"
echo "granted after: $(granted)"
echo "appops after: $(sh_ appops get $P SCHEDULE_EXACT_ALARM | tr -d '\r') · $(sh_ appops get $P USE_FULL_SCREEN_INTENT | tr -d '\r')"
echo "notification policy access: $(sh_ cmd notification list_approved_dnd 2>/dev/null | grep -c $P || true) · power-save allowlist: $(sh_ dumpsys deviceidle whitelist | grep $P | tr -d '\r' || echo no)"
sh_ cmd companiondevice disassociate-all 0 >/dev/null 2>&1 || true
