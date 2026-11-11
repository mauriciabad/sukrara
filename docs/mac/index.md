---
title: Sukrara for Mac
---

<!-- Sukrara — https://github.com/mauriciabad/sukrara · Copyright (C) 2026 Maurici Abad · SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- https://sukrara.mauri.app/mac/ — opened from Sukrara for Android (Settings › About › Sukrara on other devices) -->

[← Sukrara](../)

# Sukrara for Mac

Your glucose in the Mac menu bar, about a second behind the phone, with the same alerts. It follows a phone running
[Sukrara for Android](../android/): yours, or the person you look after.

<p>
  <img src="../img/mac/menubar.png" alt="The number and trend arrow in the menu bar" height="44">
  <img src="../img/mac/dropdown.png" alt="The menu bar panel with the same glucose card as the phone, Add Treatment, Treatments, Glucose Report and Settings" width="300">
</p>

## What it does

- **Menu bar:** the value and trend arrow in your range colours, struck through when the reading is old.
- **The panel:** the same card and chart as the phone, mute and unmute, and the way to every window.
- **Alerts:** the same alerts and sounds as the phone, with this Mac's own thresholds or the wearer's.
- **Treatments and report:** add insulin, carbs and notes; the glucose report (time in ranges, average, GMI) with CSV
  export.
- **Widgets** for the desktop and Notification Center.
- **Follows** over Bluetooth nearby or Nostr relays from anywhere, end-to-end encrypted: paste the
  `https://sukrara.mauri.app/join#…` link the phone shares. Or read Juggluco's web server on your home network.

Every behaviour, with its rules and numbers: the [behaviour catalog](../FEATURES) (for example
[the menu and windows](../FEATURES#mac-menu-and-windows), [Mac widgets](../FEATURES#mac-widgets),
[setup](../FEATURES#first-run-setup-mac)).

## Requirements

A Mac with Apple Silicon (M1 or newer) and macOS 15 or newer. Someone's phone with Sukrara for Android to follow, or
Juggluco on the same network.

## Get it

- **Homebrew:** `brew install --cask mauriciabad/tap/sukrara`.
- **From GitHub:** download `Sukrara-<version>.dmg` from the
  [latest release](https://github.com/mauriciabad/sukrara/releases), open it and drag **Sukrara** onto
  **Applications**. It's signed and notarized by Apple, so it opens like any other app. Sukrara lives in the menu bar
  (no Dock icon) and updates itself: once a day it looks for a new version, signed by Sukrara and checked before it
  installs, and offers it in its panel; *Install and Relaunch* is one click.

> **A preview, not a medical device.** Don't make treatment decisions from it.

[Privacy policy](../privacy) · [Source code](https://github.com/mauriciabad/sukrara) (GPL-3.0-or-later) ·
[Report a problem](https://github.com/mauriciabad/sukrara/issues)
