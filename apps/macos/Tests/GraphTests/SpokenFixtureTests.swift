// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import Testing

@testable import SukraraGraph

/// THE spoken sentence (#25) against contracts/graph/spoken, in English and Spanish: the card's sentence through
/// `A11y.card` and each slot's words through `A11y.metric` / `A11y.last`. Android's SpokenFixtureTest runs the same files.
@Suite struct SpokenFixtureTests {
    private let now = GraphFixtures.now
    private static let languages: [(String, CardWords)] = [("en", .en), ("es", .es)]

    @Test func everyCardCaseIsHere() {
        #expect(GraphFixtures.cases("spoken/card.json").count >= 20)
        #expect(GraphFixtures.cases("spoken/slots.json").count >= 10)
    }

    @Test(arguments: GraphFixtures.cases("spoken/card.json"))
    func card(_ c: JSON) {
        let r = c.child("reading")
        let latest = r.number("age").map { GraphReading(t: now - $0, v: r.number("sgv") ?? 0, rate: r.number("rate")) }
        let h = c.child("history")
        let history = h.number("hours").map { hours in
            let from = latest?.t ?? now
            return (1...Int(hours) * 12).reversed().map {
                GraphReading(t: from - Double($0) * 300, v: h.number("sgv")!)
            }
        }
        let s = c.child("sensor")
        let sensor = s.number("start").map { start in
            SensorInfo(start: now + start, end: now + s.number("end")!, warmupEnd: now + start + 3600)
        }
        let card = c.child("card")
        let rings = (card["rings"] as? [[String: Any]] ?? []).map(JSON.init).map {
            RingSpec(
                window: $0.number("window")!, range: TirRange(rawValue: $0.string("range") ?? "IN_RANGE")!,
                kind: SlotKind(rawValue: $0.string("kind")!)!)
        }
        var style = CardStyle(
            graph: card.number("graph").map { GraphStyle(window: $0 * 3600) }, rings: rings,
            value: card.bool("value") ?? true, badge: card.bool("badge") ?? true, sensor: sensor,
            countdownHours: card.number("countdownHours").map { Int($0) } ?? SensorLife.defaultCountdownHours,
            sensorTime: card.bool("sensorTime") ?? false, bands: Bands())
        let unit: GlucoseUnit = c.string("unit") == "mmol" ? .mmol : .mgdl
        style.unit = unit
        let data = CardData(
            readings: (history ?? []) + [latest].compactMap { $0 }, sensor: sensor,
            alerts: CardAlerts(status: status(c.string("status"))), now: now)
        // the reading's time as the clock writes it here (another test's 12 / 24-hour switch is its task's own)
        let time = latest.map { Freshness.timeText($0.t, now: now) } ?? ""
        for (lang, words) in Self.languages {
            let want = (c.expect.string(lang) ?? "?").replacingOccurrences(of: "{time}", with: time)
            let got = A11y.card(data, unit: unit, surface: .widgetCombined, style: style, words: words)
            #expect(got == want, "\(lang): \(got)")
        }
    }

    @Test(arguments: GraphFixtures.cases("spoken/slots.json"))
    func slot(_ c: JSON) {
        for (lang, words) in Self.languages {
            let s = c.child("slot")
            let got: String
            if let kind = s.string("kind") {
                got = A11y.metric(
                    SlotKind(rawValue: kind)!, TirRange(rawValue: s.string("range") ?? "IN_RANGE")!,
                    window: s.number("window")!, text: s.string("text")!, words: words)
            } else {
                let t = mark(c.child("last"))
                let with = c["with"] == nil ? nil : LastTreatment.Companion(tr: mark(c.child("with")), until: nil)
                got = A11y.last(LastTreatment.Info(tr: t, until: nil, carbs: with), now: now, words: words)
            }
            #expect(got == c.expect.string(lang), "\(lang)")
        }
    }

    private func mark(_ t: JSON) -> GraphMark {
        GraphMark(t: now - t.number("age")!, k: t.string("k")!, a: t.number("a")!)
    }

    /// `needed:low`, `taken:carbs:600`, `paused:high:never`, `muted:4800` (seconds from now) as the card's status.
    private func status(_ s: String?) -> AlertStatus {
        guard let p = s?.split(separator: ":").map(String.init) else { return .none }
        func end(_ v: String) -> TimeInterval { v == "never" ? AlertStatus.never : now + Double(v)! }
        switch p[0] {
        case "needed": return .needed(p[1] == "low" ? .low : .high)
        case "taken":
            let carbs = p[1] == "carbs"
            return .taken(
                carbs ? .low : .high, treatment: GraphMark(t: now - 600, k: p[1], a: carbs ? 30 : 3), until: end(p[2]))
        case "paused": return .paused(p[1] == "low" ? .low : .high, until: end(p[2]))
        default: return .muted(until: end(p[1]))
        }
    }
}
