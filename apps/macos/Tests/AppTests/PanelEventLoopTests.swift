// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Testing

@testable import SukraraApp

/// The panel chart's gestures through AppKit's event dispatch (FEATURES.md "Graph and value card", zooming): a pinch,
/// ⌘-scroll and a two-finger scroll, built as the window server delivers them and dispatched by `NSApplication`, reach
/// `ChartGestures`' monitor over the chart's own window and area only. An offscreen window: no status item, nothing
/// activated. The status item's clicks and who gets the focus back are checked by hand (QA.md Q6).
@Suite struct PanelEventLoopTests {
    final class Calls {
        var scroll: [(CGFloat, CGFloat)] = []
        var zoom: [(CGFloat, CGFloat)] = []
    }

    /// A chart area of 300 × 100 pt at (50, 40) in an offscreen window, its gestures recorded; `done` removes it.
    @MainActor
    func chart() -> (window: NSWindow, calls: Calls, done: () -> Void) {
        _ = NSApplication.shared
        let window = NSWindow(
            contentRect: CGRect(x: -20_000, y: -20_000, width: 400, height: 200), styleMask: [.borderless],
            backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        let calls = Calls()
        let catcher = ChartGestures.Catcher(frame: CGRect(x: 50, y: 40, width: 300, height: 100))
        catcher.handlers = ({ calls.scroll.append(($0, $1)) }, { calls.zoom.append(($0, $1)) }, { _ in })
        window.contentView?.addSubview(catcher)  // in a window: the monitor is on
        window.orderFrontRegardless()
        return (
            window, calls,
            {
                catcher.removeFromSuperview()  // out of the window: the monitor is off
                window.orderOut(nil)
            }
        )
    }

    /// An event of `window` at `point` (window coordinates) as a CGEvent to turn into a gesture or a scroll: AppKit
    /// fills in the window and the location the way a real event carries them.
    @MainActor
    func base(_ window: NSWindow, at point: CGPoint, command: Bool = false) throws -> CGEvent {
        let e = try #require(
            NSEvent.mouseEvent(
                with: .mouseMoved, location: point, modifierFlags: command ? .command : [], timestamp: 0,
                windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 0, pressure: 0))
        return try #require(e.cgEvent)
    }

    /// A trackpad pinch (`kCGSEventGesture`, HID type magnify) by `magnification`: the fields AppKit reads.
    @MainActor
    func pinch(_ magnification: Double, _ window: NSWindow, at point: CGPoint) throws -> NSEvent {
        let cg = try base(window, at: point)
        cg.type = try #require(CGEventType(rawValue: 29))
        cg.setIntegerValueField(try #require(CGEventField(rawValue: 110)), value: 8)
        cg.setDoubleValueField(try #require(CGEventField(rawValue: 113)), value: magnification)
        let e = try #require(NSEvent(cgEvent: cg))
        try #require(e.type == .magnify && e.window === window)
        return e
    }

    /// A two-finger (continuous) scroll by `dx`, `dy` points.
    @MainActor
    func scroll(dx: Int64, dy: Int64, command: Bool = false, _ window: NSWindow, at point: CGPoint) throws -> NSEvent {
        let cg = try base(window, at: point, command: command)
        cg.type = .scrollWheel
        cg.setIntegerValueField(.scrollWheelEventIsContinuous, value: 1)
        cg.setIntegerValueField(.scrollWheelEventPointDeltaAxis1, value: dy)
        cg.setIntegerValueField(.scrollWheelEventPointDeltaAxis2, value: dx)
        let e = try #require(NSEvent(cgEvent: cg))
        try #require(e.type == .scrollWheel && e.window === window && e.hasPreciseScrollingDeltas)
        return e
    }

    /// Dispatches `e` the way the app's run loop does with every event it takes off the queue (`sendEvent`, where the
    /// local monitors run). The queue itself isn't pumped: the test process shares it with the other tests.
    @MainActor
    func deliver(_ e: NSEvent) { NSApp.sendEvent(e) }

    /// One window for every gesture in turn.
    @Test @MainActor func gesturesReachTheChartThroughTheEventLoop() throws {
        let (window, calls, done) = chart()
        defer { done() }
        // a pinch zooms at the pointer
        deliver(try pinch(0.25, window, at: CGPoint(x: 50 + 225, y: 90)))
        #expect(calls.zoom.count == 1)
        #expect(abs((calls.zoom.first?.0 ?? 0) - 1.25) < 1e-6)
        #expect(abs((calls.zoom.first?.1 ?? 0) - 0.75) < 0.01)
        // ⌘-scroll zooms too
        deliver(try scroll(dx: 0, dy: 20, command: true, window, at: CGPoint(x: 50 + 150, y: 90)))
        #expect(calls.zoom.count == 2 && (calls.zoom.last?.0 ?? 0) > 1)
        #expect(calls.scroll.isEmpty)
        // a two-finger sideways scroll moves through time; a mostly vertical swipe doesn't
        deliver(try scroll(dx: 30, dy: 0, window, at: CGPoint(x: 50 + 30, y: 90)))
        #expect(calls.scroll.count == 1 && (calls.scroll.first?.0 ?? 0) > 0)
        #expect(abs((calls.scroll.first?.1 ?? 0) - 0.1) < 0.01)
        deliver(try scroll(dx: 2, dy: 30, window, at: CGPoint(x: 50 + 30, y: 90)))
        #expect(calls.scroll.count == 1)
        // outside the chart's area (the card's buttons, the rows) the gestures do nothing to the chart
        deliver(try pinch(0.25, window, at: CGPoint(x: 10, y: 10)))
        deliver(try scroll(dx: 30, dy: 0, window, at: CGPoint(x: 380, y: 190)))
        #expect(calls.zoom.count == 2 && calls.scroll.count == 1)
    }
}
