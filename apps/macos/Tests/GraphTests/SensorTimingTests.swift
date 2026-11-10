// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraGraph

/// The card's badge decides the warm-up with THE rule (contracts/sensor/life.json), as the alerts do.
@Suite struct GraphSensorTimingTests {
    @Test func badgeWarmsUpLikeTheAlerts() throws {
        let now: TimeInterval = 1_800_000_000
        let f = try SensorTimingFixture.load(now: now)
        for c in f.cases {
            let info = SensorInfo(start: c.start, end: c.start + SensorTiming.defaultWear, warmupEnd: c.warmupEnd)
            #expect(SensorLife.warming(info, latest: c.latest, now: now) == c.warming, "\(c.name)")
        }
    }
}
