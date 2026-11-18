// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// How an online source is doing as the person sees it (contracts/sources/health.json; docs/44 "Living with two
/// apps"): one state from the last call's outcome, whether the Mac has a network, and the newest reading's age. The
/// panel, Settings › Source and setup's Connect say it in words; the no-data alert still counts from the reading's
/// time. Android `SourceHealth`.
public enum SourceHealth {
    /// The last call's outcome (nil = nothing asked yet).
    public enum Outcome: String, Sendable, Codable {
        /// The site answered.
        case ok
        /// No answer (DNS, TLS, timeout, refused connection).
        case network
        /// 5xx, or an answer that isn't the site's.
        case server
        /// 404: not a Nightscout at that address.
        case notFound
        /// 401 / 403: the token or API secret.
        case refused
        /// 429: the site asked to wait.
        case rateLimited
        /// LibreLinkUp asks the person to accept new terms (login status 4).
        case terms
        /// LibreLinkUp: the account follows no one, or no longer the person picked.
        case notShared
        /// LibreLinkUp refuses this client (a version it asks for, an endpoint gone): Abbott changed it.
        case retired
    }

    public enum State: String, CaseIterable, Sendable, Codable {
        case connecting, live, late, noNewReadings, offline, unreachable, signedOut, rateLimited
        /// LibreLinkUp's own: new terms to accept, no one shares (any more), stopped answering Sukrara.
        case actionNeeded, notShared, stopped
        /// The source answers and its sensor warms up: nothing new until the warm-up's end (the newest reading is the
        /// old sensor's).
        case warmingUp
    }

    /// Newer than this is live; up to `silentAfter` late (readings come in gaps); older: nothing new reaches the site.
    public static let lateAfter: TimeInterval = 420
    public static let silentAfter: TimeInterval = 900

    /// The state for the last call's `outcome`, the network, the newest reading's age and whether the source's sensor
    /// is `warmingUp` (an answer then says `.warmingUp`, whatever the age).
    public static func state(outcome: Outcome?, online: Bool, newestAge: TimeInterval?, warmingUp: Bool = false)
        -> State
    {
        // a refusal or a rate limit says so whatever the network
        switch outcome {
        case .refused: return .signedOut
        case .rateLimited: return .rateLimited
        case .terms: return .actionNeeded
        case .notShared: return .notShared
        case .retired: return .stopped
        default: break
        }
        guard online else { return .offline }
        switch outcome {
        case nil: return .connecting
        case .network, .server, .notFound: return .unreachable
        case .ok, .refused, .rateLimited, .terms, .notShared, .retired:
            if warmingUp { return .warmingUp }
            guard let age = newestAge else { return .noNewReadings }
            if age <= lateAfter { return .live }
            return age <= silentAfter ? .late : .noNewReadings
        }
    }

    /// The source's `sensor` (its start and the default warm-up) is warming up at `now`, the newest reading taken at
    /// `latest` (`SensorTiming.warming`: no reading since its start).
    public static func warming(_ sensor: WearerSensor?, latest: Date?, now: Date) -> Bool {
        guard let sensor else { return false }
        return SensorTiming.warming(
            start: TimeInterval(sensor.start), warmupEnd: TimeInterval(sensor.warmupEnd),
            latest: latest?.timeIntervalSince1970, now: now.timeIntervalSince1970)
    }
}
