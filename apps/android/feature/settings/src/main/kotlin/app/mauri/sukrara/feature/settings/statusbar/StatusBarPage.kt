// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.data.settings.NotificationStyleSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseNotificationChannel
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.storage.SettingSpec
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.notification.GlucoseNotificationChoice
import app.mauri.sukrara.designsystem.root.AccessProblem
import app.mauri.sukrara.designsystem.root.AccessRunner
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.GraphSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Settings › Status bar & notification (docs/36 §11, §14): where the glucose shows in the status bar decides what the
 * glucose notification can be, so the one choice is here, and under it only the rows of the chosen one.
 */
data class NotificationPagePrefs(
    val style: NotificationStyle,
    /** The notification's icon for the choice in use ([NotificationStyleSettings.icon]). */
    val icon: NotificationIcon,
    /** The glucose notification's value on the lock screen (#94). */
    val lockScreen: Boolean,
    /** The card in the range colour. */
    val colorize: Boolean,
    /** The root value's placement ([app.mauri.sukrara.core.data.settings.HookPosition]) and colour. */
    val position: String,
    val rootColor: Boolean,
    /** Alerts ring in the glucose notification (*Separate alert notifications* off). */
    val alertsInside: Boolean,
    /** The newest reading as the card shows it, for the icon picker's pictures; null without one. */
    val look: GlucoseLook? = null,
)

/**
 * What Android allows and shows now (re-read on resume: the person changes them in Android's settings):
 * [liveUpdates] = Sukrara may post Live Updates, [shown] = the glucose notification's channel, its group and the app's
 * notifications are on.
 */
data class AndroidNotification(
    val liveUpdates: Boolean,
    val shown: Boolean,
)

class StatusBarViewModel(
    private val settings: SettingsRepository,
    private val systemAccess: SystemAccessPort,
    recent: Flow<List<Reading>> = flowOf(emptyList()),
    private val clock: Clock? = null,
) : ViewModel() {
    private val look: Flow<GlucoseLook?> =
        combine(recent, settings.flow(GraphSettings.GlucoseUnitSetting)) { readings, unit ->
            clock?.let { NotificationIconPreview.look(readings, it.now(), GlucoseUnit.of(unit)) }
        }.onStart { emit(null) }

    val prefs: StateFlow<NotificationPagePrefs?> =
        combine(
            NotificationStyleSettings.flow(settings),
            NotificationStyleSettings.iconFlow(settings),
            combine(settings.flow(SurfaceSettings.LockScreen), settings.flow(SurfaceSettings.Colorize), ::Pair),
            combine(settings.flow(SurfaceSettings.RootPosition), settings.flow(SurfaceSettings.RootColor), ::Pair),
            combine(settings.flow(SurfaceSettings.SeparateAlerts), look, ::Pair),
        ) { style, icon, (lock, colorize), (position, color), (separate, look) ->
            NotificationPagePrefs(style, icon, lock, colorize, position, color, alertsInside = !separate, look = look)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val access: StateFlow<SystemAccessState> = systemAccess.state

    private val runner = AccessRunner(viewModelScope)

    /** A root / LSPosed action is running; [problem] = why the last one failed (shown in the card). */
    val busy: StateFlow<Boolean> = runner.busy
    val problem: StateFlow<AccessProblem?> = runner.problem

    /** The choice; the root text is also the request for root (Magisk's prompt; KernelSU / APatch answer at once). */
    fun setStyle(style: NotificationStyle) {
        viewModelScope.launch {
            NotificationStyleSettings.set(settings, style)
            if (style == NotificationStyle.Text && !systemAccess.state.value.granted) {
                runCatching { systemAccess.checkRoot() }
            }
        }
    }

    fun setIcon(icon: NotificationIcon) {
        viewModelScope.launch { NotificationStyleSettings.setIcon(settings, icon) }
    }

    fun checkRoot() =
        runner.run {
            systemAccess.checkRoot()
            null
        }

    fun requestScope() = runner.run { systemAccess.requestScope() }

    fun restart() = runner.run { systemAccess.restartSystemUi() }

    /**
     * Back on the page: look for signs of root and ask LSPosed again (no `su`); ask for root again only while the root
     * choice is on and root isn't granted yet (the person may come back from allowing Sukrara in their root manager).
     */
    fun refresh() {
        systemAccess.refresh()
        if (!systemAccess.state.value.granted && prefs.value?.style == NotificationStyle.Text) {
            viewModelScope.launch { runCatching { systemAccess.checkRoot() } }
        }
    }

    private fun <T> set(
        spec: SettingSpec<T>,
        value: T,
    ) {
        viewModelScope.launch { settings.set(spec, value) }
    }

    fun setColorize(on: Boolean) = set(SurfaceSettings.Colorize, on)

    fun setLockScreen(on: Boolean) = set(SurfaceSettings.LockScreen, on)

    fun setPosition(position: String) = set(SurfaceSettings.RootPosition, position)

    fun setRootColor(on: Boolean) = set(SurfaceSettings.RootColor, on)

    /** *Alerts in the notification* (the same setting as Alerts › *Separate alert notifications*, the other way round). */
    fun setAlertsInside(on: Boolean) = set(SurfaceSettings.SeparateAlerts, !on)
}

/** What the page does. */
class NotificationPageActions(
    val setStyle: (NotificationStyle) -> Unit = {},
    val setIcon: (NotificationIcon) -> Unit = {},
    val setColorize: (Boolean) -> Unit = {},
    val setLockScreen: (Boolean) -> Unit = {},
    val setAlertsInside: (Boolean) -> Unit = {},
    val setPosition: (String) -> Unit = {},
    val setRootColor: (Boolean) -> Unit = {},
    val allowLiveUpdates: () -> Unit = {},
    /** Android's page of the glucose notification's channel (where the person turns it on or off). */
    val openGlucoseChannel: () -> Unit = {},
    val checkRoot: () -> Unit = {},
    val requestScope: () -> Unit = {},
    val restart: () -> Unit = {},
)

/** Whether Android lets Sukrara post Live Updates now (the person can turn them off per app; re-read on resume). */
fun Context.liveUpdatesAllowed(): Boolean =
    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 &&
        runCatching { getSystemService(NotificationManager::class.java).canPostPromotedNotifications() }.getOrDefault(false)

/**
 * Whether Android shows the glucose notification: its channel, the channel's group and Sukrara's notifications are on
 * (any of them off drops every post; docs/36 §14). No channel yet (before monitoring ever ran): shown.
 */
fun Context.glucoseNotificationShown(): Boolean =
    runCatching {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(GlucoseNotificationChannel.ID) ?: return@runCatching nm.areNotificationsEnabled()
        val groupOff = channel.group?.let { nm.getNotificationChannelGroup(it)?.isBlocked } == true
        nm.areNotificationsEnabled() && channel.importance != NotificationManager.IMPORTANCE_NONE && !groupOff
    }.getOrDefault(true)

fun Context.androidNotification() = AndroidNotification(liveUpdatesAllowed(), glucoseNotificationShown())

/** Android's per-app Live Updates switch. */
fun Context.openLiveUpdateSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * Android's page of the glucose notification's channel (its on / off switch), or the app's notifications while
 * they are all off (the channel's page can't turn them on).
 */
fun Context.openGlucoseChannelSettings() {
    val appOff = runCatching { !getSystemService(NotificationManager::class.java).areNotificationsEnabled() }.getOrDefault(false)
    val intent =
        if (appOff) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, GlucoseNotificationChannel.ID)
        }.putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * The page: the choice ([GlucoseNotificationChoice], shared with setup), then only what the chosen one has, then the
 * notification's own rows while Android shows it.
 * - The chip: *Allow Live Updates for Sukrara* while Android refuses it.
 * - The notification icon and the root text: *What the icon shows* ([NotificationIconPicker]).
 * - The root text: the one step root or the module is missing (the shared card), *Placement*, *Colour by range*, and
 *   *Glucose notification* (its on / off is Android's: the row opens it).
 * - *Nothing*, or a choice that needs the notification while Android has it off: the one step in Android's settings.
 * - The notification (while shown): *Coloured background* (not the chip), *Glucose on the lock screen* (#94), *Alerts
 *   in the notification*. Without it, a footer: every alert is a notification of its own.
 */
@Composable
fun StatusBarContent(
    prefs: NotificationPagePrefs,
    android: AndroidNotification,
    access: SystemAccessState,
    busy: Boolean,
    problem: AccessProblem?,
    actions: NotificationPageActions,
    onBack: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val style = prefs.style
    // root that works now after the choice asked for it: the one confirmation the person waited for
    val haptics = rememberHaptics()
    var wasAsking by remember { mutableStateOf(access.asking) }
    LaunchedEffect(access.asking, access.granted) {
        if (wasAsking && !access.asking && access.granted) haptics.confirm()
        wasAsking = access.asking
    }
    SettingsPage(stringResource(R.string.st_statusbar_title), onBack) {
        GlucoseNotificationChoice(style, actions.setStyle, offOffered = true)
        // the step Android's settings still need for the choice: the notification on, or off
        when {
            style.needsNotification && !android.shown -> {
                StepRow(R.string.st_statusbar_turn_on, R.string.st_statusbar_turn_on_summary, actions.openGlucoseChannel)
            }

            style == NotificationStyle.Off && android.shown -> {
                StepRow(R.string.st_statusbar_turn_off, R.string.st_statusbar_turn_off_summary, actions.openGlucoseChannel)
            }

            style == NotificationStyle.Chip && !android.liveUpdates -> {
                SettingsGroup {
                    NavigationRow(
                        stringResource(R.string.st_statusbar_allow_live_updates),
                        actions.allowLiveUpdates,
                        summary = stringResource(R.string.st_statusbar_allow_live_updates_summary),
                        icon = DsR.drawable.ds_ic_tune,
                    )
                }
            }
        }
        // the chosen one's own rows (what the status bar shows)
        if (style == NotificationStyle.Text) RootChoiceRows(prefs, access, busy, problem, actions) { choosing = true }
        val shown = android.shown && style != NotificationStyle.Off
        if (shown) NotificationIconPicker(style, prefs.icon, prefs.look, access.module.active, actions.setIcon)
        // the notification's own rows
        SettingsGroup(title = stringResource(R.string.st_statusbar_notification)) {
            if (style == NotificationStyle.Text) {
                NavigationRow(
                    stringResource(R.string.st_statusbar_glucose_notification),
                    actions.openGlucoseChannel,
                    summary =
                        stringResource(
                            if (android.shown) {
                                R.string.st_statusbar_glucose_notification_on
                            } else {
                                R.string.st_statusbar_glucose_notification_off
                            },
                        ),
                    icon = if (android.shown) DsR.drawable.ds_ic_notifications else DsR.drawable.ds_ic_notifications_off,
                )
            }
            if (shown) NotificationRows(prefs, actions)
        }
        FooterText(
            stringResource(if (shown) R.string.st_statusbar_footer else R.string.st_statusbar_no_notification_footer),
        )
    }
    if (choosing) PlacementDialog(prefs.position, access.module.active, { choosing = false }, actions.setPosition)
}

/** The glucose notification's own rows, while Android shows it. */
@Composable
private fun NotificationRows(
    prefs: NotificationPagePrefs,
    actions: NotificationPageActions,
) {
    if (prefs.style != NotificationStyle.Chip) {
        SwitchRow(
            stringResource(R.string.st_statusbar_colorize),
            prefs.colorize,
            actions.setColorize,
            summary = stringResource(R.string.st_statusbar_colorize_on),
            icon = DsR.drawable.ds_ic_palette,
        )
    }
    SwitchRow(
        stringResource(R.string.st_statusbar_lock_screen),
        prefs.lockScreen,
        actions.setLockScreen,
        summary = stringResource(if (prefs.lockScreen) R.string.st_statusbar_lock_screen_on else R.string.st_statusbar_lock_screen_off),
        icon = DsR.drawable.ds_ic_screen_lock_portrait,
    )
    // the same setting as Alerts › Separate alert notifications, worded for this page (a second place)
    SwitchRow(
        stringResource(R.string.st_statusbar_alerts),
        prefs.alertsInside,
        actions.setAlertsInside,
        summary = stringResource(if (prefs.alertsInside) R.string.st_statusbar_alerts_on else R.string.st_statusbar_alerts_off),
        icon = DsR.drawable.ds_ic_notifications_active,
    )
}

/** A step in Android's settings the choice still needs (opens the glucose notification's page there). */
@Composable
private fun StepRow(
    title: Int,
    summary: Int,
    onClick: () -> Unit,
) {
    SettingsGroup {
        NavigationRow(stringResource(title), onClick, summary = stringResource(summary), icon = DsR.drawable.ds_ic_open_in_new)
    }
}

/** Under the root choice: the missing step's card, *Placement*, *Colour by range*. */
@Composable
private fun RootChoiceRows(
    prefs: NotificationPagePrefs,
    access: SystemAccessState,
    busy: Boolean,
    problem: AccessProblem?,
    actions: NotificationPageActions,
    onPlacement: () -> Unit,
) {
    RootAccessCard(access, busy, problem, actions)
    val root = access.granted
    val module = access.module.active
    val placement = stringResource(placementLabel(prefs.position))
    val needsModule = stringResource(R.string.st_root_module_needed)
    SettingsGroup {
        ValueRow(
            stringResource(R.string.st_root_placement),
            when {
                !root -> {
                    stringResource(R.string.st_root_unavailable_no_root)
                }

                app.mauri.sukrara.core.data.settings.HookPosition
                    .needsModule(prefs.position) && !module -> {
                    stringResource(
                        if (app.mauri.sukrara.core.data.settings.HookPosition
                                .isSystemIcons(prefs.position)
                        ) {
                            R.string.st_root_needs_module_icons
                        } else {
                            R.string.st_root_needs_module
                        },
                        placement,
                    )
                }

                else -> {
                    placement
                }
            },
            onPlacement,
            enabled = root,
            icon = DsR.drawable.ds_ic_align_horizontal_left,
        )
        SwitchRow(
            stringResource(R.string.st_root_colour),
            prefs.rootColor,
            actions.setRootColor,
            summary = if (root && !module) needsModule else null,
            enabled = root && module,
            icon = DsR.drawable.ds_ic_dia_range,
        )
    }
}
