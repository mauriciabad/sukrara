# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Walks an app's screens on an EMULATOR through uiautomator (android-smoke.sh step 5): R8 can break a screen that is
never opened (a class it renamed or removed, a serializer it stripped), and the first screen proves nothing about the
others.

  python3 -I tools/release/ui-walk.py --adb ADB --serial emulator-5600 --package app.mauri.sukrara --out DIR
      [--depth 2] [--max-taps 40] [--skip REGEX]

From a fresh start (force-stopped, launched, Back once: setup's first step leaves to home) it taps every labelled
clickable of each screen it reaches, breadth first, up to --depth taps deep. After each tap the app must still run
and something must be on screen; each screen it reaches is saved as <n>.png / <n>.xml in --out. Prints one line per
tap ("ok   …" / "FAIL …"); exits 1 when a tap killed the app. Only ever talks to --serial, and refuses a device that
isn't an emulator.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


class Device:
    def __init__(self, adb, serial, package, out):
        self.adb, self.serial, self.package, self.out = adb, serial, package, out
        self.shots = 0

    def run(self, *args, check=False):
        r = subprocess.run([self.adb, "-s", self.serial, *args], capture_output=True)
        if check and r.returncode:
            sys.exit("adb %s failed: %s" % (" ".join(args), r.stderr.decode(errors="replace")))
        return r.stdout.decode(errors="replace")

    def shell(self, *args):
        return self.run("shell", *args)

    def pid(self):
        return self.shell("pidof", self.package).strip()

    def start(self):
        """A fresh start at the root screen: force-stopped, launched, Back once (setup's first step leaves to home);
        when Back leaves the app instead, launched again and the first screen is the root."""
        self.shell("am", "start", "-S", "-W", "-n", "%s/.MainActivity" % self.package)
        time.sleep(4)
        self.shell("input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
        if self.screen()[0] != self.package:
            self.shell("am", "start", "-W", "-n", "%s/.MainActivity" % self.package)
            time.sleep(3)
        return self.screen()

    def screen(self):
        """(package in front, [(label, (x, y))] of its labelled clickables, the screen's texts). uiautomator can't dump
        while something animates: a few tries."""
        for _ in range(4):
            out = self.run("exec-out", "uiautomator", "dump", "/dev/tty")
            xml = out[out.find("<?xml"):out.rfind(">") + 1] if "<?xml" in out else ""
            try:
                root = ET.fromstring(xml)
            except ET.ParseError:
                time.sleep(1)
                continue
            nodes = list(root.iter("node"))
            pkg = next((n.get("package") for n in nodes if n.get("package")), "")
            texts = [t for n in nodes for t in (n.get("text"), n.get("content-desc")) if t]
            clickables, seen = [], set()
            for n in nodes:
                if n.get("clickable") != "true" or n.get("enabled") != "true" or n.get("package") != self.package:
                    continue
                label = n.get("text") or n.get("content-desc") or next(
                    (t for c in n.iter("node") for t in (c.get("text"), c.get("content-desc")) if t), "")
                m = BOUNDS.match(n.get("bounds", ""))
                if not label or not m or label in seen:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                if x2 <= x1 or y2 <= y1:
                    continue
                seen.add(label)
                clickables.append((label.strip(), ((x1 + x2) // 2, (y1 + y2) // 2)))
            return pkg, clickables, texts
        return "", [], []

    def save(self, name):
        self.shots += 1
        base = os.path.join(self.out, "%02d-%s" % (self.shots, re.sub(r"[^A-Za-z0-9]+", "-", name)[:60].strip("-")))
        with open(base + ".png", "wb") as f:
            f.write(subprocess.run([self.adb, "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True).stdout)
        with open(base + ".xml", "w") as f:
            f.write(self.run("exec-out", "uiautomator", "dump", "/dev/tty"))


def signature(clickables, texts):
    return hashlib.sha1("\n".join(sorted(texts) + [c for c, _ in clickables]).encode()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--adb", required=True)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--depth", type=int, default=2)
    ap.add_argument("--max-taps", type=int, default=40)
    ap.add_argument("--skip", default="", help="labels never tapped (regex)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    dev = Device(a.adb, a.serial, a.package, a.out)
    if "1" not in (dev.shell("getprop", "ro.kernel.qemu").strip(), dev.shell("getprop", "ro.boot.qemu").strip()):
        sys.exit("refusing %s: not an emulator (ro.kernel.qemu / ro.boot.qemu)" % a.serial)
    skip = re.compile(a.skip) if a.skip else None
    # the walk taps quickly: no animations (put back at the end)
    scales = {k: dev.shell("settings", "get", "global", k).strip() for k in
              ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")}
    for k in scales:
        dev.shell("settings", "put", "global", k, "0")
    failed, taps, reached = False, 0, []
    try:
        pkg, root, texts = dev.start()
        if pkg != a.package:
            print("FAIL walk: %s isn't in front after launching it" % a.package)
            return 1
        dev.save("root")
        seen = {signature(root, texts)}
        queue = [[]]
        while queue and taps < a.max_taps:
            path = queue.pop(0)
            here = None  # the screen at `path` (None: navigate there first)
            labels = None
            while taps < a.max_taps:
                if here is None:
                    pkg, here, texts = dev.start()
                    for step in path:
                        target = dict(here).get(step)
                        if target is None:
                            here = None
                            break
                        dev.shell("input", "tap", str(target[0]), str(target[1]))
                        time.sleep(2)
                        pkg, here, texts = dev.screen()
                    if here is None:
                        print("WARN walk: couldn't get back to %s (the screen changed between visits)" % " > ".join(path))
                        break
                    parent = signature(here, texts)
                if labels is None:
                    labels = [l for l, _ in here if not (skip and skip.search(l))]
                if not labels:
                    break
                label = labels.pop(0)
                target = dict(here).get(label)
                if target is None:
                    here = None
                    labels.insert(0, label)
                    continue
                taps += 1
                where = " > ".join(path + [label])
                dev.shell("input", "tap", str(target[0]), str(target[1]))
                time.sleep(2)
                if not dev.pid():
                    print("FAIL walk: tapping %s killed the app (logcat has the crash)" % where)
                    failed = True
                    here = None
                    continue
                pkg, nodes, texts = dev.screen()
                if pkg == a.package:
                    sig = signature(nodes, texts)
                    new = sig not in seen
                    if new:
                        seen.add(sig)
                        dev.save(where)
                        reached.append(where)
                        if len(path) + 1 < a.depth:
                            queue.append(path + [label])
                    print("ok   walk: %s → %s" % (where, " / ".join(texts[:3]) or "(no text)"))
                else:
                    print("ok   walk: %s → %s (another app; back)" % (where, pkg or "nothing dumpable"))
                dev.shell("input", "keyevent", "KEYCODE_BACK")
                time.sleep(1)
                pkg, now, texts = dev.screen()
                here = now if pkg == a.package and signature(now, texts) == parent else None
        print("%s walk: %d taps, %d screens reached%s" % ("FAIL" if failed else "ok  ", taps, len(reached) + 1,
                                                        " (tap cap reached)" if taps >= a.max_taps else ""))
    finally:
        for k, v in scales.items():
            dev.shell("settings", "put", "global", k, v if v and v != "null" else "1")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
