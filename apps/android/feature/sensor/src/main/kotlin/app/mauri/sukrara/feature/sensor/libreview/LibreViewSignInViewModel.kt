// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sensor.libreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.sensor.libreview.SignInResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** THE LibreView account of the app (`LibreViewLogin`): signed in once here, used by the sensor, the upload and the import. */
interface LibreViewPort {
    val account: Flow<LibreViewAccountState>

    suspend fun signIn(email: String, password: String): SignInResult

    /** Signs out everywhere. */
    suspend fun signOut()
}

enum class SignInError { WrongPassword, AcceptTerms, Failed }

sealed interface SignInUi {
    /** The saved account is being read (no form flashes for a signed-in person). */
    data object Loading : SignInUi

    /**
     * The form. [email] fills it in (the account LibreView stopped accepting); [again] = it asks because LibreView no
     * longer accepts the saved password.
     */
    data class SignedOut(
        val error: SignInError? = null,
        val email: String = "",
        val again: Boolean = false,
    ) : SignInUi

    data object Working : SignInUi

    data class SignedIn(val email: String) : SignInUi
}

/**
 * THE LibreView sign-in and account screen (issue #177: one sign-in for the app). The password goes from the form to
 * the one call and, once LibreView accepted it, into the Keystore-encrypted account, never into saved state. Signing out
 * forgets the account everywhere; a running sensor keeps going.
 */
class LibreViewSignInViewModel(private val port: LibreViewPort) : ViewModel() {
    private val _ui = MutableStateFlow<SignInUi>(SignInUi.Loading)
    val ui: StateFlow<SignInUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            port.account.collect { account -> if (_ui.value != SignInUi.Working) _ui.value = uiOf(account) }
        }
    }

    val canSubmit: (email: String, password: String) -> Boolean = { e, p -> e.contains('@') && p.isNotEmpty() }

    fun signIn(email: String, password: String) {
        if (_ui.value == SignInUi.Working || !canSubmit(email, password)) return
        // the saved account LibreView refused stays refused until a sign-in works
        val again = (_ui.value as? SignInUi.SignedOut)?.again == true
        _ui.value = SignInUi.Working
        viewModelScope.launch {
            val e = email.trim()
            _ui.value =
                when (port.signIn(email, password)) {
                    is SignInResult.SignedIn -> SignInUi.SignedIn(e)

                    SignInResult.BadCredentials -> SignInUi.SignedOut(SignInError.WrongPassword, e, again)

                    SignInResult.AcceptTerms -> SignInUi.SignedOut(SignInError.AcceptTerms, e, again)

                    // offline, a server error, an answer it can't read: "couldn't sign in", never a guess at why
                    is SignInResult.Failed -> SignInUi.SignedOut(SignInError.Failed, e, again)
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            port.signOut()
            _ui.value = SignInUi.SignedOut()
        }
    }

    private fun uiOf(account: LibreViewAccountState): SignInUi =
        when (account) {
            LibreViewAccountState.SignedOut -> SignInUi.SignedOut()
            is LibreViewAccountState.SignedIn -> SignInUi.SignedIn(account.email)
            is LibreViewAccountState.SignInAgain -> SignInUi.SignedOut(email = account.email, again = true)
        }
}
