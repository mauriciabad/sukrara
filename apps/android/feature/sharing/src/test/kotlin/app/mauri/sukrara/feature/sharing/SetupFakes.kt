// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The phone's role store; [events] records each role written. */
class FakeRoles(
    private val events: MutableList<String> = mutableListOf(),
) : RoleStore {
    override val role = MutableStateFlow<Role?>(null)

    /** What choosing a role would delete (the same for every role here). */
    var loss: RoleLoss? = null

    override suspend fun loss(role: Role): RoleLoss? = loss

    override suspend fun set(role: Role) {
        events += "role:$role"
        this.role.value = role
    }
}

/** Setup renders THE alert checklist (the monitor's `AndroidAlertReadiness`); starts with everything allowed. */
class FakeReadiness : AlertReadinessSource {
    override val readiness = MutableStateFlow(AlertReadiness.Ready)

    override fun refresh() = Unit

    override fun fix(requirement: AlertRequirement) = Unit

    override fun answerUnlockedSound(answer: UnlockedSound) = Unit

    override fun testUnlockedSound(): Boolean = true

    override fun letThroughDnd(through: Boolean) = Unit
}

class FakeAlerts : AlertSettingsAccess {
    override val settings = MutableStateFlow(AlertSettings())

    override suspend fun save(settings: AlertSettings) {
        this.settings.value = settings
    }
}

class FakeHub : GlucoseHub {
    override val state = MutableStateFlow(GlucoseState.Initial)

    /** A reading from this phone's own sensor, now. */
    fun live(clock: FakeClock) {
        state.value =
            state.value.copy(latest = Reading(clock.now(), 120, null, SourceId.Libre3), source = SourceStatus.Live(clock.now()))
    }

    override suspend fun ingest(
        readings: List<Reading>,
        from: SourceId,
    ) = Unit

    override fun setSource(status: SourceStatus) = Unit

    override fun setSensor(sensor: SensorState) = Unit

    override fun setAlerts(alerts: AlertUiState) = Unit

    override fun setLink(link: LinkState?) = Unit
}

/** Root on this phone: a port whose `su` answers [answer] (a phone without root by default); counts the requests. */
class FakeSystemAccess(
    var answer: RootAccess = RootAccess.Missing,
    seen: Boolean = false,
) : SystemAccessPort {
    override val state = MutableStateFlow(SystemAccessState(rootSeen = seen, module = ModuleState.Active))
    var asked = 0

    override suspend fun checkRoot() {
        asked++
        state.update { it.copy(root = answer) }
    }

    override fun refresh() = Unit

    override suspend fun restartSystemUi(): String? = null

    override suspend fun requestScope(): String? = null
}
