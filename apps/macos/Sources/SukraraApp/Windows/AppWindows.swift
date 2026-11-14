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

    /// Where a new window opens.
    public enum Placement: Equatable, Sendable {
        /// Where it was last (its frame remembered under `app.mauri.sukrara.<kind>`).
        case remembered
        /// Centred under `anchor` (the status item, in screen coordinates), 6 pt under the menu bar; without an anchor
        /// at the top right. Opened from the panel, it takes the panel's place.
        case underMenuBar(anchor: NSRect?)
        /// Centred on the pointer (screen coordinates). Opened by a click elsewhere (a desktop widget, a toolbar
        /// button), it appears where the person is looking (#181: "I'd expect it near my cursor").
        case atPointer(NSPoint)
    }

    private var windows: [Kind: NSWindow] = [:]

    /// `placement`: where a new window opens (an open one stays where it is). A placed window is sized to its content
    /// before it is placed, and kept whole on its screen (#181: placed while its size was still unknown, Add Treatment
    /// grew past the top of the screen).
    /// `sizedByWindow`: the window, not the content, decides the size (Settings: each pane's height, the form scrolls).
    /// `configure` finishes a new window (its toolbar, its style) after its frame is restored.
    public func show(
        _ kind: Kind, title: String, placement: Placement = .remembered, sizedByWindow: Bool = false,
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
        if placement != .remembered {
            // the content's own size first: a new window starts a few points tall and grows on its first layout
            host.view.layoutSubtreeIfNeeded()
            w.setContentSize(host.view.fittingSize)
            w.setFrameOrigin(
                Self.origin(
                    size: w.frame.size, placement, screens: NSScreen.screens.map(\.visibleFrame),
                    main: NSScreen.main?.visibleFrame))
        } else if kind != .about {  // About opens centred each time, like the standard panel
            w.setFrameAutosaveName("app.mauri.sukrara.\(kind.rawValue)")
        }
        configure?(w)
        windows[kind] = w
        NSApp.setActivationPolicy(.regular)
        w.makeKeyAndOrderFront(nil)
        NSApp.activate()
    }

    /// The bottom-left corner of a window of `size` placed by `placement` on the screen it points at (`screens`: their
    /// visible frames; `main` when it points at none), kept whole and 12 pt in from that screen's sides and bottom.
    static func origin(size: NSSize, _ placement: Placement, screens: [NSRect], main: NSRect?) -> NSPoint {
        let inset: CGFloat = 12
        // the status item sits in the menu bar, above the visible frame: its screen is the one it stands over
        func screen(_ p: NSPoint) -> NSRect? {
            screens.first { $0.contains(p) } ?? screens.first { p.x >= $0.minX && p.x < $0.maxX && p.y >= $0.maxY }
        }
        let area: NSRect, x: CGFloat, top: CGFloat
        switch placement {
        case .remembered:
            return .zero
        case .underMenuBar(let anchor):
            area = anchor.flatMap { screen(NSPoint(x: $0.midX, y: $0.midY)) } ?? main ?? screens.first ?? .zero
            x = anchor.map { $0.midX - size.width / 2 } ?? (area.maxX - size.width - inset)
            top = area.maxY - 6
        case .atPointer(let p):
            area = screen(p) ?? main ?? screens.first ?? .zero
            x = p.x - size.width / 2
            top = min(p.y + size.height / 2, area.maxY - inset)
        }
        return NSPoint(
            x: min(max(x, area.minX + inset), area.maxX - size.width - inset),
            y: max(top - size.height, area.minY + inset))
    }

    public func isOpen(_ kind: Kind) -> Bool { windows[kind] != nil }

    /// The open window of `kind` (tests).
    func window(_ kind: Kind) -> NSWindow? { windows[kind] }

    public func close(_ kind: Kind) { windows[kind]?.close() }

    public func windowWillClose(_ notification: Notification) {
        guard let w = notification.object as? NSWindow, let kind = windows.first(where: { $0.value === w })?.key else {
            return
        }
        windows[kind] = nil
        if windows.isEmpty { NSApp.setActivationPolicy(.accessory) }
    }
}
