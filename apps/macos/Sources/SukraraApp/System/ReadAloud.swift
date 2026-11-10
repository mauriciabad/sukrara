// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AVFoundation
import AppKit

/// *Read Glucose Aloud* (#159): what VoiceOver says for the menu bar value (`MenubarTitle.accessibilityLabel`: THE
/// glucose sentence, then the mute) said on demand, without looking: from the status item's right-click menu, its
/// VoiceOver action and the system-wide keyboard shortcut (`GlobalShortcut`). With VoiceOver on, VoiceOver says it in
/// its own voice (an announcement); otherwise the system voice in Sukrara's language. Asked again while the system
/// voice speaks, it stops (VoiceOver users stop VoiceOver's speech with Control, as always).
final class ReadAloud {
    /// Who says it.
    enum Route: Equatable {
        /// VoiceOver, in the voice and rate the person chose for it.
        case voiceOver
        /// The system voice for the app's language.
        case voice
    }

    static func route(voiceOver: Bool) -> Route { voiceOver ? .voiceOver : .voice }

    /// The voice's language: the one the app's words are in (its localization), so the voice reads them right.
    static func language(_ localizations: [String]) -> String { localizations.first ?? "en" }

    private let synthesizer = AVSpeechSynthesizer()

    /// The system voice is speaking (the next `toggle` stops it).
    var speaking: Bool { synthesizer.isSpeaking }

    /// Says `text`, or stops the system voice when it speaks. `element`: what VoiceOver's announcement comes from.
    func toggle(_ text: String, from element: Any?) {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
            return
        }
        switch Self.route(voiceOver: NSWorkspace.shared.isVoiceOverEnabled) {
        case .voiceOver:
            NSAccessibility.post(
                element: element ?? NSApp as Any, notification: .announcementRequested,
                userInfo: [.announcement: text, .priority: NSAccessibilityPriorityLevel.high.rawValue])
        case .voice:
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = AVSpeechSynthesisVoice(language: Self.language(L10n.bundle.preferredLocalizations))
            synthesizer.speak(utterance)
        }
    }
}
