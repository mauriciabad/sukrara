// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The apps and services Sukrara works with, by name, with their own icon: the same icons the Android app shows on their
 * rows (core/designsystem, docs/10-showcase.md "Service and app icons"), plus AAPS's from its repository. Where each came from is in src/assets/logos/README.md.
 */
import type { ImageMetadata } from 'astro';
import xdrip from '@repo/apps/android/core/designsystem/src/main/res/drawable-xxxhdpi/ds_ic_app_xdrip.png';
import aaps from '../assets/logos/aaps.svg?url';
import healthConnect from '../assets/logos/health-connect.png';
import juggluco from '../assets/logos/juggluco.png';
import libreview from '../assets/logos/libreview.svg?url';
import nightscout from '../assets/logos/nightscout.svg?url';
import tidepool from '../assets/logos/tidepool.svg?url';

export interface App {
	name: string;
	/** A raster icon (optimised like the pictures) or an SVG file's URL. */
	icon: ImageMetadata | string;
	href: string;
}

export const APPS = {
	libreview: { name: 'LibreView', icon: libreview, href: 'https://www.libreview.com/' },
	tidepool: { name: 'Tidepool', icon: tidepool, href: 'https://www.tidepool.org/' },
	nightscout: { name: 'Nightscout', icon: nightscout, href: 'https://nightscout.github.io/' },
	healthConnect: { name: 'Health Connect', icon: healthConnect, href: 'https://health.google/health-connect-android/' },
	xdrip: { name: 'xDrip+', icon: xdrip, href: 'https://github.com/NightscoutFoundation/xDrip' },
	aaps: { name: 'AAPS', icon: aaps, href: 'https://github.com/nightscout/AndroidAPS' },
	juggluco: { name: 'Juggluco', icon: juggluco, href: 'https://github.com/j-kaltes/Juggluco' },
} satisfies Record<string, App>;

export type AppName = keyof typeof APPS;
