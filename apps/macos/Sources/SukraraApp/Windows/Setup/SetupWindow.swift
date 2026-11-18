// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraAlerts
import SukraraCore
import SwiftUI

/// The setup walk's state for the view: the flow plus what the steps act on.
@Observable
public final class SetupModel {
    public var flow: SetupFlow
    public let follow: FollowModel
    /// The settings window's model: the alert rules in use and "Settings from" (one alert store for both windows, so
    /// neither writes back a stale copy of the other's change).
    public let settings: SettingsModel
    /// Thresholds changed on the alert step; nothing is stored before Confirm thresholds.
    var editedThresholds: [AlertKind: Double] = [:]
    /// The source picked on the source step.
    var pickedKind: SourceKind = .sukrara
    /// *Do you have Sukrara on your phone?*: yes = follow it, no = read it online (nil = not answered here yet).
    var pickedPhone: Bool?
    @ObservationIgnored let store: any SettingsStoring
    /// Asks for notification permission; true = granted.
    @ObservationIgnored var requestNotifications: () async -> Bool = { false }
    /// Open at login (nil when this run may not touch login items: dev, fixture and snapshot runs).
    var loginItem: LoginItem?

    public init(settings: SettingsModel) {
        self.settings = settings
        self.store = settings.store
        var flow = SetupFlow(store: settings.store)
        // a Mac set up before the first question: its source answers it
        if !flow.isFirstRun, flow.states[.phone] == nil, SourceSpec.stored(in: settings.store) != nil {
            var states = flow.states
            states[.phone] = .done
            flow = SetupFlow(states: states)
            flow.save(to: settings.store)
        }
        self.flow = flow
        self.follow = settings.follow
    }

    /// The first question's answer as shown: picked here, else what the Mac runs on (a stream = yes), else yes: the
    /// recommended answer (one person, one link: following Sukrara brings alerts, treatments and settings too).
    var phone: Bool { pickedPhone ?? settings.source.running.map { $0.kind == .sukrara } ?? true }

    /// *Do you have Sukrara on your phone?*'s Continue: yes stores another Sukrara as the source and goes on to
    /// Follow; no to the sources this Mac reads itself.
    func choosePhone() {
        let yes = phone
        pickedPhone = yes
        if yes {
            pickedKind = .sukrara
            settings.source.choose(.sukrara)
            complete(.phone)
            complete(.source)
            return
        }
        if pickedKind == .sukrara {
            pickedKind = settings.source.running.map(\.kind).flatMap { $0 == .sukrara ? nil : $0 } ?? .nightscout
        }
        complete(.phone)
    }

    /// Back, over the source step when the phone is followed (it was answered by the first question).
    func back() { flow.back(over: phone == true ? [.source] : []) }

    /// The sources the source step offers: those this Mac reads itself (another Sukrara is the first question's yes).
    var onlineKinds: Set<SourceKind> { Set(SourceKind.allCases.filter { $0 != .sukrara }) }

    func complete(_ s: SetupFlow.Step) { flow.complete(s); flow.save(to: store) }

    /// The source step's Continue: the source is stored, or another source while following asks *Keep following?*
    /// first (the walk goes on once it was left: `leftLink`).
    func chooseSource() {
        if settings.leave.pick(pickedKind, from: .setup) { complete(.source) }
    }

    /// The link was left from the source step with a source picked: on to its step.
    func leftLink(_ picked: SourceKind?) {
        if picked != nil, flow.current == .source { complete(.source) }
    }

    /// The source the walk goes on with (the follow step becomes Connect Juggluco for Juggluco).
    var runningKind: SourceKind { settings.source.running?.kind ?? pickedKind }
    func skip(_ s: SetupFlow.Step) { flow.skip(s); flow.save(to: store) }

    /// The Open at login step's primary action: asks macOS, and the step is done only once Sukrara is a login item.
    /// Waiting for approval or refused, the step stays with the reason and Open Login Items… (Skip leaves it for
    /// Finish Setup…); after approving in System Settings the same button finds it on.
    func openAtLogin() {
        guard let loginItem else { return }
        loginItem.refresh()
        if loginItem.status != .on { loginItem.set(true) }
        if loginItem.status == .on { complete(.openAtLogin) }
    }

    // MARK: the alert step

    /// The alert step's rows, as in Android's setup: the five levels and no data.
    static let reviewedKinds: [AlertKind] = [.veryLow, .low, .high, .veryHigh, .extremelyHigh, .noData]

    /// A row's value: the change made here, else the threshold in use (the wearer's, the defaults or this Mac's own;
    /// no data is always this Mac's).
    func threshold(_ kind: AlertKind) -> Double { editedThresholds[kind] ?? settings.shownAlerts[kind].threshold }

    func setThreshold(_ value: Double, _ kind: AlertKind) { editedThresholds[kind] = value }

    /// The five levels keep their order (very low < low < high < very high < extremely high); else Confirm thresholds waits.
    var thresholdsInOrder: Bool {
        let levels = Self.reviewedKinds.filter { $0 != .noData }.map(threshold)
        return zip(levels, levels.dropFirst()).allSatisfy { $0 < $1 }
    }

    /// The levels come from the wearer or the defaults (not this Mac's own): changing one changes that.
    var levelsMirrored: Bool { settings.settingsFrom != .custom }

    /// Confirm thresholds: stores the changed thresholds and completes the step (never confirmed silently: only this button).
    /// A changed level applies only as this Mac's own rule, so the settings in use become this Mac's own first
    /// (Custom, starting from the wearer's or the defaults: nothing else on screen changes); no data is always this
    /// Mac's. Nothing changed = nothing stored.
    func confirmThresholds() {
        guard thresholdsInOrder else { return }
        let shown = settings.shownAlerts
        let changed = editedThresholds.filter { shown[$0.key].threshold != $0.value }
        if changed.keys.contains(where: { $0 != .noData }) { settings.useInUseAsOwn() }
        if !changed.isEmpty { settings.alerts.update { s in for (k, v) in changed { s[k].threshold = v } } }
        editedThresholds = [:]
        complete(.alerts)
    }
}

/// First-run setup: Welcome → Follow → Alerts (thresholds checked, changed, confirmed) → Notifications → Open at
/// login → Done.
/// Every step has Back and, but the source's two, Skip; closing the window leaves "Finish setup" in the panel.
public struct SetupView: View {
    @Bindable var model: SetupModel
    var close: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(model: SetupModel, close: @escaping () -> Void) {
        self.model = model
        self.close = close
    }

    public var body: some View {
        // one step at a time in a ZStack: the step left fades out in place while the next one comes in
        ZStack(alignment: .topLeading) {
            Group {
                switch model.flow.current {
                case .welcome: welcome
                case .phone:
                    page(.phone, L10n.t("Do you have Sukrara on your phone?")) {
                        PhoneChoice(selected: Binding(get: { model.phone }, set: { model.pickedPhone = $0 }))
                        Text(L10n.t("You can change it any time in Settings › Source.")).font(.callout)
                            .foregroundStyle(.secondary)
                    }
                case .source:
                    page(.source, L10n.t("Where does the glucose come from?")) {
                        SourceKindChoice(
                            selected: Binding(get: { model.pickedKind }, set: { $0.map { model.pickedKind = $0 } }),
                            only: model.phone ? nil : model.onlineKinds)
                    }
                case .follow:
                    if model.runningKind == .nightscout {
                        page(.follow, L10n.t("Connect Nightscout"), service: .nightscout) {
                            NightscoutConnect(model: model.settings.nightscout)
                        }
                    } else if model.runningKind == .libreLinkUp {
                        page(.follow, L10n.t("Sign in to LibreLinkUp"), service: .libreLinkUp) {
                            LibreLinkUpConnect(model: model.settings.libreLinkUp)
                        }
                    } else if model.runningKind == .lanJuggluco {
                        page(.follow, L10n.t("Connect Juggluco"), service: .juggluco) {
                            Form { JugglucoRows(model: model.settings.juggluco) }.formStyle(.grouped)
                                .onAppear(perform: model.settings.juggluco.load)
                                .onDisappear {
                                    model.settings.juggluco.saveAddress()
                                    model.settings.juggluco.saveSecret()
                                }
                        }
                    } else {
                        page(.follow, L10n.t("Follow someone"), service: .sukrara) { FollowStep(follow: model.follow) }
                    }
                case .alerts: page(.alerts, L10n.t("Check your alerts")) { thresholds }
                case .notifications:
                    page(.notifications, L10n.t("Allow notifications")) {
                        Text(L10n.t("Alerts reach you as notifications, even when Sukrara is in the background."))
                        if let status = model.settings.focusStatus {
                            FocusChoice(status: status) { model.settings.throughFocus = $0 }
                        }
                    }
                case .openAtLogin:
                    page(.openAtLogin, L10n.t("Open at login")) {
                        Text(L10n.t("Sukrara starts when you log in, so the menu bar always shows the glucose."))
                        OpenAtLoginState(loginItem: model.loginItem)
                    }
                case nil: done
                }
            }
            .id(model.flow.current)
            .transition(Self.step(back: model.flow.wentBack, reduceMotion: reduceMotion))
        }
        .animation(Self.stepAnimation(reduceMotion: reduceMotion), value: model.flow.current)
        .modifier(LeaveLinkPresenter(model: model.settings.leave, origin: .setup))
        .padding(24)
        .frame(width: 520, alignment: .topLeading)
    }

    /// A step's way in, as a stepped flow moves on the Mac (a navigation push, Setup Assistant's panes): forward it
    /// comes in from the trailing edge, Back from the leading one, fading in; the step left fades out in place (its
    /// removal keeps the transition it was drawn with, so it carries no direction). Reduce Motion: a cross-fade only.
    static func step(back: Bool, reduceMotion: Bool) -> AnyTransition {
        if reduceMotion { return .opacity }
        return .asymmetric(
            insertion: .offset(x: back ? -stepSlide : stepSlide).combined(with: .opacity),
            removal: .opacity)
    }

    /// The spring the steps move on; Reduce Motion: a short fade (nothing moves).
    static func stepAnimation(reduceMotion: Bool) -> Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }

    /// How far a step slides in (points): enough to read the direction, short enough to stay quick.
    static let stepSlide: CGFloat = 40

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.t("Welcome to Sukrara")).font(.largeTitle.bold())
            Text(
                L10n.t(
                    "Sukrara shows glucose in the menu bar and alerts you when it's out of range."
                )
            )
            .fixedSize(horizontal: false, vertical: true)
            Text(
                L10n.t(
                    "Sukrara isn't a medical device: values can be wrong, late or missing, and alerts may not ring. Check your sensor's app or a meter, and ask your doctor, before you decide on a treatment."
                )
            )
            .font(.callout).foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
            HStack {
                Spacer()
                Button(L10n.t("Set up later")) {
                    model.skip(.welcome); close()
                }
                Button(L10n.t("Start")) { model.complete(.welcome) }.keyboardShortcut(.defaultAction)
            }
        }
    }

    /// The five levels and no data, each with THE number input over the step's draft; the order rule and where the
    /// levels come from under them.
    private var thresholds: some View {
        VStack(alignment: .leading, spacing: 8) {
            // the one thing the rows don't show: every other settings change applies at once, these wait
            Text(L10n.t("Nothing is saved until you confirm."))
            Form {
                ForEach(SetupModel.reviewedKinds, id: \.self) { thresholdRow($0) }
            }
            .formStyle(.columns)
            .environment(\.glucoseUnit, model.settings.unit)  // the levels in the person's unit (mmol/L or mg/dL)
            if !model.thresholdsInOrder {
                Notice(
                    L10n.t(
                        "Keep the order: very low below low, low below high, high below very high, very high below extremely high."
                    ))
            }
            if model.levelsMirrored {
                Text(mirroredNote).font(.callout).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder private func thresholdRow(_ kind: AlertKind) -> some View {
        let id = "alerts.threshold.\(kind.rawValue)"
        if let number = SettingsSpec.settings.first(where: { $0.id == id })?.number,
            let format = SettingsSpec.numbers[number]
        {
            let on = model.settings.shownAlerts[kind].on
            NumberSettingRow(
                label: AlertsRangesPane.kindName(kind), format: format,
                value: Binding(get: { model.threshold(kind) }, set: { model.setThreshold($0, kind) }),
                span: { v in Self.thresholdLine(kind, v, on: on, unit: model.settings.unit) })
        }
    }

    /// The line under a threshold: what sets the alert off, the comparison the engine makes on the number in the pop-up
    /// beside it (which carries the unit): "Up to 55", "Below 70", "From 180" (SPEC §1); "Off" for an alert that is off;
    /// nothing for no data, whose "20 min" already says it. As Android's review rows (`reviewThreshold`).
    static func thresholdLine(_ kind: AlertKind, _ v: Double, on: Bool, unit: GlucoseUnit) -> String? {
        if !on { return L10n.t("Off") }
        let n = unit.format(Int(v))
        switch kind {
        case .low: return L10n.t("Below \(n)")
        case .veryLow: return L10n.t("Up to \(n)")
        case .high, .veryHigh, .extremelyHigh: return L10n.t("From \(n)")
        case .fallingFast, .risingFast, .noData: return nil
        }
    }

    /// Where the levels come from while they aren't this Mac's own, and what changing one does.
    private var mirroredNote: String {
        if model.settings.settingsFrom == .wearer, let who = model.settings.wearer?.wearer {
            return L10n.t(
                "These are \(who)'s alerts, from their phone. Changing a level makes them this Mac's own settings (Settings from: This Mac's own)."
            )
        }
        return L10n.t(
            "These are Sukrara's defaults. Changing a level makes them this Mac's own settings (Settings from: This Mac's own)."
        )
    }

    /// A step: its title (with the service's icon on a connect step, as macOS's account sign-in sheets show the
    /// provider's), what it asks, then Back, Skip and its primary button.
    private func page(
        _ step: SetupFlow.Step, _ title: String, service: AppService? = nil, @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                if let service { ServiceIcon(service: service, size: .twoLines) }
                Text(title).font(.title.bold())
            }
            content().environment(\.serviceRowIcons, service == nil)
            HStack {
                Button(L10n.t("Back")) { model.back() }
                Spacer()
                // the source's two questions can't be skipped (Android's rule too): without them Sukrara reads
                // nothing; closing the window leaves them on Finish Setup…
                if step != .phone && step != .source {
                    Button(L10n.t("Skip")) { model.skip(step) }
                }
                // one default button per window: on Follow someone, Return belongs to the invite link's Continue until
                // the follow is done
                Button(primaryTitle(step)) { Task { await primary(step) } }
                    .keyboardShortcut(step == .follow && !followDone ? nil : .defaultAction)
                    .disabled(
                        (step == .follow && !followDone) || (step == .openAtLogin && model.loginItem == nil)
                            || (step == .alerts && !model.thresholdsInOrder)
                    )
            }
        }
    }

    private var followDone: Bool {
        if model.runningKind == .lanJuggluco { return true }
        if model.runningKind == .nightscout {
            // connected: the site answered (Waiting for the first reading may take a few minutes)
            return model.settings.nightscout.snapshot().monitoring
        }
        if model.runningKind == .libreLinkUp {
            // connected: LibreLinkUp answered for the person picked
            let llu = model.settings.libreLinkUp
            return llu.mode == .connected && llu.snapshot().monitoring
        }
        switch model.follow.status {
        case .following, .waiting: return true
        default: return false
        }
    }

    private func primaryTitle(_ s: SetupFlow.Step) -> String {
        switch s {
        case .alerts: L10n.t("Confirm thresholds")
        case .notifications: L10n.t("Allow…")
        case .openAtLogin: model.loginItem?.status == .on ? L10n.t("Continue") : L10n.t("Open at login")
        default: L10n.t("Continue")
        }
    }

    private func primary(_ s: SetupFlow.Step) async {
        switch s {
        case .notifications:
            if await model.requestNotifications() { model.complete(s) } else { model.skip(s) }
        case .openAtLogin: model.openAtLogin()
        case .alerts: model.confirmThresholds()
        case .phone: model.choosePhone()
        case .source: model.chooseSource()
        default: model.complete(s)
        }
    }

    private var done: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.flow.isComplete ? L10n.t("All set") : L10n.t("Almost done")).font(.title.bold())
            if !model.flow.isComplete {
                Text(L10n.t("Some steps were skipped. \"Finish Setup…\" in the Sukrara menu takes you back to them."))
            }
            HStack {
                Spacer()
                if !model.flow.isComplete { Button(L10n.t("Finish now")) { model.flow.resume() } }
                Button(L10n.t("Done"), action: close).keyboardShortcut(.defaultAction)
            }
        }
    }
}

/// *Do you have Sukrara on your phone?*: two big choices side by side (tonal fill, a stronger outline, a spring, a
/// trackpad tap; Reduce Motion: at once), *Yes* first, preselected and marked *Recommended* (one person, one link).
/// The words fit you following your own phone and a parent following a child's. VoiceOver: two buttons with the
/// selected trait, the recommendation read with the first.
struct PhoneChoice: View {
    @Binding var selected: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Each card's picks: its symbol bounces only when that card is picked (a shared value bounced the card left too).
    @State private var picks: [Bool: Int] = [:]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            card(
                true, symbol: "iphone.radiowaves.left.and.right", title: L10n.t("Yes · Follow it"),
                means: L10n.t(
                    "Follow the phone that reads the sensor, yours or your child's: alerts, treatments and settings come with it"
                ), recommended: true)
            card(
                false, symbol: "globe", title: L10n.t("No · Read it here"),
                means: L10n.t(
                    "LibreLinkUp, Nightscout or Juggluco read on this Mac, treatments logged here: no Sukrara on the phone"
                ))
        }
    }

    private func card(_ value: Bool, symbol: String, title: String, means: String, recommended: Bool = false)
        -> some View
    {
        let on = selected == value
        return Button {
            if !on {
                NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .now)
                if !reduceMotion { picks[value, default: 0] += 1 }
            }
            selected = value
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    Image(systemName: symbol)
                        .font(.system(size: 30, weight: .regular))
                        .foregroundStyle(on ? Color.accentColor : .secondary)
                        .symbolEffect(.bounce, value: picks[value, default: 0])
                        .frame(height: 40)
                    Spacer()
                    if recommended {
                        Text(L10n.t("Recommended"))
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.accentColor.opacity(on ? 0.22 : 0.12)))
                            .foregroundStyle(Color.accentColor)
                    }
                    Image(systemName: on ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(on ? Color.accentColor : .secondary)
                        .font(.title3)
                }
                Text(title).font(.title3.bold())
                Text(means).font(.callout).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 170, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(on ? Color.accentColor.opacity(0.14) : Color(nsColor: .controlBackgroundColor))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(on ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: on ? 2 : 1)
            )
            .shadow(color: .black.opacity(on ? 0.12 : 0), radius: on ? 6 : 0, y: on ? 2 : 0)
            .modifier(ChoiceCardMotion(on: on))
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(on ? [.isSelected, .isButton] : .isButton)
    }
}

/// Where Open at login stands under the step's text: on; waiting for approval or refused, with the reason and Open
/// Login Items… (the step stays until macOS has it on); a run that may not touch login items says so.
struct OpenAtLoginState: View {
    let loginItem: LoginItem?

    var body: some View {
        if let loginItem {
            if let note = OpenAtLoginRows.note(loginItem.status, error: loginItem.lastError) {
                Notice(note) {
                    Button(L10n.t("Open Login Items…"), action: loginItem.openSettings)
                }
            } else if loginItem.status == .on {
                Label {
                    Text(L10n.t("Sukrara opens at login."))
                } icon: {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                }
            }
        } else {
            Text(L10n.t("Not available in this run (development or test build).")).foregroundStyle(.secondary)
        }
    }
}
