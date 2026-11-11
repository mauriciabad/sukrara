// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException

/**
 * The phone's password manager (Credential Manager), for the sharing identity: Backup saves it as a password, setup
 * restores from it. The system shows its own sheet. An error's message is never kept (it could quote the request):
 * only its type and a provider's number in brackets ([SaveResult.detail]) go to the diagnostic log.
 */
object PasswordManager {
    enum class Saved {
        Saved,

        /** The person closed the system's sheet (or the password manager's own screen). */
        Cancelled,

        /** No password manager on this phone takes passwords from apps: none turned on, or the one on can't. */
        NoPasswordManager,

        /** The password manager answered with an error. */
        Failed,
    }

    /** How a save ended, with [detail] for the diagnostic log (e.g. "TYPE_UNKNOWN 28434"), never the secret. */
    data class SaveResult(val saved: Saved, val detail: String? = null)

    sealed interface Got {
        /** What the password manager returned: an nsec / ncryptsec to restore from. Never logged or saved. */
        class Secret(val text: String) : Got {
            override fun toString() = "Secret"
        }

        data object NoneSaved : Got

        data object Cancelled : Got

        data object Failed : Got
    }

    suspend fun save(
        activity: Activity,
        id: String,
        secret: String,
    ): SaveResult = save { CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(id, secret)) }

    /**
     * Runs [create] (the Credential Manager call) and says how it ended. An interrupted call (the person went to
     * Android's settings meanwhile, or the system broke it off) is asked once more right away, as Android advises.
     */
    internal suspend fun save(create: suspend () -> Unit): SaveResult {
        var attempt = 0
        while (true) {
            try {
                create()
                return SaveResult(Saved.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CreateCredentialException) {
                if (e is CreateCredentialInterruptedException && ++attempt < ATTEMPTS) continue
                return SaveResult(outcome(e), detail(e.type, e.errorMessage))
            }
        }
    }

    /** What an error from Credential Manager means for the person. */
    internal fun outcome(e: CreateCredentialException): Saved =
        when (e) {
            is CreateCredentialCancellationException -> Saved.Cancelled

            // no provider takes passwords (none on, or the one on can't), or this phone has no Credential Manager
            is CreateCredentialNoCreateOptionException,
            is CreateCredentialProviderConfigurationException,
            is CreateCredentialUnsupportedException,
            -> Saved.NoPasswordManager

            else -> Saved.Failed
        }

    /** The type's last part and a number in brackets from the message ("[28434]": Google's code), nothing else. */
    internal fun detail(
        type: String,
        message: CharSequence?,
    ): String {
        val code = message?.let { CODE.find(it)?.groupValues?.get(1) }
        return type.substringAfterLast('.') + (code?.let { " $it" } ?: "")
    }

    suspend fun get(activity: Activity): Got =
        get {
            val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(GetPasswordOption())))
            (result.credential as? PasswordCredential)?.password
        }

    /** Runs [fetch] (the Credential Manager call: the password picked, or null for another kind); interrupted → once more. */
    internal suspend fun get(fetch: suspend () -> String?): Got {
        var attempt = 0
        while (true) {
            try {
                return fetch()?.let(Got::Secret) ?: Got.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (_: NoCredentialException) {
                return Got.NoneSaved
            } catch (_: GetCredentialCancellationException) {
                return Got.Cancelled
            } catch (e: GetCredentialException) {
                if (e is GetCredentialInterruptedException && ++attempt < ATTEMPTS) continue
                return Got.Failed
            }
        }
    }

    /**
     * Android's *Passwords & passkeys* page (the preferred password manager and the others turned on), where a
     * password manager is turned on for apps. Some phones have no such page: then Android's settings.
     */
    fun settingsIntents(context: Context): List<Intent> =
        listOf(
            Intent(Settings.ACTION_CREDENTIAL_PROVIDER, Uri.fromParts("package", context.packageName, null)),
            Intent(Settings.ACTION_SETTINGS),
        )

    private const val ATTEMPTS = 2
    private val CODE = Regex("\\[(\\d{3,6})]")
}
