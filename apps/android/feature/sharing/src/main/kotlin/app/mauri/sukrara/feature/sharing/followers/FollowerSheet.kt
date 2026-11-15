// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.followers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.avatar.AvatarImage
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.sheet.SheetActions
import app.mauri.sukrara.designsystem.sheet.SheetButton
import app.mauri.sukrara.designsystem.sheet.SukraraSheet
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.WriterSwitch
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.owner.FollowerRecord
import java.text.DateFormat
import java.util.Date
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * One follower, in THE app's sheet (#207: an item's details and actions open in a sheet, never a dialog here and a
 * sheet there): picture and name, since when, the nearby device's state, *Can add treatments* (a refused change snaps
 * back and says why), then *Remove* (confirmed: it can't be undone, the follower needs a new invite) and *Done*.
 */
@Composable
fun FollowerSheet(
    follower: FollowerRecord,
    /** A nearby device: true = *Connected*, false = paired but *Disconnected*, null = not one (no line). */
    nearby: Boolean?,
    writer: Boolean,
    refused: Boolean,
    onWriter: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val name = Profile.shownName(follower.name).ifBlank { stringResource(R.string.sh_approve_unnamed) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.sh_followers_remove_title, name)) },
            text = { Text(stringResource(R.string.sh_followers_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    onRemove()
                    onClose()
                }) { Text(stringResource(R.string.sh_followers_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.sh_setup_cancel)) } },
        )
        return
    }
    SukraraSheet(onClose, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FollowerPicture(follower.avatarJson, name, writer, 56.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.sh_followers_since, followerDate(follower.since)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                nearby?.let {
                    Text(
                        stringResource(if (it) R.string.sh_nearby_device_connected else R.string.sh_nearby_device_disconnected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WriterSwitch(writer, onChange = onWriter)
        if (refused) {
            Text(
                stringResource(R.string.sh_followers_writer_refused),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SheetActions(
            start = {
                SheetButton(stringResource(R.string.sh_followers_remove), DsR.drawable.ds_ic_person_remove, {
                    haptics.reject()
                    confirming = true
                }, destructive = true)
            },
            end = { SheetButton(stringResource(R.string.sh_followers_done), DsR.drawable.ds_ic_check, onClose, primary = true) },
        )
    }
}

/**
 * A follower's picture; one who can add treatments carries the *Can add treatments* icon at its corner (the list shows
 * the exception only, as the treatment list's pen and cloud marks do; the words are in the row's TalkBack text).
 */
@Composable
internal fun FollowerPicture(
    avatarJson: String?,
    name: String,
    writer: Boolean,
    size: Dp = 40.dp,
) {
    Box(Modifier.size(size)) {
        AvatarImage(avatarJson, name, size = size)
        if (writer) {
            val badge = size * BADGE
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(badge * 0.2f, badge * 0.2f)
                    .size(badge)
                    // a ring in the page's colour keeps the mark apart from the picture
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(badge * 0.18f),
            ) {
                Icon(
                    painterResource(DsR.drawable.ds_ic_edit_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private const val BADGE = 0.5f

internal fun followerDate(epochS: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochS * 1000))
