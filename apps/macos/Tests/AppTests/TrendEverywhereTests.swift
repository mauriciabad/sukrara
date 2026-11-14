// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp

/// One trend everywhere (#204, contracts/trend): the same readings, given to the app the way a follow brings them (the
/// sensor's own rate on each), are worked out once (`AppModel`) and every place on the Mac shows the same arrow: the
/// card's face, the menu bar title and its picture, VoiceOver's words, the widget's snapshot, an alert's text and the
/// Trend arrow page's preview. Android's TrendEverywhereTest is the same.
@Suite struct TrendEverywhereTests {
    let file = FileManager.default.temporaryDirectory.appending(
        path: "sukrara-trend-everywhere-\(UUID().uuidString)/glucose.json")

    /// The owner's rise of 2026-11-14 (contracts/trend/rate.json): the readings rose about 1 a minute while the sensor
    /// said 0.8, so ↗ from the readings and → from the sensor.
    static func rise() throws -> (readings: [Reading], expect: [String: [Any]]) {
        let data = try Data(contentsOf: Contracts.url("trend/rate.json"))
        let root = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let cases = try #require(root["cases"] as? [[String: Any]])
        var expect: [String: [Any]] = [:]
        var readings: [Reading] = []
        for c in cases where (c["name"] as? String)?.hasPrefix("the owner's rise") == true {
            expect[try #require(c["trend"] as? String)] = try #require((c["expect"] as? [[Any]])?.last)
            readings = try #require(c["readings"] as? [[NSNumber]]).map {
                Reading(
                    t: Date(timeIntervalSince1970: $0[0].doubleValue), mgdl: $0[1].intValue,
                    sensorRate: $0[2].doubleValue)
            }
        }
        return (readings, expect)
    }

    static func arrows() throws -> [Int: (text: String, en: String)] {
        let data = try Data(contentsOf: Contracts.url("trend/bands.json"))
        let root = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        var out: [Int: (String, String)] = [:]
        for a in try #require(root["arrows"] as? [[String: Any]]) {
            guard let b = (a["band"] as? NSNumber)?.intValue else { continue }
            out[b] = (a["text"] as! String, (a["words"] as! [String: String])["en"]!)
        }
        return out
    }

    @Test(arguments: ["balanced", "sensor"])
    func everyPlaceShowsTheSameArrow(_ setting: String) throws {
        let (readings, expect) = try Self.rise()
        let want = try #require(expect[setting])
        let band = try #require((want[3] as? NSNumber)?.intValue)
        let rate = try #require((want[1] as? NSNumber)?.doubleValue)
        let arrow = try #require(try Self.arrows()[band])
        #expect(
            arrow.text == (setting == "balanced" ? "↗" : "→"),
            "the owner's rise: ↗ from the readings, → from the sensor")

        let clock = FakeClock(readings.last!.t.addingTimeInterval(30))
        let home = URL(fileURLWithPath: "/Users/u", isDirectory: true)
        let installed = BuildInfo(isDebugBuild: false, bundlePath: "/Applications/Sukrara.app", isBundle: true)
        let plan = try LaunchPlan.parse([], build: installed, env: [:], home: home)
        let services = AppServices(plan: plan) { [file] in WidgetFeed(file: file, clock: clock, reload: { _ in }) }
        let widgetOnly = ServicesPlan(ble: false, relays: false, alerts: false, widgetFeed: true, notifications: false)
        plan.services.limited(by: widgetOnly).start(using: services)
        let model = services.model(clock: clock)
        model.graph.trend = try #require(TrendSetting(rawValue: setting))
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        snapshot.readings = readings
        model.apply(snapshot)

        // the one annotate point: the newest reading's trend is the contract's
        let newest = try #require(model.newestReading?.trend)
        #expect(abs(newest.rate - rate) < 1e-9 && newest.band == band)
        // the card's face (menu bar dropdown) and its drawn angle
        let face = GlucoseCard.face(model.panelData, unit: .mgdl, surface: .menubarCard)
        #expect(face.arrowText == arrow.text)
        #expect(abs(TrendAngle.deg(try #require(face.rate)) - TrendRate.deg(rate)) < 1e-6)
        // the menu bar title, its arrow picture and VoiceOver's words
        let title = model.menubarTitle
        #expect(title.text == "182 \(arrow.text)")
        #expect(title.mark == .arrow(rate: newest.rate, band: band))
        #expect(title.accessibilityLabel.contains(arrow.en))
        // the widget's snapshot
        let written = try #require(WidgetSnapshot.decode(try Data(contentsOf: file)))
        #expect(GlucoseCard.face(written.data, unit: .mgdl, surface: .widgetValue).arrowText == arrow.text)
        // an alert's text (its value is the newest reading's, #195)
        let firing = AlertFiring(
            id: "a", kind: .high, at: clock.now, reading: GlucoseSample(try #require(model.newestReading)),
            threshold: 180, n: 1, treated: 0)
        #expect(AlertText(language: .en).body(firing, now: clock.now) == "182 mg/dL \(arrow.text)")
        // Settings › General › Trend arrow's preview
        #expect(TrendPreview.look(model.newestReading, unit: .mgdl, now: clock.now)?.arrowText == arrow.text)
        #expect(
            TrendPreview.detail(model.newestReading, setting: model.graph.trend, unit: .mgdl)
                .hasPrefix(setting == "balanced" ? "From the readings: +1" : "From the sensor: +0.8"))
    }

    /// Changing the setting turns every arrow at once (the readings are worked out again).
    @Test func theSettingTurnsTheArrowAtOnce() throws {
        let (readings, _) = try Self.rise()
        let clock = FakeClock(readings.last!.t.addingTimeInterval(30))
        let plan = LaunchPlan(mode: .normal, services: .none, data: .isolated(root: URL(fileURLWithPath: "/tmp")))
        let model = AppModel(plan: plan, clock: clock)
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        snapshot.readings = readings
        model.apply(snapshot)
        #expect(model.statusTitle == "182 ↗")
        model.graph.trend = .sensor
        #expect(model.statusTitle == "182 →")
        model.graph.trend = .quick
        #expect(model.statusTitle == "182 ↗")
    }
}
