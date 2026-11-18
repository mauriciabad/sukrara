// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// When the Nightscout source asks the site again (contracts/sources/nightscout/poll.json): one cadence after the
/// newest reading plus the uploader's lag, never sooner than `minWait`; overdue, every 30 s, then every minute; after a
/// failure the backoff (a site's longer Retry-After wins); refused, half an hour or a new token.
public enum NightscoutPoll {
    public static let minWait: TimeInterval = 15
    public static let uploadLag: TimeInterval = 45
    public static let minCadence: TimeInterval = 60
    public static let maxCadence: TimeInterval = 300
    public static let overdue: TimeInterval = 30
    public static let late: TimeInterval = 60
    public static let lateAfter: TimeInterval = 300
    public static let backoff: [TimeInterval] = [30, 60, 120, 300]
    public static let refusedWait: TimeInterval = 1800

    /// Seconds to wait. `failures`: in a row (0 = the last call answered); `newest` / `previous`: the two newest
    /// reading times (epoch s).
    public static func wait(
        now: TimeInterval, newest: TimeInterval?, previous: TimeInterval?, failures: Int,
        retryAfter: TimeInterval? = nil, refused: Bool = false
    ) -> TimeInterval {
        if refused { return refusedWait }
        if failures > 0 {
            let b = backoff[min(failures - 1, backoff.count - 1)]
            return max(b, retryAfter ?? 0)
        }
        guard let newest else { return late }
        let cadence = previous.map { min(max(newest - $0, minCadence), maxCadence) } ?? maxCadence
        let due = newest + cadence + uploadLag
        if now < due { return max(minWait, due - now) }
        return now - due > lateAfter ? late : overdue
    }
}
