// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.ActivityMonitor;
import Toybox.Application;
import Toybox.Complications;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.UserProfile;
import Toybox.Weather;

// Fills the input model from the watch: the clock, the mode, the battery, the fields' data, the settings and THE SEAM
// (AuraData.current(), Sukrara's reading). Every count of time happens here, at every draw, from absolute times.
module AuraWatch {
    var lowPower as Boolean = false; // set by screen(): the last screen callback decides
    var revealUntil as Number = 0; // epoch ms (System.getTimer) until which a private value shows; 0 = hidden
    const REVEAL_MS = 5000;
    var _drew as String = "";

    // System.getDisplayMode(): 0 high power, 1 low power (always-on), 2 off; 9 where the watch hasn't it (API < 5)
    function displayMode() as Number {
        return System has :getDisplayMode ? System.getDisplayMode() as Number : 9;
    }

    // The display in high power, where the watch says (API 5): the only time a watch face may start a timer
    function highPower() as Boolean {
        var d = displayMode();
        return d == 0 || d == 9;
    }

    // A screen callback ([event]: S onShow, X onExitSleep, E onEnterSleep, C onDisplayModeChanged) sets always-on, and
    // the face reports it with the display mode (docs/43 "The screen").
    function screen(event as String, low as Boolean) as Void {
        lowPower = low;
        Link.screen(event, Time.now().value(), displayMode());
    }

    // The mode a draw used (a always-on, n night, w awake), reported when it changes (D<mode>).
    function drew(mode as String) as Void {
        if (!mode.equals(_drew)) {
            _drew = mode;
            var now = Time.now().value();
            Link.screen("D" + mode, now, displayMode());
            var f = facts();
            // the clock part always differs: compare without it
            var key = f.substring(0, f.find("t")) + f.substring(f.find("d"), null);
            if (!key.equals(_facts)) {
                _facts = key;
                Link.screen(f, now, displayMode());
            }
        }
    }

    function input() as AuraInput {
        var i = new AuraInput();
        var now = Time.now().value();
        var ds = System.getDeviceSettings();
        i.now = now;
        i.utc = false;
        i.is24 = ds.is24Hour;
        i.lang = ds.systemLanguage == System.LANGUAGE_SPA ? "es" : "en";
        i.phone = ds.phoneConnected;
        var st = System.getSystemStats();
        var days = st has :batteryInDays ? st.batteryInDays : null;
        i.batteryPct = (st.battery + 0.5).toNumber();
        i.battery = days != null && days > 0 ? days.toFloat() : st.battery / 10.0;
        settings(i);
        i.mode = lowPower ? "aod" : asleep(ds) ? "night" : "awake";
        // private: touch and hold shows the value for 5 s with the countdown line (SPEC.md §8)
        if (i.privacy.equals("private") && revealUntil > 0) {
            var left = revealUntil - System.getTimer();
            if (left > 0 && left <= REVEAL_MS) {
                i.privacy = "revealed";
                i.reveal = left / (REVEAL_MS * 1.0);
            } else {
                revealUntil = 0;
            }
        }
        reading(i, now);
        fields(i, now);
        return i;
    }

    // Night: the watch's own Do Not Disturb, which its Sleep Mode turns on at the person's scheduled sleep (Descent: hold
    // MENU › System › Sleep Mode; the schedule is per day). UserProfile's sleep and wake times are not that schedule:
    // the owner's Descent put the face in night at 15:51 (docs/43 "The screen").
    function asleep(ds as System.DeviceSettings) as Boolean {
        return ds has :doNotDisturb && ds.doNotDisturb == true;
    }

    // What the night decision could rest on, as the report's P entry when it changes (docs/43 "The screen"):
    // s / w UserProfile sleepTime / wakeTime (s since midnight, -1 none), t the clock (s since midnight), d Do Not
    // Disturb, z ActivityMonitor isSleepMode, n isNightModeEnabled (1 / 0, 9 not on this watch).
    var _facts as String = "";

    function facts() as String {
        var p = UserProfile.getProfile();
        var s = p != null && p.sleepTime != null ? p.sleepTime.value() : -1;
        var w = p != null && p.wakeTime != null ? p.wakeTime.value() : -1;
        var c = System.getClockTime();
        var ds = System.getDeviceSettings();
        var am = ActivityMonitor.getInfo();
        var d = ds has :doNotDisturb ? flag(ds.doNotDisturb) : 9;
        var z = am has :isSleepMode ? flag(am.isSleepMode) : 9;
        var n = ds has :isNightModeEnabled ? flag(ds.isNightModeEnabled) : 9;
        return "Ps" + s + "w" + w + "t" + (c.hour * 3600 + c.min * 60 + c.sec) + "d" + d + "z" + z + "n" + n;
    }

    function flag(v) as Number {
        return v == true ? 1 : v == false ? 0 : 9;
    }

    function settings(i as AuraInput) as Void {
        i.dial = AuraSettings.option(SettingsSpec.DIAL);
        i.halo = AuraSettings.option(SettingsSpec.HALO);
        i.trendStyle = AuraSettings.option(SettingsSpec.TREND);
        i.seconds = AuraSettings.flag(SettingsSpec.SECONDS);
        i.rangeColour = AuraSettings.flag(SettingsSpec.RANGE_COLOUR);
        i.topField = AuraSettings.option(SettingsSpec.TOP_FIELD);
        i.topField2 = AuraSettings.option(SettingsSpec.TOP_FIELD2);
        i.fields = [AuraSettings.option(SettingsSpec.FIELD1), AuraSettings.option(SettingsSpec.FIELD2)];
        i.battLabel = AuraSettings.option(SettingsSpec.BATT_LABEL);
        i.sideL = AuraSettings.option(SettingsSpec.SIDE_L);
        i.sideR = AuraSettings.option(SettingsSpec.SIDE_R);
        i.privacy = AuraSettings.flag(SettingsSpec.PRIVATE) ? "private" : "open";
    }

    // THE SEAM: Sukrara's reading, aged by the watch clock
    function reading(i as AuraInput, now as Number) as Void {
        var r = AuraData.current();
        // a payload of another version: which app to update (SPEC.md §6.6b); the reading is its glucose alone
        var u = Link.update();
        i.update = u == null ? "none" : u.equals(SukraraFace.UPDATE_PHONE) ? "phone" : "face";
        if (r == null) {
            i.sensor = "none";
            i.lastItems = [];
            return;
        }
        i.glucose = r.mgdl.toFloat();
        i.rate = r.rate;
        i.trend = r.rate != null;
        i.doubled = r.doubled;
        i.age = AuraMath.max(0, now - r.readAt) / 60.0; // a reading ahead of the clock is fresh
        i.history = r.history;
        i.spark = null;
        i.sensor = r.sensor;
        i.status = r.status;
        i.left = r.statusUntil != null ? AuraMath.max(0, r.statusUntil - now) / 60.0 : null;
        i.lastItems = r.lastItems != null ? r.lastItems : [];
        i.lastAgo = r.lastAt != null ? AuraMath.max(0, now - r.lastAt) / 60.0 : 0.0;
        if (r.warmFrom != null && r.warmUntil != null && r.warmUntil > r.warmFrom) {
            i.warm = AuraMath.clamp((now - r.warmFrom) / (r.warmUntil - r.warmFrom).toFloat(), 0, 1);
            i.readyMin = AuraMath.max(0, r.warmUntil - now) / 60.0;
        }
        // the sensor's life from Sukrara's start and end; without them the sensor rail says --
        i.sensorDates = r.sensorStarts != null && r.sensorEnds != null && r.sensorEnds > r.sensorStarts;
        i.sensorLeft = r.sensorEnds != null ? AuraMath.max(0, r.sensorEnds - now) / 3600.0 : 0.0;
        if (i.sensorDates) {
            i.sensorLife = (r.sensorEnds - r.sensorStarts) / 86400.0;
        }
        i.unit = r.unit;
        i.limits = r.limits;
        i.vals["tir"] = r.tir;
    }

    // the fields' data, only what a shown field needs (null = not known: the field says --): the watch's complications
    // for most (AuraComp, subscribed to the shown ones only), the weather and its forecast, the second zone's clock
    function fields(i as AuraInput, now as Number) as Void {
        var shown = [i.topField, i.topField2, i.fields[0], i.fields[1]];
        var ds = System.getDeviceSettings();
        // one flag for the units the fields show: ft and mi (the watch's distance setting)
        i.statute = ds.distanceUnits == System.UNIT_STATUTE;
        var hotF = ds.temperatureUnits == System.UNIT_STATUTE;
        var types = [];
        for (var n = 0; n < shown.size(); n++) {
            var k = shown[n] as String;
            var f = AuraFields.spec(k);
            if (f == null) {
                continue;
            }
            var type = f[3] as Number;
            if (type > 0) {
                types.add(type);
                i.vals[k] = comp(type, AuraComp.vals[type], i.statute, hotF);
            } else if (k.equals("weather")) {
                var w = Weather.getCurrentConditions();
                i.vals[k] = w == null ? null : {"t" => temp(w.temperature, hotF), "sky" => sky(w.condition)};
            } else if (k.equals("highLow") || k.find("forecast") == 0) {
                var days = Weather.getDailyForecast();
                var d = k.equals("highLow") ? 0 : k.substring(8, 9).toNumber() as Number;
                var fc = days != null && days.size() > d ? days[d] : null;
                i.vals[k] = fc == null ? null : {"hi" => temp(fc.highTemperature, hotF), "lo" => temp(fc.lowTemperature, hotF), "sky" => sky(fc.condition)};
            } else if (k.equals("tz2")) {
                var z = AuraZones.city(AuraSettings.number(SettingsSpec.TZ2));
                i.tz2Label = z[0] as String;
                i.vals[k] = null;
                try {
                    var loc = new Position.Location({:latitude => z[1], :longitude => z[2], :format => :degrees});
                    var lm = Gregorian.localMoment(loc, Time.now());
                    if (lm != null) {
                        var t = now + lm.getOffset();
                        i.vals[k] = ((t % 86400) + 86400) % 86400 / 60;
                    }
                } catch (e) {
                    i.vals[k] = null;
                }
            }
        }
        AuraComp.sync(types);
    }

    // a complication's value in the field's unit (SPEC.md §6.10): metres → ft, m → km or mi, m/s → s per km or mi,
    // Pa → hPa, s since midnight → min, °C → °F where the watch says
    function comp(type as Number, v, statute as Boolean, hotF as Boolean) {
        if (v == null || v instanceof String) {
            return v;
        }
        var x = (v as Numeric).toFloat();
        if (type == 15) {
            return statute ? x * 3.28084 : x;
        } else if (type == 16) {
            return x / 100;
        } else if (type == 19 || type == 20) {
            return x / (statute ? 1609.344 : 1000.0);
        } else if (type >= 31 && type <= 34) {
            return x > 0 ? (statute ? 1609.344 : 1000.0) / x : null;
        } else if (type == 13 || type == 14) {
            return x / 60;
        } else if (type == 38) {
            return temp(x, hotF);
        }
        return v;
    }

    function temp(t as Numeric?, hotF as Boolean) as Float? {
        return t == null ? null : hotF ? t * 9 / 5.0 + 32 : t.toFloat();
    }

    // a Weather.CONDITION_* as the field's icon (SPEC.md §2): S sun, P cloudSun, C cloud, R rain, N snow, T storm, F fog
    function sky(c as Number?) as String? {
        if (c == null || c < 0 || c > 52) {
            return null;
        }
        var k = "SPCRNFTNFFNRTRRRNNNNCNPSRRRRTFFRTFNFTFFFSTTNNRNNNRNNP".substring(c, c + 1);
        return ["sun", "cloudSun", "cloud", "rain", "snow", "storm", "fog"]["SPCRNTF".find(k) as Number];
    }
}

// The second time zone's cities (SPEC.md §11): code, latitude, longitude. Gregorian.localMoment takes the zone and its
// daylight saving from the place (the watch's own Alternate Time Zones aren't open to Connect IQ).
module AuraZones {
    const CITIES = [
        ["UTC", 51.48, 0.0], ["LON", 51.51, -0.13], ["LIS", 38.72, -9.14], ["MAD", 40.42, -3.70], ["PAR", 48.86, 2.35],
        ["BER", 52.52, 13.40], ["ATH", 37.98, 23.73], ["CAI", 30.04, 31.24], ["IST", 41.01, 28.98], ["MOW", 55.76, 37.62],
        ["DXB", 25.20, 55.27], ["DEL", 28.61, 77.21], ["BKK", 13.76, 100.50], ["SIN", 1.35, 103.82], ["HKG", 22.32, 114.17],
        ["SHA", 31.23, 121.47], ["TYO", 35.68, 139.69], ["SEL", 37.57, 126.98], ["SYD", -33.87, 151.21], ["AKL", -36.85, 174.76],
        ["HNL", 21.31, -157.86], ["ANC", 61.22, -149.90], ["LAX", 34.05, -118.24], ["DEN", 39.74, -104.99],
        ["MEX", 19.43, -99.13], ["CHI", 41.88, -87.63], ["NYC", 40.71, -74.01], ["BOG", 4.71, -74.07],
        ["SCL", -33.45, -70.67], ["BUE", -34.60, -58.38], ["SAO", -23.55, -46.63], ["LOS", 6.52, 3.38], ["JNB", -26.20, 28.05],
    ];

    function city(i as Number) as Array {
        return CITIES[i >= 0 && i < CITIES.size() ? i : 26] as Array;
    }
}
