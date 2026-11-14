// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// THE trend (`TrendRate`) against contracts/trend: the same cases Android's TrendRateTest runs (rates within 1e-9).
@Suite struct TrendRateTests {
    /// A contracts/trend file's top-level object.
    static func file(_ name: String) -> [String: Any] {
        guard let data = try? Data(contentsOf: Contracts.url("trend/\(name)")),
            let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }
        return o
    }

    /// One case of a file's list (passable as a test argument).
    struct Case: @unchecked Sendable, CustomTestStringConvertible {
        let o: [String: Any]
        var testDescription: String { o["name"] as? String ?? "?" }
    }

    static func cases(_ name: String, _ key: String = "cases") -> [Case] {
        (file(name)[key] as? [[String: Any]] ?? []).map(Case.init)
    }

    static func num(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }

    @Test func theSharedCasesAreThere() {
        #expect(Self.cases("rate.json").count > 30)
        #expect(Self.cases("bands.json", "sequences").count > 5)
        #expect(Self.cases("clear.json").count > 40)
    }

    @Test func theConstantsAreTheContracts() throws {
        let c = try #require(Self.file("rate.json")["constants"] as? [String: Any])
        let tau = try #require(c["tauS"] as? [String: NSNumber])
        for s in TrendSetting.allCases where s != .sensor { #expect(tau[s.rawValue]?.doubleValue == s.tau) }
        #expect((c["settings"] as? [String]) == TrendSetting.allCases.map(\.rawValue))
        #expect(c["default"] as? String == TrendSetting(stored: nil).rawValue)
        #expect(Self.num(c["windowTaus"]) == TrendRate.windowTaus)
        #expect(Self.num(c["minReadings"]).map { Int($0) } == TrendRate.minReadings)
        #expect(Self.num(c["minSpanS"]) == TrendRate.minSpan)
        #expect(Self.num(c["maxStepS"]) == TrendRate.maxStep)
        #expect(Self.num(c["hysteresis"]) == TrendRate.hysteresis)
        #expect(Self.num(c["fullScale"]) == TrendRate.fullScale)
        #expect(Self.num(c["doubleHoldS"]) == TrendRate.doubleHold)
        #expect(Self.num(c["alertHoldS"]) == TrendRate.alertHold)
        #expect(SettingsSpec.options["trend"] == TrendSetting.allCases.map(\.rawValue))
    }

    /// rate.json: at every reading, the rate, where it came from, the band and what the fast alerts judge.
    @Test(arguments: cases("rate.json"))
    func rate(_ c: Case) throws {
        let setting = try #require(TrendSetting(rawValue: c.o["trend"] as? String ?? ""))
        let readings = try #require(c.o["readings"] as? [[Any]]).map { r -> Reading in
            let t = Date(timeIntervalSince1970: Self.num(r[0]) ?? 0)
            let mgdl =
                switch r[1] as? String {
                case "LO": GlucoseRange.lo
                case "HI": GlucoseRange.hi
                default: Int(Self.num(r[1]) ?? 0)
                }
            return Reading(t: t, mgdl: mgdl, sensorRate: Self.num(r[2]))
        }
        let got = TrendRate.annotate(readings, setting: setting)
        #expect(got.count == readings.count)
        #expect(zip(got, got.dropFirst()).allSatisfy { $0.t <= $1.t })
        let byTime = Dictionary(got.map { ($0.t.timeIntervalSince1970, $0.trend) }, uniquingKeysWith: { a, _ in a })
        let expect = try #require(c.o["expect"] as? [[Any]])
        #expect(byTime.count == expect.count)
        for e in expect {
            let t = Self.num(e[0]) ?? 0
            let tr = byTime[t] ?? nil
            let at = "\(c.testDescription) at \(Int(t))"
            if let want = Self.num(e[1]) {
                #expect(tr.map { abs($0.rate - want) < 1e-9 } == true, "\(at): rate \(String(describing: tr?.rate))")
            } else {
                #expect(tr == nil, "\(at): no trend")
            }
            #expect((tr?.from.rawValue ?? "none") == e[2] as? String, "\(at): from")
            #expect(tr?.band == Self.num(e[3]).map { Int($0) }, "\(at): band")
            if let want = Self.num(e[4]) {
                #expect(tr?.held.map { abs($0 - want) < 1e-9 } == true, "\(at): held \(String(describing: tr?.held))")
            } else {
                #expect(tr?.held == nil, "\(at): no held rate")
            }
        }
    }

    /// bands.json: a rate's plain band, the drawn angle, and the bands of a sequence (hysteresis, the doubled hold).
    @Test func plainBandsAndAngles() throws {
        let plain = try #require(Self.file("bands.json")["plain"] as? [[String: Any]])
        #expect(!plain.isEmpty)
        for p in plain {
            let r = try #require(Self.num(p["rate"]))
            #expect(TrendRate.plainBand(r) == Self.num(p["band"]).map { Int($0) }, "rate \(r)")
            #expect(Trend(rate: r).step == TrendRate.plainBand(r))
        }
        let angles = try #require(Self.file("bands.json")["angles"] as? [[String: Any]])
        #expect(!angles.isEmpty)
        for a in angles {
            let r = try #require(Self.num(a["rate"])), deg = try #require(Self.num(a["deg"]))
            #expect(abs(TrendRate.deg(r) - deg) < 1e-6, "rate \(r)")
        }
        let arrows = try #require(Self.file("bands.json")["arrows"] as? [[String: Any]])
        for a in arrows {
            let band = Self.num(a["band"]).map { Int($0) }
            #expect(
                Trend.words(band: band) == (a["words"] as? [String: String])?["en"], "band \(String(describing: band))")
        }
    }

    @Test(arguments: cases("bands.json", "sequences"))
    func sequence(_ c: Case) throws {
        let points = try #require(c.o["points"] as? [[Any]])
        let ts = points.map { Self.num($0[0]) ?? 0 }
        let rates = points.map { Self.num($0[1]) }
        let want = try #require(c.o["bands"] as? [Any]).map { Self.num($0).map { Int($0) } }
        #expect(TrendRate.bands(ts: ts, rates: rates) == want)
    }

    // MARK: the quiets' clear move (contracts/trend/clear.json, unchanged from #193)

    struct Sample: TrendSample {
        var at: Date
        var mgdl: Double
    }

    @Test(arguments: cases("clear.json"))
    func clear(_ c: Case) throws {
        let readings = try #require(c.o["readings"] as? [[NSNumber]]).map {
            Sample(at: Date(timeIntervalSince1970: $0[0].doubleValue), mgdl: $0[1].doubleValue)
        }
        let now = Date(timeIntervalSince1970: Self.num(c.o["now"]) ?? 0)
        let got = TrendRate.clear(readings, at: now, up: c.o["up"] as? Bool ?? true)
        if let want = Self.num(c.o["slope"]) {
            #expect(got.map { abs($0 - want) < 1e-9 } == true, "\(String(describing: got)) ≠ \(want)")
        } else {
            #expect(got == nil, "\(c.o["why"] as? String ?? "")")
        }
    }

    let t0 = Date(timeIntervalSince1970: 1_762_100_000)
    func run(_ mgdl: [Double], step: TimeInterval = 60) -> [Sample] {
        mgdl.enumerated().map { Sample(at: t0.addingTimeInterval(Double($0.offset) * step), mgdl: $0.element) }
    }

    @Test func keepHoldsTheWindowAscendingAndReplacesTheSameTime() {
        var kept: [Sample] = []
        for r in run([60, 61, 62, 63, 64, 65, 66]) { kept = TrendRate.keep(kept, r) }
        // only the readings within 5 min of the newest: the one exactly 5 min before is out
        #expect(kept == Array(run([60, 61, 62, 63, 64, 65, 66]).dropFirst(2)))
        // a late (backfilled) reading fills its place; one of the same time replaces it
        let late = t0.addingTimeInterval(240)
        kept = TrendRate.keep(kept.filter { $0.at != late }, Sample(at: late, mgdl: 64))
        #expect(kept.map(\.at) == (2...6).map { t0.addingTimeInterval(Double($0) * 60) })
        kept = TrendRate.keep(kept, Sample(at: late, mgdl: 70))
        #expect(kept.first { $0.at == late }?.mgdl == 70)
        // a reading far older than the window is dropped at once
        #expect(TrendRate.keep(kept, Sample(at: t0.addingTimeInterval(-3600), mgdl: 50)) == kept)
    }

    @Test func aWholeNumberSlopeIsExact() {
        #expect(TrendRate.slope(run([61, 62, 63, 64, 65])) == 1)
        #expect(TrendRate.slope(run([56, 56, 57, 57, 58])) == 0.5)
        #expect(TrendRate.slope(run([60])) == 0)
    }

    // MARK: the one annotate

    @Test func aReadingKeepsItsSensorRateAndGetsTheTrend() throws {
        let rs = (0...5).map { Reading(t: t0.addingTimeInterval(Double($0) * 60), mgdl: 100 + 2 * $0, sensorRate: 0.5) }
        let got = TrendRate.annotate(rs.reversed(), setting: .balanced)
        #expect(got.map(\.t) == rs.map(\.t))
        #expect(got.allSatisfy { $0.sensorRate == 0.5 })
        // the first 3 min: too few readings, the sensor's rate; then the readings' +2
        #expect(got[0].trend?.from == .sensor && got[0].rate == 0.5)
        let last = try #require(got.last?.trend)
        #expect(last.from == .readings && abs(last.rate - 2) < 1e-9)
        // from the sensor's → the band leaves ↗ for ↑ only past 2.2 (hysteresis): a steady 2 stays ↗
        #expect(last.band == 1)
        // From the sensor: its rate at once, the fast alerts judge it as it is
        let sensor = try #require(TrendRate.annotate(rs, setting: .sensor).last?.trend)
        #expect(sensor.rate == 0.5 && sensor.held == 0.5 && sensor.from == .sensor)
        // the trend is never stored: the encoded reading carries the sensor's rate only
        let json = String(decoding: try JSONEncoder().encode(got.last!), as: UTF8.self)
        #expect(json.contains("\"rate\":0.5") && !json.contains("trend"))
        #expect(try JSONDecoder().decode(Reading.self, from: Data(json.utf8)).trend == nil)
    }

    @Test func theSettingReadsAnyStoredValue() {
        #expect(TrendSetting(stored: "quick") == .quick)
        #expect(TrendSetting(stored: "fast") == .balanced)
        #expect(TrendSetting(stored: nil) == .balanced)
    }
}
