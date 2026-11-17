<!-- Sukrara — https://github.com/mauriciabad/sukrara · Copyright (C) 2026 Maurici Abad · SPDX-License-Identifier: GPL-3.0-or-later -->
# The apps' and services' icons

The site shows each app or service Sukrara works with by its own icon (`src/lib/apps.ts`), the same one the Android app
shows on its row (docs/10-showcase.md "Service and app icons"). Each mark belongs to its owner and is used only to name
it; the site's footer says so.

| File | What, from where |
|---|---|
| `libreview.svg`, `tidepool.svg`, `nightscout.svg` | The Android app's adaptive icons (`apps/android/core/designsystem/src/main/res/drawable/ds_ic_app_*.xml`) as SVG: the background and the vector foreground, cropped to the 72 of 108 units a launcher shows. Convert again if the app's change |
| `health-connect.png` | The app's `ds_ic_app_health_connect_logo.png` (Google's official logo, from the Health Connect UI guidelines) on white, as the guidelines ask, at the inset the app's icon uses |
| `juggluco.png` | Juggluco's two launcher layers as the app keeps them (`ds_ic_app_juggluco_*`, GPL-3.0), composed and cropped like a launcher |
| `aaps.svg` | AAPS's logo, unchanged: `_docs/logo/logo-vector-2023.svg` in [nightscout/AndroidAPS](https://github.com/nightscout/AndroidAPS) (AGPL-3.0) |

xDrip+'s icon is read where the Android app keeps it (`ds_ic_app_xdrip.png`, xDrip's own launcher icon, GPL-3.0). The
one-colour marks (Android, Apple, GitHub, Google Play, Bluetooth, Homebrew, Obtainium, Nostr) are paths in
`src/lib/logos.ts`, from Simple Icons (CC0-1.0) and Starlight (MIT). Garmin's is its delta, the triangle of its logo, from
[SVG Repo](https://www.svgrepo.com/) (`garmin-svgrepo-com.svg`, CC0-1.0; the one path, unchanged, checked to hold
nothing else), drawn in one colour like the others; the owner chose it (2026-11-17). Garmin's rules for third parties
([Connect IQ](https://developer.garmin.com/brand-guidelines/connect-iq/), [Garmin Connect](https://developer.garmin.com/brand-guidelines/connect/))
ask that its images aren't altered and that app icons carry none of its branding; the site uses the mark only to name the
platform, never in Sukrara's own icons, and the footer names Garmin's trademarks.
