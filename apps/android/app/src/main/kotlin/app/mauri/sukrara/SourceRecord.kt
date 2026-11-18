// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.settings.NumberSpec
import app.mauri.sukrara.settings.SettingsSpec
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
    /**
     * Each source's wear length in minutes (schema `source.libreLinkUpWear`, `source.nightscoutWear`,
     * `source.jugglucoWear`; 0 = the format's default), for sources that don't say it: remembered across sensors and
     * source changes.
     */
    val libreLinkUpWear: Int = 0,
    val nightscoutWear: Int = 0,
    val jugglucoWear: Int = 0,
    /**
     * When your sensor started, set on the Sensor page (schema `sensor.manualStart`, epoch s; 0 = none): only for the
     * source and person it was set for, so a source or person change clears it ([with]).
     */
    val manualStart: Long = 0,
) {
    val spec: SourceSpec? get() = SourceSpec.of(Person.of(person), SourceKind.of(kind))

    /**
     * This record with [spec] as the answer, the source's own settings kept; another source or person clears the manual
     * start (it was that source's sensor: an old one coming back later would say *Sensor ended* and keep no data quiet).
     */
    fun with(spec: SourceSpec?): SourceRecord {
        val same = spec?.person?.id == person && spec?.kind?.id == kind
        return copy(person = spec?.person?.id, kind = spec?.kind?.id, manualStart = if (same) manualStart else 0)
    }

    /** The stored wear length of [kind] (minutes, 0 = the default; a source that says it has none). */
    fun wearOf(kind: SourceKind): Int =
        when (kind.wear) {
            SettingsSpec.Keys.LIBRE_LINK_UP_WEAR -> libreLinkUpWear
            SettingsSpec.Keys.NIGHTSCOUT_WEAR -> nightscoutWear
            SettingsSpec.Keys.JUGGLUCO_WEAR -> jugglucoWear
            else -> 0
        }

    /** This record with [kind]'s wear length set to [minutes]. */
    fun withWear(
        kind: SourceKind,
        minutes: Int,
    ): SourceRecord =
        when (kind.wear) {
            SettingsSpec.Keys.LIBRE_LINK_UP_WEAR -> copy(libreLinkUpWear = minutes)
            SettingsSpec.Keys.NIGHTSCOUT_WEAR -> copy(nightscoutWear = minutes)
            SettingsSpec.Keys.JUGGLUCO_WEAR -> copy(jugglucoWear = minutes)
            else -> this
        }

    /**
     * What [kind]'s sensor life needs from the person while [running] is THE source (the answer, or what an unanswered
     * phone reads): whose glucose, the valid wear length, the manual start (only [kind]'s own).
     */
    fun lifeOf(
        kind: SourceKind,
        running: SourceSpec?,
    ): LifeSettings {
        val wear = wearSpec(kind)?.let { f -> f.coerce(wearOf(kind).takeIf { it > 0 }?.toDouble() ?: f.default).toInt() } ?: 0
        // only the source it was set for (an unanswered record has no kind: what the phone reads then)
        val own = running?.kind == kind && (this.kind == null || this.kind == kind.id)
        return LifeSettings(running?.person, wear, manualStart.takeIf { own } ?: 0)
    }

    companion object {
        fun of(spec: SourceSpec?) = SourceRecord().with(spec)

        /** [kind]'s wear length format (schema `wear` / `wearLibre`); null = the source says the length. */
        fun wearSpec(kind: SourceKind): NumberSpec? =
            kind.wear
                ?.let { key -> SettingsSpec.SETTINGS.first { it.android == "state:$key" } }
                ?.number
                ?.let(SettingsSpec.NUMBERS::get)
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
