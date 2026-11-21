# 46 — Sugar Sense: every feature, how it's built, and how it compares

*2026-11-21 · investigation report for [#241](https://github.com/mauriciabad/sukrara/issues/241) · desk research
(website, stores, version histories, GitHub, press, reviews) and hands-on use with the owner's account (Premium trial,
FreeStyle Libre through LibreLinkUp): Android 2.7.0 on an emulator (walked and decompiled), the web dashboard, the
iPhone app 2.7 running on a Mac, the Mac menu bar app 1.3 and the Home Assistant integration 1.0.0 · "unverified" = no
primary source found · nothing built, no Sukrara code changed*

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

## Short answer

- **What it is**: a cloud CGM companion by Sugar Sense OÜ (Tallinn; a founder with type 1 diabetes). Its servers sign in
  to the person's **LibreLinkUp, Dexcom Share or Nightscout** and poll them around the clock (about every 5 min); they
  run **every alert on the server** and push alerts and readings to phones, which then feed widgets, Live Activities and
  Live Updates. Every other surface (watch, Mac, Windows, browser, web, Alexa, Home Assistant, Garmin, a DIY display,
  AI assistants) is a **read-only view without alerts**. A free tier with every alert; Premium ($39.99/yr) for
  glanceables, watches, AI, PDF, call for low.
- **Pace**: relaunched as 2.0 on 2026-07-02; a feature release every 2–3 weeks since, and more than a dozen surfaces
  added in those five months.
- **Where it leads Sukrara**: breadth of surfaces (iPhone, Apple Watch, CarPlay, Wear OS, Windows, browsers, web,
  Alexa, Siri, Home Assistant, MCP, a $25 bedside display); a richer log (meals with names, My Foods, medication,
  activity, meter checks, context tags, meal response); AI (food photo and barcode, insights, Copy for AI, MCP);
  predictive "heading low"; phone calls on a low; caregiver features (several people, Care Board, "I'm on it",
  check-in receipts, pause, time-limited access, access log); gamification (streaks, a mascot, a meal guessing game);
  a full growth machine (onboarding funnel, paywall, review prompts, partner program, 40 SEO posts, calculators,
  status page).
- **Where Sukrara leads**: it reads the sensor itself and alerts **without internet or any server**; sharing is
  end-to-end encrypted with no account, nearby over Bluetooth; the Mac **alerts**; alert rules are deeper (night rules,
  waits near a threshold, Remind less, treatments that quiet alerts, escalation when the wearer doesn't answer); uploads
  to LibreView, Tidepool and Nightscout, a local Nightscout / xDrip+ API, insulin pens; free and open source.
- **Their architecture's limits** are also their weak spots: no alerts when their server, the maker's cloud or the
  phone's internet fails; Apple Health and Health Connect sources get no alerts; companions never alert. They have
  planned for Abbott ending LibreLinkUp (a ready page, and an Android bridge from Juggluco and xDrip+ already in 2.7.0).

## Platforms

| Surface | What it shows and does | Plan | Sukrara counterpart | Source |
|---|---|---|---|---|
| iPhone (iOS 18.6+) | The full app: sources, alerts, Care Circle, LogBook, stats, AI, widgets, Live Activity, Siri, CarPlay; runs on Apple Silicon Macs as a fixed 1024×800 window | Free + Premium | No iPhone app | `ios`, `iphone-on-mac/` |
| Android (min SDK 26) | The same as the iPhone, plus Live Updates, a floating capsule, Health Connect, voice alerts in the background, the Juggluco / xDrip+ bridge; 5K+ installs | Free + Premium | The Android app | `play`, `android/` |
| Apple Watch | App, complications (Big Number, Graph, a mascot face), wrist alerts, Watch Siri; "glucose as a contact photo" for any face | Premium | No | `ios`, `iphone-on-mac/settings-*` |
| Wear OS | Tile (value, arrow, 3 h graph), complications, watch app (crown switches 3/6/12/24 h), faces Character / Big Number / Gauge / Halo; free plan: "Open Sugar Sense on your phone and subscribe" | Premium | Alerts reach the watch as notifications ([Alerts](FEATURES.md#alerts)) | `play`, `android/int-01…06` |
| CarPlay | Glucose, alerts, snooze, Live Activity mirrored, optional spoken readings | Premium | No | `ios` |
| Android Auto | Built (car-app library, strings, a Settings row) but **not shipped**: no car service in the 2.7.0 manifest; one review lists it (unverified) | — | No (planned) | `android` decompiled, `ds` |
| Web dashboard (PWA) | Home, Statistics, Insights, LogBook (add, edit), Care Circle, a bedside full-screen view, printable report, pairing pages for every companion, partner dashboard; "Alerts and calls run on your phone, not in the browser" | Free | No | `web`, `web/` |
| Mac menu bar (macOS 14+) | Value + arrow (capsule or plain), optional change and minutes; dropdown with 3–24 h graph and window stats; ⌃⌥G; S/M/L widgets; followee switcher; Safari extension inside; 30 s refresh; **no notifications** | Free | The Mac app (menu bar, panel, widgets, **alarms**, treatments, report) | `mac`, `mac-menubar/` |
| Windows tray | Tray badge (3 styles, one high-contrast), a floating always-on-top capsule (3 sizes, can sit on the taskbar), panel graph, Ctrl+Alt+G; no notifications | Free | No | `win` |
| Browser extension (Chrome, Edge, Firefox; Safari through the Mac app) | Badge or big-number icon, popup graph 3–24 h with window stats, followee switcher, Alt+Shift+G; 30 s poll | Free | No | `cws`, `edge`, `amo`, `ext` |
| Garmin (3 Connect IQ apps) | Widget (glance + full view, publishes a complication other faces can use), watch face (~115 watches, AOD), data field that **writes glucose into the activity's FIT file**; 5-min background refresh; pairing by code / QR | Premium | Sukrara Aura face (free), xDrip+-made faces and fields through Sukrara's xDrip+ server ([Garmin watch face](FEATURES.md#garmin-watch-face-sukrara-aura)) | `ciqw`, `ciqf`, `ciqd`, `garmin` |
| Amazon Alexa (5 languages, 9 stores) | "what's my glucose" (value, trend, change, age), "how was my night", "how is my day", "how is {name}", "how is everyone"; Echo Show card tinted by zone; Echo Show home widget (Premium); one-tap linking from the app | Free voice | No | `alexa`, `skill`, `android/int-20…22` |
| Siri (iOS) | "What's my glucose?" (free on the iPhone), "how was my night"; a Shortcuts action *Get Glucose Reading* | Free (Watch Siri Premium) | No | `ios` |
| AI assistants (MCP) | Claude (directory listing), ChatGPT, Gemini, Grok, Claude Code, Cursor read the account through OAuth: 7 read-only tools | Premium | No (planned) | `ai`, `cdir`, `mcpblog` |
| Home Assistant (HACS default since 2026-11-16) | 14 entities (glucose, trend, change, zone, in range, last reading, TIR today, alerts paused, sensor day / end, 4 limits), 60 s poll, 3 blueprints (lamp colour by zone, night light out of range, speak a low once); no options, no alerts | Free (code still has a Premium state) | No (planned) | `ha`, `gh-ha`, `home-assistant/` |
| DIY bedside display (ESP32-S3, 3.5", ~$25) | Value, arrow, change, age, graph; scenes Color / Night / Dark / Auto; bedside clock; up to 4 people; TIR and sensor days; web-USB installer; ≤ 5 per account; open-source firmware | Free | No | `gh-disp` |

## Features

**Plan**: F free, P Premium. **Sukrara today**: Yes / Partly / No, with the [FEATURES.md](FEATURES.md) section.

### Sources and sensor

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| FreeStyle Libre (LibreLinkUp) | The person's LibreLinkUp email and password; the server polls about every 5 min (the Libre lane stores a reading every 1–4 min); errors in plain words ("No patients shared with that account", re-accept Abbott's terms); Libre 3 → 3 Plus needs no reconnect | F | all (server) | **Yes**, on the device, about 10 s late, Android and Mac ([LibreLinkUp as a source](FEATURES.md#librelinkup-as-a-source-both-apps)); also the Libre 3 read directly | `libre`, `5kr1` |
| Dexcom (Share) | The sharer's login (username, email or phone with country code), region US / APAC / worldwide (US picked by itself since 2.4); needs ≥ 1 follower in Dexcom's app. Dexcom ONE: the store says ONE+ works, two reviews say ONE doesn't (unverified) | F | all | **No** (planned, [44](44-external-sources.md)); Dexcom sensors read directly, untested | `dex`, `ios` |
| Nightscout | URL (`http` upgraded to `https`, `/api/v1` and a trailing slash ignored) and one field for the API secret or a token (≥ 12 characters); public https only | F | all | **Yes**, treatments both ways ([Nightscout as a source](FEATURES.md#nightscout-as-a-source-both-apps)) | `ns` |
| Apple Health / Health Connect as the source | For meters and over-the-counter sensors; readings are uploaded but get **no server alerts or calls**; a home banner says so | F | phones | **No** (Sukrara writes glucose to Health Connect only) | `priv`, `android` |
| Manual entry | No sensor: typed readings, graphs, stats, LogBook, no alerts; a typed low gets advice ("Treat it right away and recheck in about 15 minutes") | F | phones, web | **No** | `web` |
| Phone bridge (Juggluco / xDrip+) | Android listens for Juggluco's and xDrip+'s glucose broadcasts and forwards each reading to the server, so server alerts, watches and followers keep working; a row in the source chooser ("In Juggluco open Settings › Exchange data › Glucodata broadcast") | F | Android | **Yes**, Juggluco read on the device ([Source](FEATURES.md#source-whose-glucose-and-where-it-comes-from-both-apps)) | `android` decompiled |
| Change source | An intro dialog, then a chooser: Libre / Dexcom / Nightscout / Health Connect / Manual / Phone bridge; the switch happens on the server | F | phones | **Yes**, Settings › Source, whose glucose × source | `android/settings-11` |
| Sensor age | "Day 3 of 15" with a progress bar, start, end, serial with Copy; "Remind me before it ends" ("The morning of the last day and at expiry. Not an alert."), Show on Home; **Sensor support**: Request a replacement, call Abbott support (the number picked by country), the Libre 3 safety notice; Dexcom: a sensor-code scanner and a start set by hand | F | phones | **Yes**, deeper (start and length when the source doesn't say, a new-sensor flow, the end in the calendar); **no** maker support links ([Sensor life](FEATURES.md#sensor-life), [Sensor reminders and countdowns](FEATURES.md#sensor-reminders-and-countdowns)) | `android/settings-10`, `ios` |
| Provider outage | Home banner "{provider} outage: not your phone or sensor" (confirmed / suspected), from their own monitoring; a public status page | F | phones, web | **Partly**: LibreLinkUp maintenance says until when, from Abbott's status page ([LibreLinkUp as a source](FEATURES.md#librelinkup-as-a-source-both-apps)) | `web`, `status` |
| Not supported | Stelo, Lingo (closed platforms) | — | — | Stelo read directly, untested | `cmp` |

### Glucose on screen

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Zones | In range / warning / alert from four limits (defaults 55 / 70 / 180 / 250); a reading **on** a limit is out of range; green `#65B741`, orange `#FD8D38`, red `#FF6868`, stale grey; number + arrow + a word, never colour alone | F | all | **Yes**, six ranges (adds extremely high, 300) and words ([Graph and value card](FEATURES.md#graph-and-value-card)) | `ext`, `a11y` |
| Staleness | > 15 min: grey with the reading's clock time; > 2 h: "No Data" / "--"; the same on every surface (Home Assistant entities go unavailable at 15 min) | F | all | **Yes** ([Freshness](FEATURES.md#freshness-every-glucose-surface)) | `ext`, `mac`, `ha` |
| Change (delta) | Against the newest reading ≥ 270 s older within 20 min, so 1-minute Libre streams don't jitter; "0" when flat; one test fixture shared by iOS, Android, web, Home Assistant, display and extension; a toggle | F | all | **No** change number: the card shows the trend arrow worked out from the readings ([Trend arrow](FEATURES.md#trend-arrow)) | `ext`, `gh-ha` |
| Arrow | The source's five codes (↓ ↘ → ↗ ↑); unknown → flat; a blank one stays blank | F | all | **Yes**, from the readings (Quick / Balanced / Smooth) or the sensor's | `ext` |
| Age counter | A live mm:ss counter since the reading on the hero card, the web tab title shows the value ("131 → · Sugar Sense") | F | phones, web | **Partly**: time since the reading once late ([Freshness](FEATURES.md#freshness-every-glucose-surface)) | `android/home-01-top`, `web/home-01` |
| Home | Greeting with name and avatar; round icons for Morning Report, Sense Check, streak (each can be hidden); hero card filled with the zone colour (Refresh, Full screen); graph card; TIR card; AI insight card; big **Live Updates** (or Live Activity) and **Snooze** buttons; bottom bar Home · Statistics · (+) · Logbook · Settings | F | phones, web | **Yes**, a different layout ([Home screen](FEATURES.md#home-screen)) | `android/home-01-top` |
| Graph | Day navigator with a calendar, a past day shows "Full day", windows 3 / 6 / 12 / 24 h (the phone's list is editable: add 1–24 h, swipe to remove), dashed limit lines, the line coloured per zone; **drag to scrub** with a tooltip and haptic ticks; event chips open a popup with edit / delete | F | phones, web | **Partly**: scroll back in time and pinch to zoom (Android panel, Mac dropdown), treatment markers and labels; no day picker, no scrub tooltip ([Graph and value card](FEATURES.md#graph-and-value-card)) | `android/home-16…22`, `home-30` |
| Full screen | **Day** (the zone colour fills the screen, clock, 3 h sparkline), **Night** (black, big clock, Dim), **Car** (the number mirrored for a windscreen reflection, Mirror toggle), **Speak**; the web has **Big** instead of Car; "Tap for options. Swipe down to close." | F | phones, web | **No** | `android/home-09…14`, `web/fullscreen-*` |
| Unit | mg/dL ↔ mmol/L (× 0.0555, one decimal), on every surface | F | all | **Yes** ([Glucose unit](FEATURES.md#glucose-unit-mgdl-or-mmoll)) | `ext` |
| Dark mode | On / Off / Auto in the app; the web cycles Auto → Dark → Light | F | all | Follows the system | `android/settings-47` |

### Glanceables on the phone

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Home-screen widgets | Android: number 1×1, ring 2×2, buddy 2×2 (the mascot), graph 4×2 and stats 4×2 (3 h in range, lowest, highest), the wide ones with "+" opening the Add sheet; pinned from inside the app; iOS: the same five, Lock Screen and StandBy. **Refreshed by server pushes**; a local alarm greys them 15 min after the reading. Locked: "Glucose on your home screen is Premium · Tap to unlock" | P | phones | **Yes**, free ([Home screen widgets](FEATURES.md#home-screen-widgets), [Lock screen widget](FEATURES.md#lock-screen-widget) with root, [Mac widgets](FEATURES.md#mac-widgets)) | `android/widgets-01…10` |
| Live Updates (Android) | An ongoing notification in five styles: **System** (Android 16 Live Update: zone segments and a status-bar chip "126 →", Samsung Now Bar), Minimal, Focus, Graph, Character; the status-bar icon is the number drawn as a bitmap; **When to show**: Always / Only during lows / During lows and highs (the server starts and ends the episode; removed 30 min after back in range, 3 h at most) | P | Android | **Yes**, free, always on ([Live notification](FEATURES.md#live-notification), [Root status bar](FEATURES.md#root-status-bar-system-icons)); no show-only-when-out-of-range | `android/settings-27…36`, `notif-01…03` |
| Live Activity / Dynamic Island | Styles Focus, Bold, Graph, Minimal, Compact, Character; restarted before iOS's ~8 h limit ("Restarted to stay up-to-date."); a refresh button heals a stale card; the same When to show | P | iOS | No iPhone app | `widget`, `iphone-on-mac/settings-*` |
| Floating glucose | A capsule over other apps (S / M / L, opacity, arrow, change), drag anywhere, tap opens the app, long press: "Hide for 1 hour / Hide until tomorrow / Settings"; needs *Appear on top* | P | Android | **No** | `android/floating-01`, `-02` |
| Glucose as a contact photo | A contact "Sugar Sense" whose photo is the reading, so Apple's Contacts complication shows it on any watch face | P | iOS | **No** | `ios`, `iphone-on-mac/settings-*` |
| Calendar sync | The latest reading mirrored into a private calendar, for car screens and Macs without Live Activities | P | iOS (Android: built, off) | **Partly**: the sensor's end goes in the calendar, not readings ([Sensor end in calendar](FEATURES.md#sensor-end-in-calendar)) | `ios`, `android` decompiled |
| Value on the app icon, shortcuts | iOS badge with the value (off by default); Android launcher shortcuts "My glucose", "Log meal" | F | phones | **Partly**: *Read glucose aloud* shortcut ([Read glucose aloud](FEATURES.md#read-glucose-aloud)) | `iphone-on-mac/settings-*`, `android` decompiled |

### Alerts

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Kinds and defaults | Very low 55 (Critical, every 15 min, Classic Alarm), Low 70 (Time Sensitive, 30 min), High 180 (Regular, 2 h), Very high 250 (Time Sensitive, 30 min), Heading low (Time Sensitive, 30 min), Big drop (on) / Big rise (off) (Regular, 30 min, **no rate setting**), Missed readings after 20 min (Never / 5 / 10 / 15 / 20) with an optional escalation (30 / 45 / 60 min); Reset all to defaults | F | phones | **Yes**: 7 kinds incl. extremely high 300, rates at 2 mg/dL/min held 5 min, LO / HI ([Alerts](FEATURES.md#alerts)) | `android/settings-12`, `alerts-10…36` |
| Per-alert page | Enable (the emergency ones confirm turning off: "you may not be alerted in an emergency"), threshold, **Priority** Regular / Time Sensitive / Critical with plain bullets, **Repeat** every reading / 15 / 30 min / 1 / 2 h / custom 1–120 min, **Tone** | F | phones | **Yes**: threshold, remind every, sound, volume ("like an alarm"), test | `android/alerts-10…26` |
| Tones | 22 in three length groups (short, medium, long: up to "Emergency Siren"), Silent, Vibration only, ▶ preview | F | phones | **Yes**, sound packs at discreet intensities, any phone sound | `android/alerts-24…26` |
| Heading low (predictive) | About 15–20 min ahead to the very-low line; on by default; no settings beyond on, priority, repeat, tone; computed on the server; "Now 95 ↘ may reach 55 mg/dL in ~15 min. Have fast-acting carbs ready." Users praise it ("saved me about 7 times") | F | phones | **No** (planned) | `pred`, `home`, `reviews` |
| Test this alert | A real push through the server: "Lock the phone. Sending in 3…", then "Delivered as Critical with the Classic Alarm tone" or a diagnosis (channel off / silent, Do Not Disturb, notifications off); rate-limited | F | phones | **Partly**: play the sound and vibration, send a test alert ([Settings](FEATURES.md#settings)) | `android` strings |
| Snooze | **Pause low & high** / **Pause everything**: 15 / 30 min, 1 / 2 h, custom (a −/+ stepper and chips 15–120); "How does it work?": very low and very high still ring under Pause low & high, the emergency call and followers ring under both | F | phones | **Yes**, per alert (Mute, Remind less, in the notification) and Mute all 30 min–8 h, until turned off, custom 5 min–24 h ([Alerts](FEATURES.md#alerts)) | `android/home-26…28` |
| Quiet hours | A list; each: days (default Mon–Fri), 9:00–17:00 by default, mode **Softer alerts** (default) / Pause low & high / Pause all (confirmed); may cross midnight; "End for today"; stored on the server | F | phones | **Partly**: Night alerts, a second set of rules 22:00–07:00 (very low always on); no per-day schedules | `android/alerts-01`, `-02` |
| Self-tidying alerts | An alert clears itself back in range; a new one replaces older cards of its kind even with the app closed; a high ends a low's card | F | phones | **Yes**: one at a time, the most severe wins, cleared once back by the margin | `ios` |
| Very low on the lock screen | Android: a full-screen alarm activity over the lock screen with Do Not Disturb access; iOS Critical Alerts (asked again in 2.7) | F | phones | **Yes**, alarm-volume channels and a full-screen alert | `android` decompiled |
| Call for Low | A phone call (Twilio) at 50–70 mg/dL (default 60) "the moment your glucose drops to it"; "press 1 to confirm you're okay"; after several unanswered calls it calls an **emergency contact**; snooze and quiet hours don't stop it; retries are server-side | P | phones | **No**; nearest: followers told when a very low gets no answer for 5 min ([Alerts](FEATURES.md#alerts)) | `android/settings-20`, `priv` |
| Voice alerts | Speak **all new readings** or **only alerts**; Android in the background (text to speech, the app's language when a voice exists); iOS only while the app is open with auto-lock off (tip: Announce Notifications). A blind reviewer relies on it and asks for voice choice | F | phones | **Partly**: *Read glucose aloud* on demand, Android and Mac | `android/settings-21`, `reviews` |
| Alert reliability | "Keep alerts reliable": battery optimisation; Notifications, Full-screen alarm and Do Not Disturb access checks, each opening its system page; steps per phone maker (Samsung, Xiaomi, Huawei, OnePlus / Oppo / Realme, Vivo) | F | Android | **Yes**, with Allow and Turn off ([Blocked features](FEATURES.md#blocked-features-both-apps)) | `android/settings-41…44` |
| Medication reminders | Insulin (basal) or medication: name, time, days, units / dose, alert style, sound; exact alarms on the phone; actions ✓ I took it / Took a different amount / Skip today / Remind in 1 h; "Still pending: X"; logged to the graph; two free, more Premium | F / P | phones | **No** (sensor reminders only) | `android/settings-22…26`, `notif-05` |
| Weekly insight | "Get this insight weekly · A gentle weekly note with your top pattern. No spam." | P | phones | **No** | `android/stats-*` |

### Care Circle (sharing)

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Invite | A one-time 8-character code with QR, valid 72 h, shown once; **Share openly** or **Require my approval**; a copyable invite message; "Keep this open until they're in" | F | phones, web | **Yes**: invite link or QR, a request to review ([Share with followers](FEATURES.md#share-with-followers-wearer)) | `android/care-03`, `web/care-*` |
| What followers see | Scope: Everything / Glucose and emergencies only / Everything without sleep and heart / Everything + adding to the LogBook; history, stats and streaks, Insights, a sleep / resting heart rate / HRV card | F | phones, web | **Partly**: glucose, treatments and alerts; *Can add treatments* | `android` strings, `priv` |
| Caregiver logging | Followers allowed to add log meals, notes and insulin into the wearer's LogBook under their name ("Logged by …"); a duplicate-insulin guard ("You logged 4 units at 12:10 · Add anyway?") for caregivers too | F | phones, web | **Yes**: writers add treatments, *Sending… / Not sent · Retry*; a duplicate guard for pen doses ([Insulin pens](FEATURES.md#insulin-pens-docs29)) | `android/log-12`, `reviews` |
| Follow several people | Follow ≤ 10 people; ≤ 10 followers each; **Care Board** shows everyone at once (reorderable); every companion has a person switcher; the display shows up to 4 tiles; Alexa "how is everyone"; you can follow while you share | F | all | **No**: one person per device ([Source](FEATURES.md#source-whose-glucose-and-where-it-comes-from-both-apps)) | `web`, `gh-disp`, `alexa` |
| Follower alerts | Copies of very low, very high, heading low and readings stopped, with name and value; per person: "Wake me for emergencies" (through silent and Do Not Disturb), priority, repeat, quiet hours that batch heading-low and readings-stopped into one summary, a test alert (3 a day), turning off with a warning | F | phones | **Yes**, differently: the follower runs the wearer's rules or its own (*Settings from*), and gets *Very low · no response* | `web`, `priv` |
| "I'm on it" | A follower acknowledges an alert; the others see "Mom is responding", with 5 s undo | F | phones | **No** | `android` strings |
| Receipts to the wearer | "Checked on you today / this week", "Hasn't been able to receive alerts since {date}"; the follower is told this is visible | F | phones, web | **Partly**: past followers with the date they left, who is nearby | `web` |
| Pause, time limits, revoke | Pause sharing (2 h, until 08:00 tomorrow) with or without emergency alerts; access for 48 h / 1 week / a custom end; revoking stops access within about a minute | F | phones | **Partly**: remove a follower, sharing over the internet on / off; no pause or time limit | `android` strings, `priv` |
| Who saw my data | Views over 7 days, exports over 30 days, AI assistants included; a 90-day audit kept on the server | F | phones | **No** | `android` strings, `priv` |
| Labels and nicknames | Relation labels (Mom, Dad, Guardian, Partner, Sibling, Grandparent, Child, Friend, Caregiver, School nurse, Other); private nicknames | F | phones, web | **Partly**: the wearer's name and picture | `web` |
| Follower glanceables | A "mirror" Live Activity / Live Update of the person followed | P | phones | **Yes**, free: a follower's notification and widgets show the wearer | `priv` |
| Family plan | Premium for up to 6 people in the circle ($59.99 / yr; €64.99 on a Spanish device) | P | phones | n/a (free) | `ios`, `android/care-05` |

### LogBook

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Add to your day | One sheet: Meal, Scan food, My Foods, Insulin, Medication, Activity, Meter check, Add a note (web: six, no food features); the FAB follows the active filter (Insulin filter → the insulin form) | F | phones, web | **Partly**: one form for rapid, long, carbs and a note, from home, widgets, the notification, alerts and a Quick Settings tile ([Treatments](FEATURES.md#treatments)) | `android/log-01`, `log-14` |
| Meal | Name, carbs (optional), insulin short / long and units on the same form (2.7), Save to My Foods, context tags, date and time | F | phones, web | **Partly**: carbs and insulin together, no meal names | `android/log-02` |
| Insulin | Short / long-acting, units, a name (e.g. Humalog), time; "Sugar Sense never suggests a dose" | F | phones, web | **Yes**, no insulin names; NovoPen doses logged by a tap ([Insulin pens](FEATURES.md#insulin-pens-docs29)) | `android/log-12` |
| Medication | Name, optional dose, unit (mg, mcg, g, mL, units, tablet, puff, drop, spray) | F | phones, web | **No** | `android/log-13` |
| Activity | Duration (slider), intensity light / medium / heavy; imported workouts carry the sport (28 kinds), distance, kcal, heart rate and the source app | F | phones, web | **No** | `android/log-15` |
| Meter check | A number wheel (default 100) and a note: "It never changes the graph, alerts or statistics." | F | phones, web | **No** | `android/log-16` |
| Note and tags | 140 characters; tags 🤒 Sick, 😰 Stress, 🍷 Alcohol, ✈️ Travel, 🌙 Late night, kept out of insights so unusual days don't skew them | F | phones (web: 4 tags) | **Partly**: notes, no tags | `android/log-17` |
| Day view | Day with a calendar, filter chips with counts, day sums (in range, carbs, insulin, activity); each row with the glucose at entry and its arrow; swipe to delete (confirmed); tap to edit | F | phones, web | **Partly**: 24 h on home and a 31-day Treatments page, glucose and trend at logging, swipe to delete with Undo | `android/log-10`, `log-30…31` |
| Meal response | Under each entry: "Peaked 130 mg/dL, +4 in 1 min · back in n min"; activity: "Dropped Δ to x" | F | phones, web | **No** | `android/log-10` |
| Health imports | Apple Health: meals, insulin, workouts, sleep, resting HR, HRV in; glucose, carbs, insulin out (edits and deletes follow). Health Connect: the same in, glucose out | F | phones | **Partly**: glucose out to Health Connect (carbs and insulin left out on purpose, [Not included](FEATURES.md#not-included)) | `priv`, `android` decompiled |

### Food and AI

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| My Foods | Recent / Saved tabs, "+" re-logs in one tap, bookmark; per food "Over your last n logs, X usually raised you about +Δ, peaking around t"; **Your Usual** suggestions | F | phones | **No** | `android/log-19`, `-20` |
| AI food scan | Full-screen camera with **Scan Food / Barcode**, flash, gallery, "Add a note" ("how it was cooked, how much you ate"). Result: name, "AI estimate · verify amounts", composition, meal timing, glycemic index, sugars, glycemic load ("High spike" + why), macros, recommendations (fewer slices, vegetables first, walk 15–20 min after), "Carbs to log: 120 g · Edit", **Log Meal**. The photo is analysed on their server by an AI model; barcodes are read on the phone and looked up in Open Food Facts, then USDA. 3 free scans | P | phones | **No** | `android/log-21…33`, `priv` |
| AI Insights | From ~14 days: an AI headline and paragraph, patterns with High / Moderate / Low confidence and "from n meals / nights", 👍 / 👎; a card on Home and Statistics; fallback "Showing your detected patterns"; an Anthropic Claude model on aggregated, de-identified statistics, dose advice filtered out "in code" | P | phones, web | **No** | `android/ai-01`, `web/insights-01`, `founder` |
| Morning Report | A sheet with the mascot, a title ("A few highs overnight") and a sentence written on the server, with advice ("drink some extra water today"); **Night in detail** (Premium): in range, lowest, highest, an overnight chart, the list of lows and highs; a moon chip all morning | F / P | phones | **No** | `android/home-02`, `iphone-on-mac/home-03` |
| Copy for AI | ~3 kB of text to paste into any chatbot: targets, 14-day stats, each day, the LogBook, last night, patterns, the last 24 h at one reading per 15 min; "Data only, no treatment advice"; a daily limit | F | phones, web | **No** | `ai`, `web/stats-*` |
| MCP connector | OAuth with "whose data" (you or someone you follow); 7 read-only tools (profile, current glucose, readings ≤ 14 days per call, daily stats 1–90 days, LogBook, Morning Report, insights); logs tool and time only; shows in the followee's access log; listed in Claude's directory; guides for ChatGPT, Gemini, Grok, Claude Code, Cursor in 7 languages; *Connected assistants* with Disconnect | P | all (server) | **No** (planned) | `ai`, `mcpblog`, `cdir`, `android/int-30…33` |

### Statistics and reports

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Statistics | Today / 7 / 14 / 30 / 90 days (30 and 90 Premium); TIR ring with readings count, % data and "most reliable over 14+ days at ≥70% data"; vs the previous period; 5-range breakdown; average + SD; GMI "est. A1C"; CV with Stable / Elevated; low and high events; lowest / highest with time | F / P | phones, web | **Yes**: 1 / 7 / 14 / 30 days, time in ranges, average, GMI, CV ([Home screen](FEATURES.md#home-screen) *Glucose report*, the Mac report window), and the [Metrics beside the value](FEATURES.md#metrics-beside-the-value) | `android/stats-*`, `web/stats-*` |
| Daily Pattern (AGP) | Median, 25–75 % and 5–95 % bands, the target band, a spoken summary ("Median is highest around 1 AM at 252…") | P | phones, web | **No** (investigated in [26](26-agp-report.md)) | `android/stats-*` |
| PDF report | The phone builds a 3-page PDF (14 / 30 / 90 days, LogBook optional) for the share sheet; the web prints a print stylesheet; "What We Noticed" with a tip per pattern; the LogBook with "Logged by"; a follower's print is logged | P | phones, web | **No**: the CSV, and LibreView / Tidepool for the clinic | `android/stats-06`, `web/stats-04`, `-05` |
| CSV | 14 / 30 days, phones only | F | phones | **Yes**, more periods, LibreView's layout ([Data export](FEATURES.md#data-export)) | `android/stats-06` |
| Health card | Sleep, resting HR, HRV, blood pressure, weight from Health Connect / Apple Health | F | phones | **No** | `android/stats-*` |

### Gamification

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Streaks | A day counts at ≥ 70 % in range; quiet days pause it, "an occasional day under goal is held for you", only a month without readings restarts it; last 7 days, longest, next milestone (3 days), "New record: n days!"; a flame on Home | F | phones, web | **Partly**: rewards on the rings (crown, laurel from 90 %, flowers from 95 %) ([Graph and value card](FEATURES.md#graph-and-value-card)) | `android/home-05`, `web/home-03` |
| Sense Check | A ring of 3 logged entries a day; after a meal: "Where will your glucose be two hours after your 11:32 AM meal?" (Lower / About the same / A bit higher / Much higher), revealed 2 h later ("You read it right"); "n of m guesses were close this month"; waits a few hours after a low | F | phones | **No** | `android/home-04`, `log-05` |
| Mascot | A blob whose mood follows the zone or the night, on the Morning Report, a Live Update style, a widget and watch faces; celebration lines ("🎉 In range today!", "New best") | F | phones, watches | **No** | `android/home-02`, `widgets-*` |

### Settings, account, help

| Feature | What it does · how | Plan | Where | Sukrara today | Source |
|---|---|---|---|---|---|
| Account | Sign in with Apple, Google or email; the same account on every device; companions sign in through the web; delete in the app; raw readings pruned after ~365 days | F | all | **No account**: a sharing identity with a recovery code; data on the device ([Share with followers](FEATURES.md#share-with-followers-wearer)) | `priv`, `del` |
| Profile | Name (live check, ≥ 3 characters), avatars (memoji) | F | phones | **Yes**, name and picture | `android/home-06`, `-07` |
| Settings tree | Glucose source · Alerts (Notifications, Call for low, Voice, Reminders, Live Updates, Widgets, Alert reliability) · Display (hide Home cards, app lock) · General (unit, language, graph, dark mode) · Integrations · Support · Terms & account | F | phones | **Yes**, organised differently, with search ([Settings](FEATURES.md#settings)) | `android/settings-01…06` |
| App lock | Fingerprint / screen lock (Android), Touch ID (Mac) | F | phones | **No** | `android/settings-*` |
| Languages | 7: English, German, French, Spanish, Dutch, Estonian, Georgian (the Mac app lacks Estonian; Alexa 5) | F | all | English, Spanish ([Languages](FEATURES.md#languages)) | `ios`, `mac` |
| Accessibility | Not colour alone; screen-reader sentences on core screens ("121 milligrams per deciliter, steady, in range, just now."); reduced motion; Dynamic Type "still improving" | F | all | **Yes**, one spoken sentence on every surface ([One card on every surface](FEATURES.md#one-card-on-every-surface)) | `a11y`, `android` |
| Support | FAQ (9–11 categories); Get in touch (a diagnostics file); copyable device id; **Suggest a feature** (category, title, text, "Tell me when this ships"); feedback relayed to a chat channel the team reads | F | phones | **Partly**: GitHub issues, the website | `android/support-01…09` |
| What's New, updates | Versioned cards configured remotely; soft and forced update sheets | F | phones | **Partly**: the GitHub version's updater, release notes ([Updates](FEATURES.md#updates-android-github-version)) | `android/support-03…05` |
| Service emails | Setup checklist when no readings arrive, "sensor connection working again"; an opt-in newsletter; no glucose in emails | F | — | **No** (no account) | `priv` |

### Growth and business

| Feature | What it does · how | Sukrara today | Source |
|---|---|---|---|
| Onboarding funnel | Welcome ("Catch the low before it catches you. Day or night."; Get started / I already have an account / Accept an invitation) → whose glucose → diabetes type → how you check → checks a day → worries → a count of yearly checks → the 20-min forecast promise → name → commitment → **how did you hear + creator code** (longer trial) → a "protection plan" → sign-in → source → notification priming → Health Connect → **paywall** with a trial timeline (reminder on day 6, charge on day 7) → summary; iOS tracking prompt; social proof configured remotely | Setup asks only what monitoring needs ([First-run setup (Android)](FEATURES.md#first-run-setup-android)) | `android/a-000…a-040`, `iphone-on-mac/onboarding-*` |
| Pricing and trial | Premium ("Pro"): $39.99 / yr or $9.99 / mo (€41.99 / yr on a Spanish device); Family $59.99 / yr; 7-day trial, 14 with a partner code; subscribed on the phone only (a remote paywall through a subscription service), unlocked on every other surface; a trial-ending reminder; a winback offer ("WELCOME BACK · Your first year of Pro, n% off") | Free, GPL | `home`, `partners`, `android/a-033`, `-036` |
| Review prompts | Phone: a gate in the founder's voice ("Hi, I'm Irakli… I have type 1 diabetes myself"), "Something's not right" → feedback; desktop and extension: a card after 2 days, 10 opens and a fresh in-range reading, 45-day cooldown, at most 3; store-review asks for the Garmin apps and Alexa | No | `android` strings, `ext` |
| Partner program | 30 % of net revenue for 12 months, PayPal monthly from $25, 30-day hold; link, code and QR; a dashboard (clicks, accounts, trials, subscriptions, balance); a brand kit; an application (channels, audience size, relation to diabetes, preferred code), reviewed by hand; an affiliate link for the display hardware | No | `partners`, `web/partners-01` |
| Website and SEO | ~40 long posts (many comparing Dexcom and Libre apps), 7 free tools (converter, A1C, TIR, GMI, fructosamine, a printable log, a glossary), `llms.txt`, "Ask AI about Sugar Sense" buttons, a reviews page with ratings refreshed every 12 h; Google Analytics and Ads behind a cookie banner; a sponsored review, founder-written press | The website with a Features page and the reports; no analytics | `home`, `llms`, `reviews`, `ds`, `5kr1` |
| Socials and community | Facebook page (~1.1K followers, the founder answers support in comments) and group, Instagram (~615), TikTok (3), LinkedIn (46), YouTube (3 videos, 13 subscribers); the founder on Reddit, X, Medium; in-app "Join our community" and "Share with contacts" | No | `fb`, `ig`, `tt`, `li`, `yt` |
| Help and videos | Three source troubleshooting guides, a Garmin guide, the in-app FAQ, setup pictures per step (2.7), a setup video configured remotely; 3 YouTube tutorials (0:52 overview, 2:02 Libre setup, a short) | The reports and FEATURES.md, the website | `libre`, `dex`, `ns`, `yt` |
| Status page | Their own page with JSON, refreshed every 60 s: App & API, Glucose Sync, Dexcom US, Dexcom outside US, Libre (no Nightscout) | No server to report on | `status` |
| Attribution and analytics | Firebase Analytics, Adjust, the Play install referrer, the advertising id | None | `priv`, `android` decompiled |

## How it's built

- **Cloud polling**: their servers hold the person's LibreLinkUp, Dexcom Share or Nightscout credentials (encrypted,
  AES-256-GCM) and poll them around the clock. The chain for one reading: sensor → the maker's app → the maker's cloud →
  Sugar Sense's server → push → phone.
- **Server-side alerts**: low, high, rapid change, missed readings and the prediction are worked out on the server and
  sent by APNs / FCM with their priority, tone and channel; the Android app has **no glucose logic** (none in the APK).
  Followers' copies, the phone call and quiet hours run there too.
- **Push-fed glanceables**: silent pushes carry up to 36 readings and an episode start / end, which redraw widgets, the
  Live Update and the floating capsule and show or hide them; a local alarm greys them 15 min after the reading; the
  app's own fallback poll runs every 15 min; the app logs when FCM lowers a push's priority. iOS widgets use the same
  pushes since 2.2.
- **Read-only companions**: one JSON API (a function per call over HTTPS); the extension, Mac and Windows poll every
  30 s, the web, Home Assistant (server-tunable 30–900 s) and the display every 60 s, Garmin every 5 min in the
  background. A snapshot says *ok / premium required / follower only / no source*.
- **One pairing pattern for four devices**: the device shows a 6-character code (10 min) and a QR; the person confirms
  on the phone or a signed-in web page; the device polls and gets a read-only session token; unpairing revokes it
  (Garmin, Home Assistant, the display). The extension, Mac and Windows apps sign in on the web page and get a
  short-lived token handed over by extension messaging. Signing out of a companion is local only, on purpose: a
  server logout would stop the CGM sync and the alerts.
- **Shared rules**: one fixture (the change rule) tested by iOS, Android, web, Home Assistant, the display and the
  extension; an open-source units package (npm and Ruby gem) with conversion, zones, TIR and GMI.
- **Remote control**: about 60 remote flags (each feature, a kill switch per source, forced and soft updates, What's
  New, the review-prompt numbers, onboarding social proof).
- **The LibreLinkUp shutdown plan**: a page ready at `app.sugarsense.io/app/libre` behind a flag (off on 2026-11-21):
  "Abbott has switched off the LibreLinkUp connection…"; Android → Juggluco → Nightscout (full alerts) or Health
  Connect (no alerts); iPhone → xDrip4iOS → Nightscout, a spare Android, or Dexcom; refunds for Pro; "building a direct
  bridge from Juggluco into the Android app" and "asking Abbott for official access". The bridge is already in Android
  2.7.0. No public sign that Abbott plans it (unverified); Abbott's status page lists a new "Libre Data Sharing API".
- **Stack**: native Kotlin + Jetpack Compose on Android (Hilt, Room, Glance, CameraX, WorkManager, ML Kit, Play
  Integrity), a native iOS app, a React web app, a .NET Windows app, a Manifest V3 extension, Monkey C on Garmin, C on
  the ESP32 display, Python for Home Assistant. Back end: Node.js services, a scheduled worker and Redis on
  DigitalOcean (San Francisco); MongoDB Atlas on AWS us-west-1 (moved from Ireland on 2026-09-07 for latency);
  Cloudflare; Firebase (Auth, Analytics, Crashlytics, Remote Config, Firestore, Cloud Messaging). Processors: Adapty
  (subscriptions), Adjust (attribution), Twilio (calls), Anthropic Claude (AI), Postmark (email), Telegram (feedback),
  Open Food Facts and USDA (foods), Sentry (named in the privacy policy, not in the Android app).

| | Sugar Sense | Sukrara |
|---|---|---|
| Where glucose comes from | Their server polls the maker's cloud | The phone reads the sensor (Libre 3), Juggluco, or polls LibreLinkUp / Nightscout itself |
| Where alerts run | Their server, pushed | Each device, one rules spec with shared timeline scenarios on Android and Mac |
| Without internet | No alerts, no glanceables | The direct sensor, alerts and widgets keep working; followers nearby over Bluetooth |
| Delay | Polls about every 5 min | LibreLinkUp about 10 s; the sensor every minute |
| Sharing | An account graph on their server | End-to-end encrypted over Nostr relays, or Bluetooth; no account, no server |
| Companions | Read-only, never alert | The Mac alerts; the Garmin face shows alert state |
| Data | On their server (raw readings ~365 days) | On the device; export, import, uploads the person chooses |
| Changing the app's behaviour | Remote flags, any day | Releases |
| Business | Subscription (Premium, Family) | Free, GPL-3.0 |

## Where Sukrara differs

**They have, Sukrara doesn't:**
- Surfaces: iPhone, Apple Watch, CarPlay, Wear OS app, Windows, browser extensions, web dashboard, Alexa, Siri, Home
  Assistant, MCP, a DIY display; full-screen Day / Night / Car views; a floating capsule; the glucose as a contact
  photo; readings in a calendar.
- Sources: Dexcom Share, Apple Health / Health Connect, manual entry.
- Alerts: heading low (predictive), a phone call with an emergency contact, voice on every reading, priority per
  alert, quiet hours by days, medication reminders, a delivery diagnosis when testing an alert.
- Care: several people at once (Care Board, switchers), "I'm on it", check-in receipts, pause, time-limited access,
  scopes, who saw my data, relation labels.
- Log and AI: meal names, My Foods with each food's response, Your Usual, medication, activity and imported workouts,
  meter checks, context tags, meal response, food photo and barcode scanning, AI insights, Morning Report, Copy for AI.
- Stats: 90 days, AGP, PDF, a health card (sleep, heart).
- Gamification: streaks, Sense Check, a mascot.
- Growth: the onboarding funnel, paywall and trial, review prompts, partner program, SEO content and tools, socials,
  a status page, 7 languages, an app lock.

**Sukrara has, they don't:**
- The sensor read directly (Libre 3; Dexcom, Accu-Chek SmartGuide, Aidex untested), alerts and glanceables without
  internet, before the first unlock after a restart ([Before the first unlock](FEATURES.md#before-the-first-unlock-android)).
- Sharing end-to-end encrypted with no account; followers nearby over Bluetooth; escalation to followers when a very
  low gets no answer.
- Alerts on the Mac; treatments on the Mac.
- Alert depth: extremely high, LO / HI, night rules, waits near a threshold, Remind less, a treatment that quiets the
  alert it answers, the badge as an action (*Eat carbs*), sound packs, rules checked by shared timeline scenarios.
- Outputs: LibreView, Tidepool and Nightscout uploads, a local Nightscout / xDrip+ API, broadcasts to AAPS and other
  apps ([Share with apps on this phone](FEATURES.md#share-with-apps-on-this-phone-investigation-22)); Import history.
- Insulin pens (NovoPen Echo Plus, priming told apart).
- The trend arrow worked out from the readings; configurable metrics and rings beside the value; the status-bar value
  as text with root; a lock-screen widget.
- A guided new-sensor flow, sensor start and length when the source doesn't say, the sensor's end in the calendar.
- A free, open-source Garmin face; free widgets and Live Update; free and open source as a whole.

**Both, done differently:**

| Feature | Sugar Sense | Sukrara |
|---|---|---|
| Snooze | Two kinds (low & high / everything), quiet hours keep the emergency call and followers | Per alert in the notification (Mute, Remind less, Treatment) plus Mute all; per device, never sent to others |
| Quiet times | Quiet hours: days, hours and a softer mode | Night alerts: a full second set of rules for a nightly window |
| Ranges | 4 limits, a value on a limit is out of range | 6 ranges, low below its threshold, very low at or below, highs at or above |
| Following | Up to 10 people, a board | One person per device, the wearer's or its own rules |
| Caregiver logging | Into the LogBook, named | Writers add treatments, with delivery state |
| Garmin | 3 apps, Premium, server-fed | 1 face, free, fed by the phone |
| Mac | A read-only menu bar glance | Menu bar, panel, widgets, alarms, treatments, report, read aloud |
| Live notification | 5 styles, automatic show / hide on lows | Always on, a Live Update chip, root text |
| Report | Stats screen with AGP, PDF | Report card and window, CSV; the clinic through LibreView / Tidepool |
| Sensor end | Reminder on the last day and at expiry, Abbott support links | Reminders, countdowns, a Live Update and the calendar |

## Their weak spots

**What users say** (`reviews`, `play`, `ios`, `5kr1`, `5kr2`, `ds`, `fb`):
- Widgets lag or the chart doesn't update; widgets and CarPlay are Premium only.
- Garmin shouldn't need a subscription (free Connect IQ fields exist); "more smartwatches without several apps".
- No Lingo or Libre Sense; "pretty basic", "far fewer analytics" than Supersapiens for sport.
- Won't install on older iOS / macOS; Echo Show 21 widget trouble; the French Alexa invocation is awkward; a blind
  user wants voice choice.
- Caveats: the most detailed review is sponsored and another press piece is founder-written; store reviews are answered
  and curated; possibly duplicated review texts (unverified). No Reddit threads found.

**Inconsistencies seen:**
- The streak rule reads differently on the web ("Quiet days… pause it, they never break it") and in the app (adds "an
  occasional day under goal is held for you, and only a month with no readings starts the count over").
- Home Assistant's "Time in range today" said 53 % while the app said 45 % at the same moment; the open-source TIR
  counts 70 < v < 180, against the consensus 70–180 inclusive.
- The Home Assistant README says the integration is free and lists linked instances on the web: the code still has a
  Premium state, and no page lists them.
- The web's AI guide says the in-app page "arrives with the next update"; iOS 2.7 already has it.
- The Mac app's sign-in page says "Connect the browser extension"; it signs in inside its own web view (the password
  typed again), while the iPhone app uses the default browser's session. The Safari extension is 1.1.0, the others
  1.2.1; the Mac app has no Estonian.
- Onboarding promises a carb **range** ("34 to 45 g"); the scan returns one number.
- The privacy policy names Sentry; the Android app has none. Android Auto is listed by a review but not shipped.
  Dexcom ONE support is contradicted between the store and reviews.

**Bugs seen:**
- iPhone app on the Mac: a new meal's time defaults to "1/1/2001, 2:00 AM"; VoiceOver hears unnamed groups.
- Android: the number widget kept a 3-minute-old value while the others had updated; My Foods › Recent keeps a deleted
  meal (a local cache with no delete); a reminder is deleted with no confirmation; Health Connect's *Connect* does
  nothing when glucose writing is refused (all or nothing).

**By design:**
- No alert when their server, the maker's cloud or the phone's internet fails; Apple Health / Health Connect sources and
  manual entry get no alerts; no companion alerts.
- iOS voice alerts only with the app open; rapid-change alerts have no rate setting; the prediction can't be tuned.
- Safety-adjacent features are Premium (the call for low, watches, glanceables).

## Not reached

- **The follower side** (Care Board, "I'm on it", follower alert settings, pause, time limits, the access log, the
  mirror Live Activity): needs a second account or a real invite; seen as strings only.
- **Real and test alerts, the full-screen very low, the call for low**: a test goes through the server to the owner's
  phone; calls need a number. No real low happened.
- **Changing the source** (the bridge, Health Connect, manual): would stop the owner's live source on the server.
- **Free-plan states**: the account was on the Premium trial everywhere (Home Assistant's Premium state, paywalls inside
  features).
- **Wear OS, Apple Watch, CarPlay, Garmin, Alexa, Windows, the DIY display, Safari's extension, Mac widgets**: no
  hardware, emulator or link made (pages, store listings and code read instead). Android Auto and calendar sync are
  off in 2.7.0.
- **Writing to the account** (a web LogBook entry, insight feedback, Suggest a feature, partner application, review
  prompts, delete account): not done.
- **The promoted Live Update chip and the lock screen**: the emulator showed the custom-view fallback and had no lock
  screen.
- **Desk**: the Alexa store page (a bot wall: details from a search snippet), the Facebook group (login wall), App Store
  screenshot captions, Play's history before 2.7, two press profiles (blocked), Product Hunt (no listing).

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
| `skill` / `cdir` | https://www.amazon.com/dp/B0HCNSFTVT · https://claude.ai/directory/sugar-sense |
| `status` | https://status.sugarsense.io/ |
| `web` | the web dashboard, https://app.sugarsense.io/app/, used signed in and its bundle read |
| `ext` | the browser extension's code (1.2.1 from Firefox's store; Chrome's is the same) |
| `gh-ha` / `gh-disp` / `gh-oss` | https://github.com/sugarsense/ha-sugarsense · https://github.com/sugarsense/sugarsense-display · https://github.com/sugarsense/sugarsense-oss |
| `android` | Android 2.7.0 used on an emulator and decompiled (strings, manifest, code) |
| `5kr1` / `5kr2` | https://the5krunner.com/2026/09/02/sugar-sense-libre-dexcom-and-nightscout-glucose-on-garmin/ · https://the5krunner.com/2026/09/21/libre-3-plus-review/ |
| `ds` | https://diabetesstrong.com/sugar-sense-app-review/ (sponsored) |
| `yt` / `fb` / `ig` / `tt` / `li` | https://www.youtube.com/@sugarsenseapp · https://www.facebook.com/sugarsenseapp · https://www.instagram.com/sugarsenseapp · https://www.tiktok.com/@sugar.sense · https://www.linkedin.com/company/sugarsenseapp |

## In Sukrara

Nothing has been brought in yet. When a feature from this report is built, this section records what was added and
what was left out, with the reason.
