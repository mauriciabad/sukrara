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
        _ rs: [GraphReading], _ status: AlertStatus = .none, size: CGSize = CGSize(width: 332, height: 138)
    ) -> CardLayout {
        let st = CardSurface.widgetValue.style(GraphConfig(), sensor: nil)
        return GlucoseCard.draw(
            cardContext(size), size: size, readings: rs, marks: [], now: now, st, GraphPalette.of(dark: false),
            surface: .test, part: .all, alerts: CardAlerts(status: status))
    }

    @Test func aLowWithoutAPillSaysLowUnderTheValue() {
        let lay = draw(readings(64))
        #expect(lay.first("value.range")?.info == "low [row]")
        #expect(lay.first("value.status") == nil)
    }

    @Test func eatCarbsSaysTheSideAlreadyAMutePillDoesnt() {
        #expect(draw(readings(64), .needed(.low)).first("value.range") == nil)
        #expect(draw(readings(64), .paused(.low, until: now + 600)).first("value.range")?.info == "low [row]")
    }

    @Test func inRangeAndStruckValuesSayNothing() {
        #expect(draw(readings(125)).first("value.range") == nil)
        #expect(draw(readings(64, age: 360)).first("value.range") == nil)
    }

    @Test func veryHighIsSaidApartFromLow() {
        #expect(draw(readings(262)).first("value.range")?.info == "very_high [row]")
    }
}
