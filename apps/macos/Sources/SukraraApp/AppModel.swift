// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation
import SukraraAlerts
import SukraraCore
import SukraraGraph
import SukraraShare

/// The state every surface of the menu bar app renders, composed of small controllers. One instance, owned by
/// `AppDelegate`. Data comes in through `apply(…)` (the follower runtime); the controllers exist only when the
/// launch plan allows their service (an offline run has no alert controller, no update check, no login item).
@Observable
public final class AppModel {
    public let plan: LaunchPlan
    /// The monitored readings of the last 24 hours, oldest first: the followed wearer's, else Juggluco's on this
    /// network.
    private(set) var readings: [Reading] = []
    /// The source's stored history (up to 31 days), loaded when monitoring starts and again when the panel opens: the
    /// panel's chart scrolls through it, and every surface's 7 / 14 / 30-day slots are counted over it (`slots`).
    private(set) var history: [Reading] = []
    /// Every slot's numbers over `allReadings`, counted on each new reading, history load and card settings change:
    /// the menu bar, panel and widgets show the same rings.
    private(set) var slots: SlotNumbers?
    /// Juggluco on this network (nil when the plan doesn't allow it: dev, fixture and snapshot runs).
    @ObservationIgnored let lan: (any LanSourcing)?
    /// What the Juggluco source last said.
    private(set) var lanSnapshot = LanSnapshot.off
    /// The person's Juggluco switch, address and secret (nil = off). Following someone pauses it: never two people's
    /// readings mixed.
    var lanConfig: LanConfig? {
        didSet {
            let wasMonitoring = monitoring
            syncLan()
            guard monitoring != wasMonitoring else { return }
            // switched off: its readings (and its sensor) leave the card and the alerts at once
            monitoringChanged(from: wasMonitoring)
            applySensor()
            apply(readings: monitoredReadings)
            feedWidget()
        }
    }
    @ObservationIgnored private var lanSent: LanConfig?
    @ObservationIgnored private var lanTask: Task<Void, Never>?
    /// Where `history` was loaded from.
    @ObservationIgnored private var historySource = HistorySource.none
    private(set) var sensor: SensorInfo?
    /// The wearer's live treatments as the chart's marks, oldest first.
    private(set) var marks: [GraphMark] = []
    /// The wearer's live treatments (the CSV export).
    private(set) var treatments: [Treatment] = []
    private(set) var link: LinkState = .notFollowing
    /// The wearer's readings feed the card and the alert rules: following, or following with the Keychain retrying.
    private var followMonitoring = false
    /// The Keychain won't read the follower key; the runtime retries (and the alerts go on over the stored readings).
    private(set) var keychainUnreadable = false
    /// The Keychain retrying while the wearer's stored readings are still alerted on: the panel shows the card under
    /// its line.
    var alertingOnLastData: Bool { keychainUnreadable && followMonitoring }
    /// The wearer's last 24 hours (while `followMonitoring`).
    @ObservationIgnored private var followReadings: [Reading] = []
    /// The wearer's sensor from the follower snapshots.
    @ObservationIgnored private var followSensor: WearerSensor?
    private var wearerName: String?
    /// The follower's first snapshot arrived (whether this Mac follows someone is known).
    @ObservationIgnored private var followerKnown = false
    /// Juggluco's readings feed the card and the alert rules: switched on, not following, and reached at least once.
    private var lanMonitoring: Bool { !followMonitoring && lanSent != nil && lanSnapshot.monitoring }
    private var monitoring: Bool { followMonitoring || lanMonitoring }
    var unit: GlucoseUnit = .mgdl
    /// The card's settings (bands, hours…): this Mac's, or the wearer's mirrored ones (`GraphConfig.load`).
    var graph = GraphConfig() {
        didSet { if graph != oldValue { refreshSlots() } }
    }
    /// The widgets' card settings: the same, with the Widgets place's own time shown and sensor time left.
    var widgetGraph = GraphConfig() {
        didSet { if widgetGraph != oldValue { refreshSlots() } }
    }
    /// "Now" for the card: moved by the freshness ticker at each freshness step, so a value greys and strikes on time.
    private(set) var now: Date
    var notificationPermission: NotificationPermission = .allowed
    /// Alerts can't break through Focus / Do Not Disturb (no Time Sensitive permission): the panel says so.
    var focusCanHoldAlerts = false

    @ObservationIgnored let alerts: AlertController?
    @ObservationIgnored let updates: UpdateCheck?
    @ObservationIgnored let loginItem: LoginItem?
    @ObservationIgnored let follower: (any FollowerService)?
    /// The widgets' data (nil when the plan doesn't allow the widget feed: dev and snapshot runs).
    @ObservationIgnored let widget: WidgetFeed?
    /// Counts settings changes, so each one reloads the widgets (`feedWidget`).
    @ObservationIgnored private var widgetSettings = 0
    @ObservationIgnored private let clock: any TimeSource
    @ObservationIgnored private var ticker: Task<Void, Never>?
    @ObservationIgnored private var feeds: [Task<Void, Never>] = []
    /// Every follower snapshot, after the model took its part (the windows and the mirrored alert settings).
    @ObservationIgnored var onSnapshot: ((FollowerSnapshot) -> Void)?

    init(
        plan: LaunchPlan, clock: any TimeSource = SystemClock(), alerts: AlertController? = nil,
        updates: UpdateCheck? = nil, loginItem: LoginItem? = nil, follower: (any FollowerService)? = nil,
        widget: WidgetFeed? = nil, lan: (any LanSourcing)? = nil
    ) {
        self.plan = plan
        self.clock = clock
        self.alerts = alerts
        self.updates = updates
        self.loginItem = loginItem
        self.follower = follower
        self.widget = widget
        self.lan = lan
        now = clock.now
        alerts?.onUIChange = { [weak self] in
            self?.feedWidget()
            self?.restartTicker()  // a mute started or ended: its countdown ticks in the tooltip
        }
    }

    /// Starts the follower runtime and feeds its snapshots and the wearer's alerts into the model and the alert rules.
    func run() {
        guard feeds.isEmpty else { return }
        if let lan {
            feeds.append(
                Task { [weak self] in
                    for await snapshot in await lan.snapshots() { self?.apply(lan: snapshot) }
                })
        }
        guard let follower else { return }
        feeds.append(
            Task { [weak self] in
                for await snapshot in await follower.snapshots() { self?.apply(snapshot) }
            })
        feeds.append(
            Task { [weak self] in
                for await alert in await follower.alerts() { self?.alerts?.wearerAlert(alert) }
            })
        Task { await follower.start() }
    }

    /// One follower snapshot: the link, readings, sensor and treatments, and whether the wearer's alerts are mirrored.
    func apply(_ snapshot: FollowerSnapshot) {
        let wasMonitoring = monitoring
        link = snapshot.link
        keychainUnreadable = snapshot.keychainUnreadable
        // an unreadable Keychain (retrying) still follows the wearer: keep alerting on their readings, no-data included
        followMonitoring = link.isFollowing || (snapshot.keychainUnreadable && !snapshot.readings.isEmpty)
        followReadings = followMonitoring ? snapshot.readings : []
        followSensor = snapshot.sensor
        wearerName = Profile.shownName(snapshot.wearer?.name)
        followerKnown = true
        syncLan()
        monitoringChanged(from: wasMonitoring)
        if let alerts {
            alerts.setMirroring(followMonitoring && alerts.settings.source == .wearer)
            alerts.treatments(
                snapshot.treatments.compactMap { t in
                    let kind: TreatmentKind? = t.k == .carbs ? .carbs : t.k == .rapid ? .rapidInsulin : nil
                    guard !t.del, let kind else { return nil }
                    return AlertTreatment(kind: kind, at: Date(timeIntervalSince1970: TimeInterval(t.t)))
                })
        }
        marks = followMonitoring ? CardAlertsMapping.marks(snapshot.treatments) : []
        treatments = followMonitoring ? snapshot.treatments.filter { !$0.del } : []
        applySensor()
        // only a followed wearer's (or, not following, Juggluco's) readings are shown or alerted on
        apply(readings: monitoredReadings)
        // dropped out (the wearer stopped sharing, the follow broke): say so even with the panel shut; the person's
        // own Stop following needs no note
        if wasMonitoring && !monitoring {
            if case .notFollowing = link {
                alerts?.monitoringStopped(nil)
            } else if let reason = notMonitoringReason {
                alerts?.monitoringStopped(reason)
            }
        }
        onSnapshot?(snapshot)  // first: it loads the wearer's mirrored card settings the widget draws with
        feedWidget()
    }

    /// What the Juggluco source said: its readings feed the card and the alerts while nobody is followed.
    func apply(lan snapshot: LanSnapshot) {
        let wasMonitoring = monitoring
        lanSnapshot = snapshot
        monitoringChanged(from: wasMonitoring)
        guard !followMonitoring else { return }
        applySensor()
        apply(readings: monitoredReadings)
        feedWidget()
    }

    /// The readings the card and the alert rules use: following wins over Juggluco (never two people's mixed).
    private var monitoredReadings: [Reading] {
        followMonitoring ? followReadings : lanMonitoring ? lanSnapshot.readings : []
    }

    /// The sensor of the same source: the wearer's, else the one Juggluco reads.
    private var monitoredSensor: WearerSensor? {
        followMonitoring ? followSensor : lanMonitoring ? lanSnapshot.sensor : nil
    }

    /// The card's sensor life and the alert rules' sensor window follow the monitored source.
    private func applySensor() {
        let s = monitoredSensor
        alerts?.sensor(
            s.map {
                SensorWindow(
                    start: Date(timeIntervalSince1970: TimeInterval($0.start)),
                    end: Date(timeIntervalSince1970: TimeInterval($0.end)),
                    warmupEnd: Date(timeIntervalSince1970: TimeInterval($0.warmupEnd)))
            })
        sensor = s.map {
            SensorInfo(start: TimeInterval($0.start), end: TimeInterval($0.end), warmupEnd: TimeInterval($0.warmupEnd))
        }
    }

    /// Monitoring started, stopped or moved to the other source: the alert rules forget the old one and the history
    /// is loaded from the new one.
    private func monitoringChanged(from wasMonitoring: Bool) {
        if monitoring && !wasMonitoring { alerts?.monitoringStopped(nil) }  // back: the "stopped" note goes
        let source: HistorySource = followMonitoring ? .follow : lanMonitoring ? .lan : .none
        // stopped, or straight from Juggluco to a wearer (or back): the episodes were about the other readings
        if wasMonitoring && (!monitoring || (historySource != .none && source != historySource)) {
            alerts?.resetMonitoring()
        }
        guard source != historySource else { return }
        historySource = source
        history = []
        loadHistory()
    }

    /// Hands the switch's state to the Juggluco source: on with its config while nobody is followed, else off.
    /// Waits for the follower's first snapshot (when there is a follower), so a launch while following never starts
    /// Juggluco first.
    private func syncLan() {
        guard lan != nil else { return }
        let want = followMonitoring || (follower != nil && !followerKnown) ? nil : lanConfig
        guard want != lanSent else { return }
        lanSent = want
        toLan { await $0.set(want) }
    }

    /// Runs `step` on the Juggluco source after every earlier one (switching, Try Again and stop stay in order).
    private func toLan(_ step: @escaping @Sendable (any LanSourcing) async -> Void) {
        guard let lan else { return }
        let previous = lanTask
        lanTask = Task {
            await previous?.value
            await step(lan)
        }
    }

    /// The wearer's name while following pauses Juggluco (nil = not paused).
    var lanPausedFor: String? {
        followMonitoring ? (wearerName ?? L10n.t("the wearer")) : nil
    }

    /// Try Again on the Juggluco source.
    func retryLan() {
        guard lanSent != nil else { return }
        toLan { await $0.retry() }
    }

    /// Hands the card's current input to the widgets: every follower snapshot, every alert badge change, and the unit
    /// or card settings changing (`settingsChanged`: the widget redraws at once, a settings change is rare and
    /// deliberate). The widget ages the value by itself, so the freshness ticker doesn't feed it.
    func feedWidget(settingsChanged: Bool = false) {
        guard let widget else { return }
        if settingsChanged { widgetSettings += 1 }
        let data = cardData
        let look = self.look  // the menu bar's face: the same value text and state the widget shows
        let state =
            "\(monitoring ? "monitoring" : "idle") \(look.state.rawValue) \(data.alerts.status) settings \(widgetSettings)"
        widget.update(
            WidgetSnapshot(writtenAt: clock.now.timeIntervalSince1970, data: data, config: widgetGraph, unit: unit),
            shown: .init(value: look.text ?? "—", state: state))
    }

    public convenience init(plan: LaunchPlan) { self.init(plan: plan, clock: SystemClock()) }

    // MARK: inputs

    /// New readings (the stored history at launch, then each new one). The alert rules get the newest, also an old
    /// one: "no data" is measured from it.
    func apply(readings new: [Reading]) {
        let sorted = new.sorted { $0.t < $1.t }
        // readings leaving the last 24 hours join the history, so the month stays whole while the panel stays shut
        if historySource != .none, let first = sorted.first?.t {
            let after = history.last?.t ?? .distantPast
            history += readings.filter { $0.t < first && $0.t > after }
            let oldest = clock.now.addingTimeInterval(-ChartView.maxSpan)
            if let firstKept = history.firstIndex(where: { $0.t >= oldest }), firstKept > 0 {
                history.removeFirst(firstKept)
            }
        }
        readings = sorted
        now = clock.now
        if let latest = readings.last { alerts?.reading(latest) }
        refreshSlots()
        restartTicker()
    }

    func apply(sensor info: SensorInfo?) {
        sensor = info
        restartTicker()
    }

    // MARK: what the surfaces show

    /// The card's data at `now`, from the stores only (never made-up values on a release build).
    var cardData: CardData {
        CardData(
            readings: readings.map { GraphReading(t: $0.t.timeIntervalSince1970, v: Double($0.mgdl), rate: $0.rate) },
            marks: marks, sensor: sensor,
            alerts: cardAlerts, now: now.timeIntervalSince1970, slots: slots)
    }

    /// The card's alert decision: the engine's state, or while mirroring the wearer's alerts the same rules over the
    /// latest reading (this Mac's engine then runs only no data).
    private var cardAlerts: CardAlerts {
        guard let alerts, alerts.mirroring else {
            return CardAlertsMapping.of(alerts?.ui ?? .idle, settings: alerts?.settings, marks: marks, now: now)
        }
        let latest = readings.last.map { GlucoseSample(at: $0.t, mgdl: Double($0.mgdl), rate: $0.rate) }
        return CardAlertsMapping.mirrored(
            latest: latest, settings: alerts.settings, marks: marks, sideMutes: alerts.mirroredSideMutes(at: now),
            muteAll: alerts.ui.muteAllUntil, now: now)
    }

    /// The panel card's data: `cardData` with the stored history before the snapshot's readings, so the chart
    /// scrolls and zooms through up to 31 days.
    var panelData: CardData {
        var data = cardData
        guard !history.isEmpty else { return data }
        data.readings = Self.graph(allReadings)
        return data
    }

    /// The stored history and the last 24 hours, oldest first (the report, the export and the slots).
    var allReadings: [Reading] {
        guard let first = readings.first?.t else { return history }
        return history.prefix { $0.t < first } + readings
    }

    static func graph(_ readings: [Reading]) -> [GraphReading] {
        readings.map { GraphReading(t: $0.t.timeIntervalSince1970, v: Double($0.mgdl), rate: $0.rate) }
    }

    /// Counts every slot's numbers over the whole history once (the card surfaces only look them up).
    private func refreshSlots() {
        let all = monitoring ? allReadings : []
        let new =
            all.isEmpty
            ? nil
            : SlotNumbers.of(Self.graph(all), now: now.timeIntervalSince1970, configs: [graph, widgetGraph])
        if new != slots { slots = new }
    }

    /// Loads the monitored source's stored history (31 days): when monitoring starts and each time the panel opens.
    func loadHistory() {
        let source = historySource
        guard source != .none else {
            history = []
            refreshSlots()
            return
        }
        let since = clock.now.addingTimeInterval(-ChartView.maxSpan)
        let follower = self.follower
        let lan = self.lan
        Task { [weak self] in
            let loaded: [Reading] =
                switch source {
                case .follow: await follower?.readings(since: since) ?? []
                case .lan: await lan?.readings(since: since) ?? []
                case .none: []
                }
            guard let self, self.historySource == source else { return }
            let sorted = loaded.sorted { $0.t < $1.t }
            guard sorted != self.history else { return }
            self.history = sorted
            self.refreshSlots()
            self.feedWidget()
        }
    }

    /// Nobody is followed and nothing is on its way (not following, declined, revoked, invite used, broken).
    var needsFollow: Bool {
        switch link {
        case .following, .requested, .joining: false
        default: true
        }
    }

    /// A low alert is up (Add treatment then puts the focus on Carbs).
    var isLow: Bool { alerts?.ui.active[.level]?.kind.isBelowThreshold == true }

    var look: GlucoseLook { GlucoseCard.face(cardData, unit: unit, surface: .menubarTitle) }

    var menubarTitle: MenubarTitle {
        let data = cardData
        return MenubarTitle.of(
            look: GlucoseCard.face(data, unit: unit, surface: .menubarTitle), data: data, config: graph,
            spoken: GlucoseCard.accessibilityLabel(cardData, unit: unit, surface: .menubarTitle, config: graph),
            alerts: alerts?.ui ?? .idle, reason: notMonitoringReason)
    }

    /// The menu bar title text (tests, VoiceOver fallbacks).
    public var statusTitle: String { menubarTitle.text }

    /// Why nothing is monitored, in words (the panel's first line), or nil while a value can be shown. Before the
    /// first reading the app says so instead of "no data" — and no no-data alarm is armed.
    public var notMonitoringReason: String? {
        if plan.isOffline && plan.mode != .fixture {
            return L10n.t("Not monitoring: offline development build (no sensor, internet or alerts)")
        }
        // shown over the stored readings too: with the Keychain unreadable no new reading can arrive, and the alerts
        // go on over the last ones (no data included) while the runtime retries
        if keychainUnreadable, !lanMonitoring {
            return followMonitoring
                ? L10n.t("Can't read the follower key from the Keychain. Retrying; alerting on the last data.")
                : L10n.t("Not monitoring: can't read the follower key from the Keychain. Retrying…")
        }
        if case .broken(let why) = link, !lanMonitoring { return L10n.t("Not monitoring: \(why)") }
        guard readings.isEmpty else { return nil }
        if !followMonitoring, lanSent != nil, let why = Self.lanReason(lanSnapshot.state) { return why }
        switch link {
        case .notFollowing, .joining: return L10n.t("Not monitoring: not following anyone yet")
        case .requested: return L10n.t("Not monitoring: waiting for the wearer to allow this Mac")
        case .following: return L10n.t("Waiting for the first reading from the wearer")
        case .declined: return L10n.t("Not monitoring: the wearer declined")
        case .inviteUsed: return L10n.t("Not monitoring: the invite was used by another device")
        case .revoked: return L10n.t("Not monitoring: the wearer stopped sharing")
        case .broken(let why): return L10n.t("Not monitoring: \(why)")
        }
    }

    /// Why Juggluco gives no reading yet, in words (nil when it is off).
    static func lanReason(_ state: LanState) -> String? {
        switch state {
        case .off: nil
        case .looking: L10n.t("Not monitoring: looking for Juggluco on this network")
        case .notFound: L10n.t("Not monitoring: Juggluco wasn't found on this network")
        case .connected: L10n.t("Waiting for the first reading from Juggluco")
        case .unreachable(let host): L10n.t("Not monitoring: can't reach Juggluco at \(host)")
        case .unauthorized: L10n.t("Not monitoring: Juggluco refused the API secret")
        case .refused(let typed): L10n.t("Not monitoring: \(typed) isn't an address on your home network")
        case .localNetworkDenied: L10n.t("Not monitoring: macOS doesn't let Sukrara use the local network")
        }
    }

    // MARK: freshness ticker

    /// Wakes at the next freshness step of the newest reading (fresh → late → stale → old), at the sensor's end and
    /// at each minute of its warm-up, so the title and card change exactly then. No `Timer`: one task, replaced on
    /// every new reading.
    private func restartTicker() {
        ticker?.cancel()
        let nowT = clock.now.timeIntervalSince1970
        guard
            let next = Self.nextWake(
                latest: readings.last?.t.timeIntervalSince1970, sensor: sensor,
                muteAllUntil: alerts?.ui.muteAllUntil?.timeIntervalSince1970, now: nowT)
        else { return }
        let wait = max(0.5, next - nowT)
        ticker = Task { [weak self] in
            try? await Task.sleep(for: .seconds(wait), tolerance: .seconds(1))
            guard !Task.isCancelled, let self else { return }
            self.now = self.clock.now
            self.restartTicker()
        }
    }

    /// The next moment the title changes by itself: a freshness step, the sensor's end, the warm-up's next whole
    /// minute (its figure counts down without readings), or the Mute all countdown's next minute (the tooltip's
    /// "Alerts muted for N min"). nil = nothing more will change.
    static func nextWake(
        latest: TimeInterval?, sensor: SensorInfo?, muteAllUntil: TimeInterval? = nil, now: TimeInterval
    ) -> TimeInterval? {
        var times = [Freshness.nextChangeAt(latest, now: now)].compactMap { $0 }
        if let until = muteAllUntil, until > now, until < muteAllForever.timeIntervalSince1970 {
            // minutes left round up: the figure drops as each whole minute before the end passes
            let left = until - now
            let step = left.truncatingRemainder(dividingBy: 60)
            times.append(now + (step > 0 ? step : 60) + 0.5)
        }
        if let s = sensor {
            if s.end > now { times.append(s.end + 0.5) }
            if s.start <= now, s.warmupEnd + 15 * 60 > now, latest.map({ $0 < s.start }) ?? true {
                // the figure rounds the time left up to whole minutes: it changes as each minute before the end passes
                let left = s.warmupEnd - now
                times.append(left > 0 ? now + left.truncatingRemainder(dividingBy: 60) + 0.5 : s.warmupEnd + 15 * 60)
            }
        }
        return times.min()
    }

    /// The Mac woke or the clock changed: re-read the time, re-check the alerts.
    func networkCameBack() {
        if let follower { Task { await follower.reconnect() } }
        retryLan()  // Juggluco's stream died with the network too: look again now, not after the backoff
    }

    /// The clock changed (also small NTP corrections): re-read the time and re-check the alerts; the connections stay.
    func systemTimeChanged() {
        now = clock.now
        alerts?.tick()
        restartTicker()
    }

    /// The clock's look changed (24-hour time, time zone): the card's times and the widgets are drawn again.
    func clockStyleChanged() {
        now = clock.now
        feedWidget(settingsChanged: true)
    }

    /// The Mac woke: the time moved and the sockets are likely dead; reconnect now instead of after the watchdog.
    func didWake() {
        systemTimeChanged()
        networkCameBack()
    }

    /// Stops what this model started and returns once the follower runtime and the Juggluco source have stopped
    /// (their sockets closed, their state saved), so quitting doesn't cut them off halfway.
    func stop() async {
        ticker?.cancel()
        feeds.forEach { $0.cancel() }
        feeds = []
        alerts?.stop()
        lanSent = nil
        toLan { await $0.set(nil) }
        await lanTask?.value
        await follower?.stop()
    }
}

/// Where the history was loaded from.
private enum HistorySource {
    case none, follow, lan
}
