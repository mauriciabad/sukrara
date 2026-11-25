# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sukrara Aura's memory in its own process (#217): the release face with a probe, in the Connect IQ simulator, under
the watch's 128 KB. Fails when a frame's peak passes BUDGET. Python 3.9 stdlib.

  python3 apps/garmin/aura/tools/face_memory.py [--frames N] [--keep DIR]

The face's own source is copied and a probe module (AuraMemProbe) is called at the start of each draw, after its
model and after each part AuraDraw.draw paints (the heap is highest at the end of a part: its fonts, the model, the
input). The probe hands the face a payload of contracts/watchface/payload/cases.json (3 h of history, a treatment, the
sensor's dates) as its background does, a new reading every 15 frames (the dial's repaint, the glide, the old payload
beside the new), with the default settings first, then the heaviest (lasers, both rails with the sensor's, weather and
the second zone on top, a forecast at the bottom). The face's code and data count, and the probe's own (2.2 KB) too:
the face alone peaks that much lower. Linux (CI): under Xvfb (xvfb-run), like garmin_test.py run.

Why: the owner's Descent Mk3 43 put the default face back 3 times a day, "Out Of Memory" at the bottom fields' icon
(2026-11-25). Measured with a probe like this one: 0.16.0 left ~5 KB of its 126.8 KB with the default settings, 0.18.0
nothing with the heaviest; after the fix ~12 KB (114.3 KB with the probe). A face's memory is its code and data
(~84 KB), what it keeps between frames, and one frame's input, model, part fonts and paint.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import garmin_test as gt  # noqa: E402 (the simulator's helpers)

APP = gt.APP  # apps/garmin/aura
GARMIN = gt.GARMIN
ROOT = gt.ROOT
DEVICE = gt.DEVICE
# the worst frame's peak with the probe, bytes, of the simulator's 126784 (128 KB less the system's): the face alone
# under ~114 KB, so ~10 KB stay free for what the watch's firmware counts beyond the simulator and the settings and
# words not run here. Raising it is a decision (#217): find what a frame keeps or builds first
BUDGET = 116 * 1024
FRAMES = 60
# the heaviest settings (property → stored index; SettingsSpec): lasers, the sensor rail and the glucose rail, weather
# and the second time zone on top, time in range and a forecast at the bottom, both battery labels
HEAVY = {"dial": 5, "halo": 0, "trend": 1, "seconds": True, "topField": 14, "topField2": 26, "field1": 0,
         "field2": 17, "sideL": 1, "sideR": 2, "battLabel": 2, "tz2": 26}


def mc(v, now):
    """A JSON value as a Monkey C literal; epoch times (within 30 days of the cases' now) relative to the frame's n."""
    if v is None:
        return "null"
    if v is True or v is False:
        return "true" if v else "false"
    if isinstance(v, int):
        return "(n + %d)" % (v - now) if abs(v - now) < 30 * 86400 else str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, str):
        return json.dumps(v)
    if isinstance(v, list):
        return "[" + ", ".join(mc(x, now) for x in v) + "]"
    return "{" + ", ".join("%s => %s" % (json.dumps(k), mc(x, now)) for k, x in v.items()) + "}"


def probe(frames):
    cases = json.load(open(os.path.join(ROOT, "contracts", "watchface", "payload", "cases.json")))
    payload = cases["cases"][0]["payload"]
    heavy = ", ".join("%s => %s" % (json.dumps(k), json.dumps(v)) for k, v in HEAVY.items())
    return """import Toybox.Application;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// face_memory.py's probe (never in the face): the heap after each part of a draw, the worst frame's peak
module AuraMemProbe {
    var frame as Number = 0;
    var peak as Number = 0; // this frame's
    var worst as Number = 0;
    var worstAt as String = "";
    var part as Number = 0;

    function payload(n as Number) as Dictionary {
        return %(payload)s;
    }

    // what the background leaves (LinkService.hand): a new reading every 15 frames, its value moving
    function hand() as Void {
        var n = Time.now().value();
        var p = payload(n);
        var g = p["g"] as Dictionary;
        g["v"] = 90 + (frame / 15 %% 6) * 25;
        Application.Storage.setValue(LinkBackground.HANDOFF, {"k" => "fetch", "c" => 200, "d" => p, "id" => "probe" + frame});
    }

    function start() as Void {
        if (frame == %(half)d) {
            var h = {%(heavy)s};
            var ks = h.keys();
            for (var i = 0; i < ks.size(); i++) {
                Application.Properties.setValue(ks[i] as String, h[ks[i]]);
            }
            AuraWatch.changed();
        }
        if (frame %% 15 == 0) {
            hand();
            // the pick-up waits 10 s between storage reads: this one is read now
            Link._pickedAt = 0;
        }
        peak = 0;
        part = 0;
        note();
    }

    function note() as Void {
        var u = System.getSystemStats().usedMemory;
        if (u > peak) {
            peak = u;
        }
        if (u > worst) {
            worst = u;
            worstAt = frame + "." + part;
        }
        part += 1;
    }

    function finish() as Void {
        note();
        System.println("MEM|" + frame + "|" + peak + "|" + System.getSystemStats().totalMemory + "|" + worst + "|" + worstAt);
        frame += 1;
        if (frame == %(frames)d) {
            System.println("DONE");
        }
    }
}
""" % {"payload": mc(payload, cases["now"]), "heavy": heavy, "half": frames // 2, "frames": frames}


def instrument(src):
    """The probe's calls in a copy of the face's source; each anchor must be there (a face that changed shape fails
    here, so this file follows it)."""
    def edit(name, pairs):
        p = os.path.join(src, name)
        t = open(p, encoding="utf-8").read()
        for old, new in pairs:
            if old not in t:
                raise SystemExit("face_memory: %s no longer has %r: update instrument()" % (name, old.strip()))
            t = t.replace(old, new, 1)
        open(p, "w", encoding="utf-8").write(t)

    edit("AuraView.mc", [
        ("        Link.pickUp(Time.now().value());\n", "        AuraMemProbe.start();\n        Link.pickUp(Time.now().value());\n"),
        ("        var m = new AuraModel(input());\n", "        var m = new AuraModel(input());\n        AuraMemProbe.note();\n"),
        ("        AuraDraw.draw(dc, m, sx, sy);\n    }", "        AuraDraw.draw(dc, m, sx, sy);\n        AuraMemProbe.finish();\n    }"),
    ])
    # after each part of the draw (a line `part(dc, m);`) and before each drop() of the halo and the draw
    p = os.path.join(src, "AuraDraw.mc")
    t = open(p, encoding="utf-8").read()
    s = t.index("    function draw(dc as Dc, m as AuraModel")
    e = t.index("\n    }\n", s)
    body = re.sub(r"(?m)^(\s+)((?:\w+\.)?\w+\(dc, m\);|new AuraHalo\(m\)\.paint\(dc, m\);)$", r"\1\2\n\1AuraMemProbe.note();", t[s:e])
    if body.count("AuraMemProbe.note()") < 8:
        raise SystemExit("face_memory: AuraDraw.draw's parts not found: update instrument()")
    open(p, "w", encoding="utf-8").write(t[:s] + body + t[e:])
    p = os.path.join(src, "AuraHalo.mc")
    t = open(p, encoding="utf-8").read()
    t = re.sub(r"(?m)^(\s+)AuraDraw\.drop\(\);", r"\1AuraMemProbe.note();\n\1AuraDraw.drop();", t)
    open(p, "w", encoding="utf-8").write(t)


def jungle(work, src):
    """The face's monkey.jungle with absolute paths, its source the instrumented copy plus the probe."""
    base = open(os.path.join(APP, "monkey.jungle"), encoding="utf-8").read()
    out = []
    for line in base.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        k, v = [x.strip() for x in line.split("=", 1)]
        if k == "base.sourcePath":
            parts = [src if p == "source" else os.path.normpath(os.path.join(APP, p)) for p in v.split(";")]
            v = ";".join(parts + [os.path.join(work, "probe")])
        elif k in ("project.manifest", "base.resourcePath", "base.lang.spa"):
            v = os.path.join(APP, v)
        elif k.endswith(".resourcePath"):
            v = ";".join(p if p.startswith("$(") else os.path.join(APP, p) for p in v.split(";"))
        out.append("%s = %s" % (k, v))
    path = os.path.join(work, "probe.jungle")
    open(path, "w").write("\n".join(out) + "\n")
    return path


def build(work):
    src = os.path.join(work, "source")
    shutil.copytree(os.path.join(APP, "source"), src)
    instrument(src)
    os.makedirs(os.path.join(work, "probe"))
    key = os.environ.get("CIQ_KEY", os.path.expanduser("~/.connectiq/sukrara-developer-key.der"))
    prg = os.path.join(work, "AuraMemProbe.prg")
    return src, prg, key


def run(frames, keep):
    work = keep or tempfile.mkdtemp(prefix="face-memory-")
    if keep:
        shutil.rmtree(work, ignore_errors=True)
        os.makedirs(work)
    try:
        src, prg, key = build(work)
        open(os.path.join(work, "probe", "AuraMemProbe.mc"), "w").write(probe(frames))
        cmd = [os.path.join(gt.sdk(), "bin", "monkeyc"), "-d", DEVICE, "-f", jungle(work, src), "-o", prg, "-y", key,
               "-w", "-O", "2z", "-r"]
        b = subprocess.run(cmd, capture_output=True, text=True, env=gt.env())
        if b.returncode != 0 or not os.path.exists(prg):
            print(b.stdout[-4000:], b.stderr[-4000:])
            raise SystemExit("face_memory: the probe build failed")
        lines = []
        for attempt in range(2):
            if not gt.simulator_running():
                gt.start_simulator()
            log = os.path.join(work, "run%d.log" % attempt)
            with open(log, "w") as f:
                proc = subprocess.Popen([os.path.join(gt.sdk(), "bin", "monkeydo"), prg, DEVICE], stdout=f,
                                        stderr=subprocess.STDOUT, env=gt.env())
                end = time.time() + frames * 1.5 + 60
                while time.time() < end:
                    time.sleep(2)
                    text = open(log, encoding="utf-8", errors="replace").read()
                    if "DONE" in text or "Out Of Memory" in text or "Encountered an app crash" in text:
                        break
                proc.kill()
            text = open(log, encoding="utf-8", errors="replace").read()
            lines = [l for l in text.splitlines() if l.startswith("MEM|")]
            if lines or "crash" in text:
                break
            # monkeydo lost the simulator (garmin_test.py's run does the same)
            gt.stop_simulator()
        return report(text, lines, frames)
    finally:
        if not keep:
            shutil.rmtree(work, ignore_errors=True)


def report(text, lines, frames):
    if "Out Of Memory" in text or "Encountered an app crash" in text:
        print(text[-2000:])
        print("face_memory: the face crashed in the simulator (above)")
        return 1
    if not lines:
        print(text[-2000:])
        print("face_memory: no frames (the simulator didn't run the probe)")
        return 1
    rows = [l.split("|") for l in lines]
    half = frames // 2
    total = int(rows[-1][3])
    worst = int(rows[-1][4])

    def peak(rs):
        return max((int(r[2]) for r in rs), default=0)
    print("face_memory: %d frames on the simulator's %s, %d B for the face" % (len(rows), DEVICE, total))
    print("  default settings: worst frame %d B" % peak([r for r in rows if int(r[1]) < half]))
    print("  heaviest settings: worst frame %d B" % peak([r for r in rows if int(r[1]) >= half]))
    print("  worst: %d B at frame.part %s (budget %d B; %d B left of the face's)" % (worst, rows[-1][5], BUDGET, total - worst))
    if len(rows) < frames - 2:
        print("face_memory: only %d of %d frames ran" % (len(rows), frames))
        return 1
    if worst > BUDGET:
        print("face_memory: over budget: the face's peak grew (#217). Find what it keeps or builds per frame before "
              "raising BUDGET (apps/garmin/aura/README.md › Memory)")
        return 1
    return 0


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--frames", type=int, default=FRAMES)
    ap.add_argument("--keep", help="build and log here (kept)")
    a = ap.parse_args(argv)
    return run(a.frames, a.keep)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
