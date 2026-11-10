// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Alert reliability's *Check it now*: the real test alert of the alert that matters most (#43). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TestAlertTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : AlertPlayback by AlertPlayback.None {
        val sent = mutableListOf<AlertKind>()

        override fun sendTest(
            kind: AlertKind,
            settings: AlertSettings,
        ) {
            sent += kind
        }
    }

    private fun AlertSettings.with(
        kind: AlertKind,
        on: Boolean,
        alarm: Boolean = rule(kind).alarm,
    ) = withRule(kind, rule(kind).copy(on = on, alarm = alarm))

    @Test fun anAlarmThatIsOnComesFirstThenTheFirstOnInTheAlertsOrder() {
        val noAlarms = AlertKind.entries.fold(AlertSettings()) { s, k -> s.with(k, on = s.rule(k).on, alarm = false) }
        assertEquals(AlertKind.entries.first { noAlarms.rule(it).on }, TestAlert.kind(noAlarms))
        assertEquals(AlertKind.HIGH, TestAlert.kind(noAlarms.with(AlertKind.HIGH, on = true, alarm = true)))
        val veryLowOff = noAlarms.with(AlertKind.VERY_LOW, on = false).with(AlertKind.LOW, on = true)
        assertEquals(AlertKind.LOW, TestAlert.kind(veryLowOff))
    }

    @Test fun nothingToTestWhileAlertsAreOffOrNoneIsOn() {
        assertNull(TestAlert.kind(AlertSettings(on = false)))
        assertNull(TestAlert.kind(AlertKind.entries.fold(AlertSettings()) { s, k -> s.with(k, on = false) }))
    }

    @Test fun reliabilitySendsTheRealTestAlert() {
        val playback = Recorder()
        val settings = AlertSettings()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                ReliabilityContent(AlertReadiness(emptySet()), {}, alerts = settings, playback = playback) {}
            }
        }
        compose.onNodeWithText("Send a test alert").performScrollTo().performClick()
        assertEquals(listOf(TestAlert.kind(settings)), playback.sent)
        compose.onNodeWithText("Sent: check your notifications. It goes away by itself in 2 min.").assertExists()
        // again, for another try (another Do Not Disturb setting)
        compose.onNodeWithText("Send a test alert").performClick()
        assertEquals(2, playback.sent.size)
    }

    @Test fun reliabilitySaysWhyThereIsNothingToTest() {
        val playback = Recorder()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                ReliabilityContent(AlertReadiness(emptySet()), {}, alerts = AlertSettings(on = false), playback = playback) {}
            }
        }
        compose.onNodeWithText("Send a test alert").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("No alert is on: turn one on in Alerts to test it").assertExists()
        assertTrue(playback.sent.isEmpty())
    }
}
