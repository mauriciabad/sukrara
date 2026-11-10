// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's *Add widget*: once the launcher placed it, the new widget's settings open (the pin callback carries its id). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPinTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the pin callback opens the placed widget's settings`() {
        val intent = WidgetPresence.settingsIntent(context, 42)!!
        assertEquals(ComponentName(context, WidgetConfigActivity::class.java), intent.component)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        assertTrue("started from a receiver", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a callback without an id opens nothing`() {
        assertNull(WidgetPresence.settingsIntent(context, AppWidgetManager.INVALID_APPWIDGET_ID))
    }
}
