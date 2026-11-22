// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone is locked, for the wrist (#248, contracts/alerts/SPEC.md §1 "On the wrist"): the watches say an
 * alert only while the phone isn't in the person's hand. Locked = the screen is off or the lock screen is up (Android's
 * own *user present*, as its notification cooldown counts it: false from SCREEN_OFF, true from USER_PRESENT), so a
 * phone without a lock screen counts as locked with its screen off. Read again on screen off, screen on and unlock
 * (they can't be declared in the manifest: registered while [locked] is collected), so the payload, and with it the
 * push, follows within the link's settle time.
 */
class PhoneLock(
    context: Context,
) {
    private val context = context.applicationContext

    val locked: Flow<Boolean> =
        callbackFlow {
            fun read() = trySend(runCatching { now(context) }.getOrDefault(true))
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        c: Context,
                        intent: Intent,
                    ) {
                        read()
                    }
                }
            val filter =
                IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            read()
            awaitClose { runCatching { context.unregisterReceiver(receiver) } }
        }.distinctUntilChanged()

    companion object {
        /** The rule (JVM-tested): locked unless the screen is on and no lock screen is up. */
        fun locked(
            interactive: Boolean,
            keyguard: Boolean,
        ): Boolean = !interactive || keyguard

        fun now(context: Context): Boolean =
            locked(
                interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: false,
                keyguard = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: true,
            )
    }
}
