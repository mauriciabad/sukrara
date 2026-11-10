// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// A stretch of time on one side of a group (SPEC §1 "Episodes"): it starts when a kind of the side matches and ends
/// once nothing of the side has matched for the hold time (the kind's repeat, at least 5 min).
public struct Episode: Codable, Equatable, Sendable {
    public let side: AlertSide
    public internal(set) var start: Date
    /// The kind matching now (or last).
    public var kind: AlertKind
    /// The condition held at the last evaluation, and when it last did.
    public var isOn = true
    public var lastIn: Date
    /// The firing whose notification is up (gone when the condition goes, it's quieted, or the kind changes).
    public var shown: AlertFiring?
    public var n = 0
    public var firedAt: Date?
    public var firedKind: AlertKind
    /// Why it is quiet now (nil = not quiet).
    public var quiet: QuietReason?
    /// When the next alert is due (nil = never), from the last evaluation.
    public var next: Date?
    /// *Unmute* / *Alert me anyway* at this time: treatments logged up to then and the falling rule don't quiet it.
    public var liftedAt: Date?
    /// Lifted since the last alert: the next match alerts at once.
    public var liftPending = false
    /// Since when the side has been out of range in a row (by the margin, SPEC §1 #37; nil = it is back): a waiting
    /// first alert counts from here.
    public var outSince: Date?

    init(_ kind: AlertKind, at t: Date) {
        side = kind.side
        start = t
        self.kind = kind
        lastIn = t
        firedKind = kind
        next = t
        outSince = t
    }

    /// Minimum hold time: an episode lasts at least this long after its side last matched.
    static let minHoldMin = 5

    func holdTime(_ s: AlertSettings) -> TimeInterval { TimeInterval(max(s[kind].repeatMin, Self.minHoldMin) * 60) }

    /// The clock was set back by `d`: every time moves with it (what is due stays as far away as it was).
    mutating func shiftBack(_ d: TimeInterval) {
        start = start.shiftedBack(d)
        lastIn = lastIn.shiftedBack(d)
        firedAt = firedAt?.shiftedBack(d)
        next = next?.shiftedBack(d)
        liftedAt = liftedAt?.shiftedBack(d)
        outSince = outSince?.shiftedBack(d)
        switch quiet {
        case .treatment(let k, let at, let until): quiet = .treatment(k, at: at, until: until.shiftedBack(d))
        case .sideMuted(let until): quiet = .sideMuted(until: until.shiftedBack(d))
        case .allMuted(let until): quiet = .allMuted(until: until.shiftedBack(d))
        case .fallingBack, nil: break
        }
    }
}

extension AlertEngine {
    /// What survives a relaunch (SPEC: an update must not take an ongoing alert for a new one and ring again): the open
    /// episodes, the sides' Mute / Remind less and Mute all. Not the settings: they are stored on their own (schema key
    /// `alerts`, SPEC §3 JSON) and may come from the wearer.
    public struct Snapshot: Codable, Equatable, Sendable {
        public static let formatVersion = 1
        public var v = Snapshot.formatVersion
        public var episodes: [Episode]
        public var snoozes: SideSnoozes
        public var muteAllUntil: Date?
        /// When the reading source started, and whether it has delivered a reading since (no data counts from the start
        /// until then).
        public var sourceStart: Date?
        public var sourceDelivered: Bool?
        /// The newest time the engine saw: a later launch before it means the clock was set back meanwhile.
        public var lastNow: Date?

        public init(
            episodes: [Episode], snoozes: SideSnoozes, muteAllUntil: Date?, sourceStart: Date? = nil,
            sourceDelivered: Bool? = nil, lastNow: Date? = nil
        ) {
            self.episodes = episodes
            self.snoozes = snoozes
            self.muteAllUntil = muteAllUntil
            self.sourceStart = sourceStart
            self.sourceDelivered = sourceDelivered
            self.lastNow = lastNow
        }

        public func encoded() -> Data {
            let e = JSONEncoder()
            e.outputFormatting = [.sortedKeys]
            e.dateEncodingStrategy = .secondsSince1970
            return (try? e.encode(self)) ?? Data()
        }

        /// nil when unreadable or of another format version: the caller starts fresh (alerts active, nothing muted).
        public static func decode(_ data: Data) -> Snapshot? {
            let d = JSONDecoder()
            d.dateDecodingStrategy = .secondsSince1970
            guard let s = try? d.decode(Snapshot.self, from: data), s.v == formatVersion else { return nil }
            return s
        }
    }

    public var snapshot: Snapshot {
        Snapshot(
            episodes: AlertGroup.allCases.compactMap { episodes[$0] }, snoozes: snoozes, muteAllUntil: muteAllUntil,
            sourceStart: context.sourceStart, sourceDelivered: sourceDelivered, lastNow: clock.lastNow)
    }

    /// An engine back in the `snapshot` state at `t` (no evaluation, so nothing rings). A clock set back meanwhile moves
    /// every kept time back with it; episodes over by `t` are dropped, untrustworthy mutes sanitized. The effects are the
    /// firings whose notification was up (`.resume`: show again silently, same id and n, never republish) and the next
    /// timer (`.schedule`).
    public static func restore(_ snapshot: Snapshot?, context: AlertContext, at t: Date) -> (AlertEngine, [AlertEffect])
    {
        var e = AlertEngine(context: context)
        guard let snapshot else { return (e, [.schedule(e.nextCheck(after: t))]) }
        if let start = snapshot.sourceStart {
            e.context.sourceStart = start
            e.sourceDelivered = snapshot.sourceDelivered ?? false
        }
        e.snoozes = snapshot.snoozes
        e.muteAllUntil = snapshot.muteAllUntil
        var kept: [AlertGroup: Episode] = [:]
        for ep in snapshot.episodes { kept[ep.kind.group] = ep }
        e.episodes = kept
        e.clock = ClockWatch(lastNow: snapshot.lastNow)
        e.observeClock(t, monotonic: nil)
        e.snoozes.sanitize(at: t)
        if let all = e.muteAllUntil {
            e.muteAllUntil =
                all <= t
                ? nil
                : all >= muteAllForever ? muteAllForever : min(all, t.addingTimeInterval(SettingsSpec.muteAll.max * 60))
        }
        e.inForce = e.settingsInForce(at: t)
        e.episodes = e.episodes.filter { _, ep in
            ep.lastIn <= t && t.timeIntervalSince(ep.lastIn) < ep.holdTime(e.inForce)
        }
        let resumed = AlertGroup.allCases.compactMap { e.episodes[$0]?.shown }.map(AlertEffect.resume)
        return (e, resumed + [.schedule(e.nextCheck(after: t))])
    }
}
