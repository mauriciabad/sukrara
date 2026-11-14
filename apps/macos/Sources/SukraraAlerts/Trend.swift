// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// Where glucose is heading, read from the readings themselves (SPEC §2 "Trend from the readings", #193), never from
/// the sensor's own rate: that lags by many minutes (the owner's evening: the values climbed 1–3 mg/dL a minute while
/// the rate still said falling). The one judgement behind the falling-high and the rising-low quiet; Android's `Trend`
/// is the same, number for number (both run contracts/alerts/trend.json).
public enum Trend {
    /// The window: the readings taken in the 5 minutes up to the newest one.
    public static let window: TimeInterval = 5 * 60
    /// The newest reading may be this old: a held alert fires at most this long after the readings stop.
    public static let maxAge: TimeInterval = 150
    /// At least this many readings in the window ...
    public static let minReadings = 4
    /// ... spanning at least this long ...
    public static let minSpan: TimeInterval = 3 * 60
    /// ... and none further than this after the one before (one missed reading of a 1-minute sensor, not two).
    public static let maxGap: TimeInterval = 150
    /// A step against the direction this big is still a Libre's reading noise; a bigger one is no trend.
    public static let noise = 1.0
    /// The newest two readings each beyond every reading taken more than this before them: still moving.
    public static let moving: TimeInterval = 90
    /// The least-squares slope must be at least this steep (mg/dL/min).
    public static let minSlope = 0.5

    /// `recent` (ascending by time) with `r` in it, keeping only what a window can use: the readings taken within
    /// `window` of the newest one; a reading of the same time replaces the one there.
    public static func keep(_ recent: [GlucoseSample], _ r: GlucoseSample) -> [GlucoseSample] {
        let all = (recent.filter { $0.at != r.at } + [r]).sorted { $0.at < $1.at }
        guard let newest = all.last?.at else { return [] }
        return all.filter { newest.timeIntervalSince($0.at) < window }
    }

    /// The least-squares slope (mg/dL/min) of `readings` (ascending by time, as `keep` leaves them) when they show a
    /// clear move up (`up`) or down at `now`, else nil: the window of the newest reading (at most `maxAge` old at
    /// `now`) has `minReadings` spanning `minSpan` without a gap over `maxGap` or a LO / HI, no step against the
    /// direction over `noise`, its newest two readings each beyond every reading `moving` older, and a slope of at least
    /// `minSlope` that way.
    public static func clear(_ readings: [GlucoseSample], at now: Date, up: Bool) -> Double? {
        guard let newest = readings.last, now.timeIntervalSince(newest.at) <= maxAge else { return nil }
        let w = readings.filter { newest.at.timeIntervalSince($0.at) < window && $0.at <= newest.at }
        guard w.count >= minReadings, let first = w.first, newest.at.timeIntervalSince(first.at) >= minSpan else {
            return nil
        }
        guard !w.contains(where: { $0.isLo || $0.isHi }) else { return nil }
        let dir: Double = up ? 1 : -1
        for i in 1..<w.count {
            if w[i].at.timeIntervalSince(w[i - 1].at) > maxGap { return nil }
            if dir * (w[i].mgdl - w[i - 1].mgdl) < -noise { return nil }
        }
        let stalled = w.suffix(2).contains { r in
            w.contains { r.at.timeIntervalSince($0.at) > moving && dir * (r.mgdl - $0.mgdl) <= 0 }
        }
        if stalled { return nil }
        let s = slope(w)
        return dir * s >= minSlope ? s : nil
    }

    /// The least-squares slope of `w` in mg/dL/min: time in seconds from the newest reading, summed oldest first, then
    /// `Σ(dt·dv) × 60 / Σdt²` (Android sums in the same order, so both get the same Double).
    public static func slope(_ w: [GlucoseSample]) -> Double {
        guard let t0 = w.last?.at else { return 0 }
        let n = Double(w.count)
        var st = 0.0
        var sv = 0.0
        for r in w {
            st += r.at.timeIntervalSince(t0)
            sv += r.mgdl
        }
        let mt = st / n
        let mv = sv / n
        var num = 0.0
        var den = 0.0
        for r in w {
            let dt = r.at.timeIntervalSince(t0) - mt
            num += dt * (r.mgdl - mv)
            den += dt * dt
        }
        return den == 0 ? 0 : num * 60 / den
    }
}
