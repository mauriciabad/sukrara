# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Writes the Nightscout source fixtures (python3 -I generate.py): entries.json, treatments.json, sites.json, poll.json.
# The cases and their expectations are written here by hand; this script only computes the ids (UUID v5) and the
# times, so a reviewer reads one file. Change a case here, run it, review the diff.
import json
import os
import uuid
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
NS = uuid.UUID(bytes=b"NightscoutRocks!")  # Nightscout's own v5 namespace, as the upload's identifiers use
T0 = 1792000000  # 2026-10-14 17:46:40 UTC


def pid(key, field):
    """The Sukrara id of a site treatment's entry: field = insulin | carbs | note."""
    return str(uuid.uuid5(NS, f"sukrara-ns_{key}_{field}"))


def iso(s):
    return datetime.fromtimestamp(s, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def ms(s):
    return s * 1000


def dump(name, obj):
    with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


# ---------------------------------------------------------------- entries.json
entries = []
E = entries.append
E({"name": "an xDrip+ entry with its delta", "device": None,
   "body": [{"_id": "6730a1", "type": "sgv", "sgv": 118, "date": ms(T0), "dateString": iso(T0), "direction": "Flat",
             "delta": 2.5, "device": "xDrip-DexcomG7"}],
   "readings": [[T0, 118, 0.5]]})
E({"name": "newest first in, oldest first out; an entry without a delta has no rate", "device": None,
   "body": [{"type": "sgv", "sgv": 120, "date": ms(T0 + 300)},
            {"type": "sgv", "sgv": 110, "date": ms(T0), "direction": "FortyFiveUp"}],
   "readings": [[T0, 110, None], [T0 + 300, 120, None]]})
E({"name": "calibrations and finger sticks are not glucose readings", "device": None,
   "body": [{"type": "mbg", "mbg": 104, "date": ms(T0 + 60)}, {"type": "cal", "slope": 1000, "date": ms(T0 + 30)},
            {"type": "sgv", "sgv": 101, "date": ms(T0)}],
   "readings": [[T0, 101, None]]})
E({"name": "an entry without a type but with an sgv counts", "device": None,
   "body": [{"sgv": 99, "date": ms(T0)}], "readings": [[T0, 99, None]]})
E({"name": "the time from mills, then from dateString with its offset", "device": None,
   "body": [{"type": "sgv", "sgv": 130, "mills": ms(T0 + 60)},
            {"type": "sgv", "sgv": 128, "dateString": "2026-10-14T19:46:40.000+02:00"}],
   "readings": [[T0, 128, None], [T0 + 60, 130, None]]})
E({"name": "an sgv sent as text is read, rounded", "device": None,
   "body": [{"type": "sgv", "sgv": "142.6", "date": ms(T0)}], "readings": [[T0, 143, None]]})
E({"name": "entries without a time or a number are skipped", "device": None,
   "body": [{"type": "sgv", "sgv": 100}, {"type": "sgv", "date": ms(T0)},
            {"type": "sgv", "sgv": "high", "date": ms(T0 + 60)}, {"type": "sgv", "sgv": 105, "date": ms(T0 + 120)}],
   "readings": [[T0 + 120, 105, None]]})
E({"name": "LO and HI arrive as Nightscout's 39 and 401", "device": None,
   "body": [{"type": "sgv", "sgv": 401, "date": ms(T0 + 300)}, {"type": "sgv", "sgv": 39, "date": ms(T0)}],
   "readings": [[T0, 39, None], [T0 + 300, 401, None]]})
E({"name": "this install's own upload is never read back; another Sukrara's is", "device": "Sukrara-1a2b3c",
   "body": [{"type": "sgv", "sgv": 150, "date": ms(T0 + 120), "device": "Sukrara-1a2b3c", "app": "Sukrara"},
            {"type": "sgv", "sgv": 151, "date": ms(T0 + 60), "device": "Sukrara-1a2b3c · Dexcom G7 (untested)",
             "app": "Sukrara"},
            {"type": "sgv", "sgv": 149, "date": ms(T0), "device": "Sukrara-9f8e7d", "app": "Sukrara"}],
   "readings": [[T0, 149, None]]})
E({"name": "two entries at the same time: the first in the body stays", "device": None,
   "body": [{"type": "sgv", "sgv": 112, "date": ms(T0), "device": "xDrip"},
            {"type": "sgv", "sgv": 111, "date": ms(T0), "device": "AndroidAPS"}],
   "readings": [[T0, 112, None]]})
E({"name": "a falling delta", "device": None,
   "body": [{"type": "sgv", "sgv": 80, "date": ms(T0), "delta": -12, "direction": "SingleDown"}],
   "readings": [[T0, 80, -2.4]]})
E({"name": "an empty site", "device": None, "body": [], "readings": []})

dump("entries.json", {
    "_about": [
        "A Nightscout site's glucose entries (GET /api/v1/entries/sgv.json, newest first) -> the readings Sukrara stores",
        "(reading source id 'nightscout', docs/44 step 1). 'body' -> 'readings' ([epoch s, mg/dL, rate mg/dL/min or null],",
        "oldest first, one per time). An entry counts when it is a glucose entry ('type' 'sgv', or no 'type' with an",
        "'sgv'); its time is 'date' (ms), else 'mills' (ms), else 'dateString' (ISO 8601 with its offset); 'sgv' is a",
        "number (a numeric string too, rounded half up); the rate is Nightscout's 5-minute 'delta' / 5 when 'delta' is a",
        "number, else none (Sukrara's trend then comes from the readings, contracts/trend). Values outside 40-400 are kept",
        "as sent: the hub makes them LO / HI and drops impossible ones (GlucoseRange). The own-loop guard (docs/44 'Data'",
        "rule 4): an entry this install uploaded ('device' is this install's upload device name 'device', alone or",
        "followed by ' · ') is never read back; another Sukrara's entries are (a Mac reading the site the phone uploads",
        "to). Two entries at the same time: the first in the body (the newest written) stays. Generated by generate.py.",
    ],
    "cases": entries,
})

# ---------------------------------------------------------------- treatments.json
W = [T0 - 86400, T0 + 600]  # the window read: the last 24 h
DEV = "Sukrara-1a2b3c"
OWN = "6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab"
OWN2 = "0b7d2f4e-9a61-4c3b-8e2d-5f6a7b8c9d0e"
cases = []
T = cases.append


def doc(_id, t, **kw):
    d = {"_id": _id, "created_at": iso(t)}
    d.update(kw)
    return d


T({"name": "an AndroidAPS meal bolus becomes two read-only entries: insulin and carbs",
   "site": [doc("a1", T0 - 600, eventType="Meal Bolus", insulin=4.5, carbs=40, enteredBy="AndroidAPS")],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [{"id": pid("a1", "insulin"), "k": "rapid", "a": 4.5, "t": T0 - 600, "n": ""},
                         {"id": pid("a1", "carbs"), "k": "carbs", "a": 40, "t": T0 - 600, "n": ""}],
              "delete": [], "push": [], "remove": []}})
T({"name": "the identifier names the entry when the site has one; the note goes with it",
   "site": [doc("a2", T0 - 300, identifier="ext-77", eventType="Carb Correction", carbs=15, notes="juice")],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [{"id": pid("ext-77", "carbs"), "k": "carbs", "a": 15, "t": T0 - 300, "n": "juice"}],
              "delete": [], "push": [], "remove": []}})
T({"name": "the loop's own insulin is not a treatment the person gave: SMBs and temp basals are skipped",
   "site": [doc("s1", T0 - 900, eventType="Correction Bolus", insulin=0.3, isSMB=True, type="SMB"),
            doc("s2", T0 - 800, eventType="SMB", insulin=0.2),
            doc("s3", T0 - 700, eventType="Temp Basal", rate=0.8, duration=30, insulin=0.4),
            doc("s4", T0 - 650, eventType="Temporary Target", targetTop=140),
            doc("s5", T0 - 600, eventType="Profile Switch"),
            doc("s6", T0 - 550, eventType="Site Change"),
            doc("s7", T0 - 500, eventType="Sensor Start")],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "a note, and a long-acting dose as another Sukrara writes it",
   "site": [doc("n1", T0 - 400, eventType="Note", notes="run"),
            doc("n2", T0 - 300, eventType="Note", insulinType="long-acting", notes="Long-acting insulin 12 U · morning",
                app="Sukrara", device="Sukrara-9f8e7d", identifier="9e1c2b9a-0d3e-4c5f-8a7b-0123456789aa"),
            doc("n3", T0 - 200, eventType="Correction Bolus", insulin=10, insulinType="Long-acting (Tresiba)")],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [{"id": pid("n1", "note"), "k": "note", "a": 0, "t": T0 - 400, "n": "run"},
                         {"id": pid("9e1c2b9a-0d3e-4c5f-8a7b-0123456789aa", "insulin"), "k": "long", "a": 12,
                          "t": T0 - 300, "n": "morning"},
                         {"id": pid("n3", "insulin"), "k": "long", "a": 10, "t": T0 - 200, "n": ""}],
              "delete": [], "push": [], "remove": []}})
T({"name": "an entry already pulled and unchanged: nothing to do",
   "site": [doc("a3", T0 - 600, eventType="Correction Bolus", insulin=2)],
   "local": [{"id": pid("a3", "insulin"), "k": "rapid", "a": 2, "t": T0 - 600, "n": "", "u": ms(T0 - 500), "del": False}],
   "ledger": {"pulled": [pid("a3", "insulin")], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "edited on the site: the new version replaces the pulled one",
   "site": [doc("a3", T0 - 600, eventType="Correction Bolus", insulin=2.5)],
   "local": [{"id": pid("a3", "insulin"), "k": "rapid", "a": 2, "t": T0 - 600, "n": "", "u": ms(T0 - 500), "del": False}],
   "ledger": {"pulled": [pid("a3", "insulin")], "pushed": {}},
   "expect": {"upsert": [{"id": pid("a3", "insulin"), "k": "rapid", "a": 2.5, "t": T0 - 600, "n": ""}],
              "delete": [], "push": [], "remove": []}})
T({"name": "deleted on the site (gone, or isValid false): deleted in Sukrara",
   "site": [doc("a5", T0 - 400, eventType="Carb Correction", carbs=20, isValid=False)],
   "local": [{"id": pid("a4", "insulin"), "k": "rapid", "a": 3, "t": T0 - 600, "n": "", "u": ms(T0 - 500), "del": False},
             {"id": pid("a5", "carbs"), "k": "carbs", "a": 20, "t": T0 - 400, "n": "", "u": ms(T0 - 300), "del": False}],
   "ledger": {"pulled": [pid("a4", "insulin"), pid("a5", "carbs")], "pushed": {}},
   "expect": {"upsert": [], "delete": [pid("a4", "insulin"), pid("a5", "carbs")], "push": [], "remove": []}})
T({"name": "a pulled entry outside the window read is left alone",
   "site": [],
   "local": [{"id": pid("old", "carbs"), "k": "carbs", "a": 30, "t": W[0] - 60, "n": "", "u": ms(W[0]), "del": False}],
   "ledger": {"pulled": [pid("old", "carbs")], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "Sukrara's own new entry goes to the site",
   "site": [],
   "local": [{"id": OWN, "k": "rapid", "a": 2.5, "t": T0 - 120, "n": "", "u": ms(T0 - 100), "del": False}],
   "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [OWN], "remove": []}})
T({"name": "Sukrara's own entry read back from the site is recognised, not copied",
   "site": [doc("x1", T0 - 120, identifier=OWN, eventType="Correction Bolus", insulin=2.5, app="Sukrara", device=DEV)],
   "local": [{"id": OWN, "k": "rapid", "a": 2.5, "t": T0 - 120, "n": "", "u": ms(T0 - 100), "del": False}],
   "ledger": {"pulled": [], "pushed": {OWN: ms(T0 - 100)}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "edited in Sukrara after it went up: it goes up again",
   "site": [doc("x1", T0 - 120, identifier=OWN, eventType="Correction Bolus", insulin=2.5, app="Sukrara", device=DEV)],
   "local": [{"id": OWN, "k": "rapid", "a": 3, "t": T0 - 120, "n": "", "u": ms(T0 - 50), "del": False}],
   "ledger": {"pulled": [], "pushed": {OWN: ms(T0 - 100)}},
   "expect": {"upsert": [], "delete": [], "push": [OWN], "remove": []}})
T({"name": "Sukrara's own entry edited on the site: the site's version comes back",
   "site": [doc("x1", T0 - 60, identifier=OWN, eventType="Correction Bolus", insulin=2, app="Sukrara", device=DEV)],
   "local": [{"id": OWN, "k": "rapid", "a": 2.5, "t": T0 - 120, "n": "", "u": ms(T0 - 100), "del": False}],
   "ledger": {"pulled": [], "pushed": {OWN: ms(T0 - 100)}},
   "expect": {"upsert": [{"id": OWN, "k": "rapid", "a": 2, "t": T0 - 60, "n": ""}], "delete": [], "push": [],
              "remove": []}})
T({"name": "Sukrara's own entry deleted on the site: deleted in Sukrara",
   "site": [],
   "local": [{"id": OWN, "k": "rapid", "a": 2.5, "t": T0 - 120, "n": "", "u": ms(T0 - 100), "del": False}],
   "ledger": {"pulled": [], "pushed": {OWN: ms(T0 - 100)}},
   "expect": {"upsert": [], "delete": [OWN], "push": [], "remove": []}})
T({"name": "deleted in Sukrara: removed from the site; one never sent sends nothing",
   "site": [doc("x1", T0 - 120, identifier=OWN, eventType="Correction Bolus", insulin=2.5, app="Sukrara", device=DEV)],
   "local": [{"id": OWN, "k": "rapid", "a": 2.5, "t": T0 - 120, "n": "", "u": ms(T0 - 30), "del": True},
             {"id": OWN2, "k": "carbs", "a": 10, "t": T0 - 90, "n": "", "u": ms(T0 - 20), "del": True}],
   "ledger": {"pulled": [], "pushed": {OWN: ms(T0 - 100)}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": [OWN]}})
T({"name": "an entry of this install found by its device when the identifier was lost",
   "site": [doc("x2", T0 - 120, eventType="Carb Correction", carbs=12, app="Sukrara", device=DEV)],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "a pulled entry is never sent back, even after the site deletes it",
   "site": [],
   "local": [{"id": pid("a6", "carbs"), "k": "carbs", "a": 25, "t": T0 - 300, "n": "", "u": ms(T0 - 200), "del": True}],
   "ledger": {"pulled": [pid("a6", "carbs")], "pushed": {}},
   "expect": {"upsert": [], "delete": [], "push": [], "remove": []}})
T({"name": "times from date when created_at is missing; zero amounts are not treatments",
   "site": [{"_id": "d1", "date": ms(T0 - 240), "eventType": "Bolus", "insulin": 1.5},
            {"_id": "d2", "date": ms(T0 - 230), "eventType": "Meal Bolus", "insulin": 0, "carbs": 0},
            {"_id": "d3", "eventType": "Bolus", "insulin": 1}],
   "local": [], "ledger": {"pulled": [], "pushed": {}},
   "expect": {"upsert": [{"id": pid("d1", "insulin"), "k": "rapid", "a": 1.5, "t": T0 - 240, "n": ""}],
              "delete": [], "push": [], "remove": []}})

for c in cases:
    c["expect"]["upsert"].sort(key=lambda e: (e["t"], e["id"]))
    for k in ("delete", "push", "remove"):
        c["expect"][k].sort()

dump("treatments.json", {
    "_about": [
        "Treatments both ways with a Nightscout source (docs/44 'Data', Treatments): one sync over a window the app has",
        "just read in full ('window' [from, to] epoch s; the site's treatments with created_at in it, GET",
        "/api/v1/treatments.json) -> what to change. Inputs: 'site' (the documents read), 'local' (this device's",
        "treatments with t in the window: id, k rapid|long|carbs|note, a, t, n, u ms, del), 'ledger' ('pulled': the ids",
        "this device took from the site; 'pushed': id -> the u that went up), 'device' (this install's name on the site).",
        "Outputs: 'upsert' (entries to write here, id, k, a, t, n; read-only when pulled), 'delete' (ids to delete here),",
        "'push' (own entries to create or replace on the site, by identifier = id), 'remove' (own entries to delete",
        "there). Rules: a site document stands for up to three entries, its 'insulin' (> 0; long-acting when",
        "'insulinType' says long, else rapid), its 'carbs' (> 0) and, with neither, a 'Note' / 'Announcement' with notes;",
        "a Note with insulinType long-acting and 'Long-acting insulin <n> U[ · note]' (how Sukrara writes one) is that dose.",
        "Its id is UUID v5 (Nightscout's namespace 'NightscoutRocks!') of 'sukrara-ns_<identifier, else _id>_<insulin|",
        "carbs|note>', so reading it again never copies it. Skipped: the loop's own insulin (isSMB, type or eventType",
        "SMB, Temp Basal) and every other event (Temporary Target, Profile Switch, Site / Sensor events: sensor life from",
        "the site is step 2's), a document without a time (created_at, else date / mills ms), isValid false (= deleted).",
        "Ours (identifier = a local or a pushed id, or app Sukrara with this install's device) are never pulled: edited there with",
        "nothing changed here since it went up -> the site's version comes back (upsert, same id); deleted there -> delete",
        "here. Own entries: new or changed since they went up -> push; deleted and once sent -> remove. Pulled entries",
        "missing from the read -> delete; never pushed. Entries outside the window are left alone. Order: upsert by time,",
        "then id; delete, push and remove by id. Generated by generate.py.",
    ],
    "window": W,
    "device": DEV,
    "cases": cases,
})

# ---------------------------------------------------------------- sites.json
dump("sites.json", {
    "_about": [
        "The Nightscout address a person types or pastes (setup's Connect, Settings › Source) -> the site Sukrara reads:",
        "'parse': input -> 'root' (scheme + host [+ port] + path, lowercase scheme and host, without the default port,",
        "a trailing '/', or an API path '/api/v1' / '/api/v3' at its end), 'token' (a '?token=' in the pasted address,",
        "Nightscout's way of sharing a site with a token, taken out of the address) and 'problem' (url: not a web address,",
        "or one with a user name; https: plain http to anything but this device, docs CLAUDE.md 'Cleartext'). No scheme =",
        "https. 'same': two addresses are the same site when their roots are equal (docs/44 'Data' rule 2: glucose from",
        "site X never goes back to X; Nightscout upload to the same site is off while it is the source).",
    ],
    "parse": [
        {"input": "https://ns.example.org", "root": "https://ns.example.org", "token": None, "problem": None},
        {"input": "  https://ns.example.org/  ", "root": "https://ns.example.org", "token": None, "problem": None},
        {"input": "ns.example.org", "root": "https://ns.example.org", "token": None, "problem": None},
        {"input": "HTTPS://NS.Example.ORG:443/", "root": "https://ns.example.org", "token": None, "problem": None},
        {"input": "https://ns.example.org:8443", "root": "https://ns.example.org:8443", "token": None, "problem": None},
        {"input": "https://ns.example.org/?token=reader-0123456789abcdef", "root": "https://ns.example.org",
         "token": "reader-0123456789abcdef", "problem": None},
        {"input": "https://ns.example.org/api/v1/", "root": "https://ns.example.org", "token": None, "problem": None},
        {"input": "https://host.example.org/ana/", "root": "https://host.example.org/ana", "token": None,
         "problem": None},
        {"input": "http://ns.example.org", "root": "http://ns.example.org", "token": None, "problem": "https"},
        {"input": "http://192.168.1.20:1337", "root": "http://192.168.1.20:1337", "token": None, "problem": "https"},
        {"input": "http://127.0.0.1:1337", "root": "http://127.0.0.1:1337", "token": None, "problem": None},
        {"input": "http://localhost:1337/", "root": "http://localhost:1337", "token": None, "problem": None},
        {"input": "https://user:pass@ns.example.org", "root": "https://user:pass@ns.example.org", "token": None,
         "problem": "url"},
        {"input": "ftp://ns.example.org", "root": "ftp://ns.example.org", "token": None, "problem": "url"},
        {"input": "", "root": "", "token": None, "problem": "url"},
        {"input": "not a site", "root": "not a site", "token": None, "problem": "url"},
    ],
    "same": [
        ["https://ns.example.org", "https://NS.example.org/", True],
        ["https://ns.example.org", "https://ns.example.org:443", True],
        ["https://ns.example.org", "https://ns.example.org/?token=reader-0123456789abcdef", True],
        ["https://ns.example.org/api/v1", "https://ns.example.org", True],
        ["https://ns.example.org", "ns.example.org", True],
        ["https://ns.example.org", "https://other.example.org", False],
        ["https://host.example.org/ana", "https://host.example.org/ben", False],
        ["https://ns.example.org", "https://ns.example.org:8443", False],
    ],
})

# ---------------------------------------------------------------- poll.json
dump("poll.json", {
    "_about": [
        "When a Nightscout source asks the site again (docs/44 'Living with two apps': as live as possible without",
        "asking all the time). After an answer: the next reading is expected one cadence after the newest ('cadence_s':",
        "the gap between the two newest readings, clamped to [min_cadence_s, max_cadence_s]; one reading or none:",
        "max_cadence_s) plus 'upload_lag_s', and the source asks then, never sooner than 'min_s' from now (an empty site:",
        "every 'late_s'); once it is",
        "overdue it asks every 'overdue_s', and every 'late_s' once it is more than 'late_after_s' overdue. After a",
        "failure: 'backoff_s' by the number of failures in a row (the last value from then on); rate-limited: the site's",
        "Retry-After when longer than the backoff; refused (signed out): 'refused_s', or at once when the person",
        "saves another token. Cases: 'now', 'newest' / 'previous' (reading times, null = none), 'failures' (in a row,",
        "0 = the last call answered), 'retryAfter_s', 'refused' -> 'wait_s'.",
    ],
    "min_s": 15, "upload_lag_s": 45, "min_cadence_s": 60, "max_cadence_s": 300, "overdue_s": 30, "late_s": 60,
    "late_after_s": 300, "backoff_s": [30, 60, 120, 300], "refused_s": 1800,
    "cases": [
        {"name": "a 5-minute uploader: ask 45 s after the next reading is due", "now": T0 + 60, "newest": T0,
         "previous": T0 - 300, "failures": 0, "wait_s": 285},
        {"name": "a 1-minute uploader (xDrip+ with a Libre 2)", "now": T0 + 10, "newest": T0, "previous": T0 - 60,
         "failures": 0, "wait_s": 95},
        {"name": "gaps longer than 5 min count as 5", "now": T0 + 30, "newest": T0, "previous": T0 - 900,
         "failures": 0, "wait_s": 315},
        {"name": "one reading: 5 minutes", "now": T0, "newest": T0, "previous": None, "failures": 0, "wait_s": 345},
        {"name": "never sooner than 15 s", "now": T0 + 340, "newest": T0, "previous": T0 - 300, "failures": 0,
         "wait_s": 15},
        {"name": "overdue: every 30 s", "now": T0 + 400, "newest": T0, "previous": T0 - 300, "failures": 0,
         "wait_s": 30},
        {"name": "long overdue (the uploader is silent): every minute", "now": T0 + 700, "newest": T0,
         "previous": T0 - 300, "failures": 0, "wait_s": 60},
        {"name": "an empty site: every minute", "now": T0, "newest": None, "previous": None, "failures": 0,
         "wait_s": 60},
        {"name": "first failure", "now": T0, "newest": T0 - 60, "previous": T0 - 360, "failures": 1, "wait_s": 30},
        {"name": "third failure", "now": T0, "newest": T0 - 60, "previous": T0 - 360, "failures": 3, "wait_s": 120},
        {"name": "many failures: every 5 min", "now": T0, "newest": None, "previous": None, "failures": 9,
         "wait_s": 300},
        {"name": "rate-limited: the site's Retry-After", "now": T0, "newest": None, "previous": None, "failures": 1,
         "retryAfter_s": 600, "wait_s": 600},
        {"name": "rate-limited with a short Retry-After: the backoff", "now": T0, "newest": None, "previous": None,
         "failures": 2, "retryAfter_s": 5, "wait_s": 60},
        {"name": "refused: half an hour, or a new token", "now": T0, "newest": T0 - 60, "previous": None,
         "failures": 1, "refused": True, "wait_s": 1800},
    ],
})
