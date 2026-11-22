// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Test;

// The data field's own rules (apps/garmin/tools/link-test.sh field): where a slot is from its obscured sides, what the
// activity's file gets (never an old value as current), the field's forms by slot room (SPEC §14.4).
(:test)
function fieldPlaceFromObscurity(logger as Logger) as Boolean {
    // DataField.OBSCURE_LEFT 1, TOP 2, RIGHT 4, BOTTOM 8; the 390's layouts (app.js DFS r390)
    FieldTestModel.at(FieldPlace.of(390, 194, 390, 390, 1 | 2 | 4), 0, 0); // half, top
    FieldTestModel.at(FieldPlace.of(390, 194, 390, 390, 1 | 4 | 8), 0, 196); // half, bottom
    FieldTestModel.at(FieldPlace.of(390, 138, 390, 390, 1 | 4), 0, 126); // band
    FieldTestModel.at(FieldPlace.of(194, 108, 390, 390, 1), 0, 141); // quarter, left
    FieldTestModel.at(FieldPlace.of(194, 108, 390, 390, 4), 196, 141); // quarter, right
    FieldTestModel.at(FieldPlace.of(130, 100, 390, 390, 0), 130, 145); // a middle cell
    FieldTestModel.at(FieldPlace.of(390, 390, 390, 390, 15), 0, 0); // the whole screen
    return true;
}

(:test)
function fieldRecordsOnlyFresh(logger as Logger) as Boolean {
    var p = {"g" => {"t" => 1000, "v" => 175}};
    Test.assertEqual(FieldRecord.value(p, 1030, false), 175);
    Test.assertEqual(FieldRecord.value(p, 1000 + Glucose.STRUCK_S, false), 175);
    Test.assert(FieldRecord.value(p, 1001 + Glucose.STRUCK_S, false) == null);
    Test.assertEqual(FieldRecord.value(p, 1030, true), 9.7);
    Test.assert(FieldRecord.value({"g" => null}, 1030, false) == null);
    Test.assert(FieldRecord.value(null, 1030, false) == null);
    // the FIT invalid values: UINT16 0xFFFF, FLOAT a NaN (not equal to itself)
    Test.assertEqual(FieldRecord.invalid(false), 0xFFFF);
    var nan = FieldRecord.invalid(true);
    Test.assert(nan instanceof Float && nan != nan);
    return true;
}

(:test)
function fieldFormBySlotRoom(logger as Logger) as Boolean {
    // the slots of contracts/watchface/field/cases.json's steady state on this watch's class: whole → the value, the
    // sparkline (its band a rectangle) and the label; a quarter → the value and its arrow only
    var d = new FieldInput();
    d.g = 112;
    d.age = 0.5;
    d.readAt = "20:40";
    d.deg = -12.0;
    d.hm = [10, 5, 0];
    d.hv = [108, 110, 112];
    d.noPhone = false;
    var W = 390;
    var whole = FieldLayout.field(d, new FieldSlot(0, 0, W, W, W, W, 15));
    Test.assert(FieldTestModel.kinds(whole).indexOf(0) >= 0 || FieldTestModel.kinds(whole).indexOf(6) >= 0); // the sparkline's band
    var quarter = FieldLayout.field(d, new FieldSlot(0, 141, 194, 108, W, W, 1));
    Test.assert(FieldTestModel.kinds(quarter).indexOf(0) < 0 && FieldTestModel.kinds(quarter).indexOf(6) < 0); // no sparkline
    Test.assert(FieldTestModel.kinds(quarter).indexOf(3) >= 0); // the arrow
    // not set up: no value, no arrow
    d.noPhone = true;
    var setup = FieldLayout.field(d, new FieldSlot(0, 141, 194, 108, W, W, 1));
    Test.assert(FieldTestModel.kinds(setup).indexOf(3) < 0);
    return true;
}

// the kinds of a model's elements (FieldG)
module FieldTestModel {
    function at(p as Array, x as Number, y as Number) as Void {
        Test.assertEqualMessage(p[0], x, "x " + p);
        Test.assertEqualMessage(p[1], y, "y " + p);
    }

    function kinds(g as FieldG) as Array {
        var out = [];
        for (var i = 0; i < g.els.size(); i++) {
            out.add((g.els[i] as Array)[0]);
        }
        return out;
    }
}
