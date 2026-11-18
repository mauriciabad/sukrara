// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraGraph
import SwiftUI

/// Why a metric isn't on the card (docs/44 Appendix G §4, the owner's Q3; Android `MetricNeed`): its readings cover
/// `have` of the last `window`, and the renderer draws a metric from `need` on (its 25 %: `SlotNumbers.need`).
/// Settings › Metrics says it in the metric's summary; the card stays the renderer's and says nothing.
struct MetricNeed: Equatable {
    var have: TimeInterval
    var window: TimeInterval
    var need: TimeInterval

    /// *Needs more readings: 12 h of the last 7 days, 42 h needed*.
    var text: String {
        L10n.t(
            "Needs more readings: \(Self.duration(have)) of the last \(Self.period(window)), \(Self.duration(need)) needed"
        )
    }

    /// A coverage as the person reads it: whole hours up to three days (*12 h*, *42 h*), then days (*8 days*).
    static func duration(_ seconds: TimeInterval) -> String {
        let hours = Int((seconds / 3600).rounded())
        return hours < 72 ? Durations.text(hours * 60) : days(seconds)
    }

    /// The metric's window: hours under a day (*12 h*), else days (*7 days*).
    static func period(_ seconds: TimeInterval) -> String {
        seconds < 86_400 ? Durations.text(Int((seconds / 60).rounded())) : days(seconds)
    }

    private static func days(_ seconds: TimeInterval) -> String {
        let n = Int((seconds / 86_400).rounded())
        return n == 1 ? L10n.t("1 day") : L10n.t("\(n) days")
    }
}

/// The metrics with options of their own (docs/39): the main, second and third, and the one shown in the last
/// treatment's place while there's no recent treatment (Android's `MetricSlot`).
enum MetricSlot: String, CaseIterable, Identifiable {
    case main, second, third, noRecent

    var id: String { rawValue }
    static let slots: [MetricSlot] = [.main, .second, .third]

    var title: String {
        switch self {
        case .main: L10n.t("Main metric")
        case .second: L10n.t("Second metric")
        case .third: L10n.t("Third metric")
        case .noRecent: L10n.t("No recent treatment")
        }
    }

    /// The details sheet's switch.
    var show: String {
        switch self {
        case .main: L10n.t("Show the main metric")
        case .second: L10n.t("Show the second metric")
        case .third: L10n.t("Show the third metric")
        case .noRecent: L10n.t("Show a metric instead")
        }
    }

    var footer: String {
        switch self {
        case .main:
            L10n.t(
                "The main metric sits at the right end, at full strength. In the sensor's last hours its countdown ring goes around it."
            )
        case .second, .third:
            L10n.t("Shows to the left of the main metric, when there's room. A small widget shows fewer.")
        case .noRecent: L10n.t("Shows in the last treatment's place while there's no recent treatment.")
        }
    }

    /// The main metric is never dimmed: it's the one at full strength.
    var dims: Bool { self != .main }

    func spec(_ g: GraphConfig) -> RingSpec {
        switch self {
        case .main: g.slots[0]
        case .second: g.slots[1]
        case .third: g.slots[2]
        case .noRecent: g.lastElse
        }
    }

    func set(_ r: RingSpec, in g: inout GraphConfig) {
        switch self {
        case .main: g.slots[0] = r
        case .second: g.slots[1] = r
        case .third: g.slots[2] = r
        case .noRecent: g.lastElse = r
        }
    }
}

extension RingSpec {
    /// This metric showing `kind`: a period that kind can't have becomes its default (a GMI needs a week at least).
    func showing(_ kind: SlotKind) -> RingSpec {
        var r = self
        r.kind = kind
        let format = GraphConfig.windowSpec(kind)
        if !format.valid(r.window) { r.window = format.default }
        return r
    }
}

/// Metrics (once "Rings"): what sits beside the value on every card. The main, second and third metric, then the last
/// treatment and what shows in its place while there's none, each a switch with Details… (the Alerts pane's pattern,
/// System Settings › Sharing's), then the time-in-range rings' look. Outside Custom: a read-only summary.
struct MetricsPane: View {
    @Bindable var model: SettingsModel
    @State private var editing: MetricSlot?
    @State private var editingLast = false
    @State private var confirmingReset = false

    private var g: GraphConfig { model.graph }

    var body: some View {
        Form {
            SettingsFromSection(model: model, rows: summary)
            if model.editable {
                Section {
                    ForEach(MetricSlot.slots) { metricRow($0) }
                }
                Section {
                    ToggleDetailsRow(
                        title: L10n.t("Last treatment"),
                        summary: g.lastOn ? Self.whereName(g.lastWhere) : L10n.t("Off"),
                        isOn: graphValue(\.lastOn),
                        detailsLabel: L10n.t("Last treatment details")
                    ) { editingLast = true }
                    // shown in the last treatment's place: nothing to show while that place is off
                    metricRow(.noRecent).disabled(!g.lastOn)
                }
                Section {
                    HStack {
                        Picker(L10n.t("Number in the ring"), selection: graphValue(\.ringRange)) {
                            Text(Self.tirName(.inRange, g, unit: model.unit)).tag(TirRange.inRange)
                            Text(Self.tirName(.tight, g, unit: model.unit)).tag(TirRange.tight)
                        }
                        // the ranges it counts are set in Readings
                        Button(L10n.t("Ranges…")) { model.pane = .readings }
                            .help(L10n.t("The ranges a ring counts are in the Readings pane"))
                    }
                    Toggle(isOn: graphValue(\.ringMarks)) {
                        Text(L10n.t("Rewards"))
                        Text(
                            L10n.t(
                                "Laurel from \(Self.percent(90)), flowers from \(Self.percent(95)), crown at \(Self.percent(100))"
                            ))
                    }
                } header: {
                    Text(L10n.t("Time-in-range rings"))
                }
                Section {
                    RingOrderList(order: g.ringOrder) { o in model.updateGraph { $0.ringOrder = o } }
                    HStack {
                        Spacer()
                        Button(L10n.t("Reset Order")) { model.resetRingOrder() }.disabled(!model.ringOrderChanged)
                    }
                } header: {
                    Text(L10n.t("Colour order"))
                } footer: {
                    Text(
                        L10n.t(
                            "The ring's colours clockwise from 12 o'clock. Drag a range to move it, or Control-click it for Move Up and Move Down."
                        )
                    )
                    .formFooter()
                }
                Section {
                    HStack {
                        Spacer()
                        Button(L10n.t("Reset Metrics…"), role: .destructive) { confirmingReset = true }
                            .disabled(!model.metricsChanged)
                    }
                } footer: {
                    Text(
                        L10n.t(
                            "A metric shows once there's data for a quarter of its period. The menu bar and the widgets show them; a small widget shows fewer."
                        )
                    )
                    .formFooter()
                }
            }
        }
        .formStyle(.grouped)
        .sheet(item: $editing) { slot in
            MetricDetails(model: model, slot: slot) { editing = nil }
        }
        .sheet(isPresented: $editingLast) {
            LastTreatmentDetails(model: model) { editingLast = false }
        }
        .confirmationDialog(L10n.t("Reset the metrics?"), isPresented: $confirmingReset) {
            Button(L10n.t("Reset"), role: .destructive) { model.resetMetrics() }
            Button(L10n.t("Cancel"), role: .cancel) {}
        } message: {
            Text(
                L10n.t(
                    "The metrics, the last treatment and the time-in-range rings go back to their defaults. Your ranges stay."
                ))
        }
    }

    /// A metric: its switch, what it shows and its period ("Time in range · 1 d") or Off, and Details….
    private func metricRow(_ slot: MetricSlot) -> some View {
        let r = slot.spec(g)
        return ToggleDetailsRow(
            title: slot.title,
            summary: slot == .noRecent && !g.lastOn
                ? L10n.t("Needs the last treatment on") : summary(r),
            isOn: Binding(
                get: { r.on },
                set: { on in
                    model.updateGraph {
                        var x = slot.spec($0)
                        x.on = on
                        slot.set(x, in: &$0)
                    }
                }),
            detailsLabel: L10n.t("\(slot.title) details")
        ) { editing = slot }
    }

    /// What a metric shows and its period, and why the card doesn't draw it while its readings are too few.
    private func summary(_ r: RingSpec) -> String {
        guard r.on, let n = model.need(r.window) else { return Self.metricSummary(r) }
        return "\(Self.metricSummary(r))\n\(MetricNeed(have: n.have, window: r.window, need: n.need).text)"
    }

    private func graphValue<V>(_ path: WritableKeyPath<GraphConfig, V>) -> Binding<V> {
        Binding(get: { g[keyPath: path] }, set: { v in model.updateGraph { $0[keyPath: path] = v } })
    }

    /// Outside Custom: what's in use, in words.
    private var summary: [(String, String)] {
        // the pane's order: the three metrics, the last treatment and what shows without one, the rings' look
        let slots: [(String, String)] = MetricSlot.slots.map { ($0.title, summary($0.spec(g))) }
        return slots + [
            (L10n.t("Last treatment"), g.lastOn ? Self.whereName(g.lastWhere) : L10n.t("Off")),
            (MetricSlot.noRecent.title, Self.metricSummary(g.lastElse)),
            (L10n.t("Number in the ring"), Self.tirName(g.ringRange, g, unit: model.unit)),
            (L10n.t("Rewards"), g.ringMarks ? L10n.t("On") : L10n.t("Off")),
            (L10n.t("Colour order"), g.ringOrder.map(RingOrderList.name).joined(separator: ", ")),
        ]
    }

    /// "Time in range · 1 d", or "Off".
    static func metricSummary(_ r: RingSpec) -> String {
        guard r.on else { return L10n.t("Off") }
        return "\(kindName(r.kind)) · \(NumberSettingRow.word(GraphConfig.windowSpec(r.kind), r.window))"
    }

    static func kindName(_ k: SlotKind) -> String {
        switch k {
        case .tir: L10n.t("Time in range")
        case .gmi: L10n.t("GMI")
        case .average: L10n.t("Average glucose")
        case .cv: L10n.t("Variability")
        }
    }

    /// One line on what a kind is, with its goal (the gauge's tick).
    static func kindSummary(_ k: SlotKind, unit: GlucoseUnit) -> String {
        switch k {
        case .tir: L10n.t("A ring of the time in each range")
        case .gmi: L10n.t("Estimated A1c · goal under \(percent(7))")
        // the consensus goal the gauge's tick marks (`Goal.average`, 154 mg/dL)
        case .average: L10n.t("Goal under \(unit.format(154)) \(unit.symbol)")
        case .cv: L10n.t("CV: how much glucose swings · goal \(percent(36)) or less")
        }
    }

    /// "90 %": a percentage as the settings write it (a literal % in a catalog key would be read as a format).
    static func percent(_ n: Int) -> String { "\(n)\u{00A0}%" }

    static func whereName(_ p: LastPlace) -> String {
        p == .end ? L10n.t("At the right end") : L10n.t("Next to the value")
    }

    /// "In range (70–179 mg/dL)": what a time-in-range ring's number counts, with the values.
    static func tirName(_ r: TirRange, _ g: GraphConfig, unit: GlucoseUnit) -> String {
        let top = r == .tight ? g.bands.tight : g.bands.high - 1
        let span = "\(unit.format(g.bands.low))–\(unit.format(top)) \(unit.symbol)"
        return r == .tight ? L10n.t("Very good (\(span))") : L10n.t("In range (\(span))")
    }
}

/// The sheet behind a metric's Details…: its switch; what it shows (one click each, the chosen one's goal under them);
/// its period (the kind's own choices: a GMI from a week); Dimmed (not the main one). Greyed while it's off.
struct MetricDetails: View {
    @Bindable var model: SettingsModel
    let slot: MetricSlot
    let done: () -> Void

    var body: some View {
        let r = slot.spec(model.graph)
        let placeOff = slot == .noRecent && !model.graph.lastOn
        VStack(spacing: 0) {
            Form {
                Section {
                    Toggle(slot.show, isOn: Binding(get: { r.on }, set: { v in update { $0.on = v } }))
                        .disabled(placeOff)
                    // the chosen kind's line (what it is, its goal) under the choices, in the same row
                    VStack(alignment: .leading, spacing: 6) {
                        Picker(
                            L10n.t("Shows"),
                            selection: Binding(get: { r.kind }, set: { k in update { $0 = $0.showing(k) } })
                        ) {
                            ForEach(SlotKind.allCases, id: \.self) { Text(MetricsPane.kindName($0)).tag($0) }
                        }
                        .pickerStyle(.radioGroup)
                        Text(MetricsPane.kindSummary(r.kind, unit: model.unit))
                            .font(.callout).foregroundStyle(.secondary)
                            .contentTransition(.opacity)
                            .animation(.snappy, value: r.kind)
                    }
                    .disabled(!r.on || placeOff)
                    NumberSettingRow(
                        label: L10n.t("Period"), format: GraphConfig.windowSpec(r.kind),
                        value: Binding(get: { r.window }, set: { v in update { $0.window = v } })
                    )
                    .disabled(!r.on || placeOff)
                    if slot.dims {
                        Toggle(isOn: Binding(get: { r.faded }, set: { v in update { $0.faded = v } })) {
                            Text(L10n.t("Dimmed"))
                            Text(L10n.t("Lighter than the main metric"))
                        }
                        .disabled(!r.on || placeOff)
                    }
                } footer: {
                    Text(placeOff ? L10n.t("Needs the last treatment on") : slot.footer).formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: 420)
    }

    private func update(_ change: (inout RingSpec) -> Void) {
        model.updateGraph {
            var x = slot.spec($0)
            change(&x)
            slot.set(x, in: &$0)
        }
    }
}

/// The sheet behind the last treatment's Details…: its switch, where it sits (greyed while it's off), what it shows.
struct LastTreatmentDetails: View {
    @Bindable var model: SettingsModel
    let done: () -> Void

    var body: some View {
        let g = model.graph
        VStack(spacing: 0) {
            Form {
                Section {
                    Toggle(
                        L10n.t("Show the last treatment"),
                        isOn: Binding(get: { g.lastOn }, set: { v in model.updateGraph { $0.lastOn = v } }))
                    Picker(
                        L10n.t("Where"),
                        selection: Binding(get: { g.lastWhere }, set: { v in model.updateGraph { $0.lastWhere = v } })
                    ) {
                        Text(MetricsPane.whereName(.nearValue)).tag(LastPlace.nearValue)
                        Text(MetricsPane.whereName(.end)).tag(LastPlace.end)
                    }
                    .pickerStyle(.radioGroup)
                    .disabled(!g.lastOn)
                } footer: {
                    Text(
                        L10n.t(
                            "The time since your last rapid insulin or carbs, the amount, and a ring for the time its alerts stay quiet. Rapid insulin shows for 4 h, carbs for up to 3 h; long-acting insulin and notes don't show."
                        )
                    )
                    .formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: 300)
    }
}
