// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import app.mauri.sukrara.core.data.DataModule
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.SensorLife
import app.mauri.sukrara.share.ble.AndroidNearbyServer
import app.mauri.sukrara.share.crypto.SdkNostrCrypto
import app.mauri.sukrara.share.owner.OwnerEngine
import app.mauri.sukrara.share.owner.OwnerIdentityStore
import app.mauri.sukrara.share.owner.RelayPolicy
import app.mauri.sukrara.share.owner.SeenWraps
import app.mauri.sukrara.share.owner.requests.RequestNotifications
import app.mauri.sukrara.share.owner.requests.RequestNotifier
import app.mauri.sukrara.share.owner.usecase.MirroredSettingsSource
import app.mauri.sukrara.share.owner.usecase.OwnerContext
import app.mauri.sukrara.share.owner.usecase.RandomBytes
import app.mauri.sukrara.share.transport.OkHttpWebSockets
import app.mauri.sukrara.share.transport.RelayAccess
import app.mauri.sukrara.share.transport.RelayPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * The wearer's sharing graph (composed by `AppGraph`, owner role only). Nothing is built until first use.
 * [requestNotifications] = the access-request notification (the app's notification table posts it); [settings] = the
 * wearer's settings for the followers' mirror; [scope] = the process scope (the notification's Allow / Deny).
 */
class ShareOwnerModule(
    private val context: Context,
    private val data: DataModule,
    private val hub: GlucoseHub,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    val scope: CoroutineScope,
    val requestNotifications: RequestNotifications,
    private val settings: MirroredSettingsSource = MirroredSettingsSource.None,
    /** Where the relay connections go (the app's `LaunchPlan`; release: the public relays). */
    private val relays: RelayAccess = RelayAccess.Public,
    /** The wearer's current sensor (`:sensor`'s start, end and warm-up end), sent with the live events. */
    private val sensor: Flow<SensorLife?> = flowOf(null),
) {
    val crypto by lazy { SdkNostrCrypto() }

    private val ownerContext by lazy {
        val random = SecureRandom()
        OwnerContext(crypto, RandomBytes { n -> ByteArray(n).also(random::nextBytes) })
    }

    val identityStore by lazy { OwnerIdentityStore(data.secureStore) }

    /** Relays read gift wraps addressed to the identity only after NIP-42 AUTH as it: the pool gets I when known. */
    private val transport by lazy {
        RelayPool(
            Wire.DEFAULT_RELAYS,
            authKey = { ownerEngine.identityKey() },
            crypto,
            OkHttpWebSockets(data.http),
            dispatchers.io,
            { clock.now().epochSecond },
            access = relays,
        )
    }

    private val requests by lazy { RequestNotifier(requestNotifications) }

    /** The GATT server for nearby followers; on while the switch is (Settings › Sharing › Bluetooth). */
    private val nearby by lazy { AndroidNearbyServer(context, crypto, data.settings.flow(NearbySharing), scope) }

    val ownerEngine: OwnerEngine by lazy {
        val seen = SeenWraps { id, at -> data.database.seenWraps().markSeen(id, at) }
        OwnerEngine(
            ownerContext,
            identityStore,
            transport,
            hub,
            seen,
            data.treatments,
            clock,
            dispatchers.default,
            settings = settings,
            nearby = nearby,
            relayPolicy =
                combine(data.settings.flow(RelaySharing), data.settings.flow(RelayInterval)) { on, min ->
                    RelayPolicy(on, min.toInt())
                },
            onRequest = { requests.onRequest(it) },
            sensor = sensor,
        ).also { engine ->
            watchNetwork()
            // the notification goes as soon as its request is answered anywhere (app, notification) or paused
            scope.launch { engine.state.collect { requests.onState(it) } }
        }
    }

    /**
     * Allow / Deny from the notification, through the sharing the role runs ([OwnerEngine.answerFromNotification]): it
     * never starts or stops sharing (#167), and in a process the tap just started it waits up to [START_WAIT_MS] for the
     * role's start. Done: the notification goes, also in a process that never showed it. False when it didn't happen
     * (sharing doesn't run here, the request isn't waiting any more, its invite expired, another phone also tried it,
     * sharing is paused).
     */
    suspend fun answer(follower: String, allow: Boolean): Boolean =
        ownerEngine.answerFromNotification(follower, allow, START_WAIT_MS).also { done -> if (done) requests.onAnswered(follower) }

    companion object {
        /** How long an answer from the notification waits for the role's start (the receiver's whole budget is ~10 s). */
        const val START_WAIT_MS = 4_000L

        /** Settings › Sharing › Bluetooth (the schema's `sharing.bleFollowers`). */
        val NearbySharing = SchemaSettings.bool("sharing.bleFollowers", default = true)

        /** Settings › Sharing › Over the internet (the schema's `sharing.nostr`). */
        val RelaySharing = SchemaSettings.bool("sharing.nostr", default = true)

        /** Settings › Sharing › Upload interval, minutes; 0 = every reading (the schema's `sharing.nostrInterval`). */
        val RelayInterval = SchemaSettings.number("sharing.nostrInterval")
    }

    /** The network came back: reconnect relays now instead of waiting out the backoff. */
    private fun watchNetwork() {
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = transport.reconnectNow()
            },
        )
    }
}
