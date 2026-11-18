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

/// LibreLinkUp as the Mac's source (#213 step 2), over a fake LibreLinkUp (no account, no network): the card, the
/// alerts, this Mac's own treatment log, the sensor, the no-data reasons, Connect and Settings › Source's words.
@Suite struct AppModelLibreLinkUpTests {
    let clock = FakeClock()
    nonisolated static let account = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
    nonisolated static let ana = "11111111-2222-4333-8444-555555555555"

    nonisolated static func time(_ t: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "M/d/yyyy h:mm:ss a"
        return f.string(from: t)
    }

    /// LibreLinkUp following Ana: her live reading 1 min old (rising), a graph point 10 min old, a sensor started 2 days
    /// ago; `refuse` = every login refused.
    func http(refuse: Bool = false) -> FakeLibreLinkUpHTTP {
        let clock = self.clock
        return FakeLibreLinkUpHTTP { r in
            let now = clock.now
            if r.path == "/llu/auth/login" {
                if refuse { return FakeLibreLinkUpHTTP.json(#"{"status":2}"#) }
                return FakeLibreLinkUpHTTP.json(
                    #"{"status":0,"data":{"user":{"id":"\#(Self.account)"},"authTicket":{"token":"tok","expires":\#(Int(now.timeIntervalSince1970) + 3600)}}}"#
                )
            }
            if r.path == "/llu/connections" {
                return FakeLibreLinkUpHTTP.json(
                    #"{"status":0,"data":[{"patientId":"\#(Self.ana)","firstName":"Ana","lastName":"Example"}]}"#)
            }
            return FakeLibreLinkUpHTTP.json(
                #"""
                {"status":0,"data":{"connection":{"firstName":"Ana","lastName":"Example",
                "sensor":{"sn":"0D00ABC123","a":\#(Int(now.timeIntervalSince1970) - 2 * 86_400)},
                "glucoseMeasurement":{"FactoryTimestamp":"\#(Self.time(now - 60))","ValueInMgPerDl":131,"TrendArrow":4}},
                "graphData":[{"FactoryTimestamp":"\#(Self.time(now - 600))","ValueInMgPerDl":124}]}}
                """#)
        }
    }

    func model(_ http: FakeLibreLinkUpHTTP, alerts: AlertController? = nil) -> (AppModel, LibreLinkUpSource, URL) {
        let dir = FileManager.default.temporaryDirectory.appending(path: "llu-app-\(UUID().uuidString)")
        let source = LibreLinkUpSource(
            http: http, live: InMemoryReadingsStore(), graph: InMemoryReadingsStore(),
            state: JSONFileStore(url: dir.appending(path: "state.json")), secrets: InMemorySecretStore(),
            clock: clock, sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        return (AppModel(plan: plan, clock: clock, alerts: alerts, libreLinkUp: source), source, dir)
    }

    func until(_ ok: () -> Bool) async {
        for _ in 0..<500 where !ok() { try? await Task.sleep(for: .milliseconds(10)) }
    }

    @Test func libreLinkUpFeedsTheCardTheAlertsTheSensorAndTheOwnLog() async {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let (model, _, dir) = model(http(), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .libreLinkUp
        // signed in, no one picked yet: Connect is the way
        await until { model.noDataReason == .libreLinkUp(.notSignedIn) }
        #expect(model.noDataReason?.action == .libreLinkUp)
        #expect(model.notMonitoringReason == "Not signed in to LibreLinkUp")
        let result = await model.connectLibreLinkUp(email: "alex@example.com", password: "pw", yours: false)
        #expect(result == .connected(people: [LluPerson(id: Self.ana, name: "Ana Example")], picked: Self.ana))
        model.libreLinkUpConfig = LibreLinkUpConfig(patient: Self.ana)
        #expect(AddTreatmentAvailability(model) == .available)
        await until { model.readings.last?.mgdl == 131 }
        #expect(model.readings.map(\.mgdl) == [124, 131])
        #expect(model.statusTitle.hasPrefix("131"))
        #expect(model.noDataReason == nil)
        #expect(model.onlinePath?.site == "LibreLinkUp" && model.onlinePath?.path == .internet)
        #expect(!alerts.mirroring && alerts.nextWake != nil)
        // the sensor LibreLinkUp says, with the default warm-up and wear
        let start = clock.now.timeIntervalSince1970 - 2 * 86_400
        #expect(model.sensor == SensorInfo(start: start, end: start + 15 * 86_400, warmupEnd: start + 3600))
        // this Mac's own treatment log
        let carbs = Treatment(
            id: UUID().uuidString.lowercased(), k: .carbs, a: 30, t: Int64(clock.now.timeIntervalSince1970), n: "",
            u: Int64(clock.now.timeIntervalSince1970 * 1000), o: .mac)
        #expect(await model.writeLibreLinkUp(carbs))
        await until { model.treatments.count == 1 }
        #expect(model.treatments.count == 1 && model.marks.count == 1)
        // another source: LibreLinkUp's readings and the log leave the card at once
        model.sourceKind = .lanJuggluco
        await until { model.readings.isEmpty }
        #expect(model.readings.isEmpty)
        #expect(model.treatments.isEmpty && model.marks.isEmpty)
        await model.stop()
    }

    @Test func aRefusedPasswordIsNeverMonitored() async {
        let (model, source, dir) = model(http(refuse: true))
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .libreLinkUp
        #expect(await source.connect(email: "alex@example.com", password: "wrong", yours: true) == .badCredentials)
        model.libreLinkUpConfig = LibreLinkUpConfig(patient: Self.ana)
        // no account saved: nothing to read
        await until { model.libreLinkUpSnapshot.outcome != nil }
        #expect(model.readings.isEmpty)
        #expect(model.noDataReason == .libreLinkUp(.notSignedIn))
        await model.stop()
    }

    @Test func eachHealthStateIsAReasonInWords() {
        let now = clock.now
        func snap(_ outcome: SourceHealth.Outcome?, ages: [TimeInterval], online: Bool = true) -> LibreLinkUpSnapshot {
            LibreLinkUpSnapshot(
                email: "alex@example.com", running: true, outcome: outcome, online: online,
                readings: ages.map { Reading(t: now.addingTimeInterval(-$0), mgdl: 100) })
        }
        #expect(AppModel.libreLinkUpWait(snap(nil, ages: []), now: now) == .libreLinkUp(.connecting))
        #expect(AppModel.libreLinkUpWait(snap(.ok, ages: [60]), now: now) == nil)
        #expect(AppModel.libreLinkUpWait(snap(.ok, ages: []), now: now) == .libreLinkUp(.noGlucose))
        #expect(
            AppModel.libreLinkUpWait(snap(.ok, ages: [1200]), now: now)
                == .libreLinkUp(.silent(since: now.timeIntervalSince1970 - 1200)))
        #expect(AppModel.libreLinkUpWait(snap(.network, ages: [], online: false), now: now) == .libreLinkUp(.offline))
        #expect(AppModel.libreLinkUpWait(snap(.server, ages: []), now: now) == .libreLinkUp(.unreachable))
        #expect(AppModel.libreLinkUpWait(snap(.refused, ages: []), now: now) == .libreLinkUp(.signedOut))
        #expect(AppModel.libreLinkUpWait(snap(.terms, ages: []), now: now) == .libreLinkUp(.terms))
        #expect(AppModel.libreLinkUpWait(snap(.notShared, ages: []), now: now) == .libreLinkUp(.notShared))
        #expect(AppModel.libreLinkUpWait(snap(.retired, ages: []), now: now) == .libreLinkUp(.stopped))
        #expect(AppModel.libreLinkUpWait(snap(.rateLimited, ages: []), now: now) == .libreLinkUp(.rateLimited))
        #expect(
            NoDataText.of(
                .libreLinkUp(.silent(since: now.timeIntervalSince1970 - 1200)), now: now.timeIntervalSince1970)
                == NoDataText(
                    title: "Nothing new from LibreLinkUp",
                    body: "Its last reading was 20 min ago. Open the Libre app: it may have stopped uploading."))
        #expect(LibreLinkUpStatus.of(snap(.ok, ages: [130]), now: now).text == "Live · last reading 2 min ago")
        #expect(LibreLinkUpStatus.of(snap(.refused, ages: [60]), now: now).action == .signIn)
        #expect(LibreLinkUpStatus.of(snap(.notShared, ages: []), now: now).action == .recheck)
        #expect(LibreLinkUpStatus.of(snap(.terms, ages: []), now: now).action == .recheck)
        #expect(LibreLinkUpStatus.of(snap(.server, ages: []), now: now).action == .tryAgain)
        var refused = snap(.ok, ages: [60])
        refused.signInAgain = true
        #expect(refused.health(at: now) == .signedOut)
    }

    /// Connect: the form, *Whose glucose?* for several people, the pick saved; another person than the one whose
    /// readings this Mac holds is asked first; no one shared says how and keeps no pick.
    @Test func connectSignsInPicksAndAsksBeforeAnotherPerson() async {
        let store = InMemorySettingsStore()
        let m = LibreLinkUpModel(store: store)
        let leo = LluPerson(id: "leo", name: "Leo")
        let ana = LluPerson(id: Self.ana, name: "Ana Example")
        var snap = LibreLinkUpSnapshot()
        m.snapshot = { snap }
        var haptics: [Bool] = []
        m.haptic = { haptics.append($0) }
        #expect(m.mode == .form)
        m.signIn = { _, _, yours in
            #expect(!yours)
            return .connected(people: [ana, leo], picked: nil)
        }
        m.person = { .theirs }
        m.emailDraft = "alex@example.com"
        m.passwordDraft = "pw"
        await m.connect()
        snap = LibreLinkUpSnapshot(email: "alex@example.com", people: [ana, leo])
        #expect(m.passwordDraft.isEmpty && haptics == [true])
        #expect(m.mode == .choose)
        m.choose(leo.id)
        #expect(store.value(MacSettings.libreLinkUpPatient) == "leo")
        #expect(m.mode == .connected)
        // Leo's readings and a treatment on this Mac: picking Ana asks first
        snap.patient = "leo"
        snap.readings = [Reading(t: clock.now, mgdl: 100)]
        m.choose(ana.id)
        #expect(m.confirming == ana && store.value(MacSettings.libreLinkUpPatient) == "leo")
        m.write(ana.id)
        #expect(store.value(MacSettings.libreLinkUpPatient) == Self.ana && m.confirming == nil)
        // a refused password: the reason under the field, a reject tap, nothing saved
        m.signIn = { _, _, _ in .badCredentials }
        m.passwordDraft = "wrong"
        await m.connect()
        #expect(m.result == .badCredentials && haptics.last == false)
        #expect(LibreLinkUpModel.problem(m.result) == "LibreLinkUp refused this email or password")
        // no one shared: the pick goes, Try Again asks without the password
        m.signIn = { _, _, _ in .noOne }
        m.passwordDraft = "pw"
        await m.connect()
        snap = LibreLinkUpSnapshot(email: "alex@example.com")
        #expect(store.value(MacSettings.libreLinkUpPatient).isEmpty && m.mode == .problem)
        // sign out: the pick goes too
        m.write(Self.ana)
        var signedOut = false
        m.signOutSource = { signedOut = true }
        m.signOut()
        #expect(signedOut && store.value(MacSettings.libreLinkUpPatient).isEmpty)
    }

    /// The person picked stopped sharing, others still do: said so (not "no one shares") on Settings, Connect and the
    /// panel, with *Pick Again*: the one remaining is asked first, several show the choice.
    @Test func thePersonPickedStoppedSharingAndPickAgainChoosesAnew() async {
        let store = InMemorySettingsStore()
        let m = LibreLinkUpModel(store: store)
        let leo = LluPerson(id: "leo", name: "Leo")
        let max = LluPerson(id: "max", name: "Max")
        let now = clock.now
        store.set(Self.ana, for: MacSettings.libreLinkUpPatient)
        var snap = LibreLinkUpSnapshot(
            email: "alex@example.com", people: [leo], patient: Self.ana, name: "Ana Example", running: true,
            outcome: .notShared, readings: [Reading(t: now.addingTimeInterval(-3000), mgdl: 100)])
        m.snapshot = { snap }
        m.now = { now }
        #expect(snap.pickedGone)
        let status = LibreLinkUpStatus.of(snap, now: now)
        #expect(status.text == "The person picked stopped sharing glucose with this account.")
        #expect(status.action == .pickAgain)
        #expect(AppModel.libreLinkUpWait(snap, now: now) == .libreLinkUp(.pickedGone))
        #expect(NoDataReason.libreLinkUp(.pickedGone).action == .libreLinkUp)
        #expect(m.mode == .problem)
        // Pick Again, one person left: reading them is asked first (Ana's readings are on this Mac)
        m.recheckSource = { _, saved in
            #expect(saved == Self.ana)
            return .connected(people: [leo], picked: leo.id)
        }
        await m.recheck()
        #expect(m.confirming == leo && store.value(MacSettings.libreLinkUpPatient) == Self.ana)
        m.confirming = nil
        // several left: the pick goes, the choice shows
        snap.people = [leo, max]
        m.recheckSource = { _, _ in .connected(people: [leo, max], picked: nil) }
        await m.recheck()
        snap.patient = ""
        snap.outcome = nil
        #expect(store.value(MacSettings.libreLinkUpPatient).isEmpty && m.mode == .choose)
        // no one shares at all: the words say so, not "the person picked"
        let none = LibreLinkUpSnapshot(
            email: "alex@example.com", patient: Self.ana, running: true, outcome: .notShared)
        #expect(!none.pickedGone && LibreLinkUpStatus.of(none, now: now).action == .recheck)
        #expect(AppModel.libreLinkUpWait(none, now: now) == .libreLinkUp(.notShared))
    }

    @Test func theSourceStepOffersLibreLinkUpOnline() {
        let settings = SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        let no = SetupModel(settings: settings)
        #expect(no.onlineKinds.contains(.libreLinkUp))
        #expect(SourceKind.libreLinkUp.group == .online)
        let spec = SourceSpec(person: .theirs, kind: .libreLinkUp)!
        #expect(SourceChoiceModel.title(spec) == "LibreLinkUp")
        #expect(
            SourceChoiceModel.means(spec)
                == "Their Libre app shares with your LibreLinkUp account · about 1 min late · may stop working any day")
    }
}
