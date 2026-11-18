// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Synchronization
import Testing

/// A relay pool whose relays at `urls` keep every event published to them (`OK true`): what the leave sender talks to.
actor AnsweringRelays: RelayLink {
    nonisolated let inbound: AsyncStream<RelayInbound>
    private let continuation: AsyncStream<RelayInbound>.Continuation
    let urls: [String]
    private(set) var published: [SignedEvent] = []
    private(set) var stopped = false

    init(urls: [String]) {
        self.urls = urls
        (inbound, continuation) = AsyncStream.makeStream()
    }

    func subscribe(_ id: String, _ filter: RelayFilter) {}
    func close(_ id: String) {}
    func start() {}

    func stop() {
        stopped = true
        continuation.finish()
    }

    func publish(_ event: SignedEvent) {
        published.append(event)
        for url in urls {
            continuation.yield(.ok(RelayOk(eventId: event.id, accepted: true), relay: "relay", url: url))
        }
    }
}

/// contracts/protocol/sync/leave.json (PROTOCOL-v2 "Leave"), the Mac's side: the `leave` body and lifetime, the real
/// wraps (who sealed them, to whom, when), the outbox rule, and *Stop following* sending it until two relays keep it.
/// The wearer's side (`wearer`) is the Android phone's (`LeaveWearerTest`).
struct LeaveFixtureTests {
    let fixture = loadJSON("protocol/sync/leave.json")
    let crypto = NostrSDKCrypto()
    let v = OgsVectors.shared

    private var keys: [String: [String: String]] { fixture["keys"] as! [String: [String: String]] }

    @Test func theBodyAndItsLifetimeAreTheFixtures() throws {
        let body = try JSONSerialization.jsonObject(with: Data(JSONText.encode(FollowerMessages.leaveBody()).utf8))
        #expect(NSDictionary(dictionary: body as! [String: Any]).isEqual(to: fixture["body"] as! [String: Any]))
        #expect(Wire.ttlLeave == fixture["ttl"] as! Int)
    }

    @Test func everyWrapOpensAsTheFixtureSays() throws {
        let events = fixture["events"] as! [String: Any]
        for (name, meta) in fixture["wraps"] as! [String: [String: Any]] {
            let wrap = try #require(SignedEvent.decode(json: JSONText.encode(events[name]!)))
            #expect(crypto.verify(wrap), "\(name)")
            #expect(wrap.kind == Wire.kindWrap, "\(name)")
            let to = keys[meta["to"] as! String]!
            #expect(wrap.tag("p") == to["pub"], "\(name)")
            #expect(wrap.tag("expiration") == String(meta["expiration"] as! Int), "\(name)")
            if meta["to"] as! String != "I" {
                #expect(throws: CryptoError.self, "\(name)") {
                    try crypto.unwrap(wrap, with: SecretKeyHex(keys["I"]!["sec"]!)!)
                }
            }
            let rumor = try crypto.unwrap(wrap, with: SecretKeyHex(to["sec"]!)!)
            #expect(rumor.sender == keys[meta["from"] as! String]!["pub"], "\(name)")
            #expect(rumor.kind == Wire.kindToOwner, "\(name)")
            #expect(rumor.createdAt == meta["at"] as! Int, "\(name)")
            let body = try JSONSerialization.jsonObject(with: Data(rumor.content.utf8)) as! [String: Any]
            #expect(body["type"] as? String == meta["type"] as? String, "\(name)")
        }
    }

    /// An answer counts for the relay whose connection it came on, when it names the leave and says the relay keeps it
    /// (`true`, or `false` with `duplicate:`); told once a relay or the wearer's phone took it, done at two of the valid
    /// relays (or every one), or once expired.
    @Test func theOutboxIsDoneOnlyWhenTwoRelaysKeepIt() throws {
        let outbox = fixture["outbox"] as! [String: Any]
        let made = fixture["now"] as! Int
        let ttl = fixture["ttl"] as! Int
        var wrap = try #require(
            SignedEvent.decode(json: JSONText.encode((fixture["events"] as! [String: Any])["leave-F"]!)))
        wrap.id = outbox["id"] as! String
        for run in outbox["runs"] as! [[String: Any]] {
            let name = run["name"] as! String
            var leave = PendingLeave(
                wrap: wrap, relays: (run["relays"] ?? outbox["relays"]) as! [String], expiry: made + ttl, wearer: "Ana")
            leave.nearby = run["nearby"] as? Bool ?? false
            for answer in run["answers"] as! [[String: Any]] {
                guard let ok = RelayOk(frame: JSONText.encode(answer["ok"]!)) else { continue }
                leave.answered(ok, from: answer["from"] as! String)
            }
            let at = made + (run["after"] as? Int ?? 0)
            #expect(leave.told == run["told"] as! Bool, "\(name) told")
            #expect(leave.done(now: at) == run["done"] as! Bool, "\(name) done")
            #expect(leave.waitingOn == run["waiting"] as! [String], "\(name) waiting")
            #expect(
                LeaveOutbox(leaves: [leave]).pruned(now: at).leaves.count == (leave.done(now: at) ? 0 : 1), "\(name)")
        }
    }

    /// *Stop following* (here: cancelling a request that waits) seals a `leave` before the key goes, keeps only the
    /// wrap in `leave-outbox.json` and sends it until two relays keep it; one relay isn't enough.
    @Test func stopFollowingTellsTheWearerUntilTwoRelaysKeepIt() async throws {
        let answering = Mutex<[AnsweringRelays]>([])
        let urls = Mutex(["wss://nos.lol"])
        let h = RuntimeHarness(
            now: v.now, hub: FakeRelayHub(stored: [v.event("invite")]),
            leaveRelays: { _, _ in
                let relays = AnsweringRelays(urls: urls.withLock { $0 })
                answering.withLock { $0.append(relays) }
                return relays
            },
            sleep: { _ in try await Task.sleep(for: .milliseconds(30)) })
        await h.runtime.start()
        try await h.runtime.lookUpAndFollow(link: (v.derivations["invite"] as! [String: Any])["link"] as! String)
        let leaves = h.stores.leaves
        await h.runtime.unfollow()
        #expect(try h.secrets.read(account: KeychainStore.followerAccount) == nil)
        #expect(await eventually { !answering.withLock { $0 }.isEmpty })
        let first = try #require(answering.withLock { $0.first })
        #expect(await eventually { await !first.published.isEmpty })
        let wrap = try #require(await first.published.first)
        let rumor = try crypto.unwrap(wrap, with: v.secret("I"))
        let body = try JSONSerialization.jsonObject(with: Data(rumor.content.utf8)) as! [String: Any]
        #expect(body["type"] as? String == "leave")
        #expect(wrap.tag("expiration") == String(v.now + Wire.ttlLeave))
        // one relay keeps it: told (nothing shown), but still sent until a second one does
        #expect(await eventually { await first.stopped })
        let kept = try #require(await leaves.load()?.leaves.single)
        #expect(kept.told && kept.accepted == ["wss://nos.lol"] && kept.wearer == "Alex")
        #expect(await eventually { await h.runtime.snapshotNow.untoldLeave == nil })
        urls.withLock { $0 = ["wss://relay.example.org/"] }  // the invite's other relay
        await h.runtime.reconnect()  // the Mac woke / the network came back
        #expect(await eventually { await leaves.load() == nil })
        await h.runtime.stop()
    }

    /// Offline: the outbox keeps it and the follow rows say the wearer hasn't been told yet.
    @Test func aLeaveNobodyTookIsShownUntilOneDoes() async throws {
        let urls = Mutex<[String]>([])
        let h = RuntimeHarness(
            now: v.now, hub: FakeRelayHub(stored: [v.event("invite")]),
            leaveRelays: { _, _ in AnsweringRelays(urls: urls.withLock { $0 }) },
            sleep: { _ in try await Task.sleep(for: .milliseconds(30)) })
        await h.runtime.start()
        try await h.runtime.lookUpAndFollow(link: (v.derivations["invite"] as! [String: Any])["link"] as! String)
        await h.runtime.unfollow()
        #expect(await eventually { await h.runtime.snapshotNow.untoldLeave == "Alex" })
        urls.withLock { $0 = ["wss://nos.lol", "wss://relay.example.org"] }  // the invite's two relays
        await h.runtime.reconnect()
        #expect(await eventually { await h.runtime.snapshotNow.untoldLeave == nil })
        #expect(await eventually { await h.stores.leaves.load() == nil })
        await h.runtime.stop()
    }
}

extension Array {
    fileprivate var single: Element? { count == 1 ? first : nil }
}
