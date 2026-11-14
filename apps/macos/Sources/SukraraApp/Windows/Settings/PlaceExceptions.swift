// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SwiftUI

/// THE pattern for a setting with a default and per-place exceptions (#209, docs/38-place-overrides.md), the Mac's
/// side of Android's `ExceptionsRow`: the default is the setting's own control (the main action); one *Exceptions* row
/// under it names only the places that differ ("Widgets: 6 h", or "None") and its Edit… opens a sheet with a pop-up
/// per place whose first item is *Default (…)*. As macOS Notifications' per-app Show previews ("Default") and Safari's
/// Websites settings (configured websites, then "When visiting other websites").
enum PlaceExceptions {
    /// "Default (12 h)": a place that follows the default, with the default's value.
    static func defaultChoice(_ value: String) -> String { L10n.t("Default (\(value))") }

    /// What the Exceptions row says: the places with their own value, or "None".
    static func summary(_ places: [(name: String, own: String?)]) -> String {
        let own = places.compactMap { p in p.own.map { "\(p.name): \($0)" } }
        return own.isEmpty ? L10n.t("None") : own.joined(separator: ", ")
    }
}

/// The *Exceptions* row and its sheet: [title] names the setting (the sheet's section), [places] are its pop-ups.
struct ExceptionsRow<Places: View>: View {
    let title: String
    let summary: String
    @ViewBuilder var places: () -> Places
    @State private var editing = false

    var body: some View {
        LabeledContent(L10n.t("Exceptions")) {
            HStack(alignment: .firstTextBaseline) {
                Text(summary).foregroundStyle(.secondary)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: summary)
                Button(L10n.t("Edit…")) { editing = true }
                    .accessibilityLabel(L10n.t("Edit exceptions: \(title)"))
            }
        }
        .sheet(isPresented: $editing) {
            ExceptionsSheet(title: title, places: places) { editing = false }
        }
    }
}

/// The sheet behind the Exceptions row's Edit…: a pop-up per place under the setting's name; Done or Esc closes it
/// (every change applies at once, like the pane).
struct ExceptionsSheet<Places: View>: View {
    let title: String
    @ViewBuilder var places: () -> Places
    let done: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Form {
                Section(title) { places() }
            }
            .formStyle(.grouped)
            // Done, and Esc as Done
            DialogButtons(L10n.t("Done"), cancel: nil, confirm: done)
                .padding([.horizontal, .bottom], 20)
        }
        .frame(width: 440, height: 200)
    }
}

/// A place's switch with a default (HIG: a pop-up button for mutually exclusive choices): *Default (On)*, then On /
/// Off. nil = Default.
struct OverridePicker: View {
    let label: String
    let defaultOn: Bool
    @Binding var own: Bool?

    var body: some View {
        Picker(label, selection: $own) {
            Text(PlaceExceptions.defaultChoice(ReadOnlyValue.onOff(defaultOn))).tag(Bool?.none)
            Divider()
            Text(ReadOnlyValue.onOff(true)).tag(Bool?.some(true))
            Text(ReadOnlyValue.onOff(false)).tag(Bool?.some(false))
        }
    }
}
