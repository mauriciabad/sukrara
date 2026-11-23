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
  Ana*. The screens, words and exits are designed in their feature round (CLAUDE.md "Complete flows").

From 1.0.0 (not now): read the previous version too for a window, the way other projects bridge store delays
(sources below).

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
