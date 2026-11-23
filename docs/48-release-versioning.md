# 48 — A version per app: releases, change files and shared formats

*2026-11-23 · design for the owner's question "we're forced to release everything together, even if an app has
nothing different" · desk research: GitHub, store and updater docs, other multi-app projects (sources at the end)*

## Problem

One root `VERSION` is every app's version. Every release tags `v<version>`, puts every app's files on one GitHub
release and bumps every app in every store, changed or not: 0.14.5 changed only Android and still shipped the Mac
through Sparkle and App Review; 0.17.1 changed no watch code and still bumped the three Garmin manifests (their store
uploads were skipped by hand, so GitHub says 0.17.1 where the Connect IQ store says 0.17.0). Nothing needs it: the apps
never compare each other's app versions, only the version of the formats they exchange. One version was simply where
the first scaffold (2026-10-27, Android and Mac shipped together) started.

## Decision (the owner, 2026-11-23)

1. **One version per store listing**, released only when it changed.
2. **Change files** instead of editing one CHANGELOG.
3. **Shared formats are versioned and checked**; until 1.0.0 nothing reads an older version: the person updates.

## Apps and versions

THE registry `contracts/apps/apps.json` lists every app: id, name, where its version lives, its tag prefix, its
channels, and the paths whose changes concern it.

| id | App | Version source | Tag | Channels |
|---|---|---|---|---|
| `android` | Sukrara for Android | `apps/android/VERSION` | `android-v0.18.0` | GitHub APK (updater, Obtainium, IzzyOnDroid), Google Play |
| `mac` | Sukrara for Mac | `apps/macos/VERSION` | `mac-v0.17.2` | GitHub dmg + zip, Sparkle, Homebrew, App Store |
| `aura` | Sukrara Aura | `apps/garmin/aura/manifest.xml` | `aura-v…` | GitHub `.iq` + sideload zip, Connect IQ |
| `garmin-app` | Sukrara (watch app) | `apps/garmin/app/manifest.xml` | `garmin-app-v…` | same |
| `garmin-field` | Sukrara data field | `apps/garmin/field/manifest.xml` | `garmin-field-v…` | same |
| `face-sdk` | SukraraFace barrel | `apps/garmin/sdk/manifest.xml` (5.0.0) | `face-sdk-v…` | GitHub |

- Every line continues from where it is (0.17.1; the barrel 5.0.0); versionCode / CFBundleVersion keep the formula
  (major·1 000 000 + minor·1 000 + patch) from the app's own version, so Play and Apple only ever see higher numbers.
- The root `VERSION` goes away. Everything that read it reads its app's version: Gradle, `bundle.sh`, `version.sh`,
  `install.sh`, the build-info line (*0.18.0 + 23 commits* counts from the last commit that changed **that app's**
  version), the docs' About texts.

## Change files

Every change a person would notice adds one file, `changes/<slug>.md`:

```markdown
---
apps: [android, mac]
kind: fix          # fix → patch, feature → minor
---
- **Extremely high stays quiet after insulin**: rapid insulin you log now quiets …
```

- The body is the CHANGELOG bullet, written so it reads right in each app's changelog; text that differs per app
  goes in separate files.
- Releasing an app takes every file naming it: the bullets become the app's entry in its own `CHANGELOG.md` (next to
  the app), the highest `kind` sets the next version, and the app is removed from each file's `apps` (the file is
  deleted when none is left).
- No change file, no release: an internal change ships with the app's next real change.
- The root `CHANGELOG.md` keeps the shared history up to 0.17.1 and points to the per-app files.
- A commit that changes an app's paths without a change file naming it gets a NOTE (check `changes`, like
  `features`); `Changes-skip: <reason>` for internal work.

## Release

`tools/release/release.py` without apps lists, per app, its pending change files and the next version.
`release.py android mac` releases those apps (`android=0.18.0` overrides a version):

- **version**: each app's version source, its CHANGELOG entry, the change files consumed; one commit on `next`.
- **gate**: only the released apps' builds and smoke tests.
- **github**: `main` fast-forwarded once; per app a tag `<id>-v<version>` and a normal GitHub release with only its
  files and its entry as notes. Android's is GitHub's **Latest** (the updater, Obtainium and IzzyOnDroid read it);
  every other app's is `make_latest: false`.
- **channels**, only for the apps released: Sparkle, Homebrew, TestFlight, App Store (mac); Play (android); the
  Connect IQ upload kit per Garmin app; nothing more for `face-sdk`.
- The store notes stay per app and per version code (Play's, the App Store's), written before the release as today.

What follows the tags:

| Reader | Today | Then |
|---|---|---|
| Android updater (`Releases`) | every release, tag `v…` | only `android-v…` tags |
| Website (`release.ts`) | `/releases/latest`, needs APK and dmg | the newest `android-v…` and the newest `mac-v…` release |
| Homebrew tap workflow | every published release | only `mac-v…` |
| SukraraFace download | `/releases/latest/download/SukraraFace.barrel` | the newest `face-sdk-v…` release (the website and GARMIN-FACES.md link it) |
| Website's Garmin page | "every release has a zip per app" | each Garmin app's newest release |
| Obtainium, IzzyOnDroid | Latest's APK | the same: Latest stays Android's, the other releases have no APK |

## Shared formats

What two Sukrara apps exchange is a format with a version; the registry `contracts/apps/formats.json` lists each one,
its current version, its spec and the apps that speak it. To be confirmed while building it, at least:

| Format | Version | Spoken by |
|---|---|---|
| Sharing protocol (`contracts/protocol/PROTOCOL-v2.md`, Bluetooth included) | 2 | android, mac |
| Identity (`PROTOCOL-identity.md`: profile, avatar, invite) | its message versions | android, mac |
| Watch payload and the watch's writes (`contracts/watchface/payload`) | 5 | android, aura, garmin-app, garmin-field, face-sdk |

Until 1.0.0:

- **Every app speaks only the current version.** A change old apps would misread bumps the version.
- **Tests bind the code to the registry:** each app's version constant (`WatchPayload.VERSION`, the watch's link
  version, the protocol's `v`) equals `formats.json`.
- **A format change releases every app that speaks it**: `release.py` compares `formats.json` with the one at each
  speaker's last tag and refuses a release that leaves one of them behind, so the update exists everywhere at once.
- **A device that meets another version says what to update**, never shows nothing (a blank face today). The version
  field stays where it is in every format, the one thing never changed, so any version can read it; the smaller
  number is the app to update. E.g. the watch: *Update Sukrara on your phone* / *Update this app*; the phone's Garmin
  page: *Update Sukrara Aura on your watch* with the store's *Get*; a follower: *Update Sukrara to keep following
  Ana*. The screens, words and exits: § When versions differ.

From 1.0.0 (not now): read the previous version too for a window, the way other projects bridge store delays
(sources below).

## When versions differ

*Product / UX round for #262 (2026-11-23). Words are **proposals**, en / es.*

### The person's side

When it happens: a format change ships to every speaker at once, but the stores don't land at once (Play and App Store
review in hours, Connect IQ in days), Connect IQ auto-update can be off or put an old version back (forum reports
below), the GitHub version waits for the person's OK, and a follower may be a parent who never opens the app. So the
usual case is **the morning after a release**: the phone updated itself overnight, the watch face didn't, and the
person on the way out sees a watch with no number. Or a follower whose phone is still on the old version stops getting
readings, and finds out only when the *No data* alarm rings.

What it must take off their mind: *is my glucose wrong or just missing?* (never a stale value as current, never a blank
with no reason) and *what do I tap?* (one button that opens the right update for that copy of the app). What others
do, judged:

- **Juggluco, AndroidAPS, xDrip+**: "use the same version on phone and watch", told in release notes and docs; the
  watch just stops (Juggluco, AAPS) or a toast nags with no working way out (xDrip+ on Wear OS 4+). Bad: the person
  learns from a missing number; nothing says which side to update.
- **Signal**: an expired build says so on the device in use and links the download; a linked device whose primary is
  out of date says *which* device to update and how it clears. Good: the device you look at names the other one.
- **Wear OS**: each side detects the other (capabilities) and opens the store on the other device
  (`RemoteActivityHelper`). Good: one tap from the device you hold to the update on the other.
- **Connect IQ**: updates sit in the Connect IQ app (*Device › Updates › Update All*), installed at the next sync; a
  face or data field can't open anything on the phone, a watch app and (API 5+) a data field can ask Garmin Connect to
  show a phone notification that opens a URL (`openWebPage`).

Decisions:

1. **The device you look at names the app to update**, the smaller version: *Update Sukrara on your phone* or
   *Update this app*, never "versions differ".
2. **Nothing new is shown as current**: what was last read stays and ages by the freshness rules (late, struck, gone
   at 20 min); the update line comes at once, not after 20 min. Nothing guesses at a payload it can't read (the
   owner's rule; question 1 below).
3. **One button to the right update** for that copy: Android GitHub version → the updater's *Check now* (its *Install*
   follows); Google Play → Sukrara's Play listing; Mac GitHub → Sparkle's *Check for Updates…*; Mac App Store → the App
   Store page; a Garmin app → its Connect IQ listing (`apps.json` `store`, the link the Garmin page's *Get* already
   opens) with the hint *Updates › Update All, then sync the watch*.
4. **The newer side waits for the older one's store**: a release that changes a format makes the new versions
   public in order: the Connect IQ apps first (Connect IQ publishes on approval), then Play and the App Store
   (managed publishing / manual release) and the GitHub releases. So when a device says *update*, the update exists.
5. **No new notification.** The watch says it on the wrist; the wearer's phone puts it on home's one banner place;
   a follower's *No data* alarm, which already rings when readings stop, names the reason and the button. A new alarm
   would ring for something the person can't act on at night.
6. **Updating clears it by itself**, at the first message that side can read; the history fills in as today (the
   watch's 3 h / 24 h `hist`, a follower's hour chunks).

### States and exits, per surface

| Surface | What it shows | Can do | Meanwhile | Ends |
|---|---|---|---|---|
| **Sukrara Aura** (and any face on the barrel) | the line where *No reading since* goes: *Update Sukrara on phone* / *Actualiza Sukrara en el móvil*, or *Update face in Connect IQ* / *Actualiza la esfera en Connect IQ* | nothing on the watch (a face can't open anything); the phone's banner has the button | the last reading it read, ageing; after the face itself updated, none (its stored payload is the old version): the line alone | the first payload it reads |
| **Watch app** | a screen in place of the value: the same line and **Open on phone** (`openWebPage`: Garmin Connect shows a notification that opens the Connect IQ listing, or the website's Android page `/android/#versions`); the glance: the line alone | *Open on phone*, Back | the last value, ageing; logging still works: entries wait in the outbox (*Waiting: update Sukrara* / *En espera: actualiza Sukrara*), sent once the phone reads them | the first payload it reads; the outbox empties |
| **Data field**, in an activity | its value place: `--` and *UPDATE PHONE* / *UPDATE FIELD* (es *ACTUALIZA MÓVIL* / *ACTUALIZA CAMPO*), the slot's size | nothing during the activity (a data field has no input); after it, the phone's banner | `--`; never a number it can't read | the first payload it reads |
| Complications | `---`, as with no reading (Latin letters only) | — | — | the same |
| **Phone, Garmin page and setup** | the watch's line: *Sukrara Aura on Descent needs an update* / *Sukrara Aura en Descent necesita actualizarse* + **Update** (Connect IQ listing), or *Sukrara Aura on Descent is newer than this phone's Sukrara* / *…es más nueva que Sukrara en este móvil* + **Update Sukrara** (decision 3); the app's row the same; error colour | the button; Back | the delivered reading is never named as shown | the next report with the same version |
| **Phone, home** | the one banner place (beside *Garmin sending off*): *Update Sukrara Aura on Descent* / *Actualiza Sukrara Aura en Descent* · "It can't read this phone's readings." + **Update** | the button; no close (it ends by itself), the Garmin page from the banner | the card as usual | as above |
| **Follower, Android and Mac** | the no-data reason (`NoDataReason` / `NoDataText`): *Can't read Ana's new readings · update Sukrara* / *No se pueden leer las lecturas nuevas de Ana · actualiza Sukrara* + **Update**; or, the wearer older: *Ana's Sukrara needs an update to share with you* / *Sukrara de Ana necesita actualizarse para compartir contigo* (nothing to tap: Ana updates) | the button; Following settings as today | the stored readings, ageing, the card under the reason as with any no-data reason; alerts on the last data | the first event it reads |
| Follower's *No data* alarm | its text names the reason: *No readings from Ana: update Sukrara* + **Update** action | the action, the alarm's usual mutes | — | readings again |
| **Wearer, Share** | a follower's row, from a message of another version: *Mom's Sukrara needs an update* / *Sukrara de Mamá necesita actualizarse*, or *Mom's Sukrara is newer: update Sukrara* + **Update** | the button | sharing goes on for everyone else | that follower's next readable message |
| **Joining** (invite link, nearby pairing) | the invite of another version: *This invite needs a newer Sukrara* + **Update**, or *Ana needs to update Sukrara first*; nearby, both phones name the older one | the button, Cancel | nothing saved or sent | update and open the link again |

TalkBack / VoiceOver read each line as text and each button by its label; the follower's reason and the phone's
watch line are live regions, as today. Watch screens are plain text.

### What each side must know

| Link | Knows today | Adds |
|---|---|---|
| Phone → watch payload | the watch drops another `v` silently and reports `x` (the phone shows *needs an update*, wrong when the phone is the older one) | the watch keeps the `v` it last refused and draws the line from it; its fetch adds `&v=<its version>`, so the phone knows the direction |
| Watch → phone writes | nothing: an entry carries no version | every write carries `v`; the phone answers another `v` with `r: "v"` and its own `v`; the watch keeps the entry |
| Sharing (relays, Bluetooth) | both apps throw *unsupported v* away silently (`Payload.kt`, `Payloads.swift`) | the `v` of a refused event goes to the no-data reason / the follower's row; Bluetooth's hello the same |
| Identity (invite, profile) | an unknown invite `v` is "not a Sukrara invite" | an invite with a `v` says which side to update |

**These land in the current versions (payload 5, protocol 2, invite 3), before any format change**, so the first
change already finds both sides able to say it: the old side must carry the detection.

**Never moves**, so any version reads it (`formats.json` names it per format, tests hold it):

- **Watch payload**: the top-level `v` (a whole number) beside `p` and `sent` in the dictionary; the port and fetch
  paths; the fetch's `v=` query; a write's `v` and the answer's `r` / `v`.
- **Sharing**: the envelope (event kinds, the `gk` tag, NIP-44 under the group key, the gift-wrap kinds) and `v` at the
  top of the decrypted content; Bluetooth's frames and the hello's `v`.
- **Identity**: the invite link's shape (`/join#` + 63 characters) and the invite's `v`.

A change that must move one of these is a new format beside the old one, not a version.

### Open (owner)

1. A watch that can't read the payload shows no number (decision 2). Freezing `g` (`t`, `v`) as well would let it show
   the value with the update line, a second frozen field. Keep the rule, or freeze `g`?
2. Decision 4 delays the phone's update by Connect IQ's review (usually 1–3 days) whenever the watch payload changes.
   Hold it, or ship the phone first and let the watch say *update* until Connect IQ approves?

### Draft: States and exits (for FEATURES.md when built)

**States and exits**: *versions match* → nothing shown. *A device can't read the other's format* → the device in view
says which app to update (the smaller version) and, where it can, one button to that copy's update (watch app: *Open on
phone*; phone: the Connect IQ listing or Sukrara's own updater / store; Mac: Sparkle or the App Store); the last reading
it read stays and ages (never shown as current), a watch app's entries wait in its outbox. Out: updating, which clears
it at the first message read (no tap needed); Back / Cancel leave each screen; nothing to mute or close because it ends
by itself. Reachable without a notification: the watch, the phone's Garmin page and home banner, a follower's card,
the wearer's Share rows; a follower's *No data* alarm names it. TalkBack / VoiceOver read the lines and buttons.

## Plan

1. **Versions and releases** ([#261](https://github.com/mauriciabad/sukrara/issues/261)): `apps.json`, the version sources, change files and their tool, per-app
   CHANGELOGs, `release.py` per app, the readers in the table above, build-info, `install.sh`, check `changes`,
   CLAUDE.md's *Releases*, DEVELOPMENT.md / docs/20 / ARCHITECTURE.md.
2. **Shared formats** ([#262](https://github.com/mauriciabad/sukrara/issues/262)): `formats.json` and the tests that bind each app to it, the release check, the *update*
   states on every surface (product / UX round first), CLAUDE.md's *No backwards compatibility*.

## Sources

| What | Where |
|---|---|
| One tag prefix per app in one repo | Bitwarden clients `web-v…` / `desktop-v…` ([releases](https://github.com/bitwarden/clients/releases)); Ente `photos-v…` / `auth-v…` ([releases](https://github.com/ente-io/ente/releases)); release-please `<component>-v<version>` ([docs](https://github.com/googleapis/release-please/blob/main/docs/manifest-releaser.md)) |
| Per-client versions, shared format only | Signal ([org](https://github.com/signalapp)); Element X ([android](https://github.com/element-hq/element-x-android/releases)); Home Assistant companions ([android](https://github.com/home-assistant/android/releases)) |
| Change files naming packages and bump kind | Changesets ([config](https://changesets.dev/guide/config)) |
| One Latest per repo; `make_latest` | [GitHub REST](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) |
| Channels filter by tag or asset | F-Droid `UpdateCheckMode: Tags <regex>` ([reference](https://f-droid.org/docs/Build_Metadata_Reference/)); IzzyOnDroid `ApkMatch` ([docs](https://izzyondroid.org/docs/general/YamlMetadata/)); Homebrew `:github_releases` livecheck ([docs](https://docs.brew.sh/rubydoc/Homebrew/Livecheck/Strategy/GithubReleases.html)) |
| Interface versions, not product versions | Matrix `/versions` ([spec](https://spec.matrix.org/latest/client-server-api/#get_matrixclientversions)); Kubernetes skew policy ([policy](https://kubernetes.io/releases/version-skew-policy/)); Wear OS capabilities ([docs](https://developer.android.com/training/wearables/apps/standalone-apps)) |
| Release blocked unless compatible with what's live | Pact `can-i-deploy` ([docs](https://docs.pact.io/pact_broker/can_i_deploy)) |
| Version lock done badly | Juggluco: "update Juggluco on watch and phone to the same version" ([FAQ](https://www.juggluco.nl/Juggluco/faq.html)) |
| Windows after 1.0.0 | Bitwarden ±2 majors ([support](https://bitwarden.com/help/bitwarden-software-release-support/)); Loop's 90-day countdown ([LoopDocs](https://loopkit.github.io/loopdocs/browser/automatic/)) |
| Telling people to update a paired app (§ When versions differ) | Juggluco "update on watch and phone to the same version" ([changelog](https://www.juggluco.nl/Juggluco/changes.html), [#500](https://github.com/j-kaltes/Juggluco/issues/500)); xDrip+ wear update toast that loops ([#580](https://github.com/NightscoutFoundation/xDrip/issues/580), [#4383](https://github.com/NightscoutFoundation/xDrip/discussions/4383), [#4069](https://github.com/NightscoutFoundation/xDrip/discussions/4069)); AAPS "same version on phone and watch" ([docs](https://androidaps.readthedocs.io/en/latest/WearOS/BuildingAapsWearOS.html)); Signal expiry and linked devices ([support](https://support.signal.org/hc/en-us/articles/8997185514138-Re-connect-your-primary-device-to-continue-using-Signal-Desktop), [#5252](https://github.com/signalapp/Signal-Desktop/issues/5252)); Wear OS capabilities, prompt the other device ([docs](https://developer.android.com/training/wearables/apps/standalone-apps)) |
| Connect IQ updates and what a watch app can open | Updates via *Device › Updates*, installed at sync ([Garmin support](https://support.garmin.com/en-US/?faq=z1Jiv0P4Yg4DZX5LIif5L8)); auto-update toggle and versions put back ([forum](https://forums.garmin.com/apps-software/mobile-apps-web/f/connect-iq-store-android/290335/can-you-disable-automatic-watch-face-updates), [bug](https://forums.garmin.com/developer/connect-iq/i/bug-reports/version-bug-connect-iq-iphone-app-reverts-device-to-prior-watch-face-version)); `openWebPage` for apps and (API 5) data fields, not faces ([Communications](https://developer.garmin.com/connect-iq/api-docs/Toybox/Communications.html), [app types](https://developer.garmin.com/connect-iq/articles/connect-iq-basics/App_Types.html)) |
