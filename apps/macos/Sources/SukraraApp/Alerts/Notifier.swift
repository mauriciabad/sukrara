// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
@preconcurrency import UserNotifications

/// What the alert controller asks of the notification layer.
protocol AlertNotifying {
    /// Posts `firing`, replacing its group's notification. `sound` false = a silent re-show after a relaunch.
    /// `escalated` (#39): the wearer's very low nobody answered there: "· no response", Time Sensitive when entitled.
    func post(_ firing: AlertFiring, sound: Bool, category: String, escalated: Bool)
    func clear(_ group: AlertGroup)
    /// The silent "Low alerts muted / Until 10:42" note with Unmute and Change Time…
    func postMuted(_ side: AlertSide, until: Date)
    func removeMuted(_ side: AlertSide)
    /// Settings › Alerts › Send Test Alert: `kind`'s notification with its sound and alarm level, titled "Test", with
    /// no actions (it mutes nothing) and its own identifier (it never replaces a real alert).
    func postTest(_ kind: AlertKind)
    /// An `AlertLog` line for the event log (a mute, an unmute, Mute all and its end…).
    func log(_ line: String)
    /// The passive "Sukrara stopped monitoring" note with why (`reason`, the panel's line); nil removes it.
    func monitoringStopped(_ reason: String?)
}

extension AlertNotifying {
    func post(_ firing: AlertFiring, sound: Bool, category: String) {
        post(firing, sound: sound, category: category, escalated: false)
    }
}

/// The part of `UNUserNotificationCenter` the notifier uses (tests record requests instead of posting).
protocol NotificationPosting {
    func add(_ request: UNNotificationRequest)
    func removeDelivered(ids: [String])
    /// Whether Time Sensitive delivery is on for the app (System Settings > Notifications).
    var timeSensitiveAllowed: Bool { get }
}

/// Builds the alert notifications: self-contained text in the user's language, the pack sound (copied where
/// Notification Center finds it), the actions' category, Time Sensitive for alarms when allowed.
final class Notifier: AlertNotifying {
    nonisolated static let sideKey = "side"
    nonisolated static let firingKey = "firing"
    /// The one "Sukrara stopped monitoring" note (a new one replaces it).
    nonisolated static let monitoringStoppedID = "app.mauri.sukrara.monitoring-stopped"

    private let center: any NotificationPosting
    private let sounds: SoundLibrary?
    private let text: AlertText
    private let clock: any TimeSource
    private let settings: () -> AlertSettings
    /// The person lets Sukrara through a Focus (schema `alerts.throughFocus`, #184): read at each post.
    private let throughFocus: () -> Bool
    /// The glucose unit now (`general.unit`): the body writes the value in it.
    private let unit: () -> GlucoseUnit
    private var lastVariant: [AlertKind: Int] = [:]
    /// The bug-report log: which alert was posted or cleared, never a value.
    var eventLog: RotatingFileLog?

    init(
        center: any NotificationPosting, sounds: SoundLibrary?, text: AlertText = .preferred(), clock: any TimeSource,
        unit: @escaping () -> GlucoseUnit = { .mgdl }, throughFocus: @escaping () -> Bool = { false },
        settings: @escaping () -> AlertSettings
    ) {
        self.unit = unit
        self.throughFocus = throughFocus
        self.center = center
        self.sounds = sounds
        self.text = text
        self.clock = clock
        self.settings = settings
    }

    func post(_ f: AlertFiring, sound: Bool, category: String, escalated: Bool) {
        let s = settings()
        let content = UNMutableNotificationContent()
        content.title = text.title(f, escalated: escalated)
        var words = text
        words.unit = unit()
        content.body = words.body(f, now: clock.now)
        content.categoryIdentifier = category
        content.threadIdentifier = AlertNotificationIDs.alertCategory
        content.userInfo = [Self.sideKey: f.kind.side.rawValue, Self.firingKey: f.id]
        content.interruptionLevel =
            (s[f.kind].alarm || escalated) && timeSensitive() ? .timeSensitive : .active
        content.sound = sound ? notificationSound(f.kind, s) : nil
        center.add(
            UNNotificationRequest(
                identifier: AlertNotificationIDs.notificationID(f.kind.group), content: content, trigger: nil))
        eventLog?.write("alert \(f.kind.rawValue) n=\(f.n) \(sound ? "posted" : "resumed silently")")
    }

    func postTest(_ kind: AlertKind) {
        let s = settings()
        let f = Self.testFiring(kind, threshold: s[kind].threshold, at: clock.now)
        let content = UNMutableNotificationContent()
        content.title = text.testTitle(f)
        content.body = L10n.t("This is how this alert looks and sounds. Nothing was muted or logged.")
        content.interruptionLevel = s[kind].alarm && timeSensitive() ? .timeSensitive : .active
        content.sound = notificationSound(kind, s)
        center.add(
            UNNotificationRequest(identifier: "test-\(kind.rawValue)", content: content, trigger: nil))
        eventLog?.write("alert \(kind.rawValue) test posted")
    }

    /// An alarm-level alert posts Time Sensitive only when the person lets Sukrara through a Focus and macOS allows it:
    /// by default a Focus holds Sukrara's alerts back like any app's (#184).
    private func timeSensitive() -> Bool { center.timeSensitiveAllowed && throughFocus() }

    /// The test notification's firing: a value just past the threshold, labelled as a test by its title.
    static func testFiring(_ kind: AlertKind, threshold: Double, at now: Date) -> AlertFiring {
        let mgdl: Double
        let rate: Double?
        switch kind {
        case .veryLow, .low: (mgdl, rate) = (threshold - 6, -0.6)
        case .high, .veryHigh, .extremelyHigh: (mgdl, rate) = (threshold + 12, 0.8)
        case .fallingFast: (mgdl, rate) = (120, -(threshold + 0.6))
        case .risingFast: (mgdl, rate) = (150, threshold + 0.4)
        case .noData: (mgdl, rate) = (110, nil)
        }
        let at = kind == .noData ? now.addingTimeInterval(-threshold * 60 - 300) : now
        return AlertFiring(
            id: "test", kind: kind, at: now, reading: GlucoseSample(at: at, mgdl: mgdl, rate: rate),
            threshold: threshold, n: 1, treated: 0)
    }

    func log(_ line: String) { eventLog?.write(line) }

    /// Passive (no sound, no banner through a Focus): a Mac that stopped monitoring can't ring, so it says so once,
    /// in the Notification Center too. A click opens the panel (no actions), which shows the same line and its exits.
    func monitoringStopped(_ reason: String?) {
        guard let reason else {
            center.removeDelivered(ids: [Self.monitoringStoppedID])
            return
        }
        let content = UNMutableNotificationContent()
        content.title = L10n.t("Sukrara stopped monitoring")
        content.body = reason + "\n" + L10n.t("No glucose readings or alerts until it's back.")
        content.interruptionLevel = .passive
        content.sound = nil
        center.add(UNNotificationRequest(identifier: Self.monitoringStoppedID, content: content, trigger: nil))
        eventLog?.write("monitoring stopped: notice posted")
    }

    func clear(_ group: AlertGroup) {
        eventLog?.write("alert \(group.rawValue) cleared")
        center.removeDelivered(ids: [AlertNotificationIDs.notificationID(group)])
    }

    func postMuted(_ side: AlertSide, until: Date) {
        let content = UNMutableNotificationContent()
        content.title = text.mutedTitle(side)
        content.body = text.mutedBody(until: SukraraCore.Clock.time(until))
        content.categoryIdentifier = AlertNotificationIDs.mutedCategory
        content.userInfo = [Self.sideKey: side.rawValue]
        content.interruptionLevel = .passive
        content.sound = nil
        center.add(
            UNNotificationRequest(
                identifier: AlertNotificationIDs.mutedNotificationID(side), content: content, trigger: nil))
    }

    func removeMuted(_ side: AlertSide) {
        center.removeDelivered(ids: [AlertNotificationIDs.mutedNotificationID(side)])
    }

    private func notificationSound(_ kind: AlertKind, _ s: AlertSettings) -> UNNotificationSound? {
        var rng = SystemRandomNumberGenerator()
        switch SoundLibrary.pick(kind, s, last: lastVariant[kind], using: &rng) {
        case .none: return nil
        case .system: return .default
        case .pack(let name):
            lastVariant[kind] = name.split(separator: "_").last.flatMap { Int($0) }
            // a sound that can't be copied rings the default one, never silence
            guard let file = sounds?.install(name) else { return .default }
            return UNNotificationSound(named: UNNotificationSoundName(file))
        }
    }

    /// The categories (SPEC §2): every alert has *Mute…* (typed length, empty = the default) and *Remind Less* /
    /// *Remind as Usual*; one whose repeats Remind Less wouldn't slow has Mute… alone. The muted note: *Unmute* +
    /// *Change Time…*.
    static func categories(_ text: AlertText) -> Set<UNNotificationCategory> {
        let mute = UNTextInputNotificationAction(
            identifier: AlertNotificationIDs.muteAction, title: text.mute, options: [],
            textInputButtonTitle: text.muteButton, textInputPlaceholder: text.mutePlaceholder)
        let less = UNNotificationAction(identifier: AlertNotificationIDs.remindLessAction, title: text.remindLess)
        let usual = UNNotificationAction(
            identifier: AlertNotificationIDs.remindAsUsualAction, title: text.remindAsUsual)
        let unmute = UNNotificationAction(identifier: AlertNotificationIDs.unmuteAction, title: text.unmute)
        let change = UNTextInputNotificationAction(
            identifier: AlertNotificationIDs.changeMuteAction, title: text.changeTime, options: [],
            textInputButtonTitle: text.muteButton, textInputPlaceholder: text.mutePlaceholder)
        func category(_ id: String, _ actions: [UNNotificationAction]) -> UNNotificationCategory {
            UNNotificationCategory(identifier: id, actions: actions, intentIdentifiers: [])
        }
        return [
            category(AlertNotificationIDs.alertCategory, [mute, less]),
            category(AlertNotificationIDs.alertRelaxedCategory, [mute, usual]),
            category(AlertNotificationIDs.alertMuteOnlyCategory, [mute]),
            category(AlertNotificationIDs.mutedCategory, [unmute, change]),
        ]
    }

    /// The user's answer to a notification as an alert action, or nil (a tap on the body, an unknown side). Mute… with
    /// nothing typed mutes for the default length; a typed length is clamped to the longest allowed; typed text that
    /// isn't a length changes nothing and shows the notification again (`reshow`).
    static func action(actionID: String, side rawSide: String?, typed: String?, settings: AlertSettings)
        -> AlertAction?
    {
        guard let rawSide, let side = AlertSide(rawValue: rawSide) else { return nil }
        switch actionID {
        case AlertNotificationIDs.muteAction, AlertNotificationIDs.changeMuteAction:
            let input = typed?.trimmingCharacters(in: .whitespaces) ?? ""
            let minutes = input.isEmpty ? settings.notificationActions.muteMin : Durations.parse(input)
            guard let minutes, minutes > 0 else { return .reshow(side) }
            return .mute(side, minutes: min(minutes, Int(AlertLimits.longestMuteMin)))
        case AlertNotificationIDs.remindLessAction: return .remindLess(side)
        case AlertNotificationIDs.remindAsUsualAction: return .remindAsUsual(side)
        case AlertNotificationIDs.unmuteAction: return .unmute(side)
        default: return nil
        }
    }
}

/// `UNUserNotificationCenter` behind `NotificationPosting`. Created only by `AppServices` (a launch whose plan allows
/// notifications), never by tests or offline runs.
final class SystemNotificationCenter: NotificationPosting {
    private let center = UNUserNotificationCenter.current()
    /// The build's Time Sensitive entitlement (`Entitlements.timeSensitive`).
    private let entitled: Bool
    /// Whether the alarms break through a Focus, as of the last `refresh`.
    private(set) var timeSensitive: TimeSensitive
    var timeSensitiveAllowed: Bool { timeSensitive == .on }

    init(entitled: Bool) {
        self.entitled = entitled
        timeSensitive = entitled ? .on : .unavailable
    }

    func add(_ request: UNNotificationRequest) {
        center.add(request) { error in
            if let error {
                AppLog.alerts.error("notification not posted: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func removeDelivered(ids: [String]) { center.removeDeliveredNotifications(withIdentifiers: ids) }

    /// Re-reads what the user allows (launch, app activation, panel open).
    func refresh() async -> NotificationPermission {
        let s = await center.notificationSettings()
        let permission = NotificationPermission(s)
        timeSensitive = TimeSensitive(setting: s.timeSensitiveSetting, entitled: entitled, permission: permission)
        return permission
    }
}

/// What the user allows, for the panel's warning row.
enum NotificationPermission: Equatable {
    case notAsked, allowed, denied
    /// Allowed, but without banners or sound: alerts may go unnoticed.
    case quiet

    init(_ s: UNNotificationSettings) {
        switch s.authorizationStatus {
        case .notDetermined: self = .notAsked
        case .denied: self = .denied
        default: self = s.alertSetting == .enabled && s.soundSetting == .enabled ? .allowed : .quiet
        }
    }

    var needsAttention: Bool { self == .denied || self == .quiet }
}
