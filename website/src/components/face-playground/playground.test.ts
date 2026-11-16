// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The playground's pure parts: the clock, the scenarios, and Aura's adapter over the real aura.js and cases.json.
// npm test (node --test, Node's own TypeScript).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { test } from 'node:test';
import { advance, clampTo, start } from './clock.ts';
import { liveAt, SCENARIOS } from './scenarios.ts';
import { auraFromLive, createAura, type AuraCase } from './aura/adapter.ts';
import { evaluateAura } from './aura/evaluate.ts';
import { PRESETS_ES } from './aura/presets.ts';

const repo = new URL('../../../../', import.meta.url);
const aura = evaluateAura(fs.readFileSync(new URL('tools/watchface/aura.js', repo), 'utf8'));
const { cases } = JSON.parse(fs.readFileSync(new URL('contracts/watchface/aura/cases.json', repo), 'utf8')) as { cases: AuraCase[] };
const face = createAura(aura, cases);
const scenario = (id: string) => SCENARIOS.find((s) => s.id === id)!;

test('the clock runs at its speed, stands still when paused, stops at the end', () => {
	const c = advance(start(60), 2000);
	assert.equal(c.minutes, 2);
	assert.equal(advance({ ...c, running: false }, 5000).minutes, 2);
	assert.deepEqual(clampTo(advance(c, 60000), 30), { minutes: 30, speed: 60, running: false });
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
	assert.deepEqual(liveAt(s, 20).quiet, { leftMin: 55 });
	assert.equal(liveAt(s, 74.5).quiet!.leftMin, 1);
	assert.equal(liveAt(s, 80).quiet, null);
	assert.equal(liveAt(s, 100).treatment!.agoMin, 85);
	assert.equal(liveAt(s, 10).treatment, null);
});

test('the clock and the history follow the scenario', () => {
	const l = liveAt(scenario('night-low'), 61.5);
	assert.equal(`${l.now.getHours()}:${l.now.getMinutes()}:${l.now.getSeconds()}`, '2:31:30');
	assert.equal(l.history.length, 12);
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
		sensor: { state: 'ok' },
		quiet: { leftMin: 12 },
		treatment: { items: [{ kind: 'carbs', amount: 15 }], agoMin: 4 },
		dnd: false,
	});
	assert.deepEqual(v, {
		time: '9:05:07', phone: true, sensor: 'ok', glucose: 150, rate: 0, trend: false, age: 2,
		history: [140, null, 145], spark: [140, null, 145, 150], status: 'quiet', left: 12,
		last: { items: [{ k: 'carbs', a: '15' }], ago: 4 },
	});
});

test('defaults are aura.js own; every preset is a case of cases.json, drawn as aura.js draws it', () => {
	assert.equal(face.defaults().glucose, new aura.AuraFace({}).opt().glucose);
	assert.equal(face.presets.length, Object.keys(PRESETS_ES).length, 'a preset names a case that no longer exists');
	for (const p of face.presets) {
		const c = cases.find((x) => x.id === p.id)!;
		assert.equal(face.render(p.values, 'en'), aura.svg({ ...face.defaults(), ...c.cfg, lang: 'en', size: 390, bezel: false }));
	}
	// the screen alone: no bezel (it carries a watchmaker's print)
	assert.match(face.render(face.defaults(), 'en'), /viewBox="-195 -195 390 390"/);
});
