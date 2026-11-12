#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests of tools/ci/changes.py (which Checks jobs a run needs). No network, no git."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import changes  # noqa: E402

DIFFS = {
    "docs": ["docs/FEATURES.md", "CHANGELOG.md", "README.md"],
    "notes": ["apps/android/fastlane/metadata/android/en-US/changelogs/14004.txt"],
    "version": ["VERSION", "CHANGELOG.md", "docs/DEVELOPMENT.md"],
    "android": ["apps/android/app/build.gradle.kts"],
    "contracts": ["contracts/alerts/SPEC.md"],
    "mac": ["apps/macos/Sources/App.swift"],
}


class Groups(unittest.TestCase):
    def test_groups(self):
        none = {"android": False, "mac": False}
        self.assertEqual(changes.groups(DIFFS["docs"]), none)
        self.assertEqual(changes.groups(DIFFS["notes"]), none)  # the Play notes: no build reads them
        self.assertEqual(changes.groups(DIFFS["version"]), dict(none, android=True))  # the release gate bundles the Mac app
        self.assertEqual(changes.groups(["tools/check/check.py"]), dict(none, android=True))
        self.assertEqual(changes.groups(DIFFS["android"]), dict(none, android=True))
        self.assertEqual(changes.groups(DIFFS["contracts"]), dict(none, android=True, mac=True))
        self.assertEqual(changes.groups(DIFFS["mac"]), dict(none, mac=True))
        self.assertEqual(changes.groups(DIFFS["notes"] + DIFFS["android"]), dict(none, android=True))
        self.assertEqual(changes.groups(["VERSIONS.md", ""]), none)  # a file prefix matches only itself


class Plan(unittest.TestCase):
    def diff(self, files):
        seen = []

        def d(base):
            seen.append(base)
            return files
        return d, seen

    def test_compared_with_the_newest_green_ancestor(self):
        d, seen = self.diff(DIFFS["docs"])
        p = changes.plan("push", "c3", {"c1", "c0"}, ["c2", "c1", "c0"], d)
        self.assertEqual(seen, ["c1"])  # c2 (failed or still running) is in the diff
        self.assertEqual((p["base"], p["range"], p["rules"], p["android"]), ("c1", "c1..c3", True, False))

    def test_a_docs_commit_after_a_red_one_runs_what_that_one_changed(self):
        d, _ = self.diff(DIFFS["docs"] + DIFFS["android"])  # c1..c3 has c2's android change
        self.assertTrue(changes.plan("push", "c3", {"c1"}, ["c2", "c1"], d)["android"])

    def test_an_already_green_commit_needs_nothing(self):
        d, seen = self.diff(DIFFS["android"])
        p = changes.plan("push", "c3", {"c3"}, ["c2"], d)  # main fast-forwarded to next's green commit, or its tag
        self.assertEqual(seen, [])
        self.assertEqual([p[k] for k in ["rules", "android", "mac"]], [False] * 3)

    def test_no_green_ancestor_or_full_runs_everything(self):
        d, _ = self.diff(DIFFS["docs"])
        for p in [changes.plan("push", "c3", set(), ["c2", "c1"], d),
                  changes.plan("workflow_dispatch", "c3", {"c3"}, ["c2"], d, full=True)]:
            self.assertEqual([p[k] for k in ["rules", "android", "mac"]], [True] * 3)
            self.assertEqual(p["range"], "origin/main..c3")

    def test_pull_request_against_its_base(self):
        d, seen = self.diff(DIFFS["mac"])
        p = changes.plan("pull_request", "h", {"h"}, [], d, pr_base="b")
        self.assertEqual(seen, ["b"])
        self.assertEqual((p["rules"], p["mac"], p["android"], p["range"]), (True, True, False, "b..h"))


if __name__ == "__main__":
    unittest.main()
