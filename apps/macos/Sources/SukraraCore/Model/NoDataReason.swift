// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// Why there is no glucose value to show and what can be done about it (Android `NoDataReason`, #72): the menu bar
/// panel says it with a sentence of why and its action, the widgets with the reason and where to go. Never a bare "No
/// readings yet". The app decides it (`AppModel.noDataReason`) and hands it to the widgets in their snapshot, so the
/// panel and the widgets give the same reason; each words it in the person's language.
public enum NoDataReason: Codable, Sendable, Equatable {
    /// A dev or snapshot run: nothing is monitored on purpose.
    case offlineBuild
    /// Nobody is followed (first run, after Stop following), or an invite is being looked up.
    case notFollowing
    /// This Mac asked to follow; the wearer allows it on their phone.
    case waitingApproval
    /// The wearer said no.
    case declined
    /// Another device used the invite first.
    case inviteUsed
    /// The invite expired before the wearer answered.
    case inviteExpired
    /// The wearer stopped sharing with this Mac.
    case revoked
    /// Following can't continue.
    case broken(LinkProblem)
    /// Following, but nothing from the wearer's phone: `since` = the last reading (epoch s), nil = none yet.
    case wearerSilent(since: TimeInterval?)
    /// Juggluco on this network gives no reading.
    case juggluco(JugglucoWait)
    /// The Nightscout site gives no reading (contracts/sources/health.json in the panel's words).
    case nightscout(NightscoutWait)
    /// LibreLinkUp gives no reading.
    case libreLinkUp(LibreLinkUpWait)

    /// Why Juggluco gives no reading (`LanState` in words the widget can carry).
    public enum JugglucoWait: Codable, Sendable, Equatable {
        case looking, notFound, firstReading, refusedSecret, localNetworkDenied
        case unreachable(host: String)
        case notHomeNetwork(typed: String)
    }

    /// Why the Nightscout site gives no reading.
    public enum NightscoutWait: Codable, Sendable, Equatable {
        /// The first call is on its way.
        case connecting
        /// The site answers but has no glucose at all.
        case noGlucose
        /// The site answers, nothing new since `since` (epoch s): the uploader is the place to look.
        case silent(since: TimeInterval)
        /// No network on this Mac: it catches up when it's back.
        case offline
        /// A network, but no answer from the site.
        case unreachable(host: String)
        /// The site refused the token or API secret.
        case signedOut
        /// The site asked to wait.
        case rateLimited
    }

    /// Why LibreLinkUp gives no reading (contracts/sources/health.json with LibreLinkUp's own states).
    public enum LibreLinkUpWait: Codable, Sendable, Equatable {
        /// No account signed in, or no one picked yet: Connect.
        case notSignedIn
        /// The first call is on its way.
        case connecting
        /// LibreLinkUp answers but has no glucose at all (the Libre app sends it once its sensor runs).
        case noGlucose
        /// LibreLinkUp answers, nothing new since `since` (epoch s): the Libre app is the place to look.
        case silent(since: TimeInterval)
        /// No network on this Mac: it catches up when it's back.
        case offline
        /// A network, but no answer from LibreLinkUp: it tries again by itself.
        case unreachable
        /// LibreLinkUp refused the password.
        case signedOut
        /// LibreLinkUp asked to wait.
        case rateLimited
        /// New terms to accept in the LibreLinkUp app.
        case terms
        /// No one shares with this account now (or not the person picked).
        case notShared
        /// LibreLinkUp stopped answering Sukrara: Abbott changed it.
        case stopped
    }

    /// What the panel offers for it: follow someone (a new invite), the Following settings (cancel a request, stop
    /// following), Juggluco's settings (address, secret), the Nightscout source's (site, token) or LibreLinkUp's
    /// (sign in, whose glucose).
    public enum Action: Sendable, Equatable { case follow, following, juggluco, nightscout, libreLinkUp }

    public var action: Action? {
        switch self {
        case .notFollowing, .declined, .inviteUsed, .inviteExpired, .revoked: .follow
        case .waitingApproval, .broken: .following
        case .juggluco(.looking), .juggluco(.firstReading): nil
        case .juggluco: .juggluco
        case .nightscout(.unreachable), .nightscout(.signedOut): .nightscout
        case .nightscout: nil
        case .libreLinkUp(.notSignedIn), .libreLinkUp(.signedOut), .libreLinkUp(.notShared), .libreLinkUp(.terms),
            .libreLinkUp(.stopped):
            .libreLinkUp
        case .libreLinkUp: nil
        case .offlineBuild, .wearerSilent: nil
        }
    }
}
