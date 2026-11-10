// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsCodecTest {
    /** The JSON block of contracts/alerts/SPEC.md §3: the defaults, written by hand in the spec. */
    private val specJson: String =
        Contracts
            .file("alerts/SPEC.md")
            .readText()
            .substringAfter("## 3. Settings")
            .substringAfter("```json")
            .substringBefore("```")

    @Test fun defaultsAreTheSpecsDefaults() {
        assertEquals(AlertSettings(), AlertSettingsCodec.decode(specJson))
        assertEquals(Json.parseToJsonElement(specJson), Json.parseToJsonElement(AlertSettingsCodec.encode(AlertSettings())))
    }

    @Test fun roundTrips() {
        val s =
            AlertSettings(
                on = false,
                rules = AlertSettings.DEFAULT_RULES + (AlertKind.FALLING_FAST to KindRule(true, 2.5, 0, alarm = true)),
                afterInsulin = QuietRule(false, 90),
                sounds = SoundSettings("arcade", mapOf(AlertKind.LOW to "soft"), shuffle = false),
                actions = NotificationActions(45, 60, 5, 15),
                persistence = Persistence(lowMin = 10, highMin = 30, marginMgdl = 5),
                source = AlertSource.OWN,
            )
        assertEquals(s, AlertSettingsCodec.decode(AlertSettingsCodec.encode(s)))
    }

    /** #38: the night set is read like the day's (each field at its default when missing), very low always on. */
    @Test fun nightIsReadAndVeryLowStaysOn() {
        val s =
            AlertSettingsCodec.decode(
                """{"v":1,"night":{"on":true,"from":1380,"to":2000,"alerts":{"very_low":{"on":false,"mgdl":60},"high":{"mgdl":260,"repeatMin":60}}}}""",
            )!!
        assertEquals(true, s.night.on)
        assertEquals(1380, s.night.fromMin)
        assertEquals(NightProfile.DEFAULT_TO, s.night.toMin)
        assertEquals(KindRule(true, 60.0, 5, true), s.night.rule(AlertKind.VERY_LOW))
        assertEquals(KindRule(true, 260.0, 60, false), s.night.rule(AlertKind.HIGH))
        assertEquals(AlertSettings.DEFAULT_RULES[AlertKind.LOW], s.night.rule(AlertKind.LOW))
        assertEquals(s, AlertSettingsCodec.decode(AlertSettingsCodec.encode(s)))
    }

    /** #38: the window, also across midnight, and when the rules switch next. */
    @Test fun nightWindow() {
        val utc = java.time.ZoneOffset.UTC

        fun at(h: Int, m: Int, day: Int = 0) = 1_759_968_000L + day * 86_400 + h * 3600 + m * 60
        var n = NightProfile(on = true)
        assertTrue(n.active(at(23, 0), utc) && n.active(at(6, 59), utc))
        assertFalse(n.active(at(7, 0), utc) || n.active(at(21, 59), utc))
        assertEquals(at(22, 0), n.nextSwitch(at(21, 0), utc))
        assertEquals(at(7, 0, day = 1), n.nextSwitch(at(23, 0), utc))
        n = n.copy(fromMin = 13 * 60, toMin = 14 * 60)
        assertTrue(n.active(at(13, 30), utc) && !n.active(at(14, 0), utc))
        assertFalse(n.copy(toMin = n.fromMin).active(at(13, 0), utc))
        assertNull(n.copy(toMin = n.fromMin).nextSwitch(at(0, 0), utc))
        val s = AlertSettings(night = n.withRule(AlertKind.HIGH, KindRule(false, 180.0, 15)))
        assertFalse(s.at(at(13, 30), utc).rule(AlertKind.HIGH).on)
        assertTrue(s.at(at(15, 0), utc).rule(AlertKind.HIGH).on)
    }

    /** #37: the waits and the margin are read and coerced (a low may wait at most 15 min); very low never waits. */
    @Test fun persistenceIsReadAndCoerced() {
        val s = AlertSettingsCodec.decode("""{"v":1,"persistence":{"lowMin":40,"highMin":30,"marginMgdl":5}}""")!!
        assertEquals(Persistence(lowMin = 15, highMin = 30, marginMgdl = 5), s.persistence)
        assertEquals(Persistence(), AlertSettingsCodec.decode("""{"v":1}""")!!.persistence)
        val low = AlertReading(0, 65)
        assertEquals(15, s.persistence.waitMin(AlertKind.LOW, low))
        assertEquals(0, s.persistence.waitMin(AlertKind.VERY_LOW, low))
        assertEquals(0, s.persistence.waitMin(AlertKind.LOW, AlertReading(0, 39)))
        assertEquals(0, s.persistence.waitMin(AlertKind.EXTREMELY_HIGH, AlertReading(0, 320)))
        assertEquals(30, s.persistence.waitMin(AlertKind.VERY_HIGH, AlertReading(0, 260)))
    }

    /** A newer peer's payload: unknown keys and kinds are ignored, every known field still read. */
    @Test fun decodesAPeersPayloadLeniently() {
        val wire = """{"v":1,"on":true,"future":{"x":1},"alerts":{"low":{"on":true,"mgdl":75,"repeatMin":10,"alarm":false,"tone":"x"},
            "brand_new_kind":{"on":true,"mgdl":1},"no_data":{"on":false,"min":30,"repeatMin":0}},
            "muteAfterCarbs":{"on":false,"min":20},"sounds":{"pack":"kart","kinds":{"high":"soft","nope":"x"}},"source":"own"}"""
        val s = AlertSettingsCodec.decode(wire)!!
        assertEquals(KindRule(true, 75.0, 10, false), s.rule(AlertKind.LOW))
        assertEquals(KindRule(false, 30.0, 0, false), s.rule(AlertKind.NO_DATA))
        assertEquals(AlertSettings.DEFAULT_RULES[AlertKind.HIGH], s.rule(AlertKind.HIGH))
        assertEquals(QuietRule(false, 20), s.afterCarbs)
        assertEquals(mapOf(AlertKind.HIGH to "soft"), s.sounds.perKind)
        assertEquals(AlertSource.OWN, s.source)
    }

    /** A bad value never switches an alert off: out of range is coerced, a mistyped value falls back to its default. */
    @Test fun badValuesAreCoercedNotDropped() {
        val s =
            AlertSettingsCodec.decode(
                """{"alerts":{"low":{"on":"yes","mgdl":500,"repeatMin":-3},"very_low":{"mgdl":"x"}},"notificationActions":{"muteMin":5000}}""",
            )!!
        assertEquals(KindRule(true, 120.0, 1, false), s.rule(AlertKind.LOW))
        assertEquals(55.0, s.rule(AlertKind.VERY_LOW).threshold, 0.0)
        assertEquals(720, s.actions.muteMin)
        assertEquals(0, AlertSettingsCodec.decode("""{"alerts":{"high":{"repeatMin":0}}}""")!!.rule(AlertKind.HIGH).repeatMin)
    }

    @Test fun notAnObjectIsNull() {
        assertNull(AlertSettingsCodec.decode("[1]"))
        assertNull(AlertSettingsCodec.decode("{"))
    }

    @Test fun limitsMatchTheSettingsSchema() {
        val numbers = Json.parseToJsonElement(Contracts.file("settings/settings.json").readText()).jsonObject["numbers"]!!.jsonObject
        for ((name, limit) in AlertLimits.BY_SCHEMA_NAME) {
            val n = numbers[name]?.jsonObject ?: error("$name missing in settings.json")
            assertEquals(name, n["min"]!!.jsonPrimitive.double, limit.min, 1e-9)
            assertEquals(name, n["max"]!!.jsonPrimitive.double, limit.max, 1e-9)
            assertEquals(name, n["step"]!!.jsonPrimitive.double, limit.step, 1e-9)
            assertEquals(name, n["default"]!!.jsonPrimitive.double, limit.default, 1e-9)
            assertEquals(name, n["presets"]!!.jsonArray.map { it.jsonPrimitive.double }, limit.presets)
            val specials =
                n["special"]
                    ?.jsonObject
                    ?.keys
                    ?.map { it.toDouble() }
                    ?.toSet()
                    .orEmpty()
            assertEquals(name, specials, limit.specials)
        }
    }

    @Test fun mutedByFollowsTheSpec() {
        val s = AlertSettings()
        assertNull(s.mutedBy(AlertKind.EXTREMELY_HIGH))
        assertNull(s.mutedBy(AlertKind.NO_DATA))
        assertEquals(TreatmentKind.CARBS, s.mutedBy(AlertKind.VERY_LOW))
        assertEquals(TreatmentKind.RAPID, s.mutedBy(AlertKind.RISING_FAST))
        assertNull(s.copy(afterCarbs = QuietRule(false, 15)).mutedBy(AlertKind.LOW))
        assertFalse(s.relaxChoices().any { it <= s.relaxFloor() })
    }
}
