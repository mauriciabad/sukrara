// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The example galleries on a page: each example drawn when it first comes near the screen (a page of fifty faces costs
 * only what is looked at); the moving ones play on one clock for the whole page, at 600×, while they're on screen.
 * The markup is FaceGallery.astro's; the faces come from the FaceAdapter's examples.
 */
import { faceView } from './face-view.ts';
import { SPEED } from './tour-model.ts';
import { ticker } from './ticker.ts';
import type { Example, FaceAdapter, Lang } from './types.ts';

type Tile = { e: Example; view: ReturnType<typeof faceView> };

export function mountGalleries(face: FaceAdapter, lang: Lang) {
	const byId = new Map(face.examples.flatMap((g) => g.examples).map((e) => [e.id, e] as const));
	const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
	const clock = ticker({ speed: SPEED, period: 7 * 1440 });
	const tiles = new WeakMap<Element, Tile>();
	const playing = new Set<Tile>();

	const draw = ({ e, view }: Tile) => {
		const values = e.live && !still ? { ...e.values, ...e.live.at(clock.clock.minutes % e.live.loop) } : e.values;
		view.show(face.render(values, lang, { id: `ex-${e.id}` }));
	};
	clock.listen(() => playing.forEach(draw));

	const near = new IntersectionObserver(
		(entries) => {
			for (const { target, isIntersecting } of entries) {
				let tile = tiles.get(target);
				if (!tile) {
					const e = byId.get((target as HTMLElement).dataset.example!);
					if (!e) continue;
					tile = { e, view: faceView(target as HTMLElement) };
					tiles.set(target, tile);
				}
				if (isIntersecting) draw(tile);
				if (tile.e.live && !still) {
					if (isIntersecting) playing.add(tile);
					else playing.delete(tile);
				}
			}
			if (playing.size) clock.play();
			else clock.pause();
		},
		{ rootMargin: '200px 0px' },
	);
	// every example on the page, once (FaceGallery and FaceExample both ask)
	for (const host of document.querySelectorAll<HTMLElement>('[data-example]:not([data-mounted])')) {
		host.dataset.mounted = '';
		near.observe(host);
	}
	void face.ready();
}
