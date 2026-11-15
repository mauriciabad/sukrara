// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.StatusBadge
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.ValueGlyphs
import app.mauri.sukrara.graph.cardModelOf
import app.mauri.sukrara.graph.face
import app.mauri.sukrara.graph.notificationCardStyle
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.toPoint
import app.mauri.sukrara.monitor.alerts.AlertActions
import app.mauri.sukrara.monitor.alerts.AlertCopy
import app.mauri.sukrara.monitor.alerts.GlucoseAlertPresenter
import app.mauri.sukrara.monitor.alerts.GlucoseAlertSink
import app.mauri.sukrara.monitor.notify.AppActivities
import app.mauri.sukrara.monitor.notify.CardFrame
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.GlucoseAlert
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.LiveNotificationContent.Icon
import app.mauri.sukrara.monitor.notify.LiveNotificationContent.Layout
import app.mauri.sukrara.monitor.notify.LiveNotificationContent.Target
import app.mauri.sukrara.monitor.notify.MetricRoom
import app.mauri.sukrara.monitor.notify.NotificationArt
import app.mauri.sukrara.monitor.notify.NotificationCard
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.notify.NotificationPrefs
import app.mauri.sukrara.monitor.notify.PostCounts
import app.mauri.sukrara.monitor.notify.PostGate
import app.mauri.sukrara.monitor.notify.SharingLine
import app.mauri.sukrara.monitor.notify.UiText
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The glucose notification (surface `notification-text`: title, Live Update chip, MetricStyle value and small icon;
 * its custom card is surface `notification`, [NotificationCard]), which is also the monitoring service's foreground
 * notification, so the status bar costs one post per reading. Android decides what it can look like:
 * - **the chip** (Settings › Status bar › *Glucose notification*, the default without the root value) and Live Updates
 *   allowed → a promoted ongoing Live Update: the chip is the small icon (the trend arrow) + `setShortCriticalText`
 *   ("138"). Promotion forbids custom views and colorizing; on Android 17 the expanded card is a `MetricStyle` (the
 *   value styled by range, labelled *Now* or with the late reading's time, then the card's slots), below it BigText.
 * - **the card** (chosen, or automatically while the root value is in the bar, or Live Updates refused) → the
 *   renderer's header row collapsed and header + chart expanded, on the system's notification colour, or in the range
 *   colour (grey once struck through) with *Coloured background*; the small icon is the number itself (*Number
 *   icon*) or the arrow. Without its pictures (the first post, the screen off with an out-of-date card, except for a
 *   ring, which wakes the screen; a drawing failure) it posts the same notification as text, never an old picture.
 *
 * **First in the shade** in every state: a promoted Live Update, or colorized (the range colour, else the shade's own
 * colour, which looks uncoloured), the two ways into System UI's first section ([build]).
 *
 * **It is the alert too** (docs/36, SPEC §1 "Showing an alert"; [GlucoseAlertSink]): while an alert is up it carries
 * the alert's title, buttons and swipe. A ring re-posts it at once on the kind's own channel without only-alert-once
 * (its sound, volume, vibration, Do Not Disturb; an alarm-level ring that opens full screen is a notification of its
 * own for its ring window instead, [GlucoseAlertPresenter]); for the [RING_WINDOW_MS] after it every post stays on that channel (only alert once: a switch to the silent
 * channel would stop the sound), then it goes back to the silent channel still showing the alert. A mute, a clear or
 * a swipe posts on the silent channel at once (the sound stops). Every post builds and notifies under one lock, so a
 * post built before a ring can never land after it.
 */
class LiveNotification(
    private val context: Context,
    /** The hub's state now: what the notification shows when an alert rings before the service has posted (service dead). */
    private val fallbackState: () -> GlucoseState? = { null },
    /** Whether the monitoring service runs (it keeps the notification current; without it an alert's post goes with the alert). */
    private val serviceRunning: () -> Boolean = { MonitoringService.isRunning },
) : GlucoseAlertSink {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val poster = Executors.newSingleThreadScheduledExecutor { Thread(it, "Sukrara.notify").apply { isDaemon = true } }
    private val gate =
        PostGate<Any>(MIN_GAP_MS, SystemClock::elapsedRealtime) { delay, run ->
            poster.schedule(
                { runCatching(run).onFailure { Log.w(TAG, "post failed: ${it.javaClass.simpleName}") } },
                delay,
                TimeUnit.MILLISECONDS,
            )
        }

    /** Its posts since the app started: posted, and skipped as nothing new (Settings › Advanced › Statistics). */
    val counts: StateFlow<PostCounts> get() = gate.counts

    /** The content for [state] now with the status-bar [prefs] (the chip's permission is read here). */
    fun content(
        state: GlucoseState,
        now: Instant,
        prefs: NotificationPrefs = NotificationPrefs(),
    ) = LiveNotificationContent.from(state, now, ::face, prefs.copy(promotionAllowed = promotionAllowed()))

    /** What the service last asked to post: the alert's posts show it again with the alert. */
    private class Last(
        val content: LiveNotificationContent,
        val frame: CardFrame?,
    )

    @Volatile private var last: Last? = null

    /** The alert the notification carries now (null: none), and until when (elapsed ms) a ring keeps its channel. */
    @Volatile private var alert: GlucoseAlert? = null

    @Volatile private var ringUntil = 0L

    /** Every build + notify runs under it, in order: a post built before a ring never lands after it (it would stop the sound). */
    private val postLock = Any()

    /** The Android actions of the alert's buttons. */
    private val alertActions = AlertActions(context)

    /**
     * Posts [content] unless it's what is showing; at most one post per second. [force] = re-post (dismissed, screen
     * on). [frame] = what the custom card draws (null: the card posts as text).
     */
    fun post(
        content: LiveNotificationContent,
        now: Instant,
        frame: CardFrame? = null,
        force: Boolean = false,
    ) {
        last = Last(content, frame)
        gate.submit(listOf(content, alert), force) {
            // drawn and posted off the caller's (main) thread; the gate keeps posts in order
            poster.execute {
                runCatching { notifyNow(content, now, frame, Mode.UPDATE) }
                    .onFailure { Log.w(TAG, "notify failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    /**
     * [GlucoseAlertSink]: the alert to carry and how to post it. A ring is posted at once on the caller's thread and
     * throws when Android refuses it (the runtime then shows the plain stand-in); [GlucoseAlertSink.Post.QUIET] posts at
     * once on the silent channel; an update goes through the gate.
     */
    override fun alert(
        alert: GlucoseAlert?,
        how: GlucoseAlertSink.Post,
    ) {
        val now = Instant.now()
        val shown = last
        val content = shown?.content ?: fallbackContent(now)
        val frame = shown?.frame
        when (how) {
            GlucoseAlertSink.Post.RING -> {
                synchronized(postLock) {
                    this.alert = alert
                    ringUntil = SystemClock.elapsedRealtime() + RING_WINDOW_MS
                    nm.notify(NotificationIds.GLUCOSE, build(content, now, frame, Mode.RING))
                }
                // back to the silent channel when the ring window ends, still showing the alert
                poster.schedule({ windowEnded() }, RING_WINDOW_MS, TimeUnit.MILLISECONDS)
            }

            GlucoseAlertSink.Post.QUIET -> {
                synchronized(postLock) {
                    this.alert = alert
                    ringUntil = 0L
                    if (alert == null && !serviceRunning()) {
                        // posted by an alert while the service was dead: nothing keeps it current, so it goes with the alert
                        runCatching { nm.cancel(NotificationIds.GLUCOSE) }
                    } else {
                        runCatching { nm.notify(NotificationIds.GLUCOSE, build(content, now, frame, Mode.UPDATE)) }
                            .onFailure { Log.w(TAG, "quiet post failed: ${it.javaClass.simpleName}") }
                    }
                }
            }

            GlucoseAlertSink.Post.UPDATE -> {
                this.alert = alert
                if (alert == null && !serviceRunning() && shown == null) {
                    runCatching { nm.cancel(NotificationIds.GLUCOSE) }
                } else if (shown == null) {
                    // no service post yet in this process: shown at once from the hub's state
                    poster.execute { runCatching { notifyNow(content, now, frame, Mode.UPDATE) } }
                } else {
                    post(content, now, frame, force = true)
                }
            }
        }
    }

    /** The ring window ended: posted again on the silent channel, still with the alert (a newer ring keeps its own). */
    private fun windowEnded() {
        if (SystemClock.elapsedRealtime() < ringUntil) return
        val shown = last
        runCatching { notifyNow(shown?.content ?: fallbackContent(Instant.now()), Instant.now(), shown?.frame, Mode.UPDATE) }
            .onFailure { Log.w(TAG, "window end post failed: ${it.javaClass.simpleName}") }
    }

    override fun showing(): Boolean = runCatching { nm.activeNotifications.any { it.id == NotificationIds.GLUCOSE } }.getOrDefault(true)

    /**
     * Android shows the glucose notification now: the person hasn't turned off its channel or the channel's group (both
     * drop every post silently, and cancel what was showing: docs/36 §14), so an alert can ring in it. *Nothing* in
     * Settings is exactly this state; the alerts are notifications of their own then.
     */
    override fun canCarry(): Boolean =
        runCatching {
            val channel = nm.getNotificationChannel(Channels.GLUCOSE) ?: return@runCatching true
            val groupOff = channel.group?.let { nm.getNotificationChannelGroup(it)?.isBlocked } == true
            channel.importance != NotificationManager.IMPORTANCE_NONE && !groupOff && nm.areNotificationsEnabled()
        }.getOrDefault(true)

    private fun notifyNow(
        content: LiveNotificationContent,
        now: Instant,
        frame: CardFrame?,
        mode: Mode,
    ) = synchronized(postLock) { nm.notify(NotificationIds.GLUCOSE, build(content, now, frame, mode)) }

    /** The hub's state as text (no card yet): an alert that rings before the service posted (the service dead). */
    private fun fallbackContent(now: Instant): LiveNotificationContent {
        val state = runCatching(fallbackState).getOrNull()
        return state?.let { content(it, now).copy(layout = Layout.TEXT, chip = null) }
            ?: LiveNotificationContent(
                UiText(R.string.monitor_notif_public_title),
                null,
                strike = false,
                chip = null,
                icon = Icon.APP,
                readingAt = null,
                tap = Target.OpenApp,
                actions = emptyList(),
            )
    }

    /** How a post goes out: [UPDATE] (the ring window's channel while it lasts, else silent), or a ring. */
    enum class Mode { UPDATE, RING }

    /**
     * The notification for [content] with the alert it carries now; never throws for the card: a picture that fails
     * falls back to text. [mode]: a ring posts on the alert's channel without only-alert-once.
     */
    fun build(
        content: LiveNotificationContent,
        now: Instant,
        frame: CardFrame? = null,
        mode: Mode = Mode.UPDATE,
    ): Notification {
        val alert = alert
        val ringing = alert != null && (mode != Mode.UPDATE || SystemClock.elapsedRealtime() < ringUntil)
        val channel = if (ringing) alert.channel else Channels.GLUCOSE
        val promoted = content.layout == Layout.LIVE_UPDATE && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        // the card's pictures first: without them (screen off, a drawing failure) it is a text notification and says so
        val bg = if (content.layout == Layout.CARD) cardColor(content, frame) else null
        // a ring draws even with the screen off: it turns the screen on, and its heads-up must be the card from the start
        val art = if (content.layout == Layout.CARD) frame?.let { cardArt(it, content, bg, now, alert, mode == Mode.RING) } else null
        val title = title(content, alert, now, frame, drawn = art != null)
        val text = text(content, alert, now)
        val builder =
            Notification
                .Builder(context, channel)
                .setSmallIcon(smallIcon(content))
                .setContentTitle(if (content.strike && alert == null) struck(title) else title)
                .setContentText(text)
                .setOngoing(true)
                // a ring alerts (sound, vibration, heads-up) every time; every other post never does
                .setOnlyAlertOnce(mode == Mode.UPDATE)
                .setCategory(if (ringing) alert.category else Notification.CATEGORY_STATUS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(pending(tapOf(content, alert)))
                // Android 14+ lets the person swipe a foreground notification away: with an alert that is its answer
                // and hides it until the next ring; without, the receiver puts the notification back
                .setDeleteIntent(alert?.let { swipedIntent(it) } ?: dismissedIntent())
                .setShowWhen(content.readingAt != null)
                .setStyle(Notification.BigTextStyle().bigText(text))
        content.readingAt?.let { builder.setWhen(it.toEpochMilli()) }
        listOfNotNull(alert?.sub, content.sharing?.let(::sharingText))
            .takeIf { it.isNotEmpty() }
            ?.let { builder.setSubText(it.joinToString(" · ")) }
        // the lock screen (#94): the value, or with the setting off only "Sukrara · Monitoring" (with an alert: its
        // title, no value) until the phone is unlocked
        if (content.lockScreen) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        } else {
            builder.setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion(alert, channel))
        }
        // never a full-screen intent: Android starts one only for a notification it adds, and this one is always
        // there (Android 16 ignores it on an update); an alarm-level ring that opens full screen is its own
        // notification for its ring window (GlucoseAlertPresenter)
        // at most three buttons: the alert's first, then the sensor's
        val words = alert?.let { runCatching { AlertWords.of(context, AlertWords.Ids + AlertCopy.Ids, strict = true) }.getOrNull() }
        val buttons =
            if (alert != null && words != null) {
                // a button that can't be built never takes the foreground service's notification down with it
                runCatching { alert.buttons.map { alertActions.action(alert.kind, it, words, promoted) } }
                    .onFailure { Log.w(TAG, "alert buttons: ${it.javaClass.simpleName}") }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        val sensor = content.actions.map { Notification.Action.Builder(null, resolve(it.label, now), pending(it.target)).build() }
        (buttons + sensor).take(MAX_ACTIONS).forEach(builder::addAction)
        val promotedNow =
            when (content.layout) {
                Layout.LIVE_UPDATE -> {
                    liveUpdate(builder, content, now, frame, alert)
                }

                Layout.CARD -> {
                    card(builder, content, bg, art, alert, ringing, alert?.let { alertLine(it, frame, now) })
                    false
                }

                Layout.TEXT -> {
                    false
                }
            }
        // first in the shade, in every state: System UI's first section holds only a promoted Live Update, a colorized
        // foreground service or a call (ColorizedFgsCoordinator); every other notification sorts by time and sinks under
        // newer ones. Without the range colour it is colorized in the shade's own colour, which System UI then doesn't tint
        if (!promotedNow && bg == null) builder.setColorized(true).setColor(shadeColor(frame))
        return builder.build()
    }

    /**
     * The shade's notification colour (`materialColorSurfaceContainerHigh`, the theme's: [frame]'s, or the configuration
     * now before the first card): a notification colorized in it looks uncoloured.
     */
    private fun shadeColor(frame: CardFrame?): Int {
        val dark =
            frame?.dark ?: (
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            )
        return NotificationCard.surface(context, dark)
    }

    /**
     * The title: with an alert its title ("Low · Eat carbs" in a Live Update, where no pill shows it; the
     * value first on Android 16, whose Live Update has no big value: "62 ↘ · Low glucose · Eat carbs"); without, the
     * value ("138 →", "64 ↘ · Low"), or on Android 17's Live Update the range in words (the value is the metric's).
     */
    private fun title(
        content: LiveNotificationContent,
        alert: GlucoseAlert?,
        now: Instant,
        frame: CardFrame?,
        drawn: Boolean = false,
    ): String {
        val metric = content.layout == Layout.LIVE_UPDATE && Build.VERSION.SDK_INT >= METRIC_STYLE_SDK && content.look != null
        // the card draws the value and the pill; without its pictures the title says them, as the text notification
        val card = content.layout == Layout.CARD && drawn
        if (alert == null) {
            return if (metric) resolve(content.rangeWords ?: content.title, now) else resolve(content.title, now)
        }
        // Android 17's Live Update counts a mute down in a metric of its own: not in the title too
        val words = listOf(alertLine(alert, frame, now, countdown = !metric))
        val value = content.look?.let { resolve(content.title, now) }.takeIf { !metric && !card }
        return listOfNotNull(value?.substringBefore(" · "), *words.toTypedArray()).joinToString(" · ")
    }

    /**
     * The alert's line: its headline, then the pill's words after a " · " in lower case as the headline's own context
     * ("Low · eat carbs"; the card's value box may only have room for the pill's icon); none when the headline already
     * says what to do ("Low · eat more") or while muted.
     */
    private fun alertLine(
        alert: GlucoseAlert,
        frame: CardFrame?,
        now: Instant,
        countdown: Boolean = true,
    ): String {
        val pill =
            pillWords(alert, frame, now, countdown)
                ?.takeIf { " · " !in alert.title }
                ?.replaceFirstChar { it.lowercase(java.util.Locale.getDefault()) }
        return listOfNotNull(alert.title, pill).joinToString(" · ")
    }

    /** The second line: another alert up ("Also: Falling fast"), then the content's own (the sensor, the late time). */
    private fun text(
        content: LiveNotificationContent,
        alert: GlucoseAlert?,
        now: Instant,
    ): String? {
        val own = content.text?.let { resolve(it, now) }
        if (alert == null) return own
        // without a value on screen (the service's first post, or dead) the alert's own value line says it
        val value = alert.body.takeIf { content.look == null }
        val also = alert.also?.let { context.getString(R.string.monitor_alert_also, it) }
        return listOfNotNull(value, also, own).joinToString(" · ").ifEmpty { null }
    }

    /**
     * The card's pill in words: "Eat carbs" / "Take insulin" while an alert asks for it; with [countdown] also a muted
     * side's "29m left" (where no metric counts it down: the card, Android 16).
     */
    private fun pillWords(
        alert: GlucoseAlert,
        frame: CardFrame?,
        now: Instant,
        countdown: Boolean,
    ): String? {
        val status = frame?.state?.alerts?.status ?: return null
        val look = StatusBadge.look(status, now.epochSecond, CardWords.of(context.resources)) ?: return null
        return when {
            alert.mutedUntil != null -> look.text.takeIf { countdown && look.tonal }
            look.tonal -> null
            else -> look.text
        }
    }

    /** With an alert up, the tap opens Add treatment as an answer to it (#39): Carbs on the low side. */
    private fun tapOf(
        content: LiveNotificationContent,
        alert: GlucoseAlert?,
    ): Target =
        when {
            alert == null || content.tap !is Target.QuickTreatment -> content.tap
            else -> Target.QuickTreatment(focusCarbs = alert.kind.side == Side.LOW, alertKind = alert.kind.wire)
        }

    /**
     * What the lock screen shows with *Glucose on the lock screen* off: that Sukrara is monitoring, or the alert's
     * title (never a value) on its channel.
     */
    private fun publicVersion(
        alert: GlucoseAlert?,
        channel: String,
    ): Notification =
        Notification
            .Builder(context, channel)
            .setSmallIcon(app.mauri.sukrara.core.designsystem.R.drawable.ds_ic_status_sukrara)
            .setContentTitle(alert?.title ?: context.getString(R.string.monitor_notif_public_title))
            .setContentText(if (alert == null) context.getString(R.string.monitor_notif_public_text) else null)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

    /**
     * The chip; on Android 17 the expanded card is a MetricStyle (the value by range, the late reading's time, then the
     * card's slots as words). Returns whether promotion was asked for (false: a plain notification).
     */
    private fun liveUpdate(
        b: Notification.Builder,
        content: LiveNotificationContent,
        now: Instant,
        frame: CardFrame?,
        alert: GlucoseAlert?,
    ): Boolean {
        val chip = content.chip ?: return false
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) return false
        // promoted ongoing is missing on some 36 images (NoSuchMethodError): then it stays a plain notification
        runCatching { b.setRequestPromotedOngoing(true).setShortCriticalText(resolve(chip, now)) }.onFailure { return false }
        val look = content.look ?: return true
        if (Build.VERSION.SDK_INT >= METRIC_STYLE_SDK) {
            runCatching { b.setStyle(metricStyle(content, look, now, frame, alert)) }
                .onFailure { Log.w(TAG, "metric style: ${it.javaClass.simpleName}") }
        }
        return true
    }

    /**
     * MetricStyle: the value and its arrow as the face writes them (LO / HI included; never a number of its own, no
     * unit), styled by its range while fresh and plain once struck through, labelled *Now* while fresh and with the
     * reading's time once late (never the range or the unit: the title says the range); while a side is muted its
     * countdown; the rest of the [MAX_METRICS] are the card's slots as the notification card shows them
     * (`GlucoseCard.slotTexts`: time in range, average, the last treatment, over the same windows as home), only while
     * the value is a reading. MetricStyle draws no large icon, so no sparkline (docs/36: the chart is the card's).
     */
    @RequiresApi(METRIC_STYLE_SDK)
    private fun metricStyle(
        content: LiveNotificationContent,
        look: GlucoseLook,
        now: Instant,
        frame: CardFrame?,
        alert: GlucoseAlert?,
    ): Notification.MetricStyle {
        val bands = frame?.config?.bands ?: Bands()
        val label = metricLabel(content, look, alert, frame?.config?.unit ?: GlucoseUnit.MGDL)
        // text only (docs/36 §10): a metric's value is a CharSequence, a picture can't travel in it, System UI draws it in
        // its own style (a bold span is dropped), and the system font has no heavier arrow set in all eight directions
        // (Supplemental Arrows-C is missing; the Dingbats / Misc arrows mix weights): THE text arrow
        // the value whole in its cell (docs/36 §14): a thin space, then fewer of the card's slots, before Android's "…"
        val (shown, maxMetrics) = MetricRoom.value(context, look.text, look.arrowText, MAX_METRICS)
        val value =
            if (look.strike || content.mgdl == null) {
                Notification.Metric(Notification.Metric.FixedText(shown), label)
            } else {
                Notification.Metric(Notification.Metric.FixedText(shown), label, semantic(content.mgdl, bands))
            }
        val style = Notification.MetricStyle().addMetric(value)
        alert?.mutedUntil?.let { until ->
            style.addMetric(
                Notification.Metric(
                    Notification.Metric.TimeDifference.forTimer(
                        Instant.ofEpochSecond(until),
                        Notification.Metric.TimeDifference.FORMAT_ADAPTIVE,
                    ),
                    // the title already says which side is muted
                    context.getString(R.string.monitor_metric_mute_left),
                ),
            )
        }
        val room = maxMetrics - style.metrics.size
        if (frame != null && content.mgdl != null && room > 0) {
            val model = cardModelOf(frame.state, frame.treatments, now, CardWords.of(context.resources), frame.slots(now))
            GlucoseCard.slotTexts(model, notificationCardStyle(frame.config), room).forEach { (value, label) ->
                style.addMetric(Notification.Metric(Notification.Metric.FixedText(value), label))
            }
        }
        return style.setCriticalMetric(0)
    }

    /**
     * The value metric's label (the owner, 2026-11-14: "Now" said nothing): the reading's time once it is late; while
     * fresh, the trend as a label ("Falling", "Steady", [CardWords.trendLabel]: words that fit a third of the card in
     * every language), or, when the alert's title already says the trend (*Falling fast*, *Bajada rápida*), the rate
     * ("2.4/min"): never what the title says. Sentence case, as every label.
     */
    internal fun metricLabel(
        content: LiveNotificationContent,
        look: GlucoseLook,
        alert: GlucoseAlert?,
        unit: GlucoseUnit,
    ): String {
        content.readingAt?.let { return Freshness.timeText(it.epochSecond, Instant.now().epochSecond) }
        val words = CardWords.of(context.resources)
        val trend = words.trendLabel(look.band.takeIf { look.trend == TrendMark.ARROW })
        val rate = look.rate?.takeIf { look.trend == TrendMark.ARROW }
        val repeats = alert != null && alert.title.contains(trend, ignoreCase = true)
        val label =
            if (repeats && rate != null) {
                context.getString(R.string.monitor_metric_rate, unit.rateText(kotlin.math.abs(rate), words.locale))
            } else {
                trend
            }
        return label.replaceFirstChar { it.titlecase(words.locale) }
    }

    /** The value's style by the person's ranges (a limit in the range beyond it, [Bands.of]): danger low, caution high. */
    @RequiresApi(METRIC_STYLE_SDK)
    private fun semantic(
        mgdl: Int,
        bands: Bands,
    ): Int =
        when (bands.sane().of(mgdl).group) {
            -1 -> Notification.SEMANTIC_STYLE_DANGER
            1 -> Notification.SEMANTIC_STYLE_CAUTION
            else -> Notification.SEMANTIC_STYLE_SAFE
        }

    /**
     * The card: on the system's notification colour, or with *Coloured background* colorized in the range colour; when
     * [frame] is drawn, the renderer's header row and
     * chart as custom views, with the alert's title over them while one is up (and as the heads-up while it rings).
     */
    private fun card(
        b: Notification.Builder,
        content: LiveNotificationContent,
        bg: Int?,
        art: NotificationArt?,
        alert: GlucoseAlert?,
        ringing: Boolean,
        line: String?,
    ) {
        if (content.look == null || content.mgdl == null) return
        bg?.let { b.setColorized(true).setColor(it) }
        if (art == null) return
        // TalkBack: the card's one sentence, as on every other surface (never the title's arrow); the alert's title first
        val description = listOfNotNull(line, art.description).joinToString(". ")
        val title = line
        val ink = bg?.let(NotificationCard::ink)
        b
            .setShowWhen(false) // the late time is in the card
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.monitor_notif_card_collapsed, art.collapsed, null, description, null, ink))
            .setCustomBigContentView(views(R.layout.monitor_notif_card_expanded, art.header, art.graph, description, title, ink))
        if (title != null && ringing) {
            b.setCustomHeadsUpContentView(views(R.layout.monitor_notif_card_heads_up, art.header, null, description, title, ink))
        }
    }

    /** The card's ground with *Coloured background* (null: the system's notification colour). */
    private fun cardColor(
        content: LiveNotificationContent,
        frame: CardFrame?,
    ): Int? {
        val look = content.look ?: return null
        val mgdl = content.mgdl ?: return null
        return if (content.colorize) NotificationCard.background(look, mgdl, frame?.config ?: GraphConfig()) else null
    }

    /**
     * The card's pictures for this post, or null (none yet, the screen off, a failure: text then). [screenOff]: drawn
     * with the screen off too (a ring).
     */
    private fun cardArt(
        frame: CardFrame,
        content: LiveNotificationContent,
        bg: Int?,
        now: Instant,
        alert: GlucoseAlert?,
        screenOff: Boolean = false,
    ): NotificationArt? {
        if (content.look == null || content.mgdl == null) return null
        return art(frame, content, bg, now, alert?.rangeSaid == true, screenOff)
    }

    private fun views(
        layout: Int,
        header: Bitmap,
        graph: Bitmap?,
        description: String,
        title: String?,
        ink: Int?,
    ) = RemoteViews(context.packageName, layout).apply {
        setImageViewBitmap(R.id.monitor_notif_header, header)
        setContentDescription(R.id.monitor_notif_header, description)
        if (graph != null) setImageViewBitmap(R.id.monitor_notif_graph, graph)
        if (layout != R.layout.monitor_notif_card_collapsed) {
            setViewVisibility(R.id.monitor_notif_title, if (title != null) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(R.id.monitor_notif_title, title.orEmpty())
            // on the range colour the title takes the card's ink (the system tints only its own template's text)
            ink?.let { setTextColor(R.id.monitor_notif_title, it) }
        }
    }

    private var artCache: NotificationArt? = null
    private var artKey: Any? = null
    private var graphKey: Any? = null

    /**
     * The card's pictures, redrawn only when what they show changed (the content's card key: reading, minute,
     * treatments, settings, theme), the chart at most every 5 minutes besides a new reading. While the screen is off
     * nothing is drawn: an up-to-date picture is reused, an out-of-date one is dropped (the text shows instead) and the
     * screen-on re-post draws it. A failure is logged and posts text.
     */
    @Synchronized
    private fun art(
        frame: CardFrame,
        content: LiveNotificationContent,
        bg: Int?,
        now: Instant,
        rangeSaid: Boolean,
        screenOff: Boolean,
    ): NotificationArt? {
        val quiet = frame.state.alerts.treatmentQuiet
        val key =
            listOf(
                content.cardKey,
                content.look,
                bg,
                rangeSaid,
                frame.config,
                frame.dark,
                frame.state.latest,
                frame.state.sensor,
                frame.stored.size,
                quiet,
                // the pill under the value (an alert asking, a mute counting down) is drawn from it
                frame.state.alerts.status,
            )
        val gKey = listOf(frame.config, frame.dark, frame.state.latest, frame.treatments, quiet, now.epochSecond / GRAPH_EVERY_S)
        val cached = artCache
        if (cached != null && key == artKey && gKey == graphKey) return cached
        if (!screenOff && !context.getSystemService(PowerManager::class.java).isInteractive) return null
        return runCatching {
            val model = cardModelOf(frame.state, frame.treatments, now, CardWords.of(context.resources), frame.slots(now))
            val redrawGraph = cached?.graph == null || gKey != graphKey
            val drawn =
                NotificationCard.renderNotificationArt(
                    context,
                    model,
                    frame.config,
                    bg,
                    frame.dark,
                    NotificationCard.columnDp(context),
                    redrawGraph,
                    rangeSaid,
                )
            NotificationArt(drawn.header, drawn.collapsed, if (redrawGraph) drawn.graph else cached?.graph, drawn.description)
        }.onSuccess {
            artCache = it
            artKey = key
            graphKey = gKey
        }.onFailure { Log.w(TAG, "card: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    /** Drops a post still waiting in the gate (the service is going away). */
    fun cancelPending() = gate.cancelPending()

    /**
     * The trend arrow as the renderer draws it (its continuous angle), the number with or without it, or the state's
     * icon.
     */
    private fun smallIcon(content: LiveNotificationContent): android.graphics.drawable.Icon =
        when {
            content.icon == Icon.TREND -> {
                android.graphics.drawable.Icon
                    .createWithBitmap(ValueGlyphs.trendIcon(content.trendRate, content.trendBand))
            }

            content.icon == Icon.NUMBER && content.look != null -> {
                android.graphics.drawable.Icon
                    .createWithBitmap(ValueGlyphs.icon(content.look))
            }

            content.icon == Icon.VALUE && content.look != null -> {
                android.graphics.drawable.Icon
                    .createWithBitmap(ValueGlyphs.icon(content.look, trend = false))
            }

            else -> {
                android.graphics.drawable.Icon
                    .createWithResource(context, iconRes(content.icon))
            }
        }

    /** Whether Android lets the app post Live Updates now (the person can turn them off per app). */
    fun promotionAllowed(): Boolean =
        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 &&
            runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)

    private fun resolve(
        text: UiText,
        now: Instant,
    ): String {
        val args =
            text.args.map {
                when (it) {
                    is Instant -> Freshness.timeText(it.epochSecond, now.epochSecond)
                    is UiText -> resolve(it, now)
                    else -> it
                }
            }
        return context.getString(text.res, *args.toTypedArray())
    }

    /**
     * "2 followers · 1 nearby · Nightscout API on Wi-Fi" (nearby only while a follower device is connected over
     * Bluetooth, the API only while it listens on the home Wi-Fi).
     */
    private fun sharingText(line: SharingLine): String {
        val r = context.resources
        return buildList {
            if (line.followers > 0 ||
                line.nearby > 0
            ) {
                add(r.getQuantityString(R.plurals.monitor_sub_followers, line.followers, line.followers))
            }
            if (line.nearby > 0) add(r.getQuantityString(R.plurals.monitor_sub_nearby, line.nearby, line.nearby))
            if (line.wifi) add(r.getString(R.string.monitor_sub_wifi))
        }.joinToString(" · ")
    }

    private fun struck(text: String): CharSequence =
        SpannableString(text).apply { setSpan(StrikethroughSpan(), 0, text.indexOf(' ').takeIf { it > 0 } ?: text.length, 0) }

    private fun pending(target: Target): PendingIntent =
        when (target) {
            is Target.QuickTreatment -> {
                val quick =
                    Intent(NavTargets.ACTION_QUICK_TREATMENT)
                        .setPackage(context.packageName)
                        .putExtra(NavTargets.EXTRA_FOCUS_CARBS, target.focusCarbs)
                        .apply { target.alertKind?.let { putExtra(NavTargets.EXTRA_ALERT_KIND, it) } }
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // until the quick treatment dialog is in this build, the tap opens the app
                val intent = if (hasQuickTreatment) quick else launchIntent()
                val code = REQUEST_QUICK + (if (target.focusCarbs) 1 else 0) + if (target.alertKind != null) 2 else 0
                PendingIntent.getActivity(context, code, intent, IMMUTABLE)
            }

            Target.OpenApp -> {
                PendingIntent.getActivity(context, REQUEST_OPEN, launchIntent(), IMMUTABLE)
            }

            Target.SensorSetup -> {
                // the app's non-exported trampoline: no other app can open the flow
                val setup =
                    Intent(NavTargets.ACTION_SENSOR_SETUP)
                        .setClassName(context.packageName, NavTargets.SENSOR_SETUP_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(context, REQUEST_SENSOR, setup, IMMUTABLE)
            }
        }

    // the app's activities are fixed at build time: resolved once, not on every post
    // (also found before the first unlock, when a plain lookup sees only direct-boot-aware activities)
    private val hasQuickTreatment by lazy { AppActivities.handles(context, NavTargets.ACTION_QUICK_TREATMENT) }

    private fun launchIntent(): Intent = AppActivities.launch(context)

    /**
     * The swipe while [alert] is up: its answer (#39) and seen until the next ring (#195), named by its firing (a data
     * URI keeps each firing's PendingIntent apart), then the glucose notification comes back without it, silently.
     */
    private fun swipedIntent(alert: GlucoseAlert): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_SWIPED,
            Intent(context, AlertActionReceiver::class.java)
                .setAction(AlertActionReceiver.TIDY)
                .setData(Uri.fromParts("sukrara-glucose", alert.firingId ?: "muted-${alert.kind.wire}", null))
                .putExtra(AlertActionReceiver.KIND, alert.kind.wire)
                .putExtra(AlertActionReceiver.FIRING, alert.firingId)
                .putExtra(AlertActionReceiver.GLUCOSE, true),
            IMMUTABLE,
        )

    private fun dismissedIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISSED,
            Intent(context, HealthCheckReceiver::class.java).setAction(MonitorReceiver.ACTION_GLUCOSE_DISMISSED),
            IMMUTABLE,
        )

    companion object {
        private const val TAG = "Sukrara.notify"
        private const val MIN_GAP_MS = 1_000L
        private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val REQUEST_QUICK = 10
        private const val REQUEST_OPEN = 20
        private const val REQUEST_SENSOR = 30
        private const val REQUEST_DISMISSED = 40
        private const val REQUEST_SWIPED = 41

        /**
         * After a ring every post stays on the alert's channel this long (only alert once), so the sound plays out; the
         * same 10 s as the alert's value guard ([app.mauri.sukrara.monitor.alerts.AlertLiveValue.AFTER_RING_S]).
         */
        const val RING_WINDOW_MS = 10_000L

        /** Android shows at most three actions. */
        private const val MAX_ACTIONS = 3

        /** Android 17: `Notification.MetricStyle`. */
        private const val METRIC_STYLE_SDK = 37

        /** MetricStyle shows up to three metrics. */
        private const val MAX_METRICS = 3

        /** The chart is redrawn at most this often without a new reading. */
        private const val GRAPH_EVERY_S = 300L

        /** The card's face for a hub state: value (in the person's [unit]), trend mark and freshness. */
        fun face(
            state: GlucoseState,
            now: Instant,
            unit: GlucoseUnit,
        ): GlucoseLook = GlucoseCard.face(state, now.epochSecond, CardSurface.NOTIFICATION_TEXT, unit)

        /** A state's icon resource ([Icon.TREND] is drawn by the renderer instead). */
        fun iconRes(icon: Icon): Int =
            when (icon) {
                Icon.TREND, Icon.NO_TREND, Icon.NUMBER, Icon.VALUE -> R.drawable.monitor_ic_no_trend
                Icon.NO_READINGS -> R.drawable.monitor_ic_no_readings
                Icon.SENSOR_ENDED -> R.drawable.monitor_ic_sensor_ended
                Icon.APP -> app.mauri.sukrara.core.designsystem.R.drawable.ds_ic_status_sukrara
            }
    }
}
