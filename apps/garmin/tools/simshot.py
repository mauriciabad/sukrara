# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow>=9", "pyobjc-framework-Quartz>=10; sys_platform == 'darwin'"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The Connect IQ simulator's screen as a 390 x 390 PNG.

  uv run apps/garmin/tools/simshot.py out.png [--device descentmk343mm]   (default: $CIQ_DEVICE, else descentmk343mm)

The SDK has no command for a screenshot. macOS: the simulator's window (screencapture -l by its window id; the terminal
needs Screen Recording permission), the display where the device's simulator.json puts it under the title bar, scaled
back to the device's pixels. Linux (CI, Xvfb without a window manager): the X screen (ImageMagick's import), the
display at simulator.json's place plus the simulator's 26 px menu bar. The debug case loader's calibration frame
(calibrate()) measures how far the real screen is from there; shot() takes that offset.
"""
import json
import os
import subprocess
import sys
import tempfile
import time

from PIL import Image

MAC = sys.platform == "darwin"
TITLE = 28 if MAC else 26  # above simulator.json's display: macOS's title bar; Linux's menu bar (no window manager)
BOX = getattr(Image, "Resampling", Image).BOX  # Pillow 9 (Ubuntu 22.04) has no Image.Resampling


def devices():
    for d in ("~/Library/Application Support/Garmin/ConnectIQ/Devices", "~/.Garmin/ConnectIQ/Devices"):
        if os.path.isdir(os.path.expanduser(d)):
            return os.path.expanduser(d)
    raise SystemExit("simshot: no Connect IQ device files")


def window():
    import Quartz
    ws = Quartz.CGWindowListCopyWindowInfo(Quartz.kCGWindowListOptionAll, Quartz.kCGNullWindowID)
    for w in ws:
        if str(w.get("kCGWindowName", "")).startswith("CIQ Simulator"):
            return w
    return None


def grab():
    """(image, pixels per point) of the simulator's window (macOS) or the X screen (Linux)."""
    w = None
    with tempfile.TemporaryDirectory() as d:
        raw = os.path.join(d, "w.png")
        if MAC:
            w = window()
            if w is None:
                raise SystemExit("simshot: no simulator window (start it: connectiq)")
            cmd = ["screencapture", "-x", "-o", "-l", str(w["kCGWindowNumber"]), raw]
        else:
            cmd = ["import", "-window", "root", raw]
        for _ in range(5):  # screencapture now and then can't read the window: try again
            if subprocess.run(cmd).returncode == 0 and os.path.exists(raw):
                break
            time.sleep(0.5)
        else:
            raise SystemExit("simshot: %s failed (macOS: Screen Recording permission for the terminal?)" % cmd[0])
        im = Image.open(raw).convert("RGB")
        im.load()
    scale = im.width / float(w["kCGWindowBounds"]["Width"]) if MAC else 1.0
    return im, scale


def capture(device):
    """The grab, its scale and where simulator.json puts the display (points from the grab's top left)."""
    sim = json.load(open(os.path.join(devices(), device, "simulator.json")))
    im, scale = grab()
    return im, scale, sim["display"]["location"]


# the case loader's calibration frame: 2 x 2 white squares at these device pixels
MARKS = ((100, 100), (288, 100), (100, 288), (288, 288))


def calibrate(device="descentmk343mm", marks=MARKS):
    """The offset (points) of the display against simulator.json's place, from the calibration frame: the white squares'
    centres found within 8 points of where they should be ([marks]: the frame's squares, default MARKS)."""
    im, scale, loc = capture(device)
    px = im.load()
    offs = []
    for mx, my in marks:
        cx, cy = (loc["x"] + mx + 1) * scale, (loc["y"] + TITLE + my + 1) * scale
        sx = sy = n = 0
        for y in range(int(cy - 8 * scale), int(cy + 8 * scale)):
            for x in range(int(cx - 8 * scale), int(cx + 8 * scale)):
                if sum(px[x, y]) > 600:
                    sx += x + 0.5
                    sy += y + 0.5
                    n += 1
        if not n:
            raise SystemExit("simshot: calibration marks not found")
        offs.append((sx / n / scale - cx / scale, sy / n / scale - cy / scale))
    return (sum(o[0] for o in offs) / 4, sum(o[1] for o in offs) / 4)


def shot(out, device="descentmk343mm", offset=(0.0, 0.0)):
    im, scale, loc = capture(device)
    x0, y0 = loc["x"] + offset[0], loc["y"] + TITLE + offset[1]
    box = (round(x0 * scale), round(y0 * scale), round((x0 + loc["width"]) * scale), round((y0 + loc["height"]) * scale))
    im = im.crop(box)
    if im.width != loc["width"]:
        im = im.resize((loc["width"], loc["height"]), BOX)
    im.save(out)


if __name__ == "__main__":
    args = sys.argv[1:]
    dev = os.environ.get("CIQ_DEVICE", "descentmk343mm")
    if "--device" in args:
        dev = args[args.index("--device") + 1]
    shot(args[0], dev)
