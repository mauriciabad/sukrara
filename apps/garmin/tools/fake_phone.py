# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""A stand-in for Sukrara on the phone, for the Connect IQ simulator on this Mac: answers each app's 5-minute fetch
(GET http://127.0.0.1:17582/sukrara/<face|app|field>) with a payload of contracts/watchface/payload/cases.json, its
times moved so it was sent now. Never on the phone or the watch: the simulator's web requests go to this Mac.

  python3 apps/garmin/tools/fake_phone.py [--case N] [--app-case N] [--field-case N]

N: the case's index in cases.json's `cases` (the face profile, every watch face), `app`, `field` (default 0). The simulator fires the background's
temporal event 5 minutes after the app registers it (or Simulation › Background Events); the app then draws the case.
Stop it with Ctrl-C.
"""
import argparse
import http.server
import json
import pathlib
import time

ROOT = pathlib.Path(__file__).resolve().parents[3]
CASES = json.loads((ROOT / "contracts/watchface/payload/cases.json").read_text())
DAY30 = 30 * 86400


def shifted(v, delta):
    """Every epoch time in [v] (within 30 days of the cases' now) moved by [delta] s."""
    if isinstance(v, dict):
        return {k: shifted(x, delta) for k, x in v.items()}
    if isinstance(v, list):
        return [shifted(x, delta) for x in v]
    if isinstance(v, int) and not isinstance(v, bool) and abs(v - CASES["now"]) < DAY30:
        return v + delta
    return v


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--case", type=int, default=0)
    ap.add_argument("--app-case", type=int, default=0)
    ap.add_argument("--field-case", type=int, default=0)
    a = ap.parse_args()
    pick = {"face": CASES["cases"][a.case], "app": CASES["app"][a.app_case], "field": CASES["field"][a.field_case]}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            name = self.path.split("?")[0].rsplit("/", 1)[-1]
            if name not in pick:
                self.send_error(404)
                return
            p = pick[name]["payload"]
            body = json.dumps(shifted(p, int(time.time()) - p["sent"])).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print("fake_phone: %s → %s" % (self.path[:80], pick[name]["name"][:60]), flush=True)

        def log_message(self, *args):
            pass

    print("fake_phone: 127.0.0.1:17582, " + ", ".join("%s: %s" % (k, v["name"][:50]) for k, v in pick.items()), flush=True)
    http.server.HTTPServer(("127.0.0.1", 17582), Handler).serve_forever()


if __name__ == "__main__":
    main()
