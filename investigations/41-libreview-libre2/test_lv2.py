#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""lv2.py offline: the 5-minute values, the fixture, and every call against a fake LibreView on loopback.

  python3 -I investigations/41-libreview-libre2/test_lv2.py
"""
import contextlib
import io
import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)


class Fake(BaseHTTPRequestHandler):
    """LibreView as investigation 04 measured it: one active device per account (Sukrara's) unless SetDevice."""
    calls = []
    active = {"libre3": "sukrara-device"}

    def log_message(self, *a):
        pass

    def answer(self, o):
        b = json.dumps(o).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        base = "http://127.0.0.1:%d" % self.server.server_port
        if self.path == "/assets":
            return self.answer({"Configuration": base + "/config3"})
        if self.path in ("/config3", "/configll"):
            return self.answer({"newYuUrl": base + "/api" + self.path[-1], "newYuApiKey": "KEY"})
        if self.path.startswith("/glucoseHistory"):
            if self.headers["Account-Id"] != "0bfe935e70c321c7ca3afc75ce0d0ca2f98b5422e008bb31c00c6d7f1f1c0ad6":
                return self.answer({"message": "RequiredHeaderMissing"})
            # the website's devices: LibreView's own id, the upload's DeviceId as "sn"
            return self.answer({"status": 0, "data": {"devices": [{"id": "lv-1", "sn": "DEV-1", "nickname": ""},
                                                                  {"id": "lv-2", "sn": "DEV-2", "nickname": ""}]}})
        self.send_error(404)

    def body(self):
        return json.loads(self.rfile.read(int(self.headers["Content-Length"])))

    def do_POST(self):
        o = self.body()
        Fake.calls.append((self.command, self.path, o, dict(self.headers)))
        if self.path.endswith("/nisperson/getauthentication"):
            g = "libre3" if o["GatewayType"] == "FSLibreLink3.Android" else "librelink"
            have = Fake.active.get(g)
            if have and have != o["DeviceId"] and not o["SetDevice"]:
                return self.answer({"status": 20, "reason": "wrongDeviceForUser"})
            Fake.active[g] = o["DeviceId"]
            return self.answer({"status": 0, "result": {"UserToken": "T-" + g, "AccountId": "A", "DateOfBirth": "19900101",
                                                        "FirstName": "F", "LastName": "L", "GuardianFirstName": "",
                                                        "GuardianLastName": ""}})
        if self.path == "/llu/auth/login":
            return self.answer({"status": 0, "data": {"authTicket": {"token": "LLU-TOKEN"}, "user": {"id": "u"}}})
        if self.path == "/user/updateNickname":
            if self.headers["Authorization"] != "Bearer LLU-TOKEN" or not o["deviceId"].startswith("lv-"):
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b'{"status":400,"error":{"message":"bad device"}}')
                return None
            return self.answer({"status": 0, "data": {"devices": [{"id": o["deviceId"], "nickname": o["nickname"]}]}})
        if self.path.endswith("/measurements"):
            n = len(o["DeviceData"]["measurementLog"]["scheduledContinuousGlucoseEntries"])
            return self.answer({"status": 0, "result": {"UploadId": "U", "MeasurementCounts": {"ScheduledGlucoseCount": n}}})
        self.send_error(404)

    def do_PUT(self):
        o = self.body()
        Fake.calls.append((self.command, self.path, o, dict(self.headers)))
        return self.answer({"status": 0})


class Lv2Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = HTTPServer(("127.0.0.1", 0), Fake)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        base = "http://127.0.0.1:%d" % cls.srv.server_port
        cls.tmp = tempfile.TemporaryDirectory()
        os.environ.update(LV2_STATE=cls.tmp.name, LV2_ASSETS=base + "/assets", LV2_LIBRELINK_CONFIG=base + "/configll", LV2_LLU=base,
                          LV_EMAIL="a@example.org", LV2_PASSWORD_FOR_TESTS="secret-pw")
        global lv2
        import lv2

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        cls.srv.server_close()
        cls.tmp.cleanup()

    def test_slots_take_the_first_reading_of_each_five_minutes(self):
        t = 1_000_000
        r = [(t + 61, 100, 61), (t + 5, 99, 60), (t + 240, 98, 64), (t + 300, 97, 65), (t - 3000, 5, 2)]
        self.assertEqual([(60, 99, t + 5), (65, 97, t + 300)], lv2.slots(r))
        # the same readings in any order give the same values (idempotent re-uploads)
        self.assertEqual(lv2.slots(r), lv2.slots(list(reversed(r))))

    def test_libre12_serials(self):
        self.assertTrue(lv2.libre12("3MH01GFXNU8"))
        for s in ("0D0001ABCD", "LT2401AB1CD", "3mh000abcd7", "8G1234", "3MH01GFXNU"):
            self.assertFalse(lv2.libre12(s), s)

    def test_reads_only_the_sensors_own_entries_with_their_life_count(self):
        path = os.path.join(self.tmp.name, "mixed.json")
        with open(path, "w") as f:
            json.dump([{"_id": "3MH01GFXNU8#61", "date": 2_000_000, "sgv": 30}, {"_id": "OTHER#5", "date": 1_000, "sgv": 9},
                       {"_id": "3MH01GFXNU8#60", "date": 1_940_000, "sgv": 450}, {"date": 5, "sgv": 100}], f)
        self.assertEqual([(1940, 401, 60), (2000, 39, 61)], lv2.read_entries(path, "3MH01GFXNU8"))

    def test_fixture_is_current(self):
        path = os.path.join(lv2.ROOT, "contracts", "treatments", "libreview-glucose-fixture.json")
        with open(path) as f:
            before = f.read()
        with contextlib.redirect_stdout(io.StringIO()):
            lv2.cmd_fixture()
        with open(path) as f:
            self.assertEqual(before, f.read(), "run: python3 -I lv2.py fixture")

    def test_the_proof_batch_against_the_fake_server(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            # A: no take-over: LibreView names the other device
            self.assertEqual((20, "wrongDeviceForUser"), lv2.cmd_signin("libre3", False))
            self.assertEqual(0, lv2.cmd_signin("librelink", False)[0])
            # B: take over, register the Libre 2, one hour
            self.assertEqual(0, lv2.cmd_signin("libre3", True)[0])
            self.assertEqual(0, lv2.cmd_register("3MH01GFXNU8"))
            entries = os.path.join(self.tmp.name, "entries.json")
            start = 1_793_712_000
            with open(entries, "w") as f:
                json.dump([{"_id": "3MH01GFXNU8#%d" % (60 + i), "date": (start + 3600 + 60 * i) * 1000, "sgv": 100 + i}
                           for i in range(60)], f)
            self.assertEqual(None, lv2.cmd_post(entries, "3MH01GFXNU8", start, start, start + 7200, really=False))
            n = len(Fake.calls)
            self.assertEqual(0, lv2.cmd_post(entries, "3MH01GFXNU8", start, start, start + 7200, really=True))
        self.assertNotIn("secret-pw", out.getvalue())
        method, path, body, hdr = Fake.calls[n]
        self.assertEqual(("POST", "/api3/api/measurements"), (method, path))
        self.assertEqual(12, len(body["DeviceData"]["measurementLog"]["scheduledContinuousGlucoseEntries"]))
        self.assertEqual("T-libre3", {k.lower(): v for k, v in hdr.items()}["x-newyu-token"])
        put = [c for c in Fake.calls if c[0] == "PUT"][0]
        self.assertEqual('{"activeSensor":"3MH01GFXNU8"}', put[2]["DomainData"])
        ll = [c for c in Fake.calls if c[2].get("GatewayType") == "FSLibreLink.Android"][0]
        self.assertEqual("/apil/api/nisperson/getauthentication", ll[1])
        self.assertNotIn("x-api-key", {k.lower() for k in ll[3]})
        for name in os.listdir(self.tmp.name):
            with open(os.path.join(self.tmp.name, name)) as f:
                self.assertNotIn("secret-pw", f.read())

    def test_the_nickname_goes_with_the_librelinkup_token(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            n = len(Fake.calls)
            self.assertEqual(None, lv2.cmd_nickname("DEV-2", "Sukrara", really=False))
            self.assertEqual(n, len(Fake.calls), "a dry run sends nothing")
            # by the upload's DeviceId: LibreView's own id is looked up first
            self.assertEqual(0, lv2.cmd_nickname("dev-2", "Sukrara", really=True))
            self.assertEqual({"nickname": "Sukrara", "deviceId": "lv-2"}, Fake.calls[-1][2])
            n = len(Fake.calls)
            self.assertEqual(None, lv2.cmd_nickname("DEV-9", "Sukrara", really=True))
            self.assertEqual(n + 1, len(Fake.calls), "no update for an unknown device")
        self.assertNotIn("LLU-TOKEN", out.getvalue())
        self.assertNotIn("secret-pw", out.getvalue())

    def test_refuses_plain_http_to_another_host(self):
        with self.assertRaises(SystemExit):
            lv2.http("GET", "http://example.org/x")


if __name__ == "__main__":
    unittest.main()
