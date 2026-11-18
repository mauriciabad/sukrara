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
@testable import SukraraGraph

/// A follower whose follow is Ana's, with everything a link brings, that records how it was left.
actor LinkLeftovers: FollowerService {
    let snapshot: FollowerSnapshot
    private(set) var left: [Bool] = []

    init(_ snapshot: FollowerSnapshot) { self.snapshot = snapshot }

    func snapshots() async -> AsyncStream<FollowerSnapshot> {
        let (stream, c) = AsyncStream.makeStream(of: FollowerSnapshot.self)
        c.yield(snapshot)
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
    func readings(since: Date) async -> [Reading] { snapshot.readings }
    func unfollow(keepHistory: Bool) async { left.append(keepHistory) }
    func send(_ treatment: Treatment) async -> TreatmentSendResult { .notAllowed }
}

/// One person, one link on the Mac (docs/44 Appendix G; contracts/sources/link.json): a non-link source uses nothing
/// of the link, a follow left behind another source is left at start, and the leave questions apply what was answered.
@Suite struct AppModelLinkTests {
    nonisolated static let phone = "192.168.1.7"
    let clock = FakeClock()

    /// Ana's follow with everything it brings: readings, a treatment, her sensor, her settings, Bluetooth connected.
    func ana() -> FollowerSnapshot {
        var s = FollowerSnapshot.empty
        s.link = .following(wearer: "Ana", canWrite: true)
        s.wearer = Profile(name: "Ana", avatar: nil)
        s.readings = [Reading(t: clock.now.addingTimeInterval(-30), mgdl: 200, sensorRate: 0)]
        s.treatments = [
            Treatment(
                id: "ana-carbs", k: .carbs, a: 25, t: Int64(clock.now.timeIntervalSince1970) - 600, u: 1, o: .phone)
        ]
        s.sensor = WearerSensor(start: 1000, end: 2000, warmupEnd: 1100)
        s.nearby = .unauthorized
        s.relaysConnected = 2
        s.wearerSettings = MirroredSettings(
            u: 1, json: #"{"v":2,"type":"settings","u":1,"alerts":{"alerts":{"low":{"on":true,"mgdl":80}}}}"#)
        return s
    }

    func juggluco() -> FakeLanHTTP {
        let t = Int(clock.now.timeIntervalSince1970)
        return FakeLanHTTP(
            answer: { url, _ in
                guard url.host() == Self.phone else { return nil }
                return url.path().hasSuffix("entries.json") ? (200, Data("[]".utf8)) : (404, Data())
            },
            live: { url in
                url.host() == Self.phone
                    ? (200, ["event: glucose", "data: {\"timestamp\": \(t), \"glucoseMgDl\": 123}"]) : nil
            })
    }

    func until(_ ok: () async -> Bool) async {
        for _ in 0..<500 where !(await ok()) { try? await Task.sleep(for: .milliseconds(10)) }
    }

    /// link.json `after`: Juggluco on the network as the source while a follow record of Ana's is still around (a Mac
    /// updated in the old state): each thing the link brings is checked by its id.
    @Test func aNonLinkSourceUsesNothingFromTheLink() async throws {
        let follower = LinkLeftovers(ana())
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let lan = JugglucoLanSource(
            http: juggluco(), store: InMemoryReadingsStore(), clock: clock, interfaces: { [] },
            sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, alerts: alerts, follower: follower, lan: lan)
        let store = InMemorySettingsStore()
        SourceSpec(kind: .lanJuggluco).store(in: store)
        model.sourceKind = .lanJuggluco
        model.lanConfig = LanConfig(address: Self.phone)
        model.run()
        await until {
            let left = await follower.left
            return model.readings.last?.mgdl == 123 && !left.isEmpty
        }
        #expect(await model.writeLan(Treatment(id: "mac-1", k: .rapid, a: 2, t: 1, u: 1, o: .mac)))
        await until { model.treatments.map(\.id) == ["mac-1"] }
        let settings = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        settings.wearer = WindowCoordinator.wearerSettings(ana())  // what a stale snapshot would hand the windows

        let contract = try JSONSerialization.jsonObject(with: Data(contentsOf: Contracts.url("sources/link.json")))
        let after = try #require((contract as? [String: Any])?["after"] as? [[String: Any]])
        for id in after.compactMap({ $0["id"] as? String }) {
            switch id {
            case "connection":
                // the follow was left (its relays and Bluetooth go with it), the history kept
                #expect(await follower.left == [true], "\(id)")
            case "settings":
                #expect(settings.settingsFrom == .custom && settings.editable && !settings.linkSource, "\(id)")
                #expect(GraphConfig.load(from: store, wearer: settings.wearer) == GraphConfig(), "\(id)")
            case "name":
                #expect(
                    !model.statusTitle.contains("Ana") && model.notMonitoringReason?.contains("Ana") != true, "\(id)")
            case "path":
                #expect(model.linkPath == nil && model.nearby == nil && model.blocked.isEmpty, "\(id)")
            case "alerts":
                #expect(!alerts.mirroring, "\(id)")
            case "escalation":
                #expect(!(settings.settingsFrom == .wearer && settings.linkSource), "\(id)")
            case "treatments":
                #expect(model.treatments.map(\.id) == ["mac-1"] && model.ownLogActive, "\(id)")
            case "sensor":
                #expect(model.sensor?.start != 1000, "\(id)")
            case "noData":
                #expect(model.readings.map(\.mgdl) == [123] && model.noDataReason == nil, "\(id)")
            case "record":
                // the follow record goes through the one leave (`AppModel.leaveLink`), once
                #expect(await follower.left.count == 1, "\(id)")
            default:
                Issue.record("link.json `after` has \(id): check it here")
            }
        }
    }

    /// The leave questions: *Keep following?* first, then only what `LinkRules.leave` asks; *Stop Following* applies
    /// the answers (their settings as this Mac's own, the history kept), leaves through the one function and reads the
    /// picked source; *Stop following* alone leaves no source, and another Sukrara picked later starts with their
    /// settings again.
    @Test func theLeaveQuestionsApplyWhatWasAnswered() async {
        let store = InMemorySettingsStore()
        SourceSpec.sukrara.store(in: store)
        let settings = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        settings.wearer = WindowCoordinator.wearerSettings(ana())
        let leave = settings.leave
        var left: [Bool] = []
        var linked = LinkRules.State.active
        leave.linkState = { linked }
        leave.history = { (true, Date(timeIntervalSince1970: 1_790_000_000)) }
        leave.name = { "Ana" }
        leave.leave = { left.append($0) }
        #expect(!leave.pick(.libreLinkUp, from: .settings))
        #expect(leave.askingKeepFollowing && leave.keepTitle == "Keep following Ana?")
        #expect(leave.keepText.contains("Reading LibreLinkUp here instead"))
        leave.keepFollowing()
        #expect(settings.source.stored == .sukrara && !leave.askingLeave)
        leave.pick(.libreLinkUp, from: .settings)
        leave.stopInstead()
        #expect(leave.askingLeave && leave.questions == [.settings, .history])
        #expect(leave.keepSettings && leave.keepHistory)  // nothing shifts, nothing is lost
        await leave.confirm()
        #expect(left == [true] && settings.source.stored == SourceSpec(kind: .libreLinkUp))
        #expect(store.storedSettingsFrom == .custom && settings.alerts.settings[.low].threshold == 80)
        // Stop following alone: no source; another Sukrara later starts with their settings again
        settings.source.choose(.sukrara)
        #expect(store.storedSettingsFrom == .wearer)
        settings.wearer = nil
        linked = .ended
        leave.stopFollowing()
        #expect(leave.questions == [.history])  // their settings were never received: Sukrara's defaults
        leave.keepHistory = false
        await leave.confirm()
        #expect(left == [true, false] && settings.source.stored == nil)
        // not linked: a pick asks nothing
        linked = .none
        #expect(leave.pick(.nightscout, from: .setup) && settings.source.stored == SourceSpec(kind: .nightscout))
    }
}
