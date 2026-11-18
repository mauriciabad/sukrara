// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import SukraraTesting
import Synchronization
import Testing

/// The LibreLinkUp source's loop over a fake LibreLinkUp (no network, no real account): sign-in across a region, the
/// token kept, whose glucose, the graph, its health, the person's treatment log.
@Suite struct LibreLinkUpSourceTests {
    let clock = FakeClock()
    static let email = "alex@example.com"
    static let password = "synthetic-password"
    static let account = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
    static let ana = "11111111-2222-4333-8444-555555555555"
    static let leo = "66666666-7777-4888-9999-aaaaaaaaaaaa"

    /// LibreLinkUp: one account in region `eu`, whom it follows, each person's glucose.
    final class Server: Sendable {
        let now: @Sendable () -> Date
        /// The people the account follows: (patientId, first name).
        let people = Mutex<[(String, String)]>([])
        /// Overrides: the login's status / body, the graph's status (nil = answer normally).
        let login = Mutex<(Int, String)?>(nil)
        let graphStatus = Mutex<Int?>(nil)
        /// 401 for the next graph calls (a token that stopped working early).
        let refuseGraphs = Mutex(0)
        let logins = Mutex(0)
        /// The token's lifetime from each login.
        let tokenLife = Mutex<TimeInterval>(3600)

        init(now: @escaping @Sendable () -> Date) { self.now = now }

        static func time(_ t: Date) -> String {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.timeZone = TimeZone(identifier: "UTC")
            f.dateFormat = "M/d/yyyy h:mm:ss a"
            return f.string(from: t)
        }

        func answer(_ r: FakeLibreLinkUpHTTP.Request) -> FakeLibreLinkUpHTTP.Answer? {
            let host = r.url.host() ?? ""
            guard r.url.scheme == "https", host == "api.libreview.io" || host == "api-eu.libreview.io" else {
                return nil
            }
            if r.path == "/llu/auth/login" {
                logins.withLock { $0 += 1 }
                if let (code, body) = login.withLock({ $0 }) { return FakeLibreLinkUpHTTP.json(body, status: code) }
                if host == "api.libreview.io" {
                    return FakeLibreLinkUpHTTP.json(#"{"status":0,"data":{"redirect":true,"region":"eu"}}"#)
                }
                let sent = (try? JSONSerialization.jsonObject(with: r.body ?? Data())) as? [String: String]
                guard sent?["email"] == LibreLinkUpSourceTests.email,
                    sent?["password"] == LibreLinkUpSourceTests.password
                else { return FakeLibreLinkUpHTTP.json(#"{"status":2,"error":{"message":"notAuthenticated"}}"#) }
                let n = logins.withLock { $0 }
                let expires = Int(now().timeIntervalSince1970 + tokenLife.withLock { $0 })
                return FakeLibreLinkUpHTTP.json(
                    #"{"status":0,"data":{"user":{"id":"\#(LibreLinkUpSourceTests.account)"},"authTicket":{"token":"tok-\#(n)","expires":\#(expires),"duration":3600000}}}"#
                )
            }
            guard r.headers["Authorization"]?.hasPrefix("Bearer tok-") == true,
                r.headers["Account-Id"] == LibreLinkUp.sha256(LibreLinkUpSourceTests.account)
            else { return (401, Data(), [:]) }
            if r.path == "/llu/connections" {
                let list = people.withLock { $0 }.map {
                    #"{"patientId":"\#($0.0)","firstName":"\#($0.1)","lastName":""}"#
                }
                return FakeLibreLinkUpHTTP.json(#"{"status":0,"data":[\#(list.joined(separator: ","))]}"#)
            }
            if r.path.hasPrefix("/llu/connections/"), r.path.hasSuffix("/graph") {
                if refuseGraphs.withLock({ n in
                    defer { n = max(0, n - 1) }
                    return n > 0
                }) {
                    return (401, Data(), [:])
                }
                if let status = graphStatus.withLock({ $0 }) { return (status, Data("{}".utf8), [:]) }
                let id = r.path.split(separator: "/")[2]
                guard let who = people.withLock({ $0 }).first(where: { $0.0 == id }) else { return (403, Data(), [:]) }
                let t = now()
                // Leo's glucose runs 50 higher: whose readings these are shows in the values
                let base = who.1 == "Leo" ? 150.0 : 100
                let points = [300.0, 600, 900].map { ago in
                    #"{"FactoryTimestamp":"\#(Self.time(t - ago))","ValueInMgPerDl":\#(Int(base + ago / 60))}"#
                }
                let body = #"""
                    {"status":0,"data":{"connection":{"patientId":"\#(id)","firstName":"\#(who.1)","lastName":"Example",
                    "sensor":{"sn":"0D00ABC123","a":\#(Int(t.timeIntervalSince1970 - 86_400))},
                    "glucoseMeasurement":{"FactoryTimestamp":"\#(Self.time(t - 50))","ValueInMgPerDl":\#(Int(base) + 12),"TrendArrow":4}},
                    "graphData":[\#(points.joined(separator: ","))]}}
                    """#
                return FakeLibreLinkUpHTTP.json(body)
            }
            return (404, Data(), [:])
        }
    }

    /// Every wait the loop asked for, in order.
    final class Waits: Sendable {
        let list = Mutex<[Double]>([])
    }

    struct Rig {
        let source: LibreLinkUpSource
        let http: FakeLibreLinkUpHTTP
        let secrets: InMemorySecretStore
        let waits: Waits
        let dir: URL
    }

    func rig(_ server: Server, offline: Bool = false) -> Rig {
        let http = FakeLibreLinkUpHTTP(unanswered: offline ? URLError(.notConnectedToInternet) : URLError(.timedOut)) {
            offline ? nil : server.answer($0)
        }
        let dir = FileManager.default.temporaryDirectory.appending(path: "llu-test-\(UUID().uuidString)")
        let secrets = InMemorySecretStore()
        let waits = Waits()
        let source = LibreLinkUpSource(
            http: http, live: InMemoryReadingsStore(), graph: InMemoryReadingsStore(),
            state: JSONFileStore(url: dir.appending(path: "state.json")), secrets: secrets, clock: clock,
            sleep: { s in
                waits.list.withLock { $0.append(s) }
                try await Task.sleep(for: .seconds(3600))
            })
        return Rig(source: source, http: http, secrets: secrets, waits: waits, dir: dir)
    }

    func server() -> Server {
        let clock = self.clock
        return Server(now: { clock.now })
    }

    /// Whether `ok` holds within 5 s.
    func eventually(_ ok: () -> Bool) async -> Bool {
        for _ in 0..<500 {
            if ok() { return true }
            try? await Task.sleep(for: .milliseconds(10))
        }
        return ok()
    }

    /// The first snapshot that satisfies `ok` (nil after 5 s).
    func wait(_ source: LibreLinkUpSource, _ ok: @escaping @Sendable (LibreLinkUpSnapshot) -> Bool) async
        -> LibreLinkUpSnapshot?
    {
        await withTaskGroup(of: LibreLinkUpSnapshot?.self) { group in
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

    @Test func signsInInItsRegionAndReadsTheLiveReadingAndTheGraphsGaps() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana"), (Self.account, "Alex")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        // yours: the account that follows itself is picked
        let result = await r.source.connect(email: " \(Self.email) ", password: Self.password, yours: true)
        #expect(
            result
                == .connected(
                    people: [.init(id: Self.ana, name: "Ana"), .init(id: Self.account, name: "Alex")],
                    picked: Self.account))
        await r.source.set(LibreLinkUpConfig(patient: Self.account))
        let snap = try #require(await wait(r.source) { $0.monitoring })
        // the live reading with its arrow; the graph's points at 5, 10 and 15 min (none within 2 min of the live one)
        #expect(snap.readings.map(\.mgdl) == [115, 110, 105, 112])
        #expect(snap.readings.last?.sensorRate == 1.5)
        #expect(snap.health(at: clock.now) == .live)
        #expect(snap.email == Self.email && !snap.signInAgain)
        #expect(snap.name == "Alex Example")
        #expect(snap.sensor?.serial == "0D00ABC123")
        // every call: https to LibreView's hosts, the product and version, the account's id hashed
        #expect(
            r.http.requests.allSatisfy { $0.url.scheme == "https" && $0.url.host()?.hasSuffix("libreview.io") == true })
        #expect(
            r.http.requests.allSatisfy { $0.headers["product"] == "llu.android" && $0.headers["version"] == "4.17.0" })
        let graph = try #require(r.http.requests.last { $0.path.hasSuffix("/graph") })
        #expect(graph.url.host() == "api-eu.libreview.io")
        #expect(graph.headers["Account-Id"] == LibreLinkUp.sha256(Self.account))
        // the account (with its password and token) lives only in the Keychain item
        #expect(r.secrets.read(account: MacSettings.libreLinkUpSecretAccount) != nil)
        await r.source.set(nil)
    }

    @Test func theTokenIsKeptUntilItNearsItsEndAndAnEarlyRefusalSignsInOnce() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        #expect(
            await r.source.connect(email: Self.email, password: Self.password, yours: false)
                == .connected(people: [.init(id: Self.ana, name: "Ana")], picked: Self.ana))
        await r.source.set(LibreLinkUpConfig(patient: Self.ana))
        _ = await wait(r.source) { $0.monitoring }
        let first = s.logins.withLock { $0 }  // the global host, then the region's
        #expect(first == 2)
        for _ in 0..<3 {
            await r.source.retry()
            _ = await wait(r.source) { $0.monitoring }
        }
        #expect(s.logins.withLock { $0 } == first)
        // 5 min before it expires: a new sign-in, at the region's host directly
        clock.advance(by: 3600 - 299)
        await r.source.retry()
        #expect(await eventually { s.logins.withLock { $0 } == first + 1 })
        // refused before its time: one new sign-in, then the same call again
        s.refuseGraphs.withLock { $0 = 1 }
        await r.source.retry()
        #expect(await eventually { s.logins.withLock { $0 } == first + 2 })
        #expect(await wait(r.source) { $0.outcome == .ok } != nil)
        await r.source.set(nil)
    }

    @Test func aRefusedPasswordIsNeverRetriedByItself() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        #expect(await r.source.connect(email: Self.email, password: "wrong", yours: false) == .badCredentials)
        #expect(r.secrets.read(account: MacSettings.libreLinkUpSecretAccount) == nil)  // nothing saved
        _ = await r.source.connect(email: Self.email, password: Self.password, yours: false)
        await r.source.set(LibreLinkUpConfig(patient: Self.ana))
        _ = await wait(r.source) { $0.monitoring }
        // the password changed in LibreLinkUp: the token ends, the saved password is refused once and forgotten
        s.login.withLock { $0 = (200, #"{"status":2}"#) }
        clock.advance(by: 3600)
        await r.source.retry()
        let out = try #require(await wait(r.source) { $0.signInAgain })
        #expect(out.health(at: clock.now) == .signedOut)
        #expect(out.email == Self.email)  // kept for the form
        let logins = s.logins.withLock { $0 }
        try await Task.sleep(for: .milliseconds(200))
        #expect(s.logins.withLock { $0 } == logins)
        await r.source.set(nil)
    }

    @Test func termsNoOneSharedAndSeveralPeopleAreTheirOwnAnswers() async throws {
        let s = server()
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: false) == .noOne)
        s.people.withLock { $0 = [(Self.ana, "Ana"), (Self.leo, "Leo")] }
        #expect(
            await r.source.recheck(yours: false, saved: nil)
                == .connected(
                    people: [.init(id: Self.ana, name: "Ana"), .init(id: Self.leo, name: "Leo")], picked: nil))
        // a pick that's still shared is kept
        #expect(
            await r.source.recheck(yours: false, saved: Self.leo)
                == .connected(
                    people: [.init(id: Self.ana, name: "Ana"), .init(id: Self.leo, name: "Leo")], picked: Self.leo))
        s.login.withLock { $0 = (200, #"{"status":4,"data":{"step":{"type":"tou"}}}"#) }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: false) == .terms)
        s.login.withLock { $0 = (429, #"{"status":429,"data":{"data":{"lockout":300}}}"#) }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: false) == .rateLimited)
        s.login.withLock { $0 = (404, "{}") }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: false) == .stopped)
    }

    @Test func noNetworkIsOffline() async throws {
        let r = rig(server(), offline: true)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: true) == .offline)
    }

    @Test func aPersonWhoStoppedSharingIsNotSharedAndARateLimitWaitsTenMinutes() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        _ = await r.source.connect(email: Self.email, password: Self.password, yours: false)
        await r.source.set(LibreLinkUpConfig(patient: Self.leo))
        let gone = try #require(await wait(r.source) { $0.outcome != nil })
        #expect(gone.health(at: clock.now) == .notShared)
        await r.source.set(LibreLinkUpConfig(patient: Self.ana))
        s.graphStatus.withLock { $0 = 429 }
        let waiting = try #require(await wait(r.source) { $0.outcome == .rateLimited })
        #expect(waiting.health(at: clock.now) == .rateLimited)
        #expect(await eventually { r.waits.list.withLock { $0.last } == 600 })
        await r.source.set(nil)
    }

    @Test func aVersionLibreLinkUpAsksForIsSentOnceAndKept() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        s.login.withLock { $0 = (403, #"{"status":920,"data":{"minimumVersion":"4.18.0"}}"#) }
        #expect(await r.source.connect(email: Self.email, password: Self.password, yours: false) == .stopped)
        #expect(r.http.requests.map { $0.headers["version"] } == ["4.17.0", "4.18.0"])
    }

    @Test func anotherPersonStartsAgainAndSigningOutKeepsTheReadings() async throws {
        let s = server()
        s.people.withLock { $0 = [(Self.ana, "Ana"), (Self.leo, "Leo")] }
        let r = rig(s)
        defer { try? FileManager.default.removeItem(at: r.dir) }
        _ = await r.source.connect(email: Self.email, password: Self.password, yours: false)
        await r.source.set(LibreLinkUpConfig(patient: Self.ana))
        _ = await wait(r.source) { $0.monitoring }
        let carbs = Treatment(
            id: UUID().uuidString.lowercased(), k: .carbs, a: 30, t: Int64(clock.now.timeIntervalSince1970), n: "",
            u: Int64(clock.now.timeIntervalSince1970 * 1000), o: .phone)
        #expect(await r.source.write(carbs))
        let logged = try #require(await wait(r.source) { $0.treatments.count == 1 })
        #expect(logged.treatments.first?.o == .mac)
        // sign out: the account goes, Ana's readings and log stay
        await r.source.signOut()
        let out = try #require(await wait(r.source) { $0.email.isEmpty })
        #expect(!out.running && out.treatments.count == 1)
        #expect(r.secrets.read(account: MacSettings.libreLinkUpSecretAccount) == nil)
        #expect(await !r.source.readings(since: .distantPast).isEmpty)
        #expect(await !r.source.write(carbs))  // off: nothing logged
        // Leo: Ana's readings and log are gone (two people's data never mix)
        _ = await r.source.connect(email: Self.email, password: Self.password, yours: false)
        await r.source.set(LibreLinkUpConfig(patient: Self.leo))
        let leo = try #require(await wait(r.source) { $0.patient == Self.leo && $0.monitoring })
        #expect(leo.treatments.isEmpty)
        #expect(leo.readings.allSatisfy { $0.mgdl >= 150 })
        #expect(await r.source.readings(since: .distantPast).allSatisfy { $0.mgdl >= 150 })
        await r.source.set(nil)
    }
}
