// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraGraph
import SwiftUI

/// Readings (docs/35 §7): how each reading is shown, judged and where it heads. The unit (this Mac's own, always
/// editable), the six ranges (mirrored: read-only with where they come from outside Custom) with the way to the alert
/// levels, which stay separate in Alerts, and the trend arrow (mirrored) with its live preview.
struct ReadingsPane: View {
    @Bindable var model: SettingsModel

    private var g: GraphConfig { model.graph }

    var body: some View {
        Form {
            SettingsFromSection(model: model)
            Section {
                Picker(L10n.t("Unit"), selection: $model.unit) {
                    Text(verbatim: "mg/dL").tag(GlucoseUnit.mgdl)
                    Text(verbatim: "mmol/L").tag(GlucoseUnit.mmol)
                }
                .pickerStyle(.segmented)
            } footer: {
                Text(L10n.t("This Mac's own: every value, range and alert level on this Mac shows in it."))
                    .formFooter()
            }
            rangesSection
            TrendArrowSection(model: model)
        }
        .formStyle(.grouped)
    }

    /// The six ranges, each with the values it makes count; read-only (with where they come from) outside Custom.
    private var rangesSection: some View {
        Section {
            ForEach(Array(MacSettings.bands.enumerated()), id: \.element.id) { i, band in
                // the number, then exactly which values it makes count ("Low: 56–69 mg/dL")
                NumberSettingRow(
                    label: Self.bandName(band.id), format: band.format,
                    value: Binding(get: { model.number(band) }, set: { model.setNumber($0, band) }),
                    readOnly: !model.editable,
                    span: { v in
                        RangeSpans.text(RangeSpans.ofBand(RangeSpans.replacing(g.bands, i, v), i), unit: model.unit)
                    })
            }
            if model.editable {
                HStack {
                    Spacer()
                    Button(L10n.t("Reset Ranges")) {
                        withAnimation(.snappy) { model.resetRanges() }
                    }
                    .disabled(!model.rangesChanged)
                }
            }
        } header: {
            Text(L10n.t("Ranges (\(model.unit.symbol))"))
        } footer: {
            HStack(alignment: .firstTextBaseline) {
                Text(
                    L10n.t(
                        "Used by the graph, the rings, the report and the menu bar colour, and kept in order. Alert levels are set apart, in Alerts."
                    )
                )
                .formFooter()
                Button(L10n.t("Alert Levels…")) { model.pane = .alerts }
                    .help(L10n.t("Set when each alert sounds, in the Alerts pane"))
            }
        }
    }

    static func bandName(_ id: String) -> String {
        switch id {
        case "graph.band.veryLow": L10n.t("Very low up to")
        case "graph.band.low": L10n.t("Low below")
        case "graph.band.tight": L10n.t("Very good up to")
        case "graph.band.high": L10n.t("High from")
        case "graph.band.veryHigh": L10n.t("Very high from")
        default: L10n.t("Extremely high from")
        }
    }

    /// "In range: 70–179 mg/dL": the target range in use, as Graph's *Ranges* row says it.
    static func targetText(_ b: Bands, unit: GlucoseUnit) -> String {
        RangeSpans.text([RangeSpans.Span(name: .inRange, lo: b.low, hi: b.high - 1)], unit: unit) ?? ""
    }
}
