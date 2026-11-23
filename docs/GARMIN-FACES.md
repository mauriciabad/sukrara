# Garmin watch faces that read Sukrara

Any Connect IQ watch face can show the glucose Sukrara for Android has: it asks the phone every 5 minutes, the way
Sukrara's own faces do, and gets the same answer. This page is for the developer of such a face: the library that does
it for you, the request and what comes back if you'd rather do it yourself, the rules a glucose face keeps, a face that
compiles, and what it can't do yet.

The full format, every profile and every key: [`contracts/watchface/payload/README.md`](../contracts/watchface/payload/README.md)
(this page is its *Other watch faces* part, written out), and its JSON Schema (2020-12):
[`sukrara.mauri.app/schemas/watch-payload.json`](https://sukrara.mauri.app/schemas/watch-payload.json).

## SukraraFace, the library

[`apps/garmin/sdk`](../apps/garmin/sdk/) is a [Monkey Barrel](https://developer.garmin.com/connect-iq/core-topics/shareable-libraries/),
Connect IQ's library format, called **SukraraFace**: the fetch, the version check, the order of payloads, the phone's
clock lead, and what the payload says at every draw (the age, the freshness steps, the value in the person's unit with
`LO` / `HI`, the trend, the pill, the last treatment, the sensor, today's time in range) as typed fields. Sukrara's own
apps read their payloads with the same code, and its tests run every case of `cases.json` in the simulator on every
change. It is **0BSD** (no conditions: any face, open or closed), and strictly type-checked, so a face at any type-check
level can use it.

Add it:

1. Get the barrel: every [Sukrara release](https://github.com/mauriciabad/sukrara/releases) carries it as
   `SukraraFace-<version>.barrel` (built for every watch Sukrara's own apps run on, checked before the release goes
   out) and as `SukraraFace.barrel`, whose link always gets the newest one:

   ```sh
   mkdir -p barrels
   curl -fL -o barrels/SukraraFace.barrel https://github.com/mauriciabad/sukrara/releases/latest/download/SukraraFace.barrel
   ```

   In your `monkey.jungle`: `base.barrelPath = barrels/SukraraFace.barrel` (in VS Code, *Monkey C: Configure Monkey
   Barrels* writes it for you). Pin a version by downloading `SukraraFace-<version>.barrel` from that release
   instead.
   **From source** instead (to read or change it, or to follow `next`): copy `apps/garmin/sdk` into your project (say as
   `SukraraFace/`) and name its jungle: `base.barrelPath = SukraraFace/monkey.jungle`. A git submodule of this
   repository works the same way (`base.barrelPath = sukrara/apps/garmin/sdk/monkey.jungle`), at the cost of the whole
   repository. Build the `.barrel` yourself with `barrelbuild -o SukraraFace.barrel -f SukraraFace/monkey.jungle`;
   SDK 9.3's `barrelbuild` also needs `--override-devices-json` with your SDK's `Devices` folder, or it stops on a
   NullPointerException.
2. In your `manifest.xml`: the `Background` and `Communications` permissions, and
   `<iq:barrels><iq:depends name="SukraraFace" version="5.0.0"/></iq:barrels>`.
3. Four calls (the sample face below has them in place):

| Where | Call |
|---|---|
| `AppBase.getInitialView` | `SukraraFace.register();` (the fetch every 5 minutes) |
| `AppBase.getServiceDelegate` | `return [new SukraraFace.Service()];` (or call `SukraraFace.fetch()` from your own `onTemporalEvent`) |
| `AppBase.onBackgroundData` | `if (SukraraFace.receive(data)) { WatchUi.requestUpdate(); }` |
| `WatchFace.onUpdate` | `var r = SukraraFace.reading();` (`null` until Sukrara first answers) |

What a `SukraraFace.Reading` says, worked out for the watch time it was made at (every time on the watch's clock):

| Field / function | What |
|---|---|
| `text()` | the value as the person reads it: `"112"`, `"6.2"`, `"LO"`, `"HI"`; `null` with none to show (no reading, or 20 minutes old) |
| `freshness` | `SukraraFace.CURRENT` (up to 90 s), `LATE` (show its age), `STRUCK` (from 5 min: struck through, grey, no trend), `GONE` (from 20 min: no value) |
| `ageS`, `readAt` | its age in seconds, its time |
| `trend()`, `deg`, `doubled`, `rate`, `band` | whether to draw the arrow now, its rotation (degrees, negative = up), the doubled arrow, mg/dL per minute, the trend's band −3…3 |
| `level()` | the value among the person's limits: −2 very low, −1 low, 0 in range, 1 high, 2 very high, 3 extremely high |
| `mgdl`, `unit`, `bands`, `history` | the raw value (mg/dL), `mgdl` / `mmol`, the person's limits, the 3 h before it (5 min apart, `null` = a gap) |
| `status`, `side`, `left` | the pill: `none`, `auto` (treat the `low` / `high` side), `quiet` (treated), `muted`; seconds left (`null` = no end) |
| `last`, `lastAt` | the last treatment as Sukrara's card shows it (`[{k, a, t, m}]`), its time |
| `sensor`, `warm`, `readyAt`, `sensorLife`, `sensorLeft` | `none`, `warmup`, `almost`, `ok`, `ending`, `ended`; the warm-up done (0…1) and its end; the sensor's life in days and the hours left |
| `tir` | today's time in range in whole percent, or `null` |

`SukraraFace.payload()` is the newest payload as it came, for anything else. The barrel's major version is the
payload's `v` (5.x reads version 5): a raise of `v` comes with a new major version.

## The fetch

What SukraraFace does, for a face (or a tool) that makes the request itself.

From the face's background (a `System.ServiceDelegate`), on a temporal event every 5 minutes (Connect IQ's shortest):

```monkeyc
Communications.makeWebRequest(
    "http://127.0.0.1:17582/sukrara/face",
    null,
    {
        :method => Communications.HTTP_REQUEST_METHOD_GET,
        :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
    },
    method(:onAnswer)
);
```

Garmin Connect makes the request on the phone, where Sukrara answers on loopback (Connect IQ allows plain `http` only
to `127.0.0.1`). The manifest needs the `Background` and `Communications` permissions. No query, no header, no key.

| Answer | When |
|---|---|
| `200` and the payload (a `Dictionary`) | Sukrara has a payload; `sent` is the phone's clock now |
| `503` | Sukrara is running but hasn't built a payload yet |
| a negative Connect IQ error code, no answer | the port is closed (below, *Limits*), Sukrara isn't running, Garmin Connect isn't, or the phone is out of reach |

Hand the dictionary to the face with `Background.exit(data)` (the face profile with its 3 hours fits the background's
~8 KB); the face gets it in `AppBase.onBackgroundData`, keeps it in `Application.Storage` (a restart then shows it at
once, aging) and redraws. Keep its `sent` in a key of its own too and check the order against that, not against the
stored payload: reading the stored one beside the new one holds two payloads, and a watch's glance or background has
little memory (SukraraFace does this). The first answer comes up to 5 minutes after the face registers its temporal event.

## The payload

The face profile, as JSON (times in epoch seconds, glucose always in mg/dL; every key in the
[schema](https://sukrara.mauri.app/schemas/watch-payload.json), whose `face` branch validates it):

```json
{
  "v": 5, "p": "face", "sent": 1794823776, "unit": "mgdl",
  "bands": [55, 70, 140, 180, 250, 300],
  "g": {"t": 1794823746, "v": 112, "rate": 0.4, "band": 0, "deg": -12.0},
  "hist": [94, 96, 100, null, 114, 123, 132, 140, 146, 150, 151, 149, 145, 139, 132, 124, 117, 111,
           106, 102, 99, 97, 95, 95, 96, 97, 98, 99, 100, 101, 103, 104, 105, 107, 108, 110],
  "st": null,
  "last": {"t": 1794818976, "items": [{"k": "rapid", "a": 2.0, "t": 1794818976, "m": 1794822576}], "until": 1794833376, "next": null},
  "sensor": {"s": "active", "start": 1793959776, "end": 1795255776, "warn": 1795226976},
  "tir": {"pct": 82, "until": 1794873600}
}
```

| Key | What a face does with it |
|---|---|
| `v`, `p` | `5` and `"face"`: read nothing else (another version may mean something else) |
| `sent` | the phone's clock when it answered: the clock skew (below), and which payload is newer |
| `unit` | `mgdl` or `mmol`: how the person reads glucose |
| `bands` | the person's own limits in mg/dL: `[veryLow, low, tight, high, veryHigh, extremelyHigh]`; in range is `low`…`high` |
| `g` | the newest reading, or `null`: `t` its time, `v` mg/dL (`39` = LO, `401` = HI), `rate` mg/dL per minute, `band` the trend −3…3 (±3 = a doubled arrow), `deg` the arrow's rotation in degrees (negative = up); `rate`, `band`, `deg` are `null` without a trend |
| `hist` | the 36 readings before `g`, one per 5 minutes, oldest first; `null` = no reading in that slot |
| `st` | what Sukrara asks now, or `null`: `needed` (treat a `low` / `high`), `taken` (treated, quiet until `until`), `paused` (that side muted until `until`), `muted` (all alerts muted until `until`); `until` −1 = no end |
| `last` | the last treatment as Sukrara's card shows it, or `null`; it ends at `until`, then `next` follows (same shape) |
| `sensor` | `none` (nothing paired), `warmup` (`start`, `ready`), `active` (`start`, `end`, `warn` = from when to count down), `ended` |
| `tir` | today's time in range in whole percent until `until` (the phone's midnight), or `null` |

## Rules a face keeps

A glucose face is read in a hurry, at night, before a decision. Sukrara's faces keep these rules, and a face that shows
Sukrara's glucose keeps them too (SukraraFace keeps the ones about time, units and order; the drawing is yours):

- **Never an old value as current.** Work out the age at every draw (`onUpdate`, once a minute in low power too),
  never once when the payload arrived: `skew = max(0, sent − now)` when it arrives (the watch clock behind the
  phone's), then `age = max(0, now + skew − g.t)`. Sukrara's steps
  ([`contracts/graph/freshness/freshness.json`](../contracts/graph/freshness/freshness.json)): up to 90 s current;
  from 91 s show the reading's time beside it; **from 5 minutes struck through and grey, without the trend**; from
  20 minutes no value, only *No reading since* and its time; from 3 hours no chart.
- **The units.** `v` is mg/dL whatever the person reads; with `unit` `mmol` show `v ÷ 18.0182` with one decimal.
  `39` and `401` are the sensor's limits: show `LO` and `HI`, never the number, and no trend.
- **The person's limits.** Colour by `bands`, never by fixed numbers, and never by colour alone: a low that only turns
  red is lost on an always-on screen, in sunlight or to a colour-blind eye.
- **Countdowns from their end.** `st.until`, `last.until`, `sensor.end` are absolute times: count down to them at every
  draw (adding the skew). One that has run out shows nothing: the next payload says what follows.
- **The newest wins.** Keep a payload only when its `sent` is newer than the one you have (each fetch's is: it matters
  once a face gets the same payload another way).
- **The phone decides.** Sukrara works out the bands, the trend, what to do and when to ring; a face shows it and
  never replaces the phone's alerts.

## A face that compiles

[`apps/garmin/sample`](../apps/garmin/sample/) is the smallest face that keeps these rules, with SukraraFace: the
time, the value in its unit and band with its trend, late, struck through, then gone. CI builds it
(`apps/garmin/build.sh sample`), so this example compiles with the current SDK and barrel. Its `manifest.xml` has the
two permissions and the barrel; give your face its own app id. The sample is **0BSD** like the barrel (copy it into
any face, open or closed); the rest of Sukrara is GPL-3.0.

```monkeyc
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// A watch face that reads Sukrara's glucose (https://sukrara.mauri.app/developers/garmin-faces/) with the SukraraFace
// barrel: its background asks Sukrara for Android every 5 minutes; the face draws the time and the value, and the
// barrel works out the reading's age again at every draw. Late from 90 s, struck through from 5 minutes, gone from 20:
// never an old value as current.

// Loaded by the background too (the fetch), hence (:background).
(:background)
class SampleApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        SukraraFace.register();
        return [new SampleView()];
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new SukraraFace.Service()];
    }

    // Sukrara's answer, handed over by the background: kept when it's the payload this barrel reads and the newest
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (SukraraFace.receive(data)) {
            WatchUi.requestUpdate();
        }
    }
}

class SampleView extends WatchUi.WatchFace {
    function initialize() {
        WatchFace.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var x = dc.getWidth() / 2;
        var h = dc.getHeight();
        var y = h * 2 / 3;
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var clock = System.getClockTime();
        dc.drawText(x, h / 3, Graphics.FONT_NUMBER_MEDIUM, clock.hour.format("%d") + ":" + clock.min.format("%02d"), center);

        // what Sukrara's payload says now; null until Sukrara first answers
        var r = SukraraFace.reading();
        var text = r != null ? r.text() : null;
        if (r == null || text == null) {
            // no reading, or none for 20 minutes
            dc.drawText(x, y, Graphics.FONT_LARGE, "---", center);
            return;
        }
        var struck = r.freshness == SukraraFace.STRUCK;
        // the person's own limits, never fixed numbers; grey once it is struck through
        var level = r.level() as Number;
        var colour = struck ? Graphics.COLOR_LT_GRAY : level < 0 ? Graphics.COLOR_RED : level > 0 ? Graphics.COLOR_YELLOW : Graphics.COLOR_WHITE;
        dc.setColor(colour, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, y, Graphics.FONT_LARGE, text, center);
        var half = dc.getTextWidthInPixels(text, Graphics.FONT_LARGE) / 2;
        if (struck) {
            // the value is there, but it isn't current
            dc.setPenWidth(3);
            dc.drawLine(x - half, y, x + half, y);
        } else if (r.trend()) {
            // the trend arrow, rotated by Sukrara's degrees (negative = up)
            var a = Math.toRadians(r.deg as Float);
            var ax = x + half + 14;
            dc.setPenWidth(r.doubled ? 5 : 3);
            dc.drawLine(ax - 10 * Math.cos(a), y - 10 * Math.sin(a), ax + 10 * Math.cos(a), y + 10 * Math.sin(a));
            dc.fillCircle(ax + 10 * Math.cos(a), y + 10 * Math.sin(a), 4);
        }
        if (r.freshness != SukraraFace.CURRENT) {
            // late: say how old it is
            dc.drawText(x, y + 40, Graphics.FONT_XTINY, (r.ageS / 60).toString() + " min ago", center);
        }
    }
}
```

To try it without a phone, run the Connect IQ simulator with Sukrara's stand-in for the phone, which answers the fetch
on the Mac with the payload's test cases: `python3 apps/garmin/tools/fake_phone.py --case 3` (a low), then
*Simulation › Background Events* in the simulator.

## Limits

- **Only while a Sukrara app is on the watch.** Sukrara opens the port while Garmin Connect says one of its own
  Connect IQ apps (a Sukrara watch face, the watch app *Sukrara* or *Sukrara data field*) is installed on a watch,
  and closes it with its Garmin switch. A watch with only your face gets nothing: the person installs Sukrara's watch
  app or data field too.
- **No push for other faces yet.** Garmin Connect delivers a phone message only to the app ids the phone sends to, and
  Sukrara sends only to its own: your face gets each reading by its fetch, up to 5 minutes late. How a face could be
  allowed pushes (its app id in the fetch, the person allows it on the phone) is proposed in
  [docs/47 "In Sukrara"](47-garmin-apps.md#in-sukrara).
- **Read only.** The fetch never carries the key Sukrara's watch app signs with, so a face can't log treatments or
  mute alerts.
- **Android only.** Sukrara for Android with Garmin Connect on the phone the watch is paired with.
- **What any app on the phone sees.** While the port is open, any app on the phone can read the same answer: the
  [privacy policy](https://sukrara.mauri.app/privacy/) says so (*Sukrara on your Garmin watch*). Your face adds nothing to that.
- **Not a medical device.** Neither Sukrara nor a face that reads it is cleared or approved by any regulator.

## Known barrel bugs

Connect IQ's, not SukraraFace's; what to do about each:

- **Everything a face's background or glance calls needs that scope.** SukraraFace's module is `(:background
  :glance)`, so your background can call `SukraraFace.fetch()` and `receive()`; a symbol of a barrel outside the
  caller's scope fails at run time with *Illegal Access (Out of Bounds)*, not at build time
  [[forum]](https://forums.garmin.com/developer/connect-iq/f/discussion/409623/3d-party-barrel---can-t-get-it-to-work/1941848).
- **`excludeAnnotations` don't carry over from a barrel's jungle.** `barrelbuild` ignores the barrel's own
  `excludeAnnotations`, while a barrel used as a project (`base.barrelPath = …/monkey.jungle`) applies them
  [[bug report]](https://forums.garmin.com/developer/connect-iq/i/bug-reports/excludeannotations-work-inconsistently-in-barrels-vs-barrel-projects).
  SukraraFace has none, so both ways build the same code. Its face-only functions carry `(:sukraraFace)`, a name of
  its own: exclude it in your jungle only if you never call `text()`, `trend()` or `level()`.
- **SDK 9.3's `barrelbuild`** stops on a NullPointerException without `--override-devices-json` (above, *From
  source*).

## Versions

`v` is raised when a face reading the old format would misread the new one; a key added that an old face can ignore
keeps `v`. Each raise is listed here.

SukraraFace follows [semantic versioning](https://semver.org/) tied to `v`: its **major** version is the payload's
`v` (5.x reads `v` 5), a **minor** adds something a face can use without changing what it already gets, a **patch**
fixes the barrel. A face keeps working on any newer 5.x; update the file and the `<iq:depends>` version together.

**A major version** means Sukrara for Android sends the new format: a face still on the old major gets nothing it can
read from then on. It keeps its last payload, which ages on the watch like any reading (struck through after 5
minutes, gone after 20), so it never shows an old value as current, and then shows no glucose until it is updated to
the new major. Each major comes in a Sukrara release whose notes and this section say so. To hear of one: *Watch ›
Custom › Releases* on [the repository](https://github.com/mauriciabad/sukrara), or this section.

- **5** (2026-11): the face profile at `/sukrara/face`, open to every watch face; SukraraFace 5.0.0, on every Sukrara
  release from 0.17.0 (`SukraraFace-5.0.0.barrel`).
