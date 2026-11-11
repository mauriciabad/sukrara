// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.NavTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Home's ways out to another screen (#79: a missing screen was swallowed, the tap did nothing): Bluetooth and date
 * settings fall back to the phone's Settings, and when nothing opens a short message says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OpenScreenTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Before fun onlyScreensThePhoneHasOpen() = shadowOf(app).checkActivities(true)

    private fun phoneHas(action: String) {
        val component = ComponentName("com.android.settings", "com.android.settings.$action")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    private fun started(): Intent? = shadowOf(activity).nextStartedActivity

    @Test fun aSettingsScreenOpensWhenThePhoneHasIt() {
        phoneHas(Settings.ACTION_BLUETOOTH_SETTINGS)
        phoneHas(Settings.ACTION_SETTINGS)
        openSystemScreen(activity, Settings.ACTION_BLUETOOTH_SETTINGS)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, started()?.action)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test fun withoutThatScreenThePhonesSettingsOpen() {
        phoneHas(Settings.ACTION_SETTINGS)
        openSystemScreen(activity, Settings.ACTION_DATE_SETTINGS)
        assertEquals(Settings.ACTION_SETTINGS, started()?.action)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test fun whenNothingOpensAMessageSaysSo() {
        openSystemScreen(activity, Settings.ACTION_BLUETOOTH_SETTINGS)
        assertNull(started())
        assertEquals(app.getString(R.string.app_open_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test fun ourOwnActionsStayInSukrara() {
        val quick = ComponentName(app, "app.mauri.sukrara.QuickTreatmentTest")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(quick)
        pm.addIntentFilterForActivity(quick, IntentFilter(NavTargets.ACTION_QUICK_TREATMENT).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        open(activity, Intent(NavTargets.ACTION_QUICK_TREATMENT))
        val intent = started()
        assertEquals(NavTargets.ACTION_QUICK_TREATMENT, intent?.action)
        assertEquals(app.packageName, intent?.`package`)
    }
}
