<p align="center">
  <img src="assets/brand/sukrara/macos/AppIcon-1024.png" alt="Sukrara icon" width="120">
</p>

<h1 align="center">Sukrara — your sugar, now</h1>

<p align="center">
  <a href="https://github.com/mauriciabad/sukrara/releases"><b>Releases</b></a>
  &nbsp;·&nbsp;
  <a href="docs/FEATURES.md">What it does, in detail</a>
  &nbsp;·&nbsp;
  <a href="https://sukrara.mauri.app">sukrara.mauri.app</a>
</p>

Sukrara shows your continuous glucose monitor (CGM) readings live on your **Android phone**, at a glance: in the status
bar, home screen widgets, lock screen and notifications, so you rarely need to open the app, and on your **Garmin
watch**: watch faces (Sukrara Aura so far, and any Connect IQ face can read Sukrara's glucose), a watch app you log from and a data field for workouts. It reads a **FreeStyle Libre 3** straight from the sensor, and [other
sensors](#compatible-cgm-sensors) directly or through Juggluco. It alerts you when glucose goes low or high or stops
arriving, with minimal alarm fatigue thanks to smart features and discreet sound packs, and lets you log insulin and
carbs in seconds. The people who look after you see your glucose live on their Android phone or in their **Mac menu
bar** (or your own), shared **end-to-end encrypted** over public Nostr relays or Bluetooth nearby. There's no cloud
account to create and no server to host. It's compatible with the DIY diabetes ecosystem (xDrip+, Nightscout...), and
its glucose charts are advanced, beautiful and easy to understand.
The name is Catalan-ish: *sucre* (sugar) + *ara* (now).

> **Important disclaimer: a DIY project**
>
> I made Sukrara for myself and share it with everybody. I use it every day, at my own risk, and it works fine, but I
> can't guarantee it. Anyone else who uses it does so at their own risk too. The apps are still in development: all
> the core features are there, but many more improvements and cool features are coming. See also [Legal](#legal).

## What it does

Every behaviour, with every rule and number, is in [docs/FEATURES.md](docs/FEATURES.md).

### Interconnectivity

- Send to LibreView, Tidepool, Nightscout and Health Connect. LibreView gets glucose from a Libre 3 Sukrara reads itself
  or a Libre 1 / 2 read through Juggluco, earlier glucose of the current sensor when you ask; home warns when LibreView
  stops getting your glucose.
- Can act as an xDrip+ or Nightscout API, and broadcast glucose to apps like AAPS.
- Sukrara companion apps:
  - Family and friends see your glucose live on their devices.
  - Via Bluetooth when nearby, or Nostr (internet) when far away. Both sides see which: your header marks the
    followers nearby, the followers list who is paired nearby, and their phone or Mac shows Nearby, Internet or Offline.
  - Platforms: macOS and Android.
  - End-to-end encrypted, no server to host.
  - Choose who can add treatments to your log when you invite them; change it any time from their sheet.
  - When someone stops following, your phone is told and lists them as a past follower, even if their phone was
    offline then; following again takes a new invite.
- Can read glucose data from Juggluco.
- Can read your (or someone else's) Nightscout site as the source, on the phone and on a Mac without Sukrara on the
  phone: glucose a few minutes late, treatments both ways (the site's read-only), the sensor's life from its Sensor
  Start (or the start you set when it has none), and never uploaded back to the same site.
- Can read LibreLinkUp as the source while the Libre app keeps the sensor (your own glucose, or someone else's with
  your LibreLinkUp account), on the phone and on a Mac without Sukrara on the phone: about 10 seconds late, 12
  hours back, the sensor's life; while the Libre app uploads your glucose to LibreView, Sukrara sends your treatments
  there beside it, as a second device (*FreeStyle LibreLink*), without touching the Libre app's upload (log each
  treatment in one app only); on the phone, take the sensor over in Sukrara with an NFC tap whenever you're ready; on the Mac, your treatments stay on the Mac. Abbott may
  change or end LibreLinkUp any day: Sukrara says so when it stops. LibreLinkUp stops during LibreView's maintenance
  too: Sukrara says so and until when (Abbott's status page), warns before a planned window, and the no-data alert
  says why.
- Everything stays on your phone by default.
- [Planned]: Define a formal open standard for sharing glucose over Nostr.

### Alarms and notifications

- Highly customizable alarms.
- Each alert type has its own sound and vibration.
- Sounds are discreet, with a different intensity for each alert type.
- Pick a sound pack for every alert, or a different one per alert. On Android, any sound on your phone too.
- Minimal alert fatigue: alerts go quiet when you've already acted, and wait while your glucose is clearly heading
  back into range.
- An alert on screen shows the current value, updated silently, and stays up while you're still out of range.
- One notification on Android: the glucose notification rings as the alert itself, no duplicate. Optionally, alerts
  also as their own notifications for your watch.
- Optionally, alarms can ring loud, even in Do Not Disturb.
- Configure how alarms repeat while your glucose stays out of range.
- Falling fast / rising fast alerts on the readings' trend, once the fast move has lasted 5 minutes.
- Unacknowledged very lows can alert the people who follow you.
- Quick actions in notifications: Treatment, Remind less, Mute (15 min to 2 h, or a length you type).
- On a Garmin watch with the Sukrara watch app: the alert on your wrist with the same three actions when it rings on
  your locked phone (never twice with the phone in your hand, nor again once you've seen it), and Mute all alerts from
  the watch.
- [Planned]: Predictions, more and cooler sound packs...

### Ready in a few steps

- Setup asks only what alerts and monitoring need: whose glucose (yours or someone else's, two illustrated choices) and
  where it comes from (your sensor, Juggluco, a Nightscout site, LibreLinkUp, or another Sukrara), then permissions, the sensor and alert levels;
  followers, insulin pens, updates and your old history come after, right away or later from Settings › Finish setup.
- Change whose glucose and its source any time in Settings › Source: the same app wears or follows, no reinstall. The
  Mac asks only where the glucose comes from: names come from the source.
- One person, one link: a device follows a Sukrara or reads another source, never both. Picking another source while
  following asks first; leaving asks which settings and history the device keeps, and the wearer is told.
- A feature you use that Android or macOS blocks (a missing permission, a login item waiting for you) never fails
  silently: home or the menu bar panel says so, with Allow and Turn off.

### Log effortlessly

- Supports smart insulin pens:
  - NovoPen Echo® Plus.
  - Tells priming apart from injections.
- Quick actions in notifications.
- Quick log pop-up by tapping the widget, the notification, an alert's Treatment or the Quick Settings tile.
- Set when it happened by scrolling back the minutes and hours in one wheel, or with one tap (5m, 15m, 30m, 1h).
- Add logs from the Sukrara companion apps.
- Your last 24 h of logs on the home screen, and a Treatments page with 31 days: each treatment with its chart marker,
  the glucose and trend when you logged it, and a mark only when it isn't in LibreView yet. Tap a treatment to
  edit it or add a note (pen doses too) in a sheet; swipe it away to delete, with Undo.
- From a Garmin watch with the Sukrara watch app: rapid or long insulin or carbs in a few presses, your usual amounts
  a tap away, hold START to log, Undo for 10 s. With the phone out of reach the watch keeps the entry and sends it,
  with its own time, when the phone is back.
- [Planned]: MCP for AI assistants, voice assistants...

### Glucose always at a glance

- Your glucose in the status bar as a Live Update chip, as the notification icon (value and trend, value, trend or
  just Sukrara's icon) with the graph in the notification, or with root as text anywhere in the status bar (e.g. next
  to the signal icons), its notification optional: one choice in setup and in Settings › Status bar & notification,
  root checked and picked for you when the phone has it. Or nothing at all: monitoring and every alert keep working.
- Home screen widget. You never need to open the app.
- Lock screen widget with root. No need to unlock or touch the phone. The glucose notification steps aside while it
  shows; alerts and anything that needs you stay.
- Persistent notification with the widget.
- macOS companion app with glucose in the menu bar, alarms and widgets.
- Watch faces for Garmin: Sukrara Aura, for watches with a round 390 × 390 AMOLED screen (Venu 3S, vívoactive 6,
  Forerunner 570, Descent Mk3i 43 mm …), with four fields of your choice: time in range, your last treatment or anything the watch
  has (steps, heart rate, weather, sleep, training…).
- Sukrara, the watch app for Garmin: your glucose in the watch's glance list, a page with the value, rings and a chart
  of the last hours, a page with the glucose report (time in ranges, average, GMI, CV over 1 to 30 days), and
  four complications that put your glucose, its trend and history, or a Log shortcut on any watch face that takes
  them.
- Sukrara data field for Garmin: your glucose and its recent line on an activity's data screen, a full-page alert
  during the activity if you turn it on, and your glucose saved in the activity, charted in Garmin Connect.
- Other Garmin faces and data fields made for xDrip+ (Dotscout, with its data source set to xDrip+) read Sukrara's
  xDrip+ server, and any Connect IQ watch face can read Sukrara's own watch-face data with the SukraraFace Monkey Barrel
  (`apps/garmin/sdk`, 0BSD: the fetch, the version, never an old value as current, the unit and LO / HI, typed
  fields; built on every release as `SukraraFace-<version>.barrel`, the newest always at
  `releases/latest/download/SukraraFace.barrel`) and its JSON Schema (the developer page:
  sukrara.mauri.app/developers/garmin-faces/, with a sample face in `apps/garmin/sample`).
- Getting the Garmin apps going from the phone: a setup step when Garmin Connect is on the phone (what each app gives,
  sending on or off, each one's Connect IQ store link), and a home tip with *Turn on* when a watch has
  one of Sukrara's apps but sending is off.
- A trend arrow you can trust: worked out from your readings (minutes sooner than the sensor's own arrow), turning
  smoothly with how fast glucose moves, and the same on every screen. Quick, Balanced, Smooth, or the sensor's own.
- [Planned]: Android Auto, Home Assistant, Pebble...

### Data analysis and reports

- Glucose report: time in ranges, average glucose, GMI (estimated HbA1c) and glucose variability (CV).
- Your doctor or clinic still gets your data through LibreView or Tidepool.
- Import/export your history and settings (Settings › Import and export).
- Gamification: cool visual effects from 90% time in range. (More TBD)
- The home screen widget shows a lot of useful data, cleanly:
  - Glucose value and trend.
  - Time in range over the last 24 h.
  - Time since your last insulin or carbs as the big number, its amount under it, or time in range over 7 days.
  - 12 h glucose chart with range bands and colors.
  - Glucose distribution.
  - Markers for insulin and carb logs.
  - Handles edge cases: data gaps, a sensor about to expire, muted alarms...
- [Planned]: Advanced charts, more gamification...

### More

- Battery optimized.
- Many settings, but organized and explained: Alerts, Readings (unit, ranges, trend arrow), Graph, Metrics (what
  sits beside the value: time in range, GMI, average, variability, the last treatment, each on or off), Sensor end, your data; rare ones in Alerts › Fine-tuning; search finds every one, old names included. Trust the defaults.
  A setting that can differ in the notification or the widgets (time shown, the sensor's time left) is one row that
  says the app's value and only the places that differ; its page sets each place or keeps it the same as the app.
- A nice sensor expiry flow with a Live Update and subtle reminders, whatever the source: where it doesn't say how
  long a sensor lasts (LibreLinkUp's Libre 3 or 3 Plus, Nightscout, Juggluco) you pick the length, and where it doesn't
  say when it started you set the start.
- Real-time readings from almost any CGM.
- Multilingual, but English is the main language.
- No ads, no analytics, no tracking, no account.
- Free and open source (GPL-3.0). Code on GitHub: mauriciabad/sukrara.
- Actively developed: contact me to add or change features.
- [Planned]: Massive UI/UX improvements.

### Compatible CGM sensors

- **Directly:** Abbott's FreeStyle Libre 3 and 3 Plus. Not yet tested on a real sensor: Dexcom G6, G7 / ONE+ and
  G7 15 Day / Stelo; Accu-Chek SmartGuide; Aidex X / LinX.
- **Through [Juggluco](https://github.com/j-kaltes/Juggluco)** (Sukrara reads what Juggluco reads): FreeStyle Libre 2,
  2 Plus, 3 and 3 Plus; Sibionics GS1; Dexcom G7 / ONE+; Accu-Chek SmartGuide; CareSens Air; Aidex X.

## Install

Downloads are on [GitHub releases](https://github.com/mauriciabad/sukrara/releases); Android also on
[Google Play](https://play.google.com/store/apps/details?id=app.mauri.sukrara). What Sukrara keeps and what it
sends where: the [privacy policy](https://sukrara.mauri.app/privacy).

Android comes in two versions, the same app with the same key (installs over each other, keeping the data): the
**GitHub version** (GitHub releases, Obtainium), which **can update itself once you turn it on** (opt-in, so the
same APK can go on F-Droid: setup's *Keep Sukrara up to date* step and a banner on home and in Settings suggest it, with
*Turn on* and *Not now* side by side; Settings › About › Updates keeps the switch): once a day it asks GitHub for a new
version, outside any app store, and says so in a quiet notification (*Update*, *Release notes*; never in the alerts), downloads it, checks that it is the
release's file and signed with Sukrara's key, and installs it, never while an alert is showing or a very low is on;
monitoring comes back by itself right after. Android asks you once to allow installs from Sukrara; with *Install
updates automatically* on (Settings › About › Updates, off by default) it updates without a tap where Android allows
it ([docs/FEATURES.md › Updates](docs/FEATURES.md#updates-android-github-version)). The [**Google Play
version**](https://play.google.com/store/apps/details?id=app.mauri.sukrara) is updated by Play and therefore has no updater of its own; that is the only difference. Settings' *About* row shows the version (or the update found); the page says which one is installed, that Sukrara isn't a medical device (setup's Welcome
too), links the privacy policy and links to the website's
[Versions](https://sukrara.mauri.app/android/#versions) ([docs/20-publishing.md › Flavors](docs/20-publishing.md#flavors)).

The Mac app likewise: the **GitHub version** (the dmg and the Homebrew cask), which **updates itself**: once a day it
looks for a new version (signed by Sukrara and checked before it installs) and offers it in the menu bar panel, with
*Install and Relaunch* in one click (Settings › General › Software Update: *Check for Updates…*, automatic checks, automatic
install; [docs/FEATURES.md › Updates](docs/FEATURES.md#updates-mac-github-version)); and the **App Store version** (in
preparation, TestFlight first), sandboxed and updated by the Mac App Store, so without an updater of its own. About Sukrara (app menu, right-click menu, Settings › General) says which one is installed, that Sukrara isn't a medical device,
and links to the privacy policy ([docs/FEATURES.md › Mac App Store version](docs/FEATURES.md#mac-app-store-version-mac)).

- **Android** (Android 16+, arm64): from [Google Play](https://play.google.com/store/apps/details?id=app.mauri.sukrara), or download `Sukrara-<version>.apk` on the phone and open it; allow installs from
  your browser when asked. Updates install over the old version and keep your data. With
  [Obtainium](https://obtainium.imranr.dev/), add `https://github.com/mauriciabad/sukrara`.
- **Mac** (Apple Silicon, macOS 15+): download `Sukrara-<version>.dmg` and drag **Sukrara** onto **Applications**.
  It's signed with a Developer ID and notarized by Apple, so it opens like any other app. Sukrara lives in the menu
  bar (no Dock icon). Or with Homebrew: `brew install --cask mauriciabad/tap/sukrara`. Either way Sukrara then
  updates itself (the cask says `auto_updates`, so `brew upgrade` leaves it to the app; `brew upgrade --greedy` still
  works).
- **Garmin**: from the Connect IQ store
  ([Sukrara Aura](https://apps.garmin.com/en-US/apps/2859fe48-7e9e-4e52-ae2a-a319fa76fcfc),
  [the watch app](https://apps.garmin.com/en-US/apps/c8c7b5e9-35e6-4941-b811-3bf7c9238c9a),
  [the data field](https://apps.garmin.com/en-US/apps/bae83264-c160-4498-8a4d-492b4ea7d630)), or without the store from
  the release: `<App>-<version>-sideload.zip` holds one `.prg` per watch model, to copy into the watch's `GARMIN/APPS`
  folder (it doesn't update itself; the store's copy replaces it). All need Sukrara for Android and Garmin Connect on
  the phone:
  - Watch faces: Sukrara Aura (round 390 × 390 AMOLED: Descent Mk3 / Mk3i 43 mm, Venu 3S, Venu 4 41 mm,
    vívoactive 6, Forerunner 570 42 mm, epix Pro (Gen 2) 42 mm, MARQ (Gen 2) and MARQ Aviator, Approach S70 42 mm).
  - Sukrara, the watch app, and Sukrara data field (every watch with Connect IQ 5 or newer: fēnix 7, 8, 9 and E,
    epix (Gen 2) and epix Pro, enduro 3 and 4, Forerunner 70, 165, 170, 255, 265, 570, 955, 965 and 970, Venu 2,
    3 and 4, Venu Sq 2, Venu X1, vívoactive 5 and 6, Instinct 3 AMOLED and Instinct Crossover AMOLED, MARQ (Gen 2)
    and MARQ 3, Descent Mk3 and G2, Approach S50, S70 and S72, D2 Air X10, Mach 1 and Mach 2; their Pro, Solar,
    S, X, Plus and Music editions too). Alerts on the wrist need Connect IQ 5.1: on the Venu 2 family, Venu Sq 2 and D2 Air
    X10 the phone's own alert notification, forwarded by Garmin Connect, stays the one you feel.
- **From this repo** (the owner's devices): `tools/install.sh phone|mac` builds the release, verifies it and installs
  it in place. It refuses a dirty tree, unmerged work or failing checks. On the Mac it makes Sukrara the `sukrara:`
  link handler. Such a build isn't a release and says so: About shows *Development build · 0.14.5 + 23 commits ·
  d744fd74 · 14 Nov*, and the updater offers only a newer release.

## Repository layout

```
apps/
  android/       Sukrara for Android (Kotlin, Compose; Gradle root)               → apps/android/README.md
  macos/         Sukrara for Mac (Swift 6, SwiftPM; menu bar app + widget)         → apps/macos/README.md
  garmin/        Sukrara on Garmin (Monkey C, Connect IQ): the faces, the app, the field → apps/garmin/README.md
contracts/       what every app agrees on: alert rules + scenarios, card fixtures, settings schema, protocol,
                 treatments, export, sensor traces (every app's tests read it)      → contracts/README.md
assets/brand/    app icons and brand art
docs/            FEATURES.md (behaviour catalog), ARCHITECTURE.md, DEVELOPMENT.md, QA.md, the investigation
                 reports (NN-*.md); published as the website's developers and research pages
website/         sukrara.mauri.app (Astro + Starlight): the apps' pages in English and Spanish, privacy policy,
                 invite fallback, sound test, the Mac update feed and App Links files → website/README.md
tools/           check/ (CLAUDE.md as checks), hooks/, install.sh, release/, settings/gen.py
poc/             the frozen research PoC: spikes/ and spec/ (Open Glucose Stream); reference only
third_party/     local only (gitignored): Juggluco, the Libre 3 native core's source
```

Each app is a folder in `apps/` with its own build and README; every app tests itself against the same `contracts/`
and the same `tools/check` rules, so a new app is one more folder.

## Build and test

Setup (JDK 21, Android SDK, Xcode, the Juggluco checkout, signing) is in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md);
the rules in [CLAUDE.md](CLAUDE.md); the design in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

| What | Command |
|---|---|
| Repo rules (run before committing; the git hooks run it after `tools/hooks/install.sh`) | `tools/check/run.sh` |
| Android: one module's tests | `cd apps/android && ./gradlew :core:alerts:test` |
| Android: debug APK (GitHub build; `assemblePlayDebug` for the Google Play build) | `cd apps/android && ./gradlew :app:assembleFullDebug` |
| Android: everything CI runs | `cd apps/android && ./gradlew check` |
| Mac: build + tests | `cd apps/macos && swift build && swift test` |
| Website: live preview / what CI runs | `cd website && npm run dev` / `npm run build && npm run check` |
| Mac: app bundle with the widget | `cd apps/macos && Bundle/bundle.sh` (`--unsigned` without the signing identity; the provisioning profiles in `~/.apple-dev/profiles` are used when there, `--no-profile` skips them) |
| Settings code after a schema change | `python3 tools/settings/gen.py` |
| A release to every channel (GitHub, Sparkle, Homebrew, Play internal testing, TestFlight) | `tools/release/release.py <version>` ([docs/DEVELOPMENT.md › Release](docs/DEVELOPMENT.md#release)) |
| Release builds | `tools/release/android-release.sh`, `tools/release/play-bundle.sh` (Google Play), `tools/release/mac-release.sh`, `tools/release/mac-appstore.sh` (Mac App Store) |

Manual checks per surface: [docs/QA.md](docs/QA.md). Release history: [CHANGELOG.md](CHANGELOG.md). The investigations, one
report each: `docs/NN-*.md`.

## Legal

Sukrara is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. Don't make
treatment decisions based on it. Use at your own risk. All product and company names mentioned are trademarks of their
respective owners. Sukrara is an independent project, not affiliated with them.

- Values may be wrong, late or missing, and alerts may not ring.
- The sensor is read for interoperability with the author's own glucose data. Per-sensor pairing data is a secret
  and is never committed here.
- The direct-sensor code also compiles [Juggluco](https://github.com/j-kaltes/Juggluco)'s GPLv3 sources, which are
  fetched separately and not included in this repo.

## License

[GPL-3.0-or-later](LICENSE). Use it, change it, share it — but anything you build on it has to stay open source
under the same terms, and if you distribute a binary you have to offer its source too.

```
Copyright (C) 2026 Maurici Abad

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.
```
