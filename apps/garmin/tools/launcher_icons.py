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
included) are drawn on the pixel grid instead, no antialias: the cube as an isometric pixel cube (2:1 steps, symmetric,
2 px gaps between its faces), the dot a pixel circle. The 64-colour MIP watches (no alpha blending) get the nearest
palette colour, so the compiler has nothing to dither. Sizes and screen types come from the SDK's device files
(compiler.json launcherIcon, displayType).

Sukrara Aura (the face) takes the same icon: Garmin shows a face's launcher icon almost nowhere, but where it does
(the watch's Connect IQ app list) the three apps read as one family.
"""
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
# the pixel-fitted sizes: n → (a, H, t, d, dx, dy): the top face 4a wide and 2a tall (rows of 2 px steps), the side faces
# H tall below a t-row gap, 2 px apart; the dot d px across, its centre dx right of the cube's axis and dy below its top
PIXEL = {38: (6, 9, 2, 10, 15, -1), 40: (6, 10, 2, 10, 15, -1)}


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
    a, h, t, d, dx, dy = PIXEL[n]
    on = set()
    for j in range(2 * a):  # j: the column's distance from the axis; its mirror is column -1 - j
        top, bot = j // 2, 2 * a - 1 - j // 2
        rows = list(range(top, bot + 1))
        if j >= 1:  # the side faces, apart by 2 columns at the axis; their first column steps with the next two
            s0 = 2 * a - 1 - max(j, 2) // 2 + 1 + t
            rows += range(s0, s0 + h - (1 if j == 2 * a - 1 else 0))  # the outer bottom corner rounded off
        for y in rows:
            on |= {(j, y), (-1 - j, y)}
    r = d / 2
    for y in range(dy - d, dy + d):
        for x in range(dx - d, dx + d):
            if (x + 0.5 - dx) ** 2 + (y + 0.5 - dy) ** 2 <= (r - 0.05) ** 2:
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


def drawables(n, mip):
    return (HEAD + "<!-- The launcher icon at %d px%s (apps/garmin/tools/launcher_icons.py). -->\n<drawables>\n"
            "    <bitmap id=\"LauncherIcon\" filename=\"launcher_icon.png\" dithering=\"none\"/>\n</drawables>\n"
            % (n, ", 64-colour MIP" if mip else ""))


def files():
    """path → bytes / str of everything this writes"""
    out = {}
    for app in APPS:
        tg = targets(app)
        for n, mip in sorted(set(tg.values())):
            d = GARMIN / app / folder(n, mip) / "drawables"
            out[d / "launcher_icon.png"] = icon(n, mip)
            out[d / "drawables.xml"] = drawables(n, mip)
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
