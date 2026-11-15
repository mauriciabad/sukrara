// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraShare

/// A feature that is on and in use but macOS blocks it, so it silently does nothing (docs/40, the Mac's side of
/// Android's `BlockedFeature`). The panel names the first, under the notifications warning; the settings pane that
/// holds the feature keeps the fix. Every item has a way out besides Allow: turning the feature off, or, where there
/// is no switch, Not Now (until the next launch).
enum BlockedFeature: String, CaseIterable, Equatable {
    /// Following someone, Bluetooth not allowed for Sukrara: no readings from the phone nearby when the internet is
    /// down (the Bluetooth path has no switch: it is the backup of the internet one).
    case bluetoothFollowing
    /// Open at login is on but waits for the person's approval in System Settings › General › Login Items: after a
    /// restart nothing monitors until Sukrara is opened by hand.
    case openAtLogin

    /// The feature has a switch here (Turn Off is the way out); else Not Now.
    var canTurnOff: Bool { self == .openAtLogin }

    var title: String {
        switch self {
        case .bluetoothFollowing: L10n.t("Bluetooth is blocked")
        case .openAtLogin: L10n.t("Open at login is waiting for you")
        }
    }

    var text: String {
        switch self {
        case .bluetoothFollowing:
            L10n.t("Allow Bluetooth so readings still come from the phone nearby when the internet is down.")
        case .openAtLogin:
            L10n.t("Allow Sukrara in Login Items, or it won't start after a restart and nothing alerts you.")
        }
    }

    var allow: String {
        switch self {
        case .bluetoothFollowing: L10n.t("Open Bluetooth Settings")
        case .openAtLogin: L10n.t("Open Login Items")
        }
    }

    /// What is blocked now, in case order (pure: BlockedFeaturesTests).
    static func of(link: LinkState, nearby: NearbyState?, login: LoginItem.Status?) -> [BlockedFeature] {
        var out: [BlockedFeature] = []
        if link.isFollowing, nearby == .unauthorized { out.append(.bluetoothFollowing) }
        if login == .needsApproval { out.append(.openAtLogin) }
        return out
    }

    /// System Settings › Privacy & Security › Bluetooth, where Sukrara is allowed.
    static func openBluetoothSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Bluetooth") {
            NSWorkspace.shared.open(url)
        }
    }
}
