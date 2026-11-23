# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The data field's screens against the reference (contracts/watchface/app/SPEC.md §9, §14.4; tools/watchface/app.js
S.dfSlot, S.dfAlert), a regression test.

  uv run --with pillow --with pyobjc-framework-Quartz apps/garmin/field/tools/field_test.py run OUTDIR [--update]
        builds the case loader (build.sh field cases) for CIQ_DEVICE (default descentmk343mm), runs it in the Connect IQ
        simulator (started if needed), takes a screenshot of every case of contracts/watchface/field/cases.json and its
        input (what the field drew it from), and diffs each with its reference, docs/img/field/ref/<device>/<case>.png
        (app.js drawing that same input). Writes OUTDIR/sim, ref, diff, pairs (simulator | reference) and report.txt.
        Fails (exit 1) when a case differs in more pixels than its budget (contracts/watchface/field/expect.json), when
        its input differs from the one the reference was drawn from, or when a case is missing; the build (cases.py) stops
        when a state's payload version isn't the apps' VERSION (SukraraFace.VERSION, sdk/source/read/SukraraFace.mc).
        --update: draws the references from this run's inputs (headless Chrome; set CHROME to its binary), copies them
        to docs/img/field/ref/<device>/ with the inputs, and sets each budget to the run's count + 25 % + 40 px. Look
        at the pairs before committing them.
        FIELD_CASES=id,id… only those cases (--update then keeps the other cases' references and budgets).

The pictures are compared in sRGB, pixels over 15 % different inside the screen (the circle, or the rectangle's
rounded corners): the simulator draws the atlas glyphs a pixel apart from the browser's text and its lines without the
browser's antialiasing, so a budget is never 0; a regression shows as hundreds of pixels.
"""
import importlib.util
import json
import math
import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
FIELD = os.path.dirname(HERE)
GARMIN = os.path.dirname(FIELD)
ROOT = os.path.dirname(os.path.dirname(GARMIN))
DEVICE = os.environ.get("CIQ_DEVICE", "descentmk343mm")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
RENDER = os.path.join(ROOT, "tools", "watchface", "app-render.html")
REFS = os.path.join(ROOT, "docs", "img", "field", "ref")
EXPECT = os.path.join(ROOT, "contracts", "watchface", "field", "expect.json")


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


GT = load("garmin_test", os.path.join(GARMIN, "aura", "tools", "garmin_test.py"))
CASES = load("field_cases", os.path.join(HERE, "cases.py"))
sys.path.insert(0, os.path.join(GARMIN, "tools"))
import simshot  # noqa: E402


def lines(log, prefix):
    return [l.rstrip("\n") for l in open(log, encoding="utf-8", errors="replace") if l.startswith(prefix)]


def screen():
    _, w, h = CASES.device_class(DEVICE)
    return w, h


def simulate(outdir, only=None, tag=""):
    """One case loader run (only: those cases): [ids], {id: screenshot}, {id: input}, the PERF lines."""
    from PIL import Image, ImageChops
    e = GT.env()
    e["CIQ_DEVICE"] = DEVICE
    if only:
        e["FIELD_CASES"] = ",".join(only)
    subprocess.run([os.path.join(GARMIN, "build.sh"), "field", "cases"], check=True, env=e, stdout=subprocess.DEVNULL)
    ids = [c["id"] for c in json.load(open(os.path.join(FIELD, "build", "cases", "ids.json"), encoding="utf-8"))["cases"]]
    w, _ = screen()
    marks = [(w * a // 390, w * b // 390) for a, b in ((100, 100), (288, 100), (100, 288), (288, 288))]
    if not GT.simulator_running():
        GT.start_simulator()
    log = os.path.join(outdir, "cases%s.log" % tag)
    with open(log, "w") as lf:
        proc = subprocess.Popen([os.path.join(GT.sdk(), "bin", "monkeydo"), os.path.join(GARMIN, "bin", "SukraraField-cases.prg"),
                                 DEVICE], stdout=lf, stderr=subprocess.STDOUT, env=e, start_new_session=True)
    shots, offset = {}, None
    started = time.time()
    try:
        deadline = time.time() + 3.2 * len(ids) + 90
        while time.time() < deadline and proc.poll() is None and len(shots) < len(ids):
            time.sleep(0.2)
            if offset is None and not lines(log, "CAL") and time.time() - started > 45:
                # monkeydo reaches a wedged simulator and prints nothing: restart it (the next run takes the cases)
                print("the simulator doesn't answer: restarting it")
                os.killpg(proc.pid, signal.SIGKILL)
                GT.stop_simulator()
                GT.start_simulator()
                time.sleep(6)  # its port answers a few seconds after its window
                break
            if offset is None:
                if lines(log, "CAL"):
                    time.sleep(0.8)
                    for k in range(8):
                        try:
                            offset = simshot.calibrate(DEVICE, marks)
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
            # every case draws something inside the screen: a black one is a frame between cases
            im = Image.open(path).convert("L")
            blank = ImageChops.multiply(im, mask(im.size)).getextrema()[1] < 40
            if moved or blank or lines(log, "CASE ")[-1].split()[2] != cid:
                os.remove(path)
                continue
            shots[cid] = path
        # the last case's input comes on its next frame
        end = time.time() + 4
        while time.time() < end and len(lines(log, "IN|")) < len(ids):
            time.sleep(0.3)
    finally:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except OSError:
            pass
    inputs = {}
    for l in lines(log, "IN|"):
        _, cid, js = l.split("|", 2)
        inputs[cid] = json.loads(js.replace('\\"', '"'))  # monkeydo escapes the quotes
    return ids, shots, inputs, lines(log, "PERF|")


def reference(x, path):
    """app.js's picture of the field's input x."""
    w, h = screen()
    url = "file://" + urllib.parse.quote(RENDER) + "#cfg=" + urllib.parse.quote(json.dumps({"watch": x}))
    a = [CHROME, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=1", "--allow-file-access-from-files",
         "--window-size=%d,%d" % (w, h), "--virtual-time-budget=4000", "--screenshot=" + path, url]
    for _ in range(3):
        subprocess.run(a, timeout=60, capture_output=True)
        if os.path.exists(path):
            return path
    raise SystemExit("field_test: headless Chrome drew no reference for %s" % path)


def mask(size):
    """The screen's shape (the circle, or the rectangle with its rounded corners), 3 px in from the bezel overlay."""
    from PIL import Image, ImageDraw
    m = Image.new("L", size, 0)
    w, h = size
    if w == h:
        ImageDraw.Draw(m).ellipse((3, 3, w - 4, h - 4), fill=255)
    else:
        ImageDraw.Draw(m).rounded_rectangle((3, 3, w - 4, h - 4), radius=40 if w > 400 else 20, fill=255)
    return m


def diff(sim_path, ref_path, out_path):
    """Pixels over 15 % different inside the screen; the diff image shows both dimmed and those pixels red."""
    from PIL import Image, ImageChops
    sim = GT.srgb(sim_path)
    ref = Image.open(ref_path).convert("RGB")
    if ref.size != sim.size:
        ref = ref.crop((0, 0) + sim.size)
    d = ImageChops.difference(sim, ref).convert("L")
    bad = ImageChops.multiply(d.point(lambda v: 255 if v > 255 * 0.15 else 0), mask(sim.size))
    count = sum(1 for v in bad.tobytes() if v)
    base = Image.blend(ref, sim, 0.5).point(lambda v: v // 2)
    base.paste(Image.new("RGB", sim.size, (255, 40, 40)), mask=bad)
    base.save(out_path)
    return count


def pair(sim, ref, out):
    from PIL import Image, ImageDraw
    a = GT.srgb(sim)
    b = Image.open(ref).convert("RGB") if ref and os.path.exists(ref) else Image.new("RGB", a.size, (40, 0, 0))
    w, h = a.width, a.height
    im = Image.new("RGB", (2 * w + 12, h + 22), (24, 24, 26))
    im.paste(a, (0, 22))
    im.paste(b.crop((0, 0, w, h)), (w + 12, 22))
    dr = ImageDraw.Draw(im)
    dr.text((6, 5), "simulator (%s)" % DEVICE, fill=(200, 200, 200))
    dr.text((w + 18, 5), "app.js", fill=(200, 200, 200))
    im.save(out)


def same_input(a, b):
    """The watch's input against the one the reference was drawn from (floats to 3 decimals)."""
    def norm(v):
        if isinstance(v, float):
            return round(v, 3)
        if isinstance(v, list):
            return [norm(x) for x in v]
        if isinstance(v, dict):
            return {k: norm(x) for k, x in v.items()}
        return v
    return norm(a) == norm(b)


def run(outdir, update):
    for sub in ("sim", "ref", "diff", "pairs"):
        os.makedirs(os.path.join(outdir, sub), exist_ok=True)
    ids, shots, inputs, perf = simulate(outdir)
    # a case the screenshots missed (the simulator busy or restarted): its own run, three times at most
    for k in range(3):
        left = [c for c in ids if c not in shots or c not in inputs]
        if not left:
            break
        print("again for %d cases: %s" % (len(left), ", ".join(left)))
        _, s2, i2, p2 = simulate(outdir, left, str(k + 2))
        shots.update(s2)
        inputs.update(i2)
        perf += p2
    refdir = os.path.join(REFS, DEVICE)
    expect = json.load(open(EXPECT, encoding="utf-8")) if os.path.exists(EXPECT) else {}
    budgets = expect.setdefault(DEVICE, {})
    saved = os.path.join(refdir, "inputs.json")
    known = json.load(open(saved, encoding="utf-8")) if os.path.exists(saved) and not update else {}
    if update:
        # the references, four Chromes at a time
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(4) as pool:
            list(pool.map(lambda c: reference(inputs[c], os.path.join(outdir, "ref", c + ".png")),
                          [c for c in ids if c in inputs]))
    report = ["pictures (%s): pixels over 15 %% different inside the screen, simulator against app.js; budget" % DEVICE]
    fails = []
    for cid in ids:
        x = inputs.get(cid)
        if cid not in shots or x is None:
            report.append("  %-24s no %s" % (cid, "screenshot" if cid not in shots else "input"))
            fails.append(cid)
            continue
        if update:
            ref = os.path.join(outdir, "ref", cid + ".png")
        else:
            ref = os.path.join(refdir, cid + ".png")
            if not os.path.exists(ref):
                report.append("  %-24s no reference (run with --update)" % cid)
                fails.append(cid)
                continue
            if cid in known and not same_input(known[cid], x):
                report.append("  %-24s the input differs from the reference's (run with --update and look)" % cid)
                fails.append(cid)
        pair(shots[cid], ref, os.path.join(outdir, "pairs", cid + ".png"))
        n = diff(shots[cid], ref, os.path.join(outdir, "diff", cid + ".png"))
        if update:
            budgets[cid] = int(math.ceil(n * 1.25)) + 40
        b = budgets.get(cid)
        bad = b is None or n > b
        report.append("  %-24s %6d  %s%s" % (cid, n, b if b is not None else "-", "  OVER" if bad else ""))
        if bad:
            fails.append(cid)
    if update:
        # every case: the references are replaced; FIELD_CASES: only those cases', the others kept
        subset = bool(os.environ.get("FIELD_CASES"))
        os.makedirs(refdir, exist_ok=True)
        kept = json.load(open(saved, encoding="utf-8")) if subset and os.path.exists(saved) else {}
        if not subset:
            for f in os.listdir(refdir):
                os.remove(os.path.join(refdir, f))
        for cid in ids:
            src = os.path.join(outdir, "ref", cid + ".png")
            if os.path.exists(src):
                shutil.copy(src, os.path.join(refdir, cid + ".png"))
        kept.update({cid: inputs[cid] for cid in ids if cid in inputs})
        with open(saved, "w", encoding="utf-8") as f:
            json.dump(kept, f, indent=0, sort_keys=True)
            f.write("\n")
        if not subset:
            expect[DEVICE] = {k: budgets[k] for k in ids if k in budgets}
        with open(EXPECT, "w", encoding="utf-8") as f:
            json.dump(dict(sorted(expect.items())), f, indent=1)
            f.write("\n")
    report += ["", "cost (the simulator on this Mac): layout ms | paint ms | memory used / total | elements"]
    for l in perf:
        _, cid, lay, drw, used, total, n = l.split("|")
        report.append("  %-24s %4s %5s  %7s / %s  %s" % (cid, lay, drw, used, total, n))
    report.append("")
    report.append("FAIL: %s" % ", ".join(fails) if fails else "OK: %d cases" % len(ids))
    text = "\n".join(report) + "\n"
    open(os.path.join(outdir, "report.txt"), "w").write(text)
    print(text)
    return 1 if fails else 0


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "run":
        sys.exit(run(sys.argv[2], "--update" in sys.argv[3:]))
    print(__doc__)
    sys.exit(2)
