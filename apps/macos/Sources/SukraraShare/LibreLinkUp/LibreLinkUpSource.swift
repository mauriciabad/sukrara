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

    public init(
        email: String = "", signInAgain: Bool = false, people: [LluPerson] = [], patient: String = "",
        name: String = "", running: Bool = false, outcome: SourceHealth.Outcome? = nil, online: Bool = true,
        readings: [Reading] = [], monitoring: Bool = false, treatments: [Treatment] = [], lastAnswer: Date? = nil,
        sensor: LluSensor? = nil
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
    }

    public static let off = LibreLinkUpSnapshot()

    /// The state the person sees at `now` (contracts/sources/health.json); a password refused says so first.
    public func health(at now: Date) -> SourceHealth.State {
        if signInAgain { return .signedOut }
        return SourceHealth.state(
            outcome: outcome, online: online, newestAge: readings.last.map { now.timeIntervalSince($0.t) })
    }
}

/// The LibreLinkUp source as the app sees it (`LibreLinkUpSource`; tests use it over a fake `LibreLinkUpHTTP`).
public protocol LibreLinkUpSourcing: Sendable {
    /// Each call is one subscriber: the current snapshot first, then every change (only the newest is buffered).
    func snapshots() async -> AsyncStream<LibreLinkUpSnapshot>
    /// Switches it on with `config`, or off (nil). The same config again changes nothing. Another person than the
    /// one whose readings and log it holds starts both again.
    func set(_ config: LibreLinkUpConfig?) async
    /// Signs in (Connect): saved only when LibreLinkUp accepts it; whom the account follows, and whom to read
    /// (`yours`: the account itself first).
    func connect(email: String, password: String, yours: Bool) async -> LluConnectResult
    /// *Try Again* after no one shared or new terms: whom the saved account follows, the pick kept while shared.
    func recheck(yours: Bool, saved: String?) async -> LluConnectResult
    /// Forgets the account (the readings and the log stay).
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
/// Mac's treatment log into `state.json`. HTTPS only, redirects never followed; nothing is ever uploaded.
public actor LibreLinkUpSource: LibreLinkUpSourcing {
    private let http: any LibreLinkUpHTTP
    private let live: any ReadingsStoring
    private let graph: any ReadingsStoring
    private let state: JSONFileStore<LibreLinkUpState>
    private let secrets: any SecretStoring
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
    /// The version header sent; raised once by a 403 that names a minimum (kept with the account).
    private var version = LibreLinkUp.version

    public init(
        http: any LibreLinkUpHTTP, live: any ReadingsStoring, graph: any ReadingsStoring,
        state: JSONFileStore<LibreLinkUpState>, secrets: any SecretStoring, clock: any TimeSource = SystemClock(),
        sleep: @escaping @Sendable (Double) async throws -> Void = { try await Task.sleep(for: .seconds($0)) }
    ) {
        self.http = http
        self.live = live
        self.graph = graph
        self.state = state
        self.secrets = secrets
        self.clock = clock
        self.sleep = sleep
    }

    /// The source over the files under `directory` (`readings.csv`, `graph/readings.csv`, `state.json`).
    public static func files(
        directory: URL, secrets: any SecretStoring, http: any LibreLinkUpHTTP = URLSessionLibreLinkUpHTTP()
    ) -> LibreLinkUpSource {
        LibreLinkUpSource(
            http: http, live: ReadingsFileStore(directory: directory),
            graph: ReadingsFileStore(directory: directory.appending(path: "graph", directoryHint: .isDirectory)),
            state: JSONFileStore(url: directory.appending(path: "state.json", directoryHint: .notDirectory)),
            secrets: secrets)
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

    public func connect(email: String, password: String, yours: Bool) async -> LluConnectResult {
        stop()
        let email = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let saved = try? account()
        let region = saved?.email.caseInsensitiveCompare(email) == .orderedSame ? saved?.region : nil
        let result: LluConnectResult
        switch await signIn(email: email, password: password, region: region, gen: generation) {
        case .ready(let s): result = await pick(s, yours: yours, saved: nil)
        case let other: result = Self.result(other)
        }
        publishAccount()
        restart()
        return result
    }

    public func recheck(yours: Bool, saved: String?) async -> LluConnectResult {
        stop()
        let result: LluConnectResult
        switch await session(gen: generation) {
        case .ready(let s): result = await pick(s, yours: yours, saved: saved)
        case let other: result = Self.result(other)
        }
        publishAccount()
        restart()
        return result
    }

    public func signOut() async {
        stop()
        try? secrets.delete(account: MacSettings.libreLinkUpSecretAccount)
        version = LibreLinkUp.version
        peopleChecked = nil
        var s = await log()
        s.people = []
        try? await state.save(s)
        // off until a new sign-in picks whom to read (the app clears the pick too)
        config = nil
        var snap = current
        snap.people = []
        snap.running = false
        snap.outcome = nil
        snap.monitoring = false
        publish(snap)
        publishAccount()
    }

    public func retry() async { restart() }

    public func network(online: Bool) async {
        let back = online && !self.online
        self.online = online
        guard config != nil else { return }
        if current.online != online {
            var s = current
            s.online = online
            publish(s)
        }
        if back { restart() }
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
        try? await state.save(s)
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

    private func restart() {
        stop()
        if let config { start(config) }
    }

    private func start(_ config: LibreLinkUpConfig) {
        let gen = generation
        task = Task { [weak self] in await self?.run(config, gen: gen) }
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

    /// The person's readings and log; another person's are dropped first (two people's data never mix).
    private func open(_ patient: String) async {
        var s = await log()
        if s.patient != patient {
            if !s.patient.isEmpty {
                try? await live.removeAll()
                try? await graph.removeAll()
            }
            s = LibreLinkUpState(
                patient: patient, name: s.people.first { $0.id == patient }?.name ?? "", people: s.people)
            try? await state.save(s)
        }
        var snap = current
        snap.patient = patient
        snap.name = s.name
        snap.people = s.people
        snap.sensor = s.sensor
        snap.treatments = s.live
        snap.running = true
        snap.outcome = nil
        snap.monitoring = false
        snap.online = online
        snap.readings = await day()
        publish(snap)
    }

    private func log() async -> LibreLinkUpState { await state.load() ?? LibreLinkUpState() }

    private func run(_ config: LibreLinkUpConfig, gen: Int) async {
        var failures = 0
        while !Task.isCancelled && gen == generation {
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
            let wait: TimeInterval? =
                switch c.outcome {
                case nil: nil
                case .terms: LluPoll.terms
                case .retired: LluPoll.retired
                case .notShared: LluPoll.connectionsEvery
                case .rateLimited: LluPoll.wait(failures: failures, rateLimited: c.retryAfter ?? 0)
                case .refused: LluPoll.wait(failures: failures, refused: true)
                default: LluPoll.wait(failures: failures)
                }
            // nil: only the person acts (a refused password: Sign In Again; retries lock the account)
            guard let wait else { return }
            do { try await sleep(wait) } catch { return }
        }
    }

    struct Cycle {
        var outcome: SourceHealth.Outcome?
        var retryAfter: TimeInterval?
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
        }
    }

    /// One round: the session, whom the account follows (now and then), then the graph.
    private func cycle(_ patient: String, gen: Int) async -> Cycle {
        let first = await session(gen: gen)
        guard case .ready(var ready) = first else { return Self.failed(first) }
        let now = clock.now
        if peopleChecked.map({ now.timeIntervalSince($0) >= LluPoll.connectionsEvery }) ?? true {
            var r = await get(LibreLinkUp.connectionsPath, ready)
            if r.outcome == .expired {
                // the token ended before its time: one new sign-in with the saved password, then the same call
                expire()
                guard case .ready(let again) = await session(gen: gen) else { return Cycle(outcome: .refused) }
                ready = again
                r = await get(LibreLinkUp.connectionsPath, ready)
            }
            guard r.outcome == .ok, let body = r.body else {
                return Cycle(outcome: Self.outcome(r.outcome), retryAfter: r.retryAfter)
            }
            guard gen == generation else { return Cycle(outcome: nil) }
            let people = LibreLinkUp.people(body)
            peopleChecked = now
            var s = await log()
            s.people = people
            try? await state.save(s)
            if current.people != people {
                var snap = current
                snap.people = people
                publish(snap)
            }
            if !people.contains(where: { $0.id == patient }) { return Cycle(outcome: .notShared) }
        }
        var r = await get(LibreLinkUp.graphPath(patient), ready)
        if r.outcome == .expired {
            expire()
            guard case .ready(let again) = await session(gen: gen) else { return Cycle(outcome: .refused) }
            ready = again
            r = await get(LibreLinkUp.graphPath(patient), ready)
        }
        guard r.outcome == .ok, let body = r.body else {
            // the person may have stopped sharing: the connections say so at the next round
            if r.outcome == .retired { peopleChecked = nil }
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
        try? await state.save(s)
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
        return Cycle(outcome: .ok)
    }

    /// A session that isn't ready, as the health reads it.
    static func failed(_ s: Session) -> Cycle {
        switch s {
        case .ready: Cycle(outcome: .server)
        case .signedOut: Cycle(outcome: .refused)
        case .terms: Cycle(outcome: .terms)
        case .rateLimited(let wait): Cycle(outcome: .rateLimited, retryAfter: wait)
        case .retired: Cycle(outcome: .retired)
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
        case .failed(let offline): offline ? .offline : .unreachable
        }
    }

    /// Whom the account follows after a sign-in, and whom to read (connections.json); a pick that's gone picks again.
    private func pick(_ session: Ready, yours: Bool, saved: String?) async -> LluConnectResult {
        let r = await get(LibreLinkUp.connectionsPath, session)
        guard r.outcome == .ok, let body = r.body else {
            switch r.outcome {
            case .terms: return .terms
            case .rateLimited: return .rateLimited
            case .retired, .version: return .stopped
            case .network: return online ? .unreachable : .offline
            default: return .unreachable
            }
        }
        let people = LibreLinkUp.people(body)
        var p = LibreLinkUp.pick(people, accountId: session.accountId, yours: yours, saved: saved)
        if p.state == .gone { p = LibreLinkUp.pick(people, accountId: session.accountId, yours: yours, saved: nil) }
        peopleChecked = clock.now
        var s = await log()
        s.people = people
        try? await state.save(s)
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
        /// No answer, or one Sukrara can't read (`offline`: this Mac has no network).
        case failed(offline: Bool)
    }

    /// The saved account (nil = none); throws when the Keychain item can't be read.
    private func account() throws -> Account? {
        guard let data = try secrets.read(account: MacSettings.libreLinkUpSecretAccount) else { return nil }
        let a = try JSONDecoder().decode(Account.self, from: data)
        if let v = a.version { version = v }
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
                        expires: expires, version: raised ? version : nil))
                return .ready(Ready(accountId: id, token: token, region: region))
            case .redirect(let r): region = r
            case .version(let minimum):
                if raised { return .retired }
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
            case .failed: return .failed(offline: false)
            }
        }
        return .failed(offline: false)
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
