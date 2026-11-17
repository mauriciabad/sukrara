// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Sukrara Aura's parts, as a person names them, and which of the drawing's elements belong to each. The elements are
 * found in aura.js's own markup template (MARKUP), not by position: each element drawn on the screen names the values
 * it shows ({{time.str}}, {{rail.band}}…), and those name its part. A new element in aura.js that shows one of these
 * values joins its part by itself; one that shows something new stays unlabelled (and never fades).
 */
import type { Localized, Part } from '../types.ts';

const L = (en: string, es: string): Localized => ({ en, es });

// Top to bottom on each side of the face, as the parts sit there; the lines from the labels never cross.
export const PARTS: Part[] = [
	{ id: 'date', side: 'left', icon: 'calendar_month', name: L('Date', 'Fecha'), about: L('Weekday, day and month.', 'Día de la semana, día y mes.') },
	{
		id: 'dial', side: 'left', icon: 'palette', name: L('Dial', 'Fondo'),
		about: L('The aura of your glucose: it spreads the further out of range you are. Seven styles.', 'El aura de tu glucosa: se extiende cuanto más fuera de rango estás. Siete estilos.'),
	},
	{ id: 'time', side: 'left', icon: 'schedule', name: L('Time', 'Hora'), about: L('Big, and bigger when there’s room.', 'Grande, y más aún cuando hay sitio.') },
	{
		id: 'battery', side: 'left', icon: 'battery_full', name: L('Watch battery', 'Batería del reloj'),
		about: L('A segment per day left; amber under 2 days, red under 1.', 'Un segmento por día restante; ámbar por debajo de 2 días, rojo por debajo de 1.'),
	},
	{
		id: 'value', side: 'left', icon: 'water_drop', name: L('Glucose', 'Glucosa'),
		about: L('The newest reading in its range’s colour. Late or old, it says so.', 'La lectura más reciente, en el color de su rango. Si llega tarde o es antigua, lo dice.'),
	},
	{
		id: 'fields', side: 'left', icon: 'metric_second', name: L('Two fields', 'Dos campos'),
		about: L('Time in range and your last treatment, or steps, heart rate, altitude…', 'Tiempo en rango y tu último tratamiento, o pasos, pulso, altitud…'),
	},
	{
		id: 'sweep', side: 'right', icon: 'timer', name: L('Seconds', 'Segundos'),
		about: L(
			'A faint sweep by the rim, once a minute. It runs in real time here, while the day goes 600 times faster.',
			'Un barrido tenue junto al borde, una vez por minuto. Aquí va en tiempo real, mientras el día va 600 veces más rápido.',
		),
	},
	{
		id: 'top', side: 'right', icon: 'metric_main', name: L('Top field', 'Campo de arriba'),
		about: L('Steps, or weather, sunset, heart rate, a second time zone…', 'Pasos, o tiempo, puesta de sol, pulso, otra zona horaria…'),
	},
	{
		id: 'rail', side: 'right', icon: 'dia_range', name: L('Glucose rail', 'Riel de glucosa'),
		about: L('Where you are between your limits; up is higher. The trail is the last 30 min.', 'Dónde estás entre tus límites; arriba es más alto. La estela, los últimos 30 min.'),
	},
	{
		id: 'trend', side: 'right', icon: 'monitoring', name: L('The last hours', 'Las últimas horas'),
		about: L('Up to 3 h of readings and where they’re heading. Or an arrow.', 'Hasta 3 h de lecturas y hacia dónde van. O una flecha.'),
	},
	{
		id: 'pill', side: 'right', icon: 'notifications_active', name: L('What to do', 'Qué hacer'),
		about: L('Carbs or Insulin when Sukrara says so; a bell while alerts wait or are muted.', 'Hidratos o Insulina cuando Sukrara lo dice; una campana mientras las alertas esperan o callan.'),
	},
];

/** The values each part shows, by their name in MARKUP ({{name.…}}); the first that an element names wins. */
const SHOWS: [string, string][] = [
	['time', 'time'],
	['val', 'value'],
	['msg', 'value'],
	['sp', 'trend'],
	['pill', 'pill'],
	['chip', 'pill'],
	['bead', 'rail'],
	['rail', 'rail'],
	['batt', 'battery'],
	['tf', 'top'],
	['date', 'date'],
	['b', 'fields'],
	['radar', 'sweep'],
	['dial', 'dial'],
];

const TAG = /<(\/?)([a-zA-Z][\w:-]*)((?:"[^"]*"|[^>"])*?)(\/?)>/g;

/**
 * Which start tags of a drawing belong to which part: tag number (in document order) → part id. Read from the
 * template: the elements drawn on the screen are the children of its clipped group.
 */
export function partsOfMarkup(markup: string): Map<number, string> {
	const out = new Map<number, string>();
	let n = -1;
	let depth = 0;
	let screen = -1; // the depth of the clipped group's children
	let open: { n: number; from: number; depth: number } | null = null;
	for (const m of markup.matchAll(TAG)) {
		const [whole, close, , attrs, self] = m;
		if (close) {
			depth--;
			if (open && depth === open.depth) {
				const names = [...markup.slice(open.from, m.index! + whole.length).matchAll(/\{\{\s*(\w+)\./g)].map((x) => x[1]);
				const part = SHOWS.find(([name]) => names.includes(name))?.[1];
				if (part) out.set(open.n, part);
				open = null;
			}
			if (depth < screen) screen = -1;
			continue;
		}
		n++;
		if (screen === -1 && /\sclip-path=/.test(attrs) && whole.startsWith('<g')) screen = depth + 1;
		else if (depth === screen && !open) {
			open = { n, from: m.index!, depth };
			if (self) {
				const names = [...whole.matchAll(/\{\{\s*(\w+)\./g)].map((x) => x[1]);
				const part = SHOWS.find(([name]) => names.includes(name))?.[1];
				if (part) out.set(n, part);
				open = null;
			}
		}
		if (!self) depth++;
	}
	return out;
}

/** The drawing with data-part on each element of a part (the drawing comes from the same template, tag for tag). */
export function markParts(svg: string, parts: Map<number, string>): string {
	let n = -1;
	return svg.replace(TAG, (whole, close: string, name: string) => {
		if (close) return whole;
		n++;
		const part = parts.get(n);
		return part ? `<${name} data-part="${part}"${whole.slice(name.length + 1)}` : whole;
	});
}
