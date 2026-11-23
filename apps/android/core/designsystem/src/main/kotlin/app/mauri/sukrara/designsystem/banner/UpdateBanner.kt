// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.banner

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R

/**
 * THE "update an app" notice (docs/48 "When versions differ"): another of Sukrara's apps reads another version of a
 * shared format, and [newer] says it is newer than this phone's Sukrara. [app] is its name inside a sentence ("the
 * Sukrara Aura watch face"). The title names the app to update (*Update the Sukrara Aura watch face*, or *Update
 * Sukrara* when this phone's is the older one), the text what it shows on [device] meanwhile, and *Update* opens that
 * update. One copy wherever it shows (the Garmin page, home's banner): it can't be closed, it ends
 * by itself at the first message of the same version.
 */
@Composable
fun updateBanner(
    app: String,
    device: String,
    newer: Boolean,
    onUpdate: () -> Unit,
): Banner =
    updateBanner(
        id = "$app:$newer",
        title = if (newer) stringResource(R.string.ds_update_sukrara) else stringResource(R.string.ds_update_app, app),
        text =
            if (newer) {
                stringResource(R.string.ds_update_newer_text, app, device)
            } else {
                stringResource(R.string.ds_update_text, device)
            },
        onUpdate = onUpdate,
    )

/**
 * The same notice in a place's own words (the wearer's followers page: *Update Sukrara* · "Until then, Mom can't see
 * your new readings."): [title] names the app to update, [text] what happens until then; one *Update*, no close.
 */
@Composable
fun updateBanner(
    id: String,
    title: String,
    text: String,
    onUpdate: () -> Unit,
): Banner =
    Banner(
        id = "update:$id",
        icon = R.drawable.ds_ic_update,
        title = title,
        text = text,
        action = stringResource(R.string.ds_update),
        onAction = onUpdate,
        onDismiss = null,
        tone = NoticeTone.Warning,
    )
