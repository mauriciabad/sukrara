// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Sukrara Aura's Connect IQ store images (docs/20-publishing.md › Connect IQ store: the listing), from the reference
// renderer without the bezel (its drawing prints the GARMIN mark, which a listing may not use without Garmin's
// permission). Synthetic data only: the cases of contracts/watchface/aura/cases.json.
//   node assets/brand/sukrara/connectiq/render.mjs      → docs/img/aura/store/ciq/ (needs Google Chrome, like build.mjs)
// Writes: icon-500.png (store icon: 500 × 500, a solid brand background, the face inside the 10 px margin),
// hero-en.png / hero-es.png (1440 × 720, the name and a tagline per language), screen-<case>.png (390 × 390, the
// watch's own resolution, black outside the round screen). Re-run after the face changes.

import { execFile, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', '..', '..');
const out = path.join(root, 'docs/img/aura/store/ciq');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const brand = JSON.parse(fs.readFileSync(path.join(here, '..', 'brand.json'), 'utf8')).brand;
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sukrara-ciq-'));

// the store's screens, in listing order (cases.json ids), the default face first. None shows a Carbs / Insulin pill: a
// listing may not suggest treatment (Connect IQ App Review Guidelines 1c, like the Mac App Store's screenshots)
const SCREENS = ['hero', 'sparkline', 'dial-contours-heading-up', 'dial-glow-heading-up', 'dial-lasers-heading-up',
  'dial-waterline-heading-up', 'alerts-muted-in-range', 'fields-hiking', 'always-on', 'night'];
const TAGLINE = {
  en: ['Your glucose on your wrist', 'From Sukrara on your phone'],
  es: ['Tu glucosa en la muñeca', 'Desde Sukrara en tu móvil'],
};

const face = (id, size) => {
  const file = path.join(tmp, `${id}-${size}.png`);
  execFileSync('node', [path.join(root, 'tools/watchface/build.mjs'), '--case', id, '--size', String(size), '--out', file]);
  return 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');
};

const shoot = (html, w, h, file) => {
  const page = path.join(tmp, path.basename(file, '.png') + '.html');
  fs.writeFileSync(page, html);
  const a = ['--headless=new', '--hide-scrollbars', '--force-device-scale-factor=1', '--window-size=' + w + ',' + h,
    '--virtual-time-budget=4000', '--screenshot=' + file, pathToFileURL(page).href];
  return new Promise((res, rej) => execFile(chrome, a, { timeout: 60000 }, (e) => (e ? rej(e) : res(file))));
};

const page = (body, bg) => `<!doctype html><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible+Next:wght@400;700&display=block" rel="stylesheet">
<style>html,body{margin:0;background:${bg};overflow:hidden}img{display:block}
.t{font-family:"Atkinson Hyperlegible Next",system-ui,sans-serif;color:#fff}</style>${body}`;

fs.mkdirSync(out, { recursive: true });
const jobs = [];

// icon: the default face, 480 px inside the 10 px margin, on the brand's dark blue (the guidelines: no black or
// transparent background, no text, a preview of the face for watch faces)
jobs.push(shoot(page(`<img src="${face('hero', 480)}" style="margin:10px">`, brand.bottom), 500, 500,
  path.join(out, 'icon-500.png')));

// hero: the brand gradient, the face on the left, the name and the tagline on the right
const heroFace = face('hero', 600);
for (const [lang, [line1, line2]] of Object.entries(TAGLINE)) {
  const body = `<div style="width:1440px;height:720px;background:linear-gradient(115deg,${brand.top},${brand.bottom} 70%);position:relative">
<img src="${heroFace}" style="position:absolute;left:110px;top:60px;width:600px;height:600px;border-radius:50%;box-shadow:0 30px 80px #051a3899">
<div class="t" style="position:absolute;left:800px;top:230px;width:580px">
<div style="font-size:96px;font-weight:700;letter-spacing:-2px">Sukrara Aura</div>
<div style="font-size:46px;margin-top:20px">${line1}</div>
<div style="font-size:34px;margin-top:14px;opacity:.75">${line2}</div></div></div>`;
  jobs.push(shoot(page(body, brand.bottom), 1440, 720, path.join(out, `hero-${lang}.png`)));
}

// screens: the watch's resolution, black around the round screen (what the watch shows)
for (const id of SCREENS) jobs.push(shoot(page(`<img src="${face(id, 390)}">`, '#000'), 390, 390, path.join(out, `screen-${id}.png`)));

const files = await Promise.all(jobs);
fs.rmSync(tmp, { recursive: true, force: true });
for (const f of files) console.log('wrote', path.relative(root, f), fs.statSync(f).size, 'bytes');
