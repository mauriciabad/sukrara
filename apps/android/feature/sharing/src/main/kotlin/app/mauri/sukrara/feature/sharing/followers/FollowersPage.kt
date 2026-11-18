// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.followers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.designsystem.avatar.AvatarImage
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.NumberButton
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.designsystem.sharing.PathIcon
import app.mauri.sukrara.designsystem.sharing.PathMark
import app.mauri.sukrara.feature.sharing.NearbyUi
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.mark
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.settings.SettingsSpec
import app.mauri.sukrara.share.owner.FollowerRecord
import app.mauri.sukrara.share.owner.OwnerShareState
import java.text.DateFormat
import java.util.Date
import app.mauri.sukrara.core.designsystem.R as DsR

/** What the followers page can do; each goes to the engine or another page. */
class FollowersActions(
    val review: (String) -> Unit = {},
    val remove: (String) -> Unit = {},
    val setWriter: (String, Boolean) -> Unit = { _, _ -> },
    val share: () -> Unit = {},
    val profile: () -> Unit = {},
    val backup: () -> Unit = {},
    /** The follower dialog closed (its refusal note goes away). */
    val closeFollower: () -> Unit = {},
    /** *Over the internet*. */
    val setInternet: (Boolean) -> Unit = {},
    /** *Upload interval*, minutes (0 = every reading). */
    val setInterval: (Double) -> Unit = {},
    /** The Bluetooth switch. */
    val setNearby: (Boolean) -> Unit = {},
    /** Bluetooth turned on / Nearby devices allowed: look again. */
    val refreshNearby: () -> Unit = {},
    /** *Pair nearby* on (10 min) / *Stop*. */
    val pairNearby: (Boolean) -> Unit = {},
    /** Settings › Sharing › Over Bluetooth nearby. */
    val openNearby: () -> Unit = {},
)

/**
 * Settings › Sharing while sharing: how the wearer appears (opens How you appear), Backup, requests waiting, the
 * followers with their pictures, those who can add treatments marked (a follower opens [FollowerSheet]), and the past
 * ones (removed, or left by themselves: *Left <date>*) folded under a count. Share invites someone.
 */
@Composable
fun FollowersPage(
    state: OwnerShareState.Sharing,
    actions: FollowersActions,
    modifier: Modifier = Modifier,
    writerRefused: String? = null,
    nearby: NearbyUi = NearbyUi(),
    /** The page's foot: *See also* the other data pages (the app draws them). */
    links: @Composable () -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var showRemoved by rememberSaveable { mutableStateOf(false) }
    var choosingInterval by rememberSaveable { mutableStateOf(false) }
    val everyReading = stringResource(R.string.sh_interval_every_reading)
    val active = state.followers.filter { it.active }
    val removed = state.followers.filter { !it.active }.sortedByDescending { it.revoked }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup {
            YouRow(state.profile, active.size, actions.profile)
            NavigationRow(
                title = stringResource(R.string.sh_backup_title),
                summary = stringResource(if (state.backedUp) R.string.sh_backup_row_done else R.string.sh_backup_row_missing),
                onClick = actions.backup,
                icon = DsR.drawable.ds_ic_backup,
                // not backed up: a new phone would lose the followers (the PoC's red backup icon)
                warning = !state.backedUp,
            )
            NavigationRow(
                title = stringResource(R.string.sh_followers_share),
                onClick = actions.share,
                icon = DsR.drawable.ds_ic_share,
            )
        }
        if (state.pending.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.sh_followers_waiting)) {
                for (p in state.pending) {
                    PersonRow(p.avatarJson, p.name, stringResource(R.string.sh_followers_review)) { actions.review(p.follower) }
                }
            }
        }
        SettingsGroup(title = pluralStringResource(R.plurals.sh_followers_count, active.size, active.size)) {
            if (active.isEmpty()) {
                InfoRow(
                    stringResource(R.string.sh_followers_empty_title),
                    summary = stringResource(R.string.sh_followers_empty_body),
                )
            }
            for (f in active) {
                // the exception only, as a mark on the picture (the treatment list's way); TalkBack reads it as words
                val since = stringResource(R.string.sh_followers_since, date(f.since))
                PersonRow(f.avatarJson, f.name, since, writer = f.key in state.writers, mark = nearby.mark(f.key, state.nearbyPaired)) {
                    open = f.key
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.sh_nearby_group)) {
            SwitchRow(
                title = stringResource(R.string.sh_internet_title),
                checked = nearby.internet,
                onCheckedChange = actions.setInternet,
                summary = stringResource(if (nearby.internet) R.string.sh_internet_on else R.string.sh_internet_off),
                icon = DsR.drawable.ds_ic_public,
            )
            ValueRow(
                stringResource(R.string.sh_interval_title),
                if (nearby.internet) intervalText(nearby.intervalMin) else stringResource(R.string.sh_interval_unavailable),
                { choosingInterval = true },
                icon = DsR.drawable.ds_ic_schedule,
                enabled = nearby.internet,
            )
            // the row opens its page (the fix, Pair nearby, the devices nearby); the switch turns it on or off
            NearbyRow(nearby, actions)
        }
        if (removed.isNotEmpty()) {
            // the hidden rest of the list: SettingsLib's NumberButtonPreference (*2 Removed*); *See less* folds it again
            if (!showRemoved) {
                NumberButton(removed.size, stringResource(R.string.sh_followers_removed_show), { showRemoved = true })
            }
            Reveal(showRemoved) {
                Column {
                    SettingsGroup(title = stringResource(R.string.sh_followers_removed)) {
                        for (f in removed) {
                            PersonRow(
                                f.avatarJson,
                                f.name,
                                stringResource(
                                    if (f.left) R.string.sh_followers_left_at else R.string.sh_followers_removed_at,
                                    date(f.revoked ?: 0),
                                ),
                                onClick = null,
                            )
                        }
                    }
                    NumberButton(null, stringResource(R.string.sh_followers_removed_hide), { showRemoved = false })
                }
            }
        }
        FooterText(stringResource(R.string.sh_followers_footer))
        links()
    }
    if (choosingInterval) {
        val words = CardWords.of(LocalContext.current.resources).durations
        NumberDialog(
            stringResource(R.string.sh_interval_title),
            NumberInput(SettingsSpec.NOSTR_INTERVAL),
            nearby.intervalMin,
            { if (it == 0.0) everyReading else Durations.text(it.toLong(), words) },
            { choosingInterval = false },
            onChoose = actions.setInterval,
        )
    }
    active.firstOrNull { it.key == open }?.let { f ->
        FollowerSheet(
            f,
            nearby = nearby.mark(f.key, state.nearbyPaired),
            writer = f.key in state.writers,
            refused = writerRefused == f.key,
            onWriter = { actions.setWriter(f.key, it) },
            onRemove = { actions.remove(f.key) },
            onClose = {
                open = null
                actions.closeFollower()
            },
        )
    }
}

/** The upload interval's value: *Every reading*, or *Every 15 min* (THE duration formatter). */
@Composable
private fun intervalText(min: Double): String =
    if (min <= 0.0) {
        stringResource(R.string.sh_interval_every_reading)
    } else {
        stringResource(R.string.sh_interval_every, Durations.text(min.toLong(), CardWords.of(LocalContext.current.resources).durations))
    }

/** The wearer: picture, name (or "Add your name") and how many follow; opens How you appear. */
@Composable
private fun YouRow(
    profile: Profile?,
    followers: Int,
    onClick: () -> Unit,
) {
    val name = profile?.name?.ifBlank { null }
    NavigationRow(
        name ?: stringResource(R.string.sh_profile_add_name),
        onClick,
        summary = stringResource(R.string.sh_profile_row_summary, pluralStringResource(R.plurals.sh_followers_count, followers, followers)),
        // the picture followers see, decorative: the name says who
        leading = { AvatarImage(profile?.avatar, name.orEmpty(), size = 40.dp) },
    )
}

/**
 * A person (a follower, a request) as SPA's Preference with their picture; [writer]: the picture carries the *Can add
 * treatments* mark ([FollowerPicture]); [mark]: their Bluetooth mark at the row's end (paired nearby: connected or
 * not). TalkBack reads both as words after the summary. [onClick] null: a fact, not tappable.
 */
@Composable
internal fun PersonRow(
    avatarJson: String?,
    name: String,
    summary: String,
    writer: Boolean = false,
    mark: PathMark? = null,
    onClick: (() -> Unit)?,
) {
    val shown = Profile.shownName(name).ifBlank { stringResource(R.string.sh_approve_unnamed) }
    val picture: @Composable () -> Unit = { FollowerPicture(avatarJson, shown, writer) }
    if (onClick != null) {
        val extra =
            listOfNotNull(stringResource(R.string.sh_followers_can_add).takeIf { writer }, mark?.let { stringResource(it.words) })
        NavigationRow(
            shown,
            onClick,
            summary = summary,
            leading = picture,
            summaryDescription = if (extra.isEmpty()) null else (listOf(summary) + extra).joinToString(", "),
            trailing = mark?.let { m -> { PathIcon(m, Modifier.padding(start = 16.dp)) } },
        )
    } else {
        InfoRow(shown, summary = summary, leading = picture)
    }
}

private fun date(epochS: Long): String = followerDate(epochS)
