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
        // #40: the follow is the source's start: no data rings 20 min later if no reading ever comes
        #expect(model.notMonitoringReason == "Waiting for the first reading from the wearer")
        #expect(wake.at == clock.now.addingTimeInterval(20 * 60))
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
        // #109: the panel says what is going on, not "Not monitoring"
        #expect(
            model.notMonitoringReason
                == "Can't read the follower key from the Keychain. Retrying; alerting on the last data.")
        #expect(notifier.monitoring == [nil])  // still monitoring: no "stopped" note
        #expect(model.alertingOnLastData)  // the card stays under the line
        #expect(AddTreatmentAvailability(model) == .keyUnreadable)
        #expect(AddTreatmentAvailability(model).reason == "Can't add treatments until the follower key can be read")
    }

    @Test func anUnreadableKeychainWithNothingStoredIsNotMonitoringAndRetrying() {
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock)
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .broken(.keyUnreadable)
        snapshot.keychainUnreadable = true
        model.apply(snapshot)
        #expect(
            model.notMonitoringReason == "Not monitoring: can't read the follower key from the Keychain. Retrying…")
    }

    /// #161: a wearer who replaces their identity tells this Mac first (the revoked notice): the panel says the wearer
    /// stopped sharing, with *Follow* as its way out, and no No data alarm is armed for a stream that won't come back.
    @Test func aWearerWhoStoppedSharingRingsNoNoDataAlarm() {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: notifier, wake: wake)
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts)
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        snapshot.readings = [Reading(t: clock.now, mgdl: 112, rate: 0)]
        model.apply(snapshot)
        #expect(alerts.nextWake != nil)  // armed: no data would ring if the readings stopped
        snapshot.link = .revoked
        model.apply(snapshot)
        #expect(alerts.nextWake == nil && !alerts.mirroring)
        #expect(model.noDataReason == .revoked && NoDataReason.revoked.action == .follow)
        #expect(notifier.monitoring.last == .some("Not monitoring: the wearer stopped sharing"))
        clock.advance(by: 3 * 3600)
        alerts.tick()
        model.apply(snapshot)
        #expect(notifier.posts.isEmpty)
    }

    /// #109: a monitoring Mac that drops out (not by the person) posts a passive note with why; it goes when
    /// monitoring is back. The person's own Stop following posts nothing.
    @Test func droppingOutPostsANoteThatGoesWhenMonitoringIsBack() {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: notifier, wake: wake)
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts)
        var snapshot = FollowerSnapshot.empty
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        snapshot.readings = [Reading(t: clock.now, mgdl: 112, rate: 0)]
        model.apply(snapshot)
        #expect(notifier.monitoring == [nil])  // monitoring started: nothing to show (a stale note goes)
        snapshot.link = .revoked
        model.apply(snapshot)
        #expect(notifier.monitoring == [nil, "Not monitoring: the wearer stopped sharing"])
        model.apply(snapshot)  // the same state again: no second note
        #expect(notifier.monitoring.count == 2)
        snapshot.link = .following(wearer: "Ana", canWrite: false)
        model.apply(snapshot)
        #expect(notifier.monitoring.count == 3 && notifier.monitoring.last == .some(nil))  // back: the note goes
        snapshot.link = .notFollowing  // Stop following: the person's own choice, no note
        model.apply(snapshot)
        #expect(notifier.monitoring.count == 4 && notifier.monitoring.last == .some(nil))
    }
}
