# Changelog

Sukrara's releases. 0.x versions are previews (GitHub prereleases).

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
