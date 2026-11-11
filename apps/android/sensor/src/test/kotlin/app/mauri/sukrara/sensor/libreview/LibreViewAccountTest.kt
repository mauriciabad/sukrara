// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.libreview

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewCredentials
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.sensor.pairing.ReceiverIdState
import app.mauri.sukrara.sensor.pairing.ReceiverIds
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class LibreViewAccountTest {
    private val cases =
        Json
            .parseToJsonElement(Contracts.file("sensor/libreview-auth.json").readText())
            .jsonObject
            .getValue("cases")
            .jsonArray
            .map { it.jsonObject }

    private fun body(name: String) = cases.first { it["name"]!!.jsonPrimitive.content == name }.getValue("body").toString()

    @Test
    fun everyFixtureCaseParsesAsExpected() {
        for (c in cases) {
            val expect = c.getValue("expect").jsonObject
            val answer = LibreViewAccount.parse(c.getValue("body").toString())
            val name = c["name"]!!.jsonPrimitive.content
            when (expect["kind"]!!.jsonPrimitive.content) {
                "account" -> {
                    val id = (answer as LoginAnswer.Account).accountId
                    assertEquals(name, expect["accountId"]!!.jsonPrimitive.content, id)
                    assertEquals(name, expect["receiverId"]!!.jsonPrimitive.long, ReceiverIds.fromAccountId(id))
                }

                "redirect" -> {
                    assertEquals(name, LoginAnswer.Redirect(expect["region"]!!.jsonPrimitive.content), answer)
                }

                "badCredentials" -> {
                    assertEquals(name, LoginAnswer.BadCredentials, answer)
                }

                "acceptTerms" -> {
                    assertEquals(name, LoginAnswer.AcceptTerms, answer)
                }

                "failed" -> {
                    assertTrue(name, answer is LoginAnswer.Failed)
                }
            }
        }
        assertTrue(LibreViewAccount.parse("<html>") is LoginAnswer.Failed)
    }

    /**
     * A local fake LibreLinkUp: the sign-in keeps the receiver ID in the plain state and the account (email and
     * password) only in the secure store, for the upload and the import; signing out forgets both (issue #177).
     */
    @Test
    fun signInKeepsTheAccountOnceInTheSecureStore() =
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val seen = ArrayList<String>()
            server.createContext("/llu/auth/login") { ex ->
                seen += ex.requestBody.readBytes().decodeToString()
                val out = body("signed in").encodeToByteArray()
                ex.sendResponseHeaders(200, out.size.toLong())
                ex.responseBody.use { it.write(out) }
            }
            server.start()
            try {
                val state = InMemoryStateStore(ReceiverIdState(own = 99L))
                val secure = InMemorySecureStore()
                val login = LibreViewLogin(secure)
                val account = LibreViewAccount(state, login, OkHttpClient(), Dispatchers.IO, "127.0.0.1:${server.address.port}", "http")
                assertEquals(LibreViewAccountState.SignedOut, account.account.first())
                assertFalse(account.hasReceiverId())
                val password = "pa55-word-SYNTHETIC"
                assertEquals(SignInResult.SignedIn(3078366504L), account.signIn(" owner@example.com ", password))
                assertTrue(seen.single().contains(password)) // sent once to the server
                val stored = state.data.first()
                assertEquals(ReceiverIdState(own = 99L, account = 3078366504L), stored)
                assertFalse(stored.toString().contains(password))
                assertEquals(LibreViewAccountState.SignedIn("owner@example.com"), account.account.first())
                assertEquals(LibreViewCredentials("owner@example.com", password), login.credentials())
                assertTrue(secure.read(LibreViewLogin.NAME) != null)
                assertTrue(account.hasReceiverId())

                account.signOut()
                assertEquals(ReceiverIdState(own = 99L), state.data.first()) // Sukrara's own ID survives sign-out
                assertEquals(LibreViewAccountState.SignedOut, account.account.first())
                assertNull(secure.read(LibreViewLogin.NAME))
                assertNull(login.credentials())
            } finally {
                server.stop(0)
            }
        }

    /** A receiver ID from before the account was kept (or a damaged account file) is no account: sign in once. */
    @Test
    fun aReceiverIdWithoutAnAccountIsNotUsed() =
        runBlocking {
            val login = LibreViewLogin(InMemorySecureStore())
            val account =
                LibreViewAccount(InMemoryStateStore(ReceiverIdState(own = 99L, account = 5L)), login, OkHttpClient(), Dispatchers.IO)
            assertNull(account.receiverId.first())
            login.save("owner@example.com", "pw")
            assertEquals(5L, account.receiverId.first())
            // LibreView refused the saved password (the upload found out): the receiver ID still starts sensors
            login.rejected(LibreViewCredentials("owner@example.com", "pw"))
            assertEquals(LibreViewAccountState.SignInAgain("owner@example.com"), account.account.first())
            assertEquals(5L, account.receiverId.first())
        }

    @Test
    fun badCredentialsAndUnreachableServerAreResultsNotCrashes() =
        runBlocking {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/llu/auth/login") { ex ->
                val out = body("wrong email or password").encodeToByteArray()
                ex.sendResponseHeaders(200, out.size.toLong())
                ex.responseBody.use { it.write(out) }
            }
            server.start()
            val state = InMemoryStateStore(ReceiverIdState())
            val login = LibreViewLogin(InMemorySecureStore())
            try {
                val account = LibreViewAccount(state, login, OkHttpClient(), Dispatchers.IO, "127.0.0.1:${server.address.port}", "http")
                assertEquals(SignInResult.BadCredentials, account.signIn("a@b.c", "x"))
            } finally {
                server.stop(0)
            }
            val gone = LibreViewAccount(state, login, OkHttpClient(), Dispatchers.IO, "127.0.0.1:${server.address.port}", "http")
            assertTrue(gone.signIn("a@b.c", "x") is SignInResult.Failed)
            assertEquals(ReceiverIdState(), state.data.first())
            assertEquals("nothing kept", LibreViewAccountState.SignedOut, login.current())
        }
}
