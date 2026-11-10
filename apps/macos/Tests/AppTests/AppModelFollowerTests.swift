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

@Suite struct AppModelFollowerTests {
    let clock = FakeClock()
    let notifier = RecordingNotifier()
    let wake = RecordingWake()

    @Test func followingFeedsTheAlertsAndUnfollowingDisarmsThem() {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: notifier, wake: wake)
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts)
        var snapshot = FollowerSnapshot.empty
        model.apply(snapshot)
        #expect(model.notMonitoringReason == "Not monitoring: not following anyone yet" && wake.at == nil)
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        model.apply(snapshot)
        #expect(model.notMonitoringReason == "Waiting for the first reading from the wearer" && wake.at == nil)
        snapshot.readings = [Reading(t: clock.now, mgdl: 112, rate: 0)]
        model.apply(snapshot)
        #expect(model.notMonitoringReason == nil && model.statusTitle == "112 →" && wake.at != nil)
        #expect(alerts.mirroring)
        snapshot.link = .notFollowing
        model.apply(snapshot)
        #expect(model.statusTitle == "—" && alerts.nextWake == nil && !alerts.mirroring)
    }

    /// Every way to have no value names its reason and the way out the panel offers (#72), the same reason the
    /// widgets get.
    @Test func everyNoDataStateHasAReasonAndAWayOut() {
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock)
        var snapshot = FollowerSnapshot.empty
        let cases: [(LinkState, NoDataReason, NoDataReason.Action?)] = [
            (.notFollowing, .notFollowing, .follow), (.joining, .notFollowing, .follow),
            (.requested(code: "1234", wearer: "Ana", pairing: nil), .waitingApproval, .following),
            (.declined, .declined, .follow), (.inviteUsed, .inviteUsed, .follow), (.revoked, .revoked, .follow),
            (.broken(.stateDamaged), .broken(.stateDamaged), .following),
            (.following(wearer: "Ana", canWrite: false), .wearerSilent(since: nil), nil),
        ]
        for (link, reason, action) in cases {
            snapshot.link = link
            model.apply(snapshot)
            #expect(model.noDataReason == reason && reason.action == action, "\(link)")
            let text = NoDataText.of(reason, now: clock.now.timeIntervalSince1970)
            #expect(model.notMonitoringReason == text.title && (action == nil) == (text.action == nil), "\(link)")
        }
        // a value hidden after 20 min: the card stays, the reason goes under it with the last reading's time
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        let last = clock.now.addingTimeInterval(-45 * 60)
        snapshot.readings = [Reading(t: last, mgdl: 131, rate: 0)]
        model.apply(snapshot)
        #expect(model.noDataReason == .wearerSilent(since: last.timeIntervalSince1970))
        #expect(model.notMonitoringReason == nil)
        snapshot.readings = [Reading(t: clock.now, mgdl: 131, rate: 0)]
        model.apply(snapshot)
        #expect(model.noDataReason == nil)
    }

    /// r-code-mac H2: a Keychain that won't read (the runtime retries) never disarms the no-data alarm.
    @Test func anUnreadableKeychainKeepsTheNoDataAlarmArmed() {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: notifier, wake: wake)
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts)
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        snapshot.readings = [Reading(t: clock.now, mgdl: 112, rate: 0)]
        model.apply(snapshot)
        #expect(alerts.nextWake != nil)
        snapshot.link = .broken(.keyUnreadable)
        snapshot.keychainUnreadable = true
        model.apply(snapshot)
        #expect(alerts.nextWake != nil)  // still armed: fires if the readings stop
        #expect(model.notMonitoringReason != nil)  // and the panel says why
    }
}
