// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The playground: one face (a FaceAdapter), its presets and inputs, and "Speed up time", which plays a scenario on a
 * ticker (the shared clock) and puts each moment on the face as the tour does (tour-model.ts scene()). It runs only
 * while on screen. The parts stay apart: the face draws (adapter), controls.ts is the form, ticker.ts the time,
 * scenarios.ts the world, face-view.ts the screen; this file only holds the state and passes it between them. The
 * markup it fills is FacePlayground.astro's.
 */
import { SPEEDS } from './clock.ts';
import { controls } from './controls.ts';
import { faceView } from './face-view.ts';
import { DAY } from './day/index.ts';
import { SCENARIOS as OWN, type Scenario } from './scenarios.ts';
import { ticker } from './ticker.ts';
import { chapterAt, hm, scene, START } from './tour-model.ts';
import { words } from './strings.ts';
import type { FaceAdapter, Lang, Values } from './types.ts';

// the short scenarios, then the made-up day the face tour plays (it loops, from 7:30, as there)
const SCENARIOS: Scenario[] = [...OWN, DAY];

const $ = <T extends Element>(root: ParentNode, sel: string) => root.querySelector<T>(sel)!;

export function mountPlayground(root: HTMLElement, face: FaceAdapter, lang: Lang) {
	const w = words(lang);
	const uid = root.dataset.uid!;
	const view = faceView($(root, '[data-face]'));
	const presetSelect = $<HTMLSelectElement>(root, '[data-preset]');
	const scenarioSelect = $<HTMLSelectElement>(root, '[data-scenario]');
	const play = $<HTMLButtonElement>(root, '[data-play]');
	const scrub = $<HTMLInputElement>(root, '[data-scrub]');
	const at = $<HTMLElement>(root, '[data-at]');
	const about = $<HTMLElement>(root, '[data-about]');
	const speeds = $<HTMLElement>(root, '[data-speeds]');

	// the state: the face's inputs (and what a fast clock turns off), the scenario on the clock, whether to play
	let values: Values = face.defaults();
	let still: Values = {};
	let scenario: Scenario = SCENARIOS[0];
	const clock = ticker({ speed: 60, period: scenario.minutes, stop: true });
	let wanted = false; // someone pressed play
	let shown = false; // on screen

	const form = controls($(root, '[data-controls]'), face, lang, (key, v) => {
		values = { ...values, [key]: v };
		presetSelect.value = '';
		draw();
	});

	function draw() {
		const now = { ...values, ...still };
		view.show(face.render(now, lang, { id: uid }));
		form.show(now);
	}

	/** Puts the scenario's moment on the face, locks what it drives while it runs. */
	function tick() {
		const c = clock.clock;
		const s = scene(face, scenario, c.minutes, c.running && c.speed > 1);
		values = { ...values, ...s.values };
		still = s.still;
		form.lock(c.running ? new Set(Object.keys({ ...s.values, ...still })) : new Set());
		scrub.value = String(c.minutes);
		at.textContent = scenario === DAY ? `${hm(s.live.now)} · ${chapterAt(DAY, c.minutes).name[lang]}` : w.at(hm(s.live.now), c.minutes);
		scrub.setAttribute('aria-valuetext', at.textContent);
		play.textContent = wanted ? w.pause : !scenario.loop && c.minutes >= scenario.minutes ? w.again : w.play;
		draw();
	}

	/** Plays while someone asked to and it's on screen. */
	function run() {
		if (wanted && shown) clock.play();
		else clock.pause();
	}

	function stop() {
		wanted = false;
		run();
		form.lock(new Set());
		play.textContent = w.play;
	}

	function cue(s: Scenario) {
		stop();
		scenario = s;
		scrub.max = String(s.minutes);
		about.textContent = s.about[lang];
		presetSelect.value = '';
		clock.set({ period: s.minutes, stop: !s.loop });
		clock.seek(s === DAY ? START : 0);
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
		stop();
		values = { ...p.values };
		draw();
	});
	$(root, '[data-reset]').addEventListener('click', () => {
		cue(scenario);
		values = face.defaults();
		draw();
	});

	// the scenario and the clock
	for (const s of SCENARIOS) scenarioSelect.append(new Option(s.name[lang], s.id));
	scenarioSelect.addEventListener('change', () => cue(SCENARIOS.find((s) => s.id === scenarioSelect.value)!));
	play.addEventListener('click', () => {
		wanted = !wanted;
		if (wanted) {
			if (!scenario.loop && clock.clock.minutes >= scenario.minutes) clock.seek(0);
			presetSelect.value = '';
		}
		run();
	});
	scrub.addEventListener('input', () => clock.seek(Number(scrub.value)));
	for (const x of SPEEDS) {
		const b = Object.assign(document.createElement('button'), { type: 'button', textContent: `${x}×` });
		b.setAttribute('aria-label', w.speedName(x));
		b.setAttribute('aria-pressed', String(x === clock.clock.speed));
		b.addEventListener('click', () => {
			clock.set({ speed: x });
			for (const o of speeds.querySelectorAll('button')) o.setAttribute('aria-pressed', String(o === b));
		});
		speeds.append(b);
	}

	// every tick puts the moment on the face; a scenario that stops at its end is no longer playing
	clock.listen((c) => {
		if (wanted && shown && !c.running) wanted = false;
		tick();
	});
	// the face's own inputs until someone plays
	values = face.defaults();
	scrub.max = String(scenario.minutes);
	about.textContent = scenario.about[lang];
	draw();

	// run only while on screen
	new IntersectionObserver(([e]) => {
		shown = e.isIntersecting;
		run();
	}).observe(root);
	// the face's own type; the browser redraws its text when it arrives
	void face.ready();
}
