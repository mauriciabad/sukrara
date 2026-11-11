# 30 — Before the first unlock: what Sukrara can do after a restart (Direct Boot)

*2026-11-10 · investigation report · issue #14 · follow-up #160 (2026-11-11, section 5) · Android docs, AOSP keystore2
source, the manifests of xDrip+, Juggluco and AndroidAPS · tested on an Android 17 emulator (`google_apis` arm64,
file-based encryption, PIN 1234), never on the phone*

## Goal

An overnight OS update, a crash, or Android's own restart of a phone left locked for days puts the phone in **Before
First Unlock** (BFU): it boots, but the screen-lock secret hasn't been entered. A person asleep then gets **no readings
and no alerts until the morning**, without knowing it. Find out what can run before the first unlock and build the
safest behaviour that can be tested now.

## Short answer

- **Before this change nothing of Sukrara ran before the first unlock**: no component was `directBootAware`, so
  Android didn't even start the process; `BOOT_COMPLETED` (alert check, service start) waits for the unlock.
- **Full monitoring before the unlock is blocked by storage, not by the Keystore.** Every file Sukrara has (the sealed
  secrets in `files/secure/`, DataStore settings, typed state, the Room database) is in credential-encrypted (CE)
  storage, unreadable until the unlock. Keystore keys like Sukrara's (no user authentication, no
  `setUnlockedDeviceRequired`) **do** work before the first unlock (verified below).
- **Built now (the safe minimum, plus the alarm):** a direct-boot-aware receiver that, from a small
  **device-protected plan** (times and the *No data* rule, never a glucose value), puts *Unlock to resume monitoring* on
  the lock screen at the locked boot and **rings as *No data*** when that alert would have rung, at alarm volume when
  any alert that is on is an alarm. The unlock clears it and the normal restart path takes over.
- **Built next (#160, section 5): full monitoring before the unlock** on the wearer's phone reading its own sensor:
  the pairing, the role, the alert settings and state, the display settings and the last 3 h of readings moved to
  device-protected storage, the app graph's device-protected part runs before the unlock and the readings taken then
  join the history at the unlock. Followers' phones and Juggluco keep the notice and the alarm above. The Bluetooth
  link to a real Libre 3 before the unlock is still to be seen on a phone.
- **Nobody else does it:** xDrip+, Juggluco (phone app) and AndroidAPS only listen to `BOOT_COMPLETED`; none is
  direct-boot aware, so they are all silent until the first unlock too.

## 1. What Android allows before the first unlock

From the [Direct Boot guide](https://developer.android.com/privacy-and-security/direct-boot) and the platform behaviour:

| | Before the first unlock |
|---|---|
| Components | Only those marked `android:directBootAware="true"`. If one runs, the process starts and `Application.onCreate` runs too (the Application inherits the mode). Non-aware providers aren't installed until the unlock. |
| Broadcasts | `LOCKED_BOOT_COMPLETED` to aware receivers (needs `RECEIVE_BOOT_COMPLETED`); `ACTION_USER_UNLOCKED` only to runtime receivers; `BOOT_COMPLETED` after the unlock. |
| Storage | Device-protected (DE: `createDeviceProtectedStorageContext()`, `/data/user_de/0/<pkg>`) readable; credential-encrypted (CE: `filesDir`, `getSharedPreferences`, databases) not: files are missing (`ENOENT`), listings `null`, `SharedPreferences` throw `IllegalStateException`. The guide: put in DE only what must be available then, never passwords or tokens. |
| Alarms | All alarms are gone after a restart; an aware component may set exact alarms (`USE_EXACT_ALARM` holds). |
| Notifications | Can be posted; channels (kept by the system) exist with their sounds and Do Not Disturb settings. A sound `content://` URI is only opened if its provider is direct-boot aware. Activities that aren't aware can't open (no full-screen intent); a notification tap asks for the unlock first. |
| Foreground service | `LOCKED_BOOT_COMPLETED` is a listed exemption from the background-start limits; Android 15+ refuses only some types (dataSync, camera, media, phone call, microphone) from boot receivers, not `connectedDevice`. |
| Keystore | keystore2 super-encrypts only keys bound to user authentication (`AfterFirstUnlock` super key) or `UNLOCKED_DEVICE_REQUIRED` (`enforcements.rs` `super_encryption_required`: any other app key gets `SuperEncryptionType::None`); those return `LOCKED` until the unlock. Plain keys work. |
| Bluetooth | The Bluetooth stack is direct-boot aware and runs; whether an app's BLE scan / GATT to a Libre 3 works then is untested (emulator has no Libre): #160. |

## 2. What the others do

| App | Before the first unlock |
|---|---|
| xDrip+ | Only `BOOT_COMPLETED` (`app/src/main/AndroidManifest.xml`); nothing direct-boot aware. No readings, no alerts. |
| Juggluco | Phone app: nothing direct-boot aware. Only the Wear OS watch face is (`Common/src/wear/AndroidManifest.xml`, a Wear requirement). |
| AndroidAPS | `AutoStartReceiver` on `BOOT_COMPLETED`; nothing direct-boot aware. |
| Alarm clocks (e.g. lark-alarm #43) | The pattern Sukrara now uses: an aware receiver on `LOCKED_BOOT_COMPLETED` re-arms from DE storage. |

The restart doesn't need the person: night-time OS updates, and phones that restart themselves after being left
locked (GrapheneOS's auto-reboot, the 72 h inactivity restart) put the phone back in BFU. None of the three apps warns.
Other apps meet the same wall (Linphone #2547: no calls until the first unlock; the Trime keyboard moved its files to
DE storage).

## 3. Emulator tests (2026-11-10)

Own AVD (`android-37.0` `google_apis` arm64, `ro.crypto.type=file`), debug build, `SYNTHETIC` (wearer monitoring),
`locksettings set-pin 1234`, `adb reboot`, `dumpsys user` = `RUNNING_LOCKED`. A temporary probe (not committed) logged
what the locked process can reach:

| Probe (locked, process started by `LOCKED_BOOT_COMPLETED`) | Result |
|---|---|
| Read a CE file written before the restart | `FileNotFoundException … ENOENT` |
| List `filesDir` | `null` |
| `getSharedPreferences` | `IllegalStateException: … not available until after user (id 0) is unlocked` |
| Read the DE plan | the plan, as written |
| Open a blob sealed by `KeystoreSecretBox` after the previous unlock (blob in DE) | **works** (`secret`) |
| Make a new Keystore key and seal | works |

The built behaviour, without the probe (QA.md Q8 "Before the first unlock" repeats it):

| Step | Seen |
|---|---|
| Before the restart | `/data/user_de/0/app.mauri.sukrara/files/locked-boot.json` = `{"monitoring":true,"lastReadingAt":…,"noDataMin":20,"repeatMin":60,"alarm":true,"sensor":{…}}`, no value |
| Locked boot | lock screen: *Unlock to resume monitoring* · *Your phone restarted. Sukrara can't read your sensor…*; `dumpsys notification`: id 3, channel `status`, `vis=PUBLIC`, silent; `dumpsys alarm`: `lockedboot.RING` exact at the last reading + 20 min; no crash |
| The alarm (sent by `adb root` + `am broadcast` to skip the 20 min) | id 3 on `alert_alarm.no_data`, `category=alarm`; `RingtonePlayer` played `content://app.mauri.sukrara.alertsound/no_data` (the provider answered before the unlock); the next `RING` 60 min later |
| Unlock with the PIN | id 3 and the alarm gone within a second (`USER_UNLOCKED`); 34 s later `start (Boot): Started` in the **same process**, the glucose notification back |
| Timing | `LOCKED_BOOT_COMPLETED` reached the receiver ~20 s after `sys.boot_completed` (9 receivers in the queue); repeated with the committed build: same results, no `FATAL` |

## 4. Design

- **The plan** (`LockedBootPlan`, `files/locked-boot.json` in DE storage): whether monitoring had started, the last
  reading's time, *No data*'s minutes, re-alert and sound, whether any alert that is on is an alarm, *Mute all*'s end,
  the sensor's life. Written by the alert runtime with every save (only when it changed), cleared by *Start fresh* /
  leaving the role. A damaged file reads as "monitoring, no alarm" (the notice still shows).
- **When it rings** (`firstAlarm` / `nextAlarm`): the *No data* rule's conditions with nothing but the plan: the last
  reading + its minutes, after the sensor's warm-up grace or *Mute all*'s end, never once the sensor has ended, *Mute
  all* until turned back on, *No data* off or alerts off; then every re-alert interval. A restart long after the last
  reading rings at once. Stated by `LockedBootPlanTest`, `LockedBootTest`.
- **Why ring at alarm volume** when any alert that is on is an alarm (very low is, by default): every alert, the low
  ones included, waits for the unlock, so the one alarm that can ring stands in for the loudest of them.
- **Silent notice at the boot**: a person who restarted the phone in their hand doesn't need a sound; one asleep gets
  the alarm when it matters. It is public on the lock screen (no health data in it) and its tap asks for the unlock.
- **Unlock**: `App` registers for `ACTION_USER_UNLOCKED` when its process started locked; it clears the notice and the
  alarm and builds the graph. `BOOT_COMPLETED` (also on `LockedBootReceiver`) clears again if the process died.
- **The graph never runs locked** (#14; since #160 only its device-protected part does, section 5): nothing reads or
  writes CE storage while it's unreadable, so no store can mistake a locked file for a missing or damaged one (the
  corruption log, the secure store's "can't be opened" path).
- **Security trade-off** of the plan in DE storage: the time of the last reading and the *No data* rule. Anyone
  who could read DE storage of this app before the unlock (root or an exploit of the app's sandbox) learns when a
  reading last arrived and the alert settings above, nothing else: no value, no identity, no keys. (#160 adds more to
  DE storage: "In Sukrara" says what that costs.)
- **Alert scenarios** don't change: the engine's rules are the same; this is a delivery path for when the engine can't
  run (Android only: a Mac has no locked boot for an app).

## 5. Full monitoring before the first unlock (#160, 2026-11-11)

**Storage split.** `:core:data` `DeviceProtected` lists what moved to device-protected storage, outright (no
migration: a phone updated to this version starts those fresh: role, alert settings, unit, graph and status-bar
settings, and the sensor must be scanned again): the settings stores `alerts`, `settings` (unit, source), `graph`,
`sensor`, `statusbar`; the typed state `role`, `role-backup`, `monitor-record`, `alert_state`, `sensor_end`; the secret
`sensor` (the pairing; its Keystore key `sukrara.sensor` has no user authentication, so it opens then, section 3);
`recent-readings.json` (this phone's own readings of the last 3 h, rewritten with every reading, also unlocked, so a
restart starts with the card's and the rates' recent past) and `locked-readings.csv` (every reading taken before the
unlock, appended; merged into the history at the unlock and deleted). Everything else stays credential-encrypted and
`DataModule` refuses it before the unlock (`LockedStorageException`), so nothing reads a locked file as missing.

**One graph, two phases.** `App` builds `AppGraph` at once. Before the unlock `monitorReady` (role, hub) runs; the
graph decides once (`monitorsLocked`) whether this phone monitors: the wearer with a paired sensor read directly (or
the debug build's made-up one). `LockedBootReceiver` then runs the alert check, schedules the health check and starts
the service (a `LOCKED_BOOT_COMPLETED` start is allowed), and posts *Monitoring before unlock*. The hub's readings are
`HubReadings` (the recent readings before the unlock, the history after); treatments, sharing and the follower engine
wait behind `AppGraph.whenUnlocked`. At `ACTION_USER_UNLOCKED` the readings taken before it are added to the history
under the same lock the hub writes through (none lost, none twice), the waiting inputs open and `ready` loads the rest;
the service, the hub, the sensor link and the alert runtime are the same objects throughout (no reconnect).

**Followers.** The issue keeps the sharing identity credential-encrypted, so before the wearer's unlock nothing reaches
followers (their own *No data* rings if it lasts); at the unlock the owner engine starts and publishes the hours that
changed, the locked ones included, so followers get the gap filled, then live readings. A follower's own phone keeps the
section-4 notice and alarm (its identity and the follow state are credential-encrypted too).

| Emulator test (2026-11-11, own AVD `fx_directboot`, Android 17 `google_apis` arm64, PIN 1234, debug build, `SYNTHETIC --ei cycle 108`) | Seen |
|---|---|
| Before the restart | `/data/user_de/0/app.mauri.sukrara/`: `datastore/alerts.preferences_pb`, `files/recent-readings.json` (14 KB), `files/state/{alert_state,monitor-record,role,role-backup,sensor_end}.json`, `files/locked-boot.json` |
| `adb reboot`, not unlocked (`dumpsys user`: `RUNNING_LOCKED`) | 13 s after the boot `Sukrara.monitor: start (LockedBoot): Started`; the service in the foreground; lock screen: the glucose notification (75 mg/dL ↓, then a new value every minute) and *Monitoring before unlock*; `run-as` can't reach the app (its CE dir is locked) |
| Still locked, the curve below 70 | *Low glucose · 69 mg/dL ↓* on `alert.low`; `RingtonePlayer` played `content://app.mauri.sukrara.alertsound/low` |
| `adb root` | `files/locked-readings.csv`: the source's backfill and every minute since the boot (188 lines) |
| Unlock with the PIN | the notice gone; same process and the same service record (created 6 min before); `locked-readings.csv` deleted; `start (Boot): Started` after the boot broadcast |
| `databases/sukrara.db` pulled | 188 readings, one a minute from 3 h before to the last minute, 0 gaps, 0 duplicates, the locked minutes (09:45–09:50 UTC) in place; no `FATAL` |

## In Sukrara

| Added | Where |
|---|---|
| The device-protected plan and its rules | `:monitor` `lockedboot/LockedBootPlan.kt`, `LockedBootStore.kt`; written by `AlertRuntime.save()` / `reset()` (`AlertsModule` wires the store) |
| The locked-boot path (notice, alarm, ring, unlock) | `lockedboot/LockedBoot.kt` (pure), `LockedBootReceiver.kt` (`AndroidLockedBootSurface`), `AndroidAlarms.lockedBoot`, `NotificationIds.LOCKED_BOOT`, en + es strings |
| Direct-boot-aware receiver and sound provider | `monitor/src/main/AndroidManifest.xml`; `AlertSoundProvider` reads the plan's sound before the unlock |
| No graph before the unlock; the unlock hands over | `App.kt` (lazy graph, `ACTION_USER_UNLOCKED`), `Context.alertsModule` |
| Tests | `LockedBootPlanTest`, `LockedBootTest`, `LockedBootReceiverTest` (Robolectric: DE vs CE, the notice, channels, alarms, unlock), `LockedBootPlanSaveTest`, `ManifestTest` (only these are direct-boot aware) |
| Docs | FEATURES.md "Before the first unlock (Android)", QA.md Q8 "Before the first unlock", README |

| Left out (#14) | Why |
|---|---|
| Libre 3 monitoring and level alerts before the unlock | a storage move of that size was its own round: done in #160 (below) |
| Follower before the unlock | the Nostr identity and relays have the same storage problem; #160 keeps the identity credential-encrypted (below) |
| Copying the alert record or recent readings to DE | done in #160, for the engine that now runs there |
| Full-screen alarm, *Mute…* / *Remind less* before the unlock | the activity needs the app's storage; the buttons work since #160 on the phone that monitors |
| *Vibrate only when unlocked* fallback sound | the phone is locked by definition; the channel's own sound plays |
| A sound at the locked boot | a restart in the hand needs none; the alarm rings when *No data* would have |
| `setUnlockedDeviceRequired` on Sukrara's keys | would make #160 impossible and adds nothing while the sealed files are in CE storage |

### #160: full monitoring before the first unlock (2026-11-11)

| Added | Where |
|---|---|
| The storage split: what moved to device-protected storage, the refusal of the rest before the unlock | `:core:data` `DeviceProtected`, `DataModule` (`LockedStorageException`, `unlock()`), `TypedStore` / `PreferenceStores` / `KeystoreSecureStore` per-file directories, `DiagLog` holding lines until the unlock |
| The recent readings and the readings taken before the unlock, merged at it | `readings/RecentReadings`, `readings/HubReadings` (the hub's `ReadingsRepository`) |
| The graph's locked phase and the unlock | `AppGraph` (`monitorReady`, `monitorsLocked`, `whenUnlocked`, `runningAlerts`, `onUnlocked`), `App` (graph at once, `ACTION_USER_UNLOCKED`, the race when the unlock comes before the registration), `FollowerSettingsBridge` (the wearer never reads the follower's `settingsFrom`) |
| The locked start and its notice | `LockedBootReceiver.lockedBootCompleted`, `LockedBoot.onLockedBoot(monitoring)`, `showMonitoring`, `StartReason.LockedBoot`, en + es strings; `MonitorHost.awaitMonitor`, `AlertsHost.awaitAlerts` / `alertsNow`; `MonitorModule.readyUnlocked` (root status bar, *Read glucose aloud* after the unlock) |
| Direct-boot-aware monitoring components | `MonitoringService`, `HealthCheckReceiver`, `AlertCheckReceiver`, `AlertActionReceiver` (`ManifestTest`) |
| Notifications that open the app before the unlock too | `notify/AppActivities` (the alerts', the glucose notification's and the sensor-end notices' taps; the quick-treatment lookup no longer caches "missing" from a locked start) |
| Debug: the made-up sensor before the unlock | `SyntheticSource` settings in device-protected `SharedPreferences` |
| Tests | `DeviceProtectedStorageTest`, `RecentReadingsTest`, `LockedGraphTest` (Robolectric: the locked graph reads, alerts and merges with only device-protected storage), `LockedBootTest`, `LockedBootReceiverTest`, `ManifestTest` |
| Docs | FEATURES.md "Before the first unlock (Android)", ARCHITECTURE.md (storage, the trade-off), QA.md Q8, README |

| Left out | Why |
|---|---|
| A migration of the moved files | no backwards compatibility: an updated phone starts them fresh (choose the role, confirm the alerts, scan the sensor) |
| The sharing identity, follower state and relays before the unlock | the issue keeps the identity credential-encrypted; followers get the locked hours at the unlock |
| Treatments, uploads, Health Connect, broadcasts, local servers, widgets, the root status bar, the lock-screen widget before the unlock | credential-encrypted data or accounts, or nothing to show them on; they start at the unlock |
| Juggluco as the source before the unlock | Juggluco itself doesn't run then (not direct-boot aware), and its secret is credential-encrypted |
| A full-screen alarm before the unlock | Sukrara's activities read credential-encrypted storage; the heads-up alert and its sound ring |
| `setUnlockedDeviceRequired` on Sukrara's keys | would lock the pairing until the unlock, which is the point of this change |

**The trade-off, plainly.** What moved is protected by the app sandbox and Android's file encryption at rest (and, for
the pairing, a Keystore key without user authentication), not by the screen-lock secret: on a phone that is on and was
never unlocked since its restart, someone who can break the app's sandbox (root, an exploit) can read the alert
settings, the role, the last 3 hours of glucose values and those read since the restart, and the sensor pairing (with
which they could read the sensor too). Before #160 they would have waited for the unlock. A phone that is off protects
both kinds alike. The identity, the history, treatments, uploads and their accounts stay behind the screen lock.
