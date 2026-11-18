// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Observation
import SukraraCore
import SwiftUI

/// Settings › Source's *Also saved on this Mac* (docs/44 Appendix G finding 12; Android *Also saved on this phone*):
/// what this Mac holds for sources it doesn't read now, and the histories of identities it doesn't show, each with its
/// removal, without reconnecting anything. Hidden when there is none.
@Observable
public final class AlsoSavedModel {
    /// One thing this Mac holds that isn't in use.
    enum Item: Equatable, Identifiable {
        /// LibreLinkUp's account (its email): *Sign Out…*.
        case libreLinkUp(email: String)
        /// A Nightscout site (its host) and its token: *Forget…*.
        case nightscout(host: String)
        /// Juggluco's address (or "" with only the API secret): *Forget…*.
        case juggluco(address: String)
        /// A kept history: *Delete…*.
        case kept(KeptHistory.Info)

        var id: String {
            switch self {
            case .libreLinkUp: "libreLinkUp"
            case .nightscout: "nightscout"
            case .juggluco: "juggluco"
            case .kept(let info): "kept-\(info.id)"
            }
        }
    }

    @ObservationIgnored weak var settings: SettingsModel?
    /// The kept histories not shown now (the app model's, observed: a leave or another identity read lists them
    /// again), newest first.
    @ObservationIgnored var keptNotShown: () -> [KeptHistory.Info] = { [] }
    @ObservationIgnored var deleteKept: (String) -> Void = { _ in }
    /// The Nightscout source keeps the forgotten site's readings and log apart.
    @ObservationIgnored var forgetNightscoutSource: () async -> Void = {}
    /// The removal being confirmed.
    var confirming: Item?
    /// A Nightscout token and Juggluco's API secret are in the Keychain (read by `load`, not on every draw).
    private var nightscoutSecret = false
    private var jugglucoSecret = false

    /// Reads the Keychain flags again (the pane appears, the source changes, a removal).
    func load() {
        guard let settings else { return }
        nightscoutSecret = !NightscoutModel.secret(settings.nightscout.secrets).isEmpty
        jugglucoSecret = !JugglucoModel.secret(settings.juggluco.secrets).isEmpty
    }

    /// What is listed: the accounts of the sources not read now, then the kept histories.
    var items: [Item] {
        guard let settings else { return [] }
        let running = settings.source.running?.kind
        var out: [Item] = []
        let email = settings.libreLinkUp.snapshot().email
        if running != .libreLinkUp, !email.isEmpty { out.append(.libreLinkUp(email: email)) }
        let site = settings.nightscout.site
        if running != .nightscout, !site.isEmpty || nightscoutSecret {
            out.append(.nightscout(host: NightscoutSite.parse(site).host))
        }
        let address = settings.store.value(MacSettings.jugglucoAddress)
        if running != .lanJuggluco, !address.isEmpty || jugglucoSecret {
            out.append(.juggluco(address: address))
        }
        return out + keptNotShown().map(Item.kept)
    }

    /// The confirmed removal.
    func remove(_ item: Item) async {
        confirming = nil
        guard let settings else { return }
        switch item {
        case .libreLinkUp: settings.libreLinkUp.signOut()
        case .nightscout:
            settings.nightscout.forget()
            await forgetNightscoutSource()
        case .juggluco: settings.juggluco.forget()
        case .kept(let info): deleteKept(info.id)
        }
        NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .now)
        load()
    }

    /// A kept history's name: the person's as the source gave it, else the source's.
    static func name(_ info: KeptHistory.Info) -> String {
        info.name ?? info.kind.map(SourceChoiceModel.title) ?? L10n.t("Kept history")
    }

    /// Its second line: where it came from and when it was kept.
    static func detail(_ info: KeptHistory.Info) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(info.keptAt)).formatted(date: .abbreviated, time: .omitted)
        guard let kind = info.kind else { return L10n.t("Kept \(date)") }
        return L10n.t("\(SourceChoiceModel.title(kind)) · kept \(date)")
    }

    /// The confirmation's title, message and button (Sign Out is no loss: not destructive; Forget and Delete are).
    static func confirmation(_ item: Item) -> (title: String, message: String, button: String, destructive: Bool) {
        switch item {
        case .libreLinkUp:
            (
                L10n.t("Sign out of LibreLinkUp?"),
                L10n.t(
                    "Sukrara forgets this account's sign-in on this Mac. The account and whom it follows stay as they are in LibreLinkUp."
                ), L10n.t("Sign Out"), false
            )
        case .nightscout:
            (
                L10n.t("Forget this Nightscout site?"),
                L10n.t(
                    "Sukrara forgets the site's address and token on this Mac. The site itself stays as it is, and the glucose and treatments it brought stay here as a kept history."
                ), L10n.t("Forget"), true
            )
        case .juggluco:
            (
                L10n.t("Forget Juggluco on this network?"),
                L10n.t("Sukrara forgets the phone's address and Juggluco's API secret on this Mac."),
                L10n.t("Forget"), true
            )
        case .kept(let info):
            (
                L10n.t("Delete \(name(info))'s history?"),
                L10n.t("The glucose and treatments kept on this Mac are deleted. This can't be undone."),
                L10n.t("Delete"), true
            )
        }
    }
}

/// The *Also saved on this Mac* group: one row per item, its name with the source's icon, what it is, and its removal
/// as a button with an ellipsis (it asks first), the same pattern as *Sign Out of LibreLinkUp…*.
struct AlsoSavedRows: View {
    @Bindable var model: AlsoSavedModel

    var body: some View {
        let items = model.items
        if !items.isEmpty {
            Section {
                ForEach(items) { item in row(item) }
            } header: {
                Text(L10n.t("Also saved on this Mac"))
            } footer: {
                Text(L10n.t("Kept for a source this Mac doesn't read now. Removing one changes nothing in use."))
                    .formFooter()
            }
            .confirmationDialog(
                model.confirming.map { AlsoSavedModel.confirmation($0).title } ?? "",
                isPresented: Binding(get: { model.confirming != nil }, set: { if !$0 { model.confirming = nil } }),
                presenting: model.confirming
            ) { item in
                let c = AlsoSavedModel.confirmation(item)
                Button(c.button, role: c.destructive ? .destructive : nil) { Task { await model.remove(item) } }
                Button(L10n.t("Cancel"), role: .cancel) { model.confirming = nil }
            } message: { item in
                Text(AlsoSavedModel.confirmation(item).message)
            }
        }
    }

    @ViewBuilder private func row(_ item: AlsoSavedModel.Item) -> some View {
        switch item {
        case .libreLinkUp(let email):
            line(email, button: L10n.t("Sign Out…"), item: item) {
                ServiceLabel("LibreLinkUp", service: .libreLinkUp)
            }
        case .nightscout(let host):
            line(host, button: L10n.t("Forget…"), item: item) {
                ServiceLabel("Nightscout", service: AppService.of(.nightscout))
            }
        case .juggluco(let address):
            line(address.isEmpty ? L10n.t("API secret") : address, button: L10n.t("Forget…"), item: item) {
                ServiceLabel(SourceChoiceModel.title(.lanJuggluco), service: AppService.of(.lanJuggluco))
            }
        case .kept(let info):
            LabeledContent {
                Button(L10n.t("Delete…")) { model.confirming = item }
            } label: {
                HStack(spacing: 10) {
                    if let kind = info.kind { SourceIcon(kind: kind) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(AlsoSavedModel.name(info))
                        Text(AlsoSavedModel.detail(info)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func line(
        _ detail: String, button: String, item: AlsoSavedModel.Item, @ViewBuilder label: () -> some View
    ) -> some View {
        LabeledContent {
            HStack {
                Text(detail).foregroundStyle(.secondary).textSelection(.enabled)
                Button(button) { model.confirming = item }
            }
        } label: {
            label()
        }
    }
}
