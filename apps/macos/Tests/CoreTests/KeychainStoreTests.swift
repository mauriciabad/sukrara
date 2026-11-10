// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Security
import SukraraCore
import SukraraTesting
import Synchronization
import Testing

/// The Keychain under a throwaway service (never `app.mauri.sukrara`); every test removes its item.
struct KeychainStoreTests {
    /// Whether this machine has a usable login keychain (CI runners and sandboxes may not).
    static let loginKeychainUsable: Bool = {
        let probe = KeychainStore(service: "app.mauri.sukrara.test.probe-\(UUID().uuidString)")
        defer { try? probe.delete(account: "probe") }
        return (try? probe.write(Data([1]), account: "probe")) != nil
    }()

    let store = KeychainStore(service: "app.mauri.sukrara.test-\(UUID().uuidString)")

    @Test(.enabled(if: loginKeychainUsable, "no usable login keychain on this machine: Keychain round-trip NOT tested"))
    func roundTripUpdateAndDelete() throws {
        defer { try? store.delete(account: "follower") }
        #expect(try store.read(account: "follower") == nil)  // missing item = not following
        try store.write(Data("one".utf8), account: "follower")
        try store.write(Data("two".utf8), account: "follower")  // update in place
        #expect(try store.read(account: "follower") == Data("two".utf8))
        try store.delete(account: "follower")
        #expect(try store.read(account: "follower") == nil)
        try store.delete(account: "follower")  // deleting a missing item is fine
    }

    @Test func defaultServiceIsTheAppOne() {
        #expect(KeychainStore().service == "app.mauri.sukrara")
        #expect(KeychainStore().keychain == .login)
    }

    /// Without a provisioning profile (swift test, Apple Development signature) the data-protection keychain refuses:
    /// that must surface as an error, never as "no item" (which would read as "not following").
    @Test func dataProtectionKeychainWithoutEntitlementFailsVisibly() {
        let dp = KeychainStore(
            service: "app.mauri.sukrara.test-\(UUID().uuidString)",
            keychain: .dataProtection(accessGroup: SukraraIDs.keychainAccessGroup))
        defer { try? dp.delete(account: "x") }
        do {
            try dp.write(Data([1]), account: "x")
            // Entitled build: the item round-trips.
            #expect((try? dp.read(account: "x")) == Data([1]))
        } catch let error as KeychainError {
            #expect(error == .missingEntitlement)
        } catch {
            Issue.record("unexpected error \(error)")
        }
    }

    /// The test runner has no provisioning profile: its own entitlements choose the login keychain.
    @Test func thisProcessIsNotEntitledSoItUsesTheLoginKeychain() {
        #expect(Entitlements.current().keychainAccessGroup == nil)
        let secrets = KeychainStore.secrets(service: "app.mauri.sukrara.test", entitlements: .current())
        #expect((secrets as? KeychainStore)?.keychain == .login)
    }
}

/// Which keychain a build uses, from its entitlements (`KeychainStore.secrets`): only Sukrara's own group with a
/// profile means the data-protection keychain.
struct KeychainChoiceTests {
    @Test func withoutTheGroupItIsTheLoginKeychain() {
        for entitlements in [
            Entitlements.none, Entitlements(timeSensitive: true, associatedDomains: [SukraraIDs.associatedDomain]),
            Entitlements(keychainAccessGroups: ["ABCDE12345.app.mauri.sukrara", "\(SukraraIDs.teamID).other"]),
        ] {
            #expect(entitlements.keychainAccessGroup == nil)
            let secrets = KeychainStore.secrets(service: "s", entitlements: entitlements)
            #expect((secrets as? KeychainStore) == KeychainStore(service: "s"))
        }
    }

    @Test func withTheGroupItMovesIntoTheDataProtectionKeychain() throws {
        let entitlements = Entitlements(keychainAccessGroups: ["other", SukraraIDs.keychainAccessGroup])
        #expect(entitlements.keychainAccessGroup == "QWUV9H4A3G.app.mauri.sukrara")
        let moving = try #require(KeychainStore.secrets(service: "s", entitlements: entitlements) as? MovingSecretStore)
        #expect(
            (moving.to as? KeychainStore)
                == KeychainStore(service: "s", keychain: .dataProtection(accessGroup: SukraraIDs.keychainAccessGroup)))
        #expect((moving.from as? KeychainStore) == KeychainStore(service: "s"))
    }

    @Test func universalLinksNeedTheInviteDomain() {
        #expect(!Entitlements.none.universalLinks)
        #expect(!Entitlements(associatedDomains: ["applinks:example.com"]).universalLinks)
        #expect(Entitlements(associatedDomains: ["applinks:sukrara.mauri.app"]).universalLinks)
    }
}

/// The move from the login keychain into the data-protection keychain (`MovingSecretStore`) over fakes: the follower
/// key is never lost, and a stopped follow never comes back.
struct MovingSecretStoreTests {
    let key = Data("follower state".utf8)

    /// A Keychain that can refuse, or keep something other than what was written.
    final class FlakyStore: SecretStoring {
        enum Failure: Sendable { case none, read, write, delete, garbles, missingEntitlement }
        private let items = Mutex<[String: Data]>([:])
        private let failing = Mutex(Failure.none)
        var failure: Failure {
            get { failing.withLock { $0 } }
            set { failing.withLock { $0 = newValue } }
        }
        func item(_ account: String) -> Data? { items.withLock { $0[account] } }
        func put(_ data: Data, _ account: String) { items.withLock { $0[account] = data } }

        func read(account: String) throws -> Data? {
            if failure == .read { throw KeychainError.status(errSecInteractionNotAllowed) }
            if failure == .missingEntitlement { throw KeychainError.missingEntitlement }
            return item(account)
        }
        func write(_ data: Data, account: String) throws {
            if failure == .write { throw KeychainError.status(errSecInteractionNotAllowed) }
            if failure == .missingEntitlement { throw KeychainError.missingEntitlement }
            put(failure == .garbles ? Data("garbled".utf8) : data, account)
        }
        func delete(account: String) throws {
            if failure == .delete { throw KeychainError.status(errSecAuthFailed) }
            if failure == .missingEntitlement { throw KeychainError.missingEntitlement }
            _ = items.withLock { $0.removeValue(forKey: account) }
        }
    }

    @Test func anItemInTheLoginKeychainMovesOnItsFirstRead() throws {
        let to = FlakyStore()
        let from = FlakyStore()
        from.put(key, "follower")
        let store = MovingSecretStore(to: to, from: from)
        #expect(try store.read(account: "follower") == key)
        #expect(to.item("follower") == key)
        #expect(from.item("follower") == nil)  // moved, not copied
        #expect(try store.read(account: "follower") == key)  // from the data-protection keychain from now on
    }

    @Test func nothingAnywhereIsNoItem() throws {
        #expect(try MovingSecretStore(to: FlakyStore(), from: FlakyStore()).read(account: "follower") == nil)
    }

    @Test func aCopyThatFailsKeepsTheItemWhereItWas() throws {
        for failure in [FlakyStore.Failure.write, .garbles, .missingEntitlement] {
            let to = FlakyStore()
            let from = FlakyStore()
            from.put(key, "follower")
            to.failure = failure
            let store = MovingSecretStore(to: to, from: from)
            if failure == .missingEntitlement {
                // the data-protection keychain refuses even the first read: an error, never "no item"
                #expect(throws: KeychainError.missingEntitlement) { try store.read(account: "follower") }
            } else {
                #expect(try store.read(account: "follower") == key, "\(failure)")
            }
            #expect(from.item("follower") == key, "\(failure): the login keychain keeps it")
            to.failure = .none
            #expect(to.item("follower") == nil, "\(failure): no half-made copy is left to be read first")
            #expect(try store.read(account: "follower") == key)  // the next read moves it
            #expect(to.item("follower") == key && from.item("follower") == nil)
        }
    }

    @Test func anUnreadableLoginKeychainIsAnErrorNeverNoItem() {
        let from = FlakyStore()
        from.put(key, "follower")
        from.failure = .read
        let store = MovingSecretStore(to: FlakyStore(), from: from)
        #expect(throws: KeychainError.self) { try store.read(account: "follower") }
    }

    @Test func anOldCopyThatCantBeRemovedYetIsNeverReadBack() throws {
        let to = FlakyStore()
        let from = FlakyStore()
        from.put(key, "follower")
        from.failure = .delete
        let store = MovingSecretStore(to: to, from: from)
        #expect(try store.read(account: "follower") == key)
        #expect(from.item("follower") == key)  // still there
        try store.write(Data("newer".utf8), account: "follower")
        #expect(try store.read(account: "follower") == Data("newer".utf8))  // the data-protection copy wins
        // Stop following: the old copy can't be removed either → an error (still following), not a half-stop
        #expect(throws: KeychainError.self) { try store.delete(account: "follower") }
        #expect(to.item("follower") == Data("newer".utf8))
        from.failure = .none
        try store.delete(account: "follower")
        #expect(try store.read(account: "follower") == nil)  // gone from both: the follow doesn't come back
        #expect(from.item("follower") == nil && to.item("follower") == nil)
    }

    @Test func writesGoToTheDataProtectionKeychainOnly() throws {
        let to = FlakyStore()
        let from = FlakyStore()
        try MovingSecretStore(to: to, from: from).write(key, account: "juggluco")
        #expect(to.item("juggluco") == key && from.item("juggluco") == nil)
    }
}
