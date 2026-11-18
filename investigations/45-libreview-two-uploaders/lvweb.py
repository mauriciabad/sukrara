#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Investigation 45b: the website login a reader of the Daily Log report would need (docs/45).

  python3 -I lvweb.py login     the website login (`POST /auth/login`, product lv), as lvconnect and libre-desktop
                                sign in; prints the answer's shape (status, the step it asks for), never a token

The report itself ran in a signed-in libreview.com tab: lvreport.js.
Read-only: the website is another product than the app API, so its login ends no upload session (docs/45).
Credentials: the login keychain item `dev.spike.libreview`, read only at sign-in. Nothing is stored.
"""
import datetime as dt
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request

API = "https://api.libreview.io"
UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0 Safari/537.36"


def stamp():
    return dt.datetime.now().astimezone().strftime("%H:%M:%S")


def credentials():
    meta = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview"],
                          capture_output=True, text=True, check=True).stdout
    email = next(ln.split("=", 1)[1].strip().strip('"') for ln in meta.splitlines() if '"acct"' in ln)
    pw = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview", "-a", email, "-w"],
                        capture_output=True, text=True, check=True).stdout.strip()
    if not email or not pw:
        raise SystemExit("no credentials")
    return email, pw


def post(url, body):
    if not re.match(r"^https://api(-[a-z0-9]+)?\.libreview\.io/", url):
        raise SystemExit("refusing " + url)
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST", headers={
        "User-Agent": UA, "Accept": "application/json", "Content-Type": "application/json", "product": "lv"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, {"text": e.read().decode(errors="replace")[:200]}


def cmd_login():
    email, pw = credentials()
    base = API
    for _ in range(3):
        code, o = post(base + "/auth/login", {"email": email, "password": pw})
        d = (o or {}).get("data") or {}
        if (o or {}).get("status") == 0 and d.get("redirect") and re.fullmatch(r"[a-z0-9]{2,4}", d.get("region", "")):
            base = "https://api-%s.libreview.io" % d["region"]
            continue
        break
    pw = None
    step = d.get("step") or {}
    print(stamp(), "login HTTP", code, "status", (o or {}).get("status"), "at", base.split("/")[2], "data keys", sorted(d))
    if step:
        props = step.get("props") or {}
        print("  step", step.get("type"), step.get("componentName"), "primaryMethod", props.get("primaryMethod"),
              "secondaryMethod", props.get("secondaryMethod") or "-")


if __name__ == "__main__":
    if sys.argv[1:] == ["login"]:
        cmd_login()
    else:
        raise SystemExit(__doc__)
