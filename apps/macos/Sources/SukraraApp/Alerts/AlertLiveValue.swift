// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraGraph

/// When an alert's notification shows the newest reading again, without sound (#195, SPEC §1 "Showing an alert"): a
/// value left up for minutes is wrong (glucose always moves), but every wiggle posted again is a needless update. So it
/// is updated only when what it says changes enough: the value moved `minMoveMgdl` or more, the arrow changed, or the
/// card's freshness step did (the reading's time once late, the ring once stale: `AlertText.look`). Never in the
/// `afterRing` after the alert rang. The same rule as Android's `AlertLiveValue.kt`.
enum AlertLiveValue {
    /// The smallest move of the value (mg/dL, about 0.3 mmol/L) that updates the notification.
    static let minMoveMgdl = 5
    /// No update this soon after the alert rang (Android stops a sound still playing on a silent update; the Mac keeps
    /// the same rule).
    static let afterRing: TimeInterval = 10

    /// What the notification says about a reading: its value, its arrow (or the ring), its time shown, stale.
    struct Look: Equatable {
        var mgdl: Int
        var arrow: String?
        var late: Bool
        var stale: Bool
    }

    /// `r`'s look at `now`, the card's.
    static func look(_ r: GlucoseSample, now: Date) -> Look {
        let face = AlertText.look(r, now: now)
        return Look(mgdl: Int(r.mgdl.rounded()), arrow: face.arrowText, late: face.fresh != .fresh, stale: face.strike)
    }

    /// Whether the notification showing `shown` is updated to `next`.
    static func changed(_ shown: Look, _ next: Look) -> Bool {
        abs(next.mgdl - shown.mgdl) >= minMoveMgdl || next.arrow != shown.arrow || next.late != shown.late
            || next.stale != shown.stale
    }

    /// Whether to update now: something changed and the alert didn't ring in the last `afterRing` (`rangAt` nil = it
    /// didn't ring since this notification was shown).
    static func due(_ shown: Look, _ next: Look, rangAt: Date?, now: Date) -> Bool {
        (rangAt.map { now.timeIntervalSince($0) >= afterRing } ?? true) && changed(shown, next)
    }

    /// When `r`'s look changes by itself next (the card's next freshness step after `now`), or nil.
    static func nextStep(_ r: GlucoseSample, now: Date) -> Date? {
        Freshness.nextChangeAt(r.at.timeIntervalSince1970, now: now.timeIntervalSince1970)
            .map { Date(timeIntervalSince1970: $0) }
    }
}
