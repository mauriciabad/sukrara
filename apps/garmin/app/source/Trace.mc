// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Background;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// The app's own log on the watch (#217): System.println, which a sideloaded app writes to GARMIN/Apps/LOGS/SUKRARA.TXT
// only while that file exists (nothing anywhere otherwise), read over USB (docs/43 "Seeing the watch"). Every entry
// point of the app's processes and the background's registrations, each line with the watch's time and the memory
// in use.
(:background :glance)
module Trace {
    function log(s as String) as Void {
        var st = System.getSystemStats();
        System.println(Time.now().value() + " " + s + " | mem " + st.usedMemory + "/" + st.totalMemory);
    }

    // [at] and what is registered then: the temporal event (none, D = the Duration, M = a Moment's time), the last
    // temporal run, the phone's messages
    function reg(at as String) as Void {
        var t = Background.getTemporalEventRegisteredTime();
        var ts = t == null ? "none" : (t instanceof Time.Moment ? "M" + t.value() : "D" + (t as Time.Duration).value());
        var last = Background.getLastTemporalEventTime();
        var pm = Background has :getPhoneAppMessageEventRegistered ? Background.getPhoneAppMessageEventRegistered() : "-";
        log(at + " temporal " + ts + " last " + (last != null ? last.value() : "none") + " phoneMsg " + pm);
    }
}
