// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * One person, one link (contracts/sources/link.json, docs/44 Appendix G, the owner 2026-11-18): a phone either follows
 * a Sukrara (the link: a [SourceSpec.stream]) or reads one other source, never both. These are the contract's rules,
 * pure (`LinkContractTest`); setup, Settings › Source and *Stop following* ask what they say.
 */
object Link {
    /** Whether this phone follows a Sukrara: [None], [Active] (waiting for the wearer or allowed) or [Ended]. */
    enum class Linked { None, Active, Ended }

    /** What picking a source asks first (link.json `pick.asks`). */
    enum class Ask {
        /** *Keep following {name}?*: says what the link already brings; *Keep following* (the default) or stop. */
        KeepFollowing,

        /** Straight to the leave questions: nothing is left to keep following. */
        Leave,
    }

    /** What picking [picked] asks while [linked]; null = nothing (the link again, or no link). */
    fun pick(
        linked: Linked,
        picked: SourceKind,
    ): Ask? =
        when {
            picked == SourceKind.Sukrara || linked == Linked.None -> null
            linked == Linked.Active -> Ask.KeepFollowing
            else -> Ask.Leave
        }

    /** The wearer's mirrored settings this phone holds (link.json `leave.theirs`). */
    enum class Theirs { None, Defaults, Differ }

    /** One question of leaving (link.json `leave.questions`). */
    enum class Question {
        /** *Keep {name}'s settings* / *Sukrara's defaults*. */
        Settings,

        /** *Keep it* / *Delete it*. */
        History,

        /** A change of whose glucose: *Delete the earlier history?* (with *Export CSV first*). */
        DeleteHistory,
    }

    /** The settings a phone keeps when no settings question is asked (link.json `leave.result`). */
    enum class Kept {
        /** Its own, as they are. */
        Own,

        /** The mirrored rows at the schema defaults. */
        Defaults,

        /** The wearer's mirrored rows copied as its own. */
        Theirs,
    }

    /** What leaving asks ([questions], in order) and, when the settings aren't asked, what they become ([result]). */
    data class Leave(
        val questions: List<Question>,
        val result: Kept?,
    )

    /**
     * What leaving asks: the settings only while *Their settings* was in use and theirs aren't the defaults (both
     * answers would give the same settings otherwise); the history only when the link brought some, as its deletion
     * when whose glucose changes with the leave ([personChange], Android).
     */
    fun leave(
        wearerSettings: Boolean,
        theirs: Theirs,
        history: Boolean,
        personChange: Boolean,
    ): Leave {
        val askSettings = wearerSettings && theirs == Theirs.Differ
        val questions =
            buildList {
                if (askSettings) add(Question.Settings)
                if (history) add(if (personChange) Question.DeleteHistory else Question.History)
            }
        val result =
            when {
                askSettings -> null
                wearerSettings -> Kept.Defaults
                else -> Kept.Own
            }
        return Leave(questions, result)
    }

    /** The preselected answers (link.json `defaults`): nothing the person sees shifts, nothing is lost. */
    val DEFAULT_SETTINGS: Kept = Kept.Theirs

    /** Keep the history (link.json `defaults.history`). */
    const val DEFAULT_KEEP_HISTORY: Boolean = true
}
