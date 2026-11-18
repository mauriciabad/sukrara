// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * THE rank rule (contracts/sources/dedup.json, docs/44 "Data" rule 5): one reading per measurement, whichever sources
 * brought it. Readings of two sources within [WINDOW] (either way) are the same measurement and the higher [Rank] wins:
 * a lower stored reading goes when the higher one arrives, a lower incoming one is dropped; with equal ranks what is
 * stored first stays (relay and nearby carry the same reading). A source re-sending its own time replaces itself.
 * Readings of one source never merge (a sensor reads every minute). Every store of readings goes through [plan].
 *
 * The fence (contracts/sources/link.json `fence`, docs/44 Appendix G finding 5): when the source changes for the same
 * person, or a link is left with its history kept, the next source continues that history. Its backfill fills gaps but
 * never replaces a kept reading at or before the fence (the last kept reading); after the fence the rank rule applies.
 */
object ReadingRank {
    /** Two readings of different sources this close are the same measurement (Import history's rule too). */
    val WINDOW: Duration = Duration.ofMinutes(2)

    /** Highest first (dedup.json `rank`). */
    enum class Rank { Sensor, PhoneApp, Network, Online, OnlineHistory, Sukrara, Imported }

    private val RANKS: Map<String, Rank> =
        buildMap {
            for (s in SourceKind.Sensor.readings) put(s.id, Rank.Sensor)
            put(SourceId.Juggluco.id, Rank.PhoneApp)
            // the Mac's Juggluco on the network: never stored here, ranked as the contract says
            put("juggluco-lan", Rank.Network)
            for (s in SourceId.Online) put(s.id, Rank.Online)
            // an online source's graph points only fill gaps between its live readings
            put(SourceId.LibreLinkUpGraph.id, Rank.OnlineHistory)
            for (s in SourceId.Relayed) put(s.id, Rank.Sukrara)
            for (s in SourceId.Imported) put(s.id, Rank.Imported)
        }

    /** [source]'s rank; an id no source declares (a debug build's made-up source) ranks as a sensor. */
    fun of(source: SourceId): Rank = RANKS[source.id] ?: Rank.Sensor

    /** What a store does with a batch: the readings to write and the stored ones to remove. */
    data class Plan(
        val upsert: List<Reading>,
        val remove: List<Reading>,
    )

    /**
     * The plan for [incoming] against [stored] (every stored reading within [WINDOW] of the batch, any source), batch
     * in time order: what to write ([Plan.upsert]) and which stored readings it replaces ([Plan.remove]). A stored
     * reading at or before [fence] is kept whatever comes: an incoming one of the same measurement is dropped.
     */
    fun plan(
        stored: List<Reading>,
        incoming: List<Reading>,
        fence: Instant? = null,
    ): Plan {
        val window = WINDOW.toMillis()
        val pool = stored.toMutableList()
        val removed = ArrayList<Reading>()
        val written = ArrayList<Reading>()
        for (r in incoming.sortedWith(compareBy({ it.t }, { of(it.source) }, { it.source.id }))) {
            val t = r.t.toEpochMilli()
            val near = pool.filter { it.source != r.source && abs(it.t.toEpochMilli() - t) <= window }
            if (near.any { of(it.source) <= of(r.source) }) continue
            // a kept reading behind the fence: this one is the same measurement (or its re-send) and never replaces it
            if (fence != null && (near + pool.filter { it.source == r.source && it.t == r.t }).any { it.kept(fence, written) }) continue
            for (n in near) {
                pool.remove(n)
                if (written.remove(n).not()) removed += n
            }
            pool.removeAll { it.source == r.source && it.t == r.t }
            written.removeAll { it.source == r.source && it.t == r.t }
            pool += r
            written += r
        }
        return Plan(written, removed)
    }

    /** Stored before this batch (not written by it) at or before [fence]. */
    private fun Reading.kept(
        fence: Instant,
        written: List<Reading>,
    ): Boolean = !t.isAfter(fence) && written.none { it === this }
}
