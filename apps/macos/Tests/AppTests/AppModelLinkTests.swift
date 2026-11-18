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
        var asked: [SourceKind] = []
        model.onFollowBehind = { asked.append($0) }
        await until { model.readings.last?.mgdl == 123 && !asked.isEmpty }
        // the person answers the question with Stop Following (its own test: theFollowFoundBehindASourceIsAsked)
        #expect(asked == [.lanJuggluco])
        #expect(await follower.left.isEmpty)
        await model.leaveLink(keepHistory: true)
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
                // the follow was left once answered (its relays and Bluetooth go with it), the history kept
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

    /// A Mac updated with both (the owner's, 2026-11-18: LibreLinkUp as the source and a live follow of the phone): at
    /// launch it asks *Keep following Ana?* in Settings › Source, as picking that source would, and never leaves the
    /// link unasked (a leave is final: it took a new invite). *Keep Following* (and *Cancel* in the leave questions)
    /// reads the link again; *Stop Following* leaves it, the source in use staying. An ended follow behind a source has
    /// no one to keep following: it is left at once, its history kept.
    @MainActor @Test func theFollowFoundBehindASourceIsAsked() async throws {
        let follower = LinkLeftovers(ana())
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, follower: follower)
        let store = InMemorySettingsStore()
        SourceSpec(kind: .libreLinkUp).store(in: store)
        model.sourceKind = .libreLinkUp
        let settings = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        let leave = settings.leave
        leave.linkState = { model.linkState }
        leave.history = { (true, nil) }
        leave.name = { model.linkName }
        leave.leave = { keep in await model.leaveLink(keepHistory: keep) }
        var asked: [SourceKind] = []
        model.onFollowBehind = { kind in
            asked.append(kind)
            leave.askBehind(kind)
        }
        model.run()
        await until { !asked.isEmpty }
        model.apply(ana())  // a later snapshot asks nothing more
        #expect(asked == [.libreLinkUp])
        #expect(await follower.left.isEmpty)
        #expect(leave.askingKeepFollowing && leave.keepTitle == "Keep following Ana?")
        #expect(leave.keepText.contains("Reading LibreLinkUp here instead"))
        // Keep Following: the link is the source again
        leave.keepFollowing()
        #expect(SourceSpec.stored(in: store) == .sukrara)
        #expect(await follower.left.isEmpty)
        // asked again (the next launch): Stop Following… → the leave questions; Cancel keeps following too
        SourceSpec(kind: .libreLinkUp).store(in: store)
        leave.askBehind(.libreLinkUp)
        leave.stopInstead()
        #expect(leave.askingLeave)
        leave.cancel()
        #expect(SourceSpec.stored(in: store) == .sukrara)
        // Stop Following: left (the wearer told), the source in use stays
        SourceSpec(kind: .libreLinkUp).store(in: store)
        leave.askBehind(.libreLinkUp)
        leave.stopInstead()
        await leave.confirm()
        #expect(await follower.left == [true])
        #expect(SourceSpec.stored(in: store) == SourceSpec(kind: .libreLinkUp))

        // an ended follow behind a source: left at once, nothing asked
        var ended = ana()
        ended.link = .revoked
        let gone = LinkLeftovers(ended)
        let other = AppModel(plan: plan, clock: clock, follower: gone)
        other.sourceKind = .nightscout
        var askedEnded = false
        other.onFollowBehind = { _ in askedEnded = true }
        other.run()
        await until { !(await gone.left).isEmpty }
        #expect(await gone.left == [true])
        #expect(!askedEnded)
    }

    /// Appendix G finding 12: *Also saved on this Mac* lists what isn't in use (another source's site and secret, a kept
    /// history), never the source in use, and each removal does only its own part.
    @MainActor @Test func alsoSavedListsWhatIsNotInUseAndRemovesIt() async throws {
        final class Calls {
            var forgot = 0
            var deleted: [String] = []
        }
        let calls = Calls()
        let store = InMemorySettingsStore()
        SourceSpec(kind: .lanJuggluco).store(in: store)
        store.set("https://ns.example.org", for: MacSettings.nightscoutURL)
        store.set(Self.phone, for: MacSettings.jugglucoAddress)
        let secrets = InMemorySecretStore()
        try secrets.write(Data("token".utf8), account: MacSettings.nightscoutSecretAccount)
        let settings = SettingsModel(
            store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        settings.nightscout.secrets = secrets
        settings.juggluco.secrets = secrets
        let also = settings.alsoSaved
        let ana = KeptHistory.Info(id: KeptHistory.id(.sukrara, "ana"), name: "Ana", keptAt: 1_790_000_000)
        also.keptNotShown = { calls.deleted.isEmpty ? [ana] : [] }
        also.deleteKept = { calls.deleted.append($0) }
        also.forgetNightscoutSource = { calls.forgot += 1 }
        also.load()
        // Juggluco is the source: not listed
        #expect(also.items == [.nightscout(host: "ns.example.org"), .kept(ana)])
        #expect(AlsoSavedModel.name(ana) == "Ana" && AlsoSavedModel.detail(ana).hasPrefix("Sukrara on a phone · kept"))
        #expect(AlsoSavedModel.confirmation(.kept(ana)).destructive)
        await also.remove(.nightscout(host: "ns.example.org"))
        #expect(store.value(MacSettings.nightscoutURL).isEmpty && calls.forgot == 1)
        #expect(try secrets.read(account: MacSettings.nightscoutSecretAccount) == nil)
        #expect(store.value(MacSettings.jugglucoAddress) == Self.phone)  // the source in use is untouched
        await also.remove(.kept(ana))
        #expect(calls.deleted == [ana.id] && also.items.isEmpty)
        // another source: Juggluco's address is listed, Forget clears it
        SourceSpec(kind: .nightscout).store(in: store)
        settings.source.choose(SourceSpec(kind: .nightscout))
        also.load()
        #expect(also.items == [.juggluco(address: Self.phone)])
        await also.remove(.juggluco(address: Self.phone))
        #expect(store.value(MacSettings.jugglucoAddress).isEmpty && also.items.isEmpty)
    }

    /// Appendix G findings 5 and 16: Ana's follow left with *Keep it*, then Juggluco read: Juggluco continues it. The
    /// shown history, the rings, the report and the export count over both (Ana's readings where she has one, the fence),
    /// her treatments are marks and export lines but never quiets, and the kept history is no longer listed apart.
    @Test func theNextIdentityReadContinuesTheKeptHistory() async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "continue-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let kept = root.appending(path: "kept")
        let ana = KeptHistory.id(.sukrara, "ana-key")
        let folder = KeptHistory.folder(ana, in: kept)
        let now = clock.now
        // eight days of Ana's every 5 minutes up to 10 minutes ago
        let hers = stride(from: 8 * 86_400, through: 600, by: -300).map {
            Reading(t: now.addingTimeInterval(-Double($0)), mgdl: 100, sensorRate: nil)
        }
        try await ReadingsFileStore(directory: folder, clock: clock).merge(hers)
        var log = FollowedTreatments()
        log.entries["ana-carbs"] = .init(
            treatment: Treatment(
                id: "ana-carbs", k: .carbs, a: 25, t: Int64(now.timeIntervalSince1970) - 3600, u: 1, o: .phone),
            creator: "ana")
        try await TreatmentFileStore(directory: folder, clock: clock).save(log)
        KeptHistory.write(KeptHistory.Info(id: ana, name: "Ana", keptAt: 1), in: kept)
        KeptHistory.awaitNext(ana, in: kept)
        let other = KeptHistory.id(.libreLinkUp, "leo")
        KeptHistory.write(KeptHistory.Info(id: other, name: "Leo", keptAt: 2), in: kept)

        let lan = JugglucoLanSource(
            http: juggluco(), store: InMemoryReadingsStore(), clock: clock, interfaces: { [] },
            sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: clock, lan: lan, kept: kept)
        model.sourceKind = .lanJuggluco
        model.lanConfig = LanConfig(address: Self.phone)
        model.run()
        await until { model.allReadings.count == hers.count + 1 }
        #expect(model.allReadings.last?.mgdl == 123 && model.allReadings.first?.mgdl == 100)
        #expect(KeptHistory.info(ana, in: kept)?.continuedBy == KeptHistory.id(.lanJuggluco, "network"))
        #expect(KeptHistory.info(ana, in: kept)?.awaitingNext == false)
        #expect(model.keptNotShown().map(\.id) == [other])
        // the rings count over eight days: the 7-day one shows
        #expect(model.slots?.counted == hers.count + 1)
        // her carbs: a mark and an export line
        #expect(model.treatments.map(\.id) == ["ana-carbs"] && !model.marks.isEmpty)
        model.deleteKept(other)
        #expect(model.keptNotShown().isEmpty)
        await model.stop()
    }
}
