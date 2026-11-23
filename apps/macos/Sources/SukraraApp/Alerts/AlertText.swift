// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph

/// The words of an alert notification in the user's language (English, Spanish): every Mac case of
/// `contracts/alerts/copy.json`, the words Android writes too (`AlertCopyTests`; the state lines are `MuteCopy`'s).
/// Self-contained: the value, the direction and what it means, readable on the lock screen without opening the app.
struct AlertText: Equatable {
    typealias Language = AppLanguage

    let language: Language
    /// The unit every number is written in (`general.unit`; contracts/settings/units.json).
    var unit: GlucoseUnit = .mgdl

    /// The first preferred language Sukrara speaks, else English.
    static func preferred(_ languages: [String] = Locale.preferredLanguages) -> AlertText {
        let spoken = languages.lazy
            .compactMap { Locale(identifier: $0).language.languageCode?.identifier }
            .compactMap(Language.init(rawValue:))
            .first
        return AlertText(language: spoken ?? .en)
    }

    /// Why no data comes, when the source knows it and it isn't an error (contracts/alerts/copy.json `why`): LibreView's
    /// maintenance, `ends` its end as the clock writes it (nil = none known); a follower reading another version than
    /// the wearer's (docs/48), `thisApp` = this Mac updates (*Update*), else the wearer's. The alert still rings on time.
    enum Why: Equatable, Sendable {
        case maintenance(ends: String?)
        case update(thisApp: Bool)
    }

    /// The notification title: the headline only (the same words on every platform, contracts/alerts/SPEC.md §1).
    func title(_ f: AlertFiring) -> String { headline(f) }

    /// The title with why no data comes: "No data · LibreView maintenance", "No data · update Sukrara".
    func title(_ f: AlertFiring, escalated: Bool, why: Why?) -> String {
        guard f.kind == .noData, let why else { return title(f, escalated: escalated) }
        let en = language == .en
        let context =
            switch why {
            case .maintenance: en ? "LibreView maintenance" : "mantenimiento de LibreView"
            case .update(thisApp: true): en ? "update Sukrara" : "actualiza Sukrara"
            case .update(thisApp: false):
                en ? "their Sukrara needs an update" : "su Sukrara necesita actualizarse"
            }
        return headline(f) + " · " + context
    }

    /// The body with why no data comes: the maintenance's end first when known ("Until 23:00 · last reading 22:04 ·
    /// 72 mg/dL"), else the usual body.
    func body(_ f: AlertFiring, now: Date, why: Why?) -> String {
        guard f.kind == .noData, case .maintenance(let ends?) = why, let r = f.reading else {
            return body(f, now: now)
        }
        let at = Freshness.timeText(r.at.timeIntervalSince1970, now: now.timeIntervalSince1970)
        return language == .en
            ? "Until \(ends) · last reading \(at) · \(value(r))"
            : "Hasta las \(ends) · última lectura \(at) · \(value(r))"
    }

    /// A follower's title for the wearer's very low nobody answered on the wearer's phone (#39).
    func title(_ f: AlertFiring, escalated: Bool) -> String {
        guard escalated else { return headline(f) }
        return headline(f) + (language == .en ? " · no response" : " · sin respuesta")
    }

    /// The body (registered glyph surface `alert-notification`, contracts/graph/surfaces): the value and arrow
    /// (`69 mg/dL ↘`), with the rate for falling / rising fast (`· falling 2.4 mg/dL/min`, `· falling 0.13
    /// mmol/L/min`), and for no data when the last reading came and its value. The reading's look at `now` is the card's
    /// (`GlucoseCard.face`): once late its time follows (`69 mg/dL ↘ · 14:05`); from 5 min the ring replaces the arrow
    /// and the rate goes (a notification can't strike text through; Android does). Never the threshold, never a made-up
    /// number: the sensor's LO / HI as such (not its stored 39 / 401), and without a reading it says so.
    func body(_ f: AlertFiring, now: Date) -> String {
        let en = language == .en
        if f.kind == .noData {
            // a source that never delivered a reading: nothing to count from but its start, nothing to show
            guard let r = f.reading else { return en ? "No reading yet" : "Aún no hay lecturas" }
            // when it came, not "for 20 min": a time never goes out of date on a notification left up
            let at = Freshness.timeText(r.at.timeIntervalSince1970, now: now.timeIntervalSince1970)
            return en ? "Last reading \(at) · \(value(r))" : "Última lectura \(at) · \(value(r))"
        }
        guard let r = f.reading else { return en ? "No recent reading" : "Sin lectura reciente" }
        let look = Self.look(r, now: now)
        let shown = "\(value(r)) \(look.arrowText ?? "")"
        var text = shown
        if !look.strike, let rate = r.rate, f.kind == .fallingFast || f.kind == .risingFast {
            let amount = "\(unit.rateText(abs(rate), locale: locale)) \(unit.rateSymbol)"
            switch (language, f.kind) {
            case (.en, .fallingFast): text = "\(shown) · falling \(amount)"
            case (.en, _): text = "\(shown) · rising \(amount)"
            case (.es, .fallingFast): text = "\(shown) · baja \(amount)"
            case (.es, _): text = "\(shown) · sube \(amount)"
            }
        }
        guard look.fresh != .fresh else { return text }
        return text + " · " + Freshness.timeText(r.at.timeIntervalSince1970, now: now.timeIntervalSince1970)
    }

    /// `r`'s look at `now`, the card's: the alert notification's surface asks `GlucoseCard.face` as itself.
    static func look(_ r: GlucoseSample, now: Date) -> GlucoseLook {
        let data = CardData(
            readings: [GraphReading(t: r.at.timeIntervalSince1970, v: r.mgdl, rate: r.rate, band: r.band)],
            now: now.timeIntervalSince1970)
        return GlucoseCard.face(data, unit: .mgdl, surface: .alertNotification)
    }

    private var locale: Locale { Locale(identifier: language.rawValue) }

    /// `69 mg/dL` / `3,8 mmol/L` (the user's unit, the language's decimal separator), or the sensor's LO / HI with what
    /// it means, the bound in the unit.
    private func value(_ r: GlucoseSample) -> String {
        func shown(_ mgdl: Int) -> String { "\(unit.format(mgdl, locale: locale)) \(unit.symbol)" }
        if r.isLo {
            let b = shown(GlucoseRange.floor)
            return language == .en ? "LO · below \(b)" : "LO · por debajo de \(b)"
        }
        if r.isHi {
            let b = shown(GlucoseRange.ceiling)
            return language == .en ? "HI · above \(b)" : "HI · por encima de \(b)"
        }
        return shown(Int(r.mgdl.rounded()))
    }

    /// The headline in the language (English: the engine's SPEC copy; highs never suggest more insulin).
    func headline(_ f: AlertFiring) -> String {
        guard language == .es else { return f.headline }
        let base: String
        switch f.kind {
        case .veryLow: base = "Muy baja"
        case .low: base = "Baja"
        case .high: base = "Alta"
        case .veryHigh: base = "Muy alta"
        case .extremelyHigh: base = "Extremadamente alta"
        case .fallingFast: base = "Bajada rápida"
        case .risingFast: base = "Subida rápida"
        case .noData: base = "Sin datos"
        }
        guard f.treated > 0, f.kind.group == .level else { return base }
        if f.kind.isLowSide { return base + (f.treated == 1 ? " · come más" : " · vuelve a comer") }
        return base + (f.treated == 1 ? " · sigue tras la insulina" : " · sigue tras más insulina")
    }

    /// The muted note: "Lows muted" / "Until 10:42" (`MuteCopy`, contracts/alerts/copy.json).
    func mutedTitle(_ side: AlertSide) -> String { MuteCopy.notificationTitle(side, language) }

    func mutedBody(until: String) -> String { MuteCopy.notificationText(until: until, language) }

    /// Settings › Alerts › Send Test Alert: "Test · Low".
    func testTitle(_ f: AlertFiring) -> String { "\(language == .en ? "Test" : "Prueba") · \(title(f))" }

    // Action titles: HIG title case in English, a short name for the result, never an ellipsis (the owner's review,
    // 2026-11-14: Treatment opens a window without one, so the ellipsis no longer meant anything).
    var mute: String { language == .en ? "Mute" : "Silenciar" }
    var muteButton: String { language == .en ? "Mute" : "Silenciar" }
    var mutePlaceholder: String { "30 min, 1 h, 2 h" }
    var remindLess: String { language == .en ? "Remind Less" : "Avisar menos" }
    var remindAsUsual: String { language == .en ? "Remind as Usual" : "Avisar como siempre" }
    var unmute: String { language == .en ? "Unmute" : "Reactivar" }
    var treatment: String { language == .en ? "Treatment" : "Tratamiento" }
    var update: String { language == .en ? "Update" : "Actualizar" }
}
