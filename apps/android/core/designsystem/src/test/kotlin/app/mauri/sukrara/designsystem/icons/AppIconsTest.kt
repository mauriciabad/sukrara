// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.icons

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.banner.Banner
import app.mauri.sukrara.designsystem.banner.BannerChoice
import app.mauri.sukrara.designsystem.banner.BannerSeverity
import app.mauri.sukrara.designsystem.banner.BannerSlot
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import com.android.settingslib.spa.framework.theme.SettingsDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * #181 ("in settings we had app icons in some items, bring all of them back"), and the PoC's guard for "app icons
 * have inconsistent sizes": every app or service icon goes through [AppIcons] (AOSP Settings' IconDrawableFactory
 * path) and is drawn at SPA's app-list size, and every bundled service icon is a real app icon (adaptive, or a
 * full-size legacy bitmap), so nothing can drift back to a hand-sized picture. Which rows carry which icon:
 * `ServiceRowsTest` (:integrations) and `ServiceIconRowsTest` (:feature:settings).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AppIconsTest {
    @get:Rule val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledIconsAreAppIcons() {
        for (s in Service.entries) {
            val d = ctx.getDrawable(s.bundled)
            val ok =
                d is AdaptiveIconDrawable ||
                    (
                        d is BitmapDrawable &&
                            d.intrinsicWidth == d.intrinsicHeight &&
                            d.intrinsicWidth * 160 / ctx.resources.displayMetrics.densityDpi >= 48
                    )
            assertTrue("$s: the bundled icon must be an <adaptive-icon> or a 48 dp legacy launcher bitmap, got $d", ok)
        }
    }

    /** No app installed (Robolectric): every service falls back to its bundled icon, through the same loader. */
    @Test
    fun everyServiceLoads() {
        for (s in Service.entries) assertNotNull("$s", AppIcons.forService(ctx, s))
        assertNull(AppIcons.forPackage(ctx, "com.example.not.installed"))
        // an allowed app that was uninstalled still shows an app icon: the system's default one
        assertNotNull(AppIcons.forPackageOrDefault(ctx, "com.example.not.installed"))
    }

    @Test
    fun iconsAreDrawnAtTheAppListSize() {
        assertEquals(32.dp, SettingsDimension.appIconItemSize)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Column {
                    ServiceIcon(Service.Nightscout)
                    PackageIcon("com.example.app")
                }
            }
        }
        compose
            .onNodeWithTag(serviceIconTag(Service.Nightscout), useUnmergedTree = true)
            .assertWidthIsEqualTo(SettingsDimension.appIconItemSize)
            .assertHeightIsEqualTo(SettingsDimension.appIconItemSize)
        compose
            .onNodeWithTag(packageIconTag("com.example.app"), useUnmergedTree = true)
            .assertWidthIsEqualTo(SettingsDimension.appIconItemSize)
            .assertHeightIsEqualTo(SettingsDimension.appIconItemSize)
    }

    /** The clinic banner's pills (home and the Settings overview) show LibreView's and Tidepool's own icons. */
    @Test
    fun bannerChoicesShowTheServiceIcons() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Column {
                    // a tip (both places today) and a safety card: each draws its choices' icons
                    for (severity in BannerSeverity.entries) {
                        BannerSlot(
                            Banner(
                                "clinic-$severity",
                                DsR.drawable.ds_ic_cloud_upload,
                                "Share your data with your clinic",
                                "text",
                                null,
                                {},
                                {},
                                severity,
                                choices =
                                    listOf(
                                        BannerChoice("LibreView", Service.LibreView) {},
                                        BannerChoice("Tidepool", Service.Tidepool) {},
                                    ),
                            ),
                        )
                    }
                }
            }
        }
        for (s in listOf(Service.LibreView, Service.Tidepool)) {
            val icons = compose.onAllNodesWithTag(serviceIconTag(s), useUnmergedTree = true)
            icons.assertCountEquals(BannerSeverity.entries.size)
            for (i in BannerSeverity.entries.indices) icons[i].assertWidthIsEqualTo(SettingsDimension.appIconItemSize)
        }
    }

    /** App and service icons only through the shared loader: no hand-made round crops or own sizes of brand pictures. */
    @Test
    fun noOtherIconLoaders() {
        val banned =
            Regex(
                """getApplicationIcon\(|getActivityIcon\(|\.loadIcon\(|loadUnbadgedIcon\(|R\.drawable\.ds_ic_app_|ds_ic_service_""",
            )
        val android = File("../..").canonicalFile
        val hits =
            android
                .walkTopDown()
                .onEnter { it.name != "build" && it.name != "settingslib-spa" && !it.name.startsWith(".") }
                .filter { it.extension == "kt" && "/src/main/" in it.path && it.name != "AppIcons.kt" }
                .flatMap { f ->
                    f.readLines().mapIndexedNotNull {
                        i,
                        l,
                        ->
                        if (banned.containsMatchIn(l)) "${f.relativeTo(android)}:${i + 1}: ${l.trim()}" else null
                    }
                }.toList()
        assertTrue(
            "load app / service icons with ServiceIcon / PackageIcon (icons/AppIcons.kt):\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }
}
