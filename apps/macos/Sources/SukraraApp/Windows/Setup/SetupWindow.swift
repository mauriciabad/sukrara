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
    /// Thresholds changed on the alert step; nothing is stored before Use these.
    var editedThresholds: [AlertKind: Double] = [:]
    @ObservationIgnored let store: any SettingsStoring
    /// Asks for notification permission; true = granted.
    @ObservationIgnored var requestNotifications: () async -> Bool = { false }
    /// Open at login (nil when this run may not touch login items: dev, fixture and snapshot runs).
    var loginItem: LoginItem?

    public init(settings: SettingsModel) {
        self.settings = settings
        self.store = settings.store
        self.flow = SetupFlow(store: settings.store)
        self.follow = settings.follow
    }

    func complete(_ s: SetupFlow.Step) { flow.complete(s); flow.save(to: store) }
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

    /// The five levels keep their order (very low < low < high < very high < extremely high); else Use these waits.
    var thresholdsInOrder: Bool {
        let levels = Self.reviewedKinds.filter { $0 != .noData }.map(threshold)
        return zip(levels, levels.dropFirst()).allSatisfy { $0 < $1 }
    }

    /// The levels come from the wearer or the defaults (not this Mac's own): changing one changes that.
    var levelsMirrored: Bool { settings.settingsFrom != .custom }

    /// Use these: stores the changed thresholds and completes the step (never confirmed silently: only this button).
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
/// Every step has Skip and Back; closing the window leaves "Finish setup" in the panel.
public struct SetupView: View {
    @Bindable var model: SetupModel
    var close: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(model: SetupModel, close: @escaping () -> Void) {
        self.model = model
        self.close = close
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Group {
                switch model.flow.current {
                case .welcome: welcome
                case .follow: page(.follow, L10n.t("Follow someone")) { FollowStep(follow: model.follow) }
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
            .transition(reduceMotion ? .opacity : .move(edge: .trailing).combined(with: .opacity))
        }
        .animation(reduceMotion ? nil : .spring(duration: 0.35), value: model.flow.current)
        .padding(24)
        .frame(width: 520, alignment: .topLeading)
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.t("Welcome to Sukrara")).font(.largeTitle.bold())
            Text(
                L10n.t(
                    "Sukrara shows the glucose of someone you follow in the menu bar and alerts you when it's out of range."
                ))
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
            Text(
                L10n.t(
                    "Sukrara alerts you at these values. Change any of them here: nothing changes until you choose Use these."
                ))
            Form {
                ForEach(SetupModel.reviewedKinds, id: \.self) { thresholdRow($0) }
            }
            .formStyle(.columns)
            .environment(\.glucoseUnit, model.settings.unit)  // the levels in the person's unit (mmol/L or mg/dL)
            if !model.thresholdsInOrder {
                // the colour only on the symbol: the words keep full contrast
                Label {
                    Text(
                        L10n.t(
                            "Keep the order: very low below low, low below high, high below very high, very high below extremely high."
                        ))
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                }
                .fixedSize(horizontal: false, vertical: true)
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
            let name = AlertsRangesPane.kindName(kind)
            NumberSettingRow(
                label: model.settings.shownAlerts[kind].on ? name : L10n.t("\(name) (off)"), format: format,
                value: Binding(get: { model.threshold(kind) }, set: { model.setThreshold($0, kind) }))
        }
    }

    /// Where the levels come from while they aren't this Mac's own, and what changing one does.
    private var mirroredNote: String {
        if model.settings.settingsFrom == .wearer, let who = model.settings.wearer?.wearer {
            return L10n.t(
                "These are \(who)'s alerts, from their phone. Changing a level makes them this Mac's own settings (Settings from: Custom)."
            )
        }
        return L10n.t(
            "These are Sukrara's defaults. Changing a level makes them this Mac's own settings (Settings from: Custom)."
        )
    }

    private func page(_ step: SetupFlow.Step, _ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title.bold())
            content()
            HStack {
                Button(L10n.t("Back")) { model.flow.back() }
                Spacer()
                Button(L10n.t("Skip")) { model.skip(step) }
                Button(primaryTitle(step)) { Task { await primary(step) } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(
                        (step == .follow && !followDone) || (step == .openAtLogin && model.loginItem == nil)
                            || (step == .alerts && !model.thresholdsInOrder))
            }
        }
    }

    private var followDone: Bool {
        switch model.follow.status {
        case .following, .waiting: true
        default: false
        }
    }

    private func primaryTitle(_ s: SetupFlow.Step) -> String {
        switch s {
        case .alerts: L10n.t("Use these")
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

/// Where Open at login stands under the step's text: on; waiting for approval or refused, with the reason and Open
/// Login Items… (the step stays until macOS has it on); a run that may not touch login items says so.
struct OpenAtLoginState: View {
    let loginItem: LoginItem?

    var body: some View {
        if let loginItem {
            if let note = OpenAtLoginRows.note(loginItem.status, error: loginItem.lastError) {
                // the colour only on the symbol: the words keep full contrast
                Label {
                    Text(note)
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                }
                .fixedSize(horizontal: false, vertical: true)
                Button(L10n.t("Open Login Items…"), action: loginItem.openSettings)
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
