// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.localserver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.ui.SecretClipboard
import app.mauri.sukrara.integrations.ui.ago
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** The secrets as the page shows them: hidden (default), shown, or unreadable. */
sealed interface SecretView {
    data object Hidden : SecretView

    /** The phone apps' [secret] and the home-network listener's own [lanSecret] (null: none made yet). */
    data class Shown(
        val secret: String,
        val lanSecret: String? = null,
    ) : SecretView

    data object Unreadable : SecretView
}

data class NsApiUi(
    val on: Boolean,
    val state: NsApiState,
    val secret: SecretView = SecretView.Hidden,
    /** The home-network option's switch and where it is. */
    val lanOn: Boolean = false,
    val lan: LanState = LanState.Off,
    /** The last *turn on* / *Allow on this Wi-Fi* was refused: the phone isn't on a Wi-Fi that qualifies. */
    val lanRefused: Boolean = false,
    /** The home-network listener has its own secret (made the first time the option was turned on). */
    val lanSecretMade: Boolean = false,
)

class NsApiActions(
    val setOn: (Boolean) -> Unit = {},
    val showSecret: () -> Unit = {},
    val hideSecret: () -> Unit = {},
    val renewSecret: () -> Unit = {},
    /** *New Wi-Fi secret* (confirmed): only the devices on the Wi-Fi need the new one. */
    val renewLanSecret: () -> Unit = {},
    /** On only after the page's confirmation (and Android 17's permission request). */
    val setLan: (Boolean) -> Unit = {},
    val allowHere: () -> Unit = {},
    val openAndroidSettings: () -> Unit = {},
)

class NightscoutApiViewModel(
    private val server: NightscoutApiServer,
    val localNetworkAllowed: () -> Boolean,
) : ViewModel() {
    private val secret = MutableStateFlow<SecretView>(SecretView.Hidden)
    private val refused = MutableStateFlow(false)
    private val lanSecretMade = MutableStateFlow(false)
    val ui: StateFlow<NsApiUi?> =
        combine(
            combine(server.on, server.state, secret) { on, st, s -> Triple(on, st, s) },
            server.lanOn,
            server.lan,
            refused,
            lanSecretMade,
        ) { (on, st, s), lo, l, r, made -> NsApiUi(on, st, s, lo, l, r, made || lo) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // an unreadable one exists too: its *New Wi-Fi secret* is the way out
        viewModelScope.launch { lanSecretMade.value = runCatching { server.lanSecret() != null }.getOrDefault(true) }
    }

    fun setOn(on: Boolean) {
        viewModelScope.launch { server.setOn(on) }
    }

    fun setLan(on: Boolean) {
        viewModelScope.launch {
            refused.value = guarded { server.setLan(on) } != null
            if (on) lanSecretMade.value = runCatching { server.lanSecret() != null }.getOrDefault(true)
            // a secret already on screen gets the Wi-Fi one next to it
            if (secret.value is SecretView.Shown) showSecret()
        }
    }

    fun allowHere() {
        viewModelScope.launch { refused.value = guarded { server.allowHere() } != null }
    }

    /** A store failure reads as "not turned on" (shown), never a crash. */
    private suspend fun guarded(block: suspend () -> LanState?): LanState? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LanState.NoWifi
        }

    fun recheck() = server.recheckLan()

    fun showSecret() {
        viewModelScope.launch {
            secret.value =
                try {
                    server.secret()?.let { SecretView.Shown(it, lanSecretOrNull()) } ?: SecretView.Hidden
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecretView.Unreadable
                }
        }
    }

    /** The Wi-Fi secret next to the phone's (one that can't be read is left out: its own row says so). */
    private suspend fun lanSecretOrNull(): String? =
        try {
            server.lanSecret()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    fun hideSecret() {
        secret.value = SecretView.Hidden
    }

    fun renewSecret() {
        viewModelScope.launch {
            secret.value =
                try {
                    SecretView.Shown(server.renewSecret(), lanSecretOrNull())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecretView.Unreadable
                }
        }
    }

    /** Shows the new Wi-Fi secret (with the phone's, unchanged) so it can be entered in the devices at once. */
    fun renewLanSecret() {
        viewModelScope.launch {
            secret.value =
                try {
                    val lan = server.renewLanSecret()
                    lanSecretMade.value = true
                    server.secret()?.let { SecretView.Shown(it, lan) } ?: SecretView.Hidden
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecretView.Unreadable
                }
        }
    }

    override fun onCleared() {
        secret.value = SecretView.Hidden
    }
}

@Composable
fun NightscoutApiPage(
    viewModel: NightscoutApiViewModel,
    now: () -> Instant,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Android 17: incoming connections from the home network need local network access (Nearby devices)
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // on either way: when Android said no, the page shows it and the way to allow it (or turn this off)
            viewModel.setLan(true)
        }
    LifecycleResumeEffect(viewModel) {
        viewModel.recheck()
        onPauseOrDispose {}
    }
    ui?.let {
        NightscoutApiContent(
            it,
            now(),
            NsApiActions(
                viewModel::setOn,
                viewModel::showSecret,
                viewModel::hideSecret,
                viewModel::renewSecret,
                viewModel::renewLanSecret,
                setLan = { on ->
                    // the permission exists from Android 17; older Android grants local network access already
                    if (on &&
                        Build.VERSION.SDK_INT >= LanWatcher.LOCAL_NETWORK_SDK &&
                        !viewModel.localNetworkAllowed()
                    ) {
                        ask.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    } else {
                        viewModel.setLan(on)
                    }
                },
                allowHere = viewModel::allowHere,
                openAndroidSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            ),
            modifier,
        )
    }
}

/**
 * Settings › Share with apps › *Nightscout API on this phone*: the switch, where it listens and when it was last read,
 * the API secret (hidden until asked; the screen can't be captured while it shows; a tap copies it, and it leaves the
 * clipboard a minute later), *New secret*, and how apps connect.
 */
@Composable
fun NightscoutApiContent(
    ui: NsApiUi,
    now: Instant,
    actions: NsApiActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(stringResource(R.string.in_nsapi_intro))
    SettingsGroup {
        SwitchRow(
            stringResource(R.string.in_nsapi_switch),
            ui.on,
            actions.setOn,
            summary = nsApiStatusText(ui.state, now),
        )
    }
    if (ui.on) LanGroup(ui, now, actions)
    if (ui.on || ui.secret !is SecretView.Hidden) {
        SettingsGroup(title = stringResource(R.string.in_nsapi_secret_group)) {
            when (val s = ui.secret) {
                SecretView.Hidden -> {
                    ActionRow(
                        stringResource(R.string.in_nsapi_show_secret),
                        actions.showSecret,
                        summary = stringResource(R.string.in_nsapi_show_secret_summary),
                    )
                }

                is SecretView.Shown -> {
                    SecureWindow()
                    CopyRow(s.secret, stringResource(R.string.in_nsapi_secret_label), stringResource(R.string.in_nsapi_copy))
                    InfoRow(
                        stringResource(R.string.in_nsapi_xdrip_url),
                        summary = "http://${s.secret}@127.0.0.1:${NightscoutApi.PORT}",
                    )
                    InfoRow(
                        stringResource(R.string.in_nsapi_token_url),
                        summary = "http://127.0.0.1:${NightscoutApi.PORT}/?token=${s.secret}",
                    )
                    if (s.lanSecret != null && ui.lanOn) {
                        CopyRow(s.lanSecret, stringResource(R.string.in_nsapi_lan_secret_label), stringResource(R.string.in_nsapi_lan_copy))
                        (ui.lan as? LanState.Listening)?.let { l ->
                            InfoRow(
                                stringResource(R.string.in_nsapi_lan_url),
                                summary = "http://${s.lanSecret}@${l.address}:${NightscoutApi.PORT}",
                            )
                        }
                    }
                    ActionRow(stringResource(R.string.in_nsapi_hide_secret), actions.hideSecret)
                }

                SecretView.Unreadable -> {
                    InfoRow(stringResource(R.string.in_nsapi_secret_unreadable))
                }
            }
            DangerRow(
                stringResource(R.string.in_nsapi_new_secret),
                stringResource(R.string.in_nsapi_new_secret_title),
                stringResource(R.string.in_nsapi_new_secret_text),
                stringResource(R.string.in_nsapi_new_secret_action),
                actions.renewSecret,
                summary = stringResource(R.string.in_nsapi_new_secret_summary),
            )
        }
    }
    FooterText(stringResource(R.string.in_nsapi_footer))
}

/** The status line on the switch and on Share with apps. */
@Composable
fun nsApiStatusText(
    state: NsApiState,
    now: Instant,
): String =
    when (state) {
        NsApiState.Off -> {
            stringResource(R.string.in_nsapi_off)
        }

        NsApiState.PortBusy -> {
            stringResource(R.string.in_nsapi_busy)
        }

        NsApiState.SecretUnreadable -> {
            stringResource(R.string.in_nsapi_unreadable)
        }

        is NsApiState.Listening -> {
            listOfNotNull(
                stringResource(R.string.in_nsapi_on),
                state.lan?.let { stringResource(R.string.in_nsapi_also_lan, it) },
                state.lastAsked?.let { stringResource(R.string.in_apps_xdrip_last_asked, ago(it, now)) }
                    ?: stringResource(R.string.in_apps_xdrip_not_asked),
            ).joinToString(" · ")
        }
    }

/**
 * A secret as one row: a tap copies it (marked sensitive; [SecretClipboard] takes it off the clipboard a minute later),
 * a haptic confirms and the summary says it was copied (Android shows its own copy notice too). [label] names it in
 * the clipboard, [hint] says what it is for.
 */
@Composable
private fun CopyRow(
    secret: String,
    label: String,
    hint: String,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by rememberSaveable(secret) { mutableStateOf(false) }
    // the summary says "copied" only while the secret is still meant to be on the clipboard
    LaunchedEffect(copied) {
        if (copied) {
            delay(SecretClipboard.CLEAR_AFTER_MS)
            copied = false
        }
    }
    ActionRow(
        secret,
        {
            SecretClipboard.copy(context, label, secret)
            haptics.confirm()
            copied = true
        },
        summary = if (copied) stringResource(R.string.in_nsapi_copied) else hint,
    )
}

/**
 * *On your home Wi-Fi*: the switch (turning it on asks first, with the warning), where it listens or why it doesn't
 * (and which device read last), each blocked state's way on: *Allow on this Wi-Fi* for another network, Android
 * settings when local network access was refused, and *New Wi-Fi secret* (its own secret, which travels unencrypted:
 * replacing it never touches the phone apps'). The switch is always the way off (never Android settings: Nearby
 * devices also runs the sensor).
 */
@Composable
private fun LanGroup(
    ui: NsApiUi,
    now: Instant,
    actions: NsApiActions,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = stringResource(R.string.in_nsapi_lan_group)) {
        SwitchRow(
            stringResource(R.string.in_nsapi_lan_switch),
            ui.lanOn,
            { on -> if (on) confirm = true else actions.setLan(false) },
            summary =
                when {
                    ui.lanOn -> lanStatusText(ui.lan, now)
                    ui.lanRefused -> stringResource(R.string.in_nsapi_lan_refused)
                    else -> stringResource(R.string.in_nsapi_lan_off)
                },
        )
        if (ui.lanOn) {
            when (ui.lan) {
                LanState.OtherWifi -> {
                    NavigationRow(
                        stringResource(R.string.in_nsapi_lan_allow_here),
                        actions.allowHere,
                        summary =
                            stringResource(
                                if (ui.lanRefused) R.string.in_nsapi_lan_refused else R.string.in_nsapi_lan_allow_here_summary,
                            ),
                    )
                }

                LanState.NoPermission -> {
                    NavigationRow(
                        stringResource(R.string.in_nsapi_lan_settings),
                        actions.openAndroidSettings,
                        summary = stringResource(R.string.in_nsapi_lan_settings_summary),
                    )
                }

                else -> {}
            }
        }
        if (ui.lanSecretMade) {
            val why =
                if (ui.lan ==
                    LanState.SecretUnreadable
                ) {
                    R.string.in_nsapi_lan_unreadable
                } else {
                    R.string.in_nsapi_lan_new_secret_summary
                }
            DangerRow(
                stringResource(R.string.in_nsapi_lan_new_secret),
                stringResource(R.string.in_nsapi_lan_new_secret_title),
                stringResource(R.string.in_nsapi_lan_new_secret_text),
                stringResource(R.string.in_nsapi_new_secret_action),
                actions.renewLanSecret,
                summary = stringResource(why),
            )
        }
        InfoRow(stringResource(R.string.in_nsapi_lan_warning_title), summary = stringResource(R.string.in_nsapi_lan_warning))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.in_nsapi_lan_confirm_title)) },
            text = { Text(stringResource(R.string.in_nsapi_lan_warning)) },
            confirmButton = {
                TextButton({
                    confirm = false
                    actions.setLan(true)
                }) { Text(stringResource(R.string.in_nsapi_lan_confirm_action)) }
            },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun lanStatusText(
    lan: LanState,
    now: Instant,
): String =
    when (lan) {
        LanState.Off -> {
            stringResource(R.string.in_nsapi_lan_starting)
        }

        LanState.NoWifi -> {
            stringResource(R.string.in_nsapi_lan_no_wifi)
        }

        LanState.NotPrivate -> {
            stringResource(R.string.in_nsapi_lan_not_private)
        }

        LanState.OpenWifi -> {
            stringResource(R.string.in_nsapi_lan_open)
        }

        LanState.OtherWifi -> {
            stringResource(R.string.in_nsapi_lan_other)
        }

        LanState.NoPermission -> {
            stringResource(R.string.in_nsapi_lan_no_permission)
        }

        LanState.PortBusy -> {
            stringResource(R.string.in_nsapi_busy)
        }

        LanState.ApiStopped -> {
            stringResource(R.string.in_nsapi_lan_api_stopped)
        }

        LanState.SecretUnreadable -> {
            stringResource(R.string.in_nsapi_lan_unreadable)
        }

        is LanState.Listening -> {
            val last =
                if (lan.lastCaller != null && lan.lastAsked != null) {
                    stringResource(R.string.in_nsapi_lan_last_caller, lan.lastCaller, ago(lan.lastAsked, now))
                } else {
                    stringResource(R.string.in_nsapi_lan_not_read)
                }
            stringResource(R.string.in_nsapi_lan_on, lan.address) + " · " + last
        }
    }

/** No screenshots or recents thumbnail while the secret is on screen. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        // only clear what this added: another screen may have set it too
        val added = activity.window.attributes.flags and flag == 0
        if (added) activity.window.addFlags(flag)
        onDispose { if (added) activity.window.clearFlags(flag) }
    }
}

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
