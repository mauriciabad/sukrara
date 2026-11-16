// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Sukrara Aura: the watch face (contracts/watchface/aura/SPEC.md).
class AuraApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new AuraView(), new AuraDelegate()];
    }

    // the watch's Customize menu (hold MENU on the face)
    function getSettingsView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] or Null {
        var m = new AuraSettingsMenu();
        return [m, new AuraSettingsDelegate(m)];
    }

    // the Connect IQ app changed a setting
    function onSettingsChanged() as Void {
        WatchUi.requestUpdate();
    }

    // always-on starts or ends (API 5: the display's power mode)
    function onDisplayModeChanged() as Void {
        if (System has :getDisplayMode) {
            AuraWatch.lowPower = System.getDisplayMode() == System.DISPLAY_MODE_LOW_POWER;
        }
        WatchUi.requestUpdate();
    }
}

function getApp() as AuraApp {
    return Application.getApp() as AuraApp;
}
