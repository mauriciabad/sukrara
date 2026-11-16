// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The scenarios "Speed up time" plays: a sensor's glucose over time, which readings reach the watch, treatments and
 * alerts held after them. liveAt() turns a scenario and a simulated minute into a Live moment (types.ts), in no face's
 * terms. Synthetic data only. Pure: no clock, no DOM.
 */
import type { Live, Localized } from './types.ts';

type Treatment = Exclude<Live['treatment'], null>['items'];

export type Scenario = {
	id: string;
	name: Localized;
	/** One line on what to watch for. */
	about: Localized;
	/** The watch's clock at minute 0 (hours, minutes; Monday 16 November 2026). */
	start: [number, number];
	/** How long it runs, in minutes. */
	minutes: number;
	/** The sensor's glucose at minute t (mg/dL), also before minute 0 (the hour of history). */
	glucose: (t: number) => number;
	/** Whether the reading taken at minute t reaches the watch (the phone in reach). Default: always. */
	reaches?: (t: number) => boolean;
	/** The minute the first reading is taken. Default: an hour before the start. */
	firstReading?: number;
	sensor?: (t: number) => Live['sensor'];
	treatments?: { at: number; items: Treatment }[];
	/** Alerts held after a treatment: from, to (minutes). */
	quiet?: [number, number][];
	dnd?: boolean;
};

// A small, steady wobble so a flat line still looks like a sensor's.
const wobble = (t: number) => 2 * Math.sin(t / 7) + 1.2 * Math.sin(t / 3.1);
const smooth = (x: number) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));

export const SCENARIOS: Scenario[] = [
	{
		id: 'meal',
		name: { en: 'Lunch', es: 'Comida' },
		about: {
			en: 'Insulin and carbs at 12:45; the high waits an hour after insulin, then asks for more.',
			es: 'Insulina e hidratos a las 12:45; la alta espera una hora tras la insulina y luego pide más.',
		},
		start: [12, 30],
		minutes: 210,
		glucose: (t) => {
			const x = Math.max(0, (t - 20) / 60);
			return 104 + 102 * x * Math.exp(1 - x) + wobble(t);
		},
		treatments: [{ at: 15, items: [{ kind: 'rapid', amount: 4.5 }, { kind: 'carbs', amount: 45 }] }],
		quiet: [[15, 75]],
	},
	{
		id: 'night-low',
		name: { en: 'Night low', es: 'Bajada de noche' },
		about: {
			en: 'Asleep, a slow fall to a low at 3:00; 15 g of carbs, alerts wait 15 min, back in range.',
			es: 'Dormido, una bajada lenta hasta una baja a las 3:00; 15 g de hidratos, las alertas esperan 15 min, de vuelta en rango.',
		},
		start: [1, 30],
		minutes: 180,
		glucose: (t) => {
			if (t <= 90) return 126 - 68 * Math.sin((Math.PI * Math.max(0, t)) / 180) ** 2 + wobble(t) / 2;
			if (t <= 100) return 58 + wobble(t) / 4;
			return 58 + 52 * (1 - Math.exp(-(t - 100) / 22)) + wobble(t) / 2;
		},
		treatments: [{ at: 92, items: [{ kind: 'carbs', amount: 15 }] }],
		quiet: [[92, 107]],
		dnd: true,
	},
	{
		id: 'phone-away',
		name: { en: 'Phone out of reach', es: 'Móvil lejos' },
		about: {
			en: 'The phone stays behind at 14:05: the reading gets late, then old, then “No reading since”; back at 17:45.',
			es: 'El móvil se queda atrás a las 14:05: la lectura se retrasa, luego es antigua y luego «Sin lecturas desde»; vuelve a las 17:45.',
		},
		start: [14, 0],
		minutes: 240,
		glucose: (t) => 112 + 10 * Math.sin(t / 40) + wobble(t),
		reaches: (t) => t < 5 || t >= 225,
	},
	{
		id: 'new-sensor',
		name: { en: 'New sensor', es: 'Sensor nuevo' },
		about: {
			en: 'An hour of warm-up with its countdown, then the first readings.',
			es: 'Una hora de calentamiento con su cuenta atrás y luego las primeras lecturas.',
		},
		start: [8, 0],
		minutes: 80,
		firstReading: 60,
		glucose: (t) => 128 - 0.3 * Math.max(0, t - 60) + wobble(t),
		sensor: (t) => (t >= 60 ? { state: 'ok' } : t >= 55 ? { state: 'almost' } : { state: 'warmup', done: t / 60 }),
	},
];

const DAY = [2026, 10, 16] as const;
const round1 = (x: number) => Math.round(x * 10) / 10;

/** The world at minute t of a scenario. */
export function liveAt(s: Scenario, t: number): Live {
	const now = new Date(DAY[0], DAY[1], DAY[2], s.start[0], s.start[1], 0, 0);
	now.setTime(now.getTime() + Math.round(t * 60000));
	const reaches = s.reaches ?? (() => true);
	const first = s.firstReading ?? -60;
	const g = (m: number) => Math.round(s.glucose(m));
	const got = (m: number) => m >= first && reaches(m);

	// the newest reading taken (one a minute) that reached the watch
	let at = Math.floor(t);
	while (at >= first && !reaches(at)) at--;
	const reading =
		at >= first
			? {
					glucose: g(at),
					rate: at - 5 >= first ? round1((s.glucose(at) - s.glucose(at - 5)) / 5) : null,
					ageMin: t - at,
				}
			: null;
	const history = reading ? Array.from({ length: 12 }, (_, i) => at - 5 * (12 - i)).map((m) => (got(m) ? g(m) : null)) : [];

	const q = (s.quiet ?? []).find(([a, b]) => t >= a && t < b);
	const done = (s.treatments ?? []).filter((x) => x.at <= t).at(-1);
	return {
		now,
		reading,
		history,
		phone: reaches(Math.floor(t)),
		sensor: s.sensor?.(t) ?? { state: 'ok' },
		quiet: q ? { leftMin: Math.ceil(q[1] - t) } : null,
		treatment: done ? { items: done.items, agoMin: Math.floor(t - done.at) } : null,
		dnd: !!s.dnd,
	};
}
