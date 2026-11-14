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
    /// A place that can follow the menu bar (docs/38): the menu bar's value. The format's `same_as_app` value is then the
    /// first item, "Same as the menu bar (12 h)", above a separator, and while it's chosen the closed pop-up is quiet
    /// (`PlacePopUp`).
    var follows: Double? = nil
    /// Where a read-only value comes from ("from Marta"), beside it.
    var source: String? = nil
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
                LabeledContent(label) { ReadOnlyValue(value: Self.word(format, value, unit), source: source) }
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
            if let follows {
                let followed = Self.word(shown, follows)
                PlacePopUp(
                    label: label, selection: choice, following: !isCustom && shownValue == same,
                    followed: followed,
                    spokenValue: Self.spoken(shown, isCustom || shownValue != same ? value : follows)
                ) {
                    if let same {
                        Text(PlaceSummary.sameAsMenuBar(followed)).tag(Optional(same))
                        Divider()
                    }
                    items
                }
            } else {
                Picker(label, selection: choice) { items }
                    .accessibilityValue(Self.spoken(format, value, unit))
            }
            if isCustom && shown.custom {
                NumberField(label: label, text: $text, unit: shown.unit, step: nudge, submit: commit)
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

    /// The format's presets and special values (the place's `same_as_app` aside), then Custom… (after a separator in a
    /// place's pop-up).
    @ViewBuilder private var items: some View {
        ForEach(
            shown.presets
                + shown.special.keys.filter { !shown.presets.contains($0) && $0 != same }.sorted(), id: \.self
        ) {
            Text(Self.word(shown, $0)).tag(Optional($0))
        }
        if shown.custom {
            if follows != nil { Divider() }
            Text(L10n.t("Custom…")).tag(Double?.none)
        }
    }

    /// A place's "Same as the menu bar" value (the format's `same_as_app`); nil for a row that follows nothing.
    private var same: Double? {
        follows == nil ? nil : shown.special.first(where: { $0.value == "same_as_app" })?.key
    }

    private var choice: Binding<Double?> {
        Binding(
            get: { isCustom ? nil : shownValue },
            set: { new in
                if let new {
                    custom = false
                    value = format.stored(new, unit)
                } else {
                    // a place that followed starts its own from the value it followed
                    if let follows, shownValue == same { text = shown.text(follows) }
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
        default: f.unit.isEmpty ? f.text(v) : "\(f.text(v)) \(f.unit)"
        }
    }

    /// A stored value as VoiceOver says it in `unit`: hours in full ("12 hours"), the rest as `word`.
    static func spoken(_ f: NumberSpec, _ v: Double, _ unit: GlucoseUnit) -> String {
        spoken(f.inUnit(unit), f.shown(v, unit))
    }

    /// A value of `f` in its own units as VoiceOver says it: "12 hours", "70 mg/dL", "Off".
    static func spoken(_ f: NumberSpec, _ v: Double) -> String {
        guard f.unit == "h", f.specialOf(v) == nil else { return word(f, v) }
        return Measurement(value: v, unit: UnitDuration.hours)
            .formatted(.measurement(width: .wide, usage: .asProvided).locale(L10n.locale))
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
