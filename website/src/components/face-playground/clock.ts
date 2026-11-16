// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/** The simulation clock: simulated minutes since a scenario began, running at a chosen speed. Pure: no timers. */

export type Clock = { minutes: number; speed: number; running: boolean };

/** 1× is real time; 60× is a minute a second; 600× is ten minutes a second. */
export const SPEEDS = [1, 10, 60, 600] as const;

export const start = (speed = 60): Clock => ({ minutes: 0, speed, running: true });

/** The clock after `realMs` of real time. A paused clock doesn't move. */
export const advance = (c: Clock, realMs: number): Clock =>
	c.running ? { ...c, minutes: c.minutes + (realMs / 60000) * c.speed } : c;

/** The clock stopped at its end, or as it was. */
export const clampTo = (c: Clock, endMin: number): Clock =>
	c.minutes >= endMin ? { ...c, minutes: endMin, running: false } : c;

/** The clock in a loop of `period` minutes: past the end it starts again (a day that loops). */
export const wrap = (c: Clock, period: number): Clock => ({ ...c, minutes: ((c.minutes % period) + period) % period });
