#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""A local test release for the Android GitHub version's updater (#188): no GitHub release is ever involved.

    python3 tools/updates/serve.py <apk> [--version 0.13.1] [--port 8765] [--base http://10.0.2.2:8765/]

Serves, on this computer's loopback only, what the updater reads from GitHub: `<base>releases` in the form of GitHub's
List-releases API (one prerelease `v<version>` whose asset `Sukrara-<version>.apk` carries the file's `sha256:` digest
and size), the APK under `<base>download/`, and a plain release page under `<base>releases/tag/`. The version defaults
to the APK's own (aapt2 badging). Then point a debug build at it (an emulator reaches 127.0.0.1 as 10.0.2.2); the
updater trusts the debug key there:

    adb -s emulator-5584 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \\
        -a app.mauri.sukrara.debug.UPDATES --es server http://10.0.2.2:8765/

`--digest` lists another SHA-256 than the file's (the updater must refuse the download). Python 3.9+ standard library.
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def apk_version(apk: str) -> str | None:
    """The APK's versionName, read with the newest aapt2 of the Android SDK (None without one)."""
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
    tools = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")))
    if not tools:
        return None
    out = subprocess.run([tools[-1], "dump", "badging", apk], capture_output=True, text=True).stdout
    for part in out.splitlines()[0].split() if out else []:
        if part.startswith("versionName="):
            return part.split("=", 1)[1].strip("'")
    return None


def releases(base: str, version: str, digest: str, size: int) -> bytes:
    name = "Sukrara-%s.apk" % version
    return json.dumps([{
        "tag_name": "v" + version,
        "html_url": "%sreleases/tag/v%s" % (base, version),
        "draft": False,
        "prerelease": True,
        "assets": [{"name": name, "browser_download_url": base + "download/" + name, "digest": "sha256:" + digest, "size": size}],
    }]).encode()


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("apk")
    p.add_argument("--version")
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--base", help="the server as the app sees it (default http://10.0.2.2:<port>/)")
    p.add_argument("--digest", help="list this SHA-256 instead of the file's")
    a = p.parse_args()
    data = open(a.apk, "rb").read()
    version = a.version or apk_version(a.apk)
    if not version:
        sys.exit("can't read the APK's version: pass --version")
    base = a.base or "http://10.0.2.2:%d/" % a.port
    digest = a.digest or hashlib.sha256(data).hexdigest()
    listing = releases(base, version, digest, len(data))
    apk_path = "/download/Sukrara-%s.apk" % version

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 (http.server's name)
            if self.path == "/releases":
                self.reply(200, "application/json", listing)
            elif self.path == apk_path:
                self.reply(200, "application/vnd.android.package-archive", data)
            elif self.path.startswith("/releases/tag/"):
                self.reply(200, "text/html; charset=utf-8", ("<h1>Sukrara %s</h1><p>A local test release.</p>" % version).encode())
            else:
                self.reply(404, "text/plain", b"not here")

        def reply(self, code: int, kind: str, body: bytes) -> None:
            self.send_response(code)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    print("serving v%s (%d bytes, sha256 %s) at %s; the app reads %sreleases" % (version, len(data), digest, a.host, base), flush=True)
    ThreadingHTTPServer((a.host, a.port), Handler).serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
