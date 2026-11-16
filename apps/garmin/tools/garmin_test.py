# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sukrara Aura's tests (SPEC.md §10). Python 3.9 stdlib only for the numbers; the pictures need Pillow (uv run).

  python3 apps/garmin/tools/garmin_test.py words              the face's words against contracts/graph/words/words.json
  python3 apps/garmin/tools/garmin_test.py numbers LOG        a case-loader log (D|case|path|value lines) against expect.json
  uv run --with pillow --with pyobjc-framework-Quartz apps/garmin/tools/garmin_test.py run OUTDIR [COREREFS]
        builds the case loader, runs it in the Connect IQ simulator (started if needed), checks every case's numbers and
        diffs a screenshot of every case with docs/img/aura/ref/<id>.png and, given, the references without the dial
        (core_refs.mjs) (OUTDIR: sim, diff, diff-core images, report.txt). Exit 1 on a number that differs.
        Linux (CI): python3 with Pillow 9+ and ImageMagick, under Xvfb (xvfb-run); the simulator starts on $DISPLAY.

Numbers: positions and sizes ±0.5 px, angles ±0.1°, opacities ±0.01, the dial's inputs ±0.01, colours and strings
exact; an element's numbers only where it is drawn (its disp), its disp always. Paths compare number by number (a
summarised path by its subpath count). Not compared: the dials, the halo and the seconds sweep's angle (face-aura), the
fonts' family names.
"""
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(APP))
EXPECT = os.path.join(ROOT, "contracts", "watchface", "aura", "expect.json")
CASES = os.path.join(ROOT, "contracts", "watchface", "aura", "cases.json")
REF = os.path.join(ROOT, "docs", "img", "aura", "ref")
WORDS = os.path.join(ROOT, "contracts", "graph", "words", "words.json")

SKIP_GROUPS = {"v", "ids", "metal", "arcs", "dial", "halo"}
SKIP_KEYS = {"fam", "fam2", "labelFam", "d"}  # font families; an icon's path data (the icon key says which)
ANGLES = {"deg"}
OPACITY = {"o", "fo"}
INFO = {"ext", "toward", "I", "spd"}
NUM = re.compile(r"-?\d+(?:\.\d+)?(?:e-?\d+)?")
# the reference's dial names
DIALS = {"halftone": "grain", "topo": "contours", "water": "waterline", "laser": "lasers"}


def flat(obj, prefix=""):
    """expect.json values as {path: value}; arrays of one string (the runtime's text holes) as the string."""
    out = {}
    for k, v in obj.items():
        p = prefix + k
        if isinstance(v, dict) and set(v) == {"path", "chars"}:
            out[p] = v
        elif isinstance(v, dict):
            out.update(flat(v, p + "."))
        elif isinstance(v, list) and len(v) == 1 and isinstance(v[0], str):
            out[p] = v[0]
        else:
            out[p] = v
    return out


def tol(path):
    leaf = path.rsplit(".", 1)[-1]
    if leaf in ANGLES or path.startswith("bead.deg"):
        return 0.1
    if leaf in OPACITY:
        return 0.01
    if path.startswith("info.") and leaf in INFO:
        return 0.01
    return 0.5


def same(path, want, got):
    """None when equal within tolerance, else the reason."""
    if isinstance(want, dict) and "path" in want:
        n = int(want["path"].split()[0])
        k = len(re.findall(r"M", got))
        return None if k == n else "%d subpaths, want %d" % (k, n)
    if want is None:
        return None if got in ("null", "none", "") else "want null"
    if isinstance(want, bool):
        return None if got == ("true" if want else "false") else "want %s" % want
    if isinstance(want, (int, float)):
        try:
            g = float(got)
        except ValueError:
            return "not a number"
        return None if abs(g - want) <= tol(path) + 1e-9 else "want %s" % want
    w = str(want)
    if w == got:
        return None
    # colours: the reference's short #000 forms
    if w.startswith("#") and got.startswith("#") and w.upper().replace("#000", "#000000")[:7] == got.upper():
        return None
    # numbers inside strings (paths, transforms, the glyphs' x list): the same text around the numbers, each number in tolerance
    wn, gn = NUM.findall(w), NUM.findall(got)
    if wn and NUM.sub("#", w).replace(" ", "") == NUM.sub("#", got).replace(" ", "") and len(wn) == len(gn):
        t = 0.1 if path.endswith(".tf") and "rotate" in w and path.startswith("val") else tol(path)
        bad = [(a, b) for a, b in zip(wn, gn) if abs(float(a) - float(b)) > 0.5 + 1e-9]
        if path in ("val.tf",):
            # translate ±0.5, the angle ±0.1
            bad = [(a, b) for i, (a, b) in enumerate(zip(wn, gn)) if abs(float(a) - float(b)) > (0.1 if i == 2 else 0.5) + 1e-9]
        return None if not bad else "numbers differ: %s" % ", ".join("%s≠%s" % x for x in bad[:4])
    return "want %r" % w


def disp_of(path, values):
    """The disp that governs a path (its own element's, else its group's): 'inline' when drawn."""
    parts = path.split(".")
    for i in range(len(parts) - 1, 0, -1):
        base = ".".join(parts[:i])
        for key in ("disp", "d"):
            if base + "." + key in values and path != base + "." + key:
                if values[base + "." + key] == "none":
                    return "none"
    return "inline"


SUBDISP = {  # flags inside an element that govern some of its fields
    "val": {"blk": ("bx", "by", "bw", "bh"), "strike": ("sx1", "sx2", "sy", "sw"), "arrowD": ("arrow", "tf"),
            "ringD": ("ringR", "ringW"), "revealD": ("rx1", "rx2", "ry")},
    "sp": {"rayD": ("ray", "rayCol")},
    "rail": {"zoneDisp": ("zone", "zoneCol"), "minorDisp": ("minor",)},
    "bead": {"chevDisp": ("chev",)},
    "batt": {"l2D": ("l2", "y2")},
    "msg": {"discD": ("pie", "dx", "dy"), "flagD": ("flag", "pole")},
}


def compare(case_id, want, got):
    fails = []
    w = flat(want)
    for path, val in w.items():
        group = path.split(".")[0]
        leaf = path.rsplit(".", 1)[-1]
        if group in SKIP_GROUPS or leaf in SKIP_KEYS:
            continue
        if group == "radar" and leaf in ("deg", "tr"):
            continue
        if group == "pal":
            continue  # the palette is checked through every colour that uses it
        if disp_of(path, w) == "none":
            continue
        sub = SUBDISP.get(group, {})
        hidden = any(w.get(group + "." + flag) == "none" and leaf in fields for flag, fields in sub.items())
        if hidden:
            continue
        if path == "info.dial":
            val = DIALS.get(val, val)
            if path not in got:
                continue
        if path not in got:
            fails.append("%s: %s missing" % (case_id, path))
            continue
        why = same(path, val, got[path])
        if why:
            fails.append("%s: %s = %r (%s)" % (case_id, path, got[path], why))
    return fails


def parse(log):
    cases = {}
    for line in open(log, encoding="utf-8", errors="replace"):
        line = line.rstrip("\n")
        if line.startswith("D|"):
            _, cid, path, val = line.split("|", 3)
            cases.setdefault(cid, {})[path] = val
    return cases


def numbers(log):
    want = {c["id"]: c["values"] for c in json.load(open(EXPECT, encoding="utf-8"))["cases"]}
    got = parse(log)
    fails, done = [], 0
    for cid, w in want.items():
        if cid not in got:
            continue
        done += 1
        fails += compare(cid, w, got[cid])
    return done, len(want), fails


def words():
    """The face's words (AuraWords.mc) are Sukrara's card words in both languages."""
    f = json.load(open(WORDS, encoding="utf-8"))
    w = dict(f["words"])
    w.update({k: v for k, v in f.get("faces", {}).items() if not k.startswith("_")})
    src = open(os.path.join(APP, "source", "AuraWords.mc"), encoding="utf-8").read()
    fails = []
    found = re.findall(r'return es\(lang\) \? "([^"]*)" : "([^"]*)"; } // (\w+)', src)
    if len(found) < 11:
        fails.append("AuraWords.mc: only %d words found (the pattern changed?)" % len(found))
    for m in re.finditer(r'return es\(lang\) \? "([^"]*)" : "([^"]*)"; } // (\w+)', src):
        es, en, key = m.groups()
        if key not in w:
            fails.append("%s: not in words.json" % key)
        elif (w[key]["en"], w[key]["es"]) != (en, es):
            fails.append("%s: en %r es %r, words.json en %r es %r" % (key, en, es, w[key]["en"], w[key]["es"]))
    for key, fmt_en, fmt_es in (("card_ready_at", "Ready at %1$s", "Listo a las %1$s"),):
        if (w[key]["en"], w[key]["es"]) != (fmt_en, fmt_es):
            fails.append("%s changed in words.json: update AuraWords.readyAt" % key)
    return fails


# ------------------------------------------------------------------------------------------------- the simulator run

def sdk():
    if os.environ.get("CIQ_SDK"):
        return os.environ["CIQ_SDK"].rstrip("/")
    for cfg in ("~/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg", "~/.Garmin/ConnectIQ/current-sdk.cfg"):
        if os.path.exists(os.path.expanduser(cfg)):
            return open(os.path.expanduser(cfg)).read().strip().rstrip("/")
    raise SystemExit("garmin_test: no Connect IQ SDK (set CIQ_SDK)")


def env():
    e = dict(os.environ)
    jdk = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    if "JAVA_HOME" not in e and os.path.isdir(jdk):
        e["JAVA_HOME"] = jdk
    if "JAVA_HOME" in e:
        e["PATH"] = e["JAVA_HOME"] + "/bin:" + e["PATH"]
    return e


SIM = "ConnectIQ.app/Contents/MacOS/simulator" if sys.platform == "darwin" else "bin/simulator"


def simulator_running():
    return subprocess.run(["pgrep", "-f", SIM], capture_output=True).returncode == 0


def start_simulator():
    """Starts the simulator (macOS: the app; Linux: the binary, on $DISPLAY, e.g. Xvfb) and waits for it."""
    if sys.platform == "darwin":
        subprocess.run(["open", "-a", os.path.join(sdk(), "bin", "ConnectIQ.app")], check=True)
    else:
        subprocess.Popen([os.path.join(sdk(), "bin", "connectiq")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                         start_new_session=True, env=env())
    for _ in range(60):
        if simulator_running():
            time.sleep(4)  # its window and its port
            return
        time.sleep(0.5)
    raise SystemExit("garmin_test: the simulator didn't start")


def colour_filter(img, kind):
    """The reference's colour checks (SPEC.md §8), applied to the screenshot so it compares with the reference."""
    if kind == "redshift":
        m = (0.36, 0.56, 0.12, 0, 0.03, 0.05, 0.01, 0, 0, 0, 0, 0)
        return img.convert("RGB", m)
    if kind == "grey":
        from PIL import ImageOps
        return ImageOps.grayscale(img).convert("RGB")
    return img


def diff(sim_path, ref_path, out_path):
    """Pixels that differ by more than 15 % (SPEC.md §10: compare -fuzz 15%), inside the screen circle; the diff image
    shows the reference dimmed with the differing pixels in red. Returns the count."""
    from PIL import Image, ImageChops, ImageDraw
    sim = Image.open(sim_path).convert("RGB")
    ref = Image.open(ref_path).convert("RGBA")
    flat_ref = Image.new("RGB", ref.size, (0, 0, 0))
    flat_ref.paste(ref, mask=ref.getchannel("A"))
    mask = Image.new("L", sim.size, 0)
    ImageDraw.Draw(mask).ellipse((3, 3, sim.width - 4, sim.height - 4), fill=255)  # the bezel overlay at the rim
    d = ImageChops.difference(sim, flat_ref).convert("L")
    bad = d.point(lambda v: 255 if v > 255 * 0.15 else 0)
    bad = ImageChops.multiply(bad, mask)
    count = sum(1 for v in bad.tobytes() if v)
    base = Image.blend(flat_ref, sim, 0.5).point(lambda v: v // 2)
    red = Image.new("RGB", sim.size, (255, 40, 40))
    base.paste(red, mask=bad)
    base.save(out_path)
    return count


def pictures(outdir, ids, shots, core=None):
    """Diff every screenshot with its reference (and with the reference without the dial, halo and sweep, core_refs.mjs,
    when given): the report's lines, worst first."""
    rows = []
    os.makedirs(os.path.join(outdir, "diff-core"), exist_ok=True)
    for cid in ids:
        if cid not in shots:
            rows.append((10 ** 9, 0, cid, "no screenshot"))
            continue
        n = diff(shots[cid], os.path.join(REF, cid + ".png"), os.path.join(outdir, "diff", cid + ".png"))
        k = diff(shots[cid], os.path.join(core, cid + ".png"), os.path.join(outdir, "diff-core", cid + ".png")) if core else 0
        rows.append((k if core else n, n, cid, ""))
    rows.sort(reverse=True)
    out = ["", "pictures: pixels over 15 %% different inside the screen, worst first; 'ref' against docs/img/aura/ref (%s),"
           % os.path.join(outdir, "diff"), "'core' against the reference without dial, halo and sweep (%s)" % os.path.join(outdir, "diff-core"),
           "   core     ref  case"]
    out += ["%7s %7s  %s %s" % (k if n < 10 ** 9 else "-", n if n < 10 ** 9 else "-", cid, note) for k, n, cid, note in rows]
    return out


def run(outdir, core=None):
    sys.path.insert(0, HERE)
    import simshot  # noqa: E402
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    for sub in ("sim", "diff"):
        os.makedirs(os.path.join(outdir, sub), exist_ok=True)
    subprocess.run([os.path.join(APP, "build.sh"), "cases"], check=True, env=env(), stdout=subprocess.DEVNULL)
    if not simulator_running():
        start_simulator()
    log = os.path.join(outdir, "cases.log")
    cases = json.load(open(CASES, encoding="utf-8"))["cases"]
    with open(log, "w") as lf:
        proc = subprocess.Popen([os.path.join(sdk(), "bin", "monkeydo"), os.path.join(APP, "bin", "SukraraAura-cases.prg"),
                                 "descentmk343mm"], stdout=lf, stderr=subprocess.STDOUT, env=env())
    shots = {}
    offset = None
    try:
        deadline = time.time() + 2 * 3.1 * len(cases) + 400  # a missed case comes back after the combinations
        while len(shots) < len(cases) and time.time() < deadline:
            time.sleep(0.25)
            if offset is None:
                if any(l.startswith("CAL") for l in open(log, encoding="utf-8", errors="replace")):
                    time.sleep(0.8)
                    offset = simshot.calibrate()
                    print("calibration: the screen is %.2f, %.2f pt off simulator.json" % offset)
                continue
            lines = [l for l in open(log, encoding="utf-8", errors="replace") if l.startswith("CASE ")]
            if not lines:
                continue
            _, idx, cid, filt = lines[-1].split()
            if cid in shots:
                continue
            time.sleep(0.8)  # the frame is on screen
            path = os.path.join(outdir, "sim", cid + ".png")
            simshot.shot(path, offset=offset)
            time.sleep(0.3)
            simshot.shot(path + ".2.png", offset=offset)
            from PIL import ImageChops
            moved = ImageChops.difference(Image.open(path), Image.open(path + ".2.png")).getbbox()
            os.remove(path + ".2.png")
            if moved:
                os.remove(path)
                continue  # caught between two frames: again on the next round (or the next cycle)
            again = [l for l in open(log, encoding="utf-8", errors="replace") if l.startswith("CASE ")]
            if again[-1].split()[2] != cid:
                os.remove(path)
                continue  # the next case came while capturing: try it on the next round
            if filt != "none":
                colour_filter(Image.open(path), filt).save(path)
            shots[cid] = path
        # then the battery label against every row (AuraCombos), a slice per frame
        combos = None
        deadline = time.time() + 900
        while combos is None and time.time() < deadline:
            time.sleep(1)
            for line in open(log, encoding="utf-8", errors="replace"):
                if line.startswith("COMBOS|"):
                    combos = line.strip().split("|")
    finally:
        proc.terminate()
    done, total, fails = numbers(log)
    report = ["numbers: %d of %d cases, %d differences" % (done, total, len(fails))] + fails
    if combos is None:
        report.append("combinations: no result (the case loader didn't finish them)")
        fails.append("combinations")
    else:
        bad = [l.strip() for l in open(log, encoding="utf-8", errors="replace") if l.startswith("COMBO_FAIL|")]
        report.append("combinations (battery label against every row, SPEC.md §9): %s, %s overlapping" % (combos[1], combos[2]))
        report += bad[:40]
        if bad:
            fails.append("combinations")
    report += pictures(outdir, [c["id"] for c in cases], shots, core)
    text = "\n".join(report) + "\n"
    open(os.path.join(outdir, "report.txt"), "w").write(text)
    print(text)
    return 1 if fails or done < total else 0


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    if argv[0] == "words":
        fails = words()
        print("\n".join(fails) if fails else "words: the face's card words are words.json's")
        return 1 if fails else 0
    if argv[0] == "numbers":
        done, total, fails = numbers(argv[1])
        print("\n".join(fails))
        print("numbers: %d of %d cases, %d differences" % (done, total, len(fails)))
        return 1 if fails or done < total else 0
    if argv[0] == "run":
        return run(argv[1], argv[2] if len(argv) > 2 else None)
    if argv[0] == "pictures":
        # re-diff the screenshots of an earlier run: pictures OUTDIR [CORE]
        shots = {f[:-4]: os.path.join(argv[1], "sim", f) for f in os.listdir(os.path.join(argv[1], "sim"))}
        ids = [c["id"] for c in json.load(open(CASES, encoding="utf-8"))["cases"]]
        print("\n".join(pictures(argv[1], ids, shots, argv[2] if len(argv) > 2 else None)))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
