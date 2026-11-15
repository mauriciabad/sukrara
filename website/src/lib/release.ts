// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The newest GitHub release, looked up once per build: the download buttons link its APK and dmg straight away and
 * name its version, with no version written anywhere in the site. .github/workflows/website.yml builds again when a
 * release is published, so the buttons follow every release.
 *
 * Without the API (offline, rate limit) the buttons still work: they open the newest release's page, without a version.
 * SUKRARA_RELEASE=offline skips the lookup (the link checker's CI build does, so a pull request never depends on it).
 */
import { REPO, RELEASES } from '../site';

export interface Asset {
	url: string;
	/** Bytes. */
	size: number;
	name: string;
}

export interface Release {
	/** `0.14.5`, or undefined when the lookup failed. */
	version?: string;
	date?: Date;
	/** The release's page on GitHub (its notes). */
	page: string;
	apk?: Asset;
	dmg?: Asset;
}

const FALLBACK: Release = { page: `${RELEASES}/latest` };

let cached: Promise<Release> | undefined;

/** The newest release (GitHub's "Latest": never a draft or a pre-release). */
export function latestRelease(): Promise<Release> {
	cached ??= lookUp();
	return cached;
}

async function lookUp(): Promise<Release> {
	if (process.env.SUKRARA_RELEASE === 'offline') return FALLBACK;
	const headers: Record<string, string> = { Accept: 'application/vnd.github+json', 'User-Agent': 'sukrara-website' };
	if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
	try {
		const response = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
			headers,
			signal: AbortSignal.timeout(10_000),
		});
		if (!response.ok) throw new Error(`GitHub answered ${response.status}`);
		const json = (await response.json()) as {
			tag_name: string;
			html_url: string;
			published_at: string;
			assets: { name: string; size: number; browser_download_url: string }[];
		};
		const asset = (pattern: RegExp): Asset | undefined => {
			const a = json.assets.find((a) => pattern.test(a.name));
			return a && { url: a.browser_download_url, size: a.size, name: a.name };
		};
		return {
			version: json.tag_name.replace(/^v/, ''),
			date: new Date(json.published_at),
			page: json.html_url,
			apk: asset(/^Sukrara-[\d.]+\.apk$/),
			dmg: asset(/^Sukrara-[\d.]+\.dmg$/),
		};
	} catch (error) {
		console.warn(`[release] the newest release couldn't be looked up (${error}); the buttons open ${FALLBACK.page}`);
		return FALLBACK;
	}
}

/** `22 MB` */
export function megabytes(bytes: number): string {
	return `${Math.round(bytes / 1_000_000)} MB`;
}
