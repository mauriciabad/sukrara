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

/// A Nightscout site as the Mac's source (#213 step 1), over a fake site: the card, the alerts, the treatment log, the
/// no-data reasons, setup's first question and Connect, Settings › Source's words.
@Suite struct AppModelNightscoutTests {
    let clock = FakeClock()
    static let site = "https://ns.example.org"
    static let token = "sukrara-0123456789abcdef"

    /// The site: two readings, one AndroidAPS meal bolus; `refuse` = every call 401.
    func http(refuse: Bool = false) -> FakeNightscoutHTTP {
        let now = clock.now.timeIntervalSince1970
        let entries = Data(
            """
            [{"type":"sgv","sgv":131,"date":\((now - 60) * 1000)},{"type":"sgv","sgv":128,"date":\((now - 360) * 1000)}]
            """.utf8)
        let treatments = Data(
            """
            [{"_id":"a1","eventType":"Meal Bolus","insulin":4.5,"carbs":40,"created_at":"\(NightscoutTime.iso(now - 600))"}]
            """.utf8)
        return FakeNightscoutHTTP { r in
            if refuse { return (401, Data(), [:]) }
            if r.path.hasPrefix("/api/v2/authorization/request/") {
                return FakeNightscoutHTTP.json(#"{"token":"jwt","exp":1900000000}"#)
            }
            switch (r.method, r.path) {
            case ("GET", "/api/v1/entries/sgv.json"): return (200, entries, [:])
            case ("GET", "/api/v1/treatments.json"):
                return (200, r.query("find[identifier]") == nil ? treatments : Data("[]".utf8), [:])
            case ("POST", "/api/v1/treatments"): return (200, Data("[]".utf8), [:])
            default: return (404, Data(), [:])
            }
        }
    }

    func model(_ http: FakeNightscoutHTTP, alerts: AlertController? = nil) -> (AppModel, URL) {
        let dir = FileManager.default.temporaryDirectory.appending(path: "ns-app-\(UUID().uuidString)")
        let source = NightscoutSource(
            http: http, store: InMemoryReadingsStore(), state: JSONFileStore(url: dir.appending(path: "state.json")),
            clock: clock, sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        let plan = LaunchPlan(mode: .normal, services: .all, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        return (AppModel(plan: plan, clock: clock, alerts: alerts, nightscout: source), dir)
    }

    func until(_ ok: () -> Bool) async {
        for _ in 0..<500 where !ok() { try? await Task.sleep(for: .milliseconds(10)) }
    }

    @Test func nightscoutFeedsTheCardTheAlertsAndTheTreatmentLog() async {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let (model, dir) = model(http(), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(site: Self.site, secret: Self.token)
        #expect(AddTreatmentAvailability(model) == .available)
        await until { model.readings.last?.mgdl == 131 && model.treatments.count == 2 }
        #expect(model.statusTitle.hasPrefix("131"))
        #expect(model.noDataReason == nil)
        #expect(alerts.nextWake != nil)  // no data is armed on the site's readings
        #expect(!alerts.mirroring)
        #expect(model.marks.count == 2)
        // another source: the site's readings and treatments leave the card at once
        model.sourceKind = .lanJuggluco
        await until { model.readings.isEmpty }
        #expect(model.treatments.isEmpty && model.marks.isEmpty)
        await model.stop()
    }

    /// A site whose newest reading is `readingAgo` old and whose Sensor Start was `sensorAgo` ago.
    func sensorSite(sensorAgo: TimeInterval, readingAgo: TimeInterval) -> FakeNightscoutHTTP {
        let now = clock.now.timeIntervalSince1970
        let entries = Data(#"[{"type":"sgv","sgv":140,"date":\#((now - readingAgo) * 1000)}]"#.utf8)
        let start = Data(
            #"[{"_id":"s1","eventType":"Sensor Start","created_at":"\#(NightscoutTime.iso(now - sensorAgo))"}]"#.utf8)
        return FakeNightscoutHTTP { r in
            if r.path.hasPrefix("/api/v2/authorization/request/") {
                return FakeNightscoutHTTP.json(#"{"token":"jwt","exp":1900000000}"#)
            }
            switch r.path {
            case "/api/v1/entries/sgv.json": return (200, entries, [:])
            case "/api/v1/treatments.json":
                return (200, r.query("find[eventType]") == "Sensor Start" ? start : Data("[]".utf8), [:])
            default: return (404, Data(), [:])
            }
        }
    }

    /// The site's Sensor Start is the card's sensor (warm-up, time left, ended) and the alert rules' sensor window.
    @Test func theSitesSensorStartIsTheCardsSensor() async {
        let (model, dir) = model(sensorSite(sensorAgo: 30 * 60, readingAgo: 40 * 60))
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(site: Self.site, secret: Self.token)
        await until { model.sensor != nil }
        let start = clock.now.timeIntervalSince1970 - 30 * 60
        #expect(model.sensor == SensorInfo(start: start, end: start + 15 * 86_400, warmupEnd: start + 3600))
        #expect(model.cardData.sensor == model.sensor)
        // no reading since the start: warming up, as the card shows it
        let life = NightscoutSensorLife.Life(
            id: "s1", start: Int(start), warmupEnd: Int(start) + 3600, end: Int(start) + 15 * 86_400)
        #expect(
            NightscoutSensorLife.state(
                life, latest: model.readings.last?.t.timeIntervalSince1970, now: clock.now.timeIntervalSince1970)
                == .warming)
        await model.stop()
    }

    /// A sensor that ended a day ago (started 16 days ago): its readings stopped, and no data stays quiet.
    @Test func anEndedSensorKeepsNoDataQuiet() async {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let (model, dir) = model(sensorSite(sensorAgo: 16 * 86_400, readingAgo: 40 * 60), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(site: Self.site, secret: Self.token)
        await until { model.sensor != nil && !model.readings.isEmpty }
        #expect(model.sensor.map { $0.end < clock.now.timeIntervalSince1970 } == true)
        clock.advance(by: 60)
        alerts.tick()
        #expect(alerts.ui.active[.noData] == nil)
        await model.stop()
    }

    /// The same site without a Sensor Start: no data rings for readings 40 min old (the quiet above is the sensor's).
    @Test func withoutASensorNoDataRings() async {
        let alerts = AlertController(
            settings: AlertSettings(), clock: clock, store: MemoryAlertStore(), notifier: RecordingNotifier(),
            wake: RecordingWake())
        let (model, dir) = model(sensorSite(sensorAgo: -3600, readingAgo: 40 * 60), alerts: alerts)
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(site: Self.site, secret: Self.token)
        await until { !model.readings.isEmpty }
        #expect(model.sensor == nil)  // a Sensor Start ahead of now doesn't count
        clock.advance(by: 60)
        alerts.tick()
        #expect(alerts.ui.active[.noData] != nil)
        await model.stop()
    }

    @Test func aRefusedTokenIsTheNoDataReasonWithItsWayOut() async {
        let (model, dir) = model(http(refuse: true))
        defer { try? FileManager.default.removeItem(at: dir) }
        model.run()
        model.sourceKind = .nightscout
        model.nightscoutConfig = NightscoutConfig(site: Self.site, secret: Self.token)
        await until { model.noDataReason == .nightscout(.signedOut) }
        #expect(model.noDataReason == .nightscout(.signedOut))
        #expect(model.noDataReason?.action == .nightscout)
        #expect(model.notMonitoringReason == "Nightscout refused the token")
        await model.stop()
    }

    @Test func eachHealthStateIsAReasonInWords() {
        let now = clock.now
        func snap(_ outcome: SourceHealth.Outcome?, ages: [TimeInterval], online: Bool = true) -> NightscoutSnapshot {
            NightscoutSnapshot(
                outcome: outcome, online: online, host: "ns.example.org",
                readings: ages.map { Reading(t: now.addingTimeInterval(-$0), mgdl: 100) })
        }
        #expect(AppModel.nightscoutWait(snap(nil, ages: []), now: now) == .nightscout(.connecting))
        #expect(AppModel.nightscoutWait(snap(.ok, ages: [60]), now: now) == nil)
        #expect(AppModel.nightscoutWait(snap(.ok, ages: [600]), now: now) == nil)  // late: the card's age says it
        #expect(AppModel.nightscoutWait(snap(.ok, ages: []), now: now) == .nightscout(.noGlucose))
        #expect(
            AppModel.nightscoutWait(snap(.ok, ages: [1200]), now: now)
                == .nightscout(.silent(since: now.timeIntervalSince1970 - 1200)))
        #expect(AppModel.nightscoutWait(snap(.network, ages: [], online: false), now: now) == .nightscout(.offline))
        #expect(
            AppModel.nightscoutWait(snap(.server, ages: []), now: now)
                == .nightscout(.unreachable(host: "ns.example.org")))
        #expect(AppModel.nightscoutWait(snap(.rateLimited, ages: []), now: now) == .nightscout(.rateLimited))
        #expect(
            NoDataText.of(.nightscout(.silent(since: now.timeIntervalSince1970 - 1200)), now: now.timeIntervalSince1970)
                == NoDataText(
                    title: "Nothing new from Nightscout",
                    body: "Its last reading was 20 min ago. Check the app that uploads to it."))
        // the status card says each state with its action
        #expect(NightscoutStatus.of(snap(.ok, ages: [130]), now: now).text == "Live · last reading 2 min ago")
        #expect(NightscoutStatus.of(snap(.ok, ages: [30]), now: now).text == "Live · last reading just now")
        #expect(NightscoutStatus.of(snap(.ok, ages: [600]), now: now).text == "Late · last reading 10 min ago")
        #expect(NightscoutStatus.of(snap(.refused, ages: [60]), now: now).action == .signIn)
        #expect(NightscoutStatus.of(snap(.server, ages: []), now: now).action == .tryAgain)
        #expect(
            NightscoutStatus.of(snap(.server, ages: []), now: now).text
                == "No answer from ns.example.org. Check the address.")
    }

    @Test func connectGoesFromTheFormToTheFirstReading() {
        let now = clock.now
        var s = NightscoutSnapshot(host: "ns.example.org")
        #expect(NightscoutConnect.stage(s, site: "", now: now) == .form)
        #expect(NightscoutConnect.stage(s, site: Self.site, now: now) == .connecting)
        s.outcome = .refused
        guard case .failed(let status) = NightscoutConnect.stage(s, site: Self.site, now: now) else {
            Issue.record("refused: not failed")
            return
        }
        #expect(status.text == "Nightscout refused the token.")
        s.outcome = .ok
        #expect(
            NightscoutConnect.stage(s, site: Self.site, now: now) == .waiting(NightscoutStatus.of(s, now: now).text))
        s.readings = [Reading(t: now.addingTimeInterval(-60), mgdl: 120)]
        #expect(NightscoutConnect.stage(s, site: Self.site, now: now) == .received)
    }

    @Test func theSiteFieldTakesAPastedTokenAndRefusesPlainHttp() {
        let secrets = InMemorySecretStore()
        let store = InMemorySettingsStore()
        let m = NightscoutModel(store: store, secrets: secrets)
        var told: String?
        m.secretChanged = { told = $0 }
        m.siteDraft = "https://NS.example.org/?token=\(Self.token)"
        m.typed(m.siteDraft)
        #expect(m.siteDraft == Self.site && m.secretDraft == Self.token)
        m.connect()
        #expect(store.value(MacSettings.nightscoutURL) == Self.site)
        #expect(NightscoutModel.secret(secrets) == Self.token && told == Self.token)
        #expect(m.secretDraft.isEmpty && m.hasSecret)
        m.siteDraft = "http://ns.example.org"
        m.connect()
        #expect(m.problem == .https && store.value(MacSettings.nightscoutURL) == Self.site)
        // another site while entries haven't reached the old one: asked first
        m.snapshot = { NightscoutSnapshot(host: "ns.example.org", unsent: 2) }
        m.siteDraft = "https://other.example.org"
        m.connect()
        #expect(m.confirmingSite == "https://other.example.org" && store.value(MacSettings.nightscoutURL) == Self.site)
        m.commit("https://other.example.org")
        #expect(store.value(MacSettings.nightscoutURL) == "https://other.example.org")
    }

    /// docs/44 "The journey": the Mac asks *Do you have Sukrara on your phone?* first; yes follows it (no source
    /// step), no reads it online (Whose glucose → Source without another Sukrara → Connect). Back never changes
    /// anything and steps over the source step when the phone is followed.
    @Test func theFirstQuestionChoosesFollowOrOnline() {
        let settings = SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        let yes = SetupModel(settings: settings)
        yes.complete(.welcome)
        #expect(yes.flow.current == .phone)
        yes.pickedPhone = true
        yes.choosePhone()
        yes.pickedPerson = .theirs
        yes.choosePerson()
        #expect(yes.flow.current == .follow && settings.source.stored == .theirSukrara)
        yes.back()
        #expect(yes.flow.current == .person)

        let settings2 = SettingsModel(
            store: scratchStore(), follow: FollowModel(status: .notFollowing, actions: nil), version: "t")
        let no = SetupModel(settings: settings2)
        no.complete(.welcome)
        no.pickedPhone = false
        no.choosePhone()
        no.pickedPerson = .yours
        no.choosePerson()
        #expect(no.flow.current == .source && no.pickedKind == .nightscout)
        #expect(!no.onlineKinds.contains(.sukrara))
        no.chooseSource()
        #expect(no.flow.current == .follow && no.runningKind == .nightscout)
        no.back()
        #expect(no.flow.current == .source)
        #expect(settings2.source.stored == SourceSpec(person: .yours, kind: .nightscout))
    }

    @Test func aMacSetUpBeforeTheQuestionIsNotAskedIt() {
        let store = scratchStore()
        SourceSpec(person: .theirs, kind: .sukrara)?.store(in: store)
        SetupFlow(states: [
            .welcome: .done, .person: .done, .source: .done, .follow: .done, .alerts: .done, .notifications: .done,
            .openAtLogin: .done,
        ]).save(to: store)
        let m = SetupModel(
            settings: SettingsModel(
                store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "t"))
        #expect(m.flow.isComplete)
    }

    @Test func pulledEntriesAreReadOnlyAndSaySo() {
        let pulled = Treatment(id: "p", k: .carbs, a: 40, t: 100, u: 1, o: .phone)
        let own = Treatment(id: "o", k: .rapid, a: 2, t: 200, u: 1, o: .mac)
        let m = TreatmentsModel(rows: [], canWrite: true, actions: nil)
        m.pulled = ["p"]
        m.sync([pulled, own], deliveries: ["o": .sent], canWrite: true)
        let rows = Dictionary(uniqueKeysWithValues: m.rows.map { ($0.id, $0) })
        #expect(m.canChange(rows["o"]!) && !m.canChange(rows["p"]!))
        #expect(m.shown(rows["o"]!) == nil)  // nothing to say about an entry that's in the log
    }
}
