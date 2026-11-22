// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

// The one list (S.actions, SPEC §5): the three kinds in the phone's order (log.order), each with its last entry and how
// long ago, then Mute alerts (or Muted + the time left).
module AppLists {
    // the time since, as the list says it (app.js ago())
    function ago(m as Number) as String {
        if (m < 1) {
            return AppWords.now();
        }
        if (m < 60) {
            return m + "m";
        }
        if (m < 360) {
            return (m / 60) + "h" + (m % 60 != 0 ? " " + (m % 60) + "m" : "");
        }
        return ((m + 30) / 60) + "h";
    }

    // the rows: [kind, "mute" or "muted", label, icon, colour, sub, sub colour]
    function rows(d as AppData) as Array<Array> {
        var out = [] as Array<Array>;
        var p = Link.payload();
        var log = p != null ? p.get("log") : null;
        if (log instanceof Dictionary) {
            var names = {"rapid" => Rez.Strings.KRapid, "long" => Rez.Strings.KLong, "carbs" => Rez.Strings.KCarbs};
            for (var i = 0; i < d.order.size(); i++) {
                var k = d.order[i] as String;
                var id = names.get(k);
                if (id == null) {
                    continue;
                }
                var sub = null;
                for (var j = d.trk.size() - 1; j >= 0; j--) {
                    if (k.equals(d.trk[j])) {
                        sub = AppMath.amt(k, d.tra[j] as Numeric) + " · " + ago((d.trm[j] as Float).toNumber());
                        break;
                    }
                }
                out.add([k, AppWords.s(id as ResourceId), k, AppMath.pal(k), sub, null]);
            }
        }
        // Muted (all) or Lows / Highs muted (a side), with the time left: Unmute / Change time
        var x = Link.inputs(d.now);
        if (x != null && "muted".equals(x.status)) {
            var lf = x.left;
            var s = x.side;
            out.add(["muted", s instanceof String ? AppMute.title(s) : AppWords.muted(), "bellOff", AppMath.pal("note"),
                lf instanceof Number ? Durations.short(lf, " ") : null, AppMath.pal("note")]);
        } else {
            out.add(["mute", AppWords.s(Rez.Strings.MuteAlerts), "bellOff", AppMath.SOFT, null, null]);
        }
        return out;
    }

    // the rows' y (4), and the list's geometry: [title x, title y, title anchor, selected row x, its width, its
    // corner, icon x, label x, the last entry's x, the others' icon size]; round (app.js S.actions) or the rectangles'
    // full-width rows (R.actions)
    function ys() as Array {
        if (!AppFontTable.RECT) {
            return [-74, -12, 44, 100];
        }
        var h = AppScreens.hh();
        var st = (2 * h - 86 - 40) / 4 + 2;
        return [-h + 86, -h + 86 + st, -h + 86 + 2 * st, -h + 86 + 3 * st];
    }

    function at() as Array {
        if (!AppFontTable.RECT) {
            return [0, -138, 0, -166, 332, 32, -128, -104, 150, 22];
        }
        var w = AppScreens.hw();
        var h = AppScreens.hh();
        return [-w + 18, -h + 34, -1, -w + 8, 2 * w - 16, 20, -w + 38, -w + 62, w - 20, 21];
    }

    function actions(g as AppG, d as AppData, sel as Number) as Void {
        var items = rows(d);
        var L = at();
        g.t(L[0], L[1], AppWords.s(Rez.Strings.AppName), 20, 600, AppMath.SOFT, L[2]);
        var ys = ys();
        if (items.size() == 1) {
            AppScreens.fitT(g, (ys[1] as Numeric) - 84, AppWords.s(Rez.Strings.LogOff), [17, 15, 14], 500, AppMath.DIM, 176);
        }
        var y0 = items.size() == 1 ? 3 : 0;
        for (var i = 0; i < items.size() && i < 4; i++) {
            var it = items[i];
            var y = ys[i + y0] as Numeric;
            var on = i == sel;
            var fs = on ? 30 : 23;
            if (on) {
                g.r(L[3], y - 32, L[4], 64, L[5], AppMath.CARD2, 1.0, 0);
            }
            g.icon(it[2] as String, L[6], y, on ? 26 : L[9], it[3] as Number);
            g.t(L[7], y + fs * 0.35, it[1] as String, fs, on ? 700 : 600, on ? AppMath.INK : AppMath.SOFT, -1);
            if (it[4] != null) {
                var sc = it[5] != null ? it[5] as Number : on ? AppMath.SOFT : AppMath.DIM;
                g.t(L[8], y + 7, it[4] as String, on ? 18 : 16, 600, sc, 1);
            }
        }
        AppScreens.btnHint(g, 60, AppMath.INK);
    }
}

// UP / DOWN move the selection, START opens it, BACK goes back.
class ListDelegate extends WatchUi.BehaviorDelegate {
    var view as AppView;

    function initialize(v as AppView) {
        BehaviorDelegate.initialize();
        view = v;
    }

    function onNextPage() as Boolean {
        return move(1);
    }

    function onPreviousPage() as Boolean {
        return move(-1);
    }

    function move(by as Number) as Boolean {
        var n = AppLists.rows(AppInput.build(Time.now().value(), false)).size();
        view.sel = (view.sel + by + n) % n;
        view.refresh();
        return true;
    }

    // START: a kind → its amount (SPEC §6); Mute alerts → the lengths, Muted → Unmute / Change time (§7)
    function onSelect() as Boolean {
        open(view.sel);
        return true;
    }

    function open(i as Number) as Void {
        var rows = AppLists.rows(AppInput.build(Time.now().value(), false));
        if (i < 0 || i >= rows.size()) {
            return;
        }
        var k = rows[i][0] as String;
        if (k.equals("mute") || k.equals("muted")) {
            AppNav.mute(k.equals("muted"));
        } else {
            var v = new LogView(k, 2);
            WatchUi.pushView(v, new LogDelegate(v), WatchUi.SLIDE_LEFT);
        }
    }

    // a tap on a row opens it
    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var y = (e.getCoordinates()[1] - AppDraw.oy) / AppDraw.s;
        var n = AppLists.rows(AppInput.build(Time.now().value(), false)).size();
        var all = AppLists.ys();
        var ys = n == 1 ? [all[3]] : all;
        for (var i = 0; i < ys.size() && i < n; i++) {
            if (y > ys[i] - 30 && y < ys[i] + 28) {
                view.sel = i;
                view.refresh();
                open(i);
                return true;
            }
        }
        return false;
    }
}
