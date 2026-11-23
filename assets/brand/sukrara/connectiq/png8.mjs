// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// A truecolour PNG (as headless Chrome writes it: 8 bits, RGB or RGBA, not interlaced) as a 256-colour palette PNG:
// median cut over its colours, each colour to the nearest of the palette, no dithering. The Connect IQ store takes
// screenshots of at most 150 KB (docs/20-publishing.md), and a watch in its case on a full picture weighs more.
// Opaque pictures only (the alpha channel is dropped). apng() makes the animated store icon (APNG, ≤ 300 KB) the
// same way, one palette for all its frames.
//   import { png8, apng } from './png8.mjs';   png8(fs.readFileSync(file)) → Buffer;   apng([buf, …], 1600) → Buffer

import zlib from 'node:zlib';

function decode(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('png8: not a PNG');
  let p = 8, w, h, type, idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), kind = buf.toString('latin1', p + 4, p + 8), data = buf.subarray(p + 8, p + 8 + len);
    if (kind === 'IHDR') {
      w = data.readUInt32BE(0); h = data.readUInt32BE(4); type = data[9];
      if (data[8] !== 8 || (type !== 2 && type !== 6) || data[12] !== 0) throw new Error('png8: only 8-bit RGB / RGBA, not interlaced');
    } else if (kind === 'IDAT') idat.push(data);
    p += 12 + len;
  }
  const bpp = type === 6 ? 4 : 3, stride = w * bpp, raw = zlib.inflateSync(Buffer.concat(idat)), px = Buffer.alloc(h * stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], src = y * (stride + 1) + 1, row = y * stride, prev = row - stride;
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? px[row + x - bpp] : 0, b = y ? px[prev + x] : 0, c = x >= bpp && y ? px[prev + x - bpp] : 0;
      let v = raw[src + x];
      if (f === 1) v += a;
      else if (f === 2) v += b;
      else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) {
        const q = a + b - c, pa = Math.abs(q - a), pb = Math.abs(q - b), pc = Math.abs(q - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      px[row + x] = v & 255;
    }
  }
  const rgb = new Uint32Array(w * h);
  for (let i = 0; i < w * h; i++) rgb[i] = (px[i * bpp] << 16) | (px[i * bpp + 1] << 8) | px[i * bpp + 2];
  return { w, h, rgb };
}

// median cut: split the box with the most weight × spread along its widest channel at the weighted median
function palette(counts, n) {
  const cols = [...counts.keys()], ch = (c, k) => (c >> (16 - 8 * k)) & 255;
  const box = (items) => {
    let lo = [255, 255, 255], hi = [0, 0, 0], wt = 0;
    for (const c of items) { for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], ch(c, k)); hi[k] = Math.max(hi[k], ch(c, k)); } wt += counts.get(c); }
    const span = [0, 1, 2].map((k) => hi[k] - lo[k]), axis = span.indexOf(Math.max(...span));
    return { items, wt, axis, score: items.length > 1 ? wt * span[axis] : -1 };
  };
  const boxes = [box(cols)];
  while (boxes.length < n) {
    let i = 0;
    for (let k = 1; k < boxes.length; k++) if (boxes[k].score > boxes[i].score) i = k;
    const b = boxes[i];
    if (b.score < 0) break;
    const items = b.items.sort((x, y) => ch(x, b.axis) - ch(y, b.axis));
    let acc = 0, cut = 1;
    for (; cut < items.length - 1; cut++) { acc += counts.get(items[cut - 1]); if (acc >= b.wt / 2) break; }
    boxes.splice(i, 1, box(items.slice(0, cut)), box(items.slice(cut)));
  }
  return boxes.map((b) => {
    const s = [0, 0, 0];
    for (const c of b.items) for (let k = 0; k < 3; k++) s[k] += ch(c, k) * counts.get(c);
    return s.map((v) => Math.round(v / b.wt));
  });
}

function chunk(kind, data) {
  const out = Buffer.alloc(12 + data.length), td = Buffer.concat([Buffer.from(kind, 'latin1'), data]);
  out.writeUInt32BE(data.length, 0);
  td.copy(out, 4);
  out.writeUInt32BE(zlib.crc32(td) >>> 0, 8 + data.length);
  return out;
}

// pictures of one size → one palette for all, each picture as palette indices
function quantize(pics, n) {
  const counts = new Map();
  for (const p of pics) for (const c of p.rgb) counts.set(c, (counts.get(c) || 0) + 1);
  const pal = palette(counts, n), index = new Map();
  for (const c of counts.keys()) {
    const r = c >> 16, g = (c >> 8) & 255, b = c & 255;
    let best = 0, bd = Infinity;
    pal.forEach(([pr, pg, pb], k) => {
      const d = 2 * (r - pr) ** 2 + 4 * (g - pg) ** 2 + 3 * (b - pb) ** 2;
      if (d < bd) { bd = d; best = k; }
    });
    index.set(c, best);
  }
  return { pal, idx: pics.map((p) => Uint8Array.from(p.rgb, (c) => index.get(c))) };
}
// rows x0…x0+w of y0…y0+h, each with filter 0, deflated
const rows = (idx, W, x0, y0, w, h) => {
  const raw = Buffer.alloc(h * (w + 1));
  for (let y = 0; y < h; y++) raw.set(idx.subarray((y0 + y) * W + x0, (y0 + y) * W + x0 + w), y * (w + 1) + 1);
  return zlib.deflateSync(raw, { level: 9 });
};
const SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const ihdr = (w, h) => { const b = Buffer.alloc(13); b.writeUInt32BE(w, 0); b.writeUInt32BE(h, 4); b[8] = 8; b[9] = 3; return chunk('IHDR', b); };

export function png8(buf, n = 256) {
  const pic = decode(buf), { pal, idx } = quantize([pic], n);
  return Buffer.concat([SIG, ihdr(pic.w, pic.h), chunk('PLTE', Buffer.from(pal.flat())),
    chunk('IDAT', rows(idx[0], pic.w, 0, 0, pic.w, pic.h)), chunk('IEND', Buffer.alloc(0))]);
}

// Truecolour PNGs of one size → an animated PNG (APNG) in 256 colours, one palette for every frame, each shown `ms`
// and looping forever; after the first, a frame holds only the rectangle that changed. Its first frame is the
// picture a viewer without APNG shows.
export function apng(bufs, ms, n = 256) {
  const pics = bufs.map(decode), { w, h } = pics[0];
  if (pics.some((p) => p.w !== w || p.h !== h)) throw new Error('apng: frames of different sizes');
  const { pal, idx } = quantize(pics, n);
  let seq = 0;
  const fctl = (x, y, fw, fh) => {
    const b = Buffer.alloc(26);
    b.writeUInt32BE(seq++, 0); b.writeUInt32BE(fw, 4); b.writeUInt32BE(fh, 8); b.writeUInt32BE(x, 12); b.writeUInt32BE(y, 16);
    b.writeUInt16BE(ms, 20); b.writeUInt16BE(1000, 22); // delay ms/1000 s; dispose none, blend source
    return chunk('fcTL', b);
  };
  const actl = Buffer.alloc(8);
  actl.writeUInt32BE(pics.length, 0);
  const out = [SIG, ihdr(w, h), chunk('acTL', actl), chunk('PLTE', Buffer.from(pal.flat())), fctl(0, 0, w, h), chunk('IDAT', rows(idx[0], w, 0, 0, w, h))];
  for (let k = 1; k < idx.length; k++) {
    let x0 = w, y0 = h, x1 = 0, y1 = 0;
    for (let i = 0; i < w * h; i++) if (idx[k][i] !== idx[k - 1][i]) { const x = i % w, y = (i / w) | 0; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
    if (x1 < x0) { x0 = 0; y0 = 0; x1 = 0; y1 = 0; }
    out.push(fctl(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
    const s = Buffer.alloc(4); s.writeUInt32BE(seq++, 0);
    out.push(chunk('fdAT', Buffer.concat([s, rows(idx[k], w, x0, y0, x1 - x0 + 1, y1 - y0 + 1)])));
  }
  out.push(chunk('IEND', Buffer.alloc(0)));
  return Buffer.concat(out);
}
