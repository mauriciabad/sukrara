// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.ble.BleMessages
import app.mauri.sukrara.protocol.event.AlertPayload
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.SensorLife
import app.mauri.sukrara.share.ble.NearbyServerCore
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.owner.usecase.ApproveRequest
import app.mauri.sukrara.share.owner.usecase.CloseNearbyPairing
import app.mauri.sukrara.share.owner.usecase.ControlEvents
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.CreateInvite
import app.mauri.sukrara.share.owner.usecase.DenyRequest
import app.mauri.sukrara.share.owner.usecase.ExportRecovery
import app.mauri.sukrara.share.owner.usecase.HandleInbox
import app.mauri.sukrara.share.owner.usecase.IdentityConflictDetector
import app.mauri.sukrara.share.owner.usecase.InboxSignal
import app.mauri.sukrara.share.owner.usecase.KeepIdentity
import app.mauri.sukrara.share.owner.usecase.MirroredSettingsSource
import app.mauri.sukrara.share.owner.usecase.OpenNearbyPairing
import app.mauri.sukrara.share.owner.usecase.OwnerContext
import app.mauri.sukrara.share.owner.usecase.PublishReadings
import app.mauri.sukrara.share.owner.usecase.PublishSettingsMirror
import app.mauri.sukrara.share.owner.usecase.RemoveFollower
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import app.mauri.sukrara.share.owner.usecase.SetProfile
import app.mauri.sukrara.share.owner.usecase.SetWriter
import app.mauri.sukrara.share.owner.usecase.Step
import app.mauri.sukrara.share.owner.usecase.SyncTreatments
import app.mauri.sukrara.share.owner.usecase.TellFollowers
import app.mauri.sukrara.share.transport.RelayFilter
import app.mauri.sukrara.share.transport.RelayInbound
import app.mauri.sukrara.share.transport.RelayTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.mauri.sukrara.protocol.invite.NearbyPairing as PairingCode

/**
 * The wearer's sharing coordinator: loads the identity, keeps the relay subscriptions, feeds readings from the
 * [GlucoseHub] and the wearer's sensor to [PublishReadings], the treatment log and the mirrored settings to followers ([SyncTreatments],
 * [PublishSettingsMirror]), runs every use-case one at a time (serial dispatcher + one lock held across the
 * suspending store / ledger calls, so no update overwrites another), persists before publishing, and
 * exposes [state]. Relay failures stay in the per-relay status. Logs nothing that holds keys or glucose values.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class OwnerEngine(
    private val ctx: OwnerContext,
    private val store: OwnerIdentityStore,
    private val transport: RelayTransport,
    private val hub: GlucoseHub,
    private val seen: SeenWraps,
    treatmentLog: TreatmentsRepository,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val settings: MirroredSettingsSource = MirroredSettingsSource.None,
    private val nearby: OwnerNearby = OwnerNearby.None,
    /** *Over the internet* and the upload interval (Settings › Sharing); read at start and followed while running. */
    private val relayPolicy: Flow<RelayPolicy> = flowOf(RelayPolicy()),
    /** The wearer's current sensor (null = none known): every live event carries it (PROTOCOL-v2 "Sensor life"). */
    private val sensor: Flow<SensorLife?> = flowOf(null),
    /** Called for a new access request (the notification with Allow / Deny lives in the app layer). */
    private val onRequest: (PendingRequest) -> Unit = {},
) {
    private val serial = dispatcher.limitedParallelism(1)
    private val lock = Mutex()
    private val readings = PublishReadings(ctx)
    private val conflicts = IdentityConflictDetector(ctx)
    private val treatments = SyncTreatments(ctx, treatmentLog)
    private val settingsMirror = PublishSettingsMirror(ctx)
    private val _state = MutableStateFlow<OwnerShareState>(OwnerShareState.Loading)
    val state: StateFlow<OwnerShareState> = _state.asStateFlow()

    private val _sent = MutableStateFlow(SentCounts())

    /** The live readings and hour chunks handed to the relays / nearby followers since the app started (Settings › Advanced). */
    val sent: StateFlow<SentCounts> = _sent.asStateFlow()

    @Volatile private var owner: OwnerState? = null

    /** How readings travel over the internet now ([relayPolicy]'s latest value while running). */
    @Volatile private var policy = RelayPolicy()

    @Volatile private var jobs = emptyList<Job>()

    /** Whether sharing runs (started with an identity). */
    val running: Boolean get() = jobs.isNotEmpty()
    private var historyRepublishedAt = 0L

    private fun now() = clock.now().epochSecond

    /** Every entry point: one at a time, including across suspensions inside (SecureStore, Room). */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(serial) { lock.withLock { block() } }

    /** The identity key for NIP-42 AUTH (relays serve the wraps addressed to it only after AUTH). */
    fun identityKey(): app.mauri.sukrara.protocol.KeyPair? = owner?.identity(ctx.crypto)

    /** Loads the identity and, if there is one, starts sharing in [scope] (the foreground service's). */
    suspend fun start(scope: CoroutineScope) =
        locked {
            if (jobs.isNotEmpty()) return@locked
            when (val l = store.load()) {
                OwnerIdentityStore.Load.Missing -> _state.value = OwnerShareState.NoIdentity
                is OwnerIdentityStore.Load.Unreadable -> _state.value = OwnerShareState.IdentityUnreadable(l.transient)
                is OwnerIdentityStore.Load.Loaded -> run(scope, l.state)
            }
        }

    suspend fun stop() =
        locked {
            // a closed Pair nearby can't be claimed later over the relays
            pairingNow.value?.let { p ->
                owner?.let { s -> CloseNearbyPairing(ctx)(s, p.token, now())?.let { apply(it, overBluetooth = false) } }
            }
            halt()
        }

    private fun halt() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        transport.stop()
        nearby.stop()
        pairingTimer?.cancel()
        pairingTimer = null
        pairingNow.value = null
        runScope = null
    }

    @Volatile private var runScope: CoroutineScope? = null

    /** The identity's public key while running (the backup's `d`, the inbox's `#p`). */
    @Volatile private var identityPub: String? = null

    /** What the Bluetooth server asks the engine (from its own thread: reads the current state snapshot). */
    private val nearbyHost =
        object : OwnerNearby.Host {
            override fun sid(): String? = owner?.takeIf { it.conflict == null }?.sid

            override fun followers(): Collection<PublicKeyHex> =
                owner?.activeFollowers()?.mapNotNull { PublicKeyHex.parse(it.key) }.orEmpty()

            override fun pairing(): BleMessages.Pairing? {
                val p = pairingNow.value ?: return null
                val s = owner?.takeIf { it.conflict == null } ?: return null
                val invite = s.invites.firstOrNull { it.token == p.token } ?: return null
                if (invite.claimedBy != null || invite.done || invite.expiry <= now()) return null
                val nonce = invite.pairNonce ?: return null
                return BleMessages.Pairing(
                    s.identity(ctx.crypto).public,
                    s.relays,
                    p.token,
                    p.expiry,
                    ControlEvents.profile(s),
                    PairingCode.commit(nonce),
                )
            }

            override fun onEvent(event: SignedEvent, peer: String) {
                runScope?.launch(serial) { locked { onNearby(event, peer) } }
            }
        }

    /** *Pair nearby* while open: its token and when it ends (epoch s). */
    data class NearbyPairing(val token: String, val expiry: Long)

    private val pairingNow = MutableStateFlow<NearbyPairing?>(null)

    /** Settings › Sharing shows the countdown and *Stop*; null when closed (first request, expiry, Stop, sharing stopped). */
    val nearbyPairing: StateFlow<NearbyPairing?> = pairingNow.asStateFlow()

    @Volatile private var pairingTimer: Job? = null

    /**
     * *Pair nearby*: a 10-minute single-use invite offered in the Bluetooth INFO (B§7.3); the phone advertises while
     * it's open even with no follower. False without an identity, during a conflict or when sharing isn't running.
     */
    suspend fun openNearbyPairing(): Boolean =
        locked {
            val scope = runScope ?: return@locked false
            val s = owner?.takeIf { it.conflict == null } ?: return@locked false
            val opened = OpenNearbyPairing(ctx)(s, now())
            apply(opened.step, overBluetooth = false)
            pairingTimer?.cancel()
            pairingNow.value = NearbyPairing(opened.token, opened.expiry)
            pairingTimer =
                scope.launch {
                    delay((opened.expiry - now()).coerceAtLeast(0) * 1000)
                    locked { endPairing(opened.token) }
                }
            nearby.refresh()
            true
        }

    /** *Stop*: the offer leaves INFO and its unclaimed invite goes. */
    suspend fun closeNearbyPairing() =
        locked {
            val p = pairingNow.value ?: return@locked
            pairingTimer?.cancel()
            pairingTimer = null
            owner?.let { s -> CloseNearbyPairing(ctx)(s, p.token, now())?.let { apply(it, overBluetooth = false) } }
            endPairing(p.token)
        }

    private fun endPairing(token: String) {
        if (pairingNow.value?.token != token) return
        pairingNow.value = null
        nearby.refresh()
    }

    /**
     * An event a follower device wrote over Bluetooth, trusted only by its signatures like a relay event: gift wraps to
     * the identity go to the inbox (requests, rekeys, history), a writer's entry to the log (its echo is published and
     * the event forwarded to the other centrals, B§6).
     */
    private suspend fun onNearby(ev: SignedEvent, peer: String) {
        val s = owner ?: return
        when (ev.kind) {
            Wire.KIND_WRAP -> {
                // verified before it is marked seen: anyone in range could send a forged event with a real wrap's id
                val ours = ev.tag("p") == identityPub && s.conflict == null && ctx.crypto.verify(ev)
                if (ours && seen.markSeen(ev.id, now())) inbox(s, ev)
            }

            Wire.KIND_DATA -> {
                treatments.receive(s, ev, now(), NEARBY_VIA).echo?.let {
                    nearby.forward(ev, except = peer)
                    send(it)
                }
            }
        }
    }

    private suspend fun run(scope: CoroutineScope, s0: OwnerState) {
        // Pair nearby never survives a restart: its unclaimed invite can't be claimed (persisted with the next change)
        val s = s0.copy(invites = s0.invites.filterNot { it.nearby && it.claimedBy == null })
        owner = s
        runScope = scope
        val identity = s.identity(ctx.crypto).public.hex
        identityPub = identity
        // before anything is sent: with internet sharing off not one event reaches a relay
        policy = relayPolicy.first()
        readings.chunkEvery = policy.chunkEvery
        jobs =
            listOf(
                scope.launch(serial) { transport.inbound.collect { locked { onInbound(it) } } },
                scope.launch(serial) { readingsFrom(hub) },
                scope.launch(serial) {
                    sensor.distinctUntilChanged().collect { w -> locked { owner?.let { publish(readings.onSensor(it, w, now())) } } }
                },
                scope.launch(serial) { transport.status.collect { render() } },
                scope.launch(serial) {
                    while (true) {
                        delay(PublishReadings.CHUNK_EVERY * 1000)
                        locked {
                            owner?.let { publishReadings(readings.tick(it, now())) }
                            refreshControl()
                            refreshShared()
                        }
                    }
                },
                scope.launch(serial) { relayPolicy.distinctUntilChanged().collect { locked { onPolicy(it) } } },
                scope.launch(serial) {
                    settings.settings().debounce(SETTINGS_DEBOUNCE_MS).collect { w ->
                        locked { owner?.let { apply(settingsMirror.onChange(it, w, now())) } }
                    }
                },
            )
        transport.subscribe(INBOX, listOf(RelayFilter(kinds = listOf(Wire.KIND_WRAP), p = listOf(identity))))
        transport.subscribe(OWN, listOf(RelayFilter(kinds = listOf(Wire.KIND_DATA), authors = listOf(identity), since = now() - Wire.DAY)))
        if (policy.on) transport.start() else transport.stop()
        // before the first send: the Bluetooth backlog caches what goes out from here on
        nearby.start(nearbyHost)
        s.pendingControl.mapNotNull { SignedEvent.parse(it.eventJson) }.forEach(::send)
        // a replace that never finished (the app closed while telling the followers): they get the key again. Not
        // while this process still tells them (sharing stopped and started again meanwhile): that replace goes on
        if (!replaceBusy) KeepIdentity(ctx)(s, now())?.let { apply(it) }
        publishReadings(readings.republish(s, now()))
        subscribeWriters(s)
        republishShared()
        refreshControl()
        render()
    }

    /**
     * *Over the internet* or the upload interval changed. Off: the relays close (nothing more goes to them; control
     * events wait in `pendingControl`). On again: they connect and get what they missed (waiting control events, the
     * last 24 h of chunks, the treatment log and settings, roster / backup when due); Bluetooth followers already have it.
     */
    private suspend fun onPolicy(next: RelayPolicy) {
        val before = policy
        if (next == before) return
        policy = next
        readings.chunkEvery = next.chunkEvery
        val s = owner ?: return
        if (before.on && !next.on) transport.stop()
        if (!before.on && next.on) {
            transport.start()
            s.pendingControl.mapNotNull { SignedEvent.parse(it.eventJson) }.forEach { send(it, overBluetooth = false) }
            publishReadings(readings.republish(s, now()), overBluetooth = false)
            republishShared(overBluetooth = false)
            refreshControl()
        }
    }

    private suspend fun readingsFrom(hub: GlucoseHub) {
        hub.state
            .filter { it.role == Role.Wearer }
            .map { it.recent }
            .distinctUntilChanged()
            .collect { recent -> locked { owner?.let { publishReadings(readings.onReadings(it, recent, now())) } } }
    }

    private suspend fun onInbound(m: RelayInbound) {
        val s = owner ?: return
        when (m) {
            is RelayInbound.Ok -> {
                // a replace's notice reached a relay: one follower more is told
                val t = tellingNow
                if (m.accepted && t != null && t.notices.values.any { it.id == m.eventId } && t.acked.add(m.eventId)) {
                    _telling.value = t.ui()
                }
                if (m.accepted && s.pendingControl.any { it.eventId == m.eventId }) {
                    commit(s.copy(pendingControl = s.pendingControl.filter { it.eventId != m.eventId }))
                }
            }

            is RelayInbound.Event -> {
                when (m.subscription) {
                    OWN -> conflicts.check(s, m.event, now())?.let { commit(it) }
                    INBOX -> if (s.conflict == null && seen.markSeen(m.event.id, now())) inbox(owner ?: s, m.event)
                    WRITERS -> treatments.receive(s, m.event, now(), m.relay).echo?.let(::send)
                }
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun inbox(s: OwnerState, wrap: SignedEvent) {
        val handled = HandleInbox(ctx)(s, wrap, now())
        apply(handled.step)
        when (val signal = handled.signal) {
            is InboxSignal.NewRequest -> {
                // single use: the first request closes *Pair nearby* (the next INFO no longer offers it)
                if (signal.request.token == pairingNow.value?.token) {
                    pairingTimer?.cancel()
                    pairingTimer = null
                    endPairing(signal.request.token)
                }
                onRequest(signal.request)
            }

            is InboxSignal.HistoryWanted -> {
                if (now() - historyRepublishedAt >= HISTORY_MIN_GAP) {
                    historyRepublishedAt = now()
                    publish(readings.republish(handled.step.state, now(), signal.since))
                }
            }

            null -> {
                Unit
            }
        }
    }

    /** Roster and backup again when older than [CONTROL_REFRESH] (they expire after 30 / 90 days); never during a conflict. */
    private suspend fun refreshControl() {
        val s = owner ?: return
        if (s.conflict != null) return
        var step = Step(s)
        if (now() - s.rosterPublishedAt > CONTROL_REFRESH) step += { ControlEvents.roster(ctx, it, now()) }
        if (now() - s.backupPublishedAt > CONTROL_REFRESH) step += { ControlEvents.backup(ctx, it, now()) }
        if (step.publish.isNotEmpty()) apply(step)
    }

    /** The treatment log and the settings again under the current epoch (start, new epoch, weekly, relays back on). */
    private suspend fun republishShared(overBluetooth: Boolean = true) {
        val s = owner ?: return
        val log = treatments.republish(s, now())
        val mirrored = settingsMirror.republish(log.state, now())
        apply(Step(mirrored.state, log.publish + mirrored.publish), overBluetooth)
    }

    private suspend fun refreshShared() {
        val s = owner ?: return
        if (treatments.republishDue(s, now()) || settingsMirror.republishDue(s, now())) republishShared()
    }

    /** Writers' entries: one subscription for the current writers, closed when there are none. */
    private fun subscribeWriters(s: OwnerState) {
        treatments.writerFilter(s, now())?.let { transport.subscribe(WRITERS, listOf(it)) } ?: transport.close(WRITERS)
    }

    // ---------------------------------------------------------------------------------------------- actions

    /** A version of the treatment log stored on this phone (add, edit, delete, undo, pens) → followers at once. */
    suspend fun publishTreatment(record: TreatmentRecord) {
        locked { owner?.let { send(treatments.publish(it, record, now())) } }
    }

    /**
     * A new identity with how the wearer appears: setup's *Start fresh*, or over the working one with [replaceExisting]
     * (Backup › Replace this identity, after the person confirmed that every follower must be invited again: its
     * followers are told first, see [replaceWith]). Never over an identity that can't be read; nothing changes when
     * the new one can't be saved. False also when the replace was cancelled.
     */
    suspend fun createIdentity(scope: CoroutineScope, profile: Profile?, replaceExisting: Boolean = false): Boolean {
        if (replaceExisting && owner != null) return replaceWith(scope) { CreateIdentity(ctx)(profile, now()).state } == Replace.Done
        return locked {
            if (owner != null || store.load() !is OwnerIdentityStore.Load.Missing) return@locked false
            val step = CreateIdentity(ctx)(profile, now())
            if (!store.save(step.state)) return@locked false
            run(scope, step.state)
            true
        }
    }

    /**
     * Restore from a recovery code. The UI gates it (setup's `RestoreGate`: this phone's sensor runs). Replaces an unreadable
     * identity only on success, and a working one only with [replaceExisting] (Backup › Replace this identity confirms
     * first; its followers are told once the restore is sure to work, see [replaceWith]); the identity this phone
     * already shares as is never "replaced" by itself. Nothing changes on any error or when cancelled.
     */
    suspend fun restore(scope: CoroutineScope, code: String, password: String?, replaceExisting: Boolean = false): RestoreIdentity.Result {
        var replacing = false
        val result =
            locked {
                if (owner != null && !replaceExisting) return@locked RestoreIdentity.Result.AlreadySharing
                val use = RestoreIdentity(ctx)
                val (identity, error) = use.parse(code, password)
                if (identity == null) return@locked error!!
                if (owner?.identity(ctx.crypto)?.public == identity.public) return@locked RestoreIdentity.Result.SameIdentity
                transport.start()
                val found = transport.fetch(use.identityEventsFilter(identity), FETCH_TIMEOUT_MS)
                val result = use.restore(identity, found.events, found.answered, now())
                if (result is RestoreIdentity.Result.Restored) {
                    if (owner != null) {
                        replacing = true
                    } else {
                        halt()
                        // the code that restored it is somewhere besides this phone: no "Not backed up" card
                        val restored = result.step.state.copy(restoredAt = now())
                        store.save(restored, replaceUnreadable = true)
                        // run() publishes the rotation from pendingControl (deliveries before the roster)
                        run(scope, restored)
                    }
                }
                result
            }
        if (!replacing) return result
        val restored = (result as RestoreIdentity.Result.Restored).step.state
        return if (replaceWith(scope) { restored.copy(restoredAt = now()) } == Replace.Done) result else RestoreIdentity.Result.Cancelled
    }

    private enum class Replace { Done, Kept }

    /** A replace telling the followers: each one's notice, the ids a relay accepted (under the lock), Cancel. */
    private class Telling(val notices: Map<String, SignedEvent>) {
        val acked = mutableSetOf<String>()
        val cancelled = MutableStateFlow(false)

        fun ui() = TellingFollowers(notices.size, acked.size)
    }

    @Volatile private var tellingNow: Telling? = null

    @Volatile private var replaceBusy = false

    private val _telling = MutableStateFlow<TellingFollowers?>(null)

    /** Backup › Replace this identity telling the followers (*Telling your followers…* with Cancel); null otherwise. */
    val telling: StateFlow<TellingFollowers?> = _telling.asStateFlow()

    /** Cancel while the followers are told: the identity stays and they follow again. Too late once it is replaced. */
    fun cancelReplace() {
        tellingNow?.cancelled?.value = true
    }

    /**
     * Backup › Replace this identity (PROTOCOL-v2 "Replace the identity"): a `revoked` notice from this identity to
     * every active follower, so their apps say the wearer stopped sharing instead of warning that no data arrives;
     * then, once a relay accepted every notice or after [NOTICE_TIMEOUT_MS] (at once with internet sharing off), [next]
     * replaces the identity: its own events go out and the notices no relay took go out again with them. Cancel (or a
     * failure) before that keeps the identity and sends the told followers the key again. During a conflict nobody is
     * told: the other app still shares as this identity. One replace at a time.
     */
    private suspend fun replaceWith(scope: CoroutineScope, next: () -> OwnerState): Replace {
        val t =
            locked {
                val s = owner?.takeIf { !replaceBusy } ?: return@locked null
                replaceBusy = true
                val told = if (s.conflict == null) TellFollowers(ctx)(s, now()) else null
                told?.let { apply(it.step) }
                Telling(told?.notices.orEmpty()).also {
                    tellingNow = it
                    _telling.value = it.ui().takeIf { u -> u.followers > 0 }
                }
            } ?: return Replace.Kept
        try {
            if (t.notices.isNotEmpty() && policy.on) {
                withTimeoutOrNull(NOTICE_TIMEOUT_MS) {
                    combine(_telling, t.cancelled) { u, cancelled -> cancelled || u == null || u.told >= u.followers }.first { it }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { locked { endTelling(keep = true) } }
            throw e
        }
        return locked {
            if (t.cancelled.value) return@locked endTelling(keep = true)
            val s = owner ?: return@locked endTelling(keep = false)
            // followers approved while waiting are told too (no wait: they go out with the new identity's events)
            val late = if (s.conflict == null) TellFollowers(ctx)(s, now(), except = t.notices.keys) else null
            late?.let { apply(it.step) }
            val unacked = t.notices.filterValues { it.id !in t.acked } + late?.notices.orEmpty()
            val state = TellFollowers.carry(next(), unacked)
            val saved =
                try {
                    store.save(state, replaceUnreadable = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            if (!saved) return@locked endTelling(keep = true)
            endTelling(keep = false)
            halt()
            run(scope, state)
            Replace.Done
        }
    }

    /** The replace ends here; [keep]: the identity stays and the followers told get the key again. */
    private suspend fun endTelling(keep: Boolean): Replace {
        tellingNow = null
        replaceBusy = false
        _telling.value = null
        if (keep) owner?.let { s -> KeepIdentity(ctx)(s, now())?.let { apply(it) } }
        return Replace.Kept
    }

    /** The identity the last recovery code was made for: [markRecoverySaved] counts only for that one. */
    @Volatile private var exportedFor: String? = null

    /** A recovery code (NIP-49, scrypt: 1–3 s, run off the engine's lock). Not a backup until [markRecoverySaved]. */
    suspend fun exportRecovery(password: String): String? {
        val s = owner ?: return null
        return withContext(dispatcher) { ExportRecovery(ctx)(s, password) }.also { exportedFor = s.identityHex }
    }

    /**
     * Hands the identity to the phone's password manager after the wearer tapped Save: [save] gets the entry's name
     * ("Sukrara · npub1abcd…wxyz", several identities stay apart) and the `nsec`, and returns whether the password
     * manager took it; then the "Not backed up" card goes away. The secret goes nowhere else and is never logged.
     * False without an identity or when [save] refused.
     */
    suspend fun saveToPasswordManager(save: suspend (id: String, secret: String) -> Boolean): Boolean {
        val s = owner ?: return false
        val identity = s.identity(ctx.crypto)
        val npub = ctx.crypto.npub(identity.public)
        if (!save("Sukrara · ${npub.take(NPUB_HEAD)}…${npub.takeLast(NPUB_TAIL)}", ctx.crypto.nsec(identity.secret))) return false
        // only for the identity that was saved (a restore may have replaced it meanwhile)
        locked { owner?.takeIf { it.identityHex == s.identityHex }?.let { commit(it.copy(passwordManagerSavedAt = now())) } }
        return true
    }

    /** The wearer kept the recovery code (copied it): the "Not backed up" card goes away. */
    suspend fun markRecoverySaved() =
        locked { owner?.takeIf { it.identityHex == exportedFor }?.let { commit(it.copy(recoveryExportedAt = now())) } }

    /** "How you appear": followers get it in a roster at once. False without an identity or during a conflict. */
    suspend fun setProfile(profile: Profile): Boolean =
        locked {
            val s = owner?.takeIf { it.conflict == null } ?: return@locked false
            SetProfile(ctx)(s, profile, now())?.let { apply(it) }
            true
        }

    /** A new single-use invite link, or null without an identity or during a conflict. */
    suspend fun createInvite(label: String): String? =
        locked {
            val s = owner?.takeIf { it.conflict == null } ?: return@locked null
            val created = CreateInvite(ctx)(s, label, now())
            // the invite wrap goes to the relays only (B§6: never over Bluetooth)
            apply(created.step, overBluetooth = false)
            created.link.link
        }

    /**
     * Allow: [ApproveResult.Allowed] only when [follower] is now an active follower, else why not, so the caller can
     * say it didn't work. When other phones also tried the invite (a leaked link) the wearer must type the code shown
     * on the follower's phone: [typedCode] must match.
     */
    suspend fun approve(follower: String, typedCode: String? = null): ApproveResult {
        if (owner?.conflict != null) return ApproveResult.Paused
        val request = pendingRequest(follower) ?: return ApproveResult.Gone
        if (request.needsTypedCode && typedCode != request.code) return ApproveResult.NeedsCode
        var refused = ApproveResult.Gone
        val s =
            locked {
                val st = owner ?: return@locked null
                // re-checked under the lock: a conflict, or another phone tried the link meanwhile
                val p = st.pending.firstOrNull { it.follower == follower }
                when {
                    st.conflict != null -> null.also { refused = ApproveResult.Paused }
                    p == null -> null
                    p.needsTypedCode && typedCode != p.code -> null.also { refused = ApproveResult.NeedsCode }
                    else -> ApproveRequest(ctx)(st, follower, now())?.also { apply(it) }?.state
                }
            } ?: return refused
        if (s.activeFollowers().none { it.key == follower }) return ApproveResult.Gone
        locked {
            publish(readings.republish(s, now()))
            owner?.let { apply(treatments.republish(it, now())) }
        }
        return ApproveResult.Allowed
    }

    /**
     * *Allow* / *Deny* from the access-request notification, answered through the sharing that runs: never started or
     * stopped here, so a notification can't turn the wearer's sharing on or off, whatever the process state (#167: the
     * answer used to start the engine itself and stop it when done, which in a process the tap had just started could
     * stop the sharing the role had started meanwhile). In such a process it first waits, at most [waitMs], for the
     * role's start (AppGraph's `followRole`) to load the identity. False when it didn't happen: sharing doesn't run on
     * this phone (not the wearer's, no identity), the request isn't waiting any more, it needs the app (a code to type
     * or compare), sharing is paused, the invite expired.
     */
    suspend fun answerFromNotification(follower: String, allow: Boolean, waitMs: Long): Boolean {
        if (!awaitStarted(waitMs)) return false
        val request = pendingRequest(follower) ?: return false
        if (!allow) return deny(follower) != null
        return request.allowFromNotification && approve(follower) == ApproveResult.Allowed
    }

    /**
     * Whether sharing runs, once [start] has settled: a new process's engine stays [OwnerShareState.Loading] until the
     * role's start has read the identity (or for good on a phone that isn't the wearer's: false after [waitMs]).
     * Starts nothing itself.
     */
    private suspend fun awaitStarted(waitMs: Long): Boolean {
        withTimeoutOrNull(waitMs) { state.first { it != OwnerShareState.Loading } }
        return running
    }

    /** The request [follower] has waiting, if any. */
    fun pendingRequest(follower: String): PendingRequest? = owner?.pending?.firstOrNull { it.follower == follower }

    /** "Can add entries" for an active follower (writer scope); refused during a conflict (it publishes a roster). */
    suspend fun setWriter(follower: String, allowed: Boolean) = act { SetWriter(ctx)(it, follower, allowed, now()) }

    suspend fun deny(follower: String) = act { DenyRequest(ctx)(it, follower, now()) }

    suspend fun remove(follower: String) =
        act { RemoveFollower(ctx)(it, follower, now()) }?.let { s ->
            locked { publish(readings.republish(s, now())) }
        }

    /**
     * "Forget this device" (Settings › Sharing › Nearby devices): the follower is removed (its trust ends, like Remove)
     * and its Bluetooth links drop. A device that connects again is no longer named or trusted.
     */
    suspend fun forget(follower: String) {
        remove(follower)
        nearby.forget(follower)
    }

    /** The Bluetooth server's state (Settings › Sharing). */
    val nearbyStatus: StateFlow<NearbyStatus> get() = nearby.status

    /** Bluetooth turned on or Nearby devices was allowed: the server looks again. */
    fun refreshNearby() = nearby.refresh()

    /** Settings › Advanced's *Reconnect now*: relays that dropped connect at once (their subscriptions follow). */
    fun reconnectNow() = transport.reconnectNow()

    /** Connected follower devices named by their identity proof. */
    val nearbyLinks: StateFlow<List<NearbyServerCore.Named>> get() = nearby.links

    /**
     * "I turned it off — retry": first reads what the relays hold signed by our identity (the other app's newest roster
     * and backup), so a rotation goes above the other app's epoch.
     */
    suspend fun retryConflict() =
        locked {
            val s = owner?.takeIf { it.conflict != null } ?: return@locked
            val identity = s.identity(ctx.crypto)
            val found =
                if (policy.on) {
                    transport
                        .fetch(
                            RestoreIdentity(ctx).identityEventsFilter(identity),
                            FETCH_TIMEOUT_MS,
                        ).events
                } else {
                    emptyList()
                }
            owner?.let { apply(conflicts.retry(it, found, now())) }
        }

    /** A fired or cleared alert, to every follower at once. Continues during a conflict (readings do too). */
    suspend fun publishAlert(alert: AlertPayload) = locked { owner?.let { send(readings.publishAlert(it, alert, now())) } }

    /** Runs a follower change; refused during a conflict (it would publish a backup / rotation). */
    private suspend fun act(use: (OwnerState) -> Step?): OwnerState? =
        locked {
            val s = owner?.takeIf { it.conflict == null } ?: return@locked null
            val step = use(s) ?: return@locked null
            apply(step)
            step.state
        }

    // ---------------------------------------------------------------------------------------------- plumbing

    /** Persist first, then publish: a crash in between re-sends the saved control events on the next start. */
    private suspend fun apply(step: Step, overBluetooth: Boolean = true) {
        val before = owner
        commit(step.state)
        step.publish.forEach { send(it, overBluetooth) }
        // a new epoch (rotation, conflict retry): the log and the settings again, encrypted to the new read key
        if (before != null && before.current.n != step.state.current.n) republishShared()
        if (before != null && before.activeWriters() != step.state.activeWriters()) subscribeWriters(step.state)
    }

    private suspend fun commit(s: OwnerState) {
        val changed = s != owner
        owner = s
        if (changed) store.save(s)
        render()
    }

    private fun publish(events: List<SignedEvent>) = events.forEach(::send)

    /** Readings' live events and hour chunks, counted for Settings › Advanced (counts only, nothing personal). */
    private fun publishReadings(
        events: List<SignedEvent>,
        overBluetooth: Boolean = true,
    ) {
        if (events.isEmpty()) return
        events.forEach { send(it, overBluetooth) }
        _sent.update { it.plus(events) }
    }

    /**
     * To the relays (unless [policy] keeps it off them: internet sharing off, or a live event while the upload interval
     * batches) and, unless it's the owner backup (B§6) or [overBluetooth] is false, to nearby followers.
     */
    private fun send(ev: SignedEvent, overBluetooth: Boolean = true) {
        if (policy.toRelays(ev)) transport.publish(ev)
        val identity = identityPub
        val backup = ev.kind == Wire.KIND_DATA && (identity == null || ev.tag("d") == Wire.backupD(PublicKeyHex(identity)))
        if (overBluetooth && !backup) nearby.publish(ev)
    }

    private fun render() {
        val s = owner ?: return
        nearby.refresh()
        val relays = transport.status.value
        _state.value = s.conflict?.let {
            OwnerShareState.AnotherAppSharingAsYou(it.reason, IdentityConflictDetector.followersReceiving(it.reason), relays)
        } ?: OwnerShareState.Sharing(
            s.current.n,
            s.followers,
            s.pending,
            relays,
            s.backupPublishedAt,
            s.recoveryExportedAt,
            profile = ControlEvents.profile(s),
            writers = s.activeWriters().toSet(),
            passwordManagerSavedAt = s.passwordManagerSavedAt,
            restoredAt = s.restoredAt,
            nearbyPaired = s.nearbyPaired,
            invites = s.invites,
        )
    }

    private companion object {
        const val INBOX = "inbox"
        const val OWN = "own"
        const val WRITERS = "writers"
        const val NEARBY_VIA = "ble"
        const val SETTINGS_DEBOUNCE_MS = 3_000L
        const val CONTROL_REFRESH = 6 * Wire.DAY
        const val HISTORY_MIN_GAP = 600L
        const val FETCH_TIMEOUT_MS = 12_000L

        /** Backup › Replace this identity waits this long for a relay to accept every follower's notice. */
        const val NOTICE_TIMEOUT_MS = 15_000L
        const val NPUB_HEAD = 12
        const val NPUB_TAIL = 4
    }
}
