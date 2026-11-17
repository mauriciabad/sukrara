// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Gives an SVG drawing's ids a prefix, and every reference to them (url(#…), href="#…"), so several drawings of the
 * same face can share a page: an id is the page's, and two faces with the same ids would draw each other's gradients
 * and masks. Knows nothing about which face drew it.
 */

export function scopeIds(markup: string, prefix: string): string {
	const ids = new Set([...markup.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]));
	if (!ids.size) return markup;
	const scoped = (id: string) => (ids.has(id) ? `${prefix}-${id}` : id);
	return markup
		.replace(/(\sid=")([^"]+)"/g, (_, a, id) => `${a}${scoped(id)}"`)
		.replace(/url\(#([^)]+)\)/g, (_, id) => `url(#${scoped(id)})`)
		.replace(/(href=")#([^"]+)"/g, (_, a, id) => `${a}#${scoped(id)}"`);
}
