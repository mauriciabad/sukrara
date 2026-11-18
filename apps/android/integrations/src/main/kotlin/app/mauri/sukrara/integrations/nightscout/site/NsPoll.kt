// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

/**
 * When the Nightscout source asks the site again (contracts/sources/nightscout/poll.json): right after the next reading
 * should have reached the site (one cadence after the newest, plus the uploader's lag), then every 30 s while it is
 * overdue and every minute once it is long overdue; failures back off, a refusal waits for a new token.
 */
object NsPoll {
    const val MIN_S = 15L
    const val UPLOAD_LAG_S = 45L
    const val MIN_CADENCE_S = 60L
    const val MAX_CADENCE_S = 300L
    const val OVERDUE_S = 30L
    const val LATE_S = 60L
    const val LATE_AFTER_S = 300L
    val BACKOFF_S = listOf(30L, 60L, 120L, 300L)
    const val REFUSED_S = 1800L

    /**
     * Seconds to wait at [now] (epoch s): after an answer ([failures] 0) from the [newest] and [previous] readings' times
     * (null = none); after [failures] failures in a row, the backoff ([retryAfterS]: the site's Retry-After, when longer;
     * [refused]: the token was refused).
     */
    fun waitS(
        now: Long,
        newest: Long?,
        previous: Long?,
        failures: Int,
        retryAfterS: Long? = null,
        refused: Boolean = false,
    ): Long {
        if (refused) return REFUSED_S
        if (failures > 0) {
            val backoff = BACKOFF_S[(failures - 1).coerceAtMost(BACKOFF_S.size - 1)]
            return maxOf(backoff, retryAfterS ?: 0)
        }
        newest ?: return LATE_S
        val cadence = previous?.let { (newest - it).coerceIn(MIN_CADENCE_S, MAX_CADENCE_S) } ?: MAX_CADENCE_S
        val due = newest + cadence + UPLOAD_LAG_S
        return when {
            due - now >= MIN_S -> due - now
            due - now > 0 -> MIN_S
            now - due <= LATE_AFTER_S -> OVERDUE_S
            else -> LATE_S
        }
    }
}
