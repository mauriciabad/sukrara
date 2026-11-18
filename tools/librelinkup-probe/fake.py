#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""A fake LibreLinkUp for the emulator (docs/QA.md Q30): synthetic data only, never a real account.

    python3 tools/librelinkup-probe/fake.py [--port 8766]
    adb -s emulator-5554 reverse tcp:8766 tcp:8766
    adb -s emulator-5554 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \\
        -a app.mauri.sukrara.debug.LIBRELINKUP --es server http://127.0.0.1:8766

Listens on this computer's loopback only and answers what Sukrara's LibreLinkUp client asks (login, connections, graph)
in LibreLinkUp's shapes (contracts/sources/librelinkup): any email signs in with any password but `wrong`. The glucose
is the debug SYNTHETIC source's curve (130 ± 85 mg/dL over 3 hours, a reading each minute), so a takeover simulated with
`SYNTHETIC` lands on the same values. Every request is logged (time, path, the version header, the answer).

Change what it answers while it runs, `curl 'http://127.0.0.1:8766/_fake?key=value&…'` (`/_fake` alone shows them):
  login      ok | bad (every password refused) | terms | ratelimit | redirect (once, then ok) | version (403 below
             `minimum`) | retired | down
  calls      ok | expired (401 once: tokens issued so far stop) | terms | ratelimit | version | retired | down
  people     yours (the account itself) | other (one other person) | several | none | yours+other
  minimum    the version the `version` answers ask for (default 4.18.0)
  lag        seconds after a reading LibreLinkUp has it (default 4: the owner's test 2026-11-18)
  stopped    1 = no new readings from now on (the Libre app stopped uploading); 0 = they come again
  sensor     new = a new sensor (new serial, started 70 min ago); or `<serial>,<minutes since start>`
  pt         the sensor's `pt` in the answers (default 4)
  ttl        seconds a new token lasts (default 15552000, LibreLinkUp's 180 days)
  retryafter seconds of the 429's Retry-After header (default none: the lockout in the body says 300)
  dtid       the device type of the app that uploads (default 40068, the Libre 3 app; 40066 / 40067 LibreLink)
  lvlogin    ok | bad (LibreView's app sign-in refuses the password)
  lvother    1 = another device (an older install) is the LibreLink identity's active one: Take over; 0 = none
  lvexpire   1 = every LibreView token stops working (the app signs in again)
  lvmerge    1 = LibreView ends the official app's session on a sign-in under either identity (the watchdog's case)

It also serves LibreView's app API under /lv/ (the debug build's LibreView upload goes here too, docs/45): one active
device and one session per app identity (the Libre 3 app, LibreLink), the official app holding the one `dtid` names. A
sign-in that gets a token on the official app's identity stops its upload as the real one did: LibreLinkUp gets no
new readings (`stopped=1`) until `stopped=0` (the person signed the Libre app out and in). `/_fake` lists the sign-ins
and the uploads per identity.
Python 3.9+ standard library.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ACCOUNT = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
PEOPLE = {
    ACCOUNT: ("Alex", "Example"),
    "11111111-2222-4333-8444-555555555555": ("Ana", "Example"),
    "66666666-7777-4888-9999-aaaaaaaaaaaa": ("Leo", "Example"),
}
CHOICES = {
    "yours": [ACCOUNT],
    "other": ["11111111-2222-4333-8444-555555555555"],
    "several": ["11111111-2222-4333-8444-555555555555", "66666666-7777-4888-9999-aaaaaaaaaaaa"],
    "none": [],
    "yours+other": [ACCOUNT, "11111111-2222-4333-8444-555555555555"],
}

STATE = {
    "login": "ok",
    "calls": "ok",
    "people": "yours",
    "minimum": "4.18.0",
    "lag": 4,
    "stopped": 0,
    "stoppedAt": None,
    "serial": "0D00SYN0A1",
    "sensorStart": int(time.time()) - 3 * 86400,
    "pt": 4,
    "ttl": 15552000,
    "retryafter": None,
    "redirected": False,
    "dtid": 40068,
    "lvlogin": "ok",
    "lvmerge": 0,
}
TOKENS: dict[str, int] = {}  # token -> expires (epoch s)

# The fake LibreView app API (docs/45): per app identity one active device and one live session; the official app holds
# the identity its dtid says, and any sign-in that gets a token there ends its session: LibreLinkUp stops getting
# readings (`stopped`), as on 2026-11-18. `lvother=1`: another device (an older install) is LibreLink's active one.
GATEWAYS = {"FSLibreLink3.Android": "libre3", "FSLibreLink.Android": "librelink"}
OFFICIAL = {40068: "libre3", 40066: "librelink", 40067: "librelink"}
LV = {"active": {"libre3": "official-app-device", "librelink": None}, "token": {"libre3": None, "librelink": None},
      "uploads": {"libre3": 0, "librelink": 0}, "entries": {"libre3": 0, "librelink": 0}, "signins": []}


def curve(t: int) -> tuple[int, float]:
    """The debug SYNTHETIC source's curve at epoch second [t]: (mg/dL, mg/dL per minute)."""
    phase = 2 * math.pi * (t / 60.0) / 180.0
    return round(130 + 85 * math.sin(phase)), 85 * 2 * math.pi / 180.0 * math.cos(phase)


def arrow(rate: float) -> int:
    return 1 if rate <= -2 else 2 if rate <= -1 else 3 if rate < 1 else 4 if rate < 2 else 5


def stamp(t: int) -> str:
    """LibreLinkUp's FactoryTimestamp: UTC, M/d/yyyy h:mm:ss AM."""
    d = datetime.fromtimestamp(t, timezone.utc)
    return f"{d.month}/{d.day}/{d.year} {d.hour % 12 or 12}:{d.minute:02d}:{d.second:02d} {'AM' if d.hour < 12 else 'PM'}"


def measurement(t: int, live: bool) -> dict:
    v, rate = curve(t)
    m = {"FactoryTimestamp": stamp(t), "Timestamp": stamp(t + 3600), "type": 1 if live else 0, "ValueInMgPerDl": v,
         "MeasurementColor": 1, "GlucoseUnits": 1, "Value": v, "isHigh": v > 250, "isLow": v < 70}
    if live:
        m.update({"TrendArrow": arrow(rate), "TrendMessage": None})
    return m


def newest() -> int:
    """The newest reading's time: a minute's reading [lag] seconds behind now (or when it stopped), seconds :17."""
    now = int(time.time())
    if STATE["stopped"]:
        now = STATE["stoppedAt"] or now
    t = now - int(STATE["lag"])
    return t - t % 60 + 17


def sensor() -> dict:
    return {"deviceId": "", "sn": STATE["serial"], "a": STATE["sensorStart"], "w": 60, "pt": int(STATE["pt"]), "s": False,
            "lj": False}


def connection(pid: str, live: bool = True) -> dict:
    first, last = PEOPLE[pid]
    c = {"id": "c-" + pid[:8], "patientId": pid, "country": "DE", "status": 2, "firstName": first, "lastName": last,
         "targetLow": 70, "targetHigh": 180, "uom": 1, "sensor": sensor(),
         "patientDevice": {"did": "synthetic-device", "dtid": int(STATE["dtid"]), "v": "3.6.5", "ll": 70, "hl": 250, "u": 0,
                           "fixedLowAlarmValues": {"mgdl": 60, "mmoll": 3.3}, "alarms": False},
         "created": STATE["sensorStart"]}
    if live:
        c["glucoseMeasurement"] = measurement(newest(), True)
        c["glucoseItem"] = c["glucoseMeasurement"]
    return c


def ticket(token: str) -> dict:
    return {"token": token, "expires": TOKENS.get(token, 0), "duration": int(STATE["ttl"]) * 1000}


def version_ok(header: str | None) -> bool:
    def parts(v):
        try:
            return [int(x) for x in (v or "0").split(".")]
        except ValueError:
            return [0]
    return parts(header) >= parts(STATE["minimum"])


class Handler(BaseHTTPRequestHandler):
    server_version = "fake-librelinkup"

    def log_message(self, fmt, *args):
        pass

    def send(self, code: int, body: dict, headers: dict | None = None):
        raw = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        for k, v in (headers or {}).items():
            self.send_header(k, str(v))
        self.end_headers()
        self.wfile.write(raw)
        auth = "token" if self.headers.get("Authorization") else "-"
        print(f"{time.strftime('%H:%M:%S')} {self.command} {self.path.split('?')[0]} version={self.headers.get('version')} "
              f"{auth} -> {code} {json.dumps(body)[:90]}", flush=True)

    def rate_limited(self):
        h = {"Retry-After": STATE["retryafter"]} if STATE["retryafter"] else {}
        self.send(429, {"status": 429, "data": {"code": 60, "data": {"failures": 3, "interval": 60, "lockout": 300},
                                                 "message": "locked"}}, h)

    def base(self) -> str:
        return "http://" + (self.headers.get("Host") or "127.0.0.1")

    def lv(self, method: str, path: str, body: dict):
        """LibreView's app API (docs/45), synthetic: sign-in, upload, sensor registration."""
        if path == "/lv/assets" and method == "GET":
            return self.send(200, {"Configuration": self.base() + "/lv/libre3-config"})
        if path == "/lv/libre3-config" and method == "GET":
            return self.send(200, {"newYuUrl": self.base() + "/lv", "newYuApiKey": "synthetic"})
        if path == "/lv/librelink-config" and method == "GET":
            return self.send(200, {"newYuUrl": self.base() + "/lv"})
        ident = GATEWAYS.get(body.get("GatewayType"))
        if path == "/lv/api/nisperson/getauthentication" and method == "POST" and ident:
            device = body.get("DeviceId")
            LV["signins"].append(f"{time.strftime('%H:%M:%S')} {ident} {str(device)[:8]} take-over={body.get('SetDevice')}")
            if STATE["lvlogin"] == "bad" or body.get("Password") == "wrong":
                return self.send(200, {"status": 2, "reason": "notAuthenticated"})
            active = LV["active"][ident]
            if active and active != device and not body.get("SetDevice"):
                return self.send(200, {"status": 20, "reason": "wrongDeviceForUser"})
            LV["active"][ident] = device
            LV["token"][ident] = "lv." + uuid.uuid4().hex
            if (OFFICIAL.get(int(STATE["dtid"])) == ident or int(STATE["lvmerge"])) and not STATE["stopped"]:
                # the official app's session just ended: its upload stops silently (docs/45, 35 min)
                STATE["stopped"], STATE["stoppedAt"] = 1, int(time.time())
                print(f"{time.strftime('%H:%M:%S')} !!! a sign-in as {ident} ended the official app's session: its upload "
                      "stopped", flush=True)
            return self.send(200, {"status": 0, "result": {"UserToken": LV["token"][ident], "AccountId": ACCOUNT,
                                                         "DateOfBirth": "19900101", "FirstName": "Alex",
                                                         "LastName": "Example", "GuardianFirstName": "",
                                                         "GuardianLastName": ""}})
        if path in ("/lv/api/measurements", "/lv/api/nisperson") and ident:
            token = body.get("UserToken")
            if not token or token != LV["token"][ident]:
                return self.send(200, {"status": 20, "reason": "wrongDeviceInToken"})
            if path == "/lv/api/measurements":
                log = ((body.get("DeviceData") or {}).get("measurementLog") or {})
                n = sum(len(log.get(k) or []) for k in ("insulinEntries", "foodEntries", "genericEntries"))
                g = sum(len(log.get(k) or []) for k in ("scheduledContinuousGlucoseEntries", "currentGlucoseEntries"))
                LV["uploads"][ident] += 1
                LV["entries"][ident] += n
                print(f"{time.strftime('%H:%M:%S')} LibreView upload as {ident}: {n} entries, {g} glucose", flush=True)
            return self.send(200, {"status": 0, "result": {}})
        return self.send(404, {})

    def body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            return {}

    def do_PUT(self):
        path = urlparse(self.path).path
        return self.lv("PUT", path, self.body()) if path.startswith("/lv/") else self.send(404, {})

    def do_POST(self):
        body = self.body()
        path = urlparse(self.path).path
        if path.startswith("/lv/"):
            return self.lv("POST", path, body)
        if path != "/llu/auth/login":
            return self.send(404, {})
        mode = STATE["login"]
        if mode == "down":
            return self.send(500, {})
        if mode == "retired":
            return self.send(404, {})
        if mode == "ratelimit":
            return self.rate_limited()
        if mode == "version" and not version_ok(self.headers.get("version")):
            return self.send(403, {"status": 920, "data": {"minimumVersion": STATE["minimum"]}})
        if mode == "redirect" and not STATE["redirected"]:
            STATE["redirected"] = True
            return self.send(200, {"status": 0, "data": {"redirect": True, "region": "eu"}})
        if body.get("password") == "wrong" or mode == "bad":
            return self.send(200, {"status": 2, "error": {"message": "notAuthenticated"}})
        token = "synthetic." + uuid.uuid4().hex
        TOKENS[token] = int(time.time()) + int(STATE["ttl"])
        user = {"id": ACCOUNT, "firstName": "Alex", "lastName": "Example", "email": body.get("email"), "country": "DE"}
        if mode == "terms":
            return self.send(200, {"status": 4, "data": {"step": {"type": "tou", "componentName": "AcceptDocument"},
                                                         "user": user, "authTicket": ticket(token)}})
        return self.send(200, {"status": 0, "data": {"user": user, "authTicket": ticket(token), "invitations": None}})

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/_fake":
            return self.control(parse_qs(url.query))
        if url.path.startswith("/lv/"):
            return self.lv("GET", url.path, {})
        token = (self.headers.get("Authorization") or "").removeprefix("Bearer ").strip()
        mode = STATE["calls"]
        if token not in TOKENS or TOKENS[token] < time.time():
            return self.send(401, {"message": "Unauthorized"})
        if mode == "expired":
            TOKENS.clear()
            STATE["calls"] = "ok"
            return self.send(401, {"message": "Unauthorized"})
        if mode == "down":
            return self.send(503, {})
        if mode == "retired":
            return self.send(403, {"status": 403, "message": "Forbidden"})
        if mode == "ratelimit":
            return self.rate_limited()
        if mode == "version" and not version_ok(self.headers.get("version")):
            return self.send(403, {"status": 920, "data": {"minimumVersion": STATE["minimum"]}})
        if mode == "terms":
            return self.send(200, {"status": 4, "data": {"step": {"type": "tou"}}})
        shared = CHOICES[STATE["people"]]
        if url.path == "/llu/connections":
            return self.send(200, {"status": 0, "data": [connection(p) for p in shared], "ticket": ticket(token)})
        parts = url.path.split("/")
        if len(parts) == 5 and parts[1:3] == ["llu", "connections"] and parts[4] == "graph":
            pid = parts[3]
            if pid not in shared:
                return self.send(403, {"status": 403, "message": "Forbidden"})
            last = newest()
            first = last - 12 * 3600
            points = [t for t in range(first - first % 300 + 300, last - 120, 300) if t >= STATE["sensorStart"] + 3600]
            live = last >= STATE["sensorStart"] + 3600
            data = {"connection": connection(pid, live), "activeSensors": [{"sensor": sensor(), "device": {
                "did": "synthetic-device", "dtid": int(STATE["dtid"]), "v": "3.6.5"}}], "graphData": [measurement(t, False) for t in points]}
            return self.send(200, {"status": 0, "data": data, "ticket": ticket(token)})
        return self.send(404, {})

    def control(self, q: dict):
        now = int(time.time())
        for k, (v, *_) in q.items():
            if k == "sensor":
                if v == "new":
                    STATE["serial"], STATE["sensorStart"] = "0D00SYN" + uuid.uuid4().hex[:3].upper(), now - 70 * 60
                else:
                    serial, minutes = v.split(",")
                    STATE["serial"], STATE["sensorStart"] = serial, now - int(minutes) * 60
            elif k == "stopped":
                STATE["stopped"] = int(v)
                STATE["stoppedAt"] = now if int(v) else None
            elif k == "login":
                STATE["login"] = v
                STATE["redirected"] = False
            elif k == "lvexpire":
                LV["token"] = {"libre3": None, "librelink": None}
            elif k == "lvother":
                LV["active"]["librelink"] = "older-install" if int(v) else None
            elif k in ("lag", "pt", "ttl", "retryafter", "dtid", "lvmerge"):
                STATE[k] = int(v) if v not in ("", "none") else None
            elif k in STATE:
                STATE[k] = v
            else:
                return self.send(400, {"unknown": k})
        return self.send(200, {**STATE, "newest": stamp(newest()), "tokens": len(TOKENS),
                               "libreview": {k: v for k, v in LV.items() if k != "token"}})


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8766)
    args = ap.parse_args()
    httpd = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"fake LibreLinkUp on http://127.0.0.1:{args.port} (synthetic; Ctrl-C stops it)", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
