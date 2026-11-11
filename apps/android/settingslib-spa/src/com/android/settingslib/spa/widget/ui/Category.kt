/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.spa.widget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.tooling.preview.Preview
import com.android.settingslib.spa.framework.compose.thenIf
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel

/** A category title that is placed before a group of similar items. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryTitle(title: String) {
    Text(
        text = title,
        modifier =
            Modifier.padding(
                start =
                    if (isSpaExpressiveEnabled) SettingsSpace.extraSmall4
                    else SettingsDimension.itemPaddingStart,
                top = SettingsSpace.small3,
                end =
                    if (isSpaExpressiveEnabled) SettingsSpace.extraSmall4
                    else SettingsDimension.itemPaddingEnd,
                bottom = SettingsSpace.extraSmall4,
            ),
        color = MaterialTheme.colorScheme.primary,
        style =
            if (isSpaExpressiveEnabled) MaterialTheme.typography.labelLargeEmphasized
            else MaterialTheme.typography.labelMedium,
    )
}

/**
 * A container that is used to group similar items. A [Category] displays a [CategoryTitle] and
 * visually separates groups of items.
 *
 * @param content The content of the category.
 */
@Composable
fun Category(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var displayTitle by remember { mutableStateOf(false) }
    // Sukrara: the margins decided in the same layout pass (AOSP adds them once a drawn frame had rows, so a page's
    // first frame showed its groups full width); an empty category still takes no space
    Column(modifier = Modifier.thenIf(isSpaExpressiveEnabled) { Modifier.marginsWhenNotEmpty() }) {
        if (title != null && displayTitle) CategoryTitle(title = title)
        Column(
            modifier =
                modifier
                    .onGloballyPositioned { coordinates ->
                        displayTitle = coordinates.size.height > 0
                    }
                    .thenIf(isSpaExpressiveEnabled) {
                        Modifier.fillMaxWidth().clip(SettingsShape.CornerLarge2)
                    },
            verticalArrangement =
                if (isSpaExpressiveEnabled) Arrangement.spacedBy(SettingsSpace.extraSmall1)
                else Arrangement.Top,
            content = { CompositionLocalProvider(LocalIsInCategory provides true) { content() } },
        )
    }
}

/** Sukrara: the category's margins (16 dp beside it, 4 dp above and below) while it has content; none while empty. */
private fun Modifier.marginsWhenNotEmpty(): Modifier =
    layout { measurable, constraints ->
        val h = SettingsSpace.small1.roundToPx()
        val v = SettingsSpace.extraSmall4.roundToPx()
        val p = measurable.measure(constraints.offset(-2 * h, -2 * v))
        val width = constraints.constrainWidth(p.width + 2 * h)
        if (p.height == 0) {
            layout(width, constraints.constrainHeight(0)) { p.place(h, 0) }
        } else {
            layout(width, constraints.constrainHeight(p.height + 2 * v)) { p.place(h, v) }
        }
    }

/** LocalIsInCategory containing the if the current composable is in a category. */
internal val LocalIsInCategory = staticCompositionLocalOf { false }

@Preview
@Composable
private fun CategoryPreview() {
    SettingsTheme {
        Category(title = "Appearance") {
            Preference(
                object : PreferenceModel {
                    override val title = "Title"
                    override val summary = { "Summary" }
                }
            )
            Preference(
                object : PreferenceModel {
                    override val title = "Title"
                    override val summary = { "Summary" }
                    override val icon =
                        @Composable { SettingsIcon(imageVector = Icons.Outlined.TouchApp) }
                }
            )
        }
    }
}
