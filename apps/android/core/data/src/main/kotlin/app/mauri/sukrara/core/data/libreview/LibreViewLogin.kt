// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.libreview

import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the app's one LibreView account stands. */
sealed interface LibreViewAccountState {
    /** No account: whatever needs one asks for it (the one sign-in screen). */
    data object SignedOut : LibreViewAccountState

    data class SignedIn(
        val email: String,
    ) : LibreViewAccountState

    /**
     * LibreView no longer accepts the saved password (it was changed), or it can't be read on this phone any more:
     * [email] ("" when unknown) fills the form; whatever needs the account asks to sign in again. A sensor already
     * started with the account's receiver ID keeps it.
     */
    data class SignInAgain(
        val email: String,
    ) : LibreViewAccountState
}

/** The email of a signed-in or signed-out-by-LibreView account; null when there is none. */
val LibreViewAccountState.email: String?
    get() =
        when (this) {
            LibreViewAccountState.SignedOut -> null
            is LibreViewAccountState.SignedIn -> email
            is LibreViewAccountState.SignInAgain -> email
        }

/** The email and password LibreView signs in with. Never logged, never in a state file or a setting. */
class LibreViewCredentials(
    val email: String,
    val password: String,
) {
    override fun equals(other: Any?) = other is LibreViewCredentials && other.email == email && other.password == password

    override fun hashCode() = 31 * email.hashCode() + password.hashCode()

    override fun toString() = "LibreViewCredentials(***)"
}

/**
 * THE LibreView account of the app (issue #177: the owner signed in three times): signed in once, on the one sign-in
 * screen (from setup's sensor step, a new sensor, Settings › Sensor or Share with apps › LibreView), and used by
 * everything that talks to LibreView: starting and taking over sensors (its receiver ID), the LibreView upload (its own
 * login with these credentials, again by itself when its token stops working) and Import history (the account to
 * download the file with). Signing out here signs out everywhere.
 *
 * The email and password are kept only in the Keystore-encrypted secure store ([SecretName.LIBREVIEW_ACCOUNT]); when
 * LibreView refuses the password, it is forgotten and the email stays for the form ([LibreViewAccountState.SignInAgain]).
 */
class LibreViewLogin(
    private val secure: SecureStore,
) {
    @Serializable
    private data class Saved(
        val email: String,
        /** null: LibreView refused it; sign in again. */
        val password: String? = null,
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow<LibreViewAccountState?>(null)

    /** The account; read from the secure store on first collection. */
    val account: Flow<LibreViewAccountState> =
        flow {
            load()
            emitAll(state.filterNotNull())
        }

    suspend fun current(): LibreViewAccountState {
        load()
        return checkNotNull(state.value)
    }

    suspend fun load() {
        if (state.value != null) return
        mutex.withLock { if (state.value == null) state.value = stateOf(readSaved()) }
    }

    /** The email and password to sign in with; null when signed out, refused, or unreadable now. */
    suspend fun credentials(): LibreViewCredentials? =
        mutex.withLock {
            val saved = readSaved()
            state.value = stateOf(saved)
            saved.getOrNull()?.let { s -> s.password?.let { LibreViewCredentials(s.email, it) } }
        }

    /** Keeps [email] and [password] (LibreView just accepted them): every feature uses them from now on. */
    suspend fun save(
        email: String,
        password: String,
    ) = mutex.withLock {
        val e = email.trim()
        secure.write(NAME, json.encodeToString(Saved.serializer(), Saved(e, password)).encodeToByteArray())
        state.value = LibreViewAccountState.SignedIn(e)
    }

    /**
     * LibreView refused [used]: the password is forgotten, the email stays for the form. Ignored when the account
     * changed meanwhile (a new sign-in isn't undone by an older answer).
     */
    suspend fun rejected(used: LibreViewCredentials) =
        mutex.withLock {
            val saved = readSaved().getOrNull() ?: return@withLock
            if (saved.email != used.email || saved.password != used.password) return@withLock
            secure.write(NAME, json.encodeToString(Saved.serializer(), Saved(saved.email)).encodeToByteArray())
            state.value = LibreViewAccountState.SignInAgain(saved.email)
        }

    /** Forgets the account everywhere: whatever uses it stops (the upload's token goes too). */
    suspend fun signOut() =
        mutex.withLock {
            secure.delete(NAME)
            state.value = LibreViewAccountState.SignedOut
        }

    /** The saved account (null: none); a failure when it can't be read now. */
    private suspend fun readSaved(): Result<Saved?> =
        try {
            Result.success(secure.read(NAME)?.let { json.decodeFromString(Saved.serializer(), it.decodeToString()) })
        } catch (e: SecretUnreadableException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            // not what this class wrote: the same as damaged
            Result.failure(e)
        }

    private fun stateOf(saved: Result<Saved?>): LibreViewAccountState {
        val s =
            saved.getOrElse { e ->
                // the Keystore busy for a moment: what was known stays
                if (e is SecretUnreadableException && e.transient) return state.value ?: LibreViewAccountState.SignInAgain("")
                return LibreViewAccountState.SignInAgain("")
            }
        return when {
            s == null -> LibreViewAccountState.SignedOut
            s.password == null -> LibreViewAccountState.SignInAgain(s.email)
            else -> LibreViewAccountState.SignedIn(s.email)
        }
    }

    companion object {
        const val NAME = SecretName.LIBREVIEW_ACCOUNT
        private val json = Json { ignoreUnknownKeys = true }
    }
}
