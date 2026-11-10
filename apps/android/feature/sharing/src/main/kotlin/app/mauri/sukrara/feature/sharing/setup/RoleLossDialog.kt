// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.RoleLoss
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The role step's confirmation when the new role deletes this phone's earlier history (#125): what goes, in plain
 * words (*1,204 glucose readings and 12 treatments, from 3 Oct 2026 to 10 Nov 2026*), *Export CSV first* (the
 * export page on everything; the dialog is still here on the way back), *Delete and continue* and *Cancel* (the role
 * stays as it was, nothing is deleted). Back and a tap outside are Cancel.
 */
@Composable
internal fun RoleLossDialog(
    roleName: String,
    loss: RoleLoss,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sh_setup_role_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.sh_setup_role_delete_text, roleName, lossWhat(loss), lossWhen(loss, zone)))
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sh_setup_role_delete_export))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    onDelete()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.sh_setup_role_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.sh_setup_cancel)) } },
    )
}

/** *1,204 glucose readings and 12 treatments* (either alone when the other is none). */
@Composable
private fun lossWhat(loss: RoleLoss): String {
    val numbers = NumberFormat.getIntegerInstance(locale())
    val readings =
        loss.readings.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.sh_setup_role_delete_readings, it, numbers.format(it))
        }
    val treatments =
        loss.treatments.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.sh_setup_role_delete_treatments, it, numbers.format(it))
        }
    return if (readings != null && treatments != null) {
        stringResource(R.string.sh_setup_role_delete_and, readings, treatments)
    } else {
        readings ?: treatments.orEmpty()
    }
}

/** *from 3 Oct 2026 to 10 Nov 2026*, or *from 10 Nov 2026* when it is all one day. */
@Composable
private fun lossWhen(
    loss: RoleLoss,
    zone: ZoneId,
): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale())
    val first = loss.from.atZone(zone).toLocalDate()
    val last = loss.to.atZone(zone).toLocalDate()
    return if (first == last) {
        stringResource(R.string.sh_setup_role_delete_day, format.format(first))
    } else {
        stringResource(R.string.sh_setup_role_delete_range, format.format(first), format.format(last))
    }
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]
