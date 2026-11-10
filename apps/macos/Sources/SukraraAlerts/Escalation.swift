// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// The wearer's wait for an answer to a very low (SPEC §4 "No answer to a very low", #39): what goes to the followers
/// from the wearer's run without this phone's actions (§2), with its very low escalated (`esc`) once nobody answered it
/// on the wearer's phone for `escalation.min` minutes, and `answered` when it was answered after that. The same rule
/// as Android's `AlertMachine`; the Mac never is the wearer, so here it drives the shared timelines' wearer
/// (`contracts/alerts/scenarios`), and the Mac's follower side shows what it sends (`AlertMirror`).
public struct EscalationWatch: Equatable, Sendable {
    /// What the followers get.
    public enum Event: Equatable, Sendable {
        /// A firing; `esc` = minutes without an answer (escalated), nil = not escalated.
        case fire(AlertFiring, esc: Int?)
        case clear(AlertFiring)
        /// The escalated very low `id` was answered on the wearer's phone.
        case answered(AlertKind, id: String)
    }

    /// The newest very-low firing the followers got in this stretch (nil = none).
    private var id: String?
    /// The stretch's first very-low firing.
    private var since: Date?
    private var sent = false
    private var answered = false

    public init() {}

    /// Carbs logged at or after the very low first fired: an answer on the wearer's phone.
    public func answeredBy(_ treatments: [AlertTreatment]) -> Bool {
        guard let since else { return false }
        return treatments.contains { $0.kind == .carbs && $0.at >= since }
    }

    /// One input on the wearer at `t`: `shared` = the effects of the run without this phone's actions, `answer` = the
    /// input is an answer on this phone (a tap, swipe or opening the app, a notification action, Mute all, carbs),
    /// `newest` = that run's very low still up (sent again escalated when no repeat goes out now).
    public mutating func step(
        shared: [AlertEffect], answer: Bool, newest: AlertFiring?, settings s: EscalationSettings, at t: Date
    ) -> [Event] {
        var out: [Event] = []
        func minutes() -> Int { max(s.min, Int(t.timeIntervalSince(since ?? t) / 60)) }
        for e in shared {
            switch e {
            case .fire(let f) where f.kind == .veryLow:
                if id == nil {
                    self = EscalationWatch()
                    since = t
                }
                id = f.id
                out.append(.fire(f, esc: sent && !answered ? minutes() : nil))
            case .fire(let f):
                // another level kind now: the very low is over
                if f.kind.group == .level { self = EscalationWatch() }
                out.append(.fire(f, esc: nil))
            case .clear(let f):
                if f.kind == .veryLow { self = EscalationWatch() }
                out.append(.clear(f))
            case .resume, .back, .schedule: break
            }
        }
        if let id, !answered, answer {
            if sent { out.append(.answered(.veryLow, id: id)) }
            answered = true
        }
        if id != nil, !sent, !answered, s.on, let since, t >= since.addingTimeInterval(TimeInterval(s.min * 60)) {
            let last = out.lastIndex {
                if case .fire(let f, _) = $0 { f.kind == .veryLow } else { false }
            }
            if let last, case .fire(let f, _) = out[last] {
                out[last] = .fire(f, esc: minutes())
            } else if var f = newest, f.kind == .veryLow {
                f.at = t
                out.append(.fire(f, esc: minutes()))
            }
            sent = true
        }
        return out
    }

    /// When the escalation comes due (the timer), or nil.
    public func due(_ s: EscalationSettings) -> Date? {
        guard id != nil, !sent, !answered, s.on, let since else { return nil }
        return since.addingTimeInterval(TimeInterval(s.min * 60))
    }

    /// The clock was set back by `d`: the wait keeps its length.
    public mutating func shiftBack(_ d: TimeInterval) { since = since?.shiftedBack(d) }
}
