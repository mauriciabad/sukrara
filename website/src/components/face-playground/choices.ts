// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The face pages' setting controls, one of each kind, shared by the face tour and the playground (styles in
 * face-ui.css): cards to pick one from (radio buttons, each with its drawing), an on / off card (a switch with its
 * drawing), and a labelled list. Each reports a change and can show a value; none holds state.
 */
import type { Lang, Localized } from './types.ts';

type Option = { value: string; label: Localized; picture?: string };
export type Choice = { root: HTMLElement; inputs: (HTMLInputElement | HTMLSelectElement)[]; show(v: unknown): void };

let uid = 0;
const el = <K extends keyof HTMLElementTagNameMap>(tag: K, props: Partial<HTMLElementTagNameMap[K]> = {}, ...kids: (Node | string)[]) => {
	const e = Object.assign(document.createElement(tag), props);
	e.append(...kids);
	return e;
};
const svg = (markup: string, viewBox = '0 0 48 48') => {
	const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	s.setAttribute('viewBox', viewBox);
	s.setAttribute('class', 'fu-card__pic');
	s.setAttribute('aria-hidden', 'true');
	s.innerHTML = markup;
	return s;
};

/** A row of cards, one picked (radio buttons); `legend` names the row. */
export function cards(legend: Localized, options: Option[], lang: Lang, onPick: (value: string) => void): Choice {
	const name = `fu-${++uid}`;
	const inputs = options.map((o) => el('input', { type: 'radio', name, value: o.value }));
	const row = el(
		'div',
		{ className: 'fu-cards' },
		...options.map((o, i) => {
			inputs[i].addEventListener('change', () => inputs[i].checked && onPick(o.value));
			return el('label', { className: 'fu-card' }, inputs[i], el('span', {}, ...(o.picture ? [svg(o.picture)] : []), o.label[lang]));
		}),
	);
	const root = el('fieldset', { className: 'fu-set' }, el('legend', {}, legend[lang]), row);
	return { root, inputs, show: (v) => inputs.forEach((i) => (i.checked = i.value === String(v))) };
}

/** An on / off card (a switch) with its drawing and a dot that says which. */
export function switchCard(label: Localized, picture: string | undefined, lang: Lang, onChange: (on: boolean) => void): Choice {
	const input = el('input', { type: 'checkbox' });
	input.setAttribute('role', 'switch');
	input.addEventListener('change', () => onChange(input.checked));
	const root = el(
		'label',
		{ className: 'fu-card fu-card--switch' },
		input,
		el('span', {}, ...(picture ? [svg(picture)] : []), label[lang], el('i', { className: 'fu-card__dot' })),
	);
	return { root, inputs: [input], show: (v) => (input.checked = v === true || v === 'true') };
}

/** A list to pick one from, with its label above. */
export function list(label: Localized, options: Option[], lang: Lang, onPick: (value: string) => void): Choice {
	const id = `fu-${++uid}`;
	const select = el('select', { id }, ...options.map((o) => el('option', { value: o.value }, o.label[lang])));
	select.addEventListener('change', () => onPick(select.value));
	const root = el('div', { className: 'fu-list' }, el('label', { htmlFor: id }, label[lang]), select);
	return { root, inputs: [select], show: (v) => (select.value = String(v)) };
}

/** A group of switches or lists under one name. */
export const group = (legend: Localized, lang: Lang, kids: HTMLElement[], className = '') =>
	el('fieldset', { className: `fu-set ${className}`.trim() }, el('legend', {}, legend[lang]), el('div', { className: 'fu-row' }, ...kids));
