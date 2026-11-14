// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.version

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.AppBuild
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The installed version and what build it is ([AppBuild], the manifest's meta-data): the one place every screen and
 * report reads them (docs/DEVELOPMENT.md › Development and release builds).
 */
object AppBuildInfo {
    /** versionName ("0.14.5"): the release this build is, or the last release before it. */
    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    fun build(context: Context): AppBuild =
        AppBuild.parse(
            runCatching {
                context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                    ?.getString(AppBuild.META_DATA)
            }.getOrNull(),
        )

    /** For reports and issues: "0.14.5", or "0.14.5 development build +23 d744fd74 2026-11-14" ([AppBuild.report]). */
    fun report(context: Context): String = build(context).report(version(context))

    /**
     * What a development build says where the version shows (About): "Development build · 0.14.5 + 23 commits ·
     * d744fd74 · 14 Nov", whatever is known. A release says its version and where it comes from instead (the caller's
     * wording: "0.14.5 · GitHub version").
     */
    fun developmentText(
        context: Context,
        version: String,
        build: AppBuild.Development,
    ): String {
        val res = context.resources
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val base =
            build.commits?.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.ds_version_commits, it, version, it) }
                ?: version
        val date =
            build.date?.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
        return listOfNotNull(context.getString(R.string.ds_version_development), base, build.commit, date).joinToString(" · ")
    }
}
