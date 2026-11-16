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

export const PARTS: Part[] = [
	{ id: 'date', name: L('Date', 'Fecha'), about: L('Weekday, day and month along the top.', 'Día de la semana, día y mes por arriba.') },
	{
		id: 'top',
		name: L('Top field', 'Campo de arriba'),
		about: L(
			'One of your choice under the date: steps by default; weather, sunset, heart rate, energy, a second time zone, time in range…',
			'Uno a tu elección bajo la fecha: pasos por defecto; tiempo, puesta de sol, pulso, energía, otra zona horaria, tiempo en rango…',
		),
	},
	{ id: 'time', name: L('Time', 'Hora'), about: L('Big, in the type’s own figures; it grows when there’s room.', 'Grande, con las cifras de la propia letra; crece cuando hay sitio.') },
	{
		id: 'value',
		name: L('Glucose', 'Glucosa'),
		about: L(
			'The newest reading, in its band’s colour. Late, its time shows; old, it’s struck through; then the face says since when. Warming up or ended, it says so.',
			'La lectura más reciente, en el color de su franja. Con retraso, se ve su hora; antigua, tachada; después la esfera dice desde cuándo. Calentando o terminado, lo dice.',
		),
	},
	{
		id: 'trend',
		name: L('The last hours', 'Las últimas horas'),
		about: L(
			'Your readings as a small line, up to 3 h, keeping your range in view, with Sukrara’s trend as a dashed ray. Or Sukrara’s arrow instead.',
			'Tus lecturas como una pequeña línea, hasta 3 h, sin perder de vista tu rango, con la tendencia de Sukrara como un rayo discontinuo. O la flecha de Sukrara.',
		),
	},
	{
		id: 'rail',
		name: L('Level rail', 'Riel de nivel'),
		about: L(
			'Where you are between your limits, from 1:30 to 4:30: up is higher. The bead is now, the trail the last 30 min.',
			'Dónde estás entre tus límites, de la 1:30 a las 4:30: arriba es más alto. La bolita es ahora; la estela, los últimos 30 min.',
		),
	},
	{
		id: 'pill',
		name: L('What to do', 'Qué hacer'),
		about: L(
			'Carbs or Insulin when Sukrara says so. After a treatment, a paused bell and the time left; muted, the crossed bell. A late reading’s time.',
			'Hidratos o Insulina cuando Sukrara lo dice. Tras un tratamiento, una campana en pausa y el tiempo que queda; silenciadas, la campana tachada. La hora de una lectura con retraso.',
		),
	},
	{
		id: 'battery',
		name: L('Watch battery', 'Batería del reloj'),
		about: L('A segment per day left; amber under 2 days, red under 1.', 'Un segmento por día restante; ámbar por debajo de 2 días, rojo por debajo de 1.'),
	},
	{
		id: 'fields',
		name: L('Two fields', 'Dos campos'),
		about: L(
			'Today’s time in range and your last treatment by default; or steps, heart rate, altitude, notifications… In the sensor’s last hours, its countdown.',
			'El tiempo en rango de hoy y tu último tratamiento por defecto; o pasos, pulso, altitud, notificaciones… En las últimas horas del sensor, su cuenta atrás.',
		),
	},
	{
		id: 'sweep',
		name: L('Seconds', 'Segundos'),
		about: L(
			'A faint sweep near the rim, once a minute; it can be turned off. Here it runs in real time while you point at it: the day goes 600 times faster.',
			'Un barrido tenue junto al borde, una vez por minuto; se puede desactivar. Aquí va en tiempo real mientras lo señalas: el día va 600 veces más rápido.',
		),
	},
	{
		id: 'dial',
		name: L('Dial', 'Fondo'),
		about: L(
			'The aura of your glucose: it gathers at your level and spreads the further out of range you are. Seven to choose from.',
			'El aura de tu glucosa: se concentra en tu nivel y se extiende cuanto más fuera de rango estás. Siete para elegir.',
		),
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
