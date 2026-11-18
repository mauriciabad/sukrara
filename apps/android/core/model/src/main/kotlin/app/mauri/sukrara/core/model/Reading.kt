// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Instant

/**
 * One glucose value. [mgdl] is always mg/dL (the unit setting only changes how it is shown). [sensorRate] is the
 * sensor's own rate (mg/dL/min) when it sends one: stored, sent on the wire and written to LibreView / Tidepool / the
 * CSV, and nothing else reads it but [TrendRate] (tools/check `trend`). [trend] is THE trend at this reading, written
 * only by [TrendRate.annotate] at the one place readings are read; never stored or sent. A value outside
 * [GlucoseRange] is a flagged LO / HI reading, not a number.
 */
data class Reading(
    val t: Instant,
    val mgdl: Int,
    val sensorRate: Double?,
    val source: SourceId,
    val trend: ReadingTrend? = null,
) {
    /** THE trend's rate (mg/dL/min), null without a trend: what every arrow, word and output reads. */
    val rate: Double? get() = trend?.rate

    /** Lower than the sensor can measure: shown as "LO", below every low alert threshold. */
    val isLo: Boolean get() = GlucoseRange.isLo(mgdl)

    /** Higher than the sensor can measure: shown as "HI", above every high alert threshold. */
    val isHi: Boolean get() = GlucoseRange.isHi(mgdl)

    /** Brought back by Import history ([SourceId.Imported]): never uploaded to a service. */
    val imported: Boolean get() = source in SourceId.Imported

    companion object {
        /** A sensor said "below my range" at [t]: no number is known (stored as [GlucoseRange.LO], no rate). */
        fun lo(t: Instant, source: SourceId) = Reading(t, GlucoseRange.LO, null, source)

        /** A sensor said "above my range" at [t] (stored as [GlucoseRange.HI], no rate). */
        fun hi(t: Instant, source: SourceId) = Reading(t, GlucoseRange.HI, null, source)
    }
}

/**
 * The range a glucose number is shown in. Outside it a reading is a flag, LO or HI, never a number: CGMs measure about
 * 40–400 mg/dL and say "LO" / "HI" beyond. LO / HI travel as 39 / 401 mg/dL, the values Nightscout, xDrip+ and the
 * Dexcom share use, so followers, uploads and exports understand them without a new field. The card ([Reading.isLo] /
 * [Reading.isHi]) and the alert rules (contracts/alerts/SPEC.md §1: LO matches every low kind, HI every high kind) read
 * the same bounds; the Mac has the same numbers.
 */
object GlucoseRange {
    const val FLOOR = 40
    const val CEILING = 400

    /** How a LO reading is stored and sent. */
    const val LO = FLOOR - 1

    /** How a HI reading is stored and sent. */
    const val HI = CEILING + 1

    fun isLo(mgdl: Int): Boolean = mgdl < FLOOR

    fun isHi(mgdl: Int): Boolean = mgdl > CEILING

    /** What a glucose value can be at all: anything else (0, negative, absurd) is malformed and dropped. */
    val PLAUSIBLE = 1..1000

    /**
     * [r] as it is stored, at the one way in (the hub): a malformed value is null (dropped), any number below the floor
     * becomes [LO] and any above the ceiling [HI], without a rate. Every source (sensor, Juggluco, relay, Nearby) then
     * reaches the card, the graph, uploads and the export with the same two flags, never a raw 35 or 450.
     */
    fun normalise(r: Reading): Reading? =
        when {
            r.mgdl !in PLAUSIBLE -> null
            isLo(r.mgdl) -> if (r.mgdl == LO && r.sensorRate == null) r else Reading.lo(r.t, r.source)
            isHi(r.mgdl) -> if (r.mgdl == HI && r.sensorRate == null) r else Reading.hi(r.t, r.source)
            else -> r
        }
}

/**
 * Where readings come from (the ids contracts/sources/sources.json `readings` lists; dedup.json ranks them). A value
 * class so new sources are added without touching every `when`.
 */
@JvmInline
value class SourceId(val id: String) {
    companion object {
        /** The phone talks to a Libre 3 sensor over Bluetooth (source `sensor`). */
        val Libre3 = SourceId("libre3")

        /** Juggluco's local web server on the same phone (source `phoneApp`). */
        val Juggluco = SourceId("juggluco")

        /** The other sensor families the phone reads itself (source `sensor`); untested on real sensors. */
        val DexcomG7 = SourceId("dexcom-g7")
        val DexcomG6 = SourceId("dexcom-g6")
        val SmartGuide = SourceId("accu-chek-smartguide")
        val AidexX = SourceId("aidex-x")

        /** A Nightscout site read as the source (source `nightscout`): first-hand, online, never uploaded back to it. */
        val Nightscout = SourceId("nightscout")

        /** What reaches this phone over the internet from a service (contracts/sources/outputs.json): no sensor label. */
        val Online: Set<SourceId> = setOf(Nightscout)

        /**
         * What this phone gets first-hand: its own sensor, an app on it that reads one, or an online source (never
         * relayed by another Sukrara).
         */
        val FirstHand: Set<SourceId> = setOf(Libre3, Juggluco, DexcomG7, DexcomG6, SmartGuide, AidexX, Nightscout)

        /** Another Sukrara's readings over Nostr relays (source `sukrara`). */
        val Relay = SourceId("relay")

        /** Another Sukrara's readings over Bluetooth nearby (source `sukrara`). */
        val Nearby = SourceId("nearby")

        /** What another Sukrara relays (a Sukrara stream). */
        val Relayed: Set<SourceId> = setOf(Relay, Nearby)

        /**
         * History brought back by Import history (`storage.ImportSource`: LibreView, Tidepool, a file): the person's own
         * past readings, never this phone's sensor (no live reading, no restore gate) and never uploaded.
         */
        val Imported: Set<SourceId> = setOf(SourceId("import-libreview"), SourceId("import-tidepool"), SourceId("import-file"))
    }
}

/** The stored days and the live window as one history. */
object ReadingHistory {
    /**
     * [stored] (older readings, oldest first, read now and then) up to where the [live] window starts, then the whole
     * live window (always the fresher copy: a backfill inside it shows at once). Nothing counts twice.
     */
    fun join(
        stored: List<Reading>,
        live: List<Reading>,
    ): List<Reading> {
        val start = live.firstOrNull()?.t ?: return stored
        return stored.filter { it.t.isBefore(start) } + live
    }
}
