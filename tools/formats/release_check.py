# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The shared formats' release check (docs/48 "Shared formats", contracts/apps/formats.json): a release that changes a
format's version takes every app that speaks it, so the update exists everywhere at once; a format with 'first' (the
watch payload: the Connect IQ apps) publishes those apps in an earlier release, and the others only once they are
out (Connect IQ approves in up to 3 days: the phone waits rather than ask for an update that isn't there yet).

tools/release/release.py runs it as one of its RELEASE_CHECKS: problems(root, {app id: version}) → [problem].
Each app's last release is its newest tag (contracts/apps/apps.json 'tag' + version; before the per-app tags, the
shared v<version> ones); a release from before formats.json existed speaks the registry's first versions.
"""
import json
import re
import subprocess

FORMATS = "contracts/apps/formats.json"
APPS = "contracts/apps/apps.json"
SEMVER = r"(\d+)\.(\d+)\.(\d+)"


def git(root, *args):
    r = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def last_tag(tags, prefix):
    """The newest of [tags] named <prefix><x.y.z>, else the newest shared v<x.y.z> (before the per-app tags); None."""
    def newest(p):
        found = [(tuple(map(int, m.groups())), t) for t in tags if (m := re.fullmatch(re.escape(p) + SEMVER, t))]
        return max(found)[1] if found else None
    return newest(prefix) or newest("v")


def versions(text):
    """{format: version} of a formats.json text."""
    return {f["format"]: f["version"] for f in json.loads(text)["formats"]}


def check(formats, spoken, releases):
    """The pure rule. [formats]: formats.json now; [spoken]: {app id: {format: version}} what each app's last release
    speaks; [releases]: {app id: version} the apps this release takes. A problem per app left behind."""
    out = []
    for f in formats["formats"]:
        name, want, apps = f["format"], f["version"], f["apps"]
        behind = [a for a in apps if spoken.get(a, {}).get(name, want) != want]
        if not behind:
            continue
        first = [a for a in f.get("first", []) if a in apps]
        later = [a for a in apps if a not in first]
        first_behind = [a for a in first if a in behind]
        for a in behind:
            if a in releases:
                # an app of 'later' waits for 'first' to be out (an earlier release), never in the same one
                if first and a in later and first_behind:
                    out.append("formats: %s %s needs %s released first (an earlier release, once Connect IQ approved "
                               "it), then %s" % (name, want, ", ".join(first_behind), a))
                continue
            if first and a in later and any(x in releases for x in first):
                continue  # the later apps come in the next release
            took = [x for x in apps if x in releases]
            if took:
                out.append("formats: %s %s: %s speaks %s; release it with %s" %
                           (name, want, a, spoken.get(a, {}).get(name), ", ".join(took)))
    return out


def problems(root, releases):
    """RELEASE_CHECKS entry: the shared formats against each speaker's last release."""
    now = json.loads(open("%s/%s" % (root, FORMATS)).read())
    apps = {a["id"]: a for a in json.loads(open("%s/%s" % (root, APPS)).read())["apps"]}
    tags = (git(root, "tag", "-l") or "").split()
    first_added = (git(root, "log", "--diff-filter=A", "--format=%H", "--", FORMATS) or "").split()
    baseline = git(root, "show", "%s:%s" % (first_added[-1], FORMATS)) if first_added else None
    spoken = {}
    for f in now["formats"]:
        for a in f["apps"]:
            if a in spoken or a not in apps:
                continue
            tag = last_tag(tags, apps[a]["tag"])
            text = git(root, "show", "%s:%s" % (tag, FORMATS)) if tag else None
            text = text or baseline
            spoken[a] = versions(text) if text else {}
    return check(now, spoken, releases)
