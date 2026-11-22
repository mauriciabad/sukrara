# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The watch app's screens against the reference (contracts/watchface/app/SPEC.md, tools/watchface/app.js): the
regression test of every case of contracts/watchface/app/cases.json.

  uv run --with pillow --with pyobjc-framework-Quartz apps/garmin/app/tools/app_test.py run OUTDIR
        builds the case loader (build.sh app cases), runs it in the Connect IQ simulator (started if needed), takes a
        screenshot of every case and its input (the AppData the watch drew it from), draws the reference from that
        same input (tools/watchface/app-render.html in headless Chrome; set CHROME to its binary), and writes
        OUTDIR/sim, ref, diff, pairs (simulator | reference) and report.txt. Exit 1 when a case fails:
          - its input differs from contracts/watchface/app/expect.json's (numbers ±0.01, everything else exact): the
            watch made something else of the same payload;
          - its screenshot differs from its reference docs/img/app/cases/<watch>/<id>.png in more pixels (over 15 %) than
            expect.json's budget for it: the drawing changed.
        A reference app.js now draws differently from the committed one is reported, not failed (the design changed:
        look at the pairs, then update). CIQ_DEVICE=<product> runs another watch (default descentmk343mm): its screen
        class's reference (app.js cfg.cls; two-button rounds cfg.buttons 'two'), its own expectations
        (expect.json[<product>]) and references (docs/img/app/cases/<product>/); one watch per class family has them
        (DEVICES below, QA Q32), another gets a report only. APP_CASES=id,id… only those cases.
  … app_test.py update OUTDIR
        the same run, then writes expect.json (each case's input, and its budget: the pixels it differs by now, + 15 %
        and 150) and the references. Look at OUTDIR/pairs first: the update accepts what the watch draws.

The budgets hold what app.js can't match yet (the % column's approximation, SPEC §4.1; AMOLED alpha) so a new
difference shows. Not in CI (the simulator's clock is the Mac's time zone: the clock and the reading's time are drawn).
"""
import importlib.util
import json
import os
import shutil
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
DEVICES_DIR = os.path.expanduser(os.environ.get("CIQ_DEVICES", "~/Library/Application Support/Garmin/ConnectIQ/Devices"))
# the watches with expectations: one per screen class family (SPEC §14)
DEVICES = ("descentmk343mm", "fr265s", "fenix843mm", "fenix847mm", "fenix9pro51mm", "fr255s", "fenix7", "venusq2", "venux1")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
RENDER = os.path.join(ROOT, "tools", "watchface", "app-render.html")
EXPECT = os.path.join(ROOT, "contracts", "watchface", "app", "expect.json")
REFS = os.path.join(ROOT, "docs", "img", "app", "cases")


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


GT = load("garmin_test", os.path.join(GARMIN, "aura", "tools", "garmin_test.py"))
sys.path.insert(0, os.path.join(GARMIN, "tools"))
import simshot  # noqa: E402


def device():
    """the watch's screen class (app.js CLS), screen, glance content area and whether it has UP / DOWN"""
    c = json.load(open(os.path.join(DEVICES_DIR, DEVICE, "compiler.json"), encoding="utf-8"))
    sim = json.load(open(os.path.join(DEVICES_DIR, DEVICE, "simulator.json"), encoding="utf-8"))
    fonts = load("app_fonts_cls", os.path.join(HERE, "fonts.py"))
    keys = [k.get("id") for k in sim.get("keys", [])]
    return {"cls": fonts.FAMILY[c["deviceFamily"]], "w": c["resolution"]["width"], "h": c["resolution"]["height"],
            "ga": sim["glance"]["contentArea"], "two": "up" not in keys}


DEV = None


def dev():
    global DEV
    if DEV is None:
        DEV = device()
    return DEV


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
        deadline = time.time() + 8 * len(ids) + 120  # each case 3 s once out, however slow the simulator is
        while time.time() < deadline and proc.poll() is None and len(shots) < len(ids):
            time.sleep(0.2)
            if offset is None:
                if lines(log, "CAL"):
                    time.sleep(0.8)
                    for k in range(8):
                        try:
                            offset = simshot.calibrate(DEVICE, [(dev()["w"] * a // 390, dev()["w"] * b // 390)
                                                           for a, b in ((100, 100), (288, 100), (100, 288), (288, 288))])
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
        # the last case's input is printed after its shot: wait for the loader's end
        end = time.time() + 30
        while time.time() < end and proc.poll() is None and not lines(log, "DONE"):
            time.sleep(0.5)
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
    if x["screen"] == "setup" or x["screen"] == "glance" and not x.get("sent"):
        pass  # not set up: no reading by design
    elif not x.get("sent") or x.get("g") is None and x.get("sensorState") != "warmup":
        return None
    if x["screen"] == "actions" and len(x.get("rows") or []) < 2:
        return None
    if x["screen"] == "appNums" and not x.get("rep"):
        return None
    if x["screen"] == "logPen":
        return None  # the phone's pen question: not in app.js
    k = dev()
    extra = {"cls": k["cls"]}
    if k["two"]:
        extra["buttons"] = "two"
    url = "file://" + urllib.parse.quote(RENDER) + "#cfg=" + urllib.parse.quote(json.dumps({"watch": x, "extra": extra}))
    a = [CHROME, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=1", "--allow-file-access-from-files",
         "--window-size=%d,%d" % (k["w"], k["h"]), "--virtual-time-budget=4000", "--screenshot=" + path, url]
    subprocess.run(a, check=True, timeout=60, capture_output=True)
    return path


def pair(sim, ref, out, glance):
    """simulator | reference side by side (the glance: its band of the screen), labelled."""
    from PIL import Image, ImageDraw
    a = GT.srgb(sim)
    b = Image.open(ref).convert("RGB") if ref else Image.new("RGB", a.size, (40, 0, 0))
    if glance:
        # the glance's band of the screen (its content area, centred, and a margin)
        k = dev()
        m = k["ga"]["height"] // 2 + 30
        box = (0, max(0, k["h"] // 2 - m), k["w"], min(k["h"], k["h"] // 2 + m))
        a, b = a.crop(box), b.crop(box)
    w, h = a.width, a.height
    im = Image.new("RGB", (2 * w + 12, h + 22), (24, 24, 26))
    im.paste(a, (0, 22))
    im.paste(b, (w + 12, 22))
    dr = ImageDraw.Draw(im)
    dr.text((6, 5), "simulator (%s)" % DEVICE, fill=(200, 200, 200))
    dr.text((w + 18, 5), "app.js" if ref else "app.js: no such state", fill=(200, 200, 200))
    im.save(out)


def same(a, b, path=""):
    """The differences between two inputs (numbers ±0.01), as paths."""
    if isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool) and not isinstance(b, bool):
        return [] if abs(a - b) <= 0.01 else [path]
    if isinstance(a, dict) and isinstance(b, dict):
        return [d for k in sorted(set(a) | set(b)) for d in same(a.get(k), b.get(k), path + "." + k)]
    if isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            return [path + "[]"]
        return [d for i in range(len(a)) for d in same(a[i], b[i], "%s[%d]" % (path, i))]
    return [] if a == b else [path]


def pixels(a, b):
    """Pixels over 15 % different between two pictures (the drift of a reference)."""
    from PIL import Image, ImageChops
    d = ImageChops.difference(Image.open(a).convert("RGB"), Image.open(b).convert("RGB")).convert("L")
    return sum(1 for v in d.tobytes() if v > 255 * 0.15)


def run(outdir, update=False):
    for sub in ("sim", "ref", "diff", "pairs"):
        os.makedirs(os.path.join(outdir, sub), exist_ok=True)
    shots, inputs, perf = simulate(outdir)
    check = DEVICE in DEVICES
    expect = json.load(open(EXPECT, encoding="utf-8")) if os.path.exists(EXPECT) else {}
    cases = expect.setdefault(DEVICE, {})
    refs = os.path.join(REFS, DEVICE)
    fails, notes, copies = [], [], []
    report = ["pictures: pixels over 15 % different inside the screen (the glance: its band), simulator against app.js"
              " (now | budget)"]
    for cid in sorted(inputs):
        x = inputs[cid]
        ref = reference(x, os.path.join(outdir, "ref", cid + ".png"))
        if cid not in shots:
            report.append("  %-28s no screenshot" % cid)
            fails.append("%s: no screenshot" % cid)
            continue
        pair(shots[cid], ref, os.path.join(outdir, "pairs", cid + ".png"), x["screen"] == "glance")
        n = GT.diff(shots[cid], ref, os.path.join(outdir, "diff", cid + ".png")) if ref else None
        want = cases.get(cid)
        if update and check:
            cases[cid] = {"input": x, "budget": None if n is None else int(n * 1.15) + 150}
            if ref:
                copies.append((ref, os.path.join(refs, cid + ".png")))
        elif check and want is None:
            fails.append("%s: not in expect.json (update)" % cid)
        elif check:
            bad = same(want["input"], x)
            if bad:
                fails.append("%s: the input differs at %s" % (cid, ", ".join(bad[:6])))
            committed = os.path.join(refs, cid + ".png")
            if ref and os.path.exists(committed):
                m = GT.diff(shots[cid], committed, os.path.join(outdir, "diff", cid + ".committed.png"))
                if want["budget"] is not None and m > want["budget"]:
                    fails.append("%s: %d pixels differ from its reference (budget %d)" % (cid, m, want["budget"]))
                if pixels(ref, committed) > 400:
                    notes.append("%s: app.js now draws it differently from docs/img/app/cases (the design changed?)" % cid)
        report.append("  %-28s %s | %s" % (cid, "-" if n is None else n, (want or {}).get("budget", "-")))
    report += ["", "cost: layout ms | paint ms | memory used / total | elements"]
    for l in perf:
        _, cid, lay, drw, used, total, n = l.split("|")
        report.append("  %-28s %4s %5s  %7s / %s  %s" % (cid, lay, drw, used, total, n))
    ids = [c["id"] for c in json.load(open(os.path.join(APP, "build", "cases", "ids.json"), encoding="utf-8"))]
    # a size the class's fonts lack (AppDraw.missing: tools/fonts.py)
    for key in sorted(set(l.split("|")[2] for l in lines(os.path.join(outdir, "cases.log"), "FONT|missing|"))):
        fails.append("the font %s is not in the class's fonts (tools/fonts.py)" % key)
    missing = [i for i in ids if i not in inputs or i not in shots]
    if missing:
        fails.append("no input or screenshot for %s (the simulator wedged? quit and start it again)" % ", ".join(missing))
    if update and check and missing:
        report.append("not updated: a run with every case first")
    elif update and check:
        os.makedirs(refs, exist_ok=True)
        for src, dst in copies:
            shutil.copyfile(src, dst)
        expect["about"] = ("Per watch (one per screen class family), each case of cases.json as the watch app takes it (its"
                           " input: the AppData it draws from) and its picture budget against"
                           " docs/img/app/cases/<watch>/<id>.png (pixels over 15 % different):"
                           " apps/garmin/app/tools/app_test.py run checks both, update writes them (CIQ_DEVICE=<watch>).")
        with open(EXPECT, "w", encoding="utf-8") as f:
            json.dump(expect, f, indent=1, ensure_ascii=False, sort_keys=True)
            f.write("\n")
        report += ["", "updated %s and %s" % (EXPECT, refs)]
    report += [""] + ["FAIL " + f for f in fails] + ["NOTE " + m for m in notes]
    text = "\n".join(report) + "\n"
    open(os.path.join(outdir, "report.txt"), "w").write(text)
    print(text)
    return 1 if fails else 0


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] in ("run", "update"):
        sys.exit(run(sys.argv[2], sys.argv[1] == "update"))
    else:
        print(__doc__)
        sys.exit(2)
