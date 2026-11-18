// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Observation
import SukraraCore
import SukraraShare
import SwiftUI

/// Settings › Source and setup's Connect with a Nightscout site as the source (schema `source.nightscoutUrl`,
/// `source.nightscoutTreatments`, the token in the Keychain `source.nightscoutSecret`): the site, the token, how it's
/// going and its way out, treatments both ways. The words are Android's (`ds_source_nightscout_*`).
@Observable
public final class NightscoutModel {
    @ObservationIgnored let store: any SettingsStoring
    /// The Keychain (nil in a run without one: snapshots). The token is never written to the defaults.
    @ObservationIgnored var secrets: (any SecretStoring)?
    /// What the source said last (the app model's), `.off` when nothing feeds it.
    @ObservationIgnored var snapshot: () -> NightscoutSnapshot = { .off }
    /// This run may call the internet (false in dev, fixture and snapshot runs).
    @ObservationIgnored var available: () -> Bool = { false }
    @ObservationIgnored var retry: () -> Void = {}
    /// The token changed (the Keychain posts nothing): the app re-reads the config.
    @ObservationIgnored var secretChanged: (String) -> Void = { _ in }
    /// "Now" for the status line (tests and snapshots pin it).
    @ObservationIgnored var now: () -> Date = Date.init
    /// The site as typed (saved by Connect / Return).
    var siteDraft: String
    /// A new token as typed (empty = keep the saved one).
    var secretDraft = ""
    /// What's wrong with the typed address, shown under it until it changes.
    var problem: NightscoutSite.Problem?
    /// A token is saved (the field says so without showing it).
    var hasSecret = false
    /// Another site typed while this Mac's log has entries the old site hasn't got: asked first.
    var confirmingSite: String?
    /// Sign in again asked for the token field (it takes the focus).
    var signInRequests = 0
    private var revision = 0

    init(store: any SettingsStoring, secrets: (any SecretStoring)? = nil) {
        self.store = store
        self.secrets = secrets
        siteDraft = store.value(MacSettings.nightscoutURL)
    }

    /// The saved site (its root), empty before Connect.
    var site: String {
        _ = revision
        return store.value(MacSettings.nightscoutURL)
    }

    var treatments: Bool {
        get {
            _ = revision
            return store.value(MacSettings.nightscoutTreatments)
        }
        set {
            store.set(newValue, for: MacSettings.nightscoutTreatments)
            revision += 1
        }
    }

    /// Loads the saved site into its field (when the pane or the step shows); the token stays unseen.
    func load() {
        siteDraft = site
        secretDraft = ""
        hasSecret = !Self.secret(secrets).isEmpty
        problem = nil
    }

    /// Setup's Connect appears: what's saved, without dropping an address being typed (going Back and forth).
    func open() {
        if siteDraft.isEmpty { siteDraft = site }
        hasSecret = !Self.secret(secrets).isEmpty
    }

    /// A pasted `…?token=` address fills the token and leaves the site (Nightscout's way of sharing a site).
    func typed(_ text: String) {
        problem = nil
        let parsed = NightscoutSite.parse(text)
        guard let token = parsed.token else { return }
        siteDraft = parsed.root
        secretDraft = token
    }

    /// Connect (setup) and Return (Settings): checks the address, then saves it and a new token; the source asks the
    /// site at once. Another site with entries the old one hasn't got asks first (`confirmingSite`).
    func connect() {
        let parsed = NightscoutSite.parse(siteDraft)
        if let p = parsed.problem {
            problem = p
            return
        }
        siteDraft = parsed.root
        if !site.isEmpty, !NightscoutSite.same(site, parsed.root), snapshot().unsent > 0 {
            confirmingSite = parsed.root
            return
        }
        commit(parsed.root)
    }

    /// Saves `root` and the typed token (after the confirmation when one was asked).
    func commit(_ root: String) {
        confirmingSite = nil
        let token = secretDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        if !token.isEmpty, let secrets {
            try? secrets.write(Data(token.utf8), account: MacSettings.nightscoutSecretAccount)
            hasSecret = true
        }
        secretDraft = ""
        let changed = root != site
        // another site is another source: the manual sensor start was set for the old one's sensor
        if !site.isEmpty, !NightscoutSite.same(site, root) { store.set(0.0, for: MacSettings.manualStart) }
        store.set(root, for: MacSettings.nightscoutURL)
        revision += 1
        if !token.isEmpty || changed { secretChanged(Self.secret(secrets)) } else { retry() }
    }

    /// The status card's *Sign In Again*: the token field, empty and focused, under the same site.
    func signIn() {
        siteDraft = site
        secretDraft = ""
        signInRequests += 1
    }

    /// *Open Nightscout*: the site in the browser.
    func openSite() {
        if let url = NightscoutSite.parse(site).url { NSWorkspace.shared.open(url) }
    }

    static func secret(_ secrets: (any SecretStoring)?) -> String {
        guard let data = try? secrets?.read(account: MacSettings.nightscoutSecretAccount) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    /// A typed address's problem in words.
    static func problemText(_ p: NightscoutSite.Problem) -> String {
        switch p {
        case .url: L10n.t("Not a web address")
        case .https: L10n.t("Use https://: the token would travel unprotected")
        }
    }
}

/// How the Nightscout source is doing in words (contracts/sources/health.json): the status card's line, its symbol in
/// the text's colour, and the one action that helps (Sign In Again, Try Again).
struct NightscoutStatus: Equatable {
    enum Action: Equatable { case signIn, tryAgain }
    var text: String
    var symbol: String
    var action: Action?

    static func of(_ s: NightscoutSnapshot, now: Date) -> NightscoutStatus {
        let minutes = s.readings.last.map { max(0, Int(now.timeIntervalSince($0.t) / 60)) }
        let upload = L10n.t("Check the app that uploads to it.")
        switch s.health(at: now) {
        case .connecting:
            return NightscoutStatus(text: L10n.t("Connecting…"), symbol: "arrow.triangle.2.circlepath")
        case .live:
            return NightscoutStatus(
                text: L10n.t("Live · last reading \(ago(minutes ?? 0))"), symbol: "dot.radiowaves.up.forward")
        case .late:
            return NightscoutStatus(text: L10n.t("Late · last reading \(ago(minutes ?? 0))"), symbol: "clock")
        case .noNewReadings:
            guard let minutes else {
                return NightscoutStatus(
                    text: L10n.t("Nightscout has no glucose yet.") + " " + upload, symbol: "clock.badge.questionmark",
                    action: .tryAgain)
            }
            return NightscoutStatus(
                text: L10n.t("Nothing new from Nightscout: its last reading was \(ago(minutes)).") + " " + upload,
                symbol: "clock.badge.questionmark", action: .tryAgain)
        case .offline:
            return NightscoutStatus(
                text: L10n.t("No internet. Sukrara catches up when you're back."), symbol: "wifi.slash")
        case .unreachable:
            return NightscoutStatus(
                text: L10n.t("No answer from \(s.host). Check the address."), symbol: "network.slash",
                action: .tryAgain)
        case .signedOut:
            return NightscoutStatus(text: L10n.t("Nightscout refused the token."), symbol: "lock", action: .signIn)
        case .rateLimited:
            return NightscoutStatus(
                text: L10n.t("Nightscout asked Sukrara to wait. It tries again by itself."), symbol: "hourglass")
        case .warmingUp:
            return NightscoutStatus(text: warmup(s.sensor, now: now), symbol: "hourglass")
        // LibreLinkUp's own states: a site never reaches them
        case .actionNeeded, .notShared, .stopped:
            return NightscoutStatus(
                text: L10n.t("No answer from \(s.host). Check the address."), symbol: "network.slash",
                action: .tryAgain)
        }
    }

    /// An online source's sensor warming up: "Sensor warming up · ready at 19:13", or almost ready once that time has
    /// passed (Android `ds_online_warming_up` / `ds_online_almost_ready`).
    static func warmup(_ sensor: WearerSensor?, now: Date) -> String {
        guard let sensor, TimeInterval(sensor.warmupEnd) > now.timeIntervalSince1970 else {
            return L10n.t("Sensor almost ready · waiting for the first reading")
        }
        return L10n.t(
            "Sensor warming up · ready at \(Clock.time(Date(timeIntervalSince1970: TimeInterval(sensor.warmupEnd))))")
    }

    /// "just now", "9 min ago", "1 h 30 min ago" (`Durations.text`, Android's words).
    static func ago(_ minutes: Int) -> String {
        minutes < 1 ? L10n.t("just now") : L10n.t("\(Durations.text(minutes)) ago")
    }

    /// The *Treatments both ways* row's state line.
    static func treatmentsLine(on: Bool, readOnly: Bool) -> String {
        guard on else { return L10n.t("Yours stay on this Mac") }
        return readOnly
            ? L10n.t("This token can only read: your treatments stay on this Mac")
            : L10n.t("Nightscout's arrive here; yours go there")
    }
}

/// The status card: the state in words with its action, cross-fading as it changes (a fade with Reduce Motion), read
/// once by VoiceOver when it changes.
struct NightscoutStatusRow: View {
    @Bindable var model: NightscoutModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { _ in
            let status = NightscoutStatus.of(model.snapshot(), now: model.now())
            HStack(alignment: .firstTextBaseline) {
                Label(status.text, systemImage: status.symbol)
                    .fixedSize(horizontal: false, vertical: true)
                    .id(status.symbol)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .leading)))
                Spacer()
                switch status.action {
                case .signIn: Button(L10n.t("Sign In Again"), action: model.signIn)
                case .tryAgain: Button(L10n.t("Try Again"), action: model.retry)
                case nil: EmptyView()
                }
            }
            .animation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35), value: status)
            .accessibilityElement(children: .combine)
            .onChange(of: status.symbol) { _, _ in
                AccessibilityNotification.Announcement(status.text).post()
            }
        }
    }
}

/// The site and token fields (Settings › Source and setup's Connect), with the address problem and the help under them.
struct NightscoutFields: View {
    @Bindable var model: NightscoutModel
    /// Return in a field saves (Settings); setup's Connect button does it there.
    var submits = true
    @FocusState private var tokenFocused: Bool

    var body: some View {
        LabeledContent {
            VStack(alignment: .leading, spacing: 4) {
                TextInput(label: L10n.t("Site address"), text: $model.siteDraft)
                    .onChange(of: model.siteDraft) { _, new in model.typed(new) }
                    .onSubmit { if submits { model.connect() } }
                if let p = model.problem { Notice(NightscoutModel.problemText(p), tone: .error) }
            }
        } label: {
            ServiceLabel(L10n.t("Site address"), service: .nightscout)
        }
        LabeledContent {
            VStack(alignment: .leading, spacing: 4) {
                TextInput(label: L10n.t("Access token or API secret"), text: $model.secretDraft, secure: true)
                    .focused($tokenFocused)
                    .onSubmit { if submits { model.connect() } }
                if model.hasSecret && model.secretDraft.isEmpty {
                    Text(L10n.t("Saved. Leave it empty to keep it.")).font(.caption).foregroundStyle(.secondary)
                }
            }
        } label: {
            ServiceLabel(L10n.t("Access token or API secret"), symbol: "key")
        }
        .onChange(of: model.signInRequests) { _, _ in tokenFocused = true }
        Text(
            L10n.t(
                "In Nightscout › Admin tools › Subjects, a token with the careportal role reads glucose and sends treatments; readable only reads."
            )
        )
        .font(.caption)
        .foregroundStyle(.secondary)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Settings › Source with Nightscout: how it's going, the site and token, treatments both ways, Open Nightscout.
struct NightscoutRows: View {
    @Bindable var model: NightscoutModel

    var body: some View {
        Section {
            if !model.available() {
                Text(L10n.t("Not available in this run (development or test build).")).foregroundStyle(.secondary)
            } else if !model.site.isEmpty {
                NightscoutStatusRow(model: model)
            }
            NightscoutFields(model: model)
        } header: {
            Text(verbatim: "Nightscout")
        }
        .confirmationDialog(
            L10n.t("Read another site?"),
            isPresented: Binding(get: { model.confirmingSite != nil }, set: { if !$0 { model.confirmingSite = nil } })
        ) {
            Button(L10n.t("Read the New Site"), role: .destructive) {
                if let root = model.confirmingSite { model.commit(root) }
            }
            Button(L10n.t("Cancel"), role: .cancel) { model.confirmingSite = nil }
        } message: {
            Text(
                L10n.t(
                    "Treatments logged on this Mac that \(NightscoutSite.parse(model.site).host) hasn't got yet are deleted with its glucose."
                ))
        }
        Section {
            let snap = model.snapshot()
            Toggle(isOn: $model.treatments) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.t("Treatments both ways"))
                    Text(NightscoutStatus.treatmentsLine(on: model.treatments, readOnly: snap.readOnly))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .contentTransition(.opacity)
                }
            }
            if !model.site.isEmpty {
                HStack {
                    Spacer()
                    Button(L10n.t("Open Nightscout"), action: model.openSite)
                        .help(L10n.t("Open the site in the browser"))
                }
            }
        }
    }
}

/// Setup's Connect for Nightscout: the fields and *Connect*, then where it stands: connecting (a spinner), refused or
/// unreachable (the reason inline, the fields to fix it), *Waiting for the first reading*, *Reading received* (a check
/// that pops in with a trackpad tap and a VoiceOver announcement; Reduce Motion: it fades in).
struct NightscoutConnect: View {
    @Bindable var model: NightscoutModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var received = false

    /// Where the step stands (`stage(…)`).
    enum Stage: Equatable {
        case form, connecting, failed(NightscoutStatus), waiting(String?), received
    }

    /// The step's stage from what the source says: nothing saved yet → the form; then as the site answers.
    static func stage(_ s: NightscoutSnapshot, site: String, now: Date) -> Stage {
        guard !site.isEmpty, !s.host.isEmpty else { return .form }
        let status = NightscoutStatus.of(s, now: now)
        switch s.health(at: now) {
        case .connecting: return .connecting
        case .offline, .unreachable, .signedOut, .rateLimited, .actionNeeded, .notShared, .stopped:
            return .failed(status)
        // received: a reading that is now's (an old one, or a sensor warming up, says what it waits for)
        case .live, .late: return .received
        case .noNewReadings, .warmingUp: return .waiting(status.text)
        }
    }

    var body: some View {
        let stage = Self.stage(model.snapshot(), site: model.site, now: model.now())
        VStack(alignment: .leading, spacing: 12) {
            Form { NightscoutFields(model: model, submits: true) }
                .formStyle(.columns)
            HStack(spacing: 10) {
                // Return connects until the site answers; then it is the step's Continue
                Button(L10n.t("Connect"), action: model.connect)
                    .keyboardShortcut(model.snapshot().monitoring ? nil : .defaultAction)
                    .disabled(model.siteDraft.trimmingCharacters(in: .whitespaces).isEmpty || stage == .connecting)
                switch stage {
                case .form: EmptyView()
                case .connecting:
                    ProgressView().controlSize(.small)
                    Text(L10n.t("Connecting…")).foregroundStyle(.secondary)
                case .failed(let status):
                    Label(status.text, systemImage: status.symbol)
                        .fixedSize(horizontal: false, vertical: true)
                case .waiting(let note):
                    VStack(alignment: .leading, spacing: 2) {
                        Label(L10n.t("Waiting for the first reading"), systemImage: "hourglass")
                        if let note, model.snapshot().health(at: model.now()) == .noNewReadings {
                            Text(note).font(.caption).foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                case .received:
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.t("Reading received"))
                            Text(L10n.t("Your glucose is in the menu bar now.")).font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                            .scaleEffect(received || reduceMotion ? 1 : 0.4)
                    }
                    .opacity(received ? 1 : 0)
                }
            }
            .animation(reduceMotion ? .easeInOut(duration: 0.25) : .spring(duration: 0.4, bounce: 0.35), value: stage)
            .accessibilityElement(children: .combine)
        }
        .onAppear(perform: model.open)
        .onChange(of: stage == .received, initial: true) { _, isIn in
            guard isIn, !received else { return }
            NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
            AccessibilityNotification.Announcement(L10n.t("Reading received")).post()
            withAnimation(reduceMotion ? .easeIn(duration: 0.3) : .spring(duration: 0.45, bounce: 0.4)) {
                received = true
            }
        }
    }
}
