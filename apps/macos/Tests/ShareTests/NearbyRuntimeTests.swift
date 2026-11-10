// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Synchronization
import Testing

/// The follower's Bluetooth path without a radio: the test plays the phones in range.
final class FakeNearby: NearbyTransport {
    let events: AsyncStream<NearbyEvent>
    private let continuation: AsyncStream<NearbyEvent>.Continuation
    private let leaves = Mutex<[String]>([])

    init() { (events, continuation) = AsyncStream.makeStream() }

    var left: [String] { leaves.withLock { $0 } }
    func start() async {}
    func stop() async { continuation.finish() }
    func update(identity: NearbyLink.Identity) async {}
    func send(_ event: SignedEvent) async -> Bool { false }
    func leave(phone: UUID, for seconds: Double) async { leaves.withLock { $0.append("\(phone) \(Int(seconds))") } }
    func info(_ info: BleInfo, _ owner: BleInfo.Owner, phone: UUID) {
        continuation.yield(.info(info, owner, phone: phone))
    }
}

/// Which phones the follower avoids, and for how long (B§6 "Which owner").
struct NearbyRuntimeTests {
    let v = OgsVectors.shared

    private func runtime(_ h: RuntimeHarness, _ nearby: FakeNearby) -> FollowerRuntime {
        FollowerRuntime(
            secrets: h.secrets, stores: h.stores, crypto: NostrSDKCrypto(), clock: h.clock,
            me: Profile(name: "Mac", avatar: nil), makeRelays: { _, _ in h.hub.connect() }, makeNearby: { _ in nearby },
            sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
    }

    private let stranger = BleInfo(nonce: "00", m: "00", identity: nil, relays: [], pairing: nil)

    @Test func anotherWearersPhoneIsAvoidedForTenMinutes() async throws {
        let h = RuntimeHarness(now: v.now, hub: FakeRelayHub(stored: [v.event("invite")]))
        let nearby = FakeNearby()
        let runtime = runtime(h, nearby)
        await runtime.start()
        try await runtime.lookUpAndFollow(link: (v.derivations["invite"] as! [String: Any])["link"] as! String)
        let phone = UUID()
        nearby.info(stranger, .other, phone: phone)
        #expect(await eventually { nearby.left == ["\(phone) 600"] })
        await runtime.stop()
    }

    /// A phone offering *Pair nearby* as `identity` (`secret`), with its pairing nonce `nb` committed in the offer.
    private struct Offerer {
        let secret: SecretKeyHex
        let identity: String
        let nb = NearbyPairing.nonce()

        init(_ crypto: NostrSDKCrypto) throws {
            secret = crypto.generateSecret()
            identity = try crypto.publicKey(of: secret)
        }

        func info(now: Int) -> BleInfo {
            BleInfo(
                nonce: "00", m: "00", identity: identity, relays: [],
                pairing: .init(
                    token: "C_TX2IzrmRf7hg8cATyckQ", expiry: now + 600, profile: Profile(name: "Marta", avatar: nil),
                    commit: NearbyPairing.commit(nb)))
        }

        func wrap(_ body: [String: Any], to follower: String, crypto: NostrSDKCrypto, now: Int) throws -> SignedEvent {
            try crypto.giftWrap(
                kind: Wire.kindToFollower, content: JSONText.encode(body), from: secret, to: follower,
                expiration: now + 3600)
        }
    }

    /// Joins the offer of `offerer` found over Bluetooth; returns the follower key and the request's nonce `n`.
    private func joinNearby(_ h: RuntimeHarness, _ nearby: FakeNearby, _ runtime: FollowerRuntime, _ offerer: Offerer)
        async throws -> (follower: String, na: String)
    {
        let crypto = NostrSDKCrypto()
        await runtime.start()
        await runtime.searchNearby(true)
        nearby.info(offerer.info(now: v.now), .unknown, phone: UUID())
        var offer: NearbyOffer?
        for await snapshot in await runtime.snapshots() {
            offer = snapshot.nearbyOffer
            if offer != nil { break }
        }
        try await runtime.joinNearby(try #require(offer))
        let state = try FollowerState.decode(try #require(try h.secrets.read(account: KeychainStore.followerAccount)))
        let follower = try crypto.publicKey(of: state.follower)
        #expect(await eventually { await h.hub.published().contains { $0.tag("p") == offerer.identity } })
        let request = try #require(await h.hub.published().first { $0.tag("p") == offerer.identity })
        let rumor = try crypto.unwrap(request, with: offerer.secret)
        let body = try JSONSerialization.jsonObject(with: Data(rumor.content.utf8)) as! [String: Any]
        return (follower, body["n"] as! String)
    }

    private func key(_ offerer: Offerer, to follower: String) throws -> SignedEvent {
        let crypto = NostrSDKCrypto()
        let g = try crypto.nsecEncode(v.secret("G1"))
        return try offerer.wrap(
            ["v": 2, "type": "key", "sid": v.sid, "epoch": 1, "g": g, "devices": [v.pub("D")], "relays": []],
            to: follower, crypto: crypto, now: v.now)
    }

    /// #20: a phone posing as the wearer (another identity, any name) answers and approves at once; the Mac shows the
    /// code but follows nobody until the person confirms it, and a different code ends the join with the key removed.
    @Test func aPhonePosingAsTheWearerIsNeverFollowedWithoutTheCode() async throws {
        let h = RuntimeHarness(now: v.now)
        let nearby = FakeNearby()
        let runtime = runtime(h, nearby)
        let crypto = NostrSDKCrypto()
        let impostor = try Offerer(crypto)
        let (follower, na) = try await joinNearby(h, nearby, runtime, impostor)

        await h.hub.inject(try key(impostor, to: follower))
        try await Task.sleep(for: .milliseconds(100))
        #expect(
            await runtime.currentLink
                == .requested(code: anyCode(h), wearer: "Marta", pairing: .init(code: nil, confirmed: false)))
        #expect(await runtime.confirmNearby(same: true) == false)  // no code yet: nothing to confirm

        await h.hub.inject(
            try impostor.wrap(["v": 2, "type": "pair", "n": impostor.nb], to: follower, crypto: crypto, now: v.now))
        let code = NearbyPairing.code(
            identity: impostor.identity, follower: follower, token: "C_TX2IzrmRf7hg8cATyckQ", followerNonce: na,
            wearerNonce: impostor.nb)
        #expect(
            await eventually {
                await runtime.currentLink
                    == .requested(code: anyCode(h), wearer: "Marta", pairing: .init(code: code, confirmed: false))
            })
        #expect(await !runtime.isFollowing)

        // the real wearer's phone shows another code: the person says so
        #expect(await runtime.confirmNearby(same: false))
        #expect(await runtime.currentLink == .notFollowing)
        #expect(try h.secrets.read(account: KeychainStore.followerAccount) == nil)
        await runtime.stop()
    }

    @Test func theSameCodeLetsTheHeldKeyIn() async throws {
        let h = RuntimeHarness(now: v.now)
        let nearby = FakeNearby()
        let runtime = runtime(h, nearby)
        let crypto = NostrSDKCrypto()
        let wearer = try Offerer(crypto)
        let (follower, _) = try await joinNearby(h, nearby, runtime, wearer)
        // a pair nonce that doesn't open the offer's commitment is not from that phone
        await h.hub.inject(
            try wearer.wrap(
                ["v": 2, "type": "pair", "n": NearbyPairing.nonce()], to: follower, crypto: crypto, now: v.now))
        await h.hub.inject(try key(wearer, to: follower))
        await h.hub.inject(
            try wearer.wrap(["v": 2, "type": "pair", "n": wearer.nb], to: follower, crypto: crypto, now: v.now))
        #expect(
            await eventually {
                if case .requested(_, _, let pairing?) = await runtime.currentLink { return pairing.code != nil }
                return false
            })
        #expect(await !runtime.isFollowing)
        #expect(await runtime.confirmNearby(same: true))
        #expect(await eventually { await runtime.isFollowing })
        await runtime.stop()
    }

    /// The match code in the snapshot (the wearer types it only after a leaked link; unused nearby).
    private func anyCode(_ h: RuntimeHarness) -> String {
        guard let data = try? h.secrets.read(account: KeychainStore.followerAccount),
            let state = try? FollowerState.decode(data)
        else { return "" }
        return state.code
    }

    @Test func aPhoneWithNothingToPairWithIsLeftFor5Seconds() async {
        let h = RuntimeHarness(now: v.now)
        let nearby = FakeNearby()
        let runtime = runtime(h, nearby)
        await runtime.start()
        await runtime.searchNearby(true)
        let phone = UUID()
        nearby.info(stranger, .unknown, phone: phone)
        #expect(await eventually { nearby.left == ["\(phone) 5"] })
        await runtime.stop()
    }
}
