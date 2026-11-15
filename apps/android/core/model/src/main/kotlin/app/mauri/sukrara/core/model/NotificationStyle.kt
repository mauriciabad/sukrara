// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * Where the glucose shows in the status bar, and so what the glucose notification can be (docs/36 §11, §14; Settings ›
 * Status bar & notification and setup's *Glucose notification* step): one choice, because a Live Update can't hold a
 * graph, the root text makes the chip and the value icon redundant, and nothing in the bar means no notification.
 * [key] = the stored value (`statusbar.show`).
 */
enum class NotificationStyle(
    val key: String,
) {
    /** The Live Update chip next to the clock; the notification is the basic Live Update (no graph). */
    Chip("chip"),

    /** The notification's icon ([NotificationIcon]) shows the glucose; the notification is the card with the graph. */
    Icon("icon"),

    /** Root: the value as text or icons in the status bar; the notification is the card, or none. */
    Text("text"), // i18n-ok: the stored key

    /**
     * Nothing in the status bar and no glucose notification. Android shows a foreground service's notification
     * whatever the app asks (docs/36 §14), so the person turns it off in Android's settings; monitoring goes on, and
     * every alert is a notification of its own.
     */
    Off("off"),
    ;

    /** The notification is part of this choice (the chip and the icon are the notification's). */
    val needsNotification: Boolean get() = this == Chip || this == Icon

    companion object {
        val DEFAULT = Chip

        fun of(key: String?): NotificationStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The glucose notification's icon in the status bar (`statusbar.notifIcon`, Settings › Status bar & notification):
 * drawn by the renderer's glyphs ([app.mauri.sukrara.graph.ValueGlyphs]) except [App]. [None] is only with the root
 * text: the LSPosed module hides the icon while its own value shows (root alone can't hide one app's notification
 * icon); without the module the icon stays [ValueTrend], so the glucose never leaves the bar.
 */
enum class NotificationIcon(
    val key: String,
) {
    ValueTrend("valueTrend"),
    Value("value"),
    Trend("trend"),
    App("app"),
    None("none"),
    ;

    companion object {
        val DEFAULT = ValueTrend

        fun of(key: String?): NotificationIcon = entries.firstOrNull { it.key == key } ?: DEFAULT

        /** The icons a [style] offers: the chip's icon is its arrow (none to pick); hiding it only with the root text. */
        fun offered(style: NotificationStyle): List<NotificationIcon> =
            when (style) {
                NotificationStyle.Icon -> listOf(ValueTrend, Value, Trend, App)
                NotificationStyle.Text -> entries
                NotificationStyle.Chip, NotificationStyle.Off -> emptyList()
            }
    }
}

/** The glucose notification's channel id (the monitor posts on it; Settings reads whether Android shows it). */
object GlucoseNotificationChannel {
    const val ID = "glucose.live"
}
