// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.ClockBehind
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.LinkUpdate
import app.mauri.sukrara.core.model.NotMonitoringReason
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SensorAction
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.feature.home.Samples.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class NoDataReasonTest {
    private fun reason(state: GlucoseState) = NoDataReason.of(state, homeLook(state, NOW, GlucoseUnit.MGDL))

    @Test fun `a fresh value has no reason`() = assertNull(reason(Samples.wearer()))

    @Test fun `a stale value under 20 min is still shown (struck), no reason`() = assertNull(reason(Samples.wearer(ageMin = 6)))

    @Test fun `no role yet asks to set up`() = assertEquals(NoDataReason.SetupIncomplete, reason(GlucoseState.Initial))

    @Test fun `before the first reading ever it is not monitoring, never a no-readings alarm state`() {
        val state = Samples.noSensorYet()
        assertEquals(NotMonitoringReason.NoSensorYet, state.notMonitoring) // the hub arms no data alarm in this state
        assertEquals(NoDataReason.NoSensorYet(follower = false), reason(state))
        assertEquals(HomeAction.PairSensor, reason(state)?.action)
    }

    @Test fun `a follower that never got a reading is asked to join`() {
        val r = reason(Samples.noSensorYet(SourceSpec.TheirSukrara))
        assertEquals(NoDataReason.NoSensorYet(follower = true), r)
        assertEquals(HomeAction.OpenSetup, r?.action)
    }

    @Test fun `a forgotten sensor is not monitoring, not a sensor that will reconnect`() {
        val state = Samples.wearer(ageMin = 25).copy(monitoringStartedAt = null)
        val r = reason(state)
        assertEquals(NoDataReason.NoSensorYet(follower = false, history = true), r)
        assertEquals(HomeAction.PairSensor, r?.action)
        assertFalse(r!!.fullPage) // the chart keeps the readings from before
    }

    @Test fun `25 min without readings names the last one`() {
        val state = Samples.wearer(ageMin = 25)
        assertEquals(NoDataReason.NoReadings(state.latest!!.t), reason(state))
    }

    @Test fun `Juggluco as the source says Juggluco's reason and opens Source, never the sensor's`() {
        val juggluco = SourceSpec(Person.Yours, SourceKind.PhoneApp)
        val state = Samples.wearer(ageMin = 25).copy(spec = juggluco)
        val look = homeLook(state, NOW, GlucoseUnit.MGDL)
        // not installed: says so, with Source (choose another, or install it)
        val missing = NoDataReason.of(state, look, PhoneAppProblem.Missing)
        assertEquals(NoDataReason.PhoneAppSilent(PhoneAppProblem.Missing, state.latest!!.t), missing)
        assertEquals(HomeAction.OpenSource, missing?.action)
        assertEquals(
            NoDataReason.PhoneAppSilent(PhoneAppProblem.Unreachable, state.latest!!.t),
            NoDataReason.of(state, look, PhoneAppProblem.Unreachable),
        )
        // nothing known: Juggluco sent nothing new
        assertEquals(NoDataReason.PhoneAppSilent(null, state.latest!!.t), reason(state))
        // never a reading yet, Juggluco missing: not "no sensor yet · Pair sensor"
        val never = Samples.noSensorYet(juggluco)
        assertEquals(
            NoDataReason.PhoneAppSilent(PhoneAppProblem.Missing, null),
            NoDataReason.of(never, homeLook(never, NOW, GlucoseUnit.MGDL), PhoneAppProblem.Missing),
        )
    }

    @Test fun `a follower that can't read the wearer's version names the app to update, the value still shown`() {
        val fresh = Samples.wearer(ageMin = 2).copy(spec = SourceSpec.TheirSukrara)
        val newer = reason(fresh.copy(link = LinkState("Ana", NOW, update = LinkUpdate.ThisApp)))
        assertEquals(NoDataReason.UpdateToFollow(LinkUpdate.ThisApp, "Ana"), newer)
        assertEquals(HomeAction.UpdateSukrara, newer?.action)
        val older = reason(fresh.copy(link = LinkState("Ana", NOW, update = LinkUpdate.Wearer)))
        assertEquals(NoDataReason.UpdateToFollow(LinkUpdate.Wearer, "Ana"), older)
        assertEquals(null, older?.action)
        // once the versions match: nothing while the value shows
        assertEquals(null, reason(fresh.copy(link = LinkState("Ana", NOW))))
    }

    @Test fun `follower silence names when the wearer was last heard`() {
        val heard = NOW.minus(Duration.ofMinutes(30))
        val state = Samples.wearer(ageMin = 30).copy(spec = SourceSpec.TheirSukrara, link = LinkState("Ana", heard))
        assertEquals(NoDataReason.WearerSilent(heard), reason(state))
    }

    @Test fun `bluetooth off on a wearer with no value asks to turn it on`() {
        val r = reason(Samples.wearer(ageMin = 25, sensor = SensorState.BluetoothOff))
        assertEquals(NoDataReason.BluetoothOff, r)
        assertEquals(HomeAction.EnableBluetooth, r?.action)
    }

    @Test fun `a fresh value wins over bluetooth just turned off`() = assertNull(reason(Samples.wearer(sensor = SensorState.BluetoothOff)))

    @Test fun `a sensor another app took over says how to take it back`() {
        val r = reason(Samples.wearer(ageMin = 25, sensor = SensorState.ActionNeeded(SensorAction.TakeOver)))
        assertEquals(NoDataReason.UsedByAnotherApp, r)
        assertEquals(HomeAction.PairSensor, r?.action)
        assertFalse(r!!.fullPage) // the chart keeps the readings from before
    }

    @Test fun `an unusable pairing asks for a scan of the sensor`() {
        val r = reason(Samples.wearer(ageMin = 25, sensor = SensorState.ActionNeeded(SensorAction.ScanSensor)))
        assertEquals(NoDataReason.ScanNeeded, r)
        assertEquals(HomeAction.PairSensor, r?.action)
    }

    @Test fun `a refused pairing of a scanned family asks to pair again, not to scan`() {
        val r = reason(Samples.wearer(ageMin = 25, sensor = SensorState.ActionNeeded(SensorAction.PairAgain)))
        assertEquals(NoDataReason.PairAgain, r)
        assertEquals(HomeAction.PairSensor, r?.action)
    }

    @Test fun `a fresh value wins over a sensor that needs the person`() =
        assertNull(reason(Samples.wearer(sensor = SensorState.ActionNeeded(SensorAction.TakeOver))))

    @Test fun `a stopped service comes before what the sensor needs`() {
        val taken = SensorState.ActionNeeded(SensorAction.TakeOver)
        val stopped = Samples.wearer(ageMin = 25, sensor = taken).copy(service = ServiceState.Stopped(StopReason.StartDenied))
        assertEquals(NoDataReason.MonitoringStopped, reason(stopped))
    }

    @Test fun `the permission and the sign-in keep the usual reason (their own places say them)`() {
        for (a in listOf(SensorAction.GrantBluetooth, SensorAction.SignInLibreView)) {
            val state = Samples.wearer(ageMin = 25, sensor = SensorState.ActionNeeded(a))
            assertEquals(a.name, NoDataReason.NoReadings(state.latest!!.t), reason(state))
        }
    }

    @Test fun `a phone clock behind the readings says so even while the last value shows`() {
        val ahead = Duration.ofMinutes(12)
        for (state in listOf(Samples.wearer(), Samples.wearer(ageMin = 25), Samples.wearer().copy(spec = SourceSpec.TheirSukrara))) {
            val r = reason(state.copy(clockBehind = ClockBehind(NOW + ahead, ahead)))
            assertEquals(NoDataReason.ClockBehind(ahead), r)
            assertEquals(HomeAction.OpenDateSettings, r?.action)
            assertFalse(r!!.fullPage)
        }
    }

    @Test fun `an ended sensor comes before the clock`() {
        val state = Samples.wearer(sensor = SensorState.Ended).copy(clockBehind = ClockBehind(NOW, Duration.ofMinutes(9)))
        assertEquals(NoDataReason.SensorEnded(), reason(state))
    }

    @Test fun `ended and warming up replace any number`() {
        assertEquals(NoDataReason.SensorEnded(), reason(Samples.wearer(sensor = SensorState.Ended)))
        val ready = NOW.plus(Duration.ofMinutes(40))
        assertEquals(NoDataReason.WarmingUp(ready), reason(Samples.wearer(ageMin = 25, sensor = SensorState.WarmingUp(ready))))
    }

    @Test fun `native core missing has no action but says why`() {
        val r = reason(Samples.wearer(sensor = SensorState.CoreMissing))
        assertEquals(NoDataReason.CoreMissing, r)
        assertNull(r?.action)
    }

    @Test fun `monitoring stopped by Android offers resume`() {
        val state = Samples.wearer(ageMin = 25).copy(service = ServiceState.Stopped(StopReason.StartDenied))
        assertEquals(NoDataReason.MonitoringStopped, reason(state))
    }

    @Test fun `a revoked follower is told so`() {
        val state = Samples.wearer().copy(spec = SourceSpec.TheirSukrara, link = LinkState("Ana", NOW, revoked = true))
        assertEquals(NoDataReason.LinkRevoked, reason(state))
    }

    @Test fun `an ended follow never speaks over another source's values (Appendix G finding 15)`() {
        val ended = LinkState("Ana", NOW, revoked = true)
        val llu = Samples.wearer().copy(spec = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp), link = ended)
        assertNull(reason(llu))
        val nightscout = Samples.wearer(ageMin = 25).copy(spec = SourceSpec(Person.Yours, SourceKind.Nightscout), link = ended)
        assertFalse(reason(nightscout) == NoDataReason.LinkRevoked)
    }

    @Test fun `only nothing-to-draw reasons take the card's place, the rest sit under the card and its chart`() {
        assertTrue(reason(GlucoseState.Initial)!!.fullPage)
        assertTrue(reason(Samples.noSensorYet())!!.fullPage)
        assertTrue(reason(Samples.wearer(sensor = SensorState.CoreMissing))!!.fullPage)
        val underCard =
            listOf(
                Samples.wearer(ageMin = 25),
                Samples.wearer(sensor = SensorState.Ended),
                Samples.wearer(ageMin = 25, sensor = SensorState.WarmingUp(NOW.plus(Duration.ofMinutes(40)))),
                Samples
                    .wearer(
                        ageMin = 30,
                    ).copy(spec = SourceSpec.TheirSukrara, link = LinkState("Ana", NOW.minus(Duration.ofMinutes(30)))),
                Samples.wearer(ageMin = 25).copy(service = ServiceState.Stopped(StopReason.StartDenied)),
            )
        for (state in underCard) assertFalse("$state", reason(state)!!.fullPage)
    }

    @Test fun `the damaged-data report names the store kind and error type only`() {
        val text = report(DataProblem(DamagedStore.Keys, "identity.owner", "AEADBadTagException", "identity.owner.bin.corrupt-1", NOW))
        assertTrue(text.contains("store: keys:identity.owner"))
        assertTrue(text.contains("reason: AEADBadTagException"))
    }
}
