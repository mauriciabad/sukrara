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

/// Records what the controller asks the notification layer to do.
final class RecordingNotifier: AlertNotifying {
    var tests: [AlertKind] = []
    func postTest(_ kind: AlertKind) { tests.append(kind) }
    enum Call: Equatable {
        case post(AlertKind, id: String, sound: Bool, category: String)
        case clear(AlertGroup)
        case muted(AlertSide)
        case unmuted(AlertSide)
    }
    var calls: [Call] = []
    var posts: [Call] { calls.filter { if case .post = $0 { true } else { false } } }

    func post(_ f: AlertFiring, sound: Bool, category: String, escalated: Bool) {
        calls.append(.post(f.kind, id: f.id, sound: sound, category: category))
    }
    func clear(_ group: AlertGroup) { calls.append(.clear(group)) }
    func postMuted(_ side: AlertSide, until: Date, category: String) {
        calls.append(.muted(side))
        mutedCategories.append(category)
    }
    /// Each muted note's category, in order.
    var mutedCategories: [String] = []
    func removeMuted(_ side: AlertSide) { calls.append(.unmuted(side)) }
    /// The event log's alert-state lines, in order.
    var lines: [String] = []
    func log(_ line: String) { lines.append(line) }
    /// The "stopped monitoring" notes, in order (nil = removed).
    var monitoring: [String?] = []
    func monitoringStopped(_ reason: String?) { monitoring.append(reason) }
}

final class MemoryAlertStore: AlertStateStoring {
    var state: AlertState?
    func load() -> AlertState? { state }
    func save(_ state: AlertState) { self.state = state }
}

final class RecordingWake: WakeScheduling {
    var at: Date?
    func schedule(at date: Date?, _ fire: @escaping @MainActor () -> Void) { at = date }
}

@Suite struct AlertControllerTests {
    let clock = FakeClock()
    let store = MemoryAlertStore()
    let notifier = RecordingNotifier()
    let wake = RecordingWake()

    func controller(_ settings: AlertSettings = AlertSettings()) -> AlertController {
        AlertController(settings: settings, clock: clock, store: store, notifier: notifier, wake: wake)
    }

    func reading(_ mgdl: Int, minutesAgo: Double = 0, rate: Double? = 0) -> Reading {
        Reading(t: clock.now.addingTimeInterval(-minutesAgo * 60), mgdl: mgdl, rate: rate)
    }

    @Test func noTimerAndNoAlarmBeforeTheFirstReading() {
        let c = controller()
        #expect(c.nextWake == nil && wake.at == nil)
        clock.advance(by: 6 * 3600)
        c.tick()
        #expect(notifier.posts.isEmpty && c.nextWake == nil)
    }

    @Test func noDataIsTimedByTheEngineNotByTheNextReading() {
        let c = controller()
        c.reading(reading(110))
        let deadline = clock.now.addingTimeInterval(AlertSettings()[.noData].threshold * 60)
        #expect(wake.at != nil && wake.at! <= deadline)
        clock.advance(by: AlertSettings()[.noData].threshold * 60 + 1)
        c.tick()
        #expect(notifier.posts.contains { if case .post(.noData, _, true, _) = $0 { true } else { false } })
    }

    @Test func aLowFiresWithSoundAndItsCategory() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        guard case .post(.low, _, true, let category)? = notifier.posts.first else {
            Issue.record("no low posted: \(notifier.calls)")
            return
        }
        #expect(
            category == AlertNotificationIDs.alertCategory || category == AlertNotificationIDs.alertMuteOnlyCategory)
        #expect(c.ui.isAlerting)
    }

    @Test func episodesAndMutesSurviveARelaunchWithoutRingingAgain() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        c.perform(.muteAll(until: clock.now.addingTimeInterval(3600)))
        #expect(store.state != nil)
        clock.advance(by: 60)
        notifier.calls = []
        let relaunched = controller()
        // the alert that was up shows again silently, nothing rings
        #expect(!notifier.posts.contains { if case .post(_, _, true, _) = $0 { true } else { false } })
        #expect(relaunched.ui.muteAllUntil != nil)
    }

    @Test func everyMuteActionHasItsLogLine() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        c.perform(.mute(.low, minutes: 30))
        c.perform(.unmute(.low))
        c.perform(.remindLess(.low))
        c.perform(.remindAsUsual(.low))
        c.perform(.muteAll(until: clock.now.addingTimeInterval(7200)))
        c.perform(.unmuteAll)
        c.perform(.muteAll(until: muteAllForever))
        c.perform(.unmuteAll)
        c.perform(.reshow(.low))
        let relax = AlertSettings().notificationActions.relaxMin
        #expect(
            notifier.lines == [
                "Low alerts muted for 30 min",
                "Low alerts unmuted",
                "Low reminders every \(Durations.text(relax))",
                "Low reminders as usual",
                "All alerts muted for 2 h",
                "Alerts unmuted",
                "All alerts muted until you turn it off",
                "Alerts unmuted",
            ])
    }

    @Test func aTimedMuteAllEndingByItselfIsLoggedOnceBeforeWhatItLetsRing() {
        let c = controller()
        c.reading(reading(110))
        c.perform(.muteAll(until: clock.now.addingTimeInterval(1800)))
        clock.advance(by: 1800)
        c.reading(reading(60, rate: -0.5))
        #expect(notifier.lines == ["All alerts muted for 30 min", "Alerts back on"])
        #expect(notifier.posts.contains { if case .post(.low, _, true, _) = $0 { true } else { false } })
        clock.advance(by: 60)
        c.tick()
        #expect(notifier.lines.filter { $0 == "Alerts back on" }.count == 1)
    }

    @Test func backInRangeByTheMarginIsLogged() {
        var s = AlertSettings()
        s.notificationActions.backMin = 0
        let c = controller(s)
        c.reading(reading(60, rate: -0.5))
        c.perform(.mute(.low, minutes: 60))
        clock.advance(by: 300)
        c.reading(reading(110))
        #expect(notifier.lines.last == "Low back in range: alerts as usual")
    }

    @Test func muteActionPostsTheMutedNoteAndUnmuteRemovesIt() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        c.perform(.mute(.low, minutes: 30))
        #expect(notifier.calls.contains(.muted(.low)))
        #expect(!c.ui.isAlerting)
        c.perform(.unmute(.low))
        #expect(notifier.calls.contains(.unmuted(.low)))
    }

    @Test func remindLessShowsTheAlertAgainSilentlyWithRemindAsUsual() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        guard case .post(.low, let id, true, _)? = notifier.posts.first else {
            Issue.record("no low posted: \(notifier.calls)")
            return
        }
        notifier.calls = []
        c.perform(.remindLess(.low))
        #expect(
            notifier.calls == [.post(.low, id: id, sound: false, category: AlertNotificationIDs.alertRelaxedCategory)])
        notifier.calls = []
        c.perform(.remindAsUsual(.low))
        #expect(notifier.calls == [.post(.low, id: id, sound: false, category: AlertNotificationIDs.alertCategory)])
    }

    @Test func anUnreadableMuteReplyChangesNothingAndShowsTheAlertAgainSilently() throws {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        guard case .post(.low, let id, true, let category)? = notifier.posts.first else {
            Issue.record("no low posted: \(notifier.calls)")
            return
        }
        let before = store.state
        notifier.calls = []
        let action = try #require(
            Notifier.action(actionID: AlertNotificationIDs.muteAction, side: "low", typed: "soon", settings: c.settings)
        )
        c.perform(action)
        #expect(notifier.calls == [.post(.low, id: id, sound: false, category: category)])
        #expect(store.state == before && c.ui.isAlerting)
    }

    @Test func anUnreadableChangeTimeReplyShowsTheMutedNoteAgain() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        c.perform(.mute(.low, minutes: 30))
        notifier.calls = []
        c.perform(.reshow(.low))
        #expect(notifier.calls == [.muted(.low)])
    }

    /// #194: *Log Carbs…* opens Add Treatment and the low comes back silently (macOS removed it); nothing else changes.
    @Test func logCarbsOpensAddTreatmentAndShowsTheAlertAgainSilently() throws {
        let c = controller()
        c.canLog = { true }
        var opened = 0
        c.openAddCarbs = { opened += 1 }
        c.reading(reading(60, rate: -0.5))
        guard case .post(.low, let id, true, let category)? = notifier.posts.first else {
            Issue.record("no low posted: \(notifier.calls)")
            return
        }
        #expect(category == AlertNotificationIDs.alertCategory + AlertNotificationIDs.logCarbsSuffix)
        let before = store.state
        notifier.calls = []
        let side = try #require(Notifier.logCarbs(actionID: AlertNotificationIDs.logCarbsAction, side: "low"))
        c.perform(.logCarbs(side))
        #expect(opened == 1)
        #expect(notifier.calls == [.post(.low, id: id, sound: false, category: category)])
        #expect(store.state == before && c.ui.isAlerting)
        #expect(AlertController.logLine(.logCarbs(.low), settings: c.settings, now: clock.now) == nil)
    }

    /// The muted note keeps *Log Carbs…* first on a Mac that can add entries; without it, the plain categories.
    @Test func logCarbsOnlyWhereThisMacCanAddEntries() {
        let c = controller()
        c.reading(reading(60, rate: -0.5))
        guard case .post(.low, _, true, let category)? = notifier.posts.first else {
            Issue.record("no low posted: \(notifier.calls)")
            return
        }
        #expect(!category.hasSuffix(AlertNotificationIDs.logCarbsSuffix))
        c.perform(.mute(.low, minutes: 30))
        #expect(notifier.mutedCategories.last == AlertNotificationIDs.mutedCategory)
        c.canLog = { true }
        c.perform(.reshow(.low))
        #expect(
            notifier.mutedCategories.last == AlertNotificationIDs.mutedCategory + AlertNotificationIDs.logCarbsSuffix)
    }

    @Test func aMirroredAlertComesBackSilentlyAfterRemindLessAndAnUnreadableReply() throws {
        let c = controller()
        c.setMirroring(true)
        c.wearerAlert(try payload(kind: "low", fires: true))
        notifier.calls = []
        c.perform(.remindLess(.low))
        #expect(
            notifier.calls == [.post(.low, id: "w1", sound: false, category: AlertNotificationIDs.alertRelaxedCategory)]
        )
        notifier.calls = []
        c.perform(.reshow(.low))
        #expect(
            notifier.calls == [.post(.low, id: "w1", sound: false, category: AlertNotificationIDs.alertRelaxedCategory)]
        )
    }

    @Test func mirroringShowsTheWearersAlertAndRunsOnlyNoDataHere() throws {
        let c = controller()
        c.setMirroring(true)
        c.reading(reading(60, rate: -0.5))  // a low here: the wearer decides, not this Mac
        #expect(notifier.posts.isEmpty)
        c.wearerAlert(try payload(kind: "low", fires: true))
        #expect(notifier.posts.contains { if case .post(.low, "w1", true, _) = $0 { true } else { false } })
        c.wearerAlert(try payload(kind: "low", fires: false))
        #expect(notifier.calls.last == .clear(.level))
    }

    @Test func aMirroredMuteSurvivesARelaunch() throws {
        let c = controller()
        c.setMirroring(true)
        c.perform(.mute(.low, minutes: 60))
        let relaunched = controller()
        relaunched.setMirroring(true)
        notifier.calls = []
        relaunched.wearerAlert(try payload(kind: "low", fires: true))
        #expect(notifier.posts.isEmpty)
    }

    /// #6: following someone else (or stopping) starts fresh: this Mac's Mute all and side mutes end; a *Settings from*
    /// change (mirroring on / off) keeps them.
    @Test func whoseReadingsChangedEndsTheMutesSettingsFromKeepsThem() {
        let c = controller()
        c.perform(.muteAll(until: clock.now.addingTimeInterval(3600)))
        c.perform(.mute(.low, minutes: 60))
        c.setMirroring(true)
        c.setMirroring(false)
        #expect(c.ui.muteAllUntil != nil)
        c.resetMonitoring()
        #expect(c.ui.muteAllUntil == nil)
        #expect(c.ui.quiets.isEmpty)
    }

    /// #128: after an ending, a reading still on its way (stamped before it) can't arm no data again, also after a
    /// relaunch; the next source's start clears the fence, so a join's backfill counts.
    @Test func anEndingFencesQueuedReadingsAJoinDoesNot() {
        let c = controller()
        c.reading(reading(110))
        c.resetMonitoring()
        clock.advance(by: 60)
        c.reading(reading(111, minutesAgo: 2))  // queued before the ending
        #expect(c.nextWake == nil)
        let relaunched = controller()
        relaunched.reading(reading(112, minutesAgo: 1.5))
        #expect(relaunched.nextWake == nil)
        relaunched.sourceStarted()  // a join: its backfill counts
        relaunched.reading(reading(113, minutesAgo: 5))
        #expect(relaunched.nextWake != nil)
    }

    @Test func stoppingFollowingDisarmsNoData() {
        let c = controller()
        c.reading(reading(110))
        c.resetMonitoring()
        #expect(c.nextWake == nil)
        clock.advance(by: 3 * 3600)
        c.tick()
        #expect(!notifier.posts.contains { if case .post(.noData, _, _, _) = $0 { true } else { false } })
    }

    /// #39: the wearer's very low nobody answered there shows through this Mac's own Mute once, and its answer shows it
    /// plain again without sound; this Mac can turn escalations off.
    @Test func anEscalatedVeryLowBreaksThroughThisMacsMuteOnceAndAnAnswerShowsItPlain() throws {
        let c = controller()
        c.setMirroring(true)
        c.perform(.mute(.low, minutes: 60))
        notifier.calls = []
        c.wearerAlert(try veryLow(state: "fire", esc: nil))
        #expect(notifier.posts.isEmpty)
        c.wearerAlert(try veryLow(state: "fire", esc: 5))
        #expect(notifier.posts.contains { if case .post(.veryLow, "v1", true, _) = $0 { true } else { false } })
        notifier.calls = []
        c.wearerAlert(try veryLow(state: "fire", esc: 5))  // the same escalated copy again (another relay): nothing new
        #expect(notifier.posts.isEmpty)
        c.wearerAlert(try veryLow(state: "answered", esc: nil))
        #expect(notifier.posts.contains { if case .post(.veryLow, "v1", false, _) = $0 { true } else { false } })
    }

    @Test func aMacThatTurnedEscalationsOffKeepsItsMute() throws {
        var s = AlertSettings()
        s.escalation.follow = false
        let c = controller()
        c.settings(s)
        c.setMirroring(true)
        c.perform(.mute(.low, minutes: 60))
        notifier.calls = []
        c.wearerAlert(try veryLow(state: "fire", esc: 5))
        #expect(notifier.posts.isEmpty)
    }

    func veryLow(state: String, esc: Int?) throws -> AlertPayload {
        let at = Int(clock.now.timeIntervalSince1970)
        let extra = esc.map { #","esc":\#($0)"# } ?? ""
        let json =
            #"{"v":2,"type":"alert","k":"very_low","state":"\#(state)","id":"v1","at":\#(at),"t":\#(at),"sgv":50,"th":55,"n":1\#(extra)}"#
        return try AlertPayload.decode(json)
    }

    func payload(kind: String, fires: Bool) throws -> AlertPayload {
        let at = Int(clock.now.timeIntervalSince1970)
        let json =
            #"{"v":2,"type":"alert","k":"\#(kind)","state":"\#(fires ? "fire" : "clear")","id":"w1","at":\#(at),"t":\#(at),"sgv":60,"r":-0.5,"th":70,"n":1}"#
        return try AlertPayload.decode(json)
    }
}
