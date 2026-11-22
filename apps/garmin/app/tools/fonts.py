# /// script
# requires-python = ">=3.9"
# dependencies = ["freetype-py==2.5.1", "pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The watch app's fonts, icons and class constants per screen class (contracts/watchface/app/SPEC.md §14: the atlas
set), built with Sukrara Aura's renderer (apps/garmin/aura/tools/fonts.py: the same font, FreeType without hinting, the
same atlas and BMFont formats); the data field does the same for its slots (apps/garmin/field/tools/fonts.py).

  uv run apps/garmin/app/tools/fonts.py          writes apps/garmin/app/classes/<class>/ (resources: fonts, icons,
                                                 Sukrara for Android's icon; source: AppFontTable.mc,
                                                 GlanceFontTable.mc) and the watches' block of monkey.jungle
  uv run apps/garmin/app/tools/fonts.py --check  fails if the committed output differs from a fresh build

The app draws every screen in the reference's layout units (the 390's px on round screens, the Venu Sq 2's on the
rectangles) × the class's scale s; a text of fs units is drawn at the class's atlas size nearest fs × s (app.js
snapFont; the 390 keeps its approved sizes). So a class carries every size the 390 app asks for (TEXT, BIG below)
snapped at its s, and the glance's (G) at each of its watches' glance scales (the glance's content area, the device
file's, over the 390's 248 × 103). Every text is an atlas drawn glyph by glyph like Aura's (white glyphs, 8-bit alpha,
tinted); the watches without enhanced graphics (no tinted bitmap parts: the "<class>f" folders) draw Connect IQ font
resources instead, the icons too (an icon font per size). The glance's fonts and icons are scoped to the glance (its
64 KB). Each watch builds only its class's folder (monkey.jungle).
"""
import importlib.util
import io
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
GARMIN = os.path.dirname(APP)
ROOT = os.path.dirname(os.path.dirname(GARMIN))
spec = importlib.util.spec_from_file_location("aura_fonts", os.path.join(GARMIN, "aura", "tools", "fonts.py"))
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)

CLASSES = os.path.join(APP, "classes")
JUNGLE = os.path.join(APP, "monkey.jungle")
MANIFEST = os.path.join(APP, "manifest.xml")
DEVICES = os.path.expanduser(os.environ.get("CIQ_DEVICES", "~/Library/Application Support/Garmin/ConnectIQ/Devices"))
HEAD = ("Sukrara — https://github.com/mauriciabad/sukrara", "Copyright (C) 2026 Maurici Abad",
        "SPDX-License-Identifier: GPL-3.0-or-later")

NUMS = A.DIGITS + ".LOHI-"
W = {"m": 500, "s": 600, "b": 700}
# text fonts "a<px><m|s|b>": the sizes (fs) and weights (fw) the app asks for, in layout units (app.js S.* and R.*)
TEXT = ["14m", "14s", "15m", "15s", "15b", "16m", "16s", "17m", "17s", "17b", "18m", "18s", "19m", "19s", "20m", "20s", "20b",
        "22s", "22b", "23s", "23b", "24s", "24b", "26s", "26b", "28b", "30b", "32b", "38s", "48s", "50s", "96b"]
# the set of the texts that only show a time or a unit: the no-reading time's (Aura's), the amount's unit
LATIN = A.LATIN + "−’"
CHARS = {"38s": A.GAP, "48s": A.GAP, "50s": "ug", "96b": "ug"}
# the big numbers "n<px>", weight 700 (app.js value(): fw 700); 120: the rectangles' amount
BIG = [42, 44, 46, 48, 50, 52, 54, 56, 58, 84, 88, 96, 100, 116, 120, 128]
# the glance's (S.glance at 390): value 58 / 54 (down to 46 / 42 to fit its two places), ring number 23, last-treatment
# stack 24 / 20 / 16, the reading's time 17, the pill 15, warming up 24 / 18, no reading 18 / 38
G = ["n42", "n44", "n46", "n48", "n50", "n52", "n54", "n56", "n58", "a23b", "a24b", "a20b", "a16s", "a17m", "a15s", "a24s",
     "a18m", "a18s", "a38s"]

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
    "phone": ("M8.5 2h7a2.5 2.5 0 0 1 2.5 2.5v15a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 19.5v-15A2.5 2.5 0 0 1 8.5 2zM8 4.6v12.6h8V4.6z", 0),
    "pen": ("M18.43 8.43L9.93 16.93A2 2 0 0 1 7.08 14.08L15.58 5.58A2 2 0 0 1 18.43 8.43ZM14 9.5L11.75 11.75M18 6L20 4M7.25 16.75L4.25 19.75", 2.3),
}
# the sizes the app draws them at (layout units): the pill's marks fs·0.78 (19 → 15), the sensor tag 17, the lists and
# buttons 18 – 30, the done screens 44; the glance's 12
ICON_SIZES = (15, 18, 22, 26, 30, 44)
GLANCE_ICONS = ("rapid", "long", "carbs", "bellPause", "bellOff", "note")
GLANCE_ICON = 12
# Sukrara for Android's own icon (app.js phoneIcon: the disc, the white cube, the green dot), 60 units on the waiting page
PHONE_ICON = 60
CUBE = ("M45.09,45.19Q47.00,44.09 48.91,45.19L57.93,50.40Q59.83,51.50 57.93,52.60L48.91,57.81Q47.00,58.91 45.09,57.81L36.07,52.60"
        "Q34.17,51.50 36.07,50.40ZM32.36,56.84Q32.36,54.63 34.26,55.73L43.28,60.95Q45.19,62.05 45.19,64.25L45.19,74.66Q45.19,76.86 "
        "43.28,75.77L34.26,70.55Q32.36,69.45 32.36,67.25ZM48.81,64.25Q48.81,62.05 50.72,60.95L59.74,55.73Q61.64,54.63 61.64,56.84"
        "L61.64,67.25Q61.64,69.45 59.74,70.55L50.72,75.77Q48.81,76.86 48.81,74.66Z")
DOT = "M59.50,42.00A6.50,6.50 0 1,0 72.50,42.00A6.50,6.50 0 1,0 59.50,42.00Z"

# the screen class of each deviceFamily (app.js CLS), and the reference's class table: the atlas set, the buttons
# (round: degrees clockwise from 12; rectangles: px from the top of the right edge), the layout's scale
FAMILY = {"round-360x360": "r360", "round-390x390": "r390", "round-416x416": "r416", "round-454x454": "r454",
          "round-466x466": "r466", "round-218x218": "m218", "round-240x240": "m240", "round-260x260": "m260",
          "round-280x280": "m280", "rectangle-320x360": "sq2", "rectangle-448x486": "x1"}
ATLAS = {"amoled": [14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 52, 56, 60, 64, 72, 80, 88, 96, 104, 112, 120, 128,
                    136, 148],
         "mip": [12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 44, 48, 56, 64, 72, 80, 88]}
KEYS = {"r390": (60, 120, 270, 240), "r360": (63, 119, 270, 241), "r416": (60, 119, 270, 242), "r454": (60, 121, 269, 240),
        "r466": (61, 121, 268, 240), "m218": (60, 119, 270, 241), "m240": (60, 118, 271, 242), "m260": (61, 119, 269, 240),
        "m280": (62, 122, 268, 238), "sq2": (114, 331, 0, 0), "x1": (130, 375, 0, 0)}


def classes():
    """{folder: {cls, w, h, s, mip, rect, plain, glances: [the glance scales of its watches], watches: [ids]}}"""
    ids = re.findall(r'<iq:product id="([^"]+)"', open(MANIFEST, encoding="utf-8").read())
    out = {}
    for i in ids:
        c = json.load(open(os.path.join(DEVICES, i, "compiler.json"), encoding="utf-8"))
        sim = json.load(open(os.path.join(DEVICES, i, "simulator.json"), encoding="utf-8"))
        cls = FAMILY[c["deviceFamily"]]
        plain = not c.get("enhancedGraphicSupport")
        w, h = c["resolution"]["width"], c["resolution"]["height"]
        rect = cls in ("sq2", "x1")
        k = out.setdefault(cls + ("f" if plain else ""), {
            "cls": cls, "w": w, "h": h, "s": (1.2 if w > 320 else 1.0) if rect else w / 390.0,
            "mip": c["displayType"] == "mip", "rect": rect, "plain": plain, "glances": set(), "watches": []})
        ga = sim["glance"]["contentArea"]
        k["glances"].add(min(ga["height"] / 103.0, ga["width"] / 248.0))
        k["watches"].append(i)
    return out


def snap(k, px, exact):
    """app.js snapFont: the nearest of the class's atlas set (ties: the larger); [exact] the 390's own sizes"""
    if exact:
        return int(round(px))
    best = None
    for a in ATLAS["mip" if k["mip"] else "amoled"]:
        if best is None or abs(a - px) < abs(best - px) or (abs(a - px) == abs(best - px) and a > best):
            best = a
    return best


def key_at(k, key, s, exact):
    """A font key of the 390 app ("a18s", "n58") at the scale [s] on the class"""
    if key[0] == "n":
        return "n%d" % snap(k, int(key[1:]) * s, exact)
    return "a%d%s" % (snap(k, int(key[1:-1]) * s, exact), key[-1])


def font_keys(k):
    """[(key, px, weight, chars, glance)]: the app's sizes at the class's s, the glance's at each of its watches' scales.
    A key keeps a time-only or unit-only set (CHARS) when every size that lands on it has that set."""
    r390 = k["cls"] == "r390"
    src, glance = {}, set()
    for t in TEXT:
        src.setdefault(key_at(k, "a" + t, k["s"], r390), set()).add(t)
    for n in BIG:
        src.setdefault(key_at(k, "n%d" % n, k["s"], r390), set())
    for sg in k["glances"]:
        for g in G:
            key = key_at(k, g, sg, r390 and abs(sg - 1) < 1e-6)
            src.setdefault(key, set()).add(g[1:])
            glance.add(key)
    out = []
    for key in sorted(src, key=lambda x: (x[0], int(re.sub(r"\D", "", x)), x[-1])):
        if key[0] == "n":
            out.append((key, int(key[1:]), 700, NUMS, key in glance))
            continue
        sets = {CHARS.get(t, LATIN) for t in src[key]}
        out.append((key, int(key[1:-1]), W[key[-1]], sets.pop() if len(sets) == 1 else LATIN, key in glance))
    return out


def fnt(key, px, weight, glyphs, pos, sheet, base):
    """A BMFont file of the atlas (Connect IQ's font resource)."""
    line = int(round(px * (A.ASCENT + A.DESCENT)))
    lines = [
        'info face="AtkinsonHyperlegibleNext-%d" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
        'padding=0,0,0,0 spacing=1,1' % (weight, px),
        "common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0" % (line, base, sheet.width, sheet.height),
        'page id=0 file="%s.png"' % key,
        "chars count=%d" % len(glyphs)]
    for (ch, img, left, top, adv, w, h), (gx, gy) in zip(glyphs, pos):
        lines.append("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15"
                     % (ord(ch), gx, gy, w, h, left, base - top, int(round(adv))))
    return ("\n".join(lines) + "\n").encode()


def tile(name, s):
    d, sw = ICONS[name]
    box = s + 2 * A.PAD
    paint = ('fill="none" stroke="#FFFFFF" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % sw
             if sw else 'fill="#FFFFFF" fill-rule="evenodd"')
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
           '<g transform="translate(%d %d) scale(%s)"><path d="%s" %s/></g></svg>'
           % (box, box, box, box, A.PAD, A.PAD, repr(s / 24.0), d, paint))
    return Image.open(io.BytesIO(A.rsvg(svg, box))).convert("RGBA")


def sheet(names, size):
    """One size's icons side by side (white, alpha): the sheet and [(name, x, y, box)]"""
    tiles = [(n, tile(n, size)) for n in names]
    x = y = row = 0
    pos = []
    for name, img in tiles:
        if x + img.width > 256:
            x, y, row = 0, y + row + 1, 0
        pos.append((x, y))
        x += img.width + 1
        row = max(row, img.height)
    out = Image.new("RGBA", (256 if y else x, y + row + 1), (255, 255, 255, 0))
    for (name, img), (px, py) in zip(tiles, pos):
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        white.putalpha(img.getchannel("A"))
        out.paste(white, (px, py))
    return out, [(name, px, py, img.width) for (name, img), (px, py) in zip(tiles, pos)]


def icon_font(key, names, size):
    """The icons of one size as a font resource (the watches without tinted bitmaps draw them with drawText in any
    colour): glyph i = "A" + i, each its tile, advance the tile's width, base its bottom."""
    img, boxes = sheet(names, size)
    box = boxes[0][3]
    lines = ['info face="SukraraIcons" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
             'padding=0,0,0,0 spacing=1,1' % box,
             "common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0" % (box, box, img.width, img.height),
             'page id=0 file="%s.png"' % key, "chars count=%d" % len(boxes)]
    for i, (name, x, y, w) in enumerate(boxes):
        lines.append("char id=%d x=%d y=%d width=%d height=%d xoffset=0 yoffset=0 xadvance=%d page=0 chnl=15"
                     % (65 + i, x, y, w, w, w))
    return A.png_bytes(img), ("\n".join(lines) + "\n").encode(), box


def phone_icon(px, mip):
    """app.js phoneIcon at [px]: the disc #127C95, the cube white, the dot (now: C6F432; MIP: the 64 colours)"""
    disc, dot = ("#0055AA", "#AAFF00") if mip else ("#127C95", "#C6F432")
    r = px / 2.0
    s = px / 49.64 * 0.78
    ox, oy = r - (27.61 + 24.82) * s, r - (31.36 + 24.82) * s
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d"><circle cx="%s" cy="%s" r="%s" '
           'fill="%s"/><g transform="translate(%s %s) scale(%s)"><path d="%s" fill="#FFFFFF"/><path d="%s" fill="%s"/></g></svg>'
           % (px, px, px, px, r, r, r, disc, ox, oy, s, CUBE, DOT, dot))
    img = Image.open(io.BytesIO(A.rsvg(svg, px))).convert("RGBA")
    if mip:
        # no alpha on the 64-colour screens: every pixel to the nearest of the 64 over black, opaque
        px_ = img.load()
        for y in range(img.height):
            for x in range(img.width):
                rr, gg, bb, aa = px_[x, y]
                q = [int(round(v * aa / 255.0 / 85.0)) * 85 for v in (rr, gg, bb)]
                px_[x, y] = (q[0], q[1], q[2], 255 if aa >= 128 else 0)
    return A.png_bytes(img)


def mc_list(xs):
    return "[%s]" % ", ".join(xs)


def class_files(folder, k):
    files = {}
    res = os.path.join(CLASSES, folder, "resources")
    src = os.path.join(CLASSES, folder, "source")
    plain = k["plain"]
    head = ["<!-- %s -->" % h for h in HEAD]
    fx = head + ["<!-- Generated by apps/garmin/app/tools/fonts.py: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/OFL.txt). -->",
                 "<resources>"]
    app_t, gl_t = [], []
    for key, px, weight, chars, glance in font_keys(k):
        glyphs = A.render(px, weight, chars)
        sh, pos = A.pack(glyphs)
        base = int(round(px * A.ASCENT))
        scope = ' scope="glance"' if glance else ""
        files[os.path.join(res, "fonts", key + ".png")] = A.png_bytes(sh)
        (gl_t if glance else app_t).append((key, base))
        if plain:
            files[os.path.join(res, "fonts", key + ".fnt")] = fnt(key, px, weight, glyphs, pos, sh, base)
            fx.append('    <font id="%s" filename="%s.fnt" antialias="true"%s/>' % (key, key, scope))
            continue
        # two Numbers a glyph, read as they load (no decoding: the glance has 64 KB, and a base64 table and its bytes
        # held together ran it out of memory on a Descent Mk3 43, #217): x | y << 9 | w << 17 | h << 24, then
        # xo + 128 | (yo + 128) << 8 | advance·16 << 16
        ns = []
        for (ch, img, left, top, adv, w, h), (gx, gy) in zip(glyphs, pos):
            a = int(round(adv * 16))
            yo = base - top
            assert 0 <= gx < 512 and 0 <= gy < 256 and 0 <= w < 128 and 0 <= h < 128, (folder, key, ch)
            assert -128 <= left < 128 and -128 <= yo < 128 and 0 <= a < 32768, (folder, key, ch)
            ns += [gx | gy << 9 | w << 17 | h << 24, (left + 128) | (yo + 128) << 8 | a << 16]
        files[os.path.join(res, "fonts", key + ".json")] = (json.dumps(
            [chars, ns, base], ensure_ascii=False, separators=(",", ":")) + "\n").encode()
        fx.append('    <bitmap id="%s" filename="%s.png" dithering="none" packingFormat="png"%s/>' % (key, key, scope))
        fx.append('    <jsonData id="%s_m" filename="%s.json"%s/>' % (key, key, scope))
    fx.append("</resources>")
    files[os.path.join(res, "fonts", "fonts.xml")] = ("\n".join(fx) + "\n").encode()

    # the icons: one sheet (or icon font) per size, so a screen loads only the sizes it draws (the graphics pool)
    ix = head + ["<!-- Generated by apps/garmin/app/tools/fonts.py. -->", "<resources>"]
    sizes = sorted({max(8, int(round(sz * k["s"]))) for sz in ICON_SIZES} if k["cls"] != "r390" else set(ICON_SIZES))
    gsizes = sorted({max(8, int(round(GLANCE_ICON * sg))) for sg in k["glances"]})
    tables = {}
    for scope, names, szs in (("app", list(ICONS), sizes), ("glance", list(GLANCE_ICONS), gsizes)):
        ids, boxes = [], []
        for size in szs:
            rid = ("icons%d" if scope == "app" else "gicons%d") % size
            sc = ' scope="glance"' if scope == "glance" else ""
            if plain:
                png, f, box = icon_font(rid, names, size)
                files[os.path.join(res, "icons", rid + ".png")] = png
                files[os.path.join(res, "icons", rid + ".fnt")] = f
                ix.append('    <font id="%s" filename="%s.fnt" antialias="true"%s/>' % (rid, rid, sc))
                boxes += [(n, size, i, 0, box, len(ids)) for i, n in enumerate(names)]
            else:
                img, bx = sheet(names, size)
                files[os.path.join(res, "icons", rid + ".png")] = A.png_bytes(img)
                ix.append('    <bitmap id="%s" filename="%s.png" dithering="none" packingFormat="png"%s/>' % (rid, rid, sc))
                boxes += [(n, size, x, y, w, len(ids)) for n, x, y, w in bx]
            ids.append(rid)
        tables[scope] = (ids, boxes)
    phone = max(16, int(round(PHONE_ICON * k["s"])))
    files[os.path.join(res, "icons", "phone.png")] = phone_icon(phone, k["mip"])
    ix.append('    <bitmap id="PhoneIcon" filename="phone.png" dithering="none"/>')
    ix.append("</resources>")
    files[os.path.join(res, "icons", "icons.xml")] = ("\n".join(ix) + "\n").encode()

    files[os.path.join(src, "AppFontTable.mc")] = table_src(
        folder, k, "AppFontTable", None, app_t, tables["app"], "the app's fonts and icons (the glance's are GlanceFontTable)")
    files[os.path.join(src, "GlanceFontTable.mc")] = table_src(
        folder, k, "GlanceFontTable", "(:glance)", gl_t, tables["glance"], "the glance's fonts and icons, scoped to the "
        "glance, and the class's constants")
    return files


def icon_fn(boxes, plain):
    names = []
    for b in boxes:
        if b[0] not in names:
            names.append(b[0])
    what = ("[glyph index, 0, box, font]" if plain else "[x, y, box, sheet]")
    out = ["    // the icons: %s of the nearest size (%d px margin)" % (what, A.PAD),
           "    function icon(name as String, size as Number) as Array? {"]
    for j, name in enumerate(names):
        out.append("        %sif (name.equals(%s)) {" % ("" if j == 0 else "} else ", A.mc_string(name)))
        sizes = [(s, x, y, w, sh) for n, s, x, y, w, sh in boxes if n == name]
        for i, (s, x, y, w, sh) in enumerate(sizes):
            nxt = sizes[i + 1][0] if i < len(sizes) - 1 else None
            cond = ("size <= %d" % ((s + nxt) // 2 if plain else s)) if nxt else "true"
            out.append("            %sif (%s) { return [%d, %d, %d, %d]; }" % ("" if i == 0 else "else ", cond, x, y, w, sh))
    if names:
        out.append("        }")
    out.append("        return null;")
    out.append("    }")
    return out


def table_src(folder, k, module, annotation, entries, icons, what):
    plain = k["plain"]
    out = ["// %s" % h for h in HEAD] + [
        "// Generated by apps/garmin/app/tools/fonts.py (do not edit): %s, on the %s class%s." % (
            what, k["cls"], " (font resources: these watches draw no tinted bitmap parts)" if plain else ""),
        "import Toybox.Lang;", "import Toybox.WatchUi;", ""]
    if annotation:
        out.append(annotation)
    out.append("module %s {" % module)
    if module == "GlanceFontTable":
        keys = KEYS[k["cls"]]
        out += [
            "    // the class (app.js CLS, SPEC §14): layout units → px, the 64-colour palette, a rectangle, the atlas set",
            '    const CLS = "%s";' % k["cls"],
            "    const R390 = %s; // the design's class: its approved sizes and strokes are drawn as they are"
            % ("true" if k["cls"] == "r390" else "false"),
            "    const S = %r;" % round(k["s"], 6),
            "    const MIP = %s;" % ("true" if k["mip"] else "false"),
            "    const RECT = %s;" % ("true" if k["rect"] else "false"),
            "    const PLAIN = %s; // no tinted bitmaps: texts and icons are font resources" % ("true" if plain else "false"),
            "    const ATLAS = %s;" % mc_list(str(a) for a in ATLAS["mip" if k["mip"] else "amoled"]),
            "    // the buttons: START, BACK, UP, DOWN (round: degrees clockwise from 12; rectangles: px from the top)",
            "    const KEYS = %s;" % mc_list(str(a) for a in keys),
            ""]
    out += [
        "    // key → [atlas bitmap, glyph table (jsonData: [glyphs, two Numbers a glyph, baseline])], or [font resource,",
        "    // baseline px from the line's top]; [] = not here",
        "    function atlas(key as String) as Array {",
        "        var i = %s.indexOf(key);" % mc_list(A.mc_string(e[0]) for e in entries),
        "        if (i < 0) {",
        "            return [];",
        "        }"]
    if plain:
        out.append("        return [%s[i], %s[i]];" % (mc_list("Rez.Fonts.%s" % e[0] for e in entries),
                                                     mc_list(str(e[1]) for e in entries)))
    else:
        out.append("        return [%s[i], %s[i]];" % (mc_list("Rez.Drawables.%s" % e[0] for e in entries),
                                                     mc_list("Rez.JsonData.%s_m" % e[0] for e in entries)))
    ids, boxes = icons
    out += ["    }", "",
            "    // the icon %s [i]: one per size" % ("font" if plain else "sheet"),
            "    function sheet(i as Number) as ResourceId {",
            "        return %s[i] as ResourceId;" % mc_list(("Rez.Fonts.%s" if plain else "Rez.Drawables.%s") % x for x in ids),
            "    }", ""]
    out += icon_fn(boxes, plain)
    out += ["}", ""]
    return "\n".join(out).encode()


def jungle_block(ks):
    lines = ["# BEGIN screen classes (apps/garmin/app/tools/fonts.py)",
             "# each watch builds its screen class's fonts, icons and tables (classes/<class>), and the MIP code or not"]
    ids = re.findall(r'<iq:product id="([^"]+)"', open(MANIFEST, encoding="utf-8").read())
    where = {w: f for f, k in ks.items() for w in k["watches"]}
    for i in ids:
        lines.append("%s.sourcePath = $(%s.sourcePath);classes/%s/source" % (i, i, where[i]))
        lines.append("%s.resourcePath = $(%s.resourcePath);classes/%s/resources" % (i, i, where[i]))
        # the MIP code (AppDraw's 64 colours and dithers) only where the screen has 64 colours
        # and the texts' and icons' code for atlases or font resources (the watches without tinted bitmaps)
        k = ks[where[i]]
        lines.append("%s.excludeAnnotations = $(%s.excludeAnnotations);%s;%s" % (
            i, i, "amoled" if k["mip"] else "mip", "atlas" if k["plain"] else "plain"))
    lines.append("# END screen classes")
    return "\n".join(lines)


def outputs():
    out = {}
    ks = classes()
    for folder, k in sorted(ks.items()):
        out.update(class_files(folder, k))
    j = open(JUNGLE, encoding="utf-8").read()
    block = jungle_block(ks)
    if "# BEGIN screen classes" in j:
        j = re.sub(r"# BEGIN screen classes.*?# END screen classes", lambda m: block, j, flags=re.S)
    else:
        j = j.rstrip("\n") + "\n" + block + "\n"
    out[JUNGLE] = j.encode()
    return out


def main(argv):
    out = outputs()
    if "--check" in argv:
        bad = [p for p, b in out.items() if not os.path.exists(p) or open(p, "rb").read() != b]
        bad += [os.path.join(r, f) for r, _, fs in os.walk(CLASSES) for f in fs if os.path.join(r, f) not in out]
        if bad:
            print("app fonts.py: out of date (run uv run apps/garmin/app/tools/fonts.py):\n  "
                  + "\n  ".join(os.path.relpath(p, ROOT) for p in bad))
            return 1
        print("app fonts.py: up to date")
        return 0
    for root, _, fs in os.walk(CLASSES):
        for f in fs:
            if os.path.join(root, f) not in out:
                os.remove(os.path.join(root, f))
    for p, b in out.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as fh:
            fh.write(b)
    print("app fonts.py: wrote %d files" % len(out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
