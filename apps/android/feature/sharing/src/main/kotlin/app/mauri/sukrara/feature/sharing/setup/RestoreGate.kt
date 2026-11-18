// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceStatus
import java.time.Duration
import java.time.Instant

/** Why restoring the identity isn't possible yet; each one has its own sentence and way forward on screen. */
enum class RestoreBlock {
    /** P2: this app hasn't received a live reading from its own sensor yet. */
    NoLiveReading,
}

/**
 * The restore gate. Restoring moves the stream to this phone, so the phone that shared until now stops
 * reaching the followers: allowed only once your glucose arrives here first-hand. The screen always shows the warning
 * sentence. [source]: where it comes from, so the checklist's first line says what is true (*Sensor running in
 * Sukrara* only for the sensor Sukrara reads; *Readings arriving from Nightscout* for a site).
 */
data class RestoreGate(
    val blocks: Set<RestoreBlock>,
    val source: SourceKind? = null,
) {
    val open: Boolean get() = blocks.isEmpty()

    companion object {
        /** A reading older than this doesn't prove the sensor runs here now. */
        val LIVE_WINDOW: Duration = Duration.ofMinutes(15)

        private val OWN_SOURCES = SourceId.FirstHand

        fun evaluate(
            ownSensorLive: Boolean,
            source: SourceKind? = null,
        ): RestoreGate =
            RestoreGate(
                if (ownSensorLive) emptySet() else setOf(RestoreBlock.NoLiveReading),
                source,
            )

        /** Readings are arriving live from this phone's own sensor (not a follower feed), the newest ≤ 15 min old. */
        fun ownSensorLive(
            state: GlucoseState,
            now: Instant,
        ): Boolean {
            val latest = state.latest ?: return false
            return state.source is SourceStatus.Live &&
                latest.source in OWN_SOURCES &&
                !latest.t.isAfter(now.plusSeconds(60)) &&
                Duration.between(latest.t, now) <= LIVE_WINDOW
        }
    }
}
