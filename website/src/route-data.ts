// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Per page, after Starlight builds its route data:
 *   - the link preview image (pages/og/[...route].ts) as og:image and twitter:image;
 *   - "Edit page" only on the developers' pages and reports: the apps' pages, the privacy policy and the home page are
 *     the product's words, not a document to propose changes to from the page.
 */
import { defineRouteMiddleware } from '@astrojs/starlight/route-data';
import { SITE } from './site';

export const onRequest = defineRouteMiddleware((context) => {
	const route = context.locals.starlightRoute;
	const image = new URL(`/og/${route.entry.id || 'index'}.png`, SITE).href;
	route.head.push(
		{ tag: 'meta', attrs: { property: 'og:image', content: image } },
		{ tag: 'meta', attrs: { property: 'og:image:width', content: '1200' } },
		{ tag: 'meta', attrs: { property: 'og:image:height', content: '630' } },
		{ tag: 'meta', attrs: { name: 'twitter:image', content: image } },
	);
	if (!/^(developers|research)(\/|$)/.test(route.entry.id)) route.editUrl = undefined;
});
