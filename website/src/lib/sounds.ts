// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The sound test page's data. The alerts' names and default thresholds come from the contracts every app reads
 * (contracts/alerts/copy.json, contracts/settings/settings.json), the repeats and moods from contracts/alerts/SPEC.md
 * §1, the sounds from website/public/sounds (m4a copies of contracts/alerts/sounds, which every browser plays). The
 * vibrations are drawn to scale from Android's AlertHaptics.kt (apps/android/monitor): a primitive per shape, its
 * height the strength.
 */
import copy from '../../../contracts/alerts/copy.json' with { type: 'json' };
import settings from '../../../contracts/settings/settings.json' with { type: 'json' };

type Lang = 'en' | 'es';
type Text = Record<Lang, string>;

export const PACKS: { id: string; name: string; note: Text }[] = [
	{ id: 'arcade', name: 'Arcade', note: { en: '8-bit pulse waves', es: 'ondas de pulso de 8 bits' } },
	{ id: 'kart', name: 'Kart', note: { en: 'racing brass and engines', es: 'metales y motores de carreras' } },
	{ id: 'quest', name: 'Quest', note: { en: 'harp, flute and bells (the default)', es: 'arpa, flauta y campanas (el de serie)' } },
	{ id: 'soft', name: 'Soft', note: { en: 'marimba and glass', es: 'marimba y cristal' } },
];

const n = settings.numbers;
const rate = n.thresholdRate.default.toFixed(1);

/** [shape, SVG points in a 720 × 34 box, strength 0–1] per vibration, as AlertHaptics.kt plays them. */
const VIBRATIONS: Record<string, { ms: number; shapes: [string, string, number][] }> = {
	very_low: { ms: 2200, shapes: [['quick fall', '0.0,32 0.0,4.0 20.0,32', 1], ['quick fall', '56.0,32 56.0,12.4 76.0,32', 0.7], ['quick fall', '128.0,32 128.0,18.0 148.0,32', 0.5], ['thud', '220.0,32 220.0,4.0 280.0,4.0 280.0,32', 1], ['thud', '380.0,32 380.0,4.0 440.0,4.0 440.0,32', 1]] },
	low: { ms: 980, shapes: [['quick fall', '0.0,32 0.0,6.8 20.0,32', 0.9], ['quick fall', '60.0,32 60.0,9.6 80.0,32', 0.8], ['thud', '136.0,32 136.0,6.8 196.0,6.8 196.0,32', 0.9]] },
	high: { ms: 715, shapes: [['click', '0.0,32 0.0,12.4 5.8,12.4 5.8,32', 0.7], ['click', '33.8,32 33.8,11.0 39.6,11.0 39.6,32', 0.75], ['click', '59.6,32 59.6,9.6 65.4,9.6 65.4,32', 0.8], ['click', '79.4,32 79.4,8.2 85.2,8.2 85.2,32', 0.85], ['click', '95.2,32 95.2,6.8 101.0,6.8 101.0,32', 0.9], ['quick rise', '113.0,32 143.0,4.0 143.0,32', 1]] },
	very_high: { ms: 1411, shapes: [['thud', '0.0,32 0.0,9.6 60.0,9.6 60.0,32', 0.8], ['click', '78.0,32 78.0,4.0 83.8,4.0 83.8,32', 1], ['thud', '127.8,32 127.8,9.6 187.8,9.6 187.8,32', 0.8], ['click', '205.8,32 205.8,4.0 211.6,4.0 211.6,32', 1], ['spin', '255.6,32 255.6,4.0 282.2,4.0 282.2,32', 1]] },
	extremely_high: { ms: 2481, shapes: [['thud', '0.0,32 0.0,4.0 60.0,4.0 60.0,32', 1], ['thud', '84.0,32 84.0,4.0 144.0,4.0 144.0,32', 1], ['click', '162.0,32 162.0,4.0 167.8,4.0 167.8,32', 1], ['thud', '207.8,32 207.8,4.0 267.8,4.0 267.8,32', 1], ['thud', '291.8,32 291.8,4.0 351.8,4.0 351.8,32', 1], ['click', '369.8,32 369.8,4.0 375.6,4.0 375.6,32', 1], ['spin', '419.6,32 419.6,4.0 446.2,4.0 446.2,32', 1], ['quick rise', '466.2,32 496.2,4.0 496.2,32', 1]] },
	falling_fast: { ms: 232, shapes: [['quick fall', '0.0,32 0.0,4.0 20.0,32', 1], ['low tick', '44.0,32 44.0,4.0 46.8,4.0 46.8,32', 1]] },
	rising_fast: { ms: 565, shapes: [['slow rise', '0.0,32 100.0,4.0 100.0,32', 1], ['tick', '112.0,32 112.0,4.0 114.8,4.0 114.8,32', 1]] },
	no_data: { ms: 342, shapes: [['tick', '0.0,32 0.0,4.0 2.8,4.0 2.8,32', 1], ['tick', '33.0,32 33.0,15.2 35.8,15.2 35.8,32', 0.6], ['low tick', '66.0,32 66.0,20.8 68.8,20.8 68.8,32', 0.4]] },
};

const RULES: Record<string, { rule: Text; repeat: number; mood: Text }> = {
	very_low: {
		rule: { en: `≤ ${n.thresholdVeryLow.default} mg/dL`, es: `≤ ${n.thresholdVeryLow.default} mg/dL` },
		repeat: 5,
		mood: { en: 'Game over: longer, slows down, ends low', es: 'Fin de la partida: más larga, se frena y acaba grave' },
	},
	low: {
		rule: { en: `< ${n.thresholdLow.default} mg/dL`, es: `< ${n.thresholdLow.default} mg/dL` },
		repeat: 5,
		mood: { en: 'Lost a life: short and falling', es: 'Una vida menos: corta y bajando' },
	},
	high: {
		rule: { en: `≥ ${n.thresholdHigh.default} mg/dL`, es: `≥ ${n.thresholdHigh.default} mg/dL` },
		repeat: 15,
		mood: { en: 'Final lap: a riff, then faster and higher', es: 'Última vuelta: un riff, luego más rápido y más agudo' },
	},
	very_high: {
		rule: { en: `≥ ${n.thresholdVeryHigh.default} mg/dL`, es: `≥ ${n.thresholdVeryHigh.default} mg/dL` },
		repeat: 10,
		mood: { en: 'Boss incoming: a two-note warning, rising', es: 'Llega el jefe: un aviso de dos notas, subiendo' },
	},
	extremely_high: {
		rule: { en: `≥ ${n.thresholdExtremelyHigh.default} mg/dL`, es: `≥ ${n.thresholdExtremelyHigh.default} mg/dL` },
		repeat: 5,
		mood: {
			en: 'Final boss: the warning, longer and climbing, then a held cry',
			es: 'Jefe final: el aviso, más largo y subiendo, y un grito sostenido',
		},
	},
	falling_fast: {
		rule: { en: `−${rate} mg/dL/min · off by default`, es: `−${rate} mg/dL/min · desactivada de serie` },
		repeat: 30,
		mood: { en: 'A quick sweep down', es: 'Un barrido rápido hacia abajo' },
	},
	rising_fast: {
		rule: { en: `+${rate} mg/dL/min · off by default`, es: `+${rate} mg/dL/min · desactivada de serie` },
		repeat: 30,
		mood: { en: 'A quick sweep up', es: 'Un barrido rápido hacia arriba' },
	},
	no_data: {
		rule: { en: `${n.thresholdNoData.default} min without data`, es: `${n.thresholdNoData.default} min sin datos` },
		repeat: 60,
		mood: { en: 'Connection lost: two blips', es: 'Conexión perdida: dos pitidos' },
	},
};

/** The shapes' names (Android's haptic primitives) in Spanish. */
const SHAPES_ES: Record<string, string> = {
	'quick fall': 'caída rápida',
	thud: 'golpe',
	click: 'clic',
	'quick rise': 'subida rápida',
	spin: 'giro',
	'low tick': 'tic grave',
	'slow rise': 'subida lenta',
	tick: 'tic',
};

function headline(kind: string): Text {
	const c = copy.cases.find((c) => c.state === 'headline' && 'kind' in c && c.kind === kind && c.treated === 0);
	if (!c) throw new Error(`contracts/alerts/copy.json has no headline for ${kind}`);
	return { en: c.en, es: c.es };
}

export const KINDS = Object.entries(RULES).map(([id, r]) => ({
	id,
	name: headline(id),
	side: /low|falling/.test(id) ? 'low' : /high|rising/.test(id) ? 'high' : 'none',
	rule: (lang: Lang) => `${r.rule[lang]} · ${lang === 'es' ? 'cada' : 'every'} ${r.repeat} min`,
	mood: r.mood,
	vibration: VIBRATIONS[id],
	shapeName: (shape: string, lang: Lang) => (lang === 'es' ? (SHAPES_ES[shape] ?? shape) : shape),
}));
