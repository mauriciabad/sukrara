// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/** The playground's own words (the face's inputs and presets name themselves in their adapter). */
import type { Lang } from './types.ts';

const WORDS = {
	en: {
		presets: 'Start from',
		custom: 'Your own',
		reset: 'Reset',
		scenario: 'Scenario',
		play: 'Speed up time',
		pause: 'Pause',
		again: 'Play again',
		speed: 'Speed',
		speedName: (x: number) => (x === 1 ? 'real time' : `${x} times faster`),
		timeline: 'Time in the scenario',
		at: (clock: string, min: number) => `${clock} · minute ${Math.floor(min)}`,
		face: 'The watch face',
		inputs: 'What the watch knows',
	},
	es: {
		presets: 'Empezar desde',
		custom: 'Personalizado',
		reset: 'Restablecer',
		scenario: 'Escenario',
		play: 'Acelerar el tiempo',
		pause: 'Pausa',
		again: 'Volver a empezar',
		speed: 'Velocidad',
		speedName: (x: number) => (x === 1 ? 'tiempo real' : `${x} veces más rápido`),
		timeline: 'Tiempo en el escenario',
		at: (clock: string, min: number) => `${clock} · minuto ${Math.floor(min)}`,
		face: 'La esfera',
		inputs: 'Lo que sabe el reloj',
	},
} satisfies Record<Lang, unknown>;

export const words = (lang: Lang) => WORDS[lang];
