# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""tools/formats/release_check.py: a format change releases every app that speaks it; the watch payload's Connect IQ
apps first. python3 tools/formats/test_release_check.py"""
import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import release_check  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
GARMIN = ["aura", "garmin-app", "garmin-field", "face-sdk"]


def registry(**changed):
    f = json.loads((ROOT / release_check.FORMATS).read_text())
    for x in f["formats"]:
        x["version"] = changed.get(x["format"].replace("-", "_"), x["version"])
    return f


def spoken(sharing=2, payload=5, garmin_payload=None):
    s = {a: {"sharing": sharing, "treatment": 1, "invite": 3, "avatar": 1, "watch-payload": payload}
         for a in ["android", "mac"]}
    for a in GARMIN:
        s[a] = {"watch-payload": garmin_payload or payload}
    return s


class ReleaseCheckTest(unittest.TestCase):
    def test_the_registry_names_the_apps_and_the_watch_apps_first(self):
        f = registry()
        apps = {a["id"] for a in json.loads((ROOT / release_check.APPS).read_text())["apps"]}
        for x in f["formats"]:
            self.assertLessEqual(set(x["apps"]), apps, x["format"])
            self.assertLessEqual(set(x.get("first", [])), set(x["apps"]), x["format"])
        payload = next(x for x in f["formats"] if x["format"] == "watch-payload")
        self.assertEqual(GARMIN, payload["first"])

    def test_no_change_releases_any_app_alone(self):
        self.assertEqual([], release_check.check(registry(), spoken(), {"mac": "0.17.2"}))

    def test_a_sharing_change_takes_every_speaker(self):
        f = registry(sharing=3)
        self.assertEqual(1, len(release_check.check(f, spoken(), {"android": "0.18.0"})))
        self.assertIn("mac speaks 2", release_check.check(f, spoken(), {"android": "0.18.0"})[0])
        self.assertEqual([], release_check.check(f, spoken(), {"android": "0.18.0", "mac": "0.18.0"}))

    def test_a_payload_change_releases_the_watch_apps_first_then_the_phone(self):
        f = registry(watch_payload=6)
        everyone = {a: "1.0.0" for a in GARMIN + ["android"]}
        # all at once: the phone would ask for watch updates Connect IQ hasn't approved
        self.assertTrue(release_check.check(f, spoken(), everyone))
        # the phone alone first: refused
        self.assertTrue(release_check.check(f, spoken(), {"android": "0.18.0"}))
        # one watch app alone: the others must come with it
        self.assertTrue(release_check.check(f, spoken(), {"aura": "0.18.0"}))
        # the watch apps together: fine, the phone comes next
        self.assertEqual([], release_check.check(f, spoken(), {a: "0.18.0" for a in GARMIN}))
        # once they are out at 6, the phone
        self.assertEqual([], release_check.check(f, spoken(garmin_payload=6), {"android": "0.18.0"}))

    def test_an_apps_last_release_is_its_newest_tag_else_the_shared_one(self):
        tags = ["v0.17.1", "v0.9.0", "aura-v0.17.2", "aura-v0.17.10", "android-v9.9.9"]
        self.assertEqual("aura-v0.17.10", release_check.last_tag(tags, "aura-v"))
        self.assertEqual("v0.17.1", release_check.last_tag(tags, "mac-v"))
        self.assertEqual("v0.17.1", release_check.last_tag(tags, "v"))

    def test_this_repository_has_nothing_behind(self):
        self.assertEqual([], release_check.problems(str(ROOT), {}))


if __name__ == "__main__":
    unittest.main()
