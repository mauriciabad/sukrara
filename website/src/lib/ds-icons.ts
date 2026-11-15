// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The Android app's own icons on the site: core/designsystem's `ds_ic_*.xml` vector drawables (Material Symbols,
 * Apache-2.0, and Sukrara's diabetes icons drawn on the same grid) turned into SVG at build time, so a feature wears
 * the icon the app gives it and a new icon in the app is one name away here. White, the drawables' tint colour,
 * becomes the text's colour (`currentColor`), keeping its alpha.
 *   dsIcon('notifications_active') → { viewBox: '0 0 960 960', body: '<g …><path …/></g>' }
 */
const DRAWABLES = import.meta.glob<string>('@repo/apps/android/core/designsystem/src/main/res/drawable/ds_ic_*.xml', {
	query: '?raw',
	import: 'default',
	eager: true,
});

export interface DsIcon {
	viewBox: string;
	/** The SVG elements inside <svg>, from the drawable's groups and paths in order. */
	body: string;
}

const attrs = (tag: string) =>
	Object.fromEntries([...tag.matchAll(/android:(\w+)="([^"]*)"/g)].map(([, k, v]) => [k, v] as const));

/** A drawable colour as SVG paint: white is the text's colour, a transparent one is none. */
function paint(color: string | undefined): { paint: string; opacity?: string } {
	if (!color) return { paint: 'none' };
	if (color === '@android:color/white') return { paint: 'currentColor' };
	const hex = /^#([0-9a-f]{2})?([0-9a-f]{6})$/i.exec(color);
	if (!hex) throw new Error(`ds-icons: colour ${color} isn't one the site knows`);
	const [, alpha = 'FF', rgb] = hex;
	const opacity = parseInt(alpha, 16) / 255;
	if (opacity === 0) return { paint: 'none' };
	return {
		paint: rgb.toUpperCase() === 'FFFFFF' ? 'currentColor' : `#${rgb}`,
		opacity: opacity < 1 ? opacity.toFixed(2) : undefined,
	};
}

function convert(file: string, xml: string): DsIcon | undefined {
	const source = xml.replace(/<!--[\s\S]*?-->/g, '');
	const vector = /<vector\b[^>]*>/.exec(source);
	if (!vector) return undefined; // a layer list or inset: not an icon
	const { viewportWidth, viewportHeight } = attrs(vector[0]);
	let body = '';
	for (const [tag] of source.matchAll(/<group\b[^>]*>|<\/group>|<path\b[^>]*\/>/g)) {
		if (tag === '</group>') {
			body += '</g>';
			continue;
		}
		const a = attrs(tag);
		if (tag.startsWith('<group')) {
			if (a.rotation || a.pivotX || a.pivotY) throw new Error(`ds-icons: ${file} rotates a group`);
			const translate = a.translateX || a.translateY ? `translate(${a.translateX ?? 0} ${a.translateY ?? 0})` : '';
			const scale = a.scaleX || a.scaleY ? `scale(${a.scaleX ?? 1} ${a.scaleY ?? 1})` : '';
			body += `<g transform="${[translate, scale].filter(Boolean).join(' ')}">`;
			continue;
		}
		const fill = paint(a.fillColor);
		const stroke = paint(a.strokeColor);
		const svg = [
			`d="${a.pathData}"`,
			`fill="${fill.paint}"`,
			fill.opacity && `fill-opacity="${fill.opacity}"`,
			a.fillType === 'evenOdd' && 'fill-rule="evenodd"',
			stroke.paint !== 'none' && `stroke="${stroke.paint}"`,
			stroke.paint !== 'none' && stroke.opacity && `stroke-opacity="${stroke.opacity}"`,
			stroke.paint !== 'none' && `stroke-width="${a.strokeWidth ?? 1}"`,
			stroke.paint !== 'none' && a.strokeLineCap && `stroke-linecap="${a.strokeLineCap}"`,
			stroke.paint !== 'none' && a.strokeLineJoin && `stroke-linejoin="${a.strokeLineJoin}"`,
		];
		body += `<path ${svg.filter(Boolean).join(' ')}/>`;
	}
	return { viewBox: `0 0 ${viewportWidth} ${viewportHeight}`, body };
}

const ICONS = new Map<string, DsIcon>();
for (const [path, xml] of Object.entries(DRAWABLES)) {
	const name = /ds_ic_(\w+)\.xml$/.exec(path)![1];
	const icon = convert(path, xml);
	if (icon) ICONS.set(name, icon);
}

/** The icon `ds_ic_<name>`; a name the app doesn't have fails the build. */
export function dsIcon(name: string): DsIcon {
	const icon = ICONS.get(name);
	if (!icon) throw new Error(`ds-icons: the app has no ds_ic_${name}`);
	return icon;
}
