// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewCredentials
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.testing.InMemorySecureStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** THE LibreView account (issue #177): kept once, in the secure store only; refused, forgotten, unreadable. */
class LibreViewLoginTest {
    private val secure = InMemorySecureStore()

    @Test
    fun `signed in once, read back by every user of it, signed out everywhere`() =
        runBlocking {
            val login = LibreViewLogin(secure)
            assertEquals(LibreViewAccountState.SignedOut, login.account.first())
            login.save(" me@example.com ", "pw-SYNTHETIC")
            assertEquals(LibreViewAccountState.SignedIn("me@example.com"), login.account.first())
            // another part of the app (or the next start) reads the same account
            val again = LibreViewLogin(secure)
            assertEquals(LibreViewCredentials("me@example.com", "pw-SYNTHETIC"), again.credentials())
            assertEquals(SecretName.LIBREVIEW_ACCOUNT, LibreViewLogin.NAME)
            assertFalse("never printed", again.credentials().toString().contains("SYNTHETIC"))

            login.signOut()
            assertEquals(LibreViewAccountState.SignedOut, login.current())
            assertNull(secure.read(LibreViewLogin.NAME))
            assertNull(LibreViewLogin(secure).credentials())
        }

    @Test
    fun `a refused password is forgotten and the email stays for the form, unless the account changed meanwhile`() =
        runBlocking {
            val login = LibreViewLogin(secure)
            login.save("me@example.com", "old")
            // an older answer about another password changes nothing
            login.rejected(LibreViewCredentials("me@example.com", "older"))
            assertEquals(LibreViewAccountState.SignedIn("me@example.com"), login.current())
            login.rejected(LibreViewCredentials("me@example.com", "old"))
            assertEquals(LibreViewAccountState.SignInAgain("me@example.com"), login.current())
            assertNull(login.credentials())
            assertEquals(LibreViewAccountState.SignInAgain("me@example.com"), LibreViewLogin(secure).current())
            login.save("me@example.com", "new")
            assertEquals(LibreViewCredentials("me@example.com", "new"), login.credentials())
        }

    @Test
    fun `an account the phone can't read asks to sign in again, never "signed out"`() =
        runBlocking {
            val broken =
                object : SecureStore {
                    override suspend fun read(name: String): ByteArray = throw SecretUnreadableException(name, transient = false, null)

                    override suspend fun write(
                        name: String,
                        bytes: ByteArray,
                    ) = Unit

                    override suspend fun delete(name: String) = Unit
                }
            val login = LibreViewLogin(broken)
            assertEquals(LibreViewAccountState.SignInAgain(""), login.current())
            assertNull(login.credentials())
        }
}
