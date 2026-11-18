// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation
import SukraraAlerts
import SukraraCore
import SukraraGraph

/// The settings window's state over the store: the panes, "Settings from", the card settings, the unit, the wearer's
/// mirrored values. Every change applies at once; the app picks it up through the defaults change.
@Observable
public final class SettingsModel {
    /// The panes in toolbar order (schema `settings.pane`'s values); an unknown stored one opens General.
    public enum Pane: String, CaseIterable, Sendable { case general, source, alerts, readings, graph, metrics }

    @ObservationIgnored let store: any SettingsStoring
    public let alerts: AlertSettingsStore
    public let follow: FollowModel
    /// Juggluco on this network (Settings › Source).
    public let juggluco: JugglucoModel
    /// A Nightscout site (Settings › Source, setup's Connect).
    public let nightscout: NightscoutModel
    /// LibreLinkUp (Settings › Source, setup's Connect).
    public let libreLinkUp: LibreLinkUpModel
    /// Whose glucose and where it comes from (Settings › Source, setup's two steps).
    public let source: SourceChoiceModel
    /// The sensor's start and wear length of a source that doesn't say them (Settings › Source › *Sensor*).
    public let sensorLife: SensorLifeModel
    /// The wearer's mirrored settings (nil while not following or none arrived yet).
    public var wearer: WearerSettings?
    /// Whether macOS lets Sukrara post Time Sensitive (Settings › Alerts, the setup's notification step); nil: this run
    /// posts no notifications.
    var timeSensitive: TimeSensitive?
    /// Where Sukrara stands with a Focus (#184); nil: this run posts no notifications.
    var focusStatus: FocusStatus? { timeSensitive.map { FocusStatus(through: throughFocus, timeSensitive: $0) } }
    /// The pane shown; the window reopens on it.
    public var pane: Pane {
        didSet {
            store.set(pane.rawValue, for: MacSettings.settingsPane)
            graphPage = nil  // a pane opens at its top level
        }
    }
    /// A setting's page the Graph pane shows in its place (its row's chevron; back: ⌘[ / Esc); nil = the pane.
    public var graphPage: GraphPage?
    /// The Graph pane's settings with a page of their own: the menu bar's value and the widgets' (docs/38).
    public enum GraphPage: Sendable { case hours, sensorTime }
    /// "Copy the wearer's settings…", "Reset to Defaults…" and "Reset all graph settings…" are asked first.
    public var confirmingCopy = false
    public var confirmingReset = false
    public var confirmingResetAll = false
    public let version: String
    /// Which version this is (Settings › General › About says so).
    public let distribution: Distribution
    /// A release or a development build (the version's text says which).
    public let appBuild: AppBuild
    private var revision = 0

    public init(
        store: any SettingsStoring, follow: FollowModel, wearer: WearerSettings? = nil, version: String,
        distribution: Distribution = .github, appBuild: AppBuild = .release
    ) {
        self.store = store
        self.alerts = AlertSettingsStore(store: store)
        self.follow = follow
        self.juggluco = JugglucoModel(store: store)
        self.nightscout = NightscoutModel(store: store)
        self.libreLinkUp = LibreLinkUpModel(store: store)
        self.source = SourceChoiceModel(store: store)
        self.sensorLife = SensorLifeModel(store: store)
        self.wearer = wearer
        self.version = version
        self.distribution = distribution
        self.appBuild = appBuild
        pane = Pane(rawValue: store.value(MacSettings.settingsPane)) ?? .general
    }

    // MARK: Settings from

    /// Rows mirror the wearer (read-only) while following in "Settings from: Wearer".
    public var mirrors: Bool { settingsFrom == .wearer && wearer != nil }
    /// The card settings are this Mac's own and editable (Custom); otherwise the panes show what's in use, read-only.
    public var editable: Bool { settingsFrom == .custom }

    public var settingsFrom: SettingsFrom {
        get { _ = revision; return store.settingsFrom }
        set {
            store.set(newValue.rawValue, for: MacSettings.settingsFrom)
            // `alerts.source` is derived from `follower.settingsFrom` (schema): wearer = mirror, else own rules.
            let source: AlertSource = newValue == .wearer ? .wearer : .own
            if alerts.settings.source != source { alerts.update { $0.source = source } }
            revision += 1
        }
    }

    /// "Use my own settings": this Mac's values, editable.
    public func useOwn() { settingsFrom = .custom }

    /// "Copy the wearer's settings" (after the confirmation): this Mac's own card settings and alert rules become the
    /// wearer's mirrored ones, as a starting point, and Custom applies them. Sounds and what isn't mirrored stay.
    public func copyWearer() {
        guard let wearer else { return }
        let g = GraphConfig.load(from: WearerOnly(), wearer: wearer)
        writeGraph(g)
        let copied = alerts.settings.withMirroredDefaults().mirroring(wearer)
        alerts.update { $0 = copied }
        settingsFrom = .custom
    }

    /// Makes the settings in use this Mac's own and applies them (Custom): the wearer's, as "Copy the wearer's
    /// settings" does, or Sukrara's defaults. Nothing on screen changes; from then on a change to a mirrored value made
    /// outside the Settings window (setup's alert step) applies. Already Custom: nothing to do.
    public func useInUseAsOwn() {
        switch settingsFrom {
        case .custom: return
        case .wearer where wearer != nil: copyWearer()
        case .wearer:
            writeGraph(GraphConfig())
            let defaults = alerts.settings.withMirroredDefaults()
            alerts.update { $0 = defaults }
            settingsFrom = .custom
        }
    }

    /// "Reset to Defaults" (after the confirmation, Custom only): this Mac's own mirrored settings (ranges, graph,
    /// metrics, lines, last treatment, sensor countdown, alert rules and quiet rules) become Sukrara's defaults. Sounds,
    /// the no-data alert, Mute all, the notification actions and each place's own time stay.
    public func resetToDefaults() {
        guard editable else { return }
        writeGraph(GraphConfig())
        let reset = alerts.settings.withMirroredDefaults()
        alerts.update { $0 = reset }
    }

    /// Who the mirrored settings come from now, in one sentence (General › Settings › In use).
    public var inUse: String {
        switch settingsFrom {
        case .custom: return L10n.t("This Mac's own settings, in the Alerts, Readings, Graph and Metrics panes.")
        case .wearer:
            let who = wearer?.wearer ?? L10n.t("this person")
            if wearer != nil { return L10n.t("\(who)'s settings from their phone.") }
            guard case .following = follow.status else {
                return L10n.t("Not following anyone, so Sukrara's defaults are used.")
            }
            return L10n.t("Waiting for \(who)'s settings. Sukrara's defaults are used until they arrive.")
        }
    }

    /// Where a read-only (mirrored) value comes from, said after it: "from Marta" while the wearer's settings apply,
    /// "Sukrara's default" before their settings arrived. Empty in This Mac's own (editable).
    public var mirrorSource: String {
        switch settingsFrom {
        case .custom: return ""
        case .wearer where wearer != nil: return L10n.t("from \(wearer?.wearer ?? L10n.t("this person"))")
        case .wearer: return L10n.t("Sukrara's default")
        }
    }

    // MARK: card settings

    /// The card settings in use (the menu bar's; each place may show its own time).
    public var graph: GraphConfig {
        _ = revision
        return GraphConfig.load(from: store, wearer: wearer)
    }

    /// *Trend arrow* in use (the wearer's, the default or this Mac's, like the other mirrored settings); set in Custom.
    public var trend: TrendSetting {
        get { graph.trend }
        set { updateGraph { $0.trend = newValue } }
    }

    /// The newest reading the app shows, with its trend (Readings › Trend arrow's preview); nil = none.
    @ObservationIgnored public var newestReading: () -> Reading? = { nil }

    public var unit: GlucoseUnit {
        get { _ = revision; return store.unit }
        set { store.set(newValue.rawValue, for: MacSettings.unit); revision += 1 }
    }

    /// Read Glucose Aloud from any app with its keys (schema `general.readAloudShortcut`); the app follows the change.
    public var readAloudShortcut: Bool {
        get { _ = revision; return store.value(MacSettings.readAloudShortcut) }
        set { store.set(newValue, for: MacSettings.readAloudShortcut); revision += 1 }
    }

    /// Let Sukrara through a Focus (schema `alerts.throughFocus`); the notifier reads it at each post.
    public var throughFocus: Bool {
        get { _ = revision; return store.value(MacSettings.throughFocus) }
        set { store.set(newValue, for: MacSettings.throughFocus); revision += 1 }
    }

    /// The shown value of a number setting: this Mac's in Custom, else the source's (`GraphSource`).
    public func number(_ n: MacSettings.Number) -> Double {
        _ = revision
        return GraphSource(store: store, wearer: wearer).number(n)
    }

    public func setNumber(_ v: Double, _ n: MacSettings.Number) {
        guard editable else { return }
        if store.setNumber(v, for: n) { revision += 1 }
    }

    /// A place's own time shown (0 = Same as the menu bar): always this Mac's (docs/38).
    public func placeHours(_ p: GraphPlace) -> Double { _ = revision; return store.number(p.hours) }
    public func setPlaceHours(_ v: Double, _ p: GraphPlace) { if store.setNumber(v, for: p.hours) { revision += 1 } }
    /// The sensor's time left on the menu bar's card, which every place follows; always this Mac's.
    public var sensorTime: Bool {
        get { _ = revision; return store.value(MacSettings.sensorTime) }
        set { store.set(newValue, for: MacSettings.sensorTime); revision += 1 }
    }
    /// A place's own sensor time left (nil = Same as the menu bar).
    public func sensorPlace(_ p: GraphPlace) -> Bool? {
        _ = revision; return PlaceOverride(stored: store.value(p.sensorTime)).own
    }
    public func setSensorPlace(_ own: Bool?, _ p: GraphPlace) {
        store.set(PlaceOverride(own: own).rawValue, for: p.sensorTime)
        revision += 1
    }

    /// Changes this Mac's card settings (Custom only): `change` edits the current ones, the result is stored.
    public func updateGraph(_ change: (inout GraphConfig) -> Void) {
        guard editable else { return }
        var g = graph
        change(&g)
        writeGraph(g)
    }

    public func addLine(_ mgdl: Double) {
        updateGraph { $0.lines = GraphConfig.parseLines(GraphConfig.linesText($0.lines + [Int(mgdl)])) }
    }

    public func removeLine(_ mgdl: Int) { updateGraph { $0.lines.removeAll { $0 == mgdl } } }

    public var rangesChanged: Bool { graph.bands != GraphConfig().bands }
    public func resetRanges() { updateGraph { $0.bands = GraphConfig().bands } }
    public var marksChanged: Bool { graph.marks != MarkShow.defaults }
    public func resetMarks() { updateGraph { $0.marks = .defaults } }
    public var ringOrderChanged: Bool { graph.ringOrder != DEFAULT_RING_ORDER }
    public func resetRingOrder() { updateGraph { $0.ringOrder = DEFAULT_RING_ORDER } }
    /// Any setting of the Metrics pane differs from the defaults.
    public var metricsChanged: Bool {
        let d = GraphConfig()
        return graph.slots != d.slots || graph.lastOn != d.lastOn || graph.lastWhere != d.lastWhere
            || graph.lastElse != d.lastElse || graph.ringRange != d.ringRange || graph.ringMarks != d.ringMarks
            || graph.ringOrder != d.ringOrder
    }
    /// "Reset Metrics…" (after the confirmation): the metrics, the last treatment and the rings' look back to their
    /// defaults; the ranges stay.
    public func resetMetrics() {
        let d = GraphConfig()
        updateGraph {
            $0.slots = d.slots
            $0.lastOn = d.lastOn
            $0.lastWhere = d.lastWhere
            $0.lastElse = d.lastElse
            $0.ringRange = d.ringRange
            $0.ringMarks = d.ringMarks
            $0.ringOrder = d.ringOrder
        }
    }
    /// Any card setting differs from the defaults (the place's own ones included).
    public var graphChanged: Bool {
        graph != GraphConfig() || !sensorTime
            || GraphPlace.allCases.contains {
                placeHours($0) != $0.hours.format.default || sensorPlace($0) != nil
            }
    }

    /// "Reset all graph settings" (after the confirmation): every card setting of this Mac back to its default.
    public func resetGraph() {
        guard editable else { return }
        writeGraph(GraphConfig())
        for p in GraphPlace.allCases {
            store.set(p.hours.format.default, for: p.hours.spec)
            store.set(PlaceOverride.same.rawValue, for: p.sensorTime)
        }
        store.set(true, for: MacSettings.sensorTime)
        revision += 1
    }

    /// Stores every mirrored card setting of `g` in its stored form.
    private func writeGraph(_ g: GraphConfig) {
        let b = g.bands
        let numbers: [(MacSettings.Number, Int)] = [
            (MacSettings.bandVeryLow, b.veryLow), (MacSettings.bandLow, b.low), (MacSettings.bandTight, b.tight),
            (MacSettings.bandHigh, b.high), (MacSettings.bandVeryHigh, b.veryHigh),
            (MacSettings.bandExtremelyHigh, b.extremelyHigh), (MacSettings.graphHours, g.hours),
            (MacSettings.gridHours, g.gridHours),
            (MacSettings.countdownHours, g.countdownHours),
        ]
        for (n, v) in numbers { store.setNumber(Double(v), for: n) }
        store.set(GraphConfig.slotsText(g.slots), for: MacSettings.slots)
        store.set(g.ringRange.rawValue, for: MacSettings.ringRange)
        store.set(GraphConfig.ringOrderText(g.ringOrder), for: MacSettings.ringOrder)
        store.set(g.ringMarks, for: MacSettings.ringMarks)
        store.set(g.side, for: MacSettings.side)
        store.set(g.columnCrown, for: MacSettings.columnCrown)
        store.set(g.marks.settingsText, for: MacSettings.marks)
        store.set(GraphConfig.linesText(g.lines), for: MacSettings.lines)
        store.set(g.lastOn, for: MacSettings.lastTreatment)
        store.set(g.lastWhere.rawValue, for: MacSettings.lastPlace)
        store.set(GraphConfig.slotText(g.lastElse), for: MacSettings.lastElse)
        store.set(g.trend.rawValue, for: MacSettings.trend)
        revision += 1
    }

    // MARK: alert settings

    /// The alert rules in use as the Alerts pane shows them: the wearer's mirrored values, the defaults (Default) or
    /// this Mac's own; what isn't mirrored is always this Mac's.
    public var shownAlerts: AlertSettings {
        _ = revision
        return alerts.settings.inUse(from: settingsFrom, wearer: wearer)
    }

    /// The mirrored alert rows (thresholds, repeats, quiet times) are read-only outside Custom: they come from the
    /// wearer or the defaults.
    public var alertsMirrored: Bool { !editable }
}

/// A store with nothing in it and "Settings from: Wearer": loads only what the wearer sent (else the defaults).
private final class WearerOnly: SettingsStoring, Sendable {
    func value<Value>(_ spec: SettingSpec<Value>) -> Value {
        spec.key == MacSettings.settingsFrom.key
            ? (SettingsFrom.wearer.rawValue as? Value ?? spec.defaultValue)
            : spec.defaultValue
    }
    func set<Value>(_ value: Value, for spec: SettingSpec<Value>) {}
}
