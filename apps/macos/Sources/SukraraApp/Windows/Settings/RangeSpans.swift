// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraAlerts
import SukraraCore
import SukraraGraph

/// The whole mg/dL values a limit makes count, as a range or alert row says them under its number ("Low: 56–69 mg/dL",
/// "In range: 70–179 mg/dL"; Android's RangeSpans is the same). The low limit is the first value in range (low is
/// strictly below it), every other limit the first value of the range it names (very low at or below it, the highs at
/// or above it; SPEC §1, contracts/graph/range/bands.json), so the number alone decides: nothing to choose.
enum RangeSpans {
    enum Name: CaseIterable { case veryLow, low, tight, inRange, high, veryHigh, extremelyHigh }

    /// One range: its name and its lowest and highest values (nil = open-ended).
    struct Span: Equatable {
        let name: Name
        let lo: Int?
        let hi: Int?
    }

    /// The ranges the Ranges limit `index` sets (very low, low, very good, high, very high, extremely high): its own
    /// range, and in range for the low and high limits that bound it.
    static func ofBand(_ b: Bands, _ index: Int) -> [Span] {
        let inRange = Span(name: .inRange, lo: b.low, hi: b.high - 1)
        switch index {
        case 0: return [Span(name: .veryLow, lo: nil, hi: b.veryLow)]
        case 1: return [Span(name: .low, lo: b.veryLow + 1, hi: b.low - 1), inRange]
        case 2: return [Span(name: .tight, lo: b.low, hi: min(b.tight, b.high - 1))]
        case 3: return [Span(name: .high, lo: b.high, hi: b.veryHigh - 1), inRange]
        case 4: return [Span(name: .veryHigh, lo: b.veryHigh, hi: b.extremelyHigh - 1)]
        default: return [Span(name: .extremelyHigh, lo: b.extremelyHigh, hi: nil)]
        }
    }

    /// `b` with its limit `index` set to `v`: what the row being changed would make.
    static func replacing(_ b: Bands, _ index: Int, _ v: Double) -> Bands {
        var b = b
        let x = Int(v.rounded())
        switch index {
        case 0: b.veryLow = x
        case 1: b.low = x
        case 2: b.tight = x
        case 3: b.high = x
        case 4: b.veryHigh = x
        default: b.extremelyHigh = x
        }
        return b
    }

    /// The values `kind` alerts at under `rule` (the kinds turned on bound each other; `kind` counts as on, it is the
    /// one being set) and, for low and high, the values no level alert matches. Empty for a kind without a glucose range.
    static func ofAlert(_ kind: AlertKind, _ rule: (AlertKind) -> AlertRule) -> [Span] {
        func on(_ k: AlertKind) -> Bool { k == kind || rule(k).on }
        func th(_ k: AlertKind) -> Int { Int(rule(k).threshold) }
        let lows = [AlertKind.veryLow, .low].filter(on)
        let highs = [AlertKind.high, .veryHigh, .extremelyHigh].filter(on)
        // the first value no low matches: low's own limit (70), one above very low's (56) when low is off
        func inFrom(_ k: AlertKind) -> Int { k == .low ? th(k) : th(k) + 1 }
        let inRange = Span(name: .inRange, lo: lows.map(inFrom).max(), hi: highs.map(th).min().map { $0 - 1 })
        // the next kind on the way out bounds this one: low above very low, high under very high …
        func below(_ ks: [AlertKind]) -> Int? { ks.filter(on).map(th).min().map { $0 - 1 } }
        switch kind {
        case .veryLow: return [Span(name: .veryLow, lo: nil, hi: th(kind))]
        case .low: return [Span(name: .low, lo: on(.veryLow) ? th(.veryLow) + 1 : nil, hi: th(kind) - 1), inRange]
        case .high: return [Span(name: .high, lo: th(kind), hi: below([.veryHigh, .extremelyHigh])), inRange]
        case .veryHigh: return [Span(name: .veryHigh, lo: th(kind), hi: below([.extremelyHigh]))]
        case .extremelyHigh: return [Span(name: .extremelyHigh, lo: th(kind), hi: nil)]
        case .fallingFast, .risingFast, .noData: return []
        }
    }

    /// One line per non-empty span, in `unit`: "Low: 56–69 mg/dL", "Very low: up to 55 mg/dL", "Extremely high: from
    /// 300 mg/dL"; nil when there's nothing to say.
    static func text(_ spans: [Span], unit: GlucoseUnit) -> String? {
        let lines = spans.compactMap { s -> String? in
            let name = name(s.name)
            let symbol = unit.symbol
            switch (s.lo, s.hi) {
            case (nil, nil): return nil
            case (nil, let hi?): return L10n.t("\(name): up to \(unit.format(hi)) \(symbol)")
            case (let lo?, nil): return L10n.t("\(name): from \(unit.format(lo)) \(symbol)")
            case (let lo?, let hi?) where lo > hi: return nil
            case (let lo?, let hi?) where lo == hi: return L10n.t("\(name): \(unit.format(lo)) \(symbol)")
            case (let lo?, let hi?): return L10n.t("\(name): \(unit.format(lo))–\(unit.format(hi)) \(symbol)")
            }
        }
        return lines.isEmpty ? nil : lines.joined(separator: "\n")
    }

    static func name(_ n: Name) -> String {
        switch n {
        case .veryLow: L10n.t("Very low")
        case .low: L10n.t("Low")
        case .tight: L10n.t("Very good")
        case .inRange: L10n.t("In range")
        case .high: L10n.t("High")
        case .veryHigh: L10n.t("Very high")
        case .extremelyHigh: L10n.t("Extremely high")
        }
    }
}
