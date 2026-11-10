// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// The words VoiceOver says for a card, English and Spanish (Android: core/graph `values*/strings.xml`, the `a11y_*`,
/// `trend_*` and `card_range_*` strings, same wording; contracts/graph/spoken holds the sentences both say). Kept in
/// code, not a String Catalog: the widget extension links this module and a SwiftPM resource bundle isn't packaged
/// into the appex.
public struct CardWords: Sendable {
    /// The language: a sentence's capital letter, the decimal separator.
    let locale: Locale
    let unit: (mgdl: String, mmol: String)
    let below, above: @Sendable (String) -> String
    let old, inRange, justNow: String
    let ago: @Sendable (String) -> String
    let trend:
        (unknown: String, steady: String, rising: String, risingFast: String, falling: String, fallingFast: String)
    /// The range words the card draws (`RangeState`), in the sentence's lower case.
    let range: (veryLow: String, low: String, high: String, veryHigh: String, extremelyHigh: String)
    let noReadings: String
    let noReadingSince: @Sendable (String) -> String
    /// A card without a value: when its data is from (the graph widgets, `Freshness.AgeMark`).
    let lastReading: @Sendable (String) -> String
    /// The time left in a sentence: `one` = it is a single unit ("queda 1 h", "quedan 1 h 30 min").
    let warmingUp: @Sendable (_ left: String, _ one: Bool) -> String
    let almostReady, sensorEnded: String
    let sensorEnds: @Sendable (_ left: String) -> String
    let days, hours: @Sendable (Int) -> String
    /// The status pill's sentence: the action an alert asks for, and the mutes with the time left.
    let eatCarbs, takeInsulin, mutedForever: String
    let mutedLeft: @Sendable (_ left: String, _ one: Bool) -> String
    let sideMuted: @Sendable (_ low: Bool, _ left: String, _ one: Bool) -> String
    let sideForever: @Sendable (_ low: Bool) -> String
    let lowAfterCarbs, highAfterInsulin: @Sendable (_ left: String, _ one: Bool) -> String
    /// A slot: the number it draws and its window.
    let slotInRange, slotVeryGood, slotGmi, slotAverage,
        slotCv: @Sendable (_ number: String, _ period: String) -> String
    let lessThan, moreThan: @Sendable (String) -> String
    /// An amount with its unit, singular for exactly one.
    let units, grams: @Sendable (_ number: String, _ one: Bool) -> String
    let lastRapid, lastLong, lastCarbs: @Sendable (_ amount: String, _ age: String) -> String
    let lastNote: @Sendable (_ age: String) -> String
    let chart: @Sendable (_ hours: Int, _ inRange: Int, _ below: Int, _ above: Int) -> String
    let chartEmpty: @Sendable (_ hours: Int) -> String

    public static let en = CardWords(
        locale: Locale(identifier: "en"), unit: ("milligrams per decilitre", "millimoles per litre"),
        below: { "below \($0)" }, above: { "above \($0)" }, old: "old reading", inRange: "in range",
        justNow: "just now", ago: { "\($0) ago" },
        trend: ("trend unknown", "steady", "rising", "rising fast", "falling", "falling fast"),
        range: ("very low", "low", "high", "very high", "extremely high"),
        noReadings: "Glucose: no readings yet", noReadingSince: { "Glucose: no reading since \($0)" },
        lastReading: { "Glucose: last reading \($0)" },
        warmingUp: { left, _ in "Sensor warming up, \(left) left" },
        almostReady: "Sensor almost ready, waiting for the first reading", sensorEnded: "Sensor ended. Replace sensor",
        sensorEnds: { "Sensor ends in \($0)" }, days: { $0 == 1 ? "1 day" : "\($0) days" },
        hours: { $0 == 1 ? "1 hour" : "\($0) hours" },
        eatCarbs: "Eat carbs", takeInsulin: "Take insulin", mutedForever: "Alerts muted until you turn it off",
        mutedLeft: { left, _ in "Alerts muted, \(left) left" },
        sideMuted: { low, left, _ in "\(low ? "Low" : "High") alerts muted, \(left) left" },
        sideForever: { "\($0 ? "Low" : "High") alerts muted until you turn it off" },
        lowAfterCarbs: { left, _ in "Low alerts muted after carbs, \(left) left" },
        highAfterInsulin: { left, _ in "High alerts muted after insulin, \(left) left" },
        slotInRange: { "\($0)% in range over \($1)" }, slotVeryGood: { "\($0)% very good over \($1)" },
        slotGmi: { "GMI \($0)% over \($1)" }, slotAverage: { "Average \($0) over \($1)" },
        slotCv: { "Variability \($0)% over \($1)" },
        lessThan: { "less than \($0)" }, moreThan: { "more than \($0)" },
        units: { "\($0) \($1 ? "unit" : "units")" }, grams: { "\($0) \($1 ? "gram" : "grams")" },
        lastRapid: { "Rapid insulin \($0), \($1)" }, lastLong: { "Long-acting insulin \($0), \($1)" },
        lastCarbs: { "Carbs \($0), \($1)" }, lastNote: { "Note, \($0)" },
        chart: { "Last \($0) hours: \($1)% in range, \($2)% below, \($3)% above" },
        chartEmpty: { "Last \($0) hours: no data" })

    public static let es = CardWords(
        locale: Locale(identifier: "es"), unit: ("miligramos por decilitro", "milimoles por litro"),
        below: { "por debajo de \($0)" }, above: { "por encima de \($0)" }, old: "lectura antigua",
        inRange: "en rango", justNow: "ahora mismo", ago: { "hace \($0)" },
        trend: ("tendencia desconocida", "estable", "subiendo", "subiendo rápido", "bajando", "bajando rápido"),
        range: ("muy baja", "baja", "alta", "muy alta", "extremadamente alta"),
        noReadings: "Glucosa: aún no hay lecturas", noReadingSince: { "Glucosa: sin lecturas desde \($0)" },
        lastReading: { "Glucosa: última lectura \($0)" },
        warmingUp: { "Sensor calentando, \(queda($1)) \($0)" },
        almostReady: "Sensor casi listo, esperando la primera lectura", sensorEnded: "Sensor terminado. Cambiar sensor",
        sensorEnds: { "El sensor termina en \($0)" }, days: { $0 == 1 ? "1 día" : "\($0) días" },
        hours: { $0 == 1 ? "1 hora" : "\($0) horas" },
        eatCarbs: "Come hidratos", takeInsulin: "Ponte insulina",
        mutedForever: "Alertas silenciadas hasta que lo desactives",
        mutedLeft: { "Alertas silenciadas, \(queda($1)) \($0)" },
        sideMuted: { "Alertas de glucosa \($0 ? "baja" : "alta") silenciadas, \(queda($2)) \($1)" },
        sideForever: { "Alertas de glucosa \($0 ? "baja" : "alta") silenciadas hasta que lo desactives" },
        lowAfterCarbs: { "Alertas de glucosa baja silenciadas tras los hidratos, \(queda($1)) \($0)" },
        highAfterInsulin: { "Alertas de glucosa alta silenciadas tras la insulina, \(queda($1)) \($0)" },
        slotInRange: { "\($0)% en rango durante \($1)" }, slotVeryGood: { "\($0)% muy bien durante \($1)" },
        slotGmi: { "GMI \($0)% durante \($1)" }, slotAverage: { "Media \($0) durante \($1)" },
        slotCv: { "Variabilidad \($0)% durante \($1)" },
        lessThan: { "menos de \($0)" }, moreThan: { "más de \($0)" },
        units: { "\($0) \($1 ? "unidad" : "unidades")" }, grams: { "\($0) \($1 ? "gramo" : "gramos")" },
        lastRapid: { "Insulina rápida \($0), \($1)" }, lastLong: { "Insulina lenta \($0), \($1)" },
        lastCarbs: { "Hidratos \($0), \($1)" }, lastNote: { "Nota, \($0)" },
        chart: { "Últimas \($0) horas: \($1)% en rango, \($2)% por debajo, \($3)% por encima" },
        chartEmpty: { "Últimas \($0) horas: sin datos" })

    private static func queda(_ one: Bool) -> String { one ? "queda" : "quedan" }

    /// The user's language among the two the app speaks (the system's preferred order; English otherwise).
    public static var current: CardWords {
        Bundle.preferredLocalizations(from: ["en", "es"]).first == "es" ? .es : .en
    }

    func unit(_ u: GlucoseUnit) -> String { u == .mgdl ? unit.mgdl : unit.mmol }
}

extension GlucoseUnit {
    /// A value as the card writes it: in the card's language (`CardWords.current`), "7.8" / "7,8".
    public func cardText(_ mgdl: Int) -> String { format(mgdl, locale: CardWords.current.locale) }
}

extension CardWords {

    func trend(_ rate: Double?) -> String {
        guard let rate else { return trend.unknown }
        return switch Trend(rate: rate) {
        case .risingFast: trend.risingFast
        case .rising: trend.rising
        case .steady: trend.steady
        case .falling: trend.falling
        case .fallingFast: trend.fallingFast
        }
    }

    /// The range in words (never by colour alone): the card's range word (`RangeState`), "in range" inside it.
    func range(_ sgv: Int, _ b: Bands) -> String {
        switch RangeState.of(sgv, b) {
        case nil: inRange
        case .veryLow: range.veryLow
        case .low: range.low
        case .high: range.high
        case .veryHigh: range.veryHigh
        case .extremelyHigh: range.extremelyHigh
        }
    }

    /// "just now" under a minute, else "2 min ago" / "1 h 5 min ago".
    func age(_ s: TimeInterval) -> String { s < 60 ? justNow : ago(Durations.text(Int(s / 60))) }

    /// A number the card drew ("6.7") with this language's decimal separator, so it is read as one number.
    func decimal(_ number: String) -> String {
        let sep = locale.decimalSeparator ?? "."
        return number.replacingOccurrences(of: ".", with: sep).replacingOccurrences(of: ",", with: sep)
    }

    /// `s` as a sentence: its first letter a capital.
    func sentence(_ s: String) -> String { s.prefix(1).uppercased(with: locale) + s.dropFirst() }
}

/// THE sentence VoiceOver says for the glucose card (#25; Android: graph/A11y.kt, the same sentence, both checked by
/// contracts/graph/spoken), on every surface that shows the card or its value: the menu bar title and panel and the
/// widgets. Most important first: the value with its unit (once), "old reading" when struck, the trend, the range in
/// words (never by colour alone), the age; the alert or mute the pill shows; the slots; the sensor's time left where
/// the card shows it; the chart summary. No symbols read as punctuation (arrows, "·", "<"). Built from the decisions
/// the card draws with (`GlucoseCard.face`, `StatusBadge.look`, `SensorBadge.look`, `GlucoseCard.slotItems`).
enum A11y {
    private static let maxSlots = 4

    /// "112 milligrams per decilitre, falling, in range, 2 min ago" (or why there's no value), then the alert or mute
    /// the status pill says when a value is shown and the place draws the badge.
    static func value(_ data: CardData, unit: GlucoseUnit, surface: CardSurface, style: CardStyle, words w: CardWords)
        -> String
    {
        let look = GlucoseCard.face(data, unit: unit, surface: surface)
        let glucose: String
        switch look.state {
        case .ended, .warmingUp: glucose = sensorState(data, words: w)
        case .none: glucose = w.noReadings
        case .noReadings:
            glucose = w.noReadingSince(data.latest.map { Freshness.timeText($0.t, now: data.now) } ?? "")
        case .value:
            guard let r = data.latest else { return w.noReadings }
            let u = w.unit(unit)
            let bound = r.sgv < GlucoseRange.floor || r.sgv >= GlucoseRange.hi
            let number =
                switch r.sgv {
                case ..<GlucoseRange.floor: w.below("\(unit.format(GlucoseRange.floor, locale: w.locale)) \(u)")
                case GlucoseRange.hi...: w.above("\(unit.format(GlucoseRange.ceiling, locale: w.locale)) \(u)")
                default: "\(unit.format(r.sgv, locale: w.locale)) \(u)"
                }
            let parts = [
                number, look.strike ? w.old : nil, look.strike ? nil : w.trend(look.trend == .arrow ? look.rate : nil),
                bound ? nil : w.range(r.sgv, style.bands.sane()), w.age(data.now - r.t),
            ]
            glucose = parts.compactMap { $0 }.joined(separator: ", ")
        }
        let alert =
            style.badge && look.state == .value ? status(data.alerts.status, now: data.now, words: w) : nil
        return sentences([glucose, alert].compactMap { $0 }, w)
    }

    /// "Last 12 hours: 82% in range, 3% below, 15% above" (or "no data"): the window the chart shows.
    static func chart(_ data: CardData, _ g: GraphStyle, words w: CardWords) -> String {
        let hours = Int(g.window / 3600)
        let tir = GlucoseStats.timeInRange(
            data.readings, from: data.now - g.window, to: data.now, bands: g.bands.sane())
        if tir.empty { return w.chartEmpty(hours) }
        let (below, inRange, above) = GlucoseGraph.pct3(tir.below, tir.inRange, tir.above)
        return w.chart(hours, inRange, below, above)
    }

    /// The whole card: the glucose sentence (a card without a value says how old its chart is, as its pill does), the
    /// slots, the sensor's time left where the card shows it (in the chart's hour row or as the countdown ring over the
    /// main slot), then the chart summary. A glyph surface (the menu bar title) says the glucose sentence only.
    static func card(_ data: CardData, unit: GlucoseUnit, surface: CardSurface, style: CardStyle, words w: CardWords)
        -> String
    {
        let first: String? =
            style.value || surface.glyph
            ? value(data, unit: unit, surface: surface, style: style, words: w)
            : age(data, surface: surface, words: w)
        guard !surface.glyph else { return first ?? "" }
        let slots = style.value ? GlucoseCard.slotItems(data, style, max: maxSlots) : []
        let spoken = slots.compactMap { slot($0, now: data.now, unit: unit, words: w) }
        let now = data.now
        let fr = Freshness.of(age: data.latest.map { now - $0.t })
        let life = SensorBadge.look(style.sensor, now: now, latest: data.latest?.t).flatMap { s in
            GlucoseCard.sensorSpot(style, s, fr) == .graph
                || (s.warmup == nil && !s.ended && !slots.isEmpty
                    && SensorLife.countdownFraction(end: s.end, now: now, hours: style.countdownHours) != nil)
                ? sensorLeft(s.end - now, words: w) : nil
        }
        let chart = style.graph.map { chart(data, $0, words: w) }
        return sentences([first].compactMap { $0 } + spoken + [life, chart].compactMap { $0 }, w)
    }

    /// A card without a value says how old its chart is, as the picture does (`Freshness.AgeMark`): "last reading
    /// 9:50" at every age, "old reading" too from 5 min, "no reading since 9:50" from 20 min (and once the chart is
    /// gone), "no readings yet" without any; a sensor that ended or is warming up says so, as the value's place does.
    static func age(_ data: CardData, surface: CardSurface, words w: CardWords) -> String? {
        switch GlucoseCard.face(data, unit: .mgdl, surface: surface).state {
        case .ended, .warmingUp: return sensorState(data, words: w)
        default: break
        }
        guard let t = data.latest?.t else { return w.noReadings }
        let time = Freshness.timeText(t, now: data.now)
        switch Freshness.of(age: data.now - t).ageMark {
        case .none: return nil
        case .time: return w.lastReading(time)
        case .dimmed: return "\(w.lastReading(time)), \(w.old)"
        case .noReadings, .face: return w.noReadingSince(time)
        }
    }

    /// What the status pill says, in full words: the action an alert asks for, or the mute and its time left. Nothing
    /// when the card draws no pill (`StatusBadge.look`: none, or a mute that has ended).
    static func status(_ s: AlertStatus, now: TimeInterval, words w: CardWords) -> String? {
        guard StatusBadge.look(s, now: now) != nil else { return nil }
        switch s.current(at: now) {
        case .none: return nil
        case .needed(let k): return k == .low ? w.eatCarbs : w.takeInsulin
        case .taken(_, let tr, let until):
            let (l, one) = left(until - now)
            return tr.k == "carbs" ? w.lowAfterCarbs(l, one) : w.highAfterInsulin(l, one)
        case .paused(let k, let until):
            let (l, one) = left(until - now)
            return until >= AlertStatus.never ? w.sideForever(k == .low) : w.sideMuted(k == .low, l, one)
        case .muted(let until):
            let (l, one) = left(until - now)
            return until >= AlertStatus.never ? w.mutedForever : w.mutedLeft(l, one)
        }
    }

    /// A drawn slot in words (`metric`, `last`); nil for a ring without data (the card draws none).
    private static func slot(_ item: SlotItem, now: TimeInterval, unit: GlucoseUnit, words w: CardWords) -> String? {
        switch item {
        case let .metric(spec, m, _):
            guard !m.empty else { return nil }
            let text =
                spec.kind == .tir
                ? TirRing.text(m.tir, spec) : MetricGauge.text(spec.kind, m, unit: unit, locale: w.locale).0
            return metric(spec.kind, spec.range, window: spec.window, text: text, words: w)
        case let .last(info):
            return last(info, now: now, words: w)
        }
    }

    /// A report slot by the number it draws (`text`: "86", "6.7", "<0.1"): "86% in range over 1 day", "GMI 6.7% over
    /// 14 days", "Average 141 over 1 day" (the unit is said once, by the value), "Variability 37% over 1 day".
    static func metric(_ kind: SlotKind, _ range: TirRange, window: TimeInterval, text: String, words w: CardWords)
        -> String
    {
        let number =
            text.hasPrefix("<")
            ? w.lessThan(w.decimal(String(text.dropFirst())))
            : text.hasPrefix(">") ? w.moreThan(w.decimal(String(text.dropFirst()))) : w.decimal(text)
        let s = Int(window)
        let period = s % 86_400 == 0 ? w.days(s / 86_400) : w.hours(s / 3_600)
        let phrase =
            switch kind {
            case .tir: range == .tight ? w.slotVeryGood(number, period) : w.slotInRange(number, period)
            case .gmi: w.slotGmi(number, period)
            case .average: w.slotAverage(number, period)
            case .cv: w.slotCv(number, period)
            }
        return w.sentence(phrase)
    }

    /// The last-treatment slot: "Rapid insulin 3 units, 10 min ago", then the carbs drawn inside it.
    static func last(_ info: LastTreatment.Info, now: TimeInterval, words w: CardWords) -> String {
        ([info.tr] + [info.carbs?.tr].compactMap { $0 })
            .map { treatment($0.k, amount: $0.a, age: now - $0.t, words: w) }
            .joined(separator: ". ")
    }

    /// One treatment: its kind, amount and age ("Carbs 45 grams, just now", "Note, 10 min ago").
    static func treatment(_ k: String, amount: Double, age: TimeInterval, words w: CardWords) -> String {
        let number = w.decimal(TreatmentMarks.number(amount))
        let ago = w.age(age)
        switch k {
        case "rapid": return w.lastRapid(w.units(number, amount == 1), ago)
        case "long": return w.lastLong(w.units(number, amount == 1), ago)
        case "carbs": return w.lastCarbs(w.grams(number, amount == 1), ago)
        default: return w.lastNote(ago)
        }
    }

    /// "Sensor ends in 13 days" (whole days from 24 h, as the badge), "… in 5 h", "… in 45 min".
    private static func sensorLeft(_ s: TimeInterval, words w: CardWords) -> String {
        let min = Int(max(0, s)) / 60
        return w.sensorEnds(min >= 1440 ? w.days(min / 1440) : Durations.text(min))
    }

    /// The sensor's own states where the value goes: warming up (its time left) or ended.
    private static func sensorState(_ data: CardData, words w: CardWords) -> String {
        guard let s = SensorBadge.look(data.sensor, now: data.now, latest: data.latest?.t), !s.ended else {
            return w.sensorEnded
        }
        if s.waiting(now: data.now) { return w.almostReady }
        let (l, one) = left(s.readyAt - data.now)
        return w.warmingUp(l, one)
    }

    /// The time left of `s` seconds, partial minutes rounded up ("1 min" until it is over), and whether it is a single
    /// unit, for the plural it agrees with ("queda 1 h" / "quedan 1 h 30 min"; Android's `Durations.textQuantity`).
    private static func left(_ s: TimeInterval) -> (String, Bool) {
        let m = BadgeCopy.leftMin(s)
        return (Durations.text(m), (m >= 60 && m % 60 == 0 ? m / 60 : m) == 1)
    }

    private static func sentences(_ parts: [String], _ w: CardWords) -> String {
        parts.map(w.sentence).joined(separator: ". ")
    }
}
