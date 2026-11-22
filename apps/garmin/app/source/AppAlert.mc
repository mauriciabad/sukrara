// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The app's alert (SPEC §7, app.js S.alert): what Sukrara rings on the phone (the payload's ring item), on the wrist
// with the phone's three answers. Very low and extremely high: the whole screen in the band's colour, everything white;
// otherwise a 10 px ring in the colour. The value with Aura's sparkline (the watch setting Sparkline on alerts), or
// the value and arrow bigger.
module AppAlert {
    const START = -1;
    const MIDDLE = 0;
    const YS = [-6, 50, 106];
    const WS = [236, 236, 196];

    function word(k) as String {
        var ids = {"very_low" => Rez.Strings.UVeryLow, "low" => Rez.Strings.ULow, "high" => Rez.Strings.UHigh,
            "very_high" => Rez.Strings.UVeryHigh, "extremely_high" => Rez.Strings.UExtremelyHigh,
            "falling_fast" => Rez.Strings.UFallingFast, "rising_fast" => Rez.Strings.URisingFast};
        var id = ids.get(k);
        return AppWords.s(id != null ? id as ResourceId : Rez.Strings.UNoData);
    }

    // the alert's colour: its band's; a rate alert takes the value's band, no data grey
    function colour(k, d as AppData) as Number {
        var ks = ["very_low", "low", "high", "very_high", "extremely_high"];
        var i = ks.indexOf(k);
        if (i >= 0) {
            return AppMath.pal(["veryLow", "low", "high", "veryHigh", "extremelyHigh"][i] as String);
        }
        return "no_data".equals(k) || d.g == null ? AppMath.GREY : AppMath.pal(d.band(d.g as Number));
    }

    function very(k) as Boolean {
        return "very_low".equals(k) || "extremely_high".equals(k);
    }

    // the answers the phone offers for [it]: Treatment (the side's kind), Remind less only when it would slow it down
    // (Remind as usual once relaxed), Mute (the side's lengths; no data: Mute all's)
    function rows(it as Dictionary) as Array<String> {
        var out = [] as Array<String>;
        if (Ring.treatment(it.get("side")) != null) {
            out.add("treat");
        }
        if (it.get("less") == true) {
            out.add("less");
        } else if (it.get("relaxed") == true) {
            out.add("usual");
        }
        out.add("mute");
        return out;
    }

    // the side a mute or Remind less acts on: low / high, or all (no data)
    function side(it as Dictionary) as String {
        var s = it.get("side");
        return "low".equals(s) || "high".equals(s) ? s as String : "all";
    }

    // the screen: [it] the ring item, [spark] the watch setting, [sel] the selected answer (filled)
    function draw(g as AppG, d as AppData, it as Dictionary, spark as Boolean, sel as Number) as Void {
        var k = it.get("k");
        var col = colour(k, d);
        var v = very(k);
        if (v) {
            g.r(-197, -197, 394, 394, 0, col, 1.0, 0);
        } else {
            g.a(0, 0, 189, 0, 360, col, 1.0, 10, false);
        }
        var ink = v ? AppMath.INK : col;
        var w = word(k);
        g.icon("bell", -AppMath.tw(w, 20) / 2 - 16, -140, 20, ink);
        g.ts(12, -133, w, 20, 700, ink, MIDDLE, 1.5);
        var vc = v ? AppMath.INK : null;
        if (d.noValue()) {
            AppScreens.noValue(g, d, -66);
        } else if (spark && d.hv.size() > 1) {
            // value + sparkline inside the rim: the sparkline gives way (132 → 96)
            var str = AppMath.fmtG(d.g as Number, d.unit);
            var vw = AppMath.bw(str, 84);
            var sw = 132;
            var r = 195 - (v ? 2 : 13);
            var x0 = -(vw + 12 + sw) / 2.0;
            while (sw > 96 && x0 * x0 + 113 * 113 > r * r) {
                sw -= 4;
                x0 = -(vw + 12 + sw) / 2.0;
            }
            AppParts.value(g, d, x0, -54, 84, START, false, vc);
            AppSpark.draw(g, d, [x0 + vw + 12, x0 + vw + 12 + sw, -112, -56], 0.8, v ? col : null);
        } else {
            AppParts.value(g, d, 0, -44, 100, MIDDLE, true, vc);
        }
        var tk = Ring.treatment(it.get("side"));
        var codes = rows(it);
        var sec = v ? AppMath.BLACK : AppMath.CARD2;
        for (var i = 0; i < codes.size(); i++) {
            var c = codes[i];
            var on = i == sel;
            var label = AppWords.s(c.equals("treat") ? Rez.Strings.Treatment : c.equals("less") ? Rez.Strings.RemindLess
                : c.equals("usual") ? Rez.Strings.RemindUsual : Rez.Strings.Mute);
            var icon = c.equals("treat") ? tk : c.equals("mute") ? "bellOff" : null;
            // the selected answer filled (Treatment in its kind's colour, white on the block), the others dark
            var fill = !on ? sec : c.equals("treat") && !v ? AppMath.pal(tk) : AppMath.INK;
            var fg = on ? AppMath.ON : AppMath.INK;
            var ic = on ? AppMath.ON : c.equals("treat") && !v ? AppMath.pal(tk) : AppMath.INK;
            var y = YS[i] as Number;
            var bw = WS[i] as Number;
            g.r(-bw / 2, y - 23, bw, 46, 23, fill, on || !v ? 1.0 : 0.28, 0);
            var tw0 = AppMath.tw(label, 20) + (icon != null ? 26 : 0);
            var tx = -tw0 / 2;
            if (icon != null) {
                g.icon(icon as String, tx + 9, y, 18, ic);
            }
            g.t(tx + (icon != null ? 26 : 0), y + 7, label, 20, 700, fg, START);
        }
        AppScreens.btnHint(g, 60, v ? AppMath.INK : tk != null ? AppMath.pal(tk) : AppMath.INK);
    }
}

// Aura's sparkline (app.js auraSpark, Aura's SPEC §6.5) in [bx] = [x0, x1, y0, y1], scaled by [sc]: a log time axis
// (the newest 10 min widest, up to 3 h as the width allows), 0.6·sc px per mg/dL never rescaled, the window panning on
// the last hour, overflow fading, the limits dotted with the very good band, the trend ray, the end dot. [white]: on
// the very-low block ([bg] its colour), in white.
module AppSpark {
    function draw(g as AppG, d as AppData, bx as Array, sc as Float, bg as Number?) as Void {
        var white = bg != null;
        var bx0 = bx[0] as Float;
        var bx1 = bx[1] as Float;
        var by0 = (bx[2] as Number).toFloat();
        var by1 = (bx[3] as Number).toFloat();
        var k = 100 / Math.ln(7) * sc;
        var sh = by1 - by0;
        var ppm = 0.6 * sc;
        var lo = (d.bands[1] as Number).toFloat();
        var hi = (d.bands[3] as Number).toFloat();
        var span = 10 * (Math.pow(Math.E, (bx1 - bx0 - 24 * sc) / k) - 1);
        var spanMin = span < 180 ? span : 180.0;
        var xNow = bx1 - 24 * sc;
        var last = (d.g as Number).toFloat();
        var hLo = 999.0;
        var hHi = 0.0;
        for (var i = 0; i < d.hm.size(); i++) {
            var v = d.hv[i];
            if (v != null && (d.hm[i] as Numeric) <= 60) {
                var fv = (v as Numeric).toFloat();
                hLo = fv < hLo ? fv : hLo;
                hHi = fv > hHi ? fv : hHi;
            }
        }
        var rng = sh / ppm;
        var top = hHi > hi ? hHi : hi;
        var bot = hLo < lo ? hLo : lo;
        var mid = top - bot <= rng ? (top + bot) / 2 : AppMath.clamp((hLo + hHi) / 2, last - 0.4 * rng, last + 0.4 * rng);
        var p = [by0, by1, sh, mid, ppm, 12 * sc, sc];
        var x0 = X(xNow, k, spanMin);
        var yH = Y(p, hi);
        var yL = Y(p, lo);
        g.r(x0, yH < yL ? yH : yL, xNow - x0, AppMath.abs(yL - yH), 0, white ? AppMath.INK : AppMath.pal("tight"), white ? 0.07 : 0.09, 0);
        var dash = [1.5 * sc, 4.5 * sc];
        g.l([x0, yH, xNow, yH], 0x5C5C62, OP(p, hi), 1.4 * sc, dash);
        g.l([x0, yL, xNow, yL], 0x5C5C62, OP(p, lo), 1.4 * sc, dash);
        var old = d.age >= 5;
        // white at 0.85 over the block, mixed here: a translucent line would show its joints
        var lcol = white ? mix(AppMath.INK, bg as Number, 0.85) : old ? AppMath.DIM : AppMath.SOFT;
        var pm = -1.0; // the previous point (minutes, mg/dL); -1 none
        var pv = 0;
        for (var i = 0; i < d.hm.size(); i++) {
            var m = (d.hm[i] as Numeric).toFloat();
            var v = d.hv[i];
            if (m > spanMin || v == null) {
                pm = -1.0;
                continue;
            }
            if (pm >= 0) {
                var op = OP(p, pv);
                var op2 = OP(p, v as Number);
                g.l([X(xNow, k, pm), Y(p, pv), X(xNow, k, m), Y(p, v as Number)], lcol,
                    op < op2 ? op : op2, 5.4 * sc, null);
            }
            pm = m;
            pv = v as Number;
        }
        var ex = X(xNow, k, d.age);
        var ey = Y(p, last);
        if (!old && d.rate != null) {
            var ang = d.deg * Math.PI / 180;
            var len = 22 * sc;
            var bc = d.band(d.g as Number);
            var rc = white ? AppMath.INK : bc.equals("veryLow") ? AppMath.pal("veryLow") : d.valColour();
            g.l([ex, ey, ex + len * Math.cos(ang), ey + len * Math.sin(ang)], rc, 1.0, 3.4 * sc, [4.5 * sc, 4.5 * sc]);
        }
        if (!old) {
            g.c(ex, ey, 7.5 * sc, white ? AppMath.INK : AppMath.pal(d.band(d.g as Number)), 1.0, 0);
        } else {
            g.c(ex, ey, 7.5 * sc, AppMath.BLACK, 1.0, 0);
            g.c(ex, ey, 7.5 * sc, AppMath.GREY, 1.0, 3 * sc);
        }
    }

    // [a] over [b] at [f]
    function mix(a as Number, b as Number, f as Float) as Number {
        var out = 0;
        for (var s = 0; s <= 16; s += 8) {
            var ca = (a >> s) & 255;
            var cb = (b >> s) & 255;
            out |= ((ca * f + cb * (1 - f) + 0.5).toNumber() & 255) << s;
        }
        return out;
    }

    // the time axis: [m] minutes before now
    function X(xNow as Float, k as Float, m as Numeric) as Float {
        return xNow - k * Math.ln(1 + m / 10.0);
    }

    function y0(p as Array, v as Numeric) as Float {
        return (p[0] as Float) + (p[2] as Float) / 2 - (v - (p[3] as Float)) * (p[4] as Float);
    }

    // the height, softly pinned past the box's edges
    function Y(p as Array, v as Numeric) as Float {
        var y = y0(p, v);
        var b = p[5] as Float;
        if (y < (p[0] as Float)) {
            return (p[0] as Float) - b * (1 - Math.pow(Math.E, (y - (p[0] as Float)) / b));
        }
        if (y > (p[1] as Float)) {
            return (p[1] as Float) + b * (1 - Math.pow(Math.E, ((p[1] as Float) - y) / b));
        }
        return y;
    }

    // past the box's edges the line fades
    function OP(p as Array, v as Numeric) as Float {
        var y = y0(p, v);
        var out = y < (p[0] as Float) ? (p[0] as Float) - y : y > (p[1] as Float) ? y - (p[1] as Float) : 0;
        var s6 = 6 * (p[6] as Float);
        if (out <= 0) {
            return 1.0;
        }
        if (out <= s6) {
            return 1 - 0.55 * out / s6;
        }
        var o = 0.45 - 0.3 * (out - s6) / s6;
        return o > 0.15 ? o : 0.15;
    }
}

// The alert on its own view: built in its own callback (shown, the 10 s tick, a new payload), closed once its kind no
// longer rings (answered, muted or ended on the phone). UP / DOWN choose an answer, START takes it, BACK leaves (the
// alert stays on the phone).
class AlertView extends WatchUi.View {
    var kind as String;
    var sel as Number = 0;
    var item as Dictionary? = null;
    var shown as Boolean = false;
    var _g as AppG? = null;
    var _timer as Timer.Timer? = null;

    function initialize(k as String) {
        View.initialize();
        kind = k;
    }

    function onShow() as Void {
        shown = true;
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:refresh), 10000, true);
        build();
        if (item == null) {
            // it ended while something covered it: leave in a callback of its own
            (_timer as Timer.Timer).stop();
            _timer = new Timer.Timer();
            (_timer as Timer.Timer).start(method(:close), 50, false);
        }
    }

    function onHide() as Void {
        shown = false;
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
        _g = null;
        AppDraw.release();
    }

    // the ring item for this kind, else the first that rings (one ended, another goes on), else null
    function find(now as Number) as Dictionary? {
        var items = Ring.now(Link.payload(), now, Link.skew);
        for (var i = 0; i < items.size(); i++) {
            if (kind.equals(items[i].get("k"))) {
                return items[i];
            }
        }
        if (items.size() > 0) {
            kind = items[0].get("k") as String;
            sel = 0;
            return items[0];
        }
        return null;
    }

    function build() as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        AppView.setup();
        item = find(now);
        if (item == null) {
            _g = null;
            return;
        }
        var g = new AppG();
        var sp = Application.Properties.getValue(SettingsSpec.ALERT_SPARK[0] as String);
        AppAlert.draw(g, AppInput.build(now, true), item as Dictionary, sp != false, sel);
        _g = g;
    }

    function refresh() as Void {
        if (!shown) {
            return;
        }
        build();
        if (item == null) {
            close();
            return;
        }
        WatchUi.requestUpdate();
    }

    function close() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
        if (AppNav.alert == self) {
            AppNav.alert = null;
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
    }

    function codes() as Array<String> {
        return item != null ? AppAlert.rows(item as Dictionary) : [] as Array<String>;
    }

    function move(by as Number) as Void {
        var n = codes().size();
        if (n > 0) {
            sel = (sel + by + n) % n;
            refresh();
        }
    }

    // Treatment → the amount on the side's kind; Remind less / as usual → sent, done with Undo; Mute → the lengths
    function choose(i as Number) as Void {
        var cs = codes();
        if (i < 0 || i >= cs.size()) {
            return;
        }
        var it = item as Dictionary;
        var c = cs[i];
        if (c.equals("treat")) {
            AppNav.treat(Ring.treatment(it.get("side")));
        } else {
            var v = c.equals("mute") ? new MuteView(AppAlert.side(it), "lengths", "mute", 1)
                : new MuteView(AppAlert.side(it), "sending", c, 1);
            WatchUi.pushView(v, new MuteDelegate(v), WatchUi.SLIDE_LEFT);
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (_g == null) {
            build();
        }
        if (_g != null) {
            AppDraw.paint(dc, _g as AppG);
        }
    }
}

class AlertDelegate extends WatchUi.BehaviorDelegate {
    var view as AlertView;

    function initialize(v as AlertView) {
        BehaviorDelegate.initialize();
        view = v;
    }

    function onNextPage() as Boolean {
        view.move(1);
        return true;
    }

    function onPreviousPage() as Boolean {
        view.move(-1);
        return true;
    }

    function onSelect() as Boolean {
        view.choose(view.sel);
        return true;
    }

    function onBack() as Boolean {
        view.close();
        return true;
    }

    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var y = (e.getCoordinates()[1] - AppDraw.oy) / AppDraw.s;
        var i = AppMute.hit(AppAlert.YS, view.codes().size(), y, 25);
        if (i >= 0) {
            view.sel = i;
            view.choose(i);
        }
        return true;
    }
}
