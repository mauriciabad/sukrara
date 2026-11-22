// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// Sukrara, the watch app (docs/47, apps/garmin/README.md): its glance (AppGlance), its view (AppView), its background
// on the shared link (AppService: Sukrara's push to this app's id, the 5-minute fetch of /sukrara/app) and the
// complications it publishes (AppComplications). Loaded by the background and the glance too, hence the annotations.
(:background :glance)
class SukraraApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        start();
        return [new AppView(), new WatchUi.BehaviorDelegate()];
    }

    // the app's own start (the view or the glance): the link registered, the next run at the reading's expiry, the
    // complications from what the app has (guarded: docs/47 §3), the outbox sent at once
    (:glance)
    function start() as Void {
        LinkBackground.register();
        var now = Time.now().value();
        Expiry.schedule(now);
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
    // storage (Link.pickUp at every draw), whichever comes first
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (Link.take(data, Time.now().value(), false)) {
            WatchUi.requestUpdate();
        }
    }
}
