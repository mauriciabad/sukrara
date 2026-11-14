// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// A place's own value of a switch the menu bar sets for every place (schema option `placeOverride`, docs/38):
/// `.same` follows the menu bar's, `.on` / `.off` are the place's own.
public enum PlaceOverride: String, CaseIterable, Sendable {
    case same, on, off

    /// The place's own value; nil = the menu bar's.
    public var own: Bool? {
        switch self {
        case .same: nil
        case .on: true
        case .off: false
        }
    }

    public init(own: Bool?) {
        self = own.map { $0 ? .on : .off } ?? .same
    }

    /// A stored name; unknown = `.same`.
    public init(stored: String) {
        self = PlaceOverride(rawValue: stored) ?? .same
    }
}
