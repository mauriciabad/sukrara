// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// A Nightscout site's glucose entries (`GET /api/v1/entries/sgv.json`, newest first) as readings
/// (contracts/sources/nightscout/entries.json): glucose entries only, time from `date`, `mills` or `dateString`, a
/// numeric or text `sgv` rounded half up, the rate from Nightscout's 5-minute `delta`. Entries this install uploaded
/// (`device` is this install's name, alone or before " · ") are never read back; another Sukrara's are.
public enum NightscoutEntries {
    public struct Entry: Equatable, Sendable {
        /// Epoch seconds.
        public var t: TimeInterval
        public var mgdl: Int
        /// mg/dL per minute, nil without a delta.
        public var rate: Double?

        public init(t: TimeInterval, mgdl: Int, rate: Double?) {
            self.t = t
            self.mgdl = mgdl
            self.rate = rate
        }

        public var reading: Reading { Reading(t: Date(timeIntervalSince1970: t), mgdl: mgdl, sensorRate: rate) }
    }

    /// `body` (the decoded JSON array) → entries oldest first, one per time (the first in the body stays).
    public static func parse(_ body: Any, device: String?) -> [Entry] {
        guard let list = body as? [[String: Any]] else { return [] }
        var seen = Set<TimeInterval>()
        var out: [Entry] = []
        for e in list {
            let type = e["type"] as? String
            guard type == "sgv" || (type == nil && e["sgv"] != nil) else { continue }
            if let device, let d = e["device"] as? String, d == device || d.hasPrefix(device + " · ") { continue }
            guard let t = time(e), let mgdl = number(e["sgv"]).map({ Int($0.rounded(.toNearestOrAwayFromZero)) })
            else { continue }
            guard seen.insert(t).inserted else { continue }
            let rate = (e["delta"] as? NSNumber).flatMap { n -> Double? in
                CFGetTypeID(n) == CFBooleanGetTypeID() ? nil : n.doubleValue / 5
            }
            out.append(Entry(t: t, mgdl: mgdl, rate: rate))
        }
        return out.sorted { $0.t < $1.t }
    }

    /// An entry's or treatment's time in epoch s: `date` (ms), else `mills` (ms), else `dateString` (ISO 8601).
    static func time(_ e: [String: Any]) -> TimeInterval? {
        if let ms = number(e["date"]) { return ms / 1000 }
        if let ms = number(e["mills"]) { return ms / 1000 }
        if let s = e["dateString"] as? String { return NightscoutTime.parse(s) }
        return nil
    }

    /// A JSON number, or a numeric string ("142.6"); nil for anything else (a Bool too).
    static func number(_ v: Any?) -> Double? {
        if let n = v as? NSNumber {
            guard CFGetTypeID(n) != CFBooleanGetTypeID(), n.doubleValue.isFinite else { return nil }
            return n.doubleValue
        }
        if let s = v as? String, let d = Double(s.trimmingCharacters(in: .whitespaces)), d.isFinite { return d }
        return nil
    }
}

/// Nightscout's ISO 8601 times ("2026-10-14T17:46:40.000Z", "+02:00"), with or without milliseconds.
public enum NightscoutTime {
    public static func parse(_ s: String) -> TimeInterval? {
        if let d = try? Date.ISO8601FormatStyle(includingFractionalSeconds: true).parse(s) {
            return d.timeIntervalSince1970
        }
        return (try? Date.ISO8601FormatStyle().parse(s))?.timeIntervalSince1970
    }

    /// "2026-10-14T17:46:40.000Z" (UTC, milliseconds), as Nightscout stores `created_at`.
    public static func iso(_ t: TimeInterval) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        return f.string(from: Date(timeIntervalSince1970: t))
    }
}
