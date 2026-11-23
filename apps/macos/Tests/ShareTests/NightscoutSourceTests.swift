// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Synchronization
import Testing

/// The Nightscout source's loop over a fake site (no network): sign-in, glucose, health and treatments both ways.
@Suite struct NightscoutSourceTests {
    let clock = FakeClock()
    static let site = "https://ns.example.org"
    static let token = "sukrara-0123456789abcdef"

    /// A site with `entries` (newest first), `treatments` documents and what it does with writes.
    final class Site: Sendable {
        /// JSON arrays (Data: what a Mutex can hold across isolation).
        let entriesJSON = Mutex(Data("[]".utf8))
        let treatmentsJSON = Mutex(Data("[]".utf8))

        var entries: [[String: Any]] {
            get { Self.list(entriesJSON.withLock { $0 }) }
            set { let d = json(newValue); entriesJSON.withLock { $0 = d } }
        }
        var treatments: [[String: Any]] {
            get { Self.list(treatmentsJSON.withLock { $0 }) }
            set { let d = json(newValue); treatmentsJSON.withLock { $0 = d } }
        }

        static func list(_ d: Data) -> [[String: Any]] {
            (try? JSONSerialization.jsonObject(with: d)) as? [[String: Any]] ?? []
        }
        /// The status every write gets (200, or 403 for a token that can only read).
        let writes = Mutex(200)
        /// The status entries get (200, 401, 429…).
        let entriesStatus = Mutex(200)
        /// How many times a JWT was handed out.
        let jwts = Mutex(0)

        func json(_ any: Any) -> Data { (try? JSONSerialization.data(withJSONObject: any)) ?? Data() }

        func answer(_ r: FakeNightscoutHTTP.Request) -> FakeNightscoutHTTP.Answer? {
            guard r.url.host() == "ns.example.org" else { return nil }
            let path = r.path
            if path.hasPrefix("/api/v2/authorization/request/") {
                guard path.hasSuffix(NightscoutSourceTests.token) else { return (401, Data(), [:]) }
                let n = jwts.withLock {
                    $0 += 1; return $0
                }
                return (200, json(["token": "jwt-\(n)", "exp": 1_900_000_000]), [:])
            }
            let signed =
                r.headers["Authorization"]?.hasPrefix("Bearer jwt-") == true
                || r.headers["api-secret"] == NightscoutApi.secretHeaders("long-api-secret")["api-secret"]
            guard signed else { return (401, Data(), [:]) }
            switch (r.method, path) {
            case ("GET", "/api/v1/entries/sgv.json"):
                let status = entriesStatus.withLock { $0 }
                guard status == 200 else { return (status, Data(), status == 429 ? ["Retry-After": "600"] : [:]) }
                return (200, entriesJSON.withLock { $0 }, [:])
            case ("GET", "/api/v1/treatments.json"):
                if let event = r.query("find[eventType]") {
                    let newest = treatments.filter { $0["eventType"] as? String == event }.suffix(1)
                    return (200, json(Array(newest)), [:])
                }
                if let id = r.query("find[identifier]") {
                    return (200, json(treatments.filter { $0["identifier"] as? String == id }), [:])
                }
                return (200, treatmentsJSON.withLock { $0 }, [:])
            case ("POST", "/api/v1/treatments"):
                let status = writes.withLock { $0 }
                guard status == 200 else { return (status, Data(), [:]) }
                let docs = (try? JSONSerialization.jsonObject(with: r.body ?? Data())) as? [[String: Any]] ?? []
                var list = treatments
                for var d in docs {
                    d["_id"] = UUID().uuidString
                    list.append(d)
                }
                treatments = list
                return (200, Data("[]".utf8), [:])
            case ("DELETE", _) where path.hasPrefix("/api/v1/treatments/"):
                let status = writes.withLock { $0 }
                guard status == 200 else { return (status, Data(), [:]) }
                let id = String(path.dropFirst("/api/v1/treatments/".count))
                treatments = treatments.filter { $0["_id"] as? String != id }
                return (200, Data(), [:])
            default: return (404, Data(), [:])
            }
        }
    }

    func entry(_ ago: TimeInterval, _ sgv: Int, device: String = "xDrip-DexcomG7") -> [String: Any] {
        ["type": "sgv", "sgv": sgv, "date": (clock.now.timeIntervalSince1970 - ago) * 1000, "device": device]
    }

    struct Rig {
        let source: NightscoutSource
        let http: FakeNightscoutHTTP
        let store: InMemoryReadingsStore
        let dir: URL
    }

    func rig(_ site: Site, offline: Bool = false, kept: Bool = false) -> Rig {
        let http = FakeNightscoutHTTP(unanswered: offline ? URLError(.notConnectedToInternet) : URLError(.timedOut)) {
            offline ? nil : site.answer($0)
        }
        let dir = FileManager.default.temporaryDirectory.appending(path: "ns-test-\(UUID().uuidString)")
        let store = InMemoryReadingsStore()
        let source = NightscoutSource(
            http: http, store: store, state: JSONFileStore(url: dir.appending(path: "state.json")),
            kept: kept ? dir.appending(path: "kept") : nil, clock: clock,
            sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        return Rig(source: source, http: http, store: store, dir: dir)
    }

    /// The first snapshot that satisfies `ok` (nil after 5 s).
    func wait(_ source: NightscoutSource, _ ok: @escaping @Sendable (NightscoutSnapshot) -> Bool) async
        -> NightscoutSnapshot?
    {
        await withTaskGroup(of: NightscoutSnapshot?.self) { group in
            group.addTask {
                for await s in await source.snapshots() where ok(s) { return s }
                return nil
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(5))
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    @Test func aTokenSignsInAndTheLastDayArrives() async throws {
        let site = Site()
        site.entries = [entry(60, 120), entry(360, 118), entry(660, 115)]
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        let s = try #require(await wait(r.source) { $0.readings.count == 3 })
        #expect(s.outcome == .ok && s.monitoring && s.host == "ns.example.org")
        #expect(s.readings.map(\.mgdl) == [115, 118, 120])
        #expect(s.health(at: clock.now) == .live)
        let entries = try #require(r.http.requests.first { $0.path == "/api/v1/entries/sgv.json" })
        #expect(entries.headers["Authorization"] == "Bearer jwt-1")
        // the first read goes back a day
        let since = try #require(entries.query("find[date][$gte]").flatMap(Double.init))
        #expect(abs(since / 1000 - (clock.now.timeIntervalSince1970 - 86_400)) < 1)
        // the token never travels anywhere but the exchange
        #expect(r.http.requests.allSatisfy { $0.url.host() == "ns.example.org" })
        await r.source.set(nil)
    }

    @Test func anApiSecretTravelsHashedAndOwnUploadsAreNeverReadBack() async throws {
        let site = Site()
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        // learn this install's device name from the log it made
        await r.source.set(NightscoutConfig(site: Self.site, secret: "long-api-secret", treatments: false))
        _ = await wait(r.source) { $0.outcome == .ok }
        let device = try #require(
            try JSONDecoder().decode(
                NightscoutState.self, from: Data(contentsOf: r.dir.appending(path: "state.json"))
            ).device)
        site.entries = [entry(60, 150, device: device), entry(120, 149, device: "Sukrara-9f8e7d")]
        await r.source.retry()
        let s = try #require(await wait(r.source) { !$0.readings.isEmpty })
        #expect(s.readings.map(\.mgdl) == [149])
        #expect(
            r.http.requests.last?.headers["api-secret"] == NightscoutApi.secretHeaders("long-api-secret")["api-secret"])
        await r.source.set(nil)
    }

    @Test func aRefusedTokenSignsOutAndARateLimitWaits() async throws {
        let site = Site()
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: "wrong-0123456789abcdee", treatments: false))
        let refused = try #require(await wait(r.source) { $0.outcome != nil })
        #expect(refused.health(at: clock.now) == .signedOut)
        site.entriesStatus.withLock { $0 = 429 }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        let waiting = try #require(await wait(r.source) { $0.outcome == .rateLimited })
        #expect(waiting.health(at: clock.now) == .rateLimited)
        await r.source.set(nil)
    }

    @Test func noNetworkIsOfflineAndANewTokenAsksAgain() async throws {
        let r = rig(Site(), offline: true)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        let s = try #require(await wait(r.source) { $0.outcome != nil })
        #expect(s.health(at: clock.now) == .offline)
        await r.source.set(nil)
    }

    @Test func aRedirectIsNeverFollowed() async throws {
        let http = FakeNightscoutHTTP { _ in (302, Data(), ["Location": "https://elsewhere.example.com/"]) }
        let dir = FileManager.default.temporaryDirectory.appending(path: "ns-test-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let source = NightscoutSource(
            http: http, store: InMemoryReadingsStore(), state: JSONFileStore(url: dir.appending(path: "state.json")),
            clock: clock, sleep: { _ in try await Task.sleep(for: .seconds(3600)) })
        await source.set(NightscoutConfig(site: Self.site, secret: "long-api-secret", treatments: false))
        let s = try #require(await wait(source) { $0.outcome != nil })
        #expect(s.health(at: clock.now) == .unreachable)
        #expect(http.requests.allSatisfy { $0.url.host() == "ns.example.org" })
        await source.set(nil)
    }

    @Test func treatmentsGoBothWaysAndAReadOnlyTokenKeepsThemHere() async throws {
        let site = Site()
        let at = Int(clock.now.timeIntervalSince1970) - 600
        site.entries = [entry(60, 120)]
        site.treatments = [
            [
                "_id": "a1", "eventType": "Meal Bolus", "insulin": 4.5, "carbs": 40, "enteredBy": "AndroidAPS",
                "created_at": NightscoutTime.iso(TimeInterval(at)),
            ]
        ]
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token))
        let pulled = try #require(await wait(r.source) { $0.treatments.count == 2 })
        #expect(pulled.pulled == Set(pulled.treatments.map(\.id)))
        #expect(Set(pulled.treatments.map(\.k)) == [.rapid, .carbs])
        // a site entry can't be changed here
        var site1 = pulled.treatments[0]
        site1.a = 1
        site1.u += 1
        #expect(await r.source.write(site1) == false)
        // this Mac's own goes up with its id, this install's device and Sukrara's words
        let own = Treatment(k: .long, a: 12, t: Int64(at + 60), n: "morning", u: 1, o: .follower)
        #expect(await r.source.write(own))
        _ = await wait(r.source) { $0.unsent == 0 && $0.treatments.count == 3 }
        let doc = try #require(site.treatments.first { $0["identifier"] as? String == own.id })
        #expect(doc["eventType"] as? String == "Note")
        #expect(doc["insulinType"] as? String == "long-acting")
        #expect(doc["notes"] as? String == "Long-acting insulin 12 U · morning")
        #expect((doc["device"] as? String)?.hasPrefix("Sukrara-") == true)
        // read back: recognised, not copied
        await r.source.retry()
        let again = try #require(await wait(r.source) { $0.outcome == .ok })
        #expect(again.treatments.count == 3)
        // a token that can only read: kept here, said so; glucose goes on
        site.writes.withLock { $0 = 403 }
        let note = Treatment(k: .note, a: 0, t: Int64(at + 120), n: "run", u: 1, o: .mac)
        #expect(await r.source.write(note))
        let readOnly = try #require(await wait(r.source) { $0.readOnly })
        #expect(readOnly.unsent == 1 && readOnly.treatments.contains { $0.id == note.id })
        #expect(readOnly.health(at: clock.now) == .live)
        await r.source.set(nil)
    }

    /// Add Treatment while rounds talk to the site: every entry stays (a round used to save the log it had read before
    /// the write, which dropped it: the flaky read-only wait above, CI 2026-11-23).
    @Test func treatmentsWrittenDuringRoundsAreKept() async throws {
        let site = Site()
        site.entries = [entry(60, 120)]
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token))
        _ = try #require(await wait(r.source) { $0.outcome == .ok })
        let at = Int64(clock.now.timeIntervalSince1970) - 600
        let notes = (0..<30).map { Treatment(k: .note, a: 0, t: at + Int64($0), n: "n\($0)", u: 1, o: .mac) }
        await withTaskGroup(of: Void.self) { group in
            for note in notes {
                group.addTask { _ = await r.source.write(note) }
                group.addTask { await r.source.retry() }
            }
        }
        let ids = Set(notes.map(\.id))
        let s = await wait(r.source) { ids.isSubset(of: Set($0.treatments.map(\.id))) }
        #expect(s != nil)
        await r.source.set(nil)
    }

    /// Another site starts a new log and history; the earlier site's are kept apart and come back with it; *Forget*
    /// keeps the forgotten site's apart too.
    @Test func anotherSiteIsKeptApartAndComesBack() async throws {
        let site = Site()
        site.entries = [entry(60, 120)]
        let r = rig(site, kept: true)
        let kept = r.dir.appending(path: "kept")
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        _ = await wait(r.source) { $0.readings.count == 1 }
        let carbs = Treatment(k: .carbs, a: 20, t: 1, u: 1, o: .mac)
        #expect(await r.source.write(carbs))
        site.entries = []
        await r.source.set(NightscoutConfig(site: "https://other.example.org", secret: Self.token, treatments: false))
        let other = try #require(await wait(r.source) { $0.host == "other.example.org" })
        #expect(other.readings.isEmpty && other.treatments.isEmpty)
        #expect(await r.store.load(since: .distantPast).isEmpty)
        let first = KeptHistory.id(.nightscout, "https://ns.example.org")
        #expect(KeptHistory.list(in: kept).map(\.id) == [first])
        #expect(KeptHistory.info(first, in: kept)?.name == "ns.example.org")
        // forgetting is for a site not in use
        await r.source.forget()
        #expect(KeptHistory.list(in: kept).count == 1)
        // the first site again: its readings and log come back
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        let back = try #require(await wait(r.source) { $0.host == "ns.example.org" })
        #expect(back.treatments.map(\.id) == [carbs.id] && back.readings.map(\.mgdl) == [120])
        #expect(KeptHistory.list(in: kept).isEmpty)  // the other site held nothing
        // Forget with another source: kept apart, listed
        await r.source.set(nil)
        await r.source.forget()
        #expect(KeptHistory.list(in: kept).map(\.id) == [first])
        #expect(await r.store.load(since: .distantPast).isEmpty)
    }

    /// The sensor's life comes from the newest Sensor Start / Sensor Change, also with treatments one way only, and is
    /// kept in state.json.
    @Test func theSiteSensorStartGivesTheSensorsLife() async throws {
        let site = Site()
        let start = Int(clock.now.timeIntervalSince1970) - 3 * 86_400
        site.entries = [entry(60, 120)]
        site.treatments = [
            [
                "_id": "s1", "eventType": "Sensor Start", "identifier": "sensor-1",
                "created_at": NightscoutTime.iso(TimeInterval(start)),
            ]
        ]
        let r = rig(site)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        await r.source.set(NightscoutConfig(site: Self.site, secret: Self.token, treatments: false))
        let s = try #require(await wait(r.source) { $0.sensor != nil })
        #expect(s.sensor == WearerSensor(start: start, end: start + 15 * 86_400, warmupEnd: start + 3600))
        #expect(r.http.requests.contains { $0.query("find[eventType]") == "Sensor Start" })
        #expect(r.http.requests.contains { $0.query("find[eventType]") == "Sensor Change" })
        let saved = try JSONDecoder().decode(
            NightscoutState.self, from: Data(contentsOf: r.dir.appending(path: "state.json")))
        #expect(saved.sensor?.id == "sensor-1")
        await r.source.set(nil)
    }

    @Test func aTokenIsToldFromAnApiSecretByItsShape() {
        #expect(NightscoutAuth.of("sukrara-0123456789abcdef") == .token("sukrara-0123456789abcdef"))
        #expect(NightscoutAuth.of(" reader-a1b2c3d4e5f60718 ") == .token("reader-a1b2c3d4e5f60718"))
        #expect(NightscoutAuth.of("my-long-api-secret") == .secret("my-long-api-secret"))
        #expect(NightscoutApi.outcome(status: 200) == nil)
        #expect(NightscoutApi.outcome(status: 302) == .server)
        #expect(NightscoutApi.outcome(status: 403) == .refused)
        #expect(NightscoutApi.retryAfter(["retry-after": "120"], now: .now) == 120)
    }
}
