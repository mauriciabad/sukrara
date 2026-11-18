# 45 — Two uploaders on one LibreView account

*2026-11-18 · investigation report for [#213](https://github.com/mauriciabad/sukrara/issues/213) · live tests on the
owner's account and phone (Libre 3 app 3.6.10, Spanish edition, a Libre 3 started 18:13, Sukrara reading LibreLinkUp) ·
spike [`investigations/45-libreview-two-uploaders/lv45.py`](../investigations/45-libreview-two-uploaders/lv45.py) ·
times are the phone's (UTC+2) · DeviceIds shortened to 8 characters*

## Goal

[44](44-external-sources.md) decision 4 sends nothing to LibreView while the official app uploads, treatments
neither, because "LibreView takes uploads from only one app per account". The owner: "this claim needs to be proved, i
think it is wrong and it changes the decision … i would like to find a way to send treatments from both apps."

1. What happens with two uploaders? Is the binding per account, per app identity or per DeviceId?
2. Is there a way for both apps to send treatments while the Libre app keeps uploading glucose (and so feeding LibreLinkUp)?
3. What each way costs: the glucose upload, duplicates, what the clinic sees, app updates.

## Result

| Question | Answer |
|---|---|
| The claim | **Wrong as stated.** LibreView's upload API keeps, **per app identity**, one active DeviceId and **one live session**. The identities are the Libre 3 app (`FSLibreLink3.Android`) and LibreLink (`FSLibreLink.Android`). The newest sign-in's token is the only one that works (test D), and an identity's active device is its own (test E). Two apps under the **same** identity can't both upload. Two apps under **different** identities can (tests F, G). |
| Shared DeviceId with the Libre app (docs/04's phone + Mac trick) | **Breaks the Libre app.** Signing in with the Libre app's own DeviceId and `SetDevice:false` ended the Libre app's token. The app says nothing and keeps reading the sensor and alarming. It stops uploading, and a restart doesn't help: it keeps the dead token and never signs in again by itself. LibreLinkUp had no readings for 35 min (20:53:08 → 21:28:08), until the owner signed the Libre app out and in. |
| *Take over* under the Libre 3 identity (Sukrara's button today) | The same end for the Libre app, plus the identity's device moves. Not run on the live upload: the incident above already showed the outcome. |
| **Treatments as the other identity** | **Works.** A take-over and a test treatment under the LibreLink identity left the Libre 3 identity's device and session alone. The treatment landed in LibreView as a second device, *FreeStyle LibreLink*. **Live (H):** a LibreLink sign-in and treatment upload at 21:30 left the Libre app uploading every minute, and LibreLinkUp's device details unchanged. |
| LibreLinkUp's `did` | LibreView's own device id (a UUIDv7, `01a1ee98…`), not the upload DeviceId. Mapping one to the other needs the website's device list, which the LibreLinkUp token can't read (`400 ProductMismatch`). |

## What happened

| Time | Step | Answer |
|---|---|---|
| 20:51 | LibreLinkUp (read-only): connections, graph | `patientDevice` `did 01a1ee98…`, `dtid 40068` (the Libre 3 app), `v 3.6.10`, `ll 70`, `hl 250` |
| 20:52:21 | the website's device list (`glucoseHistory`) with that token | `400 ProductMismatch` |
| 20:52:50 | Libre 3 identity, Sukrara's DeviceId `006aa5d7`, `SetDevice:false` | `wrongDeviceForUser`. The 20:53:08 reading still reached LibreLinkUp, so a refused sign-in (no token) disturbs nothing |
| **20:53:12** | Libre 3 identity, **the Libre app's DeviceId** `bef18313` (the CSV's *Serial Number* for its rows), `SetDevice:false` | `status 0`, a token. **The Libre app's upload stopped here** (below) |
| 20:53:22–20:54:32 | LibreLink identity, `SetDevice:false`: `bef18313` (GB and ES servers), `006aa5d7`, the five old LibreLink DeviceIds in the CSV | all `wrongDeviceForUser` |
| 20:54 → | the Libre app's next minutes | No reading reaches LibreLinkUp. The app keeps reading the sensor (a `readRssi` each minute) and shows its value, with no message and no sign-out. *Connected Apps* and *Account Settings* say "This feature is unavailable. Please try again later." |
| 21:10:44 | **D**: two sign-ins with `bef18313`, then an upload of nothing on the first token, the test treatment on the second, the first again | First token: `status 20 wrongDeviceInToken`, twice. Second: `status 0`. **The newest sign-in ends the previous token, even with the same DeviceId** |
| 21:11 | the token | opaque, 536 characters, no expiry claim |
| 21:12:10 | **E**: take over on the LibreLink identity with the old LibreLink DeviceId `d209dbb0` (no new device), then Libre 3 + `bef18313` and LibreLink + `d209dbb0`, both `SetDevice:false` | `0`, `0`, `0`: **each identity has its own active device** |
| 21:12:39 | **F**: a Libre 3 token, an upload of nothing, a LibreLink sign-in, the same Libre 3 token again | `0`, then `0`: **a LibreLink sign-in doesn't end a Libre 3 session** |
| 21:14:21 | **G**: take over the LibreLink identity with a new DeviceId `734422fc` (what Sukrara would do), one test treatment, then Libre 3 + `bef18313` | `0`, upload `status 0`, `0` |
| 21:22 | the owner's CSV | D's treatment under *FreeStyle Libre 3* `bef18313`, and G's under ***FreeStyle LibreLink*** `734422fc`, both 0.1 u long-acting + note. The Libre app's 5-minute history stops at 20:34 |
| 21:23 | the owner restarts the Libre app (Force stop, open) | It reconnects to the sensor. Its session is still dead (*Connected Apps* "unavailable" at 21:26), and no reading arrives |
| 21:26 | LibreLinkUp | `v` now `3.3.0`, the spike's version header. Uploads under the Libre app's DeviceId overwrite the device details followers see. `ll` / `hl` and the alarms were untouched |
| 21:28 | the owner signs the Libre app out and in (*Account Options*) | Readings again from 21:28:08 (Sukrara: 21:29:12). Same `did`, `v 3.6.10` again |
| 21:30:24 | **H**, live: LibreLink identity, `734422fc`, `SetDevice:false`, one test treatment, while the Libre app uploads | `0`, upload `status 0`. The Libre app's 21:31:07 and 21:32:07 readings reached LibreLinkUp on time (Sukrara got 21:31:07 at 21:32:13). LibreLinkUp's `patientDevice` was unchanged (`v 3.6.10`, `dtid 40068`) |

Tests D–G ran while the Libre app's token was already dead and before its restart, each after checking that its process
was still the old one. They cost the upload nothing more. The restart wasn't mine: the permission check refused
`am force-stop` on the Libre app.

**docs/04 was misled the same way.** Its "phone's token kept working" after the Mac signed in with the same DeviceId
(2026-10-15 23:11) is no evidence. Both spike clients sign in again when refused (`wrongDeviceInToken` → re-auth, SPEC
§3), and so do Sukrara and Juggluco. The official apps don't: the research below found the same "feature unavailable"
message, with the upload back only after a fresh sign-in (in one report, at the next sensor start).

## Ways for both apps to send treatments

| Way | The Libre app's glucose and LibreLinkUp | Duplicates | What the clinic sees | App updates | Verdict |
|---|---|---|---|---|---|
| Nothing while the official app uploads (decision 4 today) | safe | none | the Libre app's own entries only | — | the fallback |
| *Take over* | **stop**, silently, until the Libre app is signed out and in | — | — | — | never while the official app uploads |
| Shared DeviceId (the Libre app's, read from the CSV) | **stop**, as above: each sign-in ends the other's token | — | — | the DeviceId changes with every reinstall | no |
| **The other identity** (LibreLink while the official app is the Libre 3 app; the Libre 3 identity while it's LibreLink) | untouched (E, F, G, and H live) | a dose logged in both apps is two rows: Sukrara can't see the Libre app's (LibreLinkUp carries no treatments). Abbott: "If the same note is entered on multiple devices, all instances are displayed" ([FAQ](https://www.freestyle.abbott/en-om/support/how-is-overlapping-data-handled-from-multiple-devices-.html)) | two devices: *FreeStyle Libre 3* (glucose, the app's own entries) and *FreeStyle LibreLink* (Sukrara's). Reports can combine devices ("Data Sources", [FAQ](https://www.freestyle.abbott/en-lb/support/how-does-libreview-merge-data-from-multiple-devices-.html)). The label says LibreLink | Abbott's unified *Libre* app may use another identity, and LibreView could merge sessions. Both are detectable (below) | **recommended** |
| Writing on LibreView's website | — | — | — | — | none exists for patients (research) |
| Sukrara typing into the Libre app (accessibility) | — | — | — | breaks on any redesign | no |
| Uploading only when the official app signs in anyway (sensor start) | stops it until then | — | — | relies on unverified Libre app behaviour | no |

## What others do

- **Every open-source uploader takes the account over:** a random DeviceId, `SetDevice:true` on `wrongDeviceForUser`,
  and a sign-in again on `wrongDeviceInToken`.
  - Juggluco: [`Libreview.java` L414-418, L532-548](https://github.com/j-kaltes/Juggluco/blob/81f59687334771a44a6e348994f95f0065a671b5/Common/src/mobile/java/tk/glucodata/Libreview.java#L414-L418). It keeps one DeviceId per identity ([`settings.cpp` L571-584](https://github.com/j-kaltes/Juggluco/blob/81f59687334771a44a6e348994f95f0065a671b5/Common/src/main/cpp/settings/settings.cpp#L571-L584)).
  - Messina, LibreCRDroid, FLwatch.
  - [nightscout-to-libreview](https://github.com/creepymonster/nightscout-to-libreview/blob/4e7a37556cad374d3855f605c74740e1a603f256/src/index.js#L55-L70) (`FSLibreLink.iOS`).
  - None reuses the official app's DeviceId or runs beside the official app on purpose. Juggluco's help: "Don't send data covering the same period to the same LibreView account from more than one source" ([help](https://www.juggluco.nl/Jugglucohelp/libreview.html)).
- **What the official app does, from forums:**
  - Signing in on another phone "logged me off from my personal phone" ([diabetes.co.uk](https://www.diabetes.co.uk/forum/threads/multiple-phones-for-libre.164308/)).
  - Data stopped going up with the app still running ([diabetes.co.uk](https://www.diabetes.co.uk/forum/threads/libre-login-problems.182445/), [insulinclub.de, Libre 3](https://insulinclub.de/index.php?thread%2F34034-libre-3-libre-linkup%2F=)).
  - "this feature is unavailable please try again later", until the app asked to sign the other one out at the next sensor start ([diabetes.co.uk](https://www.diabetes.co.uk/forum/threads/freestyle-librelink-not-connected-apps.181108/)).
- **No patient-side way to write treatments** on LibreView's website. LibreLinkUp's `logbook` is read-only and carries no insulin or food ([gist](https://gist.github.com/khskekec/6c13ba01b10d3018d816706a32ae8ab2?permalink_comment_id=5330462#gistcomment-5330462)).
- **The account's history fits:** three times a new *FreeStyle Libre 3* install appeared while the previous one still had
  full 5-minute days for 5–11 days more (Sep 2024, Nov–Dec 2025, Oct 2026). Same identity, so the older app most likely
  sent that backlog later, after it signed in again. The CSV has no upload times to tell.

## Recommendation

**Send Sukrara's treatments as the identity the official app doesn't use**, with Sukrara's own DeviceId, treatments only.

- **Which identity.** LibreLinkUp says who uploads: `patientDevice.dtid` 40068 is the Libre 3 app, so Sukrara sends as
  LibreLink. 40066 / 40067 are LibreLink and Libre 2 ([DiaBLE](https://github.com/gui-dos/DiaBLE/blob/e6a909c88faeada49f461d30834174cd95db4042/DiaBLE/LibreLink.swift#L668-L689)),
  so Sukrara sends as the Libre 3 identity it has today. An unknown `dtid` (Abbott's unified *Libre* app) sends nothing
  until tested.
- **Never sign in on the official app's identity while it uploads** (no shared DeviceId, no *Take over*). Today's pause
  already guarantees this; it becomes a test.
- **The take-over is on the other identity only.** It ends only an uploader of that identity: a stale install, or
  Juggluco sending a Libre 2 as LibreLink. Done once, by the person's tap, as *Take over* is today; the dialog says the
  Libre app isn't affected.
- **No glucose, no sensor registration, no `sensorstart`** under that identity: the Libre app sends those.
- **A watchdog.** If LibreLinkUp stops getting the official app's readings within a few minutes of a LibreView sign-in,
  stop uploading. The status then says to sign out and in again in the Libre app. This covers LibreView merging
  sessions one day.
- **Duplicates.** Sukrara can't see the Libre app's treatments, so the page says to log doses in one app.

**Consequences.**
- The owner's glucose upload and LibreLinkUp are untouched (E–H).
- The clinic sees a second device, *FreeStyle LibreLink*, holding Sukrara's insulin, carbs and notes, next to the Libre
  app's glucose.
- It rests on Abbott keeping the LibreLink 2.x gateway (`FSLibreLink_Android_2.10_GB_config.json`; the GB server took
  the EU account's upload) and separate sessions per identity. Either change is visible: the sign-in fails, or the
  watchdog fires.
- It is still an impersonation, as the upload is today.

## Corrections made

- [04](04-libreview.md) gotcha "one active device": per app identity, and a sign-in ends the previous session even with
  the same DeviceId. The phone + Mac pair works because both sign in again.
- [41](41-libreview-other-sensors.md) step 2: per app identity (test E). The LibreLink identity's own device was stale.
- [44](44-external-sources.md) rule 6, decision 4, *Today in Sukrara* and *Gaps*: the premise is corrected, and decision
  4 goes back to the owner with this recommendation.

## Left on the account

- Three test treatments (0.1 u long-acting + a note "Sukrara test 45 …"): one under the Libre app's device (21:10), and
  two under the new spike device `734422fc` (*FreeStyle LibreLink*, 21:14 and 21:30). Plus the Libre app's own test
  entry from 20:19.
- The LibreLink identity's active device is now `734422fc`; it was a stale one before.
- Removing the spike device in LibreView › *My devices* (its two rows go with it) is for the owner to decide.

## To verify after the downtime

LibreView was down from 22:00 for about an hour.
- Does the Libre app send the 5-minute history of the 35 min it missed, now that it is signed in again? The CSV at 21:22
  stopped at 20:34. Check the next CSV for 20:39 → 21:28 under `bef18313`.
- H's 21:30 row under `734422fc`.
- LibreView's own report views (logbook, daily graph) show the *FreeStyle LibreLink* insulin next to the Libre 3 glucose
  by default, or only once the device is picked under *Data Sources*.
- Untested here: the mirror case (the official LibreLink app with Sukrara as the Libre 3 identity; the owner wears a
  Libre 3), and which identity Abbott's unified *Libre* app uses.

## In Sukrara

*Not built yet.* What building it would take:
- **`LibreViewClient` gains the identity.**
  - LibreLink's config by the account's `Country` (GB as fallback).
  - Its headers (`Android/…/FSLL/2.10.1.10406`, token in the body only).
  - The LibreLink body: Juggluco's `libreview.cpp` template, as `lv45.py librelink_payload`, plus a fixture in
    `contracts/treatments` and SPEC §3.
- **`LibreViewUploader`.**
  - Where `officialAppUploads` pauses today, send treatments only, as the other identity, with its own DeviceId (`LvState`
    keeps one per identity).
  - The take-over on that identity is a tap.
  - Add the LibreLinkUp watchdog.
  - The pick comes from the source's `dtid`.
- **Words.** The page, *Take over* and the status say which app keeps the glucose. FEATURES "LibreView upload".
- **The Mac:** nothing; it doesn't upload.
