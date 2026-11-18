# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Writes the LibreLinkUp source fixtures (python3 -I generate.py): login.json, calls.json, connections.json,
# graph.json, poll.json.
# The answers are synthetic, shaped like LibreLinkUp's (field names from GlucoDataHandler, xDrip4iOS and
# nightscout-librelink-up; every id, name and token here is made up). The rules below are THE reference both apps
# implement; change a rule or a case here, run it, review the diff. tools/librelinkup-probe turns a real account's
# answers into sanitised bodies that can be added as cases.
import json
import os
import re
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
NOW = 1795003200  # 2026-11-18 12:00:00 UTC
ACCOUNT = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"  # the signed-in account (yours, when you follow yourself)
ANA = "11111111-2222-4333-8444-555555555555"
LEO = "66666666-7777-4888-9999-aaaaaaaaaaaa"
TOKEN = "eyJ.synthetic.token"

ARROW_RATE = {1: -2.5, 2: -1.5, 3: 0.0, 4: 1.5, 5: 2.5}

VERSION = "4.17.0"  # the LibreLinkUp version this build sends (poll.json 'headers')
# a version LibreLinkUp may ask for: it goes into a header as it is, so anything else is no version
VERSION_FORMAT = re.compile(r"\d{1,3}(\.\d{1,4}){1,3}")

POLL = {
    "cadence_s": 60,
    "margin_s": 8,
    "margin_up_s": 5,
    "margin_hold": 10,
    "margin_max_s": 40,
    "min_s": 45,
    "backoff_s": [60, 120, 300, 600],
    "rateLimited_min_s": 600,
    "renewBefore_s": 300,
    "connectionsEvery_s": 1800,
    "terms_s": 600,
    "retired_s": 3600,
}


def dump(name, obj):
    with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


def stamp(s):
    """LibreLinkUp's FactoryTimestamp: UTC, US-style, 12-hour clock, no leading zeros on month, day and hour."""
    d = datetime.fromtimestamp(s, timezone.utc)
    hour = d.hour % 12 or 12
    return f"{d.month}/{d.day}/{d.year} {hour}:{d.minute:02d}:{d.second:02d} {'AM' if d.hour < 12 else 'PM'}"


def local_stamp(s, offset_h):
    return stamp(s + offset_h * 3600)


# ------------------------------------------------------------------ the rules (THE reference)

def parse_time(text):
    m = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{4}) (\d{1,2}):(\d{2}):(\d{2}) (AM|PM)", (text or "").strip())
    if not m:
        return None
    mo, d, y, h, mi, s, ap = m.groups()
    h = int(h) % 12 + (12 if ap == "PM" else 0)
    try:
        return int(datetime(int(y), int(mo), int(d), h, int(mi), int(s), tzinfo=timezone.utc).timestamp())
    except ValueError:
        return None


def minimum_version(data):
    """A 403's minimumVersion, when it is a version (never a header's line break or anything else)."""
    v = data.get("minimumVersion") if isinstance(data, dict) else None
    return isinstance(v, str) and VERSION_FORMAT.fullmatch(v) is not None


def newer(a, b):
    """Whether version a is newer than b, parts compared as numbers (4.17.0 > 4.9.9)."""
    x = [int(p) if p.isdigit() else 0 for p in a.split(".")]
    y = [int(p) if p.isdigit() else 0 for p in b.split(".")]
    n = max(len(x), len(y))
    return (x + [0] * (n - len(x))) > (y + [0] * (n - len(y)))


def send(asked):
    """The version every call sends: one LibreLinkUp asked for (kept with the account), unless this build's is newer."""
    return asked if asked is not None and newer(asked, VERSION) else VERSION


def login(code, body, retry_after=None):
    """One answer of POST /llu/auth/login."""
    data = body.get("data") if isinstance(body, dict) else None
    status = body.get("status") if isinstance(body, dict) else None
    if code == 429 or status == 429:
        lockout = None
        if isinstance(data, dict) and isinstance(data.get("data"), dict):
            lockout = data["data"].get("lockout")
        return {"answer": "rateLimited", "retryAfter_s": retry_after if retry_after is not None else lockout}
    if code == 403 and minimum_version(data):
        return {"answer": "version", "minimumVersion": data["minimumVersion"]}
    if code == 401:
        return {"answer": "badCredentials"}
    if code == 404:
        return {"answer": "retired"}
    if code != 200 or not isinstance(body, dict):
        return {"answer": "failed"}
    if status == 0 and isinstance(data, dict) and data.get("redirect") in (True, "true"):
        region = data.get("region")
        if isinstance(region, str) and re.fullmatch(r"[a-z]{2,3}", region):
            return {"answer": "redirect", "region": region}
        return {"answer": "failed"}
    if status == 0 and isinstance(data, dict):
        user = data.get("user") or {}
        ticket = data.get("authTicket") or {}
        uid = user.get("id")
        token = ticket.get("token")
        if isinstance(uid, str) and len(uid) == 36 and token:
            return {"answer": "account", "accountId": uid, "token": token, "expires": int(ticket.get("expires") or 0)}
        return {"answer": "failed"}
    if status == 2:
        return {"answer": "badCredentials"}
    if status == 4:
        step = (data or {}).get("step") or {}
        return {"answer": "terms", "step": step.get("type")}
    return {"answer": "failed"}


def call(code, body, retry_after=None):
    """One answer of an authenticated GET (connections, graph): how the source's health reads it."""
    data = body.get("data") if isinstance(body, dict) else None
    status = body.get("status") if isinstance(body, dict) else None
    if code == 429 or status == 429:
        return {"outcome": "rateLimited", "retryAfter_s": retry_after}
    if code == 401:
        return {"outcome": "expired"}
    if code == 403 and minimum_version(data):
        return {"outcome": "version", "minimumVersion": data["minimumVersion"]}
    if code in (403, 404, 410):
        return {"outcome": "retired"}
    if code != 200 or not isinstance(body, dict):
        return {"outcome": "server"}
    if status == 4:
        return {"outcome": "terms"}
    if status != 0 or "data" not in body:
        return {"outcome": "server"}
    return {"outcome": "ok"}


def name_of(c):
    return " ".join(x for x in [c.get("firstName"), c.get("lastName")] if x).strip()


def pick(connections, account, person, saved):
    choices = [{"id": c["patientId"], "name": name_of(c)} for c in connections if c.get("patientId")]
    ids = [c["id"] for c in choices]
    out = {"choices": choices}
    if not ids:
        return {**out, "state": "none", "pick": None}
    if saved:
        return {**out, "state": "picked", "pick": saved} if saved in ids else {**out, "state": "gone", "pick": None}
    if person is None:
        candidates = ids  # the Mac: every connection, the account's own included
    elif person == "yours":
        if account in ids:
            return {**out, "state": "picked", "pick": account}
        candidates = ids
    else:
        candidates = [i for i in ids if i != account] or ids
    if len(candidates) == 1:
        return {**out, "state": "picked", "pick": candidates[0]}
    return {**out, "state": "choose", "pick": None}


def reading(m, source, live):
    t = parse_time(m.get("FactoryTimestamp"))
    v = m.get("ValueInMgPerDl")
    if t is None or not isinstance(v, (int, float)) or v <= 0:
        return None
    rate = ARROW_RATE.get(m.get("TrendArrow")) if live else None
    return [t, int(v + 0.5), source, rate]


def graph(body):
    data = body.get("data") or {}
    conn = data.get("connection") or {}
    out = []
    for m in data.get("graphData") or []:
        r = reading(m, "librelinkup-graph", False)
        if r:
            out.append(r)
    live = conn.get("glucoseMeasurement")
    if isinstance(live, dict):
        r = reading(live, "librelinkup", True)
        if r:
            out.append(r)
    out.sort(key=lambda r: (r[0], r[2]))
    sensor = None
    for s in [((data.get("activeSensors") or [{}])[0] or {}).get("sensor"), conn.get("sensor")]:
        if isinstance(s, dict) and s.get("sn") and isinstance(s.get("a"), (int, float)) and s["a"] > 0:
            sensor = {"serial": s["sn"], "start": int(s["a"])}
            break
    return {"readings": out, "sensor": sensor, "name": name_of(conn)}


def margin(m, streak, now, before, found):
    """The margin and the in-time streak after a call answered at `now` that found the live reading `found` (None =
    none), `before` the newest live reading known before it (None = none: a first call says nothing about timing).
    A new reading more than a cadence old when found missed its call (LibreLinkUp had it later than the margin):
    the margin grows by 'margin_up_s' (at most 'margin_max_s'). New readings found in time 'margin_hold' times in a
    row: it shrinks by a second, back towards 'margin_s' (LibreLinkUp may be quick again; slowly, so a lag that stays
    isn't missed again soon). Nothing new (a reading the sensor missed, or one still on its way): unchanged."""
    if found is None or before is None or found <= before:
        return m, streak
    if now - found > POLL["cadence_s"]:
        return min(m + POLL["margin_up_s"], POLL["margin_max_s"]), 0
    streak += 1
    if streak >= POLL["margin_hold"]:
        return max(m - 1, POLL["margin_s"]), 0
    return m, streak


def timed(now, newest, m):
    """Seconds to the next call after an answer: the next reading is due a cadence after the newest live reading,
    LibreLinkUp has it within the margin; the first such time at least 'min_s' from now (a reading still missing
    is asked for at the next one's time: one call a reading at most). A reading stamped ahead of this clock counts
    as now. No reading yet: a cadence."""
    c = POLL["cadence_s"]
    if newest is None:
        return c
    n = min(newest, now)
    k = max(1, -((n + m - now - POLL["min_s"]) // c))
    return n + k * c + m - now


def wait(failures, rate_limited=None, refused=False):
    """Seconds to the next call after a failure: failures back off; a rate limit waits at least its minimum."""
    if refused:
        return None
    if rate_limited is not None:
        return max(POLL["rateLimited_min_s"], rate_limited or 0)
    b = POLL["backoff_s"]
    return b[min(max(failures, 1) - 1, len(b) - 1)]


def after(outcome, failures, retry_after=None, now=0, newest=None, m=POLL["margin_s"]):
    """Seconds to the next call after a round that ended in outcome (None = not set up): None = only when the person
    acts. An answer: timed to the next reading. Asked to wait (429) without a time: the minimum."""
    if outcome is None:
        return None
    if outcome == "ok":
        return timed(now, newest, m)
    if outcome == "terms":
        return POLL["terms_s"]
    if outcome == "retired":
        return POLL["retired_s"]
    if outcome == "notShared":
        return POLL["connectionsEvery_s"]
    if outcome == "rateLimited":
        return wait(failures, retry_after or 0)
    return wait(failures, refused=outcome == "refused")


# ------------------------------------------------------------------ login.json

def ok_login(uid=ACCOUNT, expires=NOW + 3600):
    return {"status": 0, "data": {"user": {"id": uid, "country": "ES", "uiLanguage": "es-ES"},
                                  "authTicket": {"token": TOKEN, "expires": expires, "duration": 3600000}}}


logins = []
L = logins.append
L({"name": "signed in: the account's id and a token that lasts an hour", "code": 200, "body": ok_login()})
L({"name": "signed in with a 180-day token (older accounts)", "code": 200, "body": ok_login(expires=NOW + 180 * 86400)})
L({"name": "the account lives in another region: sign in again there", "code": 200,
   "body": {"status": 0, "data": {"redirect": True, "region": "eu"}}})
L({"name": "a region that isn't one is no redirect", "code": 200,
   "body": {"status": 0, "data": {"redirect": True, "region": "eu.evil.example"}}})
L({"name": "wrong email or password (status 2)", "code": 200,
   "body": {"status": 2, "error": {"message": "notAuthenticated"}}})
L({"name": "wrong email or password (HTTP 401)", "code": 401, "body": {}})
L({"name": "new terms of use to accept: the person accepts them in LibreLinkUp", "code": 200,
   "body": {"status": 4, "data": {"step": {"type": "tou", "componentName": "AcceptDocument"},
                                  "user": {"accountType": "pat", "country": "ES"},
                                  "authTicket": {"token": "step-token", "expires": NOW + 3600, "duration": 3600000}}}})
L({"name": "a new privacy policy to accept: the same, never accepted for them", "code": 200,
   "body": {"status": 4, "data": {"step": {"type": "pp", "componentName": "AcceptDocument"}}}})
L({"name": "too many attempts: locked for a while, the lockout from the answer", "code": 429,
   "body": {"status": 429, "data": {"code": 60, "data": {"failures": 5, "interval": 60, "lockout": 300},
                                    "message": "locked"}}})
L({"name": "too many attempts, with Retry-After", "code": 429, "retryAfter": 900, "body": {}})
L({"name": "this version is too old: the minimum it asks for", "code": 403,
   "body": {"status": 920, "data": {"minimumVersion": "4.18.0"}}})
L({"name": "a minimum that isn't a version is never sent: no sign-in", "code": 403,
   "body": {"status": 920, "data": {"minimumVersion": "4.18.0\r\nX-Injected: 1"}}})
L({"name": "the login is gone (Abbott retired the API)", "code": 404, "body": {}})
L({"name": "the server failed", "code": 502, "body": {}})
L({"name": "an answer without a token is no sign-in", "code": 200,
   "body": {"status": 0, "data": {"user": {"id": ACCOUNT}}}})
for c in logins:
    c["expect"] = login(c["code"], c["body"], c.get("retryAfter"))

# ------------------------------------------------------------------ calls.json

calls = []
C = calls.append
C({"name": "an answer", "code": 200, "body": {"status": 0, "data": []}})
C({"name": "the token expired: sign in again with the saved password", "code": 401, "body": {"message": "Unauthorized"}})
C({"name": "asked to wait", "code": 429, "retryAfter": 120, "body": {}})
C({"name": "new terms mid-session", "code": 200, "body": {"status": 4, "data": {"step": {"type": "tou"}}}})
C({"name": "this version is too old", "code": 403, "body": {"status": 920, "data": {"minimumVersion": "4.18.0"}}})
C({"name": "a minimum that isn't a version is never sent: refused", "code": 403,
   "body": {"status": 920, "data": {"minimumVersion": "latest"}}})
C({"name": "refused for another reason: the API no longer serves this client", "code": 403,
   "body": {"status": 403, "message": "Forbidden"}})
C({"name": "the endpoint is gone", "code": 404, "body": {}})
C({"name": "the server failed", "code": 503, "body": {}})
C({"name": "an answer that isn't LibreLinkUp's", "code": 200, "body": {"hello": "world"}})
for c in calls:
    c["expect"] = call(c["code"], c["body"], c.get("retryAfter"))

# ------------------------------------------------------------------ connections.json


def conn(pid, first, last):
    return {"id": "c-" + pid[:8], "patientId": pid, "firstName": first, "lastName": last, "targetLow": 70,
            "targetHigh": 180, "uom": 1, "sensor": {"deviceId": "", "sn": "0D00ABC123", "a": NOW - 3 * 86400,
                                                    "w": 60, "pt": 4}}


SELF = conn(ACCOUNT, "Alex", "Example")
ANA_C = conn(ANA, "Ana", "Example")
LEO_C = conn(LEO, "Leo", "")
connections = []
K = connections.append
K({"name": "yours: the account follows itself (your own email invited from the Libre app)", "person": "yours",
   "saved": None, "data": [ANA_C, SELF]})
K({"name": "yours: one connection that isn't the account (you invited another email of yours)", "person": "yours",
   "saved": None, "data": [ANA_C]})
K({"name": "yours: two others and not yourself: choose", "person": "yours", "saved": None, "data": [ANA_C, LEO_C]})
K({"name": "someone else's: the one person this account follows", "person": "theirs", "saved": None, "data": [ANA_C]})
K({"name": "someone else's: two children: choose", "person": "theirs", "saved": None, "data": [ANA_C, LEO_C]})
K({"name": "someone else's: the account also follows itself, one other person", "person": "theirs", "saved": None,
   "data": [SELF, LEO_C]})
K({"name": "the person picked before is still shared", "person": "theirs", "saved": LEO, "data": [ANA_C, LEO_C]})
K({"name": "the person picked before stopped sharing", "person": "theirs", "saved": LEO, "data": [ANA_C]})
K({"name": "no one shares with this account yet", "person": "yours", "saved": None, "data": []})
for c in connections:
    c["expect"] = pick(c["data"], ACCOUNT, c["person"], c["saved"])
    mac = pick(c["data"], ACCOUNT, None, c["saved"])
    c["mac"] = {"state": mac["state"], "pick": mac["pick"]}

# ------------------------------------------------------------------ graph.json


def m(s, v, arrow=None):
    o = {"FactoryTimestamp": stamp(s), "Timestamp": local_stamp(s, 1), "type": 1 if arrow is None else 1,
         "ValueInMgPerDl": v, "MeasurementColor": 1, "GlucoseUnits": 1, "Value": v, "isHigh": v > 400,
         "isLow": v < 40}
    if arrow is not None:
        o["TrendArrow"] = arrow
        o["type"] = 1
    else:
        o["type"] = 0
    return o


def gbody(live=None, points=(), sensor=("0D00ABC123", NOW - 3 * 86400), active=True, first="Alex"):
    sens = {"deviceId": "", "sn": sensor[0], "a": sensor[1], "w": 60, "pt": 4} if sensor else None
    connection = {"id": "c-1", "patientId": ACCOUNT, "firstName": first, "lastName": "Example"}
    if sens:
        connection["sensor"] = sens
    if live:
        connection["glucoseMeasurement"] = m(*live)
    data = {"connection": connection, "graphData": [m(s, v) for s, v in points]}
    if sens and active:
        data["activeSensors"] = [{"sensor": sens, "device": {"did": "synthetic-device", "dtid": 40068}}]
    return {"status": 0, "data": data}


graphs = []
G = graphs.append
G({"name": "the live reading with its arrow, and the graph's 5-minute points",
   "body": gbody((NOW - 50, 112, 3), [(NOW - 900, 104), (NOW - 600, 107), (NOW - 300, 110)])})
G({"name": "every arrow: falling quickly to rising quickly", "body": gbody((NOW - 60, 140, 1))})
G({"name": "falling", "body": gbody((NOW - 60, 140, 2))})
G({"name": "rising", "body": gbody((NOW - 60, 140, 4))})
G({"name": "rising quickly", "body": gbody((NOW - 60, 140, 5))})
G({"name": "no arrow: no rate", "body": gbody((NOW - 60, 140, 0))})
G({"name": "below the sensor's range: LO travels as its number, the hub flags it", "body": gbody((NOW - 60, 38, 2))})
G({"name": "above the range", "body": gbody((NOW - 60, 512, 4))})
G({"name": "a point without a time or a value is skipped",
   "body": {"status": 0, "data": {"connection": {"glucoseMeasurement": {"ValueInMgPerDl": 120}},
                                  "graphData": [{"FactoryTimestamp": "not a time", "ValueInMgPerDl": 100},
                                                {"FactoryTimestamp": stamp(NOW - 300), "ValueInMgPerDl": 0},
                                                {"FactoryTimestamp": stamp(NOW - 600), "ValueInMgPerDl": 101}]}}})
G({"name": "afternoon and midnight times read as UTC (12-hour clock)",
   "body": gbody((1795046399, 130, 3), [(1794960000, 120), (1795003200 + 3600, 125)])})
G({"name": "the sensor only in the connection (activeSensors missing)", "body": gbody((NOW - 60, 99, 3), active=False)})
G({"name": "no sensor: life unknown", "body": gbody((NOW - 60, 99, 3), sensor=None)})
G({"name": "a new sensor: a new serial and start", "body": gbody((NOW - 60, 99, 3), sensor=("0D00XYZ789", NOW - 1800))})
G({"name": "no glucose yet (a new account)", "body": {"status": 0, "data": {"connection": {}, "graphData": []}}})
for c in graphs:
    c["expect"] = graph(c["body"])

# ------------------------------------------------------------------ poll.json

waits = []
W = waits.append
W({"name": "one failure", "failures": 1})
W({"name": "two failures", "failures": 2})
W({"name": "three", "failures": 3})
W({"name": "many: at most 10 min apart", "failures": 9})
W({"name": "asked to wait without a time", "failures": 1, "rateLimited": 0})
W({"name": "asked to wait 15 min", "failures": 1, "rateLimited": 900})
W({"name": "the password was refused: never again by itself", "failures": 1, "refused": True})
for c in waits:
    c["wait_s"] = wait(c["failures"], c.get("rateLimited"), c.get("refused", False))

afters = []
A = afters.append
A({"name": "not set up: only when the person signs in", "outcome": None, "failures": 0})
A({"name": "an answer: timed to the next reading", "outcome": "ok", "failures": 0, "now": NOW + 10, "newest": NOW,
   "margin_s": 8})
A({"name": "an answer with no reading yet: a minute", "outcome": "ok", "failures": 0, "now": NOW, "newest": None,
   "margin_s": 8})
A({"name": "no answer, twice", "outcome": "network", "failures": 2})
A({"name": "the server failed three times", "outcome": "server", "failures": 3})
A({"name": "the password was refused", "outcome": "refused", "failures": 1})
A({"name": "new terms: the person accepts them in LibreLinkUp", "outcome": "terms", "failures": 1})
A({"name": "Abbott refuses this client", "outcome": "retired", "failures": 1})
A({"name": "the person picked no longer shares", "outcome": "notShared", "failures": 1})
A({"name": "asked to wait without a time", "outcome": "rateLimited", "failures": 1})
A({"name": "asked to wait 2 min: the minimum", "outcome": "rateLimited", "failures": 1, "retryAfter_s": 120})
A({"name": "asked to wait 15 min", "outcome": "rateLimited", "failures": 1, "retryAfter_s": 900})
for c in afters:
    c["wait_s"] = after(c["outcome"], c["failures"], c.get("retryAfter_s"), c.get("now", 0), c.get("newest"),
                        c.get("margin_s", POLL["margin_s"]))

# when the next call goes, after an answer (timed): 'now', the newest live reading, the margin -> 'wait_s'
timings = []
T = timings.append
T({"name": "a reading 10 s ago: 8 s after the next one is due", "now": NOW + 10, "newest": NOW, "margin_s": 8})
T({"name": "found as it arrived", "now": NOW + 8, "newest": NOW, "margin_s": 8})
T({"name": "a larger margin", "now": NOW + 8, "newest": NOW, "margin_s": 25})
T({"name": "the next one's time is too close: the one after", "now": NOW + 30, "newest": NOW, "margin_s": 8})
T({"name": "exactly 45 s away: kept", "now": NOW + 23, "newest": NOW, "margin_s": 8})
T({"name": "the sensor missed one: still on its minute", "now": NOW + 68, "newest": NOW, "margin_s": 8})
T({"name": "an hour old (the Libre app stopped): still once a minute, on its minute", "now": NOW + 3601,
   "newest": NOW, "margin_s": 8})
T({"name": "stamped ahead of this clock: counts as now", "now": NOW, "newest": NOW + 40, "margin_s": 8})
T({"name": "no reading yet: a minute", "now": NOW, "newest": None, "margin_s": 8})
for c in timings:
    c["wait_s"] = timed(c["now"], c["newest"], c["margin_s"])

# how the margin follows LibreLinkUp: the margin before, the call's answer time, the newest live reading before and
# after it -> 'margin_s' after
margins = []
M = margins.append
M({"name": "found in time: one more in a row", "margin": 13, "streak": 3, "now": NOW + 73, "before": NOW,
   "found": NOW + 60})
M({"name": "the 10th in a row: a second less", "margin": 13, "streak": 9, "now": NOW + 73, "before": NOW,
   "found": NOW + 60})
M({"name": "never below 8", "margin": 8, "streak": 9, "now": NOW + 68, "before": NOW, "found": NOW + 60})
M({"name": "found a minute late (LibreLinkUp had it after the call): +5", "margin": 8, "streak": 4,
   "now": NOW + 128, "before": NOW, "found": NOW + 60})
M({"name": "late again, at most 40", "margin": 38, "streak": 0, "now": NOW + 218, "before": NOW + 60,
   "found": NOW + 120})
M({"name": "nothing new: unchanged", "margin": 13, "streak": 2, "now": NOW + 73, "before": NOW, "found": NOW})
M({"name": "the sensor missed one, the next found in time: not late", "margin": 8, "streak": 0, "now": NOW + 128,
   "before": NOW, "found": NOW + 120})
M({"name": "the first answer says nothing about timing", "margin": 8, "streak": 0, "now": NOW + 500,
   "before": None, "found": NOW})
M({"name": "no live reading", "margin": 8, "streak": 0, "now": NOW + 68, "before": NOW, "found": None})
for c in margins:
    c["margin_s"], c["streak_after"] = margin(c["margin"], c["streak"], c["now"], c["before"], c["found"])


def live_at(t, readings):
    """The live reading LibreLinkUp shows at t: the newest uploaded (each reading [time, uploaded])."""
    best = None
    for r, up in readings:
        if up <= t and (best is None or r > best):
            best = r
    return best


def simulate(name, readings, skew=0, answer=0, hours=3, fixed=None, start=None):
    """The source against LibreLinkUp for `hours`: readings [time, uploaded] in true time, this clock `skew` s ahead,
    each answer `answer` s after the call. -> calls an hour, the delay of each reading shown (true time from its
    reading to its answer), readings never shown live an hour, and the steps (this clock)."""
    t = readings[0][0] + 13 if start is None else start
    end = t + hours * 3600
    m, streak, before, calls, seen, steps = POLL["margin_s"], 0, None, 0, {}, []
    while t < end:
        calls += 1
        a = t + answer
        found = live_at(a, readings)
        if found is not None and found not in seen:
            seen[found] = a - found
        now = a + skew
        m2, streak = margin(m, streak, now, before, found)
        newest = found if before is None or (found is not None and found > before) else before
        w = fixed if fixed is not None else timed(now, newest, m2)
        steps.append({"now": now, "found": found, "margin_s": m2, "wait_s": w})
        m, before = m2, newest
        t = a + w
    shown = sorted(seen.values())
    span = [r for r, _ in readings if readings[0][0] + 600 <= r <= end - 120]
    lost = sum(1 for r in span if r not in seen)
    return {"name": name, "calls_per_hour": round(calls / hours, 1),
            "delay_median_s": round(shown[len(shown) // 2], 1), "delay_worst_s": round(shown[-1], 1),
            "missed_per_hour": round(lost / hours, 1)}, steps


def minutes(n=200, period=60, lag=lambda i: 4, skip=lambda i: False, t0=NOW):
    return [(t0 + i * period, t0 + i * period + lag(i)) for i in range(n) if not skip(i)]


SIMS = [
    ("the owner's test: LibreLinkUp has each reading 4 s after it", minutes(), {}),
    ("LibreLinkUp 1-7 s after it", minutes(lag=lambda i: 1 + (i * 37 % 7)), {}),
    ("the sensor misses one reading in 10", minutes(skip=lambda i: i % 10 == 5), {}),
    ("a slow uploader: 25 s after it", minutes(lag=lambda i: 25), {}),
    ("one late upload (40 s) in 15", minutes(lag=lambda i: 40 if i % 15 == 7 else 4), {}),
    ("the Libre app uploads every 5 min (readings late in batches)",
     [(NOW + i * 60, NOW + (i // 5 + 1) * 300 + 4) for i in range(200)], {}),
    ("the sensor's clock drifts (60.3 s a reading) and this clock is 20 s ahead",
     [(NOW + round(i * 60.3), NOW + round(i * 60.3) + 4) for i in range(200)], {"skew": 20}),
    ("the call takes 1.5 s", minutes(), {"answer": 1.5}),
]
simulation = []
for name, rs, kw in SIMS:
    hours = (len(rs) * 60 - 600) / 3600
    r, _ = simulate(name, rs, hours=hours, **kw)
    # the fixed minute it replaces, from every second of the minute
    fixed = [simulate(name, rs, hours=hours, fixed=60, start=rs[0][0] + p + 0.5, **kw)[0] for p in range(60)]
    r["fixedMinute"] = {
        "calls_per_hour": max(f["calls_per_hour"] for f in fixed),
        "delay_median_s": sorted(f["delay_median_s"] for f in fixed)[30],
        "delay_worst_s": max(f["delay_worst_s"] for f in fixed),
        "missed_per_hour": max(f["missed_per_hour"] for f in fixed),
    }
    simulation.append(r)
    # one call a reading at most (the 60 an hour the fixed minute made), however LibreLinkUp behaves
    assert r["calls_per_hour"] <= 60.5, r

# steps both apps replay (the margin carried from step to step, from 'margin_s'): integer times
sequences = []
for name, rs, kw in [SIMS[2], SIMS[3], SIMS[4], SIMS[6]]:
    _, steps = simulate(name, rs, hours=1, **kw)
    sequences.append({"name": name, "steps": steps[:45]})

versions = []
V = versions.append
V({"name": "none asked: this build's", "asked": None})
V({"name": "a newer one asked: sent from then on", "asked": "4.18.0"})
V({"name": "a patch asked", "asked": "4.17.1"})
V({"name": "the same as this build's", "asked": "4.17.0"})
V({"name": "an update brought a newer one: never back to an older one asked", "asked": "4.16.2"})
V({"name": "parts compared as numbers", "asked": "4.9.9"})
V({"name": "a longer version", "asked": "4.17.0.1"})
for c in versions:
    c["send"] = send(c["asked"])

ABOUT_LOGIN = [
    "LibreLinkUp's login answers -> what Sukrara does (docs/44 step 2; the LibreView sign-in uses the same login).",
    "POST https://api.libreview.io/llu/auth/login (then api-<region>.libreview.io after a redirect) with",
    "{email, password} and the headers in poll.json 'headers'. 'expect.answer': 'account' (the account id, the token",
    "and when it expires: renew 'renewBefore_s' before by signing in again), 'redirect' (sign in again at the",
    "region's host; a region that isn't 2-3 lower-case letters is refused), 'badCredentials' (never retried by itself:",
    "retries lock the account), 'terms' (status 4: the person accepts in LibreLinkUp; Sukrara never accepts for them),",
    "'rateLimited' (wait at least poll.json 'rateLimited_min_s', longer when Retry-After or the lockout says so),",
    "'version' (sent from then on, the request made once more, poll.json 'versions'; still refused = 'retired'; a",
    "minimum that isn't a version is none and never goes into a header), 'retired' (the API is gone), 'failed'.",
]
dump("login.json", {"_about": ABOUT_LOGIN, "now": NOW, "cases": logins})
dump("calls.json", {"_about": [
    "An authenticated call's answer (GET /llu/connections, /llu/connections/<patientId>/graph, with",
    "'Authorization: Bearer <token>' and 'Account-Id: sha256hex(account id)') -> the outcome the source acts on:",
    "'ok'; 'expired' (sign in again with the saved password, once); 'rateLimited'; 'terms'; 'version' (as login.json:",
    "sent from then on, the call made once more);",
    "'retired' (refused or gone: health 'stopped'); 'server' (health 'unreachable').",
], "cases": calls})
dump("connections.json", {"_about": [
    "Whose glucose the account brings (GET /llu/connections -> data[]): 'choices' are the people it follows in the",
    "answer's order (patientId, first and last name). 'pick': the person saved before while still shared (else 'gone':",
    "health 'notShared', the person picks again); for your own glucose the account itself (you invited your own email",
    "from the Libre app), else the only one; for someone else's the only one besides the account; several = 'choose'",
    "(the Connect step lists them); none = 'none' (health 'notShared': invite this email from the Libre app).",
    "'account' is the signed-in account's id. 'mac': the Mac's pick, which doesn't ask whose glucose (owner,",
    "2026-11-18): every connection, the account's own included: the saved one while still shared, else the only one,",
    "else choose.",
], "account": ACCOUNT, "cases": connections})
dump("graph.json", {"_about": [
    "One graph answer (GET /llu/connections/<patientId>/graph) -> readings and the sensor. Each reading is",
    "[epoch s, mg/dL, source, rate]: 'librelinkup' = the live reading (data.connection.glucoseMeasurement), its",
    "TrendArrow as a rate in mg/dL/min (1..5 = -2.5, -1.5, 0, 1.5, 2.5; else none: THE trend is still computed from the",
    "readings, contracts/trend); 'librelinkup-graph' = the graph's points (about every 5 min, 12 h back: they only fill",
    "gaps, dedup.json 'onlineHistory'). Times from FactoryTimestamp, which is UTC ('M/d/yyyy h:mm:ss AM'); the local",
    "Timestamp is never read. ValueInMgPerDl rounded; 0 or none = skipped; LO / HI stay numbers here (the hub flags",
    "them). 'sensor': the first of data.activeSensors[0].sensor and data.connection.sensor with a serial and a start",
    "('sn', 'a' epoch s): a new serial is a new sensor; its life uses contracts/sensor/life.json's defaults (warm-up,",
    "wear), as LibreLinkUp doesn't say how long it lasts. 'name' = the connection's first and last name.",
], "now": NOW, "cases": graphs})
dump("poll.json", {"_about": [
    "When the LibreLinkUp source asks again. After an answer, timed to the next reading ('timed'): the Libre app",
    "uploads each reading as the sensor reads it, each 'cadence_s', and LibreLinkUp has it within seconds (the owner's",
    "test 2026-11-18: about 4 s), so the next call goes 'margin_s' after the next reading is due (the newest live",
    "reading + a cadence), the first such time at least 'min_s' from now: one call a reading at most, on the sensor's",
    "own minute (its drift and a missed reading included: the next is still due on that minute). The margin follows",
    "LibreLinkUp ('margins'): a new reading more than a cadence old when found missed its call, so the margin grows",
    "'margin_up_s' (at most 'margin_max_s'); 'margin_hold' new readings in a row found in time take a second off,",
    "down to 'margin_s'; it starts at 'margin_s' with each run. 'sequences' are calls in a row both apps replay (the",
    "margin and streak carried from 'margin_s' and 0, the newest live reading from none). 'simulation': the rule",
    "against LibreLinkUp behaving in these ways, beside the fixed minute it replaced (from every second of the minute):",
    "calls an hour, the delay from a reading to its answer, readings never shown live (only the graph's 5-minute",
    "points). After failures in a row 'backoff_s'; asked to wait (429) at least 'rateLimited_min_s', or Retry-After /",
    "the lockout when longer; a refused password never by itself (the person signs in again: retries lock the",
    "account). A token is renewed 'renewBefore_s' before it expires; the connections are read again every",
    "'connectionsEvery_s' (and when the graph call is refused: the person may have stopped sharing). 'headers': what",
    "every call sends. 'after': the wait after a round that ended in 'outcome' (contracts/sources/health.json's",
    "outcomes; null = only when the person acts; 'ok' with 'now', 'newest', 'margin_s'). Asked to wait, nothing calls",
    "LibreLinkUp before that time, whatever asks for it (the network back, Try Again, another person picked: each call",
    "may lengthen the lockout); a sign-in the person makes ends the wait. 'versions': a 403 on the login or a call",
    "naming a minimum version (login.json, calls.json) has that version sent from then on and the same request made",
    "once more; it stays with the account (a new sign-in, a restart), unless this build's own version is newer",
    "('send', parts compared as numbers).",
], **POLL,
    "headers": {"product": "llu.android", "version": VERSION, "Accept": "application/json",
                "Content-Type": "application/json", "cache-control": "no-cache"},
    "cases": waits, "after": afters, "timed": timings, "margins": margins, "sequences": sequences,
    "simulation": simulation, "versions": versions})
