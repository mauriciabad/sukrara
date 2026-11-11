// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update check on a recorded List-releases response (no network). */
class UpdateCheckTest {
    private val releases = UpdateCheck.parse(checkNotNull(javaClass.getResource("/releases.json")).readText())

    @Test
    fun `parses releases and skips one without a tag`() {
        assertEquals(listOf("v0.12.2-rc1", "v0.12.1", "v0.11.53"), releases.map { it.tag })
        assertTrue(releases[0].draft)
    }

    @Test
    fun `a 0_x install is offered the newest prerelease but never a draft`() {
        assertEquals("v0.12.1", UpdateCheck.newer(releases, "0.12.0", prerelease = true)?.tag)
    }

    @Test
    fun `a stable install is never offered a prerelease`() {
        assertNull(UpdateCheck.newer(releases, "0.11.53", prerelease = false))
        assertEquals("v0.11.53", UpdateCheck.newer(releases, "0.11.52", prerelease = false)?.tag)
    }

    @Test
    fun `nothing newer is up to date`() {
        assertEquals(UpdateState.UpToDate, UpdateCheck.state(Result.success(releases), "0.12.1", prerelease = true))
    }

    @Test
    fun `a failed fetch is offline`() {
        assertEquals(UpdateState.Offline, UpdateCheck.state(Result.failure(Exception("offline")), "0.12.0", true))
    }

    @Test
    fun `only the project's release pages are opened`() {
        assertTrue(UpdateCheck.isReleaseUrl("https://github.com/mauriciabad/sukrara/releases/tag/v0.12.1"))
        assertTrue(UpdateCheck.isReleaseUrl("https://github.com/mauriciabad/sukrara/releases/download/v0.12.1/Sukrara-0.12.1.dmg"))
        listOf(
            "https://github.com/mauriciabad/sukrara/releases/",
            "https://github.com/mauriciabad/sukrara/releases",
            "https://github.com/someone/sukrara/releases/tag/v1.0",
            "https://github.com.evil.example/mauriciabad/sukrara/releases/tag/v1.0",
            "http://github.com/mauriciabad/sukrara/releases/tag/v1.0",
            "https://evil.example/https://github.com/mauriciabad/sukrara/releases/tag/v1.0",
            "https://github.com/mauriciabad/sukrara/releases/../../other/repo",
            "https://github.com/mauriciabad/sukrara/releases/%2e%2e/%2E%2E/other",
            "https://github.com/mauriciabad/sukrara/releases/tag/v1 x",
            "javascript:alert(1)",
        ).forEach { assertFalse(it, UpdateCheck.isReleaseUrl(it)) }
    }

    @Test
    fun `a release link outside the project falls back to the releases page`() {
        val parsed =
            UpdateCheck
                .parse(
                    """
                    [{"tag_name": "v9.0.0", "html_url": "https://evil.example/releases/tag/v9.0.0", "draft": false,
                      "prerelease": false}]
                    """.trimIndent(),
                ).single()
        assertEquals(UpdateCheck.RELEASES_URL, parsed.url)
        assertTrue(releases.all { UpdateCheck.isReleaseUrl(it.url) })
    }

    @Test
    fun `versions compare number by number with suffixes older`() {
        assertTrue(UpdateCheck.compare("v0.12.10", "0.12.9") > 0)
        assertTrue(UpdateCheck.compare("v0.11.12", "0.11.13") < 0)
        assertEquals(0, UpdateCheck.compare("v1.0", "1.0.0"))
        assertTrue(UpdateCheck.compare("0.12.0-rc1", "0.12.0") < 0)
        assertTrue(UpdateCheck.compare("0.12.1-rc1", "0.12.0") > 0)
        assertTrue(UpdateCheck.compare("v0.12.0-rc2", "0.12.0-rc1") > 0)
        assertEquals(0, UpdateCheck.compare("v0.12.0-rc1", "0.12.0-rc1"))
    }
}
