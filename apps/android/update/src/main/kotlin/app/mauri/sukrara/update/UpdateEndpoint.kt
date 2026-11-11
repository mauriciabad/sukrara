// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

/**
 * Where the updater looks and what it trusts: the List-releases [api], the [releasesPage] (and the pages under it the
 * app may open), the only links an APK may come from ([downloadPrefix]) and the SHA-256 of the one certificate an
 * update must be signed with ([cert], upper-case hex). Release builds always use [GitHub]; only a debug build's
 * `UPDATES` command (app/src/debug) points it at a local test server, which is the one case without https.
 */
data class UpdateEndpoint(
    val api: String,
    val releasesPage: String,
    val downloadPrefix: String,
    val cert: String,
    val httpsOnly: Boolean = true,
) {
    /** One of the project's release pages, with nothing that could leave it once a browser resolves it. */
    fun isReleasePage(url: String): Boolean = under(url, "$releasesPage/")

    /** One of the release downloads (GitHub then redirects to its file host, https only: [Downloader]). */
    fun isDownload(url: String): Boolean = under(url, downloadPrefix)

    private fun under(
        url: String,
        prefix: String,
    ) = url.startsWith(prefix) &&
        url.length > prefix.length &&
        listOf("..", "%2e", "%2E", "\\").none { it in url } &&
        url.none { it.isWhitespace() || it.isISOControl() }

    companion object {
        const val REPO_URL = "https://github.com/mauriciabad/sukrara"
        const val RELEASES_URL = "$REPO_URL/releases"

        /**
         * SHA-256 of Sukrara's release signing certificate: tools/release/android-cert.sha256 (UpdateEndpointTest keeps
         * them equal). An APK signed with anything else is never installed.
         */
        const val RELEASE_CERT = "27EC21B2544CE5EB7FD94849AD16DB8651B34CF898CDB50ADE2159231A1A8D96"

        /**
         * A debug build's local test server (`UPDATES` command; never in a release build): `<base>releases` in
         * GitHub's List-releases form, the pages under it, the APKs under `<base>download/`, trusting [cert].
         */
        fun testServer(
            base: String,
            cert: String,
        ) = UpdateEndpoint("${base}releases", "${base}releases", "${base}download/", cert.uppercase(), httpsOnly = false)

        /** The GitHub releases (all of them: `/latest` never returns a prerelease). */
        val GitHub =
            UpdateEndpoint(
                api = "https://api.github.com/repos/mauriciabad/sukrara/releases",
                releasesPage = RELEASES_URL,
                downloadPrefix = "$RELEASES_URL/download/",
                cert = RELEASE_CERT,
            )
    }
}
