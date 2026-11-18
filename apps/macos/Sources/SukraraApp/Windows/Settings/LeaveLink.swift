// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Observation
import SukraraCore
import SukraraGraph
import SwiftUI

/// One person, one link (docs/44 Appendix G; contracts/sources/link.json): picking another source while this Mac
/// follows a Sukrara asks *Keep following {name}?* first (an alert: two answers, nothing to fill in), and leaving asks
/// only the questions whose answers differ (a sheet: a short task with choices, HIG "Sheets"), then applies them in
/// the SPEC's order: the settings, the history kept or deleted, the link left through `AppModel.leaveLink`, the new
/// source (or none: the panel's *Pick a Source…*). Settings › Source and setup's source step share it.
@Observable
public final class LeaveLinkModel {
    /// Where the question was asked: only that window shows it.
    public enum Origin: Sendable { case settings, setup }

    /// The link now (`AppModel.linkState`).
    @ObservationIgnored var linkState: () -> LinkRules.State = { .none }
    /// Whether this Mac holds readings or treatments received through the link, and its oldest reading.
    @ObservationIgnored var history: () -> (held: Bool, since: Date?) = { (false, nil) }
    /// The wearer's name (nil = none yet).
    @ObservationIgnored var name: () -> String? = { nil }
    /// Leaves the link (`AppModel.leaveLink`).
    @ObservationIgnored var leave: (_ keepHistory: Bool) async -> Void = { _ in }
    /// Left with a source picked: setup goes on to its step.
    @ObservationIgnored var left: (_ picked: SourceKind?, _ origin: Origin) -> Void = { _, _ in }
    @ObservationIgnored weak var settings: SettingsModel?

    /// The source picked that asked (nil = *Stop following* alone).
    private(set) var picked: SourceKind?
    /// The question is about a follow found behind the source in use (a Mac updated from before one person, one
    /// link): *Keep Following* makes the link the source again.
    private(set) var behind = false
    private(set) var origin: Origin = .settings
    var askingKeepFollowing = false
    var askingLeave = false
    /// The answers, preselected so nothing the person sees shifts and nothing is lost.
    var keepSettings = LinkRules.keepTheirSettings
    var keepHistory = LinkRules.keepHistory
    /// The leave is under way (the sheet's buttons wait).
    private(set) var leaving = false
    /// What leaving asks now and the settings kept when it doesn't ask (`Link.leave`).
    private(set) var questions: [LinkRules.LeaveQuestion] = []
    private(set) var result: LinkRules.SettingsResult?
    private(set) var since: Date?

    /// Picking `kind` (Settings › Source, setup's source step): applied at once when nothing is asked (true), else
    /// the question that comes first.
    @discardableResult
    func pick(_ kind: SourceKind, from origin: Origin) -> Bool {
        switch LinkRules.pickAsks(linked: linkState(), picked: kind) {
        case nil:
            settings?.source.choose(SourceSpec(kind: kind))
            return true
        case .keepFollowing:
            picked = kind
            self.origin = origin
            askingKeepFollowing = true
        case .leave:
            picked = kind
            self.origin = origin
            openLeave()
        }
        return false
    }

    /// *Stop following…* (Settings › Source): the leave questions, then no source.
    func stopFollowing(from origin: Origin = .settings) {
        picked = nil
        self.origin = origin
        openLeave()
    }

    /// A live follow found behind `inUse` at launch (`AppModel.onFollowBehind`): the same question as picking `inUse`
    /// while following, asked in Settings › Source. *Keep Following* reads the link again; *Stop Following…* the leave
    /// questions, `inUse` staying the source.
    func askBehind(_ inUse: SourceKind) {
        picked = inUse
        origin = .settings
        behind = true
        askingKeepFollowing = true
    }

    /// *Keep Following*: nothing changes (a follow found behind the source in use becomes the source again).
    func keepFollowing() {
        askingKeepFollowing = false
        picked = nil
        if behind { settings?.source.choose(.sukrara) }
        behind = false
    }

    /// *Stop Following…* in the question: the leave questions.
    func stopInstead() {
        askingKeepFollowing = false
        openLeave()
    }

    private func openLeave() {
        let held = history()
        let asked = LinkRules.leave(
            settingsFrom: settings?.store.storedSettingsFrom ?? .wearer, theirs: settings?.theirs ?? .none,
            history: held.held)
        questions = asked.questions
        result = asked.result
        since = held.since
        keepSettings = LinkRules.keepTheirSettings
        keepHistory = LinkRules.keepHistory
        askingLeave = true
    }

    func cancel() {
        askingLeave = false
        picked = nil
        // a follow found behind the source in use: *Cancel* in the leave questions keeps following, as the question's
        // default does (it stays asked until answered)
        if behind { settings?.source.choose(.sukrara) }
        behind = false
    }

    /// *Stop Following*: the settings, the history, the link left, the new source (SPEC "What leaving does").
    @MainActor func confirm() async {
        guard !leaving, let settings else { return }
        leaving = true
        let kept =
            questions.contains(.settings)
            ? (keepSettings ? LinkRules.SettingsResult.theirs : .defaults) : (result ?? .own)
        settings.keepAfterLeaving(kept)
        await leave(questions.contains(.history) && keepHistory)
        let picked = picked
        if let picked { settings.source.choose(SourceSpec(kind: picked)) } else { settings.source.clear() }
        NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
        leaving = false
        askingLeave = false
        self.picked = nil
        behind = false
        left(picked, origin)
    }

    // MARK: words

    var whoName: String? { name() }

    /// The question's title.
    var keepTitle: String {
        whoName.map { L10n.t("Keep following \($0)?") } ?? L10n.t("Keep following this Sukrara?")
    }

    /// What the link already brings, whichever source was picked; the picked one named only in what it would cost.
    var keepText: String {
        let source = picked.map(Self.sourceName) ?? ""
        guard let who = whoName else {
            return L10n.t(
                "This Sukrara already sends this Mac the glucose it reads, from whichever source it uses, with treatments, pen doses, alerts and settings. Reading \(source) here instead means this Mac stops following it."
            )
        }
        return L10n.t(
            "\(who)'s Sukrara already sends this Mac the glucose it reads, from whichever source it uses, with treatments, pen doses, alerts and settings. Reading \(source) here instead means this Mac stops following it."
        )
    }

    /// A source's own name as the picker shows it, inside a sentence.
    static func sourceName(_ kind: SourceKind) -> String {
        switch kind {
        case .lanJuggluco: "Juggluco"
        case .nightscout: "Nightscout"
        case .libreLinkUp: "LibreLinkUp"
        case .sukrara: "Sukrara"
        }
    }

    var leaveTitle: String {
        whoName.map { L10n.t("Stop following \($0)?") } ?? L10n.t("Stop following this Sukrara?")
    }

    /// What the wearer's phone learns and the way back.
    var toldText: String {
        let again = L10n.t("To follow again you need a new invite link, or their phone nearby.")
        guard linkState() == .active else { return again }
        guard let who = whoName else { return L10n.t("Their phone is told.") + " " + again }
        return L10n.t("\(who)'s phone is told.") + " " + again
    }

    var keepSettingsTitle: String {
        whoName.map { L10n.t("Keep \($0)'s settings") } ?? L10n.t("Keep their settings")
    }

    /// What *Delete it* deletes.
    var deleteText: String {
        guard let since else { return L10n.t("Deletes the treatments received.") }
        let date = since.formatted(date: .abbreviated, time: .omitted)
        return L10n.t("Deletes the glucose and treatments received since \(date).")
    }
}

extension SettingsModel {
    /// The wearer's mirrored settings as this Mac holds them (`Link.leave`'s `theirs`).
    var theirs: LinkRules.Theirs {
        guard let wearer else { return .none }
        let base = alerts.settings.withMirroredDefaults()
        let same =
            GraphConfig.load(from: WearerOnly(), wearer: wearer) == GraphConfig() && base.mirroring(wearer) == base
        return same ? .defaults : .differ
    }

    /// The settings this Mac keeps when it leaves a link: the wearer's copied as its own, Sukrara's defaults, or its
    /// own as they are; either way *This Mac's own* from now (a future link starts again with *Their settings*:
    /// `SourceSpec.store`).
    func keepAfterLeaving(_ kept: LinkRules.SettingsResult) {
        switch kept {
        case .theirs where wearer != nil: copyWearer()
        case .theirs, .defaults: useDefaultsAsOwn()
        case .own: settingsFrom = .custom
        }
    }
}

/// The question and the sheet, shown in the window the pick came from.
struct LeaveLinkPresenter: ViewModifier {
    @Bindable var model: LeaveLinkModel
    let origin: LeaveLinkModel.Origin

    func body(content: Content) -> some View {
        content
            .alert(
                model.keepTitle,
                isPresented: Binding(
                    get: { model.askingKeepFollowing && model.origin == origin },
                    set: { if !$0 && model.askingKeepFollowing { model.keepFollowing() } })
            ) {
                // the safe answer: Return and Escape both keep the link
                Button(L10n.t("Keep Following"), role: .cancel) { model.keepFollowing() }
                    .keyboardShortcut(.defaultAction)
                Button(L10n.t("Stop Following…"), role: .destructive) { model.stopInstead() }
            } message: {
                Text(model.keepText)
            }
            .sheet(
                isPresented: Binding(
                    get: { model.askingLeave && model.origin == origin },
                    set: { if !$0 && !model.leaving { model.cancel() } })
            ) {
                LeaveLinkSheet(model: model)
            }
    }
}

/// *Stop following {name}?*: what the wearer learns and the way back, then only the questions `Link.leave` asks, each a
/// radio group with what each answer keeps, the safe answers preselected; *Cancel* / *Stop Following*.
struct LeaveLinkSheet: View {
    @Bindable var model: LeaveLinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(model.leaveTitle).font(.title2.bold())
            Text(model.toldText).fixedSize(horizontal: false, vertical: true)
            if model.questions.contains(.settings) {
                question(
                    L10n.t("Settings on this Mac"), selection: $model.keepSettings,
                    (
                        true, model.keepSettingsTitle,
                        L10n.t("Alerts, ranges and the graph stay as they are now, as this Mac's own.")
                    ),
                    (
                        false, L10n.t("Sukrara's defaults"),
                        L10n.t("Alerts, ranges and the graph go back to how Sukrara starts.")
                    ))
            }
            if model.questions.contains(.history) {
                question(
                    L10n.t("The history this Mac received"), selection: $model.keepHistory,
                    (
                        true, L10n.t("Keep it"),
                        L10n.t("The next source continues it: for when it reads the same person.")
                    ),
                    (false, L10n.t("Delete it"), model.deleteText))
            }
            // destructive: never the default button (HIG "Alerts", "Sheets"); Escape cancels
            DialogButtons(
                L10n.t("Stop Following"), enabled: !model.leaving, destructive: true, cancel: { model.cancel() },
                confirm: { Task { await model.confirm() } })
        }
        .padding(20)
        .frame(width: 460, alignment: .leading)
    }

    private func question(
        _ header: String, selection: Binding<Bool>, _ a: (Bool, String, String), _ b: (Bool, String, String)
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(header).font(.headline).accessibilityAddTraits(.isHeader)
            Picker(selection: selection) {
                ForEach([a, b], id: \.0) { option in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(option.1)
                        Text(option.2).font(.caption).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.vertical, 2)
                    .tag(option.0)
                }
            } label: {
                Text(header)
            }
            .pickerStyle(.radioGroup)
            .labelsHidden()
        }
    }
}
