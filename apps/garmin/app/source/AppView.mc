// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// One screen of the app on the seam layout(input) → model → draw: AppInput.build (the payload and the clock) → the
// screen's function pushes its display list (AppG) → AppDraw paints it. [kind] names the screen (AppScreens).
class AppView extends WatchUi.View {
    var kind as String;
    var page as Number = 0; // the pages: 0 the widget, 1 the numbers
    var days as Number = 14; // the numbers' period (switchable here, never saved)
    var sel as Number = 0; // the list's selection
    var _timer as Timer.Timer?;

    function initialize(k as String) {
        View.initialize();
        kind = k;
        var p = Link.payload();
        var rd = p != null ? p.get("repD") : null;
        days = rd instanceof Number ? rd : 14;
    }

    // ages and countdowns move on with nothing new from the phone: a redraw every 10 s while shown
    function onShow() as Void {
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:tick), 10000, true);
    }

    function onHide() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
        AppDraw.release();
    }

    function tick() as Void {
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        AppDraw.glance = false;
        AppDraw.table = new Lang.Method(AppFontTable, :atlas);
        AppDraw.iconAt = new Lang.Method(AppFontTable, :icon);
        AppDraw.sheetId = AppFontTable.sheet();
        AppDraw.s = dc.getWidth() / 390.0;
        AppDraw.ox = dc.getWidth() / 2.0 - 0.5;
        AppDraw.oy = dc.getHeight() / 2.0 - 0.5;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        AppDraw.paint(dc, layout(AppInput.build(now)));
    }

    function layout(d as AppData) as AppG {
        var g = new AppG();
        if (kind.equals("list")) {
            AppLists.actions(g, d, sel);
        } else if (page == 1) {
            AppScreens.appNums(g, d, days);
        } else {
            AppScreens.appA(g, d);
        }
        return g;
    }
}

// The pages: UP / DOWN or a swipe between the widget and the numbers, START the one list, BACK leaves; on the
// numbers a tap on the period chips switches the period.
class AppDelegate extends WatchUi.BehaviorDelegate {
    var view as AppView;

    function initialize(v as AppView) {
        BehaviorDelegate.initialize();
        view = v;
    }

    function onNextPage() as Boolean {
        return turn();
    }

    function onPreviousPage() as Boolean {
        return turn();
    }

    function turn() as Boolean {
        view.page = 1 - view.page;
        WatchUi.requestUpdate();
        return true;
    }

    function onSelect() as Boolean {
        AppNav.list(WatchUi.SLIDE_LEFT);
        return true;
    }

    // the numbers' period: MENU steps through 1d · 7d · 14d · 30d (the list's Period)
    function onMenu() as Boolean {
        if (view.page != 1) {
            return false;
        }
        var pers = [1, 7, 14, 30];
        var i = pers.indexOf(view.days);
        view.days = pers[(i + 1) % 4] as Number;
        WatchUi.requestUpdate();
        return true;
    }

    function onTap(e as WatchUi.ClickEvent) as Boolean {
        if (view.page != 1) {
            return false;
        }
        var c = e.getCoordinates();
        var s = AppDraw.s;
        var y = (c[1] - AppDraw.oy) / s;
        var x = (c[0] - AppDraw.ox) / s;
        if (y < -160 || y > -114) {
            return false;
        }
        var i = ((x + 109) / 56).toNumber();
        if (x < -109 || i > 3) {
            return false;
        }
        view.days = [1, 7, 14, 30][i] as Number;
        WatchUi.requestUpdate();
        return true;
    }
}
