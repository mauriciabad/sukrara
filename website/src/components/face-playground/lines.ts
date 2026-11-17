// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The face tour's leader lines, as pure geometry in page pixels: from a part's label to its place on the face (wide:
 * an elbow), or from a number on the screen's ring to the part (narrow). tour.ts measures the page and draws them.
 */
import type { Box } from './types.ts';

export type Pt = { x: number; y: number };
export type Rect = { left: number; top: number; width: number };

/** A box in the face's units (centre 0, screen radius r) → pixels in the face's square on the page. */
export function toPx(face: Rect, r: number, b: Box): Box {
	const s = face.width / (2 * r);
	return { x: face.left + (b.x + r) * s, y: face.top + (b.y + r) * s, w: b.w * s, h: b.h * s, hit: b.hit && toPx(face, r, b.hit) };
}

const clamp = (v: number, a: number, b: number) => Math.min(Math.max(v, a), b);

/** Where a line from `from` meets a box: its near side, as level with `from` as the box allows; a point is itself. */
export function target(b: Box, from: Pt): Pt {
	if (b.w === 0 && b.h === 0) return { x: b.x, y: b.y };
	const pad = Math.min(6, b.h / 2);
	return { x: from.x < b.x + b.w / 2 ? b.x : b.x + b.w, y: clamp(from.y, b.y + pad, b.y + b.h - pad) };
}

/** A box's point on the given side's edge, level with its middle; a point is itself. */
export const edge = (b: Box, left: boolean): Pt => (b.w || b.h ? { x: left ? b.x : b.x + b.w, y: b.y + b.h / 2 } : { x: b.x, y: b.y });

const f = (n: number) => Math.round(n * 10) / 10;

/** An elbow: flat out of the label, across to the ring (`rim`) at the part's height, then flat into the part. */
export function elbow(from: Pt, to: Pt, rim: number): string {
	const stub = from.x + Math.sign(rim - from.x) * Math.min(10, Math.abs(rim - from.x) / 2);
	return `M${f(from.x)} ${f(from.y)}H${f(stub)}L${f(rim)} ${f(to.y)}H${f(to.x)}`;
}

/**
 * Numbers on a ring of radius `ring` round `c`, each at its point's angle, pushed apart until neighbours are at least
 * `gap` pixels apart along the ring. Returns each one's place, in the order given.
 */
export function ringPlaces(c: Pt, ring: number, points: Pt[], gap: number): Pt[] {
	const tau = 2 * Math.PI;
	const items = points.map((p, i) => ({ i, a: (Math.atan2(p.x - c.x, -(p.y - c.y)) + tau) % tau })).sort((p, q) => p.a - q.a);
	const min = gap / ring;
	for (let pass = 0; pass < 12; pass++)
		for (let k = 0; k < items.length && items.length > 1; k++) {
			const a = items[k];
			const b = items[(k + 1) % items.length];
			const d = (b.a - a.a + tau) % tau;
			if (d < min) {
				a.a -= (min - d) / 2;
				b.a += (min - d) / 2;
			}
		}
	const out: Pt[] = [];
	for (const it of items) out[it.i] = { x: f(c.x + ring * Math.sin(it.a)), y: f(c.y - ring * Math.cos(it.a)) };
	return out;
}
