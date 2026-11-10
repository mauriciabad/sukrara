// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// `Durations` against `contracts/settings/durations.json` (the cases Android's formatter passes too), English and the
/// Spanish card durations (`DurationWords.es`: "45 min", "1 h 4 min", "2 d").
@Suite struct DurationsTests {
    func root() throws -> [String: Any] {
        let data = try Data(contentsOf: Contracts.url("settings/durations.json"))
        return try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func cases(_ key: String, in r: [String: Any]? = nil) throws -> [[Any]] {
        try #require((r ?? root())[key] as? [[Any]], "no \(key) cases")
    }

    static func formatter(_ key: String, _ w: DurationWords) -> (Int) -> String {
        switch key {
        case "text": Durations.text
        case "compact": { Durations.compact($0, w) }
        default: { Durations.whole($0, w) }
        }
    }

    @Test(arguments: ["text", "compact", "whole"])
    func format(_ key: String) throws {
        let f = Self.formatter(key, .en)
        let list = try cases(key)
        #expect(!list.isEmpty)
        for c in list {
            let minutes = try #require(c[0] as? Int)
            #expect(f(minutes) == c[1] as? String, "\(key)(\(minutes))")
        }
    }

    @Test(arguments: ["text", "compact", "whole"])
    func spanish(_ key: String) throws {
        let es = try #require(try root()["es"] as? [String: Any])
        let f = Self.formatter(key, .es)
        let list = try cases(key, in: es)
        #expect(!list.isEmpty)
        for c in list {
            let minutes = try #require(c[0] as? Int)
            #expect(f(minutes) == c[1] as? String, "es \(key)(\(minutes))")
        }
    }

    /// A compact duration of a single unit of 1, for the plural it agrees with ("queda 1 min", "quedan 1 h 5 min").
    @Test func compactIsOneOnlyForASingleUnitOfOne() {
        #expect([1, 60].map(Durations.compactIsOne) == [true, true])
        #expect([0, 2, 65, 120, 400, 2880].map(Durations.compactIsOne) == [false, false, false, false, false, false])
    }

    @Test func parse() throws {
        let list = try cases("parse")
        #expect(!list.isEmpty)
        for c in list {
            let input = try #require(c[0] as? String)
            #expect(Durations.parse(input) == c[1] as? Int, "parse(\"\(input)\")")
        }
    }
}
