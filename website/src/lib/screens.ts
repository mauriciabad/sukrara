// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Every app picture the site shows, by name, with its alt text in both languages. Pictures come from where they're
 * already kept, so they follow the apps:
 *   - the Android snapshot goldens (apps/android/<module>/src/test/snapshots): drawn by the app's own code from
 *     synthetic data, light and dark, and updated with the code;
 *   - Mac renders by `sukrara-snap` (website/src/assets/mac: how to render them again is in website/README.md);
 *   - report pictures in docs/img.
 * A golden that's renamed or deleted fails the build here, never a broken picture on the site.
 */
import type { ImageMetadata } from 'astro';

import cardDark from '@repo/apps/android/core/graph/src/test/snapshots/card-in-range-dark.png';
import cardLight from '@repo/apps/android/core/graph/src/test/snapshots/card-in-range-light.png';
import notificationDark from '@repo/apps/android/monitor/src/test/snapshots/notification-card-fresh-dark.png';
import notificationLight from '@repo/apps/android/monitor/src/test/snapshots/notification-card-fresh-light.png';
import widgetDark from '@repo/apps/android/widgets/src/test/snapshots/widget-combined-4x2-fresh-dark.png';
import widgetLight from '@repo/apps/android/widgets/src/test/snapshots/widget-combined-4x2-fresh-light.png';
import treatmentsDark from '@repo/apps/android/feature/home/src/test/snapshots/treatment-list-dark.png';
import treatmentsLight from '@repo/apps/android/feature/home/src/test/snapshots/treatment-list-light.png';
import shareDark from '@repo/apps/android/feature/sharing/src/test/snapshots/share-screen-new-dark.png';
import shareLight from '@repo/apps/android/feature/sharing/src/test/snapshots/share-screen-new-light.png';
import lockTreatmentDark from '@repo/apps/android/feature/treatments/src/test/snapshots/treatment-dialog-lock-dark.png';
import lockTreatmentLight from '@repo/apps/android/feature/treatments/src/test/snapshots/treatment-dialog-lock-light.png';
import home from '@repo/docs/img/home/home.png';
import lockscreen from '@repo/docs/img/lockscreen/lockscreen.png';
import alert from '@repo/docs/img/notification/alert-actions.png';
import statusBar from '@repo/docs/img/notification/v7-status-bar-and-notification-android37.png';
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
}

export const SCREENS = {
	card: {
		pictures: { en: { light: cardLight, dark: cardDark } },
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
	notification: {
		pictures: { en: { light: notificationLight, dark: notificationDark } },
		alt: {
			en: 'The glucose notification: the value in large type with the time in range, and its chart when expanded.',
			es: 'La notificación de glucosa: el valor en grande con el tiempo en rango, y su gráfica al desplegarla.',
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
		pictures: { en: { light: lockscreen } },
		alt: {
			en: 'The glucose card on the lock screen, around the fingerprint icon (needs root and LSPosed).',
			es: 'La tarjeta de glucosa en la pantalla de bloqueo, alrededor del icono de la huella (necesita root y LSPosed).',
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
			en: 'Settings › Status bar & notification: glucose as a chip, as the notification icon (value and trend, value, trend or Sukrara’s icon), as status bar text with root, or nothing.',
			es: 'Ajustes › Barra de estado y notificación: la glucosa como chip, como icono de la notificación (valor y tendencia, valor, tendencia o el icono de Sukrara), como texto en la barra de estado con root, o nada.',
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
} satisfies Record<string, Screen>;

export type ScreenName = keyof typeof SCREENS;
