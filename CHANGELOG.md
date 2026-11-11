# Changelog

Sukrara's releases. 0.x versions are previews (GitHub prereleases).

## Unreleased

- **Sharing (Android, wearer)**: each message from a follower is handled once, also across restarts (#78). Before, every
  start handled again every message the relays still kept: an allowed follower got its key again, an ended follow
  got its notice again, and an older name change could replace a newer one.
- **Mac**: the Mac could quit overnight when a relay connection dropped, and its glucose and alerts stopped until
  Sukrara was opened again. A relay that answers a ping twice (the answer, then the dropped connection) or never no
  longer stops it.

## 0.14.0 — the apps update themselves, Do Not Disturb respected by default, widgets like the earlier app (GitHub prerelease)

- **The Android GitHub version updates itself** (#188): once a day it looks for a new version and says so in a quiet
  notification (*Update*, *Release notes*), downloads it, checks it is the release's file signed with Sukrara's key,
  and installs it, never while an alert is showing or a very low is on; monitoring comes back by itself right after.
  Android asks once to allow installs from Sukrara. *Install updates automatically* (Settings › About › Updates, off)
  installs without a tap where Android allows it. The Google Play version keeps updating through Play. Updating to
  this version is still by hand; from then on Sukrara does it.
- **The Mac updates itself** (GitHub and Homebrew version, #91): once a day it looks for a new version, signed by
  Sukrara and checked before it installs, and offers it in the menu bar panel; *Install and Relaunch* is one click.
  Settings › General › About has *Check for Updates…*, automatic checks (on) and automatic install (off). The App
  Store version keeps updating through the store. Updating to this version is still by hand (the dmg or `brew upgrade`);
  from then on Sukrara does it.
- **Do Not Disturb and Focus are respected by default** (#184): the alert checklist says plainly whether Sukrara
  alerts in Do Not Disturb (Android) or during a Focus (Mac), with *Let Sukrara through* / *Don't let Sukrara through*
  (Android: Do Not Disturb access the first time, then the alarm channels' override; Mac: Time Sensitive), and home
  shows a neutral line while a mode keeps alerts quiet. Updating from 0.13.0: the alarm channels keep the override
  they have; the Mac's alerts stop being Time Sensitive until you let Sukrara through.
- ***Sound while the phone is unlocked*** (Android, #183) is answered from a test alert: *Test the sound*, then
  *I heard it* / *It only vibrated*. The earlier answer is asked again.
- **Widgets and the card look like the earlier app again** (#181): Android's widgets are drawn at the size the
  launcher really gives them and are never stretched or squashed, on the home screen and in the widget picker, in the
  wallpaper's colours. On Android and the Mac the rings show their number without "%", a fresh value has no "now",
  and the value has as many slots beside it as fit (up to four).
- **Android look** (#181): the phone's own colours across the app, the dark theme included, the phone's round font in
  titles, and Material 3 Expressive motion.
- **Settings** (Android, #181): a search result opens its page at the row it found and highlights it; the alert
  reliability checklist, the status lines and the dialogs use Android's own settings components; Sharing's rows have
  their icons; TalkBack says again what a tap on an action row does (copy, import, fix).
- **Sharing** (#181): a follower whose invite the wearer never answers no longer waits forever: when the invite
  expires, Sukrara says *Invite expired* with *Join with a new link* (Android and Mac). The Mac's *Find phones nearby*
  works again after a follow ended.
- **Fixes** (#181): *Active now* names the right reason when a Mute and carbs quiet the same alerts (Android and
  Mac); *Add treatment* opened from home no longer offers *Open Sukrara*; in Spanish the app says "hidratos"
  everywhere and "Glucosa baja / alta" for the alert groups.
- **Not a medical device**: setup's Welcome and About in Settings say so, on Android and the Mac; the Mac's About also
  links the privacy policy.

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
