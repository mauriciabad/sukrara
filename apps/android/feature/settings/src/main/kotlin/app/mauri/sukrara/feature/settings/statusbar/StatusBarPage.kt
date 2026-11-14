// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.storage.SettingSpec
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.errors.UserErrors
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Settings › Status bar: the glucose notification (the chip or the graph), its number icon and colour; with root, the
 * value itself.
 */
data class StatusBarPrefs(
    val chip: Boolean,
    val numberIcon: Boolean,
    /** The glucose notification's value on the lock screen (#94). */
    val lockScreen: Boolean = true,
    /** The graph's card in the range colour. */
    val colorize: Boolean = false,
)

class StatusBarViewModel(
    private val settings: SettingsRepository,
    /** null: this build has no root features (Google Play), so the page has no *With root* section. */
    private val systemAccess: SystemAccessPort?,
) : ViewModel() {
    val root: StateFlow<RootSettings?> =
        if (systemAccess == null) {
            MutableStateFlow(null)
        } else {
            combine(
                settings.flow(SurfaceSettings.RootValue),
                settings.flow(SurfaceSettings.RootPosition),
                settings.flow(SurfaceSettings.RootColor),
                settings.flow(SurfaceSettings.HideNotificationIcon),
                ::RootSettings,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    val access: StateFlow<SystemAccessState> = systemAccess?.state ?: MutableStateFlow(SystemAccessState())

    private val runner = AccessRunner(viewModelScope)

    /** A root / LSPosed action is running; [problem] = why the last one failed (shown in the banner). */
    val busy: StateFlow<Boolean> = runner.busy
    val problem: StateFlow<AccessProblem?> = runner.problem

    val actions =
        RootActions(
            setOn = { on ->
                set(SurfaceSettings.RootValue, on)
                // turning it on is the moment to ask for root (Magisk's prompt; KernelSU / APatch answer at once)
                if (on && systemAccess?.state?.value?.granted == false) {
                    viewModelScope.launch { runCatching { systemAccess.checkRoot() } }
                }
            },
            setPosition = { set(SurfaceSettings.RootPosition, it) },
            setColor = { set(SurfaceSettings.RootColor, it) },
            setHideIcon = { set(SurfaceSettings.HideNotificationIcon, it) },
            checkRoot = {
                runner.run {
                    systemAccess?.checkRoot()
                    null
                }
            },
            requestScope = { runner.run { systemAccess?.requestScope() } },
            restart = { runner.run { systemAccess?.restartSystemUi() } },
        )

    /**
     * Back on the page: look for signs of root and ask LSPosed again (no `su`); ask for root again only while the value
     * is on and root isn't granted yet: the person may come back from allowing Sukrara in their root manager. Someone
     * who never turned it on is never asked; granted root isn't asked again (Magisk would toast each time).
     */
    fun refresh() {
        val access = systemAccess ?: return
        access.refresh()
        viewModelScope.launch {
            if (!access.state.value.granted && settings.flow(SurfaceSettings.RootValue).first()) runCatching { access.checkRoot() }
        }
    }

    private fun <T> set(
        spec: SettingSpec<T>,
        value: T,
    ) {
        viewModelScope.launch { settings.set(spec, value) }
    }

    val prefs: StateFlow<StatusBarPrefs?> =
        combine(
            settings.flow(SurfaceSettings.StatusChip),
            settings.flow(SurfaceSettings.NumberIcon),
            settings.flow(SurfaceSettings.LockScreen),
            settings.flow(SurfaceSettings.Colorize),
            ::StatusBarPrefs,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setColorize(on: Boolean) {
        viewModelScope.launch { settings.set(SurfaceSettings.Colorize, on) }
    }

    fun setLockScreen(on: Boolean) {
        viewModelScope.launch { settings.set(SurfaceSettings.LockScreen, on) }
    }

    fun setChip(on: Boolean) {
        viewModelScope.launch { settings.set(SurfaceSettings.StatusChip, on) }
    }

    fun setNumberIcon(on: Boolean) {
        viewModelScope.launch { settings.set(SurfaceSettings.NumberIcon, on) }
    }
}

/**
 * Runs one root / LSPosed action at a time (Check again, Add System UI, Restart System UI): [busy] while it runs,
 * [problem] = why the last one failed (the access card shows it), cleared when the next one starts.
 */
class AccessRunner(
    private val scope: CoroutineScope,
) {
    val busy = MutableStateFlow(false)
    val problem = MutableStateFlow<AccessProblem?>(null)

    /** [action] answers null when it worked, else what LSPosed or `su` said. */
    fun run(action: suspend () -> String?) {
        if (busy.value) return
        busy.value = true
        problem.value = null
        scope.launch {
            problem.value =
                runCatching { action()?.let(AccessProblem::Said) }.getOrElse {
                    Log.w("Sukrara", "root action: ${it.javaClass.simpleName}")
                    AccessProblem.Failed(UserErrors.of(it))
                }
            busy.value = false
        }
    }
}

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
 * The page: *Glucose notification*, the chip or the graph side by side with a picture of each (they exclude each
 * other: a Live Update can't hold a graph; the graph whatever the choice while [rootValue] is in the bar), *Allow Live
 * Updates for Sukrara* while the chip is chosen and Android refuses it (the way to fix it, not a dead choice),
 * *Number icon* (only meaningful with the graph: its summary says so), *Card in the range colour* (the graph's card),
 * *Glucose on the lock screen* (#94: on by default; its summary says what the lock screen shows). The footer explains
 * what each one changes.
 */
@Composable
fun StatusBarContent(
    prefs: StatusBarPrefs,
    liveUpdatesAllowed: Boolean,
    onChip: (Boolean) -> Unit,
    onNumberIcon: (Boolean) -> Unit,
    onAllowLiveUpdates: () -> Unit,
    onBack: () -> Unit,
    onLockScreen: (Boolean) -> Unit = {},
    rootValue: Boolean = false,
    onColorize: (Boolean) -> Unit = {},
    rootSection: @Composable () -> Unit = {},
) {
    SettingsPage(stringResource(R.string.st_statusbar_title), onBack) {
        NotificationStylePicker(prefs.chip, forcedCard = rootValue, onChoose = onChip)
        SettingsGroup {
            if (prefs.chip && !rootValue && !liveUpdatesAllowed) {
                NavigationRow(
                    stringResource(R.string.st_statusbar_allow_live_updates),
                    onAllowLiveUpdates,
                    summary = stringResource(R.string.st_statusbar_allow_live_updates_summary),
                    icon = DsR.drawable.ds_ic_tune,
                )
            }
            val chipShows = prefs.chip && liveUpdatesAllowed && !rootValue
            SwitchRow(
                stringResource(R.string.st_statusbar_number_icon),
                prefs.numberIcon,
                onNumberIcon,
                summary =
                    stringResource(
                        if (chipShows) R.string.st_statusbar_number_icon_chip else R.string.st_statusbar_number_icon_summary,
                    ),
                enabled = !chipShows,
                icon = DsR.drawable.ds_ic_notifications,
            )
            SwitchRow(
                stringResource(R.string.st_statusbar_colorize),
                prefs.colorize,
                onColorize,
                summary = stringResource(if (prefs.colorize) R.string.st_statusbar_colorize_on else R.string.st_statusbar_colorize_off),
                enabled = !chipShows,
                icon = DsR.drawable.ds_ic_palette,
            )
            SwitchRow(
                stringResource(R.string.st_statusbar_lock_screen),
                prefs.lockScreen,
                onLockScreen,
                summary =
                    stringResource(if (prefs.lockScreen) R.string.st_statusbar_lock_screen_on else R.string.st_statusbar_lock_screen_off),
                icon = DsR.drawable.ds_ic_screen_lock_portrait,
            )
        }
        FooterText(stringResource(R.string.st_statusbar_footer))
        rootSection()
    }
}
