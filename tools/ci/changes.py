#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Which jobs of .github/workflows/checks.yml a run needs (its `changes` job), as GITHUB_OUTPUT lines.

A push or a run started by hand is compared with the newest ancestor that already has a green Checks run of its own
(push or workflow_dispatch, any branch), not with the push's `before`: a docs-only commit on top of a failed or
still-running one still runs what that one changed, and a run of every commit in a row isn't needed for the next to be
trusted. A commit that already has a green run (main fast-forwarded to it, its tag) needs nothing again: same commit,
same tree, same workflow. A pull request is compared with its base. No green ancestor among the last 300, or
`full`: everything runs.

    tools/ci/changes.py            reads GITHUB_EVENT_NAME, HEAD_SHA, PR_BASE, FULL, GITHUB_REPOSITORY, GITHUB_RUN_ID
Tests: python3 tools/ci/test_changes.py (CI's rules job).
"""
import json
import os
import subprocess
import sys

# A group runs when a changed path starts with one of its prefixes (a prefix without "/" is one file) and isn't under
# one of its exclusions.
# - android: its tests read tools/check/allow.json and tools/release/android-cert.sha256, its jobs run
#   tools/release scripts, and VERSION is the versionCode (an :app input). The Play notes (fastlane metadata) are text
#   the release command checks and uploads; no build reads them.
# - mac: no Mac build, test or bundle step reads tools/ (the settings generator's output is checked by the rules job,
#   the release scripts run on the release Mac). VERSION reaches only Bundle/bundle.sh's Info.plist, and the release
#   command's gate bundles, notarizes and launch-smokes the app with the new VERSION before anything is published, so
#   the version commit doesn't run the Mac job again over an unchanged app.
GROUPS = {
    "android": (["apps/android/", "contracts/", "tools/", "VERSION", "assets/"], ["apps/android/fastlane/"]),
    "mac": (["apps/macos/", "contracts/", "assets/"], []),
}
DEPTH = 300


def matches(path, prefixes):
    return any(path == p or (p.endswith("/") and path.startswith(p)) for p in prefixes)


def groups(files):
    """The groups the changed files need: {name: bool}."""
    return {name: any(matches(f, inc) and not matches(f, exc) for f in files) for name, (inc, exc) in GROUPS.items()}


def green_base(ancestors, green):
    """The newest ancestor (rev-list order, the commit itself excluded) with a green run, or None."""
    return next((sha for sha in ancestors if sha in green), None)


def plan(event, head, green, ancestors, diff, pr_base="", full=False):
    """{rules, android, mac: bool, base, range}. `diff(base)` lists the files changed from base to head."""
    everything = dict({name: True for name in GROUPS}, rules=True, base="", range="origin/main..%s" % head)
    if full:
        return everything
    if event == "pull_request":
        return dict(groups(diff(pr_base)), rules=True, base=pr_base, range="%s..%s" % (pr_base, head))
    if head in green:
        return dict({name: False for name in GROUPS}, rules=False, base=head, range="")
    base = green_base(ancestors, green)
    if base is None:
        return everything
    return dict(groups(diff(base)), rules=True, base=base, range="%s..%s" % (base, head))


def git(*args):
    return subprocess.run(["git"] + list(args), capture_output=True, text=True, check=True).stdout


def green_runs(repo, run_id):
    out = subprocess.run(["gh", "run", "list", "-R", repo, "--workflow", "checks.yml", "--status", "success",
                          "-L", "500", "--json", "headSha,event,databaseId"],
                         capture_output=True, text=True, check=True).stdout
    return {r["headSha"] for r in json.loads(out or "[]")
            if r.get("event") in ("push", "workflow_dispatch") and str(r.get("databaseId")) != str(run_id)}


def main():
    env = os.environ
    event, head = env["GITHUB_EVENT_NAME"], env["HEAD_SHA"]
    full = env.get("FULL", "") == "true"
    pr_base = env.get("PR_BASE", "")
    green = set() if full or event == "pull_request" else green_runs(env["GITHUB_REPOSITORY"], env["GITHUB_RUN_ID"])
    ancestors = [] if full or event == "pull_request" else git("rev-list", "--max-count=%d" % DEPTH, head + "^@").split()

    def diff(base):
        three = "..." if event == "pull_request" else ".."  # a PR: what it changes since it left its base
        return git("diff", "--name-only", "%s%s%s" % (base, three, head)).split("\n")

    p = plan(event, head, green, ancestors, diff, pr_base, full)
    print("base: %s (%s)" % (p["base"] or "none: everything runs", "already green" if p["base"] == head else event),
          file=sys.stderr)
    for k in ["rules"] + list(GROUPS) + ["base", "range"]:
        v = p[k]
        print("%s=%s" % (k, ("true" if v else "false") if isinstance(v, bool) else v))


if __name__ == "__main__":
    main()
