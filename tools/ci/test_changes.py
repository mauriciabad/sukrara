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
    "notes": ["apps/android/fastlane/metadata/android/en-US/changelogs/14004.txt",
              "apps/macos/AppStore/whats-new/es-ES/14004.txt"],
    "version": ["VERSION", "CHANGELOG.md", "docs/DEVELOPMENT.md"],
    "android": ["apps/android/app/build.gradle.kts"],
    "contracts": ["contracts/alerts/SPEC.md"],
    "mac": ["apps/macos/Sources/App.swift"],
    "website": ["website/src/content/docs/android.mdx", ".github/workflows/website.yml"],
    "garmin": ["apps/garmin/source/AuraView.mc"],
}


class Groups(unittest.TestCase):
    def test_groups(self):
        none = {"android": False, "mac": False, "website": False, "garmin": False}
        self.assertEqual(changes.groups(["CHANGELOG.md", "README.md"]), none)
        self.assertEqual(changes.groups(DIFFS["docs"]), dict(none, website=True))  # the site publishes docs/
        self.assertEqual(changes.groups(DIFFS["notes"]), none)  # the Play / App Store notes: no build reads them
        # the release gate bundles the Mac app; docs/DEVELOPMENT.md is on the site
        self.assertEqual(changes.groups(DIFFS["version"]), dict(none, android=True, website=True))
        self.assertEqual(changes.groups(["tools/check/check.py"]), dict(none, android=True))
        self.assertEqual(changes.groups(DIFFS["android"]), dict(none, android=True, website=True))
        self.assertEqual(changes.groups(DIFFS["contracts"]),
                         dict(none, android=True, mac=True, website=True, garmin=True))
        self.assertEqual(changes.groups(DIFFS["mac"]), dict(none, mac=True, website=True))
        self.assertEqual(changes.groups(DIFFS["notes"] + DIFFS["android"]), dict(none, android=True, website=True))
        self.assertEqual(changes.groups(DIFFS["website"]), dict(none, website=True))
        # App Links name the release key: the site checks it
        self.assertEqual(changes.groups(["tools/release/android-cert.sha256"]), dict(none, android=True, website=True))
        # the face (apps/: the site shows the apps), the pictures its screenshots are diffed against (docs/: on the site
        # too), the reference renderer, its CI setup (tools/: android too)
        self.assertEqual(changes.groups(DIFFS["garmin"]), dict(none, website=True, garmin=True))
        self.assertEqual(changes.groups(["docs/img/aura/ref/hero.png"]), dict(none, website=True, garmin=True))
        self.assertEqual(changes.groups(["docs/img/aura/ref-454/glucose-lo.png"]), dict(none, website=True, garmin=True))
        self.assertEqual(changes.groups(["docs/img/aura/store/hero.png"]), dict(none, website=True))
        self.assertEqual(changes.groups(["tools/ci/garmin.lock"]), dict(none, android=True, garmin=True))
        # the reference renderer: the face's tests and the site's playground draw with it
        self.assertEqual(changes.groups(["tools/watchface/aura.js"]), dict(none, android=True, website=True, garmin=True))
        self.assertEqual(changes.groups([".github/actions/garmin/action.yml"]), dict(none, garmin=True))
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
        self.assertEqual([p[k] for k in ["rules", "android", "mac", "website"]], [False] * 4)

    def test_no_green_ancestor_or_full_runs_everything(self):
        d, _ = self.diff(DIFFS["docs"])
        for p in [changes.plan("push", "c3", set(), ["c2", "c1"], d),
                  changes.plan("workflow_dispatch", "c3", {"c3"}, ["c2"], d, full=True)]:
            self.assertEqual([p[k] for k in ["rules", "android", "mac", "website"]], [True] * 4)
            self.assertEqual(p["range"], "origin/main..c3")

    def test_pull_request_against_its_base(self):
        d, seen = self.diff(DIFFS["mac"])
        p = changes.plan("pull_request", "h", {"h"}, [], d, pr_base="b")
        self.assertEqual(seen, ["b"])
        self.assertEqual((p["rules"], p["mac"], p["android"], p["range"]), (True, True, False, "b..h"))


if __name__ == "__main__":
    unittest.main()
