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

Sukrara shows live glucose from a **FreeStyle Libre 3** on your **Android phone** (read straight from the sensor) and
on the **Mac menu bar** of the people who look after you (or your own). It alerts you when glucose goes low or high or
stops arriving, lets you log insulin and carbs in seconds, and shares readings **end-to-end encrypted** over public
Nostr relays or Bluetooth nearby. There's no cloud account to create and no server to host. The name is
Catalan-ish: *sucre* (sugar) + *ara* (now).

> **Not a medical device** (see the [disclaimer](#disclaimer)). Releases are GitHub **prereleases** until they've been
> tested on a real sensor.

## What it does today

The full catalog, with every rule and number, is [docs/FEATURES.md](docs/FEATURES.md). In short:

**Android (you wear the sensor)**
- **Reads the Libre 3 / 3 Plus directly**: hold the phone to the sensor to start a new one or take over a running one
  (NFC), then Sukrara keeps it connected over Bluetooth by itself, with 24 h backfill and automatic reconnects. It
  uses your **LibreView account's receiver ID** (sign in once, only to get the ID), so a sensor can move between
  Sukrara and Abbott's app. Or read from **Juggluco's local web server** if Juggluco keeps the sensor.
- **Other sensors, untested**: Dexcom G7 / ONE+ / Stelo (read along with the Dexcom app), Dexcom G6 / ONE, Accu-Chek
  SmartGuide and Aidex X / LinX / GlucoRx. They were never tested with a real sensor: the new sensor screen tags them
  *Untested* and asks you to confirm before pairing; while you wear one, home asks for feedback (*Send feedback*
  opens a pre-filled GitHub issue with nothing personal) and Settings › Sensor names the family and why a connected
  sensor sends no value.
- **LO / HI** from any sensor show as *LO* / *HI*, never a made-up number, and sound the very low / extremely high
  alert at once (Android and Mac).
- **mg/dL or mmol/L** (Settings › Ranges; Mac Settings › General): every number you read or type follows it, on both
  apps: the card, the chart's range labels, notifications, alerts, widgets, the status bar, the Mac menu bar,
  TalkBack / VoiceOver, the report, the export and every glucose setting (mmol/L with one decimal, `7,8` in Spanish).
  Values stay stored in mg/dL, so switching never moves a threshold.
- A guided **new sensor** screen: a step bar (Sensor · Scan · Pair · Readings), which sensor, a drawing of where to hold
  the phone (this phone's NFC antenna) with tips after 10 s, ticked pairing steps and *Taking longer?* after 45 s, the
  warm-up countdown in a ring, haptics on the tap, the screen kept on, every failure handled on the same screen
  (ended sensor, Libre 1/2, a tag that isn't a sensor, another app holding the sensor, Bluetooth off, a wrong ID or PIN).
- **Settings › Sensor**: the current sensor (status, serial, start, end and time left), the reading source (Libre 3
  directly or Juggluco, saying first what changes; Juggluco's web-server API secret, kept encrypted), the LibreView account (email and receiver ID, sign in / out; the
  password is never stored) and *Pair new sensor*.
- **Home screen**, glucose first: the value, trend arrow (an out-of-range value says *Low* / *High* in words too, on
  every card: never by colour alone; a live reading says *now*) and a 6 h chart (its words follow a larger system font)
  you can scroll and pinch-zoom through the
  last 31 days (7 / 14 / 30-day rings count them too), treatments on the chart with their mute lines and the last one
  beside the value in a ring of its quiet time left (on the widgets and the notification too), a pill that acts (*Eat carbs* opens Add treatment with Carbs
  focused, a mute's pill its ways out), a chip for each other quiet alert, a **Mute alerts** button (30 min … 8 h,
  until you turn it off, custom) that becomes the countdown with *Unmute* / *Change time*, the sensor's end date with *Pair new sensor* in its last day,
  one banner at a time for what needs you (monitoring stopped, alerts may not ring,
  damaged saved data, finish setup), the last 24 h of treatments with delete + undo, and a **glucose report** card (1 / 7 / 14 / 30 days: time in the five ranges with their goals, average, GMI, CV, how much of the period has data). Never a bare "---": when
  there's no value it says why and what to do (Bluetooth off, the sensor used by another app, a scan or a new pairing
  needed, the phone's clock behind the readings, the sensor warming up or ended…).
- A **live notification** that is also a **Live Update chip** next to the clock (Android 16 QPR2+), with the same
  freshness steps as the card: from 5 min the number is struck through, from 20 min it's gone. Tap → *Add treatment*.
  Without the chip it is a card in your range's colour with the graph, its icon the number itself (Settings › Status
  bar); on Android 17 the Live Update expands to the value styled by range.
- **On a rooted phone** (KernelSU / Magisk): the value and its arrow as **system icons** beside Wi-Fi and the battery;
  with Sukrara's **LSPosed module** in System UI also as text beside the clock, in its range colour, placed next to the
  icon you choose and first in line when the status bar is crowded (Settings › Status bar › With root).
- **Alerts**: very low, low, high, very high, rising / falling fast and no data; each on its own channel,
  "like an alarm" ones pass Do Not Disturb; alerts that come close together still ring in full (Android's notification
  cooldown doesn't quieten them; with *Vibrate only when unlocked* on, Sukrara plays the alert sound itself while you use the phone). *Mute…*, *Remind less*, *Unmute* and *Change time* on the notification,
  in the app and with TalkBack; *Mute all* with a visible strip to undo it; every quiet alert with its ways out
  (*Unmute*, *Alert me anyway*, *Remind as usual*, *Change to*) on home and in Settings › Alerts › *Active now*. Settings › Alerts › *Lately* shows what alerted, what you muted, when Mute all ran out and what came back after a restart in the last day. The no-data check runs from an exact alarm,
  independent of the monitoring service. After a restart, before you first unlock the phone, Sukrara can't read your
  sensor (Android keeps its storage locked): it says *Unlock to resume monitoring* on the lock screen and rings as *No
  data* when that alert would have.
- **Sensor end**: reminders you set (up to six, 0–3 days before at a time; earlier at the first unlock; one the
  evening before a night-time end), a live countdown in the sensor's last hours, a warm-up countdown with *Sensor
  ready*, *Sensor ended* with *Replace sensor* (it opens the new-sensor flow; again every 2 h, or as you set it, out of
  your night until you start a new sensor), and optionally the sensor's end as an event in Sukrara's own calendar or
  one you pick (Settings › Sensor end).
- **Monitoring** in one foreground service that comes back by itself (boot, update, a 15 min health check); before the
  first reading every surface says *Not monitoring: no sensor yet* and no no-data alarm is armed.
- **Treatments**: a quick dialog for rapid / long insulin, carbs and a note (from the notification, the Quick
  Settings tile *Add treatment*, which also shows your glucose, or home), *now*, a time or *minutes ago*, a line saying which alerts it quiets and for
  how long, and a short countdown when opened on the lock screen (a ring on *Cancel*, also in the *When?* picker, and
  one spoken *Closes in 10 seconds* with TalkBack).
- **Home-screen widgets** (*Glucose*, *Glucose graph*, *Glucose and graph*) drawn by the same card renderer at the
  widget's real size (*Glucose* down to one cell), with the same card as their picker preview; home offers *Add
  widget* until a graph widget is placed (or says how to add it by hand when the launcher can't) and then opens its
  settings; a tap opens *Add treatment* (a new sensor once yours has ended); they switch to the dark theme with the
  phone.
- **Settings** for daily use (every row searchable, accents optional, your recent searches one tap away; an icon on every row): alerts (an on/off switch on each alert row, thresholds you confirm, repeats, sounds,
  quiet rules, *Play sound* and *Send a test alert*), an alert reliability checklist with *Fix* buttons
  (notifications, alarms, full-screen, battery, Do Not Disturb and Do Not Disturb access, sound while the phone is unlocked, Bluetooth; an alert Android refused shows on home with
  *Copy report*), ranges and the whole graph (lines, time
  per place, slots, ring order, treatments on the graph, sensor time; your card live on top), lock screen
  *Time to save*, sensor (*Forget this sensor*, *Open Sukrara when you scan a sensor*), your profile, backup and data
  export from the overview, Advanced (export / import / reset your settings as a file, the relays with *Reconnect
  now*, the log, your sharing identity, counts of what was sent, stored and refused), and About with an update check
  against this repo's releases.
- **Insulin pens** (NovoPen Echo Plus, NFC): hold the pen to the unlocked phone from any app and Sukrara logs the
  insulin you injected, priming kept apart, never twice (a typed dose is replaced, a typed duplicate asks *Already logged
  by your pen?*), with *Undo*; it asks when unsure, on a card under the glucose card (*Priming or injected?*, *Same
  injection?* with your typed entries to choose from). Holding the pen while Add treatment is open fills the dose and
  its time in the dialog (already logged, with *Undo*). Uploads wait until the pen's session is over. Settings › Insulin pens lists your pens: each pen's page (name,
  insulin type, forget), its *Doses read* with per-dose *Priming* / *Injected* corrections (also from the entry's row
  on home, *from the pen*, read only on a follower), the untested NovoPen 6's display check, where this phone's NFC
  antenna is, and what gets in the way (tag intents blocked, NFC off, Juggluco also reading pens) with the way to fix
  it. A read that brings injections older than 3 days (the pen not read for days) logs them and asks you to *Review*
  them, with *Undo this read*.
- **Sharing core**: your sharing identity, restorable from its recovery code so existing followers keep following;
  readings, alerts and your sensor (a new one warming up, its end) go to approved followers over Nostr relays
  (protocol v2), the sensor even before its first reading. *How readings travel* turns sharing over
  the internet off (Bluetooth followers keep every reading) or sends readings in batches every few minutes instead of
  each one. If another device is publishing as the same identity, Sukrara stops and says so instead of fighting it.
- **First-run setup** in a calm, fixed order: role (confirmed) → what alerts need → sensor → confirm your alert
  thresholds → your followers (restore once this phone has your sensor, or start fresh) → home. Any
  step can wait; home's *Finish setup* banner brings it back.
- **Sharing screens**: *How you appear* (your name and a generated picture, on home's top bar), invite one person (QR
  that springs in, link to send or copy, works once for 24 h), allow or deny their request after comparing a 4-digit
  code (the dialog shows over any screen, with their picture and the invite they used), see followers with their
  pictures, let one *add entries*, remove them (confirmed; removed ones fold away), **Backup** to your password manager
  or a recovery code (home says *Not backed up* until then; setup restores from either), **Replace this identity**
  without reinstalling (restore another one or start fresh, confirmed with the followers who lose your readings named),
  and a clear screen when another device is sharing as you.
- **Bluetooth nearby**: followers' phones and Macs in range get your readings straight from your phone, even without
  internet (on by default, *Over Bluetooth nearby*); Settings › Sharing lists the devices connected now and the ones
  paired nearby that are out of range, says when
  Bluetooth is off or the Nearby devices permission is missing, and offers the fix; the live notification says
  *2 followers · 1 nearby* (and *Nightscout API on Wi-Fi* while devices on your home Wi-Fi can read it).
- **Follow someone from an Android phone**: paste (or open) the wearer's invite link, scan their QR code (camera or a
  picture of it, read on the phone), see who is inviting (their name and picture from the invite) and tap *Follow* or
  *Cancel*, or find their phone nearby over
  Bluetooth while they have *Pair nearby* on (Settings › Sharing; both phones show the same 6-digit code and you each
  confirm it, so a stranger's phone can't pose as theirs), wait for them to allow you (after a link, with the 4-digit
  code they may ask for), then their readings, sensor, entries and alerts arrive over the relays and drive
  home, widgets, the live notification and your own alerts (including *no data*); near their phone they also arrive over
  Bluetooth, without internet. Add entries when they let you;
  notifications say when an invite was used, declined or your access removed; *Stop following* in Settings › Sharing.
  **Settings from** (Settings): use the wearer's ranges, graph and alert rules (kept up to date), Sukrara's defaults,
  or your own (start them by copying the wearer's or from the defaults); sounds and no-data alerts are always yours.
- **Share with apps**: upload your Libre 3 glucose and entries to **LibreView** for your clinic (password used once,
  never saved; asks before taking over another uploading device or uploading to another account than your sensor's; readings not sent yet survive a restart; only from the moment you switch it on; a cloud on each
  entry on home says whether LibreView has it, every 5 / 15 / 60 min or your own interval; a home banner suggests LibreView or Tidepool until one is set up), to your own **Nightscout** site (https, access token kept encrypted; 30-day catch-up,
  pauses when another app uploads to the same site; an entry another app already put there, like an AndroidAPS bolus, is
  never doubled), to **Tidepool** (both roles; a follower only after its own
  choice; password used once), an **xDrip+-compatible server** on this phone for **Garmin** watches (off until you
  switch it on; loopback only), a **Nightscout API on this phone** for apps like xDrip+ and GlucoDataHandler (off by
  default, API secret required; an extra opt-in also serves devices on your home Wi-Fi with a plain warning and its
  own Wi-Fi secret you can replace any time),
  **glucose broadcasts** to the apps you allow (AndroidAPS, GlucoDataHandler, xDrip+: the Libre 3 read by this phone
  only, one format per app, pinned to the app's signature), **Health Connect** (opt-in: this phone's own sensor
  readings, the last 30 days first), and **Export data** as a LibreView-layout CSV to save or share.
- English and Spanish (Spain) everywhere.
- **TalkBack hears one sentence** for the glucose card everywhere (home, widgets, the notification card, the Quick
  Settings tile): the value, trend, range in words and age, the alert or mute, the slots and the chart summary, the
  same words as the Mac's VoiceOver.

**Mac (you follow someone, from the menu bar)**
- The value in its range colour, the card's trend arrow at its angle and the mute bell in the **menu bar**, struck
  through when old, a symbol when readings stop or the sensor ends, with a tooltip; click for the **panel** with the
  card and chart (drawn by the same renderer rules as Android, tested against the same fixtures); scroll, pinch or
  two-finger double tap the chart through the stored 31 days. The card's pill is
  pressable (Eat carbs / Take insulin open Add Treatment; a mute opens its ways out).
- **Follow** a wearer with an invite link (pasted, dropped or opened, `https://sukrara.mauri.app/join#…` or
  `sukrara:`), a QR code image or **nearby** Bluetooth pairing (the Mac and the phone show the same 6-digit code to
  confirm before anything is followed); a link always shows who is inviting first, with *Follow* / *Cancel*; readings,
  alerts and
  treatments arrive end-to-end encrypted. Choose the name the wearer sees for this Mac and its generated picture
  (*How you appear*); the wearer's picture shows in Following and the nearby dialog. A setup window walks Welcome → Follow → Alerts → Notifications → Open at
  login.
- **Juggluco on this network** (off by default): while the Mac follows nobody, it reads glucose from Juggluco's web
  server on the home network (found automatically, or by its address; optional API secret in the Keychain), with the
  same card, rings, sensor life and alerts. Plain http, to home-network addresses only; says so when macOS's Local
  Network permission is off, with the way to turn it on.
- **Alerts** with sound packs and the same rules and timeline scenarios as Android; mute, change the time, a custom
  length, Alert Me Anyway, Remind Less and unmute from the panel; Send Test Alert.
- **Treatments**: add rapid / long / carbs / note with *When?* (a time or minutes ago) and the quiet line, see the last
  7 days by day with the phone's entries, delete your own with undo.
- **Glucose Report** (1 / 7 / 14 / 30 days: time in ranges with goals, average, GMI, CV) and **Export CSV…** by period.
- **Settings** in five panes: General (*Settings from* Wearer / Default / Custom, in Custom starting from the wearer's or Sukrara's defaults; unit, open at login, update check,
  Quit), Graph (time shown per place, treatments on the graph, lines, ranges, sensor countdown and time left), Rings
  (rings and numbers, ring order you drag, last treatment), Alerts (per alert with its summary and details, quiet times, the notification's buttons, sounds with preview, what's
  quiet now) and Following.
- The card shows the wearer's treatments on the chart with their mute lines, the last treatment beside the value and
  a pill that counts down while a treatment quiets the alert; the widgets too.
- **Desktop widgets** (Glucose, Glucose graph, Glucose + graph) with the same card; they age by themselves (struck
  through at 5 min, the graph dimmed with the reading's time, no value at 20 min) and a tap opens Add treatment.
- **No value never goes unexplained**: the panel and the widgets say why (not following anyone, waiting for the
  wearer to allow this Mac, no readings from the wearer's phone since 9:50, Juggluco not found…) and the panel offers
  the way out (*Follow Someone…*, *Following Settings…*, *Juggluco Settings…*).
- VoiceOver reads the card as the same sentence as Android's TalkBack (value, trend, range in words, age, the alert
  or mute, the slots, the chart summary) in the menu bar, the panel and the widgets; every card writes the range
  under an out-of-range value (*Low*, *Very high*) and the status item's tooltip says it.
- English and Spanish (Spain) by the Mac's language: the windows, the panel's lines and every word the card draws
  (*Come hidratos*, *quedan 45 min*, *Calentando · 40 min*), the same words as Android's.

**Not included**: the LSPosed lock-screen widget, Dexcom / Aidex / SmartGuide (untested) sensors, and the pen list and
dose review.

**Garmin watch:** switch on *Settings › Share with apps › xDrip+ server*; watch faces and data fields made for xDrip+
read it through Garmin Connect. While **Juggluco** is Sukrara's source, Juggluco keeps its own server and feeds the
watch. Upload uses a new device name per install (`Sukrara-…`), so a Nightscout site shows Sukrara as a new uploader.

## Install

Downloads are on [GitHub releases](https://github.com/mauriciabad/sukrara/releases).

- **Android** (Android 16+, arm64): download `Sukrara-<version>.apk` on the phone and open it; allow installs from
  your browser when asked. Updates install over the old version and keep your data. With
  [Obtainium](https://obtainium.imranr.dev/), turn on *Include prereleases*.
- **Mac** (Apple Silicon, macOS 15+): download `Sukrara-<version>.dmg` and drag **Sukrara** onto **Applications**. The
  app isn't notarized yet: the first open is stopped; click **Done**, then **System Settings → Privacy & Security →
  Open Anyway** ([Apple: open a Mac app from an unknown developer](https://support.apple.com/guide/mac-help/mh40616/mac)).
  Sukrara lives in the menu bar (no Dock icon). Homebrew (`mauriciabad/tap`) installs stable releases only.
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
| Android: debug APK | `cd apps/android && ./gradlew :app:assembleDebug` |
| Android: everything CI runs | `cd apps/android && ./gradlew check` |
| Mac: build + tests | `cd apps/macos && swift build && swift test` |
| Mac: app bundle with the widget | `cd apps/macos && Bundle/bundle.sh` (`--unsigned` without the signing identity) |
| Settings code after a schema change | `python3 tools/settings/gen.py` |
| Release builds | `tools/release/android-release.sh`, `tools/release/mac-release.sh` |

Manual checks per surface: [docs/QA.md](docs/QA.md). Release history: [CHANGELOG.md](CHANGELOG.md). The investigations, one
report each: `docs/NN-*.md`.

## Disclaimer

- **Not a medical device.** Values may be wrong, late or missing, and alerts may not ring. Don't make treatment
  decisions or rely on alarms from anything in this repo.
- FreeStyle Libre is a trademark of Abbott. This project is not affiliated with Abbott or with Juggluco.
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
