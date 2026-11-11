// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The releases and the APK download over a local MockWebServer (loopback: the debug endpoint's plain http). */
class DownloaderTest {
    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString()
    private val local = UpdateEndpoint.testServer(base, UpdateEndpoint.RELEASE_CERT)
    private val dir = File.createTempFile("update", "").apply { delete() }

    @After fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private val bytes = ByteArray(300_000) { (it % 251).toByte() }
    private val sha =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun downloader(endpoint: UpdateEndpoint = local) = Downloader(OkHttpClient(), endpoint, Dispatchers.IO)

    @Test
    fun `the releases come from the endpoint, its APK links only`() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{"tag_name": "v9.0.0", "html_url": "${base}releases/tag/v9.0.0", "prerelease": true,
                      "assets": [{"name": "Sukrara-9.0.0.apk", "browser_download_url": "${base}download/Sukrara-9.0.0.apk",
                                  "digest": "sha256:$sha", "size": ${bytes.size}}]}]
                    """.trimIndent(),
                ),
            )
            val release = downloader().releases().getOrThrow().single()
            assertEquals("${base}download/Sukrara-9.0.0.apk", release.apk?.url)
            assertEquals("/releases", server.takeRequest().path)
        }

    @Test
    fun `a download is checked as it comes and kept only when whole`() =
        runBlocking {
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val into = File(dir, "Sukrara-9.0.0.apk")
            val seen = mutableListOf<Float>()
            val got = downloader().download(ApkAsset("${base}download/Sukrara-9.0.0.apk", sha, bytes.size.toLong()), into) { seen += it }
            assertEquals(sha, got)
            assertTrue(into.readBytes().contentEquals(bytes))
            assertEquals(1f, seen.last())
            assertFalse(File(into.path + ".part").exists())
        }

    @Test
    fun `a download longer or shorter than the release lists is refused and leaves nothing`() =
        runBlocking {
            val into = File(dir, "Sukrara-9.0.0.apk")
            for (size in listOf(bytes.size - 1L, bytes.size + 1L)) {
                server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
                val result = runCatching { downloader().download(ApkAsset("${base}download/x.apk", sha, size), into) {} }
                assertTrue("$size", result.isFailure)
                assertFalse(into.exists())
                assertFalse(File(into.path + ".part").exists())
            }
        }

    @Test
    fun `release builds never fetch over plain http`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("[]"))
            val https = local.copy(httpsOnly = true)
            assertTrue(downloader(https).releases().isFailure)
            val into = File(dir, "x.apk")
            val result = runCatching { downloader(https).download(ApkAsset("${base}download/x.apk", sha, bytes.size.toLong()), into) {} }
            assertTrue(result.isFailure)
            assertFalse(into.exists())
        }
}
