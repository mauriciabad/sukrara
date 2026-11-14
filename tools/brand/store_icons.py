#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Renders the square full-bleed icons: the Play Store icon and the web apple-touch-icon (Python 3.9 stdlib + swift).

The icon is drawn by the frozen generator poc/spikes/brand/brand.py, which renders both from the whole 108-unit
adaptive canvas, bleed included, so its glyph comes out at 2/3 of the launcher's size (#210). This script imports
brand.py's shapes read-only, crops to the 72-unit area launchers show (`18 18 72 72`, as Android Studio's Image Asset
Studio does) and rasterizes with svg2png.swift (brand.py's own renderer no longer builds). Nothing is written to poc/.

  python3 -I tools/brand/store_icons.py      → assets/brand/sukrara/{android/play-store-512.png,web/apple-touch-icon.png}
"""
import importlib.util
import os
import shutil
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BRAND_PY = os.path.join(ROOT, "poc", "spikes", "brand", "brand.py")
OUT = os.path.join(ROOT, "assets", "brand", "sukrara")
# (output, px): square, full bleed; the store / the OS applies its own mask
TARGETS = [(os.path.join("android", "play-store-512.png"), 512), (os.path.join("web", "apple-touch-icon.png"), 180)]


def load_brand():
    spec = importlib.util.spec_from_file_location("brand", BRAND_PY)
    brand = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(brand)
    return brand


def store_svg(brand, c):
    glyph = brand.svg_shapes(c["shapes"], brand.colors(c))
    return brand.svg("18 18 72 72", f'<rect x="18" y="18" width="72" height="72" fill="url(#bg)"/>{glyph}', brand.bg_defs(c))


def main():
    build = tempfile.mkdtemp(prefix="sukrara-brand-")
    try:
        brand = load_brand()
        c = next(x for x in brand.CONCEPTS if x["id"] == "sukrara")
        src = os.path.join(build, "store.svg")
        with open(src, "w") as fh:
            fh.write(store_svg(brand, c))
        tool = os.path.join(build, "svg2png")
        subprocess.run(["swiftc", "-O", "-o", tool, os.path.join(ROOT, "tools", "brand", "svg2png.swift")], check=True)
        for path, px in TARGETS:
            subprocess.run([tool, src, os.path.join(OUT, path), str(px)], check=True)
            print(f"wrote assets/brand/sukrara/{path} ({px} px)")
    finally:
        shutil.rmtree(build, ignore_errors=True)


if __name__ == "__main__":
    main()
