// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// A NIP-01 REQ filter (only the fields the protocol uses).
public struct RelayFilter: Codable, Hashable, Sendable {
    public var kinds: [UInt16]?
    public var authors: [String]?
    public var p: [String]?
    public var d: [String]?
    public var c: [String]?
    public var since: Int?

    public init(
        kinds: [UInt16]? = nil, authors: [String]? = nil, p: [String]? = nil, d: [String]? = nil, c: [String]? = nil,
        since: Int? = nil
    ) {
        self.kinds = kinds
        self.authors = authors
        self.p = p
        self.d = d
        self.c = c
        self.since = since
    }

    enum CodingKeys: String, CodingKey {
        case kinds, authors, since
        case p = "#p"
        case d = "#d"
        case c = "#c"
    }

    /// Whether a relay would deliver `event` for this filter (the fake relay and BLE backlogs use it).
    public func matches(_ event: SignedEvent) -> Bool {
        if let kinds, !kinds.contains(event.kind) { return false }
        if let authors, !authors.contains(event.pubkey) { return false }
        if let since, event.createdAt < since { return false }
        for (name, values) in [("p", p), ("d", d), ("c", c)] {
            guard let values else { continue }
            guard event.tags.contains(where: { $0.count > 1 && $0[0] == name && values.contains($0[1]) }) else {
                return false
            }
        }
        return true
    }

    public func json() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return String(decoding: (try? encoder.encode(self)) ?? Data(), as: UTF8.self)
    }
}

/// What relays send back, tagged with the relay host (`via` = `relay:<host>`).
public enum RelayInbound: Equatable, Sendable {
    case event(SignedEvent, subscription: String, relay: String)
    /// End of stored events for a subscription on one relay (backfill done there).
    case endOfStored(subscription: String, relay: String)
    /// Answer to one of our publishes, from the connection opened with `url` (the leave outbox tells relays apart by
    /// it: PROTOCOL-v2 "Relay answers").
    case ok(RelayOk, relay: String, url: String)
    /// The relay closed a subscription (reason text from the relay; auth-required is handled inside the pool).
    case closed(subscription: String, reason: String, relay: String)
    case connection(RelayConnection, relay: String)
}

/// A relay's NIP-01 answer to an event we sent, `["OK", <event id>, <true|false>, <message>, …]` (PROTOCOL-v2 "Relay
/// answers", contracts/protocol/relays/relay-answers.json `answers`; Android `RelayOk`). `accepted` is the third
/// element when it is a JSON boolean (anything else: false); `message` the fourth when it is a string (old relays leave
/// it out).
public struct RelayOk: Equatable, Sendable {
    public var eventId: String
    public var accepted: Bool
    public var message: String

    public init(eventId: String, accepted: Bool, message: String = "") {
        self.eventId = eventId
        self.accepted = accepted
        self.message = message
    }

    /// The answer in the relay message `text`; nil when it isn't an `OK` with a string id.
    public init?(frame text: String) {
        guard let frame = try? JSONDecoder().decode(RelayFrame.self, from: Data(text.utf8)) else { return nil }
        self.init(frame)
    }

    init?(_ frame: RelayFrame) {
        guard frame.type == "OK", let id = frame.string(1) else { return nil }
        self.init(eventId: id, accepted: frame.bool(2) ?? false, message: frame.string(3) ?? "")
    }

    /// The relay keeps the event: `true` whatever the message (empty, `duplicate:`, `pow:` …), or `false` with
    /// `duplicate:`: it already holds that exact id (NIP-20 says such a relay MUST answer `true`; some answer
    /// `false`). Never any other `false`.
    public var kept: Bool { accepted || message.hasPrefix("duplicate:") }

    /// NIP-42: the relay takes the event only from an authenticated client: AUTH, then send it again.
    public var authRequired: Bool { !accepted && message.hasPrefix("auth-required:") }
}

public enum RelayConnection: Equatable, Sendable {
    case connected
    case disconnected
}

/// The relay side of the protocol. `RelayPool` talks to real relays; tests use `FakeRelay` (SukraraTesting). Tests
/// never open a socket to a real relay.
public protocol RelayTransport: Sendable {
    /// Opens or replaces subscription `id`; re-sent after every reconnect. The same filter again is a no-op.
    func subscribe(_ id: String, _ filter: RelayFilter) async
    func close(_ id: String) async
    /// Sends to every relay now and again after reconnects for an hour.
    func publish(_ event: SignedEvent) async
    /// Everything relays send. Single consumer: iterate it from one task only.
    var inbound: AsyncStream<RelayInbound> { get }
}
