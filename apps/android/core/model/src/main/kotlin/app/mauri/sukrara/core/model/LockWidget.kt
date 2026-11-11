// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * What is behind the lock-screen widget (setting `lock.background`; docs/17 "Contrast on busy wallpapers"): Material's
 * text protection for a value over a wallpaper. Card, frosted glass and dim are drawn by the System UI module around
 * the widget; the shadow by the widget itself.
 */
enum class LockBackground(
    /** The stored value. */
    val key: String,
) {
    /** On the wallpaper, like the clock. */
    NONE("none"),

    /** A soft shadow around every number, ring and line. */
    SHADOW("shadow"),

    /** Material's gradient scrim over the bottom of the screen. */
    DIM("dim"),

    /** A rounded card like the notifications. */
    CARD("card"),

    /** The wallpaper blurred and tinted behind the widget (System UI's own blur; the card where blur is off). */
    FROSTED("frosted"),
    ;

    /** Card and frosted glass have an edge: the widget keeps its content off it. */
    val boxed: Boolean get() = this == CARD || this == FROSTED

    companion object {
        val DEFAULT = FROSTED

        /** An unknown value is the default (no backwards compatibility). */
        fun of(key: String?): LockBackground = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The lock-screen widget's colours (setting `lock.appearance`): the home widgets' light or dark palette. */
enum class LockAppearance(
    /** The stored value. */
    val key: String,
) {
    /** Follows the system's dark theme, like the lock screen's notifications. */
    AUTO("auto"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Whether it draws dark while the system's dark theme is [systemNight]. */
    fun dark(systemNight: Boolean): Boolean =
        when (this) {
            LIGHT -> false
            DARK -> true
            AUTO -> systemNight
        }

    companion object {
        val DEFAULT = AUTO

        fun of(key: String?): LockAppearance = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The lock-screen widget's look (Settings › Lock screen). */
data class LockLook(
    val background: LockBackground = LockBackground.DEFAULT,
    val appearance: LockAppearance = LockAppearance.DEFAULT,
)

/**
 * The names both sides of the lock-screen widget use (#66, docs/17): System UI's module hosts the app's widget
 * provider [RECEIVER] and tells it, in the widget's options, where the lock icon is ([HOLE_LEFT], [HOLE_RIGHT],
 * [HOLE_BOTTOM]: dp from the widget's top-left) and when it (re)attached ([STAMP]: a new value makes the provider hear
 * of the widget again, its only way to learn the id System UI bound).
 */
object LockWidgetLink {
    const val RECEIVER = "app.mauri.sukrara.widgets.LockWidgetReceiver"
    const val HOLE_LEFT = "sukrara.lockHoleLeft"
    const val HOLE_RIGHT = "sukrara.lockHoleRight"
    const val HOLE_BOTTOM = "sukrara.lockHoleBottom"
    const val STAMP = "sukrara.lockStamp"
}
