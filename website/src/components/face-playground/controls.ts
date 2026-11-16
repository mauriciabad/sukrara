// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The form for a face's inputs, built from its InputSpec list: a fieldset per group; a slider with a number box, a
 * select, a switch or a time field per input. It reports edits and shows values; it holds no state of its own.
 */
import type { FaceAdapter, InputSpec, Lang, Values } from './types.ts';

let uid = 0;

const el = <K extends keyof HTMLElementTagNameMap>(tag: K, props: Partial<HTMLElementTagNameMap[K]> = {}, ...kids: (Node | string)[]) => {
	const e = Object.assign(document.createElement(tag), props);
	e.append(...kids);
	return e;
};

/** "0:40:00" → "00:40:00", as a time field wants it. */
export const timeField = (v: unknown) => {
	const [h = '0', m = '0', s = '0'] = String(v).split(':');
	return [h, m, s].map((x) => x.padStart(2, '0')).join(':');
};

type Field = { spec: InputSpec; inputs: HTMLInputElement[] | [HTMLSelectElement]; show(v: unknown): void };

export function controls(host: HTMLElement, face: FaceAdapter, lang: Lang, onInput: (key: string, value: unknown) => void) {
	const fields = new Map<string, Field>();

	const make = (spec: InputSpec): [HTMLElement, Field] => {
		const id = `fp-${++uid}`;
		const unit = spec.kind === 'number' ? spec.unit?.[lang] : undefined;
		const label = el('label', { htmlFor: id }, unit ? `${spec.label[lang]} (${unit})` : spec.label[lang]);
		if (spec.kind === 'number') {
			const range = el('input', { type: 'range', min: String(spec.min), max: String(spec.max), step: String(spec.step), id });
			const box = el('input', { type: 'number', step: String(spec.step), inputMode: 'decimal' });
			box.setAttribute('aria-label', label.textContent!);
			const read = (e: Event) => {
				const v = Number((e.target as HTMLInputElement).value);
				if (Number.isFinite(v)) onInput(spec.key, v);
			};
			range.addEventListener('input', read);
			box.addEventListener('change', read);
			const show = (v: unknown) => {
				const s = String(v);
				if (range.value !== s) range.value = s;
				if (box.value !== s && document.activeElement !== box) box.value = s;
				if (unit) range.setAttribute('aria-valuetext', `${s} ${unit}`);
			};
			const row = el('div', { className: 'fp-field fp-number' }, label, el('div', { className: 'fp-pair' }, range, box));
			return [row, { spec, inputs: [range, box], show }];
		}
		if (spec.kind === 'select') {
			const select = el('select', { id }, ...spec.options.map((o) => el('option', { value: o.value }, o.label[lang])));
			select.addEventListener('change', () => onInput(spec.key, select.value));
			const show = (v: unknown) => void (select.value = String(v));
			return [el('div', { className: 'fp-field' }, label, select), { spec, inputs: [select], show }];
		}
		if (spec.kind === 'toggle') {
			const box = el('input', { type: 'checkbox', id, role: 'switch' } as Partial<HTMLInputElement>);
			box.addEventListener('change', () => onInput(spec.key, box.checked));
			const show = (v: unknown) => void (box.checked = v === true || v === 'true');
			return [el('div', { className: 'fp-field fp-toggle' }, box, label), { spec, inputs: [box], show }];
		}
		const time = el('input', { type: 'time', step: '1', id });
		time.addEventListener('change', () => time.value && onInput(spec.key, time.value));
		const show = (v: unknown) => {
			const s = timeField(v);
			if (time.value !== s && document.activeElement !== time) time.value = s;
		};
		return [el('div', { className: 'fp-field' }, label, time), { spec, inputs: [time], show }];
	};

	for (const g of face.groups) {
		const set = el('fieldset', { className: 'fp-group' }, el('legend', {}, g.label[lang]));
		for (const spec of face.inputs.filter((i) => i.group === g.id)) {
			const [row, field] = make(spec);
			set.append(row);
			fields.set(spec.key, field);
		}
		host.append(set);
	}

	return {
		/** Shows these values in their controls. */
		show(values: Values) {
			for (const [key, f] of fields) if (key in values) f.show(values[key]);
		},
		/** Locks the controls of these keys (the simulation drives them) and frees the rest. */
		lock(keys: Set<string>) {
			for (const [key, f] of fields) for (const i of f.inputs) i.disabled = keys.has(key);
		},
	};
}
