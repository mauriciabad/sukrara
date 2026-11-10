// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

/// Why following can't continue (`LinkState.broken`): a reason the app words in the person's language, never text
/// the follower runtime wrote in English (#77).
public enum LinkProblem: String, Codable, Sendable, Equatable, CaseIterable {
    /// The Keychain doesn't give the follower key (often for a while: Sukrara keeps trying).
    case keyUnreadable
    /// The stored follower state can't be read: stop following and join again.
    case stateDamaged
    /// Stop following couldn't remove the follower key from the Keychain.
    case keyNotRemoved
}
