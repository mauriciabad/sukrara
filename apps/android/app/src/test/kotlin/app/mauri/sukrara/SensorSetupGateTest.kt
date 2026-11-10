// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.NavTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Only Sukrara's own PendingIntents open the new-sensor flow; another app's intent to the main activity just opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SensorSetupGateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val main = ComponentName(app.packageName, MainActivity::class.java.name)

    @Test fun `another app's intent with the action is ignored`() {
        val external =
            Intent(NavTargets.ACTION_SENSOR_SETUP)
                .setComponent(main)
                .putExtra(NavTargets.EXTRA_FROM_SENSOR_TAP, true)
                .putExtra("app.mauri.sukrara.extra.SENSOR_SETUP_TOKEN", "guess")
        assertNull(SensorSetupGate.request(external))
        assertNull(SensorSetupGate.request(Intent(NavTargets.ACTION_SENSOR_SETUP).setComponent(main)))
        assertNull(SensorSetupGate.request(null))
    }

    @Test fun `the trampoline opens the main activity with a signed request`() {
        val controller =
            Robolectric.buildActivity(
                SensorSetupActivity::class.java,
                Intent(NavTargets.ACTION_SENSOR_SETUP)
                    .setClassName(app.packageName, NavTargets.SENSOR_SETUP_ACTIVITY)
                    .putExtra(NavTargets.EXTRA_FROM_SENSOR_TAP, true),
            )
        val activity = controller.create().get()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(main, started.component)
        assertEquals(NewSensorRequest(fromSensorTap = true), SensorSetupGate.request(started))
        assertTrue(activity.isFinishing)
    }

    @Test fun `the trampoline's class is the one other modules name`() {
        assertEquals(NavTargets.SENSOR_SETUP_ACTIVITY, SensorSetupActivity::class.java.name)
    }
}
