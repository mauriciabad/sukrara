// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SwiftUI
@preconcurrency import UserNotifications

/// Whether Sukrara's alarms (an alert set *Like an alarm*) break through a Focus on this Mac. Decided from the build's
/// entitlement (`Entitlements.timeSensitive`: only a build with a provisioning profile has it) and what the person
/// allows (`UNNotificationSettings.timeSensitiveSetting`), re-read at launch, on activation and when the panel opens.
enum TimeSensitive: Equatable {
    /// Alarms post Time Sensitive: they show and play during a Focus that allows Time Sensitive notifications.
    case on
    /// The build may post Time Sensitive, but the person turned it off for Sukrara (System Settings › Notifications).
    case turnedOff
    /// The build has no Time Sensitive entitlement (built without a provisioning profile): alarms are normal
    /// notifications, which a Focus can hold back.
    case unavailable

    /// `setting` is the system's answer, `entitled` the build's entitlement, `permission` whether notifications were
    /// asked for yet: before that the system's answer means nothing, and an entitled build's alarms will be Time
    /// Sensitive. An entitled build the system still reports as not supported is told the truth: a Focus can hold
    /// its alarms back.
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

    /// What it means for the alarms, in the person's words.
    var text: String {
        switch self {
        case .on:
            L10n.t(
                "Alarms are Time Sensitive: they show and play during a Focus (Do Not Disturb, Sleep…) that allows Time Sensitive notifications."
            )
        case .turnedOff:
            L10n.t(
                "Time Sensitive notifications are off for Sukrara, so a Focus (Do Not Disturb, Sleep…) can hold back its alarms. Turn them on in Sukrara's notification settings."
            )
        case .unavailable:
            L10n.t(
                "A Focus (Do Not Disturb, Sleep…) can hold back Sukrara's alarms on this Mac. Allow Sukrara in each Focus, and keep the phone as the alarm."
            )
        }
    }

    var symbol: String {
        switch self {
        case .on: "bell.badge"
        case .turnedOff, .unavailable: "moon.fill"
        }
    }
}

/// The Time Sensitive line with its way out: *Open Notification Settings* (Sukrara's page, to turn Time Sensitive
/// back on) or *Open Focus Settings* (to allow Sukrara in each Focus); none while alarms break through. Shown in the
/// panel (only while a Focus can hold the alarms back), the setup's notification step and Settings › Alerts.
struct TimeSensitiveNote: View {
    let state: TimeSensitive

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(state.text, systemImage: state.symbol)
                .font(.callout)
                .fixedSize(horizontal: false, vertical: true)
            switch state {
            case .on: EmptyView()
            case .turnedOff:
                Button(L10n.t("Open Notification Settings")) { AlertsRangesPane.openNotificationSettings() }
            case .unavailable:
                Button(L10n.t("Open Focus Settings")) {
                    if let url = URL(string: "x-apple.systempreferences:com.apple.Focus-Settings.extension") {
                        NSWorkspace.shared.open(url)
                    }
                }
            }
        }
    }
}
