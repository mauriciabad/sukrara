// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.dialog

import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.settings.NumberSpec

/**
 * What [NumberDialog] offers and accepts for one setting, without UI (NumberDialogTest). Values in and out are always
 * stored values (mg/dL for a glucose format). [presets] = the options (stored; null = the format's own in the shown
 * unit), [keep] narrows them; [customMin] raises the lowest custom value; [reject] returns a message for a value that is
 * valid but not allowed here (e.g. a line that's already drawn). A glucose format in [unit] mmol/L is shown and typed
 * in its schema mmol/L variant ([NumberSpec.inUnit]: its presets, range and step) and what is picked or typed is stored
 * on the format's step ([NumberSpec.stored]); the Mac's NumberSettingRow does the same.
 */
class NumberInput(
    val format: NumberSpec,
    private val presets: List<Double>? = null,
    private val keep: (Double) -> Boolean = { true },
    val customMin: Double = format.min,
    private val reject: (Double) -> String? = { null },
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
) {
    /** The same input in [other] (what [NumberDialog] does with the screen's unit); itself when nothing changes. */
    fun inUnit(other: GlucoseUnit): NumberInput =
        if (other == unit || format.mmol == null) this else NumberInput(format, presets, keep, customMin, reject, other)

    /** The format as shown and typed: itself, or its mmol/L variant. */
    val spec: NumberSpec = format.inUnit(unit)

    /** A stored value in the shown unit. */
    private fun shown(v: Double): Double = format.shown(v, unit)

    /** The radio options (stored values): special values (sorted, e.g. 0 = "Off") first, then the presets. */
    val options: List<Double> =
        (presets ?: spec.presets.map { format.stored(it, unit) })
            .filter(keep)
            .let { p ->
                format.special.keys
                    .filter { k -> p.none { same(it, k) } }
                    .sorted() + p
            }.distinct()

    /** Whether the dialog shows a "Custom" choice with a number field. */
    val hasCustom: Boolean get() = spec.custom

    /** Whether [value] is one of the radio options (else "Custom" starts selected with [value] in the field). */
    fun isOption(value: Double): Boolean = options.any { same(it, value) }

    /**
     * Whether two stored values are the same option: the same number as shown (doubles from storage never compare
     * exactly; 140 and 141 mg/dL are both 7.8 mmol/L).
     */
    fun same(
        a: Double,
        b: Double,
    ): Boolean = kotlin.math.abs(shown(a) - shown(b)) < EPS

    /** The text the field starts with for the current [value]. */
    fun initialText(value: Double): String = if (isOption(value)) "" else spec.text(shown(value))

    /** Keeps only what a number field takes: digits, and one decimal separator when the spec has decimals. */
    fun filter(typed: String): String {
        var separator = false
        return typed
            .filter { c ->
                when {
                    c.isDigit() -> true
                    spec.decimals > 0 && (c == '.' || c == ',') && !separator -> true.also { separator = true }
                    else -> false
                }
            }.take(MAX_CHARS)
    }

    /** The result of the typed [text] (shown unit): the stored value to save, or why it can't be saved. */
    fun check(text: String): Check {
        if (text.isEmpty()) return Check.Empty
        val typed = spec.parse(text) ?: return Check.OutOfRange
        val v = format.stored(typed, unit).takeIf { it >= customMin - EPS } ?: return Check.OutOfRange
        return reject(v)?.let { Check.Rejected(it) } ?: Check.Ok(v)
    }

    /** The lowest custom value as shown (for the range text). */
    val shownMin: String get() = spec.text(maxOf(shown(customMin), spec.min))

    val shownMax: String get() = spec.text(spec.max)

    sealed interface Check {
        data object Empty : Check

        data object OutOfRange : Check

        data class Rejected(
            val message: String,
        ) : Check

        data class Ok(
            val value: Double,
        ) : Check
    }

    private companion object {
        const val MAX_CHARS = 6
        const val EPS = 1e-9
    }
}
