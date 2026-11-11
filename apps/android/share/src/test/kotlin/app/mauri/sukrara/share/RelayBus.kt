// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share

import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.share.transport.FetchResult
import app.mauri.sukrara.share.transport.RelayFilter
import app.mauri.sukrara.share.transport.RelayInbound
import app.mauri.sukrara.share.transport.RelayStatus
import app.mauri.sukrara.share.transport.RelayTransport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One in-memory relay shared by several engines (the wearer's and followers'): what any connection publishes is stored
 * and delivered at once to every open subscription it matches, on every connection, as a real relay does. Never opens
 * a socket. Each [link] is one engine's connection.
 */
class RelayBus {
    val stored = mutableListOf<SignedEvent>()
    private val links = mutableListOf<Link>()

    fun link(): Link = Link().also { links += it }

    inner class Link : RelayTransport {
        private val _inbound = MutableSharedFlow<RelayInbound>(extraBufferCapacity = 1024)
        override val inbound: SharedFlow<RelayInbound> = _inbound
        override val status: StateFlow<Map<String, RelayStatus>> = MutableStateFlow(mapOf("wss://$HOST" to RelayStatus(connected = true)))
        val subscriptions = mutableMapOf<String, List<RelayFilter>>()
        var started = false
            private set

        override fun start() {
            started = true
        }

        override fun stop() {
            started = false
            subscriptions.clear()
        }

        override fun subscribe(id: String, filters: List<RelayFilter>) {
            subscriptions[id] = filters
            stored.filter { e -> filters.any { it.matches(e) } }.forEach { _inbound.tryEmit(RelayInbound.Event(it, id, HOST)) }
            _inbound.tryEmit(RelayInbound.EndOfStored(id, HOST))
        }

        override fun close(id: String) {
            subscriptions.remove(id)
        }

        override fun publish(event: SignedEvent) {
            if (stored.none { it.id == event.id }) stored += event
            _inbound.tryEmit(RelayInbound.Ok(event.id, true, HOST))
            links.filter { it.started }.forEach { it.deliver(event) }
        }

        override fun reconnectNow() = Unit

        override suspend fun fetch(filters: List<RelayFilter>, timeoutMs: Long): FetchResult =
            FetchResult(stored.filter { e -> filters.any { it.matches(e) } }, 1)

        private fun deliver(event: SignedEvent) {
            for ((id, filters) in subscriptions.toMap()) {
                if (filters.any { it.matches(event) }) _inbound.tryEmit(RelayInbound.Event(event, id, HOST))
            }
        }
    }

    private companion object {
        const val HOST = "bus.relay"
    }
}
