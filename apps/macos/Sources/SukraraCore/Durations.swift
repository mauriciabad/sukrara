// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// THE duration formatter and parser: every user-visible duration goes through here (formats and cases:
/// `contracts/settings/durations.json`, the same file Android's `Durations` is tested against).
public enum Durations {
    /// In words and settings (notifications, menus, presets, history): "45 min", "1 h", "1 h 30 min", "12 h".
    public static func text(_ minutes: Int) -> String {
        let m = max(0, minutes)
        if m < 60 { return "\(m) min" }
        return m % 60 == 0 ? "\(m / 60) h" : "\(m / 60) h \(m % 60) min"
    }

    /// `text` of a span in seconds, partial minutes rounded up (time left: "1 min" until it is over).
    public static func text(seconds: TimeInterval) -> String { text(minutesLeft(seconds)) }

    /// Whole minutes left of `seconds`, a partial minute counting as one ("1 min" until it is over).
    public static func minutesLeft(_ seconds: TimeInterval) -> Int { (Int(max(0, seconds).rounded(.down)) + 59) / 60 }

    /// The card's compact convention: "12m", "1h 5m", "3h" (from 6 h whole hours only), "2d" from two days; in
    /// Spanish "12 min", "1 h 5 min", "2 d" (`DurationWords.es`).
    public static func compact(_ minutes: Int, _ w: DurationWords = .en) -> String {
        let m = max(0, minutes)
        switch m {
        case ..<60: return w.minutes(m)
        case ..<(6 * 60): return m % 60 == 0 ? w.hours(m / 60) : w.hoursMinutes(m / 60, m % 60)
        case ..<(48 * 60): return w.hours(m / 60)
        default: return w.days(m / 1440)
        }
    }

    /// Whether `compact(minutes)` is a single unit of 1 ("1m", "1h", "1d"): Spanish says "queda 1 min" but "quedan 1 h
    /// 5 min" (Android `Durations.compactQuantity`).
    public static func compactIsOne(_ minutes: Int) -> Bool {
        let m = max(0, minutes)
        switch m {
        case ..<60: return m == 1
        case ..<(6 * 60): return m == 60
        case ..<(48 * 60): return m / 60 == 1
        default: return m / 1440 == 1
        }
    }

    /// One unit, rounded down: "45m", from 60 min "20h", from 24 h "13d" (Spanish "45 min", "20 h", "13 d").
    public static func whole(_ minutes: Int, _ w: DurationWords = .en) -> String {
        let m = max(0, minutes)
        if m >= 1440 { return w.days(m / 1440) }
        if m >= 60 { return w.hours(m / 60) }
        return w.minutes(m)
    }

    /// Typed minutes: "45", "45 min", "1h", "1 h 30", "1h 30m", "1:30", "1,5 h" → minutes; nil when it isn't a
    /// duration. The caller checks the range.
    public static func parse(_ input: String) -> Int? {
        let s = input.trimmingCharacters(in: .whitespaces).lowercased()
        guard !s.isEmpty else { return nil }
        if let m = s.wholeMatch(of: /(\d{1,3}):(\d{2})/) {
            guard let h = Int(m.1), let mm = Int(m.2), mm < 60 else { return nil }
            return h * 60 + mm
        }
        guard let m = s.wholeMatch(of: /(?:(\d{1,3}(?:[.,]\d+)?)\s*h)?\s*(?:(\d{1,4})\s*(?:min|m)?)?/) else {
            return nil
        }
        let hours = m.1.map(String.init)
        let mins = m.2.map(String.init)
        if hours == nil && mins == nil { return nil }
        let fromHours = hours.flatMap { Double($0.replacingOccurrences(of: ",", with: ".")) }.map { $0 * 60 } ?? 0
        return Int((fromHours + Double(mins.flatMap(Int.init) ?? 0)).rounded())
    }
}

/// The unit words of the card's short durations (`Durations.compact` / `whole`; Android `DurationWords` from the
/// graph's `duration_*` strings): English without spaces ("45m", "1h 5m", "2d"), Spanish with them, SI style ("45 min",
/// "1 h 5 min", "2 d"; contracts/settings/durations.json "es").
public struct DurationWords: Sendable {
    public let minutes: @Sendable (Int) -> String
    public let hours: @Sendable (Int) -> String
    public let hoursMinutes: @Sendable (Int, Int) -> String
    public let days: @Sendable (Int) -> String

    public static let en = DurationWords(
        minutes: { "\($0)m" }, hours: { "\($0)h" }, hoursMinutes: { "\($0)h \($1)m" }, days: { "\($0)d" })
    public static let es = DurationWords(
        minutes: { "\($0) min" }, hours: { "\($0) h" }, hoursMinutes: { "\($0) h \($1) min" }, days: { "\($0) d" })

    /// The words of the language the app speaks (`AppLanguage`).
    public static var current: DurationWords { AppLanguage.current == .es ? .es : .en }
}
