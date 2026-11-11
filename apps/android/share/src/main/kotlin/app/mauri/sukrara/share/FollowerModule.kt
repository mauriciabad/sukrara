// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share

import android.content.Context
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.ble.AndroidNearbyCentral
import app.mauri.sukrara.share.ble.NearbyTransportFactory
import app.mauri.sukrara.share.crypto.SdkNostrCrypto
import app.mauri.sukrara.share.follower.FollowerEngine
import app.mauri.sukrara.share.follower.FollowerNotice
import app.mauri.sukrara.share.follower.FollowerStore
import app.mauri.sukrara.share.follower.RelayTransportFactory
import app.mauri.sukrara.share.follower.WearerSettingsCache
import app.mauri.sukrara.share.transport.AndroidNetworkWatch
import app.mauri.sukrara.share.transport.OkHttpWebSockets
import app.mauri.sukrara.share.transport.RelayAccess
import app.mauri.sukrara.share.transport.RelayPool
import okhttp3.OkHttpClient

/**
 * The follower role's sharing graph (composed by `AppGraph`). Nothing is built until first use; the engine connects
 * only while the monitoring service hosts it as the follower's reading source. [notices] = the notification for what
 * the wearer said (invite used, declined, access removed). It takes only the storage it uses: the follower record's
 * [secure] store, every stored reading and treatment (the wearer's, written and wiped by the engine), and a plain
 * copy of the wearer's settings ([wearerSettings]; a damaged copy reads as none: the record has the same settings).
 */
class FollowerModule(
    private val context: Context,
    private val secure: SecureStore,
    private val http: OkHttpClient,
    private val readings: ReadingsRepository,
    private val treatments: TreatmentsRepository,
    private val wearerSettings: StateStore<WearerSettingsCache>,
    private val hub: GlucoseHub,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val notices: (FollowerNotice, Profile?) -> Unit,
    /** Where the relay connections go (the app's `LaunchPlan`; release: the relays the wearer names). */
    private val relayAccess: RelayAccess = RelayAccess.Public,
) {
    private val crypto by lazy { SdkNostrCrypto() }

    val engine: FollowerEngine by lazy {
        val relays =
            RelayTransportFactory { urls, auth ->
                RelayPool(
                    urls,
                    auth,
                    crypto,
                    OkHttpWebSockets(http),
                    dispatchers.io,
                    { clock.now().epochSecond },
                    access = relayAccess,
                )
            }
        FollowerEngine(
            crypto = crypto,
            store = FollowerStore(secure, crypto),
            transports = relays,
            readingsRepo = readings,
            treatments = treatments,
            clock = clock,
            dispatcher = dispatchers.default,
            setLink = hub::setLink,
            onNotice = notices,
            nearbyTransports = NearbyTransportFactory { identity -> AndroidNearbyCentral(context, crypto, identity) },
            settingsCache = wearerSettings,
            network = AndroidNetworkWatch(context),
        )
    }
}
