// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every Sukrara window follows the phone's dark theme (#181: the pen read card's window had a dark-only theme): each
 * activity in the merged manifest (all modules) that draws anything, with its own theme or the application's, resolves
 * to a light theme with light system bars in light mode and a dark one in dark mode, and has no platform action bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WindowThemesTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val root: Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(System.getProperty("sukrara.mergedManifest") ?: error("sukrara.mergedManifest not set")))
            .documentElement

    private fun Element.attr(name: String) = getAttributeNS(ns, name)

    /** activity name → its window theme (`@style/…`), the application's when it has none; windows that never draw left out. */
    private val windows: Map<String, String> by lazy {
        val app = (root.getElementsByTagName("application").item(0) as Element).attr("theme")
        val list = root.getElementsByTagName("activity")
        (0 until list.length)
            .map { list.item(it) as Element }
            .filter { it.attr("name").startsWith("app.mauri.sukrara.") }
            .associate { it.attr("name") to it.attr("theme").ifEmpty { app } }
            .filterValues { it != "@android:style/Theme.NoDisplay" }
    }

    private fun resolve(
        context: Context,
        theme: String,
    ): Map<Int, Boolean> {
        assertTrue("$theme: not one of Sukrara's own styles", theme.startsWith("@style/"))
        val id = context.resources.getIdentifier(theme.removePrefix("@style/"), "style", context.packageName)
        assertNotEquals("$theme: no such style", 0, id)
        val attrs =
            intArrayOf(
                android.R.attr.isLightTheme,
                android.R.attr.windowActionBar,
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar,
            ).sortedArray()
        val a = ContextThemeWrapper(context, id).obtainStyledAttributes(attrs)
        return try {
            attrs.withIndex().associate { (i, attr) -> attr to a.getBoolean(i, false) }
        } finally {
            a.recycle()
        }
    }

    private fun check(night: Boolean) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("the pen read card and Add treatment must be among the windows", windows.keys.containsAll(OVERLAYS))
        for ((activity, theme) in windows) {
            val r = resolve(context, theme)
            val mode = if (night) "dark" else "light"
            assertEquals("$activity ($theme), $mode: isLightTheme", !night, r[android.R.attr.isLightTheme])
            assertEquals("$activity ($theme), $mode: light status bar", !night, r[android.R.attr.windowLightStatusBar])
            assertEquals("$activity ($theme), $mode: light navigation bar", !night, r[android.R.attr.windowLightNavigationBar])
            assertEquals("$activity ($theme): platform action bar", false, r[android.R.attr.windowActionBar])
        }
    }

    @Test fun `every window is light in light mode`() = check(night = false)

    @Test fun `every window is dark in dark mode`() = check(night = true)

    private companion object {
        val OVERLAYS =
            setOf(
                "app.mauri.sukrara.pen.PenReadActivity",
                "app.mauri.sukrara.feature.treatments.QuickTreatmentActivity",
            )
    }
}
