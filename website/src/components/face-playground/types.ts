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
	/** The 36 readings before it (3 h), 5 min apart, oldest first; null = missed. */
	history: (number | null)[];
	/** Whether the phone is in reach. */
	phone: boolean;
	/**
	 * The sensor: running (with the hours it has left, when known), warming up (done: 0 → 1), in its last minutes of
	 * warm-up, or ended.
	 */
	sensor: { state: 'ok'; leftH?: number } | { state: 'warmup'; done: number } | { state: 'almost' } | { state: 'ended' };
	/** Alerts held: waiting after a treatment, or muted, with the minutes left; null = Sukrara decides as usual. */
	hold: { kind: 'quiet' | 'muted'; leftMin: number } | null;
	/** The last treatment, if any in the last 24 h. */
	treatment: { items: { kind: 'rapid' | 'long' | 'carbs'; amount: number }[]; agoMin: number } | null;
	/** Do Not Disturb (the watch's sleep). */
	dnd: boolean;
	/** The watch: its battery in days left, and today's steps, when the scenario says. */
	watch?: { batteryDays: number; steps: number };
	/** Today's time in range (%), or null when not known yet; absent when the scenario doesn't say. */
	tir?: number | null;
};

/** A part of the face a person can point at: its name and what it tells them. */
export type Part = { id: string; name: Localized; about: Localized };

/** One example of the face: what it shows, in words, and its inputs; `live` moves some of them with a clock. */
export type Example = {
	id: string;
	title: Localized;
	note: Localized;
	values: Values;
	/** For a moving example: its inputs at a minute of its loop (0 ≤ minute < loop). */
	live?: { loop: number; at: (minute: number) => Values };
};
export type ExampleGroup = { id: string; title: Localized; lead: Localized; examples: Example[] };

export type RenderOptions = {
	/** Scopes the drawing's ids, so several drawings can share a page. */
	id?: string;
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
	/** The face as SVG markup; every element that belongs to a part carries data-part="<Part.id>". */
	render(values: Values, lang: Lang, options?: RenderOptions): string;
	/** The parts, in reading order (FaceTour explains them). */
	parts: Part[];
	/** The inputs that pick a look the tour offers as previews (one key, its choices): Aura's dial. */
	looks: { key: string; label: Localized; options: { value: string; label: Localized }[] };
	/** Inputs every moving preview takes (Aura: no seconds sweep, it would spin at a moving clock's speed). */
	moving: Values;
	/** Examples by group (FaceGallery shows them). */
	examples: ExampleGroup[];
	/** Resolves when the face can draw at its best (its fonts). */
	ready(): Promise<void>;
}
