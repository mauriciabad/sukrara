// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The playground: one face (a FaceAdapter), its presets and inputs, and "Speed up time", which plays a scenario on the
 * simulation clock and feeds each moment to the face. The parts stay apart: the face draws (adapter), controls.ts is
 * the form, clock.ts the time, scenarios.ts the world, face-view.ts the screen; this file only holds the state and
 * passes it between them. The markup it fills is FacePlayground.astro's.
 */
import { advance, clampTo, SPEEDS, type Clock } from './clock.ts';
import { controls } from './controls.ts';
import { faceView } from './face-view.ts';
import { DAY } from './day/index.ts';
import { liveAt, SCENARIOS as OWN, type Scenario } from './scenarios.ts';
import { words } from './strings.ts';
import type { FaceAdapter, Lang, Values } from './types.ts';

// the short scenarios, then the made-up day the face tour plays
const SCENARIOS: Scenario[] = [...OWN, DAY];

const $ = <T extends Element>(root: ParentNode, sel: string) => root.querySelector<T>(sel)!;

export function mountPlayground(root: HTMLElement, face: FaceAdapter, lang: Lang) {
	const w = words(lang);
	const view = faceView($(root, '[data-face]'));
	const presetSelect = $<HTMLSelectElement>(root, '[data-preset]');
	const scenarioSelect = $<HTMLSelectElement>(root, '[data-scenario]');
	const play = $<HTMLButtonElement>(root, '[data-play]');
	const scrub = $<HTMLInputElement>(root, '[data-scrub]');
	const at = $<HTMLElement>(root, '[data-at]');
	const about = $<HTMLElement>(root, '[data-about]');
	const speeds = $<HTMLElement>(root, '[data-speeds]');

	// the state: the face's inputs, and the scenario on the clock
	let values: Values = face.defaults();
	let scenario: Scenario = SCENARIOS[0];
	let clock: Clock = { minutes: 0, speed: 60, running: false };
	let frame = 0;

	const form = controls($(root, '[data-controls]'), face, lang, (key, v) => {
		values = { ...values, [key]: v };
		presetSelect.value = '';
		draw();
	});

	function draw() {
		view.show(face.render(values, lang));
		form.show(values);
	}

	/** Puts the scenario's moment on the face, locks what it drives while it runs. */
	function tick() {
		const live = liveAt(scenario, clock.minutes);
		const driven = face.fromLive(live);
		values = { ...values, ...driven };
		form.lock(clock.running ? new Set(Object.keys(driven)) : new Set());
		showTime(live.now);
		play.textContent = clock.running ? w.pause : clock.minutes >= scenario.minutes ? w.again : w.play;
		draw();
	}

	function showTime(now: Date) {
		scrub.value = String(clock.minutes);
		at.textContent = w.at(`${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`, clock.minutes);
		scrub.setAttribute('aria-valuetext', at.textContent);
	}

	function loop(prev: number) {
		frame = requestAnimationFrame((now) => {
			clock = clampTo(advance(clock, now - prev), scenario.minutes);
			tick();
			if (clock.running) loop(now);
		});
	}

	function cue(s: Scenario) {
		cancelAnimationFrame(frame);
		scenario = s;
		clock = { ...clock, minutes: 0, running: false };
		scrub.max = String(s.minutes);
		about.textContent = s.about[lang];
		presetSelect.value = '';
		tick();
	}

	// presets, grouped
	presetSelect.append(new Option(w.custom, ''));
	const groups = new Map<string, HTMLOptGroupElement>();
	for (const p of face.presets) {
		let g = groups.get(p.group);
		if (!g) {
			g = Object.assign(document.createElement('optgroup'), { label: face.presetGroups[p.group]?.[lang] ?? p.group });
			groups.set(p.group, g);
			presetSelect.append(g);
		}
		g.append(new Option(p.name[lang], p.id));
	}
	presetSelect.addEventListener('change', () => {
		const p = face.presets.find((x) => x.id === presetSelect.value);
		if (!p) return;
		cancelAnimationFrame(frame);
		clock = { ...clock, running: false };
		form.lock(new Set());
		play.textContent = w.play;
		values = { ...p.values };
		draw();
	});
	$(root, '[data-reset]').addEventListener('click', () => {
		cue(scenario);
		values = face.defaults();
		form.lock(new Set());
		draw();
	});

	// the scenario and the clock
	for (const s of SCENARIOS) scenarioSelect.append(new Option(s.name[lang], s.id));
	scenarioSelect.addEventListener('change', () => cue(SCENARIOS.find((s) => s.id === scenarioSelect.value)!));
	play.addEventListener('click', () => {
		if (clock.running) {
			cancelAnimationFrame(frame);
			clock = { ...clock, running: false };
			tick();
			return;
		}
		if (clock.minutes >= scenario.minutes) clock = { ...clock, minutes: 0 };
		presetSelect.value = '';
		clock = { ...clock, running: true };
		tick();
		loop(performance.now());
	});
	scrub.addEventListener('input', () => {
		clock = { ...clock, minutes: Number(scrub.value) };
		tick();
	});
	for (const x of SPEEDS) {
		const b = Object.assign(document.createElement('button'), { type: 'button', textContent: `${x}×` });
		b.setAttribute('aria-label', w.speedName(x));
		b.setAttribute('aria-pressed', String(x === clock.speed));
		b.addEventListener('click', () => {
			clock = { ...clock, speed: x };
			for (const o of speeds.querySelectorAll('button')) o.setAttribute('aria-pressed', String(o === b));
		});
		speeds.append(b);
	}

	scrub.max = String(scenario.minutes);
	about.textContent = scenario.about[lang];
	showTime(liveAt(scenario, 0).now);
	play.textContent = w.play;
	draw();
	// the face's own type; the browser redraws its text when it arrives
	void face.ready();
}
