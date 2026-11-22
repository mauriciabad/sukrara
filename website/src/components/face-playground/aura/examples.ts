// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Sukrara Aura's examples for the website, grouped by what a person deciding to install asks ("does it do X?"). Each is
 * a reference case of cases.json (the design canvases' examples, with the face's newest defaults), named and explained
 * in both languages; a few move with a clock (`live`), so a state's way in and out shows. The faces are drawn by
 * aura.js; nothing here draws or holds a design number.
 */
import type { ExampleGroup, Localized, Values } from '../types.ts';

const L = (en: string, es: string): Localized => ({ en, es });

type Ex = {
	/** Default: the case's. */
	id?: string;
	case: string;
	title: Localized;
	note: Localized;
	/** Inputs over the case's own. */
	cfg?: Values;
	live?: { loop: number; at: (minute: number) => Values };
};
type Group = { id: string; title: Localized; lead: Localized; examples: Ex[] };

/** "10:09" plus m minutes, as aura.js's time input. */
const clock = (hm: string, m: number) => {
	const [h, mm] = hm.split(':').map(Number);
	const t = Math.floor(h * 60 + mm + m);
	return `${Math.floor(t / 60) % 24}:${String(t % 60).padStart(2, '0')}:00`;
};

const GROUPS: Group[] = [
	{
		id: 'glucose',
		title: L('Your glucose, at a glance', 'Tu glucosa, de un vistazo'),
		lead: L(
			'The value in its band’s colour, the last hours beside it, and where you are on the level rail.',
			'El valor en el color de su franja, las últimas horas al lado y dónde estás en el riel de nivel.',
		),
		examples: [
			{ case: 'glucose-in-range', title: L('In range', 'En rango'), note: L('Calm: green, the bead and the last hours.', 'Tranquilo: verde, la bolita y las últimas horas.') },
			{ case: 'fields-green-good', title: L('In range, higher', 'En rango, más alto'), note: L('141–179 in a yellower green.', 'De 141 a 179, en un verde más amarillo.') },
			{ case: 'glucose-rising', title: L('Rising, with the arrow', 'Subiendo, con la flecha'), note: L('Sukrara’s arrow instead of the line, if you prefer.', 'La flecha de Sukrara en lugar de la línea, si lo prefieres.') },
			{ case: 'glucose-falling-fast', title: L('Falling fast', 'Bajando rápido'), note: L('A doubled arrow from −3 mg/dL a minute.', 'Una flecha doble desde −3 mg/dL por minuto.') },
			{ case: 'glucose-lo', title: L('LO', 'LO'), note: L('Below 40, as the sensor says it.', 'Por debajo de 40, como lo dice el sensor.') },
			{ case: 'glucose-hi', title: L('HI', 'HI'), note: L('Above 400, in its own colour.', 'Por encima de 400, en su propio color.') },
			{ case: 'glucose-mmol-widest', title: L('mmol/L', 'mmol/L'), note: L('Your unit, as on your phone.', 'Tu unidad, como en tu móvil.') },
		],
	},
	{
		id: 'trend',
		title: L('The last hours', 'Las últimas horas'),
		lead: L(
			'A small line of your readings next to the value: up to 3 hours, your range kept in view, Sukrara’s trend as a dashed ray.',
			'Una pequeña línea de tus lecturas junto al valor: hasta 3 horas, sin perder de vista tu rango, con la tendencia de Sukrara como un rayo discontinuo.',
		),
		examples: [
			{ case: 'fields-trend-spark', title: L('A steady hour', 'Una hora estable'), note: L('Honestly flat: no scale that exaggerates.', 'Plana de verdad: ninguna escala que exagere.') },
			{ case: 'fields-spark-turning', title: L('Turning', 'Cambiando de rumbo'), note: L('It bottomed at 60 and is coming back: what an arrow can’t say.', 'Tocó fondo en 60 y vuelve: lo que una flecha no puede decir.') },
			{ case: 'fields-spark-panned', title: L('A meal rise', 'Una subida tras comer'), note: L('When the rise outgrows your range, the window follows the hour.', 'Cuando la subida no cabe con tu rango, la ventana sigue a la hora.') },
			{ case: 'fields-spark-overflow', title: L('A fast rise', 'Una subida rápida'), note: L('The newest reading always inside; the start fades out.', 'La lectura más reciente siempre dentro; el principio se desvanece.') },
			{ case: 'fields-spark-gap', title: L('Missed readings', 'Lecturas perdidas'), note: L('Gaps stay gaps: nothing made up.', 'Los huecos quedan como huecos: nada inventado.') },
			{ case: 'fields-spark-wide-range', title: L('Your own range', 'Tu propio rango'), note: L('70–250 here: the band and the limits follow your settings.', 'Aquí 70–250: la franja y los límites siguen tus ajustes.') },
		],
	},
	{
		id: 'act',
		title: L('What to do now', 'Qué hacer ahora'),
		lead: L(
			'Sukrara’s own decision, short, with its icon: the same your phone shows. Never by colour alone.',
			'La decisión de Sukrara, breve, con su icono: la misma que muestra tu móvil. Nunca solo con el color.',
		),
		examples: [
			{ case: 'alerts-low', title: L('Low', 'Baja'), note: L('Carbs.', 'Hidratos.') },
			{ case: 'alerts-very-low', title: L('Very low', 'Muy baja'), note: L('Inverted: it reads with no colour at all.', 'Invertida: se lee sin ningún color.') },
			{ case: 'alerts-low-carbs-taken', title: L('Low, carbs taken', 'Baja, hidratos tomados'), note: L('The low waits 15 min after carbs: a paused bell and the time left.', 'La baja espera 15 min tras los hidratos: una campana en pausa y el tiempo que queda.') },
			{ case: 'alerts-high', title: L('High', 'Alta'), note: L('Insulin.', 'Insulina.') },
			{
				case: 'alerts-high-insulin-taken',
				title: L('High, insulin taken', 'Alta, insulina puesta'),
				note: L('Highs wait an hour after insulin; then, still high, it asks again.', 'Las altas esperan una hora tras la insulina; luego, si sigue alta, vuelve a pedirla.'),
				live: {
					loop: 70,
					at: (m) => (m < 60 ? { time: clock('10:09', m), status: 'quiet', left: 60 - m, last: { items: [{ k: 'rapid', a: '3' }], ago: m } } : { time: clock('10:09', m), status: 'auto', last: { items: [{ k: 'rapid', a: '3' }], ago: m } }),
				},
			},
			{ case: 'alerts-very-high', title: L('Very high', 'Muy alta'), note: L('Further up the rail, in its own colour.', 'Más arriba en el riel, en su propio color.') },
			{ case: 'alerts-extremely-high', title: L('Extremely high', 'Extremadamente alta'), note: L('From 300; a treatment never quiets it.', 'Desde 300; un tratamiento nunca la calla.') },
			{ case: 'alerts-falling-fast-in-range', title: L('Falling fast, in range', 'Bajando rápido, en rango'), note: L('Nothing to do yet: the steep arrow and the trail say it.', 'Aún nada que hacer: lo dicen la flecha empinada y la estela.') },
		],
	},
	{
		id: 'muted',
		title: L('Muted', 'Silenciadas'),
		lead: L('A mute on the phone shows on the watch with its time left, until it ends or you turn it off.', 'Un silencio en el móvil se ve en el reloj con su tiempo restante, hasta que acaba o lo quitas.'),
		examples: [
			{
				case: 'alerts-muted-in-range',
				title: L('Muted, in range', 'Silenciadas, en rango'),
				note: L('The crossed bell, counting down.', 'La campana tachada, con su cuenta atrás.'),
				live: { loop: 90, at: (m) => (m < 80 ? { time: clock('10:09', m), status: 'muted', left: 80 - m } : { time: clock('10:09', m), status: 'auto' }) },
			},
			{ case: 'alerts-muted-low', title: L('Muted, low', 'Silenciadas, baja'), note: L('In the colour of the side it holds.', 'En el color del lado que retiene.') },
			{ case: 'alerts-muted-forever', title: L('Muted until you turn it off', 'Silenciadas hasta que lo quites'), note: L('No end: the word Muted.', 'Sin final: la palabra Silenciadas.') },
		],
	},
	{
		id: 'age',
		title: L('Never an old value as current', 'Nunca un valor antiguo como actual'),
		lead: L(
			'The reading’s age counts on the watch itself, so it goes old by itself, phone or no phone.',
			'La antigüedad de la lectura cuenta en el propio reloj, así que envejece sola, con móvil o sin él.',
		),
		examples: [
			{
				case: 'glucose-late',
				title: L('Late, old, gone', 'Con retraso, antigua, sin lectura'),
				note: L('From 90 s its time shows; from 5 min struck through; from 20 min “No reading since”.', 'Desde 90 s se ve su hora; desde 5 min, tachada; desde 20 min, «Sin lecturas desde».'),
				live: { loop: 40, at: (m) => ({ time: clock('10:06', m), age: m }) },
			},
			{ case: 'glucose-old', title: L('Old', 'Antigua'), note: L('Struck through and grey, the bead hollow.', 'Tachada y gris, la bolita hueca.') },
			{ case: 'glucose-no-readings', title: L('No reading since', 'Sin lecturas desde'), note: L('No value at all, only when the last one was.', 'Ningún valor, solo cuándo fue la última.') },
			{ case: 'glucose-long-gap', title: L('Since yesterday', 'Desde ayer'), note: L('After 12 h the weekday joins the time.', 'Pasadas 12 h, el día de la semana se une a la hora.') },
			{ case: 'glucose-week-old', title: L('Since last week', 'Desde la semana pasada'), note: L('After 6 days, the date.', 'Pasados 6 días, la fecha.') },
		],
	},
	{
		id: 'sensor',
		title: L('Sensor, phone and battery', 'Sensor, móvil y batería'),
		lead: L(
			'When there’s no glucose to show, the face says why in words, and never draws a number that could pass for one.',
			'Cuando no hay glucosa que mostrar, la esfera dice por qué con palabras, y nunca dibuja un número que pueda pasar por uno.',
		),
		examples: [
			{
				case: 'sensor-ending',
				title: L('Sensor ending', 'El sensor termina pronto'),
				note: L('In its last 8 h, a countdown in the first field.', 'En sus últimas 8 h, una cuenta atrás en el primer campo.'),
				live: { loop: 480, at: (m) => ({ time: clock('10:09', m), sensorLeft: 8 - m / 60 }) },
			},
			{ case: 'sensor-ended-sending', title: L('Ended, still sending', 'Terminado, aún envía'), note: L('Past its end with readings still coming.', 'Pasado su final, con lecturas aún llegando.') },
			{ case: 'sensor-ended', title: L('Sensor ended', 'Sensor terminado'), note: L('Time to replace it.', 'Toca cambiarlo.') },
			{
				case: 'sensor-warmup',
				title: L('Warming up', 'Calentando'),
				note: L('A filling disc and the time it’ll be ready; then the first reading.', 'Un disco que se llena y la hora a la que estará listo; luego, la primera lectura.'),
				live: {
					loop: 70,
					at: (m) =>
						m < 55
							? { time: clock('9:51', m), sensor: 'warmup', warm: m / 60 }
							: m < 60
								? { time: clock('9:51', m), sensor: 'almost' }
								: { time: clock('9:51', m), sensor: 'ok', glucose: 124, rate: 0.2, age: 0.5, spark: [124] },
				},
			},
			{ case: 'sensor-none', title: L('Nothing paired', 'Nada emparejado'), note: L('Said plainly; no rail.', 'Dicho tal cual; sin riel.') },
			{ case: 'sensor-phone-lost', title: L('Phone out of reach', 'Móvil fuera de alcance'), note: L('The first field says why the reading is getting old.', 'El primer campo dice por qué la lectura se hace antigua.') },
			{ case: 'sensor-battery-low', title: L('Watch battery low', 'Batería del reloj baja'), note: L('Red under a day, counting hours.', 'En rojo por debajo de un día, contando horas.') },
		],
	},
	{
		id: 'screen',
		title: L('Always-on, night and private', 'Siempre activa, noche y privado'),
		lead: L(
			'The layout never moves between them; only what’s lit changes.',
			'La disposición no se mueve entre ellos; solo cambia lo que se ilumina.',
		),
		examples: [
			{ case: 'modes-aod', title: L('Always-on', 'Siempre activa'), note: L('Outlined time; only what matters lit, the rest kept for when you look.', 'La hora en contorno; solo lo importante encendido, lo demás para cuando miras.') },
			{
				id: 'modes-aod-very-low',
				case: 'modes-aod',
				title: L('Always-on, very low', 'Siempre activa, muy baja'),
				note: L('Dimmed with a margin, so the watch never blanks it: it still reads by its shape.', 'Atenuada con margen, para que el reloj nunca la apague: se sigue leyendo por su forma.'),
				cfg: { glucose: 51, rate: -2.2 },
			},
			{ case: 'modes-night', title: L('Night', 'Noche'), note: L('While the watch is in Do Not Disturb: dimmed, nothing that lights a bedroom.', 'Mientras el reloj está en No molestar: atenuada, nada que ilumine un dormitorio.') },
			{ case: 'modes-night-low', title: L('Night, low', 'Noche, baja'), note: L('A low still has its colour, place and pill.', 'Una baja conserva su color, su sitio y su píldora.') },
			{ case: 'modes-private', title: L('Private', 'Privado'), note: L('No number: the bead alone, a gauge only you can read.', 'Sin número: solo la bolita, un indicador que solo tú sabes leer.') },
			{ case: 'modes-private-held', title: L('Private, held', 'Privado, mantenido pulsado'), note: L('Hold the screen: the number for 5 s.', 'Mantén pulsada la pantalla: el número durante 5 s.') },
			{ case: 'modes-private-act', title: L('Private, act now', 'Privado, hay que actuar'), note: L('It shows itself when you need to act.', 'Se muestra solo cuando tienes que actuar.') },
			{ case: 'modes-private-old', title: L('Private, old', 'Privado, antigua'), note: L('Hidden, but an old reading still says so.', 'Oculta, pero una lectura antigua sigue diciéndolo.') },
		],
	},
	{
		id: 'fields',
		title: L('Your fields and settings', 'Tus campos y ajustes'),
		lead: L(
			'Two fields under the date and two on the bottom curve: Sukrara’s, and anything your watch has (all its complications).',
			'Dos campos bajo la fecha y dos en la curva de abajo: los de Sukrara y lo que tenga tu reloj (todas sus complicaciones).',
		),
		examples: [
			{ case: 'fields-default', title: L('As it comes', 'Tal como viene'), note: L('Steps on top; time in range and the last treatment below.', 'Pasos arriba; tiempo en rango y el último tratamiento abajo.') },
			{ case: 'fields-top-two', title: L('Two on top', 'Dos arriba'), note: L('Heart rate and Body Battery side by side; one alone sits centred.', 'Pulso y Body Battery uno al lado del otro; uno solo queda centrado.') },
			{ case: 'fields-health', title: L('Health', 'Salud'), note: L('Stress and respiration; pulse ox and sleep score.', 'Estrés y respiración; pulsioxímetro y puntuación de sueño.') },
			{ case: 'fields-weather', title: L('Weather', 'Tiempo'), note: L('Rain now, today’s high and low; sunrise and Wednesday’s snow.', 'Lluvia ahora, máxima y mínima de hoy; amanecer y la nieve del miércoles.') },
			{ case: 'fields-training', title: L('Training', 'Entrenamiento'), note: L('A 5K prediction, recovery time, a 10K pace and VO2 max.', 'Predicción de 5K, tiempo de recuperación, ritmo de 10K y VO2 máx.') },
			{ case: 'fields-top-too-long', title: L('Too long for two', 'Demasiado largo para dos'), note: L('The second top field gives way; the first stays, centred.', 'El segundo campo de arriba cede; el primero se queda, centrado.') },
			{ case: 'fields-meal', title: L('After a meal', 'Después de comer'), note: L('Sunset, energy, and insulin and carbs with the time since.', 'Puesta de sol, energía, e insulina e hidratos con el tiempo desde entonces.') },
			{ case: 'fields-heart-rate', title: L('Heart rate', 'Pulso'), note: L('Heart rate on top; steps and your last treatment below.', 'Pulso arriba; pasos y tu último tratamiento abajo.') },
			{ case: 'fields-hiking', title: L('Hiking', 'De excursión'), note: L('A second time zone, altitude and floors.', 'Otra zona horaria, altitud y pisos.') },
			{ case: 'fields-sukrara', title: L('Time in range on top', 'Tiempo en rango arriba'), note: L('Steps and notifications below.', 'Pasos y notificaciones abajo.') },
			{ case: 'fields-tir-unknown', title: L('Too early to tell', 'Aún es pronto'), note: L('Time in range shows -- under a quarter of the day.', 'El tiempo en rango muestra -- antes de un cuarto del día.') },
			{ case: 'fields-battery-both', title: L('Battery: days and percent', 'Batería: días y porcentaje'), note: L('Amber under 2 days.', 'Ámbar por debajo de 2 días.') },
			{ case: 'fields-green-off', title: L('Green in range: off', 'Verde en rango: desactivado'), note: L('White when all is well.', 'Blanco cuando todo va bien.') },
			{ case: 'halo-box', title: L('Halo: box', 'Halo: caja'), note: L('A calmer box behind the numbers.', 'Una caja más tranquila tras los números.') },
			{ case: 'halo-none', title: L('Halo: none', 'Halo: ninguno'), note: L('The dial runs straight behind them.', 'El fondo pasa directamente por detrás.') },
		],
	},
	{
		id: 'dial',
		title: L('The dial follows you', 'El fondo te sigue'),
		lead: L(
			'Grain, contours, waterline, lasers and glow gather at your level, spread the further out of range you are, and calm down as you head back.',
			'Grano, curvas de nivel, línea de agua, láseres y resplandor se concentran en tu nivel, se extienden cuanto más fuera de rango estás y se calman al volver.',
		),
		examples: [
			{ case: 'dial-grain-ideal', title: L('Ideal', 'Ideal'), note: L('Gathered tight in the middle.', 'Concentrado en el centro.') },
			{ case: 'dial-contours-heading-up', title: L('Heading up', 'Hacia arriba'), note: L('Contours lean up with you.', 'Las curvas se inclinan contigo.') },
			{ case: 'dial-waterline-high-rising', title: L('High, rising', 'Alta, subiendo'), note: L('The waterline climbs.', 'La línea de agua sube.') },
			{ case: 'dial-lasers-very-high', title: L('Very high', 'Muy alta'), note: L('Lasers spread wide.', 'Los láseres se abren.') },
			{ case: 'dial-glow-low-falling', title: L('Low, falling', 'Baja, bajando'), note: L('A glow rising from below.', 'Un resplandor que sube desde abajo.') },
			{ case: 'dial-grain-low-recovering', title: L('Low, recovering', 'Baja, recuperándose'), note: L('Softer as you head back.', 'Más suave al volver.') },
			{ case: 'dial-grain-old', title: L('Old reading', 'Lectura antigua'), note: L('Grey: nothing current to follow.', 'Gris: nada actual que seguir.') },
		],
	},
];

/** The groups with each example's full inputs: the face's defaults, its case's, then its own. */
export function examples(defaults: Values, caseCfg: (id: string) => Values | undefined): ExampleGroup[] {
	return GROUPS.map((g) => ({
		...g,
		examples: g.examples.map((e) => {
			const cfg = caseCfg(e.case);
			if (!cfg) throw new Error(`examples: no case ${e.case} in cases.json`);
			const values = { ...defaults, ...cfg, ...e.cfg };
			return { id: e.id ?? e.case, title: e.title, note: e.note, values, live: e.live };
		}),
	}));
}
