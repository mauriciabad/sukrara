# Changelog

Sukrara's releases. 0.x versions are previews.

## Unreleased

- **Sukrara Aura** no longer crashes and leaves the watch on its default face: its glide could start while the watch
  was in low power, as when it came back from a notification, and a change of the screen's mode could reach its
  background link (#223).

## 0.15.0 — the website, Sukrara Aura for Garmin, one glucose notification, the trend from your readings

- **The website, sukrara.mauri.app** (#212, #180): rebuilt with a page for each app (Android, Mac and the Garmin face)
  in English and Spanish: what Sukrara does for you and for the people who look after you, pictures of the apps
  (made-up data), install steps and the questions people ask. *Features* shows every feature as a tile, to answer
  "does it do X?" at a glance. *Get Sukrara* has every way to install (Homebrew's badge copies its command), and under
  each download *Check it's the real Sukrara* gives the signing certificate (Android) or the Developer ID and
  notarization (Mac) and the file's SHA-256, with the commands to compare. The Garmin page shows the face living
  through a made-up day, and its playground lets you try every setting. The site loads nothing from other sites; the
  developer docs and reports are under *Developers*.
- **Sukrara Aura, a Garmin watch face** (Descent Mk3i 43 mm for now; docs/42, docs/43): the time and your glucose in
  its range colour with the last hours as a sparkline (or the trend arrow), what Sukrara wants from you (carbs,
  insulin, muted), the watch battery, the sensor's days or a glucose rail on each side, and fields you choose (steps,
  time in range, the last treatment, weather, a second time zone…). Its dial is the aura of your glucose: it gathers
  at your level and spreads the further out of range you are. Always-on and night looks (night follows the watch's Do
  Not Disturb), and a private mode that shows the value only while you hold the screen. Each reading comes from
  Sukrara on the phone through Garmin Connect within seconds, with nothing to set up; an old value never looks
  current, and the face sounds no alerts. English and Spanish. **It isn't on the Connect IQ store yet**: it goes there
  once its final build has passed the check on the watch and Garmin's review (no date yet); until then only a build
  from source can be sideloaded ([docs/DEVELOPMENT.md › Garmin](docs/DEVELOPMENT.md#garmin-sukrara-aura)). Sukrara for
  Android is ready for it: **Settings › Share with apps › Garmin** has one switch for Sukrara on the watch (on by
  default), *Send now*, *Open Garmin Connect* and each watch with the reading it has (*Connected · has the 14:21
  reading*). Other Garmin faces and data fields made for xDrip+ read the **xDrip+ server**, now a page of its own
  under *Apps on this phone* that lists the apps reading it.
- **One glucose notification, which is also the alert** (Android, docs/36): while an alert is up, the glucose
  notification carries it (*Low · eat carbs* in the Live Update, *Low* over the card) instead of a second notification
  saying the same: each ring comes on the alert's own channel (its sound, vibration and Do Not Disturb behaviour),
  then it stays on, silent, with *Treatment* · *Remind less* · *Mute*; a swipe answers it. An alarm-like alert with
  the screen off still opens full screen. *Separate alert notifications* (Settings › Alerts, off) brings separate
  alerts back, e.g. for a watch that shows them. **Settings › Status bar & notification** (once *Status bar*; also a
  setup step) shows each way to see your glucose as a picture: the *Chip* (a Live Update by the clock, the default),
  the *Notification icon* (value and trend, value, trend or Sukrara's icon) with the graph in the notification,
  *Status bar text* (root), or *Nothing* (monitoring and every alert keep working; the page shows the one step left in
  Android's settings). The notification is on the system's colour (*Coloured background* for the range colour), always
  first in the shade, its value without the unit (*138 ↗*); the Live Update names the insulin (*Rapid 4u · 30g*), says
  the trend in words, and its value and arrow are never cut.
- **The trend arrow comes from your readings** (both apps, #204; the owner: the arrow said → through a rise of 1 mg/dL
  a minute, and "falling" minutes into a rebound). Every arrow, spoken trend and output used the sensor's own rate,
  which lags about 7 minutes and reads only a third of how fast glucose moves. Now one trend, worked out from the last
  minutes of readings, is the same on the card, widgets, notifications, alerts, the status bar, the Mac menu bar, read
  aloud and the Nightscout / xDrip+ outputs: about 4 minutes after the move instead of 7 (or never). The arrow turns
  smoothly with how fast glucose moves (0.4 and 0.9 look different), its words keep the ±1 / 2 / 3 bands and don't
  flip at an edge, and ⇈ / ⇊ need 2 minutes of a really fast move. **Settings › Readings › Trend arrow** (both apps):
  *Quick*, *Balanced* (default), *Smooth* or *From the sensor* (as before), with a live preview of what each says now;
  followers take the wearer's choice. You'll see ↑ / ↓ more often: that is how fast your readings really move.
- **Falling fast / rising fast alerts follow the same trend** (off by default): from the readings they ring once the
  fast move has lasted 5 minutes (on the owner's readings, at 2 mg/dL/min about 9 times a day, as often as glucose
  really moved that fast, about 9 minutes after it began; with the sensor's rate they rang twice a day and missed most
  fast moves). *From the sensor* keeps them as they were.
- **Alerts show what is true now** (both apps, #195): an alert left up shows the newest value, arrow and age, the same
  as the glucose notification and the card, updated silently with every change (never in the 10 s after a ring), LO /
  HI too. Between its rings it stays on screen: out of range again after a moment back in range, it shows again at
  once without sound, and a milder one silently replaces a worse one (very low → low). An alert you swiped or closed
  stays away until its next ring, also across a restart. Followers get the same. Once *No data* holds, the level and
  rate alerts go and *No data* says when the last reading came.
- **Treatment on every alert, and shorter words** (both apps, #194): an alert's first button is *Treatment* (Add
  treatment on Carbs for a low or falling fast, on Rapid insulin for a high or rising fast; not on *No data*, and only
  where this device can add treatments), then *Remind less* and *Mute*, with no ellipsis; a muted note offers
  *Treatment* and *Unmute* only. Headlines name the kind alone (*Very low*, *Low*, *No data*), mutes the side (*Lows
  muted · 12 min left*). *Review alert thresholds* (setup and Settings) says only what sets each alert off (*Below
  70*, *At or above 180*, *Off*), and its button is *Confirm thresholds*.
- **Quiet lows rising back into range** (both apps, #193; Settings › Alerts › Quiet times, on by default): a low alert
  waits while the last 5 minutes of readings clearly rise back into range within *Back in range within* (20 min), like
  *Quiet highs falling back into range* for highs; it comes at once when the rise stalls or the readings stop. A very
  low waits only on your own phone, and never its first alert. Both quiets read the readings now, not the sensor's
  lagging rate.
- **Metrics, once Rings** (both apps, docs/39): what sits beside the value has its own switch each: the main, second
  and third metric (time in range, GMI, average glucose or variability, each with its period and its goal), the last
  treatment (it keeps its place while off) and the metric shown when there's no recent treatment. Each opens its page
  with the live card on top; the time-in-range rings' number, rewards and colour order have a page of their own. A GMI
  covers at least 7 days. A widget's own *Metrics* shows at most 1–3 things, or just the value. The last treatment's
  ring shows the time since it first and the amount under it (*40m* over *4.5u 45g*, read aloud in that order; #206),
  and the value keeps at least 85 % of its size beside a pill.
- **A cleaner treatment list** (#207): on Android each treatment starts with its chart marker in its colour, a pen
  icon replaces *· from the pen*, and the glucose with its trend arrow when you logged it sits at the end. The cloud
  shows only when a treatment isn't in LibreView yet (and for a moment after it gets there). A tap opens it in a
  bottom sheet to change it or add a note (pen doses too), with *Delete*; a swipe deletes it, and *Deleted: 4.5u rapid
  insulin* offers *Undo* for 10 s (the trash button is gone). *See all* opens a new Treatments page with the last 31
  days by day. The Mac's Treatments window follows: chart markers, the glucose then, *Sending* / *Not sent* / *On the
  phone* only while they say something, Edit and Delete on hover (also in the context menu, with VoiceOver and a
  trackpad swipe), a pen on pen doses.
- **Add treatment** (#181): on Android, opened in the app it is a bottom sheet that comes up with the keyboard as one
  (a drag down takes both); *Minutes ago* is one wheel of minutes and hours with *5m* · *15m* · *30m* · *1h* chips
  (the same chips change a quiet time's length and the report's days); the note has no example text, and the pen
  reminder under the fields is gone. On the Mac it opens whole on screen, centred on the pointer when opened from a
  widget; the amounts and minutes ago are number fields with a stepper and the arrow keys, no field has a placeholder,
  and the buttons are the regular size.
- **Settings reorganised** (both apps, #205): **Readings** (unit, ranges, trend arrow; once *Ranges*), **Graph** (the
  chart only), **Metrics** (its own page on Android), Alerts › **Fine-tuning** (the waits near a threshold and the
  notification buttons), and **Import and export** (Export data, Import history and the settings file, out of Share
  with apps and Advanced). Sensor end has a chequered flag and the Sensor page links to it. One night for both the
  night alerts and the sensor reminders (the sensor reminders now keep quiet in your Night alerts hours: set it again
  there or in Sensor end if yours differed). Pages link to the ones people look for next (*See also*), and the trend
  arrow, the night and the sensor's time left can be changed in both places. Mac panes: General · Following · Alerts ·
  Readings · Graph · Metrics; Juggluco moved to Following. Search still finds the old names (*Ranges*, *Graph*,
  *Export*).
- **One row for a setting a place can change** (both apps, #209): *Time shown* and *Sensor time left* say the app's
  value and only the places that differ (*12 h · 3 h in the notification*); the row's page sets the notification and
  the widgets (Mac: the widgets) or keeps them *Same as the app*. A switch with options of its own opens its page
  (each quiet time, *Over Bluetooth nearby*, the Nightscout API; Mac: *Details…*) instead of rows appearing under it.
  On Android a setting shown in a second place says where it is, no icon repeats on a page, every banner, warning and
  tip is one notice with a full-size close, *Insulin pens* has the earlier app's main switch and *Automatic detection*
  page again (and says what really happens with Juggluco: it reads a pen only while it's open), and the *About* row
  says the version or the update found.
- **Who is nearby, and how readings come** (both apps): your home header adds the Bluetooth mark with how many of your
  followers' devices are connected nearby (*2 followers · ᛒ 1*); the followers list marks who is paired nearby,
  connected or not; *Over Bluetooth nearby* now lists every follower device that has connected over Bluetooth, also
  out of range (it showed only connected ones and those paired with *Pair nearby*). A follower's phone and the Mac
  panel say whether the readings come nearby over Bluetooth, over the internet or can't arrive (*Nearby*, *Internet*,
  *Offline* at the end of the Mac's mute row). The header line is whole at every font size and in Spanish (it said "1
  followe" where the line was too narrow).
- **Bluetooth sharing gets its permission, and blocked features say so** (both apps, docs/40): setup asked only part
  of *Nearby devices*, so a wearer's phone never shared over Bluetooth (followers got readings over the internet only,
  and only the Sharing page said why). Setup asks all of it now, and the switch and *Allow* ask in context. A feature
  that is on but blocked by the system (Android: Bluetooth sharing or following, Health Connect, the Nightscout API on
  home Wi-Fi; Mac: Bluetooth, *Open at login* waiting in Login Items) shows on home or the panel and on its Settings
  row, with *Allow* and *Turn off*. *Share with someone* has *Let them add treatments* (the approve dialog starts from
  it), the followers list marks who can, and the apps say "treatments" everywhere (they said "entries").
- **Setup: the essentials first, the rest when you like** (Android, #181): Welcome, role, permissions, sensor and
  alert thresholds, then *The essentials are done*, then optional steps now or later from Settings' *Finish setup*:
  *Keep Sukrara up to date* (GitHub version), *Root*, *Status bar & notification*, your followers, a new *Insulin
  pens* step and your history. Home's banner names only the essentials.
- **The GitHub version's update checks are opt-in** (Android; F-Droid allows updates from outside it only with
  consent, and the same APK will go there): *Check for updates automatically* starts off, and setup's *Keep Sukrara up
  to date* step and a banner on home and in Settings suggest it, *Turn on* and *Not now* side by side. **After
  updating from 0.14.x the checks are off** until you tap *Turn on* (or Settings › About › Updates). The Google Play
  version and the Mac are unchanged.
- **LibreView gets a Libre 1 / 2 read through Juggluco, and says when glucose doesn't arrive** (Android, docs/41):
  only a Libre 3 that Sukrara reads itself went up, so with a Libre 2 through Juggluco only treatments did. Now a
  Libre 1, 2 or 2 Plus read through Juggluco goes up as Sukrara's one LibreView device; *Send earlier glucose* sends
  the current sensor's readings from before you switched the upload on. Other sensors through Juggluco never go up,
  and the *Glucose* switch says so at once. When LibreView's newest glucose is over 2 hours behind the phone's, the
  page says *No glucose sent since …* and home *LibreView isn't getting your glucose* (*Fix*). A sensor registration
  LibreView refuses is retried until it goes through, instead of holding that sensor's glucose back.
- **Mac: pinch zoom and a smooth chart again, legible widgets, Settings and About like the system's** (#181, #189):
  pinch zooms the panel's chart again (opening the panel brings Sukrara to the front, which macOS needs for trackpad
  gestures), and the chart scrolls smoothly over a whole month (about 4.5 ms a step, was 16 ms); the card's flowers,
  laurel and first digit are drawn whole past its edges. Widgets stay legible when macOS dims the desktop: the card
  sits on its own opaque background. Settings' panes are a toolbar in the title bar, each an icon over its name,
  reopening on the last one; *About Sukrara* is its own window (the version, which version, the medical reminder, the
  website, privacy policy and source code, Sukrara for Android) from the app menu, the right-click menu and Settings ›
  General.
- **Amounts with a decimal comma in Spanish** (both apps, docs/36 §13): *4,5u* on the card, the Live Update, the
  lists, Add treatment and the pens; the GMI, time in range and the Mac's report follow the language too. The Live
  Update's Spanish labels are written to fit (*Subida rápida*, *Sin tendencia*) instead of being cut (*Subiendo
  ráp…*).
- **Add treatment no longer opens with a stray `.243` in Rapid (Android)** (the owner). A thumb on the number pad
  while the dialog was over the lock screen typed it; when the dialog closed by itself it was kept as an unsaved entry
  and filled every Add treatment for 12 hours. Only amounts typed as Save stores them (insulin to 0.1 u, whole grams)
  are kept now, and such a kept entry is no longer offered.
- **Fixes**: a Mute shorter than the alert's repeat (a high muted 3 min, repeating every 15) now alerts when it ends
  if the side still matches, instead of at the next repeat (both apps); home's glucose value overflows the card's top
  instead of being cut (Android).
- **On Google Play** (live in production since 0.14.5): a GitHub install switches with *Update from Play*, keeping its
  data (the website's Android page says how). The Mac App Store version is in App Review (submitted with 0.14.5).
- **Releases are normal GitHub releases now**, marked *Latest*, also 0.12.0 … 0.14.5 (they were prereleases, so
  GitHub's *Latest* was still the old proof of concept). [Obtainium](https://obtainium.imranr.dev/) no longer needs
  *Include prereleases*, and links to the latest release get Sukrara. The apps' own update checks (Android's, the
  Mac's Sparkle feed) and the Homebrew cask take every release as before; a GitHub prerelease (a release candidate) is
  never offered. From this release on they are also immutable: their files and tag can't change after publishing, and
  GitHub keeps a signed record of them.

## 0.14.5

- **Long-acting insulin typed after a pen read no longer asks *Already logged by your pen?* (Android)** (the owner:
  5.5u rapid from the smart pen, then 15u long-acting from a plain pen): the question compared the typed dose with
  pen entries of either insulin, so any long-acting dose near a rapid pen injection asked. Now only a pen entry of the
  same insulin within *Doses you type: time apart* asks, as in the PoC and on the Mac. When it does ask, the question
  dims the Add treatment dialog behind it instead of sitting flat on it, with *Log anyway* and *Cancel*; the *When?*
  picker gets the same dim.

## 0.14.4

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

## 0.14.3

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

## 0.14.2 — Android alerts show again

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

## 0.14.1 — sharing messages handled once, the Mac keeps running when a relay drops

- **Sharing (Android, wearer)**: each message from a follower is handled once, also across restarts (#78). Before, every
  start handled again every message the relays still kept: an allowed follower got its key again, an ended follow
  got its notice again, and an older name change could replace a newer one.
- **Mac**: the Mac could quit overnight when a relay connection dropped, and its glucose and alerts stopped until
  Sukrara was opened again. A relay that answers a ping twice (the answer, then the dropped connection) or never no
  longer stops it.

## 0.14.0 — the apps update themselves, Do Not Disturb respected by default, widgets like the earlier app

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

## 0.13.0 — fixes from the first days of 0.12.0, monitoring before the first unlock

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

## 0.12.0 — first release of the Android and Mac apps

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
