// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraGraph
import SwiftUI

/// Graph: the chart only. Atop, the target range in use with the way to Readings (where the ranges are); the time shown,
/// the distribution and crown beside the chart, the vertical lines, treatments on the graph, lines across it, the
/// sensor countdown and time left (the Mac has no sensor page). The time shown and the sensor time left are the menu
/// bar's, which the widgets follow unless they have their own: a row each, opening its page (docs/38). Outside Custom
/// the mirrored ones show as a read-only summary with the way to change them.
struct GraphPane: View {
    @Bindable var model: SettingsModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var g: GraphConfig { model.graph }

    var body: some View {
        ZStack {
            // the pane stays underneath while a page shows, so going back finds it where it was
            pane
                .opacity(model.graphPage == nil ? 1 : 0)
                .disabled(model.graphPage != nil)
                .accessibilityHidden(model.graphPage != nil)
            switch model.graphPage {
            case .hours: hoursPage.transition(pageTransition)
            case .sensorTime: sensorPage.transition(pageTransition)
            case nil: EmptyView()
            }
        }
        .confirmationDialog(L10n.t("Reset all graph settings?"), isPresented: $model.confirmingResetAll) {
            Button(L10n.t("Reset"), role: .destructive) { model.resetGraph() }
            Button(L10n.t("Cancel"), role: .cancel) {}
        } message: {
            Text(
                L10n.t(
                    "The time shown, the rings, the lines, the ranges, the last treatment and the sensor settings go back to their defaults."
                ))
        }
    }

    private var pane: some View {
        Form {
            SettingsFromSection(model: model, rows: summary)
            Section {
                // the ranges live in Readings (they colour more than the graph); here, the one in use and the way there
                LabeledContent(L10n.t("Ranges")) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(ReadingsPane.targetText(g.bands, unit: model.unit)).foregroundStyle(.secondary)
                        Button(L10n.t("Edit in Readings…")) { model.pane = .readings }
                            .help(L10n.t("The six ranges are in the Readings pane"))
                    }
                }
            }
            Section {
                // the menu bar's value is mirrored (read-only outside Custom); the widgets' is always this Mac's
                PlaceSettingRow(
                    title: L10n.t("Time shown"), value: hoursWord(g.hours),
                    spokenValue: NumberSettingRow.spoken(SettingsSpec.graphHours, Double(g.hours)),
                    summary: PlaceSummary.line(menuBar: g.hours, widgets: widgetHours, word: hoursWord),
                    spokenSummary: PlaceSummary.line(menuBar: g.hours, widgets: widgetHours) {
                        NumberSettingRow.spoken(SettingsSpec.graphHours, Double($0))
                    }
                ) { open(.hours) }
                if model.editable {
                    Toggle(L10n.t("Time in range distribution"), isOn: graphBool(\.side))
                    Toggle(L10n.t("Crown beside the graph when all of it is in range"), isOn: graphBool(\.columnCrown))
                    NumberSettingRow(
                        label: L10n.t("Vertical lines"), format: MacSettings.gridHours.format,
                        value: number(MacSettings.gridHours))
                }
            } header: {
                Text(L10n.t("Graph"))
            }
            if model.editable {
                marksSection
                linesSection
            }
            Section {
                if model.editable {
                    NumberSettingRow(
                        label: L10n.t("Sensor countdown (last hours)"), format: MacSettings.countdownHours.format,
                        value: number(MacSettings.countdownHours))
                }
                PlaceSettingRow(
                    title: L10n.t("Sensor time left"), value: ReadOnlyValue.onOff(model.sensorTime),
                    spokenValue: ReadOnlyValue.onOff(model.sensorTime), summary: sensorSummary,
                    spokenSummary: sensorSummary
                ) { open(.sensorTime) }
            } header: {
                Text(L10n.t("Sensor"))
            } footer: {
                if model.editable {
                    note(
                        L10n.t(
                            "A ring over the main ring counts down the sensor's last hours. The warm-up and Sensor ended show either way."
                        ))
                }
            }
            if model.editable {
                Section {
                    HStack {
                        Spacer()
                        Button(L10n.t("Reset All Graph Settings…"), role: .destructive) {
                            model.confirmingResetAll = true
                        }
                        .disabled(!model.graphChanged)
                    }
                }
            }
        }
        .formStyle(.grouped)
    }

    /// Time shown: the menu bar's (mirrored: read-only outside Custom, in words with where it comes from) and the
    /// widgets' (this Mac's own, always editable).
    private var hoursPage: some View {
        SettingPage(title: L10n.t("Time shown"), footer: Self.followFooter, back: back) {
            NumberSettingRow(
                label: L10n.t("Menu bar"), format: MacSettings.graphHours.format,
                value: number(MacSettings.graphHours), readOnly: !model.editable, source: model.mirrorSource)
            NumberSettingRow(
                label: L10n.t("Widgets"), format: GraphPlace.widget.hours.format,
                value: Binding(get: { model.placeHours(.widget) }, set: { model.setPlaceHours($0, .widget) }),
                follows: Double(g.hours))
        }
    }

    /// Sensor time left: the menu bar's switch (this Mac's own) and the widgets'.
    private var sensorPage: some View {
        SettingPage(title: L10n.t("Sensor time left"), footer: Self.followFooter, back: back) {
            Toggle(L10n.t("Menu bar"), isOn: $model.sensorTime)
            SwitchPlacePopUp(
                label: L10n.t("Widgets"), menuBar: model.sensorTime,
                own: Binding(get: { model.sensorPlace(.widget) }, set: { model.setSensorPlace($0, .widget) }))
        }
    }

    private static var followFooter: String { L10n.t("Widgets follow the menu bar unless you choose their own.") }

    private var pageTransition: AnyTransition {
        reduceMotion ? .opacity : .move(edge: .trailing).combined(with: .opacity)
    }

    private func open(_ page: SettingsModel.GraphPage) {
        withAnimation(reduceMotion ? nil : .snappy) { model.graphPage = page }
    }

    private func back() {
        withAnimation(reduceMotion ? nil : .snappy) { model.graphPage = nil }
    }

    /// Treatments on the graph: a row per kind, a checkbox per part it has (HIG: checkboxes for independent options).
    private var marksSection: some View {
        Section {
            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 8) {
                GridRow {
                    Text(verbatim: "")
                    ForEach(MarkShow.partIds, id: \.self) { p in
                        Text(Self.partName(p)).foregroundStyle(.secondary).font(.caption)
                    }
                }
                ForEach(MarkShow.kindIds, id: \.self) { k in
                    GridRow {
                        Text(Self.kindName(k))
                        ForEach(MarkShow.partIds, id: \.self) { p in
                            if MarkShow.partsOf(k).contains(p) {
                                Toggle(
                                    Self.partName(p),
                                    isOn: Binding(
                                        get: { g.marks.shows(k, p) },
                                        set: { v in model.updateGraph { $0.marks = $0.marks.setting(k, p, v) } })
                                )
                                .toggleStyle(.checkbox).labelsHidden()
                                .accessibilityLabel(L10n.t("\(Self.kindName(k)): \(Self.partName(p))"))
                            } else {
                                Text(verbatim: "—").foregroundStyle(.tertiary).accessibilityHidden(true)
                            }
                        }
                    }
                }
            }
            HStack {
                Spacer()
                Button(L10n.t("Reset Treatments")) { model.resetMarks() }.disabled(!model.marksChanged)
            }
        } header: {
            Text(L10n.t("Treatments on the graph"))
        } footer: {
            note(L10n.t("Close treatments share one label. Hidden amounts leave it; the time shows once."))
        }
    }

    /// "100 mg/dL" / "5.5 mmol/L": a glucose level in the person's unit.
    private func glucose(_ mgdl: Int) -> String {
        "\(model.unit.format(mgdl)) \(model.unit.symbol)"  // card-ok: a setting's level (a line), not a reading
    }

    private var linesSection: some View {
        Section {
            ForEach(g.lines, id: \.self) { l in
                LabeledContent(glucose(l)) {
                    Button(role: .destructive) {
                        model.removeLine(l)
                    } label: {
                        Image(systemName: "minus.circle")
                    }
                    .buttonStyle(.borderless)
                    .help(L10n.t("Remove this line"))
                    .accessibilityLabel(L10n.t("Remove the line at \(glucose(l))"))
                }
            }
            if g.lines.count < SettingsSpec.line.maxItems {
                AddLineRow(existing: g.lines) { model.addLine($0) }
            }
        } header: {
            Text(L10n.t("Lines across the graph"))
        } footer: {
            note(L10n.t("Dashed lines at these levels, up to six."))
        }
    }

    private var summary: [(String, String)] {
        let beside = [
            g.side ? L10n.t("time in range distribution") : nil,
            g.columnCrown ? L10n.t("crown when all in range") : nil,
        ].compactMap { $0 }
        return [
            (
                L10n.t("Lines across the graph"),
                g.lines.isEmpty
                    ? L10n.t("None")
                    : g.lines.map { model.unit.format($0) }.joined(separator: ", ") + " " + model.unit.symbol
            ),
            (L10n.t("Vertical lines"), NumberSettingRow.word(SettingsSpec.gridHours, Double(g.gridHours))),
            (L10n.t("Beside the graph"), beside.isEmpty ? L10n.t("Nothing") : beside.joined(separator: ", ")),
            (
                L10n.t("Treatments on the graph"),
                MarkShow.kindIds.map { k in
                    let parts = MarkShow.partsOf(k).filter { g.marks.shows(k, $0) }.map(Self.partName)
                    return "\(Self.kindName(k)): " + (parts.isEmpty ? L10n.t("nothing") : parts.joined(separator: ", "))
                }.joined(separator: "; ")
            ),
            (
                L10n.t("Sensor countdown (last hours)"),
                NumberSettingRow.word(SettingsSpec.countdownHours, Double(g.countdownHours))
            ),
        ]
    }

    /// The time shown in words ("12 h").
    private func hoursWord(_ h: Int) -> String { NumberSettingRow.word(SettingsSpec.graphHours, Double(h)) }

    /// The widgets' own time shown (nil = Same as the menu bar).
    private var widgetHours: Int? {
        let own = model.placeHours(.widget)
        return own > 0 ? Int(own) : nil
    }

    private var sensorSummary: String? {
        PlaceSummary.line(menuBar: model.sensorTime, widgets: model.sensorPlace(.widget), word: ReadOnlyValue.onOff)
    }

    private func number(_ n: MacSettings.Number) -> Binding<Double> {
        Binding(get: { model.number(n) }, set: { model.setNumber($0, n) })
    }

    private func graphBool(_ path: WritableKeyPath<GraphConfig, Bool>) -> Binding<Bool> {
        Binding(get: { g[keyPath: path] }, set: { v in model.updateGraph { $0[keyPath: path] = v } })
    }

    private func note(_ s: String) -> some View { Text(s).formFooter() }

    static func kindName(_ k: String) -> String {
        switch k {
        case "rapid": L10n.t("Rapid insulin")
        case "long": L10n.t("Long-acting insulin")
        case "carbs": L10n.t("Carbs")
        default: L10n.t("Notes")
        }
    }

    static func partName(_ p: String) -> String {
        switch p {
        case "point": L10n.t("Point")
        case "line": L10n.t("Mute line")
        case "amount": L10n.t("Amount")
        default: L10n.t("Time")
        }
    }
}

/// "Add a line at": the one number input with the line format, and Add.
private struct AddLineRow: View {
    /// The lines already there: adding one of them again does nothing, so Add is dimmed.
    let existing: [Int]
    let add: (Double) -> Void
    @State private var value = SettingsSpec.line.default

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            NumberSettingRow(label: L10n.t("Add a line at"), format: SettingsSpec.line, value: $value)
            Button(L10n.t("Add")) { add(value) }.disabled(existing.contains(Int(value)))
        }
    }
}

/// The pane's first section: where its settings come from now and, outside Custom, what's in use (read-only) with the
/// ways to change it.
struct SettingsFromSection: View {
    @Bindable var model: SettingsModel
    var rows: [(String, String)] = []

    var body: some View {
        if !model.editable {
            Section {
                LabeledContent(L10n.t("Settings from"), value: SettingsFromPicker.name(model.settingsFrom, model))
                ForEach(rows, id: \.0) { r in
                    LabeledContent(r.0) { Text(r.1).multilineTextAlignment(.trailing) }
                }
            } footer: {
                HStack(alignment: .firstTextBaseline) {
                    Text(model.inUse + " " + L10n.t("To change these on this Mac, choose Custom."))
                        .formFooter()
                    Spacer()
                    Button(L10n.t("Use my own settings")) { model.useOwn() }
                    Button(L10n.t("Change…")) { model.pane = .general }
                }
            }
        }
    }
}
