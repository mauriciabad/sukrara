// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.transport

import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

/**
 * #78: the Android network watch on Android's ConnectivityManager: its callback is registered while a watch is open
 * and unregistered when it closes (it used to stay registered for the life of the process); the network already up
 * when it opens isn't news, a network that comes after is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidNetworkWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    @Test
    fun `the callback is registered while the watch is open and gone once it closes`() {
        var back = 0
        val watch = AndroidNetworkWatch(app).watch { back++ }
        val callback = shadowOf(cm).networkCallbacks.single()

        assertNotNull("the test needs a network that is up", cm.activeNetwork)
        callback.onAvailable(cm.activeNetwork!!)
        assertEquals("the network already up isn't news", 0, back)
        callback.onAvailable(ShadowNetwork.newInstance(4242))
        assertEquals(1, back)

        watch.close()
        assertEquals(emptySet<ConnectivityManager.NetworkCallback>(), shadowOf(cm).networkCallbacks)
        watch.close()
    }
}
