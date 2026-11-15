// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.settings.NotificationStyleSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/36 §11, §14: the glucose notification's one choice and its icon, the same for the settings page and setup. */
class NotificationStyleSettingsTest {
    @Test fun `the chip by default, each choice round-trips`() =
        runTest {
            val s = InMemorySettingsRepository()
            assertEquals(NotificationStyle.Chip, NotificationStyleSettings.flow(s, rootBuild = true).first())
            for (style in NotificationStyle.entries) {
                NotificationStyleSettings.set(s, style)
                assertEquals(style, NotificationStyleSettings.flow(s, rootBuild = true).first())
            }
        }

    @Test fun `the root text hides an icon that would repeat the value, and keeps one that doesn't`() =
        runTest {
            val s = InMemorySettingsRepository()
            NotificationStyleSettings.set(s, NotificationStyle.Text)
            assertEquals(NotificationIcon.None, NotificationStyleSettings.iconFlow(s, rootBuild = true).first())
            NotificationStyleSettings.setIcon(s, NotificationIcon.Trend)
            NotificationStyleSettings.set(s, NotificationStyle.Icon)
            NotificationStyleSettings.set(s, NotificationStyle.Text)
            assertEquals(NotificationIcon.Trend, NotificationStyleSettings.iconFlow(s, rootBuild = true).first())
        }

    @Test fun `a hidden icon comes back as the value when the text is left`() =
        runTest {
            val s = InMemorySettingsRepository()
            NotificationStyleSettings.set(s, NotificationStyle.Text)
            NotificationStyleSettings.set(s, NotificationStyle.Icon)
            assertEquals(NotificationIcon.ValueTrend, NotificationStyleSettings.iconFlow(s, rootBuild = true).first())
            // a stored "none" read under another choice is never a hidden icon
            s.set(SurfaceSettings.NotifIcon, NotificationIcon.None.key)
            assertEquals(NotificationIcon.ValueTrend, NotificationStyleSettings.icon(NotificationStyle.Icon, NotificationIcon.None.key))
        }

    @Test fun `a build without root features never reports the root text`() =
        runTest {
            val s = InMemorySettingsRepository()
            NotificationStyleSettings.set(s, NotificationStyle.Text)
            assertEquals(NotificationStyle.Chip, NotificationStyleSettings.flow(s, rootBuild = false).first())
        }
}
