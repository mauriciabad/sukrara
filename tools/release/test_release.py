#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for the release command's pure parts, the registry and the change files: python3 tools/release/test_release.py
(no network, no keys)."""
import base64
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import appcast  # noqa: E402
import release  # noqa: E402
import stores  # noqa: E402
import versions  # noqa: E402

# the real registries: Sukrara's apps (contracts/apps/apps.json) and its Garmin apps (contracts/watchface/payload)
with open(os.path.join(release.ROOT, versions.REGISTRY), encoding="utf-8") as _f:
    APPS = versions.load_apps(_f.read())
with open(os.path.join(release.ROOT, release.GARMIN_REGISTRY), encoding="utf-8") as _f:
    REGISTRY = _f.read()
BY_ID = versions.by_id(APPS)

CHANGE = """---
apps: [android, mac]
kind: fix          # fix → patch, feature → minor
---
- **Extremely high stays quiet after insulin** (#258): rapid insulin you log now quiets
  it too. Every behaviour: [docs/FEATURES.md](docs/FEATURES.md).
"""
CHANGELOG = """# Sukrara for Android: changelog

Each release, newest first.

## 0.17.0 — 2026-11-20

- **Older**: one.
"""
MANIFEST = """<?xml version="1.0"?>
<iq:manifest version="3" xmlns:iq="http://www.garmin.com/xml/connectiq">
    <iq:application id="9ace188f" type="watchface" name="@Strings.AppName" entry="AuraApp" minApiLevel="5.0.0" version="0.1.0">
        <iq:products>
            <iq:product id="descentmk343mm"/>
            <iq:product id="fenix847mm"/>
        </iq:products>
    </iq:application>
</iq:manifest>
"""


def read(rel):
    with open(os.path.join(release.ROOT, rel), encoding="utf-8") as f:
        return f.read()


def two_changes():
    fix = versions.parse_change(CHANGE)
    feature = dict(fix, apps=["mac", "aura"], kind="feature", body="- **New**: a thing.")
    return {"changes/b-fix.md": fix, "changes/a-feature.md": feature}


class VersionTest(unittest.TestCase):
    def test_code_matches_lib_sh(self):
        self.assertEqual(release.version_code("0.14.0"), 14000)
        self.assertEqual(release.version_code("1.2.3"), 1002003)
        for app_id in ("android", "mac"):  # version.sh reads the version file the registry names
            v = read(BY_ID[app_id]["version"]).strip()
            out = subprocess.run(["sh", os.path.join(release.ROOT, "tools/release/version.sh"), app_id],
                                 capture_output=True, text=True, check=True).stdout.split()
            self.assertEqual(out, [v, str(release.version_code(v))])
        r = subprocess.run(["sh", os.path.join(release.ROOT, "tools/release/version.sh")], capture_output=True)
        self.assertNotEqual(r.returncode, 0)  # no app, no version: there is no shared one
        self.assertFalse(os.path.exists(os.path.join(release.ROOT, "VERSION")))

    def test_rejects_bad_versions(self):
        for v in ["1.2", "v1.2.3", "1.1000.0", "1.2.3-rc1", ""]:
            with self.assertRaises(ValueError):
                release.parse_version(v)

    def test_bump(self):
        self.assertEqual(versions.bump("0.17.1", "fix"), "0.17.2")
        self.assertEqual(versions.bump("0.17.1", "feature"), "0.18.0")
        self.assertEqual(versions.bump("5.0.0", "fix"), "5.0.1")
        with self.assertRaises(ValueError):
            versions.bump("0.17.1", "major")

    def test_manifest_version(self):
        aura = BY_ID["aura"]
        out = versions.with_version(aura, MANIFEST, "0.15.0")
        self.assertIn('minApiLevel="5.0.0" version="0.15.0">', out)
        self.assertIn('<iq:manifest version="3"', out)  # the manifest format's own version stays
        self.assertEqual(versions.read_version(aura, out), "0.15.0")
        self.assertEqual(out.replace("0.15.0", "0.1.0", 1), MANIFEST)
        with self.assertRaises(ValueError):
            versions.with_version(aura, MANIFEST.replace(' version="0.1.0"', ""), "0.15.0")
        barrel = '<iq:manifest version="3"><iq:barrel id="x" module="SukraraFace" version="5.0.0">'
        self.assertEqual(versions.read_version(BY_ID["face-sdk"], barrel), "5.0.0")
        self.assertIn('version="5.0.1">', versions.with_version(BY_ID["face-sdk"], barrel, "5.0.1"))
        self.assertEqual(versions.with_version(BY_ID["android"], "0.17.1\n", "0.18.0"), "0.18.0\n")


class RegistryTest(unittest.TestCase):
    """contracts/apps/apps.json against the repository: every file it names, tags, Latest, the Garmin entries."""

    def test_every_app_and_its_files(self):
        self.assertEqual([a["id"] for a in APPS], ["android", "mac", "aura", "garmin-app", "garmin-field", "face-sdk"])
        garmin = release.garmin_apps(REGISTRY)
        for a in APPS:
            versions.read_version(a, read(a["version"]))  # a version every source has
            self.assertTrue(read(a["changelog"]).startswith("# "), a["changelog"])
            for p in a["paths"] + a.get("build", []):
                self.assertTrue(os.path.exists(os.path.join(release.ROOT, p)), p)
            for m in a.get("mentions", []):  # each is where the release rewrites it
                versions.with_mentions(read(m["file"]), m["pattern"], a, "9.9.9")
            if "garmin" in a:
                self.assertIn(a["garmin"], garmin)
                self.assertEqual(garmin[a["garmin"]]["manifest"], a["version"])
            release.release_files(a, "1.0.0", garmin.get(a.get("garmin")))
        self.assertEqual(sorted(a["garmin"] for a in APPS if "garmin" in a), sorted(garmin))  # every Garmin app
        self.assertEqual([a["id"] for a in APPS if a.get("latest")], ["android"])
        self.assertEqual(BY_ID["mac"]["tag"], appcast.TAG)

    def test_tags(self):
        self.assertEqual(versions.tag(BY_ID["android"], "0.18.0"), "v0.18.0")  # what every installed updater reads
        self.assertEqual(versions.tag(BY_ID["mac"], "0.17.2"), "mac-v0.17.2")
        self.assertEqual(versions.tag_version(BY_ID["android"], "v0.18.0"), "0.18.0")
        self.assertIsNone(versions.tag_version(BY_ID["android"], "mac-v0.18.0"))
        self.assertIsNone(versions.tag_version(BY_ID["mac"], "v0.18.0"))
        self.assertEqual(versions.tag_version(BY_ID["face-sdk"], "face-sdk-v5.0.1"), "5.0.1")
        with self.assertRaisesRegex(ValueError, "share a tag"):
            versions.load_apps(json.dumps({"apps": [dict(BY_ID["android"]), dict(BY_ID["mac"], tag="v")]}))
        with self.assertRaisesRegex(ValueError, "one app is GitHub's Latest"):
            versions.load_apps(json.dumps({"apps": [dict(BY_ID["mac"])]}))

    def test_concerns(self):
        self.assertTrue(versions.concerns(BY_ID["android"], "apps/android/app/build.gradle.kts"))
        self.assertFalse(versions.concerns(BY_ID["android"], "apps/android/VERSION"))
        self.assertFalse(versions.concerns(BY_ID["android"], "apps/android/fastlane/metadata/android/en-US/x.txt"))
        self.assertFalse(versions.concerns(BY_ID["mac"], "apps/android/app/build.gradle.kts"))
        self.assertTrue(versions.concerns(BY_ID["aura"], "apps/garmin/shared/link/Link.mc"))
        self.assertTrue(versions.concerns(BY_ID["garmin-field"], "apps/garmin/sdk/source/read/SukraraFace.mc"))
        self.assertFalse(versions.concerns(BY_ID["face-sdk"], "apps/garmin/aura/source/AuraView.mc"))


class ChangeFileTest(unittest.TestCase):
    def test_parse(self):
        c = versions.parse_change(CHANGE, list(BY_ID))
        self.assertEqual((c["apps"], c["kind"]), (["android", "mac"], "fix"))
        self.assertTrue(c["body"].startswith("- **Extremely high"))
        self.assertEqual(versions.parse_change(versions.change_text(c)), c)
        for text, why in [("- x\n", "no front matter"), (CHANGE.replace("[android, mac]", "android"), "a list"),
                          (CHANGE.replace("[android, mac]", "[]"), "names no app"),
                          (CHANGE.replace("[android, mac]", "[android, android]"), "twice"),
                          (CHANGE.replace("[android, mac]", "[ios]"), "ios isn't in"),
                          (CHANGE.replace("kind: fix", "kind: major"), "not fix or feature"),
                          (CHANGE.replace("kind: fix", "summary: x"), "unknown front matter"),
                          (CHANGE.split("---\n- ")[0] + "---\n", "no text"),
                          (CHANGE.replace("- **Extremely", "**Extremely"), "start it with '- '")]:
            with self.assertRaisesRegex(ValueError, why):
                versions.parse_change(text, list(BY_ID))
        self.assertTrue(versions.is_change_file("changes/quiet-highs.md"))
        self.assertFalse(versions.is_change_file("changes/README.md"))
        self.assertFalse(versions.is_change_file("changes/x/y.md"))

    def test_next_version_and_order(self):
        ch = two_changes()
        self.assertEqual(versions.next_version("0.17.1", ch, "android"), "0.17.2")
        self.assertEqual(versions.next_version("0.17.1", ch, "mac"), "0.18.0")  # the highest kind
        self.assertIsNone(versions.next_version("0.17.1", ch, "garmin-field"))
        self.assertEqual([n for n, _ in versions.pending(ch, "mac")], ["changes/a-feature.md", "changes/b-fix.md"])

    def test_consumed(self):
        out = versions.consumed(two_changes(), ["mac", "android"])
        self.assertIsNone(out["changes/b-fix.md"])  # names no app any more: deleted
        self.assertEqual(versions.parse_change(out["changes/a-feature.md"])["apps"], ["aura"])
        self.assertEqual(versions.consumed(two_changes(), ["garmin-field"]), {})

    def test_changelog_entry(self):
        out = versions.changelog_with_entry(CHANGELOG, "0.17.3", datetime.date(2026, 11, 24), ["- **A**: a.", "- **B**: b."])
        self.assertIn("Each release, newest first.\n\n## 0.17.3 — 2026-11-24\n\n- **A**: a.\n- **B**: b.\n\n## 0.17.0", out)
        self.assertEqual(versions.changelog_entry(out, "0.17.3"), ("## 0.17.3 — 2026-11-24", "- **A**: a.\n- **B**: b."))
        self.assertEqual(versions.changelog_entry(out, "0.17.0")[1], "- **Older**: one.")
        first = versions.changelog_with_entry("# X: changelog\n\nIntro.\n", "1.0.0", "2026-11-24", ["- a"])
        self.assertEqual(first, "# X: changelog\n\nIntro.\n\n## 1.0.0 — 2026-11-24\n\n- a\n")
        with self.assertRaisesRegex(ValueError, "already has"):
            versions.changelog_with_entry(CHANGELOG, "0.17.0", "2026-11-24", ["- a"])
        with self.assertRaisesRegex(ValueError, "nothing to release"):
            versions.changelog_with_entry(CHANGELOG, "0.17.3", "2026-11-24", [])

    def test_links(self):
        body = "- [docs/A.md](docs/A.md#x), [web](https://x.y), [h](#h)"
        self.assertEqual(versions.rebase_links(body, "apps/garmin/aura"),
                         "- [docs/A.md](../../../docs/A.md#x), [web](https://x.y), [h](#h)")
        self.assertEqual(release.absolute_links(versions.rebase_links(body, "apps/macos"), "mac-v0.17.2", "apps/macos"),
                         "- [docs/A.md](https://github.com/mauriciabad/sukrara/blob/mac-v0.17.2/docs/A.md#x), "
                         "[web](https://x.y), [h](#h)")

    def test_mentions(self):
        sdk = BY_ID["face-sdk"]
        text = ("curl -o x https://github.com/mauriciabad/sukrara/releases/download/v0.17.0/SukraraFace-5.0.0.barrel\n"
                '<iq:depends name="SukraraFace" version="5.0.0"/>')
        out = text
        for m in sdk["mentions"]:
            if m["file"] == "docs/GARMIN-FACES.md":
                out = versions.with_mentions(out, m["pattern"], sdk, "5.1.0")
        self.assertEqual(out, "curl -o x https://github.com/mauriciabad/sukrara/releases/download/face-sdk-v5.1.0/"
                              'SukraraFace-5.1.0.barrel\n<iq:depends name="SukraraFace" version="5.1.0"/>')
        with self.assertRaisesRegex(ValueError, "no "):
            versions.with_mentions("nothing", sdk["mentions"][0]["pattern"], sdk, "5.1.0")

    def test_the_real_change_files_parse(self):
        for f in os.listdir(os.path.join(release.ROOT, "changes")):
            if versions.is_change_file("changes/" + f):
                versions.parse_change(read("changes/" + f), list(BY_ID))


class PlanTest(unittest.TestCase):
    CURRENT = {a["id"]: "0.17.1" for a in APPS}

    def test_plan(self):
        ch = two_changes()
        out = release.plan(APPS, self.CURRENT, ch, {"mac": None, "android": None})
        self.assertEqual(list(out), ["android", "mac"])  # the registry's order
        self.assertEqual(out["android"], ("0.17.2", ["changes/b-fix.md"]))
        self.assertEqual(out["mac"][0], "0.18.0")
        self.assertEqual(release.plan(APPS, self.CURRENT, ch, {"android": "0.18.0"})["android"][0], "0.18.0")
        for asked, why in [({"ios": None}, "isn't an app"), ({"garmin-field": None}, "no change file"),
                           ({"android": "0.17.1"}, "not above")]:
            with self.assertRaisesRegex(ValueError, why):
                release.plan(APPS, self.CURRENT, ch, asked)

    def test_args(self):
        self.assertEqual(release.parse_args_apps(["android", "mac=0.18.0"]), {"android": None, "mac": "0.18.0"})
        with self.assertRaises(ValueError):
            release.parse_args_apps(["android=1.2"])
        with self.assertRaises(ValueError):
            release.parse_args_apps(["android", "android=1.2.3"])

    def test_overview(self):
        lines = release.overview(APPS, self.CURRENT, two_changes())
        self.assertTrue(lines[0].startswith("android       0.17.1   → 0.17.2   changes/b-fix.md"), lines[0])
        self.assertIn("nothing to release", lines[4])


class NotesTest(unittest.TestCase):
    def entry(self, version="0.18.0", app="android"):
        folder = os.path.dirname(BY_ID[app]["changelog"])
        return versions.changelog_with_entry(CHANGELOG, version, "2026-11-24",
                                             [versions.rebase_links(versions.parse_change(CHANGE)["body"], folder)])

    def test_android(self):
        notes = release.github_notes(BY_ID["android"], self.entry(), "0.18.0", "AB" * 32)
        self.assertTrue(notes.startswith("**Not a medical device.** Every behaviour, with its rules and numbers: "
                                         "[docs/FEATURES.md](https://github.com/mauriciabad/sukrara/blob/v0.18.0/docs/"))
        # hard wraps joined, relative links at the tag
        self.assertIn("- **Extremely high stays quiet after insulin** (#258): rapid insulin you log now quiets it too. "
                      "Every behaviour: [docs/FEATURES.md](https://github.com/mauriciabad/sukrara/blob/v0.18.0/docs/"
                      "FEATURES.md).\n\n## Files", notes)
        self.assertIn("`Sukrara-0.18.0.apk`: Android 16+, arm64. Signed with Sukrara's release key, certificate SHA-256 "
                      "`%s`." % ("AB" * 32), notes)
        self.assertIn("Sukrara-0.18.0-android-mapping.txt.gz", notes)
        self.assertNotIn(".dmg", notes)
        self.assertNotIn("Older", notes)
        with self.assertRaisesRegex(ValueError, "no 0.19.0 entry"):
            release.github_notes(BY_ID["android"], self.entry(), "0.19.0", "AB")

    def test_mac_and_garmin_and_the_barrel(self):
        mac = release.github_notes(BY_ID["mac"], self.entry("0.17.2", "mac"), "0.17.2")
        self.assertIn("/blob/mac-v0.17.2/docs/FEATURES.md", mac)
        self.assertIn("`Sukrara-0.17.2.dmg` (or `Sukrara-mac-0.17.2.zip`)", mac)
        self.assertNotIn(".apk", mac)
        garmin = release.garmin_apps(REGISTRY)
        aura = release.github_notes(BY_ID["aura"], self.entry("0.17.2", "aura"), "0.17.2", garmin=garmin["aura"])
        self.assertIn("- `SukraraAura-0.17.2.iq` and `SukraraAura-0.17.2-sideload.zip`: Sukrara Aura, the Garmin watch "
                      "face. The `.iq` is the Connect IQ store package", aura)
        self.assertIn("/blob/aura-v0.17.2/docs/FEATURES.md", aura)
        field = release.github_notes(BY_ID["garmin-field"], self.entry("0.17.2", "garmin-field"), "0.17.2",
                                     garmin=garmin["field"])
        self.assertIn("- `SukraraField-0.17.2.iq` and `SukraraField-0.17.2-sideload.zip`: Sukrara data field, the "
                      "Garmin data field.", field)
        sdk = release.github_notes(BY_ID["face-sdk"], self.entry("5.0.1", "face-sdk"), "5.0.1")
        self.assertIn("- `SukraraFace-5.0.1.barrel`: SukraraFace 5.0.1, the Monkey Barrel", sdk)
        self.assertIn("/blob/face-sdk-v5.0.1/docs/GARMIN-FACES.md", sdk)
        # every file a release attaches is named in its notes
        for a, notes, g, v in [(BY_ID["mac"], mac, None, "0.17.2"), (BY_ID["aura"], aura, garmin["aura"], "0.17.2"),
                               (BY_ID["face-sdk"], sdk, None, "5.0.1")]:
            for rel in release.release_files(a, v, g).values():
                self.assertIn("`%s`" % os.path.basename(rel), notes)

    def test_unwrap_keeps_structure(self):
        md = "Para one\ncontinues.\n\n- item\n  wrapped\n- next\n\n```\nkeep\nlines\n```\n## H\nText"
        self.assertEqual(release.unwrap(md),
                         "Para one continues.\n\n- item wrapped\n- next\n\n```\nkeep\nlines\n```\n## H\nText")

    def test_play_notes(self):
        tmp = tempfile.mkdtemp()
        try:
            with self.assertRaisesRegex(ValueError, "no Play notes .*en-US/changelogs/14000.txt"):
                release.read_play_notes(tmp, "0.14.0")
            for lang, text in [("en-US", "New things.\n"), ("es-ES", "Cosas nuevas.\n")]:
                d = os.path.join(tmp, os.path.dirname(release.PLAY_NOTES % (lang, 14000)))
                os.makedirs(d)
                with open(os.path.join(d, "14000.txt"), "w") as f:
                    f.write(text)
            self.assertEqual(release.read_play_notes(tmp, "0.14.0"), {"en-US": "New things.", "es-ES": "Cosas nuevas."})
            with open(os.path.join(tmp, release.PLAY_NOTES % ("es-ES", 14000)), "w") as f:
                f.write("x" * 501)
            with self.assertRaisesRegex(ValueError, "es-ES are 501 characters"):
                release.read_play_notes(tmp, "0.14.0")
        finally:
            shutil.rmtree(tmp)


class AppStoreNotesTest(unittest.TestCase):
    def test_appstore_notes(self):
        tmp = tempfile.mkdtemp()
        try:
            with self.assertRaisesRegex(ValueError, "no App Store notes .*whats-new/en-US/14000.txt"):
                release.read_appstore_notes(tmp, "0.14.0")
            for lang, text in [("en-US", "New things.\n"), ("es-ES", "Cosas nuevas.\n")]:
                path = os.path.join(tmp, release.ASC_NOTES % (lang, 14000))
                os.makedirs(os.path.dirname(path))
                with open(path, "w") as f:
                    f.write(text)
            self.assertEqual(release.read_appstore_notes(tmp, "0.14.0"),
                             {"en-US": "New things.", "es-ES": "Cosas nuevas."})
            with open(os.path.join(tmp, release.ASC_NOTES % ("es-ES", 14000)), "w") as f:
                f.write("x" * 4001)
            with self.assertRaisesRegex(ValueError, "4001 characters"):
                release.read_appstore_notes(tmp, "0.14.0")
        finally:
            shutil.rmtree(tmp)


class AppStoreVersionTest(unittest.TestCase):
    """The appstore step: one App Store version in review at a time, every release submitted once it can be."""

    def v(self, version, state, vid=None):
        return {"id": vid or "v" + version, "version": version, "state": state, "release": "AFTER_APPROVAL"}

    def test_key_id_from_the_file_name(self):
        self.assertEqual(stores.asc_key_id("/x/.apple-dev/release-AuthKey_DVJY683AS9.p8"), "DVJY683AS9")
        self.assertEqual(stores.asc_key_id("AuthKey_VCB5XBQ3QZ.p8"), "VCB5XBQ3QZ")
        with self.assertRaisesRegex(RuntimeError, "isn't named"):
            stores.asc_key_id("/x/release.p8")

    def test_plan(self):
        plan = stores.asc_version_plan
        self.assertEqual(plan([], "0.14.6")[:2], ("create", None))
        self.assertEqual(plan([self.v("0.14.5", "READY_FOR_DISTRIBUTION")], "0.14.6")[:2], ("create", None))
        self.assertEqual(plan([self.v("0.14.5", "WAITING_FOR_REVIEW")], "0.14.6")[:2], ("wait", "v0.14.5"))
        self.assertEqual(plan([self.v("0.14.6", "IN_REVIEW")], "0.14.6")[:2], ("done", "v0.14.6"))
        self.assertEqual(plan([self.v("0.14.6", "READY_FOR_SALE")], "0.14.6")[:2], ("done", "v0.14.6"))
        for state in ("PREPARE_FOR_SUBMISSION", "REJECTED", "DEVELOPER_REJECTED", "READY_FOR_REVIEW"):
            self.assertEqual(plan([self.v("0.14.5", "READY_FOR_DISTRIBUTION", "live"), self.v("0.14.6", state)],
                                  "0.14.7")[:2], ("reuse", "v0.14.6"))

    def calls(self, versions, drafts=()):
        """submit_for_review against a fake API: the (method, path, body) it sent."""
        sent = []

        class Fake(stores.AppStoreConnect):
            def __init__(self):
                pass

            def versions(self, app_id, platform="MAC_OS"):
                return versions

            def call(self, method, path, params=None, body=None):
                sent.append((method, path, body))
                if path == "/v1/appStoreVersions" and method == "POST":
                    return {"data": {"id": "new"}}
                if path.endswith("/appStoreVersionLocalizations"):
                    return {"data": [{"id": "l1", "attributes": {"locale": "en-US"}},
                                     {"id": "l2", "attributes": {"locale": "es-ES"}}]}
                if path == "/v1/reviewSubmissions" and method == "GET":
                    return {"data": list(drafts)}
                if path == "/v1/reviewSubmissions":
                    return {"data": {"id": "s1"}}
                return {"data": {}}
        result = Fake().submit_for_review("app1", "0.14.6", "b6", {"en-US": "New.", "es-ES": "Nuevo."})
        return result, sent

    def test_creates_attaches_sets_whats_new_and_submits(self):
        (action, _), sent = self.calls([self.v("0.14.5", "READY_FOR_DISTRIBUTION")])
        self.assertEqual(action, "submitted")
        create = sent[0]
        self.assertEqual((create[0], create[1]), ("POST", "/v1/appStoreVersions"))
        self.assertEqual(create[2]["data"]["attributes"],
                         {"versionString": "0.14.6", "releaseType": "AFTER_APPROVAL", "platform": "MAC_OS"})
        self.assertIn(("PATCH", "/v1/appStoreVersions/new/relationships/build",
                       {"data": {"type": "builds", "id": "b6"}}), sent)
        news = {b["data"]["id"]: b["data"]["attributes"]["whatsNew"] for m, p, b in sent
                if p.startswith("/v1/appStoreVersionLocalizations/")}
        self.assertEqual(news, {"l1": "New.", "l2": "Nuevo."})
        self.assertEqual(sent[-1], ("PATCH", "/v1/reviewSubmissions/s1",
                                    {"data": {"type": "reviewSubmissions", "id": "s1",
                                              "attributes": {"submitted": True}}}))

    def test_first_version_has_no_whats_new_and_a_draft_submission_is_reused(self):
        (action, _), sent = self.calls([self.v("0.14.5", "PREPARE_FOR_SUBMISSION")], drafts=[{"id": "d1"}])
        self.assertEqual(action, "submitted")
        self.assertEqual(sent[0][:2], ("PATCH", "/v1/appStoreVersions/v0.14.5"))
        self.assertEqual(sent[0][2]["data"]["attributes"], {"versionString": "0.14.6", "releaseType": "AFTER_APPROVAL"})
        self.assertFalse([p for m, p, b in sent if "Localizations" in p])
        self.assertFalse([p for m, p, b in sent if m == "POST"])
        self.assertEqual(sent[-1][1], "/v1/reviewSubmissions/d1")

    def test_waits_while_another_version_is_in_review(self):
        (action, why), sent = self.calls([self.v("0.14.5", "WAITING_FOR_REVIEW")])
        self.assertEqual((action, sent), ("wait", []))
        self.assertIn("0.14.5 is WAITING_FOR_REVIEW", why)


class PlayTest(unittest.TestCase):
    def test_track_body(self):
        body = stores.play_track_body("internal", "0.14.0", 14000, {"es-ES": "Cosas.", "en-US": "Things."})
        self.assertEqual(body, {"track": "internal", "releases": [{
            "name": "0.14.0", "versionCodes": ["14000"], "status": "completed",
            "releaseNotes": [{"language": "en-US", "text": "Things."}, {"language": "es-ES", "text": "Cosas."}]}]})
        with self.assertRaises(ValueError):
            stores.play_track_body("internal", "0.14.0", 14000, {"en-US": " "})

    def test_track_has(self):
        track = {"track": "internal", "releases": [{"name": "0.13.0", "versionCodes": ["13000"], "status": "completed"}]}
        self.assertEqual(stores.track_has(track, 13000)["name"], "0.13.0")
        self.assertIsNone(stores.track_has(track, 14000))
        self.assertIsNone(stores.track_has({"track": "beta"}, 14000))

    def test_jwt_claims(self):
        c = stores.google_claims("sa@x.iam.gserviceaccount.com", 1000)
        self.assertEqual(c, {"iss": "sa@x.iam.gserviceaccount.com", "scope": "https://www.googleapis.com/auth/androidpublisher",
                             "aud": "https://oauth2.googleapis.com/token", "iat": 1000, "exp": 4600})
        self.assertEqual(stores.asc_claims("issuer", 1000)["exp"], 2200)  # App Store Connect: 20 minutes at most
        signing = stores.jwt_signing_input({"alg": "ES256"}, {"a": 1})
        head = json.loads(base64.urlsafe_b64decode(signing.split(".")[0] + "=="))
        self.assertEqual(head, {"alg": "ES256"})


class SigningTest(unittest.TestCase):
    """openssl signs with a throwaway key; ES256's DER signature becomes r ‖ s."""

    def test_es256_raw_signature_verifies(self):
        tmp = tempfile.mkdtemp()
        try:
            key, pub = os.path.join(tmp, "k.pem"), os.path.join(tmp, "p.pem")
            subprocess.run(["openssl", "genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-256", "-out", key],
                           check=True, capture_output=True)
            subprocess.run(["openssl", "pkey", "-in", key, "-pubout", "-out", pub], check=True, capture_output=True)
            der = stores.openssl_sign(b"payload", key_file=key)
            raw = stores.der_to_raw(der)
            self.assertEqual(len(raw), 64)
            # back to DER and verified by openssl: the conversion kept r and s
            def integer(b):
                b = b.lstrip(b"\0") or b"\0"
                if b[0] & 0x80:
                    b = b"\0" + b
                return b"\x02" + bytes([len(b)]) + b
            seq = integer(raw[:32]) + integer(raw[32:])
            with open(os.path.join(tmp, "sig"), "wb") as f:
                f.write(b"\x30" + bytes([len(seq)]) + seq)
            with open(os.path.join(tmp, "data"), "wb") as f:
                f.write(b"payload")
            r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", pub, "-signature", os.path.join(tmp, "sig"),
                                os.path.join(tmp, "data")], capture_output=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            with open(key) as f:
                self.assertEqual(len(stores.openssl_sign(b"x", key_pem=f.read())) > 0, True)  # key on stdin
        finally:
            shutil.rmtree(tmp)

    def test_der_to_raw_pads(self):
        der = b"\x30\x08\x02\x02\x00\x81\x02\x02\x01\x02"
        self.assertEqual(stores.der_to_raw(der, 4), b"\0\0\0\x81\0\0\x01\x02")


class AppcastAndRunsTest(unittest.TestCase):
    def test_appcast_item(self):
        feed = ('<channel>\n    <item>\n      <title>Sukrara 0.14.0</title>\n      <enclosure url="https://github.com/'
                'mauriciabad/sukrara/releases/download/v0.14.0/Sukrara-0.14.0.dmg" length="123" '
                'type="application/octet-stream"\n        sparkle:edSignature="SIG=="/>\n    </item>\n  </channel>')
        self.assertEqual(release.appcast_item(feed, "0.14.0")["length"], 123)
        self.assertEqual(release.appcast_item(feed, "0.14.0")["signature"], "SIG==")
        self.assertIsNone(release.appcast_item(feed, "0.14.1"))

    def test_pick_run(self):
        self.assertEqual(release.pick_run([]), "pending")
        self.assertEqual(release.pick_run([{"status": "in_progress"}]), "pending")
        self.assertEqual(release.pick_run([{"status": "completed", "conclusion": "failure"}]), "failure")
        self.assertEqual(release.pick_run([{"status": "completed", "conclusion": "failure"},
                                           {"status": "completed", "conclusion": "success"}]), "success")

    def test_a_run_started_by_hand_counts_and_a_stuck_push_run_doesnt_block(self):
        stuck = {"event": "push", "status": "completed", "conclusion": "startup_failure"}
        queued = {"event": "push", "status": "queued", "conclusion": ""}
        by_hand = {"event": "workflow_dispatch", "status": "completed", "conclusion": "success"}
        merge = {"event": "pull_request", "status": "completed", "conclusion": "success"}
        self.assertEqual(release.commit_runs([stuck, by_hand, merge]), [stuck, by_hand])
        self.assertEqual(release.pick_run(release.commit_runs([stuck, by_hand])), "success")
        self.assertEqual(release.pick_run(release.commit_runs([queued, by_hand])), "success")
        self.assertEqual(release.pick_run(release.commit_runs([stuck, merge])), "failure")  # a PR run tests a merge
        self.assertEqual(release.pick_run(release.commit_runs([stuck, dict(by_hand, status="in_progress",
                                                                           conclusion="")])), "pending")

    def test_submission_bodies(self):
        create, item, submit = stores.asc_submission_bodies("app1", "ver1")
        self.assertEqual(create["data"]["attributes"], {"platform": "MAC_OS"})
        self.assertEqual(item("s1")["data"]["relationships"]["appStoreVersion"]["data"]["id"], "ver1")
        self.assertEqual(submit("s1")["data"]["attributes"], {"submitted": True})


DISTRIBUTION = """<?xml version="1.0" encoding="utf-8"?>
<installer-gui-script minSpecVersion="2">
    <pkg-ref id="app.mauri.sukrara">
        <bundle-version>
            <bundle CFBundleShortVersionString="0.14.0" CFBundleVersion="14000.261111.2209" id="app.mauri.sukrara" path="Sukrara.app"/>
        </bundle-version>
    </pkg-ref>
    <product id="app.mauri.sukrara" version="0.14.0"/>
</installer-gui-script>
"""


class FakeAppStoreConnect:
    """App Store Connect with the builds it has: the upload (mac-appstore.sh --upload) adds one, processed at once."""

    def __init__(self, builds=()):
        self.list = list(builds)

    def app_id(self):
        return "app1"

    def builds(self, app, version=None, build=None, limit=10):
        return [b for b in self.list if version in (None, b["version"]) and build in (None, b["build"])][:limit]


class TestFlightTest(unittest.TestCase):
    """testflight uploads the pkg the gate built and validated; it builds one only when there's none."""

    def test_pkg_build_checks_version_and_build_number(self):
        self.assertEqual(release.pkg_build(DISTRIBUTION, "0.14.0"), "14000.261111.2209")
        with self.assertRaisesRegex(ValueError, "version 0.14.0, not 0.15.0"):
            release.pkg_build(DISTRIBUTION, "0.15.0")
        with self.assertRaisesRegex(ValueError, r"build number 14000 isn't 14000.<yyMMdd>.<HHmm>"):
            release.pkg_build(DISTRIBUTION.replace("14000.261111.2209", "14000"), "0.14.0")
        with self.assertRaisesRegex(ValueError, "build number 13000.261111.2209"):
            release.pkg_build(DISTRIBUTION.replace("14000.261111.2209", "13000.261111.2209"), "0.14.0")
        with self.assertRaisesRegex(ValueError, "no app.mauri.sukrara app"):
            release.pkg_build(DISTRIBUTION.replace('id="app.mauri.sukrara" path', 'id="other" path'), "0.14.0")

    def setUp(self):
        self.root, self.asc_class, self.tmp = release.ROOT, stores.AppStoreConnect, tempfile.mkdtemp()
        release.ROOT = self.tmp
        os.makedirs(os.path.join(self.tmp, release.MAC_BUILD))
        self.pkg = os.path.join(self.tmp, release.appstore_pkg("0.14.0"))

    def tearDown(self):
        release.ROOT, stores.AppStoreConnect = self.root, self.asc_class
        shutil.rmtree(self.tmp)

    def run_step(self, asc, distribution=DISTRIBUTION):
        """testflight with fake App Store Connect and fake scripts: the commands it ran."""
        ran, test = [], self

        class Fake(release.Release):
            def sh(self, cmd, mutate=True, capture=True, cwd=None, check=True, env=None):
                ran.append(cmd)
                if cmd == ["tools/release/mac-appstore.sh"]:
                    open(test.pkg, "w").close()
                if cmd == ["tools/release/mac-appstore.sh", "--upload"]:
                    asc.list.insert(0, {"id": "b1", "build": "14000.261111.2209", "version": "0.14.0",
                                        "state": "VALID", "encryption": None, "expired": False})
                return ""

            def pkg_distribution(self, pkg):
                test.assertEqual(pkg, test.pkg)
                return distribution
        stores.AppStoreConnect = lambda: asc
        step = Fake({"mac": "0.14.0"}, False, apps=APPS)
        step.testflight()
        return ran, step

    def test_uploads_the_gates_pkg_without_building_again(self):
        open(self.pkg, "w").close()
        ran, step = self.run_step(FakeAppStoreConnect())
        self.assertEqual(ran, [["tools/release/mac-appstore.sh", "--upload"]])
        self.assertIn("build 14000.261111.2209 shows Missing Compliance", step.notes_state[0])

    def test_builds_the_pkg_once_when_it_is_missing(self):
        ran, _ = self.run_step(FakeAppStoreConnect())
        self.assertEqual(ran, [["tools/release/mac-appstore.sh"], ["tools/release/mac-appstore.sh", "--upload"]])

    def test_a_pkg_of_another_version_or_an_uploaded_build_is_not_uploaded(self):
        open(self.pkg, "w").close()
        with self.assertRaisesRegex(ValueError, "version 0.15.0, not 0.14.0"):
            self.run_step(FakeAppStoreConnect(), DISTRIBUTION.replace('"0.14.0"', '"0.15.0"'))
        invalid = {"id": "b0", "build": "14000.261111.2209", "version": "0.14.0", "state": "INVALID",
                   "encryption": None, "expired": False}
        with self.assertRaisesRegex(release.Stop, "has build 14000.261111.2209 already"):
            self.run_step(FakeAppStoreConnect([invalid]))

    def test_a_build_in_app_store_connect_is_not_uploaded_again(self):
        valid = {"id": "b0", "build": "14000.261111.2209", "version": "0.14.0", "state": "VALID",
                 "encryption": True, "expired": False}
        ran, step = self.run_step(FakeAppStoreConnect([valid]))
        self.assertEqual(ran, [])
        self.assertEqual(step.notes_state, [])


class GarminTest(unittest.TestCase):
    def test_apps_from_the_registry(self):
        apps = release.garmin_apps(REGISTRY)
        self.assertEqual([(a["app"], a["file"], a["kind"]) for a in apps.values()],
                         [("aura", "SukraraAura", "watch face"), ("app", "Sukrara", "watch app"),
                          ("field", "SukraraField", "data field")])
        with self.assertRaises(ValueError):  # a new app needs its file name here and in garmin-release.sh
            release.garmin_apps(json.dumps({"apps": [{"app": "other", "name": "X", "type": "watchface"}]}))

    def test_files(self):
        apps = release.garmin_apps(REGISTRY)
        self.assertEqual(release.release_files(BY_ID["aura"], "0.15.0", apps["aura"]), {
            "iq": "apps/garmin/bin/dist/SukraraAura-0.15.0.iq",
            "sideload": "apps/garmin/bin/dist/SukraraAura-0.15.0-sideload.zip"})
        self.assertEqual(release.release_files(BY_ID["garmin-app"], "0.15.0", apps["app"])["iq"],
                         "apps/garmin/bin/dist/Sukrara-0.15.0.iq")
        self.assertEqual(release.release_files(BY_ID["face-sdk"], "5.0.1"),
                         {"barrel": "apps/garmin/bin/dist/SukraraFace-5.0.1.barrel"})

    def test_upload_kit_has_the_apps_entry(self):
        aura = release.garmin_apps(REGISTRY)["aura"]
        changelog = versions.changelog_with_entry(CHANGELOG, "0.18.0", "2026-11-24",
                                                  ["- **Two fields on top** (#240): the face shows\n  two.",
                                                   "- **Faster**: yes."])
        kit = release.garmin_upload_kit("0.18.0", aura, "/r/SukraraAura-0.18.0.iq", changelog)
        self.assertIn("  1. Sukrara Aura → Upload a new version\n  2. File: /r/SukraraAura-0.18.0.iq", kit)
        self.assertIn("       - **Two fields on top** (#240): the face shows two.\n       - **Faster**: yes.", kit)
        self.assertNotIn("Older", kit)

    def test_listing_problems(self):
        # the real listings pass
        for a in release.garmin_apps(REGISTRY):
            self.assertEqual(release.listing_problems(release.ROOT, a), [])
        # a made-up one breaks each rule once
        root = tempfile.mkdtemp()
        try:
            store = os.path.join(root, "apps/garmin/x/store")
            os.makedirs(store)
            os.makedirs(os.path.join(root, "img"))
            with open(os.path.join(store, "listing.json"), "w") as f:
                json.dump({"title": {"en": "Sukrara for Garmin", "es": "Sukrara" * 8}, "tagline": {"en": ["a", "b"]},
                           "category": "c", "images": "img", "screenshots": ["s", "t", "u", "v", "w", "z"], "price": "Free",
                           "privacy": {"en": "https://p/", "es": "https://p/es/"}, "support": {"en": "s", "es": "s"}},
                          f)
            en = "**Sukrara** shows it. Android, Garmin Connect. iPhone is not supported. https://p/\n" + "x" * 4000
            with open(os.path.join(store, "description-en.txt"), "w") as f:
                f.write(en)
            png = b"\x89PNG\r\n\x1a\n" + b"\0\0\0\rIHDR" + (500).to_bytes(4, "big") + (500).to_bytes(4, "big")
            with open(os.path.join(root, "img/icon-500.png"), "wb") as f:
                f.write(png + b"\0" * (400 * 1024))
            with open(os.path.join(root, "img/hero-en.png"), "wb") as f:
                f.write(png)
            with open(os.path.join(root, "img/screen-t.jpg"), "wb") as f:
                f.write(png)
            jpg = b"\xff\xd8\xff\xe0\x00\x04ab\xff\xc0\x00\x11\x08" + (500).to_bytes(2, "big") + (1000).to_bytes(2, "big")
            self.assertEqual(release.jpeg_size(jpg + b"\0" * 12), (1000, 500))
            with open(os.path.join(root, "img/screen-u.jpg"), "wb") as f:
                f.write(jpg + b"\0" * 12)
            problems = "\n".join(release.listing_problems(root, "x"))
            self.assertNotIn("screen-u.jpg", problems)
            for want in ["the en title is missing or names Garmin", "description-en.txt is 4", "doesn't say 'not a "
                         "medical device'", "has Markdown", "no apps/garmin/x/store/description-es.txt",
                         "img/icon-500.png is 400 KB (at most 300)", "img/hero-en.png isn't a 1440 × 720 PNG",
                         "no hero es", "no screenshot s", "6 screenshots (the store takes at most 5)",
                         "the es title is over 50 characters", "img/screen-t.jpg isn't a JPEG"]:
                self.assertIn(want, problems)
        finally:
            shutil.rmtree(root)


class StepsTest(unittest.TestCase):
    """Each step only for the apps released: the commands a dry run of every step would run."""

    def dry(self, releases):
        ran = []

        class Fake(release.Release):
            def sh(self, cmd, mutate=True, capture=True, cwd=None, check=True, env=None):
                ran.append(" ".join(cmd))
                return ""
        rel = Fake(releases, True, apps=APPS)
        rel.gate()
        rel.garmin_step()
        for step in ("sparkle", "homebrew", "play", "testflight", "appstore"):
            getattr(rel, step)()
        return ran, rel

    def test_android_alone(self):
        ran, rel = self.dry({"android": "0.18.0"})
        self.assertEqual(ran, ["tools/release/android-release.sh", "tools/release/android-smoke.sh --final --no-build",
                               "tools/release/play-bundle.sh"])
        self.assertEqual(rel.notes_state, [])

    def test_a_garmin_app_and_the_barrel(self):
        ran, rel = self.dry({"garmin-field": "0.17.2", "face-sdk": "5.0.1"})
        self.assertEqual(ran, ["tools/release/garmin-release.sh field 0.17.2", "tools/release/garmin-release.sh sdk"])
        self.assertEqual(len(rel.notes_state), 1)
        self.assertIn("Sukrara data field SukraraField-0.17.2.iq", rel.notes_state[0])
        self.assertEqual(rel.tag("garmin-field"), "garmin-field-v0.17.2")
        self.assertEqual(rel.name("face-sdk"), "SukraraFace 5.0.1")

    def test_mac(self):
        ran, _ = self.dry({"mac": "0.17.2"})
        self.assertEqual(ran[:3], ["tools/release/mac-release.sh", "apps/macos/Bundle/smoke.sh",
                                   "tools/release/mac-appstore.sh"])
        self.assertNotIn("tools/release/android-release.sh", ran)

    def test_github_marks_only_android_latest(self):
        ran = []

        class Fake(release.Release):
            def sh(self, cmd, mutate=True, capture=True, cwd=None, check=True, env=None):
                ran.append(cmd)
                return ""

            def remote_tag(self, tag_name):
                return ""
        rel = Fake({"android": "0.18.0", "aura": "0.17.2"}, True, apps=APPS)
        rel.changelogs = {"android": NotesTest.entry(None), "aura": NotesTest.entry(None, "0.17.2", "aura")}
        real = subprocess.run
        subprocess.run = lambda *a, **k: subprocess.CompletedProcess(a, 1)  # no release exists yet
        try:
            for i in rel.v:
                rel.github_release(i, "a" * 40, "AB")
        finally:
            subprocess.run = real
        creates = [c for c in ran if c[:3] == ["gh", "release", "create"]]
        self.assertEqual([(c[3], c[c.index("--title") + 1], c[c.index("--notes-file") + 2]) for c in creates],
                         [("v0.18.0", "Sukrara for Android 0.18.0", "--latest"),
                          ("aura-v0.17.2", "Sukrara Aura 0.17.2", "--latest=false")])
        self.assertTrue(creates[1][-1].endswith("SukraraAura-0.17.2-sideload.zip"))
        self.assertIn(["git", "tag", "-a", "aura-v0.17.2", "-m", "Sukrara Aura 0.17.2", "a" * 40], ran)

    def test_release_checks_hook(self):
        """#262 plugs its shared-formats check into RELEASE_CHECKS: a problem it finds stops the release."""
        seen = []
        release.RELEASE_CHECKS.append(lambda root, releases: seen.append(releases) or ["formats: aura is behind"])
        try:
            class Fake(release.Release):
                def sh(self, cmd, mutate=True, capture=True, cwd=None, check=True, env=None):
                    return "a" * 40 if cmd[:2] == ["git", "rev-parse"] else ""

                def dirty(self):
                    return []

                def checks_state(self, sha, branch=None, workflow="checks.yml"):
                    return "success"
            rel = Fake({"face-sdk": "5.0.1"}, True, apps=APPS)
            rel.preconditions()
            self.assertEqual(seen, [{"face-sdk": "5.0.1"}])
            self.assertIn("formats: aura is behind", rel.would_stop)
        finally:
            release.RELEASE_CHECKS.pop()


class CheckoutTest(unittest.TestCase):
    """Any clean checkout whose HEAD is origin/next releases: agents run release.py from their worktrees."""

    def test_start_needs_a_clean_tree_at_origin_next_and_no_branch(self):
        a, b = "a" * 40, "b" * 40
        self.assertEqual(release.start_problems(a, a, []), [])
        self.assertEqual(release.start_problems(a, b, []), ["HEAD (aaaaaaaa) isn't origin/next (bbbbbbbb)"])
        self.assertEqual(release.start_problems(a, a, ["CHANGELOG.md"]), ["the tree isn't clean: CHANGELOG.md"])
        self.assertEqual(len(release.start_problems(a, b, ["apps/android/VERSION"])), 2)

    def test_resume_allows_only_the_appcast(self):
        self.assertEqual(release.resume_problems([]), [])
        self.assertEqual(release.resume_problems(["website/public/appcast.xml"]), [])
        self.assertEqual(release.resume_problems(["website/public/appcast.xml", "apps/macos/VERSION"]),
                         ["the tree has changes besides website/public/appcast.xml: apps/macos/VERSION"])

    def test_dry_run_counts_what_would_stop(self):
        r = release.Release({"android": "0.14.0"}, True, apps=APPS)
        self.assertTrue(r.ok(True, "fine"))
        self.assertFalse(r.ok(False, "no Play notes"))
        self.assertEqual(r.would_stop, ["no Play notes"])
        with self.assertRaises(release.Stop):
            release.Release({"android": "0.14.0"}, False, apps=APPS).ok(False, "no Play notes")


class GitRepo(unittest.TestCase):
    """A real origin, a clone whose main checkout holds `next`, and a worktree on its own branch (as an agent's)."""

    def git(self, cwd, *args):
        return subprocess.run(["git"] + list(args), cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()

    def write(self, cwd, name, text):
        os.makedirs(os.path.dirname(os.path.join(cwd, name)), exist_ok=True)
        with open(os.path.join(cwd, name), "w") as f:
            f.write(text)

    def commit(self, cwd, name, text):
        self.write(cwd, name, text)
        self.git(cwd, "add", name)
        self.git(cwd, "commit", "-q", "-m", name)
        return self.git(cwd, "rev-parse", "HEAD")

    def seed(self, cwd):
        """The repository's files the version step reads: the registry, each app's version source and changelog,
        the files its mentions name."""
        for a in APPS:
            for rel in [versions.REGISTRY, a["version"], a["changelog"]] + [m["file"] for m in a.get("mentions", [])]:
                self.write(cwd, rel, read(rel))

    def setUp(self):
        self.env, self.root = dict(os.environ), release.ROOT
        os.environ.update({"GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t",
                           "GIT_AUTHOR_EMAIL": "t@example.com", "GIT_COMMITTER_NAME": "t",
                           "GIT_COMMITTER_EMAIL": "t@example.com"})
        self.tmp = tempfile.mkdtemp()
        self.origin, self.main, self.wt = (os.path.join(self.tmp, d) for d in ("origin.git", "main", "wt"))
        self.git(self.tmp, "init", "-q", "--bare", self.origin)
        self.git(self.tmp, "clone", "-q", self.origin, self.main)
        self.git(self.main, "checkout", "-q", "-b", "next")
        self.seed(self.main)
        self.git(self.main, "add", "-A")
        self.git(self.main, "commit", "-q", "-m", "seed")
        self.git(self.main, "push", "-q", "origin", "next", "next:main")
        self.git(self.main, "worktree", "add", "-q", "-b", "agent", self.wt, "origin/next")
        release.ROOT = self.wt

    def tearDown(self):
        release.ROOT = self.root
        os.environ.clear()
        os.environ.update(self.env)
        shutil.rmtree(self.tmp)


@unittest.skipUnless(shutil.which("git"), "needs git")
class WorktreeGitTest(GitRepo):
    def test_a_worktree_on_its_own_branch_can_start(self):
        r = release.Release({"android": "0.14.0"}, False, apps=APPS)
        self.assertEqual(r.sh(["git", "rev-parse", "--abbrev-ref", "HEAD"], mutate=False), "agent")
        self.assertEqual(release.start_problems(r.head(), self.git(self.wt, "rev-parse", "origin/next"), r.dirty()), [])
        r.resume_check()  # no Stop: the branch isn't next

    def test_resuming_needs_each_app_built_from_its_release_commit(self):
        self.commit(self.wt, "apps/android/VERSION", "0.19.0\n")  # the release commit
        self.commit(self.wt, "apps/garmin/aura/source/AuraDraw.mc", "fix\n")  # another app's release since
        self.commit(self.wt, "docs/DEVELOPMENT.md", "more\n")
        release.Release({"android": "0.19.0"}, False, apps=APPS).resume_check()  # no Stop: Android's build is the same
        self.commit(self.wt, "apps/android/app/Main.kt", "changed\n")
        with self.assertRaisesRegex(release.Stop, "Sukrara for Android changed since its release commit .*Main.kt"):
            release.Release({"android": "0.19.0"}, False, apps=APPS).resume_check()
        release.Release({"mac": "0.18.0"}, False, apps=APPS).resume_check()  # not the Mac's
        self.commit(self.wt, "apps/garmin/shared/link/Link.mc", "changed\n")  # every Garmin app's
        with self.assertRaisesRegex(release.Stop, "Sukrara watch app changed since"):
            release.Release({"garmin-app": "0.18.0"}, False, apps=APPS).resume_check()

    def test_push_next_merges_when_next_moved_and_keeps_head_on_the_release(self):
        appcast_commit = self.commit(self.wt, "appcast.xml", "0.14.0\n")
        other = self.commit(self.main, "other.txt", "unreleased\n")  # next moved meanwhile
        self.git(self.main, "push", "-q", "origin", "next")
        r = release.Release({"mac": "0.14.0"}, False, apps=APPS)
        r.sh(["git", "push", "origin", "HEAD:main"])
        r.push_next()
        self.assertEqual(r.head(), appcast_commit)  # never the merge
        nxt = self.git(self.origin, "rev-parse", "next")
        self.assertEqual(self.git(self.origin, "log", "-1", "--format=%s", "next"),
                         "Merge %s (Sukrara for Mac 0.14.0, on main) into next" % appcast_commit[:8])
        for c in (appcast_commit, other):
            self.git(self.origin, "merge-base", "--is-ancestor", c, nxt)
        r.push_next()  # a re-run changes nothing
        self.assertEqual(self.git(self.origin, "rev-parse", "next"), nxt)
        r.sh(["git", "push", "origin", "HEAD:main"])  # the re-run's push to main: still only the release's commits
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), appcast_commit)

    def test_the_version_step(self):
        """release.py android face-sdk's version step: versions, entries, mentions, change files consumed, one commit."""
        self.commit(self.wt, "changes/quiet.md", CHANGE)
        self.commit(self.wt, "changes/barrel.md", "---\napps: [face-sdk, aura]\nkind: feature\n---\n- **Lap**: new.\n")
        self.git(self.wt, "push", "-q", "origin", "HEAD:next")
        android, sdk = (versions.read_version(BY_ID[i], read(BY_ID[i]["version"])) for i in ("android", "face-sdk"))
        r = release.Release({"android": versions.bump(android, "fix"), "face-sdk": versions.bump(sdk, "feature")},
                            False, today=datetime.date(2026, 11, 24), apps=APPS)
        r.version()
        self.assertEqual(r.dirty(), [])
        self.assertEqual(self.git(self.origin, "rev-parse", "next"), r.head())
        msg = self.git(self.wt, "log", "-1", "--format=%B")
        self.assertTrue(msg.startswith("Release Sukrara for Android %s, SukraraFace %s: versions and CHANGELOG entries"
                                       % (r.v["android"], r.v["face-sdk"])), msg)
        self.assertIn("Changes-skip:", msg)
        wt = lambda rel: open(os.path.join(self.wt, rel), encoding="utf-8").read()  # noqa: E731
        self.assertEqual(wt("apps/android/VERSION"), r.v["android"] + "\n")
        self.assertEqual(versions.read_version(BY_ID["face-sdk"], wt("apps/garmin/sdk/manifest.xml")), r.v["face-sdk"])
        self.assertEqual(wt("apps/macos/VERSION"), read("apps/macos/VERSION"))  # not released: untouched
        entry = versions.changelog_entry(wt("apps/android/CHANGELOG.md"), r.v["android"])
        self.assertEqual(entry[0], "## %s — 2026-11-24" % r.v["android"])
        self.assertIn("[docs/FEATURES.md](../../docs/FEATURES.md)", entry[1])  # rebased for apps/android/
        self.assertIn("- **Lap**: new.", wt("apps/garmin/sdk/CHANGELOG.md"))
        self.assertIn("releases/download/face-sdk-v%s/SukraraFace-%s.barrel" % (r.v["face-sdk"], r.v["face-sdk"]),
                      wt("docs/GARMIN-FACES.md"))
        self.assertIn('<iq:depends name="SukraraFace" version="%s"/>' % r.v["face-sdk"],
                      wt("apps/garmin/sample/manifest.xml"))
        self.assertEqual(versions.parse_change(wt("changes/quiet.md"))["apps"], ["mac"])  # waits for the Mac's release
        self.assertEqual(versions.parse_change(wt("changes/barrel.md"))["apps"], ["aura"])
        mac, aura = (versions.read_version(BY_ID[i], read(BY_ID[i]["version"])) for i in ("mac", "aura"))
        r = release.Release({"mac": versions.bump(mac, "feature"), "aura": versions.bump(aura, "feature")}, False,
                            today=datetime.date(2026, 11, 24), apps=APPS)
        r.version()  # every app that named them released: the files are gone
        self.assertEqual(os.listdir(os.path.join(self.wt, "changes")), [])


@unittest.skipUnless(shutil.which("git"), "needs git")
class BuildInfoTest(unittest.TestCase):
    """tools/release/build-info.sh <app>: what an app says it is. A release only from the release scripts
    (SUKRARA_RELEASE_BUILD=1) when what the app is built from (apps.json 'paths' minus 'exclude', and 'build') is its
    release commit's; other apps' commits, the docs and the tools since don't matter. Every other build a development
    build, counted from the app's own last release."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1", GIT_AUTHOR_NAME="t",
                        GIT_AUTHOR_EMAIL="t@example.com", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@example.com",
                        GIT_COMMITTER_DATE="2026-11-14T10:00:00Z")
        self.env.pop("SUKRARA_RELEASE_BUILD", None)
        os.makedirs(os.path.join(self.tmp, "tools", "release"))
        os.makedirs(os.path.join(self.tmp, "contracts", "apps"))
        for name in ("build-info.sh", "versions.py"):
            shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), name),
                        os.path.join(self.tmp, "tools", "release"))
        shutil.copy(os.path.join(release.ROOT, versions.REGISTRY),
                    os.path.join(self.tmp, "contracts", "apps"))  # the real registry
        self.git("init", "-q")
        self.write("apps/macos/VERSION", "0.14.5\n")
        self.write("apps/macos/Sources/App.swift", "1\n")
        self.write("contracts/alerts/sounds/android/raw/high.ogg", "1\n")
        self.mac = self.commit("Release Sukrara for Mac 0.14.5")
        self.write("apps/android/VERSION", "0.14.5\n")
        self.write("apps/android/app/Main.kt", "1\n")
        self.write("website/public/appcast.xml", "0.14.4\n")
        self.release = self.commit("Release Sukrara for Android 0.14.5")

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def git(self, *args):
        return subprocess.run(["git"] + list(args), cwd=self.tmp, check=True, capture_output=True, text=True,
                              env=self.env).stdout.strip()

    def write(self, name, text):
        os.makedirs(os.path.dirname(os.path.join(self.tmp, name)), exist_ok=True)
        with open(os.path.join(self.tmp, name), "w") as f:
            f.write(text)

    def commit(self, msg):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", msg)
        return self.git("rev-parse", "--short=8", "HEAD")

    def info(self, app="android", release=False):
        env = dict(self.env, SUKRARA_RELEASE_BUILD="1") if release else self.env
        r = subprocess.run(["sh", "tools/release/build-info.sh", app], cwd=self.tmp, capture_output=True, text=True,
                           env=env)
        return r.returncode, r.stdout.strip()

    def test_only_the_release_scripts_build_a_release(self):
        self.assertEqual(self.info(), (0, "development 0 %s 2026-11-14" % self.release))  # install.sh at the release
        self.assertEqual(self.info(release=True), (0, "release"))

    def test_each_app_counts_from_its_own_release(self):
        self.assertEqual(self.info("mac"), (0, "development 1 %s 2026-11-14" % self.release))
        self.assertEqual(self.info("mac", release=True), (0, "release"))  # Android's release commit isn't the Mac's
        self.assertEqual(subprocess.run(["sh", "tools/release/build-info.sh"], cwd=self.tmp, capture_output=True,
                                        env=self.env).returncode, 2)  # which app?

    def test_the_appcast_after_the_gate_is_still_the_release(self):
        self.write("website/public/appcast.xml", "0.14.5\n")  # mac-release.sh writes it in the gate
        self.assertEqual(self.info(release=True), (0, "release"))
        feed = self.commit("Sparkle feed: Sukrara 0.14.5")  # release.py's sparkle step, before testflight
        self.assertEqual(self.info(release=True), (0, "release"))
        self.assertEqual(self.info(), (0, "development 0 %s 2026-11-14" % feed))

    def test_other_apps_docs_and_tools_since_are_still_the_release(self):
        # 0.18.0: Android and Mac waited while the Garmin apps were released; their builds hadn't changed
        self.write("apps/garmin/aura/source/AuraDraw.mc", "fix\n")
        self.write("apps/garmin/aura/manifest.xml", "0.18.1\n")
        self.write("docs/DEVELOPMENT.md", "more\n")
        self.write("tools/release/release.py", "more\n")
        self.write("contracts/apps/formats.json", "{}\n")  # a contract the build doesn't carry
        self.write("apps/android/README.md", "more\n")  # the app's exclude
        self.write("apps/android/fastlane/metadata/android/en-US/changelogs/14006.txt", "next\n")
        self.commit("Release Sukrara Aura 0.18.1")
        self.write("apps/garmin/bin/SukraraAura.iq", "untracked\n")
        self.assertEqual(self.info(release=True), (0, "release"))
        self.assertEqual(self.info("mac", release=True), (0, "release"))
        self.assertEqual(self.info(), (0, "development 1 %s 2026-11-14" % self.git("rev-parse", "--short=8", "HEAD")))

    def test_a_change_to_what_the_app_is_built_from_is_never_a_release(self):
        self.write("apps/android/app/Main.kt", "2\n")
        self.assertEqual(self.info(release=True)[0], 1)  # uncommitted
        self.assertEqual(self.info("mac", release=True), (0, "release"))  # not the Mac's
        self.commit("more")
        self.assertEqual(self.info(release=True)[0], 1)  # committed after the release commit
        self.write("apps/android/app/Main.kt", "1\n")
        self.commit("back")
        self.assertEqual(self.info(release=True), (0, "release"))  # the same files as the release commit
        self.write("contracts/alerts/sounds/android/raw/high.ogg", "2\n")  # 'build': into the APK
        self.assertEqual(self.info(release=True)[0], 1)
        self.git("checkout", "--", ".")
        self.write("apps/android/app/New.kt", "untracked\n")
        self.assertEqual(self.info(release=True)[0], 1)

    def test_outside_git_it_is_a_development_build(self):
        shutil.rmtree(os.path.join(self.tmp, ".git"))
        self.assertEqual(self.info(), (0, "development ? ? ?"))
        self.assertEqual(self.info(release=True)[0], 1)

    def test_what_the_builds_take_from_outside_their_app_is_in_build(self):
        """The release builds' outside inputs, as their scripts name them, are each under the app's 'build', so a
        change to one is a change to the release (Gradle's repoRoot.dir(...) in main source sets, the bundle and dmg
        scripts' repo paths)."""
        apps = BY_ID
        found = {"android": set(), "mac": set()}
        for dirpath, _, names in os.walk(os.path.join(release.ROOT, "apps", "android")):
            if "build" in dirpath.split(os.sep) or ".gradle" in dirpath:
                continue
            for n in names:
                if n.endswith(".gradle.kts"):
                    found["android"].update(re.findall(r'repoRoot\.dir\("([^"]+)"\)', open(os.path.join(dirpath, n)).read()))
        bundle = open(os.path.join(release.ROOT, "apps/macos/Bundle/bundle.sh")).read()
        found["mac"].update(re.findall(r'\$REPO/((?:contracts|assets)/[^"\s)]+)', bundle))
        dmg = open(os.path.join(release.ROOT, "apps/macos/Bundle/dmg.sh")).read()
        found["mac"].update(re.findall(r'\)/((?:contracts|assets)/[^"\s)]+)', dmg))
        for app_id, paths in found.items():
            self.assertTrue(paths, app_id)  # the patterns still find them
            for p in paths:
                self.assertTrue(any((p + "/").startswith(b) for b in apps[app_id]["build"]),
                                "%s's build reads %s: add it to its apps.json 'build'" % (app_id, p))


if __name__ == "__main__":
    unittest.main()
