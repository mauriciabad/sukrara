// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import androidx.annotation.StringRes
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute

/** Settings pages that belong to other features: the overview opens them ([SettingsSearchBar]'s `onOpenOther`). */
enum class OtherPage { Sensor, Sharing, Apps, Profile, Backup, Export }

/** Where a search result leads: a settings page, or another feature's page. */
sealed interface SearchTarget {
    data class Page(val route: SettingsRoute) : SearchTarget

    data class Other(val page: OtherPage) : SearchTarget
}

/** Whose phone shows a row: every phone, only the wearer's or only a follower's. */
enum class Seen { Everyone, Wearer, Follower }

/** What only the GitHub build has (the Google Play build has neither: docs/20-publishing.md › Flavors). */
enum class BuildFeature { Root, UpdateCheck }

/**
 * One row the search finds: its [title] and [summary] as the page shows them (string resources, no format
 * arguments), the [page] it is on (the breadcrumb, after its [parent] page if any: "Alerts › Low") and where tapping
 * it leads. [pensOnly] rows exist only on a phone that reads insulin pens, [seen] says on whose phone the row shows,
 * [needs] which build has it (null: both).
 */
data class SearchEntry(
    @StringRes val title: Int,
    @StringRes val page: Int,
    val target: SearchTarget,
    @StringRes val summary: Int? = null,
    val pensOnly: Boolean = false,
    val seen: Seen = Seen.Everyone,
    @StringRes val parent: Int? = null,
    val needs: BuildFeature? = null,
) {
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
 * THE search index: one entry per row of every settings page, in page order. A row added to a page is added here
 * (the same string resources), so search and pages can't drift apart; [pageTitle] is exhaustive, so a new page
 * doesn't compile until it has a title here, and `SettingsIndexTest` renders every page and checks each row on it is
 * here (and each entry is on its page).
 */
object SettingsIndex {
    /** The breadcrumb of each page; null for the overview (its rows are the pages) and per-pen pages (no fixed rows). */
    @StringRes
    fun pageTitle(route: SettingsRoute): Int? =
        when (route) {
            SettingsRoute.Overview, is SettingsRoute.Pen, is SettingsRoute.PenDoses -> null
            SettingsRoute.Alerts -> R.string.st_alerts_title
            is SettingsRoute.AlertKindPage -> kindNames.getValue(route.kind)
            SettingsRoute.NightAlerts, is SettingsRoute.NightKindPage -> R.string.st_night_title
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

    /** The page a page is under in the breadcrumb: an alert's page is under Alerts. */
    @StringRes
    fun parent(route: SettingsRoute): Int? = if (route is SettingsRoute.AlertKindPage) R.string.st_alerts_title else null

    private fun page(
        route: SettingsRoute,
        vararg rows: Pair<Int, Int?>,
        pensOnly: Boolean = false,
        seen: Seen = Seen.Everyone,
        needs: BuildFeature? = null,
    ): List<SearchEntry> {
        val title = checkNotNull(pageTitle(route)) { "$route has no page title" }
        val target = SearchTarget.Page(route)
        return rows.map { (t, s) -> SearchEntry(t, title, target, s, pensOnly, seen, parent(route), needs) }
    }

    private fun overview(
        title: Int,
        summary: Int?,
        target: SearchTarget,
        pensOnly: Boolean = false,
        seen: Seen = Seen.Everyone,
    ) = SearchEntry(title, R.string.st_settings_title, target, summary, pensOnly, seen)

    /** A row of Settings › Sensor (another feature's page). */
    private fun sensor(
        title: Int,
        summary: Int? = null,
    ) = SearchEntry(title, R.string.st_sensor, SearchTarget.Other(OtherPage.Sensor), summary)

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
            add(overview(R.string.st_pens_title, R.string.st_overview_pens, SearchTarget.Page(SettingsRoute.Pens), pensOnly = true))
            add(overview(R.string.st_backup_title, null, SearchTarget.Other(OtherPage.Backup), seen = Seen.Wearer))
            add(overview(R.string.st_export_title, R.string.st_export_summary, SearchTarget.Other(OtherPage.Export)))
            add(overview(R.string.st_adv_title, R.string.st_adv_overview_summary, SearchTarget.Page(SettingsRoute.Advanced)))
            add(overview(R.string.st_about_title, null, SearchTarget.Page(SettingsRoute.About)))
            add(overview(R.string.st_profile_title, R.string.st_profile_summary, SearchTarget.Other(OtherPage.Profile), seen = Seen.Wearer))
            add(overview(R.string.st_from_title, null, SearchTarget.Page(SettingsRoute.SettingsFrom), seen = Seen.Follower))
            // Settings › Sensor's own rows (another feature's page)
            add(sensor(R.string.st_sensor_reading_from))
            add(sensor(R.string.st_sensor_status))
            add(sensor(R.string.st_sensor_serial))
            add(sensor(R.string.st_sensor_started))
            add(sensor(R.string.st_sensor_ends))
            add(sensor(R.string.st_sensor_pair_new, R.string.st_sensor_pair_new_summary))
            add(sensor(R.string.st_sensor_source))
            add(sensor(R.string.st_sensor_source_direct, R.string.st_sensor_source_direct_why))
            add(sensor(R.string.st_sensor_source_juggluco, R.string.st_sensor_source_juggluco_why))
            add(sensor(R.string.st_sensor_juggluco_secret))
            add(sensor(R.string.st_sensor_open_on_scan))
            add(sensor(R.string.st_sensor_forget, R.string.st_sensor_forget_summary))
            add(sensor(R.string.st_sensor_libreview))
            add(sensor(R.string.st_sensor_signed_in_as))
            add(sensor(R.string.st_sensor_receiver_id))
            add(sensor(R.string.st_sensor_sign_out))

            addAll(
                page(
                    SettingsRoute.Alerts,
                    R.string.st_alerts_main to null,
                    R.string.st_mute_all to null,
                    R.string.st_review_title to null,
                    R.string.st_quiet_insulin to null,
                    R.string.st_quiet_carbs to null,
                    R.string.st_quiet_falling to null,
                    R.string.st_quiet_length to null,
                    R.string.st_night_title to R.string.st_night_use_summary,
                    R.string.st_escalation_on to R.string.st_escalation_on_summary,
                    R.string.st_escalation_min to R.string.st_escalation_footer,
                    R.string.st_escalation_follow to R.string.st_escalation_follow_summary,
                    R.string.st_wait_low to R.string.st_wait_footer,
                    R.string.st_wait_high to R.string.st_wait_footer,
                    R.string.st_wait_margin to R.string.st_wait_footer,
                    R.string.st_action_mute to null,
                    R.string.st_action_relax to null,
                    R.string.st_action_back_mgdl to null,
                    R.string.st_action_back_min to null,
                    R.string.st_sound_pack to null,
                    R.string.st_shuffle to null,
                    R.string.st_notification_settings to R.string.st_notification_settings_summary,
                ),
            )
            // each alert's own page, so a hit opens that alert ("Remind every · In Alerts › Low")
            for ((kind, name) in kindNames) {
                addAll(
                    page(
                        SettingsRoute.AlertKindPage(kind),
                        name to null,
                        R.string.st_threshold to null,
                        R.string.st_repeat to null,
                        R.string.st_alarm to R.string.st_alarm_summary,
                        R.string.st_sound to null,
                        R.string.st_notification_settings to R.string.st_notification_settings_summary,
                        R.string.st_play_sound to null,
                        R.string.st_play_vibrate to null,
                        R.string.st_send_test to R.string.st_send_test_summary,
                        R.string.st_channel_settings to R.string.st_channel_settings_summary,
                    ),
                )
            }
            addAll(
                page(
                    SettingsRoute.NightAlerts,
                    R.string.st_night_title to R.string.st_night_use_summary,
                    R.string.st_night_from to null,
                    R.string.st_night_to to null,
                ),
            )
            addAll(
                page(
                    SettingsRoute.Ranges,
                    R.string.st_unit to R.string.st_unit_footer,
                    R.string.st_band_very_low to null,
                    R.string.st_band_low to null,
                    R.string.st_band_tight to null,
                    R.string.st_band_high to null,
                    R.string.st_band_very_high to null,
                    R.string.st_band_extremely_high to null,
                    R.string.st_ranges_reset to R.string.st_ranges_reset_summary,
                ),
            )
            addAll(
                page(
                    SettingsRoute.Graph,
                    R.string.st_graph_lines to null,
                    R.string.st_graph_line_add to null,
                    R.string.st_graph_grid to null,
                    R.string.st_graph_hours to null,
                    R.string.st_graph_hours_default to null,
                    R.string.st_place_app to null,
                    R.string.st_place_notification to null,
                    R.string.st_place_widgets to null,
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
                    R.string.st_graph_side to R.string.st_graph_side_summary,
                    R.string.st_graph_crown to null,
                    R.string.st_marks_title to null,
                    R.string.st_graph_sensor to null,
                    R.string.st_graph_reset_all to null,
                ),
            )
            addAll(
                page(
                    SettingsRoute.GraphMarks,
                    R.string.st_marks_point to null,
                    R.string.st_marks_line to R.string.st_marks_line_summary,
                    R.string.st_marks_amount to null,
                    R.string.st_marks_time to null,
                    R.string.st_marks_reset to null,
                ),
            )
            addAll(page(SettingsRoute.LockScreen, R.string.st_lock_time to null, R.string.st_lock_treatment to null))
            addAll(
                page(
                    SettingsRoute.Pens,
                    R.string.st_pens_reading to null,
                    R.string.st_pens_notify to R.string.st_pens_notify_summary,
                    R.string.st_pens_yours to null,
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
                ),
            )
            addAll(page(SettingsRoute.Reliability, R.string.st_reliability_title to R.string.st_reliability_ready))
            addAll(
                page(
                    SettingsRoute.StatusBar,
                    R.string.st_statusbar_chip to R.string.st_statusbar_chip_summary,
                    R.string.st_statusbar_number_icon to R.string.st_statusbar_number_icon_summary,
                    R.string.st_statusbar_lock_screen to R.string.st_statusbar_lock_screen_on,
                ),
            )
            addAll(
                page(
                    SettingsRoute.StatusBar,
                    R.string.st_root_value to null,
                    R.string.st_root_colour to null,
                    R.string.st_root_placement to null,
                    R.string.st_root_hide_icon to R.string.st_root_hide_icon_summary,
                    needs = BuildFeature.Root,
                ),
            )
            addAll(
                page(
                    SettingsRoute.SensorEnd,
                    R.string.st_sensorend_current to null,
                    R.string.st_sensorend_reminders to null,
                    R.string.st_sensorend_add to null,
                    R.string.st_sensorend_unlock to R.string.st_sensorend_unlock_summary,
                    R.string.st_sensorend_evening to null,
                    R.string.st_sensorend_bedtime to null,
                    R.string.st_sensorend_wakeup to null,
                    R.string.st_sensorend_countdown to R.string.st_sensorend_countdown_summary,
                    R.string.st_sensorend_countdown_hours to null,
                    R.string.st_sensorend_warmup to R.string.st_sensorend_warmup_summary,
                    R.string.st_sensorend_again to null,
                ),
            )
            addAll(
                page(
                    SettingsRoute.About,
                    R.string.st_about_version to null,
                ),
            )
            addAll(page(SettingsRoute.About, R.string.st_update_check_again to null, needs = BuildFeature.UpdateCheck))
            addAll(
                page(
                    SettingsRoute.About,
                    R.string.st_report_problem to R.string.st_report_problem_summary,
                    R.string.st_source_code to R.string.st_license,
                    R.string.st_privacy to R.string.st_privacy_summary,
                ),
            )
            addAll(page(SettingsRoute.ThresholdReview, R.string.st_review_confirm to null))
            addAll(
                page(
                    SettingsRoute.Advanced,
                    R.string.st_adv_export to R.string.st_adv_export_summary,
                    R.string.st_adv_import to R.string.st_adv_import_summary,
                    R.string.st_adv_reset to R.string.st_adv_reset_summary,
                    R.string.st_adv_relays to null,
                    R.string.st_adv_check_now to null,
                    R.string.st_adv_log to R.string.st_adv_log_summary,
                    R.string.st_adv_notification to null,
                ),
            )
            addAll(page(SettingsRoute.Advanced, R.string.st_adv_identity to null, R.string.st_adv_sent to null, seen = Seen.Wearer))
            addAll(page(SettingsRoute.Advanced, R.string.st_adv_this_phone to null, R.string.st_adv_received to null, seen = Seen.Follower))
            addAll(
                page(
                    SettingsRoute.SettingsFrom,
                    R.string.st_from_wearer to R.string.st_from_wearer_summary,
                    R.string.st_from_default to R.string.st_from_default_summary,
                    R.string.st_from_custom to R.string.st_from_custom_summary,
                    R.string.st_from_copy to R.string.st_from_copy_summary,
                    R.string.st_from_reset to R.string.st_from_reset_summary,
                    seen = Seen.Follower,
                ),
            )
        }
}
