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

/// THE kinds' order's facts on this Mac (`AppModel.kindFacts`): the newest reading while fresh, the low alert's
/// threshold in force, what rings; never a pen read (a Mac reads no pens). The rule itself: `KindOrderTests`.
@Suite struct KindFactsTests {
    let clock = FakeClock()

    func model(_ settings: AlertSettings = AlertSettings()) -> (AppModel, FollowerSnapshot) {
        let alerts = AlertController(
            settings: settings, clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts)
        model.sourceKind = .sukrara
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: true)
        return (model, snapshot)
    }

    @Test func theFreshReadingAgainstTheLowThreshold() {
        let (model, base) = model()
        var snapshot = base
        snapshot.readings = [Reading(t: clock.now.addingTimeInterval(-60), mgdl: 62, sensorRate: 0)]
        model.apply(snapshot)
        let facts = model.kindFacts
        #expect(facts.reading == 62 && facts.lowBelow == 70 && facts.pen == nil)
        #expect(KindOrder.focus(facts, now: Int64(clock.now.timeIntervalSince1970)) == .carbs)
        // 30 min old: nothing is known now, the usual order (no alert rings on it either)
        snapshot.readings = [Reading(t: clock.now.addingTimeInterval(-1800), mgdl: 62, sensorRate: 0)]
        model.apply(snapshot)
        #expect(model.kindFacts.reading == nil)
        #expect(KindOrder.of(model.kindFacts, now: Int64(clock.now.timeIntervalSince1970)) == KindOrder.usual)
    }

    @Test func thePersonsOwnLowThreshold() {
        var settings = AlertSettings()
        settings[.low].threshold = 80
        let (model, base) = model(settings)
        var snapshot = base
        snapshot.readings = [Reading(t: clock.now.addingTimeInterval(-60), mgdl: 75, sensorRate: 0)]
        model.apply(snapshot)
        #expect(model.kindFacts.lowBelow == 80)
        #expect(KindOrder.focus(model.kindFacts, now: Int64(clock.now.timeIntervalSince1970)) == .carbs)
    }
}
