// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The setting *Trend arrow* (`glucose.trend`, mirrored from the wearer as `graph.trend`): where every arrow, spoken
/// trend and the fast alerts take the trend from (contracts/trend, #204).
public enum TrendSetting: String, CaseIterable, Codable, Sendable {
    /// From the readings, weighted over about 2 minutes.
    case quick
    /// From the readings, weighted over about 3 minutes (the default).
    case balanced
    /// From the readings, weighted over about 5 minutes.
    case smooth
    /// The sensor's own rate.
    case sensor

    /// The weighting's time constant, seconds; nil = the sensor's own rate.
    public var tau: TimeInterval? {
        switch self {
        case .quick: 120
        case .balanced: 180
        case .smooth: 300
        case .sensor: nil
        }
    }

    /// A stored or received value; anything else is the default.
    public init(stored: String?) { self = stored.flatMap(Self.init(rawValue:)) ?? .balanced }
}

/// Where a reading's trend came from.
public enum TrendFrom: String, Sendable {
    case readings, sensor
}

/// A reading's trend (contracts/trend): the rate (mg/dL/min) every arrow and word shows, its text band (-3 … 3, with
/// hysteresis; ±3 only once held), where it came from, and `held`: what the falling / rising fast alerts judge (the
/// rate past their threshold for 5 min, nil when it can't be told).
public struct ReadingTrend: Hashable, Sendable {
    public var rate: Double
    public var band: Int
    public var from: TrendFrom
    public var held: Double?

    public init(rate: Double, band: Int, from: TrendFrom, held: Double?) {
        self.rate = rate
        self.band = band
        self.from = from
        self.held = held
    }
}

/// A reading the #193 quiets judge (`TrendRate.clear`): when and mg/dL.
public protocol TrendSample: Equatable {
    var at: Date { get }
    var mgdl: Double { get }
}

/// THE trend (contracts/trend, docs/34-trend.md, #204): the one place that works out where glucose is heading, for
/// every reading at its own time from the readings at or before it. Android's `TrendRate` is the same, number for
/// number (both run contracts/trend). `annotate` is called at one place (`AppModel`); every arrow, word, alert and
/// output reads the reading's `trend`. The sensor's own rate (`Reading.sensorRate`) is only the fallback.
public enum TrendRate {
    /// The window: the readings of the last 5 τ.
    public static let windowTaus = 5.0
    /// At least two readings ...
    public static let minReadings = 2
    /// ... spanning at least 3 min.
    public static let minSpan: TimeInterval = 180
    /// A longer step between two readings starts over (2.5 readings of a 5-min sensor).
    public static let maxStep: TimeInterval = 750
    /// A text band is left only once the rate is this far past its edge.
    public static let hysteresis = 0.2
    /// The drawn arrow is vertical from here, and doubles (band ±3).
    public static let fullScale = 3.0
    /// The doubled arrow: the rate at least 3 at every reading of the last 2 min.
    public static let doubleHold: TimeInterval = 120
    /// The fast alerts: the rate past their threshold at every reading of the last 5 min.
    public static let alertHold: TimeInterval = 300

    /// `readings` sorted by time (stable), each with the trend of its own time (`Reading.trend`); a reading of the same
    /// time as another: the last one given counts for the computation.
    public static func annotate(_ readings: [Reading], setting: TrendSetting) -> [Reading] {
        guard !readings.isEmpty else { return [] }
        var byTime: [TimeInterval: (v: Double?, s: Double?)] = [:]
        for r in readings {
            let lohi = GlucoseRange.isLo(r.mgdl) || GlucoseRange.isHi(r.mgdl)
            byTime[r.t.timeIntervalSince1970] = (lohi ? nil : Double(r.mgdl), r.sensorRate)
        }
        let ts = byTime.keys.sorted()
        let trends = trends(
            ts: ts, values: ts.map { byTime[$0]!.v }, sensor: ts.map { byTime[$0]!.s }, setting: setting)
        let at = Dictionary(uniqueKeysWithValues: zip(ts, trends))
        let sorted = readings.enumerated().sorted { a, b in
            a.element.t == b.element.t ? a.offset < b.offset : a.element.t < b.element.t
        }
        return sorted.map { e in
            var r = e.element
            r.trend = at[r.t.timeIntervalSince1970] ?? nil
            return r
        }
    }

    /// The trend at each time `ts` (ascending, one per time): `values` mg/dL (nil = LO / HI), `sensor` the sensor's
    /// rate. From the readings (the setting's τ), else the sensor's own rate, else none; From the sensor: its rate only;
    /// a LO / HI: none.
    public static func trends(ts: [TimeInterval], values: [Double?], sensor: [Double?], setting: TrendSetting)
        -> [ReadingTrend?]
    {
        var rates: [(Double, TrendFrom)?] = []
        for i in ts.indices {
            guard values[i] != nil else {
                rates.append(nil)
                continue
            }
            if let tau = setting.tau, let r = fromReadings(ts: ts, values: values, i, tau: tau) {
                rates.append((r, .readings))
            } else if let s = sensor[i] {
                rates.append((s, .sensor))
            } else {
                rates.append(nil)
            }
        }
        let points = rates.map { $0?.0 }
        let bs = bands(ts: ts, rates: points)
        return ts.indices.map { i in
            guard let (r, from) = rates[i], let b = bs[i] else { return nil }
            let h = setting == .sensor ? r : held(ts: ts, rates: points, i)
            return ReadingTrend(rate: r, band: b, from: from, held: h)
        }
    }

    /// The rate at `i` from the readings at or before it, or nil: walk back while readings are less than 5 τ older,
    /// stopping at a LO / HI or a step over 12.5 min; at least two readings spanning at least 3 min.
    static func fromReadings(ts: [TimeInterval], values: [Double?], _ i: Int, tau: TimeInterval) -> Double? {
        guard values[i] != nil else { return nil }
        let t0 = ts[i]
        var j = i - 1
        while j >= 0 {
            if t0 - ts[j] >= windowTaus * tau || values[j] == nil || ts[j + 1] - ts[j] > maxStep { break }
            j -= 1
        }
        let first = j + 1
        guard i - first + 1 >= minReadings, t0 - ts[first] >= minSpan else { return nil }
        return weightedSlope(ts: Array(ts[first...i]), values: values[first...i].map { $0! }, tau: tau)
    }

    /// Least squares weighted `exp(−age / τ)`, mg/dL/min: time in s from the newest reading, sums oldest first,
    /// `Σw·(dt − t̄)·(v − v̄) × 60 / Σw·(dt − t̄)²`, t̄ and v̄ the weighted means (Android sums in the same order).
    public static func weightedSlope(ts: [TimeInterval], values: [Double], tau: TimeInterval) -> Double {
        guard let t0 = ts.last else { return 0 }
        let ws = ts.map { exp(-(t0 - $0) / tau) }
        var sw = 0.0
        var st = 0.0
        var sv = 0.0
        for k in ts.indices {
            sw += ws[k]
            st += ws[k] * (ts[k] - t0)
            sv += ws[k] * values[k]
        }
        let mt = st / sw
        let mv = sv / sw
        var num = 0.0
        var den = 0.0
        for k in ts.indices {
            let dt = (ts[k] - t0) - mt
            num += ws[k] * dt * (values[k] - mv)
            den += ws[k] * dt * dt
        }
        return den == 0 ? 0 : num * 60 / den
    }

    /// Nightscout's direction bands ±1 / 2 / 3 (a boundary belongs to the steeper band): -3 … 3, with nothing before.
    public static func plainBand(_ rate: Double) -> Int {
        let a = abs(rate)
        let s = a >= 3 ? 3 : a >= 2 ? 2 : a >= 1 ? 1 : 0
        return rate >= 0 ? s : -s
    }

    /// The band with hysteresis: the previous band is left only once the rate is `hysteresis` past its edge.
    static func bandH(_ rate: Double, _ prev: Int?) -> Int {
        let b = plainBand(rate)
        guard let prev, b != prev else { return b }
        return b > prev ? max(prev, plainBand(rate - hysteresis)) : min(prev, plainBand(rate + hysteresis))
    }

    /// Indices from `i` back to the first reading at or before `ts[i] − hold`, none more than 12.5 min after the one
    /// before it; nil when the readings don't reach that far.
    static func back(_ ts: [TimeInterval], _ i: Int, _ hold: TimeInterval) -> [Int]? {
        var out = [i]
        var j = i
        while ts[i] - ts[j] < hold {
            if j == 0 || ts[j] - ts[j - 1] > maxStep { return nil }
            j -= 1
            out.append(j)
        }
        return out
    }

    /// The text band at each time (ascending, one per time; nil without a rate). The band before is remembered while
    /// readings follow within 12.5 min with a rate (else it starts fresh: the plain band); a band is left only once the
    /// rate is 0.2 past its edge. Band ±3 (the doubled arrow) is entered when the rate was at least 3 that way at every
    /// reading of the last 2 min (that hold is its way in, instead of the 0.2), and left with the 0.2.
    public static func bands(ts: [TimeInterval], rates: [Double?]) -> [Int?] {
        var out: [Int?] = []
        for i in ts.indices {
            guard let r = rates[i] else {
                out.append(nil)
                continue
            }
            let prev: Int? = i > 0 && ts[i] - ts[i - 1] <= maxStep ? out[i - 1] : nil
            var b = bandH(r, prev)
            if prev == nil || abs(prev!) != 3 || (prev! > 0) != (r > 0) {
                let sign = r > 0 ? 1 : -1
                let doubled =
                    back(ts, i, doubleHold).map { idx in
                        idx.allSatisfy { k in rates[k].map { Double(sign) * $0 >= fullScale } ?? false }
                    } ?? false
                b = doubled ? 3 * sign : max(-2, min(2, b))
            }
            out.append(b)
        }
        return out
    }

    /// What the fast alerts judge at `i`: over the readings back to the first at or before 5 min earlier (none more
    /// than 12.5 min after the one before), all with a rate, the one nearest zero when they agree in sign, 0 when they
    /// don't; nil when they don't reach back 5 min or one has no rate.
    static func held(ts: [TimeInterval], rates: [Double?], _ i: Int) -> Double? {
        guard let idx = back(ts, i, alertHold) else { return nil }
        var rs: [Double] = []
        for k in idx {
            guard let r = rates[k] else { return nil }
            rs.append(r)
        }
        if rs.allSatisfy({ $0 >= 0 }) { return rs.min() }
        if rs.allSatisfy({ $0 <= 0 }) { return rs.max() }
        return 0
    }

    /// What the sensor itself says at `r` (its own rate, mg/dL/min): only for the Trend arrow page's preview, which
    /// shows it beside the trend in use.
    public static func sensorSays(_ r: Reading) -> Double? { r.sensorRate }

    /// The drawn arrow's rotation in degrees: linear, 0° flat, ±90° from ±3 mg/dL/min (y-down: negative = up).
    public static func deg(_ rate: Double) -> Double { -max(-1, min(1, rate / fullScale)) * 90 + 0 }

    // MARK: the #193 quiets' clear move (contracts/alerts/SPEC.md §2 "Clear move in the readings"), unchanged

    /// The window: the readings taken in the 5 minutes up to the newest one.
    public static let clearWindow: TimeInterval = 5 * 60
    /// The newest reading may be this old: a held alert fires at most this long after the readings stop.
    public static let clearMaxAge: TimeInterval = 150
    /// At least this many readings in the window ...
    public static let clearMinReadings = 4
    /// ... spanning at least this long ...
    public static let clearMinSpan: TimeInterval = 3 * 60
    /// ... and none further than this after the one before (one missed reading of a 1-minute sensor, not two).
    public static let clearMaxGap: TimeInterval = 150
    /// A step against the direction this big is still a Libre's reading noise; a bigger one is no trend.
    public static let clearNoise = 1.0
    /// The newest two readings each beyond every reading taken more than this before them: still moving.
    public static let clearMoving: TimeInterval = 90
    /// The least-squares slope must be at least this steep (mg/dL/min).
    public static let clearMinSlope = 0.5

    /// `recent` (ascending by time) with `r` in it, keeping only what a window can use: the readings taken within
    /// `clearWindow` of the newest one; a reading of the same time replaces the one there.
    public static func keep<S: TrendSample>(_ recent: [S], _ r: S) -> [S] {
        let all = (recent.filter { $0.at != r.at } + [r]).sorted { $0.at < $1.at }
        guard let newest = all.last?.at else { return [] }
        return all.filter { newest.timeIntervalSince($0.at) < clearWindow }
    }

    /// The least-squares slope (mg/dL/min) of `readings` (ascending by time, as `keep` leaves them) when they show a
    /// clear move up (`up`) or down at `now`, else nil: the window of the newest reading (at most `clearMaxAge` old at
    /// `now`) has `clearMinReadings` spanning `clearMinSpan` without a gap over `clearMaxGap` or a LO / HI, no step
    /// against the direction over `clearNoise`, its newest two readings each beyond every reading `clearMoving` older,
    /// and a slope of at least `clearMinSlope` that way.
    public static func clear<S: TrendSample>(_ readings: [S], at now: Date, up: Bool) -> Double? {
        guard let newest = readings.last, now.timeIntervalSince(newest.at) <= clearMaxAge else { return nil }
        let w = readings.filter { newest.at.timeIntervalSince($0.at) < clearWindow && $0.at <= newest.at }
        guard w.count >= clearMinReadings, let first = w.first, newest.at.timeIntervalSince(first.at) >= clearMinSpan
        else {
            return nil
        }
        guard !w.contains(where: { GlucoseRange.isLo($0.mgdl) || GlucoseRange.isHi($0.mgdl) }) else { return nil }
        let dir: Double = up ? 1 : -1
        for i in 1..<w.count {
            if w[i].at.timeIntervalSince(w[i - 1].at) > clearMaxGap { return nil }
            if dir * (w[i].mgdl - w[i - 1].mgdl) < -clearNoise { return nil }
        }
        let stalled = w.suffix(2).contains { r in
            w.contains { r.at.timeIntervalSince($0.at) > clearMoving && dir * (r.mgdl - $0.mgdl) <= 0 }
        }
        if stalled { return nil }
        let s = slope(w)
        return dir * s >= clearMinSlope ? s : nil
    }

    /// The least-squares slope of `w` in mg/dL/min: time in seconds from the newest reading, summed oldest first, then
    /// `Σ(dt·dv) × 60 / Σdt²` (Android sums in the same order, so both get the same Double).
    public static func slope<S: TrendSample>(_ w: [S]) -> Double {
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
