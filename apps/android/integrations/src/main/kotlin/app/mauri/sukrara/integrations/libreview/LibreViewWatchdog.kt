// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

/**
 * Whether one of Sukrara's LibreView sign-ins beside the official app stopped that app's upload (docs/45): a sign-in on
 * its identity did, silently, for 35 min, and LibreView could one day merge the identities' sessions. LibreLinkUp shows
 * it: the official app's readings stop reaching it. Pure; the uploader acts on the answer.
 */
object LvWatchdog {
    /** LibreLinkUp answered this long after the sign-in with no reading newer than it: the upload stopped. */
    const val QUIET_S = 10 * 60L

    /** The official app uploaded this recently before the sign-in, or a silence after it says nothing about it. */
    const val RECENT_S = 5 * 60L

    /** A watch ends after this, whatever LibreLinkUp said (offline the whole time: nothing to judge). */
    const val WINDOW_S = 30 * 60L

    enum class Verdict { Watching, Fine, Stopped }

    /**
     * [watch] against what LibreLinkUp shows now: its newest reading ([newestS], epoch s) and when it last answered
     * ([answeredS]). A reading after the sign-in: fine. Answers for [QUIET_S] after it and none: stopped. The official
     * app wasn't uploading right before (no reading in the [RECENT_S] before): a silence isn't the sign-in's doing, fine.
     */
    fun check(
        watch: LvWatch,
        newestS: Long?,
        answeredS: Long?,
        nowS: Long,
    ): Verdict {
        val before = watch.lastBeforeS
        return when {
            before == null || watch.atS - before > RECENT_S -> Verdict.Fine
            newestS != null && newestS > watch.atS -> Verdict.Fine
            answeredS != null && answeredS - watch.atS >= QUIET_S -> Verdict.Stopped
            nowS - watch.atS > WINDOW_S -> Verdict.Fine
            else -> Verdict.Watching
        }
    }
}
