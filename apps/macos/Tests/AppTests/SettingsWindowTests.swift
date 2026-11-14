// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Testing

@testable import SukraraApp

/// The settings window's frame after the HIG (#181): a preference toolbar of panes in the title bar, the pane's name as
/// the title, minimize and zoom dimmed, the toolbar following the pane however it changes; and About Sukrara.
@Suite struct SettingsWindowTests {
    func model() -> SettingsModel {
        SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil, macName: "Mac"),
            version: "0")
    }

    @Test func everyPaneHasItsOwnNameAndASystemSymbol() {
        let panes = SettingsModel.Pane.allCases
        #expect(Set(panes.map(\.title)).count == panes.count)
        #expect(Set(panes.map(\.symbol)).count == panes.count)
        for pane in panes {
            #expect(NSImage(systemSymbolName: pane.symbol, accessibilityDescription: nil) != nil, "\(pane.symbol)")
            #expect(!pane.help.isEmpty)
            #expect(pane.height >= 360 && pane.height <= 760)
        }
    }

    @Test func theWindowHasAPreferenceToolbarAndThePanesName() {
        let m = model()
        m.pane = .rings
        let w = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 400, height: 300), styleMask: [], backing: .buffered, defer: true)
        let chrome = SettingsWindowChrome(window: w, model: m)
        #expect(w.toolbarStyle == .preference)
        #expect(w.toolbar?.allowsUserCustomization == false)
        #expect(w.toolbar?.items.map(\.itemIdentifier.rawValue) == SettingsModel.Pane.allCases.map(\.rawValue))
        #expect(w.toolbar?.selectedItemIdentifier?.rawValue == "rings")
        #expect(w.title == SettingsModel.Pane.rings.title)
        #expect(w.styleMask.contains(.closable))
        #expect(!w.styleMask.contains(.miniaturizable) && !w.styleMask.contains(.resizable))
        #expect(w.contentRect(forFrameRect: w.frame).width == SettingsWindowChrome.width)
        _ = chrome
    }

    @Test func theToolbarFollowsThePane() async {
        let m = model()
        m.pane = .general
        let w = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 400, height: 300), styleMask: [], backing: .buffered, defer: true)
        let chrome = SettingsWindowChrome(window: w, model: m)
        m.pane = .following  // a no-data reason's way out, a pane's Change…
        for _ in 0..<20 where w.title != SettingsModel.Pane.following.title { await Task.yield() }
        #expect(w.title == SettingsModel.Pane.following.title)
        #expect(w.toolbar?.selectedItemIdentifier?.rawValue == "following")
        #expect(w.contentRect(forFrameRect: w.frame).height == SettingsModel.Pane.following.height)
        _ = chrome
    }

    @Test func aboutWritesTheVersionLikeTheStandardPanel() {
        #expect(AboutView.versionLine("0.14.5", "14005") == L10n.t("Version \("0.14.5") (\("14005"))"))
        #expect(AboutView.versionLine("dev", "") == L10n.t("Version \("dev")"))
        #expect(AboutView.versionLine("0.14.5", "0.14.5") == L10n.t("Version \("0.14.5")"))
        #expect(AboutView.distributionName(.appStore) == L10n.t("App Store version"))
        #expect(AboutView.privacyURL.absoluteString == "https://sukrara.mauri.app/privacy")
    }

    @Test func theRightClickMenuOpensAbout() {
        let menu = ContextMenu()
        #expect(menu.menu.items.first { $0.title == L10n.t("About Sukrara") }?.isEnabled == false)
        menu.openAbout = {}
        #expect(menu.menu.items.first { $0.title == L10n.t("About Sukrara") }?.isEnabled == true)
    }
}
