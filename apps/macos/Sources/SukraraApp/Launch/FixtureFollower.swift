// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import Foundation
    import SukraraCore
    import SukraraGraph
    import SukraraShare

    /// The follower of a `.fixture` launch (debug builds only): a made-up wearer "Fixture" with a seeded synthetic day,
    /// a sensor mid-life and two treatments. No socket, no Keychain, no file: the snapshot is built in memory, so the
    /// bundle smoke test can check with `lsof` that nothing opens a connection. Only *Stop Following* with *Keep it*
    /// writes: the made-up week as a kept history under `kept`, as the runtime's leave keeps a real one.
    actor FixtureFollower: FollowerService {
        private var snapshot: FollowerSnapshot
        private var subscribers: [AsyncStream<FollowerSnapshot>.Continuation] = []
        /// A synthetic week: the panel's chart scrolls and zooms past the snapshot's 24 hours.
        private let history: [Reading]
        /// Where a leave with *Keep it* keeps the week (`SukraraPaths.kept`; nil = nowhere).
        private let kept: URL?
        static let identity = "f1x7"

        init(now: Date = Date(), kept: URL? = nil) {
            self.kept = kept
            let t = now.timeIntervalSince1970
            history = RealisticSynthetic.readings(until: t - 60, days: 7).map {
                Reading(t: Date(timeIntervalSince1970: $0.t), mgdl: Int($0.v.rounded()), sensorRate: $0.rate)
            }
            let readings = history.filter { $0.t.timeIntervalSince1970 >= t - 86400 }
            let start = Int(t) - 6 * 86400
            var snapshot = FollowerSnapshot.empty
            snapshot.link = .following(wearer: "Fixture", canWrite: true)
            snapshot.readings = readings
            snapshot.sensor = WearerSensor(start: start, end: start + 14 * 86400, warmupEnd: start + 3600)
            snapshot.treatments = [
                Treatment(id: "fixture-1", k: .rapid, a: 4.5, t: Int64(t) - 1500, u: 1, o: .phone),
                Treatment(id: "fixture-2", k: .carbs, a: 45, t: Int64(t) - 1320, u: 1, o: .mac),
            ]
            snapshot.deliveries = ["fixture-2": .accepted]
            snapshot.wearer = Profile(name: "Fixture", avatar: .v1(c: 0, s: "drop", a: 45, n: 7))
            snapshot.me = Profile(name: "Test Mac", avatar: .v1(c: 5, s: "cube", a: 90, n: 3))
            snapshot.identity = Self.identity
            self.snapshot = snapshot
        }

        func snapshots() async -> AsyncStream<FollowerSnapshot> {
            let (stream, continuation) = AsyncStream.makeStream(of: FollowerSnapshot.self)
            continuation.yield(snapshot)
            subscribers.append(continuation)
            return stream
        }

        func alerts() async -> AsyncStream<AlertPayload> { AsyncStream { _ in } }
        func start() async {}
        func stop() async {}
        func reconnect() async {}
        func lookUp(link: String) async throws(FollowerJoinError) -> Invite { throw .alreadyFollowing }
        func join(invite: Invite) async throws(FollowerJoinError) { throw .alreadyFollowing }
        func cancelJoin() async {}
        func joinNearby(_ offer: NearbyOffer) async throws(FollowerJoinError) { throw .alreadyFollowing }
        func confirmNearby(same: Bool) async -> Bool { false }
        func searchNearby(_ on: Bool) async {}
        func setProfile(name: String, avatar: Avatar?) async {}
        func readings(since: Date) async -> [Reading] {
            snapshot.link == .notFollowing ? [] : history.filter { $0.t >= since }
        }
        /// Stop following ends the made-up follow (the walk of one person, one link: its leave sheet and no source);
        /// with *Keep it* the week and the two treatments are kept as the runtime keeps a wearer's.
        func unfollow(keepHistory: Bool) async {
            if keepHistory, let kept, snapshot.identity != nil {
                let id = KeptHistory.id(.sukrara, Self.identity)
                let folder = KeptHistory.folder(id, in: kept)
                try? await ReadingsFileStore(directory: folder).merge(history)
                var log = FollowedTreatments()
                for t in snapshot.treatments { log.entries[t.id] = .init(treatment: t, creator: "fixture") }
                try? await TreatmentFileStore(directory: folder).save(log)
                KeptHistory.write(KeptHistory.Info(id: id, name: "Fixture"), in: kept)
            }
            snapshot = FollowerSnapshot.empty
            for c in subscribers { c.yield(snapshot) }
        }
        func send(_ treatment: Treatment) async -> TreatmentSendResult { .notAllowed }
    }
#endif
