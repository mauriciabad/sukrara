// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.feature.sharing.RoleLoss
import app.mauri.sukrara.feature.sharing.RoleStore
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.monitor.alerts.AlertStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Setup's [RoleStore] on the app's `role` store (the hub and the alert runtime read the same one). A role change never
 * mixes two people: the other role's readings ([SourceId.notOf]: the person followed before, or one's own sensor
 * before following) are deleted, and so are the treatments when the role really changed (they are the previous
 * person's). The role is written first, so reads already leave the other role out while the delete runs. Setup asks
 * [loss] first and confirms with the person before deleting anything (#125).
 */
class AppRoles(
    private val store: StateStore<RoleRecord>,
    private val readings: ReadingsRepository,
    private val treatments: TreatmentsRepository,
) : RoleStore {
    override val role: Flow<Role?> = store.data.map { it.role }

    override suspend fun loss(role: Role): RoleLoss? {
        val before = store.data.first().role
        val theirs = readings.spanOf(SourceId.notOf(role))
        return RoleLoss.of(theirs, if (before != null && before != role) treatments.span() else StoredSpan.EMPTY)
    }

    override suspend fun set(role: Role) {
        val before = store.data.first().role
        store.update { it.copy(role = role) }
        readings.deleteFrom(SourceId.notOf(role))
        if (before != null && before != role) treatments.deleteAll()
    }
}

/** Setup's alert step on the alert runtime's own settings store: confirmed values are what it checks. */
class AppAlertSettings(private val store: AlertStateStore) : AlertSettingsAccess {
    override val settings: Flow<AlertSettings> = store.settings

    override suspend fun save(settings: AlertSettings) = store.setSettings(settings)
}
