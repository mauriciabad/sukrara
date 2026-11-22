// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// Sukrara on Garmin: the watch app, its glance, the data field, the complications as other faces show them.
// The design reference (contracts/watchface/app/SPEC.md), as tools/watchface/aura.js is for Sukrara Aura: where the
// SPEC and this code disagree, the code wins (and the SPEC gets fixed). Approved by the owner on the design canvases
// (round 2, 2026-11-22): https://claude.ai/code/artifact/e53c8936-3c98-4978-90e2-797751ca0507
//
// Pure: screen(cfg) -> { els, bez, vb, label, d }. `els` is a display list (paths, circles, rects, texts; origin at the
// screen's centre, y down, 1 unit = 1 px of the 390 × 390 Descent Mk3i 43 mm), drawn in order: the shape of a
// Monkey C draw loop. `data(cfg)` makes the INPUT (a made-up day ending 20:41) from a scenario; every screen
// function draws only from that input `d`, so the port replaces data() with the payload and keeps the rest.
// Colours, type and the arrow are Aura's (aura.js pal(), widths(), the arrow path). Known approximations, to be
// done right in the port from the widget's renderer (the owner, 2026-11-22): the % column's dots and the way they
// mask the line.
var WL = (function () {
  var PAL = {
    ink: '#FFFFFF', soft: '#A3A3A8', dim: '#6E6E73', faint: '#3A3A3E', grid: '#26262A', band: '#38383E', band2: '#4E4E55',
    grey: '#8E8E93', veryLow: '#C2413B', low: '#F0524F', tight: '#3DD47A', target: '#8CC63F', high: '#F5A524',
    veryHigh: '#FF7A45', extremelyHigh: '#E35BD0', now: '#C6F432', rapid: '#8AB4FF', long: '#C9B0FF', carbs: '#FFB74D',
    note: '#B4B4B4', on: '#1C1C1E', card: '#141416', card2: '#1C1C1F'
  };
  var PAL_AMOLED = PAL;
  // Round MIP, 64 colours (every channel 00 / 55 / AA / FF), no alpha: each token picked by hand from the 64 for its hue
  // and for sunlight (bright and saturated; nothing darker than 55 carries meaning). `card2` (a selected row, a chip, a
  // secondary button) becomes an outline (CARD); `grid` (unlit tracks) a 50 % dither of 555555. Contrast: SPEC §14.
  var PAL_MIP = {
    ink: '#FFFFFF', soft: '#AAAAAA', dim: '#555555', faint: '#555555', grid: 'DITHER:#555555:50', band: '#555555', band2: '#555555',
    grey: '#AAAAAA', veryLow: '#FF0000', low: '#FF5555', tight: '#00FF55', target: '#AAFF00', high: '#FFAA00',
    veryHigh: '#FF5500', extremelyHigh: '#FF55FF', now: '#AAFF00', rapid: '#55AAFF', long: '#AAAAFF', carbs: '#FFAA55',
    note: '#AAAAAA', on: '#000000', card: '#000000', card2: 'CARD'
  };
  // ---- screen classes (SPEC §14) ------------------------------------------------------------------------------------
  // One reference watch per class, from its device files (SDK 9.3 compiler.json / simulator.json / personality.mss):
  // the screen, the panel, touch, the glance's icon and content areas (px, in the glance row), the data-field slots
  // used for the references, the buttons (round: the angle of each, clockwise from 12; rect: the y of each on the
  // right edge, px from the top). `s` = layout units → px: round layouts are drawn in the 390's units (s = W / 390),
  // rectangular ones in the Venu Sq 2's (s = 1 there, 1.2 on the Venu X1, whose extra room goes to the layout).
  var CLS = {
    r390: { dev: 'descentmk343mm', name: 'Descent Mk3 43 mm', w: 390, h: 390, ppi: 325, touch: true, keys: { start: 60, back: 120, up: 270, down: 240 }, gi: [40, 34, 60, 60], ga: [112, 13, 248, 103] },
    r360: { dev: 'fr265s', name: 'Forerunner 265S', w: 360, h: 360, ppi: 327, touch: true, keys: { start: 63, back: 119, up: 270, down: 241 }, gi: [42, 28, 50, 50], ga: [102, 0, 240, 104] },
    r416: { dev: 'fenix843mm', name: 'fēnix 8 43 mm', w: 416, h: 416, ppi: 326, touch: true, keys: { start: 60, back: 119, up: 270, down: 242 }, gi: [16, 49, 40, 40], ga: [74, 8, 325, 122] },
    r454: { dev: 'fenix847mm', name: 'fēnix 8 47 / 51 mm', w: 454, h: 454, ppi: 326, touch: true, keys: { start: 60, back: 121, up: 269, down: 240 }, gi: [17, 53, 45, 45], ga: [82, 11, 349, 130] },
    r466: { dev: 'fenix9pro51mm', name: 'fēnix 9 Pro 51 mm', w: 466, h: 466, ppi: 310, touch: true, keys: { start: 61, back: 121, up: 268, down: 240 }, gi: [16, 55, 45, 45], ga: [84, 10, 359, 130] },
    m218: { dev: 'fr255s', name: 'Forerunner 255S', w: 218, h: 218, ppi: 202, mip: true, touch: false, keys: { start: 60, back: 119, up: 270, down: 241 }, gi: [22, 16, 40, 40], ga: [69, 0, 140, 79] },
    m240: { dev: 'fenix7s', name: 'fēnix 7S', w: 240, h: 240, ppi: 202, mip: true, touch: true, keys: { start: 60, back: 118, up: 271, down: 242 }, gi: [25, 20, 40, 40], ga: [70, 9, 151, 63] },
    m260: { dev: 'fenix7', name: 'fēnix 7', w: 260, h: 260, ppi: 202, mip: true, touch: true, keys: { start: 61, back: 119, up: 269, down: 240 }, gi: [25, 24, 40, 40], ga: [70, 13, 171, 63] },
    m280: { dev: 'fenix7x', name: 'fēnix 7X', w: 280, h: 280, ppi: 202, mip: true, touch: true, keys: { start: 62, back: 122, up: 268, down: 238 }, gi: [25, 27, 40, 40], ga: [70, 16, 191, 63] },
    sq2: { dev: 'venusq2', name: 'Venu Sq 2', w: 320, h: 360, ppi: 326, rect: true, touch: true, keys: { start: 114, back: 331 }, gi: [0, 45, 40, 40], ga: [66, 8, 254, 114] },
    x1: { dev: 'venux1', name: 'Venu X1', w: 448, h: 486, ppi: 326, rect: true, touch: true, keys: { start: 130, back: 375 }, gi: [34, 56, 65, 65], ga: [112, 14, 314, 150] }
  };
  // the data-field slots of the references, from each watch's own layouts ([x, y, w, h, obscured sides] px): half = the
  // top of 2 Fields, band = the middle of 3 Fields, quarter = the left of a 4-field layout's middle row, cap = that
  // layout's top (on the rectangles a plain band: nothing is obscured). `i` = Sukrara's slot.
  var DFS = {
    r390: { half: { i: 0, f: [[0, 0, 390, 194, 'LTR'], [0, 195, 390, 194, 'LRB']] }, band: { i: 1, f: [[0, 0, 390, 124, 'LTR'], [0, 125, 390, 138, 'LR'], [0, 266, 390, 124, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 390, 139, 'LTR'], [0, 140, 194, 108, 'L'], [195, 140, 194, 108, 'R'], [0, 251, 390, 139, 'LRB']] }, cap: { i: 0, f: [[0, 0, 390, 139, 'LTR'], [0, 140, 194, 108, 'L'], [195, 140, 194, 108, 'R'], [0, 251, 390, 139, 'LRB']] } },
    r360: { half: { i: 0, f: [[0, 0, 360, 179, 'LTR'], [0, 181, 360, 179, 'LRB']] }, band: { i: 1, f: [[0, 0, 360, 116, 'LTR'], [0, 118, 360, 125, 'LR'], [0, 245, 360, 115, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 360, 128, 'LTR'], [0, 130, 179, 107, 'L'], [181, 130, 179, 107, 'R'], [0, 239, 360, 121, 'LRB']] }, cap: { i: 0, f: [[0, 0, 360, 128, 'LTR'], [0, 130, 179, 107, 'L'], [181, 130, 179, 107, 'R'], [0, 239, 360, 121, 'LRB']] } },
    r416: { half: { i: 0, f: [[0, 0, 416, 206, 'LTR'], [0, 209, 416, 207, 'LRB']] }, band: { i: 1, f: [[0, 0, 416, 134, 'LTR'], [0, 137, 416, 144, 'LR'], [0, 284, 416, 132, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 416, 148, 'LTR'], [0, 151, 206, 123, 'L'], [209, 151, 206, 123, 'R'], [0, 277, 416, 139, 'LRB']] }, cap: { i: 0, f: [[0, 0, 416, 148, 'LTR'], [0, 151, 206, 123, 'L'], [209, 151, 206, 123, 'R'], [0, 277, 416, 139, 'LRB']] } },
    r454: { half: { i: 0, f: [[0, 0, 454, 225, 'LTR'], [0, 228, 454, 226, 'LRB']] }, band: { i: 1, f: [[0, 0, 454, 146, 'LTR'], [0, 149, 454, 157, 'LR'], [0, 309, 454, 145, 'LRB']] }, quarter: { i: 2, f: [[0, 0, 454, 161, 'LTR'], [228, 164, 225, 135, 'R'], [0, 164, 225, 135, 'L'], [0, 302, 454, 152, 'LRB']] }, cap: { i: 0, f: [[0, 0, 454, 161, 'LTR'], [228, 164, 225, 135, 'R'], [0, 164, 225, 135, 'L'], [0, 302, 454, 152, 'LRB']] } },
    r466: { half: { i: 0, f: [[0, 0, 466, 231, 'LTR'], [0, 234, 466, 232, 'LRB']] }, band: { i: 1, f: [[0, 0, 466, 150, 'LTR'], [0, 153, 466, 162, 'LR'], [0, 318, 466, 148, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 466, 165, 'LTR'], [0, 168, 231, 139, 'L'], [234, 168, 231, 139, 'R'], [0, 310, 466, 156, 'LRB']] }, cap: { i: 0, f: [[0, 0, 466, 165, 'LTR'], [0, 168, 231, 139, 'L'], [234, 168, 231, 139, 'R'], [0, 310, 466, 156, 'LRB']] } },
    m218: { half: { i: 0, f: [[0, 0, 218, 108, 'LTR'], [0, 110, 218, 108, 'LRB']] }, band: { i: 1, f: [[0, 0, 218, 70, 'LTR'], [0, 72, 218, 75, 'LR'], [0, 149, 218, 69, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 218, 77, 'LTR'], [0, 79, 108, 65, 'L'], [110, 79, 108, 65, 'R'], [0, 146, 218, 72, 'LRB']] }, cap: { i: 0, f: [[0, 0, 218, 77, 'LTR'], [0, 79, 108, 65, 'L'], [110, 79, 108, 65, 'R'], [0, 146, 218, 72, 'LRB']] } },
    m240: { half: { i: 0, f: [[0, 0, 240, 119, 'LTR'], [0, 122, 240, 119, 'LRB']] }, band: { i: 1, f: [[0, 0, 240, 76, 'LTR'], [0, 78, 240, 84, 'LR'], [0, 164, 240, 76, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 240, 85, 'LTR'], [0, 87, 119, 66, 'L'], [121, 87, 119, 66, 'R'], [0, 156, 240, 85, 'LRB']] }, cap: { i: 0, f: [[0, 0, 240, 85, 'LTR'], [0, 87, 119, 66, 'L'], [121, 87, 119, 66, 'R'], [0, 156, 240, 85, 'LRB']] } },
    m260: { half: { i: 0, f: [[0, 0, 260, 129, 'LTR'], [0, 131, 260, 129, 'LRB']] }, band: { i: 1, f: [[0, 0, 260, 82, 'LTR'], [0, 84, 260, 92, 'LR'], [0, 178, 260, 82, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 260, 92, 'LTR'], [0, 94, 129, 72, 'L'], [131, 94, 129, 72, 'R'], [0, 168, 260, 92, 'LRB']] }, cap: { i: 0, f: [[0, 0, 260, 92, 'LTR'], [0, 94, 129, 72, 'L'], [131, 94, 129, 72, 'R'], [0, 168, 260, 92, 'LRB']] } },
    m280: { half: { i: 0, f: [[0, 0, 280, 139, 'LTR'], [0, 141, 280, 139, 'LRB']] }, band: { i: 1, f: [[0, 0, 280, 89, 'LTR'], [0, 91, 280, 98, 'LR'], [0, 191, 280, 89, 'LRB']] }, quarter: { i: 1, f: [[0, 0, 280, 99, 'LTR'], [0, 101, 139, 78, 'L'], [141, 101, 139, 78, 'R'], [0, 181, 280, 99, 'LRB']] }, cap: { i: 0, f: [[0, 0, 280, 99, 'LTR'], [0, 101, 139, 78, 'L'], [141, 101, 139, 78, 'R'], [0, 181, 280, 99, 'LRB']] } },
    sq2: { half: { i: 0, f: [[7, 30, 313, 132, ''], [7, 211, 313, 132, '']] }, band: { i: 1, f: [[0, 17, 320, 97, ''], [0, 132, 320, 97, ''], [0, 248, 320, 97, '']] }, quarter: { i: 1, f: [[0, 17, 320, 97, ''], [0, 139, 157, 97, ''], [160, 139, 160, 97, ''], [0, 256, 320, 97, '']] }, cap: { i: 0, f: [[0, 17, 320, 97, ''], [0, 139, 157, 97, ''], [160, 139, 160, 97, ''], [0, 256, 320, 97, '']] } },
    x1: { half: { i: 0, f: [[0, 0, 448, 241, ''], [0, 244, 448, 242, '']] }, band: { i: 1, f: [[0, 0, 448, 156, ''], [0, 159, 448, 169, ''], [0, 331, 448, 155, '']] }, quarter: { i: 1, f: [[0, 0, 448, 173, ''], [0, 176, 222, 144, ''], [225, 176, 222, 144, ''], [0, 323, 448, 163, '']] }, cap: { i: 0, f: [[0, 0, 448, 173, ''], [0, 176, 222, 144, ''], [225, 176, 222, 144, ''], [0, 323, 448, 163, '']] } }
  };
  // the atlas set: the font sizes (px) a class may use. The 390 keeps its approved sizes exactly; every other class
  // snaps each size (390 size × s) to the nearest of its set, never under the floor (14 px AMOLED, 12 px MIP)
  var ATLAS = {
    amoled: [14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 52, 56, 60, 64, 72, 80, 88, 96, 104, 112, 120, 128, 136, 148],
    mip: [12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 44, 48, 56, 64, 72, 80, 88]
  };
  var KC = null, SE = 1;
  function snapFont(px) {
    if (!KC || KC.id === 'r390') return px;
    var set = KC.mip ? ATLAS.mip : ATLAS.amoled, best = set[0];
    for (var i = 0; i < set.length; i++) if (Math.abs(set[i] - px) < Math.abs(best - px) || (Math.abs(set[i] - px) === Math.abs(best - px) && set[i] > best)) best = set[i];
    return best;
  }
  function fe(fs) { return snapFont(fs * SE) / SE; }
  // stroke widths: AMOLED scales them (never under 1 px); MIP draws whole pixels: hairlines 1, anything else ≥ 2
  function snapStroke(px, w390) {
    if (!KC || KC.id === 'r390') return px;
    if (!KC.mip) return Math.max(1, Math.round(px * 2) / 2);
    return w390 <= 1.6 ? 1 : Math.max(2, Math.round(px));
  }
  var FAM = "'Atkinson Hyperlegible Next', system-ui, sans-serif";
  var KEYS = ' 0123456789.:·ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúñÁÉÍÓÚÑ';
  var WV = [0.28, 0.648, 0.407, 0.548, 0.576, 0.614, 0.588, 0.598, 0.51, 0.615, 0.595, 0.21, 0.21, 0.24, 0.626, 0.613, 0.645, 0.669, 0.569, 0.552, 0.694, 0.694, 0.392, 0.512, 0.631, 0.541, 0.824, 0.693, 0.714, 0.592, 0.724, 0.614, 0.592, 0.558, 0.69, 0.593, 0.856, 0.623, 0.575, 0.607, 0.525, 0.566, 0.485, 0.564, 0.538, 0.314, 0.558, 0.55, 0.252, 0.245, 0.492, 0.262, 0.845, 0.55, 0.548, 0.566, 0.574, 0.349, 0.471, 0.326, 0.54, 0.461, 0.684, 0.481, 0.449, 0.46, 0.525, 0.538, 0.252, 0.548, 0.54, 0.55, 0.626, 0.569, 0.392, 0.714, 0.69, 0.693];
  var WM = {};
  for (var i = 0; i < KEYS.length; i++) WM[KEYS[i]] = WV[i];
  // the big numbers: Atkinson's own figures at weight 600 (SPEC.md §2)
  var BIG = { '0': 0.656, '1': 0.43, '2': 0.569, '3': 0.592, '4': 0.621, '5': 0.602, '6': 0.617, '7': 0.529, '8': 0.621, '9': 0.578, ':': 0.253, '.': 0.253, 'L': 0.548, 'O': 0.737, 'H': 0.699, 'I': 0.415 };
  // widths are measured at the font size the watch really draws (fe: the class's atlas size, in layout units)
  function tw(s, fs) { var t = 0; s = String(s); fs = fe(fs); for (var k = 0; k < s.length; k++) t += WM[s[k]] !== undefined ? WM[s[k]] : 0.6; return t * fs * 1.03; }
  function bw(s, fs) { var t = 0; s = String(s); fs = fe(fs); for (var k = 0; k < s.length; k++) t += BIG[s[k]] !== undefined ? BIG[s[k]] : 0.6; return t * fs; }
  function r1(x) { return Math.round(x * 10) / 10; }
  function clamp(x, a, b) { return Math.max(a, Math.min(b, x)); }
  // Kovatchev's risk transform (Diabetes Care 1997), g in mg/dL; 0 at 112.5
  function K(g) { return 1.509 * (Math.pow(Math.log(g), 1.084) - 5.381); }
  function P(r, a) { var t = a * Math.PI / 180; return [r1(r * Math.sin(t)), r1(-r * Math.cos(t))]; }
  function arc(r, a0, a1, cx, cy) {
    cx = cx || 0; cy = cy || 0;
    var p0 = P(r, a0), p1 = P(r, a1), large = Math.abs(a1 - a0) > 180 ? 1 : 0, sweep = a1 > a0 ? 1 : 0;
    return 'M' + r1(cx + p0[0]) + ' ' + r1(cy + p0[1]) + 'A' + r + ' ' + r + ' 0 ' + large + ' ' + sweep + ' ' + r1(cx + p1[0]) + ' ' + r1(cy + p1[1]);
  }

  var ICON = {
    rapid: { d: 'M2 4.2H22L12 20.9Z', s: false },
    long: { d: 'M3.5 5.4H20.5L12 19.6Z', s: true, sw: 2.8 },
    carbs: { d: 'M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z', s: false },
    sensor: { d: 'M21.3 12a9.3 9.3 0 1 1-18.6 0a9.3 9.3 0 1 1 18.6 0zM18.7 12a6.7 6.7 0 1 0-13.4 0a6.7 6.7 0 1 0 13.4 0zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z', s: false },
    bell: { d: 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0', s: true },
    bellOff: { d: 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0M3 3l18 18', s: true },
    bellPause: { d: 'M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0M10.4 9.5v4.2M13.6 9.5v4.2', s: true },
    heart: { d: 'M12 20.5s-7.5-4.6-7.5-10.4A4.1 4.1 0 0 1 12 7.6a4.1 4.1 0 0 1 7.5 2.5c0 5.8-7.5 10.4-7.5 10.4z', s: false },
    drop: { d: 'M12 2.5C12 2.5 5 10.2 5 14.6a7 7 0 0 0 14 0C19 10.2 12 2.5 12 2.5z', s: false },
    bolt: { d: 'M13.5 2L5.5 13.5h5.2L9.8 22l8.2-12h-5.3z', s: false },
    sun: { d: 'M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0', s: true },
    check: { d: 'M4.5 12.5l4.8 4.8L19.5 7', s: true, sw: 3 },
    plus: { d: 'M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18M12 7.5v9M7.5 12h9', s: true, sw: 2.4 },
    // the app's insulin pen (Android ds_ic_dia_pen: 960-unit grid ÷ 40, moved down 2 to centre)
    pen: { d: 'M18.43 8.43L9.93 16.93A2 2 0 0 1 7.08 14.08L15.58 5.58A2 2 0 0 1 18.43 8.43ZM14 9.5L11.75 11.75M18 6L20 4M7.25 16.75L4.25 19.75', s: true, sw: 2.3 },
    phone: { d: 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z', s: true },
    warn: { d: 'M12 3L22 20H2ZM12 9.5v5M12 17v.6', s: true, sw: 2.2 },
    chart: { d: 'M3 17l5-6 4 3 5-7 4 4', s: true },
    clock: { d: 'M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18M12 7v5l3 2', s: true },
    gear: { d: 'M12 8.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1', s: true },
    steps: { d: 'M4.6 8.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM5.8 16.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0zM12.6 11.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM13.8 19.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z', s: false }
  };

  function bandOf(v) {
    if (v < 55) return 'veryLow';
    if (v < 70) return 'low';
    if (v <= 140) return 'tight';
    if (v < 180) return 'target';
    if (v < 250) return 'high';
    if (v < 300) return 'veryHigh';
    return 'extremelyHigh';
  }

  // ---- the made-up day (clock hours -> mg/dL) -------------------------------------------------------------------
  var BASE = [[8.0, 108], [8.6, 112], [9.15, 150], [9.6, 168], [10.3, 130], [11, 112], [12, 110], [12.7, 96], [13.4, 62], [13.8, 74], [14.3, 104], [14.7, 110], [15.25, 198], [15.6, 206], [16.2, 162], [16.9, 134], [17.4, 149], [17.8, 130], [18.5, 120], [19.2, 114], [19.55, 120]];
  var TAIL = {
    day: [[20.1, 186], [20.35, 190], [20.68, 175]],
    low: [[19.8, 146], [20.1, 118], [20.4, 84], [20.68, 62]],
    veryLow: [[19.8, 140], [20.1, 108], [20.4, 72], [20.68, 49]],
    high: [[19.9, 176], [20.3, 222], [20.68, 252]],
    inRange: [[19.9, 138], [20.3, 126], [20.68, 112]]
  };
  var TREAT = {
    day: [[8.75, 'long', 14], [9.0, 'rapid', 4], [9.0, 'carbs', 40], [13.45, 'carbs', 15], [14.85, 'rapid', 5], [14.85, 'carbs', 60], [19.6, 'rapid', 4.5], [19.6, 'carbs', 50]],
    low: [[8.75, 'long', 14], [9.0, 'rapid', 4], [9.0, 'carbs', 40], [13.45, 'carbs', 15], [14.85, 'rapid', 5], [14.85, 'carbs', 60], [19.6, 'rapid', 6], [19.6, 'carbs', 50]],
    high: [[8.75, 'long', 14], [9.0, 'rapid', 4], [9.0, 'carbs', 40], [13.45, 'carbs', 15], [14.85, 'rapid', 5], [14.85, 'carbs', 60], [19.6, 'carbs', 70]]
  };
  TREAT.veryLow = TREAT.low; TREAT.inRange = TREAT.day;

  function data(c) {
    var scn = TAIL[c.scenario] ? c.scenario : 'day';
    var kf = BASE.concat(TAIL[scn]);
    if (scn === 'inRange') kf = kf.map(function (p) { return [p[0], p[1] > 150 ? 150 - (p[1] - 150) * 0.15 : p[1] < 80 ? 80 + (p[1] - 80) * 0.3 : p[1]]; });
    var nowH = 20 + 41 / 60;
    function at(h) {
      if (h <= kf[0][0]) return kf[0][1];
      for (var k = 1; k < kf.length; k++) {
        if (h <= kf[k][0]) {
          var a = kf[k - 1], b = kf[k], u = (h - a[0]) / (b[0] - a[0]);
          var s = (1 - Math.cos(Math.PI * u)) / 2;
          return a[1] + (b[1] - a[1]) * s + 1.6 * Math.sin(h * 9.1) + 1.1 * Math.sin(h * 23.7);
        }
      }
      return kf[kf.length - 1][1];
    }
    var hist = [];
    for (var n = 288; n >= 0; n--) hist.push({ m: n * 5, v: Math.round(at(nowH - n * 5 / 60)) });
    var g = TAIL[scn][TAIL[scn].length - 1][1];
    hist[hist.length - 1].v = g;
    var rate = { day: -1.1, low: -1.6, veryLow: -2.2, high: 1.7, inRange: 0.2 }[scn];
    if (Number.isFinite(c.rate)) rate = c.rate;
    if (Number.isFinite(c.age) && c.age >= 5) hist = hist.filter(function (p) { return p.m >= Math.floor(c.age / 5) * 5; });
    var tr = (TREAT[scn] || []).map(function (t) { return { m: Math.round((nowH - t[0]) * 60), k: t[1], a: t[2] }; });
    var pill = null;
    if (scn === 'low') pill = { k: 'carbs', text: c.lang === 'es' ? 'Hidratos' : 'Carbs', filled: true };
    if (scn === 'veryLow') pill = { k: 'carbs', text: c.lang === 'es' ? 'Hidratos' : 'Carbs', filled: true };
    if (scn === 'high') pill = { k: 'rapid', text: c.lang === 'es' ? 'Insulina' : 'Insulin', filled: true };
    if (c.pill === 'quiet') pill = { k: 'rapid', text: '44m', filled: false, icon: 'bellPause' };
    if (c.pill === 'muted') pill = { k: 'note', text: '1h 20m', filled: false, icon: 'bellOff' };
    if (c.pill === 'none') pill = null;
    var age = Number.isFinite(c.age) ? c.age : 0.5;
    var rm = 20 * 60 + 41 - Math.floor(age), readAt = Math.floor(rm / 60) + ':' + String(rm % 60).padStart(2, '0');
    return {
      scn: scn, g: c.glucose || g, rate: rate, age: age, hist: hist, tr: tr, pill: pill, unit: c.unit || 'mgdl',
      clock: '20:41', clockH: nowH, sensorDays: 11, readAt: readAt, sensorState: c.sensor || 'ok',
      tir1: { pct: 89, split: [0, 4, 58, 31, 7, 0, 0] },
      tir7: { pct: 85, split: [1, 3, 52, 33, 9, 2, 0] },
      last: lastTreatment(tr), lang: c.lang || 'en',
      slot: watchLast(tr, c), order: kindOrder(scn, c)
    };
  }
  function lastTreatment(tr) {
    var r = null;
    tr.forEach(function (t) { if (t.k === 'rapid' && t.m <= 240 && (!r || t.m < r.m)) r = t; });
    return r;
  }
  // the watch's last treatment: ONE, by priority: rapid still active > long-acting still active > carbs still active >
  // rapid > long > carbs (within 12 h) > none. Active: rapid 4 h, long-acting 24 h, carbs 3 min per gram (30 min – 3 h).
  function watchLast(tr, c) {
    if (c.last === false) return null;
    var cases = {
      long: { k: 'long', m: 130, a: 14 }, carbs: { k: 'carbs', m: 25, a: 30 }, old: { k: 'rapid', m: 420, a: 4 }
    };
    if (c.lastCase && cases[c.lastCase]) return cases[c.lastCase];
    var act = { rapid: function (t) { return 240; }, long: function () { return 1440; }, carbs: function (t) { return Math.min(180, Math.max(30, 3 * t.a)); } };
    var newest = {};
    tr.forEach(function (t) { if (!newest[t.k] || t.m < newest[t.k].m) newest[t.k] = t; });
    var order = ['rapid', 'long', 'carbs'];
    for (var i = 0; i < 3; i++) { var t = newest[order[i]]; if (t && t.m <= act[t.k](t)) return { k: t.k, m: t.m, a: t.a }; }
    for (var j = 0; j < 3; j++) { var u = newest[order[j]]; if (u && u.m <= 720) return { k: u.k, m: u.m, a: u.a }; }
    return null;
  }
  // the kinds' order: ONE rule on the phone (the treatment dialog's focus), sent to the watch: the focused kind first,
  // then the dialog's order Rapid · Long · Carbs
  function kindOrder(scn, c) {
    var focus = c.focus || ((scn === 'low' || scn === 'veryLow') ? 'carbs' : 'rapid');
    return [focus].concat(['rapid', 'long', 'carbs'].filter(function (k) { return k !== focus; }));
  }
  function ago(m) { return m < 1 ? 'now' : m < 60 ? m + 'm' : m < 360 ? Math.floor(m / 60) + 'h' + (m % 60 ? ' ' + (m % 60) + 'm' : '') : Math.round(m / 60) + 'h'; }
  function amt(t) { return (t.k === 'carbs' ? t.a + 'g' : t.a + 'u'); }
  function fmtG(v, unit) {
    if (v < 40) return 'LO';
    if (v > 400) return 'HI';
    return unit === 'mmol' ? (v / 18.0182).toFixed(1) : String(Math.round(v));
  }

  // ---- the display list ------------------------------------------------------------------------------------------
  // The display list. Layouts are written in layout units (the 390's px on round screens, the Venu Sq 2's on
  // rectangles); each element keeps them and carries the transform to the screen's px (`gt`), so a port reads the
  // same numbers × s. Font sizes go to the class's atlas size, strokes to whole px on MIP (snapFont, snapStroke), and
  // on MIP every colour goes through col(): the 64 colours, no alpha (solid, outline or dither, SPEC §14).
  function G() { this.els = []; this.T = [{ s: SE, x: 0, y: 0 }]; this.pats = {}; }
  G.prototype.gt = function () {
    var t = this.T[this.T.length - 1];
    return 'translate(' + r1(t.x) + ' ' + r1(t.y) + ') scale(' + Math.round(t.s * 10000) / 10000 + ')';
  };
  // a nested area (the glance's content box): its own origin and scale, in the current units
  G.prototype.push = function (s, x, y) { var t = this.T[this.T.length - 1]; this.T.push({ s: t.s * s, x: t.x + x * t.s, y: t.y + y * t.s }); SE = t.s * s; };
  G.prototype.pop = function () { this.T.pop(); SE = this.T[this.T.length - 1].s; };
  G.prototype.sw = function (w, raw) { return raw || !w ? w : snapStroke(w * SE, w) / SE; };
  // MIP: one colour of the 64 for a fill / stroke / text with an opacity; returns [colour, opacity, outline]
  G.prototype.col = function (c, op, role) {
    if (!KC || !KC.mip || !c || c === 'none' || c.indexOf('url(') === 0) return [c, op];
    if (c === 'CARD') return ['#000000', 1, '#AAAAAA'];
    if (c.indexOf('DITHER:') === 0) { var q = c.split(':'); return [this.dither(q[1], Number(q[2])), 1]; }
    var rgb = parseCol(c), a = rgb[3] * (op === undefined ? 1 : op);
    if (rgb[0] + rgb[1] + rgb[2] === 0) return ['#000000', 1];
    var full = near64(rgb);
    // a tint (a fill under 60 %): a dither of the tint's colour at 3× its opacity; a checkerboard from 10 %, else one
    // pixel in four (the chart's very-good band 13 %: 005500 50 %; good 7.5 %: 005500 25 %)
    if (role === 'fill' && a < 0.6) {
      var t = near64([rgb[0] * Math.min(1, a * 3), rgb[1] * Math.min(1, a * 3), rgb[2] * Math.min(1, a * 3)]);
      if (t === '#000000') t = near64(rgb.slice(0, 3).map(function (v) { return v === Math.max(rgb[0], rgb[1], rgb[2]) ? 85 : 0; }));
      return [this.dither(t, a >= 0.1 ? 50 : 25), 1];
    }
    if (a >= 0.5) return [role === 'text' && full === '#555555' ? '#AAAAAA' : full, 1];
    var dark = near64([rgb[0] * 0.55, rgb[1] * 0.55, rgb[2] * 0.55]);
    return [dark === '#000000' ? '#555555' : dark, 1];
  };
  // an ordered dither in screen px: 50 % = a checkerboard, 25 % = one pixel in four
  G.prototype.dither = function (hex, pct) {
    var id = 'd' + hex.slice(1) + pct + '_' + Math.round(SE * 1000);
    this.pats[id] = { hex: hex, pct: pct, u: 1 / SE };
    return 'url(#' + id + ')';
  };
  G.prototype.add = function (e) {
    var f = this.col(e.fill, e.op, e.isT ? 'text' : 'fill'), st = this.col(e.stroke, e.op, 'stroke');
    e.fill = f[0]; e.stroke = st[0];
    if (KC && KC.mip) e.op = 1;
    if (f[2]) { e.stroke = f[2]; e.sw = snapStroke(2, 2) / SE; }
    e.gt = this.gt();
    this.els.push(e);
  };
  G.prototype.p = function (d, o) {
    o = o || {};
    this.add({ isP: true, isC: false, isR: false, isT: false, d: d, fill: o.fill || 'none', stroke: o.stroke || 'none', sw: this.sw(o.sw || 0, o.raw), op: o.op === undefined ? 1 : o.op, cap: o.cap || 'round', join: o.join || 'round', dash: o.dash || 'none', tf: o.tf || '', rule: o.rule || 'evenodd' });
  };
  G.prototype.c = function (cx, cy, r, o) {
    o = o || {};
    this.add({ isP: false, isC: true, isR: false, isT: false, cx: r1(cx), cy: r1(cy), r: r1(r), fill: o.fill || 'none', stroke: o.stroke || 'none', sw: this.sw(o.sw || 0), op: o.op === undefined ? 1 : o.op, dash: o.dash || 'none', tf: '' });
  };
  G.prototype.r = function (x, y, w, h, rx, o) {
    o = o || {};
    this.add({ isP: false, isC: false, isR: true, isT: false, x: r1(x), y: r1(y), w: r1(Math.max(0, w)), h: r1(Math.max(0, h)), rx: rx || 0, fill: o.fill || 'none', stroke: o.stroke || 'none', sw: this.sw(o.sw || 0), op: o.op === undefined ? 1 : o.op, tf: '' });
  };
  G.prototype.t = function (x, y, s, o) {
    o = o || {};
    this.add({ isP: false, isC: false, isR: false, isT: true, x: r1(x), y: r1(y), s: [String(s)], fs: Math.round(fe(o.fs || 20) * 1000) / 1000, fw: o.fw || 500, fill: o.fill || PAL.ink, anchor: o.anchor || 'middle', ls: o.ls || 0, op: o.op === undefined ? 1 : o.op, fam: FAM, tf: o.tf || '' });
  };
  function parseCol(c) {
    var m = /^rgba?\(([^)]+)\)/.exec(c);
    if (m) { var v = m[1].split(',').map(Number); return [v[0], v[1], v[2], v.length > 3 ? v[3] : 1]; }
    var h = c.replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 1];
  }
  // the nearest of the 64 (each channel to 00 / 55 / AA / FF)
  function near64(rgb) {
    return '#' + rgb.slice(0, 3).map(function (v) { var q = Math.round(clamp(v, 0, 255) / 85) * 85; return (q < 16 ? '0' : '') + q.toString(16).toUpperCase(); }).join('');
  }
  // WCAG 2 contrast of two colours
  function lum(c) { var v = parseCol(c).slice(0, 3).map(function (x) { x /= 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); }); return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]; }
  function contrast(a, b) { var x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); }
  // the ink on a block of colour: white on AMOLED (Aura's very-low block); on MIP white or black, whichever reads
  // better (black on the light 64-colour reds and pinks: FF5555 white 3.1:1, black 6.7:1)
  function inkOn(col) { if (!KC || !KC.mip) return '#FFFFFF'; return contrast('#FFFFFF', col) >= contrast('#000000', col) ? '#FFFFFF' : '#000000'; }
  G.prototype.icon = function (name, cx, cy, size, col) {
    var ic = ICON[name];
    if (!ic) return;
    var s = size / 24, tf = 'translate(' + r1(cx - size / 2) + ' ' + r1(cy - size / 2) + ') scale(' + r1(s * 1000) / 1000 + ')';
    if (ic.s) this.p(ic.d, { stroke: col, sw: (ic.sw || 2.2), tf: tf, raw: true });
    else this.p(ic.d, { fill: col, tf: tf });
  };
  // Sukrara's arrow (aura.js): pointing right, turned by the rate; doubled from 3 mg/dL/min
  G.prototype.arrow = function (cx, cy, box, rate, col) {
    var s = box * 1.05, q = function (x) { return r1(x * s); };
    var d = 'M' + q(-0.42) + ' ' + q(-0.07) + 'H' + q(0.25) + 'V' + q(0.07) + 'H' + q(-0.42) + 'Z' +
      'M' + q(0.46) + ' 0L' + q(0.06) + ' ' + q(0.3) + 'L' + q(0.16) + ' 0L' + q(0.06) + ' ' + q(-0.3) + 'Z';
    if (Math.abs(rate) >= 3) d += 'M' + q(0.12) + ' 0L' + q(-0.28) + ' ' + q(0.3) + 'L' + q(-0.18) + ' 0L' + q(-0.28) + ' ' + q(-0.3) + 'Z';
    var deg = r1(-clamp(rate / 3, -1, 1) * 90);
    this.p(d, { fill: col, tf: 'translate(' + r1(cx) + ' ' + r1(cy) + ') rotate(' + deg + ')', rule: 'nonzero' });
  };

  function valColour(d, opts) {
    if (d.age >= 5) return PAL.grey;
    if (opts && opts.inverted) return '#FFFFFF';
    return PAL[bandOf(d.g)];
  }
  // the value group's width at a size (as value() lays it out)
  function valueWidth(d, fs) {
    var str = fmtG(d.g, d.unit), arrow = Number.isFinite(d.rate) && str !== 'LO' && str !== 'HI';
    return bw(str, fs) + (arrow ? fs * 0.1 + fs * 0.668 * 0.78 : 0);
  }
  // the value and its arrow as one group; returns its box
  function value(g, d, x, y, fs, o) {
    o = o || {};
    var str = fmtG(d.g, d.unit), vw = bw(str, fs), cap = fs * 0.668, box = cap * 0.78, gap = fs * 0.1;
    var showArrow = o.arrow !== false && Number.isFinite(d.rate) && str !== 'LO' && str !== 'HI';
    var w = vw + (showArrow ? gap + box : 0);
    var x0 = o.anchor === 'start' ? x : o.anchor === 'end' ? x - w : x - w / 2;
    var col = o.col || valColour(d, o);
    g.t(x0, y, str, { fs: fs, fw: 700, fill: col, anchor: 'start' });
    if (d.age >= 5) g.p('M' + r1(x0 - 4) + ' ' + r1(y - cap / 2) + 'H' + r1(x0 + vw + 4), { stroke: PAL.grey, sw: Math.max(3, fs * 0.05) });
    if (showArrow) {
      if (d.age >= 5) g.c(x0 + vw + gap + box / 2, y - cap / 2, box * 0.28, { stroke: PAL.grey, sw: box * 0.1 });
      else g.arrow(x0 + vw + gap + box / 2, y - cap / 2, box, d.rate, o.arrowCol || col);
    }
    return { x0: x0, x1: x0 + w, w: w, top: y - cap, bottom: y, cap: cap };
  }

  // a time-in-range ring (the card's): segments clockwise from 12 in the ring order, the number inside
  function ring(g, cx, cy, r, sw, tir, cap, o) {
    o = o || {};
    var order = o.order || ['tight', 'target', 'high', 'veryHigh', 'extremelyHigh', 'veryLow', 'low'];
    var idx = { veryLow: 0, low: 1, tight: 2, target: 3, high: 4, veryHigh: 5, extremelyHigh: 6 };
    var tot = 0; order.forEach(function (k) { tot += tir.split[idx[k]] || 0; });
    var a = 0, gapDeg = 4;
    g.c(cx, cy, r, { stroke: PAL.grid, sw: sw });
    order.forEach(function (k) {
      var v = tir.split[idx[k]] || 0;
      if (!v) return;
      var span = v / tot * 360;
      var a0 = a + gapDeg / 2, a1 = a + span - gapDeg / 2;
      if (a1 > a0) g.p(arc(r, a0, a1, cx, cy), { stroke: o.dim ? PAL.dim : PAL[k], sw: sw, cap: 'butt' });
      a += span;
    });
    var nf = o.nfs || r * 0.82;
    g.t(cx, cy + (cap ? nf * 0.18 : nf * 0.34), tir.pct, { fs: nf, fw: 700, fill: o.dim ? PAL.soft : PAL.ink });
    if (cap) g.t(cx, cy + nf * 0.18 + r * 0.5, cap, { fs: r * 0.42, fw: 600, fill: PAL.soft });
  }

  function timeSince(m) {
    if (m < 1) return ['now'];
    if (m < 60) return [m + 'm'];
    if (m < 360) return m % 60 ? [Math.floor(m / 60) + 'h', (m % 60) + 'm'] : [Math.floor(m / 60) + 'h'];
    if (m < 2880) return [Math.round(m / 60) + 'h'];
    return [Math.round(m / 1440) + 'd'];
  }
  function lastStack(g, cx, cy, size, t) {
    var parts = timeSince(t.m), col = PAL[t.k], ms = size * 0.3;
    var two = parts.length === 2, f1 = size * (two ? 0.4 : 0.46), f2 = size * 0.32;
    var h = ms + 4 + f1 * 0.72 + (two ? 4 + f2 * 0.72 : 0), y0 = cy - h / 2;
    if (t.k === 'carbs') g.c(cx, y0 + ms / 2, ms * 0.4, { fill: col });
    else g.p('M' + r1(cx - ms / 2) + ' ' + r1(y0 + ms * 0.08) + 'H' + r1(cx + ms / 2) + 'L' + r1(cx) + ' ' + r1(y0 + ms * 0.92) + 'Z', t.k === 'long' ? { stroke: col, sw: 2 } : { fill: col });
    var yb = y0 + ms + 4 + f1 * 0.72;
    g.t(cx, yb, parts[0], { fs: f1, fw: 700, fill: PAL.ink });
    if (two) g.t(cx, yb + 4 + f2 * 0.72, parts[1], { fs: f2, fw: 600, fill: PAL.soft });
  }
  // the card's places in priority: main, last treatment (never dimmed), second (dimmed), third
  function metricSlots(d, c) {
    if (d.mets) return d.mets;
    var list = [{ kind: 'ring', tir: d.tir1 }];
    if (d.slot) list.push({ kind: 'last', slot: d.slot });
    list.push({ kind: 'ring', tir: d.tir7, dim: true });
    return list;
  }
  function drawSlot(g, m, cx, cy, r, sw, nfs) {
    if (m.kind === 'last') lastStack(g, cx, cy, 2 * r + sw, m.slot);
    else ring(g, cx, cy, r, sw, m.tir, '', { dim: m.dim, nfs: nfs, order: m.order });
  }
  function pill(g, x, y, p, o) {
    o = o || {};
    var fs = o.fs || 22, h = fs * 1.36, iw = fs * 0.78, padL = fs * 0.5, padR = fs * 0.6;
    var w = padL + iw + fs * 0.3 + tw(p.text, fs) + padR;
    var x0 = o.anchor === 'start' ? x : o.anchor === 'end' ? x - w : x - w / 2;
    var col = PAL[p.k] || PAL.note;
    // tonal on AMOLED (the kind's colour at 24 %); MIP has no tint that keeps the text readable: an outline
    if (KC && KC.mip && !p.filled) g.r(x0, y - h / 2, w, h, h / 2, { stroke: col, sw: 2 });
    else g.r(x0, y - h / 2, w, h, h / 2, { fill: col, op: p.filled ? 1 : 0.24 });
    var fg = p.filled ? PAL.on : col;
    g.icon(p.icon || p.k, x0 + padL + iw / 2, y, iw, fg);
    g.t(x0 + padL + iw + fs * 0.3, y + fs * 0.35, p.text, { fs: fs, fw: 600, fill: fg, anchor: 'start' });
    return w;
  }

  // the chart: bands, the line in its range colours, the now bead, the treatment marks and the hours
  function chart(g, d, bx, o) {
    o = o || {};
    // the height is Kovatchev's symmetrised scale (as Sukrara Aura's rail) over 40–300, where it is symmetric: lows
    // 27 %, the range 47 %, highs 26 %, 70 and 180 equally far from the middle; above 300 pins to the top
    var hours = o.hours || 12, lo = 40, hi = 300;
    var pts = d.hist.filter(function (p) { return p.m <= hours * 60; });
    var X = function (m) { return bx.x1 - (m / (hours * 60)) * (bx.x1 - bx.x0); };
    var Y = o.linear
      ? function (v) { return bx.y1 - (clamp(v, 40, 260) - 40) / 220 * (bx.y1 - bx.y0); }
      : function (v) { return bx.y1 - (K(clamp(v, lo, hi)) - K(lo)) / (K(hi) - K(lo)) * (bx.y1 - bx.y0); };
    g.r(bx.x0, Y(140), bx.x1 - bx.x0, Y(70) - Y(140), 0, { fill: PAL.tight, op: 0.13 });
    g.r(bx.x0, Y(180), bx.x1 - bx.x0, Y(140) - Y(180), 0, { fill: PAL.target, op: 0.075 });
    if (o.grid !== false) {
      [70, 180].forEach(function (v) { g.p('M' + r1(bx.x0) + ' ' + r1(Y(v)) + 'H' + r1(bx.x1), { stroke: PAL.faint, sw: 1, dash: '3 4' }); });
    }
    if (o.yLabels !== false) {
      [70, 180].forEach(function (v) { g.t(bx.x1 + 5, Y(v) + 5, d.unit === 'mmol' ? (v / 18.0182).toFixed(1) : v, { fs: o.lfs || 15, fw: 500, fill: PAL.dim, anchor: 'start' }); });
    }
    if (o.under) o.under({ X: X, Y: Y });
    // the line: one path per run of one colour
    var runs = [], cur = null;
    for (var k = 1; k < pts.length; k++) {
      var a = pts[k - 1], b = pts[k], col = PAL[bandOf((a.v + b.v) / 2)];
      if (!cur || cur.col !== col) { cur = { col: col, d: 'M' + r1(X(a.m)) + ' ' + r1(Y(a.v)) }; runs.push(cur); }
      cur.d += 'L' + r1(X(b.m)) + ' ' + r1(Y(b.v));
    }
    var lw = o.lw || 3.4;
    runs.forEach(function (rn) { g.p(rn.d, { stroke: d.age >= 5 ? PAL.grey : rn.col, sw: lw }); });
    var last = pts[pts.length - 1];
    if (o.bead !== false && last) g.c(X(last.m), Y(last.v), lw * 1.9, { fill: d.age >= 5 ? '#000000' : PAL[bandOf(last.v)], stroke: d.age >= 5 ? PAL.grey : '#000000', sw: d.age >= 5 ? 2 : 2.4 });
    // treatments
    var marks = [];
    if (o.marks !== false) {
      d.tr.filter(function (t) { return t.m <= hours * 60; }).forEach(function (t) {
        var x = X(t.m), y = bx.y1 - 7, col = PAL[t.k], ms = o.ms || 11;
        if (t.k === 'rapid') g.p('M' + r1(x - ms / 2) + ' ' + r1(y - ms * 0.42) + 'H' + r1(x + ms / 2) + 'L' + r1(x) + ' ' + r1(y + ms * 0.45) + 'Z', { fill: col });
        if (t.k === 'long') g.p('M' + r1(x - ms / 2) + ' ' + r1(y - ms * 0.42) + 'H' + r1(x + ms / 2) + 'L' + r1(x) + ' ' + r1(y + ms * 0.45) + 'Z', { stroke: col, sw: 2 });
        if (t.k === 'carbs') g.c(x - (d.tr.some(function (u) { return u.k !== 'carbs' && u.m === t.m; }) ? -0 : 0), y - (d.tr.some(function (u) { return u.k !== 'carbs' && u.m === t.m; }) ? ms * 1.3 : 0), ms * 0.4, { fill: col });
        if (t.k !== 'carbs' && o.markLabels !== false) marks.push({ x: x, t: t });
      });
    }
    // hour labels (24 h), skipping the ones a treatment label touches
    var lfs = o.lfs || 15, ly = bx.y1 + lfs + 5;
    var labels = [];
    marks.sort(function (a, b) { return b.x - a.x; }).forEach(function (mk) {
      var s = amt(mk.t), w = tw(s, lfs), L = { x0: mk.x - w / 2, x1: mk.x + w / 2 };
      if (labels.some(function (o) { return L.x1 + 4 > o.x0 && L.x0 - 4 < o.x1; })) return;
      labels.push(L);
      g.t(mk.x, ly, s, { fs: lfs, fw: 600, fill: PAL[mk.t.k] });
    });
    if (o.hoursRow !== false) {
      var step = hours >= 12 ? 3 : hours >= 6 ? 2 : 1;
      var nowH = d.clockH;
      for (var hh = Math.ceil(nowH - hours); hh <= nowH; hh++) {
        if (((hh % 24) + 24) % 24 % step) continue;
        var m = (nowH - hh) * 60, x = X(m);
        if (x < bx.x0 + 8 || x > bx.x1 - 8) continue;
        var s = String(((hh % 24) + 24) % 24), w = tw(s, lfs);
        if (labels.some(function (L) { return x + w / 2 + 4 > L.x0 && x - w / 2 - 4 < L.x1; })) continue;
        g.t(x, ly, s, { fs: lfs, fw: 500, fill: PAL.dim });
      }
    }
    return { X: X, Y: Y, lo: lo, hi: hi };
  }

  // the % column: the chart window's readings by value, drawn inside the plot from its left edge, under the line (as
  // the widget's dot histogram), the share above / in / below the range left of the plot
  function pctColumn(g, d, sc, bx, o) {
    o = o || {};
    var hours = o.hours || 12, pts = d.hist.filter(function (p) { return p.m <= hours * 60; });
    var bins = {}, max = 0, n = pts.length, above = 0, below = 0, maxLen = (bx.x1 - bx.x0) * (o.share || 0.22);
    pts.forEach(function (p) {
      var b = Math.floor(clamp(p.v, 40, 299) / 10) * 10;
      bins[b] = (bins[b] || 0) + 1; if (bins[b] > max) max = bins[b];
      if (p.v >= 180) above++; else if (p.v < 70) below++;
    });
    Object.keys(bins).forEach(function (k) {
      var v = Number(k), y0 = sc.Y(v + 10), y1 = sc.Y(v), len = (bins[k] / max) * maxLen;
      if (o.mask) {
        // the row's own background over the line: black, then the band's tint where the row lies in a band
        var mx1 = bx.x0 + Math.ceil(len / (o.dot || 5)) * (o.dot || 5) + 1.5;
        g.r(bx.x0, y0, mx1 - bx.x0, y1 - y0, 0, { fill: '#000000' });
        [[70, 140, PAL.tight, 0.13], [140, 180, PAL.target, 0.075]].forEach(function (bd) {
          var t0 = Math.max(y0, sc.Y(bd[1])), t1 = Math.min(y1, sc.Y(bd[0]));
          if (t1 > t0) g.r(bx.x0, t0, mx1 - bx.x0, t1 - t0, 0, { fill: bd[2], op: bd[3] });
        });
      }
      // dots, as the widget draws them: a row per bin, one dot per step
      var step = o.dot || 5, r = Math.min(step * 0.36, Math.max(0.8, (y1 - y0) * 0.42)), cy = (y0 + y1) / 2;
      var dd = '';
      for (var x = bx.x0 + step / 2; x <= bx.x0 + len; x += step) dd += 'M' + r1(x - r) + ' ' + r1(cy) + 'a' + r1(r) + ' ' + r1(r) + ' 0 1 0 ' + r1(2 * r) + ' 0a' + r1(r) + ' ' + r1(r) + ' 0 1 0 ' + r1(-2 * r) + ' 0';
      if (dd) g.p(dd, { fill: PAL[bandOf(v + 5)], op: 0.95 });
    });
    var pa = Math.round(above / n * 100), pb = Math.round(below / n * 100), pin = 100 - pa - pb;
    var lfs = o.lfs || 15, lx = bx.x0 - 5;
    if (pa) g.t(lx, Math.min(sc.Y(230), sc.Y(180) - 2) + lfs * 0.35, pa + '%', { fs: lfs, fw: 600, fill: PAL.soft, anchor: 'end' });
    g.t(lx, (sc.Y(70) + sc.Y(180)) / 2 + lfs * 0.35, pin + '%', { fs: lfs + 1, fw: 700, fill: PAL.ink, anchor: 'end' });
    if (pb || o.always) g.t(lx, Math.max(sc.Y(55), sc.Y(70) + lfs * 0.6) + lfs * 0.35, pb + '%', { fs: lfs, fw: 600, fill: PAL.soft, anchor: 'end' });
  }

  // Sukrara Aura's sparkline (SPEC §6.5), scaled by sc: a log time axis (the newest 10 min ≈ 36 px, up to 3 h as the
  // width allows), a fixed 0.6 px per mg/dL that never rescales, the window panning on the last hour, overflow fading,
  // the limits dotted with the very-good band between, the trend ray and the end dot
  function auraSpark(g, d, bx, o) {
    o = o || {};
    var sc = o.sc || 1, K = 100 / Math.log(7) * sc, SH = bx.y1 - bx.y0, ppm = 0.6 * sc, L = 70, H = 180;
    var spanMin = Math.min(180, 10 * (Math.exp((bx.x1 - bx.x0 - 24 * sc) / K) - 1));
    var xNow = bx.x1 - 24 * sc;
    var X = function (age) { return xNow - K * Math.log(1 + age / 10); };
    var pts = d.hist.filter(function (p) { return p.m <= spanMin; }), last = pts[pts.length - 1].v;
    var hour = d.hist.filter(function (p) { return p.m <= 60; }), hLo = 999, hHi = 0;
    hour.forEach(function (p) { hLo = Math.min(hLo, p.v); hHi = Math.max(hHi, p.v); });
    var span = SH / ppm, mid;
    if (Math.max(hHi, H) - Math.min(hLo, L) <= span) mid = (Math.max(hHi, H) + Math.min(hLo, L)) / 2;
    else mid = clamp((hLo + hHi) / 2, last - 0.4 * span, last + 0.4 * span);
    var y0 = function (v) { return bx.y0 + SH / 2 - (v - mid) * ppm; };
    var B = 12 * sc;
    var Y = function (v) {
      var y = y0(v);
      if (y < bx.y0) return bx.y0 - B * (1 - Math.exp((y - bx.y0) / B));
      if (y > bx.y1) return bx.y1 + B * (1 - Math.exp((bx.y1 - y) / B));
      return y;
    };
    var OP = function (v) {
      var y = y0(v), out = y < bx.y0 ? bx.y0 - y : y > bx.y1 ? y - bx.y1 : 0;
      if (out <= 0) return 1;
      if (out <= 6 * sc) return 1 - 0.55 * out / (6 * sc);
      return Math.max(0.15, 0.45 - 0.3 * (out - 6 * sc) / (6 * sc));
    };
    var x0 = X(spanMin);
    // band and limits
    var yH = Y(H), yL = Y(L);
    var WI = o.ink || '#FFFFFF';
    if (!(o.white && WI === '#000000')) g.r(x0, Math.min(yH, yL), xNow - x0, Math.abs(yL - yH), 0, { fill: o.white ? WI : PAL.tight, op: o.white ? 0.07 : 0.09 });
    g.p('M' + r1(x0) + ' ' + r1(yH) + 'H' + r1(xNow), { stroke: '#5C5C62', sw: 1.4 * sc, dash: r1(1.5 * sc) + ' ' + r1(4.5 * sc), op: OP(H) });
    g.p('M' + r1(x0) + ' ' + r1(yL) + 'H' + r1(xNow), { stroke: '#5C5C62', sw: 1.4 * sc, dash: r1(1.5 * sc) + ' ' + r1(4.5 * sc), op: OP(L) });
    // the line, a piece per pair so each fades by its height
    var old = d.age >= 5, lcol = o.white ? (o.ink === '#000000' ? '#000000' : 'rgba(255,255,255,0.85)') : old ? PAL.dim : PAL.soft;
    for (var k = 1; k < pts.length; k++) {
      var a = pts[k - 1], b = pts[k], op = Math.min(OP(a.v), OP(b.v));
      g.p('M' + r1(X(a.m + d.age)) + ' ' + r1(Y(a.v)) + 'L' + r1(X(b.m + d.age)) + ' ' + r1(Y(b.v)), { stroke: lcol, sw: 5.4 * sc, op: op });
    }
    var ex = X(d.age), ey = Y(last), col = valColour(d, {});
    if (!old && Number.isFinite(d.rate)) {
      var ang = -clamp(d.rate / 3, -1, 1) * Math.PI / 2, len = 22 * sc;
      g.p('M' + r1(ex) + ' ' + r1(ey) + 'L' + r1(ex + len * Math.cos(ang)) + ' ' + r1(ey + len * Math.sin(ang)), { stroke: o.white ? WI : bandOf(d.g) === 'veryLow' ? PAL.veryLow : col, sw: 3.4 * sc, dash: r1(4.5 * sc) + ' ' + r1(4.5 * sc), cap: 'butt' });
    }
    if (!old) g.c(ex, ey, 7.5 * sc, { fill: o.white ? WI : PAL[bandOf(d.g)] });
    else g.c(ex, ey, 7.5 * sc, { fill: '#000000', stroke: PAL.grey, sw: 3 * sc });
  }

  function sparkline(g, d, bx, o) {
    o = o || {};
    var hours = o.hours || 3, pts = d.hist.filter(function (p) { return p.m <= hours * 60; });
    var lo = 40, hi = 260;
    var X = function (m) { return bx.x1 - (m / (hours * 60)) * (bx.x1 - bx.x0); };
    var Y = function (v) { return bx.y1 - (clamp(v, lo, hi) - lo) / (hi - lo) * (bx.y1 - bx.y0); };
    g.r(bx.x0, Y(180), bx.x1 - bx.x0, Y(70) - Y(180), 3, { fill: PAL.tight, op: 0.12 });
    var dd = '';
    pts.forEach(function (p, i) { dd += (i ? 'L' : 'M') + r1(X(p.m)) + ' ' + r1(Y(p.v)); });
    g.p(dd, { stroke: d.age >= 5 ? PAL.grey : (o.col || PAL.soft), sw: o.sw || 3 });
    var l = pts[pts.length - 1];
    g.c(X(l.m), Y(l.v), (o.sw || 3) * 1.8, { fill: d.age >= 5 ? '#000' : PAL[bandOf(l.v)], stroke: d.age >= 5 ? PAL.grey : '#000', sw: 2 });
  }

  function clock(g, d, y, o) { o = o || {}; g.t(o.x || 0, y, d.clock, { fs: o.fs || 24, fw: 600, fill: o.fill || PAL.soft }); }
  function sensorTag(g, d, x, y, o) {
    o = o || {}; var fs = o.fs || 18, s = d.sensorDays + 'd', w = fs + 4 + tw(s, fs);
    var x0 = o.anchor === 'end' ? x - w : o.anchor === 'start' ? x : x - w / 2;
    g.icon('sensor', x0 + fs / 2, y - fs * 0.34, fs, PAL.soft);
    g.t(x0 + fs + 4, y, s, { fs: fs, fw: 600, fill: PAL.soft, anchor: 'start' });
    return w;
  }
  function lastTag(g, d, x, y, o) {
    o = o || {}; var t = d.last; if (!t) return 0;
    var fs = o.fs || 18, a = amt(t), s = ago(t.m), w = fs * 0.8 + 4 + tw(a, fs) + 6 + tw(s, fs);
    var x0 = o.anchor === 'end' ? x - w : o.anchor === 'start' ? x : x - w / 2;
    g.icon(t.k, x0 + fs * 0.4, y - fs * 0.34, fs * 0.8, PAL[t.k]);
    g.t(x0 + fs * 0.8 + 4, y, a, { fs: fs, fw: 600, fill: PAL[t.k], anchor: 'start' });
    g.t(x0 + fs * 0.8 + 4 + tw(a, fs) + 6, y, s, { fs: fs, fw: 500, fill: PAL.soft, anchor: 'start' });
    return w;
  }
  // the page dots: at the bottom on round screens; on rectangles in the bottom-left corner (the bottom row's middle is
  // the pill's)
  function pageDots(g, n, i) {
    for (var k = 0; k < n; k++) {
      var o = (k - (n - 1) / 2) * 13, r = k === i ? 4 : 3, st = { fill: k === i ? PAL.ink : PAL.dim };
      if (KC && KC.rect) g.c(-KC.hw + 24 + o, KC.hh - 9, r, st); else g.c(o, 180, r, st);
    }
  }
  // a hint at a button. Round: a short arc on the rim at the button's angle (the 390: START 60°, BACK 120°, DOWN 240°,
  // UP 270°; every class takes its own angles from its device file). Rectangles: a bar on the right edge at the
  // button's height (START top, BACK bottom; no UP / DOWN there). `cfg.buttons: 'two'` (the Venu / vívoactive /
  // Approach S50 / S72 / D2 Air X10 rounds) has no UP / DOWN either.
  function btnHint(g, ang, label, o) {
    o = o || {}; var col = o.col || PAL.ink;
    var name = { 60: 'start', 120: 'back', 270: 'up', 240: 'down' }[ang];
    if (KC && (KC.rect || KC.two) && (name === 'up' || name === 'down')) return;
    if (KC && KC.rect) {
      var y = KC.keys[name] / KC.s - KC.hh;
      g.p('M' + r1(KC.hw - 3) + ' ' + r1(y - 20) + 'V' + r1(y + 20), { stroke: col, sw: 5, op: o.op || 0.9 });
      return;
    }
    if (KC && KC.keys[name] !== undefined) ang = KC.keys[name];
    g.p(arc(190, ang - 9, ang + 9), { stroke: col, sw: 5, op: o.op || 0.9 });
    if (label) {
      var p = P(o.r || 160, ang), fs = o.fs || 18;
      g.t(p[0] + (o.dx || 0), p[1] + fs * 0.35 + (o.dy || 0), label, { fs: fs, fw: 600, fill: col, anchor: o.anchor || (ang < 180 ? 'end' : 'start') });
    }
  }
  // the screen's edge: a ring on round screens; on rectangles a rounded rectangle 6 units in. `p` < 1 draws that share
  // of it clockwise from START (hold to log fills from the button that is held)
  function rim(g, col, sw, p) {
    KC.rimPx = Math.max(KC.rimPx || 0, (6 + sw / 2) * KC.s);
    if (!KC.rect) {
      var a0 = KC.keys.start;
      if (p >= 1) g.p(arc(189, 0, 359.9), { stroke: col, sw: sw });
      else g.p(arc(189, a0, a0 + 359.9 * p), { stroke: col, sw: sw, cap: 'round' });
      return;
    }
    var i = 6, x0 = -KC.hw + i, x1 = KC.hw - i, y0 = -KC.hh + i, y1 = KC.hh - i, rc = 22, ys = KC.keys.start / KC.s - KC.hh;
    var d = 'M' + r1(x1) + ' ' + r1(ys) + 'V' + r1(y1 - rc) + 'A' + rc + ' ' + rc + ' 0 0 1 ' + r1(x1 - rc) + ' ' + r1(y1) + 'H' + r1(x0 + rc) +
      'A' + rc + ' ' + rc + ' 0 0 1 ' + r1(x0) + ' ' + r1(y1 - rc) + 'V' + r1(y0 + rc) + 'A' + rc + ' ' + rc + ' 0 0 1 ' + r1(x0 + rc) + ' ' + r1(y0) +
      'H' + r1(x1 - rc) + 'A' + rc + ' ' + rc + ' 0 0 1 ' + r1(x1) + ' ' + r1(y0 + rc) + 'V' + r1(ys);
    var L = 2 * (x1 - x0 + y1 - y0) - 8 * rc + 2 * Math.PI * rc;
    g.p(d, { stroke: col, sw: sw, cap: p >= 1 ? 'butt' : 'round', dash: p >= 1 ? 'none' : r1(L * p) + ' ' + r1(L) });
  }
  // does a box (layout units) sit inside the screen, clear of the rim, 2 px in?
  function inside(x0, y0, x1, y1) {
    var m = ((KC.rimPx || 0) + 2) / KC.s;
    if (KC.rect) return x0 >= -KC.hw + m && x1 <= KC.hw - m && y0 >= -KC.hh + m && y1 <= KC.hh - m;
    var R = 195 - m;
    return [[x0, y0], [x1, y0], [x0, y1], [x1, y1]].every(function (q) { return q[0] * q[0] + q[1] * q[1] <= R * R; });
  }
  // the whole screen in one colour (the very-low block, the field's alert)
  function full(g, col, op) { g.r(-KC.hw - 2, -KC.hh - 2, 2 * KC.hw + 4, 2 * KC.hh + 4, 0, { fill: col, op: op }); }

  // ---- the screens --------------------------------------------------------------------------------------------
  var S = {};

  // A · the widget, one screen: rings flank the value, the chart with its % column, sensor and last treatment below
  // the app's message where the value would be (no value): Aura's words
  function noValue(g, d, y) {
    if (d.sensorState === 'warmup') {
      var tw0 = tw('Warming up', 26), tot = 34 + 12 + tw0, mx = -tot / 2;
      g.c(mx + 17, y - 12, 17, { stroke: PAL.soft, sw: 2.5 });
      g.p('M' + r1(mx + 17) + ' ' + r1(y - 12) + 'L' + r1(mx + 17) + ' ' + r1(y - 24.5) + arc(12.5, 0, 0.6 * 360, mx + 17, y - 12).replace('M' + r1(mx + 17) + ' ' + r1(y - 24.5), '') + 'Z', { fill: PAL.soft });
      g.t(mx + 46, y - 3, 'Warming up', { fs: 26, fw: 600, fill: PAL.ink, anchor: 'start' });
      g.t(0, y + 30, 'Ready at ' + (d.readyAt || '21:20'), { fs: 20, fw: 500, fill: PAL.soft });
      return;
    }
    g.t(0, y - 26, 'No reading since', { fs: 22, fw: 600, fill: PAL.soft });
    g.t(0, y + 20, d.readAt, { fs: 48, fw: 600, fill: PAL.ink });
  }
  function chipRow(g, d, y, o) {
    o = o || {};
    var fs = o.fs || 20, late = d.age >= 1.5 && d.age < 20, ageS = late ? d.readAt : '', aw = late ? tw(ageS, fs) : 0;
    var pw = 0, pillOn = d.pill && d.age < 5;
    if (pillOn) { var fs2 = fs, h = fs2 * 1.36, iw = fs2 * 0.78; pw = fs2 * 0.5 + iw + fs2 * 0.3 + tw(d.pill.text, fs2) + fs2 * 0.6; }
    var gap = late && pillOn ? 12 : 0, tot = aw + gap + pw, x0 = -tot / 2;
    if (late) g.t(x0, y + fs * 0.35, ageS, { fs: fs, fw: 500, fill: PAL.soft, anchor: 'start' });
    if (pillOn) pill(g, x0 + aw + gap, y, d.pill, { fs: fs, anchor: 'start' });
    return tot > 0;
  }
  S.appA = function (g, d, c) {
    var dr = drops();
    if (!dr.clock) clock(g, d, -150, { fs: 22 });
    if (d.age >= 20 || d.sensorState === 'warmup') noValue(g, d, -66);
    else value(g, d, 0, -58, 88);
    var ms = metricSlots(d, c);
    drawSlot(g, ms[1], -142, -82, 25, 6, 22);
    drawSlot(g, ms[0], 142, -82, 25, 6, 22);
    // without the % column and the 70 / 180 labels the plot takes their room
    var bx = { x0: dr.dist ? -150 : -134, x1: dr.yLabels ? 158 : 146, y0: -22, y1: 88 };
    var sc = chart(g, d, bx, { hours: c.hours || 12, lfs: 14, ms: 10, yLabels: !dr.yLabels });
    // the % column over the line's start, masking it (the widget's)
    if (c.dist !== false && !dr.dist) pctColumn(g, d, sc, bx, { lfs: 14, hours: c.hours || 12, dot: 4.6, mask: true });
    // the bottom row: Sukrara's pill (or the reading's time), else the sensor's days (the last treatment is in its ring)
    if (!chipRow(g, d, 146, { fs: 19 }) && !dr.sensor) sensorTag(g, d, 0, 152, { fs: 17 });
    if (c.dots !== false) pageDots(g, 2, 0);
  };

  // the numbers: the phone's Glucose report, simplified (one period, the three shares with their goals, average, GMI, CV)
  S.appNums = function (g, d, c) {
    var per = c.period || '14d', pers = ['1d', '7d', '14d', '30d'];
    var cw = 50, gp = 6, x0 = -(pers.length * cw + (pers.length - 1) * gp) / 2;
    pers.forEach(function (p, i) {
      var on = p === per, x = x0 + i * (cw + gp);
      g.r(x, -152, cw, 30, 15, { fill: on ? PAL.card2 : 'none', stroke: on ? PAL.soft : PAL.faint, sw: 1.5 });
      g.t(x + cw / 2, -131, p, { fs: 17, fw: on ? 700 : 500, fill: on ? PAL.ink : PAL.soft });
    });
    // the report of the period (d.rep: the shares as shown, the bands' split, each goal met or not, the three numbers)
    var R = d.rep || { above: '14 %', inR: '85 %', good: '64 %', below: '0.1 %', split: [0, 0.1, 64, 21, 14, 0.8, 0], met: [true, true, true],
      nums: [['136', 'Average', true], ['6.6 %', 'GMI', true], ['27 %', 'CV', true]] };
    // the stacked bar (the six bands, very high on top)
    var bx = -142, by0 = -112, by1 = 64, tot = 0, keys = ['veryLow', 'low', 'tight', 'target', 'high', 'veryHigh', 'extremelyHigh'];
    R.split.forEach(function (v) { tot += v; });
    var y = by1;
    keys.forEach(function (k, i) {
      var v = R.split[i]; if (!v) return;
      var h = v / tot * (by1 - by0);
      g.r(bx, y - h + (h > 3 ? 1 : 0), 12, Math.max(1.5, h - (h > 3 ? 2 : 0)), 2, { fill: PAL[k] });
      y -= h;
    });
    function goal(x, yy, text, met) {
      var w = 26 + tw(text, 14), col = met ? PAL.tight : PAL.high;
      g.r(x, yy - 15, w, 22, 11, { fill: met ? '#173A27' : '#3D2A10' });
      if (met) g.p('M' + r1(x + 8) + ' ' + r1(yy - 4.5) + 'l3.5 3.5l6 -7', { stroke: col, sw: 2 });
      else g.p('M' + r1(x + 9) + ' ' + r1(yy - 9) + 'l7 7M' + r1(x + 16) + ' ' + r1(yy - 9) + 'l-7 7', { stroke: col, sw: 2 });
      g.t(x + 21, yy + 1, text, { fs: 14, fw: 600, fill: col, anchor: 'start' });
    }
    function row(yy, label, pct, gtext, big, sub, met) {
      g.t(-116, yy, label, { fs: big ? 22 : 19, fw: big ? 700 : 600, fill: PAL.ink, anchor: 'start' });
      g.t(152, yy + (big ? 4 : 0), pct, { fs: big ? 32 : 22, fw: 700, fill: PAL.ink, anchor: 'end' });
      if (gtext) goal(-116, yy + 22, gtext, met !== false);
      if (sub) g.t(-116 + 26 + tw(gtext, 14) + 10, yy + 23, sub, { fs: 15, fw: 500, fill: PAL.soft, anchor: 'start' });
    }
    row(-96, 'Above range', R.above, '< 25%', false, '', R.met[0]);
    row(-30, 'In range', R.inR, '> 70%', true, 'very good ' + R.good, R.met[1]);
    row(38, 'Below range', R.below, '< 4%', false, '', R.met[2]);
    g.p('M-140 80H140', { stroke: PAL.grid, sw: 1.5 });
    R.nums.forEach(function (m, i) {
      var x = -92 + i * 92;
      g.t(x, 114, m[0], { fs: 24, fw: 700, fill: PAL.ink });
      var lw = tw(m[1], 15) + 18;
      if (m[2]) g.p('M' + r1(x - lw / 2) + ' 131l3.2 3.2l5.5 -6.5', { stroke: PAL.tight, sw: 2 });
      else g.p('M' + r1(x - lw / 2 + 1) + ' 127.6l6 6M' + r1(x - lw / 2 + 7) + ' 127.6l-6 6', { stroke: PAL.high, sw: 2 });
      g.t(x - lw / 2 + 14, 137, m[1], { fs: 15, fw: 600, fill: PAL.soft, anchor: 'start' });
    });
    if (c.dots !== false) pageDots(g, 2, 1);
  };

  // ---- the glance, in the glance loop --------------------------------------------------------------------------
  // The Descent Mk3 43's glance: the app's icon in a 60 × 60 area (drawn by the watch), the app's own content in
  // 248 × 103 to its right. On screen (centre origin): icon centred at (−125, 0), content x −83…165, y −51…52.
  function appIcon(g, cx, cy, r, op) {
    g.c(cx, cy, r, { fill: '#127C95', op: op === undefined ? 1 : op });
    var s = (r * 2) / 49.64 * 0.78, ox = cx - (27.61 + 24.82) * s, oy = cy - (31.36 + 24.82) * s;
    var tf = 'translate(' + r1(ox) + ' ' + r1(oy) + ') scale(' + r1(s * 1000) / 1000 + ')';
    g.p('M45.09,45.19Q47.00,44.09 48.91,45.19L57.93,50.40Q59.83,51.50 57.93,52.60L48.91,57.81Q47.00,58.91 45.09,57.81L36.07,52.60Q34.17,51.50 36.07,50.40ZM32.36,56.84Q32.36,54.63 34.26,55.73L43.28,60.95Q45.19,62.05 45.19,64.25L45.19,74.66Q45.19,76.86 43.28,75.77L34.26,70.55Q32.36,69.45 32.36,67.25ZM48.81,64.25Q48.81,62.05 50.72,60.95L59.74,55.73Q61.64,54.63 61.64,56.84L61.64,67.25Q61.64,69.45 59.74,70.55L50.72,75.77Q48.81,76.86 48.81,74.66Z', { fill: '#FFFFFF', tf: tf, op: op === undefined ? 1 : op });
    g.p('M59.50,42.00A6.50,6.50 0 1,0 72.50,42.00A6.50,6.50 0 1,0 59.50,42.00Z', { fill: PAL.now, tf: tf, op: op === undefined ? 1 : op });
  }
  // The glance row as the watch lays it out (KC.gi / KC.ga: the icon and content areas of the device file, centred on
  // the screen as the selected glance). The content is drawn in its own units: the 390's glance (248 × 103) scaled to
  // the content's height or width, whichever is tighter (the 390's own glance stays 1:1).
  function glanceBox() {
    var ga = KC.ga, gi = KC.gi, sc = Math.min(ga[3] / 103, ga[2] / 248) / KC.s;
    var x0 = (ga[0] - KC.w / 2) / KC.s, cy = 0;
    var icon = gi ? { x: (gi[0] + gi[2] / 2 - KC.w / 2) / KC.s, y: (gi[1] + gi[3] / 2 - (ga[1] + ga[3] / 2)) / KC.s, r: gi[2] / 2 / KC.s } : null;
    return { sc: sc, x0: x0, cy: cy, w: ga[2] / KC.s / sc, h: ga[3] / KC.s / sc, rowH: ga[3] / KC.s, icon: icon };
  }
  function glanceLoop(g, d, draw, o) {
    o = o || {};
    var B = glanceBox(), gap = 65;
    // the neighbours, as the watch's own list draws them (dimmed: not selected), in the content's units
    g.push(B.sc, B.x0, B.cy);
    var ny = B.h / 2 + 84.5, ic = B.icon ? (B.icon.x - B.x0) / B.sc + 7 : -40;
    [[-ny, '#3A1416', 'heart', PAL.low, 'Heart Rate', '62 bpm'], [ny, '#14213A', 'bolt', PAL.rapid, 'Body Battery', '64']].forEach(function (n) {
      // on a round screen the row moves in as the circle narrows
      var dx = 0;
      if (!KC.rect) { var ys = Math.abs(n[0]) * B.sc, ch = Math.sqrt(Math.max(0, 195 * 195 - ys * ys)); dx = Math.max(0, (-ch + 30 - B.x0) / B.sc - (ic - 24)); }
      // a row the screen would cut is left out (the mock never shows a clipped row)
      if (!KC.rect) {
        var tx1 = B.x0 + (dx + Math.max(tw(n[4], 17), tw(n[5], 24)) + 4) * B.sc, ty0 = (n[0] - 22) * B.sc, ty1 = (n[0] + 26) * B.sc, ix0 = B.x0 + (ic + dx - 22) * B.sc, Rr = 191;
        if ([[ix0, ty0], [ix0, ty1], [tx1, ty0], [tx1, ty1]].some(function (q) { return q[0] * q[0] + q[1] * q[1] > Rr * Rr; })) return;
      }
      g.c(ic + dx, n[0], 22, { fill: n[1] }); g.icon(n[2], ic + dx, n[0], 22, n[3]);
      g.t(-1 + dx, n[0] - 6, n[4], { fs: 17, fw: 600, fill: PAL.dim, anchor: 'start' });
      g.t(-1 + dx, n[0] + 20, n[5], { fs: 24, fw: 700, fill: PAL.soft, anchor: 'start' });
    });
    g.p('M' + r1(ic - 25) + ' ' + r1(-B.h / 2 - gap / 2) + 'H' + r1(B.w - 15) + 'M' + r1(ic - 25) + ' ' + r1(B.h / 2 + gap / 2) + 'H' + r1(B.w - 15), { stroke: PAL.grid, sw: 1.5 });
    g.pop();
    // Sukrara's icon (drawn by the watch; none on the Instinct AMOLEDs' glances)
    if (B.icon) appIcon(g, B.icon.x, B.icon.y, B.icon.r);
    g.push(B.sc, B.x0, B.cy);
    if (o.frame) g.r(0, -B.h / 2, B.w, B.h, 4, { stroke: PAL.now, sw: 1, dash: '4 4', op: 0.5 });
    draw(B);
    g.pop();
  }
  // The glance: the widget's top row (as the collapsed notification): the value and its arrow, then as many of the
  // person's metrics as fit, in the card's priority (main, last treatment, second…), the rings without their period
  // label and with a bigger number. A pill or the reading's time sits under the value.
  S.glance = function (g, d, c) {
    glanceLoop(g, d, function (B) {
      var X0 = 0, X1 = B.w, R = 23, SW = 5, gapR = 6;
      var under = (d.pill && d.age < 5) || d.age >= 1.5;
      var vy = under ? 4 : 20, fs = under ? 54 : 58;
      if (d.age >= 20 || d.sensorState === 'warmup') {
        if (d.sensorState === 'warmup') {
          g.c(X0 + 14, -14, 14, { stroke: PAL.soft, sw: 2.2 });
          g.p('M' + r1(X0 + 14) + ' -14L' + r1(X0 + 14) + ' -24' + arc(10, 0, 216, X0 + 14, -14).replace(/^M[^A]+/, '') + 'Z', { fill: PAL.soft });
          g.t(X0 + 36, -6, 'Warming up', { fs: 24, fw: 600, fill: PAL.ink, anchor: 'start' });
          g.t(X0, 28, 'Ready at ' + (d.readyAt || '21:20'), { fs: 18, fw: 500, fill: PAL.soft, anchor: 'start' });
        } else {
          g.t(X0, -14, 'No reading since', { fs: 18, fw: 600, fill: PAL.soft, anchor: 'start' });
          g.t(X0, 26, d.readAt, { fs: 38, fw: 600, fill: PAL.ink, anchor: 'start' });
        }
        return;
      }
      // the same two places as page 1 (main, then the last treatment or else the second metric), from the right edge.
      // The places are decided per class with the widest value (288 and its arrow) at the smallest size (58 → 46; 54 →
      // 42 with a row under it), so they never change with the reading; a glance too narrow even then shows the main
      // place only, always. The value then takes the largest size that fits beside them (SPEC §14.1)
      var mets = metricSlots(d, c).slice(0, 2), need = 2 * (2 * R + SW) + gapR + 10, floor = fs - 12;
      if (X1 - (X0 + valueWidth({ g: 288, unit: d.unit, rate: 1 }, floor)) < need) { mets = mets.slice(0, 1); need = 2 * R + SW + 10; }
      while (fs > floor && X1 - (X0 + valueWidth(d, fs)) < need) fs -= 2;
      var v = value(g, d, X0, vy, fs, { anchor: 'start' });
      var room = X1 - (v.x1 + 10), x = X1;
      mets.forEach(function (m) {
        var w = 2 * R + SW;
        if (room < w) { room = -1; return; }
        drawSlot(g, m, x - w / 2, 0, R, SW, 23);
        x -= w + gapR; room -= w + gapR;
      });
      if (under) {
        var cy = 34, late = d.age >= 1.5, ax = X0;
        if (late) { g.t(ax, cy + 6, d.readAt, { fs: 17, fw: 500, fill: PAL.soft, anchor: 'start' }); ax += tw(d.readAt, 17) + 10; }
        if (d.pill && d.age < 5) pill(g, ax, cy, d.pill, { fs: 15, anchor: 'start' });
      }
    }, c);
  };

  // ---- the data field, in an activity's data screen ---------------------------------------------------------------
  function dfLabel(g, x, y, s, o) { o = o || {}; g.t(x, y, s, { fs: o.fs || 17, fw: 600, fill: o.fill || PAL.soft, ls: 1.2, anchor: o.anchor || 'middle' }); }
  function otherField(g, x, y, label, val, fs) { dfLabel(g, x, y - fs * 0.78, label); g.t(x, y, val, { fs: fs, fw: 700, fill: PAL.ink }); }
  function dfBlock(g, d, x0, y0, x1, y1) {
    // low / very low: the field's own block in the range's colour, the value white (Aura's very-low block)
    var b = bandOf(d.g);
    if (d.age < 5 && (b === 'veryLow' || b === 'low')) { g.r(x0, y0, x1 - x0, y1 - y0, 0, { fill: PAL[b], op: b === 'veryLow' ? 1 : 0.85 }); return true; }
    return false;
  }
  S.df1 = function (g, d) {
    var inv = dfBlock(g, d, -200, -200, 200, 200);
    dfLabel(g, 0, -128, 'GLUCOSE', { fill: inv ? '#FFFFFF' : PAL.soft });
    value(g, d, 0, -8, 116, { inverted: inv, col: inv ? '#FFFFFF' : undefined });
    if (!inv) { var scl = 1.4, Wd = 100 / Math.log(7) * scl * Math.log(19) + 24 * scl; auraSpark(g, d, { x0: -Wd / 2, x1: Wd / 2, y0: 26, y1: 110 }, { sc: scl }); }
    if (d.age >= 1.5) g.t(0, 150, d.readAt, { fs: 20, fw: 500, fill: inv ? '#FFFFFF' : PAL.soft });
  };
  S.df2 = function (g, d) {
    var inv = dfBlock(g, d, -200, -200, 200, -1);
    dfLabel(g, 0, -134, 'GLUCOSE', { fill: inv ? '#FFFFFF' : PAL.soft });
    if (inv) value(g, d, 0, -30, 92, { inverted: true, col: '#FFFFFF' });
    else {
      var vw = bw(fmtG(d.g, d.unit), 84), W = 150, gw = vw + 14 + W, x0 = -gw / 2;
      value(g, d, x0, -34, 84, { anchor: 'start', arrow: false });
      auraSpark(g, d, { x0: x0 + vw + 14, x1: x0 + vw + 14 + W, y0: -100, y1: -30 }, { sc: 0.95 });
    }
    if (d.age >= 1.5) g.t(0, -8, d.readAt, { fs: 17, fw: 500, fill: inv ? '#FFFFFF' : PAL.soft });
    g.p('M-200 0H200', { stroke: PAL.faint, sw: 2 });
    otherField(g, 0, 92, 'HEART RATE', '142', 84);
  };
  S.df3 = function (g, d) {
    otherField(g, 0, -86, 'TIMER', '32:10', 62);
    g.p('M-200 -64H200M-200 64H200', { stroke: PAL.faint, sw: 2 });
    var inv = dfBlock(g, d, -200, -63, 200, 63);
    if (inv) { dfLabel(g, -150, -38, 'GLUCOSE', { anchor: 'start', fill: '#FFFFFF' }); value(g, d, -150, 44, 76, { anchor: 'start', inverted: true, col: '#FFFFFF' }); }
    else {
      var vw = bw(fmtG(d.g, d.unit), 76), W = 150, gw = vw + 14 + W, x0 = -gw / 2;
      dfLabel(g, x0, -38, 'GLUCOSE', { anchor: 'start' });
      value(g, d, x0, 40, 76, { anchor: 'start', arrow: false });
      auraSpark(g, d, { x0: x0 + vw + 14, x1: x0 + vw + 14 + W, y0: -22, y1: 40 }, { sc: 0.85 });
    }
    otherField(g, 0, 138, 'PACE', '5:12', 62);
  };
  S.df4 = function (g, d) {
    otherField(g, 0, -86, 'DISTANCE', '6.42', 62);
    g.p('M-200 -64H200M-200 64H200M0 -64V64', { stroke: PAL.faint, sw: 2 });
    var inv = dfBlock(g, d, -200, -63, -1, 63);
    dfLabel(g, -100, -34, 'GLUCOSE', { fill: inv ? '#FFFFFF' : PAL.soft, fs: 15 });
    value(g, d, -96, 34, 60, { inverted: inv, col: inv ? '#FFFFFF' : undefined });
    otherField(g, 100, 34, 'HR', '142', 60);
    otherField(g, 0, 138, 'PACE', '5:12', 62);
  };
  S.df4small = function (g, d) {
    var inv = dfBlock(g, d, -200, -200, 200, -65);
    dfLabel(g, 0, -136, 'GLUCOSE', { fs: 14, fill: inv ? '#FFFFFF' : PAL.soft });
    value(g, d, 0, -78, 50, { inverted: inv, col: inv ? '#FFFFFF' : undefined });
    g.p('M-200 -64H200M-200 64H200M0 -64V64', { stroke: PAL.faint, sw: 2 });
    otherField(g, -100, 34, 'TIMER', '32:10', 50);
    otherField(g, 100, 34, 'HR', '142', 60);
    otherField(g, 0, 138, 'PACE', '5:12', 62);
  };

  // ---- complications in another face -----------------------------------------------------------------------------
  S.faceCx = function (g, d, c) {
    // any face: the face draws each complication from what Sukrara publishes (a String, a fixed icon); here a plain
    // face with four slot styles
    var old = d.age >= 5, v = old ? '---' : fmtG(d.g, d.unit);
    g.t(0, 22, '10:09', { fs: 108, fw: 600, fill: PAL.ink });
    g.t(0, -64, 'MON 16', { fs: 22, fw: 600, fill: PAL.soft, ls: 2 });
    // top: a round slot, icon above the value (Sukrara glucose)
    g.c(0, -122, 38, { stroke: PAL.faint, sw: 2 });
    g.icon('drop', 0, -140, 16, PAL.soft);
    g.t(0, -106, v, { fs: 26, fw: 700, fill: PAL.ink });
    g.t(0, -92, 'CGM', { fs: 10, fw: 600, fill: PAL.soft, ls: 1 });
    // bottom left: icon + text (Sukrara glucose again, in another face's style)
    g.icon('drop', -110, 85, 20, PAL.soft);
    g.t(-96, 93, v, { fs: 26, fw: 700, fill: PAL.ink, anchor: 'start' });
    // bottom right: the Log slot: a generic icon, the last treatment as text (u = rapid, g = carbs)
    // the Log value, as the watch's setting says: the time since (default), the amount, or both; the label is fixed
    var show = c.logShow || 'time', lv = 'Log';
    if (d.slot) {
      var am = d.slot.a + (d.slot.k === 'carbs' ? 'g' : 'u'), tm = timeSince(d.slot.m);
      lv = show === 'amount' ? am : show === 'both' ? am + ' ' + tm.join('') : tm.join(' ');
    }
    var lfs = lv.length > 7 ? 20 : 24;
    g.icon('pen', 34, 85, 22, PAL.soft);
    g.t(50, 93, lv, { fs: lfs, fw: 700, fill: PAL.ink, anchor: 'start' });
    g.t(50, 70, 'LOG', { fs: 12, fw: 600, fill: PAL.soft, anchor: 'start', ls: 1 });
    // bottom: a CGM+ GB face's own graph and arrow from CGM-GB-data
    if (!old) sparkline(g, d, { x0: -60, x1: 60, y0: 114, y1: 150 }, { hours: 3, sw: 2.5 });
    else g.t(0, 140, 'old', { fs: 15, fw: 600, fill: PAL.dim });
  };

  // ---- logging -----------------------------------------------------------------------------------------------------
  S.logAmount = function (g, d, c) {
    var k = c.kind || 'rapid', col = PAL[k], label = { rapid: 'Rapid insulin', carbs: 'Carbs', long: 'Long insulin' }[k];
    var a = c.amount !== undefined ? c.amount : (k === 'carbs' ? 50 : k === 'long' ? 14 : 4.5), unit = k === 'carbs' ? 'g' : 'u';
    var lw = tw(label, 22) + 30;
    g.icon(k, -lw / 2 + 10, -129, 18, col);
    g.t(-lw / 2 + 26, -122, label, { fs: 22, fw: 600, fill: col, anchor: 'start' });
    var s = String(a), fs = 128, w = bw(s, fs), uw = tw(unit, 50);
    var x0 = -(w + 8 + uw) / 2;
    g.t(x0, 22, s, { fs: fs, fw: 700, fill: PAL.ink, anchor: 'start' });
    g.t(x0 + w + 8, 22, unit, { fs: 50, fw: 600, fill: PAL.soft, anchor: 'start' });
    // up / down: chevrons above and below the number (touch: tap or drag; buttons: UP / DOWN)
    g.p('M-16 -76L0 -90L16 -76', { stroke: PAL.dim, sw: 4 });
    g.p('M-16 42L0 56L16 42', { stroke: PAL.dim, sw: 4 });
    // the usual amounts
    var chips = c.usual || (k === 'carbs' ? ['15', '30', '50'] : k === 'long' ? ['12', '14', '16'] : ['4', '4.5', '6']);
    var cw = 64, gap = 10, cx0 = -(chips.length * cw + (chips.length - 1) * gap) / 2;
    chips.forEach(function (ch, i) {
      var on = ch === s, x = cx0 + i * (cw + gap);
      g.r(x, 76, cw, 36, 18, { fill: on ? col : PAL.card2, op: 1 });
      g.t(x + cw / 2, 101, ch, { fs: 20, fw: 700, fill: on ? PAL.on : PAL.soft });
    });
    g.t(0, 146, 'Now', { fs: 20, fw: 600, fill: PAL.soft });
    btnHint(g, 60, '', {});
    btnHint(g, 270, '+', { fs: 26, r: 170, anchor: 'start', dx: -4 });
    btnHint(g, 240, '−', { fs: 26, r: 166, anchor: 'start', dx: -4 });
  };
  S.logConfirm = function (g, d, c) {
    var k = c.kind || 'rapid', col = PAL[k], a = c.amount !== undefined ? c.amount : (k === 'carbs' ? 50 : 4.5), unit = k === 'carbs' ? 'g' : 'u';
    var word = { rapid: 'rapid insulin', carbs: 'carbs', long: 'long insulin' }[k];
    if (c.dup) {
      g.icon('warn', -96, -132, 22, PAL.high);
      g.t(-80, -125, (k === 'carbs' ? '50g' : '4.5u') + ' 3 min ago', { fs: 20, fw: 600, fill: PAL.high, anchor: 'start' });
    } else g.t(0, -124, 'Log', { fs: 22, fw: 600, fill: PAL.soft });
    var wN = bw(String(a), 96), wU = tw(unit, 96) * 0.97, tot = 30 + 12 + wN + wU, x0 = -tot / 2;
    g.icon(k, x0 + 15, -40, 30, col);
    g.t(x0 + 42, -8, String(a) + unit, { fs: 96, fw: 700, fill: col, anchor: 'start' });
    g.t(0, 30, word + ' · now', { fs: 22, fw: 600, fill: PAL.ink });
    g.t(0, 62, k === 'carbs' ? 'Lows quiet for 15 min' : k === 'rapid' ? 'Highs quiet for 1 h' : 'Nothing goes quiet', { fs: 18, fw: 500, fill: PAL.soft });
    // hold to confirm: the rim fills from START; the words say how
    var p = c.progress === undefined ? 0.45 : c.progress;
    rim(g, PAL.grid, 8, 1);
    if (p > 0) rim(g, col, 8, p);
    g.r(-92, 102, 184, 44, 22, { fill: PAL.card2 });
    g.t(0, 131, 'Hold to log', { fs: 20, fw: 700, fill: PAL.ink });
    btnHint(g, 60, '', { col: col });
  };
  // what was logged, as the done screens say it: "4.5u rapid"
  function what(c) {
    var k = c.kind || 'rapid', a = c.amount !== undefined ? c.amount : (k === 'carbs' ? 50 : k === 'long' ? 14 : 4.5);
    return amt({ k: k, a: a }) + ' ' + { rapid: 'rapid', long: 'long-acting', carbs: 'carbs' }[k];
  }
  S.logSending = function (g, d, c) {
    var k = c.kind || 'rapid', col = PAL[k];
    rim(g, col, 8, 1);
    g.c(0, -36, 46, { stroke: PAL.grid, sw: 6 });
    g.p(arc(46, 20, 130, 0, -36), { stroke: col, sw: 6 });
    g.icon('phone', 0, -36, 30, PAL.soft);
    g.t(0, 46, what(c), { fs: 30, fw: 700, fill: PAL.ink });
    g.t(0, 80, 'Sending to your phone', { fs: 20, fw: 500, fill: PAL.soft });
  };
  S.logDone = function (g, d, c) {
    var k = c.kind || 'rapid', col = PAL[k];
    g.c(0, -46, 50, { fill: col });
    g.p('M-22 -46l14 14l30 -30', { stroke: PAL.on, sw: 8 });
    g.t(0, 40, 'Logged ' + what(c), { fs: 28, fw: 700, fill: PAL.ink });
    g.t(0, 72, 'Highs quiet for 1 h', { fs: 20, fw: 500, fill: PAL.soft });
    g.r(-64, 98, 128, 42, 21, { fill: PAL.card2 });
    g.t(-8, 126, 'Undo', { fs: 20, fw: 700, fill: PAL.ink });
    g.c(40, 119, 10, { stroke: PAL.grid, sw: 3 });
    g.p(arc(10, 0, 220, 40, 119), { stroke: PAL.soft, sw: 3 });
    btnHint(g, 60, '', {});
  };
  S.logQueued = function (g, d, c) {
    g.icon('phone', 0, -60, 46, PAL.high);
    g.p('M-24 -84L24 -36', { stroke: PAL.high, sw: 4 });
    g.t(0, 10, 'Saved on the watch', { fs: 26, fw: 700, fill: PAL.ink });
    g.t(0, 42, what(c) + ' · ' + d.clock, { fs: 20, fw: 600, fill: PAL[c.kind || 'rapid'] });
    g.t(0, 76, 'Goes to your phone', { fs: 18, fw: 500, fill: PAL.soft });
    g.t(0, 100, 'when it’s near', { fs: 18, fw: 500, fill: PAL.soft });
  };

  // ---- alerts -------------------------------------------------------------------------------------------------------
  S.alert = function (g, d, c) {
    var b = bandOf(d.g), low = b === 'low' || b === 'veryLow', very = b === 'veryLow' || b === 'extremelyHigh';
    var col = PAL[b], word = { veryLow: 'VERY LOW', low: 'LOW', high: 'HIGH', veryHigh: 'VERY HIGH', extremelyHigh: 'EXTREMELY HIGH', tight: 'FALLING FAST', target: 'RISING FAST' }[b];
    if (very) full(g, col);
    else rim(g, col, 10, 1);
    // on the block: white (MIP: white or black, whichever reads better on the 64-colour block, inkOn)
    var W0 = inkOn(col), ink = very ? W0 : col;
    g.icon('bell', -tw(word, 20) / 2 - 16, -140, 20, ink);
    g.t(12, -133, word, { fs: 20, fw: 700, fill: ink, ls: 1.5 });
    var spark = c.spark !== false;
    if (spark) {
      // value + sparkline inside the rim: the sparkline gives way first (132 → 96), then the value (84 → 68)
      var str = fmtG(d.g, d.unit), vfs = 84, vw = bw(str, vfs), W = 132, gw = vw + 12 + W, x0 = -gw / 2;
      while (!inside(x0, -54 - fe(vfs) * 0.7, x0 + gw, -54) && (W > 96 || vfs > 68)) {
        if (W > 96) W -= 4; else vfs -= 4;
        vw = bw(str, vfs); gw = vw + 12 + W; x0 = -gw / 2;
      }
      value(g, d, x0, -54, vfs, { anchor: 'start', arrow: false, col: very ? W0 : undefined, inverted: very });
      if (very) {
        // on the very-low block the sparkline draws in white
        var dd = Object.assign({}, d);
        auraSpark(g, dd, { x0: x0 + vw + 12, x1: x0 + vw + 12 + W, y0: -112, y1: -56 }, { sc: 0.8, white: true, ink: W0 });
      } else auraSpark(g, d, { x0: x0 + vw + 12, x1: x0 + vw + 12 + W, y0: -112, y1: -56 }, { sc: 0.8 });
    } else value(g, d, 0, -44, 100, { col: very ? W0 : undefined, inverted: very });
    var tk = low ? 'carbs' : 'rapid';
    var relaxed = c.relaxed;
    // the secondary buttons on the block: darker (AMOLED); MIP: black, or an outline in the ink when the ink is black
    var sec = !very ? PAL.card2 : !KC.mip ? 'rgba(0,0,0,0.28)' : W0 === '#000000' ? 'none' : '#000000';
    var rows = [
      { t: 'Treatment', fill: very ? W0 : PAL[tk], fg: very && W0 === '#000000' ? '#FFFFFF' : PAL.on, icon: tk },
      { t: relaxed ? 'Remind as usual' : 'Remind less', fill: sec, fg: very ? W0 : PAL.ink, line: very && KC.mip && W0 === '#000000' },
      { t: 'Mute', fill: sec, fg: very ? W0 : PAL.ink, icon: 'bellOff', line: very && KC.mip && W0 === '#000000' }
    ];
    var ys = [-6, 50, 106], ws = KC.rect ? [2 * KC.hw - 36, 2 * KC.hw - 36, 2 * KC.hw - 36] : [236, 236, 196];
    rows.forEach(function (r, i) {
      var y = ys[i], w = ws[i];
      g.r(-w / 2, y - 23, w, 46, 23, { fill: r.fill, stroke: r.line ? W0 : 'none', sw: 2 });
      var tw0 = tw(r.t, 20) + (r.icon ? 26 : 0), x0 = -tw0 / 2;
      if (r.icon) g.icon(r.icon, x0 + 9, y, 18, r.fg);
      g.t(x0 + (r.icon ? 26 : 0), y + 7, r.t, { fs: 20, fw: 700, fill: r.fg, anchor: 'start' });
    });
    // the relaxed interval under the buttons, where it fits inside the rim (else the button's words say it: SPEC §14.1)
    var rw = tw('Reminders every 30 min', 15);
    if (relaxed && inside(-rw / 2, 150 - fe(15) * 0.7, rw / 2, 150 + fe(15) * 0.22)) g.t(0, 150, 'Reminders every 30 min', { fs: 15, fw: 500, fill: very ? W0 : PAL.soft });
    btnHint(g, 60, '', { col: very ? W0 : PAL[tk] });
  };
  S.remindDone = function (g, d, c) {
    g.c(0, -56, 46, { fill: PAL.low, op: 0.24 });
    g.icon('bellPause', 0, -56, 44, PAL.low);
    g.t(0, 28, 'Lows: remind less', { fs: 28, fw: 700, fill: PAL.ink });
    g.t(0, 60, 'Reminders every 30 min', { fs: 20, fw: 500, fill: PAL.soft });
    g.r(-64, 92, 128, 42, 21, { fill: PAL.card2 });
    g.t(-8, 120, 'Undo', { fs: 20, fw: 700, fill: PAL.ink });
    g.c(40, 113, 10, { stroke: PAL.grid, sw: 3 });
    g.p(arc(10, 0, 220, 40, 113), { stroke: PAL.soft, sw: 3 });
    btnHint(g, 60, '', {});
  };
  S.wake = function (g, d) {
    // Garmin's full-screen notification, sent by the app's background (API 5.1+): title, subtitle, icon, actions.
    // Every action opens the app with that action; Remind less and Mute send it and close again at once.
    appIcon(g, 0, -140, 20);
    g.t(0, -96, 'Low 62', { fs: 32, fw: 700, fill: PAL.ink });
    g.t(0, -68, 'Falling · 20:41', { fs: 19, fw: 500, fill: PAL.soft });
    var rows = [['carbs', 'Treatment', PAL.carbs], ['bellPause', 'Remind less', PAL.soft], ['bellOff', 'Mute lows 30m', PAL.soft]];
    rows.forEach(function (r, i) {
      var y = -26 + i * 56, w = i === 2 ? 220 : 240;
      g.r(-w / 2, y - 23, w, 46, 23, { fill: PAL.card2 });
      var tw0 = tw(r[1], 20) + 26, x0 = -tw0 / 2;
      g.icon(r[0], x0 + 9, y, 18, r[2]);
      g.t(x0 + 26, y + 7, r[1], { fs: 20, fw: 700, fill: PAL.ink, anchor: 'start' });
    });
    g.t(0, 152, 'Garmin draws this', { fs: 14, fw: 500, fill: PAL.dim });
  };
  // START in the app: one list, the kinds in the phone's order (the focused one first and selected), then mute
  S.actions = function (g, d, c) {
    var last = d.lastBy || { rapid: '4.5u · 1h', carbs: '50g · 1h', long: '14u · 12h' };
    var name = { rapid: 'Rapid', carbs: 'Carbs', long: 'Long' };
    var items = d.order.map(function (k) { return { label: name[k], icon: k, col: PAL[k], sub: last[k] }; });
    items.push(c.muted ? { label: 'Muted', icon: 'bellOff', col: PAL.note, sub: '1h 20m', subCol: PAL.note } : { label: 'Mute alerts', icon: 'bellOff', col: PAL.soft });
    g.t(0, -138, 'Sukrara', { fs: 20, fw: 600, fill: PAL.soft });
    var ys = [-74, -12, 44, 100];
    items.forEach(function (it, i) {
      var y = ys[i], on = i === 0, fs = on ? 30 : 23;
      if (on) g.r(-166, y - 32, 332, 64, 32, { fill: PAL.card2 });
      g.icon(it.icon, -128, y, on ? 26 : 21, it.col);
      g.t(-104, y + fs * 0.35, it.label, { fs: fs, fw: on ? 700 : 600, fill: on ? PAL.ink : PAL.soft, anchor: 'start' });
      if (it.sub) g.t(150, y + 7, it.sub, { fs: on ? 18 : 16, fw: 600, fill: it.subCol || (on ? PAL.soft : PAL.dim), anchor: 'end' });
    });
    btnHint(g, 60, '', {});
  };
  S.muteLengths = function (g, d, c) {
    var side = c.side;
    g.t(0, -140, side === 'low' ? 'Mute lows' : side === 'high' ? 'Mute highs' : 'Mute all alerts', { fs: 22, fw: 700, fill: PAL.ink });
    g.t(0, -114, side ? 'on your phone and this watch' : 'very low and no data too', { fs: 16, fw: 500, fill: PAL.soft });
    var items = ['15 min', '30 min', '1 h', '2 h', side ? '' : 'Until I unmute'].filter(Boolean), sel = 1;
    var ys = [-62, -8, 46, 100, 150];
    items.slice(0, 5).forEach(function (it, i) {
      var y = ys[i], on = i === sel, fs = on ? 30 : 24;
      if (on) g.r(-150, y - 28, 300, 56, 28, { fill: PAL.card2 });
      g.t(0, y + fs * 0.35, it, { fs: fs, fw: on ? 700 : 600, fill: on ? PAL.ink : PAL.soft });
    });
    btnHint(g, 60, '', {});
  };
  S.muteDone = function (g, d, c) {
    g.c(0, -56, 46, { fill: PAL.note, op: 0.24 });
    g.icon('bellOff', 0, -56, 44, PAL.note);
    g.t(0, 28, c.side === 'low' ? 'Lows muted' : 'Alerts muted', { fs: 30, fw: 700, fill: PAL.ink });
    g.t(0, 60, 'for 30 min · until 21:11', { fs: 20, fw: 500, fill: PAL.soft });
    g.r(-64, 92, 128, 42, 21, { fill: PAL.card2 });
    g.t(-8, 120, 'Undo', { fs: 20, fw: 700, fill: PAL.ink });
    g.c(40, 113, 10, { stroke: PAL.grid, sw: 3 });
    g.p(arc(10, 0, 220, 40, 113), { stroke: PAL.soft, sw: 3 });
    btnHint(g, 60, '', {});
  };
  S.muted = function (g, d, c) {
    g.icon('bellOff', 0, -118, 30, PAL.note);
    g.t(0, -70, 'Alerts muted', { fs: 28, fw: 700, fill: PAL.ink });
    g.t(0, -40, '1h 20m left · until 22:01', { fs: 19, fw: 500, fill: PAL.soft });
    var ys = [10, 70], items = ['Unmute', 'Change time'];
    items.forEach(function (it, i) {
      var y = ys[i], on = i === 0;
      g.r(-130, y - 26, 260, 52, 26, { fill: on ? PAL.note : PAL.card2 });
      g.t(0, y + 8, it, { fs: 22, fw: 700, fill: on ? PAL.on : PAL.ink });
    });
    g.t(0, 134, 'Very low and no data too', { fs: 16, fw: 500, fill: PAL.dim });
    btnHint(g, 60, '', {});
  };

  // the data field's own alert during an activity (DataField.showAlert; the person turns it on in the activity's
  // Alerts › Connect IQ): a full page for a few seconds, with a vibration
  S.dfAlert = function (g, d) {
    var b = bandOf(d.g), col = PAL[b], very = b === 'veryLow';
    full(g, col);
    var W0 = inkOn(col);
    g.icon('bell', -tw('LOW', 24) / 2 - 18, -96, 24, W0);
    g.t(12, -88, very ? 'VERY LOW' : 'LOW', { fs: 24, fw: 700, fill: W0, ls: 1.5 });
    value(g, d, 0, 30, 116, { col: W0, inverted: true });
    g.t(0, 92, 'Sukrara · ' + (d.age < 1 ? 'now' : Math.round(d.age) + ' min ago'), { fs: 20, fw: 600, fill: W0 });
  };

  // ---- the watch around it ---------------------------------------------------------------------------------------
  function bezel(metal) {
    var b = new G();
    var m = metal === 'bronze' ? { a: '#7A4F35', b: '#4B2F20', edge: '#B98260', screw: '#C99272' } : { a: '#3D3F44', b: '#232528', edge: '#6B6E74', screw: '#7D8086' };
    [60, 120, 240, 270, 300].forEach(function (a) { b.r(-14, -288, 28, 22, 5, { fill: '#8A8C91' }); b.els[b.els.length - 1].tf = 'rotate(' + a + ')'; });
    b.c(0, 0, 271, { fill: '#141518' });
    b.c(0, 0, 252.5, { stroke: m.b, sw: 31 });
    b.c(0, 0, 258, { stroke: m.a, sw: 12, op: 0.8 });
    b.c(0, 0, 267.5, { stroke: m.edge, sw: 1 });
    [45, 135, 225, 315].forEach(function (a) { var p = P(252, a); b.c(p[0], p[1], 6.5, { fill: '#1E1F22', stroke: m.screw, sw: 1.3 }); });
    b.c(0, 0, 216, { stroke: '#08080A', sw: 42 });
    var nums = [[0, '24'], [60, '04'], [90, '06'], [120, '08'], [150, '10'], [210, '14'], [240, '16'], [270, '18'], [300, '20'], [330, '22']];
    nums.forEach(function (n) { var p = P(214, n[0]); b.t(p[0], p[1] + 6, n[1], { fs: 17, fw: 600, fill: '#E2E2E5', tf: 'rotate(' + (n[0] > 90 && n[0] < 270 ? n[0] - 180 : n[0]) + ' ' + p[0] + ' ' + p[1] + ')' }); });
    return b.els;
  }
  // rect elements carry a transform too
  G.prototype.rt = function () {};

  // ---- the data field in any slot of any class (S.dfSlot; SPEC §14.4) ------------------------------------------------
  // cfg.form: whole | half | band | quarter | cap (the slots of DFS). The form is chosen by the room, never by the class:
  // value + sparkline side by side when a box of 260 × 100 units (× s px) fits inside the slot and the screen; else the
  // value + arrow alone, the largest atlas size that fits. The other slots show placeholder fields.
  var OTHER = [['HEART RATE', '142'], ['TIMER', '32:10'], ['PACE', '5:12'], ['DISTANCE', '6.42']];
  function slotBox(r) {
    return { x0: (r[0] - KC.w / 2) / KC.s, y0: (r[1] - KC.h / 2) / KC.s, x1: (r[0] + r[2] - KC.w / 2) / KC.s, y1: (r[1] + r[3] - KC.h / 2) / KC.s, obs: r[4] || '' };
  }
  // a box of height h inside the slot and the screen, at the slot's side toward the centre, as wide as the circle allows
  function fitBox(b, h, pad) {
    var top = b.obs.indexOf('T') >= 0, bot = b.obs.indexOf('B') >= 0, y0, y1, mid = (b.y0 + b.y1) / 2;
    if (top && !bot) { y1 = b.y1 - pad; y0 = y1 - h; } else if (bot && !top) { y0 = b.y0 + pad; y1 = y0 + h; } else { y0 = mid - h / 2; y1 = mid + h / 2; }
    if (y0 < b.y0 + pad - 0.01 || y1 > b.y1 - pad + 0.01) return null;
    var x0 = b.x0 + pad, x1 = b.x1 - pad;
    if (!KC.rect) {
      var R = 195 - pad, ym = Math.max(Math.abs(y0), Math.abs(y1));
      if (y0 < 0 && y1 > 0) ym = Math.max(-y0, y1);
      if (ym >= R) return null;
      var hc = Math.sqrt(R * R - ym * ym);
      x0 = Math.max(x0, -hc); x1 = Math.min(x1, hc);
    }
    return x1 - x0 > 0 ? { x0: x0, x1: x1, y0: y0, y1: y1, w: x1 - x0, h: h } : null;
  }
  // the largest value (em width `em`, cap 0.668) that fits in the slot, with its label row when there is room
  function fitValue(b, em, max) {
    var best = null;
    for (var h = b.y1 - b.y0 - 8; h >= 24; h -= 2) {
      var bx = fitBox(b, h, 4); if (!bx) continue;
      var lab = h >= 64, fsH = (h - (lab ? 24 : 0) - 6) / 0.668, fsW = bx.w * 0.9 / em, fs = Math.min(fsH, fsW, max);
      if (!best || fs > best.fs + 0.5) best = { box: bx, fs: fs, lab: lab };
    }
    if (!best) return null;
    // the content (label row + value) keeps to the box's side toward the screen's centre: no gap inside it
    var ch = (best.lab ? 24 : 0) + fe(best.fs) * 0.668 + 6, bb = best.box, top = b.obs.indexOf('T') >= 0, bot = b.obs.indexOf('B') >= 0;
    var y0 = top && !bot ? bb.y1 - ch : bot && !top ? bb.y0 : (bb.y0 + bb.y1) / 2 - ch / 2;
    best.box = { x0: bb.x0, x1: bb.x1, y0: y0, y1: y0 + ch, w: bb.w, h: ch };
    return best;
  }
  function emOf(str, arrow) { var t = 0; for (var k = 0; k < str.length; k++) t += BIG[str[k]] !== undefined ? BIG[str[k]] : 0.6; return t + (arrow ? 0.1 + 0.668 * 0.78 : 0); }
  function dfField(g, d, b, whole) {
    var band = bandOf(d.g), inv = d.age < 5 && (band === 'veryLow' || band === 'low');
    if (inv) g.r(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0, 0, { fill: PAL[band], op: band === 'veryLow' ? 1 : 0.85 });
    var W0 = inv ? inkOn(PAL[band]) : null, lab = inv ? W0 : PAL.soft, str = fmtG(d.g, d.unit), late = d.age >= 1.5;
    if (whole) {
      var top = KC.rect ? -KC.hh + 40 : -128;
      dfLabel(g, 0, top, 'GLUCOSE', { fill: lab });
      value(g, d, 0, -8, 116, { inverted: inv, col: inv ? W0 : undefined });
      if (!inv) { var scl = 1.4, Wd = 100 / Math.log(7) * scl * Math.log(19) + 24 * scl; auraSpark(g, d, { x0: -Wd / 2, x1: Wd / 2, y0: 26, y1: 110 }, { sc: scl }); }
      if (late) g.t(0, 150, d.readAt, { fs: 20, fw: 500, fill: inv ? W0 : PAL.soft });
      return 'whole';
    }
    // value + sparkline side by side (the 390's half and band)
    if (!inv) {
      for (var h = Math.min(170, b.y1 - b.y0 - 12); h >= 100; h -= 2) {
        var sb = fitBox(b, h, 6);
        if (!sb || sb.w < 260) continue;
        var fs = Math.min(92, (sb.h - 30) * 0.8), vw = bw(str, fs), Wsp = Math.min(170, sb.w - vw - 14 - 16);
        if (Wsp < 90) break;
        var gw = vw + 14 + Wsp, x0 = (sb.x0 + sb.x1) / 2 - gw / 2, yb = sb.y1 - 16, cap = fs * 0.668;
        dfLabel(g, x0, sb.y0 + 20, 'GLUCOSE', { anchor: 'start' });
        if (late) g.t(x0 + gw, sb.y0 + 20, d.readAt, { fs: 17, fw: 500, fill: PAL.soft, anchor: 'end' });
        value(g, d, x0, yb, fs, { anchor: 'start', arrow: false });
        auraSpark(g, d, { x0: x0 + vw + 14, x1: x0 + gw, y0: yb - cap - 8, y1: yb }, { sc: Math.min(1, fs / 88) });
        return 'side by side';
      }
    }
    // the value and its arrow, as big as the slot allows
    var fv = fitValue(b, emOf(str, Number.isFinite(d.rate)), 116);
    if (!fv) return 'none';
    var bx = fv.box, cx = (bx.x0 + bx.x1) / 2;
    if (fv.lab) dfLabel(g, cx, bx.y0 + 16, 'GLUCOSE', { fill: lab, fs: 15 });
    value(g, d, cx, bx.y1 - 4, fv.fs, { inverted: inv, col: inv ? W0 : undefined });
    return 'value';
  }
  function otherSlot(g, b, i) {
    var o = OTHER[i % OTHER.length], fv = fitValue(b, emOf(o[1], false), 84);
    if (!fv) return;
    var bx = fv.box, cx = (bx.x0 + bx.x1) / 2;
    if (fv.lab) dfLabel(g, cx, bx.y0 + 16, o[0], { fs: 15 });
    g.t(cx, bx.y1 - 4, o[1], { fs: fv.fs, fw: 700, fill: PAL.ink });
  }
  S.dfSlot = function (g, d, c) {
    var form = c.form || 'whole';
    var L = form === 'whole' ? { i: 0, f: [[0, 0, KC.w, KC.h, 'LTRB']] } : DFS[KC.id][form], other = 0;
    L.f.forEach(function (r, i) {
      var b = slotBox(r);
      if (i === L.i) dfField(g, d, b, form === 'whole'); else otherSlot(g, b, other++);
    });
    // the slots' borders, as the watch draws them
    L.f.forEach(function (r) {
      var b = slotBox(r);
      if (r[1] > 2) g.p('M' + r1(-KC.hw) + ' ' + r1(b.y0 - 1 / KC.s) + 'H' + r1(KC.hw), { stroke: PAL.faint, sw: 2 });
      if (r[0] > 2) g.p('M' + r1(b.x0 - 1 / KC.s) + ' ' + r1(b.y0) + 'V' + r1(b.y1), { stroke: PAL.faint, sw: 2 });
    });
  };

  // ---- the rectangles' own layouts (Venu Sq 2, Venu X1; SPEC §14.3) ---------------------------------------------------
  // Drawn in the Venu Sq 2's px (KC.s = 1; the X1 1.2), the screen ±KC.hw × ±KC.hh. The corners are room a round screen
  // doesn't have: the widget's two places move up into them beside the clock, and the value gets the whole width.
  var R = {};
  R.appA = function (g, d, c) {
    var hw = KC.hw, hh = KC.hh;
    clock(g, d, -hh + 34, { fs: 22 });
    var ms = metricSlots(d, c), rr = 24, sw = 6, sy = -hh + 34;
    drawSlot(g, ms[1], -hw + 14 + rr + sw / 2, sy, rr, sw, 22);
    drawSlot(g, ms[0], hw - 14 - rr - sw / 2, sy, rr, sw, 22);
    var vy = -hh + 150;
    if (d.age >= 20 || d.sensorState === 'warmup') noValue(g, d, vy - 34);
    else value(g, d, 0, vy, 96);
    var bx = { x0: -hw + 44, x1: hw - 34, y0: vy + 26, y1: hh - 64 };
    var sc = chart(g, d, bx, { hours: c.hours || 12, lfs: 14, ms: 10 });
    if (c.dist !== false) pctColumn(g, d, sc, bx, { lfs: 14, hours: c.hours || 12, dot: 4.6, mask: true });
    if (!chipRow(g, d, hh - 22, { fs: 19 })) sensorTag(g, d, 0, hh - 16, { fs: 17 });
    if (c.dots !== false) pageDots(g, 2, 0);
  };
  R.appNums = function (g, d, c) {
    var hw = KC.hw, hh = KC.hh, per = c.period || '14d', pers = ['1d', '7d', '14d', '30d'];
    var cw = Math.min(64, (2 * hw - 32 - 18) / 4), gp = 6, x0 = -(pers.length * cw + (pers.length - 1) * gp) / 2, cyy = -hh + 16;
    pers.forEach(function (p, i) {
      var on = p === per, x = x0 + i * (cw + gp);
      g.r(x, cyy, cw, 32, 16, { fill: on ? PAL.card2 : 'none', stroke: on ? PAL.soft : PAL.faint, sw: 1.5 });
      g.t(x + cw / 2, cyy + 22, p, { fs: 17, fw: on ? 700 : 500, fill: on ? PAL.ink : PAL.soft });
    });
    var Rr = { above: 14, inR: 85, good: 64, below: 0.1, split: [0, 0.1, 64, 21, 14, 0.8, 0] };
    var top = cyy + 52, bot = hh - 86, bxx = -hw + 14, tot = 0, keys = ['veryLow', 'low', 'tight', 'target', 'high', 'veryHigh', 'extremelyHigh'];
    Rr.split.forEach(function (v) { tot += v; });
    var y = bot;
    keys.forEach(function (k, i) {
      var v = Rr.split[i]; if (!v) return;
      var h = v / tot * (bot - top);
      g.r(bxx, y - h + (h > 3 ? 1 : 0), 12, Math.max(1.5, h - (h > 3 ? 2 : 0)), 2, { fill: PAL[k] });
      y -= h;
    });
    var lx = bxx + 26, rx = hw - 12, step = (bot - top) / 3;
    function goal(x, yy, text) {
      var w = 26 + tw(text, 14);
      g.r(x, yy - 15, w, 22, 11, { fill: '#173A27' });
      g.p('M' + r1(x + 8) + ' ' + r1(yy - 4.5) + 'l3.5 3.5l6 -7', { stroke: PAL.tight, sw: 2 });
      g.t(x + 21, yy + 1, text, { fs: 14, fw: 600, fill: PAL.tight, anchor: 'start' });
      return w;
    }
    [['Above range', Rr.above + ' %', '< 25%'], ['In range', Rr.inR + ' %', '> 70%', 'very good ' + Rr.good + ' %'], ['Below range', Rr.below + ' %', '< 4%']].forEach(function (r, i) {
      var yy = top + 22 + i * step, big = i === 1;
      g.t(lx, yy, r[0], { fs: big ? 22 : 19, fw: big ? 700 : 600, fill: PAL.ink, anchor: 'start' });
      g.t(rx, yy + (big ? 4 : 0), r[1], { fs: big ? 32 : 22, fw: 700, fill: PAL.ink, anchor: 'end' });
      var w = goal(lx, yy + 24, r[2]);
      if (r[3]) g.t(lx + w + 10, yy + 25, r[3], { fs: 15, fw: 500, fill: PAL.soft, anchor: 'start' });
    });
    g.p('M' + r1(-hw + 12) + ' ' + r1(bot + 14) + 'H' + r1(hw - 12), { stroke: PAL.grid, sw: 1.5 });
    [['136', 'Average', false], ['6.6 %', 'GMI', true], ['27 %', 'CV', true]].forEach(function (m, i) {
      var x = (i - 1) * (2 * hw - 40) / 3;
      g.t(x, bot + 48, m[0], { fs: 24, fw: 700, fill: PAL.ink });
      var lw = tw(m[1], 15) + 18;
      g.p('M' + r1(x - lw / 2) + ' ' + r1(bot + 65) + 'l3.2 3.2l5.5 -6.5', { stroke: PAL.tight, sw: 2 });
      g.t(x - lw / 2 + 14, bot + 71, m[1], { fs: 15, fw: 600, fill: PAL.soft, anchor: 'start' });
    });
    if (c.dots !== false) pageDots(g, 2, 1);
  };
  R.actions = function (g, d, c) {
    var hw = KC.hw, hh = KC.hh;
    var last = { rapid: '4.5u · 1h', carbs: '50g · 1h', long: '14u · 12h' }, name = { rapid: 'Rapid', carbs: 'Carbs', long: 'Long' };
    var items = d.order.map(function (k) { return { label: name[k], icon: k, col: PAL[k], sub: last[k] }; });
    items.push(c.muted ? { label: 'Muted', icon: 'bellOff', col: PAL.note, sub: '1h 20m', subCol: PAL.note } : { label: 'Mute alerts', icon: 'bellOff', col: PAL.soft });
    g.t(-hw + 18, -hh + 34, 'Sukrara', { fs: 20, fw: 600, fill: PAL.soft, anchor: 'start' });
    var y0 = -hh + 86, step = (2 * hh - 86 - 40) / 4 + 2;
    items.forEach(function (it, i) {
      var y = y0 + i * step, on = i === 0, fs = on ? 30 : 23;
      if (on) g.r(-hw + 8, y - 32, 2 * hw - 16, 64, 20, { fill: PAL.card2 });
      g.icon(it.icon, -hw + 38, y, on ? 26 : 21, it.col);
      g.t(-hw + 62, y + fs * 0.35, it.label, { fs: fs, fw: on ? 700 : 600, fill: on ? PAL.ink : PAL.soft, anchor: 'start' });
      if (it.sub) g.t(hw - 20, y + 7, it.sub, { fs: on ? 18 : 16, fw: 600, fill: it.subCol || (on ? PAL.soft : PAL.dim), anchor: 'end' });
    });
    btnHint(g, 60, '', {});
  };
  // the amount: no UP / DOWN on the rectangles, so the chevrons are the buttons (tap; swipe up / down too)
  R.logAmount = function (g, d, c) {
    var hw = KC.hw, hh = KC.hh, k = c.kind || 'rapid', col = PAL[k], label = { rapid: 'Rapid insulin', carbs: 'Carbs', long: 'Long insulin' }[k];
    var a = c.amount !== undefined ? c.amount : (k === 'carbs' ? 50 : k === 'long' ? 14 : 4.5), unit = k === 'carbs' ? 'g' : 'u';
    var lw = tw(label, 22) + 30;
    g.icon(k, -lw / 2 + 10, -hh + 33, 18, col);
    g.t(-lw / 2 + 26, -hh + 40, label, { fs: 22, fw: 600, fill: col, anchor: 'start' });
    var s = String(a), fs = 120, w = bw(s, fs), uw = tw(unit, 48), yU = -hh + 82, ay = yU + 112, yD = ay + 34, x0 = -(w + 8 + uw) / 2;
    g.t(x0, ay, s, { fs: fs, fw: 700, fill: PAL.ink, anchor: 'start' });
    g.t(x0 + w + 8, ay, unit, { fs: 48, fw: 600, fill: PAL.soft, anchor: 'start' });
    [[yU, -1], [yD, 1]].forEach(function (cv) {
      var cy = cv[0], dir = cv[1];
      g.r(-hw + 40, cy - 20, 2 * hw - 80, 40, 20, { fill: PAL.card2 });
      g.p('M-16 ' + r1(cy - 6 * dir) + 'L0 ' + r1(cy + 6 * dir) + 'L16 ' + r1(cy - 6 * dir), { stroke: PAL.soft, sw: 4 });
    });
    var chips = k === 'carbs' ? ['15', '30', '50'] : k === 'long' ? ['12', '14', '16'] : ['4', '4.5', '6'];
    var cw = 72, gap = 12, cx0 = -(chips.length * cw + (chips.length - 1) * gap) / 2, cy0 = hh - 92;
    chips.forEach(function (ch, i) {
      var on = ch === s, x = cx0 + i * (cw + gap);
      g.r(x, cy0, cw, 38, 19, { fill: on ? col : PAL.card2 });
      g.t(x + cw / 2, cy0 + 26, ch, { fs: 20, fw: 700, fill: on ? PAL.on : PAL.soft });
    });
    g.t(0, hh - 22, 'Now', { fs: 20, fw: 600, fill: PAL.soft });
    btnHint(g, 60, '', {});
  };

  // ---- round screens smaller than the 390 (SPEC §14.2): what goes first ----------------------------------------------
  // the widget's page on the MIP sizes keeps value, arrow, the two places, the chart, the pill and the page dots; the
  // sensor's days go first (≤ 260), then the % column and the chart's 70 / 180 labels (≤ 240), then the clock (218)
  function drops() {
    var w = KC.w;
    return { sensor: KC.mip && w <= 260, dist: w <= 240, yLabels: w <= 240, clock: w <= 218 };
  }

  // ---- the class and the renderer ------------------------------------------------------------------------------------
  function setClass(id) {
    var key = CLS[id] ? id : 'r390';
    KC = Object.assign({ id: key }, CLS[key]);
    KC.s = KC.rect ? (KC.w > 320 ? 1.2 : 1) : KC.w / 390;
    KC.hw = KC.w / 2 / KC.s; KC.hh = KC.h / 2 / KC.s;
    SE = KC.s; PAL = KC.mip ? PAL_MIP : PAL_AMOLED;
  }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;'); }
  function elSvg(e) {
    var tf = (e.gt || '') + (e.tf ? ' ' + e.tf : ''), op = e.op !== 1 ? ' opacity="' + e.op + '"' : '';
    var paint = ' fill="' + e.fill + '" stroke="' + e.stroke + '"' + (e.sw ? ' stroke-width="' + r1(e.sw * 1000) / 1000 + '"' : '') + op + ' transform="' + tf + '"';
    var dash = e.dash && e.dash !== 'none' ? ' stroke-dasharray="' + e.dash + '"' : '';
    if (e.isP) return '<path d="' + e.d + '"' + paint + dash + ' stroke-linecap="' + e.cap + '" stroke-linejoin="' + e.join + '" fill-rule="' + (e.rule || 'evenodd') + '"/>';
    if (e.isC) return '<circle cx="' + e.cx + '" cy="' + e.cy + '" r="' + e.r + '"' + paint + dash + '/>';
    if (e.isR) return '<rect x="' + e.x + '" y="' + e.y + '" width="' + e.w + '" height="' + e.h + '" rx="' + e.rx + '"' + paint + '/>';
    return '<text x="' + e.x + '" y="' + e.y + '" font-family="' + esc(e.fam) + '" font-size="' + e.fs + '" font-weight="' + e.fw + '" text-anchor="' + e.anchor + '"' +
      (e.ls ? ' letter-spacing="' + e.ls + '"' : '') + ' fill="' + e.fill + '"' + op + ' transform="' + tf + '">' + esc(e.s[0]) + '</text>';
  }
  // svg(cfg): the screen at 1:1 (cfg.bezel: the 390 in its bezel). MIP: the whole screen through a filter that rounds
  // every pixel to the 64 colours, as the panel shows it (anti-aliased edges included)
  function svg(c) {
    c = c || {};
    var r = screen(c), W = r.w, H = r.h, bez = r.bez.length > 0, o = [];
    var vb = bez ? '-292 -292 584 584' : (-W / 2) + ' ' + (-H / 2) + ' ' + W + ' ' + H, size = c.size || (bez ? 584 : W);
    o.push('<svg xmlns="http://www.w3.org/2000/svg" width="' + (bez ? size : W) + '" height="' + (bez ? size : H) + '" viewBox="' + vb + '" role="img" aria-label="' + esc(r.label) + '">');
    o.push('<defs>' + (r.rect ? '<clipPath id="scr"><rect x="' + (-W / 2) + '" y="' + (-H / 2) + '" width="' + W + '" height="' + H + '" rx="' + (r.cls === 'x1' ? 34 : 18) + '"/></clipPath>' : '<clipPath id="scr"><circle cx="0" cy="0" r="' + W / 2 + '"/></clipPath>'));
    Object.keys(r.pats).forEach(function (id) {
      var p = r.pats[id], u = r1(p.u * 1000) / 1000;
      o.push('<pattern id="' + id + '" patternUnits="userSpaceOnUse" width="' + 2 * u + '" height="' + 2 * u + '"><rect width="' + u + '" height="' + u + '" fill="' + p.hex + '"/>' + (p.pct >= 50 ? '<rect x="' + u + '" y="' + u + '" width="' + u + '" height="' + u + '" fill="' + p.hex + '"/>' : '') + '</pattern>');
    });
    if (r.mip) o.push('<filter id="q64" x="0" y="0" width="1" height="1" color-interpolation-filters="sRGB"><feComponentTransfer>' + ['R', 'G', 'B'].map(function (ch) { return '<feFunc' + ch + ' type="discrete" tableValues="0 .3333 .3333 .6667 .6667 1"/>'; }).join('') + '</feComponentTransfer></filter>');
    o.push('</defs>');
    r.bez.forEach(function (e) { o.push(elSvg(e)); });
    o.push('<g clip-path="url(#scr)"' + (r.mip ? ' filter="url(#q64)"' : '') + '><rect x="' + (-W / 2 - 2) + '" y="' + (-H / 2 - 2) + '" width="' + (W + 4) + '" height="' + (H + 4) + '" fill="#000000"/>');
    r.els.forEach(function (e) { o.push(elSvg(e)); });
    o.push('</g></svg>');
    return o.join('\n');
  }

  // every text the screen would cut, or that runs into the rim: its box (Atkinson's widths, cap 0.7, descenders 0.22)
  // must lie 2 px inside the screen, and inside the rim where the screen draws one. The build lists them (none allowed)
  function clippedTexts(els) {
    var bad = [], m0 = (KC.rimPx || 0) + 2, W = KC.w, H = KC.h;
    els.forEach(function (e) {
      if (!e.isT || e.tf) return;
      var m = /translate\(([-\d.]+) ([-\d.]+)\) scale\(([-\d.]+)\)/.exec(e.gt), tx = +m[1], ty = +m[2], sc = +m[3], str = e.s[0], w = 0;
      for (var i = 0; i < str.length; i++) w += (e.fs >= 40 && BIG[str[i]] !== undefined ? BIG[str[i]] : WM[str[i]] !== undefined ? WM[str[i]] : 0.6);
      w = w * e.fs * (e.fs >= 40 ? 1 : 1.03) + (e.ls || 0) * str.length;
      var x0 = e.anchor === 'start' ? e.x : e.anchor === 'end' ? e.x - w : e.x - w / 2;
      var X0 = tx + x0 * sc, X1 = tx + (x0 + w) * sc, Y0 = ty + (e.y - 0.7 * e.fs) * sc, Y1 = ty + (e.y + (/[gjpqy,]/.test(str) ? 0.22 : 0.02) * e.fs) * sc;
      var out = KC.rect ? (X0 < -W / 2 + m0 || X1 > W / 2 - m0 || Y0 < -H / 2 + m0 || Y1 > H / 2 - m0)
        : [[X0, Y0], [X1, Y0], [X0, Y1], [X1, Y1]].some(function (q) { return Math.sqrt(q[0] * q[0] + q[1] * q[1]) > W / 2 - m0; });
      if (out) bad.push(str);
    });
    return bad;
  }
  // screen(cfg): cfg.cls picks the screen class (CLS; the 390 by default), cfg.screen the screen (S, or the
  // rectangles' own R where they have one), the rest is the scenario (data()). Returns the display list in layout
  // units with each element's transform to the screen's px, and the screen's size in px. c.input: another input `d` in
  // data()'s shape (the port's, tools/watchface/app-input.js), drawn instead of the made-up day.
  function screen(c) {
    c = c || {};
    setClass(c.cls);
    KC.two = c.buttons === 'two';
    var d = c.input || data(c), g = new G();
    var f = (KC.rect && R[c.screen]) || S[c.screen] || S.appA;
    f(g, d, c);
    var clipped = clippedTexts(g.els);
    var bez = c.bezel && KC.id === 'r390' ? bezel(c.metal) : [];
    var out = { els: g.els, bez: bez, pats: g.pats, w: KC.w, h: KC.h, cls: KC.id, mip: !!KC.mip, rect: !!KC.rect, s: KC.s, vb: c.bezel ? '-292 -292 584 584' : '-195 -195 390 390', label: 'Sukrara on Garmin (' + KC.name + '): ' + (c.screen || 'appA'), d: d, clipped: clipped };
    KC = null; SE = 1; PAL = PAL_AMOLED;
    return out;
  }
  return { screen: screen, svg: svg, CLS: CLS, PAL: PAL_AMOLED, PAL_MIP: PAL_MIP, bandOf: bandOf, fmtG: fmtG, contrast: contrast, near64: near64 };
})();
if (typeof module !== 'undefined') module.exports = WL;
