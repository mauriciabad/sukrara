// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.onenotif

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pictures of docs/36 §8: each option (how the one notification looks) in each state, with made-up values.
 *
 * - `A`: the Live Update chip by default. Android 17 MetricStyle (the value with its arrow and unit, the range or the
 *   alert in words as its label, the slot), Android 16 BigText; promoted, so the chip shows the value.
 * - `B`: the card by default. The renderer's own pictures (header + chart, drawn by a throwaway Robolectric run of
 *   `NotificationCard.renderNotificationArt` and pushed to `files/art/`), colorized, the number as the small icon;
 *   not promoted.
 * - `C`: a "graph" inside a Live Update: ProgressStyle, the last 3 h as 10 segments coloured by range, the trend as
 *   the tracker icon, and a sparkline as the large icon (the only bitmap a promoted notification shows).
 * - `D`: today's two notifications: 0.14.5's Live Update (one metric labelled "mg/dL", *Open Sukrara*) and the alert
 *   (or the *Low alerts muted* note) as a notification of its own.
 *
 * States: `normal`, `ring` (posted on the alarm channel without onlyAlertOnce: sound, heads-up), `between` (the alert
 * still up, back on the silent channel), `muted`. A, B and C are the same id throughout; D adds id 2.
 */
object Mock {
    fun post(
        s: OneService,
        opt: String,
        state: String,
    ): Notification {
        val ring = state == "ring"
        val alertUp = state == "ring" || state == "between"
        val muted = state == "muted"
        val low = alertUp || muted
        val nm = s.getSystemService(NotificationManager::class.java)
        val v = if (low) 62 else 138
        val arrow = if (low) "↘" else "→"
        val art = if (state == "normal") "normal" else if (muted) "muted" else "low"
        // the alert part rides on the glucose notification in A–C; in D it is its own notification
        val merged = opt != "D"
        val channel = if (merged && ring) OneService.ALARM else OneService.LIVE
        val b =
            Notification
                .Builder(s, channel)
                .setOngoing(true)
                .setOnlyAlertOnce(!(merged && ring))
                .setCategory(if (merged && alertUp) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open(s, 1))
                .setShowWhen(false)
        val headline = "Low glucose · Eat carbs"
        val mutedLine = "Low alerts muted · until ${clock(Instant.now().plusSeconds(3600))}"
        when (opt) {
            "A", "D" -> {
                b.setSmallIcon(Icon.createWithBitmap(png(s, "$art-trend")))
                b.setRequestPromotedOngoing(true).setShortCriticalText("$v")
                if (opt == "D") {
                    // 0.14.5: the title, the detail, one metric labelled with the unit, Open Sukrara
                    b.setContentTitle("$v mg/dL $arrow").setContentText(if (low) "Low" else "In range")
                    b.setStyle(Notification.BigTextStyle().bigText(if (low) "Low" else "In range"))
                    if (Build.VERSION.SDK_INT >= 37) {
                        b.setStyle(
                            Notification
                                .MetricStyle()
                                .addMetric(
                                    Notification.Metric(
                                        Notification.Metric.FixedText("$v"),
                                        "mg/dL",
                                        if (low) Notification.SEMANTIC_STYLE_DANGER else Notification.SEMANTIC_STYLE_SAFE,
                                    ),
                                ).setCriticalMetric(0),
                        )
                    }
                    b.addAction(Notification.Action.Builder(null, "Open Sukrara", open(s, 4)).build())
                } else {
                    val label = if (low) "Low" else "In range"
                    // Android 17: the title is the line above the metrics (the headline while an alert is up)
                    val title37 =
                        when {
                            alertUp -> headline
                            muted -> "Low alerts muted"
                            else -> "$v mg/dL $arrow"
                        }
                    b.setContentTitle(
                        if (Build.VERSION.SDK_INT >= 37) title37 else if (alertUp) "$headline · $v mg/dL $arrow" else "$v mg/dL $arrow",
                    )
                    val text =
                        when {
                            alertUp -> "Eat carbs · 93% in range today"
                            muted -> "$mutedLine · 93% in range today"
                            else -> "In range · 93% in range today"
                        }
                    b.setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                    if (Build.VERSION.SDK_INT >= 37) {
                        val style =
                            Notification.MetricStyle().addMetric(
                                Notification.Metric(
                                    Notification.Metric.FixedText("$v $arrow", "mg/dL"),
                                    label,
                                    if (low) Notification.SEMANTIC_STYLE_DANGER else Notification.SEMANTIC_STYLE_SAFE,
                                ),
                            )
                        if (muted) {
                            style.addMetric(
                                Notification.Metric(
                                    Notification.Metric.TimeDifference.forTimer(
                                        Instant.now().plusSeconds(3600),
                                        Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
                                    ),
                                    "Muted for",
                                ),
                            )
                        }
                        style.addMetric(Notification.Metric(Notification.Metric.FixedText("93%"), "In range 1 d"))
                        b.setStyle(style.setCriticalMetric(0))
                    }
                }
            }

            "B" -> {
                val bg = File(s.filesDir, "art/$art-bg.txt").readText().toLong(16).toInt()
                b.setSmallIcon(Icon.createWithBitmap(png(s, "$art-icon")))
                b.setContentTitle(if (alertUp) "$headline · $v mg/dL $arrow" else "$v mg/dL $arrow")
                b.setColorized(true).setColor(bg)
                b.setStyle(Notification.DecoratedCustomViewStyle())
                b.setCustomContentView(views(s, R.layout.card_collapsed, png(s, "$art-collapsed"), null))
                b.setCustomBigContentView(views(s, R.layout.card_expanded, png(s, "$art-header"), png(s, "$art-graph")))
            }

            "C" -> {
                b.setSmallIcon(Icon.createWithBitmap(png(s, "$art-trend")))
                b.setRequestPromotedOngoing(true).setShortCriticalText("$v")
                b.setContentTitle(if (alertUp) "$headline · $v mg/dL $arrow" else "$v mg/dL $arrow")
                b.setContentText(if (alertUp) "Eat carbs · last 3 h" else if (muted) "$mutedLine · last 3 h" else "In range · last 3 h")
                val r = readings(low)
                b.setLargeIcon(sparkline(r))
                b.setStyle(strip(s, r, art))
            }
        }
        if (merged) {
            when {
                alertUp -> {
                    logCarbs(s, b)
                    b.addAction(mute(s, "Mute…"))
                    b.addAction(Notification.Action.Builder(null, "Remind less", noop(s, 5)).build())
                }

                muted -> {
                    logCarbs(s, b)
                    b.addAction(Notification.Action.Builder(null, "Unmute", noop(s, 6)).build())
                    b.addAction(mute(s, "Change time"))
                }
            }
        } else {
            when {
                alertUp -> nm.notify(OneService.ID_ALERT, alert(s, ring))
                muted -> nm.notify(OneService.ID_ALERT, mutedNote(s))
                else -> nm.cancel(OneService.ID_ALERT)
            }
        }
        if (merged) nm.cancel(OneService.ID_ALERT)
        return b.build()
    }

    /** Today's alert notification (AlertNotifier.show): its own id, the alert channel, Log carbs / Mute… / Remind less. */
    private fun alert(
        s: Context,
        ring: Boolean,
    ): Notification =
        Notification
            .Builder(s, OneService.ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Low glucose")
            .setContentText("62 mg/dL ↘")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setGroup("alerts")
            .setShowWhen(true)
            .setOnlyAlertOnce(!ring)
            .apply { if (!ring) setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY) }
            .apply { logCarbs(s, this) }
            .addAction(mute(s, "Mute…"))
            .addAction(Notification.Action.Builder(null, "Remind less", noop(s, 5)).build())
            .build()

    /** Today's *Low alerts muted* note (AlertNotifier.showMuted): silent, a countdown, Log carbs / Unmute / Change time. */
    private fun mutedNote(s: Context): Notification {
        val until = Instant.now().plusSeconds(3600)
        return Notification
            .Builder(s, OneService.ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Low alerts muted")
            .setContentText("Until ${clock(until)}")
            .setWhen(until.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setGroup("alerts")
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .apply { logCarbs(s, this) }
            .addAction(Notification.Action.Builder(null, "Unmute", noop(s, 6)).build())
            .addAction(mute(s, "Change time"))
            .build()
    }

    private fun logCarbs(
        s: Context,
        b: Notification.Builder,
    ) = b.addAction(Notification.Action.Builder(null, "Log carbs", open(s, 3)).build())

    /**
     * `--ez plainMute true`: *Mute…* as a plain button (it would open the mute lengths over the lock screen). A promoted
     * notification drops a direct-reply action (seen on Android 17), so a Live Update needs this one.
     */
    var plainMute = false

    /** *Mute…*: a direct reply whose chips are the lengths, as Sukrara's (or a plain button: [plainMute]). */
    private fun mute(
        s: Context,
        title: String,
    ): Notification.Action =
        if (plainMute) {
            Notification.Action.Builder(null, title, open(s, 2)).build()
        } else {
            remoteMute(s, title)
        }

    private fun remoteMute(
        s: Context,
        title: String,
    ): Notification.Action =
        Notification.Action
            .Builder(
                null,
                title,
                PendingIntent.getBroadcast(s, 8, Intent(s, Noop::class.java), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            ).addRemoteInput(
                RemoteInput
                    .Builder("mute")
                    .setLabel("Mute for")
                    .setChoices(arrayOf("15 min", "30 min", "1 h", "2 h"))
                    .setAllowFreeFormInput(true)
                    .build(),
            ).build()

    private fun views(
        s: Context,
        layout: Int,
        header: Bitmap,
        graph: Bitmap?,
    ) = RemoteViews(s.packageName, layout).apply {
        setImageViewBitmap(R.id.card_header, header)
        if (graph != null) setImageViewBitmap(R.id.card_graph, graph)
    }

    private fun png(
        s: Context,
        name: String,
    ): Bitmap = BitmapFactory.decodeFile(File(s.filesDir, "art/$name.png").path)

    private fun open(
        s: Context,
        code: Int,
    ) = PendingIntent.getActivity(s, code, Intent(s, AlarmActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun noop(
        s: Context,
        code: Int,
    ) = PendingIntent.getBroadcast(s, code, Intent(s, Noop::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun clock(t: Instant) =
        java.text.DateFormat
            .getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(t.toEpochMilli()))

    /** The same made-up day as the renderer's pictures: 5-min readings, the newest last; [low] falls to 62 over 75 min. */
    private fun readings(low: Boolean): List<Int> {
        fun wave(i: Int) = (135 + 45 * sin(2 * PI * (i + 30) / 110.0) + 6 * sin(2 * PI * i / 9.0)).roundToInt()
        return (288 downTo 0).map { i ->
            val base = wave(i)
            when {
                low && i <= 15 -> (62 + (base - 62) * i / 15.0).roundToInt()
                !low && i == 0 -> 138
                else -> base
            }
        }
    }

    private fun rangeColor(v: Int) =
        when {
            v < 70 -> Color.rgb(0xD9, 0x2D, 0x2A)
            v > 180 -> Color.rgb(0xF2, 0x99, 0x1D)
            else -> Color.rgb(0x17, 0xA3, 0x4A)
        }

    /** The last 3 h (36 readings) as 10 segments of 18 min, each coloured by its lowest/highest reading's range. */
    private fun strip(
        s: Context,
        r: List<Int>,
        art: String,
    ): Notification.ProgressStyle {
        val last = r.takeLast(36)
        val style = Notification.ProgressStyle().setStyledByProgress(false).setProgress(180)
        for (k in 0 until 10) {
            val chunk = last.subList((k * 3.6).toInt(), ((k + 1) * 3.6).toInt().coerceAtMost(36))
            val worst = chunk.minByOrNull { if (it < 70) 0 else if (it > 180) 1 else 2 } ?: 120
            style.addProgressSegment(Notification.ProgressStyle.Segment(18).setColor(rangeColor(worst)))
        }
        val firstLow = last.indexOfFirst { it < 70 }
        if (firstLow > 0) style.addProgressPoint(Notification.ProgressStyle.Point(firstLow * 5).setColor(Color.RED))
        style.setProgressTrackerIcon(Icon.createWithBitmap(png(s, "$art-trend")).setTint(Color.WHITE))
        return style
    }

    /** The last 3 h as a line over the target band, the newest reading as a dot: the large icon. */
    private fun sparkline(r: List<Int>): Icon {
        val last = r.takeLast(36)
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(0x2A, 0x2D, 0x31)
        cv.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f, p)

        fun y(v: Int) = size - 16f - (v.coerceIn(40, 260) - 40) / 220f * (size - 32f)
        p.color = Color.argb(70, 0x17, 0xA3, 0x4A)
        cv.drawRect(10f, y(180), size - 10f, y(70), p)
        val path = Path()
        last.forEachIndexed { i, v ->
            val x = 14f + i * (size - 40f) / (last.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.WHITE
        cv.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = rangeColor(last.last())
        cv.drawCircle(size - 26f, y(last.last()), 13f, p)
        return Icon.createWithBitmap(bmp)
    }
}

/** The actions' target in the pictures: does nothing. */
class Noop : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        OneService.log("action ${intent.action}")
    }
}
