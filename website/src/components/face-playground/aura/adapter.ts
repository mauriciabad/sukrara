// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Sukrara Aura as a playground face. Everything the face draws, and every default, comes from its reference
 * implementation (tools/watchface/aura.js, passed in as `aura`); this file only names the inputs the controls offer
 * (contracts/watchface/aura/SPEC.md §3 and §11), picks the presets from the reference cases, and maps a simulated
 * moment onto the inputs. No drawing, no design numbers.
 */
import type { FaceAdapter, InputSpec, Live, Localized, Preset, Values } from '../types.ts';
import type { AuraModule } from './evaluate.ts';
import { GROUPS, PRESETS_ES } from './presets.ts';

export type AuraCase = { id: string; group: string; title: string; cfg: Values };

const L = (en: string, es: string): Localized => ({ en, es });
const opts = (...o: [string, string, string][]) => o.map(([value, en, es]) => ({ value, label: L(en, es) }));

const INPUTS: InputSpec[] = [
	{ key: 'glucose', group: 'reading', kind: 'number', label: L('Glucose', 'Glucosa'), min: 38, max: 420, step: 1, unit: L('mg/dL', 'mg/dL') },
	{ key: 'rate', group: 'reading', kind: 'number', label: L('Trend', 'Tendencia'), min: -4, max: 4, step: 0.1, unit: L('mg/dL a minute', 'mg/dL por minuto') },
	{ key: 'age', group: 'reading', kind: 'number', label: L('Reading’s age', 'Antigüedad de la lectura'), min: 0, max: 240, step: 0.5, unit: L('min', 'min') },
	{ key: 'unit', group: 'reading', kind: 'select', label: L('Unit', 'Unidad'), options: opts(['mgdl', 'mg/dL', 'mg/dL'], ['mmol', 'mmol/L', 'mmol/L']) },
	{
		key: 'sensor', group: 'sensor', kind: 'select', label: L('Sensor', 'Sensor'),
		options: opts(
			['ok', 'Working', 'Funcionando'], ['warmup', 'Warming up', 'Calentando'], ['almost', 'Almost ready', 'Casi listo'],
			['ending', 'Ending soon', 'Termina pronto'], ['endedSending', 'Ended, still sending', 'Terminado, aún envía'],
			['ended', 'Ended', 'Terminado'], ['none', 'Nothing paired', 'Nada emparejado'],
		),
	},
	{
		key: 'status', group: 'sensor', kind: 'select', label: L('Alerts', 'Alertas'),
		options: opts(
			['auto', 'As Sukrara decides', 'Lo que decida Sukrara'], ['quiet', 'Waiting after a treatment', 'En espera tras un tratamiento'],
			['muted', 'Muted', 'Silenciadas'], ['none', 'Nothing to say', 'Nada que decir'],
		),
	},
	{ key: 'left', group: 'sensor', kind: 'number', label: L('Time left (wait or mute)', 'Tiempo restante (espera o silencio)'), min: 0, max: 600, step: 1, unit: L('min', 'min') },
	{ key: 'phone', group: 'sensor', kind: 'toggle', label: L('Phone in reach', 'Móvil al alcance') },
	{ key: 'time', group: 'watch', kind: 'time', label: L('Time', 'Hora') },
	{ key: 'mode', group: 'watch', kind: 'select', label: L('Screen', 'Pantalla'), options: opts(['awake', 'Awake', 'Activa'], ['aod', 'Always-on', 'Siempre activa'], ['night', 'Night', 'Noche']) },
	{ key: 'privacy', group: 'watch', kind: 'select', label: L('Private', 'Privado'), options: opts(['open', 'Off', 'Desactivado'], ['private', 'On', 'Activado'], ['revealed', 'On, held to show', 'Activado, mostrado al mantener pulsado']) },
	{
		key: 'dial', group: 'look', kind: 'select', label: L('Dial', 'Fondo'),
		options: opts(
			['halftone', 'Grain', 'Grano'], ['topo', 'Contours', 'Curvas de nivel'], ['water', 'Waterline', 'Línea de agua'],
			['laser', 'Lasers', 'Láseres'], ['glow', 'Glow', 'Resplandor'], ['map', 'Map', 'Mapa'], ['black', 'Black', 'Negro'],
		),
	},
	{ key: 'halo', group: 'look', kind: 'select', label: L('Halo', 'Halo'), options: opts(['outline', 'Outline', 'Contorno'], ['box', 'Box', 'Caja'], ['none', 'None', 'Ninguno']) },
	{ key: 'trendStyle', group: 'look', kind: 'select', label: L('Trend as', 'Tendencia como'), options: opts(['arrow', 'Arrow', 'Flecha'], ['spark', 'Sparkline', 'Gráfica']) },
	{
		key: 'topField', group: 'look', kind: 'select', label: L('Top field', 'Campo de arriba'),
		options: opts(
			['weather', 'Weather', 'Tiempo'], ['steps', 'Steps', 'Pasos'], ['sunset', 'Sunset', 'Puesta de sol'], ['tir', 'Time in range', 'Tiempo en rango'],
			['heart', 'Heart rate', 'Pulso'], ['bodyBattery', 'Energy', 'Energía'], ['altitude', 'Altitude', 'Altitud'],
			['notifications', 'Notifications', 'Notificaciones'], ['tz2', 'Second time zone', 'Segunda zona horaria'], ['floors', 'Floors', 'Pisos'], ['none', 'None', 'Ninguno'],
		),
	},
	{ key: 'seconds', group: 'look', kind: 'toggle', label: L('Seconds sweep', 'Barrido de segundos') },
	{ key: 'rangeColour', group: 'look', kind: 'toggle', label: L('Green in range', 'Verde en rango') },
];

const hms = (d: Date) => `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;

/** What a live moment sets on Aura's inputs (SPEC.md §3). */
export function auraFromLive(live: Live): Values {
	const v: Values = { time: hms(live.now), phone: live.phone };
	if (live.dnd) v.mode = 'night';
	if (live.sensor.state === 'warmup') Object.assign(v, { sensor: 'warmup', warm: live.sensor.done });
	else if (live.sensor.state === 'almost') v.sensor = 'almost';
	else if (!live.reading) v.sensor = 'none';
	else v.sensor = 'ok';
	if (live.reading) {
		const { glucose, rate, ageMin } = live.reading;
		Object.assign(v, { glucose, rate: rate ?? 0, trend: rate !== null, age: Math.round(ageMin * 10) / 10 });
		Object.assign(v, { history: live.history, spark: [...live.history, glucose] });
	}
	Object.assign(v, live.quiet ? { status: 'quiet', left: live.quiet.leftMin } : { status: 'auto' });
	v.last = live.treatment
		? { items: live.treatment.items.map((i) => ({ k: i.kind, a: String(i.amount) })), ago: live.treatment.agoMin }
		: { items: [] };
	return v;
}

export function createAura(aura: AuraModule, cases: AuraCase[], fonts: () => Promise<void> = async () => {}): FaceAdapter {
	const defaults = () => new aura.AuraFace({}).opt();
	const presets: Preset[] = cases
		.filter((c) => c.id in PRESETS_ES)
		.map((c) => ({
			id: c.id,
			group: c.group,
			name: L(c.title, PRESETS_ES[c.id]),
			values: { ...defaults(), ...c.cfg },
		}));
	const face: FaceAdapter = {
		id: 'aura',
		name: L('Sukrara Aura', 'Sukrara Aura'),
		groups: [
			{ id: 'reading', label: L('Reading', 'Lectura') },
			{ id: 'sensor', label: L('Sensor and alerts', 'Sensor y alertas') },
			{ id: 'watch', label: L('Watch', 'Reloj') },
			{ id: 'look', label: L('Look', 'Aspecto') },
		],
		inputs: INPUTS,
		defaults,
		presets,
		presetGroups: GROUPS,
		fromLive: auraFromLive,
		// the screen alone at 1:1, no bezel (the bezel is a drawing of a branded watch)
		render: (values, lang) => aura.svg({ ...values, lang, size: 390, bezel: false }),
		ready: fonts,
	};
	return face;
}
