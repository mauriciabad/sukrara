# 44 — External sources: glucose from the official apps and online accounts

*2026-11-18 · investigation report for [#213](https://github.com/mauriciabad/sukrara/issues/213) · desk research and
code reading, nothing built or run · third-party code links pinned to the commits in `third_party/` (GlucoDataHandler
`215b2f0`, xDrip+ `8532095`, xDrip4iOS `c268542`, Juggluco `a983788`, AAPS `aa16db6`, Nightscout `92d0834`) ·
"unverified" = no primary source found · `A/` = `apps/android`, `M/` = `apps/macos`*

## Goal

The owner's words (the issue and its three comments), condensed:

- Keep the official CGM app and still get glucose into Sukrara, as live as possible. Preference: **the sensor read
  alongside the official app** › **the official (or a DIY) app on the device** › **online**; for each, a way without
  internet where one exists (as reading CareLink's notification would, #216).
- These sources are another kind (made elsewhere, need internet, bring more than glucose): "glucose source" may be the
  wrong name.
- **A Mac without Sukrara on the phone**: glucose in the menu bar, maybe treatments.
- **No duplicates**: LibreView as the source means no upload back to it, "but the treatments should work"; fit *Bring
  your history back*, changing it if needed.
- **Follower as a source** on both apps: a setting, not a one-way role; setup with two big illustrated choices. "Figure
  out if we do it or pass."
- **The move**: trying Sukrara beside the official app builds trust; two apps are worse and cost battery, so suggest
  moving at some point, not where the person can't (a pump's app).
- Complete journeys with meaningful motion, settings laid out as a whole, priorities from demand. Talk before building.

## Short answer

- **Follower as a source: yes, as two settings**: *Whose glucose* (yours / someone else's) × *Source*, both in Settings ›
  Source, no reinstall. A parent following a child on LibreLinkUp and an adult reading their own use the same source for
  different people, so "follower" can't be a row in one list. The code already switches roles; only the copy says
  "reinstall".
- **Build order**: Nightscout (both apps; the only two-way treatments) → Dexcom Share + LibreLinkUp (both apps) → on
  Android the official app's notification and DIY broadcasts. The owner's preference picks the path per person: the
  picker recommends the most direct one their sensor allows.
- **The sensor alongside the official app** exists only for the Dexcom G7 family (the observer, built, waiting for a
  tester). Libre 3 can't share its one Bluetooth link; whether the Libre app's notification shows the value decides if
  Libre has any offline path (owner test).
- **The Mac gets sources of its own** (Nightscout, LibreLinkUp, Dexcom Share); treatments stay on the Mac (two-way with
  Nightscout).
- **Name**: *Source* (es *Origen*).
- **No duplicates or loops**: only the first Sukrara that gets your glucose shares and uploads it, never back to where it
  came from. LibreView takes one uploader per account: while the official app uploads, Sukrara sends it nothing;
  treatments reach followers, Tidepool and Nightscout until the move.
- **The move is earned**: evidence on the Source page, a suggestion only at a sensor change for a tested family, a
  self-ticking checklist and a way back.
- **Cost**: the Person × Source change is the large part and ships first, with no new data path.

## Today in Sukrara

| Exists | For #213 |
|---|---|
| **Dexcom G7 / ONE+ / Stelo observer**: reads the sensor while the Dexcom app owns it, writes nothing; *Untested* ([23](23-sensors.md)) | already "the sensor alongside the official app" |
| Juggluco as a source (Android loopback, Mac on the network) | its server is Nightscout-shaped, as is xDrip+'s: a Nightscout reader covers both |
| LibreView sign-in via the LibreLinkUp login (`/llu/auth/login`, `version: 4.16.0`, `LibreViewAccount.kt:127,174-175`) | the LibreLinkUp source is a small step; **and** Abbott ending that API would also stop new sign-ins (a new Libre 3's receiver ID) |
| LibreView: one uploading device per account ([41](41-libreview-other-sensors.md)) | Sukrara can't send while the official app uploads without stopping it |
| Import history: ±2 min = same reading, the sensor wins ([FEATURES](FEATURES.md#import-history)) | the model for a source's backfill |
| Copy *"One role per install. Changing it later means reinstalling Sukrara."* | **wrong**: `AppRoles.set` (`SetupPorts.kt:30-47`) already switches, after *Delete the earlier history?* |
| Sources tied to the role by id (`SourceId.of / notOf(role)`) | breaks once LibreLinkUp can be either person's |
| Mac: following always wins over Juggluco (`AppModel.swift:90-91`); `Reading` has no source | becomes one picked source |
| [23](23-sensors.md) discarded follower clouds ("Sukrara's own sharing does this") and vendor broadcasts ("a passenger of another app") | #213 reopens both: those reasons hold when Sukrara reads the sensor, not when the official app holds it |

Code map with lines: Appendix B.

## The sources

Full catalogue with every link: Appendix A.

| Source | Android | Mac | Fresh | Offline | Besides glucose | Weak point |
|---|---|---|---|---|---|---|
| **Dexcom G7 family observer** | ✓ built | — | 5 min + backfill | yes | sensor state | untested |
| Libre 3 / 3 Plus / 2 US alongside | ✗ | ✗ | | | | one Bluetooth link ([Juggluco](https://github.com/j-kaltes/Juggluco/)) |
| **Official app's notification** (Dexcom, Medtronic, Eversense, Aidex, Sibionics, Medtrum, CamAPS FX, Omnipod 5…) | ✓ | — | 5 min | yes | arrow; IOB from loop apps | no time, no backfill, breaks on wording changes |
| Libre app's notification | ? | — | | | | in no reader's list (owner test) |
| **DIY broadcasts** (xDrip+, Juggluco, AAPS) | ✓ | — | live | yes | rate, sensor start | none |
| **Nightscout** (+ Gluroo, xDrip+ / Juggluco servers) | ✓ | ✓ | push or poll | LAN only | **treatments both ways**, IOB, sensor start | none: open API |
| **Dexcom Share** (not Stelo) | ✓ | ✓ | 5 min; 24 h back | no | trend | unofficial; failed logins lock the account |
| **LibreLinkUp** | ✓ | ✓ | 1 min (Libre 3); 12 h back | no | trend, sensor start | unofficial; Abbott moving to an encrypted API |
| Dexcom public API, Health Connect, Tidepool | | | 1–3 h late | | | not live |

Unverified and worth a sensor: Aidex X advertising while its app is connected (offline, and direct on a Mac); a Dexcom
G6 in the receiver slot beside the Dexcom app. Out: Libre 2 EU beside LibreLink (raw values, [02](02-direct-sensor.md)).

**Offline paths per family** (the owner's ask): Libre 3 / 3 Plus / 2 US / Lingo **none known**, so LibreLinkUp or the
move; Libre 2 EU none acceptable; Dexcom G7 family **the observer**, notification as fallback; Dexcom G6 notification
(receiver slot unverified); Medtronic Guardian notification (Simplera's lacks the value); Eversense, Aidex, Sibionics,
Medtrum, Ottai, iCan, CareSens Air notification; any CGM inside a loop app notification (value + IOB); SmartGuide none
reliable. Per family with the online fallback: Appendix A.

**What people ask for** (Reddit is unreachable from the research tools; demand comes from what people build and file):
(1) Libre on a Mac without a phone app: ≥ 10 independent LibreLinkUp menu-bar apps ([LibreBar](https://librebar.app/),
[librelink-for-mac](https://github.com/albertgd/librelink-for-mac)); (2) Dexcom on a Mac: ≥ 8 Share menu-bar apps, two
commercial ([GlucoseBar](https://glucosebar.t1d.tools/) on the Mac App Store); (3) a better viewer beside the official
app on Android: [GlucoDataHandler](https://github.com/pachi81/GlucoDataHandler), with 22 LibreLinkUp, 11 Share and 12
notification issues; (4) the Dexcom app and DIY alarms at once ([xDrip #4191](https://github.com/NightscoutFoundation/xDrip/discussions/4191));
(5) Nightscout as the loopers' hub. Base: Libre > 8 M users, Dexcom ~3.5 M ([GM Insights](https://www.gminsights.com/industry-analysis/continuous-glucose-monitoring-market)).

### Build order (the two notes reconciled)

The design notes guessed LibreLinkUp + Nightscout before demand was known; the catalogue proposed Nightscout, Share,
then LibreLinkUp marked fragile. The owner's preference is about **the path for a person**, and the picker keeps it. What
to **build** first follows three facts: the first preference is already built where a sensor allows it (G7) and
impossible for Libre 3; the Mac, the owner's named case and the top two demand signals, can only use online sources;
and the online machinery (sign-in, health states, backfill, late readings) is reused by every later source, so it's
built first on the API that won't move.

| Step | Source | Where | Why |
|---|---|---|---|
| 0 | Person × Source, no new data path | both | everything hangs on it; testable alone |
| 1 | **Nightscout** | both | open, stable, no ToS risk, pushes, two-way treatments; every DIY setup; the Mac's Juggluco reader becomes a case of it |
| 2 | **Dexcom Share + LibreLinkUp** | both | the two largest bases and the Mac demand; same shape; the LibreLinkUp login exists |
| 3 | **Notification reader + DIY broadcasts in** | Android | the second preference, offline; after the clouds as it's Android-only, has no backfill and breaks silently. **Before step 2 if the Libre app's notification shows the value** |
| — | G7 observer out of *Untested* | Android | a tester, not code |
| 4 | Trust and the move | Android | needs the arrival times steps 1–3 record |
| Later | Medtrum EasyFollow, Aidex X advert, Eversense DMS, Glooko, Tandem, CareLink (#216) | | small base, batch, or unproven |
| Never | Dexcom's API or Health Connect as live; patched vendor apps; lifting LibreLinkUp 5's crypto; borrowed Dexcom certificates | | 1–3 h late, or circumvention ([23](23-sensors.md) rule 2) |

### Risks

- **LibreLinkUp's future**: the 5.0 app (2026-04-21) encrypts its traffic; maintainers won't lift it (EU
  anti-circumvention) and keep 4.x alive "as long as we can" ([#228](https://github.com/timoschlueter/nightscout-librelink-up/discussions/228)).
  In October 2026 the plain API still answered, with a terms step and 1-hour tokens for a 5.0.1 header
  ([nightscout-clock #236](https://github.com/ktomy/nightscout-clock/pull/236)); version bumps already broke clients
  ([nightscout-connect #53](https://github.com/nightscout/nightscout-connect/pull/53)). It can end any day, with no
  workaround Sukrara may use.
- **Stores**: [GlucoDataHandler](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler&hl=en)
  ships LibreLinkUp and Share on Google Play, GlucoseBar ships Share on the Mac App Store; no action against a client
  found.
- **Lockouts**: retries lock the person's own account (Dexcom failed logins; LibreLinkUp 429 "locked",
  [#218](https://github.com/timoschlueter/nightscout-librelink-up/issues/218)): back off, never retry a refused password.
- **Two apps ring**: Sukrara can't silence the official app, whose urgent low (≤ 55 mg/dL) can't be turned off
  ([Dexcom G7](https://www.dexcom.com/en-au/dexcom-g7/how-it-works), [Libre 3 Plus](https://www.adces.org/education/danatech/home/freestyle-libre-3-plus---dexcom-g7-15-day-comparison)).
- **Late readings, battery**: clouds come 1–5 min late, in gaps; notifications carry no time or backfill. No battery
  measurement of two apps plus a poll each minute found.

## Person × Source

| Option | Verdict |
|---|---|
| A. Cloud sources only under *I wear the sensor* | ✗ a parent pretends to wear: the child's lows share and upload as the parent's |
| B. The owner's idea literally: *Sukrara (follower)* is one more source | ✗ whose glucose is about the person, not the transport; LibreLinkUp and Nightscout would each need a "mine or theirs" switch anyway |
| **C. Person × Source**: *Whose glucose* + *Source* (filtered by person) | ✓ each question asked once, both changeable, every branch one owner |
| D. Several sources merged by priority | later, as fallback; step 0's rank rule prepares it |

In C, *I follow someone* becomes *Someone else's · Their Sukrara*, a source like any other. What keys on what (the alert
engine already speaks "own vs mirror", so only its mapping changes):

| Behaviour | Keyed on | Yours | Someone else's |
|---|---|---|---|
| Card words, widget name | person | *Your glucose* | *Ana's glucose* |
| Share with Sukrara followers; uploads | person + first-hand | per the data rules | off (Tidepool opt-in, as today) |
| Pens, sensor reminders, Import history | person | yes | no |
| Alert role | person + source | `WEARER` | `FOLLOWER_MIRROR` from a Sukrara stream with *Settings from: Wearer*, else `FOLLOWER_OWN` |
| *Settings from*, escalation, writer scope | source = a Sukrara stream | — | as today |
| Treatment log owner | first-hand device | this device (two-way with a Nightscout site) | a Sukrara stream: the wearer's phone; else this device |

| Source | Persons | Android | Mac | Treatments | Sensor life |
|---|---|---|---|---|---|
| Sukrara reads your sensor | yours | ✓ | — | this device | full |
| Your sensor beside the official app (G7) | yours | ✓ | — | this device | full |
| The official app on this phone | yours | ✓ | — | this device | manual start |
| Juggluco · xDrip+ · AAPS on this phone | either | ✓ | — | this device | Juggluco: start |
| Juggluco on the network | either | — | ✓ | this device | start |
| LibreLinkUp | either | ✓ | ✓ | this device | start |
| Dexcom Share | either | ✓ | ✓ | this device | manual start |
| Nightscout | either | ✓ | ✓ | two-way with the site | its *Sensor Start* |
| Sukrara on another device | either | ✓ | ✓ | writer scope | relayed |

Each source declares its capabilities (also offline, delay, backfill) in `contracts/sources/sources.json`; UI and rules
read those, never the id. **Who signs in where** (checked): Dexcom Share takes **the sharing account's** login, not a
Dexcom Follow one ("your (or the dependent's) credentials, not the follower's", [pydexcom](https://gagebenne.github.io/pydexcom/pydexcom.html)):
a parent who manages a child's account has it, a partner usually not. For your own LibreLinkUp, invite your own email
from the Libre app: Abbott says the same email works for both apps ([FAQ](https://www.support.freestyle.abbott/hc/en-us/articles/36336824870545-As-a-parent-guardian-I-used-my-own-email-address-to-create-an-account-for-a-minor-to-use-a-FreeStyle-Libre-systems-Libre-app-Can-I-use-my-same-email-address-to-create-a-LibreLinkUp-app-account-so-we-can-connect)).

**Cost**: L for `Role` → `Person` + `SourceSpec` on Android (101 references in 36 files; a test per keying row). M each
for: readings that belong to the current person (a person change deletes, confirmed; a source change keeps) with one
reading per measurement across sources (rank sensor > phone app > network > online > imported, ±2 min as Import); the
Mac's person, `Reading.source` and one store; setup with art. S: the copy fix.

## Naming

**Source** (es *Origen*): one word, the in/out pair with *Share with apps*, its summary says what it brings
(*LibreLinkUp · glucose, sensor life*). Not *Glucose source* (the owner's point: more than glucose), *Data source*
("data" adds nothing), *Connections* (in the Libre app: where it **sends**), *CGM* (a Nightscout site isn't one) or *BG
source* (jargon). The person: *Whose glucose · Yours / Someone else's*; "wearer / follower" leave the UI.

## The journey

Who it's for: the newly diagnosed who don't dare drop the clinic's app (try Sukrara at no risk), an adult at a Mac all
day (the menu bar, no second phone app), a parent on LibreLinkUp or Dexcom (Sukrara's alerts and widgets for **the
child's** glucose, the child changes nothing), a DIY looper on Nightscout (never asked to move). Where Sukrara reads the
sensor nothing changes. Trust grows through reliable alerts and visible information
([PMC7162558](https://pmc.ncbi.nlm.nih.gov/articles/PMC7162558/)) and drops with technical issues
([Sehgal 2024](https://onlinelibrary.wiley.com/doi/10.1111/dme.15168)); AAPS runs an open loop for ≥ 7 + 7 days before
closing it ([Objectives](https://androidaps.readthedocs.io/en/latest/SettingUpAaps/CompletingTheObjectives.html)). The
"alongside" period is Sukrara's open loop: watch, compare, then switch.

**Discovery**: setup; Settings › Source; "Keep your Libre or Dexcom app: Sukrara can read it" on the website, Play and
README; on a phone with no reading yet and an official app installed, home's no-data reason offers *Use the Dexcom app
you already have*. Nothing is advertised where Sukrara reads the sensor.

### Setup

Android: Welcome → **Whose glucose** → **Source** → **Connect** → Permissions → Alerts → *(beside an official app)*
**Two apps, one low** → done → optional steps. Mac: Welcome → *Do you have Sukrara on your phone?* (yes → follow it) →
Whose glucose → Source → Connect → Alerts → Done. One decision per screen, only choices that change the experience
([HIG](https://developer.apple.com/design/human-interface-guidelines/onboarding), [NN/g](https://www.nngroup.com/articles/mobile-app-onboarding/)).

```
┌─────────────────────────────────────────┐   ┌─────────────────────────────────────────┐
│ Whose glucose will Sukrara show?        │   │ Where does your glucose come from?      │
│ ┌─────────────────────────────────────┐ │   │ RECOMMENDED                             │
│ │  (art: an arm with a sensor,        │ │   │ ◉ Sukrara reads your sensor             │
│ │   a phone beside it)                │ │   │   Live every minute · works offline     │
│ │ Yours                           ◉   │ │   │ KEEP THE APP YOU USE NOW                │
│ └─────────────────────────────────────┘ │   │ ○ Dexcom app on this phone      Found   │
│ ┌─────────────────────────────────────┐ │   │   Reads the sensor beside it · offline  │
│ │  (art: two people, one phone        │ │   │ ○ Juggluco, xDrip+ or AAPS              │
│ │   showing the other's glucose)      │ │   │ ONLINE · NEEDS INTERNET                 │
│ │ Someone else's                  ○   │ │   │ [LibreLinkUp][Dexcom Share][Nightscout] │
│ │ A child, partner or friend          │ │   │ FROM ANOTHER SUKRARA                    │
│ └─────────────────────────────────────┘ │   │ ○ Sukrara on your other phone or Mac    │
│ You can change this in Settings › Source│   │                          [ Continue ]   │
│                           [ Continue ]  │   └─────────────────────────────────────────┘
└─────────────────────────────────────────┘    Someone else's → Their Sukrara (invite) ·
                                               LibreLinkUp · Dexcom Share (their Dexcom
                                               login) · Nightscout · Juggluco / xDrip+
```

Radio cards with art drawn like `NewSensorArt.kt` (no digits: else it's a card surface); the selected card lifts and
its ring breathes once. Sources grouped by what the person feels (offline / needs internet / another Sukrara), each
with what it brings and its delay; one a device can't use isn't listed. **Connect** ends on today's *Waiting for the
first reading* → *Reading received*, the value counting in. Motion and haptics per moment: Appendix C.

```
  Two apps can ring for the same low
  The Libre app keeps its alarms. Sukrara's come about a minute later (LibreLinkUp).
  ◉ Both ring                    keep this while you try Sukrara
  ○ Sukrara rings                turn off the Libre app's low and high alarms: [How]
                                 Its urgent low (55 mg/dL) can't be turned off.
  ○ Only the Libre app           Sukrara shows glucose without alerts
```

*Sukrara rings* isn't offered with an online source (later than the official app, needs internet). The design notes'
*Only Sukrara* promised what the official apps don't allow: corrected.

### Living with two apps

- **Late readings**: the card shows their age, the source line the reason (*LibreLinkUp · 2 min*). The alert SPEC fits
  as is: older than 15 min never alerts, `no_data` counts from the reading's time. The trend quiets need ≥ 4 readings in
  5 min, the newest ≤ 2.5 min old ([SPEC](../contracts/alerts/SPEC.md)): a 5-minute source (Dexcom, any path) never
  holds them, LibreLinkUp polled each minute only sometimes. The Source page says so.
- **Three different silences**: *Late: the Libre app hasn't sent anything for 12 min · Open the Libre app* / *No
  internet · Sukrara catches up when you're back* / *LibreLinkUp needs you: accept the new terms*, the usual cause of
  follower outages ([xDrip #2392](https://github.com/NightscoutFoundation/xDrip/discussions/2392)). On home, the Source
  page and the no-data alert; never a silent stop.
- **Treatments logged in the official app never arrive** (no cloud carries them): *Log treatments in Sukrara*, once.
  More situations: Appendix C.

### The move

The Source page's *How it's going* card is the evidence (one new field: the arrival time):

```
  LibreLinkUp · last 7 days
  2,010 of 2,016 readings · 1 min 10 s after the sensor (median)
  Longest gap 9 min (Tue 03:12) · 3 alerts rang
```

| Suggest only if all (`MoveSuggestion`, Android) | Never if any |
|---|---|
| your glucose, from the official app, the sensor beside it, or your own online account | the Mac (no Sukrara on the phone is the point) |
| a family Sukrara **has tested** directly (today Libre 3 / 3 Plus) | a loop or pump app holds the CGM (AAPS, CamAPS FX, Omnipod, Tandem, CareLink) |
| ≥ 7 days at ≥ 95 % coverage | someone else's glucose |
| a sensor moment (last 24 h of one, or a new one warming up), daytime, no alert showing | *Don't suggest again*, or 2 *Not now* |

Home's banner slot (*Show me how* / *Not now*) and always *Move to Sukrara* on the Source page; never a notification:
*Start your next sensor in Sukrara · Live every minute, works offline, one app instead of two.* At a sensor change
because a new Libre 3 starts in Sukrara with the account's receiver ID, and a running one can't be read by two apps
([Juggluco FAQ](https://www.juggluco.nl/Juggluco/faq.html)).

```
  Move to Sukrara                                              Back
  1 ✓ Your history is here since Oct 3 (LibreLinkUp)
  2 ○ Your LibreLinkUp followers see you only while LibreView gets your glucose;
       Sukrara uploads to LibreView once it reads your sensor.       [Invite them to Sukrara]
  3 ○ Stop the Libre app (Force stop) so it lets go of the sensor     [Open app info]
  4 ○ Start the sensor in Sukrara                                     [Set up sensor]
  5 ○ First reading from your sensor → the source switches by itself (confirm haptic)
  6 ○ LibreView: take over the upload                                 [Take over]
  ──────────────────────────────────────────────────────
  Keep the Libre app installed for now. After 3 days without gaps, Sukrara says it's safe to remove.
```

The sign-in stays saved for *Go back to the Libre app* ([Juggluco's way back](https://www.juggluco.nl/Juggluco/faq.html)).
The graph never jumps: the sensor's backfill lands on the online readings, one per measurement.

### *Bring your history back*, source-aware

It moves after Connect and offers first what the source doesn't bring: LibreLinkUp → *Your last 12 h came with
LibreLinkUp* + the LibreView file (90 days); Dexcom Share → *Your last 24 h came with Dexcom* + Tidepool or a file;
Nightscout → one tap, *Bring back 90 days from Nightscout*; a sensor Sukrara reads → as today; a Sukrara stream or
someone else's glucose → nothing. Backfill and imports share the ±2 min rule, so they never double. **Changing whose
glucose** keeps today's *Delete the earlier history?* dialog, then the source picker.

## Data: nothing duplicated, lost or looped

1. **First-hand vs relayed**: the first Sukrara that gets the data is first-hand; from another Sukrara it's relayed. Only
   first-hand devices of **your** glucose share and upload.
2. **Never back to the origin**: LibreLinkUp never to LibreView, Share never to Dexcom, site X's glucose never to X: an
   origin tag on every reading, generalising Juggluco's `device=Sukrara` refusal.
3. **Tidepool: glucose on, unless Tidepool already links that cloud.** Checked: Tidepool links Dexcom (US only, ≥ 1 h
   late, [FAQ](https://support.tidepool.org/hc/en-us/articles/21415013012756-Dexcom-Connection-Frequently-Asked-Questions-FAQs))
   and LibreView (US only, since July 2025, [help](https://support.tidepool.org/hc/en-us/articles/35556633048340)). The
   design notes' "starts off" would cost every non-US person their Tidepool glucose. Sukrara can ask which links an
   account has ([`data_sources`](https://github.com/tidepool-org/platform/blob/master/data/service/api/v1/data_source.go);
   with a user session: unverified). Today's Libre 3 upload doubles the same way in the US.
4. **Own-loop guard** by device id, not "any Sukrara" (a Mac reading the site the phone uploads to is legitimate).
5. **One reading per measurement** (rank rule); **one treatment per id** (`merge.json`), then same kind ±2 min and amount.
6. **LibreView takes one uploader per account**: taking over to send treatments would stop the official app's glucose
   and so the LibreLinkUp source. **Nothing to LibreView while the official app uploads**; it resumes at the move
   ([Juggluco agrees](https://www.juggluco.nl/Jugglucohelp/libreview.html)).
7. **A sensor label only from a sensor Sukrara reads** ([22](22-ecosystem-outputs.md)): `SourceInfo=Libre3` turns on
   AAPS's *SMB always*; app, network and cloud readings go out as `Sukrara`, so no loop trusts internet readings as a
   sensor's. The design notes widened broadcasts without this: corrected.

| Source ↓ · target → | LibreView | Tidepool | Nightscout Y | Health Connect | Sukrara followers | Broadcasts, Garmin |
|---|---|---|---|---|---|---|
| Sukrara reads your sensor | ✓ | ✓ (3) | ✓ | ✓ | ✓ | ✓ labelled |
| Sensor beside / the official app | ✗ (6) | ✓ (3) | ✓ | ✓ | ✓ | ✓ (7) |
| Juggluco / xDrip+ / AAPS on the phone | as today | ✓ | ✓, off if that app uploads itself | ✓ | ✓ | ✓, never back to it |
| LibreLinkUp · Dexcom Share | ✗ (2) | ✓ (3) | ✓ | ✓ | ✓ | ✓ (7) |
| Nightscout site X | ✗ | off by default | X: treatments both ways; Y ✓ | ✓ | ✓ | ✓ (7) |
| Sukrara stream (yours / theirs) | ✗ | ✗ / opt-in | ✗ | ✗ | ✗ | ✓ |
| Imported history | ✗ | ✗ | ✗ | ✗ | last 30 days | ✓ |

Every "off by default" is a switch with its reason. A standalone Mac uploads nothing.

**Treatments** stay in this device's log and leave through Tidepool and Nightscout. With site X they mirror both ways:
X's arrive read-only (*From Nightscout*), Sukrara's go out as `sukrara-tr-<id>` and are recognised on the way back,
deletes arrive through the API's history ([`isValid: false`](https://github.com/nightscout/cgm-remote-monitor/blob/92d0834219aa771b5837dbcbf1baeb839a200cf6/lib/api3/doc/tutorial.md)).
A phone and a Mac both on LibreLinkUp would keep two logs that never meet, so the Mac asks *Do you have Sukrara on your
phone?* first. **Sensor life** comes from the source, else a **manual start** on the Sensor page so reminders work.

## Settings

Builds on [35](35-settings-organisation.md): **Source** owns the person, the source and its account; **Sensor** the
physical sensor; **Share with apps** the outputs.

```
Settings (Android)
├─ Alerts · Alert reliability
├─ Source ─────────── "LibreLinkUp · glucose, sensor life · 1 min"    ← new: replaces Sensor's reading
│   ├─ Whose glucose: Yours / Someone else's (Ana)                       source and the follower's
│   ├─ [status card] Live · last reading 1 min ago · How it's going      Following page
│   ├─ <the source's rows>  LibreLinkUp: Account, Person, Region
│   │                       Nightscout: Site, Token, Treatments both ways
│   │                       Sukrara: today's Following page, Settings from, Bluetooth
│   │                       Official app: Notification access, the app found
│   ├─ Two apps, one low · Move to Sukrara (each only where it applies)
│   ├─ Change source → the setup picker, in place (Back = unchanged)
│   └─ See also: Sensor · Import history
├─ Glucose · Graph · Metrics · Status bar & notification · Lock screen
├─ Sensor (yours): current sensor (from the source or a manual start), Pair new sensor, Sensor end
└─ Insulin pens · Sharing · Share with apps · Backup · Import and export · Advanced · About
```

A section that doesn't apply (Sharing for someone else's glucose) shows one row with the reason. Mac panes: `General ·
Source · Alerts · Glucose · Graph · Rings`, Juggluco one of the sources. Schema rows, states and exits: Appendix C.

## Plan

| Step | Android | Mac | contracts |
|---|---|---|---|
| 0. Person × Source | `Person` + `SourceSpec`, re-keying, the rank rule, Source page, setup's two choices, the copy fix; Bluetooth readings tagged `nearby` (today always `relay`, `FollowerEngine.kt:934`) | person, Source pane, `Reading.source`, one store | `sources.json`, `dedup.json`, schema |
| 1. Nightscout | `:sources` module: client, health states, origin tags, the data rules, two-way treatments | same | fixtures for both apps, `health.json`, late-cadence alert scenarios |
| 2. Dexcom Share + LibreLinkUp | clients, sign-ins, manual sensor start, source-aware history step | same | fixtures |
| 3. Alongside | notification reader, DIY broadcasts in, *Two apps, one low* | — | synthetic notification fixtures |
| 4. Trust and the move | arrival times, *How it's going*, `MoveSuggestion`, Move page | *How it's going* | `move.json` |

Each step ships alone; tests only against fakes; a new parity group `sources`; FEATURES.md, README, the Features page
and the privacy policy (new hosts) in the same change. Deferred: several sources at once with automatic fallback
(doubles every state), Tidepool as a live source (≥ 1 h late), uploads from a standalone Mac (doubles the matrix),
re-sharing someone else's glucose (decision 9), a Clarity file import.

## Checked against the code and the web

| The notes said | Found |
|---|---|
| Changing the role means reinstalling | **no**: `AppRoles.set` switches after a confirmation |
| ≈ 45 role branches | 101 references in 36 files: still L |
| Online sources alert "without the quiets" | partly: never at a 5-minute cadence, sometimes with LibreLinkUp each minute |
| Tidepool may get glucose from Dexcom / LibreView | **yes, US only**: rule 3's default flips to on |
| Someone else's Dexcom via *Dexcom Follow* | **no**: Share takes the sharing account's login |
| "LibreLinkUp 5.0 encrypts the glucose payload" | the 5.0 **app's** traffic is; the plain API still answered in October 2026; #228 is a discussion |
| Nightscout deletions unknown | API v3's history reports them |
| *Only Sukrara* rings | impossible: the official urgent low can't be turned off |
| Clouds sending local times | none: LibreLinkUp's `FactoryTimestamp` is UTC ([GDH](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt#L454-L456)); Share and Nightscout use epoch times |
| Store review risk | GlucoDataHandler (Play) and GlucoseBar (Mac App Store) ship these sources |
| Broadcasts from every first-hand source | only unlabelled (rule 7) |
| Sugar Sense, a LibreLinkUp Mac app | reads its own cloud ([App Store](https://apps.apple.com/us/app/sugar-sense-menu-bar-glucose/id6796828695?mt=12)) |

## Gaps (asked, not answered by either note)

- **Treatments to LibreView while the official app uploads**: impossible without stopping its glucose (rule 6); "the
  treatments should work" holds everywhere else: decision 4.
- **The Libre app's notification**: unverified; decides Libre's only offline path and the order of steps 2 and 3.
- **Battery** of two apps plus a poll each minute: no numbers; measure in step 2.
- **Sukrara's own LibreView sign-in** rides on the LibreLinkUp 4.x login: when Abbott ends it, new sign-ins (a new Libre
  3's receiver ID) stop too. Outside #213; worth its own issue.
- **Real people's words**: Reddit unreachable; demand from GitHub, forums and what people build.

## In Sukrara

Nothing is brought in yet: the design waits for the owner's decisions below. The round that builds it fills this
section with what was added and what was discarded, with the reasons.

## Decisions for the owner

**Owner, 2026-11-18: all as recommended.** Two are still open: 9 waits on a clearer explanation, and 11 is being
researched further ("we didn't truly research this enough"). The owner uses only Sukrara with Libre 3 sensors (no Libre
app, no LibreLinkUp). For testing they're fine installing apps on the phone or emulators and making accounts, as long as
their normal setup ends up Sukrara alone.

Ordered by how much they block.

1. **Person × Source** (*Whose glucose* + *Source*, both changeable) instead of "follower is one more source"?
   *Recommend yes*: the only model where a parent on LibreLinkUp and you on LibreLinkUp behave right.
2. **Build order** Nightscout → Dexcom Share + LibreLinkUp → Android notification reader + DIY broadcasts? *Recommend
   yes*: the Mac can only use online sources and Nightscout is the safe base; swap the last two if the Libre app's
   notification shows the value.
3. **LibreLinkUp although Abbott is ending its plain API**, in the Play and Mac App Store builds too? *Recommend yes*:
   the biggest demand, the login exists; its description says it may stop and its status says when it does.
4. **Nothing to LibreView** (treatments neither) while the official app uploads, until the move? *Recommend accept*:
   taking over would stop the official app's glucose and so the source; treatments reach followers, Tidepool, Nightscout.
5. **Name *Source*** (es *Origen*)? *Recommend yes*: one word, the in/out pair with *Share with apps*.
6. **Online sources alert**, late as they are, with *Both ring* as the default and no *Sukrara rings* for them?
   *Recommend yes*: never fewer alarms than the official app alone.
7. **Mac standalone**: online sources, treatments on the Mac (two-way with Nightscout), no uploads, *Do you have Sukrara
   on your phone?* first? *Recommend yes*: the menu-bar case without a second treatment log.
8. **Move suggestion** only at a sensor change, tested families, ≥ 7 days at ≥ 95 %, twice at most, never on the Mac,
   for a loop or someone else's? *Recommend yes*; say if you want it sooner or never.
9. **Re-sharing someone else's glucose** to Sukrara followers (grandparents via a parent)? *Recommend not now*: nobody
   asked; consent and loops.
10. **Manual sensor start** for sources without sensor life? *Recommend yes*: small, and reminders work for every source.
11. **Dexcom / Abbott partner APIs**? *Recommend not planned*: real time needs a commercial partnership, Dexcom's public
    API is 1–3 h late; an email costs nothing.

**What you could test for us**

- **The Libre app's notification**: does it show the number (and arrow)? Tell us, or send a crop without values.
  Decides decision 2.
- **A LibreLinkUp connection to yourself** (invite your own email from the Libre app), to check the client read-only
  against a real account.
- **Someone with Dexcom** (Share on, one follower), and a **G7 wearer** to take the observer out of *Untested*.
- **A Nightscout site fed by a loop**; our own test site with synthetic data covers the rest.
- **Medtronic, Eversense or Medtrum users**, for their apps' notification wording.
- **Buy an Aidex X / LinX?** It shows whether the sensor keeps advertising beside the LinX app (offline, and direct on a
  Mac). A G6 for the receiver slot isn't worth it (platform ending).
- **Battery**: a day with the Libre app + Sukrara on LibreLinkUp vs Sukrara alone, once step 2 exists.

## Appendix A: catalogue

Fresh = from the sensor reading to Sukrara. Net = needs internet. ✚ = besides glucose.

### Channel 1: the sensor itself, alongside the official app

| Source | Platf. | Fresh | Net | ✚ | How / limits | Stability, legal | Open impl. |
|---|---|---|---|---|---|---|---|
| **Dexcom G7 / G7 15 Day / ONE+ / Stelo, observer** | Android (the phone running the Dexcom app) | live, 5 min + passive backfill | no | trend (computed), sensor start / state | the sensor accepts several clients and tells all of them the official app's auth state; the observer waits for it ([G7SensorKit](https://github.com/LoopKit/G7SensorKit) `G7Sensor.swift:323-339`, [23](23-sensors.md)). xDrip+ does the same as "Companion Bluetooth" ([pref_advanced_settings.xml#L1376-L1378](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/res/xml/pref_advanced_settings.xml#L1376-L1378), `cgm/dex/BlueTails.java`); a user found it didn't disturb the Dexcom app ([xDrip #4191](https://github.com/NightscoutFoundation/xDrip/discussions/4191)) | no vendor secret, no writes; **exists in Sukrara** (untested). Not on a Mac: the bond is the phone's | Sukrara `G7Driver.kt`; G7SensorKit; xDrip+ BlueTails |
| **Dexcom G6 / ONE, receiver slot** | Android | live 5 min | no | trend, session | the transmitter has a phone slot and a receiver / pump slot; auth request end byte `0x02` std vs `0x01` alt ([AuthRequestTxMessage.java#L18-L19](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/java/com/eveningoutpost/dexdrip/g5model/AuthRequestTxMessage.java#L18-L19); xDrip+ uses alt for watch / WholeHouse, [Ob1G5StateMachine.java#L2159-L2161](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/java/com/eveningoutpost/dexdrip/g5model/Ob1G5StateMachine.java#L2159-L2161)). Two phone apps on the std slot fail ([AAPS G6](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/DexcomG6.html)) | **unverified next to the Dexcom app**; collides with a receiver or t:slim; G6 is being wound down | xDrip+ |
| Aidex X / LinX / GlucoRx, advertisement | Android **and Mac** (passive scan) | live, per advert | no | — | readings travel in the advertisement, decoded with the box serial (Sukrara `aidex/AidexX.kt`, [23](23-sensors.md)) | **unverified whether it keeps advertising while the vendor app is connected** | Juggluco `cpp/aidexx/` |
| Libre 3 / 3 Plus / Libre 2 US / Lingo | — | | | | one link; the sensor stops advertising once connected ([01](01-connectivity.md)); "Libre 3 sensors and US Libre 2 sensors can't be used in parallel" ([Juggluco](https://github.com/j-kaltes/Juggluco/)) | **not possible** | — |
| Libre 2 / 2 Plus EU | — | | | | LibreLink can ride on Juggluco's link ([Juggluco](https://github.com/j-kaltes/Juggluco/)), but values are raw ([02](02-direct-sensor.md)) | out | Juggluco, xDrip+ |
| Accu-Chek SmartGuide | — | | | | with two apps "one of them will receive the values the other asked for" ([Juggluco changes](https://www.juggluco.nl/Juggluco/changes.html), [FAQ](https://www.juggluco.nl/Juggluco/faq.html)) | not reliable | — |
| Eversense, Sibionics, CareSens Air, Medtrum, Ottai, Syai, Sinocare | — | | | | no open parallel implementation ([23](23-sensors.md)) | out | — |

### Channel 2: the official (or another) app on the same device

| Source | Platf. | Fresh | Net | ✚ | How / limits | Stability, legal | Open impl. |
|---|---|---|---|---|---|---|---|
| **Notification reading**: Dexcom G6 / G7 / ONE / ONE+ / Stelo, Medtronic Guardian / MiniMed / Simplera, Eversense (E3, 365), Aidex / LinX, Ottai, Sibionics (CN `com.sisensing.eco`), Sinocare iCan, CareSens Air, Medtrum, CamAPS FX, Omnipod 5, Diabeloop, Gluroo, Signos | Android | as posted (G7, Eversense 5 min) | no | value + often arrow; IOB from CamAPS / MiniMed / Omnipod 5; no reliable time, **no backfill** | `NotificationListenerService` (special access). Package lists: [UiBasedCollector.java#L75-L147](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java#L75-L147), [NotificationReceiver.kt#L259-L293](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/receiver/NotificationReceiver.kt#L259-L293), [PackageUtils.kt#L204-L226](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/utils/PackageUtils.kt#L204-L226). Needs the value in the notification (Simplera's lacks it: [xDrip #3025](https://github.com/NightscoutFoundation/xDrip/discussions/3025)); parsing fragile ([xDrip #4802](https://github.com/NightscoutFoundation/xDrip/pull/4802)); xDrip+ times readings by a 4 min 30 s sync | breaks silently on any vendor wording change; AAPS: its own reader is trusted but "no trend arrow or backfill" ([AAPS G7](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/DexcomG7.html)). Low legal risk (reads what the person sees) | xDrip+, GDH, AAPS, ESEL |
| Libre 3 app / LibreLink / Libre by Abbott notification | Android | ? | no | ? | **unverified**: no Libre package in xDrip+'s or GDH's lists | — | — |
| Vendor broadcasts: Aidex `com.microtechmd.cgms.aidex.action.BgEstimate`, Diabox, Eversense via ESEL | Android | live | no | trend, sometimes sensor start | [AAPS AndroidManifest.xml#L76-L100](https://github.com/nightscout/AndroidAPS/blob/aa16db64196e570b15bf41e85422fc159c058308/app/src/main/AndroidManifest.xml#L76-L100); GDH `AidexBroadcastReceiver.kt`, `DiaboxReceiver.kt` | depends on a vendor setting | AAPS, GDH |
| Patched vendor apps (BYODA, patched LibreLink, Ottai, Syai, Sibionics, Sinocare) | Android | live | no | — | AAPS receivers above; BYODA "very old… cannot be updated" ([AAPS G7](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/DexcomG7.html)) | modified medical apps: **never** recommended | AAPS, xDrip+ |
| **DIY broadcasts**: xDrip+ `BgEstimate`, Juggluco `glucodata.Minute`, AAPS, NS_EMULATOR | Android | live (1 min Juggluco) | no | rate, sensor start, `SourceInfo` | payloads in [22](22-ecosystem-outputs.md) §1-2 | stable for years | GDH `XDripBroadcastReceiver.kt`, `GlucoseDataReceiver.kt`, `AAPSReceiver.kt` |
| **DIY local servers**: Juggluco `:17580`, xDrip+ web service `:17580`, AAPS / xDrip+ Nightscout emulation | Android (loopback), Mac (LAN) | live (Juggluco `/x/live`) | LAN | readings, sensor start, treatments (Nightscout shape) | **exists** for Juggluco on both apps | stable | Sukrara, GDH |
| Health Connect (read) | Android | **Dexcom 3 h late** ([Dexcom](https://www.dexcom.com/en-ca/faqs/g7/how-do-i-share-my-glucose-data-with-the-apple-health-app)); Libre apps don't write it ([user report](https://eu.community.samsung.com/t5/galaxy-s25-series/samsung-health-amp-glucose-monitors/td-p/11768760), unverified) | no | glucose | background-read permission | useless live | — |
| HealthKit on the Mac | Mac | | | | macOS 13+: "your app can't read or write HealthKit data" ([Apple](https://developer.apple.com/documentation/healthkit/hkhealthstore/ishealthdataavailable())); a macOS 26 project claims otherwise ([Vitalink](https://github.com/RyanLisse/Vitalink), unverified) | — | — |

### Channel 3: online

| Source | Platf. | Fresh | Net | ✚ | Auth | Stability, ToS | Open impl. |
|---|---|---|---|---|---|---|---|
| **LibreLinkUp** (all Libre via LibreLink / Libre 3 app / Libre by Abbott) | both | current value in `connections`; LibreLink uploads each minute for Libre 3 ([04](04-libreview.md)); clients poll 60 s ([LibreLinkUpFollowManager.swift#L692](https://github.com/JohanDegraeve/xdripswift/blob/c268542e64626c564e626658fa9a6b90a059ada4/xDrip/Managers/LibreLinkUp/LibreLinkUpFollowManager.swift#L692)) or 5 min (nightscout-connect) | yes | trend, ~12 h graph, sensor serial + start ([LibreLinkUpModels.swift#L227-L262](https://github.com/JohanDegraeve/xdripswift/blob/c268542e64626c564e626658fa9a6b90a059ada4/xDrip/Managers/LibreLinkUp/LibreLinkUpModels.swift#L227-L262)), ~2 weeks of alarms. **No treatments** | a LibreLinkUp account invited by the wearer (own email works: [Abbott](https://www.support.freestyle.abbott/hc/en-us/articles/36336824870545-As-a-parent-guardian-I-used-my-own-email-address-to-create-an-account-for-a-minor-to-use-a-FreeStyle-Libre-systems-Libre-app-Can-I-use-my-same-email-address-to-create-a-LibreLinkUp-app-account-so-we-can-connect)); region redirect; terms step (status 4) | headers `product` / `version` / `Account-Id`: 403 below 4.16.0 since 2025-10-08 ([nightscout-connect #53](https://github.com/nightscout/nightscout-connect/pull/53)); GDH sends 4.17.0 ([LibreLinkSourceTask.kt#L52-L88](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt#L52-L88)); 429 lockouts ([nightscout-connect #73](https://github.com/nightscout/nightscout-connect/pull/73), [#218](https://github.com/timoschlueter/nightscout-librelink-up/issues/218)). The 5.0 app (2026-04-21) encrypts its traffic; the plain API still answered in October 2026 ([#228](https://github.com/timoschlueter/nightscout-librelink-up/discussions/228), [nightscout-clock #236](https://github.com/ktomy/nightscout-clock/pull/236)) | GDH, xDrip4iOS, nightscout-connect, nightscout-librelink-up, Juggluco ([LibreReceiverApi.java#L41](https://github.com/j-kaltes/Juggluco/blob/a983788a17a35f8e76d9da5c125996b648b4f27a/Common/src/mobile/java/tk/glucodata/LibreReceiverApi.java#L41)), ≥ 10 Mac apps |
| LibreView (patient site / app API) | both | not live: summaries; CSV behind a captcha ([04](04-libreview.md)) | yes | — | LibreView login | — | YtoTech/libreview-data-export |
| **Dexcom Share** (G4–G7, G7 15 Day, ONE+?; **not Stelo**) | both | 5 min, last 24 h, 288 readings ([pydexcom](https://gagebenne.github.io/pydexcom/pydexcom.html)) | yes | trend; **no** treatments, sensor start or alarms | the **sharing account's** login, Share on with ≥ 1 follower; regions us / ous / jp; the Dexcom app's id as `applicationId` ([DexcomShareSourceTask.kt#L37-L43](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/DexcomShareSourceTask.kt#L37-L43), [ShareConstants.java#L5-L6](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/java/com/eveningoutpost/dexdrip/cgm/sharefollow/ShareConstants.java#L5-L6)) | unofficial; a 2021 server change cut Sugarmate's legacy channel ([Sugarmate FAQ](https://www.loopandlearn.org/wp-content/uploads/2021/11/FAQs-Sugarmate-11.2.21.pdf)); trend moved from a number to text; failed logins lock the account; Stelo unsupported ([HA #128065](https://github.com/home-assistant/core/issues/128065)) | pydexcom, GDH, xDrip+ SHFollow, xDrip4iOS, LoopFollow, nightscout-connect, ≥ 8 Mac apps |
| Dexcom public API (v3) | both | **1 h late in the US, 3 h elsewhere** ([Mercury](https://mercurydevelopment.com/what-we-do/all-services/articles/dexcom-cgm-api-integration-guide-2026/), [Momentum](https://www.themomentum.ai/blog/dexcom-api-integration-developer-guide); Tidepool's FAQ confirms the hour) | yes | readings, events (carbs / insulin entered in the app), calibrations | OAuth2; sandbox / 5-user limited / partner | official; real time only for partners ([Sugarmate](https://x.com/sugarmateapp/status/1469163420855726080)) | Dexcom-API-Wrapper |
| **Nightscout** (self-hosted, T1Pal, Nightscout Pro; fed by xDrip+, AAPS, Loop, Trio, Juggluco, nightscout-connect…) and **Gluroo Global Connect** | both | push via socket.io `dataUpdate` ([websocket.js#L150](https://github.com/nightscout/cgm-remote-monitor/blob/92d0834219aa771b5837dbcbf1baeb839a200cf6/lib/server/websocket.js#L150)) or poll; as fresh as its uploader | yes (or LAN) | **treatments read and write** (deletions in API v3 history), devicestatus (IOB / COB, loop), profile, sensor start | URL + token / API secret | open, stable; no legal risk | GDH `NightscoutSourceTask.kt#L31-L33`, xDrip+ NSFollow, Nightguard, LoopFollow, xDrip4iOS; [Gluroo](https://gluroo.com/blog/glucrew/blood-sugar-readings-smartwatch-gluroo/) |
| Tidepool | both | not live: Dexcom ≥ 1 h (US), Libre cloud to cloud (US, cadence undocumented) | yes | treatments, pump data | Tidepool account | official | **exists** (Import history, upload) |
| Medtrum EasyFollow / EasyView | both | ~2 min ([Medtrum FAQ](https://www.medtrum.com/supports/question)) | yes | pump data | a **separate follower account** following one person | unofficial | [MedtrumSourceTask.kt#L46-L51](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/MedtrumSourceTask.kt#L46-L51), xDrip4iOS, nightscout-clock |
| Eversense DMS / Now | both | backfill only (ESEL) | yes | — | Eversense login | unofficial; no open live client | ESEL ([AAPS Eversense](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/Eversense.html)) |
| Sibionics (SiBCompanion) | both | live-ish | yes | — | follower account | **no open implementation** ([GDH #353](https://github.com/pachi81/GlucoDataHandler/issues/353)) | — |
| Glooko / diasend | both | batch | yes | pump, Omnipod 5, CamAPS FX | Glooko login | "Experimental"; diasend "obsolete" ([nightscout-connect](https://github.com/nightscout/nightscout-connect)) | nightscout-connect |
| CareLink | both | live-ish | yes | pump, IOB | | **#216, out for now** | xDrip+, xDrip4iOS, nightscout-connect, `third_party/carelink-bridge` |
| Tandem Source | both | batch | yes | pump | | unofficial | `third_party/tconnectsync` |
| Abbott partner API | — | | | | partnership only ([Sequenex](https://sequenex.com/cgm-data-integration/), details unverified) | not for a GPL DIY app (assumed) | — |

### Offline paths per CGM family

| Family | Best offline path while keeping the official app | Else |
|---|---|---|
| Libre 3 / 3 Plus / Libre 2 US / Lingo | **none known** (one link, no Libre app in the notification readers' lists, no broadcast, no Health Connect) | LibreLinkUp; or move to Sukrara (Libre 3) |
| Libre 2 / 2 Plus EU | none acceptable (raw values) | LibreLinkUp |
| Dexcom G7 / G7 15 Day / ONE+ / Stelo | **sensor observer (exists)**; notification as fallback | Share (not Stelo) |
| Dexcom G6 / ONE | receiver slot (unverified with the app); notification | Share |
| Medtronic Guardian / Simplera | notification (MiniMed / Guardian yes; Simplera's lacks the value) | CareLink (#216) |
| Eversense E3 / 365 | notification (5 min) | DMS backfill (ESEL) |
| Aidex X / LinX | advertisement (if it continues while connected) or the app's broadcast; notification | — |
| Accu-Chek SmartGuide | none reliable (two apps steal each other's values); not in notification lists | Accu-Chek Care has "no direct interface to third-party software" ([Roche](https://www.accu-chek.co.uk/support/smartguide-cgm), unverified wording) |
| Sibionics GS1 / GS3 | notification (CN app listed; international unverified) | no open cloud client |
| Medtrum Nano | notification | EasyFollow |
| Ottai, Syai, Sinocare iCan, CareSens Air | notification | none open |
| Any CGM inside a loop app (CamAPS FX, Omnipod 5, Diabeloop) | notification (value + IOB) | Glooko |

Mac menu-bar apps (demand 1–2). LibreLinkUp: [LibreBar](https://librebar.app/) ([blog](https://bruceelgort.com/2026/07/19/i-put-my-blood-sugar-in-my-macs-menu-bar/)),
[libre-desktop](https://github.com/sshivaji/libre-desktop), [librelink-for-mac](https://github.com/albertgd/librelink-for-mac),
[GlucOS](https://github.com/DMayoBiologic/GlucOS), [LibreLibreLink](https://github.com/MatiasColeur02/LibreLibreLink),
[libre-monitor](https://github.com/bsalgueiro/libre-monitor), [GlucoBar](https://github.com/giantvillain/GlucoBar),
[glucose-bar](https://github.com/dogfootruler-kr/glucose-bar), [glucose-on-display](https://github.com/gustavosfq/glucose-on-display),
[jackfaz/glucobar](https://github.com/jackfaz/glucobar). Dexcom Share: [GlucoseBar](https://glucosebar.t1d.tools/),
[RangeTrace](https://setapp.com/apps/rangetrace), [Tidebar](https://github.com/JustinFay01/Tidebar),
[sugarcane](https://github.com/yuvalezrati/sugarcane), [DexcomMenuBar](https://github.com/doodaddude/DexcomMenuBar),
[dexbar](https://github.com/SucculentGoose/dexbar), [dex-menu-app](https://github.com/Brandon-Burkett/dex-menu-app),
Sugarmate Glance. Also asked: Dexcom app + DIY alarms ([FUDiabetes](https://forum.fudiabetes.org/t/how-to-get-xdrip-and-g6-dexcom-apps-to-play-nice/6413));
long-tail counts (GitHub keyword hits, 2026-11-18): Sibionics 24 (Juggluco), Eversense 29, CareLink 40, Medtrum 11
(xDrip+). Market: [Future Market Insights](https://www.futuremarketinsights.com/competitive-analysis/continuous-glucose-monitoring-systems-market).

## Appendix B: code map (today)

### Android

| What | Where | Note |
|---|---|---|
| Role stored | `A/app/…/RoleRecord.kt:15,25,44` (`role.json` + backup, device-protected `DeviceProtected.kt:39`) | null = setup not done |
| Role chosen | `A/feature/sharing/…/setup/SetupScreens.kt:395-439`, `SetupViewModel.kt:326,350`, `SetupMachine.kt:167-192,278,358`; copy `strings.xml:54` | |
| Role switched | `A/app/…/SetupPorts.kt:30-47` `AppRoles.loss / set`; *Stop following* `AppFollowerSharing.kt:84-89`; start-up clean-up `AppGraph.kt:1020` | already reversible |
| Sources per role | `A/core/model/…/Reading.kt:79-136`; `DataModule.kt:187` `readingsFor(role)` | static |
| Hub / service | `AppGraph.kt:1060-1082` `readingSource()`, `MonitoringService.kt:151-162`, `GlucoseHubImpl.kt:99-145,185-194,262` (one source at a time) | `ReadingSource` (`ReadingSource.kt:14`) is the seam |
| Locked boot | `AppGraph.kt:980-983`, `LockedBootPlan.kt:15-24` | own sensor only |
| Alerts | `Episodes.kt:120` `AlertRole`; `AlertRuntime.kt:251,373,735,801-807`; `AlertMachine.kt:98-196,288` | keep; change the mapping |
| Sharing | `WearerGate.kt:15`, `OwnerEngine.kt:355`, `AppGraph.kt:198-209,448-457,1043`, `AppFeatureChecks.kt:50-109`, `AppAdvanced.kt:46-93`, `SukraraNavigation.kt:486-560` | owner engine = wearer only |
| Uploads | LibreView `LibreViewUploader.kt:237,245,512,790` (glucose from the sensor link, `AppGraph.kt:552-556`); Nightscout `NightscoutUploader.kt:269,375`, `NightscoutQueue.kt:85` (`UPLOADED_SOURCES = {Libre3, Juggluco}`); Tidepool `TidepoolQueue.kt:94`, `TidepoolUploader.kt:66,300,312-316,413`; Health Connect `HealthConnectSync.kt:128,158,185,314` | all leave imported data out |
| Apps on this phone | `AppsPage.kt:56-265`; broadcasts `BroadcastPlan.kt:89` (wearer + Libre 3 only); Nightscout API `NightscoutApiServer.kt:80,218`; xDrip+ server `XdripServer.kt:78-104` (no role check) | |
| Treatments | `ShareTreatmentSync.kt:33-60`; `TreatmentLog.kt:139-158` `WriterScope`; `SyncTreatments.kt:45-95`; pens `PensModule.kt:69-254` | owner = the wearer's phone |
| Settings mirroring | `WearerSettingsSource.kt:22`, `PublishSettingsMirror.kt`; `FollowerSettings.kt:24-46`, `FollowerSettingsBridge.kt:71-121`, `SettingsFromPage.kt` | Sukrara stream only |
| Sensor life | `AlertsModule.kt:272`, `AppSensorCalendar.kt:49,100`, `HomeScreen.kt:751,758`, `AppGraph.kt:1031-1038` | |
| Home / widgets / notification | `HomeViewModel.kt:348`, `NoDataReason.kt:45,125`, `GlucoseWidgets.kt:194`, `LockWidget.kt:160`, `WidgetTap.kt:47`, `LiveNotificationContent.kt:154-292` | words and taps |
| Import history | `SetupMachine.kt:214-221`, `AppsPage.kt:73,265`, `SukraraNavigation.kt:469-477`; rules `HistoryImport.kt:22-140` | wearer only |
| Storage | `readings` key `(t, source)` (`Entities.kt:12`); `Daos.kt:50` `upsertOwnFirst` | two first-hand sources of one person would store each measurement twice |
| Follower readings | `FollowerEngine.kt:934` always `relay` | `nearby` never set |
| LibreView sign-in | `LibreViewAccount.kt:127` `/llu/auth/login`, `:174-175` headers | the LibreLinkUp 4.x login |

### Mac

| What | Where |
|---|---|
| Follow a Sukrara phone | `M/Sources/SukraraShare/Follower/FollowerRuntime.swift` (stores `:24-31`, unfollow wipes `:436-458`); setup `M/Sources/SukraraApp/Windows/Setup/SetupFlow.swift:7-12` |
| Juggluco on this network | `M/Sources/SukraraShare/Lan/JugglucoLanSource.swift:85-88`, `JugglucoApi.swift`; switch `Windows/Settings/JugglucoRows.swift`; store `juggluco/readings.csv` |
| Arbitration | `M/Sources/SukraraApp/AppModel.swift:77-92,225-275,288,638` (`lanMonitoring` `:90-91`): following always wins; a switch resets alerts and reloads history |
| Branches | alert mirroring `AppModel.swift:188`, `AlertController.swift:29-141`; treatments only when following `AppModel.swift:189-197`; `NoDataReason.swift:48` |
| Readings | `M/Sources/SukraraCore/Model/Reading.swift` (no source); `ReadingsFileStore.swift:38-58` dedups by exact time |

## Appendix C: journey details

### Living with two apps

| Situation | What Sukrara does |
|---|---|
| Both apps ring | the default; Sukrara's quiets work on its own alerts; it never claims to have silenced the other app. Forum users keep only the official app's signal-loss alarm ([diabetes.co.uk](https://www.diabetes.co.uk/forum/threads/best-settings-for-xdrip-and-librelink-together.199873/)) |
| Readings come late (online) | the card's age says it; the source line says why; alerts as in the body (SPEC) |
| Upstream silent, internet fine | *Late: the Libre app hasn't sent anything to LibreLinkUp for 12 min. Open the Libre app.*; `no_data` as for any source |
| No internet | *No internet. The Libre app still has your glucose; Sukrara catches up when you're back* |
| The account needs action (terms, password, token) | *LibreLinkUp needs you: open LibreLinkUp, sign out and in, accept the new terms*, on home's source line, the Source page and the no-data alert's text |
| A treatment logged in the official app | never arrives (no cloud carries it); *Log treatments in Sukrara*, once |
| Battery | the source's description: *Uses more battery than reading the sensor*; no nag |
| Notification source: notification hidden or app killed | *Not getting readings: the Dexcom app's notification is off* + *Open its settings* |
| Restarted, not unlocked yet | online and app sources need credential-protected secrets: today's locked-boot notice + `no_data` (*Unlock to resume LibreLinkUp*) |

### Trust research

Trust in CGM and closed loops grows through reliable alerts and visible information
([PMC7162558](https://pmc.ncbi.nlm.nih.gov/articles/PMC7162558/)); 18 % stop CGM because they "didn't trust it", 32 %
for too many alarms ([ONBOARD](https://cdn.clinicaltrials.gov/large-docs/55/NCT04672655/Prot_SAP_002.pdf)); DIY-CGM
users' trust grows with use and drops with technical issues ([Sehgal 2024](https://onlinelibrary.wiley.com/doi/10.1111/dme.15168));
parents' connectivity fears ease with signal-loss alarms ([Elbalshy 2020](https://doi.org/10.1177/1932296819895290)).
Onboarding: one decision per screen, fewer than ten choices ([Material](https://m2.material.io/design/communication/onboarding.html)),
postpone nonessential setup ([HIG](https://developer.apple.com/design/human-interface-guidelines/onboarding)).

### Motion and haptics

| Moment | Motion | Haptic |
|---|---|---|
| Person card picked | card lifts (tonal fill, outline grows), its art's ring breathes once | tick |
| Source picked | the row expands in place to show what it brings and its delay; the others dim | tick |
| First reading from a new source | value counts up from *—*; the source line cross-fades *Waiting…* → *LibreLinkUp · 1 min* | confirm |
| The move's switch | home's source chip morphs (icon cross-fade, width spring); the graph doesn't redraw | confirm |
| Late / offline / back | status dot colour tween, the line slides; *Back online* collapses after 3 s | none (alerts own the strong haptics) |
| Checklist step ticks | the existing self-ticking check pop | confirm |
| Mac | SwiftUI springs; `NSHapticFeedbackManager` `.levelChange` on the first reading and the switch | |

All honour reduced motion (fade / snap) and announce the new state once (TalkBack / VoiceOver live region).

### States and exits (for FEATURES.md)

Each one on home's source line, the Source page and the no-data alert, with TalkBack / VoiceOver:

- **Whose glucose** → pick → *Continue*; later → *Delete the earlier history?* → *Export CSV first* / *Delete and
  continue* / *Cancel*.
- **Source picker** → pick → Connect; Back → the previous step / Settings unchanged.
- **Connect**: signing in (*Cancel*) → refused (the reason, edit, retry) / several people (pick; *Change* later) / no
  one shared (*Open LibreLinkUp* explains; retry) / terms to accept (*Open LibreLinkUp*, *Try again*) / connected →
  *Waiting for the first reading* (*Change source*) → *Reading received* (*Continue*).
- **Notification source**: access off → *Allow notification access* (a sideloaded APK first needs Android's *Allow
  restricted settings*, explained) / app not found → *Change source* / notification hidden → *Open its settings* / unit
  differs → *Use mmol/L like the Libre app?* (*Switch* / *Keep*).
- **Live · Late** (by itself; *Open the Libre app*) · **No internet** (by itself) · **Sign in again** (pre-filled) ·
  **Rate-limited** (by itself, said once) · **Locked boot** (unlock).
- **Two apps, one low** → any choice any time; *Only the Libre app* = alerts off with the existing *Turn alerts on*.
- **Move suggestion** → *Show me how* / *Not now* (next sensor, twice at most) / *Don't suggest again* (*Show closed
  tips* undoes). **Move page** → each step's action, Back (progress kept), *Go back to <app>* any time.
- **Sharing / uploads for someone else's glucose** → the reason row; *Whose glucose* is the exit.
- **Mac**: the same, plus *Do you have Sukrara on your phone?* → *Follow it* / *No, read it online*.

### Schema (`contracts/settings/settings.json`, all `"mirror": false`: a source is per device)

| id | Type | Platforms | Note |
|---|---|---|---|
| `source.person` | enum `yours / theirs` | both | replaces the role (device-protected as today) |
| `source.kind` | enum `sensor, sensorAlongside, officialApp, phoneApp, lanJuggluco, libreLinkUp, dexcomShare, nightscout, sukrara` | both, `only` per value | replaces `source.source` and the Mac's implicit choice |
| `source.libreLinkUp.patientId`, `.region` | string | both | account in the secure store / Keychain |
| `source.dexcomShare.server` | enum `us / ous / jp` | both | login in the secure store |
| `source.nightscout.url`, `.treatments` | string, bool | both | token apart from the upload's |
| `source.officialApp.package` | string | android `only` | |
| `source.twoApps` | enum `both / sukrara / official` | android `only` (no official app on a Mac) | |
| `source.moveSuggestion` | state | android `only` | the Mac never suggests |
| `sensor.manualStart` | state | both | |
| `source.jugglucoLan*` | | mac | ids unchanged, section `source` |

### Platform notes (facts, not design limits)

- Android's foreground service is `connectedDevice`; its prerequisite can be `CHANGE_NETWORK_STATE`
  ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)), as
  the follower polls relays today; Play's view of a cloud-polling source: unverified.
- Notification access on a sideloaded APK (GitHub) needs *Allow restricted settings* first on Android 13+
  ([XDA](https://www.xda-developers.com/android-13-restricted-setting-notification-listener/),
  [Google help](https://support.google.com/android/answer/12623953)); the Play build doesn't; F-Droid: unverified.
- Package visibility needs a `<queries>` entry per official app, each in `tools/check/allow.json` with its reason; each
  new host a `network_ok` entry.
- Online sources can't start before the first unlock without device-protected secrets ([30](30-direct-boot.md));
  credential-protected is the default here.
- The Mac App Store build already has outgoing network (relays); new hosts need nothing more.

### What other apps do (judged)

| App | What | Good / bad for the person |
|---|---|---|
| xDrip+ | *Hardware Data Source* incl. *Companion App* (notifications, "one companion app at a time", [docs](https://navid200.github.io/xDrip/docs/Follow/CompanionApp.html)); Nightscout follower 5–10 min late until a hand-tuned *wake delay* ([#929](https://github.com/NightscoutFoundation/xDrip/issues/929), [#3164](https://github.com/NightscoutFoundation/xDrip/discussions/3164)) | good: offline companion; bad: one flat list mixing hardware and clouds, delays tuned by hand |
| AAPS | *BG source*; Nightscout as source "not recommended… depends on stable mobile data" ([docs](https://androidaps.readthedocs.io/en/latest/SettingUpAaps/ConfigBuilder.html)); companion data "not trusted" for SMBs | good: says plainly which sources are weaker |
| Loop | remote CGMs "require internet", a cloud mark above the value while pulling from Share ([FAQ](https://loopkit.github.io/loopdocs/faqs/cgm-faqs/)) | good: a visible mark for "came over the internet" → our source line |
| GlucoDataHandler | sources grouped *Cloud services / Local apps / Notifications*; an alarm for old values ([README](https://github.com/pachi81/GlucoDataHandler)) | good: grouped by where; we group by what the person feels |
| Gluroo / Sugarmate | *Settings › CGM* / *Data Source*, in-app logins ([Gluroo](https://gluroo.com/support/faqs/), [Sugarmate](https://help.sugarmate.io/en/articles/4718755-adding-a-dexcom-data-source)) | good: in-app login; bad: "CGM" for a cloud |
| Mac menu-bar apps | GlucoBar, GlucoseBar, librelink-for-mac, Tidebar… (Appendix A) | demand is real; none has Sukrara's alerts, card, widgets, treatments |
| Juggluco | warns against two uploaders on one LibreView account ([help](https://www.juggluco.nl/Jugglucohelp/libreview.html)) | good: the rule we adopt |
| Health Connect | *Data sources and priority* settles duplicates | the model for the later fallback |

## Appendix D: edge cases and QA list

Sources and timing
1. The same reading from backfill and live push: one row (`(t, source)` upsert).
2. An online reading 16 min late: stored and drawn, never alerts; `no_data` counts from the reading's time (new scenario).
3. Service clock ahead (readings in the future): dropped as today (`GlucoseHubImpl.accept`); if every one is, *This
   phone's clock is behind*.
4. LibreLinkUp's live value and its graph point for the same minute: live readings are kept; graph points only fill
   gaps (±2 min = same, Import's rule).
5. Dexcom's 5-minute cadence: rate alerts need their minimum readings; `trend.txt` gains a 5-minute case if missing (a
   new scenario, not a changed one).
6. The cloud repeats one old value for 20 min (upstream silent): *Late*, `no_data` at its threshold, not *No internet*.
7. Region redirect (LibreLinkUp) at first sign-in and later.
8. 429 / Retry-After / "locked": back off as the Tidepool import does; never retry a refused password (account lockout).
9. Token expiry at night (1 h with LibreLinkUp's 5.0.1 identity): refresh silently; when it needs the password, the no-data
   alert says *Sign in again*.
10. The account follows several people; the picked one stops sharing: *Ana no longer shares with this account*.
11. Notification source: two official apps posting, no number, another unit, OEM battery killers stopping the listener.
12. The official app changes its notification: *Can't read the Dexcom app's notification* (the layout logged, never
    values).

Person and source changes
13. Source switch mid-alert: episodes continue (same person); a person change resets them.
14. Person switch with a treatment outbox pending: the dialog says *1 treatment not sent yet*.
15. Old source's readings in flight after a person switch: dropped by generation.
16. Sensor → online → sensor in one day: one continuous graph, no doubles (property test).
17. *Move to Sukrara* left at step 3 (Libre app stopped, sensor not started): the online source goes quiet; the page
    says *Readings pause until Sukrara reads the sensor or you reopen the Libre app*; `no_data` covers it.
18. Take-over fails (sensor started without the account): today's *wait for your next sensor*; the source stays online.
19. The official app uninstalled while it's the source: *The Libre app was removed* + *Change source*.

Duplicates and loops
20. LibreLinkUp source + LibreView upload: the upload page off with the reason (rule 6).
21. Nightscout source X + Nightscout upload X: glucose upload refused for the same site (scheme, host, trailing slash,
    token in the URL normalised); site Y allowed.
22. Site X fed by Sukrara's own upload from another device: kept (another device id), not looped back.
23. A Sukrara treatment sent to X and read back: same id, no copy. Edited there: a newer version. Deleted there:
    deleted in Sukrara (API v3 history).
24. Tidepool linked to Dexcom or LibreView (US) + Sukrara's glucose on: glucose off with *Tidepool gets it from Dexcom
    already* (rule 3).
25. Health Connect: the Dexcom app writes too: Health Connect's priority list settles it; Sukrara says so on its row.
26. Juggluco on the phone + Juggluco on the network + the phone sharing to the Mac: the Mac's source is one of them,
    never merged.
27. Phone and Mac both on LibreLinkUp, no follow: two treatment logs, said on the Mac.
28. Someone else's glucose never starts the owner engine (no A → B → A).
29. Imported + source backfill + sensor backfill over one hour: one reading per measurement.
30. An online or app source broadcasting to AAPS: no sensor label (rule 7).

Alerts and trust
31. Both apps ring: Sukrara's *Remind less* doesn't touch the other app, and nothing says it does.
32. *Sukrara rings* chosen, then the source becomes online: back to *Both ring* with a one-line note.
33. Escalation to Sukrara followers works for your glucose on any first-hand source (`WEARER`).
34. The move suggestion never fires during an alert, at night, for an untested family, on the Mac, for someone else's.
35. *How it's going* with less than a day: *after a day*.

Platform and privacy
36. Before the first unlock: online sources wait; notice + `no_data`.
37. Debug builds: sources only against local fakes (`LaunchPlan` / `LaunchMode`); synthetic data in screenshots.
38. Cleartext: every source https except loopback and Juggluco on the network; `allow.json` entries per source.
39. Secrets never in the settings export or logs (`Redact` gains the new keys).
40. Website and privacy policy list the new hosts; Features tiles en + es.
