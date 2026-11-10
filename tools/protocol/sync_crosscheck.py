#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Cross-checks the protocol fixtures against the independent Python reference (contracts/protocol/PROTOCOL-v2.md).

    python3 tools/protocol/sync_crosscheck.py                  # CI: exits 1 on any difference
    python3 tools/protocol/sync_crosscheck.py --write-vectors  # rewrites contracts/protocol/ogs/ogs-draft1.json

1. Every follower sequence in contracts/protocol/sync (the files with `steps`) is fed to the reference follower: each
   step's verdict, the `have` of every rekey request it sends and the final state must be the fixture's.
2. The OGS vectors (contracts/protocol/ogs/ogs-draft1.json) must be byte for byte what the reference's generator makes,
   and their scenario must give the same verdicts through the follower below.

The reference (poc/spec/reference, OGS draft 1, its own secp256k1 / NIP-44 code) is frozen with the PoC: it is imported
read-only (no bytecode written next to it) and the PROTOCOL-v2 rules it predates are added here, one method each:
`Follower` (rosters ranked by a newer epoch's key, rekey `have` = the newest held gk and only for events newer than
the latest key, revoked notice vs key delivery ordered by the rumor's `created_at`, a restart, events from an author
not yet in the roster not remembered, the settings rule, readings in the future (a live event rejected whole, its
sensor fields too), a roster without a profile keeping the last one) and `aligned_vectors` (the rekey
request's `have`). Both apps implement the same rules (Android `StreamFollower`, Mac `FollowerSession`). Standard
library only.
"""
from __future__ import annotations

import json
import os
import sys
import types

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONTRACTS = os.path.join(ROOT, "contracts", "protocol")
VECTORS = os.path.join(CONTRACTS, "ogs", "ogs-draft1.json")

sys.dont_write_bytecode = True  # poc/ is frozen: nothing is written next to the reference
sys.path.insert(0, os.path.join(ROOT, "poc", "spec", "reference"))
from ogs import crypto as c  # noqa: E402
from ogs import events as E  # noqa: E402
from ogs import follower as ref  # noqa: E402
from ogs import model as m  # noqa: E402
from ogs import vectors as V  # noqa: E402
from ogs.nostr import Keys, UnwrapError, decrypt, expired, tag, unwrap, verify  # noqa: E402

Result = ref.Result
FUTURE_TOLERANCE = 300
REKEY_THROTTLE = 300
NOT_IN_ROSTER_YET = ("author-not-device", "author-not-in-roster")


class Follower(ref.Follower):
    """The reference follower with the PROTOCOL-v2 acceptance rules it predates."""

    def __init__(self, F: Keys, i_pub: str):
        super().__init__(F, i_pub)
        self.last_key_at = 0  # the newest key delivery's rumor created_at
        self.revoked_at = 0
        self.settings: dict | None = None

    def restart(self):
        """An app restart: trust state and stored data kept; seen ids and the rekey throttle forgotten."""
        self._seen = set()
        self._rekey_at = {}

    def _on_event(self, ev: dict, now: int) -> Result:
        eid = ev.get("id")
        if eid in self._seen:
            return Result("ignored", "duplicate")
        if not verify(ev):
            return Result("rejected", "bad-signature")
        self._seen.add(eid)
        if expired(ev, now):
            return Result("ignored", "expired")
        if ev["kind"] == m.KIND_WRAP:
            return self._on_wrap(ev, now)
        if self.status == "revoked":
            return Result("ignored", "revoked")
        g = tag(ev, "gk")
        if g is None:
            return Result("ignored", "no-gk")
        if g not in self.epochs:
            return self._unknown_epoch(ev, g, now)
        n, G = self.epochs[g]
        if self.sid is None:
            return Result("ignored", "no-sid")
        author = ev["pubkey"]
        try:
            if ev["kind"] == m.KIND_DATA:
                r = self._on_data(ev, n, G, author, now)
            elif ev["kind"] in (m.KIND_LIVE, m.KIND_ALERT):
                if author not in self.devices:
                    r = Result("rejected", "author-not-device")
                else:
                    body = json.loads(decrypt(G, author, ev["content"]))
                    r = self._on_live(ev, body, now) if ev["kind"] == m.KIND_LIVE else self._on_alert(body, now)
            else:
                r = Result("ignored", "unknown-kind")
        except (ValueError, KeyError, TypeError) as e:
            r = Result("rejected", "invalid", str(e))
        if r.verdict == "rejected" and r.what in NOT_IN_ROSTER_YET:
            self._seen.discard(eid)  # the roster that adds the author may still be on its way: judged again then
        return r

    def _unknown_epoch(self, ev: dict, g: str, now: int) -> Result:
        """Readable once the key arrives, so not remembered. A rekey request only for an event signed by I or a roster
        device that is newer than the latest key (an older one is a backfill, not a missed rotation), at most one per
        gk per 5 min; its `have` names the newest epoch held."""
        self._seen.discard(ev["id"])
        trusted = ev["pubkey"] == self.i_pub or ev["pubkey"] in self.devices
        throttled = now - self._rekey_at.get(g, -10 ** 9) < REKEY_THROTTLE
        if self.status == "active" and trusted and ev["created_at"] > self.last_key_at and not throttled:
            self._rekey_at[g] = now
            have = max(self.epochs.items(), key=lambda kv: kv[1][0])[0]
            return Result("ignored", "unknown-epoch", g, [E.rekey_request(self.F, self.i_pub, have, now=now)])
        return Result("ignored", "unknown-epoch", g)

    def _on_data(self, ev, n, G, author, now) -> Result:
        d, cc = tag(ev, "d"), tag(ev, "c")
        if d == m.H(f"{m.NS}:settings:{self.sid}"):
            if author not in self.devices:
                return Result("rejected", "author-not-device")
            body = json.loads(decrypt(G, author, ev["content"]))
            if body.get("v") != m.WIRE_V or body.get("type") != "settings":
                raise m.Invalid("not a settings payload")
            u = m._int(body, "u")
            if self.settings is not None and u <= self.settings["u"]:
                return Result("ignored", "settings-older")
            self.settings = {"u": u, "graph": body.get("graph"), "alerts": body.get("alerts")}
            return Result("accepted", "settings")
        if d == m.d_roster(self.sid):
            # Profiles are replaced, never removed: a roster without a valid profile keeps the last one.
            kept = self.profile
            r = super()._on_data(ev, n, G, author, now)
            if r.verdict == "accepted" and self.profile is None:
                self.profile = kept
            return r
        if cc == m.c_glucose(self.sid) and author in self.devices:
            h, rows = m.parse_chunk(json.loads(decrypt(G, author, ev["content"])))
            if d == m.d_chunk(self.sid, h) and (h > now + FUTURE_TOLERANCE or
                                                  any(r.t > now + FUTURE_TOLERANCE for r in rows)):
                return Result("rejected", "reading-in-future")
        return super()._on_data(ev, n, G, author, now)

    def _on_live(self, ev, body, now) -> Result:
        reading, _, _ = m.parse_live(body)
        if reading and reading.t > now + FUTURE_TOLERANCE:
            return Result("rejected", "reading-in-future")  # before it can touch the sensor
        return super()._on_live(ev, body, now)

    def _on_wrap(self, ev, now) -> Result:
        """Revoked notices and key deliveries come back in any order after a restart: ordered by the rumor's time."""
        if tag(ev, "p") != self.F.pub:
            return Result("ignored", "wrap-not-for-us")
        try:
            rum, sender = unwrap(self.F, ev)
        except (UnwrapError, ValueError) as e:
            return Result("rejected", "unwrap", str(e))
        if rum.get("kind") != m.KIND_TO_FOLLOWER or sender != self.i_pub:
            return Result("rejected", "wrap-not-from-identity")
        at = rum.get("created_at", 0)
        try:
            body = m.parse_rumor(json.loads(rum["content"]), m.KIND_TO_FOLLOWER)
            typ = body["type"]
            if typ == "key":
                return self._on_key_at(body, at)
        except (ValueError, KeyError, TypeError) as e:
            return Result("rejected", "invalid", str(e))
        if typ == "revoked":
            if at < self.last_key_at:
                return Result("ignored", "revoked-before-latest-key")
            if self.status == "revoked":
                return Result("ignored", "revoked-already")
            self.status, self.revoked_at = "revoked", at
            return Result("accepted", "revoked")
        if typ in ("invite-used", "declined"):
            if self.status != "requested":
                return Result("ignored", typ)
            self.status = "used" if typ == "invite-used" else "declined"
            return Result("accepted", typ)
        return Result("ignored", "unknown-type", typ)

    def _on_key_at(self, body: dict, at: int) -> Result:
        """A key newer than the revoked notice re-approves (even one already held); a newer epoch's key outranks every
        roster of the epochs before it (its devices apply until that epoch's roster arrives)."""
        sid, n = body.get("sid"), m._int(body, "epoch")
        if not isinstance(sid, str) or len(sid) != 64:
            raise m.Invalid("bad sid")
        if self.sid is not None and sid != self.sid:
            return Result("rejected", "sid-changed")
        G = Keys(c.nsec_decode(body["g"]))
        g = m.gk(G.pub)
        last = max(self.last_key_at, at)
        status = "revoked" if self.status == "revoked" and self.revoked_at >= last else "active"
        if g in self.epochs:
            if self.status != "revoked" or status == "revoked":
                return Result("ignored", "key-known")
            self.status, self.last_key_at = status, last
            return Result("accepted", "key", f"epoch {n}, approved again")
        newest = max((e[0] for e in self.epochs.values()), default=0)
        self.sid = sid
        self.epochs[g] = (n, G)
        if n >= newest:
            devs = body.get("devices") or []
            if devs:
                self.devices = devs
            relays = [r for r in body.get("relays") or [] if isinstance(r, str) and r.startswith("wss://")]
            if relays:
                self.relays = relays
        if n > self.roster_at[0]:
            self.roster_at = (n, -1)
        self.status, self.last_key_at = status, last
        return Result("accepted", "key", f"epoch {n}")


def state_json(f: Follower) -> dict:
    """The follower's state as the sync fixtures' `expected` (contracts/protocol/sync/README.md)."""
    return {
        "status": {"used": "inviteused"}.get(f.status, f.status),
        "epochs": sorted(n for n, _ in f.epochs.values()),
        "devices": f.devices,
        "writers": f.writers,
        "wearer": f.profile,
        "readings": [[r.t, r.sgv, r.rate] for _, r in sorted(f.readings.items())],
        "sensor": f.sensor and {"ss": f.sensor.ss, "se": f.sensor.se, "sw": f.sensor.sw},
        "treatments": [{"id": t.id, "k": t.k, "a": t.a, "u": t.u, "o": t.o, "del": t.deleted}
                       for t in sorted(f.treatments.values(), key=lambda t: t.id)],
        "settings": f.settings,
        "alerts": len(f.alerts),
    }


def check_sequence(name: str, fx: dict) -> list[str]:
    keys = {k: Keys.from_hex(v["sec"]) for k, v in fx["keys"].items()}
    by_pub = {k.pub: k for k in keys.values()}
    f = Follower(keys[fx["follower"]], fx["identity"])
    now, fails = fx["now"], []
    for i, step in enumerate(fx["steps"]):
        if step.get("restart"):
            f.restart()
            continue
        r = f.on_event(fx["events"][step["event"]], now)
        label = f"{name} step {i} {step['event']}"
        if f"{r.verdict}:{r.what}" != step["expect"]:
            fails.append(f"{label}: expected {step['expect']}, the reference says {r}")
        have = [json.loads(unwrap(by_pub[fx["identity"]], w)[0]["content"])["have"] for w in r.out]
        want = [step["rekey"]] if "rekey" in step else []
        if have != want:
            fails.append(f"{label}: rekey have {want} expected, the reference sends {have}")
    got = state_json(f)
    for k, want in fx["expected"].items():
        if got.get(k) != want:
            fails.append(f"{name} final {k}: expected {json.dumps(want)}, the reference has {json.dumps(got.get(k))}")
    return fails


def aligned_vectors() -> str:
    """The reference's OGS vectors with one PROTOCOL-v2 alignment: the rekey request's `have` names the newest epoch the
    follower holds (G1, it saw G2 unknown), not the unknown one. Everything else is the frozen generator's bytes."""
    g1 = m.gk(V.K("read key G epoch 1").pub)
    shim = types.SimpleNamespace(**vars(E))
    shim.rekey_request = lambda F, i_pub, _unknown, **kw: E.rekey_request(F, i_pub, g1, **kw)
    real, V.E = V.E, shim
    try:
        vec = V.generate()
    finally:
        V.E = real
    vec["about"] = ("OGS draft 1 (wire version 2) test vectors. Throwaway keys derived from public strings; synthetic "
                    "glucose. Generated by poc/spec/reference with PROTOCOL-v2's rekey `have` (the newest gk held): "
                    "python3 tools/protocol/sync_crosscheck.py --write-vectors.")
    return V.dumps(vec)


def check_vectors() -> list[str]:
    fails = []
    with open(VECTORS, encoding="utf-8") as fh:
        text = fh.read()
    if text != aligned_vectors():
        fails.append(f"{os.path.relpath(VECTORS, ROOT)} differs from the reference's generator "
                     "(python3 tools/protocol/sync_crosscheck.py --write-vectors)")
    vec = json.loads(text)
    sc = vec["scenario"]
    keys = {k: Keys.from_hex(v["sec"]) for k, v in vec["keys"].items()}
    f = Follower(keys[sc["follower"]], sc["i"])
    f.status = "requested"
    for i, step in enumerate(sc["steps"]):
        r = f.on_event(vec["events"][step["event"]], sc["now"])
        if f"{r.verdict}:{r.what}" != step["expect"]:
            fails.append(f"ogs scenario step {i} {step['event']}: expected {step['expect']}, got {r}")
    return fails


def main(argv: list[str]) -> int:
    if "--write-vectors" in argv:
        with open(VECTORS, "w", encoding="utf-8") as fh:
            fh.write(aligned_vectors())
    fails, checked = check_vectors(), []
    sync = os.path.join(CONTRACTS, "sync")
    for name in sorted(os.listdir(sync)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(sync, name), encoding="utf-8") as fh:
            fx = json.load(fh)
        if "steps" not in fx:
            continue  # the wearer's side (owner-conflict.json)
        checked.append(name)
        fails += check_sequence(name, fx)
    for f in fails:
        print("FAIL", f)
    print(f"{'FAIL' if fails else 'OK'}: the reference follower on {len(checked)} sync sequences "
          f"({', '.join(checked)}) and the OGS vectors")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
