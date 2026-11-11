// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import os

/// Reconnect delays: 2 s doubling to 5 min, each scaled by a random 50–100 % so many clients don't reconnect in the
/// same instant. Reset once a relay sends anything.
public struct Backoff: Sendable {
    public var base: Double = 2
    public var cap: Double = 300

    public init(base: Double = 2, cap: Double = 300) {
        self.base = base
        self.cap = cap
    }

    /// Seconds to wait before reconnect attempt `attempt` (0 = the first retry); `jitter` in 0.5…1.
    public func delay(attempt: Int, jitter: Double) -> Double {
        min(cap, base * pow(2, Double(min(attempt, 20)))) * jitter
    }
}

/// The relay connection: one WebSocket per relay, reconnect with backoff, subscriptions and an outbox re-sent after
/// every reconnect (gift wraps must reach relays that were down), NIP-42 AUTH as `authKey` when a relay closes a REQ
/// with `auth-required` (once per connection; REQs re-sent after the relay accepts it), a watchdog that pings a relay
/// silent for 150 s and reconnects after 270 s. Events are not deduped here: the follower session does that (an
/// event under a not-yet-known epoch must be seen again later). An event for a subscription that isn't open or doesn't
/// match its filter is dropped before anyone verifies it, and a relay sending more than `eventBurst` events at once or
/// `eventsPerSecond` after that loses its socket (PROTOCOL-v2 "Relay rules"; Android `RelayPool`, same numbers). The
/// owner must call `stop()` (terminal) to release it.
public actor RelayPool: RelayTransport {
    public nonisolated let inbound: AsyncStream<RelayInbound>
    private let continuation: AsyncStream<RelayInbound>.Continuation
    private let urls: [URL]
    private let authKey: SecretKeyHex?
    private let crypto: any NostrCrypto
    private let factory: any WebSocketFactory
    private let backoff: Backoff
    private let sleep: @Sendable (Double) async throws -> Void
    private let now: @Sendable () -> Int
    private static let log = Logger(subsystem: "app.mauri.sukrara", category: "relays")
    private static let outboxSeconds = 3600
    /// Events a relay may send at once (a full start: chunks, 30 days of treatments, wraps) …
    public static let eventBurst = 2000
    /// … and per second after that.
    public static let eventsPerSecond = 20

    private struct Connection {
        var socket: (any WebSocket)?
        var task: Task<Void, Never>?
        var challenge: String?
        var authEventId: String?
        /// The relay asked for AUTH before sending its challenge: answer as soon as the challenge arrives.
        var authPending = false
        /// The relay has sent something on the current socket.
        var open = false
        var lastReceived = 0
        /// Token bucket of EVENT messages: `eventBurst` at most, refilled by `eventsPerSecond`.
        var budget = Double(RelayPool.eventBurst)
        var budgetAt = 0
    }

    /// The relay sent more events than its budget.
    private struct TooManyEvents: Error {}

    private var connections: [URL: Connection] = [:]
    private var subscriptions: [String: RelayFilter] = [:]
    private var outbox: [(event: SignedEvent, at: Int)] = []
    private var watchdog: Task<Void, Never>?
    private var stopped = false

    /// `urls`: only the ones that pass the relay-list rules (`Relays.valid`) are used. `authKey`: the key relays' gift wraps are addressed to (follower `F`, or
    /// the invite key for an invite lookup).
    public init(
        relays: [String], authKey: SecretKeyHex?, crypto: any NostrCrypto,
        factory: any WebSocketFactory = URLSessionWebSocketFactory(), backoff: Backoff = Backoff(),
        sleep: @escaping @Sendable (Double) async throws -> Void = { try await Task.sleep(for: .seconds($0)) },
        now: @escaping @Sendable () -> Int = { Int(Date().timeIntervalSince1970) }
    ) {
        urls = Relays.valid(relays).compactMap(URL.init(string:))
        self.authKey = authKey
        self.crypto = crypto
        self.factory = factory
        self.backoff = backoff
        self.sleep = sleep
        self.now = now
        // Never drop: an alert is ephemeral and can't be fetched again.
        (inbound, continuation) = AsyncStream.makeStream(bufferingPolicy: .unbounded)
    }

    /// Connects every relay (again: a restart drops the current sockets). Does nothing after `stop()`.
    public func start() {
        guard !stopped else { return }
        for url in urls {
            connections[url]?.task?.cancel()
            connections[url]?.socket?.cancel()
            connections[url, default: Connection()].task = Task { await self.run(url) }
        }
        if watchdog == nil { watchdog = Task { await self.watch() } }
    }

    /// Closes every socket and ends `inbound`, for good.
    public func stop() {
        stopped = true
        watchdog?.cancel()
        watchdog = nil
        for (url, connection) in connections {
            connection.task?.cancel()
            connection.socket?.cancel()
            connections[url] = nil
        }
        continuation.finish()
    }

    public func subscribe(_ id: String, _ filter: RelayFilter) async {
        guard subscriptions[id] != filter else { return }
        subscriptions[id] = filter
        await broadcast(Self.req(id, filter))
    }

    public func close(_ id: String) async {
        guard subscriptions.removeValue(forKey: id) != nil else { return }
        await broadcast(JSONText.encode(["CLOSE", id]))
    }

    public func publish(_ event: SignedEvent) async {
        let t = now()
        outbox.removeAll { t - $0.at > Self.outboxSeconds || $0.event.id == event.id }
        outbox.append((event, t))
        await broadcast("[\"EVENT\",\(event.json())]")
    }

    /// Relays that have sent something on their current socket.
    public var connectedCount: Int { connections.values.filter(\.open).count }

    // MARK: - per relay

    private func run(_ url: URL) async {
        var attempt = 0
        while !Task.isCancelled {
            let socket = factory.connect(to: url)
            connections[url]?.socket = socket
            connections[url]?.challenge = nil
            connections[url]?.authEventId = nil
            connections[url]?.authPending = false
            connections[url]?.open = false
            connections[url]?.lastReceived = now()
            connections[url]?.budget = Double(Self.eventBurst)
            connections[url]?.budgetAt = now()
            let attemptBefore = attempt
            for (id, filter) in subscriptions { try? await socket.send(Self.req(id, filter)) }
            for item in outbox { try? await socket.send("[\"EVENT\",\(item.event.json())]") }
            var announced = false
            do {
                while true {
                    let text = try await socket.receive()
                    if !announced {
                        announced = true
                        connections[url]?.open = true
                        continuation.yield(.connection(.connected, relay: Self.host(url)))
                    }
                    attempt = 0
                    connections[url]?.lastReceived = now()
                    guard await handle(text, from: url, socket: socket) else { throw TooManyEvents() }
                }
            } catch {
                // a relay that answers and then floods doesn't win back the shortest backoff each time
                if error is TooManyEvents { attempt = attemptBefore }
                socket.cancel()
                guard !Task.isCancelled else { return }
                connections[url]?.socket = nil
                connections[url]?.open = false
                continuation.yield(.connection(.disconnected, relay: Self.host(url)))
                Self.log.info("relay \(Self.host(url), privacy: .public) disconnected, attempt \(attempt)")
            }
            try? await sleep(backoff.delay(attempt: attempt, jitter: Double.random(in: 0.5...1)))
            attempt += 1
        }
    }

    /// False when the relay sent more events than its budget: its socket is dropped.
    private func handle(_ text: String, from url: URL, socket: any WebSocket) async -> Bool {
        guard let frame = try? JSONDecoder().decode(RelayFrame.self, from: Data(text.utf8)) else { return true }
        let relay = Self.host(url)
        func string(_ i: Int) -> String? { frame.string(i) }
        switch frame.type {
        case "EVENT":
            guard spend(url) else { return false }
            guard let sub = string(1), let filter = subscriptions[sub], let event = frame.event,
                filter.matches(event)  // not what we asked for: never verified, never seen by the session
            else { return true }
            continuation.yield(.event(event, subscription: sub, relay: relay))
        case "EOSE":
            if let sub = string(1) { continuation.yield(.endOfStored(subscription: sub, relay: relay)) }
        case "AUTH":
            connections[url]?.challenge = string(1)
            if connections[url]?.authPending == true { await authenticate(url, socket: socket) }
        case "CLOSED":
            let sub = string(1) ?? "", reason = string(2) ?? ""
            if reason.hasPrefix("auth-required"), authKey != nil {
                connections[url]?.authPending = true
                await authenticate(url, socket: socket)
            } else {
                continuation.yield(.closed(subscription: sub, reason: reason, relay: relay))
            }
        case "OK":
            guard let id = string(1) else { return true }
            let accepted = frame.bool(2) ?? false
            if id == connections[url]?.authEventId {
                Self.log.info("relay \(relay, privacy: .public) AUTH \(accepted ? "accepted" : "refused")")
                guard accepted else { return true }
                for (sub, filter) in subscriptions { try? await socket.send(Self.req(sub, filter)) }
                for item in outbox { try? await socket.send("[\"EVENT\",\(item.event.json())]") }
            } else {
                continuation.yield(.ok(eventId: id, accepted: accepted, relay: relay))
            }
        default:
            break
        }
        return true
    }

    private func spend(_ url: URL) -> Bool {
        guard var c = connections[url] else { return false }
        let t = now()
        c.budget = min(Double(Self.eventBurst), c.budget + Double(Swift.max(0, t - c.budgetAt) * Self.eventsPerSecond))
        c.budgetAt = t
        guard c.budget >= 1 else {
            connections[url] = c
            return false
        }
        c.budget -= 1
        connections[url] = c
        return true
    }

    /// NIP-42: kind 22242 with the relay and the challenge, signed by the key the wraps are addressed to.
    private func authenticate(_ url: URL, socket: any WebSocket) async {
        guard let authKey, connections[url]?.authEventId == nil, let challenge = connections[url]?.challenge,
            let event = try? crypto.sign(
                kind: Wire.kindAuth, content: "", tags: [["relay", url.absoluteString], ["challenge", challenge]],
                createdAt: now(), with: authKey)
        else { return }
        connections[url]?.authEventId = event.id
        connections[url]?.authPending = false
        try? await socket.send("[\"AUTH\",\(event.json())]")
    }

    /// Every minute: first drop every socket silent for over 270 s (the run loop reconnects it), then ping the ones
    /// silent for over 150 s all at once, each given `pingTimeout`; a ping with no answer in time drops its socket too.
    /// A half-open connection after sleep or a Wi-Fi roam can leave a ping unanswered for minutes, and it must not hold
    /// up the other relays or the next round.
    private func watch() async {
        while !Task.isCancelled {
            try? await sleep(60)
            let t = now()
            var quiet: [(url: URL, socket: any WebSocket)] = []
            for (url, connection) in connections {
                guard let socket = connection.socket else { continue }
                let silent = t - connection.lastReceived
                if silent > 270 {
                    socket.cancel()
                } else if silent > 150 {
                    quiet.append((url, socket))
                }
            }
            guard !quiet.isEmpty else { continue }
            let sleep = sleep
            let answers = await withTaskGroup(of: (URL, Bool).self) { group in
                for (url, socket) in quiet {
                    group.addTask { (url, await Self.answers(socket, within: Self.pingTimeout, sleep: sleep)) }
                }
                var out: [URL: Bool] = [:]
                for await (url, answered) in group { out[url] = answered }
                return out
            }
            for (url, socket) in quiet {
                if answers[url] == true {
                    connections[url]?.lastReceived = now()
                } else {
                    socket.cancel()
                }
            }
        }
    }

    public static let pingTimeout = 10.0

    /// True when `socket` answers a ping within `seconds`. Returns at the deadline even if the ping never does, and
    /// then cancels the ping (it never waits past its deadline).
    private static func answers(
        _ socket: any WebSocket, within seconds: Double, sleep: @escaping @Sendable (Double) async throws -> Void
    ) async -> Bool {
        let first = ResumeOnce<Bool, Never>()
        let ping = Task { first.resume(with: .success((try? await socket.ping()) != nil)) }
        let deadline = Task {
            try? await sleep(seconds)
            first.resume(with: .success(false))
        }
        defer {
            ping.cancel()
            deadline.cancel()
        }
        return await withTaskCancellationHandler {
            await withCheckedContinuation { first.start($0) }
        } onCancel: {
            first.resume(with: .success(false))  // stop(): don't wait for the deadline
        }
    }

    private func broadcast(_ text: String) async {
        for connection in connections.values {
            try? await connection.socket?.send(text)
        }
    }

    private static func req(_ id: String, _ filter: RelayFilter) -> String {
        "[\"REQ\",\(JSONText.encode(id)),\(filter.json())]"
    }

    private static func host(_ url: URL) -> String { url.host() ?? url.absoluteString }
}

/// One relay → client frame (NIP-01 `["EVENT", sub, event]`, `["OK", id, accepted, message]`, `["EOSE", sub]`, …),
/// decoded once: its type, then each element as a string, a flag or an event (anything else is skipped).
struct RelayFrame: Decodable {
    enum Item {
        case string(String), bool(Bool), event(SignedEvent), other
    }

    var type: String
    /// The elements after the type (index 1 = `items[0]`).
    var items: [Item]

    init(from decoder: any Decoder) throws {
        var c = try decoder.unkeyedContainer()
        type = try c.decode(String.self)
        items = []
        while !c.isAtEnd {
            if let s = try? c.decode(String.self) {
                items.append(.string(s))
            } else if let b = try? c.decode(Bool.self) {
                items.append(.bool(b))
            } else if let e = try? c.decode(SignedEvent.self) {
                items.append(.event(e))
            } else {
                _ = try c.decode(Skip.self)
                items.append(.other)
            }
        }
    }

    func string(_ i: Int) -> String? {
        guard i >= 1, i <= items.count, case .string(let s) = items[i - 1] else { return nil }
        return s
    }

    func bool(_ i: Int) -> Bool? {
        guard i >= 1, i <= items.count, case .bool(let b) = items[i - 1] else { return nil }
        return b
    }

    /// EVENT's event (the third element).
    var event: SignedEvent? {
        guard items.count >= 2, case .event(let e) = items[1] else { return nil }
        return e
    }

    /// Any JSON value, read and dropped.
    private struct Skip: Decodable {
        init(from decoder: any Decoder) throws {}
    }
}
