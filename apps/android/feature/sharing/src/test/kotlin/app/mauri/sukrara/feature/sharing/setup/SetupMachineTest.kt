// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Alerts
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Identity
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Permissions
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Sensor
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Welcome
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupMachineTest {
    private val none = SetupFacts(role = null, ownSensorLive = false, hasIdentity = false)
    private val facts = none.copy(role = Role.Wearer)
    private val m = SetupMachine

    private fun wearer(): SetupRecord = m.completeWelcome(SetupRecord())

    @Test
    fun `the exact step order`() {
        assertEquals(listOf(Welcome, SetupStep.Role, Permissions, Sensor, Alerts, Identity, SetupStep.History), m.steps(facts))
    }

    @Test
    fun `walk resumes at the first step neither done nor skipped`() {
        var r = SetupRecord()
        assertEquals(Welcome, m.next(r, none))
        r = m.completeWelcome(r)
        assertEquals(SetupStep.Role, m.next(r, none))
        assertEquals(Result.Done(Role.Wearer), m.chooseRole(Role.Wearer, confirmed = true, none))
        assertEquals(Permissions, m.next(r, facts))
        r = m.completePermissions(r)
        assertEquals(Sensor, m.next(r, facts))
        // after a process death the persisted record resumes at the same step
        val restored = Json.decodeFromString(SetupRecord.serializer(), Json.encodeToString(SetupRecord.serializer(), r))
        assertEquals(Sensor, m.next(restored, facts))
    }

    @Test
    fun `role needs confirmation`() {
        val r = m.completeWelcome(SetupRecord())
        assertEquals(Result.Refused(RoleRefusal.NotConfirmed), m.chooseRole(Role.Wearer, confirmed = false, none))
        assertEquals(SetupStep.Role, m.next(r, none))
    }

    @Test
    fun `a follower joins before anything else and can't skip it`() {
        assertEquals(Result.Done(Role.Follower), m.chooseRole(Role.Follower, confirmed = true, none))
        val follower = none.copy(role = Role.Follower)
        assertEquals(listOf(Welcome, SetupStep.Role, SetupStep.Join, Permissions, Alerts), m.steps(follower))
        assertTrue(SetupStep.Join !in m.steps(facts))
        val start = m.completeWelcome(SetupRecord())
        assertEquals(SetupStep.Join, m.next(start, follower))
        assertEquals(start, m.skip(start, SetupStep.Join))
        // joining alone isn't enough: the waiting screen (its code) is left with Continue
        val joined = follower.copy(following = true)
        assertEquals(SetupStep.Join, m.next(start, joined))
        val done = m.completeJoin(start, joined)
        assertEquals(Permissions, m.next(done, joined))
        // a follow that ended (declined, removed) asks for a new link again
        assertEquals(SetupStep.Join, m.next(done, follower))
        assertEquals(start, m.completeJoin(start, follower))
    }

    @Test
    fun `a damaged setup file never asks for the role again`() {
        assertEquals(Permissions, m.next(SetupRecord(), facts))
    }

    @Test
    fun `welcome and role can't be skipped`() {
        val r = SetupRecord()
        assertEquals(r, m.skip(r, Welcome))
        assertEquals(r, m.skip(r, SetupStep.Role))
    }

    @Test
    fun `sensor completes only with a live own reading`() {
        val r = m.completePermissions(wearer())
        assertEquals(Sensor, m.next(m.completeSensor(r, facts), facts))
        val live = facts.copy(ownSensorLive = true)
        assertEquals(Alerts, m.next(m.completeSensor(r, live), live))
    }

    @Test
    fun `alert step completes only with Confirm`() {
        var r = m.skip(m.completePermissions(wearer()), Sensor)
        assertEquals(Alerts, m.next(r, facts))
        r = m.skip(r, Alerts)
        assertTrue(FinishItem.AlertsNotConfirmed in m.finishItems(r, facts))
        r = m.confirmAlerts(m.reopen(r, Alerts))
        assertTrue(FinishItem.AlertsNotConfirmed !in m.finishItems(r, facts))
    }

    @Test
    fun `identity completes only once an identity exists`() {
        val r = m.confirmAlerts(m.skip(m.completePermissions(wearer()), Sensor))
        assertEquals(Identity, m.next(m.completeIdentity(r, facts), facts))
        val shared = facts.copy(hasIdentity = true)
        assertEquals(SetupStep.History, m.next(m.completeIdentity(r, shared), shared))
    }

    @Test
    fun `skipped steps become Finish-setup items and drop out when their fact changes`() {
        var r = wearer()
        for (s in listOf(Permissions, Sensor, Alerts, Identity, SetupStep.History)) r = m.skip(r, s)
        assertNull(m.next(r, facts))
        assertEquals(FinishItem.entries, m.finishItems(r, facts))
        val later = facts.copy(ownSensorLive = true, hasIdentity = true)
        assertEquals(listOf(FinishItem.AllowAlerts, FinishItem.AlertsNotConfirmed), m.finishItems(r, later))
    }

    @Test
    fun `the Finish-setup walk shows each skipped step once, in item order, then home`() {
        var r = wearer()
        for (s in listOf(Permissions, Sensor, Alerts, Identity, SetupStep.History)) r = m.skip(r, s)
        assertEquals(Permissions, m.nextItem(r, facts, emptySet()))
        assertEquals(Alerts, m.nextItem(r, facts, setOf(Permissions)))
        // Later on a step shown alone keeps it skipped (on the card) and moves on, never back to it
        r = m.skip(r, Alerts)
        assertEquals(Sensor, m.nextItem(r, facts, setOf(Permissions, Alerts)))
        assertEquals(Identity, m.nextItem(r, facts, setOf(Permissions, Alerts, Sensor)))
        assertNull(m.nextItem(r, facts, setOf(Permissions, Alerts, Sensor, Identity)))
        // a step done meanwhile drops out by itself
        assertEquals(Identity, m.nextItem(m.confirmAlerts(r), facts.copy(ownSensorLive = true), setOf(Permissions)))
    }

    @Test
    fun `leaving the walk half-way loses nothing - home's banner resumes it at the step left`() {
        // Back on the step after the role, before answering anything
        var r = wearer()
        assertEquals(SetupCard.Resume(Permissions), m.card(r, facts))
        r = m.completePermissions(r)
        assertEquals(SetupCard.Resume(Sensor), m.card(r, facts))
        // a skipped step stays skipped; the walk resumes after it
        r = m.skip(r, Sensor)
        assertEquals(SetupCard.Resume(Alerts), m.card(r, facts))
        r = m.skip(m.skip(m.skip(r, Alerts), Identity), SetupStep.History)
        // the walk is over: the first skipped step that still matters
        assertEquals(SetupCard.Finish(FinishItem.AlertsNotConfirmed), m.card(r, facts))
        assertNull(m.card(r, facts.copy(ownSensorLive = true, hasIdentity = true, alertsConfirmed = true, alertsReady = true)))
        // before a role home says setup is incomplete (no banner); a follower's join is led to by home's own reason
        assertNull(m.card(SetupRecord(), none))
        assertNull(m.card(m.completeWelcome(SetupRecord()), none.copy(role = Role.Follower)))
    }

    @Test
    fun `Back walks to the previous step, never into the role once chosen, and leaves on the first`() {
        assertNull(m.previous(Welcome, none))
        assertEquals(Welcome, m.previous(SetupStep.Role, none))
        assertNull(m.previous(Permissions, facts))
        assertEquals(Permissions, m.previous(Sensor, facts))
        assertEquals(Identity, m.previous(SetupStep.History, facts))
        // a follower can still go back to the role from the join step (a phone that wears the sensor after all)
        val follower = none.copy(role = Role.Follower)
        assertEquals(SetupStep.Role, m.previous(SetupStep.Join, follower))
        assertEquals(SetupStep.Join, m.previous(Permissions, follower))
    }

    @Test
    fun `a step shown alone is over only when the action answers it`() {
        val live = facts.copy(ownSensorLive = true)
        assertTrue(m.answers(Permissions, m::completePermissions))
        assertTrue(m.answers(Sensor) { m.skip(it, Sensor) })
        assertTrue(!m.answers(Sensor) { m.completeSensor(it, facts) })
        assertTrue(m.answers(Sensor) { m.completeSensor(it, live) })
        assertTrue(m.answers(Alerts, m::confirmAlerts))
        assertTrue(!m.answers(Identity) { m.completeIdentity(it, facts) })
        assertTrue(m.answers(Identity) { m.completeIdentity(it, facts.copy(hasIdentity = true)) })
        assertTrue(!m.answers(SetupStep.Join) { m.skip(it, SetupStep.Join) })
        // another step's answer doesn't end this one
        assertTrue(!m.answers(Alerts, m::completePermissions))
    }

    @Test
    fun `bring your history back is offered once, to a wearer, and again after the database was replaced`() {
        val done =
            m.completeIdentity(
                m.confirmAlerts(m.completeSensor(m.completePermissions(wearer()), facts.copy(ownSensorLive = true))),
                facts.copy(hasIdentity = true),
            )
        val live = facts.copy(ownSensorLive = true, hasIdentity = true, alertsConfirmed = true)
        assertEquals(SetupStep.History, m.next(done, live))
        assertTrue(m.historyDue(done, live))
        // either answer is final, and it never becomes a Finish-setup item
        for (answered in listOf(m.completeHistory(done), m.skip(done, SetupStep.History))) {
            assertNull(m.next(answered, live))
            assertTrue(m.finishItems(answered, live).isEmpty())
            assertTrue(!m.historyDue(answered, live))
            // a replaced database offers it again
            assertTrue(m.historyDue(m.reopen(answered, SetupStep.History), live))
        }
        // never to a follower (their history is the wearer's) or before a role
        assertTrue(SetupStep.History !in m.steps(facts.copy(role = Role.Follower)))
        assertTrue(!m.historyDue(done, live.copy(role = Role.Follower)) && !m.historyDue(done, live.copy(role = null)))
    }

    @Test
    fun `history is never offered on its own while the walk stopped at an earlier step`() {
        // the walk left on the step after the role: the banner resumes it there, history comes last in the walk
        assertTrue(!m.historyDue(wearer(), facts))
        val skippedAll = listOf(Permissions, Sensor, Alerts, Identity).fold(wearer()) { r, s -> m.skip(r, s) }
        assertTrue(m.historyDue(skippedAll, facts))
    }

    @Test
    fun `thresholds confirmed in Settings complete the alert step and its Finish-setup item`() {
        val confirmed = facts.copy(alertsConfirmed = true)
        val r = m.skip(m.skip(m.completePermissions(wearer()), Sensor), Alerts)
        assertTrue(FinishItem.AlertsNotConfirmed !in m.finishItems(r, confirmed))
        assertEquals(Identity, m.next(m.reopen(r, Alerts), confirmed))
    }

    @Test
    fun `no Finish-setup items before a role`() {
        assertEquals(emptyList<FinishItem>(), m.finishItems(SetupRecord(skipped = setOf(Alerts)), none))
    }

    private val github = facts.copy(updates = UpdateChoice.Open)

    @Test
    fun `the GitHub version asks about updates right after the alert checklist, both roles - never Google Play`() {
        val u = SetupStep.Updates
        assertEquals(listOf(Welcome, SetupStep.Role, Permissions, u, Sensor, Alerts, Identity, SetupStep.History), m.steps(github))
        assertEquals(
            listOf(Welcome, SetupStep.Role, SetupStep.Join, Permissions, u, Alerts),
            m.steps(github.copy(role = Role.Follower)),
        )
        // no updater (the Google Play build): no step, whatever was recorded
        assertTrue(u !in m.steps(facts) && u !in m.steps(facts.copy(role = Role.Follower)))
        assertEquals(Sensor, m.next(m.completePermissions(wearer()), facts))
    }

    @Test
    fun `Turn on or Not now answer the updates step and the walk goes on`() {
        val r = m.completePermissions(wearer())
        assertEquals(SetupStep.Updates, m.next(r, github))
        assertEquals(Sensor, m.next(m.completeUpdates(r), github))
        assertEquals(Sensor, m.next(m.skip(r, SetupStep.Updates), github))
        // answered elsewhere first (home's banner, Settings › About): on, or off on purpose
        assertEquals(Sensor, m.next(r, github.copy(updates = UpdateChoice.On)))
        assertEquals(Sensor, m.next(r, github.copy(updates = UpdateChoice.Off)))
        // Back from the sensor step shows it again (its answer kept)
        assertEquals(SetupStep.Updates, m.previous(Sensor, github))
        assertTrue(m.answers(SetupStep.Updates, m::completeUpdates))
    }

    @Test
    fun `a walk left at the updates step resumes there, but skipping it is no Finish-setup item`() {
        val r = m.completePermissions(wearer())
        assertEquals(SetupCard.Resume(SetupStep.Updates), m.card(r, github))
        val skipped = listOf(SetupStep.Updates, Sensor, Alerts, Identity, SetupStep.History).fold(r) { x, s -> m.skip(x, s) }
        assertTrue(m.finishItems(skipped, github).none { it.step == SetupStep.Updates })
    }

    @Test
    fun `an install that finished setup before the step existed is not sent back - the banner asks instead`() {
        val before = listOf(Sensor, Alerts, Identity, SetupStep.History).fold(m.completePermissions(wearer())) { r, s -> m.skip(r, s) }
        assertNull(m.next(before, github))
        // history still offered on its own when it is the only step left
        val noHistory = listOf(Sensor, Alerts, Identity).fold(m.completePermissions(wearer())) { r, s -> m.skip(r, s) }
        assertTrue(m.historyDue(noHistory, github))
        // a follower's walk: the alert step after it was answered
        val follower = github.copy(role = Role.Follower, following = true)
        val followed = m.skip(m.completePermissions(m.completeJoin(wearer(), follower)), Alerts)
        assertNull(m.next(followed, follower))
    }
}
