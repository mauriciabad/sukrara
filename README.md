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
bar, home screen widgets, lock screen and notifications, so you rarely need to open the app. It reads a **FreeStyle
Libre 3** straight from the sensor, and [other sensors](#compatible-cgm-sensors) directly or through Juggluco. It
alerts you when glucose goes low or high or stops arriving, with minimal alarm fatigue thanks to smart features and
discreet sound packs, and lets you log insulin and carbs in seconds. The people who look after you see your glucose
live on their Android phone or in their **Mac menu bar** (or your own), shared **end-to-end encrypted** over public
Nostr relays or Bluetooth nearby. There's no cloud account to create and no server to host. It's compatible with the
DIY diabetes ecosystem (xDrip+, Nightscout...), and its glucose charts are advanced, beautiful and easy to understand.
The name is Catalan-ish: *sucre* (sugar) + *ara* (now).

> **Important disclaimer: a DIY project**
>
> I made Sukrara for myself and share it with everybody. I use it every day, at my own risk, and it works fine, but I
> can't guarantee it. Anyone else who uses it does so at their own risk too. The apps are still in development: all
> the core features are there, but many more improvements and cool features are coming. See also [Legal](#legal).

## What it does

Every behaviour, with every rule and number, is in [docs/FEATURES.md](docs/FEATURES.md).

### Interconnectivity

- Send to LibreView, Tidepool, Nightscout and Health Connect.
- Can act as an xDrip+ or Nightscout API, and broadcast glucose to apps like AAPS.
- Sukrara companion apps:
  - Family and friends see your glucose live on their devices.
  - Via Bluetooth when nearby, or Nostr (internet) when far away.
  - Platforms: macOS and Android.
  - End-to-end encrypted, no server to host.
- Can read glucose data from Juggluco.
- Everything stays on your phone by default.
- [Planned]: Define a formal open standard for sharing glucose over Nostr.

### Alarms and notifications

- Highly customizable alarms.
- Each alert type has its own sound and vibration.
- Sounds are discreet, with a different intensity for each alert type.
- Pick your favorite sound pack or make your own.
- Minimal alert fatigue: alerts go quiet when you've already acted.
- Optionally, alarms can ring loud, even in Do Not Disturb.
- Configure how alarms repeat while your glucose stays out of range.
- Unacknowledged very lows can alert the people who follow you.
- Quick actions in notifications, like Mute for 1 h.
- [Planned]: Predictions, more and cooler sound packs...

### Log effortlessly

- Supports smart insulin pens:
  - NovoPen Echo® Plus.
  - Tells priming apart from injections.
- Quick actions in notifications.
- Quick log pop-up by tapping the widget, the notification or the Quick Settings tile.
- Add logs from the Sukrara companion apps.
- [Planned]: Garmin watch app, MCP for AI assistants, voice assistants...

### Glucose always at a glance

- Status bar chip via Live Update.
- Anywhere in the status bar with root (e.g. next to the signal icons).
- Home screen widget. You never need to open the app.
- Lock screen widget with root. No need to unlock or touch the phone.
- Persistent notification with the widget.
- Any other device, like a Mac or a Garmin watch.
- macOS companion app with glucose in the menu bar, alarms and widgets.
- [Planned]: Android Auto, Home Assistant, Garmin, Pebble...

### Data analysis and reports

- Glucose report: time in ranges, average glucose, GMI (estimated HbA1c) and glucose variability (CV).
- Your doctor or clinic still gets your data through LibreView or Tidepool.
- Import/export your history and settings.
- Gamification: cool visual effects from 90% time in range. (More TBD)
- The home screen widget shows a lot of useful data, cleanly:
  - Glucose value and trend.
  - Time in range over the last 24 h.
  - Time since your last insulin or carbs, or time in range over 7 days.
  - 12 h glucose chart with range bands and colors.
  - Glucose distribution.
  - Markers for insulin and carb logs.
  - Handles edge cases: data gaps, a sensor about to expire, muted alarms...
- [Planned]: Advanced charts, more gamification...

### More

- Battery optimized.
- Many settings, but organized and explained. Trust the defaults.
- A nice sensor expiry flow with a Live Update and subtle reminders.
- Real-time readings from almost any CGM.
- Multilingual, but English is the main language.
- No ads, no analytics, no tracking, no account.
- Free and open source (GPL-3.0). Code on GitHub: mauriciabad/sukrara.
- Actively developed: contact me to add or change features.
- [Planned]: Massive UI/UX improvements.

### Compatible CGM sensors

Abbott's FreeStyle Libre 2, 2 Plus, 3 and 3 Plus; Dexcom G6, G7 and ONE+; Accu-Chek SmartGuide; Sibionics GS1;
CareSens Air; and LinX / AiDEX X. For some, you need to install Juggluco.

## Install

Downloads are on [GitHub releases](https://github.com/mauriciabad/sukrara/releases). What Sukrara keeps and what it
sends where: the [privacy policy](https://sukrara.mauri.app/privacy).

Android comes in two versions, the same app with the same key (installs over each other, keeping the data): the
**GitHub version** (GitHub releases, Obtainium), which **can update itself once you turn it on** (opt-in, so the
same APK can go on F-Droid: setup's *Keep Sukrara up to date* step and a banner on home and in Settings suggest it, with
*Turn on* and *Not now* side by side; Settings › About › Updates keeps the switch): once a day it asks GitHub for a new
version, outside any app store, and says so in a quiet notification (*Update*, *Release notes*; never in the alerts), downloads it, checks that it is the
release's file and signed with Sukrara's key, and installs it, never while an alert is showing or a very low is on;
monitoring comes back by itself right after. Android asks you once to allow installs from Sukrara; with *Install
updates automatically* on (Settings › About › Updates, off by default) it updates without a tap where Android allows
it ([docs/FEATURES.md › Updates](docs/FEATURES.md#updates-android-github-version)). The **Google Play version** (in
preparation) is updated by Play and therefore has no updater of its own; that is the only difference. Settings › About says which one is installed, that Sukrara isn't a medical device (setup's Welcome
too), links the privacy policy and links to the website's
[Versions](https://sukrara.mauri.app/android/#versions) ([docs/20-publishing.md › Flavors](docs/20-publishing.md#flavors)).

The Mac app likewise: the **GitHub version** (the dmg and the Homebrew cask), which **updates itself**: once a day it
looks for a new version (signed by Sukrara and checked before it installs) and offers it in the menu bar panel, with
*Install and Relaunch* in one click (Settings › General › About: *Check for Updates…*, automatic checks, automatic
install; [docs/FEATURES.md › Updates](docs/FEATURES.md#updates-mac-github-version)); and the **App Store version** (in
preparation, TestFlight first), sandboxed and updated by the Mac App Store, so without an updater of its own. Settings › General › About says which one is installed, that Sukrara isn't a medical device,
and links to the privacy policy ([docs/FEATURES.md › Mac App Store version](docs/FEATURES.md#mac-app-store-version-mac)).

- **Android** (Android 16+, arm64): download `Sukrara-<version>.apk` on the phone and open it; allow installs from
  your browser when asked. Updates install over the old version and keep your data. With
  [Obtainium](https://obtainium.imranr.dev/), add `https://github.com/mauriciabad/sukrara`.
- **Mac** (Apple Silicon, macOS 15+): download `Sukrara-<version>.dmg` and drag **Sukrara** onto **Applications**.
  It's signed with a Developer ID and notarized by Apple, so it opens like any other app. Sukrara lives in the menu
  bar (no Dock icon). Or with Homebrew: `brew install --cask mauriciabad/tap/sukrara`. Either way Sukrara then
  updates itself (the cask says `auto_updates`, so `brew upgrade` leaves it to the app; `brew upgrade --greedy` still
  works).
- **From this repo** (the owner's devices): `tools/install.sh phone|mac` builds the release, verifies it and installs
  it in place. It refuses a dirty tree, unmerged work or failing checks. On the Mac it makes Sukrara the `sukrara:`
  link handler.

## Repository layout

```
apps/
  android/       Sukrara for Android (Kotlin, Compose; Gradle root)               → apps/android/README.md
  macos/         Sukrara for Mac (Swift 6, SwiftPM; menu bar app + widget)         → apps/macos/README.md
contracts/       what every app agrees on: alert rules + scenarios, card fixtures, settings schema, protocol,
                 treatments, export, sensor traces (every app's tests read it)      → contracts/README.md
assets/brand/    app icons and brand art
docs/            FEATURES.md (behaviour catalog), ARCHITECTURE.md, DEVELOPMENT.md, QA.md, the investigation
                 reports (NN-*.md); also the sukrara.mauri.app site
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
