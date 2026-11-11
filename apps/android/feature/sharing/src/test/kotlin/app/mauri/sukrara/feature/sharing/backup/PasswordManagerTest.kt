// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.Got
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.SaveResult
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.Saved
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every way Credential Manager can answer a save or a get, and what the person is told (Backup, restore). */
class PasswordManagerTest {
    @Test
    fun `a save that goes through is saved`() =
        runTest {
            assertEquals(SaveResult(Saved.Saved), PasswordManager.save {})
        }

    @Test
    fun `closing the sheet is cancelled, not an error`() =
        runTest {
            val r = PasswordManager.save { throw CreateCredentialCancellationException("closed") }
            assertEquals(Saved.Cancelled, r.saved)
        }

    @Test
    fun `no password manager for apps leads to settings, not a failure`() =
        runTest {
            for (e in listOf(
                CreateCredentialNoCreateOptionException(),
                CreateCredentialProviderConfigurationException(),
                CreateCredentialUnsupportedException(),
            )) {
                assertEquals(e.javaClass.simpleName, Saved.NoPasswordManager, PasswordManager.save { throw e }.saved)
            }
            assertEquals(
                SaveResult(Saved.NoPasswordManager, "TYPE_NO_CREATE_OPTIONS"),
                PasswordManager.save { throw CreateCredentialNoCreateOptionException() },
            )
        }

    @Test
    fun `an error from the password manager is failed, with its type and code for the log only`() =
        runTest {
            // the message may quote the request: only the type's last part and a bracketed number are kept
            val r = PasswordManager.save { throw CreateCredentialUnknownException("[28434] Sukrara · npub1abc… nsec1secret") }
            assertEquals(SaveResult(Saved.Failed, "TYPE_UNKNOWN 28434"), r)
            assertFalse(r.detail!!.contains("nsec"))
            val custom = PasswordManager.save { throw CreateCredentialCustomException("com.example.vault.LOCKED", "vault locked") }
            assertEquals(SaveResult(Saved.Failed, "LOCKED"), custom)
        }

    @Test
    fun `an interrupted save is asked once more`() =
        runTest {
            var calls = 0
            val r =
                PasswordManager.save {
                    if (++calls == 1) throw CreateCredentialInterruptedException()
                }
            assertEquals(Saved.Saved, r.saved)
            assertEquals(2, calls)
            var again = 0
            val twice =
                PasswordManager.save {
                    again++
                    throw CreateCredentialInterruptedException()
                }
            assertEquals(SaveResult(Saved.Failed, "TYPE_INTERRUPTED"), twice)
            assertEquals(2, again)
        }

    @Test
    fun `restore - a password, none saved, cancelled, failed, interrupted once`() =
        runTest {
            val got = PasswordManager.get { "nsec1x" }
            assertTrue(got is Got.Secret && got.text == "nsec1x")
            assertEquals("Secret", got.toString())
            assertEquals(Got.NoneSaved, PasswordManager.get { throw NoCredentialException() })
            assertEquals(Got.Cancelled, PasswordManager.get { throw GetCredentialCancellationException() })
            assertEquals(Got.Failed, PasswordManager.get { throw GetCredentialUnknownException() })
            // another kind of credential than a password
            assertEquals(Got.Failed, PasswordManager.get { null })
            var calls = 0
            val retried = PasswordManager.get { if (++calls == 1) throw GetCredentialInterruptedException() else "nsec1y" }
            assertTrue(retried is Got.Secret)
            assertEquals(2, calls)
        }
}
