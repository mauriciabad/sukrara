// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * tools/release/build-info.sh's line → [AppBuild]: only the exact "release" is a release; everything else, unreadable
 * or missing included, is a development build (nothing claims to be a release by accident). The report's version.
 */
class AppBuildTest {
    @Test
    fun onlyTheExactReleaseLineIsARelease() {
        assertEquals(AppBuild.Release, AppBuild.parse("release"))
        assertEquals(AppBuild.Release, AppBuild.parse(" release\n"))
        for (line in listOf(null, "", "development", "releases", "release 1", "Release", "\${sukraraBuild}")) {
            assertEquals(line.toString(), AppBuild.Development(), AppBuild.parse(line))
        }
    }

    @Test
    fun developmentLineGivesWhatItKnows() {
        assertEquals(
            AppBuild.Development(23, "d744fd74", LocalDate.of(2026, 11, 14)),
            AppBuild.parse("development 23 d744fd74 2026-11-14"),
        )
        assertEquals(
            AppBuild.Development(null, "d744fd74", LocalDate.of(2026, 11, 14)),
            AppBuild.parse("development ? d744fd74 2026-11-14"),
        )
        assertEquals(AppBuild.Development(), AppBuild.parse("development ? ? ?"))
        assertEquals(AppBuild.Development(commits = 0, commit = "abc"), AppBuild.parse("development 0 abc not-a-date"))
    }

    @Test
    fun reportSaysDevelopmentBuild() {
        assertEquals("0.14.5", AppBuild.Release.report("0.14.5"))
        assertEquals(
            "0.14.5 development build +23 d744fd74 2026-11-14",
            AppBuild.Development(23, "d744fd74", LocalDate.of(2026, 11, 14)).report("0.14.5"),
        )
        assertEquals("0.14.5 development build", AppBuild.Development().report("0.14.5"))
    }
}
