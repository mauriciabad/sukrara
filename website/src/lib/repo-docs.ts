// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The repository's docs/ on the website, read where they live (never copied, never moved, written the GitHub way):
 * the developer docs (FEATURES.md, ARCHITECTURE.md, …) at /developers/<name>/ and the investigation reports
 * (docs/NN-*.md) at /research/<file>/. A new report shows up by itself, ordered by its number.
 *
 * Each file's `# H1` becomes the page title and its first paragraph the description. Its links are written for
 * GitHub, relative to docs/; here they become site links:
 *   - another published doc (`03-nostr.md#goal`)        → its page (`/research/03-nostr/#goal`)
 *   - an image in docs/img (`img/x.png`)                → `/img/x.png` (docs/img is served as is: astro.config.ts)
 *   - anything else in the repo (`../poc/…`, `evidence/…`) → the file on GitHub at this commit (images: the raw file)
 *   - `https://sukrara.mauri.app/…`                     → the same page on this site (works in dev and previews)
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import type { DataStore, Loader, LoaderContext } from 'astro/loaders';
import { REPO, SITE } from '../site';

export const ROOT = fileURLToPath(new URL('../../../', import.meta.url));
export const DOCS = join(ROOT, 'docs');
const WEBSITE = join(ROOT, 'website');

/** The developer docs, in sidebar order, with their short sidebar label; any other unnumbered docs/X.md follows. */
const DEVELOPERS: Record<string, string> = {
	'FEATURES.md': 'Behaviour catalog',
	'ARCHITECTURE.md': 'Architecture',
	'DEVELOPMENT.md': 'Development',
	'QA.md': 'Manual QA',
	'GARMIN-FACES.md': 'Garmin watch faces',
};

export interface RepoDoc {
	/** The file name in docs/, e.g. `09-brand.md`. */
	file: string;
	/** The collection id and URL path: `research/09-brand` or `developers/features`. */
	id: string;
	section: 'developers' | 'research';
	/** A short sidebar label (developer docs only; reports use their title). */
	label?: string;
}

/** Every Markdown file of docs/ that the site publishes. */
export function repoDocs(): RepoDoc[] {
	const files = readdirSync(DOCS).filter((f) => f.endsWith('.md'));
	const developers = files
		.filter((f) => !/^\d/.test(f))
		.sort((a, b) => order(a) - order(b) || a.localeCompare(b))
		.map((file): RepoDoc => ({
			file,
			id: `developers/${file.replace(/\.md$/, '').toLowerCase()}`,
			section: 'developers',
			label: DEVELOPERS[file],
		}));
	const research = files
		.filter((f) => /^\d/.test(f))
		.sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
		.map((file): RepoDoc => ({ file, id: `research/${file.replace(/\.md$/, '')}`, section: 'research' }));
	return [...developers, ...research];
}

function order(file: string) {
	const i = Object.keys(DEVELOPERS).indexOf(file);
	return i < 0 ? Infinity : i;
}

/** The commit links outside docs/ point at: CI's commit, else this checkout's HEAD, else `next`. */
const REF = (() => {
	if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA;
	try {
		return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: ROOT, encoding: 'utf8' }).trim();
	} catch {
		return 'next';
	}
})();

const IMAGES = /\.(png|jpe?g|gif|webp|svg|m4a|mp4|webm)$/i;

/** Rewrites one link target written relative to docs/ (see the file comment). Unknown targets are kept as written. */
export function siteLink(target: string, docsUrls: Map<string, string>, missing: Set<string>): string {
	if (target.startsWith(SITE)) {
		// The page itself, with the trailing slash its URL has here (GitHub Pages would redirect to it).
		const [path, hash = ''] = splitHash(target.slice(SITE.length) || '/');
		return (/\/$|\.[a-z0-9]+$/i.test(path) ? path : path + '/') + hash;
	}
	if (/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(target)) return target;
	const [path, hash = ''] = splitHash(target);
	if (!path) return target;
	const absolute = resolve(DOCS, decodeURIComponent(path));
	const inRepo = relative(ROOT, absolute).split(sep).join('/');
	if (inRepo.startsWith('..')) return target;
	const page = docsUrls.get(inRepo);
	if (page) return page + hash;
	const isDir = existsSync(absolute) && statSync(absolute).isDirectory();
	if (inRepo.startsWith('docs/img/') && !isDir) return '/img/' + inRepo.slice('docs/img/'.length) + hash;
	if (!existsSync(absolute)) missing.add(target);
	if (IMAGES.test(inRepo)) return `https://raw.githubusercontent.com/${REPO}/${REF}/${inRepo}`;
	const kind = isDir ? 'tree' : 'blob';
	return `https://github.com/${REPO}/${kind}/${REF}/${inRepo.replace(/\/$/, '')}${hash}`;
}

function splitHash(target: string): [string, string?] {
	const i = target.indexOf('#');
	return i < 0 ? [target] : [target.slice(0, i), target.slice(i)];
}

/** Every link and image target in Markdown (`](…)`) and HTML (`src="…"`, `href="…"`), outside fenced code. */
function rewriteLinks(markdown: string, fix: (target: string) => string): string {
	return markdown
		.split(/(^```[\s\S]*?^```)/m)
		.map((part, i) =>
			i % 2
				? part
				: part
						.replace(/(\]\(\s*<?)([^)\s>]+)/g, (_, lead, target) => lead + fix(target))
						.replace(/(\b(?:src|href)=["'])([^"']+)/g, (_, lead, target) => lead + fix(target)),
		)
		.join('');
}

/** The first `# Heading` (the page title) and the Markdown after it. */
function splitTitle(markdown: string, file: string): { title: string; body: string } {
	const match = markdown.match(/^#\s+(.+)\n/m);
	if (!match || match.index === undefined) throw new Error(`docs/${file} has no "# Title" line`);
	return { title: match[1].trim(), body: markdown.slice(match.index + match[0].length) };
}

/** The first plain paragraph (after `## Goal` when there is one), as text, cut near 160 characters. */
function describe(body: string): string {
	const goal = body.indexOf('\n## Goal');
	const paragraphs = (goal >= 0 ? body.slice(goal + 8) : body).split(/\n\s*\n/);
	const first = paragraphs
		.map((p) => p.trim())
		.find((p) => p && !/^([#>|<!*`-]|\d+\.)/.test(p) && !/^\*[^*]/.test(p));
	if (!first) return '';
	const text = first
		.replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
		.replace(/<[^>]+>/g, '')
		.replace(/[*_`]/g, '')
		.replace(/\s+/g, ' ')
		.trim();
	if (text.length <= 160) return text;
	const cut = text.slice(0, 157);
	return cut.slice(0, cut.lastIndexOf(' ')) + '…';
}

function lastCommitDate(file: string): Date | undefined {
	try {
		const iso = execFileSync('git', ['log', '-1', '--format=%cI', '--', join('docs', file)], {
			cwd: ROOT,
			encoding: 'utf8',
		}).trim();
		return iso ? new Date(iso) : undefined;
	} catch {
		return undefined;
	}
}

/** The site URL of every published doc, keyed by its path in the repository (`docs/09-brand.md`). */
export function docsUrls(): Map<string, string> {
	return new Map(repoDocs().map((d) => [`docs/${d.file}`, `/${d.id}/`]));
}

/** Loads docs/*.md into Starlight's `docs` collection (see `withRepoDocs`). */
async function loadRepoDocs({ store, parseData, renderMarkdown, logger }: LoaderContext) {
	const urls = docsUrls();
	for (const doc of repoDocs()) {
		const path = join(DOCS, doc.file);
		const source = readFileSync(path, 'utf8');
		const missing = new Set<string>();
		const { title, body } = splitTitle(source, doc.file);
		const markdown = rewriteLinks(body, (t) => siteLink(t, urls, missing));
		for (const target of missing) logger.warn(`docs/${doc.file} links to ${target}, which isn't in the repository`);
		const data = await parseData({
			id: doc.id,
			data: {
				title,
				description: describe(body) || undefined,
				lastUpdated: lastCommitDate(doc.file),
				sidebar: doc.label ? { label: doc.label } : {},
			},
		});
		store.set({
			id: doc.id,
			data,
			body: markdown,
			rendered: await renderMarkdown(markdown, { fileURL: pathToFileURL(path) }),
			// No digest: every build renders them again (a second or two), so a change to this file shows at once.
			filePath: relative(WEBSITE, path).split(sep).join('/'),
		});
	}
}

/**
 * Starlight's docs loader (the site's own pages in src/content/docs) plus docs/*.md, in the one `docs` collection, so
 * the reports get Starlight's routing, search, sidebar and translation fallbacks like any page. The glob loader
 * deletes every entry it didn't load itself; it sees only the site's own ids here.
 */
export function withRepoDocs(siteLoader: Loader): Loader {
	return {
		name: 'sukrara-docs',
		async load(context) {
			const repoIds = new Set(repoDocs().map((d) => d.id));
			const isRepoDoc = (id: string) => repoIds.has(id);
			const store = new Proxy(context.store, {
				get(target, prop) {
					if (prop === 'keys') return () => target.keys().filter((id) => !isRepoDoc(id));
					const value = Reflect.get(target, prop);
					return typeof value === 'function' ? value.bind(target) : value;
				},
			}) as DataStore;
			await siteLoader.load({ ...context, store });
			await loadRepoDocs(context);
			context.watcher?.add(DOCS);
			context.watcher?.on('change', (changed) => {
				if (dirname(changed) === DOCS.replace(/\/$/, '') && changed.endsWith('.md')) loadRepoDocs(context);
			});
		},
	};
}
