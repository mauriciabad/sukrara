// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.LocalDate

/**
 * What the running build is (docs/DEVELOPMENT.md › Development and release builds): a published [Release], or a
 * [Development] build (tools/install.sh, CI, every local build) of some commit after the release its version names.
 * The manifest's [META_DATA] carries it, written by tools/release/build-info.sh; anything unreadable is a development
 * build, so nothing claims to be a release by accident.
 */
sealed interface AppBuild {
    data object Release : AppBuild

    /** [commits] since the release commit, [commit] HEAD's short hash, [date] its commit date; null when unknown. */
    data class Development(
        val commits: Int? = null,
        val commit: String? = null,
        val date: LocalDate? = null,
    ) : AppBuild

    /**
     * The version for reports and issues (Copy report, sensor feedback, the import's issue): "0.14.5" for a release,
     * "0.14.5 development build +23 d744fd74 2026-11-14" otherwise (whatever is known).
     */
    fun report(version: String): String =
        when (this) {
            Release -> {
                version
            }

            is Development -> {
                listOfNotNull("$version development build", commits?.let { "+$it" }, commit, date?.toString()).joinToString(" ")
            }
        }

    companion object {
        /** The manifest meta-data's name. */
        const val META_DATA = "app.mauri.sukrara.build"

        /** build-info.sh's line: "release" or "development <commits> <commit> <yyyy-mm-dd>" ("?" = unknown). */
        fun parse(line: String?): AppBuild {
            val parts = line.orEmpty().trim().split(' ')
            if (parts == listOf("release")) return Release
            if (parts.first() != "development") return Development()

            fun part(i: Int) = parts.getOrNull(i)?.takeUnless { it == "?" || it.isEmpty() }
            return Development(
                commits = part(1)?.toIntOrNull(),
                commit = part(2)?.takeIf { c -> c.all { it.isLetterOrDigit() } },
                date = part(3)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }
    }
}
