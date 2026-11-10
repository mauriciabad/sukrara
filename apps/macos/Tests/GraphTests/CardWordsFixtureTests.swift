// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraGraph

/// The card's drawn words in both languages (#77) against contracts/graph/words/words.json, the file Android's
/// CardWordsFixtureTest checks its strings.xml against: the Mac draws the same words as Android.
@Suite struct CardWordsFixtureTests {
    static let root = GraphFixtures.object("words/words.json")
    static let languages: [(String, CardWords)] = [("en", .en), ("es", .es)]

    /// A word or format of the fixture with the sample arguments filled in.
    static func filled(_ format: String) -> String {
        format.replacingOccurrences(of: "%1$s", with: "X").replacingOccurrences(of: "%1$d", with: "7")
            .replacingOccurrences(of: "%2$d", with: "5")
    }

    /// What the Mac draws for an Android string name, with the same sample arguments.
    static func mac(_ key: String, _ w: CardWords) -> String? {
        let d = w.drawn
        return switch key {
        case "badge_eat_carbs": d.eatCarbs
        case "badge_carbs_short": d.carbsShort
        case "badge_take_insulin": d.takeInsulin
        case "badge_insulin_short": d.insulinShort
        case "badge_muted_short": d.muted
        case "badge_muted_for": d.mutedFor("X")
        case "badge_replace_sensor": d.replaceSensor
        case "badge_replace_sensor_short": d.replaceSensorShort
        case "chart_back_to_now": d.backToNow
        case "duration_now": d.now
        case "duration_minutes": d.durations.minutes(7)
        case "duration_hours": d.durations.hours(7)
        case "duration_hours_minutes": d.durations.hoursMinutes(7, 5)
        case "duration_days": d.durations.days(7)
        case "period_days": d.periodDays(7)
        case "period_hours": d.periodHours(7)
        case "slot_average_short": d.average
        case "slot_gmi_short": d.gmi
        case "slot_cv_short": d.cv
        case "card_no_readings_yet": d.noReadingsYet
        case "card_no_reading_since": d.noReadingSince
        case "card_sensor_ended": d.sensorEnded
        case "card_warming_up": d.warmingUp
        case "card_ready_at": d.readyAt("X")
        case "card_almost_ready": d.almostReady
        case "card_waiting_reading": d.waitingReading
        case "card_range_very_low": w.rangeWord(.veryLow)
        case "card_range_low": w.rangeWord(.low)
        case "card_range_high": w.rangeWord(.high)
        case "card_range_very_high": w.rangeWord(.veryHigh)
        case "card_range_extremely_high": w.rangeWord(.extremelyHigh)
        default: nil
        }
    }

    @Test func everyDrawnWordIsAndroidsInBothLanguages() throws {
        let words = try #require(Self.root["words"] as? [String: [String: String]])
        #expect(words.count >= 30)
        for (key, byLanguage) in words.sorted(by: { $0.key < $1.key }) {
            for (language, w) in Self.languages {
                let want = try #require(byLanguage[language], "\(key) has no \(language)")
                #expect(Self.mac(key, w) == Self.filled(want), "\(language) \(key)")
            }
        }
    }

    @Test func timeLeftAgreesWithItsDuration() throws {
        let plurals = try #require(Self.root["plurals"] as? [String: [String: [String: String]]])
        let left = try #require(plurals["badge_time_left"])
        for (language, w) in Self.languages {
            #expect(w.drawn.timeLeft("X", true) == left[language].flatMap { $0["one"] }.map(Self.filled))
            #expect(w.drawn.timeLeft("X", false) == left[language].flatMap { $0["other"] }.map(Self.filled))
        }
        #expect(CardWords.es.timeLeft(1) == "queda 1 min" && CardWords.es.timeLeft(45) == "quedan 45 min")
        #expect(CardWords.es.timeLeft(60) == "queda 1 h" && CardWords.es.timeLeft(65) == "quedan 1 h 5 min")
        #expect(CardWords.en.timeLeft(45) == "45m left")
    }

    /// The card's Mute all pill: contracts/alerts/copy.json `pill` / `pill_forever`, both languages.
    @Test func mutePillIsTheCopyFiles() throws {
        let data = try Data(contentsOf: Contracts.url("alerts/copy.json"))
        let cases = try #require(
            (try JSONSerialization.jsonObject(with: data) as? [String: Any])?["cases"] as? [[String: Any]])
        let pills = cases.filter { ($0["state"] as? String)?.hasPrefix("pill") == true }
        #expect(pills.count == 2)
        for c in pills {
            for (language, w) in Self.languages {
                let got =
                    c["state"] as? String == "pill"
                    ? BadgeCopy.pill(leftMin: (c["leftMin"] as? Int) ?? -1, w) : w.drawn.muted
                #expect(got == c[language] as? String, "\(language) \(c["state"] ?? "")")
            }
        }
    }

    /// Every pill the badge draws is in the card's language.
    @Test func thePillSpeaksTheCardsLanguage() {
        let now = GraphFixtures.now
        let es = CardWords.es
        #expect(StatusBadge.look(.needed(.low), now: now, words: es)?.text == "Come hidratos")
        #expect(StatusBadge.look(.needed(.high), now: now, words: es)?.short == "Insulina")
        let paused = StatusBadge.look(.paused(.high, until: now + 45 * 60), now: now, words: es)
        #expect(paused?.text == "quedan 45 min" && paused?.short == "45 min")
        #expect(StatusBadge.look(.muted(until: now + 80 * 60), now: now, words: es)?.text == "Silenciadas · 1 h 20 min")
        #expect(StatusBadge.replaceSensor(es).text == "Cambiar sensor" && StatusBadge.backToNow(es).text == "Ahora")
    }
}
