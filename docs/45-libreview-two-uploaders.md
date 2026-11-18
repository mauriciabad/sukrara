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
| Reading the official app's treatments back (45b) | **Possible**, from the website's Daily Log report (`POST /reports`, client report 5), one device at a time: every rapid dose's and carbs entry's time, insulin and carbs as hourly sums, notes with their time. It needs the website's own login, which asks for an SMS code (then a trusted-device token). Details in *Follow-up (45b)*. |

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
  - *(45b)* A third-party upload stopped the official app's until it was signed out and in (gist comment, 2026-04-10, [khskekec gist](https://gist.github.com/khskekec/6c13ba01b10d3018d816706a32ae8ab2)).
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
- **Duplicates.** Sukrara doesn't see the Libre app's treatments, so the page says to log doses in one app. *(45b: it could, from the Daily Log report behind the website's SMS login; a separate decision.)*

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
  entry from 20:19, and two 45b tests logged in the Libre app at 22:04 and 22:42 (0.1 u long-acting each, notes
  "Sukrara test 45b: ignore" and "Sukrara test 45b-2: ignore").
- The LibreLink identity's active device is now `734422fc`; it was a stale one before. 45b's `ll-tokens` changed
  nothing there (no take-over, no data).
- Removing the spike device in LibreView › *My devices* (its two rows go with it) is for the owner to decide.

## Follow-up (45b): LibreLinkUp, a second LibreLink session, the clinic's view, reading treatments back

*21:44 → 22:45. Spikes `lv45.py ll-tokens`, [`lvweb.py`](../investigations/45-libreview-two-uploaders/lvweb.py) and
[`lvreport.js`](../investigations/45-libreview-two-uploaders/lvreport.js), the last in a libreview.com tab the owner
signed in to. LibreView's announced downtime ("18 November 2026 10:00 pm for approximately an hour") began between
22:04:25 and 22:05:36: every API then answered `{"status": 911}`. The website checks ran before it. LibreLinkUp's
last reading before it was 22:04:07; until then Sukrara had every minute from 21:43:07, never more than 62 s apart.*

| Question | Answer |
|---|---|
| 1. The app the owner signed in to | **LibreLinkUp** 5.2.0 (`org.nativescript.LibreLinkUp`, installed 20:17), the follower app. There's no LibreLink app on the phone. It uses the same LibreView account, following the owner, and shows the live value, the 12 h graph and a logbook of high-glucose alarms. Its glucose is the Libre app's upload, as Sukrara's is. It signs in as LibreLinkUp (`llu.android`), not as an app identity. Its first sign-in (≈ 20:18–20:22, its files' times) left the Libre app uploading until the spike's 20:53, and the LibreLink identity was still on `734422fc` at 21:54. LibreLinkUp sessions don't end each other: Sukrara's, the app's and spike logins at 20:51, 21:26 and 21:51 ran side by side, and Sukrara got every minute's reading (21:43 → 22:04, until the maintenance). |
| 2. Two sessions on one identity | No LibreLink app to test. On the LibreLink identity with the spike device alone (21:54:46): two sign-ins, then an upload of nothing on each token. The first answered `status 20 wrongDeviceInToken`, the second `0`. **The same one-live-session rule as the Libre 3 identity (D).** An official LibreLink app signed in on the account would stop uploading, most likely silently, the moment Sukrara signs in as LibreLink. |
| 3. What the clinic sees | **Every device, merged, by default.** The website's own report request (21:58) puts the Libre app's device first, the 17 others as secondaries (*FreeStyle LibreLink* `734422fc` and Sukrara's `006aa5d7` among them) and the account's connected pen. The page: "Data Merged from 19 Sources … Notes from all devices are displayed without modification". Nothing has to be picked under *Data Sources*. The reports show sums per hour and notes, with no device names (below). This account's default set is AGP, Glucose Pattern Insights, Snapshot, Device Details, Daily Patterns and Weekly Summary (`ClientReportIDs [26, 27, 1, 18, 14, 10]`). The Daily Log is an extra, and the Weekly Summary carries daily insulin and carbs totals ([Abbott's report guide](https://files.libreview.io/files/documents/en-GB/FSReportTour_2026-09-08.pdf)). A clinic's own LibreView (Pro) account: not checked; it uses the same report service. |
| 4. The Libre app's catch-up | **Yes.** Its 5-minute history 20:39 → 21:24 (10 values, 208 → 114 mg/dL) is under `bef18313` in the 21:57 file and report, with the scans the owner made during the outage (20:56–21:02). It sent the backlog after the 21:28 sign-in. |
| 5. Reading the official app's treatments | **Yes, from the Daily Log report**, behind the website login's SMS step. Details below. |

### The Daily Log report carries the treatments

- **The chain** (as [lvconnect](https://github.com/skobkars/lvconnect/blob/b3209dd5401072b76faf9d587d82a77bb6513d4e/index.js#L402-L608)
  and [libre-desktop](https://github.com/sshivaji/libre-desktop/blob/e6f85d2137f2f206d0d40391c28e8bcb5e956a5f/Sources/LibreDesktop/LibreViewClient.swift#L140-L176)
  run it for glucose). `GET /reportSettings` gives `dataSources` (LibreView id → `sn` = the upload DeviceId, `type`,
  `uploadDate`, `daysData`) and `endDate`. Then `POST /reports` with the primary device, every other one as secondary,
  `ReportIds [500000 + type]` and `ClientReportIDs [5]`. The answer's `data.url` is a channel `{ws, lp}`; the long-poll
  gives `operation: "update"` with `args.urls["5"]`, a page on `lrs-eu.libreview.io`. Fetched with `?session=<token>`,
  that page holds `window.report = {Data: …}` (63 KB for 2 days, 387 KB for 14). The whole chain took 1.1 s.
- **`Data.Days[]`**, each day:
  - `Glucose`: segments of `{Timestamp, Value, IsTimeChange}`, the 5-minute history.
  - `RapidActingInsulinLogged` and `CarbsLogged`: the time of every entry.
  - `SensorScans`, `NewSensors`, `BloodGlucose`, `Ketones`.
  - `Hours[24]`: `IsRapidActingInsulinLogged`, `RapidActingInsulinTotal` / `Manual` / `Meal` / `Correction` /
    `UserChange`, `IsLongActingInsulinLogged`, `LongActingInsulin`, `IsCarbsLogged`, `Carbs`, and `notes` as
    `[{note, timestamp, type: "custom"}]`.
  - Times are the device's wall clock as Unix-shaped seconds (`Date` is 00:00 of the day).
- **The owner's 20:19 test is there**: hour 20 of 18 Nov has `LongActingInsulin 0.1` and the note *Test treatment with
  note* at 20:19:07. Hour 21 has 0.3, the three spike doses of two devices summed, and their three notes at 21:10, 21:14
  and 21:30.
- **Checked against the 21:57 file, 14 days.** Every rapid dose and carbs entry is in the lists at its minute, with the
  hour's amount. On 17 Nov: 02:41 2 u, 14:18 9 u, 20:49 9 u. On 13 Nov: carbs at 20:11, and hour 20 has 35 g.
  Long-acting appears only as the hour's sum (15 u at 14:18 → hour 14).
- **One device at a time.** The report covers only the devices in the request (22:04). With the Libre app's device
  alone and no secondaries, hour 20 is 0.1 u + the 20:19 note and hour 21 is 0.1 u + the 21:10 note (the spike's dose
  under that DeviceId, test D). With the *FreeStyle LibreLink* device alone (`ReportIds [540066]`), hour 21 is 0.2 u +
  the 21:14 and 21:30 notes. So a reader asks for the official app's device only (its `sn` is the DeviceId; LibreLinkUp's
  `did` is its LibreView id), and Sukrara's own uploads never come back.
- **What it loses:**
  - the long-acting dose's minute;
  - each amount once an hour has two entries of a kind;
  - the link between a dose and its note (the Libre app sends the note as its own record, docs/44).

  Enough to show the Libre app's treatments with their time and amount in most hours.
- **Freshness.** At 21:57 the report (and the file) had glucose to 21:39: the 5-minute history trails by 15–20 min. The
  Libre app's device showed `uploadDate` 21:56. **A treatment logged in the Libre app is in the report within a
  minute**: the second 45b test, saved at 22:43:21 (filed at 22:42, the time of the app's reading), was there at
  22:44:01. The app uploads every minute. Hour 22 then read 0.2 u, the two 45b doses summed. The first, saved at
  22:05:49 during the maintenance, went up with the app's first uploads after it, with no new sign-in (in the report at
  22:42:14).
- **What a reader needs.**
  - **The website's own login, with its second step.** `POST /auth/login` with `product: lv` answered `status 4`, step
    `2faVerify`, primary method SMS, for this account (21:50, `lvweb.py login`). The step goes on with
    `POST /auth/continue/2fa/sendcode {isPrimaryMethod}` (SMS, or email when `false`) and
    `POST /auth/continue/2fa/result {code, isPrimaryMethod}`, which returns the session
    ([vitalTask](https://github.com/diarmuidmcg/vitalTask/blob/39a3d5cfcc6e5cfa82b71df3e55e6fac034a46ad/main.py#L112-L163),
    [cgmphasedlstm](https://github.com/robcbean/cgmphasedlstm/blob/ef5f83cfc0f182776e862261d3bdd2fa4ae2087e/freestyle/getmessages.py#L250-L280)).
  - **Skipping the code next time.** "Remember me" makes a trusted device: the website keeps `trustedDeviceTokens` in
    `localStorage` (here after the owner's SMS step), and a login with `trustedDeviceToken` in its body skips the step
    ([lvconnect](https://github.com/skobkars/lvconnect/blob/b3209dd5401072b76faf9d587d82a77bb6513d4e/index.js#L133-L160)).
    Its author: the token stays valid while it is used
    ([nightscout #7191](https://github.com/nightscout/cgm-remote-monitor/issues/7191#issuecomment-1726068948)). Abbott
    asks for the code again after a password reset, a reset of the 2FA settings or a cleared cache
    ([LibreView help](https://pro.libreview.io/articles/complete-2FA)). Junction, a paid aggregator that reads these
    treatments, warns of a forced password change at least once a year
    ([docs](https://docs.junction.com/wearables/guides/abbott-libreview)).
  - **The session.** Its token lasts 2 h (`duration 7200000`) and ends after 2 h without use (#7191). It carries the user
    id in `Account-Id` (SHA-256), and the `ticket` in answers renews it.
  - **Not the LibreLinkUp token.** On website endpoints it gets `400 ProductMismatch` (21:51).
  - **The load.** Four requests per report. Polling every 15 min (libre-desktop's pace, with a 15-minute back-off on
    `429`) is about 400 requests a day. No limit was hit in five runs. No public report of a `429` on the website API was
    found; LibreLinkUp locks an account for 300 s after 3 failures
    ([nightscout-librelink-up #218](https://github.com/timoschlueter/nightscout-librelink-up/issues/218)).
- **Nobody else reads them.** Public code reads only `Days[].Glucose` (and scans): lvconnect
  ([L662-L663](https://github.com/skobkars/lvconnect/blob/b3209dd5401072b76faf9d587d82a77bb6513d4e/index.js#L662-L663)),
  libre-desktop, cgmphasedlstm, vitalTask. Junction's "Libre Notes Timeseries" (note, carbs, rapid / long insulin,
  "hourly granularity") is the only reader found, and it is closed. The other ways in carry no treatments:
  - the Libre Data Sharing API's scopes are `read.glucose`, `read.glucometrics` and `write.insulin`
    ([Health2Sync's notes](https://hackmd.io/@conanstar/r1lgCfkD_));
  - Tidepool's Abbott link sends CGM data only ([support](https://support.tidepool.org/hc/en-us/articles/39662428332436));
  - the app API has no download (`BackupFileExists` in its sign-in answer is always `false` in captures,
    [xDrip+ #2118](https://github.com/NightscoutFoundation/xDrip/discussions/2118)).

  Abbott's new *Libre* app does bring a person's data over from the cloud
  ([FAQ](https://www.freestyle.abbott/en-us/support/faq/apps/keeping-glucose-data-when-moving-to-the-libre-app.html)).
  It does so through its encrypted API, which only Juggluco touches, and only for tokens.

### LibreView's maintenance stops LibreLinkUp too, and says so

The owner: "while libreview is under maintenance librelinkup doesn't work either". During tonight's maintenance
(probes at 22:06, 22:26 and 22:28, a few calls each, no credentials or tokens kept):
- **When.** 22:04:25–22:05:36 → 22:35:39–22:38:39, from the first answer without `lvapi` to the first normal one again.
  That is half an hour of the announced hour. LibreLinkUp had a reading again at 22:41:06. Sukrara got its first at
  22:43:32 (22:43:07): it tries again with a back-off, so it came 2 min after LibreLinkUp had readings and 5 min after
  the servers. The status page still
  said `in_progress` / `under_maintenance` at 22:44.
- **The region answers HTTP `911` with body `{"status": 911}`** (`content-type: application/json`, Cloudflare, no
  `lvapi` header, no `Retry-After`). It answers this for every path, before authentication:
  - `api-eu.libreview.io`: `/llu/auth/login`, `/llu/connections`, `…/graph`, `…/logbook`, `/llu/config/country`,
    `/auth/login`;
  - the app API's `/api/nisperson/getauthentication` on `api-c-es`, `api-c-gb` and `api-eu2`.
  
  The global `api.libreview.io` kept working. Its LibreLinkUp login answered `status 0, redirect, region "eu"`, and
  `api-us` answered `404` as usual. So the outage is per region, and the login says which region is the person's.
  Abbott's config CDN (`fsll3…/DE.json`, its `Configuration`, `FSLibreLink_Android_2.10_ES_config.json`) and
  LibreLinkUp's country config (`/llu/config/country` on the global host) carry no maintenance field.
- **The LibreLinkUp app knows it.** Its modal (ids `server-maintenance-modal-*`) has the title *Down for maintenance*,
  the text *Please try again later.* and one button, *OK*. It gives no end time.
- **Sukrara didn't.**
  - Home said *No readings from LibreLinkUp · No answer from LibreLinkUp. Sukrara tries again by itself.* (Android
    `OnlineHealthText.kt:68`; no code path knows `911`).
  - The ongoing notification said *No data · Last reading 10:04 pm · 72 mg/dL*.
  - The no-data alert rang at 22:24:07, 20 min after the last reading (22:04:07). That was right: nobody, Sukrara
    included, had the glucose.
  - The Libre app kept reading the sensor and alarming on the phone, as during the 20:53 outage.
- **Telling maintenance apart.** `911` has been seen only during maintenance and a migration. Other failures look
  different:

  | Failure | What the client gets |
  |---|---|
  | No internet | connection error |
  | Cloudflare or server trouble | `5xx` |
  | Lockout | `429` / `430` with `"locked"` |
  | Refused password | `status 2` |
  | Terms or a 2FA step | `status 4` |
  | Account moved to another region | `redirect` |

  Others saw `911` for an hour on `api-de` before a breaking change, on 2024-06-19
  ([ioBroker forum](https://forum.iobroker.net/topic/55443/test-freestyle-libre-librelinkup/168)), and during a
  region migration ([khskekec gist](https://gist.github.com/khskekec/6c13ba01b10d3018d816706a32ae8ab2?permalink_comment_id=4731427)).
  No open-source client found handles it.
- **The end time is public: Abbott's status page** ([status.freestyle.abbott](https://status.freestyle.abbott/),
  Atlassian Statuspage, public JSON at `/api/v2/summary.json` and `/api/v2/scheduled-maintenances.json`, no key).
  - At 22:28 the overall `status.indicator` was `maintenance`.
  - The in-progress entry was *AWS RDS Maintenance – EMEA, UK, IRELAND*: `scheduled_for` 12:00, `scheduled_until`
    13:00 US Pacific (22:00 → 23:00 here).
  - Its components are per product group (*LibreLinkUp Mobile App*, *LibreView Web App*, *Libre App, FreeStyle
    LibreLink, … Mobile App*, *Libre Data Sharing API*) and per region (*Europe, Middle East, Africa*, *UK & Ireland*:
    `under_maintenance`; the rest `operational`).
  - Abbott's text: LibreLinkUp users "will not be able to sign into their app or receive glucose data or
    notifications", and the official apps can't "upload data".
  - Abbott posted it 9 days ahead (2026-11-09) and said "Maintenance will begin as scheduled in 60 minutes" at 21:00.
    Ten more windows are listed up to 2027-01-14, one per region.
  - So a client can show *LibreLinkUp is down for maintenance until 23:00* from the `911`, plus the status page's entry
    for the person's region (from the login's `region`, or the account's country). It can even warn ahead of a window.
    The `911` is the live signal and the page only the plan. Tonight the servers were back by 22:38 while the page
    still said "in progress" at 22:44.
  - Mapping LibreLinkUp's regions to the page's names (`eu` → *Europe, Middle East, Africa*, `eu2` → *UK & Ireland*,
    `de` → *Germany*, …) is a small table. The page itself is unofficial for clients and can change.

## In Sukrara

*2026-11-18, the owner: "yes, build this so sukrara can also upload treatments. 'a dose logged in both apps shows
twice' it is fine. just let the user know he has to log only in one app."* Android (the Mac doesn't upload). Offline
only: tests against MockWebServer and the contract, an emulator against the fake; the live round on the owner's phone
comes after LibreView's downtime.

| Added | Where |
|---|---|
| The identity: the Libre 3 app's and LibreLink's config, headers (LibreLink: `FSLL` on the sign-in only, the token in the body only, no API key) and body (Juggluco's template, Sukrara's device names, the Libre 3 body's settings and entries byte for byte); a token per identity, the identity in it | `:integrations` `LvIdentity`, `LibreViewClient`, `LibreViewPayload.linkPayload`; SecureStore `libreview-librelink`; contracts/treatments/SPEC.md §3 "LibreLink", `libreview-librelink-fixture.json` (`lv45.py fixture`); `LibreViewPayloadTest` |
| The pick from LibreLinkUp's `dtid` (`connection.patientDevice`, else `activeSensors[0].device`; kept in `librelinkup-source`): 40068 → send as LibreLink; 40066 / 40067 → as the Libre 3 app; else nothing | `LibreLinkUp.deviceType`, `LluSourceState.deviceType`, `LibreLinkUpSource.uploader`, `LvIdentity.ofDeviceType`; the fixture's `identities` / `deviceType`; `LibreLinkUpSourceTest` |
| Beside the official app: treatments only, under the other identity, its own DeviceId (`LvState.linkDeviceId`); no glucose, registration or `sensorstart`; a sign-in at once on that identity (another device there shows *Take over* before anything is logged) | `LibreViewUploader` (`LvMode`, `uploadOn`); `LibreViewUploaderTest` *issue 213 - beside …* |
| **The guard**: every sign-in and take-over goes through one function that refuses the official app's identity, and every identity while it's unknown, read at that moment | `LibreViewUploader.signInOn`, `LvIdentity.mayUse`; *THE guard* test (cycles, refused tokens, another device, *Take over*, the `dtid` changing between them: no request under the official identity) |
| An entry stays under the identity it first went up under: its edits and deletes go there, they wait while it is the official app's; back as the uploader, a LibreLink entry is corrected as LibreLink, new ones go as the Libre 3 app; glucose from the takeover on (39ea0076e) | `LvSent.via`, `LibreViewQueue.home`, `LvEntryMarks.waits`; *back as the uploader …* test |
| The watchdog: each sign-in beside the official app watched; LibreLinkUp answering for 10 min with no reading newer than it (the app had uploaded in the 5 min before) → stopped, says why and how to fix (sign the Libre app out and in), *Send again*; home's banner | `LvWatchdog`, `LvState.watch` / `libreAppStopped`, `UploadStatus.LibreAppStopped`, `BannerKind.LibreAppStopped`; watchdog tests, `BannerSlotTest` |
| The person's view: *Beside the Libre app* (who sends what, the device *FreeStyle LibreLink*, **log each treatment in one app only**), *Glucose* "The Libre app sends it", the take-over dialog naming the identity and that the Libre app isn't affected; home's marks go up now | `LibreViewPage`, `in_lv_*` strings (en, es), `LibreViewMarks`; `IntegrationsGallery` / `HomeGallery` |
| A fake LibreView for the emulator: the LibreLinkUp fake serves the app API too, keeps one session per identity, and stops its readings on a sign-in under the official app's identity (`lvmerge`: under either) | `tools/librelinkup-probe/fake.py`, `LibreViewClient.fake`, the `LIBRELINKUP` debug command; docs/QA.md Q30 |

| Discarded | Why |
|---|---|
| LibreLink's config by the account's country | the GB config's server took the EU account's sign-in and upload (G, H); the Libre 3 identity already uses one config (DE) for everyone. One URL, the same for all; a country that fails says so in the status |
| The LibreLink body's target range 70–180 (lv45's, Juggluco's) | 70–140, the Libre 3 body's: the two identities never send the account two different ranges |
| Reading the official app's treatments to avoid doubles | LibreLinkUp carries none; the owner accepts the double and asked for the one-app rule instead |
| A sign-in only when there is something to send (beside the official app) | fewer sign-ins near the Libre app, but another device on that identity would show only after the first dose waits; the sign-in is on the identity the official app doesn't use, and the watchdog watches it |
| Resuming by itself once LibreLinkUp's readings come back after a stop | if LibreView merged the sessions, the next sign-in stops the Libre app again: a loop the person sees as glucose that keeps stopping. *Send again* is one tap after the fix the person makes anyway |
| A separate *Take over* for corrections of LibreLink entries while Sukrara is the uploader | only an older LibreLink install would block them; they stay *Updating* in the list until then. Rare, and the page's *Take over* is for the identity Sukrara sends new entries under |
| The Mac | it doesn't upload |

**Reading the Libre app's treatments (45b), if the owner wants it:** a separate decision, also not built.
- **The sign-in.** The website's own sign-in, a second one beside the LibreLinkUp login. Its SMS or email code is typed
  in Sukrara once. The trusted-device token is kept with the account in the Keystore store, and a reset asks for the
  code again.
- **The report.** A Daily Log for the official app's device only (LibreLinkUp's `did`), for today and yesterday,
  every 15 min while that app uploads.
- **The entries.** Each one becomes a treatment of origin *Libre app*, never uploaded:
  - rapid insulin and carbs at their minute, with the hour's amount (an hour with two entries of a kind: split
    evenly, or one row with the sum);
  - long-acting insulin at the hour;
  - notes at their minute.
- **Contracts and tests.** A fixture of the report JSON in `contracts/treatments`, and `HistoryImport`'s rules for
  nothing twice (the file import brings the same entries exactly).
- **What it would cost.** One more Abbott sign-in to keep alive; a format Abbott can change without notice; and about
  400 requests a day.

### In Sukrara: LibreView's maintenance (both apps)

*2026-11-18, the owner: "would be nice to detect 'Down for maintenance' edge case, and act accordingly … no readings
because of maintenance is not an error user can act on, he can only wait or change source."* Offline only: tests
against the contract and MockWebServer / fakes, the emulator against `fake.py` (`maintenance`, `until`, `ahead`); the
live window had ended before it was built.

| Added | Where |
|---|---|
| **911 is maintenance**: HTTP 911 or body `status 911`, before every other rule, on the LibreLinkUp login and every call; the account is never touched (a 911 login isn't a refused password) | contracts/sources/librelinkup `login.json` / `calls.json` (`generate.py`); Android `LibreLinkUp.parseLogin` / `parseCall`, `LluSession.Maintenance`; Mac `LibreLinkUp.swift`; both apps' contract tests |
| **A health state, not an error**: `maintenance` whatever the network and the warm-up; it ends with the first normal answer | `contracts/sources/health.json`; `OnlineHealth.Maintenance`, `SourceProblem.Maintenance`, `SourceStatus.Interrupted.until`; Mac `SourceHealth.State.maintenance` |
| **Polled gently**: the failures' backoff at most 5 min apart, 30 s after the window's end when Abbott gives one and that is sooner; a 429 still holds | `poll.json` `after` / `maintenanceMax_s` / `maintenanceAfterEnd_s`; `LluPoll.maintenanceS` (both apps) |
| **The end from Abbott's status page** (`status.freestyle.abbott/api/v2/summary.json`, no key, nothing of the person's sent): the account's region (the login's redirect) mapped to the page's components in the *LibreLinkUp Mobile App* / *LibreView Web App* groups (`eu` → *Europe, Middle East, Africa*, `eu2` → *UK & Ireland*, `us`, `de`, `fr`, `jp`, `ap` → *Other Asia*, `au` → *Australia, New Zealand*, `ae`, `ca`, `la`, `ru`, `cn`); in progress or begun → `until`, none or past its planned end → no end said; fetched only while LibreLinkUp is a signed-in source (on entering maintenance, every 10 min in it, else every 6 h); a failure keeps the last answer and says nothing | `status.json` (the rules, the regions, 14 cases); Android `LibreViewStatus` (`:core:data`), Mac `LibreViewStatus.swift`; privacy policy, `tools/check/allow.json` |
| **Every surface says it, as a wait**: home's reason / the Mac panel (*LibreView maintenance until 23:00* · *Abbott is servicing LibreView, so LibreLinkUp sends no glucose. It comes back by itself when that ends. To get glucose meanwhile, change the source.*, a quiet *Change source*), Settings › Source and setup's Connect (a clock mark, no error colour, no *Try again*), the glucose notification's line, the Mac widgets, the card on every card surface (*LibreView maintenance* / *until 23:00* in the value's place, the graph-only pill, TalkBack / VoiceOver) | `OnlineHealthText`, `maintenanceTitle`, `NoDataReason.SourceTrouble.waitOnly`, `LiveNotificationContent.detail`, `CardPause` (both renderers; graph scenes `maintenance` / `maintenance-open`, `words.json`); Mac `NoDataText`, `WidgetWords`, `LibreLinkUpRows` |
| **The no-data alert keeps its time, says why**: *No data · LibreView maintenance*, *Until 23:00 · last reading 22:04 · 72 mg/dL* | `contracts/alerts/copy.json` `why: maintenance`; `AlertCopy` / Mac `AlertText`; no scenario changed |
| **Warn ahead**: *Next maintenance · LibreView maintenance today, 22:00–23:00: LibreLinkUp sends no glucose then* on Settings › Source as soon as Abbott lists it; the same quiet line under the card (home, the Mac panel) from 12 h before; one silent notification an hour before, once a window (Android channel *LibreView maintenance*; the Mac's passive notification) | `status.json` `upcoming`, `warnHome_s`, `notify_s`; `MaintenanceNotice`, `LluSourceState.warnedS`; Mac `AppModel` |
| **The other way out, only where it makes sense**: *Read the sensor in Sukrara* beside *Change source* on home while your glucose comes from the Libre app with a Libre 3 / 3 Plus (`pt` 4) and no loop or pump app is installed (AAPS, CamAPS FX, Omnipod 5, Tandem t:slim / Mobi, MiniMed Mobile); never on the Mac | `MoveRules` (`:core:model`: docs/44 "The move"'s never-rules, for the later move suggestion too), `<queries>` in `:integrations` |
| **LibreView's own sign-in and upload**: 911 is *LibreView is down for maintenance*, waited out and retried, never *stopped* or a refused account | `Trouble.Maintenance`, `LibreViewClient`, `SignInResult.Maintenance` |
| **The source's description**: *nothing arrives while Abbott's LibreView is down, maintenance included* (Android, as the Mac's) | `ds_source_librelinkup_means*` |

| Discarded | Why |
|---|---|
| Quieting or delaying the no-data alert during maintenance | nobody, Sukrara included, has the glucose; the Libre app still alarms and Sukrara can't know whether the person wants its alarm (only the words change) |
| Saying maintenance from the status page alone | the page is the plan, the 911 the fact: tonight the servers were back at 22:38 while the page said *in progress* at 22:44 |
| The *Libre App* and *Libre Data Sharing API* groups | they list other regions' components (a Germany window names the sharing API's *United States*): a US account would be warned of Germany's window |
| A region from `/llu/config/country` or the account's country | the login's redirect already names the region the 911 comes from |
| A notification when maintenance begins | home, the notification's line and the card say it then; the no-data alert rings at 20 min as always |
| A setting to turn the heads-up off | the channel is the switch (Android's and macOS's own notification settings); one notification a window, a few a month at most |
| Warning on home days ahead | the line would sit under the glucose for days; Settings › Source has it as soon as it's listed, home from 12 h before |
| Suggesting the sensor during maintenance for someone else's glucose, an untested family, with a loop or pump app, or on the Mac | docs/44 "The move": a takeover would cut off the pump, and the Mac reads no sensor |
