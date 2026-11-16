// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The one interface between the playground and a watch face. The playground (controls, clock, scenarios) knows only
 * these types; a face plugs in as a FaceAdapter (aura/adapter.ts is Sukrara Aura's). Another face is another adapter.
 */

export type Lang = 'en' | 'es';
export type Localized = { en: string; es: string };

/** The face's inputs as the controls hold them: key → value. Keys without a control are carried as they are. */
export type Values = Record<string, unknown>;

type Base = { key: string; label: Localized; group: string };
export type InputSpec =
	| (Base & { kind: 'number'; min: number; max: number; step: number; unit?: Localized })
	| (Base & { kind: 'select'; options: { value: string; label: Localized }[] })
	| (Base & { kind: 'toggle' })
	| (Base & { kind: 'time' });

export type Preset = { id: string; group: string; name: Localized; values: Values };

/** What the world looks like at a moment of a scenario, in no face's terms (scenarios.ts makes it). */
export type Live = {
	/** The watch's clock. */
	now: Date;
	/** The newest reading that reached the watch, or null when none ever did. */
	reading: { glucose: number; rate: number | null; ageMin: number } | null;
	/** The 12 readings before it, 5 min apart, oldest first; null = missed. */
	history: (number | null)[];
	/** Whether the phone is in reach. */
	phone: boolean;
	/** The sensor: running, warming up (done: 0 → 1), or in its last minutes of warm-up. */
	sensor: { state: 'ok' } | { state: 'warmup'; done: number } | { state: 'almost' };
	/** Alerts held after a treatment, with the minutes left; null = Sukrara decides as usual. */
	quiet: { leftMin: number } | null;
	/** The last treatment, if any in the last 24 h. */
	treatment: { items: { kind: 'rapid' | 'long' | 'carbs'; amount: number }[]; agoMin: number } | null;
	/** Do Not Disturb (the watch's sleep). */
	dnd: boolean;
};

export interface FaceAdapter {
	id: string;
	name: Localized;
	groups: { id: string; label: Localized }[];
	inputs: InputSpec[];
	/** Every input's starting value (the face's own defaults). */
	defaults(): Values;
	presets: Preset[];
	/** The presets' group names, by Preset.group. */
	presetGroups: Record<string, Localized>;
	/** The values a live moment sets; the controls for these keys follow the simulation and are locked while it runs. */
	fromLive(live: Live): Values;
	/** The face as SVG markup. */
	render(values: Values, lang: Lang): string;
	/** Resolves when the face can draw at its best (its fonts). */
	ready(): Promise<void>;
}
