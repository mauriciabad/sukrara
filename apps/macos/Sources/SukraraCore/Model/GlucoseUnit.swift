// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// THE glucose unit of every number a person reads or types (setting `general.unit`; contracts/settings/units.json):
/// a setting, never derived from the region. Values are stored, sent, alerted on and exported in mg/dL; mmol/L is only
/// how they are shown and typed. Every surface writes a glucose value or rate through `format` / `rateText`, and every
/// input turns a typed value back with `stored`: one conversion and one format (Android's `GlucoseUnit` is the same).
public enum GlucoseUnit: String, Sendable, CaseIterable, Codable {
    case mgdl
    case mmol

    /// mg/dL per mmol/L: glucose's molar mass / 10 (units.json `factor`).
    public static let mgdlPerMmol = 18.0182

    /// "mg/dL" / "mmol/L".
    public var symbol: String { self == .mgdl ? "mg/dL" : "mmol/L" }

    /// "mg/dL/min" / "mmol/L/min".
    public var rateSymbol: String { self == .mgdl ? "mg/dL/min" : "mmol/L/min" }

    /// Decimals of a value / of a rate.
    public var decimals: Int { self == .mgdl ? 0 : 1 }
    public var rateDecimals: Int { self == .mgdl ? 1 : 2 }

    /// What a screen reader says for the unit.
    public var spoken: String { self == .mgdl ? "milligrams per decilitre" : "millimoles per litre" }

    /// mg/dL (or mg/dL/min) as a number of this unit, rounded to `decimals` half away from zero.
    public func shown(_ mgdl: Double, decimals: Int? = nil) -> Double {
        Self.round(self == .mmol ? mgdl / Self.mgdlPerMmol : mgdl, decimals ?? self.decimals)
    }

    /// A number of this unit (typed or picked) → mg/dL on `step` (a threshold typed in mmol/L lands on whole mg/dL).
    public func stored(_ value: Double, step: Double = 1) -> Double {
        let mgdl = self == .mmol ? value * Self.mgdlPerMmol : value
        return (mgdl / step).rounded(.toNearestOrAwayFromZero) * step
    }

    /// A value as written, without the unit: "112", "6.2" / "6,2" (the language's decimal separator).
    public func format(_ mgdl: Int, locale: Locale = .current) -> String {
        Self.number(shown(Double(mgdl)), decimals, locale)
    }

    /// A trend rate (mg/dL/min) as written in this unit per minute, without the unit: "2.0" / "0.11".
    public func rateText(_ mgdlPerMin: Double, locale: Locale = .current) -> String {
        Self.number(shown(mgdlPerMin, decimals: rateDecimals), rateDecimals, locale)
    }

    private static func round(_ x: Double, _ decimals: Int) -> Double {
        let p = pow(10, Double(decimals))
        let r = (abs(x) * p + 0.5).rounded(.down) / p
        return x < 0 && r != 0 ? -r : r
    }

    private static func number(_ v: Double, _ decimals: Int, _ locale: Locale) -> String {
        let s = String(format: "%.\(decimals)f", locale: Locale(identifier: "en_US_POSIX"), v)
        return decimals == 0 ? s : s.replacingOccurrences(of: ".", with: locale.decimalSeparator ?? ".")
    }
}
