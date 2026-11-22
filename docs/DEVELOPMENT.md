# Developing Sukrara

The know-how for whoever works on Sukrara (the apps in `apps/`, shared `contracts/`, `tools/`). The
rules are in [`CLAUDE.md`](../CLAUDE.md); the architecture in [`ARCHITECTURE.md`](ARCHITECTURE.md); the frozen PoC's
own notes stay in [`poc/spikes/showcase-android/NEXT.md`](../poc/spikes/showcase-android/NEXT.md).

## Setup

- Clone, then `tools/hooks/install.sh` once (sets `core.hooksPath=tools/hooks`, relative, so worktrees share it).
- **JDK 21** for everything Android: `brew install openjdk@21`; Gradle's toolchain finds it (`JAVA_HOME` =
  `$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` for keytool & co.). The build fails without it.
- Android SDK at `~/Library/Android/sdk`; emulator binary `~/Library/Android/sdk/emulator/emulator` (at most two
  emulators at once); the `android` CLI at `~/.local/bin/android` (create / start AVDs, screenshots, UI dumps).
- Xcode (Swift 6) for the Mac app; `swift build` / `swift test` in `apps/macos`.
- Node 24 (`website/.node-version`; `brew install node`) for the website: `npm ci` in `website/` once
  ([`website/README.md`](../website/README.md)).
- The Libre 3 native core builds from `third_party/Juggluco` (gitignored, pinned by `apps/android/sensor/juggluco.lock`;
  from a worktree the main checkout's copy is found). A release build fails without it.

## Everyday commands

| What | Command |
|---|---|
| All repo rules (CLAUDE.md as checks) | `tools/check/run.sh` (`--staged`, `--range A..B`, `--pending`, `--help`) |
| The checks' own tests | `tools/check/run.sh --selftest` |
| Cross-surface card tests | `tools/check/run.sh --layout` |
| Settings code after a schema change | `python3 tools/settings/gen.py` (commit both SettingsSpec files) |
| Play Store icon + apple-touch-icon | `python3 -I tools/brand/store_icons.py` (from the frozen `poc/spikes/brand/brand.py` shapes) |
| Android: everything CI runs | `cd apps/android && ./gradlew check` |
| Mac: build + tests | `cd apps/macos && swift build && swift test` |
| Website: live preview | `cd website && npm run dev` (http://localhost:4321) |
| Website: what CI runs | `cd website && npm run build && npm run check` (links, the apps' URLs, App Links, the feed) |
| A release to every channel | `tools/release/release.py <version>` (`--dry-run`, `--from <step>`): [Release](#release) |
| Version | `tools/release/version.sh` → `0.16.0 16000` (from `VERSION`) |

CI (`.github/workflows/checks.yml`) runs on every push, each push its own run at once (pushes never queue behind or
cancel each other; a pull request's newer push cancels its older run). Its `changes` job (`tools/ci/changes.py`)
compares the commit with the newest ancestor that has a green run of its own, not with the push before: the rules
run over that range, the Android jobs when `apps/android/` (but its `fastlane/` Play notes), `contracts/`, `tools/`,
`VERSION` or `assets/` changed in it, the Mac job when `apps/macos/`, `contracts/` or `assets/` did (no Mac step reads
`tools/`; `VERSION` reaches only the bundle, which the release gate builds and smokes), the `website` job (build +
`website/scripts/check.mjs`) when `website/`, `docs/`, `assets/`, `contracts/` or `apps/` did (it publishes docs/,
shows the goldens and checks every site URL the apps open); CHANGELOG.md alone runs only the rules. The PoC has no build jobs: it is frozen. A commit
that already has a green run (main fast-forwarded to next's commit, a tag) runs nothing again. `gate` is the only
required check. Android is four jobs side by side: `android` (`./gradlew check -x lint` + Roborazzi verify),
`android-lint` (`lint`; the two together are exactly `check`), `android-release` (the shrunk release APK keeps the
alert sounds) and `android-play`; each keeps Gradle's build cache between runs (`.github/actions/gradle`), so only the
tasks a change touches run again. That's only right while every file a task reads is one of its declared inputs: a
test that reads a file directly (not through the classpath or `Contracts`) gets it from its module's `src/`
(QualityConventionPlugin) or declares it in its build file (app, monitor, designsystem). `gh workflow run checks.yml
--ref next -f full=true` runs every job whatever changed. Every `uses:` is pinned to a
commit SHA with its release tag in a comment (`actions/checkout@<sha> # v7.0.1`); Dependabot
(`.github/dependabot.yml`) moves both weekly. Look a tag's SHA up with `gh api repos/<owner>/<action>/commits/<tag>
--jq .sha`.

## Devices

- **Phone** Pixel 10 Pro `62240DLCH0005W`. The owner relies on it daily: only in-place updates (`adb -s … install -r`
  keeps data), read-only adb otherwise. **Never** take the sensor from the running app without the owner's OK, never
  post to LibreView (real doctor-facing account), never uninstall / clear data (identity + followers), no reboots, no
  global Wi-Fi / Bluetooth toggles. Always `adb -s <serial>`.
- **Mac**: the owner's copy is `/Applications/Sukrara.app` (the notarized release since 0.12.0, with the widget; only
  `tools/install.sh mac` replaces it). Never overwrite it with a test build; test builds run from another path, offline
  (`LaunchMode`: snapshot / test runs start no Bluetooth, relays or alerts). After replacing the app, `pkill -f
  SukraraWidget.appex` and keep `pluginkit -mAv | grep sukrara` pointing only at the installed copy. `Bundle/bundle.sh` signs with the provisioning profiles in
  `~/.apple-dev/profiles` when they're there (Developer ID, Time Sensitive, data-protection keychain, universal links:
  docs/20-publishing.md); such a test build also claims `https://sukrara.mauri.app/join`, so unregister it after
  testing (`lsregister -u <app>`, `pluginkit -r <appex>`).
- **Windows**: the VM `sukrara-win11` on the Mac ([Windows VM](#windows-vm)); shut down when not in use (6 GB RAM).

## Install (one build line)

Every install must contain all merged work, or features silently vanish from the owner's devices:
`tools/install.sh phone|mac [--dry-run]`. It refuses a dirty tree, a HEAD without `origin/main`, the branches in
`tools/check/integration.txt` or the commit installed last time, and failing checks; then it builds the release variant
as a development build (signed like a release; About says *Development build · …*, see [Development and release
builds](#development-and-release-builds)), verifies it (`tools/release/verify-apk.sh` / `verify-app.sh`), installs in place and records the commit in
`<git common dir>/sukrara-installed-<target>`.

On the Mac it replaces the installed copy wherever it is: `/Applications/Sukrara.app` (the notarized release from the
dmg or the cask) when it's there, else `~/Applications/Sukrara.app`. It refuses, before anything changes, when a
`Sukrara.app` in either folder has another bundle id than `app.mauri.sukrara` or can't be replaced: macOS App
Management guards a notarized copy that has run, so the terminal app it runs in needs that permission (System Settings
› Privacy & Security › App Management, then restart the terminal). It copies the verified build next to the target
first, quits Sukrara the way its menu does (then `pkill` after 10 s), removes and unregisters the other folder's copy
(`pluginkit -r` its widget, `lsregister -u`), swaps the new bundle in, makes it the `sukrara:` link handler,
unregisters every other registered copy (worktree and test builds, a mounted dmg: `lsregister -u`, `pluginkit -r`) so
Spotlight, Launchpad and the widget gallery show one Sukrara, deletes the build copy in `apps/macos/build` (Spotlight
lists every app on disk, registered or not), and opens it again (also when a later step fails).
`--dry-run` prints where it would install and what it would remove; `SUKRARA_MAC_APP_DIRS="<dir> <dir>"` points it at
scratch folders for such a dry run.

## Signing

- **Android release key**: `~/.android/sukrara-release.jks` (alias `sukrara`), made once by
  `tools/release/make-keystore.sh`; its password lives in the Keychain item `sukrara-android-release`. The release
  build reads `SUKRARA_KEYSTORE` (default that path) and the env `SUKRARA_KEYSTORE_PASSWORD` only; `tools/install.sh` and
  the release scripts export it from the Keychain item.
  Its public SHA-256 is `tools/release/android-cert.sha256`. Backup procedure: [docs/20](20-publishing.md#the-android-release-key).
  Debug builds use `~/.android/debug.keystore` (`tools/release/android-debug-cert.sha256`, also in assetlinks).
- **Mac**: the *Developer ID Application* identity of team `QWUV9H4A3G` with the Developer ID provisioning profiles in
  `~/.apple-dev/profiles`; `tools/release/mac-release.sh` notarizes and staples the app and the dmg with the notarytool
  keychain profile `sukrara` (since 0.12.0; docs/20-publishing.md › Mac release: Developer ID and notarization).
  App Group `QWUV9H4A3G.app.mauri.sukrara`.
- **Mac updates (Sparkle)**: the EdDSA private key that signs every update is in the login keychain (Sparkle's
  `generate_keys`: service `https://sparkle-project.org`, account `ed25519`), with a backup at `~/.apple-dev/sparkle-eddsa-private.key` (chmod 600) for the
  password manager; its public key is `SUPublicEDKey` in `apps/macos/Bundle/sparkle.plist`. `mac-release.sh` signs the
  notarized dmg with it and writes `website/public/appcast.xml` (docs/20-publishing.md › Sparkle: the update feed).

## Development and release builds

Every build says what it is wherever the version shows, so nobody takes a build of `next` for the release
(`tools/release/build-info.sh`, one line both apps carry: Android the manifest's `app.mauri.sukrara.build` meta-data,
the Mac Info.plist `SukraraBuild`):

- **Release** (`release`): only with `SUKRARA_RELEASE_BUILD=1`, which only the release scripts set
  (`android-release.sh`, `play-bundle.sh`, `mac-release.sh`, `mac-appstore.sh`), and only from the release commit (the
  last commit that changed `VERSION`, with no other change and no untracked file; `website/public/appcast.xml` may differ:
  `mac-release.sh` writes it in the gate and `release.py` commits it before `testflight`); anything else stops the
  build. About and Settings keep *0.16.0 · GitHub version* / *Google Play version* / *App Store version*.
- **Development** (`development <commits> <commit> <date>`): every other build: `tools/install.sh` (which unsets the
  flag), CI, Android debug builds (`development` alone, so the build cache keeps them), `swift build` / `swift test`
  (no Info.plist). About and Settings say *Development build · 0.14.5 + 23 commits · d744fd74 · 14 Nov* (Spanish
  *Versión de desarrollo · 0.14.5 + 23 cambios · …*): the commits since the release commit (those that change more than
  `website/public/appcast.xml`), HEAD's hash and its commit date; what git can't tell (a shallow clone) is left out. Reports
  (*Copy report*, sensor feedback, the import's issue, the Mac's *Copy Report*) say *0.14.5 development build +23
  d744fd74 2026-11-14*.
- **Checks**: `verify-apk.sh` / `verify-app.sh` expect a development build unless given `--release` (the release
  scripts pass it): a release can't carry the development wording, an install.sh build can't carry the release's.
  `test_release.py` (`BuildInfoTest`), `AppBuildTest` / `VersionSummaryTest` / `ManifestTest` (Android),
  `AppBuildTests` (Mac).
- **Updates**: versionName / CFBundleShortVersionString and versionCode / CFBundleVersion stay the base release's
  (`VERSION`), so `adb install -r` and the Mac install over a release keep working, and the updaters (Android's
  `Releases.newer` + `ApkCheck`, Sparkle's CFBundleVersion) never offer the base release or an older one over a
  development build, only a newer release (`VERSION` moves only at a release, so a newer release has everything the
  development build has). The Google Play and App Store builds are always releases.

## Release

One command on the release Mac publishes a version to every channel (#187); the keys never leave the Mac:
`tools/release/release.py <version>` in a clean checkout whose HEAD is `origin/next` (its Checks run green): the main
checkout or a worktree, on any branch or a detached HEAD (every push names its refspec, `HEAD:next` / `HEAD:main`; no
local `next` or `main` is needed). Before it: write CHANGELOG.md's `## Unreleased` section (optionally
`## Unreleased — <summary>`, which becomes the entry's title) and the Google Play notes,
`apps/android/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/…` (what's new on Android, at
most 500 characters each; Fastlane's layout, which F-Droid and IzzyOnDroid read too), and the App Store notes,
`apps/macos/AppStore/whats-new/en-US/<versionCode>.txt` and `es-ES/…` (what's new on the Mac, at most 4000 characters
each; no build reads either). Then the steps, in order; each
checks its result and stops with what's done, what's left and the `--from <step>` that resumes (every step can run
again: it skips what's already there):

1. `preconditions`: a clean tree at `origin/next`, its Checks run green or still running (not awaited: a failed one
   stops the release), the version above `VERSION`, a non-empty Unreleased section, the Play and App Store notes, no tag yet,
   `tools/check/strict`, the keys, `gh`. A Checks run counts on any branch (the same commit is the same tree): the
   commit's push run or one started by hand (`gh workflow run checks.yml --ref next` when the push run is stuck, e.g.
   in a GitHub Actions incident); one green run is enough, a pull_request run doesn't count.
2. `version`: `VERSION`, the version written in DEVELOPMENT.md / QA.md / FEATURES.md, Unreleased → `## <version> — …`
   with a new empty Unreleased above; one commit, pushed to `next`. Its Checks run starts at once
   (it never queues behind the run before it), goes on while the gate runs, and covers every change since the newest
   green commit, the previous HEAD's included; it is the release's one wait for CI, in `github`.
3. `gate`: `android-release.sh` (signed APK, `verify-apk.sh`, the mapping), `android-smoke.sh --final --no-build` on
   the emulator, `play-bundle.sh`, `mac-release.sh` (notarized, Sparkle-signed, `verify-app.sh --notarized`, writes
   `website/public/appcast.xml`), `Bundle/smoke.sh`, `mac-appstore.sh` (build + `altool --validate-app`: the pkg `testflight`
   uploads). `./gradlew check` and `swift test` aren't run again: the release commit's green Checks run ran them (the
   Mac job ran on the newest green commit with the same Mac sources: `VERSION` reaches only the bundle, which this
   gate builds, notarizes and smokes).
4. `github`: the release commit's Checks run green, `main` fast-forwarded, tag `v<version>`, `gh release create
   --latest` (a normal release, *Latest*) with the APK, the Android mapping, the dmg and the zip (their
   sha256 checked against the release's), notes from the CHANGELOG entry with the disclaimer and the files. `main`'s
   own run isn't awaited: it finds the commit's green run and runs nothing again.
5. `sparkle`: the dmg on the release is the one `website/public/appcast.xml` signs → the appcast committed and pushed to `main`
   and `next` (if `next` moved meanwhile, the commit is merged into it; HEAD stays on the appcast commit, so a re-run
   never brings `next`'s other work to `main`), Pages deployed, and https://sukrara.mauri.app/appcast.xml serves the
   item.
6. `homebrew`: the tap's cask (bumped by `homebrew-tap.yml`) at the version, with the dmg's sha256 and
   `auto_updates true`.
7. `play`: the play bundle on the **internal and production** tracks with the en-US / es-ES notes, through the
   Android Publisher API (edits.insert → bundles.upload → tracks.update → edits.commit) with the service account
   `~/.android/sukrara-play.json`; a track that already has the version code is left alone. Managed publishing is
   off: production goes live once Google approves it.
8. `testflight`: unless App Store Connect has a build of the version already, the gate's pkg
   (`build/Sukrara-<version>-appstore.pkg`; missing, e.g. `--from testflight` in a fresh checkout, `mac-appstore.sh`
   builds and validates it once), its version and build number checked, uploaded with `mac-appstore.sh --upload`
   (which never builds); then waits until App Store Connect has processed it. A new build shows *Missing Compliance*
   until its export question is answered (since 0.14.6 the App Store build's Info.plist answers it).
9. `appstore`: the App Store version of the release (a new one, or the editable one reused: never submitted, rejected
   or removed from review), its VALID build attached, *What's New* from the App Store notes (once a version is live),
   released automatically after approval, submitted for App Review. Apple reviews one version at a time: while
   another is in review, this release stays on TestFlight (the summary says so) and a later release submits the
   newest. Needs the App Store Connect key with **App Manager** access (`~/.apple-dev/release-AuthKey_<id>.p8`; the
   *Developer* notary key is `notary-AuthKey_<id>.p8`, notarization reads it from the keychain profile).
10. `garmin`: the Garmin face, once it is released (`tools/release/garmin-key.sha256` committed: the switch). The
    gate built its store package and sideload builds with `garmin-release.sh` (signed with the store key after
    checking it is the pinned one; `apps/garmin/build.sh aura`'s release and export builds, debug code compiled out) and
    `github` put them on the release. The Connect IQ store has no upload API, so this step prints the file, the
    version and the What's New text to upload at https://apps-developer.garmin.com/developer/dashboard; Garmin
    reviews it before the store shows it ([docs/20](20-publishing.md#garmin)). The `version` step sets the manifest's
    version to the release's either way.
11. `summary`: every channel's state (`--from summary` alone shows it any time).

`--dry-run` runs the read-only checks and prints every command, the notes and the Play request bodies; it changes
nothing, and exits 1 with the list when a check would stop the release. Read-only store checks: `python3
tools/release/stores.py play-tracks` (opens an edit, lists the tracks, deletes the edit) and `asc-builds`.
Every release goes to both stores' review: Play production and the App Store. Tests: `python3 tools/release/test_release.py` (CI).

## Garmin face on CI

Checks' `garmin` job (when `apps/garmin`, `contracts/`, `assets/`, `docs/img/aura/ref`, `tools/watchface` or its
setup change) checks the face's words (`garmin_test.py words`, no SDK needed) and builds it on Linux (`ubuntu:22.04`:
the SDK's simulator needs WebKitGTK 4.0) with `apps/garmin/build.sh aura`. The simulator runs there headless under Xvfb
(`xvfb-run`; tried 2026-11-16 in that image: unit tests `PASSED`, the face drawn), and with no window manager its
screen sits at `simulator.json`'s display location plus the 26 px menu bar (`import -window root -crop
390x390+131+237` for the `descentmk343mm`).

**Two sets of cases.** `garmin_test.py run` over every case (about 130) takes 1–2 h: heavy dial cases make monkeydo
lose the simulator, and the harness restarts it. So the `garmin` job, which `gate` needs (30-minute timeout), runs a
fixed set of 21 (`AURA_CASES` in checks.yml; 2026-11-17: 3.5 min in the simulator, 5 min for the job): the safety states (LO, HI, no readings, old, very low, extremely high,
muted, sensor ended, phone lost), one case per dial, always-on, private and Spanish. It checks their numbers against
`expect.json`, the battery label against every row, and each case's picture; it also builds the store package, which
compiles every watch of the manifest. `garmin-all` runs only on `gh workflow run checks.yml --ref next -f full=true`,
after `garmin` and outside `gate` (180-minute timeout): a matrix with one job per watch of `apps/garmin/aura/manifest.xml`
(`garmin-sdk.sh plan`'s `devices-json`), every case on the Descent, the fixed set (`AURA_GATE_CASES`) on the others
(artifacts `garmin-test-all-<watch>`). Both jobs start from `.github/actions/garmin` (packages, SDK, device files, cache).

`tools/ci/garmin-sdk.sh` gets what it needs, pinned in `tools/ci/garmin.lock`: the Connect IQ
SDK (public download, sha256 checked) and the manifest's device files with their simulator fonts, which need a Garmin
sign-in. It signs in only when the Actions cache lacks a device (a new device, or the cache unused for 7 days), with
the headless SDK Manager [`connect-iq-sdk-manager-cli`](https://github.com/lindell/connect-iq-sdk-manager-cli)
(version and sha256 pinned), deletes the token right after, and caches `Sdks/`, `Devices/` and `Fonts/` (never the
token) before any of the project's code runs. Builds are signed with a key made for the run. Without the files and a
credential (until the owner adds it, forks, pull requests from outside) the job says so in a notice and skips its
steps: `gate` passes. `tools/ci/test_garmin_sdk.sh` (the rules job) proves that path offline.

**One `.Garmin` for every tool.** The runner sets `HOME=/github/home` in the container, and the CLI and the cache put
`~/.Garmin` there. But monkeyc finds the devices under Java's `user.home` + `/.Garmin/ConnectIQ/Devices` (Linux;
`APPDATA` on Windows, `~/Library/Application Support` on macOS), and the JDK takes `user.home` from `/etc/passwd`
(`/root`), not `$HOME`. Without a link it fails with `ERROR: Invalid device id specified: 'descentmk343mm'` while
`compiler.json` is in place (2026-11-17). `.github/actions/garmin` links `/root/.Garmin` → `$HOME/.Garmin`. A local
`ubuntu:22.04` container doesn't show this: there `$HOME` is `/root`. The CLI's download has the same layout as the
SDK Manager's. Its "Font is contains spaces and can't be downloaded" lines (three `bitstreamVeraSans` sizes) are
harmless: the SDK Manager on the Mac doesn't install those fonts either.

**The credential is a Garmin account made for CI only**, never the owner's own (that one holds the watch, health and
dive data and the store's developer identity, and has two-step verification, which a scripted sign-in can't answer).
The owner, once:

1. Create a Garmin account at https://connect.garmin.com (Create one) with an address only for this (e.g. a
   `+garmin-ci` alias), a new password from the password manager (item *Sukrara Garmin CI*), no watch paired, no
   two-step verification. Confirm the e-mail.
2. Add two repository secrets (GitHub › the repo › Settings › Secrets and variables › Actions › New repository secret,
   or `gh secret set GARMIN_CI_USERNAME -R mauriciabad/sukrara`, which asks for the value): `GARMIN_CI_USERNAME`
   (the e-mail) and `GARMIN_CI_PASSWORD`.
3. Start a run: `gh workflow run checks.yml --ref next -f full=true`. The `garmin` job signs in once ("device files:
   descentmk343mm (signing in as the CI account)"), keeps the files and builds.

If that sign-in fails because Garmin refuses scripted sign-ins (it did for Garmin Connect tools in 2026, see
[docs/20](20-publishing.md#garmin)), use a token from a browser sign-in of the same account instead:
`brew install lindell/connect-iq-sdk-manager-cli/connect-iq-sdk-manager`, then `connect-iq-sdk-manager agreement
accept` and `connect-iq-sdk-manager login` (the browser opens; sign in as the CI account), then
`base64 -i ~/.Garmin/ConnectIQ/token.json | gh secret set GARMIN_CI_TOKEN -R mauriciabad/sukrara` and
`rm ~/.Garmin/ConnectIQ/token.json`. The token wins over the password; when the job says it expired (Garmin doesn't
say after how long), do it again.

Rotate: a new password at Garmin (account › security), then `gh secret set GARMIN_CI_PASSWORD`. Revoke: `gh secret
delete GARMIN_CI_USERNAME` / `GARMIN_CI_PASSWORD` / `GARMIN_CI_TOKEN`, then delete the Garmin account (Garmin account
› data management). Either way the cached files keep the job working until a device is missing. When Garmin changes
the SDK agreement the download stops with a message: read it (`connect-iq-sdk-manager agreement view`) and put its
hash in `tools/ci/garmin.lock`, with the SDK's new version and sha256 when the SDK moves.

## Strings (Android, English + Spain Spanish)

Every user-visible text goes in the owning module's `src/main/res/values/strings.xml` and the same key in
`values-es/strings.xml` (snake_case keys grouped by screen, a `<!-- what it is [CHAR LIMIT=…] -->` comment, `%1$s`
positional arguments, `<plurals>` for counts, `translatable="false"` for brand names and units; Spanish terms:
hidratos, insulina rápida / lenta, tiempo en rango). Compose reads `stringResource`; `StringsParityTest` and the
`strings` check fail on a missing key.

A setting shown in a second place (another page, setup, a widget's settings, the other app's pane) gets words for
where it is: its own title, or a group / section header (or a footer) that says what it is there. Android
`SameWordsTest` fails on a row title that is on two kinds of settings page in the same group; the Mac `reused` check
fails on a control label written in two settings / setup files without an `allow.json` `reused_ok` entry. Row icons
tell rows apart: none repeats on a page, a list of one kind of thing has none (`SettingsIndexTest`, the galleries).

## Debug and test code

Debug commands that replace the sensor or make up data live only in `src/debug` (Android) or `#if DEBUG` / the
`sukrara-snap` and test targets (Mac): a release build cannot contain them (`debug` check, `verify-apk.sh`,
`verify-app.sh`). Test with synthetic data on the emulator, never the owner's devices or real relays.

The Android commands are `DebugFakes`' actions, `adb shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver -a
app.mauri.sukrara.debug.<NAME> …`: `SYNTHETIC`, `ALERTS`, `PEN_READ`, `PEN_TAGS`, `PEN_JUGGLUCO`, `RELAYS`, `UPDATES`, `GARMIN_SIM` and `LIBRELINKUP` (their
extras: docs/FEATURES.md "Sensor reminders and countdowns", "Insulin pens", "Sukrara Aura"; docs/QA.md "Emulator", Q27). The Mac's:
`sukrara-snap card|windows|shell <dir>` (`-es` for Spanish) and `SUKRARA_DEV=1 SUKRARA_FIXTURE=1` (a debug dev run's
QR scanner reads the made-up `SyntheticCamera` feed, never the Mac's camera; its *Stop Following* with *Keep it* writes
the made-up week as a kept history). A debug dev run walks the online sources against servers on this Mac (docs/44
Appendix G finding 17, `DevOnline`): `SUKRARA_DEV_LIBRELINKUP=http://127.0.0.1:8766` sends LibreLinkUp's calls to
`tools/librelinkup-probe/fake.py`, `SUKRARA_DEV_NIGHTSCOUT=1` runs the Nightscout source for a site on this Mac's
loopback (any stub answering Nightscout's API); such a run keeps its accounts in memory, never the login Keychain.
The `debug-docs` check fails when
docs/FEATURES.md, docs/QA.md or this file name a command or extra the code doesn't have: add the command to the code
first, or fix the docs.

**Relays in debug builds.** A debug build connects to no relay at all (`LaunchPlan`: release builds always use the
public relays) until the `RELAYS` debug command points it somewhere. For sharing tests run the local test relay
(`tools/relay/relay.py`, standard-library Python, in memory, listens on this computer's loopback only) and point every
emulator at it; the emulator reaches the computer's `127.0.0.1` as `10.0.2.2`:

```sh
python3 tools/relay/relay.py --port 7447            # logs every connection, REQ and EVENT
adb -s emulator-5554 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \
    -a app.mauri.sukrara.debug.RELAYS --es urls ws://10.0.2.2:7447
```

The command saves the choice and ends the app process, so every relay connection is made again from it (open the
app). `--es urls off` goes back to no relay; `--es urls public` uses the public relays on purpose (only when a test
needs them). Check with `adb shell cat /proc/net/tcp6` that the app's uid (`dumpsys package app.mauri.sukrara | grep
uid=`) has no socket other than `…0202000A:1D17` (10.0.2.2:7447). The relay's own tests: `python3
tools/relay/test_relay.py` (CI runs them).

**Updates in debug builds.** A debug build of the GitHub version reads the GitHub releases and trusts only the release
key, like a release build, so it never installs anything. To test the updater (#188) on an emulator, serve a local test
release (`tools/updates/serve.py`, standard-library Python, loopback only) with a newer debug build of your own (the
same code with a higher `VERSION`, never committed) and point the app at it; it then trusts the debug key and checks
once at every start (docs/QA.md Q25):

```sh
python3 tools/updates/serve.py new.apk --port 8765
adb -s emulator-5584 shell pm grant app.mauri.sukrara android.permission.ACCESS_LOCAL_NETWORK
adb -s emulator-5584 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \
    -a app.mauri.sukrara.debug.UPDATES --es server http://10.0.2.2:8765/
```

`--es server off` goes back to GitHub; `--es cert <sha256>` trusts another certificate. Android 17 treats 10.0.2.2 as
the local network, hence the grant.

**LibreLinkUp in debug builds.** A debug build signs in to Abbott's LibreLinkUp like a release build. To test the
LibreLinkUp source and the LibreView sign-in on an emulator, run the fake (`tools/librelinkup-probe/fake.py`,
standard-library Python, loopback only, synthetic people and the `SYNTHETIC` curve) and point the app at it; every
region goes there (docs/QA.md Q30):

```sh
python3 tools/librelinkup-probe/fake.py --port 8766
adb -s emulator-5554 reverse tcp:8766 tcp:8766
adb -s emulator-5554 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \
    -a app.mauri.sukrara.debug.LIBRELINKUP --es server http://127.0.0.1:8766
curl 'http://127.0.0.1:8766/_fake?login=terms'      # what it answers: login, calls, people, lag, stopped, sensor…
```

The command saves the choice and ends the app process (open the app); `--es server off` goes back to Abbott's hosts.
The fake also serves LibreView's app API (`/lv/`), so the LibreView upload goes there too (with `adb reverse` on
`127.0.0.1` only: through 10.0.2.2 the upload refuses the plain-http address and only fails): one session per app identity as
LibreView keeps them, `dtid`, `lvother`, `lvexpire` and `lvmerge` to reach every state of *Beside the Libre app*
(docs/QA.md Q30).

## Garmin (Sukrara Aura)

The watch face is `apps/garmin/aura` (Monkey C, Connect IQ; how the code is laid out and the data seam:
[apps/garmin/aura/README.md](../apps/garmin/aura/README.md); the design: `contracts/watchface/aura/SPEC.md`). It shares
`apps/garmin/shared` (the phone link) and the build with Sukrara's other Garmin apps:
[apps/garmin/README.md](../apps/garmin/README.md).

**Setup.** `brew install --cask connectiq-sdk-manager`; in the SDK Manager sign in with a Garmin account, install the
newest SDK (9.3.0 when written) and the *Descent Mk3 43mm / Mk3i 43mm* device (`descentmk343mm`): everything lands in
`~/Library/Application Support/Garmin/ConnectIQ/` (`current-sdk.cfg` names the SDK). The compiler needs a JDK 17+
(`brew install openjdk@17`; `build.sh` finds it). `brew install libmtp` for the watch on USB. Screenshots of the
simulator need Screen Recording permission for the terminal app.

**The developer key** signs every build: `~/.connectiq/sukrara-developer-key.der` (4096-bit RSA, PKCS#8 DER; the `.pem`
beside it), outside the repo like the Android release key. Made once with `openssl genrsa -out k.pem 4096 && openssl
pkcs8 -topk8 -inform PEM -outform DER -in k.pem -out k.der -nocrypt`. Keep it (back it up): the watch only updates a
face signed with the same key, and the store app is tied to it. `CIQ_KEY` points `build.sh` elsewhere.

| What | Command |
|---|---|
| Release `.prg` (no debug code) | `apps/garmin/build.sh aura` → `apps/garmin/bin/SukraraAura.prg` (`aura debug`, `aura cases` for the others) |
| Store packages (`.iq`, signed with the store key, verified) | `tools/release/garmin-release.sh aura <version>` (public) or `aura --beta` (the beta app id) → `apps/garmin/bin/dist/`; uploading: [docs/20 › Connect IQ store](20-publishing.md#connect-iq-store-the-owners-steps-beta-first-then-public) |
| The face's words against `words.json` | `python3 apps/garmin/aura/tools/garmin_test.py words` |
| The phone link's tests (simulator) | `apps/garmin/tools/link-test.sh aura` |
| Numbers and pictures of every case (simulator) | `uv run --with pillow --with pyobjc-framework-Quartz apps/garmin/aura/tools/garmin_test.py run <dir> [<core refs>]` |
| References without the dial, halo, sweep | `node apps/garmin/aura/tools/core_refs.mjs <dir>` (Chrome, like `tools/watchface/build.mjs`) |
| Fonts and icons | `uv run apps/garmin/aura/tools/fonts.py` (`--check`) |
| One screenshot of the simulator | `uv run apps/garmin/tools/simshot.py out.png` |
| Onto the owner's watch | `tools/install.sh watch` (then the owner picks the face) |

**Simulator.** `"$SDK/bin/connectiq"` opens it, `"$SDK/bin/monkeydo" <prg> descentmk343mm` runs a build in it (the
simulator must be running). It is one app for every session: don't quit one you didn't start; agents take turns.
The debug case loader (`build.sh aura cases`, `aura/manifest-cases.xml` with its own app id) shows a calibration frame, then
every case of `cases.json` for 6 s each at the reference's clock, printing each case's layout; `garmin_test.py run`
reads the prints (numbers against `expect.json`: positions ±0.5 px, angles ±0.1°, opacities ±0.01, strings and
colours exact) and screenshots each case (`simshot.py`: `screencapture` of the simulator's window, cropped where the
calibration marks are) to diff with `docs/img/aura/ref/` (`compare -fuzz 15%` equivalent; diff images in `<dir>/diff`
and, against the dial-less references, `<dir>/diff-core`). The loader is debug only: `source-cases/` (every symbol
`(:debug)`) is built by `cases.jungle` alone; the `debug` check fails if the face's `source/` names it or
`monkey.jungle` builds it, and `verify_prg.sh` refuses a `.prg` that carries it.
The loader is a *watch-app* (its frames from a 1 s timer): the face and the layout dump together don't fit a face's
128 KB, the face alone does (its memory is in the run's "cost" lines). It also shows always-on variants of the
brightest states (`aod-*`, `tools/cases.py`), measured against Garmin's 10 % luminance rule. `AURA_CASES=id,id…`
runs only those cases, `CIQ_DEVICE=<product>` another watch of the manifest (their pictures are the
Descent's: same screen, same references). monkeydo sometimes loses the simulator mid-run (it exits 0 while the app goes on; a new
monkeydo can't reach that simulator): the run then quits the simulator and goes on with the cases left.
A filled anti-aliased circle under 1 px drawn into a `BufferedBitmap` crashes the simulated firmware (the blue
triangle): the dial draws such dots as single pixels.

**Which watches** (SPEC.md §1): the round 390 × 390 AMOLED products with `enhancedGraphicSupport` in their
`compiler.json`; the face needs `Dc.setFill` / `setStroke` and PNG-packed atlases (monkeyc warns "Packing format 'png'
is not supported" on the others, and they crash at the first `setFill`), and a graphics pool of 3 MB or more
(`graphicsResourcePoolSize` in its `simulator.json`; the font atlases go there: the Forerunner 170 / 170 Music's 2 MB
runs out at the first draw). `build.sh aura export` builds every product,
`CIQ_DEVICE=<product> build.sh aura` one; the SDK Manager installs a watch's device files.

**On the watch.** `tools/install.sh watch` builds the release, verifies it and copies it over MTP to
`GARMIN/Apps/SUKAURA.PRG` (`apps/garmin/tools/sideload.py`, libmtp with the storage set: `mtp-sendfile` fails on this watch; only that file is written; an older copy of it is
replaced; close Garmin Express / OpenMTP first). The watch installs it when unplugged; select it on the watch (hold
MENU › Watch face). A sideloaded face has no settings page in the Connect IQ phone app (that needs the store package):
use the watch's Customize menu.

**CI**: [Garmin face on CI](#garmin-face-on-ci) (the SDK, a CI account's device files, a key made for the run).
`fonts.py --check`, `gen.py --check` (settings) and `garmin_test.py words` need only Python; `garmin_test.py run`
runs there under Xvfb (`xvfb-run`, Pillow 9+, ImageMagick's `import` for the screen).

**Platform decisions** (research with sources in the round's notes; the Garmin facts in
[docs/evidence/42-watch-faces/garmin.md](evidence/42-watch-faces/garmin.md)):
- *Fonts*: Atkinson Hyperlegible Next rendered by FreeType (unhinted, like the reference's browser) into one atlas per
  size of SPEC §2 (white glyphs, 8-bit alpha PNG bitmaps, `packingFormat="png"`), every glyph drawn by the face with
  `Dc.drawBitmap2` (`:bitmapX…`, `:tintColor`) at its pen position. Not Connect IQ font resources: `antialias="true"`
  is the only anti-aliasing they offer and it keeps about 8 grey levels on the edges (measured on the simulator's time),
  where a tinted PNG keeps all 256 ([Resources › Fonts](https://developer.garmin.com/connect-iq/core-topics/resources/)).
  Bitmaps load into the graphics pool (3 MB on this watch), not the 128 KB heap
  ([Graphics](https://developer.garmin.com/connect-iq/core-topics/graphics/)). The always-on time is a stroked
  variant (FreeType's stroker), not drawn twice; the halo has grown variants of the time and value (`t*h`, `v*h`).
- *Dials and alpha*: API 4's ARGB colours (`Graphics.createColor`, `setFill` / `setStroke`) and anti-aliasing work on
  this AMOLED (`alphaBlendingSupport` in the device's `compiler.json`), but Connect IQ has no gradients and blending is
  per primitive. So the dial is painted once into a 390 × 390 `BufferedBitmap` (graphics pool) and copied on every
  frame; it is painted again only when its inputs change (a new reading, the mode, a setting). In that cache each
  mark is drawn opaque in its colour pre-multiplied by its opacity over black (the cache holds only the dial and no two
  marks overlap), and each gradient becomes one opacity per mark: a contour ring the fade at its radius, a wave at its
  depth, a laser per 10 px piece, the glow 3 px rings (SPEC §13). The halo is drawn in black over the copy (the grown
  glyphs, the arrow's polygons grown 6.5 px, boxes and arc bands for the small texts), then the sweep (ARGB arcs: 6° ×
  10 px pieces of the tail, the leading line through the radial ramp), then the content.
- *Motion*: watch faces may run a `Timer` only in high power, the ~10 s after a wrist raise
  ([Watch faces](https://developer.garmin.com/connect-iq/user-experience-guidelines/watch-faces/)): the glide on a new
  reading runs at 25 fps for 0.9 s then; a reading in low power is drawn in place. `Timer.start` in low power crashes
  the face ([Timer](https://developer.garmin.com/connect-iq/api-docs/Toybox/Timer/Timer.html); the watch puts its
  default face back), and `onShow` can come in low power (back from a notification), so only `onExitSleep` opens the
  glide, with the display mode high power, and a refused timer draws the reading in place (#223). The sweep steps at
  the face's 1 Hz (a smooth sweep would redraw the screen 10–25 times a second for the whole wrist raise). Connect IQ
  exposes no reduced-motion setting (`DeviceSettings` has none), so the glide can't follow one.
- *Always-on luminance*: Garmin's rule since the Venu 2 is under 10 % of the screen's luminance
  ([AMOLED FAQ](https://developer.garmin.com/connect-iq/connect-iq-faq/how-do-i-make-a-watch-face-for-amoled-products/));
  `garmin_test.py run` measures every always-on screenshot (the `aod-*` variants `cases.py` adds: very low, LO, a
  filled pill…) and fails at 10 %.
- *Text along the arcs*: `drawRadialText` takes only system vector fonts, so each glyph of the arc fonts is a
  sub-rectangle of its atlas drawn with `Dc.drawBitmap2` (API 4.2.1: `:bitmapX…`, `:tintColor`, `:transform`) at its
  angle; tinting needs `packingFormat="png"` ([Dc](https://developer.garmin.com/connect-iq/api-docs/Toybox/Graphics/Dc.html),
  [tint exception](https://forums.garmin.com/developer/connect-iq/f/discussion/359101/how-to-resolve-exception-source-must-not-use-a-color-palette)).
  The icons are one tinted, rotated atlas the same way.
- *Settings*: properties + `settings.xml` (generated from the schema) for the Connect IQ app, and `getSettingsView()`
  (Menu2) on the watch, which sideloaded faces need
  ([Properties](https://developer.garmin.com/connect-iq/core-topics/properties-and-app-settings/),
  [sideloaded settings](https://forums.garmin.com/developer/connect-iq/f/discussion/429848/solved-settings-connect-iq-app-for-sideloaded-app---is-this-possible)).
- *Touch and hold*: `WatchFaceDelegate.onPress` (4.2.0, the Descent Mk3 has hold but no tap on faces); it returns false
  unless Private is on, so the watch's own hold still works
  ([WatchFaceDelegate](https://developer.garmin.com/connect-iq/api-docs/Toybox/WatchUi/WatchFaceDelegate.html)).
- *Always-on*: `System.getDisplayMode()` / `onDisplayModeChanged` (5.0) with `onEnterSleep` as the fallback; the whole
  face shifts ±2 px a minute ([System](https://developer.garmin.com/connect-iq/api-docs/Toybox/System.html)). The
  watch calls `onDisplayModeChanged` in the background process too, where only `(:background)` code is loaded: the app
  acts on it (and on `onSettingsChanged`) only in the face's process, else "Illegal Access (Out of Bounds)" (#223).
- *Night*: the sleep window from `UserProfile.getProfile().sleepTime / wakeTime`, wrapping past midnight
  ([Profile](https://developer.garmin.com/connect-iq/api-docs/Toybox/UserProfile/Profile.html)).
- *Battery*: `Stats.batteryInDays` (3.3.0), percent / 10 when it reads 0
  ([Stats](https://developer.garmin.com/connect-iq/api-docs/Toybox/System/Stats.html)).
- *Fields*: weather `Weather.getCurrentConditions()` (°F with the watch's statute units), sunset `Weather.getSunset`
  at the weather's place, heart rate `Activity.Info` then `ActivityMonitor.getHeartRateHistory`, Body Battery
  `SensorHistory.getBodyBatteryHistory`, altitude `Activity.Info.altitude`, steps / floors `ActivityMonitor.Info`,
  notifications `DeviceSettings.notificationCount`; unknown → `--`.
- *Second time zone*: the watch's own alternate zones aren't open to Connect IQ, so a city list (the setting) and
  `Gregorian.localMoment(location, now)` (3.3.0), which applies the place's zone and daylight saving
  ([Gregorian](https://developer.garmin.com/connect-iq/api-docs/Toybox/Time/Gregorian.html),
  [forum](https://forums.garmin.com/developer/connect-iq/f/discussion/260362/how-can-i-get-the-user-s-configured-alternate-timezone-s-from-the-sdk)).
- *Screenshots*: the SDK has no command for them; `screencapture -l` of the window, as
  [garmin-simulator-crossplatform-mcp](https://github.com/wroblisko/garmin-simulator-crossplatform-mcp) does.

## Windows VM

A Windows 11 VM on the Mac for testing Windows apps (first used for Sugar Sense's tray app,
[docs/46](46-sugar-sense.md)) and, later, building one. Set up on 2026-11-21 (Apple silicon, 10 cores, 16 GB).

**Why UTM.** [UTM](https://docs.getutm.app/guides/windows/) 5 (QEMU on Apple's Hypervisor.framework): free, open
source, no account, a Homebrew cask, scriptable (`utmctl`, and AppleScript with screen capture and keyboard / mouse
input). VMware Fusion, free for any use since November 2024, was the first choice: Broadcom's portal holds a new
account's download at "Screening Required" (export-compliance screening; with the profile completed the button stayed
disabled and the terms box couldn't be ticked), and Homebrew removed its cask. Once screening clears it's My Downloads ›
Free Software Downloads › VMware Fusion
([KB 315638](https://knowledge.broadcom.com/external/article/315638/download-and-install-vmware-fusion.html)).
Parallels Desktop is the paid step up if UTM is too slow for daily work: the only hypervisor Microsoft names for
Windows 11 Arm on Apple silicon
([Microsoft](https://support.microsoft.com/en-us/windows/options-for-using-windows-11-with-mac-computers-with-apple-m1-m2-and-m3-chips-cd15fd62-9b34-4b78-b0bc-121baa3c568c)),
with DirectX 11 graphics, clipboard and drag and drop, and Coherence; the CPU is as fast (the same Hypervisor.framework,
the same Prism emulation for x64 apps).

| | |
|---|---|
| UTM | 5.0.9 (`brew install --cask utm`); CLI `/Applications/UTM.app/Contents/MacOS/utmctl` |
| VM | `sukrara-win11`: QEMU, Apple Hypervisor, machine `virt`, 4 vCPU, 6 GB RAM (+ balloon); lives in `~/Library/Containers/com.utmapp.UTM/Data/Documents/sukrara-win11.utm` (a terminal needs Full Disk Access to list it) |
| Firmware | UEFI, TPM 2.0 (UTM's software TPM), Secure Boot on: Windows 11 installed without bypasses |
| Disk | NVMe, 80 GB thin qcow2 (~20.5 GB used): it grows into the Mac's free space, 80 GB is only the ceiling |
| Network, display | virtio-net, Shared (NAT): `utmctl ip-address sukrara-win11` prints the guest's address; virtio-ramfb at 1440×900, no 3D |
| Windows | 11 Pro ARM64 26H2 (build 26300), en-US, computer name `SUKRARA-WIN11`; ISO from [Microsoft](https://www.microsoft.com/en-us/software-download/windows11arm64) (SHA-256 checked, deleted after the install) |
| Guest tools | UTM Guest Tools: VirtIO drivers, SPICE agent, `spice-webdavd` (the shared folder), QEMU guest agent (`utmctl exec / file / ip-address`) |
| Extras | OpenSSH Server (key login, PowerShell as the shell), Developer Mode on, never sleeps or blanks, hibernation off |

**Account.** Local administrator `dev`, no Microsoft account, automatic logon (so automation finds a desktop). The
password is in the login Keychain, item `sukrara-win11 VM (Windows local account dev)`
(`security find-generic-password -s "sukrara-win11 VM (Windows local account dev)" -w`), and in no file. Since 26H2
Windows setup offers no local account (`bypassnro` and `ms-cxh:localonly` are gone); Microsoft's supported way is an
answer file, `autounattend.xml` with `LocalAccounts` and `HideOnlineAccountScreens`, used here. The Store and winget's
`msstore` source work without a Microsoft account; only rating an app asks for one. Not activated (installed with
Microsoft's generic Pro key, which never activates): a watermark and no personalisation, everything else works; a
Windows 11 Pro licence activates the Arm edition.

**How it was installed** (unattended, about 20 min): a setup CD built with `hdiutil makehybrid -iso -joliet` holding
`autounattend.xml` (the password filled in from the Keychain at build time; it wipes disk 0, installs Pro, hides the
EULA / online-account / Wi-Fi screens, creates `dev` with auto-logon), `firstlogon.ps1` (Developer Mode, power
settings, deletes Windows' copies of the answer file in `C:\Windows\Panther`, installs the guest tools silently, then
OpenSSH Server with the Mac's public key in `administrators_authorized_keys` and a firewall rule), the guest tools and
the Mac's SSH public key; the VM made by AppleScript (two CD drives, the 80 GB disk, WebDAV sharing), started, and a few
keystrokes for "press any key". Afterwards the CDs became one empty removable drive and the ISOs were deleted. The
answer-file template, the scripts and the helpers below aren't in the repo yet (they're with the docs/46 notes on the
owner's Mac, `~/Documents/sugar-sense-241/findings/windows-vm-files/`): they move to `tools/windows/` with the first
Windows work.

**Daily use.**
- Start: `open -a UTM && /Applications/UTM.app/Contents/MacOS/utmctl start sukrara-win11`; Windows logs in by itself in
  about a minute.
- Stop cleanly: `utmctl stop --request sukrara-win11`, or `Stop-Computer -Force` over SSH. A plain `utmctl stop` is a
  **power-off** (its default is `--force`). Stop it whenever Android emulators need the RAM: it holds 6 GB while
  running.
- Shell: `ssh -i ~/.ssh/sukrara-win11_ed25519 dev@<guest address>` (key only, PowerShell; the host key is in
  `~/.ssh/known_hosts`); files with `scp -i …` (paths relative to `C:\Users\dev`). Without network or SSH:
  `utmctl exec sukrara-win11 --cmd powershell.exe -NoProfile -Command "…"` (the guest agent, as SYSTEM, ~2 s, outside
  the desktop session).
- Shared folder: Mac `~/WindowsShare` ↔ guest `Z:` (SPICE WebDAV from `spice-webdavd` at `http://localhost:9843/` in
  the guest, mapped at every logon by `map-mac-share.cmd` in the Startup folder), while the VM runs from UTM. Slow for
  large trees: build on `C:\` and copy results out (or `scp`). VirtFS would be faster (WinFsp + VirtIO-FS; not set
  up).
- Snapshots: `utmctl snapshot` works for QEMU VMs; none taken (disk space).

**Developing for Windows there.** Everything below runs natively on Windows 11 Arm64, and the Arm64 tools build x64
and arm64; x64 builds run in the VM under Prism, drivers don't
([Windows on Arm](https://learn.microsoft.com/en-us/windows/arm/overview),
[Visual Studio on Arm](https://learn.microsoft.com/en-us/visualstudio/install/visual-studio-on-arm-devices)).

| Stack | Needs | Notes |
|---|---|---|
| WinUI 3 / Windows App SDK 2.x (C#) | .NET 10 SDK + the [winapp CLI](https://learn.microsoft.com/en-us/windows/apps/dev-tools/winapp-cli/) (`winapp new / run / pack`), or Visual Studio 2026 with "WinUI application development" | Microsoft's [quick start](https://learn.microsoft.com/en-us/windows/apps/get-started/start-here) offers the CLI path "for agentic coding" |
| .NET 10 (WinForms, WPF, console) | .NET 10 SDK (`-r win-x64`, `-r win-arm64`) | self-contained output ≈ 200 MB (Sugar Sense's: 209 MB), framework-dependent a few MB |
| C++ / Win32 | Visual Studio 2026 "Desktop development with C++" | the Arm64-hosted MSVC targets arm64 / x64 / x86 |
| MSIX | `winapp pack`, or the Windows SDK's MakeAppx / SignTool | one `.msix` per architecture ([single-project MSIX](https://learn.microsoft.com/en-us/windows/apps/windows-app-sdk/single-project-msix)); a bundle is a separate step (`winapp pack` since [0.3.2](https://devblogs.microsoft.com/ifdef-windows/windows-app-development-cli-v0-3-2-bundling-support-smarter-initialization-and-more/), or MakeAppx) |
| Microsoft Store | a Partner Center account, [free for individuals](https://blogs.windows.com/windowsdeveloper/2025/09/10/free-developer-registration-for-individual-developers-on-microsoft-store/) | the Store re-signs (no code-signing certificate for Store builds) and serves Arm64 to Arm PCs when the package has it |
| CI | GitHub's `windows-11-arm` and `windows-latest` (x64) runners | the VM stays for UI work and debugging |

Installed later, not now (disk): start with the .NET 10 SDK, the winapp CLI and Git (about 2–3 GB); Visual Studio only
for the XAML designer or the debugger.

| What | Command | Disk (approx.) |
|---|---|---|
| .NET 10 SDK (arm64) | `winget install --id Microsoft.DotNet.SDK.10 -e` | ~0.8 GB |
| winapp CLI | `winget install --id Microsoft.WinAppCli -e` | ~50 MB, plus the SDK packages it restores per project (~1–2 GB) |
| Git for Windows | `winget install --id Git.Git -e` | ~0.4 GB |
| VS Code (Arm64) | `winget install --id Microsoft.VisualStudioCode -e` | ~0.4 GB |
| Visual Studio 2026 Community + WinUI workload | `winget configure -f https://aka.ms/winui-config` | Microsoft: 20–50 GB typical ([requirements](https://learn.microsoft.com/en-us/visualstudio/releases/2026/vs-system-requirements)); WinUI alone roughly 10–20 GB |
| Claude Code (native Arm64) | `winget install Anthropic.ClaudeCode` | ~0.2 GB |

**Driving the VM from Claude Code** (what the docs/46 study used):

| Need | How | Worked |
|---|---|---|
| Commands, files | SSH + PowerShell (key login) | very well: the main channel |
| Commands without network / SSH | `utmctl exec` (guest agent, as SYSTEM) | well; slow, not in the desktop session |
| Screenshots | AppleScript `tell application "UTM" to capture screen (virtual machine id …) to POSIX file "….png"` (`cap.sh`) | very well: full-resolution PNG in ~0.3 s, no host window or focus needed |
| Clicks and keys | AppleScript `input mouse click … at {x, y}` (`with mouse button right`), `input keystroke … text "…" with modifiers {…}`, `input scan code … codes {…}` (`in.sh`); coordinates are the screenshot's pixels | well; no hover or wheel (submenus by click, PageDown by scan code) |
| Drag, hover, exact cursor | a PowerShell `SetCursorPos` / `mouse_event` script run in the desktop session (`drag.ps1`) | well |
| Anything in the logged-in desktop (UI Automation, launching apps, the resolution) | a scheduled task `uirun` (interactive, as `dev`) that runs `C:\Users\dev\auto\job.ps1`; `ui.ps.sh` copies a script over, runs the task, waits and prints the output | well: SSH sessions are session 0 and can't see the desktop |
| Reading the UI | a .NET `System.Windows.Automation` tree dump (`uia-lib.ps1`, `dump-app.ps1`) | good for standard controls, poor for hand-painted UI and WebView2 (nameless panes); it takes focus, which closes flyouts |

Not set up, and what would make it easier:
- **Claude Code inside the VM** (`winget install Anthropic.ClaudeCode`, its own sign-in there,
  [overview](https://code.claude.com/docs/en/overview)): it works on the files where they build, with no SSH round
  trips; the Mac session keeps screenshots and the overall view.
- **[Windows-MCP](https://github.com/CursorTouch/Windows-MCP)** (MIT, Python 3.12+, `uvx windows-mcp serve`): UI-tree,
  click, type, scroll, screenshot and shell tools in the user session; served over HTTP (`--transport streamable-http
  --host 0.0.0.0`) the Mac's Claude Code can add it as a remote MCP server at `http://<guest address>:<port>`, replacing
  `uirun` and `in.sh`. Its app tool needs Windows' display language in English.
- **UI tests for a Sukrara Windows app**: [FlaUI](https://github.com/FlaUI/FlaUI) (.NET, MIT) in xUnit / NUnit, or
  [FlaUI.WebDriver](https://github.com/FlaUI/FlaUI.WebDriver). WinAppDriver is unmaintained (last release 2020) and
  Appium's Windows driver was [archived](https://github.com/appium/appium-windows-driver) on 2026-11-07. Real UI
  Automation names (`AutomationProperties`) make these tools, Narrator and agents work; Sugar Sense's hand-painted panel
  is the counter-example. A `--render` mode like theirs (every state × language × theme to PNG) lets agents check the
  UI without driving it; the winapp CLI also has `winapp ui` and Windows Sandbox runs
  ([announcement](https://blogs.windows.com/windowsdeveloper/2026/01/22/announcing-winapp-the-windows-app-development-cli/)).
- `utmctl` covers start / stop / exec / file / ip-address / snapshot but not screenshots or input: those stay on
  AppleScript ([UTM scripting](https://docs.getutm.app/scripting/scripting/)).

**State (2026-11-21).** The VM is shut down, with Sugar Sense 1.2.1 installed and signed in to the owner's account
(signing out is local; reconnecting is one click). In the guest: the `uirun` task, `C:\Users\dev\auto\` (the job
runner, `uia-lib.ps1`), `map-mac-share.cmd`. On the Mac: the SSH key `~/.ssh/sukrara-win11_ed25519` (+ `.pub`), the
Keychain item, `~/WindowsShare` (empty), UTM. Nothing from VMware or Broadcom is installed.

## Orchestration that worked

One worktree per agent from the integration branch (`next`), a shared interface committed before dispatch,
protocol notes committed early, scaffolds (`NNa`) merged before the wave that builds on them, phone ownership handed
over explicitly between sessions.

### Cleanup

`tools/cleanup.sh [--dry-run] [--fetch] [--idle MINUTES]` removes every worktree and local branch whose commits are
all on origin/next or origin/main (the same commit or a rebased copy). It keeps main, next, the current worktree,
locked worktrees, any with changes or untracked files, and any used in the last 4 hours (an agent waiting on CI).
A squash-merged branch stays and is listed: delete it by hand once checked. Whoever creates a leftover removes it
(CLAUDE.md): an agent cleans up its own emulators, processes and temp files; the session that started it removes
its worktree and branch once the work is on `next`, with this script or `git worktree remove`.
