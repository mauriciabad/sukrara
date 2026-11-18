// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Link
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.feature.sharing.LeaveAnswers
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.share.follower.FollowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Leaving the Sukrara link (one person, one link: link.json `leave`, docs/44 Appendix G): what it asks, then the
 * settings, the link left through the one call, the history kept behind the fence (the wearer's entries read-only,
 * this phone's own unsent entries its own) or deleted.
 */
class AppLinkTest {
    private val t0 = Instant.parse("2026-11-18T08:00:00Z")
    private val events = mutableListOf<String>()
    private val follow =
        MutableStateFlow<FollowState>(FollowState.Following(FollowStatus.Active, Profile("Ana", null), "0420", true, t0, emptyMap()))
    private val running = MutableStateFlow<SourceSpec?>(SourceSpec.TheirSukrara)
    private val readings = InMemoryReadingsRepository()
    private val treatments = InMemoryTreatmentsRepository()
    private var theirs = true to Link.Theirs.Differ
    private var fence: Instant? = null
    private var keyStoreRefuses = false

    private val settings =
        object : LeaveSettings {
            override suspend fun state() = theirs

            override suspend fun keepTheirs() {
                events += "settings:theirs"
            }

            override suspend fun defaults() {
                events += "settings:defaults"
            }

            override suspend fun forNextLink() {
                events += "settingsFrom:wearer"
            }
        }

    private val link =
        AppLink(
            follow,
            running,
            readings,
            treatments,
            settings,
            setFence = { fence = it },
            leaveLink = { keep ->
                events += "leave:$keep"
                !keyStoreRefuses
            },
            afterLeave = { next -> events += "after:${next?.kind?.id}" },
        )

    private suspend fun received() {
        readings.upsert(listOf(Reading(t0, 100, null, SourceId.Relay), Reading(t0.plusSeconds(600), 110, null, SourceId.Relay)))
        // the wearer's entry, and one this phone added that never reached the wearer
        treatments.replace(record("ana", via = "wss://relay", delivery = TreatmentDelivery.Delivered, creator = "wearer"))
        treatments.replace(record("mine", via = null, delivery = TreatmentDelivery.Pending, creator = "me"))
    }

    private fun record(
        id: String,
        via: String?,
        delivery: TreatmentDelivery,
        creator: String,
    ) = TreatmentRecord(
        Treatment(id, TreatmentKind.Carbs, 25.0, t0, modifiedAt = t0, origin = TreatmentOrigin.Follower),
        creator = creator,
        author = creator,
        via = via,
        delivery = delivery,
    )

    @Test
    fun `what leaving asks, per link json`() =
        runTest {
            assertNull("nothing received: no history question", link.facts(null)!!.history)
            received()
            val facts = link.facts(null)!!
            assertEquals("Ana", facts.name)
            assertTrue(facts.told)
            assertFalse(facts.yours)
            assertEquals(listOf(Link.Question.Settings, Link.Question.History), facts.plan.questions)
            // whose glucose changes with the leave: the history goes, asked as that
            assertEquals(
                listOf(Link.Question.Settings, Link.Question.DeleteHistory),
                link.facts(SourceSpec(Person.Yours, SourceKind.LibreLinkUp))!!.plan.questions,
            )
            // an ended follow tells nobody
            follow.value = FollowState.Following(FollowStatus.Revoked, Profile("Ana", null), "0420", false, t0, emptyMap())
            assertFalse(link.facts(null)!!.told)
            // not linked: nothing to leave
            running.value = SourceSpec(Person.Theirs, SourceKind.Nightscout)
            assertNull(link.facts(null))
        }

    @Test
    fun `Keep it keeps the history behind the fence, the wearer's entries read-only and this phone's own its own`() =
        runTest {
            received()
            assertTrue(link.leave(LeaveAnswers(keepSettings = true, keepHistory = true), SourceSpec(Person.Theirs, SourceKind.LibreLinkUp)))
            assertEquals(listOf("settings:theirs", "settingsFrom:wearer", "leave:true", "after:libreLinkUp"), events)
            assertEquals(2, readings.all.size)
            assertEquals(t0.plusSeconds(600), fence)
            val ana = treatments.get("ana")!!
            assertTrue("the wearer's entry is kept read-only, never sent", ana.fromLink)
            val mine = treatments.get("mine")!!
            assertEquals("never stuck Sending… without a link", TreatmentDelivery.Local, mine.delivery)
            assertNull(mine.creator)
            assertFalse(mine.fromLink)
        }

    @Test
    fun `Delete it and Sukrara's defaults`() =
        runTest {
            received()
            fence = t0
            assertTrue(link.leave(LeaveAnswers(keepSettings = false, keepHistory = false), null))
            assertEquals(listOf("settings:defaults", "settingsFrom:wearer", "leave:false", "after:null"), events)
            assertTrue(readings.all.isEmpty())
            assertNull(treatments.get("ana"))
            assertNull(fence)
        }

    @Test
    fun `settings asked nowhere keep what link json says, and a refused key store leaves the history alone`() =
        runTest {
            received()
            // This phone's own: nothing asked, they stay
            theirs = false to Link.Theirs.Differ
            keyStoreRefuses = true
            assertFalse(link.leave(LeaveAnswers(keepSettings = false), null))
            assertEquals(listOf("settingsFrom:wearer", "leave:true"), events)
            assertFalse(treatments.get("ana")!!.fromLink)
            assertNull(fence)
            // theirs are the defaults: the defaults, unasked
            events.clear()
            theirs = true to Link.Theirs.Defaults
            keyStoreRefuses = false
            assertTrue(link.leave(LeaveAnswers(), null))
            assertEquals("settings:defaults", events.first())
        }

    @Test
    fun `a follow found behind another source is asked about while live, left at start when it ended`() =
        runTest {
            val llu = SourceSpec(Person.Yours, SourceKind.LibreLinkUp)
            // live: asked (Keep following?), never left unasked; ended: left; the link itself or no record: nothing
            assertEquals(Link.Behind.KeepFollowing, AppLink.behind(llu, recorded = true, ended = false))
            assertEquals(Link.Behind.Leave, AppLink.behind(llu, recorded = true, ended = true))
            assertNull(AppLink.behind(SourceSpec.TheirSukrara, recorded = true, ended = false))
            assertNull(AppLink.behind(llu, recorded = false, ended = false))
            assertNull(AppLink.behind(null, recorded = true, ended = false))

            // the question: whom, told, and nothing about settings or history (kept: it lies among the source's readings)
            received()
            running.value = llu
            val facts = link.behindFacts(llu)!!
            assertEquals("Ana", facts.name)
            assertTrue(facts.told)
            assertTrue(facts.yours)
            assertEquals(emptyList<Link.Question>(), facts.plan.questions)
            assertEquals(Link.Kept.Own, facts.plan.result)
            assertNull(link.behindFacts(SourceSpec.TheirSukrara))

            // Stop following: left (the wearer told), the history kept, the settings untouched, no after-leave reset
            assertTrue(link.leaveBehind())
            assertEquals(listOf("leave:true"), events)
            assertEquals(2, readings.all.size)
            assertTrue(treatments.get("ana")!!.fromLink)
            keyStoreRefuses = true
            assertFalse(link.leaveBehind())
        }
}
