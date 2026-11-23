# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The Garmin apps' launcher icons (the app's, which is also its glance's and its notifications', the data field's and Aura's),
one PNG per launcher-icon size the manifests' watches ask for.

  uv run apps/garmin/tools/launcher_icons.py           writes apps/garmin/<app>/resources-launcher<N>/ and the jungles'
                                                       per-product lines
  uv run apps/garmin/tools/launcher_icons.py --check   fails when they differ from a fresh run

Garmin's own apps and glances (fēnix 8, Venu 4, Forerunner 970) show a one-colour glyph on the black list, no
background shape. So Sukrara's is its brand glyph (assets/brand/sukrara/glyph.svg: the cube and the dot) in Garmin's
theme cyan, the colour of the "Cyan" accent in the SDK's device files (fēnix 8 / 9, epix 2, MARQ, Venu 3 …). Glyph
fills GLYPH of the icon's side, centred, antialiased (alpha). The small sizes (PIXEL: 38 and 40 px, the MIP watches'
included) are drawn on the pixel grid instead, no antialias: the cube in the glyph's proportions (30°, as 2-2-1 px steps,
symmetric, 3 px gaps between its faces), the dot a pixel circle. The 64-colour MIP watches (no alpha blending) get the nearest
palette colour, so the compiler has nothing to dither. Sizes and screen types come from the SDK's device files
(compiler.json launcherIcon, displayType).

Sukrara Aura (the face) takes the same icon: Garmin shows a face's launcher icon almost nowhere, but where it does
(the watch's Connect IQ app list) the three apps read as one family.
"""
import itertools
import json
import os
import pathlib
import re
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[3]
GARMIN = ROOT / "apps/garmin"
APPS = ("app", "field", "aura")
# each app's jungles and the manifest whose products each builds (Aura's debug case loader has its own pair)
JUNGLES = {"aura": (("monkey.jungle", "manifest.xml"), ("cases.jungle", "manifest-cases.xml"))}
GLYPH_SVG = ROOT / "assets/brand/sukrara/glyph.svg"
DEVICES = pathlib.Path(os.path.expanduser("~/Library/Application Support/Garmin/ConnectIQ/Devices"))
BEGIN = "# BEGIN launcher icon sizes (apps/garmin/tools/launcher_icons.py)"
END = "# END launcher icon sizes"
HEAD = "<!-- Sukrara — https://github.com/mauriciabad/sukrara -->\n<!-- Copyright (C) 2026 Maurici Abad -->\n" \
       "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->\n"

CYAN = (0x75, 0xC7, 0xF4)  # Garmin's theme "Cyan" (Devices/fenix847mm/glance_cyan.png and 19 more device files)
MIP_LEVELS = (0x00, 0x55, 0xAA, 0xFF)  # the 64-colour MIP palette: every channel one of these
GLYPH = 0.78  # the glyph's larger side over the icon's side
SS = 16  # supersampling for the antialias
# the pixel-fitted sizes keep the glyph's proportions (an isometric 30° cube about 0.92 as wide as tall, its faces apart
# by 12 % of its width): the slanted edges step 2-2-1 px per row (31°) out from the axis, the faces GAP px apart
STEP = (2, 2, 1)
GAP = 3
# n → (a, w, h, d, cx, cy): the top face 2a + 1 columns wide, the cube 2w + 1, its side faces' columns h tall; the dot d px
# across, centred cx px right of the axis column's left edge and cy below the cube's top
PIXEL = {38: (8, 10, 11, 10, 14, -1), 40: (9, 11, 11, 10, 15, -2)}


def nearest_mip(c):
    return tuple(min(MIP_LEVELS, key=lambda v: abs(v - x)) for x in c)


def shapes():
    """glyph.svg's filled shapes as polygons (quadratic curves flattened) and circles: [("poly", pts) | ("circle", cx, cy, r)]"""
    out = []
    for d in re.findall(r'<path d="([^"]+)"', GLYPH_SVG.read_text()):
        if "A" in d:  # the dot: two half arcs
            m = re.match(r"M([\d.]+),([\d.]+)A([\d.]+),", d)
            x0, y0, r = map(float, m.groups())
            out.append(("circle", x0 + r, y0, r))
            continue
        pts, cur = [], None
        for cmd, args in re.findall(r"([MLQZ])([^MLQZ]*)", d):
            nums = [float(v) for v in re.findall(r"-?[\d.]+", args)]
            if cmd in "ML":
                cur = (nums[0], nums[1])
                pts.append(cur)
            elif cmd == "Q":
                (cx, cy), (ex, ey) = (nums[0], nums[1]), (nums[2], nums[3])
                for i in range(1, 13):
                    t = i / 12
                    pts.append(((1 - t) ** 2 * cur[0] + 2 * (1 - t) * t * cx + t * t * ex,
                                (1 - t) ** 2 * cur[1] + 2 * (1 - t) * t * cy + t * t * ey))
                cur = (ex, ey)
        out.append(("poly", pts))
    return out


def bounds(sh):
    xs, ys = [], []
    for s in sh:
        if s[0] == "poly":
            xs += [p[0] for p in s[1]]
            ys += [p[1] for p in s[1]]
        else:
            xs += [s[1] - s[3], s[1] + s[3]]
            ys += [s[2] - s[3], s[2] + s[3]]
    return min(xs), min(ys), max(xs), max(ys)


def coverage(n, fill=GLYPH, sh=None):
    """the glyph's coverage (L, 0–255) on an n × n icon, centred, its larger side fill · n"""
    sh = sh or shapes()
    x0, y0, x1, y1 = bounds(sh)
    k = fill * n * SS / max(x1 - x0, y1 - y0)
    ox = n * SS / 2 - (x0 + x1) / 2 * k
    oy = n * SS / 2 - (y0 + y1) / 2 * k
    big = Image.new("L", (n * SS, n * SS), 0)
    dr = ImageDraw.Draw(big)
    for s in sh:
        if s[0] == "poly":
            dr.polygon([(ox + x * k, oy + y * k) for x, y in s[1]], fill=255)
        else:
            cx, cy, r = ox + s[1] * k, oy + s[2] * k, s[3] * k
            dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    return big.reduce(SS)


def pixels(n):
    """the pixel-fitted glyph on an n × n grid (PIXEL[n]), as coverage (L, 0 / 255), centred on whole pixels"""
    a, w, h, d, cx, cy = PIXEL[n]
    step = []  # step[j]: how many rows the slanted edges have stepped at the j-th column out from the axis
    for k, run in enumerate(itertools.cycle(STEP)):
        step += [k] * run
        if len(step) > w:
            break
    last = 2 * step[a] + 1  # the top face's last row: its side corners 2 rows tall
    on = set()
    for j in range(w + 1):  # j: the column's distance from the axis column 0; its mirror is column -j
        rows = list(range(step[j], last - step[j] + 1)) if j <= a else []
        if j > GAP // 2:  # the side faces, under the top face's lower edges, GAP rows below and GAP columns apart
            s = last - step[j] + GAP + 1
            rows += range(s + (j == w), s + h - (j == w))  # their outer corners rounded off
        for y in rows:
            on |= {(j, y), (-j, y)}
    r = d / 2
    for y in range(cy - d, cy + d):
        for x in range(cx - d, cx + d):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= (r - 0.05) ** 2:
                on.add((x, y))
    xs, ys = [p[0] for p in on], [p[1] for p in on]
    ox = (n - (max(xs) - min(xs) + 1)) // 2 - min(xs)
    oy = (n - (max(ys) - min(ys) + 1)) // 2 - min(ys)
    cov = Image.new("L", (n, n), 0)
    for x, y in on:
        cov.putpixel((x + ox, y + oy), 255)
    return cov


def icon(n, mip, colour=CYAN, top=None, fill=GLYPH):
    """the launcher icon, n × n RGBA: the glyph in colour (a vertical gradient when top is given: top → colour),
    antialiased, or pixel-fitted at the PIXEL sizes; on MIP the nearest palette colour, hard edges"""
    cov = pixels(n) if n in PIXEL else coverage(n, fill)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px, cv = img.load(), cov.load()
    ys = [y for y in range(n) if any(cv[x, y] for x in range(n))]
    for y in range(n):
        t = (y - ys[0]) / max(1, ys[-1] - ys[0])
        c = colour if top is None else tuple(round(a + (b - a) * t) for a, b in zip(top, colour))
        for x in range(n):
            a = cv[x, y]
            if mip:
                if a >= 128:
                    px[x, y] = nearest_mip(c) + (255,)
            elif a:
                px[x, y] = c + (a,)
    return img


def targets(app, manifest="manifest.xml"):
    """product → (launcher icon side, MIP?) for every product in the app's manifest"""
    products = re.findall(r'<iq:product id="([^"]+)"', (GARMIN / app / manifest).read_text())
    out = {}
    for p in products:
        c = json.loads((DEVICES / p / "compiler.json").read_text())
        out[p] = (c["launcherIcon"]["width"], c["displayType"] == "mip")
    return out


def folder(n, mip):
    return "resources-launcher%d%s" % (n, "mip" if mip else "")


# Garmin's notification for an alert (the watch app's background, SPEC §7): its icon at the launcher icon's size (where
# Garmin puts the app's icon by default), one per alert colour (Alerts.colour): the app's alert screen in small, the
# bell (app.js ICON.bell, a little bolder) in a ring of the colour on black, or on a disc of it for very low and extremely
# high (the screen's block: white, black on MIP). Aura's palette (app.js PAL / PAL_MIP).
ALERTS = {
    "veryLow": ("#C2413B", "#FF0000"), "low": ("#F0524F", "#FF5555"), "tight": ("#3DD47A", "#00FF55"),
    "target": ("#8CC63F", "#AAFF00"), "high": ("#F5A524", "#FFAA00"), "veryHigh": ("#FF7A45", "#FF5500"),
    "extremelyHigh": ("#E35BD0", "#FF55FF"), "grey": ("#8E8E93", "#AAAAAA"),
}
BLOCK = ("veryLow", "extremelyHigh")


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def bell(dr, k, ox, oy, sw, col):
    """app.js ICON.bell (24 × 24 units, stroke 2.2) at k px per unit from (ox, oy): M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8
    1.8H4.2z M10 20.5a2 2 0 0 0 4 0"""
    import math
    p = lambda x, y: (ox + x * k, oy + y * k)
    body = [p(6, 16.5), p(6, 11)] + [p(12 - 6 * math.cos(math.pi * t / 24), 11 - 6 * math.sin(math.pi * t / 24))
                                     for t in range(1, 24)]
    body += [p(18, 11), p(18, 16.5), p(19.8, 18.3), p(4.2, 18.3), p(6, 16.5)]
    clapper = [p(12 - 2 * math.cos(math.pi * t / 12), 20.5 + 2 * math.sin(math.pi * t / 12)) for t in range(13)]
    for line in (body, clapper):
        dr.line(line, fill=col, width=round(sw), joint="curve")
        for q in line[:1] + line[-1:]:
            dr.ellipse((q[0] - sw / 2, q[1] - sw / 2, q[0] + sw / 2, q[1] + sw / 2), fill=col)


def alert_icon(n, mip, kind):
    """the alert's icon, n × n RGBA (antialiased; MIP: palette colours, hard edges)"""
    col = rgb(ALERTS[kind][1 if mip else 0])
    block = kind in BLOCK
    big = Image.new("RGBA", (n * SS, n * SS), (0, 0, 0, 0))
    dr = ImageDraw.Draw(big)
    r = n * SS / 2
    ring = max(2, round(n * 0.09)) * SS
    if block:
        dr.ellipse((0, 0, 2 * r - 1, 2 * r - 1), fill=col + (255,))
    else:
        dr.ellipse((0, 0, 2 * r - 1, 2 * r - 1), fill=(0, 0, 0, 255))
        dr.ellipse((0, 0, 2 * r - 1, 2 * r - 1), outline=col + (255,), width=ring)
    # the bell 0.6 of the side, its drawing's middle (y 4.5 … 22.5) on the icon's; on a MIP block in black (inkOn: very
    # low FF0000 and extremely high FF55FF read better in black, SPEC §14.3)
    k = n * SS * 0.6 / 24
    ink = ((0, 0, 0) if mip else (255, 255, 255)) if block else col
    bell(dr, k, r - 12 * k, r - 13.5 * k, 2.6 * k, ink + (255,))
    img = big.reduce(SS)
    if mip:
        px = img.load()
        for y in range(n):
            for x in range(n):
                c = px[x, y]
                px[x, y] = nearest_mip(c[:3]) + (255,) if c[3] >= 128 else (0, 0, 0, 0)
    return img


def drawables(n, mip, alerts=False):
    rows = "    <bitmap id=\"LauncherIcon\" filename=\"launcher_icon.png\" dithering=\"none\"/>\n"
    if alerts:
        rows += "".join("    <bitmap id=\"Alert%s\" filename=\"alert_%s.png\" dithering=\"none\" scope=\"background\"/>\n"
                        % (k[0].upper() + k[1:], k) for k in ALERTS)
    return (HEAD + "<!-- The launcher icon at %d px%s%s (apps/garmin/tools/launcher_icons.py). -->\n<drawables>\n%s"
            "</drawables>\n" % (n, ", 64-colour MIP" if mip else "",
                                "; the alerts' icons for Garmin's notification, one per colour" if alerts else "", rows))


def files():
    """path → bytes / str of everything this writes"""
    out = {}
    for app in APPS:
        tg = targets(app)
        for n, mip in sorted(set(tg.values())):
            d = GARMIN / app / folder(n, mip) / "drawables"
            out[d / "launcher_icon.png"] = icon(n, mip)
            out[d / "drawables.xml"] = drawables(n, mip, app == "app")
            if app == "app":
                for k in ALERTS:
                    out[d / ("alert_%s.png" % k)] = alert_icon(n, mip, k)
        for name, manifest in JUNGLES.get(app, (("monkey.jungle", "manifest.xml"),)):
            jungle, tj = GARMIN / app / name, targets(app, manifest)
            lines = [BEGIN, "# each watch's launcher icon at its launcherIcon size (MIP: the 64-colour one)"]
            lines += ["%s.resourcePath = $(%s.resourcePath);%s" % (p, p, folder(*tj[p])) for p in sorted(tj)]
            lines.append(END)
            text = jungle.read_text()
            block = "\n".join(lines)
            if BEGIN in text:
                text = text[:text.index(BEGIN)] + block + text[text.index(END) + len(END):]
            else:
                text = text.rstrip("\n") + "\n" + block + "\n"
            out[jungle] = text
    return out


def same(path, want):
    if not path.exists():
        return False
    if isinstance(want, str):
        return path.read_text() == want
    have = Image.open(path).convert("RGBA")
    return have.size == want.size and have.tobytes() == want.tobytes()


def main():
    if "--check" in sys.argv:
        if not DEVICES.exists():  # no SDK device files here (CI's Linux): nothing to compare with
            return
        bad = [str(p.relative_to(ROOT)) for p, w in files().items() if not same(p, w)]
        if bad:
            sys.exit("launcher_icons.py: out of date (run uv run apps/garmin/tools/launcher_icons.py): " + ", ".join(bad))
        return
    if not DEVICES.exists():
        sys.exit("launcher_icons.py: no Connect IQ device files at %s" % DEVICES)
    for p, w in files().items():
        p.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(w, str):
            p.write_text(w)
        else:
            w.save(p, optimize=True)
        print(p.relative_to(ROOT))


if __name__ == "__main__":
    main()
