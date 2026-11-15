// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Alerts
import app.mauri.sukrara.feature.sharing.setup.SetupStep.History
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Identity
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Notification
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Pens
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Permissions
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Ready
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Root
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Sensor
import app.mauri.sukrara.feature.sharing.setup.SetupStep.Updates
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

    /** The essentials answered: permissions done, sensor and alerts skipped. */
    private fun essentials(): SetupRecord = m.skip(m.skip(m.completePermissions(wearer()), Sensor), Alerts)

    @Test
    fun `the exact step order - essentials, Ready, then the optional steps`() {
        assertEquals(listOf(Welcome, SetupStep.Role, Permissions, Sensor, Alerts, Ready, Notification, Identity, History), m.steps(facts))
        // every part of the GitHub version (root features) on a phone with NFC
        val all = facts.copy(updates = UpdateChoice.Open, pens = false, root = false)
        assertEquals(
            listOf(Welcome, SetupStep.Role, Permissions, Sensor, Alerts, Ready, Updates, Root, Notification, Identity, Pens, History),
            m.steps(all),
        )
        assertTrue(listOf(Welcome, SetupStep.Role, SetupStep.Join, Permissions, Sensor, Alerts).all { it.essential })
        assertTrue(listOf(Updates, Root, Notification, Identity, Pens, History).all { it.extra && !it.essential })
    }

    @Test
    fun `step N of M counts within its part`() {
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        assertEquals(3 to 5, m.place(Permissions, all))
        assertEquals(5 to 5, m.place(Alerts, all))
        assertNull(m.place(Ready, all))
        assertEquals(1 to 5, m.place(Updates, all))
        assertEquals(5 to 5, m.place(History, all))
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
        // a follower's glucose notification is theirs too
        assertEquals(listOf(Welcome, SetupStep.Role, SetupStep.Join, Permissions, Alerts, Ready, Notification), m.steps(follower))
        assertEquals(
            listOf(Welcome, SetupStep.Role, SetupStep.Join, Permissions, Alerts, Ready, Updates, Root, Notification),
            m.steps(follower.copy(updates = UpdateChoice.Open, pens = false, root = false)),
        )
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
    fun `after the essentials Ready - Set up the rest walks the optional steps`() {
        var r = essentials()
        assertEquals(Ready, m.next(r, facts))
        assertEquals(listOf(Notification, Identity, History), m.extrasLeft(r, facts))
        r = m.continueExtras(r)
        assertEquals(Notification, m.next(r, facts))
        // Back from the first optional step shows Ready again
        assertEquals(Ready, m.previous(Notification, facts))
    }

    @Test
    fun `Later on Ready ends the walk - the optional steps wait on Settings' Finish setup`() {
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        val later = m.skip(essentials(), Ready)
        assertNull(m.next(later, all))
        assertEquals(listOf(Updates, Notification, Identity, Pens, History), m.extrasLeft(later, all))
        // home never names an optional step (home stays about glucose): only the skipped essentials
        assertEquals(SetupCard.Finish(FinishItem.AlertsNotConfirmed), m.card(later, all))
        // Finish setup in Settings: the walk goes on into them
        val resumed = m.continueExtras(later)
        assertEquals(Updates, m.next(resumed, all))
        assertTrue(Ready in resumed.completed && Ready !in resumed.skipped)
    }

    @Test
    fun `an optional step left with Later stays on Finish setup, a final answer leaves it`() {
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        var r = m.continueExtras(essentials())
        r = m.skip(r, Updates) // Not now: final
        r = m.completeNotification(r)
        r = m.skip(r, Identity) // Later
        r = m.skip(r, Pens) // Later
        r = m.skip(r, History) // Start empty: final
        assertNull(m.next(r, all))
        assertEquals(listOf(Identity, Pens), m.extrasLeft(r, all))
        // Finish setup again: the ones put off come back, the final ones don't
        assertEquals(Identity, m.next(m.continueExtras(r), all))
        // answered elsewhere: an identity exists, pen reading answered in Settings
        assertEquals(emptyList<SetupStep>(), m.extrasLeft(r, all.copy(hasIdentity = true, pens = true)))
    }

    @Test
    fun `the pens step - wearer with NFC only, done when reading is answered anywhere`() {
        assertTrue(Pens !in m.steps(facts))
        assertTrue(Pens !in m.steps(facts.copy(role = Role.Follower, pens = false)))
        val nfc = facts.copy(pens = false)
        val r = m.completeIdentity(m.completeNotification(m.continueExtras(essentials())), nfc.copy(hasIdentity = true))
        val shared = nfc.copy(hasIdentity = true)
        assertEquals(Pens, m.next(r, shared))
        assertEquals(History, m.next(m.completePens(r), shared))
        assertEquals(History, m.next(r, shared.copy(pens = true)))
        assertTrue(m.answers(Pens, m::completePens))
    }

    @Test
    fun `a follower on Google Play - the essentials, then only the glucose notification`() {
        val follower = none.copy(role = Role.Follower, following = true)
        val r = m.confirmAlerts(m.completePermissions(m.completeJoin(wearer(), follower)))
        assertEquals(Ready, m.next(r, follower))
        assertEquals(listOf(Notification), m.extrasLeft(r, follower))
        assertNull(m.next(m.completeNotification(m.continueExtras(r)), follower))
    }

    @Test
    fun `identity completes only once an identity exists`() {
        val r = m.completeNotification(m.continueExtras(essentials()))
        assertEquals(Identity, m.next(m.completeIdentity(r, facts), facts))
        val shared = facts.copy(hasIdentity = true)
        assertEquals(History, m.next(m.completeIdentity(r, shared), shared))
    }

    @Test
    fun `skipped essentials become Finish-setup items and drop out when their fact changes`() {
        var r = wearer()
        for (s in listOf(Permissions, Sensor, Alerts, Ready)) r = m.skip(r, s)
        assertNull(m.next(r, facts))
        assertEquals(FinishItem.entries, m.finishItems(r, facts))
        val later = facts.copy(ownSensorLive = true, hasIdentity = true)
        assertEquals(listOf(FinishItem.AllowAlerts, FinishItem.AlertsNotConfirmed), m.finishItems(r, later))
    }

    @Test
    fun `the Finish-setup walk shows each skipped essential once, in item order, then home`() {
        var r = wearer()
        for (s in listOf(Permissions, Sensor, Alerts, Ready)) r = m.skip(r, s)
        assertEquals(Permissions, m.nextItem(r, facts, emptySet()))
        assertEquals(Alerts, m.nextItem(r, facts, setOf(Permissions)))
        // Later on a step shown alone keeps it skipped (on the card) and moves on, never back to it
        r = m.skip(r, Alerts)
        assertEquals(Sensor, m.nextItem(r, facts, setOf(Permissions, Alerts)))
        assertNull(m.nextItem(r, facts, setOf(Permissions, Alerts, Sensor)))
        // a step done meanwhile drops out by itself
        assertNull(m.nextItem(m.confirmAlerts(r), facts.copy(ownSensorLive = true), setOf(Permissions)))
    }

    @Test
    fun `leaving the walk half-way loses nothing - home's banner resumes it at the essential left`() {
        // Back on the step after the role, before answering anything
        var r = wearer()
        assertEquals(SetupCard.Resume(Permissions), m.card(r, facts))
        r = m.completePermissions(r)
        assertEquals(SetupCard.Resume(Sensor), m.card(r, facts))
        // a skipped step stays skipped; the walk resumes after it
        r = m.skip(r, Sensor)
        assertEquals(SetupCard.Resume(Alerts), m.card(r, facts))
        r = m.skip(r, Alerts)
        // the essentials are over (the walk waits at Ready): the first skipped one that still matters
        assertEquals(SetupCard.Finish(FinishItem.AlertsNotConfirmed), m.card(r, facts))
        assertNull(m.card(r, facts.copy(ownSensorLive = true, alertsConfirmed = true, alertsReady = true)))
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
        assertEquals(Alerts, m.previous(Ready, facts))
        assertEquals(Identity, m.previous(History, facts))
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
        val shared = facts.copy(ownSensorLive = true, hasIdentity = true, alertsConfirmed = true)
        val walked = m.completeNotification(m.continueExtras(m.completeSensor(m.completePermissions(wearer()), shared)))
        val done = m.completeIdentity(walked, shared)
        assertEquals(History, m.next(done, shared))
        assertTrue(m.historyDue(done, shared))
        // either answer is final, and it never becomes a Finish-setup item
        for (answered in listOf(m.completeHistory(done), m.skip(done, History))) {
            assertNull(m.next(answered, shared))
            assertTrue(m.finishItems(answered, shared).isEmpty())
            assertTrue(m.extrasLeft(answered, shared).isEmpty())
            assertTrue(!m.historyDue(answered, shared))
            // a replaced database offers it again
            assertTrue(m.historyDue(m.reopen(answered, History), shared))
        }
        // never to a follower (their history is the wearer's) or before a role
        assertTrue(History !in m.steps(facts.copy(role = Role.Follower)))
        assertTrue(!m.historyDue(done, shared.copy(role = Role.Follower)) && !m.historyDue(done, shared.copy(role = null)))
    }

    @Test
    fun `history is never offered on its own while other steps are open`() {
        // the walk left on the step after the role: the banner resumes it there, history comes last in the walk
        assertTrue(!m.historyDue(wearer(), facts))
        // the walk waits at Ready, or the person put the optional steps off with other ones still open
        assertTrue(!m.historyDue(essentials(), facts))
        assertTrue(!m.historyDue(m.skip(essentials(), Ready), facts))
        // everything else answered: offered on its own at launch
        assertTrue(!m.historyDue(m.skip(essentials(), Ready), facts.copy(hasIdentity = true)))
        assertTrue(m.historyDue(m.completeNotification(m.skip(essentials(), Ready)), facts.copy(hasIdentity = true)))
    }

    @Test
    fun `thresholds confirmed in Settings complete the alert step and its Finish-setup item`() {
        val confirmed = facts.copy(alertsConfirmed = true)
        val r = m.skip(m.skip(m.completePermissions(wearer()), Sensor), Alerts)
        assertTrue(FinishItem.AlertsNotConfirmed !in m.finishItems(r, confirmed))
        assertEquals(Ready, m.next(m.reopen(r, Alerts), confirmed))
    }

    @Test
    fun `no Finish-setup items or optional steps before a role`() {
        assertEquals(emptyList<FinishItem>(), m.finishItems(SetupRecord(skipped = setOf(Alerts)), none))
        assertEquals(emptyList<SetupStep>(), m.extrasLeft(SetupRecord(skipped = setOf(Alerts)), none))
    }

    private val github = facts.copy(updates = UpdateChoice.Open)

    @Test
    fun `the GitHub version asks about updates first among the optional steps, both roles - never Google Play`() {
        assertEquals(
            listOf(Welcome, SetupStep.Role, Permissions, Sensor, Alerts, Ready, Updates, Notification, Identity, History),
            m.steps(github),
        )
        // no updater (the Google Play build): no step, whatever was recorded
        assertTrue(Updates !in m.steps(facts) && Updates !in m.steps(facts.copy(role = Role.Follower)))
    }

    @Test
    fun `Turn on or Not now answer the updates step and the walk goes on`() {
        val r = m.continueExtras(essentials())
        assertEquals(Updates, m.next(r, github))
        assertEquals(Notification, m.next(m.completeUpdates(r), github))
        assertEquals(Notification, m.next(m.skip(r, Updates), github))
        // answered elsewhere first (home's banner, Settings › About): on, or off on purpose
        assertEquals(Notification, m.next(r, github.copy(updates = UpdateChoice.On)))
        assertEquals(Notification, m.next(r, github.copy(updates = UpdateChoice.Off)))
        // Back from the next step shows it again (its answer kept)
        assertEquals(Updates, m.previous(Notification, github))
        assertTrue(m.answers(Updates, m::completeUpdates))
    }

    @Test
    fun `an install that finished setup before Ready and the pens step existed is not sent back`() {
        val before = listOf(Sensor, Alerts, Identity, History).fold(m.completePermissions(wearer())) { r, s -> m.skip(r, s) }
        // Ready was passed (a later step answered): no walk, no banner for it
        assertNull(m.next(before, github))
        // the new pens step waits on Settings' Finish setup, with the followers put off back then
        assertEquals(listOf(Identity, Pens), m.extrasLeft(before, github.copy(pens = false)))
        // a follower's walk: the alert step after it was answered
        val follower = github.copy(role = Role.Follower, following = true)
        val followed = m.skip(m.completePermissions(m.completeJoin(wearer(), follower)), Alerts)
        assertEquals(Ready, m.next(followed, follower))
    }

    private val rooted = facts.copy(root = false)

    @Test
    fun `the root step - builds with root features only, before the glucose notification`() {
        assertTrue(Root !in m.steps(facts) && Root !in m.steps(facts.copy(role = Role.Follower)))
        assertEquals(Notification, m.steps(rooted)[m.steps(rooted).indexOf(Root) + 1])
        val r = m.continueExtras(essentials())
        assertEquals(Root, m.next(r, rooted))
        // No: final, the walk goes on
        assertEquals(Notification, m.next(m.completeRoot(r), rooted))
        assertTrue(m.extrasLeft(m.completeRoot(r), rooted).none { it == Root })
        // Later: on Finish setup
        val later = m.skip(r, Root)
        assertEquals(Notification, m.next(later, rooted))
        assertTrue(Root in m.extrasLeft(m.skip(later, Ready), rooted))
        // Sukrara has root (here or in Settings): done by itself
        assertEquals(Notification, m.next(r, rooted.copy(root = true)))
        assertTrue(m.answers(Root, m::completeRoot))
        assertEquals(Root, m.previous(Notification, rooted))
    }

    @Test
    fun `the glucose notification step - Continue answers it, Later on Ready keeps it on Finish setup`() {
        val r = m.continueExtras(essentials())
        assertEquals(Notification, m.next(r, facts))
        assertEquals(Identity, m.next(m.completeNotification(r), facts))
        assertTrue(Notification in m.extrasLeft(m.skip(essentials(), Ready), facts))
        assertTrue(m.answers(Notification, m::completeNotification))
    }

    @Test
    fun `an install that finished setup before the root and notification steps existed is not sent back`() {
        val shared = rooted.copy(hasIdentity = true)
        val before = m.completeHistory(m.completeIdentity(m.continueExtras(essentials()), shared))
        assertNull(m.next(before, shared))
        assertTrue(m.extrasLeft(before, shared).isEmpty())
    }
}
