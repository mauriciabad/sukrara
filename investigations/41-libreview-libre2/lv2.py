#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Investigation 41: a Libre 2 read through Juggluco, uploaded to LibreView as the Libre 3 app (docs/41).

  python3 -I lv2.py fixture                                  write contracts/treatments/libreview-glucose-fixture.json
  python3 -I lv2.py plan FILE --serial S --start EPOCH [--from E] [--to E]
                                                             the 5-minute values FILE (Juggluco entries.json) gives
  python3 -I lv2.py signin --gateway libre3|librelink [--take-over]
  python3 -I lv2.py register SERIAL                          PUT nisperson activeSensor (Libre 3 identity)
  python3 -I lv2.py post FILE --serial S --start EPOCH --from E --to E --really
  python3 -I lv2.py nickname SERIAL NICKNAME --really        the website's updateNickname with the LibreLinkUp login
                                                             (refused: 400 ProductMismatch, docs/41)

Nothing reaches LibreView without `signin` and, for data, `--really`. Credentials: $LV_EMAIL and the login keychain
item `dev.spike.libreview` (investigation 04's), read only when signing in; the password is never printed or stored.
State (spike DeviceIds, config, token) lives in $LV2_STATE (default ~/.config/sukrara-spike41/), never in the repo.
$LV2_ASSETS / $LV2_LIBRELINK_CONFIG point the client at a fake server (tests).
"""
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STATE_DIR = os.environ.get("LV2_STATE") or os.path.expanduser("~/.config/sukrara-spike41")
ASSETS = os.environ.get("LV2_ASSETS") or "https://fsll3.freestyleserver.com/Payloads/Mobile/FSLibre3/Android/Assets/3.3.0/DE.json"
LIBRELINK_CONFIG = os.environ.get("LV2_LIBRELINK_CONFIG") or \
    "https://fsll.freestyleserver.com/Payloads/Mobile/FSLibreLink/Android/Config/FSLibreLink_Android_2.10_GB_config.json"
UA = "Dalvik/2.1.0 (Linux; U; Android 16; Pixel 10 Pro Build/BP2A.250605.031)"
CULTURE = "en-ES"
GATEWAY = {"libre3": "FSLibreLink3.Android", "librelink": "FSLibreLink.Android"}
CAPS = ["scheduledContinuousGlucose", "unscheduledContinuousGlucose", "currentGlucose", "insulin", "food"] + [
    "generic-com.abbottdiabetescare.informatics." + c for c in (
        "exercise", "ondemandalarm.low", "ondemandalarm.high", "ondemandalarm.projectedlow",
        "ondemandalarm.projectedhigh", "customnote", "sensorstart", "sensorEnd", "isfGlucoseAlarm",
        "alarmSetting", "bleDisconnect", "bleReconnect", "error")]
SLOT_MIN = 5


# ---------------------------------------------------------------- what goes up (Sukrara: LvSlots / LibreViewPayload)

def libre12(serial):
    """A Libre 1 / 2 / 2 Plus serial as Juggluco names it: 11 characters, family digit 0 or 3, then M (the NFC uid's
    fixed top bytes). Sibionics, Dexcom and the other families Juggluco reads never look like this."""
    return re.fullmatch(r"[03]M[0-9A-Z]{9}", serial) is not None


def slots(readings):
    """One value per 5 minutes of the sensor's life: the first reading in each slot, under the slot's first minute.
    readings = [(t, mgdl, life)] with the sensor's own minute (Juggluco's `_id` SERIAL#life). Life count 0 is the
    sensor-start record's number. -> [(life_count, mgdl, t)], ascending."""
    best = {}
    for t, mgdl, life in readings:
        lc = life // SLOT_MIN * SLOT_MIN
        if lc <= 0:
            continue
        if lc not in best or (t, mgdl) < best[lc]:
            best[lc] = (t, mgdl)
    return [(lc, m, t) for lc, (t, m) in sorted(best.items())]


def current(readings):
    """The newest reading as the live value: (life_count, mgdl, t) with its own minute, or None."""
    if not readings:
        return None
    t, mgdl, life = max(readings)
    return life, mgdl, t


def unalf(c):
    ch = {"B": "8", "I": "1", "O": "0", "S": "5"}.get(c.upper(), c.upper())
    if ch <= "9": return ord(ch) - ord("0")
    if ch == "A": return 10
    if ch <= "H": return ord(ch) - ord("A") + 9
    if ch <= "N": return ord(ch) - ord("A") + 8
    if ch <= "R": return ord(ch) - ord("A") + 7
    return ord(ch) - ord("A") + 6


def histor(serial):
    """The sensor's record-number base (Sukrara LibreViewPayload.histor): last 9 characters, 5 bits each, << 19."""
    acc = 0
    for c in serial[-9:]:
        acc = ((acc << 5) | unalf(c)) & 0xFFFFFFFFFFFFFFFF
    v = (acc << 19) & 0xFFFFFFFFFFFFFFFF
    return v - (1 << 64) if v >= 1 << 63 else v


def jstr(s):
    return json.dumps(s, ensure_ascii=False)


def _offset(t):
    off = int(t.utcoffset().total_seconds()) // 60
    return "%s%02d:%02d" % ("+" if off >= 0 else "-", abs(off) // 60, abs(off) % 60)


def utc_ms(e): return dt.datetime.fromtimestamp(e, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def local_ms(e, tz):
    t = dt.datetime.fromtimestamp(e, tz)
    return t.strftime("%Y-%m-%dT%H:%M:%S.000") + _offset(t)


def trend(rate):
    if rate is None: return "Undetermined"
    if rate > 2: return "RisingQuickly"
    if rate > 1: return "Rising"
    if rate >= -1: return "Stable"
    if rate >= -2: return "Falling"
    return "FallingQuickly"


def sensor_start(h, start, tz, product, wear_min):
    """Both times at the sensor's start (the proof's upload sent `timestamp` = now: LibreView filed it on 15 Nov)."""
    return ('{"type":"com.abbottdiabetescare.informatics.sensorstart","extendedProperties":{"factoryTimestamp":"%s",'
            '"puckGen":0,"wearDuration":%d,"warmupTime":60,"productType":%d},"recordNumber":%d,"timestamp":"%s"}'
            % (utc_ms(start), wear_min, product, h, local_ms(start, tz)))


def payload(device_id, token, now, tz, serial, start, cur, hist, send_start, product=4, wear_min=21600,
            hardware=("GlucoBridge-spike", "GlucoBridge")):
    """Sukrara's /api/measurements body with only a sensor part (contracts/treatments/SPEC.md §3, byte for byte)."""
    h = histor(serial)
    gen = [sensor_start(h, start, tz, product, wear_min)] if send_start else []
    curs = []
    if cur:
        lc, m, t, rate = cur
        curs.append('{"extendedProperties":{"trendArrow":"%s","isActionable":true,"isViewed":false,'
                    '"factoryTimestamp":"%s","isFirstAfterTimeChange":false},"recordNumber":%d,'
                    '"timestamp":"%s","valueInMgPerDl":%d.0}' % (trend(rate), utc_ms(t), h | lc, local_ms(t, tz), m))
    hs = ['{"extendedProperties":{"canMerge":true,"isFirstAfterTimeChange":false,"factoryTimestamp":"%s"},'
          '"recordNumber":%d,"timestamp":"%s","valueInMgPerDl":%d.0}' % (utc_ms(t), h | lc, local_ms(t, tz), m)
          for lc, m, t in hist]
    return ('{"DeviceData":{"deviceSettings":{"factoryConfig":{"UOM":"mg/dL"},"firmwareVersion":"3.3.0",'
            '"miscellaneous":{"selectedLanguage":"%s","valueGlucoseTargetRangeLowInMgPerDl":70,'
            '"valueGlucoseTargetRangeHighInMgPerDl":140,"selectedTimeFormat":"24hr","selectedCarbType":"grams of carbs"},'
            '"timestamp":"%s"},"header":{"device":{"hardwareDescriptor":"%s","hardwareName":"%s",'
            '"modelName":"com.freestylelibre3.app.de","osType":"Android","osVersion":"36","uniqueIdentifier":"%s"}},'
            '"measurementLog":{"capabilities":[%s],"currentGlucoseEntries":[%s],"foodEntries":[],"genericEntries":[%s],'
            '"insulinEntries":[],"scheduledContinuousGlucoseEntries":[%s],"unscheduledContinuousGlucoseEntries":[]}},'
            '"UserToken":%s,"Domain":"Libreview","GatewayType":"%s"}'
            % (CULTURE, local_ms(now, tz), hardware[0], hardware[1], device_id, ",".join(jstr(c) for c in CAPS),
               ",".join(curs), ",".join(gen), ",".join(hs), jstr(token), GATEWAY["libre3"]))


def read_entries(path, serial):
    """Juggluco's /api/v1/entries.json (interval=0) of [serial]: [(t, mgdl, life)], oldest first, life = the sensor's
    minute from `_id` (SERIAL#life); LO / HI as Sukrara stores them."""
    out = []
    with open(path) as f:
        entries = json.load(f)
    for e in entries:
        mgdl = e.get("sgv")
        s, _, life = str(e.get("_id", "")).partition("#")
        if s != serial or not life.isdigit() or not isinstance(mgdl, int) or not 1 <= mgdl <= 1000:
            continue
        out.append((e["date"] // 1000, 39 if mgdl < 40 else 401 if mgdl > 400 else mgdl, int(life)))
    return sorted(out)


# ---------------------------------------------------------------- fixture (synthetic readings only)

def cmd_fixture():
    tz = ZoneInfo("Europe/Madrid")
    serial, start, now = "3MH000ABCD7", 1_793_712_000, 1_793_798_400
    # a minute stream (the sensor's minute runs a little behind the clock) with holes, the same minute twice, and
    # readings in the first slot (dropped: life count 0 is the sensor-start record)
    readings = [(start + 30, 101, 0), (start + 200, 102, 3)]
    v = 120
    for i in range(60, 100):
        if i in (71, 72, 73, 74, 75, 76, 88):
            continue
        v += (-1) ** i * (i % 4)
        readings.append((start + i * 61 + 7, v, i))
    readings.append((start + 95 * 61 + 40, 130, 95))
    readings.append((now - 3600, 39, 1380))
    hist = slots(readings)
    cur = current(readings)
    body = payload("00000000-0000-4000-8000-000000000041", "TOKEN", now, tz, serial, start,
                   (cur[0], cur[1], cur[2], None), hist, True, product=3, wear_min=20160)
    fx = {"zone": "Europe/Madrid", "now": now, "deviceId": "00000000-0000-4000-8000-000000000041", "token": "TOKEN",
          "serial": serial, "libre12": libre12(serial),
          "notLibre12": ["0D0001ABCD", "LT2401AB1CD", "3mh000abcd7", "8G1234", "3MH000ABCD", "1MH000ABCD7"],
          "start": start, "readingsTMgdlLife": readings, "productType": 3, "wearMin": 20160,
          "expectedSlots": hist, "expectedCurrent": list(cur), "expectedPayload": body}
    path = os.path.join(ROOT, "contracts", "treatments", "libreview-glucose-fixture.json")
    with open(path, "w") as f:
        json.dump(fx, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("wrote", os.path.relpath(path, ROOT), len(hist), "slots")


# ---------------------------------------------------------------- LibreView (only with signin; data only with --really)

def state():
    os.makedirs(STATE_DIR, mode=0o700, exist_ok=True)
    p = os.path.join(STATE_DIR, "state.json")
    s = {}
    if os.path.exists(p):
        with open(p) as f:
            s = json.load(f)
    for g in GATEWAY:
        s.setdefault("device-" + g, str(uuid.uuid4()))
    return s


def save(s):
    p = os.path.join(STATE_DIR, "state.json")
    with open(p, "w") as f:
        json.dump(s, f)
    os.chmod(p, 0o600)


def http(method, url, body=None, headers=None):
    if not url.startswith("https://") and not url.startswith("http://127.0.0.1:"):
        raise SystemExit("refusing " + url)
    data = body.encode() if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=method, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.status, json.loads(r.read().decode() or "null")


def http_or_error(method, url, body=None, headers=None):
    """[http], but an HTTP error answers with its code and (JSON) body instead of raising."""
    try:
        return http(method, url, body, headers)
    except urllib.error.HTTPError as e:
        text = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, {"text": text[:300]}


def config(gateway):
    if gateway == "libre3":
        _, a = http("GET", ASSETS)
        _, c = http("GET", a["Configuration"])
    else:
        _, c = http("GET", LIBRELINK_CONFIG)
    return c["newYuUrl"].rstrip("/"), c.get("newYuApiKey", "")


def headers(gateway, key, token=""):
    if gateway == "libre3":
        return {"Content-Type": "application/json", "Platform": "Android", "Version": "3.3.0",
                "Abbott-ADC-App-Platform": "Android/16/FSL3/3.3.0.9092", "Accept-Language": CULTURE,
                "x-api-key": key, "x-newyu-token": token}
    return {"Content-Type": "application/json; charset=utf-8", "Accept-Language": CULTURE + ", en;q=0.8",
            "Abbott-ADC-App-Platform": "Android/16/FSLL/2.10.1.10406"}


def credentials():
    email = os.environ.get("LV_EMAIL", "")
    pw = os.environ.get("LV2_PASSWORD_FOR_TESTS")  # the fake server's tests only
    if pw is None:
        if not email:
            meta = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview"],
                                  capture_output=True, text=True, check=True).stdout
            email = next(ln.split("=", 1)[1].strip().strip('"') for ln in meta.splitlines() if '"acct"' in ln)
        pw = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview", "-a", email, "-w"],
                            capture_output=True, text=True, check=True).stdout.strip()
    if not email or not pw:
        raise SystemExit("no credentials")
    return email, pw


def cmd_signin(gateway, take_over):
    s = state()
    base, key = config(gateway)
    email, pw = credentials()
    body = json.dumps({"Culture": CULTURE, "DeviceId": s["device-" + gateway], "Password": pw, "SetDevice": take_over,
                       "UserName": email, "Domain": "Libreview", "GatewayType": GATEWAY[gateway]}, separators=(",", ":"))
    code, o = http("POST", base + "/api/nisperson/getauthentication", body, headers(gateway, key))
    st, reason = (o or {}).get("status"), (o or {}).get("reason")
    print("signin", gateway, "take-over" if take_over else "no take-over", "HTTP", code, "status", st, "reason", reason)
    r = (o or {}).get("result") or {}
    if st == 0 and r.get("UserToken"):
        s["session-" + gateway] = {"base": base, "key": key, "token": r["UserToken"],
                                   "profile": {k: r.get(k) for k in ("DateOfBirth", "FirstName", "LastName",
                                                                     "GuardianFirstName", "GuardianLastName")}}
        save(s)
    return st, reason


def session(gateway="libre3"):
    s = state()
    if "session-" + gateway not in s:
        raise SystemExit("sign in first")
    return s, s["session-" + gateway]


def cmd_register(serial):
    _, ses = session()
    p = ses["profile"]
    body = json.dumps({"DateOfBirth": p["DateOfBirth"], "DomainData": '{"activeSensor":"%s"}' % serial,
                       "FirstName": p["FirstName"], "GuardianFirstName": p["GuardianFirstName"] or "",
                       "GuardianLastName": p["GuardianLastName"] or "", "LastName": p["LastName"],
                       "UserToken": ses["token"], "Domain": "Libreview", "GatewayType": GATEWAY["libre3"]},
                      separators=(",", ":"))
    code, o = http("PUT", ses["base"] + "/api/nisperson", body, headers("libre3", ses["key"], ses["token"]))
    print("register", serial, "HTTP", code, "status", (o or {}).get("status"), "reason", (o or {}).get("reason"))
    return (o or {}).get("status")


def cmd_post(path, serial, start, frm, to, really, now=None, tz=None):
    readings = [r for r in read_entries(path, serial) if frm <= r[0] < to]
    hist = slots(readings)
    print(len(readings), "readings ->", len(hist), "5-minute values", hist[:1], "...", hist[-1:])
    if not really:
        return None
    s, ses = session()
    now = now or int(dt.datetime.now().timestamp())
    zone = tz or ZoneInfo(os.environ.get("LV2_ZONE", "Asia/Nicosia"))
    body = payload(s["device-libre3"], ses["token"], now, zone, serial, start, None, hist, True, product=3, wear_min=20160,
                   hardware=("Sukrara-spike41", "Sukrara-spike41"))
    code, o = http("POST", ses["base"] + "/api/measurements", body, headers("libre3", ses["key"], ses["token"]))
    print("post HTTP", code, "answer", json.dumps(o)[:400])
    return (o or {}).get("status")


LLU = os.environ.get("LV2_LLU") or "https://api.libreview.io"


def cmd_nickname(device_id, nickname, really):
    """The website's `POST user/updateNickname {nickname, deviceId}` with the LibreLinkUp login's Bearer token (the login
    Sukrara already has), not the website's own login. Prints statuses only, never a token."""
    print("nickname", repr(nickname), "for device", device_id, "" if really else "(dry run: nothing sent)")
    if not really:
        return None
    email, pw = credentials()
    base = LLU
    llu = {"Content-Type": "application/json", "Accept": "application/json", "product": "llu.android", "version": "4.16.0"}
    for _ in range(3):
        code, o = http("POST", base + "/llu/auth/login", json.dumps({"email": email, "password": pw}), llu)
        d = (o or {}).get("data") or {}
        if (o or {}).get("status") == 0 and d.get("redirect") and re.fullmatch(r"[a-z]{2,3}", d.get("region", "")):
            base = LLU.replace("://api.", "://api-%s." % d["region"]) if "://api." in LLU else base
            continue
        break
    token = ((d.get("authTicket") or {}).get("token")) if (o or {}).get("status") == 0 else None
    print("llu login HTTP", code, "status", (o or {}).get("status"), "token" if token else "no token")
    if not token:
        return None
    # the website's headers (its httpHeaders.js): Account-Id = SHA-256 of the user id (as LibreLinkUp ≥ 4.16 wants)
    user_id = str(((d.get("user") or {}).get("id")) or "")
    auth = {"Content-Type": "application/json", "Authorization": "Bearer " + token, "Product": "lv",
            "NEWYU-LV-Web-Version": "3.27.0.42", "Account-Id": hashlib.sha256(user_id.encode()).hexdigest()}
    # LibreView names a device by its own id; the upload's DeviceId is its "sn" (the website's devices list, from
    # glucoseHistory): look it up first
    code, o = http_or_error("GET", base + "/glucoseHistory?numPeriods=1&period=14", None, auth)
    listed = ((o or {}).get("data") or {}).get("devices") or []
    print("glucoseHistory HTTP", code, "status", (o or {}).get("status"), "devices", len(listed),
          "" if listed else json.dumps(o)[:300])
    match = [dv for dv in listed if str(dv.get("sn", "")).lower() == device_id.lower() or dv.get("id") == device_id]
    if len(match) != 1:
        print("no single device with that serial or id: nothing sent")
        return None
    code, o = http_or_error("POST", base + "/user/updateNickname",
                            json.dumps({"nickname": nickname, "deviceId": match[0]["id"]}), auth)
    if code != 200:
        print("updateNickname HTTP", code, "answer", json.dumps(o)[:300])
        return None
    devices = ((o or {}).get("data") or {}).get("devices") or []
    print("updateNickname HTTP", code, "status", (o or {}).get("status"),
          "devices", [{k: v for k, v in dv.items() if k in ("id", "nickname", "sn")} for dv in devices if dv.get("nickname")])
    return (o or {}).get("status")


def main(argv):
    a = argv[1:]

    def opt(name, conv=int, default=None):
        return conv(a[a.index(name) + 1]) if name in a else default
    if not a:
        print(__doc__)
    elif a[0] == "fixture":
        cmd_fixture()
    elif a[0] == "plan":
        cmd_post(a[1], opt("--serial", str), opt("--start"), opt("--from", default=0), opt("--to", default=1 << 40), False)
    elif a[0] == "signin":
        cmd_signin(opt("--gateway", str, "libre3"), "--take-over" in a)
    elif a[0] == "register":
        cmd_register(a[1])
    elif a[0] == "post":
        cmd_post(a[1], opt("--serial", str), opt("--start"), opt("--from"), opt("--to"), "--really" in a)
    elif a[0] == "nickname":
        cmd_nickname(a[1], a[2], "--really" in a)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
