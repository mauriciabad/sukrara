// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * A screen of Sukrara's watch app or data field as inline SVG, drawn at build time by its reference renderer
 * (tools/watchface/app.js, read through `@repo`, never copied): its ids scoped so screens can share a page, hidden from
 * screen readers (the page names it), sized by its container. `fontFace` is the @font-face for the type it asks for
 * (Atkinson Hyperlegible Next for the big numbers, the site's own copy; Roboto Condensed / Roboto standing in for the
 * watch's system fonts, apps/garmin/fonts), to put on the page with it.
 */
import appSource from '@repo/tools/watchface/app.js?raw';
import atkinson from '@fontsource-variable/atkinson-hyperlegible-next/files/atkinson-hyperlegible-next-latin-wght-normal.woff2?url';
// the watch's system fonts' stand-ins (app.js TYPE), the repo's own copies (OFL)
import robotoCondensed from '@repo/apps/garmin/fonts/RobotoCondensed-Bold.woff2?url';
import roboto from '@repo/apps/garmin/fonts/Roboto-Regular.woff2?url';
import { scopeIds } from '../face-playground/svg-ids.ts';

// app.js is a plain script that exports through module.exports: run it with its own `module`
const mod = { exports: {} as { svg(cfg: Record<string, unknown>): string } };
new Function('module', appSource)(mod);

export function screenSvg(id: string, cfg: Record<string, unknown>): string {
	return scopeIds(mod.exports.svg({ cls: 'r390', ...cfg }), `ws-${id}`)
		.replace(/ role="img" aria-label="[^"]*"/, ' aria-hidden="true" focusable="false"')
		.replace(/ width="\d+" height="\d+"/, '');
}

export const fontFace =
	`<style>@font-face{font-family:'Atkinson Hyperlegible Next';src:url(${atkinson}) format('woff2');font-weight:200 800;font-display:swap}` +
	`@font-face{font-family:'Sukrara Roboto Condensed';src:url(${robotoCondensed}) format('woff2');font-weight:700;font-display:swap}` +
	`@font-face{font-family:'Sukrara Roboto';src:url(${roboto}) format('woff2');font-weight:400;font-display:swap}</style>`;
