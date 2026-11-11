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
    def test_code_and_kind_match_lib_sh(self):
        self.assertEqual(release.version_code("0.14.0"), 14000)
        self.assertEqual(release.version_code("1.2.3"), 1002003)
        self.assertEqual(release.version_kind("0.14.0"), "prerelease")
        self.assertEqual(release.version_kind("1.0.0"), "release")
        out = subprocess.run(["sh", os.path.join(release.ROOT, "tools/release/version.sh")], capture_output=True,
                             text=True, check=True).stdout.split()
        self.assertEqual(int(out[1]), release.version_code(out[0]))
        self.assertEqual(out[2], release.version_kind(out[0]))

    def test_rejects_bad_versions(self):
        for v in ["1.2", "v1.2.3", "1.1000.0", "1.2.3-rc1", ""]:
            with self.assertRaises(ValueError):
                release.parse_version(v)

    def test_bump_docs(self):
        dev = "| Version | `tools/release/version.sh` → `0.13.0 13000 prerelease` (from `VERSION`) |"
        self.assertEqual(release.bump_docs("docs/DEVELOPMENT.md", dev, "0.13.0", "0.14.0"),
                         "| Version | `tools/release/version.sh` → `0.14.0 14000 prerelease` (from `VERSION`) |")
        qa = "- [ ] Settings › About: *0.13.0 · Google Play version* (and 0.13.0 elsewhere stays)"
        self.assertEqual(release.bump_docs("docs/QA.md", qa, "0.13.0", "1.0.0"),
                         "- [ ] Settings › About: *1.0.0 · Google Play version* (and 0.13.0 elsewhere stays)")


class ChangelogTest(unittest.TestCase):
    def test_unreleased_becomes_the_entry(self):
        out = release.changelog_release(CHANGELOG, "0.14.0")
        self.assertIn("## Unreleased\n\n## 0.14.0 — the Mac updates itself (GitHub prerelease)\n\n- **The Mac", out)
        self.assertIn("- ***Sound while unlocked*** (Android, #183).\n\n## 0.13.0", out)
        heading, body = release.changelog_entry(out, "0.14.0")
        self.assertEqual(heading, "## 0.14.0 — the Mac updates itself (GitHub prerelease)")
        self.assertTrue(body.startswith("- **The Mac updates itself**"))
        self.assertTrue(body.endswith("(Android, #183)."))
        self.assertEqual(release.changelog_entry(out, "0.13.0")[1], release.changelog_entry(CHANGELOG, "0.13.0")[1])

    def test_without_summary_and_for_a_release(self):
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
        self.assertTrue(notes.startswith("**Not a medical device.** 0.x versions are GitHub prereleases."))
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

    def test_submission_bodies(self):
        create, item, submit = stores.asc_submission_bodies("app1", "ver1")
        self.assertEqual(create["data"]["attributes"], {"platform": "MAC_OS"})
        self.assertEqual(item("s1")["data"]["relationships"]["appStoreVersion"]["data"]["id"], "ver1")
        self.assertEqual(submit("s1")["data"]["attributes"], {"submitted": True})


if __name__ == "__main__":
    unittest.main()
