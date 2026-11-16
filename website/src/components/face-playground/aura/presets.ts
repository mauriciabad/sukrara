// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Which of the face's reference cases (contracts/watchface/aura/cases.json) the playground offers as presets (in
 * cases.json's order), with their names in Spanish (the English name is the case's own title). The inputs come from the case itself,
 * so a changed case shows here unchanged. Left out: the dial grid (the Dial control does that), the colour checks and
 * the Spanish copies (the page's language sets the face's).
 */

export const GROUPS = {
	glucose: { en: 'Glucose', es: 'Glucosa' },
	alerts: { en: 'Alerts', es: 'Alertas' },
	sensor: { en: 'Sensor and phone', es: 'Sensor y móvil' },
	modes: { en: 'Screen', es: 'Pantalla' },
	fields: { en: 'Fields and settings', es: 'Campos y ajustes' },
	halo: { en: 'Halo', es: 'Halo' },
} as const;

/** Case id → its name in Spanish. */
export const PRESETS_ES: Record<string, string> = {
	'glucose-in-range': 'En rango',
	'glucose-rising': 'Subiendo',
	'glucose-falling-fast': 'Bajando rápido, en rango',
	'glucose-late': 'Con retraso',
	'glucose-old': 'Antigua',
	'glucose-no-readings': 'Sin lecturas',
	'glucose-long-gap': 'Muchas horas sin lecturas',
	'glucose-another-day': 'Sin lecturas desde ayer',
	'glucose-week-old': 'Sin lecturas desde hace una semana',
	'glucose-lo': 'LO',
	'glucose-hi': 'HI',
	'glucose-mmol-widest': 'mmol/L en su valor más ancho',
	'alerts-low': 'Baja',
	'alerts-very-low': 'Muy baja',
	'alerts-low-carbs-taken': 'Baja, hidratos tomados',
	'alerts-muted-low': 'Bajas silenciadas',
	'alerts-muted-in-range': 'Silenciadas, en rango',
	'alerts-muted-forever': 'Silenciadas hasta desactivarlo',
	'alerts-muted-long': 'Silenciadas muchas horas',
	'alerts-high': 'Alta',
	'alerts-high-insulin-taken': 'Alta, insulina puesta',
	'alerts-quiet-ended': 'Se acabó la espera',
	'alerts-very-high': 'Muy alta',
	'alerts-extremely-high': 'Extremadamente alta',
	'alerts-falling-fast-in-range': 'Bajando rápido, aún en rango',
	'sensor-warmup': 'Calentando',
	'sensor-almost': 'Casi listo',
	'sensor-ending': 'El sensor termina pronto',
	'sensor-ended-sending': 'Terminado, aún envía',
	'sensor-ended': 'Sensor terminado',
	'sensor-none': 'Nada emparejado',
	'sensor-phone-lost': 'Móvil fuera de alcance',
	'sensor-battery-low': 'Batería baja',
	'modes-aod': 'Siempre activa',
	'modes-night': 'Noche',
	'modes-night-low': 'Noche, baja',
	'modes-private': 'Privado',
	'modes-private-held': 'Privado, mantenido pulsado',
	'modes-private-act': 'Privado, hay que actuar',
	'modes-private-old': 'Privado, lectura antigua',
	'fields-meal': 'Después de comer',
	'fields-no-treatment': 'Sin tratamientos hoy',
	'fields-hiking': 'De excursión',
	'fields-sukrara': 'Los de Sukrara',
	'fields-long-acting': 'Insulina lenta',
	'fields-green-off': 'Verde en rango: desactivado',
	'fields-green-good': 'Verde en rango: bien',
	'fields-battery-both': 'Batería: días y porcentaje',
	'fields-trend-spark': 'Tendencia: gráfica',
	'fields-spark-turning': 'Gráfica, cambiando de rumbo',
	'fields-spark-gap': 'Gráfica con lecturas perdidas',
	'fields-spark-panned': 'Gráfica, desplazada',
	'fields-spark-overflow': 'Gráfica, desbordada',
	'fields-seconds-off': 'Barrido de segundos desactivado',
	'halo-box': 'Halo: caja',
	'halo-none': 'Halo: ninguno',
};
