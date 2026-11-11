// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.lock

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.LockAppearance
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.numberLabeler
import app.mauri.sukrara.feature.settings.statusbar.AccessProblem
import app.mauri.sukrara.feature.settings.statusbar.AccessRunner
import app.mauri.sukrara.feature.settings.statusbar.AccessStepBanner
import app.mauri.sukrara.feature.settings.statusbar.lockAccessStep
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/** Seconds the quick treatment dialog stays over the lock screen before it closes unsaved (`lock.treatmentTime`). */
val LockTreatmentTime = SchemaSettings.number("lock.treatmentTime")

/**
 * The lock-screen widget as the page shows it: on or off, its look, and what Settings knows of the System UI module
 * ([access]); [busy] = a module action runs, [problem] = why the last one failed.
 */
data class LockWidgetUi(
    val on: Boolean,
    val look: LockLook,
    val access: SystemAccessState = SystemAccessState(),
    val busy: Boolean = false,
    val problem: AccessProblem? = null,
)

/** What the widget's rows and its access card do. */
class LockWidgetActions(
    val setOn: (Boolean) -> Unit = {},
    val setBackground: (LockBackground) -> Unit = {},
    val setAppearance: (LockAppearance) -> Unit = {},
    val requestScope: () -> Unit = {},
    val restart: () -> Unit = {},
    val checkRoot: () -> Unit = {},
)

class LockScreenViewModel(
    private val settings: SettingsRepository,
    /** null: this build has no root features, so the page has no lock-screen widget. */
    private val systemAccess: SystemAccessPort?,
) : ViewModel() {
    val seconds: StateFlow<Double?> =
        settings.flow(LockTreatmentTime).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val runner = AccessRunner(viewModelScope)

    val widget: StateFlow<LockWidgetUi?> =
        if (systemAccess == null) {
            MutableStateFlow(null)
        } else {
            combine(
                settings.flow(SurfaceSettings.LockWidget),
                SurfaceSettings.lockLook(settings),
                systemAccess.state,
                runner.busy,
                runner.problem,
                ::LockWidgetUi,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    val actions =
        LockWidgetActions(
            setOn = { on -> viewModelScope.launch { settings.set(SurfaceSettings.LockWidget, on) } },
            setBackground = { b -> viewModelScope.launch { settings.set(SurfaceSettings.LockWidgetBackground, b.key) } },
            setAppearance = { a -> viewModelScope.launch { settings.set(SurfaceSettings.LockWidgetAppearance, a.key) } },
            requestScope = { runner.run { systemAccess?.requestScope() } },
            restart = { runner.run { systemAccess?.restartSystemUi() } },
            checkRoot = {
                runner.run {
                    systemAccess?.checkRoot()
                    null
                }
            },
        )

    fun set(value: Double) {
        viewModelScope.launch { settings.set(LockTreatmentTime, value) }
    }

    /** Back on the page: ask LSPosed again (the person may come back from enabling the module), never `su`. */
    fun refresh() {
        systemAccess?.refresh()
    }
}

@StringRes
fun lockBackgroundLabel(b: LockBackground): Int =
    when (b) {
        LockBackground.NONE -> R.string.st_lock_bg_none
        LockBackground.SHADOW -> R.string.st_lock_bg_shadow
        LockBackground.DIM -> R.string.st_lock_bg_dim
        LockBackground.CARD -> R.string.st_lock_bg_card
        LockBackground.FROSTED -> R.string.st_lock_bg_frosted
    }

@StringRes
private fun lockBackgroundSummary(b: LockBackground): Int =
    when (b) {
        LockBackground.NONE -> R.string.st_lock_bg_none_summary
        LockBackground.SHADOW -> R.string.st_lock_bg_shadow_summary
        LockBackground.DIM -> R.string.st_lock_bg_dim_summary
        LockBackground.CARD -> R.string.st_lock_bg_card_summary
        LockBackground.FROSTED -> R.string.st_lock_bg_frosted_summary
    }

@StringRes
fun lockAppearanceLabel(a: LockAppearance): Int =
    when (a) {
        LockAppearance.AUTO -> R.string.st_lock_look_auto
        LockAppearance.LIGHT -> R.string.st_lock_look_light
        LockAppearance.DARK -> R.string.st_lock_look_dark
    }

@StringRes
private fun lockAppearanceSummary(a: LockAppearance): Int =
    when (a) {
        LockAppearance.AUTO -> R.string.st_lock_look_auto_summary
        LockAppearance.LIGHT -> R.string.st_lock_look_light_summary
        LockAppearance.DARK -> R.string.st_lock_look_dark_summary
    }

/** The widget's switch summary: what it is while it shows (or is off), else what it still needs. */
@StringRes
fun lockWidgetSummary(ui: LockWidgetUi): Int =
    when {
        !ui.on -> R.string.st_lock_widget_off
        ui.access.module.active -> R.string.st_lock_widget_on
        else -> R.string.st_lock_widget_needs_module
    }

private enum class LockDialog { Background, Appearance, Time }

/**
 * Settings › Lock screen: the lock-screen widget (in a build with root features: *Lock screen widget*, *Background*,
 * *Appearance*, the module's missing step as a card above them) and *Add treatment over the lock screen* (*Time to save*:
 * 10 / 20 / 30 / 60 s or a custom 10–120 s).
 */
@Composable
fun LockScreenContent(
    seconds: Double,
    onSet: (Double) -> Unit,
    onBack: () -> Unit,
    widget: LockWidgetUi? = null,
    actions: LockWidgetActions = LockWidgetActions(),
) {
    var dialog by rememberSaveable { mutableStateOf<LockDialog?>(null) }
    val label = numberLabeler()
    val spec = SettingsSpec.LOCK_TREATMENT_TIME
    SettingsPage(stringResource(R.string.st_lock_title), onBack) {
        if (widget != null) {
            AccessStepBanner(
                lockAccessStep(widget.access, widget.on),
                widget.access,
                widget.busy,
                widget.problem,
                actions.checkRoot,
                actions.requestScope,
                actions.restart,
            )
            val off = stringResource(R.string.st_lock_unavailable)
            SettingsGroup(title = stringResource(R.string.st_root_group)) {
                SwitchRow(
                    stringResource(R.string.st_lock_widget),
                    widget.on,
                    actions.setOn,
                    summary = stringResource(lockWidgetSummary(widget)),
                    icon = DsR.drawable.ds_ic_widgets,
                )
                ValueRow(
                    stringResource(R.string.st_lock_background),
                    if (widget.on) stringResource(lockBackgroundLabel(widget.look.background)) else off,
                    { dialog = LockDialog.Background },
                    enabled = widget.on,
                    icon = DsR.drawable.ds_ic_wallpaper,
                )
                ValueRow(
                    stringResource(R.string.st_lock_appearance),
                    if (widget.on) stringResource(lockAppearanceLabel(widget.look.appearance)) else off,
                    { dialog = LockDialog.Appearance },
                    enabled = widget.on,
                    icon = DsR.drawable.ds_ic_palette,
                )
            }
            FooterText(stringResource(R.string.st_lock_widget_footer))
        }
        SettingsGroup(title = stringResource(R.string.st_lock_treatment)) {
            ValueRow(
                stringResource(R.string.st_lock_time),
                label(spec, seconds),
                { dialog = LockDialog.Time },
                icon = DsR.drawable.ds_ic_timer,
            )
        }
        FooterText(stringResource(R.string.st_lock_footer))
    }
    when (dialog) {
        LockDialog.Background -> {
            ChoiceDialog(
                stringResource(R.string.st_lock_background),
                LockBackground.entries.map {
                    Choice(
                        stringResource(lockBackgroundLabel(it)),
                        it,
                        sub = stringResource(lockBackgroundSummary(it)),
                    )
                },
                widget?.look?.background ?: LockBackground.DEFAULT,
                { dialog = null },
                onChoose = actions.setBackground,
            )
        }

        LockDialog.Appearance -> {
            ChoiceDialog(
                stringResource(R.string.st_lock_appearance),
                LockAppearance.entries.map {
                    Choice(
                        stringResource(lockAppearanceLabel(it)),
                        it,
                        sub = stringResource(lockAppearanceSummary(it)),
                    )
                },
                widget?.look?.appearance ?: LockAppearance.DEFAULT,
                { dialog = null },
                onChoose = actions.setAppearance,
            )
        }

        LockDialog.Time -> {
            NumberDialog(
                stringResource(R.string.st_lock_time),
                NumberInput(spec),
                seconds,
                { label(spec, it) },
                { dialog = null },
                onChoose = onSet,
            )
        }

        null -> {
            Unit
        }
    }
}
