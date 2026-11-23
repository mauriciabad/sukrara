// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Notifications;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// The app's background: the shared link's (LinkService: the push, the fetch, the hand-off), and on every run the app's
// own work, so it happens with the app closed (docs/47 §3): the payload kept (Link), the lead and key (Outbox), the
// complications (Slots), the next run at the reading's expiry and the phone's messages registered again (ensure), a new
// alert as Garmin's notification (Alerts), then the outbox sent before the run ends. Each step is in the app's log
// (Trace).
(:background)
class AppService extends LinkService {
    var _data as Dictionary? = null;
    var _dropped as Number = 0;
    var _retried as Boolean = false;

    function initialize() {
        LinkService.initialize();
    }

    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        Trace.reg("background push");
        LinkService.onPhoneAppMessage(msg);
    }

    function onTemporalEvent() as Void {
        Trace.reg("background temporal");
        ensure(Time.now().value());
        // the payload kept, read for the expiry: the glance's copy of it, when it lacks one (GlanceCopy.heal); then let
        // go, so the run holds one payload at a time (the fetch's answer is the next)
        GlanceCopy.heal(Link.payload(), Link.skew);
        Link.unload();
        // the phone's messages left waiting (pushes Garmin didn't hand this background, #217): let go one at a time,
        // the fetch brings the newest
        if (Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(method(:onDropped));
        }
        Trace.log("background fetch, " + _dropped + " waiting dropped");
        LinkService.onTemporalEvent();
    }

    function onDropped(msg as Communications.PhoneAppMessage) as Void {
        _dropped += 1;
    }

    // A fetch that couldn't reach the phone at all (Bluetooth: Garmin Connect's link busy or down, -1 … -104; not -102,
    // too large) is asked again once at once: the next temporal run is 5 min away (Garmin's floor), and the -104 runs
    // on the owner's watch came while the face's fetches got through (2026-11-22, #217).
    function onFetched(code as Number, data as Dictionary or String or Null) as Void {
        Trace.log("background fetched " + code);
        if (!_retried && code < 0 && code >= -104 && code != -102) {
            _retried = true;
            LinkService.onTemporalEvent();
            return;
        }
        LinkService.onFetched(code, data);
    }

    // Every run registers again whatever is missing (#217): the phone's messages, and the next run at the reading's
    // expiry (Expiry). The background is never left waiting for the app to be opened.
    function ensure(now as Number) as Void {
        if (Background has :getPhoneAppMessageEventRegistered && !Background.getPhoneAppMessageEventRegistered()) {
            Background.registerForPhoneAppMessageEvent();
        }
        Expiry.schedule(now, Slots.readAt(Link.payload()));
    }

    // every run's result: kept here (AppRun.keep), worked on, then the outbox, then Background.exit (which ends the run)
    function hand(data as Dictionary) as Void {
        var now = Time.now().value();
        var p = data.get("d");
        // a new alert first, before the run's storage writes (the payload alone is about 4 KB): Garmin's notification
        // as soon as a newer payload is in (the order rule Link.accept keeps it by)
        if (Link.valid(p)) {
            var stored = Application.Storage.getValue(Link.SENT);
            if (SukraraFace.replaces(stored instanceof Number ? stored : null, p as Dictionary, now)) {
                Alerts.notify(p as Dictionary, SukraraFace.skewOf((p as Dictionary).get("sent") as Number, now), now);
            }
        }
        var out = AppRun.keep(data, now);
        Trace.log("background " + data.get("k") + " " + data.get("c") + " waiting " + data.get("n") + " kept " + out.get("kept"));
        keep(out);
        ensure(now);
        if (out.get("kept") == true) {
            Outbox.received(p as Dictionary, now);
        }
        Slots.publish(now);
        _data = out;
        Outbox.flush(method(:sent));
    }

    function sent() as Void {
        Trace.reg("background exit");
        Background.exit(_data);
    }
}

// A background run's result kept by the background itself: the payload (Link) and the glance's copy (GlanceCopy). The
// app's foreground gets only what came (Link.receive "kept"), never the payload: the glance that takes it has 64 KB,
// and the stored payload read beside a handed one ran it out of memory on a Descent Mk3 43 (#217).
(:background)
module AppRun {
    function keep(data as Dictionary, now as Number) as Dictionary {
        var p = data.get("d");
        var kept = Link.accept(p, now);
        if (kept) {
            GlanceCopy.save(p as Dictionary, Link.skew);
        }
        var ok = Link.valid(p);
        var g = ok ? (p as Dictionary).get("g") : null;
        return {"k" => data.get("k"), "c" => data.get("c"), "n" => data.get("n"), "kept" => kept,
            "sent" => ok ? (p as Dictionary).get("sent") : 0, "gt" => g instanceof Dictionary ? g.get("t") : 0};
    }
}

// A new alert from the phone as Garmin's own notification (SPEC §7 "Garmin's notification", API 5.1+; nothing on 5.0
// watches, where Garmin Connect's forwarded phone notification stays). Garmin lays it out (its type, colours and places:
// SPEC §14.7); Sukrara gives it what it takes: the alert's bell in the alert's colour as its icon (a picture per colour,
// apps/garmin/tools/launcher_icons.py: Garmin takes no BufferedBitmap there), the word and value, the trend and the
// reading's time, and the phone's actions, each of which opens the app with it; the notification itself opens the app's
// own alert (S.alert). It says again what Sukrara rings on the phone.
(:background)
module Alerts {
    const RUNG = "sukRung"; // the kinds the background last announced
    // the actions' codes (the notification's action data, back in onStart)
    const TREAT = 1;
    const LESS = 2;
    const USUAL = 3;
    const MUTE30 = 4;

    // the actions for the ring item [it]: Treatment (a side's kind), Remind less when it would slow it, Remind as usual
    // once relaxed, Mute the side for 30 min
    function choices(it as Dictionary) as Array<Number> {
        var out = [] as Array<Number>;
        var side = it.get("side");
        if (Ring.treatment(side) != null) {
            out.add(TREAT);
        }
        if (it.get("less") == true) {
            out.add(LESS);
        } else if (it.get("relaxed") == true) {
            out.add(USUAL);
        }
        if ("low".equals(side) || "high".equals(side)) {
            out.add(MUTE30);
        }
        return out;
    }

    function word(k) as ResourceId {
        var ids = {"very_low" => Rez.Strings.AlertVeryLow, "low" => Rez.Strings.AlertLow, "high" => Rez.Strings.AlertHigh,
            "very_high" => Rez.Strings.AlertVeryHigh, "extremely_high" => Rez.Strings.AlertExtremelyHigh,
            "falling_fast" => Rez.Strings.AlertFallingFast, "rising_fast" => Rez.Strings.AlertRisingFast};
        var id = ids.get(k);
        return id != null ? id as ResourceId : Rez.Strings.AlertNoData;
    }

    // THE ALERT'S COLOUR as the icon's name (AlertView.colour's): a level alert its band, a rate alert the value's band
    // ([v] mg/dL, [b] the payload's six limits; AppMath.band's rule, which the background doesn't load), no data grey
    function colour(k, v, b) as String {
        var ks = ["very_low", "low", "high", "very_high", "extremely_high"];
        var names = ["veryLow", "low", "high", "veryHigh", "extremelyHigh"];
        for (var i = 0; i < ks.size(); i++) {
            if (ks[i].equals(k)) {
                return names[i] as String;
            }
        }
        if ("no_data".equals(k) || !(v instanceof Number) || !(b instanceof Array) || b.size() != 6) {
            return "grey";
        }
        var under = [v <= b[0], v < b[1], v <= b[2], v < b[3], v < b[4], v < b[5]];
        var bands = ["veryLow", "low", "tight", "target", "high", "veryHigh"];
        for (var i = 0; i < under.size(); i++) {
            if (under[i]) {
                return bands[i] as String;
            }
        }
        return "extremelyHigh";
    }

    // the trend's words (THE trend's band −3 … 3, contracts/trend/bands.json); null = no trend
    function trend(band) as ResourceId? {
        var ids = [Rez.Strings.TrendFallingVeryFast, Rez.Strings.TrendFallingFast, Rez.Strings.TrendFalling,
            Rez.Strings.TrendSteady, Rez.Strings.TrendRising, Rez.Strings.TrendRisingFast, Rez.Strings.TrendRisingVeryFast];
        return band instanceof Number && band >= -3 && band <= 3 ? ids[band + 3] as ResourceId : null;
    }

    // the items of [p] ([skew]: the phone's clock lead) that ring at [now] and weren't announced yet; the announced
    // kinds kept
    function news(p as Dictionary?, skew as Number, now as Number) as Array<Dictionary> {
        var items = Ring.now(p, now, skew);
        var seen = Application.Storage.getValue(RUNG);
        var fresh = Ring.fresh(items, seen instanceof Array ? seen : [] as Array);
        Application.Storage.setValue(RUNG, Ring.kinds(items));
        return fresh;
    }

    // Garmin's notification for what newly rings in [p] (title "High 207", subtitle "Rising · 20:41", the icon, the
    // actions)
    function notify(p as Dictionary, skew as Number, now as Number) as Void {
        var fresh = news(p, skew, now);
        if (fresh.size() == 0 || !(Toybox has :Notifications) || !(Notifications has :showNotification)) {
            return;
        }
        // the app is open: its own alert screen says it (AppNav.check), one buzz
        var fg = Application.Storage.getValue("sukFg");
        if (fg instanceof Number && now - fg >= 0 && now - fg < 45) {
            return;
        }
        var it = fresh[0];
        var k = it.get("k");
        var g = p.get("g");
        var v = g instanceof Dictionary ? g.get("v") : null;
        var title = Application.loadResource(word(k)) as String;
        var sub = "";
        if (g instanceof Dictionary) {
            if (!"no_data".equals(k)) {
                title += " " + Glucose.value(v as Number, Slots.unitOf(p));
            }
            // the trend's words, not again after a rate alert's own ("Rising fast 250"); then the reading's time
            var tr = "falling_fast".equals(k) || "rising_fast".equals(k) ? null : trend(g.get("band"));
            sub = (tr != null ? (Application.loadResource(tr) as String) + " · " : "") + Clock.hm((g.get("t") as Number) - skew);
        }
        var labels = {TREAT => Rez.Strings.Treatment, LESS => Rez.Strings.RemindLess, USUAL => Rez.Strings.RemindUsual,
            MUTE30 => "low".equals(it.get("side")) ? Rez.Strings.MuteLows30 : Rez.Strings.MuteHighs30};
        var codes = choices(it);
        var actions = [] as Array;
        for (var i = 0; i < codes.size(); i++) {
            actions.add({:label => Application.loadResource(labels.get(codes[i]) as ResourceId),
                :data => {"a" => codes[i], "side" => it.get("side"), "k" => k}});
        }
        var icons = {"veryLow" => Rez.Drawables.AlertVeryLow, "low" => Rez.Drawables.AlertLow,
            "tight" => Rez.Drawables.AlertTight, "target" => Rez.Drawables.AlertTarget, "high" => Rez.Drawables.AlertHigh,
            "veryHigh" => Rez.Drawables.AlertVeryHigh, "extremelyHigh" => Rez.Drawables.AlertExtremelyHigh,
            "grey" => Rez.Drawables.AlertGrey};
        try {
            Notifications.showNotification(title, sub, {
                :icon => icons.get(colour(k, v, p.get("bands"))) as ResourceId,
                :data => {"a" => 0, "side" => it.get("side"), "k" => k},
                :actions => actions,
                :dismissPrevious => true
            });
        } catch (e) {
            // a watch that shows none (docs/47 §5): the app's own alert when it opens
        }
    }
}

// hours:minutes on the watch's clock (its 12 / 24 h setting)
(:background :glance)
module Clock {
    function hm(t as Number) as String {
        var c = Time.Gregorian.info(new Time.Moment(t), Time.FORMAT_SHORT);
        var h = c.hour as Number;
        if (!System.getDeviceSettings().is24Hour) {
            h = h % 12 == 0 ? 12 : h % 12;
        }
        return h + ":" + (c.min as Number).format("%02d");
    }
}
