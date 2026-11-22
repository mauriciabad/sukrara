// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
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

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        var c = state != null ? state.get(:launchedFromComplication) : null;
        _from = c instanceof Number ? c : null;
    }

    // where it opens (SPEC §5, §10): holding the Log complication → the one list; the glance, the app list and the
    // other complications → page 1. A notification's action arrives through onNote.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        start();
        Outbox.flush(null);
        if (Toybox has :Notifications && Notifications has :registerForNotificationMessages) {
            Notifications.registerForNotificationMessages(method(:onNote));
        }
        if (_from == Slots.LOG) {
            var v = new AppView("list");
            return [v, new ListDelegate(v)];
        }
        return AppNav.pages();
    }

    // Garmin's notification (AppService Alerts.notify): an action opens the app with it (SPEC §7). Treatment → the
    // amount on the side's kind; Remind less / as usual and Mute 30 min → sent, said, closed; the notification itself
    // → the app as it opened.
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
        if (a == Alerts.TREAT) {
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

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new AppService()];
    }

    // a payload from the background, through Garmin (the glance's or the app's process); the same run also waits in
    // storage (Link.pickUp at every draw), whichever comes first. The complications follow it.
    function onBackgroundData(data as Application.PersistableType) as Void {
        var now = Time.now().value();
        if (Link.take(data, now, false)) {
            WatchUi.requestUpdate();
        }
    }
}

// The routes into the app that don't start on page 1.
module AppNav {
    var _exit as Timer.Timer? = null;

    function pages() as [AppView, AppDelegate] {
        var v = new AppView("pages");
        return [v, new AppDelegate(v)];
    }

    function list(how as WatchUi.SlideType) as Void {
        var v = new AppView("list");
        WatchUi.pushView(v, new ListDelegate(v), how);
    }

    // an alert's Treatment: the one list with that kind selected (the amount screen comes next, SPEC §6)
    function treat(kind as String?) as Void {
        var v = new AppView("list");
        if (kind != null) {
            var p = Link.payload();
            var log = p != null ? p.get("log") : null;
            var order = log instanceof Dictionary && log.get("order") instanceof Array ? log.get("order") as Array : ["rapid", "long", "carbs"];
            var i = order.indexOf(kind);
            v.sel = i < 0 ? 0 : i;
        }
        WatchUi.pushView(v, new ListDelegate(v), WatchUi.SLIDE_IMMEDIATE);
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
