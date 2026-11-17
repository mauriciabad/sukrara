// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Sukrara Aura for the page: the reference implementation and its cases read where the repository keeps them (the
 * `@repo` alias, astro.config.ts), so a change to tools/watchface/aura.js or cases.json shows on the next build with no
 * edit here. Its type, Atkinson Hyperlegible Next, is the site's own copy (@fontsource), registered under the name
 * aura.js asks for: the site loads nothing from another host.
 */
import auraSource from '@repo/tools/watchface/aura.js?raw';
import { cases } from '@repo/contracts/watchface/aura/cases.json';
import atkinson from '@fontsource-variable/atkinson-hyperlegible-next/files/atkinson-hyperlegible-next-latin-wght-normal.woff2?url';
import { createAura, type AuraCase } from './adapter.ts';
import { evaluateAura } from './evaluate.ts';

async function fonts() {
	const face = new FontFace('Atkinson Hyperlegible Next', `url(${atkinson})`, { weight: '200 800' });
	document.fonts.add(await face.load());
}

export const aura = createAura(evaluateAura(auraSource), cases as AuraCase[], fonts);
