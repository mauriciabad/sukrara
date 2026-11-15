// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Publishes files that live elsewhere in the repository at a URL of the site, without copying them into the website:
 * served from their place by the dev server, copied into the build output after a build.
 *
 *   mount({ '/img/': '../docs/img/', '/favicon.svg': '../assets/brand/sukrara/web/favicon.svg' })
 *
 * A key ending in `/` publishes a folder, any other key one file. Paths are relative to website/.
 */
import { cpSync, createReadStream, existsSync, statSync } from 'node:fs';
import { extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { AstroIntegration } from 'astro';

const TYPES: Record<string, string> = {
	'.png': 'image/png',
	'.jpg': 'image/jpeg',
	'.jpeg': 'image/jpeg',
	'.gif': 'image/gif',
	'.webp': 'image/webp',
	'.svg': 'image/svg+xml',
	'.m4a': 'audio/mp4',
	'.mp4': 'video/mp4',
	'.json': 'application/json',
	'.xml': 'application/xml',
};

export default function mount(map: Record<string, string>): AstroIntegration {
	let root = '';
	const source = (url: string) => {
		for (const [at, from] of Object.entries(map)) {
			const path = decodeURIComponent(url.split(/[?#]/)[0]);
			if (at.endsWith('/') ? path.startsWith(at) : path === at) {
				const file = resolve(root, from, at.endsWith('/') ? path.slice(at.length) : '');
				if (!file.startsWith(resolve(root, from))) return undefined; // no ../ out of the folder
				return existsSync(file) && statSync(file).isFile() ? file : undefined;
			}
		}
	};
	return {
		name: 'sukrara-mount',
		hooks: {
			'astro:config:setup': ({ config }) => {
				root = fileURLToPath(config.root);
			},
			'astro:server:setup': ({ server }) => {
				server.middlewares.use((req, res, next) => {
					const file = req.url && source(req.url);
					if (!file) return next();
					res.setHeader('Content-Type', TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream');
					createReadStream(file).pipe(res);
				});
			},
			'astro:build:done': ({ dir }) => {
				for (const [at, from] of Object.entries(map)) {
					cpSync(resolve(root, from), fileURLToPath(new URL('.' + at, dir)), { recursive: true });
				}
			},
		},
	};
}
