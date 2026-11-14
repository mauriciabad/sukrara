// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI

/// The settings window's frame, after the HIG (Settings, macOS): a noncustomizable preference-style toolbar of panes in
/// the unified title bar (the traffic lights in the same bar, no tab view inside the window), always showing the active
/// pane; the window's title is the pane's name; minimize and zoom dimmed; the window takes each pane's height keeping
/// its top edge (animated, not with Reduce Motion); the last pane shown comes back (`SettingsModel.pane`).
@MainActor final class SettingsWindowChrome: NSObject, NSToolbarDelegate {
    let model: SettingsModel
    private weak var window: NSWindow?
    /// Whether the window takes the pane's height (false for a render that sizes the window to the whole form).
    private let fitsPane: Bool

    /// Sets `window` up as the settings window; keep the result as long as the window lives (the toolbar's delegate).
    init(window: NSWindow, model: SettingsModel, fitsPane: Bool = true) {
        self.model = model
        self.window = window
        self.fitsPane = fitsPane
        super.init()
        window.styleMask = [.titled, .closable]  // HIG: dim the minimize and zoom buttons
        let toolbar = NSToolbar(identifier: "app.mauri.sukrara.settings")
        toolbar.delegate = self
        toolbar.allowsUserCustomization = false
        toolbar.displayMode = .iconAndLabel
        window.toolbar = toolbar
        window.toolbarStyle = .preference
        apply(animated: false)
        observe()
    }

    /// Follows `model.pane` (a toolbar click, a pane's *Change…*, a no-data reason's way out).
    private func observe() {
        withObservationTracking {
            _ = model.pane
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                apply(animated: true)
                observe()
            }
        }
    }

    private func apply(animated: Bool) {
        guard let window else { return }
        let pane = model.pane
        window.contentViewController?.title = pane.title  // a hosting window takes its controller's title
        window.title = pane.title
        window.toolbar?.selectedItemIdentifier = pane.id
        guard fitsPane else { return }
        let area = (window.screen ?? NSScreen.main)?.visibleFrame.height ?? 900
        let height = min(pane.height, area - 80)
        var frame = window.frameRect(forContentRect: NSRect(x: 0, y: 0, width: Self.width, height: height))
        frame.origin = NSPoint(x: window.frame.minX, y: window.frame.maxY - frame.height)  // the top edge stays
        guard frame != window.frame else { return }
        let motion = animated && window.isVisible && !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        window.setFrame(frame, display: true, animate: motion)
    }

    static let width: CGFloat = 620

    // MARK: NSToolbarDelegate

    func toolbar(
        _ toolbar: NSToolbar, itemForItemIdentifier id: NSToolbarItem.Identifier, willBeInsertedIntoToolbar flag: Bool
    ) -> NSToolbarItem? {
        guard let pane = SettingsModel.Pane(rawValue: id.rawValue) else { return nil }
        let item = NSToolbarItem(itemIdentifier: id)
        item.label = pane.title
        item.toolTip = pane.help
        item.image = NSImage(systemSymbolName: pane.symbol, accessibilityDescription: nil)
        item.target = self
        item.action = #selector(select(_:))
        return item
    }

    @objc private func select(_ sender: NSToolbarItem) {
        guard let pane = SettingsModel.Pane(rawValue: sender.itemIdentifier.rawValue), pane != model.pane else {
            return
        }
        model.pane = pane
    }

    func toolbarDefaultItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] {
        SettingsModel.Pane.allCases.map(\.id)
    }
    func toolbarAllowedItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] {
        SettingsModel.Pane.allCases.map(\.id)
    }
    func toolbarSelectableItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] {
        SettingsModel.Pane.allCases.map(\.id)
    }
}

extension SettingsModel.Pane {
    var id: NSToolbarItem.Identifier { .init(rawValue) }

    /// The toolbar button's label and the window's title while the pane shows.
    var title: String {
        switch self {
        case .general: L10n.t("General")
        case .graph: L10n.t("Graph")
        case .rings: L10n.t("Rings")
        case .alerts: L10n.t("Alerts")
        case .following: L10n.t("Following")
        }
    }

    /// The toolbar button's SF Symbol.
    var symbol: String {
        switch self {
        case .general: "gearshape"
        case .graph: "chart.xyaxis.line"
        case .rings: "circle.circle"
        case .alerts: "bell.badge"
        case .following: "person.2"
        }
    }

    /// The toolbar button's tooltip: what the pane holds.
    var help: String {
        switch self {
        case .general: L10n.t("Settings from, unit, glucose source, startup and updates")
        case .graph: L10n.t("Time shown, lines, ranges, treatments and the sensor on the graph")
        case .rings: L10n.t("Rings and numbers beside the value, ring order and the last treatment")
        case .alerts: L10n.t("When to alert, quiet rules, sounds and notifications")
        case .following: L10n.t("The person this Mac follows")
        }
    }

    /// The window's content height on this pane (the grouped form scrolls past it).
    var height: CGFloat {
        switch self {
        case .general: 640
        case .graph: 700
        case .rings: 680
        case .alerts: 720
        case .following: 440
        }
    }
}

extension View {
    /// A section footer's explanation as macOS grouped forms show it (System Settings): secondary, leading-aligned
    /// under the section (a grouped form's footer aligns its text to the trailing edge by default).
    func formFooter() -> some View {
        font(.callout).foregroundStyle(.secondary)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
