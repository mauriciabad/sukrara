// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SukraraCore
import SukraraGraph

/// A `NoDataReason` in the panel's words (String Catalog, en + es; Android's `reason_*` strings): the reason in a
/// line, why in a sentence, and the action's title. The widgets say the same reason in fewer words (`WidgetWords`).
struct NoDataText: Equatable {
    var title: String
    var body: String?
    var action: String?

    /// `lastData`: the Keychain retrying while the alerts go on over the stored readings (`AppModel.alertingOnLastData`).
    static func of(_ reason: NoDataReason, now: TimeInterval, lastData: Bool = false) -> NoDataText {
        let follow = L10n.t("Follow Someone…")
        let ask = L10n.t("Ask them for a new invite to follow again.")
        switch reason {
        case .offlineBuild:
            return NoDataText(
                title: L10n.t("Not monitoring: offline development build (no sensor, internet or alerts)"))
        case .notFollowing:
            return NoDataText(
                title: L10n.t("Not monitoring: not following anyone yet"),
                body: L10n.t(
                    "Open the invite link from the person you follow, or follow their phone nearby. No alerts ring until their first reading arrives."
                ),
                action: follow)
        case .waitingApproval:
            return NoDataText(
                title: L10n.t("Not monitoring: waiting for the wearer to allow this Mac"),
                body: L10n.t("They allow it on their phone. No alerts ring until their first reading arrives."),
                action: L10n.t("Following Settings…"))
        case .declined:
            return NoDataText(title: L10n.t("Not monitoring: the wearer declined"), body: ask, action: follow)
        case .inviteUsed:
            return NoDataText(
                title: L10n.t("Not monitoring: the invite was used by another device"),
                body: L10n.t("Each invite works once. Ask the wearer for a new one."), action: follow)
        case .revoked:
            return NoDataText(
                title: L10n.t("Not monitoring: the wearer stopped sharing"),
                body: L10n.t("They removed this Mac from their followers. \(ask)"), action: follow)
        case .broken(.keyUnreadable):
            // #109: retrying, and with stored readings still alerting on them (the card stays)
            return NoDataText(
                title: lastData
                    ? L10n.t("Can't read the follower key from the Keychain. Retrying; alerting on the last data.")
                    : L10n.t("Not monitoring: can't read the follower key from the Keychain. Retrying…"),
                body: L10n.t("Sukrara keeps trying while the Mac is unlocked."),
                action: L10n.t("Following Settings…"))
        case .broken(let problem):
            return NoDataText(
                title: L10n.t("Not monitoring: \(problem.text)"), action: L10n.t("Following Settings…"))
        case .wearerSilent(let since):
            let why = L10n.t("Their phone may be off, offline or out of battery.")
            guard let since else {
                return NoDataText(
                    title: L10n.t("Waiting for the first reading from the wearer"),
                    body: L10n.t("Nothing heard yet. \(why)"))
            }
            return NoDataText(
                title: L10n.t("No readings from the wearer's phone"),
                body: L10n.t("Last reading \(Freshness.timeText(since, now: now)). \(why)"))
        case .juggluco(let wait):
            let settings = L10n.t("Juggluco Settings…")
            switch wait {
            case .looking:
                return NoDataText(title: L10n.t("Not monitoring: looking for Juggluco on this network"))
            case .notFound:
                return NoDataText(
                    title: L10n.t("Not monitoring: Juggluco wasn't found on this network"),
                    body: L10n.t("Turn on Juggluco's web server on the phone, on this network, or enter its address."),
                    action: settings)
            case .firstReading:
                return NoDataText(title: L10n.t("Waiting for the first reading from Juggluco"))
            case .unreachable(let host):
                return NoDataText(
                    title: L10n.t("Not monitoring: can't reach Juggluco at \(host)"),
                    body: L10n.t("The phone may be asleep, off or on another network."), action: settings)
            case .refusedSecret:
                return NoDataText(
                    title: L10n.t("Not monitoring: Juggluco refused the API secret"),
                    body: L10n.t("Enter the API secret set in Juggluco."), action: settings)
            case .localNetworkDenied:
                return NoDataText(
                    title: L10n.t("Not monitoring: macOS doesn't let Sukrara use the local network"), action: settings)
            case .notHomeNetwork(let typed):
                return NoDataText(
                    title: L10n.t("Not monitoring: \(typed) isn't an address on your home network"), action: settings)
            }
        }
    }
}
