// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.sheet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * THE app's bottom sheet (#207 round 2: one surface for everything opened from a list or a card, never a dialog in one
 * place and a sheet in another): an M3 modal bottom sheet, fully expanded, its content in a column with the app's
 * sheet padding (24 dp sides and bottom) and gap (12 dp); its last row is [SheetActions]. [leaving] = it slides away.
 * Dialogs stay for confirmations and pickers (M3: a dialog interrupts for a decision, a sheet shows an item's details
 * and actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SukraraSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    leaving: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // the owner closes it (saved, deleted): it slides away like a swipe down would
    LaunchedEffect(leaving) { if (leaving) state.hide() }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/**
 * A sheet's action row: a destructive action at the start ([start], e.g. [SheetButton] *Delete*), the others at the
 * end with the confirming one last (M3 dialogs and sheets).
 */
@Composable
fun SheetActions(
    end: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    start: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        start?.invoke(this)
        Spacer(Modifier.weight(1f))
        end()
    }
}

/**
 * A sheet's button: always an icon and a word (the owner: every option gets an icon). [primary] = the filled
 * confirming button; [destructive] = a text button in the error colour (*Delete*); else a text button.
 */
@Composable
fun SheetButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val label: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1)
    }
    if (primary) {
        Button(onClick, modifier, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = label)
    } else {
        TextButton(
            onClick,
            modifier,
            enabled = enabled,
            colors =
                if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            content = label,
        )
    }
}
