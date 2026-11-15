// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// sukrara.mauri.app: Astro + Starlight, static, published by .github/workflows/website.yml. How it fits together:
// website/README.md.
import { fileURLToPath } from 'node:url';
import starlight from '@astrojs/starlight';
import { defineConfig } from 'astro/config';
import starlightSidebarTopics from 'starlight-sidebar-topics';
import mount from './src/lib/mount';
import { repoDocs } from './src/lib/repo-docs';
import { GITHUB, SITE } from './src/site';

const docs = repoDocs();
const slugs = (section: string) => docs.filter((d) => d.section === section).map((d) => d.id);

export default defineConfig({
	site: SITE,
	trailingSlash: 'always',
	integrations: [
		starlight({
			title: 'Sukrara',
			description: 'Live glucose from your CGM on your phone, your Mac and your family’s devices. No account, no cloud.',
			logo: { src: '../assets/brand/sukrara/web/favicon.svg', alt: '' },
			favicon: '/favicon.svg',
			head: [
				{ tag: 'link', attrs: { rel: 'icon', href: '/favicon-32.png', sizes: '32x32', type: 'image/png' } },
				{ tag: 'link', attrs: { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' } },
				{ tag: 'meta', attrs: { name: 'theme-color', content: '#0B2E5E' } },
			],
			social: [{ icon: 'github', label: 'GitHub', href: GITHUB }],
			editLink: { baseUrl: `${GITHUB}/edit/next/website/` },
			lastUpdated: true,
			titleDelimiter: '·',
			defaultLocale: 'root',
			locales: {
				root: { label: 'English', lang: 'en' },
				es: { label: 'Español', lang: 'es' },
			},
			customCss: ['@fontsource-variable/nunito', './src/styles/theme.css'],
			markdown: { processedDirs: ['../docs/'] },
			routeMiddleware: './src/route-data.ts',
			components: {
				Banner: './src/components/overrides/Banner.astro',
				Footer: './src/components/overrides/Footer.astro',
				Hero: './src/components/overrides/Hero.astro',
				SocialIcons: './src/components/overrides/SocialIcons.astro',
			},
			plugins: [
				starlightSidebarTopics([
					{
						label: { en: 'Sukrara', es: 'Sukrara' },
						link: '/android/',
						icon: 'heart',
						items: [
							{ label: 'Apps', translations: { es: 'Apps' }, items: ['android', 'mac'] },
							{
								label: 'More',
								translations: { es: 'Más' },
								items: ['open-glucose-stream', 'sounds', 'privacy'],
							},
						],
					},
					{
						label: { en: 'Developers & research', es: 'Desarrollo e investigación' },
						link: '/developers/',
						icon: 'open-book',
						items: [
							'developers',
							{ label: 'Developer docs', translations: { es: 'Documentación' }, items: slugs('developers') },
							{ label: 'Research', translations: { es: 'Investigación' }, items: slugs('research') },
						],
					},
				]),
			],
		}),
		// Files that stay where the repository keeps them: the reports' images and the brand's web icons.
		mount({
			'/img/': '../docs/img/',
			'/favicon.svg': '../assets/brand/sukrara/web/favicon.svg',
			'/favicon-32.png': '../assets/brand/sukrara/web/favicon-32.png',
			'/apple-touch-icon.png': '../assets/brand/sukrara/web/apple-touch-icon.png',
		}),
	],
	// JPEG XL first, then AVIF, then PNG (src/lib/image-service.ts; never WebP).
	image: { service: { entrypoint: './src/lib/image-service.ts' } },
	vite: {
		// Screenshots are imported from where the apps' tests write them (src/lib/screens.ts).
		resolve: { alias: { '@repo': fileURLToPath(new URL('..', import.meta.url)) } },
		server: { fs: { allow: ['..'] } },
	},
});
