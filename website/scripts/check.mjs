#!/usr/bin/env node
// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Checks the built site (dist/) before it's published; `npm run check` after `npm run build`. Fails on:
 *   - an internal link or picture that leads nowhere: every href / src of every page must be a file of the build,
 *     with a trailing slash for pages (trailingSlash: 'always'), and its #anchor must be an id on that page;
 *   - a page the apps open that the site doesn't have: every https://sukrara.mauri.app/… URL written in the apps'
 *     code (Android Kotlin, also as "$SITE_URL/…", Mac Swift and its bundle files) must exist, with its #anchor
 *     (an invite's #code excepted). GitHub Pages redirects /privacy to /privacy/, so an app's URL may leave the
 *     slash out;
 *   - the files other systems read: App Links (assetlinks.json with Sukrara's release key, tools/release/android-cert.sha256),
 *     universal links (apple-app-site-association for /join), the Mac update feed (appcast.xml), the sitemap, the 404;
 *   - a link to another site, in the page's language, that this site has in that language: a Spanish page links the
 *     Spanish store pages and Spanish Homebrew, an English page the English ones (LOCALIZED below; add a site there
 *     when a page links one that has pages per language).
 * External links aren't fetched (that would make the build depend on other sites): `--external` checks them too.
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const WEBSITE = fileURLToPath(new URL('..', import.meta.url));
const ROOT = resolve(WEBSITE, '..');
const DIST = join(WEBSITE, 'dist');
const SITE = JSON.parse(readFileSync(join(ROOT, 'assets/brand/sukrara/brand.json'), 'utf8')).url;
const problems = [];
const fail = (message) => problems.push(message);

if (!existsSync(DIST)) {
	console.error('website/dist is missing: run `npm run build` first');
	process.exit(2);
}

function walk(dir) {
	return readdirSync(dir).flatMap((name) => {
		const path = join(dir, name);
		return statSync(path).isDirectory() ? walk(path) : [path];
	});
}

// The 404 pages are served for any path, so their own canonical links point nowhere by design.
const files = walk(DIST).filter((f) => !f.includes(`${DIST}/pagefind/`) && !/\/404(\.html|\/index\.html)$/.test(f));
const pages = new Map(); // '/android/index.html' → { lang, ids, links }
for (const file of files.filter((f) => f.endsWith('.html'))) {
	const html = readFileSync(file, 'utf8').replace(/<script\b[\s\S]*?<\/script>/g, '');
	const ids = new Set([...html.matchAll(/\s(?:id|name)="([^"]+)"/g)].map((m) => m[1]));
	const links = [...html.matchAll(/<(?:a|link|img|source|audio|video|script)\b[^>]*?\s(?:href|src)="([^"]*)"/g)].map((m) =>
		m[1].replaceAll('&amp;', '&'),
	);
	// The content's language: a Spanish URL whose page isn't translated yet shows the English one (Starlight's fallback).
	const lang = (html.match(/<main\b[^>]*\slang="([a-z]+)/) ?? html.match(/<html\b[^>]*\slang="([a-z]+)/))?.[1] ?? 'en';
	pages.set('/' + relative(DIST, file).split('\\').join('/'), { lang, ids, links });
}

/** The file a site path is served from, or undefined. Pages need their trailing slash unless `lenient`. */
function served(path, lenient = false) {
	const clean = decodeURIComponent(path.split('?')[0]);
	if (clean.endsWith('/')) return pages.has(clean + 'index.html') ? clean + 'index.html' : undefined;
	if (existsSync(join(DIST, clean)) && statSync(join(DIST, clean)).isFile()) return clean;
	if (lenient && pages.has(clean + '/index.html')) return clean + '/index.html';
}

function checkLink(from, href, { lenient = false, anchors = true } = {}) {
	if (!href || /^(?:[a-z][a-z0-9+.-]*:)/i.test(href) && !href.startsWith(SITE)) return;
	const url = new URL(href, SITE + from);
	// A full https://sukrara.mauri.app/… URL written out (in a report, say) works like an app's: Pages adds the slash.
	if (href.startsWith(SITE)) lenient = true;
	if (url.origin !== new URL(SITE).origin) return;
	const target = served(url.pathname, lenient);
	if (!target) {
		const hint = !url.pathname.endsWith('/') && pages.has(url.pathname + '/index.html') ? ' (add the trailing slash)' : '';
		return fail(`${from}: ${href} leads nowhere${hint}`);
	}
	const anchor = decodeURIComponent(url.hash.slice(1));
	if (anchors && anchor && target.endsWith('.html') && !pages.get(target).ids.has(anchor)) {
		fail(`${from}: ${href}: no #${anchor} on ${target}`);
	}
}

/**
 * Other sites with a page per language: the language a link of theirs opens in, or undefined when the link doesn't
 * say (then the site shows its default, English, or the browser's language: fine on an English page only).
 */
const LOCALIZED = {
	'play.google.com': (u) => u.searchParams.get('hl') ?? undefined,
	'apps.apple.com': (u) => ({ us: 'en', es: 'es' })[u.pathname.split('/')[1]],
	'f-droid.org': (u) => u.pathname.split('/')[1].match(/^[a-z]{2}(?:[_-][A-Za-z]+)?$/)?.[0],
	'brew.sh': (u) => (u.pathname.startsWith('/es/') ? 'es' : undefined),
	'apps.garmin.com': (u) => ({ 'en-US': 'en', 'es-ES': 'es' })[u.pathname.split('/')[1]],
};

// Every link of every page.
let count = 0;
for (const [page, { lang, links }] of pages) {
	const from = page.replace(/index\.html$/, '');
	for (const href of links) {
		const host = /^https:\/\//.test(href) && new URL(href).host;
		const opens = host && LOCALIZED[host] ? (LOCALIZED[host](new URL(href)) ?? 'en') : lang;
		if (opens !== lang) {
			fail(`${from}: ${href} isn't ${host}'s page in this page's language (${lang})`);
		}
		if (href.startsWith('#')) {
			const anchor = decodeURIComponent(href.slice(1));
			if (anchor && !pages.get(page).ids.has(anchor)) fail(`${from}: no #${anchor} on the page`);
		} else checkLink(from, href);
		count++;
	}
}

// Every URL of the site that an app opens.
function sources(dir, pattern) {
	return existsSync(dir) ? walk(dir).filter((f) => pattern.test(f) && !/\/(build|\.build)\//.test(f)) : [];
}
const appFiles = [
	...sources(join(ROOT, 'apps/android'), /\/src\/main\/.*\.(kt|xml)$/),
	...sources(join(ROOT, 'apps/macos/Sources'), /\.swift$/),
	...sources(join(ROOT, 'apps/macos/Bundle'), /\.(plist|in|entitlements)$/),
];
const appUrls = new Map();
for (const file of appFiles) {
	const text = readFileSync(file, 'utf8');
	for (const m of text.matchAll(/(?:https:\/\/sukrara\.mauri\.app|\$\{?SITE_URL\}?)(\/[^"'`\s)<>\\…]*)?/g)) {
		const path = (m[1] ?? '/').replace(/[.,;:]+$/, '');
		if (!appUrls.has(path)) appUrls.set(path, relative(ROOT, file));
	}
}
for (const [path, file] of appUrls) {
	checkLink(`${file} → `, SITE + path, { lenient: true, anchors: !path.startsWith('/join') });
}

// Files other systems read.
function json(path) {
	try {
		return JSON.parse(readFileSync(join(DIST, path), 'utf8'));
	} catch (error) {
		fail(`${path}: ${error.message}`);
	}
}
const fingerprint = readFileSync(join(ROOT, 'tools/release/android-cert.sha256'), 'utf8').trim();
const assetlinks = json('.well-known/assetlinks.json');
const target = assetlinks?.find((l) => l.target?.package_name === 'app.mauri.sukrara')?.target;
if (!target) fail('.well-known/assetlinks.json: no entry for app.mauri.sukrara');
else if (!target.sha256_cert_fingerprints.map((f) => f.replaceAll(':', '')).includes(fingerprint)) {
	fail(`.well-known/assetlinks.json: the release key (${fingerprint}) isn't listed`);
}
const aasa = json('.well-known/apple-app-site-association');
const joins = aasa?.applinks?.details?.some((d) => d.components?.some((c) => c['/'] === '/join'));
if (aasa && !joins) fail('.well-known/apple-app-site-association: /join isn’t a universal link');
const appcast = existsSync(join(DIST, 'appcast.xml')) ? readFileSync(join(DIST, 'appcast.xml'), 'utf8') : '';
if (!/<rss[\s\S]*<item>[\s\S]*<\/rss>\s*$/.test(appcast)) fail('appcast.xml: missing or without a release');
for (const file of ['404.html', 'sitemap-index.xml', 'robots.txt', 'favicon.svg']) {
	if (!existsSync(join(DIST, file))) fail(`${file} is missing`);
}

// Optionally, every link to another site.
if (process.argv.includes('--external')) {
	const external = new Set([...pages.values()].flatMap((p) => p.links).filter((h) => /^https?:\/\//.test(h) && !h.startsWith(SITE)));
	for (const url of external) {
		try {
			const r = await fetch(url, { method: 'HEAD', redirect: 'follow', signal: AbortSignal.timeout(15_000) });
			if (r.status >= 400 && r.status !== 405 && r.status !== 429) fail(`${url}: ${r.status}`);
		} catch (error) {
			fail(`${url}: ${error.cause?.code ?? error.message}`);
		}
	}
}

if (problems.length) {
	console.error(problems.map((p) => `✗ ${p}`).join('\n'));
	console.error(`\n${problems.length} problem(s) in website/dist`);
	process.exit(1);
}
console.log(`✓ ${pages.size} pages, ${count} links, ${appUrls.size} app URLs, App Links, universal links and the update feed`);
