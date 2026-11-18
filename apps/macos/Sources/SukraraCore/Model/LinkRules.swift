// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// One person, one link (docs/44 Appendix G, the owner 2026-11-18; contracts/sources/link.json): this Mac either
/// follows a Sukrara (the link, the default) or reads one other source, never both. The rules every surface asks: what
/// picking a source asks, which questions leaving asks, the answers preselected, and the fence that keeps a kept
/// history from being replaced. Android `Link` passes the same cases (`LinkContractTest`; Mac `LinkContractTests`).
public enum LinkRules {
    /// Whether this Mac is linked: not at all, a follow that runs or waits (`active`: following, asked, joining), or
    /// one that ended (the wearer removed this Mac, declined, the invite was used or expired, the follow can't go on).
    public enum State: String, Sendable, Equatable {
        case none, active, ended
    }

    /// What picking a source asks first.
    public enum PickQuestion: String, Sendable, Equatable {
        /// *Keep following {name}?*: what the link already brings, whichever source was picked; *Keep Following* is the
        /// default, *Stop Following…* goes on to the leave questions.
        case keepFollowing
        /// Straight to the leave questions: nothing is left to keep following.
        case leave
    }

    /// What picking `picked` asks while `linked` (nil = nothing: the source is set at once). Another Sukrara picked
    /// asks nothing (it is the link, or a new join).
    public static func pickAsks(linked: State, picked: SourceKind) -> PickQuestion? {
        guard picked != .sukrara else { return nil }
        switch linked {
        case .none: return nil
        case .active: return .keepFollowing
        case .ended: return .leave
        }
    }

    /// The wearer's mirrored settings as this Mac holds them: never received, received at every schema default, or
    /// different from the defaults.
    public enum Theirs: String, Sendable, Equatable {
        case none, defaults, differ
    }

    /// A question leaving asks, in order.
    public enum LeaveQuestion: String, Sendable, Equatable {
        /// *Keep {name}'s settings* / *Sukrara's defaults*.
        case settings
        /// *Keep it* / *Delete it*: the history received through the link.
        case history
    }

    /// The settings this Mac keeps when leaving: its own as they are, the mirrored rows at the schema defaults, or the
    /// wearer's mirrored rows copied as its own.
    public enum SettingsResult: String, Sendable, Equatable {
        case own, defaults, theirs
    }

    /// What leaving asks: the settings question only when both answers give different settings (their settings in use
    /// and not the defaults; the owner: "skip it when both answers give the same settings"), the history question only
    /// when something was received. `result` = the settings kept when no settings question is asked.
    public static func leave(settingsFrom: SettingsFrom, theirs: Theirs, history: Bool) -> (
        questions: [LeaveQuestion], result: SettingsResult?
    ) {
        var questions: [LeaveQuestion] = []
        var result: SettingsResult?
        if settingsFrom == .custom {
            result = .own
        } else if theirs == .differ {
            questions.append(.settings)
        } else {
            result = .defaults
        }
        if history { questions.append(.history) }
        return (questions, result)
    }

    /// The answers preselected: nothing the person sees shifts, nothing is lost.
    public static let keepTheirSettings = true
    public static let keepHistory = true

    /// The next source continuing a kept history (`fence` = its last kept reading): a reading at or within the rank
    /// window of a kept one never replaces it (backfill only fills gaps); after the fence THE rank rule applies. nil =
    /// no kept history: the rank rule alone.
    public static func merge(
        stored: [ReadingRank.Sourced], incoming: [ReadingRank.Sourced], fence: Date?
    ) -> [ReadingRank.Sourced] {
        guard let fence else { return ReadingRank.merge(stored: stored, incoming: incoming) }
        let kept = stored.filter { $0.t <= fence }
        let new = incoming.filter { r in
            !kept.contains { abs($0.t.timeIntervalSince(r.t)) <= ReadingRank.window }
        }
        return ReadingRank.merge(stored: stored, incoming: new)
    }

    /// The history shown when `current` (an identity's readings, oldest first) continues `kept` (oldest first): the
    /// fence is the last kept reading, so every reading of `current` within the rank window of a kept one is the same
    /// measurement and the kept one stays; the rest fill the gaps and go on after it (`merge(fence:)` with every kept
    /// reading at or before the fence, in one pass over two sorted lists: a month each).
    public static func continuing(kept: [Reading], current: [Reading]) -> [Reading] {
        guard !kept.isEmpty else { return current }
        return LibreLinkUp.merge(live: kept, graph: current)
    }
}
