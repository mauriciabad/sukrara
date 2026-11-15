// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.min

/**
 * How many metrics the Live Update's MetricStyle can hold with the value whole (docs/36 §14). Android gives every
 * metric the same width ("Each metric receives equal horizontal space, regardless of its content",
 * developer.android.com/develop/ui/views/notifications/metric-style) and ends a value that doesn't fit with "…"
 * (`NotificationMetricValue`: `ellipsize end`, one line). The value's style differs by release: Android 17.0 draws it
 * in `variable-display-small-emphasized-narrow`, 17.1 in the wider `variable-display-small-emphasized` (Google Sans
 * Flex on a Pixel), where "138 →" needs about 94 dp of a third's 94.5 dp and lost its arrow on the owner's phone. So
 * the value is measured here in the system's own style and font, and the card's slots give way until it fits.
 */
internal object MetricRoom {
    /** The expanded metric row's gap between two metrics (`notification_expanded_metric_margin`). */
    private const val GAP_DP = 4f

    /** System UI's notification side padding in the shade on a phone (`notification_side_paddings`). */
    private const val SHADE_SIDE_DP = 16f

    /** Kept free in a cell besides the value: rounding, the font's side bearings. */
    private const val SLACK_DP = 4f

    /**
     * The most metrics (≤ [max]) whose cell holds a value [valuePx] wide, in a row [rowPx] wide with [gapPx] between
     * cells and [slackPx] kept free; at least one (a single metric is laid out alone, full width).
     */
    fun metrics(
        valuePx: Float,
        rowPx: Float,
        gapPx: Float,
        slackPx: Float,
        max: Int,
    ): Int {
        for (n in max downTo 2) {
            val cell = (rowPx - (n - 1) * gapPx) / n
            if (valuePx + slackPx <= cell) return n
        }
        return 1
    }

    /**
     * The value as written ([forms]: the face's text and arrow with a space, then with a thin space) and how many
     * metrics it leaves room for: the first form that fits beside [max] metrics, else the first form with fewer.
     */
    fun pick(
        forms: List<String>,
        measure: (String) -> Float,
        rowPx: Float,
        gapPx: Float,
        slackPx: Float,
        max: Int,
    ): Pair<String, Int> {
        for (form in forms) if (metrics(measure(form), rowPx, gapPx, slackPx, max) == max) return form to max
        val first = forms.first()
        return first to metrics(measure(first), rowPx, gapPx, slackPx, max)
    }

    /** The value's text and arrow, written to fit, and the metrics it leaves room for on this phone ([pick]). */
    fun value(
        context: Context,
        text: String?,
        arrow: String?,
        max: Int,
    ): Pair<String, Int> {
        val parts = listOfNotNull(text, arrow)
        val forms = listOf(parts.joinToString(" "), parts.joinToString(THIN_SPACE)).distinct()
        return runCatching {
            val dm = context.resources.displayMetrics
            val dp = dm.density
            val sys = Resources.getSystem()
            val start = systemDimen(sys, "notification_content_margin_start", "notification_2025_content_margin_start") ?: (72 * dp)
            val end = systemDimen(sys, "notification_margin", "notification_2025_margin") ?: (16 * dp)
            // the narrower side: the shade is never wider than the phone held upright
            val row = min(dm.widthPixels, dm.heightPixels) - 2 * SHADE_SIDE_DP * dp - start - end
            val paint = valuePaint(context)
            pick(forms, paint::measureText, row, GAP_DP * dp, SLACK_DP * dp, max)
        }.getOrDefault(forms.first() to max)
    }

    const val THIN_SPACE = "\u2009"

    /** The system's `NotificationExpandedMetricValue` style (28 sp, its font and features) as a paint. */
    @SuppressLint("DiscouragedApi", "ResourceType")
    private fun valuePaint(context: Context): TextPaint {
        val sys = Resources.getSystem()
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        val styleId = sys.getIdentifier("NotificationExpandedMetricValue", "style", "android")
        var size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28f, context.resources.displayMetrics)
        var family: String? = null
        var features: String? = "tnum"
        if (styleId != 0) {
            val attrs = intArrayOf(android.R.attr.textSize, android.R.attr.fontFamily, android.R.attr.fontFeatureSettings)
            context.obtainStyledAttributes(styleId, attrs).apply {
                size = getDimension(0, size)
                family = getString(1) ?: family
                features = getString(2) ?: features
                recycle()
            }
        }
        paint.textSize = size
        paint.typeface = family?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
        paint.fontFeatureSettings = features
        return paint
    }

    @SuppressLint("DiscouragedApi")
    private fun systemDimen(
        sys: Resources,
        vararg names: String,
    ): Float? =
        names.firstNotNullOfOrNull { name ->
            sys.getIdentifier(name, "dimen", "android").takeIf { it != 0 }?.let(sys::getDimension)
        }
}
