// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SwiftUI

/// THE pattern for a setting the menu bar sets that the widgets can change (#209, docs/38-place-overrides.md), the
/// Mac's side of Android's place rows: one row in the pane (`PlaceSettingRow`) opens the setting's page
/// (`SettingPage`, as System Settings › Accessibility › Display › Text size) with *Menu bar* (the shared value) and
/// *Widgets*, a pop-up whose first item is *Same as the menu bar (…)* and which is quiet while it follows
/// (`PlacePopUp`).
enum PlaceSummary {
    /// The line under the setting's row: the widgets' value where it differs from the menu bar's ("6 h on widgets",
    /// "Off on widgets"); nil = nothing differs (they follow, or their own value is the menu bar's).
    static func line<V: Equatable>(menuBar: V, widgets: V?, word: (V) -> String) -> String? {
        guard let widgets, widgets != menuBar else { return nil }
        return L10n.t("\(word(widgets)) on widgets")
    }

    /// "Same as the menu bar (12 h)": the widgets' pop-up's first item.
    static func sameAsMenuBar(_ value: String) -> String { L10n.t("Same as the menu bar (\(value))") }

    /// What VoiceOver says of a pop-up that follows: "12 hours, same as the menu bar".
    static func spokenFollowing(_ value: String) -> String { L10n.t("\(value), same as the menu bar") }
}

/// The setting's row in the pane: its title, the line under it when the widgets differ, the menu bar's value on the
/// trailing side and a chevron; clicking it opens the setting's page.
struct PlaceSettingRow: View {
    let title: String
    /// The menu bar's value in words ("12 h", "On") and as VoiceOver says it ("12 hours").
    let value: String
    let spokenValue: String
    /// `PlaceSummary.line`, written and spoken.
    let summary: String?
    let spokenSummary: String?
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                    if let summary {
                        Text(summary)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .transition(.opacity)
                    }
                }
                Spacer(minLength: 8)
                Text(value)
                    .foregroundStyle(.secondary)
                    .contentTransition(.numericText())
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .frame(minHeight: 22)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.snappy, value: summary)
        .animation(.snappy, value: value)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue([spokenValue, spokenSummary].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
    }
}

/// A setting's page inside the pane: a back button and the page's title at the top (System Settings), then its
/// grouped form. ⌘[ and Esc go back too. Drawn by the pane in its own place rather than in a NavigationStack: the
/// Settings window's toolbar is AppKit's preference toolbar of panes, where SwiftUI's back button has no place.
struct SettingPage<Rows: View>: View {
    let title: String
    /// The explanation under the page's rows.
    let footer: String
    let back: () -> Void
    @ViewBuilder var rows: () -> Rows
    @AccessibilityFocusState private var titleFocused: Bool

    var body: some View {
        // the header is the section's, so it sits on the form's own background
        Form {
            Section {
                rows()
            } header: {
                header.padding(.bottom, 8)
            } footer: {
                Text(footer).formFooter()
            }
        }
        .formStyle(.grouped)
        .onAppear { titleFocused = true }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Button(action: back) {
                Image(systemName: "chevron.left")
                    .fontWeight(.semibold)
                    .frame(width: 14, height: 16)
            }
            .keyboardShortcut("[", modifiers: .command)
            .help(L10n.t("Back"))
            .accessibilityLabel(L10n.t("Back"))
            Text(title)
                .font(.title3.weight(.semibold))
                .accessibilityAddTraits(.isHeader)
                .accessibilityFocused($titleFocused)
            Spacer()
            // Esc goes back as well (a navigation key, not a dialog's Cancel)
            Button("", action: back).keyboardShortcut(.escape, modifiers: []).opacity(0).frame(width: 0, height: 0)
                .accessibilityHidden(true)
        }
        .foregroundStyle(.primary)
    }
}

/// The widgets' pop-up: while it follows the menu bar its closed label is quiet (the followed value alone, secondary,
/// borderless), so the menu bar's control stays the prominent one; with its own value it is a normal pop-up. The same
/// menu either way: `items` (*Same as the menu bar (…)* first, then the values) bound to `selection`.
struct PlacePopUp<V: Hashable, Items: View>: View {
    let label: String
    let selection: Binding<V>
    let following: Bool
    /// The value it follows, in words ("12 h"), and as VoiceOver says the pop-up's value ("12 hours" while it follows,
    /// "6 hours").
    let followed: String
    let spokenValue: String
    @ViewBuilder var items: () -> Items

    var body: some View {
        if following {
            LabeledContent {
                Menu {
                    Picker(selection: selection, content: items) { EmptyView() }
                        .pickerStyle(.inline)
                        .labelsHidden()
                } label: {
                    // drawn by SwiftUI (a plain button style), so it can be secondary: the value and the pop-up's
                    // chevrons, no bezel
                    HStack(spacing: 4) {
                        Text(followed)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2.weight(.semibold))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.trailing, 4)
                    .contentShape(Rectangle())
                }
                .menuStyle(.button)
                .buttonStyle(.plain)
                .menuIndicator(.hidden)
                .fixedSize()
                .accessibilityLabel(label)
                .accessibilityValue(PlaceSummary.spokenFollowing(spokenValue))
            } label: {
                Text(label).accessibilityHidden(true)
            }
        } else {
            Picker(label, selection: selection, content: items)
                .accessibilityValue(spokenValue)
        }
    }
}

/// The widgets' pop-up of a switch: *Same as the menu bar (On)*, then On / Off. nil = Same.
struct SwitchPlacePopUp: View {
    let label: String
    /// The menu bar's switch.
    let menuBar: Bool
    @Binding var own: Bool?

    var body: some View {
        let followed = ReadOnlyValue.onOff(menuBar)
        PlacePopUp(
            label: label, selection: $own, following: own == nil, followed: followed,
            spokenValue: own.map(ReadOnlyValue.onOff) ?? followed
        ) {
            Text(PlaceSummary.sameAsMenuBar(followed)).tag(Bool?.none)
            Divider()
            Text(ReadOnlyValue.onOff(true)).tag(Bool?.some(true))
            Text(ReadOnlyValue.onOff(false)).tag(Bool?.some(false))
        }
    }
}
