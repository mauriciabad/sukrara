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
        if let got { #expect(got.words == c.expect.string("text")) }
    }
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
