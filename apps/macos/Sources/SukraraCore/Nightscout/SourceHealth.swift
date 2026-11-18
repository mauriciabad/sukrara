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
    }

    public enum State: String, CaseIterable, Sendable, Codable {
        case connecting, live, late, noNewReadings, offline, unreachable, signedOut, rateLimited
    }

    /// Newer than this is live; up to `silentAfter` late (readings come in gaps); older: nothing new reaches the site.
    public static let lateAfter: TimeInterval = 420
    public static let silentAfter: TimeInterval = 900

    public static func state(outcome: Outcome?, online: Bool, newestAge: TimeInterval?) -> State {
        // a refusal or a rate limit says so whatever the network
        switch outcome {
        case .refused: return .signedOut
        case .rateLimited: return .rateLimited
        default: break
        }
        guard online else { return .offline }
        switch outcome {
        case nil: return .connecting
        case .network, .server, .notFound: return .unreachable
        case .ok, .refused, .rateLimited:
            guard let age = newestAge else { return .noNewReadings }
            if age <= lateAfter { return .live }
            return age <= silentAfter ? .late : .noNewReadings
        }
    }
}
