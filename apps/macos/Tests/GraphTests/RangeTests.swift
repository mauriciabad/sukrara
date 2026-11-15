// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import Testing

@testable import SukraraGraph

private let now = GraphFixtures.now

/// contracts/graph/range: the range word under an out-of-range value (#26; Android GraphFixtureTest.range).
@Suite struct RangeFixtureTests {
    @Test(arguments: GraphFixtures.cases("range/range.json"))
    func word(_ c: JSON) {
        let sgv = Int(c.number("sgv") ?? 0)
        var bands = Bands()
        let b = c.child("bands")
        if let v = b.number("low") { bands.low = Int(v) }
        if let v = b.number("high") { bands.high = Int(v) }
        let p = c.child("pill")
        let pill = p.string("kind").map {
            StatusBadge.Look(kind: $0, text: "x", short: "x", tonal: p.bool("tonal") ?? false)
        }
        let face = Freshness.look(sgv: sgv, t: now - (c.number("age") ?? 60), rate: 0, now: now)
        let got = RangeState.shown(face, sgv: sgv, bands: bands, pill: pill)
        #expect(got?.rawValue == c.expect.string("range"))
        if let got { #expect(CardWords.en.rangeWord(got) == c.expect.string("text")) }
    }
}

/// contracts/graph/range/bands.json: the ranges at their limits, a limit in the range beyond it (#181; Android
/// GraphFixtureTest.bands): the band, the value's colour, the report's spans and time in range.
@Suite struct BandsFixtureTests {
    private static func bands(_ c: JSON) -> Bands {
        let o = c.child("bands")
        let d = Bands()
        func v(_ k: String, _ def: Int) -> Int { o.number(k).map { Int($0) } ?? def }
        return Bands(
            veryLow: v("veryLow", d.veryLow), low: v("low", d.low), tight: v("tight", d.tight), high: v("high", d.high),
            veryHigh: v("veryHigh", d.veryHigh), extremelyHigh: v("extremelyHigh", d.extremelyHigh))
    }

    private static let file = GraphFixtures.object("range/bands.json")

    private static func list(_ key: String) -> [JSON] {
        (file[key] as? [[String: Any]] ?? []).map(JSON.init)
    }

    @Test(arguments: GraphFixtures.cases("range/bands.json"))
    func band(_ c: JSON) {
        let b = Self.bands(c)
        let sgv = Double(c.number("sgv") ?? 0)
        let pal = GraphPalette.of(dark: false)
        let colours: [String: CGColor] = [
            "veryLow": pal.veryLow, "low": pal.low, "tight": pal.tight, "target": pal.target, "high": pal.high,
            "veryHigh": pal.veryHigh, "extremelyHigh": pal.extremelyHigh,
        ]
        #expect(b.of(sgv).name == c.expect.string("band"))
        #expect(pal.at(b, sgv) == colours[c.expect.string("colour") ?? ""])
    }

    @Test func spans() {
        let cs = Self.list("spans")
        #expect(!cs.isEmpty)
        for c in cs {
            let b = Self.bands(c)
            for band in Band.allCases {
                let want = c.expect[band.name] as? [Any] ?? []
                let got = b.span(band)
                #expect(
                    [got.lo, got.hi] == want.map { ($0 as? NSNumber)?.intValue },
                    "\(c.testDescription): \(band.name)")
            }
        }
    }

    @Test func timeInRange() {
        let cs = Self.list("tir")
        #expect(!cs.isEmpty)
        let now = GraphFixtures.now
        for c in cs {
            // one reading every 5 minutes, the last 5 minutes before now: each counts the same
            let sgvs = (c["sgv"] as? [NSNumber] ?? []).map(\.doubleValue)
            let rs = sgvs.enumerated().map { i, v in GraphReading(t: now - Double(sgvs.count - i) * 300, v: v) }
            let tir = GlucoseStats.timeInRange(rs, from: now - Double(sgvs.count) * 300, to: now, bands: Bands())
            for band in Band.allCases {
                let want = c.expect.number(band.name) ?? -1
                #expect(abs(tir.share(band) - want) < 1e-9, "\(c.testDescription): \(band.name)")
            }
        }
    }
}

/// The menu bar's colour side at the limits: the value as written, a limit in the range beyond it (#181).
@Test func menuBarSideAtTheLimits() {
    func side(_ v: Double) -> RangeSide? {
        GlucoseCard.rangeSide(CardData(readings: [GraphReading(t: now - 60, v: v)], now: now), config: GraphConfig())
    }
    #expect(side(180) == .above)
    #expect(side(179.6) == .above)  // written 180
    #expect(side(179) == .inRange)
    #expect(side(70) == .inRange)
    #expect(side(69) == .below)
    #expect(side(55) == .below)
}

/// An out-of-range value says its range in words on the card, never by colour alone (Android RangeWordTest).
@Suite struct RangeWordTests {
    private func readings(_ v: Double, age: TimeInterval = 60) -> [GraphReading] {
        (1...24).reversed().map { GraphReading(t: now - age - Double($0) * 300, v: v, rate: 0) }
            + [GraphReading(t: now - age, v: v, rate: -0.4)]
    }

    private func draw(
        _ rs: [GraphReading], _ status: AlertStatus = .none, size: CGSize = CGSize(width: 332, height: 138),
        rangeSaid: Bool = false
    ) -> CardLayout {
        var st = CardSurface.widgetValue.style(GraphConfig(), sensor: nil)
        st.rangeSaid = rangeSaid
        return GlucoseCard.draw(
            cardContext(size), size: size, readings: rs, marks: [], now: now, st, GraphPalette.of(dark: false),
            surface: .test, part: .all, alerts: CardAlerts(status: status))
    }

    /// The word without its placement: under the value, or beside it where the number keeps its size that way
    /// (contracts/graph "The value with a pill").
    private func word(_ lay: CardLayout) -> String? {
        lay.first("value.range").map { String($0.info.split(separator: " [").first ?? "") }
    }

    @Test func aLowWithoutAPillSaysLowWithTheValue() {
        let lay = draw(readings(64))
        #expect(word(lay) == "low")
        #expect(lay.first("value.status") == nil)
    }

    @Test func eatCarbsSaysTheSideAlreadyAMutePillDoesnt() {
        #expect(draw(readings(64), .needed(.low)).first("value.range") == nil)
        #expect(word(draw(readings(64), .paused(.low, until: now + 600))) == "low")
    }

    @Test func inRangeAndStruckValuesSayNothing() {
        #expect(draw(readings(125)).first("value.range") == nil)
        #expect(draw(readings(64, age: 360)).first("value.range") == nil)
    }

    @Test func veryHighIsSaidApartFromLow() {
        #expect(word(draw(readings(262))) == "very_high")
    }

    /// A title around the card that names the level alert (Android's notification) says the range: no word, the pill
    /// stays (Android RangeWordTest).
    @Test func aTitleThatNamesTheRangeDropsTheWordAndKeepsThePill() {
        let lay = draw(readings(64), .paused(.low, until: now + 600), rangeSaid: true)
        #expect(lay.first("value.range") == nil)
        #expect(lay.first("value.status")?.info.hasPrefix("carbs") == true)
    }
}

/// contracts/graph README "The value with a pill" (round 3 of #36; the owner: "it's fine to decrease it a little"):
/// with a pill or the range word the number keeps at least 85 % of its height without one (Android PillValueSizeTest).
@Suite struct PillValueSizeTests {
    private func readings(_ v: Double) -> [GraphReading] {
        (1...24).reversed().map { GraphReading(t: now - 60 - Double($0) * 300, v: v, rate: 0) }
            + [GraphReading(t: now - 60, v: v, rate: -0.4)]
    }

    private func numberH(_ v: Double, _ status: AlertStatus, size: CGSize, rangeSaid: Bool, part: CardPart) -> CGFloat {
        var st = CardSurface.widgetValue.style(GraphConfig(), sensor: nil)
        st.rangeSaid = rangeSaid
        let lay = GlucoseCard.draw(
            cardContext(size), size: size, readings: readings(v), marks: [], now: now, st, GraphPalette.of(dark: false),
            surface: .test, part: part, alerts: CardAlerts(status: status))
        return lay.first("value.number")?.box?.height ?? 0
    }

    private func keeps(_ size: CGSize, part: CardPart, v: Double = 64) {
        let bare = numberH(v, .none, size: size, rangeSaid: true, part: part)
        #expect(bare > 0)
        let side: AlertStatus.Side = v < 100 ? .low : .high
        for status in [AlertStatus.needed(side), .paused(side, until: now + 1800)] {
            let with = numberH(v, status, size: size, rangeSaid: false, part: part)
            #expect(with >= bare * GlucoseCard.pillKeep - 0.5, "\(size) \(status): \(with / bare)")
        }
        let word = numberH(v, .none, size: size, rangeSaid: false, part: part)
        #expect(word >= bare * GlucoseCard.pillKeep - 0.5, "\(size) range word: \(word / bare)")
    }

    @Test func theNotificationValueBoxKeepsTheNumberLarge() {
        keeps(CGSize(width: 151, height: 48), part: .value)
        keeps(CGSize(width: 171, height: 40), part: .value)
        // (SF's "Very high" is wider than Android's: the Mac, which has no notification card, keeps 80 % there)
    }

    @Test func theWidgetsKeepTheNumberLarge() {
        keeps(CGSize(width: 170, height: 80), part: .all)
        keeps(CGSize(width: 250, height: 80), part: .all)
        keeps(CGSize(width: 250, height: 80), part: .all, v: 262)
    }
}
