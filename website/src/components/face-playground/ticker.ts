// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * One running clock (clock.ts) for everything a component shows, so every face it drives draws the same moment: one
 * requestAnimationFrame loop, any number of listeners. Each component makes its own; nothing is global.
 */
import { advance, clampTo, wrap, type Clock } from './clock.ts';

export type Ticker = ReturnType<typeof ticker>;

type Course = { speed: number; period: number; stop?: boolean };

/**
 * A clock at `speed` over `period` minutes, from `minutes`, stopped until play(): past the end it loops, or with
 * `stop` it stops there.
 */
export function ticker(init: Course & { minutes?: number }) {
	const o: Course = { ...init };
	let clock: Clock = { minutes: init.minutes ?? 0, speed: o.speed, running: false };
	let frame = 0;
	const listeners = new Set<(c: Clock) => void>();
	const emit = () => listeners.forEach((f) => f(clock));
	const fit = (c: Clock) => (o.stop ? clampTo(c, o.period) : wrap(c, o.period));
	const loop = (prev: number) => {
		frame = requestAnimationFrame((now) => {
			clock = fit(advance(clock, now - prev));
			emit();
			if (clock.running) loop(now);
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
			if (!clock.running) return;
			cancelAnimationFrame(frame);
			clock = { ...clock, running: false };
			emit();
		},
		seek(minutes: number) {
			clock = fit({ ...clock, minutes });
			if (!clock.running) cancelAnimationFrame(frame);
			emit();
		},
		/** Changes the speed, the period or how it ends from the next tick on; seek() to show it now. */
		set(c: Partial<Course>) {
			Object.assign(o, c);
			clock = { ...clock, speed: o.speed };
		},
	};
}
