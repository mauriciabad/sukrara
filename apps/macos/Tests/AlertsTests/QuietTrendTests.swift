// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import Testing

/// The two trend quiets (SPEC §2 "Clear move in the readings", #193) in the engine; the rule's own cases are
/// contracts/trend/clear.json (CoreTests `TrendRateTests`).
@Suite struct QuietTrendTests {
    let t0 = Date(timeIntervalSince1970: 1_762_100_000)
    func run(_ mgdl: [Double], step: TimeInterval = 60) -> [GlucoseSample] {
        mgdl.enumerated().map { GlucoseSample(at: t0.addingTimeInterval(Double($0.offset) * step), mgdl: $0.element) }
    }

    /// The engine on readings `mgdl` (a minute apart) with `s`: the level episode's quiet after the last one.
    func quiet(_ mgdl: [Double], _ s: AlertSettings = AlertSettings(), wearersOwn: Bool = false) -> QuietReason? {
        var e = AlertEngine(context: AlertContext(settings: s, wearersOwn: wearersOwn))
        for r in run(mgdl) { _ = e.handle(.reading(r), at: r.at) }
        return e.uiState(at: run(mgdl).last!.at).active[.level]?.quiet
    }

    @Test func aLowRisingBackIntoRangeIsQuietWithTheMinutesItNeeds() {
        // 65 + 1 × 20 = 85: back by 70 in 5 min
        #expect(quiet([61, 62, 63, 64, 65]) == .risingBack(inMinutes: 5))
        // exactly at the low threshold is in range (low is strictly below it): 65 + 5 = 70 quiets, + 4 = 69 doesn't
        var s = AlertSettings()
        s.muteRisingLow.min = 5
        #expect(quiet([61, 62, 63, 64, 65], s) == .risingBack(inMinutes: 5))
        s.muteRisingLow.min = 4
        #expect(quiet([61, 62, 63, 64, 65], s) == nil)
        s.muteRisingLow = QuietRule(on: false, min: 20)
        #expect(quiet([61, 62, 63, 64, 65], s) == nil)
    }

    @Test func thisMacNeverHoldsAVeryLowAndTheWearersPhoneOnlyItsRepeats() {
        // the first very low of the episode alerts here (and on the wearer's phone: the episode's first alert)
        #expect(quiet([46, 47, 48, 49, 50]) == nil)
        #expect(quiet([46, 47, 48, 49, 50], wearersOwn: true) == .risingBack(inMinutes: 20))
    }

    @Test func aHighFallingBackIsQuietFromTheReadingsNotTheRate() {
        func falling(_ rate: Double?) -> [GlucoseSample] {
            run([200, 199, 198, 197, 196]).map { GlucoseSample(at: $0.at, mgdl: $0.mgdl, rate: rate) }
        }
        for rate in [nil, 2.0] {
            var e = AlertEngine(context: AlertContext())
            for r in falling(rate) { _ = e.handle(.reading(r), at: r.at) }
            #expect(e.uiState(at: falling(rate).last!.at).active[.level]?.quiet == .fallingBack(inMinutes: 16))
        }
        // flat at 200 with the sensor's rate saying −3: no fall, the high stays up
        var e = AlertEngine(context: AlertContext())
        for r in run([200, 200, 200, 200, 200]) {
            _ = e.handle(.reading(GlucoseSample(at: r.at, mgdl: r.mgdl, rate: -3)), at: r.at)
        }
        #expect(e.uiState(at: run([200]).last!.at.addingTimeInterval(240)).active[.level]?.quiet == nil)
    }

    @Test func theReadingsSurviveARelaunch() throws {
        var e = AlertEngine(context: AlertContext())
        for r in run([60, 61, 62, 63]) { _ = e.handle(.reading(r), at: r.at) }
        let saved = try #require(AlertEngine.Snapshot.decode(e.snapshot.encoded()))
        #expect(saved.recent == run([60, 61, 62, 63]))
        let (back, _) = AlertEngine.restore(saved, context: AlertContext(), at: t0.addingTimeInterval(200))
        #expect(back.recent == run([60, 61, 62, 63]))
    }
}
