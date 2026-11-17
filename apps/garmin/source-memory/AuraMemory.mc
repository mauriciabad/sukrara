// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// The face's own memory (#217; build.sh memory, debug only): the release face's code, a watch face (128 KB), given the
// heaviest state through its real path (AuraLink.receive, the phone's payload): high and rising with the pill, 3 h of
// history with the sparkline, the last treatment, the sensor's dates, both sides glucose rails, every field on. Each
// frame prints the memory in use after the layout and after the paint (the fonts it loaded still held): MEM|…
(:debug)
class AuraMemoryApp extends AuraApp {
    function initialize() {
        AuraApp.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var now = Time.now().value();
        var hist = [];
        for (var i = 0; i < 36; i++) {
            hist.add(150 + 40 * Math.sin(i / 5.0) + i * 2);
        }
        AuraLink.receive({
            "v" => 3, "sent" => now, "unit" => "mgdl", "bands" => [55, 70, 140, 180, 250, 300],
            "g" => {"t" => now - 30, "v" => 246, "rate" => 1.6, "band" => 2, "deg" => -48.0},
            "hist" => hist,
            "st" => {"k" => "needed", "side" => "high"},
            "last" => {"t" => now - 4800, "items" => [{"k" => "rapid", "a" => 2.5}, {"k" => "carbs", "a" => 45}]},
            "sensor" => {"s" => "active", "start" => now - 864000, "end" => now + 432000, "warn" => now + 403200},
            "tir" => {"pct" => 82, "until" => now + 36000},
        }, now);
        AuraSettings.set(SettingsSpec.SIDE_L, 2);
        AuraSettings.set(SettingsSpec.SIDE_R, 2);
        AuraSettings.set(SettingsSpec.TREND, 1);
        AuraSettings.set(SettingsSpec.DIAL, 0);
        return [new AuraMemoryView(), new AuraDelegate()];
    }
}

(:debug)
class AuraMemoryView extends AuraView {
    var peak as Number = 0;

    function initialize() {
        AuraView.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var m = new AuraModel(input());
        var laid = System.getSystemStats().usedMemory;
        AuraDraw.draw(dc, m, 0, 0);
        var st = System.getSystemStats();
        var drawn = st.usedMemory;
        if (laid > peak) {
            peak = laid;
        }
        if (drawn > peak) {
            peak = drawn;
        }
        System.println("MEM|" + dc.getWidth() + "|" + laid + "|" + drawn + "|" + peak + "|" + st.totalMemory + "|" +
            AuraDials.painting());
    }
}
