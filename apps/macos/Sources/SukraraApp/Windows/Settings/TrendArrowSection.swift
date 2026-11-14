// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraGraph
import SwiftUI

/// Settings › General › Trend arrow (#204, contracts/trend): where every arrow, spoken trend and the fast alerts take
/// the trend from: Quick / Balanced / Smooth from the readings, or From the sensor. A live preview of the newest value
/// and its arrow on top (registered glyph surface `trend-preview`); outside Custom the choice is read-only with where
/// it comes from, like the other mirrored settings (the exit: Settings from › Custom, at the top of General).
struct TrendArrowSection: View {
    @Bindable var model: SettingsModel

    var body: some View {
        Section {
            TrendPreview(reading: model.newestReading(), setting: model.trend, unit: model.unit)
            if model.editable {
                Picker(L10n.t("Trend from"), selection: $model.trend) {
                    ForEach(TrendSetting.allCases, id: \.self) { s in
                        VStack(alignment: .leading, spacing: 1) {
                            Text(Self.name(s))
                            Text(Self.line(s)).font(.callout).foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                        .tag(s)
                    }
                }
                .pickerStyle(.radioGroup)
            } else {
                LabeledContent(L10n.t("Trend from")) {
                    ReadOnlyValue(value: Self.name(model.trend), source: model.mirrorSource)
                }
            }
        } header: {
            Text(L10n.t("Trend arrow"))
        } footer: {
            Text(L10n.t("Every arrow, spoken trend and the Falling fast / Rising fast alerts use this."))
                .formFooter()
        }
    }

    /// A choice's name.
    static func name(_ s: TrendSetting) -> String {
        switch s {
        case .quick: L10n.t("Quick")
        case .balanced: L10n.t("Balanced")
        case .smooth: L10n.t("Smooth")
        case .sensor: L10n.t("From the sensor")
        }
    }

    /// A choice's one line.
    static func line(_ s: TrendSetting) -> String {
        switch s {
        case .quick: L10n.t("From the readings: turns soonest, moves more.")
        case .balanced: L10n.t("From the readings: about 4 minutes behind, steady. Recommended.")
        case .smooth: L10n.t("From the readings: the calmest, about 6 minutes behind.")
        case .sensor: L10n.t("The sensor's own trend: calm, but often minutes late.")
        }
    }
}

/// The preview: the newest value and its arrow as the card shows them (`GlucoseCard.face`, surface `trend-preview`),
/// the arrow turning with a short spring when the trend or the choice changes (none with Reduce Motion), and where
/// the trend came from with its rate (and what the sensor says).
struct TrendPreview: View {
    let reading: Reading?
    let setting: TrendSetting
    let unit: GlucoseUnit
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let look = Self.look(reading, unit: unit)
        HStack(alignment: .center, spacing: 12) {
            if let text = look?.text {
                HStack(spacing: 4) {
                    Text(text).font(.system(size: 28, weight: .bold, design: .rounded)).monospacedDigit()
                        .strikethrough(look?.strike == true)
                    arrow(look)
                }
                .foregroundStyle(look?.strike == true ? .secondary : .primary)
            }
            Text(Self.detail(reading, setting: setting, unit: unit))
                .font(.callout).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(
                [
                    reading.map {
                        GlucoseCard.accessibilityLabel(
                            Self.data($0), unit: unit, surface: .trendPreview, config: GraphConfig())
                    },
                    Self.detail(reading, setting: setting, unit: unit),
                ].compactMap { $0 }.joined(separator: ". ")))
    }

    /// The card's arrow, turned to the trend's angle with a spring (doubled from ±3), or the "no trend" ring.
    @ViewBuilder private func arrow(_ look: GlucoseLook?) -> some View {
        let size: CGFloat = 22
        if look?.trend == .arrow, let rate = look?.rate, let band = look?.band,
            let cg = GlucoseCard.trendImage(rate: 0, band: band, size: size, scale: 2, color: .black)
        {
            Image(decorative: cg, scale: 2).renderingMode(.template)
                .rotationEffect(.degrees(TrendAngle.deg(rate)))
                .animation(reduceMotion ? nil : .spring(duration: 0.3, bounce: 0), value: TrendAngle.deg(rate))
        } else if look?.trend != nil,
            let cg = GlucoseCard.trendImage(rate: nil, band: nil, size: size, scale: 2, color: .black)
        {
            Image(decorative: cg, scale: 2).renderingMode(.template)
        }
    }

    /// The newest reading as the card's data now.
    static func data(_ r: Reading, now: Date = Date()) -> CardData {
        CardData(readings: [GraphReading(r)], now: now.timeIntervalSince1970)
    }

    /// The newest reading's look as the card shows it now.
    static func look(_ r: Reading?, unit: GlucoseUnit, now: Date = Date()) -> GlucoseLook? {
        r.map { GlucoseCard.face(data($0, now: now), unit: unit, surface: .trendPreview) }
    }

    /// "+1.0 mg/dL/min" in the unit.
    static func rateText(_ rate: Double, _ unit: GlucoseUnit) -> String {
        (rate < 0 ? "−" : "+") + unit.rateText(abs(rate)) + " " + unit.rateSymbol
    }

    /// Where the newest reading's trend came from, with its rate and what the sensor says.
    static func detail(_ r: Reading?, setting: TrendSetting, unit: GlucoseUnit) -> String {
        guard let r else { return L10n.t("No reading yet") }
        let sensor = TrendRate.sensorSays(r)
        let says = sensor.map { s in
            L10n.t("the sensor says \(TrendAngle.text(band: TrendRate.plainBand(s))) \(rateText(s, unit))")
        }
        guard let t = r.trend else {
            return setting == .sensor
                ? L10n.t("No trend: the sensor sent no rate.") : L10n.t("No trend yet: too few readings.")
        }
        switch (t.from, setting) {
        case (.sensor, .sensor): return L10n.t("From the sensor: \(rateText(t.rate, unit))")
        case (.sensor, _): return L10n.t("Too few readings: the sensor's rate, \(rateText(t.rate, unit))")
        case (.readings, _):
            let own = L10n.t("From the readings: \(rateText(t.rate, unit))")
            return says.map { own + " · " + $0 } ?? own
        }
    }
}
