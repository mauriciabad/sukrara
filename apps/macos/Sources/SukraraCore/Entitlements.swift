// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Security

/// The entitlements this build was signed with that only a provisioning profile grants. `Bundle/bundle.sh` adds them
/// only when it embeds a profile, so a build without one (ad hoc, a dev build, an Apple Development signature with no
/// profile) has none, and the app falls back: alarms as normal notifications, the login keychain, `sukrara:` links.
/// Read once at launch (`current`); tests build the value they need.
public struct Entitlements: Equatable, Sendable {
    /// `com.apple.developer.usernotifications.time-sensitive`: alarms may break through a Focus.
    public var timeSensitive: Bool
    /// `keychain-access-groups`: the data-protection keychain groups this build may use.
    public var keychainAccessGroups: [String]
    /// `com.apple.developer.associated-domains` (`applinks:sukrara.mauri.app`: the web invite opens the app).
    public var associatedDomains: [String]

    public init(timeSensitive: Bool = false, keychainAccessGroups: [String] = [], associatedDomains: [String] = []) {
        self.timeSensitive = timeSensitive
        self.keychainAccessGroups = keychainAccessGroups
        self.associatedDomains = associatedDomains
    }

    /// A build without a provisioning profile.
    public static let none = Entitlements()

    /// Sukrara's own data-protection keychain group when this build may use it, else nil (the login keychain).
    public var keychainAccessGroup: String? {
        keychainAccessGroups.contains(SukraraIDs.keychainAccessGroup) ? SukraraIDs.keychainAccessGroup : nil
    }

    /// Whether the web invite (`https://sukrara.mauri.app/join#…`) opens this app as a universal link.
    public var universalLinks: Bool { associatedDomains.contains(SukraraIDs.associatedDomain) }

    /// This process's own entitlements (none for a test runner or an unsigned tool).
    public static func current() -> Entitlements {
        guard let task = SecTaskCreateFromSelf(nil) else { return .none }
        func value(_ key: String) -> Any? { SecTaskCopyValueForEntitlement(task, key as CFString, nil) }
        return Entitlements(
            timeSensitive: value("com.apple.developer.usernotifications.time-sensitive") as? Bool ?? false,
            keychainAccessGroups: value("keychain-access-groups") as? [String] ?? [],
            associatedDomains: value("com.apple.developer.associated-domains") as? [String] ?? [])
    }

    /// For the log: which of them this build has.
    public var summary: String {
        "time-sensitive \(timeSensitive ? "yes" : "no"), data-protection keychain \(keychainAccessGroup == nil ? "no" : "yes"), "
            + "universal links \(universalLinks ? "yes" : "no")"
    }
}
