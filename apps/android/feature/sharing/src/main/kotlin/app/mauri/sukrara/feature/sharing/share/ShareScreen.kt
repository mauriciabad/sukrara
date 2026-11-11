// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.avatar.AvatarImage
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.feature.sharing.Invite
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.setup.NoteCard
import app.mauri.sukrara.feature.sharing.setup.StepHeader
import app.mauri.sukrara.feature.sharing.setup.startSafely
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.owner.FollowerRecord
import app.mauri.sukrara.share.owner.PendingRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * "Share with someone": a label only the wearer sees, then a single-use invite as a QR (springs in) and a link to send
 * or copy, its expiry and what the follower will see. Every invite still needs the wearer's Allow. While
 * [internetOff] (an invite link and its request travel over the internet) it says so and offers to turn it on.
 * The screen follows the invite by itself ([progress]): someone asked (*Review request* brings back a request put off
 * with Later), they follow now (*Done* back to Settings › Sharing, where they're listed), denied, or expired unused;
 * each outcome offers *Invite someone else*.
 */
@Composable
fun ShareScreen(
    invite: InviteUi,
    onCreate: (String) -> Unit,
    onAnother: () -> Unit,
    modifier: Modifier = Modifier,
    internetOff: Boolean = false,
    onTurnOnInternet: () -> Unit = {},
    progress: InviteProgress = InviteProgress.Open,
    onReview: (follower: String) -> Unit = {},
    onDone: () -> Unit = {},
) {
    val outcome = invite is InviteUi.Ready && progress !is InviteProgress.Open && progress !is InviteProgress.Asked
    var label by rememberSaveable { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            // its own page (no settings scaffold under it): without a surface the window's dark background showed
            // through with the light theme's dark text
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHeader(stringResource(R.string.sh_share_title), stringResource(R.string.sh_share_body))
        when (invite) {
            InviteUi.None, InviteUi.Creating, InviteUi.Failed -> {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(LABEL_MAX) },
                    label = { Text(stringResource(R.string.sh_share_label)) },
                    supportingText = { Text(stringResource(R.string.sh_share_label_support)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invite is InviteUi.Failed) NoteCard(stringResource(R.string.sh_share_failed), error = true)
                if (internetOff) {
                    NoteCard(stringResource(R.string.sh_share_internet_off))
                    OutlinedButton(onClick = onTurnOnInternet, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.sh_share_internet_turn_on))
                    }
                }
                Button(
                    onClick = { onCreate(label) },
                    enabled = invite !is InviteUi.Creating && !internetOff,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(if (invite is InviteUi.Failed) R.string.sh_share_try_again else R.string.sh_share_create)) }
            }

            is InviteUi.Ready -> {
                val reduced = LocalReducedMotion.current
                // the screen moves on with the invite: a fade through for each new step (none with reduced motion)
                AnimatedContent(
                    progress,
                    contentKey = { it::class },
                    transitionSpec = { Motion.fadeThrough(reduced) },
                    label = "invite",
                ) { p ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (p) {
                            InviteProgress.Open -> ReadyInvite(invite.invite, onAnother)
                            is InviteProgress.Asked -> InviteAsked(p.request, onReview)
                            is InviteProgress.Following -> InviteFollowing(p.follower, onDone, onAnother)
                            is InviteProgress.Denied -> InviteEnded(denied = p, onDone = onDone, onAnother = onAnother)
                            InviteProgress.Expired -> InviteEnded(denied = null, onDone = onDone, onAnother = onAnother)
                        }
                    }
                }
            }
        }
        if (!outcome) {
            Text(stringResource(R.string.sh_share_they_see), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.sh_share_they_see_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Someone asked with the invite: who, and *Review request* (the approve dialog; also after Later). */
@Composable
private fun ColumnScope.InviteAsked(
    request: PendingRequest,
    onReview: (String) -> Unit,
) {
    val name = Profile.shownName(request.name).ifBlank { stringResource(R.string.sh_approve_unnamed) }
    val haptics = rememberHaptics()
    LaunchedEffect(request.follower) { haptics.tick() }
    AvatarImage(request.avatarJson, name, Modifier.align(Alignment.CenterHorizontally), size = 96.dp)
    OutcomeTitle(stringResource(R.string.sh_share_asked_title, name))
    OutcomeBody(stringResource(R.string.sh_share_asked_body))
    Button(onClick = { onReview(request.follower) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.sh_share_review))
    }
}

/** Allowed: the new follower's picture springs in with a confirm haptic; *Done* goes back to the followers list. */
@Composable
private fun ColumnScope.InviteFollowing(
    follower: FollowerRecord,
    onDone: () -> Unit,
    onAnother: () -> Unit,
) {
    val name = Profile.shownName(follower.name).ifBlank { stringResource(R.string.sh_approve_unnamed) }
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val scale = remember(follower.key) { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(follower.key) {
        haptics.confirm()
        scale.animateTo(1f, Motion.defaultSpatial(reduced))
    }
    Box(
        Modifier.align(Alignment.CenterHorizontally).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    ) {
        AvatarImage(follower.avatarJson, name, size = 96.dp)
        Icon(
            painterResource(DsR.drawable.ds_ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
    OutcomeTitle(stringResource(R.string.sh_share_following_title, name))
    OutcomeBody(stringResource(R.string.sh_share_following_body))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.sh_share_done)) }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.sh_share_another))
    }
}

/** Denied ([denied], with who asked when known) or expired unused ([denied] null): the invite can't be used any more. */
@Composable
private fun ColumnScope.InviteEnded(
    denied: InviteProgress.Denied?,
    onDone: () -> Unit,
    onAnother: () -> Unit,
) {
    val title =
        when {
            denied == null -> {
                stringResource(R.string.sh_share_expired_title)
            }

            else -> {
                val name = denied.name?.let(Profile::shownName)?.ifBlank { null } ?: stringResource(R.string.sh_approve_unnamed)
                stringResource(R.string.sh_share_denied_title, name)
            }
        }
    OutcomeTitle(title)
    OutcomeBody(stringResource(if (denied == null) R.string.sh_share_expired_body else R.string.sh_share_denied_body))
    Button(
        onClick = onAnother,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.sh_share_another)) }
    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.sh_share_done))
    }
}

/** The step's headline, centred under the picture: a heading TalkBack announces as soon as the screen moves on. */
@Composable
private fun OutcomeTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier =
            Modifier.fillMaxWidth().semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
private fun OutcomeBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.ReadyInvite(
    invite: Invite,
    onAnother: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val qr = remember(invite.link) { qrBitmap(invite.link, 768) }
    val scale = remember(invite.link) { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(invite.link) {
        haptics.confirm()
        scale.animateTo(1f, Motion.defaultSpatial(reduced))
    }
    val qrLabel = stringResource(R.string.sh_share_qr_label)
    Image(
        qr.asImageBitmap(),
        contentDescription = null,
        modifier =
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(240.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp)
                .semantics { contentDescription = qrLabel },
    )
    Text(stringResource(R.string.sh_share_scan), style = MaterialTheme.typography.bodyMedium)
    val expiry = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(invite.expiresAt * 1000))
    NoteCard(
        if (invite.label.isEmpty()) {
            stringResource(R.string.sh_share_note_unnamed, expiry)
        } else {
            stringResource(R.string.sh_share_note, expiry, invite.label)
        },
    )
    // the link as selectable text too, so TalkBack users and any app can copy it
    SelectionContainer { Text(invite.link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val chooser = stringResource(R.string.sh_share_send_chooser)
    val message = stringResource(R.string.sh_share_send_message, invite.link)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                context.startSafely(Intent.createChooser(send, chooser))
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.sh_share_send)) }
        OutlinedButton(
            onClick = {
                haptics.confirm()
                scope.launch { clipboard.setClipEntry(ClipData.newPlainText(chooser, invite.link).toClipEntry()) }
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.sh_share_copy)) }
    }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.sh_share_another))
    }
}

private const val LABEL_MAX = 40

/** The invite link as a black-on-white QR bitmap ([size] px square, quiet zone included). */
internal fun qrBitmap(
    text: String,
    size: Int,
): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels =
        IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
