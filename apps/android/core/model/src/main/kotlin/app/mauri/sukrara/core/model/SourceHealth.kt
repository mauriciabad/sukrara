// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Duration

/** How the last call to an online source ended (contracts/sources/health.json `outcome`). */
enum class CallOutcome {
    /** The site answered. */
    Ok,

    /** No answer: DNS, TLS, a timeout, a refused connection. */
    Network,

    /** 5xx, a redirect, or an answer that isn't the site's. */
    Server,

    /** 404: no Nightscout at that address. */
    NotFound,

    /** 401 / 403: the token or API secret. */
    Refused,

    /** 429: the site asked to wait. */
    RateLimited,

    /** LibreLinkUp asks the person to accept new terms or a new privacy policy (login status 4). */
    Terms,

    /** LibreLinkUp: the account follows no one, or no longer the person picked. */
    NotShared,

    /** LibreLinkUp refuses this client (a version Sukrara can't send, an endpoint gone): Abbott changed the API. */
    Retired,

    /** LibreLinkUp's region answers HTTP 911: LibreView is down for maintenance (docs/45). */
    Maintenance,
}

/**
 * How an online source is doing, as the person sees it on home, Settings › Source and setup's Connect step
 * (contracts/sources/health.json, docs/44 Appendix C "States and exits"): one state from the last call, whether the
 * phone has a network, and the newest reading's age. The last three are LibreLinkUp's.
 */
enum class OnlineHealth {
    /** The first call is on its way. */
    Connecting,

    /** The newest reading is at most [LATE_AFTER] old. */
    Live,

    /** Older, up to [SILENT_AFTER]: readings come in gaps (the card shows the age). */
    Late,

    /** The site answers but nothing new reaches it (or it has no glucose at all): the uploader is the place to look. */
    NoNewReadings,

    /** No network on this phone: it catches up when it's back. */
    Offline,

    /** A network, but no answer from the site: down, or the address is wrong. */
    Unreachable,

    /** The site refused the token or API secret: enter it again. */
    SignedOut,

    /** The site asked to wait; Sukrara tries again by itself. */
    RateLimited,

    /** LibreLinkUp needs the person: new terms to accept in its app. */
    ActionNeeded,

    /** LibreLinkUp: no one shares with the account (any more): invite it from the Libre app, or pick again. */
    NotShared,

    /** LibreLinkUp stopped answering Sukrara (Abbott changed it): said plainly; an update of Sukrara may fix it. */
    Stopped,

    /** The source answers and its sensor warms up: nothing new until the warm-up's end (the newest is the old sensor's). */
    WarmingUp,

    /**
     * LibreView is down for maintenance (LibreLinkUp's region answers 911): nothing comes until it ends. An expected
     * wait, not an error: what is happening and, when Abbott's status page says it, until when.
     */
    Maintenance,
    ;

    /** Readings may still come by themselves: nothing for the person to do. */
    val fine: Boolean get() = this == Connecting || this == Live || this == Late || this == WarmingUp

    /** Nothing to fix (fine, or LibreView's maintenance: a wait that ends by itself): never in an error's colour. */
    val nothingToFix: Boolean get() = fine || this == Maintenance

    /** The problem [SourceStatus.Interrupted] carries, null while readings come. */
    val problem: SourceProblem?
        get() =
            when (this) {
                Connecting, Live, Late, WarmingUp -> null
                NoNewReadings -> SourceProblem.NoNewReadings
                Offline -> SourceProblem.Offline
                Unreachable -> SourceProblem.Unreachable
                SignedOut -> SourceProblem.SignedOut
                RateLimited -> SourceProblem.RateLimited
                ActionNeeded -> SourceProblem.ActionNeeded
                NotShared -> SourceProblem.NotShared
                Stopped -> SourceProblem.Stopped
                Maintenance -> SourceProblem.Maintenance
            }

    companion object {
        val LATE_AFTER: Duration = Duration.ofSeconds(420)
        val SILENT_AFTER: Duration = Duration.ofSeconds(900)

        /**
         * The state for the last call's [outcome] (null = nothing asked yet), whether the phone is [online], and the
         * newest reading's age ([newestAge], null = the site has none), and whether the source's sensor is [warmingUp]
         * (an answer then says [WarmingUp], whatever the age). A refusal or a rate limit says so whatever the network; a
         * failure is [Offline] only without a network. LibreView's maintenance says so whatever the network and the
         * warm-up.
         */
        fun of(
            outcome: CallOutcome?,
            online: Boolean,
            newestAge: Duration?,
            warmingUp: Boolean = false,
        ): OnlineHealth =
            when (outcome) {
                null -> {
                    if (online) Connecting else Offline
                }

                CallOutcome.Refused -> {
                    SignedOut
                }

                CallOutcome.RateLimited -> {
                    RateLimited
                }

                CallOutcome.Terms -> {
                    ActionNeeded
                }

                CallOutcome.NotShared -> {
                    NotShared
                }

                CallOutcome.Retired -> {
                    Stopped
                }

                CallOutcome.Maintenance -> {
                    Maintenance
                }

                CallOutcome.Network, CallOutcome.Server, CallOutcome.NotFound -> {
                    if (online) Unreachable else Offline
                }

                CallOutcome.Ok -> {
                    when {
                        warmingUp -> WarmingUp
                        newestAge == null || newestAge > SILENT_AFTER -> NoNewReadings
                        newestAge > LATE_AFTER -> Late
                        else -> Live
                    }
                }
            }
    }
}

/**
 * Why an online source brings nothing now ([SourceStatus.Interrupted.problem]): home and Settings › Source say it.
 * [Maintenance] is an expected wait, nothing to fix: every surface says what is happening and until when
 * ([SourceStatus.Interrupted.until]), never in an error's colour.
 */
enum class SourceProblem { NoNewReadings, Offline, Unreachable, SignedOut, RateLimited, ActionNeeded, NotShared, Stopped, Maintenance }
