// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// The languages the alert copy is written in (`contracts/alerts/copy.json`: en + es).
public enum CopyLanguage: String, Sendable, CaseIterable {
    case en, es
}

/// THE mute / remind-less state lines (SPEC §2 "Copy"): every line case of `contracts/alerts/copy.json`, in English
/// (the default: the panel and menus) and Spanish (the notifications in the person's language, `AlertText`). Durations
/// only through `Durations`. Lines are sentence case; Mac menu items are title-style.
public enum MuteCopy {
    /// The side's name ("low" covers very low, "high" very high and extremely high): "Low"; es "de glucosa baja".
    public static func sideName(_ side: AlertSide, _ lang: CopyLanguage = .en) -> String {
        switch (side, lang) {
        case (.low, .en): "Low"
        case (.high, .en): "High"
        case (.fallingFast, .en): "Falling fast"
        case (.risingFast, .en): "Rising fast"
        case (.noData, .en): "No data"
        case (.low, .es): "de glucosa baja"
        case (.high, .es): "de glucosa alta"
        case (.fallingFast, .es): "de bajada rápida"
        case (.risingFast, .es): "de subida rápida"
        case (.noData, .es): "sin datos"
        }
    }

    /// "1 h 12 min left"; es "quedan 1 h 12 min" ("queda" with one unit of 1: "queda 1 h", "queda 1 min").
    private static func left(_ minutes: Int, _ lang: CopyLanguage) -> String {
        let text = Durations.text(minutes)
        guard lang == .es else { return "\(text) left" }
        let quantity = minutes >= 60 && minutes % 60 == 0 ? minutes / 60 : minutes
        return "\(quantity == 1 ? "queda" : "quedan") \(text)"
    }

    /// "Low alerts muted · 12 min left".
    public static func sideMuted(_ side: AlertSide, leftMin: Int, _ lang: CopyLanguage = .en) -> String {
        "\(notificationTitle(side, lang)) · \(left(leftMin, lang))"
    }

    /// "Low alerts muted after carbs · 12 min left", "High alerts muted after insulin · 1 h left".
    public static func sideAfter(
        _ side: AlertSide, treatment: TreatmentKind, leftMin: Int, _ lang: CopyLanguage = .en
    ) -> String {
        switch lang {
        case .en:
            "\(sideName(side)) alerts muted after \(treatment == .carbs ? "carbs" : "insulin") · \(left(leftMin, lang))"
        case .es:
            "Alertas \(sideName(side, .es)) silenciadas tras \(treatment == .carbs ? "los hidratos" : "la insulina") · "
                + left(leftMin, lang)
        }
    }

    /// "High alerts muted while falling back into range" (no end of its own).
    public static func sideFalling(_ side: AlertSide, _ lang: CopyLanguage = .en) -> String {
        switch lang {
        case .en: "\(sideName(side)) alerts muted while falling back into range"
        case .es: "Alertas \(sideName(side, .es)) silenciadas mientras baja hacia el rango"
        }
    }

    /// "Low reminders every 30 min".
    public static func sideLess(_ side: AlertSide, intervalMin: Int, _ lang: CopyLanguage = .en) -> String {
        switch lang {
        case .en: "\(sideName(side)) reminders every \(Durations.text(intervalMin))"
        case .es: "Avisos \(sideName(side, .es)) cada \(Durations.text(intervalMin))"
        }
    }

    /// Mute all for a time: "Alerts muted · 1 h 12 min left".
    public static func allTimed(leftMin: Int, _ lang: CopyLanguage = .en) -> String {
        "\(lang == .en ? "Alerts muted" : "Alertas silenciadas") · \(left(leftMin, lang))"
    }
    public static let allForever = "Alerts muted until you turn it off"
    public static func allForever(_ lang: CopyLanguage) -> String {
        lang == .en ? allForever : "Alertas silenciadas hasta que lo desactives"
    }

    /// The silent notification while a side is muted (Mac notifications can't count down: they say the end).
    public static func notificationTitle(_ side: AlertSide, _ lang: CopyLanguage = .en) -> String {
        lang == .en ? "\(sideName(side)) alerts muted" : "Alertas \(sideName(side, .es)) silenciadas"
    }
    public static func notificationText(until: String, _ lang: CopyLanguage = .en) -> String {
        lang == .en ? "Until \(until)" : "Hasta las \(until)"
    }

    /// The card pill while Mute all runs in range: "Muted · 1h 12m" / "Muted"; es "Silenciadas · 1 h 12 min" (Spanish
    /// writes the compact durations in words).
    public static func pill(leftMin: Int, _ lang: CopyLanguage = .en) -> String {
        lang == .en ? "Muted · \(Durations.compact(leftMin))" : "Silenciadas · \(compactEs(leftMin))"
    }
    public static let pillForever = "Muted"
    public static func pillForever(_ lang: CopyLanguage) -> String { lang == .en ? pillForever : "Silenciadas" }

    /// `Durations.compact`'s steps in Spanish words (Android's `duration_*` es strings): "12 min", "1 h 5 min", whole
    /// hours from 6 h ("7 h"), days from two days ("2 d").
    private static func compactEs(_ minutes: Int) -> String {
        let m = max(0, minutes)
        switch m {
        case ..<(6 * 60): return Durations.text(m)
        case ..<(48 * 60): return Durations.text(m / 60 * 60)
        default: return "\(m / 1440) d"
        }
    }

    /// A Mute choice: "For 30 min", "For 1 h".
    public static func choice(_ minutes: Int, _ lang: CopyLanguage = .en) -> String {
        "\(lang == .en ? "For" : "Durante") \(Durations.text(minutes))"
    }
    public static let choiceForever = "Until you turn it off"
    public static func choiceForever(_ lang: CopyLanguage) -> String {
        lang == .en ? choiceForever : "Hasta que lo desactives"
    }
    public static let menuForever = "Until You Turn It Off"

    /// The line for Mute all ending at `until` (`muteAllForever` = indefinite), nil once it is over.
    public static func allLine(until: Date, now: Date, _ lang: CopyLanguage = .en) -> String? {
        guard until > now else { return nil }
        return until >= muteAllForever
            ? allForever(lang) : allTimed(leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), lang)
    }

    /// The line for a quiet reason on `side`, counting down at `now`; nil once a timed one is over (never claims a
    /// quiet that has ended).
    public static func line(_ reason: QuietReason, side: AlertSide, now: Date, _ lang: CopyLanguage = .en) -> String? {
        switch reason {
        case .treatment(_, _, let until) where until <= now: nil
        case .sideMuted(let until) where until <= now: nil
        case .treatment(let kind, _, let until):
            sideAfter(side, treatment: kind, leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), lang)
        case .fallingBack: sideFalling(side, lang)
        case .sideMuted(let until):
            sideMuted(side, leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), lang)
        case .allMuted(let until): allLine(until: until, now: now, lang)
        }
    }
}
