// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation

/// One answer of LibreLinkUp's login (contracts/sources/librelinkup/login.json `answer`).
public enum LluLogin: Equatable, Sendable, CustomStringConvertible {
    /// Signed in: the account's id, a token for the calls, and when it expires (epoch s).
    case account(accountId: String, token: String, expires: TimeInterval)
    /// Sign in again at the account's regional host (`api-<region>.libreview.io`).
    case redirect(region: String)
    case badCredentials
    /// New terms of use or a new privacy policy to accept in LibreLinkUp (`tou`, `pp`).
    case terms(step: String?)
    /// Too many attempts: wait at least this long (nil = the source's own minimum).
    case rateLimited(retryAfter: TimeInterval?)
    /// This client's version is refused: the one asked for.
    case version(minimum: String)
    /// The login is gone: Abbott retired the API.
    case retired
    /// HTTP 911 / status 911: LibreView is down for maintenance in the region (docs/45). Nothing about the account
    /// changes; asked again gently.
    case maintenance
    case failed(String)

    /// Never the token.
    public var description: String {
        switch self {
        case .account: "account(***)"
        case .redirect(let region): "redirect(\(region))"
        case .badCredentials: "badCredentials"
        case .terms(let step): "terms(\(step ?? "-"))"
        case .rateLimited(let wait): "rateLimited(\(wait.map { "\($0)" } ?? "-"))"
        case .version(let minimum): "version(\(minimum))"
        case .retired: "retired"
        case .maintenance: "maintenance"
        case .failed(let why): "failed(\(why))"
        }
    }
}

/// An authenticated call's outcome (contracts/sources/librelinkup/calls.json).
public enum LluCallOutcome: String, Sendable, Equatable {
    case ok, expired, rateLimited, terms, version, retired, server, network
    /// HTTP 911 / status 911: LibreView is down for maintenance in the region (docs/45).
    case maintenance
}

/// One authenticated call's answer: its outcome, the body when `ok`.
public struct LluCall: @unchecked Sendable {
    public var outcome: LluCallOutcome
    /// The whole answer object when `ok` (JSONSerialization's).
    public var body: [String: Any]?
    public var retryAfter: TimeInterval?
    public var minimumVersion: String?

    public init(
        _ outcome: LluCallOutcome, body: [String: Any]? = nil, retryAfter: TimeInterval? = nil,
        minimumVersion: String? = nil
    ) {
        self.outcome = outcome
        self.body = body
        self.retryAfter = retryAfter
        self.minimumVersion = minimumVersion
    }
}

/// Someone the account follows (a LibreLinkUp connection): their patient id and name.
public struct LluPerson: Codable, Equatable, Hashable, Sendable {
    public var id: String
    public var name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

/// Whose glucose the account brings (contracts/sources/librelinkup/connections.json).
public struct LluPick: Equatable, Sendable {
    public enum State: String, Sendable { case picked, choose, none, gone }
    public var choices: [LluPerson]
    public var state: State
    public var pick: String?
}

/// A sensor LibreLinkUp says is running: its serial (a new serial = a new sensor) and start (epoch s).
public struct LluSensor: Codable, Equatable, Sendable {
    public var serial: String
    public var start: Int

    public init(serial: String, start: Int) {
        self.serial = serial
        self.start = start
    }

    /// As the card and the alert rules take it: LibreLinkUp doesn't say how long it lasts, so `SensorTiming`'s
    /// defaults apply (contracts/sensor/life.json).
    public var sensor: WearerSensor {
        WearerSensor(
            start: start, end: start + Int(SensorTiming.defaultWear), warmupEnd: start + Int(SensorTiming.defaultWarmup)
        )
    }
}

/// One graph answer: the readings (the live one and the graph's points), the sensor, the person's name.
public struct LluGraph: Equatable, Sendable {
    /// A reading and the id it is stored under (`librelinkup` live, `librelinkup-graph` a graph point).
    public struct Point: Equatable, Sendable {
        public var reading: Reading
        public var source: String
        public var live: Bool { source == LibreLinkUp.liveId }
    }

    public var readings: [Point]
    public var sensor: LluSensor?
    public var name: String
}

/// THE LibreLinkUp client's pure rules (docs/44 step 2, contracts/sources/librelinkup; Android `:core:data`
/// `LibreLinkUp`'s companion): the login's and the calls' answers, the people an account follows, whose glucose to
/// read, and the graph's readings and sensor. Both apps pass the same fixtures; `LibreLinkUpSource` does the I/O.
public enum LibreLinkUp {
    public static let host = "api.libreview.io"
    public static let product = "llu.android"
    /// The LibreLinkUp version every call names (contracts/sources/librelinkup/poll.json `headers`).
    public static let version = "4.17.0"
    public static let loginPath = "/llu/auth/login"
    public static let connectionsPath = "/llu/connections"
    /// The reading ids (contracts/sources/dedup.json): the live reading, and the graph's 5-minute points.
    public static let liveId = "librelinkup"
    public static let graphId = "librelinkup-graph"

    public static func graphPath(_ patientId: String) -> String { "/llu/connections/\(patientId)/graph" }

    /// Whether `text` is a version LibreLinkUp may ask for: it goes into a header as it is, so nothing else may.
    public static func isVersion(_ text: String) -> Bool { text.wholeMatch(of: /\d{1,3}(\.\d{1,4}){1,3}/) != nil }

    /// Whether version `a` is newer than `b`, parts compared as numbers (4.17.0 > 4.9.9).
    public static func newer(_ a: String, than b: String) -> Bool {
        let x = a.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) ?? 0 }
        let y = b.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let d = (i < x.count ? x[i] : 0) - (i < y.count ? y[i] : 0)
            if d != 0 { return d > 0 }
        }
        return false
    }

    /// The version every call sends: one LibreLinkUp asked for (kept with the account), unless this build's is newer
    /// (poll.json `versions`).
    public static func send(asked: String?) -> String {
        guard let asked, newer(asked, than: version) else { return version }
        return asked
    }

    /// A 403's minimum version, when it is one.
    static func minimumVersion(_ data: [String: Any]?) -> String? {
        text(data?["minimumVersion"]).flatMap { isVersion($0) ? $0 : nil }
    }

    /// LibreView's maintenance: every path of the region's host answers HTTP 911 with `{"status": 911}`.
    public static let maintenanceCode = 911

    /// The host of `region` (nil = the global one).
    public static func host(region: String?) -> String { region.map { "api-\($0).libreview.io" } ?? host }

    /// The `Account-Id` header: the account's id as SHA-256 hex.
    public static func sha256(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// TrendArrow 1…5 (falling quickly … rising quickly) as mg/dL/min; any other: none.
    static let arrowRate: [Int: Double] = [1: -2.5, 2: -1.5, 3: 0, 4: 1.5, 5: 2.5]

    static func object(_ body: Data) -> [String: Any]? {
        (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
    }

    /// One login answer (`code`, `body`, the Retry-After header): login.json's rules.
    public static func parseLogin(code: Int, body: Data, retryAfter: TimeInterval? = nil) -> LluLogin {
        let o = object(body)
        let data = o?["data"] as? [String: Any]
        let status = int(o?["status"])
        if code == maintenanceCode || status == maintenanceCode { return .maintenance }
        if code == 429 || status == 429 {
            let lockout = number((data?["data"] as? [String: Any])?["lockout"])
            return .rateLimited(retryAfter: retryAfter ?? lockout)
        }
        if code == 403, let minimum = minimumVersion(data) { return .version(minimum: minimum) }
        if code == 401 { return .badCredentials }
        if code == 404 { return .retired }
        guard code == 200, o != nil else { return .failed("HTTP \(code)") }
        if status == 0, let data, data["redirect"] as? Bool == true || text(data["redirect"]) == "true" {
            guard let region = text(data["region"]), region.wholeMatch(of: /[a-z]{2,3}/) != nil else {
                return .failed("bad region")
            }
            return .redirect(region: region)
        }
        if status == 0, let data {
            let id = text((data["user"] as? [String: Any])?["id"])
            let ticket = data["authTicket"] as? [String: Any]
            if let id, id.count == 36, let token = text(ticket?["token"]), !token.isEmpty {
                return .account(accountId: id, token: token, expires: number(ticket?["expires"]) ?? 0)
            }
            return .failed("no token")
        }
        switch status {
        case 2: return .badCredentials
        case 4: return .terms(step: text((data?["step"] as? [String: Any])?["type"]))
        default: return .failed("status \(status.map(String.init) ?? "none")")
        }
    }

    /// An authenticated call's answer: calls.json's rules.
    public static func parseCall(code: Int, body: Data, retryAfter: TimeInterval? = nil) -> LluCall {
        let o = object(body)
        let data = o?["data"] as? [String: Any]
        let status = int(o?["status"])
        if code == maintenanceCode || status == maintenanceCode { return LluCall(.maintenance) }
        if code == 429 || status == 429 { return LluCall(.rateLimited, retryAfter: retryAfter) }
        if code == 401 { return LluCall(.expired) }
        if code == 403, let minimum = minimumVersion(data) {
            return LluCall(.version, minimumVersion: minimum)
        }
        if code == 403 || code == 404 || code == 410 { return LluCall(.retired) }
        guard code == 200, let o else { return LluCall(.server) }
        if status == 4 { return LluCall(.terms) }
        guard status == 0, o["data"] != nil else { return LluCall(.server) }
        return LluCall(.ok, body: o)
    }

    static func nameOf(_ c: [String: Any]) -> String {
        [text(c["firstName"]), text(c["lastName"])].compactMap { $0 }
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }

    /// The people a connections answer (`body` = its whole object) lists, in its order.
    public static func people(_ body: [String: Any]) -> [LluPerson] {
        (body["data"] as? [Any] ?? []).compactMap { $0 as? [String: Any] }.compactMap { c in
            guard let id = text(c["patientId"]), !id.isEmpty else { return nil }
            return LluPerson(id: id, name: nameOf(c))
        }
    }

    /// Whose glucose to read among `choices` (connections.json `mac`: the Mac doesn't ask whose glucose): `saved`
    /// while still shared (else gone), else the only connection (the account's own included), else choose.
    public static func pick(_ choices: [LluPerson], saved: String?) -> LluPick {
        let ids = choices.map(\.id)
        if ids.isEmpty { return LluPick(choices: choices, state: .none, pick: nil) }
        if let saved, !saved.isEmpty {
            return ids.contains(saved)
                ? LluPick(choices: choices, state: .picked, pick: saved)
                : LluPick(choices: choices, state: .gone, pick: nil)
        }
        return ids.count == 1
            ? LluPick(choices: choices, state: .picked, pick: ids[0])
            : LluPick(choices: choices, state: .choose, pick: nil)
    }

    /// A FactoryTimestamp (`M/d/yyyy h:mm:ss AM`, UTC) as epoch seconds, nil when it isn't one.
    public static func time(_ text: String?) -> TimeInterval? {
        guard let t = text?.trimmingCharacters(in: .whitespaces).uppercased(),
            let m = t.wholeMatch(of: /(\d{1,2})\/(\d{1,2})\/(\d{4}) (\d{1,2}):(\d{2}):(\d{2}) (AM|PM)/),
            let month = Int(m.1), let day = Int(m.2), let year = Int(m.3), let hour12 = Int(m.4),
            let minute = Int(m.5), let second = Int(m.6),
            (1...12).contains(month), (1...31).contains(day), (1...12).contains(hour12), minute < 60, second < 60
        else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let hour = hour12 % 12 + (m.7 == "PM" ? 12 : 0)
        let parts = DateComponents(year: year, month: month, day: day, hour: hour, minute: minute, second: second)
        guard let date = calendar.date(from: parts), calendar.component(.day, from: date) == day else { return nil }
        return date.timeIntervalSince1970
    }

    static func point(_ m: [String: Any], live: Bool) -> LluGraph.Point? {
        guard let t = time(text(m["FactoryTimestamp"])), let v = number(m["ValueInMgPerDl"]), v > 0, v.isFinite
        else { return nil }
        let rate = live ? int(m["TrendArrow"]).flatMap { arrowRate[$0] } : nil
        return LluGraph.Point(
            reading: Reading(t: Date(timeIntervalSince1970: t), mgdl: Int((v + 0.5).rounded(.down)), sensorRate: rate),
            source: live ? liveId : graphId)
    }

    /// One graph answer (`body` = its whole object): graph.json's rules.
    public static func graph(_ body: [String: Any]) -> LluGraph {
        let data = body["data"] as? [String: Any] ?? [:]
        let conn = data["connection"] as? [String: Any] ?? [:]
        var out = (data["graphData"] as? [Any] ?? []).compactMap {
            ($0 as? [String: Any]).flatMap { point($0, live: false) }
        }
        if let m = conn["glucoseMeasurement"] as? [String: Any], let p = point(m, live: true) { out.append(p) }
        out.sort { ($0.reading.t, $0.source) < ($1.reading.t, $1.source) }
        let first = ((data["activeSensors"] as? [Any])?.first as? [String: Any])?["sensor"] as? [String: Any]
        let sensor = [first, conn["sensor"] as? [String: Any]].compactMap { $0 }.lazy.compactMap { s -> LluSensor? in
            guard let sn = text(s["sn"]), !sn.isEmpty, let a = number(s["a"]), a > 0 else { return nil }
            return LluSensor(serial: sn, start: Int(a))
        }.first
        return LluGraph(readings: out, sensor: sensor, name: nameOf(conn))
    }

    /// THE rank rule for LibreLinkUp's two ids (contracts/sources/dedup.json; `ReadingRank.merge` gives the same):
    /// every live reading, and a graph point only where no live reading lies within the window (they only fill gaps).
    /// Both lists in time order; linear, for a month of readings.
    public static func merge(live: [Reading], graph: [Reading]) -> [Reading] {
        var out: [Reading] = []
        out.reserveCapacity(live.count + graph.count)
        var i = 0
        for g in graph {
            while i < live.count, live[i].t.timeIntervalSince(g.t) < -ReadingRank.window { i += 1 }
            let near = i < live.count && live[i].t.timeIntervalSince(g.t) <= ReadingRank.window
            if !near { out.append(g) }
        }
        out += live
        return out.sorted { $0.t < $1.t }
    }

    // MARK: - JSON values

    static func text(_ v: Any?) -> String? {
        switch v {
        case let s as String: s
        case let n as NSNumber where CFGetTypeID(n) != CFBooleanGetTypeID(): n.stringValue
        default: nil
        }
    }

    static func number(_ v: Any?) -> Double? {
        guard let n = v as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() else { return nil }
        return n.doubleValue
    }

    static func int(_ v: Any?) -> Int? {
        guard let d = number(v), d == d.rounded() else { return nil }
        return Int(d)
    }
}

/// When the LibreLinkUp source asks again (contracts/sources/librelinkup/poll.json).
public enum LluPoll {
    /// The Libre app uploads each reading as the sensor reads it, each minute.
    public static let cadence: TimeInterval = 60
    /// LibreLinkUp has a reading within seconds: the call goes this long after it is due (it grows when late).
    public static let margin: TimeInterval = 8
    public static let marginUp: TimeInterval = 5
    public static let marginHold = 10
    public static let marginMax: TimeInterval = 40
    /// A timed call never sooner than this after an answer: one call a reading at most.
    public static let minimum: TimeInterval = 45
    public static let backoff: [TimeInterval] = [60, 120, 300, 600]
    public static let rateLimitedMin: TimeInterval = 600
    public static let renewBefore: TimeInterval = 300
    public static let connectionsEvery: TimeInterval = 1800
    /// New terms: asked again now and then (the person accepts in LibreLinkUp; no lockout risk).
    public static let terms: TimeInterval = 600
    /// Abbott refused this client: asked again rarely.
    public static let retired: TimeInterval = 3600
    /// LibreView's maintenance: the backoff, at most this far apart.
    public static let maintenanceMax: TimeInterval = 300
    /// ... and this long after the window's end when the status page gives one and that is sooner.
    public static let maintenanceAfterEnd: TimeInterval = 30

    /// How late after a reading is due the call goes, and the new readings found in time in a row.
    public struct Timing: Equatable, Sendable {
        public var margin: TimeInterval
        public var streak: Int
        public init(margin: TimeInterval = LluPoll.margin, streak: Int = 0) {
            self.margin = margin
            self.streak = streak
        }
    }

    /// The timing after a call answered at `now` that found the live reading `found` (nil = none), `before` the newest
    /// live reading known before it (nil = none: a first call says nothing about timing). A new reading more than a
    /// cadence old when found missed its call: the margin grows; `marginHold` in a row found in time take a second
    /// off. Nothing new (the sensor missed one, or it is on its way): unchanged. (poll.json `margins`)
    public static func timing(_ t: Timing, now: Date, before: Date?, found: Date?) -> Timing {
        guard let found, let before, found > before else { return t }
        if now.timeIntervalSince(found) > cadence { return Timing(margin: min(t.margin + marginUp, marginMax)) }
        let streak = t.streak + 1
        return streak >= marginHold
            ? Timing(margin: max(t.margin - 1, margin)) : Timing(margin: t.margin, streak: streak)
    }

    /// Seconds to the next call after an answer: `margin` after the next reading is due (the newest live reading +
    /// a cadence), the first such time at least `minimum` from now; a reading stamped ahead counts as now; none yet:
    /// a cadence. (poll.json `timed`)
    public static func timed(now: Date, newest: Date?, margin m: TimeInterval) -> TimeInterval {
        guard let newest else { return cadence }
        let n = min(newest, now)
        let k = max(1, ((now.timeIntervalSince(n) + minimum - m) / cadence).rounded(.up))
        return n.addingTimeInterval(k * cadence + m).timeIntervalSince(now)
    }

    /// Seconds to the next call after a failure; nil = only when the person acts (a refused password: retries lock the
    /// account).
    public static func wait(failures: Int, rateLimited: TimeInterval? = nil, refused: Bool = false) -> TimeInterval? {
        if refused { return nil }
        if let rateLimited { return max(rateLimitedMin, rateLimited) }
        return backoff[min(max(failures, 1) - 1, backoff.count - 1)]
    }

    /// Seconds to the next call while LibreView is down for maintenance: the failures' backoff, at most
    /// `maintenanceMax`, and `maintenanceAfterEnd` after the window's end (`until`, Abbott's status page) when that is
    /// sooner, never sooner than the first backoff step (poll.json `after`).
    public static func maintenanceWait(failures: Int, now: Date, until: Date?) -> TimeInterval {
        var w = min(wait(failures: failures) ?? backoff[0], maintenanceMax)
        if let until, until > now {
            w = min(w, max(until.timeIntervalSince(now) + maintenanceAfterEnd, backoff[0]))
        }
        return w
    }

    /// Seconds to the next call after a round that ended in `outcome` (nil = not set up), the failures in a row and a
    /// 429's wait; nil = only when the person acts (poll.json `after`). An answer: timed to the next reading
    /// (`timed`). Asked to wait without a time: `rateLimitedMin`.
    public static func next(
        outcome: SourceHealth.Outcome?, failures: Int, retryAfter: TimeInterval? = nil,
        now: Date = Date(timeIntervalSince1970: 0), newest: Date? = nil, margin m: TimeInterval = margin,
        until: Date? = nil
    ) -> TimeInterval? {
        switch outcome {
        case nil: nil
        case .ok: timed(now: now, newest: newest, margin: m)
        case .maintenance: maintenanceWait(failures: failures, now: now, until: until)
        case .terms: terms
        case .retired: retired
        case .notShared: connectionsEvery
        case .rateLimited: wait(failures: failures, rateLimited: retryAfter ?? 0)
        case .refused: wait(failures: failures, refused: true)
        default: wait(failures: failures)
        }
    }
}
