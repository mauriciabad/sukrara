// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.juggluco

import app.mauri.sukrara.core.data.secure.SecretName
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Juggluco's web-server API secret (Juggluco › Settings › Web server; schema `source.jugglucoSecret`), kept in the
 * Keystore [SecureStore] ([SecretName.JUGGLUCO]); optional. Sent on every request as Juggluco expects it, the
 * Nightscout way: `api-secret: sha1hex(secret)` ([header]).
 */
class JugglucoSecret(private val secure: SecureStore) {
    private val set = MutableStateFlow<Boolean?>(null)

    /** Whether a secret is saved (null until first read: [refresh]). */
    val isSet: Flow<Boolean?> = set.asStateFlow()

    /**
     * The saved secret, or null when none is saved. One that exists but can't be read (the Keystore is busy or the
     * file is damaged, reported on home) is sent as none: Juggluco then refuses and Settings › Sensor says so, with
     * the secret's row still saying it is set (typing it again replaces it).
     */
    suspend fun read(): String? =
        try {
            secure
                .read(SecretName.JUGGLUCO)
                ?.decodeToString()
                ?.takeIf { it.isNotEmpty() }
                .also { set.value = it != null }
        } catch (_: SecretUnreadableException) {
            set.value = true
            null
        }

    suspend fun refresh() {
        read()
    }

    /** Saves [secret] (trimmed); blank removes it. */
    suspend fun save(secret: String) {
        val s = secret.trim()
        if (s.isEmpty()) secure.delete(SecretName.JUGGLUCO) else secure.write(SecretName.JUGGLUCO, s.encodeToByteArray())
        set.value = s.isNotEmpty()
    }

    companion object {
        const val HEADER = "api-secret"

        /** The header value for [secret]: its SHA-1 in lowercase hex (what Nightscout clients and Juggluco use). */
        fun header(secret: String): String = MessageDigest.getInstance("SHA-1").digest(secret.encodeToByteArray()).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
