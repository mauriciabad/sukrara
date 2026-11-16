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
		tour: 'Sukrara Aura through a made-up day, 600 times faster',
		parts: 'What each part is',
		partsHint: 'Point at a name or at the face to see that part alone.',
		partsHintTouch: 'Tap a name or the face to see that part alone.',
		tryIt: 'Try its settings',
		notNow: 'not on screen now',
		looks: 'Pick a dial',
		playDay: 'Play the day',
		pauseDay: 'Pause the day',
		dayTime: 'Time of the day',
		moving: 'moves',
		examples: (title: string) => `Examples: ${title}`,
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
		tour: 'Sukrara Aura a lo largo de un día inventado, 600 veces más rápido',
		parts: 'Qué es cada parte',
		partsHint: 'Señala un nombre o la esfera para ver esa parte sola.',
		partsHintTouch: 'Toca un nombre o la esfera para ver esa parte sola.',
		tryIt: 'Prueba sus ajustes',
		notNow: 'ahora no se ve',
		looks: 'Elige un fondo',
		playDay: 'Reproducir el día',
		pauseDay: 'Pausar el día',
		dayTime: 'Hora del día',
		moving: 'se mueve',
		examples: (title: string) => `Ejemplos: ${title}`,
	},
} satisfies Record<Lang, unknown>;

export const words = (lang: Lang) => WORDS[lang];
