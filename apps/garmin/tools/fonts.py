# /// script
# requires-python = ">=3.9"
# dependencies = ["freetype-py==2.5.1", "pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sukrara Aura's fonts and icons for Connect IQ, built from the sources in the repo (reproducible, no hand steps).

  uv run apps/garmin/tools/fonts.py          writes apps/garmin/resources/fonts/* and source/AuraFontTable.mc
  uv run apps/garmin/tools/fonts.py --check  fails if the committed output differs from a fresh build

Fonts: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/), one Connect IQ bitmap font (AngelCode BMFont text .fnt +
an 8-bit alpha PNG, white glyphs) per size and weight of SPEC.md §2, rendered by FreeType without hinting (the way the
reference's browser draws it), the variable font set to the weight. Sizes are the reference's (cap / 0.668, to 0.1 px).
The fonts drawn along the arcs (date, bottom fields) are also bitmaps: their glyph boxes and advances go into
AuraFontTable.mc so the face can place each glyph on the curve (drawBitmap2 with a rotation).

Icons: the SVG paths of SPEC.md §2 rendered white on transparent by rsvg-convert at each size the face draws them, so
the face tints and rotates them.
"""
import io
import json
import os
import re
import subprocess
import sys

import freetype
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(APP))
TTF = os.path.join(APP, "fonts", "AtkinsonHyperlegibleNext-wght.ttf")
OUT = os.path.join(APP, "resources", "fonts")
ICONS_OUT = os.path.join(APP, "resources", "icons")
TABLE = os.path.join(APP, "source", "AuraFontTable.mc")
NB = " "

DIGITS = "0123456789"
UPPER_ES = "ÁÉÍÓÚÑ"
LATIN = "".join(chr(c) for c in range(0x20, 0x7F)) + "°·áéíóúñüÁÉÍÓÚÑÜ¿¡" + NB
BATTERY = DIGITS + "dh%· " + NB
CAP = 0.668


def r1(x):
    return round(x * 10) / 10


# key, font px, weight, glyphs, drawn along an arc
FONTS = (
    [("t%d" % c, r1(c / CAP), 600, DIGITS + ":", False) for c in (62, 64, 66, 68)]
    + [("t%do" % c, r1(c / CAP), 600, DIGITS + ":", False) for c in (62, 64, 66, 68)]  # always-on: outlined
    + [("v%d" % c, r1(c / CAP), 600, DIGITS + ".LOHI", False) for c in (54, 56, 58, 60)]
    + [("g%d" % c, r1(c / CAP), 600, LATIN, False) for c in (36, 40)]
    + [
        ("d26", 26, 500, "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + UPPER_ES + " " + NB, True),
        ("d26b", 26, 700, DIGITS, True),
        ("l20s", 20, 600, BATTERY, False),
        ("l20b", 20, 700, BATTERY, False),
        ("l22s", 22, 600, BATTERY, False),
        ("l22b", 22, 700, BATTERY, False),
        ("l24m", 24, 500, LATIN, True),
        ("l24s", 24, 600, LATIN, True),
        ("l25m", 25, 500, LATIN, False),
        ("l25s", 25, 600, LATIN, False),
        ("l26m", 26, 500, LATIN, False),
        ("l30s", 30, 600, LATIN, False),
        ("l32s", 32, 600, LATIN, False),
    ]
)

ASCENT, DESCENT = 0.984, 0.316  # hhea, per em
ATLAS_W = 512


def face(size, weight):
    f = freetype.Face(TTF)
    f.set_var_design_coords((weight,))
    f.set_char_size(int(round(size * 64)))
    return f


OUTLINE = 2.2  # the always-on time's stroke (SPEC.md §6.3), centred on the outline


def render(size, weight, chars, outline=False):
    """Each glyph: (char, alpha image, left, top, advance px float)."""
    f = face(size, weight)
    out = []
    for ch in chars:
        if outline:
            f.load_char(ch, freetype.FT_LOAD_NO_BITMAP | freetype.FT_LOAD_NO_HINTING)
            stroker = freetype.Stroker()
            stroker.set(int(round(OUTLINE / 2 * 64)), freetype.FT_STROKER_LINECAP_ROUND, freetype.FT_STROKER_LINEJOIN_ROUND, 0)
            glyph = f.glyph.get_glyph()
            glyph.stroke(stroker, True)
            bg = glyph.to_bitmap(freetype.FT_RENDER_MODE_NORMAL, freetype.Vector(0, 0), True)
            bm, left, top = bg.bitmap, bg.left, bg.top
        else:
            f.load_char(ch if ch != NB else " ", freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_HINTING)
            bm, left, top = f.glyph.bitmap, f.glyph.bitmap_left, f.glyph.bitmap_top
        g = f.glyph
        img = Image.new("L", (max(1, bm.width), max(1, bm.rows)), 0)
        if bm.width and bm.rows:
            img = Image.frombytes("L", (bm.width, bm.rows), bytes(bm.buffer)) if bm.pitch == bm.width else \
                Image.frombytes("L", (bm.width, bm.rows), b"".join(bytes(bm.buffer[r * bm.pitch:r * bm.pitch + bm.width]) for r in range(bm.rows)))
        out.append((ch, img, left, top, g.linearHoriAdvance / 65536.0, bm.width, bm.rows))
    return out


def pack(glyphs):
    x = y = row = 0
    pos = []
    for ch, img, left, top, adv, w, h in glyphs:
        w2, h2 = max(1, w), max(1, h)
        if x + w2 + 1 > ATLAS_W:
            x, y, row = 0, y + row + 1, 0
        pos.append((x, y))
        x += w2 + 1
        row = max(row, h2)
    height = y + row + 1
    sheet = Image.new("RGBA", (ATLAS_W, height), (255, 255, 255, 0))
    for (ch, img, *_), (px, py) in zip(glyphs, pos):
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        white.putalpha(img)
        sheet.paste(white, (px, py))
    return sheet, pos


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def build_fonts():
    files = {}
    table = []
    for key, size, weight, chars, arc in FONTS:
        glyphs = render(size, weight, chars, key.endswith("o"))
        sheet, pos = pack(glyphs)
        base = int(round(size * ASCENT))
        line = int(round(size * (ASCENT + DESCENT)))
        lines = [
            'info face="AtkinsonHyperlegibleNext-%d" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 '
            'aa=1 padding=0,0,0,0 spacing=1,1' % (weight, round(size)),
            "common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0" % (line, base, sheet.width, sheet.height),
            'page id=0 file="%s.png"' % key,
            "chars count=%d" % len(glyphs),
        ]
        metrics = []
        for (ch, img, left, top, adv, w, h), (px, py) in zip(glyphs, pos):
            lines.append("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15"
                         % (ord(ch), px, py, w, h, left, base - top, int(round(adv))))
            metrics.append((ch, px, py, w, h, left, base - top, adv))
        files[key + ".fnt"] = ("\n".join(lines) + "\n").encode()
        files[key + ".png"] = png_bytes(sheet)
        table.append((key, size, weight, base, chars, arc, metrics))
    xml = ['<!-- Sukrara — https://github.com/mauriciabad/sukrara -->',
           '<!-- Copyright (C) 2026 Maurici Abad -->',
           '<!-- SPDX-License-Identifier: GPL-3.0-or-later -->',
           '<!-- Generated by apps/garmin/tools/fonts.py: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/OFL.txt). -->',
           "<resources>"]
    for key, size, weight, base, chars, arc, metrics in table:
        xml.append('    <font id="%s" filename="%s.fnt" antialias="true"/>' % (key, key))
        if arc:
            xml.append('    <bitmap id="%s_atlas" filename="%s.png" dithering="none" packingFormat="png"/>' % (key, key))
    xml.append("</resources>")
    files["fonts.xml"] = ("\n".join(xml) + "\n").encode()
    return files, table


def mc_string(s):
    return '"' + "".join(c if c not in '"\\' else "\\" + c for c in s) + '"'


def build_table(table):
    """AuraFontTable.mc: each font's resource, its baseline (px from the top of the line) and, for the arc fonts, every
    glyph's box in the atlas: 10 bytes per glyph (x, y as 16 bit; w, h; xoffset, yoffset as signed bytes; advance in
    1/16 px as 16 bit)."""
    out = [
        "// Sukrara — https://github.com/mauriciabad/sukrara",
        "// Copyright (C) 2026 Maurici Abad",
        "// SPDX-License-Identifier: GPL-3.0-or-later",
        "// Generated by apps/garmin/tools/fonts.py (do not edit): the fonts of SPEC.md §2, their baselines and the arc fonts'",
        "// glyph boxes.",
        "import Toybox.Lang;",
        "import Toybox.WatchUi;",
        "",
        "module AuraFontTable {",
        "    // key → [font resource, baseline px from the line's top, font px]",
        "    function font(key as String) as Array {",
    ]
    first = True
    for key, size, weight, base, chars, arc, metrics in table:
        out.append("        %sif (key.equals(%s)) {" % ("" if first else "} else ", mc_string(key)))
        out.append("            return [Rez.Fonts.%s, %d, %s];" % (key, base, repr(float(size))))
        first = False
    out.append("        }")
    out.append("        return [Rez.Fonts.l24m, 24, 24.0];")
    out.append("    }")
    out.append("")
    out.append("    // the arc fonts: [atlas bitmap, glyphs (String), metrics (ByteArray, 10 bytes a glyph)]")
    out.append("    function arc(key as String) as Array {")
    first = True
    for key, size, weight, base, chars, arc, metrics in table:
        if not arc:
            continue
        bs = []
        for ch, px, py, w, h, xo, yo, adv in metrics:
            a = int(round(adv * 16))
            bs += [px >> 8, px & 255, py >> 8, py & 255, w, h, xo & 255, yo & 255, a >> 8, a & 255]
        out.append("        %sif (key.equals(%s)) {" % ("" if first else "} else ", mc_string(key)))
        out.append("            return [")
        out.append("                Rez.Drawables.%s_atlas," % key)
        out.append("                %s," % mc_string(chars))
        body = ", ".join("0x%02x" % b for b in bs)
        wrapped = re.sub(r"((?:0x[0-9a-f]{2}, ){16})", r"\1\n                  ", body)
        out.append("                [" + wrapped + "]b,")
        out.append("            ];")
        first = False
    out.append("        }")
    out.append("        return [];")
    out.append("    }")
    out.append("}")
    return ("\n".join(out) + "\n").encode()


# SPEC.md §2 icons (aura.js icons()) and the pill's bells; sizes the face draws them at
ICONS = {
    "sun": ("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0", 2),
    "steps": ("M4.6 8.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM5.8 16.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0zM12.6 11.5a3.4 5.3 0 1 0 6.8 0a3.4 5.3 0 1 0-6.8 0zM13.8 19.6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z", 0),
    "sunset": ("M3 18h18M7 18a5 5 0 0 1 10 0M12 3v7M9 7.5l3 3 3-3", 2),
    "bolt": ("M13.5 2L5.5 13.5h5.2L9.8 22l8.2-12h-5.3z", 0),
    "mountain": ("M2 20L9.5 7l4 6.5 2.3-3.6L22 20z", 0),
    "heart": ("M12 20.5s-7.5-4.6-7.5-10.4A4.1 4.1 0 0 1 12 7.6a4.1 4.1 0 0 1 7.5 2.5c0 5.8-7.5 10.4-7.5 10.4z", 0),
    "bell": ("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0", 2),
    "ring": ("M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16M12 4a8 8 0 0 1 7.6 10.5", 2),
    "globe": ("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18M3 12h18M12 3c2.6 2.7 3.8 5.7 3.8 9s-1.2 6.3-3.8 9M12 3c-2.6 2.7-3.8 5.7-3.8 9s1.2 6.3 3.8 9", 2),
    "stairs": ("M3 20h5v-5h5v-5h5V5h3", 2),
    "phoneOff": ("M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM3 3l18 18", 2),
    "sensor": ("M21.3 12a9.3 9.3 0 1 1-18.6 0a9.3 9.3 0 1 1 18.6 0zM18.7 12a6.7 6.7 0 1 0-13.4 0a6.7 6.7 0 1 0 13.4 0zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", 0),
    "rapid": ("M2 4.2H22L12 20.9Z", 0),
    "long": ("M3.5 5.4H20.5L12 19.6Z", 2.8),
    "carbs": ("M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", 0),
}
BELLS = {
    "paused": "M370-320h220v-72H480l110-136v-72H370v72h110L370-392v72ZM160-200v-80h80v-280q0-83 50-147.5T420-792v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v280h80v80H160Zm320-300Zm0 420q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80ZM320-280h320v-280q0-66-47-113t-113-47q-66 0-113 47t-47 113v280Z",
    "off": "M160-200v-80h80v-280q0-33 8.5-65t25.5-61l60 60q-7 16-10.5 32.5T320-560v280h248L56-792l56-56 736 736-56 56-146-144H160Zm560-154-80-80v-126q0-66-47-113t-113-47q-26 0-50 8t-44 24l-58-58q20-16 43-28t49-18v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v206Zm-276-50Zm36 324q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80Zm33-481Z",
}
# the icons' sizes: bottom fields 20, top field round(TF·0.83) for TF 24 / 25 / 26 = 20 / 21 / 22, pill marks 23.6
ICON_SIZES = (20, 21, 22, 24)
PAD = 2  # transparent margin around each icon so a rotation never clips it


def rsvg(svg, px):
    return subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), "-f", "png"], input=svg.encode(),
                          capture_output=True, check=True).stdout


def build_icons():
    """One atlas of every icon at every size (white, alpha): icons.png, and its boxes in AuraFontTable.mc."""
    tiles = []
    for name, (d, sw) in ICONS.items():
        for s in ICON_SIZES:
            box = s + 2 * PAD
            scale = s / 24.0
            paint = ('fill="none" stroke="#FFFFFF" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % sw
                     if sw else 'fill="#FFFFFF" fill-rule="evenodd"')
            svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
                   '<g transform="translate(%d %d) scale(%s)"><path d="%s" %s/></g></svg>'
                   % (box, box, box, box, PAD, PAD, repr(scale), d, paint))
            tiles.append((name, s, Image.open(io.BytesIO(rsvg(svg, box))).convert("RGBA")))
    for name, d in BELLS.items():
        s = 24
        box = s + 2 * PAD
        # Material symbols: 960 box, y from -960; the reference scales by 0.0246 (23.6 px)
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
               '<g transform="translate(%s %s) scale(0.0246) translate(0 960)"><path d="%s" fill="#FFFFFF"/></g></svg>'
               % (box, box, box, box, PAD + 0.2, PAD + 0.2, d))
        tiles.append(("bell_" + name, s, Image.open(io.BytesIO(rsvg(svg, box))).convert("RGBA")))
    x = y = row = 0
    pos = []
    for name, s, img in tiles:
        if x + img.width > 256:
            x, y, row = 0, y + row + 1, 0
        pos.append((x, y))
        x += img.width + 1
        row = max(row, img.height)
    sheet = Image.new("RGBA", (256, y + row + 1), (255, 255, 255, 0))
    for (name, s, img), (px, py) in zip(tiles, pos):
        # white, the coverage in alpha
        a = img.getchannel("A")
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        white.putalpha(a)
        sheet.paste(white, (px, py))
    boxes = [(name, s, px, py, img.width) for (name, s, img), (px, py) in zip(tiles, pos)]
    return png_bytes(sheet), boxes


def icon_table(boxes):
    names = []
    for name, s, px, py, w in boxes:
        if name not in names:
            names.append(name)
    out = ["", "    // icons.png: every icon of SPEC.md §2 at each size, white with alpha, %d px margin; [x, y, box]" % PAD,
           "    function icon(name as String, size as Number) as Array? {"]
    first = True
    for name in names:
        out.append("        %sif (name.equals(%s)) {" % ("" if first else "} else ", mc_string(name)))
        sizes = [(s, px, py, w) for n, s, px, py, w in boxes if n == name]
        for i, (s, px, py, w) in enumerate(sizes):
            cond = "size <= %d" % s if i < len(sizes) - 1 else "true"
            out.append("            %sif (%s) { return [%d, %d, %d]; }" % ("" if i == 0 else "else ", cond, px, py, w))
        first = False
    out.append("        }")
    out.append("        return null;")
    out.append("    }")
    return out


def outputs():
    files, table = build_fonts()
    icons_png, boxes = build_icons()
    files_icons = {"icons.png": icons_png, "icons.xml": (
        "<!-- Sukrara — https://github.com/mauriciabad/sukrara -->\n<!-- Copyright (C) 2026 Maurici Abad -->\n"
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->\n<!-- Generated by apps/garmin/tools/fonts.py. -->\n"
        '<resources>\n    <bitmap id="icons" filename="icons.png" dithering="none" packingFormat="png"/>\n</resources>\n').encode()}
    src = build_table(table).decode().rstrip("\n").rstrip("}").rstrip("\n").split("\n")
    src += icon_table(boxes) + ["}", ""]
    out = {os.path.join(OUT, k): v for k, v in files.items()}
    out.update({os.path.join(ICONS_OUT, k): v for k, v in files_icons.items()})
    out[TABLE] = "\n".join(src).encode()
    return out


def main(argv):
    out = outputs()
    if "--check" in argv:
        bad = [p for p, b in out.items() if not os.path.exists(p) or open(p, "rb").read() != b]
        if bad:
            print("fonts.py: out of date (run uv run apps/garmin/tools/fonts.py):\n  " + "\n  ".join(os.path.relpath(p, ROOT) for p in bad))
            return 1
        print("fonts.py: up to date")
        return 0
    for d in (OUT, ICONS_OUT):
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            if os.path.join(d, f) not in out:
                os.remove(os.path.join(d, f))
    for p, b in out.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as fh:
            fh.write(b)
    print("fonts.py: wrote %d files" % len(out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
