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
import app.mauri.sukrara.core.model.NotificationStyle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Settings › Glucose notification (docs/36 §11; was a section of *Status bar*): where the glucose shows in the status
 * bar decides what the notification can be, so the one choice is here, and under it only the rows of the chosen one.
 */
data class NotificationPagePrefs(
    val style: NotificationStyle,
    val numberIcon: Boolean,
    /** The glucose notification's value on the lock screen (#94). */
    val lockScreen: Boolean,
    /** The card in the range colour. */
    val colorize: Boolean,
    /** The root value's placement ([app.mauri.sukrara.core.data.settings.HookPosition]) and colour. */
    val position: String,
    val rootColor: Boolean,
)

class StatusBarViewModel(
    private val settings: SettingsRepository,
    /** null: this build has no root features (Google Play): no root choice. */
    private val systemAccess: SystemAccessPort?,
) : ViewModel() {
    val rootOffered: Boolean = systemAccess != null

    val prefs: StateFlow<NotificationPagePrefs?> =
        combine(
            NotificationStyleSettings.flow(settings, rootOffered),
            settings.flow(SurfaceSettings.NumberIcon),
            settings.flow(SurfaceSettings.LockScreen),
            settings.flow(SurfaceSettings.Colorize),
            combine(settings.flow(SurfaceSettings.RootPosition), settings.flow(SurfaceSettings.RootColor), ::Pair),
        ) { style, icon, lock, colorize, (position, color) ->
            NotificationPagePrefs(style, icon, lock, colorize, position, color)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val access: StateFlow<SystemAccessState> = systemAccess?.state ?: MutableStateFlow(SystemAccessState())

    private val runner = AccessRunner(viewModelScope)

    /** A root / LSPosed action is running; [problem] = why the last one failed (shown in the card). */
    val busy: StateFlow<Boolean> = runner.busy
    val problem: StateFlow<AccessProblem?> = runner.problem

    /** The choice; the root one is also the request for root (Magisk's prompt; KernelSU / APatch answer at once). */
    fun setStyle(style: NotificationStyle) {
        viewModelScope.launch {
            NotificationStyleSettings.set(settings, style)
            if (style == NotificationStyle.Root && systemAccess?.state?.value?.granted == false) {
                runCatching { systemAccess.checkRoot() }
            }
        }
    }

    fun checkRoot() =
        runner.run {
            systemAccess?.checkRoot()
            null
        }

    fun requestScope() = runner.run { systemAccess?.requestScope() }

    fun restart() = runner.run { systemAccess?.restartSystemUi() }

    /**
     * Back on the page: look for signs of root and ask LSPosed again (no `su`); ask for root again only while the root
     * choice is on and root isn't granted yet (the person may come back from allowing Sukrara in their root manager).
     */
    fun refresh() {
        val access = systemAccess ?: return
        access.refresh()
        if (!access.state.value.granted && prefs.value?.style == NotificationStyle.Root) {
            viewModelScope.launch { runCatching { access.checkRoot() } }
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

    fun setNumberIcon(on: Boolean) = set(SurfaceSettings.NumberIcon, on)

    fun setPosition(position: String) = set(SurfaceSettings.RootPosition, position)

    fun setRootColor(on: Boolean) = set(SurfaceSettings.RootColor, on)
}

/** What the page does. */
class NotificationPageActions(
    val setStyle: (NotificationStyle) -> Unit = {},
    val setNumberIcon: (Boolean) -> Unit = {},
    val setColorize: (Boolean) -> Unit = {},
    val setLockScreen: (Boolean) -> Unit = {},
    val setPosition: (String) -> Unit = {},
    val setRootColor: (Boolean) -> Unit = {},
    val allowLiveUpdates: () -> Unit = {},
    val checkRoot: () -> Unit = {},
    val requestScope: () -> Unit = {},
    val restart: () -> Unit = {},
)

/** Whether Android lets Sukrara post Live Updates now (the person can turn them off per app; re-read on resume). */
fun Context.liveUpdatesAllowed(): Boolean =
    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 &&
        runCatching { getSystemService(NotificationManager::class.java).canPostPromotedNotifications() }.getOrDefault(false)

/** Android's per-app Live Updates switch. */
fun Context.openLiveUpdateSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * The page: the choice ([GlucoseNotificationChoice], shared with setup), then only what the chosen one has. The chip:
 * *Allow Live Updates for Sukrara* while Android refuses it. The notification icon: *Number as notification icon*.
 * The status bar text (root): the one step root or the module is missing (the shared card), *Placement*, *Colour by
 * range*. The two with the graph: *Coloured background*. All: *Glucose on the lock screen* (#94).
 */
@Composable
fun StatusBarContent(
    prefs: NotificationPagePrefs,
    rootOffered: Boolean,
    liveUpdatesAllowed: Boolean,
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
        GlucoseNotificationChoice(style, rootOffered, actions.setStyle)
        // the chosen one's own rows (what the status bar shows), then the notification's
        when (style) {
            NotificationStyle.Root -> {
                RootChoiceRows(prefs, access, busy, problem, actions) { choosing = true }
            }

            NotificationStyle.Icon -> {
                SettingsGroup {
                    SwitchRow(
                        stringResource(R.string.st_statusbar_number_icon),
                        prefs.numberIcon,
                        actions.setNumberIcon,
                        summary = stringResource(R.string.st_statusbar_number_icon_summary),
                        icon = DsR.drawable.ds_ic_notifications,
                    )
                }
            }

            NotificationStyle.Chip -> {
                if (!liveUpdatesAllowed) {
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
        }
        SettingsGroup(title = stringResource(R.string.st_statusbar_notification)) {
            if (style != NotificationStyle.Chip) {
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
                summary =
                    stringResource(if (prefs.lockScreen) R.string.st_statusbar_lock_screen_on else R.string.st_statusbar_lock_screen_off),
                icon = DsR.drawable.ds_ic_screen_lock_portrait,
            )
        }
        FooterText(stringResource(R.string.st_statusbar_footer))
    }
    if (choosing) PlacementDialog(prefs.position, access.module.active, { choosing = false }, actions.setPosition)
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
