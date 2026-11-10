// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CoreGraphics
import CoreText
import Foundation
import SukraraCore

/// How old the latest reading is decides what a surface shows (see Freshness.kt for the table).
public enum Freshness: Sendable {
    case fresh, late, stale, old, gone
    static let lateS: TimeInterval = 90, staleS: TimeInterval = 5 * 60, oldS: TimeInterval = 20 * 60,
        goneS: TimeInterval = 3 * 3600
    /// From 12 h `timeText` names the day too (a bare clock time could be today's or yesterday's); after 6 days the date.
    static let dayS: TimeInterval = 12 * 3600, dateS: TimeInterval = 6 * 86400

    static func of(age: TimeInterval?) -> Freshness {
        guard let a = age else { return .gone }
        if a <= lateS { return .fresh }
        if a <= staleS { return .late }
        if a <= oldS { return .stale }
        if a <= goneS { return .old }
        return .gone
    }
    var showValue: Bool { self == .fresh || self == .late || self == .stale }
    var showTime: Bool { self != .fresh }
    var strike: Bool { self == .stale }
    var showGraph: Bool { self != .gone }
    /// A card's value place says "now" where the time goes: a fresh value reads as live (#70; contracts/graph/freshness
    /// `now`). Text-only places don't.
    var saysNow: Bool { self == .fresh }

    /// What a card WITHOUT a value (graph-only) shows of its data's age, so it never looks current when it isn't
    /// (contracts/graph/freshness `graph_only`; Freshness.kt AgeMark): the reading's time at every age, fresh too (a
    /// widget that stops redrawing still says when its data is from), then the value's strike and "no readings" steps.
    /// `none` = no label: only a card WITH a value.
    enum AgeMark: String, Sendable {
        case none, time, dimmed, noReadings = "no_readings", face
        /// The chart's opacity while dimmed.
        static let dimAlpha: CGFloat = 0.4
        var dims: Bool { self == .dimmed || self == .noReadings }
    }

    var ageMark: AgeMark {
        switch self {
        case .fresh, .late: .time
        case .stale: .dimmed
        case .old: .noReadings
        case .gone: .face
        }
    }

    /// The next age at which `of` or `timeText` changes (for a one-shot timer / timeline entry): else a widget last
    /// drawn at 3 h still says "No reading since 7:14" the next morning.
    static func nextChange(age: TimeInterval) -> TimeInterval? {
        [lateS, staleS, oldS, goneS, dayS, dateS].first { $0 >= age }.map { $0 + 1 }
    }

    /// When what a reading taken at `t` looks like changes next, seen at `now`; nil = never again / no reading.
    public static func nextChangeAt(_ t: TimeInterval?, now: TimeInterval) -> TimeInterval? {
        guard let t else { return nil }
        return nextChange(age: now - t).map { t + $0 }
    }

    /// What every glucose surface shows for the latest reading (`sgv` at `t`, `rate` mg/dL/min) at `now`: the one
    /// decision behind the card (menubar dropdown, widgets) and the menubar title. Android's `Freshness.look`
    /// (graph/Freshness.kt) is the same rule; both run spikes/graph-fixtures/freshness. `ended` = the sensor has ended,
    /// `warmup` = a new sensor's warm-up time left: their own states, before any value. Surfaces ask `GlucoseCard.face`.
    /// `format` writes a value in the user's unit (mg/dL by default); LO / HI outside 40–400 mg/dL.
    static func look(
        sgv: Int?, t: TimeInterval?, rate: Double?, now: TimeInterval, ended: Bool = false, warmup: String? = nil,
        format: (Int) -> String = { "\($0)" }
    ) -> GlucoseLook {
        let fr = of(age: t.map { now - $0 })
        if let warmup {
            return GlucoseLook(state: .warmingUp, fresh: fr, text: nil, strike: false, trend: nil, warmup: warmup)
        }
        if ended { return GlucoseLook(state: .ended, fresh: fr, text: nil, strike: false, trend: nil) }
        guard let sgv, t != nil else {
            return GlucoseLook(state: .none, fresh: fr, text: nil, strike: false, trend: nil)
        }
        guard fr.showValue else {
            return GlucoseLook(state: .noReadings, fresh: fr, text: nil, strike: false, trend: nil)
        }
        return GlucoseLook(
            state: .value, fresh: fr, text: GlucoseRange.isLo(sgv) ? "LO" : GlucoseRange.isHi(sgv) ? "HI" : format(sgv),
            strike: fr.strike,
            trend: rate != nil && !fr.strike ? TrendMark.arrow : TrendMark.noTrend, rate: rate)
    }

    /// "14:05" today and under 12 h old, "Tue 14:05" from 12 h or on another day, "12 Oct" from 6 days (`timeKind`);
    /// the time in the Mac's 12/24-hour setting (`Clock`). A clock time doesn't tick.
    public static func timeText(_ t: TimeInterval, now: TimeInterval) -> String {
        let d = Date(timeIntervalSince1970: t)
        let time = Clock.time(d)
        switch timeKind(t, now: now) {
        case .time: return time
        case .day: return d.formatted(.dateTime.weekday(.abbreviated)) + " " + time
        case .date: return d.formatted(.dateTime.day().month(.abbreviated))
        }
    }

    /// `timeText`'s three forms ("14:05", "Tue 14:05", "12 Oct"), by their contract names (contracts/graph/freshness
    /// `times`; Freshness.kt TimeKind).
    enum TimeKind: String, Sendable { case time, day, date }

    /// Which form `timeText` writes for `t` seen at `now`: the clock time alone while it is today's and at most 12 h
    /// old, the day with it from then or on another day, the date after 6 days. A future time (a sensor's *Ready at*)
    /// is a clock time unless it falls on another day.
    static func timeKind(_ t: TimeInterval, now: TimeInterval, calendar: Calendar = .current) -> TimeKind {
        let sameDay = calendar.isDate(
            Date(timeIntervalSince1970: t), inSameDayAs: Date(timeIntervalSince1970: now))
        let age = now - t
        if sameDay && age <= dayS { return .time }
        return age <= dateS ? .day : .date
    }
}

/// The trend beside a value: the arrow, or `.noTrend` = no trend to show (no rate, or the value is struck through): a
/// hollow ring of the arrow's size (graph/Freshness.kt TrendMark: a circle has no direction, so it can't read as steady,
/// and unlike the old dot it doesn't look like a speck). Text places write it as `ring`.
public enum TrendMark: String, Sendable {
    case arrow, noTrend = "none"
    static let ring = "○"
    static func ringRadius(_ size: CGFloat) -> CGFloat { size * 0.24 }
    static func ringStroke(_ size: CGFloat) -> CGFloat { size * 0.11 }
}

/// The trend arrow's one decision (graph/CardModel.kt TrendAngle; contracts/graph/trend), one picture and one word per
/// arrow (#4): the rate's band (`Trend`, Nightscout's ±1 / 2 / 3) gives the drawn arrow's angle, snapped to 0°, ±45°
/// or ±90° (doubled from ±3), and THE text arrow every text place writes (the menu bar, alert titles). No place maps a
/// rate to an arrow itself.
public enum TrendAngle {
    /// The drawn arrow's rotation in degrees, y-down (negative = up): the band's 0, ±45 or ±90.
    public static func deg(_ rate: Double) -> Double { degrees[Trend(rate: rate).step + 3] }

    public static func isDouble(_ rate: Double) -> Bool { abs(Trend(rate: rate).step) == 3 }

    /// The text arrow for a rate.
    public static func symbol(_ rate: Double) -> String { symbols[Trend(rate: rate).step + 3] }

    private static let degrees: [Double] = [90, 90, 45, 0, -45, -90, -90]
    private static let symbols = ["⇊", "↓", "↘", "→", "↗", "↑", "⇈"]

    /// `symbol` for a rate that may be missing: no rate = the "no trend" ring, as the card draws it.
    public static func text(_ rate: Double?) -> String { rate.map(symbol) ?? TrendMark.ring }
}

/// `Freshness.look`'s answer (graph/Freshness.kt GlucoseLook): the state; for a value its text, whether it's struck
/// through and grey (5–20 min) and its trend (nil without a value).
public struct GlucoseLook: Equatable, Sendable {
    public enum State: String, Sendable {
        case value, noReadings = "no_readings", none, ended, warmingUp = "warming_up"
        /// The name the layout report uses (Android's enum names).
        var layoutName: String { rawValue.uppercased() }
    }
    public var state: State
    public var fresh: Freshness
    public var text: String?
    public var strike: Bool
    public var trend: TrendMark?
    /// While warming up: the time left ("42m").
    public var warmup: String? = nil
    public var rate: Double? = nil
    public var grey: Bool { strike }
    public var shown: Bool { state == .value }
    /// The trend as text for places that can only write text (the menu bar): the arrow of `rate` when the card draws
    /// one, the ring when it draws the "no trend" ring, nil without a value (graph/Freshness.kt `arrowText`).
    public var arrowText: String? {
        switch trend {
        case .arrow: TrendAngle.text(rate)
        case .noTrend: TrendMark.ring
        case nil: nil
        }
    }
    /// SF Symbol for "no readings for 20 min" (Android: Material Symbols sensors_off): the menubar and the card.
    static let noReadingsSymbol = "antenna.radiowaves.left.and.right.slash"
}
