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
/// launch plan allows their service (an offline run has no alert controller, no updater, no login item).
@Observable
public final class AppModel {
    public let plan: LaunchPlan
    /// The monitored readings of the last 24 hours, oldest first: the followed wearer's, Juggluco's on this network or
    /// the Nightscout site's.
    private(set) var readings: [Reading] = [] {
        didSet {
            cardReadings = Self.graph(readings)
            panelReadings = Self.graph(allReadings)
        }
    }
    /// The source's stored history (up to 31 days), loaded when monitoring starts and again when the panel opens: the
    /// panel's chart scrolls through it, and every surface's 7 / 14 / 30-day slots are counted over it (`slots`).
    private(set) var history: [Reading] = [] {
        didSet {
            historyVersion += 1
            panelReadings = Self.graph(allReadings)
        }
    }
    /// `readings` and `allReadings` as the card draws them, made once per change: the panel's chart redraws on every
    /// scroll step and must not convert a month of readings each time.
    private var cardReadings: [GraphReading] = []
    private var panelReadings: [GraphReading] = []
    /// Counts the changes of `history`: a trend worked out off the main thread lands only on the history it was for.
    @ObservationIgnored private var historyVersion = 0
    /// Every slot's numbers over `allReadings`, counted on each new reading, history load and card settings change:
    /// the menu bar, panel and widgets show the same rings.
    private(set) var slots: SlotNumbers?
    /// Juggluco on this network (nil when the plan doesn't allow it: dev, fixture and snapshot runs).
    @ObservationIgnored let lan: (any LanSourcing)?
    /// What the Juggluco source last said.
    private(set) var lanSnapshot = LanSnapshot.off
    /// Juggluco's address and secret while it is the source (nil = another source).
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
    /// The Nightscout source (nil when the plan doesn't allow it: dev, fixture and snapshot runs).
    @ObservationIgnored let nightscout: (any NightscoutSourcing)?
    /// What the Nightscout source last said.
    private(set) var nightscoutSnapshot = NightscoutSnapshot.off
    /// The site, token and treatments switch while Nightscout is the source (nil otherwise, or no site yet).
    var nightscoutConfig: NightscoutConfig? {
        didSet {
            guard nightscoutConfig != oldValue else { return }
            let wasMonitoring = monitoring
            syncNightscout()
            applyTreatments()
            guard monitoring != wasMonitoring else { return }
            monitoringChanged(from: wasMonitoring)
            applySensor()
            apply(readings: monitoredReadings)
            feedWidget()
        }
    }
    @ObservationIgnored private var nightscoutSent: NightscoutConfig?
    @ObservationIgnored private var nightscoutTask: Task<Void, Never>?
    /// Every Nightscout snapshot, after the model took its part (the treatment windows).
    @ObservationIgnored var onNightscout: ((NightscoutSnapshot) -> Void)?
    /// The LibreLinkUp source (nil when the plan doesn't allow it: dev, fixture and snapshot runs).
    @ObservationIgnored let libreLinkUp: (any LibreLinkUpSourcing)?
    /// What the LibreLinkUp source last said.
    private(set) var libreLinkUpSnapshot = LibreLinkUpSnapshot.off
    /// Whom LibreLinkUp reads while it is the source (nil otherwise, or no one picked yet).
    var libreLinkUpConfig: LibreLinkUpConfig? {
        didSet {
            guard libreLinkUpConfig != oldValue else { return }
            let wasMonitoring = monitoring
            syncLibreLinkUp()
            applyTreatments()
            guard monitoring != wasMonitoring else { return }
            monitoringChanged(from: wasMonitoring)
            applySensor()
            apply(readings: monitoredReadings)
            feedWidget()
        }
    }
    @ObservationIgnored private var libreLinkUpSent: LibreLinkUpConfig?
    @ObservationIgnored private var libreLinkUpTask: Task<Void, Never>?
    /// Every LibreLinkUp snapshot, after the model took its part (Settings › Source, setup's Connect, the treatment
    /// windows).
    @ObservationIgnored var onLibreLinkUp: ((LibreLinkUpSnapshot) -> Void)?
    /// Every Juggluco snapshot, after the model took its part (the treatment windows).
    @ObservationIgnored var onLan: ((LanSnapshot) -> Void)?
    /// Where the glucose comes from as answered (`source.kind`, docs/44); nil = not answered yet: the Mac reads what it
    /// is set up to read (following someone wins, Juggluco while nobody is followed, as before the question).
    var sourceKind: SourceKind? {
        didSet {
            guard sourceKind != oldValue else { return }
            let wasMonitoring = monitoring
            if let last = lastSnapshot { apply(last) } else { syncLan() }
            syncNightscout()
            syncLibreLinkUp()
            applyTreatments()
            if monitoring != wasMonitoring {
                // an online source switched off: its readings and sensor leave the card and the alerts at once
                monitoringChanged(from: wasMonitoring)
                applySensor()
                apply(readings: monitoredReadings)
                feedWidget()
            }
            onNightscout?(nightscoutSnapshot)
            onLibreLinkUp?(libreLinkUpSnapshot)
        }
    }
    /// Whose glucose, each source's wear length and the manual start (Settings › Source's *Sensor*): the sensor life
    /// of a source that doesn't say all of it (`SensorLives`). A change re-applies the sensor at once.
    var sensorLifeSettings = SensorLifeSettings() {
        didSet {
            guard sensorLifeSettings != oldValue else { return }
            applySensor()
            feedWidget()
        }
    }
    /// The last follower snapshot, applied again when the source changes.
    @ObservationIgnored private var lastSnapshot: FollowerSnapshot?
    @ObservationIgnored private var lanTask: Task<Void, Never>?
    /// Where `history` was loaded from, and whose it is (`shownIdentity` then).
    @ObservationIgnored private var historySource = HistorySource.none
    @ObservationIgnored private var historyIdentity: String?
    /// Where the histories of identities not read now are kept (`SukraraPaths.kept`, `KeptHistory`; nil = none: tests
    /// and snapshots that don't set one).
    @ObservationIgnored let kept: URL?
    /// The kept histories the identity read now continues (a leave with *Keep it*, then this one read), merged, oldest
    /// first: shown before its own readings (`LinkRules.continuing`), counted in the rings, the report and the export.
    @ObservationIgnored private var continued: [Reading] = []
    /// Their treatments (read-only): the chart's marks and the export, never the alert rules.
    @ObservationIgnored private var continuedTreatments: [Treatment] = []
    private(set) var sensor: SensorInfo?
    /// The wearer's live treatments as the chart's marks, oldest first.
    private(set) var marks: [GraphMark] = []
    /// The wearer's live treatments (the CSV export).
    private(set) var treatments: [Treatment] = []
    private(set) var link: LinkState = .notFollowing
    /// The Bluetooth path to the wearer's phone while following (nil = it doesn't run).
    private(set) var nearby: NearbyState?
    /// How the wearer's readings come now: nearby over Bluetooth, the internet, or neither (nil = not following).
    private(set) var linkPath: LinkPath?
    /// Blocked features put off with Not Now (this launch only; the settings pane keeps the fix).
    var notNowBlocked: Set<BlockedFeature> = []
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
    /// Juggluco's readings feed the card and the alert rules: the source, and reached at least once.
    private var lanMonitoring: Bool { !followMonitoring && lanSent != nil && lanSnapshot.monitoring }
    /// Juggluco on the network is the source: its log (this Mac's own) is the treatment log.
    var lanActive: Bool { !followMonitoring && sourceKind == .lanJuggluco && lanSent != nil }
    /// Nightscout's readings feed the card and the alert rules: the source, and the site answered at least once.
    private var nightscoutMonitoring: Bool {
        !followMonitoring && nightscoutSent != nil && nightscoutSnapshot.monitoring
    }
    /// Nightscout is the source with a site: its log is the treatment log, its state the no-data reason.
    var nightscoutActive: Bool { !followMonitoring && nightscoutSent != nil }
    /// LibreLinkUp's readings feed the card and the alert rules: the source, someone picked, and it answered once.
    private var libreLinkUpMonitoring: Bool {
        !followMonitoring && libreLinkUpSent != nil && libreLinkUpSnapshot.monitoring
    }
    /// LibreLinkUp is the source with someone to read: its log is the treatment log, its state the no-data reason.
    var libreLinkUpActive: Bool { !followMonitoring && libreLinkUpSent != nil }
    /// LibreLinkUp is the answered source but reads no one yet (signed out, or no one picked): Connect.
    private var libreLinkUpUnset: Bool {
        libreLinkUp != nil && sourceKind == .libreLinkUp && !followMonitoring && libreLinkUpSent == nil
    }
    private var monitoring: Bool { followMonitoring || lanMonitoring || nightscoutMonitoring || libreLinkUpMonitoring }
    /// How Nightscout's or LibreLinkUp's readings come (the panel's badge, with the source's name): over the internet,
    /// or none while offline; nil when neither is the source.
    var onlinePath: (path: LinkPath, site: String)? {
        if nightscoutActive, nightscoutSnapshot.outcome != nil {
            return (nightscoutSnapshot.online ? .internet : .offline, "Nightscout")
        }
        if libreLinkUpActive, libreLinkUpSnapshot.outcome != nil {
            return (libreLinkUpSnapshot.online ? .internet : .offline, "LibreLinkUp")
        }
        return nil
    }
    /// This Mac keeps the treatment log itself (every source but another Sukrara: Nightscout's site, LibreLinkUp,
    /// Juggluco on the network).
    var ownLogActive: Bool { nightscoutActive || libreLinkUpActive || lanActive }
    /// The source reads a stream from another Sukrara (or nothing is answered yet: what the Mac follows). One person,
    /// one link: only then does anything of the link count (its readings, settings, name, path, alerts, reasons).
    var followsSource: Bool { sourceKind == nil || sourceKind == .sukrara }
    /// A follow record left behind another source (a Mac updated from before one person, one link) is being left.
    @ObservationIgnored private var leavingBehind = false
    var unit: GlucoseUnit = .mgdl
    /// The card's settings (bands, hours…): this Mac's, or the wearer's mirrored ones (`GraphConfig.load`).
    var graph = GraphConfig() {
        didSet {
            guard graph != oldValue else { return }
            if graph.trend != oldValue.trend { trendChanged() }
            refreshSlots()
        }
    }
    /// The widgets' card settings: the same, with the Widgets place's own time shown and sensor time left.
    var widgetGraph = GraphConfig() {
        didSet { if widgetGraph != oldValue { refreshSlots() } }
    }
    /// "Now" for the card: moved by the freshness ticker at each freshness step, so a value greys and strikes on time.
    private(set) var now: Date
    var notificationPermission: NotificationPermission = .allowed

    /// The features macOS blocks now (docs/40), without those put off with Not Now: the panel names the first. The
    /// follow's (Bluetooth) only while it is the source.
    var blocked: [BlockedFeature] {
        BlockedFeature.of(
            link: followsSource ? link : .notFollowing, nearby: followsSource ? nearby : nil,
            login: loginItem?.status
        ).filter {
            $0.canTurnOff || !notNowBlocked.contains($0)
        }
    }

    @ObservationIgnored let alerts: AlertController?
    @ObservationIgnored let updates: Updates?
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
        updates: Updates? = nil, loginItem: LoginItem? = nil, follower: (any FollowerService)? = nil,
        widget: WidgetFeed? = nil, lan: (any LanSourcing)? = nil, nightscout: (any NightscoutSourcing)? = nil,
        libreLinkUp: (any LibreLinkUpSourcing)? = nil, kept: URL? = nil
    ) {
        self.plan = plan
        self.kept = kept
        self.clock = clock
        self.alerts = alerts
        self.updates = updates
        self.loginItem = loginItem
        self.follower = follower
        self.widget = widget
        self.lan = lan
        self.nightscout = nightscout
        self.libreLinkUp = libreLinkUp
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
        if let nightscout {
            feeds.append(
                Task { [weak self] in
                    for await snapshot in await nightscout.snapshots() { self?.apply(nightscout: snapshot) }
                })
        }
        if let libreLinkUp {
            feeds.append(
                Task { [weak self] in
                    for await snapshot in await libreLinkUp.snapshots() { self?.apply(libreLinkUp: snapshot) }
                })
        }
        guard let follower else { return }
        feeds.append(
            Task { [weak self] in
                for await snapshot in await follower.snapshots() { self?.apply(snapshot) }
            })
        feeds.append(
            Task { [weak self] in
                // the wearer's alerts only while the link is the source (link.json `after`: alerts, escalation)
                for await alert in await follower.alerts() where self?.followsSource == true {
                    self?.alerts?.wearerAlert(alert)
                }
            })
        Task { await follower.start() }
    }

    /// One follower snapshot: the link, readings, sensor and treatments, and whether the wearer's alerts are mirrored.
    func apply(_ snapshot: FollowerSnapshot) {
        let wasMonitoring = monitoring
        lastSnapshot = snapshot
        link = snapshot.link
        keychainUnreadable = followsSource && snapshot.keychainUnreadable
        // the follow's path and Bluetooth only while it is the source (Appendix G finding 1)
        nearby = followsSource ? snapshot.nearby : nil
        linkPath = followsSource ? LinkPath.of(snapshot) : nil
        // an unreadable Keychain (retrying) still follows the wearer: keep alerting on their readings, no-data included
        // Juggluco chosen as the source: the follow stays (switching back needs no new invite) but isn't read
        followMonitoring =
            followsSource && (link.isFollowing || (snapshot.keychainUnreadable && !snapshot.readings.isEmpty))
        followReadings = followMonitoring ? snapshot.readings : []
        followSensor = snapshot.sensor
        wearerName = followsSource ? Profile.shownName(snapshot.wearer?.name) : nil
        followerKnown = true
        leaveBehindIfNeeded(snapshot)
        syncLan()
        syncNightscout()
        syncLibreLinkUp()
        monitoringChanged(from: wasMonitoring)
        if let alerts { alerts.setMirroring(followMonitoring && alerts.settings.source == .wearer) }
        applyTreatments()
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

    // MARK: one person, one link

    /// Leaves the link: THE one place this Mac stops following (the leave sheet's *Stop Following*, a request
    /// withdrawn, a follow left behind another source). `keepHistory`: the readings and treatments received stay as
    /// the wearer's kept history (`KeptHistory`), else they go with the rest; `continues`: the next identity read
    /// continues it (the leave sheet's *Keep it*: "for when it reads the same person"). The runtime's
    /// `unfollow(keepHistory:)` tells the wearer (PROTOCOL-v2 `leave`).
    func leaveLink(keepHistory: Bool, continues: Bool = true) async {
        let identity = lastSnapshot?.identity
        await follower?.unfollow(keepHistory: keepHistory)
        if keepHistory, continues, let identity, let kept {
            KeptHistory.awaitNext(KeptHistory.id(.sukrara, identity), in: kept)
        }
    }

    /// Whose history is shown (`KeptHistory.id`): the followed wearer, the LibreLinkUp person, the Nightscout site or
    /// Juggluco on the network (one identity); nil while nothing is read.
    var shownIdentity: String? { identity(of: historySource) }

    private func identity(of source: HistorySource) -> String? {
        switch source {
        case .follow: lastSnapshot?.identity.map { KeptHistory.id(.sukrara, $0) }
        case .libreLinkUp:
            libreLinkUpSnapshot.patient.isEmpty ? nil : KeptHistory.id(.libreLinkUp, libreLinkUpSnapshot.patient)
        case .nightscout: nightscoutSent.map { KeptHistory.id(.nightscout, NightscoutSite.parse($0.site).root) }
        case .lan: KeptHistory.id(.lanJuggluco, "network")
        case .none: nil
        }
    }

    /// The kept histories not shown now (Settings › Source's *Also saved on this Mac*), newest first.
    func keptNotShown() -> [KeptHistory.Info] {
        guard let kept else { return [] }
        return KeptHistory.notShown(with: shownIdentity, in: kept)
    }

    /// Settings › Source's *Delete* on a kept history (confirmed there).
    func deleteKept(_ id: String) {
        guard let kept else { return }
        KeptHistory.delete(id, in: kept)
    }

    /// A follow record left behind another source (a Mac updated in the old state: one person, one link says it
    /// can't exist) is left at once: the source in use stays (the person chose it; switching back could show the
    /// wrong person), the wearer is told, the history kept, the settings untouched (they weren't in use). No question.
    private func leaveBehindIfNeeded(_ snapshot: FollowerSnapshot) {
        guard !followsSource, !leavingBehind else { return }
        switch snapshot.link {
        case .notFollowing, .joining: return
        default: break
        }
        leavingBehind = true
        AppLog.app.info("a follow kept behind another source: leaving it (history kept)")
        Task { [weak self] in
            // kept apart: the source in use may read someone else (only a leave the person answered continues it)
            await self?.leaveLink(keepHistory: true, continues: false)
            self?.leavingBehind = false
        }
    }

    /// The link's state as picking a source asks it (`LinkRules.pickAsks`): a follow that runs or waits, one that ended,
    /// or none.
    var linkState: LinkRules.State {
        switch link {
        case .notFollowing: .none
        case .joining, .requested, .following: .active
        case .declined, .inviteUsed, .inviteExpired, .revoked, .broken: .ended
        }
    }

    /// Whether this Mac holds readings or treatments received through the link (the leave sheet asks keep / delete),
    /// and its oldest reading.
    var linkHistory: (held: Bool, since: Date?) {
        let readings = historySource == .follow && !history.isEmpty ? history : (lastSnapshot?.readings ?? [])
        let held = !readings.isEmpty || !(lastSnapshot?.treatments.isEmpty ?? true)
        return (held, readings.first?.t)
    }

    /// The wearer's name as the follow gives it (nil = none yet).
    var linkName: String? { Profile.shownName(lastSnapshot?.wearer?.name) ?? FollowerAdapter.wearerName(link) }

    /// What the Juggluco source said: its readings feed the card and the alerts while it is the source, this Mac's
    /// treatment log the marks, the quiets and the export.
    func apply(lan snapshot: LanSnapshot) {
        let wasMonitoring = monitoring
        lanSnapshot = snapshot
        monitoringChanged(from: wasMonitoring)
        onLan?(snapshot)
        guard !followMonitoring else { return }
        applyTreatments()
        applySensor()
        apply(readings: monitoredReadings)
        feedWidget()
    }

    /// What the Nightscout source said: its readings feed the card and the alerts while it is the source, its
    /// treatment log the marks, the quiets and the export.
    func apply(nightscout snapshot: NightscoutSnapshot) {
        let wasMonitoring = monitoring
        nightscoutSnapshot = snapshot
        monitoringChanged(from: wasMonitoring)
        onNightscout?(snapshot)
        guard nightscoutActive else { return }
        applyTreatments()
        applySensor()
        apply(readings: monitoredReadings)
        feedWidget()
    }

    /// What the LibreLinkUp source said: its readings feed the card and the alerts while it is the source, this Mac's
    /// treatment log the marks, the quiets and the export.
    func apply(libreLinkUp snapshot: LibreLinkUpSnapshot) {
        let wasMonitoring = monitoring
        libreLinkUpSnapshot = snapshot
        monitoringChanged(from: wasMonitoring)
        onLibreLinkUp?(snapshot)
        guard libreLinkUpActive || wasMonitoring else { return }
        applyTreatments()
        applySensor()
        apply(readings: monitoredReadings)
        feedWidget()
    }

    /// The readings the card and the alert rules use: following wins over Juggluco and the online sources (never two
    /// people's mixed; only one of them is ever the source).
    private var monitoredReadings: [Reading] {
        if followMonitoring { return followReadings }
        if lanMonitoring { return lanSnapshot.readings }
        if nightscoutMonitoring { return nightscoutSnapshot.readings }
        return libreLinkUpMonitoring ? libreLinkUpSnapshot.readings : []
    }

    /// The sensor of the same source: the wearer's, the one Juggluco reads, or the one the Nightscout site's newest
    /// Sensor Start / Sensor Change started (with the default warm-up and wear): the card's warm-up and time left, and
    /// the alert rules' sensor window (no data stays quiet once it ended). Your glucose or someone else's alike.
    private var monitoredSensor: WearerSensor? {
        if followMonitoring { return followSensor }
        return sourceSensor?.wearer
    }

    /// The sensor of an online or network source by THE rule (contracts/sources/sensor-life.json): the start it said
    /// (Juggluco's, the site's Sensor Start, LibreLinkUp's serial and start) or, for a Nightscout site of your glucose,
    /// the manual start, the newer; with the source's wear length. Nil = unknown, or not one of those sources.
    var sourceSensor: SensorLives.Sensor? {
        let kind: SourceKind
        let said: SensorLives.Said?
        if lanMonitoring {
            kind = .lanJuggluco
            said = lanSnapshot.sensor.map { SensorLives.Said(id: "\($0.start)", start: $0.start) }
        } else if nightscoutMonitoring {
            kind = .nightscout
            said = nightscoutSnapshot.sensor.map { SensorLives.Said(id: "\($0.start)", start: $0.start) }
        } else if libreLinkUpMonitoring {
            kind = .libreLinkUp
            said = libreLinkUpSnapshot.sensor.map { SensorLives.Said(id: $0.serial, start: $0.start) }
        } else {
            return nil
        }
        return sensorLifeSettings.life(kind, said: said, now: clock.now.timeIntervalSince1970)
    }

    /// The treatments of the source: the followed wearer's log, or this Mac's own with every other source (with
    /// Nightscout the site's read-only among them): the chart's marks, the alert rules' treatment quiets and the CSV
    /// export. Never the wearer's while another source is in use (Appendix G finding 8).
    private func applyTreatments() {
        let followed = followsSource ? (lastSnapshot?.treatments ?? []).filter { !$0.del } : []
        let live: [Treatment] =
            followMonitoring
            ? followed
            : nightscoutActive
                ? nightscoutSnapshot.treatments.filter { !$0.del }
                : libreLinkUpActive
                    ? libreLinkUpSnapshot.treatments.filter { !$0.del }
                    : lanActive ? lanSnapshot.treatments.filter { !$0.del } : []
        // the alert rules hear the wearer's treatments as before (also before a reading), Nightscout's while it is
        // the source
        let heard = followsSource ? followed : live
        // a continued kept history's entries are marks and export lines only: old, read-only, never quiets
        let shown = continuedTreatments + live
        alerts?.treatments(
            heard.compactMap { t in
                let kind: TreatmentKind? = t.k == .carbs ? .carbs : t.k == .rapid ? .rapidInsulin : nil
                guard let kind else { return nil }
                return AlertTreatment(kind: kind, at: Date(timeIntervalSince1970: TimeInterval(t.t)))
            })
        marks = CardAlertsMapping.marks(shown)
        treatments = shown
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
            SensorInfo(
                start: TimeInterval($0.start), end: TimeInterval($0.end), warmupEnd: TimeInterval($0.warmupEnd),
                renew: sensorRenew)
        }
    }

    /// Where the monitored source's new sensor is started: the ended sensor's pill names that app (the followed phone:
    /// Sukrara there, *Replace sensor*).
    private var sensorRenew: SensorRenew {
        if followMonitoring { return .here }
        if lanMonitoring { return .juggluco }
        if nightscoutMonitoring { return .itsApp }
        return libreLinkUpMonitoring ? .libreApp : .here
    }

    /// Monitoring started, stopped or moved to the other source: the alert rules forget the old one and the history
    /// is loaded from the new one.
    private func monitoringChanged(from wasMonitoring: Bool) {
        if monitoring && !wasMonitoring { alerts?.monitoringStopped(nil) }  // back: the "stopped" note goes
        let source: HistorySource =
            followMonitoring
            ? .follow
            : lanMonitoring
                ? .lan : nightscoutMonitoring ? .nightscout : libreLinkUpMonitoring ? .libreLinkUp : .none
        // stopped, or straight from Juggluco to a wearer (or back): the episodes were about the other readings
        if wasMonitoring && (!monitoring || (historySource != .none && source != historySource)) {
            alerts?.resetMonitoring()
        }
        // the source started: no data counts from now, also before (or without) its first reading
        if monitoring { alerts?.sourceStarted() }
        guard source != historySource || (source != .none && identity(of: source) != historyIdentity) else { return }
        historySource = source
        history = []
        continued = []
        continuedTreatments = []
        loadHistory()
    }

    /// Hands the switch's state to the Juggluco source: on with its config while nobody is followed, else off.
    /// Waits for the follower's first snapshot (when there is a follower), so a launch while following never starts
    /// Juggluco first.
    private func syncLan() {
        guard lan != nil else { return }
        let waiting = follower != nil && !followerKnown && sourceKind != .lanJuggluco
        let want = followMonitoring || waiting ? nil : lanConfig
        guard want != lanSent else { return }
        lanSent = want
        toLan { await $0.set(want) }
    }

    /// Hands Nightscout's config to its source while it is the source (and nobody followed is read), else off; waits
    /// for the follower's first snapshot like Juggluco.
    private func syncNightscout() {
        guard nightscout != nil else { return }
        let waiting = follower != nil && !followerKnown && sourceKind != .nightscout
        let want = followMonitoring || waiting || sourceKind != .nightscout ? nil : nightscoutConfig
        guard want != nightscoutSent else { return }
        nightscoutSent = want
        toNightscout { await $0.set(want) }
    }

    /// Runs `step` on the Nightscout source after every earlier one.
    private func toNightscout(_ step: @escaping @Sendable (any NightscoutSourcing) async -> Void) {
        guard let nightscout else { return }
        let previous = nightscoutTask
        nightscoutTask = Task {
            await previous?.value
            await step(nightscout)
        }
    }

    /// Try Again on the Nightscout source.
    func retryNightscout() {
        guard nightscoutSent != nil else { return }
        toNightscout { await $0.retry() }
    }

    /// *Also saved on this Mac*'s *Forget* (Nightscout not the source): the site's readings and log kept apart.
    func forgetNightscout() async {
        toNightscout { await $0.forget() }
        await nightscoutTask?.value
    }

    /// Writes one of this Mac's treatments into Nightscout's log (the Treatments window and Add Treatment).
    func writeNightscout(_ treatment: Treatment) async -> Bool {
        guard nightscoutActive, let nightscout else { return false }
        return await nightscout.write(treatment)
    }

    /// Hands whom LibreLinkUp reads to its source while it is the source (and nobody followed is read), else off;
    /// waits for the follower's first snapshot like Juggluco.
    private func syncLibreLinkUp() {
        guard libreLinkUp != nil else { return }
        let waiting = follower != nil && !followerKnown && sourceKind != .libreLinkUp
        let want = followMonitoring || waiting || sourceKind != .libreLinkUp ? nil : libreLinkUpConfig
        guard want != libreLinkUpSent else { return }
        libreLinkUpSent = want
        toLibreLinkUp { await $0.set(want) }
    }

    /// Runs `step` on the LibreLinkUp source after every earlier one.
    private func toLibreLinkUp(_ step: @escaping @Sendable (any LibreLinkUpSourcing) async -> Void) {
        guard let libreLinkUp else { return }
        let previous = libreLinkUpTask
        libreLinkUpTask = Task {
            await previous?.value
            await step(libreLinkUp)
        }
    }

    /// Try Again on the LibreLinkUp source.
    func retryLibreLinkUp() {
        guard libreLinkUpSent != nil else { return }
        toLibreLinkUp { await $0.retry() }
    }

    /// Connect's *Sign In* (after every earlier step on the source); nil when this run has no LibreLinkUp source.
    func connectLibreLinkUp(email: String, password: String) async -> LluConnectResult? {
        guard let libreLinkUp else { return nil }
        await libreLinkUpTask?.value
        return await libreLinkUp.connect(email: email, password: password)
    }

    /// Connect's *Try Again* (no one shared, new terms): whom the saved account follows, read again.
    func recheckLibreLinkUp(saved: String?) async -> LluConnectResult? {
        guard let libreLinkUp else { return nil }
        await libreLinkUpTask?.value
        return await libreLinkUp.recheck(saved: saved)
    }

    /// Settings › Source's *Sign Out* (confirmed): the account goes, the readings and the log stay.
    func signOutLibreLinkUp() {
        toLibreLinkUp { await $0.signOut() }
    }

    /// Writes one of this Mac's treatments into LibreLinkUp's log (the Treatments window and Add Treatment).
    func writeLibreLinkUp(_ treatment: Treatment) async -> Bool {
        guard libreLinkUpActive, let libreLinkUp else { return false }
        return await libreLinkUp.write(treatment)
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

    /// Writes one of this Mac's treatments into Juggluco's log (the Treatments window and Add Treatment).
    func writeLan(_ treatment: Treatment) async -> Bool {
        guard lanActive, let lan else { return false }
        return await lan.write(treatment)
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
        let reason = noDataReason
        let state =
            "\(monitoring ? "monitoring" : "idle") \(look.state.rawValue) \(data.alerts.status) settings \(widgetSettings)"
            + " reason \(reason.map { String(describing: $0) } ?? "none")"
        widget.update(
            WidgetSnapshot(
                writtenAt: clock.now.timeIntervalSince1970, data: data, config: widgetGraph, unit: unit, reason: reason),
            shown: .init(value: look.text ?? "—", state: state))
    }

    public convenience init(plan: LaunchPlan) { self.init(plan: plan, clock: SystemClock()) }

    // MARK: inputs

    /// New readings (the stored history at launch, then each new one). The alert rules get the newest, also an old
    /// one: "no data" is measured from it.
    func apply(readings new: [Reading]) {
        // the kept history the identity continues stays where it has a reading (its last 24 hours here)
        let cutoff = clock.now.addingTimeInterval(-86_400)
        let keptDay = continued.lastIndex { $0.t < cutoff }.map { Array(continued[($0 + 1)...]) } ?? continued
        let sorted = LinkRules.continuing(kept: keptDay, current: new.sorted { $0.t < $1.t })
        // readings leaving the last 24 hours join the history, so the month stays whole while the panel stays shut
        if historySource != .none, let first = sorted.first?.t {
            let after = history.last?.t ?? .distantPast
            history += readings.filter { $0.t < first && $0.t > after }
            let oldest = clock.now.addingTimeInterval(-ChartView.maxSpan)
            if let firstKept = history.firstIndex(where: { $0.t >= oldest }), firstKept > 0 {
                history.removeFirst(firstKept)
            }
        }
        readings = withTrend(sorted)
        now = clock.now
        if let latest = readings.last { alerts?.reading(latest) }
        refreshSlots()
        restartTicker()
    }

    /// THE one place the Mac works out the trend (contracts/trend, #204): every reading the card, the menu bar, the
    /// widgets, the alerts, the report and read aloud get has the trend of its own time, from the readings before it
    /// with the *Trend arrow* setting in use (`graph.trend`: this Mac's, or the wearer's mirrored one). Sorted by time.
    private func withTrend(_ list: [Reading]) -> [Reading] { Self.withTrend(list, setting: graph.trend) }

    nonisolated private static func withTrend(_ list: [Reading], setting: TrendSetting) -> [Reading] {
        TrendRate.annotate(list, setting: setting)
    }

    /// `withTrend` off the main thread, for the month of history (0.04 s in a release build, 0.8 s in a debug one: on
    /// the main thread it stalled the panel as it opened, #181): the same function, so the same trends.
    @concurrent nonisolated private static func withTrendInBackground(_ list: [Reading], setting: TrendSetting) async
        -> [Reading]
    {
        withTrend(list, setting: setting)
    }

    /// The *Trend arrow* setting changed: every arrow follows at once (the history's as soon as it is worked out); the
    /// alert rules judge from the next reading (contracts/alerts/SPEC.md §2 "Fast alerts read the trend").
    private func trendChanged() {
        readings = withTrend(readings)
        feedWidget()
        trendHistory()
    }

    /// The history's trends with the setting in use, worked out off the main thread; again when the history changed
    /// meanwhile (a reading joined it), dropped when the setting changed again (its own pass follows).
    private func trendHistory() {
        let setting = graph.trend, list = history, version = historyVersion
        Task { [weak self] in
            let annotated = await Self.withTrendInBackground(list, setting: setting)
            guard let self, self.graph.trend == setting else { return }
            guard self.historyVersion == version else { return self.trendHistory() }
            self.history = annotated
            self.feedWidget()
        }
    }

    /// The newest reading with its trend (Settings › Readings › Trend arrow's preview).
    var newestReading: Reading? { readings.last }

    func apply(sensor info: SensorInfo?) {
        sensor = info
        restartTicker()
    }

    // MARK: what the surfaces show

    /// The card's data at `now`, from the stores only (never made-up values on a release build).
    var cardData: CardData {
        CardData(
            readings: cardReadings,
            marks: marks, sensor: sensor,
            alerts: cardAlerts, now: now.timeIntervalSince1970, slots: slots)
    }

    /// The card's alert decision: the engine's state, or while mirroring the wearer's alerts the same rules over the
    /// latest reading (this Mac's engine then runs only no data).
    private var cardAlerts: CardAlerts {
        guard let alerts, alerts.mirroring else {
            return CardAlertsMapping.of(alerts?.ui ?? .idle, settings: alerts?.settings, marks: marks, now: now)
        }
        let latest = readings.last.map(GlucoseSample.init)
        return CardAlertsMapping.mirrored(
            latest: latest, settings: alerts.settings, marks: marks, sideMutes: alerts.mirroredSideMutes(at: now),
            muteAll: alerts.ui.muteAllUntil, now: now)
    }

    /// The panel card's data: `cardData` with the stored history before the snapshot's readings, so the chart
    /// scrolls and zooms through up to 31 days.
    var panelData: CardData {
        var data = cardData
        guard !history.isEmpty else { return data }
        data.readings = panelReadings
        return data
    }

    /// The stored history and the last 24 hours, oldest first (the report, the export and the slots).
    var allReadings: [Reading] {
        guard let first = readings.first?.t else { return history }
        return history.prefix { $0.t < first } + readings
    }

    static func graph(_ readings: [Reading]) -> [GraphReading] {
        readings.map(GraphReading.init)
    }

    /// Counts every slot's numbers over the whole history once (the card surfaces only look them up).
    private func refreshSlots() {
        let all = monitoring ? panelReadings : []
        let new =
            all.isEmpty
            ? nil
            : SlotNumbers.of(all, now: now.timeIntervalSince1970, configs: [graph, widgetGraph])
        if new != slots { slots = new }
    }

    /// Loads the monitored source's stored history (31 days) with the kept histories it continues: when monitoring
    /// starts and each time the panel opens. The identity read now continues a kept history left waiting for it.
    func loadHistory() {
        let source = historySource
        let identity = shownIdentity
        historyIdentity = identity
        guard source != .none else {
            history = []
            refreshSlots()
            return
        }
        let kept = self.kept
        let since = clock.now.addingTimeInterval(-ChartView.maxSpan)
        let follower = self.follower
        let lan = self.lan
        let nightscout = self.nightscout
        let libreLinkUp = self.libreLinkUp
        Task { [weak self] in
            let loaded: [Reading] =
                switch source {
                case .follow: await follower?.readings(since: since) ?? []
                case .lan: await lan?.readings(since: since) ?? []
                case .nightscout: await nightscout?.readings(since: since) ?? []
                case .libreLinkUp: await libreLinkUp?.readings(since: since) ?? []
                case .none: []
                }
            let (before, marks) = await Self.continuation(of: identity, in: kept)
            guard let self, self.historySource == source, self.historyIdentity == identity else { return }
            if before != self.continued || marks != self.continuedTreatments {
                self.continued = before
                self.continuedTreatments = marks
                self.applyTreatments()
                self.apply(readings: self.monitoredReadings)
            }
            let setting = self.graph.trend, current = self.history, version = self.historyVersion
            let sorted = await Self.withTrendInBackground(
                LinkRules.continuing(kept: before.filter { $0.t >= since }, current: loaded), setting: setting)
            guard self.historySource == source, self.historyIdentity == identity, self.historyVersion == version,
                self.graph.trend == setting, await Self.differ(sorted, current)
            else { return }
            self.history = sorted
            self.refreshSlots()
            self.feedWidget()
        }
    }

    /// The kept histories `identity` continues (binding the one a leave left waiting), merged oldest first, and their
    /// treatments; read off the main thread.
    @concurrent nonisolated private static func continuation(of identity: String?, in kept: URL?) async -> (
        [Reading], [Treatment]
    ) {
        guard let identity, let kept else { return ([], []) }
        KeptHistory.continueAwaiting(by: identity, in: kept)
        var readings: [Reading] = []
        var treatments: [Treatment] = []
        for info in KeptHistory.continuing(identity, in: kept) {
            readings = LinkRules.continuing(kept: readings, current: await KeptHistory.readings(info.id, in: kept))
            treatments += await KeptHistory.treatments(info, in: kept)
        }
        return (readings, treatments)
    }

    /// Whether a freshly loaded month differs from the one shown, compared off the main thread.
    @concurrent nonisolated private static func differ(_ a: [Reading], _ b: [Reading]) async -> Bool { a != b }

    /// Nobody is followed and nothing is on its way (not following, declined, revoked, invite used, broken).
    var needsFollow: Bool {
        switch link {
        case .following, .requested, .joining: false
        default: true
        }
    }

    /// A low alert is up (Add treatment then puts the focus on Carbs).
    var isLow: Bool { alerts?.ui.active[.level]?.kind.isLowSide == true }

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

    /// Why there's no value to show and what can be done (#72; Android's no-data reasons), or nil while a value can be
    /// shown. Before the first reading the app says so instead of "no data" — and no no-data alarm is armed. Once a
    /// value is hidden (20 min and more without a reading) it says why under the card: the wearer's phone is silent,
    /// or Juggluco can't be reached.
    public var noDataReason: NoDataReason? {
        if plan.isOffline && plan.mode != .fixture { return .offlineBuild }
        // shown over the stored readings too: with the Keychain unreadable no new reading can arrive, and the alerts
        // go on over the last ones (no data included) while the runtime retries
        // the link's reasons only while it is the source (Appendix G finding 14)
        let ownSource = !followsSource || lanMonitoring || ownLogActive || libreLinkUpUnset
        if keychainUnreadable, !ownSource { return .broken(.keyUnreadable) }
        if case .broken(let why) = link, !ownSource { return .broken(why) }
        guard readings.isEmpty else {
            guard look.state == .noReadings else { return nil }
            if followMonitoring { return .wearerSilent(since: readings.last?.t.timeIntervalSince1970) }
            if nightscoutMonitoring { return Self.nightscoutWait(nightscoutSnapshot, now: clock.now) }
            if libreLinkUpMonitoring { return Self.libreLinkUpWait(libreLinkUpSnapshot, now: clock.now) }
            return lanMonitoring ? Self.lanWait(lanSnapshot.state) : nil
        }
        if !followMonitoring, lanSent != nil, let wait = Self.lanWait(lanSnapshot.state) { return wait }
        if nightscoutActive, let wait = Self.nightscoutWait(nightscoutSnapshot, now: clock.now) { return wait }
        if libreLinkUpActive, let wait = Self.libreLinkUpWait(libreLinkUpSnapshot, now: clock.now) { return wait }
        // a sensor warming up before any reading: the card's warm-up, not a reason
        if nightscoutActive, nightscoutSnapshot.health(at: clock.now) == .warmingUp { return nil }
        if libreLinkUpActive, libreLinkUpSnapshot.health(at: clock.now) == .warmingUp { return nil }
        if libreLinkUpUnset { return .libreLinkUp(.notSignedIn) }
        switch sourceKind {
        case .nightscout: return nightscoutActive ? nil : .nightscout(.noSite)
        case .libreLinkUp: return .libreLinkUp(.notSignedIn)
        case .lanJuggluco: return Self.lanWait(lanSnapshot.state) ?? .juggluco(.looking)
        case .sukrara: break
        case nil:
            // no source: left a link with nothing picked (or never answered), and nothing followed
            if followerKnown, link == .notFollowing { return .noSource }
        }
        switch link {
        case .notFollowing, .joining: return .notFollowing
        case .requested: return .waitingApproval
        case .following: return .wearerSilent(since: nil)
        case .declined: return .declined
        case .inviteUsed: return .inviteUsed
        case .inviteExpired: return .inviteExpired
        case .revoked: return .revoked
        case .broken(let why): return .broken(why)
        }
    }

    /// The reason in words when it takes the card's place (no reading to draw, an offline run, a broken link), else
    /// nil: the panel's first line, the menu bar's tooltip and VoiceOver.
    public var notMonitoringReason: String? {
        guard let reason = noDataReason else { return nil }
        let replacesCard: Bool =
            switch reason {
            case .offlineBuild, .broken: true
            default: readings.isEmpty
            }
        // the Keychain retrying over stored readings: the line says so above the card (`alertingOnLastData`)
        return replacesCard
            ? NoDataText.of(reason, now: now.timeIntervalSince1970, lastData: alertingOnLastData).title : nil
    }

    /// Why the Nightscout site gives no reading now (contracts/sources/health.json), nil while it is live.
    static func nightscoutWait(_ snapshot: NightscoutSnapshot, now: Date) -> NoDataReason? {
        switch snapshot.health(at: now) {
        case .connecting: .nightscout(.connecting)
        // warming up: the card says it (the sensor's life), nothing is wrong
        case .live, .late, .warmingUp: nil
        case .noNewReadings:
            snapshot.readings.last.map { .nightscout(.silent(since: $0.t.timeIntervalSince1970)) }
                ?? .nightscout(.noGlucose)
        case .offline: .nightscout(.offline)
        case .unreachable: .nightscout(.unreachable(host: snapshot.host))
        case .signedOut: .nightscout(.signedOut)
        case .rateLimited: .nightscout(.rateLimited)
        // LibreLinkUp's own states: a site never reaches them
        case .actionNeeded, .notShared, .stopped: .nightscout(.unreachable(host: snapshot.host))
        }
    }

    /// Why LibreLinkUp gives no reading now (contracts/sources/health.json with its own states), nil while it is live.
    static func libreLinkUpWait(_ snapshot: LibreLinkUpSnapshot, now: Date) -> NoDataReason? {
        switch snapshot.health(at: now) {
        case .connecting: .libreLinkUp(.connecting)
        // warming up: the card says it (the sensor's life), nothing is wrong
        case .live, .late, .warmingUp: nil
        case .noNewReadings:
            snapshot.readings.last.map { .libreLinkUp(.silent(since: $0.t.timeIntervalSince1970)) }
                ?? .libreLinkUp(.noGlucose)
        case .offline: .libreLinkUp(.offline)
        case .unreachable: .libreLinkUp(.unreachable)
        case .signedOut: snapshot.email.isEmpty ? .libreLinkUp(.notSignedIn) : .libreLinkUp(.signedOut)
        case .rateLimited: .libreLinkUp(.rateLimited)
        case .actionNeeded: .libreLinkUp(.terms)
        case .notShared: snapshot.pickedGone ? .libreLinkUp(.pickedGone) : .libreLinkUp(.notShared)
        case .stopped: .libreLinkUp(.stopped)
        }
    }

    /// Why Juggluco gives no reading (nil when it is off).
    static func lanWait(_ state: LanState) -> NoDataReason? {
        switch state {
        case .off: nil
        case .looking: .juggluco(.looking)
        case .notFound: .juggluco(.notFound)
        case .connected: .juggluco(.firstReading)
        case .unreachable(let host): .juggluco(.unreachable(host: host))
        case .unauthorized: .juggluco(.refusedSecret)
        case .refused(let typed): .juggluco(.notHomeNetwork(typed: typed))
        case .localNetworkDenied: .juggluco(.localNetworkDenied)
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
            self.refreshSlots()  // counted at the card's now: the panel takes them as they are (`CardData.metrics`)
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
        retryNightscout()
        retryLibreLinkUp()
    }

    /// The Mac's network path changed: the online sources say *No internet* while it is down and ask again when it's
    /// back.
    func network(online: Bool) {
        toNightscout { await $0.network(online: online) }
        toLibreLinkUp { await $0.network(online: online) }
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
        nightscoutSent = nil
        toNightscout { await $0.set(nil) }
        libreLinkUpSent = nil
        toLibreLinkUp { await $0.set(nil) }
        await lanTask?.value
        await nightscoutTask?.value
        await libreLinkUpTask?.value
        await follower?.stop()
    }
}

/// Where the history was loaded from.
private enum HistorySource {
    case none, follow, lan, nightscout, libreLinkUp
}
