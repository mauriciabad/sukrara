// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// One of LibreView's maintenance windows for the account's region (`start` … `end`); `live`: Abbott marks it in
/// progress.
public struct MaintenanceWindow: Equatable, Sendable, Codable {
    public var start: Date
    public var end: Date
    public var live: Bool

    public init(start: Date, end: Date, live: Bool = false) {
        self.start = start
        self.end = end
        self.live = live
    }
}

/// LibreView's maintenance windows from Abbott's public status page (contracts/sources/librelinkup/status.json; Android
/// `LibreViewStatus`): which windows are the account's region's, the end of the one it is in and the next one ahead.
/// Pure: `LibreLinkUpSource` fetches the page. A 911 from LibreLinkUp is what says maintenance; the page only adds times.
public enum LibreViewStatus {
    public static let url = URL(string: "https://status.freestyle.abbott/api/v2/summary.json")!
    /// For the windows ahead, while LibreLinkUp is a signed-in source.
    public static let every: TimeInterval = 21600
    /// While LibreLinkUp is in maintenance, for its end.
    public static let maintenanceEvery: TimeInterval = 600
    public static let timeout: TimeInterval = 15
    /// The panel says a window ahead from this long before its start (Settings › Source once known).
    public static let warnHome: TimeInterval = 43200
    /// The one quiet notification goes this long before a window's start.
    public static let notifyBefore: TimeInterval = 3600

    /// LibreLinkUp's region (the login's redirect) -> the page's region components (eu2 is the UK's).
    public static let regions: [String: [String]] = [
        "us": ["United States"], "eu": ["Europe, Middle East, Africa"], "eu2": ["UK & Ireland"], "de": ["Germany"],
        "fr": ["France"], "jp": ["Japan"], "ap": ["Other Asia"], "au": ["Australia, New Zealand"],
        "ae": ["United Arab Emirates"], "ca": ["Canada"], "la": ["Latin America"], "ru": ["Russia"], "cn": ["China"],
    ]
    /// The component groups that are LibreLinkUp's and LibreView's servers (others list stray regions).
    public static let groups = ["LibreLinkUp Mobile App", "LibreView Web App"]
    static let liveStatuses: Set<String> = ["in_progress", "verifying"]

    /// A status page time (`2026-11-18T13:00:00.000-08:00`, or `Z`) as a date, nil when it isn't one.
    public static func time(_ v: Any?) -> Date? {
        guard let text = (v as? String)?.trimmingCharacters(in: .whitespaces),
            let m = text.wholeMatch(of: /(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.\d+)?(Z|[+-]\d{2}:\d{2})/),
            let y = Int(m.1), let mo = Int(m.2), let d = Int(m.3), let h = Int(m.4), let mi = Int(m.5),
            let sec = Int(m.6)
        else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let parts = DateComponents(year: y, month: mo, day: d, hour: h, minute: mi, second: sec)
        guard (1...12).contains(mo), (1...31).contains(d), h < 24, mi < 60, sec < 60,
            let date = calendar.date(from: parts), calendar.component(.day, from: date) == d
        else { return nil }
        var t = date.timeIntervalSince1970
        let tz = String(m.8)
        if tz != "Z" {
            let sign: Double = tz.hasPrefix("+") ? 1 : -1
            let hh = Double(tz.dropFirst().prefix(2)) ?? 0
            let mm = Double(tz.suffix(2)) ?? 0
            t -= sign * (hh * 3600 + mm * 60)
        }
        return Date(timeIntervalSince1970: t)
    }

    /// The maintenance windows the page (`body`, its whole object) lists for LibreLinkUp's `region` (nil = not known:
    /// none), sorted by start.
    public static func windows(_ body: [String: Any], region: String?) -> [MaintenanceWindow] {
        guard let region, let names = regions[region] else { return [] }
        var groupNames: [String: String] = [:]
        for case let c as [String: Any] in body["components"] as? [Any] ?? [] where c["group"] as? Bool == true {
            if let id = c["id"] as? String, let name = c["name"] as? String { groupNames[id] = name }
        }
        var out: [MaintenanceWindow] = []
        for case let m as [String: Any] in body["scheduled_maintenances"] as? [Any] ?? [] {
            guard let status = m["status"] as? String, status == "scheduled" || liveStatuses.contains(status),
                let start = time(m["scheduled_for"]), let end = time(m["scheduled_until"]), end > start
            else { continue }
            let ours = (m["components"] as? [Any] ?? []).contains { any in
                guard let c = any as? [String: Any], let name = c["name"] as? String, names.contains(name),
                    let group = (c["group_id"] as? String).flatMap({ groupNames[$0] })
                else { return false }
                return groups.contains(group)
            }
            if ours { out.append(MaintenanceWindow(start: start, end: end, live: liveStatuses.contains(status))) }
        }
        return out.sorted { ($0.start, $0.end) < ($1.start, $1.end) }
    }

    /// The end of the window the region is in at `now` (live, or its start has come), the latest of several; nil =
    /// none known (no window, or one past its planned end: no time is made up).
    public static func until(_ windows: [MaintenanceWindow], now: Date) -> Date? {
        windows.filter { now < $0.end && ($0.live || $0.start <= now) }.map(\.end).max()
    }

    /// The next window that hasn't begun (the warn-ahead), nil = none listed.
    public static func upcoming(_ windows: [MaintenanceWindow], now: Date) -> MaintenanceWindow? {
        windows.first { $0.start > now && !$0.live }
    }
}
