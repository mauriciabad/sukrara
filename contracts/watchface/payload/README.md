# Sukrara Aura's payload (version 3)

What the phone sends the Garmin face: everything the face shows from Sukrara (`../aura/SPEC.md` §3), decided by
Sukrara exactly as the card and the glucose notification decide it (SPEC ground rule 1). Why it travels the way it
does: [docs/43](../../../docs/43-garmin-link.md).

| File | What |
|---|---|
| `cases.json` | phone state → exact payload → what the watch makes of it at a given watch time; the message order rule; versions the face ignores; a payload as Garmin's own serializer writes it |
| `schema.json` | the payload's JSON Schema |

Run by `AuraPayloadFixtureTest` (Android `:integrations`: phone → payload, through the wire format and back, Garmin's
bytes) and `LinkTest` (the face, `apps/garmin/tools/link-test.sh`: payload → the face's inputs; the cases reach Monkey
C through `apps/garmin/tools/link_cases.py`). Parity group `watchface-link`.

## How it travels

The same dictionary, two ways (both carry it whole, so either alone keeps the face right):

1. **Push**: a Connect IQ phone-app message through Garmin Connect, on every change (each new reading, a status, a
   treatment, a setting), to the face's app id. Wire format: Garmin's Monkey C serialization (string block
   `0xABCDABCD`, data block `0xDA7ADA7A`, big-endian; Android writes it itself, `MonkeyC`). The face's background
   wakes on it (`registerForPhoneAppMessageEvent`), hands it to the face (`Background.exit`, well under its ~8 KB).
   Every background run (push or fetch) also leaves its result in the face's storage (`auraHandoff`, with a run id),
   which the face picks up at its next draw when Garmin doesn't call `onBackgroundData` (#244); the id makes a run count
   once.
2. **Fetch**: the face's background asks `GET http://127.0.0.1:17582/sukrara/aura` every 5 minutes (a temporal
   event; Garmin Connect makes the request, plain http only to loopback). The answer is the same payload as JSON.

The fetch carries the face's own report back as its query (`AuraLink.mc` `saveReport`, Android `AuraReport.parse`):
`r=<run>_<run>…&d=<readingAt>.<since>`, a run being `<kind>.<at>.<sent>.<readingAt>` (kind `p` a push, `p<n>` a push that took n older waiting messages with it, `f` a fetch,
`e<code>` a failed fetch, `s` appended when the face picked it up from storage (Garmin didn't hand it over), `x` appended when the face kept the older payload; `at` the watch clock; the last 10, oldest
first) and `d` the reading the face draws (its time on the watch clock) and since when. Times only, never a value. The
phone shows it (*On the watch*) and logs it (`adb logcat -s Sukrara.Aura`, with each push's `sent` and result).

## The payload

Keys are short (the face has 128 KB, its background 64 KB). Times are **absolute** epoch seconds (32-bit), so the
watch keeps every age and countdown right between messages. Decimals are floats; everything else whole numbers.

| Key | Value |
|---|---|
| `v` | `3`. A face ignores any other version (and anything that isn't a dictionary with `v` and `sent`) |
| `sent` | the phone's clock when it was built |
| `unit` | `mgdl` / `mmol` (the face converts: mg/dL ÷ 18.0182, one decimal) |
| `bands` | the person's limits, mg/dL: `[veryLow, low, tight, high, veryHigh, extremelyHigh]` (Sukrara's `Bands`, in order) |
| `g` | the newest reading, or null (none, or not monitoring): `t` its time, `v` mg/dL (39 = LO, 401 = HI), `rate` mg/dL/min, `band` THE trend's band −3…3 (±3 = the doubled arrow), `deg` the arrow's rotation (negative = up); `rate` / `band` / `deg` null without a trend (and always for LO / HI) |
| `hist` | the 36 readings before `g` (3 hours: the sparkline's longest reach, `../aura/SPEC.md` §6.5), oldest first: slot i is the reading nearest `g.t − 300 s × (36 − i)` within 150 s, else null; `[]` without `g` |
| `st` | the pill's status (Sukrara's alert runtime), null = none: `{k: needed, side}`, `{k: taken, side, until}` (quiet after a treatment), `{k: paused, side, until}` (a side muted), `{k: muted, until}` (Mute all); `side` `low` / `high`; `until` −1 = no end |
| `last` | the treatment field, null = none: `{t, items: [{k: rapid / long / carbs, a}]}`: the card's last treatment (running insulin with the carbs riding along, the one covering the alert, or the newest rapid / carbs), else the newest of the last 24 h with whatever was logged in the same minute; notes never |
| `sensor` | `{s: none}` (not monitoring: nothing paired) · `{s: warmup, start, ready, hold, end?}` · `{s: active, start?, end?, warn?}` (`warn` = the start of the person's countdown, *Sensor countdown*, default the last 8 h) · `{s: ended}`. `start` (active) and `end` (warm-up) are the sensor's life as the phone's sensor screen has it (the wearer's paired sensor; not on a follower): the face's sensor rail (`../aura/SPEC.md` §6.9b) |
| `tir` | today's time in range on the phone, `{pct, until}` (whole percent, 100 only when every reading was in range; `until` = the end of the phone's day), or null (not enough data: under a quarter of the day so far) |

## On the watch

The face keeps the newest payload (by `sent`) in storage, so a restart shows it at once, aging. Every draw it works out
its inputs from the payload and the watch clock `now` (`cases.json` `watch` checks these):

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
- `lastAgo` = `(now − last.t)` minutes; `tir` until `tir.until`, then none.
- **Order**: keep the stored payload when an incoming one has an older `sent`, unless the stored one is more than
  5 minutes ahead of the watch clock (the phone's clock was wrong, then fixed).

## Changing it

A change lands with the phone and the face in the same change (parity group `watchface-link`), with `v` raised when
an older face would misread it (CLAUDE.md: the wire formats are versioned; every app speaks the same version).
