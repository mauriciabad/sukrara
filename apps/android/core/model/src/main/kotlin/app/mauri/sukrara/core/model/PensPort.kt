// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.Flow

/** What a pen read into the open Add treatment dialog shows (everything new of the read is logged at once). */
sealed interface PenFill {
    /**
     * The read logged [units] of [kind] (`rapid` / `long`) at [t] (epoch s): its newest injection, [primed] units
     * primed before it; [also] = the other injections the same read logged.
     */
    data class Logged(
        val kind: String,
        val units: Double,
        val primed: Double,
        val t: Long,
        val also: Int,
    ) : PenFill

    /** Nothing to fill: the read card's words (*Nothing new*, *Couldn't read the pen*, a question's title). */
    data class Said(
        val text: String,
        val error: Boolean,
    ) : PenFill
}

/** Whether an untested pen model's display check was done (it logs nothing until [Ok]). */
enum class PenCheck { None, Ok, Failed }

/** One insulin pen as Settings › Insulin pens shows it (times epoch s). */
data class PenInfo(
    val serial: String,
    /** The name it is shown with (the user's, else its model). */
    val name: String,
    val model: String,
    /** `rapid` or `long`. */
    val kind: String,
    val since: Long,
    val first: Long,
    val lastScan: Long,
    /** Doses before Sukrara at its first read (left out). */
    val older: Int,
    /** The first read found this pen's entries in the log: its newest logged dose (else null). */
    val returningSince: Long?,
    /** A model nobody has confirmed yet: nothing is logged before its display check. */
    val untested: Boolean,
    val check: PenCheck,
    /** The pen's page was opened once (the list says *Review its settings* until then). */
    val opened: Boolean,
    /** Untested and unchecked: the newest dose of the read waiting for the check (units, epoch s, read at), or null. */
    val held: PenHeldDose?,
    /** The pen's last big read (an injection older than 72 h), while it can be reviewed; else null. */
    val review: PenReview? = null,
)

/**
 * A read that logged an injection older than 72 h (*N injections logged · Since 2 Oct · Review*): when it was read
 * ([at]), its oldest injection ([since]), how many entries it logged and replaced, and its doses' [keys] (the sessions
 * *Review* lists, also after *Undo this read* made them *Not logged*). Times epoch s.
 */
data class PenReview(
    val at: Long,
    val since: Long,
    val entries: Int,
    val merged: Int,
    val keys: Set<String>,
)

/** How *Undo this read* went: undone, refused because its entries changed since, or nothing (left) to undo. */
enum class PenReadUndo { Done, Changed, Gone }

/** The newest dose of an untested pen's held read, as the pen's display shows it. */
data class PenHeldDose(
    val units: Double,
    val t: Long,
    val readAt: Long,
)

/** One dose of a session or entry: [key] identifies it to the pen runtime. */
data class PenDoseInfo(
    val key: String,
    val t: Long,
    val units: Double,
    val primed: Boolean,
    val incomplete: Boolean,
)

/** What happened to one session of a pen's doses (*Doses read*). */
enum class PenSessionStatus { Logged, Merged, Corrected, Priming, Waiting, Review, NotLogged, Before, Ignored }

/** One session of *Doses read*, newest first. */
data class PenSessionInfo(
    val serial: String,
    val t: Long,
    /** Injected units (the entry's amount when it is logged). */
    val units: Double,
    val primed: Double,
    val status: PenSessionStatus,
    /** The treatment entry it is (logged, merged, corrected). */
    val entry: String?,
    /** The question it waits on, and whether that asks *Same injection?* (else *Priming or injected?*). */
    val question: String?,
    val sameQuestion: Boolean,
    /** A *Same injection?* question's typed entries (only one can be answered from the page). */
    val candidates: Int = 0,
    /** The hand-typed amount a merge replaced. */
    val mergedFrom: Double?,
    val incomplete: Boolean,
    val doses: List<PenDoseInfo>,
)

/** A pen entry of the treatment log with its doses (the entry sheet). */
data class PenEntryInfo(
    val id: String,
    val serial: String,
    /** `rapid` or `long`. */
    val kind: String,
    val units: Double,
    val primed: Double,
    val t: Long,
    val note: String,
    val doses: List<PenDoseInfo>,
    /** The hand-typed entry (units, epoch s) the pen's read replaced. */
    val replaced: Pair<Double, Long>?,
)

/** Where a hand-typed entry came from (the *Same injection?* rows say it). */
enum class PenTypedBy { Phone, Mac, Follower }

/** A hand-typed entry a pen injection may be (*Same injection?*); epoch s. */
data class PenCandidate(
    val id: String,
    val units: Double,
    val t: Long,
    val by: PenTypedBy,
)

/** An open pen question as home's question card and *Doses read* show it (times epoch s). */
data class PenQuestionInfo(
    val id: String,
    val serial: String,
    /** *Same injection?* (else *Priming or injected?*). */
    val same: Boolean,
    /** Its doses' keys as shown: an answer carries them, so a question that grew since isn't answered. */
    val keys: List<String>,
    val doses: List<PenDoseInfo>,
    /**
     * What answering logs: *Priming or injected?*'s suggestion (the last dose injected; 0 = nothing to suggest), or
     * the pen's injection a *Same injection?* asks about, at [t].
     */
    val units: Double,
    val t: Long,
    /** *Same injection?*: the live typed entries it may be, nearest first. */
    val candidates: List<PenCandidate> = emptyList(),
    /** When it was asked (oldest first on home). */
    val asked: Long = 0,
)

/** An answer to a pen question (home's card and sheet, *Doses read*, the read card). */
sealed interface PenAnswer {
    /** *Log 1.5u*: the suggestion (the last dose injected, the ones before it priming). */
    data object Suggested : PenAnswer

    /** *All priming*: nothing of it is logged. */
    data object AllPriming : PenAnswer

    /** *Same injection?*: the typed entry it is ([entry]), or null: different injections (*None of these*). */
    data class Same(
        val entry: String?,
    ) : PenAnswer
}

/** A hand-typed or pen entry near a pen injection (units, epoch s). */
data class PenNearEntry(
    val id: String,
    val units: Double,
    val t: Long,
)

/** The state Android puts the pen taps in (the pens page's rows, home's banner). */
data class PenTaps(
    /** Android blocks this app's tag intents (Android 15+): no tap reaches it until the user allows it. */
    val blocked: Boolean,
    val nfcOff: Boolean,
    /** *Notification after each read* is on but its channel or the app's notifications are off. */
    val notifyBlocked: Boolean,
    /** Juggluco (it reads NovoPens too) is installed: both apps react to the same tap, a dose could be logged twice. */
    val juggluco: Boolean = false,
)

/**
 * Insulin pens as screens use them (the wearer's phone with NFC; `AppGraph` implements it over `:pen`). Every action
 * runs under the pen runtime's lock and returns false (nothing changed) when this phone may not write or the data
 * moved on since the screen read it.
 */
interface PensPort {
    val pens: Flow<List<PenInfo>>

    /** The open questions, oldest first (home's question card); follows the ledger and the log. */
    val questions: Flow<List<PenQuestionInfo>>

    /** Answers [question] as it was shown (refused, false, when it changed or was answered since). */
    suspend fun answer(
        question: PenQuestionInfo,
        answer: PenAnswer,
    ): Boolean

    /** The pen's sessions, newest first; follows the ledger and the log. */
    fun sessions(serial: String): Flow<List<PenSessionInfo>>

    suspend fun entry(id: String): PenEntryInfo?

    suspend fun rename(
        serial: String,
        name: String,
    )

    suspend fun setKind(
        serial: String,
        kind: String,
    )

    suspend fun opened(serial: String)

    /** *Forget this pen*: its record goes, its entries stay. */
    suspend fun forget(serial: String)

    /** *Log this injection* / *Log it*: these doses, each priming (true) or injected. */
    suspend fun logDoses(
        serial: String,
        doses: Map<String, Boolean>,
    ): Boolean

    /** The live pen entry whose session doses at [t] belong to (*Add to the 13:10 entry*). */
    suspend fun sessionEntry(
        serial: String,
        t: Long,
    ): PenNearEntry?

    suspend fun joinEntry(
        serial: String,
        doses: Map<String, Boolean>,
        id: String,
    ): Boolean

    /** The hand-typed entry logging [units] at [t] would replace (*Matches your 8u entry at 13:10*). */
    suspend fun match(
        serial: String,
        t: Long,
        units: Double,
    ): PenNearEntry?

    /**
     * Each dose of [shown] (the entry as the sheet showed it) set to priming or injected (all priming removes it).
     * Refused (null) when the entry changed since; else the entry as it was.
     */
    suspend fun correct(
        shown: PenEntryInfo,
        primed: List<Boolean>,
    ): PenEntryInfo?

    /** The snackbar's *Undo*: [was] back as it was, only while nothing changed the entry since. */
    suspend fun restore(was: PenEntryInfo): Boolean

    /**
     * *Undo this read* (Review of the pen's last big read): its entries back as they were, its doses *Not logged*.
     * [PenReadUndo.Changed] when any of its entries changed since (nothing is touched then).
     */
    suspend fun undoRead(serial: String): PenReadUndo

    /** The display check of an untested pen: *It matches* (the held read is applied) / *It doesn't* / *Check again*. */
    suspend fun checkPassed(
        serial: String,
        readAt: Long,
    ): Boolean

    suspend fun checkFailed(serial: String)

    suspend fun checkAgain(serial: String)

    /** Android's state for the taps, read again whenever a screen comes back. */
    fun taps(): PenTaps

    /** Android's screens that fix [taps]: tag intents, NFC, the *Pen reads* channel. */
    fun openTagSettings(): Boolean

    fun openNfcSettings()

    fun openNotifySettings()

    /** Juggluco's App info (where the person can turn it off or uninstall it). */
    fun openJugglucoInfo()
}

/** The treatment dialog's *Already logged by your pen?*: a live pen entry of [kind] within the match window of [at]. */
fun interface PenDuplicates {
    suspend fun near(
        kind: String,
        at: Long,
    ): PenNearEntry?
}
