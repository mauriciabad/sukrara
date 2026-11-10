// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.integrations.CurrentSensor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Sukrara → Nightscout documents, pinned to what the readers need: `date` integer ms (LoopFollow), `dateString`,
 * `sgv` / `noise` integers, `direction` only from Nightscout's enum (`NONE` when unknown: Trio drops a whole batch on an
 * unknown string), a 5-minute `delta` (Home Assistant). `identifier` = Nightscout's own v5 UUID of `device_date` for
 * entries; the treatment's id for treatments.
 */
object NsDocs {
    const val APP = "Sukrara"
    private val NS_NAMESPACE = "NightscoutRocks!".toByteArray(Charsets.US_ASCII)
    private val UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)
    private val LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    /** An untested family's name as downstream apps see it ("Dexcom G7 (untested)"); null for a verified source. */
    fun untested(source: SourceId): String? =
        when (source) {
            SourceId.DexcomG7 -> "Dexcom G7 (untested)"
            SourceId.DexcomG6 -> "Dexcom G6 (untested)"
            SourceId.SmartGuide -> "Accu-Chek SmartGuide (untested)"
            SourceId.AidexX -> "Aidex X (untested)"
            else -> null
        }

    /**
     * An entry's `device`: this install's name, plus the family for an untested one (`Sukrara-1a2b3c · Dexcom G7
     * (untested)`), so a downstream app can tell those readings apart. [NightscoutQueue.ours] still knows both.
     */
    fun entryDevice(
        device: String,
        source: SourceId,
    ): String = untested(source)?.let { "$device$FAMILY_SEPARATOR$it" } ?: device

    /** The sensor family the Sensor Change / Start notes name. */
    fun family(source: SourceId): String =
        untested(source) ?: "Libre 3"

    const val FAMILY_SEPARATOR = " · "

    /** Nightscout's trend name for a per-minute rate: the card's arrow band (contracts/graph/trend `nightscout`). */
    fun direction(ratePerMin: Double?): String = TrendAngle.nightscout(ratePerMin)

    fun isoUtc(t: Instant): String = UTC.format(t)

    fun isoLocal(
        t: Instant,
        zone: ZoneId,
    ): String = LOCAL.format(t.atZone(zone))

    fun utcOffsetMin(
        t: Instant,
        zone: ZoneId,
    ): Int = zone.rules.getOffset(t).totalSeconds / 60

    /** RFC 4122 v5 UUID, as node's `uuid.v5(name, namespace)`. */
    fun uuidV5(
        name: String,
        namespace: ByteArray = NS_NAMESPACE,
    ): String {
        val h = MessageDigest.getInstance("SHA-1").digest(namespace + name.toByteArray(Charsets.UTF_8))
        h[6] = ((h[6].toInt() and 0x0f) or 0x50).toByte()
        h[8] = ((h[8].toInt() and 0x3f) or 0x80).toByte()
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (h[i].toLong() and 0xff)
        for (i in 8 until 16) lsb = (lsb shl 8) or (h[i].toLong() and 0xff)
        return UUID(msb, lsb).toString()
    }

    /** What the server computes for a document without an identifier (`calculateIdentifier`). */
    fun serverIdentifier(
        device: String,
        dateMs: Long,
    ): String = uuidV5("${device}_$dateMs")

    fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }

    /**
     * [prev]: the reading before [r], for the rate when the sensor gives none. v1 derives `utcOffset` from
     * `dateString`'s offset, v3 takes `utcOffset` as given: local time on v1, UTC on v3, the same stored document.
     */
    fun entry(
        r: Reading,
        prev: Reading?,
        device: String,
        zone: ZoneId,
        api: NsApi,
    ): JsonObject {
        val ms = r.t.toEpochMilli()
        // no slope from or to a LO / HI (its 39 / 401 is a flag, not a value): no made-up direction for loop apps
        val rate =
            r.rateMgdlPerMin ?: prev?.takeUnless { r.isLo || r.isHi || it.isLo || it.isHi }?.let { p ->
                val dtMin = (r.t.epochSecond - p.t.epochSecond) / 60.0
                if (dtMin in 0.5..15.0) (r.mgdl - p.mgdl) / dtMin else null
            }
        return buildJsonObject {
            put("type", "sgv")
            put("sgv", r.mgdl)
            put("date", ms)
            put("dateString", if (api == NsApi.V1) isoLocal(r.t, zone) else isoUtc(r.t))
            put("utcOffset", utcOffsetMin(r.t, zone))
            put("direction", direction(rate))
            if (rate != null) put("delta", (rate * 5 * 1000).roundToLong() / 1000.0)
            put("noise", 1)
            put("device", device)
            put("app", APP)
            put("identifier", serverIdentifier(device, ms))
        }
    }

    /**
     * Rapid → `Correction Bolus` + `insulin`; carbs → `Carb Correction` + `carbs`; note → `Note`. Long-acting → `Note`
     * with the dose only in `notes`: Nightscout's IOB and LoopFollow count every `insulin` field as a bolus.
     */
    fun treatment(
        t: Treatment,
        device: String,
        zone: ZoneId,
        enteredBy: String = APP,
    ): JsonObject =
        buildJsonObject {
            put("identifier", t.id)
            put("created_at", isoUtc(t.at))
            put("date", t.at.toEpochMilli())
            put("utcOffset", utcOffsetMin(t.at, zone))
            put("enteredBy", enteredBy)
            put("app", APP)
            put("device", device)
            when (t.kind) {
                TreatmentKind.RapidInsulin -> {
                    put("eventType", "Correction Bolus")
                    put("insulin", t.amount)
                    put("insulinType", "rapid-acting")
                }

                TreatmentKind.LongInsulin -> {
                    put("eventType", "Note")
                    put("insulinType", "long-acting")
                }

                TreatmentKind.Carbs -> {
                    put("eventType", "Carb Correction")
                    put("carbs", t.amount)
                }

                TreatmentKind.Note -> {
                    put("eventType", "Note")
                }
            }
            val long = if (t.kind == TreatmentKind.LongInsulin) "Long-acting insulin ${units(t.amount)} U" else null
            val notes = listOfNotNull(long, t.note.ifBlank { null })
            if (notes.isNotEmpty()) put("notes", notes.joinToString(" · "))
        }

    /**
     * Sensor age: LoopFollow counts only `Sensor Start`, nightguard only `Sensor Change`, Nightscout's SAGE the newest of
     * both. A Libre 3 is inserted and started in one tap: both go up with the same time and their own identifiers.
     */
    fun sensorEvents(
        s: CurrentSensor,
        device: String,
        zone: ZoneId,
    ): List<JsonObject> =
        listOf("Sensor Change", "Sensor Start").map { type ->
            buildJsonObject {
                put("identifier", uuidV5("sukrara-sensor_${s.serial}_$type"))
                put("eventType", type)
                put("created_at", isoUtc(s.start))
                put("date", s.start.toEpochMilli())
                put("utcOffset", utcOffsetMin(s.start, zone))
                put("enteredBy", APP)
                put("app", APP)
                put("device", device)
                put("notes", "${family(s.source)} ${s.serial}")
            }
        }

    fun deviceStatus(
        percent: Int,
        charging: Boolean,
        at: Instant,
        device: String,
        zone: ZoneId,
    ): JsonObject =
        buildJsonObject {
            put("device", device)
            put("created_at", isoUtc(at))
            put("date", at.toEpochMilli())
            put("utcOffset", utcOffsetMin(at, zone))
            put("app", APP)
            put("identifier", serverIdentifier(device, at.toEpochMilli()))
            put(
                "uploader",
                buildJsonObject {
                    put("battery", percent)
                    put("isCharging", charging)
                },
            )
        }

    /**
     * The site's document that already is [t], uploaded by another app (AndroidAPS, xDrip+, a pump's app, Nightscout's
     * careportal), as `<_id>:<field>`; null when there is none. Nightscout merges a new treatment only on its
     * `identifier` / `_id`, or for a document without either on time + eventType: ours always carries its own identifier,
     * so the site would keep both and count the insulin / carbs twice (IOB / COB, and every loop reading the site).
     *
     * The same = within [NightscoutQueue.SAME_TREATMENT_S] of [t] and the same amount in the field Nightscout counts,
     * whatever the eventType (AndroidAPS writes `Meal Bolus`, `Correction Bolus`, `SMB`…, xDrip+ often `<none>`): rapid
     * insulin ↔ `insulin` (±0.01 U), carbs ↔ `carbs` (±0.5 g); a note or long-acting dose ↔ a `Note` with the same text.
     * Never ours (our identifier, or `app` / `enteredBy` Sukrara: another Sukrara entry is its own), never a soft-deleted
     * one (`isValid: false`), never one in [taken] (each of their documents stands for one entry of ours per field, so a
     * `Meal Bolus` covers one insulin and one carbs entry).
     */
    fun sameTreatment(
        t: Treatment,
        docs: List<JsonObject>,
        taken: Set<String>,
    ): String? {
        val field =
            when (t.kind) {
                TreatmentKind.RapidInsulin -> "insulin"
                TreatmentKind.Carbs -> "carbs"
                TreatmentKind.LongInsulin, TreatmentKind.Note -> "notes"
            }
        val ourNotes = treatment(t, "", ZoneOffset.UTC)["notes"]?.jsonPrimitive?.contentOrNull
        return docs
            .asSequence()
            .filter { d ->
                val id = d.text("_id")
                id != null && "$id:$field" !in taken &&
                    d.text("identifier") != t.id && d.text("app") != APP && d.text("enteredBy") != APP &&
                    d["isValid"]?.jsonPrimitive?.booleanOrNull != false
            }.filter { d -> d.time()?.let { abs(it.epochSecond - t.at.epochSecond) <= NightscoutQueue.SAME_TREATMENT_S } == true }
            .filter { d ->
                when (t.kind) {
                    TreatmentKind.RapidInsulin -> d.number("insulin")?.let { abs(it - t.amount) <= 0.01 } == true
                    TreatmentKind.Carbs -> d.number("carbs")?.let { abs(it - t.amount) <= 0.5 } == true
                    else -> d.text("eventType") == "Note" && ourNotes != null && d.text("notes")?.trim() == ourNotes
                }
            }.minByOrNull { d -> abs(d.time()!!.epochSecond - t.at.epochSecond) }
            ?.let { "${it.text("_id")}:$field" }
    }

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(k: String) = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    /** `created_at` (any ISO offset), else `date` / `mills` ms. */
    private fun JsonObject.time(): Instant? =
        text("created_at")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
            ?: (number("date") ?: number("mills"))?.let { Instant.ofEpochMilli(it.toLong()) }

    private fun units(a: Double): String = if (a == Math.floor(a)) a.toLong().toString() else String.format(Locale.ROOT, "%.1f", a)
}
