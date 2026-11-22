// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import AppKit
    import SukraraAlerts
    import SukraraCore
    import SukraraGraph
    import SukraraShare
    import SwiftUI

    /// `sukrara-snap shell <dir>`: the menu bar title and the panel in the states a person meets, light + dark,
    /// with synthetic data and an alert controller on in-memory fakes (nothing is posted, stored or scheduled).
    public enum ShellSnapshots {
        public static func render(to dir: URL) throws -> Int {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var n = 0
            // `SUKRARA_SNAP_ONLY=panel-nightscout`: only the scenes whose names start so
            let only = ProcessInfo.processInfo.environment["SUKRARA_SNAP_ONLY"]?.split(separator: ",").map(String.init)
            for (name, scene) in scenes() where only.map({ $0.contains { name.hasPrefix($0) } }) ?? true {
                for dark in [false, true] {
                    let view = scene.view
                        .environment(\.colorScheme, dark ? .dark : .light)
                        .background(Color(nsColor: .windowBackgroundColor))
                    let file = dir.appending(path: "\(name)-\(dark ? "dark" : "light").png")
                    try WindowSnapshots.png(view, appearance: dark ? .darkAqua : .aqua).write(to: file)
                    n += 1
                }
            }
            return n
        }

        struct Scene { var view: AnyView }

        /// A Nightscout source that only ever says `snapshot` (nothing is asked of any site).
        private actor StillNightscout: NightscoutSourcing {
            let snapshot: NightscoutSnapshot
            init(_ snapshot: NightscoutSnapshot) { self.snapshot = snapshot }
            func snapshots() -> AsyncStream<NightscoutSnapshot> {
                AsyncStream { $0.yield(snapshot) }
            }
            func set(_ config: NightscoutConfig?) {}
            func retry() {}
            func network(online: Bool) {}
            func readings(since: Date) -> [Reading] { snapshot.readings.filter { $0.t >= since } }
            func write(_ treatment: Treatment) -> Bool { false }
            func forget() {}
        }

        /// A LibreLinkUp source that only ever says `snapshot` (nothing is asked of LibreLinkUp).
        private actor StillLibreLinkUp: LibreLinkUpSourcing {
            let snapshot: LibreLinkUpSnapshot
            init(_ snapshot: LibreLinkUpSnapshot) { self.snapshot = snapshot }
            func snapshots() -> AsyncStream<LibreLinkUpSnapshot> {
                AsyncStream { $0.yield(snapshot) }
            }
            func set(_ config: LibreLinkUpConfig?) {}
            func connect(email: String, password: String) -> LluConnectResult { .unreachable }
            func recheck(saved: String?) -> LluConnectResult { .unreachable }
            func signOut() {}
            func retry() {}
            func network(online: Bool) {}
            func readings(since: Date) -> [Reading] { snapshot.readings.filter { $0.t >= since } }
            func write(_ treatment: Treatment) -> Bool { false }
        }

        static func scenes() -> [(String, Scene)] {
            let now = Date()
            let day = RealisticSynthetic.readings(until: now.timeIntervalSince1970 - 60, days: 1).map {
                Reading(t: Date(timeIntervalSince1970: $0.t), mgdl: Int($0.v.rounded()), sensorRate: $0.rate)
            }
            func ending(_ mgdl: Int, rate: Double, age: TimeInterval = 60) -> [Reading] {
                day.filter { $0.t < now.addingTimeInterval(-age - 240) }
                    + [Reading(t: now.addingTimeInterval(-age), mgdl: mgdl, sensorRate: rate)]
            }
            func steady(_ mgdl: Int) -> [Reading] {
                stride(from: 86_400.0, to: 120, by: -300).enumerated().map { i, ago in
                    Reading(t: now.addingTimeInterval(-ago), mgdl: i % 100 < 3 ? 200 : 118, sensorRate: 0)
                } + [Reading(t: now.addingTimeInterval(-60), mgdl: mgdl, sensorRate: 2.5)]
            }
            // the plan's data: a scratch folder name (nothing starts there: no `AppServices`)
            let root = FileManager.default.temporaryDirectory.appending(path: "sukrara-snap-\(UUID().uuidString)")
            let store = InMemorySettingsStore()
            let done = SetupFlow(states: [
                .welcome: .done, .follow: .done, .alerts: .done, .notifications: .done, .openAtLogin: .done,
            ])

            func scene(
                link: LinkState = .following(wearer: "Marta", canWrite: true), readings: [Reading],
                setup: SetupFlow = done, permission: NotificationPermission = .allowed,
                mode: LaunchMode = .normal, scroll: ChartScroll = ChartScroll(),
                keychainUnreadable: Bool = false, update: Updates.Reminder = .none, nearby: NearbyState? = nil,
                relays: Int? = 3, nightscout: NightscoutSnapshot? = nil, libreLinkUp: LibreLinkUpSnapshot? = nil,
                act: (AlertController) -> Void = { _ in }
            ) -> Scene {
                var own = AlertSettings()
                own.source = .own
                let controller = AlertController(
                    settings: own, clock: SystemClock(), store: MemoryAlertState(), notifier: SilentNotifier(),
                    wake: NoWake())
                // the plan only decides what the panel says (nothing here starts a service: no `AppServices`); `.all`
                // shows the panel of a monitoring Mac, the fixture's the offline synthetic run
                let plan = LaunchPlan(
                    mode: mode, services: mode == .fixture ? .none : .all,
                    data: .isolated(root: root))
                let updates = Updates(engine: StillUpdater())
                if case .available(let version, let ready) = update { updates.found(version: version, ready: ready) }
                let model = AppModel(
                    plan: plan, alerts: controller, updates: updates, nightscout: nightscout.map(StillNightscout.init),
                    libreLinkUp: libreLinkUp.map(StillLibreLinkUp.init))
                if let libreLinkUp {
                    // LibreLinkUp as the source: its readings and the sensor's serial and start
                    model.sourceKind = .libreLinkUp
                    model.libreLinkUpConfig = LibreLinkUpConfig(patient: "p")
                    model.apply(FollowerSnapshot.empty)
                    model.apply(libreLinkUp: libreLinkUp)
                }
                if let nightscout {
                    // a Nightscout site as the source: its readings and its Sensor Start's life
                    model.sourceKind = .nightscout
                    model.nightscoutConfig = NightscoutConfig(site: "https://ns.example.org", secret: "")
                    model.apply(FollowerSnapshot.empty)
                    model.apply(nightscout: nightscout)
                }
                var snapshot = FollowerSnapshot.empty
                snapshot.link = link
                snapshot.readings = readings
                snapshot.keychainUnreadable = keychainUnreadable
                snapshot.nearby = nearby
                snapshot.relaysConnected = relays
                let start = Int(now.timeIntervalSince1970) - 6 * 86400
                snapshot.sensor = WearerSensor(start: start, end: start + 14 * 86400, warmupEnd: start + 3600)
                if nightscout == nil && libreLinkUp == nil { model.apply(snapshot) }
                act(controller)
                controller.refreshUI()
                model.notificationPermission = permission
                let setupModel = SetupModel(
                    settings: SettingsModel(
                        store: store, follow: FollowModel(status: .notFollowing, actions: nil), version: "0.12.0"))
                setupModel.flow = setup
                let treatments = TreatmentsModel(rows: [], canWrite: false, actions: nil)
                treatments.isFollowing = link.isFollowing
                if case .following(_, true) = link { treatments.canWrite = true }
                let links = PanelLinks(
                    setup: setupModel, treatments: treatments, openSetup: {}, openFollow: {},
                    openAddTreatment: {},
                    openTreatments: {}, openReport: {}, openSettings: {}, openSettingsPane: { _ in })
                return Scene(
                    view: AnyView(
                        VStack(alignment: .leading, spacing: 8) {
                            MenubarStrip(title: model.menubarTitle)
                            PanelView(model: model, links: links, scroll: scroll)
                        }))
            }

            // the title alone, as a following Mac draws it for a reading (#8: its inks on every kind of bar)
            func title(_ readings: [Reading]) -> MenubarTitle {
                let controller = AlertController(
                    settings: AlertSettings(), clock: SystemClock(), store: MemoryAlertState(),
                    notifier: SilentNotifier(),
                    wake: NoWake())
                let plan = LaunchPlan(
                    mode: .normal, services: .all,
                    data: .isolated(root: root))
                let model = AppModel(plan: plan, alerts: controller)
                var snapshot = FollowerSnapshot.empty
                snapshot.link = .following(wearer: "Marta", canWrite: true)
                snapshot.readings = readings
                model.apply(snapshot)
                controller.refreshUI()
                return model.menubarTitle
            }
            let titles = [
                ending(112, rate: -0.4), ending(62, rate: -1.6), ending(246, rate: 1.2),
                ending(131, rate: 0, age: 12 * 60),
            ].map(title)
            let inks = Scene(
                view: AnyView(
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(MenubarStrip.Ground.allCases, id: \.self) { ground in
                            HStack(spacing: 0) {
                                ForEach(titles.indices, id: \.self) { MenubarStrip(title: titles[$0], ground: ground) }
                            }
                        }
                    }))

            // Nightscout as the source: a sensor its site started 30 min ago (warming, no reading since), and one that
            // started 16 days ago (ended a day ago; its last readings from before)
            func site(sensorAgo: TimeInterval, readings: [Reading]) -> NightscoutSnapshot {
                let start = Int(now.timeIntervalSince1970 - sensorAgo)
                return NightscoutSnapshot(
                    outcome: .ok, host: "ns.example.org", readings: readings, monitoring: true,
                    sensor: WearerSensor(start: start, end: start + 15 * 86_400, warmupEnd: start + 3600))
            }
            let nightscout: [(String, Scene)] = [
                (
                    "panel-nightscout-warming",
                    scene(
                        link: .notFollowing, readings: [],
                        nightscout: site(sensorAgo: 30 * 60, readings: ending(131, rate: 0, age: 45 * 60)))
                ),
                (
                    "panel-nightscout-ended",
                    scene(
                        link: .notFollowing, readings: [],
                        nightscout: site(sensorAgo: 16 * 86_400, readings: ending(140, rate: 0, age: 26 * 3600)))
                ),
                (
                    "panel-nightscout-live",
                    scene(
                        link: .notFollowing, readings: [],
                        nightscout: site(sensorAgo: 3 * 86_400, readings: ending(112, rate: -0.4, age: 120)))
                ),
            ]
            // LibreLinkUp as the source: live (its path badge), and a password LibreLinkUp refused (the reason)
            func llu(
                _ readings: [Reading], signInAgain: Bool = false, outcome: SourceHealth.Outcome? = nil,
                windows: [MaintenanceWindow] = []
            ) -> LibreLinkUpSnapshot {
                LibreLinkUpSnapshot(
                    email: "alex@example.com", signInAgain: signInAgain, patient: "p", name: "Ana Example",
                    running: true, outcome: outcome ?? (signInAgain ? .refused : .ok), readings: readings,
                    monitoring: true,
                    sensor: LluSensor(serial: "0D00ABC123", start: Int(now.timeIntervalSince1970) - 4 * 86_400),
                    windows: windows)
            }
            let libreLinkUp: [(String, Scene)] = [
                (
                    "panel-librelinkup-live",
                    scene(link: .notFollowing, readings: [], libreLinkUp: llu(ending(126, rate: 1.5, age: 70)))
                ),
                (
                    "panel-librelinkup-signedout",
                    scene(
                        link: .notFollowing, readings: [],
                        libreLinkUp: llu(ending(126, rate: 0, age: 35 * 60), signInAgain: true))
                ),
                // LibreView's maintenance (docs/45): a wait with its end, never an error; then a window ahead
                (
                    "panel-librelinkup-maintenance",
                    scene(
                        link: .notFollowing, readings: [],
                        libreLinkUp: llu(
                            ending(72, rate: -0.3, age: 25 * 60), outcome: .maintenance,
                            windows: [
                                MaintenanceWindow(
                                    start: now.addingTimeInterval(-1800), end: now.addingTimeInterval(2100), live: true)
                            ]))
                ),
                (
                    "panel-librelinkup-maintenance-ahead",
                    scene(
                        link: .notFollowing, readings: [],
                        libreLinkUp: llu(
                            ending(126, rate: 0.4, age: 70),
                            windows: [
                                MaintenanceWindow(
                                    start: now.addingTimeInterval(10 * 3600), end: now.addingTimeInterval(11 * 3600))
                            ]))
                ),
            ]
            return nightscout + libreLinkUp + [
                // in range, low, high and stale on the bar, over a wallpaper and with Reduce Transparency
                ("menubar-inks", inks),
                ("panel-following", scene(readings: ending(112, rate: -0.4))),
                ("panel-low", scene(readings: ending(62, rate: -1.6))),
                ("panel-high", scene(readings: ending(246, rate: 1.2))),
                ("panel-stale", scene(readings: ending(131, rate: 0, age: 12 * 60))),
                // a steady day, 97 % in range: both rings wear flowers, reaching past the card (#181)
                ("panel-flowers", scene(readings: steady(188))),
                (
                    "panel-muted-all",
                    scene(readings: ending(118, rate: 0.2)) {
                        $0.perform(.muteAll(until: now.addingTimeInterval(3600)))
                    }
                ),
                (
                    "panel-cant-add-treatments",
                    scene(link: .following(wearer: "Marta", canWrite: false), readings: ending(108, rate: 0.1))
                ),
                ("panel-low-muted", scene(readings: ending(64, rate: -0.8)) { $0.perform(.mute(.low, minutes: 30)) }),
                ("panel-waiting-first-reading", scene(readings: [])),
                ("panel-not-following", scene(link: .notFollowing, readings: [])),
                ("panel-revoked", scene(link: .revoked, readings: [])),
                // the no-data reasons with their ways out (#72)
                (
                    "panel-waiting-approval",
                    scene(link: .requested(code: "4821", wearer: "Marta", pairing: nil), readings: [])
                ),
                ("panel-declined", scene(link: .declined, readings: [])),
                ("panel-broken", scene(link: .broken(.stateDamaged), readings: [])),
                ("panel-wearer-silent", scene(readings: ending(131, rate: 0, age: 45 * 60))),
                (
                    "panel-keychain-retrying",
                    scene(
                        link: .broken(.keyUnreadable),
                        readings: ending(112, rate: -0.4), keychainUnreadable: true)
                ),
                (
                    "panel-finish-setup",
                    scene(
                        link: .notFollowing, readings: [],
                        setup: SetupFlow(states: [.welcome: .done, .follow: .skipped, .alerts: .done]))
                ),
                ("panel-notifications-off", scene(readings: ending(105, rate: 0), permission: .denied)),
                // how the readings come, at the end of the mute row: Bluetooth from the phone nearby, no connection,
                // and Nearby beside Mute all's Unmute / Change Time
                (
                    "panel-nearby",
                    scene(readings: ending(112, rate: -0.4), nearby: .connected(phone: "Marta's phone"))
                ),
                ("panel-offline", scene(readings: ending(112, rate: -0.4, age: 4 * 60), relays: 0)),
                (
                    "panel-muted-all-nearby",
                    scene(readings: ending(118, rate: 0.2), nearby: .connected(phone: "Marta's phone")) {
                        $0.perform(.muteAll(until: now.addingTimeInterval(3600)))
                    }
                ),
                // following, Bluetooth not allowed: the blocked feature with its ways out (docs/40)
                ("panel-bluetooth-blocked", scene(readings: ending(112, rate: -0.4), nearby: .unauthorized)),
                // a scheduled check found an update (gentle reminder), then the same once it is downloaded
                (
                    "panel-update-available",
                    scene(readings: ending(112, rate: -0.4), update: .available(version: "0.14.0", ready: false))
                ),
                (
                    "panel-update-ready",
                    scene(readings: ending(112, rate: -0.4), update: .available(version: "0.14.0", ready: true))
                ),
                ("panel-fixture", scene(readings: ending(112, rate: -0.4), mode: .fixture)),
                (
                    "panel-chart-scrolled",
                    scene(
                        readings: ending(112, rate: -0.4),
                        scroll: ChartScroll(end: now.timeIntervalSince1970 - 8 * 3600, span: 4 * 3600))
                ),
            ]
        }
    }

    /// The status item's title as the menu bar draws it (the same attributed string and bell), on a bar-like strip:
    /// the bar's material, the material over a colourful wallpaper, or a solid bar (Reduce Transparency).
    private struct MenubarStrip: View {
        enum Ground: CaseIterable { case bar, wallpaper, solid }
        let title: MenubarTitle
        var ground = Ground.bar

        var body: some View {
            HStack(spacing: 3) {
                Text(AttributedString(StatusItemController.attributed(title)))
                if let image = StatusItemController.image(title) {
                    Image(nsImage: image).renderingMode(image.isTemplate ? .template : .original)
                }
            }
            .padding(.horizontal, 10)
            .frame(height: 24)
            .background {
                switch ground {
                case .bar: Rectangle().fill(.bar)
                case .wallpaper:
                    ZStack {
                        LinearGradient(
                            colors: [.indigo, .orange, .teal, .pink], startPoint: .leading, endPoint: .trailing)
                        Rectangle().fill(.bar)
                    }
                case .solid: Color(nsColor: .windowBackgroundColor)
                }
            }
            .accessibilityLabel(title.accessibilityLabel)
        }
    }

    private final class MemoryAlertState: AlertStateStoring {
        private var state: AlertState?
        func load() -> AlertState? { state }
        func save(_ state: AlertState) { self.state = state }
    }

    private struct SilentNotifier: AlertNotifying {
        func post(_ firing: AlertFiring, sound: Bool, category: String, escalated: Bool) {}
        func clear(_ group: AlertGroup) {}
        func postMuted(_ side: AlertSide, until: Date, category: String) {}
        func removeMuted(_ side: AlertSide) {}
        func postTest(_ kind: AlertKind) {}
        func log(_ line: String) {}
        func monitoringStopped(_ reason: String?) {}
    }

    private struct NoWake: WakeScheduling {
        func schedule(at date: Date?, _ fire: @escaping @MainActor () -> Void) {}
    }
#endif
