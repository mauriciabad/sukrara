// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.AppBuild
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * About's version (docs/DEVELOPMENT.md › Development and release builds): a release says its number and where it
 * comes from; a development build (tools/install.sh, any local build) says so first, with the commits since the release,
 * the commit and its date, in English and Spanish, and never the release wording.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VersionSummaryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun context(tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private val dev = AppBuild.Development(23, "d744fd74", LocalDate.of(2026, 11, 14))

    @Test
    fun releaseKeepsItsWording() {
        val en = context("en-GB")
        assertEquals("0.14.5 · GitHub version", versionSummary(en, "0.14.5", Distribution.GitHub, AppBuild.Release))
        assertEquals("0.14.5 · Google Play version", versionSummary(en, "0.14.5", Distribution.GooglePlay, AppBuild.Release))
        assertEquals("0.14.5 · versión de GitHub", versionSummary(context("es-ES"), "0.14.5", Distribution.GitHub, AppBuild.Release))
    }

    @Test
    fun developmentBuildSaysSo() {
        val en = context("en-GB")
        assertEquals(
            "Development build · 0.14.5 + 23 commits · d744fd74 · 14 Nov",
            versionSummary(en, "0.14.5", Distribution.GitHub, dev),
        )
        assertEquals(
            "Development build · 0.14.5 + 1 commit · d744fd74 · 14 Nov",
            versionSummary(en, "0.14.5", Distribution.GitHub, dev.copy(commits = 1)),
        )
        // the release commit's own source, built by install.sh: still not the published release
        assertEquals(
            "Development build · 0.14.5 · d744fd74 · 14 Nov",
            versionSummary(en, "0.14.5", Distribution.GitHub, dev.copy(commits = 0)),
        )
        // the Play flavor too, and whatever git couldn't tell is left out
        assertEquals("Development build · 0.14.5", versionSummary(en, "0.14.5", Distribution.GooglePlay, AppBuild.Development()))
        val es = versionSummary(context("es-ES"), "0.14.5", Distribution.GitHub, dev)
        assertEquals("Versión de desarrollo · 0.14.5 + 23 cambios · d744fd74", es.substringBeforeLast(" · "))
        assert(es.endsWith("14 nov")) { es }
        for (text in listOf(versionSummary(en, "0.14.5", Distribution.GitHub, dev), es)) {
            assert("GitHub" !in text) { text }
        }
    }
}
