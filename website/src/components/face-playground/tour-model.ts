// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * What the face tour shows at a moment, as plain data: the main face's inputs, each look's preview inputs, the clock
 * and the day's chapter. Pure (no DOM, no clock): the page draws its first frame with it at build time, and tour.ts
 * every frame after.
 */
import { liveAt, type Scenario } from './scenarios.ts';
import type { FaceAdapter, Lang, Localized, Values } from './types.ts';

/** Dynamic previews run at 600×: ten minutes a second, a day in 2 min 24 s. Fixed, as the owner chose. */
export const SPEED = 600;

export type TourScenario = Scenario & { chapters: { id: string; at: number; name: Localized }[] };
export type TourState = {
	/** Simulated minutes into the scenario. */
	minutes: number;
	/** The look picked for the main face (Aura: the dial). */
	look: string;
	/** Real seconds, for the part that shows them when someone points at it (Aura: the sweep); null = none. */
	seconds: number | null;
};

const hm = (d: Date) => `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
const withSeconds = (d: Date, s: number) => {
	const x = new Date(d);
	x.setSeconds(s);
	return x;
};

export function frame(face: FaceAdapter, day: TourScenario, s: TourState) {
	// whole minutes: the face shows no seconds while it moves, so nothing redraws in between
	const live = liveAt(day, Math.floor(s.minutes));
	const now: Values = face.fromLive(live);
	const base: Values = { ...face.defaults(), ...face.moving, ...now };
	const main: Values =
		s.seconds === null
			? { ...base, [face.looks.key]: s.look }
			: // what `moving` turns off, back on at real speed: the day's hour and minute, the wall clock's second
				{ ...face.defaults(), ...face.fromLive({ ...live, now: withSeconds(live.now, s.seconds) }), [face.looks.key]: s.look };
	const m = ((Math.floor(s.minutes) % day.minutes) + day.minutes) % day.minutes;
	const chapter = day.chapters.findLast((c) => c.at <= m) ?? day.chapters.at(-1)!;
	return {
		main,
		preview: (look: string): Values => ({ ...base, [face.looks.key]: look }),
		clock: hm(live.now),
		chapter,
	};
}

/** "7:45, Breakfast with insulin" for the page and for screen readers. */
export const caption = (f: ReturnType<typeof frame>, lang: Lang) => `${f.clock} · ${f.chapter.name[lang]}`;
