// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The site's facts in one place: the domain (from the brand), the repository, and where each app is published.
 * A store going live is one line here: its `status` becomes 'live' and its `url` the listing.
 */
import brand from '../../assets/brand/sukrara/brand.json' with { type: 'json' };

/** https://sukrara.mauri.app: the brand's own URL, the one the apps open (assets/brand/sukrara/brand.json). */
export const SITE = brand.url;
export const BRAND = brand.brand;
export const REPO = 'mauriciabad/sukrara';
export const GITHUB = `https://github.com/${REPO}`;
export const RELEASES = `${GITHUB}/releases`;
export const ISSUES = `${GITHUB}/issues`;

/**
 * Where each app is published. `status`: 'live' (show it, link it), 'review' (submitted, waiting for the store) or
 * 'planned'. Only 'live' stores get a badge; the others are named as coming, never linked.
 */
export const STORES = {
	/** Google Play: #142, in production review since 2026-11-13 (docs/20-publishing.md). */
	play: {
		status: 'review',
		url: `https://play.google.com/store/apps/details?id=${brand.applicationId}`,
	},
	/** Mac App Store: TestFlight first (docs/20-publishing.md › Mac App Store). */
	appStore: { status: 'planned', url: '' },
} as const satisfies Record<string, { status: 'live' | 'review' | 'planned'; url: string }>;

/** Obtainium adds the GitHub releases and checks each APK is signed with Sukrara's key (docs/20-publishing.md). */
export const OBTAINIUM = {
	add: `obtainium://add/${GITHUB}`,
	home: 'https://obtainium.imranr.dev/',
};

/** The Homebrew cask (mauriciabad/homebrew-tap, updated by .github/workflows/homebrew-tap.yml). */
export const HOMEBREW = 'brew install --cask mauriciabad/tap/sukrara';
