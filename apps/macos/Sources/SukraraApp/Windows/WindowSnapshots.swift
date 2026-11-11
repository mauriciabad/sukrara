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
    /// Offline: a scratch defaults suite, no runtime behind the models (actions nil).
    public enum WindowSnapshots {
        /// Renders every word in one language (`es`), whatever the Mac's: the windows' (String Catalog) and the
        /// card's, the widget's and the mute lines' (`AppLanguage`).
        public static func useLanguage(_ language: String) {
            L10n.use(language: language)
            AppLanguage.forced = AppLanguage(rawValue: language)
        }

        /// Scenes drawn in their real window (title bar and toolbar, e.g. the Treatments window's "+"), by window title.
        static var windowed: [String: String] {
            ["treatments": L10n.t("Treatments"), "treatments-empty": L10n.t("Treatments")]
        }

        public static func render(to dir: URL) throws -> Int {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var n = 0
            let windowed = Self.windowed
            for (name, view) in scenes() {
                for dark in [false, true] {
                    for large in [false, true] {
                        let v =
                            view
                            .environment(\.colorScheme, dark ? .dark : .light)
                            .dynamicTypeSize(large ? .xxxLarge : .large)
                            .background(Color(nsColor: .windowBackgroundColor))
                        let file = dir.appending(path: "\(name)-\(dark ? "dark" : "light")\(large ? "-large" : "").png")
                        let appearance: NSAppearance.Name = dark ? .darkAqua : .aqua
                        let data =
                            try windowed[name].map { try windowPNG(v, title: $0, appearance: appearance) }
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
            window.title = title
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

        private static func scenes() -> [(String, AnyView)] {
            let suite = "app.mauri.sukrara.snap.\(UUID().uuidString)"
            let store = DefaultsSettingsStore(defaults: UserDefaults(suiteName: suite)!)
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
                m.settings.timeSensitive = .unavailable  // a build without a provisioning profile
                m.flow.open(step)
                out.append(("setup-\(step.rawValue)", AnyView(SetupView(model: m, close: {}))))
            }
            // the notification step of a build whose alarms break through a Focus
            let timeSensitive = SetupModel(
                settings: SettingsModel(store: store, follow: follow(.notFollowing), version: "0.12.0"))
            timeSensitive.settings.timeSensitive = .on
            timeSensitive.flow.open(.notifications)
            out.append(("setup-notifications-time-sensitive", AnyView(SetupView(model: timeSensitive, close: {}))))
            // the alert step: the wearer's levels (mirrored), levels out of order, this Mac's own (Custom, one off)
            let wearerAlerts = WearerSettings(
                json: #"{"alerts":{"alerts":{"low":{"on":true,"mgdl":72},"high":{"on":true,"mgdl":200}}}}"#,
                wearer: "Marta")
            func scratchSettings(_ wearer: WearerSettings?) -> SettingsModel {
                let s = DefaultsSettingsStore(defaults: UserDefaults(suiteName: "\(suite).\(UUID().uuidString)")!)
                return SettingsModel(store: s, follow: follow(.notFollowing), wearer: wearer, version: "0.12.0")
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
            // Juggluco on this network: on, in each state (its own defaults: the switch is on there)
            let lanStore = DefaultsSettingsStore(defaults: UserDefaults(suiteName: suite + ".lan")!)
            lanStore.set(true, for: MacSettings.jugglucoLan)
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
                out.append(
                    (
                        "settings-general-juggluco-\(name)",
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                            ).frame(width: 560, height: 820))
                    ))
            }
            out.append(
                (
                    "settings-general",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: LoginItem(service: SnapLogin(status: .enabled)),
                            updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 640))
                ))
            out.append(
                (
                    "settings-general-login-approval",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: LoginItem(service: SnapLogin(status: .requiresApproval)),
                            updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 700))
                ))
            let refused = LoginItem(service: SnapLogin(status: .notRegistered, refuses: true))
            refused.set(true)
            out.append(
                (
                    "settings-general-login-failed",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: refused, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 700))
                ))
            out.append(
                (
                    "settings-general-login-unavailable",
                    AnyView(
                        SettingsView(
                            model: settings, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 700))
                ))
            let alerts = SettingsModel(store: store, follow: following, version: "0.12.0")
            alerts.pane = .alerts
            alerts.timeSensitive = .unavailable
            out.append(
                (
                    "settings-alerts",
                    AnyView(
                        SettingsView(
                            model: alerts, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 900))
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
                            model: mirrored, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 560, height: 900))
                ))
            // the Graph and Rings panes: this Mac's own (Custom, a few values changed) and the wearer's (read-only)
            func scratch() -> DefaultsSettingsStore {
                DefaultsSettingsStore(defaults: UserDefaults(suiteName: "app.mauri.sukrara.snap.\(UUID().uuidString)")!)
            }
            let ownStore = scratch()
            let own = SettingsModel(store: ownStore, follow: following, version: "0.12.0")
            own.settingsFrom = .custom
            own.updateGraph {
                $0.lines = [100, 250]
                $0.ringCount = 3
                $0.ringOrder = [.veryLow, .low, .tight, .target, .high, .veryHigh]
            }
            own.setPlaceHours(24, .widget)
            let marta = WearerSettings(
                json: #"{"graph":{"tight":130,"defaultHours":6,"lines":"","lastPlace":"END"}}"#, wearer: "Marta")
            let fromWearer = SettingsModel(store: scratch(), follow: following, wearer: marta, version: "0.12.0")
            for (name, pane, model, height) in [
                ("settings-graph", SettingsModel.Pane.graph, own, 1500.0),
                ("settings-graph-wearer", .graph, fromWearer, 900),
                ("settings-rings", .rings, own, 1300),
                ("settings-rings-wearer", .rings, fromWearer, 700),
                ("settings-following", .following, own, 560),
            ] {
                model.pane = pane
                let m = SettingsModel(store: model.store, follow: model.follow, wearer: model.wearer, version: "0.12.0")
                m.pane = pane
                out.append(
                    (
                        name,
                        AnyView(
                            SettingsView(
                                model: m, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                            ).frame(width: 600, height: height))
                    ))
            }
            // following nobody: Follow… and Scan QR Code…
            let nobody = SettingsModel(store: scratch(), follow: follow(.notFollowing), version: "0.12.0")
            nobody.pane = .following
            out.append(
                (
                    "settings-following-none",
                    AnyView(
                        SettingsView(
                            model: nobody, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 600, height: 560))
                ))
            let general = SettingsModel(store: ownStore, follow: following, wearer: marta, version: "0.12.0")
            general.pane = .general
            out.append(
                (
                    "settings-general-custom",
                    AnyView(
                        SettingsView(
                            model: general, loginItem: nil, updateResult: nil, checkForUpdates: {}, openFollow: {}
                        ).frame(width: 600, height: 860))
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
                            Reading(t: Date(timeIntervalSince1970: $0.t), mgdl: $0.sgv, rate: $0.rate)
                        })
                let report = ReportModel(store: store, app: app)
                report.days = days == 3 ? 7 : 14
                out.append((name, AnyView(ReportView(model: report))))
            }
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
#endif
