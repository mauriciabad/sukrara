// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

// The Mac's shared input controls: every text field, number field and dialog button row is one of these, so each kind
// of interaction looks and works the same everywhere (#181; `ControlsTests` keeps every other file off the raw
// controls).

/// THE text field of the Mac: no placeholder (the owner, #181: "remove all placeholders"; Apple's Human Interface
/// Guidelines, Text fields: a placeholder is no label, it disappears as soon as someone types). Its name is the label
/// the caller shows beside or above it, and what VoiceOver reads. `secure`: a password-style field. `content`: what it
/// holds for AutoFill (`.username`, `.password`: the Passwords app and password managers offer the saved sign-in).
struct TextInput: View {
    let label: String
    @Binding var text: String
    var secure = false
    var content: NSTextContentType? = nil

    var body: some View {
        Group {
            if secure {
                SecureField(text: $text) { EmptyView() }
            } else {
                TextField(text: $text) { EmptyView() }
            }
        }
        .textContentType(content)
        .textFieldStyle(.roundedBorder)
        .accessibilityLabel(label)
    }
}

/// THE number field of the Mac (Human Interface Guidelines, Text fields and Steppers): a field that keeps only what a
/// number can hold (`keep`), a stepper beside it and the unit after it; the Up and Down arrow keys step too. `text` is
/// what was typed (the forms check it as it is typed); `step` is what one click up (+1) or down (−1) does (`stepped`
/// for a field whose text is its value). `invalid` outlines it in red. `focus` / `focusValue`: the form's focus.
struct NumberField<Focus: Hashable>: View {
    let label: String
    @Binding var text: String
    let unit: String
    let step: (_ sign: Double) -> Void
    var keep: (String) -> String = { $0 }
    var invalid = false
    var width: CGFloat? = 70
    var submit: () -> Void = {}
    var focus: FocusState<Focus>.Binding?
    var focusValue: Focus?

    var body: some View {
        HStack(spacing: 4) {
            field
                .frame(width: width)
                .frame(maxWidth: width == nil ? .infinity : nil)
            Stepper("", onIncrement: { step(1) }, onDecrement: { step(-1) })
                .labelsHidden()
                .accessibilityLabel(L10n.t("Adjust \(label)"))
            if !unit.isEmpty { Text(unit).foregroundStyle(.secondary).accessibilityHidden(true) }
        }
    }

    @ViewBuilder private var field: some View {
        let input = TextInput(label: label, text: Binding(get: { text }, set: { text = keep($0) }))
            .monospacedDigit()
            .multilineTextAlignment(.trailing)
            .onSubmit(submit)
            .onKeyPress(.upArrow) {
                step(1)
                return .handled
            }
            .onKeyPress(.downArrow) {
                step(-1)
                return .handled
            }
            .overlay(RoundedRectangle(cornerRadius: 5).stroke(invalid ? Color.red : .clear))
        if let focus, let focusValue {
            input.focused(focus, equals: focusValue)
        } else {
            input
        }
    }

    /// `text` one step of `by` further (a negative `by` steps down) on the step's grid (4.3 + 0.5 → 4.5), kept in
    /// `range`, from the typed value (`,` or `.` as the decimal mark); from nothing, up gives one step, down the lowest
    /// value. Written with up to `decimals` decimals ("4", "4.5").
    static func stepped(_ text: String, by: Double, in range: ClosedRange<Double>, decimals: Int = 0) -> String {
        let s = abs(by)
        let typed = Double(text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: "."))
        let next: Double =
            if let typed {
                by > 0 ? ((typed / s + 1e-9).rounded(.down) + 1) * s : ((typed / s - 1e-9).rounded(.up) - 1) * s
            } else {
                by > 0 ? s : range.lowerBound
            }
        let k = pow(10, Double(decimals))
        let v = (min(max(next, range.lowerBound), range.upperBound) * k).rounded() / k
        return v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(format: "%.\(decimals)f", v)
    }
}

extension NumberField where Focus == Bool {
    init(
        label: String, text: Binding<String>, unit: String, step: @escaping (Double) -> Void,
        keep: @escaping (String) -> String = { $0 }, invalid: Bool = false, width: CGFloat? = 70,
        submit: @escaping () -> Void = {}
    ) {
        self.init(
            label: label, text: text, unit: unit, step: step, keep: keep, invalid: invalid, width: width,
            submit: submit, focus: nil, focusValue: nil)
    }
}

/// THE closing row of the Mac's sheets, popovers and small windows (Human Interface Guidelines, Buttons and Sheets):
/// anything `leading` on the left, then Cancel (Escape) and the action (Return: the default button, in the accent
/// colour), at the regular size: style, not size, marks the preferred choice. `returnConfirms` false: an action that a
/// reflex Return must not take (Follow someone) stays a plain button. Without `cancel` the row has only its action
/// (Done on a settings sheet), and Escape does it too; without `action`, only Cancel. `destructive`: the action deletes
/// or ends something (Stop Following): in red, and never the default button.
struct DialogButtons<Leading: View>: View {
    let action: String?
    var enabled = true
    var returnConfirms = true
    var destructive = false
    let cancel: (() -> Void)?
    let confirm: () -> Void
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        HStack {
            leading()
            Spacer()
            if let cancel {
                Button(L10n.t("Cancel"), role: .cancel, action: cancel).keyboardShortcut(.cancelAction)
            }
            if let action {
                Button(action, role: destructive ? .destructive : nil, action: confirm)
                    .keyboardShortcut(returnConfirms && !destructive ? .defaultAction : nil)
                    .disabled(!enabled)
            }
        }
        .controlSize(.regular)
        .background {
            if cancel == nil {
                // Escape closes a Done-only sheet as Done does
                Button("", action: confirm).keyboardShortcut(.cancelAction).opacity(0).frame(width: 0, height: 0)
                    .accessibilityHidden(true)
            }
        }
    }
}

extension DialogButtons where Leading == EmptyView {
    init(
        _ action: String?, enabled: Bool = true, returnConfirms: Bool = true, destructive: Bool = false,
        cancel: (() -> Void)?, confirm: @escaping () -> Void
    ) {
        self.init(
            action: action, enabled: enabled, returnConfirms: returnConfirms, destructive: destructive, cancel: cancel,
            confirm: confirm, leading: { EmptyView() })
    }
}

extension DialogButtons {
    init(
        _ action: String?, enabled: Bool = true, returnConfirms: Bool = true, cancel: (() -> Void)?,
        confirm: @escaping () -> Void, @ViewBuilder leading: @escaping () -> Leading
    ) {
        self.init(
            action: action, enabled: enabled, returnConfirms: returnConfirms, cancel: cancel, confirm: confirm,
            leading: leading)
    }
}

/// THE notice of the Mac (the owner, 2026-11-15: warnings that are alike must be one shared component; Android's
/// `NoticeCard`): every warning or error in a form, a window or the panel is a `Notice`, so they read the same
/// everywhere. The tone's symbol in its colour, the words at full contrast beside it (Human Interface Guidelines,
/// Color: never colour alone, text in the label colour), an optional explanation in the secondary colour under the
/// title, then its buttons. `ControlsTests` keeps every other file off the warning symbols.
struct Notice<Actions: View>: View {
    enum Tone {
        /// Something to fix; nothing has failed yet (a blocked feature, notifications off).
        case warning
        /// Something failed (settings that couldn't be read, a follow that didn't work).
        case error

        var symbol: String {
            switch self {
            case .warning: "exclamationmark.triangle.fill"
            case .error: "exclamationmark.octagon.fill"
            }
        }

        var color: Color {
            switch self {
            case .warning: .orange
            case .error: .red
            }
        }
    }

    let title: String
    var text: String? = nil
    var tone: Tone = .warning
    @ViewBuilder var actions: () -> Actions

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                    if let text {
                        Text(text).font(.caption).foregroundStyle(.secondary)
                    }
                }
            } icon: {
                Image(systemName: tone.symbol).foregroundStyle(tone.color)
            }
            .fixedSize(horizontal: false, vertical: true)
            actions()
        }
        .accessibilityElement(children: .contain)
    }
}

extension Notice where Actions == EmptyView {
    init(_ title: String, text: String? = nil, tone: Tone = .warning) {
        self.init(title: title, text: text, tone: tone) { EmptyView() }
    }
}

extension Notice {
    init(
        _ title: String, text: String? = nil, tone: Tone = .warning, @ViewBuilder actions: @escaping () -> Actions
    ) {
        self.init(title: title, text: text, tone: tone, actions: actions)
    }
}
