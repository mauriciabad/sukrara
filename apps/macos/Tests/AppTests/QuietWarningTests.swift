// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraApp

/// The panel's quiet-alerts warning (schema `alerts.quietHidden`): hidden on purpose for what is down then, back when
/// anything else goes down, forgotten for what comes back up; notifications off is never hidden.
@MainActor @Suite struct QuietWarningTests {
    let sound = NotificationPermission.sound
    let banners = NotificationPermission.banners

    @Test func theRule() {
        #expect(!QuietWarning.shown(down: [], hidden: []))
        #expect(QuietWarning.shown(down: ["sound"], hidden: []))
        #expect(!QuietWarning.shown(down: ["sound"], hidden: ["sound"]))
        // something else went down: it shows again
        #expect(QuietWarning.shown(down: ["sound", "banners"], hidden: ["sound"]))
        // what came back up leaves the hidden list
        #expect(QuietWarning.kept(down: [], hidden: ["sound"]).isEmpty)
        #expect(QuietWarning.kept(down: ["sound"], hidden: ["sound", "banners"]) == ["sound"])
        #expect(QuietWarning.kept(down: ["sound", "banners"], hidden: ["sound"]) == ["sound"])
        #expect(QuietWarning.encode(["sound", "banners"]) == "banners,sound")
        #expect(QuietWarning.decode("banners,sound") == ["sound", "banners"])
        #expect(QuietWarning.decode("").isEmpty)
    }

    @Test func whatMacOSTurnedDown() {
        #expect(NotificationPermission.of(banners: true, sound: true) == .allowed)
        #expect(NotificationPermission.of(banners: true, sound: false) == .quiet([sound]))
        #expect(NotificationPermission.of(banners: false, sound: false) == .quiet([sound, banners]))
        #expect(NotificationPermission.denied.down.isEmpty)
    }

    @Test func hideShowAndTheSystemChangingLater() {
        let plan = LaunchPlan(mode: .normal, services: .none, data: .user(home: URL(fileURLWithPath: "/Users/u")))
        let model = AppModel(plan: plan, clock: FakeClock())
        var stored: Set<String>?
        model.onQuietHidden = { stored = $0 }
        model.notificationPermission = .quiet([sound])
        #expect(model.showsNotificationsWarning)
        model.hideQuietWarning(true)
        #expect(stored == [sound])
        #expect(!model.showsNotificationsWarning)
        // banners go off too: a new reason to warn
        model.notificationPermission = .quiet([sound, banners])
        #expect(model.showsNotificationsWarning)
        model.notificationPermission = .quiet([sound])
        #expect(!model.showsNotificationsWarning)
        // Settings › Alerts' Show Warning
        model.hideQuietWarning(false)
        #expect(stored == [])
        #expect(model.showsNotificationsWarning)
        // notifications off: never hidden
        model.hideQuietWarning(true)
        model.notificationPermission = .denied
        #expect(model.showsNotificationsWarning)
    }

    @Test func theWarningSaysWhatIsOff() {
        #expect(PanelView.notificationsTitle(.quiet([sound])).contains("sounds are off"))
        #expect(PanelView.notificationsTitle(.quiet([banners])).contains("banners are off"))
        #expect(PanelView.notificationsTitle(.quiet([sound, banners])).contains("banners and sounds"))
        #expect(PanelView.notificationsTitle(.denied).contains("Notifications are off"))
    }
}
