// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraAlerts
import SukraraCore
import SwiftUI

/// Alerts: alerts on/off; each kind as a row with its summary and Details… (threshold, repeat, its sound with ▶, Like
/// an alarm, Send Test Alert; the fast ones also the trend arrow, the same setting as Readings'); the way to the ranges
/// (Readings), kept apart from these levels; the quiet times; the sound pack with ▶ and Different sound each time;
/// Notification Settings…; Fine-tuning… (the waits near a threshold, #37, and what the notification's buttons offer);
/// and what is quiet right now with its ways out. The mirrored
/// rules (each kind's on / threshold / repeat but no data's, the quiet times) are read-only outside Custom; what
/// isn't mirrored (alerts on/off, no data, sounds, Like an alarm, the notification's buttons) is this Mac's.
struct AlertsRangesPane: View {
    @Bindable var model: SettingsModel
    /// The running alert rules (Send Test Alert, Active now); nil in an offline run.
    var controller: AlertController? = nil
    @State private var editing: AlertKind?
    @State private var editingNight: AlertKind?
    @State private var fineTuning = false
    @State private var editingQuiet: QuietRuleKind?

    var body: some View {
        Form {
            if model.alerts.readError {
                Section {
                    Notice(
                        L10n.t("Alert settings couldn't be read. Sukrara keeps using the last ones it read."),
                        tone: .error)
                    Button(L10n.t("Reset alert settings")) { model.alerts.resetToDefaults() }
                }
            }
            SettingsFromSection(model: model)
            Section {
                Toggle(L10n.t("Glucose alerts"), isOn: alertBinding(\.on))
                ForEach(AlertKind.allCases, id: \.self) { kind in
                    kindRow(kind)
                }
            } header: {
                Text(L10n.t("Alerts"))
            } footer: {
                // thresholds stay apart from the ranges (as LibreLink keeps them); the way to the ranges
                HStack(alignment: .firstTextBaseline) {
                    Text(L10n.t("The ranges colour the graph; these levels alert.")).formFooter()
                    Button(L10n.t("Ranges…")) { model.pane = .readings }
                        .help(L10n.t("The ranges are in the Readings pane"))
                }
            }
            if let controller { ActiveNowSection(alerts: controller) }
            nightSection
            // #39: the wearer's very low nobody answered on its phone, escalated to this Mac: only while their settings
            // apply (the wearer's alerts are mirrored), never where it does nothing (Appendix G finding 9)
            if model.settingsFrom == .wearer && model.linkSource {
                Section {
                    Toggle(L10n.t("When they don't answer a very low"), isOn: alertBinding(\.escalation.follow))
                } header: {
                    Text(L10n.t("If a very low gets no answer"))
                } footer: {
                    Text(
                        L10n.t(
                            "Alert me, also when muted here, if nobody answers it on their phone: “Very low · no response”."
                        )
                    )
                    .formFooter()
                }
            }
            // each quiet rule: its switch and what it is set to; Details… opens its length and what it does
            Section(L10n.t("Quiet times")) {
                ForEach(QuietRuleKind.allCases, id: \.self) { quietRow($0) }
            }
            Section(L10n.t("Sounds")) {
                HStack {
                    Picker(L10n.t("Sound pack"), selection: alertBinding(\.sounds.pack)) {
                        ForEach(SettingsSpec.options["soundPack"] ?? [], id: \.self) { Text(Self.packName($0)).tag($0) }
                    }
                    PreviewButton(pack: model.alerts.settings.sounds.pack, kind: .low)
                }
                Toggle(L10n.t("Different sound each time"), isOn: alertBinding(\.sounds.shuffle))
                HStack {
                    Text(L10n.t("Banners, Focus and the lock screen are in System Settings."))
                        .font(.callout).foregroundStyle(.secondary)
                    Spacer()
                    Button(L10n.t("Notification Settings…")) { Self.openNotificationSettings() }
                }
                if let status = model.focusStatus { FocusChoice(status: status) { model.throughFocus = $0 } }
            }
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L10n.t("Fine-tuning"))
                        Text(FineTuning.summary(model.shownAlerts))
                            .font(.callout).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(L10n.t("Fine-tuning…")) { fineTuning = true }
                        .accessibilityLabel(
                            L10n.t("Fine-tuning: the waits near a threshold and the notification's buttons"))
                }
            }
        }
        .formStyle(.grouped)
        .sheet(item: $editingQuiet) { which in
            QuietDetails(model: model, which: which) { editingQuiet = nil }
        }
        .sheet(isPresented: $fineTuning) {
            FineTuning(model: model) { fineTuning = false }
        }
        .sheet(item: $editingNight) { kind in
            NightDetails(model: model, kind: kind, readOnly: model.alertsMirrored && kind != .noData) {
                editingNight = nil
            }
        }
        .sheet(item: $editing) { kind in
            AlertDetails(
                model: model, kind: kind, controller: controller,
                readOnly: model.alertsMirrored && kind != .noData
            ) { editing = nil }
        }
    }

    /// Night alerts (#38): the switch, its hours, and every alert's night rule (very low always on), with the line that
    /// says they apply now.
    @ViewBuilder private var nightSection: some View {
        let n = model.shownAlerts.night
        let readOnly = model.alertsMirrored
        Section {
            if let until = controller?.ui.nightUntil {
                Label(MuteCopy.nightUntil(until: Clock.time(until)), systemImage: "moon.zzz")
            }
            if readOnly {
                LabeledContent(L10n.t("Night alerts")) {
                    ReadOnlyValue(value: ReadOnlyValue.onOff(n.on), source: model.mirrorSource)
                }
            } else {
                Toggle(L10n.t("Night alerts"), isOn: alertBinding(\.night.on))
            }
            nightTime(L10n.t("Starts"), \.from, readOnly: readOnly)
            nightTime(L10n.t("Ends"), \.to, readOnly: readOnly)
            ForEach(AlertKind.allCases, id: \.self) { kind in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(Self.kindName(kind))
                        Text(Self.nightSummary(kind, n, unit: model.unit)).font(.callout).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(L10n.t("Details…")) { editingNight = kind }
                        .controlSize(.small)
                        .accessibilityLabel(L10n.t("\(Self.kindName(kind)) alert at night"))
                }
                .disabled(!n.on)
            }
        } header: {
            Text(L10n.t("Night alerts"))
        } footer: {
            Text(
                L10n.t(
                    "Other thresholds, reminders and alarms every night. Very low always alerts at night. The quiet times, the waits near a threshold, the notification's buttons and the sounds are the same day and night."
                )
            )
            .formFooter()
        }
    }

    /// A night hour as a time picker (minutes after local midnight in the settings).
    private func nightTime(_ label: String, _ path: WritableKeyPath<NightSettings, Int>, readOnly: Bool) -> some View {
        let minute = model.shownAlerts.night[keyPath: path]
        let binding = Binding<Date>(
            get: {
                Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
            },
            set: { d in
                let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                model.alerts.update { $0.night[keyPath: path] = (c.hour ?? 0) * 60 + (c.minute ?? 0) }
            })
        return DatePicker(label, selection: binding, displayedComponents: .hourAndMinute)
            .disabled(readOnly || !model.shownAlerts.night.on)
    }

    /// "Always on · up to 55 mg/dL · every 5 min", "Off" (one alert's night rule).
    static func nightSummary(_ kind: AlertKind, _ n: NightSettings, unit: GlucoseUnit) -> String {
        var s = AlertSettings()
        s[kind] = n[kind]
        s.sounds.kinds[kind.rawValue] = "none"
        let rule = summary(kind, s, unit: unit).replacingOccurrences(of: " · " + L10n.t("silent"), with: "")
        return kind == .veryLow ? L10n.t("Always on") + " · " + rule : rule
    }

    /// One alert: its switch, a summary of what applies, and Details….
    private func kindRow(_ kind: AlertKind) -> some View {
        let shown = model.shownAlerts[kind]
        return ToggleDetailsRow(
            title: Self.kindName(kind),
            summary: Self.summary(kind, model.shownAlerts, unit: model.unit),
            isOn: Binding(get: { shown.on }, set: { v in model.alerts.update { $0[kind].on = v } }),
            readOnlySource: model.alertsMirrored && kind != .noData ? model.mirrorSource : nil,
            detailsLabel: L10n.t("\(Self.kindName(kind)) alert details")
        ) { editing = kind }
    }

    /// One quiet rule: its switch, its length while on (*For 15 min*) or *Off*, and Details….
    private func quietRow(_ which: QuietRuleKind) -> some View {
        let rule = model.shownAlerts[keyPath: which.path]
        return ToggleDetailsRow(
            title: which.name,
            summary: which.summary(rule),
            isOn: Binding(get: { rule.on }, set: { v in model.alerts.update { $0[keyPath: which.path].on = v } }),
            readOnlySource: model.alertsMirrored ? model.mirrorSource : nil,
            detailsLabel: L10n.t("\(which.name) details")
        ) { editingQuiet = which }
    }

    /// "Below 70 mg/dL · every 5 min · Quest · like an alarm" (what applies; very low "Up to 55 mg/dL").
    static func summary(_ kind: AlertKind, _ s: AlertSettings, unit: GlucoseUnit) -> String {
        let r = s[kind]
        let when: String =
            switch kind.group {
            case .level:
                kind == .low
                    ? L10n.t("Below \(unit.format(Int(r.threshold))) \(unit.symbol)")
                    : kind == .veryLow
                        ? L10n.t("Up to \(unit.format(Int(r.threshold))) \(unit.symbol)")
                        : L10n.t("From \(unit.format(Int(r.threshold))) \(unit.symbol)")
            case .rate: L10n.t("Faster than \(unit.rateText(r.threshold)) \(unit.rateSymbol)")
            case .noData: L10n.t("After \(Durations.text(Int(r.threshold))) without data")
            }
        let repeats = r.repeatMin == 0 ? L10n.t("once") : L10n.t("every \(Durations.text(r.repeatMin))")
        let pack = s.soundPack(for: kind)
        return [
            r.on ? nil : L10n.t("Off"), when, repeats, pack == "none" ? L10n.t("silent") : packName(pack),
            r.alarm ? L10n.t("like an alarm") : nil,
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }

    static func openNotificationSettings() {
        let id = Bundle.main.bundleIdentifier ?? SukraraIDs.appBundleID
        if let url = URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=\(id)") {
            NSWorkspace.shared.open(url)
        }
    }

    private func alertBinding<V>(_ path: WritableKeyPath<AlertSettings, V>) -> Binding<V> {
        Binding(
            get: { model.alerts.settings[keyPath: path] }, set: { v in model.alerts.update { $0[keyPath: path] = v } })
    }

    static func kindName(_ k: AlertKind) -> String {
        switch k {
        case .veryLow: L10n.t("Very low")
        case .low: L10n.t("Low")
        case .high: L10n.t("High")
        case .veryHigh: L10n.t("Very high")
        case .extremelyHigh: L10n.t("Extremely high")
        case .fallingFast: L10n.t("Falling fast")
        case .risingFast: L10n.t("Rising fast")
        case .noData: L10n.t("No data")
        }
    }

    static func packName(_ p: String) -> String {
        switch p {
        case "system": L10n.t("System sound")
        case "none": L10n.t("No sound")
        default: p.capitalized
        }
    }
}

/// A switch with its own options, as System Settings lists one (General › Sharing: each service's switch with an Info
/// button whose sheet holds its options): the title, a summary of what is set, Details… and the switch. Never options
/// that appear under the switch. [readOnlySource]: a mirrored switch, shown in words (*On — from Marta*), not dimmed.
struct ToggleDetailsRow: View {
    let title: String
    let summary: String
    @Binding var isOn: Bool
    var readOnlySource: String? = nil
    let detailsLabel: String
    let details: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(summary).font(.callout).foregroundStyle(.secondary)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: summary)
            }
            Spacer()
            Button(L10n.t("Details…"), action: details)
                .controlSize(.small)
                .accessibilityLabel(detailsLabel)
            if let readOnlySource {
                ReadOnlyValue(value: ReadOnlyValue.onOff(isOn), source: readOnlySource, name: title)
            } else {
                Toggle(title, isOn: $isOn).labelsHidden().toggleStyle(.switch)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

/// The quiet rules (SPEC §2), in the pane's order: the two after a treatment, then the pair that follow the readings
/// back into range (their minutes are *Back in range within*).
enum QuietRuleKind: String, CaseIterable, Identifiable {
    case afterInsulin, afterCarbs, fallingHigh, risingLow

    var id: String { rawValue }

    var path: WritableKeyPath<AlertSettings, QuietRule> {
        switch self {
        case .afterInsulin: \.muteAfterInsulin
        case .afterCarbs: \.muteAfterCarbs
        case .fallingHigh: \.muteFallingHigh
        case .risingLow: \.muteRisingLow
        }
    }

    /// The schema id of its minutes.
    var minutesID: String {
        switch self {
        case .afterInsulin: "alerts.muteAfterInsulin.min"
        case .afterCarbs: "alerts.muteAfterCarbs.min"
        case .fallingHigh: "alerts.muteFallingHigh.min"
        case .risingLow: "alerts.muteRisingLow.min"
        }
    }

    var byTrend: Bool { self == .fallingHigh || self == .risingLow }

    var name: String {
        switch self {
        case .afterInsulin: L10n.t("Quiet after insulin")
        case .afterCarbs: L10n.t("Quiet after carbs")
        case .fallingHigh: L10n.t("Quiet highs falling back into range")
        case .risingLow: L10n.t("Quiet lows rising back into range")
        }
    }

    /// The minutes' label: how long after a treatment, or how soon it must be back in range.
    var minutesLabel: String { byTrend ? L10n.t("Back in range within") : L10n.t("How long") }

    /// What the rule does (the sheet's footer).
    var explanation: String {
        switch self {
        case .afterInsulin:
            L10n.t(
                "Rapid insulin you log keeps every high and rising fast alert quiet while it starts working. If glucose is still high after that, it alerts at once."
            )
        case .afterCarbs:
            L10n.t(
                "Carbs you log keep low and falling fast alerts quiet while they work, and very low too on the phone that gets your glucose. A phone or Mac that follows you still alerts on a very low."
            )
        case .fallingHigh:
            L10n.t(
                "While the last readings show glucose clearly falling back into range in time, high and very high alerts wait. They come at once if it stalls or the readings stop."
            )
        case .risingLow:
            L10n.t(
                "While the last readings show glucose clearly rising back into range in time, low alerts wait. They come at once if it stalls or the readings stop. A very low waits only on the phone that gets your glucose, never its first alert."
            )
        }
    }

    var format: NumberSpec? {
        SettingsSpec.settings.first { $0.id == minutesID }?.number.flatMap { SettingsSpec.numbers[$0] }
    }

    /// The row's summary: "For 1 h", "Back in range within 20 min", or "Off" (never the rule's name again).
    func summary(_ rule: QuietRule) -> String {
        guard rule.on else { return L10n.t("Off") }
        let length = Durations.text(Int(rule.min))
        return byTrend ? L10n.t("Back in range within \(length)") : L10n.t("For \(length)")
    }
}

/// The sheet behind a quiet rule's Details…: its switch, its minutes (greyed while off, as System Settings greys the
/// options of a service that is off) and what it does; read-only while the wearer's rules apply. Done or Esc closes it.
struct QuietDetails: View {
    @Bindable var model: SettingsModel
    let which: QuietRuleKind
    let done: () -> Void

    var body: some View {
        let rule = model.shownAlerts[keyPath: which.path]
        let readOnly = model.alertsMirrored
        VStack(spacing: 0) {
            Form {
                Section {
                    if readOnly {
                        LabeledContent(which.name) {
                            ReadOnlyValue(value: ReadOnlyValue.onOff(rule.on), source: model.mirrorSource)
                        }
                    } else {
                        Toggle(
                            which.name,
                            isOn: Binding(
                                get: { rule.on }, set: { v in model.alerts.update { $0[keyPath: which.path].on = v } }))
                    }
                    if let format = which.format {
                        NumberSettingRow(
                            label: which.minutesLabel, format: format,
                            value: Binding(
                                get: { rule.min },
                                set: { v in model.alerts.update { $0[keyPath: which.path].min = v } }),
                            readOnly: readOnly
                        )
                        .disabled(!rule.on && !readOnly)
                    }
                } footer: {
                    Text(which.explanation).formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: 300)
    }
}

/// The sheet behind Details…: when it alerts (threshold, repeat; read-only while the wearer's rules apply), for Falling
/// fast and Rising fast the trend arrow they read, its sound with ▶, Like an alarm, and Send Test Alert.
struct AlertDetails: View {
    @Bindable var model: SettingsModel
    let kind: AlertKind
    let controller: AlertController?
    let readOnly: Bool
    let done: () -> Void
    @State private var sent = false

    var body: some View {
        let shown = model.shownAlerts[kind]
        let name = AlertsRangesPane.kindName(kind)
        VStack(spacing: 0) {
            Form {
                Section {
                    if readOnly {
                        LabeledContent(L10n.t("\(name) alert")) {
                            ReadOnlyValue(value: ReadOnlyValue.onOff(shown.on), source: model.mirrorSource)
                        }
                    } else {
                        Toggle(
                            L10n.t("\(name) alert"),
                            isOn: Binding(get: { shown.on }, set: { v in model.alerts.update { $0[kind].on = v } }))
                    }
                    let id = "alerts.threshold.\(kind.rawValue)"
                    if let number = SettingsSpec.settings.first(where: { $0.id == id })?.number,
                        let format = SettingsSpec.numbers[number]
                    {
                        NumberSettingRow(
                            label: L10n.t("Threshold"), format: format,
                            value: Binding(
                                get: { shown.threshold }, set: { v in model.alerts.update { $0[kind].threshold = v } }),
                            readOnly: readOnly,
                            span: { v in
                                let rules = model.shownAlerts
                                return RangeSpans.text(
                                    RangeSpans.ofAlert(kind) { k in
                                        k == kind ? AlertRule(on: true, threshold: v, repeatMin: 0) : rules[k]
                                    }, unit: model.unit)
                            })
                    }
                    NumberSettingRow(
                        label: L10n.t("Repeat"), format: SettingsSpec.alertRepeat,
                        value: Binding(
                            get: { Double(shown.repeatMin) },
                            set: { v in model.alerts.update { $0[kind].repeatMin = Int(v) } }),
                        readOnly: readOnly)
                } footer: {
                    if readOnly {
                        Text(
                            L10n.t("When it alerts follows their rules. Choose Custom to change it on this Mac.")
                        )
                        .formFooter()
                    }
                }
                if kind.group == .rate { TrendSourceSection(model: model) }
                Section {
                    HStack {
                        Picker(
                            L10n.t("Sound"),
                            selection: Binding(
                                get: { model.alerts.settings.sounds.kinds[kind.rawValue] ?? "" },
                                set: { v in model.alerts.update { $0.sounds.kinds[kind.rawValue] = v.isEmpty ? nil : v }
                                })
                        ) {
                            Text(L10n.t("Same as the pack")).tag("")
                            ForEach(SettingsSpec.options["soundPack"] ?? [], id: \.self) {
                                Text(AlertsRangesPane.packName($0)).tag($0)
                            }
                        }
                        PreviewButton(pack: model.alerts.settings.soundPack(for: kind), kind: kind)
                    }
                    Toggle(
                        L10n.t("Like an alarm"),
                        isOn: Binding(
                            get: { model.alerts.settings[kind].alarm },
                            set: { v in model.alerts.update { $0[kind].alarm = v } }))
                    if let controller {
                        HStack {
                            if sent {
                                Label(L10n.t("Sent"), systemImage: "checkmark.circle").foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button(L10n.t("Send Test Alert")) {
                                controller.sendTest(kind)
                                sent = true
                            }
                        }
                    }
                } header: {
                    Text(L10n.t("Sound"))
                } footer: {
                    Text(
                        L10n.t(
                            "Like an alarm: Time Sensitive, so it shows and plays during a Focus when Sukrara is allowed to. The test alert is a real notification titled “Test”; nothing is muted or logged."
                        )
                    )
                    .formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: kind.group == .rate ? 620 : 520)
    }
}

/// Falling fast / Rising fast › Trend arrow: the same setting as Readings › Trend arrow (`SettingsModel.trend`, one
/// store, never a copy), read-only with where it comes from outside Custom.
private struct TrendSourceSection: View {
    @Bindable var model: SettingsModel

    var body: some View {
        Section {
            if model.editable {
                Picker(L10n.t("Trend from"), selection: $model.trend) {
                    ForEach(TrendSetting.allCases, id: \.self) { Text(TrendArrowSection.name($0)).tag($0) }
                }
            } else {
                LabeledContent(L10n.t("Trend from")) {
                    ReadOnlyValue(value: TrendArrowSection.name(model.trend), source: model.mirrorSource)
                }
            }
        } header: {
            Text(L10n.t("Trend arrow"))
        } footer: {
            Text(
                L10n.t(
                    "The same choice as Readings › Trend arrow: every arrow and spoken trend follow it too. \(TrendArrowSection.line(model.trend))"
                )
            )
            .formFooter()
        }
    }
}

/// The sheet behind a night alert's Details…: on (very low always), its threshold and repeat at night (read-only while
/// the wearer's rules apply) and Like an alarm.
private struct NightDetails: View {
    @Bindable var model: SettingsModel
    let kind: AlertKind
    let readOnly: Bool
    let done: () -> Void

    var body: some View {
        let shown = model.shownAlerts.night[kind]
        let name = AlertsRangesPane.kindName(kind)
        VStack(spacing: 0) {
            Form {
                Section {
                    if readOnly || kind == .veryLow {
                        LabeledContent(L10n.t("\(name) alert at night")) {
                            ReadOnlyValue(value: ReadOnlyValue.onOff(shown.on), source: model.mirrorSource)
                        }
                    } else {
                        Toggle(
                            L10n.t("\(name) alert at night"),
                            isOn: Binding(
                                get: { shown.on }, set: { v in model.alerts.update { $0.night[kind].on = v } }))
                    }
                    let id = "alerts.night.threshold.\(kind.rawValue)"
                    if let number = SettingsSpec.settings.first(where: { $0.id == id })?.number,
                        let format = SettingsSpec.numbers[number]
                    {
                        NumberSettingRow(
                            label: L10n.t("Threshold"), format: format,
                            value: Binding(
                                get: { shown.threshold },
                                set: { v in model.alerts.update { $0.night[kind].threshold = v } }),
                            readOnly: readOnly,
                            span: { v in
                                let night = model.shownAlerts.night
                                return RangeSpans.text(
                                    RangeSpans.ofAlert(kind) { k in
                                        k == kind ? AlertRule(on: true, threshold: v, repeatMin: 0) : night[k]
                                    }, unit: model.unit)
                            })
                    }
                    NumberSettingRow(
                        label: L10n.t("Repeat"), format: SettingsSpec.alertRepeat,
                        value: Binding(
                            get: { Double(shown.repeatMin) },
                            set: { v in model.alerts.update { $0.night[kind].repeatMin = Int(v) } }),
                        readOnly: readOnly)
                    Toggle(
                        L10n.t("Like an alarm"),
                        isOn: Binding(
                            get: { model.alerts.settings.night[kind].alarm },
                            set: { v in model.alerts.update { $0.night[kind].alarm = v } }))
                } footer: {
                    Text(L10n.t("Applies only between the night alerts' start and end."))
                        .formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: 380)
    }
}

/// Alerts › Fine-tuning… (docs/35): the rows rarely touched, out of the main pane: the waits and margin near a
/// threshold (SPEC §1 #37, mirrored: read-only outside Custom) and what the notification's buttons offer (this Mac's).
/// Closes with Done or Esc.
struct FineTuning: View {
    @Bindable var model: SettingsModel
    let done: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Form {
                Section {
                    waitRow(L10n.t("Low alerts wait"), SettingsSpec.persistLow, \.lowMin)
                    waitRow(L10n.t("High alerts wait"), SettingsSpec.persistHigh, \.highMin)
                    waitRow(L10n.t("Ignore bounces within"), SettingsSpec.persistMargin, \.marginMgdl)
                } header: {
                    Text(L10n.t("Near a threshold"))
                } footer: {
                    Text(
                        L10n.t(
                            "A low or a high alerts only once glucose has been out of range this long in a row. Very low and extremely high never wait. A bounce back into range smaller than the margin is the same episode: no new first alert, and its treatments still count."
                        )
                    )
                    .formFooter()
                }
                Section {
                    NumberSettingRow(
                        label: L10n.t("Mute first offers"), format: SettingsSpec.actionMute,
                        value: Binding(
                            get: { Double(model.alerts.settings.notificationActions.muteMin) },
                            set: { v in model.alerts.update { $0.notificationActions.muteMin = Int(v) } }))
                    Picker(
                        L10n.t("Remind less: every"),
                        selection: Binding(
                            get: { model.alerts.settings.notificationActions.relaxMin },
                            set: { v in model.alerts.update { $0.notificationActions.relaxMin = v } })
                    ) {
                        let choices = model.alerts.settings.relaxChoices
                        let current = model.alerts.settings.notificationActions.relaxMin
                        ForEach(choices.contains(current) ? choices : [current] + choices, id: \.self) {
                            Text(Durations.text($0)).tag($0)
                        }
                    }
                    NumberSettingRow(
                        label: L10n.t("Back in range by"), format: SettingsSpec.backMgdl,
                        value: Binding(
                            get: { Double(model.alerts.settings.notificationActions.backMgdl) },
                            set: { v in model.alerts.update { $0.notificationActions.backMgdl = Int(v) } }))
                    NumberSettingRow(
                        label: L10n.t("For at least"), format: SettingsSpec.backMin,
                        value: Binding(
                            get: { Double(model.alerts.settings.notificationActions.backMin) },
                            set: { v in model.alerts.update { $0.notificationActions.backMin = Int(v) } }))
                } header: {
                    Text(L10n.t("From the notification"))
                } footer: {
                    Text(
                        L10n.t(
                            "Mute and Remind less end once the value is back inside the threshold by this margin for this long."
                        )
                    )
                    .formFooter()
                }
            }
            .formStyle(.grouped)
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 480, height: 600)
    }

    /// "Waits near a threshold: lows Off, highs 15 min · Mute 30 min": what the sheet holds, on its row in the pane.
    static func summary(_ s: AlertSettings) -> String {
        let low = NumberSettingRow.word(SettingsSpec.persistLow, Double(s.persistence.lowMin))
        let high = NumberSettingRow.word(SettingsSpec.persistHigh, Double(s.persistence.highMin))
        let mute = NumberSettingRow.word(SettingsSpec.actionMute, Double(s.notificationActions.muteMin))
        return L10n.t("Waits near a threshold: lows \(low), highs \(high) · Mute \(mute)")
    }

    /// One row of *Near a threshold* (SPEC §1 #37): read-only while the wearer's rules apply.
    private func waitRow(
        _ label: String, _ format: NumberSpec, _ path: WritableKeyPath<PersistenceSettings, Int>
    ) -> some View {
        NumberSettingRow(
            label: label, format: format,
            value: Binding(
                get: { Double(model.shownAlerts.persistence[keyPath: path]) },
                set: { v in model.alerts.update { $0.persistence[keyPath: path] = Int(v) } }),
            readOnly: model.alertsMirrored)
    }

}

/// ▶: plays `pack`'s sound for `kind` once (the bundled file; the system sound for "system"; nothing for "none").
struct PreviewButton: View {
    let pack: String
    let kind: AlertKind

    var body: some View {
        Button {
            Self.play(pack, kind)
        } label: {
            Image(systemName: "play.fill")
        }
        .buttonStyle(.borderless)
        .disabled(pack == "none")
        .help(L10n.t("Play"))
        .accessibilityLabel(L10n.t("Play \(AlertsRangesPane.packName(pack))"))
    }

    static func play(_ pack: String, _ kind: AlertKind) {
        if pack == "system" {
            NSSound.beep()
            return
        }
        guard AlertSoundSettings.packs.contains(pack),
            let url = Bundle.main.resourceURL?.appending(path: "Sounds/\(pack)_\(kind.rawValue)_1.caf"),
            let sound = NSSound(contentsOf: url, byReference: true)
        else {
            NSSound.beep()
            return
        }
        sound.play()
    }
}

/// What is quiet right now, each with its ways out (the panel's blocks): Mute all, each side's mute, Remind less.
private struct ActiveNowSection: View {
    let alerts: AlertController

    var body: some View {
        let ui = alerts.ui
        let now = Date()
        if ui.muteAllUntil != nil || !ui.quiets.isEmpty {
            Section(L10n.t("Active now")) {
                if let until = ui.muteAllUntil, let line = MuteCopy.allLine(until: until, now: now) {
                    HStack {
                        Label(line, systemImage: "bell.slash.fill")
                        Spacer()
                        Button(L10n.t("Unmute")) { alerts.perform(.unmuteAll) }
                    }
                }
                ForEach(ui.quiets, id: \.kind) { q in
                    VStack(alignment: .leading, spacing: 4) {
                        if let reason = q.quiet, let line = MuteCopy.line(reason, side: q.side, now: now) {
                            Text(line)
                        }
                        if q.relaxMin > 0 { Text(MuteCopy.sideLess(q.side, intervalMin: q.relaxMin)) }
                        HStack { QuietMenu(alerts: alerts, side: q.side, now: now) }
                            .controlSize(.small)
                    }
                }
            }
        }
    }
}

extension AlertKind: Identifiable {
    public var id: String { rawValue }
}
