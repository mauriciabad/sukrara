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
    /**
     * Its Connect IQ store listing's id (apps.json `store`): Garmin gives it when the listing is published, and it isn't
     * [id]. Null until then: no link to the listing shows anywhere ([ConnectIqStore]). TODO(#247): fill them in, in
     * apps.json first (GarminAppsTest holds these to it), once the listings are public.
     */
    val store: String?,
) {
    Aura("aura", "9ace188f-9a3a-42d6-9f7d-1afd4ce93293", "635140ec-f724-44fb-bc08-976fa9111250", WatchProfile.Face, false, null),
    App("app", "87023f2f-8e48-4253-b9c3-8e37eb2b5fee", "300d563a-89b2-4a60-8ac3-35baff189a2f", WatchProfile.App, true, null),
    Field("field", "2adf061b-0ed1-4215-bdec-2468c537e8ec", "ba2f02c6-d6f1-4db4-a9a9-d147f77ac41f", WatchProfile.Field, false, null),
    ;

    /** The ids a watch may have it under: the public app and its beta. */
    val ids: List<String> get() = listOf(id, beta)

    val isFace: Boolean get() = profile == WatchProfile.Face

    companion object {
        /** The entries the app writes: `POST http://127.0.0.1:17582/sukrara/entries`. */
        const val ENTRIES = "sukrara/entries"

        /** Sukrara's watch faces, in the page's order. */
        val faces: List<GarminApp> get() = entries.filter { it.isFace }
    }
}

/**
 * The Connect IQ store listings of Sukrara's apps ([GarminApp.store], or a debug build's stand-ins): an app without an
 * id has no link, so no broken link ever shows (#247). [url] is the listing in the phone's language (Garmin's store has
 * a Spanish one).
 */
class ConnectIqStore(
    val ids: Map<GarminApp, String> = GarminApp.entries.mapNotNull { a -> a.store?.let { a to it } }.toMap(),
) {
    /** The apps that have a listing, in the registry's order. */
    val apps: List<GarminApp> get() = GarminApp.entries.filter { it in ids }

    /** [app]'s listing for [language] (`es`: Garmin's Spanish store), or null while it has none. */
    fun url(
        app: GarminApp,
        language: String,
    ): String? = ids[app]?.let { "https://apps.garmin.com/${if (language == "es") "es-ES" else "en-US"}/apps/$it" }
}
