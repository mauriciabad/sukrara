// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The data field's big numbers per screen class, from the reference (tools/watchface/app.js S.dfSlot, S.dfAlert): every
// size (px) it draws them at in every form and state. Its words are in the watch's own fonts (SPEC §14.1: a role, no
// atlas); a word in Atkinson fails here. Read by fonts.py:
//   node apps/garmin/field/tools/field_sizes.js  →  {"<cls>": {"w": px, "h": px, "s": scale, "mip": bool, "rect": bool,
//                                                   "atlas": [the class's sizes], "nums": [px, …], "bell": px}, …}
const path = require('path');
const WL = require(path.join(__dirname, '..', '..', '..', '..', 'tools', 'watchface', 'app.js'));
const out = {};
for (const cls of Object.keys(WL.CLS)) {
  const k = WL.CLS[cls], nums = new Set();
  let bell = 0, s = 1;
  const take = (r) => {
    s = r.s;
    r.els.forEach((e) => {
      const sc = +/scale\(([-\d.]+)\)/.exec(e.gt)[1];
      if (e.isT && !e.role) {
        if (!/^[0-9.:LOHI-]+$/.test(e.s[0]) || e.fw !== 700) throw new Error(cls + ': a word in Atkinson: ' + e.s[0]);
        nums.add(Math.round(e.fs * sc));
      } else if (e.isP && e.tf && e.tf.indexOf('translate') === 0 && /scale\(([\d.]+)\)/.test(e.tf) && e.stroke !== 'none') {
        bell = Math.max(bell, Math.round(24 * +/scale\(([\d.]+)\)/.exec(e.tf)[1] * sc)); // the icon's own size × the class
      }
    });
  };
  for (const form of ['whole', 'half', 'band', 'quarter', 'cap']) {
    for (const scenario of ['day', 'low', 'veryLow', 'high', 'inRange']) {
      for (const age of [0.5, 3, 8]) {
        for (const unit of ['mgdl', 'mmol']) take(WL.screen({ screen: 'dfSlot', cls, form, scenario, age, unit, others: false }));
      }
    }
    const d = WL.screen({ cls }).d;
    take(WL.screen({ screen: 'dfSlot', cls, form, others: false, input: Object.assign({}, d, { age: 30 }) }));
    take(WL.screen({ screen: 'dfSlot', cls, form, others: false, input: Object.assign({}, d, { g: null, noPhone: true }) }));
  }
  for (const kind of ['very_low', 'low', 'high', 'very_high', 'extremely_high', 'falling_fast', 'rising_fast', 'no_data']) {
    take(WL.screen({ screen: 'dfAlert', cls, kind, scenario: 'low', age: 3 }));
  }
  out[cls] = { w: k.w, h: k.h, s: s, mip: !!k.mip, rect: !!k.rect, atlas: k.mip ? WL.ATLAS.mip : WL.ATLAS.amoled,
    nums: [...nums].sort((a, b) => a - b), bell: bell };
}
process.stdout.write(JSON.stringify(out) + '\n');
