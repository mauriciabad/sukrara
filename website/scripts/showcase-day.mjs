// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Makes the showcase's made-up day (src/components/face-playground/day/day.json) from a real glucose export: a
// Sukrara or LibreView CSV. A real fortnight shows few situations in a short time, so this picks real episodes from it
// (a night low with carbs, two meals with insulin, a high with a correction, calm stretches) and stitches them into
// one 24-hour day that loops, around a story that also has the sensor running out and a new one warming up, the phone
// out of reach, a mute and the night. Only the result is committed; the export stays with its owner.
//
//   node scripts/showcase-day.mjs <export.csv>            → writes day.json
//   node scripts/showcase-day.mjs <export.csv> --print    → prints the episodes it picked

import fs from 'node:fs';

const [file, flag] = process.argv.slice(2);
if (!file) {
	console.error('usage: node scripts/showcase-day.mjs <export.csv> [--print]');
	process.exit(2);
}
const OUT = new URL('../src/components/face-playground/day/day.json', import.meta.url);
const STEP = 5;
const DAY = 1440;

// ---- the export: readings on a 5-minute grid, and treatments ----------------------------------------------------------

/** "2026-10-15 09:00" (Sukrara) or "21-12-2022 02:35 PM" (LibreView) → minutes since the epoch, local wall time. */
function minutes(s) {
	let m = s.match(/^(\d{4})-(\d\d)-(\d\d) (\d\d):(\d\d)/);
	if (m) return Date.UTC(+m[1], m[2] - 1, +m[3], +m[4], +m[5]) / 60000;
	m = s.match(/^(\d\d)-(\d\d)-(\d{4}) (\d\d):(\d\d) (AM|PM)/);
	if (!m) return null;
	const h = (+m[4] % 12) + (m[6] === 'PM' ? 12 : 0);
	return Date.UTC(+m[3], m[2] - 1, +m[1], h, +m[5]) / 60000;
}

const rows = fs
	.readFileSync(file, 'utf8')
	.split(/\r?\n/)
	.slice(2)
	.map((l) => l.split(','));
const glucose = new Map(); // grid minute → mg/dL
const treatments = []; // { at, kind, amount }
for (const r of rows) {
	const at = minutes(r[2] ?? '');
	if (at === null) continue;
	const num = (i) => (r[i] && Number.isFinite(+r[i]) ? +r[i] : 0);
	if (r[3] === '0' && r[4]) glucose.set(Math.round(at / STEP) * STEP, +r[4]);
	if (r[3] === '4' || r[3] === '5') {
		if (num(7)) treatments.push({ at, kind: 'rapid', amount: num(7) });
		if (num(12)) treatments.push({ at, kind: 'long', amount: num(12) });
		if (num(9)) treatments.push({ at, kind: 'carbs', amount: num(9) });
	}
}
treatments.sort((a, b) => a.at - b.at);

/** The readings from `from` to `to` (minutes around `at`), or null when one is missing. */
function trace(at, from, to) {
	const out = [];
	for (let m = Math.round(at / STEP) * STEP + from; m <= Math.round(at / STEP) * STEP + to; m += STEP) {
		const v = glucose.get(m);
		if (v === undefined) return null;
		out.push(v);
	}
	return out;
}
const near = (at, kind, within) => treatments.filter((t) => t.kind === kind && Math.abs(t.at - at) <= within);
const sum = (ts) => ts.reduce((s, t) => s + t.amount, 0);
const hour = (at) => Math.floor((((at % DAY) + DAY) % DAY) / 60);

// ---- the episodes -----------------------------------------------------------------------------------------------------

/** Picks the best-scoring candidate; each is { at, from, to, trace, items, score }. */
function pick(name, cands, used) {
	const ok = cands.filter((c) => c && c.trace && !used.has(c.at)).sort((a, b) => b.score - a.score);
	if (!ok.length) throw new Error(`no ${name} in this export`);
	used.add(ok[0].at);
	return { name, ...ok[0] };
}
const used = new Set();
const items = (at, within) =>
	['rapid', 'carbs']
		.map((k) => ({ k, a: sum(near(at, k, within)) }))
		.filter((i) => i.a > 0)
		.map((i) => ({ kind: i.k, amount: i.k === 'carbs' ? Math.round(i.a) : Math.round(i.a * 2) / 2 }));

// a low that comes back up, best at night: its bottom at 55–68. The owner rarely logs carbs, so a low without a log
// gets the 15 g Sukrara suggests
const low = pick(
	'low',
	[...glucose.keys()].map((at) => {
		const tr = trace(at, -75, 90);
		if (!tr) return null;
		const min = Math.min(...tr);
		if (tr[15] !== min || min < 55 || min > 68 || Math.max(...tr) > 150) return null;
		const logged = sum(near(at, 'carbs', 20));
		const rise = tr.at(-1) - min;
		const score = rise + (hour(at) < 6 ? 40 : 0) - (rise > 70 ? 2 * (rise - 70) : 0);
		return { at, from: -75, to: 90, trace: tr, items: [{ kind: 'carbs', amount: Math.round(logged || 15) }], score };
	}),
	used,
);

// a meal: a dose of rapid insulin and a clear rise that comes back. Carbs as logged, or made up from the dose (10 g a
// unit, to the 5 g) when the owner didn't log them
const meals = (from, to, want) =>
	treatments
		.filter((t) => t.kind === 'rapid' && t.amount >= 3)
		.map((t) => {
			const tr = trace(t.at, from, to);
			if (!tr) return null;
			const base = tr[-from / STEP];
			const peak = Math.max(...tr);
			const end = tr.at(-1);
			if (base > 170 || peak - base < 30) return null;
			const units = sum(near(t.at, 'rapid', 10));
			const carbs = sum(near(t.at, 'carbs', 10)) || Math.round(units * 2) * 5;
			const items = [{ kind: 'rapid', amount: Math.round(units * 2) / 2 }, { kind: 'carbs', amount: Math.round(carbs) }];
			const score = -Math.abs(peak - base - want) - Math.abs(end - base) / 2 - (peak > 250 ? 80 : 0);
			return { at: t.at, from, to, trace: tr, items, score };
		});
const breakfast = pick('breakfast', meals(-30, 180, 70), used);
const lunch = pick('lunch', meals(-15, 210, 100), used);
const dinner = pick('dinner', meals(-15, 165, 60), used);

// a high corrected with insulin alone: over 200 at the dose, back towards range after
const high = pick(
	'high',
	treatments
		.filter((t) => t.kind === 'rapid' && !near(t.at, 'carbs', 90).length)
		.map((t) => {
			const tr = trace(t.at, -45, 150);
			if (!tr) return null;
			const at0 = tr[9];
			const peak = Math.max(...tr);
			if (at0 < 200 || peak > 300 || Math.min(...tr) < 80) return null;
			return { at: t.at, from: -45, to: 150, trace: tr, items: items(t.at, 5), score: peak - tr.at(-1) - Math.abs(peak - 250) };
		}),
	used,
);

// calm stretches in range, for the hours between: flattest first
const calm = (len) => {
	const out = [];
	for (const at of [...glucose.keys()].filter((m) => m % 60 === 0)) {
		const tr = trace(at, 0, len);
		if (!tr || Math.min(...tr) < 85 || Math.max(...tr) > 150) continue;
		const mean = tr.reduce((s, v) => s + v, 0) / tr.length;
		out.push({ at, from: 0, to: len, trace: tr, items: [], score: -Math.sqrt(tr.reduce((s, v) => s + (v - mean) ** 2, 0) / tr.length) });
	}
	return out;
};

// the long-acting dose most often taken
const longs = treatments.filter((t) => t.kind === 'long').map((t) => Math.round(t.amount));
const basal = longs.length ? longs.sort((a, b) => longs.filter((x) => x === b).length - longs.filter((x) => x === a).length)[0] : 18;

// ---- the story: where each episode goes in the day (minutes from midnight) ----------------------------------------------

const H = (h, m = 0) => h * 60 + m;
const story = [
	{ at: H(0), ep: pick('night', calm(105), used) }, // 0:00–1:45
	{ at: H(3), ep: low }, // the carbs at 3:00
	{ at: H(4, 30), ep: pick('early', calm(165), used) }, // 4:30–7:15
	{ at: H(7, 45), ep: breakfast },
	{ at: H(11, 15), ep: high }, // the correction at 11:15
	{ at: H(14, 15), ep: lunch },
	{ at: H(18), ep: pick('afternoon', calm(180), used) }, // 18:00–21:00
	{ at: H(21, 15), ep: dinner },
];

// each episode's readings where the story puts them; where two meet, the later one wins and its level eases in
const day = new Array(DAY / STEP).fill(null);
for (const { at, ep } of story) {
	const start = (at + ep.from) / STEP;
	const prev = day[(start - 1 + day.length) % day.length];
	const offset = prev === null ? 0 : prev - ep.trace[0];
	ep.trace.forEach((v, i) => {
		const ease = Math.max(0, 1 - i / 9); // the offset fades over 45 min
		if (start + i < day.length) day[start + i] = v + offset * ease; // the day ends at midnight: no wrapping
	});
}
// fill any gap left between episodes by a straight line, then join the end of the day to its start (the loop)
for (let i = 0; i < day.length; i++) {
	if (day[i] !== null) continue;
	let j = i;
	while (day[j % day.length] === null) j++;
	const a = day[(i - 1 + day.length) % day.length];
	const b = day[j % day.length];
	for (let k = i; k < j; k++) day[k % day.length] = a + ((b - a) * (k - i + 1)) / (j - i + 1);
}
const tail = 12; // the last hour eases to midnight's level
const jump = day[0] - day[day.length - 1];
for (let k = 0; k < tail; k++) day[day.length - tail + k] += (jump * (k + 1)) / (tail + 1);
const glucoseOut = day.map((v) => Math.round(v));

// treatments where the story put their episodes, and the holds Sukrara puts after them (lows 15 min, highs 60 min)
const treat = story
	.filter(({ ep }) => ep.items.length)
	.map(({ at, ep }) => ({ at, items: ep.items }));
treat.push({ at: H(22, 30), items: [{ kind: 'long', amount: basal }] });
treat.sort((a, b) => a.at - b.at);
const quiet = treat
	.filter((t) => t.items.some((i) => i.kind !== 'long'))
	.map((t) => [t.at, t.at + (t.items.some((i) => i.kind === 'rapid') ? 60 : 15)]);

const out = {
	about: 'Made up from real episodes of a Sukrara export (scripts/showcase-day.mjs): a 24-hour day that loops. Minutes from midnight.',
	step: STEP,
	glucose: glucoseOut,
	treatments: treat,
	quiet,
	muted: [[H(19, 30), H(20, 30)]],
	phoneAway: [[H(16, 50), H(18, 35)]],
	// Do Not Disturb (the night): short, so the tour shows it without spending a third of its loop on it
	dnd: [[H(23), H(23, 45)]],
	// the sensor: hours left, sped up (a fortnight in a day) and slower in its last 8 h; it ends, a new one warms up an hour
	sensor: { ended: H(15), changed: H(15, 20), ready: H(16, 20), left: [[H(16, 20), 360], [H(12), 8], [H(15), 0]] },
	// the watch's battery in days: charged while getting up, down to amber by the next dawn
	battery: [[H(7), 10], [H(6, 30), 1.6], [H(6, 50), 1.6]],
	// steps, from midnight
	steps: [[H(0), 0], [H(7), 120], [H(9), 2400], [H(13), 5200], [H(17), 7400], [H(18, 30), 10900], [H(22), 12800], [H(24), 12900]],
	chapters: [
		['night', H(0)],
		['low', H(2, 30)],
		['morning', H(7)],
		['breakfast', H(7, 45)],
		['high', H(10, 30)],
		['ending', H(12)],
		['lunch', H(14, 15)],
		['ended', H(15)],
		['warmup', H(15, 20)],
		['away', H(16, 50)],
		['back', H(18, 35)],
		['muted', H(19, 30)],
		['dinner', H(21, 15)],
		['basal', H(22, 30)],
		['sleep', H(23)],
	].map(([id, at]) => ({ id, at })),
};

if (flag === '--print') {
	const iso = (m) => new Date(m * 60000).toISOString().slice(0, 16).replace('T', ' ');
	for (const { at, ep } of story) console.log(`${String(Math.floor(at / 60)).padStart(2)}:${String(at % 60).padStart(2, '0')}  ${ep.name.padEnd(10)} from ${iso(ep.at)}  ${JSON.stringify(ep.items)}  ${Math.min(...ep.trace)}–${Math.max(...ep.trace)}`);
	console.log('basal', basal);
} else {
	fs.mkdirSync(new URL('.', OUT), { recursive: true });
	fs.writeFileSync(OUT, JSON.stringify(out, null, '\t').replace(/\[\s+([\d\s.,-]+?)\s+\]/g, (_, xs) => `[${xs.replace(/\s+/g, ' ')}]`) + '\n');
	console.log(`wrote ${OUT.pathname}`);
}
