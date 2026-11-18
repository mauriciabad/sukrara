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
- **Root** (Appendix F) rarely works "beside the official app": Abbott, Medtronic and Roche say their apps don't run on
  a rooted phone (Abbott: the Libre app "stops without notification", without alarms), Dexcom says not to. For Libre 3
  it opens nothing acceptable anyway (encrypted store and link, LSPosed refused). Where an official app still runs and
  keeps its readings unencrypted (Eversense did; unverified today), reading them read-only adds exact times and backfill
  to the notification source: later, per app, once a rooted tester checks. Hooks, lifted keys and interception: never.
- **The Mac gets sources of its own** (Nightscout, LibreLinkUp, Dexcom Share); treatments stay on the Mac (two-way with
  Nightscout).
- **Name**: *Source* (es *Origen*).
- **No duplicates or loops**: only the first Sukrara that gets your glucose shares and uploads it, never back to where it
  came from. LibreView takes one uploader per *app identity* ([45](45-libreview-two-uploaders.md)). While the official
  app uploads, Sukrara sends it nothing today. Treatments as the other identity are proven possible; that is decision 4,
  back with the owner.
- **The move is earned**: evidence on the Source page, a suggestion only at a sensor change for a tested family, a
  self-ticking checklist and a way back.
- **Cost**: the Person × Source change is the large part and ships first, with no new data path.

## Today in Sukrara

| Exists | For #213 |
|---|---|
| **Dexcom G7 / ONE+ / Stelo observer**: reads the sensor while the Dexcom app owns it, writes nothing; *Untested* ([23](23-sensors.md)) | already "the sensor alongside the official app" |
| Juggluco as a source (Android loopback, Mac on the network) | its server is Nightscout-shaped, as is xDrip+'s: a Nightscout reader covers both |
| LibreView sign-in via the LibreLinkUp login (`/llu/auth/login`, `version: 4.16.0`, `LibreViewAccount.kt:127,174-175`) | the LibreLinkUp source is a small step; **and** Abbott ending that API would also stop new sign-ins (a new Libre 3's receiver ID) |
| LibreView: one uploading device and one session per app identity ([45](45-libreview-two-uploaders.md); [41](41-libreview-other-sensors.md) assumed per account) | Sukrara can't send as the official app's identity without stopping it; as the other identity it can |
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
| Libre app's notification | ? | — | | | | in no reader's list (owner test); a 2022 hook added the value to it (Appendix F) |
| Official app's stored readings, **root** | later | — | as the app writes (Eversense 5 min) | yes | exact times, backfill | only apps that run on root with an unencrypted store (the Libre, Medtronic and Roche apps refuse root); app updates, root lost at an OTA |
| **DIY broadcasts** (xDrip+, Juggluco, AAPS) | ✓ | — | live | yes | rate, sensor start | none |
| **Nightscout** (+ Gluroo, xDrip+ / Juggluco servers) | ✓ | ✓ | push or poll | LAN only | **treatments both ways**, IOB, sensor start | none: open API |
| **Dexcom Share** (not Stelo) | ✓ | ✓ | 5 min; 24 h back | no | trend | unofficial; failed logins lock the account |
| **LibreLinkUp** | ✓ | ✓ | 1 min (Libre 3); 12 h back | no | trend, sensor start | unofficial; Abbott moving to an encrypted API; down with LibreView's maintenance (an hour per region, announced; [45](45-libreview-two-uploaders.md)) |
| Dexcom public API, Health Connect, Tidepool | | | 1–3 h late | | | not live |

Unverified and worth a sensor: Aidex X advertising while its app is connected (offline, and direct on a Mac); a Dexcom
G6 in the receiver slot beside the Dexcom app. Out: Libre 2 EU beside LibreLink (raw values, [02](02-direct-sensor.md)).

**Offline paths per family** (the owner's ask): Libre 3 / 3 Plus / 2 US / Lingo **none known**, so LibreLinkUp or the
move; Libre 2 EU none acceptable; Dexcom G7 family **the observer**, notification as fallback; Dexcom G6 notification
(receiver slot unverified); Medtronic Guardian notification (Simplera's lacks the value); Eversense, Aidex, Sibionics,
Medtrum, Ottai, iCan, CareSens Air notification; any CGM inside a loop app notification (value + IOB); SmartGuide none
reliable. Per family with the online fallback: Appendix A. **With root** (Appendix F): the Libre, LibreLink, Medtronic
and SmartGuide apps refuse to run, so nothing beside them; Eversense exact times and backfill, if its store is still
unencrypted (unverified); Dexcom needs none.

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
| Later | **Root**: the official app's unencrypted stored readings, per app that runs on a rooted phone (Eversense first); taking over a Libre 3 the Libre app started without LibreView | Android | each app needs a rooted tester's check first; adds exact times and backfill to step 3, or moves a sensor (Appendix F) |
| Never | Dexcom's API or Health Connect as live; patched vendor apps; lifting LibreLinkUp 5's crypto; borrowed Dexcom certificates; **hooks into an official app (LSPosed), keys lifted from it, Bluetooth or internet interception** | | 1–3 h late, or circumvention ([23](23-sensors.md) rule 2; Data Act 4(11), 11(1)) |

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
- **LibreView's maintenance stops it**: LibreLinkUp runs on LibreView's regional servers, so a LibreView window stops
  every reading, the official app's uploads and Sukrara's source together. 2026-11-18, EMEA and UK: 22:05 → 22:38
  (the owner's phone, [45](45-libreview-two-uploaders.md) 45b). The region answers HTTP `911` `{"status": 911}` to every
  call, before authentication. The LibreLinkUp app shows *Down for maintenance · Please try again later*; Sukrara showed
  *No answer from LibreLinkUp* and its no-data alert rang after 20 min. Abbott announces each window on
  [status.freestyle.abbott](https://status.freestyle.abbott/) (public JSON: region, start, end) days ahead, about one per
  region every month or two.
- **Lockouts**: retries lock the person's own account (Dexcom failed logins; LibreLinkUp 429 "locked",
  [#218](https://github.com/timoschlueter/nightscout-librelink-up/issues/218)): back off, never retry a refused password.
- **Two apps ring**: Sukrara can't silence the official app, whose urgent low (≤ 55 mg/dL) can't be turned off
  ([Dexcom G7](https://www.dexcom.com/en-au/dexcom-g7/how-it-works), [Libre 3 Plus](https://www.adces.org/education/danatech/home/freestyle-libre-3-plus---dexcom-g7-15-day-comparison)).
- **Late readings, battery**: clouds come 1–5 min late, in gaps; notifications carry no time or backfill. No battery
  measurement of two apps plus a poll each minute found.
- **Rooted phones**: the Libre, MiniMed, Simplera, Guardian Connect and SmartGuide apps refuse them; on one, Abbott says
  the Libre app "stops without notification", so its alarms too (Appendix F.4). Updates added these checks (LibreLink
  2020, Libre 3 3.4.0 in 2022). Where Sukrara sees root and the source is such an app, it says so once and keeps its own
  alerts on; it never suggests hiding root. A root reader of an app's store also breaks on that app's updates and on
  root lost at a system update (F.6).

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
| The official app on this phone (+ its stored readings with root, later) | yours | ✓ | — | this device | manual start |
| Juggluco · xDrip+ · AAPS on this phone | either | ✓ | — | this device | Juggluco: start |
| Juggluco on the network | either | — | ✓ | this device | start |
| LibreLinkUp | either | ✓ | ✓ | this device | start |
| Dexcom Share | either | ✓ | ✓ | this device | manual start |
| Nightscout | either | ✓ | ✓ | two-way with the site | its *Sensor Start* |
| Sukrara on another device | either | ✓ | ✓ | writer scope | relayed |

Each source declares its capabilities (also offline, delay, backfill, needs root) in `contracts/sources/sources.json`;
UI and rules read those, never the id. Root isn't a source of its own: it's how *The official app on this phone* reads
more for an app that allows it (Appendix F).

**Who signs in where** (checked): Dexcom Share takes **the sharing account's** login, not a
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
  The Libre app keeps its alarms. Sukrara's come about 10 seconds later (LibreLinkUp).
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
- **Treatments logged in the official app don't arrive by themselves**: LibreLinkUp doesn't carry them; LibreView
  keeps them, but gives them only in its reports and its *Download glucose data* file (captcha), which Import history
  reads (checked 2026-11-18, "In Sukrara"). *Log treatments in Sukrara*, once.
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
| daytime, no alert showing (any day: the move works mid-sensor; a sensor change is the natural moment) | *Don't suggest again*, or 2 *Not now* |

Home's banner slot (*Show me how* / *Not now*) and always *Move to Sukrara* on the Source page; never a notification:
*Read your sensor in Sukrara · Live every minute, works offline, one app instead of two.* **The move works at any
point of a sensor's life** (owner, 2026-11-18: "i expected a user could move to sukrara at any point"): Sukrara
already takes over a running Libre 3 that the Libre app started with the same LibreView account, with an NFC tap
(`ReceiverIds.forTakeover`, docs/02). Only one app holds the sensor ([Juggluco FAQ](https://www.juggluco.nl/Juggluco/faq.html)),
so from that tap the Libre app gets no readings and rings no alarms; the Move page says so before the tap, and the
person confirms. Not yet tried from the Libre app on a real sensor (Sukrara's takeovers so far were from other apps).

```
  Move to Sukrara                                              Back
  1 ✓ Your history is here since Oct 3 (LibreLinkUp)
  2 ○ Your LibreLinkUp followers see you only while LibreView gets your glucose;
       Sukrara uploads to LibreView once it reads your sensor.       [Invite them to Sukrara]
  3 ○ From the tap on, the Libre app gets no readings and no alarms;
       stop it so it doesn't ring "signal loss"                      [Open app info]
  4 ○ Take over the sensor in Sukrara (any day of its life)          [Set up sensor]
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
6. **LibreView takes one uploader per app identity** ([45](45-libreview-two-uploaders.md)). Taking over, or even signing
   in with the official app's own DeviceId, stops the official app's glucose silently, and so the LibreLinkUp source.
   **Nothing to LibreView under the official app's identity while it uploads.** Treatments as the *other* identity leave
   it alone (45, proven live). Until the owner decides decision 4 again, nothing goes up; glucose resumes at the move
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
re-sharing someone else's glucose (decision 9), a Clarity file import, root reading of official apps' stores (decision
12; Sukrara's `RootAccess` states and root prompt already exist for the status bar).

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

- **Treatments to LibreView while the official app uploads**: *answered by [45](45-libreview-two-uploaders.md)*. They
  are possible as the other app identity (LibreLink beside the Libre 3 app) without touching its glucose; decision 4. They wait and go up at *Take over* ("In Sukrara",
  treatments while the Libre app holds the sensor).
- **The Libre app's notification**: unverified; decides Libre's only offline path and the order of steps 2 and 3.
- **Battery** of two apps plus a poll each minute: no numbers; measure in step 2.
- **Sukrara's own LibreView sign-in** rides on the LibreLinkUp 4.x login: when Abbott ends it, new sign-ins (a new Libre
  3's receiver ID) stop too. Outside #213; worth its own issue.
- **Real people's words**: Reddit unreachable; demand from GitHub, forums and what people build.
- **Which official apps keep readings unencrypted today**: unverified for every app; the last reports are 2019
  (Eversense) and 2022 (LibreLink). Decides decision 12's first targets (Appendix F, test R2).

## In Sukrara

**Step 0, Person × Source (2026-11-18, both apps).**

Added:
- **The model**: `Person` × `SourceKind` → `SourceSpec` (Android `:core:model` `Source.kt`, Mac `SukraraCore`
  `Source.swift`), each source declaring persons, first-hand, offline, group, delay, backfill and reading ids in
  `contracts/sources/sources.json`, which both apps pass (`SourcesContractTest`, `SourcesTests`) with its `keying`
  table: `owner` (yours, first-hand) keys sharing, uploads, pens, sensor reminders, Import history and the WEARER alert
  role; `stream` keys the follower engine, *Settings from*, writer scope; `person` keys the words. The role (`Role`,
  `role.json`, `source.source`, the Mac's `jugglucoLan`) is gone; schema rows `source.person`, `source.kind` (Android:
  a typed state file with a backup, device-protected, store `state`).
- **Alerts unchanged**: the alert engine still receives WEARER / FOLLOWER_MIRROR / FOLLOWER_OWN; the runtime maps the pair
  (`AlertSide`), so no scenario in `contracts/alerts/scenarios` changed.
- **The rank rule** (`contracts/sources/dedup.json`): one reading per measurement, ±2 min, sensor > phone app > network >
  online > another Sukrara > imported; Android stores every batch through it in one Room transaction (replacing "the
  sensor removes imported copies"); a person change deletes every reading and treatment (confirmed, *Export CSV
  first*), a source change keeps them; the hub drops a reading still arriving from the previous source.
- **Bluetooth follower readings tagged `nearby`** (`FollowerEngine.heard`), the rank rule keeping one copy.
- **Setup**: *Whose glucose will Sukrara show?* (two illustrated cards drawn in the theme's colours, lift + one ring
  breath + tick, reduced motion honoured) → *Where does your glucose come from?* (SPA radio rows grouped *On this phone
  · works offline* / *From another Sukrara*, the sensor *Recommended*) → Follow someone / Connect Juggluco / Sensor. The
  wrong "reinstall" copy is gone. Mac: the same two steps (cards side by side, radio rows), *Connect Juggluco* in place
  of Follow when chosen.
- **Settings › Source** (Android overview row after Alerts; Mac pane replacing *Following*): whose glucose, where from
  (each opens the step on its own), *Now*, the source's rows (Sensor link; Juggluco state and secret; the follow and
  *Settings from*); Settings › Sharing elsewhere says sharing is for your own glucose.
- **Updates from before the question**: the device keeps reading what it's set up for (`inferredSource`: follow → their
  Sukrara, what this phone read itself last (its sensor's or Juggluco's newest reading), a sensor pairing → your
  sensor, Juggluco installed after setup began → Juggluco; Mac: following → their Sukrara) and setup asks once more,
  preselected. The readings came in after the owner's update (2026-11-18): the pairing first took a Juggluco reader
  with an old pairing for a sensor reader (its live readings unread), and Juggluco merely installed turned the owner's
  phone, whose ended sensor had no pairing any more, into a Juggluco reader.
- **The review's fixes** (product / UX / QA pass, same day): with nothing else to go by, the history says whose it is
  (`historyOwner`: a reading taken or imported → yours; only another Sukrara's → theirs), so the first answer never
  adopts one person's readings for the other (found on an update with history but no pairing: *Someone else's* kept
  the wearer's readings as the child's, and answering *Yours* then asked to delete them). *Now · Live* → *Last
  reading* (*Just now* / *2 minutes ago* / *None yet*: Live claimed a working source from any old reading); *works
  offline* said once (the group title); the Switch dialog mentions the sensor only when leaving it; *Connect
  Juggluco* says when Juggluco isn't installed; no step counter on steps opened from Settings, and Back from the
  source returns to the cards; the welcome speaks of yours or someone else's glucose; the card's press ripple inside
  its corners; Source's own icon (*input*, the drop is Readings'). Mac: someone else's art in purple (it was pink-red,
  a low's colour), no *Skip* on the two questions (Android has none; skipping left the Mac reading nothing), the
  person asked again after a relaunch half-way, *Answer…* on the unanswered banner, the follow footer only while
  following.

Discarded or deferred, with reasons:
- *Your sensor beside the official app*, official app, LibreLinkUp, Dexcom Share, Nightscout as kinds: no data path in
  step 0 (the brief); `sources.json` grows with steps 1–3.
- *Two apps, one low*, *How it's going*, the move: need a source beside an official app (steps 3–4).
- Yours × another Sukrara keeps a stream's behaviour (no pens, reminders or Import history on the second device): the
  first-hand phone owns them; the table's "person" keying applies to first-hand sources.
- The Mac's one readings store with `Reading.source`: the Mac reads one source at a time (follow or Juggluco, each its
  own store, history reloaded on a switch), so nothing can double yet; the rank rule is implemented and contract-tested
  there (`ReadingRank.merge`) for the step that merges stores (Nightscout on the Mac).
- On the Mac a person change deletes nothing: its readings are each source's own store (the follow's goes with *Stop
  following*); it changes the words.
- The Mac's *Do you have Sukrara on your phone?* first question: with no online source yet the Source step's first
  choice is that question; it comes with step 1.
- Juggluco users on the Mac who updated: their old switch isn't read (no back-compat); the Mac asks the source on
  *Finish Setup…* and doesn't read Juggluco until answered.

**Step 1, Nightscout as a source (2026-11-18, both apps).**

Added:
- **Contracts** (`contracts/sources`): `nightscout` in `sources.json` (both persons, group `online`, keyed WEARER /
  own rules), ranked `online` in `dedup.json`; `health.json` (connecting, live, late, no new readings, offline,
  unreachable, signed out, rate-limited); `outputs.json` (where the site's glucose and treatments go);
  `nightscout/` fixtures with a generator: `entries.json` (own-loop guard by device), `treatments.json` (THE two-way
  planner), `sites.json` (`?token=` split, https except loopback, same site), `sensor-life.json`, `poll.json`. Schema
  `source.nightscoutUrl` / `Treatments` / `Secret`. Alert scenarios `online.txt` (5-min cadence, late readings): no
  rule changed, both engines pass them.
- **Android** (`:integrations` `nightscout/site`, not a new `:sources` module: the Nightscout client, documents and
  upload already live there, so one client serves both directions): `NightscoutSource` (a `ReadingSource`), the account
  (token or API secret told apart by trying, saved only after the site answers), the health on home's no-data reason,
  Settings › Source rows and setup's Connect step, treatments both ways (the site's entries read-only via
  `TreatmentRecord.via`), sensor life, the data rules (same-site upload paused, LibreView none, Tidepool switch off,
  no Bluetooth state for an online source), *Bring back 90 days from Nightscout* (history step and Source page).
- **Mac**: the same model and fixtures (`SukraraCore/Nightscout`), an actor source (`SukraraShare/Nightscout`), its
  own store and **its own treatment log** for a non-stream source (add, edit, delete, undo; the site's read-only),
  the first question *Do you have Sukrara on your phone?*, Connect, Settings › Source rows, the panel's states.
- **Sensor life from the site's newest Sensor Start / Change** with the contract's default warm-up and wear (1 h, 15
  days, as Juggluco): warm-up, *Sensor ended* (no data quiet), reminders, countdown, calendar, followers.

Discarded or deferred, with reasons:
- **socket.io push**: polling timed to the next expected reading (`poll.json`) gets within ~45 s of the uploader with
  plain HTTP on both apps; a socket.io client per app for seconds isn't worth it now.
- **API v3 history for deletions**: v1 works with an API secret and every Nightscout; deletions are found by reading
  the 24 h window again in full (a capped read never deletes). Edits and deletions older than 24 h aren't seen.
- **The loop's insulin** (SMB, temp basals) isn't imported: it would quiet high alerts all day while a loop runs;
  boluses the person gives are.
- **The wear length the site doesn't say** (a G7's 10 days shows as 15): the manual start with its length (decision
  10) comes with step 2, for every source without one.
- **App broadcasts of a site's glucose**: the broadcast plan carries a Libre 3's serial and life count; apps that want
  a site's glucose read it themselves (`outputs.json`). Uploads to another site don't get the source site's Sensor
  Start yet.
- **The no-data alert's text naming the cause** (*Nightscout refused the token*): a `contracts/alerts/copy.json`
  change on both notifiers; home and Settings say it.
- **The first reading's value counting in on Connect**: a value there is a new card surface (`surfaces.json`); it says
  *Reading received* with a haptic and an announcement instead.
- **The Mac's one merged readings store**: the Mac still reads one source at a time, each its own store, so nothing
  doubles; it comes with fallback between sources.
- **Two apps, one low**: it's for an official app ringing beside Sukrara (step 3); a Nightscout site rings nothing.

**Step 2, LibreLinkUp as a source (2026-11-18, Android; the Mac the same day).**

Added:
- **Contracts** (`contracts/sources`): `libreLinkUp` in `sources.json` (both persons, group `online`, 90 s, keyed WEARER
  / own rules); two reading ids, `librelinkup` (the live reading, ranked `online`) and `librelinkup-graph` (the graph's
  5-minute points, a new rank `onlineHistory` below it: they only fill gaps, Appendix D 4) with dedup cases (the
  takeover's backfill over both); `health.json` gains `terms` → `actionNeeded`, `notShared`, `retired` → `stopped`;
  `outputs.json` row `librelinkup` (LibreView `no`, Tidepool `on` with its switch, broadcasts `no`);
  `librelinkup/` fixtures with a generator (`login.json`, `calls.json`, `connections.json`, `graph.json`, `poll.json`:
  synthetic bodies shaped like GlucoDataHandler's and xDrip4iOS's); schema `source.libreLinkUpPatient`,
  `source.libreLinkUpSecret`. Alert scenarios `librelinkup.txt` (a minute cadence 1 and 3 min late: the rising-low quiet
  holds at 1, not at 3; a reading 9 min late; out-of-order readings): no rule changed, the Android engine passes them.
- **One LibreLinkUp client** (Android `:core:data` `LibreLinkUp`): the LibreView sign-in (`LibreViewAccount`) and the
  source share its login, headers, version (4.16.0 → 4.17.0, as GlucoDataHandler and xDrip4iOS), region redirect, a
  403's minimum version sent once, terms (never accepted for the person), 429 with Retry-After or the lockout.
- **The source** (`:integrations` `librelinkup/`): its own account in the Keystore (apart from THE LibreView account, so
  a parent's account never becomes the app's LibreView account and signing out of one never stops the other), the
  token reused until 5 min before it expires (a restart doesn't log in: logins count towards the lockout), a graph call
  each minute, the whole 12 h only at the first call and after a gap, whom the account follows every 30 min; the pick
  (`connections.json`: the account itself for your glucose, the only other person for someone else's, several = pick);
  its health on home, Settings › Source and Connect; the sensor's serial and start as its life.
- **Setup's Connect step** (*Sign in as …* with the LibreView account Sukrara has, for your own glucose; the form;
  *Whose glucose?*; no one shared → the Libre app's menu path and *Try again* without the password) and the history step
  (*Your last 12 hours came with LibreLinkUp* + 90 days from LibreView). **Settings › Source** rows (account, whose
  glucose, status with its exit, sensor, *The Libre app has the sensor*: LibreView and *Read the sensor in Sukrara*,
  *Sign out* confirmed).
- **The data rules**: the LibreView upload sends nothing at all while LibreLinkUp is your source (not even its sign-in,
  which could take over), its status says why and offers no *Take over*; Tidepool's *Glucose from LibreLinkUp* (on);
  Nightscout upload and Health Connect take it; no broadcasts.
- **The takeover path** (the move, mid-sensor): with LibreLinkUp as your source the new-sensor flow's gate *Take the
  sensor from the Libre app* says, before the tap, what the Libre app and LibreLinkUp lose, offers *Open the Libre app's
  info* (force-stop) and the way back; it shows whether or not the Libre app is installed on this phone. The tap uses
  the existing takeover (`ReceiverIds.forTakeover`); the pairing switches the source to the sensor by itself (as
  pairing always did); the rank rule puts the sensor's backfill over LibreLinkUp's readings; the sensor's start is the
  sensor's own, LibreLinkUp's when the answer lacks one (`NfcPairing.knownStart`); the LibreView upload then finds the
  Libre app uploading and offers *Take over*. The way back: *Where it comes from* → *LibreLinkUp* (the account stays),
  then scan the sensor with the Libre app (said in the switch dialog).
- **A read-only probe** (`tools/librelinkup-probe/probe.py`) for the owner's account: login, connections, graph, the
  answers to a local file without the password or token; `--sanitise` turns it into fixture-ready bodies.

Discarded or deferred, with reasons:
- **Auto-accepting the terms step** (GlucoDataHandler does, `/auth/continue/tou`): accepting Abbott's terms is the
  person's act; Sukrara says where and waits.
- **The connections call each minute** (it carries the current reading too): the graph call brings the live reading and
  the backfill in one answer; connections are read every 30 min and when the graph refuses.
- **Card words with the person's name** (*Ana's glucose*) for someone else's LibreLinkUp: the name shows on Settings ›
  Source and Connect; the card's name comes from a Sukrara stream today (a later change of the hub's link).
- **The wear length** LibreLinkUp doesn't say: the default 15 days (a Libre 3's 14 shows a day long until the takeover,
  when the sensor says its own); the manual start with its length (decision 10) stays to do. LibreLinkUp can't tell
  it either (checked in the QA review): no expiry field; `sensor.pt` is 4 for a Libre 3 **and** a 3 Plus (3 for a
  Libre 2 and 2 Plus), `dtid` 40068 names the Libre 3 app, `w` (60) is the warm-up
  ([dodog/librelink #13](https://github.com/dodog/librelink/issues/13#issuecomment-6400299627),
  [xDrip4iOS #567](https://github.com/JohanDegraeve/xdripswift/issues/567)); the clients that show 14 or 15 days ask the
  person ([xDrip4iOS](https://github.com/JohanDegraeve/xdripswift/blob/f7447f1c70a194cee1c9c8eea96ebbadf62027b8/xDrip/Managers/LibreLinkUp/LibreLinkUpFollowManager.swift#L344-L355),
  [GlucoDataHandler](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/utils/GlucoDataUtils.kt#L248-L262)).
- **The full *Move to Sukrara* page and suggestion** (step 4): *Read the sensor in Sukrara* is the page's step 4; the
  checklist, *How it's going* and `MoveSuggestion` need the arrival times step 4 records. Nothing here conflicts with
  them (the page's steps reuse this gate, the switch and *Take over*).
- **One LibreLinkUp account signed in on phone and Mac at once** (decision 9's question): unverified; the probe and the
  owner's test will tell. Each device keeps its own token.
- **Battery of a call each minute**: not measured yet (the owner's day with both apps).

Added on the Mac (decisions 7 and 9: the Mac on its own, a parent with their own account):
- **The same client rules** (`SukraraCore/LibreLinkUp`: login, calls, people, pick, graph, UTC times, arrow → rate,
  `LluPoll`) passing every `contracts/sources/librelinkup` fixture (`LibreLinkUpContractTests`); `sources.json` lists
  `libreLinkUp` for both platforms, so `SourcesTests` covers it.
- **An actor source** (`SukraraShare/LibreLinkUp`, over `LibreLinkUpHTTP`: URLSession, ephemeral, redirects refused;
  `FakeLibreLinkUpHTTP` in tests): the account in the Keychain, the token kept until 5 min before it ends, a 401 → one
  new login, a refused password forgotten and never retried, terms never accepted, a 429 ≥ 10 min, connections every
  30 min, the graph each minute (12 h at first and after a gap). Live readings and graph points in **two stores**
  merged by the rank rule (`LibreLinkUp.merge`, tested equal to `ReadingRank.merge`): the Mac's readings store keeps
  no reading id, and a live reading arriving after a nearby graph point must still replace it.
- **Its own treatment log** (`librelinkup/state.json`, as Nightscout's minus the sync); another person starts readings
  and log again, asked first in Settings when this Mac holds someone's.
- **Setup and Settings › Source**: *Sign in to LibreLinkUp* (the form with AutoFill, *Whose glucose?*, no one shared,
  *Reading received*), the rows (status and exit, account, whose glucose, sensor, *Sign Out of LibreLinkUp…*), the
  panel's reasons and path badge, the widgets' words.
- **The UI's choices**: the form inline in the step and in Settings (as Nightscout's, and as the LibreLinkUp Mac apps
  that put the account in Settings, e.g. [librelink-for-mac](https://github.com/albertgd/librelink-for-mac)), not a
  sheet: one place to see and fix the account. `.username` / `.password` content types so the Passwords app fills them
  ([WWDC20 *AutoFill everywhere*](https://developer.apple.com/videos/play/wwdc2020/10115/): AppKit apps get password
  AutoFill when the fields are tagged; a SwiftUI Mac form without them gets none, [an example
  issue](https://github.com/tigerduck-app/tigerduck-app/issues/388)). Several people: radio rows in setup (two to a few
  choices, all visible), a pop-up menu in Settings (the Settings form's own control). Problems said in words with what
  to do, as [GlucOS](https://github.com/DMayoBiologic/GlucOS) does for terms and *not following anyone*; unlike
  librelink-for-mac, terms are never auto-accepted.
- **Found on the way**: switching the Mac's source away from an online one left its readings on the card until the
  next snapshot (Nightscout too): `sourceKind` now clears them at once.

Discarded or deferred on the Mac, with reasons:
- **LibreView rows, the takeover, history import**: the Mac uploads nothing and has no NFC; the 12 hours LibreLinkUp
  gives are the backfill.
- **Card words with the person's name**: as on Android (Settings and Connect name them).
- **Android's 429 without Retry-After**: Android's source passes no wait to `LluPoll` then and backs off from 1 min;
  the Mac waits the contract's 10 min (`poll.json` *asked to wait without a time*). Reported for the Android QA pass,
  fixed there (below).

**Step 2's QA review (2026-11-18, Android emulator against a fake LibreLinkUp).** The builder had checked the screens
only in Robolectric renders, so the whole journey was walked on an emulator against `tools/librelinkup-probe/fake.py`
(synthetic, loopback; the debug `LIBRELINKUP` command points the client there): setup, *Sign in as …*, the form, terms,
the redirect, 429, a version asked for, one person / several / none, a reading each minute, late, stopped 20 min and
back, the token ending early, a restart, offline, history import over the same hours, Settings › Source, Share with
apps, the takeover gate. The numbers on home matched the fake's, and no two readings of two sources were ever within
2 min of each other (`sqlite3` on the emulator). Fixed:
- **A 429 without a time** (`Retry-After` or lockout; the Mac's build found it) was retried after a minute, and a kick (the network back, *Try
  again*) called at once during any wait: each call may lengthen the account's lockout. Now at least 10 min
  (`LluPoll.next`, poll.json), no kick calls before the time LibreLinkUp gave, the status has no *Try again* (it asks by
  itself); a sign-in the person makes ends the wait.
- **A version asked for on a call** (403 with `minimumVersion` on the graph or connections call, not the login) read as
  *stopped* for an hour, and stayed so while the token lived (180 days): now sent from then on and asked once more, as
  at the login; it stays with the account through a new sign-in and a restart (it was dropped at the next sign-in), a
  newer built-in version wins; a minimum that isn't a version is never put in a header.
- **Someone who stops sharing** (LibreLinkUp refuses their graph) showed *LibreLinkUp stopped answering Sukrara* for an
  hour: whom the account follows is read at once, *Pick again* / the invite words show.
- ***Sign in as …* refused** (the LibreView password changed, or none saved) showed nothing but a haptic: now the form,
  with that email filled in and the refusal under the password. *Change account* from a working account offered *Sign
  in as* the same account and no way back but leaving the step: the form, *Sign in as …* only for another account,
  and *Keep this account*.
- ***Open the Libre app's info*** on the takeover gate did nothing when the Libre app isn't on this phone: shown only
  where it is installed.
- **Settings › Source said *Whose glucose* twice** (the person, then whom LibreLinkUp reads), with the same icon: the
  second is *Person* (its search entry *LibreLinkUp person*); a person who stopped sharing while others still do reads
  *The person picked stopped sharing…*, not *No one shares…*.
- **Fewer calls**: a pick or a sign-in asked for the graph twice in a row (an explicit kick and the config's), *Try
  again* called the connections and then the graph even when the first was refused: one call each now.
- **"Live · last reading 0 minutes ago"** on the Connect step (Android's minute resolution): *just now* under a minute
  (Nightscout's step too).
- **QA tooling**: Q30 asked for a fake LibreLinkUp nothing could point at; the `LIBRELINKUP` command and the fake now
  exist (docs/DEVELOPMENT.md), and `LibreLinkUp.kt` is on the cleartext check's `network_ok` list (its multi-line
  `Request.Builder()` slipped past the check).

Seen and left, with the reason:
- **The takeover's tap and the LibreView upload's *Take over*** can't run on an emulator (no NFC; the upload's server
  isn't faked): the gate's pages were checked in the gallery (light, dark, largest font, Spanish) and the switch to the
  sensor with its backfill was simulated with `SYNTHETIC` over the fake's identical curve (the backfill replaced every
  LibreLinkUp reading within 2 min); the owner's test covers the rest.
- **The upload status said *Sukrara takes over once it reads your sensor***, while after the tap it *offers* *Take over*:
  fixed in the polish round (below).
- **Restore your followers** said *Sensor running in Sukrara* ✓ for any source with readings: fixed in the polish round.
- **Home says nothing between 7 and 20 min** of a late or stopped LibreLinkUp (the stale value and its time show; the
  reason comes with the no-data alert at 20 min): the card's rule for every source, kept.

**Step 2's QA review, brought to the Mac (2026-11-18, against the fake `LibreLinkUpHTTP` only).** Android's list checked
on the Mac's source and screens. Fixed:
- **A version asked for on a call** read as *stopped* (the Mac raised it only at the login): sent from then on and the
  call made once more, kept in the Keychain item through a new sign-in and a restart, a newer built-in version wins, a
  minimum that isn't a version is none (`LibreLinkUp.isVersion`).
- **Someone who stops sharing** read as *stopped* for an hour: whom the account follows is read at once; with others
  still sharing, Settings and Connect say *The person picked stopped sharing glucose with this account.* with *Pick
  Again* (the one left is asked first, as any other person; several: the choice), the panel *The person picked
  stopped sharing glucose* (a new no-data reason, `pickedGone`), not *No one shares…*.
- **Asked to wait**, *Try Again*, the network back, *Try Again* on the people and another person picked all called
  LibreLinkUp at once: none calls before the time it gave (the loop starts after it), the status shows no *Try
  Again* (it already didn't); a sign-in the person makes ends the wait.
- **Calls after a sign-in or *Try Again***: the loop restarted with a round at once, so a pick of another person could
  ask for the old person's graph first, and a refused connections call was followed by connections and graph again:
  the graph once for the same person, the new person's own call only, the outcome's wait otherwise.
- **A token refused early whose new sign-in failed** always read as a refused password: its own outcome (terms, wait,
  no answer).
- **The LibreLinkUp row was titled *Whose glucose*** like the pane's person row: *Person* (es *Persona*).
- **Shared cases** (`contracts/sources/librelinkup`): a minimum that isn't a version (`login.json`, `calls.json`),
  `poll.json` `after` (the wait after each outcome, `LluPoll.next` on both apps) and `versions` (which version is
  sent); both apps' contract tests run them.

Checked and already right on the Mac: *just now* under a minute (`NightscoutStatus.ago`); *Change Account* from a
working account offers the form and *Keep This Account*; no *Try Again* on the rate-limited status.

Not applicable on the Mac: *Sign in as …* refused (the Mac has no LibreView account to offer; its form is the only
sign-in) and *Open the Libre app's info* (no takeover on the Mac).

**Polish round (2026-11-18, both apps; the owner's list from the product, UX and QA reviews).**

Added:
- **"Wearer" / "follower" left the UI** ("Naming"): the strings say it from the person's side, *Their settings* / *Ana's
  settings* (*Settings from*, was *The wearer's settings* / *Ana (wearer)*), *Copy their settings*, *No readings from
  their phone*, *They stopped sharing*, *the key to follow them*, *Logged by someone who follows you*, the camera and
  Bluetooth prompts on the Mac, the widgets' reasons; an unknown name reads *this person*. *Your followers* (the people
  who follow you) stays: it is the person's own word for them. Code names stay.
- **Setup's groups read as cards in both themes**: setup's page is SPA's `settingsBackground` (as its own `GlifScaffold`
  and every Settings page), its cards the groups' row colour (`groupsBackground` / `groupCard` in the designsystem), so
  the Source and Notification steps' rows no longer float on a near-identical white.
- **Setup's steps move on M3's shared axis X** ([Material motion](https://m3.material.io/blog/android-material-motion),
  `Motion.sharedAxisX`: 30 dp, 300 ms, the fade-through's opacity; Back slides the other way, `SetupMachine.goesBack`;
  reduced motion: the fade only). The Mac pushes a step in from the trailing edge (Back: the leading one), as a navigation
  push and Setup Assistant's panes move, and fades the one left; Reduce Motion: a cross-fade ([HIG
  Motion](https://developer.apple.com/design/human-interface-guidelines/motion), *Prefer cross-fade transitions* in
  [WWDC19 244](https://developer.apple.com/videos/play/wwdc2019/244/)).
- **One measure of glucose on both screens**: the delete-history dialog and Export say *4 days of glucose* / *18 hours of
  glucose* (the 5-minute stretches that hold a reading, `glucoseMinutes`), where one counted every reading (5,276) and
  the other the file's 5-minute rows (225).
- **The ended sensor names where it's replaced** (contracts/graph `renew`, both renderers): *Replace in Libre app*
  (LibreLinkUp), *Replace in Juggluco*, *Replace in its app* (a Nightscout site's uploader), *Ended · Replace in …*,
  VoiceOver / TalkBack *Sensor ended. Replace it in …*; home's reason says *Apply a new one and start it in the Libre
  app* and its pill and action open Source, not the new-sensor flow.
- **Home with Juggluco as the source** says Juggluco's reason with its way out (Source): *Juggluco isn't installed*
  (re-checked on resume), *doesn't answer* (its web server), *refuses Sukrara* (the API secret), else *No readings from
  Juggluco · Check that Juggluco is reading the sensor*; never the sensor's *Keep the phone near the sensor*.
- **Monitoring starts at the first unlock**: the boot broadcast after the unlock took the locked-boot path while the
  readings taken before the unlock were moved (`awaitMonitor` looked at the history's unlock, not Android's), and the
  starter read the hub's copy of the source, which follows a moment later; a source known only after the unlock (a
  follow, the history) started nothing until the app was opened or the 15-minute health check. Now the boot waits for
  the unlocked part and the starter reads THE source (`MonitorStarter` `spec`).
- **Online readings say nothing about whose they are** (`historyOwner`): someone else's Nightscout with a damaged source
  file was taken as yours (its readings aren't relayed); readings only from an online source now guess nothing and
  setup asks; relayed ones still mean someone else's, a sensor's or an import yours.
- **The LibreView upload's paused status** says what happens: *Once Sukrara reads your sensor, tap Take over to upload
  from here.*
- **Restore your followers' checklist** names the source: *Sensor running in Sukrara* only for the sensor Sukrara reads,
  else *Readings arriving from Nightscout* (LibreLinkUp, Juggluco), ticked by a live reading from it; the warning says
  *once your glucose arrives on this phone*.
- **Only the card being picked springs** (the owner: on the Mac the pick played on both cards): Mac
  `ChoiceCardMotion` springs the picked card and eases the one left back without bounce (each card's symbol bounces
  on its own pick); Android's card left settles without the spatial spring. Checked frame by frame on both.
- **The another-Sukrara group says what it needs** (the owner: *From another Sukrara* didn't say it): *From another
  Sukrara · internet or Bluetooth nearby*, the pattern of *On this phone · works offline* and *Online · needs
  internet*; "or" says either is enough, and *nearby* that Bluetooth needs the phone close. Its rows dropped the same
  words (said once); outside the group (Settings › Source's summary) Android adds them back.
- **This doc's motion table** said a picked source row expands and the others dim; the build keeps every row's
  description so the sources compare side by side (corrected in Appendix C).

Discarded or deferred, with reasons:
- **The source row expanding on pick and the others dimming** (Appendix C's first plan): hiding what the other sources
  bring takes away the comparison; the radio's own fill is the feedback.
- **The sensor-end notification and the status-bar line** (*Sensor ended · Replace sensor*, its *Replace sensor* action):
  they follow the sensor Sukrara reads (its reminders); an online source's sensor-end reminders and their words come
  with the manual start (decision 10).
- **A full-width push on the Mac**: the step left fades in place instead of sliding out (a SwiftUI removal keeps the
  transition it was drawn with, so its direction could be the previous move's); the new step's 40 pt slide gives the
  direction.

**Step 2's live test (2026-11-18, the owner's phone and Mac; a new Libre 3 started 18:13 by the Libre app, Spanish
edition; LibreLinkUp the source on both).** Found and fixed:
- ***Continue* did nothing** on Android's Connect step until a live reading arrived (`completeSensor` waits for
  `ownSensorLive`), so it looked enabled and stayed during the warm-up (Nightscout's step too). Now *Continue* answers
  the step once connected (`SetupMachine.completeConnect`); the health line says the rest. The Mac's was already
  gated on LibreLinkUp's answer.
- **The health line said *Nothing new from LibreLinkUp: its last reading was 4 hours ago… it may have stopped
  uploading*** during the warm-up, because the newest reading was the old sensor's. `health.json` gains `warmingUp`:
  with the source's sensor in its warm-up, an answer reads *Sensor warming up · ready at 19:13*, then *Sensor almost
  ready · waiting for the first reading* (both apps, both online sources). The status isn't *Live* then (Android
  `SourceStatus.Connecting`); home already showed the card's warm-up.
- **Mac: *Reading received* for any stored reading**, the old sensor's 4-hour-old one included (Android says it only for
  a live one). Now it shows only for a live or late reading; Nightscout's step too.
- **Android rang *No glucose data* for a moment at the first reading after the warm-up** (19:14:21, with the old
  sensor's 13:38 value, cleared 1 s later when *Very high* fired for 256 mg/dL). The source shows the sensor running as
  soon as it has the reading, and the alert runtime's window for a running sensor had no warm-up
  (`AlertRuntime.windowOf`), so it fed the engine a running sensor before the reading. A sensor just out of its
  warm-up now keeps its start and warm-up end there (`windowAfter`, same sensor id), and the engine ends the warm-up at
  the first reading it gets (`AlertWarmupEndTest`). The Mac didn't do this: it rang *Very high* at 19:14:09 and nothing
  else.

Answered:
- **LibreLinkUp serves whatever reaches LibreView, Sukrara's upload included.** The old sensor's readings up to 13:38:25,
  which Sukrara's LibreView upload had sent (last at 13:38:30), came back from LibreLinkUp: on the Mac, a live 13:38:25
  reading and graph points to 13:36. On the phone the rank rule kept Juggluco's copies. At 15:04 the upload found the
  Libre app uploading and paused (`pausedForLibreApp`).
- **The warm-up Live Update** was posted 45 s after LibreLinkUp gave the sensor's start, then swiped 9 s later
  (`REASON_CANCEL`), so by design it is gone for that sensor. There is no way back after a swipe:
  [#226](https://github.com/mauriciabad/sukrara/issues/226).
- **The widgets don't redraw during a warm-up** (any source: the redraw follows the newest reading's freshness steps
  only). The cause and a repro are in #226's comment.

**Step 2's live test, follow-ups (2026-11-18, both apps).** The test measured a reading's way from the sensor to
Sukrara: the phone a median 26 s (worst 45), the Mac 12 s (worst 17), while LibreLinkUp had each reading within
about 4 s. Both apps asked every 60 s, so the delay was wherever that minute happened to fall.

Added:
- **Calls timed to the next reading** (`contracts/sources/librelinkup/poll.json` `timed`, `margins`, `sequences`;
  `LluPoll.timed` / `LluPoll.timing` on both apps, the same cases): after an answer the next call goes 8 s
  (`margin_s`) after the next reading is due: the newest live reading's time + 60 s, so it follows the sensor's own
  minute and its drift, and a reading the sensor missed changes nothing (the one after is still due on that minute).
  Never sooner than 45 s after an answer, so it stays one call a reading, 60 an hour, as the fixed minute was.
  Readings arriving late: a new reading more than a minute old when found missed its call, so the margin grows 5 s
  (at most 40); ten in a row found in time take a second back, down to 8 (a lag that lasts, or this clock ahead of
  the Libre app's phone, is followed; one late upload changes nothing, because the next reading is found in time).
  Failures, the 429 wait (10 min at least, nothing calls during it), the token reuse and the 30-min connections
  read are as they were. `generate.py` runs the rule against LibreLinkUp behaving in eight ways and fails above
  60.5 calls an hour (`poll.json` `simulation`, the fixed minute beside it from each second of the minute):

  | LibreLinkUp | calls / h | delay median · worst | the fixed minute: median · worst |
  |---|---|---|---|
  | each reading 4 s after it (the owner's) | 60 | 8 s · 13 s | 35 s · 64 s |
  | 1–7 s after it | 60 | 8 s · 13 s | 34 s · 65 s (16 readings an hour never shown live) |
  | the sensor misses one reading in 10 | 60 | 8 s · 13 s | 35 s · 64 s |
  | a slow uploader, 25 s after it | 60 | 27 s · 84 s (1 an hour never shown live) | 56 s · 85 s |
  | one 40 s late upload in 15 | 60 | 8 s · 13 s (that one never shown live, as before) | 35 s · 64 s |
  | this clock 20 s ahead, the sensor's minute 60.3 s | 60 | 7 s · 63 s | 35 s · 64 s |
  | each call takes 1.5 s | 60 | 9.5 s · 14.5 s | 34 s · 64 s |

  Expected on both apps: about 9 s from the sensor (8 s + the call), the worst a few seconds more; a reading
  LibreLinkUp gets late shows at the next call, as before. The graph's 5-minute points still fill what a live
  reading missed. The fake (`tools/librelinkup-probe/fake.py`) has a reading 4 s after it by default (it was 60 s,
  from the old guess that the Libre app uploads a minute late).
- **The Mac's dev run draws Sukrara's icon**: `swift run` isn't the bundle, so macOS gave it a generic icon and *Their
  Sukrara* showed a folder in reviews. An unbundled launch now takes the brand's `AppIcon.icns` from its checkout
  (`BrandIcon`, the file `bundle.sh` puts in the bundle; `sukrara-snap` uses the same).
- **Snapshot and test runs leave no settings file**: each run made UUID-named UserDefaults suites
  (`app.mauri.sukrara.snap.<UUID>`, `app.mauri.sukrara.tests.<UUID>`, `sukrara-focus-<UUID>`; about 40 a snapshot
  run) that cfprefsd writes to ~/Library/Preferences at the first value, and nothing removed them (995 + 2,031 + 105
  had piled up). They now keep settings in memory (`InMemorySettingsStore`, moved to `SukraraCore`; the settings
  only ever go through `SettingsStoring`). `LeftoverSuitesTests`: building every snapshot scene and the tests'
  scratch settings adds no file there, and only the app's launch (`AppServices`) makes a suite.

Discarded:
- **Removing each suite when done** (`removePersistentDomain(forName:)`): a run stopped by a crash, a failed
  expectation or Ctrl-C still leaves its file, and every place that makes one has to remember. **A volatile domain**
  (`setVolatileDomain`): `UserDefaults.set` writes the suite's persistent domain whatever volatile domains exist, so
  it doesn't stop the file. In memory nothing reaches cfprefsd at all.
- **A second call when the reading isn't there yet**: it would catch a late reading sooner, but a lag that lasts
  would make it two calls a reading. The margin follows the lag instead, within the same 60 calls.
- **Waiting longer while the Libre app is silent**: fewer calls, but a later first reading when it comes back; it
  stays one call a minute, on the last reading's minute.
- **Others' timing**: GlucoDataHandler asks at a fixed interval after its last request with a delay setting
  ([DataSourceTask.kt](https://github.com/pachi81/GlucoDataHandler/blob/master/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/DataSourceTask.kt):
  `interval` ≥ 1 min, `delaySec`), not after the reading; Sukrara's Nightscout source already timed its calls to the
  next expected reading (`contracts/sources/nightscout/poll.json`), which this follows.

**Decision 10, sensor start and length (2026-11-18, both apps), with the QA review's wear length and the LibreView fix.**

Added:
- **THE rule** (`contracts/sources/sensor-life.json`, 19 cases run by both apps; `sources.json` gains `life`: `full`
  / `start` / `maybeStart` / `none`, and `wear`: the setting with the length): the source's start unless it's ahead of
  now; a manual start only for `maybeStart` / `none` sources (a Nightscout site now; Dexcom Share and an official
  app's notification later), your glucose, never ahead of now; the newer wins, the source's on a tie; the length is the
  source's wear setting; the default warm-up. A new manual start is a new sensor (`manual-<start>`).
- **One wear length per source** (schema `source.libreLinkUpWear`: the number format `wearLibre`, 14 / 15 days, no
  custom, default 15; `source.nightscoutWear`, `source.jugglucoWear`: `wear`, 10 / 14 / 15 days or 1–30, default
  15), remembered across sensors and source changes, through THE number input (on Android its choice dialog when the
  format takes no custom value). The default stays 15 for LibreLinkUp (the QA review: a wrong 14 marks a working 3 Plus
  ended on its last day and keeps no data quiet; a wrong 15 only shows one day too many). Juggluco's length came along:
  its web server says the start, never the length (a G7 read by Juggluco showed 15 days).
- **Manual start** (schema `sensor.manualStart`, the source file on Android): cleared by a source or person change
  (an old start coming back would say *Sensor ended* and keep no data quiet).
- **Android**: the sources apply the rule (`NsSensorLife.effective`; a change applies at once); Settings › Sensor
  shows an online source's sensor from THE sensor state (it showed the sensor module's: *The sensor, read by this
  phone* for a Nightscout site), with *Started*, *Lasts*, *Ends*, the start dialog and the notices; LibreLinkUp's
  Source row *Sensor · Started … · lasts 15 days · serial* opens it; home's *Sensor ended* for a site's sensor and the
  *Sensor ended* notification's *Replace sensor* (and the ended widget's tap) open it while the source is online (they
  opened the new-sensor flow, decision 10's deferred "words and action" of the polish round).
- **Mac** (the Mac doesn't ask whose glucose any more, owner 2026-11-18): the rule applies to whatever it reads
  (Juggluco on the network, Nightscout, LibreLinkUp: AppModel `sourceSensor`, the card and the alert rules' sensor
  window; no sensor reminders on a Mac); Settings › Source › *Sensor* (*Started*, *Lasts*, *Ends*) replaces
  LibreLinkUp's *Sensor* row; a manual start for a Nightscout site with an inline compact date-and-time picker bounded
  to starts still running ([HIG date pickers](https://developer.apple.com/design/human-interface-guidelines/date-pickers):
  a compact field in a form, invalid dates out of reach), cleared by another source or site.
- **The LibreView upload** sends the length the sensor reports in its `sensorstart` record (`LvSensor.reportedWearMin`
  from the Libre 3's patch info, kept when the end moves earlier): a Libre 3 went up as 15 days. None reported (read
  through Juggluco): the family's as before.

The UI's choices:
- **The length through THE number input**: a setting with a few usual values and a range is what it's for (the
  owner's rule); LibreLinkUp's two values show as its choice dialog with the sensors under each (*FreeStyle Libre 3 or
  Libre 2* / *Libre 3 Plus or Libre 2 Plus*), so nobody needs to know the number. Not a switch *Is a Libre Plus?*
  (xDrip4iOS) that only fits LibreLinkUp.
- **The start: the reminder dialog's pattern** (days as radio rows, the time as a row opening THE time picker,
  *Remove* / *Cancel* / *Save*), not M3's date picker then time picker: M3 shows date and time as separate pickers
  ([date pickers](https://m3.material.io/components/date-pickers/guidelines), [time
  pickers](https://m3.material.io/components/time-pickers/guidelines)), and a calendar month is the wrong shape for
  *the last 15 days*, mostly today or yesterday. Only days whose sensor could still be running are offered; the line
  under it says the end live and refuses *later than now* and *already ended* (a start that ends it would keep no data
  quiet) before *Save*, as M3's text fields show errors in place.
- **One place**: Settings › Sensor (docs/44 "Settings": *Sensor: current sensor (from the source or a manual start)*);
  Settings › Source's LibreLinkUp *Sensor* row leads there instead of a second copy of the choice.

How other apps handle the length (checked in their code):
- **xDrip4iOS**: a LibreLinkUp follower switch *Is a Libre Plus?*, off = 14 days, kept for the next sensors
  ([LibreLinkUpFollowerSettings.swift](https://github.com/JohanDegraeve/xdripswift/blob/c268542e64626c564e626658fa9a6b90a059ada4/xdrip/SwiftUIViews/Settings/Followers/LibreLinkUpFollowerSettings.swift#L145-L156),
  [ConstantsLibreLinkUp.swift](https://github.com/JohanDegraeve/xdripswift/blob/c268542e64626c564e626658fa9a6b90a059ada4/xdrip/Constants/ConstantsLibreLinkUp.swift#L20-L24));
  a G7 15-day switch the same way ([releases](https://github.com/JohanDegraeve/xdripswift/releases)). Good: remembered
  across sensors. Bad for the person: a default of 14 ends a 3 Plus a day early.
- **GlucoDataHandler**: one app-wide *Sensor wear-time* text field, default 14, for every source
  ([pref_general.xml](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/mobile/src/main/res/xml/pref_general.xml#L16-L21)),
  expired 2 h after it ([GlucoDataUtils.kt](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/utils/GlucoDataUtils.kt#L248-L262));
  a list 10 / 14 / 15 / 24 days elsewhere ([arrays.xml](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/res/values/arrays.xml#L146-L159)).
  Bad: a typed number with no hint, and one value for every source.
- **Juggluco** reads the sensor, so it knows the length per family
  ([SensorGlucoseData.hpp](https://github.com/j-kaltes/Juggluco/blob/a983788a17a35f8e76d9da5c125996b648b4f27a/Common/src/main/cpp/SensorGlucoseData.hpp#L78-L90));
  its web server's `sensor_status` says only the start, which is why Sukrara asks for Juggluco too.
- **Nightscout** itself: the *SAGE* plugin counts from the newest *Sensor Start* with thresholds in hours set by the
  site's owner, no length ([README](https://github.com/nightscout/cgm-remote-monitor/blob/92d0834219aa771b5837dbcbf1baeb839a200cf6/README.md#L456-L459)).

Safety, covered by tests: a start ahead of now (ignored: life unknown, no data alerts as usual; the dialog refuses
it), a length that makes the sensor already ended (ended: no data quiet; the dialog refuses such a start), the
source's newer start winning over a manual one (and an older one losing), a source switch and a person change clearing
the manual start, someone else's glucose never using one, a stored length that isn't valid reading as the default or
nearest. No alert rule changed (the engines get the same sensor window), so no scenario was added or changed.

Discarded or deferred, with reasons:
- **Writing the manual start to the Nightscout site** as a *Sensor Start* (with *Treatments both ways*): it would make
  every reader of the site agree, but writes the person's guess into their site's record; later, if asked.
- **A manual warm-up length** (G7 30 min, G6 2 h): the first reading ends the warm-up anyway.
- **The source's health line** (*Sensor warming up · ready at …*) during a manual start's warm-up: Android says it
  (the sources use the same life); on the Mac the health line keeps the source's own start (the card shows the manual warm-up).
- **A manual start for LibreLinkUp or Juggluco**: they always say the start; a new sensor shows when the Libre app or
  Juggluco starts it.
- **Dexcom Share and the official app's notification**: their sources come later; their `life` will be `none`, so the
  manual start works for them without UI changes.

**Treatments while the Libre app holds the sensor (2026-11-18, Android; the owner's phone on LibreLinkUp, the Libre 3
app uploading).** The owner logged a NovoPen dose in Sukrara; home's row showed the crossed-out cloud in the error colour
(*LibreView upload stopped*), and LibreView didn't have the dose. Not uploading it is decision 4; the mark was wrong.
Checked in code and tests, and against the owner's LibreView file of 18 Nov (read in place, never copied):
- **The file**: the Libre app (its own reader serial) sent the new sensor's readings from 19:14 (its first after the
  warm-up), seven scans, the test treatment as **two records of the same minute** (20:19 `Long-Acting Insulin` 0.1, and
  a `Notes` row *Test treatment with note*), and sensor events; Sukrara's device has readings 3 Nov 13:38 → 18 Nov
  13:36 and its 24 insulin / 1 carbs records up to 17 Nov, nothing after the pause (as decided). Each uploader shows as
  its own device (the file's `Serial Number`), so the same reading sent by both would be two rows.

Fixed:
- **The mark says it waits, and why**: `UploadMark.WaitsForLibreApp` (☁↑ in the list's grey, not the error colour)
  *Waiting for LibreView: the Libre app uploads now. It goes up once Sukrara reads your sensor and you take over.*;
  `LvEntryMarks.waitsForLibreApp` apart from `stopped`. The LibreView status adds *What you log here waits: once Sukrara
  reads your sensor, tap Take over and it goes up from here.*
- **Glucose from the takeover on**: back from LibreLinkUp the upload kept the glucose start of its first switch-on, so
  the sensor's backfill (24 h) of the hours the Libre app had already sent would have gone up again as Sukrara's device
  (docs/41: LibreView counts it twice). Now the resume moves the start to the takeover; *Send earlier glucose* offers
  the rest with its warning. Treatments keep their start: nobody sent them.
- Verified (`LibreViewUploaderTest` *issue 213*): six days of entries while paused (a pen dose of 8.5 u injected after
  1.5 u priming, long-acting doses, an edit, a delete before upload, a delete of one LibreView had) send nothing, not
  even a sign-in, mark as waiting, and after the sensor takeover and *Take over* all go up in one upload at their own
  times and kinds (8.5 u, never the priming), the edit as its new version, the deleted-before one never, the other
  retracted. Limits: the last 30 days by when the treatment happened (`WINDOW_S`), 100 per upload with the rest at once.
- **Import history from that file** brings the Libre app's treatments: the owner's real file through the import path
  on the JVM (into an empty store, Europe/Madrid: 17 057 readings and 209 treatments of the last 90 days, a second run
  adds 0) gave the test treatment as long-acting 0.1 u and a separate note *Test treatment with note*, both 20:19
  (LibreView's two records). `HistoryImporterTest` *issue 213* (synthetic, the file's shape) adds that a dose Sukrara
  logged and uploaded comes back as the same treatment (same kind ±2 min and amount), never twice. Imported treatments
  never go to LibreView.

- **Can the official app's treatments reach Sukrara?** Not by themselves; the earlier "no cloud carries them" was half
  right. **LibreLinkUp carries none**: Abbott's [FAQ](https://www.librelinkup.com/faqs) says its users don't get notes
  about insulin or food; `/llu/connections/{id}/logbook` holds only scans and alarm events (type, `alarmType`, glucose:
  [DiaBLE](https://github.com/gui-dos/DiaBLE/blob/e6a909c88faeada49f461d30834174cd95db4042/DiaBLE/LibreLink.swift#L880-L912),
  [pylibrelinkup](https://github.com/robberwick/pylibrelinkup/blob/00944c9aff47868c8c3639fe078bc5f79a052b48/src/pylibrelinkup/pylibrelinkup.py#L229-L242));
  no client imports treatments from it ([GlucoDataHandler](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt#L56-L58),
  [nightscout-librelink-up](https://github.com/timoschlueter/nightscout-librelink-up/blob/bff2317f5cc0db4a1a40971bbf601c87ab0e856d/src/index.ts#L212-L238);
  nightscout-connect makes only a *Sensor Start*,
  [librelinkup.js](https://github.com/nightscout/nightscout-connect/blob/4dde1ecdcad1fd923e69d458e5e2faa7bc78e4f1/lib/sources/librelinkup.js#L296-L320)).
  **LibreView keeps them** (the owner's file above) and shows them to the person's own website login: the export file
  (`POST /export`, behind a reCAPTCHA, [libreview-data-export](https://github.com/YtoTech/libreview-data-export/blob/eead4eeb5fe2a9226e486ec561ca316d1b473b49/export-data.py#L37-L40))
  and the Daily Log report (`POST /reports`, client report 5, no captcha; read for glucose only by
  [libre-desktop](https://github.com/sshivaji/libre-desktop/blob/e6f85d2137f2f206d0d40391c28e8bcb5e956a5f/Sources/LibreDesktop/LibreViewClient.swift#L79-L177)
  and [lvconnect](https://github.com/skobkars/lvconnect/blob/b3209dd5401072b76faf9d587d82a77bb6513d4e/index.js#L429-L441)).
  No open-source project reads treatments from LibreView; Juggluco only uploads
  ([Libreview.java](https://github.com/j-kaltes/Juggluco/blob/81f59687334771a44a6e348994f95f0065a671b5/Common/src/mobile/java/tk/glucodata/Libreview.java#L258-L503));
  Tidepool's LibreView driver skips them
  ([libreViewDriver.js](https://github.com/tidepool-org/uploader/blob/cad13ded996051d216d364430024de0b63d87c9b/lib/drivers/abbott/libreViewDriver.js#L88-L96)).
  Whether the Daily Log's `Days[]` holds insulin, food and notes is unverified (the report shows them; no parser reads
  them). *It does ([45](45-libreview-two-uploaders.md), 45b, the owner's account): the time of every rapid dose and
  carbs entry, insulin and carbs as hourly sums, long-acting only by the hour, notes with their minute; for the devices
  asked, so the Libre app's alone. The website's login asks for an SMS code first.*

Discarded or deferred, with reasons:
- **Reading the official app's treatments from LibreView's Daily Log report** by itself: the only automatic path, but
  it needs the website's own login (a second sign-in, `Account-Id`; the extra steps docs/41 found for the device
  nickname), a report format nobody parses for treatments, and it would ask LibreView every few minutes for a few
  days' use. The two-app period is meant to be short: log in Sukrara, and *Import history* brings what the Libre app
  logged, once. Worth a probe if people stay in the two-app setup for long. *Probed in 45b: the format carries them
  (hour sums, long-acting without its minute); the extra step is an SMS or email code, then a trusted-device token; about
  400 requests a day at a 15-minute pace. Still deferred: the owner decides.*
- **Taking over LibreView for treatments only**: decision 4 (it stops the Libre app's glucose, so the source).
- **Joining the Libre app's dose and its same-minute note into one treatment on import**: closer to what was logged,
  but a person's separate note of that minute would fold into the dose; two rows lose nothing. Later if asked.
- **An insulin's or carbs' note to LibreView**: the Libre app sends it as a second record (a custom note of the same
  minute, as the file shows); Sukrara sends notes only for a note (`contracts/treatments/SPEC.md` `n`). Matching the app
  is a contract change (a second record number per version, its retraction): its own issue.

**One person, one link (2026-11-18, Android; contracts/sources/link.json, Appendix G).**

Added:
- `Link.pick` / `Link.leave` / the defaults and `ReadingRank.plan`'s fence as link.json says (`LinkContractTest`).
- *Keep following Ana?* on every path that picks another source while linked (Settings › Source, *Whose glucose* →
  source, setup's source step, *Pair new sensor*), and the leave questions in one SPA dialog (the app's settings
  dialogs: `SettingsDialog` + `DialogRadio`; an M3 dialog may hold a short radio list, so two two-option questions fit
  without a full-screen dialog, and *Stop following* keeps the error colour of the app's other destructive confirms).
- `AppLink`: the settings kept (`copyWearer` / the defaults, *Settings from* back to *Their settings*), the link left
  through one call (`FollowerEngine.unfollow(keepHistory)`, which sends the `leave`), the history kept behind the fence
  (the wearer's entries read-only and never sent; own unsent entries this phone's own) or deleted; *Stop following*
  alone keeps whose glucose, so a kept history is never adopted for the other person. A follow an earlier version kept
  behind another source is left at start. The leave's *not told yet* shows in setup and Settings › Source too.
- Settings › Source: *Reconnect* on the follow; *Also saved on this phone* (*Sign out of LibreLinkUp*, *Forget
  Nightscout site*). Escalation rows and *Settings from* only where they act (search included). Settings › Metrics:
  *Needs more readings…* for a hidden metric. `NonLinkSourceTest` over link.json `after`.

Discarded:
- Keying someone else's readings by source identity on Android (the Mac's way): one person's history at a time with
  the fence gives the same result without a database version (which would wipe every phone).
- *Read another way…* as a separate row while linked: *Where it comes from* already lists every source and asks
  *Keep following?* first, one pattern instead of two.
- A follow row in *Also saved on this phone*: under D a follow is never kept behind another source.

## Decisions for the owner

**Built (2026-11-18): the `leave`.** A follower that stops tells the wearer, hard to lose (Appendix G, *In Sukrara:
the `leave`*). Coming back takes a new invite.

**Owner, 2026-11-18: switching sources with a Sukrara link, decided.** The journey review in Appendix G recommended
*one person, one link* (D); the owner's answers are at its top: D yes, other sources stay visible behind *Keep
following?*, leaving asks which settings and whether to keep the history, *Settings from* loses *Default*, the `leave`
message, no backup source (decision 6 declined). The rules both apps pass: `contracts/sources/link.json`.

**Owner, 2026-11-18, on root (decision 12): parked.** The owner disagrees with Appendix F's verdict: hiding root comes
with every root setup and isn't an issue (the Libre app ran on their rooted phone with it, so a test R1 is
unnecessary); the person owns the sensor and the phone and may take their own data for themselves; makers use their
position to lock people into basic ecosystems. The owner will reconsider later; nothing root-specific is built until
then. **Priorities**: build the easy, high-value parts; deprioritise, or skip when low value, devices the owner can't
test that aren't trivial to implement.

**Owner, 2026-11-18: all as recommended**, 9 too once explained. 11 was researched further ("we didn't truly research
this enough": Appendix E) and needs the owner's answer again. The owner uses only Sukrara with Libre 3 sensors (no
Libre app, no LibreLinkUp). For testing they're fine installing apps on the phone
or emulators and making accounts, as long as their normal setup ends up Sukrara alone.

**Owner, 2026-11-18, on the Mac: no *Whose glucose* question.** "it is pointless to ask that in mac": the Mac never
uploads or shares and has no sensor reminders, so the answer only changed words. The card says *glucose*; when a
name is needed it comes from the source (the Sukrara stream's name, LibreLinkUp's connection, Nightscout's site).
Readings and treatment logs stay apart per person, keyed by the source's own identity (a step towards showing
several people later). Android keeps the question: there it decides sharing, uploads, pens and reminders.

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
   ***Reopened (2026-11-18, [45](45-libreview-two-uploaders.md)):*** *the premise "one uploader per account" was wrong. It
   is one per app identity, so Sukrara can send its treatments as LibreLink while the Libre 3 app keeps the glucose
   (proven live, the Libre app's upload untouched). New recommendation: **treatments as the other identity**, a second
   device "FreeStyle LibreLink" for the clinic, a watchdog on LibreLinkUp, no glucose. Waiting on the owner.*
5. **Name *Source*** (es *Origen*)? *Recommend yes*: one word, the in/out pair with *Share with apps*.
6. **Online sources alert**, late as they are, with *Both ring* as the default and no *Sukrara rings* for them?
   *Recommend yes*: never fewer alarms than the official app alone.
7. **Mac standalone**: online sources, treatments on the Mac (two-way with Nightscout), no uploads, *Do you have Sukrara
   on your phone?* first? *Recommend yes*: the menu-bar case without a second treatment log.
8. **Move suggestion** (*since corrected: the move works mid-sensor, so the suggestion comes on any calm day; see The
   move*) only at a sensor change, tested families, ≥ 7 days at ≥ 95 %, twice at most, never on the Mac,
   for a loop or someone else's? *Recommend yes*; say if you want it sooner or never.
9. **Re-sharing someone else's glucose** to Sukrara followers (grandparents via a parent)? *Recommend not now*: nobody
   asked; consent and loops. *Owner: OK.* A parent who wants the child's glucose on their own Mac too signs in to the
   Mac with their own LibreLinkUp (or Dexcom Share, Nightscout) account: *Someone else's · LibreLinkUp* (decision 7);
   phone and Mac each read the account. Their treatments stay on the device they were logged on. To verify in step 2:
   whether one LibreLinkUp account can stay signed in on two devices at once.
10. **Manual sensor start** for sources without sensor life? *Recommend yes*: small, and reminders work for every source. *Built*: "In Sukrara", decision 10.
11. **Dexcom / Abbott partner APIs**? *Recommend don't apply; send a Data Act request instead* (Appendix E): real time
    is Dexcom's invitation-only API for companies with a server holding the secrets, and Abbott has no program; the EU
    Data Act gives the person their data "continuously and in real-time" where feasible, so one email per maker opens
    a serverless path or puts a refusal on record.
12. **Root**: *parked by the owner* (see the top of this section). Appendix F's recommendation (only an app's
    unencrypted store read-only, never hooks, lifted keys or interception) is not accepted; the owner decides later.

**What you could test for us**

- **The Libre app's notification**: does it show the number (and arrow)? Tell us, or send a crop without values.
  Decides decision 2. On an unrooted phone: Abbott says the app stops on a rooted one (Appendix F, R3).
- **Root, 15 min on your phone** (Appendix F, R1 + R2): does the Libre app start here, and is its store still
  encrypted? No sensor touched. And **a rooted Eversense user** for the same check on their app (decision 12).
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
| Dexcom public API (v3) | both | **1 h late in the US, 3 h elsewhere** ([Mercury](https://mercurydevelopment.com/what-we-do/all-services/articles/dexcom-cgm-api-integration-guide-2026/), [Momentum](https://www.themomentum.ai/blog/dexcom-api-integration-developer-guide); Tidepool's FAQ confirms the hour) | yes | readings, events (carbs / insulin entered in the app), calibrations | OAuth2; sandbox / 5-user limited / partner | official; real time only for invited partners ([Sugarmate](https://x.com/sugarmateapp/status/1469163420855726080), Appendix E) | Dexcom-API-Wrapper |
| **Nightscout** (self-hosted, T1Pal, Nightscout Pro; fed by xDrip+, AAPS, Loop, Trio, Juggluco, nightscout-connect…) and **Gluroo Global Connect** | both | push via socket.io `dataUpdate` ([websocket.js#L150](https://github.com/nightscout/cgm-remote-monitor/blob/92d0834219aa771b5837dbcbf1baeb839a200cf6/lib/server/websocket.js#L150)) or poll; as fresh as its uploader | yes (or LAN) | **treatments read and write** (deletions in API v3 history), devicestatus (IOB / COB, loop), profile, sensor start | URL + token / API secret | open, stable; no legal risk | GDH `NightscoutSourceTask.kt#L31-L33`, xDrip+ NSFollow, Nightguard, LoopFollow, xDrip4iOS; [Gluroo](https://gluroo.com/blog/glucrew/blood-sugar-readings-smartwatch-gluroo/) |
| Tidepool | both | not live: Dexcom ≥ 1 h (US), Libre cloud to cloud (US, cadence undocumented) | yes | treatments, pump data | Tidepool account | official | **exists** (Import history, upload) |
| Medtrum EasyFollow / EasyView | both | ~2 min ([Medtrum FAQ](https://www.medtrum.com/supports/question)) | yes | pump data | a **separate follower account** following one person | unofficial | [MedtrumSourceTask.kt#L46-L51](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/MedtrumSourceTask.kt#L46-L51), xDrip4iOS, nightscout-clock |
| Eversense DMS / Now | both | backfill only (ESEL) | yes | — | Eversense login | unofficial; no open live client | ESEL ([AAPS Eversense](https://androidaps.readthedocs.io/en/latest/CompatibleCgms/Eversense.html)) |
| Sibionics (SiBCompanion) | both | live-ish | yes | — | follower account | **no open implementation** ([GDH #353](https://github.com/pachi81/GlucoDataHandler/issues/353)) | — |
| Glooko / diasend | both | batch | yes | pump, Omnipod 5, CamAPS FX | Glooko login | "Experimental"; diasend "obsolete" ([nightscout-connect](https://github.com/nightscout/nightscout-connect)) | nightscout-connect |
| CareLink | both | live-ish | yes | pump, IOB | | **#216, out for now** | xDrip+, xDrip4iOS, nightscout-connect, `third_party/carelink-bridge` |
| Tandem Source | both | batch | yes | pump | | unofficial | `third_party/tconnectsync` |
| Abbott partner API (*Libre Data Sharing API*) | — | unpublished | | | partners only, no public program (Appendix E) | partners are companies; no answer to the community (Appendix E) | — |

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
| A treatment logged in the official app | doesn't arrive by itself (LibreLinkUp doesn't carry it; LibreView's file does, through Import history); *Log treatments in Sukrara*, once |
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
| Source picked | the radio fills; every row keeps showing what it brings and its delay, so the sources compare side by side (nothing dims or expands) | tick |
| Step to step (setup) | M3 shared axis X ([Material motion](https://m3.material.io/blog/android-material-motion)): forward slides in from the end, Back from the start, 30 dp with a fade through; Mac: the step pushes in from the trailing edge (Back: the leading one) and fades in, the step left fades out | none |
| First reading from a new source | value counts up from *—*; the source line cross-fades *Waiting…* → *LibreLinkUp · 1 min* | confirm |
| The move's switch | home's source chip morphs (icon cross-fade, width spring); the graph doesn't redraw | confirm |
| Late / offline / back | status dot colour tween, the line slides; *Back online* collapses after 3 s | none (alerts own the strong haptics) |
| Checklist step ticks | the existing self-ticking check pop | confirm |
| Mac | SwiftUI springs; `NSHapticFeedbackManager` `.levelChange` on the first reading and the switch | |

All honour reduced motion (fade / snap; setup's steps cross-fade: [HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion),
Apple's *Prefer cross-fade transitions* for screen changes, [WWDC19 244](https://developer.apple.com/videos/play/wwdc2019/244/)) and announce the new state once (TalkBack / VoiceOver live region).

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
- **Its stored readings (root, later)**: *Read its stored readings* on the source's page, off by default → root
  prompt (today's `AccessStepBanner` states) → on; *Root is off* / *The app changed* / *Its readings disagree* →
  notification only, said on the source line, with *Allow root* / *Check again*; turning it off is the exit.
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
| `source.officialApp.storedReadings` | bool | android `only` (root) | later (decision 12) |
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

Root (later, decision 12)
41. Root lost after an OTA: the source keeps the notification, says *Root is off*, `no_data` only if both stop.
42. The official app updates: a version not checked yet reads the notification only until its store check passes.
43. A stored value disagrees with the notification (±1 mg/dL, ±2 min): the store is dropped for that version, never
    alerted from.
44. Reading never stops, locks or backs up the official app (a backup once lost LibreLink data unless restarted).
45. A rooted phone with the Libre (or MiniMed, Simplera, SmartGuide) app as the source: the setup's warning shows once,
    *Only the Libre app* isn't offered, and the app's silent stop reaches `no_data`.

## Appendix E: partner and official access (decision 11)

*2026-11-18, for the owner's "we didn't truly research this enough". Desk research; Reddit is unreachable from the
research tools, so developers' accounts come from GitHub and forums.*

**In short.** Real time from a maker exists only as Dexcom's **Partner Web API**: by invitation, every known partner a
company, and Dexcom requires the client secret and tokens on the partner's server. Abbott has a partner API with no
public program; the community asked in 2026 and got no answer. Every hub the official apps write to is ≥ 1 h late by
design. The route that fits Sukrara (no server, no company, the person's own data) is the **EU Data Act**: since
2025-09-12 the person may ask for their data "continuously and in real-time" where relevant and feasible. Abbott's
notice offers a CSV; nobody is known to have asked for a live feed.

### What exists, per maker

| Maker | Official access | Fresh | Who gets it |
|---|---|---|---|
| Dexcom | **Partner Web API**, cleared with the G6 in July 2021 ([Dexcom](https://investors.dexcom.com/news/news-details/2021/FDA-Clears-Dexcom-Real-Time-APIs-for-Third-Party-Apps-and-Devices/default.aspx)); allowed client uses include "CGM secondary display alarm … and treatment decisions", not AID ([510(k) K201328](https://www.accessdata.fda.gov/cdrh_docs/pdf20/K201328.pdf)). Runs on Share: Happy Bob needs the Dexcom login and Share on ([FAQ](https://happybob.app/user-faq/)) | real time | "invited third-party developers" |
| Dexcom | **API v3** (OAuth): egvs, events, alerts, calibrations, devices ([docs](https://developer.dexcom.com/docs/)) | 1 h US, 3 h elsewhere (Appendix A) | sandbox: anyone; *Limited*: 5 users, individuals may apply; *Full*: questionnaire, "technical and commercial review", non-negotiable Data Licensing Agreement, a published app with an active user within 60 days; Dexcom "reserves the right to decline" ([Scopes & Access](https://developer.dexcom.com/docs/dexcom/scopes-access)) |
| Dexcom, Eversense | Apple Health (both), Health Connect (Dexcom) writes | 3 h ([Dexcom](https://www.dexcom.com/en-gb/faqs/how-do-i-share-my-glucose-data-s-health-app), [Eversense](https://www.eversensecgm.com/faq/?page=7)) | anyone |
| Abbott | **Libre Data Sharing API** (a component on [Abbott's status page](https://status.freestyle.abbott/), Europe included); the Libre app's *Connected Apps* list | unpublished; Happy Bob (US): "slight delay" | partners only: "scopes and onboarding are not public" ([open-wearables #1897](https://github.com/the-momentum/open-wearables/issues/1897)); the [integrations page](https://www.diabetescare.abbott/partnerships/integrations/en.html) gives no process; only LibreLink is "tested" ([Abbott](https://www.freestyle.abbott/en-jo/support/which-apps-are-approved-for-freestyle-libre-sensors-.html)) |
| Abbott | Apple Health / Health Connect | the Libre apps don't write them ([Sahha](https://sahha.ai/blog/otc-cgm-stelo-vs-lingo/), unverified by Abbott) | — |
| Medtronic / MiniMed | no public API; a Glooko deal ([2015](https://www.meddeviceonline.com/doc/medtronic-unveils-diabetes-partnerships-with-samsung-glooko-0001)) that Glooko's support later called [not compatible](https://support.glooko.com/hc/en-us/articles/360008951777-Is-Glooko-compatible-with-Medtronic-CareLink-devices-) | — | — |
| Roche SmartGuide (in mySugr since 2026-06) | "no direct interface … to third-party software" ([Roche](https://www.accu-chek.com.hk/en/support/smartguide-cgm)); platform API "documented but not public" ([Randine 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC11954377/)); mySugr syncs to Apple Health / Google ([Roche UK](https://www.accu-chek.co.uk/app-switch-mysugr), cadence unverified) | — | — |
| Sibionics, Medtrum, i-SENS, Ottai | no public API found; follower clouds read only by unofficial clients ([GDH #353](https://github.com/pachi81/GlucoDataHandler/issues/353), [nightscout-clock #106](https://github.com/ktomy/nightscout-clock/discussions/106)); CareSens Air writes Apple Health ([App Store](https://apps.apple.com/au/app/caresens-air/id1605701892)) | — | — |
| Microtech (Aidex / LinX) | the app's broadcast (a vendor setting, Appendix A) | live | the phone |
| Tidepool (nonprofit) | Dexcom: US only, newest ≥ 1 h old, polled every 45–75 min ([FAQ](https://support.tidepool.org/hc/en-us/articles/21415013012756-Dexcom-Connection-Frequently-Asked-Questions-FAQs)); Abbott cloud-to-cloud since 2025-07-28, US implied ([blog](https://www.tidepool.org/blog/abbott-freestyle-libre-integration-launched)) | ≥ 1 h | its users; Sukrara imports it already |

A 2025 study of the 16 diabetes devices and apps approved in Norway found one public API among them (Dexcom's,
delayed) and no real-time access without DIY tools ([Randine 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC11954377/)).

### Who got access, and how

| Who | From | What | How |
|---|---|---|---|
| Garmin | Dexcom | first Partner Web API client (2021-10): values and trend on watches; "intentionally does not include any of its own alerts" ([DiabetesMine](https://www.healthline.com/diabetesmine/garmin-and-dexcom-diabetes-data)) | invited ([MedCity](https://medcitynews.com/2021/10/dexcom-taps-garmin-for-first-real-time-api-integration/)) |
| Livongo, Welldoc, UnitedHealthcare Level2 | Dexcom | real time in coaching platforms | commercial partners ([MedCity](https://medcitynews.com/2021/10/dexcom-taps-garmin-for-first-real-time-api-integration/)) |
| **Happy Bob** (Harald AI Oy, a Finnish startup) | Dexcom | real time, G6 / G7 / ONE+ (not Stelo); US 2022-03, Europe and Canada 2022-12 ([Dexcom](https://www.dexcom.com/en-gb/press-releases/happy-bob-expands-to-europe-and-canada)) | Partner Web API; registered as an "FDA Class 2 Exempt device" ([manual](https://happybob.app/user-manual/)) |
| eddii | Dexcom | real time and low / high alert calls, US ([Dexcom](https://www.dexcom.com/en-us/partnerships/digital-health-apps/eddii)) | partner |
| Sugarmate (Tandem) | Dexcom | its "legacy channel" cut in 2021-10 outside the US, 2021-11 in it; moved to the Partner API ([FUDiabetes](https://forum.fudiabetes.org/t/sugarmate-losing-dexcom-g6-data-access/11962)); left the App Store outside the US in 2022 ([20](20-publishing.md)) | owned by a pump maker |
| Oura | Dexcom Stelo | US, latency unpublished ([Oura](https://support.ouraring.com/hc/en-us/articles/41171836791443-Stelo-by-Dexcom-Integration)) | a Dexcom investment ([MD+DI](https://www.mddionline.com/wearable-medical-devices/dexcom-ura-move-into-next-phase-of-partnership)) |
| One Drop, Tidepool, Rimidi, Evidation, Undermyfork… | Dexcom | the delayed API ([Dexcom, 2017](https://investors.dexcom.com/news/news-details/2017/DexCom-Opens-Data-Platform-and-Launches-Developer-Program-to-Fuel-Diabetes-App-Innovation/default.aspx)) | developer program |
| Tidepool, Omada, CCN Health, Epic; Thryve, Junction (aggregators) | Abbott | LibreView data, mostly US, latency unpublished ([CCN](https://ccnhealth.com/articles/announcements/ccn-health-freestyle-libre-integration), [Epic](https://abbott.mediaroom.com/2025-04-29-Abbott-Integrates-Libres-Data-with-Epics-Electronic-Health-Record-System,-Providing-Healthcare-Professionals-Seamless-Glucose-Monitoring-Information), [Thryve](https://www.thryve.health/features/connections/abbott-freestyle-libre-integration)) | contracts; Junction joins the person's LibreView as a *practice* ([docs](https://docs.junction.com/wearables/guides/abbott-libreview)) |
| Tandem, Insulet, Beta Bionics, Ypsomed / CamAPS, MiniMed, twiist | Abbott, Dexcom | the sensor over Bluetooth inside a cleared pump system | device partnerships ([Abbott](https://www.diabetescare.abbott/partnerships/integrations/en.html)) |
| Gluroo, xDrip+, GDH, LoopFollow, Nightscout tools | — | Share / LibreLinkUp with the person's login; Gluroo calls it the "Follow API" ([Gluroo](https://gluroo.com/support/gluroo-with-diy-loop-artificial-pancreas-system/)) | no partnership found |
| LibreLinkUp community, 2026-05 | Abbott | asked for third-party API access: the hotline "didn't have any idea what to do", emails unanswered ([#228](https://github.com/timoschlueter/nightscout-librelink-up/discussions/228)) | — |

No individual or open-source project with real-time partner access was found. The only door open to individuals is
Dexcom's *Limited Access*, on the delayed API.

### What a partnership asks of Sukrara

| Requirement | Evidence | Sukrara |
|---|---|---|
| A business that passes a commercial review | Full Access review; "commercial and technical standards" ([Dexcom](https://developer.dexcom.com/docs/dexcom/scopes-access)) | one person, free |
| A server holding the secret and tokens | "Dexcom requires that partners store tokens (e.g., client secret, access tokens) on their servers"; mobile apps "should not store tokens on the mobile device" ([Dexcom](https://developer.dexcom.com/docs/dexcom/authentication)) | no server, no account; a GPL app can't hide a secret |
| A non-negotiable licence | the Data Licensing Agreement isn't public | compatibility with GPL redistribution unknown |
| A regulatory position on alerts | Dexcom's 510(k) names the client uses; Happy Bob is registered under [21 CFR 862.1350](https://www.ecfr.gov/current/title-21/chapter-I/subchapter-H/part-862/subpart-B/section-862.1350) (secondary alarm, class II exempt); in the EU, software whose information drives treatment falls under MDR rule 11 ([MDCG 2019-11](https://health.ec.europa.eu/system/files/2020-09/md_mdcg_2019_11_guidance_en_0.pdf)). What Dexcom asks of a partner: unverified | not a medical device, and says so |
| Health data on its server | GDPR controller for special-category data | "Data Not Collected" ([20](20-publishing.md)); the privacy policy says no server |

Brand rules, insurance and fees beyond free registration aren't public. The account terms bind every route: Dexcom's
2025 terms give a "personal, non-commercial, revocable licence to … display in the Dexcom App" and, since at least
2023, bar enabling "unauthorized third-party applications"; LibreView bars automated access ([Diabettech](https://www.diabettech.com/whose-data-is-it-anyway/)).

### EU law

| Law | Gives the person | Real time | Limits |
|---|---|---|---|
| **Data Act** ([2023/2854](https://eur-lex.europa.eu/eli/reg/2023/2854/oj)) art. 4(1), since 2025-09-12; CGMs are connected products, their apps related services ([White & Case](https://www.whitecase.com/insight-alert/eu-data-act-unlocking-data-healthcare-life-sciences-and-beyond)) | readily available data, free, machine-readable, on "a simple request through electronic means" | **"where relevant and technically feasible, continuously and in real-time"** | only where the user can't access the data directly from the product or service; whether an app counts is "debatable" ([Freshfields](https://technologyquotient.freshfields.com/post/102jis5/key-insights-for-businesses-from-the-eu-commissions-faq-on-the-eu-data-act)). Restrictable where access "could undermine security requirements … resulting in a serious adverse effect on the health, safety" (4(2)); trade-secret refusal (4(7)–(8)), which the stalled Digital Omnibus would loosen ([CADChain, 2026-10](https://cadchain.com/tpost/data-act-trade-secrets)); no circumvention (5(5)) |
| Data Act art. 5–6, 9 | the same, to a third party the person picks | same | the third party may owe "reasonable" compensation, at most cost for an SME (9(4)); it may use the data only as agreed with the person |
| Data Act art. 3(1) + 50 | design for direct access, for each unit placed on the market from 2026-09-12, so today's sensors ([FAQ v1.3](https://service.betterregulation.com/sites/default/files/2025-09/Frequently%20Asked%20Questions%20-%20Data%20Act%20-%20Version%201.3_3.pdf)) | "where relevant and technically feasible, directly accessible" | |
| Abbott's [Data Act notice](https://www.freestyle.abbott/ie-en/home/data-act-notice.html) | glucose is product data; sensors send to the app at most once a minute; on request, data "generally … in CSV"; contact: Customer Service by phone | **not offered** | |
| Dexcom | no Data Act notice found ([Diabettech](https://www.diabettech.com/whose-data-is-it-anyway/)) | | |
| GDPR ([2016/679](https://eur-lex.europa.eu/eli/reg/2016/679/oj)) art. 15, 20 | a copy; portability of observed device data, sent to another controller where feasible | no: within a month (12(3)); no duty to build compatible systems (recital 68) | |
| EHDS ([2025/327](https://eur-lex.europa.eu/eli/reg/2025/327/oj)) | access to summaries, prescriptions, images, labs, discharge reports from 2029 / 2031 | no: CGM streams aren't a priority category | |

Enforcement: the national Data Act authority (art. 37), the data protection authority for personal data. No CGM user
is known to have asked for a live feed under the Data Act.

### Options

| Route | Takes | Likely answer | Verdict |
|---|---|---|---|
| Dexcom real-time API | Full Access, then an invitation; a business, a server for secrets and tokens, a licence, a stance on alerts | no or silence | **no**: contradicts "no server" and "not a medical device"; only if Sukrara ever has both |
| Dexcom API v3 | Limited (5 users) or Full, and a server | likely yes | **no**: 1–3 h late, like Health Connect and Tidepool |
| Abbott partner access | no channel; partners are companies | silence ([#228](https://github.com/timoschlueter/nightscout-librelink-up/discussions/228)) | **no** |
| Medtronic, Senseonics, Roche, Sibionics, Medtrum, i-SENS, Ottai | no program found | — | **no**; Aidex's broadcast is already in Appendix A |
| **Data Act request** from the person (art. 4, with GDPR art. 15 for the one-month clock) | one email from an EU user of the official app: you (Libre sensors, with the Libre app test setup you offered) and a Dexcom tester | Abbott: its CSV, app or LibreView; Dexcom: unknown | **yes**, one per maker: a yes is an official path with the person's own login and no server; a no is a written refusal for a complaint. Nothing changes in Sukrara until then |

**Draft** (Abbott: [EU-dpo@abbott.com](https://www.freestyle.abbott/ie-en/privacy-policy.html), as the notice gives
only a phone line; Dexcom: the DPO, Dexcom Deutschland GmbH, Mainz, per its [privacy policy](https://www.dexcom.com/en-us/legal/privacy-policy),
with "G7", "Dexcom app" and "Share").

> **Subject:** Data Act request: continuous access to my FreeStyle Libre data
>
> I use FreeStyle Libre 3 sensors in [country]; my LibreView account is [email]. Under the Data Act (Regulation (EU)
> 2023/2854), article 4(1), I request access to the readily available data from my sensors and the Libre app (glucose
> readings with their times, trend and sensor state) in a machine-readable format, continuously and in real time. This
> is relevant and feasible: your Data Act notice says the sensors send data to the app up to once a minute, and
> LibreLinkUp shows it live to followers. A CSV on request isn't continuous. I ask for an authenticated interface I can
> call with my own credentials from software I choose, on my own phone and computer, without a third-party server.
> Please also tell me how I can access the data directly from sensors placed on the market after 12 September 2026
> (article 3(1)).
>
> Please treat this also as a request under GDPR article 15. If you restrict or refuse any part, please give the legal
> ground in writing (articles 4(2), 4(7)–(8)).
>
> [name]

## Appendix F: root (decision 12)

*2026-11-18, for the owner's "root must be explored … i have the right to get my glucose data". Desk research and open
code only: no vendor app downloaded, decompiled or run, no device touched. Described as features; no key-extraction or
interception procedure is written here.*

**In short.** The premise, checked: root lets one app read another's files and change how it runs, but it doesn't make
"beside the official app" work almost always. **The official apps of Abbott, Medtronic and Roche refuse rooted
phones** (Abbott: the Libre app "stops without notification", so no alarms); Dexcom says not to (F.4). The official app
then runs beside root only where the person hides root from it, which Sukrara won't advise and an update can end any
day. Open-source projects tried four of the six doors below, and **none ships a live root source beside an official app
today**: the DIY world moved to notification reading and the sensor itself. **Libre 3 gets nothing acceptable**: the
Libre app's store is encrypted with a key only it unwraps, its Bluetooth link is encrypted with fresh keys each
connection, and since 3.4.0 it won't start beside LSPosed. What root does give: (1) where an official app still runs
and keeps its readings **unencrypted**, reading them read-only adds what notifications lack (each reading's time,
backfill); Eversense did in 2019, nothing is known for today; (2) a **takeover without LibreView** of a Libre 3 the
Libre app started offline, which is the move, not "beside". Law: your own unencrypted file on your own phone is
grey-low; lifting keys, hooking or intercepting is out.

### F.1 The doors

| Door | Gives | Open prior art | Verdict |
|---|---|---|---|
| **The official app's stored readings, read-only**, where kept unencrypted | each reading with its own time, backfill after a gap, sometimes trend and sensor | ESEL (Eversense, 2017–19), Libre2Link-Nightscout (LibreLink, 2020–22, batch) | **later, per app** that runs on a rooted phone, after a rooted tester's check (R2) |
| **The official app's own ID**, kept in plain settings | the receiver ID of a Libre 3 the Libre app started without LibreView → Sukrara's takeover | Juggluco ([FAQ-022](https://www.juggluco.nl/Juggluco/faq.html#faq-022), [convertUUID](https://www.juggluco.nl/Juggluco/libre3/convertUUID.html)) | **later**, part of the move; small group |
| The official app's **encrypted** store | as the first row | none live; a 2023 write-up opened the Libre 3 app's store once by lifting its per-install key with instrumentation ([frdmtoplay](https://www.frdmtoplay.com/freeing-glucose-data-from-the-freestyle-libre-3/)) | **never**: removes the maker's protection (Data Act 11(1), 4(11); [23](23-sensors.md) rule 2) |
| **Hooks** into the running official app (Xposed / LSPosed) | the value in its notification, a broadcast, a database copy | Free Three (closed source, 2022–23) | **never**: a patched medical app in all but name; the Libre app aborts on LSPosed since 3.4.0 |
| **The Bluetooth link** (HCI log, man in the middle) | the packets the official app gets | none as a source; Juggluco uses official apps' HCI logs only to diagnose sensors ([FAQ](https://www.juggluco.nl/Juggluco/faq.html)) | **never or not needed**: Libre 3 packets are encrypted with keys from an exchange made each connection (Sukrara's client does it, `A/sensor/…/libre3/Libre3Security.kt:21`), so a reader sees ciphertext and a man in the middle must pose as the sensor or the app (rule 2); the G7 gives its packets to the observer without root |
| The official app's internet traffic | its uploads | none; LibreLink 5 encrypts its payload ([#228](https://github.com/timoschlueter/nightscout-librelink-up/discussions/228)) | **never**: online anyway, and needs the app's certificate checks defeated |

### F.2 Per family

| Family | Official app's store (known) | What root adds | Best path |
|---|---|---|---|
| **Libre 3 / 3 Plus** (Libre 3 app, Libre app) | encrypted Realm, one key per install, kept wrapped in the app's settings and unwrapped by its native library (v3.4.2, [frdmtoplay 2023](https://www.frdmtoplay.com/freeing-glucose-data-from-the-freestyle-libre-3/)) | nothing beside it: the app stops on a rooted phone (F.4); only the takeover without LibreView | LibreLinkUp; the notification if it carries the value (Free Three's first feature was adding it, 2022, so it likely didn't; today unverified), on an unrooted phone; the move |
| **Libre 2 / 2 Plus EU** (LibreLink) | plain SQLite `sas.db`: `realTimeReadings` each minute while Bluetooth is on, `historicReadings` every 15 min, UTC ms ([Libre2Link-Nightscout](https://github.com/steve8x8/Libre2Link-Nightscout), [insulinclub 2020](https://insulinclub.de/index.php?thread/32170-howto-daten-aus-libre2-auslesen/)); unverified since 2022 | LibreLink's own calibrated values (Abbott's algorithm already ran, so rule 1 wouldn't bite), but LibreLink crashes on root since 2.5.2 (F.4): only with root hidden from it | LibreLinkUp |
| **Dexcom G6 / G7 / ONE+ / Stelo** | no open project reads it | nothing needed (and the apps warn on root) | G7 family: the observer; G6: notification |
| **Medtronic Guardian / Simplera** | no open project | nothing: both apps refuse rooted phones (F.4), though Simplera's notification lacks the value ([xDrip #3025](https://github.com/NightscoutFoundation/xDrip/discussions/3025)) | Guardian: notification; Simplera: CareLink (#216) |
| **Eversense E3 / 365** | plain SQLite, table `glucosereadings` ([ESEL `Datareader.java#L19-L28`](https://github.com/AdrianLxM/Esel/blob/7d4f1e18078fb753e99730245dd71fcc7fc0dce7/app/src/main/java/esel/esel/esel/datareader/Datareader.java#L19-L28)); unverified since 2019 | the times and backfill notifications lack (notification readers report uneven and 13-min-old readings, [2026](https://gardinerbryant.com/how-i-got-eversense-365-readings-into-home-assistant/)) | notification (ESEL companion, xDrip+); the store later, if plain |
| Sibionics, Medtrum, CareSens Air, Sinocare iCan | no open project | unknown | notification |
| Aidex / LinX, Ottai, Syai | — | nothing needed | their apps send readings themselves ([AAPS](https://androidaps.readthedocs.io/en/latest/Getting-Started/CompatiblesCgms.html)) |
| Accu-Chek SmartGuide (mySugr) | no open project (Free Three changed only mySugr's screens) | nothing: the app blocks rooted phones | none reliable |

### F.3 Projects with a root mode

| Project | Root mode, as a feature | Sensors | Life | What broke it | Code |
|---|---|---|---|---|---|
| **ESEL** (AdrianLxM, then BernhardRo) | reads the newest two readings from the Eversense app's database through `su` and `sqlite3`, sends them to xDrip+ / AAPS; "very experimental" ([README](https://github.com/AdrianLxM/Esel)) | Eversense | 2017-08 → 2019-10 | users didn't want root ([xDrip #616](https://github.com/NightscoutFoundation/xDrip/issues/616), 2018); the fork moved to a patched app with a content provider, then to the notification ([README](https://github.com/BernhardRo/Esel)) | `Datareader.java#L19-L28` |
| **Libre2Link-Nightscout** | copies LibreLink's database to a computer (backup, or root "faster") and uploads it to Nightscout | Libre 2 | 2020-05 → 2022-08; by hand, not live | "RESTART THE LIBRELINK APP TO AVOID DATA LOSS" after a backup | [`db-from-app-root#L24`](https://github.com/steve8x8/Libre2Link-Nightscout/blob/89df5a143f8b1252d75b233850431971602061fa/db-from-app-root#L24) |
| **Juggluco** | recovers the Libre 3 app's own generated ID from its settings, for a takeover | Libre 3 | current | — | receiver ID = FNV-32 of the account ID: Sukrara `ReceiverIds.kt:32-37`, [DiaBLE `LibreLink.swift#L764`](https://github.com/gui-dos/DiaBLE/blob/e6a909c88faeada49f461d30834174cd95db4042/DiaBLE/LibreLink.swift#L764) |
| **Free Three** ([insulinclub](https://insulinclub.de/index.php?thread/33795-free-three-ein-xposed-lsposed-modul-mit-erweiterungen-f%C3%BCr-abbott-libre-3-dexcom/)) | LSPosed module: the value in the Libre 3 app's notification, broadcasts to xDrip+ / Tasker / watches, a database copy; for Dexcom only the device check | Libre 3 (German app, ≤ 3.3.1 per [maheini](https://github.com/maheini/FreeStyle-Libre-3-patch)), Dexcom G6 / G7 | 2022-02 → 2023-08 (v1.19), last post 2024-04; source not published | Libre 3 3.4.0+ "erkennt das geladene LSPosed Modul und bricht den Start ab" (2023-04-17); a mySugr update loaded the wrong hooks (2023-08) ([page 7](https://insulinclub.de/index.php?postID=688775&thread%2F33795-free-three-ein-xposed-lsposed-modul-mit-erweiterungen-f%C3%BCr-abbott-libre-3-dexcom%2F=)) | not published |
| xDrip+, AAPS, GlucoDataHandler, Diabox | **none** beside an official app: xDrip+ uses `su` only on its Raspberry Pi WholeHouse ([`Root.java#L22`](https://github.com/NightscoutFoundation/xDrip/blob/8532095b9333ae9939a95e0d116a198f20392617/app/src/main/java/com/eveningoutpost/dexdrip/utils/Root.java#L22)); AAPS lists no root route ([CGMs](https://androidaps.readthedocs.io/en/latest/Getting-Started/CompatiblesCgms.html)) and only reports root ([`PhoneCheckerPlugin.kt#L45`](https://github.com/nightscout/AndroidAPS/blob/aa16db64196e570b15bf41e85422fc159c058308/plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/phoneChecker/PhoneCheckerPlugin.kt#L45)); GDH reads broadcasts, notifications and clouds ([README](https://github.com/pachi81/GlucoDataHandler)); Diabox reads the Libre 3 itself, root not mentioned ([channel](https://t.me/s/DiaboxApp)) | | | | |

### F.4 Official apps on rooted phones

Makers' own statements first; Reddit unreachable, XDA only as search snippets (unverified).

| App | Maker says | Users report | For the person |
|---|---|---|---|
| **Libre app / Libre 3 app** (also Libre 2, Libre Duo) | "Don't use the App on a jailbroken or rooted device"; there it stops "without notification", so "you won't receive alarms" ([Abbott](https://freestyle.abbott/us-en/safety-information.html), [Libre app manual 2026 p. 12](https://freestyleserver.com/Payloads/IFU/2026/q2/ART48742-103_rev-A.pdf)) | 3.4.0 "crashes only on rooted systems" ([maheini #13](https://github.com/maheini/FreeStyle-Libre-3-patch/discussions/13), 2022-09) | silent stop, **no alarms** |
| **FreeStyle LibreLink** | "not supported on rooted Android phones" ([UK FAQ](https://www.freestyle.abbott/uk-en/support/faq/question-answer.html?q=UKFaqquestion-21)) | 2.5.2 and later "crash themselves if they detect root" ([Juggluco help](https://www.juggluco.nl/Jugglucohelp/settinghelp.html)); "just crashing" ([Fairphone forum](https://forum.fairphone.com/t/fp3-nfc-freestyle-libre-root/67239), 2020-11) | crash, no message |
| **Dexcom G7 / G7 15 Day / ONE+ / Stelo / G6** | "Don't install G7 app on a … rooted (Android) smart device. It won't work correctly." ([G7 guide p. 27](https://www.manualslib.com/manual/2576156/Dexcom-G7.html?page=27)); it "will notify the user when a security issue is detected, such as … rooted" ([G7 15 Day p. 150](https://dexcompdf.s3.us-west-2.amazonaws.com/en-us/G7-15-CGM-Users-Guide.pdf)); same in the [ONE+](https://dexcompdf.s3.us-west-2.amazonaws.com/OUS+Specific+PDFs/AW-1000027-06_REV004_UG_D1Plus_OUS_en_MGDL_r6_WEB.pdf) and [Stelo](https://dexcompdf.s3.us-west-2.amazonaws.com/Stelo/AW-1000421-10+Stelo+User+Guide.pdf) guides | none from rooted phones; G6 users rooted around 2019 to pass the phone list (XDA, unverified) | a warning; whether it blocks: unverified |
| **MiniMed Mobile** (770G / 780G) | "we do not allow the app to run on a device with modified operating system (rooted device)"; Developer Options on blocks it too ([Medtronic](https://www.minimed.com/en-us/support/product-support/780g/troubleshooting-minimed-mobile-app)) | stricter since 1.2.0 (XDA, unverified) | *App Cannot Be Used* |
| **Simplera, Guardian Connect** | Simplera "will display an error message when launched and will not continue to operate" ([IFU p. 14](https://www.accessdata.fda.gov/cdrh_docs/pdf16/P160007S047c.pdf)); Guardian Connect "cannot be used with a rooted device" ([guide p. 11](https://www.minimed.com/content/dam/documents/en-us/Guardian-Connect-System-User-Guide.pdf)) | — | refuses to run |
| **Accu-Chek SmartGuide**, mySugr | "Your device is rooted. … you can't use the app on this phone." ([manual 2025-08 p. 40](https://tools.accu-chek.com/documents/dms/en/smartguide_app_manual/smartguide_app_manual_en_2025-08.pdf)); mySugr: don't use one ([manual 3.150.0](https://assets.mysugr.com/app_logbook/android/3.150.0/manual/us/logbook-android-User_Manual-en_dmi.pdf)) | — | SmartGuide blocks; mySugr warns |
| CareSens Air | "Do not install the app on a smart device that has been tampered with through hacking" ([manual p. 24](https://abmedical.ee/content/uploads/2024/12/CareSens-Air-Manual-CE-EN_240415.pdf)) | — | unknown |
| Eversense, Sibionics, Aidex / LinX, Medtrum, Ottai | nothing for rooted Android (Eversense warns about jailbroken iPhones, [365 guide](https://www.manualslib.com/manual/3652467/Senseonics-Eversense-365.html?page=147); a LinX distributor: "not supported", [sus-wel](https://sus-wel.com/pages/linx-cgm-faq)) | — | unknown |

No maker names Play Integrity; the checks seen are the apps' own (a sideloaded Libre 3 3.4.2 stopped at *Please install
this App from Google Play*, [frdmtoplay](https://www.frdmtoplay.com/freeing-glucose-data-from-the-freestyle-libre-3/)).
Makers also retire old versions (Dexcom: G7 app ≤ 2.8 unusable after 2025-08-20, [FDA](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfres/res.cfm?id=215005),
seen as a summary only), so nobody stays on a version that tolerates root. MiniMed Mobile is blocked by Developer
Options alone: a root reader must not need them (an HCI log does).

### F.5 Law

| Act | Verdict | Why |
|---|---|---|
| Your own readings, on your own phone, in an app with no server | **fine** | GDPR doesn't apply to "a purely personal or household activity" ([art. 2(2)(c)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)); Sukrara never holds the data |
| Reading an official app's **unencrypted** readings or its own ID, read-only, with the owner's root | **grey, low** | no protection of the maker's is removed and no code copied or changed (copyright protects a program's code, not the data it handles: [C-159/23](https://curia.europa.eu/juris/liste.jsf?num=C-159/23), 2024). The Data Act bars "abuse [of] gaps in the technical infrastructure of a data holder which is designed to protect the data" ([4(11)](https://data-act-law.eu/article/4/)): Android's sandbox belongs to the phone, opened by its owner, but no authority or court has said so. The app's terms may forbid it: a contract matter (the account) |
| Opening an **encrypted** store with a key lifted from the app | **not** | users "shall not alter or remove such technical protection measures without the agreement of the data holder" ([11(1)](https://data-act-law.eu/article/11/)); 4(11); rule 2 |
| **Hooking or patching** the official app | **not** (for Sukrara) | whoever "modifies a device already placed on the market … in such a way that compliance with the applicable requirements may be affected" takes on a manufacturer's duties ([MDR art. 16(1)(c)](https://eur-lex.europa.eu/eli/reg/2017/745/2023-03-20)); terms; already *never* |
| **Intercepting** the Bluetooth or internet link | **not** | needs the official app's keys or posing as the sensor or the app (11(1), rule 2) |
| Learning how an app stores readings | **fine within limits** | a lawful user may "observe, study or test the functioning of the program" ([Software Directive 5(3)](https://www.legislation.gov.uk/eudr/2009/24/article/5)); decompiling only where indispensable, when the information "has not previously been readily available", for interoperability only ([6](https://www.legislation.gov.uk/eudr/2009/24/article/6)); terms against these are "null and void" ([8](https://www.legislation.gov.uk/eudr/2009/24/article/8)). Sukrara learns formats from open projects and testers' own files, never by decompiling |
| Data Act 4(1) | **the right, not self-help** | protections "shall not … hinder a user's right to … access data" (11(1)): a refusal goes to the authority (decision 11's request), it isn't a licence to break them |

### F.6 Safety: what a root reader must catch

| State | Cause | Detect | Sukrara does |
|---|---|---|---|
| Root gone | an OTA without reinstalling root ([Magisk](https://topjohnwu.github.io/Magisk/ota.html)), the root manager denies | each read: today's `RootAccess` (`Missing` / `Refused` / `NoAnswer`) | keeps the notification (same source); *Root is off* + *Allow root*; `no_data` only if both stop |
| App updated | a new version | `versionCode` before each read | a version whose store check (tables, columns, value range) hasn't passed reads the notification only: *The Eversense app changed* |
| Format or values changed | new schema, units, time base | each stored value against the notification's (±1 mg/dL, ±2 min) | first disagreement drops the store for that version; never alerts from a value the official app doesn't show |
| Store encrypted | the maker adds encryption | the file's header | the option disappears for that app, with the reason |
| Stale | the app stopped writing or was killed | newest row older than the notification or than two cadences | *Late*, as any source |
| The official app refuses root | an update adds root detection (F.4) | app not running, no notification | *The Libre app stopped on this phone* + *Change source*; `no_data` |
| The reader disturbs the official app | a long lock, a copy mid-write, a backup that stops it | — | read-only, never stop the app, no backup, no lock held |

Freshness: as fast as the app writes (each reading where it writes each reading), with a file watch and a one-minute
poll as the floor. With each reading's time, the SPEC's trend quiets can hold, unlike the notification alone.

### F.7 Fit, and tests

- **Person × Source**: not a new source. *The official app on this phone* (yours) gains *Read its stored readings*
  (root), per app in `sources.json` (`needsRoot`, `exactTime`, `backfill`); the picker shows *Eversense app · Found ·
  exact times with root*.
- **Build order**: steps 0–4 unchanged; root stored readings and the takeover without LibreView under *Later*; hooks,
  lifted keys and interception under *Never*. The first app is the one a rooted tester shows unencrypted (R2).
- **The move**: unchanged for a Libre 3 started with LibreView (Sukrara already takes over a running one,
  `ReceiverIds.forTakeover`); with root also one the Libre app started without it.
- **On a rooted phone with an app that refuses root** (Libre, MiniMed, Simplera, Guardian Connect, SmartGuide): the
  source's setup says once *The Libre app may stop on a rooted phone without warning, and its alarms with it (Abbott)*,
  Sukrara's alerts stay on (*Only the Libre app* isn't offered), and `no_data` covers the stop. Never a hint to hide
  root.
- **The owner's phone** (rooted, Libre 3, Sukrara alone): no root path exists to test beside the Libre app, and Abbott
  says the app stops there. Two short tests confirm it; the notification test (R3) needs an unrooted phone:

| Test | Steps | Proves | Risk to live monitoring |
|---|---|---|---|
| **R1** Does the Libre app run here? (10 min) | install the Libre app from Google Play, open it to its first screens; **don't sign in to a sensor, don't tap the sensor** while it's installed; uninstall | whether today's app stops on root as Abbott says (expected yes), and how: a message or a silent stop | none expected: Sukrara keeps the sensor's one link; not tapping keeps the Libre app from claiming it |
| **R2** Is its store still encrypted? (5 min, before uninstalling, if R1 got past the first screen) | a root shell lists the Libre app's data file names and sizes and tries to open its database read-only as plain SQLite or Realm; nothing copied off the phone, no settings value read | whether the 2023 finding holds (expected yes). Run by a rooted Eversense user on their app, it decides decision 12's first app | none: read-only |
| R3 The Libre app's notification (already asked) | **on an unrooted phone** (a spare, or a tester's): at a sensor change start the new sensor in the Libre app signed in to your LibreView, a day, then take it over in Sukrara on your phone by tap | whether the notification carries the value (decision 2) | medium: a day on the Libre app's alarms on the other phone; a failed takeover leaves the sensor there, still alarming |

## Appendix G: switching sources with a Sukrara link (journey review, 2026-11-18)

**Owner's answers, 2026-11-18** (they settle the decisions below):
- **D, one person, one link: yes.** A device either follows a Sukrara (recommended, the default) or reads an external
  source; never both. "If a user picks an external source it has to be because he doesn't want to install Sukrara on
  his phone"; a phone with no internet while the Mac has it is "an odd edge case, not worth complicating the mental
  model for", and a different person in the Mac's LibreLinkUp than on the phone would "mess everything up". Sukrara on
  the phone is the best way to the Mac: the many inputs and outputs exist so Sukrara can fill gaps in people's setups
  (old sensors through Juggluco, a Nightscout someone keeps, a clinic that sees LibreView), not to run beside itself.
- **The `leave` message: yes, and hard or impossible to lose on its way.** Check and update the open standard draft
  (docs/24, 24b, 24c) with it.
- **Decision 6, the backup source** (an online source used only while the followed phone is silent): **declined** —
  it serves the same edge case.
- **Decision 8: acceptable.** Idea to consider: rejoining with the same invite or identity after leaving, instead of a
  new profile; open to either.
- **2: (a)** other sources stay visible on a linked device; picking one asks first, explaining what the link already
  brings, with *Keep following* (default) and stop-following-and-read-it-here. The copy names whichever source was
  picked or speaks of any source: "not the only service, watch out what copywriting you end up using".
- **4: ask** when leaving which settings this device keeps (theirs as its own, or Sukrara's defaults); **skip the
  question when both answers would give the same settings** (they're the defaults already).
- **5: ask** when leaving whether to keep or delete the history this device received.
- **7: yes**, *Settings from* becomes *Their settings* / *This device's own*.
- Last-seen per follower (3) comes later.

### In Sukrara: the `leave` (2026-11-18)

Built on both apps: PROTOCOL-v2 "Leave" ([spec](../contracts/protocol/PROTOCOL-v2.md#leave--v2-addition-2026-11-18-213),
fixture `contracts/protocol/sync/leave.json`). The link model (*Stop following* with the leave questions, history and
settings kept) leaves through the same call, so every way out of a link sends it.

**Delivery.** Compared, with sources:

| Option | Survives | Loses it when | Verdict |
|---|---|---|---|
| One-shot gift wrap (as `rekey`) | nothing more | the follower is offline or uninstalls; a relay drops it; the wearer is off longer than its TTL | the base, not enough alone |
| Replaceable / addressable state signed by `F` | relays MUST keep the latest version ([NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md)) | — | **rejected**: a public event by a stream key names a follower of the stream on relays (the privacy rule); kind 30078 relays may serve it only to its author ([NIP-78](https://github.com/nostr-protocol/nips/blob/master/78.md), docs/24 P1) |
| Retry until the wearer acknowledges | a relay that drops the stored copy | the follower uninstalls | **rejected**: `F` and a subscription must stay alive while the wearer is off (days). Marmot does this for MLS SelfRemove ([member-departure](https://github.com/marmot-protocol/marmot/blob/master/protocol-core/member-departure.md)), because an MLS leave needs a commit by someone else |
| Bluetooth when nearby | no internet | not connected at that moment | **kept** as the fast path |
| Silence (last seen) | an uninstall while offline | — | **later**, the only answer to that case (owner: later) |

Chosen: a **durable outbox** (the signed wrap only, never `F`), as Marmot's publish lifecycle keeps signed bytes and
per-relay status across restarts ([publish-lifecycle](https://github.com/marmot-protocol/marmot/blob/master/protocol-core/publish-lifecycle.md)):
- the wrap lives **365 days** on the relays: strfry and nostr-rs-relay put no upper bound on NIP-40 expiration
  ([strfry](https://github.com/hoytech/strfry/blob/master/src/events.cpp),
  [nostr-rs-relay](https://github.com/scsibug/nostr-rs-relay/blob/master/src/event.rs));
- it is sent until **two relays** answered `OK true`: one may drop it ([NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md):
  "relays may choose not to store gift wrapped events"; NIP-11's `retention` was removed,
  [nips#1946](https://github.com/nostr-protocol/nips/pull/1946));
- it is sent again at each start, when the network comes back, and on Android from a WorkManager job that waits for a
  network ([WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager): survives reboots, not
  an uninstall);
- over Bluetooth too when the wearer's phone is connected.

The wearer's inbox already takes every wrap to `I` at each connect (no `since`), handles each once, and handling it
again changes nothing. Signal's peer-to-peer v1 leave got lost exactly this way: members kept sending to someone who
had left ([Signal-Android #8443](https://github.com/signalapp/Signal-Android/issues/8443)).

**Coming back: a new invite** (or *Pair nearby*), with a new follower key, as the first time. Reusing the invite is
impossible: invites are single-use bearer tokens, claimed and gone in 24 h. A one-tap re-approval of the same key was
weighed against:
- **consent**: the wearer's Allow stays the one door;
- **security**: a leave lives a year on the relays and the wearer forgets handled wraps after 14 days. A re-approved
  key would need a rule comparing the follower's clock with the wearer's to keep an old leave from removing it again.
  A lost or sold phone would also keep a key the wearer re-trusts in one tap;
- **simplicity**: coming back is a join, with no new message.

So a leave is final for its key. Draft 3 already answers a removed key's join with `ended`, and Dexcom
([G7 guide](https://dexcompdf.s3.us-west-2.amazonaws.com/en-us/G7-15-CGM-Users-Guide.pdf), "Stopped Following You"),
LibreLinkUp ([manual](https://freestyleserver.com/Payloads/IFU/2026/q1/ART53380_001_rev_A_pub/languages/en/x_librelinkup/DOC53380/users_manual.html):
"the person will need to send a new invitation") and Matrix invite-only rooms do the same. It costs the owner's test
loop about a minute (decision 8).

**What the wearer sees.** On the wearer's side:
- the notification *Ana stopped following you* · *They no longer see your glucose or get your alerts.*;
- Settings › Sharing moves them under *Past followers* (was *Removed*) with *Left 18 Nov 2026*. The date is when they
  left, so a phone that was off shows the real day;
- a rotation as for Remove, so the key that left reads nothing new, escalations included;
- the escalation note says *your followers were told* only while someone follows (else *no follower to tell*);
- a waiting request that is cancelled is withdrawn.

The follower's *Stop following* says the wearer's phone is told (Android: the leave questions; Mac: the confirmation,
*Their phone is told you stopped.*). While offline, Android shows the
notification *Ana hasn't been told yet* (after the first 20 s attempt) and the Mac shows a line under *Not following
anyone*.

**Last seen** belongs to a later round. It is the only fix for a follower that uninstalls while offline, and it would
let the escalation note count only followers seen recently. Its cost is a periodic message per follower (or the
Bluetooth `HELLO` times), which the owner put off.

**Discarded**:
- a leave on a source switch (the link model decides);
- a confirmation step beyond the existing one;
- an in-app surface for *hasn't been told yet* on Android: the person lands on setup after *Stop following*; the link
  model places it;
- a leave for *Different code* in nearby pairing: the phone that answered isn't the wearer's;
- a leave for a follow that already ended.

**Open standard**: draft 3 already had a one-shot `leave` (30 days). C§9.5 now carries this delivery and the
wearer's rules, rationale D17 explains why, the reference implements them with two tests, and the vectors are
unchanged ([open-glucose-stream#1](https://github.com/mauriciabad/open-glucose-stream/pull/1), waiting for the
owner's merge). Draft 1's
text is frozen in `poc/spec`; PROTOCOL-v2 is Sukrara's.

*Product / UX / QA journey review of #213 (Person × Source, Nightscout, LibreLinkUp, the polish round), Mac first and
switching between sources on both apps, after the owner found the Mac on LibreLinkUp still linked to their phone with
the follow out of reach. Walked on the real apps: the Mac as a dev fixture run driven through Accessibility; Android on
two emulators (wearer + follower) with the local relay, the fake LibreLinkUp and a Nightscout stub. The owner's Mac
and phone were only read. Screenshots (synthetic data) stayed in the session's scratch folder, not in the repo:
`mac-01` … `mac-12`, `android/00` … `android/27`.*

### Top findings (severity order, one line each)
1. **HIGH, both apps.** A follow left behind by a source switch is invisible and can't be stopped.
   - The Mac keeps it fully live: relays, Bluetooth, the phone's settings and thresholds, the path badge.
   - Android pauses it silently.
   - The wearer's phone shows the follower as a follower in both cases.
2. **HIGH, same person on two devices.** The Mac on LibreLinkUp loses the phone's treatments and pen doses (the owner's NovoPen case). Its own entries reach nothing.
3. **HIGH, Android.** After leaving a stream, alerts, the live notification, widgets and Settings › Source carry the old follow's name. Leo's alert read "Ana" (`android/27`).
4. **HIGH, Android.** Changing *Whose glucose* keeps LibreLinkUp's pick, so someone else's glucose became "yours": it was offered to the clinic share and treated as the wearer's (`android/20`, `android/21`).
5. **HIGH, Android.** "The history on this phone stays" is false.
   - The rank rule lets the new source's backfill overwrite the old one's readings, another person's included.
   - On return, the stream's backfill is dropped. Ana's card showed the stub's curve (`android/14`).
6. **HIGH, both apps.** No follower ever tells the wearer it left, paused or switched. The wearer's list shows it as following, and the "your followers were told" note is written regardless.
7. **HIGH, Mac.** With Juggluco as the source and a kept follow, Treatments and Add Treatment write into the followed phone's log. The card's chart and alert quiets ignore those entries (`mac-11`).
8. **HIGH, both apps.** The escalation switch shows "on" where it does nothing:
   - Mac: in *Settings from* Default or Custom, and on every non-stream source.
   - Android: shows the wearer's "tell followers" rows for someone else's non-stream source.
9. **HIGH, Android.** Picking another LibreLinkUp person never asks first (the Mac asks), and their readings mix with the earlier person's.
10. **MEDIUM.** The rest is in the findings below:
    - accounts of sources not in use stay hidden;
    - a standalone Mac stuck in "Their settings";
    - no-data reasons that fall through to the follow;
    - treatments crossing logs;
    - a sensor pairing that deletes the history without the dialog;
    - rings counted over 12 h;
    - wording.

### Recommendation on A / B / C
None of the three as stated. I recommend **one person, one link (call it D)**.

A Sukrara link is a relationship, not one radio row among sources. While a device is linked, it reads that person through the link.
- **Same person (the owner's case).** The phone's own source is upstream. A phone on *Yours · LibreLinkUp* already shares those readings with its followers, plus pen doses, treatments, settings, alerts and escalation. So the Mac keeps following the phone. Reading LibreLinkUp on the Mac too adds nothing except for testing, and loses all of that.
- **Different person.** This means switching who the device watches, so it means leaving the link.
- **Leaving a link** always means *Stop following*, confirmed, and:
  - the wearer is told (a new PROTOCOL-v2 `leave` event);
  - their settings are copied as this device's own;
  - the history stays as that person's;
  - coming back takes a new invite or nearby pairing.
- **Why not the others:**
  - A is unsafe for a different person and pointless for the same one.
  - B leaves a relationship the wearer believes is live, with frozen or swapped settings and two treatment logs. That is exactly the owner's objection.
  - C is only coherent for the same person; it could come later as a "backup path" under the link if wanted.

### 1. What the Sukrara link carries, and what happens today when another source is picked

Prefixes:
- `M/` = `apps/macos/Sources/SukraraApp/` (except where `SukraraShare` is named)
- `A/` = `apps/android/` (inventory with file:line by a code-reading agent, verified by the emulator walk)

**Read-only evidence from the owner's Mac** (`sourceKind=libreLinkUp`, `sourcePerson=yours`, `settingsFrom` unset, so the default "wearer" applies):
- The follow store `readings.csv` (11,951 rows) was written to the minute while LibreLinkUp was the source.
- Four TLS sockets were open, including the relay at nos.lol.
- Bluetooth showed "Tartaruga" connected.
- `wearer-settings.json` (15 Nov) was being applied.

| Link item | Mac today | Android today |
|---|---|---|
| Relays and Bluetooth | **Stay connected.** `follower.start()` is unconditional (`M/AppModel.swift:283`). `FollowerService` has no pause (`SukraraShare/Follower/FollowerService.swift:101-131`). Readings are only ignored (`AppModel.swift:191,296-298`). | **Closed at once** (walked: relay socket gone; the wearer shows "Paired, not nearby"). `A/share/.../FollowerEngine.kt:564-578,705-718`. Follow record and key kept. |
| History | The follow store keeps filling, unused. Rings count only over the active store (`AppModel.swift:434-451,711`), so LibreLinkUp's 12 h hides the 7/14/30-day rings. | The rank rule overwrites relayed readings with online ones, another person's too. The stream's backfill is dropped on return (`ReadingRank.kt:21-34`). |
| 53 mirrored settings + *Settings from* | **Keep applying** the phone's values, thresholds included, to the other source: `M/Windows/WindowCoordinator.swift:116-119` (gated on `link.isFollowing` only), `AppDelegate.swift:330-352`, `Launch/AppServices.swift:74-77`. General and Alerts still say "Their settings" (`mac-09`). | **Silently stop** (treated as Custom): `A/app/.../FollowerSettingsBridge.kt:70-74`. Hidden, and the choice is kept for a return. Settings search still lists *Settings from* and opens a blank page (`SettingsIndex.kt:387`, `SettingsScreen.kt:711-714`). |
| Mirrored alert events, acks | Dropped: `setMirroring(followMonitoring && …)` (`AppModel.swift:306`, `Alerts/AlertController.swift:135-148`). The Mac's own engine runs on the new source. | Not received. Own engine runs (`AlertRuntime.kt:804-815,964-969`). |
| Escalation | Arrives and is dropped silently; the switch still shows "on" (`Windows/Settings/AlertsRangesPane.swift:52-64`). | Not received. The wearer still sends it to everyone and notes "followers were told" (`AlertNotes.kt:127`). |
| Treatments, writer rights, pen doses | LibreLinkUp or Nightscout: the Mac's own log. The phone's log and pen doses are invisible, and Mac entries go nowhere. Juggluco: Add Treatment still writes to the followed phone's log (`WindowCoordinator.swift:73-80`, `Panel/PanelView.swift:451-458`) while chart and quiets ignore it (`AppModel.swift:385-404`). | One shared log. The followed person's entries become locally editable (`TreatmentLog.kt:157-160`). Own entries stay *Sending…*. Nightscout uploads entries from the wearer's log to the site (`NightscoutSource.kt:450-455`, walked). |
| Sensor life | From the new source (`AppModel.swift:375-381`). | From the new source; the stale record sensor shows again on return. |
| Names, avatars | Gated (`AppModel.swift:562`). | **Stale old name** on alerts, notification, widgets and the Source page (`AlertRuntime.kt:288,947`, `SourceScreen.kt:73`, `GlucoseWidgets.kt:192`, `LockWidget.kt:158`). Cause: `follower.engine.load()` sets the hub link for any source (`AppGraph.kt:1219-1220`). |
| Wearer's list, presence | "Connected" over Bluetooth while the Mac ignores the stream. | "Following since…, Paired, not nearby". No last-seen exists (`OwnerState.kt:21-33`). Unfollow sends nothing on either app (`FollowerEngine.kt:526-528`; Mac `FollowerRuntime.swift:436-454`). |
| Link problems (revoked, broken) | Hidden behind the new source. Where that source isn't running yet, the reason falls through to the link (`AppModel.swift:779-801`). | Home keeps "You no longer follow this person" while the new source has values (`NoDataReason.kt:177-179`). |
| Path badge, blocked features | Panel badge shows the follow's Bluetooth or internet path for LibreLinkUp readings (`PanelView.swift:71-77`, linkPath first). Bluetooth-blocked warning for an unused follow (`Panel/BlockedFeatures.swift:46-48`). | Gated on stream. |
| Mute all, mutes | `resetMonitoring` on a source change (`AppModel.swift:442-444`). | Setup's switch resets mutes when the stream flag changes (`SetupViewModel.kt:397-410`). Other paths carry them over. |
| Way back | Instant: it never left. | Instant, silent resume, no Join step (`SetupViewModel.kt:427`). |

### 2. A / B / C per item

Case (i) = the new source is the **same person** the link follows. Case (ii) = a **different person**.

| Item | A: keep the link live | B: pause it | C: both, with fallback |
|---|---|---|---|
| Readings path | (i) the link stays up only for battery cost; (ii) same | closed; key kept | (i) link first, online fills gaps; (ii) impossible |
| History | (i) fine; (ii) two people's stores must stay apart | (i) gap, backfill on resume; (ii) apart | (i) one merged history (rank: stream above online) |
| Mirrored settings | (i) right; **(ii) the wrong person's thresholds** | (i) frozen copy, or own values that suddenly change (the owner's objection); (ii) must be own | (i) phone's, live |
| Alerts | (i) mirrored: the card (online, 1–3 min late) disagrees with the phone's alert; own engine: the Mac rings later and ignores carbs logged on the phone. **(ii) the child's alerts under the parent's card: unsafe** | own engine; nothing from the phone | (i) mirror while the stream is live, own engine (phone's settings) when it's silent: a SPEC change plus scenarios |
| Escalation | (i) works if mirrored; (ii) wrong person | **lost silently**, while the wearer believes it's delivered | (i) works |
| Treatments, pen doses | (i) right only if the Mac keeps the link's log (today it doesn't); (ii) wrong log | **(i) two logs: the NovoPen case**; (ii) apart | (i) the link's log |
| Wearer's view | (i) true; (ii) true, but the device isn't watching their glucose | **looks like a follower, hears nothing** (no protocol message exists) | true |
| Double-alert risk | (i) phone + Mac, as with any follow, but the Mac's is late and ignores the phone's quiets; (ii) one device ringing for two people | low | needs a "never twice for one measurement" rule |
| Way back | — | instant | — |

**The owner's NovoPen case.** The phone is on *Yours · LibreLinkUp*, so it is first-hand and its owner engine shares readings and treatments, pen doses included, with followers. Confirmed on the emulators: the wearer on LibreLinkUp kept feeding the follower. So the only model that brings the pen doses to the Mac is the one where the Mac stays linked: D, or C under a link.
- **What the wearer's phone needs for D:** nothing new for the follow itself. It does need a `leave` event when a follower stops, and ideally a last-seen time per follower.
- **For C later:** nothing beyond that.

### 3. Real use cases for "another source while linked"
- **Testing** (the owner today). Real, rare.
- **Same person, phone moved back to the official app.** If Sukrara stays on the phone (*Yours · LibreLinkUp*), it still shares, so the Mac keeps following. There is nothing to switch.
- **Phone gone** (lost, or Sukrara uninstalled). The link is dead; leave it (Stop following), then use an online source.
- **Fallback when the phone is offline.** LibreLinkUp or Nightscout for that person is usually fed by the same phone's upload, so it's no real fallback. It only helps when another device uploads the sensor, which is rare.
- **Different person** (a parent switching the Mac from the child to themselves). That means changing who the device watches, so it means leaving the link (later: several people).

Conclusion: offer another source on a linked device only behind an explanation.
- **Same person:** "Your phone already sends this Mac what it reads from LibreLinkUp, with your treatments, pen doses, alerts and settings." Buttons: *Keep following your phone* (default) / *Stop following and read LibreLinkUp here*.
- **Different person:** leaving the link, confirmed.

### 4. Recommended model (D) and the coordinator's questions
1. **While linked**, Settings › Source shows the link: who, path, *Stop following*, *Reconnect*. Other sources sit behind *Read another way…* with the explanation above. Android works the same way for *Their Sukrara* and *Sukrara on your other phone*.
2. **Leaving a link** is confirmed and says what happens:
   - the wearer is told (`leave`);
   - their settings become this device's own (`SettingsModel.copyWearer`, `M/Windows/Settings/SettingsModel.swift:101-108`), so nothing shifts;
   - the history stays as that person's (not wiped as *Stop following* wipes today, `FollowerRuntime.swift:445`, `FollowerEngine.kt:552-559`);
   - for a different person, it offers *Keep Ana's settings as a start* / *Sukrara's defaults*.
3. ***Settings from* exists only while linked.** Otherwise the device uses its own settings (that's the case on Android; the Mac isn't). The escalation switch shows only while linked with *Their settings*, as SPEC §4 lines 489-490 say.
4. **Someone else's readings are keyed by the source's identity** (wearer key, LibreLinkUp patient, Nightscout site). This is the owner's Mac decision, extended to Android. The rank rule merges only within one identity.
5. **Metrics questions:**
   - (Q1) Yes, count over every store of the person shown. "Same person" is known only by declaration: *Sukrara on your phone* and LibreLinkUp's own connection.
   - (Q2) In D a follow that isn't the source never supplies settings. They are copied once, explicitly, when leaving.
   - (Q3) Yes, a hidden metric says why on both apps, e.g. "Needs more readings: 12 h of the last 7 days (25 % needed)".
6. **C, if the owner wants it later:** "Backup: LibreLinkUp when your phone is silent", a switch under the link, same person only.

### 5. Decisions for the owner
1. Adopt D (one person, one link) instead of A, B or C?
2. On a linked device, offer other sources behind the explanation (recommended), or hide them?
3. Protocol: add `leave` now (recommended)? Per-follower last-seen now or later? The wearer's "followers were told" note only when someone is listening?
4. Leaving a link copies their settings as own: yes for the same person, ask for a different person?
5. Keep the history when leaving (recommended) instead of today's wipe?
6. C as a later backup path for the same person: wanted or never?
7. Drop *Default* from *Settings from*, keeping *Their settings* / *This device's own*? Optional; it differs little from Custom reset to defaults.
8. Accept the cost to the owner's testing: trying LibreLinkUp on the Mac means unlinking and re-pairing nearby afterwards, about a minute.

### 6. Findings
Platform; journey and repro; what's left behind or broken; fix.

1. **HIGH, Mac: the follow runs on behind another source, hidden.**
   - Repro: dev fixture, setup *Yes · Follow it* → Settings › Source → *LibreLinkUp* (`mac-07` → `mac-08`). The follow rows vanish (who, *Stop following*, *Reconnect*, *How you appear*). Only the footer says the follow is kept.
   - Left behind: relays and Bluetooth (owner's Mac), the follow store, the phone's settings and thresholds applied to LibreLinkUp, *Their settings* in General and Alerts, the panel badge naming the follow's path, the Bluetooth-blocked warning.
   - Fix: D. Until then, gate wearer settings, path badge and blocked features on `followMonitoring`; keep the follow's rows and *Stop following* on the Source pane for every source.
   - **Android fixed** (705ca9364): D. Every path that picks another source while linked asks *Keep following Ana?*, then the leave questions; *Stop following* asks them too (`AppLink`, `SetupViewModelTest`, `AppLinkTest`); a follow an earlier version kept behind another source is left at start (`AppLink.leaveLeftBehind`). `NonLinkSourceTest` checks link.json's `after` list.
   - **Mac fixed** (752c15701): D. Another source picked while following asks *Keep following?*, then the leave sheet; a follow left behind another source by an earlier version is left at start (`AppModel.leaveLink`, history kept, settings untouched); for any other source the path badge, Bluetooth warning, wearer name, settings, mirrored alerts and escalation are the link's only while it is the source. `AppModelLinkTests` (link.json `after`); walked on a fixture dev run (Accessibility; synthetic screenshots in the session scratch, `one-link/mac/`).
2. **HIGH, both: the same person loses the phone's treatments and pen doses** on a Mac reading LibreLinkUp, and Mac entries never reach the phone, Tidepool or Nightscout.
   - Fix: D (the Mac follows the phone).
3. **HIGH, Android: the old follow's name on another source's alerts, notification, widgets and Source page** (`android/25`–`27`).
   - Fix: hub link null unless `spec.stream`; take the name from the current source.
   - **Fixed** (2c1a4863d): the hub shows the follower engine's link only while the stream is the source; `GlucoseState.name` is whose glucose by name as the current source gives it (the stream's wearer, LibreLinkUp's person, none otherwise). `GlucoseHubTest`; emulator: Settings and Source name LibreLinkUp's Ana, then Leo once picked.
4. **HIGH, Android: a change of *Whose glucose* keeps the LibreLinkUp pick, and Ana's glucose became "yours"** (`android/20`, `21`).
   - It then offered *Share with your clinic* and *Set up sharing*, and alerted as the wearer. Cause: `LibreLinkUpSource.kt:312-350`.
   - Fix: a person change clears `source.libreLinkUpPatient` and picks again by the rule; it also confirms the stored Nightscout site.
   - **Fixed** (cc0cc830d): `SourceRecord.forAnotherPerson` clears the pick and the site with the history; LibreLinkUp picks again by the rule (a lone connection that isn't the account is still picked for *Yours*: the contract's rule); the Connect step only offers the earlier site (*The site read before: check it's yours*, its token kept for it) and always shows after a person change. `AppSourcesTest`, `SetupViewModelTest`; emulator: Theirs · Leo → Yours asked *Delete the earlier history?*, then LibreLinkUp's list instead of Leo.
5. **HIGH, Android: history overwritten across sources and people; "The history on this phone stays" is false** (`android/14`).
   - Fix: key someone else's readings by source identity; within one person, fence backfill at the previous source's last reading; correct the dialog copy.
   - **Android fixed** (f58cb2e77, 705ca9364): one person's history at a time (no database change). `ReadingRank.plan` takes the fence (link.json `fence`, `LinkContractTest`): set at the last reading at every source change for the same person (`AppSources`, `AppSourcesTest`) and at a leave with *Keep it*, so the next source fills gaps and never replaces a kept reading. That makes "The history on this phone stays" true; a person change still deletes it (asked).
6. **HIGH, Android: another LibreLinkUp person is read without asking, and readings mix.**
   - Fix: port the Mac's confirmation (`M/Windows/Settings/LibreLinkUpRows.swift:129-140`).
   - **Fixed** (bd07a8cf6): *Read Leo Example's glucose?* (`ReadAnotherPersonDialog`) on the Person row, setup's list and a sign-in or *Try again* that would read someone else (*Cancel* keeps the person read); the source remembers whose readings the phone holds (`LluSourceState.held`) and deletes them and the treatments before another person's arrive, dropping an answer for the earlier person still on its way. `LibreLinkUpSourceTest`, `LibreLinkUpPersonConfirmTest`; emulator: Ana → Leo asked first, and the next person change counted only Leo's 12 h.
7. **HIGH, both: the wearer is never told a follower stopped or switched.** It stays listed with no last-seen; escalations fan out to devices that aren't listening (`android/22`, `15`).
   - Fix: `leave` event (PROTOCOL-v2 addition, both apps) and last-seen (decision 3).
8. **HIGH, Mac: Juggluco source plus a kept follow writes treatments into the followed phone's log** (`mac-11`).
   - Fix: D; meanwhile the treatment log and Add Treatment follow the source only.
   - **Mac fixed** (752c15701): Juggluco on the network has its own log on this Mac (`juggluco/treatments.json`, `LanSourcing.write`), the treatment windows and Add Treatment use it; the wearer's log never counts for another source. `AppModelLinkTests` (`treatments`).
9. **HIGH, both: escalation switch on where it does nothing.**
   - Mac: Default, Custom, any non-stream source.
   - Android: wearer rows for someone else's non-stream source (`AlertsSettingsPage.kt:223-249`, `AlertsViewModel.kt:148`).
   - Risk: a parent who mutes the Mac and relies on "also when muted here" gets nothing.
   - Fix: show it only while linked with *Their settings*, or decide escalation works with own rules.
   - **Android fixed** (61c758651): the wearer's rows only where this phone owns your glucose (`spec.owner`), the follower's switch only while linked with *Their settings*, the search likewise (`EscalationRowsTest`, `SettingsSearchTest.linkRowsOnlyWhileLinked`).
   - **Mac fixed** (752c15701): Settings › Alerts shows the switch only while another Sukrara is the source with *Their settings*; the wearer's alert events are taken only then. `AppModelLinkTests` (`escalation`); walked on a fixture dev run (Accessibility; synthetic screenshots in the session scratch, `one-link/mac/`).
10. **MEDIUM-HIGH, Android: treatments cross logs.**
    - Nightscout uploaded the 25 g from Ana's log to the site.
    - The site's entries show on Ana's card after return.
    - Ana's entries become editable.
    - Own entries stay *Sending…*.
    - Ana's treatments weren't re-fetched after Theirs → Yours → Theirs (cause not pinned).
    - Fix: per-log ownership; never upload another log's entries; reset the follow's treatment cursor when the history is wiped.
    - **Android fixed** (705ca9364): a link left with *Keep it* marks the wearer's entries as kept from the link (`TreatmentRecord.fromLink`): read-only, never uploaded (Nightscout source and uploader, Tidepool, LibreView) or shared; this phone's own unsent entries become its own (no *Sending…* without a link). The follow has no treatment cursor (it asks for the last 30 days at every subscription); a deletion while still linked (yours ↔ someone else's) now asks the relays again (`FollowerEngine.refetch`). `AppLinkTest`, `NonLinkSourceTest`.
11. **MEDIUM, Android: pairing a sensor while showing someone else's glucose forces *Yours* and deletes all readings and treatments without *Delete the earlier history?*** (`A/app/.../AppGraph.kt:316-319` → `SetupPorts.kt:40-46`). Code-read, not walked.
    - Fix: go through `loss()` and the dialog.
    - **Fixed** (1f97a2dba): the new-sensor flow opened while someone else's glucose shows opens setup's source step confirming your own sensor (`SetupStepRoute.yourSensor`: *Delete the earlier history?* when there is history), then the sensor step; `setSourceKind` never changes the person by itself. `SetupViewModelTest`; emulator: Theirs · Juggluco → Settings › Sensor › *Pair new sensor* asked first, then *Connect your sensor*.
12. **MEDIUM, both: accounts of sources not in use can't be seen or removed** (follow key, LibreLinkUp account and pick, Nightscout site and token; Nightscout can't be forgotten at all).
    - Fix: an "Also on this device" group with *Sign out*, *Forget*, *Stop following*, without reconnecting.
    - **Android fixed** (6c34632a5): Settings › Source › *Also saved on this phone*: *Sign out of LibreLinkUp*, *Forget Nightscout site*, each confirmed, hidden when empty; no follow to list (D leaves it).
13. **MEDIUM, Mac: a standalone Mac runs *Settings from: Their settings*** (schema default `wearer`).
    - Alerts, Readings, Graph and Metrics stay read-only with "Waiting for this person's settings" forever.
    - Fix: *Settings from* only while linked.
    - **Fixed** (752c15701): *Settings from* applies and shows only while the source is another Sukrara (`SettingsStoring.settingsFrom`, `SettingsModel.linkSource`); every other source, and no source, runs and edits this Mac's own. `AppModelLinkTests` (`settings`); walked on a fixture dev run (Accessibility; synthetic screenshots in the session scratch, `one-link/mac/`).
14. **MEDIUM, Mac: the no-data reason falls through to the follow.** *Nightscout* chosen without a site says "Not following anyone · Follow Someone…" or "Their phone may be off" (`AppModel.swift:779-801`; reproduced in a dev run, `mac-10`).
    - Fix: a reason per source (e.g. Nightscout: no site yet); the link's reason only when the follow is the source.
    - **Fixed** (752c15701): the link's reasons only while it is the source; *Not monitoring: no Nightscout site yet* · *Connect Nightscout…*, LibreLinkUp's *Not signed in*, Juggluco's own, and *Not monitoring: no source* · *Pick a Source…* after *Stop following* alone. `AppModelLinkTests` (`noData`), `AppModelFollowerTests`.
15. **MEDIUM, Android: home keeps "You no longer follow this person · Join with a new invite"** while another source shows values.
    - Fix: gate on stream.
    - **Fixed** (c2645aa55): `NoDataReason` gives *LinkRevoked* only while the stream is the source (the hub's link is too, 2c1a4863d). `NoDataReasonTest`; not walked (needs an ended follow behind another source).
16. **MEDIUM, Mac: rings counted over LibreLinkUp's 12 h** while the follow holds 8.7 days of the same person.
    - Fix: §4 point 5.
    - Android, the "says why" part (§4 Q3; be3c32adf): Settings › Metrics adds *Needs more readings: 12 h of the last 7 days, 42 h needed* to a hidden metric's summary (`MetricNeedTest`).
17. **MEDIUM, Mac (QA tooling): dev and fixture runs have no LibreLinkUp, Nightscout or Juggluco source**, so the Mac's online journeys can't be walked on the app, only in unit tests.
    - Fix: debug-only loopback overrides for dev runs, like Android's `LIBRELINKUP` command.
18. **LOW-MEDIUM, copy:**
    - Mac: *Yes · Follow it* says "the phone that reads your sensor", which excludes a parent following a child.
    - Mac: after *Yours*, the Follow step says "Their glucose shows in the menu bar now" (`mac-04`).
    - Mac: Welcome still says "yours or someone else's", and its two lines are cut with "…" in the restored 520×194 window (`mac-01`; the owner's saved setup frame is 520×134).
    - Mac: the Source toolbar help names only the follow and Juggluco.
    - Android switch dialogs: "your glucose" for someone else's (`android/03`, `09`); "once it accepts your request" when an approved follow resumes (`android/13`); "the pairing stays" with nothing paired (`android/19`); LibreLinkUp → sensor says nothing about the Libre app holding the sensor (`android/24`).
    - Android: "0 minutes ago"; Sharing "(your sensor or Juggluco)"; InviteHere "shares its own readings" for someone else's.
    - **Mac fixed** (a003c5ba8): no *Whose glucose*; *Yes · Follow it* (preselected, *Recommended*) *Follow the phone that reads the sensor, yours or your child's…*; the follow step *The glucose shows in the menu bar now.*; Welcome without "yours or someone else's", its lines no longer cut; the Source help names every source. walked on a fixture dev run (Accessibility; synthetic screenshots in the session scratch, `one-link/mac/`).
    - **Android fixed** (3900580b6): the dialogs per person, follow, pairing and the Libre app's sensor (`sourceChangeText`, `SourceChangeTextTest`); *just now* under a minute in uploads, broadcasts, Health Connect and the Following page (`AgoTest`); Sharing's note without the parenthesis; InviteHere *shows someone else's glucose* / *follows your other Sukrara*. "The history on this phone stays" is left to finding 5.
19. **LOW:**
    - Android: Nightscout → Juggluco (not installed) skips *Connect Juggluco*, because the old Nightscout reading satisfies `facts.ownSensorLive`.
    - **Android fixed** (17e3be0d6): live means a fresh reading from the source in use (`RestoreGate.ownSensorLive` with the kind's readings; the source step checks the source just chosen). `SetupViewModelTest`, `RestoreGateTest`; emulator: LibreLinkUp → Juggluco (not installed) showed *Connect Juggluco*.
    - Mac: *Skip* on *Do you have Sukrara on your phone?* jumps to a source list without *Their Sukrara*.
    - **Mac fixed** (a003c5ba8): neither source question has *Skip*; *No* after an earlier *Yes* asks the source again (752c15701's follow-up). `SetupFlowTests`, `AppModelNightscoutTests`; walked on a fixture dev run (Accessibility; synthetic screenshots in the session scratch, `one-link/mac/`).

### 7. Questions and settings: what they change, and what to remove
- **Mac *Whose glucose*** changes only words, LibreLinkUp's pick (yourself or the other connection) and the per-person log question. Its removal (decided) must:
  - replace the pick with "all connections, the account's own included";
  - make the card name the person from the source. The deferred "card words with the person's name" becomes required, because a Mac switching between a parent's and a child's glucose would otherwise show unnamed glucose.
- **Mac *Settings from*** on a standalone Mac changes nothing except locking the panes: remove it there.
- ***Default* vs *Custom*** (both apps) differ little: decision 7.
- **Escalation switch** outside *Their settings*: hide (finding 9).
- **Mac setup's alert step** says "(Settings from: Custom)" on a standalone Mac: meaningless there.
- ***Do you have Sukrara on your phone?*** is decisive: keep it, reword it for parents.
- **Android *Whose glucose*** changes a lot (sharing, uploads, pens, reminders): keep it, but it must also reset the LibreLinkUp pick and the Nightscout site (finding 4).

### 8. Why earlier reviews missed it
FEATURES (the Mac Settings window) and QA Q30 specify the hiding as the expected result: "Juggluco on the network chosen while following someone shows Juggluco only… the follow kept for switching back" (test `AppModelSourceTests.theChosenSourceIsReadNeverBothPeople`). Reviews compared screens against that spec and never asked what the hidden follow keeps doing, or how to leave it.

Fix: Q19 and Q30 gain a "left behind" table per transition on both apps:
- sockets via `lsof` or `/proc`;
- stored accounts;
- *Settings from* in use;
- alert role;
- treatment log;
- the wearer's list.

Plus a unit test on each app: "a non-stream source uses nothing derived from the link" (settings, name, badge, treatments).

### 9. Seen on the way, and not checked
- **Leftover defaults files.** About 3,000 plists in `~/Library/Preferences` from Mac test and snapshot runs: 2,006 `app.mauri.sukrara.tests.*` and 958 `app.mauri.sukrara.snap.*`, 12 MB. Tests and `sukrara-snap` should remove their suites. *(Fixed since: f10ba774d.)*
- **Folder icon in my screenshots.** The *Sukrara on your phone* row shows a folder icon in a bare dev run: `applicationIconImage` of a non-bundle. Not a bundled-app bug. *(A dev run draws Sukrara's icon since 88af07a98.)*
- **Not checked:**
  - the Mac's Bluetooth and relays in a dev run (offline by design; read from the owner's Mac read-only and from code);
  - the Mac's online sources live;
  - widgets, Spanish, VoiceOver and TalkBack;
  - Android with Juggluco installed;
  - the wearer's "followers were told" note while the follower was away (code only).
