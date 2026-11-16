// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Runs the face's reference implementation (tools/watchface/aura.js) from its source text and returns what it exports.
 * aura.js is a plain script that exports through `module.exports` (Node) or `window.Aura` (a page); neither is an ES
 * module, so it's run here with its own `module` and no `window`: nothing global is set. The site and the tests both
 * load it this way (aura/index.ts with Vite's ?raw, the tests with fs).
 */

export type AuraModule = {
	AuraFace: new (cfg: Record<string, unknown>) => { opt(): Record<string, unknown> };
	layout(cfg: Record<string, unknown>): Record<string, unknown>;
	svg(cfg: Record<string, unknown>): string;
	/** The drawing's template (aura/parts.ts reads which element shows what). */
	MARKUP: string;
};

export function evaluateAura(source: string): AuraModule {
	const module = { exports: {} as Partial<AuraModule> };
	new Function('module', 'window', source)(module, undefined);
	const m = module.exports;
	if (typeof m.svg !== 'function' || typeof m.AuraFace !== 'function' || typeof m.MARKUP !== 'string') throw new Error('aura.js no longer exports svg, AuraFace and MARKUP');
	return m as AuraModule;
}
