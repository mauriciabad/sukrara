---
title: Sukrara for Android
---

<!-- Sukrara — https://github.com/mauriciabad/sukrara · Copyright (C) 2026 Maurici Abad · SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- https://sukrara.mauri.app/android/ — opened from Sukrara for Mac (Settings › General › Sukrara on Other Devices);
     #versions from Sukrara for Android (Settings › About › About the versions) -->

[← Sukrara](../)

# Sukrara for Android

The phone you wear the sensor with: it reads the sensor, shows your glucose everywhere on the phone, alerts you, and
shares it with the people and devices you choose. Or follow someone else's glucose with alerts of your own.

<p>
  <img src="../img/home/home.png" alt="Home: the glucose card with the value, the trend arrow and a 12 h chart, a backup reminder above it" width="206">
  <img src="../img/widgets/widgets-light.png" alt="Home-screen widgets: the glucose number with a trend arrow, time-in-range rings and a 12 h chart" width="270">
</p>

## What it does

- **Reads the sensor itself:** FreeStyle Libre 3 / 3 Plus over Bluetooth after one NFC tap, or Juggluco's web server.
  Dexcom, Accu-Chek SmartGuide and Aidex sensors are supported but untested.
- **Glucose everywhere on the phone:** the app, home-screen widgets, the notification, the status bar, and
  the lock screen (root + LSPosed).
- **Alerts:** very low to extremely high, fast changes and no data, each with your own threshold and sound; quiet
  after a treatment, never quiet when it matters.
- **Treatments:** insulin, carbs and notes, also from the lock screen; NovoPen doses by holding the pen to the phone.
- **Sharing:** followers on other phones and on [Sukrara for Mac](../mac/), end-to-end encrypted, over Bluetooth nearby
  or Nostr relays from anywhere. No account, nothing to host.
- **Other apps and services** (off until you turn them on): LibreView, Tidepool, Nightscout, Health Connect, AAPS,
  xDrip+ and Garmin watches.

Every behaviour, with its rules and numbers: the [behaviour catalog](../FEATURES) (for example
[alerts](../FEATURES#alerts), [widgets](../FEATURES#home-screen-widgets),
[sharing](../FEATURES#share-with-followers-wearer)).

## Requirements

Android 16 or newer on an arm64 phone, with NFC and Bluetooth to read the sensor directly.

## Get it

- **APK from GitHub:** download `Sukrara-<version>.apk` from the
  [latest release](https://github.com/mauriciabad/sukrara/releases) on the phone and open it; allow installs from your
  browser when asked. Updates install over the old version and keep your data; the app tells you in Settings › About
  when there is a new one.
- **[Obtainium](https://obtainium.imranr.dev/):** add `https://github.com/mauriciabad/sukrara` and turn on *Include
  prereleases*; it installs the GitHub releases and their updates for you, and checks they're signed with Sukrara's key.
- **Google Play:** in preparation.

## Versions

Sukrara for Android comes in two versions: the **GitHub version** and the **Google Play version**. They are the same
app, built from the same source code and signed with the same key, so either one installs over the other and keeps
your data. Settings › About shows which one you have, next to the version number.

| | GitHub version | Google Play version |
|---|---|---|
| Updates | Sukrara checks GitHub for a newer release when you open Settings › About and takes you to it; Obtainium can install it for you | Google Play updates it, like any app from Play |
| Where to get it | [GitHub releases](https://github.com/mauriciabad/sukrara/releases), [Obtainium](https://obtainium.imranr.dev/) | Google Play (in preparation) |
| Features | all of them | all of them |

**What the Google Play version leaves out:** only the update check, because Google Play brings the updates. Everything
else, alerts, sharing, widgets and the glucose in the status bar included, is in both. If a store version ever has to
leave something else out, it is listed here.

> **A preview, not a medical device.** Releases are prereleases until they've been tested on a real sensor. Don't
> make treatment decisions from it.

[Privacy policy](../privacy) · [Source code](https://github.com/mauriciabad/sukrara) (GPL-3.0-or-later) ·
[Report a problem](https://github.com/mauriciabad/sukrara/issues)
