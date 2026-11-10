// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Security

/// Why a Keychain call failed. Shown as a broken link ("Can't read the Keychain"), never as "not following".
public enum KeychainError: Error, Equatable, Sendable {
    /// The data-protection keychain needs a `keychain-access-groups` entitlement authorised by a provisioning profile
    /// (-34018): a build without one (ad hoc, dev, Apple Development with no profile) gets this.
    case missingEntitlement
    /// Locked keychain, denied access, … (the `OSStatus`).
    case status(Int32)
}

/// Generic-password items under one service (default `SukraraIDs.keychainService`; tests pass a temporary one), in
/// one of macOS's two keychains (TN3137). Items are readable only after first unlock and never sync.
public struct KeychainStore: SecretStoring, Equatable {
    /// The account holding the follower state (`FollowerState` JSON).
    public static let followerAccount = "follower"

    /// Which keychain the items live in.
    public enum Keychain: Equatable, Sendable {
        /// The login (file-based) keychain: works with any signature; access is an ACL on the code signature, so a
        /// signing-identity change asks the person once.
        case login
        /// The data-protection keychain, in this access group: only a build whose provisioning profile grants the
        /// group can use it (else `KeychainError.missingEntitlement`); no ACL prompts.
        case dataProtection(accessGroup: String)
    }

    public let service: String
    public let keychain: Keychain

    public init(service: String = SukraraIDs.keychainService, keychain: Keychain = .login) {
        self.service = service
        self.keychain = keychain
    }

    /// The app's secrets under `service`: the data-protection keychain when this build is entitled to Sukrara's group
    /// (moving what the login keychain still holds from a build without it), else the login keychain.
    public static func secrets(service: String, entitlements: Entitlements) -> any SecretStoring {
        let login = KeychainStore(service: service)
        guard let group = entitlements.keychainAccessGroup else { return login }
        return MovingSecretStore(
            to: KeychainStore(service: service, keychain: .dataProtection(accessGroup: group)), from: login)
    }

    public func read(account: String) throws -> Data? {
        var query = base(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        try check(status)
        guard let data = result as? Data else { throw KeychainError.status(errSecDecode) }
        return data
    }

    public func write(_ data: Data, account: String) throws {
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(base(account) as CFDictionary, update as CFDictionary)
        if status != errSecItemNotFound { return try check(status) }
        var add = base(account)
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        try check(SecItemAdd(add as CFDictionary, nil))
    }

    public func delete(account: String) throws {
        let status = SecItemDelete(base(account) as CFDictionary)
        if status == errSecItemNotFound { return }
        try check(status)
    }

    private func base(_ account: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if case .dataProtection(let group) = keychain {
            query[kSecUseDataProtectionKeychain as String] = true
            query[kSecAttrAccessGroup as String] = group
        }
        return query
    }

    private func check(_ status: OSStatus) throws {
        switch status {
        case errSecSuccess: return
        case errSecMissingEntitlement: throw KeychainError.missingEntitlement
        default: throw KeychainError.status(status)
        }
    }
}

/// A build with the data-protection keychain (`to`) whose items may still be in the login keychain (`from`), where a
/// build without the entitlement kept them. An item is moved on its first read: copied, read back and compared, and
/// only then removed from the login keychain. A copy that fails or reads back different is removed again and the
/// item stays where it was, so the follower key is never lost (the next read tries again). `delete` removes both
/// copies, the login keychain's first, so a stopped follow never comes back from the old one.
public struct MovingSecretStore: SecretStoring {
    public let to: any SecretStoring
    public let from: any SecretStoring

    public init(to: any SecretStoring, from: any SecretStoring) {
        self.to = to
        self.from = from
    }

    /// The item from `to`; else `from`'s, moved. A `from` that can't be read throws (never "no item": that would read
    /// as "not following" and wipe the wearer's stored data).
    public func read(account: String) throws -> Data? {
        if let data = try to.read(account: account) { return data }
        guard let data = try from.read(account: account) else { return nil }
        do {
            try to.write(data, account: account)
            guard try to.read(account: account) == data else { throw KeychainError.status(errSecDecode) }
        } catch {
            try? to.delete(account: account)
            return data
        }
        // moved: the old copy goes (if it can't now, `delete` removes it with the new one)
        try? from.delete(account: account)
        return data
    }

    public func write(_ data: Data, account: String) throws {
        try to.write(data, account: account)
    }

    public func delete(account: String) throws {
        try from.delete(account: account)
        try to.delete(account: account)
    }
}
