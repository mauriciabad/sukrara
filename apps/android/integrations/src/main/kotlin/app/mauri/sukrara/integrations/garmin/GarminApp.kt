// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

/**
 * A payload profile, contracts/watchface/payload "Profiles": what one kind of Connect IQ app gets ([key], the payload's
 * `p`) and the path of its 5-minute fetch. Every watch face gets [Face], Sukrara's and any other face that fetches it.
 */
enum class WatchProfile(
    val key: String,
    val path: String,
) {
    Face("face", "sukrara/face"),
    App("app", "sukrara/app"),
    Field("field", "sukrara/field"),
    ;

    companion object {
        fun ofPath(path: String) = entries.firstOrNull { it.path == path }
    }
}

/**
 * Sukrara's Connect IQ apps, contracts/watchface/payload/apps.json (GarminAppsTest holds them to it): each its own app
 * id (storage and phone messages are per app id), a beta id the phone treats the same and the payload [profile] it
 * gets. The watch faces are a list ([faces]), each with the face profile; there is one watch app and one data field.
 */
enum class GarminApp(
    val key: String,
    val id: String,
    val beta: String,
    val profile: WatchProfile,
    /** It writes entries (`POST` [ENTRIES]) and gets the signing key with its push. */
    val logs: Boolean,
    /** Its Connect IQ store listing's id (apps.json `store`; Garmin gives it at the first upload, and it isn't [id]). */
    val store: String,
) {
    Aura(
        "aura",
        id = "9ace188f-9a3a-42d6-9f7d-1afd4ce93293",
        beta = "635140ec-f724-44fb-bc08-976fa9111250",
        profile = WatchProfile.Face,
        logs = false,
        store = "2859fe48-7e9e-4e52-ae2a-a319fa76fcfc",
    ),
    App(
        "app",
        id = "87023f2f-8e48-4253-b9c3-8e37eb2b5fee",
        beta = "300d563a-89b2-4a60-8ac3-35baff189a2f",
        profile = WatchProfile.App,
        logs = true,
        store = "c8c7b5e9-35e6-4941-b811-3bf7c9238c9a",
    ),
    Field(
        "field",
        id = "2adf061b-0ed1-4215-bdec-2468c537e8ec",
        beta = "ba2f02c6-d6f1-4db4-a9a9-d147f77ac41f",
        profile = WatchProfile.Field,
        logs = false,
        store = "bae83264-c160-4498-8a4d-492b4ea7d630",
    ),
    ;

    /** The ids a watch may have it under: the public app and its beta. */
    val ids: List<String> get() = listOf(id, beta)

    val isFace: Boolean get() = profile == WatchProfile.Face

    /** Its Connect IQ store listing for the phone's [language] (`es`: Garmin's Spanish store; any other: the English). */
    fun storeUrl(language: String): String = "https://apps.garmin.com/${if (language == "es") "es-ES" else "en-US"}/apps/$store"

    companion object {
        /** The entries the app writes: `POST http://127.0.0.1:17582/sukrara/entries`. */
        const val ENTRIES = "sukrara/entries"

        /** Sukrara's watch faces, in the page's order. */
        val faces: List<GarminApp> get() = entries.filter { it.isFace }
    }
}
