// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Carbon.HIToolbox
import SukraraCore
import Testing

@testable import SukraraApp

/// *Read Glucose Aloud* (#159): who says it, in which language, and where it is offered.
@MainActor @Suite struct ReadAloudTests {
    @Test func voiceOverSaysItInItsOwnVoiceElseTheSystemVoice() {
        #expect(ReadAloud.route(voiceOver: true) == .voiceOver)
        #expect(ReadAloud.route(voiceOver: false) == .voice)
    }

    @Test func theVoiceSpeaksTheAppsLanguage() {
        #expect(ReadAloud.language(["es"]) == "es")
        #expect(ReadAloud.language(["en", "es"]) == "en")
        #expect(ReadAloud.language([]) == "en")
    }

    @Test func theMenuOffersItFirstWithTheShortcutWhileItIsOn() throws {
        let menu = ContextMenu()
        var asked = 0
        menu.readAloud = { asked += 1 }
        var on = true
        menu.shortcutOn = { on }
        let item = try #require(menu.menu.items.first)
        #expect(item.title == L10n.t("Read Glucose Aloud"))
        #expect(item.isEnabled)
        #expect(item.keyEquivalent == "g")
        #expect(item.keyEquivalentModifierMask == [.control, .shift, .command])
        if let action = item.action { _ = (item.target as? NSObject)?.perform(action) }
        #expect(asked == 1)
        on = false
        #expect(menu.menu.items.first?.keyEquivalent == "")
        // without the action (no status item yet) the item is there but disabled
        #expect(ContextMenu().menu.items.first?.isEnabled == false)
    }

    @Test func theShortcutIsControlShiftCommandGNeverVoiceOversKeys() {
        #expect(GlobalShortcut.keyCode == UInt32(kVK_ANSI_G))
        #expect(GlobalShortcut.carbonModifiers == UInt32(controlKey | shiftKey | cmdKey))
        // Control-Option is VoiceOver's modifier: VO-Command-G is "find the next graphic"
        #expect(GlobalShortcut.carbonModifiers & UInt32(optionKey) == 0)
        #expect(!GlobalShortcut.modifierFlags.contains(.option))
    }

    @Test func theShortcutIsOnByDefaultAndStoredUnderTheSchemaKey() {
        #expect(MacSettings.readAloudShortcut.defaultValue == true)
        #expect(MacSettings.readAloudShortcut.key == SettingsSpec.Keys.readAloudShortcut)
        #expect(SettingsSpec.settings.contains { $0.id == "general.readAloudShortcut" && $0.only == "mac" })
    }
}
