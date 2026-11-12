// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SwiftUI

/// The glucose unit the settings show and take numbers in (`general.unit`): the settings and setup windows set it from
/// the person's setting; every `NumberSettingRow` of a glucose format follows it (contracts/settings/units.json).
extension EnvironmentValues {
    @Entry var glucoseUnit: GlucoseUnit = .mgdl
}

/// THE number input of the Mac (CLAUDE.md "Settings never diverge"): a pop-up of the format's presets and special
/// values plus "Custom…" where custom values are allowed; Custom shows a field + stepper + unit with the range under
/// it. Typed text that isn't in range on the step turns the note red and isn't applied. `readOnly` = a mirrored row.
/// A glucose format follows the environment's `glucoseUnit`: in mmol/L the row offers the schema's mmol/L variant
/// (its presets, range, step and stepper click) and stores what is picked or typed in mg/dL on the format's step.
struct NumberSettingRow: View {
    let label: String
    let format: NumberSpec
    @Binding var value: Double
    var readOnly = false
    /// What a value means, under the row ("Low: 56–69 mg/dL"): the value set, or while typing a valid one, that one.
    var span: ((Double) -> String?)? = nil
    @Environment(\.glucoseUnit) private var unit
    @State private var custom = false
    @State private var text = ""

    /// The format as shown and typed (itself, or its mmol/L variant) and the value in it.
    private var shown: NumberSpec { format.inUnit(unit) }
    private var shownValue: Double { format.shown(value, unit) }
    private var isCustom: Bool { custom || !(shown.isPreset(shownValue) || shown.specialOf(shownValue) != nil) }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if readOnly {
                // a mirrored value in words, never a dimmed pop-up (#106: faint in dark mode)
                LabeledContent(label) { ReadOnlyValue(value: Self.word(format, value, unit)) }
            } else {
                editor
            }
            if let line = span?(spanValue) {
                Text(line)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: line)
            }
        }
    }

    /// The value the span line speaks of: a valid value being typed, else the one set.
    private var spanValue: Double {
        guard !readOnly, isCustom, shown.custom, let v = shown.parse(text) else { return value }
        return format.stored(v, unit)
    }

    private var editor: some View {
        VStack(alignment: .leading, spacing: 4) {
            Picker(label, selection: choice) {
                ForEach(
                    shown.presets + shown.special.keys.filter { !shown.presets.contains($0) }.sorted(), id: \.self
                ) {
                    Text(Self.word(shown, $0)).tag(Optional($0))
                }
                if shown.custom { Text(L10n.t("Custom…")).tag(Double?.none) }
            }
            .accessibilityValue(Self.word(format, value, unit))
            if isCustom && shown.custom {
                HStack {
                    // no title beside it: in a Form it took a column of its own and broke as "Val-ue"
                    TextField(L10n.t("Value"), text: $text)  // settings-ok: number input
                        .labelsHidden()
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 70)
                        .onSubmit(commit)
                        .accessibilityLabel(label)
                    Stepper("", onIncrement: { nudge(1) }, onDecrement: { nudge(-1) })  // settings-ok: number input
                        .labelsHidden()
                        .accessibilityLabel(L10n.t("Adjust \(label)"))
                    Text(shown.unit).foregroundStyle(.secondary)
                }
                Text(L10n.t("Enter \(shown.text(shown.min))–\(shown.text(shown.max)) \(shown.unit)"))
                    .font(.caption)
                    .foregroundStyle(shown.parse(text) == nil ? .red : .secondary)
            }
        }
        .frame(minHeight: 24)
        .onAppear { text = shown.text(shownValue) }
        .onChange(of: value) { text = shown.text(shownValue) }
        .onChange(of: unit) { text = shown.text(shownValue) }
    }

    private var choice: Binding<Double?> {
        Binding(
            get: { isCustom ? nil : shownValue },
            set: { new in
                if let new {
                    custom = false
                    value = format.stored(new, unit)
                } else {
                    custom = true
                }
            })
    }

    private func commit() {
        if let v = shown.parse(text) { value = format.stored(v, unit) }
    }

    private func nudge(_ sign: Double) {
        value = format.stored(shown.coerce(shownValue + sign * shown.nudge), unit)
    }

    /// A stored value as the row says it in `unit`: "70 mg/dL", "3.9 mmol/L", "Off", "Only once", "1 h".
    static func word(_ f: NumberSpec, _ v: Double, _ unit: GlucoseUnit) -> String {
        word(f.inUnit(unit), f.shown(v, unit))
    }

    /// A value of `f` in its own units: "70 mg/dL", "Off", "Only once", "1 h".
    static func word(_ f: NumberSpec, _ v: Double) -> String {
        switch f.specialOf(v) {
        case "off": L10n.t("Off")
        case "once": L10n.t("Only once")
        case "none": L10n.t("None")
        case "until_turned_off": L10n.t("Until you turn it off")
        case "same_as_default": L10n.t("Same as default")
        default: f.unit.isEmpty ? f.text(v) : "\(f.text(v)) \(f.unit)"
        }
    }
}

/// A setting this Mac shows but doesn't decide (the wearer's or Sukrara's default, read-only outside Custom), in words
/// at full contrast rather than a dimmed control (#106: disabled switches couldn't be told on from off in dark mode):
/// "On — from Marta", "70 mg/dL". VoiceOver reads it as one value.
struct ReadOnlyValue: View {
    let value: String
    /// Where it comes from ("from Marta", "Sukrara's default"); nil = said elsewhere (the section's footer).
    var source: String? = nil
    /// What it is, for VoiceOver where no label stands beside it ("Low" in the Alerts list).
    var name: String? = nil

    var body: some View {
        Group {
            if let source, !source.isEmpty {
                Text("\(Text(value).fontWeight(.medium)) \(Text("— \(source)").foregroundStyle(.secondary))")
            } else {
                Text(value).fontWeight(.medium)
            }
        }
        .foregroundStyle(.primary)
        .multilineTextAlignment(.trailing)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text([name ?? "", value, source ?? ""].filter { !$0.isEmpty }.joined(separator: ", ")))
    }

    /// "On" / "Off" for a mirrored switch.
    static func onOff(_ on: Bool) -> String { on ? L10n.t("On") : L10n.t("Off") }
}
