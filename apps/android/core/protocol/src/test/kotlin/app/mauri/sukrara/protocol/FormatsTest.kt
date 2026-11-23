// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.protocol.event.Avatar
import app.mauri.sukrara.protocol.invite.InviteLink
import app.mauri.sukrara.protocol.testing.ContractFiles
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * contracts/apps/formats.json, THE shared formats: the version each sharing format's code speaks and what never moves
 * in it are the registry's (the watch payload's: GarminAppsTest).
 */
class FormatsTest {
    private val formats =
        ContractFiles.json("apps/formats.json").getValue("formats").jsonArray.associate {
            it.jsonObject
                .getValue("format")
                .jsonPrimitive.content to it.jsonObject
        }

    private fun format(id: String): JsonObject = formats.getValue(id).also { assertTrue(id, "android" in apps(it)) }

    private fun apps(f: JsonObject) = f.getValue("apps").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.version() = getValue("version").jsonPrimitive.int

    private fun JsonObject.frozen() = getValue("frozen").jsonObject

    @Test
    fun theVersionsAreTheRegistrys() {
        assertEquals(format("sharing").version(), Wire.V)
        assertEquals(format("treatment").version(), Wire.TREATMENT_V)
        assertEquals(format("invite").version(), Wire.INVITE_V)
        assertEquals(format("avatar").version(), Avatar.CURRENT)
    }

    @Test
    fun theFrozenPartsAreTheCodes() {
        val kinds =
            format("sharing")
                .frozen()
                .getValue("kinds")
                .jsonArray
                .map { it.jsonPrimitive.int }
        assertEquals(
            listOf(Wire.KIND_LIVE, Wire.KIND_ALERT, Wire.KIND_DATA, Wire.KIND_WRAP, Wire.KIND_TO_OWNER, Wire.KIND_TO_FOLLOWER),
            kinds,
        )
        val invite = format("invite").frozen()
        assertEquals(InviteLink.INVITE_BASE, invite.getValue("link").jsonPrimitive.content)
        assertEquals(Wire.KIND_INVITE, invite.getValue("kind").jsonPrimitive.int)
        val link = InviteLink.of(PublicKeyHex("ab".repeat(32)), ByteArray(InviteLink.SECRET_LEN)).link
        assertEquals(invite.getValue("length").jsonPrimitive.int, link.length)
    }
}
