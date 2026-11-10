// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

/**
 * The fixed numbers and opaque tags of CGM-over-Nostr protocol v2 (contracts/protocol/PROTOCOL-v2.md). Changing any
 * value here breaks every follower in the field: a spec change lands in the spec, Android and Mac together.
 */
object Wire {
    const val KIND_LIVE = 21078
    const val KIND_ALERT = 21079
    const val KIND_DATA = 30078
    const val KIND_WRAP = 1059
    const val KIND_TO_OWNER = 30500
    const val KIND_TO_FOLLOWER = 30501
    const val KIND_INVITE = 30502

    /** Payload `v` of live, chunk, settings, roster, backup and 30500/30501 rumors. */
    const val V = 2
    const val INVITE_V = 3
    const val TREATMENT_V = 1

    const val HOUR = 3600L
    const val DAY = 86_400L
    const val TTL_LIVE = HOUR
    const val TTL_CHUNK_AFTER_HOUR = 7 * DAY
    const val TTL_ROSTER = 30 * DAY
    const val TTL_SETTINGS = 30 * DAY
    const val TTL_TREATMENT = 30 * DAY
    const val TTL_BACKUP = 90 * DAY

    /** Gift wrap lifetimes (PROTOCOL-v2 "TTLs"), absolute from now. */
    const val TTL_REQUEST = 7 * DAY
    const val TTL_HISTORY_REQUEST = DAY
    const val TTL_KEY_DELIVERY = 30 * DAY

    /** Followers drop alerts whose `at` is older than this. */
    const val ALERT_MAX_AGE = 600L

    /** A follower sends at most one rekey request per unknown `gk` in this window. */
    const val REKEY_THROTTLE = 300L

    /** Relays that take events from new keys and serve gift wraps back to their recipient (measured 2026-10-17). */
    val DEFAULT_RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net", "wss://nostr.mom", "wss://relay.snort.social")

    /** `H(x) = hex(sha256(x))[:32]` */
    fun h(x: String): String = Hex.encode(Hex.sha256(x.toByteArray())).substring(0, 32)

    /** `gk_n = hex(sha256(G_n.pub bytes))[:8]` — names an epoch in tags. */
    fun gk(readKey: PublicKeyHex): String = Hex.encode(Hex.sha256(readKey.bytes())).substring(0, 8)

    fun hourOf(t: Long): Long = t - Math.floorMod(t, HOUR)

    fun chunkD(sid: String, hour: Long) = h("cgm2:h:$sid:$hour")

    fun glucoseC(sid: String) = h("cgm2:glucose:$sid")

    fun rosterD(sid: String) = h("cgm2:roster:$sid")

    fun backupD(identity: PublicKeyHex) = h("cgm2:backup:${identity.hex}")

    fun treatmentsC(sid: String) = h("cgm2:treatments:$sid")

    fun treatmentD(sid: String, id: String) = h("cgm2:tr:$sid:$id")

    fun settingsD(sid: String) = h("cgm2:settings:$sid")

    /** The most relays kept from a received list. */
    const val MAX_RELAYS = 8

    private const val MAX_RELAY_URL = 200
    private val LAN_NAMES = listOf("localhost", "local", "lan", "home", "internal", "localdomain", "home.arpa")

    /**
     * The relays the protocol accepts from a received list (PROTOCOL-v2 "Relay rules", contracts/protocol/relays): the
     * first [MAX_RELAYS] valid ones ([isRelayUrl]), duplicates removed.
     */
    fun validRelays(urls: List<String>): List<String> = urls.filter(::isRelayUrl).distinct().take(MAX_RELAYS)

    /** A `wss://` URL with a public host: no user info, query or fragment, no loopback / LAN name or address. */
    fun isRelayUrl(url: String): Boolean {
        if (url.length > MAX_RELAY_URL || !url.startsWith("wss://") || url.any { it.code !in 0x21..0x7e }) return false
        val rest = url.removePrefix("wss://")
        if ('?' in rest || '#' in rest || '@' in rest) return false
        val authority = rest.substringBefore('/')
        if (authority.startsWith('[')) return false
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val port = authority.substring(colon + 1)
            if (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65535) return false
        }
        return publicHost(if (colon >= 0) authority.substring(0, colon).lowercase() else authority.lowercase())
    }

    private fun publicHost(host: String): Boolean {
        val labels = host.split('.')
        val wellFormed =
            host.length <= 253 &&
                labels.size >= 2 &&
                labels.all { l ->
                    l.length in 1..63 && l.first() != '-' && l.last() != '-' &&
                        l.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
                }
        return when {
            !wellFormed -> false
            labels.all { l -> l.all { it in '0'..'9' } } -> publicIpv4(labels)
            else -> LAN_NAMES.none { host == it || host.endsWith(".$it") }
        }
    }

    /** Not 0/8, 10/8, 100.64/10, 127/8, 169.254/16, 172.16/12, 192.168/16 or 224/3 (multicast, reserved, broadcast). */
    private fun publicIpv4(labels: List<String>): Boolean {
        if (labels.size != 4 || labels.any { it.length > 3 || it.toInt() > 255 }) return false
        val a = labels[0].toInt()
        val b = labels[1].toInt()
        return !(
            a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && b in 64..127) || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168)
        )
    }
}
