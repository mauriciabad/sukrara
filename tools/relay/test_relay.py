#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for the local test relay: python3 tools/relay/test_relay.py (loopback only, no public relay)."""
import asyncio
import base64
import json
import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import relay  # noqa: E402

A, B = "a" * 64, "b" * 64


def ev(kind, created_at, pubkey=A, tags=(), content="x"):
    e = {"pubkey": pubkey, "created_at": created_at, "kind": kind, "tags": [list(t) for t in tags], "content": content,
         "sig": "0" * 128}
    e["id"] = relay.event_id(e)
    return e


class StoreTest(unittest.TestCase):
    def setUp(self):
        self.store = relay.Store(now=lambda: 1000)

    def test_regular_events_are_stored_and_queried(self):
        wrap = ev(1059, 900, tags=[("p", B)])
        self.assertEqual(self.store.add(wrap), (True, "", True))
        self.assertEqual(self.store.query([{"kinds": [1059], "#p": [B]}]), [wrap])
        self.assertEqual(self.store.query([{"kinds": [1059], "#p": [A]}]), [])
        self.assertEqual(self.store.query([{"authors": [A], "since": 901}]), [])

    def test_a_bad_id_or_an_expired_event_is_refused(self):
        bad = dict(ev(1059, 900), content="changed")
        self.assertFalse(self.store.add(bad)[0])
        self.assertFalse(self.store.add(ev(1059, 900, tags=[("expiration", "999")]))[0])
        self.assertEqual(self.store.events, {})

    def test_addressable_events_keep_the_newest(self):
        old, new = ev(30078, 900, tags=[("d", "h")]), ev(30078, 950, tags=[("d", "h")])
        self.store.add(old)
        self.store.add(new)
        self.assertEqual(self.store.add(old)[1], "duplicate: have a newer version")
        self.assertEqual(self.store.query([{"kinds": [30078], "#d": ["h"]}]), [new])

    def test_ephemeral_events_are_not_stored(self):
        self.assertEqual(self.store.add(ev(21078, 900)), (True, "", True))
        self.assertEqual(self.store.query([{"kinds": [21078]}]), [])

    def test_deletion_and_vanish_remove_only_the_authors_events(self):
        mine, theirs = ev(30078, 900, tags=[("d", "h")]), ev(30078, 900, pubkey=B, tags=[("d", "h")])
        self.store.add(mine)
        self.store.add(theirs)
        self.store.add(ev(5, 950, tags=[("a", "30078:%s:h" % A)]))
        self.assertEqual(self.store.query([{"kinds": [30078]}]), [theirs])
        self.store.add(ev(62, 960, pubkey=B))
        self.assertEqual(self.store.query([{"authors": [B]}]), [])

    def test_limit_keeps_the_newest(self):
        for t in (1, 2, 3):
            self.store.add(ev(1059, t))
        self.assertEqual([e["created_at"] for e in self.store.query([{"kinds": [1059], "limit": 2}])], [3, 2])


class WireTest(unittest.TestCase):
    """A real websocket over loopback: handshake, masked client frames, REQ / EVENT / EOSE / OK and live delivery."""

    def test_publish_reaches_a_live_subscription(self):
        asyncio.run(self.run_relay())

    async def run_relay(self):
        server = await asyncio.start_server(relay.Relay(relay.Store(), lambda _: None).serve, "127.0.0.1", 0)
        port = server.sockets[0].getsockname()[1]
        sub = await Client.open(port)
        await sub.send(["REQ", "s", {"kinds": [21078]}])
        self.assertEqual(await sub.recv(), ["EOSE", "s"])
        pub = await Client.open(port)
        live = ev(21078, 900)
        await pub.send(["EVENT", live])
        self.assertEqual(await pub.recv(), ["OK", live["id"], True, ""])
        self.assertEqual(await sub.recv(), ["EVENT", "s", live])
        server.close()
        await server.wait_closed()


class Client:
    @classmethod
    async def open(cls, port):
        c = cls()
        c.reader, c.writer = await asyncio.open_connection("127.0.0.1", port)
        key = base64.b64encode(os.urandom(16)).decode()
        c.writer.write(("GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                        "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % key).encode())
        head = await c.reader.readuntil(b"\r\n\r\n")
        assert head.startswith(b"HTTP/1.1 101"), head
        return c

    async def send(self, msg):
        data, mask = json.dumps(msg).encode(), os.urandom(4)
        n = len(data)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack("!H", n))
        self.writer.write(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))
        await self.writer.drain()

    async def recv(self):
        b0, b1 = await self.reader.readexactly(2)
        n = b1 & 0x7F
        if n == 126:
            (n,) = struct.unpack("!H", await self.reader.readexactly(2))
        return json.loads(await asyncio.wait_for(self.reader.readexactly(n), 5))


if __name__ == "__main__":
    unittest.main()
