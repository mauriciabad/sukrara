// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mauri.sukrara.feature.sharing.OwnerSharing
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.SaveResult
import app.mauri.sukrara.feature.sharing.backup.PasswordManager.Saved
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backup's flow: *Save to password manager* from the tap to its end ([ui]), the recovery code, and one line per save in
 * the diagnostic log (how it ended and what the system said, never the secret). The save runs in this view model's
 * scope, so turning the phone while the system's sheet is open still gets the answer. Secrets never sit here: the
 * engine hands the identity straight to the screen's Credential Manager call, the recovery code to its dialog.
 */
class BackupViewModel(private val sharing: OwnerSharing) : ViewModel() {
    val state: StateFlow<OwnerShareState> = sharing.state

    private val _ui = MutableStateFlow(BackupUi.Idle)
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    /**
     * Save to password manager (the row, *Try again*, or coming back from Android's settings): [save] is the
     * screen's Credential Manager call. A tap while the system's sheet is open does nothing.
     */
    fun save(save: suspend (id: String, secret: String) -> SaveResult) {
        if (_ui.value == BackupUi.Asking) return
        _ui.value = BackupUi.Asking
        viewModelScope.launch {
            var result: SaveResult? = null
            val broke =
                try {
                    sharing.saveToPasswordManager { id, secret -> save(id, secret).also { result = it }.saved == Saved.Saved }
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // the identity couldn't be read, or the saved date couldn't be stored: the class name only
                    sharing.log("password manager: ${e.javaClass.simpleName}")
                    true
                }
            val r = result
            _ui.value =
                when {
                    // the password manager has it even if this phone couldn't note the date
                    r != null -> ui(r.saved)

                    broke -> BackupUi.Failed

                    // no identity any more (replaced meanwhile): the page itself says so
                    else -> BackupUi.Idle
                }
            r?.let { sharing.log(logLine(it)) }
        }
    }

    /** The recovery code for [password] (slow: scrypt), or null. The dialog keeps it only while it is shown. */
    suspend fun recoveryCode(password: String): String? = sharing.exportRecovery(password)

    /** The code was copied: no more "Not backed up", and the card says to paste it somewhere now. */
    fun recoverySaved() {
        _ui.value = BackupUi.CodeCopied
        viewModelScope.launch { sharing.recoverySaved() }
    }

    companion object {
        fun factory(sharing: OwnerSharing) = viewModelFactory { initializer { BackupViewModel(sharing) } }

        internal fun ui(saved: Saved): BackupUi =
            when (saved) {
                Saved.Saved -> BackupUi.Saved
                Saved.Cancelled -> BackupUi.Cancelled
                Saved.NoPasswordManager -> BackupUi.NoPasswordManager
                Saved.Failed -> BackupUi.Failed
            }

        internal fun logLine(r: SaveResult): String =
            "password manager: " +
                when (r.saved) {
                    Saved.Saved -> "saved"
                    Saved.Cancelled -> "not saved, the sheet was closed"
                    Saved.NoPasswordManager -> "none takes passwords"
                    Saved.Failed -> "failed"
                } + (r.detail?.let { " ($it)" } ?: "")
    }
}
