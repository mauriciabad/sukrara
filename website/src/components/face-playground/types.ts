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
	/** The last treatment, when the card's slot beside the value shows one. */
	treatment: { items: { kind: 'rapid' | 'long' | 'carbs'; amount: number }[]; agoMin: number } | null;
	/** Do Not Disturb (the watch's sleep). */
	dnd: boolean;
	/** The watch: its battery in days left, and today's steps, when the scenario says. */
	watch?: { batteryDays: number; steps: number };
	/** Today's time in range (%), or null when not known yet; absent when the scenario doesn't say. */
	tir?: number | null;
};

/**
 * A part of the face a person can point at, as the face tour names it: the side of the face its label goes on, its icon
 * (the Android app's own, lib/ds-icons.ts), its name and a short line on what it tells them.
 */
export type Part = {
	id: string;
	side: 'left' | 'right';
	icon: string;
	name: Localized;
	about: Localized;
	/**
	 * A place whose element is a setting (Aura's sides): the input that picks it, and each choice's icon, name and line.
	 * The label shows the picked one, with `name` above it.
	 */
	choice?: { key: string; options: Record<string, { icon: string; name: Localized; about: Localized }> };
};

/** A box on the screen, in the face's own units: (0, 0) its centre, the screen the circle r = `FaceAdapter.radius`, y down. A point has w = h = 0, and may name the box a pointer aims at (`hit`). */
export type Box = { x: number; y: number; w: number; h: number; hit?: Box };
/** Where a part is: its boxes; whether it's drawn now (if not, where it shows when it is); the drawing's part it names (data-part). */
export type Place = { boxes: Box[]; shown: boolean; part: string };

/**
 * A setting a person can try on the face tour: cards to pick from (each with a drawing), a list, or an on / off card.
 * `picture` is SVG markup (the elements inside a 48 × 48 view box, in currentColor).
 */
export type TourSetting =
	| { key: string; label: Localized; kind: 'cards'; options: { value: string; label: Localized; picture: string }[] }
	| { key: string; label: Localized; kind: 'select'; options: { value: string; label: Localized }[] }
	| { key: string; label: Localized; kind: 'switch'; picture: string; on: unknown; off: unknown };

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
	/** The parts, top to bottom on each side (FaceTour names them around the face). */
	parts: Part[];
	/** The screen's radius in the drawing's units (the boxes of places() are in them). */
	radius: number;
	/** Where every part is with these inputs (by Part.id). */
	places(values: Values): Record<string, Place>;
	/** The settings the face tour lets a person try, their starting values, and the inputs they set. */
	settings: { list: TourSetting[]; groups: { id: string; label: Localized; keys: string[] }[]; defaults: Values; apply(s: Values): Values };
	/**
	 * The inputs that pick a look the tour offers as previews (one key, its choices): Aura's dial. A preview shows the
	 * part `only` alone, drawn with preview(values): the newest reading's colour and place, the look's effect plain.
	 */
	looks: { key: string; label: Localized; options: { value: string; label: Localized }[]; only: string; preview(values: Values): Values };
	/** Inputs every moving preview takes (Aura: no seconds sweep, it would spin at a moving clock's speed). */
	moving: Values;
	/** Examples by group (FaceGallery shows them). */
	examples: ExampleGroup[];
	/** Resolves when the face can draw at its best (its fonts). */
	ready(): Promise<void>;
}
