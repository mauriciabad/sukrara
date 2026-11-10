// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation
import SukraraCore

/// How far an entry this Mac wrote has got (PROTOCOL-v2 "Treatments").
public enum TreatmentDeliveryState: Sendable, Equatable {
    case sending, sent, accepted
    /// No relay took it and the phone isn't near; Retry sends it again.
    case failed
}

/// What the treatment windows need from the follower runtime (`FollowerService.send` behind an adapter).
public protocol TreatmentActions: AnyObject {
    /// Signs and sends a new version of `treatment` (new entry, edit or tombstone). False = not allowed.
    func send(_ treatment: Treatment) async -> Bool
}

/// The Treatments window's state: entries newest first, delivery per entry, and edit / delete with undo (⌘Z or the
/// toast's Undo for 10 s).
@Observable
public final class TreatmentsModel {
    public static let undoSeconds: TimeInterval = 10

    public struct Row: Identifiable, Equatable, Sendable {
        public var treatment: Treatment
        /// nil for entries this Mac didn't write.
        public var delivery: TreatmentDeliveryState?
        public var id: String { treatment.id }
    }

    /// The last edit or delete, which the toast's Undo (⌘Z) takes back until `until`.
    public struct Undo: Equatable, Sendable {
        public enum Kind: Equatable, Sendable { case deleted, changed }
        public var kind: Kind
        /// The entry as it was before: Undo sends it again as a newer version.
        public var before: Row
        /// The entry the toast names (the deleted one, or the changed one as it is now).
        public var shown: Treatment
        public var until: Date
    }

    public private(set) var rows: [Row]
    /// Counts changes of the clock's look (24-hour time, time zone): the rows' times are drawn again.
    public var clockStyle = 0
    /// What the toast offers to take back, and when the offer ends.
    public private(set) var undo: Undo?
    /// The wearer allowed this Mac to add treatments.
    public var canWrite: Bool
    /// This Mac follows someone (else there is nobody to add treatments for).
    public var isFollowing = false
    /// The wearer's name as the follow shows it (nil = not following): the rows' "Logged on Ana's phone".
    public var wearer: String?
    @ObservationIgnored private weak var actions: (any TreatmentActions)?
    @ObservationIgnored private let now: () -> Date

    public init(rows: [Row], canWrite: Bool, actions: (any TreatmentActions)?, now: @escaping () -> Date = Date.init) {
        self.rows = rows.sorted { $0.treatment.t > $1.treatment.t }
        self.canWrite = canWrite
        self.actions = actions
        self.now = now
    }

    /// How close in time a typed insulin entry and a pen entry of the same kind are taken for the same injection.
    public static let penWindow: Int64 = 45 * 60

    /// The newest pen entry of an insulin kind in `entries` within `penWindow` of it (Add Treatment asks first).
    public func penDuplicate(of entries: [Treatment]) -> Treatment? {
        let insulin = entries.filter { $0.k == .rapid || $0.k == .long }
        return
            rows
            .map(\.treatment)
            .filter { pen in
                pen.fromPen && !pen.del && insulin.contains { $0.k == pen.k && abs($0.t - pen.t) <= Self.penWindow }
            }
            .max { $0.t < $1.t }
    }

    /// This Mac wrote the entry: it has a delivery state (the runtime keeps one for every entry this Mac sent; they
    /// travel as `o: follower`, like every writer's). Never the phone's, a pen's or another follower's.
    public func isMine(_ row: Row) -> Bool { row.delivery != nil }

    /// Only the entries this Mac wrote can be edited or deleted, and only while the wearer allows it to write.
    public func canChange(_ row: Row) -> Bool { canWrite && isMine(row) }

    public var undoAvailable: Bool { undo.map { $0.until > now() } ?? false }

    public func delete(_ row: Row) async {
        guard canChange(row), let i = rows.firstIndex(of: row) else { return }
        rows.remove(at: i)
        undo = Undo(
            kind: .deleted, before: row, shown: row.treatment, until: now().addingTimeInterval(Self.undoSeconds))
        var t = row.treatment
        t.del = true
        t.u = version(after: t)
        // Not sent = not deleted: the entry comes back (the phone still has it) instead of vanishing only here.
        if await actions?.send(t) != true {
            undo = nil
            insert(row)
        }
    }

    /// Saves a changed amount (0 for a note), note text and time as a newer version of the entry; nothing happens
    /// when nothing changed. A send no relay took shows *Not sent* with Retry, like a new entry.
    public func edit(_ row: Row, amount: Double, note: String, at t: Int64) async {
        guard canChange(row), let current = rows.first(where: { $0.id == row.id }) else { return }
        var e = current.treatment
        e.a = amount
        e.n = Treatment.truncated(note)
        e.t = t
        guard e.a != current.treatment.a || e.n != current.treatment.n || e.t != current.treatment.t else { return }
        e.u = version(after: current.treatment)
        undo = Undo(kind: .changed, before: current, shown: e, until: now().addingTimeInterval(Self.undoSeconds))
        insert(Row(treatment: e, delivery: .sending))
        await send(e)
    }

    /// Takes back the last edit or delete: the entry as it was, sent as a newer version (it wins over the tombstone
    /// or the change).
    public func undoLast() async {
        guard undoAvailable, let u = undo else { return }
        undo = nil
        var t = u.before.treatment
        t.del = false
        t.u = version(after: rows.first { $0.id == t.id }?.treatment ?? u.shown)
        insert(Row(treatment: t, delivery: .sending))
        await send(t)
    }

    /// A version strictly newer than `t`'s (the clock's milliseconds, or one more when the clock hasn't moved on).
    private func version(after t: Treatment) -> Int64 { max(Self.millis(now()), t.u + 1) }

    public func add(_ treatments: [Treatment]) async {
        for t in treatments {
            insert(Row(treatment: t, delivery: .sending))
            await send(t)
        }
    }

    public func retry(_ row: Row) async {
        guard row.delivery == .failed else { return }
        set(row.id, .sending)
        await send(row.treatment)
    }

    /// The runtime reported a delivery change (relay OK, the phone's echo, or a timeout).
    public func set(_ id: String, _ state: TreatmentDeliveryState) {
        guard let i = rows.firstIndex(where: { $0.id == id }) else { return }
        rows[i].delivery = state
    }

    /// The runtime's newest list (visible entries) and the delivery of each entry this Mac wrote. A row marked
    /// failed here stays failed while the runtime still holds it as sending (Retry is its exit); the entry just
    /// deleted (undo offered) stays hidden until the runtime's tombstone arrives.
    public func sync(_ treatments: [Treatment], deliveries: [String: TreatmentDeliveryState], canWrite: Bool) {
        self.canWrite = canWrite
        let failedRows = rows.filter { $0.delivery == .failed }
        let failed = Set(failedRows.map(\.id))
        let hidden = undo?.kind == .deleted ? undo?.before.id : nil
        let synced = treatments.filter { !$0.del && $0.id != hidden }
            .map { t in
                let d = deliveries[t.id]
                return Row(treatment: t, delivery: failed.contains(t.id) && d == .sending ? .failed : d)
            }
        // an entry the runtime refused never reached it: keep it on screen with Retry instead of dropping it
        let ids = Set(synced.map(\.id))
        rows = (synced + failedRows.filter { !ids.contains($0.id) }).sorted { $0.treatment.t > $1.treatment.t }
    }

    public func dismissUndo() { undo = nil }

    private func send(_ t: Treatment) async {
        let ok = await actions?.send(t) ?? false
        if !ok { set(t.id, .failed) }
    }

    private func insert(_ row: Row) {
        rows.removeAll { $0.id == row.id }
        let i = rows.firstIndex { $0.treatment.t < row.treatment.t } ?? rows.endIndex
        rows.insert(row, at: i)
    }

    /// How far back the Treatments window lists entries.
    public static let shownDays = 7

    /// `rows` from the last `shownDays` calendar days (today included), grouped by day, newest day and entry first.
    static func byDay(_ rows: [Row], now: Date, calendar: Calendar = .current) -> [(day: Date, rows: [Row])] {
        let today = calendar.startOfDay(for: now)
        guard let from = calendar.date(byAdding: .day, value: -(shownDays - 1), to: today) else { return [] }
        var out: [(day: Date, rows: [Row])] = []
        for row in rows.sorted(by: { $0.treatment.t > $1.treatment.t }) {
            let at = Date(timeIntervalSince1970: TimeInterval(row.treatment.t))
            guard at >= from else { continue }
            let day = calendar.startOfDay(for: at)
            if out.last?.day == day { out[out.count - 1].rows.append(row) } else { out.append((day, [row])) }
        }
        return out
    }

    static func millis(_ d: Date) -> Int64 { Int64((d.timeIntervalSince1970 * 1000).rounded()) }
}
