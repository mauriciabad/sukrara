// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Carbon.HIToolbox

/// The system-wide keyboard shortcut of *Read Glucose Aloud*: Control-Shift-Command-G from any app (Carbon's
/// `RegisterEventHotKey`, which needs no Accessibility permission). Not ⌃⌥⌘G: with VoiceOver that is VO-Command-G.
/// Settings › General turns it off (schema `general.readAloudShortcut`), e.g. when another app uses the same keys.
final class GlobalShortcut {
    /// The key and its modifiers as Carbon takes them.
    static let keyCode = UInt32(kVK_ANSI_G)
    static let carbonModifiers = UInt32(controlKey | shiftKey | cmdKey)
    /// The same keys as a menu item shows them.
    static let keyEquivalent = "g"
    static let modifierFlags: NSEvent.ModifierFlags = [.control, .shift, .command]
    /// How Settings writes it.
    static let symbols = "⌃⇧⌘G"

    private let action: () -> Void
    private var hotKey: EventHotKeyRef?
    private var handler: EventHandlerRef?

    init(action: @escaping () -> Void) { self.action = action }

    /// Whether the keys are registered now.
    var isOn: Bool { hotKey != nil }

    /// Registers the keys (`on`) or lets them go.
    func set(on: Bool) {
        if on { register() } else { unregister() }
    }

    /// Carbon calls back on the main thread (the application event target runs on the main run loop).
    fileprivate func fire() { action() }

    private func register() {
        guard hotKey == nil else { return }
        var spec = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
        let me = Unmanaged.passUnretained(self).toOpaque()
        if handler == nil {
            InstallEventHandler(GetApplicationEventTarget(), globalShortcutPressed, 1, &spec, me, &handler)
        }
        let id = EventHotKeyID(signature: OSType(0x534B_5241), id: 1)  // "SKRA"
        let status = RegisterEventHotKey(
            Self.keyCode, Self.carbonModifiers, id, GetApplicationEventTarget(), 0, &hotKey)
        if status != noErr {
            AppLog.app.info("read aloud shortcut not registered: \(status, privacy: .public)")
            hotKey = nil
        }
    }

    private func unregister() {
        if let hotKey { UnregisterEventHotKey(hotKey) }
        hotKey = nil
    }
}

/// The hot key's Carbon handler: hands the press to its `GlobalShortcut` (passed as `data`).
nonisolated private func globalShortcutPressed(
    _ call: EventHandlerCallRef?, _ event: EventRef?, _ data: UnsafeMutableRawPointer?
) -> OSStatus {
    guard let data else { return OSStatus(eventNotHandledErr) }
    // the address only: the shortcut it points to lives on the main thread, where Carbon calls this
    let address = UInt(bitPattern: data)
    MainActor.assumeIsolated {
        guard let pointer = UnsafeRawPointer(bitPattern: address) else { return }
        Unmanaged<GlobalShortcut>.fromOpaque(pointer).takeUnretainedValue().fire()
    }
    return noErr
}
