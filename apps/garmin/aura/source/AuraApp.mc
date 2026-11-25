// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Sukrara Aura: the watch face (contracts/watchface/aura/SPEC.md). Also loaded by its background (the phone link,
// apps/garmin/shared/link), hence (:background).
(:background)
class AuraApp extends Application.AppBase {
    // The face's process (getInitialView ran), not the background's. The watch calls onDisplayModeChanged (and may call
    // onSettingsChanged) in the background process too, where only (:background) code is loaded: AuraWatch and WatchUi
    // there crash it ("Illegal Access (Out of Bounds)", "Failed invoking <symbol>", the owner's Descent, #223).
    var face as Boolean = false;

    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        face = true;
        LinkBackground.register();
        return [new AuraView(), new AuraDelegate()];
    }

    // the phone link's background: Sukrara's push and the 5-minute fetch (docs/43)
    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new LinkService()];
    }

    // a payload from the background (contracts/watchface/payload), through Garmin; the same run also waits in storage
    // (Link.pickUp at every draw), whichever comes first
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (Link.take(data, Toybox.Time.now().value(), false)) {
            AuraData.changed();
        }
    }

    // the watch's Customize menu (hold MENU on the face)
    function getSettingsView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] or Null {
        var m = new AuraSettingsMenu();
        return [m, new AuraSettingsDelegate(m)];
    }

    // the Connect IQ app changed a setting
    function onSettingsChanged() as Void {
        if (face) {
            AuraWatch.changed();
            WatchUi.requestUpdate();
        }
    }

    // always-on starts or ends (API 5: the display's power mode)
    function onDisplayModeChanged() as Void {
        if (face) {
            AuraWatch.screen("C", AuraWatch.displayMode() == 1);
            WatchUi.requestUpdate();
        }
    }
}

function getApp() as AuraApp {
    return Application.getApp() as AuraApp;
}
