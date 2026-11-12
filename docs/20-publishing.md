# 20 — Publishing Sukrara: Homebrew, notarized Mac builds, Google Play, F-Droid and the Garmin store

*2026-10-18 · research report, nothing shipped · official docs, policy pages and recent announcements, all read on
2026-10-18 (each claim links to a numbered source; the [Sources](#sources) list gives the date seen)*

## Goal

Sukrara ships today as GitHub Release files: an APK signed with the **debug keystore** (`app.mauri.sukrara.poc`) and
a Mac dmg/zip signed with a free **Apple Development** identity, so macOS says *"Apple could not verify … is free of
malware"*. Before the real app (`app.mauri.sukrara`) is built, the user wants to know how to publish it properly on
every platform it runs on:

1. **Mac**: `brew install` for developers and a plain download for everyone else; Developer ID + notarization,
   Mac App Store, in-app updates.
2. **Android**: Google Play (the user's old developer account was **closed for inactivity**: warned 2024-01-09,
   closed 2024-03-14, fee not refunded, "create a new account"), Google's 2026 **developer verification** for
   sideloaded apps, and the open alternatives (F-Droid, IzzyOnDroid, Obtainium, Accrescent).
3. **Garmin**: the Connect IQ Store for the planned watch app.
4. **Cross-cutting**: Libre/LibreView naming, privacy policy and data-safety answers, GPL obligations, costs, effort.

## Method

Web research only (no app changes): Apple, Homebrew, Sparkle, Google Play Console Help, Android Developers, F-Droid,
IzzyOnDroid, Accrescent and Garmin pages, plus news for timelines that official pages only summarise. Third-party
sources are marked as such. The app's own facts come from the repo: `poc/spikes/showcase-android/app/build.gradle.kts`
(`targetSdk 37`, `minSdk 36`, debug signing, Juggluco's GPLv3 security core compiled from a separate checkout),
the manifest (permissions below), `poc/spikes/menubar/bundle.sh` / `dmg.sh` (Developer ID + notarytool already wired),
the last release (APK 18.9 MB, dmg 11.0 MB) and the Play Store (`app.mauri.sukrara` and `.poc` both return 404, i.e.
unused).

## Result

| Platform | Recommended channel(s) | Blocker today | Cost |
|---|---|---|---|
| Mac, non-developers | Notarized dmg from sukrara.mauri.app / GitHub Releases + **Sparkle 2** in-app updates | Paid Apple Developer Program (Developer ID) | 99 USD/year |
| Mac, developers | **Own tap** `mauriciabad/homebrew-tap` → `brew install --cask mauriciabad/tap/sukrara`; later the official cask | Official cask needs Gatekeeper-passing (notarized) builds + notability | 0 |
| Mac App Store | **Go** (2026-11-11): a sandboxed build without the update check, TestFlight first ([review risk](#mac-app-store-review-risk-2026-11-11-186)) | The App Store Connect app record and the distribution certificates (the owner) | (same 99 USD) |
| Android, everyone | **Google Play** (the reinstated personal account; app created 2026-11-10), every feature but the updater (decided 2026-11-11, [Flavors](#flavors)) | Health/medical declarations; `USE_EXACT_ALARM` and root-feature review (no 12-tester rule: the account predates it) | paid (old account) |
| Android, power users | **GitHub Releases + Obtainium**, **IzzyOnDroid**; the "full" flavor with the updater (#188) | Release key; package registered for developer verification before it is enforced globally (2027) | 0 |
| Android, F-Droid main repo | Later, optional | Build from source incl. Juggluco core + rust-nostr; likely *NonFreeNet* anti-feature | 0 |
| Garmin | Connect IQ Store, free app | Developer account; privacy policy; no medical claims | 0 |

**Total: 99 USD per year (Apple) + 25 USD once (Google Play).** Everything else is free.

## Mac

### Homebrew

**The official cask is closed to Sukrara as it is today.** Homebrew 5.0.0 (2025-11-12) deprecated casks without code
signing and deprecated `--no-quarantine` / `--quarantine`, saying it "does not wish to easily provide circumvention
to macOS security features" [[H1]](https://brew.sh/2025/11/12/homebrew-5.0.0/). On **2026-09-01** Homebrew disabled every cask in `homebrew/cask` that fails
Gatekeeper checks [[H2]](https://github.com/Homebrew/brew/issues/20755)[[H3]](https://github.com/ivoronin/TomatoBar/issues/102); casks "must not require System Integrity Protection or Gatekeeper to be disabled or
bypassed" [[H4]](https://docs.brew.sh/Acceptable-Casks). Only the official tap is affected; maintainers point non-qualifying apps to their own taps [[H2]](https://github.com/Homebrew/brew/issues/20755).
The official cask also has a notability bar (shared GitHub-popularity metrics, with room for "strong independent
public interest") [[H4]](https://docs.brew.sh/Acceptable-Casks), which a young project may not meet yet.

**Own tap: works now, and the official cask later.** What it needs:

| Piece | What |
|---|---|
| Repo | `github.com/mauriciabad/homebrew-tap` with `Casks/sukrara.rb` |
| Cask | `version`, `sha256` of the dmg, `url` to the GitHub Release asset, `name`, `desc`, `homepage`, `app "Sukrara.app"`, `depends_on macos: ">= :sequoia"` (or whatever the widgets need), `auto_updates true` (added by the bump workflow with the first Sparkle release), `zap trash:` for `~/Library/Logs/Sukrara.log`, the App Group container and preferences |
| `livecheck` | `url :url` + `strategy :github_latest` (versioned asset names required) [[H5]](https://github.com/nelsonlove/homebrew-tap/pull/3) |
| Auto-bump | a release workflow in this repo (or a scheduled one in the tap) computes the sha256 and commits the new version; a scoped token with `contents: write` is enough [[H5]](https://github.com/nelsonlove/homebrew-tap/pull/3) |
| Trust | since Homebrew 6.0.0 (2026-06-11) non-official taps need explicit trust; `brew install --cask mauriciabad/tap/sukrara` (fully qualified) trusts that cask automatically, `brew tap` + short name needs `brew trust` [[H6]](https://brew.sh/7.0.0-migration-guide/)[[H7]](https://github.com/Homebrew/brew/pull/22472) |

Until the builds are notarized, a tap cask could strip quarantine in `postflight` (`xattr -dr com.apple.quarantine`),
as several taps do [[H5]](https://github.com/nelsonlove/homebrew-tap/pull/3), but that is exactly the bypass Homebrew removed upstream and it teaches users to trust an
unverified binary. **Recommendation: publish the tap only together with the first notarized build**, then submit to
`homebrew/cask` once notability is plausible; the tap cask moves over unchanged.

**Update (v0.11.14, user's call):** the tap is live with the not-yet-notarized build:
[`mauriciabad/homebrew-tap`](https://github.com/mauriciabad/homebrew-tap), `brew install --cask
mauriciabad/tap/sukrara`. No quarantine stripping: the cask's caveats explain the one-time **Open Anyway** step, the
same as the dmg (tested 2026-10-18 on macOS 27: brew-installed app blocked with *"Sukrara" Not Opened*, runs after the
approval). `brew audit --cask --strict` and `brew style` pass. `.github/workflows/homebrew-tap.yml` bumps `version` +
`sha256` in the tap on every published release (0.x prereleases included: while the version is 0.x every release is a
prerelease, and the cask follows them like a 0.x install's update check; from 1.0.0 a prerelease is skipped); it
pushes with a write **deploy key** of the tap, stored here as the secret `HOMEBREW_TAP_DEPLOY_KEY`. **Since 0.12.0**
(#112) the cask installs the notarized `app.mauri.sukrara` dmg from this repo, without the *Open Anyway* caveat, and
its `uninstall` / `zap` name `app.mauri.sukrara`. One-time setup (the repo owner):

```sh
ssh-keygen -t ed25519 -N '' -C 'release workflow → homebrew-tap' -f /tmp/tapkey
gh repo deploy-key add /tmp/tapkey.pub -R mauriciabad/homebrew-tap --allow-write --title 'sukrara release workflow'
gh secret set HOMEBREW_TAP_DEPLOY_KEY -R mauriciabad/sukrara < /tmp/tapkey
rm /tmp/tapkey /tmp/tapkey.pub
```

### Developer ID + notarization (the real fix for "could not verify")

- **Apple Developer Program: 99 USD per membership year** (local price shown at enrolment), individuals can enrol
  (2FA Apple Account, own credit card; the seller name is the person's legal name) [[A1]](https://developer.apple.com/programs/)[[A2]](https://developer.apple.com/help/account/membership/program-enrollment/). Developer ID
  certificates and notarization cost nothing extra; a free Personal Team cannot notarize [[A3]](https://developer.apple.com/programs/whats-included/).
- **Our scripts already do the rest** (`poc/spikes/menubar/bundle.sh`, `dmg.sh`, NEXT.md §5): once, create a *Developer
  ID Application* certificate and `xcrun notarytool store-credentials sukrara …`; then
  `SIGN_ID="Developer ID Application: … (TEAMID)" NOTARY_PROFILE=sukrara ./bundle.sh && NOTARY_PROFILE=sukrara ./dmg.sh`
  signs (hardened runtime), notarizes and staples the app, then the dmg, and writes the release zip. The App Group
  is team-prefixed, so no provisioning profile is needed.
- **CI** (optional, later): the same commands in a GitHub Actions macOS job with the certificate as a base64 `.p12`
  secret and an App Store Connect API key for notarytool. Keeping it local is fine at one release a week.
- **Side effects**: the paid team may have a different Team ID than the free personal team, so the App Group
  (`<TEAMID>.app.mauri.sukrara…`) and the code signature change: existing Mac users get one more Keychain prompt,
  and the widgets read a new container (the host rewrites it on the next reading). Combine it with the
  `app.mauri.sukrara` rename so it happens once.

### Mac release: Developer ID and notarization

Built 2026-11-10 ([#91](https://github.com/mauriciabad/sukrara/issues/91), notarization first, Sparkle next):
`tools/release/mac-release.sh` switches Developer ID signing and notarization on by itself once the credentials below
exist on the release Mac; until then it builds as before (Apple Development, *Open Anyway*). **The owner creates, once**
(about an hour, as the account holder):

1. **Team ID**: developer.apple.com › Account › *Membership details*. If it isn't `QWUV9H4A3G` (the free personal
   team's), say so before the first notarized release: `TEAM` in `apps/macos/Bundle/bundle.sh`, the team check in
   `tools/release/verify-app.sh` and the App Group `<TEAM>.app.mauri.sukrara` (`SukraraIDs.appGroup` and its test, the
   entitlements: #19) change together, and the widgets read a new container.
2. **Certificate: Developer ID Application.** Not *Developer ID Installer* (Sukrara ships a dmg, not a pkg) and not
   *Apple Distribution* (that's the App Store). Xcode › Settings › Accounts › the paid team › *Manage Certificates…* ›
   + › *Developer ID Application* (the private key is made in the login keychain); or developer.apple.com ›
   Certificates › + › *Developer ID Application* (G2 Sub-CA) with a CSR from Keychain Access › Certificate Assistant ›
   *Request a Certificate From a Certificate Authority*. Check: `security find-identity -v -p codesigning` lists
   `"Developer ID Application: … (<TEAM ID>)"`. Back it up: Keychain Access › My Certificates › that identity ›
   *Export…* as `.p12` with a strong password, into the password manager (like the Android key; never the repo).
3. **App ID and provisioning profile: none for notarization.** The entitlements (a team-prefixed App Group, nothing
   restricted) need no profile. Explicit App IDs (`app.mauri.sukrara`, `app.mauri.sukrara.widget`, macOS) and a
   *Developer ID* provisioning profile only if #19 decides an entitlement needs one (e.g. Time Sensitive
   notifications, which is worth checking while enrolling); that's #19's change, not the release script's.
4. **Notary credentials**, stored in the login keychain as the notarytool profile **`sukrara`**, one of:
   - *App Store Connect API key* (recommended: not tied to the Apple Account's password or 2FA, revocable on its own):
     App Store Connect › Users and Access › Integrations › App Store Connect API › *Team Keys* › + (name
     `sukrara-notary`, access *Developer*); download `AuthKey_<KEY ID>.p8` (offered only once) and note the Key ID and
     the Issuer ID. Then
     `xcrun notarytool store-credentials sukrara --key ~/Downloads/AuthKey_<KEY ID>.p8 --key-id <KEY ID> --issuer <ISSUER ID>`,
     the `.p8` into the password manager, and the download deleted.
   - *App-specific password*: account.apple.com › Sign-In and Security › *App-Specific Passwords* › + (`sukrara-notary`).
     Then `xcrun notarytool store-credentials sukrara --apple-id <Apple Account e-mail> --team-id <TEAM ID>` (it asks
     for the password, so it stays out of the shell history).

   Check: `xcrun notarytool history --keychain-profile sukrara` answers (an empty list the first time).

**What the release then does by itself**: `mac-release.sh` finds the Developer ID identity (`SIGN_ID` picks another by
name or SHA-1) and refuses unless the profile `sukrara` answers (`NOTARY_PROFILE` names another); a Developer ID build
is never released unnotarized. `bundle.sh` signs the widget, then the app, with the hardened runtime and a secure
timestamp, submits the app to notarytool (`--wait`) and staples it. `verify-app.sh --notarized` checks, on the app and
the widget, the Developer ID authority, the hardened runtime, the secure timestamp, the stapled ticket and Gatekeeper's
verdict (`spctl`: *Notarized Developer ID*). `dmg.sh` signs, notarizes and staples the dmg, and `mac-release.sh` checks
its ticket and Gatekeeper's verdict. A rejected submission stops the release (`apps/macos/build/notary.log`; details with
`xcrun notarytool log <submission id> --keychain-profile sukrara`). 0.12.0 was the first notarized release: README ›
Install, the site, docs/DEVELOPMENT.md, the dmg window and the cask no longer mention *Open Anyway* (the dmg window
shows its first-launch help only for a build that isn't notarized), and the cask can aim at the official tap.

**Sparkle**: built on 2026-11-11 (the owner's decision: signed feed, one-click *Install and Relaunch* for the GitHub / Homebrew version, none in the App Store version), see [Sparkle: the update feed](#sparkle-the-update-feed).

### In-app updates: Sparkle 2

Sparkle 2 (current 2.9 line [[S1]](https://sparkle-project.org/documentation/)): `generate_keys` puts an **EdDSA** private key in the login keychain and prints
`SUPublicEDKey`; `SUFeedURL` points at the appcast; `generate_appcast <folder>` signs the archives and writes the
appcast (+ deltas); updates must be served over HTTPS [[S1]](https://sparkle-project.org/documentation/). Developer ID signing is recommended ("if possible") and
needed for key rotation [[S1]](https://sparkle-project.org/documentation/). **Plan**: appcast at `https://sukrara.mauri.app/appcast.xml` (GitHub Pages is HTTPS),
enclosures pointing at the GitHub Release dmg; the EdDSA private key stays on the user's Mac (export a backup to the
password manager). The cask then sets `auto_updates true` so `brew upgrade` doesn't fight Sparkle. Built: see
[Sparkle: the update feed](#sparkle-the-update-feed).

### Sparkle: the update feed

Built 2026-11-11 ([#91](https://github.com/mauriciabad/sukrara/issues/91)), Sparkle **2.10.1** (SwiftPM, exact pin).

**What the app has.** The GitHub version is the `Sukrara` executable, which links `SukraraUpdater` (Sparkle behind
`SukraraApp`'s `UpdaterEngine`); `bundle.sh` embeds `Sparkle.framework` in `Contents/Frameworks`, signs it inside-out
(`Installer.xpc`, `Downloader.xpc` keeping its entitlements, `Autoupdate`, `Updater.app`, the framework; Developer ID,
hardened runtime, secure timestamp) and merges `Bundle/sparkle.plist` into its Info.plist: `SUFeedURL`
`https://sukrara.mauri.app/appcast.xml`, `SUPublicEDKey` `VM8dSc/QTIcaHv5EgT02zW0gWCNg8rr39HqcgIMJNaM=`,
`SUEnableAutomaticChecks` (once a day, no first-launch question) and `SUVerifyUpdateBeforeExtraction` (the dmg is
checked before Sparkle opens it: its EdDSA signature, or only for a key change the dmg's own Developer ID signature of
the same team; after extraction the new app must still match, below). The App Store version is the `SukraraAppStore` executable: no Sparkle
in the binary, the bundle or the Info.plist (`bundle.sh --app-store` and `verify-app.sh --app-store` refuse any).
Dev, fixture, snapshot and test runs never create the updater (`LaunchPlan`). What the person sees:
docs/FEATURES.md › Updates (Mac, GitHub version). Notarization with Sparkle's XPC services inside: accepted on
2026-11-11 (submission `1d7bb1ff-e862-4853-84a7-57e80fcd5176`, `verify-app.sh --notarized` all ok).

**Where the feed lives: GitHub Pages, `docs/appcast.xml`.** A release asset can't be the feed: the stable
`releases/latest/download/…` URL skips prereleases, and every 0.x release is one, so a 0.x install would never see an
update; a per-release URL would have to be known before the release exists. Pages is HTTPS on the project's own
domain, already serves the site and `.well-known`, deploys on every push to `main` (`.github/workflows/website.yml`;
Jekyll copies the file unchanged, `.github/site_links.py` only touches `.md` / `.html`), and the file is reviewed in
git like the rest. The enclosures stay on GitHub Releases (the same dmg as the download and the cask).

**Prereleases.** Every 0.x release is a GitHub prerelease, so `appcast.py` puts it in Sparkle's `prerelease` channel;
the app allows that channel only while its own version is 0.x (`Updates.channels`, `UpdatesTests`). From 1.0.0 a
release goes to the default channel and 1.x installs take only those, like the cask and Android's check.

**A release** (`tools/release/mac-release.sh`, everything it did before plus): once the dmg is notarized and stapled,
Sparkle's `sign_update` (from the SwiftPM artifact, `apps/macos/.build/artifacts/sparkle/Sparkle/bin`) signs it with
the key in the login keychain (macOS may ask once to let `sign_update` use it: *Always Allow*), checks the signature,
checks the keychain's public key is the app's `SUPublicEDKey`, and `tools/release/appcast.py` adds the item to
`docs/appcast.xml`: version and build, `minimumSystemVersion` 15.0, the channel, the GitHub release page as the full
notes, the version's `CHANGELOG.md` section as the notes Sparkle shows, and the enclosure
`https://github.com/mauriciabad/sukrara/releases/download/v<version>/Sukrara-<version>.dmg` with its length and EdDSA
signature (re-running replaces that version's item). Only the dmg is the update; the zip stays a plain download. An
unnotarized build never goes in. Then, in this order: publish the GitHub release with that exact dmg (any rebuild
changes the signature: run `mac-release.sh` again), **then** commit `docs/appcast.xml` and merge it to `main`; Pages
deploys it within minutes and every GitHub install sees the update at its next daily check. Committing it first would
offer a download that isn't there yet. `tools/release/release.py` keeps that order (its `github`, then `sparkle` step) and checks the
dmg on the release is the one the item signs. `UpdatesTests` checks every item (the GitHub URL, a 64-byte signature, the
channel, the build number, newest first).

**The key.** Made on 2026-11-11 with `generate_keys` on the release Mac: the private key is in the login keychain
(a password item, service `https://sparkle-project.org`, account `ed25519`), and a backup is at
`~/.apple-dev/sparkle-eddsa-private.key` (chmod 600). **The owner copies that file into the password manager** (Proton
Pass, with the rest of `~/.apple-dev`). It's printed nowhere and never committed. On a new release Mac:
`apps/macos/.build/artifacts/sparkle/Sparkle/bin/generate_keys -f <backup file>` imports it into that Mac's login
keychain; `generate_keys -p` must print the public key above.

**If the key is lost** (no keychain item, no backup): no update can be EdDSA-signed for the installed copies, because
each checks the key in its own Info.plist. Sparkle's way out is key rotation through Apple code signing (checked in
Sparkle 2.10.1's `AppInstaller.m` / `SUUpdateValidator.m`): an update whose dmg and app are signed by the same
Developer ID team as the installed app may carry a **new** `SUPublicEDKey`, and is then accepted without the old key.
So: `generate_keys` makes a new key (back it up), its public key goes into `Bundle/sparkle.plist`, and the next release
is made as usual (`dmg.sh` signs the dmg with the app's Developer ID, `mac-release.sh` signs it with the new key);
from then on the new key is the one. If the Developer ID certificate is lost too, every Mac has to download the next
dmg by hand (README › Install; the cask still works with `brew upgrade --greedy`). A leaked key: the same rotation, as
fast as possible.

**Homebrew.** `.github/workflows/homebrew-tap.yml` adds `auto_updates true` to the cask with the first release that
has the updater (and keeps it): `brew upgrade` then leaves Sukrara to Sparkle, `brew upgrade --greedy` still upgrades
it. Not before: 0.13.0 has no updater, and a cask with `auto_updates` isn't upgraded by a plain `brew upgrade`, so
0.13.0 installs would stay there. The tap's own README needs no change.

**Checking it end to end without the owner's Mac's app.** A normal launch of a build outside the repo is the owner's
app (their follower key, relays, alerts), so the local check never runs one. It runs two Developer ID builds without a
provisioning profile (`bundle.sh --no-profile`: no invite links, no data-protection keychain) as dev runs in a scratch
folder that has a `.git` marker (isolated data: `SUKRARA_DEV=1`, `SUKRARA_DEV_HOME`), with a local patch that is never
committed: the dev run starts only the updater and checks at once with automatic download on, Sparkle uses its own
defaults domain (`SUDefaultsDomain`), reads a
`http://127.0.0.1` feed, and `updater(_:willInstallUpdateOnQuit:immediateInstallationBlock:)` installs at once. N+1's
dmg is signed with `sign_update` and listed by `appcast.py`. Afterwards the builds are deleted and unregistered
(`lsregister -u`, `pluginkit -r` for the widget). Result on 2026-11-11 (0.13.90 → 0.13.91, Developer ID, Sparkle
2.10.1): the running 0.13.90 found 0.13.91 in the `prerelease` channel, downloaded the 15 MB dmg, verified it, installed
it in place and relaunched in about 3 s; the copy in place was 0.13.91 (13091) with a valid Developer ID signature, and
the relaunched copy (started without `SUKRARA_DEV`) was refused by the launch gate, as a dev copy should be. Then a feed
item "0.13.92" carrying another file's signature: *The update is improperly signed and could not be validated*,
nothing installed. The owner's app and its defaults were untouched (no `SU*` key in `app.mauri.sukrara`).

**In Sukrara.** Added: the above. Discarded, with the reason: `generate_appcast` (it wants every archive in one local
folder and rewrites the whole feed; one item per release from `appcast.py` is simpler and keeps the history in git);
delta updates (a 10 MB dmg once a week doesn't need them, and they'd need the previous archives); a signed feed
(`SURequireSignedFeed`: every hand edit of `docs/appcast.xml` would then lock all installs out; the archives are
signed and verified before extraction, which is what protects the install); a notification for a found update
(notifications are for glucose; the panel row and Sparkle's window are enough); automatic install on by default (a
follower's alarm should not change under the person without them knowing; it's one switch away); removing Sparkle's
XPC services (unused by a non-sandboxed app, but they're part of the signed framework and notarize fine).

### Provisioning profiles: Time Sensitive alarms, the data-protection keychain, universal links (#19, #34)

Three things only a provisioning profile grants; `Bundle/bundle.sh` adds them when it finds a profile, and the app
reads its own entitlements at launch and falls back without them:

| Entitlement (`Bundle/app-profile.entitlements.in`) | With it | Without (ad hoc, dev, no profile) |
|---|---|---|
| `com.apple.developer.usernotifications.time-sensitive` | alarms break through a Focus (`TimeSensitive`) | normal notifications; the panel, setup and Settings › Alerts say a Focus can hold them back |
| `keychain-access-groups` = `QWUV9H4A3G.app.mauri.sukrara` | the follower key and Juggluco's secret in the data-protection keychain, no prompts after re-signing (`KeychainStore.secrets`) | the login keychain (an ACL on the signature: one prompt per signing change) |
| `com.apple.developer.associated-domains` = `applinks:sukrara.mauri.app` | the web invite clicked anywhere opens the Follow step (`AppLink.universal`) | the web invite opens the page, which offers the `sukrara:` link |

**Owner checklist** (done on 2026-11-10 for Developer ID; repeat when a certificate or profile is renewed):

1. **App IDs** (Certificates, Identifiers & Profiles › Identifiers): `app.mauri.sukrara` with **Time Sensitive
   Notifications** and **Associated Domains**; `app.mauri.sukrara.widget` with nothing extra. No App Groups
   capability (`QWUV9H4A3G.app.mauri.sukrara` is team-prefixed: macOS grants it from the signature) and no Keychain
   Sharing (every profile carries `keychain-access-groups = QWUV9H4A3G.*`, which covers the app's own group).
2. **Profiles**: macOS › **Developer ID**, one per App ID, with the *Developer ID Application* certificate (`Sukrara Mac
   Developer ID`, `Sukrara Mac Widget Developer ID`, valid until 2044-11-05). Only if Apple Development builds should
   keep the entitlements too: a **macOS App Development** profile per App ID with the Apple Development certificate and
   this Mac registered (its *Provisioning UDID*: `system_profiler SPHardwareDataType`).
3. **Where**: `~/.apple-dev/profiles/*.provisionprofile` (`SUKRARA_PROFILES_DIR` for another folder). `bundle.sh`
   picks, per App ID, the unexpired profile that lists the signing certificate (and this Mac, for a development one),
   and without `SIGN_ID` signs with an identity such a profile lists: with the Developer ID profiles above, every
   `bundle.sh` (and so `tools/install.sh mac` and `mac-release.sh`) signs with Developer ID and embeds them. A profile
   missing a capability stops the build with what to enable. The owner keeps a backup of `~/.apple-dev` (the Developer
   ID key, the notary `.p8`, the profiles) in their password manager (Proton Pass), like the Android release key.
4. **Check**: `cd apps/macos && Bundle/bundle.sh` ends with `signed: "Developer ID Application: …", profile "Sukrara Mac
   Developer ID" until …`; `tools/release/verify-app.sh build/Sukrara.app` lists the three entitlements (`codesign -d
   --entitlements :- build/Sukrara.app` shows them all); `Bundle/smoke.sh` launches it offline with its own data and the
   app logs `launched (…; time-sensitive yes, data-protection keychain yes, universal links yes)` (`/usr/bin/log show
   --last 5m --info --predicate 'subsystem == "app.mauri.sukrara"'`). `spctl -a -vv` says *Unnotarized Developer ID*
   until the build is notarized (above); a build made on this Mac has no quarantine, so it launches. All of this ran on
   2026-11-10 with the profiles above; a build without them logs `no` three times.
5. **Site**: `docs/.well-known/apple-app-site-association` gives `/join` to `QWUV9H4A3G.app.mauri.sukrara` (the
   profile's `TeamIdentifier`; a new team changes it there, in `bundle.sh` `TEAM`, `SukraraIDs.teamID` and
   `verify-app.sh`). GitHub Pages publishes it (`_config.yml` includes `.well-known`); Macs read it through Apple's CDN,
   `curl -s https://app-site-association.cdn-apple.com/a/v1/sukrara.mauri.app` shows what they see (refreshed within
   about a day of a change).
6. **QA** on the owner's Mac (docs/QA.md Q6, Q8), then close #19: a Focus doesn't hold back an alarm; the follower key
   moved (one Keychain prompt for the old login-keychain item, *Always Allow*); the web invite opens the Follow step.

Once a profile build has run, the follower key is in the data-protection keychain, which a build without the profile
can't read (it would show *not following*). So `bundle.sh` stops when there is a profile for the app that can't sign
the build (expired, another certificate) instead of quietly building without it; `--no-profile` builds without on
purpose. A test build with the profile also claims the invite links: never launch it from Finder, and unregister it
after testing (`lsregister -u <app>`, `pluginkit -r <appex>`).

### Mac App Store: review risk (2026-11-11, #186)

**Finding: go, TestFlight first, then the Mac App Store**, with the listing and in-app texts below. It replaces the
2026-10-18 "not now", which assumed the Mac app talks to LibreView: it doesn't (LibreView, the Nightscout upload and
the local Nightscout-style server are Android only, `only: android` in `contracts/settings/settings.json`; the Mac
has no listening socket). What Sukrara for Mac does, against the guidelines [[A4]](https://developer.apple.com/app-store/review/guidelines/):

| Guideline | What it asks | Sukrara for Mac |
|---|---|---|
| 1.4.1 medical | greater scrutiny; accuracy claims must be backed or the app is rejected ("measure … blood glucose … using only the sensors on the device" is not permitted); remind users to check with a doctor; a clearance link if any | Measures nothing: it shows readings the wearer's phone already has (Sukrara for Android over Nostr or Bluetooth, or Juggluco's web server on the home network) and alerts on them. No accuracy claim, no clearance, so none is linked. **Missing: the in-app reminder** (added with the store build: setup's first step and Settings › General) |
| 2.1 completeness | the reviewer must be able to see the app work | Nothing to see without a wearer. Review notes explain it; a demo video (Mac) and, if the owner agrees, a demo invite to a test wearer (an emulator debug build with the synthetic sensor, kept running during review). No demo mode in the app (CLAUDE.md: the release app never shows made-up values) |
| 2.4.5 (i) sandbox | appropriately sandboxed | The store build is sandboxed (below); the widget already was |
| 2.4.5 (iii) login | no auto-launch without consent | *Open at login* is a setup step and a switch the person turns on (`SMAppService`); nothing registers itself |
| 2.4.5 (vii) updates | only through the Mac App Store | The store build has no update check (like Android's Play flavor) |
| 2.4.5 (ii, iv, v, ix) | Xcode tools, one bundle, no downloaded code, no root, all localizations inside | `productbuild` package of the one bundle with the widget inside; nothing downloaded or elevated; en + es in the bundle |
| 4.1(c), 5.2.1 | no other developer's brand in the name or icon | The name is Sukrara, the icon our own; no FreeStyle Libre, Dexcom, Juggluco or Nightscout in name, subtitle, keywords or icon |
| 5.1.1 privacy | a privacy policy link in App Store Connect **and in the app**; consent; a way to withdraw | https://sukrara.mauri.app/privacy (its Mac part updated for this build). **Missing: the in-app link** (added: Settings › General). No account, no analytics |
| 5.1.3 health | no health data for ads or mining; none in iCloud; disclose the health data collected | None collected by the developer; readings stay on the Mac (31 days) and travel only end-to-end encrypted between the wearer and followers. No HealthKit, no iCloud |
| 5.2.2 third-party services | permission under the service's terms | No vendor API on the Mac. Public Nostr relays (open protocol), and Juggluco's web server, a documented feature of an app on the person's own phone, on their own network |

**How comparable apps fare** (seen 2026-11-11). On the Mac App Store: *GlucoseBar* (Dexcom Share and Nightscout in the
menu bar; Health & Fitness; "Data Not Collected"; 16+; "informational purposes only … verify any readings with their
primary CGM solution") [[A6]](https://apps.apple.com/us/app/glucosebar/id6468110131?mt=12), *Sugar Sense: Menu Bar Glucose* (Libre, Dexcom and Nightscout through its own account;
Medical; "Not a medical device … Base treatment decisions on your CGM's own app or a blood glucose meter"; brands only
in the description, not the name or subtitle; no notifications) [[A7]](https://apps.apple.com/us/app/sugar-sense-menu-bar-glucose/id6796828695?mt=12), *Glucose Graph* (Nightscout)
[[A8]](https://apps.apple.com/us/app/glucose-graph/id1668560499?mt=12). On iOS: *nightguard* (a Nightscout client with alarms, Medical, "Don't use this App for medical decisions")
[[A9]](https://apps.apple.com/us/app/nightguard/id1116430352), the official *Dexcom Follow* and *LibreLinkUp*. Gone: *Sugarmate* took itself off the
App Store in 2022 when Dexcom's partner API stopped serving users outside the US [[A10]](https://twitter.com/sugarmateapp/status/1495892882749800450); *Shuggah* (Dexcom data,
reported as a paid copy of xDrip4iOS) disappeared in August 2025 without a public reason [[A11]](https://appshunter.io/ios/app/1586789452)[[A12]](https://www.diabetes.co.uk/forum/threads/shuggah-app-vanished.210952/); *Spike*
(read sensors through Bluetooth transmitters) was never on the store: a TestFlight beta, then an enterprise
certificate Apple revoked in 2019 in a general clamp-down on enterprise distribution [[A13]](https://medium.com/trit1dtech/devastating-news-for-diy-t1d-community-apple-tries-to-clamp-down-on-distribution-of-spike-app-ef57138b6163). The DIY apps that
read sensors or drive pumps (xDrip4iOS, Loop, LoopFollow) stay self-built. **The pattern**: followers that show the
person's own data with a clear disclaimer, alarms included, pass review; the trouble is reading sensors directly and
using a vendor's API outside its terms. Sukrara for Mac does neither. The remaining risks are a reviewer who can't see
data (2.1, answered by the notes and the video) and a reviewer who reads the alerts as a medical claim (1.4.1, answered
by the disclaimer and the listing's wording).

**The listing must say**: what Sukrara for Mac is (a menu bar and widget follower of someone who shares their glucose
with Sukrara on their Android phone, or of Juggluco on the same network); that it needs that person (no sensor is read
by the Mac); alerts with their limits (a sleeping, offline or muted Mac can't ring: keep the phone's own alarms on);
**not a medical device**: values can be wrong, late or missing, don't base treatment decisions on Sukrara, check the
sensor's own app or a meter and talk to your doctor; no account, no data collected by the developer, end-to-end
encrypted sharing; free software (GPL, the source link); "FreeStyle Libre is a trademark of Abbott; Sukrara isn't
affiliated with Abbott or Juggluco" when the description names them. Category **Medical** (`LSApplicationCategoryType`
already says so). App Privacy: **Data Not Collected** (end-to-end encrypted messages through relays nobody on our side
runs or reads; the readings stay on the Mac). Age rating: whatever the questionnaire gives with *Medical or treatment
information: frequent*.

**The listing must not say**: that it measures or reads glucose from a sensor, or any accuracy figure; that it
replaces the CGM's app, a meter, the phone's alarms or a doctor; dosing or treatment advice ("take insulin", "eat
now"); "FDA", "CE", "approved", "certified", "official", "safe"; a brand (FreeStyle Libre, Libre, Dexcom, Abbott,
Juggluco, Nightscout) in the name, subtitle, keywords or icon; "best" / "#1"; screenshots with real health data (they
are synthetic, CLAUDE.md).

**Review notes** (App Store Connect › App Review Information): "Sukrara for Mac shows the glucose of a person who
shares it from Sukrara for Android (or from Juggluco on the same network). It reads no sensor itself. Without that
person the app opens normally and shows *Not following*; setup, every Settings page and *Send a test alert* work. A
video of it following a test wearer: <link>." Plus the demo invite if the owner keeps a test wearer running. No sign-in
to give: Sukrara has no accounts.

### Mac App Store: the App Store version (2026-11-11, #186)

Built next to the GitHub version, like Android's flavors ([FEATURES › Mac App Store version](FEATURES.md#mac-app-store-version-mac)):

| | GitHub version | App Store version |
|---|---|---|
| Build | `Bundle/bundle.sh` → `tools/release/mac-release.sh` (dmg, notarized) | `Bundle/bundle.sh --app-store` → `tools/release/mac-appstore.sh` (pkg) |
| Binaries | the same `swift build -c release` output | the same |
| `SukraraDistribution` (Info.plist) | `github` | `appStore` |
| Update check | once a day + *Check for Updates…* | none: not started (`ServicesPlan.all(for: .appStore)`), no menu item, no *Check now* |
| About | *0.13.0 · GitHub version* | *0.13.0 · App Store version* |
| Sandbox | no (hardened runtime) | `Bundle/appstore.entitlements.in`: network client, Bluetooth, files the person picks; plus the camera, the App Group and the profile's entitlements both versions have |
| Signing | Developer ID + the Developer ID profiles, notarized | Apple Distribution + the Mac App Store profiles; the pkg with Mac Installer Distribution |
| `CFBundleVersion` | `<versionCode>` | `<versionCode>.<yyMMdd>.<HHmm>` (a new one per upload) |
| Checks | `verify-app.sh --notarized` | `verify-app.sh --app-store`, `pkgutil --check-signature`, `altool --validate-app` |

**Same bundle id** (`app.mauri.sukrara`, decided 2026-11-11): one App ID with its capabilities (Time Sensitive, Associated
Domains), so the universal-link file, the App Group (`QWUV9H4A3G.app.mauri.sukrara`), the keychain group and the
widget's id stay one; a person has one Sukrara, and switching keeps the follow (the follower key is in the shared
keychain group). A second id would mean a second App ID, profile set, App Group and site entry, and two Sukraras that
can run side by side and both ring. The cost: the two versions can't be installed at once (TestFlight or the store
replaces the GitHub version in Applications, and `tools/install.sh mac` puts it back), and the App Store version's data
lives in its container, so after a switch the readings come back from the wearer's history and the Mac's own settings
start from the defaults (no migration: CLAUDE.md).

**What the sandbox changed in the shared code**: nothing broke. Every path comes from `homeDirectoryForCurrentUser`,
which is the container in the sandbox (`~/Library/Containers/app.mauri.sukrara/Data`), so readings, treatments, the log
and the alert sounds (`Library/Sounds` of the container, where notifications look for them) move there by themselves;
the App Group, the data-protection keychain, `SMAppService`, the global shortcut (`RegisterEventHotKey`), the camera,
Bluetooth, the save panel and `Host.current()` work as before (checked with a sandboxed probe and the smoke run: no
sandbox denial in the log). The Mac app opens no listening socket, so no `network.server`. Fixed on the way, in both
versions, for App Review: the in-app medical reminder (setup's Welcome and Settings › General › About, 1.4.1) and the
privacy policy link (About, 5.1.1). `Bundle/smoke.sh` now runs a sandboxed bundle with its dev data in a fresh folder
in its container (the only place it may write); CI's mac job builds and smoke-runs it unsigned.

**Not yet checked**: what only a running, online App Store build shows (relays, the phone nearby, Juggluco, alerts with
their sounds, the widget, open at login): QA Q6, Q8, Q22 on the first TestFlight build. The smoke runs here are
offline by design (`LaunchMode`).

### Mac App Store: signing and App Store Connect (state 2026-11-11)

**Done 2026-11-11** (an agent in the owner's signed-in Chrome; the API key `VCB5XBQ3QZ`, access *Developer*, reads
certificates, App IDs, profiles and builds but is refused (403) creating a certificate or a profile, and can't create
the app record):

- **Agreements**: the *Free Apps* agreement is active; *Paid Apps* (not needed) is unsigned. Business also asks for
  the **EU trader status** (Digital Services Act): an App Store version in the EU needs it before review; TestFlight
  doesn't.
- **Certificates**: *Apple Distribution* (`6642XM688S`) and *Mac Installer Distribution* (`36357R8WPQ`, in the
  keychain as "3rd Party Mac Developer Installer"), both until 2027-11-11, from the CSRs in `~/.apple-dev`. Key and
  certificate in `~/.apple-dev` (`apple-distribution.key/.cer`, `mac-installer-distribution.key/.cer`, chmod 600) and
  imported into the login keychain (`security import` of a throwaway `.p12`, `-T codesign/productbuild`).
  *Developer ID Application* and *Apple Development* untouched.
- **App IDs**: unchanged. No App Groups capability needed: validation accepts the team-prefixed group
  (`QWUV9H4A3G.app.mauri.sukrara`), and the Developer ID profiles stay valid. The SwiftPM resource bundle id passed
  too.
- **Profiles** (*Mac App Store Connect*, the Apple Distribution certificate): `Sukrara Mac App Store` (`9YA7458SB6`)
  and `Sukrara Mac Widget App Store` (`323P8KTP7X`), until 2027-11-11, in `~/.apple-dev/profiles/` as
  `Sukrara_Mac_App_Store.provisionprofile` and `Sukrara_Mac_Widget_App_Store.provisionprofile`. They grant what the
  Developer ID ones do (Time Sensitive, Associated Domains, the keychain group).
- **App record**: *Sukrara*, macOS, English (U.S.), `app.mauri.sukrara`, SKU `sukrara-mac`, full access; Apple ID
  `6833533014`.
- **Validation**: the first package was refused (90255, files only root can read): `bundle.sh` copied the profiles
  with their 600 mode. It installs them with 644 now, and `verify-app.sh` checks every file is readable by everyone.
- **TestFlight**: build `0.13.0 (13000.261111.1531)` uploaded and processed (*VALID*); internal group **Owner**
  (the owner's Apple Account, automatic distribution of new builds).

Back up `~/.apple-dev` (both new keys, certificates and profiles) to the password manager with the rest.

**The owner, still to do** (nothing here is accepted or submitted by an agent):

1. **Export compliance** of the build (TestFlight › the build › *Manage*): *Standard encryption algorithms instead
   of, or in addition to, Apple's* (NIP-44: secp256k1, ChaCha20, HMAC-SHA256; TLS), none proprietary; then *Is your
   app going to be available for distribution in France?*, the owner's call (yes asks for the ANSSI declaration;
   its French documents and form answers are in [docs/anssi/](anssi/formulaire.md)).
   Until it's answered the build shows *Missing Compliance* and can't be installed.
2. **Install from TestFlight** (the TestFlight app on the Mac): first quit Sukrara (TestFlight replaces
   `/Applications/Sukrara.app`, the GitHub version) and delete `~/Library/Containers/app.mauri.sukrara`, which only
   holds the agents' offline smoke runs. QA Q6, Q8 and Q22 on it; `tools/install.sh mac` goes back to the GitHub
   version.
3. **Listing**: entered 2026-11-11, except what only the owner can give
   ([the listing](#mac-app-store-the-listing-entered-2026-11-11) has what's filled and the clicks left).
4. **Submit** for review.

**Next uploads**: `tools/release/release.py` (its `testflight` step), or by hand `tools/release/mac-appstore.sh`
(build, pkg, `altool --validate-app`), then `--upload` (uploads that pkg, builds nothing); each upload
gets a new `CFBundleVersion` and goes to the Owner group by itself once its export question is answered (or once
`ITSAppUsesNonExemptEncryption` is in the Info.plist).

### Mac App Store: the listing (entered 2026-11-11)

Entered by an agent in the owner's signed-in Chrome (App Store Connect, app 6833533014). Nothing was submitted, and no
agreement, tax, banking, trader-status or export question was touched. The wording follows the
[review risk](#mac-app-store-review-risk-2026-11-11-186) rules and the Play listing: no brand in the name, subtitle or
keywords, no accuracy claim, no dosing advice, the not-a-medical-device statement.

**Filled and saved**

| Where | en-US | es-ES |
|---|---|---|
| App Information › Name / Subtitle | *Sukrara* / `Glucose in your menu bar` | *Sukrara* / `Glucosa en la barra de menús` |
| Promotional text (≤ 170) | `The glucose of someone you look after, live in your Mac's menu bar, with alerts when it goes low or high. End-to-end encrypted, no account.` | `La glucosa de alguien a quien cuidas, en directo en la barra de menús de tu Mac, con alertas cuando baja o sube. Cifrada de extremo a extremo, sin cuenta.` |
| Keywords (≤ 100) | `diabetes,CGM,blood sugar,widget,alarm,alert,follow,caregiver,family,parent,type 1,T1D,monitor,sugar` | `diabetes,CGM,azúcar,widget,alarma,alerta,seguir,cuidador,familia,padres,tipo 1,DM1,monitor,glucemia` |
| Description | 2618 characters, below | 2856 characters, the same in Spanish (the Mac app's own words: *barra de menús*, *móvil*, *Concentración*, *urgente*) |
| Screenshots | 6 at 2880 × 1800 (`~/Pictures/sukrara-appstore/en/`) | 6 (`…/es/`) |
| Support / Marketing URL | https://sukrara.mauri.app/mac/ / https://sukrara.mauri.app/ | the same |
| App Privacy › Privacy Policy URL | https://sukrara.mauri.app/privacy | the same |

Also: version **0.14.0** (was the default *1.0*; it must equal the build's version, so change it if another build is
submitted), copyright `2026 Maurici Abad`, category **Medical** + **Health & Fitness**, content rights *No third-party
content* (the readings are the person's own data), price **Free** in all 175 regions, *Sign-in required* off, and the
review notes (below).

**Age rating: 16+** (Korea 15+; 17+ on OS versions before 26). Answers: every in-app control, capability, mature theme,
sexuality, violence and chance item *None / No*; **Medical or Treatment Information: Frequent** (the app is about
managing diabetes), **Health or Wellness Topics: Yes** (the card's *Eat carbs* / *Take insulin* pills and the report's
consensus goals are self-care guidance). Judgement calls: *Messaging and Chat: No* and *User-Generated Content: No*
(a treatment note is a ≤ 100-byte log entry seen only by the wearer and the followers they approved, not a messaging or
publishing feature; Play's IARC answer "users exchange text" is broader and stays as it is). *Infrequent* medical
content would lower the rating but isn't true for this app. GlucoseBar is 16+ too.

**Description (en-US)** (the Spanish text is in App Store Connect):

```text
Sukrara shows the glucose of someone you look after in your Mac's menu bar, and alerts you when it goes too low or too high, changes fast, or stops arriving.

Sukrara for Mac follows a person who wears a glucose sensor and shares their readings from the Sukrara app on their phone. The Mac reads no sensor itself: it shows what their phone sends, end-to-end encrypted, over the internet or over Bluetooth nearby. It can also show the glucose from Juggluco on your home network.

Glucose at a glance
• The value in its range colour, with its trend arrow, in the menu bar; struck through when it gets old.
• Click it for the glucose card and a chart you can scroll back 31 days.
• Desktop widgets with the same card.
• VoiceOver reads the value, trend and range in one sentence, and a keyboard shortcut reads the glucose aloud from any app.

Alerts
• Very low, low, high, very high and extremely high alerts, rising or falling fast, and no readings, with your own thresholds and sounds or the wearer's.
• Mute them for a while and unmute them from the menu bar, or send a test alert any time.
• When you allow it, alerts come through a Focus as Time Sensitive notifications.
• A Mac that is asleep, offline or muted can't ring: keep the alarms on the wearer's phone on.

Follow someone
• Open their invite link, scan its QR code with the Mac's camera, or pair nearby over Bluetooth and compare a 6-digit code. The wearer approves every follower.
• No account to create and no server to host.

Treatments and reports
• The wearer's insulin, carbs and notes on the chart, and your own when they allow it.
• A glucose report for 1, 7, 14 or 30 days (time in ranges, average, GMI, variability), and CSV export.

Private by design
• Readings travel end-to-end encrypted from the wearer's phone to your Mac and stay on your Mac for 31 days. The developer receives none of it: no analytics, no ads, no tracking.
• Free and open source (GPL-3.0): github.com/mauriciabad/sukrara
• In English and Spanish.

What you need
• A Mac with Apple silicon and macOS 15 or newer.
• Someone who shares their glucose with Sukrara on their Android phone and sends you an invite, or Juggluco on your home network.

Important
Sukrara is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. Values can be wrong, late or missing, and alerts may not ring. Don't base treatment decisions on Sukrara: check the sensor's own app or a blood glucose meter, and ask your doctor or diabetes care team for medical advice. Sukrara is an early preview.

Sukrara is not affiliated with Juggluco or with any sensor maker.
```

*Android* is named once, under *What you need*, because the Mac app only works with that phone app. Guideline 2.3.10
forbids other platforms' names in metadata unless the app really works with them. If App Review objects anyway, it's
a metadata-only fix: replace that line with `Someone who shares their glucose with the Sukrara phone app
(sukrara.mauri.app) and sends you an invite, …`. No new build is needed.

**Review notes** (App Review Information › Notes, 1971 characters). They say:

- Sukrara has no account, so there's no sign-in to give.
- Without a wearer, the panel says *Not monitoring: not following anyone yet*.
- What the reviewer can try: setup, every Settings pane, *Settings › Alerts › an alert › Details… › Send Test Alert*,
  and About with the medical reminder and the privacy link.
- The not-a-medical-device wording.
- Where the data goes: end-to-end encrypted, through relays the developer doesn't run, kept 31 days.
- Why each entitlement is there.
- The GPL source.

They hold one placeholder, **`[VIDEO LINK TO ADD]`**. It stands for a video of the Mac following a test wearer: a
release or TestFlight Mac following an emulator debug build with the synthetic sensor, pointed at the public relays
for that recording only. Never the owner's data. Replace the placeholder with the link, or delete that line before
submitting.

**Screenshots**: made by `assets/brand/sukrara/appstore/` (the README there has the commands) from `sukrara-snap`
renders with synthetic data only (Marta, the made-up day). The pictures are in `~/Pictures/sukrara-appstore/{en,es}/`,
outside the repo:

1. `01-hero`: menu bar + panel
2. `02-alerts`: a low with its mute + Settings › Alerts
3. `03-follow`: nearby pairing code
4. `04-widgets`: live and aged
5. `05-report`: Add Treatment + Glucose Report
6. `06-dark`: the scrolled chart

None shows an *Eat carbs* / *Take insulin* pill or an update row. Making them showed the Spanish report's period
picker in English (*7 days*): the catalog key was `%@ days` where the code looks up `%lld days`. It's fixed in the
same change. `LocalizationTests` normalises every specifier to `%@`, so it can't catch that kind of mismatch.

**The owner's clicks, in order** (App Store Connect › Sukrara):

1. **0.14.0 › App Review Information › Contact Information**: first and last name, phone, e-mail. Save. (An agent
   doesn't enter personal contact details.)
2. **The video**: record it as described under *Review notes*, upload it (YouTube *Unlisted*), and put the link in
   place of `[VIDEO LINK TO ADD]` in *Notes*. Save.
3. **The build**: once 0.14.0 is on TestFlight, answer its export question (the first item of the list above). Then
   *Build › Add Build › 0.14.0*. Save.
4. **App Privacy › Get Started**: *No, we do not collect data from this app*, then Save and **Publish**
   ([the reasoning](#privacy-answers-for-both-stores-drafts-2026-11-11)).
5. **Pricing and Availability › Set Up Availability**: the countries. All of them, or leave France out to skip the
   ANSSI declaration. Save.
6. **Business › EU trader status** (Digital Services Act). It's needed before the version can go out in the EU.
7. **0.14.0 › App Store Version Release**: pick manual or automatic release. Then **Add for Review → Submit**.

The website's Mac page (`docs/mac/index.md` › *Get it*) gets an App Store badge once the app is live.

## Android

### Google Play: the account and app as set up (2026-11-10)

The research below assumed a new account; Google **reinstated the old one** instead, which changes two things: no
"12 testers × 14 days" closed test (the account predates 2023-11-13; the app dashboard offers internal, closed and
open testing at once, with no *Apply for production* step) and no new fee.

- **Account**: personal, developer account ID `5201366068122058756`, owner `mauriciabad1@gmail.com`; identity,
  contact e-mail and phone verified; public developer e-mail `support-googleplay@a.mauri.app`; no account-level policy
  issues. The three 2024 apps (GradeCalc, Begur Secret, Emoji dealer) stay *Removed by Google*; their package names
  are unrelated.
- **App**: *Sukrara*, `app.mauri.sukrara`, Play app ID `4974476015579711409`, default language en-US, Free (can't
  become paid once published), category **Medical**, store contact `support-googleplay@a.mauri.app` +
  https://sukrara.mauri.app. Since 2026 *Create app* takes the package name, so it is reserved for this account.
- **Automatic protection off** (Protected with Play → Automatic protection → *Installer check* off): Play would
  otherwise inject an installer check that tells anyone with a non-Play copy (Aurora Store, backups, transfers) to get
  the app from Play, and ship code that isn't in the source. Sukrara is distributed on GitHub / F-Droid / others too.
- **Play App Signing uses our release key** (Protected with Play → App signing → *Change key* → *Export and upload a
  key from Java keystore*, PEPK with `--rsa-aes-encryption`). Play had generated its own key at app creation; it was
  replaced before any upload. App signing key **and** upload key are both `27:EC:21:…:8D:96`
  (`tools/release/android-cert.sha256`), so every channel shares one signature and `android-release.sh`'s output is
  uploadable as is. Play's Digital Asset Links snippet matches `docs/.well-known/assetlinks.json`.
- **API access**: Google Cloud project `sukrara-play` with the Google Play Android Developer API enabled; service
  account `sukrara-play-upload@sukrara-play.iam.gserviceaccount.com`, JSON key at **`~/.android/sukrara-play.json`**
  (0600, outside the repo; recreate with `gcloud iam service-accounts keys create`). In Play Console it has only *View
  app information*, *Release apps to testing tracks* and *Manage testing tracks and edit tester lists* for Sukrara: no
  production, store listing or policy declarations. Checked: it opens an edit and lists the tracks.
- **App content done**: Ads (none), Advertising ID (not used), Government apps (no), Financial features (none),
  Content rating (IARC, *All Other App Types*: users exchange text — treatment notes reach the wearer's followers —,
  blockable, invited friends only, no location; result all ages / PEGI 3 / ESRB E / USK 0).
- **Entered 2026-11-11** (the owner deciding on screen): Sign in details *No, no part of the app is restricted*
  ([Reviewer access](#google-play-reviewer-access-app-access)); Target audience 13–15, 16–17, 18 and over; Privacy
  policy https://sukrara.mauri.app/privacy; Health apps *Diseases and conditions management* (Play: no regional
  requirements apply); Full-screen intent and Exact alarms (both *Alarm clock*); internal testing release 0.13.0
  (versionCode 13000, tester list *Testers*: the Console account; opt-in link
  https://play.google.com/apps/internaltest/4700763397995090600; the owner uploaded the `.aab` by hand; signing
  certificate `27:EC:21:…:8D:96`). The store listing en-US + es-ES is filled in but saved as a **draft** only.
- **Still open** ([#142](https://github.com/mauriciabad/sukrara/issues/142)): Data safety (a draft, the premise
  disputed: [Data safety](#google-play-data-safety-health-apps-and-permission-declarations-drafts)), the foreground
  service declaration (needs the video, [#185](https://github.com/mauriciabad/sukrara/issues/185)), the Health Connect
  declaration (hasn't appeared), the listing's AI asset answer, then *Send changes for review*:
  [what the owner does in Play Console](#google-play-what-the-owner-does-in-play-console-in-order).

### Google Play: reviewer access (App access)

Decided 2026-11-11 by the owner, replacing the 2026-11-10 *Restricted* plan of
[#141](https://github.com/mauriciabad/sukrara/issues/141): **"No, no part of the app is restricted"**. Sukrara has no
account or sign-in and every screen opens without a sensor (CGM); without one it just has no data yet (home shows
"Not monitoring: no sensor yet"; setup, every Settings page and each alert's *Send a test alert* work). So no
instructions are entered in App access. The release build still has no demo data (the synthetic sensor is in
`src/debug/` only) and won't get a demo mode: someone could take a made-up 110 for their own glucose during a real low.

**The demo video** is still needed, for the foreground service declaration (both of its tasks ask for a video link;
[declarations](#google-play-data-safety-health-apps-and-permission-declarations-drafts)): at most 2 min, uploaded to
YouTube as *Unlisted* (it must play signed out). The owner records it on their own phone:
[#185](https://github.com/mauriciabad/sukrara/issues/185) has the shot list and how to stage each event. The emulator
video of 2026-11-11 (`~/Movies/sukrara-play/sukrara-review-demo.mp4`, a debug build with the synthetic sensor) was
**rejected by the owner** and isn't used.

How the emulator was staged (still how the store screenshots are made again):

- A fresh install of the play flavor's debug build on an AVD of its own (`play_media`: android-37.0 google_apis,
  1080 × 1920, 420 dpi), wearer role, every permission through the app's own checklist, default thresholds; in setup
  tap *Later* at the sensor step (the synthetic source isn't one of the wearer's sensors), then close home's *Finish
  setup* banner. **Settings › Alerts › Low › *Like an alarm*** on for the lock-screen alert, and the lock screen on
  (`adb shell locksettings set-disabled false`).
- The local relay for the invite (`python3 tools/relay/relay.py --port 7447` and `… -a
  app.mauri.sukrara.debug.RELAYS --es urls ws://10.0.2.2:7447`, docs/DEVELOPMENT.md; never the public relays from a
  debug build).
- The synthetic source: `adb shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver -a
  app.mauri.sukrara.debug.SYNTHETIC --ez on true --ei cycle 110`. The curve crosses the low threshold (70) at cycle
  minute 113, one reading a minute (`cycle 135` is the lowest point, 45 mg/dL).

**Store screenshots** (the same emulator session, made 2026-11-11, entered in the listing draft):
`~/Pictures/sukrara-play/en/` and `…/es/`, eight each, 1080 × 1920 PNG without alpha (Play's phone size for
promotion), System UI demo mode for the status bar: `01-home`, `02-low-alert` (the lock screen with the low alert and
the glucose notification), `03-widgets` (the widgets and the Live Update chip), `04-treatment` (15 g of carbs typed,
never saved), `05-invite`, `06-alerts`, `07-home-dark`, `08-alert-reliability`; a labelled copy of each (a "Test build
· simulated sensor data" band) in `~/Pictures/sukrara-play/labelled/`. The data is the synthetic curve (about 47 % in
range). Spanish through the emulator's language (`cmd locale set-device-locale es-ES`).

**Who keeps it current**: the owner re-records the video (#185's shot list) when home, the alert screen or the
foreground service's notification change visibly, and the screenshots before a release whose listing changes. Health
data in the screenshots is synthetic only; never the owner's screenshots (CLAUDE.md).

### Google Play: a brand-new personal account (research, 2026-10-18)

**The old account is gone.** Inactive accounts (created more than a year ago and never submitted an app, or all apps
under 1,000 lifetime installs plus no verified contacts / no Console use) are closed and the fee is not refunded
[[G1]](https://support.google.com/googleplay/android-developer/answer/11605267). Closure for inactivity is not a termination: Google's 2021 announcement said closed developers "can still open
a new account" but cannot reactivate or migrate the old one [[G2]](https://www.xda-developers.com/google-play-policy-crack-down-on-dormant-accounts/). No official page says whether the **same Google
account/e-mail** can be reused; one third-party guide suggests a fresh e-mail may be needed [[G3]](https://mrhamo.com/blog/reinstate-google-play-developer-account-closed-inactivity/). The one-account-
per-person rule is about simultaneous accounts. **Plan**: try registering with the user's current Google account;
if Play Console refuses or only shows the closed account, use a new Google account (e.g. `play@mauri.app` as a
Workspace/Gmail login). An appeal to reinstate is possible but unlikely to help for an account with no live apps [[G3]](https://mrhamo.com/blog/reinstate-google-play-developer-account-closed-inactivity/).

**Package names**: Play package names "are unique and permanent … can't be deleted or re-used" [[G4]](https://support.google.com/googleplay/android-developer/answer/9859152). Anything the old
account ever uploaded stays blocked forever (the user should check their old package names, but none can be
`app.mauri.*`: the domain and the Sukrara name are from 2026). `app.mauri.sukrara` and `app.mauri.sukrara.poc` are
unused on Play today (both 404, checked 2026-10-18). **Never upload `.poc` to Play** (even a closed test would burn
the name); the first upload should be `app.mauri.sukrara`.

**New-account requirements**

| | Personal (recommended) | Organization |
|---|---|---|
| Fee | 25 USD once [[V2]](https://developer.android.com/developer-verification/guides/faq) | 25 USD once |
| Identity | legal name + address from the Google Payments profile, **verified before publishing**; contact e-mail + phone verified by OTP; Play shows name, country and developer e-mail (full address only if monetized) [[G5]](https://support.google.com/googleplay/android-developer/answer/13628312) | D-U-N-S number required, organization name/address/phone shown publicly [[G5]](https://support.google.com/googleplay/android-developer/answer/13628312) |
| Testing before production | **closed test with ≥ 12 testers opted in continuously for 14 days**, then apply for production access (questions about the app and the test) [[G6]](https://support.google.com/googleplay/android-developer/answer/14151465); open testing unlocks only after that [[G7]](https://www.choicely.com/blog/google-play-12-tester-rule) | Not required (third-party summaries [[G7]](https://www.choicely.com/blog/google-play-12-tester-rule); the official page only speaks about personal accounts created after 2023-11-13 [[G6]](https://support.google.com/googleplay/android-developer/answer/14151465)) |
| Fits the user? | Yes | Only with a registered legal entity; not worth creating one for this |

The 12 × 14 rule applies (account created after 2023-11-13 [[G6]](https://support.google.com/googleplay/android-developer/answer/14151465)); the old account's age doesn't carry over. Count
about **3 weeks** from the first closed-test release to production [[G7]](https://www.choicely.com/blog/google-play-12-tester-rule). Testers: the user, family/followers who
already use Sukrara, the Garmin/diabetes friends — 12 Google accounts that stay opted in (use a Google Group as the
tester list so nobody drops out by accident).

**Policies that matter for this app**

| Topic | Rule | What Sukrara must do |
|---|---|---|
| Target API | New apps/updates must target **API 36+** since 2026-08-31 [[G8]](https://support.google.com/googleplay/android-developer/answer/11926878) | Already `targetSdk 37` |
| Health apps declaration | Every health/medical app fills the form; categories include *Diseases and Conditions Management* and **Medical Device Apps**, the latter including "any software interfacing with medical device hardware"; EEA apps regulated under MDR/IVDR must show device name, manufacturer, IFU, UDI-DI [[G9]](https://support.google.com/googleplay/android-developer/answer/14738291) | Declare *Diseases and Conditions Management*. Whether it must also be *Medical Device Apps* is the key risk (below) |
| Health content | Non-regulated health apps must state in the description that the app is "not a medical device and does not diagnose, treat, cure, or prevent any medical condition"; external hardware must be disclosed; a privacy policy linked in Console **and in the app**, public, not a PDF [[G10]](https://support.google.com/googleplay/android-developer/answer/16679511) | Disclaimer + "requires a FreeStyle Libre 3 sensor" in the listing; privacy page on the site; link in Settings → About |
| `USE_EXACT_ALARM` | Only for apps whose **core** function is an alarm/timer or calendar; everyone else should use `SCHEDULE_EXACT_ALARM` [[G11]](https://support.google.com/googleplay/android-developer/answer/16558241) | Glucose alarms are arguably alarm-clock-like and Juggluco ships alarms on Play, but expect a declaration and possible rejection. Safer: switch the Play build to `SCHEDULE_EXACT_ALARM` + `canScheduleExactAlarms()` prompt (the alert timing doesn't need more). **Decided 2026-11-11: keep `USE_EXACT_ALARM` and declare it** ([Flavors](#flavors)) |
| Foreground service | Each FGS type declared in App content with description, user impact and a **demo video** [[G12]](https://support.google.com/googleplay/android-developer/answer/13392821) | `connectedDevice` (BLE to sensor/followers): record the video (start, notification, stop) |
| Device and Network Abuse | No interfering with other apps / the system; **no self-updating outside Play**, no downloading executable code [[G13]](https://support.google.com/googleplay/android-developer/answer/16559646) | Play flavor without the LSPosed/SystemUI hook, lock-screen widget hook, root status bar, `DUMP`; and without the in-app GitHub update check (`SettingsScreens.kt` calls `api.github.com/…/releases/latest`). **Decided 2026-11-11: the root features stay in the Play build (opt-in, the person's own root), only the update check goes** ([Flavors](#flavors)) |
| Data safety | Required, with a privacy policy, even if nothing is collected; E2E-encrypted data unreadable by the developer is not "collected"; a user-initiated transfer to a third party is not "sharing" [[G14]](https://support.google.com/googleplay/android-developer/answer/10787469) | Nostr readings: E2E encrypted → not collected (relays see public keys, timestamps, IP). LibreView: user-initiated upload of glucose + treatments + account credentials to Abbott → disclose as health data sent at the user's request. BLE/NFC: on-device. No analytics, no ads |
| Signing | New apps are enrolled in **Play App Signing**; the developer may "Provide a copy of your app signing key" instead of a Google-generated one, before the first open/production release [[G15]](https://support.google.com/googleplay/android-developer/answer/9842756) | Upload **our own** release key so GitHub/IzzyOnDroid/F-Droid (reproducible) builds share one signature with Play |

**Is it a medical device?** This is the real risk, not a form field.
- **EU** (the user is in the EEA): software that "drives or influences the use of a device" falls in the same class as
  that device (MDR Annex VIII rule 3.3, MDCG 2019-11) [[R1]](https://health.ec.europa.eu/document/download/b45335c5-1679-4c71-a91c-fc7a4d37f12b_en?filename=mdcg_2019_11_en.pdf). Reading a Libre 3 directly, showing values and alarming
  is close to being the CGM's primary display.
- **US**: FDA's *CGM secondary display* (21 CFR 862.1350, product code PJT) is class II, 510(k)-exempt, for software
  that shows CGM data to a follower; special controls include data-tampering protection and a "no dosing decisions"
  warning [[R2]](https://www.ecfr.gov/current/title-21/chapter-I/subchapter-H/part-862/subpart-B/section-862.1350)[[R3]](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfpcd/classification.cfm?id=PJT). Software that reads the sensor itself is not covered by that exemption (our inference, not a
  ruling).
- **Precedent**: Juggluco (`tk.glucodata`, reads Libre 2/3, Dexcom, alarms, uploads to LibreView) is on Play today,
  100K+ installs, updated 2026-09-20 [[P1]](https://play.google.com/store/apps/details?id=tk.glucodata); a forum post says it was pulled once after an Abbott complaint and later
  returned (second-hand) [[P2]](https://forum.fudiabetes.org/t/juggluco-back-on-play-store/15038). xDrip+ is distributed outside Play (GitHub) [[P3]](https://forum.fudiabetes.org/t/upgrade-to-libre-3-from-libre-2-using-xdrip-on-android/14774).
- **Consequence**: publish as "not a medical device", no dosing language in the listing, keep the badge texts
  (*Eat carbs*, *Take insulin*) out of screenshots or soften them in the Play build, and accept that Play (or Abbott)
  can remove the app; the sideload channel is the fallback. A lawyer's opinion is the only real answer for the final
  app; not needed for an alpha.

### Android developer verification (sideloading)

| Date | What |
|---|---|
| 2026-03-30 | Verification open to all developers in Play Console and the new **Android Developer Console** (for apps distributed only outside Play) [[V1]](https://developer.android.com/developer-verification)[[V3]](https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console) |
| 2026-08 | **Limited distribution** accounts (free, no ID, **up to 20 devices**, for students/hobbyists), the developer APIs, and the **advanced flow** launch [[V1]](https://developer.android.com/developer-verification)[[V2]](https://developer.android.com/developer-verification/guides/faq) |
| 2026-09-30 | Enforced on certified devices (Android 7+) in **Brazil, Indonesia, Singapore, Thailand**, initially only for installs from the participating stores [[V1]](https://developer.android.com/developer-verification)[[V2]](https://developer.android.com/developer-verification/guides/faq)[[V4]](https://techaiwire.com/articles/android-developer-verification-enforcement-four-countries/) |
| 2027 | Global rollout for all apps on certified devices [[V1]](https://developer.android.com/developer-verification) |

How it affects GitHub-APK distribution once it reaches Spain (2027): the APK installs normally only if its package is
registered by a verified developer with the APK's signing-key fingerprint [[V2]](https://developer.android.com/developer-verification/guides/faq)[[V5]](https://developer.android.com/developer-verification/guides/open-source-app-registration). **ADB installs are exempt**
[[V2]](https://developer.android.com/developer-verification/guides/faq). Otherwise users need the **advanced flow**: developer options, a one-day wait, then biometric/PIN, "Install
Anyway", enabled for 7 days or indefinitely; with it disabled, updates to unregistered apps fail [[V2]](https://developer.android.com/developer-verification/guides/faq). If a package
name is already registered by someone else, the claim needs extra review [[V2]](https://developer.android.com/developer-verification/guides/faq). Losing the signing key means the
package can't be registered [[V2]](https://developer.android.com/developer-verification/guides/faq). Registration is done in Play Console too ("the single place … including for
apps distributed outside of Play"), so a Play account covers it; F-Droid-signed builds are handled by adding the
platform's key fingerprint next to ours [[V5]](https://developer.android.com/developer-verification/guides/open-source-app-registration).

**For Sukrara**: the full (root) flavor and the Play flavor should be **one package, one key**, registered in the
Play Console account; the limited-distribution tier (20 devices) is too small for public sideloading and isn't
needed once the Play account exists. F-Droid calls the programme an existential threat and won't register apps on
its developers' behalf [[V4]](https://techaiwire.com/articles/android-developer-verification-enforcement-four-countries/)[[F3]](https://f-droid.org/docs/Anti-Features/); that's F-Droid's problem, not ours, as long as we register our own key.

**Seen 2026-11-12**: installing the GitHub APK on the owner's phone showed Google Play
Protect's *App scan recommended* dialog ("Play Protect hasn't seen this app before"), with *Scan app*, *Don't
install app* and *Install without scanning*. At that point Play App Signing used our key and release 0.13.0 was on
internal testing.

### Moving off the debug key

The debug keystore can't be used anywhere public (IzzyOnDroid and Accrescent refuse debug-signed APKs [[F4]](https://izzyondroid.org/docs/general/AppInclusionPolicy/)[[F6]](https://accrescent.app/docs/guide/appendix/requirements/)) and
Play requires an upload key. The plan:

1. Generate a **release keystore** (RSA 4096 or EC P-256) on the user's Mac; back it up (password manager + an
   offline copy). It is the identity of the app forever (developer verification + every channel).
2. Use it for the new package **`app.mauri.sukrara`**. A new package installs **side by side** with
   `app.mauri.sukrara.poc`, so there is no signature clash: nothing is overwritten, but nothing carries over either.
3. Migration for existing alpha users: in the last `.poc` release, show "Sukrara moved: install the new app, then
   uninstall this one". What must carry over: the Nostr device/read keys (already restorable from the password
   manager, investigation 06), settings (add a settings export/import), the LibreView login (re-enter), the sensor
   (re-pair over NFC, works since 0.10.5; force-stop the old app first, like Juggluco). History comes back from the
   followers' backfill and LibreView.
4. (Alternative if the package were kept: APK Signature Scheme v3 key rotation can move an install from the debug key
   to a new key in place. Not worth it given the rename.)

### The Android release key

Steps 1–2 above are done; step 3's migration is dropped (the app starts clean).

- **Where:** `~/.android/sukrara-release.jks` (PKCS12, RSA 4096, valid 30 years, alias `sukrara`), outside the repo,
  made once by `tools/release/make-keystore.sh` (it refuses to overwrite an existing key). Its random password is in
  the macOS Keychain item **`sukrara-android-release`** (account `sukrara`). Public fingerprint:
  `tools/release/android-cert.sha256` (also in `docs/.well-known/assetlinks.json` for `app.mauri.sukrara`, with the
  debug key's for debug builds).
- **How a release build finds it:** the keystore from `SUKRARA_KEYSTORE` (default the path above), the password from
  the environment variable `SUKRARA_KEYSTORE_PASSWORD` only (Gradle never reads the Keychain); `tools/install.sh` and the
  release scripts fill it from the Keychain item (`security find-generic-password -s sukrara-android-release -w`).
  `tools/release/verify-apk.sh` refuses an APK signed by anything else.
- **Backup:** the owner keeps a backup of the `.jks` file and its password in their password manager (Proton Pass);
  after any change to the key, copy both there again (the password: `security find-generic-password -s
  sukrara-android-release -w`), plus one offline copy. **Losing either = no more updates** for every installed copy:
  Android only installs an update signed by the same key, so users would have to uninstall (losing their data) and
  install a new app.
- **Restore on a new Mac:** put the file back at `~/.android/sukrara-release.jks` (`chmod 600`) and recreate the
  Keychain item: `security add-generic-password -s sukrara-android-release -a sukrara -w` (it prompts for the
  password, so it stays out of the shell history).

### Before every Android release: the release smoke

Run `tools/release/android-smoke.sh --final` on the commit you're about to release (emulator only; it never
touches a phone). It builds the APK with the release key (no key or no pinned Juggluco → the build stops; it never
falls back to the debug key), runs `verify-apk.sh` (signer = `android-cert.sha256`, package / version from `VERSION`,
not debuggable, Libre 3 core inside, R8 full mode with the mapping that belongs to this APK, no debug classes or
debug-tool strings, no exported component without a permission beyond the launcher and the alert sound provider,
`POST_PROMOTED_NOTIFICATIONS` declared), then installs it fresh and launches it (see `docs/QA.md` Q10
for every assertion). Keep the mapping (`apps/android/app/build/outputs/mapping/fullRelease/mapping.txt`) with the
release: crash stack traces from the owner's phone are unreadable without it. Keep-rule fixes go to the owning
module's `consumer-rules.pro` (the module that uses the reflective / JNI library), never to a blanket `-keep`.

### Cutting a release

**One command does it** (#187): `tools/release/release.py <version>` on the release Mac, from a clean checkout at
`origin/next`, a worktree included ([docs/DEVELOPMENT.md › Release](DEVELOPMENT.md#release) has the steps;
[One-command release](#one-command-release-187) below has the store parts). What it runs, and what a release by hand would be: with `tools/check/strict` committed,
`tools/release/android-release.sh` (signed APK →
`apps/android/app/build/dist/Sukrara-<version>.apk`, verified, and its R8 mapping gzipped next to it as
`Sukrara-<version>-android-mapping.txt.gz`, about 4 MB, with a copy in `~/.android/sukrara-mappings/`) and
`tools/release/mac-release.sh` (bundle → `verify-app.sh` → dmg + zip in `apps/macos/build/`). Then tag `v<version>`
on `main` and `gh release create v<version> --verify-tag --prerelease --latest=false` with the four files while the
version is 0.x (`Sukrara-<version>.apk`, `Sukrara-<version>-android-mapping.txt.gz`, `Sukrara-<version>.dmg`,
`Sukrara-mac-<version>.zip`). The mapping is a release asset (#96) so it lives as long as the APK it decodes and anyone
reading a crash trace in an issue can use it (`retrace` from the Android SDK's cmdline-tools: `gunzip` it, then `retrace
mapping.txt trace.txt`); it isn't secret (the source is public under the GPL). About lists no download (*Sukrara on
other devices* opens the website, #179). The Android GitHub version's updater (#188) takes only the asset named exactly
`Sukrara-<version>.apk` of a release, and only with the SHA-256 GitHub lists for it (the asset's `digest`, which GitHub
computes on upload): keep that name, and never replace an asset by hand after publishing without expecting installs
to refuse it until the digest matches. Play builds need no
separate mapping: they are app bundles with the mapping inside (`BUNDLE-METADATA`), which Play reads to deobfuscate
crash reports (the API's `deobfuscationfiles` upload is only for APKs). A prerelease never becomes *Latest*; a 0.x one
bumps the Homebrew cask (`homebrew-tap.yml` on *published*), a 1.x+ prerelease never does (also when dispatched by
hand). **Mac notarization switches on by itself** once the Developer ID identity and the notarytool profile exist on
the release Mac ([Mac release: Developer ID and notarization](#mac-release-developer-id-and-notarization)); they do
since 0.12.0, and a Developer ID build is never released unnotarized.

### One-command release (#187)

Built 2026-11-11. `tools/release/release.py` runs the gate and publishes GitHub, Sparkle, Homebrew, Google Play
(internal testing) and TestFlight; the keys stay on the release Mac, none is a GitHub secret. The store calls are in
`tools/release/stores.py` (Python stdlib; the JWTs are signed by `openssl`, the Play key going to it on stdin, the
`.p8` staying in its file; nothing prints a key):

- **Google Play**: Android Publisher v3 with the service account `~/.android/sukrara-play.json` (testing tracks only):
  edits.insert → bundles.upload (Play's versionCode and sha256 must be the local bundle's) → tracks.update `internal`
  (one release, `status: completed`, the version's name, notes en-US + es-ES from
  `apps/android/fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt`) → edits.commit. A versionCode already
  on the track is left alone (re-running is safe); if Play refuses to send other pending Console changes for review,
  the commit is retried with `changesNotSentForReview=true` and the summary says the Console has changes waiting.
  Checked read-only on 2026-11-11 (`stores.py play-tracks`): the edit opens, `internal` has 0.13.0 (13000,
  *completed*), production / beta / alpha are empty, the only bundle is 13000; the edit was deleted, nothing
  committed. The app isn't a draft app any more (a draft app would only take `status: draft`).
- **TestFlight**: the gate's pkg (its version and build number checked) through `mac-appstore.sh --upload`, then the
  App Store Connect API (key `VCB5XBQ3QZ`, *Developer*) until the build is `VALID`. Checked read-only (`stores.py
  asc-builds`): app 6833533014, build 0.13.0 (13000.261111.1531) `VALID`, `usesNonExemptEncryption` unset (*Missing
  Compliance*).
- **Export compliance stays one click per build** (TestFlight › the build › *Manage*) until the owner decides. Sukrara
  uses non-exempt encryption (NIP-44: secp256k1, ChaCha20, HMAC-SHA256; TLS), so `ITSAppUsesNonExemptEncryption`
  `NO` would be false, and `YES` needs an `ITSEncryptionExportComplianceCode` from an approved compliance document;
  the France question (ANSSI) is the owner's call. The API can answer per build (`PATCH /v1/builds/{id}`,
  `usesNonExemptEncryption: true`), but that's a write nobody has tried with the *Developer* key, and it doesn't answer
  France; once the owner has answered the first build and chosen, the release can set it the same way.
- **`--production` (from 1.0.0, never run yet)**: Play: the same versionCode also on `production` (`status:
  completed`; managed publishing off so it goes live after review); needs *Release to production* for the service
  account. App Store: the App Store version created or found, the build attached, a review submission with that
  version, submitted (`reviewSubmissions` / `reviewSubmissionItems`); needs a key with *App Manager* or *Admin*
  access (the *Developer* key gets 403). The listing, screenshots, App Privacy and the EU trader status have to be
  done first in App Store Connect.

**In Sukrara.** Added: the above. Discarded, with the reason: fastlane / `gcloud` / `google-auth` (none is on the
release Mac; three REST calls and a JWT don't need them, and fewer tools means fewer keys in more places);
GitHub Actions for the uploads (the keys would have to become secrets); running `./gradlew check` and `swift test`
again in the gate (the green Checks run on the same commit ran them); waiting for the version commit's Checks run
before the gate (they run side by side; it's awaited before anything is published); a second `verify-apk.sh` /
`verify-app.sh --notarized` (`android-release.sh` and `mac-release.sh` run them); Spanish Play notes made from the
CHANGELOG (it's English and covers the Mac too).

### Alternatives to Play

| Channel | Fit | Requirements / notes |
|---|---|---|
| **GitHub Releases + Obtainium** | Now | Obtainium tracks GitHub releases and (since 1.7.0, 2026-10-17) verifies the APK signing certificate before installing [[F1]](https://github.com/ImranR98/Obtainium/releases). Needs only versioned, release-signed APKs. Add an "Add to Obtainium" link on the site |
| **IzzyOnDroid** | Next, cheap | FOSS licence, aimed at end users, APK **< 30 MB** attached to a tagged release (Sukrara: 19 MB), tag = versionName/versionCode, Fastlane metadata (descriptions, icon, screenshots), **release-signed, not debuggable** [[F4]](https://izzyondroid.org/docs/general/AppInclusionPolicy/). Reproducible builds optional but verified when possible [[F4]](https://izzyondroid.org/docs/general/AppInclusionPolicy/). Must not download executables without consent |
| **F-Droid main repo** | Later | FLOSS only, built by F-Droid from source with a free toolchain; no Play Services/trackers [[F2]](https://f-droid.org/docs/Inclusion_Policy/) (Sukrara has none: no `gms`/Firebase in the build). Hard parts: the Juggluco security core comes from a separate checkout (make it a pinned submodule), rust-nostr (`nostr-sdk`) and zxing-cpp native libs must build from source. Likely anti-feature **NonFreeNet** (LibreView upload) [[F3]](https://f-droid.org/docs/Anti-Features/); root features have no anti-feature. Use reproducible builds so F-Droid ships **our** signature (no key split) |
| **Own F-Droid repo** | Optional | `fdroidserver` repo on GitHub Pages (`sukrara.mauri.app/fdroid/repo`); easy but adds little over Obtainium + IzzyOnDroid |
| **Accrescent** | No | Sign-up allowlisted only; the project says new submissions are unlikely to be accepted for now (funding) [[F5]](https://accrescent.app/blog/posts/the-future-of-accrescent/)[[F6]](https://accrescent.app/docs/guide/appendix/requirements/) |

### Flavors

Built 2026-11-10 (#142): the app module has two product flavors in the `store` dimension, with the same package
`app.mauri.sukrara`, version and release key (`AndroidApplicationConventionPlugin`). What the play flavor contains is
the owner's decision of 2026-11-11 ([#142](https://github.com/mauriciabad/sukrara/issues/142#issuecomment-6784400336),
below). The website and About call them the *GitHub version* and the *Google Play version*.

| Flavor | Channel | Build | Contents |
|---|---|---|---|
| `full` (default) | GitHub releases, Obtainium (later IzzyOnDroid, F-Droid) | `tools/release/android-release.sh` (`:app:assembleFullRelease`, APK) | everything, and the updater (#188: daily check, notification, checked install) |
| `play` | Google Play | `tools/release/play-bundle.sh` (`:app:bundlePlayRelease`, app bundle) | everything except the updater |

**The owner's decision (2026-11-11) and why**

- **Exact alarms: the Play build keeps `USE_EXACT_ALARM`** (no `SCHEDULE_EXACT_ALARM` swap). The missed-reading (no
  data) alarm, the alert repeats and the escalation of an unanswered very low to the followers are user-facing alarms
  scheduled with `setExactAndAllowWhileIdle` (`AlarmPolicy`); they must ring on time while the phone dozes and the
  app is in the background. `SCHEDULE_EXACT_ALARM` isn't enough: on Android 14+ it is denied by default for new
  installs and the person (or a cleaner app) can revoke it, and then the checks fall back to inexact while-idle alarms
  that run minutes late without the person noticing. Play allows `USE_EXACT_ALARM` only where precisely timed alarms
  are the app's core, user-facing function, documented and promoted in the description
  [[G11]](https://support.google.com/googleplay/android-developer/answer/16558241): glucose alarms are Sukrara's core
  function, the listing leads with them, and Play's exact-alarm declaration says so
  ([the draft](#exact-alarm-declaration)). Precedent: Juggluco, on Play, declares `USE_EXACT_ALARM`.
- **The root features ship in the Play build too**: the glucose in the status bar (root slot daemon, the LSPosed
  module in System UI: `Store.rootFeatures = SystemUiRoot`, the module's manifest entries, `META-INF/xposed`) and the
  lock-screen widget (#66, the same module). They work only after the person roots their phone, installs LSPosed and
  enables Sukrara's module for System UI: the status-bar value is off by default and also needs root granted to
  Sukrara; the lock-screen widget's setting is on by default (as in the PoC) but shows nothing, and sends nothing to
  System UI, until that module runs. They change only Sukrara's own icons, text and widget in System UI. The listing
  names the user-facing feature briefly and honestly (*glucose in the status bar on supported phones, with advanced
  setup*), not the means; every Play declaration and reviewer question is answered truthfully (root and LSPosed are
  named whenever Play asks how a feature works). The policy risk is Device and Network Abuse ("interfere with … the
  user's device … including … other apps"
  [[G13]](https://support.google.com/googleplay/android-developer/answer/16559646)); the answer is that it runs only
  with the person's explicit setup and consent and touches nothing but Sukrara's own status-bar items and its
  lock-screen widget.
- **No updater in the Play build**: an app from Play may not update itself, or send people to updates, other than
  through Play [[G13]](https://support.google.com/googleplay/android-developer/answer/16559646). The updater is its own
  module, `:update`, a dependency of the `full` flavor only (#188): the Play build has no update check, notification,
  download, worker, receiver or `REQUEST_INSTALL_PACKAGES` (`allow.json` `play_permissions` removes it), and
  `Store.updates()` returns null, so About has no update rows (`ManifestTest`; `verify-apk.sh --play`: no
  `app.mauri.sukrara.update` class in the mapping, no updater in the manifest, no `api.github.com` in the dex).
- **About says which version it is** (*0.12.0 · Google Play version* / *GitHub version*) and links to the website's
  [Versions](https://sukrara.mauri.app/android/#versions) (`docs/android/index.md`: same app and key; who updates
  each; where to get each; anything a store version leaves out, today only the updater). No disabled rows and no
  "install elsewhere to unlock" prompts in the Play build (Device and Network Abuse: no means to reach a non-compliant
  APK).
- **The split stays for now**: the flavors and their code remain until Play approves a release with the root features;
  then the split can go (the updater would then be the only thing `Store` decides).

**If Play rejects …** (resubmit after each; the code is all still there):

- *`USE_EXACT_ALARM`*: put the two lines of the exact-alarm fallback (in the comment of
  `app/src/play/AndroidManifest.xml`) into the manifest and set `play_permissions` in `tools/check/allow.json` to
  remove `USE_EXACT_ALARM` and add `SCHEDULE_EXACT_ALARM`, each with its reason; flip `ManifestTest`'s assertion that
  the Play build declares `USE_EXACT_ALARM`. Nothing else: the alert checklist already names *Alarms and reminders*
  with *Fix* when exact alarms aren't allowed, the checks already fall back to inexact while-idle alarms
  (`AlarmPolicy`) and re-arm on `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Then FEATURES.md › Google Play build
  and the website's Versions list it, and the exact-alarm declaration is withdrawn.
- *The root features*: `Store.rootFeatures = null` in `app/src/play`, the root fallback in the comment of
  `app/src/play/AndroidManifest.xml` into the manifest, the `META-INF/xposed/**` exclusion back in
  `app/build.gradle.kts` (the commented line), `play_permissions` removes `app.mauri.sukrara.permission.SYSTEMUI_STATE`,
  and the checks go back to "absent": `ManifestTest`'s Play tests, `verify-apk.sh --play` (no `META-INF/xposed`, no
  module components, no `systemui` / libxposed class in the mapping; the hook check only for the GitHub build) and
  `play-bundle.sh` (no `base/root/META-INF/xposed`). The last version with all of that is `ae4810b9` (`git show
  ae4810b9:<path>`). Settings › Status bar then has no *With root* section and search no root rows (`BuildFeature`,
  `SettingsSearchTest`); FEATURES.md and the website's Versions say the Play version leaves the status bar out.

Not in either release build, so there is nothing to leave out: `DUMP` (only the debug build's `DebugReceiver` declares
it), `QUERY_ALL_PACKAGES` (the queries are listed one by one in `allow.json`). `REQUEST_INSTALL_PACKAGES` is in the
GitHub build only (the updater installs its checked update with PackageInstaller).

The bundle has no language splits (`bundle.language.enableSplit = false`): Sukrara has its own language setting, and a
split that Play didn't install would leave that choice without strings. arm64 only, like the APK.

**Checks.** `ManifestTest` also reads the play flavor's merged manifest: its permissions are `allow.json`'s with
`play_permissions` applied (empty now), `USE_EXACT_ALARM` and no `SCHEDULE_EXACT_ALARM`, the System UI module's
provider, hello receiver and permission, and the same components as the GitHub build but the updater's receiver. `tools/release/play-bundle.sh`
builds the bundle signed with the release key (password from the Keychain, `--no-daemon`) and checks it: signer =
`android-cert.sha256`, base module only, arm64 only, the Libre 3 core, the R8 mapping in `BUNDLE-METADATA` (Play
deobfuscates crash reports with it), `META-INF/xposed` inside. Then `bundletool validate` and `verify-apk.sh --play` on
bundletool's universal APK when bundletool is there (`$BUNDLETOOL` = the `bundletool-all` jar from its GitHub releases,
or `bundletool` on PATH); without it, `verify-apk.sh --play` on the play APK that Gradle builds from the same R8
output. `verify-apk.sh` checks the System UI module in both builds (`META-INF/xposed`, its manifest entries, every
LSPosed hook class keeps `intercept`) and, with `--play`, no updater (no `app.mauri.sukrara.update` class in the
mapping, no updater receiver or `REQUEST_INSTALL_PACKAGES` in the manifest, no `api.github.com` in the dex); without
it, that the GitHub build has the updater's receiver. It copies `Sukrara-<version>-play.aab` to `apps/android/app/build/dist/` and archives
the mapping in `~/.android/sukrara-mappings/<version>-play-mapping.txt`. CI's `android-play` job runs it with
`--unsigned` (no key, no Juggluco checkout: every check except the signer and the Libre 3 core).

**Uploading** isn't scripted yet: the first upload to internal testing is the owner's ([the order in Play
Console](#google-play-what-the-owner-does-in-play-console-in-order)). A script for later releases can use the service
account (`~/.android/sukrara-play.json`, testing tracks only) with the Android Publisher API (`edits.insert` →
`edits.bundles.upload` → `edits.tracks.update` → `edits.commit`).

Same package and key means a user can switch versions without reinstalling and keeps their data; whichever store has
the newer version code offers it. **Decision: same package** (not `app.mauri.sukrara.full`: two registrations and a
confusing choice).

### Google Play: Data safety, Health apps and permission declarations (drafts)

Derived from the code on 2026-11-10 for the play build (the facts are the privacy page's, `docs/privacy.md`); only the
owner submits them, in Play Console › App content. Play's rules used here [[G14]](https://support.google.com/googleplay/android-developer/answer/10787469): data the app sends off the
device is *collected* (Sukrara has no server, so everything below goes to services the person signs in to); data that
is end-to-end encrypted, so no one in between can read it, doesn't need to be disclosed; a transfer the person starts
to a service they chose isn't *sharing*; data that never leaves the device isn't collected.

**State (2026-11-11): Data safety is a draft, not submitted, and open.** Saved on page 2: collects or shares data
*Yes*; encrypted in transit *Yes*; *My app does not allow users to create an account*; *Can users login to your app
with accounts created outside of the app?* *No* (the owner's answer); deletion request *Yes*, URL
https://sukrara.mauri.app/privacy. The owner disputes the premise below and will revisit it in depth; judgement call
(1) was not applied. The recommendation (2026-11-11) is *No*, with the table below as the fallback:
[Privacy answers for both stores](#privacy-answers-for-both-stores-drafts-2026-11-11). The two readings:

- **Nothing is collected** (the owner): Sukrara has no server and the developer receives nothing; the follower name is
  an alias; the LibreView e-mail is only a sign-in. The uploads go to services the person signs in to and turns on.
- **The opt-in uploads are collected** (the draft below): Play counts data sent off the device as collected even when
  it goes to a third-party service the person chose, so Health info, e-mail, name and IDs are listed as optional.

Data safety must be submitted before any review (*Send changes for review*).

**Data safety › Data collection and security**

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | The opt-in uploads (LibreView, Tidepool, Nightscout) send health data off the phone |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Every connection is https / wss, platform CAs only (`network_security_config.xml`: cleartext only to 127.0.0.1); Nightscout takes only `https://` sites. The opt-in *Nightscout API › Also on this Wi-Fi* serves plain http to the person's own devices on their network: the phone serving its owner, not collection (say so if Play asks) |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Everything is on the phone (uninstall or *Clear storage*; deleting a treatment also deletes it upstream); uploaded data is deleted in the person's own LibreView / Tidepool / Nightscout account; the developer holds nothing. The privacy policy explains it |
| Account creation | **My app does not allow users to create an account** | No account with the developer; the sharing identity is a key pair made on the phone; LibreView and Tidepool are sign-ins to existing accounts. So no account-deletion URL |

**Data types** (everything not listed: not collected, not shared)

| Category › type | Collected | Shared | Optional | Ephemeral | Purposes | What and where |
|---|---|---|---|---|---|---|
| Health and fitness › Health info | Yes | No | Yes | No | App functionality | Glucose readings, insulin, carbs, treatment notes, sensor starts / changes: LibreView, Tidepool, Nightscout, each only after the person turns it on and signs in |
| Personal info › Email address | Yes | No | Yes | No | App functionality, Account management | LibreView and Tidepool sign-in (also LibreView's sign-in for a new Libre 3 sensor) |
| Personal info › Name | Yes | No | Yes | No | App functionality | The LibreView upload resends the account's own first and last name (which came from LibreView) |
| Personal info › Other info | Yes | No | Yes | No | App functionality | The LibreView upload resends the account's date of birth (which came from LibreView) |
| Personal info › User IDs | Yes | No | Yes | No | App functionality, Account management | The LibreView account and receiver ID, the Tidepool user ID, in those services' requests |
| Device or other IDs | Yes | No | Yes | No | App functionality | A random ID per install (LibreView device ID, Tidepool / Nightscout device name) and the sensor's serial number (LibreView, Nightscout) |
| App info and performance › Diagnostics | Yes | No | Yes | No | App functionality | The phone's battery level and charging state in Nightscout's device status (Nightscout upload only) |

Not collected, and why: sharing with followers (NIP-44 end-to-end encrypted to the followers' keys, relays can't read
it; the profile name and picture travel the same way); Bluetooth sharing (the same encrypted events; *Pair nearby*
shows the wearer's name and an invite code to nearby phones for up to 10 minutes, phone to phone, never to the
developer or a service); Health Connect (written on the device); the calendar event (written to a calendar on the
device; an account calendar the person picks is synced by that account, not by Sukrara); broadcasts and the local
xDrip+ / Nightscout servers (to apps and devices the person picks); the camera (QR codes decoded on the device); the
export file and the password-manager backup (the person's own transfers). No location, contacts, messages, photos,
audio, files, app activity, web history, financial info, advertising ID, analytics or crash logs.

*Judgement calls for the owner*: (1) To not rely on the user-initiated exemption, mark Health info, Email address,
Name, Other info, User IDs and Device or other IDs as **Shared** too (purpose App functionality): the listing only gets
more cautious. (2) A follower's opt-in *Upload the person you follow* sends the wearer's readings to the Tidepool
account the follower signs in to; it is the follower's own transfer of what they received, covered by the Health info
row.

**Security practices**: data encrypted in transit: yes. Independent security review: no. Play Families Policy: no
(not a children's app).

**Health apps declaration** (App content › Health apps; saved 2026-11-11, Play: no regional requirements apply):
**Diseases and conditions management** (diabetes: glucose display, alerts, insulin and carbs log). Not *Medical device apps*: Sukrara isn't cleared or certified as a medical
device and the listing says so. The description carries the statement Play asks for ("Sukrara is not a medical device
and does not diagnose, treat, cure, or prevent any medical condition"), the reminder to ask a healthcare professional,
the hardware it needs (a FreeStyle Libre 3 / 3 Plus, NFC and Bluetooth, or an invite to follow someone) and the
Abbott disclaimer ("FreeStyle Libre and LibreView are trademarks of Abbott. Sukrara is not affiliated with or endorsed
by Abbott.") [[G10]](https://support.google.com/googleplay/android-developer/answer/16679511): see the
[store listing](#google-play-store-listing-drafts-en--es). Privacy policy URL: https://sukrara.mauri.app/privacy (in the
app: Settings › About › *Privacy policy*).

**Health Connect** (App content, after the upload): `WRITE_BLOOD_GLUCOSE` only. Purpose: "Writes the glucose
readings of the person's own sensor to Health Connect, once a minute, so other health apps they choose can use them.
Off until the person turns it on in Share with apps. Sukrara reads nothing from Health Connect." The rationale screen
is the app's `HealthPermissionsRationale` alias of MainActivity.

**Permission declarations that appear after the first upload** (each in English). State on 2026-11-11: full-screen
intent and exact alarms saved; the foreground service not saved (needs the video,
[#185](https://github.com/mauriciabad/sukrara/issues/185)); Health Connect hasn't appeared (neither *Need attention*
nor *Actioned*): watch for it.

| Declaration | Answer |
|---|---|
| Foreground service, type `connectedDevice` | **Not saved.** The form offers *Continuous data transfer to an external device* (video only) or *Other* (video + description); the drafted task *Connected device (Bluetooth)* doesn't exist. Both need the video link (#185). For *Other*, the description: "Sukrara monitors the person's glucose sensor (CGM), or the phone of the person they follow. The foreground service keeps the Bluetooth connection to the sensor (or the connection to the wearer's phone) so each reading and every low or high glucose alarm arrives within a minute while the app is closed. It starts when a sensor is paired or someone is followed, shows the current glucose in its notification the whole time, and stops when the sensor is forgotten or following stops." User impact if deferred or stopped: "Readings stop and low glucose alarms, including the very low alarm, don't ring: the person can miss a dangerous low." |
| Full-screen intent (`USE_FULL_SCREEN_INTENT`) | **Saved 2026-11-11**: core functionality *Alarm clock*, pre-grant at install *Yes*; the form has no text or video field. If Play asks: "Sukrara is a glucose alarm app. The very low glucose alarm, and any alert the person sets to ring like an alarm, is posted with a full-screen intent so that, like an alarm clock, it wakes the screen and shows on the lock screen at once with its alarm sound, because a very low glucose needs immediate action. The person answers it from the lock screen (mute, remind less) or opens the app." (Today the full-screen intent opens the app's main screen, which doesn't show over the lock screen: the phone wakes and the alert shows on the lock screen, but there is no full-screen alarm page; the text says only what happens.) |
| Exact alarms (`USE_EXACT_ALARM`) | **Saved 2026-11-11**: core functionality *Alarm clock*; no text field. [The exact-alarm text](#exact-alarm-declaration) below is what to answer if Play asks |

#### Exact alarm declaration

Where: Play Console › App content › the exact alarm permission declaration (it appears once a bundle with
`USE_EXACT_ALARM` is uploaded). Saved 2026-11-11 as core functionality **Alarm clock**; the form has no text field,
so this text (English) wasn't entered and is only what to answer if Play asks:

> Sukrara is a glucose alarm app for people with diabetes who wear a continuous glucose monitor. Its core, user-facing
> function is alarms: it rings when glucose goes too low or too high, changes fast, or stops arriving from the sensor
> (the missed-reading alarm), repeats the alarm while glucose stays out of range, and alerts the people the user chose
> when a very low alarm goes unanswered. These alarms are scheduled with setExactAndAllowWhileIdle because they must
> ring on time while the phone is idle and the app is in the background: a late low glucose alarm can be dangerous.
> SCHEDULE_EXACT_ALARM is not enough: on Android 14 and later it is denied by default for new installs and can be
> revoked by the user or another app, and the alarms would then fire late without the user noticing. The alarms are
> the first thing the store listing describes, and every alert can be tried in Settings › Alerts › an alert › Send a
> test alert.

Precedent if Play asks: Juggluco, a CGM app on Play, declares `USE_EXACT_ALARM` for the same alarms. If Play rejects it: the
fallback in [Flavors](#flavors) › *If Play rejects …*.

**If Play asks about root, LSPosed or the status bar** (a reviewer question, or *App access* / Device and Network
Abuse): answer truthfully, in English:

> Glucose in the status bar is an optional, advanced feature, off by default. It works only on a phone the user has
> rooted themselves, with the LSPosed framework installed and Sukrara's module enabled for System UI by the user, and
> after the user grants Sukrara root when they turn the feature on. It shows the user's own glucose value as status-bar
> icons or text; it doesn't change, read or interfere with any other app, and it sends nothing anywhere. Without root
> the rest of the app works the same, and Settings › Status bar explains what the feature needs.

Not a form, but a policy to keep in mind: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the alert checklist's *No battery
restrictions*, which opens Android's own dialog). Play allows it where the core function breaks under Doze; an alerting
glucose monitor is that case, and the listing says why ("So that alarms arrive while the phone sleeps, Sukrara asks
to run without battery restrictions").

### Google Play: store listing (drafts, en + es)

For Play Console › Grow users › Store presence › Main store listing (default language **English (United States) –
en-US**) and a translation **Spanish (Spain) – es-ES** (*Manage translations › Add your own translations*). Play's
limits: app name 30 characters, short description 80, full description 4000; no emoji, no "best" / "#1", no
testimonials or calls to action about ratings. The listing leads with the alarms because Play requires the core
function behind `USE_EXACT_ALARM` to be documented and promoted in the description
[[G11]](https://support.google.com/googleplay/android-developer/answer/16558241). It names the status bar feature
briefly as a user-facing feature and never the means (no "root" / "LSPosed"); the declarations above say how it works.
No dosing language (no *Take insulin*): Sukrara isn't a dosing aid.

**State (2026-11-11)**: everything below is filled in for en-US and es-ES (name, short and full description, icon,
feature graphic, the 8 phone screenshots per language in order) and saved as a **draft** only. The Review step asks an
**AI asset declaration** (*Don't label assets* / *Label assets as created or edited using AI*), still unanswered.

| Field | en-US | es-ES |
|---|---|---|
| App name (≤ 30) | `Sukrara: glucose & alerts` (25) | `Sukrara: glucosa y alertas` (26) |
| Short description (≤ 80) | `Live glucose and alarms from your CGM sensor, shared with family. No account.` (77) | `Glucosa en directo y alarmas de tu sensor, compartidas en familia. Sin cuenta.` (78) |
| App icon | `assets/brand/sukrara/android/play-store-512.png` (512 × 512, the Android launcher icon's art) | the same |
| Feature graphic | `~/Pictures/sukrara-play/feature-graphic.png` (1024 × 500, no alpha: the wordmark beside a synthetic glucose card), source [`assets/brand/sukrara/play/feature-graphic.html`](../assets/brand/sukrara/play/README.md) | the same (its only word is the name) |
| Phone screenshots | `~/Pictures/sukrara-play/en/` (1080 × 1920, synthetic data; [Reviewer access](#google-play-reviewer-access-app-access) says how they were made) | `~/Pictures/sukrara-play/es/` |
| Category / tags | Medical (set); tags: *Diabetes*, *Health* (whatever Play offers closest) | the same |
| Contact | `support-googleplay@a.mauri.app`, https://sukrara.mauri.app (set) | the same |

**Full description, en-US** (2823 characters):

```text
Sukrara shows your glucose live on your phone and rings an alarm when it goes too low or too high, changes fast, or stops arriving. It reads a FreeStyle Libre 3 or Libre 3 Plus sensor directly over Bluetooth, or follows someone who wears one and uses Sukrara.

Glucose alarms that ring on time
• Very low, low, high, very high and extremely high alarms, rising or falling fast, and a missed-readings alarm when no glucose arrives, each with your own threshold and sound.
• The very low alarm rings at alarm volume and wakes the screen like an alarm clock, also in Do Not Disturb when you allow it. Alarms repeat while your glucose stays out of range, and a very low you don't answer can alert the people who follow you.
• Mute them when you choose. After you log carbs, low alarms give them time to work; after insulin, high alarms do.
• A checklist makes sure alarms can ring (notifications, Do Not Disturb, battery, Bluetooth), with a test alarm you can send any time.

Your glucose wherever you look
• The value, its trend arrow and a chart you can scroll back 31 days, with time in range.
• Home-screen widgets, an ongoing notification and a Live Update next to the clock, also on the lock screen.
• Glucose in the status bar on supported phones, with advanced setup.

Treatments in seconds
• Log insulin, carbs and notes from the app, the notification, a Quick Settings tile or the lock screen.
• Read the doses from a compatible insulin pen by holding it to the phone.

Share with the people who look after you
• Family and friends see your glucose live on their phones or on a Mac, end-to-end encrypted, over the internet or Bluetooth nearby. You approve every follower.
• Follow someone else and get your own alarms for their glucose.
• No account to create and no server to host.

Your data stays yours
• Everything stays on your phone until you turn on a connection: LibreView, Tidepool, Nightscout, Health Connect, apps such as xDrip+ and AAPS, Garmin watches.
• No ads, no analytics, no tracking. Free and open source (GPL-3.0).

What you need
• Android 16 or newer on a 64-bit phone.
• To read a sensor: a FreeStyle Libre 3 or Libre 3 Plus sensor, NFC and Bluetooth. Support for some Dexcom, Accu-Chek SmartGuide and Aidex sensors is experimental and untested.
• To follow someone: an invite from their phone.
• So that alarms arrive while the phone sleeps, Sukrara asks to run without battery restrictions.

Important
Sukrara is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. Don't make treatment decisions from it: ask your doctor or diabetes care team for medical advice. Sukrara is an early preview.

FreeStyle Libre and LibreView are trademarks of Abbott. Sukrara is not affiliated with or endorsed by Abbott. Other product names are trademarks of their owners.
```

**Full description, es-ES** (3079 characters):

```text
Sukrara muestra tu glucosa en directo en el teléfono y hace sonar una alarma cuando baja o sube demasiado, cambia rápido o deja de llegar. Lee un sensor FreeStyle Libre 3 o Libre 3 Plus directamente por Bluetooth, o sigue a alguien que lo lleva y usa Sukrara.

Alarmas de glucosa que suenan a tiempo
• Alarmas de muy baja, baja, alta, muy alta y extremadamente alta, de subida o bajada rápida, y de lecturas perdidas cuando no llega la glucosa, cada una con tu umbral y tu sonido.
• La alarma de muy baja suena a volumen de alarma y enciende la pantalla como un despertador, también en No molestar si lo permites. Las alarmas se repiten mientras la glucosa siga fuera de rango, y una muy baja sin respuesta puede avisar a quienes te siguen.
• Siléncialas cuando quieras. Tras registrar hidratos, las alarmas de baja les dan tiempo para actuar; tras la insulina, las de alta.
• Una lista comprueba que las alarmas pueden sonar (notificaciones, No molestar, batería, Bluetooth), con una alarma de prueba que puedes enviar cuando quieras.

Tu glucosa allí donde mires
• El valor, su flecha de tendencia y una gráfica que puedes recorrer hasta 31 días atrás, con el tiempo en rango.
• Widgets en la pantalla de inicio, una notificación permanente y una actualización en directo junto al reloj, también en la pantalla de bloqueo.
• La glucosa en la barra de estado en teléfonos compatibles, con configuración avanzada.

Tratamientos en segundos
• Registra insulina, hidratos y notas desde la app, la notificación, un ajuste rápido o la pantalla de bloqueo.
• Lee las dosis de una pluma de insulina compatible acercándola al teléfono.

Comparte con quienes te cuidan
• Familia y amigos ven tu glucosa en directo en su teléfono o en un Mac, cifrada de extremo a extremo, por internet o por Bluetooth cerca. Tú apruebas a cada seguidor.
• Sigue a otra persona y recibe tus propias alarmas de su glucosa.
• Sin cuenta que crear ni servidor que mantener.

Tus datos siguen siendo tuyos
• Todo se queda en tu teléfono hasta que activas una conexión: LibreView, Tidepool, Nightscout, Health Connect, apps como xDrip+ y AAPS, relojes Garmin.
• Sin anuncios, sin analíticas, sin rastreo. Libre y de código abierto (GPL-3.0).

Qué necesitas
• Android 16 o posterior en un teléfono de 64 bits.
• Para leer un sensor: un sensor FreeStyle Libre 3 o Libre 3 Plus, NFC y Bluetooth. La compatibilidad con algunos sensores Dexcom, Accu-Chek SmartGuide y Aidex es experimental y no está probada.
• Para seguir a alguien: una invitación desde su teléfono.
• Para que las alarmas lleguen mientras el teléfono duerme, Sukrara pide funcionar sin restricciones de batería.

Importante
Sukrara no es un producto sanitario y no diagnostica, trata, cura ni previene ninguna enfermedad. No tomes decisiones de tratamiento con él: consulta a tu médico o a tu equipo de diabetes. Sukrara es una versión preliminar.

FreeStyle Libre y LibreView son marcas comerciales de Abbott. Sukrara no está afiliada a Abbott ni cuenta con su respaldo. Los demás nombres de productos son marcas comerciales de sus propietarios.
```

Every claim above is in the app today (docs/FEATURES.md); change the listing in the same change as a feature it names.

### Google Play: what the owner does in Play Console, in order

Prepared on 2026-11-11 (#142); entered the same day by an agent with the owner deciding on screen, except the bundle,
which the owner uploaded by hand. The bundle: `apps/android/app/build/dist/Sukrara-<version>-play.aab` from
`tools/release/play-bundle.sh` (signed with the release key = Play's upload key; mapping inside), built from a commit
on `main`. From the next release on, `tools/release/release.py` uploads it to internal testing through the API
([One-command release](#one-command-release-187)).

Done 2026-11-11: App access (*No, no part of the app is restricted*), Target audience (13–15, 16–17, 18 and over),
Privacy policy, Health apps, the store listing en-US + es-ES (draft), internal testing (release 0.13.0, versionCode
13000, *Available to internal testers*, not reviewed; opt-in link
https://play.google.com/apps/internaltest/4700763397995090600), the full-screen intent and exact alarm declarations.

Left, in order:

1. **Store presence › Main store listing**: answer the **AI asset declaration** in the Review step, then *Save* (the
   listing is a draft until then).
2. **App content › Data safety**: settle the open premise. The definitions, flow by flow, and the recommended *No*
   (with this draft as the fallback) are in
   [Privacy answers for both stores](#privacy-answers-for-both-stores-drafts-2026-11-11). Then *Submit*. It's required
   before any review.
3. **The video** ([#185](https://github.com/mauriciabad/sukrara/issues/185), recorded on the owner's phone), uploaded
   to YouTube as *Unlisted* (check it plays signed out); then **App content › Foreground service permissions**
   (`connectedDevice`): *Continuous data transfer to an external device* or *Other* with
   [the description](#google-play-data-safety-health-apps-and-permission-declarations-drafts), and the link. Save.
4. **Health Connect** declaration, if it appears: [the text above](#google-play-data-safety-health-apps-and-permission-declarations-drafts).
5. **Publishing overview**: *Send changes for review*. Answer any question from Play truthfully (root and LSPosed: the
   text above).
6. Install from the internal-testing link on a **spare** phone or emulator first (on the owner's daily phone it would
   replace the GitHub version: same package and key, the data stays), check Settings › About says *Google Play
   version*, then open testing as the permanent beta.

If Play rejects `USE_EXACT_ALARM` or the root features: [Flavors](#flavors) › *If Play rejects …*, a new bundle, and
the next upload.

## Garmin

The planned own watch app (watch face / data field / app, investigation 08) goes to the **Connect IQ Store**:

- Garmin developer account (18+, accurate registration info), **no fee** for free apps; paid apps go through the
  Merchant Service with an annual fee [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html).
- Upload the `.iq` from *Monkey C: Export Project* with every supported device in the manifest; Garmin reviews it
  before it appears in the store and notifies on approval; it can refuse or remove apps at its discretion [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html)[[C2]](https://developer.garmin.com/connect-iq/submit-an-app/).
- **Privacy policy required** if the app collects data; no keeping user data longer than needed [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html).
- **Medical**: comply with FDA and other laws, and **must not seek regulatory permissions or make determinations that
  could make Garmin's products regulated** [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html) — so the listing must be a display/convenience app with a "not a
  medical device" statement, never "alarm" or "diagnosis" claims. Precedent: CGM watch faces fed by
  xDrip/Juggluco servers are in the store (investigation 08; Kerfstok, CGM+).
- No Garmin marks in the app's marketing without consent [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html). The `Communications` permission (web requests to the
  phone's local server) is shown on the store page.
- Effort: small once the app exists (screenshots for 2–3 device sizes, description, privacy link).

## Web and update channels

- **sukrara.mauri.app as the install hub**: one *Download* page that detects the platform: Play badge, "APK /
  Obtainium / IzzyOnDroid" for power users, notarized dmg, `brew install --cask mauriciabad/tap/sukrara`, Garmin
  store link. Keep the README buttons pointing at the same assets (the `website.yml` link rewriting already exists).
- **Channels**: `stable` (Play production, Sparkle `appcast.xml`, cask) and `beta` (Play closed/open test, Sparkle
  `appcast-beta.xml` via `sparkle:channel`, GitHub pre-releases). The closed test needed for production access
  becomes the permanent beta channel.
- **Privacy page** https://sukrara.mauri.app/privacy (`docs/privacy.md`, HTML, not PDF [[G10]](https://support.google.com/googleplay/android-developer/answer/16679511); since 2026-11-10):
  what stays on the device, what each opt-in feature sends where (Nostr relays: end-to-end encrypted events, public
  keys, timing, IP; LibreView, Tidepool, Nightscout: what each upload carries; Health Connect, apps on the phone, the
  calendar, the password manager, the export), followers, the Mac app, how long things last and how to delete them, no
  analytics / ads / crash data, the contact address. Linked from the site's home page and Settings › About. The same
  page serves Play, Garmin and the Mac app; a feature that sends something new changes it in the same commit.

## Cross-cutting

### Privacy answers for both stores (drafts, 2026-11-11)

These are drafts for the owner's decision. Nothing here is saved in either console. The owner disputes that Sukrara
"collects" anything, so this checks that premise against each store's own definitions, one data flow at a time.
Paths below are under `apps/android/` (A) or `apps/macos/` (M).

**The definitions**

- **Google** [[G14]](https://support.google.com/googleplay/android-developer/answer/10787469):
  - Sending data from the app off the device counts as *collecting* it, whether it goes to the developer or to a third
    party.
  - Not in scope: data processed only on the device, and data that is end-to-end encrypted so that nobody but the
    sender and the recipient can read it.
  - The Data safety FAQ adds a third case: an upload the person makes directly to their own cloud account, governed by
    that provider's terms and privacy policy, which the developer never gets. That needn't be declared either.
  - A transfer the person starts, and expects, isn't *sharing*.
- **Apple** [[A14]](https://developer.apple.com/app-store/app-privacy-details/):
  - *Collecting* is sending data off the device in a way that lets the developer, or its "third-party partners",
    access it beyond serving the request in real time.
  - Third-party partners are the analytics, ad and SDK vendors whose code is in the app.
  - Data processed only on the device isn't collected.

**Android (the Play build)**

| Flow (default) | Where it goes, evidence | Who can read it | Google: collected? |
|---|---|---|---|
| Sharing over relays (on once sharing is set up) | Readings, alerts, treatments and notes, the profile, mirrored settings: NIP-44 to the read key the followers get in a gift wrap (A `core/protocol/…/event/EventFactory.kt:87-95`, `share/…/crypto/SdkNostrCrypto.kt:58-61`). The backup goes to the wearer's own key (`EventFactory.kt:78-81`). Public relays nobody on our side runs (`core/protocol/…/Wire.kt:45`) | Only the wearer and the approved followers. The relays see the envelope: random public keys, IP, time, size, kind, expiry, the app version | **No**: the end-to-end exemption. The envelope is transport metadata on servers the developer doesn't run. A strict reader could call the long-lived public key a *Device or other ID* (fallback below) |
| Bluetooth (on once someone follows) | The same encrypted events to devices nearby. During *Pair nearby* (≤ 10 min), the name, picture, invite code and relays, readable nearby (`share/…/ShareOwnerModule.kt:132`, `share/…/ble/NearbyServerCore.kt:289-301`) | The content: only the followers. The pairing offer: anyone in range, for 10 minutes, after the person opens it | **No**: end-to-end encrypted, device to device. The pairing offer is a transfer the person starts and sees |
| Invite link / QR | The person hands it over. Its secret is in the `#`, never sent to the website | Whoever gets it | **No**: the person's own transfer |
| LibreView sign-in and upload (off) | E-mail and password, readings, treatments, notes, the sensor's number, an install ID, and the name and date of birth the account already has. To Abbott, over TLS, into the person's own account (A `integrations/…/libreview/LibreViewUploader.kt:49-53`, `…/LibreViewClient.kt:180-189`) | Abbott, under its own privacy policy. Never the developer | **Debatable.** The bare definition says yes (off the device, to a third party). The own-account FAQ says no: the person's own account, the provider's terms, and the developer never gets it. Not *shared* either way |
| Tidepool (off; a follower's *Upload the person you follow*, off) | The same kinds of data to `api.tidepool.org` (A `integrations/…/tidepool/TidepoolUploader.kt:49-54`) | Tidepool, under its own privacy policy | As LibreView |
| Nightscout (off) | Readings, treatments, sensor changes, battery, a device name, to the person's own site, https only (A `integrations/…/nightscout/NightscoutUploader.kt:45-53`, `…/NsConfig.kt:62-63`) | The person's own server | **No**: it's literally the person's own storage, the FAQ case |
| Health Connect, calendar, broadcasts, xDrip+ server, Nightscout API (all off) | On the phone (the API binds to the Wi-Fi only with *Also on this Wi-Fi*, off: A `integrations/…/localserver/NightscoutApiServer.kt:447-448`) | Apps the person picks; their own devices | **No**: on the device or the person's own devices |
| Android backup | Off (A `app/src/main/AndroidManifest.xml:28-29`, `data_extraction_rules.xml`). The key backup goes to the person's own password manager | — | **No** |
| Crash reports, analytics, ads | None: no SDK that phones home (A `gradle/libs.versions.toml`). Play's own vitals are Google's, not the app's | — | **No** |
| Updater | Not in the Play build (A `app/build.gradle.kts:36`, `app/src/play/…/Store.kt:25`) | — | — |

**Mac (the App Store version)**

| Flow (default) | Where it goes, evidence | Apple: collected? |
|---|---|---|
| Following over relays | Follow request with name and picture, profile updates, requests for new keys and missed readings: all NIP-59 gift wraps to the wearer (M `Sources/SukraraShare/Follower/FollowerSession.swift:130-160`, `…/Protocol/NostrSDKCrypto.swift:58-72`). The entries added, if allowed: NIP-44 to the read key | **No**: neither the developer nor any partner can read it. The relays aren't the developer's partners: no vendor code, an open protocol |
| Bluetooth nearby | Mac to the wearer's phone | **No** |
| Juggluco on this network (off) | GETs to the person's own phone on the home network, private addresses only. The API secret only as a hash (M `Sources/SukraraShare/Lan/JugglucoApi.swift:32-39`) | **No**: their own devices |
| Updates, analytics, crash reports, iCloud, HealthKit | None. The App Store executable links no updater (M `Package.swift:67`, `Sources/SukraraAppStore/main.swift:8`). The sandbox is network client, Bluetooth and user-selected files (`Bundle/appstore.entitlements.in`). Keychain items are this-device-only | **No** |

**Precedent.** Juggluco (on Play, with LibreView and Nightscout uploads) declares "No data collected" and "No data
shared" [[P4]](https://play.google.com/store/apps/datasafety?id=tk.glucodata&hl=en). GlucoseBar (Mac App Store, Dexcom
Share and Nightscout) shows *Data Not Collected* [[A6]](https://apps.apple.com/us/app/glucosebar/id6468110131?mt=12).

**Recommendation**

- **Apple: Data Not Collected.** This one is clear-cut: there's no partner code, and everything that leaves the Mac is
  end-to-end encrypted or stays on the person's own devices.
- **Google: "No"**, the owner's reading.
  - The relays are covered by the end-to-end exemption.
  - Nightscout is covered by the own-account FAQ.
  - The integrations and the backup stay on the person's own devices or accounts.
  - LibreView and Tidepool are the person's own accounts, under the provider's own privacy policy, and the developer
    never gets them.
- **The two weak points**, if a reviewer reads strictly:
  - LibreView and Tidepool are health services, not the FAQ's "drive or cloud storage".
  - The relays see a long-lived random public key.
- **If Play rejects the Data safety form**, the fallback is the conservative draft in
  [Google Play: Data safety…](#google-play-data-safety-health-apps-and-permission-declarations-drafts), minus Name and
  Other info if preferred. That draft says *Yes*, encrypted in transit, deletion possible, and lists the opt-in uploads
  as collected, optional, for *App functionality*, not shared. Optionally add *Device or other IDs* for the relay key.
  Saying more than needed carries no penalty; saying less than Play thinks is true does.
- **The privacy page must match either answer.** It lists every flow and says the developer receives nothing. It was
  corrected on 2026-11-11 where the code said more:
  - Bluetooth is on by default once someone follows.
  - The GitHub version's daily update check.
  - The backup encrypted to the wearer's own key.
  - What the relays see.
  - LibreView setting the active sensor.
  - The Mac's Juggluco search.

**The exact clicks**

- **Play Console › App content › Data safety** (the saved draft says *Yes*):
  1. *Data collection and security*: **Does your app collect or share any of the required user data types? → No**.
     The data-type pages go away.
  2. Next, Preview, **Submit**.
- **App Store Connect › Sukrara › App Privacy**:
  1. **Get Started → No, we do not collect data from this app**, then Save.
  2. **Publish**. The Privacy Policy URL https://sukrara.mauri.app/privacy is already set for en-US and es-ES.

**Names and trademarks.** Abbott claims "Libre", the butterfly logo, the sensor's look and the colour yellow; use is
allowed only to identify Abbott's products [[T1]](https://play.google.com/store/apps/details?id=com.abbott.adc.freestyle.libre.us). Apple forbids third-party brands in the app name/icon without
approval [[A4]](https://developer.apple.com/app-store/review/guidelines/); Garmin forbids its marks in marketing without consent [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html). So: the name stays **Sukrara**, the icon
never shows a sensor or yellow butterfly, listings say "works with FreeStyle Libre 3 sensors" (nominative use, like
Juggluco's listing [[P1]](https://play.google.com/store/apps/details?id=tk.glucodata)) plus "FreeStyle Libre and LibreView are trademarks of Abbott. Sukrara is not affiliated with
or endorsed by Abbott" (already in the README disclaimer). No "LibreView" in titles or screenshots' headlines.

**GPL-3.0-or-later in stores.** Distributing binaries requires the Corresponding Source. Play, the Mac dmg and the
Connect IQ store all allow a link: each release must tag the exact commit **and** the Juggluco revision compiled into
the native core (today an unpinned local checkout: pin it as a submodule or record the hash in the release notes),
and the app's About page links to the source. GPL's "no further restrictions" conflicts with store terms only for the
Mac App Store (the long-standing App Store/GPL debate). That debate is about distributing *someone else's* GPL code
under the App Store's terms; the Mac binary contains only the owner's own code (every commit under `apps/macos` is
his) plus NostrSDK (MIT) and Apple's frameworks, and a copyright holder isn't bound by his own licence, so he can ship
it there. An outside contribution to the Mac app needs the contributor's permission for the App Store (an additional
permission in that change) before it ships there. Juggluco's own GPLv3 code is compatible (Android only).

**Costs**

| Item | Cost | When |
|---|---|---|
| Apple Developer Program (Developer ID, notarization) | 99 USD / year [[A1]](https://developer.apple.com/programs/) | before the first notarized release |
| Google Play developer account (personal) | 25 USD once [[V2]](https://developer.android.com/developer-verification/guides/faq) | before the closed test |
| Android Developer Console | 0 (not needed: Play Console registers outside-Play apps too) [[V2]](https://developer.android.com/developer-verification/guides/faq)[[V3]](https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console) | — |
| Homebrew tap, IzzyOnDroid, F-Droid, Obtainium | 0 | — |
| Garmin Connect IQ (free app) | 0 [[C1]](https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html) | — |
| D-U-N-S / legal entity | not needed (personal account) | — |
| **Total** | **99 USD/year + 25 USD once** | |

**Effort and timeline**

| Step | Who | Effort | Calendar |
|---|---|---|---|
| Enrol in Apple Developer Program | user | 30 min + approval (hours–days) | week 1 |
| Developer ID cert + notarytool profile, first notarized dmg | user (cert, keychain) + agent | 1 h | week 1 |
| Homebrew tap + cask + auto-bump workflow | agent | 2 h | week 1 |
| Sparkle 2 + appcast on the site + EdDSA key | agent (user keeps the key) | 1 day | week 2 |
| Play developer account + identity verification | user | 1 h + verification (days) | week 1–2 |
| Release keystore, `play`/`full` flavors, rename to `app.mauri.sukrara`, migration screen | agent (user stores the key) | 2–3 days | week 2 |
| Privacy page, listing texts, screenshots (synthetic data), Health/FGS/exact-alarm/data-safety forms, FGS demo video | agent drafts, user submits | 1 day | week 2 |
| Closed test: 12 testers × 14 days, then production access + review | user recruits testers | — | weeks 3–5 |
| IzzyOnDroid request (Fastlane metadata, release-signed APK) | agent | 0.5 day | week 3 |
| F-Droid main repo (reproducible build, submodules) | agent | 2–4 days | later |
| Connect IQ store | user (account) + agent | 0.5 day | when the watch app exists |

## Recommendation

**Mac**: pay for the Apple Developer Program and ship **notarized** dmg/zip with **Sparkle 2** updates from the
website; serve developers with an **own Homebrew tap** from the same release; submit to `homebrew/cask` once it's
notable. **Mac App Store** (2026-11-11, #186): go, as a second, sandboxed build of the same app
([review risk](#mac-app-store-review-risk-2026-11-11-186)), TestFlight first.

**Android**: one release key, package **`app.mauri.sukrara`**, two flavors. **Google Play** (new personal account,
closed test as the permanent beta) for everyone; **GitHub + Obtainium** and **IzzyOnDroid** for the full flavor (with
its own update check; the root features are in both since 2026-11-11); register the package and key for developer verification in the same Play Console account before the
2027 global rollout. F-Droid main repo later, with reproducible builds.

**Garmin**: Connect IQ Store, free, display-only claims.

**Next steps, in order**

1. *(user)* Enrol in the Apple Developer Program (99 USD/year, legal name as seller).
2. *(user)* Create the Developer ID Application certificate and the `sukrara` notarytool profile (NEXT.md §5).
3. *(agent)* First notarized release with the existing scripts; Homebrew tap + cask + auto-bump; README/site
   `brew` instructions.
4. *(agent)* Sparkle 2 in the Mac app, appcast on sukrara.mauri.app. *(user)* keeps the EdDSA private key backup.
5. ~~*(user)* Create the Google Play developer account~~ Done 2026-11-10: the old account was reinstated, the app
   created, our key set for Play App Signing, API access set up ([as set up](#google-play-the-account-and-app-as-set-up-2026-11-10)).
6. *(user)* Generate and back up the release keystore (an agent can script it; the user owns the file and passwords).
7. *(agent)* Rename to `app.mauri.sukrara`, `play`/`full` flavors, `SCHEDULE_EXACT_ALARM` in `play`, migration screen
   in the last `.poc` build, settings export/import, Juggluco core pinned. Done: the rename, settings export / import,
   the pinned core, the flavors and `tools/release/play-bundle.sh` (2026-11-10, [Flavors](#flavors)); the migration
   screen was dropped (the app starts clean). The owner then kept `USE_EXACT_ALARM` and the root features in `play`
   (2026-11-11); `play` only leaves out the updater.
8. *(agent)* Privacy page, store listing (with the not-a-medical-device statement and Abbott disclaimer), synthetic
   screenshots, Fastlane metadata, FGS demo video script; drafts of the Health apps, data safety, FGS and exact-alarm
   answers. *(user)* submits the forms (only the account owner can). Done: the privacy page and the drafts
   ([Data safety, Health apps and permission declarations](#google-play-data-safety-health-apps-and-permission-declarations-drafts)).
   Done 2026-11-11: the store listing (en + es), the exact-alarm, FGS and full-screen-intent texts, the phone
   screenshots and the feature graphic (outside the repo), entered in Play Console the same day except Data safety
   and the FGS declaration ([Play Console, in order](#google-play-what-the-owner-does-in-play-console-in-order)). Open:
   the demo video (the emulator one was rejected; the owner records it, #185), an upload script.
9. ~~Recruit 12 testers, closed test 14 days~~ Not required for the reinstated account; use internal testing for
   the first builds, then open testing as the permanent beta.
10. Our key is the Play App Signing key (done 2026-11-10); developer verification for outside-Play installs follows
    from the Play registration of `app.mauri.sukrara` with that key.
11. *(agent)* IzzyOnDroid inclusion request; Obtainium link on the site.
12. Later: F-Droid main repo; Connect IQ store when the watch app exists *(user creates the Garmin account)*.

**Only the user can**: pay (Apple, Google), verify identity (Apple, Google Payments profile), own the accounts
(Apple ID with 2FA, Play Console, Garmin), create the Developer ID certificate on their Mac, hold the release
keystore and Sparkle EdDSA key, submit policy declarations, recruit testers, and decide on medical-device risk.

## Risks

- **Medical-device classification** (EU MDR rule 3.3 / Play's "software interfacing with medical device hardware")
  could force a "Medical Device Apps" declaration Sukrara can't back with a certification → Play rejection or
  removal. Mitigation: display/convenience framing, the sideload channel as fallback.
- **Abbott**: a trademark or ToS complaint can pull the Play listing (reported for Juggluco [[P2]](https://forum.fudiabetes.org/t/juggluco-back-on-play-store/15038)); the LibreView
  upload uses an unofficial API.
- **`USE_EXACT_ALARM` rejection** if kept in the Play build.
- **Closed-test drop-outs** restart the 14-day clock [[G6]](https://support.google.com/googleplay/android-developer/answer/14151465)[[G7]](https://www.choicely.com/blog/google-play-12-tester-rule).
- **Developer verification** may tighten (the advanced flow is new, and F-Droid's position is unresolved [[V4]](https://techaiwire.com/articles/android-developer-verification-enforcement-four-countries/));
  losing the release key makes the package unregistrable [[V2]](https://developer.android.com/developer-verification/guides/faq) — two backups.
- **Same package on Play and GitHub**: Play may overwrite a root install with the play flavor.
- **Team ID change** on the Mac when moving to the paid team (Keychain prompt, new App Group container).
- **Homebrew tap trust** rules have changed twice in 2026 [[H6]](https://brew.sh/7.0.0-migration-guide/); recheck the cask instructions on each Homebrew major.

## Sources

All seen **2026-10-18**. "3rd-party" = not the platform owner.

- [H1] Homebrew 5.0.0 release notes — https://brew.sh/2025/11/12/homebrew-5.0.0/
- [H2] Homebrew/brew #20755, removing `--no-quarantine` — https://github.com/Homebrew/brew/issues/20755
- [H3] TomatoBar #102, cask disabled 2026-09-02 (3rd-party) — https://github.com/ivoronin/TomatoBar/issues/102
- [H4] Homebrew, Acceptable Casks — https://docs.brew.sh/Acceptable-Casks
- [H5] Example taps: livecheck, auto-bump, postflight quarantine (3rd-party) — https://github.com/nelsonlove/homebrew-tap/pull/3, https://github.com/EricAndrechek/homebrew-tap, https://github.com/HibernalGlow/homebrew-tap
- [H6] Homebrew 7.0.0 migration guide (tap trust) — https://brew.sh/7.0.0-migration-guide/
- [H7] Homebrew/brew #22472, `brew trust` — https://github.com/Homebrew/brew/pull/22472
- [A1] Apple Developer Program — https://developer.apple.com/programs/
- [A2] Apple, Enrollment — https://developer.apple.com/help/account/membership/program-enrollment/
- [A3] Apple, Membership details (Developer ID, notarization) — https://developer.apple.com/programs/whats-included/
- [A4] App Review Guidelines (1.4.1, 2.4.5, 2.5, 4.1, 5.1.3, 5.2) — https://developer.apple.com/app-store/review/guidelines/
- [A5] Bluetooth sandbox entitlement — https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.security.device.bluetooth
- [A6]–[A13] seen 2026-11-11 (3rd-party): GlucoseBar — https://apps.apple.com/us/app/glucosebar/id6468110131?mt=12; Sugar Sense: Menu Bar Glucose — https://apps.apple.com/us/app/sugar-sense-menu-bar-glucose/id6796828695?mt=12; Glucose Graph — https://apps.apple.com/us/app/glucose-graph/id1668560499?mt=12; nightguard — https://apps.apple.com/us/app/nightguard/id1116430352; Sugarmate's removal — https://twitter.com/sugarmateapp/status/1495892882749800450; Shuggah's removal — https://appshunter.io/ios/app/1586789452, https://www.diabetes.co.uk/forum/threads/shuggah-app-vanished.210952/; Spike's certificate — https://medium.com/trit1dtech/devastating-news-for-diy-t1d-community-apple-tries-to-clamp-down-on-distribution-of-spike-app-ef57138b6163
- [A14] Apple, App privacy details on the App Store (seen 2026-11-11) — https://developer.apple.com/app-store/app-privacy-details/
- [S1] Sparkle documentation (2.9) — https://sparkle-project.org/documentation/
- [G1] Play Console Help, Closure of inactive developer accounts — https://support.google.com/googleplay/android-developer/answer/11605267
- [G2] XDA, inactive-account policy (2021, 3rd-party) — https://www.xda-developers.com/google-play-policy-crack-down-on-dormant-accounts/
- [G3] Reinstating / new account after inactivity closure (3rd-party) — https://mrhamo.com/blog/reinstate-google-play-developer-account-closed-inactivity/, https://magiskmodule.gitlab.io/blog/google-play-console-has-old-inactive-account-in-it-how-do-i-create-a-new-developer-account-to-publish-app/
- [G4] Play Console Help, Create and set up your app (package names) — https://support.google.com/googleplay/android-developer/answer/9859152
- [G5] Play Console Help, account types and required information — https://support.google.com/googleplay/android-developer/answer/13628312
- [G6] Play Console Help, App testing requirements for new personal developer accounts — https://support.google.com/googleplay/android-developer/answer/14151465
- [G7] 12-tester rule explained, organization exemption (3rd-party) — https://www.choicely.com/blog/google-play-12-tester-rule, https://ontest.app/blog/google-play-12-testers-14-days-requirement-explained
- [G8] Play Console Help, Target API level requirements — https://support.google.com/googleplay/android-developer/answer/11926878
- [G9] Play Console Help, Health apps declaration form — https://support.google.com/googleplay/android-developer/answer/14738291
- [G10] Play Console Help, Health Content and Services — https://support.google.com/googleplay/android-developer/answer/16679511
- [G11] Play Console Help, Permissions and APIs that access sensitive information (exact alarms) — https://support.google.com/googleplay/android-developer/answer/16558241
- [G12] Play Console Help, Foreground service and full-screen intent requirements — https://support.google.com/googleplay/android-developer/answer/13392821
- [G13] Play policy, Device and Network Abuse — https://support.google.com/googleplay/android-developer/answer/16559646
- [G14] Play Console Help, Data safety section — https://support.google.com/googleplay/android-developer/answer/10787469
- [G15] Play Console Help, Play App Signing — https://support.google.com/googleplay/android-developer/answer/9842756
- [V1] Android developer verification — https://developer.android.com/developer-verification
- [V2] Developer verification FAQ — https://developer.android.com/developer-verification/guides/faq
- [V3] Android Developers Blog, verification rolling out to all developers — https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console
- [V4] Enforcement in four countries; F-Droid 2.0 (3rd-party) — https://techaiwire.com/articles/android-developer-verification-enforcement-four-countries/, https://pinggy.io/blog/f_droid_2_0_android_developer_verification/
- [V5] Register your app on open source platforms — https://developer.android.com/developer-verification/guides/open-source-app-registration
- [R1] MDCG 2019-11 (software qualification/classification, MDR Annex VIII 3.3) — https://health.ec.europa.eu/document/download/b45335c5-1679-4c71-a91c-fc7a4d37f12b_en?filename=mdcg_2019_11_en.pdf
- [R2] 21 CFR 862.1350 — https://www.ecfr.gov/current/title-21/chapter-I/subchapter-H/part-862/subpart-B/section-862.1350
- [R3] FDA product code PJT — https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfpcd/classification.cfm?id=PJT
- [P1] Juggluco on Google Play — https://play.google.com/store/apps/details?id=tk.glucodata
- [P2] FUDiabetes forum, Juggluco back on Play (3rd-party) — https://forum.fudiabetes.org/t/juggluco-back-on-play-store/15038
- [P3] xDrip+ installed from GitHub (3rd-party) — https://forum.fudiabetes.org/t/upgrade-to-libre-3-from-libre-2-using-xdrip-on-android/14774
- [P4] Juggluco's Data safety on Google Play (seen 2026-11-11) — https://play.google.com/store/apps/datasafety?id=tk.glucodata&hl=en
- [F1] Obtainium releases (1.7.0) — https://github.com/ImranR98/Obtainium/releases
- [F2] F-Droid Inclusion Policy — https://f-droid.org/docs/Inclusion_Policy/
- [F3] F-Droid Anti-Features — https://f-droid.org/docs/Anti-Features/
- [F4] IzzyOnDroid App Inclusion Policy / new app inclusions — https://izzyondroid.org/docs/general/AppInclusionPolicy/, https://izzyondroid.org/contributing/NewAppInclusions/
- [F5] Accrescent, The Future of Accrescent — https://accrescent.app/blog/posts/the-future-of-accrescent/
- [F6] Accrescent publishing requirements — https://accrescent.app/docs/guide/appendix/requirements/
- [C1] Garmin Connect IQ Developer Agreement (Exhibit A) — https://developer.garmin.com/downloads/connect-iq/sdks/agreement.html
- [C2] Garmin, Submit an app — https://developer.garmin.com/connect-iq/submit-an-app/
- [T1] Abbott trademark notice (via its Play listings and site, 3rd-party summary) — https://play.google.com/store/apps/details?id=com.abbott.adc.freestyle.libre.us
