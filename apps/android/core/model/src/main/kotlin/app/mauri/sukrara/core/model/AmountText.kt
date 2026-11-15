// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A treatment amount as the person reads it: whole without decimals ("4"), else one decimal with the language's
 * decimal separator ("4.5", Spanish "4,5"), like [GlucoseUnit.text]. The card, its labels, its rings and every list,
 * sheet and dialog that names an amount write it through here; the wire formats (Nightscout, LibreView, the exports)
 * keep their point.
 */
object AmountText {
    fun of(
        a: Double,
        locale: Locale,
    ): String =
        if (a % 1.0 == 0.0) {
            "${a.toLong()}"
        } else {
            String.format(Locale.ROOT, "%.1f", a).replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
        }

    /** Insulin units as the card writes them: "4.5u", "4,5u", "12u". */
    fun units(
        a: Double,
        locale: Locale,
    ): String = of(a, locale) + "u"
}
