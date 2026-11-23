// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Each app's newest GitHub release, looked up once per build: the download buttons link its APK or dmg straight away
 * and name its version, with no version written anywhere in the site. Each app has its own version and releases
 * (contracts/apps/apps.json, docs/48-release-versioning.md: Android's `v<version>`, the Mac's `mac-v<version>`, up to
 * 0.17.1 one `v<version>` release with both files); an app's newest release is the newest one carrying its file
 * (`Sukrara-<version>.apk`, `Sukrara-<version>.dmg`), which no other app's release has. .github/workflows/website.yml
 * builds again when a release is published, so the buttons follow every release.
 *
 * SUKRARA_RELEASE=required (the deploy) fails the build when the lookup fails or no release has the app's file, so a
 * published button always downloads the file. Otherwise (a local build, offline) the buttons fall back to the
 * releases page, without a version; SUKRARA_RELEASE=offline skips the lookup (CI's check build, so a pull request
 * never depends on the API).
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
	/** `v0.14.5`, `mac-v0.17.2` */
	tag?: string;
	/** An immutable release: its files can't change, and `gh release verify-asset` checks one against it. */
	immutable?: boolean;
	/** Android's APK or the Mac's dmg. */
	file?: Asset;
}

/** The apps with a file the site offers, and that file's name (its version inside). */
export type App = 'android' | 'mac';
const FILES: Record<App, RegExp> = { android: /^Sukrara-(\d+\.\d+\.\d+)\.apk$/, mac: /^Sukrara-(\d+\.\d+\.\d+)\.dmg$/ };

const FALLBACK: Release = { page: RELEASES };

interface GitHubRelease {
	tag_name: string;
	html_url: string;
	published_at: string;
	draft: boolean;
	prerelease: boolean;
	immutable?: boolean;
	assets: { name: string; size: number; browser_download_url: string; digest?: string | null }[];
}

let cached: Promise<GitHubRelease[] | undefined> | undefined;

/** The app's newest release (never a draft or a pre-release). */
export async function latestRelease(app: App): Promise<Release> {
	const mode = process.env.SUKRARA_RELEASE;
	if (mode === 'offline') return FALLBACK;
	cached ??= list();
	const releases = await cached;
	try {
		if (!releases) throw new Error('the list of releases could not be read');
		const files = releases
			.filter((r) => !r.draft && !r.prerelease)
			.flatMap((r) =>
				r.assets.flatMap((a) => {
					const v = FILES[app].exec(a.name)?.[1];
					return v ? [{ r, a, v }] : [];
				}),
			);
		const found = files.sort((x, y) => compare(y.v, x.v))[0];
		if (!found) throw new Error(`no release has a Sukrara-<version>.${app === 'android' ? 'apk' : 'dmg'}`);
		const { r: json, a, v } = found;
		const sha256 = a.digest?.startsWith('sha256:') ? a.digest.slice(7) : undefined;
		const release: Release = {
			version: v,
			tag: json.tag_name,
			date: new Date(json.published_at),
			page: json.html_url,
			immutable: json.immutable === true,
			file: { url: a.browser_download_url, size: a.size, name: a.name, sha256 },
		};
		return release;
	} catch (error) {
		if (mode === 'required') throw new Error(`[release] ${app}'s newest release is required here: ${error}`);
		console.warn(`[release] ${app}'s newest release couldn't be looked up (${error}); the buttons open ${FALLBACK.page}`);
		return FALLBACK;
	}
}

/** One page of the newest 100 releases: every app's newest is among them. */
async function list(): Promise<GitHubRelease[] | undefined> {
	const headers: Record<string, string> = { Accept: 'application/vnd.github+json', 'User-Agent': 'sukrara-website' };
	if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
	try {
		const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=100`, {
			headers,
			signal: AbortSignal.timeout(10_000),
		});
		if (!response.ok) throw new Error(`GitHub answered ${response.status}`);
		return (await response.json()) as GitHubRelease[];
	} catch (error) {
		console.warn(`[release] the releases couldn't be listed (${error})`);
		return undefined;
	}
}

/** `0.17.10` > `0.17.9`. */
function compare(a: string, b: string): number {
	const x = a.split('.').map(Number);
	const y = b.split('.').map(Number);
	for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] - y[i];
	return 0;
}

/** `22 MB` */
export function megabytes(bytes: number): string {
	return `${Math.round(bytes / 1_000_000)} MB`;
}
