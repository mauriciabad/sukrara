// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI
@preconcurrency import UserNotifications

/// Whether macOS lets Sukrara post Time Sensitive notifications on this Mac. Decided from the build's entitlement
/// (`Entitlements.timeSensitive`: only a build with a provisioning profile has it) and what the person allows
/// (`UNNotificationSettings.timeSensitiveSetting`), re-read at launch, on activation and when the panel opens. Whether
/// Sukrara uses it is the person's choice ([FocusStatus], #184).
enum TimeSensitive: Equatable {
    /// Sukrara may post Time Sensitive notifications.
    case on
    /// The build may post Time Sensitive, but the person turned it off for Sukrara (System Settings › Notifications).
    case turnedOff
    /// The build has no Time Sensitive entitlement (built without a provisioning profile).
    case unavailable

    /// `setting` is the system's answer, `entitled` the build's entitlement, `permission` whether notifications were
    /// asked for yet: before that the system's answer means nothing, and an entitled build may post Time Sensitive. An
    /// entitled build the system still reports as not supported is told the truth: it can't.
    init(setting: UNNotificationSetting, entitled: Bool, permission: NotificationPermission) {
        if setting == .enabled {
            self = .on
        } else if !entitled {
            self = .unavailable
        } else if permission == .notAsked {
            self = .on
        } else {
            self = setting == .disabled ? .turnedOff : .unavailable
        }
    }
}

/// Where Sukrara stands with a Focus (Do Not Disturb, Sleep…), the Mac's side of Android's *Do Not Disturb* item
/// (#184). By default a Focus holds Sukrara's alerts back like any app's: no Time Sensitive, nothing around it. Letting
/// Sukrara through is the person's choice (schema `alerts.throughFocus`), through macOS's own exception: the alerts that
/// ring like an alarm post Time Sensitive, which a Focus lets through when it allows Time Sensitive notifications (or
/// any of Sukrara's alerts, when the person allows Sukrara in that Focus).
enum FocusStatus: Equatable {
    /// The default: a Focus holds Sukrara's alerts back.
    case respected
    /// Let through: the alerts that ring like an alarm are Time Sensitive.
    case through
    /// Let through, but Time Sensitive is off for Sukrara in System Settings: nothing goes through yet.
    case turnedOff
    /// This build can't post Time Sensitive: allowing Sukrara in each Focus is the only way through.
    case unavailable

    init(through: Bool, timeSensitive: TimeSensitive) {
        switch timeSensitive {
        case .unavailable: self = .unavailable
        case .turnedOff: self = through ? .turnedOff : .respected
        case .on: self = through ? .through : .respected
        }
    }

    /// Sukrara's alarm-level alerts post Time Sensitive.
    var alertsThrough: Bool { self == .through }

    /// The plain statement, the same as Android's.
    var statement: String {
        alertsThrough ? L10n.t("Sukrara alerts during a Focus") : L10n.t("Sukrara doesn't alert during a Focus")
    }

    /// What it means, in the person's words.
    var detail: String {
        switch self {
        case .respected:
            L10n.t(
                "While a Focus (Do Not Disturb, Sleep…) is on, macOS holds Sukrara's alerts back like any app's, unless you allow Sukrara in it."
            )
        case .through:
            L10n.t(
                "Alerts that ring like an alarm are Time Sensitive: they come through every Focus that allows Time Sensitive notifications."
            )
        case .turnedOff:
            L10n.t(
                "Time Sensitive notifications are off for Sukrara, so a Focus still holds its alerts back. Turn them on in Sukrara's notification settings."
            )
        case .unavailable:
            L10n.t(
                "This build of Sukrara can't send Time Sensitive alerts. To get its alerts during a Focus, allow Sukrara in that Focus."
            )
        }
    }

    var symbol: String { alertsThrough ? "bell.badge" : "moon.fill" }
}

/// The Focus statement with its one button (#184): *Let Sukrara Through* / *Don't Let Sukrara Through* (the choice,
/// [onChange] gets the new one), *Open Notification Settings* while Time Sensitive is off for Sukrara (with the way
/// back), *Open Focus Settings* on a build without Time Sensitive. In Settings › Alerts and the setup's notification
/// step; VoiceOver reads the statement, its meaning, then the button.
struct FocusChoice: View {
    let status: FocusStatus
    let onChange: (Bool) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(status.statement, systemImage: status.symbol)
                .font(.callout.weight(.medium))
                .contentTransition(.opacity)
            Text(status.detail)
                .font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                switch status {
                case .respected:
                    Button(L10n.t("Let Sukrara Through")) { change(true) }
                case .through:
                    Button(L10n.t("Don't Let Sukrara Through")) { change(false) }
                case .turnedOff:
                    Button(L10n.t("Open Notification Settings")) { AlertsRangesPane.openNotificationSettings() }
                    Button(L10n.t("Don't Let Sukrara Through")) { change(false) }
                case .unavailable:
                    Button(L10n.t("Open Focus Settings")) {
                        if let url = URL(string: "x-apple.systempreferences:com.apple.Focus-Settings.extension") {
                            NSWorkspace.shared.open(url)
                        }
                    }
                }
            }
        }
        .animation(reduceMotion ? nil : .snappy, value: status)
    }

    private func change(_ through: Bool) {
        NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
        onChange(through)
    }
}
