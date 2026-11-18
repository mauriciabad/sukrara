// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// The Nightscout source's pure parts against contracts/sources (health.json and nightscout/*.json), the cases
/// Android's source passes too.
@Suite struct NightscoutContractTests {
    func json(_ path: String) throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url(path))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func cases(_ path: String, _ key: String = "cases") throws -> [[String: Any]] {
        try #require(try json(path)[key] as? [[String: Any]])
    }

    static func double(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }

    @Test func healthIsTheContractsStateForEveryCase() throws {
        let health = try json("sources/health.json")
        #expect(health["late_after_s"] as? Double == SourceHealth.lateAfter)
        #expect(health["silent_after_s"] as? Double == SourceHealth.silentAfter)
        #expect(health["states"] as? [String] == SourceHealth.State.allCases.map(\.rawValue))
        for c in try cases("sources/health.json") {
            let outcome = (c["outcome"] as? String).flatMap(SourceHealth.Outcome.init(rawValue:))
            #expect((outcome == nil) == (c["outcome"] as? String == nil))
            let state = SourceHealth.state(
                outcome: outcome, online: c["online"] as? Bool ?? true, newestAge: Self.double(c["newestAge_s"]))
            #expect(state.rawValue == c["state"] as? String, "\(c["name"] ?? "")")
        }
    }

    @Test func pollWaitsWhatTheContractSays() throws {
        let poll = try json("sources/nightscout/poll.json")
        #expect(Self.double(poll["min_s"]) == NightscoutPoll.minWait)
        #expect(Self.double(poll["upload_lag_s"]) == NightscoutPoll.uploadLag)
        #expect(Self.double(poll["min_cadence_s"]) == NightscoutPoll.minCadence)
        #expect(Self.double(poll["max_cadence_s"]) == NightscoutPoll.maxCadence)
        #expect(Self.double(poll["overdue_s"]) == NightscoutPoll.overdue)
        #expect(Self.double(poll["late_s"]) == NightscoutPoll.late)
        #expect(Self.double(poll["late_after_s"]) == NightscoutPoll.lateAfter)
        #expect((poll["backoff_s"] as? [NSNumber])?.map(\.doubleValue) == NightscoutPoll.backoff)
        #expect(Self.double(poll["refused_s"]) == NightscoutPoll.refusedWait)
        for c in try cases("sources/nightscout/poll.json") {
            let wait = NightscoutPoll.wait(
                now: Self.double(c["now"]) ?? 0, newest: Self.double(c["newest"]), previous: Self.double(c["previous"]),
                failures: c["failures"] as? Int ?? 0, retryAfter: Self.double(c["retryAfter_s"]),
                refused: c["refused"] as? Bool ?? false)
            #expect(wait == Self.double(c["wait_s"]), "\(c["name"] ?? "")")
        }
    }

    @Test func sitesParseAndCompareAsTheContractSays() throws {
        for c in try cases("sources/nightscout/sites.json", "parse") {
            let site = NightscoutSite.parse(c["input"] as? String ?? "")
            let name = c["input"] as? String ?? ""
            #expect(site.root == c["root"] as? String, "\(name)")
            #expect(site.token == c["token"] as? String, "\(name)")
            #expect(site.problem?.rawValue == c["problem"] as? String, "\(name)")
        }
        let same = try #require(try json("sources/nightscout/sites.json")["same"] as? [[Any]])
        for c in same {
            let a = c[0] as? String ?? "", b = c[1] as? String ?? ""
            #expect(NightscoutSite.same(a, b) == c[2] as? Bool, "\(a) \(b)")
        }
    }

    @Test func entriesBecomeTheContractsReadings() throws {
        for c in try cases("sources/nightscout/entries.json") {
            let got = NightscoutEntries.parse(c["body"] ?? [], device: c["device"] as? String)
            let want = (c["readings"] as? [[Any]] ?? []).map {
                NightscoutEntries.Entry(t: Self.double($0[0]) ?? 0, mgdl: $0[1] as? Int ?? 0, rate: Self.double($0[2]))
            }
            let name = c["name"] as? String ?? ""
            #expect(got.count == want.count, "\(name)")
            for (g, w) in zip(got, want) {
                #expect(g.t == w.t && g.mgdl == w.mgdl, "\(name)")
                #expect((g.rate == nil) == (w.rate == nil), "\(name)")
                if let gr = g.rate, let wr = w.rate { #expect(abs(gr - wr) < 1e-9, "\(name)") }
            }
        }
    }

    @Test func theTreatmentSyncPlansWhatTheContractSays() throws {
        let file = try json("sources/nightscout/treatments.json")
        let window = try #require(file["window"] as? [NSNumber])
        let device = try #require(file["device"] as? String)
        for c in try cases("sources/nightscout/treatments.json") {
            let name = c["name"] as? String ?? ""
            let local = (c["local"] as? [[String: Any]] ?? []).map { l in
                Treatment(
                    id: l["id"] as? String ?? "", k: Treatment.Kind(rawValue: l["k"] as? String ?? "") ?? .note,
                    a: Self.double(l["a"]) ?? 0, t: (l["t"] as? NSNumber)?.int64Value ?? 0, n: l["n"] as? String ?? "",
                    u: (l["u"] as? NSNumber)?.int64Value ?? 0, del: l["del"] as? Bool ?? false, o: .mac)
            }
            let ledgerJSON = c["ledger"] as? [String: Any] ?? [:]
            let ledger = NightscoutTreatments.Ledger(
                pulled: Set(ledgerJSON["pulled"] as? [String] ?? []),
                pushed: (ledgerJSON["pushed"] as? [String: NSNumber] ?? [:]).mapValues(\.int64Value))
            let plan = NightscoutTreatments.plan(
                site: c["site"] as? [[String: Any]] ?? [], local: local, ledger: ledger, device: device,
                window: window[0].int64Value...window[1].int64Value)
            let expect = c["expect"] as? [String: Any] ?? [:]
            let upsert = (expect["upsert"] as? [[String: Any]] ?? []).map { e in
                NightscoutTreatments.Entry(
                    id: e["id"] as? String ?? "", k: Treatment.Kind(rawValue: e["k"] as? String ?? "") ?? .note,
                    a: Self.double(e["a"]) ?? 0, t: (e["t"] as? NSNumber)?.int64Value ?? 0, n: e["n"] as? String ?? "")
            }
            #expect(plan.upsert == upsert, "\(name)")
            #expect(plan.delete == expect["delete"] as? [String], "\(name)")
            #expect(plan.push == expect["push"] as? [String], "\(name)")
            #expect(plan.remove == expect["remove"] as? [String], "\(name)")
        }
    }

    @Test func theSensorsLifeIsTheContractsForEveryCase() throws {
        let file = try json("sources/nightscout/sensor-life.json")
        let now = try #require(Self.double(file["now"]))
        let life = try json("sensor/life.json")
        #expect(Self.double(life["defaultWearMin"]).map { $0 * 60 } == SensorTiming.defaultWear)
        #expect(Self.double(life["defaultWarmupMin"]).map { $0 * 60 } == SensorTiming.defaultWarmup)
        for c in try cases("sources/nightscout/sensor-life.json") {
            let name = c["name"] as? String ?? ""
            let got = NightscoutSensorLife.life(site: c["site"] as? [[String: Any]] ?? [], now: now)
            guard let want = c["expect"] as? [String: Any] else {
                #expect(got == nil, "\(name)")
                continue
            }
            let life = try #require(got, "\(name)")
            #expect(life.id == want["id"] as? String, "\(name)")
            #expect(life.start == want["start"] as? Int, "\(name)")
            #expect(life.warmupEnd == want["warmupEnd"] as? Int, "\(name)")
            #expect(life.end == want["end"] as? Int, "\(name)")
            let state = NightscoutSensorLife.state(life, latest: Self.double(c["latest"]), now: now)
            #expect(state.rawValue == want["state"] as? String, "\(name)")
        }
    }

    /// What this Mac writes reads back as the same entry (the own-loop guard's other half).
    @Test func aDocumentThisMacWritesReadsBackAsTheSameEntry() throws {
        let entries: [Treatment] = [
            Treatment(k: .rapid, a: 2.5, t: 1_792_000_000, n: "lunch", u: 1, o: .mac),
            Treatment(k: .long, a: 12, t: 1_792_000_000, n: "", u: 1, o: .mac),
            Treatment(k: .long, a: 7.5, t: 1_792_000_000, n: "morning", u: 1, o: .mac),
            Treatment(k: .carbs, a: 40, t: 1_792_000_060, u: 1, o: .mac),
            Treatment(k: .note, a: 0, t: 1_792_000_120, n: "run", u: 1, o: .mac),
        ]
        for t in entries {
            let doc = NightscoutTreatments.document(t, device: "Sukrara-1a2b3c", zone: TimeZone(identifier: "UTC")!)
            #expect(doc["identifier"] as? String == t.id)
            #expect(doc["created_at"] as? String == NightscoutTime.iso(TimeInterval(t.t)))
            #expect(doc["device"] as? String == "Sukrara-1a2b3c")
            // a round trip through JSON, as the site stores it
            let back = try #require(
                try JSONSerialization.jsonObject(with: JSONSerialization.data(withJSONObject: doc)) as? [String: Any])
            let read = try #require(NightscoutTreatments.entries(of: back).first)
            #expect(read.k == t.k && read.a == t.a && read.t == t.t && read.n == t.n, "\(t.k)")
        }
    }

    @Test func idsAreNightscoutsUuidV5() {
        #expect(NightscoutTreatments.id("a1", "insulin") == "f82c3205-e406-5214-8b9b-43eb147eab36")
    }
}
