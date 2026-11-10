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
    /// The wearer stopped sharing with this Mac.
    case revoked
    /// Following can't continue.
    case broken(LinkProblem)
    /// Following, but nothing from the wearer's phone: `since` = the last reading (epoch s), nil = none yet.
    case wearerSilent(since: TimeInterval?)
    /// Juggluco on this network gives no reading.
    case juggluco(JugglucoWait)

    /// Why Juggluco gives no reading (`LanState` in words the widget can carry).
    public enum JugglucoWait: Codable, Sendable, Equatable {
        case looking, notFound, firstReading, refusedSecret
        case unreachable(host: String)
        case notHomeNetwork(typed: String)
    }

    /// What the panel offers for it: follow someone (a new invite), the Following settings (cancel a request, stop
    /// following), or Juggluco's settings (address, secret).
    public enum Action: Sendable, Equatable { case follow, following, juggluco }

    public var action: Action? {
        switch self {
        case .notFollowing, .declined, .inviteUsed, .revoked: .follow
        case .waitingApproval, .broken: .following
        case .juggluco(.looking), .juggluco(.firstReading): nil
        case .juggluco: .juggluco
        case .offlineBuild, .wearerSilent: nil
        }
    }
}
