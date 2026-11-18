// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `SensorLives` against contracts/sources/sensor-life.json (the cases Android's `SensorLivesContractTest` passes).
@Suite struct SensorLivesTests {
    struct Case: Decodable {
        struct Said: Decodable {
            let id: String
            let start: Int
        }
        struct Expect: Decodable {
            let id: String
            let start: Int
            let warmupEnd: Int
            let end: Int
            let from: String
            let state: String
        }
        let name: String
        let source: String
        let person: String
        let said: Said?
        let wearMin: Double
        let manualStart: Int?
        let latest: Int?
        let expect: Expect?
    }

    struct File: Decodable {
        let now: Int
        let cases: [Case]
    }

    static let file: File = {
        let data = try! Data(contentsOf: Contracts.url("sources/sensor-life.json"))
        return try! JSONDecoder().decode(File.self, from: data)
    }()

    /// The source's `life` as sources.json declares it (Android's sources too: the rule is the same on both apps).
    static let lives: [String: SensorLives.Life] = {
        let data = try! Data(contentsOf: Contracts.url("sources/sources.json"))
        let json = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
        let sources = json["sources"] as! [[String: Any]]
        return Dictionary(
            uniqueKeysWithValues: sources.map {
                ($0["id"] as! String, SensorLives.Life(rawValue: $0["life"] as! String)!)
            })
    }()

    @Test(arguments: file.cases.map(\.name))
    func everyCase(_ name: String) throws {
        let c = try #require(Self.file.cases.first { $0.name == name })
        let now = Self.file.now
        let life = try #require(Self.lives[c.source])
        let got = SensorLives.life(
            life, person: try #require(Person(rawValue: c.person)),
            said: c.said.map { SensorLives.Said(id: $0.id, start: now + $0.start) }, wearMin: c.wearMin,
            manualStart: c.manualStart.map { now + $0 }, now: TimeInterval(now))
        guard let e = c.expect else {
            #expect(got == nil)
            return
        }
        let s = try #require(got)
        #expect(s.id == e.id)
        #expect(s.start == now + e.start)
        #expect(s.warmupEnd == now + e.warmupEnd)
        #expect(s.end == now + e.end)
        #expect(s.from.rawValue == e.from)
        let state = SensorLives.state(
            s, latest: c.latest.map { TimeInterval(now + $0) }, now: TimeInterval(now))
        #expect(state.rawValue == e.state)
    }

    @Test func theMacSourcesDeclareTheirLife() {
        for kind in SourceKind.allCases { #expect(Self.lives[kind.rawValue] == kind.life, "\(kind)") }
    }

    @Test func theEarliestStartLeavesTheSensorRunning() {
        let now: TimeInterval = 1_792_000_000
        let earliest = SensorLives.earliestStart(wearMin: 20160, now: now)
        let s = SensorLives.life(
            .maybeStart, person: .yours, said: nil, wearMin: 20160, manualStart: Int(earliest), now: now)
        #expect(s.map { SensorLives.state($0, latest: now - 60, now: now) } == .active)
    }
}
