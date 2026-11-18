// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore

/// What the menu, panel, widget feed and follow window see of the follower (one value, replaced on every change).
public struct FollowerSnapshot: Sendable, Equatable {
    public var link: LinkState
    /// The last 24 hours of readings, oldest first.
    public var readings: [Reading]
    public var sensor: WearerSensor?
    /// Visible treatments, newest first.
    public var treatments: [Treatment]
    /// Treatments this Mac wrote that no relay confirmed yet (warn before "Stop following").
    public var unsentTreatments: Int
    /// How far each treatment this Mac wrote has got, by id (the Treatments window's per-entry state).
    public var deliveries: [String: TreatmentDelivery] = [:]
    public var wearerSettings: MirroredSettings?
    /// Files that were unreadable and moved aside.
    public var storeIssues: [StoreIssue]
    /// nil when the Bluetooth path isn't running.
    public var nearby: NearbyState?
    /// The relays connected now; nil until the pool has said (not following, or just started).
    public var relaysConnected: Int?
    /// A phone nearby offering to pair (while not following).
    public var nearbyOffer: NearbyOffer?
    /// The Keychain wouldn't read the follower key (`link` is `.broken` with the reason) and the runtime keeps trying.
    /// This Mac still follows the wearer: `readings` are still theirs, and the app keeps alerting on them (the
    /// no-data alarm fires if it doesn't recover) instead of treating it as "not following".
    public var keychainUnreadable = false
    /// How this Mac appears to the wearer (the name and picture sent with the request and in profile updates).
    public var me: Profile?
    /// The wearer's name and picture (from the invite, then the roster), nil before any.
    public var wearer: Profile?
    /// This Mac stopped following and no relay or the wearer's phone took its `leave` yet: the wearer's name ("" when
    /// unknown), nil when nothing waits (PROTOCOL-v2 "Leave").
    public var untoldLeave: String?

    public static let empty = FollowerSnapshot(
        link: .notFollowing, readings: [], sensor: nil, treatments: [], unsentTreatments: 0, deliveries: [:],
        wearerSettings: nil,
        storeIssues: [], nearby: nil, nearbyOffer: nil)
}

/// How the wearer's readings reach this Mac now (the panel's mark at the end of the mute row; Android's `LinkPath`
/// makes the same choice): Bluetooth names the path while it's connected (a reading over it doesn't wait for the
/// internet), else the internet while a relay is connected, else nothing can arrive.
public enum LinkPath: Sendable, Equatable {
    case nearby, internet, offline

    /// nil while not following, or before the relays have said anything and Bluetooth isn't connected.
    public static func of(_ snapshot: FollowerSnapshot) -> LinkPath? {
        guard snapshot.link.isFollowing else { return nil }
        if case .connected = snapshot.nearby { return .nearby }
        guard let relays = snapshot.relaysConnected else { return nil }
        return relays > 0 ? .internet : .offline
    }
}

/// "Pair nearby" open on a wearer's phone in Bluetooth range: its invite and `commit`, the wearer's commitment to its
/// pairing nonce. Only a name and a picture until both devices show the same pairing code.
public struct NearbyOffer: Sendable, Equatable {
    public let invite: Invite
    public let commit: String

    public init(invite: Invite, commit: String) {
        self.invite = invite
        self.commit = commit
    }

    public var wearerName: String? { Profile.shownName(invite.profile?.name) }
    public var wearerAvatar: Avatar? { invite.profile?.avatar }
}

public enum FollowerJoinError: Error, Equatable, Sendable {
    /// The text isn't a Sukrara invite link.
    case invalidLink
    /// No invite for this link reached the relays in time (the phone may be offline, or the link was replaced).
    case notFound
    /// No relay answered the lookup at all: this Mac couldn't reach the internet.
    case offline
    case expired
    /// The invite was sealed by someone else than the link names.
    case fingerprintMismatch
    /// A follow waits or runs (one that ended, declined, used elsewhere or removed, is replaced by a new join).
    case alreadyFollowing
    case cancelled
    /// The follower key couldn't be stored in the Keychain.
    case keychain
    /// This Mac's follower key couldn't be made.
    case keyFailed
}

public enum TreatmentSendResult: Equatable, Sendable {
    /// Signed and on its way (delivery shown per entry: sending → sent → accepted).
    case sending
    /// The wearer hasn't allowed this Mac to add treatments (or this Mac isn't following).
    case notAllowed
}

/// The follower runtime as the app sees it (`FollowerRuntime`; 21's AppModel and 23's follow window code against
/// this). Every call is safe in every state.
public protocol FollowerService: Sendable {
    /// Each call is one subscriber: the current snapshot first, then every change (only the newest is buffered).
    func snapshots() async -> AsyncStream<FollowerSnapshot>
    /// Alerts the wearer's phone mirrors (PROTOCOL-v2 "Alerts"), deduped across restarts. Not buffered for absent
    /// subscribers: subscribe before `start()`.
    func alerts() async -> AsyncStream<AlertPayload>
    func start() async
    /// Terminal: closes every connection and saves the state.
    func stop() async
    /// The Mac woke or the network came back: reconnect now instead of waiting for the watchdog.
    func reconnect() async
    /// Looks up the invite behind a link (web link, `sukrara:` link or a QR code's text) on the relays. Reads only: no
    /// key is made and nothing is sent, so the person sees who is inviting (`Invite.profile`) before asking.
    func lookUp(link: String) async throws(FollowerJoinError) -> Invite
    /// The person clicked *Follow* on an invite `lookUp` found: makes this Mac's follower key and asks the wearer.
    func join(invite: Invite) async throws(FollowerJoinError)
    func cancelJoin() async
    /// Follows the wearer whose phone offers nearby pairing (nothing of theirs is used until `confirmNearby(same: true)`).
    func joinNearby(_ offer: NearbyOffer) async throws(FollowerJoinError)
    /// The person compared the pairing codes on both devices: `same` lets the wearer's key in (held, or the next one);
    /// a different code ends the join (the follower key goes, nothing is sent). False when there was nothing to confirm.
    func confirmNearby(same: Bool) async -> Bool
    /// The wearer's stored readings since `since`, oldest first (the store keeps 31 days); empty while not following.
    func readings(since: Date) async -> [Reading]
    /// How this Mac appears to the wearer: used by the next join, and sent at once (end-to-end encrypted) while a
    /// follow exists. A blank name keeps the previous one.
    func setProfile(name: String, avatar: Avatar?) async
    /// Looks for phones offering nearby pairing while not following (the follow window is open).
    func searchNearby(_ on: Bool) async
    /// Stops following: tells the wearer (a `leave`, kept until two relays have it), wipes the wearer's readings,
    /// treatments, sensor and settings, then the follower key.
    func unfollow() async
    func send(_ treatment: Treatment) async -> TreatmentSendResult
}

/// A relay connection the runtime can start and stop (`RelayPool`; tests use `FakeRelay`).
public protocol RelayLink: RelayTransport {
    func start() async
    func stop() async
}

extension RelayPool: RelayLink {}
