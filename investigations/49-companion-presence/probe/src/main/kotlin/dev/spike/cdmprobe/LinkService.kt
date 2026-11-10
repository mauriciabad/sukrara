// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.cdmprobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Stands for Sukrara's MonitoringService: a connectedDevice foreground service; it stays until `stop`. */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("link", "Link", NotificationManager.IMPORTANCE_LOW))
        val notification =
            Notification
                .Builder(this, "link")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("CdmProbe link")
                .setContentText(intent?.getStringExtra("why") ?: "started")
                .build()
        runCatching { startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
            .onSuccess { Probe.log(this, "LinkService in the foreground (${intent?.getStringExtra("why")})") }
            .onFailure { Probe.log(this, "LinkService startForeground failed: ${it.javaClass.simpleName} ${it.message}") }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Probe.log(this, "LinkService stopped")
        super.onDestroy()
    }
}
