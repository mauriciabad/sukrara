// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.UploadHold
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.integrations.upload.SentRanges
import app.mauri.sukrara.integrations.upload.Span
import kotlinx.serialization.Serializable

/** The version of an entry LibreView has ([v] = its `modifiedAt` ms) and when the entry happened ([t], epoch s). */
@Serializable
data class LvSent(
    val v: Long,
    val t: Long,
    /** The kind LibreView has it as (its record number and list depend on it). */
    val k: String = "",
)

/** What one sensor already sent: life counts of its 5-minute history, the last current reading, the one-off records. */
@Serializable
data class LvSensorState(
    val serial: String,
    val sent: List<Span> = emptyList(),
    val lastCurrent: Int = -1,
    val registered: Boolean = false,
    val startSent: Boolean = false,
    /**
     * Registrations LibreView didn't accept in a row: its glucose waits and the registration is tried again at
     * [registerAfterMs] (backoff), never given up on (a refusal kept until the next sign-in held a sensor's glucose back
     * for good while treatments still went up).
     */
    val registerFailures: Int = 0,
    val registerAfterMs: Long = 0,
)

/**
 * The LibreView upload's own state (typed store `libreview-upload`; no secret: the token is in the SecureStore).
 * [ledger] = what LibreView has of each entry (a year back), so an edit or a delete retracts the version it got.
 */
@Serializable
data class LvState(
    /** This install's LibreView device id (a random UUID, made once). */
    val deviceId: String = "",
    /** The account the token and the ledger belong to (its email; the token itself is only in the SecureStore). */
    val email: String = "",
    /** The signed-in account's LibreView AccountId: decides "the same account" at the next sign-in. */
    val accountId: String = "",
    /** Sensor glucose goes up from this moment (epoch s; 0 = glucose isn't uploading). */
    val glucoseFrom: Long = 0,
    /** New entries go up when changed after this moment (epoch s; 0 = entries aren't uploading). */
    val entriesFrom: Long = 0,
    /** LibreView refused the last upload ([lastError]): nothing goes up until an entry or a setting changes. */
    val refused: Boolean = false,
    val ledger: Map<String, LvSent> = emptyMap(),
    val sensors: List<LvSensorState> = emptyList(),
    /** Another device uploads for the account: nothing goes up until the person chooses *Take over*. */
    val otherDevice: Boolean = false,
    val failures: Int = 0,
    val nextAttemptMs: Long = 0,
    /** The last upload LibreView accepted (treatments, glucose or both). */
    val lastOkMs: Long = 0,
    val lastCount: Int = 0,
    val lastError: Trouble? = null,
    /** The newest 5-minute glucose LibreView accepted (epoch s; what its reports are built from; 0 = none yet). */
    val glucoseUpToS: Long = 0,
    /** Why glucose isn't going up now (a sensor registration LibreView didn't accept); only glucose going up clears it. */
    val glucoseError: Trouble? = null,
)

/**
 * The Libre 3 history LibreView hasn't got yet (typed store `libreview-unsent`), so a restart or a process death
 * doesn't lose it: at most [LibreViewQueue.HISTORY_MIN] of one sensor, only what would go up ([LibreViewQueue.unsent]).
 */
@Serializable
data class LvUnsent(
    val serial: String = "",
    val history: List<LvGlucose> = emptyList(),
)

/** Where an entry stands with LibreView ([LibreViewQueue.mark]). */
enum class LvMark { Uploaded, Updating, Pending }

/** Pure planning of one LibreView upload: `stores − state`. */
object LibreViewQueue {
    /** Entries further back than this are never sent for the first time. */
    const val WINDOW_S = 30 * 24 * 3600L

    /** What LibreView got is remembered this long (by when the entry happened), so a late edit still retracts it. */
    private const val LEDGER_S = 365 * 24 * 3600L

    /** An entry another device made waits this long after the phone got it (that device may still change it). */
    const val GRACE_S = 120L

    const val MAX_ENTRIES = 100
    const val MAX_HISTORY = 288

    /** History of 3 days per sensor is kept (memory) and remembered as sent (state). */
    const val HISTORY_MIN = 3 * 24 * 60

    /** A current reading older than this isn't sent as current (it arrives as history). */
    const val CURRENT_MAX_AGE_S = 5 * 60L

    /**
     * The entries that LibreView lacks, oldest first, at most [MAX_ENTRIES]: new ones changed since the upload was
     * switched on ([from], epoch s) and from the last 30 days, plus every change to one LibreView already has. An
     * entry [hold] keeps back (a pen session still open) waits.
     */
    fun entries(
        records: List<TreatmentRecord>,
        ledger: Map<String, LvSent>,
        from: Long,
        nowS: Long,
        hold: UploadHold = UploadHold.NONE,
    ): List<LvEntry> =
        records
            .asSequence()
            .filter { (hold.until(it) ?: 0L) <= nowS }
            .map { it to it.treatment }
            .filter { (_, t) ->
                // what LibreView has is always corrected (edited, moved or deleted), whenever it happened
                // (a retracted one, v = 0, has nothing live there: it goes up again only as a new entry would)
                (ledger[t.id]?.v ?: 0L) != 0L ||
                    (t.at.epochSecond >= nowS - WINDOW_S && t.modifiedAt.epochSecond >= from)
            }.filter { (r, t) ->
                // another device's entry: give it a moment to settle
                t.origin == TreatmentOrigin.Phone ||
                    nowS - (r.receivedAt ?: t.modifiedAt).epochSecond >= GRACE_S
            }.map { (_, t) -> LvEntry.of(t, ledger[t.id]?.v ?: 0L) }
            // deleted before it ever went up: nothing to send
            .filter { it.lv != it.u && !(it.del && it.lv == 0L) }
            .sortedWith(compareBy({ it.t }, { it.id }))
            .take(MAX_ENTRIES)
            .flatMap { e ->
                val had = ledger[e.id]
                if (had != null && had.v != 0L && had.k.isNotEmpty() && had.k != e.k) {
                    // the kind changed: retract it under the kind LibreView has, then send it as new (unless deleted)
                    listOfNotNull(e.copy(k = had.k, del = true), e.copy(lv = 0L).takeIf { !e.del })
                } else {
                    listOf(e)
                }
            }.toList()

    /**
     * Where entry [t] stands with LibreView, by the rules of [entries]: the version it has, an older version (being
     * corrected), or a new entry it will get ([from] = entries go up when changed after it, epoch s; 0 = entries are
     * off). Null = LibreView has nothing of it and never will (changed before [from], or older than [WINDOW_S]).
     */
    fun mark(
        t: Treatment,
        ledger: Map<String, LvSent>,
        from: Long,
        nowS: Long,
    ): LvMark? {
        if (t.deleted) return null
        val has = ledger[t.id]?.v?.takeIf { it != 0L }
        return when {
            has == t.modifiedAt.toEpochMilli() -> LvMark.Uploaded
            has != null -> LvMark.Updating
            from > 0 && t.at.epochSecond >= nowS - WINDOW_S && t.modifiedAt.epochSecond >= from -> LvMark.Pending
            else -> null
        }
    }

    /** The ledger after LibreView accepted [sent], trimmed to the window. */
    fun sent(
        ledger: Map<String, LvSent>,
        sent: List<LvEntry>,
        nowS: Long,
    ): Map<String, LvSent> =
        (
            ledger +
                sent.groupBy { it.id }.mapValues { (_, es) ->
                    // a kind change sends a retraction (old kind) and the new version: remember the new one
                    val e = es.firstOrNull { !it.del } ?: es.first()
                    // a retracted entry has no live record: an Undo goes up as new, never retracting a record that
                    // never existed (v = 0 = nothing live; it stays in the ledger so it is still followed)
                    LvSent(if (e.del) 0L else e.u, e.t, e.k)
                }
        ).filterValues { it.t >= nowS - LEDGER_S }

    /**
     * The sensor part of the next upload, or null. Only [serial]'s own readings (the source tags each batch with its
     * sensor), only from [from], only when the sensor's start is known ([start] for this serial).
     */
    fun sensor(
        serial: String,
        start: Long?,
        current: LvGlucose?,
        history: Collection<LvGlucose>,
        state: LvSensorState?,
        from: Long,
        nowS: Long,
    ): LvSensor? {
        if (start == null || from <= 0) return null
        val sent = SentRanges.expand(state?.sent.orEmpty())
        val cur =
            current?.takeIf {
                it.lifeCount > (state?.lastCurrent ?: -1) && it.epochSec >= from && nowS - it.epochSec < CURRENT_MAX_AGE_S
            }
        val hist =
            history
                .filter { it.epochSec >= from && it.lifeCount.toLong() !in sent }
                .sortedBy { it.lifeCount }
                .take(MAX_HISTORY)
        if (cur == null && hist.isEmpty()) return null
        return LvSensor(serial, start, cur, hist, sendStart = state?.startSent != true)
    }

    /** What of [serial]'s [history] LibreView lacks and would get (from [from]; nothing while glucose is off). */
    fun unsent(
        serial: String?,
        history: Collection<LvGlucose>,
        state: LvState,
    ): LvUnsent {
        if (serial == null || state.glucoseFrom <= 0) return LvUnsent()
        val sent =
            SentRanges.expand(
                state.sensors
                    .firstOrNull { it.serial == serial }
                    ?.sent
                    .orEmpty(),
            )
        val keep = history.filter { it.epochSec >= state.glucoseFrom && it.lifeCount.toLong() !in sent }.sortedBy { it.lifeCount }
        return if (keep.isEmpty()) LvUnsent() else LvUnsent(serial, keep.takeLast(HISTORY_MIN))
    }

    /** The sensors' state after LibreView accepted [s]: the last two sensors, 3 days of life counts each. */
    fun sentSensor(
        sensors: List<LvSensorState>,
        s: LvSensor,
    ): List<LvSensorState> {
        val old = sensors.firstOrNull { it.serial == s.serial } ?: LvSensorState(s.serial)
        val all = SentRanges.expand(old.sent).apply { addAll(s.history.map { it.lifeCount.toLong() }) }
        val newest = maxOf(all.lastOrNull() ?: 0L, s.current?.lifeCount?.toLong() ?: 0L)
        val kept = all.tailSet(newest - HISTORY_MIN)
        val updated =
            old.copy(
                sent = SentRanges.ranges(kept),
                lastCurrent = maxOf(old.lastCurrent, s.current?.lifeCount ?: -1),
                startSent = old.startSent || s.sendStart,
            )
        return (listOf(updated) + sensors.filter { it.serial != s.serial }).take(2)
    }

    /** [serial]'s registration failed again: tried again at [retryAtMs] of the new count (the caller's backoff). */
    fun registrationFailed(
        sensors: List<LvSensorState>,
        serial: String,
        retryAtMs: (failures: Int) -> Long,
    ): List<LvSensorState> {
        val old = sensors.firstOrNull { it.serial == serial } ?: LvSensorState(serial)
        val n = old.registerFailures + 1
        return (listOf(old.copy(registerFailures = n, registerAfterMs = retryAtMs(n))) + sensors.filter { it.serial != serial }).take(2)
    }

    /**
     * The phone has glucose this much newer than LibreView's newest: a stall worth telling the person about (the
     * 5-minute history trails the live reading by ~16 min, plus one upload interval, normally).
     */
    const val BEHIND_S = 2 * 3600L

    /**
     * Since when LibreView lacks the glucose the phone has (epoch s: the newest it got [upToS], or the moment glucose
     * was switched on [from]), once the phone's newest reading [latestS] is more than [BEHIND_S] (or two upload
     * intervals, [intervalMin]) past it; else null. Whatever the reason: a sensor LibreView doesn't register, readings
     * that can't go up (Juggluco, another family), an upload that keeps failing. No readings (no sensor, warm-up) are
     * never behind; glucose off ([from] 0) neither.
     */
    fun behind(
        latestS: Long?,
        upToS: Long,
        from: Long,
        intervalMin: Double,
    ): Long? {
        if (from <= 0 || latestS == null) return null
        val since = maxOf(upToS, from)
        val limit = maxOf(BEHIND_S, (2 * intervalMin * 60).toLong())
        return since.takeIf { latestS - it > limit }
    }

    fun registered(
        sensors: List<LvSensorState>,
        serial: String,
    ): List<LvSensorState> {
        val old = sensors.firstOrNull { it.serial == serial } ?: LvSensorState(serial)
        return (listOf(old.copy(registered = true, registerFailures = 0, registerAfterMs = 0)) + sensors.filter { it.serial != serial })
            .take(2)
    }
}
