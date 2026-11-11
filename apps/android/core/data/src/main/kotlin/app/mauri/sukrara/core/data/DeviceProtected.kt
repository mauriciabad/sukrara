// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
import android.os.UserManager
import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.settings.SettingsSpec

/**
 * What Sukrara keeps in **device-protected** storage (`/data/user_de/…`, docs/30-direct-boot.md, #160): only what the
 * wearer's monitoring needs before the first unlock after a restart (the sensor link, the hub, the alerts and the
 * glucose notification). Everything else (the sharing identity and its state, the history, the treatments, uploads and
 * their accounts, the other settings) is credential-encrypted: unreadable until the first unlock, and [DataModule]
 * refuses it until then ([LockedStorageException]) instead of reading it as missing.
 *
 * The trade-off (docs/ARCHITECTURE.md "Android storage"): what is listed here is protected by the app sandbox and, for
 * the sensor pairing, a Keystore key without user authentication, not by the screen-lock secret.
 */
object DeviceProtected {
    /**
     * Settings stores: the alert settings, the unit and glucose source, the graph (ranges and card), the sensor's
     * reminders and countdown, the status bar (the glucose notification's chip, icon and lock-screen value).
     */
    val SETTINGS: Set<String> =
        setOf(
            SettingsSpec.Stores.ALERTS,
            SettingsSpec.Stores.SETTINGS,
            SettingsSpec.Stores.GRAPH,
            SettingsSpec.Stores.SENSOR,
            SettingsSpec.Stores.STATUSBAR,
        )

    /** Secrets: the sensor pairing (its Keystore key has no user authentication, so it opens before the first unlock). */
    val SECRETS: Set<String> = setOf(SecretName.SENSOR)

    /** Typed state: the role (both copies), when monitoring started, the alert runtime's record, the sensor-end memory. */
    val STATES: Set<String> = setOf("role", "role-backup", "monitor-record", "alert_state", "sensor_end")

    /** The last 3 h of this phone's own readings and those read before the unlock ([RecentReadings]). */
    const val RECENT_READINGS = "recent-readings.json"
    const val LOCKED_READINGS = "locked-readings.csv"
}

/** A credential-encrypted store was used before the first unlock after a restart: it can't be read or written then. */
class LockedStorageException(
    what: String,
) : IllegalStateException("$what is in credential-encrypted storage, locked until the first unlock")

/**
 * Whether the person has unlocked the phone since it started (credential-encrypted storage is readable). False only
 * before the first unlock after a restart.
 */
val Context.userUnlocked: Boolean
    get() = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
