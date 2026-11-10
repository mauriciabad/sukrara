// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** A role change never mixes two people: the previous role's readings and treatments go. */
class AppRolesTest {
    private val t0 = Instant.parse("2026-01-15T12:00:00Z")
    private val store = InMemoryStateStore(RoleRecord())
    private val readings = InMemoryReadingsRepository()
    private val treatments = InMemoryTreatmentsRepository()
    private val roles = AppRoles(RoleStore(store, InMemoryStateStore(RoleRecord())), readings, treatments)

    private fun reading(
        min: Long,
        source: SourceId,
    ) = Reading(t0.plusSeconds(min * 60), 100 + min.toInt(), null, source)

    private suspend fun addTreatment() =
        treatments.replace(
            TreatmentRecord(Treatment("t1", TreatmentKind.Carbs, 30.0, t0, modifiedAt = t0, origin = TreatmentOrigin.Follower)),
        )

    private suspend fun all() = readings.range(Instant.EPOCH, t0.plusSeconds(86_400))

    @Test fun followerToWearerDropsTheFollowedPersonsReadingsAndTreatments() =
        runTest {
            roles.set(Role.Follower)
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(5, SourceId.Nearby)))
            addTreatment()

            roles.set(Role.Wearer)
            readings.upsert(listOf(reading(10, SourceId.Libre3)))

            assertEquals(Role.Wearer, roles.role.first())
            assertEquals(listOf(SourceId.Libre3), all().map { it.source })
            assertTrue(treatments.between(Instant.EPOCH, t0.plusSeconds(60)).first().isEmpty())
        }

    @Test fun wearerToFollowerDropsTheOwnSensorsReadings() =
        runTest {
            roles.set(Role.Wearer)
            readings.upsert(listOf(reading(0, SourceId.Libre3), reading(1, SourceId.Juggluco)))
            addTreatment()

            roles.set(Role.Follower)

            assertTrue(all().isEmpty())
            assertTrue(treatments.between(Instant.EPOCH, t0.plusSeconds(60)).first().isEmpty())
        }

    @Test fun choosingTheSameRoleAgainKeepsTheHistory() =
        runTest {
            roles.set(Role.Wearer)
            readings.upsert(listOf(reading(0, SourceId.Libre3)))
            addTreatment()

            roles.set(Role.Wearer)

            assertEquals(1, all().size)
            assertEquals(1, treatments.between(Instant.EPOCH, t0.plusSeconds(60)).first().size)
        }
}
