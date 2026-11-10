// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol.invite

import app.mauri.sukrara.protocol.Hex
import app.mauri.sukrara.protocol.PublicKeyHex
import java.math.BigInteger

/**
 * *Pair nearby* numeric comparison (PROTOCOL-v2 §Nearby pairing): the wearer commits to `nb` in its offer, the follower
 * sends `na` with its request, the wearer opens `nb` to the request that claimed the invite, and both phones show the
 * same 6-digit [code]. Vectors: `contracts/protocol/ble/nearby-pairing.json`.
 */
object NearbyPairing {
    /** An offer lasts 10 minutes; one that claims more (plus clock skew) is ignored. */
    const val OFFER_S = 600L
    const val SKEW_S = 120L
    const val NONCE_BYTES = 32

    /** `c` = lowercase hex SHA-256 of `"cgm2:pair-c:" + nonce`. */
    fun commit(nonce: String): String = Hex.encode(Hex.sha256("cgm2:pair-c:$nonce".toByteArray()))

    /** A 32-byte nonce or a commitment: 64 lowercase hex. */
    fun isHex64(s: String?): Boolean = s != null && Hex.isKey(s)

    /** Whether an offer expiring at [expiry] may be listed at [now]: not expired, and no later than 10 min + skew. */
    fun offerExpiryOk(expiry: Long, now: Long): Boolean = expiry > now && expiry <= now + OFFER_S + SKEW_S

    /** The 6 digits both phones show: `int(SHA-256("cgm2:pair-code:"+I+":"+F+":"+t+":"+na+":"+nb)) mod 10^6`. */
    fun code(
        identity: PublicKeyHex,
        follower: PublicKeyHex,
        token: String,
        followerNonce: String,
        wearerNonce: String,
    ): String {
        val h = Hex.sha256("cgm2:pair-code:${identity.hex}:${follower.hex}:$token:$followerNonce:$wearerNonce".toByteArray())
        return BigInteger(1, h).mod(BigInteger.valueOf(1_000_000)).toString().padStart(6, '0')
    }

    /** `482913` → `482 913` (what the screens show). */
    fun spaced(code: String): String = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code

    /** A fresh nonce from [bytes] (the caller's SecureRandom). */
    fun nonce(bytes: ByteArray): String {
        require(bytes.size == NONCE_BYTES) { "pairing nonce must be 32 bytes" }
        return Hex.encode(bytes)
    }
}
