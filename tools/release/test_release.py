#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for the release command's pure parts: python3 tools/release/test_release.py (no network, no keys)."""
import base64
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release  # noqa: E402
import stores  # noqa: E402

CHANGELOG = """# Changelog

Sukrara's releases.

## Unreleased — the Mac updates itself

- **The Mac updates itself** (#91): once a day it looks for a new version,
  signed by Sukrara. Every behaviour: [docs/FEATURES.md](docs/FEATURES.md).
- ***Sound while unlocked*** (Android, #183).

## 0.13.0 — fixes (GitHub prerelease)

The first build submitted to Google Play.

- **Fixes**: one.
"""


class VersionTest(unittest.TestCase):
    def test_code_matches_lib_sh(self):
        self.assertEqual(release.version_code("0.14.0"), 14000)
        self.assertEqual(release.version_code("1.2.3"), 1002003)
        out = subprocess.run(["sh", os.path.join(release.ROOT, "tools/release/version.sh")], capture_output=True,
                             text=True, check=True).stdout.split()
        self.assertEqual(len(out), 2)
        self.assertEqual(int(out[1]), release.version_code(out[0]))

    def test_rejects_bad_versions(self):
        for v in ["1.2", "v1.2.3", "1.1000.0", "1.2.3-rc1", ""]:
            with self.assertRaises(ValueError):
                release.parse_version(v)

    def test_bump_docs(self):
        dev = "| Version | `tools/release/version.sh` → `0.13.0 13000` (from `VERSION`) |"
        self.assertEqual(release.bump_docs("docs/DEVELOPMENT.md", dev, "0.13.0", "0.14.0"),
                         "| Version | `tools/release/version.sh` → `0.14.0 14000` (from `VERSION`) |")
        qa = "- [ ] Settings › About: *0.13.0 · Google Play version* (and 0.13.0 elsewhere stays)"
        self.assertEqual(release.bump_docs("docs/QA.md", qa, "0.13.0", "1.0.0"),
                         "- [ ] Settings › About: *1.0.0 · Google Play version* (and 0.13.0 elsewhere stays)")


class ChangelogTest(unittest.TestCase):
    def test_unreleased_becomes_the_entry(self):
        out = release.changelog_release(CHANGELOG, "0.14.0")
        self.assertIn("## Unreleased\n\n## 0.14.0 — the Mac updates itself\n\n- **The Mac", out)
        self.assertIn("- ***Sound while unlocked*** (Android, #183).\n\n## 0.13.0", out)
        heading, body = release.changelog_entry(out, "0.14.0")
        self.assertEqual(heading, "## 0.14.0 — the Mac updates itself")
        self.assertTrue(body.startswith("- **The Mac updates itself**"))
        self.assertTrue(body.endswith("(Android, #183)."))
        self.assertEqual(release.changelog_entry(out, "0.13.0")[1], release.changelog_entry(CHANGELOG, "0.13.0")[1])

    def test_without_summary(self):
        out = release.changelog_release(CHANGELOG.replace("## Unreleased — the Mac updates itself", "## Unreleased"),
                                        "1.0.0")
        self.assertIn("## Unreleased\n\n## 1.0.0\n\n- **The Mac", out)

    def test_refusals(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            release.changelog_release("# C\n\n## Unreleased\n\n## 0.13.0\n\n- x\n", "0.14.0")
        with self.assertRaisesRegex(ValueError, "no '## Unreleased'"):
            release.changelog_release("# C\n\n## 0.13.0\n\n- x\n", "0.14.0")
        with self.assertRaisesRegex(ValueError, "already has"):
            release.changelog_release(CHANGELOG, "0.13.0")

    def test_the_real_changelog_moves(self):
        with open(os.path.join(release.ROOT, "CHANGELOG.md"), encoding="utf-8") as f:
            text = f.read()
        if release.changelog_entry(text, "99.0.0") is None and "## Unreleased" in text:
            try:
                out = release.changelog_release(text, "99.0.0")
            except ValueError as e:  # an empty Unreleased section right after a release
                self.assertIn("empty", str(e))
                return
            self.assertTrue(release.changelog_entry(out, "99.0.0"))


class NotesTest(unittest.TestCase):
    def test_github_notes(self):
        out = release.changelog_release(CHANGELOG, "0.14.0")
        notes = release.github_notes(out, "0.14.0", "AB" * 32)
        self.assertTrue(notes.startswith("**Not a medical device.** Every behaviour, with its rules and numbers:"))
        self.assertNotIn("prerelease", notes)
        # hard wraps joined, relative links at the tag
        self.assertIn("- **The Mac updates itself** (#91): once a day it looks for a new version, signed by Sukrara. "
                      "Every behaviour: [docs/FEATURES.md](https://github.com/mauriciabad/sukrara/blob/v0.14.0/docs/"
                      "FEATURES.md).\n- ***Sound", notes)
        self.assertIn("`Sukrara-0.14.0.apk`: Android 16+, arm64. Signed with Sukrara's release key, certificate SHA-256 "
                      "`%s`." % ("AB" * 32), notes)
        for f in ["Sukrara-0.14.0.dmg", "Sukrara-mac-0.14.0.zip", "Sukrara-0.14.0-android-mapping.txt.gz"]:
            self.assertIn(f, notes)
        self.assertNotIn("0.13.0", notes)
        self.assertFalse(release.github_notes(release.changelog_release(CHANGELOG, "1.0.0"), "1.0.0", "AB")
                         .startswith("**Not a medical device.** 0.x"))

    def test_unwrap_keeps_structure(self):
        md = "Para one\ncontinues.\n\n- item\n  wrapped\n- next\n\n```\nkeep\nlines\n```\n## H\nText"
        self.assertEqual(release.unwrap(md),
                         "Para one continues.\n\n- item wrapped\n- next\n\n```\nkeep\nlines\n```\n## H\nText")

    def test_absolute_links(self):
        self.assertEqual(release.absolute_links("[a](docs/A.md) [b](https://x.y) [c](#h)", "0.14.0"),
                         "[a](https://github.com/mauriciabad/sukrara/blob/v0.14.0/docs/A.md) [b](https://x.y) [c](#h)")

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
        step = Fake("0.14.0", False)
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


class CheckoutTest(unittest.TestCase):
    """Any clean checkout whose HEAD is origin/next releases: agents run release.py from their worktrees."""

    def test_start_needs_a_clean_tree_at_origin_next_and_no_branch(self):
        a, b = "a" * 40, "b" * 40
        self.assertEqual(release.start_problems(a, a, []), [])
        self.assertEqual(release.start_problems(a, b, []), ["HEAD (aaaaaaaa) isn't origin/next (bbbbbbbb)"])
        self.assertEqual(release.start_problems(a, a, ["CHANGELOG.md"]), ["the tree isn't clean: CHANGELOG.md"])
        self.assertEqual(len(release.start_problems(a, b, ["VERSION"])), 2)

    def test_resume_allows_only_the_appcast(self):
        self.assertEqual(release.resume_problems([]), [])
        self.assertEqual(release.resume_problems(["docs/appcast.xml"]), [])
        self.assertEqual(release.resume_problems(["docs/appcast.xml", "VERSION"]),
                         ["the tree has changes besides docs/appcast.xml: VERSION"])

    def test_dry_run_counts_what_would_stop(self):
        r = release.Release("0.14.0", True)
        self.assertTrue(r.ok(True, "fine"))
        self.assertFalse(r.ok(False, "no Play notes"))
        self.assertEqual(r.would_stop, ["no Play notes"])
        with self.assertRaises(release.Stop):
            release.Release("0.14.0", False).ok(False, "no Play notes")


@unittest.skipUnless(shutil.which("git"), "needs git")
class WorktreeGitTest(unittest.TestCase):
    """A real origin, a clone whose main checkout holds `next`, and a worktree on its own branch (as an agent's)."""

    def git(self, cwd, *args):
        return subprocess.run(["git"] + list(args), cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, cwd, name, text):
        with open(os.path.join(cwd, name), "w") as f:
            f.write(text)
        self.git(cwd, "add", name)
        self.git(cwd, "commit", "-q", "-m", name)
        return self.git(cwd, "rev-parse", "HEAD")

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
        self.commit(self.main, "VERSION", "0.14.0\n")
        self.git(self.main, "push", "-q", "origin", "next", "next:main")
        self.git(self.main, "worktree", "add", "-q", "-b", "agent", self.wt, "origin/next")
        release.ROOT = self.wt

    def tearDown(self):
        release.ROOT = self.root
        os.environ.clear()
        os.environ.update(self.env)
        shutil.rmtree(self.tmp)

    def test_a_worktree_on_its_own_branch_can_start(self):
        r = release.Release("0.14.0", False)
        self.assertEqual(r.sh(["git", "rev-parse", "--abbrev-ref", "HEAD"], mutate=False), "agent")
        self.assertEqual(release.start_problems(r.head(), self.git(self.wt, "rev-parse", "origin/next"), r.dirty()), [])
        r.resume_check()  # no Stop: the branch isn't next

    def test_push_next_merges_when_next_moved_and_keeps_head_on_the_release(self):
        appcast = self.commit(self.wt, "appcast.xml", "0.14.0\n")
        other = self.commit(self.main, "other.txt", "unreleased\n")  # next moved meanwhile
        self.git(self.main, "push", "-q", "origin", "next")
        r = release.Release("0.14.0", False)
        r.sh(["git", "push", "origin", "HEAD:main"])
        r.push_next()
        self.assertEqual(r.head(), appcast)  # never the merge
        nxt = self.git(self.origin, "rev-parse", "next")
        self.assertEqual(self.git(self.origin, "log", "-1", "--format=%s", "next"),
                         "Merge %s (Sukrara 0.14.0, on main) into next" % appcast[:8])  # not the worktree's branch
        for c in (appcast, other):
            self.git(self.origin, "merge-base", "--is-ancestor", c, nxt)
        r.push_next()  # a re-run changes nothing
        self.assertEqual(self.git(self.origin, "rev-parse", "next"), nxt)
        r.sh(["git", "push", "origin", "HEAD:main"])  # the re-run's push to main: still only the release's commits
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), appcast)


if __name__ == "__main__":
    unittest.main()
