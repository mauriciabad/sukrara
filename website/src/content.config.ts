// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import { readFileSync } from 'node:fs';
import { defineCollection } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';
import { z } from 'astro/zod';
import { topicSchema } from 'starlight-sidebar-topics/schema';
import { withRepoDocs } from './lib/repo-docs';

/** The site components' own UI strings (`site.*`): every key of en.json is required in every language. */
const siteKeys = Object.keys(JSON.parse(readFileSync(new URL('content/i18n/en.json', import.meta.url), 'utf8')));
const siteStrings = z.object(Object.fromEntries(siteKeys.filter((k) => k.startsWith('site.')).map((k) => [k, z.string()])));

export const collections = {
	// The site's pages (src/content/docs, Spanish in es/) and the repository's docs/ (lib/repo-docs.ts).
	docs: defineCollection({ loader: withRepoDocs(docsLoader()), schema: docsSchema({ extend: topicSchema }) }),
	i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema({ extend: siteStrings }) }),
};
