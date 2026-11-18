// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// One `leave` on its way to the wearer (PROTOCOL-v2 "Leave"): the signed gift wrap (never the follower key: it is
/// encrypted to the wearer and signed by a one-time key), the stream's `relays`, its `expiry` and the wearer's name for
/// the person. `accepted` = hosts of the relays that answered `OK true`; `nearby` = the wearer's phone took it over
/// Bluetooth. The same rules as Android's `PendingLeave` (contracts/protocol/sync/leave.json `outbox`).
public struct PendingLeave: Codable, Equatable, Sendable {
    public var wrap: SignedEvent
    public var relays: [String]
    public var expiry: Int
    public var wearer: String
    public var accepted: Set<String> = []
    public var nearby = false

    public init(wrap: SignedEvent, relays: [String], expiry: Int, wearer: String) {
        self.wrap = wrap
        self.relays = relays
        self.expiry = expiry
        self.wearer = wearer
    }

    private var hosts: Set<String> { Set(relays.map(LeaveOutbox.host)) }

    /// The wearer can learn it now: a relay keeps it, or the wearer's phone took it over Bluetooth.
    public var told: Bool { !accepted.isEmpty || nearby }

    /// Nothing more to send: `LeaveOutbox.needed` relays keep it (all of them when fewer are listed), or it expired.
    public func done(now: Int) -> Bool {
        now >= expiry || accepted.filter(hosts.contains).count >= min(LeaveOutbox.needed, hosts.count)
    }

    /// The relays still to send it to.
    public var waitingOn: [String] { relays.filter { !accepted.contains(LeaveOutbox.host($0)) } }

    /// `relay` (a host, as relay pools tag their answers) answered `OK true`; a relay that isn't listed doesn't count.
    public mutating func accept(_ relay: String) {
        let host = LeaveOutbox.host(relay)
        if hosts.contains(host) { accepted.insert(host) }
    }
}

/// The leaves this Mac still sends: `leave-outbox.json` in Application Support (nothing secret in it).
public struct LeaveOutbox: Codable, Equatable, Sendable {
    /// Relays that must keep a leave before the follower stops sending it (one may drop it).
    public static let needed = 2

    public var leaves: [PendingLeave] = []

    public init(leaves: [PendingLeave] = []) { self.leaves = leaves }

    /// A relay URL's host, as the relay pools tag their answers (`wss://nos.lol/` → `nos.lol`).
    public static func host(_ url: String) -> String {
        let rest = url.range(of: "://").map { String(url[$0.upperBound...]) } ?? url
        return String(rest.prefix { $0 != "/" })
    }

    /// Without the ones that are done.
    public func pruned(now: Int) -> LeaveOutbox { LeaveOutbox(leaves: leaves.filter { !$0.done(now: now) }) }

    /// The oldest leave nobody took yet: its wearer is who the person is told about.
    public var untold: PendingLeave? { leaves.first { !$0.told } }
}

public typealias LeaveOutboxStore = JSONFileStore<LeaveOutbox>

extension SukraraPaths {
    public var leaveOutboxFile: URL { appSupport.appending(path: "leave-outbox.json", directoryHint: .notDirectory) }
}

/// Carries the follower's `leave` until the wearer can't miss it (PROTOCOL-v2 "Leave"): every leave still waiting goes
/// to the relays that haven't accepted it, at once after *Stop following*, at launch and whenever the Mac wakes or the
/// network comes back, until two relays keep it or it expired. Its own short-lived relay connection (the follow and its
/// key are gone: a relay asking for AUTH gets a throwaway key's).
public actor LeaveSender {
    /// How long one attempt waits for the relays' answers (seconds).
    public static let wait: Double = 20

    private let store: LeaveOutboxStore
    private let crypto: any NostrCrypto
    private let clock: any TimeSource
    private let makeRelays: FollowerRuntime.RelayFactory
    private let sleep: @Sendable (Double) async throws -> Void
    private var running = false
    private var again = false
    private var onChange: (@Sendable () async -> Void)?

    public init(
        store: LeaveOutboxStore, crypto: any NostrCrypto, clock: any TimeSource,
        makeRelays: @escaping FollowerRuntime.RelayFactory,
        sleep: @escaping @Sendable (Double) async throws -> Void = { try await Task.sleep(for: .seconds($0)) }
    ) {
        self.store = store
        self.crypto = crypto
        self.clock = clock
        self.makeRelays = makeRelays
        self.sleep = sleep
    }

    private var now: Int { Int(clock.now.timeIntervalSince1970) }

    /// Called after every change of the outbox (the follower runtime publishes a new snapshot).
    public func onChange(_ action: @escaping @Sendable () async -> Void) { onChange = action }

    public func outbox() async -> LeaveOutbox { await store.load() ?? LeaveOutbox() }

    /// Saves `leave` before anything else happens to the follow (a crash after the key is gone can't lose it).
    public func add(_ leave: PendingLeave) async {
        await change { $0.leaves = $0.leaves.filter { $0.wrap.id != leave.wrap.id } + [leave] }
    }

    /// The follow didn't end after all (the key couldn't be removed): its leave never goes.
    public func drop(_ id: String) async { await change { $0.leaves.removeAll { $0.wrap.id == id } } }

    /// The wearer's phone took `id` over Bluetooth.
    public func tookNearby(_ id: String) async {
        await change { o in
            for i in o.leaves.indices where o.leaves[i].wrap.id == id { o.leaves[i].nearby = true }
        }
    }

    /// One attempt (another one waits for it): every leave still waiting goes to the relays that haven't accepted it,
    /// and their answers are taken for up to `wait` seconds. True when nothing is left to send.
    @discardableResult public func flush() async -> Bool {
        if running {
            again = true
            return false
        }
        running = true
        defer { running = false }
        var result = await attempt()
        while again {
            again = false
            result = await attempt()
        }
        return result
    }

    private func attempt() async -> Bool {
        await change { $0 = $0.pruned(now: now) }
        let waiting = await outbox().leaves
        guard !waiting.isEmpty else { return true }
        let ids = Set(waiting.map(\.wrap.id))
        var urls: [String] = []
        for url in waiting.flatMap(\.waitingOn) where !urls.contains(url) { urls.append(url) }
        let relays = makeRelays(urls, crypto.generateSecret())
        await relays.start()
        let answers = Task { [weak self] in
            for await message in relays.inbound {
                guard case .ok(let id, true, let relay) = message, ids.contains(id), let self else { continue }
                if await self.accepted(id, by: relay, of: ids) { return }
            }
        }
        for leave in waiting { await relays.publish(leave.wrap) }
        let timeout = Task { [sleep] in
            try? await sleep(Self.wait)
            answers.cancel()
        }
        await answers.value
        timeout.cancel()
        await relays.stop()
        await change { $0 = $0.pruned(now: now) }
        return await outbox().leaves.isEmpty
    }

    /// Records `relay`'s acceptance; true once every leave of this attempt is done.
    private func accepted(_ id: String, by relay: String, of ids: Set<String>) async -> Bool {
        await change { o in
            for i in o.leaves.indices where o.leaves[i].wrap.id == id { o.leaves[i].accept(relay) }
        }
        return await outbox().pruned(now: now).leaves.allSatisfy { !ids.contains($0.wrap.id) }
    }

    private func change(_ edit: (inout LeaveOutbox) -> Void) async {
        var o = await outbox()
        let before = o
        edit(&o)
        guard o != before else { return }
        try? await store.save(o.leaves.isEmpty ? nil : o)
        await onChange?()
    }
}
