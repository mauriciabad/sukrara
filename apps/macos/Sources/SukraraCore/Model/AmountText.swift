// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// A treatment amount as the person reads it (AmountText.kt): whole without decimals ("4"), else one decimal with the
/// language's decimal separator ("4.5", Spanish "4,5"), like `GlucoseUnit.format`. The card, its labels, its rings and
/// the treatment windows write an amount through here; the wire formats keep their point.
public enum AmountText {
    public static func of(_ a: Double, locale: Locale) -> String {
        if a == a.rounded() { return String(Int(a)) }
        let s = String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), a)
        return s.replacingOccurrences(of: ".", with: locale.decimalSeparator ?? ".")
    }

    /// Insulin units as the card writes them: "4.5u", "4,5u", "12u".
    public static func units(_ a: Double, locale: Locale) -> String { of(a, locale: locale) + "u" }
}
