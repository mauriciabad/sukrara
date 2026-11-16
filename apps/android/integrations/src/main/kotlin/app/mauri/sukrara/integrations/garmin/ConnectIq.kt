// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** A watch Garmin Connect knows: its id, its name ("Descent Mk3i 43mm") and whether it is connected now. */
data class IqDevice(
    val id: Long,
    val name: String,
    val connected: Boolean,
)

/** What sending one message ended in. */
enum class IqSend {
    /** Garmin Connect says it reached the watch. */
    Delivered,

    /** Garmin Connect says it failed (the watch out of reach, a transfer error). */
    Failed,

    /** Garmin Connect never answered (its known bug: messages vanish; or it was stopped). */
    NoAnswer,
}

/**
 * How Sukrara reaches a Connect IQ app on a watch: Garmin Connect's Connect IQ service, the one Garmin's companion SDK
 * binds. Sukrara talks to it itself (no Garmin library: Sukrara is GPL and goes on F-Droid): the service's calls by
 * their transaction numbers, the devices and apps as the plain parcels Garmin Connect reads, and the answers it
 * broadcasts back to this package (message status, app info). Tests swap it ([ConnectIqPort]).
 */
interface ConnectIqPort {
    /** Garmin Connect is installed (it is the only way to the watch). */
    fun installed(): Boolean

    /** The watches Garmin Connect knows; null = Garmin Connect couldn't be reached. */
    suspend fun devices(): List<IqDevice>?

    /** [app]'s version on [device], or null when it isn't installed there (or no answer). */
    suspend fun appVersion(
        device: IqDevice,
        app: String,
    ): Int?

    suspend fun send(
        device: IqDevice,
        app: String,
        message: ByteArray,
    ): IqSend
}

/** Garmin Connect's Connect IQ service, bound for as long as [ConnectIq] is used. */
class ConnectIq(
    context: Context,
    private val log: (String) -> Unit,
) : ConnectIqPort {
    private val app = context.applicationContext
    private val lock = Mutex()

    @Volatile private var binder: IBinder? = null
    private var bound = false

    @Volatile private var pendingSend: CompletableDeferred<Boolean>? = null

    @Volatile private var pendingInfo: CompletableDeferred<Int?>? = null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                // only the plain extras: the device / app parcelables stay unread (Android's lazy bundles)
                when (intent.action) {
                    ACTION_STATUS -> pendingSend?.complete(intent.getIntExtra(EXTRA_STATUS, -1) == 0)
                    ACTION_INFO -> pendingInfo?.complete(intent.getIntExtra(EXTRA_VERSION, NOT_INSTALLED).takeIf { it != NOT_INSTALLED })
                }
            }
        }

    init {
        // Garmin Connect answers by broadcasting to this package: exported, it carries no glucose, only a status
        app.registerReceiver(receiver, IntentFilter(ACTION_STATUS).apply { addAction(ACTION_INFO) }, Context.RECEIVER_EXPORTED)
    }

    override fun installed(): Boolean =
        runCatching { app.packageManager.getPackageInfo(GCM, 0) }.isSuccess

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                binder = service
                connected?.complete(Unit)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }

            override fun onBindingDied(name: ComponentName?) {
                binder = null
                unbind()
            }

            override fun onNullBinding(name: ComponentName?) {
                binder = null
                unbind()
            }
        }

    @Volatile private var connected: CompletableDeferred<Unit>? = null

    private fun unbind() {
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
    }

    /** The service's binder, binding it first; null when Garmin Connect isn't there or doesn't answer in time. */
    private suspend fun service(): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        unbind()
        if (!installed()) return null
        val wait = CompletableDeferred<Unit>().also { connected = it }
        val intent = Intent(SERVICE_ACTION).setComponent(ComponentName(GCM, SERVICE_CLASS))
        bound = runCatching { app.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) return null
        return try {
            withTimeout(BIND_TIMEOUT_MS) { wait.await() }
            binder
        } catch (_: TimeoutCancellationException) {
            log("Garmin Connect's Connect IQ service didn't answer")
            unbind()
            null
        }
    }

    override suspend fun devices(): List<IqDevice>? =
        lock.withLock {
            val b = service() ?: return null
            val known = call(b, TX_KNOWN) { readDevices(it) } ?: return null
            val connected = call(b, TX_CONNECTED) { readDevices(it) }?.map { it.id }?.toSet().orEmpty()
            known.map { it.copy(connected = it.id in connected) }
        }

    override suspend fun appVersion(
        device: IqDevice,
        app: String,
    ): Int? =
        lock.withLock {
            val b = service() ?: return null
            val answer = CompletableDeferred<Int?>().also { pendingInfo = it }
            val sent =
                oneway(b, TX_APP_INFO) { p ->
                    p.writeString(this.app.packageName)
                    p.writeString(ACTION_INFO)
                    writeDevice(p, device)
                    p.writeString(appId(app))
                }
            if (!sent) return null
            try {
                withTimeout(ANSWER_TIMEOUT_MS) { answer.await() }
            } catch (_: TimeoutCancellationException) {
                null
            } finally {
                pendingInfo = null
            }
        }

    override suspend fun send(
        device: IqDevice,
        app: String,
        message: ByteArray,
    ): IqSend =
        lock.withLock {
            val b = service() ?: return IqSend.NoAnswer
            val answer = CompletableDeferred<Boolean>().also { pendingSend = it }
            val sent =
                oneway(b, TX_SEND) { p ->
                    // IQMessage: the data's length, the data, where to send the status
                    p.writeInt(1)
                    p.writeInt(message.size)
                    p.writeByteArray(message)
                    p.writeString(this.app.packageName)
                    p.writeString(ACTION_STATUS)
                    writeDevice(p, device)
                    writeApp(p, app)
                }
            if (!sent) return IqSend.NoAnswer
            try {
                if (withTimeout(ANSWER_TIMEOUT_MS) { answer.await() }) IqSend.Delivered else IqSend.Failed
            } catch (_: TimeoutCancellationException) {
                IqSend.NoAnswer
            } finally {
                pendingSend = null
            }
        }

    private fun <T> call(
        b: IBinder,
        code: Int,
        read: (Parcel) -> T,
    ): T? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            b.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            log("Garmin Connect call $code failed: ${e.javaClass.simpleName}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun oneway(
        b: IBinder,
        code: Int,
        write: (Parcel) -> Unit,
    ): Boolean {
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            b.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            log("Garmin Connect call $code failed: ${e.javaClass.simpleName}")
            false
        } finally {
            data.recycle()
        }
    }

    /** A typed list of IQDevice: its size, then each as a present flag, its id, its name and its status name. */
    private fun readDevices(p: Parcel): List<IqDevice> {
        val n = p.readInt()
        if (n < 0) return emptyList()
        return (0 until n).mapNotNull {
            if (p.readInt() == 0) return@mapNotNull null
            val id = p.readLong()
            val name = p.readString().orEmpty()
            val status = p.readString()
            IqDevice(id, name, status == "CONNECTED")
        }
    }

    private fun writeDevice(
        p: Parcel,
        d: IqDevice,
    ) {
        p.writeInt(1)
        p.writeLong(d.id)
        p.writeString(d.name)
        p.writeString(if (d.connected) "CONNECTED" else "NOT_CONNECTED")
    }

    /** IQApp: version, status (UNKNOWN), the id without dashes, a display name. */
    private fun writeApp(
        p: Parcel,
        app: String,
    ) {
        p.writeInt(1)
        p.writeInt(0)
        p.writeInt(0)
        p.writeString(appId(app))
        p.writeString("")
    }

    companion object {
        const val GCM = "com.garmin.android.apps.connectmobile"
        private const val SERVICE_ACTION = "com.garmin.android.apps.connectmobile.CONNECTIQ_SERVICE_ACTION"
        private const val SERVICE_CLASS = "com.garmin.android.apps.connectmobile.connectiq.ConnectIQService"
        private const val DESCRIPTOR = "com.garmin.android.apps.connectmobile.connectiq.IConnectIQService"

        // the service's calls (IConnectIQService, in Garmin's declaration order)
        private const val TX_CONNECTED = IBinder.FIRST_CALL_TRANSACTION + 1
        private const val TX_KNOWN = IBinder.FIRST_CALL_TRANSACTION + 2
        private const val TX_APP_INFO = IBinder.FIRST_CALL_TRANSACTION + 4
        private const val TX_SEND = IBinder.FIRST_CALL_TRANSACTION + 6

        private const val ACTION_STATUS = "app.mauri.sukrara.garmin.SEND_STATUS"
        private const val ACTION_INFO = "app.mauri.sukrara.garmin.APP_INFO"
        private const val EXTRA_STATUS = "com.garmin.android.connectiq.EXTRA_STATUS"
        private const val EXTRA_VERSION = "com.garmin.android.connectiq.EXTRA_APPLICATION_VERSION"
        private const val NOT_INSTALLED = 65535

        private const val BIND_TIMEOUT_MS = 5_000L
        private const val ANSWER_TIMEOUT_MS = 20_000L

        /** Connect IQ writes app ids upper case without dashes. */
        fun appId(uuid: String) = uuid.uppercase().replace(Regex("[\\s-]"), "")
    }
}
