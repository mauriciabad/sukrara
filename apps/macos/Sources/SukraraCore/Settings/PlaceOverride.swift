// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// One place's own value of a switch that has a default for every place (schema option `placeOverride`, docs/38):
/// `.default` follows the default, `.on` / `.off` are the place's own.
public enum PlaceOverride: String, CaseIterable, Sendable {
    case `default`, on, off

    /// The place's own value; nil = the default's.
    public var own: Bool? {
        switch self {
        case .default: nil
        case .on: true
        case .off: false
        }
    }

    public init(own: Bool?) {
        self = own.map { $0 ? .on : .off } ?? .default
    }

    /// A stored name; unknown = `.default`.
    public init(stored: String) {
        self = PlaceOverride(rawValue: stored) ?? .default
    }
}
