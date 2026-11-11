// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The GitHub releases over the app's shared OkHttp client (tools/check allow.json `network_ok`): the List-releases
 * JSON and an APK download. Every hop is https ([UpdateEndpoint.httpsOnly]; only a debug build's local test server is
 * plain http), redirects never go from https to http, nothing is sent but the GET.
 */
class Downloader(
    http: OkHttpClient,
    private val endpoint: UpdateEndpoint,
    private val io: CoroutineDispatcher,
) {
    private val client =
        http
            .newBuilder()
            .followRedirects(true)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (endpoint.httpsOnly && !chain.request().url.isHttps) throw IOException("not https")
                chain.proceed(chain.request())
            }.build()

    private val api = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    /** The releases, or a failure when offline / the API refused (rate limit). */
    suspend fun releases(): Result<List<Release>> =
        withContext(io) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(endpoint.api)
                        .header("Accept", "application/vnd.github+json")
                        .build()
                api.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    Releases.parse(response.body?.string().orEmpty(), endpoint)
                }
            }
        }

    /**
     * Downloads [apk] to [into] (through a `.part` file, replaced only when complete): never more than its listed size,
     * [progress] 0..1 as it goes. Returns the file's SHA-256 (lower-case hex). Cancelling the coroutine stops it and
     * deletes the part.
     */
    suspend fun download(
        apk: ApkAsset,
        into: File,
        progress: (Float) -> Unit,
    ): String =
        withContext(io) {
            require(apk.size in 1..MAX_BYTES) { "size ${apk.size}" }
            val part = File(into.path + ".part")
            into.parentFile?.mkdirs()
            try {
                val request = Request.Builder().url(apk.url).build()
                val digest =
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = checkNotNull(response.body)
                        val length = body.contentLength()
                        check(length < 0 || length == apk.size) { "length $length" }
                        val sha = java.security.MessageDigest.getInstance("SHA-256")
                        var total = 0L
                        body.byteStream().use { input ->
                            part.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    check(total <= apk.size) { "longer than ${apk.size}" }
                                    sha.update(buffer, 0, n)
                                    output.write(buffer, 0, n)
                                    progress(total.toFloat() / apk.size)
                                }
                            }
                        }
                        check(total == apk.size) { "got $total of ${apk.size}" }
                        sha.digest().joinToString("") { "%02x".format(it) }
                    }
                into.delete()
                check(part.renameTo(into)) { "rename" }
                digest
            } finally {
                part.delete()
            }
        }

    companion object {
        /** No release APK comes near this; a bigger "APK" is never downloaded. */
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}
