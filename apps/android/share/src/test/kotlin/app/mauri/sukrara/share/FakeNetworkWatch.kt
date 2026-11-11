// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share

import app.mauri.sukrara.share.transport.NetworkWatch

/** A [NetworkWatch] for tests: counts what is opened and closed; [networkBack] is the network coming back. */
class FakeNetworkWatch : NetworkWatch {
    var opened = 0
        private set
    var closed = 0
        private set
    private val listeners = mutableListOf<() -> Unit>()

    /** Watches open now. */
    val open: Int get() = listeners.size

    override fun watch(onAvailable: () -> Unit): AutoCloseable {
        opened++
        listeners += onAvailable
        var done = false
        return AutoCloseable {
            if (!done) {
                done = true
                closed++
                listeners -= onAvailable
            }
        }
    }

    fun networkBack() = listeners.toList().forEach { it() }
}
