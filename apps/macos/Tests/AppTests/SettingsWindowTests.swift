// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraCore
import SukraraGraph
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

    /// docs/35 §7: General first (HIG), Following second (where this Mac gets glucose), then Alerts, Readings, Graph,
    /// Metrics; the schema's `settings.pane` values are this order (SettingsSchemaTests).
    @Test func thePanesComeInTheirOrder() {
        #expect(SettingsModel.Pane.allCases == [.general, .source, .alerts, .readings, .graph, .metrics])
        #expect(SettingsModel.Pane.readings.symbol == "drop")
    }

    @Test func anUnknownStoredPaneOpensGeneral() {
        let store = scratchStore()
        store.set("ranges", for: MacSettings.settingsPane)  // a pane that no longer exists
        let m = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil, macName: "Mac"), version: "0")
        #expect(m.pane == .general)
        m.pane = .readings
        let again = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil, macName: "Mac"), version: "0")
        #expect(again.pane == .readings)  // the window reopens on the last pane
    }

    /// The links between panes (docs/35 §7) land where the setting is; the trend in Alerts is Readings' own setting.
    @Test func theTrendInAlertsIsReadingsSetting() {
        let m = model()
        m.settingsFrom = .custom
        m.trend = .smooth
        #expect(m.graph.trend == .smooth)
        #expect(m.store.value(MacSettings.trend) == TrendSetting.smooth.rawValue)
    }

    @Test func theWindowHasAPreferenceToolbarAndThePanesName() {
        let m = model()
        m.pane = .metrics
        let w = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 400, height: 300), styleMask: [], backing: .buffered, defer: true)
        let chrome = SettingsWindowChrome(window: w, model: m)
        #expect(w.toolbarStyle == .preference)
        #expect(w.toolbar?.allowsUserCustomization == false)
        #expect(w.toolbar?.items.map(\.itemIdentifier.rawValue) == SettingsModel.Pane.allCases.map(\.rawValue))
        #expect(w.toolbar?.selectedItemIdentifier?.rawValue == "metrics")
        #expect(w.title == SettingsModel.Pane.metrics.title)
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
        m.pane = .source  // a no-data reason's way out, a pane's Change…
        for _ in 0..<20 where w.title != SettingsModel.Pane.source.title { await Task.yield() }
        #expect(w.title == SettingsModel.Pane.source.title)
        #expect(w.toolbar?.selectedItemIdentifier?.rawValue == "source")
        // The pane's height, or less on a short screen (a CI runner's): the window keeps 80 pt free
        let area = (w.screen ?? NSScreen.main)?.visibleFrame.height ?? 900
        #expect(w.contentRect(forFrameRect: w.frame).height == min(SettingsModel.Pane.source.height, area - 80))
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

    /// Appendix G §4 (the owner's Q3; Android `MetricNeedTest`): a metric under the card's 25 % says why in Settings ›
    /// Metrics, in hours up to three days, then days; enough (or unknown) says nothing.
    @Test func aMetricHiddenForWantOfReadingsSaysWhy() throws {
        let week: TimeInterval = 7 * 86_400
        let now: TimeInterval = 1_790_000_000
        // 12 h of readings every 5 minutes: the 7-day metric is hidden, the 12-hour one isn't
        let twelve = stride(from: now - 12 * 3600 + 300, through: now, by: 300).map { GraphReading(t: $0, v: 120) }
        var config = GraphConfig()
        config.slots[0].window = week
        config.slots[1].window = 43_200
        let slots = SlotNumbers.of(twelve, now: now, configs: [config])
        let n = try #require(slots.need(week))
        #expect(abs(n.have - 12 * 3600) < 600 && n.need == 42 * 3600)
        #expect(slots.need(43_200) == nil)
        #expect(
            MetricNeed(have: n.have, window: week, need: n.need).text
                == "Needs more readings: 12 h of the last 7 days, 42 h needed")
        let month: TimeInterval = 30 * 86_400
        #expect(
            MetricNeed(have: 0.2 * month, window: month, need: 0.25 * month).text
                == "Needs more readings: 6 days of the last 30 days, 8 days needed")
        #expect(
            MetricNeed(have: 0, window: 43_200, need: 10_800).text
                == "Needs more readings: 0 min of the last 12 h, 3 h needed")
    }
}
