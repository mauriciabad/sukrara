# /// script
# requires-python = ">=3.9"
# dependencies = ["freetype-py==2.5.1", "pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The watch app's fonts and icons (contracts/watchface/app/SPEC.md: Aura's type), built with Sukrara Aura's renderer
(apps/garmin/aura/tools/fonts.py: the same font, FreeType without hinting, the same atlas and BMFont formats).

  uv run apps/garmin/app/tools/fonts.py          writes apps/garmin/app/resources/fonts/*, resources/icons/* and
                                                 source/AppFontTable.mc
  uv run apps/garmin/app/tools/fonts.py --check  fails if the committed output differs from a fresh build

Every size is app.js's at the 390 px reference, rounded to a whole px; the app picks the nearest it has (AppDraw.font).
Every text is an atlas drawn glyph by glyph like Aura's (white glyphs, 8-bit alpha, tinted): Connect IQ's own font
resources draw the same glyphs visibly heavier (their antialiasing), so none is used. The glance's fonts and icons are scoped to the glance (its 64 KB): `G` below.
"""
import base64
import importlib.util
import io
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
GARMIN = os.path.dirname(APP)
ROOT = os.path.dirname(os.path.dirname(GARMIN))
spec = importlib.util.spec_from_file_location("aura_fonts", os.path.join(GARMIN, "aura", "tools", "fonts.py"))
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)

OUT = os.path.join(APP, "resources", "fonts")
ICONS_OUT = os.path.join(APP, "resources", "icons")
TABLE = os.path.join(APP, "source", "AppFontTable.mc")
GTABLE = os.path.join(APP, "source", "GlanceFontTable.mc")

NUMS = A.DIGITS + ".LOHI-"
W = {"m": 500, "s": 600, "b": 700}
# text fonts "a<px><m|s|b>": app.js's sizes (fs) and weights (fw)
TEXT = ["14m", "14s", "15m", "15s", "15b", "16m", "16s", "17m", "17s", "17b", "18m", "18s", "19m", "19s", "20m", "20s", "20b",
        "22s", "22b", "23s", "23b", "24s", "24b", "26s", "26b", "28b", "30b", "32b", "38s", "48s", "50s"]
# the big texts that only show a time or a unit: the no-reading time's set (Aura's), the amount's unit
CHARS = {"38s": A.GAP, "48s": A.GAP, "50s": "ug"}
# the big numbers "n<px>", weight 700 (app.js value(): fw 700)
BIG = [54, 58, 84, 88, 96, 100, 116, 128]
# the glance's (S.glance at 390): value 58 / 54, ring number 23, last-treatment stack 24 / 20 / 16, the reading's time
# 17, the pill 15, warming up 24 / 18, no reading 18 / 38
G = {"n54", "n58", "a23b", "a24b", "a20b", "a16s", "a17m", "a15s", "a24s", "a18m", "a18s", "a38s"}

# app.js ICON (24-unit paths; stroke width or 0 = filled)
ICONS = {
    "rapid": ("M2 4.2H22L12 20.9Z", 0),
    "long": ("M3.5 5.4H20.5L12 19.6Z", 2.8),
    "carbs": ("M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", 0),
    "sensor": ("M21.3 12a9.3 9.3 0 1 1-18.6 0a9.3 9.3 0 1 1 18.6 0zM18.7 12a6.7 6.7 0 1 0-13.4 0a6.7 6.7 0 1 0 13.4 0zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", 0),
    "bell": ("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0", 2.2),
    "bellOff": ("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0M3 3l18 18", 2.2),
    "bellPause": ("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0M10.4 9.5v4.2M13.6 9.5v4.2", 2.2),
    "note": ("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0M3 3l18 18", 2.2),
    "warn": ("M12 3L22 20H2ZM12 9.5v5M12 17v.6", 2.2),
    "phone": ("M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", 2.2),
    "pen": ("M18.43 8.43L9.93 16.93A2 2 0 0 1 7.08 14.08L15.58 5.58A2 2 0 0 1 18.43 8.43ZM14 9.5L11.75 11.75M18 6L20 4M7.25 16.75L4.25 19.75", 2.3),
}
# the sizes app.js draws them at (390): the pill's marks fs·0.78 (19 → 15), the sensor tag 17, the lists and buttons
# 18 – 30, the done screens 44
ICON_SIZES = (15, 18, 22, 26, 30, 44)
GLANCE_ICONS = ("rapid", "long", "carbs", "bellPause", "bellOff", "note")
GLANCE_ICON_SIZES = (12,)


def fonts():
    out = []
    for k in TEXT:
        px, w = int(k[:-1]), k[-1]
        out.append(("a" + k, px, W[w], CHARS.get(k, A.LATIN), True))
    for px in BIG:
        out.append(("n%d" % px, px, 700, NUMS, True))
    return out


def build_fonts():
    files = {}
    table = []
    for key, size, weight, chars, atlas in fonts():
        glyphs = A.render(size, weight, chars)
        sheet, pos = A.pack(glyphs)
        base = int(round(size * A.ASCENT))
        metrics = [(ch, px, py, w, h, left, base - top, adv)
                   for (ch, img, left, top, adv, w, h), (px, py) in zip(glyphs, pos)]
        files[key + ".png"] = A.png_bytes(sheet)
        if not atlas:
            line = int(round(size * (A.ASCENT + A.DESCENT)))
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
            table.append((key, base, False))
            continue
        bs = []
        for ch, px, py, w, h, xo, yo, adv in metrics:
            a = int(round(adv * 16))
            assert 0 <= w < 256 and 0 <= h < 256 and -128 <= xo < 128 and -128 <= yo < 128, (key, ch)
            bs += [px >> 8, px & 255, py >> 8, py & 255, w, h, xo & 255, yo & 255, a >> 8, a & 255]
        files[key + ".json"] = (json.dumps([chars, base64.b64encode(bytes(bs)).decode(), base], ensure_ascii=False) + "\n").encode()
        table.append((key, base, True))
    xml = ['<!-- Sukrara — https://github.com/mauriciabad/sukrara -->',
           '<!-- Copyright (C) 2026 Maurici Abad -->',
           '<!-- SPDX-License-Identifier: GPL-3.0-or-later -->',
           '<!-- Generated by apps/garmin/app/tools/fonts.py: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/OFL.txt). -->',
           "<resources>"]
    for key, base, atlas in table:
        scope = ' scope="glance"' if key in G else ""
        if not atlas:
            xml.append('    <font id="%s" filename="%s.fnt" antialias="true"%s/>' % (key, key, scope))
        else:
            xml.append('    <bitmap id="%s" filename="%s.png" dithering="none" packingFormat="png"%s/>' % (key, key, scope))
            xml.append('    <jsonData id="%s_m" filename="%s.json"%s/>' % (key, key, scope))
    xml.append("</resources>")
    files["fonts.xml"] = ("\n".join(xml) + "\n").encode()
    return files, table


def sheet(names, sizes):
    tiles = []
    for name in names:
        d, sw = ICONS[name]
        for s in sizes:
            box = s + 2 * A.PAD
            paint = ('fill="none" stroke="#FFFFFF" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % sw
                     if sw else 'fill="#FFFFFF" fill-rule="evenodd"')
            svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
                   '<g transform="translate(%d %d) scale(%s)"><path d="%s" %s/></g></svg>'
                   % (box, box, box, box, A.PAD, A.PAD, repr(s / 24.0), d, paint))
            tiles.append((name, s, Image.open(io.BytesIO(A.rsvg(svg, box))).convert("RGBA")))
    x = y = row = 0
    pos = []
    for name, s, img in tiles:
        if x + img.width > 256:
            x, y, row = 0, y + row + 1, 0
        pos.append((x, y))
        x += img.width + 1
        row = max(row, img.height)
    out = Image.new("RGBA", (256, y + row + 1), (255, 255, 255, 0))
    for (name, s, img), (px, py) in zip(tiles, pos):
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        white.putalpha(img.getchannel("A"))
        out.paste(white, (px, py))
    return A.png_bytes(out), [(name, s, px, py, img.width) for (name, s, img), (px, py) in zip(tiles, pos)]


def icon_fn(boxes):
    names = []
    for b in boxes:
        if b[0] not in names:
            names.append(b[0])
    out = ["    // the icon sheet's tiles (white with alpha, %d px margin): [x, y, box], the nearest size up" % A.PAD,
           "    function icon(name as String, size as Number) as Array? {"]
    for j, name in enumerate(names):
        out.append("        %sif (name.equals(%s)) {" % ("" if j == 0 else "} else ", A.mc_string(name)))
        sizes = [(s, px, py, w) for n, s, px, py, w in boxes if n == name]
        for i, (s, px, py, w) in enumerate(sizes):
            cond = "size <= %d" % s if i < len(sizes) - 1 else "true"
            out.append("            %sif (%s) { return [%d, %d, %d]; }" % ("" if i == 0 else "else ", cond, px, py, w))
    out.append("        }")
    out.append("        return null;")
    out.append("    }")
    return out


def table_src(module, annotation, entries, sheet_id, boxes, what):
    out = [
        "// Sukrara — https://github.com/mauriciabad/sukrara",
        "// Copyright (C) 2026 Maurici Abad",
        "// SPDX-License-Identifier: GPL-3.0-or-later",
        "// Generated by apps/garmin/app/tools/fonts.py (do not edit): %s" % what,
        "import Toybox.Lang;",
        "import Toybox.WatchUi;",
        "",
    ]
    if annotation:
        out.append(annotation)
    out += [
        "module %s {" % module,
        "    // key → [atlas bitmap, glyph table (jsonData: [glyphs, metrics in base64, baseline])], or [font resource,",
        "    // baseline px from the line's top]; [] = not here",
        "    function atlas(key as String) as Array {",
    ]
    atl = [t for t in entries if t[2]]
    fnt = [t for t in entries if not t[2]]
    keys = [t[0] for t in atl] + [t[0] for t in fnt]
    out.append("        var i = [%s].indexOf(key);" % ", ".join(A.mc_string(k) for k in keys))
    out.append("        if (i < 0) {")
    out.append("            return [];")
    out.append("        }")
    if atl:
        out.append("        if (i < %d) {" % len(atl))
        out.append("            return [[%s][i], [%s][i]];" % (", ".join("Rez.Drawables.%s" % t[0] for t in atl),
                                                             ", ".join("Rez.JsonData.%s_m" % t[0] for t in atl)))
        out.append("        }")
        out.append("        i -= %d;" % len(atl))
    out.append("        return [[%s][i], [%s][i]];" % (", ".join("Rez.Fonts.%s" % t[0] for t in fnt),
                                                     ", ".join(str(t[1]) for t in fnt)))
    out.append("    }")
    out.append("")
    out.append("    function sheet() as ResourceId {")
    out.append("        return Rez.Drawables.%s;" % sheet_id)
    out.append("    }")
    out.append("")
    out += icon_fn(boxes)
    out += ["}", ""]
    return "\n".join(out).encode()


def outputs():
    files, table = build_fonts()
    app_png, app_boxes = sheet(list(ICONS), ICON_SIZES)
    g_png, g_boxes = sheet(GLANCE_ICONS, GLANCE_ICON_SIZES)
    head = ("<!-- Sukrara — https://github.com/mauriciabad/sukrara -->\n<!-- Copyright (C) 2026 Maurici Abad -->\n"
            "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->\n<!-- Generated by apps/garmin/app/tools/fonts.py. -->\n")
    icons = {"icons.png": app_png, "gicons.png": g_png, "icons.xml": (
        head + '<resources>\n    <bitmap id="icons" filename="icons.png" dithering="none" packingFormat="png"/>\n'
        '    <bitmap id="gicons" filename="gicons.png" dithering="none" packingFormat="png" scope="glance"/>\n'
        "</resources>\n").encode()}
    out = {os.path.join(OUT, k): v for k, v in files.items()}
    out.update({os.path.join(ICONS_OUT, k): v for k, v in icons.items()})
    out[TABLE] = table_src("AppFontTable", None, [t for t in table if t[0] not in G], "icons", app_boxes,
                           "the app's fonts and icons (the glance's are GlanceFontTable).")
    out[GTABLE] = table_src("GlanceFontTable", "(:glance)", [t for t in table if t[0] in G], "gicons", g_boxes,
                            "the glance's fonts and icons, scoped to the glance.")
    return out


def main(argv):
    out = outputs()
    if "--check" in argv:
        bad = [p for p, b in out.items() if not os.path.exists(p) or open(p, "rb").read() != b]
        if bad:
            print("app fonts.py: out of date (run uv run apps/garmin/app/tools/fonts.py):\n  "
                  + "\n  ".join(os.path.relpath(p, ROOT) for p in bad))
            return 1
        print("app fonts.py: up to date")
        return 0
    for d in (OUT, ICONS_OUT):
        os.makedirs(d, exist_ok=True)
        for f in os.listdir(d):
            if os.path.join(d, f) not in out:
                os.remove(os.path.join(d, f))
    for p, b in out.items():
        with open(p, "wb") as fh:
            fh.write(b)
    print("app fonts.py: wrote %d files" % len(out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
