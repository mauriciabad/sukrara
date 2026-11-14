// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI

/// Opens the app's windows (AppKit windows hosting SwiftUI: a SwiftUI `Settings` scene can't be opened reliably
/// from an accessory menu bar app). One window per kind, frames remembered under `app.mauri.sukrara.<kind>`; the
/// app shows in the Dock and the app switcher while one is open, so keyboard users can reach it.
public final class AppWindows: NSObject, NSWindowDelegate {
    public enum Kind: String, Sendable { case settings, setup, treatments, addTreatment, report, about }

    private var windows: [Kind: NSWindow] = [:]

    /// `nearMenuBar`: a new window opens under the menu bar (Add Treatment), not where it was: centred under
    /// `anchor` (the status item, in screen coordinates) and kept on its screen, else at the top right.
    /// `sizedByWindow`: the window, not the content, decides the size (Settings: each pane's height, the form scrolls).
    /// `configure` finishes a new window (its toolbar, its style) after its frame is restored.
    public func show(
        _ kind: Kind, title: String, nearMenuBar: Bool = false, anchor: NSRect? = nil, sizedByWindow: Bool = false,
        configure: ((NSWindow) -> Void)? = nil, @ViewBuilder content: () -> some View
    ) {
        if let w = windows[kind] {
            w.makeKeyAndOrderFront(nil)
            NSApp.activate()
            return
        }
        let host = NSHostingController(rootView: content())
        if sizedByWindow { host.sizingOptions = [] }
        let w = NSWindow(contentViewController: host)
        w.title = title
        w.styleMask.insert(.closable)
        w.isReleasedWhenClosed = false
        w.delegate = self
        if nearMenuBar,
            let screen = anchor.flatMap({ a in
                NSScreen.screens.first { $0.frame.contains(NSPoint(x: a.midX, y: a.midY)) }
            })
                ?? NSScreen.main ?? NSScreen.screens.first
        {
            w.setFrameTopLeftPoint(Self.underMenuBar(size: w.frame.size, anchor: anchor, area: screen.visibleFrame))
        } else if kind != .about {  // About opens centred each time, like the standard panel
            w.setFrameAutosaveName("app.mauri.sukrara.\(kind.rawValue)")
        }
        configure?(w)
        windows[kind] = w
        NSApp.setActivationPolicy(.regular)
        w.makeKeyAndOrderFront(nil)
        NSApp.activate()
    }

    /// The top-left point of a window of `size` under the menu bar: centred under `anchor`'s x, 6 pt below the menu
    /// bar, 12 pt in from the screen's edges; without an anchor at the top right.
    static func underMenuBar(size: NSSize, anchor: NSRect?, area: NSRect) -> NSPoint {
        let inset: CGFloat = 12
        let x = anchor.map { $0.midX - size.width / 2 } ?? (area.maxX - size.width - inset)
        let clamped = min(max(x, area.minX + inset), area.maxX - size.width - inset)
        return NSPoint(x: clamped, y: area.maxY - 6)
    }

    public func isOpen(_ kind: Kind) -> Bool { windows[kind] != nil }

    public func close(_ kind: Kind) { windows[kind]?.close() }

    public func windowWillClose(_ notification: Notification) {
        guard let w = notification.object as? NSWindow, let kind = windows.first(where: { $0.value === w })?.key else {
            return
        }
        windows[kind] = nil
        if windows.isEmpty { NSApp.setActivationPolicy(.accessory) }
    }
}
