// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// contracts/sensor/life.json: THE sensor-life numbers and the warm-up cases every app decides the same way.
public struct SensorTimingFixture {
    public struct Case {
        public var name: String
        public var start: TimeInterval
        public var warmupEnd: TimeInterval
        public var latest: TimeInterval?
        public var warming: Bool
    }

    public var warmupGrace: TimeInterval
    public var defaultWarmup: TimeInterval
    public var defaultWear: TimeInterval
    /// Times are absolute: the fixture's relative ones added to `now`.
    public var cases: [Case]

    public static func load(now: TimeInterval) throws -> SensorTimingFixture {
        let data = try Data(contentsOf: Contracts.url("sensor/life.json"))
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let grace = root["warmupGraceS"] as? Double, let warmup = root["defaultWarmupMin"] as? Double,
            let wear = root["defaultWearMin"] as? Double, let list = root["cases"] as? [[String: Any]]
        else { throw CocoaError(.fileReadCorruptFile) }
        let cases = try list.map { c -> Case in
            guard let name = c["name"] as? String, let start = c["start"] as? Double,
                let end = c["warmupEnd"] as? Double, let expect = c["expect"] as? [String: Any],
                let warming = expect["warming"] as? Bool
            else { throw CocoaError(.fileReadCorruptFile) }
            return Case(
                name: name, start: now + start, warmupEnd: now + end,
                latest: (c["latest"] as? Double).map { now + $0 }, warming: warming)
        }
        return SensorTimingFixture(warmupGrace: grace, defaultWarmup: warmup * 60, defaultWear: wear * 60, cases: cases)
    }
}
