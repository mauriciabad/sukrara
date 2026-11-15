// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.settings.NotificationStyleSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/36 §11: the glucose notification's one choice over the stored keys, the same for the settings page and setup. */
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

    @Test fun `the root choice turns the root value on and hides the notification's icon`() =
        runTest {
            val s = InMemorySettingsRepository()
            s.set(SurfaceSettings.HideNotificationIcon, false)
            NotificationStyleSettings.set(s, NotificationStyle.Root)
            assertTrue(s.flow(SurfaceSettings.RootValue).first())
            assertTrue(s.flow(SurfaceSettings.HideNotificationIcon).first())
        }

    @Test fun `a build without root features never reports the root choice`() =
        runTest {
            val s = InMemorySettingsRepository()
            NotificationStyleSettings.set(s, NotificationStyle.Root)
            assertEquals(NotificationStyle.Chip, NotificationStyleSettings.flow(s, rootBuild = false).first())
        }
}
