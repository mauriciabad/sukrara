// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// Notification category and action identifiers for the alerts (used by the notifier). Every alert has *Mute…* (text
/// input; empty = `muteMin`) and *Remind less* / *Remind as usual* (SPEC §2); the kinds carbs answer (very low, low,
/// falling fast) and their muted note have *Log Carbs…* first on a Mac that can add entries: the same category with
/// `logCarbsSuffix`.
public enum AlertNotificationIDs {
    /// An alert that Remind less would slow down.
    public static let alertCategory = "app.mauri.sukrara.alert"
    /// An alert already relaxed (its second action is Remind as usual).
    public static let alertRelaxedCategory = "app.mauri.sukrara.alert.relaxed"
    /// An alert whose repeat isn't faster than Remind less (only Mute…).
    public static let alertMuteOnlyCategory = "app.mauri.sukrara.alert.muteOnly"
    /// The silent "Low alerts muted / Until 10:42" note, with Unmute and Change time.
    public static let mutedCategory = "app.mauri.sukrara.muted"

    public static let muteAction = "app.mauri.sukrara.alert.mute"
    public static let remindLessAction = "app.mauri.sukrara.alert.remindLess"
    public static let remindAsUsualAction = "app.mauri.sukrara.alert.remindAsUsual"
    public static let unmuteAction = "app.mauri.sukrara.alert.unmute"
    public static let changeMuteAction = "app.mauri.sukrara.alert.changeMute"
    /// *Log Carbs…*: opens Add Treatment with Carbs focused.
    public static let logCarbsAction = "app.mauri.sukrara.alert.logCarbs"
    /// A category whose first action is *Log Carbs…*.
    public static let logCarbsSuffix = ".logCarbs"

    /// One delivered notification per group (a new alert of the group replaces the previous one).
    public static func notificationID(_ group: AlertGroup) -> String { "app.mauri.sukrara.alert.\(group.rawValue)" }
    public static func mutedNotificationID(_ side: AlertSide) -> String { "app.mauri.sukrara.muted.\(side.rawValue)" }

    /// The category an alert of `kind` posts with; `canLog` = this Mac can add entries now.
    public static func category(for kind: AlertKind, _ s: AlertSettings, relaxed: Bool, canLog: Bool = false) -> String
    {
        let base = relaxed ? alertRelaxedCategory : s.canRelax(kind) ? alertCategory : alertMuteOnlyCategory
        return logs(kind, canLog) ? base + logCarbsSuffix : base
    }

    /// The category of `side`'s muted note.
    public static func mutedCategory(_ side: AlertSide, canLog: Bool) -> String {
        logs(side.mainKind, canLog) ? mutedCategory + logCarbsSuffix : mutedCategory
    }

    /// *Log Carbs…* is offered: carbs answer `kind` and this Mac can add entries.
    private static func logs(_ kind: AlertKind, _ canLog: Bool) -> Bool { canLog && kind.treatment == .carbs }
}
