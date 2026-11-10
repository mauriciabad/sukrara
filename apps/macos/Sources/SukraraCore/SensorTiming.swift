// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// THE sensor-life numbers on the Mac: the card's badge and warm-up (SukraraGraph `SensorLife`) and the alert engine's
/// quiet `no_data` (SukraraAlerts `SensorWindow`) decide with these, so the badge and the alerts never disagree about a
/// warm-up. Android's `:core:model` SensorTiming has the same numbers; both apps' tests check them against
/// contracts/sensor/life.json.
public enum SensorTiming {
    /// The warm-up state lasts until the first reading, at most this long past the warm-up's end.
    public static let warmupGrace: TimeInterval = 15 * 60

    /// The warm-up when the sensor doesn't say.
    public static let defaultWarmup: TimeInterval = 60 * 60

    /// The wear length when the sensor doesn't say: 15 days.
    public static let defaultWear: TimeInterval = 15 * 86400

    /// Warming up at `now` (unix s): the sensor started (`start`), no reading since its start (`latest` = the newest
    /// reading's time, nil = none) and not more than `warmupGrace` past the warm-up's end (`warmupEnd`).
    public static func warming(start: TimeInterval, warmupEnd: TimeInterval, latest: TimeInterval?, now: TimeInterval)
        -> Bool
    {
        now >= start && now < warmupEnd + warmupGrace && (latest.map { $0 < start } ?? true)
    }
}
