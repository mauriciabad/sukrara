// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A composable's context is often a wrapper around the activity (#79: `context as? Activity` missed it). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindActivityTest {
    @Test fun findsTheActivityThroughWrappers() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertSame(activity, activity.findActivity())
        assertSame(activity, ContextWrapper(activity).findActivity())
        assertSame(activity, ContextThemeWrapper(ContextWrapper(activity), 0).findActivity())
    }

    @Test fun noActivityBehindTheApplication() {
        val app: Application = ApplicationProvider.getApplicationContext()
        assertNull(app.findActivity())
        assertNull(ContextWrapper(app).findActivity())
    }
}
