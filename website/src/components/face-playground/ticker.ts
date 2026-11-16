// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * One running clock (clock.ts) for everything a component shows, so every face it drives draws the same moment: one
 * requestAnimationFrame loop, any number of listeners. Each component makes its own; nothing is global.
 */
import { advance, wrap, type Clock } from './clock.ts';

export type Ticker = ReturnType<typeof ticker>;

/** A clock at `speed` that loops every `period` minutes, from `minutes`, stopped until play(). */
export function ticker(o: { speed: number; period: number; minutes?: number }) {
	let clock: Clock = { minutes: o.minutes ?? 0, speed: o.speed, running: false };
	let frame = 0;
	const listeners = new Set<(c: Clock) => void>();
	const emit = () => listeners.forEach((f) => f(clock));
	const loop = (prev: number) => {
		frame = requestAnimationFrame((now) => {
			clock = wrap(advance(clock, now - prev), o.period);
			emit();
			loop(now);
		});
	};
	return {
		get clock() {
			return clock;
		},
		/** Calls `f` now and at every tick; returns the way to stop listening. */
		listen(f: (c: Clock) => void) {
			listeners.add(f);
			f(clock);
			return () => void listeners.delete(f);
		},
		play() {
			if (clock.running) return;
			clock = { ...clock, running: true };
			emit();
			loop(performance.now());
		},
		pause() {
			cancelAnimationFrame(frame);
			clock = { ...clock, running: false };
			emit();
		},
		seek(minutes: number) {
			clock = wrap({ ...clock, minutes }, o.period);
			emit();
		},
	};
}
