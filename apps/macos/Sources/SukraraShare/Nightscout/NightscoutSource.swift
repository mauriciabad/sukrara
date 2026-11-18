// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import os

/// What the Settings pane and setup's Connect ask for: the site (its root), the token or API secret, and whether
/// treatments go both ways.
public struct NightscoutConfig: Sendable, Equatable {
    public var site: String
    public var secret: String
    public var treatments: Bool

    public init(site: String, secret: String, treatments: Bool = true) {
        self.site = site
        self.secret = secret
        self.treatments = treatments
    }
}

/// What the app sees of the Nightscout source (one value, replaced on every change).
public struct NightscoutSnapshot: Sendable, Equatable {
    /// The last call's outcome (nil = nothing asked yet, or switched off).
    public var outcome: SourceHealth.Outcome?
    /// This Mac had a network at the last call.
    public var online: Bool
    /// The site's host as people read it ("ns.example.org"), empty while off.
    public var host: String
    /// The last 24 hours of its readings, oldest first.
    public var readings: [Reading]
    /// The site answered at least once since it was switched on: from then on its readings are monitored and losing
    /// it rings the no-data alert.
    public var monitoring: Bool
    /// This Mac's treatment log (live entries, oldest first): its own and, read-only, the site's.
    public var treatments: [Treatment]
    /// The ids that came from the site (read-only here: *From Nightscout · change it there*).
    public var pulled: Set<String>
    /// Treatments go both ways (the person's switch).
    public var treatmentsOn: Bool
    /// The site refused a treatment write: the token can only read (Sukrara keeps reading and stops sending).
    public var readOnly: Bool
    /// Entries written here that haven't reached the site as they are now.
    public var unsent: Int
    /// When the site last answered.
    public var lastAnswer: Date?
    /// The sensor the site's newest Sensor Start / Sensor Change started (nil = its life is unknown).
    public var sensor: WearerSensor?

    public init(
        outcome: SourceHealth.Outcome? = nil, online: Bool = true, host: String = "", readings: [Reading] = [],
        monitoring: Bool = false, treatments: [Treatment] = [], pulled: Set<String> = [], treatmentsOn: Bool = true,
        readOnly: Bool = false, unsent: Int = 0, lastAnswer: Date? = nil, sensor: WearerSensor? = nil
    ) {
        self.outcome = outcome
        self.online = online
        self.host = host
        self.readings = readings
        self.monitoring = monitoring
        self.treatments = treatments
        self.pulled = pulled
        self.treatmentsOn = treatmentsOn
        self.readOnly = readOnly
        self.unsent = unsent
        self.lastAnswer = lastAnswer
        self.sensor = sensor
    }

    public static let off = NightscoutSnapshot()

    /// The state the person sees at `now` (contracts/sources/health.json).
    public func health(at now: Date) -> SourceHealth.State {
        SourceHealth.state(
            outcome: outcome, online: online, newestAge: readings.last.map { now.timeIntervalSince($0.t) })
    }
}

/// The Nightscout source as the app sees it (`NightscoutSource`; tests use it over a fake `NightscoutHTTP`).
public protocol NightscoutSourcing: Sendable {
    /// Each call is one subscriber: the current snapshot first, then every change (only the newest is buffered).
    func snapshots() async -> AsyncStream<NightscoutSnapshot>
    /// Switches it on with `config`, or off (nil). The same config again changes nothing.
    func set(_ config: NightscoutConfig?) async
    /// Try Again (and the network came back): ask the site now instead of after the wait.
    func retry() async
    /// Whether this Mac has a network (the path monitor).
    func network(online: Bool) async
    /// Its stored readings since `since`, oldest first (31 days kept).
    func readings(since: Date) async -> [Reading]
    /// An entry this Mac adds, changes or deletes (a tombstone); false = not taken (read-only from the site, or off).
    func write(_ treatment: Treatment) async -> Bool
}

/// Reads a Nightscout site (docs/44 step 1): the last 24 hours of glucose at first and after a gap, then what's new,
/// asking again when the next reading is due (`NightscoutPoll`); with *Treatments both ways* the site's treatments
/// arrive read-only and this Mac's own go up (`NightscoutTreatments.plan`). Every reading goes into its own 31-day
/// store, the treatment log into `nightscout/state.json`; another site starts both again. HTTPS only (loopback http
/// for a site on this Mac), redirects never followed, the token or secret only ever to the site's own address.
public actor NightscoutSource: NightscoutSourcing {
    private let http: any NightscoutHTTP
    private let store: any ReadingsStoring
    private let state: JSONFileStore<NightscoutState>
    private let clock: any TimeSource
    private let sleep: @Sendable (Double) async throws -> Void
    private static let log = Logger(subsystem: SukraraIDs.appBundleID, category: "nightscout")
    /// How far back the first read (and a read after a gap) goes.
    static let backfill: TimeInterval = 86_400
    /// The JWT is refreshed this long before it expires.
    static let jwtMargin: TimeInterval = 120

    private var config: NightscoutConfig?
    private var task: Task<Void, Never>?
    private var current = NightscoutSnapshot.off
    private var subscribers: [UUID: AsyncStream<NightscoutSnapshot>.Continuation] = [:]
    /// Bumped by every config change and Try Again: a run of an older generation publishes nothing.
    private var generation = 0
    private var online = true
    private var jwt: (token: String, exp: TimeInterval)?
    /// The site refused a treatment write with this config: pulling goes on, pushing stops until the token changes.
    private var readOnly = false
    /// When the site's sensor events were last asked for (every `sensorEvery`; nil = not yet with this config).
    private var sensorChecked: Date?
    /// How often the newest Sensor Start / Sensor Change is asked for (the 24-hour window is read more often).
    static let sensorEvery: TimeInterval = 1800

    public init(
        http: any NightscoutHTTP, store: any ReadingsStoring, state: JSONFileStore<NightscoutState>,
        clock: any TimeSource = SystemClock(),
        sleep: @escaping @Sendable (Double) async throws -> Void = { try await Task.sleep(for: .seconds($0)) }
    ) {
        self.http = http
        self.store = store
        self.state = state
        self.clock = clock
        self.sleep = sleep
    }

    /// The source over the files under `directory` (`readings.csv`, `state.json`).
    public static func files(directory: URL, http: any NightscoutHTTP = URLSessionNightscoutHTTP()) -> NightscoutSource
    {
        NightscoutSource(
            http: http, store: ReadingsFileStore(directory: directory),
            state: JSONFileStore(url: directory.appending(path: "state.json", directoryHint: .notDirectory)))
    }

    public func snapshots() -> AsyncStream<NightscoutSnapshot> {
        let (stream, continuation) = AsyncStream.makeStream(
            of: NightscoutSnapshot.self, bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        subscribers[id] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.unsubscribe(id) } }
        continuation.yield(current)
        return stream
    }

    private func unsubscribe(_ id: UUID) { subscribers[id] = nil }

    public func set(_ config: NightscoutConfig?) async {
        guard config != self.config else { return }
        if config?.secret != self.config?.secret || config?.site != self.config?.site {
            jwt = nil
            readOnly = false
            sensorChecked = nil
        }
        self.config = config
        task?.cancel()
        task = nil
        generation += 1
        guard let config else {
            publish(.off)
            return
        }
        await open(config)
        start(config)
    }

    public func retry() async {
        guard let config else { return }
        task?.cancel()
        generation += 1
        start(config)
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
        if back { await retry() }
    }

    public func readings(since: Date) async -> [Reading] {
        (try? await store.load(since: since)) ?? []
    }

    public func write(_ treatment: Treatment) async -> Bool {
        guard let config else { return false }
        var s = await log(for: config)
        if s.ledger.pulled.contains(treatment.id) { return false }
        if let have = s.entries[treatment.id], have.u >= treatment.u { return true }
        var t = treatment
        t.o = .mac
        s.entries[t.id] = t
        try? await state.save(s)
        await publishLog(s)
        if config.treatments && !readOnly { await retry() }  // up now, not at the next reading
        return true
    }

    // MARK: - the loop

    private func start(_ config: NightscoutConfig) {
        let gen = generation
        task = Task { [weak self] in await self?.run(config, gen: gen) }
    }

    /// The site's log (another site's is dropped with its readings) and the snapshot's first values.
    private func open(_ config: NightscoutConfig) async {
        let s = await log(for: config)
        var snap = NightscoutSnapshot(
            online: online, host: NightscoutSite.parse(config.site).host, treatmentsOn: config.treatments)
        snap.readings = await day()
        fill(&snap, s)
        publish(snap)
    }

    /// The treatment log of `config`'s site, made new (readings too) when it belonged to another site.
    private func log(for config: NightscoutConfig) async -> NightscoutState {
        let root = NightscoutSite.parse(config.site).root
        if let s = await state.load(), s.site == root { return s }
        let old = await state.load()
        if old != nil { try? await store.removeAll() }
        let fresh = NightscoutState(site: root, device: old?.device ?? NightscoutTreatments.newDevice())
        try? await state.save(fresh)
        return fresh
    }

    private func run(_ config: NightscoutConfig, gen: Int) async {
        let site = NightscoutSite.parse(config.site)
        guard let root = site.url else {
            publish(outcome: .notFound, gen: gen)
            return
        }
        var failures = 0
        while !Task.isCancelled && gen == generation {
            var retryAfter: TimeInterval?
            var refused = false
            do {
                try await pull(root, config: config, gen: gen)
                failures = 0
            } catch let f as Failure {
                guard !Task.isCancelled, gen == generation else { return }
                failures += 1
                retryAfter = f.retryAfter
                refused = f.outcome == .refused
                if f.offline { online = false }
                Self.log.info("nightscout: \(f.outcome.rawValue, privacy: .public)")
                publish(outcome: f.outcome, gen: gen)
            } catch {
                return
            }
            let readings = current.readings
            let wait = NightscoutPoll.wait(
                now: clock.now.timeIntervalSince1970, newest: readings.last?.t.timeIntervalSince1970,
                previous: readings.dropLast().last?.t.timeIntervalSince1970, failures: failures,
                retryAfter: retryAfter, refused: refused)
            do { try await sleep(wait) } catch { return }
        }
    }

    /// One call's failure, classified (contracts/sources/health.json).
    struct Failure: Error {
        var outcome: SourceHealth.Outcome
        var retryAfter: TimeInterval?
        var offline = false
    }

    /// Readings since the newest stored one (24 hours at first and after a gap), then the treatments both ways.
    private func pull(_ root: URL, config: NightscoutConfig, gen: Int) async throws {
        let now = clock.now
        let newest = current.readings.last?.t
        let since = max(newest ?? .distantPast, now.addingTimeInterval(-Self.backfill))
        let minutes = Int(now.timeIntervalSince(since) / 60) + 10
        let body = try await call(
            NightscoutApi.entries(root, sinceMs: Int64(since.timeIntervalSince1970 * 1000), count: min(2000, minutes)),
            config: config)
        let s = await log(for: config)
        guard let json = try? JSONSerialization.jsonObject(with: body) else { throw Failure(outcome: .server) }
        let entries = NightscoutEntries.parse(json, device: s.device)
        let readings = entries.compactMap { e -> Reading? in
            // a clock ahead of this Mac's: dropped (no reading from the future); LO / HI as the flags
            guard e.mgdl >= 1, e.mgdl <= 1000, e.t <= now.timeIntervalSince1970 + 60 else { return nil }
            return GlucoseRange.normalised(e.reading)
        }
        if !readings.isEmpty { try? await store.merge(readings) }
        guard gen == generation else { return }
        var snap = current
        snap.readings = await day()
        snap.outcome = .ok
        snap.online = true
        online = true
        snap.monitoring = true
        snap.lastAnswer = now
        publish(snap)
        let window = config.treatments ? await syncTreatments(root, config: config, gen: gen) : nil
        await readSensor(root, window: window, config: config, gen: gen)
    }

    /// The sensor's life from the site's Sensor Start / Sensor Change (contracts/sources/nightscout/sensor-life.json),
    /// whether or not treatments go both ways: the newest of each asked for at the first call and every 30 min (the
    /// answer then holds, a deleted one included); in between, a newer one in the 24-hour window read for the
    /// treatments. Kept in `state.json`. A failure keeps the last one known.
    private func readSensor(_ root: URL, window: [[String: Any]]?, config: NightscoutConfig, gen: Int) async {
        let now = clock.now
        var s = await log(for: config)
        var life = s.sensor
        if sensorChecked.map({ now.timeIntervalSince($0) >= Self.sensorEvery }) ?? true {
            var docs = window ?? []
            for event in ["Sensor Start", "Sensor Change"] {
                guard let body = try? await call(NightscoutApi.newest(root, eventType: event), config: config),
                    let list = (try? JSONSerialization.jsonObject(with: body)) as? [[String: Any]]
                else { return }
                docs += list
            }
            guard gen == generation else { return }
            sensorChecked = now
            life = NightscoutSensorLife.life(site: docs, now: now.timeIntervalSince1970)
        } else if let newer = NightscoutSensorLife.life(site: window ?? [], now: now.timeIntervalSince1970),
            newer.start > life?.start ?? .min || (newer.start == life?.start && newer.id != life?.id)
        {
            life = newer
        }
        if life != s.sensor {
            s.sensor = life
            try? await state.save(s)
        }
        guard current.sensor != life?.sensor else { return }
        var snap = current
        snap.sensor = life?.sensor
        publish(snap)
    }

    /// The site's treatments of the last 24 hours against this Mac's log (`NightscoutTreatments.plan`). A failure
    /// here never stops the glucose: the next round tries again.
    /// Returns the documents read (nil when the read failed), for the sensor's life.
    @discardableResult
    private func syncTreatments(_ root: URL, config: NightscoutConfig, gen: Int) async -> [[String: Any]]? {
        let now = clock.now.timeIntervalSince1970
        let from = now - Self.backfill
        guard let body = try? await call(NightscoutApi.treatments(root, since: from), config: config),
            let docs = (try? JSONSerialization.jsonObject(with: body)) as? [[String: Any]], gen == generation
        else { return nil }
        var s = await log(for: config)
        let plan = NightscoutTreatments.plan(
            site: docs, local: Array(s.entries.values), ledger: s.ledger, device: s.device,
            window: Int64(from)...Int64(now + 600))
        let ms = Int64(now * 1000)
        for e in plan.upsert {
            if let sent = s.ledger.pushed[e.id], var own = s.entries[e.id] {
                // ours, changed on the site: its version, at the version that went up (nothing to send back)
                own.k = e.k
                own.a = e.a
                own.t = e.t
                own.n = Treatment.truncated(e.n)
                own.u = sent
                s.entries[e.id] = own
            } else {
                s.entries[e.id] = Treatment(id: e.id, k: e.k, a: e.a, t: e.t, n: e.n, u: ms, o: .phone)
                s.ledger.pulled.insert(e.id)
            }
        }
        for id in plan.delete {
            s.entries[id]?.del = true
            s.entries[id]?.u = ms
            s.ledger.pushed[id] = nil
        }
        try? await state.save(s)
        await publishLog(s)
        guard !readOnly, !(plan.push.isEmpty && plan.remove.isEmpty) else { return docs }
        for id in plan.push {
            guard let t = s.entries[id], gen == generation else { return docs }
            switch await upsert(root, t, device: s.device, config: config) {
            case .ok: s.ledger.pushed[id] = t.u
            case .refused: readOnly = true
            case .failed: break
            }
            if readOnly { break }
        }
        if !readOnly {
            for id in plan.remove where gen == generation {
                switch await remove(root, identifier: id, config: config) {
                case .ok: s.ledger.pushed[id] = nil
                case .refused: readOnly = true
                case .failed: break
                }
                if readOnly { break }
            }
        }
        try? await state.save(s)
        await publishLog(s)
        return docs
    }

    /// Upsert by identifier (as Android's upload): the site's copies that moved or changed kind are deleted, then the
    /// document goes up. The outcome: ok, refused (the token can only read), or another failure.
    private func upsert(_ root: URL, _ t: Treatment, device: String, config: NightscoutConfig) async -> WriteResult {
        let doc = NightscoutTreatments.document(t, device: device)
        do {
            for old in try await copies(root, identifier: t.id, config: config) {
                let moved = old["created_at"] as? String != doc["created_at"] as? String
                let retyped = old["eventType"] as? String != doc["eventType"] as? String
                if moved || retyped, let id = old["_id"] as? String {
                    _ = try await call(NightscoutApi.delete(root, documentId: id), config: config)
                }
            }
            let data = try JSONSerialization.data(withJSONObject: [doc])
            _ = try await call(NightscoutApi.post(root, treatment: data), config: config)
            return .ok
        } catch let f as Failure {
            return f.outcome == .refused ? .refused : .failed
        } catch {
            return .failed
        }
    }

    private func remove(_ root: URL, identifier: String, config: NightscoutConfig) async -> WriteResult {
        do {
            for old in try await copies(root, identifier: identifier, config: config) {
                if let id = old["_id"] as? String {
                    _ = try await call(NightscoutApi.delete(root, documentId: id), config: config)
                }
            }
            return .ok
        } catch let f as Failure {
            return f.outcome == .refused ? .refused : .failed
        } catch {
            return .failed
        }
    }

    private enum WriteResult { case ok, refused, failed }

    private func copies(_ root: URL, identifier: String, config: NightscoutConfig) async throws -> [[String: Any]] {
        let body = try await call(NightscoutApi.treatments(root, identifier: identifier), config: config)
        return ((try? JSONSerialization.jsonObject(with: body)) as? [[String: Any]] ?? []).filter { $0["_id"] != nil }
    }

    // MARK: - calls

    /// One signed call; with a token a 401 gets a fresh JWT and one more try. Throws the classified failure.
    private func call(_ request: URLRequest, config: NightscoutConfig) async throws -> Data {
        var answer = try await send(request, headers: try await auth(config, fresh: false))
        if answer.status == 401, case .token = NightscoutAuth.of(config.secret) {
            answer = try await send(request, headers: try await auth(config, fresh: true))
        }
        if let outcome = NightscoutApi.outcome(status: answer.status) {
            throw Failure(outcome: outcome, retryAfter: NightscoutApi.retryAfter(answer.headers, now: clock.now))
        }
        return answer.body
    }

    private func send(_ request: URLRequest, headers: [String: String]) async throws -> (
        status: Int, body: Data, headers: [String: String]
    ) {
        var r = request
        for (k, v) in headers { r.setValue(v, forHTTPHeaderField: k) }
        do {
            return try await http.send(r)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if Task.isCancelled { throw CancellationError() }
            throw Failure(outcome: .network, offline: NightscoutApi.isOffline(error))
        }
    }

    /// The headers that sign a call: the hashed API secret, or `Bearer` with a JWT for the token (asked when there is
    /// none, it is about to expire, or `fresh`).
    private func auth(_ config: NightscoutConfig, fresh: Bool) async throws -> [String: String] {
        switch NightscoutAuth.of(config.secret) {
        case .secret(let secret): return secret.isEmpty ? [:] : NightscoutApi.secretHeaders(secret)
        case .token(let token):
            let now = clock.now.timeIntervalSince1970
            if !fresh, let jwt, now < jwt.exp - Self.jwtMargin { return ["Authorization": "Bearer \(jwt.token)"] }
            guard let root = NightscoutSite.parse(config.site).url else { throw Failure(outcome: .notFound) }
            let answer = try await send(NightscoutApi.jwt(root, token: token), headers: [:])
            if let outcome = NightscoutApi.outcome(status: answer.status) {
                throw Failure(outcome: outcome, retryAfter: NightscoutApi.retryAfter(answer.headers, now: clock.now))
            }
            guard let obj = (try? JSONSerialization.jsonObject(with: answer.body)) as? [String: Any],
                let token = obj["token"] as? String
            else { throw Failure(outcome: .server) }
            let exp = (obj["exp"] as? NSNumber)?.doubleValue ?? now + 3600
            jwt = (token, exp)
            return ["Authorization": "Bearer \(token)"]
        }
    }

    // MARK: - publishing

    private func publish(_ snapshot: NightscoutSnapshot) {
        current = snapshot
        for c in subscribers.values { c.yield(snapshot) }
    }

    private func publish(outcome: SourceHealth.Outcome, gen: Int) {
        guard gen == generation else { return }
        var s = current
        s.outcome = outcome
        s.online = online
        publish(s)
    }

    private func publishLog(_ log: NightscoutState) async {
        var s = current
        fill(&s, log)
        guard s != current else { return }
        publish(s)
    }

    private func fill(_ snap: inout NightscoutSnapshot, _ log: NightscoutState) {
        snap.sensor = log.sensor?.sensor
        snap.treatments = log.live
        snap.pulled = log.ledger.pulled
        snap.readOnly = readOnly
        snap.unsent = log.unsent
    }

    private func day() async -> [Reading] {
        (try? await store.load(since: clock.now.addingTimeInterval(-86_400))) ?? []
    }
}
