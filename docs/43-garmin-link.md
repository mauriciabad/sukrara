# 43 — Getting readings to Sukrara Aura on a Garmin

*2026-11-16 · investigation report · Descent Mk3i 43 mm (Connect IQ 5.1), Connect IQ SDK 9.3.0, companion SDK 2.4.0
(inspected, not used)*

## Goal

Sukrara Aura ([contracts/watchface/aura](../contracts/watchface/aura/SPEC.md)) must show what the phone's Sukrara
shows, a new reading within seconds when the watch is in reach (SPEC §13), right between messages (ages, countdowns,
"No phone"), with nothing to switch on first. Today's Garmin path ([08](08-garmin.md)) is a 5-minute poll of the
xDrip+ server through Garmin Connect.

## The person first

When the link fails in real life, and what they see:

| Situation | What happens to the data | What the person sees | How it fixes itself / they fix it |
|---|---|---|---|
| Phone in another room, out of Bluetooth range | Nothing arrives | The value ages from its own time: late (reading time shown) → struck and grey → *No reading since 10:09*; Garmin's *No phone* takes the first bottom field | Back in range: the phone sends the current reading on reconnect (checked every minute) |
| Garmin Connect stopped by battery saving (Samsung, Xiaomi; xDrip+ users report the same [#3060](https://github.com/NightscoutFoundation/xDrip/discussions/3060), [swissalpine FAQ](https://github.com/swissalpine/GARMIN-xDrip-Spike-Nightscout-Watchface)) | Neither path works | Same aging; phone: Share with apps › *Sukrara Aura* "Garmin Connect isn't answering · open it" | *Open Garmin Connect*; the footer says it must run in the background |
| Garmin Connect's messaging bug ([CIQQA-4631](https://forums.garmin.com/developer/connect-iq/i/bug-reports/gcm-5-27-3-android-accepts-communications-transmit-messages-from-watch-app-but-never-delivers-them-to-the-companion-app), acknowledged, open since mid-2026: messages silently vanish on some Garmin Connect / Android versions) | Push lost; the face's 5-minute fetch still works | The face lags up to 5 min (late → shows the reading time); phone: "Garmin Connect loses messages · the face asked 3 min ago, so it updates every 5 minutes"; after 15 min without one, "messages stopped getting through · restart the watch" | Short stalls end by themselves within 5 min (Garmin Connect resets its link); a stuck link (#242: a day) ended only when the watch reconnected, so the page asks for a watch restart; Sukrara tries every 5 min meanwhile |
| Bluetooth off on the phone | Nothing arrives | Aging + *No phone*; phone: "<watch> isn't connected" | Bluetooth on: current within a minute |
| Phone or watch restart | Watch: the stored payload survives (it ages right away); phone: Sukrara's service restarts | No blank face, an aging value | Next reading pushes (≤ 1 min), or the 5-minute fetch |
| Travel, time zones | Times are absolute epoch seconds: ages never jump; today's time in range ends with the *phone's* day | Correct ages; TIR disappears at the phone's midnight until the next message | — |
| Swim, dive, shower | Bluetooth doesn't cross water (docs/42 §1.4) | Aging, as expected; struck before it could mislead | Surfacing: current on reconnect |
| Another face selected | Only the active face's background runs; Garmin queues messages for it ([Pwb0186 notes](https://github.com/Pwb0186/aaps-garmin-push-watchface)) | — | The face takes the newest by `sent` and drops older ones (contract "Order") |
| Watch clock behind the phone | A too-young age would be unsafe | — | The face adds the phone's lead (`sent − now` on receipt) to its clock: never younger, only older |

What others do, and what people say:

- **AAPS** binds Garmin Connect's Connect IQ service itself (still with Garmin's AAR for the stubs), keeps one message
  in flight, retries up to 10 times and drops messages older than 30 s; its data path is HTTP on 127.0.0.1:28891, a
  pushed message mostly only wakes the watch's long poll ([GarminDeviceClient.kt](https://github.com/nightscout/AndroidAPS/blob/master/plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/garmin/GarminDeviceClient.kt),
  [GarminPlugin.kt](https://github.com/nightscout/AndroidAPS/blob/master/plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/garmin/GarminPlugin.kt));
  its author calls native messaging "usually unreliable but good enough" ([PR #3059](https://github.com/nightscout/AndroidAPS/pull/3059)).
- **Juggluco → Kerfstok** (a watch app, not a face) uses the SDK plus an experimental direct BLE transport it falls
  back to after 12 s without a reply: "the SDK's sendMessage SUCCESS callback by itself is not an acknowledgement"
  ([AllData.java](https://github.com/j-kaltes/Juggluco/blob/primary/Common/src/mobile/java/tk/glucodata/nums/AllData.java),
  [DirectGarminIQ.java](https://github.com/j-kaltes/Juggluco/blob/primary/Common/src/mobile/java/tk/glucodata/nums/DirectGarminIQ.java)).
- **xDrip+, GlucoDataHandler**: web server only ([Local_Web_Services.md](https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/technical/Local_Web_Services.md),
  [GDH](https://github.com/pachi81/GlucoDataHandler)). **CGM+**: "up to 10 minutes behind" ([FAQ](https://watchface.io/docs/cgm/faq)).
  **CGM Gauge**: Garmin Connect "blocks HTTP after a certain time… synchronize your watch" ([store text](https://apps.garmin.com/api/appsLibraryExternalServices/api/asw/apps/9a9d309f-37ee-454b-8a95-8170023e804e)).
- **Dexcom's official Garmin app** shows "NO DATA" whenever its phone path breaks ([forum](https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-ios/329932/dexcom-g7-not-working-with-existing-gc-dexcom-data-field-and-glance-app)).
  Good: it never shows an old value; bad: no reason, no way to fix it from the phone. Sukrara keeps the aging value
  (struck once stale) and says on the phone why.
- **A push-driven face works**: [buessow's GlucoseWatchFace](https://github.com/buessow/garmin/blob/main/GlucoseWatchFace/source/GlucoseWatchFaceApp.mc)
  registers `registerForPhoneAppMessageEvent`; the [Pwb0186 face](https://github.com/Pwb0186/aaps-garmin-push-watchface)
  reports "a few seconds" against up to 5 min, also sideloaded, with gotchas: delete before registering, messages
  pile up for an inactive face, no `Communications` in a face's foreground.

## Transports compared

| | Latency | Battery | Reliability | Setup | Notes |
|---|---|---|---|---|---|
| **Pull**: the face's background polls 127.0.0.1 via `makeWebRequest` | ≤ 5 min (temporal floor, [Background](https://developer.garmin.com/connect-iq/api-docs/Toybox/Background.html)) | One background run / 5 min | Good; Garmin Connect's "HTTP blocked after a while" bug | None (http only to loopback on Android, [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/7433/makewebrequest-fails-with-response-code--300-on-android-works-on-ios)) | Fails the "seconds" requirement alone |
| **Push**: a Connect IQ message wakes the face's background (`registerForPhoneAppMessageEvent`, 3.2.0) | Seconds | One background run per message (1/min) | Garmin Connect's open messaging bug; statuses not always sent | None; works for sideloaded faces (lookup by app id) | Background `exit` data ≤ ~8 KB; the payload is ~0.5 KB |
| **Direct BLE** | Seconds | Radio on both sides | Unproven: the API docs don't list `BluetoothLowEnergy` for faces ([API](https://developer.garmin.com/connect-iq/api-docs/Toybox/BluetoothLowEnergy.html)); the phone would be a GATT peripheral beside Garmin Connect's own link | Pairing | Juggluco's reverse-engineered path is a different (legacy app) protocol |
| **Hybrid**: push + 5-minute fetch | Seconds; ≤ 5 min when push breaks | Both, small | Two independent paths through Garmin Connect | None | Chosen |

Companion SDK: `com.garmin.connectiq:ciq-companion-app-sdk:2.4.0` (2026-03) is under Garmin's proprietary SDK licence
([POM](https://repo1.maven.org/maven2/com/garmin/connectiq/ciq-companion-app-sdk/2.4.0/ciq-companion-app-sdk-2.4.0.pom)):
F-Droid refuses it ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)) and a GPL app needs a linking
exception from its copyright holders; other free apps do ship it ([appendix](#appendix-the-companion-sdk-and-free-software)).
What it does is small: bind `com.garmin.android.apps.connectmobile/.connectiq.ConnectIQService`, call its interface by
transaction number with plain parcels, serialize the message in Monkey C's format, read the answers Garmin Connect
broadcasts back; in "tethered" mode the phone listens on 7381 and the simulator joins through `adb forward`.

## Choice

**Hybrid: push on every change, plus the face's own 5-minute fetch, both carrying the whole payload.** Push gives
seconds; the fetch covers Garmin Connect's messaging bug, lost messages, a face just selected, and proves on the phone
that the face runs (*The face asking this phone*). Both go through Garmin Connect (the only bridge), so a stopped
Garmin Connect stops both: the phone says so. Sukrara speaks to Garmin Connect itself, no Garmin library.

Retry: a message Garmin Connect fails or never confirms (20 s) is sent again after 15 s, 30 s, 60 s, then every 2 min,
until a newer payload replaces it; a watch that reconnects gets the current payload at once. One message at a time.

The payload is a versioned contract: [contracts/watchface/payload](../contracts/watchface/payload/README.md).

## Proof

- The Monkey C writer was checked against Garmin's own serializer (the 2.4.0 AAR, in a throwaway test, never
  committed): every value decodes the same; `cases.json` keeps a payload as Garmin's bytes for every reader to decode.
- Phone → payload: `AuraPayloadFixtureTest` (16 cases). Link behaviour: `AuraLinkTest`, `AuraStatusTest`.
- The face: `LinkTest` runs every case on the watch side in the simulator (`apps/garmin/tools/link-test.sh`, 3 tests
  pass).
- End to end, 2026-11-16: the debug build on an emulator (`GARMIN_SIM`, synthetic readings) → `adb forward` → the
  Connect IQ simulator (descentmk343mm, *adb Connection › Start*): a throwaway TEST face (its own app id, times and
  counts only) received each minute's push (version 1, 10 keys, 12 history slots) and, on a temporal event, fetched the
  same payload from 127.0.0.1:17582 (HTTP 200); Sukrara Aura itself stored the pushed payload. The simulator's fetch
  needs *Use Device HTTPS Requirements* off (its default mimics a phone that allows only https); Garmin Connect on
  Android allows http to loopback ([08](08-garmin.md)).
- The owner's phone and watch, 2026-11-16 (Garmin Connect 5.31, Android 17):
  - **Push reaches the watch**: Garmin Connect confirmed every message 1–3 s after its reading (14:18–14:27, one or
    two a minute), and the face showed a value after each reconnect.
  - **Disconnections stop everything**, as designed: the watch on the Mac's USB (13:52–14:09, the phone link drops) and
    a Bluetooth toggle (14:11–14:19: the watch only came back when its Phone control was toggled) left the face aging
    from 13:52, then 14:19.
  - **The face applied the readings late**: at 14:21:58 it still showed 14:19 though 14:20 and 14:21 were confirmed
    delivered. The first guess, from forum reports, was a 5-minute limit on a face's background
    ([staff](https://forums.garmin.com/developer/connect-iq/f/discussion/6086/syncing-applications-when-doing-background-service-with-communications),
    [AAPS push face](https://github.com/Pwb0186/aaps-garmin-push-watchface)). **The face's report disproved it**
    (14:36–14:43): the background ran within 0–3 s of every delivered message, once a minute, but each run got the
    **oldest** waiting message: the three messages queued while the face was being installed stayed queued, so every
    push gave the face the reading from 3 minutes before (runs at 14:39:47, 14:40:46, 14:41:51 got the payloads built
    at 14:37:42, 14:38:22, 14:38:43). The 5-minute fetch (14:38:40, 14:43:41) brought the newest each time, which the
    face kept over the older pushes. Fix: each push run takes every waiting message
    (`Communications.registerForPhoneAppMessages`, called at once per waiting message) and hands the face the newest.
  - **With the fix (52dfb22fc, 14:56–15:01)**: the first run after the install took the 3 waiting messages with it
    (`p3`) and kept the newest; from then on every run got the message just sent, **1–7 s after the phone built it**
    (14:56:48 → :50, 14:57:49 → :56, 14:58:49 → :50, 14:59:50 → :52, 15:00:52 → :53). The phone builds it 3–4 s after
    the reading (it waits for the payload to settle), so **a reading reaches the watch in about 5–11 s**.
  - The phone sent a second message a couple of seconds after each reading (the payload changing in two steps): gone,
    one message per reading.
  - Not done on the device: the Bluetooth-off test (turning the phone's Bluetooth off from the agent was refused by
    its permission check); the two unplanned disconnections above showed the aging and the page's "isn't connected",
    but also that the watch may need its *Phone* control toggled to come back after the phone's Bluetooth returns
    (Garmin's link, not Sukrara's).
- The owner's phone and watch, 2026-11-20 → 22 ([#242](https://github.com/mauriciabad/sukrara/issues/242), Garmin
  Connect 5.31.1, Android 17; Sukrara's diag log, Garmin Connect's own logcat captures and the phone's battery history,
  read with root):
  - **Push works, and Garmin Connect's link to the watch stalls now and then**: delivered about once a minute all day
    (the face got 520 in 542 min), with two short stalls that ended when Garmin Connect reset its Bluetooth link
    (14:05, 16:23).
  - **Once the link stayed stuck for 26 h**: on 11-20 at 18:30 the link went silent (fetches too); Garmin Connect's
    reset at 18:41 didn't recover it: `Failed` ×476, then `NoAnswer` ×3,010, the link timing out (GATT 147) again and
    again, mostly at the face's fetch times. The fetches kept the face within 5 min; only 38 pushes reached it, each
    13–90 s after a reconnect. It ended when the watch went on USB and came back (11-21 23:57: every push delivered).
  - Not the cause: the payload (452 bytes, the same shape delivered 500+ times), Garmin Connect's update (pushes worked
    under it all day), waking the watch (2 of 228 wrist raises in the stuck stretch were followed by a push). The
    stuck stretch began 20 s after the phone entered deep Doze, but pushes had worked through 4 h of deep Doze that
    day (Garmin Connect and Sukrara are exempt from battery optimisation): a possible trigger, not shown.
  - Changed: after 15 min without a delivery the watch's line says "messages stopped getting through · restart the
    watch", and Sukrara tries every 5 min instead of about twice a minute (at once on *Send now* or a reconnect); the
    log names Garmin's status for a failure (`Failed FAILURE_DURING_TRANSFER`).
- The owner's watch, 2026-11-22 01:05–01:24 ([#244](https://github.com/mauriciabad/sukrara/issues/244)): after a
  watch restart the face got nothing from its background for 16 min, although the watch took every push (Garmin
  Connect's log: every chunk acknowledged) and the background kept fetching. Garmin didn't call `onBackgroundData`;
  no crash (no new `CIQ_LOG.YML`). It ended when the face started again (back from the watch's menus). Not Sleep Mode,
  Do Not Disturb or Battery Saver (each on at other times with the face updating). Changed: every background run also
  leaves its result in the face's storage (`auraHandoff`, Connect IQ 3.2+), which the face picks up at every draw
  (runs marked `s` in the report); the phone's line shows the face's own reading when its report stays behind a
  delivery ("the watch face isn't taking new readings · restart the watch").
- **Seeing the watch without looking at it**: Garmin's ways are `System.println` to `GARMIN/APPS/LOGS/<app>.TXT`
  (when that file exists) and crash reports in `GARMIN/APPS/LOGS/CIQ_LOG.YML`
  ([FAQ](https://forums.garmin.com/developer/connect-iq/w/wiki/4/new-developer-faq),
  [logging tips](https://github.com/blackdogit/connectiq-apps/wiki/Tips:-Logging)), both read over USB, which drops
  the phone link the check is about. Chosen instead: the face reports with its fetch what it got (each push and
  fetch, the watch's time, which payload) and what it draws; the phone shows it (*On the watch*) and logs it (logcat
  `Sukrara.Aura`). It costs nothing extra on the watch and is useful to the person too. The USB logs stay for crashes.
- **The screen** (2026-11-16, the owner: "when I raise the wrist the brightness is down… open any menu, it increases…
  back to the watchface it decreases again"): the report also carries the face's last 12 screen events, after the
  deliveries in `r` and in their shape, `<event>.<watch time>.<display mode>.0`: `S` onShow, `X` onExitSleep (wrist
  raise, button), `E` onEnterSleep, `C` onDisplayModeChanged, and `Da` / `Dn` / `Dw` the first draw in always-on /
  night / awake; the display mode is `System.getDisplayMode()` then (0 high power, 1 low power, 2 off). Times and
  modes only. The face used to decide
  always-on from the display mode alone on API 5 watches (Garmin staff advise it over the sleep callbacks,
  [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/369120/backlight-detection)): `onShow` read
  it and `onExitSleep` was ignored. Had the Descent said low power on the way back from a menu, or not called
  `onDisplayModeChanged` on a wrist raise, the face drew its dimmer always-on look (outlined grey time, dimmer
  palette) while awake: both of the owner's symptoms. Now the last callback decides: `X` and `S` → awake (motion only
  after `X`, #223), `E` → always-on on an AMOLED, `C` → the display mode. Reading a wake in logcat's runs:
  `X@<t>/sent 0` then
  `Dw@<t>/sent 0` (`Dn` in the sleep window; `sent` is the display mode); `S@<t>/sent 1` or no `C@<t>/sent 0` after
  `X` confirms the old code's mistake.
  **What the device showed** (a4962d408, 15:51 EET): `X@1794837086/sent 0 … Dn@1794837086/sent 0 … E@1794837092/sent
  2 … Da … X@1794837092/sent 0 … Dn@1794837093/sent 0`: every wrist raise woke the face, into the **night** palette
  in the afternoon. In the watch-face picker every other face was bright and this one dimmer (the owner). Night came
  from `UserProfile` sleepTime / wakeTime (documented as seconds since local midnight,
  [Profile](https://developer.garmin.com/connect-iq/api-docs/Toybox/UserProfile/Profile.html)); on newer watches
  the Sleep Mode schedule is set per day and doesn't reach those fields, which keep old or default values
  ([forum](https://forums.garmin.com/developer/connect-iq/f/discussion/409020/is-it-impossible-to-configure-sleep-wake-time-for-fenix-8)).
  The per-day `upcomingSleepTime` needs API 6 (the Descent has 5.1) and `ActivityMonitor` `isSleepMode` is
  deprecated. Garmin's own way of the night is Sleep Mode (hold MENU › System › Sleep Mode: Schedule per day, Watch
  Face, Display, Do Not Disturb, [Mk3 manual](https://www8.garmin.com/manuals/webhelp/GUID-9183E86B-2399-4CFC-AB50-EAFC6D6ED326/EN-US/GUID-10B3BD3D-5B72-4C4F-9B2D-93432AF67E43.html))
  with its own During Sleep display settings, and Do Not Disturb "dims the screen". So the face is in night while
  `DeviceSettings.doNotDisturb` is on (Sleep Mode turns it on unless the person turned that off), and the report
  carries the raw facts once per change as a `P` entry, `Ps<sleepTime>w<wakeTime>t<clock>d<doNotDisturb>z<isSleepMode>
  n<isNightModeEnabled>.<watch time>.<display mode>.0` (seconds since midnight; 1 / 0, 9 not on the watch, -1 no
  value). The panel's own brightness is the firmware's: the ambient light sensor dims a
  wrist-raise wake in low light "by design" and a button gives full power
  ([Garmin, fēnix 8](https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/fenix-8-series/406596/fenix-8---51-amoled-screen-too-dim-in-low-light-dark-environments/1927570)),
  and always-on runs the panel dimmed, which a face can't change
  ([garmin-matrix-time #164](https://github.com/wkusnierczyk/garmin-matrix-time/issues/164)).

## In Sukrara

Added:
- `:integrations` `garmin/`: `AuraPayload` (built from the hub's state, THE trend, the alert runtime's status, the
  sensor as the card decides it, the card's last treatment, today's time in range), `MonkeyC` (the wire format),
  `ConnectIq` (Garmin Connect's service, no SDK), `AuraLink` (push, retry, reconnect, the fetch server on
  127.0.0.1:17582), the page (Share with apps › Garmin since 2026-11-16's redesign: `GarminPage`, en / es); setting
  `apps.garmin` (on).
- `apps/garmin/source/link`: `AuraLinkService` (the face's background: the push, the 5-minute fetch) and `AuraLink`
  (the newest payload in storage, the inputs at every draw, THE SEAM's `AuraReading`); `LinkTest` runs every case.
- Debug: `GARMIN_SIM` sends to the Connect IQ simulator (tethered mode).
- `contracts/watchface/payload` (README, schema, cases, Garmin's bytes); parity group `watchface-link`.

Discarded, and why:
- **Garmin's companion SDK**: F-Droid refuses it (non-free), and Sukrara ships one APK to GitHub, Obtainium and
  F-Droid; being GPL alone would only take a linking exception ([appendix](#appendix-the-companion-sdk-and-free-software)).
- **Direct BLE**: not documented for faces, would compete with Garmin Connect's own connection, and Juggluco's
  reverse-engineered protocol is for a different app type.
- **A push that only says "fetch now"** (Pwb0186, AAPS): it would make every update depend on the HTTP path, which has
  its own Garmin Connect bug; the whole payload in both keeps either path enough on its own.
- **Watch → phone acknowledgements by message**: that direction is the one CIQQA-4631 breaks first; the fetch is the
  acknowledgement.
- **A secret for the fetch**: the face has no channel to learn one when push is broken, which is when the fetch is
  needed. Instead the port is narrowed (the main session's decision, 2026-11-16): it opens only while a watch is known
  to have the face (Garmin Connect says it is installed, or a push reached it), loopback only, and the switch closes
  it. **Left exposed**: while a watch has the face, any app on the phone can read the face's payload (the last 3 hours,
  the pill, the last treatment, the sensor's dates, today's time in range) from 127.0.0.1:17582, as any app can read
  the xDrip+ server while that is on. A phone without the face, or with the link off, serves nothing. **Cost**: if
  Garmin Connect neither answers about the face nor delivers a push (its bug at its worst), the port stays closed and
  the face gets nothing until one of them works; the page shows "Closed until a watch has Sukrara Aura".
- **The xDrip+ server for the face**: off by default, Juggluco's port, and its format lacks the pill, the sensor and
  the treatments. It stays as it is for other Garmin faces.

The settings (redesign, 2026-11-16; the owner: xDrip+ isn't a watch, its apps can't be switched one by one, "Sukrara
Aura" is one face of several, three "minutes ago" that look alike, *Send now* not looking like an action):
- **Share with apps › Watches › Garmin**, one row (the name says where, not which face), opening a page built like
  stock Settings' device pages: a **main switch** for Sukrara's own link, because that is what can really be turned
  off (`apps.garmin`: push and fetch together, for every Sukrara app on every watch; a per-watch or per-app switch has
  no use: the person picks the face on the watch), the [AOSP guidelines](https://source.android.com/docs/core/settings/settings-guidelines)'
  "main setting" for a feature that turns on or off as a whole; **action buttons** (*Send now*, *Open Garmin Connect*)
  as App info and Bluetooth device details draw them (SPA `ActionButtons`), not rows; **Watches** with Garmin
  Connect's own names, one line each with one clock time ("has the 14:21 reading", comparable with the phone); **Sukrara
  on your watch** (the face now; the data field and app later), where the face's fetch says whether it is the face in
  use.
- **The xDrip+ server is a local server for apps, not a watch**: its row moves to *Apps on this phone* beside the
  Nightscout API, with its own page (main switch, *Apps that read it* as a plain list with app icons, like stock
  Location's app list, no switches), and the Garmin page links to it for other faces and data fields.
- Compared: xDrip+ hides its server in Inter-app settings with a bare checkbox; GlucoDataHandler and Juggluco put a
  web-server toggle among their sources ([GDH](https://github.com/pachi81/GlucoDataHandler/wiki/Juggluco),
  [xDrip+](https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/technical/Local_Web_Services.md));
  none says which watch got which reading. Discarded: a details page per watch (one or two watches don't need it;
  their line holds all the link knows), a watch icon per row (a list of things of one kind has none), *Send now*
  disabled when no watch is near (SPA draws a disabled button's label at full contrast; a send then does no harm).

## Open

- What leaves Garmin Connect's link to the watch stuck (#242: seen once, 26 h) and whether a watch restart
  always ends it (seen: the watch's USB session did; Garmin Connect's own link resets didn't).
- Whether a face's phone-message registration survives a watch reboot on the Descent (the face registers again on
  every start).
- Whether Sukrara's own link to Garmin Connect (no Garmin library) is fine under Garmin's agreement, which forbids
  reverse engineering the SDK ([appendix](#appendix-the-companion-sdk-and-free-software)).

## Appendix: the companion SDK and free software

*Rechecked 2026-11-20.* The discard read as "no free app can use Garmin's companion SDK". Free apps use it; what rules
it out for Sukrara is F-Droid with one APK.

| Question | Finding |
|---|---|
| Licence | Proprietary. The [POM](https://repo1.maven.org/maven2/com/garmin/connectiq/ciq-companion-app-sdk/2.4.0/ciq-companion-app-sdk-2.4.0.pom) names Garmin's two old developer agreements; the [Connect IQ Agreement](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html) (updated 2024-08-06) replaces them, covers the "Companion SDK" (§IX e) and licenses it "for the sole purpose of developing and testing Applications" (§I a). [garmin/connectiq-android-sdk](https://github.com/garmin/connectiq-android-sdk) is Apache-2.0 for its sample only: its README puts the SDK under the agreement |
| Against open source? | The agreement has no clause about open source or copyleft |
| Free apps that ship it | [AAPS](https://github.com/nightscout/AndroidAPS/blob/master/gradle/libs.versions.toml) (AGPL-3.0); [Juggluco](https://github.com/j-kaltes/Juggluco/blob/primary/Common/build.gradle), [watchdrip](https://github.com/bigdigital/watchdrip), [qdomyos-zwift](https://github.com/cagnulein/qdomyos-zwift), [WheelLog](https://github.com/Wheellog/Wheellog.Android), [TAKWatch](https://github.com/TDF-PL/TAKWatch), [wormnav](https://github.com/andan67/wormnav) (GPL-3.0); [gexporter](https://github.com/gimportexportdevs/gexporter) (Apache-2.0); [Unquestionify](https://github.com/starryalley/Unquestionify-android) (MIT). Each one's Gradle file declares `ciq-companion-app-sdk` (2.0.3–2.4.0) |
| GPL | Linking a non-free library takes an additional permission (GPLv3 §7) from the copyright holders, and "you cannot authorize the exception for" other authors' code ([FSF FAQ](https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs)). Sukrara is the owner's code plus Juggluco's (`AidexX`, ported from its `aidexx`; the security core, [20](20-publishing.md)), so Juggluco's author would have to agree too. Juggluco links the SDK under plain GPL-3.0, with no exception in its licence. A permissive licence needs nothing |
| F-Droid main repo | Refused: binaries from Maven Central "must still be freely licensed, simply being included in one of those repositories is not enough" ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)); fdroidserver's scanner lists `com.garmin.connectiq:ciq-companion-app-sdk` as NonFree ([scanner.py](https://github.com/f-droid/fdroidserver/blob/master/fdroidserver/scanner.py)). None of the apps above is on F-Droid. Talking to Garmin Connect without the library is allowed, with at most *NonFreeDep* ("needs a non-libre app to work", [Anti-Features](https://f-droid.org/docs/Anti-Features/)). Sukrara's release APK passes the scanner's class check: its dex has no `com/garmin` class, only the service's name as a string (dexdump, 2026-11-20) |
| IzzyOnDroid | Proprietary components only if "essential for the app's core functionality", marked *NonFreeComp* ([policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)); the Garmin link is one feature of many, so not a safe bet |

**Sukrara's own link and Garmin's agreement.** The owner accepted the agreement to publish Sukrara Aura. It forbids to
"decompile, reverse engineer, disassemble, attempt to derive the source code of" the Program Materials (§I b; the SDK,
the Companion SDK included) and counts "the functional or technical design, logic or other internal routines or
workings of the Garmin SDK" as Confidential Information (§IX f), not to be disclosed (§IV d) unless publicly known
through no fault of yours, received from a third party without restriction, or developed independently. `ConnectIq`
uses the service's name and its calls' transaction numbers in Garmin's declaration order, and `MonkeyC` was checked
against Garmin's serializer (Proof). Copies of the interface were public before, e.g. `IConnectIQService.java` in
[NavigationIQ](https://github.com/dangphuc2470/NavigationIQ) (apparently from the SDK's own code). In the EU,
decompiling for interoperability with an independently created program is allowed when the information "has not
previously been readily available", and a contract term against it is null and void
([Directive 2009/24/EC](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32009L0024) Art. 6, 8); whether that
holds when Garmin offers the interface inside a non-free library is a legal question, not answered here. Garmin may
remove an app from its store "for any reason" (§II 5) and end the agreement for a breach not cured within 30 days
(§III b 2).

| | Sukrara's own link (today) | Garmin's SDK |
|---|---|---|
| F-Droid main repo | Yes, one APK everywhere | Only a separate build without the Garmin link; the owner chose one APK ([20](20-publishing.md)) |
| GPL | Nothing to do | A §7 exception from the owner and Juggluco's author |
| Garmin's agreement | The reverse-engineering clause above | Complies |
| Upkeep | Breaks if Garmin Connect changes its service (apps on older SDK versions rely on it too) | Garmin updates the library |

Sukrara's own link stays while F-Droid with one APK is the plan; the agreement question is in Open.
