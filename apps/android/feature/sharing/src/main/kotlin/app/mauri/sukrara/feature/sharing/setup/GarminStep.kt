// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.groupCard
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.SetupWatchApp
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * *Your Garmin watch* (optional; only while Garmin Connect is on this phone or a watch has one of Sukrara's apps,
 * #199): *Send to Sukrara on your watch* (on by default; the same switch as Settings › Share with apps › Garmin, stored
 * as it turns), then each of Sukrara's watch apps in one line with *Get* (its Connect IQ store page: its listing, or
 * the store's search until it has an id, #247) or *On your watch*. *Continue* keeps the switch as set; *Skip* leaves it as it is. Both
 * are final: the Garmin page keeps all of it.
 */
@Composable
internal fun ColumnScope.GarminStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val on = u.facts.garmin != false
    val haptics = rememberHaptics()
    StepCircle(DsR.drawable.ds_ic_watch)
    StepHeader(stringResource(R.string.sh_setup_garmin_title), stringResource(R.string.sh_setup_garmin_body))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.groupCard),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .toggleable(on, enabled = !u.busy, role = Role.Switch) {
                    haptics.toggle(it)
                    actions.setGarmin(it)
                }.heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sh_setup_garmin_switch),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(checked = on, onCheckedChange = null)
        }
    }
    for (app in u.garminApps) WatchAppRow(app)
    Text(
        stringResource(R.string.sh_setup_garmin_later),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StepButtons(
        stringResource(R.string.sh_setup_continue),
        actions.completeGarmin,
        enabled = !u.busy,
        secondary = listOf(stringResource(R.string.sh_setup_skip) to { actions.skip(SetupStep.Garmin) }),
    )
}

/** One of Sukrara's watch apps: what it gives, then *Get* (its store listing) or *On your watch*. */
@Composable
private fun WatchAppRow(app: SetupWatchApp) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val name = stringResource(app.name)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.groupCard),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(app.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(app.what),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // a watch reporting it in a moment swaps Get for the check
            AnimatedContent(app.onWatch, label = "watch-app") { onWatch ->
                when {
                    onWatch -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                painterResource(DsR.drawable.ds_ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.sh_setup_garmin_on_watch),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        val description = stringResource(R.string.sh_setup_garmin_get_description, name)
                        OutlinedButton(
                            onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(app.url(language)))) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
                        ) { Text(stringResource(R.string.sh_setup_garmin_get)) }
                    }
                }
            }
        }
    }
}
