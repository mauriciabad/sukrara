// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.libreview

import app.mauri.sukrara.core.model.MaintenanceWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * LibreView's maintenance windows from Abbott's public status page (contracts/sources/librelinkup/status.json, docs/45):
 * an Atlassian Statuspage, no key and no account. [fetch] is one GET (https only, redirects never followed, nothing of
 * the person's sent); the rules are pure in the companion, the same fixtures on both apps. A 911 from LibreLinkUp is
 * what says maintenance: the page only adds its times.
 */
class LibreViewStatus(
    http: OkHttpClient,
    /** The page's summary; a debug build's `LIBRELINKUP` fake serves its own (loopback). */
    private val url: String = URL,
) {
    private val client =
        http
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    /** The summary's windows for LibreLinkUp's [region], or null when the page didn't answer (nothing is said then). */
    fun fetch(region: String?): List<MaintenanceWindow>? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
        return try {
            client.newCall(request).execute().use { r ->
                if (r.code != 200) return null
                val body = parse(r.body?.string().orEmpty()) ?: return null
                // an answer that isn't the page's says nothing either
                if (body["scheduled_maintenances"] !is JsonArray) return null
                windows(body, region)
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** status.json `url`. */
        const val URL = "https://status.freestyle.abbott/api/v2/summary.json"

        /** status.json `every_s`: the windows ahead, while LibreLinkUp is a signed-in source. */
        const val EVERY_S = 21600L

        /** status.json `maintenanceEvery_s`: the window's end, while in it. */
        const val MAINTENANCE_EVERY_S = 600L

        /** status.json `warnHome_s`: home says a window ahead from this long before its start. */
        const val WARN_HOME_S = 43200L

        /** status.json `notify_s`: the one heads-up notification, this long before its start. */
        const val NOTIFY_S = 3600L
        private const val TIMEOUT_S = 15L

        /** LibreLinkUp's region -> the page's region components (status.json `regions`). */
        val REGIONS: Map<String, List<String>> =
            mapOf(
                "us" to listOf("United States"),
                "eu" to listOf("Europe, Middle East, Africa"),
                "eu2" to listOf("UK & Ireland"),
                "de" to listOf("Germany"),
                "fr" to listOf("France"),
                "jp" to listOf("Japan"),
                "ap" to listOf("Other Asia"),
                "au" to listOf("Australia, New Zealand"),
                "ae" to listOf("United Arab Emirates"),
                "ca" to listOf("Canada"),
                "la" to listOf("Latin America"),
                "ru" to listOf("Russia"),
                "cn" to listOf("China"),
            )

        /** The component groups that are LibreLinkUp's and LibreView's servers (status.json `groups`). */
        val GROUPS = setOf("LibreLinkUp Mobile App", "LibreView Web App")
        private val LIVE = setOf("in_progress", "verifying")
        private val json = Json { ignoreUnknownKeys = true }

        private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

        /** A status page time (`2026-11-18T13:00:00.000-08:00`, or `Z`), null when it isn't one. */
        fun time(text: String?): Instant? =
            text?.trim()?.takeIf { ISO.matches(it) }?.let {
                try {
                    OffsetDateTime.parse(it).toInstant()
                } catch (e: DateTimeParseException) {
                    null
                }
            }

        private val ISO = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})")

        /** The windows the page ([body]) lists for LibreLinkUp's [region] (null = not known: none), by start. */
        fun windows(
            body: JsonObject,
            region: String?,
        ): List<MaintenanceWindow> {
            val names = region?.let { REGIONS[it] }.orEmpty()
            if (names.isEmpty()) return emptyList()
            val groups =
                (body["components"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { it.bool("group") == true }
                    .associate { it.text("id") to it.text("name") }
            return (body["scheduled_maintenances"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { m ->
                    val status = m.text("status")
                    if (status != "scheduled" && status !in LIVE) return@mapNotNull null
                    val start = time(m.text("scheduled_for")) ?: return@mapNotNull null
                    val end = time(m.text("scheduled_until")) ?: return@mapNotNull null
                    if (!end.isAfter(start)) return@mapNotNull null
                    val ours =
                        (m["components"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.any { c ->
                            c.text("name") in names && groups[c.text("group_id")] in GROUPS
                        }
                    if (ours) MaintenanceWindow(start, end, status in LIVE) else null
                }.sortedWith(compareBy({ it.start }, { it.end }))
        }

        /**
         * The end of the window LibreView is in at [now] (marked in progress, or its start has come), the latest of
         * several; null = none known (none, or one past its planned end: no time is made up).
         */
        fun until(
            windows: List<MaintenanceWindow>,
            now: Instant,
        ): Instant? = windows.filter { now.isBefore(it.end) && (it.live || !it.start.isAfter(now)) }.maxOfOrNull { it.end }

        /** The next window that hasn't begun (the warn-ahead), null = none listed. */
        fun upcoming(
            windows: List<MaintenanceWindow>,
            now: Instant,
        ): MaintenanceWindow? = windows.firstOrNull { it.start.isAfter(now) && !it.live }

        private fun JsonObject.prim(k: String): JsonPrimitive? = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }

        private fun JsonObject.text(k: String): String? = prim(k)?.contentOrNull

        private fun JsonObject.bool(k: String): Boolean? = prim(k)?.booleanOrNull
    }
}
