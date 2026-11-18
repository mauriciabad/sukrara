// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraCore
import SukraraGraph
import SukraraShare
import SwiftUI

/// Connects the windows (setup, settings, treatments) to the running app: one model per window kind over the one
/// settings store, fed by the follower runtime's snapshots, acting through the `FollowerAdapter` and the app's
/// services (notification permission, login item, updater). Owned by `AppDelegate`.
final class WindowCoordinator {
    let windows = AppWindows()
    let follow: FollowModel
    let setup: SetupModel
    let settings: SettingsModel
    let treatments: TreatmentsModel
    let report: ReportModel
    private let model: AppModel
    private let adapter: FollowerAdapter?
    /// The treatment windows' actions while Nightscout is the source: this Mac's own log.
    private let nightscoutActions: NightscoutTreatmentActions
    /// The same with LibreLinkUp as the source.
    private let libreLinkUpActions: LibreLinkUpTreatmentActions

    init(model: AppModel, store: any SettingsStoring, requestNotifications: @escaping () async -> Bool) {
        self.model = model
        let adapter = model.follower.map(FollowerAdapter.init(follower:))
        self.adapter = adapter
        nightscoutActions = NightscoutTreatmentActions(model: model)
        libreLinkUpActions = LibreLinkUpTreatmentActions(model: model)
        follow = FollowModel(status: .notFollowing, actions: adapter)
        settings = SettingsModel(
            store: store, follow: follow, version: Self.version, distribution: model.plan.distribution,
            appBuild: .current)
        settings.newestReading = { [weak model] in model?.newestReading }
        settings.source.following = { [weak model] in model?.link.isFollowing ?? false }
        setup = SetupModel(settings: settings)
        treatments = TreatmentsModel(rows: [], canWrite: false, actions: adapter)
        // the rows' glucose then: the readings the card shows (each with its own trend) and the card's unit
        treatments.readings = { [weak model] in model?.allReadings ?? [] }
        treatments.unit = { [weak model] in model?.unit ?? .mgdl }
        report = ReportModel(store: store, app: model)
        setup.requestNotifications = requestNotifications
        setup.loginItem = model.loginItem
        follow.searchNearby = { [weak adapter] on in adapter?.searchNearby(on) }
    }

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
    }

    static var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "" }

    // MARK: runtime → windows

    /// A follower snapshot: the link, the nearby offer, the wearer's mirrored settings and the treatments.
    func apply(_ snapshot: FollowerSnapshot) {
        adapter?.update(snapshot)
        follow.status = FollowerAdapter.status(snapshot)
        follow.matchCode = FollowerAdapter.matchCode(snapshot)
        follow.source = FollowerAdapter.source(snapshot)
        follow.problem = FollowerAdapter.problem(snapshot)
        follow.bluetoothBlocked = snapshot.link.isFollowing && snapshot.nearby == .unauthorized
        follow.untoldLeave = snapshot.untoldLeave
        follow.nearby = snapshot.nearbyOffer.map {
            NearbyPhone(wearer: $0.wearerName ?? L10n.t("A phone"), avatar: $0.wearerAvatar)
        }
        follow.wearer = snapshot.wearer
        // the How You Appear row only while a follow exists (the runtime's own profile is just a default before)
        follow.me = snapshot.link == .notFollowing ? nil : snapshot.me
        settings.wearer = Self.wearerSettings(snapshot)
        // an online source: the treatment windows show this Mac's own log (`apply(nightscout:)`, `apply(libreLinkUp:)`)
        guard model.sourceKind != .nightscout, model.sourceKind != .libreLinkUp else { return }
        let canWrite = if case .following(_, true) = snapshot.link { true } else { false }
        treatments.actions = adapter
        treatments.pulled = []
        treatments.note = nil
        treatments.isFollowing = snapshot.link.isFollowing
        treatments.wearer = snapshot.link.isFollowing ? FollowerAdapter.wearerName(snapshot.link) : nil
        treatments.sync(
            snapshot.treatments, deliveries: snapshot.deliveries.compactMapValues(FollowerAdapter.delivery),
            canWrite: canWrite)
    }

    /// A Nightscout snapshot while it is the source: the treatment windows show this Mac's log, the site's entries
    /// read-only, and what a token that can only read means.
    func apply(nightscout snapshot: NightscoutSnapshot) {
        guard model.sourceKind == .nightscout else { return }
        treatments.actions = nightscoutActions
        treatments.isFollowing = false
        treatments.wearer = nil
        treatments.pulled = snapshot.pulled
        treatments.note =
            snapshot.treatmentsOn && snapshot.readOnly
            ? L10n.t("This token can only read: your treatments stay on this Mac") : nil
        let own = snapshot.treatments.filter { !snapshot.pulled.contains($0.id) }
        treatments.sync(
            snapshot.treatments, deliveries: Dictionary(uniqueKeysWithValues: own.map { ($0.id, .sent) }),
            canWrite: model.nightscoutActive)
    }

    /// A LibreLinkUp snapshot while it is the source: the treatment windows show this Mac's own log (LibreLinkUp
    /// brings none, and nothing goes anywhere).
    func apply(libreLinkUp snapshot: LibreLinkUpSnapshot) {
        guard model.sourceKind == .libreLinkUp else { return }
        treatments.actions = libreLinkUpActions
        treatments.isFollowing = false
        treatments.wearer = nil
        treatments.pulled = []
        treatments.note = nil
        treatments.sync(snapshot.treatments, deliveries: [:], canWrite: model.libreLinkUpActive)
    }

    /// The wearer's mirrored settings while following (nil otherwise: this Mac's own values apply).
    static func wearerSettings(_ snapshot: FollowerSnapshot) -> WearerSettings? {
        guard snapshot.link.isFollowing, let mirrored = snapshot.wearerSettings else { return nil }
        return WearerSettings(json: mirrored.json, wearer: FollowerAdapter.wearerName(snapshot.link))
    }

    // MARK: opening windows

    /// First launch (the walk never started): open the setup window.
    func showSetupIfFirstRun() {
        if setup.flow.isFirstRun { openSetup() }
    }

    /// "Finish setup" (nil = where the walk was left, else the first unfinished step) or one step (Follow… → `.follow`).
    func openSetup(at step: SetupFlow.Step? = nil) {
        if let step { setup.flow.open(step) } else { setup.flow.reopen() }
        windows.show(.setup, title: L10n.t("Set Up Sukrara")) {
            SetupView(model: setup) { [weak self] in self?.windows.close(.setup) }
        }
    }

    func openFollow() { openSetup(at: .follow) }

    /// Settings on its last pane, or on `pane` (a no-data reason's way out).
    func openSettings(pane: SettingsModel.Pane? = nil) {
        if let pane { settings.pane = pane }
        windows.show(
            .settings, title: settings.pane.title, sizedByWindow: true,
            configure: { [weak self] w in
                guard let self else { return }
                settingsChrome = SettingsWindowChrome(window: w, model: settings)
            }
        ) {
            SettingsWindowContent(
                model: settings, app: model, openFollow: { [weak self] in self?.openFollow() },
                answerSource: { [weak self] in self?.openSetup(at: .phone) },
                openAbout: { [weak self] in self?.openAbout() })
        }
    }

    /// The settings window's toolbar and size (kept while the window lives; a new window makes a new one).
    private var settingsChrome: SettingsWindowChrome?

    /// About Sukrara (app menu, right-click menu, Settings › General).
    func openAbout() {
        windows.show(.about, title: L10n.t("About Sukrara"), configure: AboutWindow.configure) {
            AboutView(
                version: Self.version, build: Self.build, distribution: model.plan.distribution, appBuild: .current)
        }
    }

    func openTreatments() {
        windows.show(.treatments, title: L10n.t("Treatments")) {
            TreatmentsView(model: treatments) { [weak self] in self?.openAddTreatment(atPointer: true) }
        }
    }

    func openReport() {
        windows.show(.report, title: L10n.t("Glucose Report")) { ReportView(model: report) }
    }

    /// The status item's frame on screen (set by `AppDelegate`): Add Treatment opened from the panel opens under it.
    var statusItemFrame: () -> NSRect? = { nil }
    private let addFocus = AddTreatmentFocus()

    /// Add treatment: the Carbs field has the focus while the latest reading is below the range (or a low alert is up),
    /// else Rapid; the card's pill names the field itself (`focus`). It opens under the status item (from the panel)
    /// or, `atPointer`, centred on the pointer (a click elsewhere: a desktop widget, the Treatments window's +).
    func openAddTreatment(focus: Treatment.Kind? = nil, atPointer: Bool = false) {
        let low =
            model.isLow
            || (model.look.state == .value && GlucoseCard.rangeSide(model.cardData, config: model.graph) == .below)
        let field = focus ?? (low ? .carbs : .rapid)
        // already open: a pill's field choice moves the focus there (nothing typed is lost)
        if let focus, windows.isOpen(.addTreatment) { addFocus.request(focus) }
        let placement: AppWindows.Placement =
            atPointer ? .atPointer(NSEvent.mouseLocation) : .underMenuBar(anchor: statusItemFrame())
        windows.show(.addTreatment, title: L10n.t("Add Treatment"), placement: placement) {
            AddTreatmentView(
                focus: field, focusRequests: addFocus,
                quiet: { [weak model] rapid, carbs, at in
                    TreatmentQuietNote.of(
                        rapid: rapid, carbs: carbs, at: at, now: Int64(Date().timeIntervalSince1970),
                        settings: model?.alerts?.settings, mirroring: model?.alerts?.mirroring ?? false)
                },
                penDuplicate: { [weak self] entries in self?.treatments.penDuplicate(of: entries) },
                save: { [weak self] entries, close in
                    guard let self else { return }
                    Task { await self.treatments.add(entries) }
                    if close { self.windows.close(.addTreatment) }
                },
                cancel: { [weak self] in self?.windows.close(.addTreatment) })
        }
    }

    /// Opens the menu bar panel (set by `AppDelegate`; `sukrara://open`).
    var openPanel: () -> Void = {}

    /// Links opened on this Mac: `sukrara://treatment/new` opens Add treatment, `sukrara://open` (a widget click with
    /// no data) the panel; an invite link (`sukrara:join#…`, any other `sukrara:` link, or
    /// `https://sukrara.mauri.app/join#…`) opens Follow, looks the invite up and shows who is inviting with *Follow* /
    /// *Cancel*: it never follows by itself (nothing reaches the relays unless it parses as an invite). Other links are
    /// ignored.
    func open(_ url: URL) {
        switch AppLink(url) {
        case .newTreatment:
            // a widget click: the pointer is on the widget
            if treatments.canWrite { openAddTreatment(atPointer: true) } else { openTreatments() }
        case .panel:
            openPanel()
        case .invite(let text):
            openFollow()
            Task { await follow.open(link: text) }
        case nil:
            break
        }
    }

}

/// Where a link opened on this Mac goes (`WindowCoordinator.open`).
enum AppLink: Equatable {
    /// `sukrara://treatment/new`: the widget's quick Add treatment.
    case newTreatment
    /// `sukrara://open`: the widget with no data asks for the app.
    case panel
    /// An invite link (`https://sukrara.mauri.app/join#…`, `sukrara:join#…` or any other `sukrara:` link), checked by
    /// the follow step before it reaches the relays.
    case invite(String)

    /// nil: not Sukrara's (an `https` link elsewhere than the invite page).
    init?(_ url: URL) {
        if url.scheme == SukraraIDs.urlScheme {
            switch (url.host(), url.path()) {
            case ("treatment", "/new"): self = .newTreatment
            case ("open", ""), ("open", "/"): self = .panel
            default: self = .invite(url.absoluteString)
            }
        } else if url.scheme == "https", url.host() == Self.inviteHost, url.path() == "/join" {
            self = .invite(url.absoluteString)
        } else {
            return nil
        }
    }

    /// The web invite's host (`Wire.inviteBase`).
    static let inviteHost = "sukrara.mauri.app"

    /// A universal link's page when it is the web invite (`https://sukrara.mauri.app/join#…` in a browsing-web
    /// activity), else nil: the system hands over only what the site's `apple-app-site-association` lists, but the
    /// app still opens nothing else.
    static func universal(type: String, url: URL?) -> URL? {
        guard type == NSUserActivityTypeBrowsingWeb, let url, url.scheme == "https", case .invite = AppLink(url) else {
            return nil
        }
        return url
    }
}

/// The links macOS hands the app (`sukrara:` and the web invite: `AppLink`), held until the windows exist: a link that
/// launches Sukrara arrives before `applicationDidFinishLaunching`, and opening it then would drop it. Other links are
/// ignored.
struct LinkInbox {
    /// Links that arrived before launch finished, in order.
    private(set) var pending: [URL] = []
    /// Launch finished: links are opened as they arrive.
    private(set) var isOpen = false

    /// The links to open now; before launch finished they wait (none now).
    mutating func receive(_ urls: [URL]) -> [URL] {
        let ours = urls.filter { AppLink($0) != nil }
        guard isOpen else {
            pending += ours
            return []
        }
        return ours
    }

    /// Launch finished: the links that waited, in the order they came (each handed out once).
    mutating func launched() -> [URL] {
        isOpen = true
        defer { pending = [] }
        return pending
    }
}

/// The settings window with the app's live parts: open at login (the login item), the updater.
private struct SettingsWindowContent: View {
    let model: SettingsModel
    let app: AppModel
    let openFollow: () -> Void
    let answerSource: () -> Void
    let openAbout: () -> Void

    var body: some View {
        SettingsView(
            model: model,
            loginItem: app.loginItem,
            updates: app.updates,
            openFollow: openFollow,
            reconnect: app.follower == nil ? nil : { [weak app] in app?.networkCameBack() },
            answerSource: answerSource,
            alerts: app.alerts,
            openAbout: openAbout)
    }
}
