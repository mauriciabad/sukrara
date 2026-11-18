// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Testing

/// The leave sender over the real `RelayPool` and scripted relay sockets (PROTOCOL-v2 "Leave" and "Relay answers"):
/// what a relay answers in NIP-01 `OK` frames, as it says it, decides when the leave is told and done. No network.
struct LeaveRelayAnswersTests {
    let crypto = NostrSDKCrypto()
    let now = 1_792_087_200

    /// A leave to `relays` in a fresh outbox, its sender over `factory`'s sockets; one attempt waits `wait` seconds.
    func sending(to relays: [String], factory: FakeWebSocketFactory, wait: Double = 5) async throws -> (
        LeaveSender, LeaveOutboxStore, String
    ) {
        let wrap = try crypto.sign(
            kind: Wire.kindWrap, content: "sealed", tags: [["p", String(repeating: "ab", count: 32)]], createdAt: now,
            with: crypto.generateSecret())
        let store = LeaveOutboxStore(
            url: FileManager.default.temporaryDirectory.appending(path: "leave-outbox-\(UUID().uuidString).json"))
        try await store.save(
            LeaveOutbox(leaves: [PendingLeave(wrap: wrap, relays: relays, expiry: now + Wire.ttlLeave, wearer: "Ana")]))
        let crypto = crypto
        let now = now
        let sender = LeaveSender(
            store: store, crypto: crypto, clock: FakeClock(Date(timeIntervalSince1970: Double(now))),
            makeRelays: { urls, key in
                RelayPool(
                    relays: urls, authKey: key, crypto: crypto, factory: factory,
                    // reconnect delays pass at once; the watchdog's 60 s tick waits until the attempt ends
                    sleep: { seconds in
                        if seconds >= 60 { try await Task.sleep(for: .seconds(3600)) } else { await Task.yield() }
                    }, now: { now })
            },
            sleep: { _ in try await Task.sleep(for: .seconds(wait)) })
        return (sender, store, wrap.id)
    }

    /// The socket the pool opened for `url`, once the leave went out on it.
    func relay(_ url: String, _ factory: FakeWebSocketFactory, _ id: String) async -> FakeWebSocket? {
        for _ in 0..<400 {
            if let i = factory.urls.firstIndex(where: { $0.absoluteString == url }) {
                let socket = factory.sockets[i]
                if await socket.waitUntilSent({ $0.contains { $0.hasPrefix("[\"EVENT\"") && $0.contains(id) } }) {
                    return socket
                }
                return nil
            }
            try? await Task.sleep(for: .milliseconds(5))
        }
        return nil
    }

    func sentEvents(_ socket: FakeWebSocket, _ id: String) -> Int {
        socket.sent.filter { $0.hasPrefix("[\"EVENT\"") && $0.contains(id) }.count
    }

    /// A relay URL with a port: the pool tagged answers with the host only, so the Mac never marked it told.
    @Test func aRelayKeepsItWithOrWithoutADuplicateMessageWhateverTheUrlsSpelling() async throws {
        let factory = FakeWebSocketFactory()
        let relays = ["wss://a.example:4848/", "wss://B.example", "wss://c.example/inbox"]
        let (sender, store, id) = try await sending(to: relays, factory: factory)
        let flushing = Task { await sender.flush() }
        let a = try #require(await relay("wss://a.example:4848/", factory, id))
        let b = try #require(await relay("wss://B.example", factory, id))
        #expect(await relay("wss://c.example/inbox", factory, id) != nil)
        a.push(#"["OK","\#(id)",true,""]"#)
        #expect(await eventually { await store.load()?.leaves.first?.told == true })
        // a resend after a reconnect: the relay already has it, as NIP-01's example says it
        b.push(#"["OK","\#(id)",true,"duplicate: already have this event"]"#)
        #expect(await flushing.value)
        #expect(await store.load() == nil)
    }

    /// NIP-20: a relay that already has the event says `true`; some say `false` with `duplicate:`. Both keep it.
    @Test func aDuplicateAnsweredFalseStillKeepsIt() async throws {
        let factory = FakeWebSocketFactory()
        let (sender, store, id) = try await sending(to: ["wss://a.example", "wss://b.example"], factory: factory)
        let flushing = Task { await sender.flush() }
        let a = try #require(await relay("wss://a.example", factory, id))
        let b = try #require(await relay("wss://b.example", factory, id))
        a.push(#"["OK","\#(id)",false,"duplicate: already have this event"]"#)
        b.push(#"["OK","\#(id)",true]"#)
        #expect(await flushing.value)
        #expect(await store.load() == nil)
    }

    @Test func refusalsOtherEventsAndAnythingButABooleanDontCount() async throws {
        let factory = FakeWebSocketFactory()
        let (sender, store, id) = try await sending(
            to: ["wss://a.example", "wss://b.example"], factory: factory, wait: 0.5)
        let flushing = Task { await sender.flush() }
        let a = try #require(await relay("wss://a.example", factory, id))
        let b = try #require(await relay("wss://b.example", factory, id))
        a.push(#"["OK","\#(id)",false,"blocked: you are banned from posting here"]"#)
        a.push(#"["OK","\#(id)",false,"rate-limited: slow down there chief","1000"]"#)
        b.push(#"["OK","\#(id)","true",""]"#)
        b.push(#"["OK","\#(String(repeating: "cd", count: 32))",true,""]"#)
        b.push(#"["OK","\#(id)"]"#)
        b.push(#"["NOTICE","\#(id)"]"#)
        #expect(await flushing.value == false)
        let left = try #require(await store.load()?.leaves.first)
        #expect(!left.told)
        #expect(left.waitingOn == ["wss://a.example", "wss://b.example"])
    }

    /// NIP-42 on a write: AUTH with the throwaway key, then the leave goes again and counts.
    @Test func aRelayAskingForAuthGetsAThrowawayKeysAndTheLeaveAgain() async throws {
        let factory = FakeWebSocketFactory()
        let (sender, store, id) = try await sending(to: ["wss://a.example"], factory: factory)
        let flushing = Task { await sender.flush() }
        let a = try #require(await relay("wss://a.example", factory, id))
        a.push(#"["AUTH","challenge-1"]"#)
        a.push(#"["OK","\#(id)",false,"auth-required: we only accept events from registered users"]"#)
        #expect(await a.waitUntilSent { $0.contains { $0.hasPrefix("[\"AUTH\"") } })
        let auth = try #require(a.sent.first { $0.hasPrefix("[\"AUTH\"") })
        let frame = try #require(try JSONSerialization.jsonObject(with: Data(auth.utf8)) as? [Any])
        let event = try #require(frame[1] as? [String: Any])
        #expect(event["kind"] as? Int == Int(Wire.kindAuth))
        #expect(auth.contains("challenge-1"))
        #expect(await store.load()?.leaves.first?.told == false)
        let before = sentEvents(a, id)
        a.push(#"["OK","\#(event["id"] as! String)",true,""]"#)
        #expect(
            await a.waitUntilSent { sent in sent.filter { $0.hasPrefix("[\"EVENT\"") && $0.contains(id) }.count > before
            })
        a.push(#"["OK","\#(id)",true,""]"#)
        #expect(await flushing.value)
    }
}
