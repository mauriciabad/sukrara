// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp
@testable import SukraraGraph

/// The sensor life of a source that doesn't say all of it on the Mac (docs/44 decision 10,
/// contracts/sources/sensor-life.json): a Nightscout site's Sensor Start or the manual start, with the source's wear
/// length, is the card's sensor and the alert rules' sensor window; whatever the Mac reads takes them.
@Suite struct AppModelSensorLifeTests {
    let suite = AppModelNightscoutTests()

    var now: TimeInterval { suite.clock.now.timeIntervalSince1970 }

    func alerts() -> AlertController {
        AlertController(
            settings: AlertSettings(), clock: suite.clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
    }

    /// The model reading a site whose Sensor Start was `sensorAgo` ago (negative: ahead of now, so none), readings 40
    /// min old, with `settings`.
    func run(
        sensorAgo: TimeInterval, settings: SensorLifeSettings, alerts: AlertController? = nil
    ) async -> (AppModel, URL) {
        let (model, dir) = suite.model(suite.sensorSite(sensorAgo: sensorAgo, readingAgo: 40 * 60), alerts: alerts)
        model.sensorLifeSettings = settings
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(
            site: AppModelNightscoutTests.site, secret: AppModelNightscoutTests.token)
        await suite.until { !model.readings.isEmpty }
        return (model, dir)
    }

    func settings(wearDays: Double = 15, manualAgo: TimeInterval? = nil) -> SensorLifeSettings {
        SensorLifeSettings(
            wearMin: [.nightscout: wearDays * 1440], manualStart: manualAgo.map { Int(now - $0) } ?? 0)
    }

    @Test func aManualStartIsTheSensorOfASiteWithoutOne() async {
        let (model, dir) = await run(sensorAgo: -3600, settings: settings(wearDays: 10, manualAgo: 2 * 86_400))
        defer { try? FileManager.default.removeItem(at: dir) }
        let start = TimeInterval(Int(now - 2 * 86_400))
        #expect(model.sensor?.start == start)
        #expect(model.sensor?.end == start + 10 * 86_400)
        #expect(model.sourceSensor?.from == .manual)
        await model.stop()
    }

    @Test func aManualStartAheadOfNowIsIgnoredAndNoDataRings() async {
        let alerts = alerts()
        let (model, dir) = await run(sensorAgo: -3600, settings: settings(manualAgo: -600), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        #expect(model.sensor == nil)
        suite.clock.advance(by: 60)
        alerts.tick()
        #expect(alerts.ui.active[.noData] != nil)
        await model.stop()
    }

    @Test func aLengthThatEndsTheManualSensorKeepsNoDataQuiet() async {
        let alerts = alerts()
        let (model, dir) = await run(
            sensorAgo: -3600, settings: settings(wearDays: 14, manualAgo: 15 * 86_400), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        #expect(model.sensor.map { $0.end < now } == true)
        suite.clock.advance(by: 60)
        alerts.tick()
        #expect(alerts.ui.active[.noData] == nil)
        await model.stop()
    }

    @Test func aNewerSensorStartFromTheSiteWins() async {
        let (model, dir) = await run(sensorAgo: 30 * 60, settings: settings(manualAgo: 2 * 86_400))
        defer { try? FileManager.default.removeItem(at: dir) }
        await suite.until { model.sourceSensor?.from == .source }
        #expect(model.sensor?.start == now - 30 * 60)
        await model.stop()
    }

    @Test func theWearLengthAppliesToTheSitesStartAtOnce() async {
        let (model, dir) = await run(sensorAgo: 30 * 60, settings: settings())
        defer { try? FileManager.default.removeItem(at: dir) }
        await suite.until { model.sensor != nil }
        let start = now - 30 * 60
        #expect(model.sensor?.end == start + 15 * 86_400)
        model.sensorLifeSettings = settings(wearDays: 10)
        #expect(model.sensor?.end == start + 10 * 86_400)
        await model.stop()
    }

    @Test func anotherSourceClearsTheManualStart() {
        let store = InMemorySettingsStore()
        SourceSpec(kind: .nightscout).store(in: store)
        store.set(1_791_000_000.0, for: MacSettings.manualStart)
        SourceSpec(kind: .nightscout).store(in: store)
        #expect(store.value(MacSettings.manualStart) == 1_791_000_000)  // the same answer keeps it
        SourceSpec(kind: .lanJuggluco).store(in: store)
        #expect(store.value(MacSettings.manualStart) == 0)
    }

    @Test func anotherSiteClearsTheManualStart() {
        let store = InMemorySettingsStore()
        store.set("https://ns.example.org", for: MacSettings.nightscoutURL)
        store.set(1_791_000_000.0, for: MacSettings.manualStart)
        let ns = NightscoutModel(store: store)
        ns.commit("https://ns.example.org")
        #expect(store.value(MacSettings.manualStart) == 1_791_000_000)
        ns.commit("https://other.example.org")
        #expect(store.value(MacSettings.manualStart) == 0)
    }

    @Test func thePickerRefusesAStartAheadOfNowOrAlreadyEnded() {
        let store = InMemorySettingsStore()
        let m = SensorLifeModel(store: store)
        let t = Date(timeIntervalSince1970: 1_792_000_000)
        m.now = { t }
        m.pick()
        #expect(m.draft == t && m.problem(.nightscout) == nil)
        m.draft = t.addingTimeInterval(120)
        #expect(m.problem(.nightscout) != nil)
        m.draft = t.addingTimeInterval(-16 * 86_400)
        #expect(m.problem(.nightscout) != nil)
        m.save(.nightscout)
        #expect(store.value(MacSettings.manualStart) == 0)
        m.draft = t.addingTimeInterval(-86_400)
        m.save(.nightscout)
        #expect(store.value(MacSettings.manualStart) == t.timeIntervalSince1970 - 86_400)
        #expect(m.life(.nightscout)?.from == .manual)
        m.removeStart()
        #expect(store.value(MacSettings.manualStart) == 0 && m.life(.nightscout) == nil)
    }
}
