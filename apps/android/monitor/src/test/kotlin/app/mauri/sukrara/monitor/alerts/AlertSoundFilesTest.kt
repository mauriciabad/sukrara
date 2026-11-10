// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.net.Uri
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.SoundSettings
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.monitor.AlertSoundProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * #44: every alert sound is a direct `R.raw` reference, and System UI's player can only read them. The table holds
 * exactly the contract's files, and every name the picker can return opens (or is the system sound).
 */
@RunWith(RobolectricTestRunner::class)
class AlertSoundFilesTest {
    @Test fun theTableIsTheContractsFiles() {
        val files =
            Contracts.root
                .resolve("alerts/sounds/android/raw")
                .listFiles { f -> f.name.endsWith(".ogg") }
                .orEmpty()
                .map { it.name.removeSuffix(".ogg") }
                .toSet()
        assertEquals(files, AlertSoundFiles.names)
    }

    @Test fun everyPickHasAFile() {
        val picker = AlertSoundPicker(random = { it.first })
        var t = 0L
        for (pack in AlertSoundPicker.PACKS + AlertSoundPicker.NONE + "unknown") {
            for (kind in AlertKind.entries) {
                for (shuffle in listOf(true, false)) {
                    t += AlertSoundPicker.HOLD_MS
                    val name = picker.pick(kind, SoundSettings(pack = pack, shuffle = shuffle), t)
                    assertNotNull("$pack / $kind → $name", AlertSoundFiles.of(name))
                }
            }
        }
        assertEquals(null, AlertSoundFiles.of(AlertSoundPicker.SYSTEM))
        assertEquals(null, AlertSoundFiles.of("config_statusBarIcons"))
    }

    @Test fun theProviderOnlyReads() {
        val provider = Robolectric.setupContentProvider(AlertSoundProvider::class.java, "test.alertsound")
        val uri = Uri.parse("content://test.alertsound/${AlertKind.LOW.wire}")
        for (mode in listOf("w", "rw", "wa", "rwt")) {
            assertThrows(mode, SecurityException::class.java) { provider.openAssetFile(uri, mode) }
        }
        assertTrue(runCatching { provider.openAssetFile(uri, "r") }.isSuccess)
    }
}
