// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.nfc

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android 16+ lets the person block an app's NFC tag intents (Android 17 mutes them until allowed): then no pen tap and
 * no sensor tap reaches Sukrara. The one check the pens and *Open Sukrara when you scan a sensor* both use, and the
 * one way to Android's screen that allows them. Read again whenever a screen comes back (it changes outside the app).
 */
object TagIntents {
    /** Debug builds only (the `PEN_TAGS` debug command): shows the blocked state on an emulator; real NFC is untouched. */
    val fakeBlocked = MutableStateFlow(false)

    fun blocked(context: Context): Boolean =
        fakeBlocked.value ||
            runCatching { NfcAdapter.getDefaultAdapter(context)?.isTagIntentAllowed == false }.getOrDefault(false)

    /** Android's tag-intent screen; Sukrara's App info when the phone has none. False when neither opens. */
    fun openAllow(context: Context): Boolean =
        start(context, Intent(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE)) ||
            start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    private fun start(
        context: Context,
        i: Intent,
    ): Boolean =
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
