// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraTesting
import Testing

/// Every state-line case of `contracts/alerts/copy.json` in en and es, the file Android's AlertCopyTest reads too (the
/// notification's own words: AppTests `AlertCopyTests`).
@Suite struct MuteCopyTests {
    struct Case: Decodable, Sendable, CustomTestStringConvertible {
        var state: String
        var side: String?
        var treatment: String?
        var leftMin: Int?
        var intervalMin: Int?
        var min: Int?
        var until: String?
        var en: String
        var es: String
        var testDescription: String { "\(state) \(side ?? "") → \(en)" }
    }

    struct File: Decodable { var cases: [Case] }

    static let lines: Set<String> = [
        "side_muted", "side_after", "side_falling", "side_less", "all_timed", "all_forever", "notif_title",
        "notif_text",
        "pill", "pill_forever", "choice", "choice_forever",
    ]

    static func cases() -> [Case] {
        guard let data = try? Data(contentsOf: Contracts.url("alerts/copy.json")) else { return [] }
        return ((try? JSONDecoder().decode(File.self, from: data).cases) ?? []).filter { lines.contains($0.state) }
    }

    @Test func copyFileIsFound() { #expect(Self.cases().count >= 15) }

    /// `c` worded by `MuteCopy` in `lang`, or nil for a state it doesn't know.
    static func word(_ c: Case, _ lang: CopyLanguage) -> String? {
        let side = c.side.flatMap(AlertSide.init(rawValue:))
        return switch c.state {
        case "side_muted": side.map { MuteCopy.sideMuted($0, leftMin: c.leftMin ?? -1, lang) }
        case "side_after":
            side.flatMap { s in
                c.treatment.flatMap(TreatmentKind.init(rawValue:)).map {
                    MuteCopy.sideAfter(s, treatment: $0, leftMin: c.leftMin ?? -1, lang)
                }
            }
        case "side_falling": side.map { MuteCopy.sideFalling($0, lang) }
        case "side_less": side.map { MuteCopy.sideLess($0, intervalMin: c.intervalMin ?? -1, lang) }
        case "all_timed": MuteCopy.allTimed(leftMin: c.leftMin ?? -1, lang)
        case "all_forever": MuteCopy.allForever(lang)
        case "notif_title": side.map { MuteCopy.notificationTitle($0, lang) }
        case "notif_text": MuteCopy.notificationText(until: c.until ?? "", lang)
        case "pill": MuteCopy.pill(leftMin: c.leftMin ?? -1, lang)
        case "pill_forever": MuteCopy.pillForever(lang)
        case "choice": MuteCopy.choice(c.min ?? -1, lang)
        case "choice_forever": MuteCopy.choiceForever(lang)
        default: nil
        }
    }

    @Test(arguments: cases())
    func line(_ c: Case) {
        #expect(Self.word(c, .en) == c.en)
        #expect(Self.word(c, .es) == c.es)
    }

    @Test func allLineCountsDown() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let end = now.addingTimeInterval(72 * 60)
        #expect(MuteCopy.allLine(until: end, now: now) == "Alerts muted · 1 h 12 min left")
        #expect(MuteCopy.allLine(until: end, now: now.addingTimeInterval(71 * 60 + 1)) == "Alerts muted · 1 min left")
        #expect(
            MuteCopy.allLine(until: end, now: now.addingTimeInterval(71 * 60 + 1), .es)
                == "Alertas silenciadas · queda 1 min")
        #expect(MuteCopy.allLine(until: end, now: end) == nil)
        #expect(MuteCopy.allLine(until: muteAllForever, now: now) == MuteCopy.allForever)
    }

    @Test func quietReasonLines() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let in12 = now.addingTimeInterval(12 * 60)
        #expect(
            MuteCopy.line(.treatment(.carbs, at: now, until: in12), side: .low, now: now)
                == "Low alerts muted after carbs · 12 min left")
        #expect(
            MuteCopy.line(.fallingBack(inMinutes: 8), side: .high, now: now)
                == "High alerts muted while falling back into range")
        #expect(MuteCopy.line(.sideMuted(until: in12), side: .noData, now: now) == "No data alerts muted · 12 min left")
        #expect(
            MuteCopy.line(.sideMuted(until: in12), side: .noData, now: now, .es)
                == "Alertas sin datos silenciadas · quedan 12 min")
        // a quiet that has ended never claims alerts are muted
        #expect(MuteCopy.line(.allMuted(until: now), side: .high, now: now) == nil)
        #expect(MuteCopy.line(.sideMuted(until: now), side: .low, now: now.addingTimeInterval(1)) == nil)
    }
}
