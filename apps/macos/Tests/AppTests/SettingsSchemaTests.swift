// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraApp
@testable import SukraraGraph

/// THE settings schema (contracts/settings/settings.json) against every Mac setting, as Android's SettingsSchemaTest
/// checks its own: every stored key belongs to one typed setting with the schema's key, default and format; every
/// alert-settings field the schema names is in the alert settings JSON; every option list is the Mac's, in order;
/// secrets are in the Keychain; a follower takes from the wearer exactly what the schema mirrors.
@Suite struct SettingsSchemaTests {
    /// One stored key of a typed Mac setting and the value it reads when nothing is stored.
    struct Typed {
        let id: String
        let key: String
        let value: any SettingValue
        var format: NumberSpec? = nil
    }

    /// The schema id of every row stored inside a JSON value (the alert settings): checked field by field below.
    static let json = "{json}"

    static func n(_ x: MacSettings.Number) -> Typed {
        Typed(id: x.id, key: x.spec.key, value: x.spec.defaultValue, format: x.format)
    }

    static func t<V: SettingValue>(_ id: String, _ spec: SettingSpec<V>) -> Typed {
        Typed(id: id, key: spec.key, value: spec.defaultValue)
    }

    /// Every Mac setting in UserDefaults. A schema row without one here, or one here the schema doesn't have, fails.
    static let typed: [Typed] =
        MacSettings.bands.map { n($0) } + [
            n(MacSettings.graphHours), n(MacSettings.menubarHours), n(MacSettings.widgetHours),
            n(MacSettings.ringCount), n(MacSettings.gridHours), n(MacSettings.countdownHours),
            t("graph.slots", MacSettings.slots), t("graph.ringRange", MacSettings.ringRange),
            t("graph.ringOrder", MacSettings.ringOrder), t("graph.ringMarks", MacSettings.ringMarks),
            t("graph.side", MacSettings.side), t("graph.marks", MacSettings.marks),
            t("graph.columnCrown", MacSettings.columnCrown), t("graph.lines", MacSettings.lines),
            t("graph.lastPlace", MacSettings.lastPlace), t("graph.lastElse", MacSettings.lastElse),
            t("graph.sensorTime.place", MacSettings.menubarSensorTime),
            t("graph.sensorTime.place", MacSettings.widgetSensorTime),
            t("follower.settingsFrom", MacSettings.settingsFrom), t("general.unit", MacSettings.unit),
            t("setup.steps", MacSettings.setupSteps), t("source.jugglucoLan", MacSettings.jugglucoLan),
            t("source.jugglucoAddress", MacSettings.jugglucoAddress), t("settings.pane", MacSettings.settingsPane),
            t("update.skippedVersion", MacSettings.updateSkippedVersion), t("report.days", MacSettings.reportDays),
            t("general.readAloudShortcut", MacSettings.readAloudShortcut), t(json, MacSettings.alerts),
        ]

    /// The schema's rows as JSON (the defaults and values the generated spec doesn't carry), by id.
    static let rows: [String: [String: Any]] = {
        let data = try! Data(contentsOf: Contracts.url("settings/settings.json"))
        let schema = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
        let list = schema["settings"] as! [[String: Any]]
        return Dictionary(uniqueKeysWithValues: list.map { ($0["id"] as! String, $0) })
    }()

    static let mac = SettingsSpec.settings.filter { !$0.mac.isEmpty }
    static func option(_ name: String) -> [String] { SettingsSpec.options[name] ?? [] }

    /// A JSON `true` / `false` (not a number that bridges to Bool).
    static func bool(_ v: Any?) -> Bool? {
        guard let n = v as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() else { return nil }
        return n.boolValue
    }

    @Test func everyNumberSettingHasAFormatWhoseDefaultIsValid() throws {
        for info in SettingsSpec.settings where info.type == "number" {
            let format = try #require(info.number.flatMap { SettingsSpec.numbers[$0] }, "\(info.id)")
            #expect(format.valid(format.default), "\(info.id)")
            if let mmol = format.mmol { #expect(mmol.valid(mmol.default), "\(info.id) mmol/L") }
        }
    }

    @Test func everyMacStoreIsKnown() {
        for info in Self.mac {
            #expect(
                ["defaults", "keychain", "system"].contains(String(info.mac.split(separator: ":")[0])), "\(info.id)")
        }
    }

    /// Exhaustive both ways: every UserDefaults key of the schema is read by one typed setting and vice versa.
    @Test func everyMacDefaultsKeyIsOneTypedSetting() {
        var schema: Set<String> = []
        for info in Self.mac where info.mac.hasPrefix("defaults:") {
            for key in info.mac.dropFirst("defaults:".count).split(separator: ",") {
                schema.insert("\(info.path == nil ? info.id : Self.json) \(key)")
            }
        }
        let code = Set(Self.typed.map { "\($0.id) \($0.key)" })
        #expect(schema.subtracting(code).isEmpty, "schema rows without a typed setting")
        #expect(code.subtracting(schema).isEmpty, "typed settings the schema doesn't list")
        #expect(Self.typed.count == code.count, "a key read by two typed settings")
    }

    @Test func everyTypedDefaultIsTheSchemas() throws {
        for t in Self.typed where t.id != Self.json {
            let info = try #require(SettingsSpec.settings.first { $0.id == t.id })
            let row = try #require(Self.rows[t.id])
            if let name = info.number, info.type == "number" {
                let format = try #require(SettingsSpec.numbers[name])
                #expect(t.format == format, "\(t.id)")
                #expect(t.value as? Double == format.default, "\(t.id)")
            }
            if let b = Self.bool(row["default"]) {
                #expect(t.value as? Bool == b, "\(t.id)")
            } else if let s = row["default"] as? String {
                #expect(t.value as? String == s, "\(t.id)")
            } else if let list = row["default"] as? [Double] {
                #expect(t.value as? String == list.map { String(Int($0)) }.joined(separator: ","), "\(t.id)")
            }
            if let values = row["values"] as? [String], let v = t.value as? String {
                if info.type == "order" {
                    #expect(Set(v.split(separator: ",").map(String.init)) == Set(values), "\(t.id)")
                } else {
                    #expect(values.contains(v), "\(t.id) default \(v)")
                }
            }
        }
    }

    /// The structured graph defaults are made of the schema's options and formats.
    @Test func structuredGraphDefaultsAreMadeOfTheSchema() throws {
        let window = try #require(SettingsSpec.numbers["ringWindow"])
        for text in [MacSettings.slots.defaultValue, MacSettings.lastElse.defaultValue] {
            for slot in text.split(separator: ",") {
                let parts = slot.split(separator: ":").map(String.init)
                #expect(parts.count == 3 && Self.option("slotKind").contains(parts[0]), "\(slot)")
                #expect(Double(parts[1]).map(window.valid) == true, "\(slot)")
            }
        }
        let marks = Self.option("markKind").map { "\($0):" + Self.option("markDefault.\($0)").joined(separator: "+") }
        #expect(MacSettings.marks.defaultValue == marks.joined(separator: ","))
        #expect(MarkShow.defaults.text == MacSettings.marks.defaultValue)
        let line = try #require(SettingsSpec.numbers["line"])
        #expect(MacSettings.lines.defaultValue.split(separator: ",").allSatisfy { Double($0).map(line.valid) == true })
        #expect(MacSettings.reportDays.defaultValue == GlucoseReport.defaultDays)
    }

    /// Every field of the alert settings JSON the schema names is written, at its format's default where it has one.
    @Test func everyAlertFieldTheSchemaNamesIsInTheAlertSettingsJson() throws {
        var settings = AlertSettings()
        for kind in AlertKind.allCases { settings.sounds.kinds[kind.rawValue] = "soft" }
        let json = try #require(JSONSerialization.jsonObject(with: settings.encoded()) as? [String: Any])
        let rows = Self.mac.filter { $0.mac == "defaults:\(SettingsSpec.Keys.alerts)" }
        #expect(rows.count == Self.mac.filter { $0.path != nil }.count)
        for info in rows {
            let path = try #require(info.path, "\(info.id)")
            for kind in path.contains("{kind}") ? Self.option("alertKind") : [""] {
                let field = path.replacingOccurrences(of: "{kind}", with: kind)
                let value = json.value(at: field)
                #expect(value != nil, "\(info.id): \(field)")
                guard let format = info.number.flatMap({ SettingsSpec.numbers[$0] }), let v = value as? Double else {
                    continue
                }
                #expect(format.valid(v), "\(info.id): \(field) = \(v)")
                if kind.isEmpty { #expect(v == format.default, "\(info.id): \(field) = \(v)") }
            }
            if let b = Self.bool(Self.rows[info.id]?["default"]) {
                #expect(Self.bool(json.value(at: path)) == b, "\(info.id)")
            } else if let s = Self.rows[info.id]?["default"] as? String {
                #expect(json.value(at: path) as? String == s, "\(info.id)")
            }
        }
    }

    @Test func optionListsAreTheMacs() {
        let lists: [String: [String]] = [
            "alertKind": AlertKind.allCases.map(\.rawValue),
            "exportPeriod": GlucoseCsv.Period.allCases.map(\.rawValue),
            "reportPeriod": GlucoseReport.periods.map(String.init),
            "slotKind": SlotKind.allCases.map(\.rawValue),
            "tirRange": TirRange.allCases.map(\.rawValue),
            "lastPlace": LastPlace.allCases.map(\.rawValue),
            "soundPack": AlertSoundSettings.packs + ["system", "none"],
            "settingsFrom": SettingsFrom.allCases.map(\.rawValue),
            "unit": GlucoseUnit.allCases.map(\.rawValue),
            "markKind": MarkShow.kinds,
            "markPart": MarkShow.allParts,
        ]
        var checked = Set(lists.keys)
        for (name, values) in lists { #expect(values == Self.option(name), "\(name)") }
        for kind in MarkShow.kinds {
            #expect(MarkShow.parts(kind) == Self.option("markParts.\(kind)"), "\(kind)")
            #expect(MarkShow.allParts.filter { MarkShow.defaults.has(kind, $0) } == Self.option("markDefault.\(kind)"))
            checked.formUnion(["markParts.\(kind)", "markDefault.\(kind)"])
        }
        #expect(checked == Set(SettingsSpec.options.keys), "an option list without its Mac check")
        // the enum rows' own value lists
        #expect(Self.rows["settings.pane"]?["values"] as? [String] == SettingsModel.Pane.allCases.map(\.rawValue))
        #expect(Self.rows["graph.ringRange"]?["values"] as? [String] == TirRange.allCases.map(\.rawValue))
        #expect(Self.rows["graph.lastPlace"]?["values"] as? [String] == LastPlace.allCases.map(\.rawValue))
        #expect(Self.rows["general.unit"]?["values"] as? [String] == GlucoseUnit.allCases.map(\.rawValue))
        #expect(Self.rows["alerts.sounds.pack"]?["values"] as? [String] == Self.option("soundPack"))
        let order = Self.rows["graph.ringOrder"]?["values"] as? [String] ?? []
        #expect(Set(order) == Set(Band.allCases.map(\.name)))
        let from = Self.rows["follower.settingsFrom"]?["values"] as? [String] ?? []
        #expect(Set(from) == Set(SettingsFrom.allCases.map(\.rawValue)))
    }

    @Test func secretsLiveInTheKeychainNeverInDefaults() {
        let secrets = SettingsSpec.settings.filter { $0.type == "secret" }
        #expect(secrets.map(\.id) == ["source.jugglucoSecret"])
        for info in secrets { #expect(info.mac == "keychain:\(MacSettings.jugglucoSecretAccount)", "\(info.id)") }
        #expect(!Self.typed.contains { $0.key == MacSettings.jugglucoSecretAccount })
    }

    /// The schema's `mirror` / `mirrorExcept`: a follower reads the wearer's per-kind rows for every kind but no
    /// data, even when a wearer's payload carries no data's.
    @Test func aFollowerTakesExactlyWhatTheSchemaMirrors() throws {
        let perKind = SettingsSpec.settings.filter { $0.mirror && $0.wire?.contains("{kind}") == true }
        #expect(!perKind.isEmpty)
        for info in perKind {
            #expect(info.mirrorExcept == ["no_data"], "\(info.id)")
            #expect(info.mirrorExcept.allSatisfy(Self.option("alertKind").contains), "\(info.id)")
        }
        for info in SettingsSpec.settings where !info.mirrorExcept.isEmpty {
            #expect(info.mirror && info.wire?.contains("{kind}") == true, "\(info.id)")
        }
        #expect(AlertSettings.mirroredKinds == AlertKind.allCases.filter { $0 != .noData })
        let json = #"""
            {"graph":{"low":65},"alerts":{"alerts":{"low":{"on":false,"mgdl":72,"repeatMin":30},
            "no_data":{"on":false,"min":200,"repeatMin":240}},"persistence":{"lowMin":10,"highMin":30,"marginMgdl":5},
            "night":{"on":true,"from":1380,"to":420,"alerts":{"low":{"on":true,"mgdl":80,"repeatMin":10,"alarm":true},
            "no_data":{"on":false,"min":200}}}}}
            """#
        let w = try #require(WearerSettings(json: json, wearer: "Ana"))
        #expect(w.bool("alerts.kind.on", kind: "low") == false)
        #expect(w.number("alerts.kind.repeat", kind: "low") == 30)
        #expect(w.bool("alerts.kind.on", kind: "no_data") == nil)
        #expect(w.number("alerts.kind.repeat", kind: "no_data") == nil)
        #expect(w.number("alerts.threshold.no_data") == nil)
        let own = AlertSettings()
        let inUse = own.inUse(from: .wearer, wearer: w)
        #expect(inUse[.low].on == false && inUse[.low].threshold == 72 && inUse[.low].repeatMin == 30)
        #expect(inUse[.noData] == own[.noData])
        // #37: the waits and the margin near a threshold are rules: the wearer's, back to the defaults in Default
        #expect(inUse.persistence == PersistenceSettings(lowMin: 10, highMin: 30, marginMgdl: 5))
        // #38: the night set and its hours too; like an alarm and no data's night rule stay this Mac's
        #expect(inUse.night.on && inUse.night.from == 1380 && inUse.night.to == 420)
        #expect(inUse.night[.low].threshold == 80 && inUse.night[.low].repeatMin == 10 && !inUse.night[.low].alarm)
        #expect(inUse.night[.noData] == own.night[.noData])
        var custom = own
        custom.persistence.lowMin = 15
        #expect(custom.inUse(from: .default, wearer: w).persistence == PersistenceSettings())
    }
}

extension Dictionary where Key == String, Value == Any {
    /// The value at a dotted path (`sounds.pack`), or nil.
    fileprivate func value(at path: String) -> Any? {
        path.split(separator: ".").reduce(self as Any?) { ($0 as? [String: Any])?[String($1)] }
    }
}
