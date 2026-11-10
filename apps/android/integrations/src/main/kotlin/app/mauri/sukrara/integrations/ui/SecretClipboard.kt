// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies a secret marked sensitive (Android's clipboard preview hides it) and takes it off the clipboard
 * [CLEAR_AFTER_MS] later, so it doesn't stay there for every app the person opens next. A copy made since (the same
 * label and time are the secret's) is left alone; when Android hides the clipboard from the app (in the background)
 * the secret goes anyway. One per process: the clear runs after the page is gone too.
 */
object SecretClipboard {
    const val CLEAR_AFTER_MS = 60_000L

    private val main = Handler(Looper.getMainLooper())
    private val token = Any()
    private var copied: ClipDescription? = null

    fun copy(
        context: Context,
        label: String,
        text: String,
    ) {
        val cm = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        // the copy's own description carries the time Android gave it
        copied = runCatching { cm.primaryClipDescription }.getOrNull() ?: clip.description
        main.removeCallbacksAndMessages(token)
        main.postDelayed({ clear(cm) }, token, CLEAR_AFTER_MS)
    }

    private fun clear(cm: ClipboardManager) {
        val mine = copied ?: return
        copied = null
        val now = runCatching { cm.primaryClipDescription }.getOrNull()
        if (now != null && !(now.label == mine.label && now.timestamp == mine.timestamp)) return
        runCatching { cm.clearPrimaryClip() }
    }
}
