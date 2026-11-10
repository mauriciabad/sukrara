// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation
import SukraraAlerts
import SukraraCore
import SukraraShare

/// What the user did to the alerts (notification action, panel, menu).
enum AlertAction: Equatable {
    case mute(AlertSide, minutes: Int)
    case remindLess(AlertSide)
    case unmute(AlertSide)
    case remindAsUsual(AlertSide)
    case muteAll(until: Date)
    case unmuteAll
    /// A *Mute…* / *Change Time…* reply that isn't a length: nothing changes, the side's notification comes back
    /// silently (macOS removed it when the action was used).
    case reshow(AlertSide)
}

/// Runs the alert rules on this Mac (`SukraraAlerts`) and shows what they decide through the `Notifier`.
///
/// - Own rules, or (`mirroring`) the wearer's alerts through `AlertMirror` plus this Mac's own no-data rule.
/// - The no-data rule is timed by the engine's `schedule` effect (a wake), never by the next reading, and re-checked on
///   wake from sleep and clock changes (`tick`). No data counts from the last reading or the source's start
///   (`sourceStarted`), whichever is newer: a Mac that never followed anyone (nor reached Juggluco) never rings, a source
///   that started but never delivers a reading does.
/// - Every change is saved (`AlertStateStoring`), so a relaunch resumes the alerts that were up, silently, and keeps
///   the mutes.
@Observable
final class AlertController {
    /// The alerts' state for the panel and the menu bar (bell, quiet lines, countdowns).
    private(set) var ui: AlertUIState = .idle
    /// When the engine wants its next check (nil = no timer).
    private(set) var nextWake: Date?
    private(set) var mirroring = false
    /// `ui` changed (an alert came up or ended, a mute started or ran out): the widgets redraw the card's badge.
    @ObservationIgnored var onUIChange: (() -> Void)?

    @ObservationIgnored private var engine: AlertEngine
    @ObservationIgnored private var mirror = AlertMirror()
    @ObservationIgnored private let clock: any TimeSource
    @ObservationIgnored private let store: any AlertStateStoring
    @ObservationIgnored private let notifier: any AlertNotifying
    @ObservationIgnored private let wake: any WakeScheduling
    /// When Mute all ends as last seen (nil = off): its end by itself gets one "Alerts back on" log line.
    @ObservationIgnored private var muteAllSeen: Date?

    init(
        settings: AlertSettings, clock: any TimeSource, store: any AlertStateStoring, notifier: any AlertNotifying,
        wake: any WakeScheduling
    ) {
        self.clock = clock
        self.store = store
        self.notifier = notifier
        self.wake = wake
        let saved = store.load()
        let (engine, effects) = AlertEngine.restore(
            saved?.engine, context: AlertContext(settings: settings), at: clock.now)
        self.engine = engine
        if var m = saved?.mirror {
            m.sanitize(at: clock.now)
            mirror = m
            mirrored = saved?.mirrored ?? [:]
        }
        run(effects)
    }

    var settings: AlertSettings { engine.settings }

    // MARK: inputs

    /// The newest reading the Mac has (also an old one at launch: "no data" is measured from it).
    func reading(_ r: Reading) {
        let sample = GlucoseSample(at: r.t, mgdl: Double(r.mgdl), rate: r.rate)
        noteClock()
        if mirroring {
            for side in mirror.reading(sample, settings, at: clock.now) { notifier.removeMuted(side) }
        }
        handle(.reading(sample))
    }

    func treatments(_ list: [AlertTreatment]) { handle(.treatments(list)) }
    func sensor(_ window: SensorWindow?) { handle(.sensor(window)) }

    /// The reading source started (following someone, Juggluco reached): no data counts from now until a reading, also
    /// with none ever. A source already running keeps its start (also across relaunches); `resetMonitoring` forgets it.
    func sourceStarted() {
        guard engine.context.sourceStart == nil else { return }
        handle(.source(since: clock.now))
    }

    func settings(_ s: AlertSettings) {
        guard s != engine.settings else { return }
        handle(.settings(s))
    }

    /// Following a wearer whose alerts this Mac mirrors (settings source "wearer"): only no-data runs here.
    func setMirroring(_ on: Bool) {
        guard on != mirroring else { return }
        mirroring = on
        if !on {
            for g in AlertGroup.allCases where mirror.isShowing(g) && g != .noData { notifier.clear(g) }
            mirror.forgetShown()
            mirrored = [:]
        }
        handle(.scope(on ? .noDataOnly : .all))
    }

    /// An alert event of the wearer's phone (PROTOCOL-v2 "Alerts"). No data is always this Mac's own.
    func wearerAlert(_ p: AlertPayload) {
        guard mirroring, settings.on, let kind = AlertKind(rawValue: p.kind), kind != .noData else { return }
        noteClock()
        defer { expireMirror() }
        let at = Date(timeIntervalSince1970: TimeInterval(p.at))
        if p.fires {
            let firing = AlertFiring(
                id: p.id, kind: kind, at: at,
                reading: p.sgv.map { sgv in
                    GlucoseSample(
                        at: Date(timeIntervalSince1970: TimeInterval(p.t ?? p.at)), mgdl: Double(sgv), rate: p.rate)
                },
                threshold: p.threshold ?? settings[kind].threshold, n: p.count, treated: p.treatments)
            mirrored[kind.group] = firing
            if mirror.fire(kind, id: p.id, at: at) { notifier.post(firing, sound: true, category: category(kind)) }
        } else if mirror.clear(kind, id: p.id) {
            notifier.clear(kind.group)
        }
        save()
    }

    /// The wearer's newest firing per group, shown here or not (Unmute / Remind as usual shows it again).
    @ObservationIgnored private var mirrored: [AlertGroup: AlertFiring] = [:]

    /// A user action from a notification, the panel or the menu.
    func perform(_ action: AlertAction) {
        let now = clock.now
        noteClock()
        if let line = Self.logLine(action, settings: settings, now: now) { notifier.log(line) }
        switch action {
        case .mute(let side, let minutes):
            let until = now.addingTimeInterval(TimeInterval(minutes * 60))
            if mirror.mute(side, until: until) { notifier.clear(side.mainKind.group) }
            handle(.mute(side, minutes: minutes))
            notifier.postMuted(side, until: until)
        case .remindLess(let side):
            mirror.remindLess(side, relaxMin: settings.notificationActions.relaxMin)
            handle(.remindLess(side))
            reshow(side)
        case .unmute(let side):
            notifier.removeMuted(side)
            showAgain(mirror.unmute(side, at: now))
            handle(.unmute(side))
        case .remindAsUsual(let side):
            let shownAgain = showAgain(mirror.remindAsUsual(side, at: now))
            let fired = handle(.remindAsUsual(side)).contains {
                if case .fire(let f) = $0 { f.kind.side == side } else { false }
            }
            if !shownAgain && !fired { reshow(side) }
        case .muteAll(let until):
            for g in mirror.muteAll(until: until) { notifier.clear(g) }
            handle(.muteAll(until: until))
        case .unmuteAll:
            for id in mirror.unmuteAll(at: now) { showAgain(id) }
            handle(.unmuteAll)
        case .reshow(let side):
            reshow(side)
        }
    }

    /// The event log's line for `action` (`AlertLog`); nil for one that changes nothing (`reshow`).
    static func logLine(_ action: AlertAction, settings: AlertSettings, now: Date) -> String? {
        switch action {
        case .mute(let side, let minutes): AlertLog.muted(side, minutes: minutes)
        case .unmute(let side): AlertLog.unmuted(side)
        case .remindLess(let side):
            AlertLog.remindLess(side, intervalMin: settings.notificationActions.relaxMin)
        case .remindAsUsual(let side): AlertLog.remindAsUsual(side)
        case .muteAll(let until): AlertLog.allMuted(until: until, now: now)
        case .unmuteAll: AlertLog.allUnmuted
        case .reshow: nil
        }
    }

    /// Shows `side`'s notification again without sound, as it is now (SPEC §2: macOS removes a notification once one
    /// of its actions is used): the alert still up, in its current category (after *Remind Less* with *Remind as
    /// Usual*), this Mac's own or the wearer's; with no alert up, the muted note while the side is muted.
    private func reshow(_ side: AlertSide) {
        let group = side.mainKind.group
        if let f = engine.showing(group), f.kind.side == side {
            notifier.post(f, sound: false, category: category(f.kind))
        } else if mirroring, mirror.isShowing(group), let f = mirrored[group], f.kind.side == side {
            notifier.post(f, sound: false, category: category(f.kind))
        } else if let until = [
            engine.snoozes.muteEnd(side, at: clock.now), mirror.snoozes.muteEnd(side, at: clock.now),
        ]
        .compactMap({ $0 }).max() {
            notifier.postMuted(side, until: until)
        }
    }

    /// Settings › Alerts › Send Test Alert: a real notification titled "Test" (the engine and its mutes don't see it).
    func sendTest(_ kind: AlertKind) { notifier.postTest(kind) }

    /// Monitoring dropped without the person stopping it (the wearer stopped sharing, the follow broke): a passive
    /// notification says why. nil = monitoring is back (or the person ended it): the note goes.
    func monitoringStopped(_ reason: String?) { notifier.monitoringStopped(reason) }

    /// Stopped following (or following someone new): nothing measures "no data" from the old wearer's readings any
    /// more, and their alerts go. This Mac's mutes stay (the user set them).
    func resetMonitoring() {
        for g in AlertGroup.allCases { notifier.clear(g) }
        let snapshot = AlertEngine.Snapshot(
            episodes: [], snoozes: engine.snoozes, muteAllUntil: engine.muteAllUntil, lastNow: engine.snapshot.lastNow)
        let (fresh, effects) = AlertEngine.restore(
            snapshot, context: AlertContext(settings: settings, scope: engine.context.scope), at: clock.now)
        engine = fresh
        mirror.forgetShown()
        mirrored = [:]
        run(effects)
    }

    /// The timer fired, the Mac woke, or the clock changed.
    func tick() { handle(.tick) }

    /// Quitting: no timer outlives the controller.
    func stop() { wake.schedule(at: nil) {} }

    // MARK: engine

    @discardableResult
    private func handle(_ input: AlertInput) -> [AlertEffect] {
        noteClock()
        let effects = engine.handle(input, at: clock.now, monotonic: clock.monotonic)
        run(effects)
        expireMirror()
        return effects
    }

    /// A clock set back moves the engine's kept times and this Mac's mutes on the wearer's alerts alike.
    private func noteClock() {
        let back = engine.observeClock(clock.now, monotonic: clock.monotonic)
        if back > 0 { mirror.shiftBack(back) }
    }

    /// This Mac's Mute all on the wearer's alerts ran out (the timer, or any input that comes first): the wearer's
    /// newest alerts still up show at once.
    private func expireMirror() {
        let shown = mirror.expire(at: clock.now)
        for id in shown { showAgain(id) }
        if !shown.isEmpty { save() }
    }

    private func run(_ effects: [AlertEffect]) {
        // a timed Mute all that ran out by itself, logged before what it lets ring (Unmute all ends it early: no line)
        let allEnd = engine.muteAllEnd(at: clock.now)
        if let seen = muteAllSeen, seen <= clock.now, allEnd == nil { notifier.log(AlertLog.allBack) }
        muteAllSeen = allEnd
        for effect in effects {
            switch effect {
            case .fire(let f): notifier.post(f, sound: true, category: category(f.kind))
            case .resume(let f): notifier.post(f, sound: false, category: category(f.kind))
            case .clear(let f): notifier.clear(f.kind.group)
            case .back(let side):
                notifier.log(AlertLog.back(side))
                notifier.removeMuted(side)
            case .schedule(let at):
                nextWake = at
                wake.schedule(at: at) { [weak self] in self?.tick() }
            }
        }
        refreshUI()
        save()
    }

    /// Posts the wearer's firing `id` with sound (Unmute / Remind as Usual on a mirrored alert): true = posted.
    @discardableResult
    private func showAgain(_ id: String?) -> Bool {
        guard mirroring, let id, let f = mirrored.values.first(where: { $0.id == id }) else { return false }
        notifier.post(f, sound: true, category: category(f.kind))
        return true
    }

    /// This Mac's Mute of each side on the wearer's mirrored alerts, in force at `t`.
    func mirroredSideMutes(at t: Date) -> [AlertSide: Date] {
        Dictionary(
            uniqueKeysWithValues: AlertSide.allCases.compactMap { s in mirror.snoozes.muteEnd(s, at: t).map { (s, $0) }
            })
    }

    /// Recomputes `ui` (also from the panel's countdown, so an ended mute disappears).
    func refreshUI() {
        var state = engine.uiState(at: clock.now)
        if mirroring, state.muteAllUntil == nil { state.muteAllUntil = mirror.muteAllEnd(at: clock.now) }
        guard state != ui else { return }
        ui = state
        onUIChange?()
    }

    private func category(_ kind: AlertKind) -> String {
        let relaxed = engine.snoozes.relaxMin(kind.side) > 0 || mirror.snoozes.relaxMin(kind.side) > 0
        return AlertNotificationIDs.category(for: kind, settings, relaxed: relaxed)
    }

    private func save() {
        store.save(AlertState(engine: engine.snapshot, mirror: mirror, mirrored: mirrored))
    }
}

/// Wakes the alert engine at a time (`schedule(at: nil)` cancels). The app uses `TaskWake`; tests record the date and
/// call `tick()` themselves.
protocol WakeScheduling {
    func schedule(at date: Date?, _ fire: @escaping @MainActor () -> Void)
}

/// One `Task` sleeping until the date (wall clock re-checked after sleep: the engine compares dates, and a wake from
/// sleep or a clock change also ticks through `SystemEvents`).
final class TaskWake: WakeScheduling {
    private static let maxStep: TimeInterval = 60
    private var task: Task<Void, Never>?

    func schedule(at date: Date?, _ fire: @escaping @MainActor () -> Void) {
        task?.cancel()
        task = nil
        guard let date else { return }
        task = Task {
            // short steps re-checked against the wall clock: a monotonic sleep alone can stretch over a sleeping Mac
            while date.timeIntervalSinceNow > 0 {
                let step = min(Self.maxStep, date.timeIntervalSinceNow)
                do { try await Task.sleep(for: .seconds(step), tolerance: .seconds(1)) } catch { return }
            }
            fire()
        }
    }
}
