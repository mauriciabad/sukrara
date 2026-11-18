// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraShare
import Testing

/// contracts/protocol/relays/relay-answers.json: which relay a URL names and what an `OK` says (Android runs it too).
struct RelayAnswersTests {
    let fixture = loadJSON("protocol/relays/relay-answers.json")

    @Test func everyURLNamesItsRelay() {
        for c in fixture["keys"] as! [[String: Any]] {
            let url = c["url"] as! String
            #expect(Relays.key(url) == c["key"] as? String, "\(url)")
        }
    }

    @Test func everyAnswer() {
        for c in fixture["answers"] as! [[String: Any]] {
            let frame = JSONText.encode(c["frame"]!)
            let ok = RelayOk(frame: frame)
            #expect(ok?.eventId == c["id"] as? String, "\(frame)")
            #expect((ok?.kept ?? false) == c["kept"] as? Bool, "\(frame)")
            #expect((ok?.authRequired ?? false) == c["auth"] as? Bool, "\(frame)")
        }
    }
}
