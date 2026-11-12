// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AlertThresholdReviewTest {
    private val clock = FakeClock(Instant.parse("2026-10-27T06:00:00Z"))
    private val settings = InMemorySettingsRepository()
    private val store = FakeAlertSettingsStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `editing the draft stores nothing until confirmed`() =
        runTest {
            val vm = ThresholdReviewViewModel(store, settings, clock)
            vm.setThreshold(AlertKind.LOW, 75.0)
            assertEquals(
                75.0,
                vm.draft.value!!
                    .rule(AlertKind.LOW)
                    .threshold,
                0.0,
            )
            assertEquals(0, store.writes)
            assertEquals(
                69.0,
                store.state.value
                    .rule(AlertKind.LOW)
                    .threshold,
                0.0,
            )
            assertNull(SchemaPrefs.thresholdsConfirmedAt(settings).first())
        }

    @Test
    fun `confirm stores the thresholds and when, and returns them`() =
        runTest {
            val vm = ThresholdReviewViewModel(store, settings, clock)
            vm.setThreshold(AlertKind.HIGH, 200.0)
            vm.confirm()
            assertEquals(
                200.0,
                store.state.value
                    .rule(AlertKind.HIGH)
                    .threshold,
                0.0,
            )
            assertEquals(clock.instant, SchemaPrefs.thresholdsConfirmedAt(settings).first())
            assertEquals(ThresholdReviewResult.Confirmed(store.state.value), vm.result.value)
        }

    @Test
    fun `a double tap on confirm saves once and ends the review once`() =
        runTest {
            val vm = ThresholdReviewViewModel(store, settings, clock)
            vm.setThreshold(AlertKind.HIGH, 200.0)
            vm.confirm()
            vm.confirm()
            assertEquals(1, store.writes)
            assertEquals(ThresholdReviewResult.Confirmed(store.state.value), vm.result.value)
        }

    @Test
    fun `only the first of confirm and cancel counts`() =
        runTest {
            val cancelled = ThresholdReviewViewModel(store, settings, clock)
            assertNull(cancelled.result.value)
            cancelled.setThreshold(AlertKind.LOW, 75.0)
            cancelled.cancel()
            cancelled.confirm()
            assertEquals(ThresholdReviewResult.Cancelled, cancelled.result.value)
            assertEquals(0, store.writes)
            assertNull(SchemaPrefs.thresholdsConfirmedAt(settings).first())

            val confirmed = ThresholdReviewViewModel(store, settings, clock)
            confirmed.confirm()
            confirmed.cancel()
            assertEquals(ThresholdReviewResult.Confirmed(store.state.value), confirmed.result.value)
            assertEquals(1, store.writes)
        }

    @Test
    fun `confirm keeps changes made elsewhere meanwhile (only thresholds are taken from the review)`() =
        runTest {
            val vm = ThresholdReviewViewModel(store, settings, clock)
            vm.setThreshold(AlertKind.LOW, 65.0)
            store.state.value = store.state.value.copy(on = false)
            vm.confirm()
            assertEquals(false, store.state.value.on)
            assertEquals(
                65.0,
                store.state.value
                    .rule(AlertKind.LOW)
                    .threshold,
                0.0,
            )
        }

    @Test
    fun `an out-of-format threshold is brought into the schema format`() =
        runTest {
            val vm = ThresholdReviewViewModel(store, settings, clock)
            vm.setThreshold(AlertKind.VERY_LOW, 10.0)
            assertEquals(
                40.0,
                vm.draft.value!!
                    .rule(AlertKind.VERY_LOW)
                    .threshold,
                0.0,
            )
            assertEquals(AlertSettings().rule(AlertKind.LOW), vm.draft.value!!.rule(AlertKind.LOW))
        }
}
