// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The face tour on the page: one ticker (the shared clock) plays the made-up day; every tick, tour-model.ts says what
 * the face shows, the settings a person picked go on top, face-view.ts puts it on screen, and the leader lines are
 * drawn from each name to where its part is now (lines.ts; the face says where: FaceAdapter.places). Pointing at a name
 * or a part, focusing it, or tapping it shows that part alone. The markup it fills is FaceTour.astro's; it talks to the
 * face only through its FaceAdapter.
 */
import { cards, group, list, switchCard, type Choice } from './choices.ts';
import { faceView } from './face-view.ts';
import { edge, elbow, ringPlaces, target, toPx, type Pt } from './lines.ts';
import { caption, frame, SPEED, type TourScenario } from './tour-model.ts';
import { ticker } from './ticker.ts';
import { words } from './strings.ts';
import type { FaceAdapter, Lang, Place, Values } from './types.ts';

const $ = <T extends Element>(root: ParentNode, sel: string) => root.querySelector<T>(sel)!;
const $$ = <T extends Element>(root: ParentNode, sel: string) => [...root.querySelectorAll<T>(sel)];

export function mountTour(root: HTMLElement, face: FaceAdapter, day: TourScenario, lang: Lang) {
	const w = words(lang);
	const uid = root.dataset.uid!;
	const map = $<HTMLElement>(root, '[data-map]');
	const host = $<HTMLElement>(root, '[data-face]');
	const screen = host.parentElement!;
	const main = faceView(host);
	const svg = $<SVGSVGElement>(root, '[data-lines]');
	const buttons = $$<HTMLButtonElement>(root, '[data-part-button]');
	const play = $<HTMLButtonElement>(root, '[data-play]');
	const scrub = $<HTMLInputElement>(root, '[data-scrub]');
	const now = $<HTMLElement>(root, '[data-now]');
	const lookButtons = $$<HTMLButtonElement>(root, '[data-look]');
	const previews = lookButtons.map((b) => ({ look: b.dataset.look!, view: faceView($(b, '[data-preview]')) }));

	const clock = ticker({ speed: SPEED, period: day.minutes, minutes: Number(root.dataset.start) });
	const set: Values = { ...face.settings.defaults };
	let look = root.dataset.lookStart!;
	let focus: string | null = null; // a part's id
	let at: Record<string, Place> = {};
	let shown = false; // on screen
	let previewsShown = false;
	let lastPreview = '';
	let userPaused = matchMedia('(prefers-reduced-motion: reduce)').matches;

	/** Real seconds while the sweep is on or pointed at; the day's whole minutes otherwise. */
	const realSeconds = () => focus === 'sweep' || set.seconds === true;

	// ——— the lines, measured from the page at every draw, so they follow the layout at every width ———
	function lines() {
		const box = map.getBoundingClientRect();
		const fr = host.getBoundingClientRect();
		const sr = screen.getBoundingClientRect();
		if (!box.width) return;
		const faceRect = { left: fr.left - box.left, top: fr.top - box.top, width: fr.width };
		const c = { x: sr.left - box.left + sr.width / 2, y: sr.top - box.top + sr.height / 2 };
		svg.setAttribute('viewBox', `0 0 ${box.width} ${box.height}`);
		for (const el of $$<SVGCircleElement>(svg, '[data-clip]')) {
			el.setAttribute('cx', String(c.x));
			el.setAttribute('cy', String(c.y));
			el.setAttribute('r', String(sr.width / 2));
		}
		const numbered = getComputedStyle(map).getPropertyValue('--ft-mode').trim() === 'numbered';
		const px = (id: string) => at[id].boxes.map((b) => toPx(faceRect, face.radius, b));
		// narrow: each number on the ring round the screen, at its part's angle, kept apart from the others
		const ids = buttons.map((b) => b.dataset.partButton!).filter((id) => at[id]);
		const badges = numbered
			? ringPlaces(c, sr.width / 2 - (sr.width - fr.width) / 4, ids.map((id) => edge(px(id)[0], sideOf(id) === 'left')), 24)
			: [];
		ids.forEach((id, i) => {
			const p = at[id];
			const boxes = px(id);
			let d: string;
			let ends: string;
			if (numbered) {
				const m = badges[i];
				const t = edge(boxes[0], sideOf(id) === 'left');
				const n = face.parts.findIndex((q) => q.id === id) + 1;
				d = `M${m.x} ${m.y}L${t.x} ${t.y}`;
				ends = `<circle class="ft-dot" cx="${t.x}" cy="${t.y}" r="2.5"/><circle class="ft-badge" cx="${m.x}" cy="${m.y}" r="9.5"/><text class="ft-badge-n" x="${m.x}" y="${m.y}" dy="0.36em">${n}</text>`;
			} else {
				const left = sideOf(id) === 'left';
				const ic = $(buttons.find((b) => b.dataset.partButton === id)!, '.ft-part__icon').getBoundingClientRect();
				const from: Pt = { x: (left ? ic.right + 4 : ic.left - 4) - box.left, y: ic.top + ic.height / 2 - box.top };
				const rim = left ? sr.left - box.left - 10 : sr.right - box.left + 10;
				const to = boxes.map((b) => target(b, from));
				d = to.map((t) => elbow(from, t, rim)).join('');
				ends = to.map((t) => `<circle class="ft-dot" cx="${t.x}" cy="${t.y}" r="3.25"/>`).join('');
			}
			for (const l of $$<SVGPathElement>(svg, `[data-line='${id}']`)) {
				l.setAttribute('d', d);
				l.classList.toggle('is-off', !p.shown);
			}
			const end = $<SVGGElement>(svg, `[data-end='${id}']`);
			end.classList.toggle('is-off', !p.shown);
			end.innerHTML = ends;
			// a target to point at, a little bigger than the part: small ones are hard to hit
			const hit = svg.querySelector(`[data-hit='${id}']`);
			if (hit)
				hit.innerHTML = p.shown
					? boxes
							.map((b) => b.hit ?? b)
							.map((b) => (b.w || b.h ? `<rect x="${b.x - 6}" y="${b.y - 6}" width="${b.w + 12}" height="${b.h + 12}" rx="8"/>` : `<circle cx="${b.x}" cy="${b.y}" r="16"/>`))
							.join('')
					: '';
		});
	}
	const sideOf = (id: string) => face.parts.find((p) => p.id === id)!.side;

	// ——— the face, the previews and the names, at the clock's moment ———
	function draw() {
		const f = frame(face, day, { minutes: clock.clock.minutes, look, seconds: realSeconds() ? new Date().getSeconds() : null });
		const values: Values = { ...f.main, ...face.settings.apply(set) };
		if (!realSeconds()) Object.assign(values, face.moving);
		main.show(face.render(values, lang, { id: `${uid}-m` }));
		if (previewsShown) {
			const p = face.looks.preview(values);
			const key = JSON.stringify(p, (k, v) => (k === 'time' || k === 'history' || k === 'spark' || k === face.looks.key ? undefined : v));
			if (key !== lastPreview) {
				lastPreview = key;
				for (const x of previews) x.view.show(face.render({ ...p, [face.looks.key]: x.look }, lang, { id: `${uid}-${x.look}` }));
			}
		}
		const text = caption(f, lang);
		if (now.textContent !== text) now.textContent = text;
		scrub.value = String(Math.floor(clock.clock.minutes));
		scrub.setAttribute('aria-valuetext', text);
		at = face.places(values);
		for (const b of buttons) {
			const s = String(at[b.dataset.partButton!]?.shown ?? false);
			if (b.dataset.shown !== s) b.dataset.shown = s;
		}
		paintFocus();
		lines();
	}

	// pointing at a part: it, its name and its line stay; the rest fades
	function paintFocus() {
		const part = focus ? (at[focus]?.part ?? focus) : null;
		if (part) root.dataset.focus = part;
		else delete root.dataset.focus;
		root.toggleAttribute('data-focusing', !!focus);
		for (const el of $$<Element>(root, '[data-part-button],[data-line],[data-end]')) {
			const id = el.getAttribute('data-part-button') ?? el.getAttribute('data-line') ?? el.getAttribute('data-end');
			el.classList.toggle('is-on', id === focus);
		}
		for (const el of $$<Element>(host, '[data-part]')) el.classList.toggle('is-on', !!part && el.getAttribute('data-part') === part);
	}

	function setFocus(id: string | null) {
		if (focus === id) return;
		focus = id;
		draw();
	}

	function run() {
		if (shown && !userPaused) clock.play();
		else clock.pause();
		play.setAttribute('aria-pressed', String(!userPaused));
		play.setAttribute('aria-label', userPaused ? w.playDay : w.pauseDay);
	}

	clock.listen(draw);
	// the sweep's real seconds
	window.setInterval(() => shown && realSeconds() && draw(), 1000);

	// ——— pointing: a name, a part's target over the face, or the face's own drawing ———
	const partAt = (t: EventTarget | null) => {
		const el = t instanceof Element ? t : null;
		const b = el?.closest<HTMLElement>('[data-part-button]');
		if (b) return b.dataset.partButton!;
		const hit = el?.closest('[data-hit]');
		if (hit) return hit.getAttribute('data-hit');
		const part = el && host.contains(el) ? el.closest('[data-part]')?.getAttribute('data-part') : null;
		if (!part) return null;
		return Object.entries(at).find(([, p]) => p.part === part && p.shown)?.[0] ?? null;
	};
	let keyboard: string | null = null;
	let touch: string | null = null;
	map.addEventListener('pointerover', (e) => {
		if (e.pointerType !== 'touch') setFocus(partAt(e.target) ?? keyboard ?? touch);
	});
	map.addEventListener('pointerleave', (e) => {
		if (e.pointerType !== 'touch') setFocus(keyboard ?? touch);
	});
	// a tap picks a part and keeps it; the same tap again, or a tap elsewhere on the face, lets go
	map.addEventListener('click', (e) => {
		if ((e as PointerEvent).pointerType === 'mouse' || (e.target as Element).closest('.fu-play-bar')) return;
		const id = partAt(e.target);
		touch = id && id !== touch ? id : null;
		setFocus(touch);
	});
	map.addEventListener('focusin', (e) => {
		keyboard = partAt(e.target);
		if (keyboard) setFocus(keyboard);
	});
	map.addEventListener('focusout', () => {
		keyboard = null;
		setFocus(touch);
	});
	root.addEventListener('keydown', (e) => {
		if (e.key === 'Escape') {
			touch = null;
			setFocus(null);
		}
	});

	// ——— the settings to try ———
	const changed = (key: string, v: unknown) => {
		set[key] = v;
		draw();
		requestAnimationFrame(lines);
	};
	const made = new Map<string, Choice>();
	for (const g of face.settings.groups) {
		const items = g.keys.map((k) => face.settings.list.find((s) => s.key === k)!);
		const kids = items.map((s) => {
			const c =
				s.kind === 'cards'
					? cards(s.label, s.options, lang, (v) => changed(s.key, v))
					: s.kind === 'switch'
						? switchCard(s.label, s.picture, lang, (on) => changed(s.key, on ? s.on : s.off))
						: list(s.label, s.options, lang, (v) => changed(s.key, v));
			made.set(s.key, c);
			c.show(s.kind === 'switch' ? set[s.key] === s.on : set[s.key]);
			return c.root;
		});
		// a row of cards stands alone, under its own name; switches and lists gather under the group's
		const one = items.length === 1 && items[0].kind === 'cards';
		$(root, '[data-sets]').append(one ? kids[0] : group(g.label, lang, kids, `ft-${g.id}`));
	}
	for (const b of lookButtons)
		b.addEventListener('click', () => {
			look = b.dataset.look!;
			for (const o of lookButtons) o.setAttribute('aria-pressed', String(o === b));
			draw();
		});

	// ——— time ———
	play.addEventListener('click', () => {
		userPaused = !userPaused;
		run();
	});
	scrub.addEventListener('input', () => clock.seek(Number(scrub.value)));

	// run only while on screen; previews draw only while theirs are
	new IntersectionObserver(([e]) => {
		shown = e.isIntersecting;
		run();
	}).observe(root);
	new IntersectionObserver(([e]) => {
		previewsShown = e.isIntersecting;
		if (previewsShown) draw();
	}).observe($(root, '[data-looks]'));
	new ResizeObserver(() => lines()).observe(map);

	run();
	void face.ready().then(() => {
		draw();
		root.classList.add('is-ready');
	});
}
