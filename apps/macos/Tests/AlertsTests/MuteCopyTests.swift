// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraTesting
import Testing

/// Every case of `contracts/alerts/copy.json` in English and Spanish, the file Android's MuteCopyTest reads too. The
/// card's pill cases (`pill`, `pill_forever`) are the renderer's (GraphTests `BadgeCopyTests`).
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

    static func cases() -> [Case] {
        guard let data = try? Data(contentsOf: Contracts.url("alerts/copy.json")) else { return [] }
        return (try? JSONDecoder().decode(File.self, from: data).cases) ?? []
    }

    @Test func copyFileIsFound() { #expect(Self.cases().count >= 15) }

    static func line(_ c: Case, _ l: AppLanguage) -> String? {
        let side = c.side.flatMap(AlertSide.init(rawValue:))
        return switch c.state {
        case "side_muted": side.map { MuteCopy.sideMuted($0, leftMin: c.leftMin ?? -1, l) }
        case "side_after":
            side.flatMap { s in
                c.treatment.flatMap(TreatmentKind.init(rawValue:)).map {
                    MuteCopy.sideAfter(s, treatment: $0, leftMin: c.leftMin ?? -1, l)
                }
            }
        case "side_falling": side.map { MuteCopy.sideFalling($0, l) }
        case "side_less": side.map { MuteCopy.sideLess($0, intervalMin: c.intervalMin ?? -1, l) }
        case "all_timed": MuteCopy.allTimed(leftMin: c.leftMin ?? -1, l)
        case "all_forever": MuteCopy.allForever(l)
        case "notif_title": side.map { MuteCopy.notificationTitle($0, l) }
        case "notif_text": MuteCopy.notificationText(until: c.until ?? "", l)
        case "choice": MuteCopy.choice(c.min ?? -1, l)
        case "choice_forever": MuteCopy.choiceForever(l)
        default: nil
        }
    }

    @Test(arguments: cases().filter { !$0.state.hasPrefix("pill") })
    func line(_ c: Case) throws {
        #expect(Self.line(c, .en) == c.en)
        #expect(Self.line(c, .es) == c.es)
    }

    @Test func allLineCountsDown() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let end = now.addingTimeInterval(72 * 60)
        #expect(MuteCopy.allLine(until: end, now: now, .en) == "Alerts muted · 1 h 12 min left")
        #expect(
            MuteCopy.allLine(until: end, now: now.addingTimeInterval(71 * 60 + 1), .en) == "Alerts muted · 1 min left")
        #expect(
            MuteCopy.allLine(until: end, now: now.addingTimeInterval(71 * 60 + 1), .es)
                == "Alertas silenciadas · queda 1 min")
        #expect(MuteCopy.allLine(until: end, now: end, .en) == nil)
        #expect(MuteCopy.allLine(until: muteAllForever, now: now, .en) == MuteCopy.allForever(.en))
    }

    @Test func quietReasonLines() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let in12 = now.addingTimeInterval(12 * 60)
        #expect(
            MuteCopy.line(.treatment(.carbs, at: now, until: in12), side: .low, now: now, .en)
                == "Low alerts muted after carbs · 12 min left")
        #expect(
            MuteCopy.line(.fallingBack(inMinutes: 8), side: .high, now: now, .en)
                == "High alerts muted while falling back into range")
        #expect(
            MuteCopy.line(.sideMuted(until: in12), side: .noData, now: now, .en) == "No data alerts muted · 12 min left"
        )
        #expect(
            MuteCopy.line(.sideMuted(until: in12), side: .noData, now: now, .es)
                == "Alertas sin datos silenciadas · quedan 12 min")
        // a quiet that has ended never claims alerts are muted
        #expect(MuteCopy.line(.allMuted(until: now), side: .high, now: now, .en) == nil)
        #expect(MuteCopy.line(.sideMuted(until: now), side: .low, now: now.addingTimeInterval(1), .en) == nil)
    }
}
