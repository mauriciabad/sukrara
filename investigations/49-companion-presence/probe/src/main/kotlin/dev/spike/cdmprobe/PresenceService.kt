// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.cdmprobe

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent

/**
 * Bound by the system while an observed device is present. Logs every callback with the process's standing, and on
 * an "appeared" / "connected" event tries what Sukrara would do: start the foreground monitoring service from the
 * background.
 */
class PresenceService : CompanionDeviceService() {
    override fun onCreate() {
        super.onCreate()
        Probe.log(this, "PresenceService created (bound by CDM) ${Probe.standing(this)}")
    }

    override fun onDestroy() {
        Probe.log(this, "PresenceService destroyed (unbound)")
        super.onDestroy()
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        val name = Probe.eventName(event.event)
        Probe.log(this, "presence $name association=${event.associationId} uuid=${event.uuid} ${Probe.standing(this)}")
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, DevicePresenceEvent.EVENT_BT_CONNECTED -> Probe.startLink(this, "presence:$name")
            else -> Unit
        }
    }

    @Deprecated("API 36 delivers onDevicePresenceEvent; logged to see which callbacks still arrive")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Probe.log(this, "legacy onDeviceAppeared ${Probe.describe(associationInfo)}")
    }

    @Deprecated("API 36 delivers onDevicePresenceEvent; logged to see which callbacks still arrive")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Probe.log(this, "legacy onDeviceDisappeared ${Probe.describe(associationInfo)}")
    }
}
