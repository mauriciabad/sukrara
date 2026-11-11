# Changelog

Sukrara's releases. 0.x versions are previews (GitHub prereleases).

## 0.13.0 — fixes from the first days of 0.12.0, monitoring before the first unlock (GitHub prerelease)

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
