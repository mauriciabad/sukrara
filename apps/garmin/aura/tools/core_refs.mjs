// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The reference pictures without the parts face-aura draws (the dial, the halo, the seconds sweep): every case of
// contracts/watchface/aura/cases.json drawn by tools/watchface/aura.js with dial black, halo none, seconds off, 390 px,
// so garmin_test.py can tell a face-core difference from a missing dial. Needs Node and Google Chrome (like build.mjs).
//   node apps/garmin/aura/tools/core_refs.mjs OUTDIR
import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', '..');
const render = path.join(root, 'tools/watchface/render.html');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const out = path.resolve(process.argv[2] || 'core-refs');
const { cases } = JSON.parse(fs.readFileSync(path.join(root, 'contracts/watchface/aura/cases.json'), 'utf8'));
fs.mkdirSync(out, { recursive: true });

function shoot(cfg, file) {
  const url = pathToFileURL(render).href + '#cfg=' + encodeURIComponent(JSON.stringify(cfg));
  const a = ['--headless=new', '--hide-scrollbars', '--force-device-scale-factor=1', '--default-background-color=00000000',
    '--window-size=390,390', '--virtual-time-budget=6000', '--screenshot=' + file, url];
  return new Promise((res, rej) => execFile(chrome, a, { timeout: 60000 }, (e) => (e ? rej(e) : res())));
}

let i = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (i < cases.length) {
    const c = cases[i++];
    const cfg = Object.assign({}, c.cfg, { size: 390, bezel: false, dial: 'black', halo: 'none', seconds: false });
    await shoot(cfg, path.join(out, c.id + '.png'));
  }
}));
console.log('core_refs: wrote', cases.length, 'pictures to', out);
