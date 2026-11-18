// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.storage.StateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * The typed state file `source.json` (schema `source.person`, `source.kind`): whose glucose this phone shows and where
 * it comes from, by their contract ids. Empty = setup has no answer yet; an id this version doesn't know, or a pair no
 * source offers, reads as no answer ([spec]).
 */
@Serializable
data class SourceRecord(
    val person: String? = null,
    val kind: String? = null,
) {
    val spec: SourceSpec? get() = SourceSpec.of(Person.of(person), SourceKind.of(kind))

    companion object {
        fun of(spec: SourceSpec?) = SourceRecord(spec?.person?.id, spec?.kind?.id)
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
