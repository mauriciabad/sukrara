// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// THE mute / remind-less state lines (SPEC §2 "Copy"): every case of `contracts/alerts/copy.json`, English and Spanish
/// (`l`: the language the app speaks, `AppLanguage.current`; the log passes `.en`). Durations only through `Durations`.
/// Lines are sentence case; Mac menu items are title-style. The card's pill ("Muted · 1h 12m") is the renderer's
/// (`CardWords`).
public enum MuteCopy {
    /// The side's name ("low" covers very low, "high" very high and extremely high), short and plural (the owner,
    /// 2026-11-14: "'Low alerts muted' can simply be 'Lows muted'"): "Lows" / "Bajas"; Spanish plural nouns, so
    /// "silenciadas" agrees ("Alertas sin datos").
    public static func sideName(_ side: AlertSide, _ l: AppLanguage = .current) -> String {
        switch (side, l) {
        case (.low, .en): "Lows"
        case (.high, .en): "Highs"
        case (.fallingFast, .en): "Falling fast"
        case (.risingFast, .en): "Rising fast"
        case (.noData, .en): "No data"
        case (.low, .es): "Bajas"
        case (.high, .es): "Altas"
        case (.fallingFast, .es): "Bajadas rápidas"
        case (.risingFast, .es): "Subidas rápidas"
        case (.noData, .es): "Alertas sin datos"
        }
    }

    /// "12 min left" / "quedan 12 min" ("queda" for a single unit of 1: "queda 1 h").
    private static func left(_ min: Int, _ l: AppLanguage) -> String {
        let d = Durations.text(min)
        guard l == .es else { return "\(d) left" }
        let m = max(0, min)
        return ((m >= 60 && m % 60 == 0 ? m / 60 : m) == 1 ? "queda " : "quedan ") + d
    }

    /// "Lows muted" / "Bajas silenciadas".
    private static func sideAlertsMuted(_ side: AlertSide, _ l: AppLanguage) -> String {
        l == .en ? "\(sideName(side, l)) muted" : "\(sideName(side, l)) silenciadas"
    }

    /// "Lows muted · 12 min left".
    public static func sideMuted(_ side: AlertSide, leftMin: Int, _ l: AppLanguage = .current) -> String {
        "\(sideAlertsMuted(side, l)) · \(left(leftMin, l))"
    }

    /// "Lows muted after carbs · 12 min left", "Highs muted after insulin · 1 h left".
    public static func sideAfter(
        _ side: AlertSide, treatment: TreatmentKind, leftMin: Int, _ l: AppLanguage = .current
    ) -> String {
        let after =
            switch l {
            case .en: treatment == .carbs ? "after carbs" : "after insulin"
            case .es: treatment == .carbs ? "tras los hidratos" : "tras la insulina"
            }
        return "\(sideAlertsMuted(side, l)) \(after) · \(left(leftMin, l))"
    }

    /// "Highs muted while falling back into range" (no end of its own).
    public static func sideFalling(_ side: AlertSide, _ l: AppLanguage = .current) -> String {
        "\(sideAlertsMuted(side, l)) " + (l == .en ? "while falling back into range" : "mientras baja hacia el rango")
    }

    /// "Lows muted while rising back into range" (#193; no end of its own).
    public static func sideRising(_ side: AlertSide, _ l: AppLanguage = .current) -> String {
        "\(sideAlertsMuted(side, l)) " + (l == .en ? "while rising back into range" : "mientras sube hacia el rango")
    }

    /// "Lows: remind every 30 min".
    public static func sideLess(_ side: AlertSide, intervalMin: Int, _ l: AppLanguage = .current) -> String {
        l == .en
            ? "\(sideName(side, l)): remind every \(Durations.text(intervalMin))"
            : "\(sideName(side, l)): avisar cada \(Durations.text(intervalMin))"
    }

    /// Mute all for a time: "Alerts muted · 1 h 12 min left".
    public static func allTimed(leftMin: Int, _ l: AppLanguage = .current) -> String {
        (l == .en ? "Alerts muted · " : "Alertas silenciadas · ") + left(leftMin, l)
    }
    public static func allForever(_ l: AppLanguage = .current) -> String {
        l == .en ? "Alerts muted until you turn it off" : "Alertas silenciadas hasta que lo desactives"
    }

    /// The silent notification while a side is muted (Mac notifications can't count down: they say the end).
    public static func notificationTitle(_ side: AlertSide, _ l: AppLanguage = .current) -> String {
        sideAlertsMuted(side, l)
    }
    public static func notificationText(until: String, _ l: AppLanguage = .current) -> String {
        l == .en ? "Until \(until)" : "Hasta las \(until)"
    }

    /// While the night alerts apply (#38): "Night alerts until 7:00" (`until`: the clock time they end).
    public static func nightUntil(until: String, _ l: AppLanguage = .current) -> String {
        l == .en ? "Night alerts until \(until)" : "Alertas de noche hasta las \(until)"
    }

    /// A Mute choice: "For 30 min", "For 1 h".
    public static func choice(_ minutes: Int, _ l: AppLanguage = .current) -> String {
        (l == .en ? "For " : "Durante ") + Durations.text(minutes)
    }
    public static func choiceForever(_ l: AppLanguage = .current) -> String {
        l == .en ? "Until you turn it off" : "Hasta que lo desactives"
    }
    /// The menu item (title case in English).
    public static func menuForever(_ l: AppLanguage = .current) -> String {
        l == .en ? "Until You Turn It Off" : "Hasta que lo desactives"
    }

    /// The line for Mute all ending at `until` (`muteAllForever` = indefinite), nil once it is over.
    public static func allLine(until: Date, now: Date, _ l: AppLanguage = .current) -> String? {
        guard until > now else { return nil }
        return until >= muteAllForever
            ? allForever(l) : allTimed(leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), l)
    }

    /// The line for a quiet reason on `side`, counting down at `now`; nil once a timed one is over (never claims a
    /// quiet that has ended).
    public static func line(
        _ reason: QuietReason, side: AlertSide, now: Date, _ l: AppLanguage = .current
    ) -> String? {
        switch reason {
        case .treatment(_, _, let until) where until <= now: nil
        case .sideMuted(let until) where until <= now: nil
        case .treatment(let kind, _, let until):
            sideAfter(side, treatment: kind, leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), l)
        case .fallingBack: sideFalling(side, l)
        case .risingBack: sideRising(side, l)
        case .sideMuted(let until): sideMuted(side, leftMin: Durations.minutesLeft(until.timeIntervalSince(now)), l)
        case .allMuted(let until): allLine(until: until, now: now, l)
        }
    }
}
