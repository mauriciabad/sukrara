// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraTesting
import Testing

/// `Trend` and the two trend quiets (SPEC §2 "Trend from the readings", #193).
@Suite struct TrendTests {
    struct Case: Decodable, CustomTestStringConvertible {
        let name: String
        let readings: [[Double]]
        let now: Double
        let up: Bool
        let slope: Double?
        let why: String
        var testDescription: String { name }
    }

    static func cases() -> [Case] {
        struct File: Decodable { let cases: [Case] }
        guard let data = try? Data(contentsOf: Contracts.url("alerts/trend.json")),
            let file = try? JSONDecoder().decode(File.self, from: data)
        else { return [] }
        return file.cases
    }

    @Test func theSharedCasesAreThere() { #expect(Self.cases().count > 40) }

    /// contracts/alerts/trend.json: the same cases Android's TrendTest runs.
    @Test(arguments: cases())
    func sharedCase(_ c: Case) {
        let readings = c.readings.map { GlucoseSample(at: Date(timeIntervalSince1970: $0[0]), mgdl: $0[1]) }
        let got = Trend.clear(readings, at: Date(timeIntervalSince1970: c.now), up: c.up)
        if let want = c.slope {
            #expect(got.map { abs($0 - want) < 1e-9 } == true, "\(c.name): \(String(describing: got)) ≠ \(want)")
        } else {
            #expect(got == nil, "\(c.name) (\(c.why))")
        }
    }

    let t0 = Date(timeIntervalSince1970: 1_762_100_000)
    func run(_ mgdl: [Double], step: TimeInterval = 60) -> [GlucoseSample] {
        mgdl.enumerated().map { GlucoseSample(at: t0.addingTimeInterval(Double($0.offset) * step), mgdl: $0.element) }
    }

    @Test func keepHoldsTheWindowAscendingAndReplacesTheSameTime() {
        var kept: [GlucoseSample] = []
        for r in run([60, 61, 62, 63, 64, 65, 66]) { kept = Trend.keep(kept, r) }
        // only the readings within 5 min of the newest: the one exactly 5 min before is out
        #expect(kept == Array(run([60, 61, 62, 63, 64, 65, 66]).dropFirst(2)))
        // a late (backfilled) reading fills its place; one of the same time replaces it
        let late = t0.addingTimeInterval(240)
        kept = Trend.keep(kept.filter { $0.at != late }, GlucoseSample(at: late, mgdl: 64))
        #expect(kept.map(\.at) == (2...6).map { t0.addingTimeInterval(Double($0) * 60) })
        kept = Trend.keep(kept, GlucoseSample(at: late, mgdl: 70))
        #expect(kept.first { $0.at == late }?.mgdl == 70)
        // a reading far older than the window is dropped at once
        #expect(Trend.keep(kept, GlucoseSample(at: t0.addingTimeInterval(-3600), mgdl: 50)) == kept)
    }

    @Test func aWholeNumberSlopeIsExact() {
        #expect(Trend.slope(run([61, 62, 63, 64, 65])) == 1)
        #expect(Trend.slope(run([56, 56, 57, 57, 58])) == 0.5)
        #expect(Trend.slope(run([60])) == 0)
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
