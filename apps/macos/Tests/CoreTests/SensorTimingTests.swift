// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `SensorTiming` against contracts/sensor/life.json (Android's SensorTiming passes the same file).
@Suite struct SensorTimingTests {
    @Test func numbersAreTheContracts() throws {
        let f = try SensorTimingFixture.load(now: 0)
        #expect(SensorTiming.warmupGrace == f.warmupGrace)
        #expect(SensorTiming.defaultWarmup == f.defaultWarmup)
        #expect(SensorTiming.defaultWear == f.defaultWear)
    }

    @Test func warmUpCases() throws {
        let now: TimeInterval = 1_800_000_000
        let f = try SensorTimingFixture.load(now: now)
        #expect(!f.cases.isEmpty)
        for c in f.cases {
            #expect(
                SensorTiming.warming(start: c.start, warmupEnd: c.warmupEnd, latest: c.latest, now: now) == c.warming,
                "\(c.name)")
        }
    }
}
