// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import SukraraTesting
import Testing

/// contracts/protocol/ble/nearby-pairing.json (PROTOCOL-v2 "Nearby pairing"), the same file Android's
/// `NearbyPairingVectorsTest` makes and reads: commitment, pairing code, offer expiry, the messages, and a follower
/// sequence of real signed gift wraps.
struct NearbyPairingVectorsTests {
    let fx = loadJSON("protocol/ble/nearby-pairing.json")
    let crypto = NostrSDKCrypto()

    private var now: Int { fx["now"] as! Int }
    private func key(_ name: String) -> [String: String] { (fx["keys"] as! [String: Any])[name] as! [String: String] }

    private func info(_ object: Any) -> BleInfo? {
        guard case .info(let info)? = BleMessage.parse(Data(JSONText.encode(object).utf8)) else { return nil }
        return info
    }

    @Test func commitmentAndCode() {
        #expect(NearbyPairing.commit(fx["nb"] as! String) == fx["commit"] as? String)
        for c in fx["codes"] as! [[String: String]] {
            #expect(NearbyPairing.commit(c["nb"]!) == c["c"])
            #expect(
                NearbyPairing.code(
                    identity: c["i"]!, follower: c["f"]!, token: c["t"]!, followerNonce: c["na"]!,
                    wearerNonce: c["nb"]!) == c["code"])
        }
        #expect(NearbyPairing.spaced("021472") == "021 472")
    }

    @Test func offerExpiryAndInfo() throws {
        for c in fx["offer_expiry"] as! [[String: Any]] {
            #expect(NearbyPairing.offerExpiryOK(c["x"] as! Int, now: now) == c["listed"] as? Bool)
        }
        let parsed = try #require(info(fx["info"]!))
        let offer = try #require(parsed.offer(now: now))
        #expect(offer.commit == fx["commit"] as? String)
        #expect(offer.invite.token == fx["token"] as? String)
        #expect(offer.invite.identity == key("I")["pub"])
        #expect(offer.wearerName == "Alex")
        // the wearer's INFO reads back as the same JSON
        let encoded = try JSONSerialization.jsonObject(with: BleMessage.info(parsed).encoded())
        #expect((encoded as! NSArray) == (fx["info"] as! NSArray))
        for (why, body) in fx["info_not_listed"] as! [String: Any] {
            let bad = info(["INFO", body])
            #expect(bad?.offer(now: now) == nil, "\(why)")
        }
    }

    @Test func messages() throws {
        let request = fx["request"] as! [String: Any]
        let profile = try #require(Profile(wire: request["profile"] as! [String: Any]))
        let body = FollowerMessages.requestBody(
            token: fx["token"] as! String, profile: profile, nonce: fx["na"] as? String)
        #expect(NSDictionary(dictionary: body) == NSDictionary(dictionary: request))
        let pair = try ToFollowerMessage.decode(JSONText.encode(fx["pair"]!))
        #expect(pair == .pair(nonce: fx["nb"] as! String))
    }

    /// The follower never uses the wearer's key before the person confirmed the code, and only for the committed nonce.
    @Test func followerSequence() throws {
        let seq = fx["follower"] as! [String: Any]
        let start = seq["start"] as! [String: String]
        #expect(start["status"] == "requested")
        var pair = FollowerState.NearbyPair(commit: start["c"]!, nonce: start["na"]!)
        pair.confirmed = false
        var state = FollowerState(
            follower: try #require(SecretKeyHex(key(seq["follower"] as! String)["sec"]!)),
            identity: seq["identity"] as! String, token: start["t"]!, code: "0000", pair: pair,
            me: Profile(name: "Mac", avatar: nil), wearer: nil, relays: [])
        state.status = .requested
        var session = try FollowerSession(state: state, crypto: crypto)
        let events = seq["events"] as! [String: Any]
        for step in seq["steps"] as! [[String: Any]] {
            let verdict: String?
            if step["confirm"] != nil {
                verdict = session.confirmPairing()?.verdict.description
            } else {
                let name = step["event"] as! String
                let event = try #require(SignedEvent.decode(json: JSONText.encode(events[name]!)))
                verdict = session.receive(event, now: now).verdict.description
            }
            #expect(verdict == step["expect"] as? String, "\(step)")
        }
        let expected = seq["expected"] as! [String: Any]
        #expect(session.state.status.rawValue == expected["status"] as? String)
        #expect(session.state.pair?.code == expected["code"] as? String)
        #expect(session.state.pair?.confirmed == expected["confirmed"] as? Bool)
        #expect(session.state.epochs.map(\.n).sorted() == expected["epochs"] as? [Int])
    }
}
