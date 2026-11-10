// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.mauri.sukrara.core.model.NavTargets
import java.security.SecureRandom
import java.util.HexFormat

/**
 * The new-sensor flow's one way in ([NavTargets.SENSOR_SETUP_ACTIVITY]): Sukrara's notifications, widgets and the
 * NFC tap open this non-exported trampoline, so no other app can reach it. It opens [MainActivity] with the flow's
 * action signed by [SensorSetupGate]; [MainActivity] is exported (launcher, invite links), and drops the action
 * without the signature.
 */
class SensorSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            val fromTap = intent?.getBooleanExtra(NavTargets.EXTRA_FROM_SENSOR_TAP, false) ?: false
            startActivity(SensorSetupGate.sign(launch, fromTap).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}

/** A request for the new-sensor flow; [fromSensorTap]: a sensor was tapped (NFC) while Sukrara wasn't in front. */
data class NewSensorRequest(
    val fromSensorTap: Boolean,
)

/**
 * Tells Sukrara's own new-sensor requests from any app's: [SensorSetupActivity] signs the intent with a token made
 * for this process (never stored, never leaves the app), and [MainActivity] opens the flow only for an intent that
 * carries it.
 */
object SensorSetupGate {
    private const val EXTRA_TOKEN = "app.mauri.sukrara.extra.SENSOR_SETUP_TOKEN"
    private const val TOKEN_BYTES = 16

    private val token: String = HexFormat.of().formatHex(ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes))

    /** [launch] asking for the new-sensor flow, with this process's token. */
    fun sign(
        launch: Intent,
        fromSensorTap: Boolean,
    ): Intent =
        launch
            .setAction(NavTargets.ACTION_SENSOR_SETUP)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(NavTargets.EXTRA_FROM_SENSOR_TAP, fromSensorTap)

    /** The request [intent] carries, or null when it isn't one of Sukrara's own (another app's, or none). */
    fun request(intent: Intent?): NewSensorRequest? {
        if (intent?.action != NavTargets.ACTION_SENSOR_SETUP) return null
        // another app's extras may not even unparcel: that is "not ours" too
        val signed = runCatching { intent.getStringExtra(EXTRA_TOKEN) == token }.getOrDefault(false)
        if (!signed) return null
        return NewSensorRequest(fromSensorTap = intent.getBooleanExtra(NavTargets.EXTRA_FROM_SENSOR_TAP, false))
    }
}
