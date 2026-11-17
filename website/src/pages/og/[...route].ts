// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// The link preview of every page (/og/<page id>.png, 1200 × 630): its title and description on the brand gradient,
// with the app icon. route-data.ts points each page's og:image here.
import { getCollection } from 'astro:content';
import { OGImageRoute } from 'astro-og-canvas';

const entries = await getCollection('docs');
const pages = Object.fromEntries(entries.map(({ id, data }) => [id || 'index', data]));

export const { getStaticPaths, GET } = await OGImageRoute({
	param: 'route',
	pages,
	getImageOptions: (_path, page: (typeof pages)[string]) => ({
		title: page.title,
		description: page.description,
		logo: { path: '../assets/brand/sukrara/web/apple-touch-icon.png', size: [110] },
		bgGradient: [
			[27, 181, 201],
			[11, 46, 94],
		],
		padding: 70,
		font: {
			title: { families: ['Nunito'], weight: 'ExtraBold', size: 68, lineHeight: 1.1, color: [255, 255, 255] },
			description: { families: ['Nunito'], weight: 'Normal', size: 34, lineHeight: 1.35, color: [226, 244, 248] },
		},
		fonts: ['./node_modules/@fontsource-variable/nunito/files/nunito-latin-wght-normal.woff2'],
	}),
});
