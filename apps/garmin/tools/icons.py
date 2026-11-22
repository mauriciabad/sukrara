# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The watch app's complication icons (contracts/watchface/app/SPEC.md §10), at each watch's own complicationIcon size.

  python3 apps/garmin/tools/icons.py           writes apps/garmin/app/resources-icon<N>/ and the jungle's per-product lines
  python3 apps/garmin/tools/icons.py --check   fails when they differ from a fresh run (link_cases.py --check runs it)

The drop is the phone's ds_ic_water_drop, the pen ds_ic_dia_pen (the treatment rows' insulin pen), both from
apps/android/core/designsystem; Garmin's rule for a public complication's icon is an SVG that reads on light and dark
faces, so each is drawn white with a black edge. The size per product comes from the SDK's device files
(compiler.json complicationIcon).
"""
import json
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
APP = ROOT / "apps/garmin/app"
JUNGLE = APP / "monkey.jungle"
DEVICES = pathlib.Path(os.path.expanduser("~/Library/Application Support/Garmin/ConnectIQ/Devices"))
BEGIN = "# BEGIN complication icon sizes (apps/garmin/tools/icons.py)"
END = "# END complication icon sizes"
HEAD = "<!-- Sukrara — https://github.com/mauriciabad/sukrara -->\n<!-- Copyright (C) 2026 Maurici Abad -->\n" \
       "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->\n"

# ds_ic_water_drop (Material Symbols "water_drop", outlined), viewport 0 -960 960 960
DROP = ("M480-80q-137 0-228.5-94T160-408q0-100 79.5-217.5T480-880q161 137 240.5 254.5T800-408q0 140-91.5 234T480-80Z")
# ds_ic_dia_pen: the body capsule, then the dose window, the dose button, the needle (viewport 0 0 960 960)
PEN_BODY = "M737,257 L397,597 A80,80 0 0,1 283,483 L623,143 A80,80 0 0,1 737,257 Z"
PEN_LINES = "M560,300 L470,390 M720,160 L800,80 M290,590 L170,710"


def drop(n):
    return (HEAD + '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="-40 -1000 1040 1040">\n'
            '    <path d="%s" fill="#FFFFFF" stroke="#000000" stroke-width="56" stroke-linejoin="round"/>\n</svg>\n'
            % (n, n, DROP))


def pen(n):
    edge = 'fill="none" stroke="#000000" stroke-width="128" stroke-linecap="round" stroke-linejoin="round"'
    line = 'fill="none" stroke="#FFFFFF" stroke-width="72" stroke-linecap="round" stroke-linejoin="round"'
    return (HEAD + '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="80 0 800 800">\n'
            '    <path d="%s" %s/>\n    <path d="%s" %s/>\n    <path d="%s" %s/>\n    <path d="%s" %s/>\n</svg>\n'
            % (n, n, PEN_BODY, edge, PEN_LINES, edge, PEN_BODY, line, PEN_LINES, line))


DRAWABLES = (HEAD + "<!-- The complication icons at this size (apps/garmin/tools/icons.py; resources/complications). -->\n"
             "<drawables>\n    <bitmap id=\"DropIcon\" filename=\"drop.svg\"/>\n"
             "    <bitmap id=\"LogIcon\" filename=\"pen.svg\"/>\n</drawables>\n")


def sizes():
    """product → its complication icon's side, for every product in the app's manifest"""
    products = re.findall(r'<iq:product id="([^"]+)"', (APP / "manifest.xml").read_text())
    out = {}
    for p in products:
        c = json.loads((DEVICES / p / "compiler.json").read_text()).get("complicationIcon")
        out[p] = c["width"] if c else 40
    return out


def files():
    """path → text of everything this writes"""
    sz = sizes()
    out = {}
    for n in sorted(set(sz.values())):
        d = APP / ("resources-icon%d" % n) / "drawables"
        out[d / "drop.svg"] = drop(n)
        out[d / "pen.svg"] = pen(n)
        out[d / "drawables.xml"] = DRAWABLES
    lines = [BEGIN, "# each watch's complication icons at its complicationIcon size"]
    lines += ["%s.resourcePath = $(%s.resourcePath);resources-icon%d" % (p, p, sz[p]) for p in sorted(sz)]
    lines.append(END)
    text = JUNGLE.read_text()
    block = "\n".join(lines)
    if BEGIN in text:
        text = text[:text.index(BEGIN)] + block + text[text.index(END) + len(END):]
    else:
        text = text.rstrip("\n") + "\n" + block + "\n"
    out[JUNGLE] = text
    return out


def problems():
    if not DEVICES.exists():
        return []
    return [str(p.relative_to(ROOT)) for p, t in files().items() if not p.exists() or p.read_text() != t]


def main():
    if "--check" in sys.argv:
        bad = problems()
        if bad:
            sys.exit("icons.py: out of date (run python3 apps/garmin/tools/icons.py): " + ", ".join(bad))
        return
    for p, t in files().items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(t)
        print(p.relative_to(ROOT))


if __name__ == "__main__":
    main()
