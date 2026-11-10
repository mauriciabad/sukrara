// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** A copied secret is marked sensitive and leaves the clipboard a minute later, unless something else was copied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretClipboardTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cm = app.getSystemService(ClipboardManager::class.java)

    private fun after(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun `the secret is marked sensitive and cleared after a minute`() {
        SecretClipboard.copy(app, "Nightscout API secret", "0f1e")
        assertEquals("0f1e", cm.primaryClip!!.getItemAt(0).text)
        assertTrue(cm.primaryClipDescription!!.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        after(SecretClipboard.CLEAR_AFTER_MS - 1_000)
        assertTrue(cm.hasPrimaryClip())
        after(1_000)
        assertFalse(cm.primaryClip?.getItemAt(0)?.text == "0f1e")
    }

    @Test fun `something copied since is left alone`() {
        SecretClipboard.copy(app, "Nightscout API secret", "0f1e")
        cm.setPrimaryClip(ClipData.newPlainText("note", "pasta 40 g"))
        after(SecretClipboard.CLEAR_AFTER_MS)
        assertEquals("pasta 40 g", cm.primaryClip!!.getItemAt(0).text)
    }
}
