// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The trend of a rate of change (mg/dL per minute): one of Nightscout's direction bands (±1 / 2 / 3; a boundary
/// belongs to the steeper band), the same as Android's `TrendAngle.step` (contracts/graph/trend). One arrow and one
/// word per band: ↗ rising, ↑ rising fast, ⇈ rising very fast (falling likewise).
public enum Trend: String, Sendable, CaseIterable {
    case risingVeryFast, risingFast, rising, steady, falling, fallingFast, fallingVeryFast

    /// mg/dL per minute from which the arrow doubles (⇈ / ⇊).
    public static let fullScale = 3.0

    public init(rate: Double) {
        switch rate {
        case Self.fullScale...: self = .risingVeryFast
        case 2..<Self.fullScale: self = .risingFast
        case 1..<2: self = .rising
        case ...(-Self.fullScale): self = .fallingVeryFast
        case ...(-2): self = .fallingFast
        case ...(-1): self = .falling
        default: self = .steady
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

    /// The words for an optional rate: "trend unknown" without one.
    public static func words(rate: Double?) -> String { rate.map { Trend(rate: $0).words } ?? "trend unknown" }
}
