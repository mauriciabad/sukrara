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
        let f = try file("connections.json")
        let account = try #require(f["account"] as? String)
        for c in try cases("connections.json") {
            let name = c["name"] as? String ?? ""
            let e = try #require(c["expect"] as? [String: Any])
            let people = LibreLinkUp.people(["status": 0, "data": c["data"] ?? []])
            let want = (e["choices"] as? [[String: Any]] ?? []).map {
                LluPerson(id: $0["id"] as? String ?? "", name: $0["name"] as? String ?? "")
            }
            #expect(people == want, "\(name)")
            let got = LibreLinkUp.pick(
                people, accountId: account, yours: c["person"] as? String == "yours", saved: c["saved"] as? String)
            #expect(got.state.rawValue == e["state"] as? String, "\(name)")
            #expect(got.pick == e["pick"] as? String, "\(name)")
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

    @Test func pollWaitsWhatTheContractSays() throws {
        let poll = try file("poll.json")
        #expect(Self.double(poll["every_s"]) == LluPoll.every)
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
