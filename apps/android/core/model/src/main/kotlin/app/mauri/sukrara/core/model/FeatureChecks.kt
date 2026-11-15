// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * A feature that is on (or on by default) and in use, but Android blocks it, so it silently does nothing: the
 * checklist next to [AlertReadiness] for everything that isn't an alert (docs/40). Each is listed only while it costs
 * the person something (Bluetooth sharing only while someone follows), in this order; home's banner names the first,
 * Settings marks the row of the page that has it, and the page itself has the fix. Every item has a way out besides
 * *Allow*: turning the feature off, or, where there is no switch, closing the tip (the page keeps the fix).
 *
 * The NFC taps Android blocks keep their own banner and cards (their *Allow* is one Android screen for both).
 */
enum class BlockedFeature(
    /** The feature has a switch here: *Turn off* is the way out (else *Not now* closes the tip). */
    val canTurnOff: Boolean,
) {
    /**
     * The wearer's Bluetooth sharing is on and someone follows (or *Pair nearby* is open) but Nearby devices doesn't
     * cover advertising: followers near the phone get nothing over Bluetooth, the internet is their only way.
     */
    BluetoothSharing(canTurnOff = true),

    /**
     * Following someone, Nearby devices not allowed: no readings over Bluetooth when the wearer's internet is down
     * (the follower's Bluetooth path has no switch: it is the backup of the internet one).
     */
    BluetoothFollowing(canTurnOff = false),

    /** Health Connect is on but its permission was removed: nothing is written there. */
    HealthConnect(canTurnOff = true),

    /** The Nightscout API's home Wi-Fi option is on but Android 17's local network access isn't allowed. */
    HomeNetwork(canTurnOff = true),
}

/** The live list of [BlockedFeature]s and their ways out; one instance (`AppGraph.featureChecks`). */
interface FeatureChecksSource {
    val blocked: StateFlow<List<BlockedFeature>>

    /** Reads every item again (the app calls it on resume: permissions change in Android's settings). */
    fun refresh()

    /** The runtime permissions *Allow* asks for [feature] in the system dialog; empty = [open] is the way. */
    fun permissions(feature: BlockedFeature): List<String>

    /** The system screen that allows [feature] (the app's permissions, Health Connect's page). */
    fun open(feature: BlockedFeature)

    /** *Turn off* ([BlockedFeature.canTurnOff]): the feature's own switch, as on its page. */
    fun turnOff(feature: BlockedFeature)

    companion object {
        /** Nothing blocked, nothing to do (tests, previews, a build without the features). */
        val None: FeatureChecksSource =
            object : FeatureChecksSource {
                override val blocked = kotlinx.coroutines.flow.MutableStateFlow(emptyList<BlockedFeature>())

                override fun refresh() = Unit

                override fun permissions(feature: BlockedFeature) = emptyList<String>()

                override fun open(feature: BlockedFeature) = Unit

                override fun turnOff(feature: BlockedFeature) = Unit
            }
    }
}
