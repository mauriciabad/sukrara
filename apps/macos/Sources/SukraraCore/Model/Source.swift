// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// Whose glucose this Mac shows (schema `source.person`, docs/44 "Person × Source"): the person's own, or someone
/// else's (a child, a partner, a friend). Android `Person`.
public enum Person: String, CaseIterable, Sendable, Codable {
    case yours, theirs
}

/// How the source picker groups a source (contracts/sources/sources.json `group`).
public enum SourceGroup: String, Sendable {
    case offline, network, sukrara
}

/// Where the glucose comes from (schema `source.kind`), with what it declares in contracts/sources/sources.json: the
/// persons it can bring, whether this Mac gets the data itself (`firstHand`) or another Sukrara relays it, whether it
/// works offline, the picker's group, the usual delay and the reading ids it stores. Rules and screens read these,
/// never the id (`SourcesTests`). The Mac's sources only; Android's are its own.
public enum SourceKind: String, CaseIterable, Sendable, Codable {
    /// Sukrara on a phone shares it (over the internet, or Bluetooth nearby): a Sukrara stream.
    case sukrara
    /// Juggluco's web server on the home network (plain http, private addresses).
    case lanJuggluco

    public var persons: Set<Person> { [.yours, .theirs] }
    public var firstHand: Bool { self == .lanJuggluco }
    public var offline: Bool { self == .lanJuggluco }
    public var group: SourceGroup { self == .lanJuggluco ? .network : .sukrara }
    public var delaySeconds: Int { 60 }
    public var backfill: Bool { true }
    public var readings: Set<String> { self == .lanJuggluco ? ["juggluco-lan"] : ["relay", "nearby"] }

    /// The sources the picker offers for `person`, in the picker's order.
    public static func forPerson(_ person: Person) -> [SourceKind] { allCases.filter { $0.persons.contains(person) } }
}

/// The alert engine's role a `SourceSpec` runs as (Android `AlertRole`: WEARER / FOLLOWER_MIRROR / FOLLOWER_OWN).
public enum SourceAlertRole: String, Sendable {
    case wearer = "WEARER"
    case followerMirror = "FOLLOWER_MIRROR"
    case followerOwn = "FOLLOWER_OWN"
}

/// THE choice that replaced "this Mac follows" (docs/44): whose glucose × where it comes from, keyed as
/// contracts/sources/sources.json `keying` says. `owner`: your glucose read first-hand (Juggluco on the network);
/// `stream`: another Sukrara (*Settings from*, the follow); `person`: the words and what a change deletes.
public struct SourceSpec: Equatable, Sendable {
    public let person: Person
    public let kind: SourceKind

    /// The pair, or nil when no source offers it.
    public init?(person: Person?, kind: SourceKind?) {
        guard let person, let kind, kind.persons.contains(person) else { return nil }
        self.person = person
        self.kind = kind
    }

    public var owner: Bool { person == .yours && kind.firstHand }
    public var stream: Bool { kind == .sukrara }
    public var theirs: Bool { person == .theirs }

    /// The alert role, unchanged from what the Mac did: a stream mirrors the wearer's alerts with *Settings from:
    /// Wearer*; every other case runs this Mac's own rules.
    public func alertRole(mirrorsWearer: Bool) -> SourceAlertRole {
        if owner { return .wearer }
        return stream && mirrorsWearer ? .followerMirror : .followerOwn
    }

    /// Someone else's glucose from their Sukrara: what following was.
    public static let theirSukrara = SourceSpec(person: .theirs, kind: .sukrara)!

    /// The stored answer (`sourcePerson`, `sourceKind`); nil = not answered (an unknown id too).
    public static func stored(in store: some SettingsStoring) -> SourceSpec? {
        SourceSpec(
            person: Person(rawValue: store.value(MacSettings.sourcePerson)),
            kind: SourceKind(rawValue: store.value(MacSettings.sourceKind)))
    }

    public func store(in store: some SettingsStoring) {
        store.set(person.rawValue, for: MacSettings.sourcePerson)
        store.set(kind.rawValue, for: MacSettings.sourceKind)
    }

    /// What the Mac runs on: the answer, else what it is already set up to read (a Mac updated from before the
    /// question: following someone → their Sukrara), so monitoring never stops for the answer.
    public static func running(in store: some SettingsStoring, following: Bool) -> SourceSpec? {
        stored(in: store) ?? (following ? theirSukrara : nil)
    }
}

/// THE rank rule (contracts/sources/dedup.json; Android `ReadingRank`): one reading per measurement whichever sources
/// brought it. Readings of two sources within `window` (either way) are the same measurement and the higher rank
/// wins; equal ranks keep what is stored first; a source re-sending its own time replaces itself; one source never
/// merges with itself.
public enum ReadingRank {
    public static let window: TimeInterval = 120

    /// Highest first (dedup.json `rank`).
    public enum Rank: Int, CaseIterable, Sendable, Comparable {
        case sensor, phoneApp, network, online, sukrara, imported
        public static func < (a: Rank, b: Rank) -> Bool { a.rawValue < b.rawValue }

        public var name: String { String(describing: self) }
    }

    private static let ranks: [String: Rank] = [
        "libre3": .sensor, "dexcom-g7": .sensor, "dexcom-g6": .sensor, "accu-chek-smartguide": .sensor,
        "aidex-x": .sensor, "juggluco": .phoneApp, "juggluco-lan": .network, "relay": .sukrara, "nearby": .sukrara,
        "import-libreview": .imported, "import-tidepool": .imported, "import-file": .imported,
    ]

    /// `source`'s rank; an id no source declares ranks as a sensor.
    public static func of(_ source: String) -> Rank { ranks[source] ?? .sensor }

    /// One reading with where it came from.
    public struct Sourced: Equatable, Sendable {
        public var t: Date
        public var mgdl: Int
        public var source: String
        public init(t: Date, mgdl: Int, source: String) {
            self.t = t
            self.mgdl = mgdl
            self.source = source
        }
    }

    /// What a store keeps of `stored` and `incoming` (a batch, in time order).
    public static func merge(stored: [Sourced], incoming: [Sourced]) -> [Sourced] {
        var pool = stored
        let sorted = incoming.sorted {
            ($0.t, of($0.source).rawValue, $0.source) < ($1.t, of($1.source).rawValue, $1.source)
        }
        for r in sorted {
            let near = pool.filter { $0.source != r.source && abs($0.t.timeIntervalSince(r.t)) <= window }
            if near.contains(where: { of($0.source) <= of(r.source) }) { continue }
            pool.removeAll { p in near.contains(p) || (p.source == r.source && p.t == r.t) }
            pool.append(r)
        }
        return pool.sorted { ($0.t, $0.source) < ($1.t, $1.source) }
    }
}
