# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""A version per app (docs/48-release-versioning.md): THE registry contracts/apps/apps.json, each app's version source,
the change files (changes/<slug>.md) and what a release makes of them. Pure functions on text, Python 3.9 stdlib: used
by tools/release/release.py and tools/check/check.py (check `changes`); tested by tools/release/test_release.py.

A change file:

    ---
    apps: [android, mac]
    kind: fix          # fix → patch, feature → minor
    ---
    - **What changed**: in words the person understands, as it reads in each app's CHANGELOG.
"""
import datetime
import json
import posixpath
import re

REGISTRY = "contracts/apps/apps.json"
CHANGES = "changes/"
CHANGES_README = "changes/README.md"
KINDS = ("fix", "feature")  # the bump each one makes: patch, minor
APP_KEYS = ("id", "name", "version", "tag", "changelog", "paths", "channels")
VERSION_RE = re.compile(r"(\d+)\.(\d+)\.(\d+)")
# a Connect IQ manifest: the app's or the barrel's own version (never <iq:manifest version="3">, the format's)
MANIFEST_VERSION = re.compile(r'(<iq:(?:application|barrel)\b[^>]*?\sversion=")([^"]*)(")')


def parse_version(v):
    m = VERSION_RE.fullmatch(v or "")
    if not m or int(m.group(2)) >= 1000 or int(m.group(3)) >= 1000:
        raise ValueError("version %r is not MAJOR.MINOR.PATCH (minor and patch < 1000)" % v)
    return tuple(int(x) for x in m.groups())


def version_code(v):
    """versionCode / CFBundleVersion: major·1 000 000 + minor·1 000 + patch (= lib.sh version_info)."""
    major, minor, patch = parse_version(v)
    return major * 1000000 + minor * 1000 + patch


def load_apps(text):
    """The registry's apps, in its order, checked: every key, unique ids and tag prefixes, one Latest."""
    apps = json.loads(text)["apps"]
    for a in apps:
        missing = [k for k in APP_KEYS if k not in a]
        if missing:
            raise ValueError("apps.json: %s lacks %s" % (a.get("id", "?"), ", ".join(missing)))
    for key in ("id", "tag"):
        seen = [a[key] for a in apps]
        if len(set(seen)) != len(seen):
            raise ValueError("apps.json: two apps share a %s" % key)
    tags = [a["tag"] for a in apps]
    for t in tags:  # 'v' and 'mac-v' never match each other's tags: one tag, one app
        if any(o != t and (o + "0").startswith(t + "0") for o in tags):
            raise ValueError("apps.json: tag prefix %r is ambiguous" % t)
    if [a.get("latest", False) for a in apps].count(True) != 1:
        raise ValueError("apps.json: exactly one app is GitHub's Latest")
    return apps


def by_id(apps):
    return {a["id"]: a for a in apps}


def tag(app, version):
    return app["tag"] + version


def tag_version(app, tag_name):
    """The version a tag names for this app, or None when the tag is another app's (`mac-v0.17.2` isn't Android's)."""
    if not tag_name.startswith(app["tag"]):
        return None
    rest = tag_name[len(app["tag"]):]
    return rest if VERSION_RE.fullmatch(rest) else None


def read_version(app, text):
    """The app's version from its version source's text: a VERSION file, or a Connect IQ manifest."""
    if app["version"].endswith(".xml"):
        m = MANIFEST_VERSION.search(text)
        if not m:
            raise ValueError("%s has no application or barrel version" % app["version"])
        v = m.group(2)
    else:
        v = text.strip()
    parse_version(v)
    return v


def with_version(app, text, version):
    """The version source's text with the version set."""
    parse_version(version)
    if app["version"].endswith(".xml"):
        new, n = MANIFEST_VERSION.subn(lambda m: m.group(1) + version + m.group(3), text, count=1)
        if not n:
            raise ValueError("%s has no application or barrel version" % app["version"])
        return new
    return version + "\n"


def bump(version, kind):
    major, minor, patch = parse_version(version)
    if kind == "feature":
        return "%d.%d.0" % (major, minor + 1)
    if kind == "fix":
        return "%d.%d.%d" % (major, minor, patch + 1)
    raise ValueError("kind %r is not one of %s" % (kind, ", ".join(KINDS)))


def concerns(app, path):
    """True when a change to the path concerns the app (its 'paths' minus 'exclude')."""
    def under(p, prefixes):
        return any(p == x or (x.endswith("/") and p.startswith(x)) for x in prefixes)
    return under(path, app["paths"]) and not under(path, app.get("exclude", []))


def built_from(app):
    """What the app is built from, as git pathspecs: its files ('paths' minus 'exclude', its version file kept) and
    'build' (what its build takes from outside them). A release is built only while these are its release commit's
    (the last commit that changed its version): tools/release/build-info.sh (Android, the Mac) and release.py --from
    (every app). Other apps, the docs and the tools may have moved on since."""
    return (app["paths"] + app.get("build", [])
            + [":(exclude)" + p for p in app.get("exclude", []) if p != app["version"]])


def is_change_file(path):
    return path.startswith(CHANGES) and path.endswith(".md") and path != CHANGES_README and "/" not in path[len(CHANGES):]


# --- change files -----------------------------------------------------------------------------------------------------

FRONT = re.compile(r"\A---\n(.*?)\n---\n(.*)\Z", re.S)


def parse_change(text, known=None):
    """{apps, kind, body} of a change file; ValueError says what's wrong. known: the registry's ids."""
    m = FRONT.match(text.replace("\r\n", "\n"))
    if not m:
        raise ValueError("no front matter: start with ---, apps: [...], kind: fix | feature, ---")
    fields = {}
    for line in m.group(1).splitlines():
        line = re.sub(r"\s+#.*$", "", line).strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition(":")
        if not sep:
            raise ValueError("front matter line %r isn't key: value" % line)
        fields[key.strip()] = value.strip()
    unknown = sorted(set(fields) - {"apps", "kind"})
    if unknown:
        raise ValueError("unknown front matter %s (only apps and kind)" % ", ".join(unknown))
    apps_text = fields.get("apps", "")
    lm = re.fullmatch(r"\[(.*)\]", apps_text)
    if not lm:
        raise ValueError("apps: a list like [android, mac]")
    apps = [x.strip() for x in lm.group(1).split(",") if x.strip()]
    if not apps:
        raise ValueError("apps: names no app")
    if len(set(apps)) != len(apps):
        raise ValueError("apps: names an app twice")
    if known is not None:
        bad = [x for x in apps if x not in known]
        if bad:
            raise ValueError("apps: %s isn't in %s (%s)" % (", ".join(bad), REGISTRY, ", ".join(known)))
    kind = fields.get("kind", "")
    if kind not in KINDS:
        raise ValueError("kind: %r, not fix or feature" % kind)
    body = m.group(2).strip("\n")
    if not body.strip():
        raise ValueError("no text: the CHANGELOG bullet goes under the front matter")
    if not body.lstrip().startswith("- "):
        raise ValueError("the text is a CHANGELOG bullet: start it with '- '")
    return {"apps": apps, "kind": kind, "body": body}


def change_text(change):
    return "---\napps: [%s]\nkind: %s\n---\n%s\n" % (", ".join(change["apps"]), change["kind"], change["body"])


def pending(changes, app_id):
    """The change files ({name: parsed}) naming the app, features first, each kind by name."""
    mine = [(n, c) for n, c in changes.items() if app_id in c["apps"]]
    return sorted(mine, key=lambda nc: (KINDS.index(nc[1]["kind"]) * -1, nc[0]))


def next_version(current, changes, app_id):
    """The version a release of the app takes: the highest kind of its change files; None without any."""
    mine = pending(changes, app_id)
    if not mine:
        return None
    kind = "feature" if any(c["kind"] == "feature" for _, c in mine) else "fix"
    return bump(current, kind)


def consumed(changes, released):
    """{name: new text, or None to delete}: each change file without the released apps."""
    out = {}
    for name, c in changes.items():
        if not set(c["apps"]) & set(released):
            continue
        left = [a for a in c["apps"] if a not in released]
        out[name] = change_text(dict(c, apps=left)) if left else None
    return out


def rebase_links(md, folder):
    """A change file's relative links (written from the repository's root) made relative to folder (its CHANGELOG's)."""
    up = "/".join([".."] * len([p for p in folder.split("/") if p]))

    def rebase(m):
        return "](%s)" % posixpath.normpath(posixpath.join(up, m.group(1))) if up else m.group(0)
    return re.sub(r"\]\((?!https?://|#|mailto:|/)([^)\s]+)\)", rebase, md)


# --- changelogs -------------------------------------------------------------------------------------------------------

def entry_heading(version, date):
    return "## %s — %s" % (version, date.isoformat() if isinstance(date, datetime.date) else date)


def changelog_with_entry(changelog, version, date, bullets):
    """The app's CHANGELOG.md with `## <version> — <date>` and the bullets above the newest entry (below the intro).
    Refuses a version that has an entry and an entry without bullets."""
    if changelog_entry(changelog, version):
        raise ValueError("the changelog already has a %s entry" % version)
    body = "\n".join(b.rstrip("\n") for b in bullets).strip("\n")
    if not body:
        raise ValueError("no change file for %s: nothing to release" % version)
    entry = entry_heading(version, date) + "\n\n" + body + "\n"
    m = re.search(r"^## ", changelog, re.M)
    if not m:
        return changelog.rstrip("\n") + "\n\n" + entry
    return changelog[:m.start()] + entry + "\n" + changelog[m.start():]


def changelog_entry(changelog, version):
    """(heading, body) of the version's entry, or None."""
    starts = [m.start() for m in re.finditer(r"^## ", changelog, re.M)] + [len(changelog)]
    for s, e in zip(starts, starts[1:]):
        heading = changelog[s:changelog.find("\n", s) if "\n" in changelog[s:e] else e].rstrip()
        if re.match(r"## %s(\s|$)" % re.escape(version), heading):
            return heading, changelog[s + len(heading):e].strip("\n")
    return None


# --- mentions ---------------------------------------------------------------------------------------------------------

def mention_regex(pattern):
    """A mention's pattern ({tag}, {version}) as a regex matching it at any release."""
    parts = re.split(r"(\{tag\}|\{version\})", pattern)
    return "".join({"{tag}": r"[\w.-]+?", "{version}": r"\d+\.\d+\.\d+"}.get(p, re.escape(p)) for p in parts)


def with_mentions(text, pattern, app, version):
    """The text with every mention of the pattern naming the release (app, version); ValueError when there is none."""
    new = pattern.replace("{tag}", tag(app, version)).replace("{version}", version)
    out, n = re.subn(mention_regex(pattern), lambda m: new, text)
    if not n:
        raise ValueError("no %r to rewrite" % pattern)
    return out


if __name__ == "__main__":  # python3 tools/release/versions.py built-from <app>: its pathspecs, one a line
    import os
    import sys
    if len(sys.argv) != 3 or sys.argv[1] != "built-from":
        sys.exit("usage: versions.py built-from <app>")
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", REGISTRY), encoding="utf-8") as f:
        app = by_id(load_apps(f.read())).get(sys.argv[2])
    if not app:
        sys.exit("%s isn't an app of %s" % (sys.argv[2], REGISTRY))
    print("\n".join(built_from(app)))
