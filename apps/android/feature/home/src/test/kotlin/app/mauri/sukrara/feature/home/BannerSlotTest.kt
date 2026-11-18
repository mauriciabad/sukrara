// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.BlockedFeature
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BannerMemory
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.feature.home.banners.HomeBanners
import app.mauri.sukrara.feature.home.banners.HomeEnvironment
import app.mauri.sukrara.feature.home.banners.SETUP_LATER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class BannerSlotTest {
    private val everything =
        HomeEnvironment(nextSetupStep = "Alerts", backedUp = false, widgetPlaced = false, clinicUploadSet = false)
    private val broken = AlertReadiness(setOf(AlertRequirement.ExactAlarm, AlertRequirement.Notifications))

    private fun pick(
        readiness: AlertReadiness? = AlertReadiness.Ready,
        env: HomeEnvironment = HomeEnvironment(),
        problems: Int = 0,
        memory: BannerMemory = BannerMemory(),
        at: java.time.Instant = NOW,
        stopped: Boolean = false,
    ) = HomeBanners.pick(readiness, env, stopped, problems, memory, at)

    @Test fun `nothing to say shows no banner`() = assertNull(pick())

    @Test fun `an unknown checklist claims nothing`() = assertNull(pick(readiness = null))

    @Test fun `alert reliability comes first and names the checklist's first problem`() {
        assertEquals(BannerKind.AlertsUnreliable, pick(broken, everything, problems = 2))
        assertEquals(AlertRequirement.Notifications, broken.firstProblem)
    }

    @Test fun `a failed alert step is a safety banner that can't be closed and names the step first`() {
        val failed = AlertReadiness(setOf(AlertRequirement.AlertDelivery, AlertRequirement.ExactAlarm), failedSteps = setOf("Alarm"))
        val closed = BannerMemory(mapOf(BannerKind.AlertsUnreliable.name to 1L))
        assertEquals(BannerKind.AlertsUnreliable, pick(failed, everything, memory = closed))
        assertEquals(AlertRequirement.AlertDelivery, failed.firstProblem)
        // not a setup item: Finish setup's permission step doesn't wait for it
        assertEquals(true, AlertReadiness(setOf(AlertRequirement.AlertDelivery)).setupReady)
    }

    @Test fun `monitoring stopped outranks everything, even with a value on screen`() =
        assertEquals(BannerKind.MonitoringStopped, pick(broken, everything, problems = 1, stopped = true))

    @Test fun `blocked pen taps, sensor taps or both show the one taps banner, never dismissed`() {
        listOf(BlockedTaps(pen = true), BlockedTaps(sensor = true), BlockedTaps(pen = true, sensor = true)).forEach { taps ->
            val env = everything.copy(tapsBlocked = taps)
            assertEquals(BannerKind.TapsBlocked, pick(env = env))
            // closing isn't offered, and a stored close (an older build) never hides it
            assertEquals(BannerKind.TapsBlocked, pick(env = env, memory = BannerMemory(mapOf(BannerKind.TapsBlocked.name to 1L))))
            // the safety banners still come first
            assertEquals(BannerKind.DataProblem, pick(env = env, problems = 1))
        }
        assertEquals(BannerKind.FinishSetup, pick(env = everything.copy(tapsBlocked = BlockedTaps())))
    }

    @Test fun `a blocked feature shows after the taps and before the tips, and only one without a switch closes`() {
        val sharing = everything.copy(blocked = listOf(BlockedFeature.BluetoothSharing, BlockedFeature.HealthConnect))
        assertEquals(BannerKind.FeatureBlocked, pick(env = sharing))
        assertEquals(BlockedFeature.BluetoothSharing, sharing.blockedShown(BannerMemory()))
        assertEquals(BannerKind.TapsBlocked, pick(env = sharing.copy(tapsBlocked = BlockedTaps(pen = true))))
        assertEquals(BannerKind.AlertsUnreliable, pick(broken, sharing))
        // Turn off is its way out: a stored close never hides it
        val closedSharing = BannerMemory(mapOf(HomeEnvironment.blockedKey(BlockedFeature.BluetoothSharing) to 1L))
        assertEquals(BannerKind.FeatureBlocked, pick(env = sharing, memory = closedSharing))
        // Bluetooth following has no switch: Not now closes it, for that feature only
        val following = everything.copy(blocked = listOf(BlockedFeature.BluetoothFollowing))
        assertEquals(BannerKind.FeatureBlocked, pick(env = following))
        val closed = BannerMemory().dismissKey(HomeEnvironment.blockedKey(BlockedFeature.BluetoothFollowing), NOW)
        assertEquals(BannerKind.FinishSetup, pick(env = following, memory = closed))
        assertEquals(
            BlockedFeature.HomeNetwork,
            following.copy(blocked = listOf(BlockedFeature.BluetoothFollowing, BlockedFeature.HomeNetwork)).blockedShown(closed),
        )
        // Show closed tips brings it back
        assertEquals(BannerKind.FeatureBlocked, pick(env = following, memory = closed.restoreAll()))
    }

    @Test fun `damaged data comes before finish setup`() =
        assertEquals(BannerKind.DataProblem, pick(env = everything, problems = 1))

    @Test fun `safety banners can't be dismissed`() {
        BannerKind.entries.filter { it.safety }.forEach { kind ->
            val failed = runCatching { BannerMemory().dismiss(kind, NOW) }.isFailure
            assertEquals("$kind dismissible", true, failed)
        }
    }

    @Test fun `finish setup later hides it for a day, then it comes back`() {
        val env = HomeEnvironment(nextSetupStep = "Alerts", backedUp = false)
        val later = BannerMemory().dismiss(BannerKind.FinishSetup, NOW)
        assertEquals(BannerKind.NotBackedUp, pick(env = env, memory = later, at = NOW.plus(Duration.ofHours(23))))
        assertEquals(BannerKind.FinishSetup, pick(env = env, memory = later, at = NOW.plus(SETUP_LATER).plusSeconds(1)))
    }

    @Test fun `a dismissed nudge stays dismissed and the next one takes the slot`() {
        val env = HomeEnvironment(backedUp = false, widgetPlaced = false)
        val memory = BannerMemory().dismiss(BannerKind.NotBackedUp, NOW)
        assertEquals(BannerKind.AddWidget, pick(env = env, memory = memory, at = NOW.plus(Duration.ofDays(30))))
    }

    @Test fun `a closed restart card stays closed for that state and build, and comes back with a new build`() {
        val env = HomeEnvironment(backedUp = false, systemUiRestart = ModuleState.Restart, build = 7)
        assertEquals(BannerKind.RestartSystemUi, pick(env = env))
        val memory = BannerMemory().dismissKey(env.restartKey!!, NOW)
        assertEquals(BannerKind.NotBackedUp, pick(env = env, memory = memory))
        assertEquals(BannerKind.RestartSystemUi, pick(env = env.copy(build = 8), memory = memory))
        assertEquals(BannerKind.RestartSystemUi, pick(env = env.copy(systemUiRestart = ModuleState.Update), memory = memory))
        assertEquals(BannerKind.NotBackedUp, pick(env = env.copy(systemUiRestart = null), memory = memory))
        assertEquals(1, memory.closed)
        assertEquals(BannerKind.RestartSystemUi, pick(env = env, memory = memory.restore(BannerKind.RestartSystemUi)))
    }

    @Test fun `a stalled LibreView upload shows until closed, for that stall only, below blocked features`() {
        val env = HomeEnvironment(backedUp = false, clinicBehind = NOW.minus(Duration.ofHours(3)))
        assertEquals(BannerKind.ClinicUploadBehind, pick(env = env))
        assertEquals(BannerKind.FeatureBlocked, pick(env = env.copy(blocked = listOf(BlockedFeature.HealthConnect))))
        val memory = BannerMemory().dismissKey(env.behindKey!!, NOW)
        assertEquals(BannerKind.NotBackedUp, pick(env = env, memory = memory))
        // a later stall shows again; Undo brings this one back
        assertEquals(BannerKind.ClinicUploadBehind, pick(env = env.copy(clinicBehind = NOW.minus(Duration.ofHours(2))), memory = memory))
        assertEquals(BannerKind.ClinicUploadBehind, pick(env = env, memory = memory.restore(BannerKind.ClinicUploadBehind)))
        assertEquals(BannerKind.NotBackedUp, pick(env = env.copy(clinicBehind = null)))
    }

    @Test fun `the Libre app stopped after a LibreView sign-in shows above a stalled upload, until closed, for that stop only`() {
        val stop = NOW.minus(Duration.ofMinutes(12))
        val env = HomeEnvironment(backedUp = false, clinicBehind = NOW.minus(Duration.ofHours(3)), libreAppStopped = stop)
        assertEquals(BannerKind.LibreAppStopped, pick(env = env))
        assertEquals(BannerKind.FeatureBlocked, pick(env = env.copy(blocked = listOf(BlockedFeature.HealthConnect))))
        val memory = BannerMemory().dismissKey(env.stoppedKey!!, NOW)
        assertEquals(BannerKind.ClinicUploadBehind, pick(env = env, memory = memory))
        // another stop shows again; sent again (no stop): gone
        assertEquals(BannerKind.LibreAppStopped, pick(env = env.copy(libreAppStopped = NOW), memory = memory))
        assertEquals(BannerKind.ClinicUploadBehind, pick(env = env.copy(libreAppStopped = null)))
    }

    @Test fun `Undo and Show closed tips bring closed tips back`() {
        val env = HomeEnvironment(backedUp = false, widgetPlaced = false)
        val two = BannerMemory().dismiss(BannerKind.NotBackedUp, NOW).dismiss(BannerKind.AddWidget, NOW)
        assertEquals(2, two.closed)
        assertEquals(null, pick(env = env, memory = two))
        assertEquals(BannerKind.AddWidget, pick(env = env, memory = two.restore(BannerKind.AddWidget)))
        assertEquals(BannerKind.NotBackedUp, pick(env = env, memory = two.restoreAll()))
        assertEquals(0, two.restoreAll().closed)
        // a kind this build no longer has isn't counted
        assertEquals(0, BannerMemory(mapOf("Gone" to 1L)).closed)
    }

    @Test fun `the updates suggestion comes after finish setup and before the other tips, and stays closed`() {
        val env = everything.copy(updateChecksOff = true)
        assertEquals(BannerKind.FinishSetup, pick(env = env))
        val noSetup = env.copy(nextSetupStep = null)
        assertEquals(BannerKind.TurnOnUpdates, pick(env = noSetup))
        // on, or no updater in this build (Google Play: null): never
        assertEquals(BannerKind.NotBackedUp, pick(env = noSetup.copy(updateChecksOff = false)))
        assertEquals(BannerKind.NotBackedUp, pick(env = noSetup.copy(updateChecksOff = null)))
        // Not now: closed for good (no day like Finish setup's Later), counted among the closed tips
        val closed = BannerMemory().dismiss(BannerKind.TurnOnUpdates, NOW)
        assertEquals(BannerKind.NotBackedUp, pick(env = noSetup, memory = closed, at = NOW.plus(Duration.ofDays(365))))
        assertEquals(1, closed.closed)
        assertEquals(BannerKind.TurnOnUpdates, pick(env = noSetup, memory = closed.restoreAll()))
    }
}
