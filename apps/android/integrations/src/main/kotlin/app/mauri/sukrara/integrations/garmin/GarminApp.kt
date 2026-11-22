// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

/**
 * Sukrara's Connect IQ apps, contracts/watchface/payload/apps.json (GarminAppsTest holds them to it): each its own app
 * id (storage and phone messages are per app id), a beta id the phone treats the same, the payload profile ([key]) it
 * gets and the path of its 5-minute fetch.
 */
enum class GarminApp(
    val key: String,
    val id: String,
    val beta: String,
    val path: String,
    /** It writes entries (`POST` [ENTRIES]) and gets the signing key with its push. */
    val logs: Boolean,
) {
    Aura("aura", "9ace188f-9a3a-42d6-9f7d-1afd4ce93293", "635140ec-f724-44fb-bc08-976fa9111250", "sukrara/aura", false),
    App("app", "87023f2f-8e48-4253-b9c3-8e37eb2b5fee", "300d563a-89b2-4a60-8ac3-35baff189a2f", "sukrara/app", true),
    Field("field", "2adf061b-0ed1-4215-bdec-2468c537e8ec", "ba2f02c6-d6f1-4db4-a9a9-d147f77ac41f", "sukrara/field", false),
    ;

    /** The ids a watch may have it under: the public app and its beta. */
    val ids: List<String> get() = listOf(id, beta)

    companion object {
        /** The entries the app writes: `POST http://127.0.0.1:17582/sukrara/entries`. */
        const val ENTRIES = "sukrara/entries"

        fun ofPath(path: String) = entries.firstOrNull { it.path == path }
    }
}
