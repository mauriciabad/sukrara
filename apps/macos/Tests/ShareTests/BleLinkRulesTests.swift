// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import Testing

/// contracts/protocol/ble/link-rules.json (PROTOCOL-v2 "Bluetooth link", #115): frames within one ATT write, the
/// reassembly idle time, how long the Mac leaves a phone alone, how many HELLO nonces it keeps, and whose phone an INFO
/// comes from. Android runs the same file (`BleLinkRulesTest`, `NearbyLinkRulesTest`); the wearer's long-write queue
/// is Android's only (the Mac is never the GATT server).
struct BleLinkRulesTests {
    let rules = loadJSON("protocol/ble/link-rules.json")

    @Test func everyFrameFitsOneAttWriteOrNotification() {
        for c in rules["frames"] as! [[String: Int]] {
            let frames = BleFraming.frames(msgId: 7, payload: Data(repeating: 0x61, count: 3000), mtu: c["att_mtu"]!)
            #expect(frames.map(\.count).max() == c["max_frame"], "ATT MTU \(c["att_mtu"]!)")
        }
    }

    @Test func reassemblyIdleTimeIsTheLongWriteIdleTime() {
        let longWrite = rules["long_write"] as! [String: Int]
        #expect(BleReassembler.idle == TimeInterval(longWrite["idle_s"]!))
    }

    @Test func theMacLeavesAPhoneAloneForTheSpecsTimesAndKeepsItsLastNonces() {
        let leave = rules["leave_s"] as! [String: Int]
        #expect(FollowerRuntime.nearbyLeaveOther == Double(leave["other_wearer"]!))
        #expect(FollowerRuntime.nearbyLeaveNoPairing == Double(leave["nothing_to_pair"]!))
        #expect(NearbyLink.noncesKept == rules["nonces_kept"] as? Int)
    }

    @Test func whoseInfoItIs() throws {
        for c in rules["owner"] as! [[String: Any]] {
            let name = c["name"] as! String
            let message = try #require(BleMessage.parse(Data(JSONText.encode(["INFO", c["info"]!]).utf8)), "\(name)")
            guard case .info(let info) = message else {
                Issue.record("\(name): not an INFO")
                continue
            }
            let owner = info.owner(
                identity: c["identity"] as? String, sid: c["sid"] as? String, nonces: c["nonces"] as! [String])
            #expect("\(owner)" == c["expect"] as? String, "\(name)")
        }
    }
}
