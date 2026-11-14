// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.onenotif

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView

/**
 * One foreground-service notification (id [ID_GLUCOSE]) that is the glucose value on a silent channel and, when an
 * alert fires, is posted again with the same id on an alert channel so that it rings; then back to silent. The
 * baseline (`separate`) and the "move the foreground service to the alert" variant (`move` / `back`) are here too.
 *
 * Commands (am start-foreground-service -n dev.spike.onenotif/.OneService --es cmd …):
 * - `start` [--ei v 138] [--ez promo true]: startForeground on `live` (silent, default importance)
 * - `update` [--ei v 72]: the same id on the channel it is on now, setOnlyAlertOnce(true) (a silent value update)
 * - `ring` [--es ch alarm|notif] [--ez fsi true] [--ez promo true] [--ei v 62]: the same id on the alert channel,
 *   no setOnlyAlertOnce, CATEGORY_ALARM, the alert's actions, a full-screen intent
 * - `quiet`: back to `live` (silent)
 * - `separate`: a second notification (id [ID_ALERT]) on the alert channel: today's design
 * - `move`: startForeground(ID_ALERT, ringing alert) then cancel(ID_GLUCOSE); `back`: the reverse
 * - any command: [--es metric sukrara|fixed|""] the Android 17 card's style (see [metric])
 * - `dump`: log every active notification's channel, flags and promotion
 * - `stop`
 */
class OneService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        channels(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val cmd = intent?.getStringExtra("cmd") ?: "start"
        intent?.let {
            if (it.hasExtra("v")) value = it.getIntExtra("v", value)
            if (it.hasExtra("promo")) promo = it.getBooleanExtra("promo", promo)
            if (it.hasExtra("fsi")) fsi = it.getBooleanExtra("fsi", fsi)
            if (it.hasExtra("ch")) alertChannel = it.getStringExtra("ch") ?: alertChannel
            if (it.hasExtra("metric")) metric = it.getStringExtra("metric") ?: ""
        }
        log("cmd=$cmd v=$value promo=$promo fsi=$fsi ch=$alertChannel ringing=$ringing")
        when (cmd) {
            "start" -> {
                ringing = false
                startForeground(ID_GLUCOSE, glucose(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }

            "update", "repost" -> {
                nm.notify(ID_GLUCOSE, glucose())
            }

            "ring" -> {
                ringing = true
                rings++
                nm.notify(ID_GLUCOSE, glucose(alerting = true))
            }

            "quiet" -> {
                ringing = false
                nm.notify(ID_GLUCOSE, glucose())
            }

            "separate" -> {
                rings++
                nm.notify(ID_ALERT, alert())
            }

            "move" -> {
                rings++
                startForeground(ID_ALERT, alert(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                nm.cancel(ID_GLUCOSE)
            }

            "back" -> {
                startForeground(ID_GLUCOSE, glucose(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                nm.cancel(ID_ALERT)
            }

            "stop" -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        dump()
        return START_NOT_STICKY
    }

    /** The glucose notification: silent on `live`, or ringing on the alert channel while [ringing]. */
    private fun glucose(alerting: Boolean = false): Notification {
        val channel = if (ringing) alertChannel else LIVE
        val b =
            Notification
                .Builder(this, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("$value mg/dL ↗")
                .setContentText(if (ringing) "Low glucose · ring $rings" else "In range")
                .setOngoing(true)
                // a silent update must never alert; the ring must (FLAG_ONLY_ALERT_ONCE off for it)
                .setOnlyAlertOnce(!alerting)
                .setCategory(if (ringing) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setDeleteIntent(dismissed())
                .setContentIntent(open(1))
                .setStyle(Notification.BigTextStyle())
        if (promo) b.setRequestPromotedOngoing(true).setShortCriticalText("$value")
        // Android 17: Sukrara 0.14.5's Live Update card (value + its unit as the label), or the value with the arrow
        // and the unit inside FixedText, labelled by what it is (the late time / range in words would go here)
        when (metric) {
            "sukrara" -> {
                b.setStyle(
                    Notification.MetricStyle().addMetric(
                        Notification.Metric(Notification.Metric.FixedText("$value"), "mg/dL", Notification.SEMANTIC_STYLE_SAFE),
                    ),
                )
                b.addAction(Notification.Action.Builder(null, "Open Sukrara", open(4)).build())
            }

            "fixed" -> {
                b.setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.FixedText("$value ↗", "mg/dL"),
                                if (ringing) "Low" else "In range",
                                if (ringing) Notification.SEMANTIC_STYLE_DANGER else Notification.SEMANTIC_STYLE_SAFE,
                            ),
                        ).addMetric(Notification.Metric(Notification.Metric.FixedText("74%"), "in range"))
                        .addMetric(Notification.Metric(Notification.Metric.FixedText("4.5u"), "12m ago")),
                )
            }
        }
        if (ringing) {
            b.addAction(Notification.Action.Builder(null, "Mute…", open(2)).build())
            b.addAction(Notification.Action.Builder(null, "Log carbs", open(3)).build())
            if (fsi && alerting) b.setFullScreenIntent(fullScreen(), true)
        }
        return b.build()
    }

    /** Today's design: the alert as a notification of its own. */
    private fun alert(): Notification =
        Notification
            .Builder(this, alertChannel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Low glucose")
            .setContentText("$value mg/dL ↘ · ring $rings")
            .setCategory(Notification.CATEGORY_ALARM)
            .setDeleteIntent(dismissed())
            .addAction(Notification.Action.Builder(null, "Mute…", open(2)).build())
            .apply { if (fsi) setFullScreenIntent(fullScreen(), true) }
            .build()

    private fun open(code: Int) =
        PendingIntent.getActivity(this, code, Intent(this, AlarmActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun fullScreen() =
        PendingIntent.getActivity(
            this,
            9,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("fsi", true),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun dismissed() =
        PendingIntent.getBroadcast(this, 7, Intent(this, Dismissed::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Every active notification: id, channel, flags (hex), promoted, promotable, the channel's importance. */
    private fun dump() {
        for (sbn in nm.activeNotifications) {
            val n = sbn.notification
            val ch = nm.getNotificationChannel(n.channelId)
            log(
                "active id=${sbn.id} ch=${n.channelId} imp=${ch?.importance} flags=0x${Integer.toHexString(n.flags)} " +
                    "promoted=${n.flags and Notification.FLAG_PROMOTED_ONGOING != 0} promotable=${n.hasPromotableCharacteristics()} " +
                    "fgs=${n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0} ongoing=${n.flags and Notification.FLAG_ONGOING_EVENT != 0} " +
                    "noClear=${n.flags and Notification.FLAG_NO_CLEAR != 0} fsi=${n.fullScreenIntent != null}",
            )
        }
        log("canPostPromoted=${nm.canPostPromotedNotifications()} canFsi=${nm.canUseFullScreenIntent()} dndAccess=${nm.isNotificationPolicyAccessGranted}")
    }

    companion object {
        const val ID_GLUCOSE = 1
        const val ID_ALERT = 2
        const val LIVE = "live"
        const val ALARM = "alarm"
        const val NOTIF = "notif"

        var value = 138
        var promo = true
        var fsi = true
        var alertChannel = ALARM
        var ringing = false
        var rings = 0

        /** "" = BigText; "sukrara" = 0.14.5's MetricStyle; "fixed" = the value with its arrow and unit in FixedText. */
        var metric = ""

        fun log(s: String) = Log.i("OneNotif", s)

        /** live: silent, default importance (Sukrara's glucose.live); alarm: alarm volume + DND bypass; notif: notification volume. */
        fun channels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(LIVE, "Glucose (silent)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(ALARM, "Very low (alarm)", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
                    setBypassDnd(true)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(NOTIF, "Low (notification)", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                    )
                    enableVibration(true)
                },
            )
        }
    }
}

/** The swipe: logged (while ringing it is the answer) and the glucose notification is posted again. */
class Dismissed : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        OneService.log("dismissed ringing=${OneService.ringing}")
        context.startService(Intent(context, OneService::class.java).putExtra("cmd", "repost"))
    }
}

/** The full-screen intent's (and the actions') target. */
class AlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OneService.log("AlarmActivity fsi=${intent.getBooleanExtra("fsi", false)}")
        setContentView(TextView(this).apply { text = "ALARM ${OneService.value}" ; textSize = 48f })
    }
}
