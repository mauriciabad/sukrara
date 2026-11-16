// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.ActivityMonitor;
import Toybox.Activity;
import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;
import Toybox.SensorHistory;
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

    // A screen callback ([event]: S onShow, X onExitSleep, E onEnterSleep, C onDisplayModeChanged) sets always-on, and
    // the face reports it with the display mode (docs/43 "The screen").
    function screen(event as String, low as Boolean) as Void {
        lowPower = low;
        AuraLink.screen(event, Time.now().value(), displayMode());
    }

    // The mode a draw used (a always-on, n night, w awake), reported when it changes (D<mode>).
    function drew(mode as String) as Void {
        if (!mode.equals(_drew)) {
            _drew = mode;
            AuraLink.screen("D" + mode, Time.now().value(), displayMode());
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
        i.mode = lowPower ? "aod" : asleep(now) ? "night" : "awake";
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

    // the person's sleep window, from the watch's profile (wraps past midnight)
    function asleep(now as Number) as Boolean {
        var p = UserProfile.getProfile();
        if (p == null || p.sleepTime == null || p.wakeTime == null) {
            return false;
        }
        var c = System.getClockTime();
        var t = c.hour * 3600 + c.min * 60 + c.sec;
        var s = p.sleepTime.value();
        var w = p.wakeTime.value();
        return s <= w ? t >= s && t < w : t >= s || t < w;
    }

    function settings(i as AuraInput) as Void {
        i.dial = AuraSettings.option(SettingsSpec.DIAL);
        i.halo = AuraSettings.option(SettingsSpec.HALO);
        i.trendStyle = AuraSettings.option(SettingsSpec.TREND);
        i.seconds = AuraSettings.flag(SettingsSpec.SECONDS);
        i.rangeColour = AuraSettings.flag(SettingsSpec.RANGE_COLOUR);
        i.topField = AuraSettings.option(SettingsSpec.TOP_FIELD);
        i.fields = [AuraSettings.option(SettingsSpec.FIELD1), AuraSettings.option(SettingsSpec.FIELD2)];
        i.battLabel = AuraSettings.option(SettingsSpec.BATT_LABEL);
        i.privacy = AuraSettings.flag(SettingsSpec.PRIVATE) ? "private" : "open";
    }

    // THE SEAM: Sukrara's reading, aged by the watch clock
    function reading(i as AuraInput, now as Number) as Void {
        var r = AuraData.current();
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
        i.sensorLeft = r.sensorEnds != null ? AuraMath.max(0, r.sensorEnds - now) / 3600.0 : 0.0;
        i.unit = r.unit;
        i.limits = r.limits;
        i.tir = r.tir;
    }

    // the fields' data, only what a shown field needs (null = not known: the field says --)
    function fields(i as AuraInput, now as Number) as Void {
        var shown = [i.topField, i.fields[0], i.fields[1]];
        var am = ActivityMonitor.getInfo();
        i.steps = shown.indexOf("steps") >= 0 && am.steps != null ? am.steps : null;
        i.floors = shown.indexOf("floors") >= 0 && am has :floorsClimbed && am.floorsClimbed != null ? am.floorsClimbed : null;
        var ds = System.getDeviceSettings();
        i.notifications = ds.notificationCount;
        i.temp = null;
        i.sunset = null;
        if (shown.indexOf("weather") >= 0 || shown.indexOf("sunset") >= 0) {
            var w = Weather.getCurrentConditions();
            if (w != null && w.temperature != null) {
                var t = w.temperature;
                i.temp = (ds.temperatureUnits == System.UNIT_STATUTE ? t * 9 / 5.0 + 32 : t).toFloat().toNumber();
            }
            if (shown.indexOf("sunset") >= 0 && w != null && w.observationLocationPosition != null) {
                var s = Weather.getSunset(w.observationLocationPosition, Time.now());
                if (s != null) {
                    var si = Gregorian.info(s, Time.FORMAT_SHORT);
                    i.sunset = (si.hour as Number) * 60 + (si.min as Number);
                }
            }
        }
        var ai = Activity.getActivityInfo();
        i.heart = null;
        if (shown.indexOf("heart") >= 0) {
            if (ai != null && ai.currentHeartRate != null) {
                i.heart = ai.currentHeartRate;
            } else {
                var h = ActivityMonitor.getHeartRateHistory(1, true).next();
                i.heart = h != null && h.heartRate != ActivityMonitor.INVALID_HR_SAMPLE ? h.heartRate : null;
            }
        }
        i.altitude = null;
        i.altUnit = ds.elevationUnits == System.UNIT_STATUTE ? "ft" : "m";
        if (shown.indexOf("altitude") >= 0 && ai != null && ai.altitude != null) {
            var a = ai.altitude;
            i.altitude = (ds.elevationUnits == System.UNIT_STATUTE ? a * 3.28084 : a).toNumber();
        }
        i.bodyBattery = null;
        if (shown.indexOf("bodyBattery") >= 0 && SensorHistory has :getBodyBatteryHistory) {
            var b = SensorHistory.getBodyBatteryHistory({:period => 1}).next();
            i.bodyBattery = b != null && b.data != null ? b.data.toNumber() : null;
        }
        i.tz2Min = null;
        if (shown.indexOf("tz2") >= 0) {
            var city = AuraSettings.number(SettingsSpec.TZ2);
            var z = AuraZones.city(city);
            i.tz2Label = z[0] as String;
            try {
                var loc = new Position.Location({:latitude => z[1], :longitude => z[2], :format => :degrees});
                var lm = Gregorian.localMoment(loc, Time.now());
                if (lm != null) {
                    var t = now + lm.getOffset();
                    i.tz2Min = ((t % 86400) + 86400) % 86400 / 60;
                }
            } catch (e) {
                i.tz2Min = null;
            }
        }
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
