// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraShare
import SwiftUI

/// A link join waiting for the wearer's Allow (PROTOCOL-v2 "Invite link / QR"): "Waiting for Marta to allow you", the
/// match code as one quiet line (`MatchCodeLine`) and *Cancel*, which withdraws the request (the follower key goes,
/// nothing more is sent). The wearer's Allow, Deny or a second device on the link moves it on by itself.
struct WaitingForWearer: View {
    @Bindable var follow: FollowModel
    let wearer: String

    var body: some View {
        GroupBox {
            HStack(spacing: 12) {
                AvatarView(avatar: follow.wearer?.avatar, name: wearer, size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        ProgressView().controlSize(.small)
                        Text(L10n.t("Waiting for \(wearer) to allow you")).font(.headline)
                    }
                    if let code = follow.matchCode { MatchCodeLine(wearer: wearer, code: code) }
                }
                Spacer(minLength: 0)
                Button(L10n.t("Cancel")) { Task { await follow.unfollow() } }
            }
            .padding(4)
        }
        .accessibilityElement(children: .contain)
    }
}

/// The match code a link follower shows while it waits, in Android's words: "If Marta asks for a code: 4821". The
/// wearer's phone asks for it only when another device also tried the same link (otherwise it shows no code), so it
/// is a secondary line, not a headline. VoiceOver reads the code digit by digit.
struct MatchCodeLine: View {
    let wearer: String
    let code: String

    var body: some View {
        let spaced = code.map(String.init).joined(separator: " ")
        Text(L10n.t("If \(wearer) asks for a code: \(code)"))
            .monospacedDigit()
            .foregroundStyle(.secondary)
            .textSelection(.enabled)
            .accessibilityLabel(L10n.t("If \(wearer) asks for a code: \(spaced)"))
    }
}

/// The wearer allowed this Mac: their picture with a check, "You follow Marta" and where the glucose shows now. It
/// replaces the waiting row by itself as soon as the key arrives (the follow step's spring, a fade with Reduce Motion,
/// and a trackpad tap); setup's Continue goes on.
struct FollowingNow: View {
    @Bindable var follow: FollowModel
    let wearer: String

    var body: some View {
        GroupBox {
            HStack(spacing: 12) {
                AvatarView(avatar: follow.wearer?.avatar, name: wearer, size: 44)
                    .overlay(alignment: .bottomTrailing) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(.white, .green)
                            .offset(x: 3, y: 3)
                            .accessibilityHidden(true)
                    }
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.t("You follow \(wearer)")).font(.headline)
                    Text(L10n.t("Their glucose shows in the menu bar now."))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(4)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A follow that ended, said in the follow step with its way on (paste a new link here), in Android's join-step words.
/// Nothing for any other state.
struct FollowEnded: View {
    let status: FollowStatus
    let wearer: String

    var body: some View {
        if let text = Self.text(status, wearer: wearer) {
            // the colour only on the symbol: the words keep full contrast
            Label {
                Text(text)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    static func text(_ status: FollowStatus, wearer: String) -> String? {
        switch status {
        case .declined: L10n.t("\(wearer) didn't allow you. Ask them for a new link and paste it here.")
        case .inviteUsed:
            L10n.t("Another device used this invite from \(wearer) first. Ask them for a new link and paste it here.")
        case .revoked: L10n.t("\(wearer) stopped sharing with you. Paste a new link to follow again.")
        default: nil
        }
    }
}
