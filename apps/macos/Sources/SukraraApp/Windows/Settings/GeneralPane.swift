// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import SukraraCore
import SukraraShare
import SwiftUI

/// General: Settings from (with what's in use and Copy the wearer's settings…), the read-aloud shortcut, startup (open
/// at login), Software Update (the version and which one, the updater in the GitHub version), About Sukrara and Quit.
/// The unit and the trend arrow are in Readings, Juggluco in Following. The medical-device reminder, the privacy policy
/// and Sukrara on other devices are in the About window (`AboutView`).
struct GeneralPane: View {
    @Bindable var model: SettingsModel
    /// Open at login (nil when this run may not touch login items: dev, fixture and snapshot runs).
    var loginItem: LoginItem?
    /// The updater (Sparkle); nil when this run has none: the App Store version (the store updates it) and offline dev
    /// runs.
    var updates: Updates?
    var openFollow: () -> Void
    /// Opens the About window (nil when this run has none: renders).
    var openAbout: (() -> Void)? = nil

    var body: some View {
        Form {
            Section {
                SettingsFromPicker(model: model)
            } header: {
                Text(L10n.t("Settings"))
            } footer: {
                Text(
                    L10n.t(
                        "Their settings decide the ranges, the trend arrow, the time shown, the rings, the lines, the last treatment, the sensor countdown and when to alert; This Mac's own lets you set them here. Sounds, the no-data alert, the notification's buttons and each place's own time shown and sensor time are always this Mac's."
                    )
                )
                .formFooter()
            }
            Section {
                Toggle(isOn: $model.readAloudShortcut) {
                    Text(L10n.t("Read glucose aloud with \(GlobalShortcut.symbols)"))
                    Text(
                        L10n.t(
                            "From any app: the value, its trend and how old it is, in VoiceOver's voice when VoiceOver is on. Turn it off if another app uses these keys. Read Glucose Aloud is also in the menu bar value's right-click menu."
                        ))
                }
            } header: {
                Text(L10n.t("Read aloud"))
            }
            Section(L10n.t("Startup")) {
                OpenAtLoginRows(loginItem: loginItem)
            }
            Section {
                LabeledContent(L10n.t("Version")) {
                    HStack {
                        Text(Self.versionText(model.version, model.distribution, model.appBuild)).textSelection(
                            .enabled)
                        if let updates {
                            Button(L10n.t("Check for Updates…"), action: updates.checkNow)
                                .disabled(!updates.canCheck)
                        }
                    }
                }
                if let updates { UpdateRows(updates: updates) }
            } header: {
                if model.distribution.hasOwnUpdater { Text(L10n.t("Software Update")) }
            }
            Section {
                HStack {
                    if let openAbout {
                        Button(L10n.t("About Sukrara"), action: openAbout)
                            .help(L10n.t("The version, the privacy policy, the website and Sukrara on other devices"))
                    }
                    Spacer()
                    Button(L10n.t("Quit Sukrara")) { NSApp.terminate(nil) }
                }
            }
        }
        .formStyle(.grouped)
    }
}

extension GeneralPane {

    /// "0.13.0 · GitHub version" / "0.13.0 · App Store version" (Android: "· Google Play version") for a release; any
    /// other build says it is a development build and of what ("Development build · 0.14.5 + 23 commits · d744fd74 ·
    /// 14 Nov"), so nobody takes it for the release.
    static func versionText(
        _ version: String, _ distribution: Distribution, _ build: AppBuild, locale: Locale = .current
    ) -> String {
        if let dev = build.developmentText(version, locale: locale) { return dev }
        return switch distribution {
        case .github: L10n.t("\(version) · GitHub version")
        case .appStore: L10n.t("\(version) · App Store version")
        }
    }
}

/// The updater's switches (Sparkle keeps them: schema `update.automaticChecks`, `update.automaticInstall`) and when it
/// last looked. Off: no check by itself, no reminder; *Check for Updates…* still works.
struct UpdateRows: View {
    @Bindable var updates: Updates

    var body: some View {
        Toggle(isOn: $updates.automaticChecks.animation(.snappy)) {
            Text(L10n.t("Check for updates automatically"))
            Text(L10n.t("Once a day, from sukrara.mauri.app. Every update is signed and checked before it installs."))
        }
        Toggle(isOn: $updates.automaticDownloads) {
            Text(L10n.t("Download and install updates automatically"))
            Text(L10n.t("Installed when Sukrara quits or the Mac restarts. When off, Sukrara asks first."))
        }
        .disabled(!updates.automaticChecks)
        Text(Self.lastCheck(updates.lastCheck)).font(.callout).foregroundStyle(.secondary)
    }

    /// "Last checked 2 hours ago" / "Not checked yet".
    static func lastCheck(_ date: Date?) -> String {
        guard let date else { return L10n.t("Not checked yet") }
        let ago = date.formatted(.relative(presentation: .named, unitsStyle: .wide))
        return L10n.t("Last checked \(ago)")
    }
}

/// Settings from: Their settings or This Mac's own, the sentence saying what's in use, and in Custom the copy of the wearer's
/// settings and the reset to Sukrara's defaults (each asked first).
struct SettingsFromPicker: View {
    @Bindable var model: SettingsModel

    var body: some View {
        Picker(L10n.t("Settings from"), selection: $model.settingsFrom) {
            ForEach(SettingsFrom.allCases, id: \.self) { Text(Self.name($0, model)).tag($0) }
        }
        LabeledContent(L10n.t("In use")) {
            Text(model.inUse).multilineTextAlignment(.trailing).foregroundStyle(.secondary)
        }
        if model.editable {
            HStack {
                Spacer()
                Button(L10n.t("Copy Their Settings…")) { model.confirmingCopy = true }
                    .disabled(model.wearer == nil)
                    .confirmationDialog(
                        L10n.t("Copy \(Self.who(model))'s settings?"), isPresented: $model.confirmingCopy
                    ) {
                        Button(L10n.t("Copy Settings")) { model.copyWearer() }
                        Button(L10n.t("Cancel"), role: .cancel) {}
                    } message: {
                        Text(
                            L10n.t(
                                "This Mac's ranges, time shown, rings, lines, last treatment, sensor countdown and alert rules become theirs, as a starting point to change. Sounds and the other settings of this Mac stay."
                            ))
                    }
                Button(L10n.t("Reset to Defaults…")) { model.confirmingReset = true }
                    .confirmationDialog(L10n.t("Reset to Sukrara's defaults?"), isPresented: $model.confirmingReset) {
                        Button(L10n.t("Reset to Defaults"), role: .destructive) { model.resetToDefaults() }
                        Button(L10n.t("Cancel"), role: .cancel) {}
                    } message: {
                        Text(
                            L10n.t(
                                "This Mac's ranges, graph, rings, lines, last treatment, sensor countdown, alert rules and quiet rules go back to Sukrara's defaults. Sounds, the no-data alert, Mute all and the notification's buttons stay."
                            ))
                    }
            }
        }
    }

    static func who(_ model: SettingsModel) -> String { model.wearer?.wearer ?? L10n.t("this person") }

    static func name(_ f: SettingsFrom, _ model: SettingsModel) -> String {
        switch f {
        case .wearer:
            model.wearer?.wearer.map { L10n.t("\($0)'s settings") } ?? L10n.t("Their settings")
        case .custom: L10n.t("This Mac's own")
        }
    }
}

/// Open at login with every `SMAppService` state in words and its way out; a run without a login item says so
/// (the switch is dimmed) instead of a switch that does nothing.
struct OpenAtLoginRows: View {
    let loginItem: LoginItem?

    var body: some View {
        if let loginItem {
            Toggle(
                L10n.t("Open at login"),
                isOn: Binding(get: { loginItem.status.isOn }, set: { loginItem.set($0) }))
            if let note = Self.note(loginItem.status, error: loginItem.lastError) {
                Notice(note)
            }
            if loginItem.offersLoginItems {
                Button(L10n.t("Open Login Items…"), action: loginItem.openSettings)
            }
        } else {
            Toggle(L10n.t("Open at login"), isOn: .constant(false)).disabled(true)
            Text(L10n.t("Not available in this run (development or test build)."))
                .foregroundStyle(.secondary)
        }
    }

    /// The line under the switch (a refused change first: it explains why the switch didn't move), nil when the
    /// switch says it all (on / off).
    static func note(_ status: LoginItem.Status, error: String?) -> String? {
        if let error { return L10n.t("Couldn't change Open at login: \(error)") }
        return status == .needsApproval
            ? L10n.t("Waiting for your approval: allow Sukrara in System Settings › General › Login Items.") : nil
    }
}

/// The link with the wearer, in words, with its exits (Follow… / Scan QR Code… / Stop following… / Cancel / Follow
/// again).
struct FollowingRows: View {
    @Bindable var follow: FollowModel
    var openFollow: () -> Void

    var body: some View {
        switch follow.status {
        case .notFollowing:
            row(L10n.t("Not following anyone"), "person.crop.circle.badge.questionmark")
            Button(L10n.t("Follow…"), action: openFollow)
            ScanQRButton(follow: follow, openFollow: openFollow)
        case .joining:
            row(L10n.t("Looking up the invite…"), "arrow.triangle.2.circlepath")
            Button(L10n.t("Cancel")) { Task { await follow.cancel() } }
        case .waiting(let wearer, let pairing?):
            PairingCodeView(follow: follow, wearer: wearer ?? L10n.t("this person"), check: pairing)
        case .waiting(let wearer, nil):
            WaitingForWearer(follow: follow, wearer: wearer ?? L10n.t("this person"))
        case .following(let wearer, let last, let canWrite):
            Label {
                Text(L10n.t("Following \(wearer ?? L10n.t("this person"))"))
            } icon: {
                AvatarView(avatar: follow.wearer?.avatar, name: wearer ?? "", size: 24)
            }
            .frame(minHeight: 28)
            Text(
                last.map { L10n.t("Last reading \($0.formatted(.relative(presentation: .named)))") }
                    ?? L10n.t("No reading yet")
            )
            .foregroundStyle(.secondary)
            if let source = follow.source { Text(source).foregroundStyle(.secondary) }
            Text(canWrite ? L10n.t("Can add treatments") : L10n.t("Can't add treatments (they decide)"))
                .foregroundStyle(.secondary)
            if let problem = follow.problem {
                Notice(problem) {
                    if follow.bluetoothBlocked {
                        Button(L10n.t("Open Bluetooth Settings")) { BlockedFeature.openBluetoothSettings() }
                    }
                }
            }
            if let me = follow.me {
                IdentityRow(me: me, wearer: wearer) { name, avatar in
                    Task { await follow.saveProfile(name: name, avatar: avatar) }
                }
            }
            Button(L10n.t("Stop following…"), role: .destructive) { follow.confirmingUnfollow = true }
                .confirmationDialog(
                    L10n.t("Stop following \(wearer ?? L10n.t("this person"))?"),
                    isPresented: $follow.confirmingUnfollow
                ) {
                    Button(L10n.t("Stop following"), role: .destructive) { Task { await follow.unfollow() } }
                    Button(L10n.t("Cancel"), role: .cancel) {}
                } message: {
                    Text(
                        L10n.t(
                            "Their readings are removed from this Mac. To follow again, they have to send a new invite."
                        ))
                }
        case .declined, .inviteUsed, .inviteExpired, .revoked, .broken:
            Notice(Self.endText(follow.status))
            Button(L10n.t("Follow again…"), action: openFollow)
            ScanQRButton(follow: follow, openFollow: openFollow)
        }
    }

    private func row(_ text: String, _ symbol: String) -> some View {
        Label(text, systemImage: symbol).frame(minHeight: 24)
    }

    static func endText(_ s: FollowStatus) -> String {
        switch s {
        case .declined: L10n.t("They declined. Ask them for a new invite.")
        case .inviteUsed: L10n.t("Another device used this invite. Ask for a new one.")
        case .inviteExpired: L10n.t("The invite expired before they answered. Ask them for a new one.")
        case .revoked: L10n.t("They stopped sharing with this Mac.")
        case .broken(let why): L10n.t("Following stopped: \(why.text)")
        default: ""
        }
    }
}

/// The settings window's content: the pane chosen in the window's toolbar (`SettingsWindowChrome`), one grouped form
/// filling the window (no tab view inside it); the window reopens on the last one.
struct SettingsView: View {
    @Bindable var model: SettingsModel
    /// Open at login (LoginItem over SMAppService.mainApp, schema `general.openAtLogin`).
    var loginItem: LoginItem?
    /// The updater (nil: the App Store version, offline runs).
    var updates: Updates?
    var openFollow: () -> Void
    var reconnect: (() -> Void)? = nil
    /// Settings › Source's *Answer…* (an unanswered Mac): setup's Whose glucose step.
    var answerSource: (() -> Void)? = nil
    /// The running alert rules (Send Test Alert, Active now); nil in an offline run.
    var alerts: AlertController? = nil
    /// Opens the About window (nil when this run has none: renders).
    var openAbout: (() -> Void)? = nil

    var body: some View {
        Group {
            switch model.pane {
            case .general:
                GeneralPane(
                    model: model, loginItem: loginItem, updates: updates, openFollow: openFollow,
                    openAbout: openAbout)
            case .source:
                SourcePane(model: model, openFollow: openFollow, reconnect: reconnect, answer: answerSource)
            case .alerts: AlertsRangesPane(model: model, controller: alerts)
            case .readings: ReadingsPane(model: model)
            case .graph: GraphPane(model: model)
            case .metrics: MetricsPane(model: model)
            }
        }
        .id(model.pane)  // each pane starts at its top
        .environment(\.glucoseUnit, model.unit)
        .frame(minWidth: 560, minHeight: 360)
    }
}
