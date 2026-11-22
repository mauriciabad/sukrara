// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Notifications;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// Sukrara, the watch app (docs/47, apps/garmin/README.md): its glance (AppGlance), its views (AppView), its background
// on the shared link (AppService: Sukrara's push to this app's id, the 5-minute fetch of /sukrara/app) and the
// complications it publishes (Slots). Loaded by the background and the glance too, hence the annotations.
(:background :glance)
class SukraraApp extends Application.AppBase {
    var _from as Number? = null; // the complication it was opened from
    var _fg as Boolean = false; // the app's own process (the background's never calls getInitialView)

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        var c = state != null ? state.get(:launchedFromComplication) : null;
        _from = c instanceof Number ? c : null;
    }

    // where it opens (SPEC §5, §10): holding the Log complication → the one list; the glance, the app list and the
    // other complications → page 1; not set up (nothing from the phone yet) → the setup page (§4.3). A notification's action arrives through onNote.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        start();
        Outbox.flush(null);
        if (Toybox has :Notifications && Notifications has :registerForNotificationMessages) {
            Notifications.registerForNotificationMessages(method(:onNote));
        }
        _fg = true;
        AppNav.begin();
        if (_from == Slots.LOG && Link.payload() != null) {
            var v = new AppView("list");
            return [v, new ListDelegate(v)];
        }
        return AppNav.pages();
    }

    // Garmin's notification (AppService Alerts.notify): an action opens the app with it (SPEC §7). Treatment → the
    // amount on the side's kind; Remind less / as usual and Mute 30 min → sent, said, closed; the notification itself
    // → the app's alert screen.
    function onNote(msg as Notifications.NotificationMessage) as Void {
        if (msg.type != 2) { // NOTIFICATION_MESSAGE_TYPE_SELECTED (API 5.1)
            return;
        }
        var act = msg.action;
        if (!(act instanceof Dictionary)) {
            return;
        }
        var a = act.get("a");
        var side = act.get("side");
        var low = "low".equals(side);
        if (a == 0 && act.get("k") instanceof String) {
            AppNav.show(act.get("k") as String, false);
        } else if (a == Alerts.TREAT) {
            AppNav.treat(Ring.treatment(side));
        } else if (a == Alerts.LESS && side instanceof String) {
            AppNav.sendAndClose(Outbox.mute("less", side, 0), low ? Rez.Strings.LowsLess : Rez.Strings.HighsLess);
        } else if (a == Alerts.USUAL && side instanceof String) {
            AppNav.sendAndClose(Outbox.mute("usual", side, 0), low ? Rez.Strings.LowsUsual : Rez.Strings.HighsUsual);
        } else if (a == Alerts.MUTE30 && side instanceof String) {
            AppNav.sendAndClose(Outbox.mute("mute", side, 30), low ? Rez.Strings.LowsMuted30 : Rez.Strings.HighsMuted30);
        }
    }

    // the app's own start (the view or the glance): the link registered, the next run at the reading's expiry
    (:glance)
    function start() as Void {
        LinkBackground.register();
        Expiry.schedule(Time.now().value());
    }

    // the glance list (most people see the app here first): registers the background too, so the link runs without
    // the app ever being opened
    (:glance)
    function getGlanceView() as [WatchUi.GlanceView] or [WatchUi.GlanceView, WatchUi.GlanceViewDelegate] or Null {
        LinkBackground.register();
        return [new AppGlance()];
    }

    function onStop(state as Dictionary?) as Void {
        if (_fg) {
            AppNav.end();
        }
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new AppService()];
    }

    // a background run's result, through Garmin (the glance's or the app's process): what came, the payload already
    // kept by the background (AppService.hand); the same run also waits in storage (Link.pickUp at every draw),
    // whichever comes first. The glance redraws from its copy; the app's views from the payload.
    function onBackgroundData(data as Application.PersistableType) as Void {
        var now = Time.now().value();
        if (Link.take(data, now, false)) {
            if (!_fg) {
                WatchUi.requestUpdate();
                return;
            }
            if (AppNav.current != null) {
                (AppNav.current as AppView).refresh();
            }
            if (AppNav.alert != null) {
                (AppNav.alert as AlertView).refresh();
            }
            AppNav.check(true);
        }
    }
}

// The routes into the app that don't start on page 1, and the app's own alert (SPEC §7): while the app is open, a
// kind that starts to ring opens the alert screen with a vibration and the backlight (the background then leaves
// Garmin's notification out: one buzz); opened while one rings, the app shows it first.
module AppNav {
    const SEEN = "sukRungApp"; // the kinds the app last showed
    const FG = "sukFg"; // when the app was last seen open (watch clock): the background's notification waits
    const FG_S = 45;
    var _exit as Timer.Timer? = null;
    var _beat as Timer.Timer? = null;
    var _later as Timer.Timer? = null;
    var current as AppView? = null; // the view on screen (a new payload rebuilds it)
    var alert as AlertView? = null; // the alert screen while it's open

    // the app opened: the beat (the open flag, a new alert) every 10 s; what rings now shown once the first view is up
    function begin() as Void {
        _beat = new Timer.Timer();
        (_beat as Timer.Timer).start(new Lang.Method(AppNav, :beat), 10000, true);
        _later = new Timer.Timer();
        (_later as Timer.Timer).start(new Lang.Method(AppNav, :opened), 300, false);
        open(Time.now().value());
    }

    function end() as Void {
        if (_beat != null) {
            (_beat as Timer.Timer).stop();
            _beat = null;
            Application.Storage.deleteValue(FG);
        }
    }

    function opened() as Void {
        check(false);
    }

    function beat() as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        open(now);
        check(true);
    }

    function open(now as Number) as Void {
        var fg = Application.Storage.getValue(FG);
        if (!(fg instanceof Number) || now - fg > FG_S / 2) {
            Application.Storage.setValue(FG, now);
        }
    }

    // the kinds that ring and weren't shown yet: the first one on the alert screen; [buzz] = the app was already open
    function check(buzz as Boolean) as Void {
        var items = Ring.now(Link.payload(), Time.now().value(), Link.skew);
        var seen = Application.Storage.getValue(SEEN);
        var fresh = Ring.fresh(items, seen instanceof Array ? seen : [] as Array);
        Application.Storage.setValue(SEEN, Ring.kinds(items));
        if (fresh.size() > 0) {
            show(fresh[0].get("k") as String, buzz);
        }
    }

    // the alert screen on the kind [k] (over whatever is open; the open one switches to it)
    function show(k as String, buzz as Boolean) as Void {
        if (alert != null) {
            (alert as AlertView).kind = k;
            (alert as AlertView).sel = 0;
            (alert as AlertView).refresh();
        } else {
            var v = new AlertView(k);
            alert = v;
            WatchUi.pushView(v, new AlertDelegate(v), WatchUi.SLIDE_UP);
        }
        if (buzz) {
            ring(k);
        }
    }

    // a new alert: the backlight and a vibration (longer for very low and extremely high)
    function ring(k as String) as Void {
        if (Attention has :vibrate) {
            var on = AppAlert.very(k) ? 700 : 450;
            Attention.vibrate([new Attention.VibeProfile(100, on), new Attention.VibeProfile(0, 250),
                new Attention.VibeProfile(100, on)]);
        }
        if (Attention has :backlight) {
            try {
                Attention.backlight(true);
            } catch (e) {
                // the watch keeps its own limit
            }
        }
    }

    // the fetch, now (waiting for the phone, SPEC §4.3): the answer as the background's would be taken
    function fetch() as Void {
        Communications.makeWebRequest(LinkBackground.FETCH_URL, null, {
            :method => Communications.HTTP_REQUEST_METHOD_GET,
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, new Lang.Method(AppNav, :fetched));
    }

    function fetched(code as Number, data as Dictionary or String or Null) as Void {
        var now = Time.now().value();
        if (code == 200 && Link.take({"k" => "fetch", "c" => code, "d" => data}, now, false)) {
            GlanceCopy.save(data as Dictionary, Link.skew);
            Outbox.received(data as Dictionary, now);
            if (current != null) {
                (current as AppView).refresh();
            }
            check(true);
        }
    }

    // a short tap that confirms a done action (muted, remind less)
    function confirm() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(60, 60)]);
        }
    }

    function pages() as [AppView, AppDelegate] {
        var v = new AppView("pages");
        return [v, new AppDelegate(v)];
    }

    function list(how as WatchUi.SlideType) as Void {
        var v = new AppView("list");
        WatchUi.pushView(v, new ListDelegate(v), how);
    }

    // an alert's Treatment: the amount on that side's kind (SPEC §7), over the pages; without one, the one list
    function treat(kind as String?) as Void {
        if (kind == null) {
            list(WatchUi.SLIDE_IMMEDIATE);
            return;
        }
        var v = new LogView(kind, 1);
        WatchUi.pushView(v, new LogDelegate(v), WatchUi.SLIDE_IMMEDIATE);
    }

    // the list's Mute alerts (Mute all's lengths) or Muted (Unmute / Change time, for the side that is muted): SPEC §7.
    // Done, the flow goes back to page 1 (the pill says it).
    function mute(muted as Boolean) as Void {
        var x = Link.inputs(Time.now().value());
        var s = x != null ? x.get("side") : null;
        var v = new MuteView(muted && s instanceof String ? s : "all", muted ? "muted" : "lengths", "mute", 2);
        WatchUi.pushView(v, new MuteDelegate(v), WatchUi.SLIDE_LEFT);
    }

    // a notification's Remind less / as usual / Mute: into the outbox, sent at once, said, and the app closes once the
    // phone answered (or after 8 s: the outbox keeps it and the background sends it)
    function sendAndClose(item as Dictionary, said as ResourceId) as Void {
        Outbox.add(item);
        if (WatchUi has :showToast) {
            WatchUi.showToast(said, null);
        }
        Outbox.flush(new Lang.Method(AppNav, :close));
        _exit = new Timer.Timer();
        (_exit as Timer.Timer).start(new Lang.Method(AppNav, :close), 8000, false);
    }

    function close() as Void {
        System.exit();
    }
}
