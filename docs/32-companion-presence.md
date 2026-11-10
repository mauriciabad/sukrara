# 32 — Waking for the sensor: CompanionDeviceManager presence (Android)

*2026-11-10 · investigation report · issue #49 · follow-ups #162, #163 · Android reference and guides (API 36 / 37),
AOSP `frameworks/base/services/companion`, the sources of Juggluco, xDrip+, AndroidAPS and Gadgetbridge · tested on
an Android 17 emulator (`google_apis` arm64, own AVD) with the system's own presence simulation and a standalone probe
app ([`investigations/49-companion-presence`](../investigations/49-companion-presence)); never on the phone or a
sensor*

## Goal

A Libre 3 wearer's phone keeps one Bluetooth link to the sensor, held by Sukrara's `connectedDevice` foreground
service. When Android kills that process (memory, a crash, an OEM battery manager), the link drops and nothing reads
the sensor until something starts the service again: `START_STICKY`, the alert check, or the **15-minute health
check**. Meanwhile the person gets no readings, and a low alert waits for them; at night they don't know.

Find out whether a CompanionDeviceManager (CDM) association with the sensor and presence observation give Sukrara a
better way back (a process wake when the sensor is around, a foreground-service start Android can't refuse, less
battery), and what it costs the person.

## Short answer

- **It wakes Sukrara, measured.** With an association for the sensor's address and presence observed, the system
  itself **starts a dead process, binds its `CompanionDeviceService` and allows the foreground service** when the
  sensor appears (BLE advertising) or connects. On the emulator a killed process was back 0.3-2.4 s after the event,
  a force-stopped one too, also in forced deep Doze; and **while the sensor stays present the system restarts a
  killed process by itself 10 s later** (`scheduleRebinding`), with no event and no alarm.
- **The exemptions are real and broad.** With any association and the normal permission
  `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, a background foreground-service start that Android
  refused (`DENIED`) is allowed (`COMPANION_DEVICE_MANAGER`), from every path (health check, alert check, receivers),
  sensor present or not. While the sensor is present the process ranks *perceptible* (oom_adj 201, one step below a
  foreground service), its standby bucket is *exempted* and it is on the Doze allowlist; when the sensor goes, those go
  too.
- **It doesn't touch the person's own battery choice.** The AOSP source suggested CDM could remove an *Unrestricted*
  the person set by hand; measured on Android 17, it kept it through presence changes, app updates and the
  association's removal, with and without `REQUEST_COMPANION_RUN_IN_BACKGROUND`. Someone who already followed
  Sukrara's alert checklist (*No battery restrictions*) already has the foreground-service exemption
  (`SYSTEM_ALLOW_LISTED`): for them the gain is the wake, not the exemption.
- **No battery saving.** The sensor link costs the same either way. CDM adds a system scan (filtered by address,
  low power) only while the sensor isn't connected. What it shortens is the gap after a kill: seconds instead of up to
  15 min.
- **Android 17's medical device profile helps setup.** Associating with `DEVICE_PROFILE_MEDICAL` (permission normal)
  made the probe the holder of the *medical device companion* role, which **granted Nearby devices, notifications and
  *Alarms & reminders*** with the association. The dialog's permission list also names *Bypass Do Not Disturb*; the
  shell association didn't grant Do Not Disturb access, the dialog path is unverified.
- **The Libre 3 fits, with three caveats.** It never bonds and is reached for its whole life at the fixed address
  the NFC tap gives (Sukrara does exactly this), so an address association works without bonding: the "rotating
  address" worry doesn't apply. But (1) the system dialog must *find* the sensor advertising, and a connected sensor
  doesn't advertise: the dialog belongs **between the NFC tap and Sukrara's first connection**; (2) every new sensor
  is a new address, so a new dialog every 15 days and the old association removed; (3) whether Sukrara's own GATT
  link counts as "Bluetooth connected" presence is AOSP-likely but unverified.
- **Nobody in the CGM world uses it.** Juggluco, xDrip+, AndroidAPS and the official Libre 3 and Dexcom apps declare
  no companion permission; they rely on a foreground service plus "turn battery optimisation off". Gadgetbridge
  (wearables) uses it on top of its own foreground service; Google now recommends it for exactly this.
- **Recommendation: build it as a second line under the foreground service, not instead of it**, after a phone
  check of what the emulator can't show (#162). The battery re-measure that #49 also carried moves to #163.

## 1. The API (Android 16 and 17)

Sukrara's minimum is API 36, so only the newest calls matter.

| Piece | API | What it does |
|---|---|---|
| `associate(AssociationRequest, Executor, Callback)` | 33 | Asks the system for its dialog: `onAssociationPending(IntentSender)` (started from an Activity), then `onAssociationCreated(AssociationInfo)`. Needs `uses-feature android.software.companion_device_setup`. The dialog gives up after 20 s without the device. |
| `BluetoothLeDeviceFilter` + `ScanFilter.setDeviceAddress(…)` | 26 | Which device; Sukrara knows the exact address from the NFC tap. |
| `setSingleDevice(true)` | 26 | No list: the first match gets a yes / no confirmation. |
| `setDeviceProfile(DEVICE_PROFILE_MEDICAL)` | 37 | "A medical device, e.g. blood sugar level monitor"; `REQUEST_COMPANION_PROFILE_MEDICAL` is normal (emulator `pm list permissions -f`). Also new: `setExtraPermissions(Set)`, `DEVICE_PROFILE_FITNESS_TRACKER`. |
| `startObservingDevicePresence(ObservingDevicePresenceRequest)` | 36 | `setAssociationId(id)`: watch that association's address. `setUuid` is classic-Bluetooth and automotive-projection only, not for Sukrara. The `String` variant is deprecated. |
| `CompanionDeviceService.onDevicePresenceEvent(DevicePresenceEvent)` | 36 | `BLE_APPEARED` 0, `BLE_DISAPPEARED` 1, `BT_CONNECTED` 2, `BT_DISCONNECTED` 3, `ASSOCIATION_REMOVED` 6 (and self-managed ones). Exported, protected by `BIND_COMPANION_DEVICE_SERVICE`. On Android 17 the deprecated `onDeviceAppeared(AssociationInfo)` still arrives too (measured). |
| `getMyAssociations()`, `disassociate(id)` | 33 | The person can also remove it in Settings › Connected devices; the app then gets `ASSOCIATION_REMOVED` (measured). |
| Self-managed associations (no dialog) | 33 | `REQUEST_COMPANION_SELF_MANAGED` is `signature|privileged`: not for Sukrara. A normal app always gets a dialog. |

Permissions, all normal (install-time): `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`,
`REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, `REQUEST_COMPANION_RUN_IN_BACKGROUND` (implies the
former, adds the Doze allowlist while present), `REQUEST_COMPANION_USE_DATA_IN_BACKGROUND`. A CDM association is
**not** one of the `connectedDevice` service type's prerequisites; Sukrara's `CHANGE_NETWORK_STATE` stays that.

Sources: [CompanionDeviceManager](https://developer.android.com/reference/android/companion/CompanionDeviceManager),
[ObservingDevicePresenceRequest.Builder](https://developer.android.com/reference/android/companion/ObservingDevicePresenceRequest.Builder),
[DevicePresenceEvent](https://developer.android.com/reference/android/companion/DevicePresenceEvent),
[AssociationRequest](https://developer.android.com/reference/android/companion/AssociationRequest),
[companion device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing),
[background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
("Your app uses the Companion Device Manager and declares the
REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND permission or the REQUEST_COMPANION_RUN_IN_BACKGROUND
permission"), [BLE in the background](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
("it's best to support this use case with CompanionDeviceService … However, you can also use a foreground service"),
[Android 17 beta 1](https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html) (the medical
profile "allows medical device mobile applications to request all necessary permissions with a single tap").

## 2. What an association gives a background app (measured)

Android 17 emulator (`sdk_gphone64_arm64` 17 / CE2A.260420.051.A3, userdebug), probe `dev.spike.cdmprobe`: a
`CompanionDeviceService` that, on *appeared* or *connected*, starts a `connectedDevice` foreground service (Sukrara's
`MonitoringService` stand-in). The association is made by `cmd companiondevice associate` (what the person's *Allow*
creates) and presence by `cmd companiondevice simulate-device-event`; the process is killed with `kill -9`, the way
the low-memory killer does it. Android's own verdict is the `code:` of `ActivityManager`'s "Background started FGS"
line. Script: `emulator-run.sh`; its output, condensed:

| Step | Seen |
|---|---|
| 1. No association, process dead, a background start | `fgs start: DENIED` · `Disallowed, code DENIED`; procstate 19 (cached), not on the Doze allowlist, bucket 10 |
| 3. Associated, not observing, process dead, a background start | allowed · `code COMPANION_DEVICE_MANAGER` (no presence needed) |
| 4. Observing, process dead, sensor appears (BLE) | new process; `PresenceService created (bound by CDM)` 2.4 s after the event (0.3-0.5 s once the emulator had settled), `EVENT_BLE_APPEARED`, foreground service allowed (`COMPANION_DEVICE_MANAGER`); Doze allowlist `user,dev.spike.cdmprobe`; bucket **5 (exempted)** |
| 5. Bound, foreground service stopped | oom_adj **201**, procstate 6, importance 200 (*perceptible*); with the service: 200, procstate 4 |
| 5b. Killed while the sensor is present | `binderDied` → `scheduleRebinding` → `Start proc … for bound-service` **10 s later**, by itself |
| 6. Sensor disappears | `EVENT_BLE_DISAPPEARED`; off the Doze allowlist, bucket back to 10; unbound a few seconds later |
| 7. Bluetooth connected / disconnected, process dead | `EVENT_BT_CONNECTED` starts the process and allows the service exactly like *appeared*; `BT_DISCONNECTED` is the matching *gone* |
| 8. Forced deep Doze, process dead, sensor appears | process started, service bound and the foreground service allowed, 0.5 s |
| 9. The person's own *Unrestricted* (`deviceidle whitelist +`), sensor comes and goes | kept (also in `allowlist-run.sh`: app update with the sensor away or present, association removed; both flavors, with and without `REQUEST_COMPANION_RUN_IN_BACKGROUND`) |
| 10. Force-stopped in Settings, sensor appears | **started anyway** (the bind clears the stopped state); foreground service allowed |
| 11. Association removed (Settings › Connected devices) | the service is bound once more for `EVENT_ASSOCIATION_REMOVED`; afterwards a background start is allowed only because of step 9's *Unrestricted* (`SYSTEM_ALLOW_LISTED`) |
| 12. Association with `android.app.role.COMPANION_DEVICE_MEDICAL`, new install without grants | the probe becomes the role's holder; **`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `POST_NOTIFICATIONS` granted, `SCHEDULE_EXACT_ALARM` app-op allowed**; Do Not Disturb access not granted (shell path) |

Timing on the emulator says nothing about a phone's radio: the simulation skips the scan. What it shows is that the
system path (event → process start → bind → foreground service) takes well under a second once the device is seen.

## 3. Presence for a Libre 3

- **How the system sees a BLE device**: one system scan with an address filter per association, low-power mode,
  "first match / match lost" callbacks (AOSP `BleDeviceProcessor`). A device that doesn't advertise is never
  *BLE appeared*. Docs: "For Bluetooth LE devices, this is based on scanning for device with the given address."
- **The Libre 3's address**: the NFC tap gives it (docs/02); Sukrara connects to it with `autoConnect` for the sensor's
  whole life and never bonds (`SensorDriver`: "The Libre 3 never bonds"; Juggluco's `Libre3GattCallback` never calls
  `createBond` either). So the address is stable for 15 days and an address filter matches it; CDM's advice for
  rotating addresses (a resolvable private address and a bond) isn't needed. Whether it is a public or a static random
  address is unknown and doesn't matter here.
- **Connected means not advertising.** Like most peripherals the sensor stops advertising once a central is
  connected. Two consequences: the association dialog, which scans for the device, can only find the sensor while it
  is *not* connected to Sukrara (or anything else); and while Sukrara holds the link, presence rests on *Bluetooth
  connected*, not on advertising.
- **Does Sukrara's own GATT link count as "connected"?** AOSP's `BluetoothDeviceProcessor` listens to
  `BluetoothAdapter.BluetoothConnectionCallback.onDeviceConnected`, which the Bluetooth module fires for every ACL
  link, LE included, matched by address; `DevicePresenceProcessor` then stops the BLE scan while the device is both
  connected and seen, and after a disconnect sends `BLE_DISAPPEARED` only if the device isn't seen again within 10 s.
  So the expected life is: *connected* while Sukrara reads → the link drops (Sukrara died, or out of range) → the
  sensor advertises → *BLE appeared* → Sukrara is started and reconnects. Simulated on the emulator (step 7); not yet
  seen with a real sensor: #162's first check.
- **Each sensor is a new association.** Replacing the sensor (or its end) should `disassociate` the old one; the
  association list is per app and not limited in a way that matters here.

## 4. The person's side

The person: someone starting a sensor, phone in one hand, the new sensor just applied on the back of the arm. The flow
today ([21](21-new-sensor-flow.md)): tap → pairing → warm-up. What CDM adds:

- **One system dialog per sensor**, after the tap, before pairing: with `setSingleDevice(true)` it is the
  confirmation *"Allow the app **Sukrara** to access **<device>**?"* (`confirmation_title`, from the emulator's
  `CompanionDeviceManager.apk`); with the medical profile, the device is named a *medical device* and the dialog lists
  the permissions it grants: *Nearby devices*, *Notifications*, *Set alarms and reminders* ("Send you alerts"),
  *Bypass Do Not Disturb settings* ("Send you alerts even when Do Not Disturb mode is on"). Without a profile, the
  dialog's summary is the generic *"This app will be able to sync info, like the name of someone calling, between your
  phone and the chosen device"*, which is wrong for a CGM: the medical profile is the one to use.
- **It can't block the sensor**: declining (or the 20 s timeout) must leave the flow exactly as today; Sukrara then
  works as now and offers it again later, from Settings › Sensor, the one place that explains what it does
  ("Sukrara restarts by itself when your sensor is nearby").
- **Later offers need the sensor to advertise**, i.e. Sukrara to let go of the link for the dialog's ≤ 20 s (readings
  are backfilled after; the Libre 3 keeps history).
- **It is visible and removable**: Settings › Connected devices lists the association; removing it reaches Sukrara
  as `ASSOCIATION_REMOVED`, so Settings › Sensor can show *Restart by itself: off* with the way back on.
- **Force stop no longer stops it** while the sensor is around (step 10). For a CGM that is what the person needs, but
  stopping monitoring must stay one tap in Sukrara (removing the sensor or leaving the role removes the association).
- The [guide](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing) says pairing
  "requires Location Services to be enabled"; Sukrara's scans are `neverForLocation` today, so whether the dialog
  needs Location on Android 17 is a phone check (#162).

## 5. What the others do

| App | Companion device |
|---|---|
| Juggluco | none (no `CompanionDeviceManager`, `CompanionDeviceService` or `REQUEST_COMPANION` in its source) |
| xDrip+ | none ("companion" there means companion-app data collection) |
| AndroidAPS | none |
| FreeStyle Libre 3 (Abbott) | none in the APKMirror permission lists of v3.4.2 and v3.6.0 (battery-optimisation request, exact alarms, foreground service, Bluetooth, notification policy; newer versions not checked); tells people to turn battery optimisation off |
| Dexcom G7 | none found; its FAQ asks for Nearby devices, location "all the time" and battery optimisation off |
| Gadgetbridge (wearables) | declares `REQUEST_COMPANION_RUN_IN_BACKGROUND` + presence; `GBCompanionDeviceService` only triggers a reconnect on *appeared* / *connected*, its own foreground service stays ([issue 2008](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/2008): the exemption alone "had no effect" until code changed too) |
| Glucoripper (a glucose-meter app) | `startObservingDevicePresence` in `MeterPairingManager.kt` |

Google's own line in 2026 ([Enabling seamless cross-device interactions](https://www.youtube.com/watch?v=2-EfHHa_NxE)):
use CDM "instead of a foreground service", it "keeps the process alive while the device is in range", and the new
medical / fitness profiles. For a CGM, the foreground service also carries the glucose
notification and keeps alerts' data flowing: Sukrara keeps it.

## 6. Battery

Nothing to save while connected: the minute-by-minute GATT traffic dominates and is identical. CDM's own scan runs
only while the sensor is not connected and not yet seen (it stops once a device is both connected and present), in
low-power mode with a hardware address filter; Sukrara's `autoConnect` already keeps a controller-level filter for the
same address then. Expected extra cost: small, only while the sensor is away. To be measured with the phone and the
power lab ([`poc/spikes/power`](../poc/spikes/power)) in #162, together with the Libre 3 link options #49 also
carried (#163).

## Design (for the follow-up)

- **New-sensor flow**: after the NFC tap gives the address and before `GattLink` first connects, `associate()` with an
  address filter, `setSingleDevice(true)`, `DEVICE_PROFILE_MEDICAL`, the sensor's display name. *Allow* →
  `startObservingDevicePresence(byId)` → connect. *Don't allow* / timeout / failure → connect as today. The
  association id is kept with the sensor's pairing record; replacing or ending the sensor `disassociate`s it.
- **`SensorPresenceService : CompanionDeviceService`** (`:monitor`): on `BLE_APPEARED` / `BT_CONNECTED` →
  `MonitorStarter.start(StartReason.SensorNearby)` (starting a running service is harmless); disappear events do
  nothing (the link handles its own drops); `ASSOCIATION_REMOVED` → forget the id, Settings › Sensor shows it off.
- **Unchanged**: the foreground service, `START_STICKY`, the 15-min health check, the alert check's own alarm, the
  *Monitoring stopped* notice. CDM is a second line under them, not a replacement.
- **Manifest**: the four permissions of §1 plus `REQUEST_COMPANION_PROFILE_MEDICAL`, `uses-feature
  android.software.companion_device_setup` (`required="false"`), the service; `ManifestTest` and the release gate's
  pinned permission list updated.
- Android only: the Mac has no equivalent and doesn't hold the sensor.

## In Sukrara

This investigation changes no app code. What the follow-up brings in, and what stays out:

| Brought in by #162 | Why |
|---|---|
| The association in the new-sensor flow, medical profile, before the first connection | the only moment the sensor advertises and the person is already in a "set up the sensor" task |
| `CompanionDeviceService` → `MonitorStarter` on appeared / connected | the measured wake: a dead process back in under a second, a killed one restarted within 10 s while the sensor is near |
| `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` / `RUN_IN_BACKGROUND` | the start can't be refused (`COMPANION_DEVICE_MANAGER`), and Doze / standby exemptions while the sensor is near for people who never changed the battery setting; measured not to touch an *Unrestricted* they did set |
| Settings › Sensor row, `ASSOCIATION_REMOVED` handling | complete flow: visible, removable in Settings, offered again |

| Left out | Why |
|---|---|
| Replacing the foreground service with CDM binding | the bound rank (201) is below a foreground service (200), presence ends when the sensor stops advertising and the link is down, and the service also carries the glucose notification |
| Lengthening or dropping the 15-min health check | it covers what CDM can't: a declined dialog, Bluetooth off, a sensor out of range |
| Observing by service UUID (`setUuid`) | classic Bluetooth and automotive projection only (`REQUEST_OBSERVE_DEVICE_UUID_PRESENCE` is not for third-party apps) |
| Self-managed associations (no dialog) | `REQUEST_COMPANION_SELF_MANAGED` is `signature|privileged` |
| Bonding the Libre 3 to make presence work | not needed: its address is fixed for the sensor's life |
| Using the medical profile's grants to drop Sukrara's own permission screens | only on Android 17 and only for those who accept the dialog; the alert checklist keeps checking each permission itself |
| The probe app and its scripts in Sukrara | they stay in `investigations/49-companion-presence` as the reference for the phone checks |
| The issue's own criterion "on a real Libre 3: battery, reconnect latency, dialog UX" | needs the owner's phone and sensor and the owner's OK (docs/DEVELOPMENT.md "Devices"): moved to #162's acceptance criteria |
| Re-measuring the dropped Libre 3 link options (minimal subscriptions, low-power interval, connection priority, notification skip with the screen off) | also needs the phone and sensor: #163 |
