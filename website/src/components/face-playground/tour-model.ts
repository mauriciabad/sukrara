// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * What a moving face shows at a moment, as plain data. scene(): what a scenario sets on any face while its clock moves
 * (the playground too); frame(): the face tour's main face, each look's preview, the clock and the day's chapter. Pure
 * (no DOM, no clock): the pages draw their first frame with it at build time, and tour.ts / playground.ts every frame
 * after.
 */
import { liveAt, type Scenario } from './scenarios.ts';
import type { FaceAdapter, Lang, Localized, Values } from './types.ts';

/** Dynamic previews run at 600×: ten minutes a second, a day in 2 min 24 s. Fixed, as the owner chose. */
export const SPEED = 600;
/** Where the made-up day starts on a page: 7:30, just before breakfast. */
export const START = 7 * 60 + 30;

export type TourScenario = Scenario & { chapters: { id: string; at: number; name: Localized }[] };
export type TourState = {
	/** Simulated minutes into the scenario. */
	minutes: number;
	/** The look picked for the main face (Aura: the dial). */
	look: string;
	/** Real seconds, for the part that shows them when someone points at it (Aura: the sweep); null = none. */
	seconds: number | null;
};

export const hm = (d: Date) => `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
const withSeconds = (d: Date, s: number) => {
	const x = new Date(d);
	x.setSeconds(s);
	return x;
};

/**
 * What scenario `s` sets on the face at `minutes`. While the clock moves faster than real time (`fast`): whole minutes,
 * so nothing redraws in between, and `still`, the inputs every moving face takes (Aura: no seconds sweep).
 */
export function scene(face: FaceAdapter, s: Scenario, minutes: number, fast: boolean) {
	const live = liveAt(s, fast ? Math.floor(minutes) : minutes);
	return { live, values: face.fromLive(live), still: fast ? face.moving : {} };
}

/** The made-up day's chapter at a minute. */
export const chapterAt = (day: TourScenario, minutes: number) => {
	const m = ((Math.floor(minutes) % day.minutes) + day.minutes) % day.minutes;
	return day.chapters.findLast((c) => c.at <= m) ?? day.chapters.at(-1)!;
};

export function frame(face: FaceAdapter, day: TourScenario, s: TourState) {
	const { live, values: now, still } = scene(face, day, s.minutes, true);
	const base: Values = { ...face.defaults(), ...still, ...now };
	const main: Values =
		s.seconds === null
			? { ...base, [face.looks.key]: s.look }
			: // what `moving` turns off, back on at real speed: the day's hour and minute, the wall clock's second
				{ ...face.defaults(), ...face.fromLive({ ...live, now: withSeconds(live.now, s.seconds) }), [face.looks.key]: s.look };
	const chapter = chapterAt(day, s.minutes);
	return {
		main,
		preview: (look: string): Values => ({ ...base, [face.looks.key]: look }),
		clock: hm(live.now),
		chapter,
	};
}

/** "7:45, Breakfast with insulin" for the page and for screen readers. */
export const caption = (f: ReturnType<typeof frame>, lang: Lang) => `${f.clock} · ${f.chapter.name[lang]}`;
