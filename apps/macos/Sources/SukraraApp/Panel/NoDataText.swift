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
        case .inviteExpired:
            return NoDataText(
                title: L10n.t("Not monitoring: the invite expired before the wearer answered"),
                body: L10n.t("Each invite works for 24 hours. Ask the wearer for a new one."), action: follow)
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
        case .nightscout(let wait):
            let upload = L10n.t("Check the app that uploads to it.")
            switch wait {
            case .connecting:
                return NoDataText(title: L10n.t("Connecting to Nightscout…"))
            case .noGlucose:
                return NoDataText(title: L10n.t("Nightscout has no glucose yet"), body: upload)
            case .silent(let since):
                let ago = NightscoutStatus.ago(max(1, Int(now - since) / 60))
                return NoDataText(
                    title: L10n.t("Nothing new from Nightscout"),
                    body: L10n.t("Its last reading was \(ago).") + " " + upload)
            case .offline:
                return NoDataText(title: L10n.t("No internet"), body: L10n.t("Sukrara catches up when you're back."))
            case .unreachable(let host):
                return NoDataText(
                    title: L10n.t("No answer from \(host)"), body: L10n.t("Check the address."),
                    action: L10n.t("Nightscout Settings…"))
            case .signedOut:
                return NoDataText(
                    title: L10n.t("Nightscout refused the token"), action: L10n.t("Sign In Again…"))
            case .rateLimited:
                return NoDataText(
                    title: L10n.t("Nightscout asked Sukrara to wait"), body: L10n.t("It tries again by itself."))
            }
        case .libreLinkUp(let wait):
            let settings = L10n.t("LibreLinkUp Settings…")
            switch wait {
            case .notSignedIn:
                return NoDataText(title: L10n.t("Not signed in to LibreLinkUp"), action: L10n.t("Sign In…"))
            case .connecting:
                return NoDataText(title: L10n.t("Connecting to LibreLinkUp…"))
            case .noGlucose:
                return NoDataText(
                    title: L10n.t("LibreLinkUp has no glucose yet"),
                    body: L10n.t("The Libre app sends it once its sensor runs."))
            case .silent(let since):
                let ago = NightscoutStatus.ago(max(1, Int(now - since) / 60))
                return NoDataText(
                    title: L10n.t("Nothing new from LibreLinkUp"),
                    body: L10n.t("Its last reading was \(ago).") + " "
                        + L10n.t("Open the Libre app: it may have stopped uploading."))
            case .offline:
                return NoDataText(title: L10n.t("No internet"), body: L10n.t("Sukrara catches up when you're back."))
            case .unreachable:
                return NoDataText(
                    title: L10n.t("No answer from LibreLinkUp"), body: L10n.t("It tries again by itself."))
            case .signedOut:
                return NoDataText(title: L10n.t("LibreLinkUp refused the password"), action: L10n.t("Sign In Again…"))
            case .rateLimited:
                return NoDataText(
                    title: L10n.t("LibreLinkUp asked Sukrara to wait"), body: L10n.t("It tries again by itself."))
            case .terms:
                return NoDataText(
                    title: L10n.t("LibreLinkUp needs you"),
                    body: L10n.t("Open the LibreLinkUp app, sign in and accept the new terms."), action: settings)
            case .notShared:
                return NoDataText(
                    title: L10n.t("No one shares glucose with this account now"),
                    body: L10n.t("In the Libre app: Menu › Connected apps › LibreLinkUp, invite this email."),
                    action: settings)
            case .pickedGone:
                return NoDataText(
                    title: L10n.t("The person picked stopped sharing glucose"),
                    body: L10n.t("Others still share with this account: pick again."), action: settings)
            case .stopped:
                return NoDataText(
                    title: L10n.t("LibreLinkUp stopped answering Sukrara"),
                    body: L10n.t("Abbott changed it. An update of Sukrara may bring it back."), action: settings)
            }
        }
    }
}
