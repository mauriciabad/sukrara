// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Where each of Aura's parts sits on the screen, read from aura.js's layout() (the numbers it draws with), so the face
 * tour's lines follow the parts as the face changes. Boxes in the screen's units: (0, 0) the centre, the screen the
 * circle r = 195, y down. A part the moment doesn't draw gets where it shows at a plain moment, marked not shown.
 */
import type { Box, Place, Values } from '../types.ts';

type Layout = Record<string, any>;

const box = (x: number, y: number, w: number, h: number): Box => ({ x, y, w, h });
/** The point at radius r, deg degrees clockwise from 12 o'clock. */
const at = (r: number, deg: number): Box => {
	const a = (deg * Math.PI) / 180;
	return box(Math.round(r * Math.sin(a) * 10) / 10, Math.round(-r * Math.cos(a) * 10) / 10, 0, 0);
};
const on = (d: unknown) => d === 'inline';

/** A plain moment (a fresh reading in range, awake), the base of the moments that show what another may hide. */
const PLAIN: Values = { sensor: 'ok', glucose: 112, rate: 0.4, trend: true, age: 0.5, mode: 'awake', privacy: 'open', phone: true };
const REFERENCES: Values[] = [
	{ ...PLAIN, seconds: true },
	{ ...PLAIN, glucose: 250, rate: 2, status: 'auto' },
];

/** Each part's boxes in a layout, or null where it isn't drawn. */
export function placesIn(l: Layout, values: Values): Record<string, Box[] | null> {
	const h = l.halo;
	return {
		// the date runs along the top arc (r 160); its width barely changes
		date: [box(-64, -182, 128, 26)],
		// the middle of the top field's top edge
		top: h.fw > 0 ? [{ ...box(h.fx + h.fw / 2, h.fy, 0, 0), hit: box(h.fx, h.fy, h.fw, h.fh) }] : null,
		time: [box(h.tx, h.ty, h.tw, h.th)],
		value: on(l.val.disp) ? [box(l.val.bx, l.val.by, l.val.bw, l.val.bh)] : h.mw > 0 ? [box(h.mx, h.my, h.mw, h.mh)] : null,
		trend: on(l.sp.disp) ? [box(h.sx, h.sy, h.sw, h.sh)] : on(l.val.disp) && on(l.val.arrowD) ? [box(l.val.ax - 14, l.val.ay - 14, 28, 28)] : null,
		// beside the bead, not on it (the bead is r 7 on r 182)
		rail: on(l.rail.disp) && on(l.bead.disp) ? [at(194, l.bead.deg)] : null,
		battery: on(l.batt.disp) ? [at(182, 270)] : null,
		pill: on(l.pill.disp) || on(l.chip.disp) ? [box(h.cx, h.cy, h.cw, h.ch)] : null,
		// fixed under the middle of the two (they run along the arc r 172, their glyphs inwards)
		fields: on(l.b.disp) ? [{ ...at(178, 180), hit: box(-150, 120, 300, 70) }] : null,
		// the sweep goes round once a minute: pointed at in one spot by the rim, top right
		sweep: on(l.radar.disp) ? [at(188, 28)] : null,
		// the dial is the whole ground: pointed at where nothing else is drawn; night and always-on draw none
		dial: values.mode === 'night' || values.mode === 'aod' ? null : [at(160, 315)],
	};
}

/**
 * Every part's place with these inputs. One not drawn now points at the middle of where it shows at a plain moment
 * (the pill a little lower: its middle would sit on the value's baseline).
 */
export function places(layout: (cfg: Values) => Layout, values: Values): Record<string, Place> {
	const now = placesIn(layout(values), values);
	const out: Record<string, Place> = {};
	for (const [id, boxes] of Object.entries(now)) {
		if (boxes) {
			out[id] = { boxes, shown: true, part: id };
			continue;
		}
		const b = REFERENCES.map((r) => placesIn(layout({ ...values, ...r }), { ...values, ...r })[id]).find(Boolean)?.[0] ?? box(0, 0, 0, 0);
		out[id] = { boxes: [box(b.x + b.w / 2, b.y + b.h / 2 + (id === 'pill' ? 36 : 0), 0, 0)], shown: false, part: id };
	}
	return out;
}
