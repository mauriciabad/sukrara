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
// complications (Slots), the next run at the reading's expiry (Expiry), a new alert as Garmin's notification (Alerts),
// then the outbox sent before the run ends.
(:background)
class AppService extends LinkService {
    var _data as Dictionary? = null;

    function initialize() {
        LinkService.initialize();
    }

    function onTemporalEvent() as Void {
        Expiry.schedule(Time.now().value());
        LinkService.onTemporalEvent();
    }

    // every run's result: kept, worked on, then the outbox, then Background.exit (which ends the run)
    function hand(data as Dictionary) as Void {
        keep(data);
        var now = Time.now().value();
        var p = data.get("d");
        if (Link.accept(p, now)) {
            Outbox.received(p as Dictionary, now);
            Expiry.schedule(now);
            Alerts.notify(now);
        }
        Slots.publish(now);
        _data = data;
        Outbox.flush(method(:sent));
    }

    function sent() as Void {
        Background.exit(_data);
    }
}

// A new alert from the phone as Garmin's own notification (SPEC §7 "Garmin's notification", API 5.1+; nothing on 5.0
// watches, where Garmin Connect's forwarded phone notification stays): the alert's word and value, the reading's time,
// and the phone's actions, each of which opens the app with it. It says again what Sukrara rings on the phone.
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

    // the items that ring now and weren't announced yet; the announced kinds kept
    function news(now as Number) as Array<Dictionary> {
        var items = Ring.now(Link.payload(), now, Link.skew);
        var seen = Application.Storage.getValue(RUNG);
        var fresh = Ring.fresh(items, seen instanceof Array ? seen : [] as Array);
        Application.Storage.setValue(RUNG, Ring.kinds(items));
        return fresh;
    }

    function notify(now as Number) as Void {
        var fresh = news(now);
        if (fresh.size() == 0 || !(Toybox has :Notifications) || !(Notifications has :showNotification)) {
            return;
        }
        // the app is open: its own alert screen says it (AppNav.check), one buzz
        var fg = Application.Storage.getValue("sukFg");
        if (fg instanceof Number && now - fg >= 0 && now - fg < 45) {
            return;
        }
        var it = fresh[0];
        var p = Link.payload() as Dictionary;
        var g = p.get("g");
        var title = Application.loadResource(word(it.get("k"))) as String;
        if (g instanceof Dictionary && !"no_data".equals(it.get("k"))) {
            title += " " + Glucose.value(g.get("v") as Number, Slots.unitOf(p));
        }
        var at = Slots.readAt(p);
        var sub = at != null ? Clock.hm(at) : "";
        var labels = {TREAT => Rez.Strings.Treatment, LESS => Rez.Strings.RemindLess, USUAL => Rez.Strings.RemindUsual,
            MUTE30 => "low".equals(it.get("side")) ? Rez.Strings.MuteLows30 : Rez.Strings.MuteHighs30};
        var codes = choices(it);
        var actions = [] as Array;
        for (var i = 0; i < codes.size(); i++) {
            actions.add({:label => Application.loadResource(labels.get(codes[i]) as ResourceId),
                :data => {"a" => codes[i], "side" => it.get("side"), "k" => it.get("k")}});
        }
        try {
            Notifications.showNotification(title, sub, {
                :data => {"a" => 0, "side" => it.get("side"), "k" => it.get("k")},
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
