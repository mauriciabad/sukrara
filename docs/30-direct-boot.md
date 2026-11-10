# 30 — Before the first unlock: what Sukrara can do after a restart (Direct Boot)

*2026-11-10 · investigation report · issue #14 · follow-up #160 · Android docs, AOSP keystore2 source, the manifests
of xDrip+, Juggluco and AndroidAPS · tested on an Android 17 emulator (`google_apis` arm64, file-based encryption, PIN
1234), never on the phone*

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
- **Not built:** Libre 3 monitoring and level alerts before the unlock (needs the pairing, settings, alert state and
  recent readings moved to device-protected storage, a "locked" app graph and a real-phone Bluetooth check): #160.
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
- **The graph never runs locked**: `App` builds `AppGraph` lazily and refuses to before the unlock;
  `Context.alertsModule` is null then. Nothing reads or writes CE storage while it's unreadable, so no store can
  mistake a locked file for a missing or damaged one (the corruption log, the secure store's "can't be opened" path).
- **Security trade-off** of what is now in DE storage: the time of the last reading and the *No data* rule. Anyone
  who could read DE storage of this app before the unlock (root or an exploit of the app's sandbox) learns when a
  reading last arrived and the alert settings above, nothing else: no value, no identity, no keys.
- **Alert scenarios** don't change: the engine's rules are the same; this is a delivery path for when the engine can't
  run (Android only: a Mac has no locked boot for an app).

## In Sukrara

| Added | Where |
|---|---|
| The device-protected plan and its rules | `:monitor` `lockedboot/LockedBootPlan.kt`, `LockedBootStore.kt`; written by `AlertRuntime.save()` / `reset()` (`AlertsModule` wires the store) |
| The locked-boot path (notice, alarm, ring, unlock) | `lockedboot/LockedBoot.kt` (pure), `LockedBootReceiver.kt` (`AndroidLockedBootSurface`), `AndroidAlarms.lockedBoot`, `NotificationIds.LOCKED_BOOT`, en + es strings |
| Direct-boot-aware receiver and sound provider | `monitor/src/main/AndroidManifest.xml`; `AlertSoundProvider` reads the plan's sound before the unlock |
| No graph before the unlock; the unlock hands over | `App.kt` (lazy graph, `ACTION_USER_UNLOCKED`), `Context.alertsModule` |
| Tests | `LockedBootPlanTest`, `LockedBootTest`, `LockedBootReceiverTest` (Robolectric: DE vs CE, the notice, channels, alarms, unlock), `LockedBootPlanSaveTest`, `ManifestTest` (only these are direct-boot aware) |
| Docs | FEATURES.md "Before the first unlock (Android)", QA.md Q8 "Before the first unlock", README |

| Left out | Why |
|---|---|
| Libre 3 monitoring and level alerts before the unlock | needs the pairing, settings, alert state and recent readings in DE storage, a locked app graph and a real-phone BLE check; a storage move of that size is its own round (#160) |
| Follower before the unlock | the Nostr identity and relays have the same storage problem (#160) |
| Copying the alert record or recent readings to DE now | health data in DE storage for no gain while the engine can't run there; the plan has only times |
| Full-screen alarm, *Mute…* / *Remind less* before the unlock | the activity and the action receiver need the app's storage; swiping and unlocking are the exits |
| *Vibrate only when unlocked* fallback sound | the phone is locked by definition; the channel's own sound plays |
| A sound at the locked boot | a restart in the hand needs none; the alarm rings when *No data* would have |
| `setUnlockedDeviceRequired` on Sukrara's keys | would make #160 impossible and adds nothing while the sealed files are in CE storage |
