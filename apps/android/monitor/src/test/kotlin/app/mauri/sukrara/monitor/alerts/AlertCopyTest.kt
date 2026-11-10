// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.MuteLine
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.alerts.TreatmentKind
import app.mauri.sukrara.core.model.DurationWords
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every Android case of contracts/alerts/copy.json in en and es, from the strings (`:core:designsystem`'s mute lines,
 * this module's alert texts): the mute / remind-less lines ([MuteText]), the notification's title and body
 * ([AlertCopy], in mg/dL and mmol/L), its actions and Mute all's scope. The Mac checks the same file.
 */
class AlertCopyTest {
    private fun strings(file: String): Map<String, String> {
        val nodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File(file))
                .getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            nodes
                .item(i)
                .attributes
                .getNamedItem("name")
                .nodeValue to nodes.item(i).textContent
        }
    }

    /** The mute lines' and this module's strings, and THE duration formats from `:core:graph`, in one language folder. */
    private fun words(dir: String): AlertWords {
        val own = strings("src/main/res/$dir/strings.xml") + strings("../core/designsystem/src/main/res/$dir/strings.xml")
        val graph = strings("../core/graph/src/main/res/$dir/strings.xml")
        return object : AlertWords {
            override fun get(name: String) = own[name] ?: error("missing string $name in $dir")

            override val durations =
                DurationWords(
                    graph.getValue("duration_minutes"),
                    graph.getValue("duration_hours"),
                    graph.getValue("duration_hours_minutes"),
                    graph.getValue("duration_days"),
                    graph.getValue("duration_text_minutes"),
                    graph.getValue("duration_text_hours"),
                    graph.getValue("duration_text_hours_minutes"),
                )
        }
    }

    private val en = words("values")
    private val es = words("values-es")

    private companion object {
        /** When the copy.json bodies fire (a whole minute). */
        const val AT = 1_000_020L

        /** copy.json's notification actions → this module's strings. */
        val ACTIONS =
            mapOf(
                "mute" to "alert_action_mute",
                "remind_less" to "alert_action_remind_less",
                "remind_as_usual" to "alert_action_remind_usual",
                "unmute" to "alert_action_unmute",
                "change_time" to "alert_action_change",
                "mute_input" to "alert_action_mute_for_label",
            )
    }

    private fun side(o: JsonObject) = Side.entries.first { it.wire == o.str("side") }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    private fun JsonObject.long(k: String) = getValue(k).jsonPrimitive.int.toLong()

    private fun lineOf(c: JsonObject): MuteLine =
        when (c.str("state")) {
            "side_muted" -> {
                MuteLine.SideMuted(side(c), c.long("leftMin"))
            }

            "side_after" -> {
                MuteLine.SideAfter(
                    side(c),
                    if (c.str("treatment") ==
                        "carbs"
                    ) {
                        TreatmentKind.CARBS
                    } else {
                        TreatmentKind.RAPID
                    },
                    c.long("leftMin"),
                )
            }

            "side_falling" -> {
                MuteLine.SideFalling(side(c))
            }

            "side_less" -> {
                MuteLine.SideLess(side(c), c.long("intervalMin").toInt())
            }

            "all_timed" -> {
                MuteLine.AllTimed(c.long("leftMin"))
            }

            "all_forever" -> {
                MuteLine.AllForever
            }

            "notif_title" -> {
                MuteLine.SideMuted(side(c), null)
            }

            "notif_text" -> {
                MuteLine.NotifUntil(0)
            }

            "night" -> {
                MuteLine.NightUntil(0)
            }

            "pill" -> {
                MuteLine.Pill(c.long("leftMin"))
            }

            "pill_forever" -> {
                MuteLine.PillForever
            }

            "choice" -> {
                MuteLine.Choice(c.long("min").toInt())
            }

            "choice_forever" -> {
                MuteLine.ChoiceForever
            }

            else -> {
                error("unknown state in copy.json: $c")
            }
        }

    @Test fun everyCaseMatchesInEnglishAndSpanish() {
        val cases =
            Json
                .parseToJsonElement(Contracts.file("alerts/copy.json").readText())
                .jsonObject
                .getValue("cases")
                .jsonArray
        var checked = 0
        for (c in cases.map { it.jsonObject }) {
            val enText = word(c, en, Locale.ENGLISH) ?: continue
            assertEquals("en $c", c.str("en"), enText)
            assertEquals("es $c", c.str("es"), word(c, es, Locale.forLanguageTag("es")))
            checked++
        }
        assertTrue("every Android case", checked >= 50)
    }

    /** [c] worded on Android in [w] (numbers in [locale]), or null: a case of the Mac's alone. */
    private fun word(
        c: JsonObject,
        w: AlertWords,
        locale: Locale,
    ): String? {
        if (c["platform"]?.jsonPrimitive?.content == "mac" || c["device"]?.jsonPrimitive?.content == "mac") return null
        val clock = { _: Long -> c["until"]?.jsonPrimitive?.content ?: "" }

        fun kind() = AlertKind.fromWire(c.str("kind"))!!
        return when (c.str("state")) {
            "headline" -> {
                AlertCopy.headline(kind(), c.long("treated").toInt(), w)
            }

            "body" -> {
                val reading =
                    c["mgdl"]?.let {
                        AlertReading(
                            AT - (c["ageMin"]?.jsonPrimitive?.int ?: 0) * 60L,
                            it.jsonPrimitive.int,
                            c.rate(),
                        )
                    }
                val f = Firing(kind(), "e1", 1, reading, if (kind() == AlertKind.NO_DATA) 20.0 else 70.0, AT)
                AlertCopy.body(f, w, if (c.str("unit") == "mmol") GlucoseUnit.MMOL else GlucoseUnit.MGDL, locale)
            }

            "action" -> {
                w.get(ACTIONS.getValue(c.str("action")))
            }

            "mute_choice_spoken" -> {
                w.get("alert_mute_choice_a11y").format(MuteText.sideName(side(c), w), Durations.text(c.long("min"), w.durations))
            }

            "relaxed" -> {
                w.get("alert_relaxed_subtext").format(Durations.text(c.long("intervalMin"), w.durations))
            }

            "test_title" -> {
                w.get("alert_test_title").format(w.get("alert_headline_${c.str("kind")}"))
            }

            "all_scope" -> {
                w.get("mute_all_scope")
            }

            else -> {
                MuteText.line(lineOf(c), w, clock)
            }
        }
    }

    private fun JsonObject.rate(): Double? = get("rate")?.jsonPrimitive?.content?.toDouble()

    private fun firing(kind: AlertKind, reading: AlertReading?) =
        Firing(kind = kind, id = "e1", n = 1, reading = reading, threshold = 70.0, at = 1_000_000)

    @Test fun anAlertWithoutAReadingNeverShowsAMadeUpValue() {
        for (kind in listOf(AlertKind.LOW, AlertKind.VERY_LOW, AlertKind.HIGH, AlertKind.FALLING_FAST, AlertKind.RISING_FAST)) {
            assertEquals("en $kind", "No recent reading", AlertCopy.body(firing(kind, null), en, GlucoseUnit.MGDL))
            assertEquals("es $kind", "Sin lectura reciente", AlertCopy.body(firing(kind, null), es, GlucoseUnit.MGDL))
        }
    }

    @Test fun aLevelAlertShowsTheValueAndArrowAndTheFastOnesTheRate() {
        val r = AlertReading(t = 1_000_000, mgdl = 69, rate = -2.4)
        assertTrue(AlertCopy.body(firing(AlertKind.LOW, r), en, GlucoseUnit.MGDL).startsWith("69 mg/dL "))
        assertTrue(AlertCopy.body(firing(AlertKind.FALLING_FAST, r), en, GlucoseUnit.MGDL).endsWith(" · falling 2.4 mg/dL/min"))
        assertEquals(
            "no rate: no made-up 0.0",
            AlertCopy.body(firing(AlertKind.LOW, r.copy(rate = null)), en, GlucoseUnit.MGDL),
            AlertCopy.body(firing(AlertKind.FALLING_FAST, r.copy(rate = null)), en, GlucoseUnit.MGDL),
        )
    }

    @Test fun loAndHiAreNeverTheirStoredNumber() {
        val lo = AlertReading(t = 1_000_000, mgdl = 39)
        val hi = AlertReading(t = 1_000_000, mgdl = 401)
        assertTrue(AlertCopy.body(firing(AlertKind.VERY_LOW, lo), en, GlucoseUnit.MGDL).startsWith("LO · below 40 mg/dL"))
        assertTrue(AlertCopy.body(firing(AlertKind.EXTREMELY_HIGH, hi), es, GlucoseUnit.MGDL).startsWith("HI · por encima de 400 mg/dL"))
        assertTrue(AlertCopy.body(firing(AlertKind.NO_DATA, lo), en, GlucoseUnit.MGDL).endsWith("last LO · below 40 mg/dL"))
        assertTrue("39" !in AlertCopy.body(firing(AlertKind.VERY_LOW, lo), en, GlucoseUnit.MGDL))
    }

    /** contracts/settings/units.json: the value, its bound and the rate in mmol/L, the language's decimal separator. */
    @Test fun inMmolPerLitreEveryNumberIsInTheUnit() {
        val r = AlertReading(t = 1_000_000, mgdl = 69, rate = -2.4)
        val mmol = GlucoseUnit.MMOL
        assertTrue(AlertCopy.body(firing(AlertKind.LOW, r), en, mmol, Locale.ENGLISH).startsWith("3.8 mmol/L "))
        assertTrue(AlertCopy.body(firing(AlertKind.LOW, r), es, mmol, Locale.forLanguageTag("es")).startsWith("3,8 mmol/L "))
        assertTrue(
            AlertCopy.body(firing(AlertKind.FALLING_FAST, r), en, mmol, Locale.ENGLISH).endsWith(" · falling 0.13 mmol/L/min"),
        )
        val lo = AlertReading(t = 1_000_000, mgdl = 39)
        val hi = AlertReading(t = 1_000_000, mgdl = 401)
        assertTrue(AlertCopy.body(firing(AlertKind.VERY_LOW, lo), en, mmol, Locale.ENGLISH).startsWith("LO · below 2.2 mmol/L"))
        assertTrue(
            AlertCopy
                .body(firing(AlertKind.EXTREMELY_HIGH, hi), es, mmol, Locale.forLanguageTag("es"))
                .startsWith("HI · por encima de 22,2 mmol/L"),
        )
    }

    @Test fun spanishAndEnglishHaveTheSameKeys() {
        fun keys(dir: String): Set<String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length)
                .map {
                    nodes
                        .item(it)
                        .attributes
                        .getNamedItem("name")
                        .nodeValue
                }.toSet()
        }
        assertEquals(keys("values"), keys("values-es"))
    }
}
