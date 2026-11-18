#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Investigation 45: two uploaders on one LibreView account (docs/45).

  python3 -I lv45.py llu [--devices]                        LibreLinkUp (read-only): which device feeds it (`did`);
                                                            --devices: the website's device list with that token
  python3 -I lv45.py signin DEVICE_ID [--gateway libre3|librelink [--country GB]] [--take-over] --really
                                                            the app login; prints status and reason only
  python3 -I lv45.py treatment DEVICE_ID NOTE --really      sign in (never a take-over) and send one test treatment
                                                            (0.1 u long-acting + NOTE), as Sukrara's upload would
  python3 -I lv45.py tokens DEVICE_ID NOTE --really         two sign-ins, an empty upload on the first token, the
                                                            test treatment on the second (docs/45 test D)
  python3 -I lv45.py binding APP_DEVICE_ID LIBRELINK_DEVICE_ID --really
                                                            take over on the LibreLink identity, check the Libre 3
                                                            one, give the account back (docs/45 test E)
  python3 -I lv45.py cross APP_DEVICE_ID LIBRELINK_DEVICE_ID --really
                                                            a Libre 3 token, a LibreLink sign-in, the Libre 3 token
                                                            again (docs/45 test F)
  python3 -I lv45.py fixture                                 contracts/treatments/libreview-librelink-fixture.json (offline)
  python3 -I lv45.py ll-treatment DEVICE_ID NOTE [--take-over] --really
                                                            one test treatment as the LibreLink app (docs/45 test G)
  python3 -I lv45.py ll-tokens DEVICE_ID --really           test D on the LibreLink identity: two sign-ins, an
                                                            upload of nothing on each token (docs/45b)

WARNING: every app-API sign-in that gets a token ends that identity's current session, SetDevice:false and the same
DeviceId included (docs/45 test D). Signing in on the identity the official app uses stops its upload silently until it
is signed out and in again. Only `llu` (LibreLinkUp, another product) is harmless. Nothing reaches the app API without
--really. Credentials: the login keychain item `dev.spike.libreview` (investigation 04's), read only at sign-in.
Nothing is stored: tokens live in this process only; the output never holds a password or a token.
"""
import datetime as dt
import hashlib
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request

ASSETS = "https://fsll3.freestyleserver.com/Payloads/Mobile/FSLibre3/Android/Assets/3.3.0/DE.json"
LIBRELINK_CONFIG = "https://fsll.freestyleserver.com/Payloads/Mobile/FSLibreLink/Android/Config/FSLibreLink_Android_2.10_GB_config.json"
LLU = "https://api.libreview.io"
UA = "Dalvik/2.1.0 (Linux; U; Android 16; Pixel 10 Pro Build/BP2A.250605.031)"
CULTURE = "en-ES"
GATEWAY = {"libre3": "FSLibreLink3.Android", "librelink": "FSLibreLink.Android"}
CAPS = ["scheduledContinuousGlucose", "unscheduledContinuousGlucose", "currentGlucose", "insulin", "food"] + [
    "generic-com.abbottdiabetescare.informatics." + c for c in (
        "exercise", "ondemandalarm.low", "ondemandalarm.high", "ondemandalarm.projectedlow",
        "ondemandalarm.projectedhigh", "customnote", "sensorstart", "sensorEnd", "isfGlucoseAlarm",
        "alarmSetting", "bleDisconnect", "bleReconnect", "error")]


def stamp():
    return dt.datetime.now().astimezone().strftime("%H:%M:%S")


def http(method, url, body=None, headers=None):
    if not url.startswith("https://"):
        raise SystemExit("refusing " + url)
    data = body.encode() if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=method, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            code = r.status
    except urllib.error.HTTPError as e:
        text, code = e.read().decode(errors="replace"), e.code
    try:
        return code, json.loads(text or "null")
    except ValueError:
        return code, {"text": text[:200]}


def credentials():
    meta = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview"],
                          capture_output=True, text=True, check=True).stdout
    email = next(ln.split("=", 1)[1].strip().strip('"') for ln in meta.splitlines() if '"acct"' in ln)
    pw = subprocess.run(["security", "find-generic-password", "-s", "dev.spike.libreview", "-a", email, "-w"],
                        capture_output=True, text=True, check=True).stdout.strip()
    if not email or not pw:
        raise SystemExit("no credentials")
    return email, pw


# ---------------------------------------------------------------- LibreLinkUp (read-only)

def cmd_llu(devices=False):
    email, pw = credentials()
    base = LLU
    h = {"Content-Type": "application/json", "Accept": "application/json", "product": "llu.android",
         "version": "4.17.0", "cache-control": "no-cache"}
    for _ in range(3):
        code, o = http("POST", base + "/llu/auth/login", json.dumps({"email": email, "password": pw}), h)
        d = (o or {}).get("data") or {}
        if (o or {}).get("status") == 0 and d.get("redirect") and re.fullmatch(r"[a-z]{2,3}", d.get("region", "")):
            base = "https://api-%s.libreview.io" % d["region"]
            continue
        break
    pw = None
    token = (d.get("authTicket") or {}).get("token") if (o or {}).get("status") == 0 else None
    print(stamp(), "llu login HTTP", code, "status", (o or {}).get("status"), "token" if token else "no token")
    if not token:
        return
    auth = dict(h, Authorization="Bearer " + token,
                **{"Account-Id": hashlib.sha256(str((d.get("user") or {}).get("id", "")).encode()).hexdigest()})
    code, o = http("GET", base + "/llu/connections", None, auth)
    conns = (o or {}).get("data") or []
    print(stamp(), "connections HTTP", code, len(conns))
    keep = ("did", "dtid", "v", "deviceId", "sn", "a", "w", "pt", "s", "lj", "FactoryTimestamp", "Timestamp", "ValueInMgPerDl")
    for c in conns:
        print("  connection: sensor", {k: v for k, v in (c.get("sensor") or {}).items() if k in keep},
              "patientDevice", {k: v for k, v in (c.get("patientDevice") or {}).items() if k in keep},
              "glucose", {k: v for k, v in (c.get("glucoseMeasurement") or {}).items() if k in keep})
        code, g = http("GET", base + "/llu/connections/%s/graph" % c["patientId"], None, auth)
        gd = (g or {}).get("data") or {}
        print(stamp(), "graph HTTP", code, "points", len(gd.get("graphData") or []))
        for s in gd.get("activeSensors") or []:
            print("  activeSensor: sensor", {k: v for k, v in (s.get("sensor") or {}).items() if k in keep},
                  "device", {k: v for k, v in (s.get("device") or {}).items() if k in keep})
        cg = (gd.get("connection") or {})
        print("  graph connection: sensor", {k: v for k, v in (cg.get("sensor") or {}).items() if k in keep},
              "patientDevice", {k: v for k, v in (cg.get("patientDevice") or {}).items() if k in keep})
    if not devices:
        return
    # the website's devices list (LibreView's own device `id` -> the upload's DeviceId `sn`), with this same token
    for product in ("llu.android", "lv"):
        hh = dict(auth, product=product)
        if product == "lv":
            hh["NEWYU-LV-Web-Version"] = "3.27.0.42"
        code, o = http("GET", base + "/glucoseHistory?numPeriods=1&period=14", None, hh)
        listed = ((o or {}).get("data") or {}).get("devices") if isinstance((o or {}).get("data"), dict) else None
        print(stamp(), "glucoseHistory product", product, "HTTP", code, "status", (o or {}).get("status"),
              "devices" if listed is not None else json.dumps(o)[:200])
        for dv in listed or []:
            print("  device", {k: v for k, v in dv.items() if k in ("id", "sn", "nickname", "type", "typeId", "dtid",
                                                                     "lastUpload", "lastUploadDate", "model")})
        if listed is not None:
            break


# ---------------------------------------------------------------- the app API

def config(gateway, country="GB"):
    if gateway == "libre3":
        _, a = http("GET", ASSETS)
        _, c = http("GET", a["Configuration"])
    else:
        _, c = http("GET", LIBRELINK_CONFIG.replace("_GB_", "_%s_" % country))
    return c["newYuUrl"].rstrip("/"), c.get("newYuApiKey", "")


def headers(gateway, key, token=""):
    if gateway == "libre3":
        return {"Content-Type": "application/json", "Platform": "Android", "Version": "3.3.0",
                "Abbott-ADC-App-Platform": "Android/16/FSL3/3.3.0.9092", "Accept-Language": CULTURE,
                "x-api-key": key, "x-newyu-token": token}
    return {"Content-Type": "application/json; charset=utf-8", "Accept-Language": CULTURE + ", en;q=0.8",
            "Abbott-ADC-App-Platform": "Android/16/FSLL/2.10.1.10406"}


def signin(device_id, gateway="libre3", take_over=False, country="GB"):
    base, key = config(gateway, country)
    email, pw = credentials()
    body = json.dumps({"Culture": CULTURE, "DeviceId": device_id, "Password": pw, "SetDevice": take_over,
                       "UserName": email, "Domain": "Libreview", "GatewayType": GATEWAY[gateway]}, separators=(",", ":"))
    pw = None
    code, o = http("POST", base + "/api/nisperson/getauthentication", body, headers(gateway, key))
    st, reason = (o or {}).get("status"), (o or {}).get("reason")
    print(stamp(), "signin", gateway, device_id[:8], "take-over" if take_over else "no take-over",
          "HTTP", code, "status", st, "reason", reason, "at", base.split("/")[2])
    r = (o or {}).get("result")
    if st != 0:
        print("  answer", json.dumps(o)[:400])
    elif isinstance(r, dict):
        # the keys, and the values of those that could name a device (never the token or the person's fields)
        print("  result keys", sorted(r))
        print("  device-like", {k: v for k, v in r.items() if re.search(r"device|gateway|domain|channel", k, re.I)})
    token = (r or {}).get("UserToken") if st == 0 else None
    return (base, key, token) if token else None


def utc_min(t):
    return t.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:00Z")


def local_min(t):
    off = int(t.utcoffset().total_seconds()) // 60
    return t.strftime("%Y-%m-%dT%H:%M:00") + "%s%02d:%02d" % ("+" if off >= 0 else "-", abs(off) // 60, abs(off) % 60)


def local_ms(t):
    off = int(t.utcoffset().total_seconds()) // 60
    return t.strftime("%Y-%m-%dT%H:%M:%S.000") + "%s%02d:%02d" % ("+" if off >= 0 else "-", abs(off) // 60, abs(off) % 60)


def record(tid, version, kind):
    """contracts/treatments/SPEC.md §3: first 5 bytes of SHA-256("<id>:<v>") << 8 | type (long 3, note 16)."""
    return int.from_bytes(hashlib.sha256(("%s:%d" % (tid, version)).encode()).digest()[:5], "big") << 8 | kind


def treatment_payload(device_id, token, now, tid, note):
    """Sukrara's /api/measurements body (SPEC §3) with one long-acting 0.1 u and one note, nothing else."""
    ins = ('{"insulinType":"LongActing","units":0.1,"extendedProperties":{"factoryTimestamp":"%s",'
           '"linkedGlucoseRecordNumber":"0"},"recordNumber":%d,"timestamp":"%s"}'
           % (utc_min(now), record(tid + "-i", 1, 3), local_min(now)))
    gen = ('{"type":"com.abbottdiabetescare.informatics.customnote","extendedProperties":{"factoryTimestamp":"%s",'
           '"text":%s,"linkedGlucoseRecordNumber":"0"},"recordNumber":%d,"timestamp":"%s"}'
           % (utc_min(now), json.dumps(note, ensure_ascii=False), record(tid + "-n", 1, 16), local_min(now)))
    return ('{"DeviceData":{"deviceSettings":{"factoryConfig":{"UOM":"mg/dL"},"firmwareVersion":"3.3.0",'
            '"miscellaneous":{"selectedLanguage":"%s","valueGlucoseTargetRangeLowInMgPerDl":70,'
            '"valueGlucoseTargetRangeHighInMgPerDl":140,"selectedTimeFormat":"24hr","selectedCarbType":"grams of carbs"},'
            '"timestamp":"%s"},"header":{"device":{"hardwareDescriptor":"GlucoBridge-spike","hardwareName":"GlucoBridge",'
            '"modelName":"com.freestylelibre3.app.de","osType":"Android","osVersion":"36","uniqueIdentifier":"%s"}},'
            '"measurementLog":{"capabilities":[%s],"currentGlucoseEntries":[],"foodEntries":[],"genericEntries":[%s],'
            '"insulinEntries":[%s],"scheduledContinuousGlucoseEntries":[],"unscheduledContinuousGlucoseEntries":[]}},'
            '"UserToken":%s,"Domain":"Libreview","GatewayType":"%s"}'
            % (CULTURE, local_ms(now), device_id, ",".join(json.dumps(c) for c in CAPS), gen, ins, json.dumps(token),
               GATEWAY["libre3"]))


def cmd_treatment(device_id, note, really):
    now = dt.datetime.now().astimezone()
    tid = "spike45-" + now.strftime("%Y%m%d%H%M")
    print(stamp(), "treatment", tid, "0.1 u long-acting +", repr(note), "as device", device_id[:8],
          "" if really else "(dry run: nothing sent)")
    if not really:
        return
    s = signin(device_id)
    if not s:
        return
    base, key, token = s
    code, o = http("POST", base + "/api/measurements", treatment_payload(device_id, token, now, tid, note),
                   headers("libre3", key, token))
    print(stamp(), "measurements HTTP", code, "status", (o or {}).get("status"), "reason", (o or {}).get("reason"),
          "result", json.dumps((o or {}).get("result"))[:200])


def cmd_tokens(device_id, note, really):
    """Whether a second sign-in with the same DeviceId ends the first token: sign in twice, then an upload with no
    entries on the first token, then the test treatment on the second (only while the Libre app's token is dead)."""
    if not really:
        raise SystemExit("needs --really")
    a = signin(device_id)
    b = signin(device_id)
    if not a or not b:
        return
    now = dt.datetime.now().astimezone()
    empty_upload(a, device_id, "FIRST token")
    tid = "spike45-" + now.strftime("%Y%m%d%H%M")
    code, o = http("POST", b[0] + "/api/measurements", treatment_payload(device_id, b[2], now, tid, note),
                   headers("libre3", b[1], b[2]))
    print(stamp(), "treatment", tid, "0.1 u long +", repr(note), "SECOND token: HTTP", code, "status",
          (o or {}).get("status"), "reason", (o or {}).get("reason"), "result", json.dumps((o or {}).get("result"))[:160])
    empty_upload(a, device_id, "FIRST token again")


def cmd_binding(app_device, ll_device, really):
    """Per account or per app identity: take over on the LibreLink identity (an old LibreLink DeviceId of the account),
    check the Libre 3 identity with the Libre app's DeviceId, and give the account back to it if it moved. No data."""
    if not really:
        raise SystemExit("needs --really")
    signin(ll_device, "librelink", take_over=True)
    l3 = signin(app_device)
    signin(ll_device, "librelink")
    if not l3:
        print(stamp(), "the active device moved: giving it back to", app_device[:8])
        signin(app_device, take_over=True)
        signin(app_device)
        signin(ll_device, "librelink")


def empty_upload(session, device_id, label):
    base, key, token = session
    body = treatment_payload(device_id, token, dt.datetime.now().astimezone(), "unused", "")
    body = re.sub(r'"genericEntries":\[.*?\],"insulinEntries":\[.*?\]', '"genericEntries":[],"insulinEntries":[]', body)
    code, o = http("POST", base + "/api/measurements", body, headers("libre3", key, token))
    print(stamp(), "empty upload,", label + ": HTTP", code, "status", (o or {}).get("status"), "reason", (o or {}).get("reason"))
    return (o or {}).get("status")


def cmd_cross(app_device, ll_device, really):
    """Does a LibreLink sign-in end a Libre 3 session? A Libre 3 token, a LibreLink sign-in, the Libre 3 token again."""
    if not really:
        raise SystemExit("needs --really")
    a = signin(app_device)
    if not a:
        return
    empty_upload(a, app_device, "Libre 3 token")
    signin(ll_device, "librelink")
    empty_upload(a, app_device, "the same Libre 3 token after a LibreLink sign-in")


LL_CAPS = ["scheduledContinuousGlucose", "unscheduledContinuousGlucose", "currentGlucose", "bloodGlucose", "insulin",
           "food"] + ["generic-com.abbottdiabetescare.informatics." + c for c in (
               "exercise", "customnote", "ondemandalarm.low", "ondemandalarm.high", "ondemandalarm.projectedlow",
               "ondemandalarm.projectedhigh", "sensorstart", "error", "isfGlucoseAlarm", "alarmSetting", "sensorend",
               "bleDisconnect", "bleReconnect")]


def librelink_payload(device_id, token, now, tid, note):
    """The LibreLink app's /api/measurements body (Juggluco libreview.cpp's template), one long-acting 0.1 u + a note."""
    ins = ('{"insulinType":"LongActing","units":0.1,"extendedProperties":{"factoryTimestamp":"%s",'
           '"linkedGlucoseRecordNumber":"0"},"recordNumber":%d,"timestamp":"%s"}'
           % (utc_min(now), record(tid + "-i", 1, 3), local_min(now)))
    gen = ('{"type":"com.abbottdiabetescare.informatics.customnote","extendedProperties":{"factoryTimestamp":"%s",'
           '"text":%s,"linkedGlucoseRecordNumber":"0"},"recordNumber":%d,"timestamp":"%s"}'
           % (utc_min(now), json.dumps(note, ensure_ascii=False), record(tid + "-n", 1, 16), local_min(now)))
    device = ('"device":{"hardwareDescriptor":"Sukrara-spike45","hardwareName":"Sukrara-spike45",'
              '"modelName":"com.freestylelibre.app.gb","osType":"Android","osVersion":"36","uniqueIdentifier":"%s"}'
              % device_id)
    return ('{"DeviceData":{"connectedDevices":{"insulinDevices":[]},"deviceSettings":{"factoryConfig":{"UOM":"mg/dL"},'
            '"firmwareVersion":"2.12.0","miscellaneous":{"isStreaming":false,"selectedLanguage":"%s",'
            '"valueGlucoseTargetRangeLowInMgPerDl":70,"valueGlucoseTargetRangeHighInMgPerDl":180,'
            '"selectedTimeFormat":"24hr","selectedCarbType":"grams of carbs"},"timestamp":"%s"},"forceUpload":false,'
            '"header":{%s},"measurementLog":{"bloodGlucoseEntries":[],"capabilities":[%s],"currentGlucoseEntries":[],%s,'
            '"foodEntries":[],"genericEntries":[%s],"insulinEntries":[%s],"ketoneEntries":[],'
            '"scheduledContinuousGlucoseEntries":[],"unscheduledContinuousGlucoseEntries":[]}},"UserToken":%s,'
            '"Domain":"Libreview","GatewayType":"%s"}'
            % (CULTURE, local_ms(now), device, ",".join(json.dumps(c) for c in LL_CAPS), device, gen, ins,
               json.dumps(token), GATEWAY["librelink"]))


def cmd_ll_treatment(device_id, note, take_over, really):
    """Sukrara's treatments as the LibreLink app: sign in on the LibreLink identity (take over only when asked) and
    send one test treatment; then the Libre 3 identity's DeviceId is checked by --check-libre3."""
    now = dt.datetime.now().astimezone()
    tid = "spike45ll-" + now.strftime("%Y%m%d%H%M")
    print(stamp(), "LibreLink treatment", tid, "0.1 u long-acting +", repr(note), "as device", device_id[:8],
          "" if really else "(dry run: nothing sent)")
    if not really:
        return
    s = signin(device_id, "librelink", take_over)
    if not s:
        return
    base, key, token = s
    code, o = http("POST", base + "/api/measurements", librelink_payload(device_id, token, now, tid, note),
                   {"Content-Type": "application/json"})
    print(stamp(), "measurements HTTP", code, "status", (o or {}).get("status"), "reason", (o or {}).get("reason"),
          "result", json.dumps((o or {}).get("result"))[:200])


def ll_empty_upload(session, device_id, label):
    base, _, token = session
    body = librelink_payload(device_id, token, dt.datetime.now().astimezone(), "unused", "")
    body = re.sub(r'"genericEntries":\[.*?\],"insulinEntries":\[.*?\]', '"genericEntries":[],"insulinEntries":[]', body)
    code, o = http("POST", base + "/api/measurements", body, {"Content-Type": "application/json"})
    print(stamp(), "LibreLink empty upload,", label + ": HTTP", code, "status", (o or {}).get("status"), "reason",
          (o or {}).get("reason"))
    return (o or {}).get("status")


def cmd_ll_tokens(device_id, really):
    """Test D on the LibreLink identity (docs/45b): two sign-ins with the same DeviceId, then an upload of nothing on
    each token. Only while no real app holds that identity."""
    if not really:
        raise SystemExit("needs --really")
    a = signin(device_id, "librelink")
    b = signin(device_id, "librelink")
    if not a or not b:
        return
    ll_empty_upload(a, device_id, "FIRST token")
    ll_empty_upload(b, device_id, "SECOND token")
    ll_empty_upload(a, device_id, "FIRST token again")


def cmd_fixture():
    """contracts/treatments/libreview-librelink-fixture.json: Sukrara's LibreLink body (the template above with
    Sukrara's device names and the Libre 3 body's settings: target 70-140) over libreview-fixture.json's treatments, the
    entries byte for byte the Libre 3 body's; the official app's identity from LibreLinkUp's device type, and where an
    answer says it. Offline: nothing is sent."""
    import os
    from zoneinfo import ZoneInfo
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "contracts", "treatments")
    with open(os.path.join(root, "libreview-fixture.json")) as f:
        l3 = json.load(f)
    p = l3["expectedPayload"]

    def between(a, b):
        i = p.index(a) + len(a)
        return p[i:p.index(b, i)]
    food = between('"foodEntries":[', '],"genericEntries":[')
    generic = between('"genericEntries":[', '],"insulinEntries":[')
    insulin = between('"insulinEntries":[', '],"scheduledContinuousGlucoseEntries":[')
    now = dt.datetime.fromtimestamp(l3["now"], ZoneInfo(l3["zone"]))
    device = ('"device":{"hardwareDescriptor":"Sukrara","hardwareName":"Sukrara","modelName":"com.freestylelibre.app.gb",'
              '"osType":"Android","osVersion":"36","uniqueIdentifier":%s}' % json.dumps(l3["deviceId"]))
    body = ('{"DeviceData":{"connectedDevices":{"insulinDevices":[]},"deviceSettings":{"factoryConfig":{"UOM":"mg/dL"},'
            '"firmwareVersion":"2.12.0","miscellaneous":{"isStreaming":false,"selectedLanguage":"%s",'
            '"valueGlucoseTargetRangeLowInMgPerDl":70,"valueGlucoseTargetRangeHighInMgPerDl":140,'
            '"selectedTimeFormat":"24hr","selectedCarbType":"grams of carbs"},"timestamp":"%s"},"forceUpload":false,'
            '"header":{%s},"measurementLog":{"bloodGlucoseEntries":[],"capabilities":[%s],"currentGlucoseEntries":[],%s,'
            '"foodEntries":[%s],"genericEntries":[%s],"insulinEntries":[%s],"ketoneEntries":[],'
            '"scheduledContinuousGlucoseEntries":[],"unscheduledContinuousGlucoseEntries":[]}},"UserToken":%s,'
            '"Domain":"Libreview","GatewayType":"%s"}'
            % (CULTURE, local_ms(now), device, ",".join(json.dumps(c) for c in LL_CAPS), device, food, generic, insulin,
               json.dumps(l3["token"]), GATEWAY["librelink"]))
    json.loads(body)

    def graph(conn_dtid, active_dtid):
        data = {"connection": {"patientId": "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"}}
        if conn_dtid is not None:
            data["connection"]["patientDevice"] = {"did": "synthetic-device", "dtid": conn_dtid, "v": "3.6.10"}
        if active_dtid is not None:
            data["activeSensors"] = [{"sensor": {"sn": "0D00ABC123", "a": 1794744000},
                                      "device": {"did": "synthetic-device", "dtid": active_dtid}}]
        return {"status": 0, "data": data}
    out = {
        "_about": [
            "Sukrara's treatments as the LibreLink app (contracts/treatments/SPEC.md section 3 'LibreLink', docs/45),",
            "beside the official Libre 3 app. 'expectedPayload': libreview-fixture.json's treatments (same zone, now,",
            "deviceId, token) in LibreLink's /api/measurements body; its entry arrays are byte for byte the Libre 3 body's.",
            "'identities': LibreLinkUp's patientDevice.dtid -> [the identity the official app uploads under, the one",
            "Sukrara sends under]; null = unknown: nothing goes. 'deviceType': where a graph answer says the dtid",
            "(data.connection.patientDevice first, then data.activeSensors[0].device). Generated by",
            "investigations/45-libreview-two-uploaders/lv45.py fixture.",
        ],
        "zone": l3["zone"], "now": l3["now"], "deviceId": l3["deviceId"], "token": l3["token"],
        "treatments": "libreview-fixture.json",
        "expectedPayload": body,
        "identities": [[40068, "libre3", "librelink"], [40066, "librelink", "libre3"], [40067, "librelink", "libre3"],
                       [40069, None, None], [0, None, None], [None, None, None]],
        "deviceType": [
            {"name": "the connection's patient device", "body": graph(40068, 40068), "dtid": 40068},
            {"name": "the connection's wins over the active sensor's", "body": graph(40066, 40068), "dtid": 40066},
            {"name": "only the active sensor's device", "body": graph(None, 40067), "dtid": 40067},
            {"name": "none said", "body": graph(None, None), "dtid": None},
        ],
    }
    with open(os.path.join(root, "libreview-librelink-fixture.json"), "w") as f:
        json.dump(out, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("wrote libreview-librelink-fixture.json")


def main(a):
    if not a:
        print(__doc__)
    elif a[0] == "ll-tokens":
        cmd_ll_tokens(a[1], "--really" in a)
    elif a[0] == "fixture":
        cmd_fixture()
    elif a[0] == "llu":
        cmd_llu("--devices" in a)
    elif a[0] == "signin":
        take = "--take-over" in a
        if "--really" not in a:
            raise SystemExit("a sign-in ends the identity's current session (docs/45): needs --really")
        signin(a[1], a[a.index("--gateway") + 1] if "--gateway" in a else "libre3", take,
               a[a.index("--country") + 1] if "--country" in a else "GB")
    elif a[0] == "ll-treatment":
        if "--take-over" in a and "--really" not in a:
            raise SystemExit("a take-over needs --really")
        cmd_ll_treatment(a[1], a[2], "--take-over" in a, "--really" in a)
    elif a[0] == "cross":
        cmd_cross(a[1], a[2], "--really" in a)
    elif a[0] == "binding":
        cmd_binding(a[1], a[2], "--really" in a)
    elif a[0] == "tokens":
        cmd_tokens(a[1], a[2], "--really" in a)
    elif a[0] == "treatment":
        cmd_treatment(a[1], a[2], "--really" in a)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
