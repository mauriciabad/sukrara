// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `SourceKind`, `SourceSpec` and `ReadingRank` against contracts/sources (the cases Android's `SourcesContractTest`
/// passes too).
@Suite struct SourcesTests {
    func json(_ path: String) throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url(path))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test func everyMacSourceDeclaresWhatTheContractSays() throws {
        let sources = try #require(try json("sources/sources.json")["sources"] as? [[String: Any]])
        let mac = sources.filter { ($0["platforms"] as? [String] ?? []).contains("mac") }
        #expect(Set(mac.compactMap { $0["id"] as? String }) == Set(SourceKind.allCases.map(\.rawValue)))
        for s in mac {
            let kind = try #require(SourceKind(rawValue: s["id"] as? String ?? ""))
            #expect(s["firstHand"] as? Bool == kind.firstHand)
            #expect(s["offline"] as? Bool == kind.offline)
            #expect(s["group"] as? String == kind.group.rawValue)
            #expect(s["delay_s"] as? Int == kind.delaySeconds)
            #expect(s["backfill"] as? Bool == kind.backfill)
            #expect(Set(s["readings"] as? [String] ?? []) == kind.readings)
            #expect(s["life"] as? String == kind.life.rawValue)
            #expect((s["wear"] as? String) == kind.wear?.id)
        }
        #expect(try json("sources/sources.json")["persons"] as? [String] == Person.allCases.map(\.rawValue))
    }

    /// The Mac doesn't ask whose glucose: each source (and *Settings from*) gets what `macKeying` says, and no Mac
    /// source owns anyone's glucose (it never uploads, shares or reminds).
    @Test func eachSourceGetsTheBehaviourTheMacKeyingSays() throws {
        let keying = try #require(try json("sources/sources.json")["macKeying"] as? [[String: Any]])
        for c in keying {
            let spec = try #require(SourceSpec(kind: SourceKind(rawValue: c["source"] as? String ?? "")))
            #expect(c["owner"] as? Bool == false)
            #expect(c["stream"] as? Bool == spec.stream)
            #expect(
                c["alertRole"] as? String
                    == spec.alertRole(mirrorsWearer: c["settingsFrom"] as? String == "wearer").rawValue)
        }
        #expect(Set(keying.compactMap { $0["source"] as? String }) == Set(SourceKind.allCases.map(\.rawValue)))
    }

    @Test func everyReadingIdRanksAsTheContractSays() throws {
        let dedup = try json("sources/dedup.json")
        #expect(dedup["rank"] as? [String] == ReadingRank.Rank.allCases.map(\.name))
        for (id, rank) in try #require(dedup["readingRank"] as? [String: String]) {
            #expect(ReadingRank.of(id).name == rank, "\(id)")
        }
        #expect(dedup["window_s"] as? Double == ReadingRank.window)
    }

    @Test func theRankRuleKeepsOneReadingPerMeasurement() throws {
        let cases = try #require(try json("sources/dedup.json")["cases"] as? [[String: Any]])
        func readings(_ any: Any?) -> [ReadingRank.Sourced] {
            (any as? [[Any]] ?? []).map {
                ReadingRank.Sourced(
                    t: Date(timeIntervalSince1970: ($0[0] as? Double) ?? 0), mgdl: $0[2] as? Int ?? 0,
                    source: $0[1] as? String ?? "")
            }
        }
        for c in cases {
            let kept = ReadingRank.merge(stored: readings(c["stored"]), incoming: readings(c["incoming"]))
            #expect(kept == readings(c["kept"]), "\(c["name"] ?? "")")
        }
    }

    @Test func anUnansweredMacReadsWhatItFollows() {
        let store = InMemorySettingsStore()
        #expect(SourceSpec.running(in: store, following: false) == nil)
        #expect(SourceSpec.running(in: store, following: true) == .sukrara)
        let juggluco = SourceSpec(kind: .lanJuggluco)
        juggluco.store(in: store)
        #expect(SourceSpec.stored(in: store) == juggluco)
        #expect(SourceSpec.running(in: store, following: true) == juggluco)
    }
}
