// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * The site's facts in one place: the domain (from the brand), the repository, the stores, and what a download can be
 * checked against.
 */
import brand from '../../assets/brand/sukrara/brand.json' with { type: 'json' };
import androidCert from '../../tools/release/android-cert.sha256?raw';

/** https://sukrara.mauri.app: the brand's own URL, the one the apps open (assets/brand/sukrara/brand.json). */
export const SITE = brand.url;
export const BRAND = brand.brand;
export const APPLICATION_ID = brand.applicationId;
export const REPO = 'mauriciabad/sukrara';
export const GITHUB = `https://github.com/${REPO}`;
export const RELEASES = `${GITHUB}/releases`;
export const ISSUES = `${GITHUB}/issues`;

/**
 * The store listings, in the page's language. Every outside link that has a page in the reader's language goes to that
 * one (Spanish page → Spanish store page); `scripts/check.mjs` fails on one that doesn't (LOCALIZED there).
 */
export const STORES = {
	play: (lang: string) => `https://play.google.com/store/apps/details?id=${brand.applicationId}&hl=${lang}`,
	/** App Store Connect app id 6833533014 (docs/20-publishing.md › Mac App Store). */
	appStore: (lang: string) => `https://apps.apple.com/${lang === 'es' ? 'es' : 'us'}/app/sukrara/id6833533014?mt=12`,
	/** F-Droid's main repository (docs/20-publishing.md, #191). */
	fdroid: (lang: string) => `https://f-droid.org/${lang}/packages/${brand.applicationId}/`,
};

/** Obtainium adds the GitHub releases and checks each APK is signed with Sukrara's key (docs/20-publishing.md). */
export const OBTAINIUM = {
	add: `obtainium://add/${GITHUB}`,
	home: 'https://obtainium.imranr.dev/',
};

/** The Homebrew cask (mauriciabad/homebrew-tap, updated by .github/workflows/homebrew-tap.yml). */
export const HOMEBREW = 'brew install --cask mauriciabad/tap/sukrara';
/** Homebrew's home page, in the page's language. */
export const HOMEBREW_HOME = (lang: string) => `https://brew.sh/${lang === 'es' ? 'es/' : ''}`;

/**
 * What a download can be checked against. Android: the SHA-256 of the certificate every Sukrara APK is signed with (the
 * GitHub and the Google Play version alike; tools/release/android-cert.sha256, also in the App Links file), as
 * AA:BB:… Mac: the Developer ID the app is signed with and notarized under.
 */
export const SIGNING = {
	androidCert: androidCert.trim().match(/../g)!.join(':'),
	macIdentity: 'Developer ID Application: Maurici Abad Gutierrez (QWUV9H4A3G)',
};
