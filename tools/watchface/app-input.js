// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The watch app's input (apps/garmin/app AppData, as its case loader prints it: "IN|<case>|<json>") as app.js's input
// `d`, so the reference draws the screen from exactly what the watch drew it from (contracts/watchface/app/cases.json,
// apps/garmin/app/tools/app_test.py). Pure: fromWatch(x) -> { input, cfg } for WL.screen(Object.assign(cfg, { input })).
var AppInput = (function () {
  var BANDS = ['veryLow', 'low', 'tight', 'target', 'high', 'veryHigh'];
  function fmtG(v, unit) {
    if (v < 40) return 'LO';
    if (v > 400) return 'HI';
    return unit === 'mmol' ? (v / 18.0182).toFixed(1) : String(Math.round(v));
  }
  // a metric's number as the card shows it (AppParts.number)
  function number(m, unit) {
    if (m.v === null || m.v === undefined) return '--';
    if (m.k === 'avg') return fmtG(m.v, unit);
    if (m.k === 'gmi') return Number(m.v).toFixed(1);
    return String(Math.trunc(m.v));
  }
  // a share in per mille as the report shows it (AppScreens.pctText)
  function pct(pm) { return (pm > 0 && pm < 10 ? (pm / 10).toFixed(1) : String(Math.floor((pm + 5) / 10))) + ' %'; }

  function fromWatch(x) {
    var d = {
      g: x.g, age: x.age, readAt: x.readAt, readyAt: x.readyAt, unit: x.unit, clock: x.clock, clockH: x.clockH,
      sensorDays: x.sensorDays, sensorState: x.sensorState === 'warmup' ? 'warmup' : 'ok', order: x.order, lang: 'en'
    };
    // app.js turns the arrow by the rate; the watch by the phone's angle
    d.rate = x.rate === null ? NaN : x.doubled ? (x.deg < 0 ? 3 : -3) : -x.deg / 30;
    d.hist = [];
    for (var i = 0; i < x.hm.length; i++) if (x.hv[i] !== null) d.hist.push({ m: x.hm[i], v: x.hv[i] });
    d.tr = x.trm.map(function (m, i) { return { m: Math.round(m), k: x.trk[i], a: x.tra[i] }; });
    d.pill = x.pill ? { k: x.pill[0], text: x.pill[1], filled: x.pill[2], icon: x.pill[3] } : null;
    var order = x.ro.map(function (i) { return BANDS[i]; });
    d.mets = x.slots.map(function (s) {
      if (s[0] === 'last') return { kind: 'last', slot: { k: s[1], m: s[2] } };
      var m = s[1], split = [0, 0, 0, 0, 0, 0, 0];
      if (m.k === 'tir' && m.b) m.b.forEach(function (v, i) { split[i] = v; });
      return { kind: 'ring', tir: { pct: number(m, x.unit), split: split }, dim: m.dim === true, order: order };
    });
    if (x.rep) {
      var b = x.rep.b, above = b[4] + b[5], inR = b[2] + b[3], below = b[0] + b[1];
      d.rep = {
        above: pct(above), inR: pct(inR), good: pct(b[2]), below: pct(below), split: b.concat([0]),
        met: [(x.rep.ok & 2) !== 0, (x.rep.ok & 4) !== 0, (x.rep.ok & 8) !== 0],
        nums: [[x.rep.avg !== null ? fmtG(x.rep.avg, x.unit) : '--', 'Average', (x.rep.ok & 32) !== 0],
          [x.rep.gmi !== null ? Number(x.rep.gmi).toFixed(1) + ' %' : '--', 'GMI', (x.rep.ok & 64) !== 0],
          [x.rep.cv !== null ? Number(x.rep.cv).toFixed(0) + ' %' : '--', 'CV', (x.rep.ok & 128) !== 0]]
      };
    }
    var cfg = Object.assign({ screen: x.screen, hours: x.hours, dist: x.side, period: x.days + 'd' }, x.cfg || {});
    d.lastBy = {};
    (x.rows || []).forEach(function (r) {
      if (r[0] === 'mute') { if (r[2] !== null) cfg.muted = true; } else d.lastBy[r[0]] = r[2] || '';
    });
    return { input: d, cfg: cfg };
  }
  return { fromWatch: fromWatch };
})();
if (typeof module !== 'undefined') module.exports = AppInput;
