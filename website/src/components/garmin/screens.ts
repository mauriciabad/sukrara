// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The screens of Sukrara's watch app and data field the website shows, each a case of the reference renderer
 * (tools/watchface/app.js, the same cfg as tools/watchface/app-build.mjs's CASES) with its name and one line in both
 * languages. A `face` item is one of a watch face's examples instead (face-playground, drawn on the page).
 * WatchScreens.astro draws them.
 */
type Localized = { en: string; es: string };
const L = (en: string, es: string): Localized => ({ en, es });

export type WatchScreen = {
	title: Localized;
	note: Localized;
	/** app.js's cfg: cls (screen class, default the round 390), screen and the scenario. */
	cfg?: Record<string, unknown>;
	/** Or a watch face's example (aura/examples.ts), drawn live. */
	face?: string;
	/** Its name links here (a section of the page). */
	href?: Localized;
};

export const SCREENS = {
	// The app
	glance: {
		cfg: { screen: 'glance' },
		title: L('Glance', 'Widget en un vistazo'),
		note: L('Value, trend and time in range as you scroll.', 'Valor, tendencia y tiempo en rango al pasar.'),
	},
	app: {
		cfg: { screen: 'appA' },
		title: L('Your glucose', 'Tu glucosa'),
		note: L('The last 12 hours, your ranges and treatments.', 'Las últimas 12 horas, tus rangos y tratamientos.'),
	},
	numbers: {
		cfg: { screen: 'appNums' },
		title: L('Your numbers', 'Tus números'),
		note: L('Time in range, average, GMI and CV, 1 to 30 days.', 'Tiempo en rango, media, GMI y CV, de 1 a 30 días.'),
	},
	complications: {
		cfg: { screen: 'faceCx' },
		title: L('On any watch face', 'En cualquier esfera'),
		note: L('Glucose and time since your last treatment, on faces that take complications.', 'Glucosa y tiempo desde el último tratamiento, en esferas que admiten complicaciones.'),
	},
	list: {
		cfg: { screen: 'actions' },
		title: L('Log or mute', 'Registra o silencia'),
		note: L('START: rapid, long, carbs or mute.', 'START: rápida, lenta, hidratos o silenciar.'),
	},
	amount: {
		cfg: { screen: 'logAmount' },
		title: L('The amount', 'La cantidad'),
		note: L('Buttons, or one of your usual amounts.', 'Con los botones o una de tus cantidades habituales.'),
	},
	hold: {
		cfg: { screen: 'logConfirm' },
		title: L('Hold to log', 'Mantén para registrar'),
		note: L('Letting go cancels.', 'Si sueltas, se cancela.'),
	},
	logged: {
		cfg: { screen: 'logDone' },
		title: L('Logged', 'Registrado'),
		note: L('In the phone’s log too, with Undo.', 'También en el registro del móvil, con Deshacer.'),
	},
	queued: {
		cfg: { screen: 'logQueued' },
		title: L('Phone away', 'Móvil lejos'),
		note: L('Kept on the watch, sent when it’s back.', 'Se guarda en el reloj y se envía al volver.'),
	},
	muteLengths: {
		cfg: { screen: 'muteLengths' },
		title: L('Mute alerts', 'Silenciar alertas'),
		note: L('15 min to 2 h, or until you unmute.', 'De 15 min a 2 h, o hasta que las reactives.'),
	},
	muted: {
		cfg: { screen: 'muted' },
		title: L('Muted', 'Silenciadas'),
		note: L('Time left, Unmute, Change time.', 'Tiempo restante, Reactivar, Cambiar tiempo.'),
	},
	alertLow: {
		cfg: { screen: 'alert', scenario: 'low' },
		title: L('Alert', 'Alerta'),
		note: L('Treatment, Remind less or Mute, on the watch.', 'Tratamiento, Recordar menos o Silenciar, en el reloj.'),
	},
	alertVeryLow: {
		cfg: { screen: 'alert', scenario: 'veryLow' },
		title: L('Very low', 'Muy baja'),
		note: L('The whole screen in its colour.', 'Toda la pantalla en su color.'),
	},
	old: {
		cfg: { screen: 'appA', age: 7 },
		title: L('Old reading', 'Lectura antigua'),
		note: L('Struck through with its time, never as current.', 'Tachada y con su hora, nunca como actual.'),
	},
	warmup: {
		cfg: { screen: 'appA', sensor: 'warmup' },
		title: L('New sensor', 'Sensor nuevo'),
		note: L('Warming up, and when it’s ready.', 'Calentando, y cuándo estará listo.'),
	},
	setup: {
		cfg: { screen: 'setup' },
		title: L('First start', 'Primer inicio'),
		note: L('Says what it’s waiting for and opens it on the phone.', 'Dice qué espera y lo abre en el móvil.'),
	},
	updatePhone: {
		cfg: { screen: 'update', update: 'phone' },
		title: L('Update Sukrara on your phone', 'Actualiza Sukrara en tu móvil'),
		note: L('Your glucose stays; Open on phone takes you to the update.', 'Tu glucosa se queda; Abrir en el móvil te lleva a la actualización.'),
	},
	updateApp: {
		cfg: { screen: 'update', update: 'app' },
		title: L('Update this app', 'Actualiza esta app'),
		note: L('Opens its Connect IQ listing on the phone.', 'Abre su página de Connect IQ en el móvil.'),
	},
	// The data field
	fieldBand: {
		cfg: { screen: 'dfSlot', form: 'band' },
		title: L('Beside your stats', 'Junto a tus datos'),
		note: L('Value, trend and the last hour.', 'Valor, tendencia y la última hora.'),
	},
	fieldQuarterLow: {
		cfg: { screen: 'dfSlot', form: 'quarter', scenario: 'low' },
		title: L('Low, in a small slot', 'Baja, en un hueco pequeño'),
		note: L('The slot turns its colour.', 'El hueco toma su color.'),
	},
	fieldAlert: {
		cfg: { screen: 'dfAlert', scenario: 'low' },
		title: L('Alert mid-run', 'Alerta en plena actividad'),
		note: L('Full screen when Sukrara’s alert rings.', 'A pantalla completa cuando suena la alerta de Sukrara.'),
	},
	fieldUpdate: {
		cfg: { screen: 'dfSlot', form: 'band', update: 'phone' },
		title: L('Update the phone', 'Actualiza el móvil'),
		note: L('Your glucose stays; the label says what to update.', 'Tu glucosa se queda; la etiqueta dice qué actualizar.'),
	},
	fieldMip: {
		cfg: { cls: 'm260', screen: 'dfSlot', form: 'quarter' },
		title: L('On a fēnix 7', 'En un fēnix 7'),
		note: L('Readable in sunlight, on every Connect IQ 5 watch.', 'Legible al sol, en cualquier reloj con Connect IQ 5.'),
	},
	// The top of the Garmin page: one of each
	heroFace: {
		face: 'glucose-in-range',
		title: L('Watch faces', 'Esferas'),
		href: L('#watch-faces', '#esferas'),
		note: L('Your glucose next to the time.', 'Tu glucosa junto a la hora.'),
	},
	heroApp: {
		cfg: { screen: 'appA' },
		title: L('The Sukrara watch app', 'La app de reloj Sukrara'),
		href: L('#the-sukrara-watch-app', '#la-app-de-reloj-sukrara'),
		note: L('Glance, chart, logging, alerts.', 'Widget, gráfica, registro, alertas.'),
	},
	heroField: {
		cfg: { screen: 'dfSlot', form: 'band' },
		title: L('The data field', 'El campo de datos'),
		href: L('#the-sukrara-data-field', '#el-campo-de-datos-sukrara'),
		note: L('Glucose during your workouts.', 'Tu glucosa mientras entrenas.'),
	},
} satisfies Record<string, WatchScreen>;

export type ScreenId = keyof typeof SCREENS;
