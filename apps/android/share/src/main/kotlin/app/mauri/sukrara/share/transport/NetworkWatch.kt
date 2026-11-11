// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * The network came back: the relays reconnect now instead of waiting out their backoff. The engines open a watch
 * while they run (the wearer's sharing, the follower's connection) and close it when they stop, so nothing stays
 * registered for a sharing that ended.
 */
fun interface NetworkWatch {
    /** Calls [onAvailable] whenever a new default network is up, until the returned handle is closed (idempotent). */
    fun watch(onAvailable: () -> Unit): AutoCloseable

    companion object {
        /** No network news (tests). */
        val None = NetworkWatch { AutoCloseable {} }
    }
}

/**
 * [NetworkWatch] on the default-network callback. Android calls it at once with the network already up: that isn't
 * news (the relays are connecting over it), only a network that comes after it is.
 */
class AndroidNetworkWatch(
    private val context: Context,
) : NetworkWatch {
    override fun watch(onAvailable: () -> Unit): AutoCloseable {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return AutoCloseable {}
        val upAlready = cm.activeNetwork
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                private var first = true

                override fun onAvailable(network: Network) {
                    val known = first && network == upAlready
                    first = false
                    if (!known) onAvailable()
                }
            }
        cm.registerDefaultNetworkCallback(callback)
        return AutoCloseable { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
}
