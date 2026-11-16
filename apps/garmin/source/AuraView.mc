// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// The face: every draw builds the input model from the watch (AuraWatch, THE SEAM inside), lays it out (AuraModel) and
// paints it (AuraDraw). Always-on (low power) shifts the whole face a little every minute against burn-in (SPEC.md §8).
class AuraView extends WatchUi.WatchFace {
    function initialize() {
        WatchFace.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {}

    function onShow() as Void {
        AuraWatch.screen("S", AuraWatch.displayMode() == 1);
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var m = new AuraModel(input());
        AuraWatch.drew(m.aod ? "a" : m.night ? "n" : "w");
        var sx = 0;
        var sy = 0;
        if (m.aod) {
            // Garmin's burn-in rule: move the whole face up to 4 px a minute; a 9-step square walk, ±2 px
            var steps = [[0, 0], [2, 0], [2, 2], [0, 2], [-2, 2], [-2, 0], [-2, -2], [0, -2], [2, -2]];
            var s = steps[(Time.now().value() / 60) % 9] as Array<Number>;
            sx = s[0];
            sy = s[1];
        }
        AuraDraw.draw(dc, m, sx, sy);
    }

    // the input model (the debug case loader gives a case's instead)
    function input() as AuraInput {
        return AuraWatch.input();
    }

    // high power (a wrist raise, a button): 1 Hz for ~10 s. Always awake, whatever the display mode said before.
    function onExitSleep() as Void {
        AuraWatch.screen("X", false);
        WatchUi.requestUpdate();
    }

    // low power: always-on if the person turned it on (AMOLED), else the screen goes dark
    function onEnterSleep() as Void {
        AuraWatch.screen("E", System.getDeviceSettings().requiresBurnInProtection);
        WatchUi.requestUpdate();
    }

    function onHide() as Void {
        AuraDraw.release();
    }
}

// Touch and hold on the face (the Descent has no tap on faces): in private, shows the value for 5 s (SPEC.md §8).
class AuraDelegate extends WatchUi.WatchFaceDelegate {
    function initialize() {
        WatchFaceDelegate.initialize();
    }

    function onPress(e as WatchUi.ClickEvent) as Boolean {
        if (!AuraSettings.flag(SettingsSpec.PRIVATE)) {
            return false; // not ours: the watch keeps its own hold
        }
        AuraWatch.revealUntil = System.getTimer() + AuraWatch.REVEAL_MS;
        WatchUi.requestUpdate();
        return true;
    }

    function onPowerBudgetExceeded(info as WatchUi.WatchFacePowerInfo) as Void {}
}
