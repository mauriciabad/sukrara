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
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.core.designsystem.R as DsR

/** Settings pages that belong to other features: the overview opens them ([SettingsSearchBar]'s `onOpenOther`). */
enum class OtherPage { Sensor, Sharing, Apps, Profile, Backup, Export }

/** Where a search result leads: a settings page, or another feature's page. */
sealed interface SearchTarget {
    data class Page(val route: SettingsRoute) : SearchTarget

    data class Other(val page: OtherPage) : SearchTarget
}

/** Whose phone shows a row: every phone, only the wearer's or only a follower's. */
enum class Seen { Everyone, Wearer, Follower }

/**
 * What a build may leave out (docs/20-publishing.md › Flavors): the Google Play build has no updater; root is in
 * both builds since 2026-11-11 (#142) and stays here for the fallback if Play rejects it.
 */
enum class BuildFeature { Root, UpdateCheck }

/**
 * One row the search finds: its [title] and [summary] as the page shows them (string resources, no format arguments),
 * the page it is [on] and the [trail] of page titles down to it with its [group]'s title (the result's breadcrumb:
 * "Alerts › Quiet after treatments"), and what tapping the result opens: [target], at the row [highlight] (flashed, as
 * stock Settings' search does; the row itself, the group title it names, or null when the row opens a page of its own:
 * then the result opens that page, at its top). [keywords]: other words that find it, never shown (AOSP
 * `settings:keywords`). [pensOnly] rows exist only on a phone that reads insulin pens, [seen] says on whose phone the
 * row shows, [needs] which build has it (null: both).
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
    val needs: BuildFeature? = null,
) {
    /** The page the row is on (the trail's last page). */
    @get:StringRes
    val page: Int get() = trail.last()

    /** How deep the row is: the overview's rows 0, a page's rows 1, a page under a page 2 … (shallower ranks first). */
    val depth: Int get() = if (on == SearchTarget.Page(SettingsRoute.Overview)) 0 else trail.size

    /**
     * Whether this phone shows the row: [pens] = it reads insulin pens, [wearer] = the wearer's phone (else a
     * follower's), [build] = what this build has.
     */
    fun shownOn(
        pens: Boolean,
        wearer: Boolean,
        build: Set<BuildFeature> = BuildFeature.entries.toSet(),
    ): Boolean =
        (pens || !pensOnly) &&
            (needs == null || needs in build) &&
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
    SettingsFrom(DsR.drawable.ds_ic_tune, Category.Blue),
    Ranges(DsR.drawable.ds_ic_dia_range, Category.Purple),
    Graph(DsR.drawable.ds_ic_monitoring, Category.Purple),
    StatusBar(DsR.drawable.ds_ic_smartphone, Category.Indigo),
    LockScreen(DsR.drawable.ds_ic_screen_lock_portrait, Category.Teal),
    Sensor(DsR.drawable.ds_ic_dia_sensor, Category.Green),
    SensorEnd(DsR.drawable.ds_ic_hourglass_top, Category.Orange),
    Sharing(DsR.drawable.ds_ic_group, Category.Blue),
    Apps(DsR.drawable.ds_ic_apps, Category.Orange),
    Pens(DsR.drawable.ds_ic_dia_pen, Category.Blue),
    Profile(DsR.drawable.ds_ic_account_circle, Category.Blue),
    Backup(DsR.drawable.ds_ic_backup, Category.Green),
    Export(DsR.drawable.ds_ic_file_export, Category.Yellow),
    Advanced(DsR.drawable.ds_ic_code, Category.Grey),
    About(DsR.drawable.ds_ic_info, Category.Grey),
    ;

    companion object {
        /** The section a page is in (an alert's page is in Alerts, the log in Advanced…). */
        fun of(target: SearchTarget): Section =
            when (target) {
                is SearchTarget.Other -> {
                    when (target.page) {
                        OtherPage.Sensor -> Sensor
                        OtherPage.Sharing -> Sharing
                        OtherPage.Apps -> Apps
                        OtherPage.Profile -> Profile
                        OtherPage.Backup -> Backup
                        OtherPage.Export -> Export
                    }
                }

                is SearchTarget.Page -> {
                    when (SettingsIndex.path(target.route).first()) {
                        SettingsRoute.Reliability -> Reliability
                        SettingsRoute.SettingsFrom -> SettingsFrom
                        SettingsRoute.Ranges -> Ranges
                        SettingsRoute.Graph -> Graph
                        SettingsRoute.StatusBar -> StatusBar
                        SettingsRoute.LockScreen -> LockScreen
                        SettingsRoute.SensorEnd -> SensorEnd
                        SettingsRoute.Pens -> Pens
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
            SettingsRoute.Ranges -> R.string.st_ranges_title
            SettingsRoute.Graph -> R.string.st_graph_title
            SettingsRoute.GraphMarks -> R.string.st_marks_title
            SettingsRoute.LockScreen -> R.string.st_lock_title
            SettingsRoute.Pens -> R.string.st_pens_title
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
            is SettingsRoute.AlertKindPage, SettingsRoute.NightAlerts, SettingsRoute.ThresholdReview -> SettingsRoute.Alerts
            is SettingsRoute.NightKindPage -> SettingsRoute.NightAlerts
            SettingsRoute.GraphMarks -> SettingsRoute.Graph
            SettingsRoute.Log -> SettingsRoute.Advanced
            is SettingsRoute.Pen -> SettingsRoute.Pens
            is SettingsRoute.PenDoses -> SettingsRoute.Pen(route.serial)
            else -> null
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
        needs: BuildFeature? = null,
    ) {
        val on = SearchTarget.Page(route)
        val trail = trail(route)
        for ((t, s) in rows) add(SearchEntry(t, on, trail, summary = s, group = group, pensOnly = pensOnly, seen = seen, needs = needs))
    }

    /** The row of [route]'s parent page that opens [route] (titled with [route]'s title): the result opens [route]. */
    private fun MutableList<SearchEntry>.pageRow(
        route: SettingsRoute,
        @StringRes group: Int?,
        @StringRes summary: Int? = null,
    ) {
        val parent = checkNotNull(parent(route))
        val title = checkNotNull(pageTitle(route))
        add(SearchEntry(title, SearchTarget.Page(parent), trail(parent), SearchTarget.Page(route), summary, group, highlight = null))
    }

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
            // the overview's rows: the pages themselves
            add(overview(R.string.st_alerts_title, R.string.st_overview_alerts_on, SearchTarget.Page(SettingsRoute.Alerts)))
            add(overview(R.string.st_reliability_title, R.string.st_reliability_ready, SearchTarget.Page(SettingsRoute.Reliability)))
            add(overview(R.string.st_ranges_title, R.string.st_overview_ranges, SearchTarget.Page(SettingsRoute.Ranges)))
            add(overview(R.string.st_graph_title, R.string.st_overview_graph, SearchTarget.Page(SettingsRoute.Graph)))
            add(overview(R.string.st_statusbar_title, R.string.st_overview_statusbar, SearchTarget.Page(SettingsRoute.StatusBar)))
            add(overview(R.string.st_lock_title, R.string.st_overview_lock, SearchTarget.Page(SettingsRoute.LockScreen)))
            add(overview(R.string.st_sensor, R.string.st_sensor_summary, SearchTarget.Other(OtherPage.Sensor)))
            add(overview(R.string.st_sensorend_title, R.string.st_overview_sensorend, SearchTarget.Page(SettingsRoute.SensorEnd)))
            add(overview(R.string.st_sharing, R.string.st_sharing_summary, SearchTarget.Other(OtherPage.Sharing)))
            add(overview(R.string.st_apps, R.string.st_apps_summary, SearchTarget.Other(OtherPage.Apps)))
            add(
                overview(
                    R.string.st_pens_title,
                    R.string.st_overview_pens,
                    SearchTarget.Page(SettingsRoute.Pens),
                    pensOnly = true,
                    keywords = R.string.st_pens_keywords,
                ),
            )
            add(overview(R.string.st_backup_title, null, SearchTarget.Other(OtherPage.Backup), seen = Seen.Wearer))
            add(overview(R.string.st_export_title, R.string.st_export_summary, SearchTarget.Other(OtherPage.Export)))
            add(overview(R.string.st_adv_title, R.string.st_adv_overview_summary, SearchTarget.Page(SettingsRoute.Advanced)))
            add(overview(R.string.st_about_title, null, SearchTarget.Page(SettingsRoute.About)))
            add(overview(R.string.st_profile_title, R.string.st_profile_summary, SearchTarget.Other(OtherPage.Profile), seen = Seen.Wearer))
            add(overview(R.string.st_from_title, null, SearchTarget.Page(SettingsRoute.SettingsFrom), seen = Seen.Follower))
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
            sensor(
                R.string.st_sensor_source,
                R.string.st_sensor_source to null,
                R.string.st_sensor_source_direct to R.string.st_sensor_source_direct_why,
                R.string.st_sensor_source_juggluco to R.string.st_sensor_source_juggluco_why,
            )
            sensor(R.string.st_sensor_juggluco, R.string.st_sensor_juggluco_secret to null)
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
            rows(
                alerts,
                R.string.st_quiet_title,
                R.string.st_quiet_insulin to null,
                R.string.st_quiet_carbs to null,
                R.string.st_quiet_falling to null,
                R.string.st_quiet_length to null,
            )
            rows(
                alerts,
                R.string.st_wait_title,
                R.string.st_wait_low to R.string.st_wait_footer,
                R.string.st_wait_high to R.string.st_wait_footer,
                R.string.st_wait_margin to R.string.st_wait_footer,
            )
            rows(
                alerts,
                R.string.st_actions_title,
                R.string.st_action_mute to null,
                R.string.st_action_relax to null,
                R.string.st_action_back_mgdl to null,
                R.string.st_action_back_min to null,
            )
            rows(
                alerts,
                R.string.st_sounds_title,
                R.string.st_sound_pack to null,
                R.string.st_shuffle to null,
                R.string.st_notification_settings to R.string.st_notification_settings_summary,
            )
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
            // Alerts › Night alerts, and each alert's night rule
            rows(
                SettingsRoute.NightAlerts,
                null,
                R.string.st_night_use to null,
                R.string.st_night_from to null,
                R.string.st_night_to to null,
            )
            for ((kind, group) in kindGroups) pageRow(SettingsRoute.NightKindPage(kind), group)
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

            rows(
                SettingsRoute.Ranges,
                null,
                R.string.st_unit to R.string.st_unit_footer,
                R.string.st_band_very_low to null,
                R.string.st_band_low to null,
                R.string.st_band_tight to null,
                R.string.st_band_high to null,
                R.string.st_band_very_high to null,
                R.string.st_band_extremely_high to null,
                R.string.st_ranges_reset to R.string.st_ranges_reset_summary,
            )

            val graph = SettingsRoute.Graph
            rows(
                graph,
                R.string.st_graph_lines,
                R.string.st_graph_lines to null,
                R.string.st_graph_line_add to null,
                R.string.st_graph_grid to null,
            )
            rows(
                graph,
                R.string.st_graph_hours,
                R.string.st_graph_hours to null,
                R.string.st_graph_hours_default to null,
                R.string.st_place_app to null,
                R.string.st_place_notification to null,
                R.string.st_place_widgets to null,
            )
            rows(
                graph,
                R.string.st_graph_beside,
                R.string.st_graph_rings to null,
                R.string.st_slot_main to null,
                R.string.st_slot_second to null,
                R.string.st_slot_third to null,
                R.string.st_last to null,
                R.string.st_last_else to null,
                R.string.st_last_else_period to null,
                R.string.st_last_else_faded to null,
                R.string.st_ring_counts to null,
                R.string.st_graph_marks to R.string.st_graph_marks_summary,
            )
            rows(
                graph,
                R.string.st_graph_beside_graph,
                R.string.st_graph_side to R.string.st_graph_side_summary,
                R.string.st_graph_crown to null,
            )
            pageRow(SettingsRoute.GraphMarks, null)
            rows(graph, R.string.st_graph_sensor, R.string.st_graph_sensor to null)
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
                needs = BuildFeature.Root,
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
            rows(pens, R.string.st_pens_yours, R.string.st_pens_yours to null, pensOnly = true)
            rows(
                pens,
                R.string.st_pens_detection,
                R.string.st_pens_prime_below to null,
                R.string.st_pens_prime_before to null,
                R.string.st_pens_cluster to null,
                R.string.st_pens_pending to null,
                R.string.st_pens_offer to null,
                R.string.st_pens_match to null,
                R.string.st_pens_match_units to null,
                R.string.st_pens_match_share to null,
                R.string.st_pens_reset to R.string.st_pens_reset_summary,
                pensOnly = true,
            )

            val statusBar = SettingsRoute.StatusBar
            rows(
                statusBar,
                null,
                R.string.st_statusbar_chip to R.string.st_statusbar_chip_summary,
                R.string.st_statusbar_number_icon to R.string.st_statusbar_number_icon_summary,
                R.string.st_statusbar_lock_screen to R.string.st_statusbar_lock_screen_on,
            )
            rows(
                statusBar,
                R.string.st_root_group,
                R.string.st_root_value to null,
                R.string.st_root_colour to null,
                R.string.st_root_placement to null,
                R.string.st_root_hide_icon to R.string.st_root_hide_icon_summary,
                needs = BuildFeature.Root,
            )

            val sensorEnd = SettingsRoute.SensorEnd
            rows(sensorEnd, null, R.string.st_sensorend_current to null)
            rows(sensorEnd, R.string.st_sensorend_reminders, R.string.st_sensorend_reminders to null, R.string.st_sensorend_add to null)
            rows(sensorEnd, null, R.string.st_sensorend_unlock to R.string.st_sensorend_unlock_summary)
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
            rows(sensorEnd, R.string.st_sensorend_after, R.string.st_sensorend_again to null)

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
                    needs = BuildFeature.UpdateCheck,
                ),
            )
            rows(
                about,
                R.string.st_update_group,
                R.string.st_update_auto_checks to R.string.st_update_auto_checks_summary,
                R.string.st_update_auto_install to R.string.st_update_auto_install_summary,
                needs = BuildFeature.UpdateCheck,
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
            rows(
                advanced,
                R.string.st_adv_file,
                R.string.st_adv_export to R.string.st_adv_export_summary,
                R.string.st_adv_import to R.string.st_adv_import_summary,
                R.string.st_adv_reset to R.string.st_adv_reset_summary,
            )
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
