// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The newest GitHub release, looked up once per build: the download buttons link its APK and dmg straight away and
 * name its version, with no version written anywhere in the site. .github/workflows/website.yml builds again when a
 * release is published, so the buttons follow every release.
 *
 * SUKRARA_RELEASE=required (the deploy) fails the build when the lookup fails or the release lacks the APK or the dmg,
 * so a published button always downloads the file. Otherwise (a local build, offline) the buttons fall back to the
 * newest release's page, without a version; SUKRARA_RELEASE=offline skips the lookup (CI's check build, so a pull
 * request never depends on the API).
 */
import { REPO, RELEASES } from '../site';

export interface Asset {
	url: string;
	/** Bytes. */
	size: number;
	name: string;
	/** The SHA-256 GitHub recorded when the file was uploaded (hex), if it did. */
	sha256?: string;
}

export interface Release {
	/** `0.14.5`, or undefined when the lookup failed. */
	version?: string;
	date?: Date;
	/** The release's page on GitHub (its notes). */
	page: string;
	/** `v0.14.5` */
	tag?: string;
	/** An immutable release: its files can't change, and `gh release verify-asset` checks one against it. */
	immutable?: boolean;
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
	const mode = process.env.SUKRARA_RELEASE;
	if (mode === 'offline') return FALLBACK;
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
			immutable?: boolean;
			assets: { name: string; size: number; browser_download_url: string; digest?: string | null }[];
		};
		const asset = (pattern: RegExp): Asset | undefined => {
			const a = json.assets.find((a) => pattern.test(a.name));
			const sha256 = a?.digest?.startsWith('sha256:') ? a.digest.slice(7) : undefined;
			return a && { url: a.browser_download_url, size: a.size, name: a.name, sha256 };
		};
		const release: Release = {
			version: json.tag_name.replace(/^v/, ''),
			tag: json.tag_name,
			date: new Date(json.published_at),
			page: json.html_url,
			immutable: json.immutable === true,
			apk: asset(/^Sukrara-[\d.]+\.apk$/),
			dmg: asset(/^Sukrara-[\d.]+\.dmg$/),
		};
		if (!release.apk || !release.dmg) throw new Error(`${json.tag_name} has no APK or no dmg`);
		return release;
	} catch (error) {
		if (mode === 'required') throw new Error(`[release] the newest release is required here: ${error}`);
		console.warn(`[release] the newest release couldn't be looked up (${error}); the buttons open ${FALLBACK.page}`);
		return FALLBACK;
	}
}

/** `22 MB` */
export function megabytes(bytes: number): string {
	return `${Math.round(bytes / 1_000_000)} MB`;
}
