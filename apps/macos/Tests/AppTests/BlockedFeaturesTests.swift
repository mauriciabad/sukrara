// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraShare
import SukraraTesting
import Testing

@testable import SukraraApp

/// The features macOS blocks (docs/40): listed only while they cost the person something, each with a way out.
@Suite struct BlockedFeaturesTests {
    let following = LinkState.following(wearer: "Marta", canWrite: false)

    @Test func bluetoothCountsOnlyWhileFollowing() {
        #expect(BlockedFeature.of(link: following, nearby: .unauthorized, login: nil) == [.bluetoothFollowing])
        #expect(BlockedFeature.of(link: .notFollowing, nearby: .unauthorized, login: nil).isEmpty)
        // Bluetooth turned off is the person's choice, not a block; searching or connected is fine
        #expect(BlockedFeature.of(link: following, nearby: .off, login: nil).isEmpty)
        #expect(BlockedFeature.of(link: following, nearby: .searching, login: nil).isEmpty)
        #expect(BlockedFeature.of(link: following, nearby: nil, login: nil).isEmpty)
    }

    @Test func openAtLoginCountsWhileItWaitsForApproval() {
        #expect(BlockedFeature.of(link: .notFollowing, nearby: nil, login: .needsApproval) == [.openAtLogin])
        #expect(BlockedFeature.of(link: .notFollowing, nearby: nil, login: .on).isEmpty)
        #expect(BlockedFeature.of(link: .notFollowing, nearby: nil, login: .off).isEmpty)
        #expect(
            BlockedFeature.of(link: following, nearby: .unauthorized, login: .needsApproval) == [
                .bluetoothFollowing, .openAtLogin,
            ])
    }

    @Test func everyFeatureHasAWayOutBesidesAllow() {
        // a switch (Turn Off) or Not Now: never a warning the person can only obey
        for feature in BlockedFeature.allCases {
            #expect(!feature.title.isEmpty && !feature.text.isEmpty && !feature.allow.isEmpty)
        }
        #expect(BlockedFeature.openAtLogin.canTurnOff)
        #expect(!BlockedFeature.bluetoothFollowing.canTurnOff)
    }

    @Test func notNowHidesOnlyThatFeature() {
        let plan = LaunchPlan(mode: .normal, services: .none, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: FakeClock())
        var snapshot = FollowerSnapshot.empty
        snapshot.link = following
        snapshot.nearby = .unauthorized
        model.apply(snapshot)
        #expect(model.blocked == [.bluetoothFollowing])
        model.notNowBlocked.insert(.bluetoothFollowing)
        #expect(model.blocked.isEmpty)
    }
}
