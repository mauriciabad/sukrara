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
- The Libre 3 native core builds from `third_party/Juggluco` (gitignored, pinned by `apps/android/sensor/juggluco.lock`;
  from a worktree the main checkout's copy is found). A release build fails without it.

## Everyday commands

| What | Command |
|---|---|
| All repo rules (CLAUDE.md as checks) | `tools/check/run.sh` (`--staged`, `--range A..B`, `--pending`, `--help`) |
| The checks' own tests | `tools/check/run.sh --selftest` |
| Cross-surface card tests | `tools/check/run.sh --layout` |
| Settings code after a schema change | `python3 tools/settings/gen.py` (commit both SettingsSpec files) |
| Android: everything CI runs | `cd apps/android && ./gradlew check` |
| Mac: build + tests | `cd apps/macos && swift build && swift test` |
| A release to every channel | `tools/release/release.py <version>` (`--dry-run`, `--from <step>`): [Release](#release) |
| Version | `tools/release/version.sh` → `0.14.2 14002 prerelease` (from `VERSION`) |

CI (`.github/workflows/checks.yml`) runs the rules on every push and the Android / Mac jobs when their paths (or
`contracts/`, `tools/`, `VERSION`, `assets/`) change; `gate` is the only required check. Every `uses:` is pinned to a
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

## Install (one build line)

Every install must contain all merged work, or features silently vanish from the owner's devices:
`tools/install.sh phone|mac [--dry-run]`. It refuses a dirty tree, a HEAD without `origin/main`, the branches in
`tools/check/integration.txt` or the commit installed last time, and failing checks; then it builds the release,
verifies it (`tools/release/verify-apk.sh` / `verify-app.sh`), installs in place and records the commit in
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
  notarized dmg with it and writes `docs/appcast.xml` (docs/20-publishing.md › Sparkle: the update feed).

## Release

One command on the release Mac publishes a version to every channel (#187); the keys never leave the Mac:
`tools/release/release.py <version>` in a clean checkout whose HEAD is `origin/next` (its Checks run green): the main
checkout or a worktree, on any branch or a detached HEAD (every push names its refspec, `HEAD:next` / `HEAD:main`; no
local `next` or `main` is needed). Before it: write CHANGELOG.md's `## Unreleased` section (optionally
`## Unreleased — <summary>`, which becomes the entry's title) and the Google Play notes,
`apps/android/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/…` (what's new on Android, at
most 500 characters each; Fastlane's layout, which F-Droid and IzzyOnDroid read too). Then the steps, in order; each
checks its result and stops with what's done, what's left and the `--from <step>` that resumes (every step can run
again: it skips what's already there):

1. `preconditions`: a clean tree at `origin/next`, its green Checks run, the version above `VERSION`, a non-empty
   Unreleased section, the Play notes, no tag yet, `tools/check/strict`, the keys, `gh`. Every wait for Checks takes
   the commit's push run or one started by hand (`gh workflow run checks.yml --ref next` when the push run is stuck,
   e.g. in a GitHub Actions incident); one green run is enough, a pull_request run doesn't count.
2. `version`: `VERSION`, the version written in DEVELOPMENT.md / QA.md / FEATURES.md, Unreleased → `## <version> — …
   (GitHub prerelease)` with a new empty Unreleased above; one commit, pushed to `next`. Its Checks run goes on while
   the gate runs, and is awaited before anything is published.
3. `gate`: `android-release.sh` (signed APK, `verify-apk.sh`, the mapping), `android-smoke.sh --final --no-build` on
   the emulator, `play-bundle.sh`, `mac-release.sh` (notarized, Sparkle-signed, `verify-app.sh --notarized`, writes
   `docs/appcast.xml`), `Bundle/smoke.sh`, `mac-appstore.sh` (build + `altool --validate-app`: the pkg `testflight`
   uploads). `./gradlew check` and `swift test` aren't run again: the green Checks run on the same commit ran them.
4. `github`: Checks on `next` green, `main` fast-forwarded, tag `v<version>`, `gh release create --prerelease
   --latest=false` (`--latest` from 1.0.0) with the APK, the Android mapping, the dmg and the zip (their sha256 checked
   against the release's), notes from the CHANGELOG entry with the disclaimer and the files; Checks on `main` green.
5. `sparkle`: the dmg on the release is the one `docs/appcast.xml` signs → the appcast committed and pushed to `main`
   and `next` (if `next` moved meanwhile, the commit is merged into it; HEAD stays on the appcast commit, so a re-run
   never brings `next`'s other work to `main`), Pages deployed, and https://sukrara.mauri.app/appcast.xml serves the
   item.
6. `homebrew`: the tap's cask (bumped by `homebrew-tap.yml`) at the version, with the dmg's sha256 and
   `auto_updates true`.
7. `play`: the play bundle on the **internal** track with the en-US / es-ES notes, through the Android Publisher API
   (edits.insert → bundles.upload → tracks.update → edits.commit) with the service account
   `~/.android/sukrara-play.json`; a version code already on the track is left alone.
8. `testflight`: unless App Store Connect has a build of the version already, the gate's pkg
   (`build/Sukrara-<version>-appstore.pkg`; missing, e.g. `--from testflight` in a fresh checkout, `mac-appstore.sh`
   builds and validates it once), its version and build number checked, uploaded with `mac-appstore.sh --upload`
   (which never builds); then waits until App Store Connect has processed it. A new build shows *Missing Compliance*
   until its export question is answered: the one click left (docs/20-publishing.md › Mac App Store: signing and App
   Store Connect).
9. `summary`: every channel's state (`--from summary` alone shows it any time).

`--dry-run` runs the read-only checks and prints every command, the notes and the Play request bodies; it changes
nothing, and exits 1 with the list when a check would stop the release. Read-only store checks: `python3
tools/release/stores.py play-tracks` (opens an edit, lists the tracks, deletes the edit) and `asc-builds`.
`--production` (from 1.0.0; not used yet) also promotes the Play release to production and submits the App Store build
for review; it needs Play production rights for the service account and an App Store Connect key with App Manager
access (today's key is *Developer*). Tests: `python3 tools/release/test_release.py` (CI).

## Strings (Android, English + Spain Spanish)

Every user-visible text goes in the owning module's `src/main/res/values/strings.xml` and the same key in
`values-es/strings.xml` (snake_case keys grouped by screen, a `<!-- what it is [CHAR LIMIT=…] -->` comment, `%1$s`
positional arguments, `<plurals>` for counts, `translatable="false"` for brand names and units; Spanish terms:
hidratos, insulina rápida / lenta, tiempo en rango). Compose reads `stringResource`; `StringsParityTest` and the
`strings` check fail on a missing key.

## Debug and test code

Debug commands that replace the sensor or make up data live only in `src/debug` (Android) or `#if DEBUG` / the
`sukrara-snap` and test targets (Mac): a release build cannot contain them (`debug` check, `verify-apk.sh`,
`verify-app.sh`). Test with synthetic data on the emulator, never the owner's devices or real relays.

The Android commands are `DebugFakes`' actions, `adb shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver -a
app.mauri.sukrara.debug.<NAME> …`: `SYNTHETIC`, `ALERTS`, `PEN_READ`, `PEN_TAGS`, `PEN_JUGGLUCO`, `RELAYS` and `UPDATES` (their
extras: docs/FEATURES.md "Sensor reminders and countdowns", "Insulin pens"; docs/QA.md "Emulator"). The Mac's:
`sukrara-snap card|windows|shell <dir>` (`-es` for Spanish) and `SUKRARA_DEV=1 SUKRARA_FIXTURE=1` (a debug dev run's
QR scanner reads the made-up `SyntheticCamera` feed, never the Mac's camera). The `debug-docs` check fails when
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

## Orchestration that worked

One worktree per agent from the integration branch (`next`), a shared interface committed before dispatch,
protocol notes committed early, scaffolds (`NNa`) merged before the wave that builds on them, phone ownership handed
over explicitly between sessions.
