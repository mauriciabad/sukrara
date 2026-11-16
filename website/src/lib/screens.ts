// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Every app picture the site shows, by name, with its alt text in both languages. Pictures come from where they're
 * already kept, so they follow the apps:
 *   - the Android snapshot goldens (apps/android/<module>/src/test/snapshots): drawn by the app's own code from
 *     synthetic data, light and dark, and updated with the code;
 *   - Mac renders by `sukrara-snap` (website/src/assets/mac: how to render them again is in website/README.md);
 *   - Sukrara Aura renders by its reference implementation (website/src/assets/garmin, the same README);
 *   - report pictures in docs/img.
 * A golden that's renamed or deleted fails the build here, never a broken picture on the site.
 */
import type { ImageMetadata } from 'astro';

// The card with room for what it draws past its edges (the value, the crown, the laurels).
import cardDark from '@repo/apps/android/core/graph/src/test/snapshots/panel-in-range-dark.png';
import cardLight from '@repo/apps/android/core/graph/src/test/snapshots/panel-in-range-light.png';
import lockscreenDark from '@repo/apps/android/widgets/src/test/snapshots/widget-lock-fresh-dark.png';
import lockscreenLight from '@repo/apps/android/widgets/src/test/snapshots/widget-lock-fresh-light.png';
import widgetDark from '@repo/apps/android/widgets/src/test/snapshots/widget-combined-4x2-fresh-dark.png';
import widgetLight from '@repo/apps/android/widgets/src/test/snapshots/widget-combined-4x2-fresh-light.png';
import treatmentsDark from '@repo/apps/android/feature/home/src/test/snapshots/treatment-list-dark.png';
import treatmentsLight from '@repo/apps/android/feature/home/src/test/snapshots/treatment-list-light.png';
import shareDark from '@repo/apps/android/feature/sharing/src/test/snapshots/share-screen-new-dark.png';
import shareLight from '@repo/apps/android/feature/sharing/src/test/snapshots/share-screen-new-light.png';
import lockTreatmentDark from '@repo/apps/android/feature/treatments/src/test/snapshots/treatment-dialog-lock-dark.png';
import lockTreatmentLight from '@repo/apps/android/feature/treatments/src/test/snapshots/treatment-dialog-lock-light.png';
import home from '@repo/docs/img/home/home.png';
import alert from '@repo/docs/img/notification/alert-actions.png';
import statusBar from '@repo/docs/img/statusbar-chip.png';
import macWidgetDark from '@repo/docs/img/16/mac-widget-medium-dark.png';
import macWidgetLight from '@repo/docs/img/16/mac-widget-medium-light.png';
import macPanelDark from '../assets/mac/en/panel-dark.png';
import macPanelLight from '../assets/mac/en/panel-light.png';
import macPanelDarkEs from '../assets/mac/es/panel-dark.png';
import macPanelLightEs from '../assets/mac/es/panel-light.png';
import macReportDark from '../assets/mac/en/report-dark.png';
import macReportLight from '../assets/mac/en/report-light.png';
import macReportDarkEs from '../assets/mac/es/report-dark.png';
import macReportLightEs from '../assets/mac/es/report-light.png';
import macTreatmentDark from '../assets/mac/en/treatment-add-dark.png';
import macTreatmentLight from '../assets/mac/en/treatment-add-light.png';
import macTreatmentDarkEs from '../assets/mac/es/treatment-add-dark.png';
import macTreatmentLightEs from '../assets/mac/es/treatment-add-light.png';
import garminHero from '../assets/garmin/en/hero.png';
import garminHeroEs from '../assets/garmin/es/hero.png';
import garminHigh from '../assets/garmin/en/high.png';
import garminHighEs from '../assets/garmin/es/high.png';
import garminLow from '../assets/garmin/en/low.png';
import garminLowEs from '../assets/garmin/es/low.png';
import garminVeryHigh from '../assets/garmin/en/very-high.png';
import garminVeryHighEs from '../assets/garmin/es/very-high.png';
import garminSparkline from '../assets/garmin/en/sparkline.png';
import garminSparklineEs from '../assets/garmin/es/sparkline.png';
import garminPrivate from '../assets/garmin/en/private.png';
import garminPrivateEs from '../assets/garmin/es/private.png';
import garminOld from '../assets/garmin/en/old.png';
import garminOldEs from '../assets/garmin/es/old.png';
import garminAlwaysOn from '../assets/garmin/en/always-on.png';
import garminAlwaysOnEs from '../assets/garmin/es/always-on.png';
import garminNight from '../assets/garmin/en/night.png';
import garminNightEs from '../assets/garmin/es/night.png';

type Lang = 'en' | 'es';
interface Pictures {
	light: ImageMetadata;
	/** The dark theme's picture; the light one in both themes when there's none. */
	dark?: ImageMetadata;
}
export interface Screen {
	/** Per language; Spanish falls back to English. */
	pictures: Partial<Record<Lang, Pictures>> & { en: Pictures };
	alt: Record<Lang, string>;
	/** The picture's plain background, per theme, when it's drawn edge to edge: shown as a margin around it. */
	ground?: { light: string; dark: string };
	/** A round watch screen: shown in a plain dark ring (no brand's bezel), the same in both themes. */
	round?: true;
}

export const SCREENS = {
	card: {
		pictures: { en: { light: cardLight, dark: cardDark } },
		ground: { light: '#ffffff', dark: '#1c1c1e' },
		alt: {
			en: 'The glucose card: 125 mg/dL with its trend arrow, 40 minutes since 4.5 units of insulin and 45 g of carbs, a full day in range with its crown, and the 12-hour chart with the time spent at each level.',
			es: 'La tarjeta de glucosa: 125 mg/dL con su flecha de tendencia, 40 minutos desde 4,5 unidades de insulina y 45 g de carbohidratos, un día entero en rango con su corona y la gráfica de 12 horas con el tiempo pasado en cada nivel.',
		},
	},
	widget: {
		pictures: { en: { light: widgetLight, dark: widgetDark } },
		alt: {
			en: 'A home-screen widget: 140 mg/dL, 72% of the day in range, and the night’s chart.',
			es: 'Un widget de la pantalla de inicio: 140 mg/dL, un 72 % del día en rango y la gráfica de la noche.',
		},
	},
	treatments: {
		pictures: { en: { light: treatmentsLight, dark: treatmentsDark } },
		alt: {
			en: 'The treatments list: rapid and long insulin, carbs and a note, each with the time, the glucose and trend then, and whether it reached LibreView.',
			es: 'La lista de tratamientos: insulina rápida y lenta, carbohidratos y una nota, cada uno con la hora, la glucosa y la tendencia de ese momento y si llegó a LibreView.',
		},
	},
	share: {
		pictures: { en: { light: shareLight, dark: shareDark } },
		alt: {
			en: 'Share with someone: an invite for one person, who asks to follow; you allow them. They see your glucose and trend, alerts, treatments and the sensor’s time left, end-to-end encrypted.',
			es: 'Compartir con alguien: una invitación para una persona, que pide seguirte; tú la aceptas. Ve tu glucosa y tendencia, las alertas, los tratamientos y el tiempo que le queda al sensor, cifrado de extremo a extremo.',
		},
	},
	lockTreatment: {
		pictures: { en: { light: lockTreatmentLight, dark: lockTreatmentDark } },
		alt: {
			en: 'Adding a treatment from the lock screen: rapid insulin, long insulin, carbs and a note.',
			es: 'Añadir un tratamiento desde la pantalla de bloqueo: insulina rápida, insulina lenta, carbohidratos y una nota.',
		},
	},
	home: {
		pictures: { en: { light: home } },
		alt: {
			en: 'Home on the phone: the wearer with six followers, a banner to share with the clinic through LibreView or Tidepool, a backup reminder and the glucose card with Add treatment.',
			es: 'El inicio en el móvil: quien lleva el sensor, con seis seguidores, un aviso para compartir con la consulta por LibreView o Tidepool, un recordatorio de copia de seguridad y la tarjeta de glucosa con Añadir tratamiento.',
		},
	},
	lockscreen: {
		pictures: { en: { light: lockscreenLight, dark: lockscreenDark } },
		alt: {
			en: 'The lock screen widget: 140 mg/dL, 72% of the day in range and the night’s chart, around the fingerprint sensor.',
			es: 'El widget de la pantalla de bloqueo: 140 mg/dL, un 72 % del día en rango y la gráfica de la noche, alrededor del sensor de huellas.',
		},
	},
	alert: {
		pictures: { en: { light: alert } },
		alt: {
			en: 'A low glucose alert at 62 mg/dL falling, with buttons to mute it for 15 minutes to 2 hours, log carbs, or be reminded less often.',
			es: 'Una alerta de glucosa baja a 62 mg/dL bajando, con botones para silenciarla de 15 minutos a 2 horas, apuntar carbohidratos o que avise menos a menudo.',
		},
	},
	statusBar: {
		pictures: { en: { light: statusBar } },
		alt: {
			en: 'The status bar: Sukrara’s chip, 138 and rising fast, next to the clock.',
			es: 'La barra de estado: el chip de Sukrara, 138 y subiendo rápido, junto a la hora.',
		},
	},
	macPanel: {
		pictures: {
			en: { light: macPanelLight, dark: macPanelDark },
			es: { light: macPanelLightEs, dark: macPanelDarkEs },
		},
		alt: {
			en: 'Sukrara for Mac: 112 and a falling arrow in the menu bar; its panel with the same card as the phone, Mute All Alerts, Nearby over Bluetooth, and Add Treatment, Treatments, Glucose Report and Settings.',
			es: 'Sukrara para Mac: 112 y una flecha hacia abajo en la barra de menús; su panel con la misma tarjeta que el móvil, Silenciar todas las alertas, Cerca por Bluetooth, y Añadir tratamiento, Tratamientos, Informe de glucosa y Ajustes.',
		},
	},
	macReport: {
		pictures: {
			en: { light: macReportLight, dark: macReportDark },
			es: { light: macReportLightEs, dark: macReportDarkEs },
		},
		alt: {
			en: 'The glucose report on the Mac: time in ranges over 14 days, average glucose, GMI and variability, each against the international consensus goal, and Export CSV.',
			es: 'El informe de glucosa en el Mac: tiempo en rangos durante 14 días, glucosa media, GMI y variabilidad, cada uno frente al objetivo del consenso internacional, y Exportar CSV.',
		},
	},
	macTreatment: {
		pictures: {
			en: { light: macTreatmentLight, dark: macTreatmentDark },
			es: { light: macTreatmentLightEs, dark: macTreatmentDarkEs },
		},
		alt: {
			en: 'Add Treatment on the Mac: rapid and long insulin, carbs and a note at once, now or earlier.',
			es: 'Añadir tratamiento en el Mac: insulina rápida y lenta, carbohidratos y una nota a la vez, ahora o antes.',
		},
	},
	macWidget: {
		pictures: { en: { light: macWidgetLight, dark: macWidgetDark } },
		alt: {
			en: 'A Sukrara widget on the Mac desktop with the value, trend and chart.',
			es: 'Un widget de Sukrara en el escritorio del Mac con el valor, la tendencia y la gráfica.',
		},
	},
	garmin: {
		pictures: { en: { light: garminHero }, es: { light: garminHeroEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura on a round watch screen: Monday 16 November, 14°, 10:09, and 112 in green with a steady arrow, in range; the level rail’s bead in the green band, 9 days of battery, 6,420 steps and 2 units of insulin 1 h 20 min ago.',
			es: 'Sukrara Aura en una pantalla de reloj redonda: lunes 16 de noviembre, 14°, 10:09 y 112 en verde con una flecha estable, en rango; la bolita del riel de nivel en la franja verde, 9 días de batería, 6.420 pasos y 2 unidades de insulina hace 1 h 20 min.',
		},
	},
	garminHigh: {
		pictures: { en: { light: garminHigh }, es: { light: garminHighEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura, high: 236 in amber, rising, with the grain dial spread wide around it; alerts quiet for 25 more minutes after 4.5 units of insulin and 45 g of carbs 35 minutes ago.',
			es: 'Sukrara Aura, alta: 236 en ámbar, subiendo, con el grano extendido a su alrededor; alertas en calma 25 minutos más tras 4,5 unidades de insulina y 45 g de hidratos hace 35 minutos.',
		},
	},
	garminLow: {
		pictures: { en: { light: garminLow }, es: { light: garminLowEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura, low: 62 in red, falling, with a red glow rising from below and the Carbs pill under it.',
			es: 'Sukrara Aura, baja: 62 en rojo, bajando, con un brillo rojo desde abajo y la píldora Hidratos debajo.',
		},
	},
	garminVeryHigh: {
		pictures: { en: { light: garminVeryHigh }, es: { light: garminVeryHighEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura, very high: 320 in magenta, steady, magenta lasers across the face and the Insulin pill under it.',
			es: 'Sukrara Aura, muy alta: 320 en magenta, estable, láseres magenta cruzando la esfera y la píldora Insulina debajo.',
		},
	},
	garminSparkline: {
		pictures: { en: { light: garminSparkline }, es: { light: garminSparklineEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura with the last hour instead of the arrow: 77, turning up after a dip, as a small line next to the value.',
			es: 'Sukrara Aura con la última hora en vez de la flecha: 77, subiendo tras una bajada, como una pequeña línea junto al valor.',
		},
	},
	garminPrivate: {
		pictures: { en: { light: garminPrivate }, es: { light: garminPrivateEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura in private mode while you hold the screen: 142 with a shrinking neon line under it, before it hides again.',
			es: 'Sukrara Aura en modo privado mientras mantienes la pantalla: 142 con una línea neón que se acorta debajo, antes de ocultarse otra vez.',
		},
	},
	garminOld: {
		pictures: { en: { light: garminOld }, es: { light: garminOldEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura with an old reading: 104 struck through in grey, a hollow ring instead of the arrow, and the reading’s time, 10:00.',
			es: 'Sukrara Aura con una lectura antigua: 104 tachado en gris, un anillo hueco en vez de la flecha y la hora de la lectura, 10:00.',
		},
	},
	garminAlwaysOn: {
		pictures: { en: { light: garminAlwaysOn }, es: { light: garminAlwaysOnEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura always-on: the time outlined and 112 in green on black, without fields or dial.',
			es: 'Sukrara Aura siempre activa: la hora perfilada y 112 en verde sobre negro, sin campos ni fondo.',
		},
	},
	garminNight: {
		pictures: { en: { light: garminNight }, es: { light: garminNightEs } },
		round: true,
		alt: {
			en: 'Sukrara Aura at night: the same face in dimmer colours, 118 steady and in range.',
			es: 'Sukrara Aura de noche: la misma esfera en colores más tenues, 118 estable y en rango.',
		},
	},
} satisfies Record<string, Screen>;

export type ScreenName = keyof typeof SCREENS;
