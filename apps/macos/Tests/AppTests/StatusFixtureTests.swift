// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraTesting
import Testing

@testable import SukraraAlerts
@testable import SukraraApp
@testable import SukraraGraph

/// One case of `contracts/graph/status.json`, passable as a Swift Testing argument.
nonisolated struct StatusCase: @unchecked Sendable, CustomTestStringConvertible {
    // Immutable after init (a copied Foundation dictionary), so sharing it between test tasks is safe.
    let object: [String: Any]
    var testDescription: String { object["name"] as? String ?? "?" }
    var expect: [String: Any] { object["expect"] as? [String: Any] ?? [:] }

    static let all: [StatusCase] = {
        let url = Contracts.url("graph/status.json")
        guard let data = FileManager.default.contents(atPath: url.path),
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let cases = root["cases"] as? [[String: Any]]
        else { return [] }
        return cases.map(StatusCase.init)
    }()
}

/// `contracts/graph/status.json` through the Mac's decision (Android: `GraphFixtureTest.status`): every case runs the
/// alert engine over its reading, treatments, notification mutes and Mute all, then `CardAlertsMapping` turns the
/// engine's state into the card's status, badge and last-treatment slot. The mirroring follower's path (the same
/// rules over the latest reading, `mirroredStatus`) must decide the same.
@Suite struct StatusFixtureTests {
    static let now = Date(timeIntervalSince1970: 1_800_000_000)
    var now: Date { Self.now }
    var t: TimeInterval { now.timeIntervalSince1970 }

    @Test func fileHasCases() { #expect(!StatusCase.all.isEmpty) }

    func number(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }

    /// The case's settings: the schema defaults, with its `muteAfterInsulin` / `muteAfterCarbs`.
    func settings(_ c: StatusCase) -> AlertSettings {
        var s = AlertSettings()
        let o = c.object["settings"] as? [String: Any] ?? [:]
        func rule(_ key: String, _ r: inout QuietRule) {
            guard let q = o[key] as? [String: Any] else { return }
            if let on = q["on"] as? Bool { r.on = on }
            if let min = number(q["min"]) { r.min = min }
        }
        rule("muteAfterInsulin", &s.muteAfterInsulin)
        rule("muteAfterCarbs", &s.muteAfterCarbs)
        return s
    }

    func marks(_ c: StatusCase) -> [GraphMark] {
        (c.object["treatments"] as? [[String: Any]] ?? []).map {
            GraphMark(
                t: t + (number($0["t"]) ?? 0), k: $0["k"] as? String ?? "", a: number($0["a"]) ?? 0,
                n: $0["n"] as? String ?? "", id: $0["id"] as? String ?? "")
        }.sorted { $0.t < $1.t }
    }

    func reading(_ c: StatusCase) -> GlucoseSample? {
        guard let r = c.object["reading"] as? [String: Any], let sgv = number(r["sgv"]) else { return nil }
        return GlucoseSample(at: now.addingTimeInterval(number(r["t"]) ?? 0), mgdl: sgv)
    }

    /// When a mute given as seconds from now (or "never") ends.
    func end(_ v: Any?) -> Date? {
        if (v as? String) == "never" { return muteAllForever }
        return number(v).map { now.addingTimeInterval($0) }
    }

    /// The notification mutes ending at the case's times: a side muted for whole minutes, started so it ends then.
    func sideMutes(_ c: StatusCase) -> [(AlertSide, minutes: Int, at: Date)] {
        (c.object["paused"] as? [String: Any] ?? [:]).compactMap { key, v in
            guard let side = AlertSide(rawValue: key), let left = number(v) else { return nil }
            let minutes = max(1, Int((left / 60).rounded(.up)))
            return (side, minutes, now.addingTimeInterval(left - Double(minutes * 60)))
        }
    }

    /// The engine after the case's inputs, in time order, at `now`.
    func engine(_ c: StatusCase) -> AlertEngine {
        var e = AlertEngine(context: AlertContext())
        var inputs: [(Date, AlertInput)] = [(now.addingTimeInterval(-7200), .settings(settings(c)))]
        let treatments = marks(c).compactMap { m -> AlertTreatment? in
            let k: TreatmentKind? = m.k == "carbs" ? .carbs : m.k == "rapid" ? .rapidInsulin : nil
            return k.map { AlertTreatment(kind: $0, at: Date(timeIntervalSince1970: m.t)) }
        }
        inputs.append((now.addingTimeInterval(-7200), .treatments(treatments)))
        if let r = reading(c) { inputs.append((r.at, .reading(r))) }
        for (side, minutes, at) in sideMutes(c) { inputs.append((at, .mute(side, minutes: minutes))) }
        if let all = end(c.object["allMuted"]) { inputs.append((now, .muteAll(until: all))) }
        for (at, input) in inputs.sorted(by: { $0.0 < $1.0 }) { _ = e.handle(input, at: at) }
        _ = e.handle(.tick, at: now)
        return e
    }

    /// `none`, `needed:<kind>`, `taken:<kind>:<id>`, `paused:<kind>` or `muted`, as the fixture writes it.
    func name(_ s: AlertStatus, kind: AlertKind?) -> String {
        let k = kind?.rawValue ?? "?"
        switch s {
        case .none: return "none"
        case .needed: return "needed:\(k)"
        case .taken(_, let tr, _): return "taken:\(k):\(tr.id)"
        case .paused: return "paused:\(k)"
        case .muted: return "muted"
        }
    }

    func check(_ card: CardAlerts, kind: AlertKind?, _ c: StatusCase, path: String) {
        let e = c.expect
        #expect(name(card.status, kind: kind) == e["status"] as? String, "\(path): status")
        if e["until"] != nil {
            #expect(card.status.until == end(e["until"])?.timeIntervalSince1970, "\(path): until")
        }
        let look = StatusBadge.look(card.status, now: t, words: .en)  // the fixture's words are English
        if let b = e["badge"] as? [String: Any] {
            let want = StatusBadge.Look(
                kind: b["kind"] as? String ?? "", text: b["text"] as? String ?? "", short: b["short"] as? String ?? "",
                tonal: b["tonal"] as? Bool ?? false)
            #expect(look == want, "\(path): badge")
        } else {
            #expect(look == nil, "\(path): badge")
        }
        if let l = e["last"] as? [String: Any] {
            #expect(card.last?.tr.id == l["id"] as? String, "\(path): last")
            #expect(card.last?.until == number(l["until"]).map { t + $0 }, "\(path): last until")
            let with = l["with"] as? [String: Any]
            #expect(card.last?.carbs?.tr.id == with?["id"] as? String, "\(path): last with")
            if let with {
                #expect(card.last?.carbs?.until == number(with["until"]).map { t + $0 }, "\(path): with until")
            }
        } else {
            #expect(card.last == nil, "\(path): last")
        }
    }

    /// The wearer's Mac: the engine's state.
    @Test(arguments: StatusCase.all)
    func engineDecides(_ c: StatusCase) {
        let e = engine(c)
        let ui = e.uiState(at: now)
        let card = CardAlertsMapping.of(ui, settings: e.settings, marks: marks(c), now: now)
        check(card, kind: ui.active[.level]?.kind, c, path: "engine")
    }

    /// A follower mirroring the wearer's alerts: the same rules over the latest reading, this Mac's mutes.
    @Test(arguments: StatusCase.all)
    func mirroringDecidesTheSame(_ c: StatusCase) {
        let s = settings(c), marks = marks(c)
        var mutes: [AlertSide: Date] = [:]
        for (side, minutes, at) in sideMutes(c) {
            let until = at.addingTimeInterval(Double(minutes * 60))
            if until > now { mutes[side] = until }
        }
        let card = CardAlertsMapping.mirrored(
            latest: reading(c), settings: s, marks: marks, sideMutes: mutes, muteAll: end(c.object["allMuted"]),
            now: now)
        check(card, kind: AlertEngine.level(of: reading(c), s, at: now), c, path: "mirrored")
    }
}
