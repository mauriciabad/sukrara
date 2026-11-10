// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.HistoryImport
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * Tidepool's data model read back into Sukrara's (Import history's Tidepool download), the inverse of `TidepoolData`.
 *
 * - `cbg` → readings (Tidepool returns mmol/L: × 18.01559, Tidepool's own factor, so what Sukrara sent comes back as
 *   the same mg/dL); its trend rate when it is in mg/dL/minute.
 * - `bolus` (delivered `normal` + `extended`) and `insulin` (`dose.total`, `formulation.simple.actingType` long /
 *   intermediate → long-acting, anything else rapid) → insulin entries; `food` (`nutrition.carbohydrate.net` in grams)
 *   and a bolus calculator's `wizard` `carbInput` (grams) → carbs; a datum's `notes` → the entry's note; Tidepool
 *   notes (message service, not replies) → note entries.
 * - Not read: `smbg` (finger-stick), basal and the rest of a pump's records: Sukrara has no record for them.
 * - Ids: an entry Sukrara sent (`origin.id` `sukrara-tr-<id>`) comes back with its own id; any other gets a stable id
 *   from Tidepool's datum id, so a second import matches it by id.
 */
object TidepoolHistory {
    /** The data types read (one request per window). */
    val TYPES = listOf("cbg", "bolus", "insulin", "food", "wizard")

    const val MGDL_PER_MMOL = 18.01559
    private const val OURS = "sukrara-tr-"
    private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun readings(data: JsonArray): List<Reading> =
        data.mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            if (d.text("type") != "cbg") return@mapNotNull null
            val t = d.time() ?: return@mapNotNull null
            val v = d.number("value") ?: return@mapNotNull null
            val mgdl = if (d.text("units").orEmpty().startsWith("mmol", ignoreCase = true)) v * MGDL_PER_MMOL else v
            val rate = d.number("trendRate")?.takeIf { d.text("trendRateUnits") == "mg/dL/minute" }
            Reading(t, mgdl.roundToInt(), rate, ImportSource.Tidepool.readings)
        }

    fun treatments(data: JsonArray): List<TreatmentRecord> =
        data.flatMap { e ->
            val d = e as? JsonObject ?: return@flatMap emptyList()
            val t = d.time() ?: return@flatMap emptyList()
            when (d.text("type")) {
                "bolus" -> {
                    listOfNotNull(
                        ((d.number("normal") ?: 0.0) + (d.number("extended") ?: 0.0)).takeIf { it > 0 }?.let {
                            entry(d, TreatmentKind.RapidInsulin, it, t)
                        },
                    )
                }

                "insulin" -> {
                    val acting = d.obj("formulation")?.obj("simple")?.text("actingType")
                    val kind = if (acting == "long" || acting == "intermediate") TreatmentKind.LongInsulin else TreatmentKind.RapidInsulin
                    listOfNotNull(
                        d
                            .obj("dose")
                            ?.number("total")
                            ?.takeIf { it > 0 }
                            ?.let { entry(d, kind, it, t) },
                    )
                }

                "food" -> {
                    val carbs = d.obj("nutrition")?.obj("carbohydrate")
                    val grams = carbs?.number("net")?.takeIf { carbs.text("units").let { u -> u == null || u == "grams" } }
                    listOfNotNull(grams?.takeIf { it > 0 }?.let { entry(d, TreatmentKind.Carbs, it, t) })
                }

                "wizard" -> {
                    val grams = d.number("carbInput")?.takeIf { d.text("carbUnits").let { u -> u == null || u == "grams" } }
                    listOfNotNull(grams?.takeIf { it > 0 }?.let { entry(d, TreatmentKind.Carbs, it, t, key = "wizard") })
                }

                else -> {
                    emptyList()
                }
            }
        }

    /** Tidepool notes (`GET /message/notes`): each top-level note with text becomes a note entry. */
    fun notes(messages: JsonArray): List<TreatmentRecord> =
        messages.mapNotNull { e ->
            val m = e as? JsonObject ?: return@mapNotNull null
            if (!m.text("parentmessage").isNullOrEmpty()) return@mapNotNull null
            val text = m.text("messagetext")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val at = m.text("timestamp")?.let(::instant) ?: return@mapNotNull null
            val id = HistoryImport.treatmentId(ImportSource.Tidepool, "note:" + (m.text("id") ?: m.text("guid") ?: "$at|$text"))
            TreatmentRecord(
                Treatment(id, TreatmentKind.Note, 0.0, at, text, at, false, TreatmentOrigin.Phone),
                importedFrom = ImportSource.Tidepool,
            )
        }

    private fun entry(
        d: JsonObject,
        kind: TreatmentKind,
        amount: Double,
        at: Instant,
        key: String = kind.name,
    ): TreatmentRecord {
        val origin = d.obj("origin")?.text("id")
        val ours = origin?.takeIf { it.startsWith(OURS) }?.removePrefix(OURS)?.takeIf { UUID.matches(it) }
        val id = ours ?: HistoryImport.treatmentId(ImportSource.Tidepool, "${d.text("id") ?: origin ?: "$at|$amount"}:$key")
        val note =
            (d["notes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(" ")
                ?.trim()
                .orEmpty()
        val rounded = if (kind == TreatmentKind.Carbs) Math.round(amount * 10) / 10.0 else Math.round(amount * 100) / 100.0
        return TreatmentRecord(
            Treatment(id, kind, rounded, at, note, at, false, TreatmentOrigin.Phone),
            importedFrom = ImportSource.Tidepool,
        )
    }

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(k: String) = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun JsonObject.time(): Instant? = text("time")?.let(::instant)

    private fun instant(s: String): Instant? =
        try {
            Instant.parse(s)
        } catch (e: DateTimeParseException) {
            null
        }
}
