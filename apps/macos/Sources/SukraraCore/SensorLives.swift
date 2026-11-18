// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// THE sensor life of a source that doesn't say all of it (contracts/sources/sensor-life.json, docs/44 decision 10;
/// Android `SensorLives`): the source's start or a manual one, with the source's wear length. The card's warm-up and
/// time left, *Sensor ended* (no data stays quiet) and the alert rules' sensor window take what it gives.
public enum SensorLives {
    /// What a source says of the sensor's life (sources.json `life`).
    public enum Life: String, Sendable, CaseIterable {
        /// Its start and length (another Sukrara relays its wearer's).
        case full
        /// The start, not the length (LibreLinkUp, Juggluco).
        case start
        /// A start only when one is logged (a Nightscout site's Sensor Start), never the length.
        case maybeStart
        /// Nothing (later: Dexcom Share, an official app's notification).
        case none

        /// A manual start may stand in for the start (your glucose only).
        public var takesManualStart: Bool { self == .maybeStart || self == .none }
    }

    /// The sensor a source said: its id (a new id = a new sensor) and start (epoch s).
    public struct Said: Equatable, Sendable {
        public var id: String
        public var start: Int
        public init(id: String, start: Int) {
            self.id = id
            self.start = start
        }
    }

    /// Where the start came from.
    public enum From: String, Sendable { case source, manual }

    /// The sensor's life (epoch s). `id` = the source's, or `manual-<start>` (a new manual start is a new sensor).
    public struct Sensor: Equatable, Sendable {
        public var id: String
        public var start: Int
        public var warmupEnd: Int
        public var end: Int
        public var from: From

        /// As the card and the alert rules take a sensor.
        public var wearer: WearerSensor { WearerSensor(start: start, end: end, warmupEnd: warmupEnd) }
    }

    /// The life by the contract's rule at `now`: the source's start (not ahead of now) or the manual one (`life`
    /// `maybeStart` / `none`, your glucose, not ahead of now), the newer, the source's on a tie; the wear length
    /// `wearMin` (minutes; 0 or less = the default). Nil = unknown.
    public static func life(
        _ life: Life, person: Person, said: Said?, wearMin: Double, manualStart: Int?, now: TimeInterval
    ) -> Sensor? {
        let source = said.flatMap { TimeInterval($0.start) <= now ? $0 : nil }
        let manual = manualStart.flatMap { m in
            life.takesManualStart && person == .yours && m > 0 && TimeInterval(m) <= now ? m : nil
        }
        let wear = wearMin > 0 ? Int(wearMin * 60) : Int(SensorTiming.defaultWear)
        func sensor(_ id: String, _ start: Int, _ from: From) -> Sensor {
            Sensor(
                id: id, start: start, warmupEnd: start + Int(SensorTiming.defaultWarmup), end: start + wear, from: from)
        }
        if let source, manual.map({ source.start >= $0 }) ?? true { return sensor(source.id, source.start, .source) }
        if let manual { return sensor("manual-\(manual)", manual, .manual) }
        return nil
    }

    /// The state at `now`, the newest reading at `latest`: ended from the end on, warming by `SensorTiming`, else
    /// active.
    public enum State: String, Sendable { case warming, active, ended }

    public static func state(_ s: Sensor, latest: TimeInterval?, now: TimeInterval) -> State {
        if now >= TimeInterval(s.end) { return .ended }
        let warming = SensorTiming.warming(
            start: TimeInterval(s.start), warmupEnd: TimeInterval(s.warmupEnd), latest: latest, now: now)
        return warming ? .warming : .active
    }

    /// The earliest manual start that still leaves the sensor running at `now` with `wearMin` (a start before it
    /// would be already ended).
    public static func earliestStart(wearMin: Double, now: TimeInterval) -> TimeInterval {
        let wear = wearMin > 0 ? wearMin * 60 : SensorTiming.defaultWear
        return now - wear + 60
    }
}

/// What the sensor-life rule takes from the settings (schema `source.person`, the sources' wear lengths,
/// `sensor.manualStart`): read together so one change re-applies the sensor once.
public struct SensorLifeSettings: Equatable, Sendable {
    public var person: Person
    /// Minutes, by source (`SourceKind.wear`'s setting, coerced by its format).
    public var wearMin: [SourceKind: Double]
    /// Epoch s, 0 = none.
    public var manualStart: Int

    public init(person: Person = .yours, wearMin: [SourceKind: Double] = [:], manualStart: Int = 0) {
        self.person = person
        self.wearMin = wearMin
        self.manualStart = manualStart
    }

    /// The Mac's: whatever it reads takes the manual start and the wear lengths (it rings no sensor reminders, and its
    /// person answer is going away), so the person is always yours here.
    public init(store: some SettingsStoring) {
        let wear = SourceKind.allCases.compactMap { k in k.wear.map { (k, store.number($0)) } }
        self.init(
            person: .yours, wearMin: Dictionary(uniqueKeysWithValues: wear),
            manualStart: Int(store.value(MacSettings.manualStart)))
    }

    /// `kind`'s sensor by `SensorLives.life`; nil for a source that says all of it.
    public func life(_ kind: SourceKind, said: SensorLives.Said?, now: TimeInterval) -> SensorLives.Sensor? {
        guard kind.life != .full else { return nil }
        return SensorLives.life(
            kind.life, person: person, said: said, wearMin: wearMin[kind] ?? 0,
            manualStart: manualStart > 0 ? manualStart : nil, now: now)
    }
}
