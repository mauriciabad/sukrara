// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import java.io.File

private const val NOT_REQUIRED = PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
private const val REQUIRED = PackageInstaller.SessionParams.USER_ACTION_REQUIRED

/**
 * Installs a checked APK with Android's PackageInstaller: a new session for this app's package, the file written in
 * full, committed with a status that comes back to [UpdateReceiver]. Android installs it whole or not at all: a
 * failure, a cancelled prompt or a phone that turns off midway leaves the running version as it was.
 */
class Installer(
    private val ctx: Context,
) {
    /** The person allowed installs from Sukrara (*Install unknown apps*); until then Android asks during the install. */
    fun mayInstallPackages(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /**
     * Commits [apk] (the update to [version]); [silent] asks Android not to prompt (it may still: the status says
     * so). An earlier session of Sukrara's that never finished is abandoned first.
     */
    fun install(
        apk: File,
        version: String,
        silent: Boolean,
    ) {
        val installer = ctx.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                setSize(apk.length())
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                // an automatic update asks Android not to prompt; the person's own *Update* gets Android's confirmation
                setRequireUserAction(if (silent) NOT_REQUIRED else REQUIRED)
            }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(status(id, version).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Where Android sends the session's status: [UpdateReceiver], with the extras Android fills in (so mutable). */
    private fun status(
        session: Int,
        version: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            session,
            Intent(ctx, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_STATUS).putExtra(UpdateReceiver.EXTRA_VERSION, version),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}
