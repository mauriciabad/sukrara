// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Observation
import SukraraShare

/// Why following didn't start (`FollowerJoinError`, mapped by the runtime adapter).
public enum FollowFailure: Error, Equatable, Sendable {
    case invalidLink, notFound, expired, used, offline, fingerprintMismatch, alreadyFollowing, keychain, noQRCode
    /// A nearby join the person ended because the two devices showed different pairing codes.
    case codesDiffered

    /// The text the follow window shows; every one says what to do next.
    public var message: String {
        switch self {
        case .invalidLink: L10n.t("That isn't a Sukrara invite link. Copy the whole link from the phone and try again.")
        case .notFound: L10n.t("This invite wasn't found. Ask for a new invite on the phone.")
        case .expired: L10n.t("This invite has expired. Ask for a new invite on the phone.")
        case .used: L10n.t("Another device already used this invite. Ask for a new invite on the phone.")
        case .offline: L10n.t("Can't connect over the internet. Check the internet connection and try again.")
        case .fingerprintMismatch: L10n.t("This invite doesn't match the link. Ask for a new invite on the phone.")
        case .alreadyFollowing:
            L10n.t(
                "This Mac already follows someone. Stop following in Settings › General first, then open the link again."
            )
        case .keychain: L10n.t("Sukrara couldn't save the follow key in the Keychain. Try again.")
        case .noQRCode: L10n.t("No Sukrara invite QR code found in that image.")
        case .codesDiffered:
            L10n.t("The codes didn't match, so nothing was shared. Try again next to the wearer's phone.")
        }
    }
}

/// The link with the wearer as the windows show it (`LinkState`, mapped by the adapter).
public enum FollowStatus: Equatable, Sendable {
    case notFollowing
    case joining
    /// `pairing`: a nearby join's code comparison (nil for a link).
    case waiting(wearer: String?, pairing: LinkState.PairingCheck?)
    case following(wearer: String?, lastReading: Date?, canWrite: Bool)
    case declined, inviteUsed, revoked
    case broken(String)
}

/// A phone nearby offering pairing (`NearbyOffer`).
public struct NearbyPhone: Equatable, Sendable {
    public var wearer: String
    /// The wearer's generated picture from the offer (nil = the grey initial).
    public var avatar: Avatar?
    public init(wearer: String, avatar: Avatar? = nil) {
        self.wearer = wearer
        self.avatar = avatar
    }
}

/// What the follow UI needs from the follower runtime (`FollowerService`, behind an adapter).
public protocol FollowActions: AnyObject {
    /// Looks up the invite behind `link` (reads only): who is inviting, before anything is sent. nil = cancelled.
    func lookUp(link: String) async throws(FollowFailure) -> Invite?
    /// The person clicked *Follow* on `invite`: this Mac asks the wearer for access.
    func join(invite: Invite) async throws(FollowFailure)
    func joinNearby(name: String) async throws(FollowFailure)
    /// The pairing codes on both devices: the same (the wearer's key is used), or different (the join ends).
    func confirmNearby(same: Bool) async -> Bool
    /// How this Mac appears to the wearer (used by the next join; sent at once while following).
    func setProfile(name: String, avatar: Avatar?) async
    /// The name for the next join, keeping the picture the runtime has.
    func setName(_ name: String) async
    func cancelJoin() async
    func unfollow() async
}

/// The follow UI's state: the link, the text typed or pasted, the invite waiting for *Follow*, the error shown, and the
/// nearby offer.
@Observable
public final class FollowModel {
    public var status: FollowStatus
    public var linkText = ""
    /// An invite looked up and waiting for *Follow* / *Cancel*: who is inviting comes from it. Nothing was sent yet.
    public internal(set) var invite: Invite?
    /// `linkText` came from a link opened on this Mac (not typed or pasted): *Cancel* removes it again.
    @ObservationIgnored private var opened = false
    public private(set) var failure: FollowFailure?
    public var nearby: NearbyPhone?
    /// The name the wearer sees for this Mac (typed when following): the computer's name by default.
    public var macName: String
    /// How this Mac appears to the wearer while a follow exists (the How You Appear row), nil otherwise.
    public var me: Profile?
    /// The wearer's name and picture (Following, the nearby dialog), nil before any.
    public var wearer: Profile?
    /// Unfollow asks first ("the wearer has to invite again").
    public var confirmingUnfollow = false
    /// How the readings arrive while following ("Over the internet · Bluetooth: connected to Pixel"), nil otherwise.
    public var source: String?
    /// The newest problem with the link in words (Bluetooth not allowed, a damaged file moved aside), nil = none.
    public var problem: String?
    @ObservationIgnored private weak var actions: (any FollowActions)?
    /// Look for phones offering nearby pairing (on while a follow step is on screen and not following).
    @ObservationIgnored public var searchNearby: (Bool) -> Void = { _ in }

    public init(
        status: FollowStatus, actions: (any FollowActions)?, macName: String = Host.current().localizedName ?? "Mac"
    ) {
        self.status = status
        self.actions = actions
        self.macName = macName
    }

    /// The pasted, typed or opened link: looks the invite up and shows who is inviting with *Follow* / *Cancel*.
    /// Nothing goes to the relays until the text parses as an invite, and nothing is sent before *Follow*.
    public func lookUp() async {
        failure = nil
        invite = nil
        let link = linkText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard InviteLink.parse(link) != nil else {
            failure = .invalidLink
            return
        }
        // following or waiting: a new link changes nothing and the status stays what it is
        guard canFollow else {
            failure = .alreadyFollowing
            return
        }
        let before = status
        status = .joining
        do {
            let found = try await actions?.lookUp(link: link)
            if status == .joining { status = before }
            invite = found
        } catch {
            failure = error
            status = error == .alreadyFollowing ? before : .notFollowing
        }
    }

    /// A `sukrara:` or `https://sukrara.mauri.app/join#…` link opened on this Mac: fills the field and looks it up,
    /// so the confirmation shows; it never follows by itself.
    public func open(link: String) async {
        linkText = link
        opened = true
        await lookUp()
    }

    /// The QR image's text (QRImport) goes the same way as a pasted link.
    public func lookUp(qrText: String?) async {
        guard let qrText else {
            failure = .noQRCode
            return
        }
        linkText = qrText
        opened = false
        await lookUp()
    }

    /// *Follow* on the invite shown: this Mac asks the wearer, with the name typed for it.
    public func confirmInvite() async {
        guard let pending = invite else { return }
        failure = nil
        invite = nil
        opened = false
        let before = status
        status = .joining
        await actions?.setName(macName)
        do {
            try await actions?.join(invite: pending)
        } catch {
            failure = error
            status = error == .alreadyFollowing ? before : .notFollowing
        }
    }

    /// *Cancel* (or Escape) on the invite shown: nothing was sent. A link opened on this Mac leaves the field too.
    public func cancelInvite() {
        guard invite != nil else { return }
        invite = nil
        if opened { linkText = "" }
        opened = false
    }

    public func askNearby() async {
        failure = nil
        guard let phone = nearby else { return }
        status = .waiting(wearer: phone.wearer, pairing: .init(code: nil, confirmed: false))
        do {
            try await actions?.joinNearby(name: macName)
        } catch {
            failure = error
            status = .notFollowing
        }
    }

    /// *Same Code* lets the wearer's key in; *Different Code* ends the join and says why.
    public func confirmNearby(same: Bool) async {
        failure = nil
        guard await actions?.confirmNearby(same: same) == true, !same else { return }
        failure = .codesDiffered
        status = .notFollowing
    }

    /// Saves How You Appear: the wearer sees it as soon as it reaches them.
    public func saveProfile(name: String, avatar: Avatar?) async {
        me = Profile(name: Profile.normalisedName(typed: name, previous: me?.name ?? macName), avatar: avatar)
        await actions?.setProfile(name: name, avatar: avatar)
    }

    public func cancel() async {
        await actions?.cancelJoin()
        status = .notFollowing
        invite = nil
    }

    public func unfollow() async {
        confirmingUnfollow = false
        await actions?.unfollow()
        status = .notFollowing
        linkText = ""
    }

    public func clearFailure() { failure = nil }

    /// Nobody followed and nothing on its way: the states where a nearby phone may be offered.
    public var canFollow: Bool {
        switch status {
        case .notFollowing, .declined, .inviteUsed, .revoked, .broken: true
        case .joining, .waiting, .following: false
        }
    }
}
