// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Network
import SukraraCore
import SukraraGraph
import SukraraShare

/// The menu bar app shell. Services start only through `plan.services.start(using:)` (`AppServices`), before the
/// model and UI exist, so a notification answer that launched the app finds the alert engine already restored.
public final class AppDelegate: NSObject, NSApplicationDelegate {
    public let model: AppModel
    private let services: AppServices
    private let contextMenu = ContextMenu()
    private var statusItem: StatusItemController?
    private var observers: [NSObjectProtocol] = []
    private let network = NWPathMonitor()
    /// The last path status (nil before the monitor's first report).
    private var networkSatisfied: Bool?
    private let store: DefaultsSettingsStore
    private var windows: WindowCoordinator?
    /// The wearer's mirrored settings while following (nil otherwise).
    private var wearer: WearerSettings?
    private var settingsPending = false
    /// `sukrara:` links held until the windows exist (a link that launches the app arrives before them).
    private var linkInbox = LinkInbox()
    /// *Read Glucose Aloud* and its system-wide keys (none in a snapshot run).
    private let readAloud = ReadAloud()
    private var shortcut: GlobalShortcut?

    /// `updater`: the GitHub executable's Sparkle (started only when the plan allows `.updater`); nil in the App Store
    /// executable.
    public init(plan: LaunchPlan, updater: (() -> any UpdaterEngine)? = nil) {
        let services = AppServices(plan: plan, updater: updater)
        plan.services.start(using: services)
        self.services = services
        store = DefaultsSettingsStore(defaults: services.defaults)
        model = services.model()
        model.unit = store.unit
        model.graph = GraphConfig.load(from: store, wearer: nil)
        model.widgetGraph = GraphConfig.load(from: store, wearer: nil, place: .widget)
        if model.lan != nil || model.nightscout != nil {
            secrets = services.secrets
            lanSecret = JugglucoModel.secret(secrets)
            nightscoutSecret = NightscoutModel.secret(secrets)
        }
    }

    /// The Nightscout source's token or API secret, read once from the Keychain and kept as the windows change it.
    private var nightscoutSecret = ""

    /// The site, token and treatments switch while Nightscout is the source with a site, else nil.
    private var nightscoutConfig: NightscoutConfig? {
        let site = store.value(MacSettings.nightscoutURL)
        guard storedKind == .nightscout, !site.isEmpty else { return nil }
        return NightscoutConfig(
            site: site, secret: nightscoutSecret, treatments: store.value(MacSettings.nightscoutTreatments))
    }

    /// Whom LibreLinkUp reads while it is the source and someone is picked, else nil (its account is the source's own,
    /// in the Keychain).
    private var libreLinkUpConfig: LibreLinkUpConfig? {
        let patient = store.value(MacSettings.libreLinkUpPatient)
        guard storedKind == .libreLinkUp, !patient.isEmpty else { return nil }
        return LibreLinkUpConfig(patient: patient)
    }

    /// The Keychain holding Juggluco's API secret (nil when this run has no Juggluco source).
    private var secrets: (any SecretStoring)?
    /// Juggluco's API secret, read once from the Keychain and kept as the Settings window changes it.
    private var lanSecret = ""

    /// Juggluco's address and secret while it is the source (`source.kind` lanJuggluco), else nil.
    private var lanConfig: LanConfig? {
        storedKind == .lanJuggluco
            ? LanConfig(address: store.value(MacSettings.jugglucoAddress), secret: lanSecret) : nil
    }

    /// The source as answered (nil = not answered yet).
    private var storedKind: SourceKind? { SourceKind(rawValue: store.value(MacSettings.sourceKind)) }

    public func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.mainMenu = MainMenu.make()
        let windows = WindowCoordinator(model: model, store: store) { [services] in
            await services.requestNotifications()
        }
        self.windows = windows
        windows.follow.makeCamera = services.scannerCamera
        let juggluco = windows.settings.juggluco
        juggluco.secrets = secrets
        juggluco.available = { [weak model] in model?.lan != nil }
        juggluco.state = { [weak model] in model?.lanSnapshot.state ?? .off }
        juggluco.retry = { [weak model] in model?.retryLan() }
        juggluco.secretChanged = { [weak self] secret in
            self?.lanSecret = secret
            self?.settingsChanged()
        }
        let nightscout = windows.settings.nightscout
        nightscout.secrets = secrets
        nightscout.available = { [weak model] in model?.nightscout != nil }
        nightscout.snapshot = { [weak model] in model?.nightscoutSnapshot ?? .off }
        nightscout.retry = { [weak model] in model?.retryNightscout() }
        nightscout.secretChanged = { [weak self] secret in
            self?.nightscoutSecret = secret
            self?.settingsChanged()
        }
        model.onNightscout = { [weak windows] snapshot in windows?.apply(nightscout: snapshot) }
        let libreLinkUp = windows.settings.libreLinkUp
        libreLinkUp.available = { [weak model] in model?.libreLinkUp != nil }
        libreLinkUp.snapshot = { [weak model] in model?.libreLinkUpSnapshot ?? .off }
        libreLinkUp.retry = { [weak model] in model?.retryLibreLinkUp() }
        libreLinkUp.signIn = { [weak model] email, password in
            await model?.connectLibreLinkUp(email: email, password: password)
        }
        libreLinkUp.recheckSource = { [weak model] saved in await model?.recheckLibreLinkUp(saved: saved) }
        libreLinkUp.signOutSource = { [weak model] in model?.signOutLibreLinkUp() }
        let alsoSaved = windows.settings.alsoSaved
        alsoSaved.keptNotShown = { [weak model] in model?.keptNotShown() ?? [] }
        alsoSaved.deleteKept = { [weak model] id in model?.deleteKept(id) }
        alsoSaved.forgetNightscoutSource = { [weak model] in await model?.forgetNightscout() }
        model.onLibreLinkUp = { [weak windows] snapshot in windows?.apply(libreLinkUp: snapshot) }
        model.onLan = { [weak windows] snapshot in windows?.apply(lan: snapshot) }
        windows.settings.sensorLife.sensor = { [weak model] in model?.sourceSensor }
        model.onSnapshot = { [weak self, weak windows] snapshot in
            windows?.apply(snapshot)
            self?.wearerChanged(WindowCoordinator.wearerSettings(snapshot))
        }
        contextMenu.openSettings = { [weak windows] in windows?.openSettings() }
        contextMenu.openAbout = { [weak windows] in windows?.openAbout() }
        contextMenu.openTreatments = { [weak windows] in windows?.openTreatments() }
        contextMenu.openReport = { [weak windows] in windows?.openReport() }
        let links = PanelLinks(
            setup: windows.setup, treatments: windows.treatments,
            openSetup: { [weak self] in self?.fromPanel { $0.openSetup() } },
            openFollow: { [weak self] in self?.fromPanel { $0.openFollow() } },
            openAddTreatment: { [weak self] focus in self?.fromPanel { $0.openAddTreatment(focus: focus) } },
            openTreatments: { [weak self] in self?.fromPanel { $0.openTreatments() } },
            openReport: { [weak self] in self?.fromPanel { $0.openReport() } },
            openSettings: { [weak self] in self?.fromPanel { $0.openSettings() } },
            openSettingsPane: { [weak self] pane in self?.fromPanel { $0.openSettings(pane: pane) } })
        let statusItem = StatusItemController(model: model, menu: contextMenu, links: links)
        statusItem.onOpen = { [weak self, weak model] in
            self?.refreshNotificationPermission()
            model?.loadHistory()
        }
        self.statusItem = statusItem
        statusItem.readAloud = { [weak self] in self?.readGlucoseAloud() }
        contextMenu.readAloud = { [weak self] in self?.readGlucoseAloud() }
        // only the real app takes the system-wide keys: a render, a synthetic or an offline dev run never answers them
        // (the person's own copy may be running beside it)
        if model.plan.mode == .normal && !model.plan.isOffline {
            shortcut = GlobalShortcut { [weak self] in self?.readGlucoseAloud() }
        }
        contextMenu.shortcutOn = { [weak self] in self?.shortcut?.isOn == true }
        shortcut?.set(on: store.value(MacSettings.readAloudShortcut))
        windows.statusItemFrame = { [weak statusItem] in statusItem?.screenFrame }
        contextMenu.hasUpdater = model.plan.distribution.hasOwnUpdater
        contextMenu.updates = model.updates
        services.openPanel = { [weak statusItem] in statusItem?.showPanel() }
        windows.openPanel = { [weak statusItem] in statusItem?.showPanel() }
        // a notification's Treatment: only where Add Treatment can open; carbs focused on the low side, rapid on the high
        model.alerts?.canLog = { [weak model] in model.map { AddTreatmentAvailability($0) == .available } ?? false }
        model.alerts?.openAddTreatment = { [weak self] answer in
            self?.fromPanel { $0.openAddTreatment(focus: answer == .carbs ? .carbs : .rapid) }
        }
        services.permissionChanged = { [weak self] in self?.refreshNotificationPermission() }
        observeSystemTime()
        observeClockStyle()
        observeNetwork()
        refreshNotificationPermission()
        model.sourceKind = storedKind
        model.lanConfig = lanConfig
        model.nightscoutConfig = nightscoutConfig
        model.libreLinkUpConfig = libreLinkUpConfig
        model.sensorLifeSettings = SensorLifeSettings(store: store)
        model.run()
        if case .snapshot = model.plan.mode {} else { windows.showSetupIfFirstRun() }
        // after the first-run setup window: an invite link that launched the app opens its Follow step
        for url in linkInbox.launched() { windows.open(url) }
        AppLog.app.info(
            "launched (offline: \(self.model.plan.isOffline, privacy: .public); \(self.services.entitlements.summary, privacy: .public))"
        )
    }

    /// Read Glucose Aloud (menu, VoiceOver action, ⌃⇧⌘G): what VoiceOver says for the menu bar value, said now.
    private func readGlucoseAloud() {
        readAloud.toggle(model.menubarTitle.accessibilityLabel, from: statusItem?.accessibilityElement)
    }

    /// A panel row: close the panel, then open its window.
    private func fromPanel(_ open: (WindowCoordinator) -> Void) {
        statusItem?.closePanel()
        if let windows { open(windows) }
    }

    /// Settings… ⌘, in the main menu (reached through the responder chain while a Sukrara window is key).
    @objc func showSettings(_ sender: Any?) {
        fromPanel { $0.openSettings() }
    }

    /// About Sukrara in the main menu.
    @objc func showAbout(_ sender: Any?) {
        fromPanel { $0.openAbout() }
    }

    public func applicationDidBecomeActive(_ notification: Notification) {
        refreshNotificationPermission()
        model.loginItem?.refresh()
        // back from System Settings › Local Network: look for Juggluco now, not in a minute
        if model.lanSnapshot.state == .localNetworkDenied { model.retryLan() }
    }

    /// How long Quit waits for the follower runtime and Juggluco to stop before it ends the app anyway.
    static let quitTimeout: Duration = .seconds(2)
    private var quitting = false
    private var quitAnswered = false

    /// Quit (menu, logout, restart) waits until the model has stopped what it started (relay sockets closed, the
    /// runtime's state saved), at most `quitTimeout`, then ends the app.
    public func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard !quitting else { return .terminateLater }
        quitting = true
        network.cancel()
        Task { [weak self] in
            await self?.model.stop()
            self?.answerQuit()
        }
        Task { [weak self] in
            try? await Task.sleep(for: Self.quitTimeout)
            self?.answerQuit()
        }
        return .terminateLater
    }

    private func answerQuit() {
        guard !quitAnswered else { return }
        quitAnswered = true
        NSApp.reply(toApplicationShouldTerminate: true)
    }

    public func applicationWillTerminate(_ notification: Notification) { network.cancel() }

    /// `sukrara:` links (Info.plist CFBundleURLTypes) and the web invite `https://sukrara.mauri.app/join#…` (given to
    /// the app with `open -a Sukrara <link>` or dropped on it): invite links go to the follow step's confirmation,
    /// `sukrara://treatment/new` to Add treatment, `sukrara://open` to the panel; anything else is ignored
    /// (`AppLink`). A link that launches the app arrives before `applicationDidFinishLaunching` made the windows: it
    /// waits in `linkInbox` until then.
    public func application(_ application: NSApplication, open urls: [URL]) {
        for url in linkInbox.receive(urls) { windows?.open(url) }
    }

    /// A universal link: the web invite clicked in Safari, Mail, Messages… opens the app (Associated Domains,
    /// `applinks:sukrara.mauri.app`, only in a build with a provisioning profile; the site's
    /// `.well-known/apple-app-site-association` lists the app). It goes the way of every link: the follow step's
    /// confirmation, waiting out a launch in `linkInbox`. Anything else isn't Sukrara's (Safari opens it).
    public func application(
        _ application: NSApplication, continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([any NSUserActivityRestoring]) -> Void
    ) -> Bool {
        guard let url = AppLink.universal(type: userActivity.activityType, url: userActivity.webpageURL) else {
            return false
        }
        for url in linkInbox.receive([url]) { windows?.open(url) }
        return true
    }

    /// Timers don't run while the Mac sleeps and a clock change moves every deadline: re-check alerts and freshness.
    private func observeSystemTime() {
        let workspace = NSWorkspace.shared.notificationCenter
        observers.append(
            workspace.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: .main) {
                [weak self] _ in
                Task { @MainActor in self?.model.didWake() }
            })
        observers.append(
            NotificationCenter.default.addObserver(
                forName: UserDefaults.didChangeNotification, object: nil, queue: .main
            ) { [weak self] _ in
                Task { @MainActor in self?.scheduleSettingsChanged() }
            })
        observers.append(
            NotificationCenter.default.addObserver(forName: .NSSystemClockDidChange, object: nil, queue: .main) {
                [weak self] _ in
                Task { @MainActor in self?.model.systemTimeChanged() }
            })
    }

    /// System Settings › 24-hour time or the time zone changed: every time on screen and in the widgets follows.
    private func observeClockStyle() {
        for name in Clock.changeNotifications {
            observers.append(
                NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                    Task { @MainActor in
                        self?.model.clockStyleChanged()
                        self?.windows?.treatments.clockStyle += 1
                    }
                })
        }
    }

    /// The network came back: reconnect the relays now instead of waiting for the pool's watchdog (up to 270 s).
    private func observeNetwork() {
        guard model.follower != nil || model.lan != nil || model.nightscout != nil || model.libreLinkUp != nil else {
            return
        }
        // only a transition to satisfied: the monitor's first callback (at start) reports the current path, and the
        // runtime is connecting then anyway
        network.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            Task { @MainActor in self?.pathChanged(satisfied: satisfied) }
        }
        network.start(queue: .main)
    }

    private func pathChanged(satisfied: Bool) {
        defer { networkSatisfied = satisfied }
        if satisfied && networkSatisfied == false { model.networkCameBack() }
        if satisfied != networkSatisfied { model.network(online: satisfied) }
    }

    /// Many defaults written together (one Settings edit stores every card setting) are handled once.
    private func scheduleSettingsChanged() {
        guard !settingsPending else { return }
        settingsPending = true
        Task { @MainActor in
            settingsPending = false
            settingsChanged()
        }
    }

    /// A defaults change (Settings window, another process): the alert rules, the unit and the card follow.
    private func settingsChanged() {
        services.settingsChanged()
        let graph = GraphConfig.load(from: store, wearer: wearer)
        let widgetGraph = GraphConfig.load(from: store, wearer: wearer, place: .widget)
        let changed = model.unit != store.unit || model.graph != graph || model.widgetGraph != widgetGraph
        if model.unit != store.unit { model.unit = store.unit }
        if model.graph != graph { model.graph = graph }
        if model.widgetGraph != widgetGraph { model.widgetGraph = widgetGraph }
        if changed { model.feedWidget(settingsChanged: true) }
        model.sourceKind = storedKind
        model.lanConfig = lanConfig
        model.nightscoutConfig = nightscoutConfig
        model.libreLinkUpConfig = libreLinkUpConfig
        model.sensorLifeSettings = SensorLifeSettings(store: store)
        shortcut?.set(on: store.value(MacSettings.readAloudShortcut))
    }

    /// The wearer's mirrored settings arrived, changed or went away (stopped following).
    private func wearerChanged(_ settings: WearerSettings?) {
        guard settings != wearer else { return }
        wearer = settings
        services.wearerSettings(settings)
        settingsChanged()
    }

    private func refreshNotificationPermission() {
        guard let center = services.center else { return }
        Task {
            let permission = await center.refresh()
            model.notificationPermission = permission
            windows?.settings.timeSensitive = center.timeSensitive
        }
    }
}
