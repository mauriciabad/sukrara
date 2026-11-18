#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only LibreLinkUp probe: what Sukrara's LibreLinkUp source (docs/44 step 2) will see on YOUR account.

    python3 -I tools/librelinkup-probe/probe.py                 # asks for the email and password, writes probe-<time>.json
    python3 -I tools/librelinkup-probe/probe.py --out my.json   # another file name
    python3 -I tools/librelinkup-probe/probe.py --sanitise my.json fixture.json

What it does, with the same headers and version as the apps (contracts/sources/librelinkup/poll.json):
  1. POST /llu/auth/login (and again at your region's host after a redirect),
  2. GET /llu/connections (the people the account follows),
  3. GET /llu/connections/<patientId>/graph for each of them.
Read-only: it never accepts terms, never changes a setting, never uploads. One login per run (each login counts towards
LibreLinkUp's lockout: don't run it in a loop). The password is asked on the terminal (not echoed), used for the login
and forgotten; nothing is stored anywhere but the output file, which never contains the password or the token.

The output file holds YOUR glucose, names and ids: keep it local and send only the --sanitise result. --sanitise
replaces every name, email, id, serial and token with synthetic ones (the times and values stay: they are what the
fixtures are about; the owner's readings aren't sensitive, but names and ids are), ready for contracts/sources/librelinkup.
"""
import argparse
import getpass
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.request

HOST = "api.libreview.io"
HEADERS = {
    "product": "llu.android",
    "version": "4.17.0",
    "Accept": "application/json",
    "Content-Type": "application/json",
    "cache-control": "no-cache",
}
SECRET_KEYS = {"token", "password", "email"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # the apps never follow redirects either
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def call(method, host, path, body=None, extra=None):
    req = urllib.request.Request(f"https://{host}{path}", method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={**HEADERS, **(extra or {})})
    try:
        with OPENER.open(req, timeout=30) as r:
            code, text, retry = r.status, r.read().decode("utf-8", "replace"), r.headers.get("Retry-After")
    except urllib.error.HTTPError as e:
        code, text, retry = e.code, e.read().decode("utf-8", "replace"), e.headers.get("Retry-After")
    try:
        parsed = json.loads(text)
    except ValueError:
        parsed = {"_notJson": text[:200]}
    return {"method": method, "path": path, "host": host, "code": code, "retryAfter": retry, "body": parsed}


def scrub_secrets(o):
    """The token (and anything named like a credential) never reaches the file."""
    if isinstance(o, dict):
        return {k: ("<removed>" if k in SECRET_KEYS else scrub_secrets(v)) for k, v in o.items()}
    if isinstance(o, list):
        return [scrub_secrets(v) for v in o]
    return o


def probe(out):
    email = input("LibreLinkUp email: ").strip()
    password = getpass.getpass("Password (not shown, not stored): ")
    log = {"_about": "LibreLinkUp probe answers (raw, personal: keep local; send --sanitise output)",
           "at": int(time.time()), "calls": []}
    host = HOST
    login = None
    for _ in range(2):
        r = call("POST", host, "/llu/auth/login", {"email": email, "password": password})
        data = r["body"].get("data") if isinstance(r["body"], dict) else None
        token = ((data or {}).get("authTicket") or {}).get("token")
        uid = ((data or {}).get("user") or {}).get("id")
        log["calls"].append(scrub_secrets(r))
        if isinstance(data, dict) and data.get("redirect") and re.fullmatch(r"[a-z]{2,3}", str(data.get("region"))):
            host = f"api-{data['region']}.libreview.io"
            continue
        login = (token, uid)
        break
    password = None  # forgotten
    token, uid = login or (None, None)
    if not token or not uid:
        print(f"No sign-in (HTTP {log['calls'][-1]['code']}, status {log['calls'][-1]['body'].get('status')}). "
              "Status 2 = wrong email or password, 4 = accept the new terms in the LibreLinkUp app first.")
    else:
        auth = {"Authorization": f"Bearer {token}", "Account-Id": hashlib.sha256(uid.encode()).hexdigest()}
        c = call("GET", host, "/llu/connections", extra=auth)
        log["calls"].append(scrub_secrets(c))
        people = c["body"].get("data") if isinstance(c["body"], dict) else None
        for p in people or []:
            pid = p.get("patientId")
            if pid:
                log["calls"].append(scrub_secrets(call("GET", host, f"/llu/connections/{pid}/graph", extra=auth)))
        log["accountFollowsItself"] = any(p.get("patientId") == uid for p in people or [])
        print(f"Signed in at {host}; the account follows {len(people or [])} people "
              f"({'itself among them' if log['accountFollowsItself'] else 'not itself'}).")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(log, f, ensure_ascii=False, indent=1)
    print(f"Wrote {out}: personal data, keep it local. Send: probe.py --sanitise {out} sanitised.json")


ID = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")


def sanitise(src, dst):
    raw = json.load(open(src, encoding="utf-8"))
    ids = {}

    def fake_id(v):
        if v not in ids:
            n = len(ids) + 1
            ids[v] = f"{n:08x}-0000-4000-8000-{n:012x}"
        return ids[v]

    def walk(o, key=None):
        if isinstance(o, dict):
            return {k: walk(v, k) for k, v in o.items()}
        if isinstance(o, list):
            return [walk(v, key) for v in o]
        if isinstance(o, str):
            if key in ("firstName",):
                return "Alex"
            if key in ("lastName",):
                return "Example"
            if key == "sn":
                return "0D00SYNTH" + str(len(ids) % 10)
            if key in ("email", "username", "phone", "dateOfBirth"):
                return "<removed>"
            if key in ("did", "deviceId"):
                return "synthetic-device"
            return ID.sub(lambda m: fake_id(m.group(0)), o)
        return o

    out = walk(raw)
    for c in out.get("calls", []):
        c["host"] = re.sub(r"api-[a-z]{2,3}\.", "api-xx.", c["host"])
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    text = json.dumps(out)
    left = [v for v in ids if v in text]
    print(f"Wrote {dst} ({len(ids)} ids replaced{', CHECK: some ids remain' if left else ''}). Read it before sending.")


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--out", default=f"probe-{time.strftime('%Y%m%d-%H%M%S')}.json")
    a.add_argument("--sanitise", nargs=2, metavar=("RAW", "OUT"))
    args = a.parse_args()
    if args.sanitise:
        sanitise(*args.sanitise)
    else:
        probe(args.out)


if __name__ == "__main__":
    sys.exit(main())
