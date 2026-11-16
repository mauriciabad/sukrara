// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The showcase's made-up day as a Scenario: 24 hours that loop, from day.json (made by scripts/showcase-day.mjs out of
 * real episodes, with the sensor, the phone, the mute and the night written around them). The data stays in the JSON;
 * this file only reads it (and names its chapters). Pure: no clock, no DOM.
 */
import type { Scenario } from '../scenarios.ts';
import type { Localized } from '../types.ts';

type Kind = 'rapid' | 'long' | 'carbs';
export type DayData = {
	step: number;
	glucose: number[];
	treatments: { at: number; items: { kind: Kind; amount: number }[] }[];
	quiet: [number, number][];
	muted: [number, number][];
	phoneAway: [number, number][];
	dnd: [number, number][];
	sensor: { ended: number; changed: number; ready: number; left: [number, number][] };
	battery: [number, number][];
	steps: [number, number][];
	chapters: { id: string; at: number }[];
};

const DAY = 1440;
const mod = (t: number) => ((t % DAY) + DAY) % DAY;
/** Whether minute t (of the day) falls in [a, b), also when the span crosses midnight. */
export const inSpan = (t: number, [a, b]: [number, number]) => (a <= b ? t >= a && t < b : t >= a || t < b);

/**
 * A value that moves between keyframes [minute, value], in a loop: from each keyframe to the next one (the last to the
 * first, through midnight) in a straight line.
 */
export function keyframes(ks: [number, number][]) {
	const k = [...ks].sort((a, b) => a[0] - b[0]);
	return (t: number) => {
		t = mod(t);
		let i = k.findLastIndex(([at]) => at <= t);
		if (i < 0) i = k.length - 1;
		const [a, va] = k[i];
		const [b, vb] = k[(i + 1) % k.length];
		const span = mod(b - a) || DAY;
		return va + ((vb - va) * mod(t - a)) / span;
	};
}

/** The glucose between the 5-minute samples: a smooth curve through them (Catmull-Rom), in a loop. */
export function curve(samples: number[], step: number) {
	const n = samples.length;
	const at = (i: number) => samples[((i % n) + n) % n];
	return (t: number) => {
		const x = mod(t) / step;
		const i = Math.floor(x);
		const u = x - i;
		const [p0, p1, p2, p3] = [at(i - 1), at(i), at(i + 1), at(i + 2)];
		return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
	};
}

export const CHAPTERS: Record<string, Localized> = {
	night: { en: 'Asleep', es: 'Durmiendo' },
	low: { en: 'A low at night', es: 'Una baja de noche' },
	morning: { en: 'Awake, battery charged', es: 'Despierto, batería cargada' },
	breakfast: { en: 'Breakfast with insulin', es: 'Desayuno con insulina' },
	high: { en: 'A high, then a correction', es: 'Una alta y una corrección' },
	ending: { en: 'The sensor’s last hours', es: 'Las últimas horas del sensor' },
	lunch: { en: 'Lunch', es: 'Comida' },
	ended: { en: 'Sensor ended', es: 'Sensor terminado' },
	warmup: { en: 'A new sensor warms up', es: 'Un sensor nuevo calienta' },
	away: { en: 'Phone left behind', es: 'El móvil se queda atrás' },
	back: { en: 'Phone back', es: 'Vuelve el móvil' },
	muted: { en: 'Alerts muted for the cinema', es: 'Alertas silenciadas en el cine' },
	dinner: { en: 'Dinner', es: 'Cena' },
	basal: { en: 'Long-acting insulin', es: 'Insulina lenta' },
	sleep: { en: 'Do Not Disturb: night', es: 'No molestar: noche' },
};

export function dayScenario(d: DayData): Scenario & { chapters: { id: string; at: number; name: Localized }[] } {
	const glucose = curve(d.glucose, d.step);
	const left = keyframes(d.sensor.left);
	const battery = keyframes(d.battery);
	const steps = (t: number) => {
		// steps count from midnight, so no loop between the last keyframe and the first
		const k = d.steps;
		const i = Math.max(0, k.findLastIndex(([at]) => at <= t));
		const [a, va] = k[i];
		const [b, vb] = k[Math.min(i + 1, k.length - 1)];
		return Math.round(b > a ? va + ((vb - va) * (t - a)) / (b - a) : va);
	};
	const sensor = (t: number) => {
		t = mod(t);
		const { ended, changed, ready } = d.sensor;
		if (t >= ended && t < changed) return { state: 'ended' as const };
		if (t >= changed && t < ready - 5) return { state: 'warmup' as const, done: (t - changed) / (ready - changed) };
		if (t >= ready - 5 && t < ready) return { state: 'almost' as const };
		return { state: 'ok' as const, leftH: left(t) };
	};
	const away = (t: number) => d.phoneAway.some((s) => inSpan(mod(t), s));
	// today's time in range: from the readings since midnight, once a quarter of the day has passed (as Sukrara)
	const read = (i: number) => {
		const m = i * d.step;
		return sensor(m).state === 'ok' && !away(m);
	};
	const tir = (t: number) => {
		if (mod(t) < DAY / 4) return null;
		let n = 0;
		let ok = 0;
		for (let i = 0; i * d.step <= mod(t); i++) {
			if (!read(i)) continue;
			n++;
			if (d.glucose[i] >= 70 && d.glucose[i] <= 180) ok++;
		}
		return n ? Math.round((100 * ok) / n) : null;
	};
	return {
		id: 'day',
		name: { en: 'A day', es: 'Un día' },
		about: {
			en: 'A made-up day from real episodes, on a loop.',
			es: 'Un día inventado a partir de episodios reales, en bucle.',
		},
		start: [0, 0],
		minutes: DAY,
		loop: true,
		glucose,
		reaches: (t) => !away(t),
		sensor,
		treatments: d.treatments,
		quiet: d.quiet,
		muted: d.muted,
		dnd: (t) => d.dnd.some((s) => inSpan(mod(t), s)),
		watch: (t) => ({ batteryDays: Math.round(battery(t) * 10) / 10, steps: steps(mod(t)) }),
		tir,
		chapters: d.chapters.map((c) => ({ ...c, name: CHAPTERS[c.id] ?? { en: c.id, es: c.id } })),
	};
}
