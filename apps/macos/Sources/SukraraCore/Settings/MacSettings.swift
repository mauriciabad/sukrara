// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The Mac's typed settings: one `SettingSpec` per schema entry the Mac windows edit (keys from the generated
/// `SettingsSpec.Keys`, number defaults from the generated `NumberSpec`s). Number values are always read through
/// their format (`NumberSpec.coerce`), so only values the settings accept get in.
public enum MacSettings {
    public static let bandVeryLow = number(
        "graph.band.veryLow", SettingsSpec.Keys.bandVeryLow, SettingsSpec.bandVeryLow)
    public static let bandLow = number("graph.band.low", SettingsSpec.Keys.bandLow, SettingsSpec.bandLow)
    public static let bandTight = number("graph.band.tight", SettingsSpec.Keys.bandTight, SettingsSpec.bandTight)
    public static let bandHigh = number("graph.band.high", SettingsSpec.Keys.bandHigh, SettingsSpec.bandHigh)
    public static let bandVeryHigh = number(
        "graph.band.veryHigh", SettingsSpec.Keys.bandVeryHigh, SettingsSpec.bandVeryHigh)
    public static let bandExtremelyHigh = number(
        "graph.band.extremelyHigh", SettingsSpec.Keys.bandExtremelyHigh, SettingsSpec.bandExtremelyHigh)
    public static let graphHours = number("graph.hours", SettingsSpec.Keys.graphHours, SettingsSpec.graphHours)
    public static let gridHours = number("graph.gridHours", SettingsSpec.Keys.gridHours, SettingsSpec.gridHours)
    public static let countdownHours = number(
        "sensor.countdownHours", SettingsSpec.Keys.countdownHours, SettingsSpec.countdownHours)
    /// The widgets' own time shown (`placeHours`, 0 = same as the menu bar's `graphHours`): always this Mac's, never
    /// mirrored.
    public static let widgetHours = number("graph.hours.place", SettingsSpec.Keys.widgetHours, SettingsSpec.placeHours)
    // The structured graph settings, stored in their wire spellings (PROTOCOL-v2 "Settings"); the defaults are the
    // renderer's (`GraphConfig()`, checked by GraphSettingsTests).
    public static let slots = SettingSpec(
        key: SettingsSpec.Keys.slots, defaultValue: "TIR:86400:1:1,TIR:604800:1:1,GMI:1209600:1:0")
    public static let ringRange = SettingSpec(key: SettingsSpec.Keys.ringRange, defaultValue: "IN_RANGE")
    public static let ringOrder = SettingSpec(
        key: SettingsSpec.Keys.ringOrder, defaultValue: "TIGHT,TARGET,LOW,VERY_LOW,HIGH,VERY_HIGH")
    public static let lines = SettingSpec(key: SettingsSpec.Keys.lines, defaultValue: "100")
    /// The last treatment beside the value, and where it sits while on (NEAR_VALUE / END).
    public static let lastTreatment = SettingSpec(key: SettingsSpec.Keys.lastTreatment, defaultValue: true)
    public static let lastPlace = SettingSpec(key: SettingsSpec.Keys.lastPlace, defaultValue: "NEAR_VALUE")
    /// The metric the last treatment's place shows while there's no recent treatment, on or off.
    public static let lastElse = SettingSpec(key: SettingsSpec.Keys.lastElse, defaultValue: "TIR:2592000:1:1")
    public static let marks = SettingSpec(
        key: SettingsSpec.Keys.marks,
        defaultValue: "rapid:point+line+amount+time,long:point+amount+time,carbs:point,note:point")
    public static let ringMarks = SettingSpec(key: SettingsSpec.Keys.ringMarks, defaultValue: true)
    public static let side = SettingSpec(key: SettingsSpec.Keys.side, defaultValue: true)
    public static let columnCrown = SettingSpec(key: SettingsSpec.Keys.columnCrown, defaultValue: true)
    /// The sensor's time left on the menu bar's card; the widgets follow it unless they have their own (this Mac's,
    /// never mirrored).
    public static let sensorTime = SettingSpec(key: SettingsSpec.Keys.sensorTime, defaultValue: true)
    /// The widgets' own sensor time left (`PlaceOverride`: same / on / off): always this Mac's.
    public static let widgetSensorTime = SettingSpec(
        key: SettingsSpec.Keys.placeSensorTimeWidget, defaultValue: PlaceOverride.same.rawValue)
    /// The Settings window's last pane (a view state).
    public static let settingsPane = SettingSpec(key: SettingsSpec.Keys.settingsPane, defaultValue: "general")
    /// The Glucose Report window's period in days (1, 7, 14, 30), remembered between openings.
    public static let reportDays = SettingSpec(key: SettingsSpec.Keys.reportDays, defaultValue: 14)
    public static let settingsFrom = SettingSpec(key: SettingsSpec.Keys.settingsFrom, defaultValue: "wearer")
    public static let unit = SettingSpec(key: SettingsSpec.Keys.unit, defaultValue: GlucoseUnit.mgdl.rawValue)
    /// *Trend arrow* (schema `glucose.trend`, mirrored as `graph.trend`): quick / balanced / smooth / sensor.
    public static let trend = SettingSpec(key: SettingsSpec.Keys.trend, defaultValue: TrendSetting.balanced.rawValue)
    /// *Read Glucose Aloud* from any app with Control-Shift-Command-G (on by default; off when another app needs the keys).
    public static let readAloudShortcut = SettingSpec(key: SettingsSpec.Keys.readAloudShortcut, defaultValue: true)
    /// Let Sukrara through a Focus: alarm-level alerts post Time Sensitive (off by default; #184).
    public static let throughFocus = SettingSpec(key: SettingsSpec.Keys.throughFocus, defaultValue: false)
    /// Whose glucose this Mac shows (`Person`); empty = not answered yet (docs/44 Person × Source).
    public static let sourcePerson = SettingSpec(key: SettingsSpec.Keys.sourcePerson, defaultValue: "")
    /// Where it comes from (`SourceKind`: another Sukrara, or Juggluco on the home network); empty = not answered yet.
    public static let sourceKind = SettingSpec(key: SettingsSpec.Keys.sourceKind, defaultValue: "")
    /// Juggluco's address ("192.168.1.23" or with ":port"); blank = find it on this Mac's networks.
    public static let jugglucoAddress = SettingSpec(key: SettingsSpec.Keys.jugglucoAddress, defaultValue: "")
    /// The Keychain account of Juggluco's API secret (schema `source.jugglucoSecret`).
    public static let jugglucoSecretAccount = "jugglucoSecret"
    /// The Nightscout site read as the source (its root, `NightscoutSite`); empty = none yet.
    public static let nightscoutURL = SettingSpec(key: SettingsSpec.Keys.nightscoutURL, defaultValue: "")
    /// Treatments both ways with the Nightscout source.
    public static let nightscoutTreatments = SettingSpec(
        key: SettingsSpec.Keys.nightscoutTreatments, defaultValue: true)
    /// The Keychain account of the Nightscout source's token or API secret (schema `source.nightscoutSecret`).
    public static let nightscoutSecretAccount = "nightscoutSourceSecret"
    /// The alert settings: ONE JSON value (contracts/alerts/SPEC.md §3), owned by the alert settings store.
    public static let alerts = SettingSpec(key: SettingsSpec.Keys.alerts, defaultValue: Data())
    /// The first-run walk's step states (JSON), owned by `SetupFlow`.
    public static let setupSteps = SettingSpec(key: SettingsSpec.Keys.setupSteps, defaultValue: Data())

    /// The six range bands, low to high (their rows keep them in order).
    public static let bands = [bandVeryLow, bandLow, bandTight, bandHigh, bandVeryHigh, bandExtremelyHigh]

    /// A number setting: its schema id, where it's stored and its format.
    public struct Number: Sendable {
        public let id: String
        public let spec: SettingSpec<Double>
        public let format: NumberSpec
    }

    static func number(_ id: String, _ key: String, _ format: NumberSpec) -> Number {
        Number(id: id, spec: SettingSpec(key: key, defaultValue: format.default), format: format)
    }
}

/// Where a follower's mirrored settings come from (schema option list `settingsFrom`).
public enum SettingsFrom: String, Sendable, CaseIterable {
    case wearer, `default`, custom
}

extension SettingsStoring {
    /// A number setting, coerced to a value its format accepts.
    public func number(_ n: MacSettings.Number) -> Double { n.format.coerce(value(n.spec)) }

    /// Stores `v` when the format accepts it; returns whether it did.
    @discardableResult
    public func setNumber(_ v: Double, for n: MacSettings.Number) -> Bool {
        guard n.format.valid(v) else { return false }
        set(v, for: n.spec)
        return true
    }

    public var unit: GlucoseUnit { GlucoseUnit(rawValue: value(MacSettings.unit)) ?? .mgdl }
    public var settingsFrom: SettingsFrom { SettingsFrom(rawValue: value(MacSettings.settingsFrom)) ?? .wearer }
}
