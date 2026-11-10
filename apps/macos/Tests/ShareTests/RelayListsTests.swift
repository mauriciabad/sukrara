// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import Testing

/// contracts/protocol/relays/relay-lists.json: which received relay URLs are kept (Android runs the same file).
struct RelayListsTests {
    let fixture = loadJSON("protocol/relays/relay-lists.json")

    @Test func capMatchesTheContract() {
        #expect(Relays.max == fixture["max"] as? Int)
    }

    @Test func everyURL() {
        for c in fixture["urls"] as! [[String: Any]] {
            let url = c["url"] as! String
            #expect(Relays.isRelay(url) == c["ok"] as? Bool, "\(url)")
        }
    }

    @Test func everyList() {
        for c in fixture["lists"] as! [[String: Any]] {
            #expect(Relays.list(c["in"]) == c["out"] as? [String], "\(c["in"]!)")
        }
    }

    @Test func aRosterKeepsOnlyValidRelays() throws {
        let relays = #"["wss://localhost:7447","ws://nos.lol","wss://10.0.2.2","wss://nos.lol"]"#
        let body = #"{"v":2,"epoch":1,"devices":["\#(String(repeating: "a", count: 64))"],"relays":\#(relays)}"#
        #expect(try RosterPayload.decode(body).relays == ["wss://nos.lol"])
    }
}
