// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import SukraraShare
import SwiftUI
import UniformTypeIdentifiers

/// Follow someone: paste or open an invite link, import a QR image, or pair with a phone nearby. Used by the setup
/// walk and by Settings › General › Follow….
struct FollowStep: View {
    @Bindable var follow: FollowModel
    @State private var importing = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if case .waiting(let wearer, let pairing?) = follow.status {
                PairingCodeView(follow: follow, wearer: wearer ?? L10n.t("the wearer"), check: pairing)
            } else if let phone = follow.nearby {
                NearbyPairingDialog(follow: follow, phone: phone)
            }
            Text(L10n.t("Paste the invite link from the wearer's phone (Sukrara › Share › Invite)."))
            HStack {
                TextField(L10n.t("Invite link"), text: $follow.linkText)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { Task { await follow.follow() } }
                    .accessibilityLabel(L10n.t("Invite link"))
                Button(L10n.t("Follow")) { Task { await follow.follow() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(follow.linkText.isEmpty || follow.status == .joining)
            }
            Button(L10n.t("Import a QR code image…")) { importing = true }
            if follow.canFollow, follow.nearby == nil { MacNameField(follow: follow) }
            if follow.status == .joining {
                HStack {
                    ProgressView().controlSize(.small)
                    Text(L10n.t("Looking up the invite…"))
                    Button(L10n.t("Cancel")) { Task { await follow.cancel() } }
                }
            }
            if case .waiting(let wearer, nil) = follow.status {
                HStack {
                    ProgressView().controlSize(.small)
                    Text(L10n.t("Waiting for \(wearer ?? L10n.t("the wearer")) to approve"))
                    Button(L10n.t("Cancel")) { Task { await follow.cancel() } }
                }
            }
            if let failure = follow.failure {
                Label(failure.message, systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
                    .accessibilityAddTraits(.isStaticText)
            }
        }
        .onChange(of: follow.canFollow, initial: true) { _, can in follow.searchNearby(can) }
        .onDisappear { follow.searchNearby(false) }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.image]) { result in
            let text = (try? result.get()).flatMap { url -> String? in
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                return QRImport.inviteText(atURL: url)
            }
            Task { await follow.follow(qrText: text) }
        }
    }
}

/// The name the wearer will see for this Mac (the computer's name until changed); a blank one keeps the name sent last.
struct MacNameField: View {
    @Bindable var follow: FollowModel

    var body: some View {
        LabeledContent(L10n.t("Name for this Mac")) {
            TextField(L10n.t("Name for this Mac"), text: $follow.macName)
                .textFieldStyle(.roundedBorder)
                .labelsHidden()
                .frame(maxWidth: 240)
                .onChange(of: follow.macName) {
                    if follow.macName.utf16.count > Profile.maxName {
                        follow.macName = Profile.normalisedName(typed: follow.macName, previous: "")
                    }
                }
        }
        .help(L10n.t("The wearer sees this name in their list of people who follow them"))
    }
}

/// A phone nearby offers pairing: its avatar, this Mac's name (editable), "Ask to follow" / "Not now". Asking
/// moves on to `PairingCodeView`.
struct NearbyPairingDialog: View {
    @Bindable var follow: FollowModel
    let phone: NearbyPhone

    var body: some View {
        GroupBox {
            HStack(alignment: .top, spacing: 12) {
                AvatarView(avatar: phone.avatar, name: phone.wearer, size: 44)
                VStack(alignment: .leading, spacing: 8) {
                    Text(L10n.t("\(phone.wearer)'s phone is nearby")).font(.headline)
                    MacNameField(follow: follow)
                    HStack {
                        Button(L10n.t("Ask to follow")) { Task { await follow.askNearby() } }
                        Button(L10n.t("Not now")) { follow.nearby = nil }
                    }
                }
            }
            .padding(4)
        }
        .accessibilityElement(children: .contain)
    }
}

/// A nearby join waiting (PROTOCOL-v2 "Nearby pairing"): "Asking Marta's phone…" until the phone answers, then the
/// 6-digit code both devices show with *Same Code* / *Different Code*; after *Same Code*, waiting for the wearer's
/// Allow. Cancel ends the join at any point (the follower key goes). Nothing of the wearer's is used before *Same
/// Code*. The code counts in with a spring (not with Reduce Motion), a trackpad tap confirms it arrived, and
/// VoiceOver reads it digit by digit.
struct PairingCodeView: View {
    @Bindable var follow: FollowModel
    let wearer: String
    let check: LinkState.PairingCheck
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                if let code = check.code {
                    Text(
                        check.confirmed
                            ? L10n.t("Same code. Waiting for \(wearer) to allow this Mac.")
                            : L10n.t("Does \(wearer)'s phone show this code?")
                    )
                    .font(.headline)
                    Text(NearbyPairing.spaced(code))
                        .font(.system(size: 34, weight: .semibold, design: .monospaced))
                        .contentTransition(.numericText())
                        .accessibilityLabel(code.map(String.init).joined(separator: " "))
                        .frame(maxWidth: .infinity)
                    if check.confirmed {
                        Button(L10n.t("Cancel")) { Task { await follow.unfollow() } }
                    } else {
                        Text(
                            L10n.t(
                                "Click Same Code only if every digit matches: another phone nearby could pretend to be \(wearer)."
                            )
                        )
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        HStack {
                            Button(L10n.t("Different Code")) { Task { await follow.confirmNearby(same: false) } }
                            Spacer()
                            // no Return shortcut: comparing is the point, and Return belongs to the link field
                            Button(L10n.t("Same Code")) { Task { await follow.confirmNearby(same: true) } }
                                .buttonStyle(.borderedProminent)
                        }
                    }
                } else {
                    HStack {
                        ProgressView().controlSize(.small)
                        Text(L10n.t("Asking \(wearer)'s phone…"))
                        Spacer()
                        Button(L10n.t("Cancel")) { Task { await follow.unfollow() } }
                    }
                }
            }
            .padding(4)
            .animation(reduceMotion ? nil : .spring, value: check)
        }
        .accessibilityElement(children: .contain)
        .onChange(of: check.code) { _, code in
            if code != nil { NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now) }
        }
    }
}
