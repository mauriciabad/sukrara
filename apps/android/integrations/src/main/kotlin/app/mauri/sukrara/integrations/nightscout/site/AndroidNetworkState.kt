// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [NetworkState] from the default network: online = it can reach the internet; [back] = a new default network came
 * up (the one already up when the watch starts isn't news).
 */
class AndroidNetworkState(
    context: Context,
) : NetworkState {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    override fun online(): Boolean {
        val n = cm?.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override val back: Flow<Unit> =
        callbackFlow {
            val upAlready = cm?.activeNetwork
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    private var first = true

                    override fun onAvailable(network: Network) {
                        val known = first && network == upAlready
                        first = false
                        if (!known) trySend(Unit)
                    }
                }
            cm?.registerDefaultNetworkCallback(callback)
            awaitClose { runCatching { cm?.unregisterNetworkCallback(callback) } }
        }
}
