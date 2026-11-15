// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.trended
import app.mauri.sukrara.monitor.alerts.AlertNotifier
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * The Live Update value's label (docs/36 §10; the owner: "it is nonsense to show 'Now' … of course a notification is
 * now"): the trend in words while fresh, the rate when the alert's title already says the trend, the reading's time
 * once late; sentence case; never the title's words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MetricLabelTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.now()
    private val live = LiveNotification(ctx)

    private fun content(
        rate: Double,
        ageS: Long = 30,
    ): LiveNotificationContent {
        val reading = Reading(now.minusSeconds(ageS), 62, rate, SourceId.Libre3).trended()
        val state =
            GlucoseState.Initial.copy(
                role = Role.Wearer,
                latest = reading,
                recent = listOf(reading),
                sensor = SensorState.Active(null),
                monitoringStartedAt = now - Duration.ofHours(1),
            )
        return LiveNotificationContent.from(state, now, LiveNotification::face, NotificationPrefs(chip = true, promotionAllowed = true))
    }

    private fun label(
        c: LiveNotificationContent,
        alertTitle: String? = null,
    ): String {
        val alert = alertTitle?.let { AlertNotifierStub.alert(ctx, it) }
        return live.metricLabel(c, c.look!!, alert, GlucoseUnit.MGDL)
    }

    @Test fun `fresh, the trend in words in sentence case`() {
        assertEquals("Falling", label(content(-1.2)))
        assertEquals("Steady", label(content(0.1)))
    }

    @Test fun `a rate alert already says the trend, so the rate instead`() {
        val l = label(content(-2.4), alertTitle = "Falling fast")
        assertEquals("2.4/min", l)
        assertFalse(l.contains("Falling", ignoreCase = true))
    }

    /**
     * Spanish (round 6 of #36): *Subiendo rápido* was cut to *Subiendo ráp…* beside two more metrics. The label words
     * fit a third of the card; the rate alert's Spanish headline (*Bajada rápida*) is the label's own word, so under
     * it the rate shows instead; the muted side's countdown fits too.
     */
    @Test fun `Spanish - words that fit, the headline's word, the countdown's label`() {
        val es = Configuration(ctx.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("es")) }
        val esCtx = ctx.createConfigurationContext(es)
        val liveEs = LiveNotification(esCtx)
        val c = content(-2.4)
        val fresh = liveEs.metricLabel(c, c.look!!, null, GlucoseUnit.MGDL)
        assertEquals("Bajada rápida", fresh)
        assertTrue(
            fresh,
            app.mauri.sukrara.graph.Slots
                .fits(fresh),
        )
        val alert = AlertNotifierStub.alert(esCtx, "Bajada rápida")
        assertEquals("2,4/min", liveEs.metricLabel(c, c.look!!, alert, GlucoseUnit.MGDL))
        val left = esCtx.getString(R.string.monitor_metric_mute_left)
        assertTrue(
            left,
            app.mauri.sukrara.graph.Slots
                .fits(left),
        )
        assertTrue(
            app.mauri.sukrara.graph.Slots
                .fits(ctx.getString(R.string.monitor_metric_mute_left)),
        )
    }

    @Test fun `late, the time of the reading`() {
        val c = content(-1.2, ageS = 3 * 60)
        assertEquals(
            app.mauri.sukrara.graph.Freshness
                .timeText(c.readingAt!!.epochSecond, Instant.now().epochSecond),
            label(c),
        )
    }

    /** A [app.mauri.sukrara.monitor.notify.GlucoseAlert] with [title], built by the real notifier. */
    private object AlertNotifierStub {
        fun alert(
            ctx: Context,
            title: String,
        ) = AlertNotifier(ctx)
            .glucoseAlert(
                app.mauri.sukrara.alerts.Firing(
                    app.mauri.sukrara.alerts.AlertKind.FALLING_FAST,
                    "f",
                    1,
                    app.mauri.sukrara.alerts
                        .AlertReading(t = 1, mgdl = 62, rate = -2.4),
                    2.0,
                    1,
                ),
                app.mauri.sukrara.alerts
                    .AlertSettings(),
                null,
                0,
                1,
            ).copy(title = title)
    }
}
