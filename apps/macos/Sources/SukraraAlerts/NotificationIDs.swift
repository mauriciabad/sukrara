// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// Notification category and action identifiers for the alerts (used by the notifier). Every alert has *Remind less* /
/// *Remind as usual* (SPEC §2) and *Mute* (text input; empty = `muteMin`), in that order; every kind a treatment
/// answers (carbs on the low side, rapid insulin on the high side: all but no data) and its side's muted note have
/// *Treatment* first on a Mac that can add entries: the same category with `treatmentSuffix`. No action title has an
/// ellipsis (the owner's review, 2026-11-14).
public enum AlertNotificationIDs {
    /// An alert that Remind less would slow down.
    public static let alertCategory = "app.mauri.sukrara.alert"
    /// An alert already relaxed (its second action is Remind as usual).
    public static let alertRelaxedCategory = "app.mauri.sukrara.alert.relaxed"
    /// An alert whose repeat isn't faster than Remind less (only Mute).
    public static let alertMuteOnlyCategory = "app.mauri.sukrara.alert.muteOnly"
    /// The silent "Low alerts muted / Until 10:42" note, with Unmute (the person unmutes and mutes again for a new length).
    public static let mutedCategory = "app.mauri.sukrara.muted"

    public static let muteAction = "app.mauri.sukrara.alert.mute"
    public static let remindLessAction = "app.mauri.sukrara.alert.remindLess"
    public static let remindAsUsualAction = "app.mauri.sukrara.alert.remindAsUsual"
    public static let unmuteAction = "app.mauri.sukrara.alert.unmute"
    /// *Treatment*: opens Add Treatment as an answer to the side's alert (THE kinds' order then focuses its field).
    public static let treatmentAction = "app.mauri.sukrara.alert.treatment"
    /// A category whose first action is *Treatment*.
    public static let treatmentSuffix = ".treatment"

    /// One delivered notification per group (a new alert of the group replaces the previous one).
    public static func notificationID(_ group: AlertGroup) -> String { "app.mauri.sukrara.alert.\(group.rawValue)" }
    public static func mutedNotificationID(_ side: AlertSide) -> String { "app.mauri.sukrara.muted.\(side.rawValue)" }

    /// The category an alert of `kind` posts with; `canLog` = this Mac can add entries now.
    public static func category(for kind: AlertKind, _ s: AlertSettings, relaxed: Bool, canLog: Bool = false) -> String
    {
        let base = relaxed ? alertRelaxedCategory : s.canRelax(kind) ? alertCategory : alertMuteOnlyCategory
        return logs(kind, canLog) ? base + treatmentSuffix : base
    }

    /// The category of `side`'s muted note.
    public static func mutedCategory(_ side: AlertSide, canLog: Bool) -> String {
        logs(side.mainKind, canLog) ? mutedCategory + treatmentSuffix : mutedCategory
    }

    /// *Treatment* is offered: a treatment answers `kind` (not no data) and this Mac can add entries.
    private static func logs(_ kind: AlertKind, _ canLog: Bool) -> Bool { canLog && kind.treatment != nil }
}
