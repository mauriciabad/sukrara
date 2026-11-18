// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.haptics.rememberHaptics

/**
 * Before an online source reads another person than the one whose glucose this phone holds (LibreLinkUp's person,
 * docs/44 Appendix G finding 6; the Mac's `LibreLinkUpConfirm`): [who] is read only after *Read …'s glucose*, which
 * deletes [held]'s readings and the treatments logged here (two people's glucose never mix). *Cancel* keeps the person
 * read; nothing changes.
 */
@Composable
fun ReadAnotherPersonDialog(
    who: String,
    held: String?,
    onRead: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ds_read_other_title, who)) },
        text = {
            Text(
                if (held.isNullOrBlank()) {
                    stringResource(R.string.ds_read_other_body_unnamed)
                } else {
                    stringResource(R.string.ds_read_other_body, held)
                },
            )
        },
        confirmButton = {
            TextButton({
                haptics.confirm()
                onRead()
            }) { Text(stringResource(R.string.ds_read_other_confirm, who), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.ds_cancel)) } },
    )
}
