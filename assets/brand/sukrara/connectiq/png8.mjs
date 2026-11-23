// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// A truecolour PNG (as headless Chrome writes it: 8 bits, RGB or RGBA, not interlaced) as a 256-colour palette PNG:
// median cut over its colours, each colour to the nearest of the palette, no dithering. The Connect IQ store takes
// screenshots of at most 150 KB (docs/20-publishing.md), and a watch in its case on a full picture weighs more.
// Opaque pictures only (the alpha channel is dropped).
//   import { png8 } from './png8.mjs';   png8(fs.readFileSync(file)) → Buffer

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

export function png8(buf, n = 256) {
  const { w, h, rgb } = decode(buf);
  const counts = new Map();
  for (const c of rgb) counts.set(c, (counts.get(c) || 0) + 1);
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
  const raw = Buffer.alloc(h * (w + 1));
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) raw[y * (w + 1) + 1 + x] = index.get(rgb[y * w + x]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 3;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('PLTE', Buffer.from(pal.flat())), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
