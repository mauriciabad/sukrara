// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.text.Spanned
import android.text.style.TtsSpan
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The alert's *Mute…* / *Change time* chips (#103): shortest first, and each one says to TalkBack what it does (a
 * TtsSpan: "Low alerts: mute for 30 min"), since a bare "30 min" has no action in it. The chip's text is what comes
 * back as the reply, so the receiver still parses "30 min".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertMuteChoicesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        // a library test has no launcher icon; the notifier uses the app's
        ctx.applicationInfo.icon = android.R.drawable.star_on
        AlertChannels.ensure(ctx)
    }

    private fun veryLow() = Firing(AlertKind.VERY_LOW, "e-very-low", 1, AlertReading(t = 1_000, mgdl = 50, rate = -1.0), 55.0, 1_000)

    private fun chips(n: Notification): List<CharSequence> {
        val action = n.actions.first { it.remoteInputs?.isNotEmpty() == true }
        return action.remoteInputs
            .single()
            .choices
            .toList()
    }

    private fun spoken(chip: CharSequence): String? =
        (chip as? Spanned)
            ?.getSpans(0, chip.length, TtsSpan::class.java)
            ?.singleOrNull()
            ?.args
            ?.getString(TtsSpan.ARG_TEXT)

    @Test fun `very low's chips go 15 min, 30 min, 1 h, 2 h and each names the side and the length`() {
        AlertNotifier(ctx).show(veryLow(), AlertSettings(), silent = true, who = null, relaxedMin = 0)
        val n = shadowOf(nm).getNotification(AlertNotifier.idOf(AlertKind.VERY_LOW.group)).also(::assertNotNull)
        val chips = chips(n)
        assertEquals(listOf("15 min", "30 min", "1 h", "2 h"), chips.map { it.toString() })
        assertEquals(
            listOf("Low alerts: mute for 15 min", "Low alerts: mute for 30 min", "Low alerts: mute for 1 h", "Low alerts: mute for 2 h"),
            chips.map(::spoken),
        )
    }

    @Test fun `a first length of the person's own sits in order, once`() {
        val chips = AlertNotifier(ctx).muteChoices(AlertKind.HIGH, listOf(45, 15, 30, 60, 120, 45))
        assertEquals(listOf("15 min", "30 min", "45 min", "1 h", "2 h"), chips.map { it.toString() })
        assertEquals("High alerts: mute for 45 min", spoken(chips[2]))
    }

    @Test fun `Change time on the muted notification carries the same chips`() {
        AlertNotifier(ctx).showMuted(AlertKind.VERY_LOW.group, Side.LOW, AlertKind.VERY_LOW, until = 5_000, AlertSettings(), now = 1_000)
        val n = shadowOf(nm).getNotification(AlertNotifier.idOf(AlertKind.VERY_LOW.group)).also(::assertNotNull)
        assertEquals("Low alerts: mute for 15 min", spoken(chips(n).first()))
    }

    @Config(qualifiers = "es")
    @Test
    fun `in Spanish the side reads as glucosa baja`() {
        val chips = AlertNotifier(ctx).muteChoices(AlertKind.VERY_LOW, listOf(30, 15))
        assertEquals("Alertas de glucosa baja: silenciar durante 15 min", spoken(chips[0]))
    }
}
