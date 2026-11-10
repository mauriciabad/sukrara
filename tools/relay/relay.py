#!/usr/bin/env python3
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""A local Nostr test relay for debug builds and emulator QA: no public relay is ever involved.

    python3 tools/relay/relay.py [--port 7447] [--host 127.0.0.1] [--quiet]

Then point each debug app at it (an emulator reaches this computer's 127.0.0.1 as 10.0.2.2):

    adb -s emulator-5554 shell am broadcast -n app.mauri.sukrara/.debug.DebugReceiver \\
        -a app.mauri.sukrara.debug.RELAYS --es urls ws://10.0.2.2:7447

Python 3.9+ standard library only. Implements just what the Sukrara protocol uses (contracts/protocol/PROTOCOL-v2.md):
NIP-01 EVENT / REQ / CLOSE / EOSE / OK with ids, authors, kinds, #<letter> tags, since, until and limit; replaceable
(0, 3, 10000-19999) and addressable (30000-39999) events keep their newest version; ephemeral events (20000-29999)
go to live subscriptions only; NIP-40 expiration; NIP-09 deletion (kind 5) and NIP-62 vanish (kind 62) by the same
author. Everything lives in memory and goes with the process. Event ids are checked; signatures are not (a test
relay), and AUTH is never required (an AUTH message is answered OK).
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import json
import struct
import sys
import time

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
MAX_FRAME = 4 * 1024 * 1024


def event_id(ev: dict) -> str:
    data = [0, ev["pubkey"], ev["created_at"], ev["kind"], ev["tags"], ev["content"]]
    raw = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(raw.encode()).hexdigest()


def tag(ev: dict, name: str):
    return next((t[1] for t in ev.get("tags", []) if len(t) > 1 and t[0] == name), None)


def expired(ev: dict, now: int) -> bool:
    exp = tag(ev, "expiration")
    return exp is not None and exp.isdigit() and int(exp) <= now


def matches(flt: dict, ev: dict) -> bool:
    if "ids" in flt and not any(ev["id"].startswith(i) for i in flt["ids"]):
        return False
    if "authors" in flt and not any(ev["pubkey"].startswith(a) for a in flt["authors"]):
        return False
    if "kinds" in flt and ev["kind"] not in flt["kinds"]:
        return False
    if "since" in flt and ev["created_at"] < flt["since"]:
        return False
    if "until" in flt and ev["created_at"] > flt["until"]:
        return False
    for key, wanted in flt.items():
        if key.startswith("#") and len(key) == 2:
            values = {t[1] for t in ev.get("tags", []) if len(t) > 1 and t[0] == key[1]}
            if not values & set(wanted):
                return False
    return True


class Store:
    """The relay's events (pure: no I/O, so the tests drive it directly)."""

    def __init__(self, now=lambda: int(time.time())):
        self.events: dict[str, dict] = {}
        self.now = now

    @staticmethod
    def slot(ev: dict):
        k = ev["kind"]
        if k in (0, 3) or 10000 <= k < 20000:
            return (ev["pubkey"], k)
        if 30000 <= k < 40000:
            return (ev["pubkey"], k, tag(ev, "d") or "")
        return None

    def add(self, ev: dict) -> tuple[bool, str, bool]:
        """(accepted, message, broadcast) for an EVENT."""
        try:
            if event_id(ev) != ev["id"]:
                return False, "invalid: bad event id", False
        except (KeyError, TypeError):
            return False, "invalid: not an event", False
        if expired(ev, self.now()):
            return False, "invalid: event has expired", False
        if ev["id"] in self.events:
            return True, "duplicate: already have this event", False
        k = ev["kind"]
        if 20000 <= k < 30000:
            return True, "", True
        if k == 5:
            self.delete(ev)
        if k == 62:
            self.events = {i: e for i, e in self.events.items() if e["pubkey"] != ev["pubkey"]}
            return True, "", True
        slot = self.slot(ev)
        if slot is not None:
            for old in [e for e in self.events.values() if self.slot(e) == slot]:
                if (old["created_at"], -int(old["id"], 16)) >= (ev["created_at"], -int(ev["id"], 16)):
                    return True, "duplicate: have a newer version", False
                del self.events[old["id"]]
        self.events[ev["id"]] = ev
        return True, "", True

    def delete(self, req: dict):
        ids = {t[1] for t in req["tags"] if len(t) > 1 and t[0] == "e"}
        coords = {t[1] for t in req["tags"] if len(t) > 1 and t[0] == "a"}
        for i, e in list(self.events.items()):
            if e["pubkey"] != req["pubkey"]:
                continue
            coord = "%d:%s:%s" % (e["kind"], e["pubkey"], tag(e, "d") or "")
            if i in ids or (coord in coords and e["created_at"] <= req["created_at"]):
                del self.events[i]

    def query(self, filters: list) -> list:
        now = self.now()
        out: dict[str, dict] = {}
        for flt in filters:
            found = sorted((e for e in self.events.values() if not expired(e, now) and matches(flt, e)),
                           key=lambda e: e["created_at"], reverse=True)
            if "limit" in flt:
                found = found[: max(0, int(flt["limit"]))]
            for e in found:
                out[e["id"]] = e
        return sorted(out.values(), key=lambda e: e["created_at"], reverse=True)


class Relay:
    def __init__(self, store: Store, log):
        self.store = store
        self.log = log
        self.clients: dict[object, dict[str, list]] = {}

    async def serve(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        peer = "%s:%s" % writer.get_extra_info("peername")[:2]
        try:
            if not await handshake(reader, writer):
                return
            subs: dict[str, list] = {}
            self.clients[writer] = subs
            self.log("+ %s connected" % peer)
            async for text in frames(reader, writer):
                await self.handle(peer, writer, subs, text)
        except asyncio.IncompleteReadError:
            pass  # the client went without a close frame (an app process ending, a dropped socket)
        except (ConnectionError, ValueError) as e:
            self.log("! %s %s" % (peer, e))
        finally:
            if writer in self.clients:
                self.log("- %s gone" % peer)
            self.clients.pop(writer, None)
            writer.close()

    async def handle(self, peer, writer, subs, text):
        try:
            msg = json.loads(text)
            verb = msg[0]
        except (ValueError, IndexError, KeyError, TypeError):
            await send(writer, ["NOTICE", "invalid: not a relay message"])
            return
        if verb == "EVENT" and len(msg) > 1 and isinstance(msg[1], dict):
            ev = msg[1]
            ok, why, broadcast = self.store.add(ev)
            self.log("  %s EVENT kind %s %s %s" % (peer, ev.get("kind"), str(ev.get("id"))[:8], why or "ok"))
            await send(writer, ["OK", ev.get("id", ""), ok, why])
            if broadcast:
                for w, s in list(self.clients.items()):
                    for sid, filters in list(s.items()):
                        if any(matches(f, ev) for f in filters):
                            await send(w, ["EVENT", sid, ev])
        elif verb == "REQ" and len(msg) > 2 and isinstance(msg[1], str):
            sid, filters = msg[1], [f for f in msg[2:] if isinstance(f, dict)]
            subs[sid] = filters
            found = self.store.query(filters)
            self.log("  %s REQ %s %s -> %d stored" % (peer, sid[:12], json.dumps(filters)[:120], len(found)))
            for ev in found:
                await send(writer, ["EVENT", sid, ev])
            await send(writer, ["EOSE", sid])
        elif verb == "CLOSE" and len(msg) > 1:
            subs.pop(msg[1], None)
        elif verb == "AUTH" and len(msg) > 1 and isinstance(msg[1], dict):
            await send(writer, ["OK", msg[1].get("id", ""), True, ""])
        else:
            await send(writer, ["NOTICE", "unsupported: %s" % str(verb)[:20]])


async def handshake(reader, writer) -> bool:
    head = await reader.readuntil(b"\r\n\r\n")
    lines = head.decode("latin-1").split("\r\n")
    headers = {k.strip().lower(): v.strip() for k, _, v in (l.partition(":") for l in lines[1:] if ":" in l)}
    key = headers.get("sec-websocket-key")
    if headers.get("upgrade", "").lower() != "websocket" or not key:
        body = b"Sukrara test relay: connect with a Nostr websocket client.\n"
        writer.write(b"HTTP/1.1 426 Upgrade Required\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n"
                     b"Connection: close\r\n\r\n%s" % (len(body), body))
        await writer.drain()
        return False
    accept = base64.b64encode(hashlib.sha1((key + GUID).encode()).digest()).decode()
    writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  "Sec-WebSocket-Accept: %s\r\n\r\n" % accept).encode())
    await writer.drain()
    return True


async def frames(reader, writer):
    """Yields each text message; answers pings; ends at a close frame."""
    parts: list[bytes] = []
    while True:
        b0, b1 = await reader.readexactly(2)
        opcode, masked, n = b0 & 0x0F, b1 & 0x80, b1 & 0x7F
        if n == 126:
            (n,) = struct.unpack("!H", await reader.readexactly(2))
        elif n == 127:
            (n,) = struct.unpack("!Q", await reader.readexactly(8))
        if n > MAX_FRAME:
            raise ValueError("frame too big")
        mask = await reader.readexactly(4) if masked else b"\0\0\0\0"
        data = bytes(c ^ mask[i % 4] for i, c in enumerate(await reader.readexactly(n)))
        if opcode == 8:
            await frame(writer, 8, data[:2])
            return
        if opcode == 9:
            await frame(writer, 10, data)
            continue
        if opcode == 10:
            continue
        parts.append(data)
        if b0 & 0x80:
            yield b"".join(parts).decode()
            parts = []


async def frame(writer, opcode: int, data: bytes):
    n = len(data)
    head = bytes([0x80 | opcode])
    if n < 126:
        head += bytes([n])
    elif n < 65536:
        head += bytes([126]) + struct.pack("!H", n)
    else:
        head += bytes([127]) + struct.pack("!Q", n)
    writer.write(head + data)
    await writer.drain()


async def send(writer, msg):
    try:
        await frame(writer, 1, json.dumps(msg, separators=(",", ":"), ensure_ascii=False).encode())
    except ConnectionError:
        pass


async def main(argv=None):
    ap = argparse.ArgumentParser(description="Local Nostr test relay (Sukrara debug builds, emulator QA).")
    ap.add_argument("--host", default="127.0.0.1", help="address to listen on (default 127.0.0.1: this computer only)")
    ap.add_argument("--port", type=int, default=7447)
    ap.add_argument("--quiet", action="store_true", help="log connections only")
    args = ap.parse_args(argv)

    def log(line):
        if not args.quiet or not line.startswith("  "):
            print(time.strftime("%H:%M:%S"), line, file=sys.stderr, flush=True)

    relay = Relay(Store(), log)
    server = await asyncio.start_server(relay.serve, args.host, args.port)
    log("listening on ws://%s:%d (emulators: ws://10.0.2.2:%d)" % (args.host, args.port, args.port))
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
