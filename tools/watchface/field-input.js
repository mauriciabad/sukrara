// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The data field's input (apps/garmin/field FieldInput, as its case loader prints it: "IN|<case>|<json>") as app.js's
// input `d` and cfg, so the reference draws the slot from exactly what the watch drew it from
// (contracts/watchface/field/cases.json, apps/garmin/field/tools/field_test.py). Pure: fromWatch(x) -> { input, cfg }.
var FieldInput = (function () {
  function fromWatch(x) {
    var d = { g: x.g, age: x.age, readAt: x.readAt, unit: x.unit, noPhone: x.noPhone === true, update: x.update || null, lang: 'en' };
    // app.js turns the arrow by the rate; the watch by the phone's angle (AppInput's rule)
    d.rate = x.deg === null ? NaN : x.doubled ? (x.deg < 0 ? 3 : -3) : -x.deg / 30;
    // the history before the reading, 5 min apart, oldest first (null = no reading in that slot), then the reading
    d.hist = [];
    var n = x.hist.length;
    for (var i = 0; i < n; i++) if (x.hist[i] !== null) d.hist.push({ m: 5 * (n - i), v: x.hist[i] });
    if (x.g !== null) d.hist.push({ m: 0, v: x.g });
    var cfg = { cls: x.cls, screen: x.screen, form: x.form, others: false };
    if (x.slots) cfg.slots = x.slots;
    if (x.kind) cfg.kind = x.kind;
    return { input: d, cfg: cfg };
  }
  return { fromWatch: fromWatch };
})();
if (typeof module !== 'undefined') module.exports = FieldInput;
