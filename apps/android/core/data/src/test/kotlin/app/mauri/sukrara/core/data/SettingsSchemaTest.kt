// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.settings.SECURE_STORE
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.schemaStores
import app.mauri.sukrara.settings.SettingsSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The generated spec, the store files and [SchemaSettings] agree with THE schema. */
class SettingsSchemaTest {
    private val android = SettingsSpec.SETTINGS.filter { it.android.isNotEmpty() }

    @Test
    fun `every number setting has a format whose default is valid`() {
        for (info in SettingsSpec.SETTINGS.filter { it.type == "number" }) {
            val format = SettingsSpec.NUMBERS[info.number]
            checkNotNull(format) { "${info.id} has no number format" }
            assertTrue("${info.id} default ${format.default}", format.valid(format.default))
        }
    }

    @Test
    fun `every single-key Android number setting resolves to its schema store and key`() {
        for (info in android.filter { it.type == "number" && ',' !in it.android }) {
            val setting = SchemaSettings.number(info.id)
            assertEquals(info.android, "${setting.store}:${setting.key}")
            assertEquals(SettingsSpec.NUMBERS.getValue(info.number!!).default, setting.default, 0.0)
        }
    }

    @Test
    fun `every Android store is a generated store constant`() {
        val constants =
            SettingsSpec.Stores::class.java.declaredFields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
                .toSet()
        assertEquals(constants, schemaStores())
    }

    @Test
    fun `every Android key is a generated key constant`() {
        val constants =
            SettingsSpec.Keys::class.java.declaredFields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
                .toSet()
        val keys = android.flatMap { it.android.substringAfter(':').split(',') }.toSet()
        assertEquals(constants, keys)
    }

    @Test
    fun `every Android secret lives in the SecureStore under a known name, never in a DataStore or a settings file`() {
        val secrets = android.filter { it.type == "secret" }
        assertTrue(secrets.any { it.id == "source.jugglucoSecret" })
        for (info in secrets) {
            val (store, name) = info.android.split(":", limit = 2)
            assertEquals(info.id, SECURE_STORE, store)
            assertTrue(info.id, name in app.mauri.sukrara.core.data.secure.SecretName.ALL)
        }
        assertEquals("secure:${app.mauri.sukrara.core.data.secure.SecretName.JUGGLUCO}", secrets.single().android)
        assertTrue(SECURE_STORE !in schemaStores())
        assertTrue(
            app.mauri.sukrara.core.data.settings.SettingsTransfer
                .entries()
                .none { it.store == SECURE_STORE },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ids outside the schema are refused`() {
        SchemaSettings.bool("made.up", default = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Mac-only settings are refused`() {
        SchemaSettings.bool("general.openAtLogin", default = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `type mismatch is refused`() {
        SchemaSettings.number("sharing.nostr")
    }
}
