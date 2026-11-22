// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// The newest pen read on this device: its pen's insulin (`kind`, rapid or long-acting), read at `at` (epoch s). A Mac
/// reads no pens, so it never has one; the type is here for the contract's cases.
public struct PenReadAt: Equatable, Sendable {
    public var kind: Treatment.Kind
    public var at: Int64

    public init(kind: Treatment.Kind, at: Int64) {
        self.kind = kind
        self.at = at
    }
}

/// What THE order of the treatment kinds is decided from, gathered in one place (`AppModel.kindFacts`): `reading` = the
/// newest reading in mg/dL while fresh (the alerts' 15 min; nil = none, or older), `lowBelow` = the low alert's
/// threshold in force now (whether or not the alert is on), `ringing` = the alert kinds ringing (raw values), `pen` =
/// the newest pen read (nil on a Mac).
public struct KindFacts: Equatable, Sendable {
    public var reading: Int?
    public var lowBelow: Double
    public var ringing: Set<String>
    public var pen: PenReadAt?

    public init(reading: Int?, lowBelow: Double, ringing: Set<String>, pen: PenReadAt? = nil) {
        self.reading = reading
        self.lowBelow = lowBelow
        self.ringing = ringing
        self.pen = pen
    }

    /// Nothing known: the usual order.
    public static let none = KindFacts(reading: nil, lowBelow: 70, ringing: [])
}

/// THE order of the treatment kinds wherever Sukrara offers to log one (contracts/treatments/kind-order.json; Android's
/// `KindOrder` runs the same cases): the focused kind first, then the others in `usual`. Add Treatment focuses the first
/// (its fields stay in `usual`). Carbs while low (the reading under the low alert's threshold, or an alert carbs answer
/// rings); else long-acting for `penS` after a rapid pen was read (the other insulin); else rapid.
public enum KindOrder {
    /// The dialog's fields, left to right.
    public static let usual: [Treatment.Kind] = [.rapid, .long, .carbs]

    /// The alerts carbs answer (contracts/alerts SPEC: very low, low, falling fast).
    public static let carbsAlerts: Set<String> = ["very_low", "low", "falling_fast"]

    /// How long a rapid pen's read puts long-acting first: as long as the phone's read notification lasts.
    public static let penS: Int64 = 2 * 3600

    /// Glucose is low now: carbs is what's needed.
    public static func low(_ f: KindFacts) -> Bool {
        if let r = f.reading, Double(r) < f.lowBelow { return true }
        return !f.ringing.isDisjoint(with: carbsAlerts)
    }

    /// The kind focused at `now` (epoch s): the order's first.
    public static func focus(_ f: KindFacts, now: Int64) -> Treatment.Kind {
        if low(f) { return .carbs }
        if let p = f.pen, p.kind == .rapid, (0..<penS).contains(now - p.at) { return .long }
        return .rapid
    }

    /// The kinds in order at `now` (epoch s): `focus`, then the rest of `usual`.
    public static func of(_ f: KindFacts, now: Int64) -> [Treatment.Kind] {
        let first = focus(f, now: now)
        return [first] + usual.filter { $0 != first }
    }
}
