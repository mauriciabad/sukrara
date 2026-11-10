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
    typealias Language = CopyLanguage

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

    /// The notification title: the headline only (the same words on every platform, contracts/alerts/SPEC.md §1).
    func title(_ f: AlertFiring) -> String { headline(f) }

    /// The body: the value and arrow (`69 mg/dL ↘`), with the rate for falling / rising fast (`· falling 2.4
    /// mg/dL/min`, `· falling 0.13 mmol/L/min`), and for no data how long and the last value. Never the threshold, never a made-up number: the
    /// sensor's LO / HI as such (not its stored 39 / 401), and without a reading it says so.
    func body(_ f: AlertFiring, now: Date) -> String {
        let en = language == .en
        if f.kind == .noData {
            // a source that never delivered a reading: nothing to count from but its start, nothing to show
            guard let r = f.reading else { return en ? "No reading yet" : "Aún no hay lecturas" }
            let since = Durations.text(max(Int(f.threshold.rounded()), Int(now.timeIntervalSince(r.at) / 60)))
            return en
                ? "No reading for \(since) · last \(value(r))" : "Sin lecturas desde hace \(since) · última \(value(r))"
        }
        guard let r = f.reading else { return en ? "No recent reading" : "Sin lectura reciente" }
        let shown = "\(value(r)) \(TrendAngle.text(r.rate))"
        guard let rate = r.rate, f.kind == .fallingFast || f.kind == .risingFast else { return shown }
        let amount = "\(unit.rateText(abs(rate), locale: locale)) \(unit.rateSymbol)"
        switch (language, f.kind) {
        case (.en, .fallingFast): return "\(shown) · falling \(amount)"
        case (.en, _): return "\(shown) · rising \(amount)"
        case (.es, .fallingFast): return "\(shown) · baja \(amount)"
        case (.es, _): return "\(shown) · sube \(amount)"
        }
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
        case .veryLow: base = "Glucosa muy baja"
        case .low: base = "Glucosa baja"
        case .high: base = "Glucosa alta"
        case .veryHigh: base = "Glucosa muy alta"
        case .extremelyHigh: base = "Glucosa extremadamente alta"
        case .fallingFast: base = "Bajada rápida"
        case .risingFast: base = "Subida rápida"
        case .noData: base = "Sin datos de glucosa"
        }
        guard f.treated > 0, f.kind.group == .level else { return base }
        if f.kind.isBelowThreshold { return base + (f.treated == 1 ? " · come más" : " · vuelve a comer") }
        return base + (f.treated == 1 ? " · sigue alta tras la insulina" : " · sigue alta tras más insulina")
    }

    /// The muted note: "Low alerts muted" / "Until 10:42".
    func mutedTitle(_ side: AlertSide) -> String { MuteCopy.notificationTitle(side, language) }

    func mutedBody(until: String) -> String { MuteCopy.notificationText(until: until, language) }

    /// Settings › Alerts › Send Test Alert: "Test · Low glucose".
    func testTitle(_ f: AlertFiring) -> String { "\(language == .en ? "Test" : "Prueba") · \(title(f))" }

    // Action titles (HIG title case in English; the ellipsis = more input follows).
    var mute: String { language == .en ? "Mute…" : "Silenciar…" }
    var muteButton: String { language == .en ? "Mute" : "Silenciar" }
    var mutePlaceholder: String { language == .en ? "30 min, 1 h, 2 h…" : "30 min, 1 h, 2 h…" }
    var remindLess: String { language == .en ? "Remind Less" : "Avisar menos" }
    var remindAsUsual: String { language == .en ? "Remind as Usual" : "Avisar como siempre" }
    var unmute: String { language == .en ? "Unmute" : "Reactivar" }
    var changeTime: String { language == .en ? "Change Time…" : "Cambiar duración…" }
}
