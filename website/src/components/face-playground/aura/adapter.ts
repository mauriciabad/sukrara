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
import { scopeIds } from '../svg-ids.ts';
import { examples } from './examples.ts';
import { markParts, PARTS, partsOfMarkup, SIDES } from './parts.ts';
import { places } from './places.ts';
import { tourSettings } from './settings.ts';
import { GROUPS, PRESETS_ES } from './presets.ts';

export type AuraCase = { id: string; group: string; title: string; cfg: Values };

const L = (en: string, es: string): Localized => ({ en, es });
const opts = (...o: [string, string, string][]) => o.map(([value, en, es]) => ({ value, label: L(en, es) }));

const SIDE_OPTIONS = Object.entries(SIDES).map(([value, s]) => ({ value, label: s.name }));

/** Every field a slot can show (SPEC.md §6.10, §11; the watch's own names in its strings.xml), in the settings' order. */
const FIELD_OPTIONS = opts(
	['tir', 'Time in range', 'Tiempo en rango'], ['treatment', 'Last treatment', 'Último tratamiento'], ['steps', 'Steps', 'Pasos'],
	['floors', 'Floors', 'Pisos'], ['calories', 'Calories', 'Calorías'], ['intensity', 'Intensity minutes', 'Minutos de intensidad'],
	['pushes', 'Wheelchair pushes', 'Impulsos en silla de ruedas'], ['heart', 'Heart rate', 'Pulso'], ['bodyBattery', 'Energy', 'Energía'],
	['stress', 'Stress', 'Estrés'], ['spo2', 'Pulse Ox', 'Pulsioxímetro'], ['respiration', 'Respiration', 'Respiración'],
	['sleep', 'Sleep score', 'Puntuación de sueño'], ['recovery', 'Recovery time', 'Tiempo de recuperación'], ['weather', 'Weather', 'Tiempo'],
	['temperature', 'Temperature', 'Temperatura'], ['highLow', 'Today’s high and low', 'Máxima y mínima de hoy'],
	['forecast1', 'Forecast tomorrow', 'Previsión de mañana'], ['forecast2', 'Forecast in 2 days', 'Previsión en 2 días'],
	['forecast3', 'Forecast in 3 days', 'Previsión en 3 días'], ['sunrise', 'Sunrise', 'Amanecer'], ['sunset', 'Sunset', 'Puesta de sol'],
	['pressure', 'Pressure', 'Presión'], ['altitude', 'Altitude', 'Altitud'], ['date', 'Date', 'Fecha'], ['weekday', 'Weekday', 'Día de la semana'],
	['tz2', 'Second time zone', 'Segunda zona horaria'], ['events', 'Next event', 'Próximo evento'], ['battery', 'Watch battery', 'Batería del reloj'],
	['solar', 'Solar input', 'Intensidad solar'], ['notifications', 'Notifications', 'Notificaciones'], ['training', 'Training status', 'Estado de entrenamiento'],
	['vo2Run', 'VO2 max running', 'VO2 máx. carrera'], ['vo2Bike', 'VO2 max cycling', 'VO2 máx. ciclismo'],
	['runWeek', 'Running this week', 'Carrera esta semana'], ['bikeWeek', 'Cycling this week', 'Ciclismo esta semana'],
	['race5k', 'Race prediction 5K', 'Predicción 5K'], ['race10k', 'Race prediction 10K', 'Predicción 10K'],
	['raceHalf', 'Race prediction half marathon', 'Predicción media maratón'], ['raceMarathon', 'Race prediction marathon', 'Predicción maratón'],
	['pace5k', 'Race pace 5K', 'Ritmo 5K'], ['pace10k', 'Race pace 10K', 'Ritmo 10K'], ['paceHalf', 'Race pace half marathon', 'Ritmo media maratón'],
	['paceMarathon', 'Race pace marathon', 'Ritmo maratón'], ['golf', 'Last golf round', 'Última ronda de golf'], ['none', 'None', 'Ninguno'],
);

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
	{ key: 'topField', group: 'look', kind: 'select', label: L('Top field 1', 'Campo de arriba 1'), options: FIELD_OPTIONS },
	{ key: 'topField2', group: 'look', kind: 'select', label: L('Top field 2', 'Campo de arriba 2'), options: FIELD_OPTIONS },
	{ key: 'sideL', group: 'look', kind: 'select', label: L('Left side', 'Lado izquierdo'), options: SIDE_OPTIONS },
	{ key: 'sideR', group: 'look', kind: 'select', label: L('Right side', 'Lado derecho'), options: SIDE_OPTIONS },
	{ key: 'seconds', group: 'look', kind: 'toggle', label: L('Seconds sweep', 'Barrido de segundos') },
	{ key: 'rangeColour', group: 'look', kind: 'toggle', label: L('Green in range', 'Verde en rango') },
];

const hms = (d: Date) => `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;

/** What a live moment sets on Aura's inputs (SPEC.md §3). */
export function auraFromLive(live: Live): Values {
	const v: Values = { time: hms(live.now), phone: live.phone };
	v.mode = live.dnd ? 'night' : 'awake';
	const s = live.sensor;
	if (s.state === 'warmup') Object.assign(v, { sensor: 'warmup', warm: s.done });
	else if (s.state === 'almost' || s.state === 'ended') v.sensor = s.state;
	else if (!live.reading) v.sensor = 'none';
	// "ending" is Sukrara's last 8 h of a sensor (SPEC.md §3); the face counts them down in a field
	else v.sensor = s.leftH !== undefined && s.leftH <= 8 ? 'ending' : 'ok';
	// the hours it has left, when the scenario knows them: the sensor rail fills by them (a 15-day life)
	if (s.state === 'ok' && s.leftH !== undefined) Object.assign(v, { sensorLeft: Math.max(0, s.leftH), sensorLife: 15, sensorDates: true });
	if (live.reading) {
		const { glucose, rate, ageMin } = live.reading;
		Object.assign(v, { glucose, rate: rate ?? 0, trend: rate !== null, age: Math.round(ageMin * 10) / 10 });
		Object.assign(v, { history: live.history, spark: [...live.history, glucose] });
	}
	Object.assign(v, live.hold ? { status: live.hold.kind, left: live.hold.leftMin } : { status: 'auto' });
	if (live.watch) Object.assign(v, { battery: live.watch.batteryDays, steps: live.watch.steps });
	if (live.tir !== undefined) v.tir = live.tir;
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
	const parts = partsOfMarkup(aura.MARKUP);
	const dial = INPUTS.find((i) => i.key === 'dial') as Extract<InputSpec, { kind: 'select' }>;
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
		render: (values, lang, o = {}) => {
			const svg = markParts(aura.svg({ ...values, lang, size: 390, bezel: false }), parts);
			return o.id ? scopeIds(svg, o.id) : svg;
		},
		parts: PARTS,
		radius: 195,
		places: (values) => places((cfg) => aura.layout({ ...cfg, size: 390, bezel: false }), values),
		settings: tourSettings(INPUTS),
		looks: {
			key: 'dial',
			label: dial.label,
			options: dial.options,
			only: 'dial',
			// the dial alone (no halo cut out of it) at the newest reading's colour and place, heading away from the
			// ideal (112.5) as fast as the inputs go, where the aura spreads most; the moment's night or warm-up aside
			preview: (values) => {
				const reading = values.sensor === 'ok' || values.sensor === 'ending';
				const glucose = reading && typeof values.glucose === 'number' ? values.glucose : 112;
				return { ...defaults(), ...values, sensor: 'ok', mode: 'awake', glucose, rate: glucose >= 112.5 ? 4 : -4, trend: true, age: 0.5, halo: 'none', seconds: false };
			},
		},
		moving: { seconds: false },
		examples: examples(defaults(), (id) => cases.find((c) => c.id === id)?.cfg),
		ready: fonts,
	};
	return face;
}
