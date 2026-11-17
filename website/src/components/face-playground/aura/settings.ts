// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The settings of Sukrara Aura a person can try on the face tour (contracts/watchface/aura/SPEC.md §11), with a small
 * drawing for each card. Chosen: what changes what the face shows (how the last hours show, the three fields), Private
 * (it matters to whoever checks their watch in front of others), and two switches whose effect is plain to see. Left
 * out: the halo and the battery label (small looks of one part), the second time zone's city.
 */
import type { InputSpec, Localized, TourSetting, Values } from '../types.ts';

const L = (en: string, es: string): Localized => ({ en, es });

/** Each choice's drawing: SVG elements in a 48 × 48 box, in the text's colour (`--pic-*` let a picked card tint them). */
export const PICTURES: Record<string, string> = {
	arrow: '<path d="M10 32L30 18" stroke="currentColor" stroke-width="5" stroke-linecap="round"/><path d="M38 12L22 15L33 27Z" fill="currentColor"/>',
	spark:
		'<rect x="5" y="12" width="38" height="24" rx="2" fill="currentColor" opacity="0.12"/><path d="M6 30L12 27L17 29L22 22L27 24L32 18L37 19" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/><path d="M37 19L43 15" stroke="currentColor" stroke-width="2" stroke-dasharray="2 2" stroke-linecap="round"/><circle cx="37" cy="19" r="3" fill="currentColor"/>',
	privacy:
		'<rect x="6" y="13" width="36" height="22" rx="5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="15" cy="24" r="3" fill="currentColor"/><circle cx="24" cy="24" r="3" fill="currentColor"/><circle cx="33" cy="24" r="3" fill="currentColor"/><path d="M8 40L40 8" stroke="var(--pic-cut, transparent)" stroke-width="5" stroke-linecap="round"/><path d="M8 40L40 8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
	rangeColour:
		'<text x="24" y="33" text-anchor="middle" font-family="\'Atkinson Hyperlegible Next\', system-ui, sans-serif" font-size="22" font-weight="800" fill="var(--pic-green, currentColor)" stroke="var(--pic-ink, none)" stroke-width="3.5" paint-order="stroke" stroke-linejoin="round">112</text><path d="M8 39H40" stroke="var(--pic-green, currentColor)" stroke-width="3" stroke-linecap="round" opacity="0.6"/>',
	seconds:
		'<circle cx="24" cy="24" r="17" fill="none" stroke="currentColor" stroke-width="1.6" opacity="0.4"/><path d="M24 24L24 7A17 17 0 0 1 38.7 15.5Z" fill="currentColor" opacity="0.35"/><path d="M24 24L38.7 15.5" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/><circle cx="24" cy="24" r="2.4" fill="currentColor"/>',
};

export function tourSettings(inputs: InputSpec[]) {
	const options = (key: string) => (inputs.find((i) => i.key === key) as Extract<InputSpec, { kind: 'select' }>).options;
	const fields = [{ value: 'treatment', label: L('Last treatment', 'Último tratamiento') }, ...options('topField')];
	const list: TourSetting[] = [
		{ key: 'trendStyle', label: L('The last hours as', 'Las últimas horas como'), kind: 'cards', options: options('trendStyle').map((o) => ({ ...o, picture: PICTURES[o.value] })) },
		{ key: 'privacy', label: L('Private', 'Privado'), kind: 'switch', picture: PICTURES.privacy, on: 'private', off: 'open' },
		{ key: 'rangeColour', label: L('Green in range', 'Verde en rango'), kind: 'switch', picture: PICTURES.rangeColour, on: true, off: false },
		{ key: 'seconds', label: L('Seconds sweep', 'Barrido de segundos'), kind: 'switch', picture: PICTURES.seconds, on: true, off: false },
		{ key: 'topField', label: L('Top', 'Arriba'), kind: 'select', options: options('topField') },
		{ key: 'field1', label: L('Bottom left', 'Abajo a la izquierda'), kind: 'select', options: fields },
		{ key: 'field2', label: L('Bottom right', 'Abajo a la derecha'), kind: 'select', options: fields },
	];
	return {
		list,
		groups: [
			{ id: 'trend', label: L('The last hours as', 'Las últimas horas como'), keys: ['trendStyle'] },
			{ id: 'on', label: L('Turn on', 'Activar'), keys: ['privacy', 'rangeColour', 'seconds'] },
			{ id: 'fields', label: L('Fields', 'Campos'), keys: ['topField', 'field1', 'field2'] },
		],
		defaults: { trendStyle: 'spark', privacy: 'open', rangeColour: true, seconds: true, topField: 'steps', field1: 'tir', field2: 'treatment' } as Values,
		/** The face's inputs these settings set. */
		apply: ({ field1, field2, ...rest }: Values): Values => ({ ...rest, fields: [field1, field2] }),
	};
}
