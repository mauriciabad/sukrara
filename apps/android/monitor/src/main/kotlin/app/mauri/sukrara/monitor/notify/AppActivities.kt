// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The app's activities as notifications open them, found the same way before the first unlock after a restart (#160):
 * a plain lookup then sees only direct-boot-aware activities, and Sukrara's aren't (they read the app's
 * credential-encrypted storage). From the lock screen Android asks for the unlock first, which is the way in.
 */
object AppActivities {
    private val EVERY = PackageManager.MATCH_DIRECT_BOOT_AWARE.toLong() or PackageManager.MATCH_DIRECT_BOOT_UNAWARE.toLong()

    /** The launcher activity (a new task), or an intent for the package when none is found. */
    fun launch(ctx: Context): Intent {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName)
        val activity =
            ctx.packageManager
                .queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(EVERY))
                .firstOrNull()
                ?.activityInfo
        val launch = if (activity == null) main else main.setClassName(activity.packageName, activity.name)
        return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Whether one of the app's activities handles [action] (whether or not the phone was unlocked yet). */
    fun handles(
        ctx: Context,
        action: String,
    ): Boolean =
        ctx.packageManager
            .queryIntentActivities(
                Intent(action).setPackage(ctx.packageName),
                PackageManager.ResolveInfoFlags.of(EVERY or PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ).isNotEmpty()
}
