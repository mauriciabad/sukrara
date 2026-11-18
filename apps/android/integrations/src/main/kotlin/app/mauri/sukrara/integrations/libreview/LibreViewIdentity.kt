// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import kotlinx.serialization.Serializable

/**
 * An Abbott app identity LibreView's upload API knows (docs/45): each keeps its own active device and **one live
 * session**, so a sign-in on the identity the official app uses ends that app's upload silently, while a sign-in on the
 * other one leaves it alone (proven live, docs/45 test H). Sukrara's own upload is the Libre 3 app's ([Libre3]); beside
 * the official Libre 3 app it sends treatments as LibreLink ([LibreLink]), and beside LibreLink as the Libre 3 app.
 */
@Serializable
enum class LvIdentity(
    /** The ledger's and the state's name for it (`LvSent.via`). */
    val id: String,
    /** `GatewayType` in every body. */
    val gateway: String,
    /** The device the clinic sees the uploads under in LibreView. */
    val device: String,
) {
    Libre3("libre3", "FSLibreLink3.Android", "FreeStyle Libre 3"),
    LibreLink("librelink", "FSLibreLink.Android", "FreeStyle LibreLink"),
    ;

    /** The other identity: the one Sukrara sends under beside an official app that uses this one. */
    val other: LvIdentity get() = if (this == Libre3) LibreLink else Libre3

    companion object {
        fun of(id: String): LvIdentity = entries.firstOrNull { it.id == id } ?: Libre3

        /**
         * The identity the official app uploads under, from LibreLinkUp's device type (`patientDevice.dtid`, contracts/
         * treatments/libreview-librelink-fixture.json): 40068 = the FreeStyle Libre 3 app; 40066 / 40067 = LibreLink
         * (a Libre 1 / a Libre 2; DiaBLE `LibreLink.swift`). Anything else (Abbott's unified *Libre* app, a type not seen
         * yet) or none: null, and nothing goes to LibreView until it is tested.
         */
        fun ofDeviceType(dtid: Int?): LvIdentity? =
            when (dtid) {
                40068 -> Libre3
                40066, 40067 -> LibreLink
                else -> null
            }

        /**
         * THE guard: whether Sukrara may sign in (or take over) on [identity]. While the official app uploads
         * ([besideOfficialApp], LibreLinkUp is the source) only on the identity it doesn't use, and on none while that
         * one is unknown ([official] null): a sign-in on the official app's identity stops its upload, and so the
         * glucose Sukrara reads from LibreLinkUp, until the person signs it out and in again (docs/45, 35 min on
         * 2026-11-18). Otherwise Sukrara is the uploader and may use both.
         */
        fun mayUse(
            identity: LvIdentity,
            besideOfficialApp: Boolean,
            official: LvIdentity?,
        ): Boolean = !besideOfficialApp || (official != null && identity != official)
    }
}
