// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The time source. Everything that compares against "now" (staleness, alert timers, snoozes) takes a `TimeSource`, so
/// tests drive time with a fake instead of sleeping.
public protocol TimeSource: Sendable {
    var now: Date { get }
    /// A clock that never jumps, in seconds (nil = none): how much time really passed, also when `now` was set back.
    var monotonic: TimeInterval? { get }
}

extension TimeSource {
    public var monotonic: TimeInterval? { nil }
}

/// Wall-clock time.
public struct SystemClock: TimeSource {
    public init() {}
    public var now: Date { Date() }
    /// `CLOCK_MONOTONIC`: counts on while the Mac sleeps, never moved by a clock change.
    public var monotonic: TimeInterval? { TimeInterval(clock_gettime_nsec_np(CLOCK_MONOTONIC)) / 1e9 }
}
