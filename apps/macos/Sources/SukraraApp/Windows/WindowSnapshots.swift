// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
#if DEBUG
    import AppKit
    import ServiceManagement
    import SukraraAlerts
    import SukraraCore
    import SukraraGraph
    import SukraraShare
    import SwiftUI

    /// `sukrara-snap windows <dir>`: every window state with synthetic data, light + dark, default + large text.
    /// Offline: settings in memory (no defaults suite left behind), no runtime behind the models (actions nil).
    public enum WindowSnapshots {
        /// Renders every word in one language (`es`), whatever the Mac's: the windows' (String Catalog) and the
        /// card's, the widget's and the mute lines' (`AppLanguage`).
        public static func useLanguage(_ language: String) {
            L10n.use(language: language)
            AppLanguage.forced = AppLanguage(rawValue: language)
        }

        /// The GitHub version's updater as About shows it: automatic checks on, the last check two hours ago.
        static let updates: Updates = {
            let updates = Updates(engine: StillUpdater())
            updates.state(canCheck: true, lastCheck: Date().addingTimeInterval(-2 * 3600))
            return updates
        }()

        /// Scenes drawn in their real window (title bar and toolbar, e.g. the Treatments window's "+"), by window title.
        static var windowed: [String: String] {
            [
                "treatments": L10n.t("Treatments"), "treatments-empty": L10n.t("Treatments"),
                "treatments-nightscout": L10n.t("Treatments"),
            ]
        }

        public static func render(to dir: URL) throws -> Int {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            // a render isn't the app bundle: it gets the bundle's icon, so the rows that draw Sukrara's own app icon
            // (another Sukrara as a source, Follow someone) show the brand's, as the app does
            NSApplication.shared.applicationIconImage = brandIcon
            var n = 0
            let windowed = Self.windowed
            // `SUKRARA_SNAP_ONLY=setup-phone,settings-source-nightscout`: only the scenes whose names start so
            let only = ProcessInfo.processInfo.environment["SUKRARA_SNAP_ONLY"]?.split(separator: ",").map(String.init)
            for (name, view) in scenes() where only.map({ $0.contains { name.hasPrefix($0) } }) ?? true {
                for dark in [false, true] {
                    for large in [false, true] {
                        let v =
                            view
                            .environment(\.colorScheme, dark ? .dark : .light)
                            .dynamicTypeSize(large ? .xxxLarge : .large)
                            .background(Color(nsColor: .windowBackgroundColor))
                        let file = dir.appending(path: "\(name)-\(dark ? "dark" : "light")\(large ? "-large" : "").png")
                        let appearance: NSAppearance.Name = dark ? .darkAqua : .aqua
                        // the settings window and About in their real frame: the toolbar and the window's own
                        // style come from `SnapWindow` (the title from the chrome)
                        let framed = name.hasPrefix("settings-") || name.hasPrefix("about-") ? "" : nil
                        let data =
                            try (windowed[name] ?? framed).map { try windowPNG(v, title: $0, appearance: appearance) }
                            ?? png(v, appearance: appearance)
                        try data.write(to: file)
                        n += 1
                    }
                }
            }
            return n
        }

        /// `view` as the app shows it in a window (`AppWindows`: a hosting controller in a titled window), title bar
        /// and toolbar included, drawn offscreen.
        static func windowPNG(_ view: some View, title: String, appearance: NSAppearance.Name) throws -> Data {
            let window = NSWindow(contentViewController: NSHostingController(rootView: view))
            if !title.isEmpty { window.title = title }  // empty: the window sets its own (settings: the pane)
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: appearance)
            let offscreen = CGPoint(x: -20_000, y: -20_000)
            window.setFrameOrigin(offscreen)
            window.orderFrontRegardless()
            for _ in 0..<6 {
                window.setFrameOrigin(offscreen)  // the content sizes the window: keep it off every screen
                window.contentView?.layoutSubtreeIfNeeded()
                window.displayIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.05))
            }
            defer { window.orderOut(nil) }
            guard let frame = window.contentView?.superview,
                let rep = frame.bitmapImageRepForCachingDisplay(in: frame.bounds)
            else { throw SnapError.render }
            frame.cacheDisplay(in: frame.bounds, to: rep)
            guard let data = rep.representation(using: .png, properties: [:]) else { throw SnapError.render }
            return data
        }

        static func scenes() -> [(String, AnyView)] {
            let store = InMemorySettingsStore()
            func follow(_ s: FollowStatus, failure: String? = nil, nearby: Bool = false) -> FollowModel {
                let m = FollowModel(status: s, actions: nil, macName: "Ana's MacBook")
                if nearby { m.nearby = NearbyPhone(wearer: "Marta", avatar: .v1(c: 6, s: "dots", a: 45, n: 11)) }
                if case .following = s {
                    m.wearer = Profile(name: "Marta", avatar: .v1(c: 6, s: "dots", a: 45, n: 11))
                    m.me = Profile(name: "Ana's MacBook", avatar: .v1(c: 5, s: "drop", a: 45, n: 7))
                }
                return m
            }
            var out: [(String, AnyView)] = []
            for step in SetupFlow.Step.allCases {
                let m = SetupModel(
                    settings: SettingsModel(store: store, follow: follow(.notFollowing), version: "0.12.0"))
                m.loginItem = LoginItem(service: SnapLogin(status: .notRegistered))
                // a signed build: Sukrara may post Time Sensitive, a Focus holds its alerts back by default
                m.settings.timeSensitive = .on
                m.flow.open(step)
                out.append(("setup-\(step.rawValue)", AnyView(SetupView(model: m, close: {}))))
            }
            // the notification step of a build without Time Sensitive (no provisioning profile): Focus settings
            let unavailable = SetupModel(
                settings: SettingsModel(store: store, follow: follow(.notFollowing), version: "0.12.0"))
            unavailable.settings.timeSensitive = .unavailable
            unavailable.flow.open(.notifications)
            out.append(("setup-notifications-no-time-sensitive", AnyView(SetupView(model: unavailable, close: {}))))
            // the alert step: the wearer's levels (mirrored), levels out of order, this Mac's own (Custom, one off)
            let wearerAlerts = WearerSettings(
                json: #"{"alerts":{"alerts":{"low":{"on":true,"mgdl":72},"high":{"on":true,"mgdl":200}}}}"#,
                wearer: "Marta")
            func scratchSettings(_ wearer: WearerSettings?) -> SettingsModel {
                SettingsModel(
                    store: InMemorySettingsStore(), follow: follow(.notFollowing), wearer: wearer, version: "0.12.0")
            }
            let ownAlerts = scratchSettings(nil)
            ownAlerts.settingsFrom = .custom
            ownAlerts.alerts.update { $0[.veryHigh].on = false }
            for (name, settings, edit) in [
                ("wearer", scratchSettings(wearerAlerts), [AlertKind: Double]()),
                ("order", scratchSettings(nil), [.veryLow: 65, .low: 60]), ("custom", ownAlerts, [:]),
            ] {
                let m = SetupModel(settings: settings)
                m.flow.open(.alerts)
                m.editedThresholds = edit
                out.append(("setup-alerts-\(name)", AnyView(SetupView(model: m, close: {}))))
            }
            // Open at login after the button: waiting for approval, refused, on (the step stays until it's on)
            let refusedLogin = LoginItem(service: SnapLogin(status: .notRegistered, refuses: true))
            refusedLogin.set(true)
            for (name, item) in [
                ("approval", LoginItem(service: SnapLogin(status: .requiresApproval))),
                ("failed", refusedLogin), ("on", LoginItem(service: SnapLogin(status: .enabled))),
            ] {
                let m = SetupModel(
                    settings: SettingsModel(store: store, follow: follow(.notFollowing), version: "0.12.0"))
                m.loginItem = item
                m.flow.open(.openAtLogin)
                out.append(("setup-openAtLogin-\(name)", AnyView(SetupView(model: m, close: {}))))
            }
            let doneModel = SetupModel(
                settings: SettingsModel(store: store, follow: follow(.notFollowing), version: "0.12.0"))
            doneModel.flow = SetupFlow(states: [
                .welcome: .done, .follow: .skipped, .alerts: .done, .notifications: .done, .openAtLogin: .done,
            ])
            out.append(("setup-done-skipped", AnyView(SetupView(model: doneModel, close: {}))))
            let nearbyModel = follow(.notFollowing, nearby: true)
            out.append(("follow-nearby", AnyView(FollowStep(follow: nearbyModel).padding().frame(width: 520))))
            for (name, check) in [
                ("follow-nearby-waiting", LinkState.PairingCheck(code: nil, confirmed: false)),
                ("follow-nearby-compare", LinkState.PairingCheck(code: "021472", confirmed: false)),
                ("follow-nearby-confirmed", LinkState.PairingCheck(code: "021472", confirmed: true)),
            ] {
                let waiting = follow(.waiting(wearer: "Marta", pairing: check))
                out.append((name, AnyView(FollowStep(follow: waiting).padding().frame(width: 520))))
            }
            // #173 / #174: a link join waiting with its match code, the Allow arrived, a request that ended
            let linkWaiting = follow(.waiting(wearer: "Marta", pairing: nil))
            linkWaiting.matchCode = "4821"
            out.append(("follow-link-waiting", AnyView(FollowStep(follow: linkWaiting).padding().frame(width: 520))))
            let allowed = follow(.following(wearer: "Marta", lastReading: nil, canWrite: false))
            out.append(("follow-link-allowed", AnyView(FollowStep(follow: allowed).padding().frame(width: 520))))
            let declined = follow(.declined)
            declined.wearer = Profile(name: "Marta", avatar: nil)
            out.append(("follow-link-declined", AnyView(FollowStep(follow: declined).padding().frame(width: 520))))
            let draft = IdentityDraft(name: "Ana's MacBook", avatar: .v1(c: 5, s: "drop", a: 45, n: 7))
            out.append(
                (
                    "identity-sheet",
                    AnyView(IdentitySheet(draft: draft, wearer: "Marta", onSave: { _, _ in }, onCancel: {}))
                ))
            let open = IdentityDraft(name: "Ana's MacBook", avatar: .v1(c: 1, s: "rings", a: 90, n: 7))
            open.customizing = true
            out.append(
                (
                    "identity-customize",
                    AnyView(IdentitySheet(draft: open, wearer: "Marta", onSave: { _, _ in }, onCancel: {}))
                ))
            // the grey initial: no shape or colour selected
            let initial = IdentityDraft(name: "Ana's MacBook", avatar: nil)
            initial.customizing = true
            out.append(
                (
                    "identity-customize-initial",
                    AnyView(IdentitySheet(draft: initial, wearer: "Marta", onSave: { _, _ in }, onCancel: {}))
                ))
            out.append(
                ("follow-not-following", AnyView(FollowStep(follow: follow(.notFollowing)).padding().frame(width: 520)))
            )
            let bad = follow(.notFollowing)
            bad.linkText = "hello"
            Task { await bad.lookUp() }
            out.append(("follow-error-invalid", AnyView(FollowStep(follow: bad).padding().frame(width: 520))))
            let noQR = follow(.notFollowing)
            Task { await noQR.lookUp(qrText: nil) }
            out.append(("follow-error-noqr", AnyView(FollowStep(follow: noQR).padding().frame(width: 520))))
            let invited = follow(.notFollowing)
            invited.invite = Invite(
                identity: String(repeating: "ab", count: 32), token: "t", expiry: Int.max, relays: [],
                profile: Profile(name: "Marta", avatar: .v1(c: 6, s: "dots", a: 45, n: 11)))
            out.append(("follow-invite-confirm", AnyView(FollowStep(follow: invited).padding().frame(width: 520))))
            // the QR scanner in each state, over the stand-in picture (never the Mac's camera)
            let twoCameras = [
                CameraChoice(id: "built-in", name: "FaceTime HD Camera"),
                CameraChoice(id: "iphone", name: "Ana's iPhone Camera"),
            ]
            let scans: [(String, SyntheticCamera?, [String])] = [
                ("ask", SyntheticCamera(access: .notAsked), []), ("denied", SyntheticCamera(access: .denied), []),
                ("nocamera", SyntheticCamera(cameras: []), []), ("offline", nil, []),
                ("scanning", SyntheticCamera(cameras: twoCameras), []),
                ("wrong", SyntheticCamera(), ["https://example.com/menu"]),
                ("found", SyntheticCamera(), [SyntheticCamera.invite]),
            ]
            for (name, camera, seen) in scans {
                let m = follow(.notFollowing)
                m.scanner = QRScanner(camera: camera) { _ in }
                m.scanner?.wrongFor = .seconds(3600)  // the warning stays until it's drawn
                m.scanner?.read(seen)
                out.append(("follow-scan-\(name)", AnyView(FollowStep(follow: m).padding().frame(width: 520))))
            }
            let following = follow(
                .following(wearer: "Marta", lastReading: Date().addingTimeInterval(-180), canWrite: true))
            let settings = SettingsModel(store: store, follow: following, version: "0.12.0")
            // Trend arrow's preview: a rise of about 1.2 a minute while the sensor says 0.8 (#204), worked out by the
            // app's one place (AppModel)
            let trendApp = AppModel(
                plan: LaunchPlan(mode: .normal, services: .none, data: .isolated(root: URL(fileURLWithPath: "/tmp"))))
            let riseEnd = Date().addingTimeInterval(-40)
            trendApp.apply(
                readings: (0...15).map { i in
                    Reading(
                        t: riseEnd.addingTimeInterval(Double(i - 15) * 60), mgdl: 158 + (i * 6) / 5, sensorRate: 0.8)
                })
            settings.newestReading = { trendApp.newestReading }
            // Juggluco on this network: on, in each state (its own defaults: the switch is on there)
            let lanStore = InMemorySettingsStore()
            lanStore.set(SourceKind.lanJuggluco.rawValue, for: MacSettings.sourceKind)
            let lanStates: [(String, LanState, String?)] = [
                ("looking", .looking, nil), ("notfound", .notFound, nil),
                ("connected", .connected("192.168.1.23"), nil),
                ("secret", .unauthorized("192.168.1.23"), nil), ("paused", .off, "Marta"),
                ("localnetwork", .localNetworkDenied, nil),
            ]
            for (name, state, paused) in lanStates {
                let m = SettingsModel(store: lanStore, follow: follow(.notFollowing), version: "0.12.0")
                m.juggluco.available = { true }
                m.juggluco.state = { state }
                m.juggluco.pausedFor = { paused }
                m.pane = .source
                out.append(
                    (
                        "settings-source-juggluco-\(name)",
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                            ).settingsWindow(m, width: 560, height: 820))
                    ))
            }
            out.append(
                (
                    "settings-general",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: LoginItem(service: SnapLogin(status: .enabled)),
                            updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(settings, width: 560, height: 900))
                ))
            out.append(
                (
                    "settings-general-login-approval",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: LoginItem(service: SnapLogin(status: .requiresApproval)),
                            updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(settings, width: 560, height: 700))
                ))
            let refused = LoginItem(service: SnapLogin(status: .notRegistered, refuses: true))
            refused.set(true)
            out.append(
                (
                    "settings-general-login-failed",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: refused, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(settings, width: 560, height: 700))
                ))
            out.append(
                (
                    "settings-general-login-unavailable",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(settings, width: 560, height: 700))
                ))
            let alerts = SettingsModel(store: store, follow: following, version: "0.12.0")
            alerts.pane = .alerts
            alerts.timeSensitive = .on
            out.append(
                (
                    "settings-alerts",
                    AnyView(
                        SettingsView(
                            model: alerts, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(alerts, width: 560, height: 2300))
                ))
            let mirrored = SettingsModel(
                store: store, follow: following,
                wearer: WearerSettings(
                    json: #"{"graph":{"low":72},"alerts":{"alerts":{"low":{"on":true,"mgdl":72}}}}"#, wearer: "Marta"),
                version: "0.12.0")
            mirrored.pane = .alerts
            mirrored.timeSensitive = .on
            out.append(
                (
                    "settings-alerts-mirrored",
                    AnyView(
                        SettingsView(
                            model: mirrored, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(mirrored, width: 560, height: 900))
                ))
            // the Graph and Metrics panes: this Mac's own (Custom, a few values changed) and the wearer's (read-only)
            func scratch() -> InMemorySettingsStore { InMemorySettingsStore() }
            let ownStore = scratch()
            let own = SettingsModel(store: ownStore, follow: following, version: "0.12.0")
            own.settingsFrom = .custom
            own.updateGraph {
                $0.lines = [100, 250]
                $0.slots[2].on = true
                $0.ringOrder = [.veryLow, .low, .tight, .target, .high, .veryHigh]
            }
            // the widgets differ (docs/38): their own time shown, the sensor time left off on them
            own.setPlaceHours(6, .widget)
            own.setSensorPlace(false, .widget)
            let marta = WearerSettings(
                json: #"{"graph":{"tight":130,"defaultHours":6,"lines":"","lastPlace":"END"}}"#, wearer: "Marta")
            let fromWearer = SettingsModel(store: scratch(), follow: following, wearer: marta, version: "0.12.0")
            // a setting's page in the Graph pane: the widgets following the menu bar (quiet), with their own, following
            // Marta (the menu bar's value read-only)
            let samesStore = scratch()
            SettingsModel(store: samesStore, follow: following, version: "0.12.0").settingsFrom = .custom
            let pages: [(String, any SettingsStoring, WearerSettings?, SettingsModel.GraphPage)] = [
                ("settings-graph-time-shown", samesStore, nil, .hours),
                ("settings-graph-time-shown-own", ownStore, nil, .hours),
                ("settings-graph-time-shown-wearer", fromWearer.store, marta, .hours),
                ("settings-graph-sensor-time", ownStore, nil, .sensorTime),
                ("settings-graph-sensor-time-same", samesStore, nil, .sensorTime),
            ]
            for (name, store, wearer, page) in pages {
                let m = SettingsModel(store: store, follow: following, wearer: wearer, version: "0.12.0")
                m.pane = .graph
                m.graphPage = page
                out.append(
                    (
                        name,
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                            ).settingsWindow(m, width: 600, height: 360))
                    ))
            }
            for (name, pane, model, height) in [
                ("settings-readings", SettingsModel.Pane.readings, own, 1060.0),
                ("settings-readings-wearer", .readings, fromWearer, 1000),
                ("settings-graph", .graph, own, 1280),
                ("settings-graph-wearer", .graph, fromWearer, 860),
                ("settings-metrics", .metrics, own, 1340),
                ("settings-metrics-wearer", .metrics, fromWearer, 760),
                (("settings-source", .source, own, 760)),
            ] {
                model.pane = pane
                let m = SettingsModel(store: model.store, follow: model.follow, wearer: model.wearer, version: "0.12.0")
                m.pane = pane
                m.newestReading = { trendApp.newestReading }
                m.source.following = { true }  // these scenes follow Marta
                out.append(
                    (
                        name,
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, answerSource: {},
                                openAbout: {}
                            ).settingsWindow(m, width: 600, height: height))
                    ))
            }
            // following nobody: Follow… and Scan QR Code…
            let nobody = SettingsModel(store: scratch(), follow: follow(.notFollowing), version: "0.12.0")
            nobody.pane = .source
            out.append(
                (
                    "settings-following-none",
                    AnyView(
                        SettingsView(
                            model: nobody, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(nobody, width: 600, height: 800))
                ))
            // Alerts › Fine-tuning… and Falling fast's Details… (its trend arrow is Readings' setting)
            out.append(("alerts-fine-tuning", AnyView(FineTuning(model: own) {})))
            // a metric's Details… (the third, on; the one without a recent treatment) and the last treatment's
            out.append(("metrics-details-third", AnyView(MetricDetails(model: own, slot: .third) {})))
            out.append(("metrics-details-no-recent", AnyView(MetricDetails(model: own, slot: .noRecent) {})))
            out.append(("metrics-details-last", AnyView(LastTreatmentDetails(model: own) {})))
            // a quiet rule's Details…: its switch, its length and what it does
            out.append(("alerts-quiet-details", AnyView(QuietDetails(model: own, which: .afterCarbs) {})))
            out.append(
                (
                    "alerts-details-falling-fast",
                    AnyView(AlertDetails(model: own, kind: .fallingFast, controller: nil, readOnly: false) {})
                ))
            let general = SettingsModel(store: ownStore, follow: following, wearer: marta, version: "0.12.0")
            general.pane = .general
            general.newestReading = { trendApp.newestReading }
            out.append(
                (
                    "settings-general-custom",
                    AnyView(
                        SettingsView(
                            model: general, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                        ).settingsWindow(general, width: 600, height: 880))
                ))
            out.append(
                (
                    "treatment-add",
                    AnyView(
                        AddTreatmentView(
                            focus: .carbs,
                            quiet: { _, carbs, _ in carbs ? L10n.t("Lows quiet for \(Durations.text(15))") : nil },
                            save: { _, _ in }, cancel: {}))
                ))
            // the When? popover's two ways: minutes ago (a number field) and a clock time
            out.append(("treatment-when-ago", AnyView(WhenPicker(when: .ago(15)) { _ in })))
            out.append(("treatment-when-time", AnyView(WhenPicker(when: .now) { _ in })))
            let t0 = Int64(Date().timeIntervalSince1970)
            let rows: [TreatmentsModel.Row] = [
                .init(treatment: Treatment(id: "1", k: .rapid, a: 4.5, t: t0 - 600, u: 1, o: .mac), delivery: .sending),
                .init(treatment: Treatment(id: "2", k: .carbs, a: 30, t: t0 - 900, u: 1, o: .mac), delivery: .failed),
                .init(treatment: Treatment(id: "3", k: .long, a: 12, t: t0 - 7200, u: 1, o: .phone), delivery: nil),
                .init(
                    treatment: Treatment(id: "4", k: .note, a: 0, t: t0 - 9000, n: "Walk", u: 1, o: .mac),
                    delivery: .accepted),
                .init(
                    treatment: Treatment(
                        id: "5", k: .rapid, a: 8.5, t: t0 - 2400, u: 1, o: .phone, pr: 1.5, pen: "NP1",
                        pd: [
                            .init(t: t0 - 2580, a: 1, p: true), .init(t: t0 - 2520, a: 0.5, p: true),
                            .init(t: t0 - 2400, a: 8.5),
                        ]),
                    delivery: nil),
            ]
            let tm = TreatmentsModel(rows: rows, canWrite: true, actions: nil)
            tm.wearer = "Marta"
            // the note just reached the phone: its check shows for a few seconds
            tm.set("4", .sent)
            tm.set("4", .accepted)
            // the glucose then from a synthetic day, each reading with its trend (the app's one place: AppModel)
            let glucose = AppModel(
                plan: LaunchPlan(mode: .normal, services: .none, data: .isolated(root: URL(fileURLWithPath: "/tmp"))))
            glucose.apply(
                readings: RealisticSynthetic.readings(until: TimeInterval(t0) - 60, days: 1).map {
                    Reading(t: Date(timeIntervalSince1970: $0.t), mgdl: $0.sgv, sensorRate: $0.rate)
                })
            tm.readings = { glucose.allReadings }
            out.append(("treatments", AnyView(TreatmentsView(model: tm, add: {}).frame(width: 460, height: 380))))
            // Edit Treatment on an amount and on a note (nothing changed yet: Save off)
            out.append(
                (
                    "treatment-edit",
                    AnyView(EditTreatmentView(treatment: rows[0].treatment, save: { _, _, _ in }, cancel: {}))
                ))
            out.append(
                (
                    "treatment-edit-note",
                    AnyView(EditTreatmentView(treatment: rows[3].treatment, save: { _, _, _ in }, cancel: {}))
                ))
            out.append(
                (
                    "treatments-empty",
                    AnyView(
                        TreatmentsView(model: TreatmentsModel(rows: [], canWrite: false, actions: nil), add: {}).frame(
                            width: 460, height: 300))
                ))
            // the report over a synthetic fortnight, a thin week (low coverage) and nothing
            let plan = LaunchPlan(mode: .normal, services: .none, data: .isolated(root: URL(fileURLWithPath: "/tmp")))
            for (name, days) in [("report", 14), ("report-low-coverage", 3), ("report-empty", 0)] {
                let app = AppModel(plan: plan)
                let now = Date().timeIntervalSince1970
                app.apply(
                    readings: days == 0
                        ? []
                        : RealisticSynthetic.readings(until: now - 60, days: days).map {
                            Reading(t: Date(timeIntervalSince1970: $0.t), mgdl: $0.sgv, sensorRate: $0.rate)
                        })
                let report = ReportModel(store: store, app: app)
                report.days = days == 3 ? 7 : 14
                out.append((name, AnyView(ReportView(model: report))))
            }
            // About Sukrara: the GitHub version and the App Store version
            for (name, distribution) in [("about-github", Distribution.github), ("about-appstore", .appStore)] {
                let about = AboutView(
                    version: "0.12.0", build: "12000", distribution: distribution, appBuild: .release, icon: brandIcon)
                out.append((name, AnyView(about.aboutWindow())))
            }
            out += nightscoutScenes(follow: follow(.notFollowing))
            out += libreLinkUpScenes(follow: follow(.notFollowing))
            return out
        }

        /// LibreLinkUp as the source (#213 step 2): the online sources with LibreLinkUp picked, Connect in each state,
        /// Settings › Source in each health state, and the treatment log this Mac keeps.
        private static func libreLinkUpScenes(follow: FollowModel) -> [(String, AnyView)] {
            var out: [(String, AnyView)] = []
            let now = Date()
            let ana = LluPerson(id: "11111111-2222-4333-8444-555555555555", name: "Ana Example")
            let leo = LluPerson(id: "66666666-7777-4888-9999-aaaaaaaaaaaa", name: "Leo")
            func scratch(patient: String = "") -> InMemorySettingsStore {
                let s = InMemorySettingsStore()
                s.set(SourceKind.libreLinkUp.rawValue, for: MacSettings.sourceKind)
                s.set(patient, for: MacSettings.libreLinkUpPatient)
                return s
            }
            func snap(
                _ outcome: SourceHealth.Outcome?, _ ages: [TimeInterval], email: String = "alex@example.com",
                people: [LluPerson] = [ana], signInAgain: Bool = false
            ) -> LibreLinkUpSnapshot {
                LibreLinkUpSnapshot(
                    email: email, signInAgain: signInAgain, people: people, patient: ana.id, name: ana.name,
                    running: true, outcome: outcome,
                    readings: ages.map { Reading(t: now.addingTimeInterval(-$0), mgdl: 118) },
                    monitoring: outcome == .ok,
                    sensor: LluSensor(serial: "0D00ABC123", start: Int(now.timeIntervalSince1970) - 4 * 86_400))
            }
            // the sources this Mac reads itself, LibreLinkUp picked
            let online = SetupModel(settings: SettingsModel(store: scratch(), follow: follow, version: "0.15.0"))
            online.pickedPhone = false
            online.pickedKind = .libreLinkUp
            online.flow.open(.source)
            out.append(("setup-source-online-librelinkup", AnyView(SetupView(model: online, close: {}))))
            // Connect: the form, refused, several people, no one shared, waiting, received
            let connects: [(String, String, LibreLinkUpSnapshot, LluConnectResult?)] = [
                ("form", "", .off, nil),
                ("refused", "", .off, .badCredentials),
                ("toomany", "", .off, .rateLimited),
                ("choose", "", snap(nil, [], people: [ana, leo]), nil),
                ("noone", "", snap(nil, [], people: []), .noOne),
                ("pickedgone", ana.id, snap(.notShared, [3000], people: [leo]), nil),
                ("waiting", ana.id, snap(nil, []), nil),
                ("received", ana.id, snap(.ok, [60]), nil),
            ]
            for (name, patient, snapshot, result) in connects {
                let m = SetupModel(
                    settings: SettingsModel(store: scratch(patient: patient), follow: follow, version: "0.15.0"))
                let llu = m.settings.libreLinkUp
                llu.snapshot = { snapshot }
                llu.available = { true }
                llu.now = { now }
                llu.result = result
                if result == .badCredentials || result == .rateLimited {
                    llu.emailDraft = "alex@example.com"
                    llu.passwordDraft = "synthetic"
                }
                if name == "choose" { llu.picked = leo.id }
                m.flow.open(.follow)
                out.append(("setup-connect-librelinkup-\(name)", AnyView(SetupView(model: m, close: {}))))
            }
            // Settings › Source: live (one person), several people, new terms, not shared, password refused, signed out
            let states: [(String, String, LibreLinkUpSnapshot)] = [
                ("live", ana.id, snap(.ok, [70, 130])),
                ("several", ana.id, snap(.ok, [70], people: [ana, leo])),
                ("terms", ana.id, snap(.terms, [1500])),
                ("notshared", ana.id, snap(.notShared, [3000], people: [])),
                ("pickedgone", ana.id, snap(.notShared, [3000], people: [leo])),
                ("refused", ana.id, snap(.refused, [1500], signInAgain: true)),
                ("signedout", "", .off),
            ]
            for (name, patient, snapshot) in states {
                let store = scratch(patient: patient)
                let m = SettingsModel(store: store, follow: follow, version: "0.15.0")
                m.libreLinkUp.snapshot = { snapshot }
                m.libreLinkUp.available = { true }
                m.libreLinkUp.now = { now }
                m.sensorLife.now = { now }
                m.sensorLife.sensor = {
                    snapshot.sensor.flatMap {
                        SensorLifeSettings(store: store).life(
                            .libreLinkUp, said: SensorLives.Said(id: $0.serial, start: $0.start),
                            now: now.timeIntervalSince1970)
                    }
                }
                if snapshot.signInAgain { m.libreLinkUp.emailDraft = snapshot.email }
                m.pane = .source
                out.append(
                    (
                        "settings-source-librelinkup-\(name)",
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                            ).settingsWindow(m, width: 560, height: 1120))
                    ))
            }
            return out
        }

        /// Nightscout as the source (#213 step 1): setup's first question, the online sources, Connect in each state,
        /// Settings › Source in each health state, and the treatment log with the site's read-only entries.
        private static func nightscoutScenes(follow: FollowModel) -> [(String, AnyView)] {
            var out: [(String, AnyView)] = []
            let now = Date()
            func scratch(_ kind: SourceKind?) -> InMemorySettingsStore {
                let s = InMemorySettingsStore()
                if let kind { s.set(kind.rawValue, for: MacSettings.sourceKind) }
                return s
            }
            func readings(_ ages: [TimeInterval]) -> [Reading] {
                ages.map { Reading(t: now.addingTimeInterval(-$0), mgdl: 118) }
            }
            func snap(_ outcome: SourceHealth.Outcome?, _ ages: [TimeInterval], readOnly: Bool = false)
                -> NightscoutSnapshot
            {
                NightscoutSnapshot(
                    outcome: outcome, host: "ns.example.org", readings: readings(ages), monitoring: outcome == .ok,
                    readOnly: readOnly)
            }
            // the first question: "No · Read it here"
            for (name, picked) in [("setup-phone-no", false as Bool?)] {
                let m = SetupModel(settings: SettingsModel(store: scratch(nil), follow: follow, version: "0.12.0"))
                m.pickedPhone = picked
                m.flow.open(.phone)
                out.append((name, AnyView(SetupView(model: m, close: {}))))
            }
            // the sources this Mac reads itself, Nightscout picked
            let online = SetupModel(settings: SettingsModel(store: scratch(nil), follow: follow, version: "0.12.0"))
            online.pickedPhone = false
            online.pickedKind = .nightscout
            online.flow.open(.source)
            out.append(("setup-source-online", AnyView(SetupView(model: online, close: {}))))
            // Connect: the form with a pasted address's problem, connecting, refused, waiting, received
            let connects: [(String, String, NightscoutSnapshot, NightscoutSite.Problem?)] = [
                ("form", "", .off, nil), ("problem", "", .off, .https), ("connecting", "x", snap(nil, []), nil),
                ("refused", "x", snap(.refused, []), nil), ("unreachable", "x", snap(.server, []), nil),
                ("waiting", "x", snap(.ok, []), nil), ("received", "x", snap(.ok, [60]), nil),
            ]
            for (name, site, snapshot, problem) in connects {
                let store = scratch(.nightscout)
                if !site.isEmpty { store.set("https://ns.example.org", for: MacSettings.nightscoutURL) }
                let m = SetupModel(settings: SettingsModel(store: store, follow: follow, version: "0.12.0"))
                m.settings.nightscout.snapshot = { snapshot }
                m.settings.nightscout.available = { true }
                m.flow.open(.follow)
                if problem != nil {
                    m.settings.nightscout.siteDraft = "http://ns.example.org"
                    m.settings.nightscout.connect()
                }
                out.append(("setup-connect-nightscout-\(name)", AnyView(SetupView(model: m, close: {}))))
            }
            // Settings › Source: live, late, signed out, nothing new, a token that can only read
            let states: [(String, NightscoutSnapshot)] = [
                ("live", snap(.ok, [120, 420])), ("late", snap(.ok, [600])), ("signedout", snap(.refused, [1500])),
                ("silent", snap(.ok, [1500])), ("readonly", snap(.ok, [60], readOnly: true)),
            ]
            for (name, snapshot) in states {
                let store = scratch(.nightscout)
                store.set("https://ns.example.org", for: MacSettings.nightscoutURL)
                let m = SettingsModel(store: store, follow: follow, version: "0.12.0")
                m.nightscout.snapshot = { snapshot }
                m.nightscout.available = { true }
                m.nightscout.hasSecret = true
                m.pane = .source
                out.append(
                    (
                        "settings-source-nightscout-\(name)",
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                            ).settingsWindow(m, width: 560, height: 1180))
                    ))
            }
            // the sensor's start set by the person (a site without a Sensor Start), and the picker open on it
            for name in ["manual", "picking"] {
                let store = scratch(.nightscout)
                store.set("https://ns.example.org", for: MacSettings.nightscoutURL)
                store.set(
                    (now.timeIntervalSince1970 - 2 * 86_400 - 3 * 3600).rounded(.down), for: MacSettings.manualStart)
                store.setNumber(14_400, for: MacSettings.nightscoutWear)
                let m = SettingsModel(store: store, follow: follow, version: "0.15.0")
                let snapshot = snap(.ok, [120, 420])
                m.nightscout.snapshot = { snapshot }
                m.nightscout.available = { true }
                m.nightscout.hasSecret = true
                m.sensorLife.now = { now }
                if name == "picking" { m.sensorLife.pick() }
                m.pane = .source
                out.append(
                    (
                        "settings-source-nightscout-\(name)",
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updates: Self.updates, openFollow: {}, openAbout: {}
                            ).settingsWindow(m, width: 560, height: 1240))
                    ))
            }
            // the treatment log: this Mac's own and the site's, read-only
            let t0 = Int64(now.timeIntervalSince1970)
            let own = Treatment(id: "o1", k: .rapid, a: 3.5, t: t0 - 900, u: 1, o: .mac)
            let site = [
                Treatment(id: "s1", k: .carbs, a: 45, t: t0 - 1800, u: 1, o: .phone),
                Treatment(id: "s2", k: .rapid, a: 4.5, t: t0 - 1800, n: "", u: 1, o: .phone),
            ]
            let tm = TreatmentsModel(rows: [], canWrite: true, actions: nil)
            tm.pulled = Set(site.map(\.id))
            tm.sync([own] + site, deliveries: ["o1": .sent], canWrite: true)
            out.append(
                ("treatments-nightscout", AnyView(TreatmentsView(model: tm, add: {}).frame(width: 460, height: 380))))
            return out
        }

        /// Renders `view` inside an off-screen window: lists (NSTableView), tab strips and toolbars only draw their
        /// content once they're in a window.
        static func png(_ view: some View, appearance: NSAppearance.Name) throws -> Data {
            let host = NSHostingView(rootView: view)
            host.appearance = NSAppearance(named: appearance)
            let size = host.fittingSize
            let window = NSWindow(
                contentRect: CGRect(origin: CGPoint(x: -20_000, y: -20_000), size: size), styleMask: [.borderless],
                backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.appearance = host.appearance
            window.contentView = host
            host.frame = CGRect(origin: .zero, size: size)
            window.orderFrontRegardless()
            for _ in 0..<4 {
                host.layoutSubtreeIfNeeded()
                host.displayIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.05))
            }
            defer { window.orderOut(nil) }
            guard let rep = host.bitmapImageRepForCachingDisplay(in: host.bounds) else { throw SnapError.render }
            host.cacheDisplay(in: host.bounds, to: rep)
            guard let data = rep.representation(using: .png, properties: [:]) else { throw SnapError.render }
            return data
        }

        enum SnapError: Error { case render }

        /// The brand's app icon for About (a render isn't the app bundle, whose icon this is).
        static var brandIcon: NSImage { BrandIcon.image ?? NSApp.applicationIconImage }

    }

    /// A login item service in a fixed state (snapshots never touch the real login items).
    struct SnapLogin: LoginService {
        var status: SMAppService.Status
        var refuses = false
        struct Refused: LocalizedError {
            var errorDescription: String? { "Operation not permitted" }
        }
        func register() throws { if refuses { throw Refused() } }
        func unregister() throws {}
    }

    /// Finishes the window a render draws in (`windowPNG`) as the app does: `configure` once it is in a window.
    struct SnapWindow: NSViewRepresentable {
        let configure: (NSWindow) -> AnyObject?

        final class Host: NSView {
            var configure: ((NSWindow) -> AnyObject?)?
            var kept: AnyObject?
            override func viewDidMoveToWindow() {
                super.viewDidMoveToWindow()
                guard let window, let configure else { return }
                self.configure = nil
                kept = configure(window)
            }
        }

        func makeNSView(context: Context) -> Host {
            let v = Host()
            v.configure = configure
            return v
        }
        func updateNSView(_ nsView: Host, context: Context) {}
    }

    extension View {
        /// The settings window as the app opens it (`SettingsWindowChrome`: the toolbar, the pane's title), drawn at
        /// `height` so the whole form shows.
        func settingsWindow(_ model: SettingsModel, width: CGFloat, height: CGFloat) -> some View {
            frame(width: SettingsWindowChrome.width, height: height)
                .background(
                    SnapWindow { SettingsWindowChrome(window: $0, model: model, fitsPane: false) })
        }

        /// The About window as the app opens it (`AboutWindow`).
        func aboutWindow() -> some View {
            background(
                SnapWindow {
                    AboutWindow.configure($0)
                    return nil
                })
        }
    }
#endif
