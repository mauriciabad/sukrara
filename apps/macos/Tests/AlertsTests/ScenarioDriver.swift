// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraAlerts
import SukraraCore
import SukraraTesting
import Testing

/// One timeline of `contracts/alerts/scenarios/*.txt` (format and driver: SPEC §7), the same files Android runs.
struct Scenario: CustomTestStringConvertible, Sendable {
    struct Line: Sendable {
        var minute: Int
        var second: Int
        var events: [String]
        var expect: [String]
    }

    var file: String
    var title: String
    var settings: [String]
    var lines: [Line]
    var end: Int
    var mirror: Bool
    /// `at HH:MM`: the wearer's local time (UTC) at minute 0, in minutes after midnight; nil = 08:54.
    var at: Int? = nil

    var testDescription: String { "\(file): \(title)" }

    /// "22:05" → minutes after midnight.
    static func clockMinutes(_ text: String) -> Int? {
        let p = text.trimmingCharacters(in: .whitespaces).split(separator: ":")
        guard p.count == 2, let h = Int(p[0]), let m = Int(p[1]) else { return nil }
        return h * 60 + m
    }

    static var directory: URL { Contracts.url("alerts/scenarios") }

    /// Every scenario of every file, in file order.
    static func all() -> [Scenario] {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return names.filter { $0.hasSuffix(".txt") }.sorted().flatMap { name in
            parse(name, (try? String(contentsOf: directory.appendingPathComponent(name), encoding: .utf8)) ?? "")
        }
    }

    static func parse(_ file: String, _ text: String) -> [Scenario] {
        func trim(_ s: some StringProtocol) -> String { s.trimmingCharacters(in: .whitespaces) }
        var out: [Scenario] = []
        var title: String?
        var settings: [String] = []
        var lines: [Line] = []
        var end: Int?
        var mirror = false
        var at: Int?
        func flush() {
            guard let t = title else { return }
            out.append(
                Scenario(
                    file: file, title: t, settings: settings, lines: lines, end: end ?? lines.map(\.minute).max() ?? 0,
                    mirror: mirror, at: at))
            title = nil
            settings = []
            lines = []
            end = nil
            mirror = false
            at = nil
        }
        for raw in text.components(separatedBy: "\n") {
            let l = trim(raw.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false).first ?? "")
            if l.isEmpty { continue }
            if l.hasPrefix("scenario ") {
                flush()
                title = trim(l.dropFirst(9))
            } else if l.hasPrefix("settings ") {
                settings = trim(l.dropFirst(9)).split(whereSeparator: \.isWhitespace).map(String.init)
            } else if l.hasPrefix("end ") {
                end = Int(trim(l.dropFirst(4)))
            } else if l == "mirror" {
                mirror = true
            } else if l.hasPrefix("at ") {
                at = clockMinutes(String(l.dropFirst(3)))
            } else {
                let (left, right) =
                    l.range(of: "=>").map { (trim(l[..<$0.lowerBound]), String(l[$0.upperBound...])) } ?? (l, "")
                let time = String(left.prefix { $0 != " " })
                let ms = time.split(separator: ":").map { Int($0) ?? 0 }
                let at = ms[0] * 60 + (ms.count > 1 ? ms[1] : 0)
                let events = left.dropFirst(time.count).split(separator: ",").map(trim).filter { !$0.isEmpty }
                let expect = right.split(separator: ";").map(trim).filter { !$0.isEmpty && $0 != "nothing" }
                lines.append(Line(minute: at / 60, second: at, events: events, expect: expect))
            }
        }
        flush()
        return out
    }
}

enum ScenarioError: Error, CustomStringConvertible {
    case bad(String)
    var description: String {
        switch self {
        case .bad(let s): s
        }
    }
}

/// One `key=value` of a `settings` line or `set` event on top of `s`.
func applySetting(_ s: inout AlertSettings, _ kv: String) throws {
    let p = kv.split(separator: "=", maxSplits: 1).map(String.init)
    guard p.count == 2 else { throw ScenarioError.bad("bad setting \(kv)") }
    let (k, v) = (p[0], p[1])
    func int() throws -> Int {
        guard let i = Int(v) else { throw ScenarioError.bad("not a number: \(kv)") }
        return i
    }
    func quiet(_ current: QuietRule) throws -> QuietRule {
        if v == "off" { return QuietRule(on: false, min: current.min) }
        guard let m = Double(v) else { throw ScenarioError.bad("not a number: \(kv)") }
        return QuietRule(on: true, min: m)
    }
    switch k {
    case "alerts": s.on = v == "on"
    case "carbs.mute": s.muteAfterCarbs = try quiet(s.muteAfterCarbs)
    case "insulin.mute": s.muteAfterInsulin = try quiet(s.muteAfterInsulin)
    case "falling.mute": s.muteFallingHigh = try quiet(s.muteFallingHigh)
    case "mute.min": s.notificationActions.muteMin = try int()
    case "relax.min": s.notificationActions.relaxMin = try int()
    case "back.mgdl": s.notificationActions.backMgdl = try int()
    case "back.min": s.notificationActions.backMin = try int()
    case "persist.low": s.persistence.lowMin = try int()
    case "persist.high": s.persistence.highMin = try int()
    case "persist.margin": s.persistence.marginMgdl = try int()
    case "escalate":
        if v == "off" { s.escalation.on = false } else { s.escalation.on = true; s.escalation.min = try int() }
    case "follower.escalate": s.escalation.follow = v == "on"
    case "night": s.night.on = v == "on"
    case "night.from", "night.to":
        guard let m = Scenario.clockMinutes(v) else { throw ScenarioError.bad("not a time: \(kv)") }
        if k == "night.from" { s.night.from = m } else { s.night.to = m }
    default:
        let night = k.hasPrefix("night.")
        let parts = (night ? String(k.dropFirst(6)) : k).split(separator: ".", maxSplits: 1).map(String.init)
        guard let kind = AlertKind(rawValue: parts[0]) else { throw ScenarioError.bad("unknown setting \(kv)") }
        var r = night ? s.night[kind] : s[kind]
        if parts.count == 1 {
            switch v {
            case "on": r.on = true
            case "off": r.on = false
            default:
                guard let x = Double(v) else { throw ScenarioError.bad("not a number: \(kv)") }
                r.threshold = x
            }
        } else if parts[1] == "repeat" {
            r.repeatMin = try int()
        } else {
            throw ScenarioError.bad("unknown setting \(kv)")
        }
        if night { s.night[kind] = r } else { s[kind] = r }
    }
}

/// Runs `sc` (driver: SPEC §7) and returns one line per minute that differs; empty when everything matches. A `mirror`
/// scenario also runs a follower mirroring the wearer: `AlertMirror` over a second engine that gets everything but the
/// wearer's notification actions (what the wearer publishes, SPEC §2).
func runScenario(_ sc: Scenario) throws -> [String] {
    let base = 1_760_000_040  // a whole minute
    // `at HH:MM`: that time of the same day (UTC)
    let t0 = sc.at.map { base - base % 86400 + $0 * 60 } ?? base
    let utc = TimeZone(identifier: "UTC")!
    var s = AlertSettings()
    for kv in sc.settings { try applySetting(&s, kv) }
    // the scenario's time (the monotonic clock); the wearer's own clock is `clock`, moved by `clock back`
    var clock = Date(timeIntervalSince1970: TimeInterval(t0))
    var real = clock
    var offset: TimeInterval = 0
    var treatments: [(kind: TreatmentKind, at: Date, deleted: Bool)] = []
    var got: [Int: [String]] = [:]
    var follower = AlertMirror()
    var published: [String: AlertFiring] = [:]

    func record(_ what: String) { got[Int(real.timeIntervalSince1970) / 60 - t0 / 60, default: []].append(what) }
    func live() -> [AlertTreatment] {
        treatments.filter { !$0.deleted }.map { AlertTreatment(kind: $0.kind, at: $0.at) }
    }

    func wearerEffects(_ effects: [AlertEffect]) {
        for e in effects {
            switch e {
            case .fire(let f): record(f.headline)
            case .clear(let f): record("clear \(f.kind.rawValue)")
            case .back(let side): record("back \(side.rawValue)")
            case .resume(let f): record("resume \(f.kind.rawValue)")
            case .schedule: break
            }
        }
    }
    // the wearer's run for the followers, with its escalation of an unanswered very low (#39)
    var watch = EscalationWatch()
    func publishedEvents(_ events: [EscalationWatch.Event]) {
        for e in events {
            switch e {
            case .fire(let f, let esc):
                published[f.id] = f
                let escalated = esc != nil && s.escalation.follow
                if follower.fire(f.kind, id: f.id, at: f.at, escalated: escalated) {
                    record("follower \(f.headline)" + (escalated ? " · no response" : ""))
                }
            case .clear(let f):
                if follower.clear(f.kind, id: f.id) { record("follower clear \(f.kind.rawValue)") }
            case .answered(let kind, _):
                if follower.answered(kind) != nil { record("follower answered \(kind.rawValue)") }
            }
        }
    }

    var wearer = AlertEngine(context: AlertContext(settings: s, timeZone: utc))
    // mirror: the wearer's shared engine (no notification actions: what it publishes), feeding `follower`
    var shared = AlertEngine(context: AlertContext(settings: s, timeZone: utc))
    var mono: TimeInterval { real.timeIntervalSince1970 }
    /// What the followers get after a wearer input: the shared run's `effects`, `answer` = an answer on the wearer's phone.
    func share(_ effects: [AlertEffect], answer: Bool) {
        guard sc.mirror else { return }
        publishedEvents(
            watch.step(
                shared: effects, answer: answer, newest: shared.showing(.level), settings: s.escalation, at: clock))
    }
    /// A wearer's own action (never shared): `answer` = it answers a very low on the wearer's phone.
    func wearerDo(_ input: AlertInput, answer: Bool = false) {
        wearerEffects(wearer.handle(input, at: clock, monotonic: mono))
        share([], answer: answer)
    }
    func both(_ input: AlertInput) {
        wearerEffects(wearer.handle(input, at: clock, monotonic: mono))
        guard sc.mirror else { return }
        var answer = false
        if case .treatments(let list) = input { answer = watch.answeredBy(list) }
        share(shared.handle(input, at: clock, monotonic: mono), answer: answer)
    }
    // the follower is another device: its clock is the scenario's
    func followerReading(_ r: GlucoseSample?) {
        guard sc.mirror else { return }
        for side in follower.reading(r, s, at: real) { record("follower back \(side.rawValue)") }
    }
    func followerShow(_ id: String?) { if let id, let f = published[id] { record("follower \(f.headline)") } }
    /// The follower's Mute all ran out (its timer, or the input that came first).
    func followerExpire() {
        guard sc.mirror else { return }
        for id in follower.expire(at: real) { followerShow(id) }
    }
    func minutes(_ w: [String], _ from: Int) throws -> Int? {
        guard w.count > from else { return nil }
        let t = w[from...].joined(separator: " ")
        guard let m = Durations.parse(t) else { throw ScenarioError.bad("not a duration: \(t)") }
        return m
    }
    func allEnd(_ w: [String], _ from: Int, from now: Date) throws -> Date {
        if w.count > from && w[from] == "never" { return muteAllForever }
        guard let m = try minutes(w, from) else { throw ScenarioError.bad("mute all needs a length") }
        return now.addingTimeInterval(TimeInterval(m * 60))
    }
    func side(_ name: String) throws -> AlertSide {
        guard let k = AlertKind(rawValue: name) else { throw ScenarioError.bad("unknown kind \(name)") }
        return k.side
    }
    func newestKind(_ g: AlertGroup) -> AlertKind? {
        published.values.filter { $0.kind.group == g }.max { $0.at < $1.at }?.kind
    }

    var glucose = 0.0
    var rate: Double?
    var age = 0
    var sensorOn = true
    var every = 1
    var nextReading: Int?
    // when each device asked to be woken, on the scenario's time
    var timer: Int?
    var followerTimer: Int?
    guard Set(sc.lines.map(\.minute)).count == sc.lines.count else {
        throw ScenarioError.bad("\(sc.file): \(sc.title): two lines for the same minute")
    }
    let byTime = Dictionary(uniqueKeysWithValues: sc.lines.map { (t0 + $0.second, $0) })
    let stop = t0 + (sc.end + 1) * 60
    var t = t0
    while t < stop {
        real = Date(timeIntervalSince1970: TimeInterval(t))
        clock = real.addingTimeInterval(offset)
        for ev in byTime[t]?.events ?? [] {
            let w = ev.split(whereSeparator: \.isWhitespace).map(String.init)
            func after(_ key: String) -> String? {
                w.firstIndex(of: key).flatMap { $0 > 0 && $0 + 1 < w.count ? w[$0 + 1] : nil }
            }
            switch w[0] {
            case "glucose":
                glucose = Double(w[1]) ?? 0
                rate = after("rate").flatMap(Double.init)
                age = after("age").flatMap(Int.init) ?? 0
                if nextReading == nil { nextReading = t }
            case "readings": every = Int(w[2]) ?? 1
            case "sensor":
                switch w[1] {
                case "ended":
                    sensorOn = false
                    both(
                        .sensor(
                            SensorWindow(start: clock - 15 * 86400, end: clock, warmupEnd: clock - 15 * 86400 + 3600)))
                case "started":
                    sensorOn = false
                    both(.sensor(SensorWindow(start: clock, end: clock + 15 * 86400, warmupEnd: clock + 3600)))
                default:
                    sensorOn = w[1] == "on"
                    if sensorOn { nextReading = t }
                }
            case "source":
                guard w.count > 1, w[1] == "started" else { throw ScenarioError.bad("unknown event: \(ev)") }
                both(.source(since: clock))
            case "clock":
                guard w.count > 2, w[1] == "back", let m = try minutes(w, 2) else {
                    throw ScenarioError.bad("unknown event: \(ev)")
                }
                offset -= TimeInterval(m * 60)
                clock = real.addingTimeInterval(offset)
                watch.shiftBack(TimeInterval(m * 60))
                both(.tick)  // the system's clock-change notice (the app ticks the alerts)
            case "carbs", "insulin":
                treatments.append((w[0] == "carbs" ? .carbs : .rapidInsulin, clock, false))
                both(.treatments(live()))
            case "delete":
                let k: TreatmentKind = w[1] == "carbs" ? .carbs : .rapidInsulin
                if let i = treatments.lastIndex(where: { $0.kind == k && !$0.deleted }) { treatments[i].deleted = true }
                both(.treatments(live()))
            case "mute":
                if w[1] == "all" {
                    wearerDo(.muteAll(until: try allEnd(w, 2, from: clock)), answer: true)
                } else {
                    let m = try minutes(w, 2) ?? s.notificationActions.muteMin
                    let sd = try side(w[1])
                    wearerDo(.mute(sd, minutes: m), answer: sd == .low)
                }
            case "relax": wearerDo(.remindLess(try side(w[1])), answer: try side(w[1]) == .low)
            case "unmute":
                if w[1] == "all" {
                    wearerDo(.unmuteAll, answer: true)
                } else {
                    wearerDo(.unmute(try side(w[1])), answer: try side(w[1]) == .low)
                }
            case "unrelax": wearerDo(.remindAsUsual(try side(w[1])), answer: try side(w[1]) == .low)
            // the wearer taps, swipes or opens the alert: an answer on the wearer's phone (#39)
            case "ack": share([], answer: try side(w[1]) == .low)
            case "follower":
                switch (w[1], w.count > 2 ? w[2] : "") {
                case ("mute", "all"):
                    for g in follower.muteAll(until: try allEnd(w, 3, from: real)) {
                        record("follower clear \(newestKind(g)?.rawValue ?? g.rawValue)")
                    }
                case ("mute", let k):
                    let sd = try side(k)
                    let m = try minutes(w, 3) ?? s.notificationActions.muteMin
                    if follower.mute(sd, until: real.addingTimeInterval(TimeInterval(m * 60))) {
                        record("follower clear \(k)")
                    }
                case ("relax", let k): follower.remindLess(try side(k), relaxMin: s.notificationActions.relaxMin)
                case ("unmute", "all"): for id in follower.unmuteAll(at: real) { followerShow(id) }
                case ("unmute", let k): followerShow(follower.unmute(try side(k), at: real))
                case ("unrelax", let k): followerShow(follower.remindAsUsual(try side(k), at: real))
                default: throw ScenarioError.bad("unknown event: \(ev)")
                }
            case "set":
                for kv in w.dropFirst() { try applySetting(&s, kv) }
                both(.settings(s))
            case "restart":
                let (restarted, resumed) = AlertEngine.restore(
                    AlertEngine.Snapshot.decode(wearer.snapshot.encoded()), context: wearer.context, at: clock)
                wearer = restarted
                wearerEffects(resumed)
                // the followers' alerts stay as they are
                shared =
                    AlertEngine.restore(
                        AlertEngine.Snapshot.decode(shared.snapshot.encoded()), context: shared.context, at: clock
                    ).0
            default: throw ScenarioError.bad("unknown event: \(ev)")
            }
        }
        if sensorOn, let r = nextReading, r <= t {
            // stamped by the wearer's clock; the follower gets it with the wearer's own time
            let rd = GlucoseSample(at: clock.addingTimeInterval(TimeInterval(-age * 60)), mgdl: glucose, rate: rate)
            both(.reading(rd))
            followerReading(GlucoseSample(at: rd.at.addingTimeInterval(-offset), mgdl: glucose, rate: rate))
            nextReading = t + every * 60
        } else if let tm = timer, tm <= t {
            both(.tick)
            followerReading(wearer.context.latest)
        } else if sc.mirror, let ft = followerTimer, ft <= t {
            followerReading(wearer.context.latest)
        }
        followerExpire()
        func onScenarioTime(_ d: Date?) -> Int? { d.map { Int(($0.timeIntervalSince1970 - offset).rounded(.up)) } }
        timer = onScenarioTime(
            [
                wearer.nextCheck(after: clock), sc.mirror ? shared.nextCheck(after: clock) : nil,
                sc.mirror ? watch.due(s.escalation).flatMap { $0 > clock ? $0 : nil } : nil,
            ]
            .compactMap { $0 }.min())
        followerTimer =
            sc.mirror ? follower.nextCheck(after: real).map { Int($0.timeIntervalSince1970.rounded(.up)) } : nil
        guard
            let next = [byTime.keys.filter({ $0 > t }).min(), sensorOn ? nextReading : nil, timer, followerTimer]
                .compactMap({ $0 }).filter({ $0 > t }).min()
        else {
            break
        }
        t = next
    }
    // the wearer's and the follower's actions are compared apart (their order within a minute is free)
    func split(_ l: [String]) -> [String] {
        l.filter { !$0.hasPrefix("follower ") } + l.filter { $0.hasPrefix("follower ") }
    }
    let expected = Dictionary(sc.lines.map { ($0.minute, $0.expect) }, uniquingKeysWith: { a, _ in a })
    return (0...sc.end).compactMap { m in
        let want = split(expected[m] ?? [])
        let have = split(got[m] ?? [])
        func show(_ l: [String]) -> String { l.isEmpty ? "[nothing]" : "[\(l.joined(separator: ", "))]" }
        return want == have ? nil : "minute \(m): expected \(show(want)), got \(show(have))"
    }
}
