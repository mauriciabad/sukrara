// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// Logging (SPEC §6), ported from app.js S.logAmount, S.logConfirm, S.logSending, S.logDone, S.logQueued, plus the
// phone's pen question: each screen pushes its display list from the flow's state (LogView).
module AppLog {
    const START = -1;
    const MIDDLE = 0;

    function kindName(k as String) as String {
        return AppWords.s(k.equals("carbs") ? Rez.Strings.KindCarbs : k.equals("long") ? Rez.Strings.KindLong : Rez.Strings.KindRapid);
    }

    // "rapid insulin" (the hold screen's line)
    function kindWord(k as String) as String {
        return AppWords.s(k.equals("carbs") ? Rez.Strings.LCarbs : k.equals("long") ? Rez.Strings.LLong : Rez.Strings.LRapid);
    }

    // "4.5u rapid" (sending, logged, queued)
    function what(k as String, a as Float) as String {
        var w = AppWords.s(k.equals("carbs") ? Rez.Strings.SCarbs : k.equals("long") ? Rez.Strings.SLong : Rez.Strings.SRapid);
        return AppMath.amt(k, a) + " " + w;
    }

    // the amount as the big number shows it: "4.5", "14", "50"
    function num(k as String, a as Float) as String {
        var s = AppMath.amt(k, a);
        return s.substring(0, s.length() - 1) as String;
    }

    // when it was taken: "Now" / "15 min ago" (lower case on the hold screen)
    function when(ago as Number, lower as Boolean) as String {
        if (ago == 0) {
            return AppWords.s(lower ? Rez.Strings.WNow : Rez.Strings.Now);
        }
        return Lang.format(AppWords.s(Rez.Strings.MinAgo), [ago]);
    }

    // what an entry of [k] taken [ago] min before now quiets, as the phone's Add treatment says it ("Highs quiet for
    // 1 h"; payload log.<kind>.q, counted from the entry's time); null when nothing (or no longer anything)
    function quiet(k as String, ago as Number) as String? {
        var p = Link.payload();
        var log = p != null ? p.get("log") : null;
        var kd = log instanceof Dictionary ? log.get(k) : null;
        var q = kd instanceof Dictionary ? kd.get("q") : null;
        if (!(q instanceof Dictionary) || !(q.get("min") instanceof Number)) {
            return null;
        }
        var m = (q.get("min") as Number) - ago;
        if (m <= 0) {
            return null;
        }
        var len = m < 60 ? m + " min" : m % 60 == 0 ? (m / 60) + " h" : (m / 60) + " h " + (m % 60) + " min";
        return Lang.format(AppWords.s("low".equals(q.get("side")) ? Rez.Strings.QuietLows : Rez.Strings.QuietHighs), [len]);
    }

    // a hint at a button: a short arc on the rim at its angle, and a label beside it in small (app.js btnHint)
    function hint(g as AppG, ang as Numeric, label as String?, col as Number) as Void {
        var at = AppScreens.btnHint(g, ang, col);
        if (label != null && at != null) {
            var i = [60, 120, 270, 240].indexOf(ang);
            var p = AppMath.P(ang == 270 ? 170 : 166, AppFontTable.KEYS[i] as Number);
            g.w(p[0] - 4, p[1] + AppDraw.capOf(AppDraw.SM) / 2, label, AppDraw.SM, col, START);
        }
    }

    // ---- the amount (S.logAmount) ------------------------------------------------------------------------------
    // the amount's geometry (units): round (app.js S.logAmount) or the rectangles' own (R.logAmount: no UP / DOWN, the
    // chevrons full-width buttons above and below the number): [the label's y, number's baseline, its size, the
    // unit's size, the up chevron's y, the down one's, the chips' top, chip w, gap, chip h, the time's y]
    function amountAt() as Array {
        var cS = AppDraw.capOf(AppDraw.SM);
        if (!AppFontTable.RECT) {
            var ch = cS + 16;
            return [-122, 22, 128, 50, -83, 49, 74, 64, 10, ch, 74 + ch + 10 + AppDraw.capOf(AppDraw.TI)];
        }
        var h = AppScreens.hh();
        var chh = cS + 14;
        return [-h + 14 + cS, -h + 194, 120, 48, -h + 82, -h + 228, h - 16 - AppDraw.lh(AppDraw.TI) - chh, 72, 12, chh, h - 14];
    }

    function amount(g as AppG, k as String, a as Float, usual as Array, ago as Number) as Void {
        var L = amountAt();
        var rect = AppFontTable.RECT;
        var w0 = AppScreens.hw();
        var col = AppMath.pal(k);
        var label = kindName(k);
        var SM = AppDraw.SM;
        var lf = AppDrawApp.fitRole(label, SM, rect ? 2 * w0 - 60 : 220);
        var lw = AppDraw.ww(label, lf) + 30;
        var ly = L[0] as Numeric;
        g.icon(k, -lw / 2 + 10, ly - AppDraw.capOf(lf) / 2, 18, col);
        g.w(-lw / 2 + 26, ly, label, lf, col, START);
        var s = num(k, a);
        var unit = k.equals("carbs") ? "g" : "u";
        var w = AppMath.bw(s, L[2] as Numeric);
        var uw = AppMathApp.tw(unit, L[3] as Numeric);
        var x0 = -(w + 8 + uw) / 2;
        g.n(x0, L[1], s, L[2] as Numeric, AppMath.INK, START);
        g.t(x0 + w + 8, L[1], unit, L[3] as Numeric, 600, AppMath.SOFT, START);
        if (rect) {
            for (var i = 0; i < 2; i++) {
                var cy = (L[4 + i] as Numeric);
                var dir = i == 0 ? -1 : 1;
                g.r(-w0 + 40, cy - 20, 2 * w0 - 80, 40, 20, AppMath.CARD2, 1.0, 0);
                g.l([-16, cy - 6 * dir, 0, cy + 6 * dir, 16, cy - 6 * dir], AppMath.SOFT, 1.0, 4, null);
            }
        } else {
            g.l([-16, -76, 0, -90, 16, -76], AppMath.DIM, 1.0, 4, null);
            g.l([-16, 42, 0, 56, 16, 42], AppMath.DIM, 1.0, 4, null);
        }
        var cw = L[7] as Numeric;
        var gap = L[8] as Numeric;
        var ct = L[6] as Numeric;
        var chh = L[9] as Numeric;
        var cx0 = -(usual.size() * cw + (usual.size() - 1) * gap) / 2;
        for (var i = 0; i < usual.size(); i++) {
            var ch = num(k, (usual[i] as Numeric).toFloat());
            var on = ch.equals(s);
            var x = cx0 + i * (cw + gap);
            g.r(x, ct, cw, chh, chh / 2, on ? col : AppMath.CARD2, 1.0, 0);
            g.w(x + cw / 2, ct + chh / 2 + AppDraw.capOf(SM) / 2, ch, SM, on ? AppMath.ON : AppMath.SOFT, MIDDLE);
        }
        g.w(0, L[10], when(ago, false), AppDraw.TI, AppMath.SOFT, MIDDLE);
        hint(g, 60, null, AppMath.INK);
        if (!rect) {
            hint(g, 270, "+", AppMath.INK);
            hint(g, 240, "-", AppMath.INK);
        }
    }

    // ---- hold to log (S.logConfirm): [dup] the same kind's entry of the last 15 min ("4.5u 3 min ago"), or null --
    function confirm(g as AppG, k as String, a as Float, ago as Number, dup as String?, p as Float, q as String?) as Void {
        var col = AppMath.pal(k);
        var TI = AppDraw.TI;
        var SM = AppDraw.SM;
        // the rim fills from START while it's held; drawn first, so the words keep inside it
        AppScreens.rim(g, AppMath.GRID, 8, 1.0);
        AppScreens.rim(g, col, 8, p);
        if (dup != null) {
            var df = AppDrawApp.fitRole(dup, TI, 200);
            var dw = AppDraw.ww(dup, df) + 28;
            g.icon("warn", -dw / 2 + 11, -122 - AppDraw.capOf(df) / 2, 22, AppMath.pal("high"));
            g.w(-dw / 2 + 28, -122, dup, df, AppMath.pal("high"), START);
        } else {
            g.w(0, -122, AppWords.s(Rez.Strings.Log), SM, AppMath.SOFT, MIDDLE);
        }
        var s = num(k, a);
        var unit = k.equals("carbs") ? "g" : "u";
        var wN = AppMath.bw(s, 96);
        var wU = AppMathApp.tw(unit, 96) * 0.97;
        var x0 = -(30 + 12 + wN + wU) / 2;
        g.icon(k, x0 + 15, -40, 30, col);
        g.n(x0 + 42, -8, s, 96, col, START);
        g.t(x0 + 42 + wN, -8, unit, 96, 700, col, START);
        AppScreens.fitW(g, 30, Lang.format(AppWords.s(Rez.Strings.AmountWhen), [kindWord(k), when(ago, true)]), SM, AppMath.INK);
        if (q != null) {
            AppScreens.fitW(g, 36 + AppDraw.capOf(TI) + 10, q, TI, AppMath.SOFT);
        }
        var ht = AppWords.s(Rez.Strings.HoldTouch);
        var hf = AppDrawApp.fitRole(ht, SM, 200);
        var hw0 = AppDraw.ww(ht, hf) + 44;
        var hh0 = AppDraw.capOf(SM) + 20;
        g.r(-hw0 / 2, 102, hw0, hh0, hh0 / 2, AppMath.CARD2, 1.0, 0);
        g.w(0, 102 + hh0 / 2 + AppDraw.capOf(hf) / 2, ht, hf, AppMath.INK, MIDDLE);
        hint(g, 60, null, col);
    }

    // ---- sending (S.logSending): [spin] the arc's turn, degrees ---------------------------------------------------
    function sending(g as AppG, k as String, a as Float, spin as Number) as Void {
        sendingAs(g, AppMath.pal(k), what(k, a), spin);
    }

    // any write on its way to the phone (an entry, a mute, Remind less): [col] its colour, [title] what it is
    function sendingAs(g as AppG, col as Number, title as String, spin as Number) as Void {
        AppScreens.rim(g, col, 8, 1.0);
        g.c(0, -36, 46, AppMath.GRID, 1.0, 6);
        g.a(0, -36, 46, 20 + spin, 130 + spin, col, 1.0, 6, false);
        g.icon("phone", 0, -36, 30, AppMath.SOFT);
        AppScreens.fitW(g, 46, title, AppDraw.ME, AppMath.INK);
        AppScreens.fitW(g, 56 + AppDraw.capOf(AppDraw.TI) + 6, AppWords.s(Rez.Strings.SendingPhone), AppDraw.TI, AppMath.SOFT);
    }

    // a done screen's Undo (small) with its countdown ring ([left] the share of the 10 s left; app.js undo)
    function undo(g as AppG, y as Numeric, left as Float) as Void {
        var SM = AppDraw.SM;
        var u = AppWords.s(Rez.Strings.Undo);
        var h = AppDraw.capOf(SM) + 18;
        var w = AppDraw.ww(u, SM) + 30 + 26;
        g.r(-w / 2, y, w, h, h / 2, AppMath.CARD2, 1.0, 0);
        g.w(-w / 2 + 18, y + h / 2 + AppDraw.capOf(SM) / 2, u, SM, AppMath.INK, START);
        var rx = w / 2 - 22;
        g.c(rx, y + h / 2, 9, AppMath.GRID, 1.0, 3);
        if (left > 0) {
            g.a(rx, y + h / 2, 9, 0, 360 * left, AppMath.SOFT, 1.0, 3, false);
        }
    }

    // ---- logged (S.logDone) -----------------------------------------------------------------------------------
    function done(g as AppG, k as String, a as Float, left as Float, q as String?) as Void {
        var col = AppMath.pal(k);
        g.c(0, -46, 50, col, 1.0, 0);
        g.l([-22, -46, -8, -32, 22, -62], AppMath.ON, 1.0, 8, null);
        AppScreens.fitW(g, 40, Lang.format(AppWords.s(Rez.Strings.Logged), [what(k, a)]), AppDraw.ME, AppMath.INK);
        if (q != null) {
            AppScreens.fitW(g, 50 + AppDraw.capOf(AppDraw.TI) + 6, q, AppDraw.TI, AppMath.SOFT);
        }
        if (left > 0) {
            undo(g, 96, left);
            hint(g, 60, null, AppMath.INK);
        }
    }

    // ---- the phone out of reach (S.logQueued): [at] the entry's time ("20:41") --------------------------------
    function queued(g as AppG, k as String, a as Float, at as String) as Void {
        var hi = AppMath.pal("high");
        g.icon("phone", 0, -60, 46, hi);
        g.line(-24, -84, 24, -36, hi, 4);
        var TI = AppDraw.TI;
        var cT = AppDraw.capOf(TI);
        AppScreens.fitW(g, 10, AppWords.s(Rez.Strings.SavedWatch), AppDraw.ME, AppMath.INK);
        AppScreens.fitW(g, 18 + cT + 8, what(k, a) + " · " + at, TI, AppMath.pal(k));
        var y2 = 18 + cT + 8 + 14 + cT;
        AppScreens.fitW(g, y2, AppWords.s(Rez.Strings.GoesPhone), TI, AppMath.SOFT);
        AppScreens.fitW(g, y2 + AppDraw.lh(TI), AppWords.s(Rez.Strings.WhenNear), TI, AppMath.SOFT);
    }

    // ---- the pen question (the phone's "Already logged by your pen?"): [pen] "4.5u at 20:31"; [sel] 0 Cancel, 1 Log anyway
    function pen(g as AppG, k as String, pen as String, sel as Number) as Void {
        var col = AppMath.pal(k);
        // the question on one line under the pen (clear of the button hint at the edge), else on two in xtiny without
        // the pen (the room it takes)
        var t = AppWords.s(Rez.Strings.PenAsk);
        var yb = -70.0;
        if (AppDraw.ww(t, AppDraw.XT) <= AppScreens.lineW(-70, AppDraw.XT) - 30) {
            g.icon("pen", 0, -118, 30, col);
            AppScreens.fitW(g, -70, t, AppDraw.ME, AppMath.INK);
        } else {
            yb = AppScreens.para(g, -96, t, AppDraw.XT, AppMath.INK);
        }
        AppScreens.fitW(g, yb + 14 + AppDraw.capOf(AppDraw.TI), pen, AppDraw.TI, AppMath.SOFT);
        var items = [AppWords.s(Rez.Strings.Cancel), AppWords.s(Rez.Strings.LogAnyway)];
        var ys = [10, 70];
        for (var i = 0; i < 2; i++) {
            var on = i == sel;
            g.r(-130, ys[i] - 26, 260, 52, 26, on ? AppMath.INK : AppMath.CARD2, 1.0, 0);
            g.w(0, ys[i] + AppDraw.capOf(AppDraw.SM) / 2, items[i], AppDrawApp.fitRole(items[i], AppDraw.SM, 230), on ? AppMath.ON : AppMath.INK, MIDDLE);
        }
        hint(g, 60, null, AppMath.INK);
    }

    // ---- a short word that closes by itself (Not logged: too old, Undone …) ------------------------------------
    function said(g as AppG, text as String) as Void {
        AppScreens.para(g, 8, text, AppDraw.ME, AppMath.INK);
    }
}

// The logging flow on one view (SPEC §6, §12): amount → hold to log → sending → logged (Undo 10 s) / Waiting for the
// phone / the pen question / what the phone said. BACK on the hold screen goes back to the amount; elsewhere it leaves.
class LogView extends WatchUi.View {
    const HOLD_MS = 1000;
    const UNDO_MS = 10000;
    const WAIT_MS = 8000;
    const LONG_STEP = 1.0;

    var k as String;
    var a as Float = 0.0;
    var usual as Array = [] as Array;
    var ago as Number = 0; // minutes before now (MENU steps it back)
    var state as String = "amount"; // amount, confirm, sending, done, queued, pen, undoing, said
    var dup as String? = null;
    var progress as Float = 0.0;
    var id as String? = null;
    var at as Number = 0; // the entry's time, watch clock
    var sel as Number = 0;
    var penText as String = "";
    var saidText as String = "";
    var depth as Number = 1; // the views to pop when the flow ends (the list under it too)
    var _t0 as Number = 0;
    var _timer as Timer.Timer? = null;
    var _spin as Number = 0;

    function initialize(kind as String, d as Number) {
        View.initialize();
        k = kind;
        depth = d;
        var p = Link.payload();
        var log = p != null ? p.get("log") : null;
        var kd = log instanceof Dictionary ? log.get(k) : null;
        if (kd instanceof Dictionary) {
            var u = kd.get("usual");
            usual = u instanceof Array ? u : [] as Array;
            var last = kd.get("last");
            if (last instanceof Number || last instanceof Float) {
                a = (last as Numeric).toFloat();
            }
        }
        if (a <= 0) {
            a = usual.size() > 0 ? (usual[usual.size() / 2] as Numeric).toFloat() : k.equals("carbs") ? 20.0 : k.equals("long") ? 10.0 : 2.0;
        }
    }

    function step() as Float {
        return k.equals("carbs") ? 5.0 : k.equals("long") ? LONG_STEP : 0.5;
    }

    function change(by as Number) as Void {
        var max = k.equals("carbs") ? 500.0 : 100.0;
        var v = a + by * step();
        // UP / DOWN land on the step's grid
        v = Math.round(v / step()).toFloat() * step();
        a = v < step() ? step() : v > max ? max : v;
        WatchUi.requestUpdate();
    }

    // MENU: earlier (now, 15, 30, 45 min, 1 h, 1.5 h, 2 h ago, then now again)
    function earlier() as Void {
        var steps = [0, 15, 30, 45, 60, 90, 120];
        var i = steps.indexOf(ago);
        ago = steps[(i + 1) % steps.size()] as Number;
        WatchUi.requestUpdate();
    }

    // the same kind logged in the last 15 min (from the payload's treatments), as the hold screen says it
    function same() as String? {
        var d = AppInput.build(Time.now().value(), false);
        for (var i = d.trk.size() - 1; i >= 0; i--) {
            var m = (d.trm[i] as Float).toNumber();
            if (k.equals(d.trk[i]) && m >= 0 && m <= 15 + ago) {
                return Lang.format(AppWords.s(Rez.Strings.SameKind), [AppMath.amt(k, d.tra[i] as Numeric), m + " min"]);
            }
        }
        return null;
    }

    function toConfirm() as Void {
        dup = same();
        progress = 0.0;
        state = "confirm";
        WatchUi.requestUpdate();
    }

    function back() as Boolean {
        if (state.equals("confirm")) {
            stopTimer();
            state = "amount";
            WatchUi.requestUpdate();
            return true;
        }
        close();
        return true;
    }

    // ---- hold to log: START (or a touch) held fills the rim over HOLD_MS; letting go cancels ------------------
    function press() as Void {
        if (!state.equals("confirm")) {
            return;
        }
        _t0 = System.getTimer();
        progress = 0.0;
        startTimer(50);
    }

    function release() as Void {
        if (state.equals("confirm") && progress < 1.0) {
            stopTimer();
            progress = 0.0;
            WatchUi.requestUpdate();
        }
    }

    function tick() as Void {
        if (state.equals("confirm")) {
            progress = (System.getTimer() - _t0).toFloat() / HOLD_MS;
            if (progress >= 1.0) {
                progress = 1.0;
                stopTimer();
                log();
            }
        } else if (state.equals("undoing")) {
            if (System.getTimer() - _t0 > WAIT_MS) {
                undone();
            }
        } else if (state.equals("sending")) {
            _spin = (_spin + 30) % 360;
            if (System.getTimer() - _t0 > WAIT_MS) {
                // no answer: it waits in the outbox
                state = "queued";
                _t0 = System.getTimer();
            }
        } else if (state.equals("done") || state.equals("queued") || state.equals("said")) {
            if (System.getTimer() - _t0 > UNDO_MS) {
                stopTimer();
                close();
                return;
            }
        }
        WatchUi.requestUpdate();
    }

    function startTimer(ms as Number) as Void {
        stopTimer();
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:tick), ms, true);
    }

    function stopTimer() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
    }

    // the entry into the outbox, sent at once
    function log() as Void {
        at = Time.now().value() - ago * 60;
        var it = Outbox.entry(k, a, at);
        id = it.get("id") as String;
        Outbox.add(it);
        send();
    }

    function send() as Void {
        state = "sending";
        _t0 = System.getTimer();
        startTimer(100);
        Outbox.flush(method(:answered));
        WatchUi.requestUpdate();
    }

    // the outbox went as far as it could: what became of this entry
    function answered() as Void {
        if (id == null || !(state.equals("sending") || state.equals("queued"))) {
            return;
        }
        var it = Outbox.find(Outbox.items(), id);
        if (it != null) {
            if ("pen".equals(it.get("st"))) {
                var pn = it.get("pen");
                var pa = pn instanceof Dictionary ? pn.get("a") : null;
                var pt = pn instanceof Dictionary ? pn.get("t") : null;
                penText = Lang.format(AppWords.s(Rez.Strings.PenAt), [
                    pa != null ? AppMath.amt(k, pa as Numeric) : "",
                    pt instanceof Number ? AppInput.hm(pt - Outbox.lead()) : ""]);
                sel = 0;
                state = "pen";
                stopTimer();
            } else if (state.equals("sending")) {
                state = "queued";
                _t0 = System.getTimer();
            }
            WatchUi.requestUpdate();
            return;
        }
        var note = Application.Storage.getValue(Outbox.NOTE);
        var r = note instanceof Dictionary && id.equals(note.get("id")) ? note.get("r") : null;
        _t0 = System.getTimer();
        if ("when".equals(r)) {
            saidText = AppWords.s(Rez.Strings.NotLoggedOld);
            state = "said";
        } else if ("off".equals(r)) {
            saidText = AppWords.s(Rez.Strings.NotLoggedOff);
            state = "said";
        } else if (r == null) {
            // dropped as a bug (id, kind, amount): nothing was saved
            saidText = AppWords.s(Rez.Strings.NotSent);
            state = "said";
        } else {
            state = "done";
            AppNav.confirm();
        }
        startTimer(100);
        WatchUi.requestUpdate();
    }

    // Undo (10 s after Logged): the entry's delete into the outbox, sent at once
    function undo() as Void {
        if (!state.equals("done") || id == null) {
            return;
        }
        Outbox.add(Outbox.undo(id as String));
        state = "undoing";
        _t0 = System.getTimer();
        startTimer(100);
        Outbox.flush(method(:undone));
        WatchUi.requestUpdate();
    }

    // what the phone said about the undo: Undone, Not undone, or it waits in the outbox (Waiting for the phone)
    function undone() as Void {
        if (!state.equals("undoing")) {
            return;
        }
        var note = Application.Storage.getValue(Outbox.NOTE);
        var r = note instanceof Dictionary && "u".equals(note.get("w")) && (id as String).equals(note.get("id")) ? note.get("r") : null;
        saidText = AppWords.s("done".equals(r) ? Rez.Strings.Undone : "off".equals(r) ? Rez.Strings.NotUndone : Rez.Strings.SendWait);
        state = "said";
        _t0 = System.getTimer() - UNDO_MS + 3000;
        WatchUi.requestUpdate();
    }

    // the pen question's answer: [anyway] Log anyway (sent again), else Cancel (dropped)
    function penAnswer(anyway as Boolean) as Void {
        if (id == null) {
            return;
        }
        Outbox.pen(id as String, anyway);
        if (anyway) {
            send();
        } else {
            close();
        }
    }

    function close() as Void {
        stopTimer();
        for (var i = 0; i < depth; i++) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        }
    }

    // a notification over the screen cancels a hold
    function onHide() as Void {
        if (state.equals("confirm")) {
            stopTimer();
            progress = 0.0;
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        AppView.setup();
        var g = new AppG();
        if (state.equals("amount")) {
            AppLog.amount(g, k, a, usual, ago);
        } else if (state.equals("confirm")) {
            AppLog.confirm(g, k, a, ago, dup, progress, AppLog.quiet(k, ago));
        } else if (state.equals("sending")) {
            AppLog.sending(g, k, a, _spin);
        } else if (state.equals("done")) {
            var left = 1.0 - (System.getTimer() - _t0).toFloat() / UNDO_MS;
            AppLog.done(g, k, a, left < 0 ? 0.0 : left, AppLog.quiet(k, (Time.now().value() - at) / 60));
        } else if (state.equals("queued")) {
            AppLog.queued(g, k, a, AppInput.hm(at));
        } else if (state.equals("pen")) {
            AppLog.pen(g, k, penText, sel);
        } else if (state.equals("undoing")) {
            AppLog.said(g, AppWords.s(Rez.Strings.Sending));
        } else {
            AppLog.said(g, saidText);
        }
        AppDraw.paint(dc, g);
    }
}

class LogDelegate extends TapDelegate {
    // a drag on the amount: one step per DRAG units, from the amount the drag started on
    const DRAG = 14;
    var view as LogView;
    var _y0 as Float? = null;
    var _a0 as Float = 0.0;
    var _dragged as Number = -1000; // when a drag last changed the amount (System.getTimer)

    function initialize(v as LogView) {
        TapDelegate.initialize();
        view = v;
    }

    // a drag that changed the amount ends in a swipe too: that swipe is the drag's, not one more step
    function swiped() as Boolean {
        return System.getTimer() - _dragged < 400;
    }

    // touch on the amount: drag down / up through the values (down more, as a swipe down), the value under the
    // finger as it goes; the step's grid and limits as UP / DOWN
    function onDrag(e as WatchUi.DragEvent) as Boolean {
        if (!view.state.equals("amount")) {
            return false;
        }
        var y = (e.getCoordinates()[1] - AppDraw.oy) / AppDraw.s;
        var t = e.getType();
        if (t == WatchUi.DRAG_TYPE_START || _y0 == null) {
            _y0 = y.toFloat();
            _a0 = view.a;
        }
        var n = ((y - (_y0 as Float)) / DRAG).toNumber();
        if (n != 0 || view.a != _a0) {
            view.a = _a0;
            view.change(n);
            _dragged = System.getTimer();
        }
        if (t == WatchUi.DRAG_TYPE_STOP) {
            _y0 = null;
        }
        return true;
    }

    function onPreviousPage() as Boolean {
        if (view.state.equals("amount")) {
            if (!swiped()) {
                view.change(1);
            }
        } else if (view.state.equals("pen")) {
            view.sel = 1 - view.sel;
            WatchUi.requestUpdate();
        }
        return true;
    }

    function onNextPage() as Boolean {
        if (view.state.equals("amount")) {
            if (!swiped()) {
                view.change(-1);
            }
        } else if (view.state.equals("pen")) {
            view.sel = 1 - view.sel;
            WatchUi.requestUpdate();
        }
        return true;
    }

    function onMenu() as Boolean {
        if (view.state.equals("amount")) {
            view.earlier();
        }
        return true;
    }

    function onBack() as Boolean {
        return view.back();
    }

    // START: the amount → hold; on the hold screen START is held (onKeyPressed / onKeyReleased), never a press
    function select() as Void {
        var st = view.state;
        if (st.equals("amount")) {
            view.toConfirm();
        } else if (st.equals("done")) {
            view.undo();
        } else if (st.equals("pen")) {
            view.penAnswer(view.sel == 1);
        } else if (st.equals("queued") || st.equals("said")) {
            view.close();
        }
    }

    function onKeyPressed(e as WatchUi.KeyEvent) as Boolean {
        TapDelegate.onKeyPressed(e);
        if (e.getKey() == WatchUi.KEY_ENTER && view.state.equals("confirm")) {
            view.press();
            return true;
        }
        return false;
    }

    function onKeyReleased(e as WatchUi.KeyEvent) as Boolean {
        TapDelegate.onKeyReleased(e);
        if (e.getKey() == WatchUi.KEY_ENTER && view.state.equals("confirm")) {
            view.release();
            return true;
        }
        return false;
    }

    function onKey(e as WatchUi.KeyEvent) as Boolean {
        if (e.getKey() == WatchUi.KEY_ENTER && view.state.equals("confirm")) {
            return true;
        }
        return TapDelegate.onKey(e);
    }

    // touch: on the hold screen a held touch fills the rim
    function onHold(e as WatchUi.ClickEvent) as Boolean {
        view.press();
        return true;
    }

    function onRelease(e as WatchUi.ClickEvent) as Boolean {
        view.release();
        return true;
    }

    // touch on the amount: above the number +, under it −, a usual amount's chip, the time below it earlier;
    // the pen question: its rows; the logged screen: Undo
    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var c = e.getCoordinates();
        var x = (c[0] - AppDraw.ox) / AppDraw.s;
        var y = (c[1] - AppDraw.oy) / AppDraw.s;
        var st = view.state;
        if (st.equals("amount")) {
            // the chevrons (+ above, − below), the usual amounts' chips, the time under them
            var L = AppLog.amountAt();
            var cy = L[6] as Numeric;
            var cw = L[7] as Numeric;
            var gap = L[8] as Numeric;
            if (y < (L[4] as Numeric) + 24) {
                view.change(1);
            } else if (y >= (L[5] as Numeric) - 22 && y < cy - 4) {
                view.change(-1);
            } else if (y >= cy - 4 && y < cy + (L[9] as Numeric) + 6 && view.usual.size() > 0) {
                var n = view.usual.size();
                var i = ((x + (n * cw + (n - 1) * gap) / 2.0) / (cw + gap)).toNumber();
                if (i >= 0 && i < n) {
                    view.a = (view.usual[i] as Numeric).toFloat();
                    WatchUi.requestUpdate();
                }
            } else if (y >= cy + (L[9] as Numeric) + 6) {
                view.earlier();
            } else {
                view.toConfirm();
            }
            return true;
        }
        if (st.equals("pen")) {
            if (y > -16 && y < 36) {
                view.penAnswer(false);
            } else if (y >= 44 && y < 96) {
                view.penAnswer(true);
            }
            return true;
        }
        if (st.equals("done") && y > 90) {
            view.undo();
            return true;
        }
        return st.equals("confirm");
    }
}
