// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// Mute and Remind less (SPEC §7), ported from app.js S.muteLengths, S.muteDone, S.muted, S.remindDone: each screen
// pushes its display list from the flow's state (MuteView). [side] is the payload's: all (Mute all), low or high.
module AppMute {
    const START = -1;
    const MIDDLE = 0;
    const YS = [-62, -8, 46, 100, 150];
    const BTN = [10, 70];

    // the lengths offered: a side's mute always ends (contracts/alerts SPEC), Mute all may last until turned off (0)
    function mins(side as String) as Array<Number> {
        return side.equals("all") ? [15, 30, 60, 120, 0] : [15, 30, 60, 120];
    }

    function length(min as Number) as String {
        var ids = {15 => Rez.Strings.Min15, 30 => Rez.Strings.Min30, 60 => Rez.Strings.Min60, 120 => Rez.Strings.Min120};
        var id = ids.get(min);
        return AppWords.s(id != null ? id as ResourceId : Rez.Strings.UntilOff);
    }

    function pick(side as String, all as ResourceId, low as ResourceId, high as ResourceId) as String {
        return AppWords.s(side.equals("low") ? low : side.equals("high") ? high : all);
    }

    // "Alerts muted" / "Lows muted" / "Highs muted"
    function title(side as String) as String {
        return pick(side, Rez.Strings.AlertsMuted, Rez.Strings.LowsMuted, Rez.Strings.HighsMuted);
    }

    // the done screen's line: "for 30 min · until 21:11", or "until you unmute"
    function doneLine(min as Number, at as Number) as String {
        if (min == 0) {
            return AppWords.s(Rez.Strings.UntilYou);
        }
        return Lang.format(AppWords.s(Rez.Strings.ForUntil), [length(min), AppInput.hm(at + min * 60)]);
    }

    // the muted screen's line from the time left ([left] s, or "never"): "1h 20m left · until 22:01"
    function leftLine(left, now as Number) as String {
        if (!(left instanceof Number)) {
            return AppWords.s(Rez.Strings.UntilYou);
        }
        return Lang.format(AppWords.s(Rez.Strings.LeftUntil), [Durations.short(left, " "), AppInput.hm(now + left)]);
    }

    // what a write says while it goes: "Mute lows · 30 min", "Unmute", "Remind less"
    function sendTitle(act as String, side as String, min as Number) as String {
        if (act.equals("mute")) {
            return pick(side, Rez.Strings.Mute, Rez.Strings.MuteLowsT, Rez.Strings.MuteHighsT) + " · " + length(min);
        }
        return AppWords.s(act.equals("unmute") ? Rez.Strings.Unmute : act.equals("less") ? Rez.Strings.RemindLess : Rez.Strings.RemindUsual);
    }

    // Remind less / as usual done: "Lows: remind less"
    function remindTitle(act as String, side as String) as String {
        var low = side.equals("low");
        if (act.equals("less")) {
            return AppWords.s(low ? Rez.Strings.LowsLess : Rez.Strings.HighsLess);
        }
        return AppWords.s(low ? Rez.Strings.LowsUsual : Rez.Strings.HighsUsual);
    }

    // ---- the lengths (S.muteLengths): [sel] the selected one ----------------------------------------------------
    function lengths(g as AppG, side as String, sel as Number) as Void {
        g.t(0, -140, pick(side, Rez.Strings.MuteAllT, Rez.Strings.MuteLowsT, Rez.Strings.MuteHighsT), 22, 700, AppMath.INK, MIDDLE);
        g.t(0, -114, AppWords.s(side.equals("all") ? Rez.Strings.MuteAllSub : Rez.Strings.MuteSideSub), 16, 500, AppMath.SOFT, MIDDLE);
        var ms = mins(side);
        for (var i = 0; i < ms.size(); i++) {
            var y = YS[i] as Number;
            var on = i == sel;
            var fs = on ? 30 : 24;
            if (on) {
                g.r(-150, y - 28, 300, 56, 28, AppMath.CARD2, 1.0, 0);
            }
            g.t(0, y + fs * 0.35, length(ms[i]), fs, on ? 700 : 600, on ? AppMath.INK : AppMath.SOFT, MIDDLE);
        }
        AppScreens.btnHint(g, 60, AppMath.INK);
    }

    // ---- muted (S.muteDone, S.remindDone): the bell in its colour, the title, the line, Undo with its countdown ----
    function done(g as AppG, icon as String, col as Number, title as String, line as String?, left as Float) as Void {
        g.c(0, -56, 46, col, 0.24, 0);
        g.icon(icon, 0, -56, 44, col);
        g.t(0, 28, title, icon.equals("bellOff") ? 30 : 28, 700, AppMath.INK, MIDDLE);
        if (line != null) {
            g.t(0, 60, line, 20, 500, AppMath.SOFT, MIDDLE);
        }
        if (left > 0) {
            AppLog.undo(g, 92, left);
            AppScreens.btnHint(g, 60, AppMath.INK);
        }
    }

    // ---- while muted (S.muted): Unmute / Change time, [sel] the selected one (filled) ---------------------------
    function muted(g as AppG, side as String, line as String, sel as Number) as Void {
        var note = AppMath.pal("note");
        g.icon("bellOff", 0, -118, 30, note);
        g.t(0, -70, title(side), 28, 700, AppMath.INK, MIDDLE);
        g.t(0, -40, line, 19, 500, AppMath.SOFT, MIDDLE);
        var items = [AppWords.s(Rez.Strings.Unmute), AppWords.s(Rez.Strings.ChangeTime)];
        for (var i = 0; i < 2; i++) {
            var y = BTN[i] as Number;
            var on = i == sel;
            g.r(-130, y - 26, 260, 52, 26, on ? note : AppMath.CARD2, 1.0, 0);
            g.t(0, y + 8, items[i], 22, 700, on ? AppMath.ON : AppMath.INK, MIDDLE);
        }
        if (side.equals("all")) {
            g.t(0, 134, AppWords.s(Rez.Strings.MutedAllSub), 16, 500, AppMath.DIM, MIDDLE);
        }
        AppScreens.btnHint(g, 60, AppMath.INK);
    }

    // which row a tap at [y] hits, on the lengths ([n] rows) or the muted screen's buttons; -1 none
    function hit(ys as Array, n as Number, y as Float, half as Number) as Number {
        for (var i = 0; i < n && i < ys.size(); i++) {
            if (y > (ys[i] as Number) - half && y < (ys[i] as Number) + half) {
                return i;
            }
        }
        return -1;
    }
}

// The mute flows on one view (SPEC §7, §12): the lengths → sending → muted (Undo 10 s); while muted, Unmute / Change
// time; Remind less / as usual from an alert → sending → done (Undo 10 s: the other one). What the phone answers decides
// the screen: done (known) → the done screen; no answer → Waiting for the phone (the outbox sends it when it can).
class MuteView extends WatchUi.View {
    const UNDO_MS = 10000;
    const WAIT_MS = 8000;
    const SAID_MS = 3000;

    var side as String; // all, low, high
    var state as String; // lengths, muted, sending, done, said
    var act as String = "mute"; // mute, unmute, less, usual
    var min as Number = 30;
    var sel as Number = 1;
    var undoing as Boolean = false;
    var depth as Number = 1; // the views to pop when the flow ends
    var at as Number = 0; // when it was sent (watch clock): the done screen's "until"
    var saidText as String = "";
    var _from as String? = null; // the state BACK on the lengths returns to (Change time: muted)
    var _id as String? = null;
    var _t0 as Number = 0;
    var _spin as Number = 0;
    var _timer as Timer.Timer? = null;
    var _go as Boolean = false;

    // [st] lengths / muted, or sending with [a] less / usual (an alert's Remind less: sent at once)
    function initialize(s as String, st as String, a as String, d as Number) {
        View.initialize();
        side = s;
        state = st;
        act = a;
        depth = d;
        _go = st.equals("sending");
        sel = st.equals("muted") ? 0 : 1;
    }

    function onShow() as Void {
        if (_go) {
            _go = false;
            send();
        } else if (_timer == null) {
            startTimer(state.equals("lengths") || state.equals("muted") ? 10000 : 100);
        }
    }

    function onHide() as Void {
        stopTimer();
    }

    function rows() as Number {
        return state.equals("muted") ? 2 : AppMute.mins(side).size();
    }

    function move(by as Number) as Void {
        if (state.equals("lengths") || state.equals("muted")) {
            var n = rows();
            sel = (sel + by + n) % n;
            WatchUi.requestUpdate();
        }
    }

    // START or a tap on row [i]
    function choose(i as Number) as Void {
        if (state.equals("lengths")) {
            act = "mute";
            min = AppMute.mins(side)[i];
            send();
        } else if (state.equals("muted")) {
            if (i == 0) {
                act = "unmute";
                min = 0;
                send();
            } else {
                _from = "muted";
                state = "lengths";
                sel = 1;
                WatchUi.requestUpdate();
            }
        } else if (state.equals("done")) {
            undo();
        } else if (state.equals("said")) {
            close();
        }
    }

    function back() as Boolean {
        if (state.equals("lengths") && _from != null) {
            state = _from as String;
            _from = null;
            sel = 1;
            WatchUi.requestUpdate();
            return true;
        }
        if (state.equals("lengths") || state.equals("muted")) {
            stopTimer();
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        close();
        return true;
    }

    // into the outbox and sent at once
    function send() as Void {
        var it = Outbox.mute(act, side, min);
        _id = it.get("id") as String;
        Outbox.add(it);
        at = Time.now().value();
        state = "sending";
        _t0 = System.getTimer();
        startTimer(100);
        Outbox.flush(method(:answered));
        WatchUi.requestUpdate();
    }

    // the outbox went as far as it could: what became of this action
    function answered() as Void {
        if (_id == null || !state.equals("sending")) {
            return;
        }
        if (Outbox.find(Outbox.items(), _id) != null) {
            say(AppWords.s(Rez.Strings.SendWait)); // it waits in the outbox; the background sends it
            return;
        }
        var note = Application.Storage.getValue(Outbox.NOTE);
        var r = note instanceof Dictionary && (_id as String).equals(note.get("id")) ? note.get("r") : null;
        if (!"done".equals(r) && !"known".equals(r)) {
            say(AppWords.s(Rez.Strings.NotSentShort)); // dropped (a bug): nothing changed
        } else if (undoing) {
            say(AppWords.s(act.equals("unmute") ? unmuted() : Rez.Strings.Undone));
        } else if (act.equals("unmute")) {
            say(AppWords.s(unmuted()));
        } else {
            state = "done";
            _t0 = System.getTimer();
            AppNav.confirm();
        }
        WatchUi.requestUpdate();
    }

    function unmuted() as ResourceId {
        return side.equals("low") ? Rez.Strings.LowsUnmuted : side.equals("high") ? Rez.Strings.HighsUnmuted : Rez.Strings.Unmuted;
    }

    function say(text as String) as Void {
        saidText = text;
        state = "said";
        _t0 = System.getTimer();
        WatchUi.requestUpdate();
    }

    // Undo (10 s after done): the other action, sent at once
    function undo() as Void {
        undoing = true;
        act = act.equals("mute") ? "unmute" : act.equals("less") ? "usual" : "less";
        min = 0;
        send();
    }

    function tick() as Void {
        var t = System.getTimer() - _t0;
        if (state.equals("sending")) {
            _spin = (_spin + 30) % 360;
            if (t > WAIT_MS) {
                say(AppWords.s(Rez.Strings.SendWait));
            }
        } else if (state.equals("done") && t > UNDO_MS || state.equals("said") && t > SAID_MS) {
            close();
            return;
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

    function close() as Void {
        stopTimer();
        for (var i = 0; i < depth; i++) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        AppView.setup();
        var g = new AppG();
        var now = Time.now().value();
        if (state.equals("lengths")) {
            AppMute.lengths(g, side, sel);
        } else if (state.equals("muted")) {
            var x = Link.inputs(now);
            AppMute.muted(g, side, AppMute.leftLine(x != null ? x.left : null, now), sel);
        } else if (state.equals("sending")) {
            var col = act.equals("mute") || act.equals("unmute") ? AppMath.pal("note") : AppMath.pal(side);
            AppLog.sendingAs(g, col, AppMute.sendTitle(act, side, min), _spin);
        } else if (state.equals("done")) {
            var left = 1.0 - (System.getTimer() - _t0).toFloat() / UNDO_MS;
            left = left < 0 ? 0.0 : left;
            if (act.equals("mute")) {
                AppMute.done(g, "bellOff", AppMath.pal("note"), AppMute.title(side), AppMute.doneLine(min, at), left);
            } else {
                AppMute.done(g, act.equals("less") ? "bellPause" : "bell", AppMath.pal(side), AppMute.remindTitle(act, side), null, left);
            }
        } else {
            AppLog.said(g, saidText);
        }
        AppDraw.paint(dc, g);
    }
}

class MuteDelegate extends WatchUi.BehaviorDelegate {
    var view as MuteView;

    function initialize(v as MuteView) {
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
        return view.back();
    }

    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var y = (e.getCoordinates()[1] - AppDraw.oy) / AppDraw.s;
        var st = view.state;
        var i = st.equals("lengths") ? AppMute.hit(AppMute.YS, view.rows(), y, 27)
            : st.equals("muted") ? AppMute.hit(AppMute.BTN, 2, y, 26)
            : st.equals("done") && y > 86 && y < 140 ? 0 : st.equals("said") ? 0 : -1;
        if (i >= 0) {
            view.sel = i;
            view.choose(i);
        }
        return true;
    }
}
