# Changelog

Sukrara's releases. 0.x versions are previews (GitHub prereleases).

## Unreleased

- **Long-acting insulin typed after a pen read no longer asks *Already logged by your pen?* (Android)** (the owner:
  5.5u rapid from the smart pen, then 15u long-acting from a plain pen): the question compared the typed dose with
  pen entries of either insulin, so any long-acting dose near a rapid pen injection asked. Now only a pen entry of the
  same insulin within *Doses you type: time apart* asks, as in the PoC and on the Mac. When it does ask, the question
  dims the Add treatment dialog behind it instead of sitting flat on it, with *Log anyway* and *Cancel*; the *When?*
  picker gets the same dim.

## 0.14.4 (GitHub prerelease)

- **The low limit reads 70 again, and 70 is in range** (both apps; the owner: "the 70 is the threshold, but 70 itself
  is excluded from the interval -> (70, 55) is low [55,-Infinity) is very low"): 0.14.3 showed the low limit as 69;
  now the ranges' and the low alert's default is 70 again and low means **below** it, so 69 is low and 70 in range,
  as before 0.14.3. Very low still includes its limit: 55 is very low, on the card and in the alerts. The chart's line
  reads 70, Settings › Ranges says *Low below* and the low alert *Below 70 mg/dL*; the line under each says *Low:
  56–69 mg/dL*, *In range: 70–179 mg/dL* as before. A low you set yourself (69, say) stays as you set it; 70 is a
  preset again to go back.
- **A typed number saves again (Android)**: since 0.14.0 *OK* under *Custom…* in every number setting (range limits,
  alert thresholds, reminders, quiet times …) did nothing; it saves and closes again, and so does the keyboard's
  *Done*.

## 0.14.3 (GitHub prerelease)

- **Low is below 70, and the low alerts ring where the card says Low** (both apps; the owner: "Low is below 70 (70 is
  in the okay range), so from 69 downwards. And very low from 55 or lower"): every limit is the first value of the
  range it names, so at the defaults very low is up to 55, low 56–69, in range 70–179, high 180–249 and very high from
  250, on the card (colours, range word, time in range, report) and in the alerts alike. The low limits' defaults are
  now 55 and 69 (were 54 and 70), and the low and very low alerts ring **at** their threshold, not only below it (a
  low alert at 69 rings at 69). Time in range counts 70 in range again, like LibreView; 180 stays high.
- **Each range limit and alert threshold says which values it covers** (both apps): *Low: 56–69 mg/dL*, *In range:
  70–179 mg/dL*, *Very high: 250–299* under the number, in your unit, and it follows the number while you type.
- **Add treatment (Android) looks like the earlier app again** (#181): each field's label carries its chart marker and
  stays on the outline, so the dialog no longer jumps in height when you move between fields; the time is a chip next
  to the title; over the lock screen *Unlock* sits where *Open Sukrara* does, without the extra countdown line, and the
  dialog now closes unsaved after 10 s by default (was 20 s; Settings › Lock screen › *Time to save*).
- **App icons in Settings are back** (Android, #181): the rows for LibreView, Tidepool, Nightscout, the xDrip+ server,
  Health Connect, Juggluco and every app glucose is sent to show that app's own icon again, as in the earlier app, and
  Garmin Connect is listed under the xDrip+ server again.
- **Android › Alert reliability** (dark theme): with every check on, the top banner is one card again; it showed a
  grey rectangle inside a darker card (#181).
- **Android › Settings search** works like the earlier app's again (#181): a full-screen search with each result's section icon and
  where it is (page and group), ranked like Android's own search (groups and other words find rows too), one result per
  page, and a result opens its page through the menus (Back walks up) at its row, which flashes; Alert reliability's
  checklist, Share with apps' rows and the night alerts' pages are searchable. Opening *Night alerts* from it crashed the app (and stopped
  monitoring) on 0.13–0.14.1 release builds while the night alerts or a quiet side applied: their lines' strings were
  found by name and had been shrunk away. They are found by the ids written in code now, so no build can lose them.

## 0.14.2 — Android alerts show again (GitHub prerelease)

- **Android alerts are posted again** (release builds): resource shrinking removed the alert and mute texts the app
  looks up by name, so a real alert's notification couldn't be built and no alert showed (the alert logic itself
  fired). Every string is kept now, and the release check fails if one is missing from the APK.
- **An alert that can't be shown still rings** (Android and Mac): if an alert's notification can't be built or posted,
  Sukrara shows it plain on the same channel (its sound, vibration and Do Not Disturb behaviour: the kind, the value
  with its unit and arrow, *Open Sukrara*), or else a *Monitoring problems* notice, and tries the full alert again.
  Settings › Alert reliability (Android) and the menu bar panel (Mac) say *An alert couldn't be shown*, with *Copy
  report*. The failure also reaches logcat now, not only the diagnostic log.
- **A value at a limit is in the range beyond it** (#181): at exactly 180 mg/dL (the high limit) the value is high,
  at 70 low, at 54 very low and at 250 very high, on both apps: the value's and the line's colour, the range word,
  the status bar, the menu bar, the notification, time in range and the report's ranges (*High 180–249*). Time in
  range now counts readings at 70 and 180 outside the range. Alerts are unchanged.
- **The Mac disk image has its own icons** (#181): the opened volume is a Sukrara drive and the `.dmg` file a disk
  image page with that drive, no longer the app's icon.

## 0.14.1 — sharing messages handled once, the Mac keeps running when a relay drops (GitHub prerelease)

- **Sharing (Android, wearer)**: each message from a follower is handled once, also across restarts (#78). Before, every
  start handled again every message the relays still kept: an allowed follower got its key again, an ended follow
  got its notice again, and an older name change could replace a newer one.
- **Mac**: the Mac could quit overnight when a relay connection dropped, and its glucose and alerts stopped until
  Sukrara was opened again. A relay that answers a ping twice (the answer, then the dropped connection) or never no
  longer stops it.

## 0.14.0 — the apps update themselves, Do Not Disturb respected by default, widgets like the earlier app (GitHub prerelease)

- **The Android GitHub version updates itself** (#188): once a day it looks for a new version and says so in a quiet
  notification (*Update*, *Release notes*), downloads it, checks it is the release's file signed with Sukrara's key,
  and installs it, never while an alert is showing or a very low is on; monitoring comes back by itself right after.
  Android asks once to allow installs from Sukrara. *Install updates automatically* (Settings › About › Updates, off)
  installs without a tap where Android allows it. The Google Play version keeps updating through Play. Updating to
  this version is still by hand; from then on Sukrara does it.
- **The Mac updates itself** (GitHub and Homebrew version, #91): once a day it looks for a new version, signed by
  Sukrara and checked before it installs, and offers it in the menu bar panel; *Install and Relaunch* is one click.
  Settings › General › About has *Check for Updates…*, automatic checks (on) and automatic install (off). The App
  Store version keeps updating through the store. Updating to this version is still by hand (the dmg or `brew upgrade`);
  from then on Sukrara does it.
- **Do Not Disturb and Focus are respected by default** (#184): the alert checklist says plainly whether Sukrara
  alerts in Do Not Disturb (Android) or during a Focus (Mac), with *Let Sukrara through* / *Don't let Sukrara through*
  (Android: Do Not Disturb access the first time, then the alarm channels' override; Mac: Time Sensitive), and home
  shows a neutral line while a mode keeps alerts quiet. Updating from 0.13.0: the alarm channels keep the override
  they have; the Mac's alerts stop being Time Sensitive until you let Sukrara through.
- ***Sound while the phone is unlocked*** (Android, #183) is answered from a test alert: *Test the sound*, then
  *I heard it* / *It only vibrated*. The earlier answer is asked again.
- **Widgets and the card look like the earlier app again** (#181): Android's widgets are drawn at the size the
  launcher really gives them and are never stretched or squashed, on the home screen and in the widget picker, in the
  wallpaper's colours. On Android and the Mac the rings show their number without "%", a fresh value has no "now",
  and the value has as many slots beside it as fit (up to four).
- **Android look** (#181): the phone's own colours across the app, the dark theme included, the phone's round font in
  titles, and Material 3 Expressive motion.
- **Settings** (Android, #181): a search result opens its page at the row it found and highlights it; the alert
  reliability checklist, the status lines and the dialogs use Android's own settings components; Sharing's rows have
  their icons; TalkBack says again what a tap on an action row does (copy, import, fix).
- **Sharing** (#181): a follower whose invite the wearer never answers no longer waits forever: when the invite
  expires, Sukrara says *Invite expired* with *Join with a new link* (Android and Mac). The Mac's *Find phones nearby*
  works again after a follow ended.
- **Fixes** (#181): *Active now* names the right reason when a Mute and carbs quiet the same alerts (Android and
  Mac); *Add treatment* opened from home no longer offers *Open Sukrara*; in Spanish the app says "hidratos"
  everywhere and "Glucosa baja / alta" for the alert groups.
- **Not a medical device**: setup's Welcome and About in Settings say so, on Android and the Mac; the Mac's About also
  links the privacy policy.

## 0.13.0 — fixes from the first days of 0.12.0, monitoring before the first unlock (GitHub prerelease)

The first build submitted to Google Play. Not tested on a real sensor yet. Install and notes: the
[v0.13.0 release](https://github.com/mauriciabad/sukrara/releases/tag/v0.13.0).

- **Android, after a restart**: the wearer's own sensor is read, alerts ring and the glucose notification shows
  before the first unlock (#160). What monitoring needs moved to storage that works before the unlock, with nothing
  carried over: after the update setup runs again, the alert, unit, graph, sensor and status-bar settings start from
  their defaults and the sensor is paired again; the history, treatments and sharing identity stay.
- **Settings** drawn with Android's own settings components and screens that move like the earlier app (#168, #169);
  Share with someone right in the dark theme (#170).
- **Fixes**: the phone's 24-hour setting (#166); Allow / Deny from a sharing request never turns sharing off (#167);
  Save to password manager in one go (#171); pairing codes the same on both screens, both sides move on by
  themselves (#173, #174); setup resumes where it was left, Android and Mac (#175); *Import history* reads
  LibreView's file in every date layout, and one LibreView sign-in for the whole app (#176, #177); root asked for when
  a root feature is turned on, and the LSPosed hooks work in release builds (#178).
- **New**: the LSPosed lock-screen widget around the fingerprint icon (#66); scan an invite QR code with the Mac's
  camera (#172); alarm channels the person edited take the Do Not Disturb override (#15); About opens the website's
  page for the other devices (#179) and says which version it is (GitHub or Google Play).
- **Google Play build**: the same app as the GitHub one, without its own update check.

## 0.12.0 — first release of the Android and Mac apps (GitHub prerelease)

Not tested on a real sensor yet. Install, what's in it and how to bring your history over: the
[v0.12.0 release](https://github.com/mauriciabad/sukrara/releases/tag/v0.12.0). Every behaviour:
[docs/FEATURES.md](docs/FEATURES.md).

- **Apps** for Android and Mac (`apps/`), tested against shared specs and fixtures (`contracts/`): the alert rules
  (timeline scenarios), the card renderer, protocol v2, the settings schema, treatments and the export. App id
  `app.mauri.sukrara` on both.
- **Android** (16+, arm64; you wear the sensor): Libre 3 / 3 Plus read directly (NFC start / takeover with the
  LibreView account's receiver ID, then Bluetooth with backfill) or from Juggluco; Dexcom, Accu-Chek SmartGuide and
  Aidex drivers marked *Untested*; home, the live notification and Live Update chip, widgets, alerts with mute / unmute,
  night alerts and escalation to followers, the alert checklist and a real test alert, treatments (dialog, Quick
  Settings tile, lock screen), NovoPen Echo Plus over NFC, sensor-end reminders and calendar, following someone,
  sharing over Nostr and Bluetooth nearby, uploads (LibreView, Tidepool, Nightscout over https), Health Connect,
  broadcasts, the xDrip+ server and Nightscout API on the phone, export and **Import history**, the root status bar.
  Signed with Sukrara's own release key; a Google Play build (`play` flavor) without the root / LSPosed features and
  the update check.
- **Mac** (Apple Silicon, macOS 15+; you follow someone): menu bar value and panel, following by invite / QR / nearby
  Bluetooth, alerts with sound packs (Time Sensitive), treatments, the glucose report and CSV export, settings,
  desktop widgets, Juggluco on the home network, Read Glucose Aloud. Developer ID signed and notarized.
- **Both**: mg/dL or mmol/L, English and Spanish (Spain), one TalkBack / VoiceOver sentence for the card, an update
  check against GitHub releases (0.x builds are offered prereleases).
- **Starts fresh**: nothing of 0.11.x is read; export there (Settings › Share with apps › Export data) and use setup's
  *Bring your history back*.
- **Not included**: the LSPosed lock-screen widget, carbs and insulin in Health Connect, Nightscout over plain
  `http://`.
