// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.data.settings.SettingsFrom
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceAlertRole
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.core.testing.TestDispatchers
import app.mauri.sukrara.feature.home.NoDataReason
import app.mauri.sukrara.feature.home.homeLook
import app.mauri.sukrara.feature.settings.AlertsUi
import app.mauri.sukrara.feature.settings.Escalation
import app.mauri.sukrara.feature.treatments.TreatmentWriter
import app.mauri.sukrara.feature.treatments.WriterScope
import app.mauri.sukrara.monitor.hub.GlucoseHubImpl
import app.mauri.sukrara.monitor.hub.MonitorRecord
import app.mauri.sukrara.protocol.event.SettingsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One person, one link (contracts/sources/link.json `after`, docs/44 Appendix G §8): a source that isn't the Sukrara
 * link uses nothing derived from it, with a follow's leftovers around (its record, the wearer's settings and name, an
 * ended link, the wearer's treatments). One check per id the contract lists; every id is checked.
 */
class NonLinkSourceTest {
    private val ids =
        Json
            .parseToJsonElement(Contracts.file("sources/link.json").readText())
            .jsonObject
            .getValue("after")
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.content
            }

    /** Someone else's LibreLinkUp, the follow of Ana kept around. */
    private val llu = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp)
    private val nonLink = SourceKind.entries.filter { it != SourceKind.Sukrara }.flatMap { k -> k.persons.map { SourceSpec(it, k) } }
    private val ana = LinkState("Ana", null, revoked = true, active = false)
    private val wearerSettings = SettingsPayload(1_800_000_000, JsonObject(mapOf("low" to JsonPrimitive(90))), JsonObject(emptyMap()))
    private val checked = mutableSetOf<String>()

    private fun check(
        id: String,
        block: () -> Unit,
    ) {
        assertTrue("$id isn't in link.json's after list", id in ids)
        block()
        checked += id
    }

    @Test
    fun `a non-link source uses nothing derived from the link`() =
        runTest {
            // record + connection: a follow kept behind another source is left at start (the wearer told), so nothing
            // is kept to connect with; the follower engine is the reading source only for the link
            var left = false
            val treatments = InMemoryTreatmentsRepository()
            treatments.replace(
                TreatmentRecord(
                    Treatment(
                        "ana",
                        TreatmentKind.Carbs,
                        25.0,
                        T0,
                        modifiedAt = T0,
                        origin = TreatmentOrigin.Follower,
                    ),
                    creator = "wearer",
                    via = "wss://relay",
                    delivery = TreatmentDelivery.Delivered,
                ),
            )
            check("record") {
                kotlinx.coroutines.runBlocking {
                    assertTrue(AppLink.leaveLeftBehind(llu, { true }, { true.also { left = true } }, treatments))
                }
                assertTrue(left)
            }
            check("connection") {
                // nothing to connect with after the leave, and the link itself is never left behind
                kotlinx.coroutines.runBlocking {
                    assertFalse(AppLink.leaveLeftBehind(SourceSpec.TheirSukrara, { true }, { error("never") }, treatments))
                }
            }

            // settings: never the wearer's; Settings from isn't there; every pane is editable
            check("settings") {
                kotlinx.coroutines.runBlocking {
                    val own = InMemorySettingsRepository().apply { set(SettingsFrom.Stored, SettingsFrom.Wearer.wire) }
                    val bridge =
                        FollowerSettingsBridge(
                            own,
                            Alerts(AlertSettings()),
                            MutableStateFlow(llu),
                            MutableStateFlow(true),
                            MutableStateFlow(wearerSettings),
                            MutableStateFlow("Ana"),
                        )
                    assertNull(bridge.state.first())
                    assertNull(bridge.alertMirror.first())
                    assertNull(bridge.alertsInUse.first())
                }
            }

            // name + path + noData: the hub shows the follow's link (its name, how readings come, its ending) only for
            // the link
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val spec = MutableStateFlow<SourceSpec?>(llu)
            val hub =
                GlucoseHubImpl(
                    CoroutineScope(backgroundScope.coroutineContext + dispatchers.dispatcher),
                    dispatchers,
                    FakeClock(),
                    InMemoryReadingsRepository(),
                    InMemoryStateStore(MonitorRecord()),
                    spec,
                ).also { it.start() }
            hub.setLink(ana)
            advanceUntilIdle()
            check("name") { assertNull(hub.state.value.name) }
            check("path") { assertNull(hub.state.value.link) }
            check("noData") {
                val state = GlucoseState.Initial.copy(spec = llu, link = ana)
                assertNotEquals(
                    NoDataReason.LinkRevoked,
                    NoDataReason.of(state, homeLook(state, java.time.Instant.EPOCH, GlucoseUnit.MGDL)),
                )
            }

            // alerts: never the wearer's mirrored events; the phone's own engine runs on the source
            check(
                "alerts",
            ) { nonLink.forEach { assertNotEquals(it.toString(), SourceAlertRole.FollowerMirror, it.alertRole(mirrorsWearer = true)) } }

            // escalation: the switch isn't shown
            check("escalation") {
                assertEquals(Escalation.None, AlertsUi(AlertSettings(), AlertUiState(), owner = llu.owner, follower = false).escalation)
            }

            // treatments: the source's own log (never the wearer's outbox), the wearer's kept entries read-only
            check("treatments") {
                val local = TreatmentWriter(key = null, throughOutbox = false)
                val outbox = TreatmentWriter(key = "f", throughOutbox = true)
                nonLink.forEach {
                    assertTrue(WriterScope.canAdd(it, local))
                    assertFalse(WriterScope.canAdd(it, outbox))
                }
                kotlinx.coroutines.runBlocking { assertTrue(treatments.get("ana")!!.fromLink) }
            }

            // sensor: the hub's sensor is the source's; the follow's sensor reaches it only as the link's
            check("sensor") { assertEquals(app.mauri.sukrara.core.model.SensorState.None, hub.state.value.sensor) }

            assertEquals("every id of link.json's after list is checked", ids.toSet(), checked)
        }

    private class Alerts(initial: AlertSettings) : FollowerSettingsBridge.AlertsAccess {
        val state = MutableStateFlow(initial)
        override val settings: Flow<AlertSettings> = state

        override suspend fun update(transform: (AlertSettings) -> AlertSettings) = state.update(transform)
    }

    private companion object {
        val T0: java.time.Instant = java.time.Instant.parse("2026-11-18T08:00:00Z")
    }
}
