// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.settings

import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.storage.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * THE glucose notification choice ([NotificationStyle]) over the stored keys, read and written in one place so the
 * settings page and setup can't disagree: *Root* = the root value on with the notification's icon hidden; otherwise
 * the root value off and the chip (*Chip*) or not (*Icon*).
 */
object NotificationStyleSettings {
    /** The choice now ([rootBuild] false: a build without root features never reports *Root*). */
    fun flow(
        settings: SettingsRepository,
        rootBuild: Boolean,
    ): Flow<NotificationStyle> =
        combine(settings.flow(SurfaceSettings.StatusChip), settings.flow(SurfaceSettings.RootValue)) { chip, root ->
            of(chip, root && rootBuild)
        }

    fun of(
        chip: Boolean,
        root: Boolean,
    ): NotificationStyle =
        when {
            root -> NotificationStyle.Root
            chip -> NotificationStyle.Chip
            else -> NotificationStyle.Icon
        }

    /** Stores [style]: *Root* turns the root value on and hides the notification's icon; the others turn it off. */
    suspend fun set(
        settings: SettingsRepository,
        style: NotificationStyle,
    ) {
        when (style) {
            NotificationStyle.Root -> {
                settings.set(SurfaceSettings.HideNotificationIcon, true)
                settings.set(SurfaceSettings.RootValue, true)
            }

            NotificationStyle.Chip -> {
                settings.set(SurfaceSettings.RootValue, false)
                settings.set(SurfaceSettings.StatusChip, true)
            }

            NotificationStyle.Icon -> {
                settings.set(SurfaceSettings.RootValue, false)
                settings.set(SurfaceSettings.StatusChip, false)
            }
        }
    }
}
