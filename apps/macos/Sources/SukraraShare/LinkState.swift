// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore

/// Whether this Mac follows a wearer, for the menu and the follow window. Every state has a visible exit:
/// `notFollowing` → join; `joining` → cancel; `requested` → cancel request (= stop following); `following` → stop
/// following; `declined` / `inviteUsed` / `revoked` / `broken` → stop following, then join again.
public enum LinkState: Sendable, Equatable {
    /// Not following anyone (first run, or after "Stop following").
    case notFollowing
    /// Looking up the invite on the relays.
    case joining
    /// Access asked; the wearer allows it on their phone. `code`: the match code a wearer may ask for after a leaked
    /// link. `pairing`: a nearby join's code comparison (nil for a link).
    case requested(code: String, wearer: String?, pairing: PairingCheck?)
    /// Following. `canWrite`: the wearer allowed this Mac to add treatments.
    case following(wearer: String?, canWrite: Bool)
    /// The wearer said no.
    case declined
    /// Another device used this invite first.
    case inviteUsed
    /// The wearer stopped sharing with this Mac.
    case revoked
    /// Following can't continue: why (Keychain unreadable, state damaged), worded by the app.
    case broken(LinkProblem)

    /// A nearby join waiting (PROTOCOL-v2 "Nearby pairing"): `code` = the 6 digits both devices show (nil until the
    /// wearer's phone answered); `confirmed` = the person said the phone shows the same.
    public struct PairingCheck: Sendable, Equatable {
        public var code: String?
        public var confirmed: Bool

        public init(code: String?, confirmed: Bool) {
            self.code = code
            self.confirmed = confirmed
        }
    }

    /// Readings may be shown as the wearer's (only while following).
    public var isFollowing: Bool {
        if case .following = self { return true }
        return false
    }
}
