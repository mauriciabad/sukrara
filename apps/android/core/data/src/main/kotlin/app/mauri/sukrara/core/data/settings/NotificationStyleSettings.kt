// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.settings

import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.storage.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * THE glucose notification choice ([NotificationStyle]) and its icon ([NotificationIcon]), read and written in one
 * place so the settings page, setup and the monitor can't disagree (docs/36 §14).
 */
object NotificationStyleSettings {
    /** The choice now ([rootBuild] false: a build without root features never reports *Text*: the chip instead). */
    fun flow(
        settings: SettingsRepository,
        rootBuild: Boolean,
    ): Flow<NotificationStyle> = settings.flow(SurfaceSettings.StatusShow).map { of(it, rootBuild) }

    fun of(
        key: String,
        rootBuild: Boolean,
    ): NotificationStyle =
        NotificationStyle.of(key).let { if (it == NotificationStyle.Text && !rootBuild) NotificationStyle.DEFAULT else it }

    /** The icon the notification shows for [style] (one the style offers; the stored one when it does). */
    fun icon(
        style: NotificationStyle,
        key: String,
    ): NotificationIcon {
        val stored = NotificationIcon.of(key)
        val offered = NotificationIcon.offered(style)
        return if (stored in offered || offered.isEmpty()) stored else NotificationIcon.DEFAULT
    }

    /** The icon now, for the choice now. */
    fun iconFlow(
        settings: SettingsRepository,
        rootBuild: Boolean,
    ): Flow<NotificationIcon> =
        combine(flow(settings, rootBuild), settings.flow(SurfaceSettings.NotifIcon)) { style, key -> icon(style, key) }

    /**
     * Stores [style]. *Text* hides the notification's icon when it would repeat the value the bar now shows (the
     * person can pick another after); *Icon* brings the value back to an icon that can't show (hidden: only with the
     * text).
     */
    suspend fun set(
        settings: SettingsRepository,
        style: NotificationStyle,
    ) {
        val icon = NotificationIcon.of(settings.flow(SurfaceSettings.NotifIcon).first())
        when {
            style == NotificationStyle.Text && (icon == NotificationIcon.ValueTrend || icon == NotificationIcon.Value) -> {
                settings.set(SurfaceSettings.NotifIcon, NotificationIcon.None.key)
            }

            style == NotificationStyle.Icon && icon !in NotificationIcon.offered(style) -> {
                settings.set(SurfaceSettings.NotifIcon, NotificationIcon.DEFAULT.key)
            }
        }
        settings.set(SurfaceSettings.StatusShow, style.key)
    }

    suspend fun setIcon(
        settings: SettingsRepository,
        icon: NotificationIcon,
    ) = settings.set(SurfaceSettings.NotifIcon, icon.key)
}
