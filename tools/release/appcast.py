# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes one release into Sparkle's appcast (docs/appcast.xml, https://sukrara.mauri.app/appcast.xml; #91).

    python3 tools/release/appcast.py --version 0.14.0 --build 14000 --kind prerelease \\
        --signature <sign_update -p of the dmg> --length <bytes of the dmg> [--min-os 15.0] [--feed docs/appcast.xml]

The item: the version (sparkle:version = CFBundleVersion, shortVersionString), the minimum macOS, the prerelease
channel for a prerelease (0.x installs take it: Updates.channels), the GitHub release's dmg as the enclosure with its
EdDSA signature and length, the CHANGELOG.md section of the version as the notes Sparkle shows, and the GitHub release
page as the full notes. Re-running for the same version replaces its item; items stay newest first. Python 3.9 stdlib.
"""
import argparse
import email.utils
import html
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
REPO = "https://github.com/mauriciabad/sukrara"
PRERELEASE_CHANNEL = "prerelease"


def inline(text):
    """CHANGELOG markdown inline → HTML: escapes, links, **bold**, *italic*, `code`."""
    out = html.escape(text, quote=False)
    out = re.sub(r"\[([^\]]+)\]\((https://[^)\s]+)\)", lambda m: '<a href="%s">%s</a>' % (m.group(2), m.group(1)), out)
    out = re.sub(r"\*\*\*(.+?)\*\*\*", r"<b><i>\1</i></b>", out)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<i>\1</i>", out)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    return re.sub(r"(?<![\w/])#(\d+)\b", lambda m: '<a href="%s/issues/%s">#%s</a>' % (REPO, m.group(1), m.group(1)), out)


def notes(changelog, version):
    """The CHANGELOG.md section `## <version> …` as HTML (paragraphs and bullet lists), or None."""
    lines, inside = [], False
    for line in changelog.splitlines():
        if line.startswith("## "):
            if inside:
                break
            inside = re.match(r"## %s(\s|$)" % re.escape(version), line) is not None
            continue
        if inside:
            lines.append(line)
    if not inside and not lines:
        return None
    blocks, para, items = [], [], []

    def flush():
        if para:
            blocks.append("<p>%s</p>" % inline(" ".join(para)))
            del para[:]
        if items:
            blocks.append("<ul>%s</ul>" % "".join("<li>%s</li>" % inline(" ".join(i)) for i in items))
            del items[:]
    for line in lines:
        if not line.strip():
            flush()
        elif line.startswith("- "):
            if para:
                flush()
            items.append([line[2:].strip()])
        elif line.startswith("  ") and items:
            items[-1].append(line.strip())
        else:
            if items:
                flush()
            para.append(line.strip())
    flush()
    return "\n".join(blocks) or None


def item(a, notes_html, date):
    tag = "v" + a.version
    lines = [
        "    <item>",
        "      <title>Sukrara %s</title>" % a.version,
        "      <pubDate>%s</pubDate>" % date,
        "      <sparkle:version>%s</sparkle:version>" % a.build,
        "      <sparkle:shortVersionString>%s</sparkle:shortVersionString>" % a.version,
        "      <sparkle:minimumSystemVersion>%s</sparkle:minimumSystemVersion>" % a.min_os,
    ]
    if a.kind == "prerelease":
        lines.append("      <sparkle:channel>%s</sparkle:channel>" % PRERELEASE_CHANNEL)
    lines.append("      <sparkle:fullReleaseNotesLink>%s/releases/tag/%s</sparkle:fullReleaseNotesLink>" % (REPO, tag))
    if notes_html:
        lines.append("      <description><![CDATA[%s]]></description>" % notes_html.replace("]]>", "]]&gt;"))
    lines += [
        '      <enclosure url="%s/releases/download/%s/Sukrara-%s.dmg" length="%d" type="application/octet-stream"'
        % (REPO, tag, a.version, a.length),
        '        sparkle:edSignature="%s"/>' % a.signature,
        "    </item>",
    ]
    return "\n".join(lines) + "\n"


def main(argv):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--version", required=True)
    p.add_argument("--build", required=True)
    p.add_argument("--kind", choices=["prerelease", "release"], required=True)
    p.add_argument("--signature", required=True)
    p.add_argument("--length", type=int, required=True)
    p.add_argument("--min-os", default="15.0")
    p.add_argument("--feed", default=os.path.join(ROOT, "docs/appcast.xml"))
    p.add_argument("--changelog", default=os.path.join(ROOT, "CHANGELOG.md"))
    p.add_argument("--date", help="RFC 2822 date (default: now)")
    a = p.parse_args(argv)
    if not re.fullmatch(r"\d+\.\d+\.\d+", a.version) or not re.fullmatch(r"\d+", a.build):
        sys.exit("appcast.py: version x.y.z and a numeric build, got %s (%s)" % (a.version, a.build))
    if not re.fullmatch(r"[A-Za-z0-9+/]{86}==", a.signature) or a.length <= 0:
        sys.exit("appcast.py: --signature must be sign_update's base64 EdDSA signature and --length the dmg's size")
    with open(a.feed, encoding="utf-8") as f:
        feed = f.read()
    try:
        with open(a.changelog, encoding="utf-8") as f:
            notes_html = notes(f.read(), a.version)
    except OSError:
        notes_html = None
    # the same version's item goes (a release made again), the new one goes first
    feed = re.sub(r"    <item>\n      <title>Sukrara %s</title>\n.*?    </item>\n" % re.escape(a.version), "", feed,
                  flags=re.S)
    anchor = "  </channel>"
    first = feed.find("    <item>")
    at = first if first >= 0 else feed.find(anchor)
    if at < 0:
        sys.exit("appcast.py: %s has no <channel>" % a.feed)
    date = a.date or email.utils.formatdate(usegmt=True)
    feed = feed[:at] + item(a, notes_html, date) + feed[at:]
    with open(a.feed, "w", encoding="utf-8") as f:
        f.write(feed)
    print("%s: Sukrara %s (%s, %s%s)" % (os.path.relpath(a.feed, ROOT), a.version, a.build, a.kind,
                                        ", notes from CHANGELOG.md" if notes_html else ", no CHANGELOG.md section"))


if __name__ == "__main__":
    main(sys.argv[1:])
