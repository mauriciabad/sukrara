# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The watch app's screens against the reference (contracts/watchface/app/SPEC.md, tools/watchface/app.js).

  uv run --with pillow --with pyobjc-framework-Quartz apps/garmin/app/tools/app_test.py run OUTDIR
        builds the case loader (build.sh app cases), runs it in the Connect IQ simulator (started if needed), takes a
        screenshot of every case of contracts/watchface/app/cases.json and its input (the AppData the watch drew it
        from), draws the reference from that same input (tools/watchface/app-render.html in headless Chrome; set
        CHROME to its binary), and writes OUTDIR/sim, ref, diff, pairs (simulator | reference) and report.txt: the
        pixels over 15 % different, and the frame cost and memory. CIQ_DEVICE=<product> runs another watch;
        APP_CASES=id,id… only those cases.

Not yet a pass / fail test: the reference draws the % column as app.js's approximation (SPEC §4.1) and app.js's own
words; the report lists the differences to look at.
"""
import importlib.util
import json
import os
import signal
import subprocess
import sys
import time
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
GARMIN = os.path.dirname(APP)
ROOT = os.path.dirname(os.path.dirname(GARMIN))
DEVICE = os.environ.get("CIQ_DEVICE", "descentmk343mm")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
RENDER = os.path.join(ROOT, "tools", "watchface", "app-render.html")
# the glance's band of the screen (app.js S.glance: the area y −51.5 … 51.5 and its neighbours' rules at ±84)
GLANCE_BOX = (0, 105, 390, 285)


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


GT = load("garmin_test", os.path.join(GARMIN, "aura", "tools", "garmin_test.py"))
sys.path.insert(0, os.path.join(GARMIN, "tools"))
import simshot  # noqa: E402


def lines(log, prefix):
    return [l.rstrip("\n") for l in open(log, encoding="utf-8", errors="replace") if l.startswith(prefix)]


def simulate(outdir):
    """One case loader run: {id: screenshot}, {id: input}, the PERF lines."""
    from PIL import Image, ImageChops
    e = GT.env()
    subprocess.run([os.path.join(GARMIN, "build.sh"), "app", "cases"], check=True, env=e, stdout=subprocess.DEVNULL)
    ids = [c["id"] for c in json.load(open(os.path.join(APP, "build", "cases", "ids.json"), encoding="utf-8"))]
    if not GT.simulator_running():
        GT.start_simulator()
    log = os.path.join(outdir, "cases.log")
    with open(log, "w") as lf:
        proc = subprocess.Popen([os.path.join(GT.sdk(), "bin", "monkeydo"), os.path.join(GARMIN, "bin", "Sukrara-cases.prg"),
                                 DEVICE], stdout=lf, stderr=subprocess.STDOUT, env=e, start_new_session=True)
    shots, offset = {}, None
    try:
        deadline = time.time() + 3.2 * len(ids) + 90
        while time.time() < deadline and proc.poll() is None and len(shots) < len(ids):
            time.sleep(0.2)
            if offset is None:
                if lines(log, "CAL"):
                    time.sleep(0.8)
                    for k in range(8):
                        try:
                            offset = simshot.calibrate(DEVICE)
                            break
                        except SystemExit:
                            if k == 7:
                                raise
                            time.sleep(0.5)
                continue
            cs = lines(log, "CASE ")
            if not cs:
                continue
            cid = cs[-1].split()[2]
            if cid in shots:
                continue
            time.sleep(0.7)
            path = os.path.join(outdir, "sim", cid + ".png")
            simshot.shot(path, DEVICE, offset=offset)
            time.sleep(0.25)
            simshot.shot(path + ".2.png", DEVICE, offset=offset)
            moved = ImageChops.difference(Image.open(path), Image.open(path + ".2.png")).getbbox()
            os.remove(path + ".2.png")
            if moved or lines(log, "CASE ")[-1].split()[2] != cid:
                os.remove(path)
                continue
            shots[cid] = path
    finally:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except OSError:
            pass
    inputs = {}
    for l in lines(log, "IN|"):
        _, cid, js = l.split("|", 2)
        inputs[cid] = json.loads(js.replace('\\"', '"'))  # monkeydo escapes the quotes
    return shots, inputs, lines(log, "PERF|")


def reference(x, path):
    """app.js's picture of the watch's input x (None when the reference has no such state)."""
    if not x.get("sent") or x.get("g") is None and x.get("sensorState") != "warmup":
        return None
    if x["screen"] == "actions" and len(x.get("rows") or []) < 2:
        return None
    if x["screen"] == "appNums" and not x.get("rep"):
        return None
    url = "file://" + urllib.parse.quote(RENDER) + "#cfg=" + urllib.parse.quote(json.dumps({"watch": x}))
    a = [CHROME, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=1", "--allow-file-access-from-files",
         "--window-size=390,390", "--virtual-time-budget=4000", "--screenshot=" + path, url]
    subprocess.run(a, check=True, timeout=60, capture_output=True)
    return path


def pair(sim, ref, out, glance):
    """simulator | reference side by side (the glance: its band of the screen), labelled."""
    from PIL import Image, ImageDraw
    a = GT.srgb(sim)
    b = Image.open(ref).convert("RGB") if ref else Image.new("RGB", a.size, (40, 0, 0))
    if glance:
        a, b = a.crop(GLANCE_BOX), b.crop(GLANCE_BOX)
    w, h = a.width, a.height
    im = Image.new("RGB", (2 * w + 12, h + 22), (24, 24, 26))
    im.paste(a, (0, 22))
    im.paste(b, (w + 12, 22))
    dr = ImageDraw.Draw(im)
    dr.text((6, 5), "simulator (%s)" % DEVICE, fill=(200, 200, 200))
    dr.text((w + 18, 5), "app.js" if ref else "app.js: no such state", fill=(200, 200, 200))
    im.save(out)


def run(outdir):
    for sub in ("sim", "ref", "diff", "pairs"):
        os.makedirs(os.path.join(outdir, sub), exist_ok=True)
    shots, inputs, perf = simulate(outdir)
    report = ["pictures: pixels over 15 % different inside the screen (the glance: its band), simulator against app.js"]
    for cid in sorted(inputs):
        x = inputs[cid]
        ref = reference(x, os.path.join(outdir, "ref", cid + ".png"))
        if cid not in shots:
            report.append("  %-28s no screenshot" % cid)
            continue
        pair(shots[cid], ref, os.path.join(outdir, "pairs", cid + ".png"), x["screen"] == "glance")
        n = GT.diff(shots[cid], ref, os.path.join(outdir, "diff", cid + ".png")) if ref else None
        report.append("  %-28s %s" % (cid, "-" if n is None else n))
    report += ["", "cost: layout ms | paint ms | memory used / total | elements"]
    for l in perf:
        _, cid, lay, drw, used, total, n = l.split("|")
        report.append("  %-28s %4s %5s  %7s / %s  %s" % (cid, lay, drw, used, total, n))
    text = "\n".join(report) + "\n"
    open(os.path.join(outdir, "report.txt"), "w").write(text)
    print(text)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "run":
        run(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
