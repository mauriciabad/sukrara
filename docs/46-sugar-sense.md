# 46 — Sugar Sense: every feature, how it's built, and how it compares

*2026-11-21 · investigation report for [#241](https://github.com/mauriciabad/sukrara/issues/241) · three rounds ·
**desk research** (website, stores and their screenshots, version histories, the in-app What's New, GitHub, press,
reviews, the Alexa skill's store page as the owner saw it) · **hands-on** with the owner's account (Premium trial,
FreeStyle Libre through LibreLinkUp, in round 3 a test Nightscout site) and a second account on the free plan: Android
2.7.0 on two emulators (walked and decompiled, the two accounts sharing with each other) and on the owner's Pixel 10 Pro
with root (network capture per app, the app's files, logcat), the three Garmin apps on the owner's Descent Mk3i, the web
dashboard and the invite page, the iPhone app 2.7 running on a Mac and its App Store bundle read (Apple Watch, CarPlay,
Siri, widgets, Live Activity), the Mac menu bar app 1.3 with its widgets and Safari extension, Windows 1.2.1 in a
Windows 11 VM, the AI connector with Claude Code and ChatGPT, and the Home Assistant integration 1.0.0 · **round 3**:
two throwaway Nightscout sites fed one scripted reading a minute to the two accounts (alert timings, the follower's
side, Call for Low with real calls to the owner's phone, treatments both ways, the bridge, switching source, deleting
the second account), and Wear OS 2.7.0 on a Wear OS 6 emulator paired with the phone emulator · how each claim is known:
*seen*, *read* (code, bundle, strings) or *claimed* (their site, store or policy); "unverified" = no primary source
found · nothing built, no Sukrara code changed*

Sources are cited by key (`ios`, `play`…, listed in [Sources](#sources)); screenshots by file name, without `.png`,
under their folder (`android/home-20-chart-scrub`). The screenshots stay on the owner's Mac
(`~/Documents/sugar-sense-241/<surface>/`) and are **not committed**: they show the owner's data and Sugar Sense's UI.

## Goal

The owner's words (the issue), condensed:

- Sugar Sense "is very very similar to my vision for Sukrara": explore it deeply and **enumerate every feature**: every
  website page, store page, repository, article and FAQ; install the apps and every needed companion (Home Assistant),
  open every screen.
- The list decides **what Sukrara builds and in what order**, and **how**: their UX and technical details feed
  Sukrara's plans.
- Entry points named: Android, iOS, macOS, Windows, Alexa, Home Assistant, CarPlay, Garmin, Apple Watch, Wear OS, a DIY
  display, browser extension, web dashboard, MCP; gamification, call for low alerts, meal logs, saved meals, AI
  insights, low prediction, emails, statistics, docs with video tutorials, following several people, following while
  sharing, AI food scanner, Partner Program, social accounts, status page.
- After the first round, "still missing details": treatments not saved; whether insulin reaches LibreView or
  Nightscout; the sharing functions; the free state and paywall; Wear OS, Apple Watch, CarPlay, Safari extension, Mac
  widgets, Windows, AI connectors, Garmin; the promoted Live Update chip and the lock screen.
- After the second round: are treatments saved and does insulin reach LibreView or Nightscout; can it really read
  Juggluco or xDrip+; was all of sharing checked; the free state and paywall; Wear OS, Apple Watch, CarPlay, Alexa
  (skipped), Safari's extension, Mac widgets, Windows; did the Live Update chip and the lock screen work. Answered in
  [The owner's questions](#the-owners-questions).

## Short answer

- **What it is**: a cloud CGM companion by Sugar Sense OÜ (Tallinn; a founder with type 1 diabetes). Its servers sign in
  to the person's **LibreLinkUp, Dexcom Share or Nightscout** and poll them around the clock (LibreLinkUp every 2–3 min:
  a reading reaches the phone 21–24 s after its LibreLinkUp time; a Nightscout site every 10–35 s: alerts 23–55 s after
  the reading); they run **every alert on the server** and push alerts and readings to phones, which then feed widgets,
  Live Activities and Live Updates. Every other surface (watches, Mac, Windows, browser, web, Alexa, Home Assistant,
  Garmin, a DIY display, AI assistants) is a **read-only view without alerts of its own** (CarPlay repeats the phone's;
  the Wear OS app has no notification permission). A free tier with every alert, sharing and Copy for AI; Premium
  ($39.99/yr, €41.99 on a Spanish device) for glanceables, watches, AI, PDF, 30 / 90 days, call for low, more than two
  reminders.
- **Their log stays with them**: meals, insulin, medication, activity and notes go only to their server (and, on iOS,
  meals and insulin to Apple Health, *claimed*). **Nothing reaches LibreView** (none of the first round's test entries
  is in the owner's LibreView export, taken hours later) **or Nightscout** (with a Nightscout source every request to
  the site was a read, and a meal with insulin logged in the app sent nothing; the site's own treatments never came in
  either, only its sensor start). Health Connect gets glucose only.
- **Pace**: relaunched as 2.0 on 2026-07-02; a feature release every 2–3 weeks since, and more than a dozen surfaces
  added in those five months.
- **Where it leads Sukrara**: breadth of surfaces (iPhone, Apple Watch, CarPlay, Wear OS, Windows, browsers, web,
  Alexa, Siri, Home Assistant, MCP, a $25 bedside display); a richer log (meals with names, My Foods, medication,
  activity, meter checks, context tags, meal response); AI (food photo and barcode, insights, Copy for AI, MCP);
  predictive "heading low"; phone calls on a low; caregiver features (several people, Care Board, scopes, pause,
  time-limited access, "I'm on it", check-in receipts, access log); gamification (streaks, a mascot, a meal guessing
  game); a full growth machine (onboarding funnel, paywall, review prompts, partner program, 40 SEO posts, calculators,
  status page).
- **Where Sukrara leads**: it reads the sensor itself and alerts **without internet or any server**; sharing is
  end-to-end encrypted with no account, nearby over Bluetooth; the Mac **alerts**; alert rules are deeper (night rules,
  waits near a threshold, Remind less, treatments that quiet alerts, escalation when the wearer doesn't answer) and a
  very low rings on the first reading (theirs waited for the third); glucose **and treatments** go to LibreView,
  Tidepool and Nightscout, a local Nightscout / xDrip+ API, insulin pens; the live notification stays on the lock screen
  (theirs vanishes where silent notifications are hidden); free and open source.
- **Their architecture's limits** are also their weak spots: no alerts when their server, the maker's cloud or the
  phone's internet fails; Apple Health and Health Connect sources get no alerts; companions never alert on their own (a
  watch at most shows the phone's notifications); sharing controls that failed silently in the test (pause, the access
  log). They have planned for Abbott ending LibreLinkUp (a ready page, and an Android bridge from Juggluco and xDrip+
  built into 2.7.0 but switched off by a server flag).

### The owner's questions

- **Are treatments saved, and does insulin reach LibreView or Nightscout?** Saved on their server only (the web LogBook
  within seconds, the PDF, Copy for AI, the AI connector), *seen*. Not LibreView: round 1's entries are not in the
  owner's export. Not Nightscout: in round 3 every request to the test site was a read, and a meal with 2 u logged in
  the app sent nothing; the site's boluses, carbs, notes and exercise didn't come in either, only its sensor start
  ([Where entries go](#logbook)).
- **Can it really read Juggluco or xDrip+?** Not today. 2.7.0 holds a receiver for both apps' standard glucose
  broadcasts that would forward each reading to their server (*read*), but its source row shows only when a server flag
  is on, and it is off: neither account's chooser offered it, and broadcasts sent to the app were taken in and dropped
  (*seen*). Their Libre-shutdown page sends Android users through Juggluco → Nightscout instead
  ([Phone bridge](#sources-and-sensor)).
- **Was all of sharing checked?** Nearly all, live, on two accounts in both directions: invites (every mode, length
  and scope), joining by link and by code, the invite's web page, approval, scopes, adding to the LogBook and stopping
  it, pause, time limits, the access log, stopping from either side, relations and nicknames, the test alert, real
  follower alerts, *I'm on it*, following while sharing, and an account deleted while linked. Not reached: the Care
  Board and the person switcher (two people followed), "Mom is responding" (two followers), a per-follower unit and
  language (no control in 2.7.0), the Family plan, the follower's quiet hours ([Care Circle](#care-circle-sharing),
  [Not reached](#not-reached)).
- **Free state and paywall?** Every alert, sharing, the LogBook and 14 days of stats are free; glanceables, watches, AI,
  PDF, 30 / 90 days and Call for Low are Premium, behind one paywall with a 7-day trial on the yearly plan
  ([Free plan and paywalls](#free-plan-and-paywalls)).
- **The other surfaces?** **Wear OS** *seen* on an emulator watch paired with the phone: a glance-only app, tile and
  complications, no alerts of its own. **Apple Watch** *read* in the bundle and *seen* in the App Store's nine watch
  screenshots, not run. **CarPlay** *read* in the bundle only. **Alexa** from the skill's store page (skipped by the
  owner, no Echo linked). **Safari's extension**, **Mac widgets** and **Windows** *seen*, used on the Mac and in a VM
  ([Platforms](#platforms)).
- **Did the Live Update chip and the lock screen work?** *Seen* on the owner's Pixel 10 Pro: the System style is a real
  promoted Live Update, with the chip "153 →" beside the clock. The lock screen and always-on display don't show it on
  the owner's phone: its channel is silent and the phone hides silent notifications there; with that setting changed it
  showed ([Live Updates](#glanceables-on-the-phone)).

## Platforms

| Surface | What it shows and does | Plan | Sukrara counterpart | Source |
|---|---|---|---|---|
| iPhone (iOS 18.6+) | The full app: sources, alerts, Care Circle, LogBook, stats, AI, widgets, Live Activity, Siri, CarPlay. The bundle (`Sugar Tracker`) holds the app with a CarPlay scene, a WidgetKit extension (widgets, Live Activity), a notification service (draws the contact photo, clears alert cards), the Watch app and its complications; 2,653 strings in 7 languages. On Apple Silicon Macs it runs as a fixed 1024×800 window, and its widgets and 4 Shortcuts actions work in macOS | Free + Premium | No iPhone app | `ios`, `ios-bundle/`, `iphone-on-mac/` |
| Android (min SDK 26) | The same as the iPhone, plus Live Updates, a floating capsule, Health Connect, voice alerts in the background, a Juggluco / xDrip+ bridge (built, switched off); 5K+ installs. No reading database: the last 3 h in one preferences file, the rest fetched | Free + Premium | The Android app | `play`, `android/`, `phone`, `android-r3` |
| Apple Watch | *Read* in the bundle, not run: app opening on a chosen face (Character, Big Number, Gauge, Halo), swipe for the chart, the Digital Crown switches 3 / 6 / 12 / 24 h; complications (Big Number, Graph) with the reading's age, grey after 30 min ("Apple refreshes complications about every 15-20 minutes"); the Live Activity in the Smart Stack; wrist alerts; Watch Siri; the person followed on the wrist; *Sync Now* pushes unit, limits and readings; "glucose as a contact photo" for any face. *Seen* in the App Store's nine watch screenshots: Character (the background in the zone colour, the mascot, a range bar), Gauge (a ring in the zone colour on black) and the chart page ("3H", axis 0–200 or 0–300), the reading's age top left, a Sync button on every page, five page dots (Character first, chart second, Gauge fifth). No screenshot shows Big Number, Halo, complications, an alert or the follower view | Premium | No | `ios`, `ios-bundle/`, `apple-watch/store-watch-01…09` |
| Wear OS | *Seen* on an emulator watch paired with the phone emulator. Installed from the watch's Play Store ("Requires mobile app"), it opens straight on the glucose: no sign-in, setup or permission. Two pages, nothing tappable, no settings: the face (Character, Big Number, Gauge or Halo, picked on the phone and applied in ~2 s: the reading's age counting up, value, arrow, 5-min change, a range bar) and a chart (the crown switches 3 / 6 / 12 / 24 h, but only ~70 min ever shows: the phone sends the newest 72 points). A tile (value, arrow, change since the previous reading, sparkline, age; opens the app); complications from one source (value, age, an arc, no arrow; text and image kinds in the code). Updated on each push, 20 s to 2.5 min after the reading, also over Google's cloud route when Bluetooth is gone; opening the app asks the phone for 3 h. **No alerts on the watch**: at 76 ↓ the face stayed "IN RANGE" while the phone alerted *rapidly dropping*; at the low it turned orange, nothing buzzed (the phone's notifications should mirror to a real paired watch: not seen). A follower's watch would show the person followed with no name; free: "Open Sugar Sense on your phone and subscribe" (both *read*) | Premium | No watch app; with *Separate alert notifications* on, each alert is a notification of its own, which Wear OS mirrors ([Alerts](FEATURES.md#alerts)) | `play`, `wear`, `wear/wear-10…29`, `android/int-01…06` |
| CarPlay | A Driving Task list: the reading drawn as an image, its age, in range or not, the last hour (the strings also say a 3 h graph), a note while alerts are paused, Refresh; "No Data" past 15 min; **the phone's alerts on the car screen** (low and heading low, or all five kinds, with snooze); **readings spoken** on leaving range or at every reading; the Live Activity on the CarPlay dashboard; calendar sync as the fallback for cars without it | Premium | No | `ios`, `ios-bundle/` |
| Android Auto | Built (car-app library, strings, a Settings row) but **not shipped**: no car service in the 2.7.0 manifest, its remote flag off; one review lists it (unverified) | — | No (planned) | `android` decompiled, `phone`, `ds` |
| Web dashboard (PWA) | Home, Statistics, Insights, LogBook (add, edit; the phone's entries within seconds), Care Circle, a bedside full-screen view, printable report, pairing and AI-consent pages for every companion, partner dashboard; "Alerts and calls run on your phone, not in the browser" | Free | No | `web`, `web/` |
| Mac menu bar (macOS 14+) | Value + arrow (capsule or plain), optional change and minutes; dropdown with 3–24 h graph and window stats; ⌃⌥G; one widget, *Glucose* S / M / L, filled with the zone colour; followee switcher; the Safari extension inside; 30 s refresh; **no notifications**, no Shortcuts actions. The iPhone app on the same Mac adds its own widgets (ring S–XL, graph, number, stats, buddy; live, Premium) | Free | The Mac app (menu bar, panel, widgets, **alarms**, treatments, report) | `mac`, `mac-menubar/`, `mac-widgets/` |
| Windows tray (Windows 10 1809+, x64 and Arm64) | A tray badge (zone-coloured square, coloured digits, or high contrast); a floating always-on-top capsule (on by default; S / M / L, 100 / 80 / 60 % opacity, drag anywhere, the taskbar included, hidden in full screen); a click-open panel with a 3–24 h graph, hover values and window stats; a right-click menu holding every setting; Ctrl+Alt+G (or Alt+Shift+G, Ctrl+Alt+S, off); starts with Windows; 7 languages; 30 s poll; **no window, notifications, logging or widgets**; Store updates only | Free | No | `win`, `win-app`, `windows/` |
| Browser extension (Chrome, Edge, Firefox 1.2.1; Safari 1.1.0 inside the Mac app) | Badge or big-number icon, popup graph 3–24 h with window stats, followee switcher, Alt+Shift+G, appearance Auto / Light / Dark; 30 s poll | Free | No | `cws`, `edge`, `amo`, `ext`, `safari/` |
| Garmin (3 Connect IQ apps, 1.4.4) | **Widget**: a glance (value, change, age, mini trend) and a full view (3 h graph with the limits); publishes a complication for other faces. **Watch face**: the value largest, change, age, a 3 h line, a bottom row (steps, heart rate, recovery, battery: 6 choices), background black / dark grey / light, glucose in always-on. **Data field**: records glucose (with min / max / average, or alone) into the activity's FIT file. Settings in the Connect IQ app: graph window 1–24 h, change, age, refresh every reading (5 min) or every other (10 min), slower in Do Not Disturb. The watch fetches from their server through Garmin Connect on the phone, every 5 min per app; each app pairs on its own; the face says "Alerts stay on the phone" | Premium (free to install) | Sukrara Aura face (free), xDrip+-made faces and fields through Sukrara's xDrip+ server ([Garmin watch face](FEATURES.md#garmin-watch-face-sukrara-aura)) | `ciqw`, `ciqf`, `ciqd`, `garmin`, `garmin/` |
| Amazon Alexa (invocation "sugar sense"; English AU / CA / GB / IN / US, French CA / FR, German, Spanish ES / MX / US) | "open", "what's my glucose" (value in the app's unit, trend, change, in range or not, age), "how was my night" (overnight time in range, the lowest with its time), "how is my day" (last 24 h: time in range, average, lowest, highest), "how is {name}", "how is everyone"; over 15 min old it says so, over 2 h it never speaks a number; Echo Show: number, arrow, change, zone, a 3 h graph with the target lines on a background coloured by range; a home-screen widget on Echo Show 8 / 10 / 11 / 15 / 21, grey after 15 min; read-only, never alerts; a one-time account link (one tap from the app); rated Guidance Suggested, 0 reviews | Free voice, widget Premium | No | `skill`, `alexa`, `android/int-20…22` |
| Siri and Shortcuts (iOS; the actions also on a Mac with the iPhone app) | *Check My Glucose* (spoken value, direction, age), *How Was My Night* (the Morning Report), *Get Glucose Reading* (returns value, unit, trend, zone, time, minutes ago, freshness, person and a spoken summary; nothing older than 2 h; ran on the Mac: "209 mg/dL → High, 3 minutes ago"), *Refresh Glucose* for widgets; 8 phrases each; answers from the cached reading, offline too, "out of date" after ~15 min | Free (Watch Siri Premium) | No; *Read glucose aloud* ([Read glucose aloud](FEATURES.md#read-glucose-aloud)) | `ios`, `ios-bundle/` |
| AI assistants (MCP) | A read-only OAuth connector, 7 tools; connected here to Claude Code and to ChatGPT (on its free plan, as a custom server: Sugar Sense isn't in ChatGPT's directory); listed in Claude's directory as a Community plugin; guides for Gemini, Grok, Cursor too | Premium | No (planned) | `ai`, `ai/`, `cdir`, `mcpblog` |
| Home Assistant (HACS default since 2026-11-16) | 14 entities (glucose, trend, change, zone, in range, last reading, TIR today, alerts paused, sensor day / end, 4 limits), 60 s poll, 3 blueprints (lamp colour by zone, night light out of range, speak a low once); no options, no alerts | Free (code still has a Premium state) | No (planned) | `ha`, `gh-ha`, `home-assistant/` |
| DIY bedside display (ESP32-S3, 3.5", ~$25) | Value, arrow, change, age, graph; scenes Color / Night / Dark / Auto; bedside clock; up to 4 people; TIR and sensor days; web-USB installer; ≤ 5 per account; open-source firmware | Free | No | `gh-disp` |

## Features

**Plan**: F free, P Premium. **Sukrara today**: Yes / Partly / No, with the [FEATURES.md](FEATURES.md) section.

### Sources and sensor

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| FreeStyle Libre (LibreLinkUp) | The person's LibreLinkUp email and password; the server polls every 2–3 min and pushes at once (measured on the owner's phone: readings 2–3 min apart, each on the phone 21–24 s after its LibreLinkUp time; the assistant instructions call 1–4 min the Libre cadence); errors in plain words ("No patients shared with that account", re-accept Abbott's terms); Libre 3 → 3 Plus needs no reconnect | F | all (server) | **Yes**, on the device, about 10 s late, Android and Mac ([LibreLinkUp as a source](FEATURES.md#librelinkup-as-a-source-both-apps)); also the Libre 3 read directly. Side by side on the owner's phone Sugar Sense was at times a reading behind (153 → while Sukrara showed 157 ↗; 159 / 161; 162 / 162) | `libre`, `5kr1`, `phone` |
| Dexcom (Share) | The sharer's login (username, email or phone with country code), region US / APAC / worldwide (US picked by itself since 2.4, Japan / Singapore since iOS 2.6); needs ≥ 1 follower in Dexcom's app. Dexcom ONE: the store says ONE+ works, two reviews say ONE doesn't (unverified) | F | all | **No** (planned, [44](44-external-sources.md)); Dexcom sensors read directly, untested | `dex`, `ios`, `changelog` |
| Nightscout | URL (`http` upgraded to `https`, `/api/v1` and a trailing slash ignored) and one field for the API secret or a token (≥ 12 characters, so a site open to reading still needs some text there); public https only. Their server (DigitalOcean) sends the credential as the `api-secret` header; on connecting it reads the site's auth, the newest reading, up to 288 readings since a time, and the newest *Sensor Start* / *Sensor Change* treatment (the sensor's age); then only new readings, **every ~10–35 s** (every few seconds while one is late), each fetched 2–30 s after it lands, and the sensor treatment about hourly. It never writes and reads no other treatment; a read-only token is enough (*seen* in the test sites' request logs). Past 15 min without readings: a Home banner and a sheet with the checks (address, secret or token), their troubleshooting guide and their Facebook group | F | all | **Yes**, treatments both ways; asks again right after the next reading is due ([Nightscout as a source](FEATURES.md#nightscout-as-a-source-both-apps)) | `ns`, `android-r3`, `android/r3-st-01`, `-02` |
| Apple Health / Health Connect as the source | For meters and over-the-counter sensors; readings are uploaded but get **no server alerts or calls**; a home banner says so | F | phones | **No** (Sukrara writes glucose to Health Connect only) | `priv`, `android` |
| Manual entry | No sensor: typed readings (mg/dL or mmol/L; onboarding asks for the latest one), graphs, stats, LogBook, no alerts. Home: "Manual mode: we watch only the readings you log. No overnight alerts.", a 24 h chart, no Live Updates or Snooze buttons. Settings drops Call for low, Voice alerts, Live Updates, Widgets, Care Circle and Wear OS, but keeps the whole alert list; Add offers *Add a glucose reading* instead of Meter check; a reading isn't a LogBook row; a typed low gets advice ("Treat it right away and recheck in about 15 minutes"); such an account can't share ("no live sensor for a follower to be alerted on") | F | phones, web | **No** | `web`, `android/r2-free-01…40` |
| Phone bridge (Juggluco / xDrip+) | **Built, not available in 2.7.0.** A receiver for Juggluco's and xDrip+'s standard glucose broadcasts (value, time, rate or slope, serial or source; kept when 20–600 mg/dL and timed from 24 h ago to 5 min ahead; the arrow from the rate: ≥ 2 ↑, ≥ 1 ↗) that, with the bridge as the source, forwards each reading to the server, so server alerts, watches and followers keep working; a setup page ("In Juggluco open Settings, then Exchange data, turn on Glucodata broadcast and add this app", the last reading received, *Use as my glucose source*) (*read*). The row shows only when the remote flag `phoneBridgeEnabledAndroid` is on: neither account's chooser had it, also after broadcasts arrived, and those were taken in and dropped (*seen*). Round 1 listed the row from the strings; no user can pick it today | F (off) | Android | **Yes**, Juggluco's local web server read on the device ([Source](FEATURES.md#source-whose-glucose-and-where-it-comes-from-both-apps)) | `android` decompiled, `android/r3-ns-03-chooser`, `-ns-08-chooser-after-bridge-broadcast` |
| Change source | An intro dialog, then a chooser: Libre / Dexcom / Nightscout / Health Connect / Manual; the switch happens on the server. *Seen* switching both accounts to Nightscout: the earlier history stays, the new source is read only from the newest stored reading on (the site's earlier 3 h never came in), and the first new reading shows a jump in the change (+1.3 mmol/L); after the second account switched from Manual, its follow of the owner had gone from its own side only (re-inviting fixed it) | F | phones | **Yes**, Settings › Source, whose glucose × source; a Nightscout site read 24 h back | `android/settings-11`, `android/r3-ns-02…09` |
| Sensor age | "Day 3 of 15" with a progress bar, start, end, serial with Copy; "Remind me before it ends" ("The morning of the last day and at expiry. Not an alert."), Show on Home; **Sensor support**: Request a replacement, call Abbott support (the number picked by country and kept on the phone), the Libre 3 safety notice; Dexcom: a sensor-code scanner and a start set by hand; Nightscout: the newest *Sensor Change* / *Sensor Start*, read about hourly, with a 10-day life assumed ("Nightscout · Day 3 of 10"; one 5 days old wasn't found, outside Nightscout's default window) | F | phones | **Yes**, deeper (start and length when the source doesn't say, a new-sensor flow, the end in the calendar); **no** maker support links ([Sensor life](FEATURES.md#sensor-life), [Sensor reminders and countdowns](FEATURES.md#sensor-reminders-and-countdowns)) | `android/settings-10`, `ios`, `phone`, `android/r3-tr-06-e1-sensor-day3of10` |
| Provider outage | Home banner "{provider} outage: not your phone or sensor" (confirmed / suspected), from their own monitoring (banner texts in Firestore); a public status page | F | phones, web | **Partly**: LibreLinkUp maintenance says until when, from Abbott's status page ([LibreLinkUp as a source](FEATURES.md#librelinkup-as-a-source-both-apps)) | `web`, `status`, `phone` |
| Not supported | Stelo, Lingo (closed platforms) | — | — | Stelo read directly, untested | `cmp` |

### Glucose on screen

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Zones | In range / warning / alert from four limits (defaults 55 / 70 / 180 / 250); a reading **on** a limit is out of range; green `#65B741`, orange `#FD8D38`, red `#FF6868`, stale grey; number + arrow + a word, never colour alone | F | all | **Yes**, six ranges (adds extremely high, 300) and words ([Graph and value card](FEATURES.md#graph-and-value-card)) | `ext`, `a11y` |
| Staleness | > 15 min: grey with the reading's clock time; > 2 h: "No Data" / "--"; the same on every surface (Home Assistant entities go unavailable at 15 min; Alexa and Siri say the reading is old, and Alexa never speaks a number past 2 h), except the watch complications, grey after 30 min | F | all | **Yes** ([Freshness](FEATURES.md#freshness-every-glucose-surface)) | `ext`, `mac`, `ha`, `skill`, `win-app`, `ios-bundle/` |
| Change (delta) | Against the newest reading ≥ 270 s older within 20 min, so 1-minute Libre streams don't jitter; "0" when flat; one test fixture shared by iOS, Android, web, Home Assistant, display and extension (Windows drops it past 20 min too); a toggle | F | all | **No** change number: the card shows the trend arrow worked out from the readings ([Trend arrow](FEATURES.md#trend-arrow)) | `ext`, `gh-ha`, `win-app` |
| Arrow | The source's five codes (↓ ↘ → ↗ ↑); unknown → flat; a blank one stays blank | F | all | **Yes**, from the readings (Quick / Balanced / Smooth) or the sensor's | `ext` |
| Age counter | A live mm:ss counter since the reading on the hero card, the web tab title shows the value ("131 → · Sugar Sense") | F | phones, web | **Partly**: time since the reading once late ([Freshness](FEATURES.md#freshness-every-glucose-surface)) | `android/home-01-top`, `web/home-01` |
| Home | Greeting with name and avatar; round icons for Morning Report, Sense Check, streak (each can be hidden); hero card filled with the zone colour (Refresh, Full screen); graph card; TIR card; AI insight card; big **Live Updates** (or Live Activity) and **Snooze** buttons; bottom bar Home · Statistics · (+) · Logbook · Settings | F | phones, web | **Yes**, a different layout ([Home screen](FEATURES.md#home-screen)) | `android/home-01-top` |
| Graph | Day navigator with a calendar, a past day shows "Full day", windows 3 / 6 / 12 / 24 h (the phone's list is editable: add 1–24 h, swipe to remove), dashed limit lines, the line coloured per zone; **drag to scrub** with a tooltip and haptic ticks (hover on Windows); event chips, stacked with a count when close, open a popup with edit / delete | F | phones, web | **Partly**: scroll back in time and pinch to zoom (Android panel, Mac dropdown), treatment markers and labels; no day picker, no scrub tooltip ([Graph and value card](FEATURES.md#graph-and-value-card)) | `android/home-16…22`, `home-30`, `android/r2-log-05`, `windows/flyout-06-graph-hover-24h` |
| Full screen | **Day** (the zone colour fills the screen, clock, 3 h sparkline), **Night** (black, big clock, Dim), **Car** (the number mirrored for a windscreen reflection, Mirror toggle), **Speak**; the web has **Big** instead of Car; "Tap for options. Swipe down to close." | F | phones, web | **No** | `android/home-09…14`, `web/fullscreen-*` |
| Unit | mg/dL ↔ mmol/L (× 0.0555, one decimal), on every surface; a follower sees the wearer's unit | F | all | **Yes** ([Glucose unit](FEATURES.md#glucose-unit-mgdl-or-mmoll)) | `ext`, `android/r2-share-47` |
| Dark mode | On / Off / Auto in the app; the web cycles Auto → Dark → Light; Windows follows the system | F | all | Follows the system | `android/settings-47` |

### Glanceables on the phone

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Home-screen widgets | Android: number 1×1, ring 2×2, buddy 2×2 (the mascot), graph 4×2 and stats 4×2 (3 h in range, lowest, highest), the wide ones with "+" opening the Add sheet; pinned from inside the app; iOS: the same five, Lock Screen (circular, rectangular, inline) and StandBy, the ring and "+" interactive. **Redrawn by server pushes** (iOS: a WidgetKit push token the server uses); a local alarm greys them 15 min after the reading. Locked: "Glucose on your home screen is Premium · Tap to unlock"; a follower's: "Unlock in Sugar Sense to follow on your widget" (Family); manual: "Open Sugar Sense to log". The iOS widgets also run on a Mac | P | phones, Mac | **Yes**, free ([Home screen widgets](FEATURES.md#home-screen-widgets), [Lock screen widget](FEATURES.md#lock-screen-widget) with root, [Mac widgets](FEATURES.md#mac-widgets)) | `android/widgets-01…10`, `ios-bundle/`, `mac-widgets/` |
| Live Updates (Android) | An ongoing notification in five styles. **System**: a real promoted Live Update (seen on a Pixel 10 Pro, Android 17): progress style with five zone segments and a drop at the value, title "153 → +4 mg/dL", "In range" / "High", the zone colour as accent, a counter from the reading, public on the lock screen, and the **status-bar chip** "153 →" beside the clock whenever the app isn't in front (also Samsung's Now Bar); where promotion isn't allowed, a custom view. **Minimal, Focus, Graph** (a ~80 min chart when expanded) and **Character** (the mascot): custom views, never promoted, in the shade's *Silent* section. The status-bar icon is the number drawn as a bitmap. The channel is low importance, so **on a phone that hides silent notifications on the lock screen (the owner's) it is on neither the lock screen nor the always-on display**, promoted or not; with them shown, a monochrome card (value, zone, counter, bar). **When to show**: Always / Only during lows / During lows and highs (the server starts and ends the episode; removed 30 min after back in range, 3 h at most); switched to *Only during lows*, the card already up stayed for 10+ min in range | P | Android | **Yes**, free, always on, a Live Update chip, on the lock screen also with silent notifications hidden (a default-importance channel, #94) ([Live notification](FEATURES.md#live-notification), [Root status bar](FEATURES.md#root-status-bar-system-icons)); one style, no show-only-when-out-of-range | `android/settings-27…36`, `notif-01…03`, `phone/live-01…12`, `phone/lock-01…04` |
| Live Activity / Dynamic Island | Lock Screen styles Minimal, Compact, Focus, Graph, Bold, Character, the change on every style; the Dynamic Island built per side from Value, Arrow, Timer or Emoji (the emoji hides the number); started by a server push and restarted about every 7 h ("Restarted to stay up-to-date."); a ↻ heals a stale card; the same When to show; also in the watch's Smart Stack and on the CarPlay dashboard | P | iOS | No iPhone app | `widget`, `ios-bundle/`, `iphone-on-mac/settings-*` |
| Floating glucose | A capsule over other apps (S / M / L, opacity, arrow, change), drag anywhere, tap opens the app, long press: "Hide for 1 hour / Hide until tomorrow / Settings"; needs *Appear on top*. Windows has the same capsule, on by default | P | Android, Windows | **No** | `android/floating-01`, `-02`, `windows/pill-*` |
| Glucose as a contact photo | A contact "Sugar Sense" whose photo is the reading, redrawn by the notification service on each push and by a background task, so Apple's Contacts complication shows it on any watch face; grey after 15 min, "--" after 2 h; the contact is deleted when turned off | P | iOS | **No** | `ios`, `ios-bundle/`, `iphone-on-mac/settings-*` |
| Calendar sync | The latest reading mirrored into a private calendar ("A fallback for your Mac and cars without Live Activity support") | P | iOS (Android: built, off) | **Partly**: the sensor's end goes in the calendar, not readings ([Sensor end in calendar](FEATURES.md#sensor-end-in-calendar)) | `ios`, `ios-bundle/`, `android` decompiled |
| Value on the app icon, shortcuts | iOS badge with the value (off by default); Android launcher shortcuts "My glucose", "Log meal" | F | phones | **Partly**: *Read glucose aloud* shortcut ([Read glucose aloud](FEATURES.md#read-glucose-aloud)) | `iphone-on-mac/settings-*`, `android` decompiled |

### Alerts

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Kinds and defaults | Very low 55 (Critical, every 15 min, Classic Alarm), Low 70 (Time Sensitive, 30 min), High 180 (Regular, 2 h), Very high 250 (Time Sensitive, 30 min), Heading low (Time Sensitive, 30 min), Big drop (on) / Big rise (off) (Regular, 30 min, **no rate setting**), Missed readings after 20 min (Never / 5 / 10 / 15 / 20) with an optional escalation (30 / 45 / 60 min); Reset all to defaults. *Seen* on scripted Nightscout readings, one a minute: Low and High on the first reading past the line (180 itself counts), 31–36 s after it; **Very low only on the third reading in a row past its line** (3 min; 55 then 53 then up never alerted), Very high on the second ≥ 250; Big drop 55 s after the drop began; Missed readings at 19 min, the escalation at 30–31 min (the free account got only the escalation); a late batch of readings removed both cards within seconds; Very low repeated after 18 min | F | phones | **Yes**: 7 kinds incl. extremely high 300, rates at 2 mg/dL/min held 5 min, LO / HI; very low and extremely high ring on their first reading ([Alerts](FEATURES.md#alerts)) | `android/settings-12`, `alerts-10…36`, `android/r3-sc-*`, `android/r3-st-10` |
| Delivery | Alerts come in pushes of their own: on the owner's phone a real *Glucose high · 185 → (+13)* arrived ~21 s after its LibreLinkUp time, on the first reading over 180; from Nightscout, 23–55 s after the reading. The readings for the screen come every 1–4 min, so the app can trail its own alerts: during the test low it still showed 59 while the server had 55 and 53, until Refresh. A custom view on the kind's own channel, cleared by a tap; a new kind clears the card before it | F | phones | **Yes**, worked out on the device at each reading, as the live notification itself ([Live notification](FEATURES.md#live-notification)) | `phone`, `android/r3-sc-12-e1-after-call` |
| Per-alert page | Enable (the emergency ones confirm turning off: "you may not be alerted in an emergency"), threshold, **Priority** Regular / Time Sensitive / Critical with plain bullets, **Repeat** every reading / 15 / 30 min / 1 / 2 h / custom 1–120 min, **Tone** | F | phones | **Yes**: threshold, remind every, sound, volume ("like an alarm"), test | `android/alerts-10…26` |
| Tones | 22 in three length groups (short, medium, long: up to "Emergency Siren"), Silent, Vibration only, ▶ preview | F | phones | **Yes**, sound packs at discreet intensities, any phone sound | `android/alerts-24…26` |
| Heading low (predictive) | About 15–20 min ahead to the very-low line; on by default; no settings beyond on, priority, repeat, tone; computed on the server; "Now 95 ↘ may reach 55 mg/dL in ~15 min. Have fast-acting carbs ready." Users praise it ("saved me about 7 times"). *Seen* on a steady fall of 2 a minute from 113: it fired 23 s after the first falling reading, "Now 111 — may reach 55 mg/dL in ~9 min", the time taken from one pair of readings 16 s apart (55 came 28 min later); buttons 30 Min / 1 Hour / 2 Hours; the follower got "{name} may be heading low" the same second, without buttons; the card cleared itself at the low | F | phones | **No** (planned) | `pred`, `home`, `reviews`, `android/r3-sc-01…03` |
| Test this alert | A real push through the server: "Lock the phone. Sending in 3…", then "Delivered as Critical with the Classic Alarm tone" or a diagnosis (channel off / silent, Do Not Disturb, notifications off); rate-limited. Followers: "Test alert · This is a test. Real alerts about {name} will look and sound like this." (3 a day) | F | phones | **Partly**: play the sound and vibration, send a test alert ([Settings](FEATURES.md#settings)) | `android` strings, `android/r2-share-45` |
| Snooze | **Pause low & high** / **Pause everything**: 15 / 30 min, 1 / 2 h, custom (a −/+ stepper and chips 15–120); "How does it work?": very low and very high still ring under Pause low & high (*seen*), the emergency call and followers ring under both. From an alert: 30 Min / 1 Hour / 2 Hours; Home's Snooze button then shows the countdown, and one tap on it ends the snooze at once. A follower: Pause warnings / Pause everything, 15 min to 2 h, no custom | F | phones | **Yes**, per alert (Mute, Remind less, in the notification) and Mute all 30 min–8 h, until turned off, custom 5 min–24 h ([Alerts](FEATURES.md#alerts)) | `android/home-26…28`, `android/r2-share-88`, `android/r3-sn-01…05` |
| Quiet hours | A list; each: days (default Mon–Fri), 9:00–17:00 by default, mode **Softer alerts** (default; nothing overrides Silent) / **Pause low & high** (low, high, rapid change, heading low, missed readings; very low and very high still come) / Pause all (confirmed); may cross midnight; "End for today"; while on, Home's Snooze reads "Until 7:40 PM"; stored on the server and the phone. *Seen* under Pause low & high: a fall of 5 a minute raised no Big drop, but a minute after the end a held Big drop arrived about the last reading, by then 9 min old; deleting a schedule asks nothing | F | phones | **Partly**: Night alerts, a second set of rules 22:00–07:00 (very low always on); no per-day schedules | `android/alerts-01`, `-02`, `phone`, `android/r3-qh-01…08` |
| Self-tidying alerts | An alert clears itself back in range; a new one replaces older cards of its kind even with the app closed; a high ends a low's card (*seen* on Android: heading low cleared at the low, the low by a Big drop, the very low by a fast rise, missed readings by late readings) | F | phones | **Yes**: one at a time, the most severe wins, cleared once back by the margin | `ios`, `ios-bundle/` |
| Very low on the lock screen | Android: the code has a full-screen alarm activity (shows when locked, turns the screen on), but the very lows seen came as a Critical notification without it: through Do Not Disturb, alarm sound and usage, vibration, and the locked screen stayed dark (full-screen and Do Not Disturb access granted); iOS Critical Alerts (asked again in 2.7) | F | phones | **Yes**, alarm-volume channels and a full-screen alert | `android` decompiled, `android/r3-sc-24`, `-26` |
| Call for Low | A phone call (Twilio) at 50–70 mg/dL (default 60; 2.8–3.9 mmol/L) "the moment your glucose drops to it"; after several unanswered calls it calls an **emergency contact**; snooze and quiet hours don't stop it. Setup: your number with a country picker, the level, *Call my emergency contact* and its number; **neither number is checked** (no test call, no code). *Seen* with the owner's own number in both: the first call came 24 s after the first reading ≤ 60, from an Estonian number (+372 6692182), a synthetic voice: "Sugar Sense alert. Your glucose is low, 59. If you are okay, press 1 now. Otherwise we will alert your emergency contact."; 1 → "Thank you. Please treat your low glucose now. Goodbye." In a second low the call came with the very-low alert, 4 min after the first reading ≤ 60; unanswered, it called again 5 min later, then the emergency contact 9.5 min after that: "Emergency alert from Sugar Sense. Someone who added you as their emergency contact has low glucose, 49, and is not responding to calls. Please check on them now." (no name, no key to answer). No call shows in the app, the wearer's or a follower's, and followers aren't told. Free: a paywall headed "UNLOCK CALL FOR LOW" | P | phones | **No**; nearest: followers told when a very low gets no answer for 5 min ([Alerts](FEATURES.md#alerts)) | `android/settings-20`, `priv`, `phone`, `android/r3-cfl-01…24`, `android-r3` |
| Voice alerts | Speak **all new readings** or **only alerts**; Android in the background (text to speech, the app's language when a voice exists); iOS only while the app is open with auto-lock off (tip: Announce Notifications); CarPlay speaks on leaving range or every reading. A blind reviewer relies on it and asks for voice choice | F | phones | **Partly**: *Read glucose aloud* on demand, Android and Mac | `android/settings-21`, `reviews`, `ios-bundle/` |
| Alert reliability | "Keep alerts reliable": battery optimisation; Notifications, Full-screen alarm and Do Not Disturb access checks, each opening its system page; steps per phone maker (Samsung, Xiaomi, Huawei, OnePlus / Oppo / Realme, Vivo) | F | Android | **Yes**, with Allow and Turn off ([Blocked features](FEATURES.md#blocked-features-both-apps)) | `android/settings-41…44` |
| Medication reminders | Insulin (basal) or medication: name, time, days, units / dose, alert style, sound; exact alarms on the phone (a new phone shows *Allow exact reminders*), kept on that phone; the notification says "Time to take your {name}." without the dose, with ✓ I took it / Skip today / Remind in 1 h (a different amount in the app); *I took it* from the shade logs the dose at the reminder's time without opening the app; "Still pending: X"; two free, "+" for a third opens the paywall | F / P | phones | **No** (sensor reminders only) | `android/settings-22…26`, `notif-05`, `android/r2-log-27…31`, `r2-free-21…22` |
| Weekly insight | "Get this insight weekly · A gentle weekly note with your top pattern. No spam." | P | phones | **No** | `android/stats-*` |

### Care Circle (sharing)

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Care Circle page | "…They see your glucose, alerts and LogBook. They can add to your LogBook only if you allow it. Always free…"; Invite someone to follow you; People I follow › Follow someone (code or scan: "The person you follow can see when you last checked on them and whether alerts reach you."); My followers; *Share sleep and heart data* for the whole circle; *Stop everyone adding to my LogBook*; *Who saw my data*; a Family Plan card. A manual account has no Care Circle row and no Invite | F | phones, web | **Yes**: Settings › Share with followers ([Share with followers](FEATURES.md#share-with-followers-wearer)) | `android/r2-share-01`, `-14`, `-15`, `-100`, `web/care-*` |
| Invite | Three sheets in a row: **Share openly** / **Require my approval**; **Permanent / 48 hours / 1 week / Custom end date**; **Everything / Glucose and emergencies only / Everything, plus adding to LogBook**; then a one-time 8-character code with QR (72 h to redeem), Copy code, *Share invite* (the system sheet), what the invite grants in words, "Keep this open until they're in" (the sheet doesn't change when they join). An invite that allows adding always needs approval, even *Share openly* ("You'll approve them before they see anything.") | F | phones, web | **Yes**: invite link or QR, each request reviewed | `android/care-03`, `android/r2-share-02…05`, `-93`, `android/r3-care-02…07` |
| Join | The invite link (an App Link) opens straight to "Request sent · We've asked them to approve you…", with no name of who invited and no confirm step; or the code typed or scanned; a used code: "That invite is invalid, already used, or expired. Ask them to send a fresh one." A new follower's setup: "Following a family member? … You don't need your own sensor", the alert permission, *Try a test alert*, then the wearer's Home. Without the app, the invite page `api.sugarsense.io/api/v1/care/join?code=…` (*seen*, English, light or dark): "You've been invited to follow a loved one's glucose…", the code in large type with *Copy code*, *Open in Sugar Sense* (the `sugarsense://` scheme), both stores, "It works once and expires within 72 hours"; the page doesn't check the code (any value shows the same); with no code, "This invite link looks incomplete" and where to make a new one. The shorter `/care/join`, which the app also claims as an App Link, has no web page (a bare error) | F | phones, web | **Yes**, showing who invites with their picture, *Follow* / *Cancel*, and a match code when two devices tried the same link ([Following (Android)](FEATURES.md#following-android)) | `android/r2-share-10`, `-21`, `-41…46`, `-94`, `care-join`, `web/care-join-*` |
| Approval | A notification "New follow request · {name} asked to follow your glucose. Approve or decline in Care Circle." with no buttons; the row "Wants to follow you — tap to approve"; a sheet naming what the invite grants (adding to the LogBook) with Approve / Decline; open invites skip it | F | phones | **Yes**: the wearer allows or denies each device | `android/r2-share-11…14` |
| What followers see | A scope per follower, changeable later: Everything / Everything, without sleep and heart data / Glucose and emergencies only; history, stats and streaks, Insights, a sleep / heart card; the wearer's unit (a per-follower unit and language exist in the API, behind a flag) | F | phones, web | **Partly**: glucose, treatments and alerts; *Can add treatments* | `android/r2-share-31`, `priv` |
| The follower's app | Home is the wearer's: "Following · can add" (or "read-only") with their name and initials, their value, chart with entries, time in range, an AI card ("Unlock AI to see why 🔒" when free), Live Updates and Snooze; Settings gains *Alerts for {name}* and Wear OS; Statistics with export; the Care Circle lists who you follow with ••• *Alert settings / Rename / Relationship / Stop following*. Glucose-only: no entries or AI card, "Readings in Range 210 of 371" | F | phones, web | **Yes**: the follower's home, widgets and live notification show the wearer ([Following (Android)](FEATURES.md#following-android)) | `android/r2-share-47…59`, `-66`, `-97` |
| Caregiver logging | Followers with adding allowed write into the wearer's LogBook: "Add to {name}'s day" with the whole sheet (food scanning included), "For {name}" on the form, a first-save notice ("Your name, {name}, will appear on entries you add…"), then "Logged by {name} (Partner)" on both sides, with no notification to the wearer; a duplicate-insulin guard ("You logged 4 units at 12:10 · Add anyway?"); the wearer can stop one follower or everyone adding | F | phones, web | **Yes**: writers add treatments, *Sending… / Not sent · Retry*; a duplicate guard for pen doses ([Insulin pens](FEATURES.md#insulin-pens-docs29)) | `android/log-12`, `android/r2-share-61…65`, `-80`, `-86`, `-87`, `reviews` |
| Follower alerts | Per person, *Alerts for {name}*: **Wake me for emergencies** (off by default: urgent low and high through silent and Do Not Disturb; turning it on asks for Do Not Disturb access), *Send a test alert*, and under *Advanced*: Very low (Time Sensitive, every 30 min), Very high (Time Sensitive, no repeat setting), Heading low and Missed readings (every 30 min), quiet hours that hold heading-low and readings-stopped notices for one summary (urgent low and high always come through); the wearer's limits, no plain low / high or rapid-change alerts. *Seen* on the follower: heading low ("{name} may be heading low · {name}'s glucose is dropping (111 mg/dL)…") the same second as the wearer's; very low ("{name} is very low · … They may need help right now.") 32 s after the wearer's and very high 1 s after, both with *I'm on it*; "{name}'s readings stopped" at 15–19 min; "{name} is back in range" 12–14 min after; "Readings from {name} are back: 6.7" (the wearer's unit, no unit word). Nothing about lows, highs, fast changes or calls; {name} is the follower's private nickname | F | phones | **Yes**, differently: the follower runs the wearer's rules or its own (*Settings from*), every kind, and gets *Very low · no response* | `android/r2-share-52…55`, `web`, `priv`, `android/r3-sc-21…23`, `-st-11`, `android/r3-care-19…21` |
| "I'm on it" | A follower acknowledges a very low or very high; the strings say the other followers then see "Mom is responding" (*read*; with one follower, not seen). *Seen*: the alert turns into "You're on it" with Undo and goes after about a minute; Undo (used 21 s later) withdrew it but didn't bring the alert back. The wearer got "{name} saw your alert · {name} (Partner) saw your alert and is responding." 3 min later, and nothing on Home or in the Care Circle | F | phones | **No**; a follower's history says when the wearer answered a very low | `android` strings, `android/r3-ack-03…23` |
| Receipts to the wearer | "Checked on you today / this week" on the follower's row, "Hasn't been able to receive alerts since {date}"; the follower is told this is visible | F | phones, web | **Partly**: past followers with the date they left, who is nearby | `web`, `android/r2-share-14`, `-100` |
| Pause, time limits, stop | Pause sharing per follower: 2 hours / Until tomorrow at 08:00 / Custom, then "Keep emergency alerts / Pause everything" (it didn't take in the test: [bugs](#their-weak-spots)); access that ends ("Ends 23 Nov" on both sides); *Stop sharing with them* ends it at once, with no confirmation, and the follower sees "Following ended · You no longer have access to {name}'s glucose…"; the follower's *Stop following* asks first; access stops within about a minute | F | phones | **Partly**: remove a follower, sharing over the internet on / off; no pause or time limit | `android/r2-share-30…35`, `-90…92`, `-102`, `priv` |
| Who saw my data | "Who checked on you over the last 7 days, and any files they exported in the last 30 days. Sugar Sense shows when someone looked, never how many times."; AI assistants included; a 90-day audit kept on the server; never loaded in the test ("Couldn't load right now") | F | phones | **No** | `android/r2-share-82`, `priv` |
| Labels and nicknames | The follower picks the relation (Mom, Dad, Guardian, Partner, Sibling, Grandparent, Child, Friend, Caregiver, School nurse, Other), shown "on their follower list and in their notifications about you"; private nicknames (*Rename*); a new follower introduces itself with a name and a relation | F | phones, web | **Partly**: the wearer's name and picture, the follower's name | `android/r2-share-58`, `-59`, `-95`, `web` |
| Follower glanceables | A "mirror" Live Activity / Live Update, widgets and watch of the person followed (the same 5 styles, no capsule); a free follower, even of a Premium wearer, gets the **Sugar Sense Family** paywall | P | phones | **Yes**, free: a follower's notification and widgets show the wearer | `priv`, `android/r2-share-70…72` |
| Family plan | Premium for up to 6 people in the circle ($59.99 / yr; €64.99 / yr or €5.42 / mo on a Spanish device): live notification, watch, widgets, insights, AI scanning for everyone | P | phones | n/a (free) | `ios`, `android/care-05`, `android/r2-share-71` |
| Follow several people | Follow ≤ 10 people; ≤ 10 followers each; **Care Board** shows everyone at once (reorderable); every companion has a person switcher; the display shows up to 4 tiles; Alexa "how is everyone" (*claimed*, *read*). You can follow while you share; *seen* with one person each way, it is a side view: no switcher and no board; Settings › Care Circle › "Tap to view glucose" opens a bare card (name, "Following · read-only", value, arrow, unit, "Updated just now", Refresh; no chart or history), in the followed person's unit; alerts about them come as above | F | all | **No**: one person per device ([Source](FEATURES.md#source-whose-glucose-and-where-it-comes-from-both-apps)) | `web`, `gh-disp`, `alexa`, `android/r3-care-13…17` |

### LogBook

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Add to your day | One sheet: Meal, Scan food, My Foods, Insulin, Medication, Activity, Meter check, Add a note (web: six, no food features; a manual account: *Add a glucose reading* instead of Meter check; a follower: "Add to {name}'s day"); the FAB follows the active filter (Insulin filter → the insulin form) | F | phones, web | **Partly**: one form for rapid, long, carbs and a note, from home, widgets, the notification, alerts and a Quick Settings tile ([Treatments](FEATURES.md#treatments)) | `android/log-01`, `log-14`, `android/r2-log-01` |
| Meal | Name, carbs (optional), insulin short / long and units on the same form (2.7: saved as two entries at one time, one chip with a count on the chart), Save to My Foods, context tags, date and time (a clock dial; a back-dated entry takes the glucose of that moment); a half-filled form survives a restart | F | phones, web | **Partly**: carbs and insulin together, no meal names | `android/log-02`, `android/r2-log-02…09` |
| Insulin | Short / long-acting (none preselected), units (input capped), a name: long-acting offers Lantus / Levemir / Tresiba / Toujeo chips; time; "Sugar Sense never suggests a dose"; the name shows in the LogBook, popup and PDF | F | phones, web | **Yes**, no insulin names; NovoPen doses logged by a tap ([Insulin pens](FEATURES.md#insulin-pens-docs29)) | `android/log-12`, `android/r2-log-06` |
| Medication | Name, optional dose, unit (mg, mcg, g, mL, units, tablet, puff, drop, spray) | F | phones, web | **No** | `android/log-13`, `android/r2-log-10` |
| Activity | Duration (slider) and intensity light / medium / heavy, no sport or name; imported workouts carry the sport (28 kinds), distance, kcal, heart rate and the source app (Garmin Connect, through Health) | F | phones, web | **No** | `android/log-15`, `android/r2-log-11` |
| Meter check | A number wheel (default 100) and a note; the entry reads "Meter 148 mg/dL · Sensor 148 mg/dL →"; "It never changes the graph, alerts or statistics." | F | phones, web | **No** | `android/log-16`, `android/r2-log-12` |
| Note and tags | 140 characters; tags 🤒 Sick, 😰 Stress, 🍷 Alcohol, ✈️ Travel, 🌙 Late night, kept out of insights so unusual days don't skew them | F | phones (web: 4 tags) | **Partly**: notes, no tags | `android/log-17`, `android/r2-log-13` |
| Day view | Day with a calendar, filter chips with counts, day sums ("In Range 58% · Carbs 143 g · Insulin 25 u · Activity 27 m"), Morning / Afternoon groups; each row with the glucose at entry and its arrow; tap to edit (the same form); swipe to delete, confirmed, **no undo** | F | phones, web | **Partly**: 24 h on home and a 31-day Treatments page, glucose and trend at logging, swipe to delete with Undo | `android/log-10`, `log-30…31`, `android/r2-log-17`, `-18` |
| Meal response | Under each entry: "Peaked 154 mg/dL, +5 in 39 min · back in 14 min"; activity: "Dropped Δ to x" | F | phones, web | **No** | `android/log-10`, `android/r2-log-17` |
| Where entries go | **Their server only**: the web LogBook within seconds, the PDF, Copy for AI, the AI connector, Sense Check's ring; on iOS meals and insulin also go to Apple Health (since 2.3). **Not Health Connect** (glucose is the one record it writes), **not LibreView** (the first round's carbs, scanned meal, reminder insulin and note are absent from the owner's LibreView export taken hours later, which has no food or note record after 13 Nov and only another app's insulin), **not Nightscout** (no LibreView, Dexcom or Nightscout address in the app, no outside write among its requests; the server holds those logins "used only to read"). With a Nightscout source (*seen*): a meal with 2 u logged in the app caused no request to the site (every request was a read), and the site's Meal Bolus, Correction Bolus, Carb Correction, Note and Exercise never reached the LogBook or the chart: only *Sensor Start* / *Sensor Change* is read. Statistics and insights ignore entries (their patterns are glucose-only) | F | phones, web | **Yes**: insulin, carbs and notes to LibreView, Tidepool and Nightscout when turned on, treatments both ways with a Nightscout source ([LibreView upload](FEATURES.md#libreview-upload), [Tidepool upload](FEATURES.md#tidepool-upload-docs27), [Nightscout upload](FEATURES.md#nightscout-upload-docs25)) | `android/r2-*`, `web/r2-logbook-01-owner-entries`, `lv`, `changelog`, `android/r3-tr-04-e1-logbook` |
| Health imports | Apple Health: meals, insulin, workouts, sleep, resting HR, HRV in; glucose, carbs, insulin out (edits and deletes follow). Health Connect: the same in; **glucose out only** (from the server's graph, not while viewing someone followed) | F | phones | **Partly**: glucose out to Health Connect (carbs and insulin left out on purpose, [Not included](FEATURES.md#not-included)) | `priv`, `android` decompiled |

### Food and AI

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| My Foods | Recent / Saved tabs; a Recent row opens a filled Meal form (without its insulin); a Saved food opens a page with ½× / 1× / 2× portions, edit, one-tap *Log*, Remove and "How this affects you" ("Over your last n logs, X usually raised you about +Δ, peaking around t"; before that "Still learning your response"); a bookmark saves from Recent or a scan; **Your Usual** suggestions | F | phones | **No** | `android/log-19`, `-20`, `android/r2-log-19…23` |
| AI food scan | Full-screen camera with **Scan Food / Barcode**, flash, gallery, "Add a note" ("how it was cooked, how much you ate"). Result: name, "AI estimate · verify amounts", composition, meal timing, glycemic index, sugars, glycemic load ("High spike" + why), macros, recommendations (fewer slices, vegetables first, walk 15–20 min after), "Carbs to log: 120 g · Edit", **Log Meal**. The photo is analysed on their server by an AI model and not kept; barcodes are read on the phone and looked up in Open Food Facts, then USDA. The note counts ("I ate only half of it": "(half eaten)", 75 g instead of 120); the same photo three times gave 120 / 118 / 120 g and glycemic index 70 / 80 / 75. 3 free scans with no counter shown, then a 🔒 PRO badge | P | phones | **No** | `android/log-21…33`, `android/r2-log-24…25`, `r2-free-41…46`, `priv` |
| AI Insights | From ~14 days: an AI headline and paragraph, patterns with High / Moderate / Low confidence and "from n meals / nights", 👍 / 👎; a card on Home and Statistics; fallback "Showing your detected patterns"; free: the patterns, with "Unlock AI to see why 🔒"; an Anthropic Claude model on aggregated, de-identified statistics, dose advice filtered out "in code"; none mentioned the logged meals | P | phones, web | **No** | `android/ai-01`, `web/insights-01`, `founder`, `android/r2-share-66` |
| Morning Report | A sheet with the mascot, a title ("A few highs overnight") and a sentence written on the server, with advice ("drink some extra water today"); **Night in detail** (Premium): in range, lowest, highest, an overnight chart, the list of lows and highs; a moon chip all morning | F / P | phones | **No** | `android/home-02`, `iphone-on-mac/home-03` |
| Copy for AI | ~3 kB of text to paste into any chatbot: targets, 14-day stats, each day, the LogBook, last night, patterns, the last 24 h at one reading per 15 min; "Data only, no treatment advice"; a daily limit; free on every plan | F | phones, web | **No** | `ai`, `web/stats-*`, `android/r2-free-50` |
| MCP connector | An OAuth 2.1 server (dynamic registration, client metadata documents, PKCE, revocation, one read scope, 120 requests a minute). Consent on their web page: **whose data** (you and each person you follow), what it can read, "It cannot change anything in your account, receive your alerts or act for you". 7 read-only tools: profile (unit, limits, source, time zone, the sensor's day), the current reading (age, freshness, zone), readings ≤ 14 days per call (raw, 5 or 15 min), daily stats (completeness, GMI, CV, daily rows, AGP percentiles), the LogBook (the glucose at each entry, who logged it), the Morning Report (each episode), insights (confidence, sample size); instructions to the assistant (mg/dL, over 15 min isn't current, say whose data, never calculate doses). Claude Code answered "now", "last night", "last week" (flagging 25 % data) and meals with their rises, and declined dose advice; ChatGPT drew its own cards and a chart, then offered a form for the carb ratio and correction factor. *Connected assistants* with Disconnect (access ends at once; uninstalling the ChatGPT plugin also revoked it); shows in the followee's access log | P | all (server) | **No** (planned) | `ai`, `ai/`, `mcpblog`, `cdir`, `android/int-30…33` |

### Statistics and reports

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Statistics | Today / 7 / 14 / 30 / 90 days (30 and 90 Premium); TIR ring with readings count, % data and "most reliable over 14+ days at ≥70% data"; vs the previous period; 5-range breakdown; average + SD; GMI "est. A1C"; CV with Stable / Elevated; low and high events; lowest / highest with time | F / P | phones, web | **Yes**: 1 / 7 / 14 / 30 days, time in ranges, average, GMI, CV ([Home screen](FEATURES.md#home-screen) *Glucose report*, the Mac report window), and the [Metrics beside the value](FEATURES.md#metrics-beside-the-value) | `android/stats-*`, `web/stats-*`, `android/r2-free-30` |
| Daily Pattern (AGP) | Median, 25–75 % and 5–95 % bands, the target band, a spoken summary ("Median is highest around 1 AM at 252…") | P | phones, web | **No** (investigated in [26](26-agp-report.md)) | `android/stats-*` |
| PDF report | The phone builds a 3-page PDF (14 / 30 / 90 days) for the share sheet; the web prints a print stylesheet; "What We Noticed" with a tip per pattern; with *Include LogBook entries*, a page listing each day ("Insulin logged 25 u · Carbs 143 g · Activity 27 min · 1 medication · 1 note") and every entry with time, details, tags, glucose and **Logged by** (a reminder's dose without basal / bolus); a follower's print is logged; a free follower sees "PDF Report (Pro)", a manual account no PDF | P | phones, web | **No**: the CSV, and LibreView / Tidepool for the clinic | `android/stats-06`, `web/stats-04`, `-05`, `android/r2-stats-pdf-report-14d-with-logbook.pdf` |
| CSV | A glucose file (`SugarSense-Glucose-14-Days-<date>.csv`; contents not opened), 14 / 30 days, phones only, free for wearers and followers | F | phones | **Yes**, more periods, LibreView's layout ([Data export](FEATURES.md#data-export)) | `android/stats-06`, `android/r2-free-32`, `-33` |
| Health card | Sleep, resting HR, HRV, blood pressure, weight from Health Connect / Apple Health | F | phones | **No** | `android/stats-*` |

### Gamification

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Streaks | A day counts at ≥ 70 % in range; quiet days pause it, "an occasional day under goal is held for you", only a month without readings restarts it; last 7 days, longest, next milestone (3 days), "New record: n days!"; a flame on Home | F | phones, web | **Partly**: rewards on the rings (crown, laurel from 90 %, flowers from 95 %) ([Graph and value card](FEATURES.md#graph-and-value-card)) | `android/home-05`, `web/home-03` |
| Sense Check | A ring of 3 logged entries a day (then the icon turns into a check); after a meal: "Where will your glucose be two hours after your 11:32 AM meal?" (Lower / About the same / A bit higher / Much higher), revealed 2 h later ("You read it right"); "n of m guesses were close this month"; waits a few hours after a low | F | phones | **No** | `android/home-04`, `log-05` |
| Mascot | A blob whose mood follows the zone or the night, on the Morning Report, the Character Live Update, the buddy widget (on the Mac too), the watch faces; celebration lines ("🎉 In range today!", "New best") | F | phones, watches | **No** | `android/home-02`, `widgets-*`, `phone/live-08-character-shade` |

### Settings, account, help

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Account | Sign in with Apple, Google or email; three consents at sign-up (terms and privacy, the medical disclaimer, health-data processing; marketing email optional); the same account on every device; companions sign in through the web; raw readings pruned after ~365 days. **Delete account** (Settings › Terms & account, *seen* on the second account): one dialog, "This permanently deletes your Sugar Sense account and all of its data. Glucose syncing, alerts and emergency calls will stop. This cannot be undone."; no new sign-in, no export offer, no word on a subscription; 2 s later the Welcome screen; the server stopped reading its Nightscout site 21 s later; the owner's Care Circle lost both links (the person followed and the follower) with no notice | F | all | **No account**: a sharing identity with a recovery code; data on the device ([Share with followers](FEATURES.md#share-with-followers-wearer)) | `priv`, `del`, `android/r2-onb-12`, `android/r3-del-01…10` |
| Profile | Name (live check, ≥ 3 characters), avatars (memoji) | F | phones | **Yes**, name and picture | `android/home-06`, `-07` |
| Settings tree | Glucose source · Alerts (Notifications, Call for low, Voice, Reminders, Live Updates, Widgets, Alert reliability) · Display (hide Home cards, app lock) · General (unit, language, graph, dark mode) · Integrations · Support · Terms & account | F | phones | **Yes**, organised differently, with search ([Settings](FEATURES.md#settings)) | `android/settings-01…06` |
| App lock | Fingerprint / screen lock (Android), Touch ID (Mac) | F | phones | **No** | `android/settings-*` |
| Languages | 7: English, German, French, Spanish, Dutch, Estonian, Georgian (the Mac app lacks Estonian; Windows has all 7, its store page names 6); Alexa: English, French, German, Spanish | F | all | English, Spanish ([Languages](FEATURES.md#languages)) | `ios`, `mac`, `win-app`, `skill` |
| Accessibility | Not colour alone; screen-reader sentences on core screens ("121 milligrams per deciliter, steady, in range, just now."; Windows: the tray tooltip and the capsule's name "Sugar Sense: 147, steady, Updated just now"); reduced motion; Dynamic Type "still improving"; Windows' web-view sign-in shows UI Automation only nameless panes (its hand-painted panel's tree wasn't read) | F | all | **Yes**, one spoken sentence on every surface ([One card on every surface](FEATURES.md#one-card-on-every-surface)) | `a11y`, `android`, `win-app` |
| Support | FAQ (9–11 categories); Get in touch (a diagnostics file); copyable device id; **Suggest a feature** (category, title, text, "Tell me when this ships"); Windows: Help & Feedback (4,000 characters, the version attached); feedback relayed to a chat channel the team reads | F | phones, Windows | **Partly**: GitHub issues, the website | `android/support-01…09`, `windows/feedback-02-signed-in` |
| What's New, updates | Versioned cards configured remotely, in 7 languages (Android 1.0, 2.3.2, 2.7; iOS 2.0–2.7); soft and forced update sheets; Windows and the Garmin apps only through their stores | F | phones | **Partly**: the GitHub version's updater, release notes ([Updates](FEATURES.md#updates-android-github-version)) | `android/support-03…05`, `changelog` |
| Service emails | Setup checklist when no readings arrive, "sensor connection working again"; an opt-in newsletter; no glucose in emails | F | — | **No** (no account) | `priv` |

### Free plan and paywalls

| Who | Free | Premium (where the paywall showed) |
|---|---|---|
| Wearer with a sensor | Live readings, every alert (heading low, quiet hours, tests; in round 3 the free account got the missed-readings escalation but not the 20-min alert before it, cause not found), the Care Circle ("Always free": invite, approval, scopes, time limits, pause, adding, the access log, stopping), the LogBook with My Foods and Your Usual, 3 food scans, stats for today / 7 / 14 days, CSV, the Morning Report, Copy for AI, two reminders, Alexa voice, Siri, Health Connect / Apple Health, web, Mac, Windows, extensions, Home Assistant, the display | AI explanations, scans after 3, the AI connector ("🔒 Premium feature"), 30 / 90 days (lock badges) and the Daily Pattern, PDF, Night in detail, Call for low, a third reminder, widgets, Live Updates / Live Activity, the capsule, CarPlay, Apple Watch, Wear OS (the watch: "Open Sugar Sense on your phone and subscribe", *read*), Garmin (pairing still offered under a 🔒 PRO chip), the Echo Show widget, the contact photo, calendar sync |
| Free follower of a Premium wearer | Live glucose, chart, LogBook (by scope), stats 7 / 14 days, alerts and their settings, the test alert, snooze, CSV, adding entries, scanning food for the wearer | AI explanations, 30 / 90 days, PDF ("Pro"), Live Updates, widgets, the watch: the **Family** paywall |
| Manual (no sensor) | Typed readings, the LogBook, stats, CSV, reminders | The same gates; no sharing |

- **One paywall for every gate** (the Settings header card, 30 / 90 days, a third reminder, the fourth scan), with no
  word on what was tapped (except Call for Low's, headed "UNLOCK CALL FOR LOW", `android/r3-cfl-06`): "How your 7-day
  free trial works" (today full access, a reminder on day 6, charged on day 7), four benefits (AI meal scanning, AI
  insights, ChatGPT / Claude & more, watch + live notification + widgets), **Yearly** "Start for free" €41.99
  ("€3.50/mo", struck €119.88, "SAVE 65%") and **Monthly** €9.99, which has **no trial** ("Unlock Sugar Sense Pro");
  Restore, Terms, Privacy. Closing it the first time: "Start your 7-day free trial · No payment now · … We'll remind you
  before your free trial ends · Start my free week / I don't want it" (`android/r2-free-11…13`).
- Followers get *Sugar Sense Family* instead (€5.42/mo, €64.99/yr). The sensor path through onboarding ends on the
  paywall; the meter path ends on a Free vs Premium table ("Your safety net stays free, always. Premium is free for your
  first 7 days") with no paywall (`android/r2-onb-23`).
- The trial is held by the store's subscription service: during it the app's cached entitlement from their server read
  "free" (`phone`). Not seen: the winback offer, the creator paywall, buying Family.

### Growth and business

| Feature | What it does · how | Sukrara today | Source |
|---|---|---|---|
| Onboarding funnel | Welcome ("Catch the low before it catches you. Day or night."; Get started / I already have an account / Accept an invitation) → whose glucose → diabetes type → how you check → checks a day → worries → a count of yearly checks → the 20-min forecast promise → name → commitment → **how did you hear + creator code** (longer trial) → a "protection plan" → sign-in → source → notification priming → Health Connect → **paywall** with a trial timeline (reminder on day 6, charge on day 7) → summary; iOS tracking prompt; social proof configured remotely. A partner with type 2 on a meter: the copy turns to "they", "How did you hear" (with an inline creator code) comes early, "That's about 1,460 finger pricks a year" (4 a day assumed), "Here's your partner's Type 2 protection plan", the first reading, a notification ask for reminders, Health Connect and AI pitches, 4.9 stars and three 5-star reviews, and the Free vs Premium table instead of the paywall | Setup asks only what monitoring needs ([First-run setup (Android)](FEATURES.md#first-run-setup-android)) | `android/a-000…a-040`, `iphone-on-mac/onboarding-*`, `android/r2-onb-01…23` |
| Pricing and trial | Premium ("Pro"): $39.99 / yr or $9.99 / mo (€41.99 / yr on a Spanish device); Family $59.99 / yr; 7-day trial (yearly only), 14 with a partner code; subscribed on the phone only (a remote paywall through a subscription service), unlocked on every other surface; a trial-ending reminder; a winback offer ("WELCOME BACK · Your first year of Pro, n% off"); [Free plan and paywalls](#free-plan-and-paywalls) | Free, GPL | `home`, `partners`, `android/a-033`, `-036`, `android/r2-free-11…13` |
| Review prompts | Phone: a gate in the founder's voice ("Hi, I'm Irakli… I have type 1 diabetes myself"), "Something's not right" → feedback; desktop and extension: a card (Windows: "Enjoying Sugar Sense?", I love it → the Store, Could be better → feedback) after 2 days, 10 opens and a fresh in-range reading, 45-day cooldown, at most 3; store-review asks for the Garmin apps and Alexa | No | `android` strings, `ext`, `win-app` |
| Partner program | 30 % of net revenue for 12 months, PayPal monthly from $25, 30-day hold; link, code and QR; a dashboard (clicks, accounts, trials, subscriptions, balance); a brand kit; an application (channels, audience size, relation to diabetes, preferred code), reviewed by hand; an affiliate link for the display hardware | No | `partners`, `web/partners-01` |
| Website and SEO | ~40 long posts (many comparing Dexcom and Libre apps), 7 free tools (converter, A1C, TIR, GMI, fructosamine, a printable log, a glossary), `llms.txt`, "Ask AI about Sugar Sense" buttons, a reviews page with ratings refreshed every 12 h; Google Analytics and Ads behind a cookie banner; a sponsored review, founder-written press | The website with a Features page and the reports; no analytics | `home`, `llms`, `reviews`, `ds`, `5kr1` |
| Socials and community | Facebook page (~1.1K followers, the founder answers support in comments) and group, Instagram (~615), TikTok (3), LinkedIn (46), YouTube (3 videos, 13 subscribers); the founder on Reddit, X, Medium; in-app "Join our community" and "Share with contacts" | No | `fb`, `ig`, `tt`, `li`, `yt` |
| Help and videos | Three source troubleshooting guides, a Garmin guide, the in-app FAQ, setup pictures per step (2.7), a setup video configured remotely; 3 YouTube tutorials (0:52 overview, 2:02 Libre setup, a short) | The reports and FEATURES.md, the website | `libre`, `dex`, `ns`, `yt` |
| Status page | Their own page with JSON, refreshed every 60 s: App & API, Glucose Sync, Dexcom US, Dexcom outside US, Libre (no Nightscout) | No server to report on | `status` |
| Attribution and analytics | Firebase Analytics, Adjust, the Play install referrer, the advertising id | None | `priv`, `android` decompiled |

## How it's built

- **Cloud polling**: their servers hold the person's LibreLinkUp, Dexcom Share or Nightscout credentials (encrypted,
  AES-256-GCM) and poll them around the clock (LibreLinkUp every 2–3 min, from the readings' times; a Nightscout site
  every ~10–35 s, from its request log). The chain for one reading: sensor → the maker's app → the maker's cloud → Sugar
  Sense's server → push → phone, 21–24 s after the reading reached LibreLinkUp.
- **Server-side alerts**: low, high, rapid change, missed readings and the prediction are worked out on the server and
  sent by APNs / FCM in pushes of their own, with their priority, tone and channel (from LibreLinkUp, in the same second
  as the reading's push; from Nightscout, 23–55 s after the reading while the readings' pushes came every 1–4 min); the
  Android app has **no glucose logic** (none in the APK). Low and high alert on the first reading past the line, very
  high on the second, very low on the third; the very low goes out Critical (through Do Not Disturb, alarm usage) with
  no full-screen intent. Followers' copies, *I'm on it*, the phone call (two calls ~5 min apart, then the emergency
  contact) and quiet hours run there too; an alert held by quiet hours is sent when they end.
- **The phone** (the owner's Pixel, root): no reading database. One preferences file holds the last 72 readings (3 h),
  the profile (limits, each alert's settings, the emergency contact), the sensor (serial, start, length, the country's
  Abbott support), quiet hours, recent foods, the onboarding answers and the sign-in tokens; a Firestore cache holds
  only the home-banner texts; Remote Config the flags and What's New. FCM data pushes come every 1.5–3.5 min, each with
  the **last 12 readings**, so a lost push heals; the app draws from the payload and calls their API only on opening, on
  a setting change and in a 15-min fallback job (network required). Every push and wake also opens a new connection to
  the subscription service (~16 in 12 min) and tries the Wear data layer twice (failing when there is no watch: 930
  times since install). At start it probes the local ports instrumentation tools use (Frida's), beside Play Integrity.
- **Push-fed glanceables**: silent pushes carry up to 36 readings and an episode start / end, which redraw widgets, the
  Live Update and the floating capsule and show or hide them; a local alarm greys them 15 min after the reading. iOS:
  widgets through WidgetKit push tokens (since 2.2), the Live Activity started and ended by server pushes, the contact
  photo drawn by the notification service.
- **Read-only companions**: one JSON API (a function per call over HTTPS); the extension, Mac and Windows poll every
  30 s (Windows also on a network change, waking and a display change, with a 30-min full resync and a 24 h cache), the
  web, Home Assistant (server-tunable 30–900 s) and the display every 60 s. A snapshot says *ok / premium required /
  follower only / no source*.
- **Watches**: Garmin's apps call their server through **Garmin Connect on the phone** (one kept-alive HTTP/2
  connection), one background run per app every 5 min (~300 B up, ~750 B down), the Sugar Sense phone app not involved;
  the widget keeps running for its glance after the face is switched off. **Wear OS** (*read* in the watch APK, not
  obfuscated, and *seen*): on each push the phone app writes three data items to every connected watch: the glucose (the
  newest 72 points, unit, the four limits, colour style; ~3 KB of JSON), Premium on or off, the face style; the watch
  asks for a refresh with a message, which the phone serves at most once a minute with the last 3 h from their server.
  The watch app declares only vibration, wake lock and the Play licence check: no internet, no notifications, no
  sensors; its complications carry a timeline that turns them stale 30 min after the reading without waking the app;
  two more complication sources pack zone and arrow for an unpublished Sugar Sense watch face. The Apple Watch has a
  *Sync Now* from the iPhone (unit, limits, readings).
- **One pairing pattern for four devices**: the device shows a 6-character code (10 min) and a QR; the person confirms
  on the phone or a signed-in web page; the device polls and gets a read-only session token; unpairing revokes it
  (Garmin, each app on its own; Home Assistant; the display). The extension, Mac and Windows apps sign in on the web
  page and get a short-lived token handed over by extension messaging (Windows and the Mac reuse the extension's page,
  in an embedded web view). Signing out of a companion is local only, on purpose: a server logout would stop the CGM
  sync and the alerts.
- **Shared rules**: one fixture (the change rule) tested by iOS, Android, web, Home Assistant, the display and the
  extension; an open-source units package (npm and Ruby gem) with conversion, zones, TIR and GMI.
- **Remote control**: about 60 remote flags (each feature, a kill switch per source, forced and soft updates, What's
  New, the review-prompt numbers, onboarding social proof, Android Auto off, Samsung's Now Bar on, the phone bridge
  `phoneBridgeEnabledAndroid` off).
- **The LibreLinkUp shutdown plan**: a page ready at `app.sugarsense.io/app/libre` behind a flag (off on 2026-11-21):
  "Abbott has switched off the LibreLinkUp connection…"; Android → Juggluco → Nightscout (full alerts) or Health Connect
  (no alerts); iPhone → xDrip4iOS → Nightscout, a spare Android, or Dexcom; refunds for Pro; "building a direct bridge
  from Juggluco into the Android app" and "asking Abbott for official access". The bridge's code is in Android 2.7.0,
  switched off. No public sign that Abbott plans it (unverified); Abbott's status page lists a new "Libre Data Sharing
  API".
- **Stack**: native Kotlin + Jetpack Compose on Android (Hilt, Room, Glance, CameraX, WorkManager, ML Kit, Play
  Integrity); a native iOS app (WidgetKit, ActivityKit, CarPlay templates, App Intents; entitlements for Critical
  Alerts, Time Sensitive, HealthKit background delivery, CarPlay driving task); a React web app; on Windows **.NET 8
  WinForms, self-contained** (209 MB installed; every control hand-painted with GDI+, WebView2 only for sign-in, one
  MSIX per architecture built by CI, a hidden `--render <dir>` mode that writes every state × language × theme × scale
  to PNG: 160 files, their screenshot and visual-test harness); a Manifest V3 extension; Monkey C on Garmin; C on the
  ESP32 display; Python for Home Assistant. Back end: Node.js services, a scheduled worker and Redis on DigitalOcean
  (San Francisco); MongoDB Atlas on AWS us-west-1 (moved from Ireland on 2026-09-07 for latency); Cloudflare; Firebase
  (Auth, Analytics, Crashlytics, Remote Config, Firestore, Cloud Messaging). Processors: Adapty (subscriptions), Adjust
  (attribution), Twilio (calls), Anthropic Claude (AI), Postmark (email), Telegram (feedback), Open Food Facts and USDA
  (foods), Sentry (named in the privacy policy, not in the Android app).

| | Sugar Sense | Sukrara |
|---|---|---|
| Where glucose comes from | Their server polls the maker's cloud | The phone reads the sensor (Libre 3), Juggluco, or polls LibreLinkUp / Nightscout itself |
| Where alerts run | Their server, pushed | Each device, one rules spec with shared timeline scenarios on Android and Mac |
| Without internet | No alerts, no glanceables | The direct sensor, alerts and widgets keep working; followers nearby over Bluetooth |
| Delay | LibreLinkUp every 2–3 min, then 21–24 s to the phone; Nightscout read every 10–35 s, alerts 23–55 s after the reading, the screen's readings every 1–4 min | LibreLinkUp about 10 s; Nightscout asked again right after the next reading is due; the sensor every minute |
| Sharing | An account graph on their server | End-to-end encrypted over Nostr relays, or Bluetooth; no account, no server |
| Companions | Read-only, never alert | The Mac alerts; the Garmin face shows alert state |
| Watches | Garmin fetches from their server through Garmin Connect; Wear OS fed by the phone app (the newest 72 points), never alerting on the wrist | The Garmin face pulls from Sukrara on the phone, never the internet; alerts reach a Wear OS watch as separate notifications |
| Data | On their server (raw readings ~365 days); the phone keeps 3 h | On the device; export, import, uploads the person chooses |
| Outputs | Glucose to Health Connect and Apple Health; meals and insulin to Apple Health; nothing to LibreView or Nightscout | Glucose and treatments to LibreView, Tidepool, Nightscout; a local Nightscout / xDrip+ API; broadcasts |
| Changing the app's behaviour | Remote flags, any day | Releases |
| Business | Subscription (Premium, Family) | Free, GPL-3.0 |

## Where Sukrara differs

**They have, Sukrara doesn't:**
- Surfaces: iPhone, Apple Watch, CarPlay (alerts and spoken readings in the car), a Wear OS app (face, chart, tile,
  complications; no setup on the watch), Windows (tray badge, a capsule on the taskbar), browser extensions, web
  dashboard, Alexa, Siri and Shortcuts, Home Assistant, MCP (Claude, ChatGPT), a DIY display, a Garmin widget and a data
  field that records glucose into activities; full-screen Day / Night / Car views; a floating capsule; the glucose as a
  contact photo; readings in a calendar.
- Sources: Dexcom Share, Apple Health / Health Connect, manual entry.
- Alerts: heading low (predictive), a phone call with an emergency contact, voice on every reading, priority per
  alert, quiet hours by days, medication reminders that log the dose from the notification, a delivery diagnosis when
  testing an alert.
- Care: several people at once (Care Board, switchers), a scope per follower, approval optional per invite, time-limited
  access, pause with or without emergencies, "I'm on it" with a notice back to the wearer, check-in receipts, who saw my
  data, relations chosen by the follower, a first-save notice that the follower's name shows.
- Log and AI: meal names, insulin names, My Foods with portions and each food's response, Your Usual, medication,
  activity and imported workouts, meter checks, context tags, meal response, food photo and barcode scanning, AI
  insights, Morning Report, Copy for AI, the AI connector.
- Stats: 90 days, AGP, PDF with the LogBook, a health card (sleep, heart).
- Gamification: streaks, Sense Check, a mascot.
- Growth: the onboarding funnel, paywall and trial, review prompts, partner program, SEO content and tools, socials,
  a status page, 7 languages, an app lock.

**Sukrara has, they don't:**
- The sensor read directly (Libre 3; Dexcom, Accu-Chek SmartGuide, Aidex untested), alerts and glanceables without
  internet, before the first unlock after a restart ([Before the first unlock](FEATURES.md#before-the-first-unlock-android)).
- Sharing end-to-end encrypted with no account; followers nearby over Bluetooth; escalation to followers when a very
  low gets no answer; a join that shows who invites before anything is asked; the wearer told when a follower leaves.
- Alerts on the Mac; treatments on the Mac.
- Alert depth: extremely high, LO / HI, a very low that rings on its first reading, night rules, waits near a threshold,
  Remind less, a treatment that quiets the alert it answers, the badge as an action (*Eat carbs*), sound packs, rules
  checked by shared timeline scenarios.
- Outputs: glucose and treatments to LibreView, Tidepool and Nightscout, a local Nightscout / xDrip+ API, broadcasts
  to AAPS and other apps ([Share with apps on this phone](FEATURES.md#share-with-apps-on-this-phone-investigation-22));
  Import history.
- Insulin pens (NovoPen Echo Plus, priming told apart).
- The trend arrow worked out from the readings; configurable metrics and rings beside the value; the status-bar value
  as text with root; a lock-screen widget; the live notification on the lock screen and always-on display whatever the
  silent-notification setting; Undo on deletes.
- A guided new-sensor flow, sensor start and length when the source doesn't say, the sensor's end in the calendar.
- A free, open-source Garmin face fed from the phone; free widgets, Live Update and followers' glanceables; free and
  open source as a whole.

**Both, done differently:**

| Feature | Sugar Sense | Sukrara |
|---|---|---|
| Snooze | Two kinds (low & high / everything), quiet hours keep the emergency call and followers | Per alert in the notification (Mute, Remind less, Treatment) plus Mute all; per device, never sent to others |
| Quiet times | Quiet hours: days, hours and a softer mode | Night alerts: a full second set of rules for a nightly window |
| Ranges | 4 limits, a value on a limit is out of range | 6 ranges, low below its threshold, very low at or below, highs at or above |
| Following | Up to 10 people, a board, scopes, pause, time limits; approval optional; four kinds of alert at the wearer's limits | One person per device, the wearer's rules or its own, every kind; every device approved |
| Joining | The link goes straight to a request, nothing shown | The invite's name and picture, *Follow* / *Cancel*, a match code |
| No answer to a very low | Calls to the wearer's phone (two, ~5 min apart), then one to an emergency contact's number, ~14 min after the first unanswered call; nothing shown in either app | The followers' phones get *Very low · no response* after 5 min (5–30 min) with no answer on the wearer's phone; in the history on both sides |
| Nightscout as the source | Their server reads every 10–35 s, never writes; alerts reach the phone in 23–55 s | The phone (or Mac) reads the site itself right after a reading is due; treatments both ways |
| Caregiver logging | Into the LogBook, named, no notice to the wearer | Writers add treatments, with delivery state |
| Garmin | 3 apps, Premium; Garmin Connect fetches from their server every 5 min | 1 face, free; pulls from Sukrara on the phone every 5 min |
| Mac | A read-only menu bar glance and a widget | Menu bar, panel, widgets, alarms, treatments, report, read aloud |
| Live notification | 5 styles, one promoted (System, a chip); a silent channel, so gone from lock screens that hide silent notifications; automatic show / hide on lows | One style, always on, a Live Update chip, root text; on every lock screen |
| Report | Stats screen with AGP, PDF with the LogBook | Report card and window, CSV; the clinic through LibreView / Tidepool |
| Sensor end | Reminder on the last day and at expiry, Abbott support links | Reminders, countdowns, a Live Update and the calendar |

## Their weak spots

**What users say** (`reviews`, `play`, `ios`, `5kr1`, `5kr2`, `ds`, `fb`):
- Widgets lag or the chart doesn't update; widgets and CarPlay are Premium only.
- Garmin shouldn't need a subscription (free Connect IQ fields exist); "more smartwatches without several apps".
- No Lingo or Libre Sense; "pretty basic", "far fewer analytics" than Supersapiens for sport.
- Won't install on older iOS / macOS; Echo Show 21 widget trouble; the French Alexa invocation is awkward; a blind
  user wants voice choice.
- Caveats: the most detailed review is sponsored and another press piece is founder-written; store reviews are answered
  and curated; possibly duplicated review texts (unverified). No Reddit threads found. The Alexa skill has no reviews.

**Inconsistencies seen:**
- The streak rule reads differently on the web ("Quiet days… pause it, they never break it") and in the app (adds "an
  occasional day under goal is held for you, and only a month with no readings starts the count over").
- Home Assistant's "Time in range today" said 53 % while the app said 45 % at the same moment; the open-source TIR
  counts 70 < v < 180, against the consensus 70–180 inclusive.
- The Home Assistant README says the integration is free and lists linked instances on the web: the code still has a
  Premium state, and no page lists them.
- The web's AI guide says the in-app list of connected assistants "arrives with the next update"; the apps already
  have it.
- The Mac and Windows sign-in pages say "Connect the browser extension" (both reuse its page in an embedded web view,
  the password typed again), while the iPhone app uses the default browser's session. The Safari extension is 1.1.0,
  the others 1.2.1; the Mac app has no Estonian; Windows' store page names 6 languages, the app has 7.
- The app says Alexa answers in 5 languages, Dutch included; the skill's store page lists English, French, German and
  Spanish.
- Onboarding promises a carb **range** ("34 to 45 g"); the scan returns one number, and a different one for the same
  photo.
- A manual account's alert list shows every alert, though its readings never alert; the web LogBook drops the *Late
  night* tag (it has 4); CarPlay's strings speak of "the last hour" and of a 3 h graph; the watch complications grey at
  30 min, everything else at 15.
- The Live Update's *When to show* explainer says to "tap the refresh button on the card itself": no Android style has
  one (iOS wording).
- The privacy policy names Sentry; the Android app has none. Android Auto is listed by a review but not shipped.
  Dexcom ONE support is contradicted between the store and reviews.
- Call for Low is described as a call on a *confirmed* low (`priv`, `ds`); the first call came on the first reading
  ≤ 60, while the very-low alert waits for a third reading past 55, so a call can come before the very-low alert, or
  with it.
- A Big drop read "Glucose normal, rapidly dropping · 76 ↓ (+23)": the change is against 5 min earlier, when glucose
  was still rising. The missed-readings alert says "You didn't have new readings since 20 minutes" at 19 min.
- Wear OS: the tile's change is since the previous reading (0, +12) and the app's over 5 min (−13, +60); at one moment
  the age read 01:59 in the app, "now" on the tile and "3M" on the complications; the app, chart and tile go stale at
  15 min, the complications at 30, and then only lose a colour a monochrome face never drew (*seen*: "31M 122" looked
  fresh). The store's Wear OS text promises a tile with a 3 h graph: it drew a sparkline of what the phone sent (~70 min
  on this 1-min feed).

**Bugs seen:**
- iPhone app on the Mac: a new meal's time defaults to "1/1/2001, 2:00 AM"; VoiceOver hears unnamed groups.
- Android, glanceables and log: the number widget kept a 3-minute-old value while the others had updated; My Foods ›
  Recent kept a deleted meal until a restart; a reminder is deleted with no confirmation; Health Connect's *Connect*
  does nothing when glucose writing is refused (all or nothing); deleting an entry has no undo; an edited scan has lost
  its photo (a red placeholder); *Only during lows* left the Live Update up in range for 10+ min; tapping the manual-mode
  banner does nothing.
- Android, sharing (two emulators; two ANRs came with the host out of memory and aren't counted): *Pause sharing*
  failed ("Couldn't save · Please try again in a moment"), then saved without effect (the follower kept live glucose);
  *Who saw my data* never loaded; *Stop sharing with them* has no confirmation and signs the follower out of their own
  account (back to Welcome; the follower's *Stop following* does the same); a manual account that opens an invite link
  gets no follower view until it logs out and takes *Accept an invitation*; "Logged by" took the Google display name,
  not the profile name, so two accounts of one person looked alike; glucose-only followers keep LogBook and Statistics
  tabs that fail with "Couldn't load your log" / "Couldn't Load Stats"; *Stop them adding* applied but the list kept
  the old state until reopened; the invite sheet doesn't notice the join.
- Windows: after the first pairing the app sat inert ("--", clicks and the hotkey ignored) for ~3 min until restarted
  (once); *Hide in Full Screen* hid the capsule for 6 s, then it came back on top; a large capsule covers the Language
  submenu; the tray icon starts in the overflow and the app never asks to be pinned; the web-view sign-in makes
  Windows ask "Let Sugar Sense enumerate your passkeys?".
- Safari: the first connect fails ("Couldn't reach the extension in this browser") until Safari is allowed on their
  site and the page is reloaded, and nothing says so; the token-refresh hosts stay at *Ask* (the hourly refresh may need
  a grant too: unverified).
- Garmin: installing the face makes it the watch's face at once, and Garmin Connect can't switch this watch back; the widget checks
  its pairing code only while open, and reopening it drops a code already claimed (three codes for one pairing).
- Android, round 3: *I'm on it*'s Undo withdrew it but didn't bring the alert back; after a source switch, a follow
  vanished from one side only; the wearer's Care Circle list still didn't refresh after a request; deleting a
  quiet-hours schedule asks nothing.
- Wear OS (emulator): the 12 h and 24 h charts show ~70 min; the chart's numbers cover its time labels; the ranged
  complication has no arrow; nothing on the watch says the phone is out of reach, and once, after reconnecting, it kept
  122 "IN RANGE" for 6 min while the phone alerted high (likely the emulator's link; restarting the watch's services
  cleared it); the phone's *Sync now to your watch* gives no feedback.

**By design:**
- No alert when their server, the maker's cloud or the phone's internet fails; Apple Health / Health Connect sources and
  manual entry get no alerts; no companion alerts.
- iOS voice alerts only with the app open; rapid-change alerts have no rate setting; the prediction can't be tuned.
- Safety-adjacent features are Premium (the call for low, watches, glanceables), for a follower too.
- The live card sits on a silent channel: lock screens and always-on displays that hide silent notifications lose it,
  the promoted one included.
- Every push wakes a connection to the subscription service and the Wear data layer: battery and data at every reading.
- The connector can only ask assistants not to dose: ChatGPT asked for a carb ratio and correction factor anyway.

**Lessons for Sukrara** (the mistake, then what Sukrara does instead):
- **Glucose taken from any app** (the phone bridge; off in 2.7.0). The receiver for Juggluco's and xDrip+'s broadcasts
  accepts them from every app on the phone, with range checks only; switched on, any installed app could feed the
  readings that drive alerts, followers and calls. Sukrara takes no glucose broadcasts: it reads Juggluco from
  Juggluco's own web server on the phone, with an optional API secret (which proves Sukrara to Juggluco, not the
  reverse: an app holding Juggluco's port could still answer, a gap noted in [Sensor life](FEATURES.md#sensor-life)),
  and sends its own broadcasts only to the apps the person allowed, each by package. Any new input: readings only from
  the app the person picked (its package and signing certificate, or a permission it must hold), and its name shown
  where the reading is.
- **Phone numbers saved unchecked** (Call for Low). Neither the person's number nor the emergency contact's is
  confirmed, so a typo leaves the safety net silently broken, and the contact never agrees to be called. Sukrara has no
  calls: its escalation goes only to followers whose device the wearer approved. If calls or texts come: confirm each
  number (a code, or a test call that must be answered), let the contact accept, and show the state in the app.
- **An emergency message nobody can act on.** The emergency contact hears "Someone who added you as their emergency
  contact…": no name, no next step, no key to answer; no app shows that calls happened. Sukrara's escalation is a
  follower alert about the named wearer, and both sides' history says it was sent (*Very low: no answer for 5 min,
  your followers were told*).
- **A very low that waits.** Very low needed a third reading in a row past 55 (3 min on a 1-min feed; 55 then 53
  never alerted).
  Sukrara's very low, extremely high, LO and HI never wait ([SPEC §1](../contracts/alerts/SPEC.md)); the optional waits
  near a threshold are for the milder kinds.
- **Old news delivered late.** A Big drop held by quiet hours arrived when they ended, about a reading 9 min old, the
  fall long over. Sukrara: when Mute all ends, whatever still matches the newest readings alerts at once, and nothing
  else.
- **A forecast from one pair of readings.** Heading low took its "~9 min" from two readings 16 s apart (55 came 28 min
  later). The early warning is the valuable part, and users praise it; for Sukrara's planned prediction: the rate over
  several minutes, as its fast alerts already use (a trend held for 5 min), and a loose time.
- **A watch that only mirrors.** The Wear OS app can't alert, has three freshness rules, three changes and three ages,
  sends a count of points (72 points: 6 h of 5-min data, 72 min of 1-min data), and never says the phone is out of
  reach. Sukrara has one freshness rule for every surface ([Freshness](FEATURES.md#freshness-every-glucose-surface)) and
  separate alert notifications for watches; its own Wear OS app should alert on the wrist, keep the card's one
  freshness, change and age, send a time window, and show the link's state. Worth copying: no setup on the watch, the
  face picked on the phone, a refresh on opening, Google's cloud route when Bluetooth is gone, complications that grey
  on a timeline.
- **Leaving without a word.** A deleted account vanished from the other person's Care Circle with no notice, and
  *Stop sharing* has no confirmation. Sukrara tells the wearer when a follower leaves.
- **An App Link with no page.** The app claims `/care/join` and `/api/v1/care/join`; only the second has a web page,
  so the first opens the app when it is installed and a bare error page when it isn't (round 2 opened the short one;
  which one the share text and QR carry wasn't checked). Sukrara's website check fails when a URL the apps open is
  missing from the site.

## Not reached

- **Alerts**: the very-low full-screen alarm (the server's pushes never asked for it); the Softer alerts and Pause all
  modes; whether pressing 1 quiets later calls (the second low's call waited for the very low: that, or the episode
  counted as one); why the free account got no 20-min missed-readings alert; heading low and Call for Low on a real
  sensor (the scenarios used one scripted reading a minute).
- **Sharing**: the Care Board and the person switcher (they need two people followed), "Mom is responding" (two
  followers), a per-follower unit and language (no control in 2.7.0, behind a flag), the follower's quiet hours, what
  *Who saw my data* lists and what a pause does (both failed in round 2), buying Family.
- **Phone bridge** end to end: switched off by a server flag; Juggluco and xDrip+ not installed.
- **Wear OS on a real watch**: the phone's alerts mirrored to the wrist (the emulator pairing had no notification
  bridge), the text and image complications live, the open app's buzz on a zone change, the free and follower states.
- **Apple Watch, CarPlay, Live Activity and Dynamic Island running**: no simulator runs the App Store build and the Mac
  copy has no watch code; described from the bundle's strings, entitlements and images, and for the watch from the
  App Store's screenshots (none shows Big Number, Halo, a complication, an alert or the follower view).
- **Garmin**: the data field during an activity, the face's always-on look, the locked state on the watch, what the
  watch's requests contain (encrypted between Garmin Connect and their server).
- **Windows**: following someone, live low / high / stale / paused states (seen only in the app's own renders),
  Narrator and the panel's accessibility tree, the x64 build and Windows 10, sign-in with Apple or email.
- **Alexa and the DIY display**: no Echo or display linked (Alexa skipped by the owner); the skill from its store page.
- **AI**: Gemini, Grok, the claude.ai web connector; several people through one connection; ChatGPT's plan limits.
- **Free plan**: the winback offer, the creator paywall, Home Assistant's Premium state.
- **Account**: signing in again after deleting (it would make a new account).
- **The invite page**: other languages (it varies by the browser's language), and which path the share text and QR
  carry.
- **Food**: barcode scanning (the emulator's camera has nothing to scan).
- **Messages to their team**: Suggest a feature, Help & Feedback, insight feedback, partner application, store
  reviews: not sent.
- **Desk**: the Facebook group (login wall), the iPhone and iPad screenshots' captions, Play's history before 2.7 (the
  in-app notes cover Android 1.0, 2.3.2 and 2.7), two press profiles (blocked), Product Hunt (no listing); the server's
  LibreLinkUp poll is inferred from reading times. Android Auto and calendar sync are off in 2.7.0.

## Sources

| Key | Link |
|---|---|
| `home` | https://sugarsense.io/ |
| `llms` | https://sugarsense.io/llms.txt |
| `ai` | https://sugarsense.io/ai |
| `mcpblog` | https://sugarsense.io/connect-cgm-to-chatgpt-claude |
| `alexa` | https://sugarsense.io/dexcom-alexa-blood-sugar |
| `garmin` | https://sugarsense.io/garmin-watch-glucose-setup |
| `ha` | https://sugarsense.io/home-assistant |
| `libre` / `dex` / `ns` | https://sugarsense.io/libre-cgm-troubleshooting · https://sugarsense.io/dexcom-cgm-troubleshooting · https://sugarsense.io/nightscout-troubleshooting |
| `partners` | https://sugarsense.io/partners |
| `reviews` | https://sugarsense.io/reviews |
| `priv` / `del` / `a11y` | https://sugarsense.io/privacy-policy · https://sugarsense.io/delete-account · https://sugarsense.io/accessibility |
| `pred` / `widget` | https://sugarsense.io/predictive-low-glucose-alert · https://sugarsense.io/glucose-widget-iphone-lock-screen |
| `founder` | https://sugarsense.io/i-have-lived-with-type-1-diabetes-for-12-years-so-i-built-the-app-i-always-wanted |
| `cmp` | https://sugarsense.io/best-cgm-apps-2026-compared |
| `ios` / `mac` | https://apps.apple.com/us/app/id6737151615 · https://apps.apple.com/app/id6796828695 |
| `play` | https://play.google.com/store/apps/details?id=io.sugarsense.app |
| `win` | https://apps.microsoft.com/detail/9mv633rpr9k1 |
| `cws` / `edge` / `amo` | https://chromewebstore.google.com/detail/mddenhbcocihaebnjcplahonohdlefff · https://microsoftedge.microsoft.com/addons/detail/kgccicikeimphnopfjokgldnnadeghan · https://addons.mozilla.org/firefox/addon/sugar-sense/ |
| `ciqw` / `ciqf` / `ciqd` | https://apps.garmin.com/en-US/apps/19c4658f-6af3-41e8-b0bb-317c5de73920 · https://apps.garmin.com/en-US/apps/5bf02aaf-f9da-40d9-aded-76bb8158fb52 · https://apps.garmin.com/en-US/apps/dab2d770-df9a-4a25-900f-11ca19b16486 |
| `skill` / `cdir` | https://www.amazon.com/dp/B0HCNSFTVT (as the owner saw it, 2026-11-21) · https://claude.ai/directory/sugar-sense |
| `status` | https://status.sugarsense.io/ |
| `web` | the web dashboard, https://app.sugarsense.io/app/, used signed in and its bundle read |
| `ext` | the browser extension's code (1.2.1 from Firefox's store; Chrome's is the same) |
| `gh-ha` / `gh-disp` / `gh-oss` | https://github.com/sugarsense/ha-sugarsense · https://github.com/sugarsense/sugarsense-display · https://github.com/sugarsense/sugarsense-oss |
| `android` | Android 2.7.0 used on two emulators and decompiled (strings, manifest, code) |
| `phone` | Android 2.7.0 on the owner's Pixel 10 Pro (Android 17, root): the network per app, the app's files, logcat; screenshots in `phone/` |
| `ios-bundle` | the App Store build 2.7's bundle installed on a Mac: strings, entitlements, App Intents, assets |
| `win-app` | Windows 1.2.1 (Arm64) in a Windows 11 VM: used, its package, strings and metadata read, its own renders |
| `changelog` | the in-app What's New from Remote Config (Android 1.0–2.7, iOS 2.0–2.7) and the Garmin apps' store notes |
| `lv` | the owner's LibreView export (CSV, read only, 2026-11-21) |
| `android-r3` | round 3 on the two emulators: two throwaway Nightscout sites fed one scripted reading a minute (their request logs), the app's notifications and logcat, real calls to the owner's phone (recorded and transcribed); screenshots `android/r3-*` |
| `wear` | Wear OS 2.7.0 on a Wear OS 6 emulator paired with the phone emulator (the owner's account on a test Nightscout feed), the watch APK read; screenshots `wear/`, the phone's `android/r4-*` |
| `apple-watch` | the App Store listing's nine Apple Watch screenshots and the website's watch images (`apple-watch/store-watch-*`, `site-*`) |
| `care-join` | the invite page, https://api.sugarsense.io/api/v1/care/join, with a code, without one and with a made-up one (`web/care-join-*`) |
| `5kr1` / `5kr2` | https://the5krunner.com/2026/09/02/sugar-sense-libre-dexcom-and-nightscout-glucose-on-garmin/ · https://the5krunner.com/2026/09/21/libre-3-plus-review/ |
| `ds` | https://diabetesstrong.com/sugar-sense-app-review/ (sponsored) |
| `yt` / `fb` / `ig` / `tt` / `li` | https://www.youtube.com/@sugarsenseapp · https://www.facebook.com/sugarsenseapp · https://www.instagram.com/sugarsenseapp · https://www.tiktok.com/@sugar.sense · https://www.linkedin.com/company/sugarsenseapp |

## In Sukrara

Nothing has been brought in yet. When a feature from this report is built, this section records what was added and
what was left out, with the reason.
