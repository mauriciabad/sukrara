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
| `android` | Sukrara for Android | `apps/android/VERSION` | `v0.18.0` | GitHub APK (updater, Obtainium, IzzyOnDroid), Google Play |
| `mac` | Sukrara for Mac | `apps/macos/VERSION` | `mac-v0.17.2` | GitHub dmg + zip, Sparkle, Homebrew, App Store |
| `aura` | Sukrara Aura | `apps/garmin/aura/manifest.xml` | `aura-v…` | GitHub `.iq` + sideload zip, Connect IQ |
| `garmin-app` | Sukrara watch app | `apps/garmin/app/manifest.xml` | `garmin-app-v…` | same |
| `garmin-field` | Sukrara data field | `apps/garmin/field/manifest.xml` | `garmin-field-v…` | same |
| `face-sdk` | SukraraFace barrel | `apps/garmin/sdk/manifest.xml` (5.0.0) | `face-sdk-v…` | GitHub |

- **Android keeps the plain `v` tags.** Every installed GitHub build up to 0.17.1 looks for updates in the `v…` tags
  only (`Releases.compare` reads `android-v0.18.0` as version 0, older than anything) and downloads
  `Sukrara-<tag without v>.apk`: an `android-v` tag would leave every one of them on its version without a word. Those
  installs can't follow a rename, so the rename isn't made; the other apps' tags never start with `v`, so no reader
  mistakes one for Android's.
- Every line continues from where it is (0.17.1; the barrel 5.0.0); versionCode / CFBundleVersion keep the formula
  (major·1 000 000 + minor·1 000 + patch) from the app's own version, so Play and Apple only ever see higher numbers.
- The root `VERSION` goes away. Everything that read it reads its app's version: Gradle, `bundle.sh`, `version.sh
  <app>`, `install.sh`, the build-info line (`build-info.sh <app>`: *0.18.0 + 23 commits* counts from the last commit
  that changed **that app's** version). The docs no longer write a live version (their About texts say *<version> ·
  GitHub version*), so a release rewrites no doc but the links to its files (`mentions` in apps.json: the barrel's
  download link and `<iq:depends>` in GARMIN-FACES.md and the sample face).

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
  goes in separate files. Its links are written from the repository's root; the release rebases them for each
  CHANGELOG's folder (and makes them absolute at the tag in the GitHub notes).
- Releasing an app takes every file naming it: the bullets become the app's entry in its own `CHANGELOG.md` (next to
  the app, `## <version> — <date>`, features first), the highest `kind` sets the next version, and the app is removed
  from each file's `apps` (the file is deleted when none is left).
- No change file, no release: an internal change ships with the app's next real change.
- The root `CHANGELOG.md` keeps the shared history up to 0.17.1 and points to the per-app files.
- A commit that changes an app's paths without a change file naming it gets a NOTE (check `changes`, like
  `features`); `Changes-skip: <reason>` for internal work.

## Release

`tools/release/release.py` without apps lists, per app, its pending change files and the next version.
`release.py android mac` releases those apps (`android=0.18.0` overrides a version; `--from <step>` resumes from
`gate` on with the checked-out release commit's versions: the `version` step consumes the change files, so it is never
re-run):

- **preconditions**: per app a change file, a version above its current one and no tag yet, and its channels' needs
  (Play notes, App Store notes, the Connect IQ listing and store key); then every function in `RELEASE_CHECKS`, the
  hook #262's formats check plugs into.
- **version**: each app's version source, its CHANGELOG entry, its `mentions`, the change files consumed; one commit
  on `next`.
- **gate**: only the released apps' builds and smoke tests.
- **github**: `main` fast-forwarded once; per app a tag `<tag><version>` and a normal GitHub release with only its
  files and its entry as notes. Android's is GitHub's **Latest** (the updater, Obtainium and IzzyOnDroid read it);
  every other app's is `make_latest: false`.
- **channels**, only for the apps released: Sparkle, Homebrew, TestFlight, App Store (mac); Play (android); the
  Connect IQ upload kit per Garmin app; nothing more for `face-sdk`.
- The store notes stay per app and per version code (Play's, the App Store's), written before the release as today.
- **Order between apps** (#262: a watch payload change publishes the phone only once Connect IQ approved the watch
  apps): two runs, the watch apps' first; a `RELEASE_CHECKS` function can refuse the second until the first is live.
  Within a run, `garmin` is the first channel step, so Connect IQ's review (the slowest) starts at once.

What follows the tags:

| Reader | Today | Then |
|---|---|---|
| Android updater (`Releases`) | every release, tag `v…` | only `v…` tags: unchanged for the installed ones (they read `mac-v…` as older than anything); the new one skips other tags and asks for 100 releases a page, so a run of other apps' releases never hides Android's |
| Website (`release.ts`) | `/releases/latest`, needs APK and dmg | one list of releases: the newest carrying `Sukrara-<version>.apk` and the newest carrying `Sukrara-<version>.dmg` (the version from the file's name), so the shared releases up to 0.17.1 count until each app's first own one; the hero names both versions |
| Homebrew tap workflow | every published release | only `mac-v…` (the job's condition; one dispatched by hand is refused otherwise) |
| SukraraFace download | `/releases/latest/download/SukraraFace.barrel` | `releases/download/face-sdk-v<version>/SukraraFace-<version>.barrel` in GARMIN-FACES.md, rewritten by each SukraraFace release (GitHub has no "latest of a tag prefix" link, so the unversioned `SukraraFace.barrel` copy goes) |
| Website's Garmin page | "every release has a zip per app" | each app's releases on GitHub have the zip (the releases list: GitHub has no link to the newest of one prefix) |
| Obtainium, IzzyOnDroid | Latest's APK | the same: Latest stays Android's with the same `v…` tags, the other releases have no APK; nothing to change in their settings |

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
2. **The glucose stays on the wrist** (the owner, 2026-11-23): the payload's glucose `g` (its `t` and `v`) never moves,
   so a watch that can't read the rest still shows the current value, ageing by the freshness rules, with the update
   line at once; nothing else of a payload it can't read is shown. A phone or follower that can't read a format keeps
   what it last read, ageing (late, struck, gone at 20 min), never shown as current.
3. **One button to the right update** for that copy: Android GitHub version → the updater's *Check now* (its *Install*
   follows); Google Play → Sukrara's Play listing; Mac GitHub → Sparkle's *Check for Updates…*; Mac App Store → the App
   Store page; a Garmin app → its Connect IQ listing (`apps.json` `store`, the link the Garmin page's *Get* already
   opens) with the hint *Updates › Update All, then sync the watch*.
4. **The newer side waits for the older one's store**: a release that changes a format makes the new versions
   public in order: the Connect IQ apps first (Connect IQ publishes on approval), then Play and the App Store
   (managed publishing / manual release) and the GitHub releases. So when a device says *update*, the update exists.
   The owner, 2026-11-23: hold the phone for Connect IQ's review (up to 3 days, only when the watch payload changes)
   rather than ask for an update that isn't out yet.
5. **No new notification.** The watch says it on the wrist; the wearer's phone puts it on home's one banner place;
   a follower's *No data* alarm, which already rings when readings stop, names the reason and the button. A new alarm
   would ring for something the person can't act on at night.
6. **Updating clears it by itself**, at the first message that side can read; the history fills in as today (the
   watch's 3 h / 24 h `hist`, a follower's hour chunks).

### States and exits, per surface

| Surface | What it shows | Can do | Meanwhile | Ends |
|---|---|---|---|---|
| **Sukrara Aura** (and any face on the barrel) | the line where *No reading since* goes: *Update Sukrara on phone* / *Actualiza Sukrara en el móvil*, or *Update face in Connect IQ* / *Actualiza la esfera en Connect IQ* | nothing on the watch (a face can't open anything); the phone's banner has the button | the current glucose from `g` with its value colour and age, as with a late reading; nothing else of the payload | the first payload it reads |
| **Watch app** | a screen in place of the value: the same line and **Open on phone** (`openWebPage`: Garmin Connect shows a notification that opens the Connect IQ listing, or the website's Android page `/android/#versions`); the glance: the line alone | *Open on phone*, Back | the current value from `g`, ageing; logging still works: entries wait in the outbox (*Waiting: update Sukrara* / *En espera: actualiza Sukrara*), sent once the phone reads them | the first payload it reads; the outbox empties |
| **Data field**, in an activity | its value place: `--` and *UPDATE PHONE* / *UPDATE FIELD* (es *ACTUALIZA MÓVIL* / *ACTUALIZA CAMPO*), the slot's size | nothing during the activity (a data field has no input); after it, the phone's banner | the current value from `g` where the slot has room for it beside the line, else `--`; never a number it can't read | the first payload it reads |
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

- **Watch payload**: the top-level `v` (a whole number) beside `p` and `sent` in the dictionary; `unit` and `bands`
  (the value in the person's unit and colour); the glucose `g` (`t`, `v`); the port and fetch paths; the fetch's `v=`
  query; a write's `v` (outside the signature, checked before it) and the answer's `r` / `v`. A fetch or write without
  `v` is version 5 (the builds from before they said one).
- **Sharing**: the envelope (event kinds, the `gk` tag, NIP-44 under the group key, the gift-wrap kinds) and `v` at the
  top of the decrypted content; Bluetooth's frames and the hello's `v`.
- **Identity**: the invite link's shape (`/join#` + 63 characters) and the invite's `v`.

A change that must move one of these is a new format beside the old one, not a version.

**As built (#262)**, where it differs from the above:

- `formats.json` lists five formats: sharing 2, treatments 1 (their own `v`), invite 3, avatar 1 (`reads: every`: its
  renderers stay, a newer one draws the fallback) and the watch payload 5. `first` names the apps a format change
  publishes first (the watch payload's Connect IQ apps); the release check is `tools/formats/release_check.py`
  (`problems(root, releases)`, one of `release.py`'s `RELEASE_CHECKS`).
- A watch that can't read a payload keeps its glucose alone as a payload of its own version (`SukraraFace.glucoseOnly`)
  in place of the stored one: every reader already draws a payload without trend, history or status, so no draw code
  needed a second path. The version refused is kept apart (`Link.update()`, the barrel's `update()`).
- The phone names the side from the fetch's `v` alone; the report's `x` (kept the older payload) no longer means
  *outdated* (it still says the order rule kept an older one).
- The Garmin page's notice sits under the main switch (a `NoticeCard`, Warning tone) and the watch's line, the app's
  row and Share with apps' Garmin row say it too. *Update Sukrara*: the GitHub version opens About (its updater checks
  on opening and offers *Install*), Google Play opens Sukrara's listing.
- **Sukrara Aura**: two lines in the chip row (*Update Sukrara* / *on phone*, *Update face* / *in Connect IQ*): one line
  at the face's 24 px is wider than the circle there (Spanish 290–365 px), and two keep the numbers' size. It replaces
  the reading's time (the value's look says its age) and shows in private and always-on (Aura SPEC §6.6b).
- **Watch app**: the update page is the waiting page's layout (app SPEC §4.4) with the glucose where the phone's icon
  goes, so one page design serves both; START and a tap are *Open on phone* there (no list from the pages; the Log
  complication still opens it). The body says why (*This app is newer* / *Sukrara on your phone is newer*).
- **Data field**: the current glucose stays and its label row says *UPDATE PHONE* / *UPDATE FIELD* (amber), instead
  of `--` beside the words: the field reads `g` like the face, and in an activity the number matters most; a slot too
  narrow for the label shows the value alone (the face, the app and the phone say it).
- **The phone's notice** (the owner, 2026-11-23: "not the best copywriting nor ui … we should also show a banner in
  the homescreen"): one designsystem notice, `updateBanner`, on the Garmin page and as home's banner (Warning tone,
  the update icon, no close). The title names the app to update and nothing else (*Update the Sukrara Aura watch
  face* / *Update Sukrara*); the text says what the watch does until then (*shows only your glucose*), the device
  named once; one button, *Update*. The watch's line on the Garmin page no longer repeats it; the app's row keeps its
  state. Home's banner sits after the safety banners and before the tips.
- **Complications** keep the current glucose (no trend, no history), not `---`: it is a real, current reading.
- **Setup's Garmin step** shows the same `updateBanner` under its switch (`SetupGarmin.update`, AppGarmin's one
  mismatch rule); *Update* goes where home's does.
- **The watch app's update page** takes the title in small when medium needs three lines (the wide fonts of fr265s
  and the like cut *Update Sukrara on your phone* after two): one rule for the setup and update pages (app SPEC §4.3).
- **Followers**: a payload of another `v` (`OtherVersion` / `PayloadError.version`) is rejected as `version` only
  after the event's signature, epoch, author and decryption passed, and the follower keeps which app to update
  (`FollowerState.update` / `FollowerSession.update`, not persisted: the relays bring the events again); the next
  accepted event clears it. The no-data reason (`UpdateToFollow` / `.updateToFollow`) shows **over the last value
  too**, like a clock that runs behind, instead of waiting for the 20 minutes: every new reading is being dropped.
  Its words follow the Garmin notice's pattern (the title names the app to update, the text what happens until
  then): *Update Sukrara* · "Until then, Ana's new readings can't be shown here." + *Update*, or *Ana needs to update
  Sukrara* · "Their Sukrara is older than this one. New readings come once they update it." Fixture
  `contracts/protocol/sync/other-version.json` (a step's `update`, the expected state's `update`), cross-checked by
  `sync_crosscheck.py`. The Mac's *Update Sukrara…* is Sparkle's check (`Updates.checkNow`), or the App Store
  version's page.

### Draft: States and exits (for FEATURES.md when built)

**States and exits**: *versions match* → nothing shown. *A device can't read the other's format* → the device in view
says which app to update (the smaller version) and, where it can, one button to that copy's update (watch app: *Open on
phone*; phone: the Connect IQ listing or Sukrara's own updater / store; Mac: Sparkle or the App Store); a watch keeps
showing the current glucose (the frozen `g`), a phone or follower the last reading it read, ageing (never shown as
current); a watch app's entries wait in its outbox. Out: updating, which clears
it at the first message read (no tap needed); Back / Cancel leave each screen; nothing to mute or close because it ends
by itself. Reachable without a notification: the watch, the phone's Garmin page and home banner, a follower's card,
the wearer's Share rows; a follower's *No data* alarm names it. TalkBack / VoiceOver read the lines and buttons.

## Built (#261, 2026-11-23)

`contracts/apps/apps.json` (each app: version source, tag prefix, Latest, CHANGELOG, paths, channels, the Garmin entry,
`mentions`), `apps/android/VERSION` and `apps/macos/VERSION` (the root `VERSION` deleted), `tools/release/versions.py`
(the registry, version sources, change files, CHANGELOG entries: pure, used by `release.py` and `tools/check`),
`changes/` with its README, a CHANGELOG per app (the root one keeps the history up to 0.17.1), `release.py <app> …`
(above), `version.sh <app>`, `build-info.sh <app>`, check `changes` (a NOTE for an app's change without a change file,
`Changes-skip: <reason>`; a FAIL for a change file that doesn't parse), the Homebrew workflow, `appcast.py`
(`mac-v…` URLs, the Mac's CHANGELOG), `tools/ci/changes.py` (a VERSION or CHANGELOG alone runs no app job), the
readers in the table above (the Android updater, `release.ts` and the hero, the Garmin page). What
differs from the design above, and why:

- Android's tag stays `v<version>` (above: the installed updaters).
- No doc carries the live version any more, instead of the release rewriting the About texts per app: one version per
  app would have made every doc example name one app's version; only the links to a release's files are rewritten.
- The Garmin key pin no longer switches the Garmin apps on: an app is released when it is named, and
  `garmin-release.sh` still signs only with the pinned key.
- The CHANGELOG entry is `## <version> — <date>`: no summary title (no change file owns one).

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
