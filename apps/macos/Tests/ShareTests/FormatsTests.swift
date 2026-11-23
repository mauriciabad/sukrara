// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import Testing

/// contracts/apps/formats.json, THE shared formats: the version each format the Mac speaks is the registry's, and so
/// is what never moves in it.
struct FormatsTests {
    static var formats: [String: [String: Any]] {
        let list = loadJSON("apps/formats.json")["formats"] as! [[String: Any]]
        return Dictionary(uniqueKeysWithValues: list.map { ($0["format"] as! String, $0) })
    }

    static func format(_ id: String) -> [String: Any] { formats[id]! }

    @Test func theMacSpeaksEveryFormatOfItsOwn() {
        let mine = Self.formats.values.filter { ($0["apps"] as! [String]).contains("mac") }
        #expect(Set(mine.map { $0["format"] as! String }) == ["sharing", "treatment", "invite", "avatar"])
    }

    @Test func theVersionsAreTheRegistrys() {
        #expect(Self.format("sharing")["version"] as? Int == Wire.version)
        #expect(Self.format("treatment")["version"] as? Int == Wire.treatmentVersion)
        #expect(Self.format("invite")["version"] as? Int == Wire.inviteVersion)
        #expect(Self.format("avatar")["version"] as? Int == Avatar.currentVersion)
    }

    @Test func theFrozenPartsAreTheCodes() {
        let sharing = Self.format("sharing")["frozen"] as! [String: Any]
        #expect(
            (sharing["kinds"] as! [Int]).map(UInt16.init)
                == [Wire.kindLive, Wire.kindAlert, Wire.kindData, Wire.kindWrap, Wire.kindToOwner, Wire.kindToFollower])
        let invite = Self.format("invite")["frozen"] as! [String: Any]
        #expect(invite["link"] as? String == Wire.inviteBase)
        #expect(UInt16(invite["kind"] as! Int) == Wire.kindInvite)
        let link = InviteLink.parse(Wire.inviteBase + String(repeating: "A", count: 32))
        #expect(link?.url.count == invite["length"] as? Int)
    }
}
