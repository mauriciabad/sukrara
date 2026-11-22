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

    // the rows: [kind or "mute", label, icon, colour, sub, sub colour]
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
        var x = Link.inputs(d.now);
        var muted = x != null && "muted".equals(x.get("status")) && x.get("side") == null;
        if (muted) {
            var lf = x.get("left");
            out.add(["mute", AppWords.muted(), "bellOff", AppMath.pal("note"),
                lf instanceof Number ? Durations.short(lf, " ") : null, AppMath.pal("note")]);
        } else {
            out.add(["mute", AppWords.s(Rez.Strings.MuteAlerts), "bellOff", AppMath.SOFT, null, null]);
        }
        return out;
    }

    function actions(g as AppG, d as AppData, sel as Number) as Void {
        var items = rows(d);
        g.t(0, -138, AppWords.s(Rez.Strings.AppName), 20, 600, AppMath.SOFT, 0);
        if (items.size() == 1) {
            g.t(0, -96, AppWords.s(Rez.Strings.LogOff), 17, 500, AppMath.DIM, 0);
        }
        var ys = [-74, -12, 44, 100];
        var y0 = items.size() == 1 ? 3 : 0;
        for (var i = 0; i < items.size() && i < 4; i++) {
            var it = items[i];
            var y = ys[i + y0];
            var on = i == sel;
            var fs = on ? 30 : 23;
            if (on) {
                g.r(-166, y - 32, 332, 64, 32, AppMath.CARD2, 1.0, 0);
            }
            g.icon(it[2] as String, -128, y, on ? 26 : 22, it[3] as Number);
            g.t(-104, y + fs * 0.35, it[1] as String, fs, on ? 700 : 600, on ? AppMath.INK : AppMath.SOFT, -1);
            if (it[4] != null) {
                var sc = it[5] != null ? it[5] as Number : on ? AppMath.SOFT : AppMath.DIM;
                g.t(150, y + 7, it[4] as String, on ? 18 : 16, 600, sc, 1);
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
        if (!k.equals("mute")) {
            var v = new LogView(k, 2);
            WatchUi.pushView(v, new LogDelegate(v), WatchUi.SLIDE_LEFT);
        } else {
            AppNav.mute(rows[i][4] != null);
        }
    }

    // a tap on a row opens it
    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var y = (e.getCoordinates()[1] - AppDraw.oy) / AppDraw.s;
        var n = AppLists.rows(AppInput.build(Time.now().value(), false)).size();
        var ys = n == 1 ? [100] : [-74, -12, 44, 100];
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
