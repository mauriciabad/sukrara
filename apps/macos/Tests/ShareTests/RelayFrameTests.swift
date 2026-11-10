// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Testing

@testable import SukraraShare

/// A relay frame is decoded once, the event included (no re-encode and second decode per EVENT).
struct RelayFrameTests {
    func frame(_ text: String) -> RelayFrame? { try? JSONDecoder().decode(RelayFrame.self, from: Data(text.utf8)) }

    @Test func eventFramesCarryTheEvent() throws {
        let e = SignedEvent(
            id: "aa", pubkey: "bb", createdAt: 7, kind: 1059, tags: [["p", "cc"]], content: "x", sig: "dd")
        let f = try #require(frame("[\"EVENT\",\"inbox\",\(e.json())]"))
        #expect(f.type == "EVENT" && f.string(1) == "inbox" && f.event == e)
    }

    @Test func otherFramesKeepTheirStringsAndFlags() throws {
        let ok = try #require(frame(#"["OK","abc",true,"duplicate: already have it"]"#))
        #expect(ok.string(1) == "abc" && ok.bool(2) == true && ok.string(3) == "duplicate: already have it")
        let closed = try #require(frame(#"["CLOSED","live","auth-required: sign in"]"#))
        #expect(closed.string(1) == "live" && closed.string(2) == "auth-required: sign in" && closed.event == nil)
        #expect(frame(#"["EOSE","live"]"#)?.string(1) == "live")
        #expect(frame(#"["AUTH","challenge-1"]"#)?.string(1) == "challenge-1")
    }

    /// Anything else in a frame (numbers, null, nested values, a broken event) is skipped, never a crash or a loop.
    @Test func oddElementsAreSkipped() throws {
        let f = try #require(frame(#"["EVENT","inbox",{"id":1},null,[1,[2]],3.5,"tail"]"#))
        #expect(f.event == nil && f.string(1) == "inbox" && f.string(6) == "tail" && f.items.count == 6)
        #expect(frame("not json") == nil && frame("{}") == nil && frame("[]") == nil)
    }
}
