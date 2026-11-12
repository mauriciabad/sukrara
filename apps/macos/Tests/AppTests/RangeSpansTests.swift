// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraAlerts
import SukraraCore
import SukraraGraph
import Testing

@testable import SukraraApp

/// The values a limit makes count, written under it (Android's RangeSpansTest has the same cases).
@Suite struct RangeSpansTests {
    typealias Span = RangeSpans.Span

    @Test func theRangesAtTheirDefaults() {
        let b = Bands()
        #expect(RangeSpans.ofBand(b, 0) == [Span(name: .veryLow, lo: nil, hi: 55)])
        #expect(RangeSpans.ofBand(b, 1) == [Span(name: .low, lo: 56, hi: 69), Span(name: .inRange, lo: 70, hi: 179)])
        #expect(RangeSpans.ofBand(b, 2) == [Span(name: .tight, lo: 70, hi: 140)])
        #expect(
            RangeSpans.ofBand(b, 3) == [Span(name: .high, lo: 180, hi: 249), Span(name: .inRange, lo: 70, hi: 179)])
        #expect(RangeSpans.ofBand(b, 4) == [Span(name: .veryHigh, lo: 250, hi: 299)])
        #expect(RangeSpans.ofBand(b, 5) == [Span(name: .extremelyHigh, lo: 300, hi: nil)])
        #expect(RangeSpans.text(RangeSpans.ofBand(b, 1), unit: .mgdl) == "Low: 56–69 mg/dL\nIn range: 70–179 mg/dL")
        #expect(RangeSpans.text(RangeSpans.ofBand(b, 0), unit: .mgdl) == "Very low: up to 55 mg/dL")
        #expect(RangeSpans.text(RangeSpans.ofBand(b, 5), unit: .mgdl) == "Extremely high: from 300 mg/dL")
        // the row being changed: its typed value
        #expect(RangeSpans.ofBand(RangeSpans.replacing(b, 1, 75), 1).first == Span(name: .low, lo: 56, hi: 75))
    }

    @Test func theAlertsAtTheirDefaults() {
        let s = AlertSettings()
        #expect(RangeSpans.ofAlert(.veryLow) { s[$0] } == [Span(name: .veryLow, lo: nil, hi: 55)])
        #expect(
            RangeSpans.ofAlert(.low) { s[$0] } == [
                Span(name: .low, lo: 56, hi: 69), Span(name: .inRange, lo: 70, hi: 179),
            ])
        #expect(
            RangeSpans.ofAlert(.high) { s[$0] }
                == [Span(name: .high, lo: 180, hi: 249), Span(name: .inRange, lo: 70, hi: 179)])
        #expect(RangeSpans.ofAlert(.veryHigh) { s[$0] } == [Span(name: .veryHigh, lo: 250, hi: 299)])
        #expect(RangeSpans.ofAlert(.extremelyHigh) { s[$0] } == [Span(name: .extremelyHigh, lo: 300, hi: nil)])
        #expect(RangeSpans.ofAlert(.noData) { s[$0] }.isEmpty)
    }

    /// A kind turned off no longer bounds its neighbours: with very high off, high reaches extremely high.
    @Test func onlyTheKindsTurnedOnBoundEachOther() {
        var s = AlertSettings()
        s[.veryHigh].on = false
        s[.veryLow].on = false
        #expect(RangeSpans.ofAlert(.high) { s[$0] }.first == Span(name: .high, lo: 180, hi: 299))
        #expect(RangeSpans.ofAlert(.low) { s[$0] }.first == Span(name: .low, lo: nil, hi: 69))
        // the kind being set counts as on: its own row shows what it would do
        #expect(RangeSpans.ofAlert(.veryHigh) { s[$0] }.first == Span(name: .veryHigh, lo: 250, hi: 299))
    }

    @Test func writtenInTheUnit() {
        #expect(
            RangeSpans.text(RangeSpans.ofBand(Bands(), 1), unit: .mmol)
                == "Low: 3.1–3.8 mmol/L\nIn range: 3.9–9.9 mmol/L")
        #expect(RangeSpans.text([Span(name: .tight, lo: 181, hi: 179)], unit: .mgdl) == nil)
        #expect(RangeSpans.text([Span(name: .low, lo: 69, hi: 69)], unit: .mgdl) == "Low: 69 mg/dL")
    }
}
