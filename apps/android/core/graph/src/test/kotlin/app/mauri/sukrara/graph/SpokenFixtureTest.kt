// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.Fixtures.bool
import app.mauri.sukrara.graph.Fixtures.double
import app.mauri.sukrara.graph.Fixtures.long
import app.mauri.sukrara.graph.Fixtures.str
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucosePoint
import app.mauri.sukrara.graph.stats.TirRange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * THE spoken sentence (#25) against contracts/graph/spoken, in English and Spanish: the card's sentence through
 * [A11y.card] and each slot's words through [A11y.metric] / [A11y.last]. The Mac runs the same files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpokenFixtureTest {
    private val now = Scenes.NOW
    private val languages = mapOf("en" to Locale.ENGLISH, "es" to Locale.forLanguageTag("es"))

    private fun cases(path: String) = Fixtures.read(path)["cases"]!!.jsonArray.map { it.jsonObject }

    private fun name(c: JsonObject) = c["name"].str()!!

    @Test
    fun card() {
        val cs = cases("spoken/card.json")
        assertTrue(cs.size >= 20)
        for (c in cs) {
            val r = c["reading"]?.jsonObject
            val latest = r?.let { GlucosePoint(now - it["age"].long()!!, it["sgv"].long()!!.toInt(), it["rate"].double()) }
            val history =
                c["history"]?.jsonObject?.let { h ->
                    val from = latest?.t ?: now
                    (1..h["hours"].long()!! * 12).map { k -> GlucosePoint(from - k * 300, h["sgv"].long()!!.toInt(), null) }.reversed()
                } ?: emptyList()
            val sensor = c["sensor"]?.jsonObject?.let { SensorInfo(now + it["start"].long()!!, now + it["end"].long()!!) }
            val card = c["card"]?.jsonObject
            val style =
                CardStyle(
                    graph = card?.get("graph").long()?.let { GraphStyle(windowS = it * 3600) },
                    rings =
                        card?.get("rings")?.jsonArray?.map { it.jsonObject }?.map {
                            RingSpec(
                                it["window"].long()!!,
                                TirRange.valueOf(it["range"].str() ?: "IN_RANGE"),
                                SlotKind.valueOf(it["kind"].str()!!),
                            )
                        } ?: emptyList(),
                    value = card?.get("value").bool() ?: true,
                    badge = card?.get("badge").bool() ?: true,
                    sensorTime = card?.get("sensorTime").bool() ?: false,
                    bands = Bands(),
                    unit = GlucoseUnit.of(c["unit"].str()),
                )
            for ((lang, locale) in languages) {
                val model =
                    CardModel(
                        readings = history + listOfNotNull(latest),
                        treatments = emptyList(),
                        nowS = now,
                        words = Scenes.words(locale),
                        status = status(c["status"].str()),
                        sensor = sensor,
                        countdownHours = card?.get("countdownHours").long()?.toInt() ?: SensorLife.DEFAULT_COUNTDOWN_HOURS,
                    )
                val time = latest?.let { Freshness.timeText(it.t, now) }.orEmpty()
                val want = c["expect"]!!.jsonObject[lang].str()!!.replace("{time}", time)
                assertEquals("${name(c)} ($lang)", want, A11y.card(model, style, CardSurface.TEST))
            }
        }
    }

    @Test
    fun slots() {
        val cs = cases("spoken/slots.json")
        assertTrue(cs.size >= 10)
        for (c in cs) {
            for ((lang, locale) in languages) {
                val w = Scenes.words(locale)
                val s = c["slot"]?.jsonObject
                val got =
                    if (s != null) {
                        A11y.metric(
                            SlotKind.valueOf(s["kind"].str()!!),
                            TirRange.valueOf(s["range"].str() ?: "IN_RANGE"),
                            s["window"].long()!!,
                            s["text"].str()!!,
                            w,
                        )
                    } else {
                        A11y.last(info(c["last"]!!.jsonObject, c["with"]?.jsonObject?.let { info(it, null) }), now, w)
                    }
                assertEquals("${name(c)} ($lang)", c["expect"]!!.jsonObject[lang].str(), got)
            }
        }
    }

    private fun info(
        t: JsonObject,
        with: LastTreatment.Info?,
    ): LastTreatment.Info {
        val kind =
            when (t["k"].str()) {
                "rapid" -> TreatmentKind.RapidInsulin
                "long" -> TreatmentKind.LongInsulin
                "carbs" -> TreatmentKind.Carbs
                else -> TreatmentKind.Note
            }
        return LastTreatment.Info(Scenes.treatment(t["k"].str()!!, kind, t["a"].double()!!, t["age"].long()!!), null, with)
    }

    /** `needed:low`, `taken:carbs:600`, `paused:high:never`, `muted:4800` (seconds from now) as the card's status. */
    private fun status(s: String?): CardStatus {
        val p = s?.split(':') ?: return CardStatus.None

        fun end(v: String) = if (v == "never") CardStatus.NEVER else now + v.toLong()
        return when (p[0]) {
            "needed" -> CardStatus.Needed(lowSide = p[1] == "low")
            "taken" -> CardStatus.Taken(if (p[1] == "carbs") TreatmentKind.Carbs else TreatmentKind.RapidInsulin, now - 600, end(p[2]))
            "paused" -> CardStatus.Paused(lowSide = p[1] == "low", until = end(p[2]))
            "muted" -> CardStatus.Muted(end(p[1]))
            else -> error("status $s")
        }
    }

    @Test
    fun endedMuteHasNoPill() {
        val w = Scenes.words()
        assertNull(StatusBadge.look(CardStatus.Taken(TreatmentKind.RapidInsulin, now - 3600, now), now, w))
        assertNull(StatusBadge.look(CardStatus.Paused(lowSide = true, until = now - 1), now, w))
        assertNull(StatusBadge.look(CardStatus.Muted(now), now, w))
        assertEquals("1m left", StatusBadge.look(CardStatus.Paused(lowSide = true, until = now + 1), now, w)?.text)
    }

    @Test
    fun placeHoursZeroMeansTheDefault() {
        val c = GraphConfig(defaultHours = 12, placeHours = mapOf(GraphPlace.entries.first() to 0))
        assertEquals(12, c.hours(GraphPlace.entries.first()))
    }

    @Test
    fun theRangeFollowsTheSettings() {
        val m = Scenes.model(listOf(GlucosePoint(now - 60, 200, 0.0)), Scenes.words(), sensor = null)
        assertEquals("200 milligrams per decilitre, steady, high, 1 min ago", A11y.value(m, Bands(), CardSurface.TEST, GlucoseUnit.MGDL))
        assertEquals(
            "200 milligrams per decilitre, steady, in range, 1 min ago",
            A11y.value(m, Bands().copy(high = 220), CardSurface.TEST, GlucoseUnit.MGDL),
        )
    }

    /** A new reading's live announcement (home's polite live region): as it came in, with no age or alert. */
    @Test
    fun aReadingIsSaidAsItCame() {
        val style = CardStyle(sensorTime = false, bands = Bands(), unit = GlucoseUnit.MGDL)
        val m =
            Scenes
                .model(listOf(GlucosePoint(now - 420, 112, -1.5)), Scenes.words(), sensor = null)
                .copy(status = CardStatus.Needed(lowSide = true))
        // seven minutes on, the card says "old reading"; the announcement stays what came in
        assertEquals("112 milligrams per decilitre, falling, in range", A11y.reading(m, style, CardSurface.APP_PANEL))
        assertEquals(
            "60 milligrams per decilitre, steady, low",
            A11y.reading(
                Scenes.model(listOf(GlucosePoint(now - 60, 60, 0.0)), Scenes.words(), sensor = null),
                style,
                CardSurface.APP_PANEL,
            ),
        )
        assertNull(A11y.reading(Scenes.model(emptyList(), Scenes.words(), sensor = null), style, CardSurface.APP_PANEL))
    }
}
