// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import os

/// Whom the LibreLinkUp source reads (schema `source.libreLinkUpPatient`), set while it is the person's source.
public struct LibreLinkUpConfig: Sendable, Equatable {
    public var patient: String

    public init(patient: String) { self.patient = patient }
}

/// The answer of Connect's *Sign In* (or of *Try Again*).
public enum LluConnectResult: Equatable, Sendable {
    /// Signed in: the people the account follows; the one to read (nil = several: pick one).
    case connected(people: [LluPerson], picked: String?)
    case badCredentials
    /// New terms to accept in LibreLinkUp first.
    case terms
    case rateLimited
    /// Signed in, but no one shares with this account yet.
    case noOne
    case unreachable
    case offline
    /// LibreLinkUp refuses this client: Abbott changed it.
    case stopped
    /// LibreView is down for maintenance in the account's region (HTTP 911): nothing to fix, it comes back by itself.
    case maintenance
}

/// What the app sees of the LibreLinkUp source (one value, replaced on every change).
public struct LibreLinkUpSnapshot: Sendable, Equatable {
    /// The saved account's email ("" = no account).
    public var email: String
    /// An account is saved but can't sign in by itself (its password was refused, or the Keychain item is unreadable).
    public var signInAgain: Bool
    /// The people the account follows (the last connections answer).
    public var people: [LluPerson]
    /// Whose readings and log this Mac holds, and their name as LibreLinkUp says it.
    public var patient: String
    public var name: String
    /// It reads now (a person picked and LibreLinkUp the source).
    public var running: Bool
    /// The last call's outcome (nil = nothing asked yet, or off).
    public var outcome: SourceHealth.Outcome?
    /// This Mac had a network at the last call.
    public var online: Bool
    /// The last 24 hours of its readings, oldest first (live readings, and the graph's points in their gaps).
    public var readings: [Reading]
    /// LibreLinkUp answered at least once since it was switched on: from then on its readings are monitored and losing
    /// it rings the no-data alert.
    public var monitoring: Bool
    /// This Mac's treatment log (live entries, oldest first).
    public var treatments: [Treatment]
    /// When LibreLinkUp last answered.
    public var lastAnswer: Date?
    /// The sensor LibreLinkUp says is running (nil = its life is unknown).
    public var sensor: LluSensor?
    /// LibreView's maintenance windows for the account's region, from Abbott's status page (the last good answer;
    /// empty = none known).
    public var windows: [MaintenanceWindow]

    public init(
        email: String = "", signInAgain: Bool = false, people: [LluPerson] = [], patient: String = "",
        name: String = "", running: Bool = false, outcome: SourceHealth.Outcome? = nil, online: Bool = true,
        readings: [Reading] = [], monitoring: Bool = false, treatments: [Treatment] = [], lastAnswer: Date? = nil,
        sensor: LluSensor? = nil, windows: [MaintenanceWindow] = []
    ) {
        self.email = email
        self.signInAgain = signInAgain
        self.people = people
        self.patient = patient
        self.name = name
        self.running = running
        self.outcome = outcome
        self.online = online
        self.readings = readings
        self.monitoring = monitoring
        self.treatments = treatments
        self.lastAnswer = lastAnswer
        self.sensor = sensor
        self.windows = windows
    }

    public static let off = LibreLinkUpSnapshot()

    /// While LibreView is down for maintenance (health `maintenance`): the end Abbott's status page gives, nil = none
    /// known (contracts/sources/librelinkup/status.json `until`).
    public func maintenanceUntil(at now: Date) -> Date? {
        health(at: now) == .maintenance ? LibreViewStatus.until(windows, now: now) : nil
    }

    /// The next maintenance window that hasn't begun (the warn-ahead), nil = none listed.
    public func upcoming(at now: Date) -> MaintenanceWindow? { LibreViewStatus.upcoming(windows, now: now) }

    /// The person picked no longer shares with the account, others still do (pick again, not "no one shares").
    public var pickedGone: Bool { !patient.isEmpty && !people.isEmpty && !people.contains { $0.id == patient } }

    /// The state the person sees at `now` (contracts/sources/health.json); a password refused says so first.
    public func health(at now: Date) -> SourceHealth.State {
        if signInAgain { return .signedOut }
        return SourceHealth.state(
            outcome: outcome, online: online, newestAge: readings.last.map { now.timeIntervalSince($0.t) },
            warmingUp: SourceHealth.warming(sensor?.sensor, latest: readings.last?.t, now: now))
    }
}

/// The LibreLinkUp source as the app sees it (`LibreLinkUpSource`; tests use it over a fake `LibreLinkUpHTTP`).
public protocol LibreLinkUpSourcing: Sendable {
    /// Each call is one subscriber: the current snapshot first, then every change (only the newest is buffered).
    func snapshots() async -> AsyncStream<LibreLinkUpSnapshot>
    /// Switches it on with `config`, or off (nil). The same config again changes nothing. Another person than the
    /// one whose readings and log it holds: theirs are kept aside (`KeptHistory.shelve`) and the other's taken back.
    func set(_ config: LibreLinkUpConfig?) async
    /// Signs in (Connect): saved only when LibreLinkUp accepts it; whom the account follows, and whom to read (the
    /// only connection, the account's own included; several: the person picks).
    func connect(email: String, password: String) async -> LluConnectResult
    /// *Try Again* after no one shared or new terms: whom the saved account follows, the pick kept while shared.
    func recheck(saved: String?) async -> LluConnectResult
    /// Forgets the account (the readings and the log stay; while another source is read they are kept apart).
    func signOut() async
    /// Try Again (and the network came back): ask now instead of after the wait.
    func retry() async
    /// Whether this Mac has a network (the path monitor).
    func network(online: Bool) async
    /// Its stored readings since `since`, oldest first (31 days kept).
    func readings(since: Date) async -> [Reading]
    /// An entry this Mac adds, changes or deletes (a tombstone); false = not taken (off).
    func write(_ treatment: Treatment) async -> Bool
}

/// Reads LibreLinkUp (docs/44 step 2, contracts/sources/librelinkup; Android `LibreLinkUpSource`): the person the
/// account follows, their live reading each minute (its arrow as the sensor's rate) and the graph's 5-minute points of
/// the last 12 hours, which only fill gaps (`LibreLinkUp.merge`); whom the account follows every 30 minutes; the
/// sensor's serial and start as its life. The account (email, password, token, region) lives only in the Keychain
/// (`source.libreLinkUpSecret`); the token is reused until 5 minutes before it expires (each login counts towards
/// LibreLinkUp's lockout), a token refused early signs in again once, a refused password is never retried by itself,
/// new terms are never accepted for the person. Readings go into its own 31-day stores (live and graph apart), this
/// Mac's treatment log into `state.json`; one person's at a time, the others kept aside under `kept` (per patient,
/// `KeptHistory`). HTTPS only, redirects never followed; nothing is ever uploaded.
public actor LibreLinkUpSource: LibreLinkUpSourcing {
    private let http: any LibreLinkUpHTTP
    /// Abbott's status page (nil = never asked: tests, offline runs).
    private let status: (any LibreLinkUpHTTP)?
    /// When the status page was last asked (nil = not yet).
    private var statusAsked: Date?
    private let live: any ReadingsStoring
    private let graph: any ReadingsStoring
    private let state: JSONFileStore<LibreLinkUpState>
    /// The log as last saved (`log()`).
    private var cached: LibreLinkUpState?
    private let secrets: any SecretStoring
    /// Where the people it doesn't read now are kept (`SukraraPaths.kept`; nil = nowhere: they are deleted).
    private let kept: URL?
    private let clock: any TimeSource
    private let sleep: @Sendable (Double) async throws -> Void
    private static let log = Logger(subsystem: SukraraIDs.appBundleID, category: "librelinkup")
    /// A gap after which the whole graph is read again.
    static let gap: TimeInterval = 300
    /// Each call keeps a little before the newest reading known (a late graph point).
    static let overlap: TimeInterval = 1200
    /// A reading stamped a little ahead (this Mac's clock behind) still counts.
    static let ahead: TimeInterval = 300

    private var config: LibreLinkUpConfig?
    private var task: Task<Void, Never>?
    private var current = LibreLinkUpSnapshot.off
    private var subscribers: [UUID: AsyncStream<LibreLinkUpSnapshot>.Continuation] = [:]
    /// Bumped by every config change, sign-in, sign-out and Try Again: a run of an older generation publishes nothing.
    private var generation = 0
    private var online = true
    private var loaded = false
    /// When whom the account follows was last read (nil = not yet in this run).
    private var peopleChecked: Date?
    /// The version header sent; raised by a 403 that names a minimum (kept with the account, poll.json `versions`).
    private var version = LibreLinkUp.version
    /// LibreLinkUp asked to wait (429): nothing calls it before then, whatever asks (the network back, Try Again,
    /// another person picked: each call may lengthen the lockout); a sign-in the person makes ends it.
    private var holdUntil: Date?

    private var holding: Bool { holdUntil.map { clock.now < $0 } ?? false }

    /// Asked to wait `wait` (nil = no time given: the contract's minimum).
    private func hold(_ wait: TimeInterval?) {
        holdUntil = clock.now.addingTimeInterval(LluPoll.wait(failures: 1, rateLimited: wait ?? 0) ?? 0)
    }

    public init(
        http: any LibreLinkUpHTTP, live: any ReadingsStoring, graph: any ReadingsStoring,
        state: JSONFileStore<LibreLinkUpState>, secrets: any SecretStoring, kept: URL? = nil,
        clock: any TimeSource = SystemClock(),
        sleep: @escaping @Sendable (Double) async throws -> Void = { try await Task.sleep(for: .seconds($0)) },
        status: (any LibreLinkUpHTTP)? = nil
    ) {
        self.http = http
        self.status = status
        self.live = live
        self.graph = graph
        self.state = state
        self.secrets = secrets
        self.kept = kept
        self.clock = clock
        self.sleep = sleep
    }

    /// The source over the files under `directory` (`readings.csv`, `graph/readings.csv`, `state.json`), the people
    /// it doesn't read now under `kept`.
    public static func files(
        directory: URL, kept: URL, secrets: any SecretStoring, http: any LibreLinkUpHTTP = URLSessionLibreLinkUpHTTP(),
        status: (any LibreLinkUpHTTP)? = URLSessionLibreLinkUpHTTP()
    ) -> LibreLinkUpSource {
        LibreLinkUpSource(
            http: http, live: ReadingsFileStore(directory: directory),
            graph: ReadingsFileStore(
                directory: directory.appending(path: KeptHistory.graphFolder, directoryHint: .isDirectory)),
            state: JSONFileStore(url: directory.appending(path: KeptHistory.stateFile, directoryHint: .notDirectory)),
            secrets: secrets, kept: kept, status: status)
    }

    public func snapshots() async -> AsyncStream<LibreLinkUpSnapshot> {
        await load()
        let (stream, continuation) = AsyncStream.makeStream(
            of: LibreLinkUpSnapshot.self, bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        subscribers[id] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.unsubscribe(id) } }
        continuation.yield(current)
        return stream
    }

    private func unsubscribe(_ id: UUID) { subscribers[id] = nil }

    public func set(_ config: LibreLinkUpConfig?) async {
        await load()
        guard config != self.config else { return }
        self.config = config
        stop()
        guard let config, !config.patient.isEmpty else {
            self.config = nil
            var s = current
            s.running = false
            s.outcome = nil
            s.monitoring = false
            publish(s)
            return
        }
        peopleChecked = nil
        await open(config.patient)
        start(config)
    }

    public func connect(email: String, password: String) async -> LluConnectResult {
        stop()
        // a sign-in the person makes ends a wait LibreLinkUp asked for
        holdUntil = nil
        // the account's region may be another: its windows are asked again
        statusAsked = nil
        let email = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let saved = try? account()
        let same = saved?.email.caseInsensitiveCompare(email) == .orderedSame
        let region = same ? saved?.region : nil
        // the same account signing in again (Sign In Again) keeps the person it reads while they still share
        let held = same && !current.patient.isEmpty ? current.patient : nil
        let result: LluConnectResult
        switch await signIn(email: email, password: password, region: region, gen: generation) {
        case .ready(let s): result = await pick(s, saved: held)
        case let other: result = Self.result(other)
        }
        publishAccount()
        resume(after: result)
        return result
    }

    public func recheck(saved: String?) async -> LluConnectResult {
        // asked to wait: not even Try Again calls before then
        if holding { return .rateLimited }
        stop()
        let result: LluConnectResult
        switch await session(gen: generation) {
        case .ready(let s): result = await pick(s, saved: saved)
        case let other: result = Self.result(other)
        }
        publishAccount()
        resume(after: result)
        return result
    }

    /// The loop again after a sign-in or Try Again, which already read whom the account follows: the graph at once
    /// only when the same person is read on (another person picked starts through `set`, so one graph call either
    /// way), else after the wait the answer calls for.
    private func resume(after result: LluConnectResult) {
        let outcome: SourceHealth.Outcome? =
            switch result {
            case .connected(_, let picked): picked != nil && picked == config?.patient ? nil : .ok
            case .badCredentials: .refused
            case .terms: .terms
            case .rateLimited: .rateLimited
            case .noOne: .notShared
            case .unreachable: .server
            case .offline: .network
            case .stopped: .retired
            case .maintenance: .maintenance
            }
        restart(after: outcome.flatMap { LluPoll.next(outcome: $0, failures: 1) } ?? 0)
    }

    public func signOut() async {
        let running = config != nil
        stop()
        try? secrets.delete(account: MacSettings.libreLinkUpSecretAccount)
        version = LibreLinkUp.version
        peopleChecked = nil
        var s = await log()
        s.people = []
        if !running, !s.patient.isEmpty {
            // signed out while another source is read (*Also saved on this Mac*): the person read is kept apart,
            // listed with its *Delete* (nothing of it would be reachable otherwise)
            await shelve(s)
            s = LibreLinkUpState()
            var snap = current
            snap.patient = ""
            snap.name = ""
            snap.treatments = []
            snap.sensor = nil
            snap.readings = []
            current = snap
        }
        await save(s)
        // off until a new sign-in picks whom to read (the app clears the pick too)
        config = nil
        var snap = current
        snap.people = []
        snap.running = false
        snap.outcome = nil
        snap.monitoring = false
        snap.windows = []
        statusAsked = nil
        publish(snap)
        publishAccount()
    }

    public func retry() async {
        // asked to wait: the loop asks by itself when the time LibreLinkUp gave is up
        guard !holding else { return }
        restart()
    }

    public func network(online: Bool) async {
        let back = online && !self.online
        self.online = online
        guard config != nil else { return }
        if current.online != online {
            var s = current
            s.online = online
            publish(s)
        }
        if back && !holding { restart() }
    }

    public func readings(since: Date) async -> [Reading] {
        let l = (try? await live.load(since: since)) ?? []
        let g = (try? await graph.load(since: since)) ?? []
        return LibreLinkUp.merge(live: l, graph: g)
    }

    public func write(_ treatment: Treatment) async -> Bool {
        guard config != nil else { return false }
        var s = await log()
        if let have = s.entries[treatment.id], have.u >= treatment.u { return true }
        var t = treatment
        t.o = .mac
        s.entries[t.id] = t
        await save(s)
        var snap = current
        snap.treatments = s.live
        publish(snap)
        return true
    }

    // MARK: - the loop

    private func stop() {
        task?.cancel()
        task = nil
        generation += 1
    }

    private func restart(after delay: TimeInterval = 0) {
        stop()
        if let config { start(config, after: delay) }
    }

    /// The loop for `config`, its first call after `delay` and never before a wait LibreLinkUp asked for.
    private func start(_ config: LibreLinkUpConfig, after delay: TimeInterval = 0) {
        let gen = generation
        let first = max(delay, holdUntil.map { $0.timeIntervalSince(clock.now) } ?? 0)
        task = Task { [weak self] in await self?.run(config, gen: gen, after: first) }
    }

    /// The account and the log, read once.
    private func load() async {
        guard !loaded else { return }
        loaded = true
        let s = await log()
        var snap = current
        snap.patient = s.patient
        snap.name = s.name
        snap.people = s.people
        snap.treatments = s.live
        snap.sensor = s.sensor
        current = snap
        publishAccount()
    }

    /// The person's readings and log: another person's are kept aside first and this one's taken back when kept
    /// (`KeptHistory`, per patient: two people's data never mix, and none is lost).
    private func open(_ patient: String) async {
        var s = await log()
        if s.patient != patient {
            await shelve(s)
            s = await unshelve(patient, people: s.people)
            await save(s)
        }
        var snap = current
        snap.patient = patient
        snap.name = s.name
        snap.people = s.people
        snap.sensor = s.sensor
        snap.treatments = s.live
        snap.running = true
        // asked to wait: it still is (no call before then), not "connecting"
        snap.outcome = holding ? .rateLimited : nil
        snap.monitoring = false
        snap.online = online
        snap.readings = await day()
        publish(snap)
    }

    /// The log (`state.json`, read once): every change reads it and saves it with no wait in between, so a treatment
    /// written here while a round talks to LibreLinkUp is never overwritten by the round's older copy (as Nightscout's).
    private func log() async -> LibreLinkUpState {
        if let cached { return cached }
        let s = await state.load() ?? LibreLinkUpState()
        // another call may have read it meanwhile
        if let cached { return cached }
        cached = s
        return s
    }

    /// `s` as the log: in memory at once (every read after this sees it), then in `state.json`.
    private func save(_ s: LibreLinkUpState) async {
        cached = s
        try? await state.save(s)
    }

    /// The readings stores as kept (`KeptHistory.Shelf`): the live ones in the folder, the graph's in `graph/`.
    private var shelves: [KeptHistory.Shelf] { [("", live), (KeptHistory.graphFolder, graph)] }

    /// `s`'s person (their readings, sensor and log; not whom the account follows) kept aside; without a kept folder,
    /// deleted.
    private func shelve(_ s: LibreLinkUpState) async {
        guard !s.patient.isEmpty else { return }
        guard let kept else {
            try? await live.removeAll()
            try? await graph.removeAll()
            return
        }
        let person = LibreLinkUpState(patient: s.patient, name: s.name, sensor: s.sensor, entries: s.entries)
        let log = s.entries.isEmpty ? nil : try? JSONEncoder().encode(person)
        await KeptHistory.shelve(
            KeptHistory.Info(
                id: KeptHistory.id(.libreLinkUp, s.patient), name: s.name.isEmpty ? nil : s.name,
                keptAt: Int(clock.now.timeIntervalSince1970)),
            readings: shelves, log: log, in: kept)
    }

    /// `patient`'s state: taken back from the kept ones (readings, sensor, log), else new.
    private func unshelve(_ patient: String, people: [LluPerson]) async -> LibreLinkUpState {
        let name = people.first { $0.id == patient }?.name
        var fresh = LibreLinkUpState(patient: patient, name: name ?? "", people: people)
        guard let kept,
            let data = await KeptHistory.unshelve(KeptHistory.id(.libreLinkUp, patient), readings: shelves, in: kept)
        else { return fresh }
        if let back = try? JSONDecoder().decode(LibreLinkUpState.self, from: data) {
            fresh.name = name ?? back.name
            fresh.sensor = back.sensor
            fresh.entries = back.entries
        }
        return fresh
    }

    private func run(_ config: LibreLinkUpConfig, gen: Int, after first: TimeInterval) async {
        if first > 0 {
            do { try await sleep(first) } catch { return }
        }
        var failures = 0
        // the newest live reading of this run and the calls' timing to the next one (poll.json `timed`)
        var newest: Date?
        var timing = LluPoll.Timing()
        while !Task.isCancelled && gen == generation {
            let before = current.outcome
            let c = await cycle(config.patient, gen: gen)
            guard !Task.isCancelled, gen == generation else { return }
            failures = c.outcome == .ok ? 0 : failures + 1
            if let outcome = c.outcome, outcome != .ok {
                Self.log.info("librelinkup: \(outcome.rawValue, privacy: .public)")
                var s = current
                s.outcome = outcome
                s.online = online
                publish(s)
            }
            publishAccount()
            // LibreView's maintenance windows: at once on entering one (for its end), then now and then
            let maintenance = c.outcome == .maintenance
            await askStatus(now: before != .maintenance && maintenance, maintenance: maintenance)
            guard !Task.isCancelled, gen == generation else { return }
            let now = clock.now
            if c.outcome == .ok {
                // the margin follows how soon LibreLinkUp has the readings
                timing = LluPoll.timing(timing, now: now, before: newest, found: c.live)
                if let found = c.live, found > newest ?? .distantPast { newest = found }
            }
            let wait = LluPoll.next(
                outcome: c.outcome, failures: failures, retryAfter: c.retryAfter, now: now, newest: newest,
                margin: timing.margin, until: LibreViewStatus.until(current.windows, now: now))
            // asked to wait: nothing calls LibreLinkUp before then (each call may lengthen the lockout)
            holdUntil = c.outcome == .rateLimited ? wait.map { clock.now.addingTimeInterval($0) } : nil
            // nil: only the person acts (a refused password: Sign In Again; retries lock the account)
            guard let wait else { return }
            do { try await sleep(wait) } catch { return }
        }
    }

    /// Abbott's status page for the account's region (contracts/sources/librelinkup/status.json): `now` at once (a
    /// maintenance just began), else every `maintenanceEvery` while in one, every `every` otherwise. GET only, no
    /// account, no cookies, redirects never followed; a failure or an answer that isn't the page's keeps what was known
    /// and says nothing.
    private func askStatus(now: Bool, maintenance: Bool) async {
        guard let status else { return }
        let at = clock.now
        let every = maintenance ? LibreViewStatus.maintenanceEvery : LibreViewStatus.every
        if !now, let asked = statusAsked, at.timeIntervalSince(asked) < every { return }
        statusAsked = at
        guard let region = (try? account())?.region else { return }
        var r = URLRequest(
            url: LibreViewStatus.url, cachePolicy: .reloadIgnoringLocalCacheData,
            timeoutInterval: LibreViewStatus.timeout)
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let a = try? await status.send(r), a.status == 200,
            let o = (try? JSONSerialization.jsonObject(with: a.body)) as? [String: Any],
            o["scheduled_maintenances"] is [Any]
        else { return }
        let windows = LibreViewStatus.windows(o, region: region)
        guard windows != current.windows else { return }
        var snap = current
        snap.windows = windows
        publish(snap)
    }

    struct Cycle {
        var outcome: SourceHealth.Outcome?
        var retryAfter: TimeInterval?
        /// The live reading in the answer (nil = none): the calls are timed to the next one.
        var live: Date?
    }

    static func outcome(_ o: LluCallOutcome) -> SourceHealth.Outcome {
        switch o {
        case .ok: .ok
        case .expired: .refused  // a second refusal right after a new sign-in
        case .rateLimited: .rateLimited
        case .terms: .terms
        case .version, .retired: .retired
        case .server: .server
        case .network: .network
        case .maintenance: .maintenance
        }
    }

    /// One round: the session, whom the account follows (now and then), then the graph.
    private func cycle(_ patient: String, gen: Int) async -> Cycle {
        let first = await session(gen: gen)
        guard case .ready(var ready) = first else { return Self.failed(first) }
        let now = clock.now
        let checked = peopleChecked.map { now.timeIntervalSince($0) < LluPoll.connectionsEvery } ?? false
        if !checked {
            let (end, again) = await shared(patient, ready, gen: gen, now: now)
            if let end { return end }
            ready = again
        }
        let (r, again) = await call(LibreLinkUp.graphPath(patient), ready, gen: gen)
        guard r.outcome == .ok, let body = r.body else {
            // LibreLinkUp refuses this person's graph: they may have stopped sharing (Pick Again), said now, not in an
            // hour as "stopped"
            if r.outcome == .retired, checked, let end = await shared(patient, again, gen: gen, now: now).0 {
                return end
            }
            return Cycle(outcome: Self.outcome(r.outcome), retryAfter: r.retryAfter)
        }
        guard gen == generation else { return Cycle(outcome: nil) }
        let g = LibreLinkUp.graph(body)
        let known = current.readings.last?.t
        // the whole 12 hours at the first call and after a gap, else only what's new (old graph points don't change)
        let from = known.flatMap { now.timeIntervalSince($0) > Self.gap ? nil : $0.addingTimeInterval(-Self.overlap) }
        let batch = g.readings.filter { p in
            p.reading.t > (from ?? .distantPast) && p.reading.t <= now.addingTimeInterval(Self.ahead)
        }
        let liveBatch = batch.filter(\.live).map { GlucoseRange.normalised($0.reading) }
        let graphBatch = batch.filter { !$0.live }.map { GlucoseRange.normalised($0.reading) }
        if !liveBatch.isEmpty { try? await live.merge(liveBatch) }
        if !graphBatch.isEmpty { try? await graph.merge(graphBatch) }
        guard gen == generation else { return Cycle(outcome: nil) }
        var s = await log()
        if !g.name.isEmpty { s.name = g.name }
        if let sensor = g.sensor { s.sensor = sensor }
        await save(s)
        var snap = current
        snap.readings = await day()
        snap.name = s.name
        snap.sensor = s.sensor
        snap.outcome = .ok
        snap.online = true
        online = true
        snap.monitoring = true
        snap.lastAnswer = now
        publish(snap)
        return Cycle(outcome: .ok, live: g.readings.filter(\.live).map(\.reading.t).max())
    }

    /// Whom the account follows, read again: nil while `patient` is still shared, else the round's end (not shared, or
    /// the call's failure); the session the call ended with.
    private func shared(_ patient: String, _ ready: Ready, gen: Int, now: Date) async -> (Cycle?, Ready) {
        let (r, ready) = await call(LibreLinkUp.connectionsPath, ready, gen: gen)
        guard r.outcome == .ok, let body = r.body else {
            return (Cycle(outcome: Self.outcome(r.outcome), retryAfter: r.retryAfter), ready)
        }
        guard gen == generation else { return (Cycle(outcome: nil), ready) }
        let people = LibreLinkUp.people(body)
        peopleChecked = now
        var s = await log()
        s.people = people
        await save(s)
        if current.people != people {
            var snap = current
            snap.people = people
            publish(snap)
        }
        return (people.contains { $0.id == patient } ? nil : Cycle(outcome: .notShared), ready)
    }

    /// A session that isn't ready, as the health reads it.
    static func failed(_ s: Session) -> Cycle {
        switch s {
        case .ready: Cycle(outcome: .server)
        case .signedOut: Cycle(outcome: .refused)
        case .terms: Cycle(outcome: .terms)
        case .rateLimited(let wait): Cycle(outcome: .rateLimited, retryAfter: wait)
        case .retired: Cycle(outcome: .retired)
        case .maintenance: Cycle(outcome: .maintenance)
        case .failed: Cycle(outcome: .network)
        }
    }

    static func result(_ s: Session) -> LluConnectResult {
        switch s {
        case .ready: .unreachable
        case .signedOut: .badCredentials
        case .terms: .terms
        case .rateLimited: .rateLimited
        case .retired: .stopped
        case .maintenance: .maintenance
        case .failed(let offline): offline ? .offline : .unreachable
        }
    }

    /// Whom the account follows after a sign-in, and whom to read (connections.json); a pick that's gone picks again.
    private func pick(_ session: Ready, saved: String?) async -> LluConnectResult {
        let (r, _) = await call(LibreLinkUp.connectionsPath, session, gen: generation)
        guard r.outcome == .ok, let body = r.body else {
            switch r.outcome {
            case .terms: return .terms
            case .rateLimited:
                hold(r.retryAfter)
                return .rateLimited
            case .expired: return .badCredentials
            case .retired, .version: return .stopped
            case .maintenance: return .maintenance
            case .network: return online ? .unreachable : .offline
            default: return .unreachable
            }
        }
        // LibreLinkUp answered: no longer asked to wait
        holdUntil = nil
        let people = LibreLinkUp.people(body)
        var p = LibreLinkUp.pick(people, saved: saved)
        if p.state == .gone { p = LibreLinkUp.pick(people, saved: nil) }
        peopleChecked = clock.now
        var s = await log()
        s.people = people
        await save(s)
        var snap = current
        snap.people = people
        publish(snap)
        return p.state == .none ? .noOne : .connected(people: people, picked: p.pick)
    }

    // MARK: - the account

    /// The Keychain item (schema `source.libreLinkUpSecret`): never logged, never in the defaults.
    struct Account: Codable, CustomStringConvertible {
        var email: String
        /// nil: LibreLinkUp refused it; sign in again.
        var password: String?
        var accountId = ""
        var region: String?
        var token = ""
        var expires: TimeInterval = 0
        /// The version LibreLinkUp asked for, once raised.
        var version: String?

        var description: String { "Account(***)" }
    }

    struct Ready: Sendable {
        var accountId: String
        var token: String
        var region: String?
    }

    enum Session {
        case ready(Ready)
        /// No account saved, or the password was refused: sign in (again).
        case signedOut
        case terms
        case rateLimited(TimeInterval?)
        case retired
        /// LibreView is down for maintenance (HTTP 911): the account is kept as it is.
        case maintenance
        /// No answer, or one Sukrara can't read (`offline`: this Mac has no network).
        case failed(offline: Bool)
    }

    /// The saved account (nil = none); throws when the Keychain item can't be read.
    private func account() throws -> Account? {
        guard let data = try secrets.read(account: MacSettings.libreLinkUpSecretAccount) else { return nil }
        let a = try JSONDecoder().decode(Account.self, from: data)
        // a version LibreLinkUp asked for, unless this build (or this run) already sends a newer one
        if let v = a.version, LibreLinkUp.newer(v, than: version) { version = v }
        return a
    }

    private func save(_ a: Account) {
        guard let data = try? JSONEncoder().encode(a) else { return }
        try? secrets.write(data, account: MacSettings.libreLinkUpSecretAccount)
    }

    /// The account's email and whether it can sign in by itself, into the snapshot.
    private func publishAccount() {
        var snap = current
        do {
            let a = try account()
            snap.email = a?.email ?? ""
            snap.signInAgain = a != nil && a?.password == nil
        } catch {
            snap.email = ""
            snap.signInAgain = true
        }
        if snap != current { publish(snap) }
    }

    /// A token that works now: the saved one, or (`signIn`) a new login with the saved password.
    private func session(gen: Int) async -> Session {
        let a: Account
        do {
            guard let saved = try account() else { return .signedOut }
            a = saved
        } catch {
            return .signedOut
        }
        let now = clock.now.timeIntervalSince1970
        if !a.token.isEmpty, !a.accountId.isEmpty, a.expires - LluPoll.renewBefore > now {
            return .ready(Ready(accountId: a.accountId, token: a.token, region: a.region))
        }
        guard let password = a.password else { return .signedOut }
        return await signIn(email: a.email, password: password, region: a.region, gen: gen)
    }

    /// The token stopped working before its time: the next session signs in again.
    private func expire() {
        guard var a = try? account() else { return }
        a.token = ""
        a.expires = 0
        save(a)
    }

    /// One sign-in: the region redirect followed, a version LibreLinkUp asks for sent once. Saved only when
    /// LibreLinkUp accepts it (and nothing newer happened meanwhile); a refusal of the saved account forgets its
    /// password (the email stays for the form).
    private func signIn(email: String, password: String, region start: String?, gen: Int) async -> Session {
        var region = start
        var raised = false
        for _ in 0..<3 {
            let answer: (status: Int, body: Data, headers: [String: String])
            do {
                answer = try await http.send(
                    LibreLinkUpApi.login(email: email, password: password, region: region, version: version))
            } catch {
                return .failed(offline: NightscoutApi.isOffline(error) || !online)
            }
            guard gen == generation else { return .failed(offline: false) }
            let login = LibreLinkUp.parseLogin(
                code: answer.status, body: answer.body,
                retryAfter: NightscoutApi.retryAfter(answer.headers, now: clock.now))
            switch login {
            case .account(let id, let token, let expires):
                save(
                    Account(
                        email: email, password: password, accountId: id, region: region, token: token,
                        expires: expires, version: version == LibreLinkUp.version ? nil : version))
                return .ready(Ready(accountId: id, token: token, region: region))
            case .redirect(let r): region = r
            case .version(let minimum):
                // asked once per sign-in; the same version refused again: Abbott changed more than the version
                if raised || minimum == version { return .retired }
                raised = true
                version = minimum
            case .badCredentials:
                // never retried by itself: retries lock the account
                if var saved = try? account(), saved.email == email {
                    saved.password = nil
                    saved.token = ""
                    saved.expires = 0
                    save(saved)
                }
                return .signedOut
            case .terms: return .terms
            case .rateLimited(let wait): return .rateLimited(wait)
            case .retired: return .retired
            case .maintenance: return .maintenance
            case .failed: return .failed(offline: false)
            }
        }
        return .failed(offline: false)
    }

    /// One authenticated call (calls.json): a version LibreLinkUp asks for is sent from then on and the call made again
    /// once; a token that stopped working before its time signs in again once with the saved password and the call is
    /// made again. A sign-in that fails then answers as the call's outcome. The session the call ended with.
    private func call(_ path: String, _ first: Ready, gen: Int) async -> (LluCall, Ready) {
        var ready = first
        var r = await get(path, ready)
        if r.outcome == .version, raise(r.minimumVersion) { r = await get(path, ready) }
        guard r.outcome == .expired else { return (r, ready) }
        expire()
        let again = await session(gen: gen)
        guard case .ready(let s) = again else { return (Self.call(again), ready) }
        ready = s
        r = await get(path, ready)
        if r.outcome == .version, raise(r.minimumVersion) { r = await get(path, ready) }
        return (r, ready)
    }

    /// A sign-in that failed in the middle of a call, as the call's answer.
    static func call(_ s: Session) -> LluCall {
        switch s {
        case .ready: LluCall(.server)
        case .signedOut: LluCall(.expired)
        case .terms: LluCall(.terms)
        case .rateLimited(let wait): LluCall(.rateLimited, retryAfter: wait)
        case .retired: LluCall(.retired)
        case .maintenance: LluCall(.maintenance)
        case .failed: LluCall(.network)
        }
    }

    /// A call refused this client's version: `minimum` is sent from now on and kept with the account (a restart and a
    /// new sign-in send it too). False when it is sent already (refused as well: Abbott changed more than the version).
    private func raise(_ minimum: String?) -> Bool {
        guard let minimum, minimum != version, LibreLinkUp.isVersion(minimum) else { return false }
        version = minimum
        if var a = try? account() {
            a.version = minimum
            save(a)
        }
        return true
    }

    /// One authenticated GET.
    private func get(_ path: String, _ s: Ready) async -> LluCall {
        let request = LibreLinkUpApi.get(
            path, region: s.region, token: s.token, accountId: s.accountId, version: version)
        do {
            let a = try await http.send(request)
            return LibreLinkUp.parseCall(
                code: a.status, body: a.body, retryAfter: NightscoutApi.retryAfter(a.headers, now: clock.now))
        } catch {
            if NightscoutApi.isOffline(error) { online = false }
            return LluCall(.network)
        }
    }

    // MARK: - publishing

    private func publish(_ snapshot: LibreLinkUpSnapshot) {
        current = snapshot
        for c in subscribers.values { c.yield(snapshot) }
    }

    private func day() async -> [Reading] { await readings(since: clock.now.addingTimeInterval(-86_400)) }
}
