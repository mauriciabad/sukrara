// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import Foundation
import SukraraCore
import SukraraGraph
import Testing

/// The entry points every Mac surface uses (no `@testable`: only what the app and the widget can call).
@Suite struct PublicAPITests {
    private let now = GraphFixtures.now
    private var reading: GraphReading { GraphReading(t: now - 60, v: 112, rate: -1.2) }

    @Test func widgetSnapshotRoundTripsAndRejectsOtherVersions() throws {
        var config = GraphConfig()
        config.bands.high = 200
        let data = CardData(
            readings: [reading], marks: [GraphMark(t: now - 600, k: "rapid", a: 4.5, id: "i1")],
            sensor: SensorInfo(start: now - 86400, end: now + 13 * 86400, warmupEnd: now - 82800),
            alerts: CardAlerts(status: .paused(.high, until: AlertStatus.never), muteEnds: ["i1": now + 3000]), now: now
        )
        let snap = WidgetSnapshot(writtenAt: now, data: data, config: config, unit: .mmol)
        let bytes = try snap.encoded()
        #expect(WidgetSnapshot.decode(bytes) == snap)
        var other = try #require(try JSONSerialization.jsonObject(with: bytes) as? [String: Any])
        other["v"] = WidgetSnapshot.version + 1
        #expect(WidgetSnapshot.decode(try JSONSerialization.data(withJSONObject: other)) == nil)
        #expect(WidgetSnapshot.decode(Data("{}".utf8)) == nil)
    }

    @Test func faceIsInTheUsersUnit() {
        let data = CardData(readings: [reading], now: now)
        #expect(GlucoseCard.face(data, unit: .mgdl, surface: .menubarTitle).text == "112")
        #expect(GlucoseCard.face(data, unit: .mmol, surface: .menubarTitle).text == "6.2")
        #expect(GlucoseCard.face(CardData(readings: [], now: now), unit: .mgdl, surface: .menubarTitle).text == nil)
    }

    /// The value widget's sentence (no chart) in `words`, with the default settings unless `config` says otherwise.
    private func spoken(
        _ data: CardData, _ unit: GlucoseUnit = .mgdl, surface: CardSurface = .widgetValue,
        config: GraphConfig = GraphConfig(), words: CardWords = .en
    ) -> String {
        GlucoseCard.accessibilityLabel(data, unit: unit, surface: surface, config: config, words: words)
    }

    /// The public entry point says THE sentence (contracts/graph/spoken, `SpokenFixtureTests`) with each surface's
    /// own parts: the value surfaces their slots, the chart surfaces their summary, the menu bar title the value only.
    @Test func everySurfaceSaysTheSameGlucoseSentence() {
        // every 5 min: the newest 9 h in range, the 3 h before high
        let rs = (0..<144).map { GraphReading(t: now - Double($0) * 300, v: $0 < 108 ? 120 : 200, rate: 0) }
        let data = CardData(readings: rs, now: now)
        let glucose = "120 milligrams per decilitre, steady, in range, just now"
        #expect(spoken(data, surface: .menubarTitle) == glucose)
        for s in [CardSurface.menubarCard, .widgetValue, .widgetCombined] {
            #expect(spoken(data, surface: s).hasPrefix(glucose + ". "), "\(s)")
        }
        // the default slots: the day's ring (the 7-day one has too little data), then the chart's summary
        #expect(spoken(data, surface: .widgetValue) == glucose + ". 75% in range over 1 day")
        #expect(
            spoken(data, surface: .widgetCombined).hasSuffix(
                ". 75% in range over 1 day. Last 12 hours: 75% in range, 0% below, 25% above"))
        var six = GraphConfig()
        six.hours = 6
        #expect(
            spoken(data, surface: .menubarCard, config: six).hasSuffix(
                "Last 6 hours: 100% in range, 0% below, 0% above"))
        // the graph widget shows no value: its data's time (as its pill, at every age), then the summary
        #expect(spoken(data, surface: .widgetGraph).hasPrefix("Glucose: last reading "))
        #expect(spoken(data, surface: .widgetGraph).contains(". Last 12 hours: "))
        #expect(spoken(CardData(readings: [], now: now), surface: .menubarTitle) == "Glucose: no readings yet")
    }

    /// Never by colour alone: the range follows the user's bounds (UX review F6).
    @Test func voiceOverRangeFollowsTheSettings() {
        let data = CardData(readings: [GraphReading(t: now - 60, v: 200, rate: 0)], now: now)
        #expect(spoken(data, surface: .menubarTitle).contains(", high, "))
        var raised = GraphConfig()
        raised.bands.high = 220
        #expect(spoken(data, surface: .menubarTitle, config: raised).contains(", in range, "))
    }

    @Test func voiceOverInSpanish() {
        let low = CardData(
            readings: [GraphReading(t: now - 120, v: 64, rate: -2.5)], alerts: CardAlerts(status: .needed(.low)),
            now: now)
        #expect(
            spoken(low, surface: .menubarTitle, words: .es)
                == "64 miligramos por decilitro, bajando rápido, baja, hace 2 min. Come hidratos")
    }

    @Test func everyCardSurfaceRendersABitmap() {
        let data = CardData(readings: [reading], now: now)
        for s in CardSurface.allCases where s != .menubarTitle {
            let image = GlucoseCard.image(
                surface: s, size: CGSize(width: 332, height: 138), config: GraphConfig(), data: data, unit: .mmol,
                dark: true)
            #expect(image?.width == 664, "\(s)")
        }
    }
}
