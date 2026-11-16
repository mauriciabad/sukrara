# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow>=10", "pyobjc-framework-Quartz>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The Connect IQ simulator's screen as a 390 x 390 PNG (macOS; the terminal needs Screen Recording permission).

  uv run apps/garmin/tools/simshot.py out.png [--device descentmk343mm]

The SDK has no command for a screenshot: this captures the simulator's window (screencapture -l, by its window id) and
crops the display where the device's simulator.json puts it, scaled back to the device's pixels.
"""
import json
import os
import subprocess
import sys
import tempfile

import Quartz
from PIL import Image

TITLE = 28  # the window's title bar, points


def window():
    ws = Quartz.CGWindowListCopyWindowInfo(Quartz.kCGWindowListOptionOnScreenOnly, Quartz.kCGNullWindowID)
    for w in ws:
        if str(w.get("kCGWindowName", "")).startswith("CIQ Simulator"):
            return w
    return None


def shot(out, device="descentmk343mm"):
    w = window()
    if w is None:
        raise SystemExit("simshot: no simulator window (start it: connectiq)")
    sim = json.load(open(os.path.expanduser("~/Library/Application Support/Garmin/ConnectIQ/Devices/%s/simulator.json" % device)))
    loc = sim["display"]["location"]
    with tempfile.TemporaryDirectory() as d:
        raw = os.path.join(d, "w.png")
        subprocess.run(["screencapture", "-x", "-o", "-l", str(w["kCGWindowNumber"]), raw], check=True)
        im = Image.open(raw).convert("RGB")
        scale = im.width / float(w["kCGWindowBounds"]["Width"])
        box = (round(loc["x"] * scale), round((loc["y"] + TITLE) * scale),
               round((loc["x"] + loc["width"]) * scale), round((loc["y"] + TITLE + loc["height"]) * scale))
        im = im.crop(box)
        if im.width != loc["width"]:
            im = im.resize((loc["width"], loc["height"]), Image.Resampling.BOX)
        im.save(out)


if __name__ == "__main__":
    args = sys.argv[1:]
    dev = "descentmk343mm"
    if "--device" in args:
        dev = args[args.index("--device") + 1]
    shot(args[0], dev)
