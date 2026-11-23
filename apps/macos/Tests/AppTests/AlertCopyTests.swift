// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraTesting
import Testing

@testable import SukraraApp

/// Every notification case of `contracts/alerts/copy.json` the Mac writes, in en and es (#42): the title, the body in
/// mg/dL and mmol/L, the actions in the Mac's title case, the test alert, and Mute all's scope under its panel line.
/// The same file is Android's AlertCopyTest; the state lines are `MuteCopyTests`.
@Suite struct AlertCopyTests {
    nonisolated struct Case: Decodable, Sendable, CustomTestStringConvertible {
        var state: String
        var kind: String?
        var treated: Int?
        var mgdl: Double?
        var rate: Double?
        var ageMin: Int?
        var ageS: Int?
        var unit: String?
        var action: String?
        var platform: String?
        var device: String?
        var en: String
        var es: String
        var en_mac: String?
        var es_mac: String?
        /// `maintenance`: the no-data alert while LibreLinkUp's region is down for LibreView's maintenance.
        var why: String?
        /// The maintenance's end as the clock writes it.
        var ends: String?
        var testDescription: String { "\(state) \(kind ?? action ?? device ?? "") → \(en)" }
    }

    nonisolated struct File: Decodable { var cases: [Case] }

    nonisolated static let notification: Set<String> = [
        "headline", "headline_escalated", "body", "action", "test_title", "all_scope",
    ]

    nonisolated static func cases() -> [Case] {
        guard let data = try? Data(contentsOf: Contracts.url("alerts/copy.json")) else { return [] }
        return ((try? JSONDecoder().decode(File.self, from: data).cases) ?? []).filter {
            notification.contains($0.state) && $0.platform != "android" && $0.device != "phone"
        }
    }

    static let at = Date(timeIntervalSince1970: 1_000_020)

    /// A body case's reading time: `ageS` or `ageMin` before `at`.
    static func readingAt(_ c: Case) -> Date { at.addingTimeInterval(-TimeInterval(c.ageS ?? (c.ageMin ?? 0) * 60)) }

    /// `c`'s expected text, its `{time}` the reading's time as the card writes it (`Freshness.timeText`).
    static func expected(_ text: String, _ c: Case) -> String {
        text.replacingOccurrences(
            of: "{time}",
            with: Freshness.timeText(readingAt(c).timeIntervalSince1970, now: at.timeIntervalSince1970))
    }

    /// The Mac panel's catalog value of `key` in `language`.
    static func catalog(_ key: String, _ language: String) throws -> String? {
        let json = try JSONSerialization.jsonObject(with: Data(contentsOf: LocalizationTests.catalog)) as? [String: Any]
        let entry = (json?["strings"] as? [String: Any])?[key] as? [String: Any]
        let loc = (entry?["localizations"] as? [String: Any])?[language] as? [String: Any]
        return (loc?["stringUnit"] as? [String: Any])?["value"] as? String
    }

    /// `c` as the Mac words it in `lang`.
    static func word(_ c: Case, _ lang: AppLanguage) throws -> String? {
        var text = AlertText(language: lang)
        text.unit = c.unit == "mmol" ? .mmol : .mgdl
        func firing() -> AlertFiring? {
            guard let kind = c.kind.flatMap(AlertKind.init(rawValue:)) else { return nil }
            let reading = c.mgdl.map { GlucoseSample(at: readingAt(c), mgdl: $0, rate: c.rate) }
            return AlertFiring(
                id: "e1", kind: kind, at: at, reading: reading, threshold: kind == .noData ? 20 : 70, n: 1,
                treated: c.treated ?? 0)
        }
        let why: AlertText.Why? =
            switch c.why {
            case "maintenance": .maintenance(ends: c.ends)
            case "update": .update(thisApp: true)
            case "update_wearer": .update(thisApp: false)
            default: nil
            }
        switch c.state {
        case "headline": return firing().map { text.title($0, escalated: false, why: why) }
        case "headline_escalated": return firing().map { text.title($0, escalated: true) }
        case "body": return firing().map { text.body($0, now: at, why: why) }
        case "test_title": return firing().map(text.testTitle)
        case "all_scope": return try catalog("Very low and no data included, on this Mac only", lang.rawValue)
        case "action":
            switch c.action {
            case "mute": return text.mute
            case "remind_less": return text.remindLess
            case "remind_as_usual": return text.remindAsUsual
            case "unmute": return text.unmute
            case "treatment": return text.treatment
            case "update": return text.update
            case "mute_input": return text.mutePlaceholder
            case "mute_send": return text.muteButton
            default: return nil
            }
        default: return nil
        }
    }

    @Test func copyFileIsFound() { #expect(Self.cases().count >= 30) }

    @Test(arguments: cases())
    func copy(_ c: Case) throws {
        #expect(try Self.word(c, .en) == Self.expected(c.en_mac ?? c.en, c))
        #expect(try Self.word(c, .es) == Self.expected(c.es_mac ?? c.es, c))
    }
}
