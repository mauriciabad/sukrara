// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

/// contracts/treatments/kind-order.json: the same cases Android's KindOrderTest passes.
@Suite struct KindOrderTests {
    nonisolated(unsafe) static let root: [String: Any] = {
        let data = FileManager.default.contents(atPath: Contracts.url("treatments/kind-order.json").path)!
        return try! JSONSerialization.jsonObject(with: data) as! [String: Any]
    }()

    static func kind(_ s: Any?) -> Treatment.Kind { Treatment.Kind(rawValue: s as! String)! }

    @Test func theConstantsAreTheContracts() {
        #expect((Self.root["usual"] as! [String]).map { Self.kind($0) } == KindOrder.usual)
        #expect(Set(Self.root["carbs_alerts"] as! [String]) == KindOrder.carbsAlerts)
        #expect((Self.root["pen_s"] as! NSNumber).int64Value == KindOrder.penS)
    }

    @Test func everyCase() {
        let now: Int64 = 1_794_823_776
        for c in Self.root["cases"] as! [[String: Any]] {
            let name = c["name"] as! String
            let pen = (c["pen"] as? [String: Any]).map {
                PenReadAt(kind: Self.kind($0["k"]), at: now - ($0["ago_s"] as! NSNumber).int64Value)
            }
            let facts = KindFacts(
                reading: (c["reading"] as? NSNumber)?.intValue, lowBelow: (c["lowBelow"] as! NSNumber).doubleValue,
                ringing: Set(c["ringing"] as! [String]), pen: pen)
            let want = (c["order"] as! [String]).map { Self.kind($0) }
            #expect(KindOrder.of(facts, now: now) == want, "\(name)")
            #expect(KindOrder.focus(facts, now: now) == want.first, "\(name)")
        }
    }
}
