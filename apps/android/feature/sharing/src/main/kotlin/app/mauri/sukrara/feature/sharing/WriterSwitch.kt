// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.designsystem.rows.SwitchRow

/**
 * THE *Can add treatments* switch (a follower's writer scope), one row without an icon (the owner: no icon on it;
 * the followers list marks the picture instead) where the wearer makes the invite, allows the request and opens the
 * follower's sheet. Each place words it for itself ([title], [summary]): the invite's *Let them add treatments*, the
 * request's *Let Anna add treatments*, the sheet's *Can add treatments*.
 */
@Composable
fun WriterSwitch(
    checked: Boolean,
    title: String = stringResource(R.string.sh_followers_writer),
    summary: String = stringResource(R.string.sh_followers_writer_summary),
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SwitchRow(
        title = title,
        checked = checked,
        onCheckedChange = onChange,
        summary = summary,
        enabled = enabled,
    )
}
