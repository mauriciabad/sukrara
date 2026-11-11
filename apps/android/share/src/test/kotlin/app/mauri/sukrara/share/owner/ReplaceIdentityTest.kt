// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.SecretKey
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.event.RumorCodec
import app.mauri.sukrara.share.FakeHub
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.ble.FakeNearbyServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Backup › Replace this identity tells the followers first (#161), on contracts/protocol/sync/replace-identity.json
 * (`replace`; its follower `steps` are replayed by `SyncFixturesTest`, `FollowerSyncFixtureTest` and the Mac): every
 * active follower gets a `revoked` notice sealed by the old identity, nothing of the new identity goes out before a
 * relay accepted every notice or the wait timed out, a notice no relay took goes out again with the new identity, and
 * Cancel keeps the identity and sends each follower told the key again, newer than the notice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplaceIdentityTest {
    private val fixture = Json.parseToJsonElement(Contracts.file("protocol/sync/replace-identity.json").readText()).jsonObject
    private val keys =
        fixture.getValue("keys").jsonObject.mapValues { (_, k) ->
            KeyPair(SecretKey.ofHex(k.str("sec"))!!, PublicKeyHex(k.str("pub")))
        }
    private val order = fixture.getValue("replace").jsonObject
    private val followers = order.getValue("followers").jsonArray.map { it.jsonObject }
    private val active = followers.filter { it["removed"]?.jsonPrimitive?.booleanOrNull != true }.map { it.str("f") }
    private val oldIdentity = keys.getValue("I")

    /** The wearer before the replace: the fixture's identity, phone and epoch, its followers (one removed earlier). */
    private fun wearer() =
        OwnerState(
            identityHex = keys.getValue("I").secret.revealHex(),
            deviceHex = keys.getValue("D").secret.revealHex(),
            sid = fixture.str("sid"),
            epochs = listOf(EpochKey(1, keys.getValue("G1").secret.revealHex())),
            followers =
                followers.map { f ->
                    val removed = f["removed"]?.jsonPrimitive?.booleanOrNull == true
                    val key = keys.getValue(f.str("f")).public.hex
                    FollowerRecord(key, f.str("f"), NOW - 1000, revoked = if (removed) NOW - 500 else null)
                },
            rosterPublishedAt = NOW,
            backupPublishedAt = NOW,
        )

    @Test fun everyRunTellsTheFollowersFirstThenReplacesOrKeeps() {
        order.getValue("runs").jsonArray.forEach { run(it.jsonObject) }
    }

    private fun run(r: JsonObject) =
        runTest {
            val name = r.str("name")
            val relay = FakeRelayTransport()
            val nearby = FakeNearbyServer()
            val store = OwnerIdentityStore(InMemorySecureStore())
            store.save(wearer())
            val engine =
                OwnerEngine(
                    OwnerFixtures.context(),
                    store,
                    relay,
                    FakeHub(),
                    SeenWraps.None,
                    InMemoryTreatmentsRepository(),
                    FakeClock(Instant.ofEpochSecond(NOW)),
                    StandardTestDispatcher(testScheduler),
                    nearby = nearby,
                    relayPolicy = MutableStateFlow(RelayPolicy(on = r.getValue("internet").jsonPrimitive.boolean)),
                )
            engine.start(backgroundScope)
            runCurrent()
            val start = currentTime
            val sentBefore = nearby.published.size
            val replaced = async { engine.createIdentity(backgroundScope, Profile("Ana", null), replaceExisting = true) }
            runCurrent()

            // every active follower is told, by the old identity, over Bluetooth and (internet on) the relays
            val sent = nearby.published.drop(sentBefore).distinctBy { it.id }
            val notices = active.associateWith { f -> sent.single { revokedFor(f, it) } }
            assertEquals("$name: no notice to a removed follower", active.size, sent.count { opens(it) is Rumor.Revoked })
            if (r.getValue("internet").jsonPrimitive.boolean) assertTrue(name, notices.values.all { it in relay.published })

            val acks = r.getValue("acks").jsonObject.mapValues { it.value.jsonPrimitive.long * 1000 }
            val cancelAt = r.ms("cancelAt")
            val replacedAt = r.ms("replacedAt")
            val ackSteps = acks.map { (f, at) -> at to suspend { relay.ok(notices.getValue(f).id) } }
            val cancelStep = listOfNotNull(cancelAt?.let { it to suspend { engine.cancelReplace() } })
            val timeline = (ackSteps + cancelStep).sortedBy { it.first }
            for ((at, act) in timeline) {
                at(start + at)
                assertFalse("$name: replaced before ${at / 1000} s", replaced.isCompleted)
                act()
                runCurrent()
            }
            if (replacedAt != null) {
                if (start + replacedAt > currentTime) {
                    at(start + replacedAt - 1)
                    assertFalse("$name: replaced before ${replacedAt / 1000} s", replaced.isCompleted)
                    at(start + replacedAt)
                }
                assertTrue("$name: replaced at ${replacedAt / 1000} s", replaced.isCompleted && replaced.await())
                val now = (store.load() as OwnerIdentityStore.Load.Loaded).state
                val newIdentity = now.identity(crypto).public.hex
                assertFalse(name, newIdentity == oldIdentity.public.hex)
                // the order: every notice before the first event of the new identity
                val firstNew = nearby.published.indexOfFirst { it.pubkey == newIdentity }
                assertTrue(name, firstNew > notices.values.maxOf { nearby.published.indexOf(it) })
                // the notices no relay took go out again with the new identity until one does
                val resent =
                    r
                        .getValue("resent")
                        .jsonArray
                        .map { it.jsonPrimitive.content }
                        .toSet()
                val carried =
                    now.pendingControl
                        .filter { it.key.startsWith("replaced:") }
                        .map { it.eventId }
                        .toSet()
                assertEquals(name, resent.map { notices.getValue(it).id }.toSet(), carried)
                if (r.getValue("internet").jsonPrimitive.boolean) {
                    resent.forEach { f ->
                        val id = notices.getValue(f).id
                        assertEquals("$name: $f's notice again", 2, relay.published.count { it.id == id })
                    }
                }
                assertNull(engine.telling.value)
            } else {
                runCurrent()
                assertTrue("$name: kept", replaced.isCompleted && !replaced.await())
                val kept = (store.load() as OwnerIdentityStore.Load.Loaded).state
                assertEquals(name, oldIdentity.public.hex, kept.identity(crypto).public.hex)
                assertNull(name, kept.replacing)
                // each follower told gets the key again, newer than its notice: it follows again
                r.getValue("keyAgain").jsonArray.map { it.jsonPrimitive.content }.forEach { f ->
                    val noticeAt = crypto.unwrap(keys.getValue(f).secret, notices.getValue(f))!!.createdAt
                    val key =
                        nearby.published.mapNotNull { crypto.unwrap(keys.getValue(f).secret, it) }.singleOrNull {
                            RumorCodec.decode(it.kind, it.content) is Rumor.KeyDelivery
                        }
                    assertNotNull("$name: $f gets the key again", key)
                    assertTrue("$name: $f's key is newer than the notice", key!!.createdAt > noticeAt)
                }
                assertNull(engine.telling.value)
            }
            engine.stop()
        }

    /** The app closed while telling the followers: the identity was never replaced, so they get the key again. */
    @Test fun aReplaceCutShortGivesTheFollowersTheKeyAgainOnStart() =
        runTest {
            val relay = FakeRelayTransport()
            val store = OwnerIdentityStore(InMemorySecureStore())
            val told = active.map { keys.getValue(it).public.hex }
            store.save(wearer().copy(replacing = ReplaceRecord(NOW, told)))
            val engine =
                OwnerEngine(
                    OwnerFixtures.context(),
                    store,
                    relay,
                    FakeHub(),
                    SeenWraps.None,
                    InMemoryTreatmentsRepository(),
                    FakeClock(Instant.ofEpochSecond(NOW)),
                    StandardTestDispatcher(testScheduler),
                )
            engine.start(backgroundScope)
            runCurrent()
            val again =
                active.map { f ->
                    relay.published.mapNotNull { crypto.unwrap(keys.getValue(f).secret, it) }.single {
                        RumorCodec.decode(it.kind, it.content) is Rumor.KeyDelivery
                    }
                }
            assertTrue(again.all { it.createdAt > NOW })
            assertNull((store.load() as OwnerIdentityStore.Load.Loaded).state.replacing)
            engine.stop()
        }

    /** Telling shows how many notices a relay took; no one to tell (no followers) replaces at once. */
    @Test fun progressCountsTheNoticesARelayTookAndNoFollowersMeansNoWait() =
        runTest {
            val relay = FakeRelayTransport()
            val store = OwnerIdentityStore(InMemorySecureStore())
            store.save(wearer())
            val engine =
                OwnerEngine(
                    OwnerFixtures.context(),
                    store,
                    relay,
                    FakeHub(),
                    SeenWraps.None,
                    InMemoryTreatmentsRepository(),
                    FakeClock(Instant.ofEpochSecond(NOW)),
                    StandardTestDispatcher(testScheduler),
                )
            engine.start(backgroundScope)
            runCurrent()
            val replaced = async { engine.createIdentity(backgroundScope, Profile("Ana", null), replaceExisting = true) }
            runCurrent()
            assertEquals(TellingFollowers(active.size, 0), engine.telling.value)
            val first = relay.published.first { revokedFor(active.first(), it) }
            relay.ok(first.id)
            runCurrent()
            assertEquals(TellingFollowers(active.size, 1), engine.telling.value)
            // a second replace while one runs changes nothing
            assertFalse(engine.createIdentity(backgroundScope, Profile("Bea", null), replaceExisting = true))
            relay.published.filter { e -> active.drop(1).any { revokedFor(it, e) } }.forEach { relay.ok(it.id) }
            runCurrent()
            assertTrue(replaced.await())
            assertNull(engine.telling.value)

            // the new identity has no followers: the next replace doesn't wait
            val again = async { engine.createIdentity(backgroundScope, Profile("Ana", null), replaceExisting = true) }
            runCurrent()
            assertTrue(again.isCompleted && again.await())
            assertNull(engine.telling.value)
            engine.stop()
        }

    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - currentTime)
        runCurrent()
    }

    private fun opens(ev: SignedEvent): Rumor? =
        followers.map { keys.getValue(it.str("f")) }.firstNotNullOfOrNull { k ->
            crypto
                .unwrap(k.secret, ev)
                ?.takeIf { it.kind == Wire.KIND_TO_FOLLOWER && it.sender == oldIdentity.public }
                ?.let { RumorCodec.decode(it.kind, it.content) }
        }

    private fun revokedFor(f: String, ev: SignedEvent): Boolean {
        val u = crypto.unwrap(keys.getValue(f).secret, ev) ?: return false
        if (u.kind != Wire.KIND_TO_FOLLOWER || u.sender != oldIdentity.public) return false
        return RumorCodec.decode(u.kind, u.content) == Rumor.Revoked
    }

    private fun JsonElement.str(k: String) = jsonObject.getValue(k).jsonPrimitive.content

    /** A time in the fixture (s after the notices went out) in ms; null for `null`. */
    private fun JsonObject.ms(k: String) =
        this[k]
            ?.takeIf { it != JsonNull }
            ?.jsonPrimitive
            ?.long
            ?.times(1000)

    private companion object {
        const val NOW = 1_792_087_200L
    }
}
