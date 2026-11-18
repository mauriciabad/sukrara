// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
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
    private val none = SetupFacts(spec = null, ownSensorLive = false, hasIdentity = false)
    private val facts = none.copy(spec = SourceSpec.YourSensor)
    private val m = SetupMachine

    private fun wearer(): SetupRecord = m.completeWelcome(SetupRecord())

    /** The essentials answered: permissions done, sensor and alerts skipped. */
    private fun essentials(): SetupRecord = m.skip(m.skip(m.completePermissions(wearer()), Sensor), Alerts)

    @Test
    fun `the exact step order - essentials, Ready, then the optional steps`() {
        assertEquals(
            listOf(Welcome, SetupStep.Person, SetupStep.Source, Permissions, Sensor, Alerts, Ready, Root, Notification, Identity, History),
            m.steps(facts),
        )
        // every part of the GitHub version on a phone with NFC
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        assertEquals(
            listOf(
                Welcome,
                SetupStep.Person,
                SetupStep.Source,
                Permissions,
                Sensor,
                Alerts,
                Ready,
                Updates,
                Root,
                Notification,
                Identity,
                Pens,
                History,
            ),
            m.steps(all),
        )
        assertTrue(listOf(Welcome, SetupStep.Person, SetupStep.Source, SetupStep.Join, Permissions, Sensor, Alerts).all { it.essential })
        assertTrue(listOf(Updates, Root, Notification, Identity, Pens, History).all { it.extra && !it.essential })
    }

    @Test
    fun `step N of M counts within its part`() {
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        assertEquals(4 to 6, m.place(Permissions, all))
        assertEquals(6 to 6, m.place(Alerts, all))
        assertNull(m.place(Ready, all))
        assertEquals(1 to 6, m.place(Updates, all))
        assertEquals(6 to 6, m.place(History, all))
    }

    @Test
    fun `walk resumes at the first step neither done nor skipped`() {
        var r = SetupRecord()
        assertEquals(Welcome, m.next(r, none))
        r = m.completeWelcome(r)
        assertEquals(SetupStep.Person, m.next(r, none))
        assertEquals(Permissions, m.next(r, facts))
        r = m.completePermissions(r)
        assertEquals(Sensor, m.next(r, facts))
        // after a process death the persisted record resumes at the same step
        val restored = Json.decodeFromString(SetupRecord.serializer(), Json.encodeToString(SetupRecord.serializer(), r))
        assertEquals(Sensor, m.next(restored, facts))
    }

    @Test
    fun `an install updated from before the question is asked whose glucose and where from once more`() {
        val updated = none.copy(running = SourceSpec.YourSensor)
        val r = m.completeWelcome(SetupRecord(completed = setOf(Permissions, Alerts)))
        assertEquals(SetupStep.Person, m.next(r, updated))
        assertEquals(SetupCard.Resume(SetupStep.Person), m.card(r, updated))
        // the walk is the one of what it reads: a wearer's
        assertTrue(SetupStep.Join !in m.steps(updated))
        assertEquals(Permissions, m.previous(Sensor, updated))
    }

    @Test
    fun `the source picker starts on what the phone reads, else the usual source of that person`() {
        assertEquals(SourceKind.Sensor, m.preselected(null, Person.Yours))
        assertEquals(SourceKind.Sukrara, m.preselected(null, Person.Theirs))
        val juggluco = SourceSpec(Person.Yours, SourceKind.PhoneApp)
        assertEquals(SourceKind.PhoneApp, m.preselected(juggluco, Person.Yours))
        assertEquals(SourceKind.Sukrara, m.preselected(juggluco, Person.Theirs))
    }

    @Test
    fun `someone else's glucose from Juggluco connects it, and nothing of it is shared, imported or read by pens`() {
        val theirs = none.copy(spec = SourceSpec(Person.Theirs, SourceKind.PhoneApp), pens = false)
        assertEquals(
            listOf(Welcome, SetupStep.Person, SetupStep.Source, Permissions, Sensor, Alerts, Ready, Root, Notification),
            m.steps(theirs),
        )
    }

    @Test
    fun `a stream joins before anything else and can't skip it`() {
        val follower = none.copy(spec = SourceSpec.TheirSukrara)
        // a follower's glucose notification is theirs too
        assertEquals(
            listOf(Welcome, SetupStep.Person, SetupStep.Source, SetupStep.Join, Permissions, Alerts, Ready, Root, Notification),
            m.steps(follower),
        )
        assertEquals(
            listOf(Welcome, SetupStep.Person, SetupStep.Source, SetupStep.Join, Permissions, Alerts, Ready, Updates, Root, Notification),
            m.steps(follower.copy(updates = UpdateChoice.Open, pens = false)),
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
    fun `a damaged setup file never asks for the source again`() {
        assertEquals(Permissions, m.next(SetupRecord(), facts))
    }

    @Test
    fun `welcome, whose glucose and the source can't be skipped`() {
        val r = SetupRecord()
        assertEquals(r, m.skip(r, Welcome))
        assertEquals(r, m.skip(r, SetupStep.Person))
        assertEquals(r, m.skip(r, SetupStep.Source))
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
        assertEquals(listOf(Root, Notification, Identity, History), m.extrasLeft(r, facts))
        r = m.continueExtras(r)
        assertEquals(Root, m.next(r, facts))
        // Back from the first optional step shows Ready again
        assertEquals(Ready, m.previous(Root, facts))
    }

    @Test
    fun `Later on Ready ends the walk - the optional steps wait on Settings' Finish setup`() {
        val all = facts.copy(updates = UpdateChoice.Open, pens = false)
        val later = m.skip(essentials(), Ready)
        assertNull(m.next(later, all))
        assertEquals(listOf(Updates, Root, Notification, Identity, Pens, History), m.extrasLeft(later, all))
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
        assertTrue(Pens !in m.steps(facts.copy(spec = SourceSpec.TheirSukrara, pens = false)))
        val nfc = facts.copy(pens = false)
        val r = m.completeIdentity(m.completeNotification(m.continueExtras(essentials())), nfc.copy(hasIdentity = true))
        val shared = nfc.copy(hasIdentity = true)
        assertEquals(Pens, m.next(r, shared))
        assertEquals(History, m.next(m.completePens(r), shared))
        assertEquals(History, m.next(r, shared.copy(pens = true)))
        assertTrue(m.answers(Pens, m::completePens))
    }

    @Test
    fun `a follower on Google Play - the essentials, then only root and the glucose notification`() {
        val follower = none.copy(spec = SourceSpec.TheirSukrara, following = true)
        val r = m.confirmAlerts(m.completePermissions(m.completeJoin(wearer(), follower)))
        assertEquals(Ready, m.next(r, follower))
        assertEquals(listOf(Root, Notification), m.extrasLeft(r, follower))
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
        assertNull(m.card(m.completeWelcome(SetupRecord()), none.copy(spec = SourceSpec.TheirSukrara)))
    }

    @Test
    fun `Back walks to the previous step, never into the role once chosen, and leaves on the first`() {
        assertNull(m.previous(Welcome, none))
        assertEquals(Welcome, m.previous(SetupStep.Person, none))
        assertEquals(SetupStep.Person, m.previous(SetupStep.Source, none))
        assertEquals(SetupStep.Person, m.previous(SetupStep.Source, facts))
        assertNull(m.previous(Permissions, facts))
        assertEquals(Permissions, m.previous(Sensor, facts))
        assertEquals(Alerts, m.previous(Ready, facts))
        assertEquals(Identity, m.previous(History, facts))
        // a stream can still go back to the source from the join step (another source after all)
        val follower = none.copy(spec = SourceSpec.TheirSukrara)
        assertEquals(SetupStep.Source, m.previous(SetupStep.Join, follower))
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
        assertTrue(History !in m.steps(facts.copy(spec = SourceSpec.TheirSukrara)))
        assertTrue(
            !m.historyDue(done, shared.copy(spec = SourceSpec.TheirSukrara)) &&
                !m.historyDue(done, shared.copy(spec = null, running = null)),
        )
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
            listOf(
                Welcome,
                SetupStep.Person,
                SetupStep.Source,
                Permissions,
                Sensor,
                Alerts,
                Ready,
                Updates,
                Root,
                Notification,
                Identity,
                History,
            ),
            m.steps(github),
        )
        // no updater (the Google Play build): no step, whatever was recorded
        assertTrue(Updates !in m.steps(facts) && Updates !in m.steps(facts.copy(spec = SourceSpec.TheirSukrara)))
    }

    @Test
    fun `Turn on or Not now answer the updates step and the walk goes on`() {
        val r = m.continueExtras(essentials())
        assertEquals(Updates, m.next(r, github))
        assertEquals(Root, m.next(m.completeUpdates(r), github))
        assertEquals(Root, m.next(m.skip(r, Updates), github))
        // answered elsewhere first (home's banner, Settings › About): on, or off on purpose
        assertEquals(Root, m.next(r, github.copy(updates = UpdateChoice.On)))
        assertEquals(Root, m.next(r, github.copy(updates = UpdateChoice.Off)))
        // Back from the next step shows it again (its answer kept)
        assertEquals(Updates, m.previous(Root, github))
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
        val follower = github.copy(spec = SourceSpec.TheirSukrara, following = true)
        val followed = m.skip(m.completePermissions(m.completeJoin(wearer(), follower)), Alerts)
        assertEquals(Ready, m.next(followed, follower))
    }

    @Test
    fun `the root step - every role, before the glucose notification`() {
        assertTrue(Root in m.steps(facts) && Root in m.steps(facts.copy(spec = SourceSpec.TheirSukrara)))
        assertEquals(Notification, m.steps(facts)[m.steps(facts).indexOf(Root) + 1])
        val r = m.continueExtras(essentials())
        assertEquals(Root, m.next(r, facts))
        // No: final, the walk goes on
        assertEquals(Notification, m.next(m.completeRoot(r), facts))
        assertTrue(m.extrasLeft(m.completeRoot(r), facts).none { it == Root })
        // Later: on Finish setup
        val later = m.skip(r, Root)
        assertEquals(Notification, m.next(later, facts))
        assertTrue(Root in m.extrasLeft(m.skip(later, Ready), facts))
        // Sukrara has root (here or in Settings): done by itself
        assertEquals(Notification, m.next(r, facts.copy(root = true)))
        assertTrue(m.answers(Root, m::completeRoot))
        assertEquals(Root, m.previous(Notification, facts))
    }

    @Test
    fun `the glucose notification step - Continue answers it, Later on Ready keeps it on Finish setup`() {
        val r = m.completeRoot(m.continueExtras(essentials()))
        assertEquals(Notification, m.next(r, facts))
        assertEquals(Identity, m.next(m.completeNotification(r), facts))
        assertTrue(Notification in m.extrasLeft(m.skip(essentials(), Ready), facts))
        assertTrue(m.answers(Notification, m::completeNotification))
    }

    @Test
    fun `an install that finished setup before the root and notification steps existed is not sent back`() {
        val shared = facts.copy(hasIdentity = true)
        val before = m.completeHistory(m.completeIdentity(m.continueExtras(essentials()), shared))
        assertNull(m.next(before, shared))
        assertTrue(m.extrasLeft(before, shared).isEmpty())
    }

    @Test
    fun `steps slide back only on the way back in the walk, never while finishing`() {
        assertTrue(m.goesBack(SetupStep.Source, SetupStep.Person, finishing = false))
        assertTrue(m.goesBack(Alerts, Sensor, finishing = false))
        assertEquals(false, m.goesBack(SetupStep.Person, SetupStep.Source, finishing = false))
        assertEquals(false, m.goesBack(Welcome, Welcome, finishing = false))
        // home's Finish setup shows Permissions, Alerts, then Sensor: each one comes next, never back
        assertEquals(false, m.goesBack(Alerts, Sensor, finishing = true))
    }
}
