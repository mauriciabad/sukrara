// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Sukrara Aura: the reference implementation of the watch face (contracts/watchface/aura/SPEC.md).
// THE source of truth for every number in the spec: the layout, the geometry and the look of every state, as one pure
// function of the inputs. It is the design canvas's face component, unchanged in logic; the Garmin face must produce the
// same numbers (contracts/watchface/aura/expect.json) and the same pictures (docs/img/aura/ref/*.png).
//
// Use: node tools/watchface/build.mjs (writes expect.json and the PNGs), or open tools/watchface/render.html#cfg=<json>.
// Coordinates: the screen's centre is (0, 0), y grows down, the screen is the circle r = 195 (device pixel = x + 195).
// Angles: degrees clockwise from 12 o'clock; P(r, a) = (r·sin a, −r·cos a).

class AuraFace {
  constructor(cfg) { this.props = { cfg: cfg || {} }; }
  opt() {
    const p = this.props || {};
    const c = p.cfg || {};
    const g = (k, d) => {
      if (c[k] !== undefined && c[k] !== null) return c[k];
      if (p[k] !== undefined && p[k] !== null && p[k] !== '') return p[k];
      return d;
    };
    const b = (k, d) => { const v = g(k, d); return v === true || v === 'true'; };
    return {
      glucose: Number(g('glucose', 112)),
      rate: g('rate', 0.4),
      trend: b('trend', true),
      age: Number(g('age', 0.5)),
      history: g('history', null),
      spark: g('spark', null),
      sensor: String(g('sensor', 'ok')),
      status: String(g('status', 'auto')),
      left: Number(g('left', 25)),
      mode: String(g('mode', 'awake')),
      privacy: String(g('privacy', 'open')),
      reveal: Number(g('reveal', 0.6)),
      unit: String(g('unit', 'mgdl')),
      lang: String(g('lang', 'en')),
      filter: String(g('filter', 'none')),
      bezel: b('bezel', true),
      metal: String(g('metal', 'carbon')),
      size: Number(g('size', 584)),
      time: String(g('time', '10:09:36')),
      battery: Number(g('battery', 8.6)),
      batteryPct: g('batteryPct', null),
      battLabel: String(g('battLabel', 'days')),
      rangeColour: b('rangeColour', true),
      seconds: b('seconds', true),
      halo: String(g('halo', 'outline')),
      topField: String(g('topField', 'weather')),
      fields: g('fields', null),
      last: g('last', null),
      lastKind: String(g('lastKind', 'rapid')),
      lastAmount: g('lastAmount', 2),
      lastAgo: Number(g('lastAgo', 80)),
      steps: Number(g('steps', 6420)),
      phone: b('phone', true),
      numerals: String(g('numerals', 'atkinson')),
      dial: String(g('dial', 'halftone')),
      trendStyle: String(g('trendStyle', 'arrow')),
      warm: Number(g('warm', 0.45)),
      sensorLeft: Number(g('sensorLeft', 5)),
      tir: c.tir === null ? null : Number(g('tir', 82)),
    };
  }

  words(lang) {
    if (lang === 'es') {
      return {
        wd: ['DOM', 'LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB'], mo: ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'],
        dw: ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'], ms: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'],
        carbs: 'Hidratos', insulin: 'Insulina', muted: 'Silenciadas', ended: 'Terminado', replace: 'Sensor',
        sensorEnded: 'Terminado', warming: 'Calentando', readyAt: (t) => 'Listo a las ' + t, almost: 'Casi listo',
        noSince: 'Sin lecturas desde', noYet: 'Sin lecturas', noPhone: 'Sin móvil', now: 'ahora',
        m: (n) => n + ' min', h: (n) => n + ' h', hm: (h, m) => h + ' h ' + m + ' min', d: (n) => n + ' d',
      };
    }
    return {
      wd: ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'], mo: ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'],
      dw: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'], ms: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      carbs: 'Carbs', insulin: 'Insulin', muted: 'Muted', ended: 'Ended', replace: 'Sensor',
      sensorEnded: 'Ended', warming: 'Warming up', readyAt: (t) => 'Ready at ' + t, almost: 'Almost ready',
      noSince: 'No reading since', noYet: 'No readings', noPhone: 'No phone', now: 'now',
      m: (n) => n + 'm', h: (n) => n + 'h', hm: (h, m) => h + 'h ' + m + 'm', d: (n) => n + 'd',
    };
  }

  fonts() {
    return {
      atkinson: { fam: "'Atkinson Hyperlegible Next', system-ui, sans-serif", cap: 0.668, cell: 0.656, colon: 0.253, dot: 0.253 },
      redhat: { fam: "'Red Hat Display', system-ui, sans-serif", cap: 0.7, cell: 0.672, colon: 0.25, dot: 0.25 },
      inter: { fam: "'Inter Tight', system-ui, sans-serif", cap: 0.728, cell: 0.63, colon: 0.282, dot: 0.282 },
      barlow: { fam: "'Barlow', system-ui, sans-serif", cap: 0.7, cell: 0.585, colon: 0.3, dot: 0.273 },
    };
  }

  pal(mode) {
    const base = {
      ink: '#FFFFFF', soft: '#A3A3A8', dim: '#6E6E73', faint: '#3A3A3E', grid: '#26262A', band: '#38383E', band2: '#4E4E55', grey: '#8E8E93', texture: '#19191C',
      veryLow: '#C2413B', low: '#F0524F', tight: '#3DD47A', target: '#8CC63F', high: '#F5A524', veryHigh: '#FF7A45', extremelyHigh: '#E35BD0',
      now: '#C6F432', rapid: '#8AB4FF', long: '#C9B0FF', carbs: '#FFB74D', note: '#B4B4B4', neutral: '#A3A3A8',
    };
    if (mode === 'night') {
      return Object.assign({}, base, {
        ink: '#8C8C92', soft: '#5E5E64', dim: '#46464B', faint: '#2A2A2E', grid: '#1A1A1D', band: '#242428', band2: '#303035', grey: '#56565B',
        veryLow: '#A63A35', low: '#B8433F', tight: '#2E9C5C', target: '#6B9631', high: '#B57C1E', veryHigh: '#BC5C36', extremelyHigh: '#A84A9C',
        now: '#7E9A26', rapid: '#5F7EB3', long: '#8C7AB3', carbs: '#B3833A', note: '#7A7A7E', neutral: '#5E5E64',
      });
    }
    if (mode === 'aod') {
      return Object.assign({}, base, { ink: '#C9C9CE', soft: '#7A7A80', dim: '#55555A', faint: '#323236', grid: '#1C1C1F', band: '#2A2A2E', band2: '#2A2A2E', grey: '#6E6E73', now: '#A9CF2A' });
    }
    return base;
  }

  icons() {
    return {
      sun: { d: 'M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0', s: true },
      steps: { d: 'M4.6 8.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM5.8 16.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0zM12.6 11.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM13.8 19.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z', s: false },
      sunset: { d: 'M3 18h18M7 18a5 5 0 0 1 10 0M12 3v7M9 7.5l3 3 3-3', s: true },
      bolt: { d: 'M13.5 2L5.5 13.5h5.2L9.8 22l8.2-12h-5.3z', s: false },
      mountain: { d: 'M2 20L9.5 7l4 6.5 2.3-3.6L22 20z', s: false },
      heart: { d: 'M12 20.5s-7.5-4.6-7.5-10.4A4.1 4.1 0 0 1 12 7.6a4.1 4.1 0 0 1 7.5 2.5c0 5.8-7.5 10.4-7.5 10.4z', s: false },
      bell: { d: 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0', s: true },
      ring: { d: 'M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16M12 4a8 8 0 0 1 7.6 10.5', s: true },
      globe: { d: 'M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18M3 12h18M12 3c2.6 2.7 3.8 5.7 3.8 9s-1.2 6.3-3.8 9M12 3c-2.6 2.7-3.8 5.7-3.8 9s1.2 6.3 3.8 9', s: true },
      stairs: { d: 'M3 20h5v-5h5v-5h5V5h3', s: true },
      phoneOff: { d: 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM3 3l18 18', s: true },
      sensor: { d: 'M21.3 12a9.3 9.3 0 1 1-18.6 0a9.3 9.3 0 1 1 18.6 0zM18.7 12a6.7 6.7 0 1 0-13.4 0a6.7 6.7 0 1 0 13.4 0zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z', s: false },
      rapid: { d: 'M2 4.2H22L12 20.9Z', s: false },
      long: { d: 'M3.5 5.4H20.5L12 19.6Z', s: true, sw: 2.8 },
      carbs: { d: 'M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z', s: false },
    };
  }

  widths() {
    if (!this._w) {
      const k = ' 0123456789.:·ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúñÁÉÍÓÚÑ';
      const w = [0.28, 0.648, 0.407, 0.548, 0.576, 0.614, 0.588, 0.598, 0.51, 0.615, 0.595, 0.21, 0.21, 0.24, 0.626, 0.613, 0.645, 0.669, 0.569, 0.552, 0.694, 0.694, 0.392, 0.512, 0.631, 0.541, 0.824, 0.693, 0.714, 0.592, 0.724, 0.614, 0.592, 0.558, 0.69, 0.593, 0.856, 0.623, 0.575, 0.607, 0.525, 0.566, 0.485, 0.564, 0.538, 0.314, 0.558, 0.55, 0.252, 0.245, 0.492, 0.262, 0.845, 0.55, 0.548, 0.566, 0.574, 0.349, 0.471, 0.326, 0.54, 0.461, 0.684, 0.481, 0.449, 0.46, 0.525, 0.538, 0.252, 0.548, 0.54, 0.55, 0.626, 0.569, 0.392, 0.714, 0.69, 0.693];
      const m = {};
      Array.from(k).forEach((ch, i) => { m[ch] = w[i]; });
      m[' '] = 0.28;
      this._w = m;
    }
    return this._w;
  }

  tw(s, size) {
    const m = this.widths();
    let t = 0;
    for (const ch of String(s)) t += (m[ch] !== undefined ? m[ch] : 0.6);
    return t * size * 1.03;
  }

  K(g) { return 1.509 * (Math.pow(Math.log(g), 1.084) - 5.381); }

  ang(g) {
    const k0 = this.K(40), k1 = this.K(400);
    const v = Math.min(400, Math.max(40, g));
    return 133 - 86 * (this.K(v) - k0) / (k1 - k0);
  }

  band(g) {
    if (g <= 55) return 'veryLow';
    if (g < 70) return 'low';
    if (g <= 140) return 'tight';
    if (g < 180) return 'target';
    if (g < 250) return 'high';
    return 'veryHigh';
  }

  mapLines() {
    if (this._ct) return this._ct;
    let d = '';
    const cx = -38, cy = 52;
    for (let k = 1; k <= 12; k++) {
      const base = 24 * k;
      const pts = [];
      for (let i = 0; i < 120; i++) {
        const t = (i / 120) * Math.PI * 2;
        const f = 1 + 0.12 * Math.sin(3 * t + 1.3) + 0.07 * Math.sin(5 * t + 0.4) + 0.05 * Math.sin(2 * t + 2.2);
        const r = base * f + 5 * Math.sin(4 * t + k * 0.9);
        pts.push((cx + r * Math.cos(t)).toFixed(1) + ' ' + (cy + r * Math.sin(t)).toFixed(1));
      }
      d += 'M' + pts.join('L') + 'Z';
    }
    this._ct = d;
    return d;
  }

  renderVals() {
    const c = this.opt();
    if (!this._uid) this._uid = 'a';
    const u = this._uid;
    const ids = { clip: u + 'c', red: u + 'r', grey: u + 'g', metal: u + 'm', sheen: u + 's', btn: u + 'b', glow: u + 'w', cg: u + 'x', wg: u + 'y', rg: u + 'q', rm: u + 'k', halo: u + 'h', top: u + 't', bottom: u + 'o', nofly: u + 'n' };
    const W = this.words(c.lang);
    const FS = this.fonts();
    const F = FS[c.numerals] || FS.atkinson;
    const LF = "'Atkinson Hyperlegible Next', system-ui, sans-serif";
    const aod = c.mode === 'aod', night = c.mode === 'night', awake = !aod && !night;
    const pal = this.pal(c.mode);
    const ICONS = this.icons();
    const NB = '\u00a0';
    const r1 = (x) => Math.round(x * 10) / 10;
    const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
    const P = (r, a) => { const t = a * Math.PI / 180; return [r1(r * Math.sin(t)), r1(-r * Math.cos(t))]; };
    const arc = (r, a1, a2, ccw) => {
      const p1 = P(r, a1), p2 = P(r, a2);
      let d = ccw ? a1 - a2 : a2 - a1;
      d = ((d % 360) + 360) % 360;
      return 'M' + p1[0] + ' ' + p1[1] + 'A' + r + ' ' + r + ' 0 ' + (d > 180 ? 1 : 0) + ' ' + (ccw ? 0 : 1) + ' ' + p2[0] + ' ' + p2[1];
    };
    const show = (b) => (b ? 'inline' : 'none');
    // Sukrara's compact durations (contracts/settings/durations.json "compact"; under a minute "now", as the card)
    const dur = (min) => {
      const m = Math.max(0, Math.round(min));
      if (m < 1) return W.now;
      if (m < 60) return W.m(m);
      const h = Math.floor(m / 60), mm = m % 60;
      if (m < 360) return mm ? W.hm(h, mm) : W.h(h);
      if (m < 2880) return W.h(h);
      return W.d(Math.floor(m / 1440));
    };
    const clock = (d) => d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
    const noIcon = { d: 'M0 0', tf: 'translate(0 0)', fill: 'none', stroke: 'none', sw: 0, disp: 'none' };
    const iconAt = (key, x, y, rot, size, col) => {
      const ic = ICONS[key];
      if (!ic) return noIcon;
      const k = Math.round(size / 24 * 1000) / 1000;
      return { key, d: ic.d, tf: 'translate(' + r1(x) + ' ' + r1(y) + ') rotate(' + r1(rot) + ') scale(' + k + ') translate(-12 -12)', fill: ic.s ? 'none' : col, stroke: ic.s ? col : 'none', sw: ic.sw || 2, disp: 'inline' };
    };

    let now;
    const live = c.time === 'live';
    if (live) now = new Date();
    else {
      const tp = c.time.split(':').map(Number);
      now = new Date(2026, 10, 16, tp[0] || 0, tp[1] || 0, tp[2] || 0);
    }

    // the reading: Sukrara's decisions (docs/42 §3)
    const reads = c.sensor === 'ok' || c.sensor === 'ending' || c.sensor === 'endedSending';
    const fr = c.age <= 1.5 ? 'fresh' : c.age <= 5 ? 'late' : c.age <= 20 ? 'stale' : c.age < 180 ? 'gap' : 'long';
    const hasValue = reads && (fr === 'fresh' || fr === 'late' || fr === 'stale');
    const struck = hasValue && fr === 'stale';
    const current = hasValue && !struck;
    const g = c.glucose, lo = g < 40, hi = g > 400;
    const bk = lo ? 'veryLow' : hi ? 'veryHigh' : this.band(g);
    const ck = (hi || g >= 300) ? 'extremelyHigh' : bk;
    const out = bk !== 'tight' && bk !== 'target';
    const lowSide = bk === 'veryLow' || bk === 'low';
    const rate = Number(c.rate);
    const trendOn = c.trend && Number.isFinite(rate) && !lo && !hi && !struck;
    const readAt = new Date(now.getTime() - c.age * 60000);
    // Sukrara's Freshness.timeText (contracts/graph/freshness/freshness.json "times"): the clock while it's today's and
    // at most 12 h old, the weekday with it from 12 h or on another day, the date after 6 days
    const ageS = (now.getTime() - readAt.getTime()) / 1000;
    const readTxt = ageS > 6 * 86400 ? readAt.getDate() + NB + W.ms[readAt.getMonth()]
      : (ageS > 12 * 3600 || readAt.toDateString() !== now.toDateString() ? W.dw[readAt.getDay()] + NB : '') + clock(readAt);

    // the pill: Sukrara's need or what holds it, short, with its icon
    let pill = null;
    // time left of a quiet or mute; a mute with no end (left = 'never') says Muted, as Sukrara's card does
    const left = Number.isFinite(c.left) ? dur(c.left) : W.muted;
    if (current) {
      if (c.status === 'auto' && out) pill = lowSide ? { k: 'carbs', t: W.carbs, tonal: false, icon: 'carbs' } : { k: 'rapid', t: W.insulin, tonal: false, icon: 'rapid' };
      else if (c.status === 'quiet' && out) pill = { k: lowSide ? 'carbs' : 'rapid', t: left, tonal: true, icon: 'paused' };
      else if (c.status === 'muted') pill = out ? { k: lowSide ? 'carbs' : 'rapid', t: left, tonal: true, icon: 'paused' } : { k: 'note', t: left, tonal: true, icon: 'off' };
    }
    if (!pill && c.sensor === 'endedSending' && hasValue) pill = { k: 'note', t: W.ended, tonal: false, icon: 'sensor' };
    if (c.sensor === 'ended') pill = { k: 'note', t: W.replace, tonal: false, icon: 'sensor' };
    const needed = !!(pill && !pill.tonal && (pill.k === 'carbs' || pill.k === 'rapid'));
    const priv = c.privacy === 'private', revealed = c.privacy === 'revealed';
    const valueOn = hasValue && (!priv || needed || revealed);
    // the reading's time isn't the number: it shows in private too, so a hidden late or old reading still says so
    const timeOn = hasValue && (fr === 'late' || fr === 'stale');
    const pillOn = !!pill && (hasValue || c.sensor === 'ended');
    const topRow = c.topField !== 'none';
    const topOn = !aod && topRow;
    const msgKind = (c.sensor === 'warmup' || c.sensor === 'almost') ? 'warm' : c.sensor === 'ended' ? 'ended' : c.sensor === 'none' ? 'none' : !hasValue ? 'gap' : null;
    const chipKind = pillOn ? 'pill' : (timeOn || msgKind === 'warm') ? 'age' : null;

    // the layout: the rows that are there, centred between the date and the bottom fields, as big as the circle allows
    const ts = c.trendStyle === 'spark' ? 'spark' : 'arrow';
    const vStr = lo ? 'LO' : hi ? 'HI' : c.unit === 'mmol' ? (g / 18.0182).toFixed(1) : String(Math.round(g));
    const tStr = clock(now);
    const timeGeom = (T) => {
      const tFs = T / F.cap;
      const tc = (ch) => (ch === ':' ? F.colon + 0.1 : F.cell) * tFs;
      let tW = 0;
      const txs = [];
      for (const ch of tStr) { const w = tc(ch); txs.push(tW + w / 2); tW += w; }
      return { tFs, tW, txs };
    };
    const valueGeom = (V) => {
      const vFs = V / F.cap;
      const cw = (ch) => (ch === '.' ? F.dot + 0.06 : F.cell) * vFs;
      let vW = 0;
      const vxs = [];
      for (const ch of vStr) { const w = cw(ch); vxs.push(vW + w / 2); vW += w; }
      const gap = 0.08 * vFs, box = 0.62 * vFs, SW = 100;
      const total = ts === 'spark' ? vW + 14 + SW : vW + gap + box;
      return { vFs, vW, vxs, gap, box, total, SW };
    };
    const battDaysT = c.battery < 1 ? W.h(Math.max(1, Math.round(c.battery * 24))) : W.d(Math.round(c.battery));
    const battPctN = c.batteryPct !== null && c.batteryPct !== undefined ? Number(c.batteryPct) : Math.round(c.battery * 10);
    const battPctT = clamp(Math.round(battPctN), 0, 100) + '%';
    const battBoth = c.battLabel === 'both';
    const battL1 = c.battLabel === 'percent' ? battPctT : battDaysT;
    const battFs = battBoth ? 20 : 22;
    // both: stacked (days over percent) where the stack fits beside the value or message; else on one line, which always
    // fits in the gap above the value's row
    const battOneT = battDaysT + NB + '·' + NB + battPctT;
    const battWOf = (one) => (one ? this.tw(battOneT, battFs) : Math.max(this.tw(battL1, battFs), battBoth ? this.tw(battPctT, battFs) : 0));
    const battHOf = (one) => (battBoth && !one ? battFs * 0.668 * 2 + 9 : battFs * 0.668);
    const halfAt = (yy) => Math.sqrt(Math.max(0, 171 * 171 - yy * yy)) - 6;
    const sets = chipKind === 'pill' ? [[62, 54, 24]] : chipKind === 'age' ? [[64, 56, 24], [62, 54, 24]] : [[68, 60, 26], [66, 58, 26], [64, 56, 25], [62, 54, 24]];
    // the left edge of the value's row: the value (laid out even while private hides it, so nothing moves on reveal),
    // or the first line of a message
    const msgLeft = msgKind === 'warm' ? -(46 + this.tw(W.warming, 32)) / 2 : msgKind === 'ended' ? -(38 + this.tw(W.sensorEnded, 32)) / 2
      : msgKind === 'none' ? -this.tw(W.noYet, 30) / 2 : msgKind === 'gap' ? -this.tw(W.noSince, 25) / 2 : 0;
    const fitRows = (one) => {
    let L = null, fits = false;
    for (const set of sets) {
      const T = set[0], V = set[1], TF = set[2];
      const roomy = !chipKind;
      const cap2 = roomy ? 40 : 36;
      const vb = msgKind === 'gap' ? 16.7 + 12 + cap2 : msgKind === 'warm' ? 34 : msgKind === 'ended' ? 32 : msgKind === 'none' ? 20 : V;
      const chipH = chipKind === 'pill' ? 38 : chipKind === 'age' ? 17 : 0;
      const tfc = topRow ? TF * 0.668 : 0;
      const H = (topRow ? tfc + 20 : 0) + T + 22 + vb + (chipH ? 18 + chipH : 0);
      let y = -7 - H / 2;
      const l = { T, V, TF, roomy, cap2, chipH };
      if (topRow) { l.tfBase = y + tfc; y += tfc + 20; } else l.tfBase = y;
      l.tTop = y; l.tBase = y + T; y += T + 22;
      l.vTop = y; l.vBase = y + vb; y += vb;
      l.chipC = chipH ? y + 18 + chipH / 2 : y;
      l.tg = timeGeom(T);
      l.vg = valueGeom(V);
      L = l;
      const okT = l.tg.tW / 2 <= halfAt(l.tTop);
      const okV = !hasValue || (l.vg.total / 2 <= halfAt(l.vTop) && l.vg.total / 2 <= halfAt(l.vBase));
      const lBot = l.tBase + 6 + battHOf(one);
      const left = hasValue ? -l.vg.total / 2 : msgLeft;
      const okL = aod || lBot <= l.vTop || left >= -166 + battWOf(one) + 8;
      if (okT && okV && okL) { fits = true; break; }
    }
    return { L, fits };
    };
    // stacked only where some size fits with it; otherwise one line (which never meets the value's row)
    const battOne = battBoth && !fitRows(false).fits;
    const L = fitRows(battOne).L;
    const battW = battWOf(battOne), battH = battHOf(battOne);

    // the value, with Sukrara's arrow or the sparkline
    const vCap = L.V, vY = L.vBase;
    const vFs = L.vg.vFs, vW = L.vg.vW, vxs = L.vg.vxs, gap = L.vg.gap, box = L.vg.box, total = L.vg.total, SW = L.vg.SW;
    const x0 = -total / 2, xv = x0;
    const sx = xv + vW + 14;
    const ax = r1(ts === 'arrow' ? x0 + vW + gap + box / 2 : 0);
    const ay = r1(vY - vCap / 2);
    const inverted = !struck && bk === 'veryLow';
    let vFill = pal.ink;
    if (struck) vFill = pal.grey;
    else if (inverted) vFill = '#FFFFFF';
    else if (out) vFill = pal[ck];
    else if (c.rangeColour) vFill = pal[bk];
    const s = box * 1.05, q = (x) => r1(x * s);
    let arrow = 'M' + q(-0.42) + ' ' + q(-0.07) + 'H' + q(0.25) + 'V' + q(0.07) + 'H' + q(-0.42) + 'Z' +
      'M' + q(0.46) + ' 0L' + q(0.06) + ' ' + q(0.3) + 'L' + q(0.16) + ' 0L' + q(0.06) + ' ' + q(-0.3) + 'Z';
    if (Math.abs(rate) >= 3) arrow += 'M' + q(0.12) + ' 0L' + q(-0.28) + ' ' + q(0.3) + 'L' + q(-0.18) + ' 0L' + q(-0.28) + ' ' + q(-0.3) + 'Z';
    const deg = r1(-clamp((Number.isFinite(rate) ? rate : 0) / 3, -1, 1) * 90);
    const blkR = ts === 'arrow' ? x0 + total : xv + vW;
    const revealLen = clamp(c.reveal, 0, 1) * vW;
    const val = {
      disp: show(valueOn), str: vStr, fam: F.fam, fs: r1(vFs), y: r1(vY),
      xs: vxs.map((x) => r1(xv + x)).join(' '), fill: vFill,
      blk: show(inverted), bx: r1(xv - 12), by: r1(vY - vCap - 12), bw: r1(blkR - xv + 24), bh: vCap + 24,
      strike: show(struck), sx1: r1(xv - 6), sx2: r1(xv + vW + 6), sy: r1(vY - vCap * 0.48), sw: r1(0.075 * vFs),
      arrow, tf: 'translate(' + ax + 'px, ' + ay + 'px) rotate(' + deg + 'deg)', arrowD: show(valueOn && ts === 'arrow' && trendOn),
      ax, ay, ringR: r1(0.24 * box), ringW: r1(0.11 * box), ringD: show(ts === 'arrow' && !trendOn),
      revealD: show(revealed && hasValue), rx1: r1(xv + vW / 2 - revealLen / 2), rx2: r1(xv + vW / 2 + revealLen / 2), ry: r1(vY + 13),
    };

    // the sparkline: the last hour on a log-time axis (the newest minutes widest), Kovatchev's scale like the rail
    const k40 = this.K(40), k300 = this.K(300);
    const SH = vCap + 14, sy = vY - vCap - 6;
    const yv = (v) => r1(sy + SH * (1 - (this.K(clamp(v, 40, 300)) - k40) / (k300 - k40)));
    const xa = (age) => r1(sx + SW * (1 - Math.log(1 + age / 10) / Math.log(7)));
    const series = Array.isArray(c.spark) ? c.spark.slice() : Array.isArray(c.history) ? c.history.concat([g]) : (Number.isFinite(rate) ? [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0].map((k) => g - rate * 5 * k) : [g]);
    const pts = [];
    // (a lone point between gaps is a dot: its zero-length subpath, closed, takes the round cap)
    let line = '', run = 0;
    const cut = () => { if (run === 1) line += 'Z'; run = 0; };
    series.forEach((v, j) => {
      const age = c.age + 5 * (series.length - 1 - j);
      if (age > 60) return;
      if (v === null || v === undefined) { cut(); return; }
      const pt = [xa(age), yv(Number(v))];
      pts.push(pt);
      line += (run ? 'L' : 'M') + pt[0] + ' ' + pt[1];
      run++;
    });
    cut();
    const end = pts.length ? pts[pts.length - 1] : [r1(sx + SW), yv(g)];
    const rl = 22, rad = deg * Math.PI / 180;
    const beadCol = out ? pal[ck] : (c.rangeColour ? pal[bk] : pal.now);
    const sp = {
      disp: show(valueOn && ts === 'spark'),
      limits: 'M' + r1(sx) + ' ' + yv(180) + 'H' + r1(sx + SW) + 'M' + r1(sx) + ' ' + yv(70) + 'H' + r1(sx + SW), limCol: aod ? '#3A3A3E' : '#5C5C62',
      line: line || 'M0 0', lineCol: struck ? pal.dim : pal.soft,
      ray: 'M' + end[0] + ' ' + end[1] + 'l' + r1(rl * Math.cos(rad)) + ' ' + r1(rl * Math.sin(rad)), rayD: show(trendOn), rayCol: vFill === '#FFFFFF' ? pal.veryLow : vFill,
      ex: end[0], ey: end[1], endFill: current && trendOn ? beadCol : '#000000', endStroke: current && trendOn ? 'none' : (struck ? pal.grey : vFill),
    };

    // the chip row: the reading's time once it's late, then the pill
    const ageW = timeOn ? this.tw(readTxt, 24) : 0;
    const pw = pillOn ? 15.2 + 23.6 + 9.5 + this.tw(pill.t, 24) + 17.1 : 0;
    const gapA = timeOn && pillOn ? 14 : 0;
    const rx0 = -(ageW + gapA + pw) / 2;
    const chip = { disp: show(timeOn), x: r1(rx0), y: r1(L.chipC + 8), t: readTxt };
    let pillV = { disp: 'none', x: 0, y: 0, w: 0, fill: '#000', fo: 1, tc: '#000', tx: 0, ty: 0, t: '', mark: noIcon, bell: { d: 'M0 0', tf: 'translate(0 0)', disp: 'none' } };
    if (pillOn) {
      const px = rx0 + ageW + gapA, col = pal[pill.k] || pal.note, fg = pill.tonal ? col : '#1C1C1E';
      const icx = px + 15.2 + 11.8, icy = L.chipC;
      const bellD = pill.icon === 'off'
        ? 'M160-200v-80h80v-280q0-33 8.5-65t25.5-61l60 60q-7 16-10.5 32.5T320-560v280h248L56-792l56-56 736 736-56 56-146-144H160Zm560-154-80-80v-126q0-66-47-113t-113-47q-26 0-50 8t-44 24l-58-58q20-16 43-28t49-18v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v206Zm-276-50Zm36 324q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80Zm33-481Z'
        : 'M370-320h220v-72H480l110-136v-72H370v72h110L370-392v72ZM160-200v-80h80v-280q0-83 50-147.5T420-792v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v280h80v80H160Zm320-300Zm0 420q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80ZM320-280h320v-280q0-66-47-113t-113-47q-66 0-113 47t-47 113v280Z';
      const isBell = pill.icon === 'paused' || pill.icon === 'off';
      pillV = {
        disp: 'inline', x: r1(px), y: r1(icy - 19), w: r1(pw), fill: col, fo: pill.tonal ? 0.24 : 1, tc: fg, tx: r1(px + 15.2 + 23.6 + 9.5), ty: r1(icy + 8), t: pill.t,
        mark: isBell ? noIcon : iconAt(pill.icon, icx, icy, 0, 23.6, fg),
        bell: isBell ? { d: bellD, tf: 'translate(' + r1(icx - 11.8) + ' ' + r1(icy - 11.8) + ') scale(0.0246) translate(0 960)', disp: 'inline' } : { d: 'M0 0', tf: 'translate(0 0)', disp: 'none' },
      };
    }

    // no value: say why, never a figure that could pass for glucose
    let msg = { disp: 'none', dx: 0, dy: 0, discD: 'none', pie: 'M0 0', flag: 'M0 0', pole: 'M0 0', flagD: 'none', x1: 0, y1: 0, a1: 'middle', fs1: 25, c1: pal.ink, t1: '', y2: 0, fs2: 24, w2: 500, c2: pal.soft, fam2: LF, t2: '' };
    let msgBox = [0, 0, 0, 0];
    if (msgKind === 'warm') {
      const almost = c.sensor === 'almost';
      const frac = almost ? 0.96 : clamp(c.warm, 0.02, 0.98);
      const t1 = W.warming, fs1 = 32, w1 = this.tw(t1, fs1), tot = 34 + 12 + w1, mx = -tot / 2;
      const dx = r1(mx + 17), dy = r1(L.vTop + 17), pr = 12.5, ea = frac * 360;
      const ex = r1(dx + pr * Math.sin(ea * Math.PI / 180)), ey = r1(dy - pr * Math.cos(ea * Math.PI / 180));
      const pie = 'M' + dx + ' ' + dy + 'L' + dx + ' ' + r1(dy - pr) + 'A' + pr + ' ' + pr + ' 0 ' + (ea > 180 ? 1 : 0) + ' 1 ' + ex + ' ' + ey + 'Z';
      const ready = clock(new Date(now.getTime() + (1 - frac) * 60 * 60000));
      msg = Object.assign(msg, { disp: 'inline', dx, dy, discD: 'inline', pie, x1: r1(mx + 46), y1: r1(dy + fs1 * 0.668 / 2), a1: 'start', fs1, c1: pal.ink, t1, y2: r1(L.chipC + 8), t2: almost ? W.almost : W.readyAt(ready) });
      msgBox = [mx - 10, L.vTop - 6, tot + 20, L.chipC + 14 - L.vTop];
    } else if (msgKind === 'ended') {
      const t1 = W.sensorEnded, fs1 = 32, w1 = this.tw(t1, fs1), tot = 26 + 12 + w1, mx = -tot / 2, fy = r1(L.vTop + 1);
      let flag = '';
      [[0, 0], [2, 0], [1, 1]].forEach((cell) => { flag += 'M' + r1(mx + 3 + cell[0] * 7.5) + ' ' + r1(fy + cell[1] * 7.5) + 'h7.5v7.5h-7.5Z'; });
      const pole = 'M' + r1(mx + 2) + ' ' + r1(fy - 2) + 'V' + r1(fy + 30) + 'M' + r1(mx + 3) + ' ' + fy + 'h22.5v15h-22.5';
      msg = Object.assign(msg, { disp: 'inline', flag, pole, flagD: 'inline', x1: r1(mx + 38), y1: r1(L.vTop + 16 + fs1 * 0.668 / 2), a1: 'start', fs1, c1: pal.ink, t1 });
      msgBox = [mx - 10, L.vTop - 6, tot + 20, 44];
    } else if (msgKind === 'none') {
      msg = Object.assign(msg, { disp: 'inline', x1: 0, y1: r1(L.vTop + 20), a1: 'middle', fs1: 30, c1: pal.soft, t1: W.noYet });
      const w = this.tw(W.noYet, 30);
      msgBox = [-w / 2 - 10, L.vTop - 8, w + 20, 36];
    } else if (msgKind === 'gap') {
      const fs2 = r1(L.cap2 / F.cap);
      msg = Object.assign(msg, { disp: 'inline', x1: 0, y1: r1(L.vTop + 16.7), a1: 'middle', fs1: 25, c1: pal.soft, t1: W.noSince, y2: r1(L.vTop + 16.7 + 12 + L.cap2), fs2, w2: 600, c2: pal.ink, fam2: F.fam, t2: readTxt });
      msgBox = [-120, L.vTop - 8, 240, 16.7 + 12 + L.cap2 + 16];
    }

    // the level rail: Kovatchev's risk scale, the user's limits as notches
    const A = (v) => this.ang(v);
    const tick = (v, a, b2) => { const p1 = P(a, A(v)), p2 = P(b2, A(v)); return 'M' + p1[0] + ' ' + p1[1] + 'L' + p2[0] + ' ' + p2[1]; };
    const zones = { veryLow: [A(55), 133], low: [A(70), A(55)], high: [A(250), A(180)], veryHigh: [47, A(250)] };
    const z = zones[bk];
    const railOn = c.sensor !== 'none';
    const beadOn = hasValue && railOn;
    const beadA = lo ? 133 : hi ? 47 : A(g);
    const rail = {
      disp: show(railOn), base: arc(182, 47, 133, false),
      band: arc(182, A(180), A(70), false), tight: arc(182, A(140), A(70), false), bandW: aod ? 2 : 6,
      zone: z ? arc(182, z[0], z[1], false) : 'M0 0', zoneCol: pal[ck], zoneDisp: show(beadOn && out && !struck && !aod),
      minor: [50, 60, 80, 90, 100, 110, 120, 130, 150, 160, 170, 200, 220, 240, 280, 350].map((v) => tick(v, 177, 187)).join(''),
      minorDisp: show(!aod),
      mid: [40, 140, 300, 400].map((v) => tick(v, 175, 189)).join(''),
      major: [55, 70, 180, 250].map((v) => tick(v, 171, 192)).join(''),
    };
    let chev = 'M0 0';
    if (lo) { const t = P(182, 139.5), b1 = P(176.5, 135.6), b2 = P(187.5, 135.6); chev = 'M' + t[0] + ' ' + t[1] + 'L' + b1[0] + ' ' + b1[1] + 'L' + b2[0] + ' ' + b2[1] + 'Z'; }
    if (hi) { const t = P(182, 40.5), b1 = P(176.5, 44.4), b2 = P(187.5, 44.4); chev = 'M' + t[0] + ' ' + t[1] + 'L' + b1[0] + ' ' + b1[1] + 'L' + b2[0] + ' ' + b2[1] + 'Z'; }
    const bead = {
      disp: show(beadOn), deg: r1(beadA), col: struck ? pal.grey : beadCol,
      fill: struck ? '#000000' : beadCol, stroke: struck ? pal.grey : 'none', r: aod ? 6 : 7,
      glow: show(!struck && awake), chev, chevDisp: show(beadOn && (lo || hi)),
    };
    const hist = Array.isArray(c.history) ? c.history.slice(-6) : Array.isArray(c.spark) ? c.spark.slice(-7, -1) : (trendOn ? [6, 5, 4, 3, 2, 1].map((k) => g - rate * 5 * k) : []);
    const trailOn = beadOn && !struck && !aod && !lo && !hi;
    const trail = [];
    for (let i = 0; i < 6; i++) {
      const h = hist[hist.length - 6 + i], v = h === null || h === undefined ? NaN : Number(h);
      const ok = trailOn && Number.isFinite(v);
      trail.push({ deg: r1(ok ? A(v) : beadA), r: r1(2.2 + i * 0.45), o: r1(0.1 + i * 0.07), d: show(ok) });
    }

    // the dial: a map texture, or a background that draws the body's state.
    // ext: how extreme (0 at 112, ~0.46 at the range's edges, 1 near 45 or 305) = how much of the screen the colour fills.
    // toward: heading back to the ideal (+) fades it, heading away (-) strengthens it (I, 0.45 to 1.65).
    const kv = this.K(clamp(g, 40, 400));
    const ext = hasValue ? clamp(Math.abs(kv) / 1.9, 0, 1) : 0;
    const side = kv >= 0 ? 1 : -1;
    const toward = current && trendOn ? clamp(-side * rate / 2, -1, 1) * Math.min(1, Math.abs(kv) / 0.3) : 0;
    const I = 1 + 0.65 * Math.max(0, -toward) - 0.55 * Math.max(0, toward);
    const spd = trendOn ? clamp(Math.abs(rate) / 3, 0, 1) : 0;
    const levelY = hasValue ? r1(-182 * Math.cos(beadA * Math.PI / 180)) : 60;
    const idealY = r1(-182 * Math.cos(A(112.5) * Math.PI / 180));
    const dcol = !current ? pal.grey : out ? pal[ck] : (c.rangeColour ? pal[bk] : pal.neutral);
    const dial = { d1: 'M0 0', f1: 'none', s1: 'none', w1: 1, o1: 1, d1D: 'none', d2: 'M0 0', s2: 'none', w2: 1, o2: 1, d2D: 'none', d3: 'M0 0', s3: 'none', w3: 1, o3: 1, d3D: 'none', wy1: 0, wy2: 1, dash: current ? 'none' : '2 6', glowD: 'none', gx: 0, gy: 0, gr: 100, col: '#000000', go: 0, go2: 0, go3: 0, go4: 0 };
    if (awake && c.dial === 'map') Object.assign(dial, { d1: this.mapLines(), s1: pal.texture, w1: 1.2, d1D: 'inline', dash: 'none' });
    if (awake && c.dial === 'topo') {
      // soft, round, never touching; fading with distance from your level, so a high paints the top, a low the bottom
      const spc = 22 - 7 * ext, Rf = current ? 85 + 270 * ext : 240;
      const cxT = 30, cyT = levelY;
      let d = '';
      for (let k = 1; k * spc < Rf; k++) {
        const base = k * spc, ptsT = [];
        for (let i = 0; i < 120; i++) {
          const t = (i / 120) * Math.PI * 2;
          const rr = base * (1 + 0.05 * Math.sin(2 * t + 0.9) + 0.035 * Math.sin(3 * t + 2.1)) + spc * 0.14 * spd * Math.sin(4 * t + k * 0.5);
          ptsT.push(r1(cxT + rr * Math.cos(t)) + ' ' + r1(cyT + rr * Math.sin(t)));
        }
        d += 'M' + ptsT.join('L') + 'Z';
      }
      const peak = current ? clamp((0.36 + 0.4 * ext) * I, 0.1, 0.95) : 0.22;
      const q3 = (x) => r1(x * 1000) / 1000;
      Object.assign(dial, { d1: d || 'M0 0', s1: 'url(#' + ids.cg + ')', w1: r1(1.3 * (0.8 + 0.3 * I) * 10) / 10, o1: 1, d1D: 'inline', gx: cxT, gy: cyT, gr: r1(Rf), col: dcol, go: q3(peak), go2: q3(peak * 0.72), go3: q3(peak * 0.36), go4: q3(peak * 0.11) });
    }
    if (awake && c.dial === 'water') {
      // the water below your level: wavy lines parallel to the waterline, fading downwards; deeper the more extreme
      const yL = hasValue ? levelY : 150;
      const amp = 2 + 7 * spd;
      const wave = (y0, a2) => { let w = ''; for (let x = -196; x <= 196; x += 6) w += (x === -196 ? 'M' : 'L') + x + ' ' + r1(y0 + a2 * Math.sin(x / 14 + 0.8)); return w; };
      let d = '';
      const depth = current ? 45 + 150 * ext : 0;
      const step = clamp(9 - 3.4 * (I - 0.45), 5, 9);
      if (current) for (let k = 1; k * step < depth && yL + k * step < 196; k++) d += wave(yL + k * step, amp * Math.max(0.35, 1 - k * 0.04));
      const peak = current ? clamp((0.36 + 0.32 * ext) * I, 0.12, 0.9) : 0;
      Object.assign(dial, { d1: d || 'M0 0', s1: 'url(#' + ids.wg + ')', w1: 1.3, o1: 1, d1D: show(!!d), wy1: r1(yL), wy2: r1(yL + Math.max(20, depth)), col: dcol, go: r1(peak * 1000) / 1000,
        d2: wave(yL, amp), s2: dcol, w2: 2, o2: current ? 0.75 : 0.32, d2D: 'inline' });
    }
    if (awake && c.dial === 'laser') {
      // near-horizontal lasers, each with its own tilt and place: more and brighter the more extreme, gathered at your level near the bead
      if (!this._lz) {
        let sd = 1337;
        const rnd = () => { sd = (sd * 1664525 + 1013904223) % 4294967296; return sd / 4294967296; };
        this._lz = [];
        for (let i = 0; i < 70; i++) this._lz.push([rnd(), rnd(), rnd(), rnd(), rnd()]);
      }
      // edge to edge (no ends), a gaussian bell around your level: dense and bright near it, thinning out further away
      const n = current ? Math.round(5 + 9 * ext + 14 * ext * ext) : 4;
      const sig = current ? 12 + 60 * ext : 90;
      let dB = '', dM = '';
      for (let i = 0; i < n; i++) {
        const qz = this._lz[i];
        const gz = Math.sqrt(-2 * Math.log(Math.max(1e-6, qz[0]))) * Math.cos(2 * Math.PI * qz[1]);
        const y = levelY + gz * sig;
        const xc = -100 + 200 * qz[2];
        const tn = Math.tan((qz[4] * 2 - 1) * (6 + 8 * spd) * Math.PI / 180);
        const seg = 'M-200 ' + r1(y + (-200 - xc) * tn) + 'L200 ' + r1(y + (200 - xc) * tn);
        if (current && Math.abs(gz) < 0.9) dB += seg; else dM += seg;
      }
      const pk = current ? clamp((0.55 + 0.3 * ext) * I, 0.2, 1) : 0.3;
      const lg = 'url(#' + ids.cg + ')';
      Object.assign(dial, { gx: 30, gy: levelY, gr: r1(200 + 140 * ext), col: dcol, go: 1, go2: 0.9, go3: 0.62, go4: 0.3,
        d1: dB || 'M0 0', s1: lg, w1: 1.7, o1: r1(pk * 100) / 100, d1D: show(!!dB),
        d2: dM || 'M0 0', s2: lg, w2: 1.1, o2: r1(pk * 0.38 * 100) / 100, d2D: show(!!dM),
        d3: dB || 'M0 0', s3: lg, w3: 6, o3: r1(pk * 0.16 * 100) / 100, d3D: show(!!dB) });
    }
    if (awake && c.dial === 'glow' && current) {
      const bp = P(182, beadA);
      const go = clamp((0.06 + 0.34 * ext) * I, 0.03, 0.55);
      Object.assign(dial, { glowD: 'inline', gx: bp[0], gy: bp[1], gr: r1(90 + 320 * ext + 40 * spd), col: dcol, go: r1(go * 1000) / 1000, go2: r1(go * 0.4 * 1000) / 1000 });
    }
    if (awake && c.dial === 'halftone') {
      const sig = 16 + 20 * ext + 175 * ext * ext, rmax = (0.75 + 1.5 * ext) * Math.pow(I, 0.9), yL = levelY;
      let d = '';
      for (let y = -192; y <= 192; y += 12) {
        for (let x = -192; x <= 192; x += 12) {
          const xx = x + ((y / 12) % 2 ? 6 : 0);
          if (xx * xx + y * y > 190 * 190) continue;
          const rr = current ? rmax * Math.exp(-Math.pow((y - yL) / sig, 2)) : 0.6;
          if (rr < 0.3) continue;
          const rq = r1(Math.min(rr, 5.4));
          d += 'M' + r1(xx - rq) + ' ' + y + 'a' + rq + ' ' + rq + ' 0 1 0 ' + r1(2 * rq) + ' 0a' + rq + ' ' + rq + ' 0 1 0 ' + r1(-2 * rq) + ' 0';
        }
      }
      Object.assign(dial, { d1: d || 'M0 0', f1: dcol, s1: 'none', o1: current ? r1(clamp((0.42 + 0.3 * ext) * Math.min(1.25, I), 0.2, 0.9) * 100) / 100 : 0.3, d1D: 'inline', dash: 'none' });
    }

    const dialInfo = { dial: c.dial, ext: r1(ext * 1000) / 1000, toward: r1(toward * 1000) / 1000, I: r1(I * 1000) / 1000, spd: r1(spd * 1000) / 1000, levelY, idealY, colour: dcol };

    // the seconds: a sweep near the rim in the bead's colour (neon in range with green off); where it is against 12 says how long until :00
    const secs = now.getSeconds();
    const radar = {
      disp: show(awake && c.seconds), col: current ? beadCol : pal.grey,
      deg: live ? ((now.getHours() * 60 + now.getMinutes()) * 60 + secs) * 6 : secs * 6,
      tr: live ? 'transform 1s linear' : 'none',
    };

    // the reserve rail: battery; amber under 2 days, red under 1; its label at the rail's top end
    let on = '', off = '';
    const lit = clamp(Math.ceil(c.battery), 0, 10);
    for (let i = 0; i < 10; i++) {
      const a1 = 227 + i * 8.78;
      const d = arc(182, a1, a1 + 6.98, false);
      if (i < lit) on += d; else off += d;
    }
    const battCol = c.battery < 1 ? pal.low : c.battery < 2 ? pal.high : pal.soft;
    const by1 = L.tBase + 6 + battFs * 0.668;
    const batt = {
      disp: show(!aod), on: on || 'M0 0', off: off || 'M0 0', col: battCol, w: c.battery < 2 ? 700 : 600,
      l1: battOne ? battOneT : battL1, l2: battBoth && !battOne ? battPctT : '', l2D: show(battBoth && !battOne),
      y1: r1(by1), y2: r1(by1 + battFs * 0.668 + 9), fs: battFs,
    };

    // the time: centred, every digit in its own fixed cell
    const tCap = L.T, tY = L.tBase, tW = L.tg.tW, t0 = -tW / 2;
    const time = {
      str: tStr, xs: L.tg.txs.map((x) => r1(t0 + x)).join(' '), y: r1(tY), fam: F.fam, fs: r1(L.tg.tFs),
      fill: aod ? 'none' : pal.ink, stroke: aod ? pal.grey : 'none', sw: aod ? 2.2 : 0,
    };

    const date = {
      wd: W.wd[now.getDay()] + NB, day: String(now.getDate()), mo: NB + W.mo[now.getMonth()],
      fill: aod ? pal.dim : pal.soft, dayFill: aod ? pal.soft : pal.ink,
    };

    // fields: the top one under the date, two on the bottom curve
    const fmtN = (n) => String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, c.lang === 'es' ? '.' : ',');
    const amt = (it) => { let a = String(it.a); if (c.lang === 'es') a = a.replace('.', ','); return a + (it.k === 'carbs' ? 'g' : 'u'); };
    const lastItems = c.last && Array.isArray(c.last.items) ? c.last.items : [{ k: c.lastKind, a: c.lastAmount }];
    const lastAgo = c.last && c.last.ago !== undefined ? Number(c.last.ago) : c.lastAgo;
    const fieldRuns = (key) => {
      const t = (str, col, w) => ({ t: 's', s: String(str), col: col || pal.ink, wt: w || 500 });
      const i = (k, col) => ({ t: 'i', key: k, col: col || pal.soft });
      const gp = (w) => ({ t: 'g', w });
      switch (key) {
        case 'steps': return [i('steps'), gp(5), t(fmtN(c.steps))];
        case 'weather': return [i('sun'), gp(6), t('14°')];
        case 'sunset': return [i('sunset'), gp(6), t('17:42')];
        case 'bodyBattery': return [i('bolt'), gp(4), t('62')];
        case 'altitude': return [i('mountain'), gp(6), t(fmtN(1240) + NB + 'm')];
        case 'heart': return [i('heart'), gp(5), t('64')];
        case 'notifications': return [i('bell'), gp(5), t('3')];
        case 'tir': return [i('ring'), gp(5), t(c.tir === null ? '--' : Math.round(c.tir) + '%')];
        case 'tz2': return [i('globe'), gp(6), t('NYC' + NB + '4:09')];
        case 'floors': return [i('stairs'), gp(5), t('8')];
        case 'sensor': return [i('sensor'), gp(5), t(dur(c.sensorLeft * 60))];
        case 'noPhone': return [i('phoneOff'), gp(5), t(W.noPhone)];
        case 'treatment': {
          const r = [];
          if (!lastItems.length) return r;
          lastItems.forEach((it, n) => {
            const kc = pal[it.k] || pal.rapid;
            if (n) r.push(gp(8));
            r.push(i(it.k, kc), gp(4), t(amt(it), kc, 600));
          });
          r.push(gp(9), t(dur(lastAgo), pal.soft));
          return r;
        }
        default: return [];
      }
    };
    const measure = (runs, fs, iw) => runs.forEach((rn) => { rn.w = rn.t === 'i' ? iw : rn.t === 's' ? this.tw(rn.s, fs) : rn.w; });
    const noText = { x: 0, off: 0, s: '', col: '#000000', w: 500, disp: 'none' };

    const TF = L.TF, tfIcon = Math.round(TF * 0.83);
    const topRuns = fieldRuns(c.topField);
    measure(topRuns, TF, tfIcon);
    const topW = topRuns.reduce((a, rn) => a + rn.w, 0);
    let xx = -topW / 2;
    const tfI = [], tfT = [];
    topRuns.forEach((rn) => {
      if (rn.t === 'i') tfI.push(iconAt(rn.key, xx + rn.w / 2, L.tfBase - TF * 0.334, 0, tfIcon, rn.col));
      else if (rn.t === 's') tfT.push({ x: r1(xx), s: rn.s, col: rn.col, w: rn.wt, disp: 'inline' });
      xx += rn.w;
    });
    while (tfI.length < 2) tfI.push(noIcon);
    while (tfT.length < 3) tfT.push(noText);
    if (!topOn) { tfI.forEach((x, n) => { tfI[n] = noIcon; }); tfT.forEach((x, n) => { tfT[n] = noText; }); }

    let keys = Array.isArray(c.fields) ? c.fields.slice(0, 2) : ['steps', 'treatment'];
    if (c.sensor === 'ending') keys = ['sensor', keys[1] || 'treatment'];
    if (!c.phone) keys = ['noPhone', keys[1] || 'treatment'];
    const BR = 172, BA0 = 232, BA1 = 128;
    const BL = BR * (BA0 - BA1) * Math.PI / 180;
    const prio = (k) => (k === 'sensor' || k === 'noPhone' ? 3 : k === 'treatment' ? 2 : 1);
    const build = (ks) => { let r = []; ks.forEach((k) => { const f = fieldRuns(k); if (!f.length) return; if (r.length) r.push({ t: 'g', w: 26 }); r = r.concat(f); }); measure(r, 24, 20); return r; };
    let runs = build(keys);
    while (keys.length > 1 && runs.reduce((a, rn) => a + rn.w, 0) > BL - 8) {
      const drop = keys.reduce((m, k, n) => (prio(k) < prio(keys[m]) ? n : m), 0);
      keys = keys.filter((k, n) => n !== drop);
      runs = build(keys);
    }
    let bs = Math.max(0, (BL - runs.reduce((a, rn) => a + rn.w, 0)) / 2);
    const bI = [], bT = [];
    const bHalo = [];
    runs.forEach((rn) => {
      if (rn.t !== 'g') bHalo.push([rn.t, bs, rn.w]);
      if (rn.t === 'i') { const an = BA0 - (bs + rn.w / 2) / BR * 180 / Math.PI; const p = P(BR - 8, an); bI.push(iconAt(rn.key, p[0], p[1], an - 180, 20, rn.col)); }
      else if (rn.t === 's') bT.push({ off: r1(bs), s: rn.s, col: rn.col, w: rn.wt, disp: 'inline' });
      bs += rn.w;
    });
    while (bI.length < 5) bI.push(noIcon);
    while (bT.length < 6) bT.push(noText);
    const b = { disp: show(!aod), i0: bI[0], i1: bI[1], i2: bI[2], i3: bI[3], i4: bI[4], t0: bT[0], t1: bT[1], t2: bT[2], t3: bT[3], t4: bT[4], t5: bT[5] };

    // the halo: where the dial stops short of the numbers (box, outline that follows the glyphs, or none)
    const hasChipRow = timeOn || pillOn;
    const haloMode = c.halo;
    const valBox = valueOn ? [x0 - 12, vY - vCap - 12, total + 24, vCap + 24] : (msgKind ? msgBox : [0, 0, 0, 0]);
    const halo = {
      boxD: show(haloMode === 'box'), outD: show(haloMode === 'outline'),
      tx: r1(t0 - 10), ty: r1(tY - tCap - 10), tw: r1(tW + 20), th: tCap + 20,
      vx: r1(valBox[0]), vy: r1(valBox[1]), vw: r1(valBox[2]), vh: r1(valBox[3]),
      cx: r1(rx0 - 8), cy: r1(L.chipC - 23), cw: hasChipRow ? r1(ageW + gapA + pw + 16) : 0, ch: hasChipRow ? 46 : 0,
      fx: r1(-topW / 2 - 8), fy: r1(L.tfBase - TF * 0.668 - 8), fw: topOn ? r1(topW + 16) : 0, fh: topOn ? r1(TF * 0.668 + 16) : 0,
      sx: r1(sx - 8), sy: r1(sy - 8), sw: valueOn && ts === 'spark' ? SW + 16 : 0, sh: r1(SH + 16),
      mx: r1(msgBox[0]), my: r1(msgBox[1]), mw: msgKind ? r1(msgBox[2]) : 0, mh: r1(msgBox[3]),
    };

    // the outline's small texts (date, top field, bottom fields, battery label): their line box (Atkinson's ascender 0.73 em
    // over the baseline, descender 0.2 em under it) grown 5.5 px, as a band along the arc for the date and the bottom
    // fields (at 24 px with 5.5 px around each glyph, the grown glyphs already merge into that box)
    const rr = (x, y, w, h, r) => (w > 0 && h > 0 ? 'M' + r1(x + r) + ' ' + r1(y) + 'H' + r1(x + w - r) + 'A' + r + ' ' + r + ' 0 0 1 ' + r1(x + w) + ' ' + r1(y + r)
      + 'V' + r1(y + h - r) + 'A' + r + ' ' + r + ' 0 0 1 ' + r1(x + w - r) + ' ' + r1(y + h) + 'H' + r1(x + r) + 'A' + r + ' ' + r + ' 0 0 1 ' + r1(x) + ' ' + r1(y + h - r)
      + 'V' + r1(y + r) + 'A' + r + ' ' + r + ' 0 0 1 ' + r1(x + r) + ' ' + r1(y) + 'Z' : '');
    const band = (r, w, a1, a2) => {
      const o = arc(r + w / 2, a1, a2, false), i = P(r - w / 2, a2), i2 = P(r - w / 2, a1);
      return o + 'L' + i[0] + ' ' + i[1] + 'A' + (r - w / 2) + ' ' + (r - w / 2) + ' 0 ' + (a2 - a1 > 180 ? 1 : 0) + ' 0 ' + i2[0] + ' ' + i2[1] + 'Z';
    };
    let small = '';
    if (topOn && topRuns.length) {
      const up = Math.max(TF * 0.73, tfIcon / 2 + TF * 0.334);
      small += rr(-topW / 2 - 5.5, L.tfBase - up - 5.5, topW + 11, up + TF * 0.2 + 11, 5.5);
    }
    if (!aod) {
      small += rr(-171.5, by1 - battFs * 0.73 - 5.5, this.tw(batt.l1, battFs) + 11, battFs * 0.93 + 11, 5.5);
      if (batt.l2) small += rr(-171.5, by1 + battFs * 0.668 + 9 - battFs * 0.73 - 5.5, this.tw(batt.l2, battFs) + 11, battFs * 0.93 + 11, 5.5);
      const dw = Array.from(date.wd + date.day + date.mo).length * 2 + this.tw(date.wd + date.day + date.mo, 26);
      const da = (dw / 2 + 5.5) / 160 * 180 / Math.PI;
      small += band(169.5, 30, -da, da);
      bHalo.forEach(([t, s0, w]) => {
        const a1 = BA0 - (s0 + w + 5.5) / BR * 180 / Math.PI, a2 = BA0 - (s0 - 5.5) / BR * 180 / Math.PI;
        small += t === 'i' ? band(164, 29.2, a1, a2) : band(BR - 24 * 0.265, 24 * 0.93 + 11, a1, a2);
      });
    }

    const metals = {
      carbon: { a: '#3D3F44', b: '#232528', c: '#4A4D52', d: '#26282B', edge: '#6B6E74', screw: '#7D8086' },
      bronze: { a: '#7A4F35', b: '#4B2F20', c: '#9A6747', d: '#563624', edge: '#B98260', screw: '#C99272' },
    };

    const trendWord = !trendOn ? 'no trend' : Math.abs(rate) < 1 ? 'steady' : (rate > 0 ? 'rising' : 'falling') + (Math.abs(rate) >= 3 ? ' fast' : Math.abs(rate) < 2 ? ' slowly' : '');
    const spoken = valueOn ? vStr + ((lo || hi) ? ', ' : c.unit === 'mmol' ? ' mmol/L, ' : ' mg/dL, ') + trendWord + (struck ? ', old reading from ' + readTxt : timeOn ? ', from ' + readTxt : '') + (pillOn ? ', ' + pill.t : '') : (msg.t1 ? msg.t1 + (msg.t2 ? ' ' + msg.t2 : '') : 'glucose hidden' + (timeOn ? ', ' + (struck ? 'old reading from ' : 'from ') + readTxt : ''));

    // the runtime wraps a plain-string hole in an HTML <span>, which SVG <text> doesn't draw; an array renders as bare text
    const wrap = (o, keysW) => keysW.forEach((k) => { o[k] = [String(o[k] === undefined || o[k] === null ? '' : o[k])]; });
    wrap(date, ['wd', 'day', 'mo']);
    wrap(time, ['str']);
    wrap(batt, ['l1', 'l2']);
    wrap(val, ['str']);
    wrap(chip, ['t']);
    wrap(pillV, ['t']);
    wrap(msg, ['t1', 't2']);
    const tf = { y: r1(L.tfBase), fs: TF, i0: tfI[0], i1: tfI[1], t0: Object.assign({}, tfT[0]), t1: Object.assign({}, tfT[1]), t2: Object.assign({}, tfT[2]) };
    wrap(tf.t0, ['s']); wrap(tf.t1, ['s']); wrap(tf.t2, ['s']);
    ['t0', 't1', 't2', 't3', 't4', 't5'].forEach((k) => { b[k] = Object.assign({}, b[k]); wrap(b[k], ['s']); });

    return {
      ids,
      v: {
        size: c.size, viewBox: c.bezel ? '-292 -292 584 584' : '-195 -195 390 390', bezelDisp: show(c.bezel),
        filter: c.filter === 'redshift' ? 'url(#' + ids.red + ')' : c.filter === 'grey' ? 'url(#' + ids.grey + ')' : 'none',
        labelFam: LF, dash: [NB + '-' + NB], label: 'Sukrara Aura watch face. ' + tStr + '. ' + spoken + '.',
      },
      metal: metals[c.metal] || metals.carbon,
      arcs: { top: arc(160, 300, 60, false), bottom: arc(BR, BA0, BA1, true), halo: small || 'M0 0' },
      info: dialInfo, layout: { T: L.T, V: L.V, TF: L.TF, roomy: L.roomy, chipKind, msgKind, tfBase: r1(L.tfBase), tTop: r1(L.tTop), tBase: r1(L.tBase), vTop: r1(L.vTop), vBase: r1(L.vBase), chipC: r1(L.chipC) },
      pal, dial, radar, halo, date, tf, time, batt, rail, bead,
      t0: trail[0], t1: trail[1], t2: trail[2], t3: trail[3], t4: trail[4], t5: trail[5],
      val, sp, chip, pill: pillV, msg, b,
    };
  }
}

// {{dotted.path}} holes in the markup, filled from layout()'s values (arrays join, missing → '').
function fill(markup, vals) {
  return markup.replace(/\{\{\s*([\w.$]+)\s*\}\}/g, (m, path) => {
    let v = vals;
    for (const k of path.split('.')) v = v === undefined || v === null ? undefined : v[k];
    if (Array.isArray(v)) return v.join('');
    return v === undefined || v === null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  });
}

/** All the face's values for a configuration (see SPEC.md §4 for every field). */
function layout(cfg) { return new AuraFace(cfg).renderVals(); }

/** The face as SVG (cfg.bezel = true draws the Descent's bezel around it; false: the 390 px screen only, 1:1). */
function svg(cfg) { return fill(MARKUP, layout(cfg)); }

const MARKUP = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{{v.size}}\" height=\"{{v.size}}\" viewBox=\"{{v.viewBox}}\" role=\"img\" aria-label=\"{{v.label}}\" style=\"display: block; overflow: visible\">\n<defs>\n<clipPath id=\"{{ids.clip}}\"><circle cx=\"0\" cy=\"0\" r=\"195\"></circle></clipPath>\n<filter id=\"{{ids.red}}\" color-interpolation-filters=\"sRGB\"><feColorMatrix type=\"matrix\" values=\"0.36 0.56 0.12 0 0 0.03 0.05 0.01 0 0 0 0 0 0 0 0 0 0 1 0\"></feColorMatrix></filter>\n<filter id=\"{{ids.grey}}\" color-interpolation-filters=\"sRGB\"><feColorMatrix type=\"saturate\" values=\"0\"></feColorMatrix></filter>\n<radialGradient id=\"{{ids.metal}}\" cx=\"0\" cy=\"0\" r=\"268\" gradientUnits=\"userSpaceOnUse\">\n<stop offset=\"0.86\" stop-color=\"{{metal.a}}\"></stop>\n<stop offset=\"0.905\" stop-color=\"{{metal.b}}\"></stop>\n<stop offset=\"0.955\" stop-color=\"{{metal.c}}\"></stop>\n<stop offset=\"1\" stop-color=\"{{metal.d}}\"></stop>\n</radialGradient>\n<linearGradient id=\"{{ids.sheen}}\" x1=\"0.1\" y1=\"0\" x2=\"0.9\" y2=\"1\">\n<stop offset=\"0\" stop-color=\"#FFFFFF\" stop-opacity=\"0.24\"></stop>\n<stop offset=\"0.42\" stop-color=\"#FFFFFF\" stop-opacity=\"0\"></stop>\n<stop offset=\"0.8\" stop-color=\"#FFFFFF\" stop-opacity=\"0\"></stop>\n<stop offset=\"1\" stop-color=\"#FFFFFF\" stop-opacity=\"0.1\"></stop>\n</linearGradient>\n<linearGradient id=\"{{ids.btn}}\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n<stop offset=\"0\" stop-color=\"#5E6065\"></stop>\n<stop offset=\"0.5\" stop-color=\"#C9CBD0\"></stop>\n<stop offset=\"1\" stop-color=\"#6E7075\"></stop>\n</linearGradient>\n<radialGradient id=\"{{ids.glow}}\" cx=\"{{dial.gx}}\" cy=\"{{dial.gy}}\" r=\"{{dial.gr}}\" gradientUnits=\"userSpaceOnUse\">\n<stop offset=\"0\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go}}\"></stop>\n<stop offset=\"0.5\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go2}}\"></stop>\n<stop offset=\"1\" stop-color=\"{{dial.col}}\" stop-opacity=\"0\"></stop>\n</radialGradient>\n<radialGradient id=\"{{ids.cg}}\" cx=\"{{dial.gx}}\" cy=\"{{dial.gy}}\" r=\"{{dial.gr}}\" gradientUnits=\"userSpaceOnUse\">\n<stop offset=\"0\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go}}\"></stop>\n<stop offset=\"0.22\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go2}}\"></stop>\n<stop offset=\"0.48\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go3}}\"></stop>\n<stop offset=\"0.76\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go4}}\"></stop>\n<stop offset=\"1\" stop-color=\"{{dial.col}}\" stop-opacity=\"0\"></stop>\n</radialGradient>\n<linearGradient id=\"{{ids.wg}}\" x1=\"0\" y1=\"{{dial.wy1}}\" x2=\"0\" y2=\"{{dial.wy2}}\" gradientUnits=\"userSpaceOnUse\">\n<stop offset=\"0\" stop-color=\"{{dial.col}}\" stop-opacity=\"{{dial.go}}\"></stop>\n<stop offset=\"1\" stop-color=\"{{dial.col}}\" stop-opacity=\"0\"></stop>\n</linearGradient>\n<radialGradient id=\"{{ids.rg}}\" cx=\"0\" cy=\"0\" r=\"196\" gradientUnits=\"userSpaceOnUse\">\n<stop offset=\"0\" stop-color=\"#000000\"></stop>\n<stop offset=\"0.7\" stop-color=\"#000000\"></stop>\n<stop offset=\"0.985\" stop-color=\"#FFFFFF\"></stop>\n<stop offset=\"1\" stop-color=\"#FFFFFF\"></stop>\n</radialGradient>\n<mask id=\"{{ids.rm}}\" maskUnits=\"userSpaceOnUse\" x=\"-200\" y=\"-200\" width=\"400\" height=\"400\"><circle cx=\"0\" cy=\"0\" r=\"197\" fill=\"url(#{{ids.rg}})\"></circle></mask>\n<mask id=\"{{ids.halo}}\" maskUnits=\"userSpaceOnUse\" x=\"-200\" y=\"-200\" width=\"400\" height=\"400\">\n<rect x=\"-200\" y=\"-200\" width=\"400\" height=\"400\" fill=\"#FFFFFF\"></rect>\n<g display=\"{{halo.boxD}}\" fill=\"#000000\">\n<rect x=\"{{halo.tx}}\" y=\"{{halo.ty}}\" width=\"{{halo.tw}}\" height=\"{{halo.th}}\" rx=\"16\"></rect>\n<rect x=\"{{halo.vx}}\" y=\"{{halo.vy}}\" width=\"{{halo.vw}}\" height=\"{{halo.vh}}\" rx=\"16\"></rect>\n<rect x=\"{{halo.cx}}\" y=\"{{halo.cy}}\" width=\"{{halo.cw}}\" height=\"{{halo.ch}}\" rx=\"22\"></rect>\n<rect x=\"{{halo.fx}}\" y=\"{{halo.fy}}\" width=\"{{halo.fw}}\" height=\"{{halo.fh}}\" rx=\"12\"></rect>\n</g>\n<g display=\"{{halo.outD}}\" fill=\"#000000\" stroke=\"#000000\" stroke-width=\"13\" stroke-linejoin=\"round\" stroke-linecap=\"round\">\n<text x=\"{{time.xs}}\" y=\"{{time.y}}\" text-anchor=\"middle\" font-family=\"{{time.fam}}\" font-size=\"{{time.fs}}\" font-weight=\"600\">{{time.str}}</text>\n<text x=\"{{val.xs}}\" y=\"{{val.y}}\" text-anchor=\"middle\" font-family=\"{{val.fam}}\" font-size=\"{{val.fs}}\" font-weight=\"600\" display=\"{{val.disp}}\">{{val.str}}</text>\n<path d=\"{{val.arrow}}\" style=\"transform: {{val.tf}}; transform-origin: 0px 0px\" display=\"{{val.arrowD}}\"></path>\n<rect x=\"{{halo.sx}}\" y=\"{{halo.sy}}\" width=\"{{halo.sw}}\" height=\"{{halo.sh}}\" rx=\"8\" stroke=\"none\"></rect>\n<rect x=\"{{halo.cx}}\" y=\"{{halo.cy}}\" width=\"{{halo.cw}}\" height=\"{{halo.ch}}\" rx=\"22\" stroke=\"none\"></rect>\n<path d=\"{{arcs.halo}}\" stroke=\"none\"></path>\n<rect x=\"{{halo.mx}}\" y=\"{{halo.my}}\" width=\"{{halo.mw}}\" height=\"{{halo.mh}}\" rx=\"14\" stroke=\"none\"></rect>\n</g>\n</mask>\n<path id=\"{{ids.top}}\" d=\"{{arcs.top}}\"></path>\n<path id=\"{{ids.bottom}}\" d=\"{{arcs.bottom}}\"></path>\n<path id=\"{{ids.nofly}}\" d=\"M33.5 -211.4A214 214 0 0 1 181.5 -113.4\"></path>\n</defs>\n\n<g display=\"{{v.bezelDisp}}\">\n<g fill=\"url(#{{ids.btn}})\">\n<rect x=\"-14\" y=\"-288\" width=\"28\" height=\"22\" rx=\"5\" transform=\"rotate(60)\"></rect>\n<rect x=\"-14\" y=\"-288\" width=\"28\" height=\"22\" rx=\"5\" transform=\"rotate(120)\"></rect>\n<rect x=\"-14\" y=\"-288\" width=\"28\" height=\"22\" rx=\"5\" transform=\"rotate(240)\"></rect>\n<rect x=\"-14\" y=\"-288\" width=\"28\" height=\"22\" rx=\"5\" transform=\"rotate(270)\"></rect>\n<rect x=\"-14\" y=\"-288\" width=\"28\" height=\"22\" rx=\"5\" transform=\"rotate(300)\"></rect>\n</g>\n<circle cx=\"0\" cy=\"0\" r=\"271\" fill=\"#141518\"></circle>\n<circle cx=\"0\" cy=\"0\" r=\"252.5\" fill=\"none\" stroke=\"url(#{{ids.metal}})\" stroke-width=\"31\"></circle>\n<circle cx=\"0\" cy=\"0\" r=\"252.5\" fill=\"none\" stroke=\"url(#{{ids.sheen}})\" stroke-width=\"31\"></circle>\n<circle cx=\"0\" cy=\"0\" r=\"267.5\" fill=\"none\" stroke=\"{{metal.edge}}\" stroke-width=\"1\"></circle>\n<path d=\"M-2 -239V-266M2 -239V-266M-2 239V266M2 239V266\" stroke=\"{{metal.edge}}\" stroke-width=\"1.6\"></path>\n<g fill=\"#1E1F22\" stroke=\"{{metal.screw}}\" stroke-width=\"1.3\">\n<circle cx=\"178.2\" cy=\"-178.2\" r=\"6.5\"></circle>\n<circle cx=\"178.2\" cy=\"178.2\" r=\"6.5\"></circle>\n<circle cx=\"-178.2\" cy=\"178.2\" r=\"6.5\"></circle>\n<circle cx=\"-178.2\" cy=\"-178.2\" r=\"6.5\"></circle>\n</g>\n<path d=\"M175.9 -178.2h4.6M-180.5 -178.2h4.6M175.9 178.2h4.6M-180.5 178.2h4.6\" stroke=\"#0B0B0C\" stroke-width=\"1.6\"></path>\n<circle cx=\"0\" cy=\"0\" r=\"216\" fill=\"none\" stroke=\"#08080A\" stroke-width=\"42\"></circle>\n<g font-family=\"'Barlow Semi Condensed', 'Arial Narrow', sans-serif\" font-size=\"17\" font-weight=\"600\" fill=\"#E2E2E5\" text-anchor=\"middle\" dominant-baseline=\"central\">\n<text transform=\"translate(0 -214) rotate(0)\">24</text>\n<text transform=\"translate(185.3 -107) rotate(60)\">04</text>\n<text transform=\"translate(214 0) rotate(90)\">06</text>\n<text transform=\"translate(185.3 107) rotate(-60)\">08</text>\n<text transform=\"translate(107 185.3) rotate(-30)\">10</text>\n<text transform=\"translate(-107 185.3) rotate(30)\">14</text>\n<text transform=\"translate(-185.3 107) rotate(60)\">16</text>\n<text transform=\"translate(-214 0) rotate(-90)\">18</text>\n<text transform=\"translate(-185.3 -107) rotate(-60)\">20</text>\n<text transform=\"translate(-107 -185.3) rotate(-30)\">22</text>\n<text x=\"0\" y=\"214\" letter-spacing=\"1.6\" font-size=\"16\">GARMIN</text>\n</g>\n<path d=\"M204.9 -55.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M204.9 55.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M149.5 151.3a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M53.6 206.7a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-57.2 206.7a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-153.1 151.3a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-208.5 55.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-208.5 -55.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-153.1 -151.3a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0M-57.2 -206.7a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0\" fill=\"#8A8A8F\"></path>\n<text font-family=\"'Barlow Semi Condensed', 'Arial Narrow', sans-serif\" font-size=\"14\" font-weight=\"600\" letter-spacing=\"0.5\" dominant-baseline=\"central\"><textPath href=\"#{{ids.nofly}}\"><tspan fill=\"#E8352F\">NO-FLY TIME</tspan><tspan fill=\"#CFCFD2\">{{v.dash}}</tspan><tspan fill=\"#2BB8DA\">ALT TZ</tspan></textPath></text>\n</g>\n\n<g clip-path=\"url(#{{ids.clip}})\" filter=\"{{v.filter}}\" font-family=\"{{v.labelFam}}\">\n<circle cx=\"0\" cy=\"0\" r=\"195\" fill=\"#000000\"></circle>\n\n<circle cx=\"0\" cy=\"0\" r=\"195\" fill=\"url(#{{ids.glow}})\" mask=\"url(#{{ids.halo}})\" display=\"{{dial.glowD}}\"></circle>\n<path d=\"{{dial.d3}}\" mask=\"url(#{{ids.halo}})\" fill=\"none\" stroke=\"{{dial.s3}}\" stroke-width=\"{{dial.w3}}\" stroke-linecap=\"round\" opacity=\"{{dial.o3}}\" display=\"{{dial.d3D}}\"></path>\n<path d=\"{{dial.d1}}\" mask=\"url(#{{ids.halo}})\" fill=\"{{dial.f1}}\" stroke=\"{{dial.s1}}\" stroke-width=\"{{dial.w1}}\" stroke-dasharray=\"{{dial.dash}}\" stroke-linejoin=\"round\" stroke-linecap=\"round\" opacity=\"{{dial.o1}}\" display=\"{{dial.d1D}}\"></path>\n<path d=\"{{dial.d2}}\" mask=\"url(#{{ids.halo}})\" fill=\"none\" stroke=\"{{dial.s2}}\" stroke-width=\"{{dial.w2}}\" stroke-dasharray=\"{{dial.dash}}\" stroke-linecap=\"round\" opacity=\"{{dial.o2}}\" display=\"{{dial.d2D}}\"></path>\n\n<g mask=\"url(#{{ids.rm}})\" display=\"{{radar.disp}}\">\n<g fill=\"{{radar.col}}\" style=\"transform: rotate({{radar.deg}}deg); transform-origin: 0px 0px; transition: {{radar.tr}}\">\n<path d=\"M0 0L-189.3 -50.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0001\"></path>\n<path d=\"M0 0L-186.4 -60.6A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0005\"></path>\n<path d=\"M0 0L-183.0 -70.2A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0009\"></path>\n<path d=\"M0 0L-179.1 -79.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0014\"></path>\n<path d=\"M0 0L-174.6 -89.0A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0019\"></path>\n<path d=\"M0 0L-169.7 -98.0A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0024\"></path>\n<path d=\"M0 0L-164.4 -106.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0030\"></path>\n<path d=\"M0 0L-158.6 -115.2A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0036\"></path>\n<path d=\"M0 0L-152.3 -123.3A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0042\"></path>\n<path d=\"M0 0L-145.7 -131.1A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0048\"></path>\n<path d=\"M0 0L-138.6 -138.6A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0054\"></path>\n<path d=\"M0 0L-131.1 -145.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0061\"></path>\n<path d=\"M0 0L-123.3 -152.3A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0067\"></path>\n<path d=\"M0 0L-115.2 -158.6A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0074\"></path>\n<path d=\"M0 0L-106.7 -164.4A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0082\"></path>\n<path d=\"M0 0L-98.0 -169.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0089\"></path>\n<path d=\"M0 0L-89.0 -174.6A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0097\"></path>\n<path d=\"M0 0L-79.7 -179.1A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0105\"></path>\n<path d=\"M0 0L-70.2 -183.0A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0114\"></path>\n<path d=\"M0 0L-60.6 -186.4A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0122\"></path>\n<path d=\"M0 0L-50.7 -189.3A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0132\"></path>\n<path d=\"M0 0L-40.8 -191.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0141\"></path>\n<path d=\"M0 0L-30.7 -193.6A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0151\"></path>\n<path d=\"M0 0L-20.5 -194.9A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0162\"></path>\n<path d=\"M0 0L-10.3 -195.7A196 196 0 0 1 0.0 -196.0Z\" opacity=\"0.0173\"></path>\n<path d=\"M0 -140V-196\" fill=\"none\" stroke=\"{{radar.col}}\" stroke-width=\"1.6\" opacity=\"0.5\"></path>\n</g>\n</g>\n\n<text font-size=\"26\" font-weight=\"500\" letter-spacing=\"2\" fill=\"{{date.fill}}\" text-anchor=\"middle\"><textPath href=\"#{{ids.top}}\" startOffset=\"50%\"><tspan>{{date.wd}}</tspan><tspan fill=\"{{date.dayFill}}\" font-weight=\"700\">{{date.day}}</tspan><tspan>{{date.mo}}</tspan></textPath></text>\n\n<path d=\"{{tf.i0.d}}\" transform=\"{{tf.i0.tf}}\" fill=\"{{tf.i0.fill}}\" stroke=\"{{tf.i0.stroke}}\" stroke-width=\"{{tf.i0.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{tf.i0.disp}}\"></path>\n<path d=\"{{tf.i1.d}}\" transform=\"{{tf.i1.tf}}\" fill=\"{{tf.i1.fill}}\" stroke=\"{{tf.i1.stroke}}\" stroke-width=\"{{tf.i1.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{tf.i1.disp}}\"></path>\n<text x=\"{{tf.t0.x}}\" y=\"{{tf.y}}\" font-size=\"{{tf.fs}}\" font-weight=\"{{tf.t0.w}}\" fill=\"{{tf.t0.col}}\" display=\"{{tf.t0.disp}}\">{{tf.t0.s}}</text>\n<text x=\"{{tf.t1.x}}\" y=\"{{tf.y}}\" font-size=\"{{tf.fs}}\" font-weight=\"{{tf.t1.w}}\" fill=\"{{tf.t1.col}}\" display=\"{{tf.t1.disp}}\">{{tf.t1.s}}</text>\n<text x=\"{{tf.t2.x}}\" y=\"{{tf.y}}\" font-size=\"{{tf.fs}}\" font-weight=\"{{tf.t2.w}}\" fill=\"{{tf.t2.col}}\" display=\"{{tf.t2.disp}}\">{{tf.t2.s}}</text>\n\n<text x=\"{{time.xs}}\" y=\"{{time.y}}\" text-anchor=\"middle\" font-family=\"{{time.fam}}\" font-size=\"{{time.fs}}\" font-weight=\"600\" fill=\"{{time.fill}}\" stroke=\"{{time.stroke}}\" stroke-width=\"{{time.sw}}\">{{time.str}}</text>\n\n<g display=\"{{batt.disp}}\">\n<path d=\"{{batt.off}}\" fill=\"none\" stroke=\"{{pal.grid}}\" stroke-width=\"6\"></path>\n<path d=\"{{batt.on}}\" fill=\"none\" stroke=\"{{batt.col}}\" stroke-width=\"6\"></path>\n<text x=\"-166\" y=\"{{batt.y1}}\" font-size=\"{{batt.fs}}\" font-weight=\"{{batt.w}}\" fill=\"{{batt.col}}\">{{batt.l1}}</text>\n<text x=\"-166\" y=\"{{batt.y2}}\" font-size=\"{{batt.fs}}\" font-weight=\"{{batt.w}}\" fill=\"{{batt.col}}\" display=\"{{batt.l2D}}\">{{batt.l2}}</text>\n</g>\n\n<g display=\"{{rail.disp}}\">\n<path d=\"{{rail.base}}\" fill=\"none\" stroke=\"{{pal.faint}}\" stroke-width=\"1.5\"></path>\n<path d=\"{{rail.band}}\" fill=\"none\" stroke=\"{{pal.band}}\" stroke-width=\"{{rail.bandW}}\"></path>\n<path d=\"{{rail.tight}}\" fill=\"none\" stroke=\"{{pal.band2}}\" stroke-width=\"{{rail.bandW}}\"></path>\n<path d=\"{{rail.zone}}\" fill=\"none\" stroke=\"{{rail.zoneCol}}\" stroke-width=\"3\" opacity=\"0.85\" display=\"{{rail.zoneDisp}}\"></path>\n<path d=\"{{rail.minor}}\" stroke=\"{{pal.faint}}\" stroke-width=\"1.4\" display=\"{{rail.minorDisp}}\"></path>\n<path d=\"{{rail.mid}}\" stroke=\"{{pal.dim}}\" stroke-width=\"1.6\"></path>\n<path d=\"{{rail.major}}\" stroke=\"{{pal.soft}}\" stroke-width=\"2\"></path>\n<g style=\"transform: rotate({{t0.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t0.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t0.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t0.o}}\"></circle></g>\n<g style=\"transform: rotate({{t1.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t1.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t1.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t1.o}}\"></circle></g>\n<g style=\"transform: rotate({{t2.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t2.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t2.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t2.o}}\"></circle></g>\n<g style=\"transform: rotate({{t3.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t3.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t3.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t3.o}}\"></circle></g>\n<g style=\"transform: rotate({{t4.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t4.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t4.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t4.o}}\"></circle></g>\n<g style=\"transform: rotate({{t5.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{t5.d}}\"><circle cx=\"0\" cy=\"-182\" r=\"{{t5.r}}\" fill=\"{{bead.col}}\" opacity=\"{{t5.o}}\"></circle></g>\n<g style=\"transform: rotate({{bead.deg}}deg); transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{bead.disp}}\">\n<circle cx=\"0\" cy=\"-182\" r=\"13\" fill=\"{{bead.col}}\" opacity=\"0.2\" display=\"{{bead.glow}}\"></circle>\n<circle cx=\"0\" cy=\"-182\" r=\"{{bead.r}}\" fill=\"{{bead.fill}}\" stroke=\"{{bead.stroke}}\" stroke-width=\"2.4\"></circle>\n</g>\n<path d=\"{{bead.chev}}\" fill=\"{{bead.col}}\" display=\"{{bead.chevDisp}}\"></path>\n</g>\n\n<g display=\"{{val.disp}}\">\n<rect x=\"{{val.bx}}\" y=\"{{val.by}}\" width=\"{{val.bw}}\" height=\"{{val.bh}}\" rx=\"12\" fill=\"{{pal.veryLow}}\" display=\"{{val.blk}}\"></rect>\n<text x=\"{{val.xs}}\" y=\"{{val.y}}\" text-anchor=\"middle\" font-family=\"{{val.fam}}\" font-size=\"{{val.fs}}\" font-weight=\"600\" fill=\"{{val.fill}}\">{{val.str}}</text>\n<line x1=\"{{val.sx1}}\" y1=\"{{val.sy}}\" x2=\"{{val.sx2}}\" y2=\"{{val.sy}}\" stroke=\"{{val.fill}}\" stroke-width=\"{{val.sw}}\" stroke-linecap=\"round\" display=\"{{val.strike}}\"></line>\n<path d=\"{{val.arrow}}\" fill=\"{{val.fill}}\" style=\"transform: {{val.tf}}; transform-origin: 0px 0px; transition: transform 0.9s cubic-bezier(0.25, 0.9, 0.3, 1.1)\" display=\"{{val.arrowD}}\"></path>\n<circle cx=\"{{val.ax}}\" cy=\"{{val.ay}}\" r=\"{{val.ringR}}\" fill=\"none\" stroke=\"{{val.fill}}\" stroke-width=\"{{val.ringW}}\" display=\"{{val.ringD}}\"></circle>\n<line x1=\"{{val.rx1}}\" y1=\"{{val.ry}}\" x2=\"{{val.rx2}}\" y2=\"{{val.ry}}\" stroke=\"{{pal.now}}\" stroke-width=\"3\" stroke-linecap=\"round\" display=\"{{val.revealD}}\"></line>\n</g>\n\n<g display=\"{{sp.disp}}\">\n<path d=\"{{sp.limits}}\" fill=\"none\" stroke=\"{{sp.limCol}}\" stroke-width=\"1.4\" stroke-dasharray=\"1.5 4.5\" stroke-linecap=\"round\"></path>\n<path d=\"{{sp.line}}\" fill=\"none\" stroke=\"{{sp.lineCol}}\" stroke-width=\"5.4\" stroke-linejoin=\"round\" stroke-linecap=\"round\"></path>\n<path d=\"{{sp.ray}}\" fill=\"none\" stroke=\"{{sp.rayCol}}\" stroke-width=\"3.4\" stroke-dasharray=\"4.5 4.5\" stroke-linecap=\"round\" display=\"{{sp.rayD}}\"></path>\n<circle cx=\"{{sp.ex}}\" cy=\"{{sp.ey}}\" r=\"7.5\" fill=\"{{sp.endFill}}\" stroke=\"{{sp.endStroke}}\" stroke-width=\"3\"></circle>\n</g>\n\n<text x=\"{{chip.x}}\" y=\"{{chip.y}}\" font-size=\"24\" font-weight=\"500\" fill=\"{{pal.soft}}\" display=\"{{chip.disp}}\">{{chip.t}}</text>\n<g display=\"{{pill.disp}}\">\n<rect x=\"{{pill.x}}\" y=\"{{pill.y}}\" width=\"{{pill.w}}\" height=\"38\" rx=\"19\" fill=\"{{pill.fill}}\" fill-opacity=\"{{pill.fo}}\"></rect>\n<path d=\"{{pill.mark.d}}\" transform=\"{{pill.mark.tf}}\" fill=\"{{pill.mark.fill}}\" stroke=\"{{pill.mark.stroke}}\" stroke-width=\"{{pill.mark.sw}}\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{pill.mark.disp}}\"></path>\n<path d=\"{{pill.bell.d}}\" transform=\"{{pill.bell.tf}}\" fill=\"{{pill.tc}}\" display=\"{{pill.bell.disp}}\"></path>\n<text x=\"{{pill.tx}}\" y=\"{{pill.ty}}\" font-size=\"24\" font-weight=\"600\" fill=\"{{pill.tc}}\">{{pill.t}}</text>\n</g>\n\n<g display=\"{{msg.disp}}\">\n<circle cx=\"{{msg.dx}}\" cy=\"{{msg.dy}}\" r=\"17\" fill=\"none\" stroke=\"{{pal.soft}}\" stroke-width=\"2.5\" display=\"{{msg.discD}}\"></circle>\n<path d=\"{{msg.pie}}\" fill=\"{{pal.soft}}\" display=\"{{msg.discD}}\"></path>\n<path d=\"{{msg.flag}}\" fill=\"{{pal.ink}}\" display=\"{{msg.flagD}}\"></path>\n<path d=\"{{msg.pole}}\" fill=\"none\" stroke=\"{{pal.ink}}\" stroke-width=\"2.4\" stroke-linecap=\"round\" display=\"{{msg.flagD}}\"></path>\n<text x=\"{{msg.x1}}\" y=\"{{msg.y1}}\" text-anchor=\"{{msg.a1}}\" font-size=\"{{msg.fs1}}\" font-weight=\"600\" fill=\"{{msg.c1}}\">{{msg.t1}}</text>\n<text x=\"0\" y=\"{{msg.y2}}\" text-anchor=\"middle\" font-family=\"{{msg.fam2}}\" font-size=\"{{msg.fs2}}\" font-weight=\"{{msg.w2}}\" fill=\"{{msg.c2}}\">{{msg.t2}}</text>\n</g>\n\n<g display=\"{{b.disp}}\">\n<path d=\"{{b.i0.d}}\" transform=\"{{b.i0.tf}}\" fill=\"{{b.i0.fill}}\" stroke=\"{{b.i0.stroke}}\" stroke-width=\"{{b.i0.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{b.i0.disp}}\"></path>\n<path d=\"{{b.i1.d}}\" transform=\"{{b.i1.tf}}\" fill=\"{{b.i1.fill}}\" stroke=\"{{b.i1.stroke}}\" stroke-width=\"{{b.i1.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{b.i1.disp}}\"></path>\n<path d=\"{{b.i2.d}}\" transform=\"{{b.i2.tf}}\" fill=\"{{b.i2.fill}}\" stroke=\"{{b.i2.stroke}}\" stroke-width=\"{{b.i2.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{b.i2.disp}}\"></path>\n<path d=\"{{b.i3.d}}\" transform=\"{{b.i3.tf}}\" fill=\"{{b.i3.fill}}\" stroke=\"{{b.i3.stroke}}\" stroke-width=\"{{b.i3.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{b.i3.disp}}\"></path>\n<path d=\"{{b.i4.d}}\" transform=\"{{b.i4.tf}}\" fill=\"{{b.i4.fill}}\" stroke=\"{{b.i4.stroke}}\" stroke-width=\"{{b.i4.sw}}\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill-rule=\"evenodd\" display=\"{{b.i4.disp}}\"></path>\n<text font-size=\"24\" font-weight=\"{{b.t0.w}}\" fill=\"{{b.t0.col}}\" display=\"{{b.t0.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t0.off}}\">{{b.t0.s}}</textPath></text>\n<text font-size=\"24\" font-weight=\"{{b.t1.w}}\" fill=\"{{b.t1.col}}\" display=\"{{b.t1.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t1.off}}\">{{b.t1.s}}</textPath></text>\n<text font-size=\"24\" font-weight=\"{{b.t2.w}}\" fill=\"{{b.t2.col}}\" display=\"{{b.t2.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t2.off}}\">{{b.t2.s}}</textPath></text>\n<text font-size=\"24\" font-weight=\"{{b.t3.w}}\" fill=\"{{b.t3.col}}\" display=\"{{b.t3.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t3.off}}\">{{b.t3.s}}</textPath></text>\n<text font-size=\"24\" font-weight=\"{{b.t4.w}}\" fill=\"{{b.t4.col}}\" display=\"{{b.t4.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t4.off}}\">{{b.t4.s}}</textPath></text>\n<text font-size=\"24\" font-weight=\"{{b.t5.w}}\" fill=\"{{b.t5.col}}\" display=\"{{b.t5.disp}}\"><textPath href=\"#{{ids.bottom}}\" startOffset=\"{{b.t5.off}}\">{{b.t5.s}}</textPath></text>\n</g>\n\n<circle cx=\"0\" cy=\"0\" r=\"194.5\" fill=\"none\" stroke=\"#121214\" stroke-width=\"1\"></circle>\n</g>\n</svg>";

if (typeof module !== 'undefined') module.exports = { AuraFace, layout, svg, MARKUP };
if (typeof window !== 'undefined') window.Aura = { AuraFace, layout, svg, MARKUP };
