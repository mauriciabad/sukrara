// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// What the app hands the widget through the App Group (`SukraraPaths.appGroupSnapshot()`): the card's whole input,
/// so the widget draws exactly what the app's card shows. Versioned: a snapshot of another version reads as none
/// (the widget then shows its "open Sukrara" state, never a guessed value), and the app rewrites it on its next
/// reading. It lives with the renderer (not SukraraCore) because it carries the card's types.
public struct WidgetSnapshot: Sendable, Codable, Equatable {
    /// The format this build writes and reads (2: with the no-data reason).
    public static let version = 2

    public var v: Int
    /// When the app wrote it (epoch s): the widget's "updated" time, never the reading's.
    public var writtenAt: TimeInterval
    public var data: CardData
    public var config: GraphConfig
    public var unit: GlucoseUnit
    /// Why the app has no value to show (`AppModel.noDataReason`): the widget says it instead of a bare "No readings
    /// yet" (#72). Nil while a value can be shown.
    public var reason: NoDataReason?

    public init(
        writtenAt: TimeInterval, data: CardData, config: GraphConfig, unit: GlucoseUnit, reason: NoDataReason? = nil
    ) {
        self.v = Self.version
        self.writtenAt = writtenAt
        self.data = data
        self.config = config
        self.unit = unit
        self.reason = reason
    }

    public func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(self)
    }

    /// The snapshot in `data`, or nil when it is unreadable or of another version.
    public static func decode(_ data: Data) -> WidgetSnapshot? {
        guard let s = try? JSONDecoder().decode(WidgetSnapshot.self, from: data), s.v == version else { return nil }
        return s
    }
}
