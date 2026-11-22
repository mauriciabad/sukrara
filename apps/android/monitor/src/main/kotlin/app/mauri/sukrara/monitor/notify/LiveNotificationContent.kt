// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import androidx.annotation.StringRes
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.NotMonitoringReason
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.RangeState
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.monitor.R
import java.time.Instant

/** Text from resources, resolved when the notification is built (so this model stays testable on the JVM). */
data class UiText(
    @param:StringRes val res: Int,
    val args: List<Any> = emptyList(),
)

/**
 * The status-bar settings and what Android allows, as the glucose notification needs them: [chip] = Settings › Status
 * bar › *Glucose notification* (the chip, else the card with the graph); [promotionAllowed] = Android lets the app
 * post Live Updates now; [rootValue] = the root value is in the status bar (the card then, automatically: the bar
 * already shows the value); [icon] = the notification's icon while there's no chip (*Nothing* posts Sukrara's icon);
 * [colorize] = the card in the range colour; [cardKey] = everything the custom card draws besides the hub state
 * (treatments, graph settings, theme, the minute), so equal contents mean the same card.
 */
data class NotificationPrefs(
    val chip: Boolean = true,
    val rootValue: Boolean = false,
    val colorize: Boolean = false,
    val promotionAllowed: Boolean = false,
    val icon: NotificationIcon = NotificationIcon.DEFAULT,
    val cardKey: Any? = null,
    val sharing: SharingLine? = null,
    /** The person's ranges: the title says an out-of-range value's range in words (#26). */
    val bands: Bands = Bands(),
    /** The person's glucose unit: the title's value and unit (`general.unit`). */
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    /** The value shows on the lock screen (`statusbar.lockScreen`, #94); off = private, a public "Monitoring" there. */
    val lockScreen: Boolean = true,
    /**
     * The lock-screen widget is on and *Hide the notification* too (`lock.widget`, `lock.hideNotification`): the posts
     * the widget covers are marked for the System UI module ([LiveNotificationContent.hideUnderWidget]).
     */
    val lockHide: Boolean = false,
)

/**
 * Who can read along, as the glucose notification's sub text says it ("2 followers · 1 nearby · Nightscout API on
 * Wi-Fi"): [followers] = the wearer's active followers, [nearby] = follower devices connected over Bluetooth now (both
 * the wearer's only), [wifi] = the Nightscout API listens on the home Wi-Fi now (either role: devices on the network
 * can read). Null = none of it on this phone.
 */
data class SharingLine(
    val followers: Int,
    val nearby: Int,
    val wifi: Boolean = false,
)

/**
 * What the glucose notification shows, decided from the hub state and [NotificationPrefs] alone. Equal contents = the
 * same notification (the notifier skips re-posting it). The value, trend mark and freshness come from the card's face
 * rule ([face], `GlucoseCard.face`), never formatted here.
 */
data class LiveNotificationContent(
    val title: UiText,
    val text: UiText?,
    /** The value is struck through (5–20 min old). */
    val strike: Boolean,
    /** The Live Update chip's text ("138"; its icon is the arrow); null = icon only. */
    val chip: UiText?,
    val icon: Icon,
    /** The trend's rate (mg/dL/min) the [Icon.TREND] arrow turns by and its band (doubled at ±3; contracts/trend). */
    val trendRate: Double? = null,
    val trendBand: Int? = null,
    /** The reading's time, shown once it's late. */
    val readingAt: Instant?,
    val tap: Target,
    val actions: List<Action>,
    /** How a value shows; the states without one are always [Layout.TEXT]. */
    val layout: Layout = Layout.TEXT,
    /** The face's decision for the value ([Icon.NUMBER], the card, MetricStyle); null without a value. */
    val look: GlucoseLook? = null,
    /** The value's reading (mg/dL), for the card's range colour and the metric's style. */
    val mgdl: Int? = null,
    /** [NotificationPrefs.cardKey] while [layout] is [Layout.CARD]. */
    val cardKey: Any? = null,
    /**
     * The sub text ([NotificationPrefs.sharing]): a wearer's followers, and the Nightscout API on the Wi-Fi in either
     * role; null with none of it.
     */
    val sharing: SharingLine? = null,
    /** [NotificationPrefs.lockScreen]: public on the lock screen, else private with a public "Monitoring". */
    val lockScreen: Boolean = true,
    /**
     * The value's range in words for the Live Update's title on Android 17 (*In range*, *Low*, *High*…: the big value
     * is the metric's), null without a value.
     */
    val rangeWords: UiText? = null,
    /** [NotificationPrefs.colorize]: the card in the range colour, else the system's notification colour. */
    val colorize: Boolean = false,
    /**
     * The lock-screen widget says all of it ([NotificationPrefs.lockHide] on): a state the card draws (a value, struck
     * or late, no readings, a follower's ended sensor, warming up), no button (*Set up sensor*, *Replace sensor*) and
     * no line the card doesn't say (Bluetooth off, the sensor app missing, LibreView's maintenance, a value's ended
     * sensor). The post is then marked for the System UI module ([app.mauri.sukrara.monitor.systemui.LockHide]) unless
     * an alert is on it. The sub text (followers, the Nightscout API) never keeps it (the owner, 2026-11-22).
     */
    val hideUnderWidget: Boolean = false,
) {
    /** [TREND] = the renderer's arrow for [trendRate]; [NUMBER] = the value and its trend, [VALUE] = the value alone; the others are the states' icons. */
    enum class Icon { TREND, NO_TREND, NUMBER, VALUE, NO_READINGS, SENSOR_ENDED, APP }

    enum class Layout {
        /** A promoted Live Update: the chip (arrow + value), MetricStyle expanded on Android 17. */
        LIVE_UPDATE,

        /** Colorized in the range colour with the renderer's header row and chart (no chip). */
        CARD,

        /** Title and text only (no value, or nothing better allowed). */
        TEXT,
    }

    sealed interface Target {
        /**
         * The quick "Add treatment" dialog (its focused field is THE kinds' order's first, decided as it opens).
         * [alertKind]: the alert the glucose notification carries, so opening it answers that alert (#39).
         */
        data class QuickTreatment(
            val alertKind: String? = null,
        ) : Target

        data object OpenApp : Target

        data object SensorSetup : Target
    }

    data class Action(val label: UiText, val target: Target)

    companion object {
        private val REPLACE_SENSOR = Action(UiText(R.string.monitor_action_replace_sensor), Target.SensorSetup)

        /**
         * The notification for [state] at [now]. Order: setup not done → the sensor's own states (ended, warming up)
         * → not monitoring yet → waiting for a first value → the value as the face shows it.
         */
        fun from(
            state: GlucoseState,
            now: Instant,
            face: (GlucoseState, Instant, GlucoseUnit) -> GlucoseLook,
            prefs: NotificationPrefs = NotificationPrefs(),
        ): LiveNotificationContent {
            if (state.notMonitoring == NotMonitoringReason.SetupIncomplete) return notMonitoring(state).copy(lockScreen = prefs.lockScreen)
            val sharing =
                prefs.sharing?.let { s ->
                    // followers are your glucose's, read here; the API on the Wi-Fi is any source's
                    val line = if (state.spec?.owner == true) s else s.copy(followers = 0, nearby = 0)
                    line.takeIf { state.spec != null && (it.followers > 0 || it.nearby > 0 || it.wifi) }
                }
            val c = content(state, now, face, prefs)
            return c.copy(sharing = sharing, lockScreen = prefs.lockScreen, hideUnderWidget = prefs.lockHide && covered(c))
        }

        /**
         * The widget says everything [c] says: a state the card draws ([hideUnderWidget] as the state set it), no button
         * and no second line but the late reading's time (the card's freshness says it).
         */
        private fun covered(c: LiveNotificationContent): Boolean =
            c.hideUnderWidget && c.actions.isEmpty() && (c.text == null || c.text.res == R.string.monitor_notif_reading_at)

        private fun content(
            state: GlucoseState,
            now: Instant,
            face: (GlucoseState, Instant, GlucoseUnit) -> GlucoseLook,
            prefs: NotificationPrefs,
        ): LiveNotificationContent {
            val look = face(state, now, prefs.unit)
            val detail = detail(state)
            return when (look.state) {
                GlucoseLook.State.ENDED -> {
                    plain(
                        UiText(R.string.monitor_notif_sensor_ended),
                        detail,
                        Icon.SENSOR_ENDED,
                        if (state.spec?.owner == true) Target.SensorSetup else Target.OpenApp,
                        actions =
                            if (state.spec?.owner == true) {
                                listOf(REPLACE_SENSOR)
                            } else {
                                emptyList()
                            },
                    ).copy(hideUnderWidget = true)
                }

                GlucoseLook.State.WARMING_UP -> {
                    plain(UiText(R.string.monitor_notif_warming_up, listOf(look.warmup.orEmpty())), detail, Icon.APP, Target.OpenApp)
                        .copy(hideUnderWidget = true)
                }

                GlucoseLook.State.NONE -> {
                    if (state.notMonitoring != null) {
                        notMonitoring(state)
                    } else {
                        plain(UiText(R.string.monitor_notif_waiting), detail, Icon.APP, Target.QuickTreatment())
                    }
                }

                GlucoseLook.State.NO_READINGS -> {
                    val at = state.latest!!.t
                    LiveNotificationContent(
                        title = UiText(R.string.monitor_notif_no_readings_since, listOf(at)),
                        text = detail,
                        strike = false,
                        chip = null,
                        icon = Icon.NO_READINGS,
                        readingAt = at,
                        tap = Target.QuickTreatment(),
                        actions = emptyList(),
                        hideUnderWidget = true,
                    )
                }

                GlucoseLook.State.VALUE -> {
                    value(state, look, detail, prefs)
                }
            }
        }

        /**
         * A value: a Live Update when the chip is wanted and allowed and the root value isn't in the bar, else the card
         * with the graph. The chip's arrow is its icon (the renderer's), never a text arrow too: two arrows could
         * disagree. It can't strike text through: from 5 min it shows the reading's time (with the ring icon) instead
         * of a bare number that would read as current. Without the chip the small icon is the person's
         * *Notification icon* ([NotificationIcon]). No unit anywhere (the owner, 2026-11-14: "they're obvious"); no *Open Sukrara* (the tap
         * opens Add treatment).
         */
        private fun value(
            state: GlucoseState,
            look: GlucoseLook,
            detail: UiText?,
            prefs: NotificationPrefs,
        ): LiveNotificationContent {
            val reading = state.latest!!
            val live = prefs.chip && prefs.promotionAllowed && !prefs.rootValue
            val chip =
                when {
                    !live -> null
                    look.strike -> UiText(R.string.monitor_chip_stale, listOf(reading.t))
                    else -> UiText(R.string.monitor_chip_value, listOf(look.text!!))
                }
            val trend = if (look.trend == TrendMark.ARROW) Icon.TREND else Icon.NO_TREND
            val icon =
                when {
                    live -> {
                        trend
                    }

                    else -> {
                        when (prefs.icon) {
                            // hidden by the module while its value shows; without it the value stays in the bar
                            NotificationIcon.ValueTrend, NotificationIcon.None -> Icon.NUMBER

                            NotificationIcon.Value -> Icon.VALUE

                            NotificationIcon.Trend -> trend

                            NotificationIcon.App -> Icon.APP
                        }
                    }
                }
            val range = RangeState.shown(look, reading.mgdl, prefs.bands, null)
            return LiveNotificationContent(
                // the range in words too, never by colour alone (#26): "64 ↘ · Low", "3,6 ↘ · Baja"
                title =
                    range?.let { UiText(R.string.monitor_notif_value_range, listOf(look.text!!, look.arrowText!!, UiText(it.words))) }
                        ?: UiText(R.string.monitor_notif_value, listOf(look.text!!, look.arrowText!!)),
                // a sensor past its end whose readings keep arriving (#3): the value, and under it that it has ended
                text =
                    when {
                        look.ended -> UiText(R.string.monitor_notif_sensor_ended_line)

                        // LibreView's maintenance says why the value ages (the value's age is struck already)
                        state.source.maintenance != null -> detail

                        look.fresh.showTime -> UiText(R.string.monitor_notif_reading_at, listOf(reading.t))

                        else -> detail
                    },
                strike = look.strike,
                chip = chip,
                icon = icon,
                trendRate = look.rate.takeIf { look.trend == TrendMark.ARROW },
                trendBand = look.band.takeIf { look.trend == TrendMark.ARROW },
                readingAt = if (look.fresh.showTime) reading.t else null,
                tap = Target.QuickTreatment(),
                actions = if (look.ended && state.spec?.owner == true) listOf(REPLACE_SENSOR) else emptyList(),
                layout = if (live) Layout.LIVE_UPDATE else Layout.CARD,
                look = look,
                mgdl = reading.mgdl,
                cardKey = if (live) null else prefs.cardKey,
                rangeWords = UiText(range?.words ?: R.string.monitor_range_in_range),
                colorize = prefs.colorize,
                hideUnderWidget = true,
            )
        }

        /** Not monitoring: *Set up sensor* for your glucose; someone else's or a stream's tap opens the app (no button repeating it). */
        private fun notMonitoring(state: GlucoseState): LiveNotificationContent {
            if (state.spec?.owner == false) return plain(UiText(R.string.monitor_notif_not_monitoring), null, Icon.APP, Target.OpenApp)
            val setUp = Action(UiText(R.string.monitor_action_set_up_sensor), Target.SensorSetup)
            return plain(UiText(R.string.monitor_notif_not_monitoring), null, Icon.APP, setUp.target, listOf(setUp))
        }

        private fun plain(
            title: UiText,
            text: UiText?,
            icon: Icon,
            tap: Target,
            actions: List<Action> = emptyList(),
        ) = LiveNotificationContent(title, text, strike = false, chip = null, icon = icon, readingAt = null, tap = tap, actions = actions)

        /**
         * The second line (under the value or state): LibreView's maintenance and its end (docs/45: an expected wait,
         * said as soon as the source says it), or what the sensor needs.
         */
        private fun detail(state: GlucoseState): UiText? =
            state.source.maintenance?.let { m ->
                m.until?.let { UiText(R.string.monitor_notif_maintenance_until, listOf(it)) } ?: UiText(R.string.monitor_notif_maintenance)
            } ?: when (state.sensor) {
                SensorState.BluetoothOff -> UiText(R.string.monitor_notif_bluetooth_off)
                SensorState.CoreMissing -> UiText(R.string.monitor_notif_core_missing)
                else -> null
            }
    }
}
