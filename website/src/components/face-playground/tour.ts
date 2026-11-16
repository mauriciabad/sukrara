// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The face tour on the page: one ticker (the shared clock) plays the made-up day; every tick, tour-model.ts says what
 * each face shows and face-view.ts puts it on screen, the main face and every look's preview at the same moment.
 * Pointing at a part (or focusing its button) fades the rest. The markup it fills is FaceTour.astro's; it talks to the
 * face only through its FaceAdapter.
 */
import { faceView } from './face-view.ts';
import { caption, frame, SPEED, type TourScenario, type TourState } from './tour-model.ts';
import { ticker } from './ticker.ts';
import { words } from './strings.ts';
import type { FaceAdapter, Lang } from './types.ts';

const $ = <T extends Element>(root: ParentNode, sel: string) => root.querySelector<T>(sel)!;
const $$ = <T extends Element>(root: ParentNode, sel: string) => [...root.querySelectorAll<T>(sel)];

export function mountTour(root: HTMLElement, face: FaceAdapter, day: TourScenario, lang: Lang) {
	const w = words(lang);
	const uid = root.dataset.uid!;
	const main = faceView($(root, '[data-face]'));
	const mainHost = $<HTMLElement>(root, '[data-face]');
	const play = $<HTMLButtonElement>(root, '[data-play]');
	const scrub = $<HTMLInputElement>(root, '[data-scrub]');
	const now = $<HTMLElement>(root, '[data-now]');
	const partButtons = $$<HTMLButtonElement>(root, '[data-part-button]');
	const lookButtons = $$<HTMLButtonElement>(root, '[data-look]');
	const previews = lookButtons.map((b) => ({ look: b.dataset.look!, view: faceView($(b, '[data-preview]')) }));

	const clock = ticker({ speed: SPEED, period: day.minutes, minutes: Number(root.dataset.start) });
	let look = root.dataset.lookStart!;
	let focus: string | null = null;
	let shown = false; // on screen
	let previewsShown = false;
	let userPaused = matchMedia('(prefers-reduced-motion: reduce)').matches;
	let sweep = 0;

	function draw() {
		const f = frame(face, day, { minutes: clock.clock.minutes, look, seconds: focus === 'sweep' ? new Date().getSeconds() : null } satisfies TourState);
		main.show(face.render(f.main, lang, { id: `${uid}-m` }));
		if (previewsShown) for (const p of previews) p.view.show(face.render(f.preview(p.look), lang, { id: `${uid}-${p.look}` }));
		const text = caption(f, lang);
		if (now.textContent !== text) now.textContent = text;
		scrub.value = String(Math.floor(clock.clock.minutes));
		scrub.setAttribute('aria-valuetext', text);
		// which parts are drawn now
		for (const b of partButtons) {
			const on = $$<Element>(mainHost, `[data-part="${b.dataset.partButton}"]`).some((e) => e.getAttribute('display') !== 'none');
			if (b.dataset.shown !== String(on)) b.dataset.shown = String(on);
		}
	}

	function setFocus(id: string | null) {
		if (focus === id) return;
		focus = id;
		if (id) root.dataset.focus = id;
		else delete root.dataset.focus;
		clearInterval(sweep);
		if (id === 'sweep') sweep = window.setInterval(draw, 1000);
		draw();
	}

	function run() {
		const go = shown && !userPaused;
		if (go) clock.play();
		else clock.pause();
		play.setAttribute('aria-pressed', String(!userPaused));
		play.textContent = userPaused ? w.playDay : w.pauseDay;
	}

	clock.listen(draw);

	// parts: buttons, and the face itself under a pointer
	// whatever is under the pointer decides; with no pointer over the tour, the focused part's button
	const partAt = (t: EventTarget | null) => {
		const el = t instanceof Element ? t : null;
		const button = el?.closest<HTMLElement>('[data-part-button]');
		if (button) return button.dataset.partButton!;
		return el && mainHost.contains(el) ? (el.closest('[data-part]')?.getAttribute('data-part') ?? null) : null;
	};
	let keyboard: string | null = null;
	root.addEventListener('pointerover', (e) => setFocus(partAt(e.target) ?? keyboard));
	root.addEventListener('pointerleave', () => setFocus(keyboard));
	root.addEventListener('focusin', (e) => {
		keyboard = partAt(e.target);
		setFocus(keyboard);
	});
	root.addEventListener('focusout', () => {
		keyboard = null;
		setFocus(null);
	});
	root.addEventListener('keydown', (e) => {
		if (e.key === 'Escape') setFocus(null);
	});

	// looks: a click puts it on the main face
	for (const b of lookButtons) {
		b.addEventListener('click', () => {
			look = b.dataset.look!;
			for (const o of lookButtons) o.setAttribute('aria-pressed', String(o === b));
			draw();
		});
	}

	// time
	play.addEventListener('click', () => {
		userPaused = !userPaused;
		run();
	});
	scrub.addEventListener('input', () => clock.seek(Number(scrub.value)));

	// run only while on screen; previews draw only while theirs is
	new IntersectionObserver(([e]) => {
		shown = e.isIntersecting;
		run();
	}).observe(root);
	new IntersectionObserver(([e]) => {
		previewsShown = e.isIntersecting;
		if (previewsShown) draw();
	}).observe($(root, '[data-looks]'));

	run();
	void face.ready().then(draw);
}
