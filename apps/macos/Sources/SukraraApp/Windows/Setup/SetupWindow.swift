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
    public let alerts: AlertSettingsStore
    @ObservationIgnored let store: any SettingsStoring
    /// Asks for notification permission; true = granted.
    @ObservationIgnored var requestNotifications: () async -> Bool = { false }
    /// Open at login (nil when this run may not touch login items: dev, fixture and snapshot runs).
    var loginItem: LoginItem?

    public init(store: any SettingsStoring, follow: FollowModel) {
        self.store = store
        self.flow = SetupFlow(store: store)
        self.follow = follow
        self.alerts = AlertSettingsStore(store: store)
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
}

/// First-run setup: Welcome → Follow → Alerts (thresholds confirmed) → Notifications → Open at login → Done.
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
                        Text(
                            L10n.t(
                                "A Focus (Do Not Disturb, Sleep…) can hold back Sukrara's alarms on this Mac. Allow Sukrara in each Focus, and keep the phone as the alarm."
                            )
                        )
                        .foregroundStyle(.secondary)
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

    private var thresholds: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.t("Sukrara alerts at these values. Use them, or change them in Settings › Alerts."))
            ForEach([AlertKind.veryLow, .low, .high, .veryHigh, .extremelyHigh], id: \.self) { k in
                LabeledContent(AlertsRangesPane.kindName(k)) {
                    Text(
                        model.alerts.settings[k].on ? "\(Int(model.alerts.settings[k].threshold)) mg/dL" : L10n.t("Off")
                    )
                }
                .frame(minHeight: 24)
            }
        }
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
                    .disabled((step == .follow && !followDone) || (step == .openAtLogin && model.loginItem == nil))
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
