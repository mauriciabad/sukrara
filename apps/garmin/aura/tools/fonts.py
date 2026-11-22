# /// script
# requires-python = ">=3.9"
# dependencies = ["freetype-py==2.5.1", "pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sukrara Aura's fonts and icons for Connect IQ, built from the sources in the repo (reproducible, no hand steps).

  uv run apps/garmin/aura/tools/fonts.py          writes apps/garmin/aura/resources/fonts/* and source/AuraFontTable.mc
  uv run apps/garmin/aura/tools/fonts.py --check  fails if the committed output differs from a fresh build

Fonts: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/), one atlas per size and weight of SPEC.md §2 (white glyphs
with 8-bit alpha, a PNG bitmap resource), rendered by FreeType without hinting (the way the reference's browser draws it),
the variable font set to the weight. Sizes are the reference's (cap / 0.668, to 0.1 px). Each glyph's box and advance go
into AuraFontTable.mc and the face draws every glyph itself (Dc.drawBitmap2, tinted; rotated along the arcs). Not
Connect IQ font resources: their antialias="true" keeps only a few grey levels (the simulator's edges showed about 8),
a tinted PNG bitmap keeps all 256 (docs/DEVELOPMENT.md › Garmin, "Fonts").
The halo (SPEC.md §6.13) has its own atlases for the time and the value: the glyphs grown by 6.5 px (the reference's
13 px round stroke), which the face draws in black over the dial.

Icons: the SVG paths of SPEC.md §2 rendered white on transparent by rsvg-convert at each size the face draws them, so
the face tints and rotates them.
"""
import base64
import io
import json
import os
import re
import subprocess
import sys

import freetype
from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
GARMIN = os.path.dirname(APP)  # apps/garmin
ROOT = os.path.dirname(os.path.dirname(GARMIN))
TTF = os.path.join(GARMIN, "fonts", "AtkinsonHyperlegibleNext-wght.ttf")
OUT = os.path.join(APP, "resources", "fonts")
ICONS_OUT = os.path.join(APP, "resources", "icons")
TABLE = os.path.join(APP, "source", "AuraFontTable.mc")
NB = " "

DIGITS = "0123456789"
UPPER_ES = "ÁÉÍÓÚÑ"
LATIN = "".join(chr(c) for c in range(0x20, 0x7F)) + "°·áéíóúñüÁÉÍÓÚÑÜ¿¡" + NB
BATTERY = DIGITS + "dhm%·- " + NB + "inowar"  # the side labels: days, hours, percent, the warm-up's minutes (now / ahora), --
# the atlases' sets (their glyph tables cost the heap): the field texts' letters, digits and marks; the no-reading
# time's digits and the weekdays and months it may show (Freshness.timeText, en and es)
FIELDS = "".join(c for c in LATIN if c.isalnum() or c in " ,.:%°·-'/+()" + NB)
GAP = "".join(sorted(set(DIGITS + ": " + NB + "SunMonTueWedThuFriSatdomlunmarmiéjueviesábJanFebMarAprMayJunJulAugSep"
                         "OctNovDecenefebmarabrmayjunjulagosepoctnovdic")))
CAP = 0.668


def r1(x):
    return round(x * 10) / 10


# key, font px, weight, glyphs, drawn along an arc (unused: every font is an atlas)
FONTS = (
    [("t%d" % c, r1(c / CAP), 600, DIGITS + ":", False) for c in (62, 64, 66, 68, 70, 72, 74, 76)]
    + [("t%do" % c, r1(c / CAP), 600, DIGITS + ":", False) for c in (62, 64, 66, 68, 70, 72, 74, 76)]  # always-on: outlined
    + [("t%dh" % c, r1(c / CAP), 600, DIGITS + ":", False) for c in (62, 64, 66, 68, 70, 72, 74, 76)]  # the halo: grown 6.5 px
    + [("v%d" % c, r1(c / CAP), 600, DIGITS + ".LOHI", False) for c in (54, 56, 58, 60)]
    + [("v%dh" % c, r1(c / CAP), 600, DIGITS + ".LOHI", False) for c in (54, 56, 58, 60)]
    + [("g%d" % c, r1(c / CAP), 600, GAP, False) for c in (36, 40)]
    + [
        ("d26", 26, 500, "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + UPPER_ES + " " + NB, True),
        ("d26b", 26, 700, DIGITS, True),
        ("l20s", 20, 600, BATTERY, False),
        ("l20b", 20, 700, BATTERY, False),
        ("l22s", 22, 600, BATTERY, False),
        ("l22b", 22, 700, BATTERY, False),
        ("l24m", 24, 500, FIELDS, True),
        ("l24s", 24, 600, FIELDS, True),
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
HALO = 6.5  # the halo's growth around the time and the value (SPEC.md §6.13: stroked 13 px, round joins)


def to_image(bm):
    if not (bm.width and bm.rows):
        return Image.new("L", (1, 1), 0)
    if bm.pitch == bm.width:
        return Image.frombytes("L", (bm.width, bm.rows), bytes(bm.buffer))
    return Image.frombytes("L", (bm.width, bm.rows), b"".join(bytes(bm.buffer[r * bm.pitch:r * bm.pitch + bm.width]) for r in range(bm.rows)))


def stroked(f, ch, radius):
    """The glyph's outline stroked radius px each side (round caps and joins): (bitmap, left, top)."""
    f.load_char(ch, freetype.FT_LOAD_NO_BITMAP | freetype.FT_LOAD_NO_HINTING)
    stroker = freetype.Stroker()
    stroker.set(int(round(radius * 64)), freetype.FT_STROKER_LINECAP_ROUND, freetype.FT_STROKER_LINEJOIN_ROUND, 0)
    glyph = f.glyph.get_glyph()
    glyph.stroke(stroker, True)
    bg = glyph.to_bitmap(freetype.FT_RENDER_MODE_NORMAL, freetype.Vector(0, 0), True)
    return to_image(bg.bitmap), bg.left, bg.top


def render(size, weight, chars, kind=""):
    """Each glyph: (char, alpha image, left, top, advance px float, width, height). kind: "" filled, "o" outlined (the
    always-on time), "h" the halo (filled and grown by HALO)."""
    f = face(size, weight)
    out = []
    for ch in chars:
        if kind == "o":
            img, left, top = stroked(f, ch, OUTLINE / 2)
        elif kind == "h":
            img, left, top = stroked(f, ch, HALO)
            f.load_char(ch, freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_HINTING)
            fill = to_image(f.glyph.bitmap)
            layer = Image.new("L", img.size, 0)
            layer.paste(fill, (f.glyph.bitmap_left - left, top - f.glyph.bitmap_top))
            img = ImageChops.lighter(img, layer)
        else:
            f.load_char(ch if ch != NB else " ", freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_HINTING)
            bm, left, top = f.glyph.bitmap, f.glyph.bitmap_left, f.glyph.bitmap_top
            img = to_image(bm)
        bw, bh = (img.width, img.height) if (img.width > 1 or img.height > 1) else (0, 0)
        out.append((ch, img, left, top, f.glyph.linearHoriAdvance / 65536.0, bw, bh))
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


def atlas_font(key, arc):
    """Drawn glyph by glyph from an 8-bit alpha atlas: the big numbers (time, value, no-reading time; their outline and
    halo variants) and the arc fonts. The small straight texts stay Connect IQ font resources: their glyph tables would
    cost the face's heap ~1.5 KB a font, and at 20–32 px their edges show the few grey levels far less."""
    return arc or key[0] in "tvg"


def build_fonts():
    files = {}
    table = []
    for key, size, weight, chars, _arc in FONTS:
        kind = key[-1] if key[-1] in "oh" else ""
        glyphs = render(size, weight, chars, kind)
        sheet, pos = pack(glyphs)
        base = int(round(size * ASCENT))
        metrics = []
        for (ch, img, left, top, adv, w, h), (px, py) in zip(glyphs, pos):
            metrics.append((ch, px, py, w, h, left, base - top, adv))
        files[key + ".png"] = png_bytes(sheet)
        if not atlas_font(key, _arc):
            # a Connect IQ font resource: BMFont text + the PNG
            line = int(round(size * (ASCENT + DESCENT)))
            lines = [
                'info face="AtkinsonHyperlegibleNext-%d" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 '
                'smooth=1 aa=1 padding=0,0,0,0 spacing=1,1' % (weight, round(size)),
                "common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0" % (line, base, sheet.width, sheet.height),
                'page id=0 file="%s.png"' % key,
                "chars count=%d" % len(glyphs),
            ]
            for ch, px, py, w, h, xo, yo, adv in metrics:
                lines.append("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15"
                             % (ord(ch), px, py, w, h, xo, yo, int(round(adv))))
            files[key + ".fnt"] = ("\n".join(lines) + "\n").encode()
            table.append((key, size, weight, base, chars, None))
            continue
        bs = []
        for ch, px, py, w, h, xo, yo, adv in metrics:
            a = int(round(adv * 16))
            assert 0 <= w < 256 and 0 <= h < 256 and -128 <= xo < 128 and -128 <= yo < 128, (key, ch)
            bs += [px >> 8, px & 255, py >> 8, py & 255, w, h, xo & 255, yo & 255, a >> 8, a & 255]
        files[key + ".json"] = (json.dumps([chars, base64.b64encode(bytes(bs)).decode(), base], ensure_ascii=False) + "\n").encode()
        table.append((key, size, weight, base, chars, metrics))
    xml = ['<!-- Sukrara — https://github.com/mauriciabad/sukrara -->',
           '<!-- Copyright (C) 2026 Maurici Abad -->',
           '<!-- SPDX-License-Identifier: GPL-3.0-or-later -->',
           '<!-- Generated by apps/garmin/aura/tools/fonts.py: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/OFL.txt). -->',
           "<resources>"]
    for key, size, weight, base, chars, metrics in table:
        if metrics is None:
            xml.append('    <font id="%s" filename="%s.fnt" antialias="true"/>' % (key, key))
            continue
        xml.append('    <bitmap id="%s" filename="%s.png" dithering="none" packingFormat="png"/>' % (key, key))
        xml.append('    <jsonData id="%s_m" filename="%s.json"/>' % (key, key))
    xml.append("</resources>")
    files["fonts.xml"] = ("\n".join(xml) + "\n").encode()
    return files, table


def mc_string(s):
    return '"' + "".join(c if c not in '"\\' else "\\" + c for c in s) + '"'


def build_table(table):
    """AuraFontTable.mc: each font's atlas and its glyph table, a jsonData resource [glyphs, metrics in base64, baseline px
    from the line's top]; metrics are 10 bytes per glyph (x, y as 16 bit; w, h; xoffset, yoffset from the line's top as
    signed bytes; advance in 1/16 px as 16 bit). Resources, not code: code counts against the face's 128 KB, a resource
    only while it's loaded."""
    out = [
        "// Sukrara — https://github.com/mauriciabad/sukrara",
        "// Copyright (C) 2026 Maurici Abad",
        "// SPDX-License-Identifier: GPL-3.0-or-later",
        "// Generated by apps/garmin/aura/tools/fonts.py (do not edit): the fonts of SPEC.md §2 as atlases and their glyph",
        "// tables (jsonData: [glyphs, metrics in base64, baseline]), and the icons.",
        "import Toybox.Lang;",
        "import Toybox.WatchUi;",
        "",
        "module AuraFontTable {",
        "    // key → [atlas bitmap, glyph table], or [font resource, baseline px from the line's top] (the small straight",
        "    // texts: Connect IQ fonts, in the graphics pool; an atlas's glyph table is in the heap while it's loaded)",
        "    function atlas(key as String) as Array {",
    ]
    atl = [t for t in table if t[5] is not None]
    fnt = [t for t in table if t[5] is None]
    keys = [t[0] for t in atl] + [t[0] for t in fnt]
    out.append("        var i = [%s].indexOf(key);" % ", ".join(mc_string(k) for k in keys))
    out.append("        if (i < 0) {")
    out.append("            return [];")
    out.append("        }")
    out.append("        if (i < %d) {" % len(atl))
    out.append("            return [[%s][i], [%s][i]];" % (", ".join("Rez.Drawables.%s" % t[0] for t in atl),
                                                         ", ".join("Rez.JsonData.%s_m" % t[0] for t in atl)))
    out.append("        }")
    out.append("        i -= %d;" % len(atl))
    out.append("        return [[%s][i], [%s][i]];" % (", ".join("Rez.Fonts.%s" % t[0] for t in fnt),
                                                     ", ".join(str(t[3]) for t in fnt)))
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
    "battery": ("M7 5h10a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H7a1.5 1.5 0 0 1-1.5-1.5v-14A1.5 1.5 0 0 1 7 5zM8.1 7.1v12.8h7.8V7.1zM9.5 2.5h5v2.5h-5zM9.7 12h4.6v6.3H9.7z", 0),
    "flame": ("M12 2.5c.7 3.3 2.7 5 4.4 7 1.6 1.9 2.6 3.8 2.6 6a7 7 0 0 1-14 0c0-2.4 1.1-4.3 2.6-5.9.2 1.8 1 3 2.4 3.6-.6-3.9.4-7.4 2-10.7z", 0),
    "stopwatch": ("M19.5 13.5a7.5 7.5 0 1 1-15 0a7.5 7.5 0 1 1 15 0M12 13.5V9.5M9.5 2.5h5M12 2.5V6M18.2 6.8l1.4-1.4", 2),
    "wheelchair": ("M11.6 4a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0M10 8v6h5.5l2.5 5.5M10 11h4.5M7.4 11.6a5.6 5.6 0 1 0 7.9 6.4", 2),
    "calendar": ("M5 5.5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1zM4 10h16M8.5 3v4.5M15.5 3v4.5", 2),
    "event": ("M5 5.5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1zM4 10h16M8.5 3v4.5M15.5 3v4.5M13.5 13.5h3v3h-3z", 2),
    "sunrise": ("M3 18h18M7 18a5 5 0 0 1 10 0M12 10.5V3.5M9 6.5l3-3 3 3", 2),
    "cloud": ("M7 18.5h10.5a3.9 3.9 0 0 0 .6-7.8A6 6 0 0 0 6.6 10.6a4 4 0 0 0 .4 7.9z", 2),
    "cloudSun": ("M8.5 2.5V4M3 8h1.5M4.6 4.1l1 1M12.4 4.1l-1 1M11.7 8.6A3.4 3.4 0 0 0 5.3 9.7M9.5 20h8.5a3.4 3.4 0 0 0 .5-6.8A5 5 0 0 0 9 13.2a3.4 3.4 0 0 0 .5 6.8z", 2),
    "rain": ("M7 14.5h10.5a3.6 3.6 0 0 0 .6-7.2A5.6 5.6 0 0 0 7 7.2a3.7 3.7 0 0 0 0 7.3zM8.5 17.5l-1 3M12.5 17.5l-1 3M16.5 17.5l-1 3", 2),
    "snow": ("M7 14.5h10.5a3.6 3.6 0 0 0 .6-7.2A5.6 5.6 0 0 0 7 7.2a3.7 3.7 0 0 0 0 7.3zM8 17.5v3M6.5 19h3M15.5 17.5v3M14 19h3", 2),
    "storm": ("M7 14.5h10.5a3.6 3.6 0 0 0 .6-7.2A5.6 5.6 0 0 0 7 7.2a3.7 3.7 0 0 0 0 7.3zM13 15.5l-2.5 3.5h3.5L11.5 22", 2),
    "fog": ("M3.5 8h17M6 12h14M3.5 16h17M8 20h9", 2),
    "thermo": ("M10 13.7V5a2 2 0 0 1 4 0v8.7a4 4 0 1 1-4 0zM12 9v8", 2),
    "gauge": ("M4.8 17.5a8.5 8.5 0 1 1 14.4 0M12 13.5l3.5-4.5M7.5 17.5h9", 2),
    "run": ("M17.8 4.5a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0M13.5 8L11 13.5M13.5 8l3 3 2.5-.5M13.5 8l-3.5 1-2.5 2.5M11 13.5l3 2.5-1 4.5M11 13.5l-2.5 3-3.5 1", 2),
    "bike": ("M9.8 16a3.8 3.8 0 1 1-7.6 0a3.8 3.8 0 1 1 7.6 0M21.8 16a3.8 3.8 0 1 1-7.6 0a3.8 3.8 0 1 1 7.6 0M6 16l3.5-6.5H16L18 16M9.5 9.5L13 16H6M16 9.5l-1-3h2.5M8 7h3", 2),
    "hourglass": ("M7 3.5h10M7 20.5h10M8 3.5c0 4.5 4 5.5 4 8.5s-4 4-4 8.5M16 3.5c0 4.5-4 5.5-4 8.5s4 4 4 8.5", 2),
    "stress": ("M2.5 13h4l2-5 3.5 10 3-8 2 3h4.5", 2),
    "drop": ("M12 3.5s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11zM14 14.5a2 2 0 1 1-4 0a2 2 0 1 1 4 0", 2),
    "breath": ("M3 9h10.5A2.5 2.5 0 1 0 11 6.5M3 13h15a2.5 2.5 0 1 1-2.5 2.5M3 17h7", 2),
    "lungs": ("M12 3.5v7.5M12 11c-1.6 0-3-1-3-2.5M12 11c1.6 0 3-1 3-2.5M8.5 7C5.5 8 4 12 4 16.5c0 2 1 3 2.5 3s3-1 3-3V9M15.5 7c3 1 4.5 5 4.5 9.5 0 2-1 3-2.5 3s-3-1-3-3V9", 2),
    "solar": ("M5 21l2-7h10l2 7zM6 17.5h12M12 14v7M12 2.5V4M5.8 5.3l1.1 1.1M18.2 5.3l-1.1 1.1M8.5 10.5a3.5 3.5 0 0 1 7 0", 2),
    "moon": ("M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z", 0),
    "trend": ("M3.5 17.5l5.5-5.5 4 4 7-7M15 9h5v5", 2),
    "flag": ("M5 21V3h1.6v18zM6.6 4h12v9h-12zM6.6 4h3v3h-3zM12.6 4h3v3h-3zM9.6 7h3v3h-3zM15.6 7h3v3h-3zM6.6 10h3v3h-3zM12.6 10h3v3h-3z", 0),
    "golf": ("M10 20V3.5l7 3-7 3M5.5 20.5h9M19.5 18a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0", 2),
}
BELLS = {
    "paused": "M370-320h220v-72H480l110-136v-72H370v72h110L370-392v72ZM160-200v-80h80v-280q0-83 50-147.5T420-792v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v280h80v80H160Zm320-300Zm0 420q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80ZM320-280h320v-280q0-66-47-113t-113-47q-66 0-113 47t-47 113v280Z",
    "off": "M160-200v-80h80v-280q0-33 8.5-65t25.5-61l60 60q-7 16-10.5 32.5T320-560v280h248L56-792l56-56 736 736-56 56-146-144H160Zm560-154-80-80v-126q0-66-47-113t-113-47q-26 0-50 8t-44 24l-58-58q20-16 43-28t49-18v-28q0-25 17.5-42.5T480-880q25 0 42.5 17.5T540-820v28q80 20 130 84.5T720-560v206Zm-276-50Zm36 324q-33 0-56.5-23.5T400-160h160q0 33-23.5 56.5T480-80Zm33-481Z",
}
# the icons' sizes: bottom fields and the side labels' marks 20, top field round(TF·0.83) for TF 24 / 25 / 26 = 20 / 21 / 22, pill marks 23.6
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
    # one row per icon, a column per size (the bells: the last column only), so a box is found from the name's row
    cols = [sum(b + 2 * PAD + 1 for b in ICON_SIZES[:i]) for i in range(len(ICON_SIZES))]
    rowh = max(ICON_SIZES) + 2 * PAD + 1
    names = []
    pos = []
    for name, sz, img in tiles:
        if name not in names:
            names.append(name)
        pos.append((cols[ICON_SIZES.index(sz)], names.index(name) * rowh))
    x, y, row = 0, (len(names) - 1) * rowh, rowh - 1
    sheet = Image.new("RGBA", (cols[-1] + max(ICON_SIZES) + 2 * PAD, y + row + 1), (255, 255, 255, 0))
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
    for name, sz, px, py, w in boxes:
        if name not in names:
            names.append(name)
    n_plain = len([n for n in names if not n.startswith("bell_")])
    cols = [sum(b + 2 * PAD + 1 for b in ICON_SIZES[:i]) for i in range(len(ICON_SIZES))]
    rowh = max(ICON_SIZES) + 2 * PAD + 1
    conds = " : ".join("size <= %d ? %d" % (b, i) for i, b in enumerate(ICON_SIZES[:-1])) + " : %d" % (len(ICON_SIZES) - 1)
    return ["", "    // icons.png: every icon of SPEC.md §2, a row each, a column per size (%s px; the bells %d only), white with" % (
                ", ".join(str(b) for b in ICON_SIZES), ICON_SIZES[-1]),
            "    // alpha, %d px margin; [x, y, box]" % PAD,
            "    function icon(name as String, size as Number) as Array? {",
            "        var i = [%s].indexOf(name);" % ", ".join(mc_string(n) for n in names),
            "        if (i < 0) {",
            "            return null;",
            "        }",
            "        var c = i >= %d ? %d : %s;" % (n_plain, len(ICON_SIZES) - 1, conds),
            "        return [[%s][c], i * %d, [%s][c]];" % (", ".join(str(x) for x in cols), rowh, ", ".join(str(b + 2 * PAD) for b in ICON_SIZES)),
            "    }"]


def outputs():
    files, table = build_fonts()
    icons_png, boxes = build_icons()
    files_icons = {"icons.png": icons_png, "icons.xml": (
        "<!-- Sukrara — https://github.com/mauriciabad/sukrara -->\n<!-- Copyright (C) 2026 Maurici Abad -->\n"
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->\n<!-- Generated by apps/garmin/aura/tools/fonts.py. -->\n"
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
            print("fonts.py: out of date (run uv run apps/garmin/aura/tools/fonts.py):\n  " + "\n  ".join(os.path.relpath(p, ROOT) for p in bad))
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
