// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import app.mauri.sukrara.feature.sharing.FakeOwnerSharing
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.SaveResult
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.Saved
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Backup › Save to password manager from the tap to every end, and what the diagnostic log gets. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val fake = FakeOwnerSharing()
    private val vm = BackupViewModel(fake)

    @Test
    fun `saved in one go - the password manager gets the entry, the log only that it worked`() {
        var asked: Pair<String, String>? = null
        vm.save { id, secret -> SaveResult(Saved.Saved).also { asked = id to secret } }
        assertEquals(BackupUi.Saved, vm.ui.value)
        assertEquals("Sukrara · npub1test" to "nsec1test", asked)
        assertEquals(listOf("password manager: saved"), fake.logged)
    }

    @Test
    fun `every end of the system's sheet has its own state, and the log names the type, never the secret`() {
        val ends =
            listOf(
                SaveResult(Saved.Cancelled, "TYPE_USER_CANCELED") to BackupUi.Cancelled,
                SaveResult(Saved.NoPasswordManager, "TYPE_NO_CREATE_OPTIONS") to BackupUi.NoPasswordManager,
                SaveResult(Saved.Failed, "TYPE_UNKNOWN 28434") to BackupUi.Failed,
            )
        for ((result, ui) in ends) {
            vm.save { _, _ -> result }
            assertEquals(ui, vm.ui.value)
        }
        assertEquals(
            listOf(
                "password manager: not saved, the sheet was closed (TYPE_USER_CANCELED)",
                "password manager: none takes passwords (TYPE_NO_CREATE_OPTIONS)",
                "password manager: failed (TYPE_UNKNOWN 28434)",
            ),
            fake.logged,
        )
        assertTrue(fake.logged.none { "nsec" in it })
    }

    @Test
    fun `a tap while the sheet is open does nothing, then try again asks once more`() {
        val answer = CompletableDeferred<SaveResult>()
        vm.save { _, _ -> answer.await() }
        assertEquals(BackupUi.Asking, vm.ui.value)
        vm.save { _, _ -> SaveResult(Saved.Saved) }
        assertEquals(listOf("passwordManager"), fake.calls)
        answer.complete(SaveResult(Saved.Failed))
        assertEquals(BackupUi.Failed, vm.ui.value)
        // Try again (or coming back from Android's settings)
        vm.save { _, _ -> SaveResult(Saved.Saved) }
        assertEquals(BackupUi.Saved, vm.ui.value)
        assertEquals(listOf("passwordManager", "passwordManager"), fake.calls)
    }

    @Test
    fun `without an identity nothing is shown (the page says so itself), a broken identity is a failure`() {
        fake.hasIdentity = false
        vm.save { _, _ -> error("never asked") }
        assertEquals(BackupUi.Idle, vm.ui.value)
        fake.hasIdentity = true
        fake.saveThrows = IOException("keystore")
        vm.save { _, _ -> SaveResult(Saved.Saved) }
        assertEquals(BackupUi.Failed, vm.ui.value)
        assertEquals(listOf("password manager: IOException"), fake.logged)
    }

    @Test
    fun `a recovery code made instead says to paste it, and counts as backed up`() {
        vm.save { _, _ -> SaveResult(Saved.NoPasswordManager) }
        vm.recoverySaved()
        assertEquals(BackupUi.CodeCopied, vm.ui.value)
        assertTrue("recoverySaved" in fake.calls)
    }
}
