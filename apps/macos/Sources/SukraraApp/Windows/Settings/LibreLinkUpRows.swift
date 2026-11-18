// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Observation
import SukraraCore
import SukraraShare
import SwiftUI

/// Settings › Source and setup's Connect with LibreLinkUp as the source (docs/44 step 2; schema
/// `source.libreLinkUpPatient`, the account in the Keychain `source.libreLinkUpSecret`, which only the source reads and
/// writes): sign in, whose glucose, how it's going and its way out, sign out. The words are Android's
/// (`sh_setup_llu_*`, `in_llu_*`, `ds_online_llu_*`).
@Observable
public final class LibreLinkUpModel {
    @ObservationIgnored let store: any SettingsStoring
    /// What the source said last (the app model's), `.off` when nothing feeds it.
    @ObservationIgnored var snapshot: () -> LibreLinkUpSnapshot = { .off }
    /// This run may call the internet (false in dev, fixture and snapshot runs).
    @ObservationIgnored var available: () -> Bool = { false }
    /// The source's *Sign In* and *Try Again* (nil = no source in this run).
    @ObservationIgnored var signIn: (_ email: String, _ password: String) async -> LluConnectResult? = { _, _ in nil }
    @ObservationIgnored var recheckSource: (_ saved: String?) async -> LluConnectResult? = { _ in nil }
    @ObservationIgnored var signOutSource: () -> Void = {}
    @ObservationIgnored var retry: () -> Void = {}
    /// "Now" for the status line (tests and snapshots pin it).
    @ObservationIgnored var now: () -> Date = Date.init
    /// Feedback that confirms or rejects (a trackpad tap; tests count them).
    @ObservationIgnored var haptic: (_ ok: Bool) -> Void = { ok in
        NSHapticFeedbackManager.defaultPerformer.perform(ok ? .levelChange : .generic, performanceTime: .now)
    }

    var emailDraft = ""
    /// Never stored here: it goes to the source, which keeps it in the Keychain only once LibreLinkUp accepts it.
    var passwordDraft = ""
    /// A sign-in or *Try Again* on its way.
    var busy = false
    /// The last *Sign In* / *Try Again* answer (shown until the fields change).
    var result: LluConnectResult?
    /// *Change Account*: the form over a signed-in account (*Keep This Account* goes back).
    var changing = false
    /// The Choose step's pick before *Continue*.
    var picked: String?
    /// Another person picked while this Mac holds someone's readings or log: asked first.
    var confirming: LluPerson?
    var signingOut = false
    /// *Sign In Again* asked for the password field (it takes the focus).
    var signInRequests = 0
    private var revision = 0

    init(store: any SettingsStoring) {
        self.store = store
    }

    /// Whom it reads (schema `source.libreLinkUpPatient`; "" = no one picked).
    var patient: String {
        _ = revision
        return store.value(MacSettings.libreLinkUpPatient)
    }

    /// What the step and the rows show now.
    enum Mode: Equatable { case form, choose, problem, connected }

    var mode: Mode {
        let s = snapshot()
        let health = s.health(at: now())
        if changing || s.email.isEmpty || s.signInAgain { return .form }
        if patient.isEmpty && s.people.count > 1 { return .choose }
        if patient.isEmpty || health == .notShared || health == .actionNeeded { return .problem }
        return .connected
    }

    /// The form appears: the saved email filled in (a password is never shown).
    func open() {
        if emailDraft.isEmpty { emailDraft = snapshot().email }
    }

    /// A field changed: the last answer goes.
    func edited() { result = nil }

    /// *Sign In*: the source signs in, reads whom the account follows and picks when the choice is clear.
    @MainActor func connect() async {
        let email = emailDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty, !passwordDraft.isEmpty, !busy else { return }
        busy = true
        let answer = await signIn(email, passwordDraft)
        busy = false
        handle(answer, signedIn: true)
    }

    /// *Try Again* (no one shared, new terms): whom the saved account follows, read again (no password).
    @MainActor func recheck() async {
        guard !busy else { return }
        busy = true
        let answer = await recheckSource(patient.isEmpty ? nil : patient)
        busy = false
        handle(answer, signedIn: false)
    }

    private func handle(_ answer: LluConnectResult?, signedIn: Bool) {
        result = answer
        guard let answer else { return }
        switch answer {
        case .connected(let people, let p):
            haptic(true)
            if signedIn {
                passwordDraft = ""
                changing = false
            }
            picked = nil
            // several people and none picked (a new sign-in, or the one picked stopped sharing): the choice shows
            if let p { choose(p) } else if signedIn || !people.contains(where: { $0.id == patient }) { write("") }
        case .noOne:
            haptic(false)
            passwordDraft = ""
            changing = false
            write("")
        default:
            haptic(false)
        }
    }

    /// Whom to read; another person than the one whose readings or log this Mac holds is asked first.
    func choose(_ id: String) {
        let s = snapshot()
        let holds = !s.patient.isEmpty && (!s.treatments.isEmpty || !s.readings.isEmpty)
        if holds, s.patient != id {
            confirming = s.people.first { $0.id == id } ?? LluPerson(id: id, name: "")
            return
        }
        write(id)
    }

    /// Saves whom to read (after the confirmation when one was asked): the app hands it to the source.
    func write(_ id: String) {
        confirming = nil
        store.set(id, for: MacSettings.libreLinkUpPatient)
        revision += 1
    }

    /// The status's *Sign In Again*: the form, the email kept, the password field focused.
    func signInAgain() {
        changing = true
        emailDraft = snapshot().email
        passwordDraft = ""
        result = nil
        signInRequests += 1
    }

    /// *Sign Out* (confirmed): the account goes, glucose stops until a new sign-in; the readings stay.
    func signOut() {
        signingOut = false
        write("")
        passwordDraft = ""
        result = nil
        changing = false
        signOutSource()
    }

    /// A person's name, or what to call them without one.
    static func name(_ p: LluPerson) -> String { p.name.isEmpty ? L10n.t("Someone without a name") : p.name }

    /// A problem *Sign In* or *Try Again* met, in words (nil = none to show).
    static func problem(_ r: LluConnectResult?) -> String? {
        switch r {
        case .badCredentials: L10n.t("LibreLinkUp refused this email or password")
        case .terms:
            L10n.t(
                "LibreLinkUp needs you first: open the LibreLinkUp app, sign in and accept the new terms. Then try again."
            )
        case .rateLimited:
            L10n.t(
                "Too many attempts: LibreLinkUp asks to wait a few minutes. Sukrara doesn't retry by itself, so your account isn't locked longer."
            )
        case .noOne:
            L10n.t(
                "No one shares glucose with this account yet. In the Libre app: Menu › Connected apps › LibreLinkUp, invite this email, accept the invite in LibreLinkUp, then try again."
            )
        case .unreachable: L10n.t("No answer from LibreLinkUp. Try again in a moment.")
        case .offline: L10n.t("No internet. Connect and try again.")
        case .stopped:
            L10n.t("LibreLinkUp stopped answering Sukrara: Abbott changed it. An update of Sukrara may bring it back.")
        case .maintenance:
            L10n.t(
                "LibreView maintenance. LibreLinkUp sends no glucose until it ends; readings come back by themselves.")
        case .connected, nil: nil
        }
    }
}

/// How LibreLinkUp is doing in words (contracts/sources/health.json with its own states; Android `onlineHealthText`
/// with LibreLinkUp's words): the status line, its symbol in the text's colour, and the one action that helps.
struct LibreLinkUpStatus: Equatable {
    /// `pickAgain`: whom the account follows read again; the one remaining asked first, several: the choice shows.
    enum Action: Equatable { case signIn, tryAgain, recheck, pickAgain }
    var text: String
    var symbol: String
    var action: Action?

    static var pickedGone: String { L10n.t("The person picked stopped sharing glucose with this account.") }

    static func of(_ s: LibreLinkUpSnapshot, now: Date) -> LibreLinkUpStatus {
        let minutes = s.readings.last.map { max(0, Int(now.timeIntervalSince($0.t) / 60)) }
        switch s.health(at: now) {
        case .connecting:
            return LibreLinkUpStatus(text: L10n.t("Connecting…"), symbol: "arrow.triangle.2.circlepath")
        case .live:
            return LibreLinkUpStatus(
                text: L10n.t("Live · last reading \(NightscoutStatus.ago(minutes ?? 0))"),
                symbol: "dot.radiowaves.up.forward")
        case .late:
            return LibreLinkUpStatus(
                text: L10n.t("Late · last reading \(NightscoutStatus.ago(minutes ?? 0))"), symbol: "clock")
        case .noNewReadings:
            guard let minutes else {
                return LibreLinkUpStatus(
                    text: L10n.t("LibreLinkUp has no glucose yet. The Libre app sends it once its sensor runs."),
                    symbol: "clock.badge.questionmark", action: .tryAgain)
            }
            return LibreLinkUpStatus(
                text: L10n.t(
                    "Nothing new from LibreLinkUp: its last reading was \(NightscoutStatus.ago(minutes)). Open the Libre app: it may have stopped uploading."
                ), symbol: "clock.badge.questionmark", action: .tryAgain)
        case .offline:
            return LibreLinkUpStatus(
                text: L10n.t("No internet. Sukrara catches up when you're back."), symbol: "wifi.slash")
        case .unreachable:
            return LibreLinkUpStatus(
                text: L10n.t("No answer from LibreLinkUp. Sukrara tries again by itself."), symbol: "network.slash",
                action: .tryAgain)
        case .signedOut:
            return LibreLinkUpStatus(
                text: L10n.t("LibreLinkUp refused the password. Sign in again."), symbol: "lock", action: .signIn)
        case .rateLimited:
            return LibreLinkUpStatus(
                text: L10n.t("LibreLinkUp asked Sukrara to wait. It tries again by itself."), symbol: "hourglass")
        case .actionNeeded:
            return LibreLinkUpStatus(
                text: L10n.t("LibreLinkUp needs you: open the LibreLinkUp app, sign in and accept the new terms."),
                symbol: "hand.raised", action: .recheck)
        case .notShared:
            if s.pickedGone {
                return LibreLinkUpStatus(
                    text: Self.pickedGone, symbol: "person.crop.circle.badge.questionmark", action: .pickAgain)
            }
            return LibreLinkUpStatus(
                text: L10n.t(
                    "No one shares glucose with this account now. In the Libre app: Menu › Connected apps › LibreLinkUp, invite this email."
                ), symbol: "person.crop.circle.badge.questionmark", action: .recheck)
        case .stopped:
            return LibreLinkUpStatus(
                text: L10n.t(
                    "LibreLinkUp stopped answering Sukrara: Abbott changed it. An update of Sukrara may bring it back."),
                symbol: "xmark.octagon", action: .tryAgain)
        case .warmingUp:
            return LibreLinkUpStatus(text: NightscoutStatus.warmup(s.sensor?.sensor, now: now), symbol: "hourglass")
        case .maintenance:
            // an expected wait, not an error: what is happening and when it ends, no Try Again
            return LibreLinkUpStatus(text: Self.maintenance(until: s.maintenanceUntil(at: now)), symbol: "clock")
        }
    }

    /// LibreView's maintenance in the status line's words, with its end when Abbott's status page gives one.
    static func maintenance(until: Date?) -> String {
        guard let until else {
            return L10n.t(
                "LibreView maintenance. LibreLinkUp sends no glucose until it ends; readings come back by themselves.")
        }
        let end = SukraraCore.Clock.time(until)
        return L10n.t(
            "LibreView maintenance until \(end). LibreLinkUp sends no glucose until then; readings come back by themselves."
        )
    }
}

/// A LibreView maintenance window ahead for the account's region (contracts/sources/librelinkup/status.json
/// `upcoming`): one quiet line, in the secondary colour with the clock symbol, never above the glucose. Settings ›
/// Source shows it once known, the panel from `LibreViewStatus.warnHome` before its start.
enum MaintenanceAhead {
    /// "LibreView maintenance today, 22:00–23:00: LibreLinkUp sends no glucose then".
    static func text(_ w: MaintenanceWindow, now: Date, calendar: Calendar = .current) -> String {
        let range = "\(SukraraCore.Clock.time(w.start))–\(SukraraCore.Clock.time(w.end))"
        return L10n.t(
            "LibreView maintenance \(day(w.start, now: now, calendar: calendar)), \(range): LibreLinkUp sends no glucose then"
        )
    }

    /// "today", "tomorrow", else a short weekday and date ("Thu 3 Dec").
    static func day(_ d: Date, now: Date, calendar: Calendar = .current) -> String {
        if calendar.isDate(d, inSameDayAs: now) { return L10n.t("today") }
        if let next = calendar.date(byAdding: .day, value: 1, to: now), calendar.isDate(d, inSameDayAs: next) {
            return L10n.t("tomorrow")
        }
        let f = DateFormatter()
        f.locale = L10n.locale
        f.calendar = calendar
        f.timeZone = calendar.timeZone
        f.setLocalizedDateFormatFromTemplate("EEEdMMM")
        return f.string(from: d)
    }

    /// The panel's line: from `warnHome` before the window's start until it starts (nil otherwise).
    static func panel(_ s: LibreLinkUpSnapshot, now: Date) -> String? {
        guard let w = s.upcoming(at: now), w.start.timeIntervalSince(now) <= LibreViewStatus.warnHome else {
            return nil
        }
        return text(w, now: now)
    }
}

/// The warn-ahead line (`MaintenanceAhead`): secondary text with the clock symbol; VoiceOver reads it as one line.
struct MaintenanceAheadRow: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "clock")
            .font(.callout)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .combine)
    }
}

/// The status line with its action, cross-fading as it changes (a fade with Reduce Motion), read once by VoiceOver
/// when it changes.
struct LibreLinkUpStatusRow: View {
    @Bindable var model: LibreLinkUpModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { _ in
            let status = LibreLinkUpStatus.of(model.snapshot(), now: model.now())
            HStack(alignment: .firstTextBaseline) {
                Label(status.text, systemImage: status.symbol)
                    .fixedSize(horizontal: false, vertical: true)
                    .id(status.symbol)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .leading)))
                Spacer()
                switch status.action {
                case .signIn: Button(L10n.t("Sign In Again"), action: model.signInAgain)
                case .tryAgain: Button(L10n.t("Try Again"), action: model.retry)
                case .recheck: Button(L10n.t("Try Again")) { Task { await model.recheck() } }.disabled(model.busy)
                case .pickAgain: Button(L10n.t("Pick Again")) { Task { await model.recheck() } }.disabled(model.busy)
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

/// The email and password (AutoFill offers a saved sign-in), *Sign In* with its progress, and a problem said once:
/// a refused password under the fields, the others as a notice.
struct LibreLinkUpForm: View {
    @Bindable var model: LibreLinkUpModel
    @FocusState private var passwordFocused: Bool

    var body: some View {
        LabeledContent {
            TextInput(label: L10n.t("Email"), text: $model.emailDraft, content: .username)
                .onChange(of: model.emailDraft) { _, _ in model.edited() }
                .onSubmit { passwordFocused = true }
        } label: {
            ServiceLabel(L10n.t("Email"), service: .libreLinkUp)
        }
        LabeledContent {
            VStack(alignment: .leading, spacing: 4) {
                TextInput(label: L10n.t("Password"), text: $model.passwordDraft, secure: true, content: .password)
                    .focused($passwordFocused)
                    .onChange(of: model.passwordDraft) { _, _ in model.edited() }
                    .onSubmit { Task { await model.connect() } }
                if model.result == .badCredentials, let p = LibreLinkUpModel.problem(model.result) {
                    Notice(p, tone: .error)
                }
            }
        } label: {
            ServiceLabel(L10n.t("Password"), symbol: "key")
        }
        .onChange(of: model.signInRequests) { _, _ in passwordFocused = true }
        LabeledContent {
            HStack(spacing: 10) {
                // Return signs in while the form shows (the step's Continue waits for the first answer)
                Button(model.busy ? L10n.t("Signing In…") : L10n.t("Sign In")) { Task { await model.connect() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(
                        model.busy || model.passwordDraft.isEmpty
                            || model.emailDraft.trimmingCharacters(in: .whitespaces).isEmpty)
                if model.busy { ProgressView().controlSize(.small) }
                if model.changing && !model.snapshot().email.isEmpty && !model.snapshot().signInAgain {
                    Button(L10n.t("Keep This Account")) {
                        model.changing = false
                        model.result = nil
                    }
                }
            }
        } label: {
            EmptyView()
        }
        if model.result != .badCredentials, let p = LibreLinkUpModel.problem(model.result) {
            Notice(p)
        }
    }
}

/// The note every LibreLinkUp place carries once: it isn't Sukrara's and may change or stop.
struct LibreLinkUpNote: View {
    static var text: String {
        L10n.t(
            "LibreLinkUp is Abbott's and isn't made for Sukrara: it may change or stop any day. Sukrara tells you when it does."
        )
    }

    var body: some View {
        Text(Self.text)
            .font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// *Whose glucose?* among the people the account follows: radio rows (setup) or a pop-up menu (Settings).
struct LibreLinkUpPeople: View {
    @Bindable var model: LibreLinkUpModel
    var menu = false

    var body: some View {
        let people = model.snapshot().people
        if menu {
            Picker(selection: Binding(get: { model.patient }, set: { model.choose($0) })) {
                if !people.contains(where: { $0.id == model.patient }) {
                    Text(L10n.t("Pick someone")).tag(model.patient)
                }
                ForEach(people, id: \.id) { p in Text(LibreLinkUpModel.name(p)).tag(p.id) }
            } label: {
                ServiceLabel(L10n.t("Person"), symbol: "person.crop.circle")
            }
        } else {
            Picker(selection: $model.picked) {
                ForEach(people, id: \.id) { p in Text(LibreLinkUpModel.name(p)).tag(Optional(p.id)) }
            } label: {
                EmptyView()
            }
            .pickerStyle(.radioGroup)
            .labelsHidden()
        }
    }
}

/// The confirmation before another person's glucose replaces the one this Mac reads: the alerts change person, so it
/// asks; nothing is deleted (the person read now is kept apart and comes back when picked again: `KeptHistory`).
struct LibreLinkUpConfirm: ViewModifier {
    @Bindable var model: LibreLinkUpModel

    func body(content: Content) -> some View {
        let who = model.confirming.map(LibreLinkUpModel.name) ?? ""
        let held = model.snapshot().name
        content.confirmationDialog(
            L10n.t("Read \(who)'s glucose?"),
            isPresented: Binding(get: { model.confirming != nil }, set: { if !$0 { model.confirming = nil } })
        ) {
            Button(L10n.t("Read \(who)'s Glucose")) {
                if let id = model.confirming?.id { model.write(id) }
            }
            Button(L10n.t("Cancel"), role: .cancel) { model.confirming = nil }
        } message: {
            Text(
                held.isEmpty
                    ? L10n.t(
                        "This Mac then shows \(who)'s glucose and alerts on it. The readings and treatments it holds now stay here, apart, and come back if that person is read again."
                    )
                    : L10n.t(
                        "This Mac then shows \(who)'s glucose and alerts on it instead of \(held)'s. \(held)'s readings and treatments stay here, apart, and come back if \(held) is read again."
                    ))
        }
    }
}

/// Settings › Source with LibreLinkUp: how it's going, the account (*Change Account…*, the form while signed out),
/// whose glucose, *Sign Out of LibreLinkUp…* (confirmed). The password is never shown.
struct LibreLinkUpRows: View {
    @Bindable var model: LibreLinkUpModel

    var body: some View {
        let snap = model.snapshot()
        let mode = model.mode
        Section {
            if !model.available() {
                Text(L10n.t("Not available in this run (development or test build).")).foregroundStyle(.secondary)
            }
            if mode == .form {
                if snap.signInAgain && model.result == nil {
                    Notice(L10n.t("LibreLinkUp refused the password. Sign in again."))
                }
                LibreLinkUpForm(model: model)
            } else {
                if !model.patient.isEmpty {
                    LibreLinkUpStatusRow(model: model)
                    if let w = snap.upcoming(at: model.now()) {
                        MaintenanceAheadRow(text: MaintenanceAhead.text(w, now: model.now()))
                    }
                } else if mode == .problem {
                    Notice(LibreLinkUpModel.problem(.noOne) ?? "") {
                        Button(model.busy ? L10n.t("Trying…") : L10n.t("Try Again")) {
                            Task { await model.recheck() }
                        }
                        .disabled(model.busy)
                    }
                }
                LabeledContent {
                    HStack {
                        Text(snap.email).textSelection(.enabled)
                        Button(L10n.t("Change Account…")) {
                            model.emailDraft = snap.email
                            model.changing = true
                        }
                    }
                } label: {
                    ServiceLabel(L10n.t("Account"), service: .libreLinkUp)
                }
                if snap.people.count > 1 || (!snap.people.isEmpty && mode == .choose) {
                    LibreLinkUpPeople(model: model, menu: true)
                } else if let only = snap.people.first(where: { $0.id == model.patient }) {
                    LabeledContent {
                        Text(LibreLinkUpModel.name(only))
                    } label: {
                        ServiceLabel(L10n.t("Person"), symbol: "person.crop.circle")
                    }
                }
            }
        } header: {
            Text(verbatim: "LibreLinkUp")
        } footer: {
            Text(LibreLinkUpNote.text).formFooter()
        }
        .onAppear(perform: model.open)
        .modifier(LibreLinkUpConfirm(model: model))
        if !snap.email.isEmpty {
            Section {
                HStack {
                    Spacer()
                    Button(L10n.t("Sign Out of LibreLinkUp…")) { model.signingOut = true }
                }
            }
            .confirmationDialog(
                L10n.t("Sign out of LibreLinkUp?"), isPresented: $model.signingOut
            ) {
                Button(L10n.t("Sign Out")) { model.signOut() }
                Button(L10n.t("Cancel"), role: .cancel) { model.signingOut = false }
            } message: {
                Text(
                    L10n.t(
                        "Sukrara gets no glucose until you sign in again or choose another source. The readings already here stay."
                    ))
            }
        }
    }
}

/// Setup's Connect for LibreLinkUp (Android `LluStep`): sign in, then *Whose glucose?* when the account follows several
/// people, then *Signed in as …* with whose glucose and how it's going (*Waiting for the first reading* → *Reading
/// received*: a check that pops in with a trackpad tap and a VoiceOver announcement; Reduce Motion: it fades in). No
/// one shared, or new terms: what to do and *Try Again* without the password. No glucose value shows here.
struct LibreLinkUpConnect: View {
    @Bindable var model: LibreLinkUpModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var received = false

    /// *Reading received*: a reading that is now's (live or late), not the old sensor's before a warm-up (the owner's
    /// test, 2026-11-18: a reading 4 hours old read as received).
    static func received(_ s: LibreLinkUpSnapshot, now: Date) -> Bool {
        !s.readings.isEmpty && [.live, .late].contains(s.health(at: now))
    }

    var body: some View {
        let snap = model.snapshot()
        let mode = model.mode
        VStack(alignment: .leading, spacing: 12) {
            switch mode {
            case .form:
                Text(
                    L10n.t(
                        "Use the LibreLinkUp account the Libre app invited. For your own glucose: in the Libre app, Menu › Connected apps › LibreLinkUp, invite your own email, then accept the invite in LibreLinkUp once."
                    )
                )
                .fixedSize(horizontal: false, vertical: true)
                Form { LibreLinkUpForm(model: model) }.formStyle(.columns)
            case .choose:
                Text(L10n.t("Whose glucose?")).font(.headline)
                Text(
                    L10n.t(
                        "This account follows several people. Pick the one Sukrara shows; you can change it in Settings › Source."
                    )
                )
                .fixedSize(horizontal: false, vertical: true)
                LibreLinkUpPeople(model: model)
                HStack {
                    Button(L10n.t("Change Account")) { model.changing = true }
                    Button(L10n.t("Choose")) { if let p = model.picked { model.choose(p) } }
                        .keyboardShortcut(.defaultAction)
                        .disabled(model.picked == nil)
                }
            case .problem:
                let terms = snap.health(at: model.now()) == .actionNeeded || model.result == .terms
                // the one picked stopped sharing, others still do: pick again, not "no one shares"
                let gone = !terms && snap.pickedGone && model.patient == snap.patient
                Notice(
                    L10n.t("Signed in as \(snap.email)"),
                    text: terms
                        ? LibreLinkUpModel.problem(.terms)
                        : gone ? LibreLinkUpStatus.pickedGone : LibreLinkUpModel.problem(.noOne)
                ) {
                    HStack {
                        Button(model.busy ? L10n.t("Trying…") : gone ? L10n.t("Pick Again") : L10n.t("Try Again")) {
                            Task { await model.recheck() }
                        }
                        .disabled(model.busy)
                        Button(L10n.t("Change Account")) { model.changing = true }
                    }
                }
            case .connected:
                connected(snap)
            }
            LibreLinkUpNote()
        }
        .animation(reduceMotion ? .easeInOut(duration: 0.25) : .spring(duration: 0.4, bounce: 0.2), value: mode)
        .onAppear(perform: model.open)
        .modifier(LibreLinkUpConfirm(model: model))
        .onChange(of: Self.received(snap, now: model.now()) && mode == .connected, initial: true) { _, isIn in
            guard isIn, !received else { return }
            NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
            AccessibilityNotification.Announcement(L10n.t("Reading received")).post()
            withAnimation(reduceMotion ? .easeIn(duration: 0.3) : .spring(duration: 0.45, bounce: 0.4)) {
                received = true
            }
        }
    }

    @ViewBuilder private func connected(_ snap: LibreLinkUpSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Label(L10n.t("Signed in as \(snap.email)"), systemImage: "person.crop.circle.badge.checkmark")
                Spacer()
                Button(L10n.t("Change Account")) { model.changing = true }
            }
            if !snap.name.isEmpty {
                Text(L10n.t("Reading \(snap.name)'s glucose.")).foregroundStyle(.secondary)
            }
            if !Self.received(snap, now: model.now()) {
                let status = LibreLinkUpStatus.of(snap, now: model.now())
                let health = snap.health(at: model.now())
                if health == .connecting || (health == .noNewReadings && snap.readings.isEmpty) {
                    HStack(spacing: 8) {
                        ProgressView().controlSize(.small)
                        Text(L10n.t("Waiting for the first reading from LibreLinkUp…")).foregroundStyle(.secondary)
                    }
                } else {
                    LibreLinkUpStatusRow(model: model).id(status.symbol)
                }
            } else {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L10n.t("Reading received"))
                        Text(L10n.t("The glucose is in the menu bar now.")).font(.caption).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                        .scaleEffect(received || reduceMotion ? 1 : 0.4)
                }
                .opacity(received ? 1 : 0)
                .accessibilityElement(children: .combine)
            }
        }
    }
}
