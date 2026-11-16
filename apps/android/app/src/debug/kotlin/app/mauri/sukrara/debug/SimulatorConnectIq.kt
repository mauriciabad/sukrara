// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.debug

import android.content.Context
import androidx.core.content.edit
import app.mauri.sukrara.integrations.garmin.ConnectIqPort
import app.mauri.sukrara.integrations.garmin.IqDevice
import app.mauri.sukrara.integrations.garmin.IqSend
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Debug builds: Sukrara Aura's push goes to the Connect IQ simulator on the Mac instead of Garmin Connect (the
 * companion SDK's "tethered" mode): this phone listens on 127.0.0.1:[PORT], `adb forward tcp:7381 tcp:7381` joins the
 * simulator to it (simulator › Connection › Start), and each message is written to it as the same bytes Garmin
 * Connect would carry. The simulator shows up as one connected watch, "Simulator", with the face installed. The
 * face's own fetch reaches this phone through `adb forward tcp:17582 tcp:17582`. `GARMIN_SIM --ez on true|false`
 * ([DebugFakes]); from the next start. Never in a release build (this source set).
 */
class SimulatorConnectIq(
    private val log: (String) -> Unit,
) : ConnectIqPort {
    @Volatile private var client: Socket? = null

    init {
        thread(name = "garmin-sim", isDaemon = true) {
            try {
                ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                    while (true) {
                        val s = server.accept()
                        client?.runCatching { close() }
                        client = s
                        log("Connect IQ simulator connected")
                        // the simulator's own messages (none expected) are read and dropped, so the socket stays healthy
                        thread(isDaemon = true) {
                            runCatching {
                                val buf = ByteArray(4096)
                                while (s.getInputStream().read(buf) >= 0) Unit
                            }
                            if (client === s) client = null
                        }
                    }
                }
            } catch (e: IOException) {
                log("Connect IQ simulator port: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun installed() = true

    override suspend fun devices(): List<IqDevice> = listOf(IqDevice(SIM_ID, "Simulator", client?.isConnected == true))

    override suspend fun appVersion(
        device: IqDevice,
        app: String,
    ): Int = 1

    override suspend fun send(
        device: IqDevice,
        app: String,
        message: ByteArray,
    ): IqSend {
        val s = client ?: return IqSend.Failed
        return try {
            s.getOutputStream().apply {
                write(message)
                flush()
            }
            IqSend.Delivered
        } catch (e: IOException) {
            client = null
            IqSend.Failed
        }
    }

    companion object {
        /** The SDK's tethered port. */
        const val PORT = 7381
        private const val SIM_ID = 12345L
        private const val PREFS = "debug_garmin_sim"

        fun isOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

        fun set(
            context: Context,
            on: Boolean,
        ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putBoolean("on", on) }
    }
}
