// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.core.data.readings.TrendReadings
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.feature.settings.TrendArrowPreview
import app.mauri.sukrara.feature.treatments.TreatmentTileService
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.graph.face
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.integrations.broadcasts.BroadcastPayloads
import app.mauri.sukrara.integrations.localserver.NightscoutApi
import app.mauri.sukrara.integrations.localserver.XdripEndpoint
import app.mauri.sukrara.integrations.nightscout.NsApi
import app.mauri.sukrara.integrations.nightscout.NsDocs
import app.mauri.sukrara.monitor.LiveNotification
import app.mauri.sukrara.monitor.alerts.AlertCopy
import app.mauri.sukrara.monitor.alerts.AlertRuntime
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.NotificationPrefs
import app.mauri.sukrara.monitor.readaloud.ReadAloud
import app.mauri.sukrara.monitor.systemui.HookText
import app.mauri.sukrara.monitor.systemui.RootPrefs
import app.mauri.sukrara.monitor.systemui.RootStatusBar
import app.mauri.sukrara.monitor.systemui.SlotIcons
import app.mauri.sukrara.widgets.WidgetFrame
import app.mauri.sukrara.widgets.WidgetKind
import app.mauri.sukrara.widgets.WidgetOverrides
import app.mauri.sukrara.widgets.WidgetPlacements
import app.mauri.sukrara.widgets.describeCard
import app.mauri.sukrara.widgets.describeLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * One trend everywhere (#204, contracts/trend): the owner's rise of 2026-11-14 afternoon (about +1 mg/dL a minute while
 * the sensor's own rate said 0.79) goes through THE annotate point ([TrendReadings], as the hub reads it), and every
 * place that shows the trend shows the same arrow: ↗ from the readings (Balanced), → from the sensor. A setting change
 * turns them all at once. The Mac's TrendEverywhereTests run the same readings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TrendEverywhereTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 2026-11-14 14:30:37–15:12:52 (UTC+2), one a minute, with Abbott's rate (contracts/trend/rate.json "the owner's rise"). */
    private val rise =
        listOf(
            "14:30:37|148|-0.09",
            "14:31:39|146|-0.19",
            "14:32:38|144|-0.27",
            "14:33:39|143|-0.29",
            "14:34:38|144|-0.22",
            "14:35:40|146|-0.12",
            "14:36:39|148|-0.06",
            "14:37:40|148|-0.04",
            "14:38:41|147|-0.05",
            "14:39:41|147|-0.04",
            "14:40:41|147|-0.06",
            "14:41:41|146|-0.1",
            "14:42:42|146|-0.14",
            "14:43:42|145|-0.13",
            "14:44:41|147|-0.07",
            "14:45:43|150|0.07",
            "14:46:43|153|0.18",
            "14:47:43|154|0.24",
            "14:48:43|154|0.24",
            "14:49:43|154|0.21",
            "14:50:44|154|0.2",
            "14:51:44|155|0.2",
            "14:52:45|155|0.21",
            "14:53:45|156|0.24",
            "14:54:46|158|0.31",
            "14:55:46|160|0.37",
            "14:56:45|161|0.41",
            "14:57:46|163|0.45",
            "14:58:46|164|0.48",
            "14:59:46|165|0.52",
            "15:00:47|169|0.63",
            "15:01:47|171|0.68",
            "15:02:47|172|0.7",
            "15:03:47|174|0.73",
            "15:04:48|175|0.76",
            "15:05:48|176|0.76",
            "15:06:48|177|0.74",
            "15:07:50|178|0.74",
            "15:08:48|179|0.78",
            "15:09:50|180|0.79",
            "15:10:51|181|0.8",
            "15:11:50|182|0.79",
            "15:12:52|182|0.79",
        ).map {
            val (hms, v, r) = it.split('|')
            Reading(Instant.parse("2026-11-14T${hms}Z").minusSeconds(2 * 3600), v.toInt(), r.toDouble(), SourceId.Libre3)
        }

    private val now: Instant = rise.last().t.plusSeconds(30)
    private val nowS = now.epochSecond
    private val setting = MutableStateFlow(TrendSetting.Balanced)
    private val readings =
        TrendReadings(InMemoryReadingsRepository().also { runBlocking { it.upsert(rise) } }, setting)

    /** The hub's state from the one annotate point. */
    private fun state(): GlucoseState {
        val recent = runBlocking { readings.window(now.minusSeconds(24 * 3600)).first() }
        return GlucoseState.Initial.copy(
            role = Role.Wearer,
            latest = recent.last(),
            recent = recent,
            sensor = SensorState.Active(null),
            monitoringStartedAt = now.minusSeconds(3600),
        )
    }

    /** Every place's arrow (text arrow, or its word / Nightscout name mapped back to the band's text) for [s]. */
    private fun arrows(s: GlucoseState): Map<String, String> {
        val latest = s.latest!!
        val band = latest.trend?.band
        val text = TrendAngle.text(band)
        val word = CardWords.of(context.resources).trend(band)
        val look = GlucoseCard.face(s, nowS, CardSurface.APP_PANEL, GlucoseUnit.MGDL)
        val out = LinkedHashMap<String, String>()
        out["card"] = look.arrowText!!
        out["card angle"] = if (look.rate?.let(TrendAngle::deg) == latest.rate?.let(TrendAngle::deg)) text else "angle off"
        val notification =
            LiveNotificationContent.from(
                s,
                now,
                LiveNotification::face,
                NotificationPrefs(chip = true, promotionAllowed = true),
            )
        out["glucose notification"] = notification.title.args[2].toString()
        val firing = Firing(AlertKind.HIGH, "e", 1, AlertRuntime.readingOf(latest), 180.0, nowS)
        val words = AlertWords.of(context, AlertWords.Ids + AlertCopy.Ids)
        out["alert body"] = if (text in AlertCopy.body(firing, words, GlucoseUnit.MGDL, Locale.ENGLISH, nowS)) text else "missing"
        val iconsLook = RootStatusBar.look(s, nowS)
        val icons = SlotIcons.plan(iconsLook, SlotIcons.Words("", ""))
        out["status icons"] = if (icons.trend == SlotIcons.trendDrawable(band!!)) text else icons.trend.toString()
        val hook = RootStatusBar.hookState(s, RootPrefs(true, "after_clock", true, true), Bands(), GlucoseUnit.MGDL)
        out["status hook"] = HookText.face(hook, nowS).arrowText!!
        out["quick tile"] =
            TreatmentTileService
                .subtitle(
                    GlucoseCard.face(s, nowS, CardSurface.QUICK_TILE, GlucoseUnit.MGDL),
                    latest.t.epochSecond,
                    nowS,
                ) { "" }!!
                .substringAfter(' ')
        out["read aloud"] = if (word in ReadAloud.sentence(s, Bands(), GlucoseUnit.MGDL, nowS, context.resources)) text else "missing"
        val frame = WidgetFrame(s, emptyList(), GraphConfig(), WidgetPlacements(), now, dark = false)
        out["widget description"] = if (word in describeCard(context, WidgetKind.VALUE, frame, WidgetOverrides())) text else "missing"
        out["lock description"] = if (word in describeLock(context, frame)) text else "missing"
        val ns = TrendAngle.nightscout(band)
        out["nightscout upload"] =
            if (NsDocs.entry(latest, "d", ZoneOffset.UTC, NsApi.V1)["direction"]!!.jsonPrimitive.content == ns) text else "off"
        out["nightscout endpoint"] = if (NightscoutApi.entry(latest)["direction"]!!.jsonPrimitive.content == ns) text else "off"
        out["xdrip endpoint"] = if (XdripEndpoint.direction(latest) == ns) text else "off"
        // the settings page's preview asks the card's face as its own registered surface (the observer is global: only
        // the faces asked on this thread count, not those background work elsewhere in the JVM asks meanwhile)
        val asked = mutableListOf<CardSurface>()
        val me = Thread.currentThread()
        GlucoseCard.faceObserver = { surface, _ -> if (Thread.currentThread() == me) asked += surface }
        val preview =
            try {
                TrendArrowPreview.of(s.recent, now, GlucoseUnit.MGDL).getValue(setting.value).look!!
            } finally {
                GlucoseCard.faceObserver = null
            }
        out["trend preview"] =
            if (asked.isNotEmpty() && asked.all { it == CardSurface.TREND_PREVIEW }) preview.arrowText!! else "not its surface"
        return out
    }

    @Test fun `every place shows the readings' arrow, then the sensor's once the setting says so`() {
        val readingsArrows = arrows(state())
        assertTrue(readingsArrows.toString(), readingsArrows.values.all { it == "↗" })

        setting.value = TrendSetting.Sensor
        val sensorArrows = arrows(state())
        assertTrue(sensorArrows.toString(), sensorArrows.values.all { it == "→" })
        assertEquals(readingsArrows.keys, sensorArrows.keys)
    }

    @Test fun `the xDrip+ broadcast sends the trend's rate under its own names, never the sensor's own rate`() {
        val latest = state().latest!!
        val out = BroadcastPayloads.Out(latest.mgdl, latest.rate, latest.t.toEpochMilli(), "3MH0", 0)
        val slope = BroadcastPayloads.xdrip(out)[BroadcastPayloads.X_SLOPE] as Double
        assertEquals(latest.rate!! / 60_000.0, slope, 1e-12)
        assertTrue("the readings' trend, not Abbott's 0.79", latest.rate!! > 0.9)
    }
}
