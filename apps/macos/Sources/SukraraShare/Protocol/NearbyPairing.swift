// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation

/// *Pair nearby* numeric comparison (PROTOCOL-v2 "Nearby pairing"): the wearer commits to `nb` in its offer, the
/// follower sends `na` with its request, the wearer opens `nb` to the request that claimed the invite, and both
/// devices show the same 6-digit `code`. Vectors: `contracts/protocol/ble/nearby-pairing.json`.
public enum NearbyPairing {
    /// An offer lasts 10 minutes; one that claims more (plus clock skew) is ignored.
    public static let offerSeconds = 600
    public static let skewSeconds = 120

    /// `c` = lowercase hex SHA-256 of `"cgm2:pair-c:" + nonce`.
    public static func commit(_ nonce: String) -> String {
        Hex.encode(SHA256.hash(data: Data("cgm2:pair-c:\(nonce)".utf8)))
    }

    /// Whether an offer expiring at `expiry` may be listed at `now`: not expired, no later than 10 min + skew.
    public static func offerExpiryOK(_ expiry: Int, now: Int) -> Bool {
        expiry > now && expiry <= now + offerSeconds + skewSeconds
    }

    /// `int(SHA-256("cgm2:pair-code:" + I + ":" + F + ":" + t + ":" + na + ":" + nb)) mod 10^6`, zero-padded.
    public static func code(
        identity: String, follower: String, token: String, followerNonce: String, wearerNonce: String
    ) -> String {
        let text = "cgm2:pair-code:\(identity):\(follower):\(token):\(followerNonce):\(wearerNonce)"
        let value = SHA256.hash(data: Data(text.utf8)).reduce(0) { ($0 * 256 + Int($1)) % 1_000_000 }
        return String(format: "%06d", value)
    }

    /// `482913` → `482 913` (what the screens show).
    public static func spaced(_ code: String) -> String {
        guard code.count == 6 else { return code }
        return "\(code.prefix(3)) \(code.suffix(3))"
    }

    /// A fresh 32-byte nonce, lowercase hex (the system generator is cryptographically secure on macOS).
    public static func nonce() -> String {
        var generator = SystemRandomNumberGenerator()
        return Hex.encode((0..<32).map { _ in UInt8.random(in: 0...255, using: &generator) })
    }
}
