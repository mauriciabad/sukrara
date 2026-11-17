// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * Astro's sharp image service, plus JPEG XL: the site's pictures are JPEG XL first, then AVIF, then PNG (the
 * <Screenshot> component asks for them in that order; never WebP). Sharp's prebuilt libvips has no JPEG XL encoder,
 * so sharp resizes the picture as usual and libjxl, compiled to WebAssembly (@jsquash/jxl), encodes it.
 */
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import type { LocalImageService } from 'astro';
import sharpService from 'astro/assets/services/sharp';
import sharp from 'sharp';

/** libjxl's "visually lossless" range; app pictures are flat colours and text, where lower shows. */
const JXL_QUALITY = 85;

let encoder: Promise<(typeof import('@jsquash/jxl/encode.js'))['default']> | undefined;

function jxlEncoder() {
	encoder ??= (async () => {
		const { default: encode, init } = await import('@jsquash/jxl/encode.js');
		// In Node the module can't fetch its own .wasm: hand it over, compiled.
		const require = createRequire(import.meta.url);
		const wasm = await readFile(require.resolve('@jsquash/jxl/codec/enc/jxl_enc.wasm'));
		await init(await WebAssembly.compile(wasm));
		return encode;
	})();
	return encoder;
}

const service: LocalImageService = {
	...sharpService,
	async transform(inputBuffer, transform, config, ...rest) {
		if (transform.format !== 'jxl') return sharpService.transform(inputBuffer, transform, config, ...rest);
		// Resized (and everything else Astro asks for) by sharp, losslessly, then encoded once.
		const { data: png } = await sharpService.transform(inputBuffer, { ...transform, format: 'png' }, config, ...rest);
		const { data, info } = await sharp(png).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
		const encode = await jxlEncoder();
		const jxl = await encode(
			{ data: new Uint8ClampedArray(data.buffer, data.byteOffset, data.length), width: info.width, height: info.height, colorSpace: 'srgb' },
			{ quality: JXL_QUALITY },
		);
		return { data: new Uint8Array(jxl), format: 'jxl' };
	},
};

export default service;
