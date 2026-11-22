# 47 — Sukrara's own Garmin apps: the watch app, its glance and complications, the data field

*2026-11-22 · investigation report for [#199](https://github.com/mauriciabad/sukrara/issues/199) (with #220, #221,
#239, #240) · desk research: Garmin's docs and the installed Connect IQ SDK 9.3.0 (device files, samples), public
sources of other Garmin CGM apps, Garmin forums · builds on [08](08-garmin.md), [42](42-watch-faces.md),
[43](43-garmin-link.md), [46](46-sugar-sense.md)*

## Goal

Beside Sukrara Aura, Sukrara's own Connect IQ apps (#199): see glucose at a glance and in a full app (card, chart),
during an activity (a data field), and in other watch faces (complications); log carbs, rapid and long insulin from
the wrist with the phone's quiet rules; a low felt on the wrist with the phone in a bag; as many watches as possible
from the first version. The look is designed with the owner in its own rounds (`contracts/watchface/app/SPEC.md`, its
reference `tools/watchface/app.js`); this report is the platform and the architecture under it.

## Choice

| | Decision | Why (sections below) |
|---|---|---|
| Apps | **Sukrara** (a `watch-app`: glance + app + background + 4 complications) and **Sukrara data field** (a `datafield`, its own store listing), beside **Sukrara Aura** | A data field can't be part of another app, nor read its complications or storage (§2) |
| Code | `apps/garmin/{aura,app,field}` share `apps/garmin/shared` through jungle source paths (no barrel) | Compiled into each app like its own code, no runtime cost; a barrel adds a manifest and product list to keep in sync (§2) |
| Data | Every app gets its own push (its UUID) and its own 5-min fetch, as Aura does (docs/43) | Storage is per app and data fields can't subscribe to complications (§2) |
| Payload | One versioned contract (`contracts/watchface/payload`, v4) with a **profile per app**: Aura 3 h of history (its 128 KB is nearly full, #217), the app 24 h plus the treatments in it, the metrics, the amounts to log, what is ringing; the data field 3 h and what is ringing | Memory per app type (§4): face 128 KB, app 768 KB, glance and background 64 KB, data field 128–256 KB |
| Writes (logging) | `POST http://127.0.0.1:17582/sukrara/entries` through Garmin Connect's proxy; the watch's own id per entry kept in an outbox until the phone answers with it; the phone stores by id (never twice) and runs a phone entry's rules; each write signed (HMAC-SHA256) with a key only the push carries | `Communications.transmit` is the path Garmin Connect's open bug loses (CIQQA-4631, docs/43) and has no end-to-end answer; the loopback port is open to every app on the phone (§6) |
| Wrist alerts | The phone's own alert notification (Garmin Connect mirrors it) stays the alert on every watch; the app adds what the phone says is ringing (5.1+ `showNotification`, vibration in the app and the data field) as the design decides | A face or background can't vibrate; `showNotification`'s vibration varies by watch (§5) |
| Watches | Every watch with API 5 or newer (round AMOLED 360–466, round MIP 218–280, Venu Sq 2, Venu X1) from the first version; Instinct 3 Solar / E (1-bit, 32 KB) later; nothing below API 5 | §9: API ≤ 3.4 has no complications and 32 KB glances |
| Phone side | Sukrara's own link (`ConnectIq`, no Garmin library) to every installed Sukrara app on every watch; `registerApp` (watch → phone messages) not used | F-Droid (docs/43); writes don't need it |

## Plan

1. **Ground**: this report; #239 (the treatment field by the card's rule).
2. **Contract**: payload v4 with the profiles, the entries endpoint (request, answer, signing, dedupe) and their cases.
3. **Watch**: `apps/garmin` split into `aura`, `app`, `field`, `shared` (build, CI, install, release); the app and the
   data field on the shared link with a placeholder look (one input model per surface, `layout(input) → model`,
   `draw(model)`, so the design's port replaces only the drawing); logging with its outbox; complications; alerts.
4. **Phone**: the profiles, a push per installed app, the fetch per app, the entries endpoint, the Garmin page's
   *Sukrara on your watch* rows.
5. **Design port**: the SPEC's surfaces, every screen class; Aura on the other screen sizes (#220) and its top fields
   (#240).
6. **Proof and words**: simulator tests per app and screen class, the owner's watch and phone, README, FEATURES.md,
   QA.md, the website (Garmin and Features pages, en / es), the store listings ([20](20-publishing.md)).

## Platform

**Marks:** **C** = checked in Garmin's docs, the installed SDK or a real app's source · **R** = reported (forum or third party, not confirmed) · **?** = unknown.
**Paths:** `SDK/` = `~/Library/Application Support/Garmin/ConnectIQ/Sdks/connectiq-sdk-mac-9.3.0-2026-10-20-95888f262/` (its `doc/` is the offline copy of [developer.garmin.com/connect-iq](https://developer.garmin.com/connect-iq/)); `DEV/` = `~/Library/Application Support/Garmin/ConnectIQ/Devices/<id>/{compiler,simulator}.json`.

### 1. SDK and API level now

| Fact | Mark | Source |
|---|---|---|
| Newest SDK **9.3.0** (2026-10-20). No 9.4 or System 10 beta announced | C | `SDK/bin/version.txt`; [SDK page](https://developer.garmin.com/connect-iq/sdk/), [Homebrew cask](https://formulae.brew.sh/cask/connectiq) |
| Newest **API level 6.0.3** (the `api.db` header says 6.0.4). SDK 9.1 = API 6.0.0, 9.2 = 6.0.2, 9.3 = 6.0.3; SDK 8.x = API 5.1–5.2 | C | `SDK/bin/api.db` line 1; "Since" tags in `SDK/doc/Toybox/*`; `SDK/doc/docs/Readme/History.html` |
| The newest watches (fēnix 8 / 9, FR 570 / 970, Venu 4, vívoactive 6, Instinct 3, MARQ 3, Venu X1, enduro 3 / 4) report CIQ **6.0.2–6.0.3**. fēnix 7, epix 2, FR 165 / 255 / 265 / 955 / 965, Venu 3: **5.2**. Descent Mk3, Approach S50 / S70, Descent G2: **5.1**. Venu 2 family, Venu Sq 2, D2 Air X10: **5.0** | C | `DEV/*/compiler.json` `partNumbers[].connectIQVersion` (table in §9) |

What's new since 8.x that matters here:

| API | What | Mark | Source |
|---|---|---|---|
| 5.1.0 | `Toybox.Notifications.showNotification` (title, subtitle, body, icon, action buttons, works from the **background**), `registerForNotificationMessages`. 9.1 added notifications with no actions | C | `SDK/doc/docs/Core_Topics/Notifications.html`, `SDK/samples/Notifications/source/BackgroundDelegate.mc`, History 9.1.0 |
| 5.1.0 | The native watch-face editor (fēnix 8 and newer): `<watchface-config>`, a complication slot with `allowAny="true"` takes **any Connect IQ complication**; `WatchFaceDelegate.onTap` | C | `SDK/doc/docs/Core_Topics/Editing_Watch_Faces_On_Device.html` |
| 5.2.0 | `WatchUi.configureTouchEvents` (watch apps can turn touch off), `ActivityPrompts`, `DataField.setWorkout/routeTo` | C | `SDK/doc/Toybox/WatchUi.html`, History 8.3–8.4 |
| 6.0.0 | `Communications.registerForPhoneAppMessageErrors` (a phone message that couldn't be received), `transmit(ByteArray)`, `UserProfile.upcomingSleepTime/WakeTime` (docs/43's night bug), log file 5 → 64 KiB | C | `SDK/doc/Toybox/Communications.html`, History 9.1.0 |
| 6.0.2 | Sleep score complication | C | History 9.2.0 |
| 6.0.3 | `makeWebRequest` `:body` (String / ByteArray) with any Content-Type string; responses may be ByteArray; `enableConnection` (LTE / Wi-Fi); `Audio` (microphone, speaker), `String.split/join`, `Math.min/max/abs` | C | `SDK/doc/Toybox/Communications.html`; [Garmin Rumors](https://garminrumors.com/connect-iq-sdk-9-3-microphone-speaker-lte/) |
| Health-specific APIs | None new for glucose; Garmin has no native glucose complication type or CGM API for Connect IQ (the complication enum ends at sleep score) | C | `SDK/doc/Toybox/Complications.html` |

### 2. App types and packaging

| Question | Answer | Mark | Source |
|---|---|---|---|
| One app = watch app + glance + complication publisher + background? | **Yes.** A `watch-app` (on CIQ 4+ a "super app") can implement `getGlanceView()`, `getServiceDelegate()` and publish complications. On CIQ 4+ devices a `widget` is compiled as a device app; it only appears in the glance list if it implements `getGlanceView()` | C / R | `SDK/doc/docs/Connect_IQ_Basics/App_Types.html`, `Core_Topics/Glances.html`; [forum: super apps](https://forums.garmin.com/developer/connect-iq/f/discussion/245387/super-apps-and-widgets-in-ciq-4-0), [glance bug report](https://forums.garmin.com/developer/connect-iq/i/bug-reports/when-app-type-is-widget-in-manifest-xml-and-getglanceview-is-not-overridden-compiler-should-automatically-provide-default-glance-view-for-ciq-4-devices-for-max-portability) |
| Data field separate? | **Yes**: the manifest has one `type`; a data field is its own app (own UUID, own .iq, own store listing, own permissions). Sugar Sense ships 3 listings (widget, face, field), buessow's AAPS set 3 projects, CGM+ a separate "CGM+ Datafield" | C | `SDK/doc/docs/Core_Topics/Manifest_and_Permissions.html`; [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/375348/is-it-possible-to-make-an-app-support-multiple-app-types/1791225); docs/46 l.126; [CGM+ FAQ](https://watchface.io/docs/cgm/faq) |
| Sharing data on the watch | `Application.Storage` is "only available to the application"; no inter-app storage ("Garmin nixed the idea due to security concerns"). **Complications are the only on-watch channel**, and only to **watch faces**: `Toybox.Complications` is not available to data fields at all (no subscribe, no publish). So the data field must get its own data from the phone (push to its own UUID + its own 5-min fetch) | C / R | `SDK/doc/docs/Core_Topics/Persisting_Data.html`; App_Types table (Complications: Watch Face, App, ACP; not Data Field, not Widget); [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/375348/is-it-possible-to-make-an-app-support-multiple-app-types/1791225) |
| Phone side, several apps | Each app UUID is its own mailbox; the phone pushes to each installed UUID. "Multiple companion apps cannot be registered to receive messages from the same ConnectIQ application" | C | `SDK/doc/docs/Core_Topics/Mobile_SDK_for_Android.html` |
| Code sharing: jungle `sourcePath` | `base.sourcePath = source;../shared/source`, `base.resourcePath = resources;../shared/resources`, `excludeAnnotations` per app / device. Compiled into each app like its own source: no runtime or memory overhead, dead code still costs PRG space unless excluded; `(:background)` / `(:glance)` annotations work as usual. buessow/garmin does exactly this for GlucoseWidget / GlucoseDataField / GlucoseWatchFace (`../Shared/source`) | C | `SDK/doc/docs/Core_Topics/Build_Configuration.html`, `Reference_Guides/Jungle_Reference.html`; [buessow GlucoseWidget/monkey.jungle](https://github.com/buessow/garmin/blob/main/GlucoseWidget/monkey.jungle) |
| Code sharing: barrels | A barrel is a module with its own manifest, version, products and permissions, consumed as `.barrel` or by jungle link; "all files … must live in the Barrel project's root directory, importing files external to the project is not supported"; resources namespaced (`@Lib.Drawables.x`); compiler bugs around background / glance resources in barrels were fixed only in 9.2. Store: nothing against either | C | `SDK/doc/docs/Core_Topics/Shareable_Libraries.html`, History 9.2.0 |
| **Recommendation** | **Jungle `sourcePath`** to one `apps/garmin/shared/` (link, payload decoder, words, settings spec, glucose formatting), plus `excludeAnnotations` per app. Barrels add a second manifest / product list to keep in sync and an extra module prefix, and buy nothing in a single repo | — | — |

### 3. Complications

| Topic | Fact | Mark | Source |
|---|---|---|---|
| Who can publish | Device apps and audio content providers, **up to 4** complications, id 0–255 (keep stable), permission `ComplicationPublisher`. Only **watch faces** subscribe (`ComplicationSubscriber`); Face It is a consumer too | C | `SDK/doc/docs/Core_Topics/Complications.html` |
| Resources | `<complication id access=public|protected|private longLabel shortLabel icon glancePreview>` + required `<faceIt defaultText>` + optional `<range><value>…`. Icon: **SVG** for public / protected, **fixed** (can't change at runtime). `glancePreview="true"` on one complication = the value shown when the glance is in a glance folder. Device icon size e.g. 45×45 (`complicationIcon` in compiler.json) | C | same; `DEV/fenix847mm/compiler.json` |
| Value | `updateComplication(index, {:value, :shortLabel, :unit, :ranges})`; value String / Number / Float / Long / Double / null; unit a `UNIT_*` (converted by subscriber) or a **string** (shown as is); ranges an array of ≥ 3 numbers; omitted keys keep the previous value. Garmin's advice: Latin characters only (arrows → ASCII, as faBolus does `^`, `v`, `->`) | C | `SDK/doc/Toybox/Complications.html`, core topic; [faBolus BgComplication.mc](https://github.com/faBolus-app/faBolusGarmin/blob/main/source/app/BgComplication.mc) |
| Shortlabel | "A five-character string" for radial display | C | core topic |
| From background | Works; the common pattern (weather widgets, faBolus) publishes in the background service (every 5–15 min, or on a phone message). Publishing only from `onBackgroundData` makes faces update only after the app opens | R | [forum 371220](https://forums.garmin.com/developer/connect-iq/f/discussion/371220/background-task-for-complication-glance-preview), [forum 349460](https://forums.garmin.com/developer/connect-iq/f/discussion/349460/stupid-complications-questions), [forum 317937](https://forums.garmin.com/developer/connect-iq/f/discussion/317937/made-some-progress-on-publishing-complications/1547572) |
| From foreground | Mixed: buessow's GlucoseWidget publishes from its glance / app process ([Messenger.mc](https://github.com/buessow/garmin/blob/main/GlucoseWidget/source/Messenger.mc)); one dev reports an uncatchable "Illegal Access (Out of Bounds)" in the simulator ([PR](https://github.com/eugene8080/claude-usage-widget/pull/12)); one open thread with a custom complication never reaching faces after CIQ 7 ([forum 378515](https://forums.garmin.com/developer/connect-iq/f/discussion/378515/has-something-changed-in-a-recent-ciq-regarding-passing-custom-complications-to-watch-faces/1805117)). Publish from both, guarded, and test on device | R | as cited |
| Rate limit | None documented; background is the limit in practice (≥ 5 min temporal; a phone message wakes it at once, ~1/min seen in docs/43) | ? | — |
| Subscribing | `getComplications()` iterator → match `getType() == COMPLICATION_TYPE_INVALID` (a CIQ one) and `longLabel`; `subscribeToUpdates(id)`, callback; re-subscribe on every start; `ComplicationNotFoundException` when the publisher is uninstalled. Ids compare with `.equals()`, and storing an `Id` in Storage fails (store the long label) | C / R | core topic; [forum 317937](https://forums.garmin.com/developer/connect-iq/f/discussion/317937/made-some-progress-on-publishing-complications/1547572) |
| Long press | Face: `WatchFaceDelegate.onPress` (API 4.2, touch-and-hold, touch watches) → `Complications.exitTo(id)` → the publisher starts with `:launchedFromComplication = <complication index>` in `onStart`'s state. So **the app can open a specific view per complication** (index 3 → the log screen). Button-only watches: no `onPress`; on fēnix 8+ the native editor may offer hold-to-launch (?) | C | core topic "Hold to Launch"; `SDK/doc/Toybox/Application/AppBase.html` (`:launchedFromComplication`); `WatchFaceDelegate.html` (onPress 4.2.0, onTap 5.1.0) |
| Devices that can publish | Every watch with API ≥ 4.2: all API 5 / 6 watches incl. Instinct 3 Solar / E (all have `complicationIcon`); none of fēnix 6 / FR 245 / 945 / MARQ gen 1 / Instinct 2 / Venu 1 (API ≤ 3.4); Edge: no | C | `DEV/*/compiler.json` |
| **CGM+ GB** (how others subscribe) | 3 complications found by long label: `CGM-GB-value` (short `CGM`; value string, unit string mg/dL or mmol/L, ranges null), `CGM-GB-data` (short `D-DATA`; ranges = floats [unit 0/1, trend 0–9, low, high, very low, very high, sample time, "old" time, "too old" time]; value with "-" = old), `CGM-GB-graph` (short `D-GRAPH`; ranges = 288 floats, 5 min apart, newest first, 0 = no data). Faces "must use" the old / too-old times | C | [watchface.io/docs/cgm/developer](https://watchface.io/docs/cgm/developer) |
| Others | Dexcom's widget is usable in Face It ([Garmin Dexcom CIQ](https://www.garmin.com/en-US/c/who-we-work-with/dexcom-ciq/)); faBolus publishes glucose (id 0) + 3 slots, a stale value as no number and label `--`; buessow publishes value + `" mg/dL"` | C | sources cited |

### 4. Memory and limits

Memory per app type (KB, `DEV/*/compiler.json` `appTypes[].memoryLimit`), **C**:

| Class (example) | Face | App | Glance | Background | Data field | Glance live? |
|---|---|---|---|---|---|---|
| API 6 AMOLED (fēnix 8 47, FR 970, MARQ 3, Instinct 3 AMOLED) | 128 | 768 | 64 | 64 | **128** | yes |
| API 5.x AMOLED (Descent Mk3, FR 965, Venu 3, vívoactive 5, epix 2) and Venu 4 / vívoactive 6 / FR 570 / 170 | 128 | 768 | 64 | 64 | 256 | yes |
| API 5.2 MIP (fēnix 7, FR 955; FR 255 app 512) | 128 | 768 | 64 | 64 | 256 | yes |
| API 6 MIP (fēnix 8 Solar, enduro 3 / 4) | 128 | 768 | 64 | 64 | 128 | yes |
| Instinct 3 Solar / Instinct E (mono 176 / 166) | 64 | 128 | 32 | 32 | **32** | yes |
| API 3.4 (fēnix 6, Instinct 2) for reference | 64–128 | 96–1280 | 32 | 32 | 32–128 | partly |

| Limit | Value | Mark | Source |
|---|---|---|---|
| Background run time | Killed if it doesn't `exit` within **30 s**; may be killed any time for the foreground; temporal ≥ 5 min | C | `SDK/doc/docs/Core_Topics/Backgrounding.html` |
| Callbacks in background | Only the delegate function is guaranteed to finish; a `makeWebRequest` / `transmit` callback may never run | R | [forum 6086](https://forums.garmin.com/developer/connect-iq/f/discussion/6086/syncing-applications-when-doing-background-service-with-communications) and the bundled search summaries above |
| `Background.exit` data | ≈ **8 KB** (`ExitDataSizeLimitException`) | C | `SDK/doc/Toybox/Background.html` |
| Storage | **32 KB per value**; total per app `appStorageCapacity` = **10 MB** on API 5 / 6 watches (128–256 KB on old ones); background may write Storage since 3.2, `onStorageChanged` in the other process | C | `Core_Topics/Persisting_Data.html`, `Toybox/Application/Storage.html`, `DEV/*/simulator.json` |
| Phone → watch message | "No set limit … implicit limit based on the available memory" (staff); the receiving background has 32–64 KB; Sukrara's payload is ~0.5 KB | R (staff) | [forum 2524](https://forums.garmin.com/developer/connect-iq/f/discussion/2524/ble-data-transmission) |
| Watch → phone `transmit` | ≤ 3 transfers in flight (else queue-full error); < 500 B took ~7 s in one report | R | [forum 2444](https://forums.garmin.com/developer/connect-iq/f/discussion/2444/communications-transmit-queue-full) |
| `makeWebRequest` response | Must match its Content-Type; JSON must be an object (else **-400**); an error status with a text body comes back as -400; 9.1 enforces a max JSON size over BLE | C / R | `Toybox/Communications.html`; History 9.1.0; [forum 228037](https://forums.garmin.com/developer/connect-iq/f/discussion/228037/makewebrequest-responsecode-returns--400-on-device-but-works-in-sim) |
| Glance | Lifecycle: "live" (kept alive, `requestUpdate` works, < 1 Hz advised) or "background UI update" (run at most when shown and ≥ 30 s since the last; drawing cached). All API 5 / 6 watches report `liveUpdates: true` | C | `Core_Topics/Glances.html`; `DEV/*/simulator.json` `glance.liveUpdates` |

### 5. Alerts on the wrist

| Path | Can it vibrate? | Mark | Source |
|---|---|---|---|
| `Attention.vibrate` / `playTone` | App types: watch app, widget, glance, **data field**, ACP. **Not** watch face, **not background** ("You can only do vibrations in the foreground process"). Forerunners: no patterns, one duty cycle | C / R | App_Types table; `Toybox/Attention.html`; [forum 357743](https://forums.garmin.com/developer/connect-iq/f/discussion/357743/triggering-vibrations-in-the-background) |
| `Notifications.showNotification` from the background (API 5.1+) | Full-screen notification with actions, launches the app with `:launchedFromNotification`. **Vibration varies**: none on FR 955, sound and vibration on epix 2 Pro 51 mm; not in the watch's notification list; no history. Venu 3S didn't render one in one report. Not on Venu 2 family / Venu Sq 2 / D2 Air X10 (API 5.0) | C (API) / R (behaviour) | `Core_Topics/Notifications.html`; [forum 406092](https://forums.garmin.com/developer/connect-iq/f/discussion/406092/new-notifications-api); [Venu 3S bug](https://forums.garmin.com/developer/connect-iq/i/bug-reports/notifications-shownotification-renders-in-venu-3s-simulator-but-not-on-physical-venu-3s); faBolus uses it for phone-marked alerts ([manifest](https://github.com/faBolus-app/faBolusGarmin/blob/main/manifest.xml)) |
| `Background.requestApplicationWake(msg)` | A confirmation "open app?"; may be suppressed for lack of resources; ignored for faces; buzz inconsistent across devices (staff: no buzz would be an integration bug) | C / R | `Core_Topics/Notifications.html`; [forum 8141](https://forums.garmin.com/developer/connect-iq/f/discussion/8141/background-task-sending-alerts-notifications) |
| Phone asks the watch to open the app | Garmin Connect's `openApplication` (companion SDK; service transaction 6) shows a prompt on the watch; then the app's foreground can vibrate | C (API) / ? (behaviour without SDK) | `Core_Topics/Mobile_SDK_for_Android.html`; [IConnectIQService.java](https://github.com/dangphuc2470/NavigationIQ/blob/main/app/src/main/java/com/garmin/android/apps/connectmobile/connectiq/IConnectIQService.java) |
| Data field during an activity | `DataField.showAlert(DataFieldAlert)` (API 3.2, permission `DataFieldAlert`): full-page, auto-dismiss; **off by default**, the person must enable it under activity Settings › Alerts › Connect IQ. `Attention.vibrate` is allowed in data fields | C | `SDK/doc/docs/User_Experience_Guidelines/Data_Fields.html`, `Toybox/WatchUi/DataField.html` |
| Phone's own notifications (smart notifications) | Sukrara's alert notifications reach the watch if Garmin Connect's App Notifications allow Sukrara; vibration / tone per the watch's notification and Focus-mode settings; per-app control on the watch (Connected Apps); Android reply only for texts. Juggluco and Dexcom rely on this | C (manual) / R | [vívoactive 6 manual](https://www8.garmin.com/manuals/webhelp/GUID-8C2C402F-55AC-431F-9CF2-1442B89CE149/EN-US/GUID-043F879B-FC35-4B22-A025-DF91C157B13F.html); [Garmin FAQ](https://support.garmin.com/en-GB/?faq=TLeDN92ZU0AgN4df6HakwA); [Juggluco help](https://www.juggluco.nl/Jugglucohelp/settinghelp.html); [Healthline](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data) |
| Garmin Connect 5.18 regression | Phone DND was carried to the watch, notifications stopped vibrating; Garmin said it would revert | R | [Notebookcheck](https://www.notebookcheck.net/Garmin-users-flag-notifications-issue-after-new-Connect-app-update.1147906.0.html) |

What other CGM apps do: **Dexcom** (widget + data field) never alerts on the watch, phone notifications only ([Healthline](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data)). **CGM+**: "The app has no alerts" ([FAQ](https://watchface.io/docs/cgm/faq)). **Kerfstok** (Juggluco): a foreground watch app, `Attention.vibrate` while it runs, no background ([source](https://github.com/j-kaltes/Kerfstok/blob/master/source/misc.mc)); **Libre 3 Kerfstok** has low / high / signal-loss alarms that vibrate until a button or touch ([store](https://apps.garmin.com/apps/44d1b2ed-bb3d-4b14-a6cf-e52544207f6c), mechanism ?). **Sugar Sense**, **GGlance**: no alerts (docs/46; [gglance.app](https://gglance.app/)). **faBolus**: background `showNotification` for alerts the phone marks.

### 6. Watch → phone (writes)

**How a phone app without Garmin's SDK receives messages** (C, from public sources): call the service's `registerApp(IQApp, String action, String package)` (transaction `FIRST_CALL_TRANSACTION + 8`, declaration order `openStore, getConnectedDevices, getKnownDevices, getStatus, getApplicationInfo, openApplication, sendMessage, sendImage, registerApp, sendTransientMessage, getPartNumber`); Garmin Connect then broadcasts `action` to that package with extras `com.garmin.android.connectiq.EXTRA_REMOTE_DEVICE` (IQDevice parcel), `…EXTRA_APPLICATION_ID`, `…EXTRA_PAYLOAD` (byte[] in Monkey C's serialisation: Sukrara needs a reader beside `MonkeyC.kt`'s writer). The SDK uses action `com.garmin.android.connectiq.INCOMING_MESSAGE`; AAPS uses its own `app.aaps…ON_MESSAGE_<device>_<app>`; the receiver is exported. Sources: [AAPS GarminDeviceClient.kt](https://github.com/nightscout/AndroidAPS/blob/master/plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/garmin/GarminDeviceClient.kt), [NavigationIQ IConnectIQService.java](https://github.com/dangphuc2470/NavigationIQ/blob/main/app/src/main/java/com/garmin/android/apps/connectmobile/connectiq/IConnectIQService.java), [ConnectIQDeviceStrategy.java](https://github.com/dangphuc2470/NavigationIQ/blob/main/app/src/main/java/com/garmin/android/connectiq/ConnectIQDeviceStrategy.java). Gadgetbridge / watchdrip / Juggluco use the SDK or their own BLE (docs/43); not re-read here.

| | `Communications.transmit` | `makeWebRequest` to `http://127.0.0.1:<port>` |
|---|---|---|
| Where | Foreground of apps / widgets; background (callback not guaranteed); data fields since API 5.0 (C, App_Types footnote) | Same contexts (C); http to loopback allowed by Garmin Connect on Android only (docs/08, [forum 7433](https://forums.garmin.com/developer/connect-iq/f/discussion/7433/makewebrequest-fails-with-response-code--300-on-android-works-on-ios)) |
| Methods / body | Any serialisable object (+ ByteArray 6.0) | GET, POST, PUT, DELETE; params as query (GET) or form / JSON body (POST, PUT); raw `:body` + any Content-Type only on 6.0.3 (C, `Toybox/Communications.html`) |
| Success signal | `onComplete` = handed to Garmin Connect, **not** that the phone app got it (Juggluco: "SUCCESS … is not an acknowledgement", docs/43) | The phone's own HTTP answer: 200 + a JSON object that can echo the entry's id |
| Failure codes | `onError` only | -104 no BLE, -2 / -3 BLE timeouts, -300 timeout, -400 / -401 / -402 / -403 response problems, plus the server's status (C, `Toybox/Communications.html`); no queuing ([forum 245344](https://forums.garmin.com/developer/connect-iq/f/discussion/245344/makewebrequest-returns-responsecode--104)) |
| Known bugs | **CIQQA-4631**: Garmin Connect 5.27.3 accepts transmits and never delivers them, open since mid-2026 (docs/43) | "HTTP blocked after a while" (docs/08, CGM Gauge); GC 4.20 once broke local HTTP ([forum](https://forums.garmin.com/developer/connect-iq/f/discussion/228037/makewebrequest-responsecode-returns--400-on-device-but-works-in-sim)) |
| Phone side | `registerApp` (another reverse-engineered call, same agreement question as docs/43) + exported receiver | Sukrara's existing loopback server (17582) |
| Precedent | Kerfstok (numbers via transmit), Juggluco | **AAPS** carbs: `GET /carbs?carbs=N&key=…` on 127.0.0.1:28891, key checked on every endpoint ([GarminPlugin.kt](https://github.com/nightscout/AndroidAPS/blob/master/plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/garmin/GarminPlugin.kt), [buessow GlucoseServiceDelegate.mc](https://github.com/buessow/garmin/blob/main/Shared/source/GlucoseServiceDelegate.mc)) |

**More reliable for a write that must not be lost or doubled: `makeWebRequest` to loopback**, because it has an end-to-end answer from Sukrara itself and doesn't go through the broken transmit path; neither transport is exactly-once, so make it so above them:
- The watch gives each entry a random id (`Cryptography.randomBytes`, API 3.0) when the person confirms, keeps it in a Storage outbox, and sends `POST /garmin/entries` with id, kind, amount, entry time (the watch clock + the phone lead docs/43 already tracks).
- The phone stores by id (an insert of a known id answers 200 with the stored entry, never a second row) and runs the phone's normal treatment rules (quiet rules, the duplicate-insulin guard).
- The watch removes it from the outbox only on 200 with its id; it retries from the foreground at once, then from the background every 5 min (callbacks not guaranteed, so the outbox, not the callback, is the truth), and shows *Sent* / *Waiting for the phone* on the log screen and glance.
- **Authentication** (another app on the phone can reach 127.0.0.1 too): the phone puts a per-watch secret in the Connect IQ payload it already pushes (only Garmin Connect can deliver to the app's UUID; AAPS's `key` precedent), the watch signs each write with **HMAC-SHA256** (`Cryptography.HashBasedMessageAuthenticationCode`, API 3.0, C: `SDK/doc/Toybox/Cryptography/`) over id + fields + time, the phone rejects a bad MAC and an old time. docs/43 refused a secret for the *read* fetch (it must work while push is broken); a *write* can wait for one delivered payload, and an app that has never received one has no data to log against anyway.

### 7. Input on the watch

| Control | Status | Mark | Source |
|---|---|---|---|
| `WatchUi.Picker` + `PickerFactory` | The generic picker (API 1.2), any values, works with buttons and touch; the right base for "units" and "grams" | C | `SDK/doc/Toybox/WatchUi/Picker.html`, `SDK/samples/Picker` |
| `NumberPicker` | **Deprecated**, "may be removed after System 3", eight fixed modes only | C | `Toybox/WatchUi/NumberPicker.html` |
| `TextPicker` | 256 characters since 9.2 (a note) | C | History 9.2.0 |
| `Menu2` (+ `IconMenuItem`, toggles), `CustomMenu` | Lists; icons left / right | C | `Core_Topics/Native_Controls.html` |
| `ActionMenu` / `showActionMenu` (3.4) | Contextual actions on an "action view" (no icons); each product has a hint for how to open it | C | `Personality_Library/Action_Views.html` |
| `WatchUi.Confirmation` (1.0) | Yes / no; Garmin: "small amounts of friction to important decisions"; a delete confirmation must be built with personality selectors | C | `Personality_Library/Confirmations.html` |
| `showToast` (3.4) | Short text + icon, non-blocking (for "Sent", "Waiting for phone") | C | `Personality_Library/Toasts.html` |
| Button hints / personality selectors | `system_icon_*__hint_button_*` placed per product; excluded where the button doesn't exist | C | `Personality_Library/Input_Hints.html` |
| `configureTouchEvents` (5.2) | A watch app can disable touch (wet wrist, accidental taps) | C | `Toybox/WatchUi.html` |
| Glance | No input; selecting it launches the app with `:launchedFromGlance`; `GlanceViewDelegate.onGlanceEvent` exists "none for now"; on CIQ 4+ the app launched from the glance "is subject to the old restrictions of widgets" (time-out) | C / R | `Core_Topics/Glances.html`, `Toybox/WatchUi/GlanceViewDelegate.html`; [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/245387/super-apps-and-widgets-in-ciq-4-0) |
| Entry points | Activity list (apps), glance list (apps with glances since API 4.0), watch-face long press (complications), notification actions (5.1), `System.exitTo(Intent)` from another app (confirmation shown) | C | `UX_Guidelines/Entry_Points.html`, `Core_Topics/Intents.html`, `Toybox/System.html` |
| UX guidance | "Present key information quickly and with minimal interaction. Save deep dives for mobile"; the initial view is a call to action; page loops; keep text centred on round screens | C | `UX_Guidelines/Understanding_What_You_Are_Building.html`, `Connect_IQ_Basics/App_Types.html` |
| Hot keys / Controls menu to open a CIQ app | Hot keys exist (fēnix 8: System › Shortcuts); whether a CIQ app can be a target | ? | [Tom's Guide](https://www.tomsguide.com/wellness/smartwatches/8-hidden-garmin-watch-features-that-you-need-to-know-about), [fēnix 8 manual](https://www8.garmin.com/manuals/webhelp/GUID-EECCAC99-90D6-4AB1-9A3A-EC433D3365E2/EN-US/GUID-3A355D29-6245-4C45-A727-4CE60B3F9313.html) |

### 8. Data field

| Topic | Fact | Mark | Source |
|---|---|---|---|
| Simple vs complex | `SimpleDataField` (return a value from `compute`, native font and every layout) vs `DataField` (own drawing, must fit 1–4+ field layouts) | C | `Connect_IQ_Basics/App_Types.html` |
| `compute()` | Once per second with `Activity.Info` | C | `Toybox/WatchUi/DataField.html` |
| Obscurity | `getObscurityFlags()` (only valid in `onUpdate`): `OBSCURE_TOP/LEFT/RIGHT/BOTTOM` for round-screen cut-offs | C | same; `UX_Guidelines/Data_Fields.html` |
| Input | None ("reserved for the activity"); settings from the phone and, since 3.2, an on-device settings flow (Connect IQ settings in the activity menu) | C | `UX_Guidelines/Data_Fields.html`, `Entry_Points.html` |
| Background | Allowed (Background ✓ for data fields): temporal 5 min, phone-message event | C | App_Types table |
| Communications | In data fields since **API 5.0** (before: background only) | C | App_Types footnote |
| FIT | `createField` up to **16** metrics; RECORD (≤ 1/s, Garmin Connect area chart if `displayInChart`), LAP, SESSION; label, units, chart colour in `<fitContributions>`; **data fields ≤ 32 bytes per message** (apps 256); preview with `monkeygraph`. buessow records glucose as SINT16 mg/dL + FLOAT mmol/L (RECORD) and min / max (SESSION) | C | `Core_Topics/Activity_Recording.html`, `Toybox/ActivityRecording/Session.html`; [GlucoseFitContributor.mc](https://github.com/buessow/garmin/blob/main/GlucoseDataField/source/GlucoseFitContributor.mc) |
| Alerts | `showAlert` (off until the person enables it), `Attention.vibrate` | C | §5 |
| Memory | 128 KB (API 6), 256 KB (API 5.x), **32 KB** (Instinct 3 Solar / E) | C | §4 |

### 9. Devices

Every watch in SDK 9.3's device files with API ≥ 5.0, grouped by screen (`DEV/*`, **C**). All: complication publishing yes, glances yes (live), storage 10 MB. Touch: ✓ / buttons only: ✗.

| Class | Watches | API | Touch | Face / App / Glance / BG / DF (KB) |
|---|---|---|---|---|
| Round AMOLED 360 | FR 265S, Venu 2S | 5.0–5.2 | ✓ | 128 / 768 / 64 / 64 / 256 |
| Round AMOLED 390 (19) | Descent Mk3 43, Descent G2, Approach S50, S70 42, epix Pro 42, FR 70, 165(M), 170(M), 570 42, MARQ 2, MARQ 2 Aviator, Venu 3S, Venu 4 41, vívoactive 5, 6, **Instinct 3 AMOLED 45, Instinct Crossover AMOLED** | 5.1–6.0 | ✓ (Instinct ✗) | 128 / 768 / 64 / 64 / 256 (Instinct: DF 128) |
| Round AMOLED 416 (13) | fēnix 8 43, fēnix 9 43, fēnix 9 Pro 43, fēnix E, epix 2, epix Pro 47, FR 265, Venu 2, 2 Plus, D2 Air X10, D2 Mach 1, Approach S72 43, Instinct 3 AMOLED 50 | 5.0–6.0 | ✓ (Instinct ✗) | 128 / 768 / 64 / 64 / 128–256 |
| Round AMOLED 454 (16) | fēnix 8 47/51, fēnix 8 Pro, fēnix 9 47, 9 Pro 47, Descent Mk3 51, epix Pro 51, FR 965, 970, 570 47, Venu 3, Venu 4 45, MARQ 3, D2 Mach 2 (Pro), Approach S70 47, S72 47 | 5.1–6.0 | ✓ | 128 / 768 / 64 / 64 / 128–256 |
| Round AMOLED 466 | fēnix 9 Pro 51 | 6.0 | ✓ | 128 / 768 / 64 / 64 / 128 |
| Round MIP 64-colour 218 / 240 | FR 255S(M) (✗), fēnix 7S, 7S Pro | 5.2 | mixed | 128 / 512–768 / 64 / 64 / 256 |
| Round MIP 260 | fēnix 7, 7 Pro, FR 255(M), 955 (5.2); fēnix 8 Solar 47, 9 Pro Solar 47 (6.0) | 5.2–6.0 | mixed | 128 / 512–768 / 64 / 64 / 128–256 |
| Round MIP 280 | fēnix 7X, 7X Pro (5.2); enduro 3, 4, fēnix 8 Solar 51, 9 Pro Solar 51 (6.0) | 5.2–6.0 | ✓ | 128 / 768 / 64 / 64 / 128–256 |
| Rectangular AMOLED | Venu Sq 2 (Music) 320×360 (5.0); Venu X1 448×486 (6.0) | 5.0–6.0 | ✓ | 128 / 768 / 64 / 64 / 256 |
| Semi-octagon mono (1 bit) | Instinct 3 Solar 45/50, Instinct E 40/45 (176 / 166) | 6.0 | ✗ | **64 / 128 / 32 / 32 / 32** |

Leave out (API ≤ 3.4: no complications, 32 KB glance / background, most no `Notifications`): fēnix 5 / 6, FR 245 / 745 / 945 (LTE), MARQ gen 1, Instinct 2 / 2S / 2X / Crossover, Descent Mk1 / Mk2 / G1, enduro 1, Venu (gen 1), Venu Sq (1), vívoactive 3 / 4, D2 Air / Charlie / Delta, Approach S60 / S62, the Legacy editions; anything API 1–2. Edge / handhelds: not watches (the data field could later target Edge 1040+, API 6, as Dexcom does; no complications there).

Feature cut-offs inside the kept set (C): `Notifications` needs 5.1 (drops Venu 2 / 2 Plus / 2S, Venu Sq 2, D2 Air X10 to the phone-notification path); `makeWebRequest :body` needs 6.0.3 (use params-dict POST everywhere instead); native face editor / `onTap` 5.1 on fēnix 8+; `onPress` long-press needs touch.

### 10. Store

| Rule | Mark | Source |
|---|---|---|
| **Medical**: an app "intended for use in the diagnosis, cure, mitigation, treatment or prevention of disease" must bring regulatory clearance for its markets, **otherwise** its description, features and functionality must not indicate such use and be "for informational purposes only"; Garmin may still reject. Dexcom's own apps say "secondary display … passive monitoring only". Insulin logging and alerts need careful wording ("log", "reminder", never dosing advice) | C | `SDK/doc/docs/App_Review_Guidelines/Overview.html` §1c; [Garmin Dexcom CIQ](https://www.garmin.com/en-US/c/who-we-work-with/dexcom-ciq/) |
| Physical safety: Garmin may reject apps "that may create a false sense of security, such as 'safety awareness' apps" (relevant to a wrist low alert: say it doesn't replace the phone's) | C | same §1b |
| Performance: rejection for draining battery, frequent crashes, interfering with the activity experience | C | same §2 |
| Describe every feature, requirements and dependencies (Sukrara on the phone, Garmin Connect), the supported devices accurately; no claimed Garmin affiliation | C | same §4a |
| Privacy: GDPR responsibility when using Communications to collect personal data | C | `Core_Topics/Publishing_to_the_Store.html` |
| Several apps from one developer: no rule against it; each app type is its own submission (Sugar Sense, CGM+, Dexcom each list app + field separately); review ≤ 72 h | C | same; docs/46 |
| Permissions are asked at install ("permission must be requested from the user at the time of installation"); the app will list Background, Communications, ComplicationPublisher, Notifications (+ FitContributor, DataFieldAlert for the field) | C | `Core_Topics/Manifest_and_Permissions.html` |

## Open

To check on a device: publishing a complication from the app's foreground (the background is the reported way);
whether `showNotification` vibrates on the Descent Mk3; whether a data field's background gets the phone's messages
like an app's; whether a Connect IQ app can be a hot-key target; `exitTo` on button-only watches. The agreement
question of docs/43 stands for the new apps.

## In Sukrara

Comes with each step of the plan.

**Steps 2 and 4: the contract and the phone** (2026-11-22). Added:
- `contracts/watchface/payload`, version 4: `apps.json`, the one registry of the three apps (app id, a fixed beta id
  each, profile, fetch path), held to the phone (`GarminAppsTest`) and to the watch builds (`link_cases.py --check`:
  every existing manifest's id and `build.sh`'s beta id); the profiles (`aura` = version 3's keys; `app`; `field`) with
  `p` in every payload; `entries.json` and `mutes.json` for the writes.
- Android `:integrations` `garmin/`: `GarminApp`; `AuraPayload.of(app, inputs)` per profile; `AuraLink` pushes each
  installed app id (public or beta) on each watch its profile, with the retry and stuck rule per app (`AppLink`) and
  the watch line adding them up; the fetch per path; the port open while a watch has any Sukrara app; `WatchWrites`
  (`POST /sukrara/entries`, `POST /sukrara/mute`; `LocalHttpServer` takes a bounded POST body on listed paths);
  `WatchKey`; `GarminDisplay` (the display settings' one resolver); the Garmin page's three rows (en / es).
- `:feature:treatments` `TreatmentsModule.fromWatch` (the dialog's Save path with the watch's id, `TreatmentLog.add`'s
  `id`); `AppGraph` wires it and the alert runtime's `AlertControls` into the link.
- The face accepts version 4 with its profile (`AuraLink.mc`).

Changed from "Choice" / §6, and why:
- **The endpoint is `/sukrara/entries`** (§6 said `/garmin/entries` once): the fetch paths' prefix.
- **Every answer about a write is HTTP 200** with `{id, r}`: Garmin Connect turns other statuses into error codes and
  may drop the body (§4), so a refusal's reason (`clock` with the phone's time, `pen`) would be lost.
- **The watch's id is a UUID v4 and becomes the treatment's id**: contracts/treatments says the creating device makes
  the id, so the log itself is the dedupe (no second store of ids), and a deleted entry's id stays known.
- **One key per phone, not per watch**: only Garmin Connect delivers to the app's id, and with no per-watch revoke on
  the page a per-watch key buys nothing; the phone tries one key instead of each.
- **Every field a string, signed as sent** (`id|k|a|t|s|f`): a JSON number may come back from Garmin Connect's
  proxy written differently; strings sign the same on both sides.
- **The send time `s` is signed and new per try**, the entry time `t` separate: a replay is refused outside 5 min of
  the phone's clock (answer `clock` with `now`, so a watch clock that is off corrects itself), while an entry kept in
  the outbox for hours still logs at its own time (up to the dialog's 1439 minutes back).
- **`tx` as four lists and an 8-character id prefix**: a dictionary per entry with the full id put a heavy day (64
  entries) at 8.9 KB, over `Background.exit`'s ~8 KB; now under 6 KB (`theAppsDayFitsItsBackground`).
- **Time in range has six bands, not seven**: the phone's time in range has no extremely-high share (that limit only
  colours the line and the number, `Bands`); the watch gets the phone's six.
- **`ring` carries kind, side, `relaxed` and `less`** (whether *Remind less* would slow it: the SPEC shows the button
  only then); its value is `g` (the alert is about the newest reading).
- **`last` items carry their own time and mute end** (round 2): the watch draws the card's double ring
  (`LastTreatment.Info.left`) at every draw without the phone.
- **`log.order`** (round 2, the owner: "the order of the kinds must be dynamic, with ONE source of truth so phone and
  watch never drift"): the log list's order is THE kinds' order (`contracts/treatments/kind-order.json`, `KindOrder`
  on Android and the Mac), from the same facts the phone's Add treatment focuses from, so every dialog and the watch
  agree; the scattered focus decisions (the notification, the alert's button, the widgets, the pill, the pen notice,
  the Mac's own) are gone.
- **The watch as a place** (SPEC §11): `WatchDisplay` (`:core:graph`, each field null = Same as the app) over the
  app's `GraphConfig` in `GarminDisplay.resolve`, the one place; stored per watch by Garmin Connect's id
  (`WatchSettingsStore`, typed state `watches`, schema `apps.garmin.watch`, Android only: the Mac sends nothing to a
  watch). The fetch can't say which watch asks (Garmin Connect makes it), so the app's push carries `w` and the app
  sends it back as `&w=`; without it, the one watch with the app, else the app's values. The metrics' windows are
  every watch's together.
- **The watch's settings page** (Share with apps › Garmin › a watch's *Sukrara*; `:feature:settings`
  `WatchSettingsContent` / `WatchValueContent`, routed in the app as `GarminWatchRoute`): every value a row with *Same
  as the app*; simple ones in docs/38's place dialogs, compound ones on the app's own pages (`MetricContent`,
  `GraphMarksContent`, the colour order of `TirRingsContent`) bound to the watch's values under a *Same as the app*
  switch (read-only while on). Each metric follows on its own (`watchSlots` = three parts, an empty one Same as the
  app): one metric of its own doesn't freeze the other two. The app's `last` follows the watch's *Last treatment* as
  `else` did (off: no `last`); the face's treatment field stays its own. The store keeps each watch's name with its
  values, so a watch Garmin Connect no longer knows is listed and its values can be let go.
- **The Garmin page per watch**: one group per watch (two or more), each app's line from that watch's link
  (`AppLine.on`): the app's fetch counted per watch by `&w=`, Garmin Connect's delivery when it hasn't asked. The
  two-buzz line (SPEC §13 decided 2) is a question ("Two buzzes for one alert?"), never a plain "switch it off": 5.0
  watches can't ring on their own and some 5.x ones ring without vibrating.
- **The setup link** (SPEC §4.3): the watch app's *Open on phone* opens `https://sukrara.mauri.app/garmin/setup/`
  through Garmin Connect; Android claims `/garmin/setup` and `/es/garmin/setup` as App Links and opens the Garmin page
  (setup while it isn't done); the website has both pages for a phone without Sukrara. The site check now also reads
  the Garmin apps' Monkey C for the URLs they open.
- **The data field gets 3 h** (SPEC §9: its sparkline goes up to 3 h as the slot allows), 36 readings as the face.
- **`log.<kind>.q`**: what an entry logged now quiets (side and minutes), from the rules the phone's Add treatment
  says its quiet line from (`AlertHints.quiet`: nothing on a follower mirroring the wearer's alerts), so the watch's
  hold and *Logged* screens say *Highs quiet for 1 h* as the phone does (SPEC §5).
- **`rep.ok`**: the report card's goals met per period, one bit per `Goal`, so the watch holds no thresholds.
- **Undo from the watch** (`POST /sukrara/undo`, SPEC §6's *Undo*): by the entry's id, signed over `id|s` with the
  same key and send-time window; the phone runs its own delete (`TreatmentLog.delete`, home's), so sync, the Nightscout
  delete, LibreView / Tidepool retraction and the quiet rules follow without a path of their own. Only an entry stored
  from the watch: the record keeps `via = watch` (`TreatmentRecord.fromWatch`, an existing column, no migration);
  phone and watch ids are both UUID v4, so the id alone can't tell. Answers `done`, `gone` (already deleted: done),
  `unknown`, `foreign` (refused), `off`, plus `sig` / `clock` / `id` as for entries.
- **Mute from the watch** (the owner's addition): the alert runtime's own actions, with *Remind less* / *Remind as usual* (round 2). *Until turned off* is Mute all's
  only: a side's mute always ends on the phone (contracts/alerts SPEC, 1–720 min), so the watch doesn't offer it.
- **Display settings in `ds`, the Numbers page in `rep`** (the design's addition): resolved on the phone in one place
  for a watch's own values later (docs/38 places). There is no green-in-range setting on the phone (the face's is a
  watch setting), so none is sent. `rep` has every period the report offers (1 / 7 / 14 / 30 d): about 250 bytes.

Discarded, and why:
- `registerApp` and watch → phone messages: the broken transmit path (CIQQA-4631) and no end-to-end answer (§6).
- Keeping version 3 beside 4: no backwards compatibility (CLAUDE.md); the face reads 4.
- A secret for the fetch: docs/43's reason stands (it must work while push is broken); the writes carry the key.
- Renaming `AuraLink` / `AuraPayload` to app-neutral names: a rename across the module and docs with no behaviour
  change; left for when the watch apps land (done in version 5, below).

**Version 5: watch faces, a list** (2026-11-22, the owner: "Aura is one of many watchfaces sukrara will have"). Added:
- The face profile `face` at `GET /sukrara/face` (was `aura`, `/sukrara/aura`): the one profile of every watch face.
  `apps.json` lists Aura as one face; Android holds the faces as a list (`GarminApp.faces`, `WatchProfile`), pushes the
  profile to every registered face Garmin Connect says is installed, and the Garmin page has a *Watch faces* group.
  Watch side: `(:face)` in `shared/link` (Aura's own complications annotation became `(:complications)`), storage
  keys `link*`.
- Names: `GarminLink`, `WatchPayload`, `GarminStatus`, `GarminWatch`, `LinkReport`; logcat `Sukrara.Garmin`.
- The face profile is documented as open to any Connect IQ face (payload README *Other watch faces*, the website's
  developer page): read only, fetch only, while a watch has a Sukrara app. A fetch without Sukrara's report doesn't
  count as a Sukrara face on the page.

Discarded, and why:
- A query parameter naming which Sukrara face fetched: costs every face bytes (#217) for a case that needs two
  Sukrara faces on one watch; until then the faces' fetch counts for a face while no other Sukrara face may be there.
- Pushes to other faces: Garmin Connect delivers only to the app ids the phone names, so it needs a registration the
  person allows; not built. Proposal (#199): a face sends its app id in the fetch (`GET /sukrara/face?app=<uuid>`);
  the Garmin page lists an unknown id under *Watch faces* as *Another face asks for your glucose* with Allow / Ignore
  (TalkBack-readable, undone from the same row); an allowed id is stored (an Android-only schema setting), pushed the
  face profile like Sukrara's faces, keeps the port open by itself (no Sukrara app needed) and is named on the page.
  The developer page is [GARMIN-FACES.md](GARMIN-FACES.md).

**The app on a Descent Mk3 43: out of memory, the watchdog** (2026-11-22, #217). The owner's watch (firmware 28.04,
Connect IQ 6.0.4) logged both; reproduced in the simulator with a full day from `fake_phone.py` (glance mode,
*Background Events*), then measured with `System.getSystemStats` and the device file's `watchdogCount`:
- **The glance ran out of memory**: it has 61 248 B, its code, data and view take 48.5 KB before it draws; a full app
  payload is 12.9 KB in memory (24 h, 64 treatments, reports) and the glance held two (the one Garmin handed to
  `onBackgroundData` and the stored one `Link.accept` read and wrote back). Now the background keeps the payload and
  writes the glance's copy (`GlanceCopy`: what the glance draws, 3.4 KB), hands the foreground only what came
  (`kept`, `sent`, the reading's time) and the glance reads only its copy (`Link.use`): 58 KB at the end of a draw.
  `Link.accept` reads the stored payload's time alone (`linkSent`), so a background run holds one payload (46 KB used
  at the hand-off, 37 KB after). `MemoryTest` holds the copy, the hand-off and the glance's input to 9 KB and a run to
  its payload + 2 KB.
- **The watchdog stopped page 1** in the % column's smoothing: the layout did the chart, the profile over 261 levels
  and the rows in one callback, 200 000–240 000 of the simulator's 240 000 (a 24 h window over it); the simulator stops
  at the same line between 120 000 and 160 000, about where the watch's limit lies. Now the column is worked out
  once per payload and window in steps of under 25 000 (each its own 50 ms callback of the view's one timer:
  a watch allows few) and the line from a table of the scale: the layout is 40 000–50 000 (12 h) and 50 000–60 000
  (24 h), under the paint (60 000–75 000, unchanged). The first frame after opening shows no column; it appears
  within a few callbacks.
- **The glance again, from its second draw** (2026-11-22, #217): the watch logged *Out Of Memory* in `AppDraw.loadFont`
  every ~15 s. Measured in the simulator's glance (logged `getSystemStats` per step, then a ballast held through every
  draw: the free memory at the real peak, transients included): 2.4 KB of the glance's first draw stays (modules and
  classes loaded once), so later draws start at 50.6 KB of 61 248 B; three glyph tables (each a base64 string decoded
  into bytes, ~1.8 KB held + 1.5 KB while decoding) were held with the display list, 58.8 KB at the third. Now: glyph
  tables are two Numbers a glyph read as they load (`tools/fonts.py`), the glance holds one font at a time and drops
  each display-list element as it paints it, its copy goes before the layout, the copy keeps only what the glance draws
  (no null entries, the ring's four keys, the second metric only without `else`, one item a treatment step), the input
  reads only the link's status and sensor (`Link.inputs`' 20-entry dictionary was 1.3 KB), the palette and the label
  widths are no longer arrays and dictionaries kept in memory, and the app no longer builds `shared/plain` and
  `shared/input` (2 KB of code the glance loaded). 47.4 KB between draws; a 9 KB ballast passes every draw, 10 KB
  doesn't (before: 2–3 KB). `MemoryTest.memGlanceDraw` holds each step to its budget.

**Getting the apps going, from the phone** (2026-11-22, #199; the owner: a banner like LibreView's when a watch has an
app but the switch is off, a setup step, store links on the Garmin page and in About). Added:
- **Knowing with the switch off** (`GarminLink.detect`): Garmin Connect's Connect IQ service answers whatever
  Sukrara's switch says (the link already asked it for the watches every minute), so with the switch off the link
  asks only its app-info question, per app id and connected watch, at start and when a watch connects (cached 30 min
  as the push's own check). No message is sent and the port stays closed. A watch that isn't connected can't be
  asked, so it stays unknown.
- **Home's tip** `BannerKind.GarminOff` (*Your watch gets no glucose*, *Turn on*, close), under *Keep Sukrara up to
  date*. Closing is remembered per app (`GarminTip.keys`), so a newly installed app shows it again; turning sending off
  yourself closes it for the apps found then (as About's update switch closes the updates tip).
- **Setup's *Your Garmin watch*** (`SetupStep.Garmin`, optional, before *Bring your history back*, wearer and
  follower): offered while Garmin Connect is installed or a watch has an app; the switch, one line per app with *Get*
  or *On your watch*, *Continue* / *Skip* (final). An install whose walk went past it isn't sent back for it.
- **Store ids in the registry**: `apps.json` `store` (null), read by the phone (`GarminApp.store`, `ConnectIqStore`,
  `GarminAppsTest`) and the website (`CONNECT_IQ_STORE` replaced the placeholder `CONNECT_IQ_APP_ID`; the badge links
  only a published listing). The Garmin page's *Connect IQ store* group and About's rows show only apps with an id.
  #247 fills them in once Garmin publishes the listings.
- **A debug fake** (`GARMIN_FAKE`, `FakeGarminConnect`): a made-up connected watch with the given apps and stand-in
  store ids, for the emulator.

Discarded, and why:
- **Guessing the store URLs**: a listing's id is given when it is published and isn't the app id, so a guessed URL
  would be a broken link (#247 instead).
- **Opening the Garmin page from the tip**: one tap that turns sending on does what the person needs; the page is one
  row away in Settings, and the snackbar says where.
- **Asking Garmin Connect on every resume**: the question goes through Garmin Connect's service and its broadcasts;
  start and reconnects are when a new app can have appeared without the phone knowing.
- **A step that blocks setup until an app is installed**: installing happens in Garmin's store, on its own time;
  nothing waits for it.

After the owner's review of the screenshots (2026-11-23: "just Sukrara is confusing", the icons "completely
nonsense"): the phone names each app with its kind (*Sukrara Aura watch face*, *Sukrara watch app*, *Sukrara data
field*; es *Esfera Sukrara Aura*, *App de reloj Sukrara*, *Campo de datos Sukrara*; the watch and the store keep the
apps' own names), and shows Connect IQ's own symbols for watch faces, device apps and data fields, redrawn as vector
icons (`ds_ic_watch_face`, `ds_ic_device_app`, `ds_ic_data_field`) instead of Material's watch, grid and stopwatch.
Discarded: About's three Connect IQ store rows (*Sukrara for Garmin* is enough there; the Garmin page and setup keep
the store links).
After the owner's next review (2026-11-23): an app on no watch is a *Connect IQ store* row of its own on the Garmin
page, gone from the watch's groups (no app twice); it and setup's *Get* open the store's search for Sukrara
(`apps.garmin.com/<en-US|es-ES>/search?keyword=Sukrara`) while the listing's id is null, so *Coming soon* is gone
(#247 swaps in the listings). The watch-face icon's hands show 10:10, the data-field icon's middle line spans the band,
and About's *Sukrara for Mac* has an Apple logo (`ds_ic_apple`).
**The app's background on the owner's watch** (2026-11-22, #217). The phone's diag log showed the app's fetch missing
for hours after the crash fix's install while Aura's and the field's came every 5 min. Most of it was the watch on the
Mac's USB for installs and log reads (no app runs then): the fetches came back every 5 min once it was unplugged
(22:24Z). What was really wrong, from the app's own report in its fetch: background runs whose fetch never reached the
phone (21:21Z `-300` timeout, 22:13Z `-104` Bluetooth unavailable) while the face's got through; the phone's pushes
never handled by the app's background (every run in its report a fetch, unread messages in
`GARMIN/Apps/MAIL?/SUKRARA.MBX`, none for Aura or the field); and once `FAILURE_MESSAGE_TOO_LARGE`. The app's push is
the full day, ~3.8 KB serialized (2.8 KB JSON) against the face's 0.6 KB. Watch side, changed:
- **The background keeps itself registered**: a one-shot Moment (the reading's expiry) left nothing registered when
  the watch refused it (the old `catch`) or a run was missed. Now every run and every app start registers again what's
  missing: the phone's messages, and the next run with `Expiry.plan` (the expiry; refused, 5 min from now, which no
  past run is within; last the 5-minute Duration, which repeats by itself). The glance registers the Duration when
  nothing is registered or a Moment was missed (`LinkBackground.stale`). `slotsExpiryPlan`, `linkBackgroundStale`.
- **The phone's waiting messages are taken**: the open app registers for the phone's messages (those waiting come at
  once), each taken as a push; a temporal run lets the waiting ones go before its fetch (one at a time: the fetch
  brings the newest). A fetch Bluetooth couldn't send (`-1` … `-104`, not `-102`) is asked again at once; the app asks
  the phone when it opens with a reading older than 5 min.
- **One payload at a time in a run**: the stored payload is let go before the fetch (`Link.unload`): 41 KB at a run's
  peak in the simulator, was 48.5 KB, of 61 248 B.
- **The app's own log**: every entry point and registration (`Trace`, also in the release: `System.println`, written
  to `GARMIN/Apps/LOGS/SUKRARA.TXT` only where that file exists, a sideloaded watch).
Open: whether the watch hands the app's background its messages at all (the log will tell); the push's size, for which
the phone would push only what changes each minute and leave the full day to the fetch (a payload contract change).
**Wrist alerts only while the phone can't say them** (2026-11-23, #248; the owner: "I only want the garmin glucose
alerts to show up if the phone is locked and at the same time i get the phone notification. otherwise it makes alarm
fatigue"). Added:
- **The rule on the phone, in one place**: `AlertUiState.wrist(locked)` (`:core:model`) = the runtime's `shownKinds`
  (what rings with its notification up: posted or its stand-in, not swiped away, #195) while the phone is locked;
  `WatchPayload` puts only that in `ring` for the app and the data field. `PhoneLock` (`:integrations`) reads the lock
  as Android's notification cooldown counts *user present* (screen off or the lock screen up = locked; a phone without
  a lock screen counts with its screen off) on screen off / screen on / unlock, and the link's payload flow takes it:
  a lock or an unlock while an alert rings changes the payload and is pushed within the settle time (3 s); with
  nothing ringing it changes nothing and sends nothing. The runtime also publishes a swipe at once.
- **Contract**: alerts SPEC §1 "On the wrist" and the scenario format's `wrist` / `lock` / `unlock` / `swipe`
  (`scenarios/wrist.txt`, 9 timelines: locked with the notification, unlocked, swiped, two alerts one swiped, restart,
  Mute, Mute all, lock / unlock mid-alert, a follower's own lock); both drivers and the Android runtime run them (the
  runtime answers with its own `shownKinds`). Payload cases: the very low unlocked, the very low swiped (only falling
  fast on the wrist), the field's very high unlocked; app SPEC §7 / §9 / §13 decided 6.
- **Followers** follow the same rule: their runtime and their lock, their own notifications.

Discarded, and why:
- **A separate `buzz` flag beside `ring`**: the watch uses `ring` only to alert (Garmin's notification, the app's alert
  screen, the data field's alert); the value, its colour and the pill come from `g` and `st`. Gating `ring` keeps
  every screen right with no watch change and no new payload version, so a phone update alone is enough.
- **Keeping the wrist's "already said" across an unlock**: an alert locked again with its notification up is said
  again on the wrist (the owner's rule: both hold), which is also what tells a person who put the phone away unanswered.
