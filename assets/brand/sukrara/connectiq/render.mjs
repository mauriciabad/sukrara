// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The Connect IQ store images of Sukrara's three Garmin apps (docs/20-publishing.md › Connect IQ store: the listings),
// from the reference renderers, without a bezel (the reference bezel prints the GARMIN mark, which a listing may not use
// without Garmin's permission). Synthetic data only: Aura's cases (contracts/watchface/aura/cases.json, through
// tools/watchface/build.mjs) and the app's and data field's screens (tools/watchface/app.js, through app-render.html).
//   node assets/brand/sukrara/connectiq/render.mjs [aura|app|field …]   (needs Google Chrome, like build.mjs)
// Each app's apps/garmin/<app>/store/listing.json says what to write into its `images` folder:
//   icon-500.png   the store icon: 500 × 500, an opaque background (not black), no text, inside the 10 px margin.
//                  Aura: a preview of the face (Garmin's advice for watch faces); the app and the data field: Sukrara's
//                  glyph on the brand's gradient (Garmin's advice for apps and data fields: one brand identity)
//   hero-<lang>.png  1440 × 720: the title (`heroTitle` when the store title is too long for it) and the tagline per
//                    language over the app's screens
//   screen-<id>.png  each of `screenshots`, at the watch's own resolution, black outside the screen
// None shows a Carbs / Insulin pill or an amount to take: a listing may not suggest treatment (Connect IQ App Review
// Guidelines 1c). Re-run after a screen's design changes; look at every picture before committing it.

import { execFile, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', '..', '..');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const brand = JSON.parse(fs.readFileSync(path.join(here, '..', 'brand.json'), 'utf8')).brand;
const glyph = fs.readFileSync(path.join(here, '..', 'glyph.svg'), 'utf8').replace(/fill="#000000"/g, 'fill="#FFFFFF"');
const font = pathToFileURL(path.join(root, 'apps/garmin/fonts/AtkinsonHyperlegibleNext-wght.ttf')).href;
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sukrara-ciq-'));
const listing = (app) => JSON.parse(fs.readFileSync(path.join(root, 'apps/garmin', app, 'store/listing.json'), 'utf8'));

// the app's and the data field's screens (tools/watchface/app-build.mjs's cases; `cls` from app.js CLS): the round
// 454 AMOLED (fēnix 8 47 mm) unless said, one MIP and one rectangle to show the other screens
const APP_SCREENS = {
  glance: { screen: 'glance' },
  app: { screen: 'appA' },
  'app-numbers': { screen: 'appNums' },
  complications: { screen: 'faceCx' },
  list: { screen: 'actions' },
  'log-amount': { screen: 'logAmount' },
  'log-hold': { screen: 'logConfirm' },
  'log-done': { screen: 'logDone' },
  'alert-low': { screen: 'alert', scenario: 'low' },
  'app-old': { screen: 'appA', age: 7 },
  'app-mip': { screen: 'appA', cls: 'm260' },
  'app-square': { screen: 'appA', cls: 'sq2' },
  'field-band': { screen: 'dfSlot', form: 'band' },
  'field-whole': { screen: 'dfSlot', form: 'whole' },
  'field-half': { screen: 'dfSlot', form: 'half' },
  'field-quarter-low': { screen: 'dfSlot', form: 'quarter', scenario: 'low' },
  'field-alert': { screen: 'dfAlert', scenario: 'low' },
  'field-old': { screen: 'dfSlot', form: 'band', age: 8 },
  'field-mip': { screen: 'dfSlot', form: 'band', cls: 'm260' },
};
const SIZE = { r454: [454, 454], m260: [260, 260], sq2: [320, 360] };

const run = (a) => new Promise((res, rej) => execFile(chrome, a, { timeout: 90000 }, (e) => (e ? rej(e) : res())));
// headless Chrome sometimes exits with an error after writing the file: a picture on disk counts; else try again
async function chromeShot(url, w, h, file, bg) {
  const a = ['--headless=new', '--hide-scrollbars', '--force-device-scale-factor=1', '--default-background-color=' + bg,
    '--window-size=' + w + ',' + h, '--virtual-time-budget=8000', '--screenshot=' + file, url];
  for (let i = 0; i < 3; i++) {
    fs.rmSync(file, { force: true });
    await run(a).catch(() => {});
    if (fs.existsSync(file)) return file;
  }
  throw new Error('no picture: ' + file);
}
const shoot = (html, w, h, file) => {
  const page = path.join(tmp, path.relative(root, file).replace(/[/\\]/g, '_') + '.html');
  fs.writeFileSync(page, html);
  return chromeShot(pathToFileURL(page).href, w, h, file, '000000ff');
};
const dataUri = (file) => 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');

// one Aura case (build.mjs) at a size
const auraFace = (id, size) => {
  const file = path.join(tmp, `aura-${id}-${size}.png`);
  execFileSync('node', [path.join(root, 'tools/watchface/build.mjs'), '--case', id, '--size', String(size), '--out', file]);
  return file;
};
// one screen of the app or the data field (app-render.html), at the class's own pixels
const appScreen = async (id) => {
  const cfg = Object.assign({ cls: 'r454' }, APP_SCREENS[id]);
  if (!APP_SCREENS[id]) throw new Error('render.mjs: no screen ' + id);
  const [w, h] = SIZE[cfg.cls];
  const file = path.join(tmp, `app-${id}.png`);
  const url = pathToFileURL(path.join(root, 'tools/watchface/app-render.html')).href + '#cfg=' + encodeURIComponent(JSON.stringify(cfg));
  await chromeShot(url, w, h, file, '000000ff');
  return file;
};

const page = (body, bg) => `<!doctype html><meta charset="utf-8">
<style>@font-face{font-family:"Atkinson Hyperlegible Next";src:url("${font}") format("truetype");font-weight:200 800}
html,body{margin:0;background:${bg};overflow:hidden}img{display:block}
.t{font-family:"Atkinson Hyperlegible Next",system-ui,sans-serif;color:#fff}</style>${body}`;
const gradient = `linear-gradient(115deg,${brand.top},${brand.bottom} 70%)`;
const round = (src, x, y, d, shadow = true) => `<img src="${src}" style="position:absolute;left:${x}px;top:${y}px;width:${d}px;height:${d}px;border-radius:50%;${shadow ? 'box-shadow:0 30px 80px #051a3899' : ''}">`;
// the hero's words: the title and the tagline's two lines, right of the screens
const words = (title, [l1, l2], x) => `<div class="t" style="position:absolute;left:${x}px;top:0;bottom:0;right:50px;display:flex;flex-direction:column;justify-content:center">
<div style="font-size:${title.length > 14 ? 80 : 96}px;font-weight:700;letter-spacing:-2px;line-height:1.05">${title}</div>
<div style="font-size:46px;margin-top:22px;line-height:1.15">${l1}</div>
<div style="font-size:34px;margin-top:14px;opacity:.8;line-height:1.2">${l2}</div></div>`;

async function render(app) {
  const l = listing(app);
  const out = path.join(root, l.images);
  fs.mkdirSync(out, { recursive: true });
  for (const f of fs.readdirSync(out)) if (/^(icon-500|hero-[a-z]+|screen-.*)\.png$/.test(f)) fs.rmSync(path.join(out, f));
  const jobs = [];
  if (app === 'aura') {
    // icon: the default face, 480 px inside the 10 px margin, on the brand's dark blue
    jobs.push(shoot(page(`<img src="${dataUri(auraFace('hero', 480))}" style="margin:10px">`, brand.bottom), 500, 500, path.join(out, 'icon-500.png')));
    const hero = dataUri(auraFace('hero', 600));
    for (const [lang, tag] of Object.entries(l.tagline)) {
      const body = `<div style="width:1440px;height:720px;background:${gradient};position:relative">${round(hero, 110, 60, 600)}${words(l.title[lang], tag, 800)}</div>`;
      jobs.push(shoot(page(body, brand.bottom), 1440, 720, path.join(out, `hero-${lang}.png`)));
    }
    for (const id of l.screenshots) {
      jobs.push(shoot(page(`<img src="${dataUri(auraFace(id, 390))}">`, '#000'), 390, 390, path.join(out, `screen-${id}.png`)));
    }
  } else {
    // icon: the glyph, white, on the gradient (the Play icon's look), well inside the 10 px margin
    jobs.push(shoot(page(`<div style="width:500px;height:500px;background:${gradient};display:flex;align-items:center;justify-content:center">
<div style="width:300px;height:300px">${glyph.replace('<svg ', '<svg width="300" height="300" ')}</div></div>`, brand.bottom), 500, 500, path.join(out, 'icon-500.png')));
    // hero: the app's page 1 with the logged screen in front; the data field on an activity's data screen
    const [big, small] = app === 'app' ? ['app', 'complications'] : ['field-band', 'field-alert'];
    const bigSrc = dataUri(await appScreen(big)), smallSrc = dataUri(await appScreen(small));
    for (const [lang, tag] of Object.entries(l.tagline)) {
      const body = `<div style="width:1440px;height:720px;background:${gradient};position:relative">${round(bigSrc, 70, 70, 580)}${round(smallSrc, 530, 400, 280)}${words((l.heroTitle || l.title)[lang], tag, 860)}</div>`;
      jobs.push(shoot(page(body, brand.bottom), 1440, 720, path.join(out, `hero-${lang}.png`)));
    }
    for (const id of l.screenshots) {
      jobs.push(appScreen(id).then((f) => { fs.copyFileSync(f, path.join(out, `screen-${id}.png`)); return path.join(out, `screen-${id}.png`); }));
    }
  }
  return Promise.all(jobs);
}

const apps = process.argv.slice(2).length ? process.argv.slice(2) : ['aura', 'app', 'field'];
for (const app of apps) {
  const files = await render(app);
  for (const f of files) console.log('wrote', path.relative(root, f), fs.statSync(f).size, 'bytes');
}
fs.rmSync(tmp, { recursive: true, force: true });
