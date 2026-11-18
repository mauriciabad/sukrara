// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraTesting
import Testing
import UserNotifications

@testable import SukraraApp

/// Records requests instead of posting (no real notification ever leaves a test).
final class FakeCenter: NotificationPosting {
    var added: [UNNotificationRequest] = []
    var removed: [String] = []
    var timeSensitiveAllowed = true
    /// How many of the next requests macOS refuses (as with notifications turned off for Sukrara).
    var refuse = 0
    func add(_ request: UNNotificationRequest, done: @escaping @MainActor ((any Error)?) -> Void) {
        added.append(request)
        guard refuse > 0 else { return done(nil) }
        refuse -= 1
        done(UNError(.notificationsNotAllowed))
    }
    func removeDelivered(ids: [String]) { removed += ids }
}

@Suite struct NotifierTests {
    let clock = FakeClock()
    let center = FakeCenter()

    func firing(_ kind: AlertKind, mgdl: Double = 62, rate: Double? = -1.5, treated: Int = 0) -> AlertFiring {
        AlertFiring(
            id: "abc", kind: kind, at: clock.now, reading: GlucoseSample(at: clock.now, mgdl: mgdl, rate: rate),
            threshold: 70, n: 1, treated: treated)
    }

    func notifier(
        _ lang: AlertText.Language = .en, settings: AlertSettings = AlertSettings(), throughFocus: Bool = false
    ) -> Notifier {
        Notifier(
            center: center, sounds: nil, text: AlertText(language: lang), clock: clock,
            throughFocus: { throughFocus }, settings: { settings })
    }

    @Test func categoryIDsAreTheAppOnes() {
        let ids = Set(Notifier.categories(AlertText(language: .en)).map(\.identifier))
        let plain = [
            "app.mauri.sukrara.alert", "app.mauri.sukrara.alert.relaxed", "app.mauri.sukrara.alert.muteOnly",
            "app.mauri.sukrara.muted",
        ]
        #expect(ids == Set(plain + plain.map { $0 + ".treatment" }))
        let categories = Notifier.categories(AlertText(language: .en))
        func actions(_ id: String) -> [String]? { categories.first { $0.identifier == id }?.actions.map(\.identifier) }
        // the order everywhere: Remind less / as usual, then Mute; the muted note only Unmute (no Change time)
        #expect(
            actions("app.mauri.sukrara.alert") == [
                AlertNotificationIDs.remindLessAction, AlertNotificationIDs.muteAction,
            ])
        #expect(
            actions("app.mauri.sukrara.alert.relaxed") == [
                AlertNotificationIDs.remindAsUsualAction, AlertNotificationIDs.muteAction,
            ])
        #expect(actions("app.mauri.sukrara.muted") == [AlertNotificationIDs.unmuteAction])
    }

    /// The owner's review, 2026-11-14: no notification action has an ellipsis, in English or Spanish.
    @Test func noActionTitleHasAnEllipsis() {
        for lang in [AlertText.Language.en, .es] {
            for category in Notifier.categories(AlertText(language: lang)) {
                for action in category.actions {
                    #expect(!action.title.contains("…") && !action.title.contains("..."), "\(action.title)")
                }
            }
        }
    }

    /// The `.treatment` categories have *Treatment* first, which brings Sukrara forward, then the same actions.
    @Test func treatmentComesFirstAndOpensSukrara() throws {
        let categories = Notifier.categories(AlertText(language: .en))
        let alert = try #require(categories.first { $0.identifier == "app.mauri.sukrara.alert.treatment" })
        #expect(
            alert.actions.map(\.identifier) == [
                AlertNotificationIDs.treatmentAction, AlertNotificationIDs.remindLessAction,
                AlertNotificationIDs.muteAction,
            ])
        #expect(alert.actions[0].title == "Treatment" && alert.actions[0].options.contains(.foreground))
        #expect(alert.actions[2].title == "Mute")
        let muted = try #require(categories.first { $0.identifier == "app.mauri.sukrara.muted.treatment" })
        #expect(
            muted.actions.map(\.identifier) == [
                AlertNotificationIDs.treatmentAction, AlertNotificationIDs.unmuteAction,
            ])
        let es = Notifier.categories(AlertText(language: .es)).first { $0.identifier == alert.identifier }
        #expect(es?.actions.first?.title == "Tratamiento")
        #expect(es?.actions.last?.title == "Silenciar")
    }

    /// Every kind a treatment answers (both sides, not no data), only on a Mac that can add entries; the answer maps
    /// back to its side.
    @Test func treatmentOnBothSidesButNoData() {
        let s = AlertSettings()
        let suffix = AlertNotificationIDs.treatmentSuffix
        for kind in AlertKind.allCases {
            let logs = AlertNotificationIDs.category(for: kind, s, relaxed: false, canLog: true).hasSuffix(suffix)
            #expect(logs == (kind != .noData), "\(kind)")
            #expect(!AlertNotificationIDs.category(for: kind, s, relaxed: false, canLog: false).hasSuffix(suffix))
        }
        #expect(
            AlertNotificationIDs.category(for: .low, s, relaxed: true, canLog: true)
                == "app.mauri.sukrara.alert.relaxed.treatment")
        #expect(AlertNotificationIDs.mutedCategory(.fallingFast, canLog: true).hasSuffix(suffix))
        #expect(AlertNotificationIDs.mutedCategory(.high, canLog: true).hasSuffix(suffix))
        #expect(AlertNotificationIDs.mutedCategory(.noData, canLog: true) == AlertNotificationIDs.mutedCategory)
        #expect(Notifier.treatment(actionID: AlertNotificationIDs.treatmentAction, side: "low") == .low)
        #expect(Notifier.treatment(actionID: AlertNotificationIDs.treatmentAction, side: "high") == .high)
        #expect(Notifier.treatment(actionID: AlertNotificationIDs.muteAction, side: "low") == nil)
        #expect(
            Notifier.action(actionID: AlertNotificationIDs.treatmentAction, side: "low", typed: nil, settings: s) == nil
        )
    }

    @Test func alertTextIsSelfContainedInEnglishAndSpanish() {
        notifier(.en).post(firing(.low), sound: true, category: AlertNotificationIDs.alertCategory)
        notifier(.es).post(firing(.low), sound: true, category: AlertNotificationIDs.alertCategory)
        let en = center.added[0].content, es = center.added[1].content
        #expect(en.title == "Low")
        #expect(en.body == "62 mg/dL ↘")
        #expect(es.title == "Baja")
        #expect(es.body == "62 mg/dL ↘")
        #expect(center.added[0].identifier == AlertNotificationIDs.notificationID(.level))
        #expect(en.userInfo[Notifier.sideKey] as? String == "low")
    }

    /// contracts/alerts/SPEC.md §1: no threshold; fast alerts add the rate; no data says how long and the last value.
    @Test func bodyFollowsTheSpec() {
        let en = AlertText(language: .en), es = AlertText(language: .es)
        let fast = firing(.fallingFast, mgdl: 120, rate: -2.4)
        #expect(en.body(fast, now: clock.now) == "120 mg/dL ↓ · falling 2.4 mg/dL/min")
        #expect(es.body(fast, now: clock.now) == "120 mg/dL ↓ · baja 2,4 mg/dL/min")
        #expect(en.body(firing(.risingFast, mgdl: 150, rate: 2.0), now: clock.now).hasSuffix(" · rising 2.0 mg/dL/min"))
        #expect(!en.body(firing(.fallingFast, rate: nil), now: clock.now).contains("·"))
        let old = AlertFiring(
            id: "n", kind: .noData, at: clock.now,
            reading: GlucoseSample(at: clock.now.addingTimeInterval(-25 * 60), mgdl: 120, rate: nil), threshold: 20,
            n: 1, treated: 0)
        // #195: when the last reading came, never "for 25 min" (a time doesn't go out of date on a notification left up)
        let at = Freshness.timeText(clock.now.timeIntervalSince1970 - 25 * 60, now: clock.now.timeIntervalSince1970)
        #expect(en.body(old, now: clock.now) == "Last reading \(at) · 120 mg/dL")
        #expect(es.body(old, now: clock.now) == "Última lectura \(at) · 120 mg/dL")
        let lo = firing(.veryLow, mgdl: 39, rate: nil)
        #expect(en.body(lo, now: clock.now).hasPrefix("LO · below 40 mg/dL"))
        #expect(es.headline(firing(.low, treated: 2)) == "Baja · vuelve a comer")
    }

    /// contracts/settings/units.json: in mmol/L the value, the LO bound and the rate are in the unit, the language's
    /// decimal separator.
    @Test func bodyInMmolWritesEveryNumberInTheUnit() {
        var en = AlertText(language: .en), es = AlertText(language: .es)
        en.unit = .mmol
        es.unit = .mmol
        let fast = firing(.fallingFast, mgdl: 69, rate: -2.4)
        #expect(en.body(fast, now: clock.now) == "3.8 mmol/L ↓ · falling 0.13 mmol/L/min")
        #expect(es.body(fast, now: clock.now) == "3,8 mmol/L ↓ · baja 0,13 mmol/L/min")
        #expect(en.body(firing(.veryLow, mgdl: 39, rate: nil), now: clock.now).hasPrefix("LO · below 2.2 mmol/L"))
        #expect(
            es.body(firing(.extremelyHigh, mgdl: 401, rate: nil), now: clock.now).hasPrefix(
                "HI · por encima de 22,2 mmol/L"))
        // the notifier writes the unit it is given now
        var s = AlertSettings()
        s.rules[.low]?.on = true
        let n = Notifier(center: center, sounds: nil, text: en, clock: clock, unit: { .mmol }, settings: { s })
        n.post(firing(.low, mgdl: 62), sound: false, category: AlertNotificationIDs.alertCategory)
        #expect(center.added.last?.content.body.hasPrefix("3.4 mmol/L") == true)
    }

    @Test func aSilentResumeHasNoSoundAndASoundCopyFailureRingsTheDefault() {
        notifier().post(firing(.low), sound: false, category: AlertNotificationIDs.alertCategory)
        #expect(center.added[0].content.sound == nil)
        // sounds: nil = nothing could be copied → the default sound, never silence
        notifier().post(firing(.low), sound: true, category: AlertNotificationIDs.alertCategory)
        #expect(center.added[1].content.sound != nil)
    }

    /// #195: a post without sound (a value update, a silent show, a re-post after an action) is passive: it replaces
    /// what the list shows and never presents a banner again; an alarm that rings stays as loud as it was.
    @Test func aSilentPostIsPassiveARingIsNot() {
        var s = AlertSettings()
        s.rules[.veryLow]?.alarm = true
        notifier(settings: s, throughFocus: true).post(firing(.veryLow, mgdl: 50), sound: true, category: "")
        notifier(settings: s, throughFocus: true).post(firing(.veryLow, mgdl: 48), sound: false, category: "")
        notifier().post(firing(.low), sound: false, category: AlertNotificationIDs.alertCategory)
        #expect(center.added.map(\.content.interruptionLevel) == [.timeSensitive, .passive, .passive])
        #expect(center.added.map(\.identifier).allSatisfy { $0 == AlertNotificationIDs.notificationID(.level) })
    }

    /// #195 review: while Sukrara is in front, only an alert posted again without sound stays out of the banners. A
    /// ringing alert whose sound is None (no sound in its content) still shows, and so do the muted and stopped notes.
    @Test func inFrontOnlyASilentRepostStaysOutOfTheBanners() {
        var s = AlertSettings()
        s.sounds.pack = "none"
        let n = notifier(settings: s)
        n.post(firing(.veryLow, mgdl: 50), sound: true, category: AlertNotificationIDs.alertCategory)
        n.post(firing(.veryLow, mgdl: 44), sound: false, category: AlertNotificationIDs.alertCategory)
        n.postMuted(.low, until: clock.now.addingTimeInterval(1800))
        n.monitoringStopped("Bluetooth is off")
        let shown = center.added.map { Notifier.presentation($0.content) }
        #expect(center.added[0].content.sound == nil)
        #expect(shown == [[.banner, .sound, .list], [.list], [.banner, .sound, .list], [.banner, .sound, .list]])
    }

    /// #184: a Focus holds Sukrara's alerts back by default; letting it through makes the alarms Time Sensitive, when
    /// macOS allows it.
    @Test func alarmsAreTimeSensitiveOnlyWhenThePersonLetsSukraraThrough() {
        var s = AlertSettings()
        s.rules[.veryLow]?.alarm = true
        notifier(settings: s).post(firing(.veryLow, mgdl: 50), sound: true, category: "")
        notifier(settings: s).post(firing(.veryLow, mgdl: 50), sound: true, category: "", escalated: true)
        notifier(settings: s).postTest(.veryLow)
        notifier(settings: s, throughFocus: true).post(firing(.veryLow, mgdl: 50), sound: true, category: "")
        center.timeSensitiveAllowed = false
        notifier(settings: s, throughFocus: true).post(firing(.veryLow, mgdl: 50), sound: true, category: "")
        #expect(center.added.map(\.content.interruptionLevel) == [.active, .active, .active, .timeSensitive, .active])
    }

    @Test func actionsMapToAlertActions() {
        let s = AlertSettings()
        typealias IDs = AlertNotificationIDs
        #expect(
            Notifier.action(actionID: IDs.muteAction, side: "low", typed: "", settings: s) == .mute(.low, minutes: 30))
        #expect(
            Notifier.action(actionID: IDs.muteAction, side: "high", typed: "1 h", settings: s)
                == .mute(.high, minutes: 60))
        #expect(
            Notifier.action(actionID: IDs.muteAction, side: "low", typed: "45", settings: s)
                == .mute(.low, minutes: 45))
        #expect(Notifier.action(actionID: IDs.muteAction, side: "low", typed: "soon", settings: s) == .reshow(.low))
        #expect(
            Notifier.action(actionID: IDs.muteAction, side: "high", typed: "0", settings: s) == .reshow(.high))
        let longest = Int(AlertLimits.longestMuteMin)
        #expect(
            Notifier.action(actionID: IDs.muteAction, side: "low", typed: "\(longest + 600)", settings: s)
                == .mute(.low, minutes: longest))
        #expect(
            Notifier.action(actionID: IDs.remindLessAction, side: "high", typed: nil, settings: s) == .remindLess(.high)
        )
        #expect(
            Notifier.action(actionID: IDs.remindAsUsualAction, side: "high", typed: nil, settings: s)
                == .remindAsUsual(.high))
        #expect(
            Notifier.action(actionID: IDs.unmuteAction, side: "no_data", typed: nil, settings: s) == .unmute(.noData))
        #expect(
            Notifier.action(actionID: UNNotificationDefaultActionIdentifier, side: "low", typed: nil, settings: s)
                == nil)
        #expect(Notifier.action(actionID: IDs.unmuteAction, side: "sideways", typed: nil, settings: s) == nil)
    }

    /// 0.14.1 (Android) lesson: an alert whose notification can't be posted is never dropped silently. macOS refused
    /// it: Sukrara posts it again plain (the same words in the same place, no actions, the default sound), writes it to
    /// the event log and lists it for the panel's "An alert couldn't be shown" (the step and macOS's error, never a
    /// value) until an alert of that group is posted.
    @Test func aRefusedAlertIsPostedAgainPlainAndListedUntilAnAlertOfItsGroupIsPosted() throws {
        var failed: Set<String> = []
        let n = notifier()
        n.onFailedSteps = { failed = $0 }
        center.refuse = 1
        n.post(firing(.low), sound: true, category: AlertNotificationIDs.alertCategory)
        try #require(center.added.count == 2)
        let plain = center.added[1]
        #expect(plain.identifier == AlertNotificationIDs.notificationID(.level))
        #expect(plain.content.title == "Low")
        #expect(plain.content.body == "62 mg/dL ↘")
        #expect(plain.content.categoryIdentifier.isEmpty && plain.content.userInfo.isEmpty)
        #expect(plain.content.sound == .default)
        #expect(failed == ["Show:level UNErrorDomain 1"])
        #expect(!failed.contains { $0.contains("62") })

        n.post(firing(.low, mgdl: 60), sound: true, category: AlertNotificationIDs.alertCategory)
        #expect(failed.isEmpty)
    }

    @Test func aPlainAlertRefusedTooStaysListedAndAResumeStaysSilent() throws {
        var failed: Set<String> = []
        let n = notifier()
        n.onFailedSteps = { failed = $0 }
        center.refuse = 2
        n.post(firing(.high, mgdl: 220, rate: 1), sound: false, category: AlertNotificationIDs.alertCategory)
        try #require(center.added.count == 2)
        #expect(center.added[1].content.sound == nil)
        #expect(failed == ["Show:level UNErrorDomain 1", "Plain:level UNErrorDomain 1"])
        // another group posted fine: the level group's failure stays
        n.post(firing(.noData), sound: true, category: AlertNotificationIDs.alertCategory)
        #expect(failed == ["Show:level UNErrorDomain 1", "Plain:level UNErrorDomain 1"])
    }

    /// macOS answers later: a refusal that comes back after the alert was cleared never brings it back.
    @Test func aRefusalAfterTheAlertEndedPostsNothing() {
        final class LateCenter: NotificationPosting {
            var added: [UNNotificationRequest] = []
            var answers: [@MainActor ((any Error)?) -> Void] = []
            var timeSensitiveAllowed = false
            func add(_ request: UNNotificationRequest, done: @escaping @MainActor ((any Error)?) -> Void) {
                added.append(request)
                answers.append(done)
            }
            func removeDelivered(ids: [String]) {}
        }
        let late = LateCenter()
        var failed: Set<String> = []
        let n = Notifier(center: late, sounds: nil, text: AlertText(language: .en), clock: clock) { AlertSettings() }
        n.onFailedSteps = { failed = $0 }
        n.post(firing(.low), sound: true, category: AlertNotificationIDs.alertCategory)
        n.clear(.level)
        late.answers[0](UNError(.notificationsNotAllowed))
        #expect(late.added.count == 1)
        #expect(failed == ["Show:level UNErrorDomain 1"])
    }

    /// *Copy Report*: the failed steps, the app's and macOS's versions; nothing personal.
    @Test func theAlertReportHasTheStepsAndTheVersionsOnly() {
        let report = AlertReport.text(
            failedSteps: ["Show:level UNErrorDomain 1", "Plain:level UNErrorDomain 1"], version: "1.2.3", os: "26.1")
        #expect(
            report
                == "Sukrara 1.2.3 · macOS 26.1\nAlert steps failed: Plain:level UNErrorDomain 1, Show:level UNErrorDomain 1"
        )
    }

    @Test func mutedNoteIsSilentWithItsCategory() {
        notifier().postMuted(.low, until: clock.now.addingTimeInterval(1800))
        let r = center.added[0]
        #expect(r.identifier == AlertNotificationIDs.mutedNotificationID(.low))
        #expect(r.content.title == "Lows muted" && r.content.sound == nil)
        #expect(r.content.categoryIdentifier == AlertNotificationIDs.mutedCategory)
    }

    @Test func preferredLanguageFallsBackToEnglish() {
        #expect(AlertText.preferred(["es-ES", "en"]).language == .es)
        #expect(AlertText.preferred(["fr-FR", "es"]).language == .es)
        #expect(AlertText.preferred(["de"]).language == .en)
    }

    /// #109: a Mac that stopped monitoring says so once, passively (no sound), with why; it goes when it's back.
    @Test func theStoppedMonitoringNoteIsPassiveAndReplaceable() {
        let n = notifier()
        n.monitoringStopped("Not monitoring: they stopped sharing")
        n.monitoringStopped("Not monitoring: they stopped sharing")
        #expect(center.added.count == 2 && Set(center.added.map(\.identifier)) == [Notifier.monitoringStoppedID])
        let c = center.added[0].content
        #expect(c.title == "Sukrara stopped monitoring")
        #expect(c.body.hasPrefix("Not monitoring: they stopped sharing\n"))
        #expect(c.interruptionLevel == .passive && c.sound == nil && c.categoryIdentifier.isEmpty)
        n.monitoringStopped(nil)
        #expect(center.removed == [Notifier.monitoringStoppedID])
    }
}

/// #19 / #184: whether macOS lets Sukrara post Time Sensitive, from the build's entitlement (only a provisioning profile
/// grants it) and the system's answer, and where Sukrara stands with a Focus (the person's choice).
@Suite struct TimeSensitiveTests {
    @Test func aBuildWithoutTheEntitlementSaysAFocusCanHoldTheAlarms() {
        for setting in [UNNotificationSetting.notSupported, .disabled] {
            for permission in [NotificationPermission.notAsked, .allowed, .denied, .quiet] {
                #expect(TimeSensitive(setting: setting, entitled: false, permission: permission) == .unavailable)
            }
        }
    }

    @Test func anEntitledBuildIsOnUnlessThePersonTurnedItOff() {
        #expect(TimeSensitive(setting: .enabled, entitled: true, permission: .allowed) == .on)
        // not asked yet: the system's answer means nothing, the alarms will be Time Sensitive
        #expect(TimeSensitive(setting: .notSupported, entitled: true, permission: .notAsked) == .on)
        #expect(TimeSensitive(setting: .disabled, entitled: true, permission: .notAsked) == .on)
        #expect(TimeSensitive(setting: .disabled, entitled: true, permission: .allowed) == .turnedOff)
        // the system says no even though the build is entitled: the truth, a Focus can hold them back
        #expect(TimeSensitive(setting: .notSupported, entitled: true, permission: .allowed) == .unavailable)
    }

    /// #184: the statement follows the person's choice and what macOS allows, the same words as Android's.
    @Test func focusStatusIsTheChoiceAndWhatMacOSAllows() {
        #expect(FocusStatus(through: false, timeSensitive: .on) == .respected)
        #expect(FocusStatus(through: true, timeSensitive: .on) == .through)
        #expect(FocusStatus(through: false, timeSensitive: .turnedOff) == .respected)
        #expect(FocusStatus(through: true, timeSensitive: .turnedOff) == .turnedOff)
        #expect(FocusStatus(through: true, timeSensitive: .unavailable) == .unavailable)
        let all: [FocusStatus] = [.respected, .through, .turnedOff, .unavailable]
        #expect(all.filter(\.alertsThrough) == [.through])
        #expect(Set(all.map(\.detail)).count == 4)
        #expect(FocusStatus.through.statement == "Sukrara alerts during a Focus")
        #expect(FocusStatus.respected.statement == "Sukrara doesn't alert during a Focus")
    }

    @Test func settingsKeepTheChoice() {
        let store = InMemorySettingsStore()
        let model = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "test")
        #expect(model.focusStatus == nil)  // nil: this run posts no notifications
        model.timeSensitive = .on
        #expect(model.focusStatus == .respected)
        model.throughFocus = true
        #expect(model.focusStatus == .through)
        #expect(store.value(MacSettings.throughFocus))
        model.throughFocus = false
        #expect(model.focusStatus == .respected)
    }
}
