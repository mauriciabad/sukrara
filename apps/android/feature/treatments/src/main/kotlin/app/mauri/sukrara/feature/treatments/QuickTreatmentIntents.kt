// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.content.Context
import android.content.Intent
import app.mauri.sukrara.core.model.NavTargets

/** Where a quick Add treatment was opened from (kept for the dialog's choices, e.g. the tile collapses the shade). */
enum class QuickSource { Notification, Widget, Tile, Home }

/**
 * The intents that open the quick Add treatment dialog over whatever is on screen (widgets, the glucose
 * notification, the Quick Settings tile). Explicit (component set): the activity isn't exported.
 */
object QuickTreatmentIntents {
    const val EXTRA_SOURCE = "app.mauri.sukrara.extra.QUICK_SOURCE"

    /** [focusCarbs] = open with Carbs focused (a low is going on); otherwise the dialog decides from the state. */
    fun open(
        context: Context,
        source: QuickSource,
        focusCarbs: Boolean = false,
    ): Intent =
        Intent(NavTargets.ACTION_QUICK_TREATMENT)
            .setClassName(context.packageName, QuickTreatmentActivity::class.java.name)
            .putExtra(EXTRA_SOURCE, source.name)
            .putExtra(NavTargets.EXTRA_FOCUS_CARBS, focusCarbs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun focusCarbs(intent: Intent?): Boolean = intent?.getBooleanExtra(NavTargets.EXTRA_FOCUS_CARBS, false) == true

    /** Long-acting focused: *Add treatment* after a rapid pen's read (the other insulin). */
    internal fun focusLong(intent: Intent?): Boolean = intent?.getBooleanExtra(NavTargets.EXTRA_FOCUS_LONG, false) == true

    /** *When* to start with: a pen read's injection (*Add treatment* on its card), else null (now). */
    internal fun at(intent: Intent?): java.time.Instant? =
        intent?.takeIf { it.hasExtra(NavTargets.EXTRA_AT) }?.let { java.time.Instant.ofEpochMilli(it.getLongExtra(NavTargets.EXTRA_AT, 0)) }
}
