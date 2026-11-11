// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mauri.sukrara.feature.sharing.Invite
import app.mauri.sukrara.feature.sharing.NearbyUi
import app.mauri.sukrara.feature.sharing.OwnerSharing
import app.mauri.sukrara.feature.sharing.profile.ProfileSave
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.owner.ApproveResult
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.PendingRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The invite being made on the share screen. */
sealed interface InviteUi {
    data object None : InviteUi

    data object Creating : InviteUi

    data class Ready(val invite: Invite) : InviteUi

    /** No identity, a conflict, or the relays refused: say so, offer Try again. */
    data object Failed : InviteUi
}

/** An Allow in progress ([refused] null) or refused (why), for [request] — kept so the answer shows after it's gone. */
data class ApproveUi(val request: PendingRequest, val working: Boolean, val refused: ApproveResult? = null)

/**
 * Share screen, approve dialog, followers page, How you appear and Backup: every action goes through [OwnerSharing]
 * (the engine). Secrets never sit in this view model: the recovery code is returned to the dialog that shows it.
 */
class SharingViewModel(
    private val sharing: OwnerSharing,
    /** Epoch seconds (an invite's expiry). */
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {
    val state: StateFlow<OwnerShareState> = sharing.state

    private val _invite = MutableStateFlow<InviteUi>(InviteUi.None)
    val invite: StateFlow<InviteUi> = _invite.asStateFlow()

    /** The watch over the invite shown (kept across the screen's pauses, so a denied request still has its name). */
    private var watch: Pair<String, InviteWatch>? = null

    /**
     * What happened to the invite shown, live: asked, allowed (a new follower), denied or expired ([InviteProgress]);
     * [InviteProgress.Open] while none is shown.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<InviteProgress> =
        _invite
            .flatMapLatest { ui ->
                val shown = (ui as? InviteUi.Ready)?.invite ?: return@flatMapLatest flowOf(InviteProgress.Open)
                val w = watch?.takeIf { it.first == shown.token }?.second ?: InviteWatch(shown.token, shown.expiresAt)
                watch = shown.token to w
                combine(sharing.state, ticks(shown.expiresAt)) { s, t -> w.progress(s, t) }
            }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), InviteProgress.Open)

    /** The time now, and once more when [expiresAt] passes (an unused invite then shows as expired). */
    private fun ticks(expiresAt: Long): Flow<Long> =
        flow {
            emit(now())
            val wait = expiresAt - now()
            if (wait > 0) {
                delay(wait * 1000)
                emit(now())
            }
        }

    private val _approve = MutableStateFlow<ApproveUi?>(null)
    val approve: StateFlow<ApproveUi?> = _approve.asStateFlow()

    private val _profileSave = MutableStateFlow(ProfileSave.Idle)
    val profileSave: StateFlow<ProfileSave> = _profileSave.asStateFlow()

    /** A "Can add entries" change that was refused (the switch shows the engine's value again; a note says why). */
    private val _writerRefused = MutableStateFlow<String?>(null)
    val writerRefused: StateFlow<String?> = _writerRefused.asStateFlow()

    fun createInvite(label: String) {
        _invite.value = InviteUi.Creating
        viewModelScope.launch {
            _invite.value = sharing.createInvite(label)?.let(InviteUi::Ready) ?: InviteUi.Failed
        }
    }

    /** Another invite for someone else (the shown one stays valid until used or expired). */
    fun newInvite() {
        _invite.value = InviteUi.None
    }

    /** Allow, once: a second tap while it runs does nothing (the dialog may already show the next request). */
    fun approve(request: PendingRequest, typedCode: String? = null) {
        if (_approve.value?.working == true) return
        _approve.value = ApproveUi(request, working = true)
        viewModelScope.launch {
            val result = sharing.approve(request.follower, typedCode)
            _approve.value = if (result == ApproveResult.Allowed) null else ApproveUi(request, working = false, refused = result)
        }
    }

    /** The wearer read why an Allow didn't happen. */
    fun approveSeen() {
        if (_approve.value?.working != true) _approve.value = null
    }

    /** The follower dialog closed: a refusal note shouldn't come back with it. */
    fun writerSeen() {
        _writerRefused.value = null
    }

    fun deny(follower: String) {
        _approve.value = null
        viewModelScope.launch { sharing.deny(follower) }
    }

    fun remove(follower: String) = viewModelScope.launch { sharing.remove(follower) }

    fun setWriter(follower: String, allowed: Boolean) {
        _writerRefused.value = null
        viewModelScope.launch { if (!sharing.setWriter(follower, allowed)) _writerRefused.value = follower }
    }

    /** "How you appear": [onSaved] runs once the engine took it; a refusal keeps the edit and shows why. */
    fun setProfile(profile: Profile, onSaved: () -> Unit) {
        _profileSave.value = ProfileSave.Saving
        viewModelScope.launch {
            val ok = sharing.setProfile(profile)
            _profileSave.value = if (ok) ProfileSave.Idle else ProfileSave.Failed
            if (ok) onSaved()
        }
    }

    /** Bluetooth nearby (switch, state, named links) while the sharing page shows. */
    val nearby: StateFlow<NearbyUi> = sharing.nearby.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), NearbyUi())

    fun setInternet(on: Boolean) {
        viewModelScope.launch { sharing.setInternet(on) }
    }

    fun setUploadInterval(min: Double) {
        viewModelScope.launch { sharing.setUploadInterval(min) }
    }

    fun setNearby(on: Boolean) {
        viewModelScope.launch { sharing.setNearby(on) }
    }

    fun refreshNearby() = sharing.refreshNearby()

    fun pairNearby(on: Boolean) {
        viewModelScope.launch { sharing.pairNearby(on) }
    }

    fun retryConflict() {
        viewModelScope.launch { sharing.retryConflict() }
    }

    /** The recovery code for [password] (slow: scrypt), or null. The caller keeps it only while it is shown. */
    suspend fun recoveryCode(password: String): String? = sharing.exportRecovery(password)

    fun recoverySaved() {
        viewModelScope.launch { sharing.recoverySaved() }
    }

    /** Hands the identity to the password manager through [save] (the screen's Credential Manager call). */
    suspend fun saveToPasswordManager(save: suspend (id: String, secret: String) -> Boolean): Boolean = sharing.saveToPasswordManager(save)

    companion object {
        private const val STOP_AFTER_MS = 5_000L

        fun factory(sharing: OwnerSharing) = viewModelFactory { initializer { SharingViewModel(sharing) } }
    }
}
