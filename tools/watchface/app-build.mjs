// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Sukrara on Garmin: the reference pictures of every screen on every screen class, from the reference (app.js).
//   node tools/watchface/app-build.mjs                  every class → docs/img/app/ref/<class>/<case>.png
//   node tools/watchface/app-build.mjs --class r360     one class (ids: app.js CLS)
//   node tools/watchface/app-build.mjs --sheets <dir>   also a contact sheet per class (<dir>/<class>.png)
//   node tools/watchface/app-build.mjs --list           the cases
//   node tools/watchface/app-build.mjs --check          only the check that no text is cut by the screen or the rim
// Needs Google Chrome (set CHROME to its binary), as build.mjs.

import { createRequire } from 'node:module';
import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const WL = require('./app.js');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const refDir = path.join(root, 'docs/img/app/ref');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const args = process.argv.slice(2);
const opt = (f, d) => (args.includes(f) ? args[args.indexOf(f) + 1] : d);

// the folder of each class
export const DIRS = { r360: 'round-360', r390: 'round-390', r416: 'round-416', r454: 'round-454', r466: 'round-466', m218: 'mip-218', m240: 'mip-240', m260: 'mip-260', m280: 'mip-280', sq2: 'rect-venu-sq2', x1: 'rect-venu-x1' };

// every screen of SPEC §1, and the states that change a layout
export const CASES = [
  ['glance', { screen: 'glance' }],
  ['glance-low', { screen: 'glance', scenario: 'low' }],
  ['glance-old', { screen: 'glance', age: 7 }],
  ['glance-warmup', { screen: 'glance', sensor: 'warmup' }],
  ['app', { screen: 'appA' }],
  ['app-low', { screen: 'appA', scenario: 'low' }],
  ['app-high', { screen: 'appA', scenario: 'high' }],
  ['app-muted', { screen: 'appA', pill: 'muted' }],
  ['app-old', { screen: 'appA', age: 7 }],
  ['app-no-reading', { screen: 'appA', age: 29 }],
  ['app-warmup', { screen: 'appA', sensor: 'warmup' }],
  ['app-numbers', { screen: 'appNums' }],
  ['list', { screen: 'actions' }],
  ['list-muted', { screen: 'actions', muted: true }],
  ['log-amount', { screen: 'logAmount' }],
  ['log-amount-carbs', { screen: 'logAmount', kind: 'carbs', amount: 30 }],
  ['log-amount-two-buttons', { screen: 'logAmount', buttons: 'two' }],
  ['log-hold', { screen: 'logConfirm' }],
  ['log-hold-same-kind', { screen: 'logConfirm', dup: true, progress: 0.2 }],
  ['log-sending', { screen: 'logSending' }],
  ['log-done', { screen: 'logDone' }],
  ['log-queued', { screen: 'logQueued' }],
  ['mute-lengths', { screen: 'muteLengths' }],
  ['mute-done', { screen: 'muteDone' }],
  ['muted', { screen: 'muted' }],
  ['notification', { screen: 'wake' }],
  ['alert-low', { screen: 'alert', scenario: 'low' }],
  ['alert-very-low', { screen: 'alert', scenario: 'veryLow' }],
  ['alert-high', { screen: 'alert', scenario: 'high', relaxed: true }],
  ['alert-extremely-high', { screen: 'alert', scenario: 'high', glucose: 330 }],
  ['remind-done', { screen: 'remindDone' }],
  ['field-whole', { screen: 'dfSlot', form: 'whole' }],
  ['field-half', { screen: 'dfSlot', form: 'half' }],
  ['field-band', { screen: 'dfSlot', form: 'band' }],
  ['field-quarter', { screen: 'dfSlot', form: 'quarter' }],
  ['field-cap', { screen: 'dfSlot', form: 'cap' }],
  ['field-band-very-low', { screen: 'dfSlot', form: 'band', scenario: 'veryLow' }],
  ['field-quarter-low', { screen: 'dfSlot', form: 'quarter', scenario: 'low' }],
  ['field-alert', { screen: 'dfAlert', scenario: 'low' }],
  ['field-alert-high', { screen: 'dfAlert', scenario: 'high', kind: 'high' }],
  ['field-old', { screen: 'dfSlot', form: 'band', age: 8 }],
  ['field-setup', { screen: 'dfSetup', form: 'band' }],
  ['field-setup-quarter', { screen: 'dfSetup', form: 'quarter' }],
  ['complications', { screen: 'faceCx' }],
];

function shoot(hash, file, w, h) {
  const url = pathToFileURL(path.join(here, 'app-render.html')).href + '#' + hash;
  const a = ['--headless=new', '--hide-scrollbars', '--force-device-scale-factor=1', '--default-background-color=00000000',
    '--window-size=' + w + ',' + h, '--virtual-time-budget=8000', '--screenshot=' + file, url];
  // headless Chrome sometimes exits with an error after writing the file: a picture on disk counts; else try again
  const once = () => new Promise((res) => { try { fs.rmSync(file, { force: true }); } catch (e) { /* none */ } execFile(chrome, a, { timeout: 90000 }, () => res(fs.existsSync(file))); });
  return once().then((ok) => ok || once()).then((ok) => ok || once()).then((ok) => { if (!ok) throw new Error('no picture: ' + file); });
}
async function pool(jobs, n) {
  let i = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (i < jobs.length) { const j = jobs[i++]; await j(); } }));
}

if (args.includes('--list')) { CASES.forEach((c) => console.log(c[0], JSON.stringify(c[1]))); process.exit(0); }
// every text inside the screen and clear of the rim, on every class (app.js clippedTexts): listed, and the build fails
let clippedN = 0;
for (const k of Object.keys(WL.CLS)) for (const [id, cfg] of CASES) {
  const r = WL.screen(Object.assign({ cls: k }, cfg));
  if (r.clipped.length) { clippedN++; console.log('clipped', k, id, JSON.stringify(r.clipped)); }
}
if (args.includes('--check')) process.exit(clippedN ? 1 : 0);
if (clippedN) { console.log(clippedN, 'screens cut a text'); process.exit(1); }
const only = opt('--class');
const classes = Object.keys(WL.CLS).filter((k) => !only || k === only);
const sheets = opt('--sheets');
const jobs = [];
for (const k of classes) {
  const cl = WL.CLS[k], dir = path.join(refDir, DIRS[k]);
  fs.mkdirSync(dir, { recursive: true });
  for (const [id, cfg] of CASES) {
    const c = Object.assign({ cls: k }, cfg);
    jobs.push(() => shoot('cfg=' + encodeURIComponent(JSON.stringify(c)), path.join(dir, id + '.png'), cl.w, cl.h));
  }
  if (sheets) {
    fs.mkdirSync(sheets, { recursive: true });
    const cols = Math.max(4, Math.floor(3000 / (cl.w + 16))), rows = Math.ceil(CASES.length / cols);
    const W = 40 + cols * (cl.w + 16), H = 120 + rows * (cl.h + 44);
    const sh = { title: DIRS[k] + ' · ' + cl.name + ' (' + cl.w + ' × ' + cl.h + (cl.mip ? ', MIP 64 colours' : ', AMOLED') + (cl.touch ? ', touch' : ', buttons only') + ')',
      sub: 'Every screen of contracts/watchface/app/SPEC.md §1 from tools/watchface/app.js at 1:1 px (cls: ' + k + ').', items: CASES.map(([id, cfg]) => ({ id, cfg: Object.assign({ cls: k }, cfg) })) };
    jobs.push(() => shoot('sheet=' + encodeURIComponent(JSON.stringify(sh)), path.join(sheets, DIRS[k] + '.png'), W, H));
  }
}
await pool(jobs, 4);
console.log('wrote', jobs.length, 'pictures for', classes.join(', '));
