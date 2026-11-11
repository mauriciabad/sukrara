// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * What Backup shows under its rows about *Save to password manager* (until the next try): the system's sheet open,
 * how it ended (each failure with its way on, right there), or a recovery code made instead.
 */
enum class BackupUi {
    Idle,

    /** The system's sheet is open: the row waits. */
    Asking,
    Saved,

    /** The person closed the sheet: nothing more to say than that. */
    Cancelled,

    /** No password manager takes passwords from apps → *Open settings* (asks again on return) or a recovery code. */
    NoPasswordManager,

    /** The password manager answered with an error → *Try again*, *Open settings* or a recovery code. */
    Failed,

    /** A recovery code was copied: paste it somewhere now. */
    CodeCopied,
}

/**
 * Backup: the sharing identity somewhere besides this phone, so a new phone keeps the followers. Save to the password
 * manager (recommended; the system's sheet asks first; [onSave]), a recovery code for people who keep their own copy,
 * and whether the relays hold the encrypted follower list. When the password manager can't take it, the card under
 * the rows says why and offers the way on in place: Android's password manager settings (coming back asks the
 * password manager again by itself), *Try again*, or a recovery code instead. Leaving needs nothing: an unsaved code
 * is simply not a backup. Last, *Replace this identity* ([onReplace]): restore another one or start fresh.
 */
@Composable
fun BackupScreen(
    state: OwnerShareState.Sharing,
    ui: BackupUi,
    onSave: () -> Unit,
    recoveryCode: suspend (password: String) -> String?,
    onRecoveryKeep: () -> Unit,
    modifier: Modifier = Modifier,
    onReplace: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var showRecovery by remember { mutableStateOf(false) }
    // Android's password manager settings; coming back from them asks the password manager again (the card says so)
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onSave() }
    val openSettings = {
        PasswordManager.settingsIntents(context).any { intent ->
            try {
                settings.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        Unit
    }
    // a haptic for how the save ended, once (not again when the screen is recreated)
    var felt by rememberSaveable { mutableStateOf(ui) }
    LaunchedEffect(ui) {
        if (ui == felt) return@LaunchedEffect
        felt = ui
        when (ui) {
            BackupUi.Saved -> haptics.confirm()
            BackupUi.NoPasswordManager, BackupUi.Failed -> haptics.reject()
            else -> Unit
        }
    }
    val saved = state.passwordManagerSavedAt > 0
    val asking = ui == BackupUi.Asking
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (state.backedUp) R.string.sh_backup_intro_saved else R.string.sh_backup_intro_missing),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SettingsGroup {
            ActionRow(
                title = stringResource(if (saved) R.string.sh_backup_save_again else R.string.sh_backup_save),
                summary =
                    when {
                        asking -> stringResource(R.string.sh_backup_asking)
                        saved -> stringResource(R.string.sh_backup_saved_at, date(state.passwordManagerSavedAt))
                        else -> stringResource(R.string.sh_backup_recommended)
                    },
                enabled = !asking,
                onClick = onSave,
            )
            NavigationRow(
                title = stringResource(R.string.sh_recovery_title),
                summary =
                    if (state.recoveryExportedAt > 0) {
                        stringResource(R.string.sh_recovery_kept_at, date(state.recoveryExportedAt))
                    } else {
                        stringResource(R.string.sh_recovery_summary)
                    },
                onClick = { showRecovery = true },
            )
            InfoRow(
                title = stringResource(R.string.sh_backup_relays),
                summary =
                    if (state.backupPublishedAt > 0) {
                        stringResource(R.string.sh_backup_relays_updated, date(state.backupPublishedAt))
                    } else {
                        stringResource(R.string.sh_backup_relays_not_yet)
                    },
            )
        }
        Reveal(ui != BackupUi.Idle && ui != BackupUi.Asking) {
            Swap(ui, Modifier.padding(horizontal = 16.dp)) { shown ->
                SaveCard(
                    shown,
                    state,
                    onTryAgain = onSave,
                    onOpenSettings = openSettings,
                    onRecoveryCode = { showRecovery = true },
                )
            }
        }
        FooterText(stringResource(R.string.sh_backup_footer))
        SettingsGroup {
            NavigationRow(
                title = stringResource(R.string.sh_replace_title),
                summary = stringResource(R.string.sh_replace_summary),
                onClick = onReplace,
            )
        }
    }
    if (showRecovery) {
        RecoveryDialog(recoveryCode, onKeep = onRecoveryKeep, onClose = { showRecovery = false })
    }
}

/**
 * How the save ended, with its way on as buttons right here. Red only while the identity exists nowhere else; a
 * failed *Save again* says the earlier copy stays. TalkBack hears it when it appears (polite live region).
 */
@Composable
private fun SaveCard(
    ui: BackupUi,
    state: OwnerShareState.Sharing,
    onTryAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecoveryCode: () -> Unit,
) {
    val problem = ui == BackupUi.NoPasswordManager || ui == BackupUi.Failed
    val red = problem && !state.backedUp
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = if (red) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (red) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            ),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ui) {
                BackupUi.NoPasswordManager -> {
                    CardTitle(stringResource(R.string.sh_backup_none_title))
                    Text(stringResource(R.string.sh_backup_none_text), style = MaterialTheme.typography.bodyMedium)
                    CardButtons(
                        primary = stringResource(R.string.sh_backup_open_settings) to onOpenSettings,
                        secondary = listOf(stringResource(R.string.sh_backup_use_code) to onRecoveryCode),
                    )
                }

                BackupUi.Failed -> {
                    CardTitle(stringResource(R.string.sh_backup_failed_title))
                    Text(
                        if (state.passwordManagerSavedAt > 0) {
                            stringResource(R.string.sh_backup_failed_again_text, date(state.passwordManagerSavedAt))
                        } else {
                            stringResource(R.string.sh_backup_failed_text)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CardButtons(
                        primary = stringResource(R.string.sh_backup_try_again) to onTryAgain,
                        secondary =
                            listOf(
                                stringResource(R.string.sh_backup_open_settings) to onOpenSettings,
                                stringResource(R.string.sh_backup_use_code) to onRecoveryCode,
                            ),
                    )
                }

                else -> {
                    Text(
                        stringResource(
                            when (ui) {
                                BackupUi.Cancelled -> R.string.sh_backup_cancelled
                                BackupUi.CodeCopied -> R.string.sh_backup_code_copied
                                else -> R.string.sh_backup_saved
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Full width so the largest font wraps; at least 48 dp tall. */
@Composable
private fun CardButtons(
    primary: Pair<String, () -> Unit>,
    secondary: List<Pair<String, () -> Unit>>,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(primary.first) }
        for ((label, action) in secondary) {
            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        }
    }
}

/**
 * A recovery code: the identity encrypted with a password of at least [MIN_PASSWORD] characters (made in 1–3 s), then
 * shown once with Copy & close. The dialog's window is secure (no screenshots, hidden in Recents), the copy is
 * marked sensitive, and neither the password nor the code outlives the dialog. Closing during the work drops it.
 */
@Composable
fun RecoveryDialog(
    recoveryCode: suspend (String) -> String?,
    onKeep: () -> Unit,
    onClose: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()
    val label = stringResource(R.string.sh_recovery_title)
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sh_recovery_text))
                val shown = code
                if (shown == null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.sh_recovery_password)) },
                        supportingText = { Text(stringResource(R.string.sh_recovery_password_rule, MIN_PASSWORD)) },
                        singleLine = true,
                        enabled = !working,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (working) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text(stringResource(R.string.sh_recovery_working))
                        }
                    }
                    if (failed) Text(stringResource(R.string.sh_recovery_failed), color = MaterialTheme.colorScheme.error)
                } else {
                    // not selectable: Copy and close is the one copy, marked sensitive
                    Text(shown, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.sh_recovery_keep), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val shown = code
            if (shown == null) {
                Button(
                    enabled = password.length >= MIN_PASSWORD && !working,
                    onClick = {
                        working = true
                        failed = false
                        scope.launch {
                            code =
                                try {
                                    recoveryCode(password)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                            failed = code == null
                            working = false
                            password = ""
                        }
                    },
                ) { Text(stringResource(R.string.sh_recovery_create)) }
            } else {
                Button(onClick = {
                    val clip =
                        ClipData.newPlainText(label, shown).apply {
                            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                        }
                    scope.launch {
                        clipboard.setClipEntry(clip.toClipEntry())
                        haptics.confirm()
                        onKeep()
                        onClose()
                    }
                }) { Text(stringResource(R.string.sh_recovery_copy_close)) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.sh_followers_close)) } },
    )
}

const val MIN_PASSWORD = 12

private fun date(epochS: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochS * 1000))
