// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The data field's type per screen class, from the reference (tools/watchface/app.js S.dfSlot, S.dfAlert): every text
// size (px) and weight it draws in every form and state, the big numbers apart. Read by fonts.py:
//   node apps/garmin/field/tools/field_sizes.js  →  {"<cls>": {"w": px, "h": px, "s": scale, "mip": bool, "rect": bool,
//                                                   "atlas": [the class's sizes], "text": ["<px><m|s|b>", …],
//                                                   "nums": [px, …], "bell": px}, …}
const path = require('path');
const WL = require(path.join(__dirname, '..', '..', '..', '..', 'tools', 'watchface', 'app.js'));
const out = {};
const W = { 500: 'm', 600: 's', 700: 'b' };
for (const cls of Object.keys(WL.CLS)) {
  const k = WL.CLS[cls], texts = new Set(), nums = new Set();
  let bell = 0, s = 1;
  const take = (r) => {
    s = r.s;
    r.els.forEach((e) => {
      const sc = +/scale\(([-\d.]+)\)/.exec(e.gt)[1];
      if (e.isT) {
        const px = Math.round(e.fs * sc * 100) / 100;
        if (/^[0-9.:LOHI-]+$/.test(e.s[0]) && e.fw === 700) nums.add(Math.round(px));
        else texts.add(px + W[e.fw]);
      } else if (e.isP && e.tf && e.tf.indexOf('translate') === 0 && /scale\(([\d.]+)\)/.test(e.tf) && e.stroke !== 'none') {
        bell = Math.max(bell, Math.round(24 * sc));
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
    text: [...texts].sort(), nums: [...nums].sort((a, b) => a - b), bell: bell };
}
process.stdout.write(JSON.stringify(out) + '\n');
