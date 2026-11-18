// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.mauri.sukrara.core.model.BlockedFeature
import app.mauri.sukrara.core.model.FeatureChecksSource
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.integrations.healthconnect.HcStatus
import app.mauri.sukrara.integrations.healthconnect.PlatformHealth
import app.mauri.sukrara.integrations.localserver.LanState
import app.mauri.sukrara.integrations.localserver.LanWatcher
import app.mauri.sukrara.share.ble.AndroidNearbyCentral
import app.mauri.sukrara.share.ble.AndroidNearbyServer
import app.mauri.sukrara.share.ble.NearbyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What each feature says now, for [FeatureChecks.blocked] (the states the features' own pages show). */
data class FeatureFacts(
    val role: SourceSpec?,
    /** The wearer's Bluetooth server: NeedsPermission only while it is wanted (switch on, someone follows). */
    val sharing: NearbyStatus = NearbyStatus.Stopped,
    /** The follower's Bluetooth path: NeedsPermission only while following. */
    val following: NearbyStatus = NearbyStatus.Stopped,
    val healthConnect: HcStatus = HcStatus.Off,
    val homeNetwork: LanState = LanState.Off,
)

/** The blocked features, in [BlockedFeature] order: pure (FeatureChecksTest). */
object FeatureChecks {
    fun blocked(f: FeatureFacts): List<BlockedFeature> =
        buildList {
            if (f.role?.owner == true && f.sharing == NearbyStatus.NeedsPermission) add(BlockedFeature.BluetoothSharing)
            if (f.role?.stream == true && f.following == NearbyStatus.NeedsPermission) add(BlockedFeature.BluetoothFollowing)
            if (f.role?.owner == true && f.healthConnect == HcStatus.NeedsPermission) add(BlockedFeature.HealthConnect)
            if (f.homeNetwork == LanState.NoPermission) add(BlockedFeature.HomeNetwork)
        }
}

/**
 * THE blocked-features checklist ([FeatureChecksSource], docs/40) over the features' own states: each feature's
 * state is read only on the role that has it (a follower's phone never builds the wearer's sharing). [refresh] asks
 * each to look again (on resume and after a permission answer).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppFeatureChecks(
    context: Context,
    private val scope: CoroutineScope,
    role: Flow<SourceSpec?>,
    private val sharing: Port<NearbyStatus>,
    private val following: Port<NearbyStatus>,
    private val healthConnect: Port<HcStatus>,
    private val homeNetwork: Port<LanState>,
) : FeatureChecksSource {
    /** One feature's state, how to look again and how to turn it off (null: no switch). */
    class Port<T>(
        val state: () -> Flow<T>,
        val refresh: () -> Unit,
        val turnOff: (suspend () -> Unit)? = null,
    )

    private val ctx = context.applicationContext

    override val blocked: StateFlow<List<BlockedFeature>> =
        role
            .distinctUntilChanged()
            .flatMapLatest { r ->
                combine(
                    if (r?.owner == true) sharing.state() else flowOf(NearbyStatus.Stopped),
                    if (r?.stream == true) following.state() else flowOf(NearbyStatus.Stopped),
                    if (r?.owner == true) healthConnect.state() else flowOf(HcStatus.Off),
                    if (r != null) homeNetwork.state() else flowOf(LanState.Off),
                ) { s, fo, hc, lan -> FeatureChecks.blocked(FeatureFacts(r, s, fo, hc, lan)) }
            }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The role as last read: [refresh] wakes only what it has. */
    @Volatile private var current: SourceSpec? = null

    init {
        scope.launch { role.collect { current = it } }
    }

    override fun refresh() {
        // only what this source has built: a stream's phone never wakes the owner's sharing (and the other way round)
        val spec = current
        when {
            spec?.owner == true -> {
                sharing.refresh()
                healthConnect.refresh()
            }

            spec?.stream == true -> {
                following.refresh()
            }
        }
        if (current != null) homeNetwork.refresh()
    }

    override fun permissions(feature: BlockedFeature): List<String> =
        when (feature) {
            BlockedFeature.BluetoothSharing -> {
                AndroidNearbyServer.PERMISSIONS
            }

            BlockedFeature.BluetoothFollowing -> {
                AndroidNearbyCentral.PERMISSIONS
            }

            // Health Connect's own sheet (Android 14+ routes the request there), as its page asks
            BlockedFeature.HealthConnect -> {
                listOf(PlatformHealth.PERMISSION)
            }

            // Android 17's local network access; older Android has none to ask (and never blocks it)
            BlockedFeature.HomeNetwork -> {
                if (Build.VERSION.SDK_INT >= LanWatcher.LOCAL_NETWORK_SDK) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()
            }
        }

    override fun open(feature: BlockedFeature) {
        val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        val intent =
            if (feature == BlockedFeature.HealthConnect) {
                PlatformHealth.manageIntent(ctx)
            } else {
                app
            }
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            runCatching { ctx.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    override fun turnOff(feature: BlockedFeature) {
        val port =
            when (feature) {
                BlockedFeature.BluetoothSharing -> sharing
                BlockedFeature.BluetoothFollowing -> following
                BlockedFeature.HealthConnect -> healthConnect
                BlockedFeature.HomeNetwork -> homeNetwork
            }
        val off = port.turnOff ?: return
        scope.launch {
            runCatching { off() }
            port.refresh()
        }
    }
}
