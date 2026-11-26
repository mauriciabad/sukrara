#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""One command releases the apps that changed, each with its own version (docs/48-release-versioning.md,
docs/DEVELOPMENT.md › Release):

    tools/release/release.py                              every app: its version, its change files, its next version
    tools/release/release.py <app>[=<version>] … [--dry-run] [--from <step>]

<app> is an id of contracts/apps/apps.json (android, mac, aura, garmin-app, garmin-field, face-sdk). Each app's next
version comes from the change files naming it (changes/*.md: the highest kind, fix → patch, feature → minor);
<app>=<version> sets it. An app without a change file isn't released.

Runs on the release Mac (the keys stay there), in any clean checkout whose HEAD is origin/next: the main checkout or a
worktree, on any branch or a detached HEAD. Every push names its refspec (HEAD:next, HEAD:main); nothing needs a local
`next` or `main` branch. The steps, in order, each only for the apps released; each checks its result and stops on
failure with what's done and what's left (resume with --from <step>; every step from gate on can run again):

  preconditions  a clean tree, HEAD = origin/next, its Checks run green or still running (not awaited: the version
                 commit's run covers it; a failed one stops), per app: a change file, the new version above the
                 current one, no tag <tag><version> yet; android: the Play notes
                 (apps/android/fastlane/metadata/android/<en-US|es-ES>/changelogs/<versionCode>.txt, at most 500
                 characters) and its keys; mac: the App Store notes (apps/macos/AppStore/whats-new/…, see appstore)
                 and the App Store Connect key; a Garmin app: the store key and its Connect IQ listing
                 (apps/garmin/<app>/store: listing_problems); then every RELEASE_CHECKS function (#262); gh
  version        per app: its version source, its CHANGELOG.md entry from its change files (each file loses the app,
                 and goes once it names none), the files that link its release ('mentions'); one commit, pushed to
                 next (fast-forward; its Checks run is awaited before github)
  gate           android: android-release.sh (verify-apk.sh inside), android-smoke.sh --final --no-build on the
                 emulator, play-bundle.sh; mac: mac-release.sh (notarized, Sparkle-signed, verify-app.sh --notarized
                 inside, writes website/public/appcast.xml), Bundle/smoke.sh, mac-appstore.sh (build + validate);
                 a Garmin app: garmin-release.sh <app> <version> (its .iq and sideload zip, verified, signed with the
                 pinned store key); face-sdk: garmin-release.sh sdk (the SukraraFace barrel). ./gradlew check and
                 swift test aren't repeated: the release commit's green Checks run ran them (the Mac job on the newest
                 green commit with the same Mac sources: apps/macos/VERSION reaches only the bundle, which this gate
                 builds and smokes).
  github         the release commit's Checks run green (its push or one started by hand, any branch; the one wait for
                 CI); main fast-forwarded once; per app a tag <tag><version> and a normal GitHub release titled
                 "<name> <version>" with only its files, its CHANGELOG entry as notes; Android's is GitHub's Latest
                 (the updater, Obtainium, IzzyOnDroid), every other one --latest=false. main's own run isn't awaited:
                 the commit's green run is what it finds (tools/ci/changes.py)
  garmin         a Garmin app (first of the channels: Connect IQ's review is the slowest): the Connect IQ store has no
                 upload API: the release's .iq (built and signed with the store key in the gate, on its GitHub
                 release) and what to enter at the developer dashboard, for the owner's upload: Upload a new version,
                 with its What's new (an app's first upload, with its listing, is by hand: docs/20-publishing.md ›
                 Connect IQ store: the owner's steps)
  sparkle        mac: website/public/appcast.xml committed to main and next once the release's dmg is there, Pages
                 deployed, the feed at https://sukrara.mauri.app/appcast.xml serves the item
  homebrew       mac: the tap's workflow committed the cask for this version (the dmg's sha256)
  play           android: the play bundle on Google Play's internal and production tracks with the en-US / es-ES
                 notes (Android Publisher API; managed publishing is off, so production goes live once Google
                 approves it)
  testflight     mac: the App Store pkg the gate built and validated (built once by mac-appstore.sh if it's missing,
                 e.g. --from testflight in a fresh checkout), its version and build number checked, uploaded by
                 mac-appstore.sh --upload; then App Store Connect processes the build (export compliance: summary)
  appstore       mac: the App Store version: created (or the editable one reused), the build attached, What's New from
                 apps/macos/AppStore/whats-new/<en-US|es-ES>/<versionCode>.txt (at most 4000 characters), released
                 after approval, submitted for App Review (needs the App Manager key); while another version is in
                 review it waits on TestFlight, and a later release submits the newest
  summary        every channel's state

--dry-run      runs the read-only checks, prints every command, request body and note it would use; changes nothing;
               exits 1 when a check would stop the release
--from STEP    starts at STEP, gate or later, at the release commit or a later one (next after other apps' releases):
               the versions are the apps' current ones, and what each app is built from (apps.json 'paths' minus
               'exclude', and 'build') must be its release commit's
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stores  # noqa: E402
import versions  # noqa: E402
from versions import parse_version, version_code  # noqa: E402,F401

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "formats"))
import release_check  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
REPO = "mauriciabad/sukrara"
REPO_URL = "https://github.com/" + REPO
TAP = "mauriciabad/homebrew-tap"
FEED_URL = "https://sukrara.mauri.app/appcast.xml"
STEPS = ["preconditions", "version", "gate", "github", "garmin", "sparkle", "homebrew", "play", "testflight",
         "appstore", "summary"]
RESUMABLE = STEPS[2:]  # the version step consumes the change files: a new release starts at preconditions
PLAY_LANGS = ["en-US", "es-ES"]
PLAY_TRACKS = ["internal", "production"]
PLAY_NOTES = "apps/android/fastlane/metadata/android/%s/changelogs/%d.txt"
ASC_LANGS = ["en-US", "es-ES"]
ASC_NOTES = "apps/macos/AppStore/whats-new/%s/%d.txt"
DIST = "apps/android/app/build/dist"
MAC_BUILD = "apps/macos/build"
APPCAST = "website/public/appcast.xml"
# The Garmin apps (contracts/watchface/payload/apps.json; apps.json's 'garmin' names the entry)
GARMIN_REGISTRY = "contracts/watchface/payload/apps.json"
GARMIN_FILES = {"aura": "SukraraAura", "app": "Sukrara", "field": "SukraraField"}  # garmin-release.sh's <Name>
GARMIN_KINDS = {"watchface": "watch face", "watch-app": "watch app", "datafield": "data field"}
GARMIN_DIST = "apps/garmin/bin/dist"
GARMIN_DASHBOARD = "https://apps-developer.garmin.com/developer/dashboard"
# the listing's rules (docs/20-publishing.md › Connect IQ store: what it asks and checks)
GARMIN_LANGS = ["en", "es"]
GARMIN_DESCRIPTION_MAX = 4000
GARMIN_ICON_MAX, GARMIN_HERO_MAX, GARMIN_SCREEN_MAX = 300 * 1024, 2 * 1024 * 1024, 150 * 1024
GARMIN_SCREENSHOTS_MAX, GARMIN_TITLE_MAX = 5, 50  # the upload form's limits (2026-11-23)
GARMIN_MUST_SAY = {"en": ["not a medical device", "Android", "Garmin Connect", "iPhone is not supported",
                          "not affiliated with Garmin"],
                   "es": ["no es un producto sanitario", "Android", "Garmin Connect", "No funciona con iPhone",
                          "no está afiliada a Garmin"]}
# Checks across the apps a release takes (#262: the shared formats): each is f(root, {app id: version}) → [problem],
# run in preconditions; a problem stops the release. An order between apps (#262: a watch payload change publishes the
# phone only once Connect IQ approved the watch apps) is two runs, the watch apps' first; a check here can refuse the
# second until the first is live.
RELEASE_CHECKS = [release_check.problems]  # tools/formats/release_check.py: every speaker at once, the watch apps first


# --- pure parts (tools/release/test_release.py) ----------------------------------------------------------------------

def plan(apps, current, changes, asked):
    """{id: (next version, [change file names])} for the apps asked ({id: version or None}), in the registry's order.
    ValueError says why an app can't be released: unknown, no change file, a version not above the current one."""
    known = versions.by_id(apps)
    out = {}
    for app_id, v in asked.items():
        if app_id not in known:
            raise ValueError("%s isn't an app of %s (%s)" % (app_id, versions.REGISTRY, ", ".join(known)))
        names = [n for n, _ in versions.pending(changes, app_id)]
        if not names:
            raise ValueError("%s: no change file names it (changes/*.md): nothing to release" % app_id)
        nxt = v or versions.next_version(current[app_id], changes, app_id)
        if parse_version(nxt) <= parse_version(current[app_id]):
            raise ValueError("%s: %s is not above its version %s" % (app_id, nxt, current[app_id]))
        out[app_id] = (nxt, names)
    return {a["id"]: out[a["id"]] for a in apps if a["id"] in out}


def parse_args_apps(words):
    """['android', 'mac=0.18.0'] → {'android': None, 'mac': '0.18.0'}."""
    out = {}
    for w in words:
        app_id, _, v = w.partition("=")
        if v:
            parse_version(v)
        if app_id in out:
            raise ValueError("%s named twice" % app_id)
        out[app_id] = v or None
    return out


LIST_OR_BLOCK = re.compile(r"^\s*([-*+] |\d+\. |#|\||>|```)")


def unwrap(md):
    """Joins hard-wrapped Markdown lines (GitHub release notes keep line breaks): a line continues the one before unless
    it is blank, starts a list item, heading, table, quote or code fence, or is inside a fence."""
    out, fence = [], False
    for line in md.split("\n"):
        if line.lstrip().startswith("```"):
            fence = not fence
            out.append(line)
            continue
        if fence or not line.strip() or LIST_OR_BLOCK.match(line) or not out or not out[-1].strip() \
                or out[-1].lstrip().startswith(("#", "|", "```")):
            out.append(line)
        else:
            out[-1] = out[-1].rstrip() + " " + line.strip()
    return "\n".join(out)


def absolute_links(md, tag_name, base=""):
    """Relative links → the files at the tag (GitHub release notes have no base path); base: the folder they are
    relative to (a CHANGELOG's: apps/android for apps/android/CHANGELOG.md)."""
    return re.sub(r"\]\((?!https?://|#|mailto:)([^)\s]+)\)",
                  lambda m: "](%s/blob/%s/%s)" % (REPO_URL, tag_name, os.path.normpath(os.path.join(base, m.group(1)))),
                  md)


def release_files(app, version, garmin=None):
    """{key: path} of the files the app's GitHub release carries (garmin: its garmin_apps() entry)."""
    v = version
    if app["id"] == "android":
        return {"apk": "%s/Sukrara-%s.apk" % (DIST, v), "mapping": "%s/Sukrara-%s-android-mapping.txt.gz" % (DIST, v)}
    if app["id"] == "mac":
        return {"dmg": "%s/Sukrara-%s.dmg" % (MAC_BUILD, v), "zip": "%s/Sukrara-mac-%s.zip" % (MAC_BUILD, v)}
    if app["id"] == "face-sdk":
        return {"barrel": "%s/SukraraFace-%s.barrel" % (GARMIN_DIST, v)}
    if garmin:
        return {"iq": "%s/%s-%s.iq" % (GARMIN_DIST, garmin["file"], v),
                "sideload": "%s/%s-%s-sideload.zip" % (GARMIN_DIST, garmin["file"], v)}
    raise ValueError("release.py doesn't know the files of %s" % app["id"])


def files_notes(app, version, cert_sha256=None, garmin=None):
    """The release notes' Files section: each file the release carries, what it is."""
    v = version
    if app["id"] == "android":
        lines = ["- `Sukrara-%s.apk`: Android 16+, arm64. Signed with Sukrara's release key, certificate SHA-256 `%s`."
                 % (v, cert_sha256),
                 "- `Sukrara-%s-android-mapping.txt.gz`: the R8 mapping that makes an Android crash trace readable (for "
                 "bug reports; not something to install)." % v]
    elif app["id"] == "mac":
        lines = ["- `Sukrara-%s.dmg` (or `Sukrara-mac-%s.zip`): Mac, Apple Silicon, macOS 15+. Notarized. The GitHub and "
                 "Homebrew Mac app updates itself (Sparkle)." % (v, v)]
    elif app["id"] == "face-sdk":
        lines = ["- `SukraraFace-%s.barrel`: SukraraFace %s, the Monkey Barrel a Connect IQ watch face reads Sukrara's "
                 "glucose with, for developers (0BSD; [docs/GARMIN-FACES.md](%s/blob/%s/docs/GARMIN-FACES.md))."
                 % (v, v, REPO_URL, versions.tag(app, v))]
    else:
        g = garmin
        lines = ["- `%s-%s.iq` and `%s-%s-sideload.zip`: %s, the Garmin %s. The `.iq` is the Connect IQ store package (the "
                 "store installs it, not by hand); the zip has one `%s-<watch>.prg` per watch model, to copy into the "
                 "watch's `GARMIN/APPS` folder over USB without the store." % (g["file"], v, g["file"], v, g["name"],
                                                                               g["kind"], g["file"])]
    return "## Files\n\n" + "\n".join(lines)


def github_notes(app, changelog, version, cert_sha256=None, garmin=None):
    """The app's GitHub release notes: the medical disclaimer, its CHANGELOG entry and its files."""
    entry = versions.changelog_entry(changelog, version)
    if not entry:
        raise ValueError("%s has no %s entry" % (app["changelog"], version))
    t = versions.tag(app, version)
    head = ("**Not a medical device.** Every behaviour, with its rules and numbers: [docs/FEATURES.md](%s/blob/%s/"
            "docs/FEATURES.md)." % (REPO_URL, t))
    body = absolute_links(unwrap(entry[1]), t, os.path.dirname(app["changelog"])).strip()
    return head + "\n\n" + body + "\n\n" + files_notes(app, version, cert_sha256, garmin) + "\n"


def read_play_notes(root, version):
    """{language: text} from the Fastlane changelogs of the version code (Play's limit: 500 characters each)."""
    notes, code = {}, version_code(version)
    for lang in PLAY_LANGS:
        path = os.path.join(root, PLAY_NOTES % (lang, code))
        if not os.path.exists(path):
            raise ValueError("no Play notes %s (what's new for Play, %s, at most %d characters)"
                             % (os.path.relpath(path, root), lang, stores.PLAY_NOTES_MAX))
        with open(path, encoding="utf-8") as f:
            notes[lang] = f.read().strip()
    stores.play_track_body("internal", version, code, notes)  # validates length and emptiness
    return notes


def read_appstore_notes(root, version):
    """{locale: text}: the App Store's "What's New" for the version code (at most 4000 characters each)."""
    notes, code = {}, version_code(version)
    for lang in ASC_LANGS:
        path = os.path.join(root, ASC_NOTES % (lang, code))
        if not os.path.exists(path):
            raise ValueError("no App Store notes %s (what's new on the Mac, %s, at most %d characters)"
                             % (os.path.relpath(path, root), lang, stores.ASC_WHATS_NEW_MAX))
        with open(path, encoding="utf-8") as f:
            text = f.read().strip()
        if not text or len(text) > stores.ASC_WHATS_NEW_MAX:
            raise ValueError("App Store notes %s: %d characters (1 to %d)"
                             % (os.path.relpath(path, root), len(text), stores.ASC_WHATS_NEW_MAX))
        notes[lang] = text
    return notes


def appcast_item(feed, version):
    """{length, signature, url} of the version's item in website/public/appcast.xml, or None."""
    m = re.search(r"<item>\s*<title>Sukrara %s</title>(.*?)</item>" % re.escape(version), feed, re.S)
    if not m:
        return None
    e = re.search(r'<enclosure url="([^"]+)" length="(\d+)"[^>]*?sparkle:edSignature="([^"]+)"', m.group(1), re.S)
    return {"url": e.group(1), "length": int(e.group(2)), "signature": e.group(3)} if e else None


def garmin_apps(registry):
    """Sukrara's Garmin apps from the payload registry (contracts/watchface/payload/apps.json, its text), by its
    'app': {app: {app, name, kind, file (garmin-release.sh's <Name>), manifest}}."""
    out = {}
    for a in json.loads(registry)["apps"]:
        if a["app"] not in GARMIN_FILES:
            raise ValueError("apps.json: release.py doesn't know the Garmin app %r (GARMIN_FILES)" % a["app"])
        out[a["app"]] = {"app": a["app"], "name": a["name"], "kind": GARMIN_KINDS[a["type"]],
                         "file": GARMIN_FILES[a["app"]], "manifest": a["manifest"]}
    return out


def png_size(data):
    """(width, height) of a PNG's bytes, or None."""
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def jpeg_size(data):
    """(width, height) of a JPEG's bytes (its first SOFn segment), or None."""
    if data[:2] != b"\xff\xd8":
        return None
    p = 2
    while p + 9 < len(data) and data[p] == 0xFF:
        kind, n = data[p + 1], int.from_bytes(data[p + 2:p + 4], "big")
        if kind in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            return int.from_bytes(data[p + 7:p + 9], "big"), int.from_bytes(data[p + 5:p + 7], "big")
        p += 2 + n
    return None


def listing_files(app, listing):
    """The listing's images, apps/garmin/<app>/store/listing.json's: [(what, path, (w, h) or None, max bytes)]. The
    icon and the heroes are PNG; the screenshots JPEG (render.mjs: the sharpest within 150 KB)."""
    d = listing["images"]
    out = [("icon", d + "/icon-500.png", (500, 500), GARMIN_ICON_MAX)]
    out += [("hero " + lang, "%s/hero-%s.png" % (d, lang), (1440, 720), GARMIN_HERO_MAX) for lang in GARMIN_LANGS]
    out += [("screenshot " + s, "%s/screen-%s.jpg" % (d, s), None, GARMIN_SCREEN_MAX) for s in listing["screenshots"]]
    return out


def listing_problems(root, app):
    """What keeps an app's Connect IQ listing (apps/garmin/<app>/store) from the store's rules (docs/20-publishing.md ›
    Connect IQ store: what it asks and checks): every field, each language's description in plain text within 4000
    (counted in UTF-8 bytes: the store's count is undocumented), the not-a-medical-device paragraph, the requirements,
    no Garmin in the title, and every image at its size and weight."""
    base = os.path.join(root, "apps/garmin", app, "store")
    try:
        with open(os.path.join(base, "listing.json"), encoding="utf-8") as f:
            listing = json.load(f)
    except (OSError, ValueError) as e:
        return ["%s: no listing (%s)" % (app, e)]
    problems = []
    for key in ("title", "tagline", "category", "images", "screenshots", "privacy", "support", "price"):
        if not listing.get(key):
            problems.append("%s: listing.json has no %s" % (app, key))
    if problems:
        return problems
    if len(listing["screenshots"]) > GARMIN_SCREENSHOTS_MAX:
        problems.append("%s: %d screenshots (the store takes at most %d)" % (app, len(listing["screenshots"]),
                                                                            GARMIN_SCREENSHOTS_MAX))
    for lang in GARMIN_LANGS:
        if "garmin" in listing["title"].get(lang, "garmin").lower():
            problems.append("%s: the %s title is missing or names Garmin" % (app, lang))
        if len(listing["title"].get(lang, "")) > GARMIN_TITLE_MAX:
            problems.append("%s: the %s title is over %d characters" % (app, lang, GARMIN_TITLE_MAX))
        path = os.path.join(base, "description-%s.txt" % lang)
        if not os.path.exists(path):
            problems.append("%s: no %s" % (app, os.path.relpath(path, root)))
            continue
        with open(path, encoding="utf-8") as f:
            text = f.read()
        n = len(text.strip().encode("utf-8"))
        if n > GARMIN_DESCRIPTION_MAX:
            problems.append("%s: description-%s.txt is %d bytes (at most %d)" % (app, lang, n, GARMIN_DESCRIPTION_MAX))
        for must in GARMIN_MUST_SAY[lang] + [listing["privacy"][lang]]:
            if must not in text:
                problems.append("%s: description-%s.txt doesn't say %r" % (app, lang, must))
        if re.search(r"^\s*(#|[-*] |\d+\. )|\*\*|\]\(", text, re.M):
            problems.append("%s: description-%s.txt has Markdown (the store shows plain text)" % (app, lang))
    for what, rel, size, limit in listing_files(app, listing):
        try:
            with open(os.path.join(root, rel), "rb") as f:
                data = f.read()
        except OSError:
            problems.append("%s: no %s (%s; node assets/brand/sukrara/connectiq/render.mjs)" % (app, what, rel))
            continue
        jpg = rel.endswith(".jpg")
        got = jpeg_size(data) if jpg else png_size(data)
        if got is None or (size and got != size):
            problems.append("%s: %s isn't a %s%s (%s)" % (app, rel, "%d × %d " % size if size else "", "JPEG" if jpg else "PNG",
                                                          got))
        if len(data) > limit:
            problems.append("%s: %s is %d KB (at most %d)" % (app, rel, len(data) // 1024, limit // 1024))
    return problems


def garmin_upload_kit(version, garmin, iq, changelog):
    """What the owner does at the Connect IQ developer dashboard (no upload API) for one app: the lines release.py
    prints. Every Garmin app has its listing (its first upload is by hand), so: Upload a new version with the What's new
    (its CHANGELOG entry)."""
    entry = versions.changelog_entry(changelog, version)
    notes = unwrap(entry[1]).strip() if entry else "(no CHANGELOG entry for %s: say what changed)" % version
    return "\n".join([
        "%s (%s), Connect IQ store, by hand at %s (sign in as the developer account):" % (
            garmin["name"], garmin["kind"], GARMIN_DASHBOARD),
        "  1. %s → Upload a new version" % garmin["name"],
        "  2. File: %s" % iq,
        "  3. App Version: %s" % version,
        "  4. What's new (its CHANGELOG entry):",
    ] + ["       " + line for line in notes.splitlines()] + [
        "  5. Submit. Garmin reviews it; the new version stays off the store until it is approved (e-mail).",
    ])


def appstore_pkg(version):
    """The App Store pkg mac-appstore.sh builds and validates (the gate) and uploads with --upload (testflight)."""
    return "%s/Sukrara-%s-appstore.pkg" % (MAC_BUILD, version)


def pkg_build(distribution, version):
    """The build number (CFBundleVersion) of the app in an App Store pkg, from the pkg's Distribution file, once it is
    the version's: CFBundleShortVersionString = version, CFBundleVersion = <versionCode>.<yyMMdd>.<HHmm>
    (bundle.sh --app-store). Raises ValueError when it isn't."""
    bundles = [b for b in ET.fromstring(distribution).iter("bundle") if b.get("id") == stores.BUNDLE_ID]
    if not bundles:
        raise ValueError("the App Store pkg has no %s app" % stores.BUNDLE_ID)
    short, build = bundles[0].get("CFBundleShortVersionString"), bundles[0].get("CFBundleVersion") or ""
    if short != version:
        raise ValueError("the App Store pkg is version %s, not %s" % (short, version))
    if not re.fullmatch(r"%d\.\d{6}\.\d{4}" % version_code(version), build):
        raise ValueError("the App Store pkg's build number %s isn't %d.<yyMMdd>.<HHmm>" % (build, version_code(version)))
    return build


def start_problems(head, origin_next, dirty):
    """Why this checkout can't start a release ([] when it can): the tree must be clean and HEAD must be origin/next.
    The branch doesn't matter (a worktree's own branch, a detached HEAD): every push names its refspec."""
    out = []
    if dirty:
        out.append("the tree isn't clean: %s" % ", ".join(dirty))
    if head != origin_next:
        out.append("HEAD (%s) isn't origin/next (%s)" % (head[:8], origin_next[:8]))
    return out


def resume_problems(dirty):
    """Why --from a later step can't go on ([] when it can): changes besides website/public/appcast.xml, which the gate
    writes and the sparkle step commits. The branch doesn't matter."""
    extra = [f for f in dirty if f != APPCAST]
    return ["the tree has changes besides %s: %s" % (APPCAST, ", ".join(extra))] if extra else []


def commit_runs(runs):
    """The runs that test the commit itself: its push, or one started by hand (workflow_dispatch, e.g. when the push run
    is stuck in a GitHub Actions incident). A pull_request run tests a merge with the base, not the commit."""
    return [r for r in runs if r.get("event") in ("push", "workflow_dispatch")]


def pick_run(runs):
    """The state of a commit's workflow runs (gh run list --json status,conclusion): 'success' when one succeeded,
    'pending' while one runs (or none started yet), else 'failure'."""
    if any(r.get("conclusion") == "success" for r in runs):
        return "success"
    if not runs or any(r.get("status") != "completed" for r in runs):
        return "pending"
    return "failure"


# --- running ---------------------------------------------------------------------------------------------------------

class Stop(Exception):
    pass


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


class Release:
    def __init__(self, releases, dry, today=None, apps=None):
        """releases: {app id: version}, in the registry's order; apps: the registry's (default: read from ROOT)."""
        self.apps = versions.by_id(apps or versions.load_apps(self.read(versions.REGISTRY)))
        self.v = dict(releases)
        self.dry = dry
        self.today = today or datetime.date.today()
        self.changelogs = {}  # dry run: the CHANGELOGs the version step would write
        self.notes_state = []  # what the summary reports as still manual
        self.would_stop = []  # dry run: every check that would have stopped the release

    def has(self, app_id):
        return app_id in self.v

    def name(self, app_id):
        return "%s %s" % (self.apps[app_id]["name"], self.v[app_id])

    def tag(self, app_id):
        return versions.tag(self.apps[app_id], self.v[app_id])

    def garmin_ids(self):
        return [i for i in self.v if self.apps[i].get("garmin")]

    def garmin(self, app_id):
        """The app's entry of the payload registry (name, kind, file), or None for a non-Garmin app."""
        g = self.apps[app_id].get("garmin")
        return garmin_apps(self.read(GARMIN_REGISTRY))[g] if g else None

    def files(self, app_id):
        return release_files(self.apps[app_id], self.v[app_id], self.garmin(app_id))

    # shell
    def sh(self, cmd, mutate=True, capture=True, cwd=None, check=True, env=None):
        shown = " ".join(cmd)
        if self.dry and mutate:
            print("  would run: %s" % shown)
            return ""
        if not capture:
            print("  $ %s" % shown, flush=True)
        r = subprocess.run(cmd, cwd=cwd or ROOT, capture_output=capture, text=True, env=env)
        if check and r.returncode != 0:
            raise Stop("%s failed (exit %d)%s" % (shown, r.returncode,
                                                  (": " + (r.stderr or r.stdout).strip()[-800:]) if capture else ""))
        return (r.stdout or "").strip() if capture else ""

    def ok(self, cond, msg):
        if cond:
            return True
        if self.dry:
            print("  WOULD STOP: %s" % msg)
            self.would_stop.append(msg)
            return False
        raise Stop(msg)

    def wait(self, what, probe, timeout_s, every_s=30):
        """Polls probe() → 'success' | 'pending' | 'failure: …' until it isn't pending."""
        if self.dry:
            print("  would wait for %s" % what)
            return
        print("  waiting for %s" % what, flush=True)
        deadline = time.time() + timeout_s
        while True:
            state = probe()
            if state == "success":
                print("  ok: %s" % what)
                return
            if state != "pending":
                raise Stop("%s: %s" % (what, state))
            if time.time() > deadline:
                raise Stop("%s: still pending after %d min" % (what, timeout_s // 60))
            time.sleep(every_s)

    def gh_json(self, args):
        out = self.sh(["gh"] + args, mutate=False)
        return json.loads(out) if out else None

    def checks_state(self, sha, branch=None, workflow="checks.yml"):
        """The state of `workflow`'s runs on commit `sha`: on any branch (a commit is the same tree wherever it was
        pushed), or on `branch` only (Pages deploys from main)."""
        where = ["--branch", branch] if branch else []
        runs = commit_runs(self.gh_json(["run", "list", "-R", REPO, "--workflow", workflow, "--commit", sha] + where
                                        + ["--json", "event,status,conclusion,url", "-L", "20"]) or [])
        state = pick_run(runs)  # one green run is enough: a stuck or failed-to-start one beside it doesn't count
        if state == "failure":
            return "failure (%s)" % ", ".join(r.get("url", "") for r in runs)
        return state

    def git_ok(self, args):
        return subprocess.run(["git"] + args, cwd=ROOT, capture_output=True).returncode == 0

    def remote_tag(self, tag_name):
        """The commit the tag on origin points at, or ''."""
        out = self.sh(["git", "ls-remote", "--tags", "origin", "refs/tags/%s*" % tag_name], mutate=False)
        refs = dict(reversed(line.split("\t")) for line in out.splitlines() if "\t" in line)
        ref = "refs/tags/" + tag_name
        return refs.get(ref + "^{}") or refs.get(ref, "")  # an annotated tag's commit is the peeled ^{} line

    def resume_check(self):
        """--from a later step: a commit with the release's versions checked out (the release commit, or a later one:
        next after other apps' releases), each app still built from its release commit's files (versions.built_from:
        a change since is the next version, released anew), nothing changed besides website/public/appcast.xml."""
        for problem in resume_problems(self.dirty()):
            self.ok(False, problem)
        for i in self.v:
            app = self.apps[i]
            base = self.sh(["git", "log", "-1", "--format=%H", "--", app["version"]], mutate=False)
            changed = self.sh(["git", "diff", "--name-only", base, "--"] + versions.built_from(app), mutate=False)
            self.ok(not changed, "%s changed since its release commit %s (%s): that's its next version, released with "
                    "a change file, not --from" % (app["name"], base[:8], ", ".join(changed.splitlines()[:3])))

    def push_next(self):
        """Pushes HEAD to next. If next moved meanwhile (other work landed), merges HEAD into it instead, so main stays
        an ancestor of next (the merge brings only the release's own commits in). HEAD ends on the release's own commit
        either way, never on the merge: a re-run's HEAD:main must not carry next's unreleased work to main."""
        if self.dry:
            print("  would run: git push origin HEAD:next (if next moved: merge HEAD into origin/next, then push)")
            return
        if self.git_ok(["push", "origin", "HEAD:next"]):
            return
        mine = self.head()
        self.sh(["git", "fetch", "--quiet", "origin"])
        if self.git_ok(["merge-base", "--is-ancestor", mine, "origin/next"]):
            return  # next has it already (a re-run)
        self.sh(["git", "reset", "--keep", "origin/next"])
        merged = self.git_ok(["merge", "--no-ff", "-m", "Merge %s (%s, on main) into next" % (
            mine[:8], ", ".join(self.name(i) for i in self.v)), mine])  # git's default would name this checkout's branch
        if not merged:
            self.git_ok(["merge", "--abort"])
        pushed = merged and self.git_ok(["push", "origin", "HEAD:next"])
        self.sh(["git", "reset", "--keep", mine])
        self.ok(merged, "merging %s into next failed (a conflict or a hook): merge it by hand, then --from sparkle"
                % mine[:8])
        self.ok(pushed, "pushing the merge of %s into next failed: --from sparkle again" % mine[:8])

    def head(self):
        return self.sh(["git", "rev-parse", "HEAD"], mutate=False)

    def path(self, rel):
        return os.path.join(ROOT, rel)

    def read(self, rel):
        with open(self.path(rel), encoding="utf-8") as f:
            return f.read()

    def dirty(self):
        out = subprocess.run(["git", "status", "--porcelain", "-z"], cwd=ROOT, capture_output=True, text=True,
                             check=True).stdout  # not stripped: " M README.md" starts with a space
        return [e[3:] for e in out.split("\0") if len(e) > 3]

    def current_version(self, app_id):
        return versions.read_version(self.apps[app_id], self.read(self.apps[app_id]["version"]))

    def changelog(self, app_id):
        return self.changelogs.get(app_id) or self.read(self.apps[app_id]["changelog"])

    def skip(self, step, needs):
        """True (and said) when no app this step serves is released."""
        if any(self.has(i) for i in needs):
            return False
        print("  nothing for %s: %s isn't released" % (step, " or ".join(needs)))
        return True

    # steps
    def preconditions(self):
        self.sh(["git", "fetch", "--quiet", "--tags", "origin"], mutate=False)
        sha = self.head()
        for problem in start_problems(sha, self.sh(["git", "rev-parse", "origin/next"], mutate=False), self.dirty()):
            self.ok(False, problem)
        self.ok(self.git_ok(["merge-base", "--is-ancestor", "origin/main", "HEAD"]),
                "origin/main has commits next doesn't: main can't be fast-forwarded")
        self.ok(os.path.exists(self.path("tools/check/strict")), "tools/check/strict is missing")
        for i in self.v:
            self.ok(not self.sh(["git", "ls-remote", "--tags", "origin", self.tag(i)], mutate=False),
                    "tag %s exists on origin" % self.tag(i))
        if self.has("android"):
            try:
                read_play_notes(ROOT, self.v["android"])
            except ValueError as e:
                self.ok(False, str(e))
            for f, what in [("~/.android/sukrara-release.jks", "the Android release key"),
                            ("~/.android/sukrara-play.json", "the Play service account key")]:
                self.ok(os.path.exists(os.path.expanduser(f)), "no %s (%s)" % (what, f))
        if self.has("mac"):
            try:
                read_appstore_notes(ROOT, self.v["mac"])
            except ValueError as e:
                self.ok(False, str(e))
            key, issuer = stores.asc_key_paths()
            self.ok(bool(key and issuer), "no App Store Connect API key / issuer (~/.apple-dev)")
        if self.garmin_ids():
            try:
                self.sh(["tools/release/garmin-release.sh", "--key-check"], mutate=False)
            except Stop as e:
                self.ok(False, str(e))
            for i in self.garmin_ids():
                for problem in listing_problems(ROOT, self.apps[i]["garmin"]):
                    self.ok(False, "Connect IQ listing: " + problem)
        for check in RELEASE_CHECKS:
            for problem in check(ROOT, dict(self.v)):
                self.ok(False, problem)
        self.sh(["gh", "auth", "status"], mutate=False)
        # Not awaited here: the version commit's run (awaited in github) covers every change since the newest green
        # commit (tools/ci/changes.py), this one's included. Only a run that already failed stops the release.
        state = self.checks_state(sha)
        self.ok(state in ("success", "pending"), "the Checks run on %s is %s" % (sha[:8], state))
        if state == "pending":
            print("  Checks on %s still running: the version commit's run covers it" % sha[:8])

    def change_files(self):
        """{name: parsed} of every change file (a broken one stops: check `changes` names it)."""
        out = {}
        d = self.path(versions.CHANGES)
        for f in sorted(os.listdir(d)) if os.path.isdir(d) else []:
            rel = versions.CHANGES + f
            if versions.is_change_file(rel):
                try:
                    out[rel] = versions.parse_change(self.read(rel), list(self.apps))
                except ValueError as e:
                    raise Stop("%s: %s" % (rel, e))
        return out

    def version(self):
        changes = self.change_files()
        files = {}
        for i, v in self.v.items():
            app = self.apps[i]
            folder = os.path.dirname(app["changelog"])  # a change file's links are from the root: rebased for it
            bullets = [versions.rebase_links(c["body"], folder) for _, c in versions.pending(changes, i)]
            files[app["version"]] = versions.with_version(app, self.read(app["version"]), v)
            files[app["changelog"]] = versions.changelog_with_entry(self.read(app["changelog"]), v, self.today, bullets)
            self.changelogs[i] = files[app["changelog"]]
            for m in app.get("mentions", []):
                text = files.get(m["file"]) or self.read(m["file"])
                files[m["file"]] = versions.with_mentions(text, m["pattern"], app, v)
        gone = []
        for rel, text in versions.consumed(changes, list(self.v)).items():
            if text is None:
                gone.append(rel)
            else:
                files[rel] = text
        if self.dry:
            for rel, text in files.items():
                if text != self.read(rel):
                    print("  would write %s" % rel)
            for rel in gone:
                print("  would delete %s" % rel)
            for i in self.v:
                print("  %s's entry: %s" % (i, versions.changelog_entry(self.changelogs[i], self.v[i])[0]))
        else:
            for rel, text in files.items():
                with open(self.path(rel), "w", encoding="utf-8") as f:
                    f.write(text)
            for rel in gone:
                os.unlink(self.path(rel))
        self.sh(["git", "add", "-A", "--"] + list(files) + gone)
        self.sh(["git", "commit", "-q", "-m", "Release %s: versions and CHANGELOG entries" % ", ".join(
            self.name(i) for i in self.v), "-m",
            "Features-skip: version bump only; every behaviour in it is already in docs/FEATURES.md", "-m",
            "Changes-skip: the release consumes the change files"])
        self.sh(["git", "push", "origin", "HEAD:next"])  # fast-forward only: git refuses anything else
        print("  its Checks run is awaited before anything is published (github), the gate runs meanwhile")

    def gate(self):
        r = "tools/release/"
        if self.has("android"):
            self.sh([r + "android-release.sh"], capture=False)
            self.sh([r + "android-smoke.sh", "--final", "--no-build"], capture=False)
            self.sh([r + "play-bundle.sh"], capture=False)
        if self.has("mac"):
            self.sh([r + "mac-release.sh"], capture=False)
            self.sh(["apps/macos/Bundle/smoke.sh"], capture=False)
            # rebuilds build/Sukrara.app as the App Store version; its validated pkg is the one testflight uploads
            self.sh([r + "mac-appstore.sh"], capture=False)
        for i in self.garmin_ids():
            self.sh([r + "garmin-release.sh", self.apps[i]["garmin"], self.v[i]], capture=False)
        if self.has("face-sdk"):  # a barrel that doesn't build stops the release
            self.sh([r + "garmin-release.sh", "sdk"], capture=False)
        if self.dry:
            print("  would check the files it leaves%s, and that nothing else changed"
                  % (", website/public/appcast.xml's %s item" % self.v["mac"] if self.has("mac") else ""))
            return
        left = [p for i in self.v for p in self.files(i).values()]
        if self.has("android"):
            left.append("%s/Sukrara-%s-play.aab" % (DIST, self.v["android"]))
        if self.has("mac"):
            left.append(appstore_pkg(self.v["mac"]))
            self.ok(appcast_item(self.read(APPCAST), self.v["mac"]),
                    "%s has no item for %s (was the build notarized?)" % (APPCAST, self.v["mac"]))
        for rel in left:
            self.ok(os.path.exists(self.path(rel)), "the gate left no %s" % rel)
        extra = [f for f in self.dirty() if f != APPCAST]
        self.ok(not extra, "the gate changed files it shouldn't: %s" % ", ".join(extra))

    def check_dmg_is_the_signed_one(self):
        if self.dry:
            print("  would check the dmg is the one %s signs (length, sign_update --verify)" % APPCAST)
            return
        v = self.v["mac"]
        item = appcast_item(self.read(APPCAST), v)
        dmg = self.path(self.files("mac")["dmg"])
        if not self.ok(item is not None, "%s has no item for %s: run the gate (mac-release.sh)" % (APPCAST, v)):
            return
        if not self.ok(os.path.exists(dmg), "no %s" % dmg):
            return
        self.ok(item["length"] == os.path.getsize(dmg), "the appcast's length isn't the dmg's: rebuilt since?")
        sign_update = self.path("apps/macos/.build/artifacts/sparkle/Sparkle/bin/sign_update")
        self.sh([sign_update, "--verify", dmg, item["signature"]], mutate=False)

    def github(self):
        sha = self.head()
        for i in self.v:
            self.ok(self.current_version(i) == self.v[i] or (self.dry and i in self.changelogs),
                    "%s's version isn't %s: check out a commit with the release's versions" % (i, self.v[i]))
        self.wait("Checks on %s" % sha[:8], lambda: self.checks_state(sha), 150 * 60)
        if self.has("mac"):
            self.check_dmg_is_the_signed_one()
        for i in self.v:
            for rel in self.files(i).values():
                self.ok(self.dry or os.path.exists(self.path(rel)), "no %s: run the gate" % rel)
        # main: fast-forward to the release commit, once for every app
        self.sh(["git", "fetch", "--quiet", "--tags", "origin"], mutate=False)
        if self.sh(["git", "rev-parse", "origin/main"], mutate=False) != sha:
            self.sh(["git", "push", "origin", "HEAD:main"])
        cert = self.read("tools/release/android-cert.sha256").strip()
        for i in self.v:
            self.github_release(i, sha, cert)
        # No second wait on main: main now points at the commit whose run is green above, and that run is what main's
        # own run finds (tools/ci/changes.py: an already green commit needs nothing again).

    def github_release(self, i, sha, cert):
        """The app's tag on the release commit and its GitHub release with its files (Latest only for the 'latest'
        app: GitHub keeps one Latest per repository, and Obtainium / IzzyOnDroid take Android's APK from it)."""
        t, app = self.tag(i), self.apps[i]
        remote = self.remote_tag(t)
        if remote:
            self.ok(remote == sha, "tag %s on origin isn't %s" % (t, sha[:8]))
        else:
            if not self.sh(["git", "tag", "-l", t], mutate=False):
                self.sh(["git", "tag", "-a", t, "-m", self.name(i), sha])
            self.sh(["git", "push", "origin", "refs/tags/" + t])
        files = self.files(i)
        exists = subprocess.run(["gh", "release", "view", t, "-R", REPO], capture_output=True).returncode == 0
        if not exists:
            notes = github_notes(app, self.changelog(i), self.v[i], cert, self.garmin(i))
            if self.dry:
                print("  %s's notes:\n%s" % (t, "\n".join("    " + line for line in notes.splitlines())))
            with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False) as f:
                f.write(notes)
            latest = "--latest" if app.get("latest") else "--latest=false"
            self.sh(["gh", "release", "create", t, "-R", REPO, "--verify-tag", "--title", self.name(i),
                     "--notes-file", f.name, latest] + [self.path(p) for p in files.values()])
            os.unlink(f.name)
        if not self.dry:
            assets = {a["name"]: a for a in self.gh_json(["release", "view", t, "-R", REPO, "--json", "assets"])["assets"]}
            for rel in files.values():
                name, want = os.path.basename(rel), "sha256:" + sha256(self.path(rel))
                if name not in assets:
                    self.sh(["gh", "release", "upload", t, "-R", REPO, self.path(rel)])
                elif assets[name].get("digest") and assets[name]["digest"] != want:
                    raise Stop("%s's %s isn't the local file (%s)" % (t, name, assets[name]["digest"]))

    def sparkle(self):
        if self.skip("sparkle", ["mac"]):
            return
        v = self.v["mac"]
        item = appcast_item(self.read(APPCAST), v)
        if self.dry and item is None:
            print("  would check %s has the %s item (mac-release.sh writes it in the gate) and the release's dmg "
                  "matches it" % (APPCAST, v))
            print("  would run: git commit %s; git push origin HEAD:main" % APPCAST)
            self.push_next()
            print("  would wait for GitHub Pages and %s to serve %s" % (FEED_URL, v))
            return
        self.ok(item is not None, "%s has no item for %s" % (APPCAST, v))
        committed = APPCAST not in self.dirty()
        if not committed:
            # the download the item names must be there first (#91)
            asset = self.gh_json(["release", "view", self.tag("mac"), "-R", REPO, "--json", "assets"]) if not self.dry \
                else None
            if asset is not None:
                dig = {a["name"]: a.get("digest") for a in asset["assets"]}.get(os.path.basename(item["url"]))
                self.ok(dig == "sha256:" + sha256(self.path(self.files("mac")["dmg"])),
                        "the release has no dmg matching the appcast (%s)" % dig)
            self.sh(["git", "add", APPCAST])
            self.sh(["git", "commit", "-q", "-m", "Sparkle feed: %s" % self.name("mac"), "-m",
                     "The GitHub release %s with its dmg is published (#91)." % self.tag("mac"), "-m",
                     "Features-skip: the update feed only"])
        sha = self.head()
        self.sh(["git", "push", "origin", "HEAD:main"])  # fast-forward: main is the release commit
        self.push_next()
        self.wait("GitHub Pages (website.yml on %s)" % sha[:8],
                  lambda: self.checks_state(sha, "main", "website.yml"), 30 * 60)

        def served():
            try:
                with urllib.request.urlopen("%s?r=%d" % (FEED_URL, time.time()), timeout=30) as r:
                    body = r.read().decode()
            except OSError:
                return "pending"
            return "success" if appcast_item(body, v) == item else "pending"
        self.wait("%s to serve %s" % (FEED_URL, v), served, 20 * 60)

    def homebrew(self):
        if self.skip("homebrew", ["mac"]):
            return
        v = self.v["mac"]
        dmg = self.path(self.files("mac")["dmg"])

        def cask():
            out = self.sh(["gh", "api", "repos/%s/contents/Casks/sukrara.rb" % TAP, "-H",
                           "Accept: application/vnd.github.raw"], mutate=False)
            ver = re.search(r'^  version "([^"]+)"', out, re.M)
            sha = re.search(r'^  sha256 "([^"]+)"', out, re.M)
            if not ver or ver.group(1) != v:
                runs = self.gh_json(["run", "list", "-R", REPO, "--workflow", "homebrew-tap.yml", "--event", "release",
                                     "--json", "displayTitle,status,conclusion,url", "-L", "10"]) or []
                mine = [r for r in runs if r.get("displayTitle") == self.name("mac")]
                if mine and pick_run(mine) == "failure":
                    return "failure: the tap workflow failed (%s)" % mine[0].get("url")
                return "pending"
            if not sha or sha.group(1) != sha256(dmg):
                return "failure: the cask's sha256 isn't the dmg's"
            if "auto_updates true" not in out:
                return "failure: the cask lacks auto_updates true (#91)"
            return "success"
        self.wait("the Homebrew cask at %s (%s)" % (v, TAP), cask, 20 * 60)

    def play(self):
        """The play bundle on the internal and the production track (every release goes to production: 0.x is public
        work in progress, the owner's 2026-11-13 decision). Managed publishing is off, so production goes live once
        Google approves it. A track that already has the version code is left alone (re-running is safe)."""
        if self.skip("play", ["android"]):
            return
        v = self.v["android"]
        code = version_code(v)
        aab = self.path("%s/Sukrara-%s-play.aab" % (DIST, v))
        try:
            notes = read_play_notes(ROOT, v)
        except ValueError as e:
            self.ok(False, str(e))
            return
        bodies = [stores.play_track_body(t, v, code, notes) for t in PLAY_TRACKS]
        if self.dry:
            print("  would upload %s (versionCode %d) to the %s tracks:"
                  % (os.path.relpath(aab, ROOT), code, " and ".join(PLAY_TRACKS)))
            print("    edits.insert → bundles.upload")
            for body in bodies:
                print("    tracks.update " + json.dumps(body, ensure_ascii=False))
            print("    → edits.commit (production: sent for review, live on approval)")
            return
        self.ok(os.path.exists(aab), "no %s: run the gate (play-bundle.sh)" % aab)
        play = stores.Play()
        edit = play.insert_edit()
        try:
            todo = [b for b in bodies if not stores.track_has(play.track(edit, b["track"]), code)]
            for b in bodies:
                if b not in todo:
                    print("  the %s track already has %d" % (b["track"], code))
            if not todo:
                play.delete_edit(edit)
                return
            if code not in [int(b["versionCode"]) for b in play.bundles(edit)]:
                up = play.upload_bundle(edit, aab)
                self.ok(int(up["versionCode"]) == code, "Play read versionCode %s" % up["versionCode"])
                self.ok(up.get("sha256") == sha256(aab), "Play's sha256 of the bundle isn't the local one")
                print("  uploaded versionCode %d" % code)
            for b in todo:
                play.update_track(edit, b)
            try:
                play.commit(edit)
            except stores.ApiError as e:
                if "changesNotSentForReview" not in e.message:
                    raise
                play.commit(edit, not_for_review=True)
                self.notes_state.append("Play: the release waits in Play Console › Publishing overview (the API "
                                        "couldn't send it for review): Send changes for review there")
        except BaseException:
            try:
                play.delete_edit(edit)
            except Exception:
                pass
            raise
        check = play.insert_edit()
        try:
            rels = {t: stores.track_has(play.track(check, t), code) for t in PLAY_TRACKS}
        finally:
            play.delete_edit(check)
        for t, rel in rels.items():
            self.ok(rel is not None, "the %s track doesn't list %d after the commit" % (t, code))
            print("  %s track: %s %d %s" % (t, rel["name"], code, rel["status"]))

    def pkg_distribution(self, pkg):
        """The Distribution file of a productbuild pkg (pkgutil --expand into a temporary directory)."""
        tmp = tempfile.mkdtemp()
        try:
            self.sh(["pkgutil", "--expand", pkg, os.path.join(tmp, "pkg")], mutate=False)
            with open(os.path.join(tmp, "pkg", "Distribution"), encoding="utf-8") as f:
                return f.read()
        finally:
            shutil.rmtree(tmp)

    def appstore_build(self):
        """The build number of the App Store pkg the gate built and validated, its version and build number checked.
        Without the pkg (--from testflight in a fresh checkout), mac-appstore.sh builds and validates it, once."""
        v = self.v["mac"]
        pkg = self.path(appstore_pkg(v))
        if not os.path.exists(pkg):
            print("  no %s (the gate builds it): building it" % appstore_pkg(v))
            self.sh(["tools/release/mac-appstore.sh"], capture=False)
        return pkg_build(self.pkg_distribution(pkg), v)

    def testflight(self):
        if self.skip("testflight", ["mac"]):
            return
        v = self.v["mac"]
        pkg = appstore_pkg(v)
        if self.dry:
            print("  would look for a %s build in App Store Connect, else upload %s" % (v, pkg))
            if os.path.exists(self.path(pkg)):
                try:
                    print("  %s is build %s" % (pkg, pkg_build(self.pkg_distribution(self.path(pkg)), v)))
                except ValueError as e:
                    self.ok(False, str(e))
            else:
                print("  no pkg yet (the gate builds it); if the gate left none, would run: tools/release/mac-appstore.sh")
            print("  would check its version and build number, then run: tools/release/mac-appstore.sh --upload")
            print("  would wait until the build's processingState is VALID")
            return
        asc = stores.AppStoreConnect()
        app = asc.app_id()
        live = [b for b in asc.builds(app, version=v) if b["state"] in ("PROCESSING", "VALID") and not b["expired"]]
        if live:
            build = live[0]["build"]
            print("  App Store Connect has %s (%s) already: not uploaded again" % (build, live[0]["state"]))
        else:
            build = self.appstore_build()
            self.ok(not asc.builds(app, build=build, limit=1),
                    "App Store Connect has build %s already: delete %s and run --from testflight again (it builds a new "
                    "one)" % (build, pkg))
            self.sh(["tools/release/mac-appstore.sh", "--upload"], capture=False)
        found = {}

        def processed():
            b = asc.builds(app, build=build, limit=1)
            if not b:
                return "pending"  # an upload shows up in the API after a few minutes
            found.update(b[0])
            if b[0]["state"] == "VALID":
                return "success"
            return "pending" if b[0]["state"] == "PROCESSING" else "failure: %s" % b[0]["state"]
        self.wait("App Store Connect to process %s" % build, processed, 90 * 60, every_s=60)
        if found.get("encryption") is None:
            self.notes_state.append("TestFlight: build %s shows Missing Compliance: App Store Connect › TestFlight › "
                                    "the build › Manage (docs/20-publishing.md › Mac App Store: signing)" % build)

    def appstore(self):
        """The App Store version of this release, with its TestFlight build and "What's New", submitted for App Review
        and released automatically once approved (like Play production). Apple reviews one version at a time: while
        another is in review the release stays on TestFlight, and a later release submits the newest."""
        if self.skip("appstore", ["mac"]):
            return
        v = self.v["mac"]
        try:
            notes = read_appstore_notes(ROOT, v)
        except ValueError as e:
            self.ok(False, str(e))
            return
        if self.dry:
            print("  would find the App Store versions, then create or reuse %s (release after approval), attach its "
                  "VALID build, set What's New (%s) and submit it for App Review; nothing while another version is "
                  "in review" % (v, ", ".join(sorted(notes))))
            return
        asc = stores.AppStoreConnect()
        app = asc.app_id()
        b = [x for x in asc.builds(app, version=v) if x["state"] == "VALID" and not x["expired"]]
        self.ok(bool(b), "no VALID %s build in App Store Connect: run --from testflight" % v)
        action, why = asc.submit_for_review(app, v, b[0]["id"], notes)
        print("  %s: %s (build %s)" % (action, why, b[0]["build"]))
        if action == "wait":
            self.notes_state.append("App Store: %s stays on TestFlight (%s); the next release after that review "
                                    "submits the newest" % (v, why))

    def garmin_step(self):
        """The Connect IQ store has no upload API (docs/20-publishing.md › Garmin): the gate built and signed the .iq,
        github put it on the app's release; this prints what the owner uploads and types at the developer dashboard."""
        if self.skip("garmin", [i for i in self.apps if self.apps[i].get("garmin")]):
            return
        for i in self.garmin_ids():
            g = self.garmin(i)
            iq = self.path(self.files(i)["iq"])
            self.ok(self.dry or os.path.exists(iq), "no %s: run the gate" % iq)
            kit = garmin_upload_kit(self.v[i], g, iq, self.changelog(i))
            print("\n".join("  " + line for line in kit.splitlines()) + "\n")
            self.notes_state.append("Connect IQ store: upload a new version of %s %s at %s (the garmin step's lines)" % (
                g["name"], os.path.basename(iq), GARMIN_DASHBOARD))

    def summary(self):
        print(", ".join("%s (%s)" % (self.name(i), self.tag(i)) for i in self.v) + ":")
        if self.dry:
            print("  would report each GitHub release and the stores of the apps released")
            return

        def line(name, fn):
            try:
                print("  %-22s %s" % (name, fn()))
            except Exception as e:  # a summary reports, it doesn't stop
                print("  %-22s unknown (%s)" % (name, e))

        def github(i):
            def get():
                r = self.gh_json(["release", "view", self.tag(i), "-R", REPO, "--json", "url,isPrerelease,assets"])
                pre = ", marked prerelease: gh release edit %s --prerelease=false" % self.tag(i) if r["isPrerelease"] else ""
                return "%s (%d files%s)" % (r["url"], len(r["assets"]), pre)
            return get

        def sparkle():
            with urllib.request.urlopen("%s?r=%d" % (FEED_URL, time.time()), timeout=30) as r:
                return "%s %s" % (FEED_URL, "serves it" if appcast_item(r.read().decode(), self.v["mac"]) else "NOT yet")

        def homebrew():
            out = self.sh(["gh", "api", "repos/%s/contents/Casks/sukrara.rb" % TAP, "-H",
                           "Accept: application/vnd.github.raw"], mutate=False)
            return "cask " + re.search(r'^  version "([^"]+)"', out, re.M).group(1)

        def play():
            p = stores.Play()
            edit = p.insert_edit()
            try:
                return ", ".join("%s: %s" % (t["track"], "; ".join("%s %s" % (r.get("name"), r.get("status"))
                                                                    for r in t.get("releases", [])))
                                 for t in p.tracks(edit) if t.get("releases"))
            finally:
                p.delete_edit(edit)

        def testflight():
            asc = stores.AppStoreConnect()
            b = asc.builds(asc.app_id(), version=self.v["mac"], limit=1)
            if not b:
                return "no %s build" % self.v["mac"]
            return "%s %s, export compliance %s" % (b[0]["build"], b[0]["state"],
                                                     "answered" if b[0]["encryption"] is not None else "MISSING")

        def appstore():
            asc = stores.AppStoreConnect()
            return ", ".join("%s %s" % (v["version"], v["state"]) for v in asc.versions(asc.app_id())[:3])
        for i in self.v:
            line("GitHub " + i, github(i))
        if self.has("mac"):
            for name, fn in [("Sparkle", sparkle), ("Homebrew", homebrew), ("TestFlight", testflight),
                             ("App Store", appstore)]:
                line(name, fn)
        if self.has("android"):
            line("Play", play)
        for n in self.notes_state:
            print("  manual: " + n)


STEP_METHODS = dict({s: s for s in STEPS}, garmin="garmin_step")


def overview(rel_apps, current, changes):
    """The lines release.py prints without apps: each app's version, its change files and its next version."""
    out = []
    for a in rel_apps:
        mine = versions.pending(changes, a["id"])
        nxt = versions.next_version(current[a["id"]], changes, a["id"])
        if nxt:
            out.append("%-13s %-8s → %-8s %s" % (a["id"], current[a["id"]], nxt, ", ".join(n for n, _ in mine)))
        else:
            out.append("%-13s %-8s   nothing to release" % (a["id"], current[a["id"]]))
    return out


def main(argv):
    p = argparse.ArgumentParser(description="Releases the apps that changed, each with its own version (docs/48).")
    p.add_argument("apps", nargs="*", help="app ids of contracts/apps/apps.json, each optionally =<version>")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--from", dest="start", choices=["preconditions"] + RESUMABLE, default=STEPS[0])
    a = p.parse_args(argv)
    with open(os.path.join(ROOT, versions.REGISTRY), encoding="utf-8") as f:
        registry = versions.load_apps(f.read())
    current = {}
    for app in registry:
        with open(os.path.join(ROOT, app["version"]), encoding="utf-8") as f:
            current[app["id"]] = versions.read_version(app, f.read())
    try:
        asked = parse_args_apps(a.apps)
    except ValueError as e:
        sys.exit(str(e))
    probe = Release({}, True)
    try:
        changes = probe.change_files()
    except Stop as e:
        sys.exit(str(e))
    if not asked:
        print("\n".join(overview(registry, current, changes)))
        print("\nRelease: tools/release/release.py <app> … (see --help)")
        return 0
    if a.start == "preconditions":
        try:
            releases = {i: v for i, (v, _) in plan(registry, current, changes, asked).items()}
        except ValueError as e:
            sys.exit(str(e) + ("\nReleased already? Resume with --from gate (or a later step)." if "no change file" in
                               str(e) else ""))
    else:  # the release commit or a later one is checked out: its versions are the release's
        unknown = [i for i in asked if i not in current]
        if unknown:
            sys.exit("%s isn't an app of %s" % (", ".join(unknown), versions.REGISTRY))
        wrong = ["%s is %s, not %s" % (i, current[i], v) for i, v in asked.items() if v and v != current[i]]
        if wrong and not a.dry_run:
            sys.exit("--from %s: %s (check out a commit with the release's versions)" % (a.start, "; ".join(wrong)))
        releases = {app["id"]: asked[app["id"]] or current[app["id"]] for app in registry if app["id"] in asked}
    rel = Release(releases, a.dry_run)
    steps = STEPS[STEPS.index(a.start):]
    if a.start != "preconditions":
        try:
            rel.resume_check()
        except Stop as e:
            sys.exit("--from %s: %s" % (a.start, e))
    print("Releasing " + ", ".join("%s (%s)" % (rel.name(i), rel.tag(i)) for i in releases))
    done = STEPS[:STEPS.index(a.start)]
    for step in steps:
        print("\n== %s%s" % (step, " (dry run)" if a.dry_run else ""), flush=True)
        try:
            getattr(rel, STEP_METHODS[step])()
        except (Stop, ValueError, OSError, stores.ApiError, RuntimeError) as e:
            left = steps[steps.index(step):]
            print("\nStopped at '%s': %s" % (step, e), file=sys.stderr)
            print("Done: %s" % (", ".join(done) or "nothing"), file=sys.stderr)
            print("Left: %s" % ", ".join(left), file=sys.stderr)
            if step in RESUMABLE:
                print("Resume: tools/release/release.py %s --from %s" % (" ".join(releases), step), file=sys.stderr)
            return 1
        done.append(step)
    if rel.would_stop:
        sys.stdout.flush()
        print("\nThe dry run found %d thing(s) that would stop the release:" % len(rel.would_stop), file=sys.stderr)
        for msg in rel.would_stop:
            print("  - " + msg, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
