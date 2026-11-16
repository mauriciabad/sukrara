// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;

// The words the face draws, English and Spanish (SPEC.md §5). The card words are Sukrara's
// (contracts/graph/words/words.json, the key beside each); tools/watchface/garmin_test.py checks them against it.
// The face picks the language from the watch (AuraWatch), the tests from the case.
module AuraWords {
    const NB = " ";

    function es(lang as String) as Boolean {
        return lang.equals("es");
    }

    function weekdays(lang as String) as Array {
        return es(lang) ? ["DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"] : ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
    }

    function months(lang as String) as Array {
        return es(lang)
            ? ["ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"]
            : ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];
    }

    // the reading's time with its day ("Sun 19:09"); the date after 6 days ("12 Oct")
    function days(lang as String) as Array {
        return es(lang) ? ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"] : ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    }

    function monthsShort(lang as String) as Array {
        return es(lang)
            ? ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
            : ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    }

    function carbs(lang as String) as String { return es(lang) ? "Hidratos" : "Carbs"; } // badge_carbs_short
    function insulin(lang as String) as String { return es(lang) ? "Insulina" : "Insulin"; } // badge_insulin_short
    function muted(lang as String) as String { return es(lang) ? "Silenciadas" : "Muted"; } // badge_muted_short
    function ended(lang as String) as String { return es(lang) ? "Terminado" : "Ended"; } // badge_sensor_ended_short
    function replace(lang as String) as String { return es(lang) ? "Sensor" : "Sensor"; } // badge_replace_sensor_short
    function warming(lang as String) as String { return es(lang) ? "Calentando" : "Warming up"; } // card_warming_up
    function almost(lang as String) as String { return es(lang) ? "Casi listo" : "Almost ready"; } // card_almost_ready
    function noSince(lang as String) as String { return es(lang) ? "Sin lecturas desde" : "No reading since"; } // card_no_reading_since
    function noYet(lang as String) as String { return es(lang) ? "Sin lecturas" : "No readings"; } // card_no_readings_short
    function noPhone(lang as String) as String { return es(lang) ? "Sin móvil" : "No phone"; } // face_no_phone
    function now(lang as String) as String { return es(lang) ? "ahora" : "now"; } // duration_now

    // card_ready_at
    function readyAt(lang as String, t as String) as String {
        return (es(lang) ? "Listo a las " : "Ready at ") + t;
    }

    // duration_minutes / _hours / _hours_minutes / _days
    function m(lang as String, n as Number) as String { return n.toString() + (es(lang) ? " min" : "m"); }
    function h(lang as String, n as Number) as String { return n.toString() + (es(lang) ? " h" : "h"); }
    function d(lang as String, n as Number) as String { return n.toString() + (es(lang) ? " d" : "d"); }
    function hm(lang as String, hh as Number, mm as Number) as String {
        return es(lang) ? hh.toString() + " h " + mm.toString() + " min" : hh.toString() + "h " + mm.toString() + "m";
    }

    // Sukrara's compact durations (contracts/settings/durations.json "compact"; under a minute "now", as the card)
    function dur(lang as String, min as Float) as String {
        var mm = Math.floor(min + 0.5).toNumber();
        if (mm < 1) {
            return now(lang);
        }
        if (mm < 60) {
            return m(lang, mm);
        }
        if (mm < 360) {
            var hh = mm / 60;
            var rest = mm % 60;
            return rest == 0 ? h(lang, hh) : hm(lang, hh, rest);
        }
        if (mm < 2880) {
            return h(lang, mm / 60);
        }
        return d(lang, mm / 1440);
    }

    // thousands with "," (en) / "." (es)
    function thousands(lang as String, n as Number) as String {
        var neg = n < 0;
        var s = (neg ? -n : n).toString();
        var sep = es(lang) ? "." : ",";
        var out = "";
        var len = s.length();
        for (var i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                out += sep;
            }
            out += s.substring(i, i + 1);
        }
        return (neg ? "-" : "") + out;
    }

    // a treatment's amount: 4.5u / 4,5u, 45g
    function amount(lang as String, kind as String, a as Numeric or String) as String {
        var s;
        if (a instanceof Float || a instanceof Double) {
            var f = a.toFloat();
            s = f == Math.floor(f) ? f.toNumber().toString() : f.format("%.1f");
        } else {
            s = a.toString();
        }
        if (es(lang)) {
            var i = s.find(".");
            if (i != null) {
                s = s.substring(0, i) + "," + s.substring(i + 1, s.length());
            }
        }
        return s + (kind.equals("carbs") ? "g" : "u");
    }
}
