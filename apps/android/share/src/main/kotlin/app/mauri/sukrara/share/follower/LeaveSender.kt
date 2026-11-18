// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.follower

import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.protocol.NostrCrypto
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.follow.LeaveOutbox
import app.mauri.sukrara.protocol.follow.PendingLeave
import app.mauri.sukrara.share.transport.NetworkWatch
import app.mauri.sukrara.share.transport.RelayInbound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Carries the follower's `leave` to the wearer until the wearer can't miss it (PROTOCOL-v2 "Leave"): the outbox
 * ([LeaveOutbox], saved as is) is sent to every relay that hasn't accepted it, at once after *Stop following*, at each
 * start, whenever the network comes back and from [scheduleRetry]'s job (Android: WorkManager, which waits for a
 * network also while the app is closed), until [LeaveOutbox.NEEDED] relays keep it or it expired. Its own short-lived
 * relay connection: the follow it belonged to is gone (and with it the follower key: a relay asking for AUTH gets a
 * throwaway key's).
 */
class LeaveSender(
    private val store: StateStore<LeaveOutbox>,
    private val transports: RelayTransportFactory,
    private val crypto: NostrCrypto,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val network: NetworkWatch = NetworkWatch.None,
    /** Asks the system to call [flush] again once a network is up, also with the app closed; nothing in tests. */
    private val scheduleRetry: () -> Unit = {},
) {
    private val lock = Mutex()

    @Volatile private var watch: AutoCloseable? = null

    /** What is still on its way (the untold notification follows it). */
    val outbox: Flow<LeaveOutbox> get() = store.data

    /** Saves [leave] before anything else happens to the follow (a crash after the key is deleted can't lose it). */
    suspend fun add(leave: PendingLeave) = store.update { o -> LeaveOutbox(o.leaves.filter { it.eventId != leave.eventId } + leave) }

    /** The follow didn't end after all (the key couldn't be deleted): its leave never goes. */
    suspend fun drop(eventId: String) = store.update { o -> LeaveOutbox(o.leaves.filter { it.eventId != eventId }) }

    /** The wearer's phone took [eventId] over Bluetooth (the person is told it's done; the relays still get it). */
    suspend fun tookNearby(eventId: String) = store.update { it.update(eventId) { l -> l.copy(nearby = true) } }

    /** Tries now, in the background. */
    fun kick() {
        scope.launch { runCatching { flush() }.onFailure { if (it is CancellationException) throw it } }
    }

    /**
     * One attempt: every leave still waiting goes to the relays that haven't accepted it, and their answers are taken
     * for up to [waitMs]. True when nothing is left to send; otherwise the network watch and [scheduleRetry] bring it
     * back. One at a time.
     */
    suspend fun flush(waitMs: Long = WAIT_MS): Boolean =
        lock.withLock {
            store.update { it.pruned(now()) }
            val waiting = store.data.first().leaves
            if (waiting.isEmpty()) return@withLock finished()
            val events = waiting.mapNotNull { l -> SignedEvent.parse(l.wrapJson) }
            val ids = events.mapTo(HashSet()) { it.id }
            val urls = waiting.flatMap { it.waitingOn() }.distinct()
            val throwaway = crypto.generate()
            val relays = transports.create(urls) { throwaway }
            try {
                coroutineScope {
                    val answers =
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            relays.inbound.collect { m ->
                                if (m is RelayInbound.Ok && m.accepted && m.eventId in ids) {
                                    store.update { it.update(m.eventId) { l -> l.accept(m.relay) } }
                                    // every leave of this attempt is kept: stop listening (ends this child only)
                                    if (store.data
                                            .first()
                                            .pruned(now())
                                            .leaves
                                            .none { it.eventId in ids }
                                    ) {
                                        throw Done()
                                    }
                                }
                            }
                        }
                    relays.start()
                    events.forEach(relays::publish)
                    withTimeoutOrNull(waitMs) { answers.join() }
                    answers.cancel()
                }
            } finally {
                relays.stop()
            }
            store.update { it.pruned(now()) }
            if (store.data
                    .first()
                    .leaves
                    .isEmpty()
            ) {
                finished()
            } else {
                waitForNetwork()
            }
        }

    /** At start: anything a previous run couldn't send goes now. */
    suspend fun resume() {
        if (store.data
                .first()
                .leaves
                .isNotEmpty()
        ) {
            kick()
        }
    }

    private fun finished(): Boolean {
        watch?.close()
        watch = null
        return true
    }

    private fun waitForNetwork(): Boolean {
        if (watch == null) watch = network.watch(::kick)
        scheduleRetry()
        return false
    }

    private fun now() = clock.now().epochSecond

    private class Done : CancellationException("leave delivered")

    companion object {
        /** How long one attempt waits for the relays' answers. */
        const val WAIT_MS = 20_000L
    }
}
