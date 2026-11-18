// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// THE LibreLinkUp client's rules (`LibreLinkUp`, `LluPoll`) against contracts/sources/librelinkup: login, calls,
/// people, graph, when to ask again. Android's `LibreLinkUpContractTest` passes the same cases.
@Suite struct LibreLinkUpContractTests {
    func file(_ name: String) throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url("sources/librelinkup/\(name)"))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func cases(_ name: String) throws -> [[String: Any]] { try #require(try file(name)["cases"] as? [[String: Any]]) }

    func body(_ c: [String: Any]) throws -> Data { try JSONSerialization.data(withJSONObject: c["body"] ?? [:]) }

    static func double(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }

    @Test func everyLoginAnswerMeansWhatTheContractSays() throws {
        for c in try cases("login.json") {
            let name = c["name"] as? String ?? ""
            let e = try #require(c["expect"] as? [String: Any])
            let got = LibreLinkUp.parseLogin(
                code: c["code"] as? Int ?? 0, body: try body(c), retryAfter: Self.double(c["retryAfter"]))
            switch e["answer"] as? String {
            case "account":
                #expect(
                    got
                        == .account(
                            accountId: e["accountId"] as? String ?? "", token: e["token"] as? String ?? "",
                            expires: Self.double(e["expires"]) ?? -1), "\(name)")
            case "redirect": #expect(got == .redirect(region: e["region"] as? String ?? ""), "\(name)")
            case "badCredentials": #expect(got == .badCredentials, "\(name)")
            case "terms": #expect(got == .terms(step: e["step"] as? String), "\(name)")
            case "rateLimited": #expect(got == .rateLimited(retryAfter: Self.double(e["retryAfter_s"])), "\(name)")
            case "version": #expect(got == .version(minimum: e["minimumVersion"] as? String ?? ""), "\(name)")
            case "retired": #expect(got == .retired, "\(name)")
            default:
                if case .failed = got {} else { Issue.record("\(name): \(got)") }
            }
            // a token never reaches a log line
            #expect(!String(describing: got).contains("synthetic"))
        }
    }

    @Test func everyAuthenticatedAnswerHasTheContractsOutcome() throws {
        for c in try cases("calls.json") {
            let name = c["name"] as? String ?? ""
            let e = try #require(c["expect"] as? [String: Any])
            let got = LibreLinkUp.parseCall(
                code: c["code"] as? Int ?? 0, body: try body(c), retryAfter: Self.double(c["retryAfter"]))
            #expect(got.outcome.rawValue == e["outcome"] as? String, "\(name)")
            if got.outcome == .rateLimited { #expect(got.retryAfter == Self.double(e["retryAfter_s"]), "\(name)") }
            if got.outcome == .version { #expect(got.minimumVersion == e["minimumVersion"] as? String, "\(name)") }
        }
    }

    @Test func whoseGlucoseIsPickedAsTheContractSays() throws {
        for c in try cases("connections.json") {
            let name = c["name"] as? String ?? ""
            let e = try #require(c["expect"] as? [String: Any])
            let people = LibreLinkUp.people(["status": 0, "data": c["data"] ?? []])
            let want = (e["choices"] as? [[String: Any]] ?? []).map {
                LluPerson(id: $0["id"] as? String ?? "", name: $0["name"] as? String ?? "")
            }
            #expect(people == want, "\(name)")
            // the Mac's pick: every connection, the account's own included (it doesn't ask whose glucose)
            let mac = try #require(c["mac"] as? [String: Any])
            let got = LibreLinkUp.pick(people, saved: c["saved"] as? String)
            #expect(got.state.rawValue == mac["state"] as? String, "\(name)")
            #expect(got.pick == mac["pick"] as? String, "\(name)")
        }
    }

    @Test func everyGraphAnswerBringsTheContractsReadingsAndSensor() throws {
        for c in try cases("graph.json") {
            let name = c["name"] as? String ?? ""
            let e = try #require(c["expect"] as? [String: Any])
            let g = LibreLinkUp.graph(c["body"] as? [String: Any] ?? [:])
            let want = (e["readings"] as? [[Any]] ?? []).map { r in
                "\(Self.double(r[0]) ?? 0) \(r[1]) \(r[2]) \(Self.double(r[3]).map { "\($0)" } ?? "nil")"
            }
            let got = g.readings.map { p in
                "\(p.reading.t.timeIntervalSince1970) \(p.reading.mgdl) \(p.source) \(p.reading.sensorRate.map { "\($0)" } ?? "nil")"
            }
            #expect(got == want, "\(name)")
            let sensor = e["sensor"] as? [String: Any]
            #expect(g.sensor?.serial == sensor?["serial"] as? String, "\(name)")
            #expect(g.sensor.map { Double($0.start) } == Self.double(sensor?["start"]), "\(name)")
            #expect(g.name == e["name"] as? String, "\(name)")
        }
    }

    @Test func theSensorsLifeUsesTheDefaults() {
        let s = LluSensor(serial: "0D00ABC123", start: 1_794_744_000).sensor
        #expect(s.warmupEnd - s.start == Int(SensorTiming.defaultWarmup))
        #expect(s.end - s.start == Int(SensorTiming.defaultWear))
    }

    @Test func theHeadersNameTheContractsProductAndVersion() throws {
        let h = try #require(try file("poll.json")["headers"] as? [String: Any])
        #expect(h["product"] as? String == LibreLinkUp.product)
        #expect(h["version"] as? String == LibreLinkUp.version)
    }

    static func date(_ v: Any?) -> Date? { double(v).map { Date(timeIntervalSince1970: $0) } }

    @Test func pollWaitsWhatTheContractSays() throws {
        let poll = try file("poll.json")
        #expect(Self.double(poll["cadence_s"]) == LluPoll.cadence)
        #expect(Self.double(poll["margin_s"]) == LluPoll.margin)
        #expect(Self.double(poll["margin_up_s"]) == LluPoll.marginUp)
        #expect(poll["margin_hold"] as? Int == LluPoll.marginHold)
        #expect(Self.double(poll["margin_max_s"]) == LluPoll.marginMax)
        #expect(Self.double(poll["min_s"]) == LluPoll.minimum)
        #expect((poll["backoff_s"] as? [NSNumber])?.map(\.doubleValue) == LluPoll.backoff)
        #expect(Self.double(poll["rateLimited_min_s"]) == LluPoll.rateLimitedMin)
        #expect(Self.double(poll["renewBefore_s"]) == LluPoll.renewBefore)
        #expect(Self.double(poll["connectionsEvery_s"]) == LluPoll.connectionsEvery)
        for c in try cases("poll.json") {
            let wait = LluPoll.wait(
                failures: c["failures"] as? Int ?? 0, rateLimited: Self.double(c["rateLimited"]),
                refused: c["refused"] as? Bool ?? false)
            #expect(wait == Self.double(c["wait_s"]), "\(c["name"] ?? "")")
        }
        #expect(Self.double(poll["terms_s"]) == LluPoll.terms)
        #expect(Self.double(poll["retired_s"]) == LluPoll.retired)
        // the wait after a round, by its outcome (Android's LluPoll.next passes the same cases)
        for c in try #require(poll["after"] as? [[String: Any]]) {
            let outcome = try (c["outcome"] as? String).map { try #require(SourceHealth.Outcome(rawValue: $0)) }
            let wait = LluPoll.next(
                outcome: outcome, failures: c["failures"] as? Int ?? 0, retryAfter: Self.double(c["retryAfter_s"]),
                now: Self.date(c["now"]) ?? Date(timeIntervalSince1970: 0), newest: Self.date(c["newest"]),
                margin: Self.double(c["margin_s"]) ?? LluPoll.margin)
            #expect(wait == Self.double(c["wait_s"]), "\(c["name"] ?? "")")
        }
    }

    @Test func callsAreTimedToTheNextReading() throws {
        let poll = try file("poll.json")
        for c in try #require(poll["timed"] as? [[String: Any]]) {
            let wait = LluPoll.timed(
                now: try #require(Self.date(c["now"])), newest: Self.date(c["newest"]),
                margin: try #require(Self.double(c["margin_s"])))
            #expect(wait == Self.double(c["wait_s"]), "\(c["name"] ?? "")")
        }
        for c in try #require(poll["margins"] as? [[String: Any]]) {
            let t = LluPoll.timing(
                LluPoll.Timing(margin: try #require(Self.double(c["margin"])), streak: c["streak"] as? Int ?? -1),
                now: try #require(Self.date(c["now"])), before: Self.date(c["before"]), found: Self.date(c["found"]))
            let want = LluPoll.Timing(
                margin: try #require(Self.double(c["margin_s"])), streak: c["streak_after"] as? Int ?? -1)
            #expect(t == want, "\(c["name"] ?? "")")
        }
        // calls in a row, the timing carried
        for q in try #require(poll["sequences"] as? [[String: Any]]) {
            var t = LluPoll.Timing()
            var newest: Date?
            for (i, step) in try #require(q["steps"] as? [[String: Any]]).enumerated() {
                let now = try #require(Self.date(step["now"]))
                let found = Self.date(step["found"])
                t = LluPoll.timing(t, now: now, before: newest, found: found)
                if let found, found > newest ?? .distantPast { newest = found }
                let name = "\(q["name"] ?? "") #\(i)"
                #expect(t.margin == Self.double(step["margin_s"]), "\(name)")
                #expect(
                    LluPoll.timed(now: now, newest: newest, margin: t.margin) == Self.double(step["wait_s"]), "\(name)")
            }
        }
    }

    @Test func theVersionSentIsTheOneAskedForUnlessThisBuildsIsNewer() throws {
        for c in try #require(try file("poll.json")["versions"] as? [[String: Any]]) {
            #expect(LibreLinkUp.send(asked: c["asked"] as? String) == c["send"] as? String, "\(c["name"] ?? "")")
        }
    }

    /// The fast merge of the two ids keeps exactly what THE rank rule keeps.
    @Test func theTwoIdsMergeAsTheRankRule() {
        var rng = SystemRandomNumberGenerator()
        for _ in 0..<50 {
            let base = 1_795_000_000.0
            let live = (0..<40).map { _ in base + Double(Int.random(in: 0..<7200, using: &rng)) }.sorted()
            let graph = stride(from: base, to: base + 7200, by: 300).filter { _ in Bool.random(using: &rng) }
            let l = Array(Set(live)).sorted().map { Reading(t: Date(timeIntervalSince1970: $0), mgdl: 100) }
            let g = graph.map { Reading(t: Date(timeIntervalSince1970: $0), mgdl: 90) }
            let fast = LibreLinkUp.merge(live: l, graph: g).map { "\($0.t.timeIntervalSince1970) \($0.mgdl)" }
            let rule = ReadingRank.merge(
                stored: [],
                incoming: l.map { .init(t: $0.t, mgdl: $0.mgdl, source: LibreLinkUp.liveId) }
                    + g.map { .init(t: $0.t, mgdl: $0.mgdl, source: LibreLinkUp.graphId) }
            ).map { "\($0.t.timeIntervalSince1970) \($0.mgdl)" }
            #expect(fast == rule)
        }
    }
}
