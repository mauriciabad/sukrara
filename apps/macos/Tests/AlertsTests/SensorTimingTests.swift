// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraTesting
import Testing

/// The alert engine's `SensorWindow` decides the warm-up with THE rule (contracts/sensor/life.json), as the card does.
@Suite struct AlertSensorTimingTests {
    @Test func windowWarmsUpLikeTheCard() throws {
        let now: TimeInterval = 1_800_000_000
        let f = try SensorTimingFixture.load(now: now)
        #expect(SensorWindow.warmupGrace == SensorTiming.warmupGrace)
        for c in f.cases {
            let w = SensorWindow(
                start: Date(timeIntervalSince1970: c.start),
                end: Date(timeIntervalSince1970: c.start + SensorTiming.defaultWear),
                warmupEnd: Date(timeIntervalSince1970: c.warmupEnd))
            let warming = w.isWarmingUp(
                at: Date(timeIntervalSince1970: now), latestReading: c.latest.map { Date(timeIntervalSince1970: $0) })
            #expect(warming == c.warming, "\(c.name)")
        }
    }
}
