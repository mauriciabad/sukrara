// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore

/// The event log's lines for what changed the alerts' state (the Android app's *Lately* history), worded like the app's
/// own lines (`MuteCopy`: side names, `Durations`), so a person can see afterwards why something did or didn't ring.
/// Never a glucose value.
enum AlertLog {
    /// "Low alerts muted for 30 min".
    static func muted(_ side: AlertSide, minutes: Int) -> String {
        "\(MuteCopy.sideName(side)) alerts muted for \(Durations.text(minutes))"
    }

    /// "Low alerts unmuted".
    static func unmuted(_ side: AlertSide) -> String { "\(MuteCopy.sideName(side)) alerts unmuted" }

    /// *Remind Less*: "Low reminders every 30 min" (`MuteCopy.sideLess`).
    static func remindLess(_ side: AlertSide, intervalMin: Int) -> String {
        MuteCopy.sideLess(side, intervalMin: intervalMin)
    }

    /// "Low reminders as usual".
    static func remindAsUsual(_ side: AlertSide) -> String { "\(MuteCopy.sideName(side)) reminders as usual" }

    /// Mute all: "All alerts muted for 2 h" / "All alerts muted until you turn it off".
    static func allMuted(until: Date, now: Date) -> String {
        until >= muteAllForever
            ? "All alerts muted until you turn it off"
            : "All alerts muted for \(Durations.text(seconds: until.timeIntervalSince(now)))"
    }

    /// Mute all turned off.
    static let allUnmuted = "Alerts unmuted"

    /// A timed Mute all that ran out by itself.
    static let allBack = "Alerts back on"

    /// A side's Mute / Remind Less ended by the back-in-range margin: "Low back in range: alerts as usual".
    static func back(_ side: AlertSide) -> String { "\(MuteCopy.sideName(side)) back in range: alerts as usual" }
}
