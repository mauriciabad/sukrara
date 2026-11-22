# Garmin watch faces that read Sukrara

Any Connect IQ watch face can show the glucose Sukrara for Android has: it asks the phone every 5 minutes, the way
Sukrara's own faces do, and gets the same answer. This page is for the developer of such a face: the request, what
comes back, the rules a glucose face keeps, a face that compiles, and what it can't do yet.

The full format, every profile and every key: [`contracts/watchface/payload/README.md`](../contracts/watchface/payload/README.md)
(this page is its *Other watch faces* part, written out).

## The fetch

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
once, aging) and redraws. The first answer comes up to 5 minutes after the face registers its temporal event.

## The payload

The face profile, as JSON (times in epoch seconds, glucose always in mg/dL):

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
Sukrara's glucose keeps them too:

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

[`apps/garmin/sample`](../apps/garmin/sample/) is the smallest face that keeps these rules: the time, the value in
its unit and band, late, struck through, then gone. CI builds it (`apps/garmin/build.sh sample`), so this example
compiles with the current SDK. Its `manifest.xml` has the two permissions; give your face its own app id.

```monkeyc
import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// A watch face that reads Sukrara's glucose (https://sukrara.mauri.app/developers/garmin-faces/). Its background asks
// Sukrara for Android every 5 minutes; the face draws the time and the value, working out the reading's age again at
// every draw: struck through from 5 minutes, gone from 20. Never an old value as current.

// Loaded by the background too (the fetch), hence (:background).
(:background)
class SampleApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        // every 5 minutes, Connect IQ's shortest; a registration that stands is kept
        if (Background.getTemporalEventRegisteredTime() == null) {
            Background.registerForTemporalEvent(new Time.Duration(5 * 60));
        }
        return [new SampleView()];
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new SampleFetch()];
    }

    // Sukrara's answer, handed over by the background. Kept only when it's the face profile of the version this face
    // reads: another version may mean something else.
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (!(data instanceof Dictionary)) {
            return;
        }
        var sent = data.get("sent");
        if (data.get("v") != 5 || !"face".equals(data.get("p")) || !(sent instanceof Number)) {
            return;
        }
        // how far the watch clock is behind the phone's: every age adds it
        var now = Time.now().value();
        data.put("skew", sent > now ? sent - now : 0);
        Application.Storage.setValue("sukrara", data);
        WatchUi.requestUpdate();
    }
}

// The fetch: Garmin Connect makes the request on the phone, where Sukrara answers on loopback.
(:background)
class SampleFetch extends System.ServiceDelegate {
    function initialize() {
        ServiceDelegate.initialize();
    }

    function onTemporalEvent() as Void {
        Communications.makeWebRequest(
            "http://127.0.0.1:17582/sukrara/face",
            null,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            method(:onAnswer)
        );
    }

    // 200 with the payload; anything else (Sukrara not running, no reading yet, the phone away) hands over nothing
    function onAnswer(code as Number, data as Dictionary or String or Null) as Void {
        Background.exit(code == 200 ? data : null);
    }
}

class SampleView extends WatchUi.WatchFace {
    function initialize() {
        WatchFace.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var x = dc.getWidth() / 2;
        var h = dc.getHeight();
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var clock = System.getClockTime();
        dc.drawText(x, h / 3, Graphics.FONT_NUMBER_MEDIUM, clock.hour.format("%d") + ":" + clock.min.format("%02d"), center);

        var payload = Application.Storage.getValue("sukrara");
        var glucose = payload instanceof Dictionary ? glucoseNow(payload, Time.now().value()) : null;
        if (glucose == null) {
            dc.drawText(x, h * 2 / 3, Graphics.FONT_LARGE, "---", center);
            return;
        }
        var text = glucose[0];
        var age = glucose[1];
        dc.setColor(glucose[2], Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, h * 2 / 3, Graphics.FONT_LARGE, text, center);
        if (age > 5 * 60) {
            // struck through: the value is there, but it isn't current
            var half = dc.getTextWidthInPixels(text, Graphics.FONT_LARGE) / 2;
            dc.setPenWidth(3);
            dc.drawLine(x - half, h * 2 / 3, x + half, h * 2 / 3);
        }
        if (age > 90) {
            // late: say how old it is
            dc.drawText(x, h * 2 / 3 + 40, Graphics.FONT_XTINY, (age / 60).toString() + " min ago", center);
        }
    }

    // What the value says now: [its text, its age in seconds, its colour], or null with no reading or one older than
    // 20 minutes. The payload's keys: contracts/watchface/payload/README.md › The payload.
    function glucoseNow(payload as Dictionary, now as Number) as [String, Number, Number] or Null {
        var g = payload.get("g");
        if (!(g instanceof Dictionary)) {
            return null;
        }
        var v = g.get("v") as Number;
        var age = now + (payload.get("skew") as Number) - (g.get("t") as Number);
        if (age < 0) {
            age = 0;
        }
        if (age > 20 * 60) {
            return null;
        }
        var text;
        if (v < 40) {
            text = "LO";
        } else if (v > 400) {
            text = "HI";
        } else if ("mmol".equals(payload.get("unit"))) {
            text = (v / 18.0182).format("%.1f");
        } else {
            text = v.toString();
        }
        // bands: [veryLow, low, tight, high, veryHigh, extremelyHigh] in mg/dL; grey once it is struck through
        var bands = payload.get("bands") as Array<Number>;
        var colour = Graphics.COLOR_WHITE;
        if (age > 5 * 60) {
            colour = Graphics.COLOR_LT_GRAY;
        } else if (v < bands[1]) {
            colour = Graphics.COLOR_RED;
        } else if (v > bands[3]) {
            colour = Graphics.COLOR_YELLOW;
        }
        return [text, age, colour];
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

## Versions

`v` is raised when a face reading the old format would misread the new one; a key added that an old face can ignore
keeps `v`. Each raise is listed here.

- **5** (2026-11): the face profile at `/sukrara/face`, open to every watch face.
