// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.feature.sharing.HistoryLoss
import app.mauri.sukrara.feature.sharing.SourcePort
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.monitor.alerts.AlertStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Setup's [SourcePort] on the app's `source` store (the hub and the alert runtime read the same one). A change of whose
 * glucose never mixes two people: every reading and treatment (the previous person's) is deleted. A source change for
 * the same person keeps them as one continuous history: the fence goes at the last reading kept ([setFence],
 * contracts/sources/link.json `fence`), so the new source's backfill fills gaps behind it and never replaces what the
 * previous source read (docs/44 Appendix G finding 5); after it, the rank rule keeps one reading per measurement. The person is
 * compared with what the phone runs on ([running]: an install updated from before the question has history of that
 * person). Setup asks [loss] first and confirms with the person before deleting anything (#125). The other person's
 * Nightscout site and LibreLinkUp pick go too ([SourceRecord.forAnotherPerson]).
 */
class AppSources(
    private val store: StateStore<SourceRecord>,
    override val running: Flow<SourceSpec?>,
    private val readings: ReadingsRepository,
    private val treatments: TreatmentsRepository,
    /**
     * After a change of whose glucose, with the record before it: the sources let go of what was the other person's
     * (the site to confirm, LibreLinkUp's name).
     */
    private val anotherPerson: suspend (before: SourceRecord, spec: SourceSpec) -> Unit = { _, _ -> },
    /** The kept history's fence (`history.json`): the last kept reading, null = none (the history went). */
    private val setFence: suspend (Instant?) -> Unit = {},
) : SourcePort {
    override val stored: Flow<SourceSpec?> = store.data.map { it.spec }

    /**
     * Whose the history is: what the phone runs on, else the person a left link kept it for (*Stop following* alone
     * keeps whose glucose in the record, with no source).
     */
    private suspend fun personBefore(): Person? = running.first()?.person ?: Person.of(store.data.first().person)

    override suspend fun loss(spec: SourceSpec): HistoryLoss? {
        val before = personBefore() ?: return null
        if (before == spec.person) return null
        return HistoryLoss.of(readings.span(), treatments.span())
    }

    override suspend fun set(spec: SourceSpec) {
        val before = running.first()
        val person = personBefore()
        val another = person != null && person != spec.person
        val was = store.data.first()
        store.update { r -> r.with(spec).let { if (another) it.forAnotherPerson() else it } }
        if (another) {
            readings.deleteAll()
            treatments.deleteAll()
            setFence(null)
            anotherPerson(was, spec)
        } else if (before != null && before.kind != spec.kind) {
            setFence(readings.span().last)
        }
    }
}

/** Setup's alert step on the alert runtime's own settings store: confirmed values are what it checks. */
class AppAlertSettings(private val store: AlertStateStore) : AlertSettingsAccess {
    override val settings: Flow<AlertSettings> = store.settings

    override suspend fun save(settings: AlertSettings) = store.setSettings(settings)
}
