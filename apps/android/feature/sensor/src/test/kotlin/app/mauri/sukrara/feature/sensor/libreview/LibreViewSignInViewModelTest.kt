// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sensor.libreview

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.sensor.libreview.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibreViewSignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeAccount(var answer: SignInResult) : LibreViewPort {
        override val account = MutableStateFlow<LibreViewAccountState>(LibreViewAccountState.SignedOut)
        var calls = 0

        override suspend fun signIn(email: String, password: String): SignInResult {
            calls++
            if (answer is SignInResult.SignedIn) account.value = LibreViewAccountState.SignedIn(email.trim())
            return answer
        }

        override suspend fun signOut() {
            account.value = LibreViewAccountState.SignedOut
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun result(answer: SignInResult): Pair<FakeAccount, SignInUi> {
        val account = FakeAccount(answer)
        val vm = LibreViewSignInViewModel(account)
        var ui: SignInUi? = null
        runTest(dispatcher) {
            runCurrent()
            vm.signIn(" me@example.com ", "secret")
            runCurrent()
            ui = vm.ui.value
        }
        return account to ui!!
    }

    @Test fun success() = assertEquals(SignInUi.SignedIn("me@example.com"), result(SignInResult.SignedIn(3078366504)).second)

    @Test fun `wrong password`() =
        assertEquals(SignInUi.SignedOut(SignInError.WrongPassword, "me@example.com"), result(SignInResult.BadCredentials).second)

    @Test fun `terms to accept in the LibreView app`() =
        assertEquals(SignInUi.SignedOut(SignInError.AcceptTerms, "me@example.com"), result(SignInResult.AcceptTerms).second)

    @Test fun offline() =
        assertEquals(SignInUi.SignedOut(SignInError.Failed, "me@example.com"), result(SignInResult.Failed("UnknownHostException")).second)

    @Test fun `an empty form sends nothing`() =
        runTest(dispatcher) {
            val account = FakeAccount(SignInResult.BadCredentials)
            val vm = LibreViewSignInViewModel(account)
            vm.signIn("", "")
            vm.signIn("me@example.com", "")
            runCurrent()
            assertEquals(0, account.calls)
        }

    @Test fun `signed in elsewhere shows who, and signing out signs out everywhere`() =
        runTest(dispatcher) {
            val account =
                FakeAccount(SignInResult.SignedIn(1)).apply {
                    this.account.value =
                        LibreViewAccountState.SignedIn("me@example.com")
                }
            val vm = LibreViewSignInViewModel(account)
            runCurrent()
            assertEquals(SignInUi.SignedIn("me@example.com"), vm.ui.value)
            vm.signOut()
            runCurrent()
            assertEquals(SignInUi.SignedOut(), vm.ui.value)
            assertEquals(LibreViewAccountState.SignedOut, account.account.value)
        }

    @Test fun `a password LibreView no longer accepts asks again with the email filled in`() =
        runTest(dispatcher) {
            val account = FakeAccount(SignInResult.BadCredentials)
            account.account.value = LibreViewAccountState.SignInAgain("me@example.com")
            val vm = LibreViewSignInViewModel(account)
            runCurrent()
            assertEquals(SignInUi.SignedOut(email = "me@example.com", again = true), vm.ui.value)
            vm.signIn("me@example.com", "still-wrong")
            runCurrent()
            assertEquals(SignInUi.SignedOut(SignInError.WrongPassword, "me@example.com", again = true), vm.ui.value)
            account.answer = SignInResult.SignedIn(1)
            vm.signIn("me@example.com", "new")
            runCurrent()
            assertEquals(SignInUi.SignedIn("me@example.com"), vm.ui.value)
        }
}
