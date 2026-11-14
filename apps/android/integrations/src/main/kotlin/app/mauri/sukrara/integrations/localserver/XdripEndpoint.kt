// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.localserver

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.integrations.CurrentSensor
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The xDrip+ "local web service" answers, byte-compatible with Juggluco's server (field order and number formats), which
 * is what Garmin Connect IQ glucose apps (CGM+ data fields, CGM Connect, …) poll through Garmin Connect. Pure: the
 * readings (newest first, the last 24 h) and the sensor come in, the JSON goes out.
 *
 * Paths: `sgv.json`, `api/v1/entries/sgv.json`, `api/v1/entries.json`, `api/v1/entries` (`count`, `interval`,
 * `brief_mode`, `sensor`), `pebble`, `status.json`, `api/v1/status.json`.
 */
object XdripEndpoint {
    const val PORT = 17580
    const val DEVICE = "Sukrara"
    private const val SPACING_S = 270L
    private const val STATUS = "{\"settings\":{\"units\":\"mg/dL\",\"thresholds\":{\"bgHigh\":185,\"bgLow\":65}}}"
    private val UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)
    private val LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private val STATUS_TIME = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm", Locale.US)

    fun route(
        req: HttpRequest,
        newestFirst: List<Reading>,
        sensor: CurrentSensor?,
        now: Instant,
        zone: ZoneId,
    ): HttpAnswer =
        when (req.path) {
            "sgv.json", "api/v1/entries/sgv.json", "api/v1/entries.json", "api/v1/entries" -> {
                HttpAnswer("200 OK", sgv(req.query, newestFirst, sensor, now, zone, utc = req.path.startsWith("api/")))
            }

            "pebble" -> {
                HttpAnswer("200 OK", pebble(newestFirst, now))
            }

            "status.json", "api/v1/status.json" -> {
                HttpAnswer("200 OK", STATUS)
            }

            else -> {
                HttpAnswer.NOT_FOUND
            }
        }

    /** Newest first, at least [minGap] s apart (readings jitter by a second or two: an ask for 300 allows 30 s slack). */
    private fun spaced(
        all: List<Reading>,
        count: Int,
        minGap: Long = SPACING_S,
    ): List<Reading> {
        val out = ArrayList<Reading>()
        for (r in all) {
            if (out.isNotEmpty() && out.last().t.epochSecond - r.t.epochSecond < minGap) continue
            out += r
            if (out.size >= count) break
        }
        return out
    }

    /** mg/dL per 5 minutes: THE trend's rate (contracts/trend), null without one (never a made-up 0). */
    private fun delta(r: Reading): Double? = r.rate?.times(5)

    private fun flag(v: String?) = v?.let { it.isEmpty() || it[0] !in "fFnN0" } ?: false

    private fun sgv(
        q: Map<String, String>,
        all: List<Reading>,
        sensor: CurrentSensor?,
        now: Instant,
        zone: ZoneId,
        utc: Boolean,
    ): String {
        // Monkey C apps send floats ("count=72.0"); Juggluco's parser stops at the '.', so do the same
        val count = (q["count"]?.toDoubleOrNull()?.toInt() ?: 24).coerceIn(1, 1000)
        val minGap = q["interval"]?.toDoubleOrNull()?.toLong()?.let { (it - minOf(30, it / 10)).coerceAtLeast(0) } ?: SPACING_S
        val brief = flag(q["brief_mode"])
        val withSensor = flag(q["sensor"])
        val list = spaced(all, count, minGap)
        return list
            .mapIndexed { i, r ->
                val d = delta(r)
                buildString {
                    append('{')
                    if (!brief) {
                        val ds = if (utc) UTC.format(r.t) else LOCAL.format(r.t.atZone(zone))
                        append("\"_id\":\"${sensor?.serial ?: DEVICE}#${r.t.epochSecond / 60}\",\"device\":\"$DEVICE\",")
                        append("\"dateString\":\"$ds\",\"sysTime\":\"$ds\",")
                    }
                    append("\"date\":${r.t.epochSecond}000,\"sgv\":${r.mgdl},")
                    // no trend: no delta at all (a 0 would read as flat on a watch) and direction NONE
                    d?.let { append("\"delta\":${String.format(Locale.US, "%.3f", it)},") }
                    append("\"direction\":\"${direction(r)}\",\"noise\":1")
                    if (!brief) append(",\"filtered\":${r.mgdl}000,\"unfiltered\":${r.mgdl}000,\"rssi\":100,\"type\":\"sgv\"")
                    if (i == 0) {
                        append(",\"units_hint\":\"mgdl\"")
                        if (withSensor && sensor != null) append(",\"sensor_status\":\"${sensorStatus(sensor, now, zone)}\"")
                    }
                    append('}')
                }
            }.joinToString(",\n", "[", "]\n")
    }

    private fun pebble(
        all: List<Reading>,
        now: Instant,
    ): String {
        val r = spaced(all, 1).firstOrNull()
        val bgs =
            r
                ?.let {
                    val d = delta(it)
                    "{\"sgv\":\"${it.mgdl}\",\"trend\":${trend(
                        it,
                    )},\"direction\":\"${direction(it)}\",\"datetime\":${it.t.epochSecond}000," +
                        d?.let { v -> "\"bgdelta\":\"${String.format(Locale.US, "%.2f", v)}\"," }.orEmpty() + "\"iob\":\"0.00\"}"
                }.orEmpty()
        return "{\"status\":[{\"now\":${now.toEpochMilli()}}],\"bgs\":[$bgs],\"cals\":[]}"
    }

    /** "dd-mm-yy HH:MM (Xd Yh)": sensor start and age, as Juggluco reports it. */
    private fun sensorStatus(
        s: CurrentSensor,
        now: Instant,
        zone: ZoneId,
    ): String {
        val age = now.epochSecond - s.start.epochSecond
        return "${STATUS_TIME.format(s.start.atZone(zone))} (${age / 86400}d ${age % 86400 / 3600}h)"
    }

    /** The Nightscout direction of [r]'s trend: its text band, as every arrow shows it (contracts/trend). */
    fun direction(r: Reading): String = TrendAngle.nightscout(r.trend?.band)

    private fun trend(r: Reading): Int =
        when (direction(r)) {
            "DoubleUp" -> 1
            "SingleUp" -> 2
            "FortyFiveUp" -> 3
            "Flat" -> 4
            "FortyFiveDown" -> 5
            "SingleDown" -> 6
            "DoubleDown" -> 7
            else -> 0
        }
}
