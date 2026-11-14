// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// A trend's text band (contracts/trend `bands.json`): one of Nightscout's direction bands (±1 / 2 / 3), as
/// `TrendRate` decides it for a reading (hysteresis, the doubled arrow's hold) or, `init(rate:)`, for a rate with
/// nothing before it. One arrow and one word per band: ↗ rising, ↑ rising fast, ⇈ rising very fast (falling likewise).
public enum Trend: String, Sendable, CaseIterable {
    case risingVeryFast, risingFast, rising, steady, falling, fallingFast, fallingVeryFast

    /// mg/dL per minute from which the arrow doubles (⇈ / ⇊).
    public static let fullScale = 3.0

    /// The plain band of a rate with nothing before it (`TrendRate.plainBand`).
    public init(rate: Double) { self.init(band: TrendRate.plainBand(rate)) }

    /// A band -3 … 3 (`ReadingTrend.band`; clamped).
    public init(band: Int) {
        switch band {
        case 3...: self = .risingVeryFast
        case 2: self = .risingFast
        case 1: self = .rising
        case 0: self = .steady
        case -1: self = .falling
        case -2: self = .fallingFast
        default: self = .fallingVeryFast
        }
    }

    /// The band as a number: 3 (⇈) … 0 (→) … -3 (⇊).
    public var step: Int {
        switch self {
        case .risingVeryFast: 3
        case .risingFast: 2
        case .rising: 1
        case .steady: 0
        case .falling: -1
        case .fallingFast: -2
        case .fallingVeryFast: -3
        }
    }

    /// What a screen reader says: "rising fast", "steady".
    public var words: String {
        switch self {
        case .risingVeryFast: "rising very fast"
        case .risingFast: "rising fast"
        case .rising: "rising"
        case .steady: "steady"
        case .falling: "falling"
        case .fallingFast: "falling fast"
        case .fallingVeryFast: "falling very fast"
        }
    }

    /// The words for an optional band: "trend unknown" without one.
    public static func words(band: Int?) -> String { band.map { Trend(band: $0).words } ?? "trend unknown" }
}
