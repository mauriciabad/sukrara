// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The device's clock as last seen, to tell a clock set back (the person changed the time) from time passing: what the
/// engine keeps then moves back with it, so a repeat, a Mute or no data stays as far away as it was.
public struct ClockWatch: Codable, Equatable, Sendable {
    /// A backward jump smaller than this (NTP corrections) is ignored.
    public static let tolerance: TimeInterval = 60

    /// The newest time seen (kept across launches: a clock set back while the app was closed is noticed too).
    public var lastNow: Date?
    /// `lastNow` on the monotonic clock; not kept (that clock starts again with the Mac).
    var lastMonotonic: TimeInterval?

    private enum CodingKeys: String, CodingKey { case lastNow }

    public init(lastNow: Date? = nil) { self.lastNow = lastNow }

    /// Notes `now` at `monotonic` (s, nil = unknown) and returns how far the clock was set back (0 = not): exactly when
    /// the monotonic clock says how much time passed since the last call, else as if none had (what was due then comes
    /// at most that long later).
    public mutating func observe(_ now: Date, monotonic: TimeInterval?) -> TimeInterval {
        let passed = lastMonotonic.flatMap { prev in monotonic.flatMap { $0 >= prev ? $0 - prev : nil } }
        if let monotonic { lastMonotonic = monotonic }
        guard let last = lastNow else {
            lastNow = now
            return 0
        }
        let back = last.addingTimeInterval(passed ?? 0).timeIntervalSince(now)
        if passed != nil || back > Self.tolerance || now > last { lastNow = now }
        return back > Self.tolerance ? back : 0
    }
}

extension Date {
    /// This time moved back by `delta` (a clock set back); `muteAllForever` stays.
    func shiftedBack(_ delta: TimeInterval) -> Date { self >= muteAllForever ? self : addingTimeInterval(-delta) }
}
