// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import android.content.Context
import android.os.UserManager
import app.mauri.sukrara.core.data.json.SukraraJson
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The [LockedBootPlan] file in **device-protected** storage (`/data/user_de/…/files/locked-boot.json`), the one file
 * Sukrara can read before the first unlock after a restart. Written atomically (temp file, fsync, rename), and only
 * when the plan changed. Not in backups (the app has none) and never anything but [LockedBootPlan].
 */
class LockedBootStore(
    private val file: File,
) {
    private var written: LockedBootPlan? = null

    /** The saved plan; null when there is none (never set up, or written by nothing yet). */
    @Synchronized
    fun read(): LockedBootPlan? {
        if (!file.exists()) return null
        return try {
            SukraraJson.decodeFromString(LockedBootPlan.serializer(), file.readText())
        } catch (e: SerializationException) {
            LockedBootPlan.UNREADABLE
        } catch (e: IllegalArgumentException) {
            LockedBootPlan.UNREADABLE
        } catch (e: IOException) {
            LockedBootPlan.UNREADABLE
        }
    }

    /** Saves [plan] if it differs from what this process last wrote. Throws [IOException] when the disk refuses. */
    @Synchronized
    fun write(plan: LockedBootPlan) {
        if (plan == written) return
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(SukraraJson.encodeToString(LockedBootPlan.serializer(), plan).toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) throw IOException("rename failed")
        written = plan
    }

    companion object {
        const val FILE = "locked-boot.json"

        /** The store in [context]'s device-protected storage (readable before the first unlock). */
        fun of(context: Context): LockedBootStore =
            LockedBootStore(File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, FILE))
    }
}

/**
 * Whether the person has unlocked the phone since it started (credential-encrypted storage is readable). False only
 * before the first unlock after a restart; anything but [LockedBootReceiver] and the sound provider then stays off.
 */
val Context.userUnlocked: Boolean
    get() = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
