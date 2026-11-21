// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The face: every draw builds the input model from the watch (AuraWatch, THE SEAM inside), lays it out (AuraModel) and
// paints it (AuraDraw). Always-on (low power) shifts the whole face a little every minute against burn-in (SPEC.md §8).
class AuraView extends WatchUi.WatchFace {
    // the glide on a new reading (SPEC.md §7): the bead, the trail and the arrow move to their new angles in 0.9 s.
    // Only in high power (the ~10 s after a wrist raise, when a watch face may run a timer): a reading that arrives in
    // low power is drawn in its place.
    const GLIDE_MS = 900;
    const FRAME_MS = 40;
    // May the glide run its timer: from a wrist raise or a button (onExitSleep) to onEnterSleep, onHide or onShow.
    // Timer.start crashes a watch face in low power, and a face shown again (back from a notification or a menu) can
    // already be in low power with no onEnterSleep to follow (#223).
    var high as Boolean = false;
    var target as Array? = null; // the angles the last awake frame aimed at: [bead, trail (Array), arrow]
    var shown as Array? = null; // the angles the last frame drew
    var from as Array? = null; // a glide's start, null when none runs
    var glideStart as Number = 0;
    var timer as Timer.Timer? = null;

    function initialize() {
        WatchFace.initialize();
    }

    function trailDegs(m as AuraModel) as Array {
        var d = [];
        for (var i = 0; i < m.trail.size(); i++) {
            d.add((m.trail[i] as Array)[0]);
        }
        return d;
    }

    function moved(a as Array, b as Array) as Boolean {
        if ((a[0] as Float) != (b[0] as Float) || (a[2] as Float) != (b[2] as Float)) {
            return true;
        }
        var ta = a[1] as Array;
        var tb = b[1] as Array;
        if (ta.size() != tb.size()) {
            return true;
        }
        for (var i = 0; i < ta.size(); i++) {
            if ((ta[i] as Float) != (tb[i] as Float)) {
                return true;
            }
        }
        return false;
    }

    function tick() as Void {
        WatchUi.requestUpdate();
    }

    function stopGlide() as Void {
        from = null;
        if (timer != null) {
            (timer as Timer.Timer).stop();
            timer = null;
        }
    }

    // the glide's frames; null when the watch refuses the timer (the reading is then drawn in place)
    function glideFrames() as Timer.Timer? {
        try {
            var t = new Timer.Timer();
            t.start(method(:tick), FRAME_MS, true);
            return t;
        } catch (e) {
            return null;
        }
    }

    // starts a glide when the angles changed during high power, and draws its frame
    function glide(m as AuraModel) as Void {
        var tgt = [m.beadA, trailDegs(m), m.deg];
        var awake = high && m.awake && AuraWatch.highPower();
        if (awake && target != null && shown != null && moved(tgt, target as Array)) {
            from = shown;
            glideStart = System.getTimer();
            if (timer == null) {
                timer = glideFrames();
                if (timer == null) {
                    from = null;
                }
            }
        }
        // only an awake frame is a glide's start: always-on and night draw no trail (or another look), and waking from
        // them is no new reading
        target = m.awake ? tgt : null;
        if (from != null) {
            var t = (System.getTimer() - glideStart).toFloat() / GLIDE_MS;
            if (t >= 1 || !awake) {
                stopGlide();
            } else {
                var e = AuraMath.ease(t);
                var f = from as Array;
                m.beadDrawA = (f[0] as Float) + ((tgt[0] as Float) - (f[0] as Float)) * e;
                var ft = f[1] as Array;
                for (var i = 0; i < m.trail.size() && i < ft.size(); i++) {
                    var p = m.trail[i] as Array;
                    m.trail[i] = [(ft[i] as Float) + ((p[0] as Float) - (ft[i] as Float)) * e, p[1], p[2]];
                }
                m.arrow((f[2] as Float) + ((tgt[2] as Float) - (f[2] as Float)) * e);
            }
        }
        shown = [m.beadDrawA, trailDegs(m), m.drawDeg];
    }

    function onLayout(dc as Graphics.Dc) as Void {}

    // shown (back from a menu or a notification, the face picked): the awake look; onEnterSleep or a low-power display
    // mode follows if not. No motion until a wrist raise: the watch may already be in low power (#223).
    function onShow() as Void {
        high = false;
        AuraWatch.screen("S", false);
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        // what the background left in storage, when Garmin didn't hand it over (#244): this draw shows it already
        AuraLink.pickUp(Time.now().value());
        var m = new AuraModel(input());
        AuraWatch.drew(m.aod ? "a" : m.night ? "n" : "w");
        glide(m);
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
        high = true;
        AuraWatch.screen("X", false);
        WatchUi.requestUpdate();
    }

    // low power: always-on if the person turned it on (AMOLED), else the screen goes dark
    function onEnterSleep() as Void {
        high = false;
        stopGlide();
        AuraWatch.screen("E", System.getDeviceSettings().requiresBurnInProtection);
        WatchUi.requestUpdate();
    }

    function onHide() as Void {
        high = false;
        stopGlide();
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
