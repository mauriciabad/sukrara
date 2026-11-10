// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.sensorend

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.monitor.notify.SoundGap
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The *Sensor ended* notification (its tap and *Replace sensor*) opens the app's non-exported new-sensor trampoline,
 * explicitly: no other app can open the flow, and an implicit intent would fall back to home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorEndedIntentTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val main = ComponentName(ctx.packageName, "app.mauri.sukrara.MainActivity")

    @Before fun setUp() {
        ctx.applicationInfo.icon = android.R.drawable.star_on
        // the app's launcher activity (a library test has none)
        val pm = shadowOf(ctx.packageManager)
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(
            main,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @Test fun `tap and Replace sensor open the app's new-sensor trampoline`() {
        AndroidSensorEndPresenter(ctx).ended()
        // a sounding info notification waits for SoundGap
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SoundGap.GAP_MS))
        val posted = shadowOf(nm).getNotification(AndroidSensorEndPresenter.id(SensorNotice.ENDED))
        for (pending in listOf(posted.contentIntent, posted.actions.single().actionIntent)) {
            val intent = shadowOf(pending).savedIntent
            assertEquals(ComponentName(ctx.packageName, NavTargets.SENSOR_SETUP_ACTIVITY), intent.component)
            assertEquals(NavTargets.ACTION_SENSOR_SETUP, intent.action)
        }
    }
}
