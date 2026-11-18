// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// Whose glucose a source brings in contracts/sources/sources.json (`persons`) and the sensor-life contract: Android's
/// *Whose glucose* question. The Mac doesn't ask it (the owner, 2026-11-18: it never uploads, shares or reminds, so the
/// answer only changed words): its sources are keyed by kind alone (`SourceSpec`), names come from the source.
public enum Person: String, CaseIterable, Sendable, Codable {
    case yours, theirs
}

/// How the source picker groups a source (contracts/sources/sources.json `group`).
public enum SourceGroup: String, Sendable, CaseIterable {
    case offline, network, online, sukrara
}

/// Where the glucose comes from (schema `source.kind`), with what it declares in contracts/sources/sources.json:
/// whether this Mac gets the data itself (`firstHand`) or another Sukrara relays it, whether it
/// works offline, the picker's group, the usual delay and the reading ids it stores. Rules and screens read these,
/// never the id (`SourcesTests`). The Mac's sources only; Android's are its own.
public enum SourceKind: String, CaseIterable, Sendable, Codable {
    /// Sukrara on a phone shares it (over the internet, or Bluetooth nearby): a Sukrara stream.
    case sukrara
    /// Juggluco's web server on the home network (plain http, private addresses).
    case lanJuggluco
    /// A Nightscout site (https): glucose and treatments both ways, about 2 minutes late.
    case nightscout
    /// LibreLinkUp, Abbott's follower service the Libre app uploads to (https): glucose about 10 seconds late and the
    /// sensor's life; no treatments (this Mac keeps its own).
    case libreLinkUp

    public var firstHand: Bool { self != .sukrara }
    public var offline: Bool { self == .lanJuggluco }
    public var group: SourceGroup {
        switch self {
        case .sukrara: .sukrara
        case .lanJuggluco: .network
        case .nightscout, .libreLinkUp: .online
        }
    }
    public var delaySeconds: Int {
        switch self {
        case .nightscout: 120
        case .libreLinkUp: 90
        case .sukrara, .lanJuggluco: 60
        }
    }
    public var backfill: Bool { true }
    public var readings: Set<String> {
        switch self {
        case .sukrara: ["relay", "nearby"]
        case .lanJuggluco: ["juggluco-lan"]
        case .nightscout: ["nightscout"]
        case .libreLinkUp: [LibreLinkUp.liveId, LibreLinkUp.graphId]
        }
    }

    /// What it says of the sensor's life (sources.json `life`): its start and length, the start only, a start only
    /// when one is logged (a Nightscout site's Sensor Start), or nothing.
    public var life: SensorLives.Life {
        switch self {
        case .sukrara: .full
        case .lanJuggluco, .libreLinkUp: .start
        case .nightscout: .maybeStart
        }
    }

    /// The setting holding the wear length of a source that doesn't say it (sources.json `wear`; nil = it says it).
    public var wear: MacSettings.Number? {
        switch self {
        case .sukrara: nil
        case .lanJuggluco: MacSettings.jugglucoWear
        case .nightscout: MacSettings.nightscoutWear
        case .libreLinkUp: MacSettings.libreLinkUpWear
        }
    }
}

/// The alert engine's role a `SourceSpec` runs as (Android `AlertRole`: WEARER / FOLLOWER_MIRROR / FOLLOWER_OWN).
public enum SourceAlertRole: String, Sendable {
    case wearer = "WEARER"
    case followerMirror = "FOLLOWER_MIRROR"
    case followerOwn = "FOLLOWER_OWN"
}

/// THE choice that replaced "this Mac follows" (docs/44): where the glucose comes from, keyed as
/// contracts/sources/sources.json `macKeying` says. The Mac never owns anyone's glucose (it never uploads, shares or
/// reminds), so a source is keyed by its kind alone: `stream` = another Sukrara (*Settings from*, the follow, the
/// mirrored alerts and escalation); every other source runs this Mac's own settings and rules.
public struct SourceSpec: Equatable, Sendable {
    public let kind: SourceKind

    public init(kind: SourceKind) { self.kind = kind }

    /// A stored answer; nil = not answered.
    public init?(kind: SourceKind?) {
        guard let kind else { return nil }
        self.kind = kind
    }

    public var stream: Bool { kind == .sukrara }

    /// The alert role: a stream mirrors the wearer's alerts with *Settings from: Their settings*; every other case
    /// runs this Mac's own rules.
    public func alertRole(mirrorsWearer: Bool) -> SourceAlertRole {
        stream && mirrorsWearer ? .followerMirror : .followerOwn
    }

    /// Another Sukrara: what following is.
    public static let sukrara = SourceSpec(kind: .sukrara)

    /// The stored answer (`sourceKind`); nil = not answered (an unknown id too).
    public static func stored(in store: some SettingsStoring) -> SourceSpec? {
        SourceSpec(kind: SourceKind(rawValue: store.value(MacSettings.sourceKind)))
    }

    /// Stores the answer; another source clears the manual sensor start (it was set for that source's sensor).
    public func store(in store: some SettingsStoring) {
        if Self.stored(in: store)?.kind != kind { store.set(0.0, for: MacSettings.manualStart) }
        store.set(kind.rawValue, for: MacSettings.sourceKind)
    }

    /// What the Mac runs on: the answer, else what it is already set up to read (a Mac updated from before the
    /// question: following someone → another Sukrara), so monitoring never stops for the answer.
    public static func running(in store: some SettingsStoring, following: Bool) -> SourceSpec? {
        stored(in: store) ?? (following ? sukrara : nil)
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
        case sensor, phoneApp, network, online, onlineHistory, sukrara, imported
        public static func < (a: Rank, b: Rank) -> Bool { a.rawValue < b.rawValue }

        public var name: String { String(describing: self) }
    }

    private static let ranks: [String: Rank] = [
        "libre3": .sensor, "dexcom-g7": .sensor, "dexcom-g6": .sensor, "accu-chek-smartguide": .sensor,
        "aidex-x": .sensor, "juggluco": .phoneApp, "juggluco-lan": .network, "nightscout": .online,
        "librelinkup": .online, "librelinkup-graph": .onlineHistory, "relay": .sukrara, "nearby": .sukrara,
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
