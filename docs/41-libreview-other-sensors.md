# 41 — Libre 2 glucose to LibreView, read through Juggluco

*2026-11-15 · investigation report · follows [04 — LibreView](04-libreview.md#in-sukrara-glucose-that-never-went-up) ·
spike [`investigations/41-libreview-libre2`](../investigations/41-libreview-libre2)*

## Goal

The owner's LibreView had no glucose after 2 Nov 18:59. Their Libre 3 Plus ended then, and since 3 Nov they've worn a Libre 2 read
through Juggluco. Sukrara uploaded only treatments (docs/04: glucose went up only from a Libre 3 Sukrara reads itself).
The owner: "it should not happen to begin with", "Libre 2 glucose should go up". Questions:

1. How do other apps get a Libre 2 into LibreView, and can Sukrara do it **as the one uploader**? docs/04 found that two
   uploaders fight over the account's active device.
2. Which other sensors can go up?
3. How much of the gap since 2 Nov can be filled?

## Result

| Question | Answer |
|---|---|
| How Juggluco does it | A **second identity**: the LibreLink app (`FSLibreLink.Android`, config `FSLibreLink_Android_2.10_<country>_config.json`, header `Abbott-ADC-App-Platform: Android/<rel>/FSLL/2.10.1.10406`, no API key), its own DeviceId, the sensor's 15-minute history (averaged stream) + scans, `sensorstart` with `productType 3`, `wearDuration 20160`, `PUT nisperson` with the whole serial. When LibreView answers `wrongDeviceForUser`, it **takes over by itself** (`SetDevice:true`). That's the fight. ([`Libreview.java` postgetauth](https://github.com/j-kaltes/Juggluco/blob/main/Common/src/mobile/java/tk/glucodata/Libreview.java), [`libreview.cpp`](https://github.com/j-kaltes/Juggluco/blob/main/Common/src/main/cpp/net/libreview/libreview.cpp), [`putsensor.cpp`](https://github.com/j-kaltes/Juggluco/blob/main/Common/src/main/cpp/net/libreview/putsensor.cpp)) |
| Sukrara's way | **One device, one identity** for every Libre: the Libre 3 app identity Sukrara already uses. A Libre 2's readings go up as 5-minute history under its own serial and life count. Nothing else uploads, so nothing fights. **Proved on the real account**: LibreView registered the Libre 2 and stored its 13 values exactly (below). |
| Other sensors | Libre 1 / 2 / 2 Plus through Juggluco: **yes**. Dexcom, SmartGuide, AiDEX, Sibionics: **no**. They would show in the clinic's LibreView as Abbott data, and Juggluco's author refuses them for that reason ([Juggluco #250](https://github.com/j-kaltes/Juggluco/issues/250): "I don't want to create a mess in Libreview"). A Libre 3 through Juggluco: not recognisable from Juggluco's serial, so not sent. |
| The gap | **2 Nov 18:59 → 3 Nov 14:39: nothing to fill.** No sensor gave readings: the Libre 3 Plus had ended, and the Libre 2 started at 13:38 with a one-hour warm-up. **3 Nov 14:39 → now: all of it** (Juggluco holds 16 952 minute readings, 3 438 five-minute values; 14 gaps of 6–30 min are lost in Juggluco too). Sukrara sends it once the person taps *Send earlier glucose*. |

## What other apps and people do

- **Juggluco** (GPL; read 2026-11-15 at `afe6539`) keeps separate tokens and DeviceIds per identity (`getlibreDeviceID(libre3)`).
  It sends Libre 2 data as LibreLink, "the last 89 days" on a first upload, and offers *Resend data from a date*. Its help
  says: "Don't send data covering the same period to the same LibreView account from more than one source"
  ([help](https://www.juggluco.nl/Jugglucohelp/libreview.html), [FAQ](https://www.juggluco.nl/Juggluco/faq.html)).
  It never sends non-Abbott sensors (#250 above). The DeviceId "identifies an app installation/uploader", and LibreView
  lists each one as a device that can be deleted with all its data ([#148](https://github.com/j-kaltes/Juggluco/issues/148)).
- **The real LibreLink app's upload**, captured in [xDrip+ discussion #2118](https://github.com/NightscoutFoundation/xDrip/discussions/2118):
  scheduled glucose every 15 min, with record numbers 15 apart (serial base | life count), and a `PUT nisperson` registration
  ("a new sensor is introduced to the system: acknowledged by the server"). xDrip+'s maintainer declined to upload to
  LibreView at all.
- **Missing data**: Juggluco users see "Success" while LibreView lacks data ([#271](https://github.com/j-kaltes/Juggluco/issues/271),
  [#14](https://github.com/j-kaltes/Juggluco/issues/14): it waits 20 min between uploads).
- **One device per account**: LibreLink users report that signing in on another phone signs the first one out
  ([diabetes.co.uk forum](https://www.diabetes.co.uk/forum/threads/multiple-phones-for-libre.164308/)). Whether LibreView tracks that per app
  identity or per account isn't documented anywhere (step 2 of the proof asks). *Per app identity: [45](45-libreview-two-uploaders.md).*
- **Abbott's apps**: one LibreView login for LibreLink, the Libre 3 app and the new *Libre* app
  ([Abbott](https://www.freestyle.abbott/en-us/support/faq/apps/libre-app-login-credentials-for-freestyle-libre-3-users.html)).
  The *Libre* app replaces the Libre 2 and Libre 3 apps ([App Store](https://apps.apple.com/us/app/libre-by-abbott/id6670330506)),
  so one app for both families is where Abbott itself is going.

## Design (offline, proved by tests)

- **Where the readings come from.** Juggluco's web server names every reading `SERIAL#lifeCount` (`_id` in
  `entries.json`, `recordId` in the live stream; the life count is the sensor's own minute: it ran 86 min behind the
  phone's clock over 12 days, so the start time can't stand in for it). `JugglucoSource` passes these on as
  `JugglucoEntry`; `SourceManager` emits them as a `SensorGlucoseBatch` with `everyMinute`.
- **What goes up.** A Libre 1 / 2 / 2 Plus serial (11 characters, `0M…` / `3M…`: the NFC uid's fixed top bytes;
  Sibionics, Dexcom never match). One value per 5 minutes of the sensor's life: the first reading in each slot, under
  the slot's first minute. Record number = the serial's base (last 9 characters, docs/04) | that life count. The same
  readings in any order give the same records, so re-sending is harmless. `sensorstart` as LibreLink sends a Libre 2
  (`productType 3`, `wearDuration 20160`). The registration uses the whole serial.
- **Fixture**: `lv2.py fixture` → `contracts/treatments/libreview-glucose-fixture.json` (synthetic minute stream with
  holes, a repeated minute, readings in slot 0) → `LibreViewPayloadTest` reproduces the values and the bytes.
- **The gap.** Juggluco's first history after the app starts is now the current sensor's whole life (`historyPath`,
  `find[date][$gte]=start`; before: the last 1 600 minutes), and the upload holds 15 days instead of 3. The upload
  still starts at the switch-on by itself (another app may have sent the earlier time). *Send earlier glucose* sends
  the rest when the person asks.

## Proof on the real account

*2026-11-15, each step approved by the owner, run one at a time from the main session with the owner's direct OK,
stopping at the first unexpected answer.* Steps and expected answers: (1) Libre 3 identity, spike DeviceId, no take-over →
`wrongDeviceForUser`; (2) LibreLink identity, no take-over → `status 0` means the active device is tracked per app;
(3) take over → `status 0`; (4) register `3MH01GFXNU8` → `status 0`; (5) 3 Nov 15:00–16:00, 13 five-minute values
under device `Sukrara-spike41`; (6) LibreView's CSV. Cleanup: remove the spike device(s) in LibreView › My devices;
Sukrara › LibreView › *Take over*; delete `~/.config/sukrara-spike41`.

| Time (local) | Step | Answer |
|---|---|---|
| ≈ 14:55 | 1. Libre 3 identity, spike device, no take-over | `status 20 wrongDeviceForUser`, as expected: Sukrara is the account's uploading device |
| | 2. LibreLink identity, spike device, no take-over | `status 20 wrongDeviceForUser`: the uploading device is (most likely) one per **account**, not per app. A stale LibreLink device would answer the same, so it isn't conclusive, but either way a second identity next to Sukrara's would have to take over. *[45](45-libreview-two-uploaders.md) settled it: **per app identity**. The LibreLink identity had its own stale device, and taking it over leaves the Libre 3 identity's device and session alone* |
| | 3. Libre 3 identity, take over | `status 0` |
| | 4. Register `3MH01GFXNU8` (a Libre 2, whole serial) under the Libre 3 identity | `status 0` |
| 14:56 | 5. 13 five-minute values, 3 Nov 15:00–16:00 (207 → 150 mg/dL) + `sensorstart` | `status 0`, `UploadId`, `SerialNumber` = the spike DeviceId; every `MeasurementCounts` and `ItemCount` 0. That's the answer the real LibreLink app gets too (xDrip+ #2118), so it says nothing: ingestion is asynchronous |

**CSV check** (the owner's *Download glucose data*, 15:10): **passed.** 14 rows of the spike device (Device "FreeStyle
Libre 3", serial = the spike DeviceId). There are **13 Historic Glucose rows on 3 Nov, 15:00 → 15:59, the values identical to
Juggluco's** (207, 205, 196, 195, 186, 185, 177, 168, 162, 157, 160, 151, 150). So LibreView stores a Libre 2's glucose sent
as the Libre 3 app, under the Libre 2's serial. The 15:04 → 15:11 step is right: Juggluco itself has no reading
between 15:08:39 and 15:11:40 (minutes 90–91), so slot 90's first reading is 15:11. The 14th row, the `sensorstart`
note, was filed on **15 Nov 14:56**, the upload time: Sukrara's (and the Libre 3 app's) `timestamp` was "now". Fixed:
both times are now the sensor's start, so a start sent days later (mid-sensor, a backfill) is still filed on the day the
sensor started. Sukrara sends it once per sensor (`LvSensorState.startSent`).

Cleanup: the owner removes the spike device in LibreView › My devices (its 14 rows go with it) and taps *Take over* in
Sukrara; `~/.config/sukrara-spike41` and the copy of Juggluco's readings are deleted.

## In Sukrara

| Added | Where |
|---|---|
| Juggluco's readings with their sensor and life count; the first history is the current sensor's whole life | `:sensor` `JugglucoSource` (`parseHistory`, `parseLive`, `historyPath`), `SourceManager`; `JugglucoSourceTest` |
| A Libre 1 / 2 read through Juggluco goes up as 5-minute values of its own serial and life count, as the one device; other families never | `:integrations` `LibreSerial`, `LibreViewQueue.slots`, `LibreViewUploader.buffer`, `LvSensor.productType`; `LibreViewPayloadTest` (contract fixture), `LibreViewUploaderTest` |
| *Send earlier glucose*: the current sensor's glucose from before the switch-on, once asked (warns that another app's upload would count twice) | `LibreViewUploader.sendEarlier`, `LibreViewPage`; `LibreViewUploaderTest` |
| The sensor's whole life held (15 days, was 3) | `LibreViewQueue.HISTORY_MIN` |
| The stalled banner without a clinic: "Nothing since …" | `:feature:home` strings |
| `sensorstart` filed at the sensor's start, not the upload (both families) | `LibreViewPayload`, contracts/treatments/SPEC.md §3, the fixture |

| Discarded | Why |
|---|---|
| A second identity (LibreLink) for the Libre 2, as Juggluco does | two tokens, two devices in LibreView, two registrations, and if LibreView tracks the active device per account, the take-over fight inside one app; Abbott's own *Libre* app is one app for both families. *([45](45-libreview-two-uploaders.md): it is per identity, so no fight; the other reasons stand)* |
| Turning on Juggluco's *Send to Libreview* next to Sukrara's treatments | Juggluco takes over by itself on `wrongDeviceForUser`; two uploaders on one account is what docs/04 found fighting |
| Dexcom, SmartGuide, AiDEX, Sibionics glucose | the clinic would read it as Abbott data; Juggluco's author refuses the same (#250) |
| A Libre 3 read through Juggluco | its serial in Juggluco's `_id` can't be told apart from other families with certainty; a Libre 3 is read by Sukrara itself |
| Life counts from the sensor's start time | the Libre 2's own minute drifted 86 min from the clock in 12 days; Juggluco's counter is what LibreLink uses |
| 15-minute history (LibreLink's) | the Libre 3 identity's history is 5-minute; LibreView builds its reports from it either way |
| Backfilling by itself from the sensor's start | another app may have sent that time: LibreView would count it twice; only the person knows |
| A date and time choice for *Send earlier glucose* | the phone holds only the current sensor, so the choice is "from when Sukrara has it" or nothing; a picker adds a step without a case that needs it yet |
| Naming Sukrara's device in LibreView (the website's *Device nickname*) | the website sets it with `POST user/updateNickname {nickname, deviceId}` on its own API (`api[-region].libreview.io`, Bearer token from the website's `auth/login`, header `Account-Id` = SHA-256 of the user id), where `deviceId` is LibreView's own id from the devices list (`glucoseHistory` `data.devices`: Sukrara's `sn 006aa5d7…` is `id 01a1cade…`), not the upload's DeviceId. Neither the upload nor the app login has a nickname field. Tried with the LibreLinkUp login Sukrara already has: without `Account-Id` → `400 RequiredHeaderMissing`, with it → `400 ProductMismatch`. Only the website's own login would do: a second sign-in (with its extra steps) for a label, so not done. The person can type it on the website |
| Juggluco's calibrated values | Sukrara takes what Juggluco serves; Juggluco's own LibreView upload sends uncalibrated values. Not handled: the owner doesn't calibrate |
