// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import android.app.Activity
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.share.owner.OwnerShareState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Backup's save card: every end of *Save to password manager* has its words and its way on, right on the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val sharing =
        OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), backupPublishedAt = 1_761_400_000, recoveryExportedAt = 0)

    private var ui by mutableStateOf(BackupUi.Idle)
    private var state by mutableStateOf(sharing)
    private var saves = 0
    private var kept = 0

    private fun show() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupScreen(state, ui, onSave = { saves++ }, recoveryCode = { "ncryptsec1test" }, onRecoveryKeep = { kept++ })
                }
            }
        }
    }

    @Test
    fun `the row saves in one tap and waits while the system's sheet is open`() {
        show()
        compose.onNodeWithText("Save to password manager").performClick()
        assertEquals(1, saves)
        ui = BackupUi.Asking
        compose.onNodeWithText("Waiting for your password manager…").assertExists()
        compose.onNode(hasText("Save to password manager") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Save to password manager").performClick()
        assertEquals(1, saves)
        ui = BackupUi.Saved
        compose
            .onNodeWithText("Saved to your password manager. On a new phone, choose Restore from password manager when Sukrara sets up.")
            .assertExists()
    }

    @Test
    fun `no password manager - open Android's settings, and coming back asks again`() {
        ui = BackupUi.NoPasswordManager
        show()
        compose.onNodeWithText("No password manager can save it").performScrollTo()
        compose.onNodeWithText("Open settings").performScrollTo().performClick()
        val activity = shadowOf(compose.activity)
        val started = activity.nextStartedActivityForResult
        assertEquals(Settings.ACTION_CREDENTIAL_PROVIDER, started.intent.action)
        assertEquals("package:" + compose.activity.packageName, started.intent.dataString)
        assertEquals(0, saves)
        // back from the settings page (Back gives no result): the password manager is asked again
        activity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        compose.waitForIdle()
        assertEquals(1, saves)
    }

    @Test
    fun `no password manager - a recovery code instead opens right here`() {
        ui = BackupUi.NoPasswordManager
        show()
        compose.onNodeWithText("Make a recovery code instead").performScrollTo().performClick()
        compose.onNodeWithText("Choose a password. The code only works with it; keep both apart.").assertExists()
    }

    @Test
    fun `failed - try again, and a failed save again says the earlier copy stays`() {
        ui = BackupUi.Failed
        show()
        compose.onNodeWithText("Your password manager didn't save it").performScrollTo()
        compose.onNodeWithText("Nothing was saved. Try again, or choose another password manager in Android's settings.").assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, saves)
        compose.onNodeWithText("Open settings").assertExists()
        compose.onNodeWithText("Make a recovery code instead").assertExists()
        state = sharing.copy(passwordManagerSavedAt = 1_761_450_000)
        compose.onNodeWithText("Save to password manager again").assertExists()
        compose.onNode(hasText("Nothing changed: the copy it saved on", substring = true)).assertExists()
    }

    @Test
    fun `cancelled just says so, and the row is there to try again`() {
        ui = BackupUi.Cancelled
        show()
        compose.onNodeWithText("Not saved: you closed the password manager.").assertExists()
        compose.onNodeWithText("Open settings").assertDoesNotExist()
        compose.onNodeWithText("Save to password manager").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `a copied recovery code says to paste it now`() {
        ui = BackupUi.CodeCopied
        show()
        compose
            .onNodeWithText("Recovery code copied. Paste it now somewhere you keep important things, away from its password.")
            .assertExists()
        assertEquals(0, kept)
    }
}
