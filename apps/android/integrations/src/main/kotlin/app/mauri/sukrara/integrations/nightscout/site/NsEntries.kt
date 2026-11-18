// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.integrations.nightscout.NsDocs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.floor

/**
 * A Nightscout site's glucose entries → the readings Sukrara stores (contracts/sources/nightscout/entries.json): glucose
 * entries only, the time from `date` / `mills` / `dateString`, the rate from Nightscout's 5-minute `delta`, and never
 * an entry this install uploaded itself ([ownDevice], docs/44 "Data" rule 4).
 */
object NsEntries {
    private val json = Json { ignoreUnknownKeys = true }

    /** The readings of [body] (an entries array), oldest first, one per time; an unreadable body throws. */
    fun parse(
        body: String,
        ownDevice: String?,
    ): List<Reading> {
        val arr = json.parseToJsonElement(body) as JsonArray
        val byTime = LinkedHashMap<Long, Reading>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val type = o.text("type")
            if (type != null && type != "sgv") continue
            val device = o.text("device")
            if (ownDevice != null && device != null &&
                (device == ownDevice || device.startsWith(ownDevice + NsDocs.FAMILY_SEPARATOR))
            ) {
                continue
            }
            val sgv = o.number("sgv") ?: continue
            val t = o.time() ?: continue
            val rate = o.number("delta")?.let { it / 5.0 }
            byTime.putIfAbsent(t.epochSecond, Reading(t, floor(sgv + 0.5).toInt(), rate, SourceId.Nightscout))
        }
        return byTime.values.sortedBy { it.t }
    }

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

    private fun JsonObject.number(k: String): Double? {
        val p = this[k] as? JsonPrimitive ?: return null
        if (p == JsonNull) return null
        return (p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull())?.takeIf { it.isFinite() }
    }

    private fun JsonObject.time(): Instant? {
        for (k in listOf("date", "mills")) number(k)?.let { return Instant.ofEpochMilli(it.toLong()) }
        return text("dateString")?.let { s -> runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull() }
    }
}
