// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.integrations.nightscout.NsSite
import app.mauri.sukrara.integrations.nightscout.NsSiteProblem
import java.net.URI
import java.net.URLDecoder

/**
 * The Nightscout address a person typed or pasted, as the source reads it (contracts/sources/nightscout/sites.json):
 * its [root] (scheme + host [+ port] + path: lowercase scheme and host, no default port, no trailing `/`, no `/api/v1`
 * or `/api/v3` at the end), the [token] a pasted `?token=` carried (Nightscout's way of sharing a site; taken out of
 * the address) and [problem] when it can't be used. No scheme = https; plain http only to this phone ([NsSite]'s rule).
 */
data class NightscoutSite(
    val root: String,
    val token: String? = null,
    val problem: NsSiteProblem? = null,
) {
    /** The upload client's site (only when there's no [problem]). */
    val site: NsSite get() = NsSite(root, problem)

    /** The host the person sees in messages ("No answer from ns.example.org"). */
    val host: String get() = runCatching { URI(root).host }.getOrNull() ?: root

    companion object {
        private val API_PATH = Regex("/api/v[13]$", RegexOption.IGNORE_CASE)

        fun parse(input: String): NightscoutSite {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return NightscoutSite(trimmed, problem = NsSiteProblem.Url)
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val u = runCatching { URI(withScheme) }.getOrNull() ?: return NightscoutSite(trimmed, problem = NsSiteProblem.Url)
            val scheme = u.scheme?.lowercase()
            val host =
                u.host?.lowercase() ?: return NightscoutSite(trimmed.trimEnd('/'), problem = NsSiteProblem.Url)
            if (u.rawUserInfo != null) return NightscoutSite(withScheme.trimEnd('/'), problem = NsSiteProblem.Url)
            val token =
                u.rawQuery
                    ?.split('&')
                    ?.firstOrNull { it.startsWith("token=") }
                    ?.substringAfter('=')
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                    ?.takeIf { it.isNotBlank() }
            val defaultPort = (scheme == "https" && u.port == 443) || (scheme == "http" && u.port == 80)
            val port = if (u.port == -1 || defaultPort) "" else ":${u.port}"
            val path = (u.rawPath ?: "").trimEnd('/').replace(API_PATH, "").trimEnd('/')
            val root = "$scheme://$host$port$path"
            val problem =
                when (scheme) {
                    "https" -> null
                    "http" -> if (NsSite.isLoopback(host.removePrefix("[").removeSuffix("]"))) null else NsSiteProblem.Https
                    else -> NsSiteProblem.Url
                }
            return NightscoutSite(root, token, problem)
        }

        /** Two addresses are the same site (docs/44 "Data" rule 2: nothing goes back to where it came from). */
        fun same(
            a: String,
            b: String,
        ): Boolean = parse(a).root == parse(b).root
    }
}
