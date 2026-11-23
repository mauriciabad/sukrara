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
    var upd as String? = null; // a payload of another version: the app to update (AppData.update), its page in place of the pages
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
        build();
        arm();
        if (notSetUp || upd != null) {
            AppNav.fetch(); // a working phone fills it in seconds (an updated one too)
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
    // the updates (docs/48 decision 3): this app's Connect IQ listing (contracts/watchface/payload/apps.json "store"),
    // or, Sukrara on the phone older, the website's Android page where each copy's update is said
    const STORE = "https://apps.garmin.com/en-US/apps/c8c7b5e9-35e6-4941-b811-3bf7c9238c9a";
    const STORE_ES = "https://apps.garmin.com/es-ES/apps/c8c7b5e9-35e6-4941-b811-3bf7c9238c9a";
    const ANDROID = "https://sukrara.mauri.app/android/#versions";
    const ANDROID_ES = "https://sukrara.mauri.app/es/android/#versiones";

    // Open on phone: Garmin Connect opens the link on the phone, then Look at your phone for 5 s; and the phone is asked
    // at once. Waiting for the phone: the setup link (Sukrara for Android takes it as an App Link and opens where the
    // person can act; without Sukrara the website says how to get it). A payload of another version: the update
    function openSetup() as Void {
        var es = System.getDeviceSettings().systemLanguage == System.LANGUAGE_SPA;
        var url = es ? SETUP.substring(0, 26) + "es/" + SETUP.substring(26, SETUP.length()) : SETUP;
        if (upd != null) {
            url = "app".equals(upd) ? (es ? STORE_ES : STORE) : (es ? ANDROID_ES : ANDROID);
        }
        Communications.openWebPage(url, null, null);
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
        // the glance's copy of what this view shows, when it lacks one
        GlanceCopy.heal(Link.payload(), Link.skew);
        AppView.setup();
        var d = AppInput.build(now, kind.equals("pages") && page == 0);
        notSetUp = !d.sent;
        upd = notSetUp ? null : d.update;
        if (opened != null && System.getTimer() - (opened as Number) >= LOOK_MS) {
            opened = null;
        }
        _g = layout(d);
        if (AppScreens.want != null) {
            arm();
        }
    }

    // the view's one timer (a watch allows few): the 10 s tick, or while the % column is prepared a step every 50 ms
    // (AppScreens.prepare: its work in callbacks of its own, then the page again with it)
    function arm() as Void {
        var t = _timer;
        if (t != null) {
            t.stop();
            t.start(method(:tick), AppScreens.want != null ? 50 : 10000, false);
        }
    }

    function tick() as Void {
        if (AppScreens.want != null && !AppScreens.prepare()) {
            arm();
            return;
        }
        refresh();
        arm();
    }

    // the app's drawing: its own font and icon tables, the screen's scale and centre
    static function setup() as Void {
        var w = System.getDeviceSettings().screenWidth;
        var h = System.getDeviceSettings().screenHeight;
        AppDraw.glance = false;
        AppDraw.table = new Lang.Method(AppFontTable, :atlas);
        AppDraw.iconAt = new Lang.Method(AppFontTable, :icon);
        AppDraw.sheetAt = new Lang.Method(AppFontTable, :sheet);
        AppDraw.bitmapAt = new Lang.Method(AppDrawApp, :bitmap);
        AppDraw.scale(AppFontTable.S);
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
        } else if (upd != null && !kind.equals("list")) {
            AppScreens.update(g, d, opened != null);
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

// START and a tap told apart, for every screen of the app. A BehaviorDelegate turns a tap into onSelect too, and
// onSelect comes first: returning true there swallows the tap, so onTap never ran and a tap did what START does (the
// numbers' chips, the list's rows, the amount's chevrons and chips: #250). START comes as onKeyPressed(KEY_ENTER),
// then onSelect; a tap as onSelect alone. So onSelect acts only after START's press and lets a tap through to onTap;
// a START whose press didn't come first reaches onKey(KEY_ENTER) and acts there.
class TapDelegate extends WatchUi.BehaviorDelegate {
    var _start as Boolean = false;

    function initialize() {
        BehaviorDelegate.initialize();
    }

    // what START does on this screen
    function select() as Void {
    }

    function onSelect() as Boolean {
        if (_start) {
            select();
            return true;
        }
        return false;
    }

    function onKeyPressed(e as WatchUi.KeyEvent) as Boolean {
        if (e.getKey() == WatchUi.KEY_ENTER) {
            _start = true;
        }
        return false;
    }

    function onKeyReleased(e as WatchUi.KeyEvent) as Boolean {
        if (e.getKey() == WatchUi.KEY_ENTER) {
            _start = false;
        }
        return false;
    }

    function onKey(e as WatchUi.KeyEvent) as Boolean {
        if (e.getKey() == WatchUi.KEY_ENTER) {
            select();
            return true;
        }
        return BehaviorDelegate.onKey(e);
    }
}

// The pages: UP / DOWN or a swipe between the widget and the numbers, START the one list, BACK leaves; on the
// numbers a tap on a period chip switches the period, MENU steps it.
class AppDelegate extends TapDelegate {
    var view as AppView;

    function initialize(v as AppView) {
        TapDelegate.initialize();
        view = v;
    }

    function onNextPage() as Boolean {
        return turn();
    }

    function onPreviousPage() as Boolean {
        return turn();
    }

    function turn() as Boolean {
        if (view.notSetUp || view.upd != null) {
            return true;
        }
        view.page = 1 - view.page;
        AppDraw.release(); // one page's fonts and icons in the graphics pool at a time
        view.refresh();
        return true;
    }

    // START: the one list; not set up, or another version: Open on phone
    function select() as Void {
        if (view.notSetUp || view.upd != null) {
            if (view.opened == null) {
                view.openSetup();
            }
            return;
        }
        AppNav.list(WatchUi.SLIDE_LEFT);
    }

    // the numbers' period: MENU steps through 1d · 7d · 14d · 30d
    function onMenu() as Boolean {
        if (view.page != 1 || view.notSetUp || view.upd != null) {
            return false;
        }
        var pers = [1, 7, 14, 30];
        var i = pers.indexOf(view.days);
        view.days = pers[(i + 1) % 4] as Number;
        view.refresh();
        return true;
    }

    // a tap: not set up, the Open on phone button; on the numbers a period chip; anywhere else what START does
    function onTap(e as WatchUi.ClickEvent) as Boolean {
        var c = e.getCoordinates();
        var s = AppDraw.s;
        var y = (c[1] - AppDraw.oy) / s;
        var x = (c[0] - AppDraw.ox) / s;
        if (view.notSetUp || view.upd != null) {
            var sb = AppScreens.setupButton();
            if (view.opened == null && y > sb[0] - 8 && y < sb[0] + sb[1] + 8) {
                view.openSetup();
            }
            return true;
        }
        if (view.page == 1) {
            var L = AppScreens.numsAt();
            var cw = L[0] as Numeric;
            var x0 = -(4 * cw + 18) / 2.0;
            var i = ((x - x0 + 3) / (cw + 6)).toNumber();
            if (y >= (L[1] as Numeric) - 8 && y <= (L[1] as Numeric) + (L[2] as Numeric) + 8 && x >= x0 - 3 && i <= 3) {
                view.days = [1, 7, 14, 30][i] as Number;
                view.refresh();
                return true;
            }
        }
        select();
        return true;
    }
}
