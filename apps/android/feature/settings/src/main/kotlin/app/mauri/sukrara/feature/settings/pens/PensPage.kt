// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.pens

import android.nfc.NfcAdapter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.core.model.PenInfo
import app.mauri.sukrara.core.model.PenTaps
import app.mauri.sukrara.core.model.PensPort
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.nfc.AntennaArt
import app.mauri.sukrara.designsystem.nfc.antennaSpot
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.BlockedTapsKind
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.TapsBlockedCard
import app.mauri.sukrara.feature.settings.numberLabeler
import app.mauri.sukrara.settings.NumberSpec
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/** One rule of *Automatic detection*: its setting, number format and row title / summary. */
private data class Rule(
    val setting: Setting<Double>,
    val spec: NumberSpec,
    val title: Int,
)

private val Reading = SchemaSettings.text("pens.reading", "ask", setOf("ask", "on", "off"))
private val Notify = SchemaSettings.bool("pens.notify", default = false)
private val Rules =
    listOf(
        Rule(SchemaSettings.number("pens.primeBelow"), SettingsSpec.PEN_PRIME_BELOW, R.string.st_pens_prime_below),
        Rule(SchemaSettings.number("pens.primeBefore"), SettingsSpec.PEN_PRIME_BEFORE, R.string.st_pens_prime_before),
        Rule(SchemaSettings.number("pens.cluster"), SettingsSpec.PEN_CLUSTER, R.string.st_pens_cluster),
        Rule(SchemaSettings.number("pens.pending"), SettingsSpec.PEN_PENDING, R.string.st_pens_pending),
        Rule(SchemaSettings.number("pens.offer"), SettingsSpec.PEN_OFFER, R.string.st_pens_offer),
        Rule(SchemaSettings.number("pens.match"), SettingsSpec.PEN_MATCH, R.string.st_pens_match),
        Rule(SchemaSettings.number("pens.matchUnits"), SettingsSpec.PEN_MATCH_UNITS, R.string.st_pens_match_units),
        Rule(SchemaSettings.number("pens.matchShare"), SettingsSpec.PEN_MATCH_SHARE, R.string.st_pens_match_share),
    )

/** Settings › Insulin pens as the page shows it. */
data class PensUi(
    val reading: String,
    val notify: Boolean,
    val rules: List<Double>,
    val pens: List<PenInfo> = emptyList(),
    val taps: PenTaps = PenTaps(blocked = false, nfcOff = false, notifyBlocked = false),
)

class PensViewModel(
    private val settings: SettingsRepository,
    private val port: PensPort?,
) : ViewModel() {
    private val taps = MutableStateFlow(port?.taps() ?: PenTaps(blocked = false, nfcOff = false, notifyBlocked = false))

    /** Switched On while Android blocked the taps: Android's screen was opened (the page says how it went on return). */
    private var askedAllow = false
    private val _allowResult = MutableStateFlow<Boolean?>(null)
    val allowResult: StateFlow<Boolean?> = _allowResult

    val ui: StateFlow<PensUi?> =
        combine(
            settings.flow(Reading),
            settings.flow(Notify),
            combine(Rules.map { settings.flow(it.setting) }) { it.toList() },
            port?.pens ?: flowOf(emptyList()),
            taps,
        ) { r, n, rules, pens, t -> PensUi(r, n, rules, pens, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Android's NFC, tag-intent and notification state changes outside the app: read again on every resume. */
    fun refresh() {
        val now = port?.taps() ?: return
        taps.value = now
        if (askedAllow) {
            askedAllow = false
            _allowResult.value = !now.blocked
        }
    }

    fun allowShown() {
        _allowResult.value = null
    }

    /** The main switch: reading On or Off (*Ask* is only the state before anyone chose). */
    fun setOn(on: Boolean) {
        viewModelScope.launch { settings.set(Reading, if (on) "on" else "off") }
        // switched on while Android blocks the taps: straight to Android's allow screen, once
        if (on && taps.value.blocked && port?.openTagSettings() == true) askedAllow = true
    }

    fun allowTaps() {
        port?.openTagSettings()
    }

    fun openNfc() {
        port?.openNfcSettings()
    }

    fun openNotify() {
        port?.openNotifySettings()
    }

    fun setNotify(on: Boolean) {
        viewModelScope.launch { settings.set(Notify, on) }
    }

    fun setRule(
        index: Int,
        value: Double,
    ) {
        viewModelScope.launch { settings.set(Rules[index].setting, value) }
    }

    fun reset() {
        viewModelScope.launch { Rules.forEach { settings.set(it.setting, it.spec.default) } }
    }
}

/**
 * Settings › Insulin pens (the wearer's phone with NFC): the *Read insulin pens* main switch (off with a note while
 * still *Ask*; off shows only what pens Sukrara reads),
 * *Notification after each read*, *Automatic detection* (its own page, [PenDetectionContent]), your pens, and which
 * pens work.
 */
@Composable
fun PensContent(
    ui: PensUi,
    vm: PensViewModel,
    onBack: () -> Unit,
    openDetection: () -> Unit = {},
    openPen: (String) -> Unit = {},
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val allowResult by vm.allowResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val allowedText = stringResource(R.string.st_pens_taps_allowed)
    val stillText = stringResource(R.string.st_pens_taps_still_blocked)
    LaunchedEffect(allowResult) {
        val r = allowResult ?: return@LaunchedEffect
        Toast.makeText(context, if (r) allowedText else stillText, Toast.LENGTH_SHORT).show()
        vm.allowShown()
    }
    SettingsPage(stringResource(R.string.st_pens_title), onBack) {
        // Android blocks the taps (reading Ask or On): every tap does nothing until allowed
        if (ui.reading != "off" && ui.taps.blocked) TapsBlockedCard(BlockedTapsKind.Pen, vm::allowTaps)
        // where to touch: this phone's NFC antenna (its own spot when Android knows it)
        AntennaIntro()
        // the page's main switch (the PoC's, SPA MainSwitchPreference): On or Off; *Ask* (nobody chose yet: setup's
        // step or the first pen tap's card asks) shows as off with what a tap does then
        MainSwitchRow(
            stringResource(R.string.st_pens_reading),
            ui.reading == "on",
            vm::setOn,
            note = if (ui.reading == "ask") stringResource(R.string.st_pens_reading_ask) else null,
        )
        // Off: the rest of the reading settings go (SPA's main switch), the pens stay reachable (their doses, Forget)
        val on = ui.reading != "off"
        if (on) {
            ReadingGroup(ui, vm::openNfc, vm::setNotify, vm::openNotify, openDetection)
        }
        FooterText(stringResource(R.string.st_pens_reading_footer))
        // Juggluco reads any pen tapped while it's open on screen (its reader mode: no setting of its own or Android's
        // turns that off, and Android tells no app which one is in front): a fact to know, not a state to fix
        if (ui.reading == "on" && ui.taps.juggluco) FooterText(stringResource(R.string.st_pens_juggluco))
        // no icons: a list of pens under its title (one pen icon on every row would tell nothing apart)
        if (on || ui.pens.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.st_pens_yours)) {
                if (ui.pens.isEmpty()) {
                    InfoRow(
                        stringResource(R.string.st_pens_none),
                        summary = stringResource(R.string.st_pens_none_summary),
                    )
                }
                for (p in ui.pens) NavigationRow(p.name, { openPen(p.serial) }, summary = penSummary(p))
            }
        }
        FooterText(stringResource(R.string.st_pens_supported))
    }
}

/** The pens page's settings while reading is on (or still *Ask*): NFC off, the read notification, Automatic detection. */
@Composable
private fun ReadingGroup(
    ui: PensUi,
    openNfc: () -> Unit,
    setNotify: (Boolean) -> Unit,
    openNotify: () -> Unit,
    openDetection: () -> Unit,
) {
    val label = numberLabeler()
    SettingsGroup {
        if (ui.taps.nfcOff) {
            NavigationRow(
                stringResource(R.string.st_pens_nfc_off),
                openNfc,
                summary = stringResource(R.string.st_pens_nfc_off_summary),
                icon = DsR.drawable.ds_ic_nfc_off,
            )
        }
        if (ui.notify && ui.taps.notifyBlocked) {
            // on but blocked in Android: the row opens the channel's settings, the switch still turns it off
            SplitSwitchRow(
                stringResource(R.string.st_pens_notify),
                true,
                setNotify,
                openNotify,
                summary = stringResource(R.string.st_pens_notify_blocked),
                icon = DsR.drawable.ds_ic_notifications,
            )
        } else {
            SwitchRow(
                stringResource(R.string.st_pens_notify),
                ui.notify,
                setNotify,
                summary = stringResource(R.string.st_pens_notify_summary),
                icon = DsR.drawable.ds_ic_notifications,
            )
        }
        NavigationRow(
            stringResource(R.string.st_pens_detection),
            openDetection,
            summary =
                stringResource(
                    R.string.st_pens_detection_summary,
                    label(Rules[0].spec, ui.rules[0]),
                    label(Rules[2].spec, ui.rules[2]),
                ),
            icon = DsR.drawable.ds_ic_tune,
        )
    }
}

/** One group of *Automatic detection*: its title, its rules ([Rules] indices) and the footer that says what they do. */
private data class DetectionGroup(
    val title: Int,
    val rules: IntRange,
    val footer: Int,
)

private val DetectionGroups =
    listOf(
        DetectionGroup(R.string.st_pens_priming_group, 0..1, R.string.st_pens_priming_footer),
        DetectionGroup(R.string.st_pens_injections_group, 2..4, R.string.st_pens_injections_footer),
        DetectionGroup(R.string.st_pens_typed_group, 5..7, R.string.st_pens_typed_footer),
    )

/**
 * Settings › Insulin pens › Automatic detection (the PoC's page): the rules in groups (*Priming*, *Injections*, *Doses
 * you type*), each the one number input showing its value, a footer per group saying what its rules do, and *Reset to
 * defaults* (dialog). No icons on the rules: eight numbers of one kind are told apart by their titles and groups.
 */
@Composable
fun PenDetectionContent(
    ui: PensUi,
    vm: PensViewModel,
    onBack: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(-1) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    val label = numberLabeler()
    SettingsPage(stringResource(R.string.st_pens_detection), onBack) {
        SettingsIntro(stringResource(R.string.st_pens_detection_intro))
        for (g in DetectionGroups) {
            SettingsGroup(title = stringResource(g.title)) {
                for (i in g.rules) ValueRow(stringResource(Rules[i].title), label(Rules[i].spec, ui.rules[i]), { editing = i })
            }
            FooterText(stringResource(g.footer))
        }
        SettingsGroup {
            ActionRow(
                stringResource(R.string.st_pens_reset),
                { resetting = true },
                enabled = Rules.indices.any { ui.rules[it] != Rules[it].spec.default },
                icon = DsR.drawable.ds_ic_restart_alt,
            )
        }
    }
    if (editing >= 0) {
        val r = Rules[editing]
        val i = editing
        NumberDialog(stringResource(r.title), NumberInput(r.spec), ui.rules[i], { label(r.spec, it) }, { editing = -1 }, onChoose = {
            vm.setRule(i, it)
        })
    }
    if (resetting) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { resetting = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.st_pens_reset_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.st_pens_reset_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    vm.reset()
                    resetting = false
                }) { androidx.compose.material3.Text(stringResource(R.string.st_pens_reset_confirm)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton({ resetting = false }) {
                    androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** The pens page's intro: the back of this phone with a circle on its NFC antenna, and how to hold the pen there. */
@Composable
private fun AntennaIntro() {
    val context = LocalContext.current
    val spot = remember(context) { antennaSpot(NfcAdapter.getDefaultAdapter(context)) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AntennaArt(spot, stringResource(R.string.st_pens_antenna_desc), Modifier.size(width = 56.dp, height = 96.dp))
        Text(
            stringResource(R.string.st_pens_antenna),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
