// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import app.mauri.sukrara.core.model.UpdateRelease
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The APK of a release: its download [url], its SHA-256 as GitHub lists it ([sha256], lower-case hex) and [size]. */
data class ApkAsset(
    val url: String,
    val sha256: String,
    val size: Long,
)

/** One GitHub release of mauriciabad/sukrara; [apk] null = none Sukrara can check (no APK, or no digest). */
data class Release(
    val tag: String,
    val url: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val apk: ApkAsset? = null,
) {
    val version: String get() = tag.removePrefix("v")

    fun model() = UpdateRelease(version, url, prerelease, installable = apk != null)
}

/** The pure part of the update check: which release is newer, and what the List-releases JSON says. */
object Releases {
    /**
     * The release to offer: the newest non-draft one newer than [installed]; prereleases only when the installed
     * version is a prerelease itself (major 0). Null = up to date.
     */
    fun newer(
        releases: List<Release>,
        installed: String,
        prerelease: Boolean,
    ): Release? =
        releases
            .filter { !it.draft && (prerelease || !it.prerelease) }
            .filter { compare(it.tag, installed) > 0 }
            .maxWithOrNull { a, b -> compare(a.tag, b.tag) }

    /**
     * Compares two versions ("v0.12.1", "0.12.0-rc2") number by number; a version with a pre-release suffix
     * ("-rc1") is older than the same numbers without one.
     */
    fun compare(
        a: String,
        b: String,
    ): Int {
        fun numbers(v: String) =
            v
                .removePrefix("v")
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        val sa = a.substringAfter('-', "")
        val sb = b.substringAfter('-', "")
        return when {
            sa.isEmpty() && sb.isEmpty() -> 0

            sa.isEmpty() -> 1

            sb.isEmpty() -> -1

            // "rc2" > "rc1": the suffix's number decides
            else -> (sa.filter(Char::isDigit).toIntOrNull() ?: 0).compareTo(sb.filter(Char::isDigit).toIntOrNull() ?: 0)
        }
    }

    /**
     * Parses the List-releases JSON (unknown fields ignored; a release without a tag is skipped). A page link that
     * isn't one of [endpoint]'s release pages falls back to its releases page, so the app never opens it; an APK is
     * kept only when it is `Sukrara-<version>.apk`, its link is one of [endpoint]'s downloads and GitHub lists its
     * SHA-256.
     */
    fun parse(
        json: String,
        endpoint: UpdateEndpoint = UpdateEndpoint.GitHub,
    ): List<Release> =
        Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val tag = o.string("tag_name") ?: return@mapNotNull null
            Release(
                tag = tag,
                url = o.string("html_url")?.takeIf(endpoint::isReleasePage) ?: endpoint.releasesPage,
                prerelease = o["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false,
                draft = o["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                apk = apk(o["assets"] as? JsonArray, tag.removePrefix("v"), endpoint),
            )
        }

    private fun apk(
        assets: JsonArray?,
        version: String,
        endpoint: UpdateEndpoint,
    ): ApkAsset? {
        val asset =
            assets
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it.string("name") == "Sukrara-$version.apk" } ?: return null
        val url = asset.string("browser_download_url")?.takeIf(endpoint::isDownload) ?: return null
        val digest = asset.string("digest")?.lowercase() ?: return null
        val sha256 = digest.removePrefix("sha256:").takeIf { digest.startsWith("sha256:") && it.isSha256() } ?: return null
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: return null
        return ApkAsset(url, sha256, size)
    }

    private fun String.isSha256() = length == 64 && all { it in '0'..'9' || it in 'a'..'f' }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
