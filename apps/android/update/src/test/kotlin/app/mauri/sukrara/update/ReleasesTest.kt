// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update check on a recorded List-releases response (no network). */
class ReleasesTest {
    private val releases = Releases.parse(checkNotNull(javaClass.getResource("/releases.json")).readText())

    @Test
    fun `parses releases and skips one without a tag`() {
        assertEquals(listOf("v0.12.2-rc1", "v0.12.1", "v0.11.53", "v0.12.3-rc1"), releases.map { it.tag })
        assertTrue(releases[0].draft)
        assertTrue(releases[3].prerelease)
    }

    @Test
    fun `the newest release is offered, never a draft or a release candidate`() {
        assertEquals("v0.12.1", Releases.newer(releases, "0.12.0")?.tag)
        assertEquals("v0.12.1", Releases.newer(releases, "0.11.52")?.tag)
    }

    @Test
    fun `nothing newer is up to date`() {
        assertNull(Releases.newer(releases, "0.12.1"))
        assertNull(Releases.newer(releases, "0.13.0"))
    }

    @Test
    fun `the release's APK is its Sukrara-version apk with GitHub's SHA-256`() {
        val apk = checkNotNull(releases[1].apk)
        assertEquals("https://github.com/mauriciabad/sukrara/releases/download/v0.12.1/Sukrara-0.12.1.apk", apk.url)
        assertEquals("cecb94ab15ba22a86503602bad0a6175eb9013b6a0803e8773fc5163edcb3c6d", apk.sha256)
        assertEquals(21998103L, apk.size)
        assertTrue(releases[1].model().installable)
        assertEquals("0.12.1", releases[1].model().version)
        // listed without a digest: nothing to check it against, so About opens the release page instead
        assertNull(releases[2].apk)
        assertFalse(releases[2].model().installable)
    }

    @Test
    fun `an APK link or digest that isn't the release's is never kept`() {
        fun apk(
            url: String,
            digest: String = "sha256:" + "a".repeat(64),
            name: String = "Sukrara-9.0.0.apk",
        ) = Releases
            .parse(
                """
                [{"tag_name": "v9.0.0", "html_url": "https://github.com/mauriciabad/sukrara/releases/tag/v9.0.0",
                  "assets": [{"name": "$name", "browser_download_url": "$url", "digest": "$digest", "size": 10}]}]
                """.trimIndent(),
            ).single()
            .apk
        val ok = "https://github.com/mauriciabad/sukrara/releases/download/v9.0.0/Sukrara-9.0.0.apk"
        assertEquals(ok, apk(ok)?.url)
        assertNull(apk("https://evil.example/Sukrara-9.0.0.apk"))
        assertNull(apk("http://github.com/mauriciabad/sukrara/releases/download/v9.0.0/Sukrara-9.0.0.apk"))
        assertNull(apk("https://github.com/mauriciabad/sukrara/releases/download/../../x/Sukrara-9.0.0.apk"))
        assertNull(apk(ok, digest = "md5:abc"))
        assertNull(apk(ok, digest = "sha256:abc"))
        assertNull(apk(ok, name = "Other-9.0.0.apk"))
    }

    @Test
    fun `only the project's release pages are opened`() {
        val github = UpdateEndpoint.GitHub
        assertTrue(github.isReleasePage("https://github.com/mauriciabad/sukrara/releases/tag/v0.12.1"))
        assertTrue(github.isReleasePage("https://github.com/mauriciabad/sukrara/releases/download/v0.12.1/Sukrara-0.12.1.dmg"))
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
        ).forEach { assertFalse(it, github.isReleasePage(it)) }
    }

    @Test
    fun `a release link outside the project falls back to the releases page`() {
        val parsed =
            Releases
                .parse(
                    """
                    [{"tag_name": "v9.0.0", "html_url": "https://evil.example/releases/tag/v9.0.0", "draft": false,
                      "prerelease": false}]
                    """.trimIndent(),
                ).single()
        assertEquals(UpdateEndpoint.RELEASES_URL, parsed.url)
        assertTrue(releases.all { UpdateEndpoint.GitHub.isReleasePage(it.url) })
    }

    @Test
    fun `versions compare number by number with suffixes older`() {
        assertTrue(Releases.compare("v0.12.10", "0.12.9") > 0)
        assertTrue(Releases.compare("v0.11.12", "0.11.13") < 0)
        assertEquals(0, Releases.compare("v1.0", "1.0.0"))
        assertTrue(Releases.compare("0.12.0-rc1", "0.12.0") < 0)
        assertTrue(Releases.compare("0.12.1-rc1", "0.12.0") > 0)
        assertTrue(Releases.compare("v0.12.0-rc2", "0.12.0-rc1") > 0)
        assertEquals(0, Releases.compare("v0.12.0-rc1", "0.12.0-rc1"))
        assertTrue(Releases.compare("v0.14.0", "0.13.0") > 0)
        assertTrue(Releases.compare("v1.0.0", "0.99.99") > 0)
    }
}
