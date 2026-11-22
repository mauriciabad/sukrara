// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The faces' pure parts: the clock and the shared ticker, the scenarios and the made-up day, and Aura's adapter over
// the real aura.js and cases.json (its parts, examples and scoped ids). npm test (node --test, Node's own TypeScript).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { test } from 'node:test';
import { advance, clampTo, start, wrap } from './clock.ts';
import { HISTORY, liveAt, SCENARIOS } from './scenarios.ts';
import { ticker } from './ticker.ts';
import { scopeIds } from './svg-ids.ts';
import { chapterAt, scene, START } from './tour-model.ts';
import { curve, dayScenario, inSpan, keyframes, type DayData } from './day/day.ts';
import { auraFromLive, createAura, type AuraCase } from './aura/adapter.ts';
import { evaluateAura } from './aura/evaluate.ts';
import { edge, elbow, ringPlaces, target, toPx } from './lines.ts';
import { PRESETS_ES } from './aura/presets.ts';

const repo = new URL('../../../../', import.meta.url);
const aura = evaluateAura(fs.readFileSync(new URL('tools/watchface/aura.js', repo), 'utf8'));
const { cases } = JSON.parse(fs.readFileSync(new URL('contracts/watchface/aura/cases.json', repo), 'utf8')) as { cases: AuraCase[] };
const dayData = JSON.parse(fs.readFileSync(new URL('./day/day.json', import.meta.url), 'utf8')) as DayData;
const face = createAura(aura, cases);
const scenario = (id: string) => SCENARIOS.find((s) => s.id === id)!;
const day = dayScenario(dayData);
const H = (h: number, m = 0) => h * 60 + m;

test('the clock runs at its speed, stands still when paused, stops at the end or loops', () => {
	const c = advance(start(60), 2000);
	assert.equal(c.minutes, 2);
	assert.equal(advance({ ...c, running: false }, 5000).minutes, 2);
	assert.deepEqual(clampTo(advance(c, 60000), 30), { minutes: 30, speed: 60, running: false });
	// 600×: ten simulated minutes a second; a day loops after 144 s
	assert.equal(advance(start(600), 1000).minutes, 10);
	assert.equal(wrap(advance(start(600), 145_000), 1440).minutes, 10);
	assert.equal(wrap({ minutes: -5, speed: 1, running: true }, 1440).minutes, 1435);
});

test('one ticker drives every listener with the same moment', () => {
	const frames: ((t: number) => void)[] = [];
	const g = globalThis as unknown as Record<string, unknown>;
	const saved = { raf: g.requestAnimationFrame, caf: g.cancelAnimationFrame };
	g.requestAnimationFrame = (f: (t: number) => void) => frames.push(f);
	g.cancelAnimationFrame = () => void frames.splice(0);
	try {
		const t = ticker({ speed: 600, period: 1440, minutes: 1435 });
		const seen: number[][] = [[], []];
		t.listen((c) => seen[0].push(c.minutes));
		const stop = t.listen((c) => seen[1].push(c.minutes));
		t.play();
		const now = performance.now();
		frames.shift()!(now + 1000); // a second at 600×: ten minutes, past midnight
		assert.ok(Math.abs(t.clock.minutes - 5) < 0.01);
		assert.deepEqual(seen[0], seen[1]);
		stop();
		frames.shift()!(now + 1500);
		assert.equal(seen[0].length, seen[1].length + 1);
		t.pause();
		assert.equal(t.clock.running, false);
		assert.equal(frames.length, 0);
		t.seek(-60);
		assert.equal(t.clock.minutes, 1380);
		// pausing a stopped clock tells no one
		const before = seen[0].length;
		t.pause();
		assert.equal(seen[0].length, before);
		// the playground's: a scenario that stops at its end, at the speed picked
		const p = ticker({ speed: 60, period: 1440 });
		p.set({ period: 30, stop: true, speed: 600 });
		p.seek(0);
		p.play();
		frames.shift()!(performance.now() + 5000); // 50 minutes at 600×: stops at 30
		assert.deepEqual(p.clock, { minutes: 30, speed: 600, running: false });
		assert.equal(frames.length, 0, 'a stopped clock asks for no more frames');
	} finally {
		g.requestAnimationFrame = saved.raf;
		g.cancelAnimationFrame = saved.caf;
	}
});

test('a scenario on a moving face: whole minutes and no sweep when fast, the day’s chapters', () => {
	const s = scenario('night-low');
	const slow = scene(face, s, 61.5, false);
	assert.equal(slow.values.time, '2:31:30');
	assert.deepEqual(slow.still, {});
	const fast = scene(face, s, 61.5, true);
	assert.equal(fast.values.time, '2:31:00');
	assert.deepEqual(fast.still, face.moving);
	assert.equal(chapterAt(day, START).id, chapterAt(day, START + 1440).id);
	assert.equal(chapterAt(day, day.chapters[1].at).id, day.chapters[1].id);
	assert.equal(chapterAt(day, 0).id, day.chapters[0].at === 0 ? day.chapters[0].id : day.chapters.at(-1)!.id);
});

test('a reading that stops arriving ages with the clock: late, old, then none', () => {
	const s = scenario('phone-away');
	const ages = [3, 8, 30, 200].map((t) => liveAt(s, t).reading!.ageMin);
	assert.deepEqual(ages, [0, 4, 26, 196]);
	assert.equal(liveAt(s, 30).phone, false);
	assert.equal(liveAt(s, 230).reading!.ageMin, 0);
	const v = auraFromLive(liveAt(s, 30));
	assert.equal(v.age, 26);
	assert.equal(v.phone, false);
	assert.match(face.render(v as never, 'en'), /No reading since/);
});

test('waits count down and treatments age', () => {
	const s = scenario('meal');
	assert.deepEqual(liveAt(s, 20).hold, { kind: 'quiet', leftMin: 55 });
	assert.equal(liveAt(s, 74.5).hold!.leftMin, 1);
	assert.equal(liveAt(s, 80).hold, null);
	assert.equal(liveAt(s, 100).treatment!.agoMin, 85);
	assert.equal(liveAt(s, 10).treatment, null);
	// the card's slot beside the value: the carbs ride along while the insulin's quiet runs, then the insulin alone
	assert.deepEqual(liveAt(s, 20).treatment!.items.map((i) => i.kind), ['rapid', 'carbs']);
	assert.deepEqual(liveAt(s, 80).treatment!.items.map((i) => i.kind), ['rapid']);
	// 15 g of carbs stay 45 min (3 min per gram)
	assert.ok(liveAt(scenario('night-low'), 136).treatment);
	assert.equal(liveAt(scenario('night-low'), 137).treatment, null);
});

test('the clock and the history follow the scenario', () => {
	const l = liveAt(scenario('night-low'), 61.5);
	assert.equal(`${l.now.getHours()}:${l.now.getMinutes()}:${l.now.getSeconds()}`, '2:31:30');
	assert.equal(l.history.length, HISTORY);
	assert.equal(auraFromLive(l).time, '2:31:30');
	assert.equal(auraFromLive(l).mode, 'night');
	const warm = liveAt(scenario('new-sensor'), 30);
	assert.equal(warm.reading, null);
	assert.deepEqual(auraFromLive(warm).sensor, 'warmup');
});

test('the adapter maps a moment onto aura.js inputs', () => {
	const v = auraFromLive({
		now: new Date(2026, 10, 16, 9, 5, 7),
		reading: { glucose: 150, rate: null, ageMin: 2 },
		history: [140, null, 145],
		phone: true,
		sensor: { state: 'ok', leftH: 5 },
		hold: { kind: 'muted', leftMin: 12 },
		treatment: { items: [{ kind: 'carbs', amount: 15 }], agoMin: 4 },
		dnd: false,
		watch: { batteryDays: 1.5, steps: 4200 },
		tir: null,
	});
	assert.deepEqual(v, {
		time: '9:05:07', phone: true, mode: 'awake', sensor: 'ending', sensorLeft: 5, sensorLife: 15, sensorDates: true, glucose: 150, rate: 0, trend: false, age: 2,
		history: [140, null, 145], spark: [140, null, 145, 150], status: 'muted', left: 12, battery: 1.5, steps: 4200, tir: null,
		last: { items: [{ k: 'carbs', a: '15' }], ago: 4 },
	});
});

test('defaults are aura.js own; every preset is a case of cases.json, drawn as aura.js draws it', () => {
	assert.equal(face.defaults().glucose, new aura.AuraFace({}).opt().glucose);
	assert.equal(face.presets.length, Object.keys(PRESETS_ES).length, 'a preset names a case that no longer exists');
	const bare = (svg: string) => svg.replace(/ data-part="\w+"/g, '');
	for (const p of face.presets) {
		const c = cases.find((x) => x.id === p.id)!;
		assert.equal(bare(face.render(p.values, 'en')), aura.svg({ ...face.defaults(), ...c.cfg, lang: 'en', size: 390, bezel: false }));
	}
	// the screen alone: no bezel (it carries a watchmaker's print)
	assert.match(face.render(face.defaults(), 'en'), /viewBox="-195 -195 390 390"/);
});

test('every part names elements of the drawing, and only the screen’s', () => {
	// with the battery on the left and the sensor rail on the right, every element a part can name is drawn
	const values = { ...face.defaults(), status: 'quiet', glucose: 220, sideL: 'battery', sideR: 'sensor' };
	const svg = face.render(values, 'en');
	const marked = new Set([...svg.matchAll(/data-part="(\w+)"/g)].map((m) => m[1]));
	const named = new Set([...Object.values(face.places(values)).map((p) => p.part), 'rail']);
	for (const p of named) assert.ok(marked.has(p), `no element of ${p}`);
	for (const id of marked) assert.ok(named.has(id), `${id} isn't a part`);
	// the bezel and the masks belong to no part
	assert.doesNotMatch(svg.slice(0, svg.indexOf('clip-path="url(')), /data-part/);
});

test('places: every part is somewhere on the screen, drawn or where it shows', () => {
	const at = face.places(face.defaults());
	assert.deepEqual(Object.keys(at).sort(), face.parts.map((p) => p.id).sort());
	for (const [id, p] of Object.entries(at))
		for (const b of p.boxes) assert.ok(Math.hypot(b.x + b.w / 2, b.y + b.h / 2) <= face.radius, `${id} is off the screen`);
	// nothing to do in range: the pill isn't drawn; its line points under the value, where it shows
	assert.equal(at.pill.shown, false);
	assert.ok(at.pill.boxes[0].y > at.value.boxes[0].y + at.value.boxes[0].h / 2);
	assert.equal(face.places({ ...face.defaults(), glucose: 250, rate: 2, status: 'auto' }).pill.shown, true);
	// the sweep's spot stays put as it goes round; the night draws no dial
	const sweep = (t: string) => face.places({ ...face.defaults(), time: t }).sweep.boxes[0];
	assert.deepEqual(sweep('10:09:07'), sweep('10:09:42'));
	assert.equal(face.places({ ...face.defaults(), mode: 'night' }).dial.shown, false);
	// a warm-up has no reading: the rail, the last hours and the pill keep their usual places, not the centre
	const warm = face.places({ ...face.defaults(), sensor: 'warmup' });
	for (const id of ['sideR', 'trend', 'pill']) assert.ok(!warm[id].shown && Math.hypot(warm[id].boxes[0].x, warm[id].boxes[0].y) > 40, id);
});

test('the sides: each label names the element its setting picks, on its own side of the face', () => {
	const at = face.places({ ...face.defaults(), sideL: 'sensor', sideR: 'battery' });
	assert.deepEqual([at.sideL.part, at.sideL.shown, at.sideR.part, at.sideR.shown], ['sensor', true, 'battery', true]);
	assert.ok(at.sideL.boxes[0].x < 0 && at.sideR.boxes[0].x > 0);
	// the glucose rail on the left: beside its mirrored bead
	const rail = face.places({ ...face.defaults(), sideL: 'level', sideR: 'none' });
	assert.equal(rail.sideL.part, 'rail');
	assert.ok(rail.sideL.boxes[0].x < -180);
	// nothing on a side: not shown, pointed at where its element would be
	assert.equal(rail.sideR.shown, false);
	assert.ok(rail.sideR.boxes[0].x > 150);
	// always-on draws no side element
	assert.equal(face.places({ ...face.defaults(), mode: 'aod' }).sideL.shown, false);
	// every choice has its words and icon; the made-up day gives the sensor rail its hours left
	for (const p of face.parts.filter((x) => x.choice))
		for (const [k, o] of Object.entries(p.choice!.options)) assert.ok(o.icon && o.name.es && o.about.en, `${p.id}: ${k}`);
	const v = auraFromLive(liveAt(day, H(10)));
	assert.equal(v.sensorDates, true);
	assert.ok(Number(v.sensorLeft) > 0);
});

test('the tour’s settings set inputs the face has; a dial preview is the dial alone, at the reading', () => {
	const s = face.settings;
	for (const g of s.groups) for (const k of g.keys) assert.ok(s.list.some((x) => x.key === k), k);
	const values = s.apply(s.defaults);
	assert.deepEqual(values.fields, ['tir', 'treatment']);
	assert.match(face.render({ ...face.defaults(), ...values }, 'es'), /<svg/);
	const p = face.looks.preview({ ...face.defaults(), glucose: 60, sensor: 'ok', mode: 'night' });
	assert.equal(p.glucose, 60);
	assert.equal(p.mode, 'awake');
	assert.equal(p.halo, 'none');
	assert.ok(Number(p.rate) < 0, 'below the ideal it falls further, where the aura spreads most');
	assert.equal(face.looks.preview({ ...face.defaults(), sensor: 'warmup' }).glucose, 112);
});

test('leader lines: into the near side of a part, elbows, numbers kept apart on the ring', () => {
	const face_ = { left: 100, top: 50, width: 390 };
	assert.deepEqual(toPx(face_, 195, { x: 0, y: 0, w: 10, h: 0 }), { x: 295, y: 245, w: 10, h: 0, hit: undefined });
	const b = { x: 200, y: 100, w: 50, h: 40 };
	assert.deepEqual(target(b, { x: 0, y: 0 }), { x: 200, y: 106 });
	assert.deepEqual(target(b, { x: 400, y: 120 }), { x: 250, y: 120 });
	assert.deepEqual(edge(b, false), { x: 250, y: 120 });
	assert.equal(elbow({ x: 0, y: 10 }, { x: 100, y: 40 }, 60), 'M0 10H10L60 40H100');
	const placed = ringPlaces({ x: 0, y: 0 }, 100, [{ x: 0, y: -50 }, { x: 1, y: -50 }, { x: 50, y: 0 }], 20);
	assert.ok(Math.hypot(placed[0].x - placed[1].x, placed[0].y - placed[1].y) >= 19, 'two numbers overlap');
	assert.ok(placed.every((p) => Math.abs(Math.hypot(p.x, p.y) - 100) < 0.2));
});

test('scoped ids: each drawing refers only to its own', () => {
	const a = face.render(face.defaults(), 'en', { id: 'f1' });
	const ids = [...a.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
	assert.ok(ids.length > 5 && ids.every((id) => id.startsWith('f1-')));
	const refs = [...a.matchAll(/url\(#([^)]+)\)|href="#([^"]+)"/g)].map((m) => m[1] ?? m[2]);
	assert.ok(refs.every((r) => ids.includes(r)), 'a reference to an id the drawing lacks');
	assert.equal(scopeIds('<svg><circle fill="url(#other)"/></svg>', 'x'), '<svg><circle fill="url(#other)"/></svg>');
});

test('examples: every one a case of cases.json with words in both languages; live ones stay in their loop', () => {
	const all = face.examples.flatMap((g) => g.examples);
	assert.ok(all.length >= 50);
	assert.equal(new Set(all.map((e) => e.id)).size, all.length, 'two examples with one id');
	for (const e of all) {
		for (const l of ['en', 'es'] as const) assert.ok(e.title[l] && e.note[l], `${e.id}: no ${l} words`);
		if (!e.live) continue;
		for (const m of [0, e.live.loop / 2, e.live.loop - 0.1]) assert.match(face.render({ ...e.values, ...e.live.at(m) }, 'es'), /<svg/);
	}
	const dials = face.looks.options.map((o) => o.value);
	assert.deepEqual(dials, ['halftone', 'topo', 'water', 'laser', 'glow', 'map', 'black']);
	assert.deepEqual(face.moving, { seconds: false });
});

test('keyframes and the glucose curve loop through midnight', () => {
	const k = keyframes([[H(22), 2], [H(2), 6]]);
	assert.equal(k(H(0)), 4);
	assert.equal(k(H(12)), 4); // from 2:00 back to 22:00
	const c = curve([100, 110, 120, 110], 5);
	assert.equal(c(5), 110);
	assert.ok(Math.abs(c(20) - 100) < 1e-9, 'the curve goes back to the first sample');
	assert.ok(inSpan(H(23, 30), [H(23), H(7)]) && inSpan(H(3), [H(23), H(7)]) && !inSpan(H(12), [H(23), H(7)]));
});

test('the made-up day: a full day that loops without a jump', () => {
	assert.equal(dayData.glucose.length * dayData.step, 1440);
	assert.ok(dayData.glucose.every((g) => g >= 40 && g <= 400));
	const step = (a: number, b: number) => Math.abs(a - b);
	assert.ok(step(dayData.glucose[0], dayData.glucose.at(-1)!) <= 12, 'the day ends far from where it starts');
	for (let i = 1; i < dayData.glucose.length; i++) assert.ok(step(dayData.glucose[i], dayData.glucose[i - 1]) <= 35, `a jump at ${i * 5} min`);
	const a = liveAt(day, H(10));
	const b = liveAt(day, H(10) + 1440);
	assert.deepEqual(a, b);
	// the history before midnight is yesterday's evening
	assert.equal(liveAt(day, 5).history.filter((x) => x !== null).length, HISTORY);
});

test('the made-up day shows a bit of everything', () => {
	const at = (t: number) => auraFromLive(liveAt(day, t)) as Record<string, unknown>;
	const moments = Array.from({ length: 1440 }, (_, t) => ({ t, v: at(t) }));
	const some = (f: (v: Record<string, unknown>) => boolean) => moments.some(({ v }) => f(v));
	const g = (v: Record<string, unknown>) => (v.sensor === 'ok' || v.sensor === 'ending') && Number(v.age) < 1.5 ? Number(v.glucose) : NaN;
	assert.ok(some((v) => g(v) >= 70 && g(v) <= 180), 'in range');
	assert.ok(some((v) => g(v) < 70), 'a low');
	// the night (Do Not Disturb) shows, briefly: there to be seen, not a third of the loop
	const night = moments.filter(({ v }) => v.mode === 'night').length;
	assert.ok(night > 0 && night <= 60, `the night lasts ${night} min`);
	assert.ok(some((v) => g(v) > 180 && v.status === 'auto'), 'a high that asks for insulin');
	assert.ok(some((v) => g(v) > 180 && v.status === 'quiet'), 'a high waiting after insulin');
	assert.ok(some((v) => v.status === 'muted'), 'a mute');
	assert.ok(some((v) => v.sensor === 'ending'), 'the sensor’s last hours');
	assert.ok(some((v) => v.sensor === 'ended') && some((v) => v.sensor === 'warmup') && some((v) => v.sensor === 'almost'), 'a sensor change');
	assert.ok(some((v) => v.phone === false && Number(v.age) > 1.5 && Number(v.age) <= 5), 'a late reading');
	assert.ok(some((v) => v.phone === false && Number(v.age) > 5 && Number(v.age) <= 20), 'an old reading');
	assert.ok(some((v) => v.phone === false && Number(v.age) > 20), 'no reading since');
	assert.ok(some((v) => v.tir === null) && some((v) => typeof v.tir === 'number'), 'time in range, once known');
	assert.ok(some((v) => Number(v.battery) < 2), 'the watch battery runs low');
	const kinds = new Set(dayData.treatments.flatMap((t) => t.items.map((i) => i.kind)));
	assert.deepEqual([...kinds].sort(), ['carbs', 'long', 'rapid']);
	// the treatment field is the card's slot: long-acting never, and nothing once the last dose's 4 h are up
	const last = (v: Record<string, unknown>) => (v.last as { items: { k: string }[] }).items;
	assert.ok(!some((v) => last(v).some((i) => i.k === 'long')), 'long-acting never shows');
	assert.ok(some((v) => last(v).length === 0) && some((v) => last(v).length > 0), 'a last treatment, then none');
	// the sensor counts down faster than real life, but never backwards while it runs
	let prev = Infinity;
	for (let t = dayData.sensor.ready; t < dayData.sensor.ready + 1440; t += 5) {
		const s = liveAt(day, t).sensor;
		if (s.state !== 'ok') break;
		assert.ok(s.leftH! <= prev);
		prev = s.leftH!;
	}
});
