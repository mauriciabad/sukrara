// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// One screen of the app on the seam layout(input) → model → draw: AppInput.build (the payload and the clock) → the
// screen's function pushes its display list (AppG) → AppDraw paints it. [kind] names the screen (AppScreens). The
// display list is built in its own callback (refresh: shown, the 10 s tick, a button, a new payload) and onUpdate only
// paints it: the watchdog counts each callback, and the widget's chart and % column with their paint don't fit one.
class AppView extends WatchUi.View {
    var kind as String;
    var page as Number = 0; // the pages: 0 the widget, 1 the numbers
    var days as Number = 14; // the numbers' period (switchable here, never saved)
    var sel as Number = 0; // the list's selection
    var notSetUp as Boolean = false; // waiting for the phone: it never sent anything (SPEC §4.3)
    var opened as Number? = null; // when Open on phone was pressed (System.getTimer)
    var _look as Timer.Timer? = null;
    var _timer as Timer.Timer?;
    var _g as AppG? = null;

    function initialize(k as String) {
        View.initialize();
        kind = k;
        var p = Link.payload();
        var rd = p != null ? p.get("repD") : null;
        days = rd instanceof Number ? rd : 14;
    }

    // ages and countdowns move on with nothing new from the phone: a new display list every 10 s while shown
    function onShow() as Void {
        AppNav.current = self;
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:refresh), 10000, true);
        build();
        if (notSetUp) {
            AppNav.fetch(); // a working phone fills it in seconds
        }
    }

    function onHide() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
        if (AppNav.current == self) {
            AppNav.current = null;
        }
        _g = null;
        AppDraw.release();
    }

    const LOOK_MS = 5000;
    // the setup link (the website's /es/ page for a watch in Spanish)
    const SETUP = "https://sukrara.mauri.app/garmin/setup/";

    // Open on phone: Garmin Connect opens the setup link on the phone (Sukrara for Android takes it as an App Link and
    // opens where the person can act; without Sukrara the website says how to get it), then Look at your phone for
    // 5 s; and the phone is asked at once
    function openSetup() as Void {
        var es = System.getDeviceSettings().systemLanguage == System.LANGUAGE_SPA;
        Communications.openWebPage(es ? SETUP.substring(0, 26) + "es/" + SETUP.substring(26, SETUP.length()) : SETUP, null, null);
        AppNav.fetch();
        opened = System.getTimer();
        _look = new Timer.Timer();
        (_look as Timer.Timer).start(method(:refresh), LOOK_MS, false);
        refresh();
    }

    // a new display list, then a paint
    function refresh() as Void {
        build();
        WatchUi.requestUpdate();
    }

    function build() as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        AppView.setup();
        var d = AppInput.build(now, kind.equals("pages") && page == 0);
        notSetUp = !d.sent;
        if (opened != null && System.getTimer() - (opened as Number) >= LOOK_MS) {
            opened = null;
        }
        _g = layout(d);
    }

    // the app's drawing: its own font and icon tables, the screen's scale and centre
    static function setup() as Void {
        var w = System.getDeviceSettings().screenWidth;
        var h = System.getDeviceSettings().screenHeight;
        AppDraw.glance = false;
        AppDraw.table = new Lang.Method(AppFontTable, :atlas);
        AppDraw.iconAt = new Lang.Method(AppFontTable, :icon);
        AppDraw.sheetAt = new Lang.Method(AppFontTable, :sheet);
        AppDraw.s = w / 390.0;
        AppDraw.ox = w / 2.0 - 0.5;
        AppDraw.oy = h / 2.0 - 0.5;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (_g == null) {
            build();
        }
        AppDraw.paint(dc, _g as AppG);
    }

    function layout(d as AppData) as AppG {
        var g = new AppG();
        if (notSetUp) {
            AppScreens.setup(g, opened != null);
        } else if (kind.equals("list")) {
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
        if (view.notSetUp) {
            return true;
        }
        view.page = 1 - view.page;
        AppDraw.release(); // one page's fonts and icons in the graphics pool at a time
        view.refresh();
        return true;
    }

    // START: the one list; not set up: Open on phone (nothing could be signed yet)
    function onSelect() as Boolean {
        if (view.notSetUp) {
            if (view.opened == null) {
                view.openSetup();
            }
            return true;
        }
        AppNav.list(WatchUi.SLIDE_LEFT);
        return true;
    }

    // the numbers' period: MENU steps through 1d · 7d · 14d · 30d (the list's Period)
    function onMenu() as Boolean {
        if (view.page != 1 || view.notSetUp) {
            return false;
        }
        var pers = [1, 7, 14, 30];
        var i = pers.indexOf(view.days);
        view.days = pers[(i + 1) % 4] as Number;
        view.refresh();
        return true;
    }

    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var c = e.getCoordinates();
        var s = AppDraw.s;
        var y = (c[1] - AppDraw.oy) / s;
        var x = (c[0] - AppDraw.ox) / s;
        if (view.notSetUp) {
            if (view.opened == null && y > 64 && y < 128) {
                view.openSetup();
            }
            return true;
        }
        if (view.page != 1) {
            return false;
        }
        if (y < -160 || y > -114) {
            return false;
        }
        var i = ((x + 109) / 56).toNumber();
        if (x < -109 || i > 3) {
            return false;
        }
        view.days = [1, 7, 14, 30][i] as Number;
        view.refresh();
        return true;
    }
}
