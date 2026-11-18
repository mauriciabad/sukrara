// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The sensor's life from a Nightscout source (contracts/sources/nightscout/sensor-life.json; docs/44 "Person ×
/// Source": Nightscout's sensor life = its *Sensor Start*): the newest Sensor Start or Sensor Change on the site (not
/// deleted, not after now; at the same time the Sensor Start's) starts the sensor. The site doesn't say how long it
/// lasts, so `SensorTiming`'s defaults apply, and its warm-up rule. Android `NsSensorLife`.
public enum NightscoutSensorLife {
    /// One sensor as the site says it (epoch s). A new `id` = a new sensor.
    public struct Life: Codable, Equatable, Sendable {
        public var id: String
        public var start: Int
        public var warmupEnd: Int
        public var end: Int

        public init(id: String, start: Int, warmupEnd: Int, end: Int) {
            self.id = id
            self.start = start
            self.warmupEnd = warmupEnd
            self.end = end
        }

        /// As the card and the alert rules take a sensor.
        public var sensor: WearerSensor { WearerSensor(start: start, end: end, warmupEnd: warmupEnd) }
    }

    public enum State: String, Sendable {
        case warming, active, ended
    }

    static let events: Set<String> = ["Sensor Start", "Sensor Change"]

    /// The sensor `site`'s documents start at `now`, nil when none does (life unknown).
    public static func life(site: [[String: Any]], now: TimeInterval) -> Life? {
        var best: (t: Int64, start: Bool, id: String)?
        for doc in site where (doc["isValid"] as? Bool) != false {
            guard let event = doc["eventType"] as? String, events.contains(event),
                let id = (doc["identifier"] as? String) ?? (doc["_id"] as? String),
                let t = NightscoutTreatments.time(doc), TimeInterval(t) <= now
            else { continue }
            let isStart = event == "Sensor Start"
            if let b = best, (b.t, b.start ? 1 : 0) >= (t, isStart ? 1 : 0) { continue }
            best = (t, isStart, id)
        }
        guard let best else { return nil }
        let start = Int(best.t)
        return Life(
            id: best.id, start: start, warmupEnd: start + Int(SensorTiming.defaultWarmup),
            end: start + Int(SensorTiming.defaultWear))
    }

    /// Warming until a reading newer than the start (at most the grace past the warm-up), ended from `end` on.
    public static func state(_ life: Life, latest: TimeInterval?, now: TimeInterval) -> State {
        if now >= TimeInterval(life.end) { return .ended }
        let warming = SensorTiming.warming(
            start: TimeInterval(life.start), warmupEnd: TimeInterval(life.warmupEnd), latest: latest, now: now)
        return warming ? .warming : .active
    }
}
