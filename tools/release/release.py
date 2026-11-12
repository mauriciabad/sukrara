#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""One command publishes a version to every channel (#187, docs/DEVELOPMENT.md › Release):

    tools/release/release.py <version> [--dry-run] [--from <step>]

Runs on the release Mac (the keys stay there), in any clean checkout whose HEAD is origin/next: the main checkout or a
worktree, on any branch or a detached HEAD. Every push names its refspec (HEAD:next, HEAD:main); nothing needs a local
`next` or `main` branch. The steps, in order; each checks its result and stops on failure with what's done and what's
left (resume with --from <step>; every step can run again):

  preconditions  a clean tree, HEAD = origin/next, its Checks run green or still running (not awaited: the version
                 commit's run covers it; a failed one stops), the new version above VERSION, CHANGELOG.md's Unreleased section, the Play notes
                 (apps/android/fastlane/metadata/android/<en-US|es-ES>/changelogs/<versionCode>.txt, at most 500
                 characters) and the App Store notes (apps/macos/AppStore/whats-new/…, see appstore), no tag yet,
                 the keys and gh
  version        VERSION, the version in docs (DEVELOPMENT.md, QA.md, FEATURES.md), CHANGELOG's Unreleased → the
                 version's entry; commit, push to next (fast-forward; its Checks run is awaited before github)
  gate           android-release.sh (verify-apk.sh inside), android-smoke.sh --final --no-build on the emulator,
                 play-bundle.sh, mac-release.sh (notarized, Sparkle-signed, verify-app.sh --notarized inside, writes
                 docs/appcast.xml), Bundle/smoke.sh, mac-appstore.sh (build + validate). ./gradlew check and swift test
                 aren't repeated: the release commit's green Checks run ran them (the Mac job on the newest green
                 commit with the same Mac sources: VERSION reaches only the bundle, which this gate builds and smokes).
  github         the release commit's Checks run green (its push or one started by hand, any branch; the one wait for
                 CI); main fast-forwarded, tag v<version>, the GitHub release (a normal one, Latest) with the APK, the
                 Android mapping, the dmg and the zip, notes from the CHANGELOG entry. main's own run isn't awaited:
                 the commit's green run is what it finds (tools/ci/changes.py)
  sparkle        docs/appcast.xml committed to main and next once the release's dmg is there, Pages deployed, the feed
                 at https://sukrara.mauri.app/appcast.xml serves the item
  homebrew       the tap's workflow committed the cask for this version (the dmg's sha256)
  play           the play bundle on Google Play's internal and production tracks with the en-US / es-ES notes (Android
                 Publisher API; managed publishing is off, so production goes live once Google approves it)
  testflight     the App Store pkg the gate built and validated (built once by mac-appstore.sh if it's missing, e.g.
                 --from testflight in a fresh checkout), its version and build number checked, uploaded by
                 mac-appstore.sh --upload; then App Store Connect processes the build (export compliance: summary)
  appstore       the App Store version: created (or the editable one reused), the build attached, What's New from
                 apps/macos/AppStore/whats-new/<en-US|es-ES>/<versionCode>.txt (at most 4000 characters), released
                 after approval, submitted for App Review (needs the App Manager key); while another version is in
                 review it waits on TestFlight, and a later release submits the newest
  summary        every channel's state

--dry-run      runs the read-only checks, prints every command, request body and note it would use; changes nothing;
               exits 1 when a check would stop the release
--from STEP    starts at STEP (the release commit must be checked out: VERSION = <version>)
"""
import argparse
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

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
REPO = "mauriciabad/sukrara"
REPO_URL = "https://github.com/" + REPO
TAP = "mauriciabad/homebrew-tap"
FEED_URL = "https://sukrara.mauri.app/appcast.xml"
STEPS = ["preconditions", "version", "gate", "github", "sparkle", "homebrew", "play", "testflight", "appstore",
         "summary"]
PLAY_LANGS = ["en-US", "es-ES"]
PLAY_TRACKS = ["internal", "production"]
PLAY_NOTES = "apps/android/fastlane/metadata/android/%s/changelogs/%d.txt"
ASC_LANGS = ["en-US", "es-ES"]
ASC_NOTES = "apps/macos/AppStore/whats-new/%s/%d.txt"
DIST = "apps/android/app/build/dist"
MAC_BUILD = "apps/macos/build"
DOCS_WITH_VERSION = ["docs/DEVELOPMENT.md", "docs/QA.md", "docs/FEATURES.md"]


# --- pure parts (tools/release/test_release.py) ----------------------------------------------------------------------

def parse_version(v):
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", v or "")
    if not m or int(m.group(2)) >= 1000 or int(m.group(3)) >= 1000:
        raise ValueError("version %r is not MAJOR.MINOR.PATCH (minor and patch < 1000)" % v)
    return tuple(int(x) for x in m.groups())


def version_code(v):
    major, minor, patch = parse_version(v)
    return major * 1000000 + minor * 1000 + patch  # = lib.sh version_info


def sections(changelog):
    """[(heading line, start, end)] of every `## ` section (end = the next one's start)."""
    starts = [m.start() for m in re.finditer(r"^## ", changelog, re.M)]
    out = []
    for i, s in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(changelog)
        out.append((changelog[s:changelog.index("\n", s) if "\n" in changelog[s:] else end].rstrip(), s, end))
    return out


def changelog_release(changelog, version):
    """CHANGELOG.md with the Unreleased section turned into `## <version>[ — <summary>]` and a new,
    empty Unreleased section above it. `## Unreleased — <summary>` gives the summary. Refuses an empty Unreleased
    section and a version that already has an entry."""
    secs = sections(changelog)
    if any(re.match(r"## %s(\s|$)" % re.escape(version), h) for h, _, _ in secs):
        raise ValueError("CHANGELOG.md already has a %s entry" % version)
    unreleased = [x for x in secs if re.match(r"## Unreleased(\s|$)", x[0])]
    if not unreleased:
        raise ValueError("CHANGELOG.md has no '## Unreleased' section")
    heading, start, end = unreleased[0]
    body = changelog[start + len(heading):end].strip("\n")
    if not body.strip():
        raise ValueError("CHANGELOG.md's Unreleased section is empty: nothing to release")
    summary = re.sub(r"^## Unreleased\s*(—|-)?\s*", "", heading).strip()
    title = "## " + version + (" — " + summary if summary else "")
    return changelog[:start] + "## Unreleased\n\n" + title + "\n\n" + body + "\n\n" + changelog[end:].lstrip("\n")


def changelog_entry(changelog, version):
    """(heading, body) of the version's CHANGELOG.md entry, or None."""
    for heading, start, end in sections(changelog):
        if re.match(r"## %s(\s|$)" % re.escape(version), heading):
            return heading, changelog[start + len(heading):end].strip("\n")
    return None


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


def absolute_links(md, version):
    """Repo-relative links → the files at the tag (GitHub release notes have no base path)."""
    return re.sub(r"\]\((?!https?://|#|mailto:)([^)\s]+)\)",
                  lambda m: "](%s/blob/v%s/%s)" % (REPO_URL, version, m.group(1).lstrip("./")), md)


def github_notes(changelog, version, cert_sha256):
    """The GitHub release notes: the medical disclaimer, the CHANGELOG entry and the files."""
    entry = changelog_entry(changelog, version)
    if not entry:
        raise ValueError("CHANGELOG.md has no %s entry" % version)
    v = version
    head = ("**Not a medical device.** Every behaviour, with its rules and numbers: [docs/FEATURES.md](%s/blob/v%s/"
            "docs/FEATURES.md)." % (REPO_URL, v))
    files = "\n".join([
        "## Files",
        "",
        "- `Sukrara-%s.apk`: Android 16+, arm64. Signed with Sukrara's release key, certificate SHA-256 `%s`."
        % (v, cert_sha256),
        "- `Sukrara-%s.dmg` (or `Sukrara-mac-%s.zip`): Mac, Apple Silicon, macOS 15+. Notarized. The GitHub and "
        "Homebrew Mac app updates itself (Sparkle)." % (v, v),
        "- `Sukrara-%s-android-mapping.txt.gz`: the R8 mapping that makes an Android crash trace readable (for bug "
        "reports; not something to install)." % v,
    ])
    return head + "\n\n" + absolute_links(unwrap(entry[1]), v).strip() + "\n\n" + files + "\n"


def bump_docs(path, text, old, new):
    """The version written in the docs: DEVELOPMENT.md's `version.sh` example, QA.md / FEATURES.md's About texts
    (*<version> · … version*)."""
    if path.endswith("DEVELOPMENT.md"):
        text = re.sub(r"(`tools/release/version\.sh` → `)[^`]*(`)",
                      lambda m: "%s%s %d%s" % (m.group(1), new, version_code(new), m.group(2)),
                      text)
    return text.replace("*%s · " % old, "*%s · " % new)


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
    """{length, signature, url} of the version's item in docs/appcast.xml, or None."""
    m = re.search(r"<item>\s*<title>Sukrara %s</title>(.*?)</item>" % re.escape(version), feed, re.S)
    if not m:
        return None
    e = re.search(r'<enclosure url="([^"]+)" length="(\d+)"[^>]*?sparkle:edSignature="([^"]+)"', m.group(1), re.S)
    return {"url": e.group(1), "length": int(e.group(2)), "signature": e.group(3)} if e else None


def artifacts(version):
    return {
        "apk": "%s/Sukrara-%s.apk" % (DIST, version),
        "mapping": "%s/Sukrara-%s-android-mapping.txt.gz" % (DIST, version),
        "dmg": "%s/Sukrara-%s.dmg" % (MAC_BUILD, version),
        "zip": "%s/Sukrara-mac-%s.zip" % (MAC_BUILD, version),
    }


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
    """Why --from a later step can't go on ([] when it can): changes besides docs/appcast.xml, which the gate writes
    and the sparkle step commits. The branch doesn't matter; main() checks VERSION is the release's."""
    extra = [f for f in dirty if f != "docs/appcast.xml"]
    return ["the tree has changes besides docs/appcast.xml: %s" % ", ".join(extra)] if extra else []


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
    def __init__(self, version, dry):
        self.v = version
        self.code = version_code(version)
        self.dry = dry
        self.changelog = None  # dry run: the CHANGELOG the version step would write
        self.notes_state = []  # what the summary reports as still manual
        self.would_stop = []  # dry run: every check that would have stopped the release

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

    def remote_tag(self):
        """The commit tag v<version> on origin points at, or ''."""
        out = self.sh(["git", "ls-remote", "--tags", "origin", "refs/tags/v%s*" % self.v], mutate=False)
        refs = dict(reversed(l.split("\t")) for l in out.splitlines() if "\t" in l)
        tag = "refs/tags/v" + self.v
        return refs.get(tag + "^{}") or refs.get(tag, "")  # an annotated tag's commit is the peeled ^{} line

    def resume_check(self):
        """--from a later step: the release commit (or the appcast commit on it) checked out, nothing else changed."""
        for problem in resume_problems(self.dirty()):
            self.ok(False, problem)

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
        merged = self.git_ok(["merge", "--no-ff", "-m", "Merge %s (Sukrara %s, on main) into next" % (mine[:8], self.v),
                              mine])  # git's default message would name this checkout's branch, not next
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

    def current_changelog(self):
        return self.changelog if self.changelog is not None else self.read("CHANGELOG.md")

    # steps
    def preconditions(self):
        current = self.read("VERSION").strip()
        self.ok(parse_version(self.v) > parse_version(current), "%s is not above VERSION (%s)" % (self.v, current))
        self.sh(["git", "fetch", "--quiet", "--tags", "origin"], mutate=False)
        sha = self.head()
        for problem in start_problems(sha, self.sh(["git", "rev-parse", "origin/next"], mutate=False), self.dirty()):
            self.ok(False, problem)
        self.ok(self.git_ok(["merge-base", "--is-ancestor", "origin/main", "HEAD"]),
                "origin/main has commits next doesn't: main can't be fast-forwarded")
        self.ok(not self.sh(["git", "ls-remote", "--tags", "origin", "v" + self.v], mutate=False),
                "tag v%s exists on origin" % self.v)
        self.ok(os.path.exists(self.path("tools/check/strict")), "tools/check/strict is missing")
        try:
            changelog_release(self.read("CHANGELOG.md"), self.v)
        except ValueError as e:
            self.ok(False, str(e))
        for read in (read_play_notes, read_appstore_notes):
            try:
                read(ROOT, self.v)
            except ValueError as e:
                self.ok(False, str(e))
        for f, what in [("~/.android/sukrara-release.jks", "the Android release key"),
                        ("~/.android/sukrara-play.json", "the Play service account key")]:
            self.ok(os.path.exists(os.path.expanduser(f)), "no %s (%s)" % (what, f))
        key, issuer = stores.asc_key_paths()
        self.ok(bool(key and issuer), "no App Store Connect API key / issuer (~/.apple-dev)")
        self.sh(["gh", "auth", "status"], mutate=False)
        # Not awaited here: the version commit's run (awaited in github) covers every change since the newest green
        # commit (tools/ci/changes.py), this one's included. Only a run that already failed stops the release.
        state = self.checks_state(sha)
        self.ok(state in ("success", "pending"), "the Checks run on %s is %s" % (sha[:8], state))
        if state == "pending":
            print("  Checks on %s still running: the version commit's run covers it" % sha[:8])

    def version(self):
        current = self.read("VERSION").strip()
        changelog = self.read("CHANGELOG.md")
        if current == self.v and changelog_entry(changelog, self.v):
            print("  VERSION is %s and CHANGELOG.md has its entry: already done" % self.v)
        else:
            new_changelog = changelog_release(changelog, self.v)
            files = {"VERSION": self.v + "\n", "CHANGELOG.md": new_changelog}
            for rel in DOCS_WITH_VERSION:
                files[rel] = bump_docs(rel, self.read(rel), current, self.v)
            if self.dry:
                self.changelog = new_changelog
                for rel, text in files.items():
                    if text != self.read(rel):
                        print("  would write %s" % rel)
                print("  the entry's heading: " + changelog_entry(new_changelog, self.v)[0])
            else:
                for rel, text in files.items():
                    with open(self.path(rel), "w", encoding="utf-8") as f:
                        f.write(text)
            self.sh(["git", "add", "--"] + list(files))
            self.sh(["git", "commit", "-q", "-m", "Version %s: CHANGELOG entry, the version in the docs" % self.v,
                     "-m", "Features-skip: version bump only; every behaviour in it is already in docs/FEATURES.md"])
        self.sh(["git", "push", "origin", "HEAD:next"])  # fast-forward only: git refuses anything else
        print("  its Checks run is awaited before anything is published (github), the gate runs meanwhile")

    def gate(self):
        r = "tools/release/"
        self.sh([r + "android-release.sh"], capture=False)
        self.sh([r + "android-smoke.sh", "--final", "--no-build"], capture=False)
        self.sh([r + "play-bundle.sh"], capture=False)
        self.sh([r + "mac-release.sh"], capture=False)
        self.sh(["apps/macos/Bundle/smoke.sh"], capture=False)
        # rebuilds build/Sukrara.app as the App Store version; its validated pkg is the one testflight uploads
        self.sh([r + "mac-appstore.sh"], capture=False)
        if self.dry:
            print("  would check the files it leaves, docs/appcast.xml's %s item, and that nothing else changed" % self.v)
            return
        for rel in list(artifacts(self.v).values()) + ["%s/Sukrara-%s-play.aab" % (DIST, self.v), appstore_pkg(self.v)]:
            self.ok(os.path.exists(self.path(rel)), "the gate left no %s" % rel)
        self.ok(appcast_item(self.read("docs/appcast.xml"), self.v),
                "docs/appcast.xml has no item for %s (was the build notarized?)" % self.v)
        extra = [f for f in self.dirty() if f != "docs/appcast.xml"]
        self.ok(not extra, "the gate changed files it shouldn't: %s" % ", ".join(extra))

    def check_dmg_is_the_signed_one(self):
        if self.dry:
            print("  would check the dmg is the one docs/appcast.xml signs (length, sign_update --verify)")
            return
        item = appcast_item(self.read("docs/appcast.xml"), self.v)
        dmg = self.path(artifacts(self.v)["dmg"])
        if not self.ok(item is not None, "docs/appcast.xml has no item for %s: run the gate (mac-release.sh)" % self.v):
            return
        if not self.ok(os.path.exists(dmg), "no %s" % dmg):
            return
        self.ok(item["length"] == os.path.getsize(dmg), "the appcast's length isn't the dmg's: rebuilt since?")
        sign_update = self.path("apps/macos/.build/artifacts/sparkle/Sparkle/bin/sign_update")
        self.sh([sign_update, "--verify", dmg, item["signature"]], mutate=False)

    def github(self):
        sha = self.head()
        self.ok(self.read("VERSION").strip() == self.v or (self.dry and self.changelog is not None),
                "VERSION isn't %s: check out the release commit" % self.v)
        self.wait("Checks on %s" % sha[:8], lambda: self.checks_state(sha), 150 * 60)
        self.check_dmg_is_the_signed_one()
        files = artifacts(self.v)
        for rel in files.values():
            self.ok(self.dry or os.path.exists(self.path(rel)), "no %s: run the gate" % rel)
        # main: fast-forward to the release commit
        self.sh(["git", "fetch", "--quiet", "--tags", "origin"], mutate=False)
        if self.sh(["git", "rev-parse", "origin/main"], mutate=False) != sha:
            self.sh(["git", "push", "origin", "HEAD:main"])
        # the tag
        remote = self.remote_tag()
        if remote:
            self.ok(remote == sha, "tag v%s on origin isn't %s" % (self.v, sha[:8]))
        else:
            if not self.sh(["git", "tag", "-l", "v" + self.v], mutate=False):
                self.sh(["git", "tag", "-a", "v" + self.v, "-m", "Sukrara " + self.v, sha])
            self.sh(["git", "push", "origin", "refs/tags/v" + self.v])
        # the release
        exists = subprocess.run(["gh", "release", "view", "v" + self.v, "-R", REPO], capture_output=True).returncode == 0
        if not exists:
            cert = self.read("tools/release/android-cert.sha256").strip()
            notes = github_notes(self.current_changelog(), self.v, cert)
            if self.dry:
                print("  the notes:\n" + "\n".join("    " + l for l in notes.splitlines()))
            with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False) as f:
                f.write(notes)
            self.sh(["gh", "release", "create", "v" + self.v, "-R", REPO, "--verify-tag", "--title",
                     "Sukrara " + self.v, "--notes-file", f.name, "--latest"] + [self.path(p) for p in files.values()])
            os.unlink(f.name)
        if not self.dry:
            assets = {a["name"]: a for a in self.gh_json(["release", "view", "v" + self.v, "-R", REPO, "--json",
                                                          "assets"])["assets"]}
            for rel in files.values():
                name, want = os.path.basename(rel), "sha256:" + sha256(self.path(rel))
                if name not in assets:
                    self.sh(["gh", "release", "upload", "v" + self.v, "-R", REPO, self.path(rel)])
                elif assets[name].get("digest") and assets[name]["digest"] != want:
                    raise Stop("the release's %s isn't the local file (%s)" % (name, assets[name]["digest"]))
        # No second wait on main: main now points at the commit whose run is green above, and that run is what main's
        # own run finds (tools/ci/changes.py: an already green commit needs nothing again).

    def sparkle(self):
        item = appcast_item(self.read("docs/appcast.xml"), self.v)
        if self.dry and item is None:
            print("  would check docs/appcast.xml has the %s item (mac-release.sh writes it in the gate) and the "
                  "release's dmg matches it" % self.v)
            print("  would run: git commit docs/appcast.xml; git push origin HEAD:main")
            self.push_next()
            print("  would wait for GitHub Pages and %s to serve %s" % (FEED_URL, self.v))
            return
        self.ok(item is not None, "docs/appcast.xml has no item for %s" % self.v)
        committed = "docs/appcast.xml" not in self.dirty()
        if not committed:
            # the download the item names must be there first (#91)
            asset = self.gh_json(["release", "view", "v" + self.v, "-R", REPO, "--json", "assets"]) if not self.dry else None
            if asset is not None:
                dig = {a["name"]: a.get("digest") for a in asset["assets"]}.get(os.path.basename(item["url"]))
                self.ok(dig == "sha256:" + sha256(self.path(artifacts(self.v)["dmg"])),
                        "the release has no dmg matching the appcast (%s)" % dig)
            self.sh(["git", "add", "docs/appcast.xml"])
            self.sh(["git", "commit", "-q", "-m", "Sparkle feed: Sukrara %s" % self.v, "-m",
                     "The GitHub release v%s with its dmg is published (#91)." % self.v, "-m",
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
            return "success" if appcast_item(body, self.v) == item else "pending"
        self.wait("%s to serve %s" % (FEED_URL, self.v), served, 20 * 60)

    def homebrew(self):
        dmg = self.path(artifacts(self.v)["dmg"])

        def cask():
            out = self.sh(["gh", "api", "repos/%s/contents/Casks/sukrara.rb" % TAP, "-H",
                           "Accept: application/vnd.github.raw"], mutate=False)
            ver = re.search(r'^  version "([^"]+)"', out, re.M)
            sha = re.search(r'^  sha256 "([^"]+)"', out, re.M)
            if not ver or ver.group(1) != self.v:
                runs = self.gh_json(["run", "list", "-R", REPO, "--workflow", "homebrew-tap.yml", "--event", "release",
                                     "--json", "displayTitle,status,conclusion,url", "-L", "10"]) or []
                mine = [r for r in runs if r.get("displayTitle") == "Sukrara " + self.v]
                if mine and pick_run(mine) == "failure":
                    return "failure: the tap workflow failed (%s)" % mine[0].get("url")
                return "pending"
            if not sha or sha.group(1) != sha256(dmg):
                return "failure: the cask's sha256 isn't the dmg's"
            if "auto_updates true" not in out:
                return "failure: the cask lacks auto_updates true (#91)"
            return "success"
        self.wait("the Homebrew cask at %s (%s)" % (self.v, TAP), cask, 20 * 60)

    def play(self):
        """The play bundle on the internal and the production track (every release goes to production: 0.x is public
        work in progress, the owner's 2026-11-13 decision). Managed publishing is off, so production goes live once
        Google approves it. A track that already has the version code is left alone (re-running is safe)."""
        aab = self.path("%s/Sukrara-%s-play.aab" % (DIST, self.v))
        try:
            notes = read_play_notes(ROOT, self.v)
        except ValueError as e:
            self.ok(False, str(e))
            return
        bodies = [stores.play_track_body(t, self.v, self.code, notes) for t in PLAY_TRACKS]
        if self.dry:
            print("  would upload %s (versionCode %d) to the %s tracks:"
                  % (os.path.relpath(aab, ROOT), self.code, " and ".join(PLAY_TRACKS)))
            print("    edits.insert → bundles.upload")
            for body in bodies:
                print("    tracks.update " + json.dumps(body, ensure_ascii=False))
            print("    → edits.commit (production: sent for review, live on approval)")
            return
        self.ok(os.path.exists(aab), "no %s: run the gate (play-bundle.sh)" % aab)
        play = stores.Play()
        edit = play.insert_edit()
        try:
            todo = [b for b in bodies if not stores.track_has(play.track(edit, b["track"]), self.code)]
            for b in bodies:
                if b not in todo:
                    print("  the %s track already has %d" % (b["track"], self.code))
            if not todo:
                play.delete_edit(edit)
                return
            if self.code not in [int(b["versionCode"]) for b in play.bundles(edit)]:
                up = play.upload_bundle(edit, aab)
                self.ok(int(up["versionCode"]) == self.code, "Play read versionCode %s" % up["versionCode"])
                self.ok(up.get("sha256") == sha256(aab), "Play's sha256 of the bundle isn't the local one")
                print("  uploaded versionCode %d" % self.code)
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
            rels = {t: stores.track_has(play.track(check, t), self.code) for t in PLAY_TRACKS}
        finally:
            play.delete_edit(check)
        for t, rel in rels.items():
            self.ok(rel is not None, "the %s track doesn't list %d after the commit" % (t, self.code))
            print("  %s track: %s %d %s" % (t, rel["name"], self.code, rel["status"]))

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
        pkg = self.path(appstore_pkg(self.v))
        if not os.path.exists(pkg):
            print("  no %s (the gate builds it): building it" % appstore_pkg(self.v))
            self.sh(["tools/release/mac-appstore.sh"], capture=False)
        return pkg_build(self.pkg_distribution(pkg), self.v)

    def testflight(self):
        pkg = appstore_pkg(self.v)
        if self.dry:
            print("  would look for a %s build in App Store Connect, else upload %s" % (self.v, pkg))
            if os.path.exists(self.path(pkg)):
                try:
                    print("  %s is build %s" % (pkg, pkg_build(self.pkg_distribution(self.path(pkg)), self.v)))
                except ValueError as e:
                    self.ok(False, str(e))
            else:
                print("  no pkg yet (the gate builds it); if the gate left none, would run: tools/release/mac-appstore.sh")
            print("  would check its version and build number, then run: tools/release/mac-appstore.sh --upload")
            print("  would wait until the build's processingState is VALID")
            return
        asc = stores.AppStoreConnect()
        app = asc.app_id()
        live = [b for b in asc.builds(app, version=self.v) if b["state"] in ("PROCESSING", "VALID") and not b["expired"]]
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
        try:
            notes = read_appstore_notes(ROOT, self.v)
        except ValueError as e:
            self.ok(False, str(e))
            return
        if self.dry:
            print("  would find the App Store versions, then create or reuse %s (release after approval), attach its "
                  "VALID build, set What's New (%s) and submit it for App Review; nothing while another version is "
                  "in review" % (self.v, ", ".join(sorted(notes))))
            return
        asc = stores.AppStoreConnect()
        app = asc.app_id()
        b = [x for x in asc.builds(app, version=self.v) if x["state"] == "VALID" and not x["expired"]]
        self.ok(bool(b), "no VALID %s build in App Store Connect: run --from testflight" % self.v)
        action, why = asc.submit_for_review(app, self.v, b[0]["id"], notes)
        print("  %s: %s (build %s)" % (action, why, b[0]["build"]))
        if action == "wait":
            self.notes_state.append("App Store: %s stays on TestFlight (%s); the next release after that review "
                                    "submits the newest" % (self.v, why))

    def summary(self):
        print("Sukrara %s (%d):" % (self.v, self.code))
        if self.dry:
            print("  would report GitHub, Sparkle, Homebrew, Play, TestFlight and the App Store")
            return

        def line(name, fn):
            try:
                print("  %-10s %s" % (name, fn()))
            except Exception as e:  # a summary reports, it doesn't stop
                print("  %-10s unknown (%s)" % (name, e))

        def github():
            r = self.gh_json(["release", "view", "v" + self.v, "-R", REPO, "--json", "url,isPrerelease,assets"])
            pre = ", marked prerelease: gh release edit v%s --prerelease=false --latest" % self.v if r["isPrerelease"] else ""
            return "%s (%d files%s)" % (r["url"], len(r["assets"]), pre)

        def sparkle():
            with urllib.request.urlopen("%s?r=%d" % (FEED_URL, time.time()), timeout=30) as r:
                return "%s %s" % (FEED_URL, "serves it" if appcast_item(r.read().decode(), self.v) else "NOT yet")

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
            b = asc.builds(asc.app_id(), version=self.v, limit=1)
            if not b:
                return "no %s build" % self.v
            return "%s %s, export compliance %s" % (b[0]["build"], b[0]["state"],
                                                     "answered" if b[0]["encryption"] is not None else "MISSING")
        def appstore():
            asc = stores.AppStoreConnect()
            return ", ".join("%s %s" % (v["version"], v["state"]) for v in asc.versions(asc.app_id())[:3])
        for name, fn in [("GitHub", github), ("Sparkle", sparkle), ("Homebrew", homebrew), ("Play", play),
                         ("TestFlight", testflight), ("App Store", appstore)]:
            line(name, fn)
        for n in self.notes_state:
            print("  manual: " + n)


def main(argv):
    p = argparse.ArgumentParser(description="One command publishes a version to every channel (#187).")
    p.add_argument("version")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--from", dest="start", choices=STEPS, default=STEPS[0])
    a = p.parse_args(argv)
    try:
        parse_version(a.version)
    except ValueError as e:
        sys.exit(str(e))
    rel = Release(a.version, a.dry_run)
    steps = STEPS[STEPS.index(a.start):]
    if a.start not in ("preconditions", "version") and not a.dry_run:
        current = rel.read("VERSION").strip()
        if current != a.version:
            sys.exit("--from %s: VERSION is %s, not %s (check out the release commit)" % (a.start, current, a.version))
    if a.start not in ("preconditions", "version"):
        try:
            rel.resume_check()
        except Stop as e:
            sys.exit("--from %s: %s" % (a.start, e))
    done = STEPS[:STEPS.index(a.start)]
    for step in steps:
        print("\n== %s%s" % (step, " (dry run)" if a.dry_run else ""), flush=True)
        try:
            getattr(rel, step)()
        except (Stop, ValueError, OSError, stores.ApiError, RuntimeError) as e:
            left = steps[steps.index(step):]
            print("\nStopped at '%s': %s" % (step, e), file=sys.stderr)
            print("Done: %s" % (", ".join(done) or "nothing"), file=sys.stderr)
            print("Left: %s" % ", ".join(left), file=sys.stderr)
            print("Resume: tools/release/release.py %s --from %s" % (a.version, step), file=sys.stderr)
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
