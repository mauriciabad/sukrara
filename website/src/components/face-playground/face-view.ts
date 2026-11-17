// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Shows a face's SVG markup in a host element and keeps it current. A new drawing is patched onto the one on screen
 * (attributes and text, node by node) instead of replacing it, so the transitions the face's markup declares run as
 * they would on the watch (the bead's glide, the arrow turning). Knows nothing about which face it shows.
 */

const parser = typeof DOMParser === 'undefined' ? null : new DOMParser();

function patch(live: Element, next: Element) {
	for (const { name } of [...live.attributes]) if (!next.hasAttribute(name)) live.removeAttribute(name);
	for (const { name, value } of [...next.attributes]) if (live.getAttribute(name) !== value) live.setAttribute(name, value);
	const a = live.childNodes;
	const b = next.childNodes;
	const same = a.length === b.length && [...a].every((n, i) => n.nodeName === b[i].nodeName);
	if (!same) {
		live.replaceChildren(...[...b].map((n) => live.ownerDocument.importNode(n, true)));
		return;
	}
	b.forEach((n, i) => {
		const m = a[i];
		if (n.nodeType === Node.ELEMENT_NODE) patch(m as Element, n as Element);
		else if (m.nodeValue !== n.nodeValue) m.nodeValue = n.nodeValue;
	});
}

export function faceView(host: HTMLElement) {
	let last = '';
	return {
		show(markup: string) {
			if (markup === last) return;
			last = markup;
			const next = parser!.parseFromString(markup, 'image/svg+xml').documentElement;
			const live = host.firstElementChild;
			if (live && live.nodeName === next.nodeName) patch(live, next);
			else host.replaceChildren(document.importNode(next, true));
		},
	};
}
