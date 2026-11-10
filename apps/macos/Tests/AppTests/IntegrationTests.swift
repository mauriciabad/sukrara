// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp

/// A follower that answers like `inner` and records `stop`, which takes a moment (as closing sockets does): a caller
/// that doesn't wait for it sees `stopped` false.
actor StopRecordingFollower: FollowerService {
    private let inner: any FollowerService
    private(set) var stopped = false

    init(_ inner: any FollowerService) { self.inner = inner }

    func snapshots() async -> AsyncStream<FollowerSnapshot> { await inner.snapshots() }
    func alerts() async -> AsyncStream<AlertPayload> { await inner.alerts() }
    func start() async { await inner.start() }
    func stop() async {
        try? await Task.sleep(for: .milliseconds(50))
        await inner.stop()
        stopped = true
    }
    func reconnect() async {}
    func lookUp(link: String) async throws(FollowerJoinError) -> Invite { throw .alreadyFollowing }
    func join(invite: Invite) async throws(FollowerJoinError) { throw .alreadyFollowing }
    func cancelJoin() async {}
    func joinNearby(_ offer: NearbyOffer) async throws(FollowerJoinError) { throw .alreadyFollowing }
    func confirmNearby(same: Bool) async -> Bool { false }
    func searchNearby(_ on: Bool) async {}
    func setProfile(name: String, avatar: Avatar?) async {}
    func readings(since: Date) async -> [Reading] { await inner.readings(since: since) }
    func unfollow() async {}
    func send(_ treatment: Treatment) async -> TreatmentSendResult { .notAllowed }
}

/// The app's wiring: the fixture launch, mirrored alert settings, the runtime → windows mapping.
@Suite struct IntegrationTests {
    let home = URL(fileURLWithPath: "/Users/u", isDirectory: true)

    // MARK: fixture launch

    @Test func fixtureNeedsADebugDevRun() throws {
        let debug = BuildInfo(isDebugBuild: true, bundlePath: "/Users/u/repo/build/Sukrara.app", isBundle: true)
        let release = BuildInfo(isDebugBuild: false, bundlePath: "/Users/u/repo/build/Sukrara.app", isBundle: true)
        let installed = BuildInfo(isDebugBuild: false, bundlePath: "/Applications/Sukrara.app", isBundle: true)
        let exists: (String) -> Bool = { $0 == "/Users/u/repo/.git" }
        let env = ["SUKRARA_DEV": "1", "SUKRARA_FIXTURE": "1"]

        let p = try LaunchPlan.parse([], build: debug, env: env, home: home, exists: exists)
        #expect(p.mode == .fixture)
        #expect(p.services == .none)
        #expect(p.data.isIsolated)
        // a release build never shows made-up data
        #expect(throws: LaunchError.debugOnly) {
            try LaunchPlan.parse([], build: release, env: env, home: home, exists: exists)
        }
        // the installed app ignores the variable
        let user = try LaunchPlan.parse([], build: installed, env: env, home: home, exists: exists)
        #expect(user.mode == .normal)
        #expect(user.services == .all)
    }

    @Test func fixtureShowsTheSyntheticWearer() async {
        let plan = LaunchPlan(mode: .fixture, services: .none, data: .isolated(root: home))
        let model = AppModel(plan: plan)
        var snapshot: FollowerSnapshot?
        for await s in await FixtureFollower().snapshots() {
            snapshot = s
            break
        }
        model.apply(try! #require(snapshot))
        #expect(model.notMonitoringReason == nil)
        #expect(model.statusTitle != "")
        // an offline dev run without the fixture still says why nothing is shown
        let offline = AppModel(plan: LaunchPlan(mode: .normal, services: .none, data: .isolated(root: home)))
        #expect(offline.notMonitoringReason?.contains("offline") == true)
    }

    /// The fixture launch through the app's own wiring with an alert controller: the synthetic wearer's snapshot runs
    /// in, the controller mirrors the wearer's alerts (its own engine only checks no data) and posts nothing, the
    /// card gets its decision from it, and quitting stops the follower before it returns.
    @Test func fixtureRunsWithAnAlertController() async throws {
        let notifier = RecordingNotifier()
        let alerts = AlertController(
            settings: AlertSettings(), clock: SystemClock(), store: MemoryAlertStore(), notifier: notifier,
            wake: RecordingWake())
        let follower = StopRecordingFollower(FixtureFollower())
        let plan = LaunchPlan(mode: .fixture, services: .none, data: .isolated(root: home))
        let model = AppModel(plan: plan, alerts: alerts, follower: follower)
        model.run()
        for _ in 0..<300 where !model.link.isFollowing { try await Task.sleep(for: .milliseconds(10)) }
        #expect(model.link.isFollowing && model.notMonitoringReason == nil)
        #expect(alerts.mirroring)
        #expect(model.cardData.marks.map(\.id) == ["fixture-1", "fixture-2"])
        #expect(notifier.posts.isEmpty)
        await model.stop()
        #expect(await follower.stopped)
    }

    // MARK: mirrored alert settings

    @Test func wearerValuesReplaceOnlyMirroredFields() throws {
        var own = AlertSettings()
        own[.low].threshold = 80
        own[.noData].threshold = 25
        own[.low].alarm = true
        own.sounds.pack = "kart"
        let wearer = try #require(
            WearerSettings(
                json: #"""
                    {"alerts":{"alerts":{"low":{"on":false,"mgdl":65,"repeatMin":10},"no_data":{"on":false,"min":60,"repeatMin":1}},
                     "muteAfterCarbs":{"on":false,"min":30}}}
                    """#, wearer: "Marta"))
        let s = own.mirroring(wearer)
        #expect(s[.low].on == false)
        #expect(s[.low].threshold == 65)
        #expect(s[.low].repeatMin == 10)
        #expect(s[.low].alarm == true)  // alarm is this Mac's
        #expect(s[.noData] == own[.noData])  // no data is always this Mac's: never silenced by the wearer
        #expect(s.sounds.pack == "kart")
        #expect(s.muteAfterCarbs == QuietRule(on: false, min: 30))
        #expect(s[.high] == own[.high])  // not sent: own value
    }

    // MARK: runtime → windows

    @Test func linkStatesMapToTheFollowWindow() {
        var s = FollowerSnapshot.empty
        let check = LinkState.PairingCheck(code: "021472", confirmed: false)
        s.link = .requested(code: "1234", wearer: "Marta", pairing: check)
        #expect(FollowerAdapter.status(s) == .waiting(wearer: "Marta", pairing: check))
        s.link = .requested(code: "1234", wearer: "Marta", pairing: nil)
        #expect(FollowerAdapter.status(s) == .waiting(wearer: "Marta", pairing: nil))
        s.link = .following(wearer: "Marta", canWrite: false)
        s.readings = [Reading(t: Date(timeIntervalSince1970: 100), mgdl: 120)]
        #expect(
            FollowerAdapter.status(s)
                == .following(wearer: "Marta", lastReading: Date(timeIntervalSince1970: 100), canWrite: false))
        #expect(FollowerAdapter.source(s) == "Internet")
        #expect(FollowerAdapter.problem(s) == nil)
        s.nearby = .connected(phone: "Pixel")
        #expect(FollowerAdapter.source(s) == "Internet · Bluetooth: connected to Pixel")
        s.nearby = .unauthorized
        #expect(FollowerAdapter.problem(s)?.contains("Privacy & Security") == true)
        s.storeIssues = [StoreIssue(file: "readings.csv", quarantined: nil)]
        #expect(FollowerAdapter.problem(s)?.hasPrefix("readings.csv couldn't be read") == true)
        s.link = .notFollowing
        #expect(FollowerAdapter.source(s) == nil)
        #expect(FollowerAdapter.failure(.cancelled) == nil)
        #expect(FollowerAdapter.failure(.keyFailed) == .keychain)
        #expect(FollowerAdapter.failure(.alreadyFollowing) == .alreadyFollowing)
        // the invite lookup's own outcomes: each says what to do next
        #expect(FollowerAdapter.failure(.offline) == .offline)
        #expect(FollowerAdapter.failure(.notFound) == .notFound && FollowerAdapter.failure(.expired) == .expired)
        #expect(FollowerAdapter.failure(.offline)?.message.contains("internet") == true)
    }

    @Test func anInviteWhileFollowingKeepsTheStatus() async {
        let following = FollowStatus.following(wearer: "Marta", lastReading: nil, canWrite: false)
        let model = FollowModel(status: following, actions: nil)
        model.linkText = Wire.inviteScheme + String(repeating: "a", count: 32)
        #expect(InviteLink.parse(model.linkText) != nil)
        await model.lookUp()
        #expect(model.status == following)
        #expect(model.failure == .alreadyFollowing)
    }

    @Test func wearerSettingsOnlyWhileFollowing() {
        var s = FollowerSnapshot.empty
        s.wearerSettings = MirroredSettings(u: 1, json: #"{"graph":{"low":72}}"#)
        #expect(WindowCoordinator.wearerSettings(s) == nil)
        s.link = .following(wearer: "Marta", canWrite: true)
        #expect(WindowCoordinator.wearerSettings(s)?.wearer == "Marta")
    }

    @Test func treatmentsSyncKeepsFailedRowsAndDelivery() {
        let model = TreatmentsModel(rows: [], canWrite: false, actions: nil)
        let mine = Treatment(id: "a", k: .carbs, a: 30, t: 200, u: 1, o: .mac)
        let phone = Treatment(id: "b", k: .rapid, a: 2, t: 100, u: 1, o: .phone)
        let gone = Treatment(id: "c", k: .note, a: 0, t: 300, n: "x", u: 2, del: true, o: .mac)
        model.sync([phone, mine, gone], deliveries: ["a": .sending], canWrite: true)
        #expect(model.canWrite)
        #expect(model.rows.map(\.id) == ["a", "b"])
        #expect(model.rows[0].delivery == .sending)
        #expect(model.rows[1].delivery == nil)
        model.set("a", .failed)
        // still sending in the runtime: the failure (and its Retry) stays
        model.sync([phone, mine], deliveries: ["a": .sending], canWrite: true)
        #expect(model.rows[0].delivery == .failed)
        // the relay took it
        model.sync([phone, mine], deliveries: ["a": .sent], canWrite: true)
        #expect(model.rows[0].delivery == .sent)
    }
}
