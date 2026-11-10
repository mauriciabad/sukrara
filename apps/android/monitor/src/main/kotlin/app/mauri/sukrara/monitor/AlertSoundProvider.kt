// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.SoundSettings
import app.mauri.sukrara.monitor.alerts.AlertNotifier
import app.mauri.sukrara.monitor.alerts.AlertSoundFiles
import app.mauri.sukrara.monitor.alerts.AlertSoundPicker
import app.mauri.sukrara.monitor.lockedboot.LockedBootStore
import app.mauri.sukrara.monitor.lockedboot.userUnlocked

/**
 * Serves `content://<applicationId>.alertsound/<kind>`, every alert channel's sound. SystemUI's notification player
 * opens the URI for every alert, so the file is decided at play time ([AlertSoundPicker]: the pack chosen in the app,
 * a random variant). **Exported, read-only, nothing secret**: the system's player runs in another process and must
 * open it (a per-URI grant can't be attached to a channel). canonicalize = identity, or a backup / restore resets the
 * channels to the default sound. A sound the person picks in the channel's system settings overrides all of this.
 * Direct-boot aware: before the first unlock after a restart it reads only the device-protected [LockedBootStore].
 *
 * It never blocks System UI's binder thread (#44): the sound settings are the module's cached [AlertsModule.sounds]
 * (the defaults until the store first answered), the file is a direct `R.raw` reference ([AlertSoundFiles]), and any
 * mode but `r` is refused.
 */
class AlertSoundProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (mode != "r") throw SecurityException("read-only")
        val ctx = context ?: return null
        val kind = uri.lastPathSegment?.let(AlertKind::fromWire) ?: return null
        val module = ctx.alertsModule
        val sounds =
            if (!ctx.userUnlocked) {
                // before the first unlock: the sound the plan saved (only the locked boot's alarm rings then)
                LockedBootStore.of(ctx).read()?.let { SoundSettings(pack = it.sound, shuffle = it.shuffle) } ?: SoundSettings()
            } else {
                module?.sounds?.value ?: SoundSettings()
            }
        val picker = module?.soundPicker ?: AlertSoundPicker()
        // a file that can't be opened falls back to the system sound, never to silence
        return open(ctx, picker.pick(kind, sounds, System.currentTimeMillis())) ?: open(ctx, AlertSoundPicker.SYSTEM)
    }

    private fun open(ctx: Context, name: String): AssetFileDescriptor? =
        if (name == AlertSoundPicker.SYSTEM) {
            ctx.contentResolver.openAssetFileDescriptor(Settings.System.DEFAULT_NOTIFICATION_URI, "r")
        } else {
            AlertSoundFiles.of(name)?.let { ctx.resources.openRawResourceFd(it) }
        }

    override fun getType(uri: Uri) = "audio/ogg"

    override fun canonicalize(url: Uri) = url

    override fun uncanonicalize(url: Uri) = url

    /** The name the channel's settings page shows for the sound: "Sukrara · Low". */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val ctx = requireNotNull(context)
        val kind = uri.lastPathSegment?.let(AlertKind::fromWire)
        val title = "Sukrara" + (kind?.let { " · " + AlertNotifier.kindLabel(ctx, it) } ?: "")
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, "title")
        return MatrixCursor(cols).apply {
            addRow(
                cols.map { c ->
                    when (c) {
                        OpenableColumns.DISPLAY_NAME, "title" -> title
                        "_id" -> 0
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
