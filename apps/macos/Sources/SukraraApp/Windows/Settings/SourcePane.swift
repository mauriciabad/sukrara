// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import AppKit
import Observation
import SukraraCore
import SwiftUI

/// Where the glucose comes from (schema `source.kind`; docs/44), as setup's two steps and Settings › Source edit it. The
/// Mac doesn't ask whose glucose (the owner, 2026-11-18): the words speak of the glucose, names come from the source.
@Observable
public final class SourceChoiceModel {
    @ObservationIgnored let store: any SettingsStoring
    /// Whether this Mac follows someone now (the answer an unanswered Mac runs on).
    @ObservationIgnored var following: () -> Bool = { false }
    /// The answer changed: the app model reads it again.
    @ObservationIgnored var changed: () -> Void = {}
    private var revision = 0

    init(store: any SettingsStoring) {
        self.store = store
    }

    /// The stored answer (nil = not answered yet).
    var stored: SourceSpec? {
        _ = revision
        return SourceSpec.stored(in: store)
    }

    /// What the Mac runs on: the answer, else following → their Sukrara.
    var running: SourceSpec? { stored ?? SourceSpec.running(in: store, following: following()) }

    func choose(_ spec: SourceSpec) {
        // another Sukrara picked while nothing is followed starts a new link with *Their settings* (leaving one made
        // the settings this Mac's own); an unanswered Mac that follows keeps its answer
        if spec.stream, stored?.kind != .sukrara, !following() {
            store.set(SettingsFrom.wearer.rawValue, for: MacSettings.settingsFrom)
        }
        spec.store(in: store)
        revision += 1
        NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .now)
        changed()
    }

    /// No source (*Stop following* alone): the panel's *Pick a Source…*.
    func clear() {
        SourceSpec.clear(in: store)
        revision += 1
        changed()
    }

    static func title(_ kind: SourceKind) -> String {
        switch kind {
        case .sukrara: L10n.t("Sukrara on a phone")
        case .lanJuggluco: L10n.t("Juggluco on this network")
        case .nightscout: "Nightscout"
        case .libreLinkUp: "LibreLinkUp"
        }
    }

    /// How the picker groups the sources: by what the person feels (docs/44 "Setup").
    static func groupName(_ g: SourceGroup) -> String {
        switch g {
        case .offline: L10n.t("On this Mac · works offline")
        case .network: L10n.t("On this network · works offline")
        case .online: L10n.t("Online · needs internet")
        case .sukrara: L10n.t("From another Sukrara · internet or Bluetooth nearby")
        }
    }

    static func means(_ kind: SourceKind) -> String {
        switch kind {
        case .sukrara:
            L10n.t("The phone that reads the sensor shares it here, with alerts, treatments and settings")
        case .lanJuggluco:
            L10n.t("Juggluco on a phone on this Wi-Fi reads the sensor; Sukrara reads Juggluco")
        case .nightscout:
            L10n.t("Glucose and treatments from a site · about 2 min late · nothing arrives while the site is down")
        case .libreLinkUp:
            L10n.t(
                "The Libre app shares with a LibreLinkUp account · about 1 min late · nothing arrives while Abbott's LibreView is down, maintenance included · may stop working any day"
            )
        }
    }
}

/// A big choice card's pick (Whose glucose, *Do you have Sukrara on your phone?*; Android's `PersonChoiceCard`): only
/// the card being picked lifts on a spring; the one left goes back to plain at once-ish, without the pick's spring or
/// lift (it moved both: one `.animation` over the pair sprang the card being left too). Reduce Motion: no lift, no
/// animation.
struct ChoiceCardMotion: ViewModifier {
    let on: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .scaleEffect(on && !reduceMotion ? 1.01 : 1)
            .animation(Self.animation(picked: on, reduceMotion: reduceMotion), value: on)
    }

    /// The card being picked: the spring; the card being left: a short ease without bounce; Reduce Motion: none.
    static func animation(picked: Bool, reduceMotion: Bool) -> Animation? {
        if reduceMotion { return nil }
        return picked ? .spring(duration: 0.35, bounce: 0.25) : .easeOut(duration: 0.15)
    }
}

/// The sources, one radio choice each with what it brings, grouped by what the person feels (*On this network · works
/// offline*, *Online · needs internet*, *From another Sukrara · internet or Bluetooth nearby*): setup's source step
/// (without another Sukrara when the Mac reads it itself) and Settings › Source.
struct SourceKindChoice: View {
    @Binding var selected: SourceKind?
    /// The sources offered (nil = every one).
    var only: Set<SourceKind>? = nil

    var body: some View {
        let kinds = SourceKind.allCases.filter { only?.contains($0) ?? true }
        VStack(alignment: .leading, spacing: 10) {
            ForEach(SourceGroup.allCases.filter { g in kinds.contains { $0.group == g } }, id: \.self) { group in
                VStack(alignment: .leading, spacing: 4) {
                    Text(SourceChoiceModel.groupName(group).uppercased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .accessibilityAddTraits(.isHeader)
                    Picker(selection: $selected) {
                        ForEach(kinds.filter { $0.group == group }, id: \.self) { kind in
                            // the app's or service's own icon, as Android's source rows (SourceIcon)
                            HStack(spacing: 10) {
                                SourceIcon(kind: kind)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(SourceChoiceModel.title(kind))
                                    Text(SourceChoiceModel.means(kind)).font(.caption).foregroundStyle(.secondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            .padding(.vertical, 3)
                            .tag(Optional(kind))
                        }
                    } label: {
                        EmptyView()
                    }
                    .pickerStyle(.radioGroup)
                    .labelsHidden()
                }
            }
        }
    }
}

/// Settings › Source: where the glucose comes from (radio rows), then that source's own rows: the
/// person this Mac follows (Follow…, the waiting code, Stop following…, Reconnect Now), Juggluco on this network
/// (its state, address and secret), a Nightscout site (how it's going, site, token, treatments both ways) or LibreLinkUp
/// (how it's going, account, whose glucose, sign out), then *Sensor*: the start and length where the source doesn't
/// say them (`SensorLifeRows`). A change applies at once (nothing to delete on the Mac: the readings are each
/// source's own store); Back is just another pick.
struct SourcePane: View {
    @Bindable var model: SettingsModel
    var openFollow: () -> Void
    var reconnect: (() -> Void)?
    /// An unanswered Mac's *Answer…*: setup's two steps, preselected (nil in renders).
    var answer: (() -> Void)? = nil

    var body: some View {
        let choice = model.source
        let running = choice.running
        Form {
            // PROTOCOL-v2 "Leave" still on its way after this Mac left a link for another source (or none): said
            // until it goes (with the link the follow's rows say it)
            if running?.stream != true, let untold = model.follow.untoldLeave {
                Section {
                    Label(FollowingRows.untoldText(untold), systemImage: "paperplane")
                        .foregroundStyle(.secondary)
                }
            }
            if running == nil {
                Section {
                    HStack(alignment: .firstTextBaseline) {
                        Label(
                            L10n.t("No source: Sukrara reads nothing until you pick where the glucose comes from."),
                            systemImage: "info.circle")
                        Spacer()
                        if let answer { Button(L10n.t("Answer…"), action: answer) }
                    }
                }
                Section {
                    SourceKindChoice(
                        selected: Binding(get: { nil }, set: { $0.map { model.leave.pick($0, from: .settings) } }))
                } header: {
                    Text(L10n.t("Where it comes from"))
                }
            }
            if let running {
                Section {
                    // another source picked while following asks first (one person, one link)
                    SourceKindChoice(
                        selected: Binding(
                            get: { running.kind }, set: { $0.map { model.leave.pick($0, from: .settings) } }))
                } header: {
                    Text(L10n.t("Where it comes from"))
                }
                switch running.kind {
                case .sukrara:
                    Section {
                        FollowingRows(
                            follow: model.follow, openFollow: openFollow,
                            stopFollowing: { model.leave.stopFollowing(from: .settings) })
                    }
                    if case .following = model.follow.status, let reconnect {
                        Section {
                            HStack {
                                Spacer()
                                Button(L10n.t("Reconnect Now"), action: reconnect)
                                    .help(L10n.t("Reconnect over the internet and to the phone over Bluetooth now"))
                            }
                        }
                    }
                case .lanJuggluco:
                    Section {
                        JugglucoRows(model: model.juggluco)
                    } header: {
                        Text(verbatim: "Juggluco")
                    } footer: {
                        Text(
                            L10n.t(
                                "In Juggluco: Settings › Web server, with Local only off. Plain http on your home network only: readings travel unencrypted between the phone and this Mac."
                            )
                        )
                        .formFooter()
                    }
                    .onAppear(perform: model.juggluco.load)
                    .onDisappear {
                        model.juggluco.saveAddress()
                        model.juggluco.saveSecret()
                    }
                case .nightscout:
                    NightscoutRows(model: model.nightscout)
                        .onAppear(perform: model.nightscout.load)
                case .libreLinkUp:
                    LibreLinkUpRows(model: model.libreLinkUp)
                }
                // the sensor's start and length where the source doesn't say them (docs/44 decision 10)
                if running.kind.wear != nil {
                    SensorLifeRows(model: model.sensorLife, kind: running.kind)
                }
            }
        }
        .formStyle(.grouped)
        .modifier(LeaveLinkPresenter(model: model.leave, origin: .settings))
    }
}
