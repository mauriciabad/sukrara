// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.designsystem.rows.Category
import app.mauri.sukrara.designsystem.rows.CategoryIcon
import app.mauri.sukrara.designsystem.rows.nameRes
import app.mauri.sukrara.designsystem.rows.whyRes
import app.mauri.sukrara.feature.settings.QuietKind
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.settings.graph.MetricSlot
import app.mauri.sukrara.feature.settings.graph.PlaceSetting
import app.mauri.sukrara.feature.settings.quietFooter
import app.mauri.sukrara.feature.settings.quietLengthTitle
import app.mauri.sukrara.feature.settings.quietName
import app.mauri.sukrara.core.designsystem.R as DsR

/** Settings pages that belong to other features: the overview opens them ([SettingsSearchBar]'s `onOpenOther`). */
enum class OtherPage { Source, Sensor, Sharing, Apps, Profile, Backup, Export, Import }

/** Where a search result leads: a settings page, or another feature's page. */
sealed interface SearchTarget {
    data class Page(val route: SettingsRoute) : SearchTarget

    data class Other(val page: OtherPage) : SearchTarget
}

/** Whose phone shows a row: every phone, only the wearer's or only a follower's. */
enum class Seen { Everyone, Wearer, Follower }

/**
 * One row the search finds: its [title] and [summary] as the page shows them (string resources, no format arguments),
 * the page it is [on] and the [trail] of page titles down to it with its [group]'s title (the result's breadcrumb:
 * "Alerts › Quiet times"), and what tapping the result opens: [target], at the row [highlight] (flashed, as
 * stock Settings' search does; the row itself, the group title it names, or null when the row opens a page of its own:
 * then the result opens that page, at its top). [keywords]: other words that find it, never shown (AOSP
 * `settings:keywords`). [pensOnly] rows exist only on a phone that reads insulin pens, [seen] says on whose phone the
 * row shows, [updaterOnly] rows only in the build with the updater (the GitHub version; docs/20-publishing.md ›
 * Flavors).
 */
data class SearchEntry(
    @StringRes val title: Int,
    val on: SearchTarget,
    val trail: List<Int>,
    val target: SearchTarget = on,
    @StringRes val summary: Int? = null,
    @StringRes val group: Int? = null,
    @StringRes val highlight: Int? = title,
    @StringRes val keywords: Int? = null,
    val pensOnly: Boolean = false,
    val seen: Seen = Seen.Everyone,
    val updaterOnly: Boolean = false,
) {
    /** The page the row is on (the trail's last page). */
    @get:StringRes
    val page: Int get() = trail.last()

    /** How deep the row is: the overview's rows 0, a page's rows 1, a page under a page 2 … (shallower ranks first). */
    val depth: Int get() = if (on == SearchTarget.Page(SettingsRoute.Overview)) 0 else trail.size

    /**
     * Whether this phone shows the row: [pens] = it reads insulin pens, [wearer] = the wearer's phone (else a
     * follower's), [updater] = this build has the updater.
     */
    fun shownOn(
        pens: Boolean,
        wearer: Boolean,
        updater: Boolean = true,
    ): Boolean =
        (pens || !pensOnly) &&
            (updater || !updaterOnly) &&
            when (seen) {
                Seen.Everyone -> true
                Seen.Wearer -> wearer
                Seen.Follower -> !wearer
            }
}

/**
 * A section of the overview (its row's icon): every result shows the icon of the section it is in, as stock
 * Settings' search does. The overview draws its rows with the same table, so the two can't differ.
 */
enum class Section(
    @param:DrawableRes val icon: Int,
    val color: Category,
) {
    Alerts(DsR.drawable.ds_ic_notifications, Category.Red),
    Reliability(DsR.drawable.ds_ic_check_circle, Category.Red),
    Source(DsR.drawable.ds_ic_water_drop, Category.Green),
    SettingsFrom(DsR.drawable.ds_ic_tune, Category.Blue),
    Readings(DsR.drawable.ds_ic_water_drop, Category.Purple),
    Graph(DsR.drawable.ds_ic_monitoring, Category.Purple),
    Metrics(DsR.drawable.ds_ic_speed, Category.Purple),
    StatusBar(DsR.drawable.ds_ic_status_bar_notification, Category.Indigo),
    LockScreen(DsR.drawable.ds_ic_screen_lock_portrait, Category.Teal),
    Sensor(DsR.drawable.ds_ic_dia_sensor, Category.Green),
    SensorEnd(DsR.drawable.ds_ic_sports_score, Category.Orange),
    Sharing(DsR.drawable.ds_ic_group, Category.Blue),
    Apps(DsR.drawable.ds_ic_apps, Category.Orange),
    Pens(DsR.drawable.ds_ic_dia_pen, Category.Blue),
    Profile(DsR.drawable.ds_ic_account_circle, Category.Blue),
    Backup(DsR.drawable.ds_ic_backup, Category.Green),
    ImportExport(DsR.drawable.ds_ic_file_export, Category.Yellow),
    Advanced(DsR.drawable.ds_ic_code, Category.Grey),
    About(DsR.drawable.ds_ic_info, Category.Grey),
    ;

    companion object {
        /** The section a page is in (an alert's page is in Alerts, the log in Advanced…). */
        fun of(target: SearchTarget): Section =
            when (target) {
                is SearchTarget.Other -> {
                    when (target.page) {
                        OtherPage.Source -> Source
                        OtherPage.Sensor -> Sensor
                        OtherPage.Sharing -> Sharing
                        OtherPage.Apps -> Apps
                        OtherPage.Profile -> Profile
                        OtherPage.Backup -> Backup
                        OtherPage.Export, OtherPage.Import -> ImportExport
                    }
                }

                is SearchTarget.Page -> {
                    when (SettingsIndex.path(target.route).first()) {
                        SettingsRoute.Reliability -> Reliability
                        SettingsRoute.SettingsFrom -> SettingsFrom
                        SettingsRoute.Readings -> Readings
                        SettingsRoute.Graph -> Graph
                        SettingsRoute.Metrics -> Metrics
                        SettingsRoute.StatusBar -> StatusBar
                        SettingsRoute.LockScreen -> LockScreen
                        SettingsRoute.SensorEnd -> SensorEnd
                        SettingsRoute.Pens -> Pens
                        SettingsRoute.ImportExport -> ImportExport
                        SettingsRoute.Advanced -> Advanced
                        SettingsRoute.About -> About
                        else -> Alerts
                    }
                }
            }
    }
}

/**
 * THE search index: one entry per row of every settings page, in page order, with the group it is in. A row added to a
 * page is added here (the same string resources), so search and pages can't drift apart; [pageTitle] is exhaustive, so
 * a new page doesn't compile until it has a title here, and `SettingsIndexTest` renders every page and checks each row
 * on it is here with its group (and each entry is on its page, and its result flashes its row there).
 */
object SettingsIndex {
    /** The title of each page; null for the overview (its rows are the pages) and per-pen pages (no fixed rows). */
    @StringRes
    fun pageTitle(route: SettingsRoute): Int? =
        when (route) {
            SettingsRoute.Overview, is SettingsRoute.Pen, is SettingsRoute.PenDoses -> null
            SettingsRoute.Alerts -> R.string.st_alerts_title
            is SettingsRoute.AlertKindPage -> kindNames.getValue(route.kind)
            is SettingsRoute.NightKindPage -> kindNames.getValue(route.kind)
            SettingsRoute.NightAlerts -> R.string.st_night_title
            SettingsRoute.AlertsFineTuning -> R.string.st_finetune_title
            is SettingsRoute.QuietRulePage -> quietName(route.which)
            SettingsRoute.Readings -> R.string.st_readings_title
            SettingsRoute.TrendArrow -> R.string.st_trend_title
            SettingsRoute.Graph -> R.string.st_graph_title
            SettingsRoute.Metrics -> R.string.st_metrics_title
            is SettingsRoute.MetricPage -> route.slot.title
            SettingsRoute.LastTreatment -> R.string.st_last
            SettingsRoute.TirRings -> R.string.st_tir_title
            SettingsRoute.ImportExport -> R.string.st_files_title
            SettingsRoute.GraphMarks -> R.string.st_marks_title
            is SettingsRoute.PlacePage -> placeTitle(route.setting)
            SettingsRoute.LockScreen -> R.string.st_lock_title
            SettingsRoute.Pens -> R.string.st_pens_title
            SettingsRoute.PenDetection -> R.string.st_pens_detection
            SettingsRoute.Reliability -> R.string.st_reliability_title
            SettingsRoute.StatusBar -> R.string.st_statusbar_title
            SettingsRoute.SensorEnd -> R.string.st_sensorend_title
            SettingsRoute.About -> R.string.st_about_title
            SettingsRoute.ThresholdReview -> R.string.st_review_title
            SettingsRoute.Advanced -> R.string.st_adv_title
            SettingsRoute.Log -> R.string.st_adv_log
            SettingsRoute.SettingsFrom -> R.string.st_from_title
        }

    /** The page whose row opens [route] (null: the overview's): an alert's page is under Alerts. */
    fun parent(route: SettingsRoute): SettingsRoute? =
        when (route) {
            is SettingsRoute.AlertKindPage,
            SettingsRoute.NightAlerts,
            SettingsRoute.ThresholdReview,
            SettingsRoute.AlertsFineTuning,
            is SettingsRoute.QuietRulePage,
            -> {
                SettingsRoute.Alerts
            }

            is SettingsRoute.NightKindPage -> {
                SettingsRoute.NightAlerts
            }

            SettingsRoute.GraphMarks -> {
                SettingsRoute.Graph
            }

            is SettingsRoute.MetricPage, SettingsRoute.LastTreatment, SettingsRoute.TirRings -> {
                SettingsRoute.Metrics
            }

            // a setting's page with its places is under its home: Time shown in Graph, the sensor time in Sensor end
            is SettingsRoute.PlacePage -> {
                when (route.setting) {
                    PlaceSetting.Hours -> SettingsRoute.Graph
                    PlaceSetting.SensorTime -> SettingsRoute.SensorEnd
                }
            }

            SettingsRoute.TrendArrow -> {
                SettingsRoute.Readings
            }

            SettingsRoute.Log -> {
                SettingsRoute.Advanced
            }

            SettingsRoute.PenDetection, is SettingsRoute.Pen -> {
                SettingsRoute.Pens
            }

            is SettingsRoute.PenDoses -> {
                SettingsRoute.Pen(route.serial)
            }

            else -> {
                null
            }
        }

    /** The pages the menus pass through to [route], the overview's row first: a result opens them all (Back walks up). */
    fun path(route: SettingsRoute): List<SettingsRoute> = generateSequence(route, ::parent).toList().reversed()

    /** The titles along [path]: the breadcrumb down to [route] ("Alerts", "Night alerts"). */
    private fun trail(route: SettingsRoute): List<Int> = path(route).map { checkNotNull(pageTitle(it)) { "$it has no page title" } }

    /** [rows] of [route] in [group] (title to summary). */
    private fun MutableList<SearchEntry>.rows(
        route: SettingsRoute,
        @StringRes group: Int?,
        vararg rows: Pair<Int, Int?>,
        pensOnly: Boolean = false,
        seen: Seen = Seen.Everyone,
        updaterOnly: Boolean = false,
    ) {
        val on = SearchTarget.Page(route)
        val trail = trail(route)
        for ((t, s) in rows) {
            add(SearchEntry(t, on, trail, summary = s, group = group, pensOnly = pensOnly, seen = seen, updaterOnly = updaterOnly))
        }
    }

    /**
     * A *See also* row of [route] that opens another page ([target], titled [title]): found as that page (the result is
     * the page, at its top). AOSP Settings links pages the same way (a Preference whose fragment or intent opens the
     * other screen; SettingsLib SPA's `SettingsEntryBuilder.createLinkFrom`): one page reachable from several.
     */
    private fun MutableList<SearchEntry>.link(
        route: SettingsRoute,
        @StringRes title: Int,
        target: SearchTarget,
        @StringRes summary: Int? = null,
        seen: Seen = Seen.Everyone,
    ) {
        val on = SearchTarget.Page(route)
        add(SearchEntry(title, on, trail(route), target, summary, R.string.st_see_also, highlight = null, seen = seen))
    }

    /** The row of [route]'s parent page that opens [route] (titled with [route]'s title): the result opens [route]. */
    private fun MutableList<SearchEntry>.pageRow(
        route: SettingsRoute,
        @StringRes group: Int?,
        @StringRes summary: Int? = null,
        @StringRes keywords: Int? = null,
        pensOnly: Boolean = false,
    ) {
        val parent = checkNotNull(parent(route))
        val title = checkNotNull(pageTitle(route))
        add(
            SearchEntry(
                title,
                SearchTarget.Page(parent),
                trail(parent),
                SearchTarget.Page(route),
                summary,
                group,
                highlight = null,
                keywords = keywords,
                pensOnly = pensOnly,
            ),
        )
    }

    /** The title of a setting's page with its places (docs/38): the setting's name. */
    private fun placeTitle(setting: PlaceSetting): Int =
        when (setting) {
            PlaceSetting.Hours -> R.string.st_graph_hours
            PlaceSetting.SensorTime -> R.string.st_graph_sensor
        }

    /** The place rows of a setting's page (docs/38), in its *Notification and widgets* group. */
    private fun MutableList<SearchEntry>.places(setting: PlaceSetting) =
        rows(
            SettingsRoute.PlacePage(setting),
            R.string.st_places_group,
            R.string.st_place_notification to null,
            R.string.st_place_widgets to null,
        )

    private val overviewTrail = listOf(R.string.st_settings_title)

    /** A row of the overview: a page; the result opens it at its top. */
    private fun overview(
        title: Int,
        summary: Int?,
        target: SearchTarget,
        pensOnly: Boolean = false,
        seen: Seen = Seen.Everyone,
        keywords: Int? = null,
    ) = SearchEntry(
        title,
        SearchTarget.Page(SettingsRoute.Overview),
        overviewTrail,
        target,
        summary,
        highlight = null,
        keywords = keywords,
        pensOnly = pensOnly,
        seen = seen,
    )

    /** Rows of Settings › Sensor (another feature's page) in [group]. */
    private fun MutableList<SearchEntry>.sensor(
        @StringRes group: Int,
        vararg rows: Pair<Int, Int?>,
    ) {
        val on = SearchTarget.Other(OtherPage.Sensor)
        for ((t, s) in rows) add(SearchEntry(t, on, listOf(R.string.st_sensor), summary = s, group = group))
    }

    private val kindNames =
        mapOf(
            AlertKind.VERY_LOW to R.string.st_kind_very_low,
            AlertKind.LOW to R.string.st_kind_low,
            AlertKind.HIGH to R.string.st_kind_high,
            AlertKind.VERY_HIGH to R.string.st_kind_very_high,
            AlertKind.EXTREMELY_HIGH to R.string.st_kind_extremely_high,
            AlertKind.FALLING_FAST to R.string.st_kind_falling_fast,
            AlertKind.RISING_FAST to R.string.st_kind_rising_fast,
            AlertKind.NO_DATA to R.string.st_kind_no_data,
        )

    /** The group each alert is listed in (Alerts and Night alerts). */
    private val kindGroups =
        mapOf(
            AlertKind.VERY_LOW to R.string.st_group_lows,
            AlertKind.LOW to R.string.st_group_lows,
            AlertKind.HIGH to R.string.st_group_highs,
            AlertKind.VERY_HIGH to R.string.st_group_highs,
            AlertKind.EXTREMELY_HIGH to R.string.st_group_highs,
            AlertKind.FALLING_FAST to R.string.st_group_rates,
            AlertKind.RISING_FAST to R.string.st_group_rates,
            AlertKind.NO_DATA to R.string.st_group_signal,
        )

    val entries: List<SearchEntry> =
        buildList {
            // the overview's rows: the pages themselves, in the overview's order (docs/35); a moved page's old name is
            // among its keywords, so muscle memory still finds it ("Ranges" → Readings, "Rings" → Metrics)
            add(overview(R.string.st_alerts_title, R.string.st_overview_alerts_on, SearchTarget.Page(SettingsRoute.Alerts)))
            add(overview(R.string.st_reliability_title, R.string.st_reliability_ready, SearchTarget.Page(SettingsRoute.Reliability)))
            add(overview(R.string.st_from_title, null, SearchTarget.Page(SettingsRoute.SettingsFrom), seen = Seen.Follower))
            add(
                overview(
                    R.string.st_readings_title,
                    R.string.st_overview_readings,
                    SearchTarget.Page(SettingsRoute.Readings),
                    keywords = R.string.st_readings_keywords,
                ),
            )
            add(
                overview(
                    R.string.st_graph_title,
                    R.string.st_overview_graph,
                    SearchTarget.Page(SettingsRoute.Graph),
                    keywords = R.string.st_graph_keywords,
                ),
            )
            add(
                overview(
                    R.string.st_metrics_title,
                    R.string.st_overview_metrics,
                    SearchTarget.Page(SettingsRoute.Metrics),
                    keywords = R.string.st_metrics_keywords,
                ),
            )
            add(overview(R.string.st_statusbar_title, R.string.st_overview_statusbar, SearchTarget.Page(SettingsRoute.StatusBar)))
            add(overview(R.string.st_lock_title, R.string.st_overview_lock, SearchTarget.Page(SettingsRoute.LockScreen)))
            add(overview(R.string.st_source_title, R.string.st_source_summary, SearchTarget.Other(OtherPage.Source)))
            add(overview(R.string.st_sensor, R.string.st_sensor_summary, SearchTarget.Other(OtherPage.Sensor)))
            add(overview(R.string.st_sensorend_title, R.string.st_overview_sensorend, SearchTarget.Page(SettingsRoute.SensorEnd)))
            add(
                overview(
                    R.string.st_pens_title,
                    R.string.st_overview_pens,
                    SearchTarget.Page(SettingsRoute.Pens),
                    pensOnly = true,
                    keywords = R.string.st_pens_keywords,
                ),
            )
            add(overview(R.string.st_sharing, R.string.st_sharing_summary, SearchTarget.Other(OtherPage.Sharing)))
            add(overview(R.string.st_apps, R.string.st_apps_summary, SearchTarget.Other(OtherPage.Apps)))
            add(overview(R.string.st_backup_title, null, SearchTarget.Other(OtherPage.Backup), seen = Seen.Wearer))
            add(
                overview(
                    R.string.st_files_title,
                    R.string.st_overview_files,
                    SearchTarget.Page(SettingsRoute.ImportExport),
                    keywords = R.string.st_files_keywords,
                ),
            )
            add(overview(R.string.st_adv_title, R.string.st_adv_overview_summary, SearchTarget.Page(SettingsRoute.Advanced)))
            add(overview(R.string.st_about_title, null, SearchTarget.Page(SettingsRoute.About)))
            add(overview(R.string.st_profile_title, R.string.st_profile_summary, SearchTarget.Other(OtherPage.Profile), seen = Seen.Wearer))
            // Settings › Sensor's own rows (another feature's page)
            sensor(
                R.string.st_sensor_current,
                R.string.st_sensor_reading_from to null,
                R.string.st_sensor_status to null,
                R.string.st_sensor_serial to null,
                R.string.st_sensor_started to null,
                R.string.st_sensor_ends to null,
                R.string.st_sensor_pair_new to R.string.st_sensor_pair_new_summary,
            )
            // Settings › Source's own rows (the app's page: it holds another Sukrara's follow too)
            val source = SearchTarget.Other(OtherPage.Source)
            add(SearchEntry(R.string.st_source_whose, source, listOf(R.string.st_source_title)))
            add(SearchEntry(R.string.st_source_from, source, listOf(R.string.st_source_title)))
            add(
                SearchEntry(
                    R.string.st_sensor_juggluco_secret,
                    source,
                    listOf(R.string.st_source_title),
                    group = R.string.st_sensor_juggluco,
                ),
            )
            // Sensor → Source: where the glucose comes from is chosen there
            add(
                SearchEntry(
                    R.string.st_source_title,
                    SearchTarget.Other(OtherPage.Sensor),
                    listOf(R.string.st_sensor),
                    SearchTarget.Other(OtherPage.Source),
                    R.string.st_source_summary,
                    R.string.st_see_also,
                    highlight = null,
                ),
            )
            // Sensor ↔ Sensor end (docs/35 §7): its own overview row stays, and the Sensor page links to it
            add(
                SearchEntry(
                    R.string.st_sensorend_title,
                    SearchTarget.Other(OtherPage.Sensor),
                    listOf(R.string.st_sensor),
                    SearchTarget.Page(SettingsRoute.SensorEnd),
                    R.string.st_sensorend_link_summary,
                    R.string.st_see_also,
                    highlight = null,
                ),
            )
            sensor(
                R.string.st_sensor_this_phone,
                R.string.st_sensor_open_on_scan to null,
                R.string.st_sensor_forget to R.string.st_sensor_forget_summary,
            )
            sensor(
                R.string.st_sensor_libreview,
                R.string.st_sensor_libreview to null,
                R.string.st_sensor_signed_in_as to null,
                R.string.st_sensor_receiver_id to null,
            )

            // Settings › Alert reliability: the checklist's items (the setup's), then its test
            val reliability = SettingsRoute.Reliability
            for (r in AlertRequirement.entries.filter { it.inSetup }) {
                add(SearchEntry(r.nameRes(), SearchTarget.Page(reliability), trail(reliability), summary = r.whyRes()))
            }
            rows(reliability, R.string.st_reliability_test_title, R.string.st_send_test to null)
            link(reliability, R.string.st_alerts_title, SearchTarget.Page(SettingsRoute.Alerts))

            val alerts = SettingsRoute.Alerts
            rows(alerts, null, R.string.st_alerts_main to null, R.string.st_mute_all to null)
            // each alert's row opens its page
            for ((kind, group) in kindGroups) pageRow(SettingsRoute.AlertKindPage(kind), group)
            pageRow(SettingsRoute.NightAlerts, null, R.string.st_night_use_summary)
            rows(
                alerts,
                R.string.st_escalation_title,
                R.string.st_escalation_on to R.string.st_escalation_on_summary,
                R.string.st_escalation_min to R.string.st_escalation_footer,
                seen = Seen.Wearer,
            )
            rows(
                alerts,
                R.string.st_escalation_title,
                R.string.st_escalation_follow to R.string.st_escalation_follow_summary,
                seen = Seen.Follower,
            )
            pageRow(SettingsRoute.ThresholdReview, null)
            // each quiet rule's row opens its page: its switch and its length are there
            for (which in QuietKind.entries) pageRow(SettingsRoute.QuietRulePage(which), R.string.st_quiet_title)
            rows(
                alerts,
                R.string.st_sounds_title,
                R.string.st_sound_pack to null,
                R.string.st_shuffle to null,
                R.string.st_alerts_separate to R.string.st_alerts_separate_on,
                R.string.st_notification_settings to R.string.st_notification_settings_summary,
            )
            // the rarely touched rows: one named subpage at the end of Alerts (docs/35: no global advanced mode)
            add(
                SearchEntry(
                    R.string.st_finetune_title,
                    SearchTarget.Page(alerts),
                    trail(alerts),
                    SearchTarget.Page(SettingsRoute.AlertsFineTuning),
                    R.string.st_finetune_summary,
                    highlight = null,
                    keywords = R.string.st_finetune_keywords,
                ),
            )
            link(alerts, R.string.st_reliability_title, SearchTarget.Page(SettingsRoute.Reliability))
            link(alerts, R.string.st_readings_title, SearchTarget.Page(SettingsRoute.Readings), R.string.st_overview_readings)
            for (which in QuietKind.entries) {
                rows(SettingsRoute.QuietRulePage(which), null, R.string.st_quiet_use to null, quietLengthTitle(which) to quietFooter(which))
            }
            val fine = SettingsRoute.AlertsFineTuning
            rows(
                fine,
                R.string.st_wait_title,
                R.string.st_wait_low to R.string.st_wait_footer,
                R.string.st_wait_high to R.string.st_wait_footer,
                R.string.st_wait_margin to R.string.st_wait_footer,
            )
            rows(
                fine,
                R.string.st_actions_title,
                R.string.st_action_mute to null,
                R.string.st_action_relax to null,
                R.string.st_action_back_mgdl to null,
                R.string.st_action_back_min to null,
            )
            rows(fine, null, R.string.st_finetune_reset to null)
            // each alert's own page, so a hit opens that alert ("Remind every · Alerts › Low › Alert when")
            for (kind in kindNames.keys) {
                val page = SettingsRoute.AlertKindPage(kind)
                rows(page, R.string.st_alert_when, R.string.st_threshold to null, R.string.st_repeat to null)
                rows(
                    page,
                    R.string.st_sound_title,
                    R.string.st_alarm to R.string.st_alarm_summary,
                    R.string.st_sound to null,
                    R.string.st_notification_settings to R.string.st_notification_settings_summary,
                )
                rows(
                    page,
                    R.string.st_try_title,
                    R.string.st_play_sound to null,
                    R.string.st_play_vibrate to null,
                    R.string.st_send_test to R.string.st_send_test_summary,
                    R.string.st_channel_settings to R.string.st_channel_settings_summary,
                )
            }
            // the fast alerts use the trend arrow: its choice is on their pages too (docs/35 §7: the same setting), as
            // *Trend from* in a *Trend arrow* group of its own (under *Alert when* it read as a condition)
            for (kind in listOf(AlertKind.FALLING_FAST, AlertKind.RISING_FAST)) {
                rows(SettingsRoute.AlertKindPage(kind), R.string.st_trend_title, R.string.st_trend_from to R.string.st_trend_shared_footer)
            }
            // Alerts › Night alerts, and each alert's night rule
            rows(
                SettingsRoute.NightAlerts,
                null,
                R.string.st_night_use to null,
                R.string.st_night_from to null,
                R.string.st_night_to to null,
            )
            for ((kind, group) in kindGroups) pageRow(SettingsRoute.NightKindPage(kind), group)
            // THE night also keeps the sensor reminders quiet: Night alerts links Sensor end (the wearer's phone)
            link(SettingsRoute.NightAlerts, R.string.st_sensorend_title, SearchTarget.Page(SettingsRoute.SensorEnd), seen = Seen.Wearer)
            for (kind in kindNames.keys) {
                rows(
                    SettingsRoute.NightKindPage(kind),
                    R.string.st_alert_when,
                    R.string.st_threshold to null,
                    R.string.st_repeat to null,
                    R.string.st_alarm to R.string.st_alarm_summary,
                )
            }
            rows(SettingsRoute.ThresholdReview, null, R.string.st_review_confirm to null)

            val readings = SettingsRoute.Readings
            rows(readings, null, R.string.st_unit to R.string.st_unit_footer)
            // the Trend arrow row opens its page: found by its words (arrow, trend, delay …) too
            add(
                SearchEntry(
                    R.string.st_trend_title,
                    SearchTarget.Page(readings),
                    trail(readings),
                    SearchTarget.Page(SettingsRoute.TrendArrow),
                    R.string.st_trend_summary,
                    highlight = null,
                    keywords = R.string.st_trend_keywords,
                ),
            )
            rows(
                SettingsRoute.TrendArrow,
                R.string.st_trend_from,
                R.string.st_trend_quick to R.string.st_trend_quick_summary,
                R.string.st_trend_balanced to R.string.st_trend_balanced_summary,
                R.string.st_trend_smooth to R.string.st_trend_smooth_summary,
                R.string.st_trend_sensor to R.string.st_trend_sensor_summary,
            )
            rows(
                readings,
                R.string.st_ranges_title,
                R.string.st_band_very_low to null,
                R.string.st_band_low to null,
                R.string.st_band_tight to null,
                R.string.st_band_high to null,
                R.string.st_band_very_high to null,
                R.string.st_band_extremely_high to null,
            )
            rows(readings, null, R.string.st_ranges_reset to R.string.st_ranges_reset_summary)
            // the ranges aren't the alert levels (LibreLink keeps them apart too): Readings links Alerts
            link(readings, R.string.st_alerts_title, SearchTarget.Page(SettingsRoute.Alerts))

            val graph = SettingsRoute.Graph
            // the ranges colour the graph: a Ranges row atop opens them in Readings (docs/35 §7)
            add(
                SearchEntry(
                    R.string.st_ranges_title,
                    SearchTarget.Page(graph),
                    trail(graph),
                    SearchTarget.Page(readings),
                    highlight = null,
                ),
            )
            rows(
                graph,
                R.string.st_graph_lines,
                R.string.st_graph_lines to null,
                R.string.st_graph_line_add to null,
                R.string.st_graph_grid to null,
            )
            // Time shown: the app's value and the places that differ; its page has them all (docs/38)
            pageRow(SettingsRoute.PlacePage(PlaceSetting.Hours), null, keywords = R.string.st_places_keywords)
            places(PlaceSetting.Hours)
            rows(
                graph,
                R.string.st_graph_beside_graph,
                R.string.st_graph_side to R.string.st_graph_side_summary,
                R.string.st_graph_crown to null,
            )
            pageRow(SettingsRoute.GraphMarks, null)
            // the sensor time's row, the same as Sensor end's: it opens the same page
            add(
                SearchEntry(
                    R.string.st_graph_sensor,
                    SearchTarget.Page(graph),
                    trail(graph),
                    SearchTarget.Page(SettingsRoute.PlacePage(PlaceSetting.SensorTime)),
                    highlight = null,
                    keywords = R.string.st_places_keywords,
                ),
            )
            rows(graph, null, R.string.st_graph_reset_all to null)
            rows(
                SettingsRoute.GraphMarks,
                R.string.st_marks_rapid,
                R.string.st_marks_point to null,
                R.string.st_marks_line to R.string.st_marks_line_summary,
                R.string.st_marks_amount to null,
                R.string.st_marks_time to null,
            )
            rows(SettingsRoute.GraphMarks, null, R.string.st_marks_reset to null)

            // Metrics (docs/39): a row per thing beside the value, each opening its page; the rings' look on a page of its own
            val metrics = SettingsRoute.Metrics
            for (slot in MetricSlot.entries) pageRow(SettingsRoute.MetricPage(slot), null)
            pageRow(SettingsRoute.LastTreatment, null)
            pageRow(SettingsRoute.TirRings, null)
            rows(metrics, null, R.string.st_metrics_reset_all to null)
            for (slot in MetricSlot.entries) {
                val page = SettingsRoute.MetricPage(slot)
                rows(page, null, slot.show to null)
                rows(
                    page,
                    R.string.st_metric_shows,
                    R.string.st_metric_kind_tir to R.string.st_metric_kind_tir_summary,
                    R.string.st_metric_kind_gmi to R.string.st_metric_kind_gmi_summary,
                    R.string.st_metric_kind_average to null,
                    R.string.st_metric_kind_cv to R.string.st_metric_kind_cv_summary,
                )
                rows(page, null, R.string.st_metric_period to null)
                if (slot.dims) rows(page, null, R.string.st_metric_dimmed to R.string.st_metric_dimmed_summary)
            }
            rows(SettingsRoute.LastTreatment, null, R.string.st_last_show to null)
            rows(SettingsRoute.LastTreatment, R.string.st_last_where, R.string.st_last_near to null, R.string.st_last_end to null)
            val rings = SettingsRoute.TirRings
            rows(rings, R.string.st_tir_number, R.string.st_tir_in_range to null, R.string.st_tir_tight to null)
            rows(rings, null, R.string.st_tir_rewards to R.string.st_tir_rewards_summary)
            rows(rings, R.string.st_tir_order, R.string.st_tir_order to R.string.st_tir_order_footer)
            link(rings, R.string.st_readings_title, SearchTarget.Page(readings), R.string.st_overview_readings)

            val lock = SettingsRoute.LockScreen
            add(
                SearchEntry(
                    R.string.st_lock_time,
                    SearchTarget.Page(lock),
                    trail(lock),
                    group = R.string.st_lock_treatment,
                    keywords = R.string.st_lock_time_keywords,
                ),
            )
            rows(lock, R.string.st_lock_treatment, R.string.st_lock_treatment to null)
            rows(
                lock,
                R.string.st_root_group,
                R.string.st_lock_widget to R.string.st_lock_widget_on,
                R.string.st_lock_background to null,
                R.string.st_lock_appearance to null,
            )

            val pens = SettingsRoute.Pens
            rows(pens, null, R.string.st_pens_reading to null, pensOnly = true)
            add(
                SearchEntry(
                    R.string.st_pens_notify,
                    SearchTarget.Page(pens),
                    trail(pens),
                    summary = R.string.st_pens_notify_summary,
                    keywords = R.string.st_pens_notify_keywords,
                    pensOnly = true,
                ),
            )
            pageRow(SettingsRoute.PenDetection, null, pensOnly = true)
            rows(pens, R.string.st_pens_yours, R.string.st_pens_yours to null, pensOnly = true)
            val detection = SettingsRoute.PenDetection
            rows(
                detection,
                R.string.st_pens_priming_group,
                R.string.st_pens_prime_below to null,
                R.string.st_pens_prime_before to null,
                pensOnly = true,
            )
            rows(
                detection,
                R.string.st_pens_injections_group,
                R.string.st_pens_cluster to null,
                R.string.st_pens_pending to null,
                R.string.st_pens_offer to null,
                pensOnly = true,
            )
            rows(
                detection,
                R.string.st_pens_typed_group,
                R.string.st_pens_match to null,
                R.string.st_pens_match_units to null,
                R.string.st_pens_match_share to null,
                pensOnly = true,
            )
            rows(detection, null, R.string.st_pens_reset to null, pensOnly = true)

            val statusBar = SettingsRoute.StatusBar
            // THE choice (docs/36 §11, §14): each option by its name
            rows(
                statusBar,
                DsR.string.ds_notif_style_heading,
                DsR.string.ds_notif_style_chip to DsR.string.ds_notif_style_chip_summary,
                DsR.string.ds_notif_style_icon to DsR.string.ds_notif_style_icon_summary,
                DsR.string.ds_notif_style_off to DsR.string.ds_notif_style_off_summary,
                DsR.string.ds_notif_style_root to DsR.string.ds_notif_style_root_summary,
            )
            rows(
                statusBar,
                R.string.st_statusbar_icon_group,
                R.string.st_statusbar_icon_value_trend to null,
                R.string.st_statusbar_icon_value to null,
                R.string.st_statusbar_icon_trend to null,
                R.string.st_statusbar_icon_app to null,
            )
            rows(
                statusBar,
                R.string.st_statusbar_notification,
                R.string.st_statusbar_colorize to R.string.st_statusbar_colorize_on,
                R.string.st_statusbar_lock_screen to R.string.st_statusbar_lock_screen_on,
                R.string.st_statusbar_alerts to R.string.st_statusbar_alerts_on,
            )
            rows(
                statusBar,
                null,
                R.string.st_root_colour to null,
                R.string.st_root_placement to null,
            )
            // with the root text: the icon hidden, the notification's on / off (Android's)
            rows(statusBar, R.string.st_statusbar_icon_group, R.string.st_statusbar_icon_none to null)
            rows(statusBar, R.string.st_statusbar_notification, R.string.st_statusbar_glucose_notification to null)

            val sensorEnd = SettingsRoute.SensorEnd
            rows(sensorEnd, null, R.string.st_sensorend_current to null)
            rows(sensorEnd, R.string.st_sensorend_reminders, R.string.st_sensorend_reminders to null, R.string.st_sensorend_add to null)
            rows(sensorEnd, null, R.string.st_sensorend_unlock to R.string.st_sensorend_unlock_summary)
            // THE night (docs/35 §7): the same time range as Night alerts, edited here too
            rows(
                sensorEnd,
                R.string.st_sensorend_night,
                R.string.st_sensorend_evening to null,
                R.string.st_sensorend_bedtime to null,
                R.string.st_sensorend_wakeup to null,
            )
            rows(
                sensorEnd,
                R.string.st_sensorend_countdowns,
                R.string.st_sensorend_countdown to R.string.st_sensorend_countdown_summary,
                R.string.st_sensorend_countdown_hours to null,
                R.string.st_sensorend_warmup to R.string.st_sensorend_warmup_summary,
            )
            // the sensor's time left on the graph: its home is here, Graph has the same row (docs/35 §7, docs/38); here
            // its group says where it shows
            pageRow(
                SettingsRoute.PlacePage(PlaceSetting.SensorTime),
                R.string.st_sensorend_on_graph,
                keywords = R.string.st_places_keywords,
            )
            rows(SettingsRoute.PlacePage(PlaceSetting.SensorTime), null, R.string.st_sensor_time_show to null)
            places(PlaceSetting.SensorTime)
            rows(sensorEnd, R.string.st_sensorend_after, R.string.st_sensorend_again to null)
            // the sensor's end in the calendar (the wearer's phone)
            rows(
                sensorEnd,
                R.string.st_calendar_group,
                R.string.st_calendar_title to null,
                R.string.st_calendar_where to null,
                R.string.st_calendar_gcal_title to R.string.st_calendar_gcal_hint,
                seen = Seen.Wearer,
            )
            link(sensorEnd, R.string.st_sensor, SearchTarget.Other(OtherPage.Sensor), R.string.st_sensor_summary)
            link(sensorEnd, R.string.st_night_title, SearchTarget.Page(SettingsRoute.NightAlerts))

            val about = SettingsRoute.About
            rows(about, null, R.string.st_about_version to null)
            // the GitHub version's updater (#188): its answer's way out and its two switches
            // *Check again* is the update row's summary: up to date, that row flashes
            add(
                SearchEntry(
                    R.string.st_update_check_again,
                    SearchTarget.Page(about),
                    trail(about),
                    highlight = R.string.st_update_none,
                    updaterOnly = true,
                ),
            )
            rows(
                about,
                R.string.st_update_group,
                R.string.st_update_auto_checks to R.string.st_update_auto_checks_summary,
                R.string.st_update_auto_install to R.string.st_update_auto_install_summary,
                updaterOnly = true,
            )
            rows(about, null, R.string.st_about_versions to R.string.st_about_versions_summary)
            rows(about, R.string.st_other_devices, R.string.st_device_mac to R.string.st_device_mac_summary)
            rows(
                about,
                null,
                R.string.st_report_problem to R.string.st_report_problem_summary,
                R.string.st_source_code to R.string.st_license,
                R.string.st_privacy to R.string.st_privacy_summary,
                R.string.st_not_medical to null,
            )

            val advanced = SettingsRoute.Advanced
            rows(advanced, R.string.st_adv_relays, R.string.st_adv_relays to null, R.string.st_adv_check_now to null)
            add(
                SearchEntry(
                    R.string.st_adv_log,
                    SearchTarget.Page(advanced),
                    trail(advanced),
                    SearchTarget.Page(SettingsRoute.Log),
                    R.string.st_adv_log_summary,
                    R.string.st_adv_diagnostics,
                    highlight = null,
                ),
            )
            rows(advanced, R.string.st_adv_diagnostics, R.string.st_adv_identity to null, seen = Seen.Wearer)
            rows(advanced, R.string.st_adv_diagnostics, R.string.st_adv_this_phone to null, seen = Seen.Follower)
            rows(advanced, R.string.st_adv_stats, R.string.st_adv_sent to null, seen = Seen.Wearer)
            rows(advanced, R.string.st_adv_stats, R.string.st_adv_received to null, seen = Seen.Follower)
            rows(advanced, R.string.st_adv_stats, R.string.st_adv_notification to null)

            // Import and export: Export data and Import history (`:integrations`' rows: the app's `AppsSearch`), the
            // settings file (out of Advanced: it isn't an advanced feature, docs/35 §7) and the other data pages
            val files = SettingsRoute.ImportExport
            rows(
                files,
                R.string.st_adv_file,
                R.string.st_adv_export to R.string.st_adv_export_summary,
                R.string.st_adv_import to R.string.st_adv_import_summary,
                R.string.st_adv_reset to R.string.st_adv_reset_summary,
            )
            link(files, R.string.st_sharing, SearchTarget.Other(OtherPage.Sharing), R.string.st_sharing_summary)
            link(files, R.string.st_apps, SearchTarget.Other(OtherPage.Apps), R.string.st_apps_summary)
            link(files, R.string.st_backup_title, SearchTarget.Other(OtherPage.Backup), seen = Seen.Wearer)

            val from = SettingsRoute.SettingsFrom
            rows(
                from,
                R.string.st_from_header,
                R.string.st_from_wearer to R.string.st_from_wearer_summary,
                R.string.st_from_default to R.string.st_from_default_summary,
                R.string.st_from_custom to R.string.st_from_custom_summary,
                seen = Seen.Follower,
            )
            rows(
                from,
                R.string.st_from_start_from,
                R.string.st_from_copy to R.string.st_from_copy_summary,
                R.string.st_from_reset to R.string.st_from_reset_summary,
                seen = Seen.Follower,
            )
        }
}

/** A section's icon as the overview draws it (the coloured category icon, AOSP Settings' homepage icons). */
@Composable
fun SectionIcon(section: Section) = CategoryIcon(section.icon, section.color)
