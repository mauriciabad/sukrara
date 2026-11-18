// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.designsystem.rows.nameRes
import app.mauri.sukrara.feature.settings.QuietKind
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.settings.graph.PlaceSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * THE schema against the search (#205, docs/35): every setting of `contracts/settings/settings.json` that has a row on
 * Android is found by the search on a page of its schema `section` (Readings' settings in Readings, the metrics' in
 * Metrics …), and its second places (docs/35 §7: the same setting editable on another page) are found there too. A
 * setting without a row of its own, or one another feature's page draws, is listed with why.
 */
class SchemaSearchTest {
    private fun page(route: SettingsRoute) = SearchTarget.Page(route)

    private val sensor = SearchTarget.Other(OtherPage.Sensor)
    private val source = SearchTarget.Other(OtherPage.Source)
    private val low = AlertKind.LOW

    /** Each schema setting's row: its title and the page it's on. */
    private val rows: Map<String, Pair<Int, SearchTarget>> =
        buildMap {
            put("glucose.trend", R.string.st_trend_balanced to page(SettingsRoute.TrendArrow))
            put("graph.band.veryLow", R.string.st_band_very_low to page(SettingsRoute.Readings))
            put("graph.band.low", R.string.st_band_low to page(SettingsRoute.Readings))
            put("graph.band.tight", R.string.st_band_tight to page(SettingsRoute.Readings))
            put("graph.band.high", R.string.st_band_high to page(SettingsRoute.Readings))
            put("graph.band.veryHigh", R.string.st_band_very_high to page(SettingsRoute.Readings))
            put("graph.band.extremelyHigh", R.string.st_band_extremely_high to page(SettingsRoute.Readings))
            put("general.unit", R.string.st_unit to page(SettingsRoute.Readings))
            put("graph.hours", R.string.st_graph_hours to page(SettingsRoute.Graph))
            put("graph.hours.place", R.string.st_place_notification to page(SettingsRoute.PlacePage(PlaceSetting.Hours)))
            put("graph.side", R.string.st_graph_side to page(SettingsRoute.Graph))
            put("graph.columnCrown", R.string.st_graph_crown to page(SettingsRoute.Graph))
            put("graph.lines", R.string.st_graph_line_add to page(SettingsRoute.Graph))
            put("graph.gridHours", R.string.st_graph_grid to page(SettingsRoute.Graph))
            put("graph.marks", R.string.st_marks_point to page(SettingsRoute.GraphMarks))
            put("graph.slots", R.string.st_metric_main to page(SettingsRoute.Metrics))
            put("graph.ringRange", R.string.st_tir_in_range to page(SettingsRoute.TirRings))
            put("graph.ringOrder", R.string.st_tir_order to page(SettingsRoute.TirRings))
            put("graph.ringMarks", R.string.st_tir_rewards to page(SettingsRoute.TirRings))
            put("graph.last", R.string.st_last to page(SettingsRoute.Metrics))
            put("graph.lastPlace", R.string.st_last_near to page(SettingsRoute.LastTreatment))
            put("graph.lastElse", R.string.st_last_else to page(SettingsRoute.Metrics))
            put("graph.sensorTime", R.string.st_graph_sensor to page(SettingsRoute.SensorEnd))
            put("graph.sensorTime.place", R.string.st_place_notification to page(SettingsRoute.PlacePage(PlaceSetting.SensorTime)))
            put("sensor.countdownHours", R.string.st_sensorend_countdown_hours to page(SettingsRoute.SensorEnd))
            put("sensor.reminders", R.string.st_sensorend_reminders to page(SettingsRoute.SensorEnd))
            put("sensorCalendar.on", R.string.st_calendar_title to page(SettingsRoute.SensorEnd))
            put("sensorCalendar.calendarId", R.string.st_calendar_where to page(SettingsRoute.SensorEnd))
            put("follower.settingsFrom", R.string.st_from_wearer to page(SettingsRoute.SettingsFrom))
            put("update.automaticChecks", R.string.st_update_auto_checks to page(SettingsRoute.About))
            put("update.automaticInstall", R.string.st_update_auto_install to page(SettingsRoute.About))
            put("source.person", R.string.st_source_whose to source)
            put("source.kind", R.string.st_source_from to source)
            put("source.jugglucoSecret", R.string.st_sensor_juggluco_secret to source)
            put("source.nightscoutUrl", R.string.st_source_ns_site to source)
            put("source.nightscoutSecret", R.string.st_source_ns_site to source)
            put("source.nightscoutTreatments", R.string.st_source_ns_treatments to source)
            put("alerts.on", R.string.st_alerts_main to page(SettingsRoute.Alerts))
            put("alerts.kind.on", R.string.st_kind_low to page(SettingsRoute.Alerts))
            for (k in listOf("very_low", "low", "high", "very_high", "extremely_high", "falling_fast", "rising_fast", "no_data")) {
                val kind = checkNotNull(AlertKind.fromWire(k))
                put("alerts.threshold.$k", R.string.st_threshold to page(SettingsRoute.AlertKindPage(kind)))
                put("alerts.night.threshold.$k", R.string.st_threshold to page(SettingsRoute.NightKindPage(kind)))
            }
            put("alerts.kind.repeat", R.string.st_repeat to page(SettingsRoute.AlertKindPage(low)))
            put("alerts.kind.alarm", R.string.st_alarm to page(SettingsRoute.AlertKindPage(low)))
            put("alerts.kind.sound", R.string.st_sound to page(SettingsRoute.AlertKindPage(low)))
            put("alerts.muteAfterInsulin.on", R.string.st_quiet_insulin to page(SettingsRoute.Alerts))
            put("alerts.muteAfterInsulin.min", R.string.st_quiet_length to page(SettingsRoute.QuietRulePage(QuietKind.AfterInsulin)))
            put("alerts.muteFallingHigh.on", R.string.st_quiet_falling to page(SettingsRoute.Alerts))
            put("alerts.muteFallingHigh.min", R.string.st_quiet_within to page(SettingsRoute.QuietRulePage(QuietKind.FallingHigh)))
            put("alerts.muteRisingLow.on", R.string.st_quiet_rising to page(SettingsRoute.Alerts))
            put("alerts.muteRisingLow.min", R.string.st_quiet_within to page(SettingsRoute.QuietRulePage(QuietKind.RisingLow)))
            put("alerts.muteAfterCarbs.on", R.string.st_quiet_carbs to page(SettingsRoute.Alerts))
            put("alerts.muteAfterCarbs.min", R.string.st_quiet_length to page(SettingsRoute.QuietRulePage(QuietKind.AfterCarbs)))
            put("alerts.persistence.lowMin", R.string.st_wait_low to page(SettingsRoute.AlertsFineTuning))
            put("alerts.persistence.highMin", R.string.st_wait_high to page(SettingsRoute.AlertsFineTuning))
            put("alerts.persistence.marginMgdl", R.string.st_wait_margin to page(SettingsRoute.AlertsFineTuning))
            put("alerts.actions.muteMin", R.string.st_action_mute to page(SettingsRoute.AlertsFineTuning))
            put("alerts.actions.relaxMin", R.string.st_action_relax to page(SettingsRoute.AlertsFineTuning))
            put("alerts.actions.backMgdl", R.string.st_action_back_mgdl to page(SettingsRoute.AlertsFineTuning))
            put("alerts.actions.backMin", R.string.st_action_back_min to page(SettingsRoute.AlertsFineTuning))
            put("alerts.escalation.on", R.string.st_escalation_on to page(SettingsRoute.Alerts))
            put("alerts.escalation.min", R.string.st_escalation_min to page(SettingsRoute.Alerts))
            put("alerts.escalation.follow", R.string.st_escalation_follow to page(SettingsRoute.Alerts))
            put("alerts.night.on", R.string.st_night_use to page(SettingsRoute.NightAlerts))
            put("alerts.night.from", R.string.st_night_from to page(SettingsRoute.NightAlerts))
            put("alerts.night.to", R.string.st_night_to to page(SettingsRoute.NightAlerts))
            put("alerts.night.kind.on", R.string.st_kind_low to page(SettingsRoute.NightAlerts))
            put("alerts.night.kind.repeat", R.string.st_repeat to page(SettingsRoute.NightKindPage(low)))
            put("alerts.night.kind.alarm", R.string.st_alarm to page(SettingsRoute.NightKindPage(low)))
            put("alerts.sounds.pack", R.string.st_sound_pack to page(SettingsRoute.Alerts))
            put("alerts.sounds.shuffle", R.string.st_shuffle to page(SettingsRoute.Alerts))
            put("alerts.separate", R.string.st_alerts_separate to page(SettingsRoute.Alerts))
            put("alerts.unlockedSound", AlertRequirement.SoundWhenUnlocked.nameRes() to page(SettingsRoute.Reliability))
            put("statusbar.show", DsR.string.ds_notif_style_chip to page(SettingsRoute.StatusBar))
            put("statusbar.colorize", R.string.st_statusbar_colorize to page(SettingsRoute.StatusBar))
            put("statusbar.notifIcon", R.string.st_statusbar_icon_value_trend to page(SettingsRoute.StatusBar))
            put("statusbar.lockScreen", R.string.st_statusbar_lock_screen to page(SettingsRoute.StatusBar))
            put("statusbar.hookPosition", R.string.st_root_placement to page(SettingsRoute.StatusBar))
            put("statusbar.hookColor", R.string.st_root_colour to page(SettingsRoute.StatusBar))
            put("lock.widget", R.string.st_lock_widget to page(SettingsRoute.LockScreen))
            put("lock.background", R.string.st_lock_background to page(SettingsRoute.LockScreen))
            put("lock.appearance", R.string.st_lock_appearance to page(SettingsRoute.LockScreen))
            put("lock.treatmentTime", R.string.st_lock_time to page(SettingsRoute.LockScreen))
            put("pens.reading", R.string.st_pens_reading to page(SettingsRoute.Pens))
            put("pens.notify", R.string.st_pens_notify to page(SettingsRoute.Pens))
            put("pens.primeBelow", R.string.st_pens_prime_below to page(SettingsRoute.PenDetection))
            put("pens.primeBefore", R.string.st_pens_prime_before to page(SettingsRoute.PenDetection))
            put("pens.cluster", R.string.st_pens_cluster to page(SettingsRoute.PenDetection))
            put("pens.pending", R.string.st_pens_pending to page(SettingsRoute.PenDetection))
            put("pens.offer", R.string.st_pens_offer to page(SettingsRoute.PenDetection))
            put("pens.match", R.string.st_pens_match to page(SettingsRoute.PenDetection))
            put("pens.matchUnits", R.string.st_pens_match_units to page(SettingsRoute.PenDetection))
            put("pens.matchShare", R.string.st_pens_match_share to page(SettingsRoute.PenDetection))
        }

    /** The second places (docs/35 §7): the same setting editable on another page, found there too. */
    private val alsoIn: Map<String, Pair<Int, SearchTarget>> =
        mapOf(
            "glucose.trend" to (R.string.st_trend_from to page(SettingsRoute.AlertKindPage(AlertKind.FALLING_FAST))),
            "alerts.night.from" to (R.string.st_sensorend_bedtime to page(SettingsRoute.SensorEnd)),
            "alerts.night.to" to (R.string.st_sensorend_wakeup to page(SettingsRoute.SensorEnd)),
            "graph.sensorTime" to (R.string.st_graph_sensor to page(SettingsRoute.Graph)),
            // Graph's Sensor time left row opens the same page as Sensor end's, with the places (docs/38)
            "graph.sensorTime.place" to (R.string.st_graph_sensor to page(SettingsRoute.Graph)),
        )

    /** Android settings without a row the settings search lists here, with why. */
    private val noRow: Map<String, String> =
        mapOf(
            "alerts.source" to "set by Settings from (wearer's alerts or own rules), not a row of its own",
            "alerts.v" to "the alert settings' format version",
            "alerts.thresholdsConfirmedAt" to "a state (when the thresholds were last confirmed), shown in the review's row",
            "settings.searchRecent" to "the search's own recent queries",
            "widget.overrides" to "each widget's own configure screen (Reconfigure), not a settings page",
            "pens.pen" to "per-pen pages titled with the pen's own name",
            "sharing.nostr" to "Settings › Sharing (another feature's page): found as that page",
            "sharing.nostrInterval" to "Settings › Sharing (another feature's page): found as that page",
            "sharing.bleFollowers" to "Settings › Sharing (another feature's page): found as that page",
        )

    /** The pages a schema `section` may be on (the section of the overview row the page is under). */
    private val sections: Map<String, Set<Section>> =
        mapOf(
            "readings" to setOf(Section.Readings),
            "graph" to setOf(Section.Graph),
            "metrics" to setOf(Section.Metrics),
            "sensor" to setOf(Section.SensorEnd),
            "source" to setOf(Section.Source),
            "alerts" to setOf(Section.Alerts, Section.Reliability),
            "statusbar" to setOf(Section.StatusBar),
            "lock" to setOf(Section.LockScreen),
            "pens" to setOf(Section.Pens),
            "general" to setOf(Section.About, Section.SettingsFrom),
        )

    private val schema =
        Json
            .parseToJsonElement(Contracts.file("settings/settings.json").readText())
            .jsonObject
            .getValue("settings")
            .jsonArray
            .map { it.jsonObject }

    /** Settings with Android UI: an Android store, not Share with apps' (the app's AppsSearch and its test cover those). */
    private val android =
        schema
            .filter { it["android"] != null && it["android"] != JsonNull }
            .filter { it.getValue("section").jsonPrimitive.content != "apps" }
            .associate { it.getValue("id").jsonPrimitive.content to it.getValue("section").jsonPrimitive.content }

    private fun found(
        title: Int,
        at: SearchTarget,
    ) = SettingsIndex.entries.any { it.title == title && it.on == at }

    @Test
    fun everySettingIsFoundOnAPageOfItsSection() {
        val problems = mutableListOf<String>()
        for ((id, section) in android) {
            if (id in noRow) continue
            val (title, at) =
                rows[id] ?: run {
                    problems += "$id: no row listed here (add it, or to noRow with why)"
                    continue
                }
            if (!found(title, at)) problems += "$id: the search has no such row on $at"
            val allowed =
                sections[section] ?: run {
                    problems += "$id: section $section has no pages here"
                    continue
                }
            if (Section.of(at) !in allowed) problems += "$id: section $section, but its row is in ${Section.of(at)}"
        }
        for ((id, place) in alsoIn) {
            if (!found(place.first, place.second)) {
                problems +=
                    "$id: its second place ${place.second} isn't searchable"
            }
        }
        val stale = (rows.keys + noRow.keys) - android.keys
        assertTrue("settings this test lists that the schema doesn't have on Android: $stale", stale.isEmpty())
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The old page names find the pages they moved to (docs/35 §6: muscle memory). */
    @Test
    fun oldNamesStillFindThePages() {
        fun keywordsOf(route: SettingsRoute) =
            SettingsIndex.entries.first { it.target == page(route) && it.on == page(SettingsRoute.Overview) }.keywords
        assertTrue(keywordsOf(SettingsRoute.Readings) == R.string.st_readings_keywords)
        assertTrue(keywordsOf(SettingsRoute.Metrics) == R.string.st_metrics_keywords)
        assertTrue(keywordsOf(SettingsRoute.ImportExport) == R.string.st_files_keywords)
    }
}
