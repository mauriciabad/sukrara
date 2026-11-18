// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `Link` against contracts/sources/link.json: what picking asks, which questions leaving asks, the answers
/// preselected and the fence (the cases Android's `LinkContractTest` passes too). The `after` list is
/// `AppModelLinkTests.aNonLinkSourceUsesNothingFromTheLink`.
@Suite struct LinkContractTests {
    func contract() throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url("sources/link.json"))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func cases(_ key: String) throws -> [[String: Any]] {
        try #require(try contract()[key] as? [[String: Any]])
    }

    @Test func pickingASourceAsksWhatTheContractSays() throws {
        var checked = 0
        for c in try cases("pick") {
            // the sensor is Android's own
            guard let picked = SourceKind(rawValue: c["picked"] as? String ?? "") else { continue }
            let linked = try #require(LinkRules.State(rawValue: c["linked"] as? String ?? ""))
            #expect(
                LinkRules.pickAsks(linked: linked, picked: picked)?.rawValue == c["asks"] as? String, "\(c["name"]!)")
            checked += 1
        }
        #expect(checked == 8)
    }

    @Test func aFollowFoundBehindAnotherSourceIsAskedAboutWhileLive() throws {
        let all = try cases("behind")
        #expect(all.count == 3)
        for c in all {
            let linked = try #require(LinkRules.State(rawValue: c["linked"] as? String ?? ""))
            #expect(LinkRules.behind(linked: linked)?.rawValue == c["asks"] as? String, "\(c["name"]!)")
        }
    }

    @Test func leavingAsksOnlyTheQuestionsWhoseAnswersDiffer() throws {
        var checked = 0
        for c in try cases("leave") where c["personChange"] as? Bool != true {  // Android's Whose glucose
            let from = try #require(SettingsFrom(rawValue: c["settingsFrom"] as? String ?? ""))
            let theirs = try #require(LinkRules.Theirs(rawValue: c["theirs"] as? String ?? ""))
            let got = LinkRules.leave(settingsFrom: from, theirs: theirs, history: c["history"] as? Bool ?? false)
            #expect(got.questions.map(\.rawValue) == c["questions"] as? [String], "\(c["name"]!)")
            #expect(got.result?.rawValue == c["result"] as? String, "\(c["name"]!)")
            checked += 1
        }
        #expect(checked == 6)
    }

    @Test func theAnswersPreselectedKeepWhatThePersonSees() throws {
        let defaults = try #require(try contract()["defaults"] as? [String: String])
        #expect(defaults["settings"] == (LinkRules.keepTheirSettings ? "theirs" : "defaults"))
        #expect(defaults["history"] == (LinkRules.keepHistory ? "keep" : "delete"))
    }

    @Test func theNextSourcesBackfillNeverReplacesAKeptReading() throws {
        func readings(_ any: Any?) -> [ReadingRank.Sourced] {
            (any as? [[Any]] ?? []).map {
                ReadingRank.Sourced(
                    t: Date(timeIntervalSince1970: ($0[0] as? Double) ?? 0), mgdl: $0[2] as? Int ?? 0,
                    source: $0[1] as? String ?? "")
            }
        }
        for c in try cases("fence") {
            let fence = Date(timeIntervalSince1970: c["fence_s"] as? Double ?? 0)
            let kept = LinkRules.merge(stored: readings(c["stored"]), incoming: readings(c["incoming"]), fence: fence)
            #expect(kept == readings(c["kept"]), "\(c["name"]!)")
            // the shown history (`continuing`) keeps the same readings where every stored one is kept (at or before
            // the fence: a kept history ends there)
            let stored = readings(c["stored"])
            guard stored.allSatisfy({ $0.t <= fence }) else { continue }
            func plain(_ list: [ReadingRank.Sourced]) -> [Reading] {
                list.map { Reading(t: $0.t, mgdl: $0.mgdl, sensorRate: nil) }
            }
            let shown = LinkRules.continuing(kept: plain(stored), current: plain(readings(c["incoming"])))
            #expect(shown == plain(readings(c["kept"])), "\(c["name"]!)")
        }
    }
}
