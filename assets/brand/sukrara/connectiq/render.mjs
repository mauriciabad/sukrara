// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The Connect IQ store images of Sukrara's three Garmin apps (docs/20-publishing.md › Connect IQ store: the listings),
// drawn from the reference renderers with synthetic data: Aura's faces from tools/watchface/aura.js through the
// website's adapter (website/src/components/face-playground/aura: the parts, their places, the dial alone), the app's
// and the data field's screens from tools/watchface/app.js, each inline in one page per picture, shot by headless Chrome.
//   node assets/brand/sukrara/connectiq/render.mjs [aura|app|field …]   (needs Google Chrome, set CHROME to its binary;
//   and Node 23.6 or newer, which runs the website's TypeScript as it is)
// Each app's apps/garmin/<app>/store/listing.json says what to write into its `images` folder:
//   icon-500.png     the store icon: 500 × 500, an animated PNG (ICONS), transparent around the face or the watch,
//                    which fill it edge to edge (the owner's choice), no text. Aura: the face (Garmin's advice for
//                    watch faces); the app and the data field: their watch with their screens
//   hero-<lang>.png  1440 × 720 per language: the kind of app as Garmin names it (`kind`), the title (`heroTitle` when
//                    the store title is too long for it), the tagline and "no internet", beside the app in a watch
//   screen-<id>.jpg  each of `screenshots`, at most 5 (the store's limit), laid out at 1000 × 500: one or two screens
//                    in a watch of our own (no Garmin mark: Garmin's own bezels print GARMIN), more as the screens
//                    alone; drawn at 2× and saved as the widest JPEG within 150 KB (SHOT_WIDTHS). One set for every
//                    language (the store keeps one), so their words are English, as are the screens (app.js)
// None shows a Carbs / Insulin pill or an amount to take (Connect IQ App Review Guidelines 1c), nor the app's alert
// screen as if it opened by itself: on the watch an alert arrives as Garmin's notification and the app's screen is
// what opening it shows (#256); only the data field shows its own alert, during an activity. Re-run after a screen's
// design changes; look at every picture before committing it (each must stay within the store's weights:
// tools/release/release.py checks them). A label points at a place on its screen picked by eye (LABELLED below):
// check the lines after a screen's layout changes.

import { execFile, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { apng } from './png8.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', '..', '..');
const require = createRequire(import.meta.url);
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const brand = JSON.parse(fs.readFileSync(path.join(here, '..', 'brand.json'), 'utf8')).brand;
const glyph = fs.readFileSync(path.join(here, '..', 'glyph.svg'), 'utf8').replace(/fill="#000000"/g, 'fill="#FFFFFF"');
const fontUrl = (f) => pathToFileURL(path.join(root, 'apps/garmin/fonts', f)).href;
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sukrara-ciq-'));
const listing = (app) => JSON.parse(fs.readFileSync(path.join(root, 'apps/garmin', app, 'store/listing.json'), 'utf8'));

// the reference renderers: Aura through the website's adapter (its parts and places), the app and the data field
const Aura = require(path.join(root, 'tools/watchface/aura.js'));
const WL = require(path.join(root, 'tools/watchface/app.js'));
const site = (f) => import(pathToFileURL(path.join(root, 'website/src/components/face-playground', f)).href);
const { createAura } = await site('aura/adapter.ts');
const { scopeIds } = await site('svg-ids.ts');
const aura = createAura(Aura, JSON.parse(fs.readFileSync(path.join(root, 'contracts/watchface/aura/cases.json'), 'utf8')).cases);

// ---- words ------------------------------------------------------------------------------------------------------------

// the line every hero carries: the readings come from the phone, no internet in between (docs/20-publishing.md)
const NO_INTERNET = { en: 'Straight from your phone, no internet', es: 'Directo desde tu móvil, sin internet' };
// Aura's dials as the website names them (its adapter), shown as previews in the hero: the ones that follow the
// glucose (not black, nor the map, which never moves)
const DIALS = ['halftone', 'topo', 'water', 'laser', 'glow'];
const dialName = (v, lang) => aura.looks.options.find((o) => o.value === v).label[lang];

// ---- pages and pictures -----------------------------------------------------------------------------------------------

const run = (a) => new Promise((res, rej) => execFile(chrome, a, { timeout: 90000 }, (e) => (e ? rej(e) : res())));
// headless Chrome sometimes exits with an error after writing the file: a picture on disk counts; else try again
async function chromeShot(url, w, h, file, clear = false, scale = 1) {
  const a = ['--headless=new', '--hide-scrollbars', '--force-device-scale-factor=' + scale, '--default-background-color=' + (clear ? '00000000' : '000000ff'),
    '--window-size=' + w + ',' + h, '--virtual-time-budget=8000', '--screenshot=' + file, url];
  for (let i = 0; i < 3; i++) {
    fs.rmSync(file, { force: true });
    await run(a).catch(() => {});
    if (fs.existsSync(file)) return file;
  }
  throw new Error('no picture: ' + file);
}
const page = (w, h, bg, body) => `<!doctype html><meta charset="utf-8">
<style>
@font-face{font-family:"Atkinson Hyperlegible Next";src:url("${fontUrl('AtkinsonHyperlegibleNext-wght.ttf')}") format("truetype");font-weight:200 800;font-display:block}
@font-face{font-family:"Sukrara Roboto Condensed";src:url("${fontUrl('RobotoCondensed-Bold.woff2')}") format("woff2");font-weight:700;font-display:block}
@font-face{font-family:"Sukrara Roboto";src:url("${fontUrl('Roboto-Regular.woff2')}") format("woff2");font-weight:400;font-display:block}
html,body{margin:0;overflow:hidden}
.pic{position:relative;width:${w}px;height:${h}px;overflow:hidden;background:${bg};font-family:"Atkinson Hyperlegible Next",system-ui,sans-serif;color:#fff}
.pic *{box-sizing:border-box}
.abs{position:absolute}
.scr svg{display:block;width:100%;height:100%}
.dial-only [data-part]:not([data-part='dial']){display:none}
</style><div class="pic">${body}</div>`;
let pageNo = 0;
// bg 'transparent': a picture with an alpha channel (the icons); scale: device pixels per CSS pixel
async function shoot(w, h, bg, body, file, scale = 1) {
  const html = path.join(tmp, `p${++pageNo}.html`);
  fs.writeFileSync(html, page(w, h, bg, body));
  await chromeShot(pathToFileURL(html).href, w, h, file, bg === 'transparent', scale);
  return file;
}

// ---- the screens --------------------------------------------------------------------------------------------------------

let ids = 0;
const fill = (svg) => svg.replace(/ width="[\d.]+" height="[\d.]+"/, ' width="100%" height="100%"').replace(/ role="img" aria-label="[^"]*"/, '');
// an Aura face (SPEC.md §3 inputs over the face's defaults); dialOnly: the dial alone, as the website's previews.
// Never a Carbs / Insulin pill (what to take: Connect IQ App Review Guidelines 1c): such a moment needs alerts waiting,
// muted or with nothing to say
const face = (cfg, lang = 'en', dialOnly = false) => {
  const v = { ...aura.defaults(), ...cfg }, pill = Aura.layout({ ...v, size: 390, bezel: false }).pill;
  if (!dialOnly && pill.disp !== 'none' && /^(carbs|rapid|long)$/.test(pill.mark && pill.mark.key)) throw new Error(`render.mjs: a ${pill.mark.key} pill on ${JSON.stringify(cfg).slice(0, 80)}`);
  const svg = fill(aura.render(v, lang, { id: 'a' + ++ids }));
  return { svg: dialOnly ? `<div class="dial-only" style="width:100%;height:100%">${svg}</div>` : svg, w: 390, h: 390, cfg };
};
// a screen of the app or the data field (app.js cfg; the fēnix 8 47 mm unless `cls` says another class); never a
// Carbs / Insulin pill either (a low or high scenario says `pill`)
const SIZE = { r454: [454, 454], m260: [260, 260], sq2: [320, 360] };
const screen = (cfg) => {
  const c = { cls: 'r454', ...cfg };
  const [w, h] = SIZE[c.cls], d = WL.screen(c).d;
  if (/^(appA|glance)$/.test(c.screen) && d.pill && d.pill.filled) throw new Error(`render.mjs: a ${d.pill.text} pill on ${JSON.stringify(cfg)}`);
  return { svg: fill(scopeIds(WL.svg(c), 's' + ++ids)), w, h, rect: c.cls === 'sq2', cfg: c };
};

// ---- the watch ------------------------------------------------------------------------------------------------------

// A watch of our own around a round screen, for a picture with one or two (with more, the screens alone: grid()):
// centred at (cx, cy), the screen D px wide; a graphite case with a plain bezel (no marks, no numbers), five buttons
// (the round watches' places: START 60°, BACK 120°, DOWN 240°, UP 270°, LIGHT 300°) and a strap: running o.reach px up
// and down (o.strap 'full', the default), only down ('down', a watch in front of another) or a short stub that fades
// out ('stub', a watch with words under it); a shadow unless o.shadow is false
const STUB = 0.3; // the stub beyond the case, × D
const caseHeight = (D) => D * 1.27;
function watch(s, cx, cy, D, o = {}) {
  const u = ++ids, R = D / 2, mode = o.strap || 'full', box = D * 1.67;
  const tall = mode === 'stub' ? caseHeight(D) + 2 * STUB * D : Math.max(box, 2 * (o.reach || 1000));
  const defs = `<defs>
<linearGradient id="f${u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset="${(STUB * D * 0.85 / tall).toFixed(3)}" stop-color="#fff"/><stop offset="${(1 - STUB * D * 0.85 / tall).toFixed(3)}" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<mask id="k${u}" maskContentUnits="objectBoundingBox"><rect width="1" height="1" fill="url(#f${u})"/></mask>
<linearGradient id="m${u}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7A7D84"/><stop offset=".32" stop-color="#3E4146"/><stop offset=".7" stop-color="#24262A"/><stop offset="1" stop-color="#131417"/></linearGradient>
<linearGradient id="e${u}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#9A9DA3"/><stop offset=".5" stop-color="#2A2C30"/><stop offset="1" stop-color="#505359"/></linearGradient>
<linearGradient id="s${u}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#101114"/><stop offset=".35" stop-color="#26282C"/><stop offset=".55" stop-color="#2C2E33"/><stop offset="1" stop-color="#121316"/></linearGradient>
<linearGradient id="b${u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6C6F75"/><stop offset="1" stop-color="#25272B"/></linearGradient>
</defs>`;
  const btn = (deg) => `<rect x="${-R * 0.085}" y="${-R * 1.335}" width="${R * 0.17}" height="${R * 0.13}" rx="${R * 0.035}" fill="url(#b${u})" transform="rotate(${deg})"/>`;
  const ticks = Array.from({ length: 60 }, (_, k) => {
    const a = k * 6 * Math.PI / 180, r0 = R * (k % 5 ? 1.1 : 1.075), r1 = R * 1.14;
    return `M${(r0 * Math.sin(a)).toFixed(1)} ${(-r0 * Math.cos(a)).toFixed(1)}L${(r1 * Math.sin(a)).toFixed(1)} ${(-r1 * Math.cos(a)).toFixed(1)}`;
  }).join('');
  const strap = `<rect x="${-R * 0.6}" y="${mode === 'down' ? 0 : -tall / 2}" width="${R * 1.2}" height="${mode === 'down' ? tall / 2 : tall}" fill="url(#s${u})"${mode === 'stub' ? ` mask="url(#k${u})"` : ''}/>`;
  const body = `${[60, 120, 240, 270, 300].map(btn).join('')}
<rect x="${-R * 0.72}" y="${-R * 1.46}" width="${R * 1.44}" height="${R * 2.92}" rx="${R * 0.3}" fill="url(#m${u})"/>
<circle r="${R * 1.27}" fill="url(#m${u})"/><circle r="${R * 1.265}" fill="none" stroke="url(#e${u})" stroke-width="${R * 0.012}" opacity=".7"/>
<circle r="${R * 1.18}" fill="#0D0E10"/><circle r="${R * 1.18}" fill="none" stroke="#000" stroke-width="${R * 0.01}" opacity=".6"/>
<path d="${ticks}" stroke="#3A3C42" stroke-width="${R * 0.012}" stroke-linecap="round"/>
<circle r="${R * 1.045}" fill="#000"/>`;
  const shadow = o.shadow === false ? '' : 'filter:drop-shadow(0 ' + D * 0.06 + 'px ' + D * 0.12 + 'px rgba(2,8,24,.6))';
  return `<svg class="abs" style="left:${cx - box / 2}px;top:${cy - tall / 2}px;${shadow}" width="${box}" height="${tall}" viewBox="${-box / 2} ${-tall / 2} ${box} ${tall}">${defs}${strap}${body}</svg>
<div class="abs scr" style="left:${cx - R}px;top:${cy - R}px;width:${D}px;height:${D}px;border-radius:50%;overflow:hidden;background:#000">${s.svg}</div>
<div class="abs" style="left:${cx - R}px;top:${cy - R}px;width:${D}px;height:${D}px;border-radius:50%;background:linear-gradient(140deg,rgba(255,255,255,.08),rgba(255,255,255,0) 38%)"></div>`;
}
// where a point of the screen (fractions of its width and height, 0..1 from the top left) sits in the picture
const onScreen = (s, cx, cy, D, [fx, fy]) => {
  const H = s.rect ? D * s.h / s.w : D;
  return [cx - D / 2 + fx * D, cy - H / 2 + fy * H];
};

// ---- the pieces of a picture ------------------------------------------------------------------------------------------

// The screenshots: laid out at W × H (1000 × 500) and drawn at 2×; then the largest JPEG within the form's 150 KB, the
// store's enlarged view being up to 890 CSS px wide (1780 px on a 2× screen) while its 256-colour PNGs of 1000 px came
// out soft there: the widest of SHOT_WIDTHS that fits at quality 50 or better, at the best quality that fits
const SHOT_SCALE = 2, SHOT_MAX = 146 * 1024, SHOT_WIDTHS = [1800, 1600, 1400, 1200, 1000];
function jpeg(png, file, max) {
  const small = path.join(tmp, 'small-' + path.basename(file, '.jpg') + '.png');
  for (const w of SHOT_WIDTHS) {
    execFileSync('sips', ['-Z', String(w), png, '--out', small], { stdio: 'ignore' });
    for (let q = 80; q >= 50; q -= 5) {
      execFileSync('sips', ['-s', 'format', 'jpeg', '-s', 'formatOptions', String(q), small, '--out', file], { stdio: 'ignore' });
      if (fs.statSync(file).size <= max) { fs.rmSync(small); return file; }
    }
  }
  throw new Error(`render.mjs: ${path.basename(file)} weighs more than ${max} bytes at ${SHOT_WIDTHS.at(-1)} px, quality 50`);
}
const SHOT_BG = `radial-gradient(ellipse 75% 85% at 50% 45%,#16507F 0%,${brand.bottom} 55%,#061A38 100%)`;
const HERO_BG = `linear-gradient(115deg,${brand.top},${brand.bottom} 70%)`;
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

// a screen as the website shows it: no watch around it, round or the square's rounded rectangle, centred at (cx, cy),
// D px wide, a faint ring at its edge and a soft shadow under it
const screenHeight = (s, D) => (s.rect ? D * s.h / s.w : D);
function bare(s, cx, cy, D, o = {}) {
  const H = screenHeight(s, D), r = s.rect ? D * 18 / 320 + 'px' : '50%', ring = Math.max(1.5, D * 0.006);
  const shadow = o.shadow === false ? '' : `,0 ${D * 0.04}px ${D * 0.1}px rgba(2,8,24,.55)`;
  return `<div class="abs scr" style="left:${cx - D / 2}px;top:${cy - H / 2}px;width:${D}px;height:${H}px;border-radius:${r};overflow:hidden;background:#000;box-shadow:0 0 0 ${ring}px rgba(255,255,255,.18)${shadow}">${s.svg}</div>`;
}

// labels left and right of a screen, each a line to its place on it: [{ at: [fx, fy], name, sub, side }]
function labels(s, cx, cy, D, items, o = {}) {
  const rad = o.rad || D / 2 + 6, gap = o.gap || 62, colL = cx - rad - (o.room || 54), colR = cx + rad + (o.room || 54);
  const top = o.top || 40, bottom = o.bottom || 460;
  let svg = '', html = '';
  for (const side of ['left', 'right']) {
    // as wide as asked, within the picture's 12 px edge
    const lw = Math.min(o.labelW || 250, side === 'left' ? colL - 12 : o.w - colR - 12);
    const its = items.filter((i) => i.side === side).map((i) => ({ ...i, p: onScreen(s, cx, cy, D, i.at) })).sort((a, b) => a.p[1] - b.p[1]);
    // each where its place is, pushed apart; then back up if the last ran off the bottom
    let y = top;
    // each as tall as its words (a sub of more than one line: about 9.9 px a character at 19.5 px)
    const tall = (i) => 37 + (i.sub ? 24 * Math.ceil(i.sub.length * 9.9 / lw) : 0);
    for (const i of its) { i.y = Math.max(i.p[1] - (i.sub ? 16 : 0), y); y = i.y + Math.max(gap, tall(i)); }
    const over = its.length ? its[its.length - 1].y + tall(its[its.length - 1]) - bottom : 0;
    // from the bottom up, each above the next; then down again from the top if that ran out of room
    if (over > 0) { let lim = bottom; for (const i of [...its].reverse()) { i.y = Math.min(i.y, lim - tall(i)); lim = i.y; } let t = top; for (const i of its) { i.y = Math.max(i.y, t); t = i.y + tall(i); } }
    for (const i of its) {
      const x0 = side === 'left' ? colL + 12 : colR - 12, ly = i.y + 17;
      const elbow = side === 'left' ? Math.min(i.p[0] - 16, cx - rad - 6) : Math.max(i.p[0] + 16, cx + rad + 6);
      svg += `<polyline points="${x0},${ly} ${elbow},${ly} ${i.p[0]},${i.p[1]}" fill="none" stroke="#fff" stroke-opacity=".8" stroke-width="2"/>
<circle cx="${i.p[0]}" cy="${i.p[1]}" r="6" fill="${brand.accent}" stroke="#061A38" stroke-width="2.5"/>`;
      const pos = side === 'left' ? `right:${o.w - colL}px;text-align:right` : `left:${colR}px`;
      html += `<div class="abs" style="${pos};top:${i.y}px;width:${lw}px">
<div style="font-size:28px;font-weight:700;line-height:32px">${esc(i.name)}</div>${i.sub ? `<div style="font-size:19.5px;line-height:24px;opacity:.78;margin-top:2px">${esc(i.sub)}</div>` : ''}</div>`;
    }
  }
  return `<svg class="abs" style="left:0;top:0" width="${o.w}" height="${o.h}">${svg}</svg>${html}`;
}

// rows of screens, each with a caption under it ([[{ s, name, sub }, …], …]), under a title when it has one: each row
// one block in its band; one or two in a row in their watch, more the screens alone, as large as the band and their
// slot allow (the owner, 2026-11-23: frames only where they don't take the room the screens need)
function grid(rows, w, h, o = {}) {
  const head = o.title ? 54 : 0, band = (h - head) / rows.length, small = rows.length > 1 || rows[0].length > 4;
  const capH = small ? 50 : 64, nameF = small ? 22 : 27, subF = small ? 16.5 : 20;
  const title = o.title ? `<div class="abs" style="left:0;right:0;top:16px;text-align:center;font-size:30px;font-weight:700">${esc(o.title)}</div>` : '';
  return title + rows.map((items, r) => {
    const slot = w / items.length, framed = items.length <= 2;
    if (framed) {
      // the watch's case (1.27 D) and its strap's stub as tall as the band allows
      const D = Math.min(slot * 0.62, (band - capH - 24) / (1.27 + STUB), o.max || 999), top = head + band * r + (band - D * (1.27 + STUB) - capH) / 2;
      const cy = top + D * (0.635 + STUB / 2), capY = cy + D * 0.635 + STUB * D * 0.45;
      return items.map((it, k) => watch(it.s, slot * (k + 0.5), cy, D, { strap: 'stub' }) + caption(it, slot * (k + 0.5), slot, capY)).join('');
    }
    const D = Math.min(slot * (rows.length > 1 ? 0.84 : 0.88), band - capH - 18, o.max || 999);
    // a square screen as tall as the round ones
    const Ds = items.map((it) => (it.s.rect ? D * it.s.w / it.s.h : D)), maxH = Math.max(...items.map((it, k) => screenHeight(it.s, Ds[k])));
    const top = head + band * r + (band - maxH - 12 - capH) / 2, cy = top + maxH / 2, capY = top + maxH + 12;
    return items.map((it, k) => bare(it.s, slot * (k + 0.5), cy, Ds[k]) + caption(it, slot * (k + 0.5), slot, capY)).join('');
  }).join('');
  function caption(it, cx, slot, capY) {
    return `<div class="abs" style="left:${cx - slot / 2 + 4}px;width:${slot - 8}px;top:${capY}px;text-align:center">
<div style="font-size:${nameF}px;font-weight:700;line-height:1.15">${esc(it.name)}</div>${it.sub ? `<div style="font-size:${subF}px;line-height:1.25;opacity:.8;margin-top:2px">${esc(it.sub)}</div>` : ''}</div>`;
  }
}

// Material Symbols' public_off (Apache-2.0; the app's ds_ic_public_off), in the text's colour: "no internet"
const noNet = (size) => `<svg viewBox="0 -960 960 960" width="${size}" height="${size}" fill="currentColor" style="flex:none"><path d="M819-28 701-146q-48 32-103.5 49T480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-62 17-117.5T146-701L27-820l57-57L876-85l-57 57ZM440-162v-78q-33 0-56.5-23.5T360-320v-40L168-552q-3 18-5.5 36t-2.5 36q0 121 79.5 212T440-162Zm374-99-58-58q21-37 32.5-77.5T800-480q0-98-54.5-179T600-776v16q0 33-23.5 56.5T520-680h-80v45L261-814q48-31 103-48.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 61-17.5 116T814-261Z"/></svg>`;
// the hero's words: what kind of app as the store names it (a line each, faded), the title, the tagline's lines,
// "no internet" and any extra
const words = (kind, title, lines, lang, x, extra = '') => `<div class="abs" style="left:${x}px;top:0;bottom:0;right:40px;display:flex;flex-direction:column;justify-content:center">
<div style="font-size:19px;font-weight:600;letter-spacing:2px;line-height:1.45;text-transform:uppercase;opacity:.62;margin-bottom:10px">${[].concat(kind).map(esc).join('<br>')}</div>
<div style="font-size:${title.length > 14 ? 80 : 92}px;font-weight:700;letter-spacing:-2px;line-height:1.02">${esc(title)}</div>
${lines.map((l, k) => `<div style="font-size:${k ? 34 : 44}px;margin-top:${k ? 10 : 20}px;line-height:1.15;opacity:${k ? 0.85 : 1}">${esc(l)}</div>`).join('')}
<div style="display:flex;align-items:center;gap:12px;margin-top:26px;font-size:28px;font-weight:600;color:${brand.accent}">${noNet(34)}<span>${esc(NO_INTERNET[lang])}</span></div>
${extra}</div>`;

// the readings' way, CGM → Sukrara on the phone → the watch, with no internet in between
function noInternet(s, w) {
  // the app's own sensor icon (ds_ic_dia_sensor), the phone with Sukrara's icon, the watch with this app's screen
  const CGM = '<svg viewBox="0 0 960 960" width="84" height="84" fill="none" stroke="#fff"><circle cx="373" cy="587" r="230" stroke-width="80"/><circle cx="373" cy="587" r="80" fill="#fff" stroke="none"/><path d="M466.2,239.3 A360,360 0 0,1 720.7,493.8M512.6,138.2 A470,470 0 0,1 821.8,447.4" stroke-width="70" stroke-linecap="round"/></svg>';
  const y = 296, xs = [180, 460, 790];
  const phone = `<div class="abs" style="left:${xs[1] - 52}px;top:${y - 88}px;width:104px;height:176px;border-radius:20px;background:#15171B;border:4px solid #5A5D63;box-shadow:0 18px 40px rgba(2,8,24,.55);display:flex;align-items:center;justify-content:center">
<div style="width:58px;height:58px;border-radius:15px;background:${HERO_BG};display:flex;align-items:center;justify-content:center">${glyph.replace('<svg ', '<svg width="38" height="38" ')}</div></div>`;
  const arrow = (x0, x1) => `<svg class="abs" style="left:${x0}px;top:${y - 12}px" width="${x1 - x0}" height="24"><path d="M2 12H${x1 - x0 - 6}M${x1 - x0 - 16} 3L${x1 - x0 - 5} 12L${x1 - x0 - 16} 21" fill="none" stroke="${brand.accent}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  const cap = (x, t) => `<div class="abs" style="left:${x - 140}px;width:280px;top:420px;text-align:center;font-size:27px;font-weight:600;line-height:31px">${t}</div>`;
  return `<div class="abs" style="left:56px;top:40px;display:flex;align-items:center;gap:16px;font-size:44px;font-weight:700;color:${brand.accent}">${noNet(50)}Works without internet</div>
<div class="abs" style="left:56px;top:106px;width:${w - 112}px;font-size:25px;line-height:32px;opacity:.88">Each new reading goes from your CGM to Sukrara on your phone, and straight on to your watch as it arrives.</div>
<div class="abs" style="left:${xs[0] - 42}px;top:${y - 42}px">${CGM}</div>${cap(xs[0], 'Your CGM')}
${arrow(xs[0] + 70, xs[1] - 72)}${phone}${cap(xs[1], 'Sukrara on your phone')}
${arrow(xs[1] + 72, xs[2] - 118)}${watch(s, xs[2], y, 160, { strap: 'stub' })}${cap(xs[2], 'Your watch')}`;
}

// a screen in its watch on the right, a few words about it on the left
function explain(s, head, lines) {
  return `<div class="abs" style="left:60px;top:0;bottom:0;width:500px;display:flex;flex-direction:column;justify-content:center">
<div style="font-size:42px;font-weight:700;line-height:48px;margin-bottom:16px">${esc(head)}</div>
${lines.map((l) => `<div style="font-size:27px;line-height:35px;margin-top:6px;opacity:.9">${esc(l)}</div>`).join('')}</div>
${watch(s, 770, 250, 330, { reach: 500 })}`;
}

// ---- what each listing shows ------------------------------------------------------------------------------------------

// A moment of the website's made-up day (website/…/day/day.json, made from real episodes of a Sukrara export): the
// reading at h:mm, the 3 h before it, the rate over the last 10 min, the last treatment, whether alerts wait after it
// or are muted, the steps. `shift` / `scale` move the readings into a range the day doesn't reach. Over it, `cfg`.
const DAY = JSON.parse(fs.readFileSync(path.join(root, 'website/src/components/face-playground/day/day.json'), 'utf8'));
function day(hm, o = {}) {
  const [h, m] = hm.split(':').map(Number), t = h * 60 + m, i = Math.round(t / 5);
  const g = (k) => Math.round(DAY.glucose[(k + 288) % 288] * (o.scale || 1) + (o.shift || 0));
  const tr = DAY.treatments.filter((x) => x.at <= t).at(-1);
  const quiet = DAY.quiet.find(([a, b]) => t >= a && t < b), muted = DAY.muted.find(([a, b]) => t >= a && t < b);
  const st = DAY.steps.findIndex(([at]) => at > t), [[t0, s0], [t1, s1]] = [DAY.steps[st - 1], DAY.steps[st]];
  return {
    time: `${h}:${String(m).padStart(2, '0')}:00`, glucose: g(i), rate: (g(i) - g(i - 2)) / 10,
    spark: Array.from({ length: 37 }, (_, k) => g(i - 36 + k)),
    last: tr ? { items: tr.items.map((x) => ({ k: x.kind, a: String(x.amount) })), ago: t - tr.at } : { items: [] },
    status: muted ? 'muted' : quiet ? 'quiet' : 'auto', left: (muted || quiet || [0, t + 25])[1] - t,
    steps: Math.round(s0 + (s1 - s0) * (t - t0) / (t1 - t0)), ...o.cfg,
  };
}
// Aura's moments: high and still rising after a correction (the hero, the parts named: the aura spreads; the seconds'
// sweep by its label)
const RISE = day('11:20');
const PARTS_CFG = day('11:30', { cfg: { time: '11:30:06' } });
// Aura's parts, each where the website's places put it (aura.places: on the 390 screen, centre 0, y down), or `at`
// where the place falls on another part (the dial: on its dots; the bottom fields: each its own)
const AURA_PARTS = [
  { part: 'dial', side: 'left', name: 'Dial', sub: 'the background: wider out of range', at: [-75, -125] },
  { part: 'sideL', side: 'left', name: 'Watch battery', sub: 'a mark per day left' },
  { part: 'value', side: 'left', name: 'Glucose', sub: 'in its range’s colour' },
  { part: 'fields', side: 'left', name: 'Time in range', sub: 'today', at: [-125, 145] },
  { part: 'sweep', side: 'right', name: 'Seconds', sub: 'a faint sweep by the rim' },
  { part: 'top', side: 'right', name: 'Your fields', sub: 'steps, weather, heart rate…' },
  { part: 'sideR', side: 'right', name: 'Glucose rail', sub: 'your limits, the last 30 min' },
  { part: 'trend', side: 'right', name: 'The last hours', sub: 'or Sukrara’s trend arrow' },
  { part: 'pill', side: 'right', name: 'Sukrara’s alerts', sub: 'a bell while they wait or are muted' },
  { part: 'fields', side: 'right', name: 'Last treatment', sub: 'insulin and carbs, time since', at: [118, 150] },
];
function auraParts(cfg) {
  const p = aura.places({ ...aura.defaults(), ...cfg });
  return AURA_PARTS.map((l) => {
    const b = p[l.part].boxes[0];
    const [x, y] = l.at || [l.side === 'left' ? b.x : b.x + b.w, b.y + b.h / 2];
    return { ...l, at: [(x + 195) / 390, (y + 195) / 390] };
  });
}

// The app's and the data field's labels: where each part sits on its fēnix 8 47 mm screen (fractions of the screen
// from the top left), picked by eye on the picture
const LABELLED = {
  glance: { s: { screen: 'glance' }, title: 'Glance: Sukrara in your watch’s glance list', items: [
    { at: [0.33, 0.43], side: 'left', name: 'Glucose and trend' },
    { at: [0.715, 0.4], side: 'right', name: 'Last treatment', sub: 'rapid insulin, 1 h 5 min ago' },
    { at: [0.935, 0.5], side: 'right', name: 'Time in range', sub: 'over the days you pick' },
  ] },
  app: { s: { screen: 'appA' }, items: [
    { at: [0.07, 0.29], side: 'left', name: 'Last treatment', sub: 'rapid insulin, 1 h 5 min ago' },
    { at: [0.05, 0.58], side: 'left', name: 'Time in ranges', sub: 'above, in and below, 12 h' },
    { at: [0.175, 0.76], side: 'left', name: 'Your treatments', sub: 'insulin and carbs you logged' },
    { at: [0.5, 0.17], side: 'right', name: 'Glucose and trend' },
    { at: [0.94, 0.29], side: 'right', name: 'Time in range', sub: 'over the days you pick' },
    { at: [0.85, 0.5], side: 'right', name: 'The last 12 hours', sub: 'with your range and limits' },
    { at: [0.635, 0.87], side: 'right', name: 'Sensor days left', sub: 'or Sukrara’s pill, when needed' },
  ] },
  complications: { s: { screen: 'faceCx' }, title: 'Complications, on any watch face that takes them', items: [
    { at: [0.3, 0.84], side: 'left', name: 'The last 24 hours' },
    { at: [0.6, 0.12], side: 'right', name: 'Glucose', sub: 'in any slot the face offers' },
    { at: [0.85, 0.7], side: 'right', name: 'Log', sub: 'time since your last treatment' },
  ] },
  field: { s: { screen: 'dfSlot', form: 'band' }, title: 'On an activity’s data screen', items: [
    { at: [0.07, 0.55], side: 'left', name: 'Glucose', sub: 'in its range’s colour' },
    { at: [0.72, 0.6], side: 'right', name: 'The last hours', sub: 'your limits dotted' },
  ] },
};
const W = 1000, H = 500;
// a screen in its watch with its parts named (and what it is, when that needs saying)
function named(s, items, title) {
  const D = title ? 330 : 360, cy = title ? 278 : 250;
  const top = title ? `<div class="abs" style="left:0;right:0;top:18px;text-align:center;font-size:25px;font-weight:600;opacity:.85">${esc(title)}</div>` : '';
  return watch(s, 500, cy, D, { reach: H }) + labels(s, 500, cy, D, items, { w: W, h: H, rad: D * 0.635, room: 20, labelW: 262, top: title ? 66 : 14, bottom: 496, gap: 54 }) + top;
}
const labelled = (id) => named(screen(LABELLED[id].s), LABELLED[id].items, LABELLED[id].title);
const rows = (r, o) => [W, H, grid(r, W, H, o)];

// the pictures of each listing (at most 5, the store's limit; the most important first): id → () => [w, h, body]
const SHOTS = {
  aura: {
    parts: () => [W, H, named(face(PARTS_CFG), auraParts(PARTS_CFG))],
    // every range's colour, the pill's states, treatments, the arrow and the line, other fields and dials
    ranges: () => rows([[
      { s: face(day('03:05', { shift: -16, cfg: { dial: 'glow' } })), name: 'Very low', sub: 'carbs taken: alerts wait' },
      { s: face(day('02:55', { cfg: { status: 'muted', left: 25, dial: 'topo', topField: 'heart' } })), name: 'Low', sub: 'alerts muted, time left' },
      { s: face(day('20:10', { cfg: { dial: 'water' } })), name: 'In range', sub: 'very good: green' },
      { s: face(day('09:05', { cfg: { trendStyle: 'arrow', dial: 'laser', topField: 'weather', vals: { weather: { t: 9, sky: 'rain' } } } })), name: 'In range', sub: '141–179: yellower, with the arrow' },
    ], [
      { s: face(day('15:05', { cfg: { fields: ['bodyBattery', 'treatment'] } })), name: 'High', sub: 'after lunch: insulin and carbs' },
      { s: face(day('11:40', { cfg: { dial: 'laser', topField: 'sunset' } })), name: 'Very high', sub: 'a correction: alerts wait' },
      { s: face(day('11:40', { scale: 1.25, cfg: { status: 'none', dial: 'topo', topField: 'heart' } })), name: 'Extremely high', sub: 'from 300' },
      { s: face(day('13:20', { cfg: { trendStyle: 'arrow', dial: 'water' } })), name: 'Falling fast', sub: 'in range: nothing to do yet' },
    ]], { D: 112 }),
    // how the dial follows the glucose and its trend: the same dial, five moments
    dial: () => rows([[
      { s: face(day('20:10', { cfg: { dial: 'topo' } })), name: 'In range, steady', sub: 'gathered at your level' },
      { s: face(day('10:50', { cfg: { dial: 'topo' } })), name: 'Heading up fast', sub: 'it spreads as you leave your range' },
      { s: face(day('11:40', { cfg: { dial: 'topo' } })), name: 'Very high', sub: 'the further out, the wider' },
      { s: face(day('12:10', { cfg: { dial: 'topo' } })), name: 'Coming back', sub: 'calmer as you head back' },
      { s: face(day('16:35', { shift: -20, cfg: { status: 'quiet', left: 10, last: { items: [{ k: 'carbs', a: '15' }], ago: 5 }, dial: 'topo' } })), name: 'Low, falling', sub: 'it spreads from below' },
    ]], { D: 140, title: 'The dial follows your glucose and its trend' }),
    'no-internet': () => [W, H, noInternet(face(day('20:10')), W)],
    // the sensor's end and the next one's warm-up; old readings; always-on, night and private
    sensor: () => rows([[
      { s: face(day('13:40', { cfg: { sensor: 'ending', sensorLeft: 5 } })), name: 'Sensor ending', sub: 'its last hours counted down' },
      { s: face({ ...day('15:15'), sensor: 'ended' }), name: 'Sensor ended', sub: 'time to replace it' },
      { s: face({ ...day('15:40'), sensor: 'warmup', warm: 0.3 }), name: 'Warming up', sub: 'and when it’s ready' },
      { s: face(day('17:20', { cfg: { age: 9 } })), name: 'Old reading', sub: 'struck through, never as current' },
    ], [
      { s: face(day('17:00', { cfg: { age: 12, phone: false } })), name: 'Phone out of reach', sub: 'it says why' },
      { s: face(day('07:30', { cfg: { mode: 'aod' } })), name: 'Always-on', sub: 'only what matters lit' },
      { s: face(day('23:30', { cfg: { mode: 'night' } })), name: 'Night', sub: 'dimmed in Do Not Disturb' },
      { s: face(day('18:20', { cfg: { privacy: 'private' } })), name: 'Private', sub: 'the number when you hold' },
    ]], { D: 112 }),
  },
  app: {
    app: () => [W, H, labelled('app')],
    // the glance and the complications, what most people see first
    glance: () => rows([[
      { s: screen({ screen: 'glance' }), name: 'Glance', sub: 'glucose, trend, last treatment, time in range' },
      { s: screen({ screen: 'faceCx' }), name: 'Complications', sub: 'glucose, the last 24 h and the time since your last treatment, on any watch face that takes them' },
    ]], { D: 230 }),
    // every range's colour, the pills, old readings, a new sensor, the alert as it opens, a square screen
    states: () => rows([[
      { s: screen({ screen: 'appA', scenario: 'veryLow', pill: 'none' }), name: 'Very low' },
      { s: screen({ screen: 'appA', scenario: 'inRange' }), name: 'In range' },
      { s: screen({ screen: 'appA', pill: 'muted' }), name: 'In range, higher', sub: 'alerts muted' },
      { s: screen({ screen: 'appA', scenario: 'high', pill: 'quiet' }), name: 'Very high', sub: 'alerts wait after insulin' },
    ], [
      { s: screen({ screen: 'appA', age: 7 }), name: 'Old reading', sub: 'struck through, never as current' },
      { s: screen({ screen: 'appA', sensor: 'warmup' }), name: 'New sensor', sub: 'warming up' },
      { s: screen({ screen: 'alert', scenario: 'low' }), name: 'An alert', sub: 'open its notification' },
      { s: screen({ screen: 'appA', scenario: 'low', pill: 'none', cls: 'sq2' }), name: 'Square screens', sub: 'and round MIP' },
    ]], { D: 112 }),
    // logging, muting
    log: () => rows([[
      { s: screen({ screen: 'actions' }), name: 'Log or mute', sub: 'START, from any page' },
      { s: screen({ screen: 'logAmount' }), name: 'The amount', sub: 'or one you often use' },
      { s: screen({ screen: 'logConfirm' }), name: 'Hold to log', sub: 'letting go cancels' },
      { s: screen({ screen: 'logDone' }), name: 'Logged', sub: 'on your phone too' },
      { s: screen({ screen: 'muted' }), name: 'Muted', sub: '15 min to 2 h, Unmute' },
    ]], { D: 140, title: 'Log insulin and carbs, or mute alerts, from your wrist' }),
    'no-internet': () => [W, H, noInternet(screen({ screen: 'appA' }), W)],
  },
  field: {
    field: () => [W, H, labelled('field')],
    sizes: () => rows([[
      { s: screen({ screen: 'dfSlot', form: 'whole' }), name: 'Whole screen' },
      { s: screen({ screen: 'dfSlot', form: 'half' }), name: 'Half' },
      { s: screen({ screen: 'dfSlot', form: 'quarter', scenario: 'low' }), name: 'Small slot', sub: 'a low turns it red' },
    ]], { D: 200, title: 'Any size of slot' }),
    alert: () => [W, H, explain(screen({ screen: 'dfAlert', scenario: 'low' }), 'Its own alert',
      ['During an activity, your locked phone’s alert fills the screen and vibrates.'])],
    'no-internet': () => [W, H, noInternet(screen({ screen: 'dfSlot', form: 'band' }), W)],
    more: () => rows([[
      { s: screen({ screen: 'dfSlot', form: 'band', scenario: 'veryLow' }), name: 'Very low', sub: 'the slot in its colour' },
      { s: screen({ screen: 'dfSlot', form: 'band', scenario: 'high' }), name: 'Very high' },
      { s: screen({ screen: 'dfSlot', form: 'band', age: 8 }), name: 'Old reading', sub: 'struck through, never as current' },
      { s: screen({ screen: 'dfSlot', form: 'band', cls: 'm260' }), name: 'Round MIP', sub: 'readable in sunlight' },
    ]], { D: 168 }),
  },
};

// The store icons (the upload form's Cover Image: 500 × 500, ≤ 300 KB): animated PNGs, as other listings' (the store
// serves the file as it is and plays it), ICON_MS a frame, looping. Transparent around the face or the watch, which
// fill the picture edge to edge: the owner's choice (2026-11-23) over Garmin's brand guidelines, which ask for a solid
// background (docs/20-publishing.md). Aura: the round face through a day (the dial following the glucose); the app: its
// watch with its pages, the main one first; the data field: its watch with each size of slot, the whole screen first.
// The watch: its buttons and case touch the sides (the UP button on the left reaches 1.335 R, the case on the right
// 1.27 R), its strap runs off the top and bottom; no shadow.
const ICON_MS = 1600;
const iconFace = (cfg) => `<div class="abs scr" style="left:0;top:0;width:500px;height:500px;border-radius:50%;overflow:hidden">${face(cfg).svg}</div>`;
const ICON_R = 500 / (1.335 + 1.27);
const iconWatch = (cfg) => watch(screen(cfg), 1.335 * ICON_R, 250, 2 * ICON_R, { reach: 500, shadow: false });
const ICONS = {
  aura: () => ['11:40', '13:20', '20:10', '08:35', '11:20'].map((t) => iconFace(day(t))),
  app: () => [{ screen: 'appA' }, { screen: 'appNums' }, { screen: 'glance' }, { screen: 'actions' }, { screen: 'logDone' }].map(iconWatch),
  field: () => [{ screen: 'dfSlot', form: 'whole' }, { screen: 'dfSlot', form: 'half' }, { screen: 'dfSlot', form: 'band' },
    { screen: 'dfSlot', form: 'quarter', scenario: 'low' }, { screen: 'dfAlert', scenario: 'low' }].map(iconWatch),
};

// ---- the listing --------------------------------------------------------------------------------------------------------

async function render(app) {
  const l = listing(app);
  const out = path.join(root, l.images);
  fs.mkdirSync(out, { recursive: true });
  for (const f of fs.readdirSync(out)) if (/^(icon-500|hero-[a-z]+|screen-.*)\.(png|jpg)$/.test(f)) fs.rmSync(path.join(out, f));
  const jobs = [];
  // the icon: an animated PNG of ICONS' frames, the first the still a viewer without APNG shows
  const frames = ICONS[app]().map((body, k) => shoot(500, 500, 'transparent', body, path.join(tmp, `${app}-icon-${k + 1}.png`)));
  jobs.push(Promise.all(frames).then((files) => {
    if (FRAMES) for (const f of files) fs.copyFileSync(f, path.join(FRAMES, path.basename(f)));
    const file = path.join(out, 'icon-500.png');
    fs.writeFileSync(file, apng(files.map((f) => fs.readFileSync(f)), ICON_MS));
    return file;
  }));
  for (const lang of Object.keys(l.tagline)) {
    const title = (l.heroTitle || l.title)[lang];
    let body;
    if (app === 'aura') {
      // the face rising steeply in its watch; beside the words, the dials that follow the glucose, each alone
      const preview = aura.looks.preview({ ...aura.defaults(), ...RISE }), mine = RISE.dial || 'halftone';
      const dials = `<div style="display:flex;gap:10px;margin-top:30px">${DIALS.map((d) => `<div style="width:100px;display:flex;flex-direction:column;align-items:center;text-align:center">
<div class="scr" style="width:88px;height:88px;border-radius:50%;overflow:hidden;background:#000;box-shadow:0 0 0 ${d === mine ? `3px ${brand.accent}` : '2px rgba(255,255,255,.25)'}">${face({ ...preview, dial: d }, lang, true).svg}</div>
<div style="font-size:19px;margin-top:10px;line-height:22px;opacity:.85">${esc(dialName(d, lang))}</div></div>`).join('')}</div>`;
      body = `<div class="abs" style="inset:0;background:${HERO_BG}"></div>${watch(face(RISE, lang), 400, 360, 470, { reach: 720 })}${words(l.kind[lang], title, l.tagline[lang], lang, 800, dials)}`;
    } else {
      // the main screen, and in front of it a second: the report (the app) or the field's own alert (the data field)
      const [big, small] = app === 'app' ? [{ screen: 'appA' }, { screen: 'appNums' }] : [{ screen: 'dfSlot', form: 'band' }, { screen: 'dfAlert', scenario: 'low' }];
      body = `<div class="abs" style="inset:0;background:${HERO_BG}"></div>${watch(screen(big), 330, 320, 420, { reach: 720 })}${watch(screen(small), 650, 545, 210, { strap: 'down', reach: 720 })}${words(l.kind[lang], title, l.tagline[lang], lang, 860)}`;
    }
    jobs.push(shoot(1440, 720, brand.bottom, body, path.join(out, `hero-${lang}.png`)));
  }
  for (const id of l.screenshots) {
    if (!SHOTS[app][id]) throw new Error(`render.mjs: no picture ${id} for ${app}`);
    const [w, h, body] = SHOTS[app][id]();
    // drawn at SHOT_SCALE × its layout, then the best JPEG that weighs at most SHOT_MAX
    jobs.push(shoot(w, h, SHOT_BG, body, path.join(tmp, `screen-${id}.png`), SHOT_SCALE).then((f) => jpeg(f, path.join(out, `screen-${id}.jpg`), SHOT_MAX)));
  }
  return Promise.all(jobs);
}

// node render.mjs [aura|app|field …] [--frames <dir>]: --frames also keeps each icon frame there, to look at one by one
const args = process.argv.slice(2), at = args.indexOf('--frames');
const FRAMES = at >= 0 ? path.resolve(args.splice(at, 2)[1]) : null;
if (FRAMES) fs.mkdirSync(FRAMES, { recursive: true });
const apps = args.length ? args : ['aura', 'app', 'field'];
try {
  for (const app of apps) {
    const files = await render(app);
    for (const f of files) console.log('wrote', path.relative(root, f), fs.statSync(f).size, 'bytes');
  }
} finally {
  fs.rmSync(tmp, { recursive: true, force: true });
}
