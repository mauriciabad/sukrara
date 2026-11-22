# Sukrara's watch payload (version 4)

What the phone sends Sukrara's Connect IQ apps, and what they send back: everything a watch shows from Sukrara
(`../aura/SPEC.md` §3 for the face, `../app/SPEC.md` for the app once designed), decided by Sukrara exactly as the card
and the glucose notification decide it (SPEC ground rule 1). Why it travels the way it does:
[docs/43](../../../docs/43-garmin-link.md) (the face's link) and [docs/47](../../../docs/47-garmin-apps.md) §2, §4, §6
(the apps, their memory, the writes).

| File | What |
|---|---|
| `apps.json` | **the registry**: every Sukrara Connect IQ app, its app id and beta id, its profile, its fetch path; the port and the entries path |
| `cases.json` | phone state → exact payload, per profile (`cases` the face, `app`, `field`); for the face also what it makes of it at a given watch time, the message order rule, versions it ignores; a payload as Garmin's own serializer writes it |
| `entries.json` | entries from the watch: request → the phone's answer → what it stores |
| `undos.json` | *Undo* of an entry the watch logged: request → the phone's answer → the entry its delete removes |
| `mutes.json` | mutes from the watch: request → the phone's answer → the alert runtime's action |
| `schema.json` | the payload's JSON Schema, one branch per profile |

Run by `AuraPayloadFixtureTest` (Android `:integrations`: phone → payload for every profile, through the wire format
and back, Garmin's bytes), `GarminAppsTest` (the registry against the phone's `GarminApp`), `WatchWritesTest` (the
entries, undo and mute endpoints) and `WatchEntryTest` (`:feature:treatments`: the phone's entry and delete paths), and `LinkTest` (the watch's
shared link, `apps/garmin/tools/link-test.sh <app>`: payload → the face's inputs, each app its own profile; the cases
reach Monkey C through `apps/garmin/tools/link_cases.py`, whose `--check` also holds every manifest's id and type and
every fetch path to `apps.json`; `apps/garmin/build.sh` takes the beta ids from it). Parity group
`watchface-link`.

## The apps

| App | Connect IQ type | Profile | Fetch | Writes |
|---|---|---|---|---|
| **Sukrara Aura** | watch face (128 KB, background 64 KB) | `aura` | `GET /sukrara/aura` | no |
| **Sukrara** | watch app: glance, app, background, complications (app 768 KB, glance and background 64 KB) | `app` | `GET /sukrara/app` | `POST /sukrara/entries`, `POST /sukrara/undo`, `POST /sukrara/mute` |
| **Sukrara data field** | data field (128–256 KB) | `field` | `GET /sukrara/field` | no (a data field has no input) |

Each app is its own Connect IQ app id, so each has its own storage and its own phone messages; a data field can't read
another app's storage or complications (docs/47 §2). So every app gets its own push and makes its own fetch, and
**each profile carries only what that app draws**: the face is nearly full (#217), and the app's glance and background
have 64 KB to decode a message in.

Every app also has a **beta id** (a Connect IQ beta app is a separate id, docs/20-publishing.md). The phone treats the
beta like the public app: a push to whichever of the two Garmin Connect says is installed, the same profile, the same
fetch path.

## How it travels

The same dictionary, two ways (both carry it whole, so either alone keeps the app right):

1. **Push**: a Connect IQ phone-app message through Garmin Connect, on every change (each new reading, a status, a
   treatment, a setting, a ring), to each installed Sukrara app id on each connected watch, each with its own profile.
   Wire format: Garmin's Monkey C serialization (string block `0xABCDABCD`, data block `0xDA7ADA7A`, big-endian;
   Android writes it itself, `MonkeyC`). The app's background wakes on it (`registerForPhoneAppMessageEvent`) and
   hands it on (`Background.exit`, under its ~8 KB; the app profile stays under 6 KB with a full day and 64 entries, `theAppsDayFitsItsBackground`). Every background run
   (push or fetch) also leaves its result in the app's storage (the face's `auraHandoff`, with a run id), picked up at
   the next draw when Garmin doesn't call `onBackgroundData` (#244); the id makes a run count once. Retries and the
   stuck rule (docs/43) hold **per app on each watch**: one app's failed message is sent again without sending the
   others' again.
2. **Fetch**: each app's background asks `GET http://127.0.0.1:17582/<its fetch path>` every 5 minutes (a temporal
   event; Garmin Connect makes the request, plain http only to loopback). The answer is the same profile as JSON,
   **never with `key`**. The port is open only while a watch is known to have any Sukrara app (Garmin Connect says it
   is installed, or a push reached it), loopback only, and the main switch closes it.

The fetch carries the app's own report back as its query (`apps/garmin/shared/link/Link.mc` `saveReport`, Android `AuraReport.parse`):
`r=<run>_<run>…&d=<readingAt>.<since>`, a run being `<kind>.<at>.<sent>.<readingAt>` (kind `p` a push, `p<n>` a push that took n older waiting messages with it, `f` a fetch,
`e<code>` a failed fetch, `s` appended when it picked it up from storage (Garmin didn't hand it over), `x` appended when it kept the older payload; `at` the watch clock; the last 10, oldest
first) and `d` the reading it draws (its time on the watch clock) and since when. Times only, never a value. The
app adds `&w=<w>`, the watch's id from its last push: the phone answers with that watch's own display settings (a
fetch without `w`, before any push arrived: the one watch with the app, else the phone app's own values). The
phone shows it (*On the watch*) and logs it (`adb logcat -s Sukrara.Aura`, with each push's app, `sent` and result).

## Profiles

| Key | `aura` | `app` | `field` |
|---|---|---|---|
| `v`, `p`, `sent`, `unit`, `bands`, `g`, `st` | ✓ | ✓ | ✓ |
| `hist` | 36 (3 h) | 288 (24 h: the app zooms 3 / 6 / 12 / 24 h itself, 12 h by default) | 12 (1 h) |
| `ring` | — (a face can't vibrate) | ✓ | ✓ |
| `last`, `sensor` | ✓ | ✓ | — |
| `tir` (today) | ✓ | — | — |
| `tx`, `ranges`, `m`, `else`, `ro`, `log`, `ds`, `rep`, `repD` | — | ✓ | — |
| `key`, `w` | — | **push only** | — |

## The payload

Keys are short. Times are **absolute** epoch seconds (32-bit), so the watch keeps every age and countdown right
between messages. Decimals are floats; everything else whole numbers. Amounts are floats (units, or grams).

Every profile:

| Key | Value |
|---|---|
| `v` | `4`. An app ignores any other version, any other profile, and anything that isn't a dictionary with `v`, `p` and `sent` |
| `p` | the profile: `aura` / `app` / `field` |
| `sent` | the phone's clock when it was built |
| `unit` | `mgdl` / `mmol` (the watch converts: mg/dL ÷ 18.0182, one decimal) |
| `bands` | the person's limits, mg/dL: `[veryLow, low, tight, high, veryHigh, extremelyHigh]` (Sukrara's `Bands`, in order) |
| `g` | the newest reading, or null (none, or not monitoring): `t` its time, `v` mg/dL (39 = LO, 401 = HI), `rate` mg/dL/min, `band` THE trend's band −3…3 (±3 = the doubled arrow), `deg` the arrow's rotation (negative = up); `rate` / `band` / `deg` null without a trend (and always for LO / HI) |
| `hist` | the profile's number of readings before `g` (above), oldest first: slot i of n is the reading nearest `g.t − 300 s × (n − i)` within 150 s, else null; `[]` without `g` |
| `st` | the pill's status (Sukrara's alert runtime), null = none: `{k: needed, side}`, `{k: taken, side, until}` (quiet after a treatment), `{k: paused, side, until}` (a side muted), `{k: muted, until}` (Mute all); `side` `low` / `high`; `until` −1 = no end |

The face and the app:

| Key | Value |
|---|---|
| `last` | the treatment field, null = none: `{t, items: [{k: rapid / carbs, a, t, m}], until, next}`, exactly the card's slot beside the value (Sukrara's one rule, `LastTreatment`: running insulin with the carbs riding along, the one covering the alert, or the newest rapid insulin (4 h) / carbs (3 min per gram, 30 min to 3 h); never long-acting or notes) at `sent`; `t` = its newest item's time; each item's own `t` and `m`, its mute's end (`LastTreatment.Info.until`; −1 = its kind mutes nothing with this phone's rules), so the watch draws the card's double ring at every draw: each kind's share left = `(m − now) / (m − t)`, 0 to 1 (`Info.left`); `until` = when that answer ends with nothing new logged (−1 = no end), `next` = the answer from then on, the same shape (null = none), so the watch moves on by itself with the phone out of reach |
| `sensor` | `{s: none}` (not monitoring: nothing paired) · `{s: warmup, start, ready, hold, end?}` · `{s: active, start?, end?, warn?}` (`warn` = the start of the person's countdown, *Sensor countdown*, default the last 8 h) · `{s: ended}`. `start` (active) and `end` (warm-up) are the sensor's life as the phone's sensor screen has it (the wearer's paired sensor; not on a follower): the face's sensor rail (`../aura/SPEC.md` §6.9b), the app's days left |

The face only:

| Key | Value |
|---|---|
| `tir` | today's time in range on the phone, `{pct, until}` (whole percent, 100 only when every reading was in range; `until` = the end of the phone's day), or null (not enough data: under a quarter of the day so far) |

The app and the data field:

| Key | Value |
|---|---|
| `ring` | what Sukrara's alert runtime rings now on this phone, `[{k, side, relaxed, less}]` in the kinds' order (`very_low`, `low`, `high`, `very_high`, `extremely_high`, `falling_fast`, `rising_fast`, `no_data`; at most one level, one rate and the no-data alert at once), `side` `low` / `high` / `none` (no data); `relaxed` = it reminds less often now (the person's *Remind less*: *Remind as usual* undoes it), `less` = *Remind less* would slow it down (contracts/alerts SPEC "Remind less": the watch offers the button only then); `[]` = nothing rings (and while not monitoring). The value it rings about is `g`. A kind that appears is a new alert; one that goes was answered, muted or ended |

The app only:

| Key | Value |
|---|---|
| `tx` | the treatments of the 24 h before `sent`, oldest first, at most the newest 64, as four lists side by side (a dictionary per entry would be twice the bytes): `{t: [times], k: [rapid / long / carbs], a: [amounts], i: [ids]}`, `i` = the first 8 characters of the entry's id in the log (an entry from the watch has the watch's id: its outbox finds it by them); notes and deleted entries never |
| `ranges` | time in range over the last day and the last 7 days (rolling, as the card's rings count them), `[day, week]`, each `{w, pct, b}` or null with under a quarter of the window covered: `w` the window (s), `pct` the in-range share as a whole percent (100 only when every reading was in range), `b` the share per band in per mille, `[veryLow, low, tight, target, high, veryHigh]` (Sukrara's six time-in-range bands; `extremelyHigh` colours the line only and has no time-in-range share) |
| `m` | the card's metrics beside the value as the person set them (`graph.slots`, the ones that are on, the main one first), each `{k, w, v, dim}`: `k` `tir` / `gmi` / `avg` / `cv`, `w` its window (s), `dim` = drawn dimmed where it isn't the main one, `v` its number or null under a quarter of the window covered (`tir` the share of its range as a whole percent, `gmi` % with one decimal, `avg` mg/dL (the watch converts), `cv` whole %); a `tir` one also has `r` (`in` = in range, `tight` = the very good range: the person's *ring range*), `b` (per mille per band as in `ranges`, null without data) and `mark` (`none` / `laurel` / `flowers` / `crown`: the card's reward, with 70 % of the window covered) |
| `else` | what the card's last-treatment slot shows while there is no last treatment (`graph.lastElse`; the watch shows it once no step of `last` is still ahead), the shape of an `m` item; null when off or when the card has no last-treatment place; a `v` of null shows nothing, as on the card |
| `ro` | the rings' band order clockwise from 12 o'clock, as band indexes into `b` (`graph.ringOrder`; default `[2, 3, 1, 0, 4, 5]`: very good, in range, low, very low, high, very high) |
| `log` | what logging offers, or null when this phone takes no entries now (not monitoring, setup not done, a follower the wearer doesn't let add entries): `{order, rapid, long, carbs}`: `order` = THE kinds' order at `sent` (`../../treatments/kind-order.json`, Sukrara's `KindOrder` over the same facts the phone's Add treatment focuses from: carbs first while low, long-acting for 2 h after a rapid pen's read, else rapid), the log list's order, its first the one to offer first; each kind `{last, usual}`: `last` = that kind's newest amount of the last 30 days (null = none), `usual` = up to three amounts logged most often in the last 30 days (ties: the most recently logged), smallest first |
| `ds` | the display settings the app follows, each the phone's own value resolved for the watch (one place, `GarminDisplay.resolve`: a watch's own values, docs/38's place overrides, resolve there): `hours` the chart's window (`graph.hours`; the app's own zoom starts there), `marks` per kind (`rapid`, `long`, `carbs`, `note`) the parts shown (`point`, `line` the mute line, `amount`, `time`; `graph.marks`), `side` the distribution and % column beside the chart (`graph.side`), `crown` its crown at 100 % (`graph.columnCrown`). No green-in-range setting on the phone: the value and line always take the band's colour there (the face's *green in range* is a watch setting, `garmin.aura.rangeColour`) |
| `rep` | the Numbers page for every period the phone's report offers (`reportPeriod`: 1, 7, 14, 30 days, rolling), so the watch switches period without the phone: each `{d, cov, b, avg, gmi, cv, ok}` or null without a reading in it: `cov` the share of the period with data in per mille (under 700 the report says so), `b` per mille per band as in `ranges`, `avg` mg/dL, `gmi` % with one decimal (mmol/mol = (gmi − 2.152) × 10.929), `cv` % with one decimal, `ok` the goals met as the phone's report card decides them (`Goal`, the consensus targets), one bit each: bit 0 very high < 5 %, 1 above < 25 %, 2 in range > 70 %, 3 below < 4 %, 4 very low < 1 %, 5 average < 154 mg/dL, 6 GMI < 7 %, 7 CV ≤ 36 % (the watch keeps no goal of its own) |
| `repD` | the period the Numbers page opens on: 14 (the phone's report card starts there; the Mac's remembers its own, `report.days`), or the watch's own *Report period* |
| `key` | **push only**: the phone's signing key for entries and mutes, 64 hex characters (32 bytes). The fetch never carries it |
| `w` | **push only**: this watch's id on the phone (Garmin Connect's id for it, decimal digits), so the watch's fetch says which watch asks (`&w=`, above) and `m`, `else`, `ro`, `ds`, `repD` come resolved for that watch (its own values, `../app/SPEC.md` §11) |

## On the watch

Each app keeps its newest payload (by `sent`) in storage, so a restart shows it at once, aging. Every draw it works out
its inputs from the payload and the watch clock `now` (`cases.json` `watch` checks the face's):

- **Clock skew**: on receipt, `skew = max(0, sent − now)` (the watch clock behind the phone's; kept with the payload).
  `age = max(0, now + skew − g.t)`. A watch clock ahead only makes readings older, never younger.
- **Freshness** from `age` with Sukrara's steps (`../../graph/freshness/freshness.json`); a value is never shown as
  current from when it was *received*.
- `glucose`: `g.v`, or `LO` / `HI` under 40 / over 400; `trend` = a `rate` is there; `double` = `|band| = 3`.
- **Status**: `needed` → `auto`; `taken` → `quiet`; `paused` and `muted` → `muted` (`side` null for Mute all). `left` =
  `until − (now + skew)` seconds, `never` for −1. A countdown that has run out shows no pill: the phone's next
  message says what follows.
- **Sensor**: `none`; `warmup` while `now < hold` and no `g` newer than `start` (`warm` = `(now − start) / (ready −
  start)`, `almost` from `ready`), else `ok`; `active`: `ended` from `end`, `ending` from `warn` (`sensorLeft` =
  `(end − now)` hours), else `ok`; `ended`. "Ended but still sending" is `ended` with a fresh or late value. An
  active sensor with `start` and `end` also gives `sensorLife` = `(end − start)` in days and `sensorLeft` =
  `(end − now)` hours (never below 0) in every state: the sensor rail; without them the rail says `--`.
- `last`: the first of `last`, `last.next`, … whose `until` is still ahead of `now + skew` (−1 = never), else none;
  `lastAgo` = `(now + skew − its t)` minutes. `tir` until `tir.until`, then none.
- `ring` counts only while `g` is fresh or `ring` says `no_data`: a payload that has aged past fresh rings nothing
  (the phone's next message says what rings).
- **Order**: keep the stored payload when an incoming one has an older `sent`, unless the stored one is more than
  5 minutes ahead of the watch clock (the phone's clock was wrong, then fixed). The app keeps the newest `key` and `w` it
  was pushed (a fetch has neither and leaves them).

## Entries from the watch

The app logs rapid insulin, long-acting insulin and carbs with `POST http://127.0.0.1:17582/sukrara/entries`, through
Garmin Connect's proxy (`makeWebRequest`; before API 6.0.3 a POST can only send its params as a form or JSON body, so
both are taken). Another app on the phone can reach 127.0.0.1 too, so **every write is signed with `key`**, which only
Garmin Connect's push to the app's id carries (docs/47 §6); a write needs one delivered push, and an app that never got
one has nothing to log against anyway.

**Request** (`entries.json` `cases`): a JSON object (`Content-Type: application/json`) or a form
(`application/x-www-form-urlencoded`), at most 1 KB, **every value a string**:

| Field | Value |
|---|---|
| `id` | the entry's id, a lowercase UUID v4 the watch makes when the person confirms (`Cryptography.randomBytes(16)`, version and variant bits set): it becomes the treatment's id in the log (contracts/treatments: created once by the device that creates it) |
| `k` | `rapid` / `long` / `carbs` |
| `a` | the amount as the phone stores it: insulin with at most one decimal (`4.5`, `14`), carbs in whole grams (`30`) |
| `t` | when it was taken, epoch s on the phone's clock (the watch clock + `lead`, below) |
| `s` | when this try is sent, epoch s on the phone's clock, new on every try |
| `f` | `1` = log it although a pen entry is near (the person said *Log anyway* after `pen`), else `0` |
| `mac` | HMAC-SHA256 with the key (its 32 bytes) over `id|k|a|t|s|f` (the strings exactly as sent, joined by `|`), lowercase hex |

**Answer**: HTTP 200 with a JSON object, `{id, r, …}` (Garmin Connect turns other statuses into error codes and may drop
the body, so every answer about an entry is a 200). The phone checks in this order and answers the first that fails:

| `r` | When | Stored? | The watch |
|---|---|---|---|
| `sig` | no key on the phone, or `mac` doesn't match | no | keeps it; sends it again once a push brought a key (another app on the phone can't sign) |
| `clock` | `s` more than 300 s from the phone's clock; the answer has `now` (the phone's clock) | no | sets `lead` from `now` and sends it again at once (a replay outside that window also ends here) |
| `id` | `id` isn't a lowercase UUID v4 | no | drops it (a bug) |
| `kind` | `k` isn't one of the three (notes stay the phone's) | no | drops it |
| `known` | an entry with this `id` is in the log (a retry whose answer was lost, a replay, or a different entry under a known id): `e` = the stored one `{t, k, a}`, or null when it was deleted on the phone | **never a second time**, never changed | removes it from the outbox |
| `amount` | not a number, zero, over 100 u / 500 g, or more decimals than the phone stores (the phone's Add treatment limits, `TreatmentForm`; never rounded behind the person's back) | no | drops it |
| `when` | `t` more than 1439 min before the phone's clock (the phone's furthest *minutes ago*) or more than 300 s after it | no | drops it, says *Not logged: too old* |
| `off` | this phone takes no entries now (as `log` null) | no | drops it, says so |
| `pen` | rapid or long insulin a pen entry of the same insulin already logged near `t` (the phone's *Already logged by your pen?*), `f` 0: `pen: {t, a}` | no | asks the person; *Log anyway* sends it again with `f` = 1 (a new `s` and `mac`), *Cancel* drops it |
| `stored` | everything else: stored exactly as the phone's own Add treatment stores it (the same log, sync, quiet rules and uploads); `e` = `{t, k, a}` (`t` no later than the phone's clock) | yes | removes it from the outbox, says *Logged* |

A request that isn't one (another path: 404; another method: 405; over 1 KB: 413; no parseable body or a field
missing: 400 with `{r: bad}`) stores nothing.

**The outbox** (the watch): an entry goes into storage when the person confirms it, with its id; it leaves only on
`stored` / `known` or a drop above, **or when a payload's `tx.i` has its id's first 8 characters** (an answer lost after the phone stored it).
The watch sends from the foreground at once and from the background every 5 minutes (callbacks there aren't
guaranteed: the outbox, not the callback, is the truth), and shows *Waiting for the phone* while it's there. `lead` =
the phone's clock minus the watch's, from the last payload's `sent` at receipt (signed: a watch clock ahead gives a
negative one), or from a `clock` answer's `now`.

**What follows**: the next payload (pushed at once: the entry changes it) has the entry in `tx` with its id, in `last`
when the card's rule picks it, in `log` (`last`, `usual`; `order` as the phone's facts now say), and the alert
runtime's quiet in `st` / `ring`.

## Undo from the watch

*Undo* (10 s after *Logged*, `../app/SPEC.md` §6) deletes the entry the watch just logged, by its id, with
`POST http://127.0.0.1:17582/sukrara/undo`: the same channel as entries (the same key, body encodings, send-time window
and answer shape; `undos.json`). The phone deletes it through **its own delete** (home's, `TreatmentLog.delete`): a
newer deleted version, synced to followers and the Mac, deleted from Nightscout, retracted from LibreView and Tidepool
by their uploads, and the alert runtime's quiet rules no longer count it. The watch keeps the undo in its outbox until
an answer below drops it (a lost answer: it sends it again, and `gone` says it is done), and sends it only after its
entry's `stored` or `known`.

| Field | Value |
|---|---|
| `id` | the entry's id, the UUID v4 the watch made for it (`Entries from the watch`) |
| `s` | when this try is sent, epoch s on the phone's clock, new on every try |
| `mac` | HMAC-SHA256 with the key over `id|s`, lowercase hex |

**Answer**: HTTP 200 with `{id, r, …}`, checked in this order:

| `r` | When | Changes | The watch |
|---|---|---|---|
| `sig`, `clock`, `id` | as for entries (`clock` has `now`) | nothing | as for entries |
| `unknown` | no entry with this id in the log (it never reached the phone) | nothing | drops it |
| `foreign` | the entry wasn't logged from the watch (the phone's own, a pen's, a follower's): the watch only takes back its own | nothing | drops it (a bug) |
| `gone` | it is deleted already (on the phone, or a retry whose answer was lost) | nothing | as `done` |
| `off` | this phone may not change it now (as `log` null, or it is no longer this phone's to change) | nothing | drops it, says *Not undone* |
| `done` | everything else: the phone's own delete ran | the entry is deleted | removes it from the outbox, says *Undone* |

An entry logged from the watch is one the phone stored from `POST /sukrara/entries` (it keeps that with the entry,
Android `TreatmentRecord.fromWatch`); an edit on another device (a follower's) makes it that device's, and it is
`foreign` from then on. A deleted id stays known: the same entry sent again is `known` with `e` null, never logged
again. The next payload no longer has it in `tx`, `last` or `log`.

## Mute from the watch

The app mutes, unmutes and changes how often a side reminds with `POST http://127.0.0.1:17582/sukrara/mute`, the same channel as entries (the same key,
body encodings, send-time window and answer shape; `mutes.json`). It runs **the alert runtime's own actions**, the ones
the alert notification and home offer (`AlertControls`), never a rule of its own; `st` in the next push shows the result.

| Field | Value |
|---|---|
| `id` | a lowercase UUID v4 per action (a retry of the same action keeps it) |
| `do` | `mute` / `unmute` / `less` (*Remind less*) / `usual` (*Remind as usual*) |
| `side` | `all` (*Mute all*), `low` or `high` (that side's alerts) |
| `min` | for `mute`: `15`, `30`, `60`, `120`, or `0` = until turned off (`all` only: a side's mute always ends, contracts/alerts SPEC); for `unmute`, `less`, `usual`: `0` (`less` and `usual` take `low` or `high` only) |
| `s` | when this try is sent, epoch s on the phone's clock |
| `mac` | HMAC-SHA256 with the key over `id|do|side|min|s`, lowercase hex |

Answers (HTTP 200, `{id, r, …}`), checked in this order: `sig`, `clock` (with `now`), `id` as for entries; `action` = not
one of the combinations above (nothing changes; the watch drops it); `known` = this action id was done in the last 10
minutes (a retry, a replay: done once); `done` = the runtime ran it: *Mute all* for `min` minutes or until turned off,
the side's mute for `min` minutes, *Unmute* of the side (it also ends a quiet after a treatment, as the phone's button
does), the end of *Mute all*, or the side's *Remind less* / *Remind as usual* (its `relaxMin`).

## Changing it

A change lands with the phone and every app in the same change (parity group `watchface-link`), with `v` raised when
an older app would misread it (CLAUDE.md: the wire formats are versioned; every app speaks the same version). A key
an older app ignores needs no raise. Version 4 (#199): the profiles and `p`, the app's and the data field's keys, the
entries, undo and mute endpoints; the face's keys as in version 3.
