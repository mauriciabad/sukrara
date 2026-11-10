// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.protocol.invite.NearbyPairing
import app.mauri.sukrara.share.follower.PairingCheck

/**
 * A *Pair nearby* join on the follower's phone (PROTOCOL-v2 §Nearby pairing): *Asking Ana's phone…* until the wearer's
 * phone answers, then the 6-digit code both phones show with *Same code* / *Different code*; after *Same code*,
 * waiting for the wearer's Allow. The code is read digit by digit by TalkBack; it arrives with a tick.
 */
@Composable
internal fun PairingCodeCard(
    check: PairingCheck,
    wearer: String,
    onSame: () -> Unit,
    onDifferent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedContent(check.code to check.confirmed, label = "pairing") { (code, confirmed) ->
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (code == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.sh_pair_asking, wearer), style = MaterialTheme.typography.bodyLarge)
                    }
                    return@Column
                }
                LaunchedEffect(code) { haptics.tick() }
                Text(
                    stringResource(if (confirmed) R.string.sh_pair_waiting else R.string.sh_pair_compare, wearer),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    NearbyPairing.spaced(code),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { contentDescription = code.toList().joinToString(" ") },
                )
                if (!confirmed) {
                    Text(stringResource(R.string.sh_pair_why, wearer), style = MaterialTheme.typography.bodyMedium)
                    // wraps at large font sizes instead of squeezing the buttons
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.reject()
                                onDifferent()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.sh_pair_different)) }
                        Button(
                            onClick = {
                                haptics.confirm()
                                onSame()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.sh_pair_same)) }
                    }
                }
            }
        }
    }
}
