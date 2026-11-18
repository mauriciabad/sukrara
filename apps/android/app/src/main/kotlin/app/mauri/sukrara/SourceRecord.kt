// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * The typed state file `source.json` (schema `source.person`, `source.kind`, `source.nightscoutUrl`,
 * `source.nightscoutTreatments`, `source.libreLinkUpPatient`): whose glucose this phone shows, where it comes from (by
 * their contract ids), the Nightscout source's site and treatments switch, and whom LibreLinkUp reads. Empty person / kind = setup has no answer yet; an id this version
 * doesn't know, or a pair no source offers, reads as no answer ([spec]). The site stays when the source changes, so
 * going back to Nightscout needs no new address (its token stays in the secure store).
 */
@Serializable
data class SourceRecord(
    val person: String? = null,
    val kind: String? = null,
    val nightscoutUrl: String = "",
    val nightscoutTreatments: Boolean = true,
    /** Whom the LibreLinkUp source reads (schema `source.libreLinkUpPatient`): kept when the source changes (the way back). */
    val libreLinkUpPatient: String = "",
) {
    val spec: SourceSpec? get() = SourceSpec.of(Person.of(person), SourceKind.of(kind))

    /** This record with [spec] as the answer, the source's own settings kept. */
    fun with(spec: SourceSpec?) = copy(person = spec?.person?.id, kind = spec?.kind?.id)

    companion object {
        fun of(spec: SourceSpec?) = SourceRecord().with(spec)
    }
}

/**
 * The source, kept in two files (`source.json` and `source-backup.json`) so one damaged file never stops monitoring: a
 * damaged file is quarantined and reported (home's data-problem banner) and reads as no answer, and then the other copy
 * answers. Without that, a damaged `source.json` would send the phone back to setup and silently stop the service and
 * its alerts. Writers use [update] (setup, Settings › Source); it writes the primary first, then the backup.
 */
class SourceStore(
    private val primary: StateStore<SourceRecord>,
    private val backup: StateStore<SourceRecord>,
) : StateStore<SourceRecord> {
    override val data: Flow<SourceRecord> = combine(primary.data, backup.data) { p, b -> if (p.spec != null) p else b }

    /** Not atomic across the two files: the source has one writer at a time (setup or Settings › Source). */
    override suspend fun update(transform: (SourceRecord) -> SourceRecord) {
        val next = transform(data.first())
        primary.update { next }
        backup.update { next }
    }

    /**
     * Brings the two copies back in line at start (`AppGraph.monitorReady`): an answer only one file has is copied to
     * the other (a write cut between the two files, a quarantined file). The primary wins when both have one: it is
     * written first. A clear cut between the two writes comes back from the backup: on purpose, keeping monitoring is
     * the safe failure (setup clears it again).
     */
    suspend fun heal() {
        val p = primary.data.first()
        val b = backup.data.first()
        when {
            p.spec != null && p != b -> backup.update { p }
            p.spec == null && b.spec != null -> primary.update { b }
        }
    }
}

/**
 * What a phone with no answer reads (`AppGraph.inferSource`: an install updated from before the question, a damaged
 * source file), in this order: following another Sukrara → their Sukrara; else what this phone read itself last
 * ([lastReadHere]: the sensor or Juggluco); else a sensor pairing → your sensor; else Juggluco installed after setup
 * began → Juggluco; else whose the history is ([historyOwner]). [readings] null = not readable yet (before the first
 * unlock): the pairing decides until the next start after the unlock. The readings come before the pairing: switching
 * to Juggluco kept the pairing, so a Juggluco reader with an old (or ended) pairing was taken for a sensor reader and
 * its live readings went unread; and a sensor reader whose pairing was forgotten was taken for a Juggluco reader only
 * because Juggluco is installed. The lambdas are asked only when their turn comes (the follow and the setup record
 * are loaded then).
 */
suspend fun inferredSource(
    following: suspend () -> Boolean,
    readings: ReadingsRepository?,
    paired: suspend () -> Boolean,
    jugglucoAfterSetup: suspend () -> Boolean,
): SourceSpec? {
    if (following()) return SourceSpec.TheirSukrara
    readings?.let { lastReadHere(it) }?.let { return it }
    return when {
        paired() -> SourceSpec.YourSensor
        jugglucoAfterSetup() -> SourceSpec(Person.Yours, SourceKind.PhoneApp)
        readings != null -> historyOwner(readings)
        else -> null
    }
}

/**
 * The source this phone read itself last: the newer of its sensor's readings and Juggluco's (a source change keeps the
 * history, so both can be there); null = it read neither (a new install, a follower, imports only).
 */
suspend fun lastReadHere(readings: ReadingsRepository): SourceSpec? {
    val sensor = readings.spanOf(SourceKind.Sensor.readings).last
    val app = readings.spanOf(SourceKind.PhoneApp.readings).last
    return when {
        app != null && (sensor == null || app > sensor) -> SourceSpec(Person.Yours, SourceKind.PhoneApp)
        sensor != null -> SourceSpec.YourSensor
        else -> null
    }
}

/**
 * Whose the history on this phone is when nothing else says what it reads ([inferredSource]: a wearer without a
 * pairing, a follow that ended, both from before the question, a damaged source file): a reading this phone took from a
 * sensor or imported makes it your own (your sensor); one relayed by another Sukrara someone else's (their Sukrara: a
 * person change deletes the other's readings, so the two never mix); readings only from an online source (a
 * Nightscout site, LibreLinkUp) say nothing, since either person's glucose comes that way, so nothing is guessed and
 * setup asks; none = a new install. Without it the first answer adopted that history for the other person (your own
 * readings shown as a child's, or a child's Nightscout readings taken as yours) and answering the truth later deleted it.
 */
suspend fun historyOwner(readings: ReadingsRepository): SourceSpec? {
    val all = readings.span().count
    val relayed = readings.spanOf(SourceId.Relayed).count
    val online = readings.spanOf(SourceId.Online).count
    return when {
        // a reading this phone took from a sensor or imported: only your own glucose comes that way
        all > relayed + online -> SourceSpec.YourSensor

        relayed > 0 -> SourceSpec.TheirSukrara

        // an online source's readings are either person's: ask
        else -> null
    }
}
