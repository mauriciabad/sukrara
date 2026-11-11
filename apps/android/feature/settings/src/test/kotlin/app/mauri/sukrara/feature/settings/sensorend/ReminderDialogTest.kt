// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.sensorend

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorReminder
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * #181: a reminder's dialog is SPA's SettingsDialog with ListPreference's radio rows (TalkBack: radio buttons, one
 * selected), the time row, and Remove / Cancel / Save.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReminderDialogTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private var config = SensorEndConfig(reminders = listOf(SensorReminder(1, 9 * 60)))

    private fun show() =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SensorEndContent(
                    SensorEndPrefs(config, 8, SensorState.Active(now.plusSeconds(3 * 86_400))),
                    now,
                    SensorEndActions(change = { config = it(config) }),
                    onBack = {},
                )
            }
        }

    @Test
    fun aReminderIsChangedWithRadioRowsAndSaved() {
        show()
        compose.onNodeWithText("1 day before", substring = true).performScrollTo().performClick()
        compose.onNode(hasText("1 day before") and isSelectable()).assertIsSelected()
        val two = compose.onNodeWithText("2 days before")
        assertEquals(Role.RadioButton, two.fetchSemanticsNode().config[SemanticsProperties.Role])
        two.performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(SensorReminder(2, 9 * 60)), config.reminders)
    }

    @Test
    fun anExistingReminderCanBeRemoved() {
        show()
        compose.onNodeWithText("1 day before", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(emptyList<SensorReminder>(), config.reminders)
    }
}
