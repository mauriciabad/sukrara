// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.follower

import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.protocol.UnsignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.follow.LeaveOutbox
import app.mauri.sukrara.protocol.follow.PendingLeave
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.transport.RelayAccess
import app.mauri.sukrara.share.transport.RelayPool
import app.mauri.sukrara.share.transport.RelaySocket
import app.mauri.sukrara.share.transport.SocketEvent
import app.mauri.sukrara.share.transport.WebSocketFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The leave sender over the real [RelayPool] and scripted relay sockets (PROTOCOL-v2 "Leave" and "Relay answers"):
 * what a relay answers in NIP-01 `OK` frames, as it says it, decides when the leave is told and done. No network.
 */
class LeaveRelayAnswersTest {
    private class Relay(val url: String, private val onEvent: (SocketEvent) -> Unit) : RelaySocket {
        val sent = mutableListOf<String>()

        override fun send(text: String): Boolean = sent.add(text)

        override fun cancel() = Unit

        fun says(frame: String) = onEvent(SocketEvent.Message(frame))
    }

    private val sockets = mutableListOf<Relay>()
    private val factory = WebSocketFactory { url, onEvent -> Relay(url, onEvent).also { sockets += it } }
    private val clock = FakeClock(Instant.ofEpochSecond(NOW))
    private val store = InMemoryStateStore(LeaveOutbox())
    private val wrap = crypto.sign(crypto.generate(), UnsignedEvent(Wire.KIND_WRAP, "sealed", listOf(listOf("p", "ab".repeat(32))), NOW))
    private val id = wrap.id

    private fun TestScope.sender(access: RelayAccess = RelayAccess.Public) =
        LeaveSender(
            store,
            {
                urls,
                auth,
                ->
                RelayPool(urls, auth, crypto, factory, StandardTestDispatcher(testScheduler), { NOW }, jitter = { 1.0 }, access = access)
            },
            crypto,
            clock,
            backgroundScope,
            relaysFor = access::connectTo,
        )

    private suspend fun leaving(relays: List<String>) =
        store.update { LeaveOutbox(listOf(PendingLeave(wrap.toJson(), id, relays, NOW + Wire.TTL_LEAVE, "Ana"))) }

    private suspend fun leave() =
        store.data
            .first()
            .leaves
            .singleOrNull()

    /** The newest socket the pool opened for [url]. */
    private fun relay(url: String) = sockets.last { it.url == url }

    private fun Relay.got(eventId: String) = sent.count { it.startsWith("[\"EVENT\"") && it.contains(eventId) }

    @Test fun aRelayKeepsItWithOrWithoutADuplicateMessageWhateverTheUrlsSpelling() =
        runTest {
            leaving(listOf("wss://a.example:4848/", "wss://B.example", "wss://c.example/inbox"))
            val sending = async { sender().flush() }
            runCurrent()
            assertEquals(listOf("wss://a.example:4848/", "wss://B.example", "wss://c.example/inbox"), sockets.map { it.url })
            sockets.forEach { assertEquals(it.url, 1, it.got(id)) }
            relay("wss://a.example:4848/").says("""["OK","$id",true,""]""")
            runCurrent()
            assertTrue("one relay keeps it: told", leave()!!.told)
            assertFalse("one isn't enough", sending.isCompleted)
            // a resend after a reconnect: the relay already has it, as NIP-01's example says it
            relay("wss://B.example").says("""["OK","$id",true,"duplicate: already have this event"]""")
            runCurrent()
            assertTrue("two relays keep it: done, the connection closes", sending.await())
            assertEquals(null, leave())
        }

    /**
     * The emulator walk of #213: a debug build's test relay stands in for the stream's relays. Before, its answer was
     * matched to none of them, so the leave stayed *hasn't been told yet* and went again every attempt.
     */
    @Test fun aDebugBuildsTestRelayStandsInForTheStreamsRelays() =
        runTest {
            leaving(Wire.DEFAULT_RELAYS)
            val sending = async { sender(RelayAccess.Local(listOf("ws://10.0.2.2:7447"))).flush() }
            runCurrent()
            val local = relay("ws://10.0.2.2:7447")
            assertEquals(1, sockets.size)
            local.says("""["OK","$id",true,"duplicate: already have this event"]""")
            runCurrent()
            assertTrue("the only relay it goes to keeps it: done", sending.await())
            assertEquals(null, leave())
        }

    @Test fun refusalsOtherEventsAndAnythingButABooleanDontCount() =
        runTest {
            leaving(listOf("wss://a.example", "wss://b.example"))
            val sending = async { sender().flush() }
            runCurrent()
            val a = relay("wss://a.example")
            val b = relay("wss://b.example")
            a.says("""["OK","$id",false,"blocked: you are banned from posting here"]""")
            a.says("""["OK","$id",false,"rate-limited: slow down there chief","1000"]""")
            b.says("""["OK","$id","true",""]""")
            b.says("""["OK","${"cd".repeat(32)}",true,""]""")
            b.says("""["OK","$id"]""")
            b.says("""["NOTICE","$id"]""")
            runCurrent()
            assertFalse(leave()!!.told)
            advanceTimeBy(LeaveSender.WAIT_MS + 1)
            assertFalse("still to send: it waits for the next attempt", sending.await())
            assertEquals(listOf("wss://a.example", "wss://b.example"), leave()!!.waitingOn())
        }

    /** NIP-20: a relay that already has the event says `true`; some say `false` with `duplicate:`. Both keep it. */
    @Test fun aDuplicateAnsweredFalseStillKeepsIt() =
        runTest {
            leaving(listOf("wss://a.example", "wss://b.example"))
            val sending = async { sender().flush() }
            runCurrent()
            relay("wss://a.example").says("""["OK","$id",false,"duplicate: already have this event"]""")
            relay("wss://b.example").says("""["OK","$id",true]""")
            runCurrent()
            assertTrue(sending.await())
        }

    /** NIP-42 on a write: AUTH with the throwaway key (never the follower's), then the leave goes again and counts. */
    @Test fun aRelayAskingForAuthGetsAThrowawayKeysAndTheLeaveAgain() =
        runTest {
            leaving(listOf("wss://a.example"))
            val sending = async { sender().flush() }
            runCurrent()
            val a = relay("wss://a.example")
            a.says("""["AUTH","challenge-1"]""")
            a.says("""["OK","$id",false,"auth-required: we only accept events from registered users"]""")
            runCurrent()
            val auth = a.sent.single { it.startsWith("[\"AUTH\"") }
            val authEvent = Json.parseToJsonElement(auth).jsonArray[1].jsonObject
            assertEquals(
                22242,
                authEvent
                    .getValue("kind")
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertTrue(auth.contains("challenge-1"))
            assertFalse(leave()!!.told)
            a.says("""["OK","${authEvent.getValue("id").jsonPrimitive.content}",true,""]""")
            runCurrent()
            assertEquals("sent again once authenticated", 2, a.got(id))
            a.says("""["OK","$id",true,""]""")
            runCurrent()
            assertTrue(sending.await())
        }
}
